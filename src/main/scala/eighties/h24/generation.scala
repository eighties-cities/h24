package eighties.h24

import java.io.{BufferedInputStream, FileInputStream, FileOutputStream}
import java.text.SimpleDateFormat
import java.util.Calendar

import better.files._
import com.github.tototoshi.csv.{CSVFormat, CSVReader, DefaultCSVFormat}
import dynamic.MoveMatrix
import dynamic.MoveMatrix.{Cell, TimeSlice}
import eighties.h24.social._
import eighties.h24.tools.Log
import space.{BoundingBox, Location}
import eighties.h24.tools.random._
import eighties.h24.tools.math._
import monocle.macros.Lenses
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.geometry.jts.{JTS, JTSFactoryFinder}
import org.geotools.referencing.CRS
import org.joda.time.{DateTime, DateTimeFieldType}
import org.locationtech.jts.geom.{Coordinate, Envelope, GeometryCollection, GeometryFactory, MultiPolygon, Point, Polygon}
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulationBuilder
import org.opengis.referencing.crs.CoordinateReferenceSystem
import org.opengis.referencing.operation.MathTransform
import scalaz.Memo

import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.util.{Failure, Random, Success, Try}

object  generation {
  type LCell = ((Int, Int), Cell)
  type SpatialCellIndex = STRtree

  sealed class SchoolAge(val from: Int, val to: Option[Int])

  object SchoolAge {
    object From0To1 extends SchoolAge(0, Some(1))
    object From2To5 extends SchoolAge(2, Some(5))
    object From6To10 extends SchoolAge(6, Some(10))
    object From11To14 extends SchoolAge(11, Some(14))
    object From15To17 extends SchoolAge(15, Some(17))
    object From18To24 extends SchoolAge(18, Some(24))
    object From25To29 extends SchoolAge(25, Some(29))
    object Above30 extends SchoolAge(30, None)

    def all = Vector[SchoolAge](From0To1, From2To5, From6To10, From11To14, From15To17, From18To24, From25To29, Above30)
    def index(age: Double) = SchoolAge.all.lastIndexWhere(value => age > value.from)
  }

  case class AreaID(id: String) extends AnyVal

  object WorldFeature {
    import boopickle.Default._

    def save(features: WorldFeature, file: java.io.File)  = {
      file.getParentFile.mkdirs()
      val os = new FileOutputStream(file)
      try os.getChannel.write(Pickle.intoBytes(features))
      finally os.close()
    }

    def load(file: java.io.File) = {
      val is = new FileInputStream(file)
      try Unpickle[WorldFeature].fromBytes(is.getChannel.toMappedByteBuffer)
      finally is.close()
    }
  }

  @Lenses case class WorldFeature(individualFeatures: Array[IndividualFeature], originalBoundingBox: BoundingBox, boundingBox: BoundingBox, gridSize: Int)


  @Lenses case class IndividualFeature(
    ageCategory: Int,
    sex: Int,
    education: Int,
    location: space.Location)

  case class Equipment(typeEquipment: String, point: Point, location:space.Coordinate, quality: String, iris: AreaID)
  case class Activity(point: Point, location: space.Coordinate)
  case class Regex (pos:Integer, regex : String => String)
  case class Slice(from:Integer,to:Integer)
  case class ShapeData(file:File, spatialId : String, regexs:Option[Seq[Regex]] = None )

  case class CSVData (file:File, startLine:Int, slices:Seq[Slice], spatialId:Integer, separator:CSVFormat, regexs:Option[Seq[Regex]] = None)
  case class CellsData (file:File, xName:String, yName:String, dName:String)

  def withCSVReader[T](file: File)(format: CSVFormat)(f: CSVReader => T) = {
    val in = new BufferedInputStream(new FileInputStream(file.toJava))
    val stream = new LZMACompressorInputStream(in)
    val reader = CSVReader.open(Source.fromInputStream(stream, "ISO-8859-1"))(format)
    try f(reader)
    finally reader.close
  }

  object CommaFormat extends DefaultCSVFormat {
    override val delimiter = ','
  }
  object SemicolonFormat extends DefaultCSVFormat {
    override val delimiter = ';'
  }

  object CSVData {

    def mapToArea[T](data:CSVData)(implicit transform: Vector[Vector[Double]] => T ) = withCSVReader(data.file)(data.separator) { reader =>
      Try {
        reader.iterator.drop(data.startLine).filter(lines => lines.nonEmpty).map { line =>
          val cleanLine = data.regexs match {
            case Some(r) => applyRegex(r, line)
            case None => line
          }
          AreaID(cleanLine(data.spatialId)) -> transform(slicing(line, data).toVector)
        }.toMap
      }
    }

    def slicing(line: Seq[String], data: CSVData) = {
      data.slices match {
        case l if data.slices.nonEmpty => l.map { s =>
          line.slice(s.from, s.to).map(toDouble).toVector
        }
        case _ => Seq(line.map(toDouble).toVector)
      }
    }

    def toDouble(s:String):Double = {

      val removeQuote = (s: String) => s.replace("\"", "")
      val replaceComma = (s: String) => s.replace(",", ".")
      val replaceEmptyCol = (s: String) => if (s.trim.isEmpty) "0.0" else s

      val toDouble = (s: String) => s.toDouble

      def makeTry[TIn, TOut](fn: TIn => TOut) = (x: TIn) => Try(fn(x))

      val tryParsing = makeTry(removeQuote andThen replaceComma andThen replaceEmptyCol andThen toDouble)
      val d = tryParsing(s)
      d.get
    }


  }

  @scala.annotation.tailrec
  def applyRegex(regexes : Seq[Regex], l:Seq[String] ):Seq[String] = {
    if (regexes.nonEmpty) {
      val fPopped :: newRList = regexes
      val newSList = l.updated(fPopped.pos,fPopped.regex(l(fPopped.pos)))
      applyRegex(newRList, newSList)
    }
    else l
  }


  def readGeometry(shapeData:ShapeData, filter: String => Boolean): Try[(Seq[AreaID],AreaID => Option[MultiPolygon])] = {
    def aggregated(geometry: Map[AreaID, MultiPolygon]): AreaID => Option[MultiPolygon] = Memo.mutableHashMapMemo {
      id: AreaID =>
      geometry.get(id) match {
        case Some(mp) => Some(mp)
        case None =>
          val irises = geometry.filter { case (key, _) => key.id.startsWith(id.id) }
          union(irises.values)
      }
    }

    val store = new ShapefileDataStore(shapeData.file.toJava.toURI.toURL)
    try {
      val reader = store.getFeatureReader
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          val ids = featureReader.filter(feature => filter(feature.getAttribute(shapeData.spatialId).toString.trim))
            .map { feature =>
              val geom = feature.getDefaultGeometry.asInstanceOf[MultiPolygon]
              val id = feature.getAttribute(shapeData.spatialId).toString
              val idClean = shapeData.regexs match {
                case Some(r) => applyRegex(r,Seq(id))
                case None => Seq(id)
              }
              AreaID(idClean.head) -> geom
            }.toMap
          (ids.keys.toSeq, aggregated(ids))
        }
      } finally reader.close()
    } finally store.dispose()
  }


  val transposeCSV =  (s:Seq[Vector[Double]]) =>  s.transpose.map { case Seq(x, y) => x / y }.toVector
  val flatCSV = (s:Seq[Vector[Double]]) => s.flatten.toVector

  def readEducationSex(sexData : CSVData) = CSVData.mapToArea[Vector[Vector[Double]]](sexData)
  def readAgeSchool(eduData:CSVData) = CSVData.mapToArea[Vector[Double]](eduData)(flatCSV)
  def readAgeSex(popData : CSVData) = CSVData.mapToArea[Vector[Double]](popData)(flatCSV)

  def readEquipment(file: File) = withCSVReader(file)(CommaFormat){ reader =>
    Try {
      reader.iterator.drop(1).map { line =>
        val iris = line(4).trim.replaceAll("_","").replaceAll("0000$","").trim
        val typeEquipment = line(5)
        val x = line(6)
        val y = line(7)
        val quality = line(8)
        AreaID(iris) -> Vector(typeEquipment,x,y,quality)
      }.toVector
    }
  }

  def readMobilityFlows(file: File)(commune: AreaID) = withCSVReader(file)(SemicolonFormat) { reader =>
    Try {
      reader.iterator.drop(1).filter{ l => l.head == commune.id }.map { line =>
        val workLocation = line(2)
        val numberOfFlows = line(4).toDouble
        (workLocation, numberOfFlows)
      }.toVector
    }
  }

  def mainActivityLocationFromMobilityFlows(workFile: File, studyFile: File, geometry: AreaID => Option[MultiPolygon]) = scalaz.Memo.mutableHashMapMemo { commune: AreaID =>
    val workFlows = readMobilityFlows(workFile)(commune).get.toArray
    val studyFlows = readMobilityFlows(studyFile)(commune).get.toArray
    (work: Boolean, rng: Random) => {
      val areaID = multinomial(if (work) workFlows else studyFlows)(rng)
      geometry(AreaID(areaID)).map(geom=>{
        val sampler = new PolygonSampler(geom)
        val coordinate = sampler.apply(rng)
        geom.getFactory.createPoint(coordinate)
      })
    }
  }

  type KMCell = (MultiPolygon, Double, Int, Int)

  def readCells(cellsData: CellsData) = {
    val store = new ShapefileDataStore(cellsData.file.toJava.toURI.toURL)
    try {
      val reader = store.getFeatureReader
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          val index = new STRtree()
          featureReader.foreach { feature =>
              val geom = feature.getDefaultGeometry.asInstanceOf[MultiPolygon]
              val ind = feature.getAttribute(cellsData.dName).asInstanceOf[Double]
              val x = feature.getAttribute(cellsData.xName).asInstanceOf[Double].toInt
              val y = feature.getAttribute(cellsData.yName).asInstanceOf[Double].toInt
              index.insert(geom.getEnvelopeInternal, (geom,ind,x,y))
            }
          index
        }
      } finally reader.close()
    } finally store.dispose()
  }

  def sampleIris(ageSexV: Vector[Double], schoolAgeV: Vector[Double], educationSexV: Vector[Vector[Double]], rnd: Random) = {
    val total = ageSexV.sum

    val ageSexSizes = Seq(6, 2)
    val ageSexVariate = new RasterVariate(ageSexV.toArray, ageSexSizes)

    val educationSexSizes = Seq(7)
    val educationSexVariates = ArrayBuffer(
      new RasterVariate(educationSexV(0).toArray, educationSexSizes),
      new RasterVariate(educationSexV(1).toArray, educationSexSizes))

    (0 until total.toInt).map { _ =>
      val sample = ageSexVariate.compute(rnd)
      val ageIndex = (sample(0) * ageSexSizes.head).toInt
      val sex = (sample(1) * ageSexSizes(1)).toInt
      val schooled = ageIndex match {
        case 0 => rnd.nextDouble() < (schoolAgeV.slice(7,10).sum / (ageSexV(0) + ageSexV(6)))//schoolAgeV.slice(0,3).sum)
        case 1 => rnd.nextDouble() < (schoolAgeV.slice(10,13).sum / schoolAgeV.slice(3,6).sum)
        case _ => rnd.nextDouble() < (schoolAgeV(13) / schoolAgeV(6))
      }
      val education = if (schooled) 0 else {
        if (ageIndex > 0) (educationSexVariates(sex).compute(rnd)(0) * educationSexSizes.head).toInt + 1
        else 1 // less thant 15yo but not in school => DIPL0
      }
      (ageIndex, sex, education)
    }
  }

  /*
    Generate the population by considering the geometries of the irises.
    This generates a population from the statistics at the iris level and chooses the cell in which the sampled individuals are placed by selecting the cells that intersect the iris geometries.
     */
  def generatePopulation(
    rnd: Random,
    irises: Seq[AreaID],
    geometry: AreaID => Option[MultiPolygon],
    ageSex: Map[AreaID, Vector[Double]],
    schoolAge: Map[AreaID, Vector[Double]],
    educationSex: Map[AreaID, Vector[Vector[Double]]],
    cells: STRtree) = {
    /*
    //test
    val env = cells.getRoot.getBounds
    val allCells = cells.query(env.asInstanceOf[Envelope]).toArray.toSeq.map(_.asInstanceOf[KMCell])
    val totalPopulation = allCells.map(_._2).sum
    println("pop from cells  " + totalPopulation)
    val totalPopIris = irises.map { id => ageSex.get(id).get.sum }.sum
    println("pop from irises " + totalPopIris)
    */
    val inCRS = CRS.decode("EPSG:2154")
//    val outCRS = CRS.decode("EPSG:3035")
    val outCRS = CRS.decode("EPSG:27572")
    val transform = CRS.findMathTransform(inCRS, outCRS, true)

    // the zipWithIndex is only useful for debug so we might want to remove it at some point...
    irises.zipWithIndex.map { case (id: AreaID, index: Int) =>
      if ((index % 1000) == 0) Log.log(s"$index")
      // we use getOrElse to handle the absence of an IRIS in the INSEE database
      val ageSexV = ageSex.getOrElse(id, Vector())
      val schoolAgeV = schoolAge.getOrElse(id, Vector())
      val educationSexV = educationSex.getOrElse(id, Vector())
      val total = ageSexV.sum
      // make sure all relevant distributions exist
      if (total > 0 && ageSexV.nonEmpty && schoolAgeV.nonEmpty && educationSexV(0).nonEmpty && educationSexV(1).nonEmpty) {
        def seq = sampleIris(ageSexV, schoolAgeV, educationSexV, rnd)
        val transformedIris = JTS.transform(geometry(id).get, transform)
        val relevantCells = cells.query(transformedIris.getEnvelopeInternal).toArray.toSeq.map(_.asInstanceOf[KMCell]).filter(_._1.intersects(transformedIris))
        val relevantCellsArea = relevantCells.map {
          cell => {
            val g = cell._1.intersection(transformedIris)
            ((cell._3, cell._4), cell._2 * g.getArea / cell._1.getArea)
          }
        }.toArray.filter { case (_, v) => v > 0 }
        val sampledCells = if (relevantCellsArea.isEmpty) {
          // if no cell with proper population data, sample uniformly in all cells intersecting
          relevantCells.map{cell=>((cell._3, cell._4), 1.0)}.toArray
        } else relevantCellsArea
        seq.map {sample=>
          val cell = multinomial(sampledCells)(rnd)
          IndividualFeature(ageCategory = sample._1, sex = sample._2, education = sample._3, location = cell)
        }.filter(_.ageCategory > 0) //remove people with age in 0-14
      } else IndexedSeq()
    }
  }



  def generateFeatures(
    filter: String => Boolean,
    shapeData: ShapeData,
    popData:CSVData,
    schoolAgeData:CSVData,
    educationSexData:CSVData,
    cellsData:CellsData,
    rng: Random,
    pop: (Random, Seq[AreaID], AreaID => Option[MultiPolygon], Map[AreaID, Vector[Double]], Map[AreaID, Vector[Double]], Map[AreaID, Vector[Vector[Double]]], STRtree) => Seq[IndexedSeq[generation.IndividualFeature]]) =
    for {
      (spatialUnit, geom) <- readGeometry(shapeData, filter)
      ageSex <- readAgeSex(popData)
      schoolAge <- readAgeSchool(schoolAgeData)
      educationSex <- readEducationSex(educationSexData)
      cells <- readCells(cellsData)
    } yield pop(rng, spatialUnit, geom, ageSex, schoolAge, educationSex, cells).flatten

  /*
  Generate the population without considering the geometries of the irises.
  This generates a population from the statistics at the iris level but by choosing randomly the cell in which the sampled individuals are placed.
  The "geometry" parameter is only given to have the same signature...
   */
  def generatePopulationRandomly(
    rnd: Random,
    irises: Seq[AreaID],
    geometry: AreaID => Option[MultiPolygon],
    ageSex: Map[AreaID, Vector[Double]],
    schoolAge: Map[AreaID, Vector[Double]],
    educationSex: Map[AreaID, Vector[Vector[Double]]],
    cells: STRtree) = {
    val env = cells.getRoot.getBounds
    val allCells = cells.query(env.asInstanceOf[Envelope]).toArray.toSeq.map(_.asInstanceOf[KMCell])
    val allCellsArea = allCells.map{cell => {((cell._3, cell._4), cell._2)}}.toArray.filter{case (_,v) => v>0}.map{case (l,_) => (l,1.0)}.toList
    val mn = new Multinomial(allCellsArea)
    var i = 0
    irises.map { id =>
      val ageSexV = ageSex(id)
      val schoolAgeV = schoolAge(id)
      val educationSexV = educationSex(id)
      val total = ageSexV.sum
      val ageSexSizes = Seq(6,2)
      val ageSexVariate = new RasterVariate(ageSexV.toArray, ageSexSizes)
      val educationSexSizes = Seq(7)
      val educationSexVariates = ArrayBuffer(
        new RasterVariate(educationSexV(0).toArray, educationSexSizes),
        new RasterVariate(educationSexV(1).toArray, educationSexSizes))

      val res = (0 until total.toInt).map{ _ =>
        if (i % 1000000 == 0) Log.log(Calendar.getInstance.getTime + " ind " + i)
        i = i + 1

        val sample = ageSexVariate.compute(rnd)
        val ageIndex = (sample(0)*ageSexSizes.head).toInt
        val ageInterval = Age.all(ageIndex)
        val residual = sample(0)*ageSexSizes.head - ageIndex
        val age = ageInterval.to.map(max => rescale(ageInterval.from, max, residual))
        val sex = (sample(1)*ageSexSizes(1)).toInt

        var tempIndex = -1
        var tempP = -1.0
        val schooled = age match {
          case Some(a) =>
            val schoolAgeIndex = SchoolAge.index(a)
            tempIndex = schoolAgeIndex
            if (schoolAgeIndex == 0) false else {
              tempP = schoolAgeV(schoolAgeIndex - 1)
              rnd.nextDouble() < schoolAgeV(schoolAgeIndex - 1)
            }
          case None => false
        }
        val education = if (schooled) 0 else {
          if (ageIndex > 0) (educationSexVariates(sex).compute(rnd)(0) * educationSexSizes.head).toInt + 1
          else 1
        }
        //val cell = multinomial(allCellsArea)(rnd)
        val cell = mn.draw(rnd)
        IndividualFeature(
          ageCategory = ageIndex,
          sex = sex,
          education = education,
          location = cell
        )
      }.filter(_.ageCategory > 0)//remove people with age in 0-14
      res
    }
  }

  def generatePoint(geom: MultiPolygon, inOutTransform: MathTransform, outCRS: CoordinateReferenceSystem)(rnd:Random) = {
      val sampler = new PolygonSampler(geom)
      val coordinate = sampler(rnd)
      val transformed = JTS.transform(coordinate, null, inOutTransform)
      JTS.toGeometry(JTS.toDirectPosition(transformed, outCRS))
    }

  def generateEquipment(rnd: Random, eq: Vector[(AreaID, Vector[String])], geometry: AreaID => Option[MultiPolygon], completeArea: MultiPolygon) = {
    val l93CRS = CRS.decode("EPSG:2154")
    val l2eCRS = CRS.decode("EPSG:27572")
    val outCRS = CRS.decode("EPSG:3035")
    val transformL93 = CRS.findMathTransform(l93CRS, outCRS, true)
    val transformL2E = CRS.findMathTransform(l2eCRS, outCRS, true)
    val transformedArea = JTS.transform(completeArea, transformL93)
    eq.map {
      case (id, vec) =>
        val typeEquipment = vec(0)
        val x = vec(1)
        val y = vec(2)
        val quality = vec(3).trim
        if (quality.equalsIgnoreCase("bonne") || quality.equalsIgnoreCase("acceptable")) {
          val coordinate = new Coordinate(x.toDouble, y.toDouble)
          val transformed = JTS.transform(coordinate, null, transformL2E)
          val point = JTS.toGeometry(JTS.toDirectPosition(transformed, outCRS))
          Equipment(
            typeEquipment = typeEquipment,
            point = point,
            location = (point.getX, point.getY),
            quality = quality,
            iris = id
          )
        } else {
          val geom = geometry(id)
          geom match {
            case Some(g) =>
              val point = generatePoint(g, transformL93, outCRS)(rnd)
              Equipment(
                typeEquipment = typeEquipment,
                point = point,
                location = (point.getX, point.getY),
                quality = quality,
                iris = id
              )
            case None => Equipment(
              typeEquipment = typeEquipment,
              point = JTS.toGeometry(JTS.toDirectPosition(new Coordinate(0.0,0.0), outCRS)),
              location = (0.0, 0.0),
              quality = quality,
              iris = id
            )
          }
          /*
          val irises = geometry.filter{case (key,g)=>key.equalsIgnoreCase(id)||key.startsWith(id)}
          val size = irises.size
          if (size == 0) {
            println(s"Could Not find IRIS $id")
            Equipment(
              typeEquipment = typeEquipment,
              point = JTS.toGeometry(JTS.toDirectPosition(new Coordinate(0.0,0.0), outCRS)),
              location = (0.0, 0.0),
              quality = quality,
              iris = id
            )
          } else {
            if (size > 1) {
              println(s"union of $size irises for $id")
            }
            val geom = union(irises.values)
            val sampler = new PolygonSampler(geom.get)
            val coordinate = sampler(rnd)
            val transformed = JTS.transform(coordinate, null, transformL93)
            val point = JTS.toGeometry(JTS.toDirectPosition(transformed, outCRS))
            Equipment(
              typeEquipment = typeEquipment,
              point = point,
              location = (point.getX, point.getY),
              quality = quality,
              iris = id
            )
          }
          */
        }
    }.filter (e => e.point.intersects(transformedArea))
  }

  def union(polygons: Iterable[MultiPolygon]): Option[MultiPolygon] = {
    if (polygons.size == 1) {
      Some(polygons.head)
    } else {
      val geometryFactory = JTSFactoryFinder.getGeometryFactory()
      val union = geometryFactory.createGeometryCollection(polygons.toArray).union
      union match {
        case p: Polygon => Some(geometryFactory.createMultiPolygon(Array(p)))
        case mp: MultiPolygon => Some(mp)
        case _ => None
      }
    }
  }

  def generateEquipments(bpe: File, shapeData: ShapeData, filter: String => Boolean, rng: Random) = {
    for {
      (irises, geometry) <- readGeometry(shapeData, filter).toOption
      completeArea <- union(irises.flatMap(i => geometry(i).toSeq))
      equipment <- readEquipment(bpe).toOption
    } yield generateEquipment(rng, equipment, geometry, completeArea)//.toIterator
  }

//  def sampleActivity(feature: IndividualFeature, rnd: RandomGenerator, distance: Double = 10000) = {
//    val poisson = new PoissonDistribution(rnd, distance, PoissonDistribution.DEFAULT_EPSILON, PoissonDistribution.DEFAULT_MAX_ITERATIONS)
//    val dist = poisson.sample.toDouble
//    val angle = rnd.nextDouble * Math.PI * 2.0
//    val p = feature.point
//    val factory = p.getFactory
//    val point = factory.createPoint(new Coordinate(p.getX + Math.cos(angle) * dist, p.getY + Math.sin(angle) * dist))
//    Activity(
//      point = point,
//      location = (point.getX, point.getY)
//    )
//  }

  class RasterVariate(pdf: Array[Double], val m_size: Seq[Int]) {
    val N = m_size.size
    val m_totalsSize = m_size.product
    val m_cdf = buildCdf(pdf, m_size)
    val m_sum = pdf.sum //foldLeft(0.0)((a, b) => a + b)

    def buildCdf(pdf: Array[Double], size: Seq[Int]) = {
      var sum = 0.0
      val cdf = Array.ofDim[Double](pdf.length + 1)
      cdf(0) = 0.0

      for (i <- 1 to m_totalsSize) {
        sum = sum + pdf(i - 1)
        cdf(i) = sum
      }

      cdf.map(_ / sum)
    }

    def compute(rng: Random): Array[Double] = {
      import collection.Searching._

      val x = rng.nextDouble()
      var offset = m_cdf.search(x).insertionPoint - 1
      val output = Array.ofDim[Double](N)
      for (i <- 0 until N) {
        val ix = offset % m_size(i)
        output(i) = (ix + rng.nextDouble()) / m_size(i)
        offset /= m_size(i)
      }
      output
    }

  }
  class PolygonSampler(val polygon: MultiPolygon, val tolerance: Double = 0.1) {
    val triangles = {
      val builder = new ConformingDelaunayTriangulationBuilder
      builder.setSites(polygon)
      builder.setConstraints(polygon)
      builder.setTolerance(tolerance)
      val triangleCollection = builder.getTriangles(polygon.getFactory).asInstanceOf[GeometryCollection]
      var areaSum = 0.0
      val trianglesInPolygon = (0 until triangleCollection.getNumGeometries).map(triangleCollection.getGeometryN(_).asInstanceOf[Polygon]).filter(p => {
        val area = p.getArea
        p.intersection(polygon).getArea > 0.99 * area
      })
      trianglesInPolygon.map { triangle =>
        areaSum += triangle.getArea
        (areaSum, triangle)
      }
    }
    val totalArea = triangles.last._1
    def apply(rnd: Random) = {
      val s = rnd.nextDouble() * totalArea
      val t = rnd.nextDouble()
      val triangleIndex = triangles.indexWhere(s < _._1)
      val area = triangles(triangleIndex)._1
      val previousArea = if (triangles.isDefinedAt(triangleIndex - 1)) triangles(triangleIndex - 1)._1 else 0.0
      val triangle = triangles(triangleIndex)._2
      val tmp = Math.sqrt((s - previousArea) / (area - previousArea))
      val a = 1 - tmp
      val b = (1 - t) * tmp
      val c = t * tmp
      val coord = triangle.getCoordinates
      val p1 = coord(0)
      val p2 = coord(1)
      val p3 = coord(2)
      val x1 = p1.x
      val x2 = p2.x
      val x3 = p3.x
      val y1 = p1.y
      val y2 = p2.y
      val y3 = p3.y
      val x = a * x1 + b * x2 + c * x3
      val y = a * y1 + b * y2 + c * y3
      new Coordinate(x,y)
    }
  }

  case class Flow(timeSlice: TimeSlice, sex:Sex, age:Age, education:Education, activity: space.Location, residence: space.Location)

  def readFlowsFromEGT(aFile: File, location: Coordinate=>space.Location, slices: Vector[TimeSlice]) =
    withCSVReader(aFile)(SemicolonFormat){ reader =>
    Try {
      def startTime(line: Map[String, String]) = line("HEURE_DEB").trim
      def endTime(line: Map[String, String]) = line("HEURE_FIN").trim
      def motif(line: Map[String, String]) = line("MOTIF").trim
      def pX(line: Map[String, String]) = line("ZF_X").trim
      def pY(line: Map[String, String]) = line("ZF_Y").trim
      def pXres(line: Map[String, String]) = line("RES_ZF_X").trim
      def pYres(line: Map[String, String]) = line("RES_ZF_Y").trim
      def getSex(line: Map[String, String]) = line("SEX").trim
      def getAge(line: Map[String, String]) = line("AGE").toInt
      def getEduc(line: Map[String, String]) = line("KEDUC").toInt
      reader.allWithHeaders().filter { line =>
        val (d1, d2, mot, px, py, resx, resy) = (startTime(line), endTime(line), motif(line), pX(line), pY(line), pXres(line), pYres(line))
        !(mot.isEmpty || mot.equalsIgnoreCase("8") || mot.equalsIgnoreCase("9") ||
          px.equalsIgnoreCase("NA") || py.equalsIgnoreCase("NA") ||
          resx.equalsIgnoreCase("NA") || resy.equalsIgnoreCase("NA") ||
          d1.equalsIgnoreCase("NA") || d2.equalsIgnoreCase("NA"))
      }.flatMap { line =>
        def format(date:String) = {
          val formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss")
          Try{new DateTime(formatter.parse(date))} match {
            case Success(d) => d.toInstant
            case Failure(_) => new DateTime(new SimpleDateFormat("dd-MM-yyyy").parse(date)).toInstant
          }
        }
        //val formatter = new SimpleDateFormat("dd/MM/yy hh:mm")
        val date_start = format(startTime(line))
        val date_end = format(endTime(line))
//        val midnight = format("02/01/2010 00:00")
        val sex = getSex(line).toInt match {
          case 1 => Sex.Male
          case 2 => Sex.Female
        }
        val age = Age.parse(getAge(line))
        val dipl = Try{getEduc(line) match {
          case 0 => Education.Dipl0
          case 1 => Education.Schol
          case 2 => Education.BEPC
          case 3 => Education.BEPC
          case 4 => Education.CAPBEP
          case 5 => Education.BAC
          case 6 => Education.BACP2
          case 7 => Education.SUP
          case 8 => Education.BAC
          case 9 => Education.BAC
        }} match {
          case Success(e)=>e
          case Failure(_) => Education.Dipl0
        }
        val point_x = pX(line).replaceAll(",",".").toDouble
        val point_y = pY(line).trim.replaceAll(",",".").toDouble
        val res_x = pXres(line).trim.replaceAll(",",".").toDouble
        val res_y = pYres(line).trim.replaceAll(",",".").toDouble

        val minutesStart = date_start.get(DateTimeFieldType.minuteOfDay())
        val minutesEnd = date_end.get(DateTimeFieldType.minuteOfDay())
        val timeSlices =
          (if (minutesEnd < minutesStart) Array(TimeSlice(minutesStart, 24 * 60), TimeSlice(0, minutesEnd))
          else Array(TimeSlice(minutesStart, minutesEnd))).flatMap(t=> MoveMatrix.split(t, slices))
//          if(date_start.isBefore(midnight) && date_end.isAfter(midnight)) Array(TimeSlice(date_start.get(DateTimeFieldType.minuteOfDay()), 24 * 60), TimeSlice(0, date_end.get(DateTimeFieldType.minuteOfDay())))
//          else Array(TimeSlice(date_start.get(DateTimeFieldType.minuteOfDay()), date_end.get(DateTimeFieldType.minuteOfDay())))

        timeSlices.map(s => Flow(s, sex, age, dipl, location(new Coordinate(point_x,point_y)),location(new Coordinate(res_x,res_y))))
      }.filter(_.age.from >= 15)
    }
  }
  import MoveMatrix._

  /*
  Add the contribution of a flow to a cell for the given time slice.
   */
  def addFlowToCell(c: Cell, flow: Flow, timeSlice: TimeSlice): Cell = {
    // find out if the flow contributes to the time slice
    val intersection = overlap(flow.timeSlice, timeSlice).toDouble
    // if not, do not modify cell
    if(intersection <= 0.0) c
    else {
      // category of the flow
      val cat = AggregatedSocialCategory(SocialCategory(age = flow.age, sex = flow.sex, education = flow.education))
      c.get(cat) match {
        case Some(moves) =>
          // get the index of the flow destination (activity) in the moves
          val index = moves.indexWhere { m => Move.location.get(m) == flow.activity }
          if (index == -1) c + (cat -> (moves :+ Move(flow.activity, intersection.toFloat)))
          else {
            // since the cell already contains flows for this location, just add the flow contribution
            val v = MoveMatrix.Move.ratio.get(moves(index))
            c + (cat -> moves.updated(index, Move(flow.activity, v + intersection.toFloat)))
          }
        case None =>
          c + (cat -> Array(Move(flow.activity, intersection.toFloat)))
      }
    }
  }

  def normalizeFlows(c: Cell): Cell =
    c.map { case (category, moves) =>
      val total = moves.map(Move.ratio.get).sum
      category -> moves.map { Move.ratio.modify(_ / total) }
    }

//  def addFlowToMatrix(slices: TimeSlices, flow: Flow): TimeSlices =
//    slices.map { case (time, slice) =>
//     time ->
//       MoveMatrix.cell(flow.residence).modify { current => addFlowToCell(current, flow, time) }
//    }

  def noMove(timeSlices: Vector[TimeSlice], i: Int, j: Int): MoveMatrix =
    timeSlices.map { ts =>
      ts -> Array.tabulate(i, j) { (_, _) => Map.empty[AggregatedSocialCategory, Array[Move]] }
    }

  def idw(power: Double)(location: Location, moves: Array[Move], neighborhood: Vector[(Location, Array[Move])]): Array[Move] = {
    if (moves.isEmpty) {
      val weights = neighborhood.map(v => v._1 -> 1.0 / scala.math.pow(space.distance(location, v._1), power)).toMap
      def destinationsIndex = neighborhood.flatMap(_._2).map(_.locationIndex).distinct.toArray

      destinationsIndex.map { di =>
        val v = for {
          n <- neighborhood
          value <- n._2.filter(m => m.locationIndex == di).map(Move.ratio.get)
        } yield (n._1, value)

        val values =
          v.map { t =>
            val w = weights(t._1)
            (w, w * t._2)
          }

        val weightSum = values.map(_._1).sum
        val valuesSum = values.map(_._2).sum
        Move(di, (valuesSum / weightSum).toFloat)
      }
    } else moves
  }

  def interpolateFlows(
    index: SpatialCellIndex,
    interpolate: (Location, Array[Move], Vector[(Location, Array[Move])]) => Array[Move])
    (c: Cell, location: Location): Cell = {
    val movesByCat = movesInNeighborhoodByCategory(location, index)
    AggregatedSocialCategory.all.map {
      category =>
        def moves = c.getOrElse(category, Array())
        def m =
          for {
            (l, c) <- movesByCat
            mm <- c.get(category)
          } yield l -> mm

        category -> interpolate(location, moves, m.toVector)
    }.toMap
  }

  val timeSlices = Vector(
    MoveMatrix.TimeSlice.fromHours(0, 8),
    MoveMatrix.TimeSlice.fromHours(8, 16),
    MoveMatrix.TimeSlice.fromHours(16, 24)
  )

  lazy val nightTimeSlice = timeSlices(0)
  lazy val dayTimeSlice = timeSlices(1)
  lazy val eveningTimeSlice = timeSlices(2)

//  def interval(timeSlice: MoveMatrix.TimeSlice) = {
//    //val initialDate = new DateTime(2010, 1, 1, 0)
//    new Interval(new DateTime(2010, 1, 1, timeSlice.from, 0), new DateTime(2010, 1, 1, timeSlice.to, 0))
//  }

  def flowsFromEGT(originalBoundingBox: BoundingBox, boundingBox: BoundingBox, gridSize: Int, aFile: File, inputSRID: String, slices: Vector[TimeSlice] = timeSlices): Try[MoveMatrix] = {
    val inputCRS = CRS.decode(inputSRID)
    val outCRS = CRS.decode("EPSG:3035")

    val transform = CRS.findMathTransform(inputCRS, outCRS, true)
    val geomFactory = new GeometryFactory

    def location(coord: Coordinate): space.Location = {
      val laea_coord = JTS.transform(coord, null, transform)
      // replace by cell...
      val dx = laea_coord.x - originalBoundingBox.minI// * 1000
      val dy = laea_coord.y - originalBoundingBox.minJ// * 1000
      space.cell((dx, dy), gridSize)
    }

    /*
     * Interpolate an entire move matrix.
     * @param moveMatrix the input matrix
     * @return an interpolated matrix
     */
    def interpolate(moveMatrix: MoveMatrix): MoveMatrix =
      moveMatrix.map {
        case (time, cellMatrix) =>
          // create a spatial index to find the neighbors in the matrix
          val index = new STRtree()
          getLocatedCellsFromCellMatrix(cellMatrix).foreach { lc =>
            val p = geomFactory.createPoint(new Coordinate(lc._1._1, lc._1._2))
            index.insert(p.getEnvelopeInternal, lc)
          }
          time -> CellMatrix.modify(interpolateFlows(index, idw(2.0)))(cellMatrix)
      }

    def getMovesFromOppositeSex(c: Cell): Cell =
      AggregatedSocialCategory.all.flatMap { cat =>
        val moves = c.get(cat)
        def noSex = c.find { case (sc, _) => AggregatedSocialCategory.age.get(sc) == AggregatedSocialCategory.age.get(cat) && sc.education == cat.education }.map(_._2)
        (moves orElse noSex) map (m => cat -> m)
      }.toMap


    readFlowsFromEGT(aFile, location, slices).map { l =>
      val nm = noMove(slices, boundingBox.sideI, boundingBox.sideJ)
      for {
        slice <- nm
        f <- l
      } {
        val cell = CellMatrix.get(f.residence)(slice._2)
        CellMatrix.update(f.residence)(slice._2, addFlowToCell(cell, f, slice._1))
      }

      interpolate(nm).map {
        case (c, cellMatrix) => c -> CellMatrix.modify((cell, _) => normalizeFlows(getMovesFromOppositeSex(cell)))(cellMatrix)
      }
    }
  }
}

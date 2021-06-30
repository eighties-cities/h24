package eighties.h24

import java.io.{BufferedInputStream, FileInputStream, FileOutputStream}
import java.text.SimpleDateFormat

import better.files._
import com.github.tototoshi.csv.{CSVFormat, CSVReader, DefaultCSVFormat}
import dynamic.MoveMatrix
import dynamic.MoveMatrix.{Cell, TimeSlice}
import eighties.h24.sampling.{classical_IPF, decomposeAgeSex, nonZero}
import eighties.h24.social._
import eighties.h24.tools.Log
import space.{BoundingBox, Location}
import eighties.h24.tools.random._
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

    def all: Vector[SchoolAge] = Vector[SchoolAge](From0To1, From2To5, From6To10, From11To14, From15To17, From18To24, From25To29, Above30)
    def index(age: Double): Int = SchoolAge.all.lastIndexWhere(value => age > value.from)
  }

  case class AreaID(id: String) extends AnyVal

  object WorldFeature {
    import boopickle.Default._

    def save(features: WorldFeature, file: java.io.File): Int = {
      file.getParentFile.mkdirs()
      val os = new FileOutputStream(file)
      try os.getChannel.write(Pickle.intoBytes(features))
      finally os.close()
    }

    def load(file: java.io.File): WorldFeature = {
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

  def withCSVReader[T](file: File)(format: CSVFormat)(f: CSVReader => T): T = {
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

    def mapToArea[T](data:CSVData)(implicit transform: Vector[Vector[Double]] => T ): Try[Map[AreaID, T]] =
      withCSVReader(data.file)(data.separator) { reader =>
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

    def slicing(line: Seq[String], data: CSVData): Seq[Vector[Double]] = {
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


  val transposeCSV: Seq[Vector[Double]] => Vector[Double] = _.transpose.map { case Seq(x, y) => x / y }.toVector
  val flatCSV: Seq[Vector[Double]] => Vector[Double] = _.flatten.toVector

  def readEducationSex(sexData : CSVData): Try[Map[AreaID, Vector[Vector[Double]]]] = CSVData.mapToArea[Vector[Vector[Double]]](sexData)
  def readAgeSchool(eduData:CSVData): Try[Map[AreaID, Vector[Double]]] = CSVData.mapToArea[Vector[Double]](eduData)(flatCSV)
  def readAgeSex(popData : CSVData): Try[Map[AreaID, Vector[Double]]] = CSVData.mapToArea[Vector[Double]](popData)(flatCSV)

  def readEquipment(file: File): Try[Vector[(AreaID, Vector[String])]] = withCSVReader(file)(CommaFormat){ reader =>
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

  def readMobilityFlows(file: File)(commune: AreaID): Try[Vector[(String, Double)]] = withCSVReader(file)(SemicolonFormat) { reader =>
    Try {
      reader.iterator.drop(1).filter{ l => l.head == commune.id }.map { line =>
        val workLocation = line(2)
        val numberOfFlows = line(4).toDouble
        (workLocation, numberOfFlows)
      }.toVector
    }
  }

  def mainActivityLocationFromMobilityFlows(workFile: File, studyFile: File, geometry: AreaID => Option[MultiPolygon]): AreaID => (Boolean, Random) => Option[Point] = scalaz.Memo.mutableHashMapMemo { commune: AreaID =>
    val workFlows = readMobilityFlows(workFile)(commune).get.toArray
    val studyFlows = readMobilityFlows(studyFile)(commune).get.toArray
    (work: Boolean, rng: Random) => {
      val areaID = multinomial(if (work) workFlows else studyFlows)(rng)
      geometry(AreaID(areaID)).map(geom=>{
        val sampler = new PolygonSampler(geom, computeTriangles(geom))
        val coordinate = sampler.apply(rng)
        geom.getFactory.createPoint(coordinate)
      })
    }
  }

  type KMCell = (MultiPolygon, Double, Int, Int)

  def readCells(cellsData: CellsData): Try[SpatialCellIndex] = {
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

  /**
  Sample using Iris data (age, sex, education).
   */
  def sampleIris(age10V: Vector[Double], age15SexV: Vector[Double], schoolAgeV: Vector[Double], educationSexV: Vector[Vector[Double]], rnd: Random): Seq[(Int, Int, Int)] = {
    def toZippedList(v: Vector[Double]) = v.zipWithIndex.map(_.swap).toList
    def toAgeSex(index: Int) = (index%15, index/15)
    // build the contingency table age category - sex using fine age classes (combining age10 and age6 - 15 years classes) and sex-age6 (age15Sex) margins
    val decomposedAgeSex = decomposeAgeSex(age10V, age15SexV)
    val ageSexM = new Multinomial(toZippedList(decomposedAgeSex.toVector))
    val educationSexM = ArrayBuffer(new Multinomial(toZippedList(educationSexV(0))), new Multinomial(toZippedList(educationSexV(1))))
    val p = schoolAgeV.slice(0, 7) // population for ages values (P12_POP0205	P12_POP0610	P12_POP1114 P12_POP1517	P12_POP1824	P12_POP2529 P12_POP30P)
    val p_scol = schoolAgeV.slice(7, 14) // schooled population for ages values (P12_SCOL0205	P12_SCOL0610	P12_SCOL1114 P12_SCOL1517	P12_SCOL1824	P12_SCOL2529 P12_SCOL30P)
    val p15PSex = Array(age15SexV.slice(1,6).sum,age15SexV.slice(7,12).sum) // total population over 15 by sex
    val nscol15PSex = educationSexV.map(_.sum) // total not schooled population over 15 by sex
    val scol15PSex = p15PSex.zip(nscol15PSex).map{case(a,b)=>a-b} // total schooled population over 15 by sex
    // build the contingency table of population schooled by age - sex for ages aver 15
    val scolAge15PSex = classical_IPF(nonZero(p_scol.slice(3,7).toArray), nonZero(scol15PSex))
    (0 until age15SexV.sum.toInt).map { _ =>
      // sample age and sex
      val (ageIndex, sex) = toAgeSex(ageSexM.draw(rnd))
      val schooled = ageIndex match {
        case 0 => false // 0-2
          // for ages from 3 to 14, use the probabilities SCOL-AGE/POP-AGE
        case 1 => rnd.nextDouble() < p_scol(0) / p(0) // 3-5 //FIXME here we approximate and use a proba for 2-5 instead of 3-5
        case 2 => rnd.nextDouble() < p_scol(1) / p(1) // 6-10
        case 3 => rnd.nextDouble() < p_scol(2) / p(2) // 11-14
          // for ages above 15, we can use the contingency tables created
        case 4 => rnd.nextDouble() < scolAge15PSex(0)(sex) / decomposedAgeSex(4+sex*15) //p_scol(3) / p(3) // 15-17
        case 5 => rnd.nextDouble() < scolAge15PSex(1)(sex) / decomposedAgeSex(5+sex*15) // p_scol(4) / p(4) // 18-24
        case 6 => rnd.nextDouble() < scolAge15PSex(2)(sex) / decomposedAgeSex(6+sex*15) // p_scol(5) / p(5) // 25-29
        case _ => rnd.nextDouble() < scolAge15PSex(3)(sex) / decomposedAgeSex.slice(7+sex*15, 15+sex*15).sum // p_scol(6) / p(6) // 30-+
      }
      val education = if (schooled) 0 else { // Schol
        if (ageIndex > 3) educationSexM(sex).draw(rnd) + 1 // +1 because the first element is DIPL0
        else 1 // less thant 15yo but not in school => DIPL0
      }
      (ageIndex, sex, education)
    }
  }

  /**
    Generate the population by considering the geometries of the irises.
    This generates a population from the statistics at the iris level and chooses the cell in which the sampled individuals are placed by selecting the cells that intersect the iris geometries.
     */
  def generatePopulation(
    rnd: Random,
    irises: Seq[AreaID],
    geometry: AreaID => Option[MultiPolygon],
    age10: Map[AreaID, Vector[Double]],
    ageSex: Map[AreaID, Vector[Double]],
    schoolAge: Map[AreaID, Vector[Double]],
    educationSex: Map[AreaID, Vector[Vector[Double]]],
    cells: STRtree): Seq[Seq[IndividualFeature]] = {
    val inCRS = CRS.decode("EPSG:2154")
    val outCRS = CRS.decode("EPSG:27572")
    // the transform is used to reproject the Iris data into the same projection as the cells (and to be able to compute their intersection)
    val transform = CRS.findMathTransform(inCRS, outCRS, true)
    // the zipWithIndex is only useful for debug so we might want to remove it at some point...
    irises.zipWithIndex.map { case (id: AreaID, index: Int) =>
      // if ((index % 1000) == 0) Log.log(s"$index")
      // we use getOrElse to handle the absence of an IRIS in the INSEE database
      val age10V = age10.getOrElse(id, Vector())
      val ageSexV = ageSex.getOrElse(id, Vector())
      val schoolAgeV = schoolAge.getOrElse(id, Vector())
      val educationSexV = educationSex.getOrElse(id, Vector())
      val total = ageSexV.sum
      // make sure all relevant distributions exist
      if (total > 0 && age10V.nonEmpty && ageSexV.nonEmpty && schoolAgeV.nonEmpty && educationSexV(0).nonEmpty && educationSexV(1).nonEmpty) {
        val transformedIris = JTS.transform(geometry(id).get, transform)
        val relevantCells = cells.query(transformedIris.getEnvelopeInternal).toArray.toSeq.map(_.asInstanceOf[KMCell]).filter(_._1.intersects(transformedIris))
        val relevantCellsArea = relevantCells.map { cell => ((cell._3, cell._4), cell._2 * cell._1.intersection(transformedIris).getArea / cell._1.getArea) }.toArray.filter { case (_, v) => v > 0 }
        val sampledCells = if (relevantCellsArea.isEmpty) {
          // if no cell with proper population data, sample uniformly in all cells intersecting
          relevantCells.map{cell=>((cell._3, cell._4), 1.0)}.toArray
        } else relevantCellsArea
        sampleIris(age10V, ageSexV, schoolAgeV, educationSexV, rnd) map { sample =>
          // sample a location cell using a multinomial
          def cell = multinomial(sampledCells)(rnd)
          IndividualFeature(ageCategory = sample._1, sex = sample._2, education = sample._3, location = cell)
        }
      } else IndexedSeq()
    }
  }

  def generateFeatures(
    filter: String => Boolean,
    shapeData: ShapeData,
    age10Data:CSVData,
    ageSexData:CSVData,
    schoolAgeData:CSVData,
    educationSexData:CSVData,
    cellsData:CellsData,
    rng: Random,
    pop: (Random, Seq[AreaID], AreaID => Option[MultiPolygon], Map[AreaID, Vector[Double]], Map[AreaID, Vector[Double]], Map[AreaID, Vector[Double]], Map[AreaID, Vector[Vector[Double]]], STRtree) => Seq[Seq[generation.IndividualFeature]]): Try[Seq[IndividualFeature]] =
    for {
      (spatialUnit, geom) <- readGeometry(shapeData, filter)
      age <- readAgeSex(age10Data)
      ageSex <- readAgeSex(ageSexData)
      schoolAge <- readAgeSchool(schoolAgeData)
      educationSex <- readEducationSex(educationSexData)
      cells <- readCells(cellsData)
    } yield pop(rng, spatialUnit, geom, age, ageSex, schoolAge, educationSex, cells).flatten

  /**
  Generate the population without considering the geometries of the irises.
  This generates a population from the statistics at the iris level but by choosing randomly the cell in which the sampled individuals are placed.
  The "geometry" parameter is only given to have the same signature...
   */
  def generatePopulationRandomly(
    rnd: Random,
    irises: Seq[AreaID],
    geometry: AreaID => Option[MultiPolygon],
    age10: Map[AreaID, Vector[Double]],
    ageSex: Map[AreaID, Vector[Double]],
    schoolAge: Map[AreaID, Vector[Double]],
    educationSex: Map[AreaID, Vector[Vector[Double]]],
    cells: STRtree): Seq[Seq[IndividualFeature]] = {
    val env = cells.getRoot.getBounds
    val allCells = cells.query(env.asInstanceOf[Envelope]).toArray.toSeq.map(_.asInstanceOf[KMCell])
    val allCellsArea = allCells.map{cell => {((cell._3, cell._4), cell._2)}}.toArray.filter{case (_,v) => v>0}.map{case (l,_) => (l,1.0)}.toList
    val mn = new Multinomial(allCellsArea)
    irises.map { id =>
      val age10V = age10.getOrElse(id, Vector())
      val ageSexV = ageSex.getOrElse(id, Vector())
      val schoolAgeV = schoolAge.getOrElse(id, Vector())
      val educationSexV = educationSex.getOrElse(id, Vector())
      val total = ageSexV.sum
      if (total > 0 && age10V.nonEmpty && ageSexV.nonEmpty && schoolAgeV.nonEmpty && educationSexV(0).nonEmpty && educationSexV(1).nonEmpty) {
        sampleIris(age10V, ageSexV, schoolAgeV, educationSexV, rnd) map { sample=>
          def cell = mn.draw(rnd)
          IndividualFeature(ageCategory = sample._1, sex = sample._2, education = sample._3, location = cell)
        }
      } else IndexedSeq()
    }
  }

  def generatePoint(geom: MultiPolygon, inOutTransform: MathTransform, outCRS: CoordinateReferenceSystem)(rnd:Random): Point = {
      val sampler = new PolygonSampler(geom, computeTriangles(geom))
      val coordinate = sampler(rnd)
      val transformed = JTS.transform(coordinate, null, inOutTransform)
      JTS.toGeometry(JTS.toDirectPosition(transformed, outCRS))
    }

  def generateEquipment(rnd: Random, eq: Vector[(AreaID, Vector[String])], geometry: AreaID => Option[MultiPolygon], completeArea: MultiPolygon): Seq[Equipment] = {
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

  def generateEquipments(bpe: File, shapeData: ShapeData, filter: String => Boolean, rng: Random): Option[Seq[Equipment]] = {
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

  def sampleInPolygon(polygon: MultiPolygon, rng: Random): Coordinate = {
    val envelope = polygon.getEnvelopeInternal
    var coordinate = new Coordinate(envelope.getMinX + rng.nextDouble() * envelope.getWidth, envelope.getMinY + rng.nextDouble() * envelope.getHeight)
    while (!polygon.contains(polygon.getFactory.createPoint(coordinate))) {
      coordinate = new Coordinate(envelope.getMinX + rng.nextDouble() * envelope.getWidth, envelope.getMinY + rng.nextDouble() * envelope.getHeight)
    }
    coordinate
  }

  def computeTriangles(polygon: MultiPolygon, tolerance: Double = 0.1): Seq[(Double, Polygon)] = {
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

  class PolygonSampler(val polygon: MultiPolygon, val triangles: Seq[(Double, Polygon)]) {
    val totalArea: Double = triangles.last._1

    def apply(rnd: Random): Coordinate = {
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
      new Coordinate(x, y)
    }
  }

  case class Flow(timeSlice: TimeSlice, sex:Sex, age:Age, education:AggregatedEducation, activity: space.Location, residence: space.Location)

  def readFlowsFromEGT(aFile: File, location: Coordinate=>space.Location, slices: Vector[TimeSlice]): Try[List[Flow]] =
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
//          val formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss")
          val formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
          Try{new DateTime(formatter.parse(date))} match {
            case Success(d) => d.toInstant
            case Failure(_) =>
              println(s"Failure for $date")
              new DateTime(new SimpleDateFormat("dd-MM-yyyy").parse(date)).toInstant
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
        //FIXME
        val age = Age.parse(getAge(line)).get
        val dipl = Try{getEduc(line) match {
//          case 0 => Education.Dipl0
//          case 1 => Education.Schol
//          case 2 => Education.BEPC
//          case 3 => Education.BEPC
//          case 4 => Education.CAPBEP
//          case 5 => Education.BAC
//          case 6 => Education.BACP2
//          case 7 => Education.SUP
//          case 8 => Education.BAC
//          case 9 => Education.BAC
          case 1 => AggregatedEducation.Low
          case 2 => AggregatedEducation.Middle
          case 3 => AggregatedEducation.High
        }} match {
          case Success(e)=>e
          case Failure(_) =>
            println(s"Failure for ${line("KEDUC")}")
            AggregatedEducation.Low//Education.Dipl0
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

        timeSlices.map(s => Flow(s, sex, age, dipl, location(new Coordinate(point_x,point_y)), location(new Coordinate(res_x,res_y))))
      }.filter(_.age.from >= 15)
    }
  }
  import MoveMatrix._

  /**
  Add the contribution of a flow to a cell for the given time slice.
   */
  def addFlowToCell(c: Cell, flow: Flow, timeSlice: TimeSlice): Cell = {
    // find out if the flow contributes to the time slice
    val intersection = overlap(flow.timeSlice, timeSlice).toDouble
    // if not, do not modify cell
    if(intersection <= 0.0) c
    else {
      // category of the flow
      val cat = new AggregatedSocialCategory(age = AggregatedAge(flow.age), sex = flow.sex, education = flow.education)
      //AggregatedSocialCategory(SocialCategory(age = flow.age, sex = flow.sex, education = flow.education))
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

  lazy val nightTimeSlice: TimeSlice = timeSlices(0)
  lazy val dayTimeSlice: TimeSlice = timeSlices(1)
  lazy val eveningTimeSlice: TimeSlice = timeSlices(2)

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
      def dx = laea_coord.x - originalBoundingBox.minI
      def dy = laea_coord.y - originalBoundingBox.minJ
      space.cell((dx, dy), gridSize)
    }

    /*
     * Interpolate an entire move matrix.
     *
     * @param moveMatrix the input matrix
     * @return an interpolated matrix
     */
    def interpolate(moveMatrix: MoveMatrix): MoveMatrix = {
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
    }

    def getMovesFromOppositeSex(c: Cell): Cell =
      AggregatedSocialCategory.all.flatMap { cat =>
        val moves = c.get(cat)
        def noSex = c.find { case (sc, _) => sc.age == cat.age && sc.education == cat.education }.map(_._2)
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

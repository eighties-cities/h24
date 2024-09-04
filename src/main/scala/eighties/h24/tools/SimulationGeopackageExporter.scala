package eighties.h24.tools

import eighties.h24.dynamic.MoveMatrix.TimeSlice
import eighties.h24.generation.*
import eighties.h24.simulation.*
import eighties.h24.social.AggregatedSocialCategory
import eighties.h24.space.{BoundingBox, Index, Location, World}
import monocle.*
import monocle.function.all.*
import org.geotools.coverage.grid.GridCoverageFactory
import org.geotools.data.simple.SimpleFeatureWriter
import org.geotools.data.{DataUtilities, DefaultTransaction}
import org.geotools.gce.geotiff.GeoTiffFormat
import org.geotools.geometry.jts.{Geometries, ReferencedEnvelope}
import org.geotools.geopkg.FeatureEntry
import org.geotools.referencing.CRS
import org.locationtech.jts.geom.{Coordinate, GeometryFactory}
import org.opengis.referencing.crs.CoordinateReferenceSystem
import scopt.OParser

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

@main def SimulationGeopackageExporter(args: String*): Unit = {

  object Individual {
    def apply(
      feature: IndividualFeature,
      random: Random,
      stableDestinations: Map[TimeSlice, Location] = Map.empty): Individual = {
      val socialCategory = AggregatedSocialCategory(feature)

      Individual(
        socialCategory = socialCategory,
        feature.location,
        feature.location,
        stableDestinations
      )
    }

    def apply(
      socialCategory: AggregatedSocialCategory,
      home: Location,
      location: Location,
      stableDestinations: Map[TimeSlice, Location]): Individual =
      new Individual(
        AggregatedSocialCategory.shortAggregatedSocialCategoryIso(socialCategory),
        Location.toIndex(home),
        Location.toIndex(location),
        mapOfStableLocationToArray(stableDestinations))

    def arrayMapIso: Iso[Array[(TimeSlice, Int)], Map[TimeSlice, Int]] = monocle.Iso[Array[(TimeSlice, Int)], Map[TimeSlice, Int]](_.toMap)(_.toArray)

    def locationV = Focus[Individual](_.location) composeIso Location.indexIso
    def homeV = Focus[Individual](_.home) composeIso Location.indexIso
    def socialCategoryV = Focus[Individual](_.socialCategory) composeIso AggregatedSocialCategory.shortAggregatedSocialCategoryIso

    def arrayToMapOfStableLocation(array: Array[Short]) =
      (timeSlices zip array).filter(_._2 != Location.noLocationIndex).map{ case (a, b) => a -> Location.fromIndex(b) }.toMap

    def mapOfStableLocationToArray(map: Map[TimeSlice, Location]) = timeSlices.map(t => map.get(t).map(Location.toIndex).getOrElse(Location.noLocationIndex)).toArray

    def timeSlicesMapIso =
      monocle.Iso[Array[Short], Map[TimeSlice, Location]] (arrayToMapOfStableLocation) (mapOfStableLocationToArray)

    def stableDestinationsV = Focus[Individual](_.stableDestinations) composeIso timeSlicesMapIso

    def education = socialCategoryV composeLens Focus[AggregatedSocialCategory](_.education)
    def age = socialCategoryV composeLens Focus[AggregatedSocialCategory](_.age)
    def sex = socialCategoryV composeLens Focus[AggregatedSocialCategory](_.sex)
    def i = Individual.locationV composeLens Focus[(Int, Int)](_._1)
    def j = Individual.locationV composeLens Focus[(Int, Int)](_._2)
  }

  case class Individual(
    socialCategory: Byte,
    home: Short,
    location: Short,
    stableDestinations: Array[Short])

  case class Config(
    population: Option[File] = None,
    moves: Option[File] = None,
    output: Option[File] = None,
    seed: Option[Long] = None)

  val builder = OParser.builder[Config]
  val parser = {
    import builder.*
    OParser.sequence(
      programName("h24 simulator"),
      opt[File]('p', "population")
        .required()
        .action((x, c) => c.copy(population = Some(x)))
        .text("population file generated with h24"),
      opt[File]('m', "moves")
        .required()
        .action((x, c) => c.copy(moves = Some(x)))
        .text("result path where the moves are generated"),
      opt[File]('o', "outputFile")
        .required()
        .action((x, c) => c.copy(output = Some(x)))
        .text("output file"),
      opt[Long]('s', "seed")
        .action((x, c) => c.copy(seed = Some(x)))
        .text("seed for the random number generator")
    )
  }

  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      val seed = config.seed.getOrElse(42L)
      val rng = new Random(seed)

      //  println(Calendar.getInstance.getTime + " loading population")
      val worldFeatures = config.population.get
      // this is ugly but I need the bbox to map to geotiff....
      val tmp = WorldFeature.load(worldFeatures)
      val obbox = tmp.originalBoundingBox
      val bbox = tmp.boundingBox
      val gridSize = tmp.gridSize

      val moves = config.moves.get
      val outFile = config.output.get
      outFile.getParentFile.mkdirs()
      val params = new util.HashMap[String, Object]()
      params.put("dbtype", "geopkg")
      params.put("database", outFile.getPath)
      val specs = "geom:Polygon:srid=3035,pop:Integer," +
          AggregatedSocialCategory.all.map(cat=>"pop_"+AggregatedSocialCategory.toCode(cat).mkString("_")+":Integer").mkString(",")

      val geometryFactory = new GeometryFactory
      import org.geotools.geopkg.GeoPackage
      Log.log("create geopackage")
      val geopkg = new GeoPackage(outFile)
      Log.log("init geopackage")
      geopkg.init()
      Log.log("create geopackage")
      def createWriter(slice: Int): (SimpleFeatureWriter, DefaultTransaction) = {
        val entry = new FeatureEntry
        val featureTypeName = s"Population_$slice"
        val featureType = DataUtilities.createType(featureTypeName, specs)
        entry.setGeometryColumn(featureType.getGeometryDescriptor.getLocalName)
        val geometryType = featureType.getGeometryDescriptor.getType
        val gType = Geometries.getForName(geometryType.getName.getLocalPart)
        entry.setGeometryType(gType)
        val referencedEnvelope = new ReferencedEnvelope(obbox.minI, obbox.maxI + gridSize, obbox.minJ, obbox.maxJ + gridSize, CRS.decode("EPSG:3035"))
        entry.setBounds(referencedEnvelope)
        Log.log(s"create featureentry $featureTypeName")
        geopkg.create(entry, featureType)
        val transaction = new DefaultTransaction()
        (geopkg.writer(entry, true, null, transaction), transaction)
      }
      Log.log("Let's go")
      def buildIndividual(feature: IndividualFeature, random: Random) = Individual(feature, random)
      def exchange(moved: World[Individual], day: Int, slice: Int, rng: Random) = {
        println(s"exchange $day $slice")
        val (writer, transaction) = createWriter(slice)
        val mappedValues = {
          val cellBuffer: Array[Array[ArrayBuffer[Byte]]] = Array.fill(bbox.sideI, bbox.sideJ) { ArrayBuffer[Byte]() }
          for {
            s <- moved.individuals
            (i,j) = Individual.locationV.get(s)
          } cellBuffer(i)(j) += s.socialCategory
          Index[Byte](cellBuffer.map(_.map(_.toArray)), bbox.sideI, bbox.sideJ)
        }.cells.map(_.map(categories => if (categories.nonEmpty) {
          Some(AggregatedSocialCategory.all.map(cat=>categories.count(c=> c == AggregatedSocialCategory.shortAggregatedSocialCategoryIso(cat))))
        } else None))
        for {
          (l, i) <- mappedValues.zipWithIndex
          (valueOption, j) <- l.zipWithIndex
          if valueOption.isDefined
        } {
          val ii = obbox.minI + i * gridSize
          val jj = obbox.minJ + j * gridSize
          val value = valueOption.get
          val (p0, p1, p2, p3) = (new Coordinate(ii, jj), new Coordinate(ii + gridSize, jj), new Coordinate(ii + gridSize, jj + gridSize), new Coordinate(ii, jj + gridSize))
          def polygon = geometryFactory.createPolygon(Array(p0, p1, p2, p3, p0))
          val values = Array[AnyRef](polygon, value.sum.asInstanceOf[AnyRef]) ++ value.map(_.asInstanceOf[AnyRef])
          def simpleFeature = writer.next
          simpleFeature.setAttributes(values)
          writer.write()
        }
        writer.close()
        transaction.commit()
        Log.log("close transaction")
        transaction.close()
        moved
      }


      simulate(
        days = 1,
        population = worldFeatures,
        moves = moves,
        moveType = MoveType.Data,
        buildIndividual,
        exchange,
        Individual.stableDestinationsV,
        Individual.locationV,
        Individual.homeV,
        Individual.socialCategoryV.get,
        rng = rng
      )
      geopkg.close()
    case _ =>
  }

}

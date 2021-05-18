package eighties.h24.tools

import java.io.{File => JFile}
import java.util
import eighties.h24.generation._
import eighties.h24.social.AggregatedSocialCategory.shortAggregatedSocialCategoryIso
import eighties.h24.social.{Age, AggregatedSocialCategory, Education}
import eighties.h24.space
import eighties.h24.space.{Location, generateWorld}
import monocle.PLens
import monocle.macros.Lenses
import org.geotools.data.{DataUtilities, DefaultTransaction}
import org.geotools.geometry.jts.{Geometries, ReferencedEnvelope}
import org.geotools.geopkg.FeatureEntry
import org.geotools.referencing.CRS
import org.locationtech.jts.geom.{Coordinate, GeometryFactory}
import scopt.OParser

import scala.util.Random

object PopulationGeopackageExporter extends App {
  case class Config(
                     population: Option[JFile] = None,
                     output: Option[JFile] = None,
                     cellExport: Boolean = false
                   )

  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName("population export as shapefile"),
      opt[java.io.File]('p', "population")
        .required()
        .action((x, c) => c.copy(population = Some(x)))
        .text("population file generated with h24"),
      opt[java.io.File]('o', "output")
        .required()
        .action((x, c) => c.copy(output = Some(x)))
        .text("output directory"),
      opt[Boolean]('c', "cell")
        .optional()
        .action((x, c) => c.copy(cellExport = x))
        .text("export as cells")
    )
  }
  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      Log.log("load features")
      val res = WorldFeature.load(config.population.get)
      val bbox = res.originalBoundingBox
      val gridSize = res.gridSize
      val outFile = config.output.get
      outFile.getParentFile.mkdirs()
      val params = new util.HashMap[String, Object]()
      params.put("dbtype", "geopkg")
      params.put("database", outFile.getPath)
      val specs = if (config.cellExport) {
        "geom:Polygon:srid=3035,pop:Integer," +
          AggregatedSocialCategory.all.map(cat=>"pop_"+AggregatedSocialCategory.toCode(cat).mkString("_")+":Integer").mkString(",")
//          "pop111:Integer,pop112:Integer,pop113:Integer," +
//          "pop121:Integer,pop122:Integer,pop123:Integer," +
//          "pop131:Integer,pop132:Integer,pop133:Integer," +
//          "pop211:Integer,pop212:Integer,pop213:Integer," +
//          "pop221:Integer,pop222:Integer,pop223:Integer," +
//          "pop231:Integer,pop232:Integer,pop233:Integer"
      } else {
        "geom:Point:srid=3035," +
          "cellX:Integer," +
          "cellY:Integer," +
          "ageCat:String," +
          "sex:String," +
          "education:String"
      }
      val featureTypeName = "Population"
      val featureType = DataUtilities.createType(featureTypeName, specs)
      val geometryFactory = new GeometryFactory
      import org.geotools.geopkg.GeoPackage
      Log.log("create geopackage")
      val geopkg = new GeoPackage(outFile)
      Log.log("init geopackage")
      geopkg.init()
      Log.log("create geopackage")
      val entry = new FeatureEntry
      entry.setGeometryColumn (featureType.getGeometryDescriptor.getLocalName)
      val geometryType = featureType.getGeometryDescriptor.getType
      val gType = Geometries.getForName (geometryType.getName.getLocalPart)
      entry.setGeometryType (gType)
      val referencedEnvelope = new ReferencedEnvelope(bbox.minI, bbox.maxI+gridSize, bbox.minJ, bbox.maxJ+gridSize, CRS.decode("EPSG:3035"))
      Log.log(s"bbox ${bbox.minI} - ${bbox.minJ} - $gridSize")
      entry.setBounds(referencedEnvelope)
      Log.log("create featureentry")
      geopkg.create(entry, featureType)
      val transaction = new DefaultTransaction()
      Log.log("create writer")
      val writer = geopkg.writer(entry,true, null, transaction)
      Log.log("Let's go")
      if (config.cellExport) {
        @Lenses case class Individual(socialCategory: AggregatedSocialCategory, home: Short)
        def homeV: PLens[Individual, Individual, (Int, Int), (Int, Int)] = Individual.home composeIso space.Location.indexIso
        def buildIndividual(feature: IndividualFeature, random: Random) =
          Individual(AggregatedSocialCategory(feature),Location.toIndex(feature.location))
        def world = generateWorld(res.individualFeatures, buildIndividual, homeV, homeV, new util.Random(42))
        val index = space.Index.indexIndividuals(world, homeV.get)
        def getValue(individual: Individual) = individual.socialCategory
        val mappedValues = index.cells.map(_.map(individuals => if (individuals.nonEmpty)
          {
            val categories = individuals.map(getValue)
            Some(AggregatedSocialCategory.all.map(cat=>categories.count(c=> c == cat)))
          } else None))
        for {
          (l, i) <- mappedValues.zipWithIndex
          (valueOption, j) <- l.zipWithIndex
          if valueOption.isDefined
        } {
          val ii = bbox.minI + i * gridSize
          val jj = bbox.minJ + j * gridSize
          val value = valueOption.get
          val (p0, p1, p2, p3)  = (new Coordinate(ii, jj), new Coordinate(ii+gridSize, jj), new Coordinate(ii+gridSize, jj+gridSize), new Coordinate(ii, jj+gridSize))
          def polygon = geometryFactory.createPolygon(Array(p0,p1,p2,p3,p0))
          val values = Array[AnyRef](polygon, value.sum.asInstanceOf[AnyRef]) ++ value.map(_.asInstanceOf[AnyRef])
          def simpleFeature = writer.next
          simpleFeature.setAttributes(values)
          writer.write()
        }
      } else {
        // FIXME the index if for debug only: we might want to remove it later
        for {
          (feature, index) <- res.individualFeatures.zipWithIndex
        } {
          if (index % 100000 == 0) Log.log(s"$index")
          import feature._
          def point = geometryFactory.createPoint(new Coordinate(bbox.minI + location._1.toDouble * gridSize + gridSize / 2, bbox.minJ + location._2.toDouble * gridSize + gridSize / 2))

          val values = Array[AnyRef](
            point,
            location._1.asInstanceOf[AnyRef],
            location._2.asInstanceOf[AnyRef],
            Age(ageCategory).toString.asInstanceOf[AnyRef],
            (if (sex == 0) "Homme" else "Femme").asInstanceOf[AnyRef],
            s"$education - ${Education(education).toString}".asInstanceOf[AnyRef]
          )

          def simpleFeature = writer.next

          simpleFeature.setAttributes(values)
          writer.write()
        }
      }
      Log.log("close writer")
      writer.close()
      Log.log("commit transaction")
      transaction.commit()
      Log.log("close transaction")
      transaction.close()
      Log.log("close geopackage")
      geopkg.close()
      Log.log("all done")
    case _ =>
  }
}

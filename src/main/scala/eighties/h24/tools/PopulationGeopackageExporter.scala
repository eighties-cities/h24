package eighties.h24.tools

import java.io.{File => JFile}
import java.util

import eighties.h24.generation._
import eighties.h24.social.{Age, Education}
import org.geotools.data.{DataUtilities, DefaultTransaction}
import org.geotools.geometry.jts.{Geometries, ReferencedEnvelope}
import org.geotools.geopkg.FeatureEntry
import org.geotools.referencing.CRS
import org.locationtech.jts.geom.{Coordinate, GeometryFactory}
import scopt.OParser

object PopulationGeopackageExporter extends App {
  case class Config(
                     population: Option[JFile] = None,
                     output: Option[JFile] = None
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
        .text("output directory")
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
      val specs = "geom:Point:srid=3035," +
        "cellX:Integer," +
        "cellY:Integer," +
        "ageCat:String," +
        "sex:String," +
        "education:String"
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

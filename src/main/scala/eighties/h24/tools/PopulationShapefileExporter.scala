package eighties.h24.tools

import java.io.{File => JFile}

import org.locationtech.jts.geom.{Coordinate, GeometryFactory}
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.{DataUtilities, Transaction}
import eighties.h24.generation._
import eighties.h24.social.{Age, Education}
import scopt.OParser

@main def PopulationShapefileExporter(args: String*): Unit = {
  
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
      val outFile = config.output.get
      outFile.getParentFile.mkdirs()
      val specs = "geom:Point:srid=3035," +
        "cellX:Integer," +
        "cellY:Integer," +
        "age:String," +
        "sex:String," +
        "education:String"
      val geometryFactory = new GeometryFactory
      val factory = new ShapefileDataStoreFactory
      val dataStore = factory.createDataStore(outFile.toURI.toURL)
      val featureTypeName = "Object"
      val featureType = DataUtilities.createType(featureTypeName, specs)
      dataStore.createSchema(featureType)
      val typeName = dataStore.getTypeNames()(0)
      val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
      val res = WorldFeature.load(config.population.get)
      val bbox = res.originalBoundingBox
      val gridSize = res.gridSize
      for {
        feature <- res.individualFeatures
      } {
        import feature._
        val point = geometryFactory.createPoint(new Coordinate(bbox.minI + location._1.toDouble*gridSize + gridSize/2, bbox.minJ + location._2.toDouble*gridSize + gridSize/2))
        val values = Array[AnyRef](
          point,
          location._1.asInstanceOf[AnyRef],
          location._2.asInstanceOf[AnyRef],
          Age(ageCategory).toString.asInstanceOf[AnyRef],
          (if (sex == 0) "Homme" else "Femme").asInstanceOf[AnyRef],
          s"$education - ${Education(education).toString}".asInstanceOf[AnyRef]
        )
        val simpleFeature = writer.next
        simpleFeature.setAttributes(values)
        writer.write()
      }
      writer.close()
      dataStore.dispose()
    case _ =>
  }
}

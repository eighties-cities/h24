package eighties.h24.tools

import better.files._
import org.locationtech.jts.geom.{Coordinate, GeometryFactory}
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.{DataUtilities, Transaction}
import eighties.h24.generation._

object QGISPopulationGenerator extends App {
  val inputFileName = "population44.bin"
  val outputFileName = "generated-population44.shp"
  val outputPath = File("results")
  outputPath.createDirectories
  val outFile = outputPath / outputFileName
  val specs = "geom:Point:srid=3035," +
              "cellX:Integer," +
              "cellY:Integer," +
              "ageCat:Integer," +
              "sex:Integer," +
              "education:Integer"
  val geometryFactory = new GeometryFactory
  val factory = new ShapefileDataStoreFactory
  val dataStore = factory.createDataStore(outFile.toJava.toURI.toURL)
  val featureTypeName = "Object"
  val featureType = DataUtilities.createType(featureTypeName, specs)
  dataStore.createSchema(featureType)
  val typeName = dataStore.getTypeNames()(0)
  val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
  val res = WorldFeature.load(outputPath / inputFileName)
  val bbox = res.originalBoundingBox
  for {
    (feature, i) <- res.individualFeatures.zipWithIndex
  } {
    import feature._
    val point = geometryFactory.createPoint(new Coordinate(bbox.minI + location._1.toDouble + 500.0, bbox.minJ + location._2.toDouble + 500.0))
    val values = Array[AnyRef](
      point,
      location._1.asInstanceOf[AnyRef],
      location._2.asInstanceOf[AnyRef],
      ageCategory.asInstanceOf[AnyRef],
      sex.asInstanceOf[AnyRef],
      education.asInstanceOf[AnyRef]
    )
    val simpleFeature = writer.next
    simpleFeature.setAttributes(values)
    writer.write()
  }
  writer.close()
  dataStore.dispose()
}

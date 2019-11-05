package eighties.h24.tools

import java.util.Calendar

import better.files.File
import org.locationtech.jts.geom.{Coordinate, GeometryFactory}
import eighties.h24.generation.WorldFeature
import org.geotools.data.DataUtilities
import org.geotools.feature.DefaultFeatureCollection
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.geojson.feature.FeatureJSON
import org.geotools.referencing.CRS

object GeoJSONPopulationGenerator extends App {
  val inputFileName = "population2.bin"
  val outputFileName = "population2.json"
  val path = File("data")
  val outputPath = File("results")
  outputPath.createDirectories
  val outFile = outputPath / outputFileName
  val specs = "geom:Point:srid=3035," +
//              "cellX:Integer," +
//              "cellY:Integer," +
              "ageCat:Integer," +
              "sex:Integer," +
              "education:Integer"
  val crs = CRS.decode( "EPSG:3035" )
  val featureTypeName = "Individual"
  val featureType = DataUtilities.createType(featureTypeName, specs)
  val featureCollection = new DefaultFeatureCollection(featureTypeName, featureType)
  println(Calendar.getInstance.getTime + " Loading population")
  val res = WorldFeature.load((outputPath / inputFileName).toJava)
  println(Calendar.getInstance.getTime + " Converting population")
  val geometryFactory = new GeometryFactory
  for {
    (feature, _) <- res.individualFeatures.zipWithIndex
  } {
    import feature._
    val point = geometryFactory.createPoint(new Coordinate(location._1.toDouble + 500.0, location._2.toDouble + 500.0))
    point.setUserData(crs)
    val values = Array[AnyRef](
      point,
//      location._1.asInstanceOf[AnyRef],
//      location._2.asInstanceOf[AnyRef],
      ageCategory.asInstanceOf[AnyRef],
      sex.asInstanceOf[AnyRef],
      education.asInstanceOf[AnyRef]
    )
    featureCollection.add( SimpleFeatureBuilder.build( featureType, values, null) )
  }
  println(Calendar.getInstance.getTime + " Writing population")
  val io = new FeatureJSON
  println(featureCollection.getBounds.getCoordinateReferenceSystem)
  io.writeFeatureCollection(featureCollection, outFile.toJava)
  println(Calendar.getInstance.getTime + " Finished")
}

package eighties.h24.tools

import java.io.{BufferedWriter, FileWriter}

import better.files._
import eighties.h24.generation
import eighties.h24.generation._
import org.apache.commons.math3.random.JDKRandomGenerator
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.{DataUtilities, Transaction}

object EquipmentGenerator extends App {

  val path = File("data")
  val outputPath = File("results")

  outputPath.createDirectories()

  val outFile = outputPath / "generated-equipment-IDF.shp"
  val outFileCSV = outputPath / "generated-equipment-IDF.csv"

  val specs = "geom:Point:srid=3035,cellX:Integer,cellY:Integer,type:String,quality:String,iris:String"
  val factory = new ShapefileDataStoreFactory
  val dataStore = factory.createDataStore(outFile.toJava.toURI.toURL)
  val featureTypeName = "Object"
  val featureType = DataUtilities.createType(featureTypeName, specs)
  dataStore.createSchema(featureType)
  val typeName = dataStore.getTypeNames()(0)
  val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
  val rng = new JDKRandomGenerator(42)
  val contourIRISFile = path / "CONTOURS-IRIS_FE_IDF.shp"

  val bw = new BufferedWriter(new FileWriter(outFileCSV.toJava))

  val cleanIris = Regex(0, s => s.replaceAll("0000$", "").trim)
  val shpData = ShapeData( contourIRISFile, "DCOMIRIS", Some(Seq(cleanIris)) )
  val bpeFile = path / "bpe14-IDF.csv.lzma"

  for ((feature, i) <- generation.generateEquipments(bpeFile, shpData, _ => true, rng).get.zipWithIndex) {
    import feature._
    def discrete(v:Double) = (v / 1000.0).toInt
    val cellX = discrete(location._1)
    val cellY = discrete(location._2)
    val values = Array[AnyRef](
      point,
      cellX.asInstanceOf[AnyRef],
      cellY.asInstanceOf[AnyRef],
      typeEquipment.asInstanceOf[AnyRef],
      quality.asInstanceOf[AnyRef],
      iris.asInstanceOf[AnyRef])
    val simpleFeature = writer.next
    simpleFeature.setAttributes(values)
    writer.write
    bw.write(s"$cellX, $cellY\n")
  }
  writer.close
  bw.close
}

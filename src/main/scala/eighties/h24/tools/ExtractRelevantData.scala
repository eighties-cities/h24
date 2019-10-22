package eighties.h24.tools

import java.io.File

import better.files
import org.geotools.data.{DataUtilities, Transaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.geotools.factory.CommonFactoryFinder
import org.opengis.feature.simple.SimpleFeature
import scopt.OParser

import scala.util.Try

object ExtractRelevantData extends App {
  case class Config(
                     contour: Option[File] = None,
                     grid: Option[File] = None,
                     infraPopulation: Option[File] = None,
                     infraFormation: Option[File] = None,
                     deps: Option[Seq[Int]] = None,
                     output: Option[File] = None)

  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName("relevant data extractor"),
      opt[File]('c', "contour")
        .required()
        .action((x, c) => c.copy(contour = Some(x)))
        .text("Contour IRIS shapefile"),
      opt[File]('g', "grid")
        .required()
        .action((x, c) => c.copy(grid = Some(x)))
        .text("Grid shapefile"),
//      opt[File]('p', "infraPopulation")
//        .required()
//        .action((x, c) => c.copy(infraPopulation = Some(x)))
//        .text("infraPopulation XLS file"),
//      opt[File]('f', "infraFormation")
//        .required()
//        .action((x, c) => c.copy(infraPopulation = Some(x)))
//        .text("infraFormation XLS file"),
      opt[Seq[Int]]('d', "deps")
        .required()
        .action((x, c) => c.copy(deps = Some(x)))
        .text("deps"),
      opt[File]('o', "output")
        .required()
        .action((x, c) => c.copy(output = Some(x)))
        .text("output directory")
    )
  }
  def filterShape(file: File, filter: SimpleFeature => Boolean, outputFile: File) = {
    val store = new ShapefileDataStore(file.toURI.toURL)
    val factory = new ShapefileDataStoreFactory
    val dataStore = factory.createDataStore(outputFile.toURI.toURL)
    dataStore.createSchema(store.getSchema)
    val typeName = dataStore.getTypeNames()(0)
    val writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT)
    try {
      val reader = store.getFeatureReader
      try {
        Try {
          val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
          featureReader.filter(feature=>filter(feature)).foreach{feature=>writer.next.setAttributes(feature.getAttributes)}
          writer.write()
          writer.close()
          dataStore.dispose()
        }
      } finally reader.close()
    } finally store.dispose()
  }

  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      import eighties.h24.dynamic._
      import better.files._
      // create the output directory
      val outputDirectory = config.output.get
      outputDirectory.mkdirs()
      // extract the relevant data from the input files and put them in the output directory
      val outputContourFile = new java.io.File(outputDirectory, config.contour.get.getName)
      println("outputContourFile = " + outputContourFile)
      filterShape(config.contour.get, (feature:SimpleFeature)=>config.deps.get.contains(feature.getAttribute("DEPCOM").toString.substring(2)), outputContourFile)
      val store = new ShapefileDataStore(outputContourFile.toURI.toURL)
      val ff = CommonFactoryFinder.getFilterFactory2()
      val featureSource = store.getFeatureSource
      val outputGridFile = new java.io.File(outputDirectory, config.grid.get.getName)
      println("outputGridFile = " + outputGridFile)
      filterShape(config.grid.get, (feature:SimpleFeature)=>{!featureSource.getFeatures(ff.intersects(ff.property(store.getSchema.getGeometryDescriptor.getLocalName), ff.literal(feature.getDefaultGeometry))).isEmpty}, outputGridFile)
      store.dispose()
    case _ =>
  }

}

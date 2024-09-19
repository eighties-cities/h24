package eighties.h24.tools

import java.io.{BufferedOutputStream, File, FileOutputStream, IOException, FileNotFoundException }
import java.util.{NoSuchElementException}
import com.github.tototoshi.csv.CSVWriter
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream
import org.apache.poi.ss.usermodel.{Cell, CellType, Row, Sheet}
import org.geotools.data.Transaction
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.geotools.factory.CommonFactoryFinder
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.locationtech.jts.geom.Geometry
import org.opengis.feature.simple.SimpleFeature
import scopt.OParser

import scala.util.Try
import com.github.tototoshi.csv.defaultCSVFormat

@main def ExtractRelevantData(args: String*): Unit = {
  case class Config(
                     contour: Option[File] = None,
                     grid: Option[File] = None,
                     infraPopulation: Option[File] = None,
                     infraFormation: Option[File] = None,
                     deps: Option[Seq[String]] = None,
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
      opt[File]('p', "infraPopulation")
        .required()
        .action((x, c) => c.copy(infraPopulation = Some(x)))
        .text("infraPopulation XLS file"),
      opt[File]('f', "infraFormation")
        .required()
        .action((x, c) => c.copy(infraFormation = Some(x)))
        .text("infraFormation XLS file"),
      opt[Seq[String]]('d', "deps")
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
          featureReader.filter(feature=>filter(feature)).foreach{
            feature=>
              writer.next.setAttributes(feature.getAttributes)
              writer.write()
          }
          writer.close()
          dataStore.dispose()
        }
      } catch {
        case e: IOException => println("Had an IOException trying reading this file")
        case e: FileNotFoundException => println("File Not Found")
        case e: NoSuchElementException => println("No such element")
        case e: IllegalArgumentException => println("Bad Argument")
        case _: Throwable => println("Other Exception")
      }
      finally {
       try {
         reader.close()
       }catch {
         case e:IOException => println("Had an IOException trying closing this reader")
       }
      }
    } catch {
      case e: IOException => println("Had an IOException trying reading getting feature reader")
    }
    finally store.dispose()
  }

  def getStringCellValue(cell: Cell) = {for (cel <- Option(cell)) yield {if (cel.getCellType == CellType.NUMERIC) cel.getNumericCellValue.toString else cel.getStringCellValue}} getOrElse ""
  def filterCSV(input: File, rowFilter: Row => Boolean, output: File): Unit = {
    val bos = new BufferedOutputStream(new FileOutputStream(output))
    val lzmaos = new LZMACompressorOutputStream(bos)
    val writer = CSVWriter.open(lzmaos)
    import org.apache.poi.poifs.filesystem.POIFSFileSystem
    val fs = new POIFSFileSystem(input)
    import org.apache.poi.hssf.usermodel.HSSFWorkbook
    val workbook = new HSSFWorkbook(fs.getRoot, true)
    val sheet: Sheet = workbook.sheetIterator().next
    (for (index <- 0 until 6) yield sheet.getRow(index)).foreach(row=>writer.writeRow((0 until row.getPhysicalNumberOfCells).map(col=>getStringCellValue(row.getCell(col)))))
    (for (index <- 6 until sheet.getLastRowNum) yield sheet.getRow(index)).filter(rowFilter).foreach(row=>writer.writeRow((0 until row.getPhysicalNumberOfCells).map(col=>getStringCellValue(row.getCell(col)))))
    fs.close()
    writer.close()
  }
  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      // create the output directory
      val outputDirectory = config.output.get
      outputDirectory.mkdirs()
      Log.log("deps = " + config.deps.map(_.mkString(",")))
      // extract the relevant data from the input files and put them in the output directory
      val outputContourFile = new java.io.File(outputDirectory, config.contour.get.getName)
      Log.log("outputContourFile = " + outputContourFile)
      filterShape(config.contour.get, (feature:SimpleFeature)=>if (config.deps.isEmpty) true else config.deps.get.contains(feature.getAttribute("DEPCOM").toString.trim.substring(0,2)), outputContourFile)
      val store = new ShapefileDataStore(outputContourFile.toURI.toURL)
      val ff = CommonFactoryFinder.getFilterFactory2()
      val featureSource = store.getFeatureSource
      val l2eCRS = CRS.decode("EPSG:27572")
      val l3CRS = CRS.decode("EPSG:2154")
      val transform = CRS.findMathTransform(l2eCRS, l3CRS, true)
      val outputGridFile = new java.io.File(outputDirectory, config.grid.get.getName)
      Log.log("outputGridFile = " + outputGridFile)
      Log.log("filtershape")
      filterShape(config.grid.get, (feature:SimpleFeature)=>{!featureSource.getFeatures(ff.intersects(ff.property(store.getSchema.getGeometryDescriptor.getLocalName), ff.literal(JTS.transform(feature.getDefaultGeometry.asInstanceOf[Geometry], transform)))).isEmpty}, outputGridFile)
      Log.log("store.dispose start")
      store.dispose()
      Log.log("store.dispose end")
      val outputInfraPopulationFile = new java.io.File(outputDirectory, config.infraPopulation.get.getName.substring(0, config.infraPopulation.get.getName.lastIndexOf("."))+".csv.lzma")
      Log.log("outputInfraPopulationFile = "+outputInfraPopulationFile)
      filterCSV(config.infraPopulation.get, (row:Row)=>if (config.deps.isEmpty) true else config.deps.get.contains(getStringCellValue(row.getCell(3))), outputInfraPopulationFile)
      val outputInfraFormationFile = new java.io.File(outputDirectory, config.infraFormation.get.getName.substring(0, config.infraFormation.get.getName.lastIndexOf("."))+".csv.lzma")
      Log.log("outputInfraFormationFile = "+outputInfraFormationFile)
      filterCSV(config.infraFormation.get, (row:Row)=>if (config.deps.isEmpty) true else config.deps.get.contains(getStringCellValue(row.getCell(3))), outputInfraFormationFile)
      Log.log("done")
    case _ =>
  }

}

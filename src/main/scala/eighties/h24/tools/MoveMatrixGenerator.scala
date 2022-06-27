package eighties.h24.tools

import java.io.File

import eighties.h24.dynamic.MoveMatrix
import eighties.h24.dynamic.MoveMatrix.MoveMatrix
import eighties.h24.generation.{WorldFeature, flowsFromEGT}
import eighties.h24.space.BoundingBox
import org.geotools.data.{DataUtilities, Transaction}
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.locationtech.jts.geom.{Coordinate, GeometryFactory}
import scopt._


@main def MoveMatrixGenerator(args: String*): Unit = {
  
  case class Config(
    egt: Option[File] = None,
    inputSRID: Option[String] = None,
    population: Option[File] = None,
    moves: Option[File] = None)

  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName("move matrix generator"),
      // option -f, --foo
      opt[File]('e', "egt")
        .required()
        .action((x, c) => c.copy(egt = Some(x)))
        .text("presence file compressed with lzma"),
      opt[String]('s', "srid")
        .required()
        .action((x, c) => c.copy(inputSRID = Some(x)))
        .text("presence file compressed with lzma"),
      opt[File]('p', "population")
        .required()
        .action((x, c) => c.copy(population = Some(x)))
        .text("population file generated with h24"),
      opt[File]('m', "moves")
        .required()
        .action((x, c) => c.copy(moves = Some(x)))
        .text("result path where the moves are generated")
    )
  }



  def flowDestinationsFromEGT(bb: BoundingBox, matrix: MoveMatrix, res: File): Unit = {
    val factory = new ShapefileDataStoreFactory
    val geomFactory = new GeometryFactory()
    val dataStoreRes = factory.createDataStore(res.toURI.toURL)
    val featureTypeNameRes = "Res"
    val specsRes = "geom:Point:srid=3035"
    val featureTypeRes = DataUtilities.createType(featureTypeNameRes, specsRes)
    dataStoreRes.createSchema(featureTypeRes)
    val typeNameRes = dataStoreRes.getTypeNames()(0)
    val writerRes = dataStoreRes.getFeatureWriterAppend(typeNameRes, Transaction.AUTO_COMMIT)

    def allMoves =
      for {
        (_, cm) <- matrix
        c <- cm.flatten
        (_, ms) <- c.toSeq
        m <- ms
      } yield m

    allMoves.foreach { v =>
      val loc = MoveMatrix.Move.location.get(v)
      val x = bb.minI + loc._1 * 1000 + 500.0
      val y = bb.minJ + loc._2 * 1000 + 500.0
      val valuesRes = Array[AnyRef](geomFactory.createPoint(new Coordinate(x, y)))
      val simpleFeatureRes = writerRes.next
      simpleFeatureRes.setAttributes(valuesRes)
      writerRes.write()
    }

    writerRes.close()
  }

  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      import eighties.h24.dynamic._
      import better.files._
      def population = WorldFeature.load(config.population.get)
      val bb = population.boundingBox
      Log.log("boundingBox = " + bb.minI + " " + bb.minJ + " " + bb.sideI + " " + bb.sideJ)
      val obb = population.originalBoundingBox
      Log.log("originalBoundingBox = " + obb.minI + " " + obb.minJ + " " + obb.sideI + " " + obb.sideJ)
      val gridSize = population.gridSize
      Log.log("gridSize = " + gridSize)

      val newMatrix = flowsFromEGT(obb, bb, gridSize, config.egt.get.toScala, config.inputSRID.getOrElse("EPSG:2154")).get
      config.moves.get.toScala.parent.createDirectories()
      config.moves.get.delete()

      MoveMatrix.save(newMatrix, config.moves.get)
    case _ =>
  }

}

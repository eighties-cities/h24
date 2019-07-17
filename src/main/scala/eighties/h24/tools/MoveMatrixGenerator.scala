package eighties.h24.tools

import java.io.File
import eighties.h24.generation.{WorldFeature, flowsFromEGT}
import eighties.h24.social.AggregatedSocialCategory
import scopt._

object MoveMatrixGenerator extends App {

  case class Config(
    egt: Option[File] = None,
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
        .text("EGT file compressed with lzma"),
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

  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      import eighties.h24.dynamic._
      import better.files._

      def ls(c: AggregatedSocialCategory) = MoveMatrix.moves { category => category == c } composeLens MoveMatrix.location

      config.moves.get.mkdirs()

      val bb = WorldFeature.load(config.population.get.toScala).originalBoundingBox
      println(bb.minI + " " + bb.minJ + " " + bb.sideI + " " + bb.sideJ)

      val newMatrix = flowsFromEGT(bb, config.egt.get.toScala).get
      MoveMatrix.saveCell(newMatrix, config.moves.get.toScala)

    case _ =>
  }

}

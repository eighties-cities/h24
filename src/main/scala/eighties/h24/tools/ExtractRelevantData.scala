package eighties.h24.tools

import java.io.File

import scopt.OParser

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
      opt[File]('p', "infraPopulation")
        .required()
        .action((x, c) => c.copy(infraPopulation = Some(x)))
        .text("infraPopulation XLS file"),
      opt[File]('f', "infraFormation")
        .required()
        .action((x, c) => c.copy(infraPopulation = Some(x)))
        .text("infraFormation XLS file"),
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

  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      import eighties.h24.dynamic._
      import better.files._
      // extract the relevant data from the input files and put them in the output directory
    case _ =>
  }

}

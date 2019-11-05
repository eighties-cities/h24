package eighties.h24.tools

import java.util.Calendar

import better.files._
import eighties.h24.generation._
import eighties.h24.space._
import scopt.OParser
import Log._

object PopulationGenerator extends App {
  case class Config(
                     contour: Option[java.io.File] = None,
                     grid: Option[java.io.File] = None,
                     gridSize: Option[Int] = None,
                     infraPopulation: Option[java.io.File] = None,
                     infraFormation: Option[java.io.File] = None,
                     randomPop: Option[Boolean] = Some(false),
                     output: Option[java.io.File] = None)

  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName("population generator"),
      opt[java.io.File]('c', "contour")
        .required()
        .action((x, c) => c.copy(contour = Some(x)))
        .text("Contour IRIS shapefile"),
      opt[java.io.File]('g', "grid")
        .required()
        .action((x, c) => c.copy(grid = Some(x)))
        .text("Grid shapefile"),
      opt[Int]('s', "grid size")
        .required()
        .action((x, c) => c.copy(gridSize = Some(x)))
        .text("Grid size"),
      opt[java.io.File]('p', "infraPopulation")
        .required()
        .action((x, c) => c.copy(infraPopulation = Some(x)))
        .text("infraPopulation XLS file"),
      opt[java.io.File]('f', "infraFormation")
        .required()
        .action((x, c) => c.copy(infraFormation = Some(x)))
        .text("infraFormation XLS file"),
      opt[Boolean]('r', "randomPop")
        .action((x, c) => c.copy(randomPop = Some(x)))
        .text("if set to true, generates a random population"),
      opt[java.io.File]('o', "output")
        .required()
        .action((x, c) => c.copy(output = Some(x)))
        .text("output directory")
    )
  }

//  val inputDirectory = File("data") / "44"
//  val contourIRISFile = inputDirectory / "CONTOURS-IRIS.shp"
//  val baseICEvolStructPopFileName = inputDirectory / "base-ic-evol-struct-pop-2012.csv.lzma"
//  val baseICDiplomesFormationPopFileName = inputDirectory / "base-ic-diplomes-formation-2012.csv.lzma"
//  val cellFile = inputDirectory /"R_rfl09_LAEA1000.shp"
  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      val contourIRISFile = config.contour.get.toScala
      val baseICEvolStructPopFileName = config.infraPopulation.get.toScala
      val baseICDiplomesFormationPopFileName = config.infraFormation.get.toScala
      val cellFile = config.grid.get.toScala

      val cleanIris = Regex(0, s => s.replaceAll("0000$", "").trim)

      val popData = CSVData(baseICEvolStructPopFileName, 6, Seq(Slice(34, 40), Slice(44, 50)), 0, CommaFormat, Some(Seq(cleanIris)))
      val eduData = CSVData(baseICDiplomesFormationPopFileName, 6, Seq(Slice(13, 20), Slice(20, 27)), 0, CommaFormat, Some(Seq(cleanIris)))
      val sexData = CSVData(baseICDiplomesFormationPopFileName, 6, Seq(Slice(36, 43), Slice(44, 51)), 0, CommaFormat, Some(Seq(cleanIris)))
      val shpData = ShapeData(contourIRISFile, "DCOMIRIS", Some(Seq(cleanIris))) //changes name after the 2014 update
      val cellsData = CellsData(cellFile, "x_laea", "y_laea", "ind")

      log("Generating population")
//      val randomPop = false
      val features = generateFeatures(
        _ => true,
        shpData,
        popData,
        eduData,
        sexData,
        cellsData,
        new util.Random(42),
        if (config.randomPop.get) generatePopulationRandomly else generatePopulation
      ).get.toArray

      log("Relocating population")

      val gridSize = config.gridSize.get
      val originalBoundingBox = BoundingBox(features, IndividualFeature.location.get)

      def relocate = IndividualFeature.location.modify(BoundingBox.translate(originalBoundingBox)) andThen IndividualFeature.location.modify(scale(gridSize))

      val relocatedFeatures = features.map(relocate)

      log("Saving population")

      val boundingBox = BoundingBox[IndividualFeature](relocatedFeatures, _.location)
      // make sure the output directory structure exists
      config.output.get.getParentFile.mkdirs()

      WorldFeature.save(
        WorldFeature(relocatedFeatures, originalBoundingBox, boundingBox, gridSize),
        config.output.get
      )
    case _ =>
  }
}

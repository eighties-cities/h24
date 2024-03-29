package eighties.h24.tools

import better.files._
import eighties.h24.generation._
import eighties.h24.space._
import scopt.OParser
import Log._
import monocle._

/**
 * Generate a synthetic population.
 */
@main def PopulationGenerator(args: String*): Unit = {
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

  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      val contourIRISFile = config.contour.get.toScala
      val baseICEvolStructPopFileName = config.infraPopulation.get.toScala
      val baseICDiplomesFormationPopFileName = config.infraFormation.get.toScala
      val cellFile = config.grid.get.toScala

      val cleanIris = Regex(0, s => s.replaceAll("0000$", "").trim)
      // Values for P12_H  (P12_H0014	P12_H1529	P12_H3044	P12_H4559	P12_H6074	P12_H75P) and P12_H  (P12_F0014	P12_F1529	P12_F3044	P12_F4559	P12_F6074	P12_F75P)
      val (p12_H, p12_F) = (Slice(34, 40), Slice(44, 50))
      val age15SexData = CSVData(baseICEvolStructPopFileName, 6, Seq(p12_H, p12_F), 0, CommaFormat, Some(Seq(cleanIris)))
      // Values for P12_10  (P12_POP0002	P12_POP0305	P12_POP0610	P12_POP1117	P12_POP1824	P12_POP2539	P12_POP4054	P12_POP5564	P12_POP6579	P12_POP80P)
      val p12_10 = Slice(14, 24)
      val age10Data = CSVData(baseICEvolStructPopFileName, 6, Seq(p12_10), 0, CommaFormat, Some(Seq(cleanIris)))
      // Values for P12_POP (P12_POP0205	P12_POP0610	P12_POP1114	P12_POP1517	P12_POP1824	P12_POP2529	P12_POP30P) and P12_SCOL (P12_SCOL0205	P12_SCOL0610	P12_SCOL1114	P12_SCOL1517	P12_SCOL1824	P12_SCOL2529	P12_SCOL30P)
      val (p12_POP, p12_SCOL) = (Slice(13, 20),Slice(20, 27))
      val schoolAgeData = CSVData(baseICDiplomesFormationPopFileName, 6, Seq(p12_POP, p12_SCOL), 0, CommaFormat, Some(Seq(cleanIris)))
      val educationSexData = CSVData(baseICDiplomesFormationPopFileName, 6, Seq(Slice(36, 43), Slice(44, 51)), 0, CommaFormat, Some(Seq(cleanIris)))
      val shpData = ShapeData(contourIRISFile, "DCOMIRIS", Some(Seq(cleanIris))) //changes name after the 2014 update
      val cellsData = CellsData(cellFile, "x_laea", "y_laea", "ind")

      log("Generating population")
      val gridSize = config.gridSize.get
      def relocateFeatures(f: Array[IndividualFeature]) = {
        val originalBoundingBox = BoundingBox(f, Focus[IndividualFeature](_.location).get)
        log("Relocating population")
        (f.map(Focus[IndividualFeature](_.location).modify(BoundingBox.translate(originalBoundingBox)) andThen Focus[IndividualFeature](_.location).modify(scale(gridSize))), originalBoundingBox)
      }

      if (config.randomPop.get) log("Random population") else log("Observed population")
      val (relocatedFeatures, originalBoundingBox) = relocateFeatures {
        generateFeatures(
          _ => true,
          shpData,
          age10Data,
          age15SexData,
          schoolAgeData,
          educationSexData,
          cellsData,
          new util.Random(42),
          if (config.randomPop.get) {
            generatePopulationRandomly
          } else generatePopulation
        ).get.toArray
      }

      log("Saving population")
      // make sure the output directory structure exists
      config.output.get.getParentFile.mkdirs()

      def createWorldFeature(f: Array[IndividualFeature], obb: BoundingBox) = {
        WorldFeature(f, obb, BoundingBox(f, Focus[IndividualFeature](_.location).get), gridSize)
      }
      WorldFeature.save(
        createWorldFeature(relocatedFeatures, originalBoundingBox),
        config.output.get
      )
    case _ =>
  }
}

package eighties.h24.tools

import java.util.Calendar

import better.files.File
import eighties.h24.generation._
import eighties.h24.space._

object PopulationGenerator extends App {

  val inputDirectory = File("data") / "44"
  val contourIRISFile = inputDirectory / "CONTOURS-IRIS.shp"
  val baseICEvolStructPopFileName = inputDirectory / "base-ic-evol-struct-pop-2012.csv.lzma"
  val baseICDiplomesFormationPopFileName = inputDirectory / "base-ic-diplomes-formation-2012.csv.lzma"
  val cellFile = inputDirectory /"R_rfl09_LAEA1000.shp"

  val cleanIris = Regex(0, s => s.replaceAll("0000$", "").trim)

  val popData = CSVData(baseICEvolStructPopFileName,6,Seq(Slice(34, 40), Slice(44, 50)), 0, CommaFormat, Some(Seq(cleanIris)))
  val eduData = CSVData(baseICDiplomesFormationPopFileName,6,Seq(Slice(13,20), Slice(20,27)),0,CommaFormat, Some(Seq(cleanIris)))
  val sexData = CSVData(baseICDiplomesFormationPopFileName,6,Seq(Slice(36,43), Slice(44,51)),0,CommaFormat, Some(Seq(cleanIris)))
  val shpData = ShapeData(contourIRISFile, "DCOMIRIS", Some(Seq(cleanIris)) )
  val cellsData = CellsData(cellFile,"x_laea","y_laea","ind")

  println(Calendar.getInstance.getTime + " Generating population")
  val randomPop = false
  val features = generateFeatures(
    _ => true,
    shpData,
    popData,
    eduData,
    sexData,
    cellsData,
    new util.Random(42),
    if (randomPop) generatePopulation2 else generatePopulation
  ).get.toArray
  println(Calendar.getInstance.getTime + " Relocating population")
  val originalBoundingBox = BoundingBox(features, IndividualFeature.location.get)
  def relocate = IndividualFeature.location.modify(BoundingBox.translate(originalBoundingBox))
  val relocatedFeatures = features.map(relocate)
  println(Calendar.getInstance.getTime + " Saving population")
  val boundingBox = BoundingBox[IndividualFeature](relocatedFeatures, _.location)
  WorldFeature.save(
    WorldFeature(relocatedFeatures, originalBoundingBox, boundingBox),
    File("results/population44.bin")
//    File("results/population_random.bin")
  )
}

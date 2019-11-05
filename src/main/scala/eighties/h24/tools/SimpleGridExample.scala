package eighties.h24.tools

import java.util.Calendar

import better.files.File
import eighties.h24.generation._
import eighties.h24.space.BoundingBox

object SimpleGridExample extends App {

  val inputDirectory = File("data")
  val contourIRISFile = inputDirectory / "simple_grid_5.shp"
  val baseICEvolStructPopFileName = inputDirectory / "simple_grid_5_struct_pop.csv.lzma"
  val baseICDiplomesFormationPopFileName = inputDirectory / "simple_grid_5_diplome_formation.csv.lzma"
  val cellFile = inputDirectory /"simple_grid_cells.shp"

  //def IrisCleaning(l:Seq[String]) = l.head.

  val cleanIris = Regex(0, s => s.replaceAll("0000$", "").trim)
  val popData = CSVData(baseICEvolStructPopFileName,1,Seq(Slice(1, 13)), 0, SemicolonFormat)
  val eduData = CSVData(baseICDiplomesFormationPopFileName,1,Seq(Slice(1, 14),Slice(14,27)),0, CommaFormat)
  val sexData = CSVData(baseICDiplomesFormationPopFileName,1,Seq(Slice(1, 14),Slice(14,27)),0, CommaFormat)
  val shpData = ShapeData( contourIRISFile, "id", Some(Seq(cleanIris)) )
  val cellsData = CellsData(cellFile,"x","y","ind")

  // Generate population

  println(Calendar.getInstance.getTime + "Generating population")
  val randomPop = true
  val features = generateFeatures(
    _ => true,
    shpData,
    popData,
    eduData,
    sexData,
    cellsData,
    new util.Random(42),
    generatePopulationRandomly,
  ).get.toArray

  // Relocate population

  println(Calendar.getInstance.getTime + " Relocating population")

  val originalBoundingBox = BoundingBox(features, IndividualFeature.location.get)
  def relocate = IndividualFeature.location.modify(BoundingBox.translate(originalBoundingBox))
  val relocatedFeatures = features.map(relocate)

  println(Calendar.getInstance.getTime + " Saving population")
  val boundingBox = BoundingBox[IndividualFeature](relocatedFeatures, _.location)
  WorldFeature.save(
    WorldFeature(relocatedFeatures, originalBoundingBox, boundingBox, 1000),
    //    File("results/population.bin")
    File("results/SimpleGrid10.bin").toJava
  )

}

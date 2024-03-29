package eighties.h24.tools

import java.util.Calendar

import better.files.File
import eighties.h24.generation._
import eighties.h24.space.BoundingBox
import monocle._

@main def SimpleGridExample(args: String*): Unit = {

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

  Log.log(Calendar.getInstance.getTime.toString + "Generating population")
  val randomPop = true
  val features = generateFeatures(
    _ => true,
    shpData,
    popData,// FIXME : I just gave it a vector with the right signature for it to compile...
    popData,
    eduData,
    sexData,
    cellsData,
    new util.Random(42),
    generatePopulationRandomly,
  ).get.toArray

  // Relocate population

  Log.log(Calendar.getInstance.getTime.toString + " Relocating population")

  val gridSize = 1000 // FIXME I just had to put something
  val originalBoundingBox = BoundingBox(features, Focus[IndividualFeature](_.location).get)
  def relocate = Focus[IndividualFeature](_.location).modify(BoundingBox.translate(originalBoundingBox))
  val relocatedFeatures = features.map(relocate)

  Log.log(Calendar.getInstance.getTime.toString + " Saving population")
  val boundingBox = BoundingBox[IndividualFeature](relocatedFeatures, _.location)
  WorldFeature.save(
    WorldFeature(relocatedFeatures, originalBoundingBox, boundingBox, gridSize),
    //    File("results/population.bin")
    File("results/SimpleGrid10.bin").toJava
  )

}

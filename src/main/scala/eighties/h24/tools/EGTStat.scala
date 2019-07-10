package eighties.h24.tools

import better.files._
import eighties.h24.generation
import eighties.h24.generation._
import eighties.h24.social._

object EGTStat extends App {

  import eighties.h24.dynamic._

  def ls(c: AggregatedSocialCategory) =
    MoveMatrix.moves { category => category == c } composeLens MoveMatrix.location

  val path = File("../data/EGT 2010/presence semaine EGT")
  val outputPath = File("results")
  outputPath.createDirectories()

  //val outFileRes = outputPath / "matrix.bin"
  def features = WorldFeature.load(File("results/population.bin"))

  val newMatrix = generation.flowsFromEGT(features.originalBoundingBox, path / "presence_semaine_GLeRoux.csv.lzma").get

  val allMovesValue = MoveMatrix.allMoves.getAll(newMatrix).toVector
  val allLocations = allMovesValue.map(MoveMatrix.Move.location.get)
  val locationCount = allLocations.size

//  val cellsValue = MoveMatrix.cells.getAll(newMatrix).toVector

  //  def unreached =
  //    AggregatedCategory.all.map { ac =>
//      val cat = ls(ac).getAll(newMatrix).groupBy(x => x)
//      ac -> allLocations.count(l => !cat.contains(l)).toDouble / locationCount
//    }

//  unreached.foreach(println)


//  MoveMatrix.cells.getAll(newMatrix).map { cell =>
//    cell.map {case (k, v) => println(k -> v.unzip._2.sum)}
//
//  }


}

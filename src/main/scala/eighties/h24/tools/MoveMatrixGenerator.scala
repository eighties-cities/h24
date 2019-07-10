package eighties.h24.tools

import better.files.File
import eighties.h24.generation.{WorldFeature, flowsFromEGT}
import eighties.h24.social.AggregatedSocialCategory

object MoveMatrixGenerator extends App {

  import eighties.h24.dynamic._

  def ls(c: AggregatedSocialCategory) = MoveMatrix.moves { category => category == c } composeLens MoveMatrix.location

  val path = File("../data/EGT 2010/presence semaine EGT")
  val outputPath = File("results")
  outputPath.createDirectories()

  val bb = WorldFeature.load(File("data/population.bin")).originalBoundingBox
  println(bb.minI + " " + bb.minJ + " " + bb.sideI + " " + bb.sideJ)

  val newMatrix = flowsFromEGT(bb, path / "presence_semaine_GLeRoux.csv.lzma").get
  MoveMatrix.saveCell(newMatrix, outputPath / "moves")
}

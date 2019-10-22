package eighties.h24.tools

import java.io.{File, FileOutputStream}

import boopickle.Default.Pickle
import eighties.h24.dynamic.MoveMatrix.{TimeSlice, cellName}
import eighties.h24.generation.{WorldFeature, flowsFromEGT}
import eighties.h24.social.AggregatedSocialCategory
import eighties.h24.space.Location
import scopt._
import org.mapdb._

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


      //def ls(c: AggregatedSocialCategory) = MoveMatrix.moves { category => category == c } composeLens MoveMatrix.location
      //config.moves.get.mkdirs()

      val bb = WorldFeature.load(config.population.get.toScala).originalBoundingBox
      println(bb.minI + " " + bb.minJ + " " + bb.sideI + " " + bb.sideJ)


      val newMatrix = flowsFromEGT(bb, config.egt.get.toScala).get
      config.moves.get.toScala.parent.createDirectories()

//      def save(timeSlice: TimeSlice, location: (Int, Int), cell: MoveMatrix.Cell) = {
//        import boopickle.Default._
//        val (i, j) = location
//        val os = new FileOutputStream((config.moves.get.toScala / MoveMatrix.cellName(timeSlice, i, j)).toJava)
//        try os.getChannel.write(Pickle.intoBytes[MoveMatrix.Cell](cell))
//        finally os.close()
//      }

      println(newMatrix.head._2.size)

      val db = DBMaker.fileDB(config.moves.get)
        .fileMmapEnableIfSupported() // Only enable mmap on supported platforms
        .fileMmapPreclearDisable()   // Make mmap file faster
        .cleanerHackEnable().make

      val map = db.hashMap("moves").createOrOpen.asInstanceOf[HTreeMap[Any, Any]]


      def save(timeSlice: TimeSlice, location: (Int, Int), cell: MoveMatrix.Cell) = {
        println(s"put $timeSlice $location")
        map.put((location, timeSlice), cell)
      }

      //import eighties.h24.dynamic._
      //save(eighties.h24.generation.timeSlices.head, (0, 0),  Map(AggregatedSocialCategory.all.head -> Array(MoveMatrix.Move(7.toShort, 0.9.toFloat))))

      MoveMatrix.saveCell(newMatrix, save)
      db.close()

    case _ =>
  }

}

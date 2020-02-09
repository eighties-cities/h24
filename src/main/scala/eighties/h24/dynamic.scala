package eighties.h24

import org.locationtech.jts.geom.{Coordinate, Envelope}
import org.locationtech.jts.index.strtree.STRtree
import eighties.h24.tools.random.multinomial
import eighties.h24.social._
import space.{Attraction, Index, Location, World}
import eighties.h24.generation.{LCell, dayTimeSlice, nightTimeSlice}
import monocle.function.all.index
import monocle.macros.Lenses
import monocle.{Lens, Traversal}
import tools.random._

import scala.reflect.ClassTag
import scala.util.Random

object dynamic {

  import MoveMatrix._

  def reachable[T](index: Index[T]) =
    for {
      i <- 0 until index.sideI
      j <- 0 until index.sideJ
      if !index.cells(i)(j).isEmpty
    } yield (i, j)

  def randomMove[I: ClassTag](world: World[I], timeSlice: TimeSlice, ratio: Double, location: Lens[I, Location], stableDestinations: I => Map[TimeSlice, Location], random: Random) = {
    val reach = reachable(Index.indexIndividuals(world, location.get))
    val rSize = reach.size
    def randomLocation = location.modify(l => if(random.nextDouble() < ratio) reach(random.nextInt(rSize)) else l)
    def move(individual: I) = stableLocationOrMove(individual, timeSlice, stableDestinations, location, randomLocation)
    (World.allIndividuals[I] modify move) (world)
  }

  def goBackHome[W, I](world: W, allIndividuals: Traversal[W, I], location: Lens[I, Location], home: I => Location) = {
    def m = (individual: I) => location.set(home(individual))(individual)
    (allIndividuals modify m)(world)
  }

  object MoveMatrix {

    object TimeSlice {
      def fromHours(from: Int, to: Int): TimeSlice = new TimeSlice(from * 60, to * 60)
    }

    case class TimeSlice(from: Int, to: Int) {
      def length = to - from
      override def toString = s"${from}_$to"
    }

    def isIncluded(t1: TimeSlice, t2: TimeSlice) = t1.from >= t2.from && t1.to <= t2.to
    def overlap(t1: TimeSlice, t2: TimeSlice) = {
      if ((t1.to <= t2.from) ||  (t2.to <= t1.from)) 0 // disjoint time slices
      else if(isIncluded(t1, t2)) t1.length
      else if(isIncluded(t2, t1)) t2.length
      else if(t1.from < t2.from && t1.to > t2.from) t1.to - t2.from
      else if(t1.to > t2.to && t1.from < t2.to) t2.to - t1.from
      else throw new RuntimeException("overlap does'nt take into account all configurations")
    }

    def extract(t1: TimeSlice)(t2: TimeSlice) =
      if ((t1.to <= t2.from) ||  (t2.to <= t1.from)) Array[TimeSlice]() // disjoint time slices
      else if(isIncluded(t1, t2)) Array(t1)
      else if(isIncluded(t2, t1)) Array(t2)
      else if(t1.from < t2.from && t1.to > t2.from) Array(TimeSlice(t2.from, t1.to))
      else if(t1.to > t2.to && t1.from < t2.to) Array(TimeSlice(t1.from, t2.to))
      else throw new RuntimeException("split does'nt take into account all configurations")

    def split(t: TimeSlice, timeSlices: Vector[TimeSlice]) = timeSlices.toArray.flatMap(extract(t))

    object Move {
      def location = Move.locationIndex composeIso Location.indexIso
      def apply(location: Location, ratio: Float) = new Move(Location.toIndex(location), ratio)
    }

    object CellMatrix {
      def get(location: Location)(cellMatrix: CellMatrix) = cellMatrix(location._1)(location._2)
      def update(location: Location)(cellMatrix: CellMatrix, value: Cell) = cellMatrix(location._1)(location._2) = value
      def modify(f: (Cell, Location) => Cell)(matrix: CellMatrix): CellMatrix =
        matrix.zipWithIndex.map { case(line, i) => line.zipWithIndex.map { case(c, j) => f(c, (i, j)) } }

    }

    object Cell {
      def empty: Cell = Map()
    }

    type MoveMatrix = Vector[(TimeSlice, CellMatrix)]
    type CellMatrix = Array[Array[Cell]]
    type Cell = Map[AggregatedSocialCategory, Array[Move]]

    type LocatedCell = (TimeSlice, Int, Int) => Cell
    @Lenses case class Move(locationIndex: Short, ratio: Float)

    def cellName(t: TimeSlice, i: Int, j: Int) = s"${t.from}-${t.to}_${i}_$j"

    def getLocatedCells(timeSlice: MoveMatrix) =
      for {
        (ts, matrix) <- timeSlice
        (l, c) <- getLocatedCellsFromCellMatrix(matrix)
      } yield (ts, l, c)

    def getLocatedCellsFromCellMatrix(matrix: CellMatrix) =
      for {
        (line, i) <- matrix.zipWithIndex
        (c, j) <- line.zipWithIndex
      } yield ((i, j), c)


    def cell(location: Location) =
      index[Vector[Vector[Cell]], Int, Vector[Cell]](location._1) composeOptional index(location._2)


//    def cells =
//      each[MoveMatrix, (TimeSlice, CellMatrix)] composeLens
//        second[(TimeSlice, CellMatrix), CellMatrix] composeIso arrayVectorIso composeTraversal each composeIso arrayVectorIso composeTraversal
//        each[Vector[Cell], Cell]

//    def allMoves =
//      cells composeTraversal
//        each[Cell, Array[Move]] composeIso arrayVectorIso composeTraversal each[Vector[Move], Move]
//
//    def moves(category: AggregatedSocialCategory => Boolean) =
//      cells composeTraversal
//        filterIndex[Cell, AggregatedSocialCategory, Array[Move]](category) composeIso arrayVectorIso composeTraversal
//        each[Vector[Move], Move]

//    def movesInNeighborhood(cellMatrix: CellMatrix, category: AggregatedSocialCategory, neighbor: Location => Boolean) =
//      for {
//        (line, i) <- cellMatrix.zipWithIndex
//        (cell, j) <- line.zipWithIndex
//        loc = Location(i,j)
//        if (neighbor(loc))
//        moves <- cell.get(category).toSeq
//      } yield (loc -> moves)

    def movesInNeighborhood(location: Location, category: AggregatedSocialCategory, index: STRtree) =
      for {
        (l,c) <- index.query(new Envelope(location._1 - 10, location._1 + 10, location._2 - 10, location._2 + 10)).toArray.toSeq.map(_.asInstanceOf[LCell])
        moves <- c.get(category)
      } yield l -> moves

    def distance(location1: Location, location2: Location) = new Coordinate(location1._1, location1._2).distance(new Coordinate(location2._1, location2._2))
    def movesInNeighborhoodByCategory(location: Location, index: STRtree) =
      for {
        (l, c) <- index.query(new Envelope(location._1 - 10, location._1 + 10, location._2 - 10, location._2 + 10)).toArray.toSeq.map(_.asInstanceOf[LCell]).filter(x=> distance(x._1, location) <= 10)
      } yield l -> c

    def location = MoveMatrix.Move.location
    def moveRatio = MoveMatrix.Move.ratio

    def noMove(i: Int, j: Int) =
      Vector.tabulate(i, j) {(ii, jj) => AggregatedSocialCategory.all.map { c => c -> Vector((ii, jj) -> 1.0) }.toMap }

    import boopickle.Default._

    implicit val categoryPickler = transformPickler((i: Int) => SocialCategory.all(i))(s => SocialCategory.all.indexOf(s))
    implicit val aggregatedCategoryPickler = transformPickler((i: Int) => AggregatedSocialCategory.all(i))(s => AggregatedSocialCategory.all.indexOf(s))
//
//    def save(moves: MoveMatrix, file: File) = {
//      val os = new FileOutputStream(file.toJava)
//      try os.getChannel.write(Pickle.intoBytes(moves))
//      finally os.close()
//    }

    def saveCell(moves: MoveMatrix, save: (TimeSlice, (Int, Int), MoveMatrix.Cell) => Unit) = {
      getLocatedCells(moves).foreach { case (t, l, cell) => save(t, l, cell) }
    }

    def save(moves: MoveMatrix, file: java.io.File) = {
      file.getParentFile.mkdirs

      import org.mapdb._
      val db = DBMaker.fileDB(file)
        .fileMmapEnableIfSupported() // Only enable mmap on supported platforms
        .fileMmapPreclearDisable()   // Make mmap file faster
        .cleanerHackEnable().make

      val map = db.hashMap("moves").createOrOpen.asInstanceOf[HTreeMap[Any, Any]]

      def save(timeSlice: TimeSlice, location: (Int, Int), cell: Cell) = {
        map.put((location, timeSlice), cell)
      }

      MoveMatrix.saveCell(moves, save)
      db.close()
    }

    def load(file: java.io.File) = {
      import org.mapdb._

      val thread = Thread.currentThread()
      val cl = thread.getContextClassLoader

      thread.setContextClassLoader(TimeSlice.getClass.getClassLoader)
      try {
        val db = DBMaker.fileDB(file)
          .fileMmapEnableIfSupported() // Only enable mmap on supported platforms
          .fileMmapPreclearDisable() // Make mmap file faster
          .cleanerHackEnable()
          .fileLockDisable() // Do not lock file (pb since db not closed)
          .make

        db.hashMap("moves").createOrOpen.asInstanceOf[HTreeMap[(Location, TimeSlice), Cell]]
      } finally thread.setContextClassLoader(cl)
    }

//      val os = new FileOutputStream((file / timeSlicesFileName).toJava)
//      try os.getChannel.write(Pickle.intoBytes(moves.map(_._1)))
//      finally os.close()


    //    def load(file: File) = {
    //      val is = new FileInputStream(file.toJava)
    //      try Unpickle[MoveMatrix].fromBytes(is.getChannel.toMappedByteBuffer)
    //      finally is.close()
    //    }

//    def loadTimeSlices(file: File) = {
//      val is = new FileInputStream((file / timeSlicesFileName).toJava)
//      try Unpickle[Cell].fromBytes(is.getChannel.toMappedByteBuffer)
//      finally is.close()
//    }

//    def loadCell(file: File, t: TimeSlice, i: Int, j: Int) = {
//      val is = new FileInputStream((file / cellName(t, i, j)).toJava)
//      try Unpickle[Cell].fromBytes(is.getChannel.toMappedByteBuffer)
//      finally is.close()
//    }

  }

  def moveFlowDefaultOnOtherSex[I](cellMoves: Cell, individual: I, socialCategory: I => AggregatedSocialCategory) /*moves: MoveMatrix.CellMatrix, individualInCel: Individual)*/ = {
    //val location = Individual.location.get(individual)
    //val cellMoves = moves(location._1)(location._2)
    val aggregatedCategory = socialCategory(individual)
    def myCategory = cellMoves.get(aggregatedCategory)
    def noSex = cellMoves.find { case(c, _) => AggregatedSocialCategory.age.get(c) == AggregatedSocialCategory.age.get(aggregatedCategory) && c.education == aggregatedCategory.education }.map(_._2)
    myCategory orElse noSex
  }

  def sampleDestinationInMoveMatrix[I](cellMoves: Cell, individual: I, socialCategory: I => AggregatedSocialCategory, random: Random) =
    moveFlowDefaultOnOtherSex(cellMoves, individual, socialCategory).flatMap { m =>
      if(m.isEmpty) None else Some(multinomial(m.map{ m => MoveMatrix.Move.location.get(m) -> MoveMatrix.Move.ratio.get(m).toDouble })(random))
    }

  def stableLocationOrMove[I](individual: I, timeSlice: TimeSlice, stableDestinations: I => Map[TimeSlice, Location], location: Lens[I, Location], move: I => I) =
    stableDestinations(individual).get(timeSlice) match {
      case None => move(individual)
      case Some(stableDestination) => location.set(stableDestination)(individual)
    }

  def moveInMoveMatrix[I: ClassTag](world: World[I], locatedCell: LocatedCell, timeSlice: TimeSlice, stableDestination: I => Map[TimeSlice, Location], location: Lens[I, Location], home: I => Location, socialCategory: I => AggregatedSocialCategory, random: Random): World[I] = {
    def sampleMoveInMatrix[J](cellMoves: Cell, location: Lens[J, Location], socialCategory: J => AggregatedSocialCategory)(individual: J) =
      sampleDestinationInMoveMatrix(cellMoves, individual, socialCategory, random) match {
        case Some(destination) => location.set(destination)(individual)
        case None => individual
      }

    val newIndividuals = Array.ofDim[I](world.individuals.length)
    var index = 0

    for {
      (line, i) <- Index.cells.get(Index.indexIndividuals(world, home)).zipWithIndex
      (individuals, j) <- line.zipWithIndex
    } {
      lazy val cell = locatedCell(timeSlice, i, j)
      for {
        individual <- individuals
      } {
        newIndividuals(index) = stableLocationOrMove(individual, timeSlice, stableDestination, location, sampleMoveInMatrix(cell, location, socialCategory))
        index += 1
      }
    }

    World.individuals[I].set(newIndividuals)(world)
  }

  def assignRandomDayLocation[I: ClassTag](world: World[I], locatedCell: LocatedCell, stableDestination: Lens[I, Map[TimeSlice, Location]], location: I => Location, home: I => Location, socialCategory: I => AggregatedSocialCategory, rng: Random) = {
    val newIndividuals = Array.ofDim[I](world.individuals.length)
    var index = 0

    for {
      (line, i) <- Index.cells.get(Index.indexIndividuals(world, location)).zipWithIndex
      (individuals, j) <- line.zipWithIndex
    } {
      val workTimeMovesFromCell = locatedCell(dayTimeSlice, i, j)

      assert(workTimeMovesFromCell != null)

      for {
        individual <- individuals
      } {
         def newIndividual =
           dynamic.sampleDestinationInMoveMatrix(workTimeMovesFromCell, individual, socialCategory, rng) match {
            case Some(d) => stableDestination.modify(_ + (dayTimeSlice -> d))(individual)
            case None => stableDestination.modify(_ + (dayTimeSlice -> home(individual)))(individual)
          }
        newIndividuals(index) = newIndividual
        index += 1
      }
    }

    World.individuals.set(newIndividuals)(world)
  }

  def assignFixNightLocation[I: ClassTag](world: World[I], stableDestination: Lens[I, Map[TimeSlice, Location]], home: I => Location) =
    World.allIndividuals[I].modify { individual => stableDestination.modify(_ + (nightTimeSlice -> home(individual)))(individual) } (world)

  def randomiseLocation[I: ClassTag](world: World[I], location: I => Location, home: Lens[I, Location], random: Random) = {
    val reach = reachable(Index[I](world.individuals.iterator, location, world.sideI, world.sideJ))
    val reachSize = reach.size

    def assign(individual: I): I =
      home.set(reach(random.nextInt(reachSize))) (individual)

    (World.allIndividuals[I] modify assign)(world)
  }

  def generateAttractions[I: ClassTag](world: World[I], proportion: Double, location: I => Location, random: Random) = {
    val reach = reachable(Index.indexIndividuals(world, location))

    def attraction = {
      val location = reach.randomElement(random)
      val education = AggregatedEducation.all.randomElement(random)
      Attraction(location, education)
    }

    def attractions = (0 until (reach.size * proportion).toInt).map(_ => attraction)
    World.attractions.set(attractions.toArray)(world)
  }

  def logistic(l: Double, k: Double, x0: Double)(x: Double) = l / (1.0 +  math.exp(-k * (x - x0)))
}

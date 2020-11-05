package eighties.h24


import eighties.h24.social._
import monocle.Monocle._
import monocle._
import monocle.macros._
import eighties.h24.generation.IndividualFeature
import eighties.h24.tools.lens._

import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import scala.util.Random

object space {
  type Coordinate = (Double, Double)
  type Location = (Int, Int)


  object Location {
    def lowerBound(l1: Location, l2: Location): Location = (math.min(l1._1, l2._1), math.min(l1._2, l2._2))
    def upperBound(l1: Location, l2: Location): Location = (math.max(l1._1, l2._1), math.max(l1._2, l2._2))

    def apply(i:Int, j:Int) = (i,j)

    def fromIndex(i: Short) = {
      val x = i / Byte.MaxValue
      val y = i - (x * Byte.MaxValue)
      (x, y)
    }

    def toIndex(l: Location) = (l._1 * Byte.MaxValue + l._2).toShort

    lazy val indexIso = monocle.Iso[Short, Location](fromIndex)(toIndex)

    def noLocationIndex = Short.MaxValue
  }

  /* définition d'un voisinage*/
  def neighbours(side: Int, location: Location, size: Int) = {
    val (i, j) = location
    for {
      di <- -size to size
      dj <- -size to size
      if di != 0 && dj != 0
      ni = i + di
      nj = j + dj
      if ni >= 0 && nj >= 0 && ni < side && nj < side
    } yield (i + di, j + dj)
  }

  def cell(p: Coordinate, gridSize: Int) = ((p._1 / gridSize).toInt, (p._2 / gridSize).toInt)
  def cellIndex(l: Location, gridSize: Int) = ((l._1 / gridSize).toInt, (l._2 / gridSize).toInt)

  def distance(l1: Location, l2: Location) = {
    val c1 = new org.locationtech.jts.geom.Coordinate(l1._1, l1._2)
    val c2 = new org.locationtech.jts.geom.Coordinate(l2._1, l2._2)
    c1.distance(c2)
  }

  def scale(size: Int)(location: Location): Location = (location._1 / size, location._2 / size)

  object BoundingBox {
    def apply[T](content: Array[T], location: T => Location): BoundingBox = {
      val (minI, minJ) = content.view.map(location).reduceLeft(Location.lowerBound)
      val (maxI, maxJ) = content.view.map(location).reduceLeft(Location.upperBound)
      BoundingBox(minI = minI, maxI = maxI, minJ = minJ, maxJ = maxJ)
    }

    def translate(boundingBox: BoundingBox)(location: Location): Location = (location._1 - boundingBox.minI, location._2 - boundingBox.minJ)

    def allLocations(boundingBox: BoundingBox) =
      for {
        i <- boundingBox.minI to boundingBox.maxI
        j <- boundingBox.minJ to boundingBox.maxJ
      } yield (i, j)
  }

  case class BoundingBox(minI: Int, maxI: Int, minJ: Int, maxJ: Int) {
    def sideI = maxI - minI + 1
    def sideJ = maxJ - minJ + 1
  }

  object World {

    def apply[I: ClassTag](individuals: Array[I], location: Lens[I, Location], home: Lens[I, Location], attractions: Array[Attraction]): World[I] = {
      val boundingBox = BoundingBox(individuals, location.get)

      def relocate =
        home.modify(BoundingBox.translate(boundingBox)) andThen// TODO: add Scale?
          location.modify(BoundingBox.translate(boundingBox))// TODO: add Scale?

      World(
        individuals.map(relocate),
        attractions,
        boundingBox.minI,
        boundingBox.minJ,
        boundingBox.sideI,
        boundingBox.sideJ)
    }

    def individualsVector[I: ClassTag] = World.individuals[I] composeLens arrayToVector[I]
    def allIndividuals[I: ClassTag] = individualsVector[I] composeTraversal each

  }

  //def arrayToVectorLens[A: Manifest] = monocle.Lens[Array[A], Vector[A]](_.toVector)(v => _ => v.toArray)
  //def array2ToVectorLens[A: Manifest] = monocle.Lens[Array[Array[A]], Vector[Vector[A]]](_.toVector.map(_.toVector))(v => _ => v.map(_.toArray).toArray)

  /* Définition d'une classe Grid, composé de vecteurs, de edges et de side*/
  @Lenses case class World[I](individuals: Array[I], attractions: Array[Attraction], originI: Int, originJ: Int, sideI: Int, sideJ: Int)
  @Lenses case class Attraction(location: Location, education: AggregatedEducation)

  object Index {

    def indexIndividuals[I: ClassTag](world: World[I], location: I => Location) =
      Index[I](World.individuals.get(world).iterator, location, world.sideI, world.sideJ)

    def indexAttraction[I](world: World[I]) =
      Index[Attraction](World.attractions.get(world).iterator, Attraction.location.get(_), world.sideI, world.sideJ)

    def apply[T: ClassTag](content: Iterator[T], location: T => Location, sideI: Int, sideJ: Int): Index[T] = {
      val cellBuffer: Array[Array[ArrayBuffer[T]]] = Array.fill(sideI, sideJ) { ArrayBuffer[T]() }

      for                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               {
        s <- content
        (i, j) = location(s)
      } cellBuffer(i)(j) += s

      Index[T](cellBuffer.map(_.map(_.toArray)), sideI, sideJ)
    }

    def getLocatedCells[T, U](index: Index[T]) =
      for {
        (l, i) <- index.cells.view.zipWithIndex
        (c, j) <- l.zipWithIndex
      } yield (c, Location(i, j))

    def allCells[T: ClassTag] = cells[T] composeIso arrayVectorIso[Array[Array[T]]] composeTraversal each composeIso arrayVectorIso[Array[T]] composeTraversal each composeIso arrayVectorIso[T]
    def allIndividuals[T: ClassTag] = allCells[T] composeTraversal each
  }

  @Lenses case class Index[T](cells: Array[Array[Array[T]]], sideI: Int, sideJ: Int)

  def generateWorld[I: ClassTag](
    features: Array[IndividualFeature],
    build: (IndividualFeature, Random) => I,
    location: Lens[I, Location],
    home: Lens[I, Location],
    rng: Random,
    attractions: Array[Attraction] = Array.empty,
    included: IndividualFeature => Boolean = feature => Age(feature.ageCategory) != Age.From0To14 && Age(feature.ageCategory) != Age.Above75) = {

    def individuals: Array[I] = features.filter(included).map(f => build(f, rng))

    /*def equipements =
      for {
        equipments <- generation.generateEquipments(path, rng)
      } yield equipments.flatMap(_.)*/

    //assignWork(workerRatio, generateAttractions(World(individuals.get, Vector.empty), 0.01, rng), rng)

    World(individuals, location, home, attractions)
  }
}

package eighties.h24
/*
 * Copyright (C) 2019 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */


import better.files.File

import eighties.h24.dynamic.MoveMatrix.{LocatedCell, TimeSlice}
import eighties.h24.dynamic.{MoveMatrix, assignFixNightLocation, assignRandomDayLocation}
import eighties.h24.generation.{IndividualFeature, WorldFeature, timeSlices}
import eighties.h24.social.AggregatedSocialCategory
import eighties.h24.space.{BoundingBox, Location, World, generateWorld}
import monocle.Lens

import scala.annotation.tailrec
import scala.reflect.ClassTag
import scala.util.Random

object simulation {

  sealed trait MoveType

  object MoveType {
    case object Data extends MoveType
    case object Random extends MoveType
    case object No extends MoveType
  }

  def simulate[I: ClassTag](
    days: Int,
    population: java.io.File,
    moves: java.io.File,
    moveType: MoveType,
    buildIndividual: (IndividualFeature, Random) => I,
    exchange: (World[I], Int, Random) => World[I],
    stableDestinations: Lens[I, Map[TimeSlice, Location]],
    location: Lens[I, Location],
    home: Lens[I, Location],
    socialCategory: I => AggregatedSocialCategory,
    rng: Random) = {

    def worldFeature = WorldFeature.load(population)
    val bbox = worldFeature.originalBoundingBox

    val moveMatrix = MoveMatrix.load(moves)
    def locatedCell: LocatedCell = (timeSlice: TimeSlice, i: Int, j: Int) => moveMatrix.get((i, j), timeSlice)

    @tailrec def simulateOneDay(world: space.World[I], bb: BoundingBox, timeSlices: List[TimeSlice], locatedCell: LocatedCell, day: Int, slice: Int = 0): World[I] = {
      timeSlices match {
        case Nil => world
        case time :: t =>
          def moved = moveType match {
            case MoveType.Data => dynamic.moveInMoveMatrix(world, locatedCell, time, stableDestinations.get, location, home.get, socialCategory, rng)
            case MoveType.Random => dynamic.randomMove(world, time, 1.0, location, stableDestinations.get, rng)
            case MoveType.No => world
          }

          def convicted = exchange(moved, slice, rng)

          simulateOneDay(convicted, bb, t, locatedCell, day, slice + 1)
      }
    }

    @tailrec def simulateAllDays(day: Int, world: World[I]): World[I] =
      if(day == days) world
      else {
        def newWorld = simulateOneDay(world, bbox, timeSlices.toList, locatedCell, day)
        simulateAllDays(day + 1, newWorld)
      }

    def world = generateWorld(worldFeature.individualFeatures, buildIndividual, location, home, rng)

    def populationWithMoves =
      moveType match {
        case MoveType.Data =>
          val fixedDay =  assignRandomDayLocation(world, locatedCell, stableDestinations, location.get, home.get, socialCategory, rng)
          assignFixNightLocation(
            fixedDay,
            stableDestinations,
            home.get
          )
        case MoveType.Random => assignFixNightLocation(world, stableDestinations, home.get)
        case MoveType.No => assignFixNightLocation(world, stableDestinations, home.get)
      }

    simulateAllDays(0, populationWithMoves)
  }
}

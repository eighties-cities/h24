package eighties.h24.tools

import scala.annotation.tailrec
import scala.util.Random

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
 */
object random {

  class Multinomial[T](values: List[(T, Double)]) {
    @tailrec private def multinomial0(values: List[(T, Double)])(draw: Double): T = {
      values match {
        case Nil ⇒ throw new RuntimeException("List should never be empty.")
        case (bs, _) :: Nil ⇒ bs
        case (bs, weight) :: tail ⇒
          if (draw <= weight) bs
          else multinomial0(tail)(draw - weight)
      }
    }
    val max =  values.map(_._2).sum
    def draw(implicit random: Random): T = {
      val drawn = random.nextDouble() * max
      multinomial0(values)(drawn)
    }
  }

  implicit class SeqDecorator[T](s: Seq[T]) {
    def randomElement(random: Random) = {
      val size = s.size
      s(random.nextInt(size))
    }
  }
  implicit class ArrayDecorator[T](s: Array[T]) {
    def randomElement(random: Random) = {
      val size = s.length
      s(random.nextInt(size))
    }
  }


  def multinomial[T](values: Array[(T, Double)])(implicit random: Random): T = {
    var sum = 0.0
    var previousSum = 0.0
    val cdf = Array.ofDim[Double](values.length + 1)

    for {
      i <- values.indices
    } {
      sum = previousSum + values(i)._2
      cdf(i + 1) = sum
      previousSum = sum
    }

    import collection.Searching._

    val drawn = random.nextDouble() * sum
    val drawnIndex = cdf.search(drawn)

    values(drawnIndex.insertionPoint - 1)._1
  }

}

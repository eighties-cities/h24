package eighties.h24.tools

import monocle.{Iso, Lens}

import scala.reflect.ClassTag

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
object lens {
  def arrayToVector[A: ClassTag]: Lens[Array[A], Vector[A]] = monocle.Lens[Array[A], Vector[A]](_.toVector)(v => _ => v.toArray)
  def array2ToVector[A: ClassTag]: Lens[Array[Array[A]], Vector[Vector[A]]] = monocle.Lens[Array[Array[A]], Vector[Vector[A]]](_.toVector.map(_.toVector))(v => _ => v.map(_.toArray).toArray)
  def arrayVectorIso[A: ClassTag]: Iso[Array[A], Vector[A]] = Iso[Array[A], Vector[A]](_.toVector)(_.toArray)
}

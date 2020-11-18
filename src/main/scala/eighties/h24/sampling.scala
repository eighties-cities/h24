package eighties.h24

import breeze.linalg.{DenseMatrix, DenseVector, convert, inv}
import eighties.h24.social.Age

import scala.annotation.tailrec

object sampling {
  /**
   * A simple 2D classical IPF implementation.
   */
  def classical_IPF(u: Array[Double], v: Array[Double], max_iter: Int = 100, tolerance: Double = 0.1): Array[Array[Double]] = {
    // initialize matrix with ones
    val m_init = Array.fill(u.length, v.length)(1.0)

    @tailrec
    def recursive(m: Array[Array[Double]], iter: Int): Array[Array[Double]] = {
      if (iter == 0) m
      else {
        val s1 = m.map(_.sum)
        val factor1 = u.zip(s1).map { case (a, b) => a / b }
        val res1 = m.zip(factor1).map { case (l, f) => l.map(_ * f) }
        val s2 = v.indices.map(i => res1.map(_ (i)).sum).toArray
        val factor2 = v.zip(s2).map { case (a, b) => a / b }
        val res2 = res1.map(l => l.zip(factor2).map { case (a, b) => a * b })
        val diff = res2.zip(m).map { case (l1, l2) => l1.zip(l2).map { case (a, b) => math.abs(b - a) }.max }.max
        if (diff < tolerance) res2
        else recursive(res2, iter - 1)
      }
    }

    recursive(m_init, max_iter)
  }

  def contains(a1: Age, a2: Age): Boolean = {
    a2.from >= a1.from && (a1.to.isEmpty || (a1.to.isDefined && a2.to.isDefined && a2.to.get <= a1.to.get))
  }

  def splitAndSolve(a1: Array[Age], v1: Array[Double], a2: Array[Age], v2: Array[Double]): (Array[Age], Array[Double]) = {
    val a = a1 ++ a2
    val starts = a.map(_.from).distinct.sorted
    val ages = starts.zipWithIndex.map { case (from, index) => new Age(from, if (index < starts.length - 1) Some(starts(index + 1) - 1) else None) }
    val m: DenseMatrix[Double] = DenseMatrix.tabulate(ages.length, ages.length) { case (i, j) => if (contains(a(i), ages(j))) 1.0 else 0.0 }
    //    println(s"ages = ${ages.mkString(",")}")
    println(s"matrix = $m")
    println(s"inverse matrix = ${convert(inv(m), Int)}")
    (ages, solve(inv(m))(v1 ++ v2.dropRight(1)))
  }

  def solve(m: DenseMatrix[Double])(values: Array[Double]): Array[Double] = {
    (m * DenseVector(values)).toArray
  }

  val inverseMatrix: DenseMatrix[Double] = DenseMatrix(
    (1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    (0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    (0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    (-1.0, -1.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0),
    (1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0),
    (0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    (-1.0, -1.0, -1.0, -1.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0),
    (1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, -1.0, -1.0, 0.0, 0.0, 0.0),
    (-1.0, -1.0, -1.0, -1.0, -1.0, -1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0),
    (1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, -1.0, -1.0, -1.0, 0.0, 0.0),
    (-1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0),
    (1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, -1.0, -1.0, -1.0, -1.0, 0.0),
    (-1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0),
    (1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, -1.0, -1.0, -1.0, -1.0, -1.0),
    (0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
  )

  def nonZero(array: Array[Double]): Array[Double] = array.map(v => if (v <= 0.0) 0.01 else v)

  def decomposeAgeSex(age10V: Vector[Double], age15SexV: Vector[Double]): Seq[Double] = {
    // we sum up the values from H and F but we don't need the last value
    def values = age10V.toArray ++ (0 to 4).map(i => age15SexV(i) + age15SexV(i + 6)).toArray
    val ages = solve(sampling.inverseMatrix)(values)
    (0 to 5).flatMap { index =>
      val marginsSex = Array(age15SexV(index), age15SexV(index + 6))
      val marginsAge = index match {
        case 0 => ages.take(4) // 0-2 + 3-5 + 6-10 + 11-14
        case 1 => ages.slice(4, 7) // 15-17 + 18-24 + 25-29
        case 2 => ages.slice(7, 9) // 30-39 + 40-44
        case 3 => ages.slice(9, 11) // 45-54 + 55-59
        case 4 => ages.slice(11, 13) // 60-64 + 65-74
        case 5 => ages.takeRight(2) // 75-79 + 80+
      }
      val r = classical_IPF(nonZero(marginsAge), nonZero(marginsSex))
      r
    }.transpose.flatten.toVector
  }

}

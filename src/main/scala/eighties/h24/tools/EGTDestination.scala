package eighties.h24.tools

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

import better.files.File
import eighties.h24.dynamic.MoveMatrix.MoveMatrix
import eighties.h24.dynamic._
import eighties.h24.generation.WorldFeature
import eighties.h24.space.BoundingBox
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.{DataUtilities, Transaction}
import org.locationtech.jts.geom.{Coordinate, GeometryFactory}

/**
 */
object EGTDestination extends App {

  def flowDestinationsFromEGT(bb: BoundingBox, matrix: MoveMatrix, res: File) = {
    val factory = new ShapefileDataStoreFactory
    val geomfactory = new GeometryFactory()
    val file = DataUtilities.urlToFile(res.toJava.toURI.toURL)
    val dataStoreRes = factory.createDataStore(res.toJava.toURI.toURL)
    val featureTypeNameRes = "Res"
    val specsRes = "geom:Point:srid=3035"
    val featureTypeRes = DataUtilities.createType(featureTypeNameRes, specsRes)
    dataStoreRes.createSchema(featureTypeRes)
    val typeNameRes = dataStoreRes.getTypeNames()(0)
    val writerRes = dataStoreRes.getFeatureWriterAppend(typeNameRes, Transaction.AUTO_COMMIT)

    def allMoves =
      for {
        (_, cm) <- matrix.toVector
        (_, c) <- cm.toVector
        (_, ms) <- c.toVector
        m <- ms
      } yield m


    allMoves.foreach { v =>
      val loc = MoveMatrix.Move.location.get(v)
      val x = (bb.minI + loc._1) * 1000 + 500.0
      val y = (bb.minJ + loc._2) * 1000 + 500.0
      val valuesRes = Array[AnyRef](geomfactory.createPoint(new Coordinate(x, y)))
      val simpleFeatureRes = writerRes.next
      simpleFeatureRes.setAttributes(valuesRes)
      writerRes.write
    }

    writerRes.close
  }
  val outputPath = File("results")
  outputPath.createDirectories()
  val outFileRes = outputPath / "TEST_DEST_IDW.shp"
  def bb = WorldFeature.load(File("results/population.bin")).originalBoundingBox
  println(bb.minI + " " + bb.minJ)
  val moveTimeLapse = MoveMatrix.load(outputPath / "matrix.bin")

  flowDestinationsFromEGT(bb, moveTimeLapse, outFileRes)
}

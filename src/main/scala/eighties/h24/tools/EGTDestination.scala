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

import eighties.h24.dynamic.MoveMatrix.{Cell, MoveMatrix}
import eighties.h24.dynamic._
import eighties.h24.generation.WorldFeature
import eighties.h24.space.BoundingBox
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.{DataUtilities, Transaction}
import org.locationtech.jts.geom.{Coordinate, GeometryFactory}
import scopt._
import java.io.File

/**
 */
object EGTDestination extends App {

  case class Config(
    population: Option[File] = None,
    moves: Option[File] = None,
    output: Option[File] = None)

  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName("move matrix tester"),
      // option -f, --foo
      opt[File]('p', "population")
        .required()
        .action((x, c) => c.copy(population = Some(x)))
        .text("population file generated with h24"),
      opt[File]('m', "moves")
        .required()
        .action((x, c) => c.copy(moves = Some(x)))
        .text("path of the move file"),
      opt[File]('o', "output")
        .required()
        .action((x, c) => c.copy(output = Some(x)))
        .text("path of the output file")
    )
  }




  OParser.parse(parser, args, Config()) match {
    case Some(c) =>
      import better.files._

      val bb = WorldFeature.load(c.population.get.toScala).originalBoundingBox
      println(bb.minI + " " + bb.minJ)

      val moveTimeLapse = MoveMatrix.load(c.moves.get.toScala)

      val outputPath = c.output.get.toScala
      outputPath.parent.createDirectories()


      def flowDestinationsFromEGT(bb: BoundingBox, res: java.io.File) = {
        val factory = new ShapefileDataStoreFactory
        val geomfactory = new GeometryFactory()

        val dataStoreRes = factory.createDataStore(res.toURI.toURL)
        val featureTypeNameRes = "Res"
        val specsRes = "geom:Point:srid=3035"
        val featureTypeRes = DataUtilities.createType(featureTypeNameRes, specsRes)
        dataStoreRes.createSchema(featureTypeRes)
        val typeNameRes = dataStoreRes.getTypeNames()(0)
        val writerRes = dataStoreRes.getFeatureWriterAppend(typeNameRes, Transaction.AUTO_COMMIT)

        import collection.JavaConverters._

        def allMoves =
          moveTimeLapse.values().iterator().asInstanceOf[java.util.Iterator[Cell]].asScala.flatMap(c => c.values.flatten)

        allMoves.foreach { v =>
          println(s"add move $v")
          val loc = MoveMatrix.Move.location.get(v)
          val x = (bb.minI + loc._1) * 1000 + 500.0
          val y = (bb.minJ + loc._2) * 1000 + 500.0
          val valuesRes = Array[AnyRef](geomfactory.createPoint(new Coordinate(x, y)))
          val simpleFeatureRes = writerRes.next
          simpleFeatureRes.setAttributes(valuesRes)
          writerRes.write
        }

        writerRes.close()
        dataStoreRes.dispose()
      }


      flowDestinationsFromEGT(bb, outputPath.toJava)
    case _ =>
  }


}

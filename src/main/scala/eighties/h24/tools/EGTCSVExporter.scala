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

import java.io.File

import com.github.tototoshi.csv.CSVWriter
import eighties.h24.dynamic.MoveMatrix.{Cell, Move, TimeSlice}
import eighties.h24.dynamic._
import eighties.h24.generation.WorldFeature
import eighties.h24.social.{AggregatedAge, AggregatedEducation, AggregatedSocialCategory, Sex}
import eighties.h24.space.{BoundingBox, Location}
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.{DataUtilities, Transaction}
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.locationtech.jts.geom.{Coordinate, GeometryFactory}
import scopt._

/**
 */
object EGTCSVExporter extends App {

  case class Config(
    population: Option[File] = None,
    moves: Option[File] = None,
    output: Option[File] = None)

  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName("move matrix export as CSV"),
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

      val world = WorldFeature.load(c.population.get.toScala)
      val bb = world.originalBoundingBox
      println("obbox = " + bb.minI + " " + bb.minJ)
      val gridSize = world.gridSize
      println("gridSize = " + gridSize)

      val moveTimeLapse = MoveMatrix.load(c.moves.get.toScala)

      val outputPath = c.output.get.toScala
      outputPath.parent.createDirectories()


      def flowDestinationsFromEGT(bb: BoundingBox, gridSize: Int, res: java.io.File) = {
        import collection.JavaConverters._
        val inCRS = CRS.decode("EPSG:3035")
        val outCRS = CRS.decode("EPSG:4326")
        val transform = CRS.findMathTransform(inCRS, outCRS, true)
        def toLatLon(i: Int, j: Int) = {
          val x = bb.minI + i * gridSize + gridSize / 2
          val y = bb.minJ + j * gridSize + gridSize / 2
          val coord = JTS.transform(new Coordinate(x, y), null, transform)
          Seq(coord.y, coord.x)
        }
//        def toString(category:AggregatedSocialCategory) = s"${AggregatedAge.toCode(category.age)}_${Sex.toCode(category.sex)}_${AggregatedEducation.toCode(category.education)}"
        val writer = CSVWriter.open(res)
        // write header
        writer.writeRow(Seq("origin_id","origin_lat","origin_lon",/*"timeSlice","category",*/"destination_id","destination_lat","destination_lon"/*,"ratio"*/))
        moveTimeLapse.asScala.groupBy(_._1._1).map(x=>(x._1,x._2.flatMap(_._2).flatMap(_._2))).filter{case ((i: Int,j: Int), moves: Iterable[Move])=>(i % 2 == 0)&&(j % 2 == 0)}.foreach{case ((i: Int,j: Int), moves: Iterable[Move]) => {
          val originSeq = Seq(s"origin_${i}_${j}")++toLatLon(i,j)//++Seq(timeSlice.toString)
//          cell.foreach{case (category:AggregatedSocialCategory, moves: Array[Move]) => {
            moves.groupBy(_.locationIndex).map(_._1).map(Location.fromIndex).filter{case (di: Int,dj: Int)=>(di % 2 == 0)&&(dj % 2 == 0)}.foreach {
              case (di: Int, dj: Int) => writer.writeRow(originSeq ++ Seq(s"destination_${di}_${dj}")++toLatLon(di,dj))
            }
//          }}
        }}
        writer.close()
      }
      flowDestinationsFromEGT(bb, gridSize, outputPath.toJava)
    case _ =>
  }
}

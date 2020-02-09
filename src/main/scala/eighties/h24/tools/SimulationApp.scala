package eighties.h24.tools

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File

import eighties.h24.dynamic.MoveMatrix.TimeSlice
import eighties.h24.generation._
import eighties.h24.simulation._
import eighties.h24.social.AggregatedSocialCategory
import eighties.h24.space.{BoundingBox, Index, Location, World}
import monocle.function.all._
import monocle.macros.Lenses
import org.geotools.coverage.grid.GridCoverageFactory
import org.geotools.gce.geotiff.GeoTiffFormat
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.referencing.CRS
import org.opengis.referencing.crs.CoordinateReferenceSystem
import scopt.OParser

import scala.util.Random

object SimulationApp extends App {
  object Individual {
    def apply(
      feature: IndividualFeature,
      random: Random,
      stableDestinations: Map[TimeSlice, Location] = Map.empty): Individual = {
      val socialCategory = AggregatedSocialCategory(feature)

      Individual(
        socialCategory = socialCategory,
        feature.location,
        feature.location,
        stableDestinations
      )
    }

    def apply(
      socialCategory: AggregatedSocialCategory,
      home: Location,
      location: Location,
      stableDestinations: Map[TimeSlice, Location]): Individual =
      new Individual(
        AggregatedSocialCategory.shortAggregatedSocialCategoryIso(socialCategory),
        Location.toIndex(home),
        Location.toIndex(location),
        mapOfStableLocationToArray(stableDestinations))

    def arrayMapIso = monocle.Iso[Array[(TimeSlice, Int)], Map[TimeSlice, Int]](_.toMap)(_.toArray)

    def locationV = Individual.location composeIso Location.indexIso
    def homeV = Individual.home composeIso Location.indexIso
    def socialCategoryV = Individual.socialCategory composeIso AggregatedSocialCategory.shortAggregatedSocialCategoryIso

    def arrayToMapOfStableLocation(array: Array[Short]) =
      (timeSlices zip array).filter(_._2 != Location.noLocationIndex).map{ case (a, b) => a -> Location.fromIndex(b) }.toMap

    def mapOfStableLocationToArray(map: Map[TimeSlice, Location]) = timeSlices.map(t => map.get(t).map(Location.toIndex).getOrElse(Location.noLocationIndex)).toArray

    def timeSlicesMapIso =
      monocle.Iso[Array[Short], Map[TimeSlice, Location]] (arrayToMapOfStableLocation) (mapOfStableLocationToArray)

    def stableDestinationsV = Individual.stableDestinations composeIso timeSlicesMapIso

    def education = socialCategoryV composeLens AggregatedSocialCategory.education
    def age = socialCategoryV composeLens AggregatedSocialCategory.age
    def sex = socialCategoryV composeLens AggregatedSocialCategory.sex
    def i = Individual.locationV composeLens first
    def j = Individual.locationV composeLens second
  }

  @Lenses case class Individual(
    socialCategory: Byte,
    home: Short,
    location: Short,
    stableDestinations: Array[Short])

  def filter(n:Int) = n>=30
  val format = new GeoTiffFormat()
  def mapColorHSV(
    world: World[Individual],
    originalBoundingBox: BoundingBox,
    width: Int,
    height: Int,
    file: File,
    getValue: Individual => Double,
    atHome: Boolean = true,
    textLeft: String= "",
    textRight: String= "",
    filter: Int => Boolean = _=>true,
    aggregator: Array[Double] => Double = v => v.sum / v.length,
    minValue: Double = 0.0,
    maxValue: Double = 1.0,
    cellSize: Int = 1000,
    crs: CoordinateReferenceSystem = CRS.decode("EPSG:3035")) = {
//    val minX = boundingBox.minI
//    val minY = boundingBox.minJ
//    val maxX = minX + width
//    val maxY = minY + height
    val rangeValues = maxValue - minValue
    val pixelSize = 10
    val bufferedImage = new BufferedImage(width*pixelSize, height*pixelSize, BufferedImage.TYPE_INT_ARGB)
    val raster = bufferedImage.getRaster
    val index = Index.indexIndividuals(world, if (atHome) Individual.homeV.get else Individual.locationV.get)
    val hMax = 240.0/360.0
//    def clamp(v: Double) = math.min(math.max(v, 0.0),1.0)
    for {
      (l, i) <- index.cells.zipWithIndex
      (c, j) <- l.zipWithIndex
    } yield {
      val ii = i* pixelSize
      val jj = (height - j - 1) * pixelSize
      val values = c map getValue
      val size = values.length
      if (filter(size)) {
        def aggValue = aggregator(values)
        val color = if (size > 0) {
          val lambda = math.clamp((aggValue - minValue) / rangeValues)
          Array(((1.0 - lambda) * hMax).toFloat, 1.0f, 1.0f, 255.0f)
        } else {
          Array(0.0f, 0.0f, 0.0f, 0.0f)
        }
        val c = Color.getHSBColor(color(0), color(1), color(2))
        val array = Array(c.getRed, c.getGreen, c.getBlue, color(3).toInt)
        val vec = Array.fill(pixelSize * pixelSize)(array).flatten
        raster.setPixels(ii, jj, pixelSize, pixelSize, vec)
      }
    }
    if (!textLeft.isEmpty) {
      val g = bufferedImage.getGraphics
      import java.awt.{Color, Font}
      g.setColor(Color.black)
      g.setFont(new Font("Serif", Font.BOLD, 50))
      g.drawString(s"$textLeft", pixelSize, (height - 1) * pixelSize)
      g.drawString(s"$textRight", (width - 20) * pixelSize, (height - 1) * pixelSize)
      (0 to 4).foreach{v=>
        val d = v.toDouble/4.0
        val c = Color.getHSBColor(((1.0 - d) * 240 / 360).toFloat, 1f, 1f)
        val shift = 5 * pixelSize
        val h = (4 - v) * shift
        g.setColor(c)
        g.fillRect((width - 20) * pixelSize, h, shift, shift)
        g.setColor(Color.black)
        g.drawString(s"$d", (width - 14) * pixelSize, h + shift)
      }
    }
    val referencedEnvelope = new ReferencedEnvelope(originalBoundingBox.minI, originalBoundingBox.maxI+cellSize, originalBoundingBox.minJ, originalBoundingBox.maxJ+cellSize, crs)
    val factory = new GridCoverageFactory
    val coverage = factory.create("GridCoverage", bufferedImage, referencedEnvelope)
    file.getParentFile.mkdirs()
    format.getWriter(file).write(coverage, null)
  }


  case class Config(
    population: Option[File] = None,
    moves: Option[File] = None,
    days: Option[Int] = None,
    output: Option[File] = None,
    seed: Option[Long] = None)

  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName("h24 simulator"),
      opt[File]('p', "population")
        .required()
        .action((x, c) => c.copy(population = Some(x)))
        .text("population file generated with h24"),
      opt[File]('m', "moves")
        .required()
        .action((x, c) => c.copy(moves = Some(x)))
        .text("result path where the moves are generated"),
      opt[Int]('d', "days")
        .required()
        .action((x, c) => c.copy(days = Some(x)))
        .text("number of days to simulate"),
      opt[File]('o', "outputDirectory")
        .required()
        .action((x, c) => c.copy(output = Some(x)))
        .text("output path where the maps are generated"),
      opt[Long]('s', "seed")
        .action((x, c) => c.copy(seed = Some(x)))
        .text("seed for the random number generator")
    )
  }

  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      val seed = config.seed.getOrElse(42L)
      val rng = new Random(seed)

      //  println(Calendar.getInstance.getTime + " loading population")
      val worldFeatures = config.population.get
      // this is ugly but I need the bbox to map to geotiff....
      val bbox = WorldFeature.load(worldFeatures).originalBoundingBox

      val moves = config.moves.get
      val output = config.output.get
      output.mkdirs()
      def buildIndividual(feature: IndividualFeature, random: Random) = Individual(feature, random)
      def exchange(moved: World[Individual], day: Int, slice: Int, rng: Random) = {
        mapColorHSV(moved, bbox, moved.sideI, moved.sideJ, new File(output, s"${day}_${slice}.tif"), (_: Individual) => 1, atHome = false, filter = filter, aggregator = v=>v.sum, minValue = 0.0, maxValue = 5000.0)
        moved
      }


      simulate(
        days = config.days.get,
        population = worldFeatures,
        moves = moves,
        moveType = MoveType.Data,
        buildIndividual,
        exchange,
        Individual.stableDestinationsV,
        Individual.locationV,
        Individual.homeV,
        Individual.socialCategoryV.get,
        rng = rng
      )
    case _ =>
  }

}

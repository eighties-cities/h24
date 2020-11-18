package eighties.h24.tools

import java.util
import java.util.Calendar

import better.files.Dsl.SymbolicOperations
import better.files.File
import eighties.h24.generation._
import eighties.h24.social._
import org.geotools.data.DataStoreFinder
import org.geotools.factory.CommonFactoryFinder
import org.geotools.util.factory.GeoTools
import org.opengis.feature.simple.SimpleFeature

object Comparator extends App {
  val dataDirectory = File("data")
  val generatedData = File("../h24/results_IDF")
  val outputDirectory = File("results_IDF")

  println(Calendar.getInstance.getTime.toString + " loading population")
  val population = generatedData / "population.bin"

  val counts = outputDirectory / "counts.csv"
  counts < "sex,age,edu,nb agents pop synthétique\n"

  def worldFeature: WorldFeature = WorldFeature.load(population.toJava)

  val features = worldFeature.individualFeatures.filter(feature =>
    Age(feature.ageCategory) != Age.From0To2 &&
      Age(feature.ageCategory) != Age.From3To5 &&
      Age(feature.ageCategory) != Age.From6To10 &&
      Age(feature.ageCategory) != Age.From11To14 &&
      Age(feature.ageCategory) != Age.From75To79 &&
      Age(feature.ageCategory) != Age.Above80).map(feature => (
    Sex(feature.sex),
    AggregatedAge(Age(feature.ageCategory)),
    AggregatedEducation(Education(feature.education), Age(feature.ageCategory))))
  for {
    sex <- Sex.all
    age <- AggregatedAge.all
    edu <- AggregatedEducation.all
  } {
    counts << s"${Sex.toCode(sex)},${AggregatedAge.toCode(age)},${AggregatedEducation.toCode(edu)},${features.count(feature => feature._1 == sex && feature._2 == age && feature._3 == edu)}"
  }
}

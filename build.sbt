organization := "eighties"

name := "h24"
version := "1.0-SNAPSHOT"
scalaVersion := "3.7.0"

val monocleVersion = "3.1.0"
val geotoolsVersion = "24.0"
val breezeVersion = "2.0"

resolvers ++= Seq(
  "osgeo" at "https://repo.osgeo.org/repository/release/",
  "geosolutions" at "https://maven.geo-solutions.it/",
  "geotoolkit" at "https://maven.geotoolkit.org/",
  "Boundless" at "https://repo.boundlessgeo.com/main",
  "Altassian" at "https://maven.artifacts.atlassian.com/"
)

libraryDependencies ++= Seq (
  "org.mapdb" % "mapdb" % "3.0.8",
  "com.github.scopt" %% "scopt" % "4.0.1",
  "dev.optics"  %%  "monocle-core"    % monocleVersion,
  "dev.optics"  %%  "monocle-macro"   % monocleVersion,
  "org.geotools" % "gt-referencing" % geotoolsVersion exclude("javax.media", "jai_core"),
  "org.geotools" % "gt-shapefile" % geotoolsVersion exclude("javax.media", "jai_core"),
  "org.geotools" % "gt-epsg-wkt" % geotoolsVersion exclude("javax.media", "jai_core"),
  "org.geotools" % "gt-cql" % geotoolsVersion exclude("javax.media", "jai_core"),
  "org.geotools" % "gt-geotiff" % geotoolsVersion exclude("javax.media", "jai_core"),
  "org.geotools" % "gt-image" % geotoolsVersion exclude("javax.media", "jai_core"),
  "org.geotools" % "gt-coverage" % geotoolsVersion exclude("javax.media", "jai_core"),
  "org.geotools" % "gt-geojson" % geotoolsVersion exclude("javax.media", "jai_core"),
  "org.geotools" % "gt-geopkg" % geotoolsVersion exclude("javax.media", "jai_core"),
  "com.github.tototoshi" %% "scala-csv" % "1.3.10",
  "org.apache.commons" % "commons-compress" % "1.19",
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "org.tukaani" % "xz" % "1.6",
  "com.github.pathikrit" %% "better-files" % "3.9.2",
  "org.scalaz" %% "scalaz-core" % "7.3.6",
  "org.scalanlp" %% "breeze" % breezeVersion,
  "org.scalanlp" %% "breeze-natives" % breezeVersion,
  "org.typelevel"  %% "squants"  % "1.8.3",
  "joda-time" % "joda-time" % "2.9.7",
  "io.suzaku" %% "boopickle" % "1.4.0",

  "javax.media" % "jai-core" % "1.1.3", // from "https://github.com/jai-imageio/jai-imageio-core/releases/download/jai-imageio-core-1.4.0/jai-imageio-core-1.4.0.jar", //https://repo.osgeo.org/repository/release/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar",
  "javax.media" % "jai_codec" % "1.1.3",
  "javax.media" % "jai_imageio" % "1.1",
  "org.apache.poi" % "poi-ooxml"  % "4.1.1",
)

excludeDependencies += ExclusionRule("org.typelevel", "cats-kernel_2.13")

scalacOptions ++= Seq("-release:11", "-language:postfixOps")


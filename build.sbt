organization := "eighties"

name := "h24"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.6"

crossScalaVersions := Seq("2.13.6")

val monocleVersion = "2.0.1"

val geotoolsVersion = "24.0"

val breezeVersion = "1.1"

resolvers ++= Seq(
  "osgeo" at "https://repo.osgeo.org/repository/release/",
  "geosolutions" at "https://maven.geo-solutions.it/",
  "geotoolkit" at "https://maven.geotoolkit.org/",
  "Boundless" at "https://repo.boundlessgeo.com/main"
)

libraryDependencies ++= Seq (
  "org.mapdb" % "mapdb" % "3.0.8",
  "com.github.scopt" %% "scopt" % "4.0.0-RC2",
  "com.github.julien-truffaut"  %%  "monocle-core"    % monocleVersion,
  "com.github.julien-truffaut"  %%  "monocle-generic" % monocleVersion,
  "com.github.julien-truffaut"  %%  "monocle-macro"   % monocleVersion,
  "org.geotools" % "gt-referencing" % geotoolsVersion,
  "org.geotools" % "gt-shapefile" % geotoolsVersion,
  "org.geotools" % "gt-epsg-wkt" % geotoolsVersion,
  "org.geotools" % "gt-cql" % geotoolsVersion,
  "org.geotools" % "gt-geotiff" % geotoolsVersion,
  "org.geotools" % "gt-image" % geotoolsVersion,
  "org.geotools" % "gt-coverage" % geotoolsVersion,
  "org.geotools" % "gt-geojson" % geotoolsVersion,
  "org.geotools" % "gt-geopkg" % geotoolsVersion,
  "com.github.tototoshi" %% "scala-csv" % "1.3.6",
  "org.apache.commons" % "commons-compress" % "1.19",
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "org.tukaani" % "xz" % "1.6",
  "com.github.pathikrit" %% "better-files" % "3.8.0",
  "org.scalaz" %% "scalaz-core" % "7.3.2",
  "org.scalanlp" %% "breeze" % breezeVersion,
  "org.scalanlp" %% "breeze-natives" % breezeVersion,
  "org.typelevel"  %% "squants"  % "1.6.0",
  "joda-time" % "joda-time" % "2.9.7",
  "io.suzaku" %% "boopickle" % "1.3.1",
  "javax.media" % "jai-core" % "1.1.3" from "https://repo.osgeo.org/repository/release/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar",
  "javax.media" % "jai_codec" % "1.1.3",
  "javax.media" % "jai_imageio" % "1.1",
  "org.apache.poi" % "poi-ooxml"  % "4.1.1"
)
 

scalacOptions ++= Seq("-target:jvm-1.8", "-language:postfixOps", "-Ymacro-annotations")



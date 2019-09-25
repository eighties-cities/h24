organization := "eighties"

name := "h24"

version := "1.0-SNAPSHOT"

scalaVersion := "2.12.10"

val monocleVersion = "1.5.0"

val geotoolsVersion = "21.0"

val breezeVersion = "0.13.2"

resolvers ++= Seq(
  "osgeo" at "http://download.osgeo.org/webdav/geotools/",
  "geosolutions" at "http://maven.geo-solutions.it/",
  "geotoolkit" at "http://maven.geotoolkit.org/",
)


libraryDependencies ++= Seq (
  "org.mapdb" % "mapdb" % "3.0.7",
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
  "com.github.tototoshi" %% "scala-csv" % "1.3.4",
  "org.apache.commons" % "commons-compress" % "1.11",
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "org.tukaani" % "xz" % "1.6",
  "com.github.pathikrit" %% "better-files" % "2.17.1",
  "org.scalanlp" %% "breeze" % breezeVersion,
  "org.scalanlp" %% "breeze-natives" % breezeVersion,
  "org.typelevel"  %% "squants"  % "1.1.0",
  "joda-time" % "joda-time" % "2.9.7",
  "io.suzaku" %% "boopickle" % "1.2.6",
  "javax.media" % "jai_core" % "1.1.3" from "http://download.osgeo.org/webdav/geotools/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar",
  "javax.media" % "jai_codec" % "1.1.3",
  "javax.media" % "jai_imageio" % "1.1"
)
 
addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

enablePlugins(SbtOsgi)

updateOptions := updateOptions.value.withGigahorse(false)

osgiSettings

OsgiKeys.exportPackage := Seq("eighties.*;-split-package:=merge-first")

OsgiKeys.importPackage := Seq("*;resolution:=optional")

OsgiKeys.privatePackage := Seq("!scala.*,!java.*,*")

OsgiKeys.requireCapability := """osgi.ee; osgi.ee="JavaSE";version:List="1.8,1.9""""

OsgiKeys.additionalHeaders :=  Map(
  "Specification-Title" -> "Spec Title",
  "Specification-Version" -> "Spec Version",
  "Specification-Vendor" -> "Eighties",
  "Implementation-Title" -> "Impl Title",
  "Implementation-Version" -> "Impl Version",
  "Implementation-Vendor" -> "Eighties"
)

OsgiKeys.embeddedJars := (Keys.externalDependencyClasspath in Compile).value map (_.data) filter (f=> (f.getName startsWith "gt-"))


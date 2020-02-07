organization := "eighties"

name := "h24"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.1"

crossScalaVersions := Seq("2.12.10", "2.13.1")

val monocleVersion = "2.0.1"

val geotoolsVersion = "22.0"

//val breezeVersion = "1.0"

resolvers ++= Seq(
  "osgeo" at "https://download.osgeo.org/webdav/geotools/",
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
  "com.github.tototoshi" %% "scala-csv" % "1.3.6",
  "org.apache.commons" % "commons-compress" % "1.19",
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "org.tukaani" % "xz" % "1.6",
  "com.github.pathikrit" %% "better-files" % "3.8.0",
  "org.scalaz" %% "scalaz-core" % "7.2.30",
  //"org.scalanlp" %% "breeze" % breezeVersion,
  //"org.scalanlp" %% "breeze-natives" % breezeVersion,
  "org.typelevel"  %% "squants"  % "1.6.0",
  "joda-time" % "joda-time" % "2.9.7",
  "io.suzaku" %% "boopickle" % "1.3.1",
  //"javax.media" % "jai_core" % "1.1.3" from ("http://download.osgeo.org/webdav/geotools/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar", allowInsecureProtocol = true),
  //"javax.media" % "jai_codec" % "1.1.3",
  //"javax.media" % "jai_imageio" % "1.1",
  "org.apache.poi" % "poi-ooxml"  % "4.1.1",
  //"org.scala-lang.modules" %% "scala-collection-compat" % "2.1.3"
)
 
//addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)


scalacOptions ++= {
  scalaBinaryVersion.value match {
    case x if x.startsWith("2.12") => Seq("-target:jvm-1.8")
    case _ => Seq("-target:jvm-1.8", "-language:postfixOps", "-Ymacro-annotations")
  }
}

libraryDependencies ++= {
  scalaBinaryVersion.value match {
    case x if x.startsWith("2.12") => Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full))
    case _ => Seq()
  }
}

/*enablePlugins(SbtOsgi)

//updateOptions := updateOptions.value.withGigahorse(false)

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

*/

// do not use coursier at the moment: it fails on jai_core for some reason
useCoursier := false

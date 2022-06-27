package eighties.h24.tools

import java.util

import better.files._
import eighties.h24.generation._
import eighties.h24.social.{Age, Education}
import eighties.h24.tools.Log._
import eighties.h24.tools.random.multinomial
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.data.{DataUtilities, DefaultTransaction}
import org.geotools.geometry.jts.{Geometries, ReferencedEnvelope}
import org.geotools.geopkg.FeatureEntry
import org.geotools.referencing.CRS
import org.locationtech.jts.geom.{GeometryFactory, MultiPolygon}
import org.locationtech.jts.index.strtree.STRtree
import scopt.OParser

import scala.util.Try

/**
 * Generate a synthetic population in buildings and export it as a geopackage file.
 */
 @main def PopulationInBuildingsGenerator(args: String*): Unit = {
  
  case class Config(
                     contour: Option[java.io.File] = None,
                     building: Option[java.io.File] = None,
                     infraPopulation: Option[java.io.File] = None,
                     infraFormation: Option[java.io.File] = None,
                     output: Option[java.io.File] = None)

  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName("population generator"),
      opt[java.io.File]('c', "contour")
        .required()
        .action((x, c) => c.copy(contour = Some(x)))
        .text("Contour IRIS shapefile"),
      opt[java.io.File]('b', "building")
        .required()
        .action((x, c) => c.copy(building = Some(x)))
        .text("Building shapefile"),
      opt[java.io.File]('p', "infraPopulation")
        .required()
        .action((x, c) => c.copy(infraPopulation = Some(x)))
        .text("infraPopulation XLS file"),
      opt[java.io.File]('f', "infraFormation")
        .required()
        .action((x, c) => c.copy(infraFormation = Some(x)))
        .text("infraFormation XLS file"),
      opt[java.io.File]('o', "output")
        .required()
        .action((x, c) => c.copy(output = Some(x)))
        .text("output directory")
    )
  }

  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      val contourIRISFile = config.contour.get.toScala
      val baseICEvolStructPopFileName = config.infraPopulation.get.toScala
      val baseICDiplomesFormationPopFileName = config.infraFormation.get.toScala
      val buildingFile = config.building.get.toScala

      val cleanIris = Regex(0, s => s.replaceAll("0000$", "").trim)
      // Values for P12_H  (P12_H0014	P12_H1529	P12_H3044	P12_H4559	P12_H6074	P12_H75P) and P12_H  (P12_F0014	P12_F1529	P12_F3044	P12_F4559	P12_F6074	P12_F75P)
      val (p12_H, p12_F) = (Slice(34, 40), Slice(44, 50))
      val age15SexData = CSVData(baseICEvolStructPopFileName, 6, Seq(p12_H, p12_F), 0, CommaFormat, Some(Seq(cleanIris)))
      // Values for P12_10  (P12_POP0002	P12_POP0305	P12_POP0610	P12_POP1117	P12_POP1824	P12_POP2539	P12_POP4054	P12_POP5564	P12_POP6579	P12_POP80P)
      val p12_10 = Slice(14, 24)
      val age10Data = CSVData(baseICEvolStructPopFileName, 6, Seq(p12_10), 0, CommaFormat, Some(Seq(cleanIris)))
      // Values for P12_POP (P12_POP0205	P12_POP0610	P12_POP1114	P12_POP1517	P12_POP1824	P12_POP2529	P12_POP30P) and P12_SCOL (P12_SCOL0205	P12_SCOL0610	P12_SCOL1114	P12_SCOL1517	P12_SCOL1824	P12_SCOL2529	P12_SCOL30P)
      val (p12_POP, p12_SCOL) = (Slice(13, 20),Slice(20, 27))
      val schoolAgeData = CSVData(baseICDiplomesFormationPopFileName, 6, Seq(p12_POP, p12_SCOL), 0, CommaFormat, Some(Seq(cleanIris)))
      val educationSexData = CSVData(baseICDiplomesFormationPopFileName, 6, Seq(Slice(36, 43), Slice(44, 51)), 0, CommaFormat, Some(Seq(cleanIris)))
      val shpData = ShapeData(contourIRISFile, "DCOMIRIS", Some(Seq(cleanIris))) //changes name after the 2014 update

      def readBuildings(buildingFile: File) = {
        val store = new ShapefileDataStore(buildingFile.toJava.toURI.toURL)
        try {
          val reader = store.getFeatureReader
          try {
            Try {
              val featureReader = Iterator.continually(reader.next).takeWhile(_ => reader.hasNext)
              val index = new STRtree()
              featureReader.foreach { feature =>
                try {
                  val id = feature.getAttribute("ID").asInstanceOf[String]
                  val geom = feature.getDefaultGeometry.asInstanceOf[MultiPolygon]
                  val volume = feature.getAttribute("HAUTEUR").asInstanceOf[Int] * geom.getArea
                  index.insert(geom.getEnvelopeInternal, (geom, id, volume))
                } catch {
                  case e: Exception => e.printStackTrace()
                }
              }
              index
            }
          } finally reader.close()
        } finally store.dispose()
      }

      val buildings = readBuildings(buildingFile).get
      log("Generating population")
      val rnd = new util.Random(42)
      val referencedEnvelope = new ReferencedEnvelope(CRS.decode("EPSG:2154"))
      val geometryFactory = new GeometryFactory
      val features = for {
          (spatialUnit, geometry) <- readGeometry(shpData, _ => true)
          age10 <- readAgeSex(age10Data)
          ageSex <- readAgeSex(age15SexData)
          schoolAge <- readAgeSchool(schoolAgeData)
          educationSex <- readEducationSex(educationSexData)
        } yield {
        // the zipWithIndex is only useful for debug so we might want to remove it at some point...
        spatialUnit.zipWithIndex.flatMap { case (id: AreaID, index: Int) =>
          // we use getOrElse to handle the absence of an IRIS in the INSEE database
          val age10V = age10.getOrElse(id, Vector())
          val ageSexV = ageSex.getOrElse(id, Vector())
          val schoolAgeV = schoolAge.getOrElse(id, Vector())
          val educationSexV = educationSex.getOrElse(id, Vector())
          val total = ageSexV.sum
          if ((index % 100) == 0)
            Log.log(s"$index with $total individuals")
          // make sure all relevant distributions exist
          if (total > 0 && ageSexV.nonEmpty && schoolAgeV.nonEmpty && educationSexV(0).nonEmpty && educationSexV(1).nonEmpty) {
            val geom = geometry(id).get
            val relevantBuildings = buildings.query(geom.getEnvelopeInternal).toArray.map(_.asInstanceOf[(MultiPolygon, String, Double)]).filter(_._1.intersects(geom))
            val relevantCellsAreaBuildingVolumes = relevantBuildings.map { building => ((building._1, building._2), building._3) }.filter { case (_, v) => v > 0 }
            val sampledCells = if (relevantCellsAreaBuildingVolumes.isEmpty) {
              // if no building, sample uniformly in iris
              if (relevantBuildings.isEmpty) {
                Array(((geom, ""), 1.0))
                // if no building with proper volume, sample uniformly in all cells intersecting
              } else relevantBuildings.map { building => ((building._1, building._2), 1.0) }
            } else relevantCellsAreaBuildingVolumes
            sampleIris(age10V, ageSexV, schoolAgeV, educationSexV, rnd) map { sample =>
              def building = multinomial(sampledCells)(rnd)
              def coordinate = sampleInPolygon(building._1, rnd)
              def pointInPolygon = geometryFactory.createPoint(coordinate)
              referencedEnvelope.expandToInclude(coordinate)
              (sample._1, sample._2, sample._3, pointInPolygon)
            }
          } else IndexedSeq()
        }
      }
      log("Saving population")
      val outFile = config.output.get
      // make sure the output directory structure exists
      outFile.getParentFile.mkdirs()
      val params = new util.HashMap[String, Object]()
      params.put("dbtype", "geopkg")
      params.put("database", outFile.getPath)
      val specs = "geom:Point:srid=2154," +
        "age:String," +
        "sex:String," +
        "education:String"
      val featureTypeName = "PopulationInBuildings"
      val featureType = DataUtilities.createType(featureTypeName, specs)
      import org.geotools.geopkg.GeoPackage
      Log.log("create geopackage")
      val geopkg = new GeoPackage(outFile)
      Log.log("init geopackage")
      geopkg.init()
      Log.log("create geopackage")
      val entry = new FeatureEntry
      entry.setGeometryColumn (featureType.getGeometryDescriptor.getLocalName)
      val geometryType = featureType.getGeometryDescriptor.getType
      val gType = Geometries.getForName (geometryType.getName.getLocalPart)
      entry.setGeometryType (gType)
      entry.setBounds(referencedEnvelope)
      Log.log("create featureentry")
      geopkg.create(entry, featureType)
      val transaction = new DefaultTransaction()
      Log.log("create writer")
      val writer = geopkg.writer(entry,true, null, transaction)
      Log.log("Let's go")
      // FIXME the index if for debug only: we might want to remove it later
      for {
        (feature, index) <- features.get.zipWithIndex
      } {
        if (index % 100000 == 0) Log.log(s"$index")
        val values = Array[AnyRef](
          feature._4,
          Age(feature._1).toString.asInstanceOf[AnyRef],
          (if (feature._2 == 0) "Homme" else "Femme").asInstanceOf[AnyRef],
          s"${feature._3} - ${Education(feature._3).toString}".asInstanceOf[AnyRef]
        )
        def simpleFeature = writer.next
        simpleFeature.setAttributes(values)
        writer.write()
      }
      Log.log("close writer")
      writer.close()
      Log.log("commit transaction")
      transaction.commit()
      Log.log("close transaction")
      transaction.close()
      Log.log("close geopackage")
      geopkg.close()
      Log.log("all done")
    case _ =>
  }
}

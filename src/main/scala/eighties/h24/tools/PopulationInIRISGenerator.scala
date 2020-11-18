package eighties.h24.tools

import java.util

import better.files.Dsl.SymbolicOperations
import better.files._
import eighties.h24.generation._
import eighties.h24.social.{Age, Education}
import eighties.h24.tools.Log._
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.referencing.CRS
import org.locationtech.jts.geom.GeometryFactory
import scopt.OParser

/**
 * Generate a synthetic population in IRIS and export it as a CSV file.
 */
object PopulationInIRISGenerator extends App {
  case class Config(
                     contour: Option[java.io.File] = None,
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
        .text("output file")
    )
  }

  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      val contourIRISFile = config.contour.get.toScala
      val baseICEvolStructPopFileName = config.infraPopulation.get.toScala
      val baseICDiplomesFormationPopFileName = config.infraFormation.get.toScala

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
      val outDirectory = config.output.get.toScala
      // make sure the output directory structure exists
      outDirectory.createDirectories()
      val outCountSyntheticPop = outDirectory / "pop.csv"
      val outDiff = outDirectory / "diff.csv"
      val outDiffProb = outDirectory / "diff_prob.csv"
      val header = "IRIS,P12_POP," +
        "P12_POP0002,P12_POP0305,P12_POP0610,P12_POP1117,P12_POP1824,P12_POP2539,P12_POP4054,P12_POP5564,P12_POP6579,P12_POP80P," +
        "P12_POP0014,P12_POP1529,P12_POP3044,P12_POP4559,P12_POP6074,P12_POP75P," +
        "P12_POPH,P12_H0014,P12_H1529,P12_H3044,P12_H4559,P12_H6074,P12_H75P," +
        "P12_POPF,P12_F0014,P12_F1529,P12_F3044,P12_F4559,P12_F6074,P12_F75P," +
        "P12_SCOL0205,P12_SCOL0610,P12_SCOL1114,P12_SCOL1517,P12_SCOL1824,P12_SCOL2529,P12_SCOL30P," +
        "P12_HNSCOL15P,P12_HNSCOL15P_DIPL0,P12_HNSCOL15P_CEP,P12_HNSCOL15P_BEPC,P12_HNSCOL15P_CAPBEP,P12_HNSCOL15P_BAC,P12_HNSCOL15P_BACP2,P12_HNSCOL15P_SUP," +
        "P12_FNSCOL15P,P12_FNSCOL15P_DIPL0,P12_FNSCOL15P_CEP,P12_FNSCOL15P_BEPC,P12_FNSCOL15P_CAPBEP,P12_FNSCOL15P_BAC,P12_FNSCOL15P_BACP2,P12_FNSCOL15P_SUP\n"
      outCountSyntheticPop < header
      outDiff < header
      outDiffProb < header
      log("Generating population")
      val rnd = new util.Random(42)
      for {
          (spatialUnit, geometry) <- readGeometry(shpData, _ => true)
          age10 <- readAgeSex(age10Data)
          ageSex <- readAgeSex(age15SexData)
          schoolAge <- readAgeSchool(schoolAgeData)
          educationSex <- readEducationSex(educationSexData)
        } yield {
        // the zipWithIndex is only useful for debug so we might want to remove it at some point...
        spatialUnit.zipWithIndex.foreach { case (id: AreaID, index: Int) =>
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
            val samples = sampleIris(age10V, ageSexV, schoolAgeV, educationSexV, rnd).map{feature=>
              (Age(feature._1).toString,if (feature._2 == 0) "Homme" else "Femme",Education(feature._3).toString)
            }
            // P12_POP0002	P12_POP0305	P12_POP0610	P12_POP1117	P12_POP1824	P12_POP2539	P12_POP4054	P12_POP5564	P12_POP6579	P12_POP80P
            val age10_synth = Vector(samples.count(_._1.equals("00 - 02")),samples.count(_._1.equals("03 - 05")),samples.count(_._1.equals("06 - 10")),samples.count(i=>i._1.equals("11 - 14")||i._1.equals("15 - 17")),
              samples.count(_._1.equals("18 - 24")),samples.count(i=>i._1.equals("25 - 29")||i._1.equals("30 - 39")),samples.count(i=>i._1.equals("40 - 44")||i._1.equals("45 - 54")),
              samples.count(i=>i._1.equals("55 - 59")||i._1.equals("60 - 64")),samples.count(i=>i._1.equals("65 - 74")||i._1.equals("75 - 79")),samples.count(_._1.equals("80 - P")))
            // P12_POP0014	P12_POP1529	P12_POP3044	P12_POP4559	P12_POP6074	P12_POP75P
            val age6_synth = Vector(samples.count(i=>i._1.equals("00 - 02")||i._1.equals("03 - 05")||i._1.equals("06 - 10")||i._1.equals("11 - 14")),
              samples.count(i=>i._1.equals("15 - 17")||i._1.equals("18 - 24")||i._1.equals("25 - 29")),
              samples.count(i=>i._1.equals("30 - 39")||i._1.equals("40 - 44")),
              samples.count(i=>i._1.equals("45 - 54")||i._1.equals("55 - 59")),
              samples.count(i=>i._1.equals("60 - 64")||i._1.equals("65 - 74")),
              samples.count(i=>i._1.equals("75 - 79")||i._1.equals("80 - P")))
            val (men,women) = samples.partition(_._2.equals("Homme"))
            // P12_POPH	P12_H0014	P12_H1529	P12_H3044	P12_H4559	P12_H6074	P12_H75P
            val ageMen = Vector(men.count(i=>i._1.equals("00 - 02")||i._1.equals("03 - 05")||i._1.equals("06 - 10")||i._1.equals("11 - 14")),
              men.count(i=>i._1.equals("15 - 17")||i._1.equals("18 - 24")||i._1.equals("25 - 29")),
              men.count(i=>i._1.equals("30 - 39")||i._1.equals("40 - 44")),
              men.count(i=>i._1.equals("45 - 54")||i._1.equals("55 - 59")),
              men.count(i=>i._1.equals("60 - 64")||i._1.equals("65 - 74")),
              men.count(i=>i._1.equals("75 - 79")||i._1.equals("80 - P")))
            // P12_POPF	P12_F0014	P12_F1529	P12_F3044	P12_F4559	P12_F6074	P12_F75P
            val ageWomen = Vector(women.count(i=>i._1.equals("00 - 02")||i._1.equals("03 - 05")||i._1.equals("06 - 10")||i._1.equals("11 - 14")),
              women.count(i=>i._1.equals("15 - 17")||i._1.equals("18 - 24")||i._1.equals("25 - 29")),
              women.count(i=>i._1.equals("30 - 39")||i._1.equals("40 - 44")),
              women.count(i=>i._1.equals("45 - 54")||i._1.equals("55 - 59")),
              women.count(i=>i._1.equals("60 - 64")||i._1.equals("65 - 74")),
              women.count(i=>i._1.equals("75 - 79")||i._1.equals("80 - P")))
            // P12_SCOL0205	P12_SCOL0610	P12_SCOL1114	P12_SCOL1517	P12_SCOL1824	P12_SCOL2529	P12_SCOL30P
            def olderThan15(i: (String, String, String)) = ! (i._1.equals("00 - 02")||i._1.equals("03 - 05") || i._1.equals("06 - 10") || i._1.equals("11 - 14"))
            def olderThan30(i: (String, String, String)) = olderThan15(i) && ! (i._1.equals("15 - 17")||i._1.equals("18 - 24") || i._1.equals("25 - 29"))
            val scol = Vector(samples.count(i=>i._1.equals("03 - 05") && i._3.equalsIgnoreCase("Schol")),
              samples.count(i=>i._1.equals("06 - 10") && i._3.equalsIgnoreCase("Schol")),
              samples.count(i=>i._1.equals("11 - 14") && i._3.equalsIgnoreCase("Schol")),
              samples.count(i=>i._1.equals("15 - 17") && i._3.equalsIgnoreCase("Schol")),
              samples.count(i=>i._1.equals("18 - 24") && i._3.equalsIgnoreCase("Schol")),
              samples.count(i=>i._1.equals("25 - 29") && i._3.equalsIgnoreCase("Schol")),
              samples.count(i=>olderThan30(i) && i._3.equalsIgnoreCase("Schol"))
            )
            // P12_HNSCOL15P	P12_HNSCOL15P_DIPL0	P12_HNSCOL15P_CEP	P12_HNSCOL15P_BEPC	P12_HNSCOL15P_CAPBEP	P12_HNSCOL15P_BAC	P12_HNSCOL15P_BACP2	P12_HNSCOL15P_SUP
            val educationMen = Vector(men.count(i => olderThan15(i) && !i._3.equalsIgnoreCase("Schol")),
              men.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("Dipl0")),
              men.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("CEP")),
              men.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("BEPC")),
              men.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("CAPBEP")),
              men.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("BAC")),
              men.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("BACP2")),
              men.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("SUP")))
            // P12_FNSCOL15P	P12_FNSCOL15P_DIPL0	P12_FNSCOL15P_CEP	P12_FNSCOL15P_BEPC	P12_FNSCOL15P_CAPBEP	P12_FNSCOL15P_BAC	P12_FNSCOL15P_BACP2	P12_FNSCOL15P_SUP
            val educationWomen = Vector(women.count(i=> olderThan15(i) && !i._3.equalsIgnoreCase("Schol")),
              women.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("Dipl0")),
              women.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("CEP")),
              women.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("BEPC")),
              women.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("CAPBEP")),
              women.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("BAC")),
              women.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("BACP2")),
              women.count(i=> olderThan15(i) && i._3.equalsIgnoreCase("SUP")))
            // IRIS
            val irisName = if (id.id.length == 9) id.id else id.id+"0000" // put the zeros back in
            outCountSyntheticPop << s"$irisName," +
              // P12_POP
              s"${samples.length}," +
              s"${age10_synth.mkString(",")},${age6_synth.mkString(",")}," +
              s"${men.length},${ageMen.mkString(",")},${women.length},${ageWomen.mkString(",")}," +
              s"${scol.mkString(",")}," +
              s"${educationMen.mkString(",")},${educationWomen.mkString(",")}"
            val diffAge10 = age10V.zip(age10_synth).map{case (a,b)=>scala.math.abs(a-b)}
            val diffAge6 = age6_synth.indices.map(index=>ageSexV(index)+ageSexV(index+6)).zip(age6_synth).map{case (a,b)=>scala.math.abs(a-b)}
            val (ageSexMen, ageSexWomen) = ageSexV.splitAt(6)
            val diffAgeMen = ageMen.zip(ageSexMen).map{case (a,b)=>scala.math.abs(a-b)}
            val diffAgeWomen = ageWomen.zip(ageSexWomen).map{case (a,b)=>scala.math.abs(a-b)}
            val diffScol = schoolAgeV.takeRight(7).zip(scol).map{case (a,b)=>scala.math.abs(a-b)}
            val diffEduMen = educationSexV(0).prepended(educationSexV(0).sum).zip(educationMen).map{case (a,b)=>scala.math.abs(a-b)}
            val diffEduWomen = educationSexV(1).prepended(educationSexV(1).sum).zip(educationWomen).map{case (a,b)=>scala.math.abs(a-b)}
            outDiff << s"$irisName," +
              // P12_POP
              s"${scala.math.abs(samples.length - total)}," +
              s"${diffAge10.mkString(",")},${diffAge6.mkString(",")}," +
              s"${scala.math.abs(men.length - ageSexMen.sum)},${diffAgeMen.mkString(",")},${scala.math.abs(women.length - ageSexWomen.sum)},${diffAgeWomen.mkString(",")}," +
              s"${diffScol.mkString(",")}," +
              s"${diffEduMen.mkString(",")},${diffEduWomen.mkString(",")}"
            outDiffProb << s"$irisName," +
              // P12_POP
              s"${if (total==0) 0 else scala.math.abs(samples.length - total)/total}," +
              s"${diffAge10.map(v=> if (total==0) 0 else v/total).mkString(",")},${diffAge6.map(v=> if (total==0) 0 else v/total).mkString(",")}," +
              s"${if (ageSexMen.sum == 0) 0 else scala.math.abs(men.length - ageSexMen.sum)/ageSexMen.sum},${diffAgeMen.map(v=>if (ageSexMen.sum == 0) 0 else v/ageSexMen.sum).mkString(",")}," +
              s"${if (ageSexWomen.sum == 0) 0 else scala.math.abs(women.length - ageSexWomen.sum)/ageSexWomen.sum},${diffAgeWomen.map(v=>if (ageSexWomen.sum == 0) 0 else v/ageSexWomen.sum).mkString(",")}," +
              s"${diffScol.map(v=>if (total==0) 0 else v/total).mkString(",")}," +
              s"${diffEduMen.map(v=>if(educationSexV(0).sum ==0) 0 else v/educationSexV(0).sum).mkString(",")}," +
              s"${diffEduWomen.map(v=>if(educationSexV(1).sum ==0) 0 else v/educationSexV(1).sum).mkString(",")}"
          }
        }
      }
      Log.log("all done")
    case _ =>
  }
}

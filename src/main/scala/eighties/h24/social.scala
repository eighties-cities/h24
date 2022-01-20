package eighties.h24

import eighties.h24.generation._
import monocle._

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
object social {
  /* -------------------------- Social Category -------------------------- */

  sealed class Age(val from: Int, val to: Option[Int]) {
    override def toString: String = s"${"%02d".format(from)} - ${if (to.isDefined) "%02d".format(to.get) else "P"}"
  }

  object Age {

    object From0To2 extends Age(0, Some(2))
    object From3To5 extends Age(3, Some(5))
    object From6To10 extends Age(6, Some(10))
    object From11To14 extends Age(11, Some(14))
    object From15To17 extends Age(15, Some(17))
    object From18To24 extends Age(18, Some(24))
    object From25To29 extends Age(25, Some(29))
    object From30To39 extends Age(30, Some(39))
    object From40To44 extends Age(40, Some(44))
    object From45To54 extends Age(45, Some(54))
    object From55To59 extends Age(55, Some(59))
    object From60To64 extends Age(60, Some(64))
    object From65To74 extends Age(65, Some(74))
    object From75To79 extends Age(75, Some(79))
    object Above80 extends Age(80, None)

    //    object From0To14 extends Age(0, Some(14))
//    object From15To29 extends Age(15, Some(29))
//    object From30To44 extends Age(30, Some(44))
//    object From45To59 extends Age(45, Some(59))
//    object From60To74 extends Age(60, Some(74))
//    object Above75 extends Age(75, None)

//    lazy val all = Array(From0To14, From15To29, From30To44, From45To59, From60To74, Above75)
    lazy val all: Array[Age] = Array(From0To2, From3To5, From6To10, From11To14, From15To17, From18To24, From25To29,
      From30To39, From40To44, From45To54, From55To59, From60To64, From65To74, From75To79, Above80)

    def apply(code: Int): Age =
//      code match {
//        case 0 => From0To14
//        case 1 => From15To29
//        case 2 => From30To44
//        case 3 => From45To59
//        case 4 => From60To74
//        case 5 => Above75
//      }
      code match {
        case 0 => From0To2
        case 1 => From3To5
        case 2 => From6To10
        case 3 => From11To14
        case 4 => From15To17
        case 5 => From18To24
        case 6 => From25To29
        case 7 => From30To39
        case 8 => From40To44
        case 9 => From45To54
        case 10 => From55To59
        case 11 => From60To64
        case 12 => From65To74
        case 13 => From75To79
        case 14 => Above80
      }
    def parse(age: Int): Option[Age] =
//      if (age < 15) From0To14 else
//      if (age < 30) From15To29 else
//      if (age < 45) From30To44 else
//      if (age < 60) From45To59 else
//      if (age < 75) From60To74 else Above75
      all.findLast(value => age > value.from)
  }


  type AggregatedAge = Byte

  object AggregatedAge {

    implicit def ordered: Ordering[AggregatedAge] = Ordering.by[AggregatedAge, String](toCode)

    val Age1: AggregatedAge = 0.toByte
    val Age2: AggregatedAge = 1.toByte
    val Age3: AggregatedAge = 2.toByte

    def apply(age: Age): AggregatedAge = age match {
//      case Age.From0To14 | Age.From15To29 => Age1
//      case Age.From30To44 | Age.From45To59 => Age2
//      case Age.From60To74 | Age.Above75 => Age3
//      case _: Age => Age1
      case Age.From0To2 | Age.From3To5 | Age.From6To10 | Age.From11To14 | Age.From15To17 | Age.From18To24 | Age.From25To29 => Age1
      case Age.From30To39 | Age.From40To44 | Age.From45To54 | Age.From55To59 => Age2
      case Age.From60To64 | Age.From65To74 | Age.From75To79 | Age.Above80 => Age3
      case _: Age => Age1
    }

    def all: Array[AggregatedAge] = Array(Age1, Age2, Age3)

    def toCode(age: AggregatedAge): String =
      age match {
        case Age1 => "1"
        case Age2 => "2"
        case Age3 => "3"
      }
  }

  type Sex = Byte

  object Sex {

    implicit def ordered: Ordering[Sex] = Ordering.by[Sex, String](toCode)


    val Male: Sex = 0.toByte
    val Female: Sex = 1.toByte

    def all: Array[AggregatedAge] = Array(Male, Female)

    def apply(code: Int): Sex =
      code match {
        case 0 => Male
        case 1 => Female
      }

    def toCode(sex: Sex): String =
      sex match {
        case Male => "1"
        case Female => "2"
      }
  }

  sealed trait Education {
    override def toString: String = getClass.getSimpleName.dropRight(1)
  }

  object Education {
    object Schol extends Education
    object Dipl0 extends Education
    object CEP extends Education
    object BEPC extends Education
    object CAPBEP extends Education
    object BAC extends Education
    object BACP2 extends Education
    object SUP extends Education

    def all: Array[Education] = Array(Schol, Dipl0, CEP, BEPC, CAPBEP, BAC, BACP2, SUP)

    def apply(code: Int): Education =
      code match {
        case 0 => Schol
        case 1 => Dipl0
        case 2 => CEP
        case 3 => BEPC
        case 4 => CAPBEP
        case 5 => BAC
        case 6 => BACP2
        case 7 => SUP
      }
  }

  type AggregatedEducation = Byte

  object AggregatedEducation {
    val Low: AggregatedEducation = 0.toByte
    val Middle: AggregatedEducation = 1.toByte
    val High: AggregatedEducation = 2.toByte

    def all: Array[AggregatedAge] = Array(Low, Middle, High)

    def apply(education: Education, age: Age): AggregatedEducation =
      education match {
        case Education.Schol => if (age.from < 18) Low else if (age.from < 25) Middle else High
        case Education.Dipl0 | Education.BEPC | Education.CAPBEP | Education.CEP => Low
        case Education.BAC | Education.BACP2 => Middle
        case Education.SUP => High
      }

    def toCode(education: AggregatedEducation): String =
      education match {
        case Low => "1"
        case Middle => "2"
        case High => "3"
      }

  }


  object SocialCategory {
    def all: Array[SocialCategory] =
      for {
        age <- Age.all
        sex <- Sex.all
        education <- Education.all
      } yield SocialCategory(age, sex, education)

    def apply(feature: IndividualFeature): SocialCategory =
      new SocialCategory(
        sex = Sex(feature.sex),
        age = Age(feature.ageCategory),
        education = Education(feature.education))

  }

  case class SocialCategory(age: Age, sex: Sex, education: Education)


  object AggregatedSocialCategory {
    def apply(category: SocialCategory): AggregatedSocialCategory =
      new AggregatedSocialCategory(
        age = AggregatedAge(category.age),
        sex = category.sex,
        education = AggregatedEducation(category.education, category.age)
      )

    def apply(feature: IndividualFeature): AggregatedSocialCategory =
      AggregatedSocialCategory(SocialCategory(feature))

    lazy val all: Array[AggregatedSocialCategory] =
      for {
        sex <- Sex.all
        age <- AggregatedAge.all
        education <- AggregatedEducation.all
      } yield AggregatedSocialCategory(age, sex, education)

    def toCode(aggregatedSocialCategory: AggregatedSocialCategory): Array[String] =
      Array(
        Sex.toCode(aggregatedSocialCategory.sex),
        AggregatedAge.toCode(aggregatedSocialCategory.age),
        AggregatedEducation.toCode(aggregatedSocialCategory.education))


    def ageValue: PLens[AggregatedAge, AggregatedAge, AggregatedAge, AggregatedAge] = shortAggregatedSocialCategoryIso composeLens Focus[AggregatedSocialCategory](_.age)
    def sexValue: PLens[AggregatedAge, AggregatedAge, Sex, Sex] = shortAggregatedSocialCategoryIso composeLens Focus[AggregatedSocialCategory](_.sex)
    def educationValue: PLens[AggregatedAge, AggregatedAge, AggregatedEducation, AggregatedEducation] = shortAggregatedSocialCategoryIso composeLens Focus[AggregatedSocialCategory](_.education)
    def shortAggregatedSocialCategoryIso: Iso[AggregatedAge, AggregatedSocialCategory] = monocle.Iso[Byte, AggregatedSocialCategory](i => all(i.toInt - Byte.MinValue))(c => (all.indexOf(c) + Byte.MinValue).toByte)
  }


  case class AggregatedSocialCategory(
    age: AggregatedAge,
    sex: Sex,
    education: AggregatedEducation)


  type ShortAggregatedSocialCategory = Short
}

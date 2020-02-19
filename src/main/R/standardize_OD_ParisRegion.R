# ================================================================================#
# Create trip (H24_trip) and individual table (H24_ind) from origin-destination survey. 
#
# Valid for Paris region (EGT 2010), France
#
# November 2019 - Julie Vallée, Aurelie Douet, Guillaume le Roux
# Mobiliscope : https://mobiliscope.parisgeo.cnrs.fr
# ================================================================================#


# Chargement des bibliotheques
library(stringr)
library(TraMineR)
library(dplyr)
library(readr)
library(Hmisc)
library(foreign)
library(sp)
library(sf)



################# TRIP TABLE #######################

# Load data
load("data_egt/deplacements_semaine.RData")

  ##Clean ID carreau
  deplacements_semaine$resc[deplacements_semaine$resc=="641771g"] <- "641771G"
  deplacements_semaine$resc[deplacements_semaine$resc=="822312g"] <- "822312G"
  deplacements_semaine$resc[deplacements_semaine$resc=="583149g"] <- "583149G"
  deplacements_semaine$resc[deplacements_semaine$resc=="555488e"] <- "555488E"
  deplacements_semaine$resc[deplacements_semaine$resc=="583422h"] <- "583422H"

  deplacements_semaine$orc[deplacements_semaine$orc=="641771g"] <- "641771G"
  deplacements_semaine$orc[deplacements_semaine$orc=="822312g"] <- "822312G"
  deplacements_semaine$orc[deplacements_semaine$orc=="583149g"] <- "583149G"
  deplacements_semaine$orc[deplacements_semaine$orc=="555488e"] <- "555488E"
  deplacements_semaine$orc[deplacements_semaine$orc=="583422h"] <- "583422H"

  deplacements_semaine$destc[deplacements_semaine$destc=="641771g"] <- "641771G"
  deplacements_semaine$destc[deplacements_semaine$destc=="822312g"] <- "822312G"
  deplacements_semaine$destc[deplacements_semaine$destc=="583149g"] <- "583149G"
  deplacements_semaine$destc[deplacements_semaine$destc=="555488e"] <- "555488E"
  deplacements_semaine$destc[deplacements_semaine$destc=="583422h"] <- "583422H"
  
  # Clean data (according to trip order)
  deplacements_semaine$ID_pers <- ifelse(nchar(deplacements_semaine$np)==1,paste(deplacements_semaine$nquest,deplacements_semaine$np,sep="0"),paste(deplacements_semaine$nquest,deplacements_semaine$np,sep=""))
  
  deplacements_semaine$orh[deplacements_semaine$ID_pers=="9210106101" & deplacements_semaine$nd=="3"] <- 12
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7703180101" & deplacements_semaine$nd=="4"] <- 35
  deplacements_semaine$orm[deplacements_semaine$ID_pers=="7716204102" & deplacements_semaine$nd=="5"] <- 55
  deplacements_semaine$orm[deplacements_semaine$ID_pers=="9408275101" & deplacements_semaine$nd=="2"] <- 20
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9408275101" & deplacements_semaine$nd=="2"] <- 40
  deplacements_semaine$orm[deplacements_semaine$ID_pers=="9408275101" & deplacements_semaine$nd=="3"] <- 40
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9408275102" & deplacements_semaine$nd=="1"] <- 22
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9414903103" & deplacements_semaine$nd=="2"] <- 30
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7501035102" & deplacements_semaine$nd=="5"] <- 50
  deplacements_semaine$desth[deplacements_semaine$ID_pers=="7501035102" & deplacements_semaine$nd=="5"] <- 20
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9101018101" & deplacements_semaine$nd=="12"] <- 38
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9103006102" & deplacements_semaine$nd=="1"] <- 10
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9103006102" & deplacements_semaine$nd=="2"] <- 15
  deplacements_semaine$orm[deplacements_semaine$ID_pers=="9111108102" & deplacements_semaine$nd=="3"] <- 25
  deplacements_semaine$orm[deplacements_semaine$ID_pers=="9204257101" & deplacements_semaine$nd=="3"] <- 56
  deplacements_semaine$orh[deplacements_semaine$ID_pers=="9204257101" & deplacements_semaine$nd=="3"] <- 17
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9204257101" & deplacements_semaine$nd=="3"] <- 0
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9308041102" & deplacements_semaine$nd=="1"] <- 10
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9511901101" & deplacements_semaine$nd=="3"] <- 35
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9511901101" & deplacements_semaine$nd=="6"] <- 40
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7806453101" & deplacements_semaine$nd=="1"] <- 35
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7502188101" & deplacements_semaine$nd=="1"] <- 52
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9414193101" & deplacements_semaine$nd=="1"] <- 19
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7502335102" & deplacements_semaine$nd=="5"] <- 35
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7812166102" & deplacements_semaine$nd=="2"] <- 35
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9511045101" & deplacements_semaine$nd=="4"] <- 45
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7507105101" & deplacements_semaine$nd=="1"] <- 26
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7507105103" & deplacements_semaine$nd=="1"] <- 26
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7509141101" & deplacements_semaine$nd=="4"] <- 10
  deplacements_semaine$orm[deplacements_semaine$ID_pers=="7703128101" & deplacements_semaine$nd=="3"] <- 10
  deplacements_semaine$orm[deplacements_semaine$ID_pers=="7703428101" & deplacements_semaine$nd=="2"] <- 45
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7705103102" & deplacements_semaine$nd=="5"] <- 35
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7705103104" & deplacements_semaine$nd=="5"] <- 35
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7806413101" & deplacements_semaine$nd=="6"] <- 45
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7808040101" & deplacements_semaine$nd=="1"] <- 15
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9106017102" & deplacements_semaine$nd=="4"] <- 30
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9107056101" & deplacements_semaine$nd=="1"] <- 27
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9111093101" & deplacements_semaine$nd=="3"] <- 32
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9112094102" & deplacements_semaine$nd=="5"] <- 40
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9112094103" & deplacements_semaine$nd=="6"] <- 40
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9112094104" & deplacements_semaine$nd=="3"] <- 40
  deplacements_semaine$orm[deplacements_semaine$ID_pers=="9204164101" & deplacements_semaine$nd=="2"] <- 35
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9309257102" & deplacements_semaine$nd=="1"] <- 30
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9309257102" & deplacements_semaine$nd=="5"] <- 25
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9309257102" & deplacements_semaine$nd=="6"] <- 35
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9307901105" & deplacements_semaine$nd=="2"] <- 15
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9410094101" & deplacements_semaine$nd=="3"] <- 40
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9411234101" & deplacements_semaine$nd=="1"] <- 45
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9411234101" & deplacements_semaine$nd=="10"] <- 35
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9502279101" & deplacements_semaine$nd=="4"] <- 5
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9511901101" & deplacements_semaine$nd=="3"] <- 35
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9410215102" & deplacements_semaine$nd=="5"] <- 11
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7514446101" & deplacements_semaine$nd=="1"] <- 34
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7706282101" & deplacements_semaine$nd=="1"] <- 40
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9403813101" & deplacements_semaine$nd=="2"] <- 34
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9406114102" & deplacements_semaine$nd=="2"] <- 8
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="9209304101" & deplacements_semaine$nd=="2"] <- 35
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7510518101" & deplacements_semaine$nd=="3"] <- 50
  deplacements_semaine$destm[deplacements_semaine$ID_pers=="7703394101" & deplacements_semaine$nd=="1"] <- 30
  deplacements_semaine <- filter(deplacements_semaine, ID_pers!="9302190101")
  deplacements_semaine <- arrange(deplacements_semaine, ID_pers, orh,orm)

  #suppression des individus avec des heures manquantes
  IDhNA <- data.frame(ID_pers = character(length(unique(deplacements_semaine$ID_pers[is.na(deplacements_semaine$orh)==FALSE & is.na(deplacements_semaine$desth)==FALSE]))))
  IDhNA$ID_pers <- unique(deplacements_semaine$ID_pers[is.na(deplacements_semaine$orh)==FALSE & is.na(deplacements_semaine$desth)==FALSE])
  deplacements_semaine <-  merge(x = deplacements_semaine, y=IDhNA, by = "ID_pers", all = FALSE)
  deplacements_semaine <- arrange(deplacements_semaine, ID_pers, orh,orm)
  
tripTable <- deplacements_semaine
rm(deplacements_semaine)

# Create ID variables
## ID_IND : for every respondent combine nquest & np
tripTable$ID_IND <- ifelse(nchar(tripTable$np)==1,
                           paste(tripTable$nquest, tripTable$np,sep="_0"),
                           paste(tripTable$nquest, tripTable$np,sep="_"))

## ID_ED : for every OD survey combine IDD4 (ville centre) & IDD3 (annee)
tripTable$ID_ED <- "75000_2010"


# Create 'purpose' variables
## O_PURPOSE et D_PURPOSE (ormot et desmot) in six groups
## 1 : home ; 2 : work ; 3 : study ; 4 : shopping ; 5 : leasure ; 6 : others

tripTable <- tripTable %>% 
  mutate(O_PURPOSE = plyr::mapvalues(ormot_h9, c("", "1", "2", "3", "4", "5","6","7","01", "02", "03", "04", "05","06","07"), 
                                c(NA,1,2,2,3,4,5,6,1,2,2,3,4,5,6)))

tripTable <- tripTable %>% 
  mutate(D_PURPOSE = plyr::mapvalues(destmot_h9, c("", "1", "2", "3", "4", "5","6","7", "8","9","01", "02", "03", "04", "05","06","07", "08","09"), 
                                     c(NA,1,2,2,3,4,5,6,5,6, 1,2,2,3,4,5,6,5,6)))


# Create 'transportation' variables
## Mode adherent (MOD_ADH): walk, bike etc. = 1 
tripTable$MOD_ADH <- ifelse (tripTable$modp_h6=="04" | tripTable$modp_h6=="06", 1, 0)

# Build trip table
tripTable <- tripTable %>%
  transmute(ID_IND, ID_ED,
            RES_ZF = resc, RES_SEC = ressect, 
            O_ZF = orc, O_SEC = orsect,
            D_ZF = destc, D_SEC = destsect, 
            H_START = orh, M_START = orm,
            H_END = desth, M_END = destm,
            D9 = duree, O_PURPOSE, D_PURPOSE, MOD_ADH)


## Add centroids
sfZF <- st_read("data_egt/carr100m_proj.shp", stringsAsFactors = F)  %>% 
  st_transform(crs = "+init=epsg:27572")
sfZF$centroid <- st_centroid(sfZF$geometry)
sfZF$ZF_X <- as.character(lapply (sfZF$centroid, '[[',1))
sfZF$ZF_Y <- as.character(lapply (sfZF$centroid, '[[',2))
sfZF$CODE_ZF <- as.character (sfZF$IDENT)
zonefine <- as.data.frame (select (sfZF, CODE_ZF, ZF_X, ZF_Y))
zonefine <- zonefine %>%
  transmute(CODE_ZF, ZF_X, ZF_Y)
rm (sfZF)

tripTable <- merge(tripTable, zonefine, by.x="O_ZF", by.y="CODE_ZF", all.x = TRUE)
tripTable$O_ZF_X <- tripTable$ZF_X
tripTable$O_ZF_Y <- tripTable$ZF_Y
tripTable <- select (tripTable, -c(ZF_X, ZF_Y))

tripTable <- merge(tripTable, zonefine, by.x="D_ZF", by.y="CODE_ZF",all.x = TRUE)
tripTable$D_ZF_X <- tripTable$ZF_X
tripTable$D_ZF_Y <- tripTable$ZF_Y
tripTable <- select (tripTable, -c(ZF_X, ZF_Y))

## Clean HOURS/MIN
tripTable$H_START[tripTable$ID_IND=="92101061_01" & tripTable$nd=="3"] <- 12
tripTable$M_END[tripTable$ID_IND=="77031801_01" & tripTable$NDEP=="4"] <- 35
tripTable$M_START[tripTable$ID_IND=="77162041_02" & tripTable$NDEP=="5"] <- 55
tripTable$M_START[tripTable$ID_IND=="94082751_01" & tripTable$NDEP=="2"] <- 20
tripTable$M_END[tripTable$ID_IND=="94082751_01" & tripTable$NDEP=="2"] <- 40
tripTable$M_START[tripTable$ID_IND=="94082751_01" & tripTable$NDEP=="3"] <- 40
tripTable$M_END[tripTable$ID_IND=="94082751_02" & tripTable$NDEP=="1"] <- 22
tripTable$M_END[tripTable$ID_IND=="94149031_03" & tripTable$NDEP=="2"] <- 30
tripTable$M_END[tripTable$ID_IND=="75010351_02" & tripTable$NDEP=="5"] <- 50
tripTable$H_START[tripTable$ID_IND=="75010351_02" & tripTable$NDEP=="5"] <- 20
tripTable$M_END[tripTable$ID_IND=="91010181_01" & tripTable$NDEP=="12"] <- 38
tripTable$M_END[tripTable$ID_IND=="91030061_02" & tripTable$NDEP=="1"] <- 10
tripTable$M_END[tripTable$ID_IND=="91030061_02" & tripTable$NDEP=="2"] <- 15
tripTable$M_START[tripTable$ID_IND=="91111081_02" & tripTable$NDEP=="3"] <- 25
tripTable$M_START[tripTable$ID_IND=="92042571_01" & tripTable$NDEP=="3"] <- 56
tripTable$H_START[tripTable$ID_IND=="92042571_01" & tripTable$NDEP=="3"] <- 17
tripTable$M_END[tripTable$ID_IND=="92042571_01" & tripTable$NDEP=="3"] <- 0
tripTable$M_END[tripTable$ID_IND=="93080411_02" & tripTable$NDEP=="1"] <- 10
tripTable$M_END[tripTable$ID_IND=="95119011_01" & tripTable$NDEP=="3"] <- 35
tripTable$M_END[tripTable$ID_IND=="95119011_01" & tripTable$NDEP=="6"] <- 40
tripTable$M_END[tripTable$ID_IND=="78064531_01" & tripTable$NDEP=="1"] <- 35
tripTable$M_END[tripTable$ID_IND=="75021881_01" & tripTable$NDEP=="1"] <- 52
tripTable$M_END[tripTable$ID_IND=="94141931_01" & tripTable$NDEP=="1"] <- 19
tripTable$M_END[tripTable$ID_IND=="75023351_02" & tripTable$NDEP=="5"] <- 35
tripTable$M_END[tripTable$ID_IND=="78121661_02" & tripTable$NDEP=="2"] <- 35
tripTable$M_END[tripTable$ID_IND=="95110451_01" & tripTable$NDEP=="4"] <- 45
tripTable$M_END[tripTable$ID_IND=="75071051_01" & tripTable$NDEP=="1"] <- 26
tripTable$M_END[tripTable$ID_IND=="75071051_03" & tripTable$NDEP=="1"] <- 26
tripTable$M_END[tripTable$ID_IND=="75091411_01" & tripTable$NDEP=="4"] <- 10
tripTable$M_START[tripTable$ID_IND=="77031281_01" & tripTable$NDEP=="3"] <- 10
tripTable$M_START[tripTable$ID_IND=="77034281_01" & tripTable$NDEP=="2"] <- 45
tripTable$M_END[tripTable$ID_IND=="77051031_02" & tripTable$NDEP=="5"] <- 35
tripTable$M_END[tripTable$ID_IND=="77051031_04" & tripTable$NDEP=="5"] <- 35
tripTable$M_END[tripTable$ID_IND=="78064131_01" & tripTable$NDEP=="6"] <- 45
tripTable$M_END[tripTable$ID_IND=="78080401_01" & tripTable$NDEP=="1"] <- 15
tripTable$M_END[tripTable$ID_IND=="91060171_02" & tripTable$NDEP=="4"] <- 30
tripTable$M_END[tripTable$ID_IND=="91070561_01" & tripTable$NDEP=="1"] <- 27
tripTable$M_END[tripTable$ID_IND=="91110931_01" & tripTable$NDEP=="3"] <- 32
tripTable$M_END[tripTable$ID_IND=="91120941_02" & tripTable$NDEP=="5"] <- 40
tripTable$M_END[tripTable$ID_IND=="91120941_03" & tripTable$NDEP=="6"] <- 40
tripTable$M_END[tripTable$ID_IND=="91120941_04" & tripTable$NDEP=="3"] <- 40
tripTable$M_START[tripTable$ID_IND=="92041641_01" & tripTable$NDEP=="2"] <- 35
tripTable$M_END[tripTable$ID_IND=="93092571_02" & tripTable$NDEP=="1"] <- 30
tripTable$M_END[tripTable$ID_IND=="93092571_02" & tripTable$NDEP=="5"] <- 25
tripTable$M_END[tripTable$ID_IND=="93092571_02" & tripTable$NDEP=="6"] <- 35
tripTable$M_END[tripTable$ID_IND=="93079011_05" & tripTable$NDEP=="2"] <- 15
tripTable$M_END[tripTable$ID_IND=="94100941_01" & tripTable$NDEP=="3"] <- 40
tripTable$M_END[tripTable$ID_IND=="94112341_01" & tripTable$NDEP=="1"] <- 45
tripTable$M_END[tripTable$ID_IND=="94112341_01" & tripTable$NDEP=="10"] <- 35
tripTable$M_END[tripTable$ID_IND=="95022791_01" & tripTable$NDEP=="4"] <- 5
tripTable$M_END[tripTable$ID_IND=="95119011_01" & tripTable$NDEP=="3"] <- 35
tripTable$M_END[tripTable$ID_IND=="94102151_02" & tripTable$NDEP=="5"] <- 11
tripTable$M_END[tripTable$ID_IND=="75144461_01" & tripTable$NDEP=="1"] <- 34
tripTable$M_END[tripTable$ID_IND=="77062821_01" & tripTable$NDEP=="1"] <- 40
tripTable$M_END[tripTable$ID_IND=="94038131_01" & tripTable$NDEP=="2"] <- 34
tripTable$M_END[tripTable$ID_IND=="94061141_02" & tripTable$NDEP=="2"] <- 8
tripTable$M_END[tripTable$ID_IND=="92093041_01" & tripTable$NDEP=="2"] <- 35
tripTable$M_END[tripTable$ID_IND=="75105181_01" & tripTable$NDEP=="3"] <- 50
tripTable$M_END[tripTable$ID_IND=="77033941_01" & tripTable$NDEP=="1"] <- 30

tripTable <- filter(tripTable, ID_IND != "93021901_01")

# Build final trip table 
tripTable <- dplyr::arrange(tripTable, ID_IND, NDEP)
tripTable <- tripTable %>%
  transmute(ID_IND, ID_ED, 
            RES_ZF, RES_SEC, 
            O_ZF, O_ZF_X, O_ZF_Y, O_SEC, 
            D_ZF, D_ZF_X, D_ZF_Y, D_SEC,
            H_START, M_START, H_END, M_END,
            D9, O_PURPOSE, D_PURPOSE, MOD_ADH)

## Save final trip table
save(tripTable, file = "scriptsr/data/H24_trip.RDS")
write.csv2(tripTable, "scriptsr/data/H24_trip.csv", row.names = FALSE)


################# IND TABLE #######################

# Load data
load("data_egt/personnes_semaine.RData")

  ##Clean ID carreau
  personnes_semaine$resc[personnes_semaine$resc=="641771g"] <- "641771G"
  personnes_semaine$resc[personnes_semaine$resc=="822312g"] <- "822312G"
  personnes_semaine$resc[personnes_semaine$resc=="583149g"] <- "583149G"
  personnes_semaine$resc[personnes_semaine$resc=="555488e"] <- "555488E"
  personnes_semaine$resc[personnes_semaine$resc=="583422h"] <- "583422H"

  
indTable <- personnes_semaine
rm(personnes_semaine)


# Create ID variables
## ID_IND : for every respondent combine nquest & np
indTable$ID_IND <- ifelse(nchar(indTable$np)==1,
                           paste(indTable$nquest, indTable$np,sep="_0"),
                           paste(indTable$nquest, indTable$np,sep="_"))

## ID_ED : for every OD survey combine IDD4 (ville centre) & IDD3 (annee)
indTable$ID_ED <- "75000_2010"

## Delete individuals less than 5 yrs. 
indTable <- filter(indTable, age>=5)


# Create socio-demographic variables
## sex (SEX)
## 1: Male ; 2 : Female
indTable <- indTable %>%
  mutate(SEX = sexe)

## age groups (KAGE) in three groups 
## 1: 16-29 yrs.; 2: 30-59 yrs.; 3: 60 yrs. and more.
## 0 : less than 16 yrs.
indTable <- indTable %>% 
  mutate(KAGE = case_when(as.numeric(age) >= 16 & as.numeric(age) <= 29 ~ 1,
                          as.numeric(age) >= 30 & as.numeric(age) <= 59 ~ 2,
                          as.numeric(age) >= 60 ~ 3,
                          TRUE ~ 0))

## education groups (KEDUC) in three groups
## 1 : low (sans diplôme - BEPC; CEP; BEP/CAP) ; 2 : middle (Bac - Bac+2) ; 3 : up (> Bac+2)
indTable <- indTable %>% 
  mutate(KEDUC = plyr::mapvalues(dipl, c("", "00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "93", "97", "90"), 
                                c(NA, NA, 1, 1, 1, 2, 2, 3, 1, 2, 1, NA, NA, NA)))


## Ajout des centroides de ZF RESIDENCE
indTable <- merge(indTable, zonefine, by.x="resc", by.y="CODE_ZF", all.x= TRUE)
indTable$RES_ZF_X <- indTable$ZF_X
indTable$RES_ZF_Y <- indTable$ZF_Y


## Delete individuals outside Paris region 
indTable <- filter(indTable, indTable$nondepl!=8)

# Build final individual table 
indTable <- dplyr::arrange(indTable, ID_IND)
indTable <- indTable %>%
  transmute(ID_IND, ID_ED, RES_ZF_CODE = resc, RES_SEC = ressect,
            RES_ZF_X, RES_ZF_Y,
            SEX, AGE = as.numeric(age), KAGE, KEDUC, nondepl)

## Save final individual table
save(indTable, file = "scriptsr/data/H24_ind.RDS")
write.csv2(indTable, "scriptsr/data/H24_ind.csv", row.names = FALSE)
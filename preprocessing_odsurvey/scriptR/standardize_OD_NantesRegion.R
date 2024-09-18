# ================================================================================#
# Create trip table (H24_trip) and individual table (H24_ind) from origin-destination survey

# Nantes area (EDGT 2015), France.
# 
# November 2020 - Julie Vallee, Aurelie Douet, Guillaume le Roux
# cf. Mobiliscope : https://mobiliscope.cnrs.fr
# ================================================================================#

# Library
library(readr)
library(dplyr)
library(stringr)
library(TraMineR)
library(Hmisc)
library(sp)
library(sf)
library(readxl)

################# TRIP TABLE #######################
setwd('~/H24/H24_Library')

# Load data
## open-data : https://www.data.gouv.fr/fr/datasets/enquete-deplacements-en-loire-atlantique-2/
tripTable <- read_csv("opendata_44/02c_EDGT_44_DEPLA_FAF_TEL_DIST_2015-11-10.txt",col_names = FALSE)

# Extract variables
tripTable <- tripTable %>% 
  mutate(DP1 = substr(X1, 1, 1),
        IDD3 = 2015,
        IDD4 = "44000",
        ZFD = substr(X1, 2, 7),
        ECH = substr(X1, 8, 11),
        PER = substr(X1, 12, 13),
        NDEP = substr(X1, 14, 15),
        GD1 = NA,
        STD = substr(X1, 2, 4), 
        D2A = substr(X1, 16, 17),
        D2B = substr(X1, 18, 19),
        D3 = substr(X1, 20, 25),
        GDO1 = NA,
        STDO = substr(D3, 1, 3), 
        D4 = substr(X1, 26, 29),
        D5A = substr(X1, 30, 31),
        D5B = substr(X1, 32, 33),
        D6 = substr(X1, 34, 35),
        D7 = substr(X1, 36, 41),
        GDD1 = NA,
        STDD = substr(D7, 1, 3),
        D8 = substr(X1, 42, 45),
        D9 = substr(X1, 49, 49),
        D10 = NA,
        D11 = NA,
        D12 = NA,
        MODP = substr(X1, 50, 51),
        TYPD = NA,
        METHOD = NA ) %>% 
  select(-(X1))


# Create ID variables
## ID_IND : for every respondent combine ZFD, ECH & PER
## ID_ED : for every OD survey combine IDD4 (ville centre) & IDD3 (annee)
tripTable <- tripTable %>% 
  mutate(ID_IND = str_c(ZFD, "_", ECH, "_", PER),
         ID_ED = str_c(tripTable$IDD4, "_", tripTable$IDD3))

# Create 'purpose' variables
##O_PURPOSE et D_PURPOSE (D2A et D5A) in six groups
## 1 : home ; 2 : work ; 3 : study ; 4 : shopping ; 5 : leasure ; 6 : others

tripTable <- tripTable %>% 
  mutate(O_PURPOSE = plyr::mapvalues(D2A, c("", "01", "1","02", "2","11","12","13","14","21","22","23","24","96","25","26","27","28","97","29","30","31","32","33","34","35","98","41","42","43","44", "45", "51","52","53","54","61","62","63","64","71","72","73","74","81","82","91"), 
                                          c(NA,  1,    1,   1,   1,   2,   2,   2,   2,  3,   3,    3,   3,   3,  3,    3,   3,  3,   3,    3,  4,   4,   4,   4,   4,   4,   4,   5,   5,   5,    5,   5,    5,   5,    5,   5,  6,   6,   6,   6,   6,    6,   6,   6,   2,   5,  6)))
tripTable <- tripTable %>% 
  mutate(D_PURPOSE = plyr::mapvalues(D5A, c("", "01", "1","02", "2","11","12","13","14","21","22","23","24","96","25","26","27","28","97","29","30","31","32","33","34","35","98","41","42","43","44", "45", "51","52","53","54","61","62","63","64","71","72","73","74","81","82","91"), 
                                          c(NA,   1,    1,  1,   1,   2,   2,   2,   2,   3,   3,  3,   3,   3,   3,   3,   3,   3,   3,    3,   4,  4,    4,   4,   4,   4,   4,   5,   5,   5,   5,   5,    5,    5,   5,   5,  6,   6,    6,  6,    6,   6,   6,   6,   2,   5,   6)))


# Create 'transportation' variables 
## In three groups (from MODP) 
tripTable <- tripTable %>% 
  mutate(MODE = plyr::mapvalues(MODP, c("", "01", "1", "10","11", "12", "13", "14", "15", "16", "17", "18", "21", "22", "31", "32", "33", "37", "38", "39","41","42", "51", "61", "71", "81", "82", "91", "92", "93", "94", "95", "96"), 
                                 c(NA,3,3,3,3,3,2,2,2,2,2,2,2,2,1,1,1,1,1,1,1,1,1,2,2,2,2,2,1,3,3,2,2)))

## Mode adherent (MOD_ADH): walk, bike etc. = 1 
tripTable$MOD_ADH <- ifelse (tripTable$MODE==3, 1, 0)


# Build trip table 
tripTable <- tripTable %>%
  transmute(ID_IND, ID_ED, NDEP = as.character(NDEP), 
            RES_ZF = ZFD, RES_SEC = STD, 
            O_ZF = D3, O_SEC = STDO, 
            D_ZF = D7, D_SEC = STDD, 
            H_START = as.integer(substr(D4, 1, 2)), M_START = as.integer(substr(D4, 3, 4)),
            H_END = as.integer(substr(D8, 1, 2)), M_END = as.integer(substr(D8, 3, 4)),
            D9 = as.numeric(D9), O_PURPOSE, D_PURPOSE, MOD_ADH)

# Add centroids
## See in open-data : https://www.data.gouv.fr/fr/datasets/enquete-deplacements-en-loire-atlantique-2/
zonefine <- read_excel("opendata_44/07_denomination_perimetres_EDGT44_DTIR_D30_D10_D2.xls", 
                                                                 sheet = "EDGT44_2015_ZF")
zonefine <- zonefine %>% 
  mutate(CODE_ZF = ifelse(nchar(Id_zf_cerema) == 4, str_c("00", Id_zf_cerema), ifelse (nchar(Id_zf_cerema) == 5, str_c("0", Id_zf_cerema), Id_zf_cerema)))
        
zonefine$ZF_X <- zonefine$XL93
zonefine$ZF_Y <- zonefine$YL93
zonefine <- zonefine %>%
  transmute(CODE_ZF, ZF_X, ZF_Y)

tripTable <- merge(tripTable, zonefine, by.x="O_ZF", by.y="CODE_ZF", all.x= TRUE)
tripTable$O_ZF_X <- tripTable$ZF_X
tripTable$O_ZF_Y <- tripTable$ZF_Y
tripTable <- select (tripTable, -c(ZF_X, ZF_Y))

tripTable <- merge(tripTable, zonefine, by.x="D_ZF", by.y="CODE_ZF", all.x= TRUE)
tripTable$D_ZF_X <- tripTable$ZF_X
tripTable$D_ZF_Y <- tripTable$ZF_Y
tripTable <- select (tripTable, -c(ZF_X, ZF_Y))

# Build final trip table 
tripTable <- dplyr::arrange(tripTable, ID_IND, NDEP)
tripTable <- tripTable %>%
  transmute(ID_IND, ID_ED, NDEP, 
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
## See in open-data : https://www.data.gouv.fr/fr/datasets/enquete-deplacements-en-loire-atlantique-2/
indTable <- read_csv("opendata_44/02b_EDGT_44_PERSO_FAF_TEL_ModifPCS_2016-04-14.txt",col_names = FALSE)

# Extract variables
indTable <- indTable %>% 
  mutate(PP1 = substr(X1, 1, 1),
         IDP3 = "2015",
         IDP4 = "44000",
         ZFP = substr(X1, 2, 7),
         ECH = substr(X1, 8, 11),
         PER = substr(X1, 12, 13),
         GP1 = NA,
         STP = substr(X1, 2, 4),
         PENQ = substr(X1, 14, 14),
         P2 = substr(X1, 17, 17),
         P4 = substr(X1, 19, 20),
         P8 = substr(X1, 22, 22),
         METHOD = NA) %>% 
  select(-(X1)) %>% 
  mutate(P8 = ifelse(P8 == "0 ", "", P8))
  

# Select surveyed people (PENQ = 1) - 
## In phone surveys, only 1 or 2 members of households are surveyed
indTable <- filter(indTable, PENQ == "1")

## Create ID variables
## Place-based variables (Code secteurs et ZF de longueur 3 et 6)
## ID_IND : for every respondent combine ZFD, ECH et PER
## ID_ED : for every OD survey combine IDD4 (ville centre) & IDD3 (annee)
indTable <- indTable %>% 
 mutate(indECH = ifelse(nchar(ECH) == 1, str_c("000", ECH), 
                      ifelse(nchar(ECH) == 2, str_c("00", ECH), 
                             ifelse(nchar(ECH) == 3, str_c("0", ECH), ECH))),
        PER = ifelse(nchar(PER) == 1, str_c("0",PER), PER),
        ID_IND = str_c(ZFP, "_", ECH, "_", PER),
        ID_ED = str_c(IDP4, "_", IDP3))


# Create socio-demographic variables
## sex (SEX)
## 1: Male ; 2 : Female
indTable <- indTable %>%
  mutate(SEX = P2)

## age groups (KAGE) in three groups 
## 1: 15-29 yrs.; 2: 30-59 yrs.; 3: 60 yrs. and more.
## 0 if less than 15 yrs.
indTable <- indTable %>% 
    mutate(KAGE = case_when(as.numeric(P4) >= 15 & as.numeric(P4) <= 29 ~ 1,
                            as.numeric(P4) >= 30 & as.numeric(P4) <= 59 ~ 2,
                            as.numeric(P4) >= 60 ~ 3,
                           TRUE ~ 0))

## education groups (KEDUC) in three groups - Attention ! Differences according to Origin-destination surveys
## 1 : low (sans diplome - BEPC; CEP; BEP/CAP) ; 2 : middle (Bac - Bac+2) ; 3 : up (> Bac+2)
## For people "at school" : 1 (low) if [0-17 yrs.] / 2 (middle) if [18-24 yrs.] / 3 (up) if [25 yrs. and more]
indTable <- indTable %>% 
  mutate(KEDUC = plyr::mapvalues(P8, c("", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "93", "97", "90"), 
                                    c(NA,   9,   1,   1,   1,   2,   2,   3,   1,   2,   1,   NA,   NA,   NA)))

indTable$KEDUC = ifelse (indTable$KEDUC==9 & indTable$P4<18, 1,
                         ifelse (indTable$KEDUC==9 & indTable$P4>=18 & indTable$P4<25, 2,
                                 ifelse (indTable$KEDUC==9 & indTable$P4>=25, 3, indTable$KEDUC)))

# Add centroids of residential area (ZF)
indTable <- merge(indTable, zonefine, by.x="ZFP", by.y="CODE_ZF", all.x= TRUE)
indTable$RES_ZF_X <- indTable$ZF_X
indTable$RES_ZF_Y <- indTable$ZF_Y

# Build final individual table 
indTable <- dplyr::arrange(indTable, ID_IND)
indTable <- indTable %>%
  transmute(ID_IND, ID_ED, RES_ZF_CODE = ZFP, RES_SEC = STP,
            RES_ZF_X, RES_ZF_Y,
            SEX, AGE = as.numeric(P4), KAGE, KEDUC)

## Save final individual table
save(indTable, file = "scriptsr/data/H24_ind.RDS")
write.csv2(indTable, "scriptsr/data/H24_ind.csv", row.names = FALSE)
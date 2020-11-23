# ================================================================================#
# Create location table (H24_location) from trip table (H24_trip) and individual table (H24_ind)

# Valid for every French city region

# November 2020 - Julie Vallee, Aurelie Douet, Guillaume le Roux
# cf. Mobiliscope : https://mobiliscope.cnrs.fr
# ================================================================================#

# Chargement des bibliotheques
library(plyr)
library(stringr)
library(dplyr)
library(lubridate)
library(foreach)
library(readr)
library(tidyr)
library(rgdal)
library(sp)
library(sf)
library(rgeos)
library(PBSmapping)
library(maptools)
library(geosphere)


# Load trip table and individual table

indTable <- read_delim("~/H24/H24_Library/scriptsr/data/H24_ind.csv",";", escape_double = FALSE, trim_ws = TRUE)
tripTable <- read_delim("~/H24/H24_Library/scriptsr/data/H24_trip.csv",";", escape_double = FALSE, trim_ws = TRUE)

# Reframe hours/minutes variables

  ## Rearrange trip table
  tripTable <- arrange(tripTable, ID_IND, H_START, M_START)
  
  ## Delete trips outside 4pm-4pm
  tripTable <- filter(tripTable, H_START>=4 | H_END>=4)
  tripTable <- filter(tripTable, H_START<=28 | H_END<=28)
 
  ## Adjust min (min=0) when h=28 ou h=4 
  tripTable$M_END[tripTable$H_END>=28] <- 0
  tripTable$H_END[tripTable$H_END>=28] <- 28
  tripTable$M_END[tripTable$H_START<4] <- 0
  tripTable$H_END[tripTable$H_START<4] <- 4

  
  ## Create variables HEURE_DEB et HEURE_FIN (format ISO)
  tripTable$HEURE_FIN <- ifelse(tripTable$H_END>23, as.character.Date(ISOdatetime(2010,1,2,tripTable$H_END-24,tripTable$M_END,0)),
                                as.character.Date(ISOdatetime(2010,1,1,tripTable$H_END,tripTable$M_END,0)) )
  tripTable$HEURE_DEB <- ifelse(tripTable$H_START>23, as.character.Date(ISOdatetime(2010,1,2,tripTable$H_START-24,tripTable$M_START,0)),
                                as.character.Date(ISOdatetime(2010,1,1,tripTable$H_START,tripTable$M_START,0)) )
  
  ## Compute duration
  tripTable$duree <- as.numeric(difftime(ymd_hms(tripTable$HEURE_FIN, truncated=3),ymd_hms(tripTable$HEURE_DEB, truncated=3), units= "mins"))
  
  ## Delete individuals with missing data about hours
  IDhNA <- data.frame(ID_IND = character(length(unique(tripTable$ID_IND[is.na(tripTable$H_START)==FALSE & is.na(tripTable$H_END)==FALSE]))))
  IDhNA$ID_IND <- unique(tripTable$ID_IND[is.na(tripTable$H_START)==FALSE & is.na(tripTable$H_END)==FALSE])
  tripTable <-  merge(x = tripTable, y = IDhNA, by = "ID_IND", all = FALSE)
  tripTable <- arrange(tripTable, ID_IND, H_START, M_START)
  
# Create variable number of trips/individuals (nobs)                        
tripTable_GRP <- group_by(.data = tripTable, ID_IND)
nobs <- as.data.frame(dplyr::summarize(tripTable_GRP, nobs = n()))
tripTable_GRP <- merge(x = tripTable_GRP, y = nobs, by = "ID_IND", all =  TRUE)
  


# Build location table (prezTable)
  
  ## Table initialisation 
  prezTable <- data.frame(ID_IND = character (0), ID_ED = character(0), ID_ORDRE = integer(0), 
                          CODE_ZF = character(0), CODE_SEC = character(0), 
                          ZF_X =character(0), ZF_Y=character(0),
                          HEURE_DEB = character(0), HEURE_FIN = character(0), DUREE = numeric(0), 
                          MOTIF = integer(0), 
                          stringsAsFactors = FALSE )
  
  ## Fill location table
  VecID <- unique(tripTable_GRP$ID_IND)
  
  VecID1 <- VecID[1:4000]
  VecID2 <- VecID[4001:8000]
  VecID3 <- VecID[8001:12000]
  VecID4 <- VecID[12001:16000]
  VecID5 <- VecID[16001:20000]
  VecID6 <- VecID[20001:24000]
  VecID7 <- VecID[24001:28000]
  VecID8 <- VecID[28001:length(VecID)]
  
  ##### Prez1 #######
  prezTable_1 <- data.frame(ID_IND = character (0), ID_ED = character(0), ID_ORDRE = integer(0),  
                            CODE_ZF = character(0), CODE_SEC = character(0), 
                            ZF_X = character(0),ZF_Y = character(0),
                            HEURE_DEB = character(0), HEURE_FIN = character(0), DUREE = numeric(0),
                            MOTIF = integer(0),
                            stringsAsFactors = FALSE)
  
  invisible(foreach (ind = VecID1, .verbose = FALSE) %do% {
    
    depl_ind <- filter(tripTable_GRP, tripTable_GRP$ID_IND == ind)
    nobs <- depl_ind$nobs[1]
    
    prez_ind <- data.frame(ID_IND = character (nobs+1), ID_ED = character(nobs+1), ID_ORDRE = integer(nobs+1),
                           CODE_ZF = character(nobs+1), CODE_SEC = character(nobs+1), 
                           ZF_X = character(nobs+1),  ZF_Y = character(nobs+1),
                           HEURE_DEB = character(nobs+1), HEURE_FIN = character(nobs+1), DUREE = numeric(nobs+1), 
                           MOTIF = integer(nobs+1),
                           stringsAsFactors = FALSE)
    
    prez_ind[ , 1] <- rep(depl_ind$ID_IND[1], nobs+1)
    prez_ind[ , 2] <- rep(depl_ind$ID_ED[1], nobs+1)
    prez_ind[ , 3] <- 1:(nobs+1)
    prez_ind[1, 4] <- depl_ind$O_ZF[1]
    prez_ind[1, 5] <- depl_ind$O_SEC[1]
    prez_ind[1, 6] <- depl_ind$O_ZF_X[1]
    prez_ind[1, 7] <- depl_ind$O_ZF_Y[1]
    prez_ind[1, 8] <- as.character.Date(ISOdatetime(2010,1,1,4,0,0))
    prez_ind[nobs+1, 9] <- as.character.Date(ISOdatetime(2010,1,2,4,0,0))
    prez_ind[1, 11] <- depl_ind$O_PURPOSE[1]

    
    for (i in 1:nobs){
      prez_ind[i+1, 4] <- depl_ind$D_ZF[i]
      prez_ind[i+1, 5] <- depl_ind$D_SEC[i]
      prez_ind[i+1, 6] <- depl_ind$D_ZF_X[i]
      prez_ind[i+1, 7] <- depl_ind$D_ZF_Y[i]
      prez_ind[i+1, 8] <- ifelse(depl_ind$H_END[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_END[i]-24,depl_ind$M_END[i],0)),
                                 as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_END[i],depl_ind$M_END[i],0)) )
      prez_ind[i, 9] <- ifelse(depl_ind$H_START[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_START[i]-24,depl_ind$M_START[i],0)),
                               as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_START[i],depl_ind$M_START[i],0)) )
      prez_ind[i+1, 11] <- depl_ind$D_PURPOSE[i]
      
    }
    prezTable_1 <-rbind(prezTable_1, prez_ind)
    
  })
  
  
  ##### Prez2 #######
  prezTable_2 <- data.frame(ID_IND = character (0), ID_ED = character(0), ID_ORDRE = integer(0),  
                            CODE_ZF = character(0), CODE_SEC = character(0), 
                            ZF_X = character(0),ZF_Y = character(0),
                            HEURE_DEB = character(0), HEURE_FIN = character(0), DUREE = numeric(0),
                            MOTIF = integer(0),
                            stringsAsFactors = FALSE)
  
  invisible(foreach (ind = VecID2, .verbose = FALSE) %do% {
    
    depl_ind <- filter(tripTable_GRP, tripTable_GRP$ID_IND == ind)
    nobs <- depl_ind$nobs[1]
    
    prez_ind <- data.frame(ID_IND = character (nobs+1), ID_ED = character(nobs+1), ID_ORDRE = integer(nobs+1),
                           CODE_ZF = character(nobs+1), CODE_SEC = character(nobs+1), 
                           ZF_X = character(nobs+1),  ZF_Y = character(nobs+1),
                           HEURE_DEB = character(nobs+1), HEURE_FIN = character(nobs+1), DUREE = numeric(nobs+1), 
                           MOTIF = integer(nobs+1),
                           stringsAsFactors = FALSE)
    
    prez_ind[ , 1] <- rep(depl_ind$ID_IND[1], nobs+1)
    prez_ind[ , 2] <- rep(depl_ind$ID_ED[1], nobs+1)
    prez_ind[ , 3] <- 1:(nobs+1)
    prez_ind[1, 4] <- depl_ind$O_ZF[1]
    prez_ind[1, 5] <- depl_ind$O_SEC[1]
    prez_ind[1, 6] <- depl_ind$O_ZF_X[1]
    prez_ind[1, 7] <- depl_ind$O_ZF_Y[1]
    prez_ind[1, 8] <- as.character.Date(ISOdatetime(2010,1,1,4,0,0))
    prez_ind[nobs+1, 9] <- as.character.Date(ISOdatetime(2010,1,2,4,0,0))
    prez_ind[1, 11] <- depl_ind$O_PURPOSE[1]
    
    
    for (i in 1:nobs){
      prez_ind[i+1, 4] <- depl_ind$D_ZF[i]
      prez_ind[i+1, 5] <- depl_ind$D_SEC[i]
      prez_ind[i+1, 6] <- depl_ind$D_ZF_X[i]
      prez_ind[i+1, 7] <- depl_ind$D_ZF_Y[i]
      prez_ind[i+1, 8] <- ifelse(depl_ind$H_END[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_END[i]-24,depl_ind$M_END[i],0)),
                                 as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_END[i],depl_ind$M_END[i],0)) )
      prez_ind[i, 9] <- ifelse(depl_ind$H_START[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_START[i]-24,depl_ind$M_START[i],0)),
                               as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_START[i],depl_ind$M_START[i],0)) )
      
      prez_ind[i,9] <-  as.character.Date(ymd_hms(prez_ind[i,9], truncated=3) + minutes(ceiling(depl_ind$duree[i]/2)))
     
      prez_ind[i+1, 11] <- depl_ind$D_PURPOSE[i]
      
    }
    prezTable_2 <-rbind(prezTable_2, prez_ind)
    
  })
  
  
  ##### Prez3 #######
  prezTable_3 <- data.frame(ID_IND = character (0), ID_ED = character(0), ID_ORDRE = integer(0),  
                            CODE_ZF = character(0), CODE_SEC = character(0), 
                            ZF_X = character(0),ZF_Y = character(0),
                            HEURE_DEB = character(0), HEURE_FIN = character(0), DUREE = numeric(0),
                            MOTIF = integer(0),
                            stringsAsFactors = FALSE)
  
  invisible(foreach (ind = VecID3, .verbose = FALSE) %do% {
    
    depl_ind <- filter(tripTable_GRP, tripTable_GRP$ID_IND == ind)
    nobs <- depl_ind$nobs[1]
    
    prez_ind <- data.frame(ID_IND = character (nobs+1), ID_ED = character(nobs+1), ID_ORDRE = integer(nobs+1),
                           CODE_ZF = character(nobs+1), CODE_SEC = character(nobs+1), 
                           ZF_X = character(nobs+1),  ZF_Y = character(nobs+1),
                           HEURE_DEB = character(nobs+1), HEURE_FIN = character(nobs+1), DUREE = numeric(nobs+1), 
                           MOTIF = integer(nobs+1),
                           stringsAsFactors = FALSE)
    
    prez_ind[ , 1] <- rep(depl_ind$ID_IND[1], nobs+1)
    prez_ind[ , 2] <- rep(depl_ind$ID_ED[1], nobs+1)
    prez_ind[ , 3] <- 1:(nobs+1)
    prez_ind[1, 4] <- depl_ind$O_ZF[1]
    prez_ind[1, 5] <- depl_ind$O_SEC[1]
    prez_ind[1, 6] <- depl_ind$O_ZF_X[1]
    prez_ind[1, 7] <- depl_ind$O_ZF_Y[1]
    prez_ind[1, 8] <- as.character.Date(ISOdatetime(2010,1,1,4,0,0))
    prez_ind[nobs+1, 9] <- as.character.Date(ISOdatetime(2010,1,2,4,0,0))
    prez_ind[1, 11] <- depl_ind$O_PURPOSE[1]
    
    
    for (i in 1:nobs){
      prez_ind[i+1, 4] <- depl_ind$D_ZF[i]
      prez_ind[i+1, 5] <- depl_ind$D_SEC[i]
      prez_ind[i+1, 6] <- depl_ind$D_ZF_X[i]
      prez_ind[i+1, 7] <- depl_ind$D_ZF_Y[i]
      prez_ind[i+1, 8] <- ifelse(depl_ind$H_END[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_END[i]-24,depl_ind$M_END[i],0)),
                                 as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_END[i],depl_ind$M_END[i],0)) )
      prez_ind[i, 9] <- ifelse(depl_ind$H_START[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_START[i]-24,depl_ind$M_START[i],0)),
                               as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_START[i],depl_ind$M_START[i],0)) )
     
      prez_ind[i+1, 11] <- depl_ind$D_PURPOSE[i]
      
    }
    prezTable_3 <-rbind(prezTable_3, prez_ind)
    
  })
  
  ##### Prez4 #######
  prezTable_4 <- data.frame(ID_IND = character (0), ID_ED = character(0), ID_ORDRE = integer(0),  
                            CODE_ZF = character(0), CODE_SEC = character(0), 
                            ZF_X = character(0),ZF_Y = character(0),
                            HEURE_DEB = character(0), HEURE_FIN = character(0), DUREE = numeric(0),
                            MOTIF = integer(0),
                            stringsAsFactors = FALSE)
  
  invisible(foreach (ind = VecID4, .verbose = FALSE) %do% {
    
    depl_ind <- filter(tripTable_GRP, tripTable_GRP$ID_IND == ind)
    nobs <- depl_ind$nobs[1]
    
    prez_ind <- data.frame(ID_IND = character (nobs+1), ID_ED = character(nobs+1), ID_ORDRE = integer(nobs+1),
                           CODE_ZF = character(nobs+1), CODE_SEC = character(nobs+1), 
                           ZF_X = character(nobs+1),  ZF_Y = character(nobs+1),
                           HEURE_DEB = character(nobs+1), HEURE_FIN = character(nobs+1), DUREE = numeric(nobs+1), 
                           MOTIF = integer(nobs+1),
                           stringsAsFactors = FALSE)
    
    prez_ind[ , 1] <- rep(depl_ind$ID_IND[1], nobs+1)
    prez_ind[ , 2] <- rep(depl_ind$ID_ED[1], nobs+1)
    prez_ind[ , 3] <- 1:(nobs+1)
    prez_ind[1, 4] <- depl_ind$O_ZF[1]
    prez_ind[1, 5] <- depl_ind$O_SEC[1]
    prez_ind[1, 6] <- depl_ind$O_ZF_X[1]
    prez_ind[1, 7] <- depl_ind$O_ZF_Y[1]
    prez_ind[1, 8] <- as.character.Date(ISOdatetime(2010,1,1,4,0,0))
    prez_ind[nobs+1, 9] <- as.character.Date(ISOdatetime(2010,1,2,4,0,0))
    prez_ind[1, 11] <- depl_ind$O_PURPOSE[1]
    
    
    for (i in 1:nobs){
      prez_ind[i+1, 4] <- depl_ind$D_ZF[i]
      prez_ind[i+1, 5] <- depl_ind$D_SEC[i]
      prez_ind[i+1, 6] <- depl_ind$D_ZF_X[i]
      prez_ind[i+1, 7] <- depl_ind$D_ZF_Y[i]
      prez_ind[i+1, 8] <- ifelse(depl_ind$H_END[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_END[i]-24,depl_ind$M_END[i],0)),
                                 as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_END[i],depl_ind$M_END[i],0)) )
      prez_ind[i, 9] <- ifelse(depl_ind$H_START[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_START[i]-24,depl_ind$M_START[i],0)),
                               as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_START[i],depl_ind$M_START[i],0)) )

      prez_ind[i+1, 11] <- depl_ind$D_PURPOSE[i]
      
    }
    prezTable_4 <-rbind(prezTable_4, prez_ind)
    
  })
  
  ##### Prez5 #######
  prezTable_5 <- data.frame(ID_IND = character (0), ID_ED = character(0), ID_ORDRE = integer(0),  
                            CODE_ZF = character(0), CODE_SEC = character(0), 
                            ZF_X = character(0),ZF_Y = character(0),
                            HEURE_DEB = character(0), HEURE_FIN = character(0), DUREE = numeric(0),
                            MOTIF = integer(0),
                            stringsAsFactors = FALSE)
  
  invisible(foreach (ind = VecID5, .verbose = FALSE) %do% {
    
    depl_ind <- filter(tripTable_GRP, tripTable_GRP$ID_IND == ind)
    nobs <- depl_ind$nobs[1]
    
    prez_ind <- data.frame(ID_IND = character (nobs+1), ID_ED = character(nobs+1), ID_ORDRE = integer(nobs+1),
                           CODE_ZF = character(nobs+1), CODE_SEC = character(nobs+1), 
                           ZF_X = character(nobs+1),  ZF_Y = character(nobs+1),
                           HEURE_DEB = character(nobs+1), HEURE_FIN = character(nobs+1), DUREE = numeric(nobs+1), 
                           MOTIF = integer(nobs+1),
                           stringsAsFactors = FALSE)
    
    prez_ind[ , 1] <- rep(depl_ind$ID_IND[1], nobs+1)
    prez_ind[ , 2] <- rep(depl_ind$ID_ED[1], nobs+1)
    prez_ind[ , 3] <- 1:(nobs+1)
    prez_ind[1, 4] <- depl_ind$O_ZF[1]
    prez_ind[1, 5] <- depl_ind$O_SEC[1]
    prez_ind[1, 6] <- depl_ind$O_ZF_X[1]
    prez_ind[1, 7] <- depl_ind$O_ZF_Y[1]
    prez_ind[1, 8] <- as.character.Date(ISOdatetime(2010,1,1,4,0,0))
    prez_ind[nobs+1, 9] <- as.character.Date(ISOdatetime(2010,1,2,4,0,0))
    prez_ind[1, 11] <- depl_ind$O_PURPOSE[1]
    
    
    for (i in 1:nobs){
      prez_ind[i+1, 4] <- depl_ind$D_ZF[i]
      prez_ind[i+1, 5] <- depl_ind$D_SEC[i]
      prez_ind[i+1, 6] <- depl_ind$D_ZF_X[i]
      prez_ind[i+1, 7] <- depl_ind$D_ZF_Y[i]
      prez_ind[i+1, 8] <- ifelse(depl_ind$H_END[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_END[i]-24,depl_ind$M_END[i],0)),
                                 as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_END[i],depl_ind$M_END[i],0)) )
      prez_ind[i, 9] <- ifelse(depl_ind$H_START[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_START[i]-24,depl_ind$M_START[i],0)),
                               as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_START[i],depl_ind$M_START[i],0)) )

      prez_ind[i+1, 11] <- depl_ind$D_PURPOSE[i]
      
    }
    prezTable_5 <-rbind(prezTable_5, prez_ind)
    
  })
  
  ##### Prez6 #######
  prezTable_6 <- data.frame(ID_IND = character (0), ID_ED = character(0), ID_ORDRE = integer(0),  
                            CODE_ZF = character(0), CODE_SEC = character(0), 
                            ZF_X = character(0),ZF_Y = character(0),
                            HEURE_DEB = character(0), HEURE_FIN = character(0), DUREE = numeric(0),
                            MOTIF = integer(0),
                            stringsAsFactors = FALSE)
  
  invisible(foreach (ind = VecID6, .verbose = FALSE) %do% {
    
    depl_ind <- filter(tripTable_GRP, tripTable_GRP$ID_IND == ind)
    nobs <- depl_ind$nobs[1]
    
    prez_ind <- data.frame(ID_IND = character (nobs+1), ID_ED = character(nobs+1), ID_ORDRE = integer(nobs+1),
                           CODE_ZF = character(nobs+1), CODE_SEC = character(nobs+1), 
                           ZF_X = character(nobs+1),  ZF_Y = character(nobs+1),
                           HEURE_DEB = character(nobs+1), HEURE_FIN = character(nobs+1), DUREE = numeric(nobs+1), 
                           MOTIF = integer(nobs+1),
                           stringsAsFactors = FALSE)
    
    prez_ind[ , 1] <- rep(depl_ind$ID_IND[1], nobs+1)
    prez_ind[ , 2] <- rep(depl_ind$ID_ED[1], nobs+1)
    prez_ind[ , 3] <- 1:(nobs+1)
    prez_ind[1, 4] <- depl_ind$O_ZF[1]
    prez_ind[1, 5] <- depl_ind$O_SEC[1]
    prez_ind[1, 6] <- depl_ind$O_ZF_X[1]
    prez_ind[1, 7] <- depl_ind$O_ZF_Y[1]
    prez_ind[1, 8] <- as.character.Date(ISOdatetime(2010,1,1,4,0,0))
    prez_ind[nobs+1, 9] <- as.character.Date(ISOdatetime(2010,1,2,4,0,0))
    prez_ind[1, 11] <- depl_ind$O_PURPOSE[1]
    
    
    for (i in 1:nobs){
      prez_ind[i+1, 4] <- depl_ind$D_ZF[i]
      prez_ind[i+1, 5] <- depl_ind$D_SEC[i]
      prez_ind[i+1, 6] <- depl_ind$D_ZF_X[i]
      prez_ind[i+1, 7] <- depl_ind$D_ZF_Y[i]
      prez_ind[i+1, 8] <- ifelse(depl_ind$H_END[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_END[i]-24,depl_ind$M_END[i],0)),
                                 as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_END[i],depl_ind$M_END[i],0)) )
      prez_ind[i, 9] <- ifelse(depl_ind$H_START[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_START[i]-24,depl_ind$M_START[i],0)),
                               as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_START[i],depl_ind$M_START[i],0)) )

      prez_ind[i+1, 11] <- depl_ind$D_PURPOSE[i]
      
    }
    prezTable_6 <-rbind(prezTable_6, prez_ind)
    
  })
  
  ##### Prez7 #######
  prezTable_7 <- data.frame(ID_IND = character (0), ID_ED = character(0), ID_ORDRE = integer(0),  
                            CODE_ZF = character(0), CODE_SEC = character(0), 
                            ZF_X = character(0),ZF_Y = character(0),
                            HEURE_DEB = character(0), HEURE_FIN = character(0), DUREE = numeric(0),
                            MOTIF = integer(0),
                            stringsAsFactors = FALSE)
  
  invisible(foreach (ind = VecID7, .verbose = FALSE) %do% {
    
    depl_ind <- filter(tripTable_GRP, tripTable_GRP$ID_IND == ind)
    nobs <- depl_ind$nobs[1]
    
    prez_ind <- data.frame(ID_IND = character (nobs+1), ID_ED = character(nobs+1), ID_ORDRE = integer(nobs+1),
                           CODE_ZF = character(nobs+1), CODE_SEC = character(nobs+1), 
                           ZF_X = character(nobs+1),  ZF_Y = character(nobs+1),
                           HEURE_DEB = character(nobs+1), HEURE_FIN = character(nobs+1), DUREE = numeric(nobs+1), 
                           MOTIF = integer(nobs+1),
                           stringsAsFactors = FALSE)
    
    prez_ind[ , 1] <- rep(depl_ind$ID_IND[1], nobs+1)
    prez_ind[ , 2] <- rep(depl_ind$ID_ED[1], nobs+1)
    prez_ind[ , 3] <- 1:(nobs+1)
    prez_ind[1, 4] <- depl_ind$O_ZF[1]
    prez_ind[1, 5] <- depl_ind$O_SEC[1]
    prez_ind[1, 6] <- depl_ind$O_ZF_X[1]
    prez_ind[1, 7] <- depl_ind$O_ZF_Y[1]
    prez_ind[1, 8] <- as.character.Date(ISOdatetime(2010,1,1,4,0,0))
    prez_ind[nobs+1, 9] <- as.character.Date(ISOdatetime(2010,1,2,4,0,0))
    prez_ind[1, 11] <- depl_ind$O_PURPOSE[1]
    
    
    for (i in 1:nobs){
      prez_ind[i+1, 4] <- depl_ind$D_ZF[i]
      prez_ind[i+1, 5] <- depl_ind$D_SEC[i]
      prez_ind[i+1, 6] <- depl_ind$D_ZF_X[i]
      prez_ind[i+1, 7] <- depl_ind$D_ZF_Y[i]
      prez_ind[i+1, 8] <- ifelse(depl_ind$H_END[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_END[i]-24,depl_ind$M_END[i],0)),
                                 as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_END[i],depl_ind$M_END[i],0)) )
      prez_ind[i, 9] <- ifelse(depl_ind$H_START[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_START[i]-24,depl_ind$M_START[i],0)),
                               as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_START[i],depl_ind$M_START[i],0)) )
      # if (depl_ind$MOD_ADH[i]==1){
      #   prez_ind[i+1,8] <-as.character.Date(ymd_hms(prez_ind[i+1,8], truncated=3) - minutes(floor(depl_ind$duree[i]/2)))
      #   prez_ind[i,9] <-  as.character.Date(ymd_hms(prez_ind[i,9], truncated=3) + minutes(ceiling(depl_ind$duree[i]/2)))
      # }
      prez_ind[i+1, 11] <- depl_ind$D_PURPOSE[i]
      
    }
    prezTable_7 <-rbind(prezTable_7, prez_ind)
    
  })
  
  ##### Prez8 #######
  prezTable_8 <- data.frame(ID_IND = character (0), ID_ED = character(0), ID_ORDRE = integer(0),  
                            CODE_ZF = character(0), CODE_SEC = character(0), 
                            ZF_X = character(0),ZF_Y = character(0),
                            HEURE_DEB = character(0), HEURE_FIN = character(0), DUREE = numeric(0),
                            MOTIF = integer(0),
                            stringsAsFactors = FALSE)
  
  invisible(foreach (ind = VecID8, .verbose = FALSE) %do% {
    
    depl_ind <- filter(tripTable_GRP, tripTable_GRP$ID_IND == ind)
    nobs <- depl_ind$nobs[1]
    
    prez_ind <- data.frame(ID_IND = character (nobs+1), ID_ED = character(nobs+1), ID_ORDRE = integer(nobs+1),
                           CODE_ZF = character(nobs+1), CODE_SEC = character(nobs+1), 
                           ZF_X = character(nobs+1),  ZF_Y = character(nobs+1),
                           HEURE_DEB = character(nobs+1), HEURE_FIN = character(nobs+1), DUREE = numeric(nobs+1), 
                           MOTIF = integer(nobs+1),
                           stringsAsFactors = FALSE)
    
    prez_ind[ , 1] <- rep(depl_ind$ID_IND[1], nobs+1)
    prez_ind[ , 2] <- rep(depl_ind$ID_ED[1], nobs+1)
    prez_ind[ , 3] <- 1:(nobs+1)
    prez_ind[1, 4] <- depl_ind$O_ZF[1]
    prez_ind[1, 5] <- depl_ind$O_SEC[1]
    prez_ind[1, 6] <- depl_ind$O_ZF_X[1]
    prez_ind[1, 7] <- depl_ind$O_ZF_Y[1]
    prez_ind[1, 8] <- as.character.Date(ISOdatetime(2010,1,1,4,0,0))
    prez_ind[nobs+1, 9] <- as.character.Date(ISOdatetime(2010,1,2,4,0,0))
    prez_ind[1, 11] <- depl_ind$O_PURPOSE[1]
    
    
    for (i in 1:nobs){
      prez_ind[i+1, 4] <- depl_ind$D_ZF[i]
      prez_ind[i+1, 5] <- depl_ind$D_SEC[i]
      prez_ind[i+1, 6] <- depl_ind$D_ZF_X[i]
      prez_ind[i+1, 7] <- depl_ind$D_ZF_Y[i]
      prez_ind[i+1, 8] <- ifelse(depl_ind$H_END[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_END[i]-24,depl_ind$M_END[i],0)),
                                 as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_END[i],depl_ind$M_END[i],0)) )
      prez_ind[i, 9] <- ifelse(depl_ind$H_START[i]>23, as.character.Date(ISOdatetime(2010,1,2,depl_ind$H_START[i]-24,depl_ind$M_START[i],0)),
                               as.character.Date(ISOdatetime(2010,1,1,depl_ind$H_START[i],depl_ind$M_START[i],0)) )

      prez_ind[i+1, 11] <- depl_ind$D_PURPOSE[i]
      
    }
    prezTable_8 <-rbind(prezTable_8, prez_ind)
    
  })
  
  prezTable <-rbind(prezTable_1, prezTable_2, prezTable_3, prezTable_4, prezTable_5, prezTable_6, prezTable_7, prezTable_8)
  
  ## Individuals without trips (stay at home during the 24h period)
  prezNonDepl <- anti_join(x = select(indTable, ID_IND, ID_ED, CODE_ZF = RES_ZF_CODE, CODE_SEC = RES_SEC, ZF_X=RES_ZF_X, ZF_Y=RES_ZF_Y ), y = prezTable, by = "ID_IND")
  prezNonDepl <- prezNonDepl %>% 
  transmute(ID_IND, ID_ED, ID_ORDRE = 1, CODE_ZF, CODE_SEC, ZF_X, ZF_Y, HEURE_DEB = as.character.Date(ISOdatetime(2010,1,1,4,0,0)), HEURE_FIN = as.character.Date(ISOdatetime(2010,1,2,4,0,0)),
           DUREE = 24*60, MOTIF = 1)

  ### Combine the two locations tables (with and without trips during the 24h period)
    prezTable <-rbind(prezTable, prezNonDepl)
    prezTable <- dplyr::arrange(prezTable, ID_IND, HEURE_DEB)
 
  
  ## Create temporal variables
  
    ### Delete artefacts (null duration)
    prezTable <- filter(prezTable, prezTable$HEURE_DEB!=as.character.Date(ISOdatetime(2010,1,1,4,0,0))|
                        prezTable$HEURE_FIN!=as.character.Date(ISOdatetime(2010,1,1,4,0,0)))
    prezTable <- filter(prezTable, prezTable$HEURE_DEB!=as.character.Date(ISOdatetime(2010,1,2,4,0,0))|
                        prezTable$HEURE_FIN!=as.character.Date(ISOdatetime(2010,1,2,4,0,0)))
  
    
    ### Compute duration
    prezTable$DUREE <- as.numeric(difftime(ymd_hms(prezTable$HEURE_FIN, truncated=3),ymd_hms(prezTable$HEURE_DEB, truncated=3), units= "mins"))

  
  ## Add data from individual table
  prezTable <- left_join(prezTable, y = select(indTable, ID_IND, RES_ZF_CODE, RES_ZF_X, RES_ZF_Y, SEX, AGE, KAGE, KEDUC), by = "ID_IND")
  prezTable <- filter(prezTable, AGE>=5)
  
  
# Save final location table
save(prezTable, file = "~/H24/H24_Library/scriptsr/data/H24_location.RDS")
  
write.csv2(prezTable, "~/H24/H24_Library/scriptsr/data/H24_location.csv", row.names = FALSE)


# Build final location table without ID
prezTable_noID <- prezTable %>%
  transmute(ID_ED, CODE_ZF, ZF_X,ZF_Y,
            HEURE_DEB, HEURE_FIN, DUREE,
            MOTIF,
            RES_ZF_CODE, RES_ZF_X, RES_ZF_Y, 
            SEX, KAGE, KEDUC)

# Save final location table with no ID
save(prezTable_noID, file = "~/H24/H24_Library/scriptsr/data/H24_location_noID.RDS")

write.csv2(prezTable_noID, "~/H24/H24_Library/scriptsr/data/H24_location_noID.csv", row.names = FALSE)

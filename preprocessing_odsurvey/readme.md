# h24

## Step 1. Standardize OD Data
Get database from Origin-Destination survey. Following tables are necessary: trip database, people database, GIS database

You have to standardize trip database and people database issued from origin-destination surveys with Rscritps located in preprocessing_odsurvey/scriptR/

- For Loire-Atlantique-Nantes (dep 44), let's use EDGT 2015 (open data available here https://www.data.gouv.fr/fr/datasets/enquete-deplacements-en-loire-atlantique-2/) and run the standardize_OD_NantesRegion.R script
- For the Paris region (the whole Île-de-France), use EGT 2010 (if you have access to data) and run the standardize_OD_ParisRegion.R script

Whatever the region, you should get two tables: H24_trip.csv & H24_ind.csv

Notes
- Trip dataset is limited to weekday trips. We considered weekday trips as occurring an “average working day” (Monday to Friday)
- We kept trips occurring between 4:00 am (day before survey) and 3:59 am (day of survey) and removed trips outside this window.

## Step 2. Create location table from OD data
Secondly, run the generate_location.R script (located in preprocessing_odsurvey/scriptR/) 
You should get location table (H24_location.csv) with location of every respondent around the clock

Location attributes
- ZF_X & ZF_Y: coordinates: X Y – centroids of the area – projection L93
- CODE_ZF: ID of the area
- HEURE_DEB &  HEURE_FIN: start & end time (hh:mn)
- DUREE: duration (in minutes)
- MOTIF: home (1) ; work (2) ; study (3) ; shopping (4) ; leasure (5) ; others (6)

Respondent attributes
- RES_ZF_X &  RES_ZF_Y: coordinates X Y – centroids of the residential area – projection L93
- RES_ZF_CODE: ID of the residential area
- SEX: Male (1); Female (2)
- AGE: in years
- KAGE: Age in groups. 5-14 yrs (0); 15-29 yrs (1); 30-59 yrs (2); 60 yrs. and more (3)
- KEDUC: Educational level in groups (from their last achieved qualification). Low (no diploma - BEPC; CEP; BEP/CAP) (1) ; Middle (Bac-Bac+2) (2) ; Up (>Bac+2) (3). 
For respondents still at school, we defined their education level from their age : low for students less than 18 yrs.; middle for students between 18 and 24 yrs.; up for students aged 25 or more.

Notes :
- In the location table, transportation periods were removed.
- In the location table, respondents who reported staying at home all day (whatever the reason) were assigned to their place of residence over the entire observation period.

You can also directly download resulting location tables (in data-output)
- for Loire-Atlantique (EDGT 2015):  H24_location_NantesRegion_noID.csv
- for the whole Île-de-France (EGT 2010): H24_location_ParisRegion_noID.csv
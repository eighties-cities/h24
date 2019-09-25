# h24

Yet another synthetic population generator.

## Install

sbt publishLocal

## Generate a synthetic population for your study area

### Get Files for your study area
Run the getData.sh script. It will:
- get the **CONTOURS-IRIS** files from http://professionnel.ign.fr/contoursiris for instance: https://wxs-telechargement.ign.fr/1yhlj2ehpqf3q6dt6a2y7b64/telechargement/inspire/CONTOURS-IRIS-FRANCE-2014-01-01$CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01/file/CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01.7z
- get **R_rfl09_LAEA1000** file from https://www.insee.fr/fr/statistiques/fichier/1405815/ECP1KM_09_MET.zip
- get **base-ic-evol-struct-pop** file from https://www.insee.fr/fr/statistiques/fichier/2028582/infra-population-2012.zip
- get **base-ic-diplomes-formation** file from https://www.insee.fr/fr/statistiques/fichier/2028265/infra-formation-2012.zip

You should now have a "data" directory with all the relevant data in it.
Note: We should have these files on IPFS very soon.

### Select the relevant data for your study area
- geographically for **CONTOURS-IRIS** and **R_rfl09_LAEA1000** (could be done automatically)
- by selection using *DEP*, *COM* or other relevant codes for **base-ic-evol-struct-pop** and **base-ic-diplomes-formation** (keep headers and everything, just filter the data)
- export the latter files as *CSV* and create *csv.lzma* files

This whole process should be automatically done in the future (given the geographical boundaries of the study area for instance).


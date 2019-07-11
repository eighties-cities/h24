# h24

Yet another synthetic population generator.

## Generate a synthetic population for your study area

### Get Files for your study area
- get **CONTOURS-IRIS** file from http://professionnel.ign.fr/contoursiris
- get **R_rfl09_LAEA1000** from https://www.insee.fr/fr/statistiques/fichier/1405815/ECP1KM_09_MET.zip
- get **base-ic-evol-struct-pop** from https://www.insee.fr/fr/statistiques/fichier/2028582/infra-population-2012.zip
- get **base-ic-diplomes-formation** from https://www.insee.fr/fr/statistiques/fichier/2028265/infra-formation-2012.zip

We should have these files on IPFS very soon.

### Select the relevant data for your study area
- geographically for **CONTOURS-IRIS** and **R_rfl09_LAEA1000** (could be done automatically)
- by selection using *DEP*, *COM* or other relevant codes for **base-ic-evol-struct-pop** and **base-ic-diplomes-formation** (keep headers and everything, just filter the data)
- export the latter files as *CSV* and create *csv.lzma* files

This whole process should be automatically done in the future (given the geographical boundaries of the study area for instance).


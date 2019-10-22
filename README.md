# h24

Yet another synthetic population generator.

## Install

```shell script
sbt publishLocal
```

## Generate a synthetic population for your study area

### Get Files for your study area
Run the getData.sh script.
```shell script
source getData.sh
```
It will:
- download the **CONTOURS-IRIS** files from http://professionnel.ign.fr/contoursiris for instance: https://wxs-telechargement.ign.fr/1yhlj2ehpqf3q6dt6a2y7b64/telechargement/inspire/CONTOURS-IRIS-FRANCE-2014-01-01$CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01/file/CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01.7z
- download **R_rfl09_LAEA1000** file from https://www.insee.fr/fr/statistiques/fichier/1405815/ECP1KM_09_MET.zip
- download **base-ic-evol-struct-pop** file from https://www.insee.fr/fr/statistiques/fichier/2028582/infra-population-2012.zip
- download **base-ic-diplomes-formation** file from https://www.insee.fr/fr/statistiques/fichier/2028265/infra-formation-2012.zip

You should now have a "data" directory with all the relevant data in it.
Note: We should have these files on IPFS very soon.

### Select the relevant data for your study area
- geographically for **CONTOURS-IRIS** and **R_rfl09_LAEA1000**
- by selection using *DEP*, *COM* or other relevant codes for **base-ic-evol-struct-pop** and **base-ic-diplomes-formation** (keep headers and everything, just filter the data)
- export the latter files as *CSV* and create *csv.lzma* files

This whole process can be automatically done using the following command:
```shell script
sbt "runMain eighties.h24.tools.ExtractRelevantData -c data/CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01/CONTOURS-IRIS/1_DONNEES_LIVRAISON_2014/CONTOURS-IRIS_2-0_SHP_LAMB93_FE-2014/CONTOURS-IRIS_FE.shp -g data/GRID/R_rfl09_LAEA1000.shp -p data/base-ic-evol-struct-pop-2012.xls -f data/base-ic-diplomes-formation-2012.xls -d dep_list -o prepared_data"
```
Where *dep_list* is a list of "départements" you wish to extract from your data and *prepared_data* is the output directory.

For instance, the following command extracts the data for the 44 département (Loire Atlantique):
```shell script
sbt "runMain eighties.h24.tools.ExtractRelevantData -c data/CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01/CONTOURS-IRIS/1_DONNEES_LIVRAISON_2014/CONTOURS-IRIS_2-0_SHP_LAMB93_FE-2014/CONTOURS-IRIS_FE.shp -g data/GRID/R_rfl09_LAEA1000.shp -p data/base-ic-evol-struct-pop-2012.xls -f data/base-ic-diplomes-formation-2012.xls -d 44 -o prepared_data_44"
```

The following command extracts the data for the entire Île-de-France région:
```shell script
sbt "runMain eighties.h24.tools.ExtractRelevantData -c data/CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01/CONTOURS-IRIS/1_DONNEES_LIVRAISON_2014/CONTOURS-IRIS_2-0_SHP_LAMB93_FE-2014/CONTOURS-IRIS_FE.shp -g data/GRID/R_rfl09_LAEA1000.shp -p data/base-ic-evol-struct-pop-2012.xls -f data/base-ic-diplomes-formation-2012.xls -d 75,77,78,91,92,93,94,95 -o prepared_data_IDF"
```

You get the idea, right?

### You are ready to generate you synthetic population!
Let's keep our examples running. The parameters should look familiar.
For Loire-Atlantique:
```shell script
sbt "runMain eighties.h24.tools.PopulationGenerator -c prepared_data_44/CONTOURS-IRIS_FE.shp -g prepared_data_44/R_rfl09_LAEA1000.shp -p prepared_data_44/base-ic-evol-struct-pop-2012.csv.lzma -f prepared_data_44/base-ic-diplomes-formation-2012.csv.lzma -o results_44/population.bin"
```
For Île-de-France (note we added a JVM option to give more memory to the process):
```shell script
sbt -J-Xmx4G "runMain eighties.h24.tools.PopulationGenerator -c prepared_data_IDF/CONTOURS-IRIS_FE.shp -g prepared_data_IDF/R_rfl09_LAEA1000.shp -p prepared_data_IDF/base-ic-evol-struct-pop-2012.csv.lzma -f prepared_data_IDF/base-ic-diplomes-formation-2012.csv.lzma -o results_IDF/population.bin"
```

#!/bin/bash

mkdir data

(
cd data || exit

# Get Contour Iris file from IGN
curl -H "User-Agent: Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:68.0) Gecko/20100101 Firefox/68.0" -H "Upgrade-Insecure-Requests: 1" 'https://data.geopf.fr/telechargement/download/CONTOURS-IRIS/CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01/CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01.7z' --compressed -o 'file.7z' --verbose
7z x file.7z
\rm file.7z
# We recreate the merged CONTOUR FILE to simplify further processing
mkdir -p CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01/CONTOURS-IRIS/1_DONNEES_LIVRAISON_2014/CONTOURS-IRIS_2-0_SHP_LAMB93_FE-2014/
ogrmerge.py -single -o CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01/CONTOURS-IRIS/1_DONNEES_LIVRAISON_2014/CONTOURS-IRIS_2-0_SHP_LAMB93_FE-2014/CONTOURS-IRIS_FE.shp CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01/CONTOURS-IRIS/1_DONNEES_LIVRAISON_2014/CONTOURS-IRIS_2-0_SHP_*/*.shp

# Get 1Km grid file from INSEE
wget https://www.insee.fr/fr/statistiques/fichier/1405815/ECP1KM_09_MET.zip
unzip ECP1KM_09_MET.zip
\rm ECP1KM_09_MET.zip
# Convert mif files from INSEE to shapefile
ogr2ogr GRID R_rfl09_LAEA1000.mif
# Cleanup unused files extrated from zip
\rm R_rfl09_LAEA1000.*
\rm Lisezmoi_donneescarroyees.txt
\rm 'Liste variables mВtropole.pdf'

# Get infra population file from INSEE
wget https://www.insee.fr/fr/statistiques/fichier/2028582/infra-population-2012.zip
unzip infra-population-2012.zip
\rm infra-population-2012.zip

# Get infra formation file from INSEE
wget https://www.insee.fr/fr/statistiques/fichier/2028265/infra-formation-2012.zip
unzip infra-formation-2012.zip
\rm infra-formation-2012.zip
)

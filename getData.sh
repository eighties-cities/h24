#!/bin/bash

mkdir data
cd data

curl -H "User-Agent: Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:68.0) Gecko/20100101 Firefox/68.0" -H "Upgrade-Insecure-Requests: 1" 'https://wxs-telechargement.ign.fr/1yhlj2ehpqf3q6dt6a2y7b64/telechargement/inspire/CONTOURS-IRIS-FRANCE-2014-01-01$CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01/file/CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01.7z' --compressed -o 'file.7z' --verbose
7z x file.7z
\rm file.7z

wget https://www.insee.fr/fr/statistiques/fichier/1405815/ECP1KM_09_MET.zip
unzip ECP1KM_09_MET.zip
\rm ECP1KM_09_MET.zip
ogr2ogr GRID R_rfl09_LAEA1000.mif
\rm R_rfl09_LAEA1000.*
\rm Lisezmoi_donneescarroyees.txt
\rm 'Liste variables mВtropole.pdf'

wget https://www.insee.fr/fr/statistiques/fichier/2028582/infra-population-2012.zip
unzip infra-population-2012.zip
\rm infra-population-2012.zip

wget https://www.insee.fr/fr/statistiques/fichier/2028265/infra-formation-2012.zip
unzip infra-formation-2012.zip
\rm infra-formation-2012.zip

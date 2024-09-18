#!/bin/bash

docker build . --tag h24 --build-arg UID="$(id -u)" --build-arg GID="$(id -g)"

docker run -it -d --name h24 -e UID=$(id -u $USER) -e GID=$(id -g $USER) --mount type=bind,source="$PWD"/data,target=/home/h24/source/prepared_data_IDF --mount type=bind,source="$PWD"/result,target=/home/h24/source/results_IDF --mount type=bind,source="$PWD"/result/jar,target=/home/h24/.ivy2 h24:latest

docker exec h24 sh -c "sbt 'runMain eighties.h24.tools.ExtractRelevantData -c data/CONTOURS-IRIS_2-0__SHP_LAMB93_FXX_2014-01-01/CONTOURS-IRIS/1_DONNEES_LIVRAISON_2014/CONTOURS-IRIS_2-0_SHP_LAMB93_FE-2014/CONTOURS-IRIS_FE.shp -g data/GRID/R_rfl09_LAEA1000.shp -p data/base-ic-evol-struct-pop-2012.xls -f data/base-ic-diplomes-formation-2012.xls -d 75,77,78,91,92,93,94,95 -o prepared_data_IDF'"

docker exec h24 sh -c "sbt -J-Xmx4G 'runMain eighties.h24.tools.PopulationGenerator -c prepared_data_IDF/CONTOURS-IRIS_FE.shp -g prepared_data_IDF/R_rfl09_LAEA1000.shp -s 1000 -p prepared_data_IDF/base-ic-evol-struct-pop-2012.csv.lzma -f prepared_data_IDF/base-ic-diplomes-formation-2012.csv.lzma -o results_IDF/population.bin'"

docker exec h24 sh -c "sbt -J-Xmx4G 'runMain eighties.h24.tools.MoveMatrixGenerator -e OD_IDF/H24_location_noID_ParisRegion.csv.lzma -s EPSG:27572 -p results_IDF/population.bin -m results_IDF/moves.bin'"

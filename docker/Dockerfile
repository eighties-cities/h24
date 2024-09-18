FROM eclipse-temurin:17-jdk-jammy AS build-openmole-sources

MAINTAINER Romain Reuillon <romain.reuillon@iscpif.fr>, Sebastien Rey-Coyrehourcq <sebastien.rey-coyrehourcq@univ-rouen.fr> 

RUN apt-get update && apt-get install --no-install-recommends -y gnupg curl

RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list
RUN curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | gpg --no-default-keyring --keyring gnupg-ring:/etc/apt/trusted.gpg.d/scalasbt-release.gpg --import
RUN chmod 644 /etc/apt/trusted.gpg.d/scalasbt-release.gpg

RUN apt-get update && apt-get install --no-install-recommends -y gnupg libgnutls30 ntp apt-transport-https ca-certificates git git-lfs curl sbt python3 python3-pycurl bash tar gzip ca-certificates-java openssh-client tree unzip p7zip-full gdal-bin gosu lzma
RUN update-ca-certificates

COPY ./docker-entrypoint.sh /usr/local/bin
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

ARG GID
ARG UID

RUN addgroup --gid $GID h24 && useradd -m -d /home/h24 -s /bin/sh -g h24 -u $UID h24 
USER h24


WORKDIR /home/h24

RUN git clone https://github.com/eighties-cities/h24.git source


WORKDIR /home/h24/source

RUN unzip ./InputODData/H24_location_noID_ParisRegion.zip -d OD_IDF/
RUN lzma -f ./OD_IDF/H24_location_noID_ParisRegion.csv

RUN ./getData.sh

ENTRYPOINT ["sbt"]

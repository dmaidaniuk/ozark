#!/usr/bin/env bash

set -ev

GLASSFISH_URL="http://download.oracle.com/glassfish/5.0/nightly/glassfish-5.0-web-b10-06_29_2017.zip"

if [ "${1}" == "glassfish-bundled" ]; then

  curl -s -o glassfish5.zip "${GLASSFISH_URL}"
  unzip -q glassfish5.zip
  mvn -B -V -Pbundled clean install
  find ./test/ -name \*.war -exec cp {} ./glassfish5/glassfish/domains/domain1/autodeploy/ \;
  glassfish5/bin/asadmin start-domain
  sleep 120
  mvn -Pintegration -Dintegration.serverPort=8080 verify
  glassfish5/bin/asadmin stop-domain

elif [ "${1}" == "glassfish-module" ]; then

  curl -s -o glassfish5.zip "${GLASSFISH_URL}"
  unzip -q glassfish5.zip
  mvn -B -V -P\!bundled,module clean install
  cp ozark/target/ozark-core-*.jar ./glassfish5/glassfish/modules/
  cp jersey/target/ozark-jersey-*.jar ./glassfish5/glassfish/modules/
  cp ~/.m2/repository/javax/mvc/javax.mvc-api/1.0-SNAPSHOT/*.jar ./glassfish5/glassfish/modules/
  find ./test/ -name \*.war -exec cp {} ./glassfish5/glassfish/domains/domain1/autodeploy/ \;
  glassfish5/bin/asadmin start-domain
  sleep 120
  mvn -Pintegration -Dintegration.serverPort=8080 verify
  glassfish5/bin/asadmin stop-domain

else
  echo "Unknown test type: $1"
  exit 1;
fi

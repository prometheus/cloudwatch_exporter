#!/bin/bash 
cd /cloudwatch_exporter
mvn package -Dmaven.test.skip=true

#!/bin/bash

java -jar /cloudwatch_exporter.jar ${CW_EXPORTER_PORT:-9106} /${CW_EXPORTER_CONFIG_FILE:-config}.yml
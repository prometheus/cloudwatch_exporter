FROM java
MAINTAINER Prometheus Team <prometheus-developers@googlegroups.com>
EXPOSE 9106

WORKDIR /cloudwatch_exporter
ADD . /cloudwatch_exporter
WORKDIR /

RUN mkdir /config

ADD config.yml /config/
ADD target/cloudwatch_exporter-0.5-SNAPSHOT.jar /cloudwatch_exporter.jar
ENTRYPOINT [ "java", "-jar", "/cloudwatch_exporter.jar", "9106", "/config/config.yml" ]

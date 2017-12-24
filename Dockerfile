FROM java
MAINTAINER Prometheus Team <prometheus-developers@googlegroups.com>
EXPOSE 9106

WORKDIR /cloudwatch_exporter
ADD . /cloudwatch_exporter
WORKDIR /

RUN mkdir /config

ADD config.yml /config/
ENTRYPOINT [ "java", "-jar", "/cloudwatch_exporter.jar", "9106", "/config/config.yml" ]

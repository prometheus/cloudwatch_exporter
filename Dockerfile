FROM wehkamp/jre:8.121.13-r0_02
MAINTAINER Prometheus Team <prometheus-developers@googlegroups.com>
LABEL container.name=wehkamp/prometheus-cloudwatch-exporter:1.3

EXPOSE 9106

WORKDIR /cloudwatch_exporter
ADD . /cloudwatch_exporter
RUN apk update \
    && apk add maven openjdk8 \
    && mvn package \
    && mv target/cloudwatch_exporter-*-with-dependencies.jar /cloudwatch_exporter.jar \
    && apk del maven openjdk8 \
    && rm -rf /cloudwatch_exporter


WORKDIR /
COPY config.yml /
ENTRYPOINT [ "java", "-jar", "/cloudwatch_exporter.jar", "9106", "/config.yml" ]
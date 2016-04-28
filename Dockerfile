FROM wehkamp/jre:1.8
MAINTAINER Prometheus Team <prometheus-developers@googlegroups.com>
LABEL container.name=wehkamp/prometheus-cloudwatch-exporter:1.0

EXPOSE 9106

WORKDIR /cloudwatch_exporter
ADD . /cloudwatch_exporter
RUN echo 'http://dl-3.alpinelinux.org/alpine/edge/testing' >> /etc/apk/repositories \
    && echo 'http://dl-3.alpinelinux.org/alpine/edge/community' >> /etc/apk/repositories \
    && apk update \
    && apk add maven openjdk8-jre \
    && mvn package \
    && mv target/cloudwatch_exporter-*-with-dependencies.jar /cloudwatch_exporter.jar \
    && apk del maven openjdk8-jre \
    && rm -rf /cloudwatch_exporter

WORKDIR /
ONBUILD COPY config.yml /
ENTRYPOINT [ "java", "-jar", "/cloudwatch_exporter.jar", "9106", "/config.yml" ]

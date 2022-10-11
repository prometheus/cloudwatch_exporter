FROM 234348545939.dkr.ecr.eu-west-1.amazonaws.com/wehkamp/jre:8.121.13-r0_02

ENTRYPOINT ["/exporter.sh"]
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
COPY config*.yml /
COPY exporter.sh /
RUN chmod 755 /exporter.sh
LABEL container.name=wehkamp/prometheus-cloudwatch-exporter:1.4.7-13

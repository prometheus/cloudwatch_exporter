FROM openjdk:11 as builder

WORKDIR /cloudwatch_exporter
ADD . /cloudwatch_exporter
RUN apt-get update -qq && apt-get install -qq maven && mvn -DskipTests package && \
    mv target/cloudwatch_exporter-*-with-dependencies.jar /cloudwatch_exporter.jar

FROM openjdk:11-jre-slim as runner
MAINTAINER Prometheus Team <prometheus-developers@googlegroups.com>
EXPOSE 9106

WORKDIR /
RUN mkdir /config
ONBUILD ADD config.yml /config/
COPY --from=builder /cloudwatch_exporter.jar /cloudwatch_exporter.jar

RUN apt-get update && apt-get install -y wget && apt-get clean && rm -rf /var/lib/{apt,dpkg,cache,log}/

ENTRYPOINT [ "java", "-jar", "/cloudwatch_exporter.jar", "9106"]
CMD ["/config/config.yml"]

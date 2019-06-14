FROM openjdk:13-alpine3.9 as builder

WORKDIR /cloudwatch_exporter
ADD . /cloudwatch_exporter
RUN apk add maven &&\
    mvn -q package &&\
    mvn -q dependency:purge-local-repository && \
    mv target/cloudwatch_exporter-*-with-dependencies.jar /cloudwatch_exporter.jar &&\
    apk del maven

FROM openjdk:13-alpine3.9 as runner
MAINTAINER Prometheus Team <prometheus-developers@googlegroups.com>
EXPOSE 9106

WORKDIR /
RUN mkdir /config
ONBUILD ADD config.yml /config/
COPY --from=builder /cloudwatch_exporter.jar /cloudwatch_exporter.jar
ENTRYPOINT [ "java", "-jar", "/cloudwatch_exporter.jar", "9106"]
CMD ["/config/config.yml"]

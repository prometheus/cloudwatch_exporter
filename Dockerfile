FROM eclipse-temurin:21-jdk-noble as builder

SHELL ["/bin/bash", "-xe", "-o", "pipefail", "-c"]

WORKDIR /cloudwatch_exporter
COPY . /cloudwatch_exporter

RUN ./mvnw package \
 && mv target/cloudwatch_exporter-*-with-dependencies.jar /cloudwatch_exporter.jar

FROM eclipse-temurin:21-jre-noble as runner
LABEL maintainer="The Prometheus Authors <prometheus-developers@googlegroups.com>"
EXPOSE 9106

WORKDIR /
RUN mkdir /config
COPY --from=builder /cloudwatch_exporter.jar /cloudwatch_exporter.jar
ENTRYPOINT [ "java", "-jar", "/cloudwatch_exporter.jar", "9106"]
CMD ["/config/config.yml"]

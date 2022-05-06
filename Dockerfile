FROM eclipse-temurin:17-jdk-focal as builder

SHELL ["/bin/bash", "-xe", "-o", "pipefail", "-c"]

ARG MAVEN_VERSION=3.8.5
ARG MAVEN_SHA512=89ab8ece99292476447ef6a6800d9842bbb60787b9b8a45c103aa61d2f205a971d8c3ddfb8b03e514455b4173602bd015e82958c0b3ddc1728a57126f773c743

ADD https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz /opt/maven.tar.gz
RUN mkdir -p /opt/maven \
 && echo "${MAVEN_SHA512}  /opt/maven.tar.gz" | sha512sum -c \
 && tar -x --strip-components=1 -C /opt/maven -f /opt/maven.tar.gz
ENV PATH /opt/maven/bin:${PATH}

WORKDIR /cloudwatch_exporter
COPY . /cloudwatch_exporter

# As of Java 13, the default is POSIX_SPAWN, which doesn't seem to work on
# ARM64: https://github.com/openzipkin/docker-java/issues/34#issuecomment-721673618
ENV MAVEN_OPTS "-Djdk.lang.Process.launchMechanism=vfork"

RUN mvn package \
 && mv target/cloudwatch_exporter-*-with-dependencies.jar /cloudwatch_exporter.jar

FROM eclipse-temurin:17-jre-focal as runner
LABEL maintainer="The Prometheus Authors <prometheus-developers@googlegroups.com>"
EXPOSE 9106

WORKDIR /
RUN mkdir /config
COPY --from=builder /cloudwatch_exporter.jar /cloudwatch_exporter.jar
ENTRYPOINT [ "java", "-jar", "/cloudwatch_exporter.jar", "9106"]
CMD ["/config/config.yml"]

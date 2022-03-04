FROM eclipse-temurin:17-jdk-focal as builder

SHELL ["/bin/bash", "-xe", "-o", "pipefail", "-c"]

ENV MAVEN_VERSION 3.8.4
ENV MAVEN_SHA512 a9b2d825eacf2e771ed5d6b0e01398589ac1bfa4171f36154d1b5787879605507802f699da6f7cfc80732a5282fd31b28e4cd6052338cbef0fa1358b48a5e3c8

RUN mkdir -p /opt/maven
RUN curl -o /opt/maven.tar.gz -sSfL https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz
RUN echo "${MAVEN_SHA512}  /opt/maven.tar.gz" | sha512sum -c
RUN tar -x --strip-components=1 -C /opt/maven -f /opt/maven.tar.gz
ENV PATH /opt/maven/bin:${PATH}

WORKDIR /cloudwatch_exporter
ADD . /cloudwatch_exporter

# As of Java 13, the default is POSIX_SPAWN, which doesn't seem to work on
# ARM64: https://github.com/openzipkin/docker-java/issues/34#issuecomment-721673618
ENV MAVEN_OPTS "-Djdk.lang.Process.launchMechanism=vfork"

RUN mvn package
RUN mv target/cloudwatch_exporter-*-with-dependencies.jar /cloudwatch_exporter.jar

FROM eclipse-temurin:17-jre-focal as runner
LABEL maintainer="The Prometheus Authors <prometheus-developers@googlegroups.com>"
EXPOSE 9106

WORKDIR /
RUN mkdir /config
COPY --from=builder /cloudwatch_exporter.jar /cloudwatch_exporter.jar
ENTRYPOINT [ "java", "-jar", "/cloudwatch_exporter.jar", "9106"]
CMD ["/config/config.yml"]

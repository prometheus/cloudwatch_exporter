FROM openjdk:17-jdk-bullseye as builder

RUN mkdir -p /opt/maven
RUN curl -o /opt/maven.tar.gz -sSfL https://dlcdn.apache.org/maven/maven-3/3.8.3/binaries/apache-maven-3.8.3-bin.tar.gz
RUN echo '1c12a5df43421795054874fd54bb8b37d242949133b5bf6052a063a13a93f13a20e6e9dae2b3d85b9c7034ec977bbc2b6e7f66832182b9c863711d78bfe60faa  /opt/maven.tar.gz' | shasum -a 512 -c
RUN tar -x --strip-components=1 -C /opt/maven -f /opt/maven.tar.gz
ENV PATH /opt/maven/bin:${PATH}

WORKDIR /cloudwatch_exporter
ADD . /cloudwatch_exporter

# As of Java 13, the default is POSIX_SPAWN, which doesn't seem to work on
# ARM64: https://github.com/openzipkin/docker-java/issues/34#issuecomment-721673618
ENV MAVEN_OPTS "-Djdk.lang.Process.launchMechanism=vfork"

RUN mvn package
RUN mv target/cloudwatch_exporter-*-with-dependencies.jar /cloudwatch_exporter.jar

FROM openjdk:17-slim-bullseye as runner
LABEL maintainer="The Prometheus Authors <prometheus-developers@googlegroups.com>"
EXPOSE 9106

WORKDIR /
RUN mkdir /config
COPY --from=builder /cloudwatch_exporter.jar /cloudwatch_exporter.jar
ENTRYPOINT [ "java", "-jar", "/cloudwatch_exporter.jar", "9106"]
CMD ["/config/config.yml"]

FROM eclipse-temurin:25-jdk-noble as builder

SHELL ["/bin/bash", "-xe", "-o", "pipefail", "-c"]

ARG MAVEN_VERSION=3.9.16
ARG MAVEN_SHA512=831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6

ADD https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz /opt/maven.tar.gz
RUN mkdir -p /opt/maven \
 && echo "${MAVEN_SHA512}  /opt/maven.tar.gz" | sha512sum -c \
 && tar -x --strip-components=1 -C /opt/maven -f /opt/maven.tar.gz
ENV PATH /opt/maven/bin:${PATH}

WORKDIR /cloudwatch_exporter
COPY . /cloudwatch_exporter

RUN mvn package \
 && mv target/cloudwatch_exporter-*-with-dependencies.jar /cloudwatch_exporter.jar

FROM eclipse-temurin:25-jre-noble as runner
LABEL maintainer="The Prometheus Authors <prometheus-developers@googlegroups.com>"
EXPOSE 9106

WORKDIR /
RUN mkdir /config
COPY --from=builder /cloudwatch_exporter.jar /cloudwatch_exporter.jar
ENTRYPOINT [ "java", "-jar", "/cloudwatch_exporter.jar", "9106"]
CMD ["/config/config.yml"]

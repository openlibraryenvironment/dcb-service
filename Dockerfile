# syntax=docker/dockerfile:1
# dcb-service: Java 25 (dcb/build.gradle sets source/target 25), Micronaut 5, shadow jar.
#
# The repo's own release path is the micronaut-application plugin's `dockerBuild`
# task, which needs a working Gradle and JDK 25 on the host. This Dockerfile exists
# so the workspace-root compose file can build dcb-service from source like every
# other service, with no host toolchain at all.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# buildSrc carries the org.olf.dcb-bom and service.k_int.micronaut-multi convention
# plugins that dcb/build.gradle applies by id, so it is a build input, not an extra.
COPY gradle gradle
COPY gradlew settings.gradle build.gradle gradle.properties micronaut-cli.yml ./
COPY buildSrc buildSrc
COPY dcb dcb

# The wrapper may carry CRLF from a Windows checkout; /bin/sh cannot run that.
#
# Tests are deliberately NOT run here. They need Postgres and Elasticsearch, and an
# image build is the wrong place to discover that — that is what .gitlab-ci.yml is
# for. This stage proves the code compiles and packages, nothing more.
RUN --mount=type=cache,target=/root/.gradle \
    sed -i 's/\r$//' gradlew && chmod +x gradlew && \
    ./gradlew --no-daemon --console=plain :dcb:shadowJar

# Not -alpine: the compose healthcheck probes /health through bash's /dev/tcp
# (see docker-compose.yml), and alpine ships no bash.
FROM eclipse-temurin:25-jre
WORKDIR /app

# The conventional-git plugin derives the version from git tags, and .dockerignore
# excludes .git, so the jar's version segment is not something this file can pin.
# Glob it.
COPY --from=build /src/dcb/build/libs/*-all.jar /app/dcb-service.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/dcb-service.jar"]

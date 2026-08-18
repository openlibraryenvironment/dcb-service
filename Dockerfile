# syntax=docker/dockerfile:1
#
# dcb-service: Java 25 (dcb/build.gradle sets source/target 25), Micronaut + shadow jar.
#
# RECONSTRUCTED. This file was missing from the repository and has never been tracked in
# git, while docker-compose.yml has always declared `build: context: ./dcb-service`. The
# consequence was silent and expensive: `docker compose build dcb-service` failed with
# "failed to read dockerfile: open Dockerfile: no such file or directory", `docker compose
# up` carried on with the LAST IMAGE THAT HAPPENED TO EXIST, and that image dated from
# 2026-08-05. Anything merged after it simply was not in the running service — most
# visibly DiscoveryPatronRequestsController and PatronAssertionVerifier, so every
# /discovery/requests call 401'd against a route that did not exist, and My Requests
# looked like an authentication fault in Symposia.
#
# The stages below are recovered from `docker history` on that image, so the result is
# what was running rather than a fresh guess: temurin 25, a shadow jar copied from
# /src/dcb/build/libs/*-all.jar to /app/dcb-service.jar, port 8080.
#
# NOT ADDED, deliberately: the non-root USER that symposia-service's Dockerfile carries.
# That service's image was built for it; this one writes to /app/logs at runtime, so
# adding a user here without also fixing ownership swaps a stale-image bug for a
# permissions one. It is worth doing as its own change, with its own test.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# Build inputs only. .dockerignore already drops build/, */build, attic, docs, scripts,
# myScripts and polarisCleanup, so a stale local build directory cannot leak into the
# image and be mistaken for a fresh one — which is the same class of fault as the missing
# Dockerfile itself.
COPY gradle gradle
COPY gradlew settings.gradle build.gradle gradle.properties micronaut-cli.yml ./
COPY buildSrc buildSrc
COPY dcb dcb

# The wrapper may carry CRLF from a Windows checkout; /bin/sh cannot run that.
# `:dcb:shadowJar` and not `build`: the tests need Testcontainers, which is not available
# inside a build container, and ADR-0001 owns how the suite is run in any case.
RUN --mount=type=cache,target=/root/.gradle \
    sed -i 's/\r$//' gradlew && chmod +x gradlew && \
    ./gradlew --no-daemon --console=plain :dcb:shadowJar

FROM eclipse-temurin:25-jre
WORKDIR /app

# No package installs on purpose — the compose healthcheck probes /health over bash's
# /dev/tcp rather than pulling curl in, for the reason symposia-service's Dockerfile
# gives at length: an apt round-trip on every build fails the whole stack whenever a
# mirror is mid-sync.
COPY --from=build /src/dcb/build/libs/*-all.jar /app/dcb-service.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/dcb-service.jar"]

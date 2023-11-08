[![Release](https://github.com/openlibraryenvironment/dcb-service/actions/workflows/release.yml/badge.svg?branch=main)](https://github.com/openlibraryenvironment/dcb-service/actions/workflows/release.yml)

# DCB Service

A Direct Consortial Borrowing Service

# Source code and Docker container

This module is distributed in source and pre-compiled docker container form:

* https://github.com/openlibraryenvironment/dcb-service
* https://nexus.libsdev.k-int.com/#browse/browse:libsdev-docker:v2%2Fknowledgeintegration%2Fdcb

# Deployment

The module is deployed as a docker container which supports the following runtime ENV settings

Note: Today flyway does not support r2dbc datasources, so we need to configure both JDBC and R2DBC datasources - same
DB connection effectively, 2 different connections - with JDBC only being used for database migrations.

| ENV                                      | Description                                                                        | Example                                                                                   |
|------------------------------------------|------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| DCB_ITEMRESOLVER_CODE                    | Identify the default resolution strategy for selecting items. Currently Geo. Geo only works when Agencies and Locations are configured with latitude and longitude fields. Items with missing geo data will ranked last when this strategt is selected | FirstItem    | Geo                                       |
| R2DBC_DATASOURCES_DEFAULT_URL            | R2DBC Connect URL                                                                  | r2dbc:postgresql://localhost:5432/dcb                                                     |
| R2DBC_DATASOURCES_DEFAULT_USERNAME       | R2DBC Username                                                                     | dcb                                                                                       |
| R2DBC_DATASOURCES_DEFAULT_PASSWORD       | R2DBC Password                                                                     | dcb                                                                                       |
| DATASOURCES_DEFAULT_URL                  | JDBC Connect URL                                                                   | jdbc:postgresql://localhost:5432/dcb                                                      |
| DATASOURCES_DEFAULT_USERNAME             | JDBC Username                                                                      | dcb                                                                                       |
| DATASOURCES_DEFAULT_PASSWORD             | JDBC Password                                                                      | dcb                                                                                       |
| KEYCLOAK_CERT_URL                        | The URL used for validating JWTs                                                   | https://reshare-hub-kc.libsdev.k-int.com/realms/reshare-hub/protocol/openid-connect/certs |
| MICRONAUT_HTTP_CLIENT_READ_TIMEOUT       | Default HTTP Client Timeout                                                        | PT1M                                                                                      |
| MICRONAUT_HTTP_CLIENT_MAX_CONTENT_LENGTH | Max content length                                                                 | 20971520                                                                                  |
| DCB_SHEDULED_TASKS_ENABLED               | perform scheduled tasks                                                            | true                                                                                      |
| REACTOR_DEBUG                            | DEVELOPMENT FLAG! set to the string "true" to enable reactor annotated stack trace | true                                                                                      |
| POD_NAME                            | In K8S environments, set to metadata.name (And decide if you want deployments or statefulsets). Elsewhere set this to the name you want to appear by this instance of DCB for stats logging. Be aware that multiple instances may be running for load balancing | INGEST-DCB-0001

# Configuration

Additional optional configuration values. These may be set in configuration files or using environment variables.

| Name                                        | Description                                                                       | Format                                                                               | Default |
|---------------------------------------------|-----------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|---------|
| dcb.demo.ingest.limit                       | Maximum number of records to ingest when running in demo environment              | Integer value                                                                        | 1000    |
| dcb.requestability.location.codes.allowed   | Location codes allow list for item requestability                                 | List                                                                                 | None    |
| dcb.requestability.location.filtering       | Whether items should be filtered by location code when determining requestability | Boolean                                                                              | false   |
| dcb.request-workflow.state-transition-delay | Delay between transitions in the request workflow                                 | [ISO-8601 format](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html) | PT0.0S  |
| dcb.requests.supplying.patron-type          | Fixed patron type for supplying agency virtual patrons                            | Integer                                                                              | 210     |
| dcb.requests.preflight-checks.enabled       | Whether preflight checks for placing patron requests are enabled                  | Boolean                                                                              | true    |


# API Documentation

Module documentation is auto generated and is accessed from the following URL once the container has
started: https://openlibraryenvironment.github.io/dcb-service/openapi/

# General Documentation

Module documentation can be found here: https://openlibraryenvironment.github.io/dcb-service/


# Release Procedures

## Tag a prerelease

./gradew cgTagPre


## Trace graalvm issues with

export JDK_JAVAC_OPTIONS="--trace-class-initialization=org.codehaus.stax2.typed.Base64Variants"


## Get a postgres session

by finding the postgres port with 

    docker ps

Then running psql

    psql -U test -h localhost -p PORT_FROM_DOCKER_PS

password will be test


[![Release](https://github.com/openlibraryenvironment/dcb-service/actions/workflows/release.yml/badge.svg?branch=main)](https://github.com/openlibraryenvironment/dcb-service/actions/workflows/release.yml)

# DCB Service

A Direct Consortial Borrowing Service

# Source code and Docker container

This module is distributed in source and pre-compiled docker container form:

* https://github.com/openlibraryenvironment/dcb-service
* https://nexus.libsdev.k-int.com/#browse/browse:libsdev-docker:v2%2Fknowledgeintegration%2Fdcb

# Deployment

## Secret Manager Integration

By creating a bootstrap.yml file and mounting it at /bootstrap.yml, then referencing this file in an
environment variable: MICRONAUT_CONFIG_FILES=/bootstrap.yml devops teams can point DCB at a local secret
manager. Info for different environments can be found here: https://guides.micronaut.io/latest/tag-distributed_configuration.html
and the bootstrap.yml under dcb/src/main/resources contains commented out sections for AWS Secrets Manager and 
Hashicorp Vault.


## Environment overrides 

It is important to note that Micronaut declarative configuration reads environment variables last, so if switching to secrets
manager, you should remember to remove any environment variables as they will override distributed config.


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
| MICRONAUT_METRICS_EXPORT_CLOUDWATCH_ENABLED | In AWS environments set to true to enable cloudwatch metrics export | true |
| MICRONAUT_METRICS_EXPORT_CLOUDWATCH_NAMESPCE | Cloudwatch namespace | dcb |
| DCB_LOG_APPENDERS | Optionally change the log appenders -default is both :- "CLOUDWATCH JSON_SYNC" set this to disable one or the other | JSON_SYNC |
| DCB_INDEX_NAME | ES or OS index name ||
| DCB_INDEX_USERNAME | ES or OS username ||
| DCB_INDEX_PASSWORD | ES or OS password ||
| ELASTICSEARCH_HTTP_HOSTS OR OPENSEARCH_HTTP_HOSTS | The url of the ES or OS instance | |

# Configuration

Additional optional configuration values. These may be set in configuration files or using environment variables.

| Name                                                                    | Description                                                            | Format                                                                               | Default    |
|-------------------------------------------------------------------------|------------------------------------------------------------------------|--------------------------------------------------------------------------------------|------------|
| dcb.demo.ingest.limit                                                   | Maximum number of records to ingest when running in demo environment   | Integer value                                                                        | 1000       |
| dcb.request-workflow.state-transition-delay                             | Delay between transitions in the request workflow                      | [ISO-8601 format](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html) | PT0.0S     |
| dcb.requests.supplying.patron-type                                      | Fixed patron type for supplying agency virtual patrons                 | Integer                                                                              | 210        |
| dcb.requests.preflight-checks.pickup-location.enabled                   | Whether pickup location preflight checks are enabled                   | Boolean                                                                              | true       |
| dcb.requests.preflight-checks.pickup-location-to-agency-mapping.enabled | Whether pickup location to agency mapping preflight checks are enabled | Boolean                                                                              | true       |
| dcb.requests.preflight-checks.resolve-patron.enabled                    | Whether patron resolution preflight checks are enabled                 | Boolean                                                                              | true       |
| dcb.requests.preflight-checks.duplicate-requests.enabled                | Whether patron duplicate requests preflight checks are enabled         | Boolean                                                                              | true       |
| dcb.requests.preflight-checks.duplicate-requests.request-window         | Request window that duplicate requests are disallowed (In seconds)     | Integer value                                                                        | 900        |
| dcb.resolution.live-availability.timeout                                | Maximum wait for responses for live availability during resolution     | [ISO-8601 format](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html) | 30 Seconds |

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




Useful greps: 
  "Unable to map canonical item type"



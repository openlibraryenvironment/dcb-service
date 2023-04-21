[![Release](https://github.com/openlibraryenvironment/reshare-dcb-service/actions/workflows/release.yml/badge.svg?branch=main)](https://github.com/openlibraryenvironment/reshare-dcb-service/actions/workflows/release.yml)

# reshare-dcb-service

A Direct Consortial Borrowing Service for ReShare Libraries

# Source code and Docker container

This module is distributed in source and precompiled docker container form:

* https://github.com/openlibraryenvironment/reshare-dcb-service
* https://nexus.libsdev.k-int.com/#browse/browse:libsdev-docker:v2%2Fknowledgeintegration%2Freshare-dcb

# Deployment

The module is deployed as a docker container which supports the following runtime ENV settings

Note: Today flyway does not support r2dbc datasources, so we need to configure both JDBC and R2DBC datasources - same
DB connection effectively, 2 different connections - with JDBC only being used for database migrations.

| ENV                                | Description       | Example                               |
|------------------------------------|-------------------|---------------------------------------|
| R2DBC_DATASOURCES_DEFAULT_URL      | R2DBC Connect URL | r2dbc:postgresql://localhost:5432/dcb |
| R2DBC_DATASOURCES_DEFAULT_USERNAME | R2DBC Username    | dcb                                   |
| R2DBC_DATASOURCES_DEFAULT_PASSWORD | R2DBC Password    | dcb                                   
| DATASOURCES_DEFAULT_URL            | JDBC Connect URL  | jdbc:postgresql://localhost:5432/dcb  |
| DATASOURCES_DEFAULT_USERNAME       | JDBC Username     | dcb                                   |
| DATASOURCES_DEFAULT_PASSWORD       | JDBC Password     | dcb                                   |
| KEYCLOAK_CERT_URL                  | The URL used for validating JWTs     | https://reshare-hub-kc.libsdev.k-int.com/realms/reshare-hub/protocol/openid-connect/certs |
| MICRONAUT_HTTP_CLIENT_READ_TIMEOUT | Default HTTP Client Timeout  | PT1M |
| MICRONAUT_HTTP_CLIENT_MAX_CONTENT_LENGTH | Max content length  | 20971520 |
| DCB_SHEDULED_TASKS_ENABLED | perform scheduled tasks | true |
| REACTOR_DEBUG | DEVELOPMENT FLAG! set to the string "true" to enable reactor annotated stack trace  | true |

# API Documentation

Module documentation is auto generated and is accessed from the following URL once the container has
started: https://openlibraryenvironment.github.io/reshare-dcb-service/openapi/

# General Documentation

Module documentation can be found here: https://openlibraryenvironment.github.io/reshare-dcb-service/


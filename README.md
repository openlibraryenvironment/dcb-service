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

| ENV | Description | Example |
|---|---|---|
|r2dbc_datasources_default_url|R2DBC Connect URL|r2dbc:postgresql://localhost:5432/dcb|
|r2dbc_datasources_default_username|R2DBC Username|dcb|
|r2dbc_datasources_default_password|R2DBC Password|dcb
|datasources_default_url|JDBC Connect URL|jdbc:postgresql://localhost:5432/dcb|
|datasources_default_username|JDBC Username|dcb|
|datasources_default_password|JDBC Password|dcb|

# API Documentation
API Docs can be found here: https://openlibraryenvironment.github.io/reshare-dcb-service/openapi/

# General Documentation
Module documentation can be found here: https://openlibraryenvironment.github.io/reshare-dcb-service/


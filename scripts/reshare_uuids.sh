#!/bin/bash

# ReShare DCB makes extensive use of UUID5 to generate stable UUIDs for well known items
# We construct our UUIDS by making a root namespace from the domain name projectreshare.org
# We then construct sub-namespaces for each domain. The following commands show how this happens
# RESHARE_ROOT_UUID=`uuidgen --sha1 -n @dns --name projectreshare.org`
# AGENCIES_NS_UUID=`uuidgen --sha1 -n $RESHARE_ROOT_UUID --name Agency`
# HOSTLMS_NS_UUID=`uuidgen --sha1 -n $RESHARE_ROOT_UUID --name HostLms`
# LOCATION_NS_UUID=`uuidgen --sha1 -n $RESHARE_ROOT_UUID --name Location`

# But the UUIDs those commands output are held here for ease of reference
export RESHARE_ROOT_UUID=fa18464f-bdfd-53d9-9a88-6cf81fa9636b
export AGENCIES_NS_UUID=9d0855a0-2f50-5a83-a316-c364d459a8be
export HOSTLMS_NS_UUID=172c587d-4011-58b6-a3e0-58feafc64414
export LOCATION_NS_UUID=ca9ea401-2f43-532b-af95-a4fdbd5d46a5

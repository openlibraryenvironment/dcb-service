#!/bin/bash

TARGET=${1:-https://dcb-uat.sph.k-int.com}

curl -v "$TARGET/info" | jq

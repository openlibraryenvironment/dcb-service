#!/bin/bash

# Define configurable variables
TARGET_URL="http://localhost:8080"
RULESET_NAME="itemTEST"
RULESET_TYPE="DISJUNCTIVE"
PROPERTY_NAME="Test"
PROPERTY_VALUE=7
CONDITION_NEGATED=true

# Token retrieval
TOKEN=$(./login)

# Function to send a request with common headers
function send_request() {
  local method="$1"
  local url="$2"
  local data="${3:-}"  # Optional data for POST requests

  curl -s -X "$method" "$url" \
    -H "Accept-Language: en" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    ${data:+--data "$data"}  # Add data only if provided
}

# Function to check for rulesets
function check_rulesets() {
  local size="$1"  # Size parameter for the API call (optional)

  send_request GET "$TARGET_URL/object-rules?number=0&size=${size:-10}" | json_pp
}

# Construct the ruleset data
ruleset_data="{
  \"name\": \"$RULESET_NAME\",
  \"type\": \"$RULESET_TYPE\",
  \"conditions\": [
    {
      \"operation\": \"propertyValueAnyOf\",
      \"property\": \"$PROPERTY_NAME\",
      \"values\": [$PROPERTY_VALUE],
      \"negated\": $CONDITION_NEGATED
    }
  ]
}"

# Script execution

echo
echo "Checking for no rulesets:"
check_rulesets
sleep 5

echo
echo "Creating new object ruleset:"
send_request POST "$TARGET_URL/object-rules" "$ruleset_data" | json_pp
sleep 5

echo
echo "Checking for new ruleset:"
check_rulesets

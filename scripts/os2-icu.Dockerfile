# OpenSearch 2.x with the ICU analysis plugin.
#
# Exists to test the production index backend locally. Production runs OpenSearch
# (AWS Fargate); Micronaut 5 bumped opensearch-java 2.21.0 -> 3.8.0, so a 3.x
# client talking to a 2.x server is the production-critical compatibility case and
# is not covered by any test or CI job.
#
# analysis-icu is required for the same reason as the Elasticsearch image:
# sharedIndex/settings-2.json defines an icu_folding_nopunc analyzer.
ARG OS_VERSION=2.19.1
FROM opensearchproject/opensearch:${OS_VERSION}

RUN /usr/share/opensearch/bin/opensearch-plugin install --batch analysis-icu

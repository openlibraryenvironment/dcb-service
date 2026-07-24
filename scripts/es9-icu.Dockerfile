# Elasticsearch 9 with the ICU analysis plugin.
#
# Why this exists: the shared index settings (sharedIndex/settings-2.json) define an
# `icu_folding_nopunc` analyzer using icu_tokenizer/icu_folding, so a stock
# elasticsearch image cannot create the index. The existing folio-es:8.7.0 image
# bundles analysis-icu for the same reason -- this is its 9.x equivalent.
#
# Micronaut 5 upgraded the Elasticsearch Java client to 9.x, and a 9.x client is
# rejected by an 8.x server ("Accept version must be either version 8 or 7, but
# found 9"), so local development needs a 9.x server to match the client.
ARG ES_VERSION=9.4.0
FROM docker.elastic.co/elasticsearch/elasticsearch:${ES_VERSION}

RUN elasticsearch-plugin install --batch analysis-icu

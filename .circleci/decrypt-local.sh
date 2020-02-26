#!/usr/bin/env bash

openssl aes-256-cbc -d -md sha256 \
    -in  keystore.properties.cipher \
    -out keystore.properties \
    -pass env:CIRCLE_OPEN_SSL_PASSWORD

openssl aes-256-cbc -d -md sha256 \
    -in  play-api.json.cipher \
    -out play-api.json \
    -pass env:CIRCLE_OPEN_SSL_PASSWORD

openssl aes-256-cbc -d -md sha256 \
    -in  keystore_synapse.jks.cipher \
    -out keystore_synapse.jks \
    -pass env:CIRCLE_OPEN_SSL_PASSWORD
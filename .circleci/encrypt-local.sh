#!/usr/bin/env bash

openssl aes-256-cbc -e -md sha256 \
    -in  keystore.properties \
    -out keystore.properties.cipher \
    -pass env:CIRCLE_OPEN_SSL_PASSWORD

openssl aes-256-cbc -e -md sha256 \
    -in  play-api.json \
    -out play-api.json.cipher \
    -pass env:CIRCLE_OPEN_SSL_PASSWORD

openssl aes-256-cbc -e -md sha256 \
    -in  keystore_synapse.jks \
    -out keystore_synapse.jks.cipher \
    -pass env:CIRCLE_OPEN_SSL_PASSWORD
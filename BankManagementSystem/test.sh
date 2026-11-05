#!/usr/bin/env bash
# Build and run all tests
set -e
./build.sh
java -cp out TestRunner

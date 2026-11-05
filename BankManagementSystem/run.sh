#!/usr/bin/env bash
# Build and start the console app. Add --demo on the first run for sample users and accounts.
set -e
./build.sh
java -cp out Main "$@"

#!/usr/bin/env bash
# Compile everything into ./out  (needs JDK 17 or later)
set -e
rm -rf out && mkdir out
javac -d out $(find src test -name '*.java')
echo "Build OK"

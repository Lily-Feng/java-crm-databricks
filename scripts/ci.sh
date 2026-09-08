#!/usr/bin/env bash
# §11.2: ./mvnw -B verify + local integration + image/Compose smoke.
# The integration/Docker-smoke stages are added from Milestone 5 onward; today this
# script only runs Java verification (Milestone 1 scope).
set -euo pipefail

cd "$(dirname "$0")/.."

if command -v java >/dev/null 2>&1 && java -version >/dev/null 2>&1; then
  ./mvnw -B verify
else
  echo "No local JDK found; building inside maven:3.9-eclipse-temurin-25 instead." >&2
  docker run --rm \
    -v "$(pwd)":/work -w /work \
    -v maven-repo-cache:/root/.m2 \
    maven:3.9-eclipse-temurin-25 \
    mvn -B verify
fi

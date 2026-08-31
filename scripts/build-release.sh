#!/usr/bin/env bash
# Build a signed Play Store bundle. Requires OpenJDK 17 (Homebrew: brew install openjdk@17).
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ ! -f keystore.properties ]]; then
  echo "Missing keystore.properties — copy keystore.properties.example and fill in passwords."
  exit 1
fi

./gradlew bundleRelease
echo ""
echo "AAB ready:"
echo "  app/build/outputs/bundle/release/app-release.aab"

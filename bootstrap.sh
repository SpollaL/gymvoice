#!/bin/bash
set -e

GRADLE_VERSION="8.6"
GRADLE_ZIP="/tmp/gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_DIR="/tmp/gradle-${GRADLE_VERSION}"

echo "Downloading Gradle ${GRADLE_VERSION}..."
curl -L -o "$GRADLE_ZIP" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
unzip -q "$GRADLE_ZIP" -d /tmp/

echo "Generating wrapper..."
"${GRADLE_DIR}/bin/gradle" wrapper --gradle-version="$GRADLE_VERSION" --distribution-type=bin

chmod +x gradlew
rm -rf "$GRADLE_DIR" "$GRADLE_ZIP"

echo "Done. Run: ./gradlew assembleDebug"

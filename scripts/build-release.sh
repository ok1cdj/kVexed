#!/usr/bin/env bash
# Reproducible local release build. Runs the engine test suite first, then
# produces a signed, minified APK named kvexed-<versionName>.apk in the project
# root.
#
# Requires JDK 17+ and a signing keystore configured in local.properties
# (signing.storeFile / storePassword / keyAlias / keyPassword). See
# local.properties.example. The Android Studio JBR is used automatically if
# JAVA_HOME is not already set.
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -z "${JAVA_HOME:-}" ] && [ -x /opt/android-studio/jbr/bin/java ]; then
    export JAVA_HOME=/opt/android-studio/jbr
fi

# The engine oracle gates the release: a regression must fail the build.
./gradlew :core:test --no-daemon

./gradlew clean assembleRelease --no-daemon

VER=$(grep -oE 'versionName[[:space:]]+"[^"]+"' app/build.gradle | grep -oE '"[^"]+"' | tr -d '"')
SRC=app/build/outputs/apk/release/app-release.apk
OUT="kvexed-${VER}.apk"
cp "$SRC" "$OUT"

echo "Built $OUT ($(du -h "$OUT" | cut -f1))"

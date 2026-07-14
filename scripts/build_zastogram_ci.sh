#!/usr/bin/env bash
set -euo pipefail

ZASTOGRAM_APP_ID="${ZASTOGRAM_APP_ID:-20847974}"
ZASTOGRAM_APP_HASH="${ZASTOGRAM_APP_HASH:-41e494501cda12f9331a97131bd73eeb}"

UPSTREAM_REPO="https://github.com/Telegram-FOSS-Team/Telegram-FOSS.git"
UPSTREAM_DIR="Telegram-FOSS-src"
APP_ID_PACKAGE="org.zastogram.messenger"
APP_LABEL="Zastogram"

rm -rf "$UPSTREAM_DIR"
git clone --recursive --depth 1 "$UPSTREAM_REPO" "$UPSTREAM_DIR"
cd "$UPSTREAM_DIR"

cat > API_KEYS <<API_KEYS
APP_ID = ${ZASTOGRAM_APP_ID}
APP_HASH = ${ZASTOGRAM_APP_HASH}
API_KEYS

# Rebrand visible app name and Android applicationId. Do not rename Java packages.
sed -i "s/APP_PACKAGE=org.telegram.messenger/APP_PACKAGE=${APP_ID_PACKAGE}/" gradle.properties
sed -i 's/android:label="Telegram FOSS Beta"/android:label="Zastogram Beta"/g' TMessagesProj/config/debug/AndroidManifest*.xml
sed -i 's/android:label="Telegram FOSS"/android:label="Zastogram"/g' TMessagesProj/config/release/AndroidManifest*.xml

# Build native dependencies required by Telegram-FOSS.
export NDK="${ANDROID_HOME}/ndk/21.4.7075529"
export NINJA_PATH="$(command -v ninja)"
cd TMessagesProj/jni
./build_libvpx_clang.sh
./build_ffmpeg_clang.sh
./patch_ffmpeg.sh
./patch_boringssl.sh
./build_boringssl.sh
cd ../..

./gradlew --no-daemon assembleAfatDebug

mkdir -p ../zastogram-output
find . -path '*/build/outputs/apk/*/debug/*.apk' -print -exec cp {} ../zastogram-output/zastogram-afat-debug.apk \;
ls -lah ../zastogram-output

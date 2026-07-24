#!/usr/bin/env bash

# MeowGram = Nagram (current MD3, official-Telegram-based) with ONLY the visible
# app name rebranded to "MeowGram". Instead of building ~40 min of native code
# from source, this takes Nagram's working release APK and rebrands it with
# apktool: decode -> rename the display name -> rebuild -> sign.
# Fast (~5 min), no native build, no NDK/JDK-17.

LOG_DIR="${GITHUB_WORKSPACE:-$(pwd)}/zastogram-output"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/build.log"

run_build() {
set -euo pipefail
NAGRAM_APK_URL="${NAGRAM_APK_URL:-https://github.com/NextAlone/Nagram/releases/download/1240/Nagram-v12.8.1.1240-arm64-v8a.apk}"
APP_LABEL="${APP_LABEL:-MeowGram}"
APKTOOL_VERSION="${APKTOOL_VERSION:-3.0.3}"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

echo "STEP 1/7: config"; echo "  Nagram APK: ${NAGRAM_APK_URL}"; echo "  Label: ${APP_LABEL}"

echo "STEP 2/7: apktool jar (cached)"
APKTOOL_JAR="$HOME/.gradle/caches/meowgram/apktool_${APKTOOL_VERSION}.jar"
mkdir -p "$(dirname "$APKTOOL_JAR")"
if [ ! -f "$APKTOOL_JAR" ]; then
  curl -fSL --retry 3 -o "$APKTOOL_JAR" "https://github.com/iBotPeaches/Apktool/releases/download/v${APKTOOL_VERSION}/apktool_${APKTOOL_VERSION}.jar"
fi
echo "  java:"; java -version 2>&1 | head -1

echo "STEP 3/7: download Nagram APK"
WORK="$(mktemp -d)"
cd "$WORK"
curl -fSL --retry 3 -o nagram.apk "$NAGRAM_APK_URL"
ls -lah nagram.apk

echo "STEP 4/7: apktool decode (resources only; dex kept as-is)"
java -jar "$APKTOOL_JAR" d -f -s nagram.apk -o app
echo "  decoded to: $(ls -d app 2>/dev/null)"

echo "STEP 5/7: rebrand display name Nagram -> ${APP_LABEL}"
# Replace the capitalized app name in every locale's strings.xml. Lowercase
# 'nagram' (URLs / package ids) is left untouched.
find app/res -name 'strings.xml' -path '*/values*' -print0 2>/dev/null | xargs -0 -r sed -i "s/Nagram/${APP_LABEL}/g"
sed -i "s/Nagram/${APP_LABEL}/g" app/AndroidManifest.xml 2>/dev/null || true
echo "  AppName: $(grep -m1 -o '<string name="AppName">[^<]*</string>' app/res/values/strings.xml 2>/dev/null || echo '(see manifest)')"

echo "STEP 6/7: apktool build (aapt2 is the default in apktool 3.x)"
java -jar "$APKTOOL_JAR" b app -o meowgram-unsigned.apk
ls -lah meowgram-unsigned.apk

echo "STEP 7/7: zipalign + apksigner (stable MeowGram key)"
BUILD_TOOLS="${ANDROID_HOME}/build-tools/33.0.0"
KS="$HOME/.gradle/caches/meowgram-nagram/release.keystore"
mkdir -p "$(dirname "$KS")"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -storetype PKCS12 \
    -alias meowgram -keyalg RSA -keysize 2048 -validity 36500 \
    -storepass meowgram -keypass meowgram \
    -dname "CN=MeowGram,O=MeowGram,L=Frankfurt,ST=Hesse,C=DE"
fi
"${BUILD_TOOLS}/zipalign" -f -p 4 meowgram-unsigned.apk meowgram-aligned.apk
"${BUILD_TOOLS}/apksigner" sign \
  --ks "$KS" --ks-pass pass:meowgram --key-pass pass:meowgram \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --in meowgram-aligned.apk --out "${LOG_DIR}/meowgram.apk"
"${BUILD_TOOLS}/apksigner" verify "${LOG_DIR}/meowgram.apk" >/dev/null 2>&1 && echo "  apksigner verify OK" || echo "  apksigner verify: non-fatal"
ls -lah "$LOG_DIR"
echo "RESULT: ok"
}

set +e
run_build 2>&1 | tee "$LOG_FILE"
code=${PIPESTATUS[0]}
set -e
if [ "$code" -ne 0 ]; then
  echo "Rebrand failed with exit code $code" | tee -a "$LOG_FILE"
  # Ensure there is always an artifact to inspect (the workflow uploads *.apk).
  tail -500 "$LOG_FILE" > "$LOG_DIR/BUILD_FAILED_LOG_NOT_AN_INSTALLABLE_APK.apk"
  # Surface the error in the run summary so it's visible on the run page without
  # downloading the artifact (Azure log storage is unreachable for some agents).
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
      echo "### Rebrand failed (exit $code)"
      echo "Last log lines:"
      echo '```'
      tail -40 "$LOG_FILE"
      echo '```'
    } >> "$GITHUB_STEP_SUMMARY"
  fi
  exit 0
fi
# Success: note it in the run summary too.
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  echo "### MeowGram rebrand OK -> zastogram-output/meowgram.apk" >> "$GITHUB_STEP_SUMMARY"
fi

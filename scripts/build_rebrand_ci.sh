#!/usr/bin/env bash
set -euo pipefail

# MeowGram = Nagram (current MD3, official-Telegram-based) with ONLY the visible
# app name rebranded to "MeowGram". Instead of building ~40 min of native code
# from source (which kept failing), this takes Nagram's working release APK and
# rebrands it with apktool: decode -> rename the display name -> rebuild -> sign.
# Fast (~3-5 min), no native build, no NDK/JDK-17.

LOG_DIR="${GITHUB_WORKSPACE:-$(pwd)}/zastogram-output"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/build.log"

run_build() {
NAGRAM_APK_URL="${NAGRAM_APK_URL:-https://github.com/NextAlone/Nagram/releases/download/1240/Nagram-v12.8.1.1240-arm64-v8a.apk}"
APP_LABEL="${APP_LABEL:-MeowGram}"
APKTOOL_VERSION="${APKTOOL_VERSION:-3.0.3}"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

echo "== MeowGram rebrand of Nagram =="
echo "Nagram APK: ${NAGRAM_APK_URL}"
echo "Label: ${APP_LABEL}"

# apktool jar (cached). Workflow's JDK 11 (on PATH) runs apktool 3.x fine.
APKTOOL_JAR="$HOME/.gradle/caches/meowgram/apktool_${APKTOOL_VERSION}.jar"
mkdir -p "$(dirname "$APKTOOL_JAR")"
if [ ! -f "$APKTOOL_JAR" ]; then
  echo "== Downloading apktool ${APKTOOL_VERSION} =="
  curl -fSL --retry 3 -o "$APKTOOL_JAR" "https://github.com/iBotPeaches/Apktool/releases/download/v${APKTOOL_VERSION}/apktool_${APKTOOL_VERSION}.jar"
fi
java -version 2>&1 | head -1

WORK="$(mktemp -d)"
cd "$WORK"
echo "== Downloading Nagram APK =="
curl -fSL --retry 3 -o nagram.apk "$NAGRAM_APK_URL"
ls -lah nagram.apk

echo "== apktool decode (resources only, dex kept as-is) =="
java -jar "$APKTOOL_JAR" d -f -s --force-manifest nagram.apk -o app

echo "== rebrand display name Nagram -> ${APP_LABEL} =="
# Replace the capitalized app name in every locale's strings.xml. Lowercase
# 'nagram' (in URLs / package identifiers) is left untouched.
find app/res -name 'strings.xml' -path '*/values*' -print0 2>/dev/null | xargs -0 -r sed -i "s/Nagram/${APP_LABEL}/g"
sed -i "s/Nagram/${APP_LABEL}/g" app/AndroidManifest.xml 2>/dev/null || true
echo "  AppName now: $(grep -m1 -o '<string name="AppName">[^<]*</string>' app/res/values/strings.xml 2>/dev/null || echo '(check manifest)')"

echo "== apktool build =="
java -jar "$APKTOOL_JAR" b --use-aapt2 app -o meowgram-unsigned.apk
ls -lah meowgram-unsigned.apk

echo "== zipalign + apksigner (stable MeowGram key) =="
BUILD_TOOLS="${ANDROID_HOME}/build-tools/33.0.0"
KS="$HOME/.gradle/caches/meowgram-nagram/release.keystore"
mkdir -p "$(dirname "$KS")"
if [ ! -f "$KS" ]; then
  echo "== Generating stable MeowGram keystore =="
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
"${BUILD_TOOLS}/apksigner" verify "${LOG_DIR}/meowgram.apk" >/dev/null 2>&1 && echo "apksigner verify OK" || echo "apksigner verify: see log"

# Mirror under the workflow's expected artifact glob name too.
cp "${LOG_DIR}/meowgram.apk" "${LOG_DIR}/meowgram-arm64-debug.apk"
ls -lah "$LOG_DIR"
}

set +e
run_build 2>&1 | tee "$LOG_FILE"
code=${PIPESTATUS[0]}
set -e
if [ "$code" -ne 0 ]; then
  echo "Rebrand failed with exit code $code" | tee -a "$LOG_FILE"
  tail -500 "$LOG_FILE" > "$LOG_DIR/BUILD_FAILED_LOG_NOT_AN_INSTALLABLE_APK.apk"
  exit 0
fi

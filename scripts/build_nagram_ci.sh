#!/usr/bin/env bash
set -euo pipefail

# MeowGram on the Nagram base (current official-Telegram-derived client, MD3).
# arm64-only single-installer APK. The app module in Nagram is TMessagesProj
# (com.android.application, applicationId xyz.nextalone.nagram); its release
# signingConfig references TMessagesProj/release.keystore and reads
# KEYSTORE_PASS/ALIAS_NAME/ALIAS_PASS from local.properties.

LOG_DIR="${GITHUB_WORKSPACE:-$(pwd)}/zastogram-output"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/build.log"

run_build() {
MEOWGRAM_APP_ID="${MEOWGRAM_APP_ID:-${ZASTOGRAM_APP_ID:-20847974}}"
MEOWGRAM_APP_HASH="${MEOWGRAM_APP_HASH:-${ZASTOGRAM_APP_HASH:-41e494501cda12f9331a97131bd73eeb}}"
APP_LABEL="MeowGram"
KS_ALIAS="meowgram"
KS_PASS="meowgram"
UPSTREAM_REPO="https://github.com/NextAlone/Nagram.git"
UPSTREAM_DIR="Nagram-src"
BUILD_NATIVE_ARCHES="${BUILD_NATIVE_ARCHES:-arm64}"
BUILD_ANDROID_ABI="${BUILD_ANDROID_ABI:-arm64-v8a}"

echo "== MeowGram (Nagram) bootstrap =="
echo "Installing JDK 17..."
sudo apt-get update -qq 2>/dev/null || true
sudo apt-get install -y openjdk-17-jdk-headless >/dev/null 2>&1 || echo "WARN: openjdk-17 install failed"
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
[ -d "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | head -2 || echo "WARN: java not on PATH"

# Disk-aware swap (capped, guarded) so heavy native (BoringSSL) compiles don't
# OOM. Every command guarded so it can never abort the build.
{
  if ! sudo swapon --show 2>/dev/null | grep -q swapfile; then
    avail_mb=$(($(df -Pm / 2>/dev/null | awk 'NR==2{print $4}')))
    want_mb=6144
    [ "${want_mb:-0}" -gt $((avail_mb - 2048)) ] && want_mb=$((avail_mb - 2048))
    if [ "${want_mb:-0}" -ge 1024 ]; then
      echo "== Creating ${want_mb}MB swap (avail ${avail_mb}MB) =="
      sudo fallocate -l "${want_mb}M" /swapfile 2>/dev/null || sudo dd if=/dev/zero of=/swapfile bs=1M count="${want_mb}" status=none 2>/dev/null || true
      sudo chmod 600 /swapfile 2>/dev/null || true
      sudo mkswap /swapfile >/dev/null 2>&1 || true
      sudo swapon /swapfile 2>/dev/null || true
    fi
  fi
} || true
echo "== memory =="; free -h 2>/dev/null | head -3 || true
echo "== disk =="; df -h / 2>/dev/null | head -2 || true
export CMAKE_BUILD_PARALLEL_LEVEL="${CMAKE_BUILD_PARALLEL_LEVEL:-2}"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
SDKMGR="sdkmanager"
[ -x "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" ] && SDKMGR="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
echo "Installing NDK 27.2.12479018 + cmake 3.22.1..."
yes | "$SDKMGR" --licenses >/dev/null 2>&1 || true
"$SDKMGR" --install "ndk;27.2.12479018" "cmake;3.22.1" >/dev/null 2>&1 || echo "WARN: ndk/cmake install issues"
NAGRAM_NDK="$ANDROID_HOME/ndk/27.2.12479018"

echo "== MeowGram (Nagram) build config =="
echo "NDK=${NAGRAM_NDK}  ABI=${BUILD_ANDROID_ABI}  ARCHES=${BUILD_NATIVE_ARCHES}"

rm -rf "$UPSTREAM_DIR"
git clone --recursive --depth 1 --shallow-submodules "$UPSTREAM_REPO" "$UPSTREAM_DIR"
cd "$UPSTREAM_DIR"

# local.properties: SDK/NDK + Telegram API keys + signing credentials (read by
# TMessagesProj/build.gradle for the release signingConfig).
cat > local.properties <<LOCALPROPS
sdk.dir=${ANDROID_HOME}
ndk.dir=${NAGRAM_NDK}
TELEGRAM_APP_ID=${MEOWGRAM_APP_ID}
TELEGRAM_APP_HASH=${MEOWGRAM_APP_HASH}
KEYSTORE_PASS=${KS_PASS}
ALIAS_NAME=${KS_ALIAS}
ALIAS_PASS=${KS_PASS}
LOCALPROPS

# Avoid Sentry symbol upload attempting network calls (no token in CI).
rm -f sentry.properties 2>/dev/null || true

# Rebrand the visible app name Nagram -> MeowGram (display name only; the
# applicationId xyz.nextalone.nagram stays so it matches google-services.json).
find TMessagesProj/src/main/res -name 'strings.xml' -path '*/values*' -print0 2>/dev/null | xargs -0 -r sed -i "s/Nagram/${APP_LABEL}/g"
sed -i "s/Nagram/${APP_LABEL}/g" TMessagesProj/src/main/AndroidManifest.xml 2>/dev/null || true

# Stable release.keystore (TMessagesProj/release.keystore) cached so successive
# builds share a signature and install over each other.
KEYSTORE_CACHE="$HOME/.gradle/caches/meowgram-nagram/release.keystore"
mkdir -p "$(dirname "$KEYSTORE_CACHE")"
if [ ! -f "$KEYSTORE_CACHE" ]; then
  echo "== Generating stable MeowGram keystore =="
  keytool -genkeypair -v \
    -keystore "$KEYSTORE_CACHE" -storetype PKCS12 \
    -alias "${KS_ALIAS}" -keyalg RSA -keysize 2048 -validity 36500 \
    -storepass "${KS_PASS}" -keypass "${KS_PASS}" \
    -dname "CN=MeowGram,O=MeowGram,L=Frankfurt,ST=Hesse,C=DE" || echo "WARN: keystore gen failed"
fi
cp "$KEYSTORE_CACHE" TMessagesProj/release.keystore

# Cap tmessages CMake parallelism (default -j=16 OOMs); no-op if absent.
sed -i 's/-j=16/-j=2/g' TMessagesProj/build.gradle 2>/dev/null || true

# Native deps for arm64. Same build_*.sh as Telegram-FOSS; ninja forced to -j2.
export NDK="$NAGRAM_NDK"
export ANDROID_NDK_HOME="$NAGRAM_NDK"
REAL_NINJA="$(command -v ninja || echo ninja)"
printf '#!/bin/bash\nexec "%s" -j2 "$@"\n' "$REAL_NINJA" > /tmp/ninja-j2.sh
chmod +x /tmp/ninja-j2.sh
export NINJA_PATH="/tmp/ninja-j2.sh"
export PATH="$ANDROID_HOME/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"

echo "== MeowGram: native build (arm64) =="
echo "  nproc=$(nproc)"
cd TMessagesProj/jni
git submodule update --init ffmpeg libvpx boringssl 2>/dev/null || true
echo "== STEP: build_libvpx_clang.sh ${BUILD_NATIVE_ARCHES} =="
./build_libvpx_clang.sh ${BUILD_NATIVE_ARCHES}
echo "== STEP: build_ffmpeg_clang.sh ${BUILD_NATIVE_ARCHES} =="
./build_ffmpeg_clang.sh ${BUILD_NATIVE_ARCHES}
./patch_ffmpeg.sh
echo "== STEP: patch_boringssl.sh =="
./patch_boringssl.sh
echo "== STEP: build_boringssl.sh ${BUILD_NATIVE_ARCHES} (heavy) =="
./build_boringssl.sh ${BUILD_NATIVE_ARCHES}
cd ../..

echo "== STEP: gradlew :TMessagesProj:assembleRelease (arm64) =="
# Release (not Debug): a debug build has android:debuggable=true, which Infinix
# XOS / Play Protect reject on direct sideload as 'package damaged' even though
# the APK is valid. Release is non-debuggable and installs like the rebrand APK.
./gradlew --no-daemon -Pandroid.injected.build.abi=${BUILD_ANDROID_ABI} :TMessagesProj:assembleRelease

mkdir -p ../zastogram-output
find . -name '*.apk' -print
first_apk=$(find . -name '*.apk' | head -1)
if [ -z "${first_apk}" ]; then
  echo "No APK produced"
  find . -path '*/build/outputs/*' -maxdepth 9 -type f | sort | tail -200
  return 1
fi

# Make the APK directly installable: zipalign + apksigner (v1+v2+v3).
BUILD_TOOLS_DIR="${ANDROID_HOME}/build-tools/33.0.0"
out_apk="../zastogram-output/meowgram-nagram-arm64.apk"
cp "${first_apk}" "${out_apk}"
if [ -x "${BUILD_TOOLS_DIR}/apksigner" ] && [ -x "${BUILD_TOOLS_DIR}/zipalign" ]; then
  echo "== zipalign + apksigner =="
  tmp="${out_apk}.aligned"
  "${BUILD_TOOLS_DIR}/zipalign" -f -p 4 "${out_apk}" "${tmp}"
  "${BUILD_TOOLS_DIR}/apksigner" sign \
    --ks "TMessagesProj/release.keystore" --ks-pass pass:"${KS_PASS}" --key-pass pass:"${KS_PASS}" \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --in "${tmp}" --out "${out_apk}"
  rm -f "${tmp}"
  "${BUILD_TOOLS_DIR}/apksigner" verify "${out_apk}" >/dev/null 2>&1 && echo "apksigner verify OK" || echo "apksigner verify: non-fatal"
fi
ls -lah ../zastogram-output
echo "RESULT: ok"
}

set +e
run_build 2>&1 | tee "$LOG_FILE"
code=${PIPESTATUS[0]}
set -e
if [ "$code" -ne 0 ]; then
  echo "Build failed with exit code $code" | tee -a "$LOG_FILE"
  tail -500 "$LOG_FILE" > "$LOG_DIR/BUILD_FAILED_LOG_NOT_AN_INSTALLABLE_APK.apk"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
      echo "### Nagram build failed (exit $code)"
      echo "**Memory:**"; echo '```'; free -h 2>/dev/null | head -3 || true; echo '```'
      echo "**Disk (/):**"; echo '```'; df -h / 2>/dev/null | head -2 || true; echo '```'
      echo "**OOM killer (dmesg):**"; echo '```'
      { dmesg 2>/dev/null || sudo dmesg 2>/dev/null; } | grep -iE "killed process|out of memory|oom-kill|invoked oom" | tail -10 || echo "(none / dmesg unavailable)"
      echo '```'
      echo "**Last 80 log lines:**"; echo '```'; tail -80 "$LOG_FILE"; echo '```'
    } >> "$GITHUB_STEP_SUMMARY"
  fi
  exit 0
fi
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  echo "### MeowGram (Nagram) build OK -> zastogram-output/meowgram-nagram-arm64.apk" >> "$GITHUB_STEP_SUMMARY"
fi

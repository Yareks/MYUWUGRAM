#!/usr/bin/env bash
set -euo pipefail

# MeowGram build on the Nagram base (current official-Telegram-derived client,
# MD3 UI). arm64-only for a fast single-installer APK.
#
# Replaces the older Telegram-FOSS build. The older FOSS build is preserved at
# scripts/build_foss_ci.sh and selected via MEOWGRAM_CLIENT=foss in the
# dispatcher (scripts/build_zastogram_ci.sh).

LOG_DIR="${GITHUB_WORKSPACE:-$(pwd)}/zastogram-output"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/build.log"

run_build() {
MEOWGRAM_APP_ID="${MEOWGRAM_APP_ID:-${ZASTOGRAM_APP_ID:-20847974}}"
MEOWGRAM_APP_HASH="${MEOWGRAM_APP_HASH:-${ZASTOGRAM_APP_HASH:-41e494501cda12f9331a97131bd73eeb}}"

UPSTREAM_REPO="https://github.com/NextAlone/Nagram.git"
UPSTREAM_DIR="Nagram-src"
APP_ID_PACKAGE="org.meowgram.messenger"
APP_LABEL="MeowGram"
BUILD_NATIVE_ARCHES="${BUILD_NATIVE_ARCHES:-arm64}"
BUILD_ANDROID_ABI="${BUILD_ANDROID_ABI:-arm64-v8a}"

# Nagram needs NDK 27.2 + cmake 3.22.1 + JDK 17. The workflow only sets up
# NDK 21.4 / cmake 3.10.2 / JDK 11, so install the newer ones here (CI has
# network + sudo). Guarded so missing pieces are reported clearly.
echo "== MeowGram (Nagram) bootstrap =="
echo "Installing JDK 17..."
sudo apt-get update -qq 2>/dev/null || true
sudo apt-get install -y openjdk-17-jdk-headless >/dev/null 2>&1 || echo "WARN: openjdk-17 install failed"
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
if [ -d "$JAVA_HOME" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
java -version 2>&1 | head -2 || echo "WARN: java not on PATH"

# Prevent OOM during heavy native (BoringSSL) compilation. GitHub runners ship
# ~7GB RAM and no swap; the newer BoringSSL (post-quantum crypto) gets SIGKILL'd
# by the OOM killer mid-build. Add a disk-aware swap file (never larger than
# fits, leaving >=2GB free) and cap CMake parallelism. Every command here is
# guarded so a missing swap can NEVER abort the build (an earlier version used a
# 16GB swap that didn't fit and tripped 'set -e' via disk-full).
{
  if ! sudo swapon --show 2>/dev/null | grep -q swapfile; then
    avail_mb=$(($(df -Pm / 2>/dev/null | awk 'NR==2{print $4}')))
    want_mb=6144
    if [ "${want_mb:-0}" -gt $((avail_mb - 2048)) ]; then
      want_mb=$((avail_mb - 2048))
    fi
    if [ "${want_mb:-0}" -ge 1024 ]; then
      echo "== Creating ${want_mb}MB swap (avail ${avail_mb}MB on /) =="
      sudo fallocate -l "${want_mb}M" /swapfile 2>/dev/null || sudo dd if=/dev/zero of=/swapfile bs=1M count="${want_mb}" status=none 2>/dev/null || true
      sudo chmod 600 /swapfile 2>/dev/null || true
      sudo mkswap /swapfile >/dev/null 2>&1 || true
      sudo swapon /swapfile 2>/dev/null || true
    fi
  fi
} || true
echo "== memory ==" && (free -h 2>/dev/null | head -3 || true)
echo "== disk ==" && (df -h / 2>/dev/null | head -2 || true)
export CMAKE_BUILD_PARALLEL_LEVEL="${CMAKE_BUILD_PARALLEL_LEVEL:-2}"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
SDKMGR="sdkmanager"
if [ -x "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" ]; then
  SDKMGR="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
fi
echo "Installing NDK 27.2.12479018 + cmake 3.22.1..."
yes | "$SDKMGR" --licenses >/dev/null 2>&1 || true
"$SDKMGR" --install "ndk;27.2.12479018" "cmake;3.22.1" >/dev/null 2>&1 || echo "WARN: ndk/cmake install via sdkmanager had issues"
NAGRAM_NDK="$ANDROID_HOME/ndk/27.2.12479018"

echo "== MeowGram (Nagram) build config =="
echo "UPSTREAM_REPO=${UPSTREAM_REPO}"
echo "BUILD_NATIVE_ARCHES=${BUILD_NATIVE_ARCHES}"
echo "BUILD_ANDROID_ABI=${BUILD_ANDROID_ABI}"
echo "JAVA_HOME=${JAVA_HOME}"
echo "ANDROID_HOME=${ANDROID_HOME}"
echo "NDK=${NAGRAM_NDK}"

rm -rf "$UPSTREAM_DIR"
git clone --recursive --depth 1 --shallow-submodules "$UPSTREAM_REPO" "$UPSTREAM_DIR"
cd "$UPSTREAM_DIR"

# local.properties: SDK + NDK + Telegram API keys.
cat > local.properties <<LOCALPROPS
sdk.dir=${ANDROID_HOME}
ndk.dir=${NAGRAM_NDK}
TELEGRAM_APP_ID=${MEOWGRAM_APP_ID}
TELEGRAM_APP_HASH=${MEOWGRAM_APP_HASH}
LOCALPROPS

# Rebrand applicationId + visible name. APP_PACKAGE sets applicationId.
sed -i "s/APP_PACKAGE=org.telegram.messenger/APP_PACKAGE=${APP_ID_PACKAGE}/" gradle.properties
sed -i 's/android:label="Telegram"/android:label="MeowGram"/g' TMessagesProj/config/debug/AndroidManifest*.xml TMessagesProj/config/release/AndroidManifest*.xml 2>/dev/null || true

# Nagram's signingConfigs reference TMessagesProj/config/release.keystore with
# alias=androidkey / pass=android. That file is NOT in the repo, so generate a
# stable one (keytool ships with the JDK) and cache it under the Gradle cache
# dir so every build shares a signature and APKs update over each other.
KEYSTORE_CACHE="$HOME/.gradle/caches/meowgram-nagram/release.keystore"
mkdir -p "$(dirname "$KEYSTORE_CACHE")"
if [ ! -f "$KEYSTORE_CACHE" ]; then
  echo "== Generating stable MeowGram release.keystore (Nagram) =="
  if command -v keytool >/dev/null 2>&1; then
    keytool -genkeypair -v \
      -keystore "$KEYSTORE_CACHE" -storetype PKCS12 \
      -alias androidkey -keyalg RSA -keysize 2048 -validity 36500 \
      -storepass android -keypass android \
      -dname "CN=MeowGram,O=MeowGram,L=Frankfurt,ST=Hesse,C=DE" \
      || echo "WARN: keystore generation failed"
  fi
fi
mkdir -p TMessagesProj/config
cp "$KEYSTORE_CACHE" TMessagesProj/config/release.keystore 2>/dev/null || true

# Limit ABI to arm64-v8a only for a fast single-arch build. Nagram declares
# abiFilters in the 'afat' flavor (app module) and defaultConfig (lib module).
BUILD_ANDROID_ABI="${BUILD_ANDROID_ABI}" python3 - <<'PATCH_ABI'
import os
from pathlib import Path
abi = os.environ['BUILD_ANDROID_ABI']
for rel in ['TMessagesProj_App/build.gradle', 'TMessagesProj_AppStandalone/build.gradle']:
    p = Path(rel)
    if p.exists():
        t = p.read_text()
        t = t.replace('abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"', f'abiFilters "{abi}"')
        p.write_text(t)
p = Path('TMessagesProj/build.gradle')
if p.exists():
    t = p.read_text()
    if 'abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"' in t:
        t = t.replace('abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"', f'abiFilters "{abi}"')
    # Cap the tmessages CMake build parallelism; the upstream default (-j=16)
    # OOMs the runner. No-op if the arg is absent.
    t = t.replace('-j=16', '-j=2')
    p.write_text(t)
PATCH_ABI

# Native dependencies (ffmpeg/libvpx/boringssl) for arm64 only. Nagram ships the
# same build_*.sh scripts as Telegram-FOSS. NDK env is needed by them.
export NDK="$NAGRAM_NDK"
export ANDROID_NDK_HOME="$NAGRAM_NDK"
# Force ninja to -j2 via a wrapper set as NINJA_PATH. build_boringssl.sh passes
# -DCMAKE_MAKE_PROGRAM=${NINJA_PATH} to CMake, so this caps the BoringSSL build
# parallelism regardless of whether CMAKE_BUILD_PARALLEL_LEVEL is honored —
# without it the run OOMs mid-compile at [~474/639].
REAL_NINJA="$(command -v ninja || echo ninja)"
printf '#!/bin/bash\nexec "%s" -j2 "$@"\n' "$REAL_NINJA" > /tmp/ninja-j2.sh
chmod +x /tmp/ninja-j2.sh
export NINJA_PATH="/tmp/ninja-j2.sh"
export PATH="$ANDROID_HOME/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"

echo "== MeowGram: native build starting =="
echo "  nproc=$(nproc)"
free -h 2>/dev/null | head -3 || true
df -h / 2>/dev/null | head -2 || true

cd TMessagesProj/jni

# Make sure the submodules are present (recursive clone should have done it, but
# be safe; these are no-ops if already checked out).
git submodule update --init ffmpeg libvpx boringssl 2>/dev/null || true

echo "== STEP: build_libvpx_clang.sh ${BUILD_NATIVE_ARCHES} =="
./build_libvpx_clang.sh ${BUILD_NATIVE_ARCHES}
echo "== STEP: build_ffmpeg_clang.sh ${BUILD_NATIVE_ARCHES} =="
./build_ffmpeg_clang.sh ${BUILD_NATIVE_ARCHES}
./patch_ffmpeg.sh
echo "== STEP: patch_boringssl.sh =="
./patch_boringssl.sh
echo "== STEP: build_boringssl.sh ${BUILD_NATIVE_ARCHES} (heavy, OOM-prone) =="
./build_boringssl.sh ${BUILD_NATIVE_ARCHES}
echo "== after boringssl =="
free -h 2>/dev/null | head -3 || true
df -h / 2>/dev/null | head -2 || true
cd ../..

echo "== gradlew assembleAfatDebug =="
./gradlew --no-daemon -Pandroid.injected.build.abi=${BUILD_ANDROID_ABI} assembleAfatDebug

# Collect the APK and make it install-friendly (zipalign + apksigner v1+v2+v3).
mkdir -p ../zastogram-output
find . -name '*.apk' -print
first_apk=$(find . -name '*.apk' | head -1)
if [ -z "${first_apk}" ]; then
  echo "No APK files were produced"
  find . -path '*/build/outputs/*' -maxdepth 8 -type f | sort | tail -200
  return 1
fi

BUILD_TOOLS_DIR="${ANDROID_HOME}/build-tools/33.0.0"
APKSIGNER="${BUILD_TOOLS_DIR}/apksigner"
ZIPALIGN="${BUILD_TOOLS_DIR}/zipalign"
out_apk="../zastogram-output/meowgram-nagram-arm64-debug.apk"
cp "${first_apk}" "${out_apk}"

if [ -x "$APKSIGNER" ] && [ -x "$ZIPALIGN" ] && [ -f "TMessagesProj/config/release.keystore" ]; then
  echo "== zipalign + apksigner (v1+v2+v3) =="
  tmp_aligned="${out_apk}.aligned"
  "$ZIPALIGN" -f -p 4 "${out_apk}" "${tmp_aligned}"
  "$APKSIGNER" sign \
    --ks "TMessagesProj/config/release.keystore" --ks-pass pass:android --key-pass pass:android \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --in "${tmp_aligned}" --out "${out_apk}"
  rm -f "${tmp_aligned}"
  "$APKSIGNER" verify "${out_apk}" >/dev/null 2>&1 && echo "apksigner verify OK" || echo "apksigner verify: see logs"
else
  echo "== zipalign/apksigner unavailable; using Gradle-signed APK as-is =="
fi
ls -lah ../zastogram-output
}

set +e
run_build 2>&1 | tee "$LOG_FILE"
code=${PIPESTATUS[0]}
set -e
if [ "$code" -ne 0 ]; then
  echo "Build failed with exit code $code" | tee -a "$LOG_FILE"
  tail -500 "$LOG_FILE" > "$LOG_DIR/BUILD_FAILED_LOG_NOT_AN_INSTALLABLE_APK.apk"
  # Surface the failure on the run page (no artifact download needed): memory,
  # disk, OOM-killer messages from dmesg, and the last 80 log lines.
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

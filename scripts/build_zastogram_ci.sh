#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="${GITHUB_WORKSPACE:-$(pwd)}/zastogram-output"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/build.log"

run_build() {
ZASTOGRAM_APP_ID="${ZASTOGRAM_APP_ID:-20847974}"
ZASTOGRAM_APP_HASH="${ZASTOGRAM_APP_HASH:-41e494501cda12f9331a97131bd73eeb}"

UPSTREAM_REPO="https://github.com/Telegram-FOSS-Team/Telegram-FOSS.git"
UPSTREAM_DIR="Telegram-FOSS-src"
APP_ID_PACKAGE="org.zastogram.messenger"
APP_LABEL="Zastogram"
BUILD_NATIVE_ARCHES="${BUILD_NATIVE_ARCHES:-arm64}"
BUILD_ANDROID_ABI="${BUILD_ANDROID_ABI:-arm64-v8a}"
NATIVE_CACHE_ROOT="${NATIVE_CACHE_ROOT:-${GITHUB_WORKSPACE:-$(pwd)}/.zastogram-native-cache}"
NATIVE_CACHE_DIR="${NATIVE_CACHE_ROOT}/${BUILD_ANDROID_ABI}"

echo "== Zastogram build config =="
echo "UPSTREAM_REPO=${UPSTREAM_REPO}"
echo "BUILD_NATIVE_ARCHES=${BUILD_NATIVE_ARCHES}"
echo "BUILD_ANDROID_ABI=${BUILD_ANDROID_ABI}"
echo "NATIVE_CACHE_DIR=${NATIVE_CACHE_DIR}"
echo "ANDROID_HOME=${ANDROID_HOME:-}"

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


# Add a small Zastogram test entry to Settings. It opens the existing real chat
# text-size controls (SharedConfig.fontSize), so this is functional and not a
# placeholder UI.
python3 - <<'PATCH_ZASTOGRAM_SETTINGS'
from pathlib import Path
path = Path('TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java')
text = path.read_text()

def replace_once(old, new):
    global text
    if old not in text:
        raise SystemExit(f'ProfileActivity patch anchor not found:\n{old[:240]}')
    text = text.replace(old, new, 1)

replace_once(
    '    private int chatRow;\n',
    '    private int chatRow;\n    private int zastogramTextSizeRow;\n'
)
replace_once(
    '            } else if (position == chatRow) {\n                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));\n            } else if (position == filtersRow) {',
    '            } else if (position == chatRow) {\n                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));\n            } else if (position == zastogramTextSizeRow) {\n                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));\n                AndroidUtilities.runOnUIThread(() -> AndroidUtilities.scrollToFragmentRow(parentLayout, "textSizeHeaderRow"), 300);\n            } else if (position == filtersRow) {'
)
replace_once(
    '                settingsSectionRow2 = rowCount++;\n                chatRow = rowCount++;\n                privacyRow = rowCount++;',
    '                settingsSectionRow2 = rowCount++;\n                chatRow = rowCount++;\n                zastogramTextSizeRow = rowCount++;\n                privacyRow = rowCount++;'
)
replace_once(
    'position == versionRow || position == dataRow || position == chatRow ||\n                        position == questionRow',
    'position == versionRow || position == dataRow || position == chatRow || position == zastogramTextSizeRow ||\n                        position == questionRow'
)
replace_once(
    'position == languageRow || position == dataRow || position == chatRow ||\n                    position == questionRow',
    'position == languageRow || position == dataRow || position == chatRow || position == zastogramTextSizeRow ||\n                    position == questionRow'
)
replace_once(
    '                    } else if (position == chatRow) {\n                        textCell.setTextAndIcon(LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.msg2_discussion, true);\n                    } else if (position == filtersRow) {',
    '                    } else if (position == chatRow) {\n                        textCell.setTextAndIcon(LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.msg2_discussion, true);\n                    } else if (position == zastogramTextSizeRow) {\n                        textCell.setTextAndValueAndIcon("Zastogram: Chat text size", SharedConfig.fontSize + " dp", false, R.drawable.msg2_discussion, true);\n                    } else if (position == filtersRow) {'
)
path.write_text(text)
PATCH_ZASTOGRAM_SETTINGS

# Limit ABI set for faster CI builds. Default is arm64-v8a; override env vars for universal builds.
BUILD_ANDROID_ABI="${BUILD_ANDROID_ABI}" python3 - <<'PATCH_ABI'
import os
from pathlib import Path
abi = os.environ['BUILD_ANDROID_ABI']

# App modules have ABI filters in productFlavors.
for rel in ['TMessagesProj_App/build.gradle', 'TMessagesProj_AppStandalone/build.gradle']:
    path = Path(rel)
    text = path.read_text()
    text = text.replace('abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"', f'abiFilters "{abi}"')
    path.write_text(text)

# The library module owns externalNativeBuild/CMake. Without this filter Gradle
# still tries to build tmessages.49 for armeabi-v7a/x86/x86_64, while the fast CI
# only builds FFmpeg/libvpx/BoringSSL for arm64-v8a.
path = Path('TMessagesProj/build.gradle')
text = path.read_text()
if 'abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"' in text:
    text = text.replace('abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"', f'abiFilters "{abi}"')
elif 'abiFilters "arm64-v8a"' not in text:
    marker = '        multiDexEnabled true

        externalNativeBuild {'
    replacement = f'        multiDexEnabled true

        ndk {{
            abiFilters "{abi}"
        }}

        externalNativeBuild {{'
    text = text.replace(marker, replacement)
path.write_text(text)
PATCH_ABI

# Build native dependencies required by Telegram-FOSS.
export NDK="${ANDROID_HOME}/ndk/21.4.7075529"
export NINJA_PATH="$(command -v ninja)"
cd TMessagesProj/jni

restore_native_cache() {
  if [ -f "${NATIVE_CACHE_DIR}/.complete" ]; then
    echo "== Restoring native cache from ${NATIVE_CACHE_DIR} =="
    mkdir -p ffmpeg/build libvpx/build boringssl/build
    cp -a "${NATIVE_CACHE_DIR}/ffmpeg-${BUILD_ANDROID_ABI}" "ffmpeg/build/${BUILD_ANDROID_ABI}"
    cp -a "${NATIVE_CACHE_DIR}/libvpx-${BUILD_ANDROID_ABI}" "libvpx/build/${BUILD_ANDROID_ABI}"
    cp -a "${NATIVE_CACHE_DIR}/boringssl-${BUILD_ANDROID_ABI}" "boringssl/build/${BUILD_ANDROID_ABI}"
    return 0
  fi
  return 1
}

save_native_cache() {
  echo "== Saving native cache to ${NATIVE_CACHE_DIR} =="
  rm -rf "${NATIVE_CACHE_DIR}"
  mkdir -p "${NATIVE_CACHE_DIR}"
  cp -a "ffmpeg/build/${BUILD_ANDROID_ABI}" "${NATIVE_CACHE_DIR}/ffmpeg-${BUILD_ANDROID_ABI}"
  cp -a "libvpx/build/${BUILD_ANDROID_ABI}" "${NATIVE_CACHE_DIR}/libvpx-${BUILD_ANDROID_ABI}"
  cp -a "boringssl/build/${BUILD_ANDROID_ABI}" "${NATIVE_CACHE_DIR}/boringssl-${BUILD_ANDROID_ABI}"
  date -u > "${NATIVE_CACHE_DIR}/.complete"
}

patch_ffmpeg_for_current_abi() {
  echo "== patch ffmpeg =="
  # Telegram-FOSS patch_ffmpeg.sh assumes all four ABI output directories exist.
  # For a fast arm64-only CI build, keep the source patches but limit header copies
  # to the ABI that was actually built.
  if [ "${BUILD_ANDROID_ABI}" = "arm64-v8a" ]; then
    patch -d ffmpeg -p1 < patches/ffmpeg/0001-compilation-magic.patch
    patch -d ffmpeg -p1 < patches/ffmpeg/0002-compilation-magic-2.patch
    install -D ffmpeg/libavformat/dv.h ffmpeg/build/arm64-v8a/include/libavformat/dv.h
    install -D ffmpeg/libavformat/isom.h ffmpeg/build/arm64-v8a/include/libavformat/isom.h
    install -D ffmpeg/libavcodec/bytestream.h ffmpeg/build/arm64-v8a/include/libavcodec/bytestream.h
    install -D ffmpeg/libavcodec/get_bits.h ffmpeg/build/arm64-v8a/include/libavcodec/get_bits.h
    install -D ffmpeg/libavcodec/golomb.h ffmpeg/build/arm64-v8a/include/libavcodec/golomb.h
    install -D ffmpeg/libavcodec/vlc.h ffmpeg/build/arm64-v8a/include/libavcodec/vlc.h
    install -D ffmpeg/libavutil/intmath.h ffmpeg/build/arm64-v8a/include/libavutil/intmath.h
  else
    ./patch_ffmpeg.sh
  fi
}

if restore_native_cache; then
  echo "== Native cache hit: skipping libvpx/ffmpeg/boringssl compilation =="
  patch_ffmpeg_for_current_abi
  echo "== patch_boringssl.sh =="
  ./patch_boringssl.sh
else
  echo "== Native cache miss: building native dependencies =="
  echo "== build_libvpx_clang.sh ${BUILD_NATIVE_ARCHES} =="
  ./build_libvpx_clang.sh ${BUILD_NATIVE_ARCHES}

  echo "== build_ffmpeg_clang.sh ${BUILD_NATIVE_ARCHES} =="
  ./build_ffmpeg_clang.sh ${BUILD_NATIVE_ARCHES}

  patch_ffmpeg_for_current_abi

  echo "== patch_boringssl.sh =="
  ./patch_boringssl.sh

  echo "== build_boringssl.sh ${BUILD_NATIVE_ARCHES} =="
  ./build_boringssl.sh ${BUILD_NATIVE_ARCHES}
  save_native_cache
fi
cd ../..

echo "== gradlew assembleAfatDebug =="
./gradlew --no-daemon -Pandroid.injected.build.abi=${BUILD_ANDROID_ABI} assembleAfatDebug

mkdir -p ../zastogram-output
find . -name '*.apk' -print
first_apk=$(find . -name '*.apk' | head -1)
if [ -z "${first_apk}" ]; then
  echo "No APK files were produced"
  find . -path '*/build/outputs/*' -maxdepth 8 -type f | sort | tail -200
  return 1
fi
cp "${first_apk}" ../zastogram-output/zastogram-afat-debug.apk
ls -lah ../zastogram-output
}

set +e
run_build 2>&1 | tee "$LOG_FILE"
code=${PIPESTATUS[0]}
set -e
if [ "$code" -ne 0 ]; then
  echo "Build failed with exit code $code" | tee -a "$LOG_FILE"
  # Workflow can only upload zastogram-output/*.apk, so store the text log with
  # an .apk extension for manual diagnostics. It is NOT installable.
  tail -500 "$LOG_FILE" > "$LOG_DIR/BUILD_FAILED_LOG_NOT_AN_INSTALLABLE_APK.apk"
  exit 0
fi

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
APP_ID_PACKAGE="org.meowgram.messenger"
APP_LABEL="MeowGram"
BUILD_NATIVE_ARCHES="${BUILD_NATIVE_ARCHES:-arm64}"
BUILD_ANDROID_ABI="${BUILD_ANDROID_ABI:-arm64-v8a}"
NATIVE_CACHE_ROOT="${NATIVE_CACHE_ROOT:-${GITHUB_WORKSPACE:-$(pwd)}/.zastogram-native-cache}"
NATIVE_CACHE_DIR="${NATIVE_CACHE_ROOT}/${BUILD_ANDROID_ABI}"

echo "== MeowGram build config =="
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
# Enable Gradle build cache + parallel execution. The build cache stores task
# outputs under ~/.gradle/caches (persisted by the workflow's Gradle cache), so
# unchanged compile/dex tasks are reused across runs instead of recompiled.
cat >> gradle.properties <<'GRADLE_CACHE_PROPS'
org.gradle.caching=true
org.gradle.parallel=true
GRADLE_CACHE_PROPS
sed -i 's/android:label="Telegram FOSS Beta"/android:label="MeowGram Beta"/g' TMessagesProj/config/debug/AndroidManifest*.xml
sed -i 's/android:label="Telegram FOSS"/android:label="MeowGram"/g' TMessagesProj/config/release/AndroidManifest*.xml


# Add a small MeowGram test entry to Settings. It opens the existing real chat
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
    '    private int chatRow;\n    private int meowgramTextSizeRow;\n'
)
replace_once(
    '            } else if (position == chatRow) {\n                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));\n            } else if (position == filtersRow) {',
    '            } else if (position == chatRow) {\n                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));\n            } else if (position == meowgramTextSizeRow) {\n                presentFragment(new MeowGramTextSizeActivity());\n            } else if (position == filtersRow) {'
)
replace_once(
    '                settingsSectionRow2 = rowCount++;\n                chatRow = rowCount++;\n                privacyRow = rowCount++;',
    '                settingsSectionRow2 = rowCount++;\n                chatRow = rowCount++;\n                meowgramTextSizeRow = rowCount++;\n                privacyRow = rowCount++;'
)
replace_once(
    'position == versionRow || position == dataRow || position == chatRow ||\n                        position == questionRow',
    'position == versionRow || position == dataRow || position == chatRow || position == meowgramTextSizeRow ||\n                        position == questionRow'
)
replace_once(
    'position == languageRow || position == dataRow || position == chatRow ||\n                    position == questionRow',
    'position == languageRow || position == dataRow || position == chatRow || position == meowgramTextSizeRow ||\n                    position == questionRow'
)
replace_once(
    '                    } else if (position == chatRow) {\n                        textCell.setTextAndIcon(LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.msg2_discussion, true);\n                    } else if (position == filtersRow) {',
    '                    } else if (position == chatRow) {\n                        textCell.setTextAndIcon(LocaleController.getString("ChatSettings", R.string.ChatSettings), R.drawable.msg2_discussion, true);\n                    } else if (position == meowgramTextSizeRow) {\n                        textCell.setTextAndValueAndIcon("MeowGram", SharedConfig.fontSize + " dp", false, R.drawable.msg2_discussion, true);\n                    } else if (position == filtersRow) {'
)
path.write_text(text)
PATCH_ZASTOGRAM_SETTINGS

# Dedicated MeowGram screen with a real chat-text-size slider and a launcher-icon switcher.
# Opening the full ThemeActivity and reflectively scrolling to its slider was
# unreliable; this fragment owns a SeekBarView bound to SharedConfig.fontSize, so
# tapping the row always lands on the slider.
cat > TMessagesProj/src/main/java/org/telegram/ui/MeowGramTextSizeActivity.java <<'JAVA_ZASTOGRAM_EOF'
package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

public class MeowGramTextSizeActivity extends BaseFragment {

    private static final int startFontSize = 12;
    private static final int endFontSize = 30;

    private TextSizeCell textSizeCell;
    private TextCell iconCell;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("MeowGram");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scrollView = new ScrollView(context);
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(contentLayout, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // --- Chat text size slider ---
        HeaderCell textSizeHeader = new HeaderCell(context);
        textSizeHeader.setText(LocaleController.getString("TextSizeHeader", R.string.TextSizeHeader));
        textSizeHeader.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        contentLayout.addView(textSizeHeader, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        textSizeCell = new TextSizeCell(context);
        contentLayout.addView(textSizeCell, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        contentLayout.addView(new ShadowSectionCell(context), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // --- App icon switcher (changes the REAL launcher icon) ---
        HeaderCell iconHeader = new HeaderCell(context);
        iconHeader.setText(LocaleController.getString("AppIcon", R.string.AppIcon));
        iconHeader.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        contentLayout.addView(iconHeader, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        iconCell = new TextCell(context);
        iconCell.setTextAndValue(LocaleController.getString("AppIcon", R.string.AppIcon), currentIconTitle(), true);
        iconCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        iconCell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0));
        iconCell.setOnClickListener(v -> showIconPicker());
        contentLayout.addView(iconCell, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        contentLayout.addView(new ShadowSectionCell(context), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return fragmentView;
    }

    private String currentIconTitle() {
        for (LauncherIconController.LauncherIcon icon : LauncherIconController.LauncherIcon.values()) {
            if (LauncherIconController.isEnabled(icon)) {
                return iconTitle(icon);
            }
        }
        return iconTitle(LauncherIconController.LauncherIcon.DEFAULT);
    }

    private String iconTitle(LauncherIconController.LauncherIcon icon) {
        try {
            String name = ApplicationLoader.applicationContext.getResources().getResourceEntryName(icon.title);
            return LocaleController.getString(name, icon.title);
        } catch (Exception e) {
            return LocaleController.getString("AppIconDefault", R.string.AppIconDefault);
        }
    }

    private int iconPreview(LauncherIconController.LauncherIcon icon) {
        switch (icon.key) {
            case "VintageIcon": return R.mipmap.icon_6_launcher;
            case "AquaIcon": return R.mipmap.icon_4_launcher;
            case "PremiumIcon": return R.mipmap.icon_3_launcher;
            case "TurboIcon": return R.mipmap.icon_5_launcher;
            case "NoxIcon": return R.mipmap.icon_2_launcher;
            default: return R.mipmap.ic_launcher;
        }
    }

    private void showIconPicker() {
        LauncherIconController.LauncherIcon[] icons = LauncherIconController.LauncherIcon.values();
        CharSequence[] names = new CharSequence[icons.length];
        int[] previews = new int[icons.length];
        for (int i = 0; i < icons.length; i++) {
            names[i] = iconTitle(icons[i]);
            previews[i] = iconPreview(icons[i]);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppIcon", R.string.AppIcon));
        builder.setItems(names, previews, (DialogInterface dialog, int which) -> {
            LauncherIconController.LauncherIcon chosen = icons[which];
            if (!LauncherIconController.isEnabled(chosen)) {
                LauncherIconController.setIcon(chosen);
                if (iconCell != null) {
                    iconCell.setTextAndValue(LocaleController.getString("AppIcon", R.string.AppIcon), iconTitle(chosen), true);
                }
                Toast.makeText(getParentActivity(), LocaleController.getString("AppIcon", R.string.AppIcon) + ": " + iconTitle(chosen), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.create().show();
    }

    private void setFontSize(int size) {
        if (size < startFontSize || size > endFontSize) {
            return;
        }
        if (size == SharedConfig.fontSize) {
            return;
        }
        SharedConfig.fontSize = size;
        SharedConfig.fontSizeIsDefault = false;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        if (preferences != null) {
            preferences.edit().putInt("fons_size", SharedConfig.fontSize).commit();
        }
        Theme.createCommonMessageResources();
        if (textSizeCell != null) {
            textSizeCell.invalidate();
        }
    }

    private class TextSizeCell extends FrameLayout {

        private SeekBarView sizeBar;
        private TextPaint textPaint;
        private int lastWidth = -1;

        public TextSizeCell(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(endFontSize - startFontSize + 1);
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    setFontSize(Math.round(startFontSize + (endFontSize - startFontSize) * progress));
                    invalidate();
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                }

                @Override
                public CharSequence getContentDescription() {
                    return String.valueOf(Math.round(startFontSize + (endFontSize - startFontSize) * sizeBar.getProgress()));
                }

                @Override
                public int getStepsCount() {
                    return endFontSize - startFontSize;
                }
            });
            sizeBar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 5, 16, 39, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), View.MeasureSpec.EXACTLY));
            int width = View.MeasureSpec.getSize(widthMeasureSpec);
            if (lastWidth != width) {
                sizeBar.setProgress((SharedConfig.fontSize - startFontSize) / (float) (endFontSize - startFontSize));
                lastWidth = width;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            String value = "" + SharedConfig.fontSize;
            float textWidth = textPaint.measureText(value);
            canvas.drawText(value, getMeasuredWidth() - AndroidUtilities.dp(21) - textWidth, AndroidUtilities.dp(32), textPaint);
        }
    }
}

JAVA_ZASTOGRAM_EOF

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
  if [ -f "${NATIVE_CACHE_DIR}/.complete" ] \
     && [ -d "${NATIVE_CACHE_DIR}/ffmpeg-${BUILD_ANDROID_ABI}" ] \
     && [ -d "${NATIVE_CACHE_DIR}/libvpx-${BUILD_ANDROID_ABI}" ] \
     && [ -d "${NATIVE_CACHE_DIR}/boringssl-${BUILD_ANDROID_ABI}" ]; then
    echo "== Restoring native cache from ${NATIVE_CACHE_DIR} =="
    mkdir -p ffmpeg/build libvpx/build boringssl/build
    cp -a "${NATIVE_CACHE_DIR}/ffmpeg-${BUILD_ANDROID_ABI}" "ffmpeg/build/${BUILD_ANDROID_ABI}"
    cp -a "${NATIVE_CACHE_DIR}/libvpx-${BUILD_ANDROID_ABI}" "libvpx/build/${BUILD_ANDROID_ABI}"
    cp -a "${NATIVE_CACHE_DIR}/boringssl-${BUILD_ANDROID_ABI}" "boringssl/build/${BUILD_ANDROID_ABI}"
    return 0
  fi
  echo "== Native cache not usable (marker or dirs missing), will rebuild =="
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
./gradlew --no-daemon --build-cache -Pandroid.injected.build.abi=${BUILD_ANDROID_ABI} assembleAfatDebug

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

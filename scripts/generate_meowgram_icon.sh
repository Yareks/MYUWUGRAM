#!/usr/bin/env bash
#
# Replaces the MeowGram launcher icon at BUILD TIME from a source image.
#
# Usage: scripts/generate_meowgram_icon.sh [SOURCE_IMAGE] [RES_ROOT]
#   SOURCE_IMAGE  path to a square-ish image (default: branding/icon.png)
#   RES_ROOT      upstream res dir (default: TMessagesProj/src/main/res)
#
# What it produces:
#   - Legacy launcher PNGs (ic_launcher.png + ic_launcher_round.png) at 5
#     densities = source photo (center-cropped square) + semi-transparent
#     Telegram paper-plane overlay.
#   - Android 8+ adaptive icon: background = source photo, foreground =
#     semi-transparent plane. (mipmap-anydpi-v26/ic_launcher*.xml rewritten.)
#
# If SOURCE_IMAGE is missing, this is a no-op (upstream default icon is kept),
# so the build never breaks while waiting for a custom image.
#
# Requires ImageMagick (convert/identify). The CI workflow installs it.

set -euo pipefail

SRC="${1:-branding/icon.png}"
RES="${2:-TMessagesProj/src/main/res}"
PLANE_ALPHA="${PLANE_ALPHA:-0.55}"   # opacity of the paper-plane overlay

if [ ! -f "$SRC" ]; then
  echo "== generate_icon: $SRC not found; keeping upstream icon =="
  exit 0
fi

# Graceful degradation: if ImageMagick is unavailable in the runner (e.g. the
# workflow doesn't install it), skip the icon replacement rather than failing
# the whole build. The upstream default icon is kept in that case.
if ! command -v convert >/dev/null 2>&1 || ! command -v identify >/dev/null 2>&1; then
  echo "== generate_icon: ImageMagick (convert/identify) not installed; skipping custom icon =="
  exit 0
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "== generate_icon: source=$SRC plane_alpha=$PLANE_ALPHA =="

# --- 1. square center-crop of the source at high resolution ---
maxside=$(identify -format '%w %h\n' "$SRC" | awk '{print ($1>$2?$1:$2)}')
convert "$SRC" -gravity center -background black -extent "${maxside}x${maxside}" -resize 512x512 "$TMP/square.png"

# densities: launcher_px (legacy PNG) | adaptive_px (108dp canvas)
gen_density() {
  local dir="$1" lp="$2" ap="$3"
  local out="$RES/$dir"
  local plane="$out/icon_foreground_sa.png"

  # --- legacy launcher icon: photo + semi-transparent plane ---
  if [ -f "$plane" ]; then
    convert "$plane" -resize "${ap}x${ap}" "$TMP/plane_$ap.png"
    convert "$TMP/plane_$ap.png" -channel A -evaluate multiply "$PLANE_ALPHA" +channel "$TMP/planeF_$ap.png"
    convert "$TMP/square.png" -resize "${lp}x${lp}" "$TMP/base_$lp.png"
    convert "$TMP/planeF_$ap.png" -resize "${lp}x${lp}" "$TMP/planeF_lp_$lp.png"
    convert "$TMP/base_$lp.png" "$TMP/planeF_lp_$lp.png" -gravity center -composite "$TMP/legacy_$lp.png"
  else
    convert "$TMP/square.png" -resize "${lp}x${lp}" "$TMP/legacy_$lp.png"
  fi
  cp "$TMP/legacy_$lp.png" "$out/ic_launcher.png"
  cp "$TMP/legacy_$lp.png" "$out/ic_launcher_round.png"

  # --- adaptive icon: photo background + semi-transparent plane foreground ---
  convert "$TMP/square.png" -resize "${ap}x${ap}" "$out/icon_meow_bg.png"
  if [ -f "$plane" ]; then
    convert "$TMP/planeF_$ap.png" "$out/icon_meow_fg.png"
  fi
}

gen_density mipmap-mdpi    48 108
gen_density mipmap-hdpi    72 162
gen_density mipmap-xhdpi   96 216
gen_density mipmap-xxhdpi 144 324
gen_density mipmap-xxxhdpi 192 432

# --- rewrite adaptive icon XMLs to use the new bg/fg ---
write_adaptive_xml() {
  local xml="$RES/mipmap-anydpi-v26/$1"
  local fg_line=""
  if [ -f "$RES/mipmap-mdpi/icon_meow_fg.png" ]; then
    fg_line='    <foreground android:drawable="@mipmap/icon_meow_fg" />'
  else
    fg_line='    <foreground android:drawable="@mipmap/icon_foreground_sa" />'
  fi
  cat > "$xml" <<ADAPTIVE
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/icon_meow_bg" />
$fg_line
    <monochrome android:drawable="@drawable/icon_plane" />
</adaptive-icon>
ADAPTIVE
}
write_adaptive_xml ic_launcher.xml
write_adaptive_xml ic_launcher_round.xml

echo "== generate_icon: done =="

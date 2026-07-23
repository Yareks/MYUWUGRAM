#!/usr/bin/env bash
set -euo pipefail

# Dispatcher: picks how MeowGram is produced.
#   MEOWGRAM_CLIENT=rebrand (default) -> take Nagram's working release APK and
#                        rebrand ONLY the visible app name to "MeowGram" with
#                        apktool (fast ~5 min, no native build, reliable).
#   MEOWGRAM_CLIENT=foss             -> older Telegram-FOSS base (2024), built
#                        from source. Reliable but old UI.
#   MEOWGRAM_CLIENT=nagram           -> build current MD3 Nagram from source.
#                        Blocked on CI-log access to debug; very slow.
#
# The GitHub Actions workflow always runs this file, so switching modes is a
# one-line change to the default below (no workflow edit, which the Arena token
# cannot do).

CLIENT="${MEOWGRAM_CLIENT:-rebrand}"
DIR="$(cd "$(dirname "$0")" && pwd)"

case "$CLIENT" in
  rebrand)
    echo "== MeowGram: rebrand Nagram release APK (default) =="
    exec bash "$DIR/build_rebrand_ci.sh" "$@"
    ;;
  nagram)
    echo "== MeowGram: build current MD3 Nagram from source =="
    exec bash "$DIR/build_nagram_ci.sh" "$@"
    ;;
  foss|*)
    echo "== MeowGram: Telegram-FOSS build from source =="
    exec bash "$DIR/build_foss_ci.sh" "$@"
    ;;
esac

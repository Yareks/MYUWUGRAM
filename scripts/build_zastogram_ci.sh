#!/usr/bin/env bash
set -euo pipefail

# Dispatcher: picks which MeowGram base to build.
#   MEOWGRAM_CLIENT=nagram  (default) -> current MD3 client, official-Telegram-based
#   MEOWGRAM_CLIENT=foss              -> older Telegram-FOSS base (2024), kept as fallback
#
# The GitHub Actions workflow always runs this file, so switching bases is a
# one-line change to the default below (no workflow edit, which the Arena token
# cannot do).

CLIENT="${MEOWGRAM_CLIENT:-nagram}"
DIR="$(cd "$(dirname "$0")" && pwd)"

case "$CLIENT" in
  foss)
    echo "== MeowGram client: Telegram-FOSS (fallback) =="
    exec bash "$DIR/build_foss_ci.sh" "$@"
    ;;
  nagram|*)
    echo "== MeowGram client: Nagram (default) =="
    exec bash "$DIR/build_nagram_ci.sh" "$@"
    ;;
esac

#!/usr/bin/env bash
set -euo pipefail

# Dispatcher: picks which MeowGram base to build.
#   MEOWGRAM_CLIENT=foss   (default) -> older Telegram-FOSS base (2024), builds &
#                        installs reliably; safe default while the Nagram path
#                        is being finished.
#   MEOWGRAM_CLIENT=nagram          -> current MD3 client, official-Telegram-based.
#                        NOTE: the Nagram build is blocked on access to a CI
#                        failure log to debug (Azure log storage + no CI write
#                        permission for a diag-push). Flip the default back to
#                        'nagram' once it builds green.
#
# The GitHub Actions workflow always runs this file, so switching bases is a
# one-line change to the default below (no workflow edit, which the Arena token
# cannot do).

CLIENT="${MEOWGRAM_CLIENT:-foss}"
DIR="$(cd "$(dirname "$0")" && pwd)"

case "$CLIENT" in
  nagram)
    echo "== MeowGram client: Nagram (current MD3) =="
    exec bash "$DIR/build_nagram_ci.sh" "$@"
    ;;
  foss|*)
    echo "== MeowGram client: Telegram-FOSS (default, reliable) =="
    exec bash "$DIR/build_foss_ci.sh" "$@"
    ;;
esac

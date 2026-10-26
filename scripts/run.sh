#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-explorer}"
if [[ $# -gt 0 ]]; then shift; fi
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
case "$MODE" in
  collector)
    bash "$ROOT/scripts/build.sh" collector
    exec "$JAVA" -jar "$ROOT/build/procverse-collector.jar" "$@"
    ;;
  explorer)
    bash "$ROOT/scripts/build.sh" explorer
    FX="${JAVAFX_LIB:-}"
    if [[ -d "$FX/lib" ]]; then FX="$FX/lib"; fi
    exec "$JAVA" --module-path "$FX" --add-modules javafx.controls \
      -cp "$ROOT/build/explorer:$ROOT/build/collector" Universe "$@"
    ;;
  *) echo 'Usage: bash scripts/run.sh [explorer|collector] [collector options]' >&2; exit 2 ;;
esac

#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-explorer}"
case "$MODE" in collector|explorer|test) ;; *) echo 'Usage: bash scripts/build.sh [collector|explorer|test]' >&2; exit 2 ;; esac
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAR="${JAVA_HOME:+$JAVA_HOME/bin/}jar"
command -v "$JAVAC" >/dev/null || { echo 'Install a JDK (17+) or set JAVA_HOME.' >&2; exit 1; }
CORE="$ROOT/build/collector"
mkdir -p "$CORE"
"$JAVAC" --release 17 -encoding UTF-8 -d "$CORE" \
  "$ROOT/src/ProcessInfo.java" "$ROOT/src/ProcessSnapshot.java" \
  "$ROOT/src/ProcessMonitor.java" "$ROOT/src/Collector.java"
"$JAR" --create --file "$ROOT/build/procverse-collector.jar" --main-class Collector -C "$CORE" .
if [[ "$MODE" == collector ]]; then exit 0; fi
if [[ "$MODE" == test ]]; then
  JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
  mkdir -p "$ROOT/build/collector-tests"
  "$JAVAC" --release 17 -encoding UTF-8 -cp "$CORE" -d "$ROOT/build/collector-tests" "$ROOT/tests/CollectorTest.java"
  "$JAVA" -cp "$CORE:$ROOT/build/collector-tests" CollectorTest
  exit 0
fi
FX="${JAVAFX_LIB:-}"
if [[ -d "$FX/lib" ]]; then FX="$FX/lib"; fi
if [[ ! -f "$FX/javafx.controls.jar" ]]; then
  echo 'Set JAVAFX_LIB to your Linux JavaFX SDK lib directory (or /usr/share/openjfx/lib).' >&2
  exit 1
fi
mkdir -p "$ROOT/build/explorer"
"$JAVAC" -encoding UTF-8 --module-path "$FX" --add-modules javafx.controls \
  -cp "$CORE" -d "$ROOT/build/explorer" "$ROOT/src/Universe.java" "$ROOT/src/Main.java"

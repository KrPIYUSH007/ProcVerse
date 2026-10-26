#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
UUID='procverse@krpiyush007'
if [[ "${EUID}" -eq 0 ]]; then echo 'Run as your desktop user, without sudo.' >&2; exit 1; fi
command -v gnome-shell >/dev/null || { echo 'GNOME Shell 46 is required.' >&2; exit 1; }
case "$(gnome-shell --version)" in
  *' 46.'*|*' 46') ;;
  *) echo 'This prototype supports GNOME Shell 46 only.' >&2; exit 1 ;;
esac
command -v java >/dev/null || { echo 'Install Java 17+ on the desktop session PATH first.' >&2; exit 1; }
DATA_HOME="${XDG_DATA_HOME:-$HOME/.local/share}"
[[ "$DATA_HOME" == /* ]] || { echo 'XDG_DATA_HOME must be absolute.' >&2; exit 1; }
DEST="$DATA_HOME/gnome-shell/extensions/$UUID"
[[ ! -L "$DEST" ]] || { echo 'Refusing to replace a symlinked extension directory.' >&2; exit 1; }
bash "$ROOT/scripts/build.sh" collector
# Verify the Java found by the desktop can execute our Java-17 bytecode.
java -jar "$ROOT/build/procverse-collector.jar" --help >/dev/null
if command -v gnome-extensions >/dev/null; then gnome-extensions disable "$UUID" 2>/dev/null || true; fi
mkdir -p "$DEST"
for file in extension.js model.js metadata.json package.json; do
  cp -- "$ROOT/gnome/$UUID/$file" "$DEST/$file"
done
cp -- "$ROOT/build/procverse-collector.jar" "$DEST/collector.jar"
printf 'Installed ProcVerse into %s\n' "$DEST"
printf '\nOn Wayland, log out and log back in to load new or updated extension code.\n'
printf 'Then run: gnome-extensions enable %s\n' "$UUID"
printf 'Disable:  gnome-extensions disable %s\n' "$UUID"

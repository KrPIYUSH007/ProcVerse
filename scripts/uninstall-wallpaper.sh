#!/usr/bin/env bash
set -euo pipefail
UUID='procverse@krpiyush007'
if [[ "${EUID}" -eq 0 ]]; then echo 'Run as your desktop user, without sudo.' >&2; exit 1; fi
DATA_HOME="${XDG_DATA_HOME:-$HOME/.local/share}"
[[ "$DATA_HOME" == /* ]] || { echo 'XDG_DATA_HOME must be absolute.' >&2; exit 1; }
DEST="$DATA_HOME/gnome-shell/extensions/$UUID"
[[ ! -L "$DEST" ]] || { echo 'Refusing to remove a symlinked extension directory.' >&2; exit 1; }
if command -v gnome-extensions >/dev/null; then gnome-extensions disable "$UUID" 2>/dev/null || true; fi
# Remove only the known files belonging to this extension; never recursively delete.
for file in extension.js model.js metadata.json package.json collector.jar; do
  rm -f -- "$DEST/$file"
done
if [[ -d "$DEST" ]]; then rmdir -- "$DEST" 2>/dev/null || true; fi
printf 'ProcVerse wallpaper removed. Log out and back in to clear cached extension code.\n'

# Wallpaper integration: GNOME Shell 46 / Wayland

The repository now includes a GNOME Shell **46** wallpaper extension prototype.
It launches its own Java collector, reads JSON asynchronously, and draws a process
galaxy behind application windows. The existing JavaFX Explorer is unchanged.
The extension has not yet been run in a real GNOME session by the developer;
the next validation is on the Ubuntu GNOME 46 / Wayland VM.

## Install the wallpaper prototype

Run as your normal desktop user, without sudo. A JDK 17+ and the `java` command on
your desktop session's PATH are required. JavaFX is not required for the wallpaper.

```bash
git pull origin main
bash scripts/build.sh test
bash scripts/install-wallpaper.sh
```

Save your work, **log out of GNOME and log back in**, then run:

```bash
gnome-extensions enable procverse@krpiyush007
gnome-extensions info procverse@krpiyush007
```

The same logout/login cycle is needed after reinstalling updated JavaScript:
GNOME caches extension modules. Do not use Alt+F2 `r` to restart a Wayland session.
The install script copies files into your user extension directory, disables any
loaded older version, and does not enable the extension automatically.

### What to expect

- A black-space galaxy behind windows and desktop icons, with state colors,
  category regions, parent stars and orbiting children. It does not capture clicks.
- One collector shared across monitors, sampling every three seconds. There is no
  need to run `collector` separately or keep a terminal open.
- Cairo rendering at at most 10 FPS; at most 80 sampled process stars per category.
  The full snapshot is retained by the collector, but the wallpaper is an ambient
  sampled view, not a complete process inventory. Parents are prioritized when sampling.
- Animation repaints pause during lock/overview and on fullscreen monitors.
  The collector continues sampling; battery and window-occlusion optimizations are
  not yet implemented. CPU/GPU usage must be measured on real GNOME, especially in a VM.
- No process name labels on the desktop. Use the JavaFX Explorer to inspect details.
- Disabling removes drawing actors, stops the timer and collector, and reveals
  your original wallpaper. No background settings are rewritten or service installed.

Disable or remove:

```bash
gnome-extensions disable procverse@krpiyush007
bash scripts/uninstall-wallpaper.sh
```

### If it does not appear

```bash
gnome-extensions info procverse@krpiyush007
java -version
journalctl -b _COMM=gnome-shell | grep -i procverse
```

Send the extension state and relevant ProcVerse log lines. The integration uses
GNOME 46's private background group; other Shell versions are intentionally not
declared compatible. Multiple-monitor placement, desktop icons, wallpaper settings
changes and suspend/resume need testing on the actual desktop.

For isolated development on GNOME 46, an alternative is a nested Wayland session:
`dbus-run-session gnome-shell --nested --wayland`, enabling ProcVerse inside it.

## Extension checks

Node.js is needed only for developer-side JavaScript checks, not for installation:

```bash
node --check gnome/procverse@krpiyush007/extension.js
node tests/wallpaper-model.test.mjs
node --experimental-vm-modules tests/wallpaper-lifecycle.test.mjs
```

Model tests cover invalid feeds, cycles, hierarchy, sampling, bounds, and orbital
positions. Lifecycle tests use mocked GNOME objects to check cleanup, cancellation,
monitor rebuilds, and late callbacks; they do not prove real Shell compatibility.

## Linux development workflow

Install a JDK (17 or newer). The collector needs no JavaFX or graphical session.

```bash
git pull origin main
bash scripts/run.sh collector --once
bash scripts/build.sh test
```

For the Explorer, set `JAVAFX_LIB` to the **Linux** JavaFX SDK's `lib` directory.
Use a JDK compatible with that SDK's version.

```bash
export JAVAFX_LIB="$HOME/path/to/javafx-sdk/lib"
bash scripts/run.sh explorer
```

The launch script recompiles sources before starting. Classes in `src/` are never
used. JavaFX and collector outputs are separate under ignored `build/` directories.
The Windows SDK used for local checks cannot be used on Linux.

## Local process feed

```bash
# Continuous newline-delimited JSON; Ctrl+C stops it.
bash scripts/run.sh collector --interval-ms 3000

# Optional atomic file feed in the current user's runtime directory.
# Run from a normal logged-in desktop session, without sudo.
bash scripts/run.sh collector --output "$XDG_RUNTIME_DIR/procverse-snapshot.json"
```

The parent output directory must already exist. Files are replaced atomically and
are owner-readable/writable on POSIX filesystems. Failed collection leaves the last
snapshot intact and exits nonzero; consumers must check the timestamp for staleness.
Diagnostics go to stderr, so stdout contains only JSON during collection. A process
that exits or cannot be read is skipped and counted. Failure to enumerate `/proc`
is an error rather than a successful empty snapshot.

Schema version 1:

```json
{"schemaVersion":1,"collectedAtEpochMs":1792950000000,"skippedProcesses":0,"processes":[{"pid":"42","ppid":"1","name":"java","state":"S","residentMemoryKiB":2048}]}
```

PIDs are strings, timestamps use Unix epoch milliseconds, and resident memory uses
KiB. No CPU percentage is claimed yet. The feed contains process names, not full
command lines. Everything stays local; there is no HTTP server or network port.

## How it will reach the desktop

1. The existing Explorer and the headless collector share `ProcessMonitor` and
   `ProcessInfo`; `ProcessSnapshot` defines the transport contract.
2. The GNOME Shell 46 extension consumes collector stdout directly and renders an
   ambient galaxy behind application windows. Its renderer is separate from JavaFX.
3. Prototype enable/disable, desktop input behavior, monitor layout, resource usage,
   and cleanup on the actual GNOME 46 Wayland machine before adding autostart.
4. The install/uninstall scripts package the collector with the extension; later
   releases can add preferences and distribution packaging after desktop validation.
5. Later, connect Explorer to the same live feed to avoid duplicate scans when both
   views are open. Today Explorer still samples directly through the shared reader.

Each milestone is compiled and tested, pushed to GitHub, then pulled and tested on
Linux. GNOME integration is a prototype until the real session checks pass.

## Implementation references

- [GNOME extension structure and Wayland testing](https://gjs.guide/extensions/development/creating.html)
- [GNOME async subprocess streams and cancellation](https://gjs.guide/guides/gio/subprocesses.html)
- [GNOME 46 background-group implementation](https://github.com/GNOME/gnome-shell/blob/46.0/js/ui/layout.js)
- [St.DrawingArea repaint and Cairo context](https://gnome.pages.gitlab.gnome.org/gnome-shell/st/class.DrawingArea.html)

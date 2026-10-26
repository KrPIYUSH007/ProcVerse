# Wallpaper integration: GNOME Shell 46 / Wayland

The current milestone adds a headless collector and reliable launch scripts.
It does not yet install a GNOME extension or change the desktop background.
The existing Explorer keeps its current appearance.

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
2. A GNOME Shell 46 extension will consume the local feed and render an ambient
   galaxy behind application windows. Its renderer is separate from JavaFX.
3. Prototype enable/disable, desktop input behavior, monitor layout, resource usage,
   and cleanup on the actual GNOME 46 Wayland machine before adding autostart.
4. Package the collector and extension together with install/uninstall scripts.
5. Later, connect Explorer to the same live feed to avoid duplicate scans when both
   views are open. Today Explorer still samples directly through the shared reader.

Each milestone is compiled and tested, pushed to GitHub, then pulled and tested on
Linux. The extension is the next milestone; it is not bundled in this foundation.

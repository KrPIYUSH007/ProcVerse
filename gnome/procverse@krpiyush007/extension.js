import Gio from 'gi://Gio';
import GLib from 'gi://GLib';
import St from 'gi://St';
import {Extension} from 'resource:///org/gnome/shell/extensions/extension.js';
import * as Main from 'resource:///org/gnome/shell/ui/main.js';
import {CATEGORIES, buildGalaxy, positionsAt} from './model.js';

export default class ProcVerseWallpaper extends Extension {
    enable() {
        this._running = true;
        this._actors = [];
        this._galaxy = null;
        this._error = null;
        this._cancellable = new Gio.Cancellable();
        this._startedAt = GLib.get_monotonic_time();
        try {
            if (!Main.layoutManager._backgroundGroup)
                throw new Error('GNOME 46 background group unavailable');
            this._rebuildMonitors();
            this._monitorSignal = Main.layoutManager.connect('monitors-changed', () => this._rebuildMonitors());
            // One collector per enabled extension, shared by every monitor.
            const java = GLib.find_program_in_path('java');
            if (!java) throw new Error('Java 17+ is required. Install a JDK and log in again.');
            this._process = Gio.Subprocess.new([java, '-Xmx96m', '-jar',
                this.dir.get_child('collector.jar').get_path(), '--interval-ms', '3000'],
            Gio.SubprocessFlags.STDOUT_PIPE);
            this._stream = new Gio.DataInputStream({base_stream: this._process.get_stdout_pipe(), close_base_stream: true});
            this._readNext(this._stream, this._cancellable);
            this._process.wait_async(null, (proc, result) => {
                try {
                    proc.wait_finish(result);
                    if (this._running && this._process === proc) {
                        this._error = 'Collector stopped. Disable and enable ProcVerse to retry.';
                        console.error(`ProcVerse: ${this._error}`);
                        this._queueRepaint();
                    }
                } catch (e) { console.error(`ProcVerse collector wait: ${e.message}`); }
            });
            // Deliberately 10 FPS for a first CPU-drawn wallpaper prototype.
            this._timer = GLib.timeout_add(GLib.PRIORITY_DEFAULT_IDLE, 100, () => {
                if (!this._running) return GLib.SOURCE_REMOVE;
                if (!Main.sessionMode.isLocked && !Main.overview.visible)
                    this._queueRepaint();
                return GLib.SOURCE_CONTINUE;
            });
        } catch (e) {
            this.disable();
            throw e;
        }
    }

    _readNext(stream, cancellable) {
        stream.read_line_async(GLib.PRIORITY_DEFAULT_IDLE, cancellable, (input, result) => {
            try {
                const [line] = input.read_line_finish_utf8(result);
                if (!this._running || cancellable.is_cancelled() || line === null) return;
                if (line.length > 8 * 1024 * 1024) throw new Error('Snapshot too large');
                this._galaxy = buildGalaxy(JSON.parse(line));
                this._error = null;
                this._readNext(stream, cancellable);
            } catch (e) {
                if (cancellable.is_cancelled() || !this._running) return;
                this._error = 'Invalid process feed; disable and enable ProcVerse to retry.';
                console.error(`ProcVerse feed: ${e.message}`);
                this._process?.force_exit();
                this._queueRepaint();
            }
        });
    }

    _rebuildMonitors() {
        for (const actor of this._actors) actor.destroy();
        this._actors = [];
        for (const monitor of Main.layoutManager.monitors) {
            const actor = new St.DrawingArea({reactive: false, can_focus: false,
                width: monitor.width, height: monitor.height, clip_to_allocation: true});
            actor.set_position(monitor.x, monitor.y);
            actor.connect('repaint', area => {
                const cr = area.get_context();
                try {
                    const [width, height] = area.get_surface_size();
                    this._draw(cr, width, height);
                } catch (e) {
                    console.error(`ProcVerse drawing: ${e.message}`);
                } finally { cr.$dispose(); }
            });
            // Above the ordinary wallpaper, below desktop windows (including icons).
            // Private GNOME 46 API: version intentionally pinned in metadata.json.
            global.window_group.insert_child_above(actor, Main.layoutManager._backgroundGroup);
            this._actors.push(actor);
        }
    }

    _queueRepaint() {
        for (let i = 0; i < this._actors.length; i++) {
            if (!global.display.get_monitor_in_fullscreen(i)) this._actors[i].queue_repaint();
        }
    }

    _draw(cr, width, height) {
        cr.setSourceRGB(0, 0, 0);
        cr.paint();
        // Stable decorative stars; no random flicker between repaints.
        cr.setSourceRGBA(0.8, 0.85, 1, 0.2);
        for (let i = 0; i < 180; i++) {
            cr.arc(((i * 7919) % 1009) / 1009 * width, ((i * 3571) % 1013) / 1013 * height, 0.6, 0, Math.PI * 2);
            cr.fill();
        }
        if (!this._galaxy || this._error) {
            cr.setSourceRGBA(0.7, 0.8, 0.9, 0.8);
            cr.setFontSize(14);
            cr.moveTo(30, height - 35);
            cr.showText(this._error ?? 'ProcVerse: waiting for live processes...');
            return;
        }
        const seconds = (GLib.get_monotonic_time() - this._startedAt) / 1000000;
        const groups = this._galaxy.groups;
        const columns = Math.min(3, Math.max(1, groups.length));
        const rows = Math.ceil(groups.length / columns);
        const cellW = width * 0.88 / columns, cellH = height * 0.8 / Math.max(1, rows);
        const radius = Math.min(cellW, cellH) * 0.41;
        groups.forEach((group, index) => {
            const cx = width * 0.06 + (index % columns + 0.5) * cellW;
            const cy = height * 0.1 + (Math.floor(index / columns) + 0.5) * cellH;
            const category = CATEGORIES[group.category];
            cr.setSourceRGBA(...category.color, 0.17);
            cr.setLineWidth(0.7);
            cr.arc(cx, cy, radius, 0, Math.PI * 2);
            cr.stroke();
            cr.setSourceRGBA(...category.color, 0.65);
            cr.setFontSize(11);
            cr.moveTo(cx - radius * 0.6, cy - radius - 12);
            cr.showText(category.name);
            const positions = positionsAt(group, seconds);
            const tracks = new Set();
            for (const node of group.nodes) {
                const [nx, ny] = positions.get(node.pid);
                const x = cx + nx * radius, y = cy + ny * radius;
                if (node.parent) {
                    const key = `${node.parent.pid}:${node.orbitRadius}`;
                    if (!tracks.has(key)) {
                        tracks.add(key);
                        const [px, py] = positions.get(node.parent.pid);
                        cr.setSourceRGBA(0.3, 0.65, 0.8, 0.1);
                        cr.arc(cx + px * radius, cy + py * radius, node.orbitRadius * radius, 0, Math.PI * 2);
                        cr.stroke();
                    }
                }
                const rgb = stateColor(node.state);
                const r = node.children.length ? 4.5 : Math.max(1.1, Math.min(2.8, node.residentMemoryKiB / 40000));
                cr.setSourceRGBA(...rgb, 0.12);
                cr.arc(x, y, r * 2.2, 0, Math.PI * 2);
                cr.fill();
                cr.setSourceRGBA(...rgb, 0.9);
                cr.arc(x, y, r, 0, Math.PI * 2);
                cr.fill();
                if (node.children.length) {
                    cr.setSourceRGBA(1, 0.85, 0.5, 0.6);
                    cr.arc(x, y, r + 2, 0, Math.PI * 2);
                    cr.stroke();
                }
            }
        });
        if (Date.now() - this._galaxy.timestamp > 15000) {
            cr.setSourceRGBA(1, 0.7, 0.3, 0.7);
            cr.setFontSize(12);
            cr.moveTo(30, height - 30);
            cr.showText('ProcVerse: process feed is stale');
        }
    }

    disable() {
        this._running = false;
        if (this._timer) GLib.Source.remove(this._timer);
        this._timer = 0;
        if (this._monitorSignal) Main.layoutManager.disconnect(this._monitorSignal);
        this._monitorSignal = 0;
        this._cancellable?.cancel();
        this._cancellable = null;
        this._process?.force_exit();
        this._process = null;
        if (this._stream) {
            try { this._stream.close(null); } catch (e) { console.error(`ProcVerse stream cleanup: ${e.message}`); }
        }
        this._stream = null;
        for (const actor of this._actors ?? []) actor.destroy();
        this._actors = [];
        this._galaxy = null;
    }
}

function stateColor(state) {
    switch (state) {
        case 'R': return [0.2, 1, 0.2];
        case 'S': return [0, 0.9, 1];
        case 'Z': return [1, 0.2, 0.2];
        case 'T': return [1, 0.6, 0];
        default: return [0.85, 0.9, 1];
    }
}

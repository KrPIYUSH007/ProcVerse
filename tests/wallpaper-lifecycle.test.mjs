// Tests extension ownership with mocked GNOME objects, not a real Shell session.
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
import * as model from '../gnome/procverse@krpiyush007/model.js';

const timers = new Map(), signals = new Map(), processes = [], reads = [], actors = [];
let nextId = 1, disposed = 0;
const context = vm.createContext({console, Date, Math, Set, Map});
const Main = {sessionMode: {isLocked: false}, overview: {visible: false}, layoutManager: {
    _backgroundGroup: {}, monitors: [{x: 0, y: 0, width: 1200, height: 800}],
    connect(name, callback) { const id = nextId++; signals.set(id, callback); return id; },
    disconnect(id) { signals.delete(id); },
}};
const GLib = {PRIORITY_DEFAULT_IDLE: 0, SOURCE_CONTINUE: true, SOURCE_REMOVE: false,
    get_monotonic_time: () => 1000000, find_program_in_path: () => '/usr/bin/java',
    timeout_add(priority, interval, callback) { assert.equal(interval, 100); const id = nextId++; timers.set(id, callback); return id; },
    Source: {remove: id => timers.delete(id)}};
class Cancellable { cancel() { this.cancelled = true; } is_cancelled() { return !!this.cancelled; } }
class Stream {
    read_line_async(priority, cancel, callback) { reads.push({stream: this, cancel, callback}); }
    read_line_finish_utf8(result) { return [result]; }
    close() { this.closed = true; }
}
const Gio = {Cancellable, DataInputStream: Stream, SubprocessFlags: {STDOUT_PIPE: 1}, Subprocess: {
    new(argv) {
        assert.equal(argv[0], '/usr/bin/java');
        assert.ok(argv.includes('--interval-ms'));
        const proc = {get_stdout_pipe: () => ({}), wait_async(cancel, cb) { this.waitCallback = cb; },
            wait_finish() {}, force_exit() { this.stopped = true; }};
        processes.push(proc); return proc;
    },
}};
class DrawingArea {
    constructor(props) { Object.assign(this, props); actors.push(this); this.repaintCount = 0; }
    set_position(x, y) { this.x = x; this.y = y; }
    connect(signal, callback) { this.repaint = callback; }
    destroy() { this.destroyed = true; }
    queue_repaint() { this.repaintCount++; }
    get_surface_size() { return [this.width, this.height]; }
    get_context() { return new Proxy({}, {get: (target, key) => key === '$dispose' ? () => disposed++ : () => {}}); }
}
context.global = {window_group: {insert_child_above() {}}, display: {get_monitor_in_fullscreen: () => false}};
const sources = {
    'gi://Gio': {default: Gio}, 'gi://GLib': {default: GLib}, 'gi://St': {default: {DrawingArea}},
    'resource:///org/gnome/shell/extensions/extension.js': {Extension: class {}},
    'resource:///org/gnome/shell/ui/main.js': Main,
    './model.js': model,
};
const extension = new vm.SourceTextModule(await readFile(new URL('../gnome/procverse@krpiyush007/extension.js', import.meta.url), 'utf8'), {context});
await extension.link(specifier => {
    const exports = sources[specifier];
    assert.ok(exports, `Unexpected import ${specifier}`);
    return new vm.SyntheticModule(Object.keys(exports), function () {
        for (const [key, value] of Object.entries(exports)) this.setExport(key, value);
    }, {context});
});
await extension.evaluate();
const app = new extension.namespace.default();
app.dir = {get_child: () => ({get_path: () => '/extension/collector.jar'})};
app.enable();
assert.equal(processes.length, 1);
assert.equal(timers.size, 1);
assert.equal(signals.size, 1);
assert.equal(actors[0].reactive, false, 'Does not intercept desktop clicks');
const firstRead = reads.shift();
firstRead.callback(firstRead.stream, JSON.stringify({schemaVersion: 1, collectedAtEpochMs: Date.now(), processes: []}));
assert.equal(app._galaxy.total, 0);
actors[0].repaint(actors[0]);
assert.equal(disposed, 1, 'Cairo context disposed after painting');
const tick = [...timers.values()][0];
tick();
assert.equal(actors[0].repaintCount, 1);
Main.sessionMode.isLocked = true;
tick();
assert.equal(actors[0].repaintCount, 1, 'No animation repaint while locked');
Main.sessionMode.isLocked = false;
Main.layoutManager.monitors.push({x: 1200, y: 0, width: 1200, height: 800});
[...signals.values()][0]();
assert.ok(actors[0].destroyed);
assert.equal(app._actors.length, 2);
const oldRead = reads.shift();
const oldProc = processes[0];
app.disable();
assert.equal(timers.size, 0);
assert.equal(signals.size, 0);
assert.ok(oldProc.stopped && oldRead.cancel.is_cancelled());
assert.ok(actors.every(actor => actor.destroyed));
app.enable();
oldRead.callback(oldRead.stream, '{}');
oldProc.waitCallback(oldProc, {});
assert.equal(app._error, null, 'Old callbacks do not mutate a re-enabled extension');
assert.equal(processes.length, 2);
assert.ok(!processes[1].stopped);
app.disable();
assert.equal(timers.size, 0);
assert.equal(signals.size, 0);
assert.ok(processes.every(proc => proc.stopped));
GLib.find_program_in_path = () => null;
assert.throws(() => app.enable(), /Java 17/);
assert.equal(timers.size, 0);
assert.equal(signals.size, 0);
assert.ok(actors.every(actor => actor.destroyed), 'Failed enable leaves no desktop actors');
console.log('Wallpaper lifecycle checks passed (mocked GNOME runtime)');

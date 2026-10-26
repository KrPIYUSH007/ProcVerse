import assert from 'node:assert/strict';
import {buildGalaxy, categoryOf, positionsAt, validateSnapshot} from '../gnome/procverse@krpiyush007/model.js';

const process = (pid, ppid, name, memory = 1000) => ({pid, ppid, name, state: 'S', residentMemoryKiB: memory});
const snapshot = processes => ({schemaVersion: 1, collectedAtEpochMs: Date.now(), processes});
assert.equal(categoryOf('crashpad'), 6);
assert.equal(categoryOf('sh'), 3);
assert.throws(() => validateSnapshot({schemaVersion: 2}), /invalid/);
assert.throws(() => buildGalaxy(snapshot([process('1', '0', 'java'), process('1', '0', 'java')])), /Invalid process/);
assert.throws(() => buildGalaxy(snapshot([{...process('1', '0', 'java'), residentMemoryKiB: -1}])));
assert.deepEqual(buildGalaxy(snapshot([])).groups, []);

const fixture = [process('1', '0', 'systemd'), process('2', '1', 'bash'),
    process('3', '2', 'chrome', 200000), process('4', '3', 'crashpad'),
    process('5', '4', 'helper'), process('6', '1', 'sshd')];
for (let i = 10; i < 250; i++) fixture.push(process(`${i}`, '3', 'chrome', i * 100));
fixture.push(process('900', '901', 'unknown'), process('901', '900', 'unknown'));
const galaxy = buildGalaxy(snapshot(fixture));
assert.equal(galaxy.total, fixture.length);
const browser = galaxy.groups.find(g => g.category === 4);
assert.ok(browser.nodes.length <= 80, 'Per-category render cap');
assert.ok(browser.nodes.some(p => p.pid === '3'), 'Parent retained when sampling');
for (const group of galaxy.groups) {
    const positions = positionsAt(group, 123);
    const later = positionsAt(group, 130);
    assert.equal(positions.size, group.nodes.length, 'Cycles broken and all sampled nodes rendered');
    for (const node of group.nodes) {
        const [x, y] = positions.get(node.pid);
        assert.ok(Number.isFinite(x) && Number.isFinite(y));
        assert.ok(Math.hypot(x, y) <= 1.001, 'Complete family stays inside normalized region');
        if (node.parent) {
            const [px, py] = positions.get(node.parent.pid);
            assert.ok(Math.abs(Math.hypot(x - px, y - py) - node.orbitRadius) < 1e-9);
        } else assert.deepEqual(later.get(node.pid), positions.get(node.pid), 'Stable family anchor');
    }
}
const helpers = buildGalaxy(snapshot(fixture.slice(0, 6))).groups.find(g => g.category === 4);
assert.equal(helpers.nodes.find(n => n.pid === '4').parent.pid, '3', 'Unknown helpers inherit browser family');
assert.equal(helpers.nodes.find(n => n.pid === '5').parent.pid, '4', 'Nested family retained');
console.log('Wallpaper model checks passed');

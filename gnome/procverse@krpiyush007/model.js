// Pure layout and validation: no GNOME imports, so this is testable outside Shell.
export const CATEGORIES = [
    {name: 'System Services', color: [0.4, 0.8, 1], words: ['systemd', 'init', 'dbus', 'udev', 'cron', 'polkit']},
    {name: 'Network', color: [0.3, 1, 0.5], words: ['ssh', 'network', 'dhcp', 'dns', 'avahi', 'nginx']},
    {name: 'Java / Dev', color: [1, 0.7, 0.2], words: ['java', 'gradle', 'python', 'node', 'code']},
    {name: 'User Apps', color: [0.9, 0.4, 0.9], words: ['bash', 'zsh', 'gnome', 'xterm', 'konsole', 'vim', 'nano']},
    {name: 'Browser', color: [1, 0.5, 0.3], words: ['firefox', 'chrome', 'chromium', 'brave', 'electron']},
    {name: 'Kernel', color: [0.5, 0.6, 0.9], words: ['kthread', 'kworker', 'ksoftirq', 'migration', 'watchdog', 'rcu']},
    {name: 'Other', color: [0.6, 0.6, 0.65], words: []},
];

export function categoryOf(name) {
    const lower = name.toLowerCase();
    if (lower === 'sh') return 3;
    const index = CATEGORIES.findIndex(c => c.words.some(word => lower.includes(word)));
    return index < 0 ? 6 : index;
}

export function validateSnapshot(snapshot) {
    if (snapshot?.schemaVersion !== 1 || !Number.isFinite(snapshot.collectedAtEpochMs)
        || !Array.isArray(snapshot.processes) || snapshot.processes.length > 50000)
        throw new Error('Unsupported or invalid ProcVerse snapshot');
    const seen = new Set();
    for (const p of snapshot.processes) {
        if (!p || typeof p.pid !== 'string' || !/^\d+$/.test(p.pid)
            || typeof p.ppid !== 'string' || !/^\d+$/.test(p.ppid)
            || typeof p.name !== 'string' || p.name.length > 256
            || typeof p.state !== 'string' || p.state.length !== 1
            || !Number.isFinite(p.residentMemoryKiB) || p.residentMemoryKiB < 0 || seen.has(p.pid))
            throw new Error('Invalid process in ProcVerse snapshot');
        seen.add(p.pid);
    }
    return snapshot;
}

export function buildGalaxy(snapshot) {
    validateSnapshot(snapshot);
    const all = new Map(snapshot.processes.map(p => [p.pid, {...p, category: categoryOf(p.name)}]));
    // Unclassified helpers inherit a known ancestor's category, with cycle protection.
    for (const p of all.values()) {
        if (p.category !== 6) continue;
        const visited = new Set([p.pid]);
        let ancestor = all.get(p.ppid);
        for (let depth = 0; ancestor && depth < 64 && !visited.has(ancestor.pid); depth++) {
            if (ancestor.category !== 6) { p.category = ancestor.category; break; }
            visited.add(ancestor.pid);
            ancestor = all.get(ancestor.ppid);
        }
    }
    const groups = [];
    for (let category = 0; category < CATEGORIES.length; category++) {
        const candidates = [...all.values()].filter(p => p.category === category);
        if (!candidates.length) continue;
        // Cap each category independently so kernel threads cannot crowd out user apps.
        candidates.sort((a, b) => b.residentMemoryKiB - a.residentMemoryKiB || Number(a.pid) - Number(b.pid));
        const picked = new Map();
        for (const candidate of candidates) {
            const chain = [];
            let p = candidate;
            const visited = new Set();
            while (p && p.category === category && !picked.has(p.pid) && !visited.has(p.pid) && chain.length < 64) {
                chain.push(p);
                visited.add(p.pid);
                p = all.get(p.ppid);
            }
            for (const item of chain.reverse()) {
                if (picked.size < 80) picked.set(item.pid, {...item, children: [], extent: 10});
            }
            if (picked.size >= 80) break;
        }
        const nodes = [...picked.values()].sort((a, b) => Number(a.pid) - Number(b.pid));
        for (const node of nodes) node.parent = picked.get(node.ppid) ?? null;
        for (const node of nodes) {
            const visited = new Set([node.pid]);
            for (let parent = node.parent; parent; parent = parent.parent) {
                if (visited.has(parent.pid)) { node.parent = null; break; }
                visited.add(parent.pid);
            }
        }
        for (const node of nodes) node.parent?.children.push(node);
        const roots = nodes.filter(n => !n.parent);
        const order = [...roots];
        for (let i = 0; i < order.length; i++) order.push(...order[i].children);
        for (const parent of [...order].reverse()) {
            if (!parent.children.length) continue;
            const childExtent = Math.max(...parent.children.map(n => n.extent)) + 8;
            const firstRing = Math.max(30, childExtent / Math.sin(Math.PI / Math.min(8, Math.max(2, parent.children.length))));
            parent.children.forEach((child, i) => {
                const ring = Math.floor(i / 8);
                child.orbitRadius = firstRing + ring * childExtent * 2.2;
                const count = Math.min(8, parent.children.length - ring * 8);
                child.angle = (i % 8) * Math.PI * 2 / count + ring * 2.399963;
                child.speed = 0.12 / Math.sqrt(child.orbitRadius / 30);
                parent.extent = Math.max(parent.extent, child.orbitRadius + child.extent);
            });
        }
        const area = roots.reduce((sum, n) => sum + (n.extent * 2 + 20) ** 2, 0);
        const target = Math.sqrt(area) * 1.3;
        let x = 0, y = 0, rowHeight = 0, width = 0;
        for (const root of roots) {
            const size = root.extent * 2 + 20;
            if (x && x + size > target) { y += rowHeight; x = rowHeight = 0; }
            root.anchorX = x + size / 2;
            root.anchorY = y + size / 2;
            x += size;
            rowHeight = Math.max(rowHeight, size);
            width = Math.max(width, x);
        }
        const height = y + rowHeight;
        const scale = 0.85 / Math.max(1, Math.hypot(width, height) / 2);
        for (const node of nodes) {
            node.extent *= scale;
            if (node.parent) node.orbitRadius *= scale;
            else {
                node.anchorX = (node.anchorX - width / 2) * scale;
                node.anchorY = (node.anchorY - height / 2) * scale;
            }
        }
        groups.push({category, total: candidates.length, nodes: order});
    }
    return {groups, total: snapshot.processes.length, timestamp: snapshot.collectedAtEpochMs};
}

export function positionsAt(group, seconds) {
    const positions = new Map();
    for (const node of group.nodes) {
        if (!node.parent) positions.set(node.pid, [node.anchorX, node.anchorY]);
        else {
            const parent = positions.get(node.parent.pid);
            const angle = node.angle + seconds * node.speed;
            positions.set(node.pid, [parent[0] + Math.cos(angle) * node.orbitRadius,
                parent[1] + Math.sin(angle) * node.orbitRadius]);
        }
    }
    return positions;
}

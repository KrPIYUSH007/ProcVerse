import java.util.ArrayList;
import java.util.List;

/** Headless layout regression checks; no live /proc or JavaFX window needed. */
public class UniverseLayoutTest {
    static ProcessInfo process(String pid, String parent, String name) {
        return new ProcessInfo(pid, parent, name, "S", 1000, 0);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Universe universe = new Universe();
        List<ProcessInfo> processes = new ArrayList<>(List.of(
                process("1", "0", "systemd"),
                process("2", "1", "bash"),
                process("3", "2", "chrome"),
                process("4", "3", "crashpad"),
                process("5", "4", "helper"),
                process("6", "1", "sshd")));
        for (int i = 10; i < 60; i++)
            processes.add(process("" + i, "3", "chrome"));
        for (ProcessInfo p : universe.sortByHierarchy(processes)) universe.addStar3D(p);
        universe.assignOrbits(processes);

        Universe.Star3D browser = universe.starMap.get("3");
        Universe.Star3D helper = universe.starMap.get("4");
        check(browser.cluster == Universe.CLUSTERS[4], "Browser category");
        check(!browser.orbiting, "Browser anchors its category rather than orbiting shell");
        check(helper.cluster == browser.cluster && helper.orbiting, "Helper stays with browser");
        check(universe.starMap.get("5").orbiting, "Nested family orbits");
        check(universe.starMap.get("2").cluster == Universe.CLUSTERS[3], "Shell category");
        check(universe.starMap.get("6").cluster == Universe.CLUSTERS[1], "Network category");
        check(browser.childCount == 51, "All browser children counted");
        check(universe.starMap.get("59").orbiting, "Large families still orbit");
        check(universe.visualRadius(browser) > universe.visualRadius(universe.starMap.get("59")),
                "Parent visually larger than equal-memory leaf");
        check(universe.orbitOrder.indexOf(browser) < universe.orbitOrder.indexOf(helper),
                "Parent animation precedes children");
        double x = browser.x;
        universe.assignOrbits(processes);
        check(browser.x == x, "Refresh preserves family anchor");

        helper.info.ppid = "1";
        universe.assignOrbits(processes);
        check(helper.orbiting && helper.parentPid.equals("1")
                && helper.cluster == Universe.CLUSTERS[0], "Reparenting updates family");
        System.out.println("Universe layout checks passed");
    }
}

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Fixture-based collector checks that run without Linux or JavaFX. */
public class CollectorTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path fixture = Files.createTempDirectory("procverse-test-");
        try {
            Files.createDirectories(fixture.resolve("42"));
            Files.writeString(fixture.resolve("42/status"),
                    "Name:\tbrowser helper\nState:\tS (sleeping)\nPPid:\t1\nVmRSS:\t2048 kB\n");
            Files.createDirectories(fixture.resolve("2"));
            Files.writeString(fixture.resolve("2/status"), "Name:\tkthreadd\nState:\tS (sleeping)\nPPid:\t0\n");
            Files.createDirectories(fixture.resolve("99")); // process exited before status could be read
            Files.createDirectories(fixture.resolve("100"));
            Files.writeString(fixture.resolve("100/status"), "Name:\tbroken\n");
            Files.createDirectories(fixture.resolve("net"));
            ProcessSnapshot snapshot = ProcessMonitor.readSnapshot(fixture);
            check(snapshot.processes.size() == 2, "Only readable complete processes included");
            check(snapshot.skippedProcesses == 2, "Races and malformed entries counted");
            check(snapshot.processes.get(0).pid.equals("2"), "Stable numeric PID order");
            check(snapshot.processes.get(0).memory == 0, "Kernel process without VmRSS supported");
            ProcessInfo helper = snapshot.processes.get(1);
            check(helper.name.equals("browser helper"), "Full process name retained");
            check(helper.memory == 2048 && helper.ppid.equals("1"), "Memory and hierarchy parsed");
            String json = snapshot.toJson();
            check(json.contains("\"schemaVersion\":1") && json.contains("\"residentMemoryKiB\":2048"), "Version and explicit memory unit");
            check(ProcessSnapshot.quote("a\"\\\n\t\u0001").equals("\"a\\\"\\\\\\n\\t\\u0001\""), "JSON escapes controls and quotes");
            Path output = fixture.resolve("snapshot.json");
            Collector.writeSnapshot(output, json);
            check(Files.readString(output).trim().equals(json), "Snapshot published completely");
            Collector.writeSnapshot(output, "{}");
            check(Files.readString(output).trim().equals("{}"), "Existing snapshot replaced");
            if (Files.getFileStore(output).supportsFileAttributeView("posix"))
                check(Files.getPosixFilePermissions(output).equals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")), "Owner-only snapshot file");
            try {
                ProcessMonitor.readSnapshot(fixture.resolve("missing"));
                throw new AssertionError("Unavailable process root must fail");
            } catch (IOException expected) { }
            try {
                Collector.run(new String[]{"--interval-ms", "0"});
                throw new AssertionError("Invalid interval must fail");
            } catch (IllegalArgumentException expected) { }
            System.out.println("Collector checks passed");
        } finally {
            try (var paths = Files.walk(fixture)) {
                for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }
}

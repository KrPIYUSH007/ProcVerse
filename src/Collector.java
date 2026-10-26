import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/** Headless process feed: no JavaFX, network listener, or root privileges. */
public class Collector {
    public static void main(String[] args) {
        try { run(args); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (IOException | IllegalArgumentException e) {
            System.err.println("ProcVerse collector: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(String[] args) throws IOException, InterruptedException {
        boolean once = false;
        long intervalMs = 3000;
        Path output = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help":
                    System.out.println("Collector [--once] [--interval-ms N] [--output FILE]\n"
                            + "Default: JSON snapshots on stdout every 3000 ms.\n"
                            + "--output atomically replaces a local snapshot file instead.");
                    return;
                case "--once": once = true; break;
                case "--interval-ms":
                    if (++i == args.length) throw new IllegalArgumentException("Missing interval");
                    intervalMs = Long.parseLong(args[i]);
                    if (intervalMs < 500 || intervalMs > 60000)
                        throw new IllegalArgumentException("Interval must be between 500 and 60000 ms");
                    break;
                case "--output":
                    if (++i == args.length) throw new IllegalArgumentException("Missing output path");
                    output = Path.of(args[i]);
                    break;
                default: throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        do {
            String json = ProcessMonitor.readSnapshot(Path.of("/proc")).toJson();
            if (output == null) {
                System.out.println(json);
                if (System.out.checkError()) return;
            } else { writeSnapshot(output, json); }
            if (once) return;
            Thread.sleep(intervalMs);
        } while (!Thread.currentThread().isInterrupted());
    }

    static void writeSnapshot(Path destination, String json) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (!Files.isDirectory(parent)) throw new IOException("Output directory does not exist: " + parent);
        Path temporary;
        if (Files.getFileStore(parent).supportsFileAttributeView("posix")) {
            temporary = Files.createTempFile(parent, ".procverse-", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } else { temporary = Files.createTempFile(parent, ".procverse-", ".tmp"); }
        try {
            Files.writeString(temporary, json + "\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Output filesystem must support atomic snapshot replacement", e);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}

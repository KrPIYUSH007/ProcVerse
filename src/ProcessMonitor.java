import java.io.*;
import java.util.*;
import java.nio.file.Path;

public class ProcessMonitor {

    public static List<ProcessInfo> getProcesses() {
        try { return readSnapshot(Path.of("/proc")).processes; }
        catch (IOException e) {
            System.err.println("ProcVerse: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static ProcessSnapshot readSnapshot(Path procRoot) throws IOException {

        List<ProcessInfo> processes = new ArrayList<>();

        File proc = procRoot.toFile();
        int skipped = 0;

        File[] files = proc.listFiles();

        if (files == null) {
            throw new IOException("Cannot enumerate process directory: " + procRoot);
        }

        for (File file : files) {

            // Check if folder name is numeric PID
            if (file.isDirectory() &&
                file.getName().matches("\\d+")) {

                String pid = file.getName();

                File statusFile =
                    procRoot.resolve(pid).resolve("status").toFile();

                String processName = "";
                String processState = "";
                String parentPid = "0";
                int memoryUsage = 0;

                try (
                    BufferedReader br =
                        new BufferedReader(
                            new FileReader(statusFile)
                        )
                ) {

                    String line;

                    while ((line = br.readLine()) != null) {

                        // Process Name
                        if (line.startsWith("Name:")) {

                            String[] parts =
                                line.trim().split("\\s+");

                            if (parts.length >= 2) {
                                processName = line.substring(line.indexOf(':') + 1).trim();
                            }
                        }

                        // Process State
                        if (line.startsWith("State:")) {

                            String[] parts =
                                line.trim().split("\\s+");

                            if (parts.length >= 2) {
                                processState = parts[1];
                            }
                        }

                        // Parent PID
                        if (line.startsWith("PPid:")) {

                            String[] parts =
                                line.trim().split("\\s+");

                            if (parts.length >= 2) {
                                parentPid = parts[1];
                            }
                        }

                        // Memory Usage
                        if (line.startsWith("VmRSS:")) {

                            String[] parts =
                                line.trim().split("\\s+");

                            if (parts.length >= 2) {

                                try {

                                    memoryUsage =
                                        Integer.parseInt(parts[1]);

                                } catch (Exception e) {
                                }
                            }
                        }
                    }

                } catch (Exception e) {

                    skipped++;

                    continue;
                }

                if (processName.isEmpty() || processState.isEmpty()) {
                    skipped++;
                    continue;
                }
                // Add process to list
                processes.add(
                    new ProcessInfo(
                        pid,
                        parentPid,
                        processName,
                        processState,
                        memoryUsage,
                        0
                    )
                );
            }
        }

        processes.sort(Comparator.comparingLong(p -> Long.parseLong(p.pid)));
        return new ProcessSnapshot(System.currentTimeMillis(), processes, skipped);
    }
}

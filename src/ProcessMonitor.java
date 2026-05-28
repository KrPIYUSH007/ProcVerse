import java.io.*;
import java.util.*;

public class ProcessMonitor {

    public static List<ProcessInfo> getProcesses() {

        List<ProcessInfo> processes = new ArrayList<>();

        File proc = new File("/proc");

        File[] files = proc.listFiles();

        if (files == null) {
            return processes;
        }

        for (File file : files) {

            // Check if folder name is numeric PID
            if (file.isDirectory() &&
                file.getName().matches("\\d+")) {

                String pid = file.getName();

                File statusFile =
                    new File("/proc/" + pid + "/status");

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
                                processName = parts[1];
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

                    System.out.println(
                        "Failed process: " + pid
                    );

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

        return processes;
    }
}

import java.io.*;
import java.util.*;



public class Main {
  public static void printTree(
        List<ProcessInfo> processes,
        String parentPid,
        int level
    ) {

        for (ProcessInfo process : processes) {

            if (process.ppid.equals(parentPid)) {

                String indent = "";

                for (int i = 0; i < level; i++) {
                    indent += "   ";
                }

                String color = getColor(process.state);

                System.out.printf(
                    "%s%s└── %s (%s)\033[0m\n",
                    color,
                    indent,
                    process.name,
                    process.pid
                );

                printTree(processes, process.pid, level + 1);
            }
        }
    }
    // ANSI Colors
    public static String getColor(String state) {

        switch (state) {

            case "R":
                return "\033[32m"; // Green

            case "S":
                return "\033[33m"; // Yellow

            case "Z":
                return "\033[31m"; // Red

            case "T":
                return "\033[34m"; // Blue

            default:
                return "\033[37m"; // White
        }
    }

    public static void main(String[] args) throws Exception {

        while (true) {

            List<ProcessInfo> processes = new ArrayList<>();

            // Clear terminal
            System.out.print("\033[H\033[2J");
            System.out.flush();

            File proc = new File("/proc");

            File[] files = proc.listFiles();

            if (files == null) {
                System.out.println("Could not read /proc");
                return;
            }

            for (File file : files) {

                // Check if directory name is numeric
                if (file.isDirectory() && file.getName().matches("\\d+")) {

                    String pid = file.getName();

                    File statusFile = new File("/proc/" + pid + "/status");
                    File statFile = new File("/proc/" + pid + "/stat");

                    String processName = "";
                    String processState = "";
                  String parentPid = "";
		     int memoryUsage = 0;
                    int cpuTime = 0;

                    // Read status file
                    try (BufferedReader br = new BufferedReader(new FileReader(statusFile))) {

                        String line;

                        while ((line = br.readLine()) != null) {
                           if (line.startsWith("PPid:")) {

    String[] parts = line.split("\\s+");

    if (parts.length > 1) {
        parentPid = parts[1];
    }
}      


                            // Process name
                            if (line.startsWith("Name:")) {

                                String[] parts = line.split("\\s+");

                                if (parts.length > 1) {
                                    processName = parts[1];
                                }
                            }

                            // Process state
                            if (line.startsWith("State:")) {

                                String[] parts = line.split("\\s+");

                                if (parts.length > 1) {
                                    processState = parts[1];
                                }
                            }

                            // Memory usage
                            if (line.startsWith("VmRSS:")) {

                                String[] parts = line.split("\\s+");

                                if (parts.length > 1) {

                                    try {
                                        memoryUsage = Integer.parseInt(parts[1]);
                                    } catch (Exception e) {
                                    }
                                }
                            }
                        }

                    } catch (Exception e) {
                        continue;
                    }

                    // Read CPU stats
                    try {

                        BufferedReader br = new BufferedReader(new FileReader(statFile));

                        String statLine = br.readLine();

                        if (statLine != null) {

                            String[] fields = statLine.split("\\s+");

                            if (fields.length > 14) {

                                int utime = Integer.parseInt(fields[13]);
                                int stime = Integer.parseInt(fields[14]);

                                cpuTime = utime + stime;
                            }
                        }

                        br.close();

                    } catch (Exception e) {
                    }

                    // Add process
                    processes.add(
                        new ProcessInfo(
                            pid,
                            parentPid,
                            processName,
                            processState,
                            memoryUsage,
                            cpuTime
                        )
                    );
                }
            }

            // Sort by memory usage
            processes.sort((a, b) -> Integer.compare(b.memory, a.memory));

            // Print processes


                System.out.println("🌌 PROCVERSE PROCESS TREE 🌌\n");

printTree(processes, "0", 0);    

            Thread.sleep(2000);
        }
    }
}

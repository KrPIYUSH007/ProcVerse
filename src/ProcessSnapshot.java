import java.util.List;

/** Versioned local transport, independent of JavaFX. */
public class ProcessSnapshot {
    public final long collectedAtEpochMs;
    public final List<ProcessInfo> processes;
    public final int skippedProcesses;

    public ProcessSnapshot(long timestamp, List<ProcessInfo> processes, int skipped) {
        collectedAtEpochMs = timestamp;
        this.processes = List.copyOf(processes);
        skippedProcesses = skipped;
    }

    public String toJson() {
        StringBuilder out = new StringBuilder();
        out.append("{\"schemaVersion\":1,\"collectedAtEpochMs\":").append(collectedAtEpochMs)
                .append(",\"skippedProcesses\":").append(skippedProcesses).append(",\"processes\":[");
        boolean first = true;
        for (ProcessInfo p : processes) {
            if (!first) out.append(',');
            first = false;
            out.append("{\"pid\":").append(quote(p.pid)).append(",\"ppid\":").append(quote(p.ppid))
                    .append(",\"name\":").append(quote(p.name)).append(",\"state\":").append(quote(p.state))
                    .append(",\"residentMemoryKiB\":").append(p.memory).append('}');
        }
        return out.append("]}").toString();
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20 || Character.isSurrogate(c)) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}

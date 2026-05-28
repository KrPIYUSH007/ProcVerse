public class ProcessInfo {

    public String pid;
    public String ppid;
    public String name;
    public String state;
    public int memory;
    public int cpuTime;

    public ProcessInfo(
        String pid,
        String ppid,
        String name,
        String state,
        int memory,
        int cpuTime
    ) {

        this.pid = pid;
        this.ppid = ppid;
        this.name = name;
        this.state = state;
        this.memory = memory;
        this.cpuTime = cpuTime;
    }
}

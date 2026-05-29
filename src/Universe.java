import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Universe extends Application {

    // -----------------------------------------------
    // Cluster definitions — name, keywords, position
    // -----------------------------------------------
    static class Cluster {
        String   name;
        String[] keywords;
        double   cx, cy;   // center on virtual canvas
        Color    labelColor;

        Cluster(String name, String[] keywords, double cx, double cy, Color labelColor) {
            this.name       = name;
            this.keywords   = keywords;
            this.cx         = cx;
            this.cy         = cy;
            this.labelColor = labelColor;
        }

        boolean matches(String procName) {
            String lower = procName.toLowerCase();
            for (String kw : keywords) {
                if (lower.contains(kw)) return true;
            }
            return false;
        }
    }

    static class StarNode {
        Circle star;
        Text   pidLabel;
        Text   ppidLabel;
        boolean orbiting    = false;
        double  orbitAngle  = 0;
        double  orbitSpeed  = 0;
        double  orbitRadius = 0;
        String  parentPid   = null;

        StarNode(Circle star, Text pidLabel, Text ppidLabel) {
            this.star      = star;
            this.pidLabel  = pidLabel;
            this.ppidLabel = ppidLabel;
        }
    }

    @Override
    public void start(Stage stage) {
        try {
            javafx.geometry.Rectangle2D screen =
                javafx.stage.Screen.getPrimary().getVisualBounds();

            double W  = screen.getWidth();
            double H  = screen.getHeight();
            double VW = W * 3;
            double VH = H * 3;

            // -----------------------------------------------
            // Define clusters — positions spread across canvas
            // -----------------------------------------------
            Cluster[] clusters = {
                new Cluster("⚙ System Services",
                    new String[]{"systemd", "init", "dbus", "udev", "cron",
                                 "rsyslog", "syslog", "polkit", "accounts"},
                    VW * 0.20, VH * 0.25, Color.color(0.4, 0.8, 1.0)),

                new Cluster("🌐 Network Services",
                    new String[]{"ssh", "sshd", "network", "dhcp", "dns",
                                 "avahi", "nm-", "wpa", "curl", "wget", "nginx", "apache"},
                    VW * 0.50, VH * 0.18, Color.color(0.3, 1.0, 0.5)),

                new Cluster("☕ Java Processes",
                    new String[]{"java", "javac", "gradle", "maven", "mvn",
                                 "kotlin", "scala"},
                    VW * 0.80, VH * 0.25, Color.color(1.0, 0.7, 0.2)),

                new Cluster("🖥 User Apps",
                    new String[]{"bash", "sh", "zsh", "fish", "terminal",
                                 "gnome", "xterm", "konsole", "vim", "nano",
                                 "code", "gedit", "python", "node"},
                    VW * 0.25, VH * 0.70, Color.color(0.9, 0.4, 0.9)),

                new Cluster("🌍 Browser",
                    new String[]{"firefox", "chrome", "chromium", "brave",
                                 "opera", "webkit", "electron"},
                    VW * 0.55, VH * 0.65, Color.color(1.0, 0.5, 0.3)),

                new Cluster("🔧 Kernel Threads",
                    new String[]{"kthread", "kworker", "ksoftirq", "migration",
                                 "watchdog", "rcu", "irq"},
                    VW * 0.80, VH * 0.70, Color.color(0.5, 0.5, 0.7)),
            };

            // Fallback cluster for unmatched processes
            Cluster fallback = new Cluster("✦ Other",
                new String[]{},
                VW * 0.50, VH * 0.45, Color.color(0.6, 0.6, 0.6));

            // -----------------------------------------------
            // Tooltip
            // -----------------------------------------------
            Rectangle tooltipBg = new Rectangle();
            tooltipBg.setFill(Color.color(0, 0, 0, 0.85));
            tooltipBg.setArcWidth(6);
            tooltipBg.setArcHeight(6);
            tooltipBg.setVisible(false);

            Text tooltipName  = new Text();
            Text tooltipPid   = new Text();
            Text tooltipPpid  = new Text();
            Text tooltipState = new Text();
            Text tooltipMem   = new Text();

            Font tooltipFont = Font.font("Monospace", 12);
            for (Text t : new Text[]{tooltipName, tooltipPid, tooltipPpid, tooltipState, tooltipMem}) {
                t.setFont(tooltipFont);
                t.setFill(Color.WHITE);
                t.setVisible(false);
            }

            // -----------------------------------------------
            // Scene groups
            // -----------------------------------------------
            Group labelsGroup = new Group(); // cluster name labels
            Group linesGroup  = new Group();
            Group starsGroup  = new Group();
            Group universe    = new Group(labelsGroup, linesGroup, starsGroup);

            Scale scaleTransform = new Scale(1.0, 1.0, 0, 0);
            universe.getTransforms().add(scaleTransform);

            Map<String, StarNode> nodeMap = new HashMap<>();
            Map<String, Line>     lineMap = new HashMap<>();

            // Draw cluster labels
            drawClusterLabels(clusters, fallback, labelsGroup);

            // Load and place processes
            List<ProcessInfo> initial = ProcessMonitor.getProcesses();
            List<ProcessInfo> sorted  = sortByHierarchy(initial);

            for (ProcessInfo p : sorted) {
                addStar(p, starsGroup, nodeMap, clusters, fallback,
                        tooltipBg, tooltipName, tooltipPid, tooltipPpid,
                        tooltipState, tooltipMem, W, H);
            }
            assignOrbits(sorted, nodeMap);
            rebuildLines(sorted, nodeMap, lineMap, linesGroup);

            Pane root = new Pane(universe);
            root.getChildren().addAll(
                tooltipBg, tooltipName, tooltipPid,
                tooltipPpid, tooltipState, tooltipMem
            );

            // Center view on middle of canvas
            universe.setTranslateX(-(VW - W) / 2);
            universe.setTranslateY(-(VH - H) / 2);

            // -----------------------------------------------
            // Animation timer — orbit motion
            // -----------------------------------------------
            final long[] lastTime = {0};
            AnimationTimer orbitTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    try {
                        if (lastTime[0] == 0) { lastTime[0] = now; return; }
                        double dt = (now - lastTime[0]) / 1_000_000_000.0;
                        lastTime[0] = now;

                        for (StarNode sn : nodeMap.values()) {
                            if (!sn.orbiting || sn.parentPid == null) continue;
                            StarNode parent = nodeMap.get(sn.parentPid);
                            if (parent == null) continue;

                            sn.orbitAngle += sn.orbitSpeed * dt;
                            double nx = parent.star.getCenterX()
                                        + Math.cos(sn.orbitAngle) * sn.orbitRadius;
                            double ny = parent.star.getCenterY()
                                        + Math.sin(sn.orbitAngle) * sn.orbitRadius;

                            sn.star.setCenterX(nx);
                            sn.star.setCenterY(ny);

                            double r = sn.star.getRadius();
                            sn.pidLabel .setX(nx - 10); sn.pidLabel .setY(ny + r + 12);
                            sn.ppidLabel.setX(nx - 10); sn.ppidLabel.setY(ny + r + 23);
                        }

                        for (Map.Entry<String, Line> entry : lineMap.entrySet()) {
                            StarNode sn = nodeMap.get(entry.getKey());
                            if (sn == null || sn.parentPid == null) continue;
                            StarNode parent = nodeMap.get(sn.parentPid);
                            if (parent == null) continue;
                            Line line = entry.getValue();
                            line.setStartX(parent.star.getCenterX());
                            line.setStartY(parent.star.getCenterY());
                            line.setEndX(sn.star.getCenterX());
                            line.setEndY(sn.star.getCenterY());
                        }
                    } catch (Exception ex) {
                        System.err.println("AnimationTimer: " + ex.getMessage());
                    }
                }
            };
            orbitTimer.start();

            // -----------------------------------------------
            // Realtime ticker — every 3 seconds
            // -----------------------------------------------
            Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
                try {
                    List<ProcessInfo> current = ProcessMonitor.getProcesses();
                    Map<String, ProcessInfo> currentMap = new HashMap<>();
                    for (ProcessInfo p : current) currentMap.put(p.pid, p);

                    List<String> dead = new ArrayList<>();
                    for (String pid : nodeMap.keySet()) {
                        if (!currentMap.containsKey(pid)) dead.add(pid);
                    }
                    for (String pid : dead) {
                        StarNode sn = nodeMap.remove(pid);
                        starsGroup.getChildren().removeAll(sn.star, sn.pidLabel, sn.ppidLabel);
                    }

                    List<ProcessInfo> newProcs = new ArrayList<>();
                    for (ProcessInfo p : current) {
                        if (!nodeMap.containsKey(p.pid)) newProcs.add(p);
                    }
                    for (ProcessInfo p : sortByHierarchy(newProcs)) {
                        addStar(p, starsGroup, nodeMap, clusters, fallback,
                                tooltipBg, tooltipName, tooltipPid, tooltipPpid,
                                tooltipState, tooltipMem, W, H);
                    }

                    for (ProcessInfo p : current) {
                        StarNode sn = nodeMap.get(p.pid);
                        if (sn == null) continue;
                        sn.star.setRadius(Math.max(3, Math.min(p.memory / 5000.0, 25)));
                        switch (p.state) {
                            case "R": sn.star.setFill(Color.LIME);   break;
                            case "S": sn.star.setFill(Color.CYAN);   break;
                            case "Z": sn.star.setFill(Color.RED);    break;
                            case "T": sn.star.setFill(Color.ORANGE); break;
                            default:  sn.star.setFill(Color.WHITE);
                        }
                    }

                    assignOrbits(current, nodeMap);
                    rebuildLines(current, nodeMap, lineMap, linesGroup);

                } catch (Exception ex) {
                    System.err.println("Ticker: " + ex.getMessage());
                }
            }));
            ticker.setCycleCount(Timeline.INDEFINITE);
            ticker.play();

            // -----------------------------------------------
            // Zoom
            // -----------------------------------------------
            final double ZOOM_FACTOR = 1.15;
            root.setOnScroll(e -> {
                double delta = e.getDeltaY();
                if (delta == 0) delta = -e.getTextDeltaY();
                if (delta == 0) { e.consume(); return; }

                double oldScale = scaleTransform.getX();
                double newScale = (delta < 0)
                    ? Math.min(oldScale * ZOOM_FACTOR, 10.0)
                    : Math.max(oldScale / ZOOM_FACTOR, 0.05);

                double cx = e.getX(), cy = e.getY();
                double lx = (cx - universe.getTranslateX()) / oldScale;
                double ly = (cy - universe.getTranslateY()) / oldScale;

                scaleTransform.setX(newScale);
                scaleTransform.setY(newScale);
                universe.setTranslateX(cx - lx * newScale);
                universe.setTranslateY(cy - ly * newScale);
                e.consume();
            });

            // Pan
            final double[] drag = new double[2];
            root.setOnMousePressed(e -> {
                drag[0] = e.getSceneX() - universe.getTranslateX();
                drag[1] = e.getSceneY() - universe.getTranslateY();
            });
            root.setOnMouseDragged(e -> {
                universe.setTranslateX(e.getSceneX() - drag[0]);
                universe.setTranslateY(e.getSceneY() - drag[1]);
            });

            // Double-click reset
            root.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    scaleTransform.setX(1.0);
                    scaleTransform.setY(1.0);
                    universe.setTranslateX(-(VW - W) / 2);
                    universe.setTranslateY(-(VH - H) / 2);
                }
            });

            Scene scene = new Scene(root, W, H, Color.BLACK);
            stage.setTitle("ProcVerse 🌌  |  Scroll: zoom   Drag: pan   Double-click: reset");
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();

        } catch (Exception ex) {
            System.err.println("Fatal: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // -----------------------------------------------
    // Draw cluster name labels on the canvas
    // -----------------------------------------------
    private void drawClusterLabels(Cluster[] clusters, Cluster fallback, Group labelsGroup) {
        for (Cluster c : clusters) {
            addClusterLabel(c, labelsGroup);
        }
        addClusterLabel(fallback, labelsGroup);
    }

    private void addClusterLabel(Cluster c, Group labelsGroup) {
        // Outer glow ring
        Circle ring = new Circle(c.cx, c.cy, 180);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(c.labelColor.deriveColor(0, 1, 1, 0.08));
        ring.setStrokeWidth(1.5);

        // Cluster name text
        Text label = new Text(c.cx - 80, c.cy - 195, c.name);
        label.setFont(Font.font("Monospace", FontWeight.BOLD, 14));
        label.setFill(c.labelColor.deriveColor(0, 1, 1, 0.6));

        labelsGroup.getChildren().addAll(ring, label);
    }

    // -----------------------------------------------
    // Assign a cluster to a process by name
    // -----------------------------------------------
    private Cluster assignCluster(ProcessInfo p, Cluster[] clusters, Cluster fallback) {
        for (Cluster c : clusters) {
            if (c.matches(p.name)) return c;
        }
        return fallback;
    }

    // -----------------------------------------------
    // Sort processes: parents before children
    // -----------------------------------------------
    private List<ProcessInfo> sortByHierarchy(List<ProcessInfo> processes) {
        Map<String, ProcessInfo> byPid = new HashMap<>();
        for (ProcessInfo p : processes) byPid.put(p.pid, p);

        List<ProcessInfo> sorted  = new ArrayList<>();
        List<ProcessInfo> pending = new ArrayList<>(processes);
        int maxPasses = processes.size() + 1;

        while (!pending.isEmpty() && maxPasses-- > 0) {
            List<ProcessInfo> next = new ArrayList<>();
            for (ProcessInfo p : pending) {
                if (p.ppid.equals("0") || !byPid.containsKey(p.ppid)
                        || sorted.stream().anyMatch(s -> s.pid.equals(p.ppid))) {
                    sorted.add(p);
                } else {
                    next.add(p);
                }
            }
            if (next.size() == pending.size()) {
                sorted.addAll(next);
                break;
            }
            pending = next;
        }
        return sorted;
    }

    // -----------------------------------------------
    // Assign orbit parameters
    // -----------------------------------------------
    private void assignOrbits(List<ProcessInfo> processes, Map<String, StarNode> nodeMap) {
        Map<String, List<String>> childrenOf = new HashMap<>();
        for (ProcessInfo p : processes) {
            if (!p.ppid.equals("0") && nodeMap.containsKey(p.ppid)) {
                childrenOf.computeIfAbsent(p.ppid, k -> new ArrayList<>()).add(p.pid);
            }
        }

        for (Map.Entry<String, List<String>> entry : childrenOf.entrySet()) {
            String       parentPid = entry.getKey();
            List<String> children  = entry.getValue();
            int          count     = children.size();

            if (count > 40) continue;

            double orbitRadius = Math.min(40 + count * 7, 160);

            for (int i = 0; i < count; i++) {
                StarNode sn = nodeMap.get(children.get(i));
                if (sn == null) continue;
                if (!sn.orbiting) {
                    sn.orbiting    = true;
                    sn.parentPid   = parentPid;
                    sn.orbitRadius = orbitRadius;
                    sn.orbitAngle  = (2 * Math.PI / count) * i;
                    sn.orbitSpeed  = 0.5 / Math.sqrt(orbitRadius / 40.0);
                } else {
                    sn.orbitRadius = orbitRadius;
                }
            }
        }
    }

    // -----------------------------------------------
    // Add a star — placed inside its cluster zone
    // -----------------------------------------------
    private void addStar(
            ProcessInfo p,
            Group starsGroup,
            Map<String, StarNode> nodeMap,
            Cluster[] clusters,
            Cluster fallback,
            Rectangle tooltipBg,
            Text tooltipName, Text tooltipPid, Text tooltipPpid,
            Text tooltipState, Text tooltipMem,
            double W, double H) {

        double x, y;
        StarNode parentNode = nodeMap.get(p.ppid);

        if (parentNode != null && !p.ppid.equals("0")) {
            // Place near parent
            double angle = Math.random() * 2 * Math.PI;
            double dist  = 40 + Math.random() * 70;
            x = parentNode.star.getCenterX() + Math.cos(angle) * dist;
            y = parentNode.star.getCenterY() + Math.sin(angle) * dist;
        } else {
            // Place in cluster zone
            Cluster c = assignCluster(p, clusters, fallback);
            double angle = Math.random() * 2 * Math.PI;
            double dist  = Math.random() * 160;
            x = c.cx + Math.cos(angle) * dist;
            y = c.cy + Math.sin(angle) * dist;
        }

        double radius = Math.max(3, Math.min(p.memory / 5000.0, 25));
        Circle star   = new Circle(x, y, radius);

        switch (p.state) {
            case "R": star.setFill(Color.LIME);   break;
            case "S": star.setFill(Color.CYAN);   break;
            case "Z": star.setFill(Color.RED);    break;
            case "T": star.setFill(Color.ORANGE); break;
            default:  star.setFill(Color.WHITE);
        }

        // Labels — hidden until hover
        Text pidLabel  = new Text(x - 10, y + radius + 12, "PID:" + p.pid);
        pidLabel.setFont(Font.font("Monospace", 9));
        pidLabel.setFill(Color.color(1, 1, 1, 0.8));
        pidLabel.setVisible(false);

        Text ppidLabel = new Text(x - 10, y + radius + 23, "PAR:" + p.ppid);
        ppidLabel.setFont(Font.font("Monospace", 9));
        ppidLabel.setFill(Color.color(1, 1, 1, 0.5));
        ppidLabel.setVisible(false);

        star.setOnMouseEntered(e -> {
            pidLabel .setVisible(true);
            ppidLabel.setVisible(true);

            String stateFull;
            switch (p.state) {
                case "R": stateFull = "Running";  break;
                case "S": stateFull = "Sleeping"; break;
                case "Z": stateFull = "Zombie";   break;
                case "T": stateFull = "Stopped";  break;
                default:  stateFull = "Unknown";
            }

            tooltipName .setText("  " + p.name);
            tooltipPid  .setText("  PID   : " + p.pid);
            tooltipPpid .setText("  PPID  : " + p.ppid);
            tooltipState.setText("  State : " + stateFull);
            tooltipMem  .setText("  Mem   : " + p.memory + " kB");

            double tx  = e.getSceneX() + 14;
            double ty  = e.getSceneY() - 10;
            double boxW = 180, boxH = 90;

            if (tx + boxW > W) tx = e.getSceneX() - boxW - 6;
            if (ty + boxH > H) ty = e.getSceneY() - boxH - 6;

            tooltipBg.setX(tx);       tooltipBg.setY(ty);
            tooltipBg.setWidth(boxW); tooltipBg.setHeight(boxH);

            double lh = 16;
            tooltipName .setX(tx); tooltipName .setY(ty + lh);
            tooltipPid  .setX(tx); tooltipPid  .setY(ty + lh * 2);
            tooltipPpid .setX(tx); tooltipPpid .setY(ty + lh * 3);
            tooltipState.setX(tx); tooltipState.setY(ty + lh * 4);
            tooltipMem  .setX(tx); tooltipMem  .setY(ty + lh * 5);

            tooltipBg   .setVisible(true);
            tooltipName .setVisible(true);
            tooltipPid  .setVisible(true);
            tooltipPpid .setVisible(true);
            tooltipState.setVisible(true);
            tooltipMem  .setVisible(true);

            star.setOpacity(0.7);
        });

        star.setOnMouseExited(e -> {
            pidLabel .setVisible(false);
            ppidLabel.setVisible(false);
            tooltipBg   .setVisible(false);
            tooltipName .setVisible(false);
            tooltipPid  .setVisible(false);
            tooltipPpid .setVisible(false);
            tooltipState.setVisible(false);
            tooltipMem  .setVisible(false);
            star.setOpacity(1.0);
        });

        starsGroup.getChildren().addAll(pidLabel, ppidLabel, star);
        nodeMap.put(p.pid, new StarNode(star, pidLabel, ppidLabel));
    }

    // -----------------------------------------------
    // Rebuild connection lines
    // -----------------------------------------------
    private void rebuildLines(
            List<ProcessInfo> processes,
            Map<String, StarNode> nodeMap,
            Map<String, Line> lineMap,
            Group linesGroup) {

        linesGroup.getChildren().clear();
        lineMap.clear();

        for (ProcessInfo p : processes) {
            StarNode child  = nodeMap.get(p.pid);
            StarNode parent = nodeMap.get(p.ppid);
            if (child != null && parent != null) {
                Line line = new Line(
                    parent.star.getCenterX(), parent.star.getCenterY(),
                    child .star.getCenterX(), child .star.getCenterY()
                );
                line.setStroke(Color.color(1, 1, 1, 0.10));
                line.setStrokeWidth(0.5);
                linesGroup.getChildren().add(line);
                lineMap.put(p.pid, line);
            }
        }
    }

    public static void main(String[] args) {
        launch();
    }
}

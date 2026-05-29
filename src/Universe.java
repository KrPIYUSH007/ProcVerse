import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Universe extends Application {

    // -----------------------------------------------
    // Cluster definition
    // -----------------------------------------------
    static class Cluster {
        String   name;
        String[] keywords;
        double   cx3d, cy3d, cz3d; // 3D center
        double[] color; // r,g,b 0-1

        Cluster(String name, String[] keywords,
                double cx, double cy, double cz, double r, double g, double b) {
            this.name     = name;
            this.keywords = keywords;
            this.cx3d = cx; this.cy3d = cy; this.cz3d = cz;
            this.color = new double[]{r, g, b};
        }

        boolean matches(String procName) {
            String lower = procName.toLowerCase();
            for (String kw : keywords) if (lower.contains(kw)) return true;
            return false;
        }
    }

    // -----------------------------------------------
    // Star3D — process in 3D space
    // -----------------------------------------------
    static class Star3D {
        ProcessInfo info;
        double x, y, z;       // 3D world position
        double baseRadius;    // radius at z=0
        String parentPid;

        // Orbit
        boolean orbiting    = false;
        double  orbitAngle  = 0;
        double  orbitSpeed  = 0;
        double  orbitRadius = 0;
        double  orbitTilt   = 0; // tilt of orbit plane

        Star3D(ProcessInfo info, double x, double y, double z) {
            this.info = info;
            this.x = x; this.y = y; this.z = z;
            this.baseRadius = Math.max(2, Math.min(info.memory / 15000.0, 10));
            this.parentPid  = info.ppid;
        }
    }

    // -----------------------------------------------
    // Cluster definitions in 3D space
    // -----------------------------------------------
    static final Cluster[] CLUSTERS = {
        new Cluster("⚙ System Services",
            new String[]{"systemd","init","dbus","udev","cron","rsyslog","polkit"},
            -400, -200, 0,   0.4, 0.8, 1.0),
        new Cluster("🌐 Network",
            new String[]{"ssh","sshd","network","dhcp","dns","avahi","nm-","nginx"},
            200, -300, -100, 0.3, 1.0, 0.5),
        new Cluster("☕ Java / Dev",
            new String[]{"java","javac","gradle","python","node","code"},
            400,  100, 200,  1.0, 0.7, 0.2),
        new Cluster("🖥 User Apps",
            new String[]{"bash","sh","zsh","gnome","xterm","konsole","vim","nano"},
            -300, 200, -200, 0.9, 0.4, 0.9),
        new Cluster("🌍 Browser",
            new String[]{"firefox","chrome","chromium","brave","electron"},
            100,  300, 100,  1.0, 0.5, 0.3),
        new Cluster("🔧 Kernel",
            new String[]{"kthread","kworker","ksoftirq","migration","watchdog","rcu"},
            -100, -100, 300, 0.5, 0.6, 0.9),
    };

    static final Cluster FALLBACK = new Cluster("✦ Other",
        new String[]{}, 0, 0, 0, 0.5, 0.5, 0.5);

    // Camera state
    double camRotX  = 0.3;   // tilt (radians)
    double camRotY  = 0;     // spin (radians)
    double camZoom  = 800;   // perspective focal length
    double camDist  = 0;     // camera forward/back offset (moves into scene)

    // Auto-rotation speed
    double autoRotY = 0.0003;

    // Drag state
    double dragStartX, dragStartY;
    double dragRotX, dragRotY;

    // Star map
    Map<String, Star3D> starMap = new HashMap<>();

    // Canvas for drawing
    Canvas canvas;
    GraphicsContext gc;

    // Tooltip state
    Star3D hoveredStar = null;

    // Screen center
    double CX, CY;

    @Override
    public void start(Stage stage) {
        try {
            javafx.geometry.Rectangle2D screen =
                javafx.stage.Screen.getPrimary().getVisualBounds();

            double W = screen.getWidth();
            double H = screen.getHeight();
            CX = W / 2;
            CY = H / 2;

            // Canvas — we draw everything manually each frame
            canvas = new Canvas(W, H);
            gc     = canvas.getGraphicsContext2D();

            // Tooltip overlay (JavaFX nodes, fixed on screen)
            Rectangle tooltipBg = new Rectangle();
            tooltipBg.setFill(Color.color(0, 0, 0, 0.85));
            tooltipBg.setArcWidth(6); tooltipBg.setArcHeight(6);
            tooltipBg.setVisible(false);

            Text tooltipName  = new Text();
            Text tooltipPid   = new Text();
            Text tooltipPpid  = new Text();
            Text tooltipState = new Text();
            Text tooltipMem   = new Text();

            Font tf = Font.font("Monospace", 12);
            for (Text t : new Text[]{tooltipName,tooltipPid,tooltipPpid,tooltipState,tooltipMem}) {
                t.setFont(tf); t.setFill(Color.WHITE); t.setVisible(false);
            }

            Pane root = new Pane(canvas);
            root.getChildren().addAll(tooltipBg,tooltipName,tooltipPid,tooltipPpid,tooltipState,tooltipMem);

            // Load initial processes
            List<ProcessInfo> initial = ProcessMonitor.getProcesses();
            List<ProcessInfo> sorted  = sortByHierarchy(initial);
            for (ProcessInfo p : sorted) addStar3D(p);
            assignOrbits(sorted);

            // Generate background starfield once
            initBackgroundStars(W, H);

            // -----------------------------------------------
            // Main render + orbit loop
            // -----------------------------------------------
            final long[] lastNano = {0};

            AnimationTimer timer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (lastNano[0] == 0) { lastNano[0] = now; return; }
                    double dt = (now - lastNano[0]) / 1_000_000_000.0;
                    lastNano[0] = now;

                    // Auto-rotate
                    camRotY += autoRotY;

                    // Advance orbits
                    for (Star3D s : starMap.values()) {
                        if (!s.orbiting || s.parentPid == null) continue;
                        Star3D parent = starMap.get(s.parentPid);
                        if (parent == null) continue;

                        s.orbitAngle += s.orbitSpeed * dt;

                        // Orbit in a tilted plane around parent
                        double ox = Math.cos(s.orbitAngle) * s.orbitRadius;
                        double oz = Math.sin(s.orbitAngle) * s.orbitRadius;
                        double oy = oz * Math.sin(s.orbitTilt);
                        oz        = oz * Math.cos(s.orbitTilt);

                        s.x = parent.x + ox;
                        s.y = parent.y + oy;
                        s.z = parent.z + oz;
                    }

                    // Draw frame
                    drawFrame(W, H, hoveredStar,
                              tooltipBg, tooltipName, tooltipPid,
                              tooltipPpid, tooltipState, tooltipMem);
                }
            };
            timer.start();

            // -----------------------------------------------
            // Realtime process update — every 3 seconds
            // -----------------------------------------------
            Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
                try {
                    List<ProcessInfo> current = ProcessMonitor.getProcesses();
                    Map<String, ProcessInfo> currentMap = new HashMap<>();
                    for (ProcessInfo p : current) currentMap.put(p.pid, p);

                    // Remove dead
                    starMap.keySet().removeIf(pid -> !currentMap.containsKey(pid));

                    // Add new
                    List<ProcessInfo> newProcs = new ArrayList<>();
                    for (ProcessInfo p : current)
                        if (!starMap.containsKey(p.pid)) newProcs.add(p);
                    for (ProcessInfo p : sortByHierarchy(newProcs)) addStar3D(p);

                    // Update existing
                    for (ProcessInfo p : current) {
                        Star3D s = starMap.get(p.pid);
                        if (s == null) continue;
                        s.info = p;
                        s.baseRadius = Math.max(2, Math.min(p.memory / 15000.0, 10));
                    }

                    assignOrbits(current);
                } catch (Exception ex) {
                    System.err.println("Ticker: " + ex.getMessage());
                }
            }));
            ticker.setCycleCount(Timeline.INDEFINITE);
            ticker.play();

            // -----------------------------------------------
            // Mouse drag — rotate galaxy
            // -----------------------------------------------
            canvas.setOnMousePressed(e -> {
                dragStartX = e.getX();
                dragStartY = e.getY();
                dragRotX   = camRotX;
                dragRotY   = camRotY;
                autoRotY   = 0; // pause auto-rotate while dragging
            });

            canvas.setOnMouseDragged(e -> {
                double dx = e.getX() - dragStartX;
                double dy = e.getY() - dragStartY;
                camRotY = dragRotY + dx * 0.005;
                camRotX = dragRotX - dy * 0.005;
                // Clamp vertical tilt
                camRotX = Math.max(-1.2, Math.min(1.2, camRotX));
            });

            canvas.setOnMouseReleased(e -> {
                autoRotY = 0.0003; // resume auto-rotate
            });

            // -----------------------------------------------
            // Scroll — fly into/out of the scene
            // -----------------------------------------------
            canvas.setOnScroll(e -> {
                double delta = e.getDeltaY();
                if (delta == 0) delta = -e.getTextDeltaY();
                // Negative delta = scroll up = fly IN (increase camDist)
                camDist -= delta * 1.5;
                // No hard cap — you can fly as deep as you want
                // but don't go past the stars (prevent clipping)
                camDist = Math.max(-camZoom + 100, camDist);
            });

            // -----------------------------------------------
            // Mouse move — hover detection
            // -----------------------------------------------
            canvas.setOnMouseMoved(e -> {
                hoveredStar = pickStar(e.getX(), e.getY());

                if (hoveredStar != null) {
                    ProcessInfo p = hoveredStar.info;
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

                    double tx = e.getX() + 14, ty = e.getY() - 10;
                    double bw = 185, bh = 90;
                    if (tx + bw > W) tx = e.getX() - bw - 6;
                    if (ty + bh > H) ty = e.getY() - bh - 6;

                    tooltipBg.setX(tx); tooltipBg.setY(ty);
                    tooltipBg.setWidth(bw); tooltipBg.setHeight(bh);

                    double lh = 16;
                    tooltipName .setX(tx); tooltipName .setY(ty + lh);
                    tooltipPid  .setX(tx); tooltipPid  .setY(ty + lh*2);
                    tooltipPpid .setX(tx); tooltipPpid .setY(ty + lh*3);
                    tooltipState.setX(tx); tooltipState.setY(ty + lh*4);
                    tooltipMem  .setX(tx); tooltipMem  .setY(ty + lh*5);

                    tooltipBg   .setVisible(true);
                    tooltipName .setVisible(true);
                    tooltipPid  .setVisible(true);
                    tooltipPpid .setVisible(true);
                    tooltipState.setVisible(true);
                    tooltipMem  .setVisible(true);
                } else {
                    tooltipBg   .setVisible(false);
                    tooltipName .setVisible(false);
                    tooltipPid  .setVisible(false);
                    tooltipPpid .setVisible(false);
                    tooltipState.setVisible(false);
                    tooltipMem  .setVisible(false);
                }
            });

            // Double-click — reset camera
            canvas.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    camRotX  = 0.3;
                    camRotY  = 0;
                    camZoom  = 800;
                    camDist  = 0;
                    autoRotY = 0.0003;
                }
            });

            Scene scene = new Scene(root, W, H, Color.BLACK);
            stage.setTitle("ProcVerse 🌌  |  Drag: rotate   Scroll: zoom   Double-click: reset");
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();

        } catch (Exception ex) {
            System.err.println("Fatal: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // -----------------------------------------------
    // Project 3D point to 2D screen
    // Returns [screenX, screenY, scale] or null if behind camera
    // -----------------------------------------------
    double[] project(double wx, double wy, double wz) {
        // Rotate around Y axis
        double cosY = Math.cos(camRotY), sinY = Math.sin(camRotY);
        double rx = wx * cosY + wz * sinY;
        double rz = -wx * sinY + wz * cosY;

        // Rotate around X axis
        double cosX = Math.cos(camRotX), sinX = Math.sin(camRotX);
        double ry = wy * cosX - rz * sinX;
        rz         = wy * sinX + rz * cosX;

        // Move camera forward (camDist pushes into the scene)
        double depth = rz + camZoom - camDist;
        if (depth < 50) return null;

        double scale = camZoom / depth;
        double sx = CX + rx * scale;
        double sy = CY + ry * scale;

        return new double[]{sx, sy, scale};
    }

    // Background starfield — generated once, drawn every frame
    double[] bgStarX, bgStarY, bgStarSize, bgStarOpacity;

    void initBackgroundStars(double W, double H) {
        int count = 1800;
        bgStarX       = new double[count];
        bgStarY       = new double[count];
        bgStarSize    = new double[count];
        bgStarOpacity = new double[count];

        for (int i = 0; i < count; i++) {
            bgStarX[i]       = Math.random() * W;
            bgStarY[i]       = Math.random() * H;
            // Mix of tiny pinpoints and slightly larger stars
            bgStarSize[i]    = Math.random() < 0.85 ? Math.random() * 1.2 : 1.5 + Math.random() * 1.5;
            bgStarOpacity[i] = 0.15 + Math.random() * 0.65;
        }
    }

    void drawBackgroundStars() {
        for (int i = 0; i < bgStarX.length; i++) {
            double s = bgStarSize[i];
            double o = bgStarOpacity[i];
            // Slightly warm/cool tint variation
            double tint = (i % 3 == 0) ? 0.85 : 1.0;
            gc.setFill(Color.color(tint, tint, 1.0, o));
            gc.fillOval(bgStarX[i] - s/2, bgStarY[i] - s/2, s, s);
        }
    }
    void drawFrame(double W, double H, Star3D hovered,
                   Rectangle tooltipBg,
                   Text tooltipName, Text tooltipPid, Text tooltipPpid,
                   Text tooltipState, Text tooltipMem) {

        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, W, H);

        // Background starfield
        drawBackgroundStars();

        // Collect projected stars, sort by depth (far first)
        List<double[]> projected = new ArrayList<>(); // [sx,sy,scale,starIndex]

        List<Star3D> stars = new ArrayList<>(starMap.values());

        for (int i = 0; i < stars.size(); i++) {
            Star3D s = stars.get(i);
            double[] p = project(s.x, s.y, s.z);
            if (p == null) continue;
            projected.add(new double[]{p[0], p[1], p[2], i});
        }

        // Sort far to near (painter's algorithm)
        projected.sort((a, b) -> Double.compare(a[2], b[2]));

        // Draw connection lines first — ALL parent-child relationships
        for (Star3D s : stars) {
            if (s.parentPid == null) continue;
            Star3D parent = starMap.get(s.parentPid);
            if (parent == null) continue;

            double[] sp = project(s.x, s.y, s.z);
            double[] pp = project(parent.x, parent.y, parent.z);
            if (sp == null || pp == null) continue;

            // Line brightness based on average depth of both endpoints
            double avgScale = (sp[2] + pp[2]) / 2.0;
            double opacity  = Math.max(0.05, Math.min(0.35, avgScale * 0.4));

            // Color lines by cluster — use child star's state color tinted
            double[] rgb = stateColor(s.info.state);
            gc.setStroke(Color.color(rgb[0] * 0.6, rgb[1] * 0.6, rgb[2] * 0.6, opacity));
            gc.setLineWidth(Math.max(0.3, avgScale * 0.6));
            gc.strokeLine(pp[0], pp[1], sp[0], sp[1]);
        }

        // Draw cluster labels (fixed HUD positions)
        drawClusterHUD(W, H);

        // Draw stars near to far
        for (double[] proj : projected) {
            int i = (int) proj[3];
            if (i >= stars.size()) continue;
            Star3D s  = stars.get(i);
            double sx = proj[0], sy = proj[1], scale = proj[2];

            double r = Math.max(1.5, s.baseRadius * scale);

            // Depth-based brightness
            double brightness = Math.min(1.0, scale * 1.2);

            // Base color from state
            double[] rgb = stateColor(s.info.state);

            // Glow effect — outer halo
            double glowR = r * 2.2;
            gc.setFill(Color.color(
                rgb[0] * brightness * 0.3,
                rgb[1] * brightness * 0.3,
                rgb[2] * brightness * 0.3,
                0.25
            ));
            gc.fillOval(sx - glowR, sy - glowR, glowR * 2, glowR * 2);

            // Star core
            gc.setFill(Color.color(
                rgb[0] * brightness,
                rgb[1] * brightness,
                rgb[2] * brightness
            ));
            gc.fillOval(sx - r, sy - r, r * 2, r * 2);

            // Highlight hovered star
            if (s == hovered) {
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(1.5);
                gc.strokeOval(sx - r - 2, sy - r - 2, (r + 2) * 2, (r + 2) * 2);
            }
        }
    }

    // -----------------------------------------------
    // Draw cluster name labels as HUD overlay
    // -----------------------------------------------
    void drawClusterHUD(double W, double H) {
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 11));

        for (Cluster c : CLUSTERS) {
            double[] p = project(c.cx3d, c.cy3d, c.cz3d);
            if (p == null) continue;

            double scale = p[2];
            double opacity = Math.min(0.8, scale * 0.9);

            gc.setFill(Color.color(c.color[0], c.color[1], c.color[2], opacity));
            gc.fillText(c.name, p[0] - 50, p[1] - (180 * scale) - 5);

            // Cluster ring
            double ringR = 180 * scale;
            gc.setStroke(Color.color(c.color[0], c.color[1], c.color[2], opacity * 0.2));
            gc.setLineWidth(1.0);
            gc.strokeOval(p[0] - ringR, p[1] - ringR, ringR * 2, ringR * 2);
        }
    }

    // -----------------------------------------------
    // Pick star under mouse cursor
    // -----------------------------------------------
    Star3D pickStar(double mx, double my) {
        Star3D closest = null;
        double minDist = 20; // px threshold

        for (Star3D s : starMap.values()) {
            double[] p = project(s.x, s.y, s.z);
            if (p == null) continue;

            double r    = Math.max(1.5, s.baseRadius * p[2]);
            double dist = Math.hypot(mx - p[0], my - p[1]);

            if (dist < Math.max(r + 4, minDist)) {
                minDist = dist;
                closest = s;
            }
        }
        return closest;
    }

    // -----------------------------------------------
    // State → RGB color
    // -----------------------------------------------
    double[] stateColor(String state) {
        switch (state) {
            case "R": return new double[]{0.2, 1.0, 0.2}; // lime
            case "S": return new double[]{0.0, 0.9, 1.0}; // cyan
            case "Z": return new double[]{1.0, 0.2, 0.2}; // red
            case "T": return new double[]{1.0, 0.6, 0.0}; // orange
            default:  return new double[]{1.0, 1.0, 1.0}; // white
        }
    }

    // -----------------------------------------------
    // Add a star in 3D space
    // -----------------------------------------------
    void addStar3D(ProcessInfo p) {
        Star3D parentStar = starMap.get(p.ppid);
        double x, y, z;

        if (parentStar != null && !p.ppid.equals("0")) {
            // Spawn near parent
            double angle1 = Math.random() * 2 * Math.PI;
            double angle2 = Math.random() * 2 * Math.PI;
            double dist   = 40 + Math.random() * 80;
            x = parentStar.x + Math.cos(angle1) * Math.cos(angle2) * dist;
            y = parentStar.y + Math.sin(angle2) * dist * 0.5;
            z = parentStar.z + Math.sin(angle1) * Math.cos(angle2) * dist;
        } else {
            // Place in cluster zone
            Cluster c = assignCluster(p);
            double angle1 = Math.random() * 2 * Math.PI;
            double angle2 = (Math.random() - 0.5) * Math.PI;
            double dist   = Math.cbrt(Math.random()) * 180;
            x = c.cx3d + Math.cos(angle1) * Math.cos(angle2) * dist;
            y = c.cy3d + Math.sin(angle2) * dist;
            z = c.cz3d + Math.sin(angle1) * Math.cos(angle2) * dist;
        }

        starMap.put(p.pid, new Star3D(p, x, y, z));
    }

    // -----------------------------------------------
    // Assign cluster by process name
    // -----------------------------------------------
    Cluster assignCluster(ProcessInfo p) {
        for (Cluster c : CLUSTERS) if (c.matches(p.name)) return c;
        return FALLBACK;
    }

    // -----------------------------------------------
    // Assign orbit parameters
    // -----------------------------------------------
    void assignOrbits(List<ProcessInfo> processes) {
        Map<String, List<String>> childrenOf = new HashMap<>();
        for (ProcessInfo p : processes) {
            if (!p.ppid.equals("0") && starMap.containsKey(p.ppid))
                childrenOf.computeIfAbsent(p.ppid, k -> new ArrayList<>()).add(p.pid);
        }

        for (Map.Entry<String, List<String>> entry : childrenOf.entrySet()) {
            String       parentPid = entry.getKey();
            List<String> children  = entry.getValue();
            int          count     = children.size();
            if (count > 40) continue;

            double orbitRadius = Math.min(40 + count * 6, 150);

            for (int i = 0; i < count; i++) {
                Star3D sn = starMap.get(children.get(i));
                if (sn == null) continue;
                if (!sn.orbiting) {
                    sn.orbiting    = true;
                    sn.parentPid   = parentPid;
                    sn.orbitRadius = orbitRadius;
                    sn.orbitAngle  = (2 * Math.PI / count) * i;
                    sn.orbitSpeed  = 0.4 / Math.sqrt(orbitRadius / 40.0);
                    sn.orbitTilt   = (Math.random() - 0.5) * 0.8; // random tilt
                } else {
                    sn.orbitRadius = orbitRadius;
                }
            }
        }
    }

    // -----------------------------------------------
    // Sort processes: parents before children
    // -----------------------------------------------
    List<ProcessInfo> sortByHierarchy(List<ProcessInfo> processes) {
        Map<String, ProcessInfo> byPid = new HashMap<>();
        for (ProcessInfo p : processes) byPid.put(p.pid, p);

        List<ProcessInfo> sorted  = new ArrayList<>();
        List<ProcessInfo> pending = new ArrayList<>(processes);

        int maxPasses = processes.size() + 1;
        while (!pending.isEmpty() && maxPasses-- > 0) {
            List<ProcessInfo> next = new ArrayList<>();
            for (ProcessInfo p : pending) {
                if (p.ppid.equals("0") || !byPid.containsKey(p.ppid)
                        || sorted.stream().anyMatch(s -> s.pid.equals(p.ppid)))
                    sorted.add(p);
                else
                    next.add(p);
            }
            if (next.size() == pending.size()) { sorted.addAll(next); break; }
            pending = next;
        }
        return sorted;
    }

    public static void main(String[] args) {
        launch();
    }
}

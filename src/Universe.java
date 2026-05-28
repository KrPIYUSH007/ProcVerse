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
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Universe extends Application {

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

            // Tooltip
            Rectangle tooltipBg = new Rectangle();
            tooltipBg.setFill(Color.color(0, 0, 0, 0.80));
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

            // Two separate groups: lines behind, stars in front
            Group linesGroup = new Group();
            Group starsGroup = new Group();

            // Outer group holds both, gets zoom/pan transforms
            Group universe = new Group(linesGroup, starsGroup);
            Scale scaleTransform = new Scale(1.0, 1.0, 0, 0);
            universe.getTransforms().add(scaleTransform);

            Map<String, StarNode> nodeMap = new HashMap<>();
            Map<String, Line>     lineMap = new HashMap<>();

            // Initial load
            List<ProcessInfo> initial = ProcessMonitor.getProcesses();
            for (ProcessInfo p : initial) {
                addStar(p, starsGroup, nodeMap, VW, VH,
                        tooltipBg, tooltipName, tooltipPid, tooltipPpid,
                        tooltipState, tooltipMem, W, H);
            }
            assignOrbits(initial, nodeMap);
            rebuildLines(initial, nodeMap, lineMap, linesGroup);

            Pane root = new Pane(universe);
            root.getChildren().addAll(
                tooltipBg, tooltipName, tooltipPid,
                tooltipPpid, tooltipState, tooltipMem
            );

            universe.setTranslateX(-(VW - W) / 2);
            universe.setTranslateY(-(VH - H) / 2);

            // Animation timer — orbit motion
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

                        // Update line endpoints
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
                        System.err.println("AnimationTimer error: " + ex.getMessage());
                    }
                }
            };
            orbitTimer.start();

            // Realtime update every 3 seconds
            Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
                try {
                    List<ProcessInfo> current = ProcessMonitor.getProcesses();

                    Map<String, ProcessInfo> currentMap = new HashMap<>();
                    for (ProcessInfo p : current) currentMap.put(p.pid, p);

                    // Remove dead
                    List<String> dead = new ArrayList<>();
                    for (String pid : nodeMap.keySet()) {
                        if (!currentMap.containsKey(pid)) dead.add(pid);
                    }
                    for (String pid : dead) {
                        StarNode sn = nodeMap.remove(pid);
                        starsGroup.getChildren().removeAll(sn.star, sn.pidLabel, sn.ppidLabel);
                    }

                    // Add new
                    for (ProcessInfo p : current) {
                        if (!nodeMap.containsKey(p.pid)) {
                            addStar(p, starsGroup, nodeMap, VW, VH,
                                    tooltipBg, tooltipName, tooltipPid, tooltipPpid,
                                    tooltipState, tooltipMem, W, H);
                        }
                    }

                    // Update existing
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
                    System.err.println("Ticker error: " + ex.getMessage());
                }
            }));
            ticker.setCycleCount(Timeline.INDEFINITE);
            ticker.play();

            // Zoom
            final double ZOOM_FACTOR = 1.15;
            final double MIN_SCALE   = 0.05;
            final double MAX_SCALE   = 10.0;

            root.setOnScroll(e -> {
                double delta = e.getDeltaY();
                if (delta == 0) delta = -e.getTextDeltaY();
                if (delta == 0) { e.consume(); return; }

                double oldScale = scaleTransform.getX();
                double newScale = (delta < 0)
                    ? Math.min(oldScale * ZOOM_FACTOR, MAX_SCALE)
                    : Math.max(oldScale / ZOOM_FACTOR, MIN_SCALE);

                double cursorX = e.getX();
                double cursorY = e.getY();
                double localX  = (cursorX - universe.getTranslateX()) / oldScale;
                double localY  = (cursorY - universe.getTranslateY()) / oldScale;

                scaleTransform.setX(newScale);
                scaleTransform.setY(newScale);
                universe.setTranslateX(cursorX - localX * newScale);
                universe.setTranslateY(cursorY - localY * newScale);
                e.consume();
            });

            // Pan
            final double[] dragStart = new double[2];
            root.setOnMousePressed(e -> {
                dragStart[0] = e.getSceneX() - universe.getTranslateX();
                dragStart[1] = e.getSceneY() - universe.getTranslateY();
            });
            root.setOnMouseDragged(e -> {
                universe.setTranslateX(e.getSceneX() - dragStart[0]);
                universe.setTranslateY(e.getSceneY() - dragStart[1]);
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
            System.err.println("Fatal start() error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

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

            if (count > 50) continue;

            double orbitRadius = Math.min(35 + count * 6, 150);

            for (int i = 0; i < count; i++) {
                StarNode sn = nodeMap.get(children.get(i));
                if (sn == null) continue;

                if (!sn.orbiting) {
                    sn.orbiting    = true;
                    sn.parentPid   = parentPid;
                    sn.orbitRadius = orbitRadius;
                    sn.orbitAngle  = (2 * Math.PI / count) * i;
                    sn.orbitSpeed  = 0.6 / Math.sqrt(orbitRadius / 35.0);
                } else {
                    sn.orbitRadius = orbitRadius;
                }
            }
        }
    }

    private void addStar(
            ProcessInfo p,
            Group starsGroup,
            Map<String, StarNode> nodeMap,
            double VW, double VH,
            Rectangle tooltipBg,
            Text tooltipName, Text tooltipPid, Text tooltipPpid,
            Text tooltipState, Text tooltipMem,
            double W, double H) {

        double x, y;
        StarNode parentNode = nodeMap.get(p.ppid);
        if (parentNode != null && !p.ppid.equals("0")) {
            double angle = Math.random() * 2 * Math.PI;
            double dist  = 60 + Math.random() * 90;
            x = parentNode.star.getCenterX() + Math.cos(angle) * dist;
            y = parentNode.star.getCenterY() + Math.sin(angle) * dist;
            x = Math.max(10, Math.min(VW - 10, x));
            y = Math.max(10, Math.min(VH - 10, y));
        } else {
            x = Math.random() * (VW - 20) + 10;
            y = Math.random() * (VH - 20) + 10;
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

        star.setOnMouseEntered(e -> {
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
            tooltipBg   .setVisible(false);
            tooltipName .setVisible(false);
            tooltipPid  .setVisible(false);
            tooltipPpid .setVisible(false);
            tooltipState.setVisible(false);
            tooltipMem  .setVisible(false);
            star.setOpacity(1.0);
        });

        Text pidLabel  = new Text(x - 10, y + radius + 12, "PID:" + p.pid);
        pidLabel.setFont(Font.font("Monospace", 9));
        pidLabel.setFill(Color.color(1, 1, 1, 0.6));

        Text ppidLabel = new Text(x - 10, y + radius + 23, "PAR:" + p.ppid);
        ppidLabel.setFont(Font.font("Monospace", 9));
        ppidLabel.setFill(Color.color(1, 1, 1, 0.35));

        starsGroup.getChildren().addAll(pidLabel, ppidLabel, star);
        nodeMap.put(p.pid, new StarNode(star, pidLabel, ppidLabel));
    }

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
                line.setStroke(Color.color(1, 1, 1, 0.15));
                line.setStrokeWidth(0.6);
                linesGroup.getChildren().add(line);
                lineMap.put(p.pid, line);
            }
        }
    }

    public static void main(String[] args) {
        launch();
    }
}

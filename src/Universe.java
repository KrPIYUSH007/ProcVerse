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
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Universe extends Application {

    @Override
    public void start(Stage stage) {

        // Use screen dimensions
        javafx.geometry.Rectangle2D screen =
            javafx.stage.Screen.getPrimary().getVisualBounds();

        double W = screen.getWidth();
        double H = screen.getHeight();

        // Virtual canvas is 3x the screen so zooming out reveals more stars
        double VW = W * 3;
        double VH = H * 3;

        // --- Tooltip overlay (lives outside the zoomable group) ---
        Rectangle tooltipBg = new Rectangle();
        tooltipBg.setFill(Color.color(0, 0, 0, 0.75));
        tooltipBg.setArcWidth(6);
        tooltipBg.setArcHeight(6);
        tooltipBg.setVisible(false);

        Text tooltipName  = new Text();
        Text tooltipPid   = new Text();
        Text tooltipState = new Text();
        Text tooltipMem   = new Text();

        Font tooltipFont = Font.font("Monospace", 12);
        for (Text t : new Text[]{tooltipName, tooltipPid, tooltipState, tooltipMem}) {
            t.setFont(tooltipFont);
            t.setFill(Color.WHITE);
            t.setVisible(false);
        }

        // Get real Linux processes
        List<ProcessInfo> processes = ProcessMonitor.getProcesses();

        // --- Zoomable/pannable group ---
        Group universe = new Group();

        // --- Pass 1: create all stars, store by PID ---
        Map<String, Circle> starMap = new HashMap<>();

        for (ProcessInfo process : processes) {

            double x = Math.random() * (VW - 20) + 10;
            double y = Math.random() * (VH - 20) + 10;

            double radius = Math.max(3, Math.min(process.memory / 5000.0, 25));

            Circle star = new Circle(x, y, radius);

            switch (process.state) {
                case "R": star.setFill(Color.LIME);   break;
                case "S": star.setFill(Color.CYAN);   break;
                case "Z": star.setFill(Color.RED);    break;
                case "T": star.setFill(Color.ORANGE); break;
                default:  star.setFill(Color.WHITE);
            }

            // Hover: show tooltip
            star.setOnMouseEntered(e -> {

                String stateFull;
                switch (process.state) {
                    case "R": stateFull = "Running";  break;
                    case "S": stateFull = "Sleeping"; break;
                    case "Z": stateFull = "Zombie";   break;
                    case "T": stateFull = "Stopped";  break;
                    default:  stateFull = "Unknown";
                }

                tooltipName .setText("  " + process.name);
                tooltipPid  .setText("  PID   : " + process.pid);
                tooltipState.setText("  State : " + stateFull);
                tooltipMem  .setText("  Mem   : " + process.memory + " kB");

                double tx = e.getSceneX() + 14;
                double ty = e.getSceneY() - 10;
                double boxW = 160;
                double boxH = 72;

                if (tx + boxW > W) tx = e.getSceneX() - boxW - 6;
                if (ty + boxH > H) ty = e.getSceneY() - boxH - 6;

                tooltipBg.setX(tx);       tooltipBg.setY(ty);
                tooltipBg.setWidth(boxW); tooltipBg.setHeight(boxH);

                double lh = 16;
                tooltipName .setX(tx); tooltipName .setY(ty + lh);
                tooltipPid  .setX(tx); tooltipPid  .setY(ty + lh * 2);
                tooltipState.setX(tx); tooltipState.setY(ty + lh * 3);
                tooltipMem  .setX(tx); tooltipMem  .setY(ty + lh * 4);

                tooltipBg   .setVisible(true);
                tooltipName .setVisible(true);
                tooltipPid  .setVisible(true);
                tooltipState.setVisible(true);
                tooltipMem  .setVisible(true);

                star.setOpacity(0.7);
            });

            star.setOnMouseExited(e -> {
                tooltipBg   .setVisible(false);
                tooltipName .setVisible(false);
                tooltipPid  .setVisible(false);
                tooltipState.setVisible(false);
                tooltipMem  .setVisible(false);

                star.setOpacity(1.0);
            });

            starMap.put(process.pid, star);
        }

        // --- Pass 2: draw connection lines ---
        for (ProcessInfo process : processes) {

            Circle child  = starMap.get(process.pid);
            Circle parent = starMap.get(process.ppid);

            if (child != null && parent != null) {

                Line line = new Line(
                    parent.getCenterX(), parent.getCenterY(),
                    child .getCenterX(), child .getCenterY()
                );

                line.setStroke(Color.color(1, 1, 1, 0.15));
                line.setStrokeWidth(0.6);

                universe.getChildren().add(line);
            }
        }

        // --- Pass 3: stars on top of lines ---
        universe.getChildren().addAll(starMap.values());

        // --- Root pane: universe group + tooltip overlay ---
        Pane root = new Pane(universe);
        root.getChildren().addAll(tooltipBg, tooltipName, tooltipPid, tooltipState, tooltipMem);

        // -----------------------------------------------
        // Zoom with scroll wheel
        // -----------------------------------------------
        final double ZOOM_FACTOR = 1.12;
        final double MIN_SCALE   = 0.1;
        final double MAX_SCALE   = 8.0;

        root.setOnScroll(e -> {

            double oldScale = universe.getScaleX();
            double newScale;

            if (e.getDeltaY() > 0) {
                newScale = Math.min(oldScale * ZOOM_FACTOR, MAX_SCALE);
            } else {
                newScale = Math.max(oldScale / ZOOM_FACTOR, MIN_SCALE);
            }

            // Pivot zoom around the mouse cursor
            // Convert mouse scene coords into the group's local space
            double pivotX = e.getX();
            double pivotY = e.getY();

            double scaleDelta = newScale / oldScale;

            universe.setScaleX(newScale);
            universe.setScaleY(newScale);

            // Adjust translate so the point under the cursor stays fixed
            universe.setTranslateX(pivotX - scaleDelta * (pivotX - universe.getTranslateX()));
            universe.setTranslateY(pivotY - scaleDelta * (pivotY - universe.getTranslateY()));

            e.consume();
        });

        // -----------------------------------------------
        // Pan with click-and-drag
        // -----------------------------------------------
        final double[] dragStart = new double[2];

        root.setOnMousePressed(e -> {
            dragStart[0] = e.getSceneX() - universe.getTranslateX();
            dragStart[1] = e.getSceneY() - universe.getTranslateY();
        });

        root.setOnMouseDragged(e -> {
            universe.setTranslateX(e.getSceneX() - dragStart[0]);
            universe.setTranslateY(e.getSceneY() - dragStart[1]);
        });

        // -----------------------------------------------
        // Double-click to reset view
        // -----------------------------------------------
        root.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                universe.setScaleX(1.0);
                universe.setScaleY(1.0);
                universe.setTranslateX(-(VW - W) / 2);
                universe.setTranslateY(-(VH - H) / 2);
            }
        });

        // Start centered: shift the universe so its center aligns with screen center
        universe.setTranslateX(-(VW - W) / 2);
        universe.setTranslateY(-(VH - H) / 2);

        Scene scene = new Scene(root, W, H, Color.BLACK);

        stage.setTitle("ProcVerse 🌌  |  Scroll: zoom   Drag: pan   Double-click: reset");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}

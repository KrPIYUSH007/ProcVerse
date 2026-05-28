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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Universe extends Application {

    @Override
    public void start(Stage stage) {

        // Screen dimensions
        javafx.geometry.Rectangle2D screen =
            javafx.stage.Screen.getPrimary().getVisualBounds();

        double W = screen.getWidth();
        double H = screen.getHeight();

        // Virtual canvas 3x screen so zooming out reveals more stars
        double VW = W * 3;
        double VH = H * 3;

        // -----------------------------------------------
        // Tooltip — fixed on screen, outside zoomable group
        // -----------------------------------------------
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

        // -----------------------------------------------
        // Load processes
        // -----------------------------------------------
        List<ProcessInfo> processes = ProcessMonitor.getProcesses();

        // -----------------------------------------------
        // Zoomable / pannable group
        // -----------------------------------------------
        Group universe = new Group();

        // Use an explicit Scale transform so we control the pivot point
        Scale scaleTransform = new Scale(1.0, 1.0, 0, 0);
        universe.getTransforms().add(scaleTransform);

        // -----------------------------------------------
        // Pass 1 — build stars
        // -----------------------------------------------
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

            // Hover — show tooltip
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
                tooltipPpid .setText("  PPID  : " + process.ppid);
                tooltipState.setText("  State : " + stateFull);
                tooltipMem  .setText("  Mem   : " + process.memory + " kB");

                double tx  = e.getSceneX() + 14;
                double ty  = e.getSceneY() - 10;
                double boxW = 180;
                double boxH = 90;

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

            starMap.put(process.pid, star);

            // PID label beneath the star
            Text pidLabel = new Text(x - 10, y + radius + 12, "PID:" + process.pid);
            pidLabel.setFont(Font.font("Monospace", 9));
            pidLabel.setFill(Color.color(1, 1, 1, 0.6));
            universe.getChildren().add(pidLabel);

            // PPID label beneath the PID label
            Text ppidLabel = new Text(x - 10, y + radius + 23, "PAR:" + process.ppid);
            ppidLabel.setFont(Font.font("Monospace", 9));
            ppidLabel.setFill(Color.color(1, 1, 1, 0.35));
            universe.getChildren().add(ppidLabel);
        }

        // -----------------------------------------------
        // Pass 2 — connection lines
        // -----------------------------------------------
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

        // -----------------------------------------------
        // Pass 3 — stars on top of lines
        // -----------------------------------------------
        universe.getChildren().addAll(starMap.values());

        // Root pane
        Pane root = new Pane(universe);
        root.getChildren().addAll(
            tooltipBg, tooltipName, tooltipPid,
            tooltipPpid, tooltipState, tooltipMem
        );

        // Start centered on the virtual canvas
        universe.setTranslateX(-(VW - W) / 2);
        universe.setTranslateY(-(VH - H) / 2);

        // -----------------------------------------------
        // Zoom — scroll wheel, pivot at cursor
        //
        // On Linux with a physical mouse wheel:
        //   scroll UP   → deltaY is NEGATIVE → zoom IN
        //   scroll DOWN → deltaY is POSITIVE → zoom OUT
        // -----------------------------------------------
        final double ZOOM_FACTOR = 1.15;
        final double MIN_SCALE   = 0.05;
        final double MAX_SCALE   = 10.0;

        root.setOnScroll(e -> {

            // Determine zoom direction
            // Try deltaY first; fall back to textDeltaY for touchpad drivers
            double delta = e.getDeltaY();
            if (delta == 0) delta = -e.getTextDeltaY();
            if (delta == 0) { e.consume(); return; }

            double oldScale = scaleTransform.getX();
            double newScale;

            // Negative delta = scroll up = zoom IN
            if (delta < 0) {
                newScale = Math.min(oldScale * ZOOM_FACTOR, MAX_SCALE);
            } else {
                newScale = Math.max(oldScale / ZOOM_FACTOR, MIN_SCALE);
            }

            // Cursor in scene coords
            double cursorX = e.getX();
            double cursorY = e.getY();

            // Convert cursor to universe local space (undo translate + old scale)
            double localX = (cursorX - universe.getTranslateX()) / oldScale;
            double localY = (cursorY - universe.getTranslateY()) / oldScale;

            // Apply new scale (pivot is 0,0 of the group)
            scaleTransform.setX(newScale);
            scaleTransform.setY(newScale);

            // Adjust translate so the point under the cursor stays fixed
            universe.setTranslateX(cursorX - localX * newScale);
            universe.setTranslateY(cursorY - localY * newScale);

            e.consume();
        });

        // -----------------------------------------------
        // Pan — click and drag
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
        // Double-click — reset view
        // -----------------------------------------------
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
    }

    public static void main(String[] args) {
        launch();
    }
}

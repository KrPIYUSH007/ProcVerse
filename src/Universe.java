import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.Font;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.util.List;

public class Universe extends Application {

    @Override
    public void start(Stage stage) {

        Pane root = new Pane();

        // Get real Linux processes
        List<ProcessInfo> processes =
            ProcessMonitor.getProcesses();

        // --- Tooltip overlay ---
        // Background box
        Rectangle tooltipBg = new Rectangle();
        tooltipBg.setFill(Color.color(0, 0, 0, 0.75));
        tooltipBg.setArcWidth(6);
        tooltipBg.setArcHeight(6);
        tooltipBg.setVisible(false);

        // Label lines
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

        // Create stars for processes
        for (ProcessInfo process : processes) {

            double x = Math.random() * 800;
            double y = Math.random() * 600;

            // Star size based on memory usage
            double radius =
                Math.max(
                    3,
                    Math.min(process.memory / 5000.0, 25)
                );

            Circle star = new Circle(x, y, radius);

            // Color based on process state
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

                // Position tooltip near cursor, keep it inside window
                double tx = e.getSceneX() + 14;
                double ty = e.getSceneY() - 10;

                double boxW = 160;
                double boxH = 72;

                if (tx + boxW > 800) tx = e.getSceneX() - boxW - 6;
                if (ty + boxH > 600) ty = e.getSceneY() - boxH - 6;

                tooltipBg.setX(tx);
                tooltipBg.setY(ty);
                tooltipBg.setWidth(boxW);
                tooltipBg.setHeight(boxH);

                double lineH = 16;
                tooltipName .setX(tx); tooltipName .setY(ty + lineH);
                tooltipPid  .setX(tx); tooltipPid  .setY(ty + lineH * 2);
                tooltipState.setX(tx); tooltipState.setY(ty + lineH * 3);
                tooltipMem  .setX(tx); tooltipMem  .setY(ty + lineH * 4);

                tooltipBg   .setVisible(true);
                tooltipName .setVisible(true);
                tooltipPid  .setVisible(true);
                tooltipState.setVisible(true);
                tooltipMem  .setVisible(true);

                // Highlight hovered star
                star.setOpacity(0.7);
            });

            // Hover exit: hide tooltip
            star.setOnMouseExited(e -> {
                tooltipBg   .setVisible(false);
                tooltipName .setVisible(false);
                tooltipPid  .setVisible(false);
                tooltipState.setVisible(false);
                tooltipMem  .setVisible(false);

                star.setOpacity(1.0);
            });

            root.getChildren().add(star);
        }

        // Add tooltip nodes last so they render on top of stars
        root.getChildren().addAll(tooltipBg, tooltipName, tooltipPid, tooltipState, tooltipMem);

        Scene scene = new Scene(root, 800, 600, Color.BLACK);

        stage.setTitle("ProcVerse 🌌");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}

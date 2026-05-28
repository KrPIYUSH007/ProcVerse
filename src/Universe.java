import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.util.List;

public class Universe extends Application {

    @Override
    public void start(Stage stage) {

        Pane root = new Pane();

        // Get real Linux processes
        List<ProcessInfo> processes =
            ProcessMonitor.getProcesses();

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

            Circle star =
                new Circle(x, y, radius);

            // Color based on process state
            switch (process.state) {

                case "R":
                    star.setFill(Color.LIME);
                    break;

                case "S":
                    star.setFill(Color.CYAN);
                    break;

                case "Z":
                    star.setFill(Color.RED);
                    break;

                case "T":
                    star.setFill(Color.ORANGE);
                    break;

                default:
                    star.setFill(Color.WHITE);
            }

            root.getChildren().add(star);
        }

        Scene scene =
            new Scene(root, 800, 600, Color.BLACK);

        stage.setTitle("ProcVerse 🌌");

        stage.setScene(scene);

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}

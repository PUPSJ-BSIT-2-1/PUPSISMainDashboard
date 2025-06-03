/**
 * Test class for PUPSIS application.
 * This class initializes the JavaFX application and loads the main dashboard.
 * It serves as a test entry point for the application.
 */

package com.example.pupsis_main_dashboard;

import com.example.pupsis_main_dashboard.utilities.StageAndSceneUtils;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

public class TestStudentDB extends Application {
    private static final Logger logger = LoggerFactory.getLogger(TestStudentDB.class);

    @Override
    public void start(Stage stage) {
        StageAndSceneUtils utility = new StageAndSceneUtils();

        try {
            Stage initializedStage = utility.loadStage(
                    "fxml/StudentDashboard.fxml",
                    "PUPSIS",
                    Objects.requireNonNull(getClass().getResource("/com/example/pupsis_main_dashboard/Images/PUPSJ Logo.png")).toExternalForm(),
                    StageAndSceneUtils.WindowSize.MEDIUM
            );

            // Apply theme to the scene
            PUPSIS.applyGlobalTheme(initializedStage.getScene());

            initializedStage.show();
        } catch (IOException e) {
            logger.error("Error initializing stage", e);
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
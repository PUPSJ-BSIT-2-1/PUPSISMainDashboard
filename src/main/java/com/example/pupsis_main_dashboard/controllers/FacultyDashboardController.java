package com.example.pupsis_main_dashboard.controllers;

//import com.example.pupsis_main_dashboard.utility.ControllerUtils;

import com.example.pupsis_main_dashboard.utilities.DBConnection;
import com.example.pupsis_main_dashboard.utilities.SessionData;
import com.example.pupsis_main_dashboard.utilities.RememberMeHandler;
import com.example.pupsis_main_dashboard.utilities.StageAndSceneUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class FacultyDashboardController {

    @FXML private HBox homeHBox;
    @FXML private HBox subjectsHBox;
    @FXML private HBox gradesHBox;
    @FXML private HBox schoolCalendarHBox;
    @FXML private HBox scheduleHBox;
    @FXML private HBox settingsHBox;
    @FXML private HBox aboutHBox;
    @FXML private HBox logoutHBox;
    @FXML private Label studentNameLabel;
    @FXML private Label studentIdLabel;
    @FXML private Label departmentLabel;
    @FXML private ScrollPane contentPane;
    @FXML private Node fade1;
    @FXML private Node fade2;

    private final StageAndSceneUtils stageUtils = new StageAndSceneUtils();
    private final Logger logger = LoggerFactory.getLogger(FacultyDashboardController.class);
    private final Map<String, Parent> contentCache = new HashMap<>();
    private String formattedName;
    
    // FXML paths as constants
    private static final String HOME_FXML = "/com/example/pupsis_main_dashboard/fxml/FacultyHomeContent.fxml";
    private static final String GRADES_FXML = "/com/example/pupsis_main_dashboard/fxml/GradingModule.fxml";
    private static final String CALENDAR_FXML = "/com/example/pupsis_main_dashboard/fxml/SchoolCalendar.fxml";
    private static final String SETTINGS_FXML = "/com/example/pupsis_main_dashboard/fxml/SettingsContent.fxml";
    private static final String ABOUT_FXML = "/com/example/pupsis_main_dashboard/fxml/AboutContent.fxml";
    private static final String SCHEDULE_FXML = "/com/example/pupsis_main_dashboard/fxml/FacultyRoomAssignment.fxml";

    // Initialize the controller and set up the dashboard
    @FXML public void initialize() {
        homeHBox.getStyleClass().add("selected");

        String identifier = RememberMeHandler.getCurrentUserFacultyNumber();
        if (identifier != null && !identifier.isEmpty()) {
            // Get faculty info from the database
            loadFacultyInfo(identifier);
        } else {
            // Handle case when no user is logged in
            studentNameLabel.setText("User not logged in");
            studentIdLabel.setText("");
            departmentLabel.setText("");
        }
        
        // Initialize fade1 as fully transparent and fade2 as visible
        fade1.setOpacity(0);
        fade2.setOpacity(1);
        
        // Setup scroll pane fade effects
        setupScrollPaneFadeEffects();

        // Preload and cache all FXML content that may be accessed from the sidebar
        preloadAllContent();
    }
    
    // Set up scroll pane fade effects based on scroll position
    private void setupScrollPaneFadeEffects() {
        contentPane.vvalueProperty().addListener((_, _, newVal) -> {
            double vvalue = newVal.doubleValue();
            
            // Show/hide top fade based on scroll position
            // If scroll value is not 0, show fade1
            fade1.setOpacity(vvalue > 0 ? 1 : 0);
            
            // Show/hide bottom fade based on scroll position
            // If at bottom, hide fade2, otherwise show as long as we've scrolled
            if (Math.abs(vvalue - 1.0) < 0.001) { // Check if vvalue is at the bottom
                fade2.setOpacity(0);
            } else {
                fade2.setOpacity(1); // Always visible unless at the very bottom
            }
        });
    }
    
    // Preload and cache all FXML content
    private void preloadAllContent() {
        // Load and cache Home content first (already shown)
        loadHomeContent();
        
        // Preload and cache other content
        preloadFxmlContent(GRADES_FXML);
        preloadFxmlContent(CALENDAR_FXML);
        preloadFxmlContent(SETTINGS_FXML);
        preloadFxmlContent(ABOUT_FXML);
        preloadFxmlContent(SCHEDULE_FXML);
    }
    
    // Preload and cache a specific FXML file
    private void preloadFxmlContent(String fxmlPath) {
        try {
            if (!contentCache.containsKey(fxmlPath)) {
                var resource = getClass().getResource(fxmlPath);
                if (resource != null) {
                    Parent content = FXMLLoader.load(resource);
                    contentCache.put(fxmlPath, content);
                } else {
                    System.err.println("Resource not found: " + fxmlPath);
                }
            }
        } catch (IOException e) {
            System.err.println("Error preloading content: " + fxmlPath);
            logger.error("Error preloading content: {}", fxmlPath, e);
        }
    }
    
    // Load faculty information from a database
    private void loadFacultyInfo(String identifier) {
        // Get and display faculty name and ID
        getFacultyData(identifier);
    }
    
    // Load faculty data from the database
    private void getFacultyData(String identifier) {
        try (Connection connection = DBConnection.getConnection()) {
            // Query directly by faculty_number
            String query = "SELECT faculty_number, firstname, lastname, department FROM faculty WHERE faculty_number = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, identifier);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    updateFacultyUI(rs);
                    return;
                }
            }
            
            // If we get here, faculty not found
            Platform.runLater(() -> {
                studentNameLabel.setText("Unknown Faculty");
                studentIdLabel.setText("ID not found");
                departmentLabel.setText("Department not found");
            });
            
        } catch (SQLException e) {
            // Handle database error
            Platform.runLater(() -> {
                studentNameLabel.setText("Error loading data");
                studentIdLabel.setText("");
                departmentLabel.setText("");
            });
        }
    }
    
    private void updateFacultyUI(ResultSet rs) throws SQLException {
        String facultyId = rs.getString("faculty_number");
        String firstName = rs.getString("firstname");
        String lastName = rs.getString("lastname");
        String department = rs.getString("department");
        SessionData.getInstance().setFacultyId(facultyId);
        
        formattedName = formatFacultyName(firstName, lastName);

        Platform.runLater(() -> {
            // Set the faculty ID first to ensure it's available
            studentNameLabel.setText(formattedName);
            studentIdLabel.setText(facultyId);
            departmentLabel.setText(department != null ? department : "Department not set");
        });
    }
    
    // Format the faculty name as "LastName, FirstName"
    private String formatFacultyName(String firstName, String lastName) {
        StringBuilder formattedName = new StringBuilder();
        
        // Add last name
        if (lastName != null && !lastName.trim().isEmpty()) {
            formattedName.append(lastName.trim());
            formattedName.append(", ");
        }
        
        // Add first name
        if (firstName != null && !firstName.trim().isEmpty()) {
            formattedName.append(firstName.trim());
        }
        
        return formattedName.toString().trim();
    }

    // Handle sidebar item clicks and load the corresponding content
    @FXML public void handleSidebarItemClick(MouseEvent event) {
        HBox clickedHBox = (HBox) event.getSource();
        clearAllSelections();
        clickedHBox.getStyleClass().add("selected");

        if (clickedHBox == settingsHBox) {
            loadContent(SETTINGS_FXML);
        } else if (clickedHBox == homeHBox) {
            loadContent(HOME_FXML);
        } else {
            contentPane.setContent(null);
            String fxmlPath = getFxmlPathFromHBox(clickedHBox);

            if (fxmlPath != null) {
                loadContent(fxmlPath);
            }
        }
    }
    
    // Get FXML path based on clicked HBox
    private String getFxmlPathFromHBox(HBox clickedHBox) {
        return switch (clickedHBox.getId()) {
            case "subjectsHBox" -> null;
            case "gradesHBox" -> GRADES_FXML;
            case "scheduleHBox" -> SCHEDULE_FXML;
            case "schoolCalendarHBox" -> CALENDAR_FXML;
            case "aboutHBox" -> ABOUT_FXML;
            case "settingsHBox" -> SETTINGS_FXML;
            default -> HOME_FXML;
        };
    }

    public void loadContent(String fxmlPath) {
        try {
            Parent content = contentCache.get(fxmlPath);
            if (content == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
                content = loader.load();

                if (fxmlPath.equals(HOME_FXML)) {
                    FacultyHomeContentController facultyHomeContentController = loader.getController();
                    facultyHomeContentController.setFacultyDashboardController(this, formattedName);
                }

                // Set faculty ID in SessionData when loading grading module
                if (fxmlPath.equals(GRADES_FXML)) {
                    String facultyId = SessionData.getInstance().getFacultyId(); // ← Better
                    SessionData.getInstance().setStudentId(facultyId); // if needed
                }

                if (fxmlPath.equals(SCHEDULE_FXML)) {
                    String facultyId = SessionData.getInstance().getFacultyId();
                    SessionData.getInstance().setFacultyId(facultyId); // redundant unless needed again
                }
                contentCache.put(fxmlPath, content);
                addLayoutChangeListener(content);
            }
            contentPane.setContent(content);
            resetScrollPosition();
        } catch (IOException e) {
            contentPane.setContent(new Label("Error loading content"));
        }
    }
    
    // Add layout change listener to content
    private void addLayoutChangeListener(Parent content) {
        content.layoutBoundsProperty().addListener((_, _, newVal) -> {
            if (newVal.getHeight() > 0) {
                Platform.runLater(() -> {
                    contentPane.setVvalue(0);
                    contentPane.layout();
                });
            }
        });
    }
    
    // Reset scroll position to top
    private void resetScrollPosition() {
        Platform.runLater(() -> {
            contentPane.setVvalue(0);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> contentPane.setVvalue(0));
                }
            }, 100); // 100ms delay for final layout
        });
    }

    // Load the home content into the ScrollPane
    private void loadHomeContent() {
        loadContent(HOME_FXML);
    }

    // Handle the logout button click event
    @FXML public void handleLogoutButton(MouseEvent ignoredEvent) throws IOException {
        contentCache.clear();
        StageAndSceneUtils.clearCache();
        if (logoutHBox.getScene() != null && logoutHBox.getScene().getWindow() != null) {
            Stage currentStage = (Stage) logoutHBox.getScene().getWindow();
            stageUtils.loadStage(currentStage, "fxml/FacultyLogin.fxml", StageAndSceneUtils.WindowSize.MEDIUM);
        }
    }

    public void handleQuickActionClicks(String fxmlPath) {
        if (fxmlPath.equals(SCHEDULE_FXML)) {
            clearAllSelections();
            scheduleHBox.getStyleClass().add("selected");
        }

        if (fxmlPath.equals(GRADES_FXML)) {
            clearAllSelections();
            schoolCalendarHBox.getStyleClass().add("selected");
        }
    }

    // Clear all selections from the sidebar items
    private void clearAllSelections() {
        homeHBox.getStyleClass().remove("selected");
        subjectsHBox.getStyleClass().remove("selected");
        gradesHBox.getStyleClass().remove("selected");
        scheduleHBox.getStyleClass().remove("selected");
        schoolCalendarHBox.getStyleClass().remove("selected");
        settingsHBox.getStyleClass().remove("selected");
        aboutHBox.getStyleClass().remove("selected");
        logoutHBox.getStyleClass().remove("selected");
    }
}
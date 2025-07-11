package com.sisjuan.controllers;

import com.sisjuan.SISJUAN;
import com.sisjuan.utilities.DBConnection;
import com.sisjuan.utilities.RememberMeHandler;
import com.sisjuan.utilities.SessionData;
import com.sisjuan.utilities.StageAndSceneUtils;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.prefs.Preferences;

public class AdminDashboardController {

    @FXML private HBox homeHBox;
    @FXML private HBox settingsHBox;
    @FXML private HBox aboutHBox;
    @FXML private HBox logoutHBox;
    @FXML private HBox paymentInfoHBox;
    @FXML private HBox facultyHBox;
    @FXML private HBox subjectsHBox;
    @FXML private HBox scheduleHBox;
    @FXML private HBox calendarHBox;
    @FXML private HBox studentsHBox;
    @FXML private Label studentNameLabel;
    @FXML private Label studentIdLabel;
    @FXML private Label departmentLabel;
    @FXML private ScrollPane contentPane;
    @FXML private Node fade1;
    @FXML private Node fade2;
    @FXML private StackPane loadingOverlay;
    @FXML private HBox refreshHBox;

    private static final String USER_TYPE = "ADMIN";
    private static final String HOME_FXML = "/com/sisjuan/fxml/AdminHomeContent.fxml";
    private static final String PAYMENT_INFO_FXML = "/com/sisjuan/fxml/AdminStudentPaymentManagement.fxml";
    private static final String SUBJECTS_FXML = "/com/sisjuan/fxml/AdminSubjectManagement.fxml";
    private static final String FACULTY_FXML = "/com/sisjuan/fxml/AdminFacultyPreview.fxml";
    private static final String SCHEDULE_FXML = "/com/sisjuan/fxml/AdminClassSchedule.fxml";
    private static final String CALENDAR_FXML = "/com/sisjuan/fxml/GeneralCalendar.fxml";
    private static final String SETTINGS_FXML = "/com/sisjuan/fxml/GeneralSettings.fxml";
    private static final String ABOUT_FXML = "/com/sisjuan/fxml/GeneralAbouts.fxml";
    private static final String STUDENT_MANAGEMENT_FXML = "/com/sisjuan/fxml/AdminStudentManagement.fxml";

    private final StageAndSceneUtils stageUtils = new StageAndSceneUtils();
    private final Logger logger = LoggerFactory.getLogger(AdminDashboardController.class);
    private final Map<String, Parent> contentCache = new HashMap<>();
    
    // Initialize the controller and set up the dashboard
    @FXML public void initialize() {
        long t0 = System.currentTimeMillis();
        logger.info("[PERF] AdminDashboardController.initialize() started");

        // FXML loaded before this point
        long t1 = System.currentTimeMillis();
        logger.info("[PERF] FXML loaded in {} ms", (t1 - t0));

        logger.info("[PERF] Starting controller setup...");
        homeHBox.getStyleClass().add("selected");

        Preferences prefs = Preferences.userNodeForPackage(AdminLoginController.class); // Use AdminLoginController's preferences node
        //String facultyId = prefs.get("faculty_id", null); // Retrieve stored faculty_id
        String facultyId = RememberMeHandler.getCurrentUserIdentifier();
        System.out.println("Admin faculty_id: " + facultyId);
        if (facultyId != null && !facultyId.isEmpty()) {
            loadFacultyInfo(facultyId);
        } else {
            // Handle case when no user is logged in or faculty_id is not found
            logger.warn("Admin faculty_id not found in preferences. Cannot load admin info.");
            studentNameLabel.setText("User not identified");
            studentIdLabel.setText("");
            departmentLabel.setText("");
        }
        
        // Initialize fade1 as fully transparent and fade2 as visible
        fade1.setOpacity(0);
        fade2.setOpacity(1);
        
        // Setup scroll pane fade effects
        setupScrollPaneFadeEffects();
        
        // Setup click handlers for all menu items
        setupClickHandlers();

        long t2 = System.currentTimeMillis();
        logger.info("[PERF] Controller setup finished in {} ms", (t2 - t1));

        // Show loading overlay or set all content panes to show "Loading..."
        showLoadingOverlay(true);

        // Show home FXML immediately (without data)
        loadHomeFxmlSkeleton();

        // Run data load in background
        Task<Void> dataLoadTask = new Task<>() {
            @Override
            protected Void call() {
                logger.info("[PERF] Starting data load (background thread)...");
                long dataStart = System.currentTimeMillis();
                preloadAllContent();
                long dataEnd = System.currentTimeMillis();
                logger.info("[PERF] Data loaded in {} ms (background)", (dataEnd - dataStart));
                return null;
            }
        };
        dataLoadTask.setOnSucceeded(event -> {
            logger.info("[PERF] Populating UI (background data load complete)...");
            long uiStart = System.currentTimeMillis();
            // Hide loading overlay and update UI as needed
            showLoadingOverlay(false);
            loadContent(HOME_FXML);
            long uiEnd = System.currentTimeMillis();
            logger.info("[PERF] UI populated in {} ms", (uiEnd - uiStart));
            logger.info("[PERF] AdminDashboardController.initialize() total time: {} ms", (uiEnd - t0));
        });
        dataLoadTask.setOnFailed(event -> {
            logger.error("[PERF] Data load failed", dataLoadTask.getException());
            showLoadingOverlay(false);
        });
        new Thread(dataLoadTask).start();
    }

    // Set up click handlers for all sidebar menu items
    private void setupClickHandlers() {
        // For each HBox in the sidebar, set up the click handler
        homeHBox.setOnMouseClicked(this::handleSidebarItemClick);
        settingsHBox.setOnMouseClicked(this::handleSidebarItemClick);
        aboutHBox.setOnMouseClicked(this::handleSidebarItemClick);
        paymentInfoHBox.setOnMouseClicked(this::handleSidebarItemClick);
        facultyHBox.setOnMouseClicked(this::handleSidebarItemClick);
        subjectsHBox.setOnMouseClicked(this::handleSidebarItemClick);
        scheduleHBox.setOnMouseClicked(this::handleSidebarItemClick);
        calendarHBox.setOnMouseClicked(this::handleSidebarItemClick);
        studentsHBox.setOnMouseClicked(this::handleSidebarItemClick);
        // Logout has a separate handler
        logoutHBox.setOnMouseClicked(event -> {
            try {
                handleLogoutButton(event);
            } catch (IOException e) {
                logger.error("Failed to handle logout button click", e);
            }
        });
        refreshHBox.setOnMouseClicked(this::handleRefreshButton);
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
        // Only load/cache FXML and data in this method, DO NOT TOUCH UI
        // All UI updates must be done on FX thread after this task completes

        // Optionally, you can cache FXML nodes here if you want, but do not call setContent or modify UI
        preloadFxmlContent(SCHEDULE_FXML);
        preloadFxmlContent(SETTINGS_FXML);
        preloadFxmlContent(CALENDAR_FXML);
        preloadFxmlContent(ABOUT_FXML);
        preloadFxmlContent(STUDENT_MANAGEMENT_FXML);
        preloadFxmlContent(PAYMENT_INFO_FXML);
        preloadFxmlContent(SUBJECTS_FXML);
        preloadFxmlContent(FACULTY_FXML);
    }
    
    // Preload and cache a specific FXML file
    private void preloadFxmlContent(String fxmlPath) {
        try {
            // Only cache, do not update UI here
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
    private void loadFacultyInfo(String facultyId) {
        // Get and display faculty name and ID
        getFacultyData(facultyId);
    }
    
    // Load faculty data from the database using faculty_id
    private void getFacultyData(String facultyIdStr) {
        String query = """
            SELECT f.faculty_id, 
                   COALESCE(f.faculty_number, '') AS faculty_number, 
                   f.firstname, 
                   COALESCE(f.middlename, '') AS middlename, 
                   f.lastname,
                   f.email, 
                   COALESCE(f.contactnumber, '') AS contactnumber, 
                   COALESCE(d.department_name, 'N/A') AS department_name, 
                   COALESCE(fs.status_name, 'N/A') AS status
            FROM public.faculty f
            LEFT JOIN public.departments d ON f.department_id = d.department_id
            LEFT JOIN public.faculty_statuses fs ON f.faculty_status_id = fs.faculty_status_id
            WHERE f.faculty_id = (SELECT f.faculty_id FROM public.faculty f WHERE f.faculty_number = ?) AND f.admin_type = TRUE
            """;
        
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setString(1, facultyIdStr); // faculty_id is int2, so parse and set as int
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    updateFacultyUI(rs);
                } else {
                    // Faculty with this ID isn't found or is not an admin
                    logger.warn("Admin data not found for faculty_id: {} or user is not admin_type=TRUE", facultyIdStr);
                    Platform.runLater(() -> {
                        studentNameLabel.setText("Admin Not Found");
                        studentIdLabel.setText("ID: " + facultyIdStr);
                        departmentLabel.setText("-");
                    });
                }
            }
        } catch (SQLException e) {
            logger.error("Database error while fetching admin data for faculty_id: " + facultyIdStr, e);
            Platform.runLater(() -> {
                studentNameLabel.setText("Error loading data");
                studentIdLabel.setText("");
                departmentLabel.setText("");
            });
        } catch (NumberFormatException e) {
            logger.error("Error parsing faculty_id from preferences: " + facultyIdStr, e);
            Platform.runLater(() -> {
                studentNameLabel.setText("Invalid Admin ID format");
                studentIdLabel.setText("");
                departmentLabel.setText("");
            });
        }
    }
    
    // Update the UI with faculty data
    private void updateFacultyUI(ResultSet rs) throws SQLException {
        String firstName = rs.getString("firstname");
        String lastName = rs.getString("lastname");
        String departmentName = rs.getString("department_name");
        String facultyId = rs.getString("faculty_id");

        Platform.runLater(() -> {
            studentNameLabel.setText(formatFacultyName(firstName, lastName));
            studentIdLabel.setText("ID: " + facultyId);
            departmentLabel.setText(departmentName);
        });
    }
    
    // Format the faculty name as "FirstName LastName"
    private String formatFacultyName(String firstName, String lastName) {
        StringBuilder formattedName = new StringBuilder();
        
        // Add first name
        if (firstName != null && !firstName.trim().isEmpty()) {
            formattedName.append(firstName.trim());
        }
        
        // Add a space if both names are present
        if (formattedName.length() > 0 && lastName != null && !lastName.trim().isEmpty()) {
            formattedName.append(" ");
        }
        
        // Add last name
        if (lastName != null && !lastName.trim().isEmpty()) {
            formattedName.append(lastName.trim());
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
//        } else if (clickedHBox == homeHBox) {
//            loadContent(HOME_FXML);
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
            case "registrationHBox" ->
                    null;
            case "paymentInfoHBox" -> PAYMENT_INFO_FXML;
            case "subjectsHBox" -> SUBJECTS_FXML;
            case "gradesHBox" -> null;
            case "scheduleHBox" -> SCHEDULE_FXML;
            case "calendarHBox" -> CALENDAR_FXML;
            case "aboutHBox" ->ABOUT_FXML;
            case "facultyHBox" -> FACULTY_FXML;
            case "homeHBox" -> HOME_FXML;
            case "studentsHBox" -> STUDENT_MANAGEMENT_FXML;
            case "settingsHBox" -> SETTINGS_FXML;
            default -> HOME_FXML;
        };
    }

    // Loads FXML content and applies the global theme to the root scene
    public void loadContent(String fxmlPath) {
        try {
            Parent content = contentCache.get(fxmlPath);
            if (content == null) {
                FXMLLoader loader = new FXMLLoader(
                        Objects.requireNonNull(getClass().getResource(fxmlPath))
                );
                content = loader.load();
                if (fxmlPath.equals(HOME_FXML)) {
                    String facultyId = studentIdLabel.getText();
                    SessionData.getInstance().setStudentId(facultyId);
                }
                contentCache.put(fxmlPath, content);
                addLayoutChangeListener(content);
            }
            // Remove direct theme class assignment from content node
            // Instead, apply global theme to the scene root
            if (contentPane.getScene() != null) {
                Preferences userPrefs = Preferences.userNodeForPackage(GeneralSettingsController.class).node(USER_TYPE);
                boolean darkModeEnabled = userPrefs.getBoolean(GeneralSettingsController.THEME_PREF, false);
                SISJUAN.applyThemeToSingleScene(contentPane.getScene(), darkModeEnabled);
            }
            contentPane.setContent(content);
            resetScrollPosition();
        } catch (IOException e) {
            contentPane.setContent(new Label("Error loading content"));
            logger.error("Error loading content", e);
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

    // Handle the logout button click event
    @FXML public void handleLogoutButton(MouseEvent ignoredEvent) throws IOException {
        contentCache.clear();
        StageAndSceneUtils.clearCache();
        if (logoutHBox.getScene() != null && logoutHBox.getScene().getWindow() != null) {
            Stage currentStage = (Stage) logoutHBox.getScene().getWindow();
            stageUtils.loadStage(currentStage, "/com/sisjuan/fxml/AdminLogin.fxml", StageAndSceneUtils.WindowSize.MEDIUM);
        }
    }

    @FXML
    private void handleRefreshButton(MouseEvent event) {
        logger.info("Admin Refresh button clicked. Reloading dashboard data.");
        // Save the currently selected sidebar HBox and FXML path
        HBox selectedHBox = getCurrentlySelectedSidebarHBox();
        String selectedFxml = getFxmlPathFromHBox(selectedHBox);
        refreshAllDashboardData(selectedHBox, selectedFxml);
    }

    private HBox getCurrentlySelectedSidebarHBox() {
        List<HBox> sidebarItems = Arrays.asList(
            homeHBox, paymentInfoHBox, facultyHBox, subjectsHBox, scheduleHBox, calendarHBox, studentsHBox, settingsHBox, aboutHBox
        );
        for (HBox item : sidebarItems) {
            if (item != null && item.getStyleClass().contains("selected")) {
                return item;
            }
        }
        return null;
    }

    private void refreshAllDashboardData(HBox selectedHBox, String selectedFxml) {
        contentCache.clear();
        // Only call initialize parts that do not reload the sidebar selection/content
        // Instead of calling initialize(), just reload user info and theme if needed
        reloadUserInfo();
        // Restore sidebar selection visually
        if (selectedHBox != null) updateSelectedSidebarItem(selectedHBox);
        // Reload the previously selected content
        if (selectedFxml != null) {
            loadContent(selectedFxml);
        } else {
            loadContent(HOME_FXML);
        }
    }

    private void reloadUserInfo() {
        Preferences prefs = Preferences.userNodeForPackage(AdminLoginController.class);
        String facultyId = prefs.get("faculty_id", null);
        if (facultyId != null && !facultyId.isEmpty()) {
            loadFacultyInfo(facultyId);
        } else {
            studentNameLabel.setText("User not identified");
            studentIdLabel.setText("");
            departmentLabel.setText("");
        }
    }

    private String getFxmlPathForHBox(HBox hBox) {
        return switch (hBox.getId()) {
            case "paymentInfoHBox" -> PAYMENT_INFO_FXML;
            case "facultyHBox" -> FACULTY_FXML;
            case "subjectsHBox" -> SUBJECTS_FXML;
            case "scheduleHBox" -> SCHEDULE_FXML;
            case "calendarHBox" -> CALENDAR_FXML;
            case "studentsHBox" -> STUDENT_MANAGEMENT_FXML;
            case "settingsHBox" -> SETTINGS_FXML;
            case "aboutHBox" -> ABOUT_FXML;
            default -> HOME_FXML;
        };
    }

    private void updateSelectedSidebarItem(HBox selectedHBox) {
        // Remove 'selected' from all
        List<HBox> sidebarItems = Arrays.asList(
            homeHBox, paymentInfoHBox, facultyHBox, subjectsHBox, scheduleHBox, calendarHBox, studentsHBox, settingsHBox, aboutHBox
        );
        for (HBox item : sidebarItems) {
            if (item != null) item.getStyleClass().remove("selected");
        }
        // Add 'selected' to the chosen one
        if (selectedHBox != null) selectedHBox.getStyleClass().add("selected");
    }

    public void handleQuickActionClicks(String fxmlPath) {
        if (fxmlPath.equals(SCHEDULE_FXML)) {
            clearAllSelections();
            scheduleHBox.getStyleClass().add("selected");
        }

        if (fxmlPath.equals(SUBJECTS_FXML)) {
            clearAllSelections();
            subjectsHBox.getStyleClass().add("selected");
        }

        if (fxmlPath.equals(STUDENT_MANAGEMENT_FXML)) {
            clearAllSelections();
            studentsHBox.getStyleClass().add("selected");
        }

        if (fxmlPath.equals(FACULTY_FXML)) {
            clearAllSelections();
            facultyHBox.getStyleClass().add("selected");
        }
    }

    // Clear all selections from the sidebar items
    private void clearAllSelections() {
        homeHBox.getStyleClass().remove("selected");
        settingsHBox.getStyleClass().remove("selected");
        aboutHBox.getStyleClass().remove("selected");
        logoutHBox.getStyleClass().remove("selected");
        paymentInfoHBox.getStyleClass().remove("selected");
        facultyHBox.getStyleClass().remove("selected");
        studentsHBox.getStyleClass().remove("selected");
        subjectsHBox.getStyleClass().remove("selected");
        scheduleHBox.getStyleClass().remove("selected");
        calendarHBox.getStyleClass().remove("selected");
        studentsHBox.getStyleClass().remove("selected");
    }

    private void showLoadingOverlay(boolean show) {
        // If you have a loading overlay StackPane, show/hide it here
        // Or set all content panes to show "Loading..." text
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(show);
        }
        // If you don't have a loadingOverlay, implement as needed per your FXML
    }

    // Loads the home FXML skeleton immediately, without waiting for data
    private void loadHomeFxmlSkeleton() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(HOME_FXML));
            Parent homeContent = loader.load();
            contentPane.setContent(homeContent);
        } catch (IOException e) {
            contentPane.setContent(new Label("Error loading home content"));
            logger.error("Error loading home FXML skeleton", e);
        }
    }
}
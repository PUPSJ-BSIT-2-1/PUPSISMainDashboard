package com.example.pupsis_main_dashboard.controllers;

import com.example.pupsis_main_dashboard.utilities.DBConnection;
//import com.example.pupsis_main_dashboard.databaseOperations.dbConnection2;
import com.example.pupsis_main_dashboard.utilities.SessionData;
import com.example.pupsis_main_dashboard.models.Subject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;

import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.TextField;
import javafx.scene.control.TableRow;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import java.io.IOException;
import java.util.Objects;

import javafx.scene.Node;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;

public class GradingModuleController implements Initializable {
    @FXML private TextField searchBar; // Add this field
    @FXML private TableView<Subject> subjectsTable;
    @FXML private TableColumn<Subject, String> yearSecCol;
    @FXML private TableColumn<Subject, String> semCol;
    @FXML private TableColumn<Subject, String> subjCodeCol;
    @FXML private TableColumn<Subject, String> subjDescCol;
    @FXML private Label validationLabel;
    @FXML private StackPane searchIconContainer;

    private final ObservableList<Subject> subjectsList = FXCollections.observableArrayList();
    // Keep a reference to the original data
    private final ObservableList<Subject> originalSubjectsList = FXCollections.observableArrayList();
    private String facultyId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize UI components first
        yearSecCol.setCellValueFactory(new PropertyValueFactory<>("yearSection"));
        semCol.setCellValueFactory(new PropertyValueFactory<>("semester"));
        subjCodeCol.setCellValueFactory(new PropertyValueFactory<>("subjectCode"));
        subjDescCol.setCellValueFactory(new PropertyValueFactory<>("subjectDescription"));

        yearSecCol.setReorderable(false);
        semCol.setReorderable(false);
        subjCodeCol.setReorderable(false);
        subjDescCol.setReorderable(false);

        // Set up search icon click handler
        searchIconContainer.setOnMouseClicked(event -> performSearch());

        // Show loading indicator
        subjectsTable.setPlaceholder(new Label("Loading data..."));

        validationLabel.setText(facultyId);

        // Try to get student ID with a small delay to ensure SessionData is populated
        Platform.runLater(() -> {
            facultyId = SessionData.getInstance().getStudentId();
            if (facultyId != null && !facultyId.isEmpty()) {
                // Load data asynchronously
                Task<ObservableList<Subject>> loadTask = getObservableListTask();
                new Thread(loadTask).start();
            } else {
                subjectsTable.setPlaceholder(new Label("No faculty ID available"));
            }
        });
    }

    private String attemptToRetrieveStudentId() {
        // Try to get student ID from label if available
        Node studentIdLabel = subjectsTable.getScene() != null ?
                subjectsTable.getScene().lookup("#studentIdLabel") : null;

        if (studentIdLabel instanceof Label) {
            String id = ((Label) studentIdLabel).getText();
            if (id != null && !id.isEmpty()) {
                SessionData.getInstance().setStudentId(id);
                return id;
            }
        }
        return null;
    }

    private Task<ObservableList<Subject>> getObservableListTask() {
        Task<ObservableList<Subject>> loadTask = new Task<>() {
            @Override
            protected ObservableList<Subject> call() throws Exception {
                return loadSubjectsDataAsync();
            }
        };

        loadTask.setOnSucceeded(e -> {
            subjectsList.setAll(loadTask.getValue());
            originalSubjectsList.setAll(subjectsList);
            setupSearch();
            setupRowClickHandler();
        });

        loadTask.setOnFailed(e -> {
            subjectsTable.setPlaceholder(new Label("Error loading data"));
            loadTask.getException().printStackTrace();
        });
        return loadTask;
    }
    private void setupRowClickHandler() {
        subjectsTable.setRowFactory(tv -> {
            TableRow<Subject> row = new TableRow<>() {
                @Override
                protected void updateItem(Subject item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        // Remove hover styles for empty rows
                        getStyleClass().add("empty-row");
                    } else {
                        getStyleClass().remove("empty-row");
                    }
                }
            };

            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2) {
                    Subject selectedSubject = row.getItem();
                    String subjectCode = selectedSubject.getSubjectCode();
                    String subjectDesc = selectedSubject.getSubjectDescription();

                    try {
                        ScrollPane contentPane = (ScrollPane) subjectsTable.getScene().lookup("#contentPane");
                        if (contentPane != null) {
                            // Create the FXMLLoader instance
                            FXMLLoader loader = new FXMLLoader(
                                    getClass().getResource("/com/example/pupsis_main_dashboard/fxml/EditGradesPage.fxml")
                            );

                            // Load the FXML
                            Parent newContent = loader.load();

                            // Get the controller after loading
                            EditGradesPageController controller = loader.getController();

                            // Set the subject code and description
                            controller.setSubjectCode(subjectCode);
                            controller.setSubjectDesc(subjectDesc);

                            // Set the content
                            contentPane.setContent(newContent);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            return row;
        });
    }
    private ObservableList<Subject> loadSubjectsDataAsync() throws SQLException {

        if (facultyId == null || facultyId.isEmpty()) {
            throw new SQLException("Student ID not set");
        }

        ObservableList<Subject> tempList = FXCollections.observableArrayList();
        try (Connection conn = DBConnection.getConnection()) {
            String query = "SELECT year_section, subject_code, description, semester " +
                    "FROM faculty_load WHERE faculty_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, facultyId);
                pstmt.setFetchSize(50);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        tempList.add(new Subject(
                                rs.getString("year_section"),
                                rs.getString("semester"),
                                rs.getString("subject_code"),
                                rs.getString("description")
                        ));
                    }
                }
            }
        }
        return tempList; // Make sure this is always returned
    }

    private void setupSearch() {
        // Create a filtered list wrapping the original list
        FilteredList<Subject> filteredData = new FilteredList<>(subjectsList, p -> true);

        // Add listener to searchBar text property
        searchBar.textProperty().addListener((observable, oldValue, newValue) -> {
            applyFilter(filteredData, newValue);
        });

        // Make search field responsive to ENTER key
        searchBar.setOnAction(event -> performSearch());

        // Wrap the FilteredList in a SortedList
        SortedList<Subject> sortedData = new SortedList<>(filteredData);

        // Bind the SortedList comparator to the TableView comparator
        sortedData.comparatorProperty().bind(subjectsTable.comparatorProperty());

        // Add sorted (and filtered) data to the table
        subjectsTable.setItems(sortedData);

        subjectsTable.getColumns().forEach(column -> column.setReorderable(false));
    }
    
    // Method to handle search button clicks
    private void performSearch() {
        // Get the current text from the search field
        String searchText = searchBar.getText();
        
        // Request focus on the table to show we're done with the search input
        subjectsTable.requestFocus();
        
        // If there's an active filter and results are empty, show a helpful message
        if (!searchText.isEmpty() && subjectsTable.getItems().isEmpty()) {
            subjectsTable.setPlaceholder(new Label("No matches found for: " + searchText));
        }
    }
    
    // Method to apply filter logic - extracted for reuse
    private void applyFilter(FilteredList<Subject> filteredData, String newValue) {
        filteredData.setPredicate(subject -> {
            // If search text is empty, display all subjects
            if (newValue == null || newValue.isEmpty()) {
                return true;
            }

            // Convert search text to lower case
            String lowerCaseFilter = newValue.toLowerCase();

            // Match against all fields
            if (subject.getYearSection().toLowerCase().contains(lowerCaseFilter)) {
                return true;
            }
            if (subject.getSemester().toLowerCase().contains(lowerCaseFilter)) {
                return true;
            }
            if (subject.getSubjectCode().toLowerCase().contains(lowerCaseFilter)) {
                return true;
            }

            if (subject.getSubjectDescription().toLowerCase().contains(lowerCaseFilter)){
                return true;
            }
            return false; // Does not match
        });
    }

    // Update your refreshTable method to maintain the search functionality
    @FXML
    public void refreshTable() {
        subjectsList.clear();
        originalSubjectsList.clear();
        subjectsTable.setPlaceholder(new Label("Loading data...")); // Reset placeholder

        Task<ObservableList<Subject>> refreshTask = new Task<>() {
            @Override
            protected ObservableList<Subject> call() throws Exception {
                return loadSubjectsDataAsync();
            }
        };

        refreshTask.setOnSucceeded(e -> {
            ObservableList<Subject> newData = refreshTask.getValue();
            subjectsList.setAll(newData);
            originalSubjectsList.setAll(newData);
            // The table will automatically update its display
        });

        refreshTask.setOnFailed(e -> {
            subjectsTable.setPlaceholder(new Label("Error refreshing data"));
            refreshTask.getException().printStackTrace();
        });

        new Thread(refreshTask).start();
    }
}
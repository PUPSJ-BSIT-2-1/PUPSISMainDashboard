package com.example.pupsis_main_dashboard.controllers;

import com.example.pupsis_main_dashboard.models.Student;
import com.example.pupsis_main_dashboard.utilities.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;

import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;

public class EditGradesPageController implements Initializable {

    @FXML private TextField searchBar;
    @FXML private Label gradesHeaderlbl;
    @FXML private Label subDesclbl;
    @FXML private Label subjDescLbl;
    @FXML private MenuButton subjCodeCombBox;
    @FXML private MenuButton yrSecCombBox;
    @FXML private TableView<Student> studentsTable;
    @FXML private TableColumn<Student, String> noStudCol;
    @FXML private TableColumn<Student, String> studIDCol;
    @FXML private TableColumn<Student, String> studNameCol;
    @FXML private TableColumn<Student, String> subjCodeCol;
    @FXML private TableColumn<Student, String> finGradeCol;
    @FXML private TableColumn<Student, String> gradeStatCol;

    private final ObservableList<Student> studentsList = FXCollections.observableArrayList();
    private final StudentCache studentCache = StudentCache.getInstance();
    private String selectedSubjectCode;
    private String selectedSubjectDesc;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (studentsTable == null) {
            System.err.println("Error: studentsTable is null. Check FXML file for proper fx:id.");
            return;
        }

        setupRowHoverEffect();

        // Make table editable
        studentsTable.setEditable(true);

        // Initialize the columns with the correct property names
        noStudCol.setCellValueFactory(new PropertyValueFactory<>("studentNo"));
        studIDCol.setCellValueFactory(new PropertyValueFactory<>("studentId"));
        studNameCol.setCellValueFactory(new PropertyValueFactory<>("studentNa"));
        subjCodeCol.setCellValueFactory(new PropertyValueFactory<>("subjCode"));
        finGradeCol.setCellValueFactory(new PropertyValueFactory<>("finalGrade"));
        gradeStatCol.setCellValueFactory(new PropertyValueFactory<>("gradeStatus"));

        // Make final grade column editable with custom cell factory
        finGradeCol.setCellFactory(_ -> new TableCell<>() {
            private TextField textField;

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setGraphic(null);
                }
            }

            @Override
            public void startEdit() {
                super.startEdit();

                if (textField == null) {
                    createTextField();
                }
                setText(null);
                setGraphic(textField);
                textField.requestFocus();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }

            private void createTextField() {
                textField = new TextField(getItem());
                textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);

                textField.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ENTER) {
                        if (isValidGrade(textField.getText())) {
                            commitEdit(textField.getText());
                        } else {
                            cancelEdit();
                            showError("Invalid Grade", "Please enter a valid grade between 1.0 and 5.0");
                        }
                    } else if (e.getCode() == KeyCode.ESCAPE) {
                        cancelEdit();
                    }
                });

                textField.focusedProperty().addListener((_, _, isNowFocused) -> {
                    if (!isNowFocused) {
                        if (isValidGrade(textField.getText())) {
                            commitEdit(textField.getText());
                        } else {
                            cancelEdit();
                            showError("Invalid Grade", "Please enter a valid grade between 1.0 and 5.0");
                        }
                    }
                });
            }
        });

        // Handle the commit of edits
        finGradeCol.setOnEditCommit(event -> {
            Student student = event.getRowValue();
            student.setFinalGrade(event.getNewValue());
            updateGradeInDatabase(student);
        });

        // Make other columns non-editable
        noStudCol.setEditable(false);
        studIDCol.setEditable(false);
        studNameCol.setEditable(false);
        subjCodeCol.setEditable(false);
        gradeStatCol.setEditable(false);

        // Prevent column reordering
        studentsTable.getColumns().forEach(column -> column.setReorderable(false));

        // Populate the subject codes dropdown
        populateSubjectCodes();

        // If a subject code was set before initialization, load it now
        if (selectedSubjectCode != null && selectedSubjectDesc != null) {
            subjCodeCombBox.setText(selectedSubjectCode);
            subDesclbl.setText(selectedSubjectDesc);
            loadStudentsBySubjectCode(selectedSubjectCode);
        }
    }

    // Setup row hover effect
    private void setupRowHoverEffect() {
        studentsTable.setRowFactory(_ -> {
            TableRow<Student> row = new TableRow<>() {
                @Override
                protected void updateItem(Student item, boolean empty) {
                    super.updateItem(item, empty);
                    // Reset style for empty rows
                    if (empty || item == null) {
                        getStyleClass().add("empty-row"); // Reset style for empty rows
                    } else {
                        getStyleClass().remove("empty-row"); // Reset style for non-empty rows
                    }
                }
            };

            // Add mouse hover effect
            row.hoverProperty().addListener((_, _, isNowHovered) -> {
                if (isNowHovered && !row.isEmpty()) {
                    row.setStyle("table-row-cell:hover"); // Set your desired hover color
                } else {
                    row.setStyle(""); // Reset style when not hovered
                }
            });

            return row;
        });
    }

    // Check if the entered grade is valid
    private boolean isValidGrade(String grade) {
        try {
            float gradeValue = Float.parseFloat(grade);
            return gradeValue >= 1.0 && gradeValue <= 5.0;
        } catch (NumberFormatException e) {
            showError("Invalid Grade",
                    """
                            Please enter a valid grade:
                            • Must be between 1.00 and 5.00
                            """
            );
            return false;
        }
    }

    // Update the grade in the database
    private void updateGradeInDatabase(Student student) {
        try (Connection conn = DBConnection.getConnection()) {
            // Update both final grade and grade status
            String query = "UPDATE grade SET \"final_grade\" = ?, \"gradestat\" = ? " +
                    "WHERE \"student_id\" = ? AND \"subject_code\" = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                String newGrade = student.getFinalGrade();
                float gradeValue = Float.parseFloat(newGrade);

                // Calculate grade status
                String gradeStatus;
                if (gradeValue >= 1.00 && gradeValue <= 3.00) {
                    gradeStatus = "P";
                } else if (gradeValue > 3.00 && gradeValue <= 5.00) {
                    gradeStatus = "F";
                } else {
                    gradeStatus = "INC";
                }

                // Set parameters for update
                pstmt.setFloat(1, gradeValue);
                pstmt.setString(2, gradeStatus);
                pstmt.setString(3, student.getStudentId());
                pstmt.setString(4, student.getSubjCode());

                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    // Update the student object's grade status
                    student.setGradeStatus(gradeStatus);

                    // Refresh the specific row in the table
                    int rowIndex = studentsList.indexOf(student);
                    if (rowIndex >= 0) {
                        studentsList.set(rowIndex, student);
                    }

                    // Refresh the TableView
                    studentsTable.refresh();

                    // Show a success message
                    showSuccess(String.format(
                            "Grade successfully updated to: %.2f\nStatus: %s",
                            gradeValue, gradeStatus));
                }
            }
        } catch (SQLException e) {
            showError("Database Error", "Failed to update grade: " + e.getMessage());
            e.printStackTrace();
        } catch (NumberFormatException e) {
            showError("Invalid Grade Format", "Please enter a valid numeric grade");
            e.printStackTrace();
        }
    }

    // Load students by subject code
    private void loadStudentsBySubjectCode(String subjectCode) {
        // Check cache first
        ObservableList<Student> cachedStudents = StudentCache.get(subjectCode);
        if (cachedStudents != null) {
            updateTableView(cachedStudents);
            return;
        }

        // Run database operation in the background thread
        Task<ObservableList<Student>> loadTask = new Task<>() {
            @Override
            protected ObservableList<Student> call() throws Exception {
                ObservableList<Student> tempList = FXCollections.observableArrayList();

                try (Connection conn = DBConnection.getConnection()) {
                    String query = """
                    SELECT g."id", g."student_id", g."subject_code", g."final_grade",
                           g."gradestat", concat(firstname, ' ', lastname) AS "Student Name"
                    FROM grade g, students s
                    WHERE g."student_id" = s."student_id" and g.subject_code = ? and g.faculty_id = ?
                    ORDER BY CAST(g."id" AS INTEGER)""";

                    try (PreparedStatement pstmt = conn.prepareStatement(query,
                            ResultSet.TYPE_FORWARD_ONLY,
                            ResultSet.CONCUR_READ_ONLY)) {

                        pstmt.setFetchSize(100);
                        pstmt.setString(1, subjectCode);
                        pstmt.setString(2, SessionData.getInstance().getStudentId());

                        try (ResultSet rs = pstmt.executeQuery()) {
                            while (rs.next()) {
                                if (isCancelled()) break;

                                Student student = new Student(
                                        rs.getString("id"),
                                        rs.getString("student_id"),
                                        rs.getString("Student Name"),
                                        rs.getString("subject_code"),
                                        rs.getString("final_grade"),
                                        rs.getString("gradestat")
                                );
                                tempList.add(student);
                            }
                        }
                    }
                    return tempList;
                }
            }
        };

        loadTask.setOnSucceeded(_ -> {
            ObservableList<Student> result = loadTask.getValue();
            studentCache.put(subjectCode, result);
            updateTableView(result);
            setupSearch();
        });

        loadTask.setOnFailed(_ -> {
            Throwable ex = loadTask.getException();
            showError("Database Error", "Failed to load data: " + ex.getMessage());
            ex.printStackTrace();
        });

        new Thread(loadTask).start();
    }

    // Populate subject codes
    private void populateSubjectCodes() {
        try (Connection conn = DBConnection.getConnection()) {
            String query = """
                SELECT DISTINCT subject_code FROM grade WHERE faculty_id = ?;""";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, SessionData.getInstance().getStudentId());
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String subjCode = rs.getString("subject_code");
                    javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(subjCode);
                    item.setOnAction(_ -> {
                        subjCodeCombBox.setText(subjCode);
                        loadStudentsBySubjectCode(subjCode);
                    });
                    subjCodeCombBox.getItems().add(item);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading subject codes: " + e.getMessage());
            e.printStackTrace();
            showError("Database Error", "Failed to load subject codes: " + e.getMessage());
        }
    }

    // Update the TableView
    private void updateTableView(ObservableList<Student> students) {
        Platform.runLater(() -> {
            studentsList.clear();
            studentsList.addAll(students);
            studentsTable.setItems(studentsList);
        });
    }

    // Set the selected subject code
    public void setSubjectCode(String subjectCode) {
        this.selectedSubjectCode = subjectCode;
        if (subjCodeCombBox != null) {
            subjCodeCombBox.setText(subjectCode);
            loadStudentsBySubjectCode(subjectCode);
        }
    }

    // Setup search
    private void setupSearch() {

        // Create a filtered list wrapping the original list
        FilteredList<Student> filteredData = new FilteredList<>(studentsList, _ -> true);

        // Add listener to searchBar text property
        searchBar.textProperty().addListener((_, _, newValue) ->
                filteredData.setPredicate(subject -> {
            // If a search text is empty, display all subjects
            if (newValue == null || newValue.isEmpty()) {
                return true;
            }

            // Convert search text to lower case
            String lowerCaseFilter = newValue.toLowerCase();

            // Match against all fields
            if (subject.getStudentId().toLowerCase().contains(lowerCaseFilter)) {
                return true;
            }
            if (subject.getStudentNa().toLowerCase().contains(lowerCaseFilter)) {
                return true;
            }
            return subject.getSubjCode().toLowerCase().contains(lowerCaseFilter);// Does not match
        }));

        // Wrap the FilteredList in a SortedList
        SortedList<Student> sortedData = new SortedList<>(filteredData);

        // Bind the SortedList comparator to the TableView comparator
        sortedData.comparatorProperty().bind(studentsTable.comparatorProperty());

        // Add sorted (and filtered) data to the table
        studentsTable.setItems(sortedData);

        studentsTable.getColumns().forEach(column -> column.setReorderable(false));
    }

    // Set the selected subject description
    public void setSubjectDesc(String subjectDesc) {
        this.selectedSubjectDesc = subjectDesc;
        if (subjDescLbl != null) {
            subjDescLbl.setText(subjectDesc);
        }
    }

    // Clear the cache for a specific subject
    public void clearCacheForSubject(String subjectCode) {
        studentCache.remove(subjectCode);
    }

    // Handle refresh button click
    @FXML private void handleRefresh() {
        if (selectedSubjectCode != null) {
            studentCache.remove(selectedSubjectCode);
            loadStudentsBySubjectCode(selectedSubjectCode);
        }
    }

    // Show the error message dialog box with the specified title and content
    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // Show the success message dialog box with the specified title and content
    private void showSuccess(String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}

package com.mycompany.msr.amis;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class AssignmentReportController implements Initializable {

    private final ReportService reportService = ServiceRegistry.getReportService();

    @FXML private ComboBox<String> cmbPerson;
    @FXML private ComboBox<String> cmbEquipmentType;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private TableView<Assignment> tableAssignments;
    @FXML private TableColumn<Assignment, String> colPerson;
    @FXML private TableColumn<Assignment, String> colDepartment;
    @FXML private TableColumn<Assignment, String> colEquipment;
    @FXML private TableColumn<Assignment, String> colReason;
    @FXML private TableColumn<Assignment, Integer> colQuantity;
    @FXML private TableColumn<Assignment, String> colStatus;
    @FXML private TableColumn<Assignment, String> colDate;

    private final ObservableList<Assignment> data = FXCollections.observableArrayList();
    private List<Assignment> allAssignments = List.of();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        colPerson.setCellValueFactory(new PropertyValueFactory<>("person"));
        colDepartment.setCellValueFactory(new PropertyValueFactory<>("department"));
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentType"));
        colReason.setCellValueFactory(new PropertyValueFactory<>("reason"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        setupContextMenu();
        loadData();
        loadFilters();
    }

    private void loadData() {
        try {
            allAssignments = reportService.getAssignmentReport();
            data.setAll(allAssignments);
        } catch (Exception e) {
            showAlert("Error", "Failed to load data:\n" + e.getMessage());
        }
        tableAssignments.setItems(data);
    }

    private void loadFilters() {
        cmbPerson.getItems().clear();
        cmbEquipmentType.getItems().clear();
        cmbStatus.getItems().clear();

        for (Assignment assignment : allAssignments) {
            addIfMissing(cmbPerson, assignment.getPerson());
            addIfMissing(cmbEquipmentType, assignment.getEquipmentType());
            addIfMissing(cmbStatus, assignment.getStatus());
        }
    }

    @FXML
    private void handleFilter(ActionEvent event) {
        String person = cmbPerson.getValue();
        String type = cmbEquipmentType.getValue();
        String status = cmbStatus.getValue();

        data.clear();
        for (Assignment assignment : allAssignments) {
            if (!matchesFilter(assignment.getPerson(), person)) {
                continue;
            }
            if (!matchesFilter(assignment.getEquipmentType(), type)) {
                continue;
            }
            if (!matchesFilter(assignment.getStatus(), status)) {
                continue;
            }
            data.add(assignment);
        }

        tableAssignments.setItems(data);
    }

    private void handleRefresh() {
        cmbPerson.setValue(null);
        cmbEquipmentType.setValue(null);
        cmbStatus.setValue(null);
        loadData();
        loadFilters();
        showAlert("Refresh", "Assignment report refreshed successfully.");
    }

    @FXML
    private void handleExport(ActionEvent event) {
        if (data.isEmpty()) {
            showAlert("No Data", "No assignment data to export.");
            return;
        }

        File file = FileLocationHelper.fileInDownloads("assignment_report.csv");
        OperationFeedbackHelper.showInfo(
                "Export Starting",
                "Preparing assignment report export.\n\nRows to export: " + data.size()
        );

        try (FileWriter writer = new FileWriter(file)) {
            writer.append("Responsible Person,Department,Equipment,Reason,Quantity,Status,Date\n");

            for (Assignment assignment : data) {
                writer.append(csvSafe(assignment.getPerson())).append(",")
                        .append(csvSafe(assignment.getDepartment())).append(",")
                        .append(csvSafe(assignment.getEquipmentType())).append(",")
                        .append(csvSafe(assignment.getReason())).append(",")
                        .append(String.valueOf(assignment.getQuantity())).append(",")
                        .append(csvSafe(assignment.getStatus())).append(",")
                        .append(csvSafe(assignment.getDate())).append("\n");
            }

            OperationFeedbackHelper.showInfo(
                    "Export Complete",
                    "Assignment report exported successfully to:\n" + file.getAbsolutePath()
            );
        } catch (IOException e) {
            e.printStackTrace();
            OperationFeedbackHelper.showError(
                    "Export Failed",
                    "Assignment report export failed:\n" + e.getMessage()
            );
        }
    }

    private String csvSafe(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private boolean matchesFilter(String value, String filter) {
        return filter == null || filter.isBlank() || (value != null && value.equalsIgnoreCase(filter));
    }

    private void addIfMissing(ComboBox<String> comboBox, String value) {
        if (value == null || value.isBlank() || comboBox.getItems().contains(value)) {
            return;
        }
        comboBox.getItems().add(value);
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem refresh = new MenuItem("Refresh Assignment Report");
        refresh.setOnAction(event -> handleRefresh());
        menu.getItems().add(refresh);
        tableAssignments.setContextMenu(menu);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

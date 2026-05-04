package com.mycompany.msr.amis;

import java.io.File;
import java.io.FileWriter;
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

public class OutstandingReportController implements Initializable {

    private final ReportService reportService = ServiceRegistry.getReportService();

    @FXML private ComboBox<String> cmbPerson;
    @FXML private TableView<Distribution> tableOutstanding;
    @FXML private TableColumn<Distribution, String> colAssetCode;
    @FXML private TableColumn<Distribution, String> colAssignedTo;
    @FXML private TableColumn<Distribution, String> colPhone;
    @FXML private TableColumn<Distribution, String> colNID;
    @FXML private TableColumn<Distribution, Integer> colAssignmentId;
    @FXML private TableColumn<Distribution, String> colDate;
    @FXML private TableColumn<Distribution, String> colStatus;
    @FXML private TableColumn<Distribution, String> colOutstandingRemarks;

    private final ObservableList<Distribution> data = FXCollections.observableArrayList();
    private List<Distribution> allOutstanding = List.of();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        colAssetCode.setCellValueFactory(new PropertyValueFactory<>("assetCode"));
        colAssignedTo.setCellValueFactory(new PropertyValueFactory<>("assignedTo"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colNID.setCellValueFactory(new PropertyValueFactory<>("nid"));
        colAssignmentId.setCellValueFactory(new PropertyValueFactory<>("assignmentId"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colOutstandingRemarks.setCellValueFactory(new PropertyValueFactory<>("outstandingRemarks"));

        setupContextMenu();
        loadData();
        loadPeople();
    }

    private void loadPeople() {
        cmbPerson.getItems().clear();
        for (Distribution distribution : allOutstanding) {
            addIfMissing(cmbPerson, distribution.getAssignedTo());
        }
    }

    private void loadData() {
        try {
            allOutstanding = reportService.getOutstandingReport();
            data.setAll(allOutstanding);
        } catch (Exception e) {
            showAlert("Error", "Failed to load data:\n" + e.getMessage());
        }

        tableOutstanding.setItems(data);
    }

    @FXML
    private void handleFilter(ActionEvent event) {
        String person = cmbPerson.getValue();

        data.clear();
        for (Distribution distribution : allOutstanding) {
            if (!matchesContains(distribution.getAssignedTo(), person)) {
                continue;
            }
            data.add(distribution);
        }

        tableOutstanding.setItems(data);
    }

    private void handleRefresh() {
        cmbPerson.setValue(null);
        loadData();
        loadPeople();
        showAlert("Refresh", "Outstanding data refreshed.");
    }

    @FXML
    private void handleExport(ActionEvent event) {
        if (data.isEmpty()) {
            showAlert("No Data", "No outstanding data to export.");
            return;
        }

        try {
            File file = FileLocationHelper.fileInDownloads("outstanding_report.csv");
            OperationFeedbackHelper.showInfo(
                    "Export Starting",
                    "Preparing outstanding report export.\n\nRows to export: " + data.size()
            );

            try (FileWriter writer = new FileWriter(file)) {
                writer.append("Asset Code,Assigned To,Phone,NID,Assignment ID,Date,Status,Outstanding Remarks\n");

                for (Distribution distribution : data) {
                    writer.append(csvSafe(distribution.getAssetCode())).append(",")
                            .append(csvSafe(distribution.getAssignedTo())).append(",")
                            .append(csvSafe(distribution.getPhone())).append(",")
                            .append(csvSafe(distribution.getNid())).append(",")
                            .append(String.valueOf(distribution.getAssignmentId())).append(",")
                            .append(csvSafe(distribution.getDate())).append(",")
                            .append(csvSafe(distribution.getStatus())).append(",")
                            .append(csvSafe(distribution.getOutstandingRemarks())).append("\n");
                }
            }

            OperationFeedbackHelper.showInfo(
                    "Export Complete",
                    "Outstanding report exported successfully to:\n" + file.getAbsolutePath()
            );
        } catch (Exception e) {
            e.printStackTrace();
            OperationFeedbackHelper.showError(
                    "Export Failed",
                    "Outstanding report export failed:\n" + e.getMessage()
            );
        }
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem refresh = new MenuItem("Refresh Outstanding Report");
        refresh.setOnAction(event -> handleRefresh());
        menu.getItems().add(refresh);
        tableOutstanding.setContextMenu(menu);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String csvSafe(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private void addIfMissing(ComboBox<String> comboBox, String value) {
        if (value == null || value.isBlank() || comboBox.getItems().contains(value)) {
            return;
        }
        comboBox.getItems().add(value);
    }

    private boolean matchesContains(String value, String filter) {
        return filter == null || filter.isBlank() || (value != null && value.toLowerCase().contains(filter.toLowerCase()));
    }
}

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

public class ReturnEquipmentListController implements Initializable {

    private final ReportService reportService = ServiceRegistry.getReportService();

    @FXML private ComboBox<String> cmbPerson;
    @FXML private ComboBox<String> cmbCondition;
    @FXML private TableView<ReturnRecord> tableReturns;
    @FXML private TableColumn<ReturnRecord, Void> colNo;
    @FXML private TableColumn<ReturnRecord, String> colAssetCode;
    @FXML private TableColumn<ReturnRecord, String> colResponsibleOfficer;
    @FXML private TableColumn<ReturnRecord, String> colEquipmentType;
    @FXML private TableColumn<ReturnRecord, String> colAssignmentReason;
    @FXML private TableColumn<ReturnRecord, String> colReturnedBy;
    @FXML private TableColumn<ReturnRecord, String> colPhone;
    @FXML private TableColumn<ReturnRecord, String> colNID;
    @FXML private TableColumn<ReturnRecord, String> colCondition;
    @FXML private TableColumn<ReturnRecord, String> colRemarks;
    @FXML private TableColumn<ReturnRecord, String> colDate;

    private final ObservableList<ReturnRecord> data = FXCollections.observableArrayList();
    private List<ReturnRecord> allReturns = List.of();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        TableNumbering.install(colNo);
        colAssetCode.setCellValueFactory(new PropertyValueFactory<>("assetCode"));
        colResponsibleOfficer.setCellValueFactory(new PropertyValueFactory<>("responsibleOfficer"));
        colEquipmentType.setCellValueFactory(new PropertyValueFactory<>("assignmentEquipmentType"));
        colAssignmentReason.setCellValueFactory(new PropertyValueFactory<>("assignmentReason"));
        colReturnedBy.setCellValueFactory(new PropertyValueFactory<>("returnedBy"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colNID.setCellValueFactory(new PropertyValueFactory<>("nid"));
        colCondition.setCellValueFactory(new PropertyValueFactory<>("returnCondition"));
        colRemarks.setCellValueFactory(new PropertyValueFactory<>("remarks"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("returnDate"));

        setupContextMenu();
        loadData();
        loadPeople();
    }

    private void loadPeople() {
        cmbPerson.getItems().clear();
        cmbCondition.getItems().clear();
        for (ReturnRecord record : allReturns) {
            addIfMissing(cmbPerson, record.getReturnedBy());
            addIfMissing(cmbCondition, record.getReturnCondition());
        }
    }

    private void loadData() {
        try {
            allReturns = reportService.getReturnReport();
            data.setAll(allReturns);
        } catch (Exception e) {
            showAlert("Error", "Failed to load data:\n" + e.getMessage());
        }
        tableReturns.setItems(data);
    }

    @FXML
    private void handleFilter(ActionEvent event) {
        String person = cmbPerson.getValue();
        String condition = cmbCondition.getValue();

        data.clear();
        for (ReturnRecord record : allReturns) {
            if (!matchesContains(record.getReturnedBy(), person)) {
                continue;
            }
            if (!matchesExact(record.getReturnCondition(), condition)) {
                continue;
            }
            data.add(record);
        }
        tableReturns.setItems(data);
    }

    private void handleRefresh() {
        cmbPerson.setValue(null);
        cmbCondition.setValue(null);
        loadData();
        loadPeople();
        showAlert("Refresh", "Return equipment list refreshed successfully.");
    }

    @FXML
    private void handleExport(ActionEvent event) {
        if (data.isEmpty()) {
            showAlert("No Data", "No return data to export.");
            return;
        }

        try {
            File file = FileLocationHelper.fileInDownloads("return_equipment_list.csv");
            OperationFeedbackHelper.showInfo(
                    "Export Starting",
                    "Preparing return equipment list export.\n\nRows to export: " + data.size()
            );

            try (FileWriter writer = new FileWriter(file)) {
                writer.append("Asset Code,Responsible Officer,Equipment Type,Assignment Reason,Returned By,Phone,NID,Return Condition,Remarks,Return Date\n");
                for (ReturnRecord record : data) {
                    writer.append(csvSafe(record.getAssetCode())).append(",")
                            .append(csvSafe(record.getResponsibleOfficer())).append(",")
                            .append(csvSafe(record.getAssignmentEquipmentType())).append(",")
                            .append(csvSafe(record.getAssignmentReason())).append(",")
                            .append(csvSafe(record.getReturnedBy())).append(",")
                            .append(csvSafe(record.getPhone())).append(",")
                            .append(csvSafe(record.getNid())).append(",")
                            .append(csvSafe(record.getReturnCondition())).append(",")
                            .append(csvSafe(record.getRemarks())).append(",")
                            .append(csvSafe(record.getReturnDate())).append("\n");
                }
            }

            OperationFeedbackHelper.showInfo(
                    "Export Complete",
                    "Return equipment list exported successfully to:\n" + file.getAbsolutePath()
            );
        } catch (Exception e) {
            e.printStackTrace();
            OperationFeedbackHelper.showError(
                    "Export Failed",
                    "Return equipment list export failed:\n" + e.getMessage()
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

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem refresh = new MenuItem("Refresh Return Equipment List");
        refresh.setOnAction(event -> handleRefresh());
        menu.getItems().add(refresh);
        tableReturns.setContextMenu(menu);
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

    private boolean matchesExact(String value, String filter) {
        return filter == null || filter.isBlank() || (value != null && value.equalsIgnoreCase(filter));
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

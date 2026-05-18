package com.mycompany.msr.amis;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.text.Text;

public class AssetHistoryController implements Initializable {

    private final AssetHistoryService assetHistoryService = ServiceRegistry.getAssetHistoryService();

    @FXML private TextField txtAssetCode;
    @FXML private Label lblAssetCode;
    @FXML private Label lblSerialNumber;
    @FXML private Label lblEquipmentName;
    @FXML private Label lblCategory;
    @FXML private Label lblEntryDate;
    @FXML private Label lblCurrentStatus;
    @FXML private TableView<AssetHistoryRecord> tableHistory;
    @FXML private TableColumn<AssetHistoryRecord, Void> colNo;
    @FXML private TableColumn<AssetHistoryRecord, String> colActivityDate;
    @FXML private TableColumn<AssetHistoryRecord, String> colEventType;
    @FXML private TableColumn<AssetHistoryRecord, String> colActor;
    @FXML private TableColumn<AssetHistoryRecord, String> colAffectedPerson;
    @FXML private TableColumn<AssetHistoryRecord, String> colDetails;
    @FXML private TableColumn<AssetHistoryRecord, String> colStatus;

    private final ObservableList<AssetHistoryRecord> data = FXCollections.observableArrayList();
    private String currentAssetCode = "";

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        TableNumbering.install(colNo);
        colActivityDate.setCellValueFactory(new PropertyValueFactory<>("activityDate"));
        colEventType.setCellValueFactory(new PropertyValueFactory<>("eventType"));
        colActor.setCellValueFactory(new PropertyValueFactory<>("actor"));
        colAffectedPerson.setCellValueFactory(new PropertyValueFactory<>("affectedPerson"));
        colDetails.setCellValueFactory(new PropertyValueFactory<>("details"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        configureTableAppearance();
        tableHistory.setItems(data);
        setupContextMenu();
        clearSummary();
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        String assetCode = txtAssetCode.getText() == null ? "" : txtAssetCode.getText().trim();
        if (assetCode.isBlank()) {
            showAlert("Search", "Enter an asset code.");
            return;
        }

        currentAssetCode = assetCode;
        AssetHistoryResult result;
        try {
            result = assetHistoryService.getAssetHistory(assetCode);
        } catch (Exception e) {
            showAlert("Error", "Failed to load asset history:\n" + e.getMessage());
            return;
        }

        if (result == null || result.getSummary() == null) {
            data.clear();
            clearSummary();
            showAlert("Not Found", "No asset was found for code: " + assetCode);
            return;
        }

        applySummary(result.getSummary());
        List<AssetHistoryRecord> displayRecords = includeLocalMaintenanceRecords(result.getRecords());
        data.setAll(displayRecords);
    }

    @FXML
    private void handleClear(ActionEvent event) {
        currentAssetCode = "";
        txtAssetCode.clear();
        data.clear();
        clearSummary();
    }

    @FXML
    private void handleExport(ActionEvent event) {
        if (currentAssetCode.isBlank() || data.isEmpty()) {
            showAlert("No Data", "Search for an asset with history before exporting.");
            return;
        }

        try {
            File file = FileLocationHelper.fileInDownloads("asset_history_" + currentAssetCode + ".csv");
            try (FileWriter writer = new FileWriter(file)) {
                writer.append("Asset Code,Activity Date,Event Type,Actor,Affected Person,Details,Status\n");
                for (AssetHistoryRecord record : data) {
                    writer.append(csvSafe(currentAssetCode)).append(",")
                            .append(csvSafe(record.getActivityDate())).append(",")
                            .append(csvSafe(record.getEventType())).append(",")
                            .append(csvSafe(record.getActor())).append(",")
                            .append(csvSafe(record.getAffectedPerson())).append(",")
                            .append(csvSafe(record.getDetails())).append(",")
                            .append(csvSafe(record.getStatus())).append("\n");
                }
            }

            OperationFeedbackHelper.showInfo(
                    "Export Complete",
                    "Asset history exported successfully to:\n" + file.getAbsolutePath()
            );
        } catch (Exception e) {
            e.printStackTrace();
            OperationFeedbackHelper.showError("Export Failed", "Asset history export failed:\n" + e.getMessage());
        }
    }

    private void applySummary(AssetHistorySummary summary) {
        lblAssetCode.setText(valueOrDash(summary.getAssetCode()));
        lblSerialNumber.setText(valueOrDash(summary.getSerialNumber()));
        lblEquipmentName.setText(valueOrDash(summary.getEquipmentName()));
        lblCategory.setText(valueOrDash(summary.getCategory()));
        lblEntryDate.setText(valueOrDash(summary.getEntryDate()));
        lblCurrentStatus.setText(valueOrDash(summary.getCurrentStatus()));
    }

    private List<AssetHistoryRecord> includeLocalMaintenanceRecords(List<AssetHistoryRecord> records) {
        List<AssetHistoryRecord> merged = new ArrayList<>(records == null ? List.of() : records);
        for (MaintenanceRecord maintenance : DatabaseHandler.getMaintenanceRecords()) {
            if (maintenance == null || !sameAsset(currentAssetCode, maintenance.getAssetCode())) {
                continue;
            }
            AssetHistoryRecord historyRecord = toMaintenanceHistoryRecord(maintenance);
            if (!containsHistoryRecord(merged, historyRecord)) {
                merged.add(historyRecord);
            }
        }
        merged.sort((first, second) -> {
            int dateCompare = valueOrEmpty(second.getActivityDate()).compareTo(valueOrEmpty(first.getActivityDate()));
            if (dateCompare != 0) {
                return dateCompare;
            }
            return valueOrEmpty(second.getEventType()).compareTo(valueOrEmpty(first.getEventType()));
        });
        return merged;
    }

    private AssetHistoryRecord toMaintenanceHistoryRecord(MaintenanceRecord record) {
        boolean completed = "COMPLETED".equalsIgnoreCase(record.getStatus());
        return new AssetHistoryRecord(
                record.getMaintenanceDate(),
                completed ? "MAINTENANCE COMPLETED" : "SENT TO MAINTENANCE",
                record.getPerformedBy(),
                "",
                buildMaintenanceDetails(record),
                completed ? "AVAILABLE" : "MAINTENANCE"
        );
    }

    private String buildMaintenanceDetails(MaintenanceRecord record) {
        StringBuilder details = new StringBuilder();
        appendDetail(details, "Issue", record.getIssue());
        appendDetail(details, "Action taken", record.getActionTaken());
        appendDetail(details, "Cost", record.getCost());
        appendDetail(details, "Maintenance status", record.getStatus());
        return details.toString();
    }

    private void appendDetail(StringBuilder details, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (details.length() > 0) {
            details.append(" | ");
        }
        details.append(label).append(": ").append(value.trim());
    }

    private boolean containsHistoryRecord(List<AssetHistoryRecord> records, AssetHistoryRecord candidate) {
        String candidateKey = historyKey(candidate);
        for (AssetHistoryRecord record : records) {
            if (historyKey(record).equals(candidateKey)) {
                return true;
            }
        }
        return false;
    }

    private String historyKey(AssetHistoryRecord record) {
        if (record == null) {
            return "";
        }
        return normalizeKey(record.getActivityDate()) + "|"
                + normalizeKey(record.getEventType()) + "|"
                + normalizeKey(record.getActor()) + "|"
                + normalizeKey(record.getAffectedPerson()) + "|"
                + normalizeKey(record.getDetails()) + "|"
                + normalizeKey(record.getStatus());
    }

    private boolean sameAsset(String first, String second) {
        return normalizeKey(first).equals(normalizeKey(second));
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem refresh = new MenuItem("Refresh Asset History");
        refresh.setOnAction(event -> {
            if (!currentAssetCode.isBlank()) {
                handleSearch(null);
            }
        });
        menu.getItems().add(refresh);
        tableHistory.setContextMenu(menu);
    }

    private void configureTableAppearance() {
        tableHistory.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        colActivityDate.setPrefWidth(150);
        colActivityDate.setMinWidth(150);
        colEventType.setPrefWidth(210);
        colEventType.setMinWidth(190);
        colActor.setPrefWidth(200);
        colActor.setMinWidth(180);
        colAffectedPerson.setPrefWidth(220);
        colAffectedPerson.setMinWidth(200);
        colDetails.setPrefWidth(520);
        colDetails.setMinWidth(420);
        colStatus.setPrefWidth(180);
        colStatus.setMinWidth(170);

        colDetails.setCellFactory(column -> new TableCell<AssetHistoryRecord, String>() {
            private final Text text = new Text();

            {
                text.wrappingWidthProperty().bind(column.widthProperty().subtract(24));
                setGraphic(text);
                setPrefHeight(USE_COMPUTED_SIZE);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    text.setText("");
                    setGraphic(null);
                } else {
                    text.setText(formatHistoryDetails(getTableRow().getItem(), item));
                    setGraphic(text);
                }
            }
        });
    }

    private String formatHistoryDetails(AssetHistoryRecord record, String details) {
        if (details == null || details.isBlank()) {
            return "";
        }

        String formatted = details.replace(" | ", "\n");
        if (record != null && "RETURNED".equalsIgnoreCase(record.getEventType())) {
            formatted = formatted.replace("Remarks: returned", "Notes: Returned to store");
        }
        return formatted;
    }

    private void clearSummary() {
        lblAssetCode.setText("-");
        lblSerialNumber.setText("-");
        lblEquipmentName.setText("-");
        lblCategory.setText("-");
        lblEntryDate.setText("-");
        lblCurrentStatus.setText("-");
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

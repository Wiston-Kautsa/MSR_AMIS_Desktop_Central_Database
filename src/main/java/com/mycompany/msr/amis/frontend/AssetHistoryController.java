package com.mycompany.msr.amis;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
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
        data.setAll(result.getRecords());
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
        colEventType.setPrefWidth(150);
        colEventType.setMinWidth(150);
        colActor.setPrefWidth(220);
        colActor.setMinWidth(220);
        colAffectedPerson.setPrefWidth(240);
        colAffectedPerson.setMinWidth(240);
        colDetails.setPrefWidth(620);
        colDetails.setMinWidth(620);
        colStatus.setPrefWidth(150);
        colStatus.setMinWidth(150);

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
        if (title != null && title.toLowerCase().contains("error")) {
            OperationFeedbackHelper.showError(title, message);
        } else {
            OperationFeedbackHelper.showInfo(title, message);
        }
    }
}

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

public class InventoryReportController implements Initializable {

    private final ReportService reportService = ServiceRegistry.getReportService();

    @FXML private ComboBox<String> cmbCategory;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private TableView<Equipment> tableInventory;
    @FXML private TableColumn<Equipment, String> colAssetCode;
    @FXML private TableColumn<Equipment, String> colName;
    @FXML private TableColumn<Equipment, String> colCategory;
    @FXML private TableColumn<Equipment, String> colSerial;
    @FXML private TableColumn<Equipment, String> colCondition;
    @FXML private TableColumn<Equipment, String> colStatus;
    @FXML private TableColumn<Equipment, String> colSource;
    @FXML private TableColumn<Equipment, String> colDate;

    private final ObservableList<Equipment> data = FXCollections.observableArrayList();
    private List<Equipment> allInventory = List.of();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        colAssetCode.setCellValueFactory(new PropertyValueFactory<>("assetCode"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colCondition.setCellValueFactory(new PropertyValueFactory<>("condition"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colSource.setCellValueFactory(new PropertyValueFactory<>("source"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("entryDate"));

        setupContextMenu();
        loadData();
        loadCategories();
    }

    private void loadData() {
        try {
            allInventory = reportService.getInventoryReport();
            data.setAll(allInventory);
        } catch (Exception e) {
            showAlert("Error", "Failed to load data:\n" + e.getMessage());
        }
        tableInventory.setItems(data);
    }

    private void loadCategories() {
        cmbCategory.getItems().clear();
        cmbStatus.getItems().clear();
        for (Equipment equipment : allInventory) {
            addIfMissing(cmbCategory, equipment.getCategory());
            addIfMissing(cmbStatus, equipment.getStatus());
        }
    }

    @FXML
    private void handleFilter(ActionEvent event) {
        String category = cmbCategory.getValue();
        String status = cmbStatus.getValue();

        data.clear();
        for (Equipment equipment : allInventory) {
            if (!matchesFilter(equipment.getCategory(), category)) {
                continue;
            }
            if (!matchesFilter(equipment.getStatus(), status)) {
                continue;
            }
            data.add(equipment);
        }
        tableInventory.setItems(data);
    }

    private void handleRefresh() {
        cmbCategory.setValue(null);
        cmbStatus.setValue(null);
        loadData();
        loadCategories();
        showAlert("Refresh", "Inventory report refreshed successfully.");
    }

    @FXML
    private void handleExport(ActionEvent event) {
        if (data.isEmpty()) {
            showAlert("No Data", "There is no data to export.");
            return;
        }

        File file = FileLocationHelper.fileInDownloads("inventory_report.csv");
        OperationFeedbackHelper.showInfo(
                "Export Starting",
                "Preparing inventory report export.\n\nRows to export: " + data.size()
        );

        try (FileWriter writer = new FileWriter(file)) {
            writer.append("Asset Code,Name,Category,IMEI/Serial Number,Condition,Status,Source,Date\n");
            for (Equipment equipment : data) {
                writer.append(csvSafe(equipment.getAssetCode())).append(",")
                        .append(csvSafe(equipment.getName())).append(",")
                        .append(csvSafe(equipment.getCategory())).append(",")
                        .append(csvSafe(equipment.getSerialNumber())).append(",")
                        .append(csvSafe(equipment.getCondition())).append(",")
                        .append(csvSafe(equipment.getStatus())).append(",")
                        .append(csvSafe(equipment.getSource())).append(",")
                        .append(csvSafe(equipment.getEntryDate())).append("\n");
            }

            OperationFeedbackHelper.showInfo(
                    "Export Complete",
                    "Inventory report exported successfully to:\n" + file.getAbsolutePath()
            );
        } catch (Exception e) {
            e.printStackTrace();
            OperationFeedbackHelper.showError(
                    "Export Failed",
                    "Inventory report export failed:\n" + e.getMessage()
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
        MenuItem refresh = new MenuItem("Refresh Inventory Report");
        refresh.setOnAction(event -> handleRefresh());
        menu.getItems().add(refresh);
        tableInventory.setContextMenu(menu);
    }

    private void addIfMissing(ComboBox<String> comboBox, String value) {
        if (value == null || value.isBlank() || comboBox.getItems().contains(value)) {
            return;
        }
        comboBox.getItems().add(value);
    }

    private boolean matchesFilter(String value, String filter) {
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

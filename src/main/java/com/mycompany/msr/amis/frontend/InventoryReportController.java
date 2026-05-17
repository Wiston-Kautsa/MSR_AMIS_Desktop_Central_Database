package com.mycompany.msr.amis;

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
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class InventoryReportController implements Initializable {

    private final ReportService reportService = ServiceRegistry.getReportService();

    @FXML private ComboBox<String> cmbCategory;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private TableView<Equipment> tableInventory;
    @FXML private TableColumn<Equipment, Void> colNo;
    @FXML private TableColumn<Equipment, String> colAssetCode;
    @FXML private TableColumn<Equipment, String> colName;
    @FXML private TableColumn<Equipment, String> colCategory;
    @FXML private TableColumn<Equipment, String> colSerial;
    @FXML private TableColumn<Equipment, String> colCondition;
    @FXML private TableColumn<Equipment, String> colStatus;
    @FXML private TableColumn<Equipment, String> colSource;
    @FXML private TableColumn<Equipment, String> colPurchaseCost;
    @FXML private TableColumn<Equipment, String> colLocation;
    @FXML private TableColumn<Equipment, String> colWarrantyExpiry;
    @FXML private TableColumn<Equipment, String> colSupplier;
    @FXML private TableColumn<Equipment, String> colDate;

    private final ObservableList<Equipment> data = FXCollections.observableArrayList();
    private List<Equipment> allInventory = List.of();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        TableNumbering.install(colNo);
        colAssetCode.setCellValueFactory(new PropertyValueFactory<>("assetCode"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colCondition.setCellValueFactory(new PropertyValueFactory<>("condition"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colSource.setCellValueFactory(new PropertyValueFactory<>("source"));
        colPurchaseCost.setCellValueFactory(new PropertyValueFactory<>("purchaseCost"));
        CurrencyFormatHelper.installCurrencyCellFactory(colPurchaseCost);
        colLocation.setCellValueFactory(new PropertyValueFactory<>("location"));
        colWarrantyExpiry.setCellValueFactory(new PropertyValueFactory<>("warrantyExpiry"));
        colSupplier.setCellValueFactory(new PropertyValueFactory<>("supplier"));
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
            if (!ReportFilterHelper.matchesDateRange(equipment.getEntryDate(), dpFrom, dpTo)) {
                continue;
            }
            data.add(equipment);
        }
        tableInventory.setItems(data);
    }

    private void handleRefresh() {
        cmbCategory.setValue(null);
        cmbStatus.setValue(null);
        dpFrom.setValue(null);
        dpTo.setValue(null);
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

        ReportExportHelper.exportCsv("inventory_report", "Inventory Report", new ArrayList<>(data), columns());
    }

    @FXML
    private void handleExportPdf(ActionEvent event) {
        ReportExportHelper.exportPdf("inventory_report", "Inventory Report", new ArrayList<>(data), columns());
    }

    private List<ReportExportHelper.Column<Equipment>> columns() {
        return List.of(
                new ReportExportHelper.Column<>("Asset Code", Equipment::getAssetCode),
                new ReportExportHelper.Column<>("Name", Equipment::getName),
                new ReportExportHelper.Column<>("Category", Equipment::getCategory),
                new ReportExportHelper.Column<>("IMEI/Serial Number", Equipment::getSerialNumber),
                new ReportExportHelper.Column<>("Condition", Equipment::getCondition),
                new ReportExportHelper.Column<>("Status", Equipment::getStatus),
                new ReportExportHelper.Column<>("Source", Equipment::getSource),
                new ReportExportHelper.Column<>("Purchase Cost", equipment -> CurrencyFormatHelper.formatLocalCurrency(equipment.getPurchaseCost())),
                new ReportExportHelper.Column<>("Location", Equipment::getLocation),
                new ReportExportHelper.Column<>("Warranty Expiry", Equipment::getWarrantyExpiry),
                new ReportExportHelper.Column<>("Supplier", Equipment::getSupplier),
                new ReportExportHelper.Column<>("Date", Equipment::getEntryDate)
        );
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

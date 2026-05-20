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

public class MaintenanceReportController implements Initializable {

    @FXML private ComboBox<String> cmbAssetCode;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private TableView<MaintenanceRecord> tableMaintenance;
    @FXML private TableColumn<MaintenanceRecord, Void> colNo;
    @FXML private TableColumn<MaintenanceRecord, String> colAssetCode;
    @FXML private TableColumn<MaintenanceRecord, String> colIssue;
    @FXML private TableColumn<MaintenanceRecord, String> colActionTaken;
    @FXML private TableColumn<MaintenanceRecord, String> colPerformedBy;
    @FXML private TableColumn<MaintenanceRecord, String> colDate;
    @FXML private TableColumn<MaintenanceRecord, String> colCost;
    @FXML private TableColumn<MaintenanceRecord, String> colStatus;

    private final ObservableList<MaintenanceRecord> data = FXCollections.observableArrayList();
    private List<MaintenanceRecord> allMaintenance = List.of();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        TableNumbering.install(colNo);
        colAssetCode.setCellValueFactory(cell -> cell.getValue().assetCodeProperty());
        colIssue.setCellValueFactory(cell -> cell.getValue().issueProperty());
        colActionTaken.setCellValueFactory(cell -> cell.getValue().actionTakenProperty());
        colPerformedBy.setCellValueFactory(cell -> cell.getValue().performedByProperty());
        colDate.setCellValueFactory(cell -> cell.getValue().maintenanceDateProperty());
        colCost.setCellValueFactory(cell -> cell.getValue().costProperty());
        CurrencyFormatHelper.installCurrencyCellFactory(colCost);
        colStatus.setCellValueFactory(cell -> cell.getValue().statusProperty());

        setupContextMenu();
        loadData();
        loadFilters();
    }

    @FXML
    private void handleFilter(ActionEvent event) {
        String assetCode = cmbAssetCode.getValue();
        String status = cmbStatus.getValue();
        data.clear();
        for (MaintenanceRecord record : allMaintenance) {
            if (!matchesFilter(record.getAssetCode(), assetCode)) {
                continue;
            }
            if (!matchesFilter(record.getStatus(), status)) {
                continue;
            }
            if (!ReportFilterHelper.matchesDateRange(record.getMaintenanceDate(), dpFrom, dpTo)) {
                continue;
            }
            data.add(record);
        }
        tableMaintenance.setItems(data);
    }

    @FXML
    private void handleExport(ActionEvent event) {
        if (data.isEmpty()) {
            showAlert("No Data", "There is no maintenance data to export.");
            return;
        }
        ReportExportHelper.exportCsv("maintenance_report", "Maintenance Report", new ArrayList<>(data), columns());
    }

    @FXML
    private void handleExportPdf(ActionEvent event) {
        if (data.isEmpty()) {
            showAlert("No Data", "There is no maintenance data to export.");
            return;
        }
        ReportExportHelper.exportPdf("maintenance_report", "Maintenance Report", new ArrayList<>(data), columns());
    }

    private void loadData() {
        allMaintenance = new ArrayList<>(DatabaseHandler.getMaintenanceRecords());
        data.setAll(allMaintenance);
        tableMaintenance.setItems(data);
    }

    private void loadFilters() {
        cmbAssetCode.getItems().clear();
        cmbStatus.getItems().clear();
        for (MaintenanceRecord record : allMaintenance) {
            addIfMissing(cmbAssetCode, record.getAssetCode());
            addIfMissing(cmbStatus, record.getStatus());
        }
    }

    private void handleRefresh() {
        cmbAssetCode.setValue(null);
        cmbStatus.setValue(null);
        dpFrom.setValue(null);
        dpTo.setValue(null);
        loadData();
        loadFilters();
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem refresh = new MenuItem("Refresh Maintenance Report");
        refresh.setOnAction(event -> handleRefresh());
        menu.getItems().add(refresh);
        tableMaintenance.setContextMenu(menu);
    }

    private List<ReportExportHelper.Column<MaintenanceRecord>> columns() {
        return List.of(
                new ReportExportHelper.Column<>("Asset Code", MaintenanceRecord::getAssetCode),
                new ReportExportHelper.Column<>("Issue", MaintenanceRecord::getIssue),
                new ReportExportHelper.Column<>("Action Taken", MaintenanceRecord::getActionTaken),
                new ReportExportHelper.Column<>("Performed By", MaintenanceRecord::getPerformedBy),
                new ReportExportHelper.Column<>("Date", MaintenanceRecord::getMaintenanceDate),
                new ReportExportHelper.Column<>("Cost", record -> CurrencyFormatHelper.formatLocalCurrency(record.getCost())),
                new ReportExportHelper.Column<>("Status", MaintenanceRecord::getStatus)
        );
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
        OperationFeedbackHelper.showInfo(title, message);
    }
}

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

public class DistributionReportController implements Initializable {

    private final ReportService reportService = ServiceRegistry.getReportService();

    @FXML private ComboBox<String> cmbPerson;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private TableView<Distribution> tableDistribution;
    @FXML private TableColumn<Distribution, Void> colNo;
    @FXML private TableColumn<Distribution, String> colAssetCode;
    @FXML private TableColumn<Distribution, String> colResponsiblePerson;
    @FXML private TableColumn<Distribution, String> colAssignedTo;
    @FXML private TableColumn<Distribution, String> colPhone;
    @FXML private TableColumn<Distribution, String> colNID;
    @FXML private TableColumn<Distribution, String> colStatus;
    @FXML private TableColumn<Distribution, String> colDate;

    private final ObservableList<Distribution> data = FXCollections.observableArrayList();
    private List<Distribution> allDistributions = List.of();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        TableNumbering.install(colNo);
        colAssetCode.setCellValueFactory(new PropertyValueFactory<>("assetCode"));
        colResponsiblePerson.setCellValueFactory(new PropertyValueFactory<>("responsiblePerson"));
        colAssignedTo.setCellValueFactory(new PropertyValueFactory<>("assignedTo"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colNID.setCellValueFactory(new PropertyValueFactory<>("nid"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        setupContextMenu();
        tableDistribution.setItems(data);
        loadDataAsync(false);
    }

    private void loadPeople() {
        cmbPerson.getItems().clear();
        cmbStatus.getItems().clear();
        for (Distribution distribution : allDistributions) {
            addIfMissing(cmbPerson, distribution.getAssignedTo());
            addIfMissing(cmbStatus, distribution.getStatus());
        }
    }

    private void loadData() {
        try {
            allDistributions = reportService.getDistributionReport();
            data.setAll(allDistributions);
        } catch (Exception e) {
            showAlert("Error", "Failed to load data:\n" + e.getMessage());
        }

        tableDistribution.setItems(data);
    }

    private void loadDataAsync(boolean showRefreshMessage) {
        tableDistribution.setDisable(true);
        UiBackgroundLoader.run(
                "distribution-report-loader",
                reportService::getDistributionReport,
                records -> {
                    allDistributions = records == null ? List.of() : records;
                    data.setAll(allDistributions);
                    tableDistribution.setItems(data);
                    loadPeople();
                    tableDistribution.setDisable(false);
                    if (showRefreshMessage) {
                        showAlert("Refresh", "Data refreshed successfully.");
                    }
                },
                error -> {
                    allDistributions = List.of();
                    data.clear();
                    tableDistribution.setItems(data);
                    tableDistribution.setDisable(false);
                    showAlert("Error", "Failed to load data:\n" + safeMessage(error));
                }
        );
    }

    @FXML
    private void handleFilter(ActionEvent event) {
        String person = cmbPerson.getValue();
        String status = cmbStatus.getValue();

        data.clear();
        for (Distribution distribution : allDistributions) {
            if (!matchesContains(distribution.getAssignedTo(), person)) {
                continue;
            }
            if (!matchesExact(distribution.getStatus(), status)) {
                continue;
            }
            if (!ReportFilterHelper.matchesDateRange(distribution.getDate(), dpFrom, dpTo)) {
                continue;
            }
            data.add(distribution);
        }

        tableDistribution.setItems(data);
    }

    private void handleRefresh() {
        cmbPerson.setValue(null);
        cmbStatus.setValue(null);
        dpFrom.setValue(null);
        dpTo.setValue(null);
        loadDataAsync(true);
    }

    @FXML
    private void handleExport(ActionEvent event) {
        if (data.isEmpty()) {
            showAlert("No Data", "No distribution data to export.");
            return;
        }

        ReportExportHelper.exportCsv("distribution_report", "Distribution Report", new ArrayList<>(data), columns());
    }

    @FXML
    private void handleExportPdf(ActionEvent event) {
        ReportExportHelper.exportPdf("distribution_report", "Distribution Report", new ArrayList<>(data), columns());
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem refresh = new MenuItem("Refresh Distribution List");
        refresh.setOnAction(event -> handleRefresh());
        menu.getItems().add(refresh);
        tableDistribution.setContextMenu(menu);
    }

    private List<ReportExportHelper.Column<Distribution>> columns() {
        return List.of(
                new ReportExportHelper.Column<>("Asset Code", Distribution::getAssetCode),
                new ReportExportHelper.Column<>("Responsible Person", Distribution::getResponsiblePerson),
                new ReportExportHelper.Column<>("Assigned To", Distribution::getAssignedTo),
                new ReportExportHelper.Column<>("Phone", Distribution::getPhone),
                new ReportExportHelper.Column<>("NID", Distribution::getNid),
                new ReportExportHelper.Column<>("Status", Distribution::getStatus),
                new ReportExportHelper.Column<>("Date", Distribution::getDate)
        );
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
        if (title != null && title.toLowerCase().contains("error")) {
            OperationFeedbackHelper.showError(title, message);
        } else {
            OperationFeedbackHelper.showInfo(title, message);
        }
    }

    private String safeMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? "The distribution report could not be loaded."
                : throwable.getMessage();
    }
}

package com.mycompany.msr.amis;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class OutstandingReportController implements Initializable {

    private final ReportService reportService = ServiceRegistry.getReportService();

    @FXML private ComboBox<String> cmbPerson;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private CheckBox chkOverdueOnly;
    @FXML private TableView<Distribution> tableOutstanding;
    @FXML private TableColumn<Distribution, Void> colNo;
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
        TableNumbering.install(colNo);
        colAssetCode.setCellValueFactory(new PropertyValueFactory<>("assetCode"));
        colAssignedTo.setCellValueFactory(new PropertyValueFactory<>("assignedTo"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colNID.setCellValueFactory(new PropertyValueFactory<>("nid"));
        colAssignmentId.setCellValueFactory(new PropertyValueFactory<>("assignmentId"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty(
                ReportFilterHelper.isOverdue(cellData.getValue().getDate())
                        ? "OVERDUE"
                        : cellData.getValue().getStatus()
        ));
        colOutstandingRemarks.setCellValueFactory(new PropertyValueFactory<>("outstandingRemarks"));
        colOutstandingRemarks.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    return;
                }
                setText(item == null || item.trim().isEmpty() ? "No outstanding reason recorded" : item);
            }
        });

        setupContextMenu();
        tableOutstanding.setItems(data);
        loadDataAsync(false);
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

    private void loadDataAsync(boolean showRefreshMessage) {
        tableOutstanding.setDisable(true);
        UiBackgroundLoader.run(
                "outstanding-report-loader",
                reportService::getOutstandingReport,
                records -> {
                    allOutstanding = records == null ? List.of() : records;
                    data.setAll(allOutstanding);
                    tableOutstanding.setItems(data);
                    loadPeople();
                    tableOutstanding.setDisable(false);
                    if (showRefreshMessage) {
                        showAlert("Refresh", "Outstanding data refreshed.");
                    }
                },
                error -> {
                    allOutstanding = List.of();
                    data.clear();
                    tableOutstanding.setItems(data);
                    tableOutstanding.setDisable(false);
                    showAlert("Error", "Failed to load data:\n" + safeMessage(error));
                }
        );
    }

    @FXML
    private void handleFilter(ActionEvent event) {
        String person = cmbPerson.getValue();

        data.clear();
        for (Distribution distribution : allOutstanding) {
            if (!matchesContains(distribution.getAssignedTo(), person)) {
                continue;
            }
            if (!ReportFilterHelper.matchesDateRange(distribution.getDate(), dpFrom, dpTo)) {
                continue;
            }
            if (chkOverdueOnly != null && chkOverdueOnly.isSelected()
                    && !ReportFilterHelper.isOverdue(distribution.getDate())) {
                continue;
            }
            data.add(distribution);
        }

        tableOutstanding.setItems(data);
    }

    private void handleRefresh() {
        cmbPerson.setValue(null);
        dpFrom.setValue(null);
        dpTo.setValue(null);
        chkOverdueOnly.setSelected(false);
        loadDataAsync(true);
    }

    @FXML
    private void handleExport(ActionEvent event) {
        if (data.isEmpty()) {
            showAlert("No Data", "No outstanding data to export.");
            return;
        }

        ReportExportHelper.exportCsv("outstanding_report", "Outstanding Equipment Report", new ArrayList<>(data), columns());
    }

    @FXML
    private void handleExportPdf(ActionEvent event) {
        ReportExportHelper.exportPdf("outstanding_report", "Outstanding Equipment Report", new ArrayList<>(data), columns());
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem refresh = new MenuItem("Refresh Outstanding Report");
        refresh.setOnAction(event -> handleRefresh());
        menu.getItems().add(refresh);
        tableOutstanding.setContextMenu(menu);
    }

    private void showAlert(String title, String message) {
        if (title != null && title.toLowerCase().contains("error")) {
            OperationFeedbackHelper.showError(title, message);
        } else {
            OperationFeedbackHelper.showInfo(title, message);
        }
    }

    private List<ReportExportHelper.Column<Distribution>> columns() {
        return List.of(
                new ReportExportHelper.Column<>("Asset Code", Distribution::getAssetCode),
                new ReportExportHelper.Column<>("Assigned To", Distribution::getAssignedTo),
                new ReportExportHelper.Column<>("Phone", Distribution::getPhone),
                new ReportExportHelper.Column<>("NID", Distribution::getNid),
                new ReportExportHelper.Column<>("Assignment ID", distribution -> Integer.toString(distribution.getAssignmentId())),
                new ReportExportHelper.Column<>("Date", Distribution::getDate),
                new ReportExportHelper.Column<>("Status", distribution -> ReportFilterHelper.isOverdue(distribution.getDate()) ? "OVERDUE" : distribution.getStatus()),
                new ReportExportHelper.Column<>("Outstanding Remarks", Distribution::getOutstandingRemarks)
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

    private String safeMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? "The outstanding report could not be loaded."
                : throwable.getMessage();
    }
}

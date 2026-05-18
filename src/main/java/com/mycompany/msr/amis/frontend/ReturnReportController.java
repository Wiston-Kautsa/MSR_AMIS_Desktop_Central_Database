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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.text.Text;

public class ReturnReportController implements Initializable {

    private final ReportService reportService = ServiceRegistry.getReportService();

    @FXML private ComboBox<String> cmbPerson;
    @FXML private ComboBox<String> cmbCondition;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private TableView<ReturnRecord> tableReturns;
    @FXML private TableColumn<ReturnRecord, Void> colNo;
    @FXML private TableColumn<ReturnRecord, String> colAssetCode;
    @FXML private TableColumn<ReturnRecord, String> colSerialNumber;
    @FXML private TableColumn<ReturnRecord, String> colEquipmentName;
    @FXML private TableColumn<ReturnRecord, String> colCategory;
    @FXML private TableColumn<ReturnRecord, String> colSource;
    @FXML private TableColumn<ReturnRecord, String> colResponsibleOfficer;
    @FXML private TableColumn<ReturnRecord, String> colDateTaken;
    @FXML private TableColumn<ReturnRecord, String> colReason;
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
        colSerialNumber.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colEquipmentName.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colSource.setCellValueFactory(new PropertyValueFactory<>("source"));
        colResponsibleOfficer.setCellValueFactory(new PropertyValueFactory<>("responsibleOfficer"));
        colDateTaken.setCellValueFactory(new PropertyValueFactory<>("dateTaken"));
        colReason.setCellValueFactory(new PropertyValueFactory<>("assignmentReason"));
        colReturnedBy.setCellValueFactory(new PropertyValueFactory<>("returnedBy"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colNID.setCellValueFactory(new PropertyValueFactory<>("nid"));
        colCondition.setCellValueFactory(new PropertyValueFactory<>("returnCondition"));
        colRemarks.setCellValueFactory(new PropertyValueFactory<>("remarks"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("returnDate"));

        configureTableAppearance();
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
            if (!ReportFilterHelper.matchesDateRange(record.getReturnDate(), dpFrom, dpTo)) {
                continue;
            }
            data.add(record);
        }

        tableReturns.setItems(data);
    }

    private void handleRefresh() {
        cmbPerson.setValue(null);
        cmbCondition.setValue(null);
        dpFrom.setValue(null);
        dpTo.setValue(null);
        loadData();
        loadPeople();
        showAlert("Refresh", "Return report refreshed successfully.");
    }

    @FXML
    private void handleExport(ActionEvent event) {
        if (data.isEmpty()) {
            showAlert("No Data", "No return data to export.");
            return;
        }

        ReportExportHelper.exportCsv("return_report", "Return Report", new ArrayList<>(data), columns());
    }

    @FXML
    private void handleExportPdf(ActionEvent event) {
        ReportExportHelper.exportPdf("return_report", "Return Report", new ArrayList<>(data), columns());
    }

    private List<ReportExportHelper.Column<ReturnRecord>> columns() {
        return List.of(
                new ReportExportHelper.Column<>("Asset Code", ReturnRecord::getAssetCode),
                new ReportExportHelper.Column<>("IMEI/Serial Number", ReturnRecord::getSerialNumber),
                new ReportExportHelper.Column<>("Equipment Name", ReturnRecord::getEquipmentName),
                new ReportExportHelper.Column<>("Category", ReturnRecord::getCategory),
                new ReportExportHelper.Column<>("Source", ReturnRecord::getSource),
                new ReportExportHelper.Column<>("Responsible Officer", ReturnRecord::getResponsibleOfficer),
                new ReportExportHelper.Column<>("Date Given Out", ReturnRecord::getDateTaken),
                new ReportExportHelper.Column<>("Reason", ReturnRecord::getAssignmentReason),
                new ReportExportHelper.Column<>("Returned By", ReturnRecord::getReturnedBy),
                new ReportExportHelper.Column<>("Phone", ReturnRecord::getPhone),
                new ReportExportHelper.Column<>("NID", ReturnRecord::getNid),
                new ReportExportHelper.Column<>("Return Condition", ReturnRecord::getReturnCondition),
                new ReportExportHelper.Column<>("Remarks", ReturnRecord::getRemarks),
                new ReportExportHelper.Column<>("Date Returned", ReturnRecord::getReturnDate)
        );
    }

    private void configureTableAppearance() {
        tableReturns.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        installWrappingCell(colSerialNumber);
        installWrappingCell(colEquipmentName);
        installWrappingCell(colSource);
        installWrappingCell(colResponsibleOfficer);
        installWrappingCell(colReason);
        installWrappingCell(colReturnedBy);
        installWrappingCell(colRemarks);
    }

    private void installWrappingCell(TableColumn<ReturnRecord, String> column) {
        column.setCellFactory(tableColumn -> new TableCell<>() {
            private final Text text = new Text();

            {
                text.wrappingWidthProperty().bind(tableColumn.widthProperty().subtract(24));
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
                    text.setText(item);
                    setGraphic(text);
                }
            }
        });
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem refresh = new MenuItem("Refresh Return Report");
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

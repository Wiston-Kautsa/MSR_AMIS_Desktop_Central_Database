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

public class AssignmentReportController implements Initializable {

    private final ReportService reportService = ServiceRegistry.getReportService();

    @FXML private ComboBox<String> cmbPerson;
    @FXML private ComboBox<String> cmbEquipmentType;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private TableView<Assignment> tableAssignments;
    @FXML private TableColumn<Assignment, Void> colNo;
    @FXML private TableColumn<Assignment, String> colPerson;
    @FXML private TableColumn<Assignment, String> colDepartment;
    @FXML private TableColumn<Assignment, String> colEquipment;
    @FXML private TableColumn<Assignment, String> colReason;
    @FXML private TableColumn<Assignment, Integer> colQuantity;
    @FXML private TableColumn<Assignment, String> colStatus;
    @FXML private TableColumn<Assignment, String> colDate;

    private final ObservableList<Assignment> data = FXCollections.observableArrayList();
    private List<Assignment> allAssignments = List.of();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        TableNumbering.install(colNo);
        colPerson.setCellValueFactory(new PropertyValueFactory<>("person"));
        colDepartment.setCellValueFactory(new PropertyValueFactory<>("department"));
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentType"));
        colReason.setCellValueFactory(new PropertyValueFactory<>("reason"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        setupContextMenu();
        loadData();
        loadFilters();
    }

    private void loadData() {
        try {
            allAssignments = reportService.getAssignmentReport();
            data.setAll(allAssignments);
        } catch (Exception e) {
            showAlert("Error", "Failed to load data:\n" + e.getMessage());
        }
        tableAssignments.setItems(data);
    }

    private void loadFilters() {
        cmbPerson.getItems().clear();
        cmbEquipmentType.getItems().clear();
        cmbStatus.getItems().clear();

        for (Assignment assignment : allAssignments) {
            addIfMissing(cmbPerson, assignment.getPerson());
            addIfMissing(cmbEquipmentType, assignment.getEquipmentType());
            addIfMissing(cmbStatus, assignment.getStatus());
        }
    }

    @FXML
    private void handleFilter(ActionEvent event) {
        String person = cmbPerson.getValue();
        String type = cmbEquipmentType.getValue();
        String status = cmbStatus.getValue();

        data.clear();
        for (Assignment assignment : allAssignments) {
            if (!matchesFilter(assignment.getPerson(), person)) {
                continue;
            }
            if (!matchesFilter(assignment.getEquipmentType(), type)) {
                continue;
            }
            if (!matchesFilter(assignment.getStatus(), status)) {
                continue;
            }
            if (!ReportFilterHelper.matchesDateRange(assignment.getDate(), dpFrom, dpTo)) {
                continue;
            }
            data.add(assignment);
        }

        tableAssignments.setItems(data);
    }

    private void handleRefresh() {
        cmbPerson.setValue(null);
        cmbEquipmentType.setValue(null);
        cmbStatus.setValue(null);
        dpFrom.setValue(null);
        dpTo.setValue(null);
        loadData();
        loadFilters();
        showAlert("Refresh", "Assignment report refreshed successfully.");
    }

    @FXML
    private void handleExport(ActionEvent event) {
        if (data.isEmpty()) {
            showAlert("No Data", "No assignment data to export.");
            return;
        }

        ReportExportHelper.exportCsv("assignment_report", "Assignment Report", new ArrayList<>(data), columns());
    }

    @FXML
    private void handleExportPdf(ActionEvent event) {
        ReportExportHelper.exportPdf("assignment_report", "Assignment Report", new ArrayList<>(data), columns());
    }

    private List<ReportExportHelper.Column<Assignment>> columns() {
        return List.of(
                new ReportExportHelper.Column<>("Responsible Person", Assignment::getPerson),
                new ReportExportHelper.Column<>("Department", Assignment::getDepartment),
                new ReportExportHelper.Column<>("Equipment", Assignment::getEquipmentType),
                new ReportExportHelper.Column<>("Reason", Assignment::getReason),
                new ReportExportHelper.Column<>("Quantity", assignment -> Integer.toString(assignment.getQuantity())),
                new ReportExportHelper.Column<>("Status", Assignment::getStatus),
                new ReportExportHelper.Column<>("Date", Assignment::getDate)
        );
    }

    private boolean matchesFilter(String value, String filter) {
        return filter == null || filter.isBlank() || (value != null && value.equalsIgnoreCase(filter));
    }

    private void addIfMissing(ComboBox<String> comboBox, String value) {
        if (value == null || value.isBlank() || comboBox.getItems().contains(value)) {
            return;
        }
        comboBox.getItems().add(value);
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem refresh = new MenuItem("Refresh Assignment Report");
        refresh.setOnAction(event -> handleRefresh());
        menu.getItems().add(refresh);
        tableAssignments.setContextMenu(menu);
    }

    private void showAlert(String title, String message) {
        if (title != null && title.toLowerCase().contains("error")) {
            OperationFeedbackHelper.showError(title, message);
        } else {
            OperationFeedbackHelper.showInfo(title, message);
        }
    }
}

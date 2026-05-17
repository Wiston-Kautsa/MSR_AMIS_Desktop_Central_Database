package com.mycompany.msr.amis;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

public class AuditLogsController implements Initializable {

    @FXML private ComboBox<String> cmbAction;
    @FXML private ComboBox<String> cmbUsername;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private Label lblAuditScope;
    @FXML private Label lblAuditStatus;
    @FXML private TableView<AuditLog> tableAuditLogs;
    @FXML private TableColumn<AuditLog, Void> colNo;
    @FXML private TableColumn<AuditLog, Number> colId;
    @FXML private TableColumn<AuditLog, String> colUsername;
    @FXML private TableColumn<AuditLog, String> colAction;
    @FXML private TableColumn<AuditLog, String> colModule;
    @FXML private TableColumn<AuditLog, String> colDetails;
    @FXML private TableColumn<AuditLog, String> colActionTime;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
        configureTable();
        configureScope();
        setupContextMenu();
        loadFilters();
        loadLogs();
    }

    @FXML
    private void handleFilter(ActionEvent event) {
        loadLogs();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        if (cmbAction != null) {
            cmbAction.setValue(null);
        }
        if (Session.hasRole(AccessControl.ROLE_SUPER_ADMIN) && cmbUsername != null) {
            cmbUsername.setValue(null);
        }
        if (dpFrom != null) {
            dpFrom.setValue(null);
        }
        if (dpTo != null) {
            dpTo.setValue(null);
        }
        loadFilters();
        loadLogs();
        updateStatus("Audit filters reset and logs refreshed.");
    }

    @FXML
    private void handleExport(ActionEvent event) {
        ObservableList<AuditLog> rows = tableAuditLogs.getItems();
        if (rows == null || rows.isEmpty()) {
            updateStatus("No audit logs available to export.");
            OperationFeedbackHelper.showWarning(
                    "No Audit Logs",
                    "There are no audit logs to export."
            );
            return;
        }

        ReportExportHelper.exportCsv("audit_logs", "Audit Logs", new ArrayList<>(rows), columns());
    }

    @FXML
    private void handleExportPdf(ActionEvent event) {
        ObservableList<AuditLog> rows = tableAuditLogs.getItems();
        ReportExportHelper.exportPdf("audit_logs", "Audit Logs", new ArrayList<>(rows), columns());
    }

    private void configureTable() {
        TableNumbering.install(colNo);
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colAction.setCellValueFactory(new PropertyValueFactory<>("action"));
        colModule.setCellValueFactory(new PropertyValueFactory<>("moduleName"));
        colDetails.setCellValueFactory(new PropertyValueFactory<>("details"));
        colActionTime.setCellValueFactory(new PropertyValueFactory<>("actionTime"));
    }

    private void configureScope() {
        if (Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)) {
            lblAuditScope.setText("Showing all system audit logs.");
            cmbUsername.setDisable(false);
            cmbUsername.setManaged(true);
            cmbUsername.setVisible(true);
        } else {
            String actor = currentUsername();
            lblAuditScope.setText("Showing audit logs for " + actor + " only.");
            cmbUsername.setDisable(true);
            cmbUsername.setManaged(false);
            cmbUsername.setVisible(false);
        }
    }

    private void loadFilters() {
        ObservableList<AuditLog> source = Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)
                ? AuditService.getAllLogs()
                : AuditService.getLogsByUsername(currentUsername());

        Set<String> actions = new LinkedHashSet<>();
        Set<String> usernames = new LinkedHashSet<>();
        for (AuditLog log : source) {
            if (!log.getAction().isBlank()) {
                actions.add(log.getAction());
            }
            if (!log.getUsername().isBlank()) {
                usernames.add(log.getUsername());
            }
        }

        cmbAction.getItems().setAll(actions);
        if (Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)) {
            cmbUsername.getItems().setAll(usernames);
        }
    }

    private void loadLogs() {
        ObservableList<AuditLog> logs = Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)
                ? AuditService.getAllLogs()
                : AuditService.getLogsByUsername(currentUsername());

        String selectedAction = cmbAction.getValue();
        String selectedUsername = Session.hasRole(AccessControl.ROLE_SUPER_ADMIN) ? cmbUsername.getValue() : currentUsername();

        ObservableList<AuditLog> filtered = logs.filtered(log ->
                matches(log.getAction(), selectedAction) &&
                        matches(log.getUsername(), selectedUsername) &&
                        ReportFilterHelper.matchesDateRange(normalizeAuditDate(log.getActionTime()), dpFrom, dpTo)
        );

        tableAuditLogs.setItems(filtered);
        updateStatus("Loaded " + filtered.size() + " audit log entries.");
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem refresh = new MenuItem("Reset Filters and Refresh");
        refresh.setOnAction(event -> handleRefresh(null));

        MenuItem export = new MenuItem("Export Audit Logs as CSV");
        export.setOnAction(event -> handleExport(null));

        MenuItem exportPdf = new MenuItem("Export Audit Logs as PDF");
        exportPdf.setOnAction(event -> handleExportPdf(null));

        menu.getItems().addAll(refresh, export, exportPdf);
        tableAuditLogs.setContextMenu(menu);
    }

    private boolean matches(String value, String selected) {
        return selected == null || selected.isBlank() || selected.equalsIgnoreCase(value);
    }

    private void updateStatus(String message) {
        if (lblAuditStatus != null) {
            lblAuditStatus.setText(message == null ? "" : message);
        }
    }

    private String currentUsername() {
        User user = Session.getCurrentUser();
        if (user == null) {
            return "unknown_user";
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim();
        }
        return user.getUsername() == null || user.getUsername().isBlank()
                ? "unknown_user"
                : user.getUsername().trim();
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? "Unexpected error."
                : e.getMessage();
    }

    private List<ReportExportHelper.Column<AuditLog>> columns() {
        return List.of(
                new ReportExportHelper.Column<>("ID", log -> Integer.toString(log.getId())),
                new ReportExportHelper.Column<>("Username", AuditLog::getUsername),
                new ReportExportHelper.Column<>("Action", AuditLog::getAction),
                new ReportExportHelper.Column<>("Module", AuditLog::getModuleName),
                new ReportExportHelper.Column<>("Details", AuditLog::getDetails),
                new ReportExportHelper.Column<>("Action Time", AuditLog::getActionTime)
        );
    }

    private String normalizeAuditDate(String value) {
        if (value == null || value.length() < 10) {
            return "";
        }
        return value.substring(0, 10);
    }
}

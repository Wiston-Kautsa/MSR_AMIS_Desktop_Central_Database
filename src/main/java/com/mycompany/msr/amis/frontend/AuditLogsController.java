package com.mycompany.msr.amis;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.concurrent.Task;
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

    private static final int INITIAL_LOG_LIMIT = 500;

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

    private ObservableList<AuditLog> cachedLogs = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
        configureTable();
        configureScope();
        setupContextMenu();
        tableAuditLogs.setItems(FXCollections.observableArrayList());
        loadLogsAsync(false);
    }

    @FXML
    private void handleFilter(ActionEvent event) {
        applyCurrentFilters();
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
        loadLogsAsync(true);
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
        lblAuditScope.setText("Showing all system audit logs.");
        cmbUsername.setDisable(false);
        cmbUsername.setManaged(true);
        cmbUsername.setVisible(true);
    }

    private void loadFilters(ObservableList<AuditLog> source) {
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
        cmbUsername.getItems().setAll(usernames);
    }

    private void applyCurrentFilters() {
        String selectedAction = cmbAction.getValue();
        String selectedUsername = cmbUsername.getValue();

        ObservableList<AuditLog> filtered = cachedLogs.filtered(log ->
                matches(log.getAction(), selectedAction) &&
                        matches(log.getUsername(), selectedUsername) &&
                        ReportFilterHelper.matchesDateRange(normalizeAuditDate(log.getActionTime()), dpFrom, dpTo)
        );

        tableAuditLogs.setItems(filtered);
        updateStatus("Showing " + filtered.size() + " of " + cachedLogs.size() + " recent audit log entries.");
    }

    private void loadLogsAsync(boolean refreshRequested) {
        updateStatus(refreshRequested
                ? "Refreshing recent audit logs..."
                : "Loading recent audit logs...");
        setControlsDisabled(true);

        Task<ObservableList<AuditLog>> task = new Task<>() {
            @Override
            protected ObservableList<AuditLog> call() {
                return AuditService.getRecentLogs(INITIAL_LOG_LIMIT);
            }
        };

        task.setOnSucceeded(event -> {
            cachedLogs = task.getValue() == null
                    ? FXCollections.observableArrayList()
                    : task.getValue();
            loadFilters(cachedLogs);
            applyCurrentFilters();
            setControlsDisabled(false);
        });
        task.setOnFailed(event -> {
            setControlsDisabled(false);
            updateStatus("Audit logs could not be loaded: " + safeMessage(task.getException()));
        });

        Thread worker = new Thread(task, "audit-logs-loader");
        worker.setDaemon(true);
        worker.start();
    }

    private void setControlsDisabled(boolean disabled) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> setControlsDisabled(disabled));
            return;
        }
        if (cmbAction != null) {
            cmbAction.setDisable(disabled);
        }
        if (cmbUsername != null) {
            cmbUsername.setDisable(disabled);
        }
        if (dpFrom != null) {
            dpFrom.setDisable(disabled);
        }
        if (dpTo != null) {
            dpTo.setDisable(disabled);
        }
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

    private String safeMessage(Throwable e) {
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

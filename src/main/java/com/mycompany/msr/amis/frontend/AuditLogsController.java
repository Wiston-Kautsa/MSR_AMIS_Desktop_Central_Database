package com.mycompany.msr.amis;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.ResourceBundle;
import java.util.Set;

public class AuditLogsController implements Initializable {

    @FXML private ComboBox<String> cmbAction;
    @FXML private ComboBox<String> cmbUsername;
    @FXML private Label lblAuditScope;
    @FXML private Label lblAuditStatus;
    @FXML private TableView<AuditLog> tableAuditLogs;
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
        loadFilters();
        loadLogs();
        updateStatus("Audit logs refreshed.");
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

        try {
            File file = FileLocationHelper.fileInDownloads("audit_logs.csv");
            try (FileWriter writer = new FileWriter(file)) {
                writer.append("ID,Username,Action,Module,Details,Action Time\n");
                for (AuditLog log : rows) {
                    writer.append(String.valueOf(log.getId())).append(",")
                            .append(csvSafe(log.getUsername())).append(",")
                            .append(csvSafe(log.getAction())).append(",")
                            .append(csvSafe(log.getModuleName())).append(",")
                            .append(csvSafe(log.getDetails())).append(",")
                            .append(csvSafe(log.getActionTime())).append("\n");
                }
            }
            updateStatus("Audit logs exported to " + file.getAbsolutePath());
            OperationFeedbackHelper.showInfo(
                    "Export Complete",
                    "Audit logs exported successfully to:\n" + file.getAbsolutePath()
            );
        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("Audit log export failed: " + safeMessage(e));
            OperationFeedbackHelper.showError(
                    "Export Failed",
                    "Audit logs could not be exported.\n\n" + safeMessage(e)
            );
        }
    }

    private void configureTable() {
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
                        matches(log.getUsername(), selectedUsername)
        );

        tableAuditLogs.setItems(filtered);
        updateStatus("Loaded " + filtered.size() + " audit log entries.");
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem refresh = new MenuItem("Refresh Audit Logs");
        refresh.setOnAction(event -> handleRefresh(null));

        MenuItem export = new MenuItem("Export Audit Logs");
        export.setOnAction(event -> handleExport(null));

        menu.getItems().addAll(refresh, export);
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

    private String csvSafe(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? "Unexpected error."
                : e.getMessage();
    }
}

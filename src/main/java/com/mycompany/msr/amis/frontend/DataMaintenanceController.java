package com.mycompany.msr.amis;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.control.TitledPane;

public class DataMaintenanceController implements Initializable {

    private final DataMaintenanceService dataMaintenanceService = ServiceRegistry.getDataMaintenanceService();

    private static final Map<String, String> CONFIRMATION_PHRASES = new LinkedHashMap<>();

    static {
        CONFIRMATION_PHRASES.put("RETURNS", "RESET RETURNS DATA");
        CONFIRMATION_PHRASES.put("DISTRIBUTION", "RESET DISTRIBUTION DATA");
        CONFIRMATION_PHRASES.put("ASSIGNMENTS", "RESET ASSIGNMENTS DATA");
        CONFIRMATION_PHRASES.put("EQUIPMENT", "RESET EQUIPMENT DATA");
        CONFIRMATION_PHRASES.put("AUDIT_LOG", "RESET AUDIT LOG DATA");
        CONFIRMATION_PHRASES.put("FULL_OPERATIONAL_DATA", "RESET FULL OPERATIONAL DATA");
    }

    @FXML private Label lblEquipmentCount;
    @FXML private Label lblAssignmentCount;
    @FXML private Label lblDistributionCount;
    @FXML private Label lblReturnCount;
    @FXML private Label lblAuditLogCount;
    @FXML private Label lblMaintenanceStatus;
    @FXML private Label lblPageSubtitle;
    @FXML private TextField txtReturnsConfirmation;
    @FXML private TextField txtDistributionConfirmation;
    @FXML private TextField txtAssignmentsConfirmation;
    @FXML private TextField txtEquipmentConfirmation;
    @FXML private TextField txtAuditLogConfirmation;
    @FXML private TextField txtFullOperationalConfirmation;
    @FXML private Button btnResetReturns;
    @FXML private Button btnResetDistribution;
    @FXML private Button btnResetAssignments;
    @FXML private Button btnResetEquipment;
    @FXML private Button btnResetAuditLog;
    @FXML private Button btnResetFullOperational;
    @FXML private TitledPane summaryPane;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)) {
            throw new SecurityException("Data Maintenance is available only to Super Admin.");
        }
        configureModeText();
        configureConfirmationFields();
        refreshSummary();
    }

    @FXML
    private void handleRefresh() {
        refreshSummary();
    }

    @FXML
    private void handleResetReturns() {
        resetComponent("RETURNS", "Reset Returns Data");
    }

    @FXML
    private void handleResetDistribution() {
        resetComponent("DISTRIBUTION", "Reset Distribution Data");
    }

    @FXML
    private void handleResetAssignments() {
        resetComponent("ASSIGNMENTS", "Reset Assignments Data");
    }

    @FXML
    private void handleResetEquipment() {
        resetComponent("EQUIPMENT", "Reset Equipment Data");
    }

    @FXML
    private void handleResetAuditLog() {
        resetComponent("AUDIT_LOG", "Reset Audit Log Data");
    }

    @FXML
    private void handleResetFullOperationalData() {
        resetComponent("FULL_OPERATIONAL_DATA", "Reset Full Operational Data");
    }

    private void refreshSummary() {
        try {
            DataMaintenanceSummary summary = dataMaintenanceService.getSummary();
            lblEquipmentCount.setText(Integer.toString(summary.getEquipmentCount()));
            lblAssignmentCount.setText(Integer.toString(summary.getAssignmentCount()));
            lblDistributionCount.setText(Integer.toString(summary.getDistributionCount()));
            lblReturnCount.setText(Integer.toString(summary.getReturnCount()));
            lblAuditLogCount.setText(Integer.toString(summary.getAuditLogCount()));
            updateStatus(modePrefix() + " data maintenance counts refreshed.");
        } catch (Exception e) {
            e.printStackTrace();
            updateStatus(resolveMessage(e));
            OperationFeedbackHelper.showError("Load Failed", resolveMessage(e));
        }
    }

    private void resetComponent(String component, String title) {
        String phrase = CONFIRMATION_PHRASES.get(component);
        if (phrase == null) {
            OperationFeedbackHelper.showError("Configuration Error", "No confirmation phrase is configured for " + component + ".");
            return;
        }
        TextField confirmationField = confirmationFieldFor(component);
        if (confirmationField == null || !phrase.equalsIgnoreCase(confirmationField.getText().trim())) {
            updateStatus("Confirmation phrase does not match for " + title + ".");
            return;
        }

        try {
            String message = dataMaintenanceService.resetComponent(component);
            refreshSummary();
            confirmationField.clear();
            updateConfirmationButtonState(component);
            updateStatus(message);
        } catch (Exception e) {
            e.printStackTrace();
            String message = resolveMessage(e);
            updateStatus(message);
        }
    }

    private void updateStatus(String message) {
        if (lblMaintenanceStatus != null) {
            lblMaintenanceStatus.setText(message == null ? "" : message);
        }
    }

    private void configureModeText() {
        boolean localMode = ServiceRegistry.getConfiguration().usesLocalDatabase();
        if (lblPageSubtitle != null) {
            lblPageSubtitle.setText(localMode
                    ? "Super Admin tools for controlled operational data maintenance in the local SQLite database with audit tracing"
                    : "Super Admin tools for controlled operational data maintenance in the central PostgreSQL database with audit tracing");
        }
        if (summaryPane != null) {
            summaryPane.setText(localMode ? "Local Data Summary" : "Remote Data Summary");
        }
        updateStatus(localMode
                ? "Local SQLite maintenance is ready."
                : "Remote PostgreSQL maintenance is ready.");
    }

    private String modePrefix() {
        return ServiceRegistry.getConfiguration().usesLocalDatabase() ? "Local" : "Remote";
    }

    private String resolveMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "The data maintenance operation failed.";
        }
        String message = exception.getMessage();
        if (message.toLowerCase().contains("connection refused")
                || message.toLowerCase().contains("getsockopt")) {
            return "API is not reachable. Start the API server and try again.";
        }
        return message;
    }

    private void configureConfirmationFields() {
        bindConfirmation("RETURNS", txtReturnsConfirmation);
        bindConfirmation("DISTRIBUTION", txtDistributionConfirmation);
        bindConfirmation("ASSIGNMENTS", txtAssignmentsConfirmation);
        bindConfirmation("EQUIPMENT", txtEquipmentConfirmation);
        bindConfirmation("AUDIT_LOG", txtAuditLogConfirmation);
        bindConfirmation("FULL_OPERATIONAL_DATA", txtFullOperationalConfirmation);
    }

    private void bindConfirmation(String component, TextField field) {
        if (field == null) {
            return;
        }
        field.textProperty().addListener((obs, oldValue, newValue) -> updateConfirmationButtonState(component));
        updateConfirmationButtonState(component);
    }

    private void updateConfirmationButtonState(String component) {
        String phrase = CONFIRMATION_PHRASES.get(component);
        TextField field = confirmationFieldFor(component);
        Button button = buttonFor(component);
        if (phrase == null || field == null || button == null) {
            return;
        }
        button.setDisable(!phrase.equalsIgnoreCase(field.getText().trim()));
    }

    private TextField confirmationFieldFor(String component) {
        switch (component) {
            case "RETURNS":
                return txtReturnsConfirmation;
            case "DISTRIBUTION":
                return txtDistributionConfirmation;
            case "ASSIGNMENTS":
                return txtAssignmentsConfirmation;
            case "EQUIPMENT":
                return txtEquipmentConfirmation;
            case "AUDIT_LOG":
                return txtAuditLogConfirmation;
            case "FULL_OPERATIONAL_DATA":
                return txtFullOperationalConfirmation;
            default:
                return null;
        }
    }

    private Button buttonFor(String component) {
        switch (component) {
            case "RETURNS":
                return btnResetReturns;
            case "DISTRIBUTION":
                return btnResetDistribution;
            case "ASSIGNMENTS":
                return btnResetAssignments;
            case "EQUIPMENT":
                return btnResetEquipment;
            case "AUDIT_LOG":
                return btnResetAuditLog;
            case "FULL_OPERATIONAL_DATA":
                return btnResetFullOperational;
            default:
                return null;
        }
    }
}

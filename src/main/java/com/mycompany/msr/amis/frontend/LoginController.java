package com.mycompany.msr.amis;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ResourceBundle;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class LoginController implements Initializable {

    private final AuthService authService = ServiceRegistry.getAuthService();

    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtPasswordVisible;
    @FXML private CheckBox chkShowPassword;
    @FXML private Label lblLoginStatus;
    @FXML private Label lblDefaultAdmin;

    @FXML private VBox loginPanel;
    @FXML private VBox resetPanel;
    @FXML private TextField txtResetIdentifier;
    @FXML private TextField txtResetCode;
    @FXML private PasswordField txtResetNewPassword;
    @FXML private PasswordField txtResetConfirmPassword;
    @FXML private Label lblResetStatus;
    @FXML private HBox resetStatusBanner;
    @FXML private Button btnSendResetCode;
    @FXML private Button btnResetPassword;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (txtPasswordVisible != null && txtPassword != null) {
            txtPasswordVisible.textProperty().bindBidirectional(txtPassword.textProperty());
        }

        if (chkShowPassword != null && txtPasswordVisible != null && txtPassword != null) {
            chkShowPassword.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                txtPasswordVisible.setVisible(isSelected);
                txtPasswordVisible.setManaged(isSelected);
                txtPassword.setVisible(!isSelected);
                txtPassword.setManaged(!isSelected);
            });
        }

        if (lblDefaultAdmin != null) {
            if (ServiceRegistry.getConfiguration().usesLocalDatabase()) {
                if (ServiceRegistry.getConfiguration().isUsingLocalFallback()) {
                    lblDefaultAdmin.setText(
                            "Automatic mode: central API is offline, so this session is using the local SQLite database. Offline changes are not pushed back to PostgreSQL automatically."
                    );
                } else {
                    lblDefaultAdmin.setText(
                            "Use your assigned account to sign in. If access is not yet configured, contact the system administrator."
                    );
                }
            } else {
                String prefix = ServiceRegistry.getConfiguration().isAutomaticMode()
                        ? "Automatic mode: central API is reachable. "
                        : "";
                lblDefaultAdmin.setText(
                        prefix + "Central API: " + ServiceRegistry.getConfiguration().getApiBaseUrl()
                                + " | Sign in with your assigned account. If access is not yet configured, contact the system administrator."
                );
            }
        }

        showLoginPanel();
    }

    @FXML
    private void handleLogin() {
        String email = txtEmail.getText() == null ? "" : txtEmail.getText().trim().toLowerCase();
        String password = txtPassword.getText() == null ? "" : txtPassword.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showStatus("Enter email and password.");
            return;
        }

        User user;
        try {
            user = authService.authenticate(email, password);
        } catch (SecurityException e) {
            showStatus(e.getMessage());
            return;
        } catch (RuntimeException e) {
            showStatus(e.getMessage());
            return;
        }
        if (user == null) {
            showStatus("Invalid email or password.");
            return;
        }

        try {
            Session.setCurrentUser(user);
            if (authService.isTemporarySetupAccount(user)) {
                Session.setSetupMode(true);
                App.showSetupUsersPage();
                return;
            }
            Session.setSetupMode(false);
            App.showDashboardPage();
        } catch (SecurityException e) {
            showStatus(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            showStatus("Login succeeded, but the dashboard could not be opened.");
        }
    }

    @FXML
    private void showResetPanel() {
        if (txtResetIdentifier != null && (txtResetIdentifier.getText() == null || txtResetIdentifier.getText().isBlank())) {
            txtResetIdentifier.setText(txtEmail.getText() == null ? "" : txtEmail.getText().trim().toLowerCase());
        }
        switchPanel("reset");
        clearResetInputFields(false);
        setResetStatus("Ready to send a reset code.", "info");
    }

    @FXML
    private void showLoginPanel() {
        switchPanel("login");
        if (chkShowPassword != null) {
            chkShowPassword.setSelected(false);
        }
    }

    @FXML
    private void handleSendResetCode() {
        String identifier = normalized(txtResetIdentifier.getText());
        if (identifier.isBlank()) {
            setResetStatus("Enter your registered email address or username.", "error");
            return;
        }

        btnSendResetCode.setDisable(true);
        btnResetPassword.setDisable(true);
        setResetFieldsEnabled(false);
        setResetStatus("Sending reset code to your registered account...", "progress");

        Task<String> sendResetTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                if (ServiceRegistry.getConfiguration().usesLocalDatabase()) {
                    String resetCode = PasswordUtils.generateNumericCode(6);
                    String recipientEmail = authService.issuePasswordResetCode(
                            identifier,
                            resetCode,
                            LocalDateTime.now().plusMinutes(10)
                    );
                    try {
                        EmailService.sendPasswordResetCode(recipientEmail, resetCode);
                        return "Reset code sent to " + maskEmail(recipientEmail) + ". It expires in 10 minutes.";
                    } catch (Exception e) {
                        authService.clearPasswordResetCode(identifier);
                        throw e;
                    }
                }
                return authService.issuePasswordResetCode(identifier, "", LocalDateTime.now().plusMinutes(10));
            }
        };

        sendResetTask.setOnSucceeded(workerStateEvent -> {
            btnSendResetCode.setDisable(false);
            setResetFieldsEnabled(true);
            btnResetPassword.setDisable(false);
            setResetStatus(sendResetTask.getValue(), "success");
        });

        sendResetTask.setOnFailed(workerStateEvent -> {
            btnSendResetCode.setDisable(false);
            btnResetPassword.setDisable(true);
            Throwable failure = sendResetTask.getException();
            setResetStatus(
                    failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
                            ? "Failed to send reset code."
                            : failure.getMessage(),
                    "error"
            );
        });

        Thread sendResetThread = new Thread(sendResetTask, "password-reset-email");
        sendResetThread.setDaemon(true);
        sendResetThread.start();
    }

    @FXML
    private void handleSubmitPasswordReset() {
        String identifier = normalized(txtResetIdentifier.getText());
        String resetCode = normalized(txtResetCode.getText());
        String newPassword = txtResetNewPassword.getText() == null ? "" : txtResetNewPassword.getText();
        String confirmPassword = txtResetConfirmPassword.getText() == null ? "" : txtResetConfirmPassword.getText();

        if (identifier.isBlank() || resetCode.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
            setResetStatus("Complete every field before resetting the password.", "error");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            setResetStatus("The new password and confirmation do not match.", "error");
            return;
        }

        try {
            authService.resetPasswordWithCode(identifier, resetCode, newPassword);
            Session.clear();
            txtEmail.setText(identifier);
            txtPassword.clear();
            clearResetInputFields(true);
            setResetStatus("Password updated successfully. Sign in with the new password.", "success");
            showStatus("Password reset successfully. Sign in with the new password.");
            showAlert("Saved", "Password reset saved. Sign in with the new password.");
            App.showLoginPage();
        } catch (Exception e) {
            setResetStatus(e.getMessage(), "error");
        }
    }

    @FXML
    private void clearResetFields() {
        clearResetInputFields(true);
        setResetStatus("Ready to send a reset code.", "info");
    }

    @FXML
    private void handleClear() {
        txtEmail.clear();
        txtPassword.clear();
        if (chkShowPassword != null) {
            chkShowPassword.setSelected(false);
        }
        showStatus("");
    }

    private void showStatus(String message) {
        if (lblLoginStatus != null) {
            lblLoginStatus.setText(message);
        }
    }

    private void switchPanel(String panel) {
        boolean showLogin = "login".equals(panel);
        boolean showReset = "reset".equals(panel);
        if (loginPanel != null) {
            loginPanel.setManaged(showLogin);
            loginPanel.setVisible(showLogin);
        }
        if (resetPanel != null) {
            resetPanel.setManaged(showReset);
            resetPanel.setVisible(showReset);
        }
    }

    private void clearResetInputFields(boolean clearIdentifier) {
        if (clearIdentifier && txtResetIdentifier != null) {
            txtResetIdentifier.clear();
        }
        if (txtResetCode != null) {
            txtResetCode.clear();
        }
        if (txtResetNewPassword != null) {
            txtResetNewPassword.clear();
        }
        if (txtResetConfirmPassword != null) {
            txtResetConfirmPassword.clear();
        }
        setResetFieldsEnabled(false);
        if (btnSendResetCode != null) {
            btnSendResetCode.setDisable(false);
        }
        if (btnResetPassword != null) {
            btnResetPassword.setDisable(true);
        }
    }

    private void setResetFieldsEnabled(boolean enabled) {
        if (txtResetCode != null) {
            txtResetCode.setDisable(!enabled);
        }
        if (txtResetNewPassword != null) {
            txtResetNewPassword.setDisable(!enabled);
        }
        if (txtResetConfirmPassword != null) {
            txtResetConfirmPassword.setDisable(!enabled);
        }
    }

    private void setResetStatus(String message, String tone) {
        if (lblResetStatus != null) {
            lblResetStatus.setText(message == null ? "" : message);
        }
        if (resetStatusBanner != null) {
            resetStatusBanner.getStyleClass().removeAll("status-info", "status-success", "status-error", "status-progress");
            switch (tone == null ? "" : tone) {
                case "success":
                    resetStatusBanner.getStyleClass().add("status-success");
                    break;
                case "progress":
                    resetStatusBanner.getStyleClass().add("status-progress");
                    break;
                case "error":
                    resetStatusBanner.getStyleClass().add("status-error");
                    break;
                default:
                    resetStatusBanner.getStyleClass().add("status-info");
                    break;
            }
        }
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private void showAlert(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String maskEmail(String email) {
        String normalized = normalized(email);
        int atIndex = normalized.indexOf('@');
        if (atIndex <= 1) {
            return normalized;
        }
        return normalized.charAt(0) + "***" + normalized.substring(atIndex - 1);
    }
}

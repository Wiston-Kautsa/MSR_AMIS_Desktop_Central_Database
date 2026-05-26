package com.mycompany.msr.amis;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class LoginController implements Initializable {

    private final AuthService authService = ServiceRegistry.getAuthService();
    private final RememberedLoginStore rememberedLoginStore = new RememberedLoginStore();
    private List<String> rememberedEmails = new ArrayList<>();
    private boolean applyingRememberedSelection;
    private boolean loginInProgress;

    @FXML private ComboBox<String> cmbEmail;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtPasswordVisible;
    @FXML private CheckBox chkShowPassword;
    @FXML private CheckBox chkRememberLogin;
    @FXML private Label lblLoginStatus;
    @FXML private Label lblLoginProgress;
    @FXML private Button btnLogin;
    @FXML private VBox loginProgressPanel;
    @FXML private ProgressBar loginProgressBar;

    @FXML private VBox loginPanel;
    @FXML private VBox resetPanel;
    @FXML private VBox initialPasswordPanel;
    @FXML private TextField txtResetIdentifier;
    @FXML private TextField txtResetCode;
    @FXML private PasswordField txtResetNewPassword;
    @FXML private PasswordField txtResetConfirmPassword;
    @FXML private Label lblResetStatus;
    @FXML private HBox resetStatusBanner;
    @FXML private Button btnSendResetCode;
    @FXML private Button btnResetPassword;
    @FXML private PasswordField txtInitialNewPassword;
    @FXML private PasswordField txtInitialConfirmPassword;
    @FXML private Label lblInitialPasswordStatus;
    @FXML private HBox initialPasswordStatusBanner;
    @FXML private Button btnInitialPasswordChange;
    private Timeline loginProgressTimeline;

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

        setupRememberedLogins();

        showLoginPanel();
    }

    @FXML
    private void handleLogin() {
        String email = getEmailInput();
        String password = txtPassword.getText() == null ? "" : txtPassword.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showStatus("Enter email and password.");
            return;
        }
        if (loginInProgress) {
            return;
        }
        loginInProgress = true;
        setLoginFormDisabled(true);
        startLoginProgress();
        showStatus("Checking account credentials...");

        Task<User> loginTask = new Task<>() {
            @Override
            protected User call() {
                return authService.authenticate(email, password);
            }
        };
        loginTask.setOnSucceeded(event -> finishLoginProgress(() -> completeLogin(email, password, loginTask.getValue())));
        loginTask.setOnFailed(event -> {
            loginInProgress = false;
            stopLoginProgress(false);
            setLoginFormDisabled(false);
            Throwable failure = loginTask.getException();
            showStatus(failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
                    ? "Login failed. Check your credentials and try again."
                    : failure.getMessage());
        });

        Thread loginThread = new Thread(loginTask, "login-authentication");
        loginThread.setDaemon(true);
        loginThread.start();
    }

    private void completeLogin(String email, String password, User user) {
        loginInProgress = false;
        setLoginFormDisabled(false);
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
            if (Session.isPasswordChangeRequired()) {
                showInitialPasswordPanel();
                return;
            }
            Session.setSetupMode(false);
            rememberSuccessfulLogin(email, password);
            App.showDashboardPage();
            if (ServiceRegistry.getConfiguration().usesLocalDatabase()) {
                mirrorCentralDataAfterLogin(user, password);
            }
        } catch (SecurityException e) {
            loginInProgress = false;
            showStatus(e.getMessage());
        } catch (IOException e) {
            loginInProgress = false;
            e.printStackTrace();
            showStatus("Login succeeded, but the dashboard could not be opened.");
        }
    }

    private void setLoginFormDisabled(boolean disabled) {
        if (btnLogin != null) {
            btnLogin.setDisable(disabled);
            btnLogin.setText(disabled ? "LOADING..." : "LOGIN");
        }
        if (cmbEmail != null) {
            cmbEmail.setDisable(disabled);
        }
        if (txtPassword != null) {
            txtPassword.setDisable(disabled);
        }
        if (txtPasswordVisible != null) {
            txtPasswordVisible.setDisable(disabled);
        }
    }

    private void startLoginProgress() {
        stopLoginProgress(false);
        setLoginProgressVisible(true);
        updateLoginProgress(0.0);

        loginProgressTimeline = new Timeline(new KeyFrame(Duration.millis(90), event -> {
            double current = loginProgressBar == null ? 0.0 : loginProgressBar.getProgress();
            double next = current < 0.35
                    ? current + 0.035
                    : current < 0.70
                    ? current + 0.018
                    : current + 0.007;
            updateLoginProgress(Math.min(next, 0.95));
        }));
        loginProgressTimeline.setCycleCount(Timeline.INDEFINITE);
        loginProgressTimeline.play();
    }

    private void finishLoginProgress(Runnable afterComplete) {
        if (loginProgressTimeline != null) {
            loginProgressTimeline.stop();
            loginProgressTimeline = null;
        }
        updateLoginProgress(1.0);
        showStatus("Loading complete. Opening system...");

        PauseTransition pause = new PauseTransition(Duration.millis(250));
        pause.setOnFinished(event -> afterComplete.run());
        pause.play();
    }

    private void stopLoginProgress(boolean keepVisible) {
        if (loginProgressTimeline != null) {
            loginProgressTimeline.stop();
            loginProgressTimeline = null;
        }
        if (!keepVisible) {
            setLoginProgressVisible(false);
            updateLoginProgress(0.0);
        }
    }

    private void updateLoginProgress(double progress) {
        double bounded = Math.max(0.0, Math.min(1.0, progress));
        if (loginProgressBar != null) {
            loginProgressBar.setProgress(bounded);
        }
        if (lblLoginProgress != null) {
            lblLoginProgress.setText(Math.round(bounded * 100) + "%");
        }
    }

    private void setLoginProgressVisible(boolean visible) {
        if (loginProgressPanel != null) {
            loginProgressPanel.setManaged(visible);
            loginProgressPanel.setVisible(visible);
        }
    }

    private void mirrorCentralDataAfterLogin(User user, String password) {
        Task<Void> mirrorTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ServiceRegistry.getRemoteMirrorCoordinator().handleRemoteLogin(user, password);
                return null;
            }
        };
        mirrorTask.setOnFailed(event -> {
            Throwable failure = mirrorTask.getException();
            String message = failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
                    ? "Try Sync Center after confirming the API is reachable."
                    : failure.getMessage();
            OperationFeedbackHelper.showWarning(
                    "Mirror Refresh Failed",
                    "Login succeeded, but the local SQLite mirror could not be refreshed. " + message
            );
        });
        Thread mirrorThread = new Thread(mirrorTask, "post-login-mirror-refresh");
        mirrorThread.setDaemon(true);
        mirrorThread.start();
    }

    @FXML
    private void showResetPanel() {
        if (txtResetIdentifier != null && (txtResetIdentifier.getText() == null || txtResetIdentifier.getText().isBlank())) {
            txtResetIdentifier.setText(getEmailInput());
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
    private void handleInitialPasswordChange() {
        String newPassword = txtInitialNewPassword == null || txtInitialNewPassword.getText() == null
                ? ""
                : txtInitialNewPassword.getText();
        String confirmPassword = txtInitialConfirmPassword == null || txtInitialConfirmPassword.getText() == null
                ? ""
                : txtInitialConfirmPassword.getText();
        String validationError = validatePasswordChange(newPassword, confirmPassword);
        if (!validationError.isBlank()) {
            setInitialPasswordStatus(validationError, "error");
            return;
        }

        if (btnInitialPasswordChange != null) {
            btnInitialPasswordChange.setDisable(true);
        }
        setInitialPasswordStatus("Changing password...", "progress");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                authService.completeInitialPasswordChange(newPassword);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            if (btnInitialPasswordChange != null) {
                btnInitialPasswordChange.setDisable(false);
            }
            setInitialPasswordStatus("Password changed successfully. Opening dashboard...", "success");
            try {
                App.showDashboardPage();
            } catch (IOException exception) {
                setInitialPasswordStatus("Password changed, but the dashboard could not be opened.", "error");
            }
        });
        task.setOnFailed(event -> {
            if (btnInitialPasswordChange != null) {
                btnInitialPasswordChange.setDisable(false);
            }
            Throwable failure = task.getException();
            setInitialPasswordStatus(failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
                    ? "Password change failed."
                    : failure.getMessage(), "error");
        });
        Thread thread = new Thread(task, "initial-password-change");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleInitialPasswordSignOut() {
        Session.clear();
        clearInitialPasswordFields();
        showLoginPanel();
        showStatus("Signed out. Enter your account again to continue.");
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
                    String resetCode = PasswordUtils.generateSecureToken(32);
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
        String passwordError = validatePasswordStrength(newPassword);
        if (!passwordError.isBlank()) {
            setResetStatus(passwordError, "error");
            return;
        }

        try {
            authService.resetPasswordWithCode(identifier, resetCode, newPassword);
            if (ServiceRegistry.getConfiguration().usesLocalDatabase()) {
                ServiceRegistry.getRemoteMirrorCoordinator().updateMirroredPassword(identifier, newPassword);
            }
            rememberedLoginStore.remove(identifier);
            refreshRememberedEmailChoices();
            Session.clear();
            setEmailInput(identifier);
            txtPassword.clear();
            clearResetInputFields(true);
            setResetStatus("Password updated successfully. Sign in with the new password.", "success");
            showStatus("Password reset successfully. Sign in with the new password.");
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
        setEmailInput("");
        txtPassword.clear();
        if (chkShowPassword != null) {
            chkShowPassword.setSelected(false);
        }
        showStatus("");
    }

    @FXML
    private void handleForgetSavedLogin() {
        String email = getEmailInput();
        rememberedLoginStore.remove(email);
        refreshRememberedEmailChoices();
        txtPassword.clear();
        showStatus(email.isBlank() ? "Saved login list refreshed." : "Forgot saved login for " + email + ".");
    }

    private void showStatus(String message) {
        if (lblLoginStatus != null) {
            lblLoginStatus.setText(message);
        }
    }

    private void setupRememberedLogins() {
        if (cmbEmail == null) {
            return;
        }
        cmbEmail.setEditable(true);
        cmbEmail.setCellFactory(listView -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String email, boolean empty) {
                    super.updateItem(email, empty);
                    setText(empty || email == null ? null : email);
                }
            };
            cell.setOnMousePressed(event -> {
                if (!cell.isEmpty()) {
                    event.consume();
                    applyRememberedEmailSelection(cell.getItem());
                }
            });
            return cell;
        });
        refreshRememberedEmailChoices();

        cmbEmail.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldValue, newValue) -> applyRememberedEmailSelection(newValue)
        );
        cmbEmail.setOnAction(event -> applyRememberedEmailSelection(currentEmailComboValue()));
        cmbEmail.setOnHidden(event -> applyRememberedEmailSelection(currentEmailEditorText()));
        cmbEmail.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (loginInProgress) {
                return;
            }
            if (applyingRememberedSelection) {
                return;
            }
            if (newValue != null && !newValue.equals(oldValue)) {
                filterRememberedEmailChoices(newValue);
                String normalizedValue = normalized(newValue).toLowerCase();
                if (rememberedEmails.contains(normalizedValue)) {
                    applyRememberedEmailSelection(normalizedValue);
                    return;
                }
                if (!applyingRememberedSelection && !newValue.equals(cmbEmail.getSelectionModel().getSelectedItem())) {
                    txtPassword.clear();
                    if (chkRememberLogin != null) {
                        chkRememberLogin.setSelected(false);
                    }
                }
            }
        });

        if (chkRememberLogin != null) {
            chkRememberLogin.setSelected(false);
        }
    }

    private void refreshRememberedEmailChoices() {
        if (cmbEmail == null) {
            return;
        }
        String current = getEmailInput();
        rememberedEmails = rememberedLoginStore.getEmails();
        cmbEmail.getItems().setAll(rememberedEmails);
        setEmailInput(current);
    }

    private void filterRememberedEmailChoices(String typedEmail) {
        if (cmbEmail == null) {
            return;
        }
        String query = normalized(typedEmail).toLowerCase();
        if (query.isBlank()) {
            cmbEmail.getItems().setAll(rememberedEmails);
            cmbEmail.hide();
            return;
        }

        List<String> matches = rememberedEmails.stream()
                .filter(email -> email.contains(query))
                .collect(Collectors.toList());
        cmbEmail.getItems().setAll(matches);
        if (!matches.isEmpty() && cmbEmail.isFocused()) {
            cmbEmail.show();
        } else {
            cmbEmail.hide();
        }
    }

    private void fillRememberedPasswordFromSelection(String email) {
        String password = rememberedLoginStore.getPassword(email);
        if (!password.isBlank() && txtPassword != null) {
            txtPassword.setText(password);
            if (chkRememberLogin != null) {
                chkRememberLogin.setSelected(true);
            }
        }
    }

    private void applyRememberedEmailSelection(String email) {
        String normalizedEmail = normalized(email).toLowerCase();
        if (normalizedEmail.isBlank() || !rememberedEmails.contains(normalizedEmail)) {
            return;
        }

        applyingRememberedSelection = true;
        try {
            cmbEmail.setValue(normalizedEmail);
            cmbEmail.getSelectionModel().select(normalizedEmail);
            if (cmbEmail.getEditor() != null) {
                cmbEmail.getEditor().setText(normalizedEmail);
                cmbEmail.getEditor().positionCaret(normalizedEmail.length());
            }
            fillRememberedPasswordFromSelection(normalizedEmail);
            cmbEmail.hide();
        } finally {
            Platform.runLater(() -> applyingRememberedSelection = false);
        }
    }

    private void rememberSuccessfulLogin(String email, String password) {
        if (chkRememberLogin != null && !chkRememberLogin.isSelected()) {
            return;
        }
        rememberedLoginStore.save(email, password);
        rememberedEmails = rememberedLoginStore.getEmails();
    }

    private String getEmailInput() {
        if (cmbEmail == null) {
            return "";
        }
        return normalized(currentEmailEditorText()).toLowerCase();
    }

    private String currentEmailComboValue() {
        if (cmbEmail == null) {
            return "";
        }
        String editorText = normalized(currentEmailEditorText()).toLowerCase();
        String value = normalized(cmbEmail.getValue()).toLowerCase();
        if (!value.isBlank()
                && rememberedEmails.contains(value)
                && (editorText.isBlank() || value.equals(editorText) || value.contains(editorText))) {
            return value;
        }
        return editorText;
    }

    private String currentEmailEditorText() {
        if (cmbEmail == null) {
            return "";
        }
        return cmbEmail.isEditable() && cmbEmail.getEditor() != null
                ? cmbEmail.getEditor().getText()
                : cmbEmail.getValue();
    }

    private void setEmailInput(String email) {
        if (cmbEmail == null) {
            return;
        }
        String normalizedEmail = normalized(email).toLowerCase();
        cmbEmail.setValue(normalizedEmail);
        if (cmbEmail.getEditor() != null) {
            cmbEmail.getEditor().setText(normalizedEmail);
        }
    }

    private void switchPanel(String panel) {
        boolean showLogin = "login".equals(panel);
        boolean showReset = "reset".equals(panel);
        boolean showInitialPassword = "initialPassword".equals(panel);
        if (loginPanel != null) {
            loginPanel.setManaged(showLogin);
            loginPanel.setVisible(showLogin);
        }
        if (resetPanel != null) {
            resetPanel.setManaged(showReset);
            resetPanel.setVisible(showReset);
        }
        if (initialPasswordPanel != null) {
            initialPasswordPanel.setManaged(showInitialPassword);
            initialPasswordPanel.setVisible(showInitialPassword);
        }
    }

    private void showInitialPasswordPanel() {
        switchPanel("initialPassword");
        clearInitialPasswordFields();
        setInitialPasswordStatus("Create a strong password to continue.", "info");
    }

    private void clearInitialPasswordFields() {
        if (txtInitialNewPassword != null) {
            txtInitialNewPassword.clear();
        }
        if (txtInitialConfirmPassword != null) {
            txtInitialConfirmPassword.clear();
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

    private void setInitialPasswordStatus(String message, String tone) {
        if (lblInitialPasswordStatus != null) {
            lblInitialPasswordStatus.setText(message == null ? "" : message);
        }
        if (initialPasswordStatusBanner != null) {
            initialPasswordStatusBanner.getStyleClass().removeAll("status-info", "status-success", "status-error", "status-progress");
            switch (tone == null ? "" : tone) {
                case "success":
                    initialPasswordStatusBanner.getStyleClass().add("status-success");
                    break;
                case "progress":
                    initialPasswordStatusBanner.getStyleClass().add("status-progress");
                    break;
                case "error":
                    initialPasswordStatusBanner.getStyleClass().add("status-error");
                    break;
                default:
                    initialPasswordStatusBanner.getStyleClass().add("status-info");
                    break;
            }
        }
    }

    private String validatePasswordChange(String newPassword, String confirmPassword) {
        if (newPassword.isBlank() || confirmPassword.isBlank()) {
            return "Enter and confirm the new password.";
        }
        if (!newPassword.equals(confirmPassword)) {
            return "The new password and confirmation do not match.";
        }
        return validatePasswordStrength(newPassword);
    }

    private String validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            return "Password must be at least 8 characters.";
        }
        if (!password.chars().anyMatch(Character::isUpperCase)
                || !password.chars().anyMatch(Character::isLowerCase)
                || !password.chars().anyMatch(Character::isDigit)) {
            return "Password must include uppercase, lowercase, and a number.";
        }
        return "";
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private String resolveMessage(Exception exception) {
        return exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Unknown error"
                : exception.getMessage();
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

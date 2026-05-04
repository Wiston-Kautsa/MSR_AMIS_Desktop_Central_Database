package com.mycompany.msr.amis;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

public class BackupSyncController implements Initializable {

    @FXML private Label lblCurrentRole;
    @FXML private Label lblLatestOfficialVersion;
    @FXML private Label lblLocalVersion;
    @FXML private Label lblLastBackup;
    @FXML private Label lblStatus;
    @FXML private Label lblFeedbackTitle;
    @FXML private Label lblFeedbackSummary;
    @FXML private Label lblReviewSubmission;
    @FXML private Label lblReviewStatus;
    @FXML private Label lblOperationPercent;
    @FXML private Label lblOperationState;

    @FXML private TextField txtOfficialPath;
    @FXML private TextField txtSubmissionsPath;
    @FXML private TextField txtLocalBackupPath;
    @FXML private TextArea txtDisplayContent;
    @FXML private TextArea txtReviewDetails;
    @FXML private ProgressBar progressOperation;

    @FXML private Button btnRestoreLatestOfficial;
    @FXML private Button btnSubmitBackup;
    @FXML private Button btnReviewSubmission;
    @FXML private Button btnRefreshSubmissions;
    @FXML private Button btnApprovePublish;

    @FXML private TitledPane superAdminPanel;
    @FXML private VBox feedbackPanel;

    @FXML private TableView<BackupSyncRecord> tblSubmissions;
    @FXML private TableColumn<BackupSyncRecord, String> colSubmissionFileName;
    @FXML private TableColumn<BackupSyncRecord, String> colSubmissionBy;
    @FXML private TableColumn<BackupSyncRecord, String> colSubmissionDate;
    @FXML private TableColumn<BackupSyncRecord, String> colSubmissionStatus;

    @FXML private TableView<BackupSyncRecord> tblOfficialHistory;
    @FXML private TableColumn<BackupSyncRecord, String> colOfficialFileName;
    @FXML private TableColumn<BackupSyncRecord, String> colOfficialDate;
    @FXML private TableColumn<BackupSyncRecord, String> colOfficialBy;

    @FXML private TableView<BackupSyncRecord> tblMyBackups;
    @FXML private TableColumn<BackupSyncRecord, String> colMyBackupFileName;
    @FXML private TableColumn<BackupSyncRecord, String> colMyBackupDate;
    @FXML private TableColumn<BackupSyncRecord, String> colMyBackupLocation;
    @FXML private TableColumn<BackupSyncRecord, String> colMyBackupStatus;

    private RestoreService restoreService;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!canAccessBackupSync()) {
            throw new SecurityException("Backup & Restore is available only to Admin and Super Admin in LOCAL_DATABASE mode.");
        }
        configureTables();
        configureRoleVisibility();
        resetReviewPanel();
        loadConfigurationAndData();
    }

    @FXML
    private void handleRestoreLatestOfficial() {
        if (restoreService == null) {
            showFeedback("Configuration Required",
                    "Backup & Restore paths are not configured.",
                    "",
                    "warning");
            return;
        }
        runOperation("Restore Latest Official", new Task<>() {
            @Override
            protected RestoreService.RestoreResult call() throws Exception {
                updateProgress(0, 100);
                updateMessage("Preparing restore...");
                updateProgress(20, 100);
                RestoreService.RestoreResult result = restoreService.restoreLatestOfficialDatabase(currentActor());
                updateMessage("Reloading backup tables...");
                updateProgress(85, 100);
                return result;
            }
        }, result -> {
            RestoreService.RestoreResult restoreResult = (RestoreService.RestoreResult) result;
            refreshAllData();
            updateStatus("Restored " + restoreResult.getOfficialFile().getFileName() + ". Restart the application to reload data.");
            showFeedback("Done",
                    "Restore completed.",
                    "",
                    "success");
        });
    }

    @FXML
    private void handleSubmitBackup() {
        if (restoreService == null) {
            showFeedback("Configuration Required",
                    "Backup & Restore paths are not configured.",
                    "",
                    "warning");
            return;
        }
        runOperation("Submit Backup", new Task<>() {
            @Override
            protected Path call() throws Exception {
                updateProgress(0, 100);
                updateMessage("Creating local backup...");
                updateProgress(35, 100);
                Path submission = restoreService.submitBackup(currentActor());
                updateMessage("Refreshing backup queues...");
                updateProgress(85, 100);
                return submission;
            }
        }, result -> {
            Path submission = (Path) result;
            refreshAllData();
            updateStatus("Submitted backup " + submission.getFileName() + ".");
            showFeedback("Done",
                    "Backup submitted.",
                    "",
                    "success");
        });
    }

    @FXML
    private void handleRefreshSubmissions() {
        refreshAllData();
        updateStatus("Backup & Restore data refreshed.");
    }

    @FXML
    private void handleReviewSubmission() {
        if (restoreService == null) {
            showFeedback("Configuration Required",
                    "Backup & Restore paths are not configured.",
                    "The module could not load the configured locations. Review the shared and local backup paths before trying again.",
                    "warning");
            return;
        }
        if (!Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)) {
            showFeedback("Access Denied",
                    "Only the Super Admin can review submission compatibility here.",
                    "",
                    "error");
            return;
        }

        BackupSyncRecord selected = tblSubmissions != null ? tblSubmissions.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            resetReviewPanel();
            showFeedback("Submission Required",
                    "Select a submitted backup first.",
                    "",
                    "warning");
            return;
        }
        runOperation("Review Submission", new Task<>() {
            @Override
            protected RestoreService.CompatibilityReport call() throws Exception {
                updateProgress(0, 100);
                updateMessage("Opening submitted database...");
                updateProgress(20, 100);
                RestoreService.CompatibilityReport report = restoreService.analyzeSubmission(selected.getFilePath());
                updateMessage("Preparing compatibility summary...");
                updateProgress(90, 100);
                return report;
            }
        }, result -> {
            RestoreService.CompatibilityReport report = (RestoreService.CompatibilityReport) result;
            if (lblReviewSubmission != null) {
                lblReviewSubmission.setText(selected.getFileName());
            }
            if (lblReviewStatus != null) {
                lblReviewStatus.setText(report.isSafeToMerge()
                        ? "Safe to Merge"
                        : "Blocked by " + report.getConflictCount() + " conflict(s)");
            }
            if (txtReviewDetails != null) {
                txtReviewDetails.setText(report.toDisplayText());
            }
            showFeedback(report.isSafeToMerge() ? "Done" : "Review Result",
                    report.isSafeToMerge() ? "Submission is compatible." : "Submission has conflicts.",
                    "",
                    report.isSafeToMerge() ? "success" : "warning");
            updateStatus("Reviewed submission compatibility for " + selected.getFileName() + ".");
        }, () -> resetReviewPanel());
    }

    @FXML
    private void handleApproveAndPublish() {
        if (restoreService == null) {
            showFeedback("Configuration Required",
                    "Backup & Restore paths are not configured.",
                    "",
                    "warning");
            return;
        }
        if (!Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)) {
            showFeedback("Access Denied",
                    "Only the Super Admin can approve and publish submissions.",
                    "",
                    "error");
            return;
        }

        BackupSyncRecord selected = tblSubmissions != null ? tblSubmissions.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            showFeedback("Submission Required",
                    "Select a submitted backup first.",
                    "",
                    "warning");
            return;
        }
        runOperation("Approve & Publish", new Task<>() {
            @Override
            protected RestoreService.PublishResult call() throws Exception {
                updateProgress(0, 100);
                updateMessage("Validating submission...");
                updateProgress(20, 100);
                RestoreService.PublishResult result = restoreService.approveAndPublish(selected.getFilePath(), currentActor());
                updateMessage("Refreshing official history...");
                updateProgress(90, 100);
                return result;
            }
        }, result -> {
            RestoreService.PublishResult publishResult = (RestoreService.PublishResult) result;
            refreshAllData();
            updateStatus("Published " + publishResult.getPublishedFile().getFileName() + " as the latest official version.");
            showFeedback("Done",
                    "Publish completed.",
                    "",
                    "success");
        });
    }

    private void configureTables() {
        if (colSubmissionFileName != null) {
            colSubmissionFileName.setCellValueFactory(new PropertyValueFactory<>("fileName"));
            colSubmissionBy.setCellValueFactory(new PropertyValueFactory<>("actor"));
            colSubmissionDate.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
            colSubmissionStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        }

        if (colOfficialFileName != null) {
            colOfficialFileName.setCellValueFactory(new PropertyValueFactory<>("fileName"));
            colOfficialDate.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
            colOfficialBy.setCellValueFactory(new PropertyValueFactory<>("actor"));
        }

        if (colMyBackupFileName != null) {
            colMyBackupFileName.setCellValueFactory(new PropertyValueFactory<>("fileName"));
            colMyBackupDate.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
            colMyBackupLocation.setCellValueFactory(new PropertyValueFactory<>("location"));
            colMyBackupStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        }

        if (tblSubmissions != null) {
            tblSubmissions.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue == null) {
                    resetReviewPanel();
                    return;
                }
                if (lblReviewSubmission != null) {
                    lblReviewSubmission.setText(newValue.getFileName());
                }
                if (lblReviewStatus != null) {
                    lblReviewStatus.setText("Pending Review");
                }
                if (txtReviewDetails != null) {
                    txtReviewDetails.setText("Review the selected submission to compare it against the latest official database before approval.");
                }
            });
        }
    }

    private void configureRoleVisibility() {
        String role = Session.getCurrentUser() != null ? Session.getCurrentUser().getRole() : AccessControl.ROLE_USER;
        if (lblCurrentRole != null) {
            lblCurrentRole.setText(role);
        }

        boolean isSuperAdmin = Session.hasRole(AccessControl.ROLE_SUPER_ADMIN);
        if (superAdminPanel != null) {
            superAdminPanel.setManaged(isSuperAdmin);
            superAdminPanel.setVisible(isSuperAdmin);
        }
    }

    private void loadConfigurationAndData() {
        try {
            BackupSyncConfig config = BackupSyncConfigService.load();
            restoreService = new RestoreService(config);

            txtOfficialPath.setText(config.getOfficialPath().toString());
            txtSubmissionsPath.setText(config.getSubmissionsPath().toString());
            txtLocalBackupPath.setText(config.getLocalBackupPath().toString());

            refreshAllData();
            updateStatus("Backup & Restore module loaded.");
            showFeedback("Ready",
                    "Backup & Restore loaded.",
                    "",
                    "info");
            resetOperationProgress();
        } catch (IOException e) {
            e.printStackTrace();
            showFeedback("Configuration Error",
                    "Failed to load Backup & Restore paths.",
                    safeMessage(e),
                    "error");
            updateStatus("Failed to load Backup & Restore configuration.");
        }
    }

    private void refreshAllData() {
        if (restoreService == null) {
            return;
        }

        try {
            lblLatestOfficialVersion.setText(restoreService.describeLatestOfficialVersion());
            lblLocalVersion.setText(restoreService.describeLocalVersion());

            var myBackups = FXCollections.observableArrayList(restoreService.listMyBackups(currentActor()));
            tblMyBackups.setItems(myBackups);
            lblLastBackup.setText(myBackups.isEmpty() ? "No backup yet" : myBackups.get(0).getTimestamp());

            if (Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)) {
                tblSubmissions.setItems(FXCollections.observableArrayList(restoreService.listSubmissions()));
                tblOfficialHistory.setItems(FXCollections.observableArrayList(restoreService.listOfficialHistory()));
            } else {
                if (tblSubmissions != null) {
                    tblSubmissions.setItems(FXCollections.observableArrayList());
                }
                if (tblOfficialHistory != null) {
                    tblOfficialHistory.setItems(FXCollections.observableArrayList());
                }
            }
            resetReviewPanel();
        } catch (IOException e) {
            e.printStackTrace();
            showFeedback("Refresh Failed",
                    "Backup & Restore data could not be refreshed.",
                    safeMessage(e),
                    "error");
            updateStatus("Refresh failed.");
        }
    }

    private void showFeedback(String title, String summary, String details, String tone) {
        if (lblFeedbackTitle != null) {
            lblFeedbackTitle.setText(title == null ? "" : title);
        }
        if (lblFeedbackSummary != null) {
            String normalizedSummary = summary == null ? "" : summary;
            lblFeedbackSummary.setText(normalizedSummary);
            lblFeedbackSummary.setManaged(!normalizedSummary.isBlank());
            lblFeedbackSummary.setVisible(!normalizedSummary.isBlank());
        }
        if (txtDisplayContent != null) {
            String normalizedDetails = details == null ? "" : details;
            txtDisplayContent.setText(normalizedDetails);
            txtDisplayContent.setManaged(!normalizedDetails.isBlank());
            txtDisplayContent.setVisible(!normalizedDetails.isBlank());
        }
        if (feedbackPanel != null) {
            feedbackPanel.getStyleClass().removeAll("feedback-info", "feedback-success", "feedback-warning", "feedback-error");
            switch (tone == null ? "" : tone) {
                case "success":
                    feedbackPanel.getStyleClass().add("feedback-success");
                    break;
                case "warning":
                    feedbackPanel.getStyleClass().add("feedback-warning");
                    break;
                case "error":
                    feedbackPanel.getStyleClass().add("feedback-error");
                    break;
                default:
                    feedbackPanel.getStyleClass().add("feedback-info");
                    break;
            }
        }
    }

    private void updateStatus(String message) {
        if (lblStatus != null) {
            lblStatus.setText(message == null ? "" : message);
        }
    }

    private String currentActor() {
        User user = Session.getCurrentUser();
        if (user == null) {
            return "unknown";
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return user.getUsername();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? "An unexpected error occurred while processing this action."
                : message;
    }

    private void resetReviewPanel() {
        if (lblReviewSubmission != null) {
            lblReviewSubmission.setText("Select a submitted backup to review.");
        }
        if (lblReviewStatus != null) {
            lblReviewStatus.setText("Not Reviewed");
        }
        if (txtReviewDetails != null) {
            txtReviewDetails.setText("");
        }
    }

    private void runOperation(String operationName, Task<?> task, java.util.function.Consumer<Object> onSuccess) {
        runOperation(operationName, task, onSuccess, null);
    }

    private void runOperation(String operationName, Task<?> task, java.util.function.Consumer<Object> onSuccess,
                              Runnable onFailure) {
        bindOperationProgress(operationName, task);
        setActionButtonsDisabled(true);

        task.setOnSucceeded(event -> {
            progressOperation.progressProperty().unbind();
            lblOperationState.textProperty().unbind();
            progressOperation.setProgress(1);
            lblOperationPercent.setText("100%");
            lblOperationState.setText(operationName + " done");
            setActionButtonsDisabled(false);
            onSuccess.accept(task.getValue());
        });

        task.setOnFailed(event -> {
            progressOperation.progressProperty().unbind();
            lblOperationState.textProperty().unbind();
            progressOperation.setProgress(0);
            lblOperationPercent.setText("0%");
            lblOperationState.setText(operationName + " failed");
            setActionButtonsDisabled(false);
            Exception e = task.getException() instanceof Exception
                    ? (Exception) task.getException()
                    : new Exception(task.getException());
            showFeedback("Failed", safeMessage(e), "", "error");
            updateStatus(operationName + " failed.");
            if (onFailure != null) {
                onFailure.run();
            }
        });

        Thread thread = new Thread(task, "backup-sync-" + operationName.replace(' ', '-').toLowerCase());
        thread.setDaemon(true);
        thread.start();
    }

    private void bindOperationProgress(String operationName, Task<?> task) {
        if (progressOperation != null) {
            progressOperation.progressProperty().unbind();
            progressOperation.progressProperty().bind(task.progressProperty());
        }
        if (lblOperationState != null) {
            lblOperationState.textProperty().unbind();
            lblOperationState.textProperty().bind(task.messageProperty());
        }
        if (lblOperationPercent != null) {
            lblOperationPercent.setText("0%");
        }
        task.progressProperty().addListener((obs, oldValue, newValue) -> {
            if (lblOperationPercent != null) {
                double progress = newValue == null ? 0 : newValue.doubleValue();
                int percent = progress < 0 ? 0 : (int) Math.round(progress * 100);
                lblOperationPercent.setText(percent + "%");
            }
        });
        updateStatus(operationName + " started.");
    }

    private void resetOperationProgress() {
        if (progressOperation != null) {
            progressOperation.progressProperty().unbind();
            progressOperation.setProgress(0);
        }
        if (lblOperationPercent != null) {
            lblOperationPercent.setText("0%");
        }
        if (lblOperationState != null) {
            lblOperationState.textProperty().unbind();
            lblOperationState.setText("Idle");
        }
    }

    private void setActionButtonsDisabled(boolean disabled) {
        if (btnRestoreLatestOfficial != null) btnRestoreLatestOfficial.setDisable(disabled);
        if (btnSubmitBackup != null) btnSubmitBackup.setDisable(disabled);
        if (btnReviewSubmission != null) btnReviewSubmission.setDisable(disabled);
        if (btnRefreshSubmissions != null) btnRefreshSubmissions.setDisable(disabled);
        if (btnApprovePublish != null) btnApprovePublish.setDisable(disabled);
    }

    private boolean canAccessBackupSync() {
        return ServiceRegistry.getConfiguration().usesLocalDatabase()
                && Session.hasRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
    }
}

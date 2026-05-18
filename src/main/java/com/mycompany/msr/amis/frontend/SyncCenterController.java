package com.mycompany.msr.amis;

import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;

public final class SyncCenterController implements Initializable {

    private static final DateTimeFormatter SESSION_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Map<String, Boolean> PANE_STATES = new HashMap<>();

    private final SyncCenterService syncCenterService = ServiceRegistry.getSyncCenterService();
    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();
    private final String machineName = resolveMachineName();
    private final String deviceId = UUID.nameUUIDFromBytes(machineName.getBytes(StandardCharsets.UTF_8)).toString();
    private final String syncSessionId = "SYNC-" + SESSION_STAMP.format(LocalDateTime.now());

    private boolean syncPaused;
    private int pushedThisSession;
    private int pulledThisSession;
    private int retryCount;
    private ConfirmedAction pendingConfirmationAction;

    @FXML private Label lblPendingCount;
    @FXML private Label lblAppliedCount;
    @FXML private Label lblRejectedCount;
    @FXML private Label lblFailedCount;
    @FXML private Label lblSyncStatus;
    @FXML private Label lblApiConnectivity;
    @FXML private Label lblPostgresStatus;
    @FXML private Label lblQueueService;
    @FXML private Label lblLastSuccessfulSync;
    @FXML private Label lblTotalQueued;
    @FXML private Label lblConflictCount;
    @FXML private Label lblPushedThisSession;
    @FXML private Label lblPulledThisSession;
    @FXML private Label lblRetryCount;
    @FXML private Label lblLastSyncUser;
    @FXML private Label lblSyncLock;
    @FXML private Label lblProgressText;
    @FXML private Label lblInlineConfirmTitle;
    @FXML private Label lblInlineConfirmMessage;
    @FXML private Label lblApiBaseUrl;
    @FXML private Label lblDataMode;
    @FXML private Label lblPollInterval;
    @FXML private Label lblBatchSize;
    @FXML private Label lblMaxRetries;
    @FXML private Label lblTimeout;
    @FXML private Label lblSyncStrategy;
    @FXML private Label lblTokenStatus;
    @FXML private Label lblAutoSync;
    @FXML private Label lblPolicyEngine;
    @FXML private Label lblEnterpriseControls;
    @FXML private Label lblTransportStatus;
    @FXML private Label lblDeviceName;
    @FXML private Label lblDeviceId;
    @FXML private Label lblCurrentUser;
    @FXML private Label lblSyncSessionId;
    @FXML private Label lblLastPushTime;
    @FXML private Label lblLastPullTime;
    @FXML private Label lblLastFailedSync;
    @FXML private Label lblSyncOwner;
    @FXML private Label lblOfflineQueue;
    @FXML private Label lblSnapshotComparison;
    @FXML private TextArea txtErrorDetails;
    @FXML private ProgressBar progressSync;
    @FXML private CheckBox chkEquipment;
    @FXML private CheckBox chkAssignments;
    @FXML private CheckBox chkReturns;
    @FXML private CheckBox chkUsers;
    @FXML private CheckBox chkAuditLogs;
    @FXML private VBox pnlInlineConfirm;
    @FXML private TitledPane paneSyncActions;
    @FXML private TitledPane paneSyncOverview;
    @FXML private TitledPane paneQueueWorkbench;
    @FXML private TitledPane paneLogsSettings;

    @FXML private TableView<SyncQueueRecord> tblQueue;
    @FXML private TableColumn<SyncQueueRecord, Void> colQueueNo;
    @FXML private TableColumn<SyncQueueRecord, Number> colQueueId;
    @FXML private TableColumn<SyncQueueRecord, String> colQueueEntity;
    @FXML private TableColumn<SyncQueueRecord, String> colQueueOperation;
    @FXML private TableColumn<SyncQueueRecord, String> colQueueStatus;
    @FXML private TableColumn<SyncQueueRecord, String> colQueueRecord;
    @FXML private TableColumn<SyncQueueRecord, String> colQueueActor;
    @FXML private TableColumn<SyncQueueRecord, String> colQueueCreatedAt;
    @FXML private TableColumn<SyncQueueRecord, String> colQueueError;

    @FXML private TableView<SyncQueueRecord> tblConflicts;
    @FXML private TableColumn<SyncQueueRecord, Number> colConflictId;
    @FXML private TableColumn<SyncQueueRecord, String> colConflictEntity;
    @FXML private TableColumn<SyncQueueRecord, String> colConflictRecord;
    @FXML private TableColumn<SyncQueueRecord, String> colConflictLocalAt;
    @FXML private TableColumn<SyncQueueRecord, String> colConflictCentralAt;
    @FXML private TableColumn<SyncQueueRecord, String> colConflictType;
    @FXML private TableColumn<SyncQueueRecord, String> colConflictAction;

    @FXML private TableView<SyncLogRecord> tblLogs;
    @FXML private TableColumn<SyncLogRecord, String> colLogTimestamp;
    @FXML private TableColumn<SyncLogRecord, String> colLogUser;
    @FXML private TableColumn<SyncLogRecord, String> colLogMachine;
    @FXML private TableColumn<SyncLogRecord, String> colLogEntity;
    @FXML private TableColumn<SyncLogRecord, String> colLogOperation;
    @FXML private TableColumn<SyncLogRecord, Number> colLogRecordsCount;
    @FXML private TableColumn<SyncLogRecord, String> colLogDuration;
    @FXML private TableColumn<SyncLogRecord, String> colLogResult;
    @FXML private TableColumn<SyncLogRecord, String> colLogError;

    @FXML private TableView<SyncValidationIssue> tblValidationIssues;
    @FXML private TableColumn<SyncValidationIssue, String> colIssueSeverity;
    @FXML private TableColumn<SyncValidationIssue, String> colIssueCategory;
    @FXML private TableColumn<SyncValidationIssue, String> colIssueEntity;
    @FXML private TableColumn<SyncValidationIssue, String> colIssueRecord;
    @FXML private TableColumn<SyncValidationIssue, String> colIssueMessage;

    @FXML private Button btnProcessPending;
    @FXML private Button btnPullNow;
    @FXML private Button btnRetryRejected;
    @FXML private Button btnPauseSync;
    @FXML private Button btnResumeSync;
    @FXML private Button btnClearCompletedLogs;
    @FXML private Button btnForceFullResync;
    @FXML private Button btnClearQueue;
    @FXML private Button btnResetSyncState;
    @FXML private Button btnForceReleaseLock;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!AccessControl.canAccessSyncCenter()) {
            throw new SecurityException("Sync Center is available only to Admin and Super Admin.");
        }
        configurePaneStateMemory();
        configureTables();
        applyStaticMetadata();
        applyAccessPolicy();
        refresh();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    @FXML
    private void handleProcessPending() {
        if (syncPaused) {
            setStatus("Queue service is paused. Resume sync before pushing changes.");
            return;
        }
        try {
            AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
            List<SyncValidationIssue> issues = syncCenterService.validateBeforeSync();
            tblValidationIssues.getItems().setAll(issues);
            long blockingIssues = issues.stream()
                    .filter(issue -> "ERROR".equalsIgnoreCase(issue.getSeverity()))
                    .count();
            if (blockingIssues > 0) {
                throw new IllegalStateException("Pre-sync validation failed: " + blockingIssues
                        + " issue(s). Use Validation Issues -> Fix Before Sync.");
            }
            if (hasDestructiveActions()) {
                requestInlineConfirmation(
                        "Destructive Sync Warning",
                        "The selected queue includes delete/retire/freeze actions. Continue pushing to the Central Server?",
                        this::executePendingEquipmentPush
                );
                return;
            }
            executePendingEquipmentPush();
        } catch (Exception exception) {
            setProgress(0, "Sync failed");
            setError(resolveMessage(exception));
        }
    }

    private void executePendingEquipmentPush() throws Exception {
        setProgress(0.35, "Sync in progress");
        String message = Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)
                ? syncCenterService.pushPendingEquipment()
                : syncCenterService.pushPendingEquipmentForActor(currentActor());
        pushedThisSession++;
        setProgress(1.0, "Sync completed");
        refresh();
        setStatus(message);
    }

    @FXML
    private void handlePullNow() {
        try {
            String message = syncCenterService.pullFromCentralServer();
            pulledThisSession++;
            lblLastPullTime.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            refresh();
            setStatus(message);
        } catch (Exception exception) {
            setError(resolveMessage(exception));
        }
    }

    @FXML
    private void handleRetryRejected() {
        try {
            if (!AccessControl.canRetryRejectedSyncItems()) {
                throw new SecurityException("Retrying failed sync items is available only to Admin and Super Admin.");
            }
            String message = syncCenterService.retryRejectedQueue();
            retryCount++;
            refresh();
            setStatus(message);
        } catch (Exception exception) {
            setError(resolveMessage(exception));
        }
    }

    @FXML
    private void handlePauseSync() {
        syncPaused = true;
        applyPauseState();
        setStatus("Queue service paused for this Sync Center session.");
    }

    @FXML
    private void handleResumeSync() {
        syncPaused = false;
        applyPauseState();
        setStatus("Queue service resumed.");
    }

    @FXML
    private void handleReviewChanges() {
        try {
            List<SyncQueueRecord> active = new ArrayList<>(tblQueue.getItems());
            Map<String, Long> counts = active.stream()
                    .collect(Collectors.groupingBy(SyncQueueRecord::getEntityType, Collectors.counting()));
            if (counts.isEmpty()) {
                OperationFeedbackHelper.showInfo("Review Changes", "There are no active queued changes to review.");
                return;
            }
            StringBuilder message = new StringBuilder("Queued changes before push:\n\n");
            counts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> message.append(entry.getValue()).append(" ").append(entry.getKey()).append(" change(s)\n"));
            OperationFeedbackHelper.showInfo("Review Changes", message.toString());
        } catch (Exception exception) {
            setError(resolveMessage(exception));
        }
    }

    @FXML
    private void handleRunValidation() {
        try {
            List<SyncValidationIssue> issues = syncCenterService.validateBeforeSync();
            tblValidationIssues.getItems().setAll(issues);
            setStatus(issues.isEmpty()
                    ? "Pre-sync validation passed."
                    : "Pre-sync validation failed: " + issues.size() + " issue(s).");
        } catch (Exception exception) {
            setError(resolveMessage(exception));
        }
    }

    @FXML
    private void handleViewIssues() {
        int count = tblValidationIssues.getItems().size();
        OperationFeedbackHelper.showInfo("Validation Issues", count == 0
                ? "No validation issues are currently listed."
                : count + " validation issue(s) are listed in the table.");
    }

    @FXML
    private void handleFixBeforeSync() {
        OperationFeedbackHelper.showInfo(
                "Fix Before Sync",
                "Correct the listed local records in their source screens, then run validation again before pushing."
        );
    }

    @FXML
    private void handleDryRun() {
        try {
            String result = syncCenterService.runDryRun(selectedEntityScope());
            setError(result);
            setStatus("Dry run completed. No central data was changed.");
        } catch (Exception exception) {
            setError(resolveMessage(exception));
        }
    }

    @FXML
    private void handleViewDifferences() {
        SyncQueueRecord selected = tblConflicts.getSelectionModel().getSelectedItem();
        if (selected == null) {
            OperationFeedbackHelper.showInfo("View Differences", "Select a conflict first.");
            return;
        }
        OperationFeedbackHelper.showInfo(
                "Conflict Details",
                "Queue ID: " + selected.getId()
                        + "\nEntity: " + selected.getEntityType()
                        + "\nRecord: " + selected.getEntityKey()
                        + "\nConflict: " + selected.getConflictType()
                        + "\nDetails: " + firstNonBlank(selected.getErrorMessage(), selected.getDescription())
        );
    }

    @FXML
    private void handleKeepLocal() {
        conflictResolutionNotReady("Keep Local");
    }

    @FXML
    private void handleKeepCentral() {
        conflictResolutionNotReady("Keep Central");
    }

    @FXML
    private void handleMerge() {
        conflictResolutionNotReady("Merge");
    }

    @FXML
    private void handleCopyError() {
        String text = txtErrorDetails == null ? "" : txtErrorDetails.getText();
        if (text == null || text.isBlank()) {
            OperationFeedbackHelper.showInfo("Copy Error", "There is no error detail to copy.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        setStatus("Error detail copied to clipboard.");
    }

    @FXML
    private void handleOpenDetails() {
        SyncQueueRecord selected = tblQueue.getSelectionModel().getSelectedItem();
        if (selected == null && !tblConflicts.getItems().isEmpty()) {
            selected = tblConflicts.getItems().get(0);
        }
        if (selected == null) {
            OperationFeedbackHelper.showInfo("Details", "No queue item is selected.");
            return;
        }
        setError(formatError(selected));
    }

    @FXML
    private void handleExportLogsCsv() {
        ReportExportHelper.exportCsv("sync_logs", "Sync Logs", new ArrayList<>(tblLogs.getItems()), logColumns());
    }

    @FXML
    private void handleExportLogsPdf() {
        ReportExportHelper.exportPdf("sync_logs", "Sync Logs", new ArrayList<>(tblLogs.getItems()), logColumns());
    }

    @FXML
    private void handleClearCompletedLogs() {
        try {
            requireSuperAdmin();
            String message = syncCenterService.clearCompletedLogs(currentActor());
            refresh();
            setStatus(message);
        } catch (Exception exception) {
            setError(resolveMessage(exception));
        }
    }

    @FXML
    private void handleForceFullResync() {
        try {
            requireSuperAdmin();
            String message = syncCenterService.pullFromCentralServer();
            pulledThisSession++;
            lblLastPullTime.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            refresh();
            setStatus("Force full resync completed. " + message);
        } catch (Exception exception) {
            setError(resolveMessage(exception));
        }
    }

    @FXML
    private void handleClearQueue() {
        try {
            requireSuperAdmin();
            requestInlineConfirmation(
                    "Clear Queue",
                    "Clear all sync queue records? This does not delete central data.",
                    () -> {
                        String message = syncCenterService.clearQueue(currentActor());
                        refresh();
                        setStatus(message);
                    }
            );
        } catch (Exception exception) {
            setError(resolveMessage(exception));
        }
    }

    @FXML
    private void handleResetSyncState() {
        try {
            requireSuperAdmin();
            requestInlineConfirmation(
                    "Reset Sync State",
                    "Reset all local sync queue and sync audit records?",
                    () -> {
                        String message = syncCenterService.resetSyncState(currentActor());
                        refresh();
                        setStatus(message);
                    }
            );
        } catch (Exception exception) {
            setError(resolveMessage(exception));
        }
    }

    @FXML
    private void handleInlineConfirmContinue() {
        ConfirmedAction action = pendingConfirmationAction;
        clearInlineConfirmation();
        if (action == null) {
            return;
        }
        try {
            action.run();
        } catch (Exception exception) {
            setError(resolveMessage(exception));
        }
    }

    @FXML
    private void handleInlineConfirmCancel() {
        clearInlineConfirmation();
        setStatus("Action cancelled.");
    }

    @FXML
    private void handleForceReleaseLock() {
        try {
            requireSuperAdmin();
            String message = syncCenterService.forceReleaseSyncLock(currentActor());
            refresh();
            setStatus(message);
        } catch (Exception exception) {
            setError(resolveMessage(exception));
        }
    }

    private void refresh() {
        try {
            SyncCenterSummary summary = syncCenterService.getSummary();
            List<SyncQueueRecord> visibleQueue = filterQueue(syncCenterService.getQueueRecords());
            List<SyncQueueRecord> activeQueue = filterActiveQueue(visibleQueue);
            List<SyncQueueRecord> conflicts = filterConflicts(visibleQueue);
            List<SyncAuditRecord> visibleAudit = filterAudit(syncCenterService.getAuditRecords());
            List<SyncLogRecord> logs = buildLogs(visibleQueue, visibleAudit);
            List<SyncValidationIssue> issues = syncCenterService.validateBeforeSync();

            applyVisibleCounts(visibleQueue, conflicts);
            tblQueue.getItems().setAll(activeQueue);
            tblConflicts.getItems().setAll(conflicts);
            tblLogs.getItems().setAll(logs);
            tblValidationIssues.getItems().setAll(issues);
            applyHealth(summary, visibleQueue, visibleAudit);
            applyPauseState();
            setStatus(summary.getStatusMessage());
        } catch (Exception exception) {
            setError(resolveMessage(exception));
        }
    }

    private void configurePaneStateMemory() {
        rememberPaneState("actions", paneSyncActions);
        rememberPaneState("overview", paneSyncOverview);
        rememberPaneState("workbench", paneQueueWorkbench);
        rememberPaneState("logsSettings", paneLogsSettings);
    }

    private void rememberPaneState(String key, TitledPane pane) {
        if (pane == null) {
            return;
        }
        Boolean savedState = PANE_STATES.get(key);
        if (savedState != null) {
            pane.setExpanded(savedState);
        } else {
            PANE_STATES.put(key, pane.isExpanded());
        }
        pane.expandedProperty().addListener((observable, oldValue, newValue) ->
                PANE_STATES.put(key, Boolean.TRUE.equals(newValue)));
    }

    private void configureTables() {
        TableNumbering.install(colQueueNo);
        colQueueId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colQueueEntity.setCellValueFactory(new PropertyValueFactory<>("entityType"));
        colQueueOperation.setCellValueFactory(new PropertyValueFactory<>("operationType"));
        colQueueStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colQueueRecord.setCellValueFactory(new PropertyValueFactory<>("entityKey"));
        colQueueActor.setCellValueFactory(new PropertyValueFactory<>("actor"));
        colQueueCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        colQueueError.setCellValueFactory(new PropertyValueFactory<>("errorMessage"));

        colConflictId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colConflictEntity.setCellValueFactory(new PropertyValueFactory<>("entityType"));
        colConflictRecord.setCellValueFactory(new PropertyValueFactory<>("entityKey"));
        colConflictLocalAt.setCellValueFactory(new PropertyValueFactory<>("localVersionTimestamp"));
        colConflictCentralAt.setCellValueFactory(new PropertyValueFactory<>("centralVersionTimestamp"));
        colConflictType.setCellValueFactory(new PropertyValueFactory<>("conflictType"));
        colConflictAction.setCellValueFactory(new PropertyValueFactory<>("resolutionAction"));

        colLogTimestamp.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        colLogUser.setCellValueFactory(new PropertyValueFactory<>("user"));
        colLogMachine.setCellValueFactory(new PropertyValueFactory<>("machineName"));
        colLogEntity.setCellValueFactory(new PropertyValueFactory<>("entity"));
        colLogOperation.setCellValueFactory(new PropertyValueFactory<>("operation"));
        colLogRecordsCount.setCellValueFactory(new PropertyValueFactory<>("recordsCount"));
        colLogDuration.setCellValueFactory(new PropertyValueFactory<>("duration"));
        colLogResult.setCellValueFactory(new PropertyValueFactory<>("result"));
        colLogError.setCellValueFactory(new PropertyValueFactory<>("errorMessage"));

        colIssueSeverity.setCellValueFactory(new PropertyValueFactory<>("severity"));
        colIssueCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colIssueEntity.setCellValueFactory(new PropertyValueFactory<>("entity"));
        colIssueRecord.setCellValueFactory(new PropertyValueFactory<>("recordIdentifier"));
        colIssueMessage.setCellValueFactory(new PropertyValueFactory<>("message"));

        tblQueue.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.getErrorMessage().isBlank()) {
                setError(formatError(newValue));
            }
        });
        tblConflicts.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                setError(formatError(newValue));
            }
        });
    }

    private void applyStaticMetadata() {
        AppConfiguration configuration = ServiceRegistry.getConfiguration();
        lblApiBaseUrl.setText(configuration.getApiBaseUrl());
        lblDataMode.setText(configuration.getConfiguredMode().name() + " / effective " + configuration.getDataAccessMode().name());
        lblPollInterval.setText(configuration.isAutomaticMode() ? "30 seconds background check" : "Manual");
        lblBatchSize.setText("All pending records in scope");
        lblMaxRetries.setText("Manual retry");
        lblTimeout.setText("4 seconds health probe");
        lblSyncStrategy.setText("Desktop -> API -> Central Server");
        lblAutoSync.setText(configuration.isAutomaticMode() ? "ENABLED" : "DISABLED");
        lblTransportStatus.setText(configuration.getApiBaseUrl().startsWith("https://") ? "HTTPS" : "HTTP - not production secure");
        lblDeviceName.setText(machineName);
        lblDeviceId.setText(deviceId);
        lblCurrentUser.setText(currentActor());
        lblSyncSessionId.setText(syncSessionId);
    }

    private void applyAccessPolicy() {
        boolean superAdmin = Session.hasRole(AccessControl.ROLE_SUPER_ADMIN);
        boolean adminOrSuperAdmin = AccessControl.canRetryRejectedSyncItems();
        btnRetryRejected.setManaged(adminOrSuperAdmin);
        btnRetryRejected.setVisible(adminOrSuperAdmin);
        btnClearCompletedLogs.setManaged(superAdmin);
        btnClearCompletedLogs.setVisible(superAdmin);
        btnForceFullResync.setManaged(superAdmin);
        btnForceFullResync.setVisible(superAdmin);
        btnClearQueue.setManaged(superAdmin);
        btnClearQueue.setVisible(superAdmin);
        btnResetSyncState.setManaged(superAdmin);
        btnResetSyncState.setVisible(superAdmin);
        btnForceReleaseLock.setManaged(superAdmin);
        btnForceReleaseLock.setVisible(superAdmin);
    }

    private void applyHealth(SyncCenterSummary summary, List<SyncQueueRecord> queue, List<SyncAuditRecord> audits) {
        boolean hasToken = Session.getAuthToken() != null && !Session.getAuthToken().isBlank();
        lblApiConnectivity.setText(summary.isOnlineReady() ? "Online" : hasToken ? "Offline" : "Authentication Failed");
        lblPostgresStatus.setText(summary.isOnlineReady() ? "Connected" : "Unknown");
        lblQueueService.setText(syncPaused ? "Paused" : "Running");
        lblTokenStatus.setText(resolveTokenStatus());
        lblLastSuccessfulSync.setText(lastAuditAt(audits, "APPLIED", "Never"));
        lblLastFailedSync.setText(lastAuditAt(audits, "FAILED", "Never"));
        lblLastPushTime.setText(lastAuditAt(audits, "APPLIED", lblLastPushTime.getText()));
        if (lblLastSyncUser != null) {
            lblLastSyncUser.setText(lastAuditActor(audits));
        }
        applyLockInfo();
        lblOfflineQueue.setText(queue.stream().filter(record -> !"APPLIED".equalsIgnoreCase(record.getStatus())).count()
                + " unsynced record(s). Oldest: " + oldestQueueDate(queue));
        lblSnapshotComparison.setText("Local queued: " + queue.size() + ". Central comparison requires API count endpoints.");
        lblPushedThisSession.setText(Integer.toString(pushedThisSession));
        lblPulledThisSession.setText(Integer.toString(pulledThisSession));
        lblRetryCount.setText(Integer.toString(retryCount));
        btnProcessPending.setDisable(syncPaused || !summary.isOnlineReady() || !AccessControl.canAccessSyncCenter());
        btnPullNow.setDisable(!summary.isOnlineReady());
        if (queue.stream().anyMatch(record -> "FAILED".equalsIgnoreCase(record.getStatus()) || "REJECTED".equalsIgnoreCase(record.getStatus()))) {
            setError("Sync attention required.\n\nFailed or rejected queue records exist. Select a row for details.");
        }
    }

    private void applyPauseState() {
        lblQueueService.setText(syncPaused ? "Paused" : "Running");
        btnPauseSync.setDisable(syncPaused);
        btnResumeSync.setDisable(!syncPaused);
        if (btnProcessPending != null) {
            btnProcessPending.setDisable(syncPaused || btnProcessPending.isDisable());
        }
    }

    private void applyLockInfo() {
        try {
            SyncLockInfo lockInfo = syncCenterService.getSyncLockInfo();
            if (lockInfo.isActive()) {
                lblSyncLock.setText("ACTIVE");
                lblSyncOwner.setText(lockInfo.getLockedBy() + " / " + lockInfo.getSessionId());
            } else {
                lblSyncLock.setText("Inactive");
                lblSyncOwner.setText("None");
            }
        } catch (Exception exception) {
            lblSyncLock.setText("Unknown");
            lblSyncOwner.setText(resolveMessage(exception));
        }
    }

    private Set<String> selectedEntityScope() {
        Set<String> scope = new LinkedHashSet<>();
        if (chkEquipment != null && chkEquipment.isSelected()) {
            scope.add("EQUIPMENT");
        }
        if (chkAssignments != null && chkAssignments.isSelected()) {
            scope.add("ASSIGNMENT");
            scope.add("DISTRIBUTION");
        }
        if (chkReturns != null && chkReturns.isSelected()) {
            scope.add("RETURN");
        }
        if (chkUsers != null && chkUsers.isSelected()) {
            scope.add("USER");
        }
        if (chkAuditLogs != null && chkAuditLogs.isSelected()) {
            scope.add("AUDIT");
        }
        return scope;
    }

    private boolean hasDestructiveActions() {
        for (SyncQueueRecord record : tblQueue.getItems()) {
            String operation = normalize(record.getOperationType());
            String message = normalize(record.getDescription() + " " + record.getErrorMessage());
            if ("DELETE".equals(operation) || message.contains("RETIRED") || message.contains("FROZEN")) {
                return true;
            }
        }
        return false;
    }

    private void setProgress(double progress, String text) {
        if (progressSync != null) {
            progressSync.setProgress(progress);
        }
        if (lblProgressText != null) {
            lblProgressText.setText(text);
        }
    }

    private String oldestQueueDate(List<SyncQueueRecord> queue) {
        return queue.stream()
                .filter(record -> !"APPLIED".equalsIgnoreCase(record.getStatus()))
                .map(SyncQueueRecord::getCreatedAt)
                .filter(value -> value != null && !value.isBlank())
                .min(String::compareTo)
                .orElse("None");
    }

    private List<SyncQueueRecord> filterQueue(List<SyncQueueRecord> records) {
        if (Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)) {
            return records;
        }
        if (!Session.hasRole(AccessControl.ROLE_ADMIN)) {
            return List.of();
        }
        String actor = currentActor();
        return records.stream()
                .filter(record -> actor.equalsIgnoreCase(record.getActor()))
                .collect(Collectors.toList());
    }

    private List<SyncQueueRecord> filterActiveQueue(List<SyncQueueRecord> records) {
        return records.stream()
                .filter(record -> !"APPLIED".equals(normalize(record.getStatus())))
                .collect(Collectors.toList());
    }

    private List<SyncQueueRecord> filterConflicts(List<SyncQueueRecord> records) {
        return records.stream()
                .filter(record -> "REJECTED".equals(normalize(record.getStatus())) || "FAILED".equals(normalize(record.getStatus())))
                .collect(Collectors.toList());
    }

    private List<SyncAuditRecord> filterAudit(List<SyncAuditRecord> records) {
        if (Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)) {
            return records;
        }
        if (!Session.hasRole(AccessControl.ROLE_ADMIN)) {
            return List.of();
        }
        String actor = currentActor();
        return records.stream()
                .filter(record -> actor.equalsIgnoreCase(record.getActor()))
                .collect(Collectors.toList());
    }

    private void applyVisibleCounts(List<SyncQueueRecord> records, List<SyncQueueRecord> conflicts) {
        int pending = 0;
        int applied = 0;
        int rejected = 0;
        int failed = 0;
        for (SyncQueueRecord record : records) {
            switch (normalize(record.getStatus())) {
                case "APPLIED":
                    applied++;
                    break;
                case "REJECTED":
                    rejected++;
                    break;
                case "FAILED":
                    failed++;
                    break;
                default:
                    pending++;
                    break;
            }
        }
        lblPendingCount.setText(Integer.toString(pending));
        lblAppliedCount.setText(Integer.toString(applied));
        lblRejectedCount.setText(Integer.toString(rejected));
        lblFailedCount.setText(Integer.toString(failed));
        lblTotalQueued.setText(Integer.toString(records.size()));
        lblConflictCount.setText(Integer.toString(conflicts.size()));
    }

    private List<SyncLogRecord> buildLogs(List<SyncQueueRecord> queue, List<SyncAuditRecord> audits) {
        Map<Long, SyncQueueRecord> byQueueId = new HashMap<>();
        for (SyncQueueRecord record : queue) {
            byQueueId.put(record.getId(), record);
        }
        List<SyncLogRecord> logs = new ArrayList<>();
        for (SyncAuditRecord audit : audits) {
            SyncQueueRecord queueRecord = byQueueId.get(audit.getQueueId());
            logs.add(new SyncLogRecord(
                    audit.getCreatedAt(),
                    audit.getActor(),
                    queueRecord == null ? machineName : firstNonBlank(queueRecord.getMachineId(), machineName),
                    queueRecord == null ? "SYNC" : queueRecord.getEntityType(),
                    queueRecord == null ? audit.getAction() : queueRecord.getOperationType(),
                    audit.getQueueId() > 0 ? 1 : extractCount(audit.getDetails()),
                    "",
                    audit.getOutcome(),
                    queueRecord == null ? audit.getDetails() : firstNonBlank(queueRecord.getErrorMessage(), audit.getDetails())
            ));
        }
        logs.sort(Comparator.comparing(SyncLogRecord::getTimestamp).reversed());
        return logs;
    }

    private List<ReportExportHelper.Column<SyncLogRecord>> logColumns() {
        return List.of(
                new ReportExportHelper.Column<>("Timestamp", SyncLogRecord::getTimestamp),
                new ReportExportHelper.Column<>("User", SyncLogRecord::getUser),
                new ReportExportHelper.Column<>("Machine Name", SyncLogRecord::getMachineName),
                new ReportExportHelper.Column<>("Entity", SyncLogRecord::getEntity),
                new ReportExportHelper.Column<>("Operation", SyncLogRecord::getOperation),
                new ReportExportHelper.Column<>("Records Count", record -> Integer.toString(record.getRecordsCount())),
                new ReportExportHelper.Column<>("Duration", SyncLogRecord::getDuration),
                new ReportExportHelper.Column<>("Result", SyncLogRecord::getResult),
                new ReportExportHelper.Column<>("Error Message", SyncLogRecord::getErrorMessage)
        );
    }

    private String formatError(SyncQueueRecord record) {
        return "ERROR:\n"
                + "Queue ID: " + record.getId() + "\n"
                + "Entity: " + record.getEntityType() + "\n"
                + "Operation: " + record.getOperationType() + "\n"
                + "Record: " + record.getEntityKey() + "\n"
                + "Computer: " + firstNonBlank(record.getMachineId(), "Unknown") + "\n"
                + "Status: " + record.getStatus() + "\n"
                + "Message: " + firstNonBlank(record.getErrorMessage(), record.getDescription());
    }

    private void conflictResolutionNotReady(String action) {
        OperationFeedbackHelper.showWarning(
                action,
                action + " requires stored local and central field-level snapshots. The current system now exposes conflicts for review, but automatic merge/overwrite resolution is not enabled yet."
        );
    }

    private void setStatus(String message) {
        lblSyncStatus.setText(message == null ? "" : message);
    }

    private void setError(String message) {
        if (txtErrorDetails != null) {
            txtErrorDetails.setText(message == null ? "" : message);
        }
        setStatus(message);
    }

    private String resolveMessage(Exception exception) {
        return exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "The sync center operation failed."
                : exception.getMessage();
    }

    private String currentActor() {
        User currentUser = Session.getCurrentUser();
        if (currentUser == null) {
            return "unknown";
        }
        if (currentUser.getEmail() != null && !currentUser.getEmail().isBlank()) {
            return currentUser.getEmail();
        }
        return currentUser.getUsername();
    }

    private String resolveMachineName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            String computerName = System.getenv("COMPUTERNAME");
            return computerName == null || computerName.isBlank() ? "UNKNOWN_DEVICE" : computerName;
        }
    }

    private String resolveTokenStatus() {
        String token = Session.getAuthToken();
        if (token == null || token.isBlank()) {
            return "No token";
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return "Token present";
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            int expIndex = payload.indexOf("\"exp\":");
            if (expIndex < 0) {
                return "Token valid";
            }
            int start = expIndex + 6;
            int end = start;
            while (end < payload.length() && Character.isDigit(payload.charAt(end))) {
                end++;
            }
            long expiresAt = Long.parseLong(payload.substring(start, end));
            long minutes = (expiresAt - Instant.now().getEpochSecond()) / 60;
            if (minutes <= 0) {
                return "Token expired";
            }
            return "JWT expires in " + minutes + " minute(s)";
        } catch (Exception ignored) {
            return "Token present";
        }
    }

    private String lastAuditAt(List<SyncAuditRecord> audits, String outcome, String fallback) {
        return audits.stream()
                .filter(record -> outcome.equalsIgnoreCase(record.getOutcome()))
                .map(SyncAuditRecord::getCreatedAt)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(fallback == null || fallback.isBlank() ? "Never" : fallback);
    }

    private String lastAuditActor(List<SyncAuditRecord> audits) {
        return audits.stream()
                .map(SyncAuditRecord::getActor)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("None");
    }

    private int extractCount(String details) {
        if (details == null) {
            return 0;
        }
        String digits = details.replaceAll("[^0-9]", " ").trim();
        if (digits.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits.split("\\s+")[0]);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void requireSuperAdmin() {
        AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN);
    }

    private void requestInlineConfirmation(String title, String message, ConfirmedAction action) {
        pendingConfirmationAction = action;
        if (lblInlineConfirmTitle != null) {
            lblInlineConfirmTitle.setText(title == null || title.isBlank() ? "Confirm Action" : title);
        }
        if (lblInlineConfirmMessage != null) {
            lblInlineConfirmMessage.setText(message == null ? "" : message);
        }
        if (pnlInlineConfirm != null) {
            pnlInlineConfirm.setManaged(true);
            pnlInlineConfirm.setVisible(true);
        }
        setStatus(message);
    }

    private void clearInlineConfirmation() {
        pendingConfirmationAction = null;
        if (pnlInlineConfirm != null) {
            pnlInlineConfirm.setManaged(false);
            pnlInlineConfirm.setVisible(false);
        }
    }

    @FunctionalInterface
    private interface ConfirmedAction {
        void run() throws Exception;
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = first == null ? "" : first.trim();
        return normalizedFirst.isBlank() ? (second == null ? "" : second.trim()) : normalizedFirst;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}

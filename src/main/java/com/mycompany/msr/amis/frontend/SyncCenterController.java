package com.mycompany.msr.amis;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public final class SyncCenterController implements Initializable {

    private final SyncCenterService syncCenterService = ServiceRegistry.getSyncCenterService();

    @FXML private Label lblPendingCount;
    @FXML private Label lblAppliedCount;
    @FXML private Label lblRejectedCount;
    @FXML private Label lblFailedCount;
    @FXML private Label lblSyncStatus;
    @FXML private Label lblSyncMode;
    @FXML private TableView<SyncQueueRecord> tblQueue;
    @FXML private TableColumn<SyncQueueRecord, Number> colQueueId;
    @FXML private TableColumn<SyncQueueRecord, String> colQueueEntity;
    @FXML private TableColumn<SyncQueueRecord, String> colQueueOperation;
    @FXML private TableColumn<SyncQueueRecord, String> colQueueStatus;
    @FXML private TableColumn<SyncQueueRecord, String> colQueueActor;
    @FXML private TableColumn<SyncQueueRecord, String> colQueueCreatedAt;
    @FXML private TableColumn<SyncQueueRecord, String> colQueueError;
    @FXML private TableView<SyncAuditRecord> tblAudit;
    @FXML private TableColumn<SyncAuditRecord, Number> colAuditId;
    @FXML private TableColumn<SyncAuditRecord, Number> colAuditQueueId;
    @FXML private TableColumn<SyncAuditRecord, String> colAuditAction;
    @FXML private TableColumn<SyncAuditRecord, String> colAuditOutcome;
    @FXML private TableColumn<SyncAuditRecord, String> colAuditActor;
    @FXML private TableColumn<SyncAuditRecord, String> colAuditCreatedAt;
    @FXML private Button btnProcessPending;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        configureTables();
        refresh();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    @FXML
    private void handleProcessPending() {
        try {
            String message = syncCenterService.processPendingQueue();
            refresh();
            setStatus(message);
        } catch (Exception exception) {
            setStatus(resolveMessage(exception));
        }
    }

    private void refresh() {
        try {
            SyncCenterSummary summary = syncCenterService.getSummary();
            lblPendingCount.setText(Integer.toString(summary.getPendingCount()));
            lblAppliedCount.setText(Integer.toString(summary.getAppliedCount()));
            lblRejectedCount.setText(Integer.toString(summary.getRejectedCount()));
            lblFailedCount.setText(Integer.toString(summary.getFailedCount()));
            lblSyncMode.setText(summary.isOnlineReady() ? "ONLINE READY" : "OFFLINE ONLY");
            btnProcessPending.setDisable(!summary.isOnlineReady() || summary.getPendingCount() == 0);

            tblQueue.getItems().setAll(filterQueue(syncCenterService.getQueueRecords()));
            tblAudit.getItems().setAll(filterAudit(syncCenterService.getAuditRecords()));
            setStatus(summary.getStatusMessage());
        } catch (Exception exception) {
            setStatus(resolveMessage(exception));
        }
    }

    private List<SyncQueueRecord> filterQueue(List<SyncQueueRecord> records) {
        if (Session.hasRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN)) {
            return records;
        }
        String actor = currentActor();
        return records.stream()
                .filter(record -> actor.equalsIgnoreCase(record.getActor()))
                .collect(Collectors.toList());
    }

    private List<SyncAuditRecord> filterAudit(List<SyncAuditRecord> records) {
        if (Session.hasRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN)) {
            return records;
        }
        String actor = currentActor();
        return records.stream()
                .filter(record -> actor.equalsIgnoreCase(record.getActor()))
                .collect(Collectors.toList());
    }

    private void configureTables() {
        colQueueId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colQueueEntity.setCellValueFactory(new PropertyValueFactory<>("entityType"));
        colQueueOperation.setCellValueFactory(new PropertyValueFactory<>("operationType"));
        colQueueStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colQueueActor.setCellValueFactory(new PropertyValueFactory<>("actor"));
        colQueueCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        colQueueError.setCellValueFactory(new PropertyValueFactory<>("errorMessage"));

        colAuditId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAuditQueueId.setCellValueFactory(new PropertyValueFactory<>("queueId"));
        colAuditAction.setCellValueFactory(new PropertyValueFactory<>("action"));
        colAuditOutcome.setCellValueFactory(new PropertyValueFactory<>("outcome"));
        colAuditActor.setCellValueFactory(new PropertyValueFactory<>("actor"));
        colAuditCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
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

    private void setStatus(String message) {
        lblSyncStatus.setText(message == null ? "" : message);
    }

    private String resolveMessage(Exception exception) {
        return exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "The sync center operation failed."
                : exception.getMessage();
    }
}

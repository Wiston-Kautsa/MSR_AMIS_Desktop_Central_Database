package com.mycompany.msr.amis;

import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.ResourceBundle;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class DashboardsController implements Initializable {
    private static final String FRONTEND_RESOURCE_ROOT = "/com/mycompany/msr/amis/frontend/";
    private static final Set<String> AUTO_REFRESH_PAGES = Set.of(
            "EquipmentList.fxml",
            "AssignmentList.fxml",
            "ReturnEquipmentList.fxml",
            "InventoryReport.fxml",
            "AssignmentReport.fxml",
            "DistributionReport.fxml",
            "ReturnReport.fxml",
            "OutstandingReport.fxml",
            "MaintenanceReport.fxml",
            "AssetHistory.fxml",
            "AuditLogs.fxml",
            "SyncCenter.fxml",
            "Departments.fxml",
            "Users.fxml",
            "Maintenance.fxml"
    );

    private final DashboardService dashboardService = ServiceRegistry.getDashboardService();
    private final ReportService reportService = ServiceRegistry.getReportService();
    private final ConnectionStatusService connectionStatusService = new ConnectionStatusService();
    private Node dashboardHome;
    private Timeline connectionStatusTimeline;
    private Timeline backgroundSyncTimeline;
    private boolean connectionStatusCheckInProgress;
    private boolean backgroundSyncInProgress;
    private boolean manualOfflineRequested;
    private String currentPageFxml;
    private ConnectionStatusService.ConnectionStatus currentConnectionStatus;

    @FXML private StackPane contentArea;
    @FXML private Label lblTotalAssets;
    @FXML private Label lblAvailableAssets;
    @FXML private Label lblBorrowedThisMonth;
    @FXML private Label lblBorrowedBreakdown;
    @FXML private Label lblReturnedAssets;
    @FXML private Label lblUtilizationRate;
    @FXML private Label lblAvailabilityRate;
    @FXML private Button btnBackupSync;
    @FXML private Button btnAuditLogs;
    @FXML private Button btnUsers;
    @FXML private Button btnDepartments;
    @FXML private Button btnDataMaintenance;
    @FXML private Button btnSyncCenter;
    @FXML private Button btnLogout;
    @FXML private Button btnGoOnline;
    @FXML private TitledPane paneDataRecords;
    @FXML private TitledPane paneAdministration;
    @FXML private PieChart assetStatusPieChart;
    @FXML private ProgressBar progressUtilization;
    @FXML private ProgressBar progressAvailability;
    @FXML private VBox alertsContainer;
    @FXML private Label lblConnectionStatus;
    @FXML private Label lblConnectionDetail;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (contentArea != null && !contentArea.getChildren().isEmpty()) {
            dashboardHome = contentArea.getChildren().get(0);
        }
        if (btnUsers != null) {
            btnUsers.setManaged(AccessControl.canViewUserManagement());
            btnUsers.setVisible(AccessControl.canViewUserManagement());
        }
        if (btnDepartments != null) {
            btnDepartments.setManaged(AccessControl.canManageDepartments());
            btnDepartments.setVisible(AccessControl.canManageDepartments());
        }
        if (btnBackupSync != null) {
            boolean allowed = canAccessBackupSync();
            btnBackupSync.setManaged(allowed);
            btnBackupSync.setVisible(allowed);
        }
        if (btnAuditLogs != null) {
            btnAuditLogs.setManaged(AccessControl.canViewAuditLogs());
            btnAuditLogs.setVisible(AccessControl.canViewAuditLogs());
        }
        if (btnDataMaintenance != null) {
            boolean allowed = canAccessDataMaintenance();
            btnDataMaintenance.setManaged(allowed);
            btnDataMaintenance.setVisible(allowed);
        }
        if (btnSyncCenter != null) {
            boolean allowed = AccessControl.canAccessSyncCenter();
            btnSyncCenter.setManaged(allowed);
            btnSyncCenter.setVisible(allowed);
        }
        updateRoleSectionVisibility();
        applyLoggedInUser();
        if (lblTotalAssets != null) {
            refreshDashboard();
        }
        startConnectionStatusMonitor();
        startBackgroundSync();
    }

    private void applyLoggedInUser() {
        if (btnLogout == null) {
            return;
        }
        User user = Session.getCurrentUser();
        if (user == null) {
            btnLogout.setTooltip(new Tooltip("Logged in as Unknown user"));
            return;
        }

        String name = user.getFullName();
        if (name == null || name.isBlank()) {
            name = user.getUsername();
        }
        String role = user.getRole() == null || user.getRole().isBlank() ? "" : " (" + user.getRole() + ")";
        String displayName = (name == null || name.isBlank() ? "Unknown user" : name) + role;
        btnLogout.setTooltip(new Tooltip("Logged in as " + displayName));
    }

    private void loadPage(String fxml) {
        try {
            String path = FRONTEND_RESOURCE_ROOT + fxml;
            URL resource = getClass().getResource(path);
            if (resource == null) {
                throw new IllegalStateException("FXML not found: " + path);
            }

            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            contentArea.getChildren().clear();
            currentPageFxml = fxml;

            if (root instanceof Region) {
                Region region = (Region) root;
                region.prefWidthProperty().bind(contentArea.widthProperty());
                region.prefHeightProperty().bind(contentArea.heightProperty());
            }

            contentArea.getChildren().add(root);
            TableReadabilityHelper.applyTo(root);
        } catch (Exception e) {
            e.printStackTrace();
            OperationFeedbackHelper.showError(
                    "Navigation Error",
                    "The requested page could not be loaded.\n\nPage: " + fxml + "\nCause: " + resolveNavigationMessage(e)
            );
        }
    }

    private String resolveNavigationMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable == null ? "" : throwable.getMessage();
        }
        if (message == null || message.isBlank()) {
            return "Check that the API is running and the page configuration is valid.";
        }
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("connection refused") || lowerMessage.contains("getsockopt")) {
            return "API is not reachable. Start the API server and try again.";
        }
        if (message.endsWith(".fxml:20")) {
            return "The page controller could not start. Check that the API is running and try again.";
        }
        return message;
    }

    @FXML
    private void openDashboard(ActionEvent event) {
        showDashboardHome();
    }

    private void updateRoleSectionVisibility() {
        setPaneAvailability(paneDataRecords, isAvailable(btnBackupSync) || isAvailable(btnAuditLogs) || isAvailable(btnSyncCenter));
        setPaneAvailability(paneAdministration, isAvailable(btnUsers) || isAvailable(btnDepartments) || isAvailable(btnDataMaintenance));
    }

    private boolean isAvailable(Node node) {
        return node != null && node.isManaged() && node.isVisible();
    }

    private void setPaneAvailability(TitledPane pane, boolean available) {
        if (pane == null) {
            return;
        }
        pane.setManaged(available);
        pane.setVisible(available);
        if (!available) {
            pane.setExpanded(false);
        }
    }

    public void showDashboardHome() {
        if (contentArea == null || dashboardHome == null) {
            return;
        }
        contentArea.getChildren().setAll(dashboardHome);
        currentPageFxml = null;
        if (dashboardHome instanceof Region) {
            Region region = (Region) dashboardHome;
            region.prefWidthProperty().bind(contentArea.widthProperty());
            region.prefHeightProperty().bind(contentArea.heightProperty());
        }
        refreshDashboard();
    }

    public void refreshDashboard() {
        DashboardSummary summary;
        try {
            summary = dashboardService.getDashboardSummary();
        } catch (Exception e) {
            OperationFeedbackHelper.showError("Dashboard Error", e.getMessage());
            return;
        }

        lblTotalAssets.setText(String.valueOf(summary.getTotalAssets()));
        lblAvailableAssets.setText(String.valueOf(summary.getAvailableAssets()));
        lblBorrowedThisMonth.setText(String.valueOf(summary.getBorrowedThisMonth()));
        if (lblBorrowedBreakdown != null) {
            lblBorrowedBreakdown.setText(
                    "Returned: " + summary.getReturnedThisMonth() +
                            "  |  Still In Use: " + summary.getStillInUseFromBorrowedThisMonth()
            );
        }
        lblReturnedAssets.setText(String.valueOf(summary.getReturnedThisMonth()));

        double utilizationRate = summary.getTotalAssets() == 0 ? 0 : (double) summary.getAssetsInUse() / summary.getTotalAssets();
        double availabilityRate = summary.getTotalAssets() == 0 ? 0 : (double) summary.getAvailableAssets() / summary.getTotalAssets();

        lblUtilizationRate.setText(String.format("%.0f%%", utilizationRate * 100));
        lblAvailabilityRate.setText(String.format("%.0f%%", availabilityRate * 100));
        progressUtilization.setProgress(utilizationRate);
        progressAvailability.setProgress(availabilityRate);

        loadAssetStatusChart(summary.getAvailableAssets(), summary.getBorrowedThisMonth(), summary.getReturnedThisMonth());
        loadAlerts(summary);
    }

    private void loadAssetStatusChart(int availableAssets, int borrowedThisMonth, int returnedThisMonth) {
        if (assetStatusPieChart == null) {
            return;
        }

        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        if (availableAssets > 0) {
            data.add(new PieChart.Data("Available", availableAssets));
        }
        if (borrowedThisMonth > 0) {
            data.add(new PieChart.Data("Borrowed This Month", borrowedThisMonth));
        }
        if (returnedThisMonth > 0) {
            data.add(new PieChart.Data("Returned This Month", returnedThisMonth));
        }
        if (data.isEmpty()) {
            data.add(new PieChart.Data("No Data", 1));
        }

        assetStatusPieChart.setLabelsVisible(true);
        assetStatusPieChart.setData(data);
    }

    private void loadAlerts(DashboardSummary summary) {
        if (alertsContainer == null) {
            return;
        }

        alertsContainer.getChildren().clear();
        addAlertItem("Monthly borrowing", summary.getBorrowedThisMonth() + " asset(s) were borrowed this month.");

        if (summary.getOutstandingWithRemarks() > 0) {
            addAlertItem(
                    "Outstanding return reasons captured",
                    summary.getOutstandingWithRemarks() + " outstanding asset(s) already have return remarks recorded."
            );
        }

        long overdue = 0;
        try {
            overdue = reportService.getOutstandingReport().stream()
                    .filter(distribution -> ReportFilterHelper.isOverdue(distribution.getDate()))
                    .count();
        } catch (Exception ignored) {
            // Dashboard should still render if the detailed report cannot be loaded.
        }
        if (overdue > 0) {
            addAlertItem(
                    "Overdue returns",
                    overdue + " outstanding asset(s) have been issued for more than 30 days."
            );
        }

        if (summary.getReturnedThisMonth() == 0) {
            addAlertItem("Monthly returns", "No returns have been recorded yet this month.");
        }

        if (alertsContainer.getChildren().isEmpty()) {
            alertsContainer.getChildren().add(createEmptyState("No alerts right now."));
        }
    }

    private VBox createEmptyState(String text) {
        VBox item = new VBox();
        item.getStyleClass().add("dashboard-feed-item");
        Label label = new Label(text);
        label.getStyleClass().add("dashboard-feed-copy");
        label.setWrapText(true);
        item.getChildren().add(label);
        return item;
    }

    private void addAlertItem(String titleText, String bodyText) {
        VBox item = new VBox(4);
        item.getStyleClass().addAll("dashboard-feed-item", "dashboard-alert-item");

        Label title = new Label(titleText);
        title.getStyleClass().add("dashboard-feed-title");

        Label body = new Label(bodyText);
        body.getStyleClass().add("dashboard-feed-copy");
        body.setWrapText(true);

        item.getChildren().addAll(title, body);
        alertsContainer.getChildren().add(item);
    }

    @FXML
    private void handleRefreshDashboard(ActionEvent event) {
        refreshDashboard();
    }

    @FXML private void openAddEquipment() { loadPage("AddEquipment.fxml"); }
    @FXML private void openEquipmentList() { loadPage("EquipmentList.fxml"); }
    @FXML private void openMaintenance() { loadPage("Maintenance.fxml"); }
    @FXML private void openCreateAssignment() { loadPage("CreateAssignment.fxml"); }
    @FXML private void openDistributeEquipment() { loadPage("DistributeEquipment.fxml"); }
    @FXML private void openDistributionList() { loadPage("DistributionList.fxml"); }
    @FXML private void openAssignmentList() { loadPage("AssignmentList.fxml"); }
    @FXML private void openReturnEquipment() { loadPage("ReturnEquipment.fxml"); }
    @FXML private void openInventoryReport() { loadPage("InventoryReport.fxml"); }
    @FXML private void openAssignmentReport() { loadPage("AssignmentReport.fxml"); }
    @FXML private void openDistributionReport() { loadPage("DistributionReport.fxml"); }
    @FXML private void openAssetHistory() { loadPage("AssetHistory.fxml"); }
    @FXML private void openMaintenanceReport() { loadPage("MaintenanceReport.fxml"); }
    @FXML private void openReturnEquipmentList() { loadPage("ReturnEquipmentList.fxml"); }
    @FXML private void openReturnReport() { loadPage("ReturnReport.fxml"); }
    @FXML private void openOutstandingReport() { loadPage("OutstandingReport.fxml"); }

    @FXML
    private void openBackupSync() {
        try {
            if (!canAccessBackupSync()) {
                throw new SecurityException("Backup & Restore is available only to Super Admin in LOCAL_DATABASE mode.");
            }
            loadPage("BackupSync.fxml");
        } catch (SecurityException e) {
            OperationFeedbackHelper.showError("Access Denied", e.getMessage());
        }
    }

    @FXML
    private void openAuditLogs() {
        try {
            AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
            loadPage("AuditLogs.fxml");
        } catch (SecurityException e) {
            OperationFeedbackHelper.showError("Access Denied", e.getMessage());
        }
    }

    @FXML
    private void openUsers() {
        try {
            AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN, AccessControl.ROLE_USER);
            loadPage("Users.fxml");
        } catch (SecurityException e) {
            OperationFeedbackHelper.showError("Access Denied", e.getMessage());
        }
    }

    @FXML
    private void openDepartments() {
        try {
            AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
            loadPage("Departments.fxml");
        } catch (SecurityException e) {
            OperationFeedbackHelper.showError("Access Denied", e.getMessage());
        }
    }

    @FXML
    private void openDataMaintenance() {
        try {
            if (!canAccessDataMaintenance()) {
                throw new SecurityException("Data Maintenance is available only to Super Admin.");
            }
            loadPage("DataMaintenance.fxml");
        } catch (SecurityException e) {
            OperationFeedbackHelper.showError("Access Denied", e.getMessage());
        }
    }

    @FXML
    private void openSyncCenter() {
        try {
            if (!AccessControl.canAccessSyncCenter()) {
                throw new SecurityException("Sync Center is available only to Super Admin and Admin.");
            }
            loadPage("SyncCenter.fxml");
        } catch (SecurityException e) {
            OperationFeedbackHelper.showError("Access Denied", e.getMessage());
        }
    }

    @FXML
    private void handleGoOnline(ActionEvent event) {
        if (isCurrentStatusOnline() && !manualOfflineRequested) {
            manualOfflineRequested = true;
            stopBackgroundSync();
            applyConnectionStatus(currentConnectionStatus);
            return;
        }

        if (btnGoOnline != null) {
            btnGoOnline.setDisable(true);
            btnGoOnline.setText("Checking...");
        }

        Task<GoOnlineResult> task = new Task<>() {
            @Override
            protected GoOnlineResult call() {
                ConnectionStatusService.ConnectionStatus status = connectionStatusService.checkStatus();
                boolean reachable = status != null
                        && status.getStyleClass() != null
                        && status.getStyleClass().contains("connection-status-online");
                if (!reachable) {
                    return new GoOnlineResult(status, "Central API is not reachable. Contact IT support to start the API service and confirm PostgreSQL is running. Then click Go Online again.");
                }

                if (!ServiceRegistry.getRemoteMirrorCoordinator().hasRemoteSession()) {
                    return new GoOnlineResult(
                            status,
                            null
                    );
                }

                try {
                    ServiceRegistry.getSyncCenterService().processPendingQueue();
                    return new GoOnlineResult(status, null);
                } catch (Exception exception) {
                    return new GoOnlineResult(status, null);
                }
            }
        };

        task.setOnSucceeded(done -> {
            restoreGoOnlineButton();
            GoOnlineResult result = task.getValue();
            if (result != null && isStatusOnline(result.status)) {
                manualOfflineRequested = false;
                applyConnectionStatus(result.status);
                startBackgroundSync();
                refreshVisibleData();
            } else if (result != null && result.message != null && !result.message.isBlank()) {
                if (!manualOfflineRequested) {
                    applyConnectionStatus(result.status);
                }
                OperationFeedbackHelper.showError("API Not Reachable", result.message);
            } else if (result != null && !manualOfflineRequested) {
                applyConnectionStatus(result.status);
            }
        });
        task.setOnFailed(done -> {
            restoreGoOnlineButton();
            refreshConnectionStatus();
        });

        Thread thread = new Thread(task, "go-online-check");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML private void openAboutUs() { loadPage("AboutUs.fxml"); }

    @FXML
    private void openLogout(ActionEvent event) {
        try {
            stopConnectionStatusMonitor();
            User user = Session.getCurrentUser();
            String actor = user == null || user.getEmail() == null || user.getEmail().isBlank()
                    ? "unknown_user"
                    : user.getEmail().trim();
            AuditService.log(actor, "LOGOUT", "AUTH", "User logged out.");
            Session.clear();
            App.showLoginPage();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startConnectionStatusMonitor() {
        if (contentArea != null) {
            contentArea.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene == null) {
                    stopConnectionStatusMonitor();
                }
            });
        }
        refreshConnectionStatus();
        connectionStatusTimeline = new Timeline(
                new KeyFrame(Duration.seconds(20), event -> refreshConnectionStatus())
        );
        connectionStatusTimeline.setCycleCount(Timeline.INDEFINITE);
        connectionStatusTimeline.play();
    }

    private void stopConnectionStatusMonitor() {
        if (connectionStatusTimeline != null) {
            connectionStatusTimeline.stop();
            connectionStatusTimeline = null;
        }
        stopBackgroundSync();
    }

    private void refreshConnectionStatus() {
        if (connectionStatusCheckInProgress) {
            return;
        }

        connectionStatusCheckInProgress = true;
        Task<ConnectionStatusService.ConnectionStatus> task = new Task<>() {
            @Override
            protected ConnectionStatusService.ConnectionStatus call() {
                return connectionStatusService.checkStatus();
            }
        };

        task.setOnSucceeded(event -> {
            connectionStatusCheckInProgress = false;
            applyConnectionStatus(task.getValue());
        });
        task.setOnFailed(event -> {
            connectionStatusCheckInProgress = false;
            applyConnectionStatus(new ConnectionStatusService.ConnectionStatus(
                    "OFFLINE",
                    "Central database server is unreachable.",
                    "connection-status-offline"
            ));
        });

        Thread thread = new Thread(task, "connection-status-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void startBackgroundSync() {
        stopBackgroundSync();
        if (!ServiceRegistry.getConfiguration().isAutomaticMode()) {
            return;
        }
        backgroundSyncTimeline = new Timeline(
                new KeyFrame(Duration.seconds(30), event -> runBackgroundSync())
        );
        backgroundSyncTimeline.setCycleCount(Timeline.INDEFINITE);
        backgroundSyncTimeline.play();
    }

    private void stopBackgroundSync() {
        if (backgroundSyncTimeline != null) {
            backgroundSyncTimeline.stop();
            backgroundSyncTimeline = null;
        }
    }

    private void runBackgroundSync() {
        if (manualOfflineRequested
                || backgroundSyncInProgress
                || !ServiceRegistry.getRemoteMirrorCoordinator().hasRemoteSession()) {
            return;
        }

        backgroundSyncInProgress = true;
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return ServiceRegistry.getSyncCenterService().processPendingQueue();
            }
        };

        task.setOnSucceeded(event -> {
            backgroundSyncInProgress = false;
            refreshVisibleData();
        });
        task.setOnFailed(event -> {
            backgroundSyncInProgress = false;
            refreshConnectionStatus();
        });

        Thread thread = new Thread(task, "background-sync");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshVisibleData() {
        if (contentArea == null) {
            return;
        }
        if (currentPageFxml == null) {
            refreshDashboard();
            return;
        }
        if (AUTO_REFRESH_PAGES.contains(currentPageFxml)) {
            loadPage(currentPageFxml);
        }
    }

    private void applyConnectionStatus(ConnectionStatusService.ConnectionStatus status) {
        if (status == null) {
            return;
        }
        currentConnectionStatus = status;
        if (lblConnectionStatus != null) {
            lblConnectionStatus.setText(manualOfflineRequested ? "OFFLINE (MANUAL)" : status.getLabel());
            lblConnectionStatus.getStyleClass().removeAll(
                    "connection-status-online",
                    "connection-status-offline",
                    "connection-status-local"
            );
            lblConnectionStatus.getStyleClass().add(manualOfflineRequested
                    ? "connection-status-offline"
                    : status.getStyleClass());
        }
        if (lblConnectionDetail != null) {
            lblConnectionDetail.setText(manualOfflineRequested
                    ? "Automatic Central Server sync is paused. Local changes are queued until you go online again."
                    : status.getDetail());
        }
        if (btnGoOnline != null) {
            boolean offline = status.getStyleClass() != null
                    && status.getStyleClass().contains("connection-status-offline");
            boolean online = status.getStyleClass() != null
                    && status.getStyleClass().contains("connection-status-online");
            boolean autoMode = ServiceRegistry.getConfiguration().isAutomaticMode();
            btnGoOnline.setVisible(autoMode || offline);
            btnGoOnline.setManaged(autoMode || offline);
            btnGoOnline.setDisable(false);
            btnGoOnline.setText(online && !manualOfflineRequested ? "Go Offline" : "Go Online");
        }
    }

    private void restoreGoOnlineButton() {
        if (btnGoOnline != null) {
            btnGoOnline.setDisable(false);
            btnGoOnline.setText(isCurrentStatusOnline() && !manualOfflineRequested ? "Go Offline" : "Go Online");
        }
    }

    private boolean isCurrentStatusOnline() {
        return isStatusOnline(currentConnectionStatus);
    }

    private boolean isStatusOnline(ConnectionStatusService.ConnectionStatus status) {
        return status != null
                && status.getStyleClass() != null
                && status.getStyleClass().contains("connection-status-online");
    }

    private boolean canAccessBackupSync() {
        return ServiceRegistry.getConfiguration().usesLocalDatabase()
                && Session.hasRole(AccessControl.ROLE_SUPER_ADMIN);
    }

    private boolean canAccessDataMaintenance() {
        return Session.hasRole(AccessControl.ROLE_SUPER_ADMIN);
    }

    private static final class GoOnlineResult {
        private final ConnectionStatusService.ConnectionStatus status;
        private final String message;

        private GoOnlineResult(ConnectionStatusService.ConnectionStatus status, String message) {
            this.status = status;
            this.message = message;
        }
    }
}

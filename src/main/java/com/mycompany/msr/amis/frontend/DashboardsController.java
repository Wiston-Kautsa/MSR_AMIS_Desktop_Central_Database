package com.mycompany.msr.amis;

import java.io.IOException;
import java.net.URL;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class DashboardsController implements Initializable {
    private static final String FRONTEND_RESOURCE_ROOT = "/com/mycompany/msr/amis/frontend/";

    private final DashboardService dashboardService = ServiceRegistry.getDashboardService();
    private final ConnectionStatusService connectionStatusService = new ConnectionStatusService();
    private Node dashboardHome;
    private Timeline connectionStatusTimeline;
    private boolean connectionStatusCheckInProgress;

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
    @FXML private Button btnDataMaintenance;
    @FXML private Button btnSyncCenter;
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
            btnUsers.setManaged(AccessControl.canManageUsers());
            btnUsers.setVisible(AccessControl.canManageUsers());
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
            btnSyncCenter.setManaged(true);
            btnSyncCenter.setVisible(true);
        }
        if (lblTotalAssets != null) {
            refreshDashboard();
        }
        startConnectionStatusMonitor();
    }

    private void loadPage(String fxml) {
        try {
            String path = FRONTEND_RESOURCE_ROOT + fxml;
            URL resource = getClass().getResource(path);
            if (resource == null) {
                OperationFeedbackHelper.showError("Navigation Error", "The requested page could not be loaded.");
                return;
            }

            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            contentArea.getChildren().clear();

            if (root instanceof Region) {
                Region region = (Region) root;
                region.prefWidthProperty().bind(contentArea.widthProperty());
                region.prefHeightProperty().bind(contentArea.heightProperty());
            }

            contentArea.getChildren().add(root);
        } catch (Exception e) {
            e.printStackTrace();
            OperationFeedbackHelper.showError(
                    "Navigation Error",
                    "The requested page could not be loaded.\n\n" + e.getMessage()
            );
        }
    }

    @FXML
    private void openDashboard(ActionEvent event) {
        if (contentArea == null || dashboardHome == null) {
            return;
        }
        contentArea.getChildren().setAll(dashboardHome);
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
    @FXML private void openCreateAssignment() { loadPage("CreateAssignment.fxml"); }
    @FXML private void openDistributeEquipment() { loadPage("DistributeEquipment.fxml"); }
    @FXML private void openAssignmentList() { loadPage("AssignmentList.fxml"); }
    @FXML private void openReturnEquipment() { loadPage("ReturnEquipment.fxml"); }
    @FXML private void openInventoryReport() { loadPage("InventoryReport.fxml"); }
    @FXML private void openAssignmentReport() { loadPage("AssignmentReport.fxml"); }
    @FXML private void openDistributionReport() { loadPage("DistributionReport.fxml"); }
    @FXML private void openAssetHistory() { loadPage("AssetHistory.fxml"); }
    @FXML private void openReturnEquipmentList() { loadPage("ReturnEquipmentList.fxml"); }
    @FXML private void openReturnReport() { loadPage("ReturnReport.fxml"); }
    @FXML private void openOutstandingReport() { loadPage("OutstandingReport.fxml"); }

    @FXML
    private void openBackupSync() {
        try {
            if (!canAccessBackupSync()) {
                throw new SecurityException("Backup & Restore is available only to Admin and Super Admin in LOCAL_DATABASE mode.");
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
            AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
            loadPage("Users.fxml");
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
        loadPage("SyncCenter.fxml");
    }

    @FXML private void openAboutUs() { loadPage("AboutUs.fxml"); }

    @FXML
    private void openLogout(ActionEvent event) {
        try {
            stopConnectionStatusMonitor();
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

    private void applyConnectionStatus(ConnectionStatusService.ConnectionStatus status) {
        if (status == null) {
            return;
        }
        if (lblConnectionStatus != null) {
            lblConnectionStatus.setText(status.getLabel());
            lblConnectionStatus.getStyleClass().removeAll(
                    "connection-status-online",
                    "connection-status-offline",
                    "connection-status-local"
            );
            lblConnectionStatus.getStyleClass().add(status.getStyleClass());
        }
        if (lblConnectionDetail != null) {
            lblConnectionDetail.setText(status.getDetail());
        }
    }

    private boolean canAccessBackupSync() {
        return ServiceRegistry.getConfiguration().usesLocalDatabase()
                && Session.hasRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
    }

    private boolean canAccessDataMaintenance() {
        return Session.hasRole(AccessControl.ROLE_SUPER_ADMIN);
    }
}

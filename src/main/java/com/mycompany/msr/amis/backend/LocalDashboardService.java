package com.mycompany.msr.amis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;

public final class LocalDashboardService implements DashboardService {

    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();

    @Override
    public DashboardSummary getDashboardSummary() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        int totalAssets = executeCountQuery("SELECT COUNT(*) FROM equipment");
        int availableAssets = executeCountQuery(
                "SELECT COUNT(*) FROM equipment WHERE status = 'AVAILABLE'"
        );
        int assetsInUse = executeCountQuery(
                "SELECT COUNT(DISTINCT asset_code) FROM distribution WHERE returned = 0"
        );

        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        int borrowedThisMonth = executeDateBoundCount(
                "SELECT COUNT(*) FROM distribution WHERE DATE(date) >= ? AND DATE(date) <= DATE('now')",
                monthStart
        );
        int returnedThisMonth = executeDateBoundCount(
                "SELECT COUNT(*) FROM returns WHERE DATE(return_date) >= ? AND DATE(return_date) <= DATE('now')",
                monthStart
        );
        int stillInUse = executeDateBoundCount(
                "SELECT COUNT(*) FROM distribution WHERE DATE(date) >= ? AND DATE(date) <= DATE('now') AND returned = 0",
                monthStart
        );
        int outstandingWithRemarks = executeCountQuery(
                "SELECT COUNT(*) FROM distribution WHERE returned = 0 AND outstanding_remarks IS NOT NULL AND TRIM(outstanding_remarks) <> ''"
        );

        return new DashboardSummary(
                totalAssets,
                availableAssets,
                borrowedThisMonth,
                stillInUse,
                returnedThisMonth,
                assetsInUse,
                outstandingWithRemarks
        );
    }

    private int executeCountQuery(String sql) {
        try (Connection conn = DatabaseHandler.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load dashboard summary.", e);
        }
    }

    private int executeDateBoundCount(String sql, LocalDate startDate) {
        try (Connection conn = DatabaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, startDate.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load dashboard summary.", e);
        }
    }
}

package com.mycompany.msr.amis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class LocalAssetHistoryService implements AssetHistoryService {

    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();

    @Override
    public AssetHistoryResult getAssetHistory(String assetCode) {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        AssetHistorySummary summary = loadSummary(assetCode);
        if (summary == null) {
            return null;
        }
        return new AssetHistoryResult(summary, loadHistory(assetCode));
    }

    private AssetHistorySummary loadSummary(String assetCode) {
        String sql =
                "SELECT asset_code, serial_number, name, category, entry_date, status " +
                "FROM equipment WHERE LOWER(TRIM(asset_code)) = LOWER(TRIM(?))";

        try (Connection conn = DatabaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new AssetHistorySummary(
                        rs.getString("asset_code"),
                        rs.getString("serial_number"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("entry_date"),
                        rs.getString("status")
                );
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load asset summary.", e);
        }
    }

    private List<AssetHistoryRecord> loadHistory(String assetCode) {
        List<AssetHistoryRecord> records = new ArrayList<>();
        String sql =
                "SELECT activity_date, event_type, actor, affected_person, details, status " +
                "FROM (" +
                "    SELECT e.entry_date AS activity_date, " +
                "           'REGISTERED' AS event_type, " +
                "           COALESCE(e.source, 'System') AS actor, " +
                "           '' AS affected_person, " +
                "           'Asset added: ' || COALESCE(e.name, '') || " +
                "           CASE WHEN COALESCE(TRIM(e.category), '') = '' THEN '' ELSE ' | Category: ' || TRIM(e.category) END || " +
                "           CASE WHEN COALESCE(TRIM(e.condition), '') = '' THEN '' ELSE ' | Condition: ' || TRIM(e.condition) END || " +
                "           CASE WHEN COALESCE(TRIM(e.serial_number), '') = '' THEN '' ELSE ' | Serial: ' || TRIM(e.serial_number) END AS details, " +
                "           COALESCE(e.status, '') AS status, " +
                "           0 AS event_order, " +
                "           e.id AS record_id " +
                "    FROM equipment e " +
                "    WHERE LOWER(TRIM(e.asset_code)) = LOWER(TRIM(?)) " +
                "    UNION ALL " +
                "    SELECT d.assigned_at AS activity_date, " +
                "           'ISSUED' AS event_type, " +
                "           COALESCE(a.person, '') AS actor, " +
                "           COALESCE(d.assigned_to, '') AS affected_person, " +
                "           CASE WHEN COALESCE(TRIM(a.reason), '') = '' THEN 'Asset issued' ELSE 'Reason: ' || TRIM(a.reason) END || " +
                "           CASE WHEN COALESCE(TRIM(a.department), '') = '' THEN '' ELSE ' | Department: ' || TRIM(a.department) END || " +
                "           CASE WHEN COALESCE(TRIM(a.equipment_type), '') = '' THEN '' ELSE ' | Equipment type: ' || TRIM(a.equipment_type) END || " +
                "           CASE WHEN COALESCE(TRIM(d.phone), '') = '' THEN '' ELSE ' | Phone: ' || TRIM(d.phone) END || " +
                "           CASE WHEN COALESCE(TRIM(d.nid), '') = '' THEN '' ELSE ' | NID: ' || TRIM(d.nid) END AS details, " +
                "           CASE WHEN d.returned = 1 THEN 'RETURNED' ELSE 'ASSIGNED' END AS status, " +
                "           1 AS event_order, " +
                "           d.id AS record_id " +
                "    FROM distribution d " +
                "    LEFT JOIN assignments a ON a.id = d.assignment_id " +
                "    WHERE LOWER(TRIM(d.asset_code)) = LOWER(TRIM(?)) " +
                "    UNION ALL " +
                "    SELECT m.maintenance_date AS activity_date, " +
                "           CASE WHEN UPPER(COALESCE(m.status, '')) = 'COMPLETED' THEN 'MAINTENANCE COMPLETED' ELSE 'MAINTENANCE' END AS event_type, " +
                "           COALESCE(m.performed_by, '') AS actor, " +
                "           '' AS affected_person, " +
                "           'Issue: ' || COALESCE(m.issue, '') || " +
                "           CASE WHEN COALESCE(TRIM(m.action_taken), '') = '' THEN '' ELSE ' | Action taken: ' || TRIM(m.action_taken) END || " +
                "           CASE WHEN COALESCE(TRIM(m.cost), '') = '' THEN '' ELSE ' | Cost: ' || TRIM(m.cost) END AS details, " +
                "           COALESCE(m.status, '') AS status, " +
                "           3 AS event_order, " +
                "           m.id AS record_id " +
                "    FROM maintenance_log m " +
                "    WHERE LOWER(TRIM(m.asset_code)) = LOWER(TRIM(?)) " +
                "    UNION ALL " +
                "    SELECT r.return_date AS activity_date, " +
                "           'RETURNED' AS event_type, " +
                "           COALESCE(r.returned_by, '') AS actor, " +
                "           '' AS affected_person, " +
                "           CASE WHEN COALESCE(TRIM(r.condition), '') = '' THEN 'Asset returned' ELSE 'Condition: ' || TRIM(r.condition) END || " +
                "           CASE WHEN COALESCE(TRIM(r.remarks), '') = '' THEN '' ELSE ' | Remarks: ' || TRIM(r.remarks) END || " +
                "           CASE WHEN COALESCE(TRIM(r.phone), '') = '' THEN '' ELSE ' | Phone: ' || TRIM(r.phone) END || " +
                "           CASE WHEN COALESCE(TRIM(r.nid), '') = '' THEN '' ELSE ' | NID: ' || TRIM(r.nid) END AS details, " +
                "           'AVAILABLE' AS status, " +
                "           2 AS event_order, " +
                "           r.id AS record_id " +
                "    FROM returns r " +
                "    WHERE LOWER(TRIM(r.asset_code)) = LOWER(TRIM(?)) " +
                ") history " +
                "ORDER BY COALESCE(activity_date, '') DESC, event_order DESC, record_id DESC";

        try (Connection conn = DatabaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetCode);
            ps.setString(2, assetCode);
            ps.setString(3, assetCode);
            ps.setString(4, assetCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(new AssetHistoryRecord(
                            rs.getString("activity_date"),
                            rs.getString("event_type"),
                            rs.getString("actor"),
                            rs.getString("affected_person"),
                            rs.getString("details"),
                            rs.getString("status")
                    ));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load asset history.", e);
        }
        return records;
    }
}

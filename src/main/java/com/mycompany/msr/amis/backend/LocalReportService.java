package com.mycompany.msr.amis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class LocalReportService implements ReportService {

    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();

    @Override
    public List<Equipment> getInventoryReport() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        List<Equipment> data = new ArrayList<>();
        try (Connection conn = DatabaseHandler.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM equipment")) {
            while (rs.next()) {
                data.add(new Equipment(
                        rs.getInt("id"),
                        rs.getString("asset_code"),
                        rs.getString("serial_number"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("condition"),
                        rs.getString("source"),
                        rs.getString("entry_date"),
                        rs.getString("status")
                ));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load inventory report.", e);
        }
        return data;
    }

    @Override
    public List<Assignment> getAssignmentReport() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return new LocalAssignmentService().getAssignments();
    }

    @Override
    public List<Distribution> getDistributionReport() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        List<Distribution> data = new ArrayList<>();
        String sql =
                "SELECT d.*, a.person AS responsible_person FROM distribution d LEFT JOIN assignments a ON a.id = d.assignment_id";
        try (Connection conn = DatabaseHandler.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Distribution distribution = new Distribution(
                        rs.getString("asset_code"),
                        "",
                        rs.getString("assigned_to"),
                        rs.getString("phone"),
                        rs.getString("nid")
                );
                distribution.setAssignmentId(rs.getInt("assignment_id"));
                distribution.setResponsiblePerson(rs.getString("responsible_person"));
                distribution.setDistributionDate(parseDate(rs.getString("date")));
                distribution.setStatus(rs.getInt("returned") == 0 ? "ACTIVE" : "RETURNED");
                distribution.setOutstandingRemarks(rs.getString("outstanding_remarks"));
                data.add(distribution);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load distribution report.", e);
        }
        return data;
    }

    @Override
    public List<ReturnRecord> getReturnReport() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        List<ReturnRecord> data = new ArrayList<>();
        String sql =
                "SELECT r.asset_code, e.serial_number, e.name, e.category, e.source, d.date AS date_taken, " +
                        "a.person AS responsible_officer, a.equipment_type AS assignment_equipment_type, a.reason AS assignment_reason, " +
                        "r.returned_by, r.phone, r.nid, r.condition, r.remarks, r.return_date " +
                        "FROM returns r " +
                        "LEFT JOIN distribution d ON d.id = (" +
                        "SELECT d2.id FROM distribution d2 WHERE d2.asset_code = r.asset_code ORDER BY d2.id DESC LIMIT 1" +
                        ") " +
                        "LEFT JOIN assignments a ON a.id = d.assignment_id " +
                        "LEFT JOIN equipment e ON e.asset_code = r.asset_code " +
                        "ORDER BY r.return_date DESC, r.id DESC";
        try (Connection conn = DatabaseHandler.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                data.add(new ReturnRecord(
                        rs.getString("asset_code"),
                        rs.getString("serial_number"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("source"),
                        rs.getString("date_taken"),
                        rs.getString("responsible_officer"),
                        rs.getString("assignment_equipment_type"),
                        rs.getString("assignment_reason"),
                        rs.getString("returned_by"),
                        rs.getString("phone"),
                        rs.getString("nid"),
                        rs.getString("condition"),
                        rs.getString("remarks"),
                        rs.getString("return_date")
                ));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load return report.", e);
        }
        return data;
    }

    @Override
    public List<Distribution> getOutstandingReport() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        List<Distribution> data = new ArrayList<>();
        try (Connection conn = DatabaseHandler.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM distribution WHERE returned = 0")) {
            while (rs.next()) {
                Distribution distribution = new Distribution(
                        rs.getString("asset_code"),
                        "",
                        rs.getString("assigned_to"),
                        rs.getString("phone"),
                        rs.getString("nid")
                );
                distribution.setAssignmentId(rs.getInt("assignment_id"));
                distribution.setDistributionDate(parseDate(rs.getString("date")));
                distribution.setStatus("OUTSTANDING");
                distribution.setOutstandingRemarks(rs.getString("outstanding_remarks"));
                data.add(distribution);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load outstanding report.", e);
        }
        return data;
    }

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? LocalDate.now() : LocalDate.parse(value);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }
}

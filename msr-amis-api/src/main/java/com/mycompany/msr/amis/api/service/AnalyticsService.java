package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.dto.assignment.AssignmentResponse;
import com.mycompany.msr.amis.api.dto.dashboard.DashboardSummaryResponse;
import com.mycompany.msr.amis.api.dto.report.DistributionReportItemResponse;
import com.mycompany.msr.amis.api.dto.report.InventoryReportItemResponse;
import com.mycompany.msr.amis.api.dto.report.ReturnReportItemResponse;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final JdbcTemplate jdbcTemplate;
    private final OperationsService operationsService;

    public AnalyticsService(JdbcTemplate jdbcTemplate, OperationsService operationsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationsService = operationsService;
    }

    public DashboardSummaryResponse getDashboardSummary() {
        int totalAssets = count("SELECT COUNT(*) FROM equipment");
        int availableAssets = count(
                "SELECT COUNT(*) FROM equipment WHERE status = 'AVAILABLE'"
        );
        int assetsInUse = count("SELECT COUNT(DISTINCT asset_code) FROM distribution WHERE returned = FALSE");

        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        int borrowedThisMonth = countWithDateBound(
                "SELECT COUNT(*) FROM distribution WHERE assigned_at >= ? AND assigned_at <= ?",
                monthStart
        );
        int returnedThisMonth = countWithDateBound(
                "SELECT COUNT(*) FROM returns WHERE return_date >= ? AND return_date <= ?",
                monthStart
        );
        int stillInUseFromBorrowedThisMonth = countWithDateBound(
                "SELECT COUNT(*) FROM distribution WHERE assigned_at >= ? AND assigned_at <= ? AND returned = FALSE",
                monthStart
        );
        int outstandingWithRemarks = count(
                "SELECT COUNT(*) FROM distribution WHERE returned = FALSE AND outstanding_remarks IS NOT NULL AND TRIM(outstanding_remarks) <> ''"
        );

        return new DashboardSummaryResponse(
                totalAssets,
                availableAssets,
                borrowedThisMonth,
                stillInUseFromBorrowedThisMonth,
                returnedThisMonth,
                assetsInUse,
                outstandingWithRemarks
        );
    }

    public List<InventoryReportItemResponse> getInventoryReport() {
        return jdbcTemplate.query(
                "SELECT id, asset_code, name, category, serial_number, item_condition, source, entry_date, status, " +
                        "purchase_cost, location, warranty_expiry, supplier " +
                        "FROM equipment ORDER BY entry_date DESC, id DESC",
                (rs, rowNum) -> new InventoryReportItemResponse(
                        rs.getInt("id"),
                        rs.getString("asset_code"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("serial_number"),
                        rs.getString("item_condition"),
                        rs.getString("source"),
                        rs.getDate("entry_date").toLocalDate().toString(),
                        rs.getString("status"),
                        EquipmentFacadeService.formatLocalCurrencyValue(rs.getString("purchase_cost")),
                        rs.getString("location"),
                        rs.getDate("warranty_expiry") == null ? "" : rs.getDate("warranty_expiry").toLocalDate().toString(),
                        rs.getString("supplier")
                )
        );
    }

    public List<AssignmentResponse> getAssignmentReport() {
        return operationsService.getAssignments();
    }

    public List<DistributionReportItemResponse> getDistributionReport() {
        return jdbcTemplate.query(
                "SELECT d.id, d.asset_code, d.assigned_to, d.phone, d.nid, d.assignment_id, d.assigned_at, d.returned, d.outstanding_remarks, " +
                        "a.person AS responsible_person " +
                        "FROM distribution d " +
                        "LEFT JOIN assignments a ON a.id = d.assignment_id " +
                        "ORDER BY d.assigned_at DESC, d.id DESC",
                (rs, rowNum) -> mapDistributionReportItem(
                        rs.getInt("id"),
                        rs.getString("asset_code"),
                        rs.getString("responsible_person"),
                        rs.getString("assigned_to"),
                        rs.getString("phone"),
                        rs.getString("nid"),
                        rs.getInt("assignment_id"),
                        rs.getDate("assigned_at").toLocalDate().toString(),
                        rs.getBoolean("returned") ? "RETURNED" : "ACTIVE",
                        rs.getString("outstanding_remarks")
                )
        );
    }

    public List<ReturnReportItemResponse> getReturnReport() {
        return jdbcTemplate.query(
                "SELECT r.asset_code, e.serial_number, e.name, e.category, e.source, d.assigned_at AS date_taken, " +
                        "a.person AS responsible_officer, a.equipment_type AS assignment_equipment_type, a.reason AS assignment_reason, " +
                        "r.returned_by, r.phone, r.nid, r.item_condition, r.remarks, r.return_date " +
                        "FROM returns r " +
                        "LEFT JOIN distribution d ON d.id = (" +
                        "SELECT d2.id FROM distribution d2 WHERE d2.asset_code = r.asset_code ORDER BY d2.id DESC LIMIT 1" +
                        ") " +
                        "LEFT JOIN assignments a ON a.id = d.assignment_id " +
                        "LEFT JOIN equipment e ON e.asset_code = r.asset_code " +
                        "ORDER BY r.return_date DESC, r.id DESC",
                (rs, rowNum) -> new ReturnReportItemResponse(
                        rs.getString("asset_code"),
                        rs.getString("serial_number"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("source"),
                        toDateString(rs.getDate("date_taken")),
                        rs.getString("responsible_officer"),
                        rs.getString("assignment_equipment_type"),
                        rs.getString("assignment_reason"),
                        rs.getString("returned_by"),
                        rs.getString("phone"),
                        rs.getString("nid"),
                        rs.getString("item_condition"),
                        rs.getString("remarks"),
                        toDateString(rs.getDate("return_date"))
                )
        );
    }

    public List<DistributionReportItemResponse> getOutstandingReport() {
        return jdbcTemplate.query(
                "SELECT DISTINCT ON (LOWER(TRIM(asset_code))) " +
                        "id, asset_code, assigned_to, phone, nid, assignment_id, assigned_at, outstanding_remarks " +
                        "FROM distribution " +
                        "WHERE returned = FALSE " +
                        "ORDER BY LOWER(TRIM(asset_code)), assigned_at DESC, id DESC",
                (rs, rowNum) -> mapDistributionReportItem(
                        rs.getInt("id"),
                        rs.getString("asset_code"),
                        "",
                        rs.getString("assigned_to"),
                        rs.getString("phone"),
                        rs.getString("nid"),
                        rs.getInt("assignment_id"),
                        toDateString(rs.getDate("assigned_at")),
                        "OUTSTANDING",
                        rs.getString("outstanding_remarks")
                )
        );
    }

    private DistributionReportItemResponse mapDistributionReportItem(
            int id,
            String assetCode,
            String responsiblePerson,
            String assignedTo,
            String phone,
            String nid,
            int assignmentId,
            String date,
            String status,
            String outstandingRemarks
    ) {
        return new DistributionReportItemResponse(
                id,
                assetCode,
                responsiblePerson,
                assignedTo,
                phone,
                nid,
                assignmentId,
                date,
                status,
                outstandingRemarks
        );
    }

    private int count(String sql) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }

    private int countWithDateBound(String sql, LocalDate monthStart) {
        Integer result = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                Date.valueOf(monthStart),
                Date.valueOf(LocalDate.now())
        );
        return result == null ? 0 : result;
    }

    private String toDateString(Date value) {
        return value == null ? "" : value.toLocalDate().toString();
    }
}

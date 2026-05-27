package com.mycompany.msr.amis.api.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mycompany.msr.amis.api.dto.sync.SyncPushRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncQueueRecordRequest;
import com.mycompany.msr.amis.api.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class SyncServiceTest {

    @Test
    void pushNowDispatchesDepartmentRecords() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SyncQueueService syncQueueService = mock(SyncQueueService.class);
        SyncAuditService syncAuditService = mock(SyncAuditService.class);
        SyncLockService syncLockService = mock(SyncLockService.class);
        SyncValidationService syncValidationService = mock(SyncValidationService.class);
        DepartmentService departmentService = mock(DepartmentService.class);

        when(jdbcTemplate.queryForObject(startsWith("INSERT INTO sync_sessions"), eq(Long.class), any(), any(), any(), any()))
                .thenReturn(42L);
        when(jdbcTemplate.query(eq("SELECT value FROM sync_settings WHERE key=?"), any(RowMapper.class), eq("sync.lock_ttl_seconds")))
                .thenReturn(List.of("900"));
        when(syncQueueService.enqueue(eq(42L), eq("admin@example.com"), eq("MACHINE-1"), any()))
                .thenReturn(7L);
        when(syncValidationService.validateRecord(any())).thenReturn(List.of());
        when(syncQueueService.alreadyProcessed("dept-sync-1")).thenReturn(false);
        when(departmentService.createDepartment("admin@example.com", "ICT")).thenReturn("ICT");

        SyncService service = new SyncService(
                jdbcTemplate,
                syncQueueService,
                syncAuditService,
                mock(SyncConflictService.class),
                syncLockService,
                syncValidationService,
                mock(EquipmentSyncHandler.class),
                mock(OperationsService.class),
                mock(UserManagementService.class),
                departmentService,
                mock(UserRepository.class)
        );

        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("name", "ICT");
        service.pushNow("admin@example.com", new SyncPushRequest(
                "SESSION-1",
                "MACHINE-1",
                "Machine 1",
                false,
                List.of("DEPARTMENT"),
                List.of(new SyncQueueRecordRequest("DEPARTMENT", "ICT", "CREATE", "dept-sync-1", "", payload, null))
        ));

        verify(departmentService).createDepartment("admin@example.com", "ICT");
        verify(syncQueueService).markApplied(7L);
        verify(syncQueueService).markIdempotencyKeyProcessed(
                eq("admin@example.com"),
                eq("MACHINE-1"),
                eq("dept-sync-1"),
                eq("DEPARTMENT"),
                eq("ICT"),
                eq("CREATE"),
                eq(payload)
        );
    }
}

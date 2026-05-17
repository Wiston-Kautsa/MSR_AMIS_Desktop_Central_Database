package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.exception.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SyncLockService {

    private final JdbcTemplate jdbcTemplate;

    public SyncLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquire(String actor, String machineId, String sessionToken, int ttlSeconds) {
        expireOldLocks();
        try {
            int inserted = jdbcTemplate.update(
                    "INSERT INTO sync_locks(locked_by, machine_id, session_token, expires_at, status) " +
                            "VALUES (?, ?, ?, ?, 'ACTIVE') ON CONFLICT DO NOTHING",
                    normalize(actor),
                    normalize(machineId),
                    normalize(sessionToken),
                    Timestamp.from(Instant.now().plusSeconds(Math.max(ttlSeconds, 60)))
            );
            if (inserted == 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Another sync operation is already running.");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Unable to acquire sync lock: " + exception.getMessage());
        }
    }

    public void release(String sessionToken) {
        jdbcTemplate.update(
                "UPDATE sync_locks SET status='RELEASED' WHERE status='ACTIVE' AND session_token=?",
                normalize(sessionToken)
        );
    }

    public void forceRelease(String actor) {
        jdbcTemplate.update("UPDATE sync_locks SET status='FORCE_RELEASED' WHERE status='ACTIVE'");
    }

    public LockState getState() {
        return jdbcTemplate.query(
                "SELECT locked_by, machine_id, session_token, started_at, expires_at FROM sync_locks WHERE status='ACTIVE' ORDER BY started_at DESC LIMIT 1",
                (rs, rowNum) -> new LockState(
                        true,
                        rs.getString("locked_by"),
                        rs.getString("machine_id"),
                        rs.getString("session_token"),
                        rs.getTimestamp("started_at").toString(),
                        rs.getTimestamp("expires_at").toString()
                )
        ).stream().findFirst().orElse(new LockState(false, "", "", "", "", ""));
    }

    private void expireOldLocks() {
        jdbcTemplate.update("UPDATE sync_locks SET status='EXPIRED' WHERE status='ACTIVE' AND expires_at < CURRENT_TIMESTAMP");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record LockState(
            boolean active,
            String lockedBy,
            String machineId,
            String sessionToken,
            String startedAt,
            String expiresAt
    ) {
    }
}

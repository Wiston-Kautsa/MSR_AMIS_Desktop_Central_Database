package com.mycompany.msr.amis.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ActionAuditService {

    private final JdbcTemplate jdbcTemplate;

    public ActionAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void log(String actor, String action, String entity, String entityId, String details) {
        jdbcTemplate.update(
                "INSERT INTO audit_log(action, entity, entity_id, performed_by, username, module_name, details, action_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                normalize(action),
                normalize(entity),
                normalize(entityId),
                normalize(actor),
                normalize(actor),
                normalize(entity),
                normalize(details)
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

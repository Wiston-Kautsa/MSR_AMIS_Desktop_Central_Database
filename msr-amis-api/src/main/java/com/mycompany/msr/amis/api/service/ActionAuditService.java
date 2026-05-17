package com.mycompany.msr.amis.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ActionAuditService {

    private final JdbcTemplate jdbcTemplate;
    private final SuperUserStatusEmailService superUserStatusEmailService;

    public ActionAuditService(JdbcTemplate jdbcTemplate,
                              SuperUserStatusEmailService superUserStatusEmailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.superUserStatusEmailService = superUserStatusEmailService;
    }

    public void log(String actor, String action, String entity, String entityId, String details) {
        String normalizedAction = normalize(action);
        String normalizedEntity = normalize(entity);
        String normalizedEntityId = normalize(entityId);
        String normalizedActor = normalize(actor);
        String normalizedDetails = normalize(details);

        jdbcTemplate.update(
                "INSERT INTO audit_log(action, entity, entity_id, performed_by, username, module_name, details, action_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                normalizedAction,
                normalizedEntity,
                normalizedEntityId,
                normalizedActor,
                normalizedActor,
                normalizedEntity,
                normalizedDetails
        );
        superUserStatusEmailService.sendAuditStatus(
                normalizedActor,
                normalizedAction,
                normalizedEntity,
                normalizedEntityId,
                normalizedDetails
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

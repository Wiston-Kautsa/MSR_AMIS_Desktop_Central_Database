package com.mycompany.msr.amis.api.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SuperUserStatusEmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuperUserStatusEmailService.class);

    private final JavaMailSender mailSender;
    private final Environment environment;

    public SuperUserStatusEmailService(JavaMailSender mailSender,
                                       Environment environment) {
        this.mailSender = mailSender;
        this.environment = environment;
    }

    public void sendAuditStatus(String actor, String action, String entity, String entityId, String details) {
        try {
            if (!isEnabled()) {
                return;
            }
            if (action != null && action.startsWith("PASSWORD_RESET_")) {
                return;
            }

            Set<String> recipients = configuredPrimarySuperAdminEmails();
            if (recipients.isEmpty()) {
                return;
            }

            String from = requiredConfig("MSR_AMIS_SMTP_FROM");
            String subject = "MSR AMIS system status: " + valueOrUnknown(action);
            String body = buildBody(actor, action, entity, entityId, details);

            for (String recipient : recipients) {
                try {
                    sendMessage(from, recipient, subject, body);
                } catch (Exception exception) {
                    LOGGER.warn("Failed to send system status email to {}", recipient, exception);
                }
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to prepare system status email notification.", exception);
        }
    }

    private void sendMessage(String from, String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private boolean isEnabled() {
        return Boolean.parseBoolean(environment.getProperty("MSR_AMIS_SUPER_USER_STATUS_EMAILS_ENABLED", "false"));
    }

    private Set<String> configuredPrimarySuperAdminEmails() {
        Set<String> emails = new LinkedHashSet<>();
        String email = normalize(environment.getProperty("MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL"));
        if (!email.isBlank()) {
            emails.add(email);
        }
        return emails;
    }

    private String requiredConfig(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing configuration value: " + name);
        }
        return value.trim();
    }

    private String buildBody(String actor, String action, String entity, String entityId, String details) {
        return "A system status event was recorded in MSR AMIS.\n\n"
                + "Time: " + OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "\n"
                + "Action: " + valueOrUnknown(action) + "\n"
                + "Module: " + valueOrUnknown(entity) + "\n"
                + "Record ID: " + valueOrUnknown(entityId) + "\n"
                + "Performed by: " + valueOrUnknown(actor) + "\n"
                + "Details: " + valueOrUnknown(details) + "\n";
    }

    private String valueOrUnknown(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? "N/A" : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

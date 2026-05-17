package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.domain.UserAccount;
import com.mycompany.msr.amis.api.domain.UserRole;
import com.mycompany.msr.amis.api.domain.UserStatus;
import com.mycompany.msr.amis.api.repository.UserRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class OperationalNotificationEmailService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final boolean enabled;
    private final String fromAddress;
    private final String fallbackRecipients;

    public OperationalNotificationEmailService(JavaMailSender mailSender,
                                               UserRepository userRepository,
                                               @Value("${MSR_AMIS_OPERATION_EMAILS_ENABLED:false}") boolean enabled,
                                               @Value("${MSR_AMIS_SMTP_USERNAME:}") String fromAddress,
                                               @Value("${MSR_AMIS_OPERATION_EMAIL_RECIPIENTS:}") String fallbackRecipients) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.enabled = enabled;
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
        this.fallbackRecipients = fallbackRecipients == null ? "" : fallbackRecipients.trim();
    }

    public void notifyDistribution(String actor, int assignmentId, List<String> assetCodes) {
        send(
                "MSR-AMIS equipment distribution",
                "Equipment was distributed by " + safe(actor) + ".\n\nAssignment ID: " + assignmentId +
                        "\nAssets: " + String.join(", ", assetCodes)
        );
    }

    public void notifyReturns(String actor, int assignmentId, List<String> assetCodes) {
        send(
                "MSR-AMIS equipment return",
                "Equipment was returned by " + safe(actor) + ".\n\nAssignment ID: " + assignmentId +
                        "\nAssets: " + String.join(", ", assetCodes)
        );
    }

    private void send(String subject, String body) {
        List<String> targets = recipientList();
        if (!enabled || targets.isEmpty() || fromAddress.isBlank()) {
            return;
        }

        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(targets.toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
        } catch (Exception ignored) {
            // Operational emails must not roll back completed distribution or return transactions.
        }
    }

    private List<String> recipientList() {
        List<String> superAdminEmails = userRepository.findByRoleAndStatusOrderByFullNameAsc(UserRole.SUPER_ADMIN, UserStatus.ACTIVE)
                .stream()
                .map(UserAccount::getEmail)
                .map(OperationalNotificationEmailService::safeEmail)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (!superAdminEmails.isEmpty()) {
            return superAdminEmails;
        }

        if (fallbackRecipients.isBlank()) {
            return List.of();
        }
        return Arrays.stream(fallbackRecipients.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }

    private static String safeEmail(String value) {
        return value == null ? "" : value.trim();
    }
}

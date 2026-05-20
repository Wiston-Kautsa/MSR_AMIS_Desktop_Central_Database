package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.exception.ApiException;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetEmailService.class);

    private final JavaMailSender mailSender;
    private final Environment environment;

    public PasswordResetEmailService(JavaMailSender mailSender, Environment environment) {
        this.mailSender = mailSender;
        this.environment = environment;
    }

    public void sendResetCode(String recipientEmail, String resetCode) {
        String from = requiredConfig("MSR_AMIS_SMTP_FROM");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipientEmail);
            message.setSubject("MSR AMIS password reset code");
            message.setText(
                    "Use this code to reset your MSR AMIS password:\n\n"
                            + resetCode + "\n\n"
                            + "This code expires in 10 minutes.\n"
                            + "If you did not request a password reset, contact your administrator."
            );
            mailSender.send(message);
            LOGGER.info("Password reset code email sent to {}", recipientEmail);
        } catch (MailAuthenticationException exception) {
            LOGGER.warn("Password reset code email authentication failed for {}", recipientEmail, exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "SMTP authentication failed. Check the sender email password and make sure Authenticated SMTP is enabled for the mailbox.");
        } catch (Exception exception) {
            LOGGER.warn("Password reset code email failed for {}", recipientEmail, exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to send reset email. Check SMTP configuration.");
        }
    }

    private String requiredConfig(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Email reset is not configured. Missing configuration value: " + name);
        }
        return value.trim();
    }
}

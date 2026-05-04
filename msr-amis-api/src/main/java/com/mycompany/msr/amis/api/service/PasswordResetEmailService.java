package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailService {

    private final JavaMailSender mailSender;

    public PasswordResetEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendResetCode(String recipientEmail, String resetCode) {
        String from = requiredEnv("MSR_AMIS_SMTP_FROM");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipientEmail);
            message.setSubject("MSR AMIS password reset code");
            message.setText(
                    "Your MSR AMIS password reset code is " + resetCode + ".\n\n"
                            + "The code expires in 10 minutes.\n"
                            + "If you did not request this reset, contact your administrator."
            );
            mailSender.send(message);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to send reset email. Check SMTP configuration.");
        }
    }

    private String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Email reset is not configured. Missing environment variable: " + name);
        }
        return value.trim();
    }
}

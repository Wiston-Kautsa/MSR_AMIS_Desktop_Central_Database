package com.mycompany.msr.amis;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

public final class EmailService {

    private static final String ENV_SMTP_HOST = "MSR_AMIS_SMTP_HOST";
    private static final String ENV_SMTP_PORT = "MSR_AMIS_SMTP_PORT";
    private static final String ENV_SMTP_USER = "MSR_AMIS_SMTP_USERNAME";
    private static final String ENV_SMTP_PASSWORD = "MSR_AMIS_SMTP_PASSWORD";
    private static final String ENV_SMTP_FROM = "MSR_AMIS_SMTP_FROM";
    private static final String ENV_SMTP_SSL = "MSR_AMIS_SMTP_SSL";
    private static final String ENV_SMTP_STARTTLS = "MSR_AMIS_SMTP_STARTTLS";
    private static final String ENV_SMTP_TIMEOUT_MS = "MSR_AMIS_SMTP_TIMEOUT_MS";
    private static final int DEFAULT_TIMEOUT_MS = 10000;

    private EmailService() {
    }

    public static void sendPasswordResetCode(String recipientEmail, String resetCode) throws Exception {
        String smtpHost = requiredEnv(ENV_SMTP_HOST);
        int smtpPort = requiredIntEnv(ENV_SMTP_PORT);
        String smtpUser = requiredEnv(ENV_SMTP_USER);
        String smtpPassword = requiredEnv(ENV_SMTP_PASSWORD);
        String smtpFrom = requiredEnv(ENV_SMTP_FROM);
        Boolean configuredSsl = optionalBooleanEnv(ENV_SMTP_SSL);
        Boolean configuredStartTls = optionalBooleanEnv(ENV_SMTP_STARTTLS);
        boolean useSsl = configuredSsl != null ? configuredSsl : smtpPort == 465;
        boolean useStartTls = configuredStartTls != null ? configuredStartTls : smtpPort == 587;

        // Port 587 is the standard SMTP submission port and should prefer STARTTLS.
        if (smtpPort == 587 && useStartTls) {
            useSsl = false;
        }
        if (useSsl && useStartTls) {
            throw new Exception("Email reset is not configured correctly. Enable either SSL or STARTTLS, not both.");
        }
        int timeoutMs = optionalIntEnv(ENV_SMTP_TIMEOUT_MS, DEFAULT_TIMEOUT_MS);

        String subject = "MSR AMIS password reset code";
        String body = "Your MSR AMIS password reset code is " + resetCode + ".\r\n\r\n"
                + "The code expires in 10 minutes.\r\n"
                + "If you did not request this reset, contact your administrator.";

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", Integer.toString(smtpPort));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", Integer.toString(timeoutMs));
        props.put("mail.smtp.timeout", Integer.toString(timeoutMs));
        props.put("mail.smtp.writetimeout", Integer.toString(timeoutMs));
        props.put("mail.smtp.ssl.enable", Boolean.toString(useSsl));
        props.put("mail.smtp.starttls.enable", Boolean.toString(useStartTls));

        jakarta.mail.Session mailSession = jakarta.mail.Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPassword);
            }
        });

        MimeMessage message = new MimeMessage(mailSession);
        try {
            message.setFrom(new InternetAddress(smtpFrom));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail, false));
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");
            Transport.send(message);
        } catch (MessagingException e) {
            throw new Exception("Failed to send reset email. " + safeMessage(e), e);
        }
    }

    private static String requiredEnv(String name) throws Exception {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new Exception("Email reset is not configured. Missing environment variable: " + name);
        }
        return value.trim();
    }

    private static String optionalEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static int requiredIntEnv(String name) throws Exception {
        String value = requiredEnv(name);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new Exception("Email reset is not configured correctly. " + name + " must be a whole number.");
        }
    }

    private static int optionalIntEnv(String name, int defaultValue) throws Exception {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new Exception("Email reset is not configured correctly. " + name + " must be a whole number.");
        }
    }

    private static Boolean optionalBooleanEnv(String name) throws Exception {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
            return Boolean.parseBoolean(normalized);
        }
        throw new Exception("Email reset is not configured correctly. " + name + " must be true or false.");
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? "Unknown SMTP error."
                : message;
    }
}

package com.mycompany.msr.amis;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final Path ENV_FILE = Path.of(".env");
    private static final int DEFAULT_TIMEOUT_MS = 10000;

    private EmailService() {
    }

    public static void sendPasswordResetCode(String recipientEmail, String resetCode) throws Exception {
        Map<String, String> envFileValues = loadEnvFile();
        String smtpHost = requiredConfig(ENV_SMTP_HOST, envFileValues);
        int smtpPort = requiredIntConfig(ENV_SMTP_PORT, envFileValues);
        String smtpUser = requiredConfig(ENV_SMTP_USER, envFileValues);
        String smtpPassword = requiredConfig(ENV_SMTP_PASSWORD, envFileValues);
        String smtpFrom = requiredConfig(ENV_SMTP_FROM, envFileValues);
        Boolean configuredSsl = optionalBooleanConfig(ENV_SMTP_SSL, envFileValues);
        Boolean configuredStartTls = optionalBooleanConfig(ENV_SMTP_STARTTLS, envFileValues);
        boolean useSsl = configuredSsl != null ? configuredSsl : smtpPort == 465;
        boolean useStartTls = configuredStartTls != null ? configuredStartTls : smtpPort == 587;

        // Port 587 is the standard SMTP submission port and should prefer STARTTLS.
        if (smtpPort == 587 && useStartTls) {
            useSsl = false;
        }
        if (useSsl && useStartTls) {
            throw new Exception("Email reset is not configured correctly. Enable either SSL or STARTTLS, not both.");
        }
        int timeoutMs = optionalIntConfig(ENV_SMTP_TIMEOUT_MS, DEFAULT_TIMEOUT_MS, envFileValues);

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

    private static String requiredConfig(String name, Map<String, String> envFileValues) throws Exception {
        String value = resolveConfig(name, envFileValues);
        if (value == null || value.isBlank()) {
            throw new Exception("Email reset is not configured. Missing configuration value: " + name);
        }
        return value.trim();
    }

    private static String resolveConfig(String name, Map<String, String> envFileValues) {
        String value = envFileValues.get(name);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int requiredIntConfig(String name, Map<String, String> envFileValues) throws Exception {
        String value = requiredConfig(name, envFileValues);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new Exception("Email reset is not configured correctly. " + name + " must be a whole number.");
        }
    }

    private static int optionalIntConfig(String name, int defaultValue, Map<String, String> envFileValues) throws Exception {
        String value = resolveConfig(name, envFileValues);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new Exception("Email reset is not configured correctly. " + name + " must be a whole number.");
        }
    }

    private static Boolean optionalBooleanConfig(String name, Map<String, String> envFileValues) throws Exception {
        String value = resolveConfig(name, envFileValues);
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
            return Boolean.parseBoolean(normalized);
        }
        throw new Exception("Email reset is not configured correctly. " + name + " must be true or false.");
    }

    private static Map<String, String> loadEnvFile() {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(ENV_FILE)) {
            return values;
        }

        try {
            List<String> lines = Files.readAllLines(ENV_FILE);
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }

                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException ignored) {
            // OS environment variables can still provide SMTP settings.
        }
        return values;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? "Unknown SMTP error."
                : message;
    }
}

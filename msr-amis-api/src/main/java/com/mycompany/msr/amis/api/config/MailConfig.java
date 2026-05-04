package com.mycompany.msr.amis.api.config;

import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender() {
        String host = requiredEnv("MSR_AMIS_SMTP_HOST");
        int port = parseInt(requiredEnv("MSR_AMIS_SMTP_PORT"), "MSR_AMIS_SMTP_PORT");
        String username = requiredEnv("MSR_AMIS_SMTP_USERNAME");
        String password = requiredEnv("MSR_AMIS_SMTP_PASSWORD");
        Boolean configuredSsl = optionalBooleanEnv("MSR_AMIS_SMTP_SSL");
        Boolean configuredStartTls = optionalBooleanEnv("MSR_AMIS_SMTP_STARTTLS");
        boolean useSsl = configuredSsl != null ? configuredSsl : port == 465;
        boolean useStartTls = configuredStartTls != null ? configuredStartTls : port == 587;

        if (port == 587 && useStartTls) {
            useSsl = false;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password.trim());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", Boolean.toString(useSsl));
        props.put("mail.smtp.starttls.enable", Boolean.toString(useStartTls));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value.trim();
    }

    private static int parseInt(String value, String name) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(name + " must be a whole number.");
        }
    }

    private static Boolean optionalBooleanEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(value.trim()) || "false".equalsIgnoreCase(value.trim())) {
            return Boolean.parseBoolean(value.trim());
        }
        throw new IllegalStateException(name + " must be true or false.");
    }
}

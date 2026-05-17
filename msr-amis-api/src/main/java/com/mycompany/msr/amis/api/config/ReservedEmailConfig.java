package com.mycompany.msr.amis.api.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ReservedEmailConfig {

    private final String primarySuperAdminEmail;
    private final String setupAdminEmail;
    private final String setupUserEmail;
    private final Set<String> reservedEmails;
    private final Set<String> bootstrapEmails;

    public ReservedEmailConfig(Environment environment) {
        this.primarySuperAdminEmail = normalizeEmail(environment.getProperty(
                "MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL",
                ""
        ));
        this.setupAdminEmail = normalizeEmail(environment.getProperty(
                "MSR_AMIS_SETUP_ADMIN_EMAIL",
                ""
        ));
        this.setupUserEmail = normalizeEmail(environment.getProperty(
                "MSR_AMIS_SETUP_USER_EMAIL",
                ""
        ));

        this.bootstrapEmails = new LinkedHashSet<>();
        addIfPresent(bootstrapEmails, setupAdminEmail);
        addIfPresent(bootstrapEmails, setupUserEmail);

        this.reservedEmails = new LinkedHashSet<>();
        addIfPresent(reservedEmails, primarySuperAdminEmail);
        addAll(reservedEmails, configuredEmails(
                environment,
                "MSR_AMIS_RESERVED_SUPER_ADMIN_EMAILS",
                primarySuperAdminEmail
        ));
        addAll(reservedEmails, configuredEmails(
                environment,
                "MSR_AMIS_RESERVED_ADMIN_EMAILS",
                setupAdminEmail
        ));
        addAll(reservedEmails, configuredEmails(
                environment,
                "MSR_AMIS_RESERVED_USER_EMAILS",
                setupUserEmail
        ));
    }

    public String primarySuperAdminEmail() {
        return primarySuperAdminEmail;
    }

    public boolean isPrimarySuperAdminEmail(String email) {
        return primarySuperAdminEmail.equals(normalizeEmail(email));
    }

    public boolean isReservedEmail(String email) {
        return reservedEmails.contains(normalizeEmail(email));
    }

    public boolean isBootstrapEmail(String email) {
        return bootstrapEmails.contains(normalizeEmail(email));
    }

    public boolean isConfiguredAccountEmail(String email) {
        return isPrimarySuperAdminEmail(email) || isBootstrapEmail(email) || isReservedEmail(email);
    }

    private void addAll(Set<String> target, String rawEmails) {
        if (rawEmails == null || rawEmails.isBlank()) {
            return;
        }
        Arrays.stream(rawEmails.split(","))
                .map(ReservedEmailConfig::normalizeEmail)
                .filter(value -> !value.isBlank())
                .forEach(target::add);
    }

    private void addIfPresent(Set<String> target, String email) {
        if (!email.isBlank()) {
            target.add(email);
        }
    }

    private String configuredEmails(Environment environment, String key, String fallback) {
        String value = environment.getProperty(key);
        return value == null ? fallback : value;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}

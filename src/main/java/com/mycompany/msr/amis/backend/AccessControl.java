package com.mycompany.msr.amis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AccessControl {

    private static final String PRIMARY_SUPER_ADMIN_ENV = "MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL";
    private static final String SETUP_ADMIN_ENV = "MSR_AMIS_SETUP_ADMIN_EMAIL";
    private static final String SETUP_USER_ENV = "MSR_AMIS_SETUP_USER_EMAIL";
    private static final String RESERVED_SUPER_ADMIN_ENV = "MSR_AMIS_RESERVED_SUPER_ADMIN_EMAILS";
    private static final String RESERVED_ADMIN_ENV = "MSR_AMIS_RESERVED_ADMIN_EMAILS";
    private static final String RESERVED_USER_ENV = "MSR_AMIS_RESERVED_USER_EMAILS";
    public static final String PRIMARY_SUPER_ADMIN_EMAIL = resolveConfig(PRIMARY_SUPER_ADMIN_ENV, "");
    public static final String DEFAULT_ADMIN_EMAIL = resolveConfig(SETUP_ADMIN_ENV, "");
    public static final String DEFAULT_USER_EMAIL = resolveConfig(SETUP_USER_ENV, "");
    private static final Set<String> RESERVED_EMAILS = resolveReservedEmails();
    private static final Set<String> TEMPORARY_SETUP_EMAILS = resolveTemporarySetupEmails();
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_FROZEN = "FROZEN";
    public static final String STATUS_RETIRED = "RETIRED";

    private AccessControl() {
    }

    public static void requireRole(String... allowedRoles) {
        if (!Session.hasRole(allowedRoles)) {
            throw new SecurityException("Access denied.");
        }
    }

    public static boolean canManageUsers() {
        return Session.isSetupMode() || Session.hasRole(ROLE_SUPER_ADMIN, ROLE_ADMIN);
    }

    public static boolean canManageDepartments() {
        return Session.hasRole(ROLE_SUPER_ADMIN, ROLE_ADMIN);
    }

    public static boolean canDeleteRecords() {
        return Session.hasRole(ROLE_SUPER_ADMIN);
    }

    public static boolean canManageLifecycleRecords() {
        return Session.hasRole(ROLE_SUPER_ADMIN, ROLE_ADMIN);
    }

    public static boolean canViewAuditLogs() {
        return Session.hasRole(ROLE_SUPER_ADMIN, ROLE_ADMIN);
    }

    public static boolean canAccessSyncCenter() {
        return Session.hasRole(ROLE_SUPER_ADMIN, ROLE_ADMIN);
    }

    public static boolean canRetryRejectedSyncItems() {
        return Session.hasRole(ROLE_SUPER_ADMIN, ROLE_ADMIN);
    }

    public static boolean canAssignRole(String role) {
        if (Session.isSetupMode()) {
            return ROLE_ADMIN.equalsIgnoreCase(role)
                    || ROLE_USER.equalsIgnoreCase(role);
        }
        if (Session.hasRole(ROLE_SUPER_ADMIN)) {
            return ROLE_SUPER_ADMIN.equalsIgnoreCase(role)
                    || ROLE_ADMIN.equalsIgnoreCase(role)
                    || ROLE_USER.equalsIgnoreCase(role);
        }
        if (Session.hasRole(ROLE_ADMIN)) {
            return ROLE_ADMIN.equalsIgnoreCase(role)
                    || ROLE_USER.equalsIgnoreCase(role);
        }
        if (Session.hasRole(ROLE_USER)) {
            return ROLE_USER.equalsIgnoreCase(role);
        }
        return false;
    }

    public static boolean canManageTarget(User target) {
        if (target == null || Session.getCurrentUser() == null) {
            return false;
        }
        if (Session.hasRole(ROLE_SUPER_ADMIN)) {
            return true;
        }
        if (Session.hasRole(ROLE_ADMIN)) {
            return !ROLE_SUPER_ADMIN.equalsIgnoreCase(target.getRole());
        }
        return Session.hasRole(ROLE_USER) && ROLE_USER.equalsIgnoreCase(target.getRole());
    }

    public static boolean isProtectedSuperAdmin(User user) {
        return user != null && ROLE_SUPER_ADMIN.equalsIgnoreCase(user.getRole());
    }

    public static boolean isPrimarySuperAdminEmail(String email) {
        return email != null && PRIMARY_SUPER_ADMIN_EMAIL.equalsIgnoreCase(email.trim());
    }

    public static boolean isTemporarySetupAccountEmail(String email) {
        if (email == null) {
            return false;
        }
        return TEMPORARY_SETUP_EMAILS.contains(normalizeEmail(email));
    }

    public static boolean isKnownTemporaryAccountEmail(String email) {
        return RESERVED_EMAILS.contains(normalizeEmail(email));
    }

    public static boolean isPrimarySuperAdmin(User user) {
        return user != null && isPrimarySuperAdminEmail(user.getEmail());
    }

    private static String resolveConfig(String key, String fallback) {
        String configured = System.getenv(key);
        if (configured == null || configured.isBlank()) {
            configured = readEnvFileValue(key);
        }
        return configured == null || configured.isBlank()
                ? normalizeEmail(fallback)
                : normalizeEmail(configured);
    }

    private static Set<String> resolveReservedEmails() {
        Set<String> emails = new LinkedHashSet<>();
        addReserved(emails, PRIMARY_SUPER_ADMIN_EMAIL);
        addReserved(emails, resolveListConfig(RESERVED_SUPER_ADMIN_ENV, PRIMARY_SUPER_ADMIN_EMAIL));
        addReserved(emails, resolveListConfig(RESERVED_ADMIN_ENV, DEFAULT_ADMIN_EMAIL));
        addReserved(emails, resolveListConfig(RESERVED_USER_ENV, DEFAULT_USER_EMAIL));
        return Set.copyOf(emails);
    }

    private static Set<String> resolveTemporarySetupEmails() {
        Set<String> emails = new LinkedHashSet<>();
        addReserved(emails, DEFAULT_ADMIN_EMAIL);
        addReserved(emails, DEFAULT_USER_EMAIL);
        return Set.copyOf(emails);
    }

    private static String resolveListConfig(String key, String fallback) {
        String configured = System.getenv(key);
        if (configured == null) {
            configured = readEnvFileValue(key);
        }
        return configured == null ? fallback : configured;
    }

    private static void addReserved(Set<String> target, String rawEmails) {
        if (rawEmails == null || rawEmails.isBlank()) {
            return;
        }
        Arrays.stream(rawEmails.split(","))
                .map(AccessControl::normalizeEmail)
                .filter(value -> !value.isBlank())
                .forEach(target::add);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String readEnvFileValue(String key) {
        Path envFile = Path.of(".env");
        if (!Files.exists(envFile)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(envFile);
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator <= 0 || !key.equals(line.substring(0, separator).trim())) {
                    continue;
                }
                String value = line.substring(separator + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        } catch (IOException ignored) {
            // Fall back to process environment or the built-in development account.
        }
        return null;
    }
}

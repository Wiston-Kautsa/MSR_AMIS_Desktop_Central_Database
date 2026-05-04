package com.mycompany.msr.amis;

public final class Session {

    private static User currentUser;
    private static boolean setupMode;
    private static String authToken;
    private static boolean passwordChangeRequired;

    private Session() {
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void setSetupMode(boolean enabled) {
        setupMode = enabled;
    }

    public static boolean isSetupMode() {
        return setupMode;
    }

    public static void setAuthToken(String token) {
        authToken = token;
    }

    public static String getAuthToken() {
        return authToken;
    }

    public static void setPasswordChangeRequired(boolean required) {
        passwordChangeRequired = required;
    }

    public static boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public static void clear() {
        currentUser = null;
        setupMode = false;
        authToken = null;
        passwordChangeRequired = false;
    }

    public static boolean hasRole(String... allowedRoles) {
        if (currentUser == null || allowedRoles == null) {
            return false;
        }

        String currentRole = currentUser.getRole();
        for (String allowedRole : allowedRoles) {
            if (allowedRole != null && allowedRole.equalsIgnoreCase(currentRole)) {
                return true;
            }
        }
        return false;
    }
}

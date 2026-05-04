package com.mycompany.msr.amis;

public enum DataAccessMode {
    LOCAL_DATABASE,
    REMOTE_API,
    AUTO;

    public static DataAccessMode from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return REMOTE_API;
        }

        String normalized = rawValue.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        for (DataAccessMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported data access mode: " + rawValue);
    }
}

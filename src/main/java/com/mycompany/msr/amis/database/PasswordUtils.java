package com.mycompany.msr.amis;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility for password hashing using SHA-256 with a random salt.
 *
 * Stored format: BASE64(salt) + ":" + BASE64(SHA-256(salt + password))
 * This avoids storing plain-text passwords in the database.
 *
 * NOTE: For production use, prefer BCrypt or Argon2. This implementation
 * is a significant improvement over plain text and requires no extra
 * dependencies beyond the standard Java library.
 */
public final class PasswordUtils {

    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtils() {}

    /**
     * Hash a plain-text password into a storable salted hash string.
     */
    public static String hash(String plainPassword) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = sha256(salt, plainPassword);
        return Base64.getEncoder().encodeToString(salt)
                + ":"
                + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Verify a plain-text password against a stored hash string.
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (storedHash == null || !storedHash.contains(":")) {
            // Legacy plain-text fallback (for existing accounts before hashing was added)
            return plainPassword != null && plainPassword.equals(storedHash);
        }
        String[] parts = storedHash.split(":", 2);
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
        byte[] actualHash = sha256(salt, plainPassword);
        // Constant-time comparison to prevent timing attacks
        if (expectedHash.length != actualHash.length) return false;
        int diff = 0;
        for (int i = 0; i < expectedHash.length; i++) {
            diff |= (expectedHash[i] ^ actualHash[i]);
        }
        return diff == 0;
    }

    private static byte[] sha256(byte[] salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            return md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static String generateNumericCode(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Code length must be greater than zero.");
        }

        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}

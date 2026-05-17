package com.mycompany.msr.amis;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Utility for BCrypt password hashing.
 * Legacy salted SHA-256 hashes are still accepted during verification so
 * existing local users can sign in until their password is changed.
 */
public final class PasswordUtils {

    private static final int SALT_BYTES = 16;
    private static final int BCRYPT_WORK_FACTOR = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtils() {}

    /**
     * Hash a plain-text password into a storable BCrypt hash string.
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("Password cannot be null.");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_WORK_FACTOR));
    }

    /**
     * Verify a plain-text password against a stored hash string.
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
            return BCrypt.checkpw(plainPassword, storedHash);
        }
        if (!storedHash.contains(":")) {
            return false;
        }
        try {
            String[] parts = storedHash.split(":", 2);
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
            byte[] actualHash = sha256(salt, plainPassword);
            if (expectedHash.length != actualHash.length) return false;
            int diff = 0;
            for (int i = 0; i < expectedHash.length; i++) {
                diff |= (expectedHash[i] ^ actualHash[i]);
            }
            return diff == 0;
        } catch (IllegalArgumentException exception) {
            return false;
        }
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

    public static String generateSecureToken(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Token length must be greater than zero.");
        }
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return builder.toString();
    }
}

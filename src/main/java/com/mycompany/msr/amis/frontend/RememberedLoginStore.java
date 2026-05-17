package com.mycompany.msr.amis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class RememberedLoginStore {

    private static final String PREF_LOGINS = "rememberedLogins";
    private static final String PREF_LAST_EMAIL = "lastEmail";
    private static final String ENTRY_SEPARATOR = "\n";
    private static final String FIELD_SEPARATOR = "\t";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final Preferences preferences = Preferences.userNodeForPackage(RememberedLoginStore.class);
    private final SecureRandom secureRandom = new SecureRandom();

    List<String> getEmails() {
        return new ArrayList<>(loadEntries().keySet());
    }

    String getLastEmail() {
        return preferences.get(PREF_LAST_EMAIL, "");
    }

    String getPassword(String email) {
        RememberedLogin entry = loadEntries().get(normalize(email));
        return entry == null ? "" : entry.password;
    }

    boolean hasSavedPassword(String email, String password) {
        String savedPassword = getPassword(email);
        return !savedPassword.isBlank() && savedPassword.equals(password);
    }

    void save(String email, String password) {
        String normalizedEmail = normalize(email);
        if (normalizedEmail.isBlank() || password == null || password.isBlank()) {
            return;
        }

        Map<String, RememberedLogin> entries = loadEntries();
        entries.put(normalizedEmail, new RememberedLogin(normalizedEmail, password));
        persist(entries);
        preferences.put(PREF_LAST_EMAIL, normalizedEmail);
    }

    void remove(String email) {
        String normalizedEmail = normalize(email);
        if (normalizedEmail.isBlank()) {
            return;
        }

        Map<String, RememberedLogin> entries = loadEntries();
        entries.remove(normalizedEmail);
        persist(entries);
        if (normalizedEmail.equalsIgnoreCase(getLastEmail())) {
            preferences.put(PREF_LAST_EMAIL, entries.isEmpty() ? "" : entries.keySet().iterator().next());
        }
    }

    private Map<String, RememberedLogin> loadEntries() {
        Map<String, RememberedLogin> entries = new LinkedHashMap<>();
        String raw = preferences.get(PREF_LOGINS, "");
        if (raw.isBlank()) {
            return entries;
        }

        for (String row : raw.split(ENTRY_SEPARATOR)) {
            String[] parts = row.split(FIELD_SEPARATOR, 2);
            if (parts.length != 2) {
                continue;
            }
            String email = decode(parts[0]);
            String password = decrypt(parts[1]);
            if (!email.isBlank() && password != null) {
                entries.put(email, new RememberedLogin(email, password));
            }
        }
        return entries;
    }

    private void persist(Map<String, RememberedLogin> entries) {
        StringBuilder builder = new StringBuilder();
        for (RememberedLogin entry : entries.values()) {
            if (builder.length() > 0) {
                builder.append(ENTRY_SEPARATOR);
            }
            builder.append(encode(entry.email));
            builder.append(FIELD_SEPARATOR);
            builder.append(encrypt(entry.password));
        }
        preferences.put(PREF_LOGINS, builder.toString());
    }

    private String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to remember login password.", exception);
        }
    }

    private String decrypt(String encodedPayload) {
        try {
            byte[] payload = Base64.getDecoder().decode(encodedPayload);
            if (payload.length <= IV_BYTES) {
                return null;
            }

            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return null;
        }
    }

    private SecretKeySpec key() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String material = System.getProperty("user.name", "")
                + "|" + System.getProperty("user.home", "")
                + "|" + System.getProperty("os.name", "")
                + "|MSR-AMIS remembered login";
        return new SecretKeySpec(digest.digest(material.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        try {
            return normalize(new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return "";
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static final class RememberedLogin {
        private final String email;
        private final String password;

        private RememberedLogin(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }
}

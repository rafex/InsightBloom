package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.ether.crypto.password.PasswordHasherPBKDF2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

public class PasswordService {

    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 310_000;
    private static final int DERIVED_KEY_BYTES = 32;
    private static final String PREFIX = "$pbkdf2$";

    private final PasswordHasherPBKDF2 hasher = new PasswordHasherPBKDF2(DERIVED_KEY_BYTES);
    private final SecureRandom rng = new SecureRandom();

    public String hash(final String password) {
        final byte[] salt = new byte[SALT_BYTES];
        rng.nextBytes(salt);
        final var ph = hasher.hash(password.toCharArray(), salt, ITERATIONS);
        return PREFIX + ITERATIONS + "$" + hex(salt) + "$" + hex(ph.hash());
    }

    public boolean verify(final String password, final String storedHash) {
        if (storedHash == null) return false;
        if (storedHash.startsWith(PREFIX)) {
            final String[] parts = storedHash.substring(PREFIX.length()).split("\\$");
            if (parts.length != 3) return false;
            final int iterations = Integer.parseInt(parts[0]);
            final byte[] salt = HexFormat.of().parseHex(parts[1]);
            final byte[] expected = HexFormat.of().parseHex(parts[2]);
            return hasher.verify(password.toCharArray(), salt, iterations, expected);
        }
        // Legacy: bare SHA-256 hex (64 chars, no prefix) — still accepted for migration
        return sha256(password).equals(storedHash);
    }

    public boolean isLegacyHash(final String storedHash) {
        return storedHash != null && !storedHash.startsWith(PREFIX);
    }

    public static String sha256(final String input) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String hex(final byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}

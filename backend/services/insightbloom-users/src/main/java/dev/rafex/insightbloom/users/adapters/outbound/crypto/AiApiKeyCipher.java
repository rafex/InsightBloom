package dev.rafex.insightbloom.users.adapters.outbound.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Encrypts the provider key at rest using the already required service-to-service secret. */
public final class AiApiKeyCipher {
    private static final String PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public AiApiKeyCipher(final String internalApiKey) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            throw new IllegalArgumentException("INTERNAL_API_KEY is required to protect the AI provider key");
        }
        try {
            this.key = MessageDigest.getInstance("SHA-256")
                    .digest(internalApiKey.getBytes(StandardCharsets.UTF_8));
        } catch (final Exception e) {
            throw new IllegalStateException("Unable to initialize AI key protection", e);
        }
    }

    public String encrypt(final String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            final byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            final byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            final byte[] packed = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, packed, 0, nonce.length);
            System.arraycopy(ciphertext, 0, packed, nonce.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (final Exception e) {
            throw new IllegalStateException("Unable to encrypt AI provider key", e);
        }
    }

    public String decrypt(final String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            if (!encoded.startsWith(PREFIX)) throw new IllegalArgumentException("Unsupported AI key format");
            final byte[] packed = Base64.getDecoder().decode(encoded.substring(PREFIX.length()));
            if (packed.length <= NONCE_BYTES) throw new IllegalArgumentException("Invalid encrypted AI key");
            final byte[] nonce = java.util.Arrays.copyOfRange(packed, 0, NONCE_BYTES);
            final byte[] ciphertext = java.util.Arrays.copyOfRange(packed, NONCE_BYTES, packed.length);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (final Exception e) {
            throw new IllegalStateException("Unable to decrypt AI provider key", e);
        }
    }
}

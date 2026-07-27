package dev.rafex.insightbloom.users.application.usecases;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Codifica/decodifica el codigo de intercambio SSO usado para pasar sesion a subdominios de
 * herramientas (hoy: el chat) sin exponer el JWT de larga duracion en la URL. Mismo esquema que
 * {@link WorkspaceDownloadToken}: firmado con HMAC-SHA256, TTL corto (60s, alcanza para la
 * navegacion inicial), y de un solo uso -- el nonce se marca consumido en el primer decode
 * exitoso, asi que aunque el codigo quede en logs de acceso o historial del navegador, ya no
 * sirve para nada tras el primer canje.
 */
public class SsoExchangeToken {
    static final long EXPIRY_SECONDS = 60;
    private static final long NONCE_RETENTION_SECONDS = EXPIRY_SECONDS * 2;

    private final Mac hmac;
    private final ConcurrentHashMap<String, Instant> consumedNonces = new ConcurrentHashMap<>();

    public SsoExchangeToken(final String secret) {
        try {
            this.hmac = Mac.getInstance("HmacSHA256");
            this.hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        } catch (final Exception e) {
            throw new IllegalStateException("No se pudo inicializar la firma de codigos de intercambio SSO", e);
        }
    }

    String encode(final String subjectUuid, final String kind, final String role) {
        final String payload = String.join(":",
                subjectUuid,
                kind,
                role == null ? "" : role,
                String.valueOf(Instant.now().getEpochSecond()),
                UUID.randomUUID().toString()
        );
        final String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        final String signature = sign(encodedPayload);
        return encodedPayload + "." + signature;
    }

    /** {@code Optional.empty()} si el codigo esta mal formado, corrupto, vencido, tiene firma
     *  invalida, o ya fue usado. Si es valido, el nonce queda consumido (no se puede reusar). */
    Optional<Parsed> decode(final String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        final int dot = code.lastIndexOf('.');
        if (dot < 0) return Optional.empty();
        final String encodedPayload = code.substring(0, dot);
        final String signature = code.substring(dot + 1);
        if (!constantTimeEquals(signature, sign(encodedPayload))) return Optional.empty();

        final String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
        final String[] parts = payload.split(":", 5);
        if (parts.length != 5) return Optional.empty();

        final long issuedAt;
        try {
            issuedAt = Long.parseLong(parts[3]);
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
        final long now = Instant.now().getEpochSecond();
        if (now - issuedAt > EXPIRY_SECONDS) return Optional.empty();

        final String nonce = parts[4];
        cleanupExpiredNonces(now);
        if (consumedNonces.putIfAbsent(nonce, Instant.now()) != null) {
            return Optional.empty(); // ya se uso
        }
        return Optional.of(new Parsed(parts[0], parts[1], parts[2].isEmpty() ? null : parts[2]));
    }

    // javax.crypto.Mac no es thread-safe para doFinal() concurrente -- este use case es
    // compartido (singleton) entre requests, asi que se sincroniza el unico punto que lo usa.
    private synchronized String sign(final String encodedPayload) {
        final byte[] raw = hmac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static boolean constantTimeEquals(final String a, final String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private void cleanupExpiredNonces(final long nowEpochSeconds) {
        consumedNonces.entrySet().removeIf(e ->
                nowEpochSeconds - e.getValue().getEpochSecond() > NONCE_RETENTION_SECONDS);
    }

    record Parsed(String subjectUuid, String kind, String role) {
    }
}

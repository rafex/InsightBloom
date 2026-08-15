package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Capability corta para publicar el sandbox desde su propio terminal.
 *
 * La capability no sustituye a la sesión de InsightBloom ni se devuelve al navegador. Se
 * materializa en el home del asiento asignado y únicamente autoriza el sandbox/conferencia que
 * contiene el token firmado.
 */
public final class SandboxPublicationCapability {
    public static final long MAX_TTL_SECONDS = 3600;
    private static final String VERSION = "v1";

    private final Mac hmac;

    public SandboxPublicationCapability(final String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("SANDBOX_PUBLICATION_SECRET no puede estar vacío");
        }
        try {
            hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        } catch (final Exception e) {
            throw new IllegalStateException("No se pudo inicializar la firma de capability del sandbox", e);
        }
    }

    public String encode(final Sandbox sandbox) {
        final long now = Instant.now().getEpochSecond();
        final long sandboxExpiry = sandbox.getExpiresAt() == null
                ? now + MAX_TTL_SECONDS
                : sandbox.getExpiresAt().getEpochSecond();
        final long expiresAt = Math.min(now + MAX_TTL_SECONDS, sandboxExpiry);
        final String payload = String.join(":", VERSION, sandbox.getConferenceUuid(), sandbox.getUuid(),
                sandbox.getUserUuid(), String.valueOf(sandbox.getSeatIndex()), String.valueOf(now),
                String.valueOf(expiresAt));
        final String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    public Optional<Parsed> decode(final String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        final int separator = token.lastIndexOf('.');
        if (separator <= 0 || separator == token.length() - 1) return Optional.empty();
        final String encoded = token.substring(0, separator);
        final String signature = token.substring(separator + 1);
        if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8),
                sign(encoded).getBytes(StandardCharsets.UTF_8))) return Optional.empty();
        final String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
        final String[] parts = payload.split(":", -1);
        if (parts.length != 7 || !VERSION.equals(parts[0])
                || parts[1].isBlank() || parts[2].isBlank() || parts[3].isBlank()) return Optional.empty();
        try {
            final int seatIndex = Integer.parseInt(parts[4]);
            final long issuedAt = Long.parseLong(parts[5]);
            final long expiresAt = Long.parseLong(parts[6]);
            final long now = Instant.now().getEpochSecond();
            if (seatIndex < 0 || issuedAt > now || expiresAt <= now || expiresAt <= issuedAt
                    || expiresAt - issuedAt > MAX_TTL_SECONDS) return Optional.empty();
            return Optional.of(new Parsed(parts[1], parts[2], parts[3], seatIndex, expiresAt));
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
    }

    private synchronized String sign(final String encodedPayload) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
    }

    public record Parsed(String conferenceUuid, String sandboxUuid, String userUuid,
                         int seatIndex, long expiresAt) { }
}

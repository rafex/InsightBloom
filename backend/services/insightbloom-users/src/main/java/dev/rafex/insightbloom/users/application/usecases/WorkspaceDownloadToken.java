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
 * Codifica/decodifica el token de descarga del workspace -- formato compartido entre
 * {@link GenerateWorkspaceDownloadUrlUseCase} (lo emite) y {@link DownloadWorkspaceZipUseCase}
 * (lo valida), para que ambos lados nunca se desincronicen del formato.
 *
 * AUD-07: la version anterior era un base64 plano sin firmar (sandboxUuid:userUuid:issuedAt:nonce)
 * con 1h de expiracion y reutilizable -- ademas de viajar en la URL (inevitable, la descarga es
 * una navegacion directa del navegador, no un fetch con headers), cualquiera podia FORJAR un token
 * valido para el sandbox/usuario de otra persona con solo conocer ambos UUIDs (no habia firma que
 * lo impidiera). Ahora: (1) firmado con HMAC-SHA256 sobre un secreto de servidor -- no falsificable
 * sin la clave; (2) TTL corto (2 min, alcanza para que el navegador siga la redireccion); (3) de un
 * solo uso -- el nonce se marca consumido en el primer decode exitoso.
 */
public class WorkspaceDownloadToken {
    static final long EXPIRY_SECONDS = 120;
    private static final long NONCE_RETENTION_SECONDS = EXPIRY_SECONDS * 2;

    private final Mac hmac;
    private final ConcurrentHashMap<String, Instant> consumedNonces = new ConcurrentHashMap<>();

    public WorkspaceDownloadToken(final String secret) {
        try {
            this.hmac = Mac.getInstance("HmacSHA256");
            this.hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        } catch (final Exception e) {
            throw new IllegalStateException("No se pudo inicializar la firma de tokens de descarga", e);
        }
    }

    String encode(final String sandboxUuid, final String userUuid) {
        final String payload = String.join(":",
                sandboxUuid,
                userUuid,
                String.valueOf(Instant.now().getEpochSecond()),
                UUID.randomUUID().toString()
        );
        final String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        final String signature = sign(encodedPayload);
        return encodedPayload + "." + signature;
    }

    /** {@code Optional.empty()} si el token esta mal formado, corrupto, vencido, tiene firma
     *  invalida, o ya fue usado. Si es valido, el nonce queda consumido (no se puede reusar). */
    Optional<Parsed> decode(final String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        final int dot = token.lastIndexOf('.');
        if (dot < 0) return Optional.empty();
        final String encodedPayload = token.substring(0, dot);
        final String signature = token.substring(dot + 1);
        if (!constantTimeEquals(signature, sign(encodedPayload))) return Optional.empty();

        final String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
        final String[] parts = payload.split(":", 4);
        if (parts.length != 4) return Optional.empty();

        final long issuedAt;
        try {
            issuedAt = Long.parseLong(parts[2]);
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
        final long now = Instant.now().getEpochSecond();
        if (now - issuedAt > EXPIRY_SECONDS) return Optional.empty();

        final String nonce = parts[3];
        cleanupExpiredNonces(now);
        if (consumedNonces.putIfAbsent(nonce, Instant.now()) != null) {
            return Optional.empty(); // ya se uso
        }
        return Optional.of(new Parsed(parts[0], parts[1]));
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

    record Parsed(String sandboxUuid, String userUuid) {
    }
}

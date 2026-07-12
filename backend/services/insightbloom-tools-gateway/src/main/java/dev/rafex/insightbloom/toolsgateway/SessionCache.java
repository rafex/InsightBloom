package dev.rafex.insightbloom.toolsgateway;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache en memoria de sesiones de gateway ya validadas: evita revalidar el token de
 * InsightBloom (llamada HTTP a insightbloom-users) en cada sub-recurso (JS/CSS/XHR) que pide
 * el navegador dentro del iframe de la herramienta — solo la navegacion inicial (que trae
 * {@code ib_token} en la query string) paga ese costo; el resto usa la cookie de sesion del
 * gateway. No es un cache distribuido: si el gateway escala a mas de una replica, cada pod
 * valida su propia sesion en el primer request que le toque, lo cual es aceptable dado el TTL
 * corto y que la validacion contra insightbloom-users es barata.
 */
final class SessionCache {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    String mint(final java.time.Duration ttl) {
        final byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        final String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(id, Instant.now().plus(ttl));
        return id;
    }

    boolean isValid(final String sessionId) {
        if (sessionId == null) return false;
        final Instant expiry = sessions.get(sessionId);
        if (expiry == null) return false;
        if (Instant.now().isAfter(expiry)) {
            sessions.remove(sessionId);
            return false;
        }
        return true;
    }
}

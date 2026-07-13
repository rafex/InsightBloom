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
 *
 * Fase 3 del IDE: {@code dynamicTarget} guarda, para las herramientas con destino por-sesion
 * (el sandbox de un usuario especifico, a diferencia de drawio/Etherpad que son instancias
 * compartidas con un target fijo), el Service DNS resuelto una sola vez al mintear la sesion.
 */
final class SessionCache {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private record Session(Instant expiry, String dynamicTarget) {}

    String mint(final java.time.Duration ttl) {
        return mint(ttl, null);
    }

    String mint(final java.time.Duration ttl, final String dynamicTarget) {
        final byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        final String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(id, new Session(Instant.now().plus(ttl), dynamicTarget));
        return id;
    }

    boolean isValid(final String sessionId) {
        return liveSession(sessionId) != null;
    }

    /** null si la sesion no es valida, o si es valida pero no tiene target dinamico (herramienta compartida). */
    String dynamicTarget(final String sessionId) {
        final Session session = liveSession(sessionId);
        return session == null ? null : session.dynamicTarget();
    }

    private Session liveSession(final String sessionId) {
        if (sessionId == null) return null;
        final Session session = sessions.get(sessionId);
        if (session == null) return null;
        if (Instant.now().isAfter(session.expiry())) {
            sessions.remove(sessionId);
            return null;
        }
        return session;
    }
}

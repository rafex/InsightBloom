package dev.rafex.insightbloom.users.adapters.inbound.http.middleware;

import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.services.DeviceFingerprintAuditor;
import dev.rafex.insightbloom.users.domain.services.TokenService;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Middleware global (ver JettyMiddleware, registrado en UsersApplication.java al construir el
 * servidor): en cada request autenticado, compara el header X-Device-Fingerprint contra el
 * fingerprint guardado en el token al momento del login, y registra la discrepancia via
 * DeviceFingerprintAuditor -- SIN bloquear nunca el request (ver javadoc de esa clase para el
 * porque). Corre para TODA ruta sin tocar los ~55 call-sites de
 * validateTokenUseCase.execute(token) repartidos en los handlers.
 *
 * Nota de rendimiento: hace una consulta extra a `tokens` por request (tokenService.validate),
 * duplicando el trabajo que el handler de destino va a repetir con ValidateTokenUseCase --
 * aceptable en esta escala, y evita tocar cada handler individualmente.
 */
public final class DeviceFingerprintAuditHandler extends Handler.Wrapper {
    private static final Logger LOGGER = Logger.getLogger(DeviceFingerprintAuditHandler.class.getName());

    private final TokenService tokenService;
    private final DeviceFingerprintAuditor auditor;

    public DeviceFingerprintAuditHandler(final Handler next, final TokenService tokenService,
                                          final DeviceFingerprintAuditor auditor) {
        super(next);
        this.tokenService = tokenService;
        this.auditor = auditor;
    }

    @Override
    public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
        try {
            final String auth = request.getHeaders().get("Authorization");
            final String requestFingerprint = request.getHeaders().get("X-Device-Fingerprint");
            if (auth != null && auth.startsWith("Bearer ") && requestFingerprint != null && !requestFingerprint.isBlank()) {
                final String tokenValue = auth.substring(7);
                tokenService.validate(tokenValue).ifPresent(token -> runAudit(token, requestFingerprint));
            }
        } catch (final Exception e) {
            // Nunca debe romper el request real por un fallo en la auditoria.
            LOGGER.log(Level.WARNING, "DeviceFingerprintAuditHandler: fallo auditando fingerprint", e);
        }
        return super.handle(request, response, callback);
    }

    private void runAudit(final Token token, final String requestFingerprint) {
        final String subjectKind = token.getTokenKind() == TokenKind.GUEST ? "guest" : "user";
        auditor.audit(token.getUuid(), token.subjectUuid(), subjectKind,
                token.getDeviceFingerprint(), requestFingerprint);
    }
}

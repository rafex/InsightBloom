package dev.rafex.insightbloom.users.application.usecases;

import java.util.Optional;

/**
 * Mintea un codigo de intercambio SSO de un solo uso a partir del JWT de sesion vigente -- el
 * front lo pide justo antes de abrir el chat en su subdominio y pone el codigo (no el JWT) en la
 * URL, evitando que el token de larga duracion quede en historial del navegador o en logs de
 * acceso de proxies. Ver {@link SsoExchangeToken} para el formato y las garantias de un solo
 * uso/TTL corto, y {@link ConsumeSsoExchangeUseCase} para el otro extremo del canje.
 */
public class CreateSsoExchangeUseCase {
    private final ValidateTokenUseCase validateTokenUseCase;
    private final SsoExchangeToken tokenCodec;

    public CreateSsoExchangeUseCase(final ValidateTokenUseCase validateTokenUseCase, final SsoExchangeToken tokenCodec) {
        this.validateTokenUseCase = validateTokenUseCase;
        this.tokenCodec = tokenCodec;
    }

    public record Result(String code, long expiresInSeconds) {}

    /** {@code Optional.empty()} si el token es invalido/expirado o pertenece a un invitado --
     *  el chat SSO requiere cuenta registrada, igual que ya exige {@code POST /api/sso} hoy. */
    public Optional<Result> execute(final String rawToken) {
        final var validation = validateTokenUseCase.execute(rawToken);
        if (!validation.valid() || "guest".equals(validation.kind())) {
            return Optional.empty();
        }
        final String code = tokenCodec.encode(validation.subjectUuid(), validation.kind(), validation.role());
        return Optional.of(new Result(code, SsoExchangeToken.EXPIRY_SECONDS));
    }
}

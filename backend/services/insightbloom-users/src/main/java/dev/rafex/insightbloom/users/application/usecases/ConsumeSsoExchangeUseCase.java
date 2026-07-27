package dev.rafex.insightbloom.users.application.usecases;

/**
 * Canjea un codigo de intercambio SSO (ver {@link CreateSsoExchangeUseCase}) por los mismos datos
 * que {@link ValidateTokenUseCase#execute} habria devuelto para el JWT original -- lo llama el
 * backend del subdominio de la herramienta (hoy: chat) directamente, server-to-server, sin volver
 * a tocar el JWT del usuario.
 */
public class ConsumeSsoExchangeUseCase {
    private final SsoExchangeToken tokenCodec;

    public ConsumeSsoExchangeUseCase(final SsoExchangeToken tokenCodec) {
        this.tokenCodec = tokenCodec;
    }

    public ValidateTokenUseCase.ValidationResult execute(final String code) {
        return tokenCodec.decode(code)
                .map(p -> new ValidateTokenUseCase.ValidationResult(true, p.subjectUuid(), p.kind(), p.role(), null))
                .orElse(new ValidateTokenUseCase.ValidationResult(false, null, null, null, null));
    }
}

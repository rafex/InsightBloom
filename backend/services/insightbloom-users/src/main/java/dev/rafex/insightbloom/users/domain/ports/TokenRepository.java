package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.Token;
import java.util.List;
import java.util.Optional;

public interface TokenRepository {
    void save(Token token);
    Optional<Token> findByValue(String tokenValue);
    void revokeAllForUser(String userUuid);

    void revokeByValue(String tokenValue);

    /** Tokens activos (no vencidos, no revocados) de un usuario, ordenados ASC por creacion --
     *  ver PlatformDeviceGuard, limite de sesiones simultaneas (revoca el mas viejo). */
    List<Token> findActiveByUser(String userUuid);

    /** Tokens activos que comparten un fingerprint, sin importar el usuario/invitado -- ver
     *  PlatformDeviceGuard, deteccion de multicuenta a nivel plataforma. */
    List<Token> findActiveByFingerprint(String deviceFingerprint);

    void revokeAllForFingerprint(String deviceFingerprint);

    /** Revoca un token puntual por su uuid de fila (no el valor crudo, que no se puede
     *  recuperar del hash almacenado) -- usado para revocar la sesion mas vieja de un usuario. */
    void revokeByUuid(String tokenUuid);
}

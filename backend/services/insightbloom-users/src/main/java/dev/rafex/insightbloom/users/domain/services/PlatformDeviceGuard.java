package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.PlatformDeviceBlock;
import dev.rafex.insightbloom.users.domain.model.PlatformDeviceBlockReason;
import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.ports.PlatformDeviceBlockRepository;
import dev.rafex.insightbloom.users.domain.ports.TokenRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controla abuso a nivel PLATAFORMA (no atado a un evento puntual, a diferencia de
 * {@link DeviceAccessGuard}, que solo mira dentro de una conferencia): cuantas sesiones activas
 * puede tener un mismo usuario a la vez, cuantas cuentas distintas puede compartir un mismo
 * dispositivo, y cuantas cuentas nuevas puede crear un mismo dispositivo en un dia.
 *
 * Reusa la tabla {@code tokens} (ya tiene {@code device_fingerprint} desde el login/guest-login)
 * en vez de una tabla de sesiones nueva -- como los tokens expiran solos (24h usuario / 8h
 * invitado), "cuantas sesiones activas comparten este fingerprint" se auto-limpia sin trabajo
 * extra.
 *
 * Comportamiento acordado: al detectar abuso, se bloquea el LOGIN completo desde ese dispositivo
 * (no solo Jitsi/IDE) y queda en cola de revision para un system_admin
 * (ver ListPlatformDeviceBlocksUseCase/UnblockPlatformDeviceUseCase).
 */
public class PlatformDeviceGuard {

    private static final int DEFAULT_MAX_ACCOUNTS_PER_DEVICE = 5;
    private static final int DEFAULT_MAX_SESSIONS_PER_USER = 3;
    private static final int DEFAULT_MAX_REGISTRATIONS_PER_DEVICE_PER_DAY = 3;

    public sealed interface Result {
        record Allowed() implements Result {}
        record Blocked(PlatformDeviceBlockReason reason) implements Result {}
    }

    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PlatformDeviceBlockRepository blockRepository;

    public PlatformDeviceGuard(final TokenRepository tokenRepository, final UserRepository userRepository,
                                final PlatformDeviceBlockRepository blockRepository) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.blockRepository = blockRepository;
    }

    /**
     * Llamado justo despues de emitir el token de login (real o invitado), antes de devolverlo
     * al cliente. Si el device ya estaba bloqueado, ni se cuenta -- se rechaza directo.
     */
    public Result checkAndRegisterLogin(final String deviceFingerprint, final String subjectUuid,
                                         final TokenKind kind, final PlatformSettings settings) {
        if (deviceFingerprint == null || deviceFingerprint.isBlank()) {
            return new Result.Allowed();
        }
        if (blockRepository.findActive(deviceFingerprint).isPresent()) {
            return new Result.Blocked(PlatformDeviceBlockReason.MULTI_ACCOUNT);
        }

        if (kind == TokenKind.USER) {
            enforceSessionLimit(subjectUuid, settings);
        }

        final int maxAccountsPerDevice = settings.getMaxAccountsPerDevice() != null
                ? settings.getMaxAccountsPerDevice() : DEFAULT_MAX_ACCOUNTS_PER_DEVICE;
        final List<Token> deviceTokens = tokenRepository.findActiveByFingerprint(deviceFingerprint);
        final Set<String> distinctSubjects = deviceTokens.stream()
                .map(Token::subjectUuid)
                .collect(Collectors.toSet());
        if (distinctSubjects.size() > maxAccountsPerDevice) {
            blockRepository.save(new PlatformDeviceBlock(
                    deviceFingerprint, PlatformDeviceBlockReason.MULTI_ACCOUNT, distinctSubjects.size()));
            tokenRepository.revokeAllForFingerprint(deviceFingerprint);
            return new Result.Blocked(PlatformDeviceBlockReason.MULTI_ACCOUNT);
        }

        return new Result.Allowed();
    }

    /** Llamado antes de crear la cuenta en RegisterUseCase. */
    public Result checkRegistration(final String deviceFingerprint, final PlatformSettings settings) {
        if (deviceFingerprint == null || deviceFingerprint.isBlank()) {
            return new Result.Allowed();
        }
        if (blockRepository.findActive(deviceFingerprint).isPresent()) {
            return new Result.Blocked(PlatformDeviceBlockReason.REGISTRATION_SPAM);
        }

        final int maxRegistrationsPerDay = settings.getMaxRegistrationsPerDevicePerDay() != null
                ? settings.getMaxRegistrationsPerDevicePerDay() : DEFAULT_MAX_REGISTRATIONS_PER_DEVICE_PER_DAY;
        final long recentRegistrations = userRepository.countByRegistrationFingerprintSince(
                deviceFingerprint, Instant.now().minus(24, ChronoUnit.HOURS));
        if (recentRegistrations >= maxRegistrationsPerDay) {
            blockRepository.save(new PlatformDeviceBlock(
                    deviceFingerprint, PlatformDeviceBlockReason.REGISTRATION_SPAM, (int) recentRegistrations + 1));
            return new Result.Blocked(PlatformDeviceBlockReason.REGISTRATION_SPAM);
        }

        return new Result.Allowed();
    }

    private void enforceSessionLimit(final String userUuid, final PlatformSettings settings) {
        final int maxSessionsPerUser = settings.getMaxSessionsPerUser() != null
                ? settings.getMaxSessionsPerUser() : DEFAULT_MAX_SESSIONS_PER_USER;
        final List<Token> active = tokenRepository.findActiveByUser(userUuid);
        if (active.size() >= maxSessionsPerUser) {
            // Ordenado ASC por created_at (ver SqliteTokenRepository) -- el primero de la lista
            // es la sesion mas vieja, se revoca para dejar espacio a la nueva.
            tokenRepository.revokeByUuid(active.get(0).getUuid());
        }
    }
}

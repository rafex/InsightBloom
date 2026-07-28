package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.OtpCode;

import java.time.Instant;
import java.util.Optional;

public interface OtpCodeRepository {
    void save(OtpCode otpCode);
    Optional<OtpCode> findLatestActive(String identifier);
    void markConsumed(String uuid);

    /** Limite de intentos de verificacion (login OTP) -- ver VerifyLoginOtpUseCase. */
    void incrementFailedAttempts(String uuid);

    /** Cuantos codigos se generaron para este identificador desde {@code since} -- usado para
     *  el rate limit de envio (ver RequestLoginOtpUseCase), sin necesitar una tabla aparte. */
    int countSince(String identifier, Instant since);
}

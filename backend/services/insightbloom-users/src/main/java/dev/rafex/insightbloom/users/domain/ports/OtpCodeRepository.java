package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.OtpCode;

import java.util.Optional;

public interface OtpCodeRepository {
    void save(OtpCode otpCode);
    Optional<OtpCode> findLatestActive(String identifier);
    void markConsumed(String uuid);
}

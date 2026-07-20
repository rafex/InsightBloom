package dev.rafex.insightbloom.users.domain.ports;

import java.util.List;

import dev.rafex.insightbloom.users.domain.model.DeviceFingerprintFlag;

public interface DeviceFingerprintFlagRepository {
    /** Upsert por token_uuid: crea la fila si es la primera discrepancia de esa sesion, o
     *  incrementa occurrence_count y actualiza last_seen_* si ya existia. */
    void recordMismatch(String tokenUuid, String subjectUuid, String subjectKind,
                         String loginFingerprint, String requestFingerprint);

    List<DeviceFingerprintFlag> findAll();

    void markReviewed(String uuid, String reviewedByUserUuid);
}

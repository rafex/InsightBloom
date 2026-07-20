package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Discrepancia de fingerprint detectada DENTRO de una misma sesion ya logueada -- el fingerprint
 * que llega en un request no coincide con el que se guardo al emitir el token en el login. Nunca
 * corta la sesion (ver DeviceFingerprintAuditor); es pura visibilidad para que un system_admin lo
 * revise en /dashboard/admin/device-access. Una fila por sesion (token), no por request.
 */
public class DeviceFingerprintFlag {
    private final String uuid;
    private final String tokenUuid;
    private final String subjectUuid;
    private final String subjectKind;
    private final String loginFingerprint;
    private String lastSeenFingerprint;
    private int occurrenceCount;
    private final Instant firstSeenAt;
    private Instant lastSeenAt;
    private Instant reviewedAt;
    private String reviewedBy;

    public DeviceFingerprintFlag(String tokenUuid, String subjectUuid, String subjectKind,
                                  String loginFingerprint, String lastSeenFingerprint) {
        this.uuid = UUID.randomUUID().toString();
        this.tokenUuid = tokenUuid;
        this.subjectUuid = subjectUuid;
        this.subjectKind = subjectKind;
        this.loginFingerprint = loginFingerprint;
        this.lastSeenFingerprint = lastSeenFingerprint;
        this.occurrenceCount = 1;
        this.firstSeenAt = Instant.now();
        this.lastSeenAt = this.firstSeenAt;
    }

    public DeviceFingerprintFlag(String uuid, String tokenUuid, String subjectUuid, String subjectKind,
                                  String loginFingerprint, String lastSeenFingerprint, int occurrenceCount,
                                  Instant firstSeenAt, Instant lastSeenAt, Instant reviewedAt, String reviewedBy) {
        this.uuid = uuid;
        this.tokenUuid = tokenUuid;
        this.subjectUuid = subjectUuid;
        this.subjectKind = subjectKind;
        this.loginFingerprint = loginFingerprint;
        this.lastSeenFingerprint = lastSeenFingerprint;
        this.occurrenceCount = occurrenceCount;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.reviewedAt = reviewedAt;
        this.reviewedBy = reviewedBy;
    }

    public String getUuid() { return uuid; }
    public String getTokenUuid() { return tokenUuid; }
    public String getSubjectUuid() { return subjectUuid; }
    public String getSubjectKind() { return subjectKind; }
    public String getLoginFingerprint() { return loginFingerprint; }
    public String getLastSeenFingerprint() { return lastSeenFingerprint; }
    public int getOccurrenceCount() { return occurrenceCount; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewedBy() { return reviewedBy; }
}

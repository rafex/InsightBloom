package dev.rafex.insightbloom.moderation.domain.model;
import java.time.Instant;
import java.util.UUID;
public class BlockedTerm {
    private final String uuid;
    private final String scope; // "word" | "detail" | "all"
    private final String termNormalized;
    private String status; // "active" | "inactive"
    private final String createdByUserUuid;
    private final Instant createdAt;
    private Instant updatedAt;

    public BlockedTerm(String scope, String termNormalized, String createdByUserUuid) {
        this.uuid = UUID.randomUUID().toString();
        this.scope = scope;
        this.termNormalized = termNormalized;
        this.status = "active";
        this.createdByUserUuid = createdByUserUuid;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
    public BlockedTerm(String uuid, String scope, String termNormalized, String status,
                       String createdByUserUuid, Instant createdAt, Instant updatedAt) {
        this.uuid = uuid; this.scope = scope; this.termNormalized = termNormalized;
        this.status = status; this.createdByUserUuid = createdByUserUuid;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
    }
    public String getUuid() { return uuid; }
    public String getScope() { return scope; }
    public String getTermNormalized() { return termNormalized; }
    public String getStatus() { return status; }
    public String getCreatedByUserUuid() { return createdByUserUuid; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isActive() { return "active".equals(status); }
}

package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.OtpRequestAudit;

import java.time.Instant;
import java.util.List;

public interface OtpRequestAuditPort {
    void save(OtpRequestAudit audit);
    Page find(Filter filter, int limit, int offset);
    int deleteOlderThan(Instant cutoff);

    record Filter(Instant from, Instant to, String accountUuid, String clientIp, String outcome) {}
    record Page(List<OtpRequestAudit> items, int total) {}
}

package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.JaasUsageRepository;

import java.time.YearMonth;
import java.time.ZoneOffset;

public class GetJaasUsageUseCase {
    public static final int DEFAULT_MONTHLY_LIMIT = 25;

    private final JaasUsageRepository repository;
    private final int monthlyLimit;
    private final int monthlyBandwidthLimitGb;

    public GetJaasUsageUseCase(final JaasUsageRepository repository, final int monthlyLimit) {
        this(repository, monthlyLimit, 200);
    }

    public GetJaasUsageUseCase(final JaasUsageRepository repository, final int monthlyLimit,
                               final int monthlyBandwidthLimitGb) {
        this.repository = repository;
        this.monthlyLimit = Math.max(1, monthlyLimit);
        this.monthlyBandwidthLimitGb = Math.max(1, monthlyBandwidthLimitGb);
    }

    public Usage execute() {
        final String month = YearMonth.now(ZoneOffset.UTC).toString();
        final int used = repository.countUniqueParticipants(month);
        return new Usage(month, used, monthlyLimit, Math.max(0, monthlyLimit - used),
                Math.min(100, Math.round((used * 1000.0) / monthlyLimit) / 10.0),
                monthlyBandwidthLimitGb, null);
    }

    public record Usage(String month, int uniqueParticipants, int monthlyLimit,
                        int remaining, double percentage, int bandwidthLimitGb,
                        Double bandwidthUsedGb) {}
}

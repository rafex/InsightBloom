package dev.rafex.insightbloom.contracts;

import java.time.Instant;

public record ApiMeta(String requestId, String timestamp, Integer page, Integer pageSize, Long total, Integer totalPages) {
    public static ApiMeta of(String requestId) {
        return new ApiMeta(requestId, Instant.now().toString(), null, null, null, null);
    }

    public static ApiMeta paged(String requestId, int page, int pageSize, long total) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new ApiMeta(requestId, Instant.now().toString(), page, pageSize, total, totalPages);
    }
}

package dev.rafex.insightbloom.contracts;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, ApiMeta meta) {
    public static <T> ApiResponse<T> of(T data, String requestId) {
        return new ApiResponse<>(data, ApiMeta.of(requestId));
    }
}

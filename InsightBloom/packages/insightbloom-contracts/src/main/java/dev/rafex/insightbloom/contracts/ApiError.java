package dev.rafex.insightbloom.contracts;

public record ApiError(ErrorBody error, ApiMeta meta) {
    public record ErrorBody(String code, String message, Object details) {}

    public static ApiError of(String code, String message, String requestId) {
        return new ApiError(new ErrorBody(code, message, null), ApiMeta.of(requestId));
    }
}

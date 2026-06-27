package dev.rafex.insightbloom.stats.domain.ports;
public interface UsersPort {
    record ValidationResult(boolean valid, String subjectUuid, String kind, String role) {}
    ValidationResult validate(String token);
}

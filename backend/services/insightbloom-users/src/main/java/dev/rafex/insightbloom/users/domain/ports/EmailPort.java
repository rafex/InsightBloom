package dev.rafex.insightbloom.users.domain.ports;

public interface EmailPort {
    boolean isEnabled();

    void send(String toEmail, String subject, String body);
}

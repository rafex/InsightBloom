package dev.rafex.insightbloom.users.domain.ports;

public interface SmsPort {
    boolean isEnabled();

    void send(String toPhone, String message);
}

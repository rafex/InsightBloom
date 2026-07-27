package dev.rafex.insightbloom.users.domain.ports;

public interface EmailPort {
    boolean isEnabled();

    void send(String toEmail, String subject, String body);

    /** Cuerpo HTML (2026-07-27, email de boleto con diseño visual). */
    void sendHtml(String toEmail, String subject, String htmlBody);
}

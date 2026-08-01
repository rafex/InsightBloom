package dev.rafex.insightbloom.users.domain.ports;

import java.util.List;

public interface EmailPort {
    boolean isEnabled();

    void send(String toEmail, String subject, String body);

    /** Cuerpo HTML (2026-07-27, email de boleto con diseño visual). */
    void sendHtml(String toEmail, String subject, String htmlBody);

    /**
     * Cuerpo HTML con recursos MIME. La implementación por defecto conserva la
     * compatibilidad con adaptadores que todavía no soportan adjuntos.
     */
    default void sendHtml(final String toEmail, final String subject, final String htmlBody,
                          final List<EmailAttachment> attachments) {
        sendHtml(toEmail, subject, htmlBody);
    }

    record EmailAttachment(String fileName, String contentType, byte[] content, String contentId) {
        public EmailAttachment {
            if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("attachment_filename_required");
            if (contentType == null || contentType.isBlank()) throw new IllegalArgumentException("attachment_content_type_required");
            if (content == null || content.length == 0) throw new IllegalArgumentException("attachment_content_required");
        }
    }
}

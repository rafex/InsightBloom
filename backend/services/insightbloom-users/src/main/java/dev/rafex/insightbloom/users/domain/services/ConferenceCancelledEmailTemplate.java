package dev.rafex.insightbloom.users.domain.services;

/**
 * Email HTML de notificación de cancelación de evento.
 */
public final class ConferenceCancelledEmailTemplate {
    private static final String BG = "#1e1e24";
    private static final String ACCENT = "#ef4444";
    private static final String TEXT_MAIN = "#f8fafc";
    private static final String TEXT_MUTED = "#94a3b8";

    private ConferenceCancelledEmailTemplate() { }

    public static String render(final String eventName, final String reason) {
        final String safeEventName = escapeHtml(eventName);
        final String safeReason = escapeHtml(reason != null && !reason.isBlank() ? reason : "");
        final String reasonHtml = safeReason.isBlank() ? ""
                : "<p style=\"color:" + TEXT_MUTED + ";font-size:13px;margin-top:12px;line-height:1.5;\">"
                + "Motivo: <strong>" + safeReason + "</strong></p>";

        return "<!DOCTYPE html><html lang=\"es\"><body style=\"margin:0;padding:24px;"
                + "background:#f3f4f6;font-family:system-ui,-apple-system,'Segoe UI',sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:520px;margin:0 auto;\">"
                + "<tr><td style=\"background:" + BG + ";border-radius:16px;padding:28px 24px;\">"
                + "<div style=\"color:" + ACCENT + ";font-weight:700;font-size:13px;letter-spacing:1px;text-transform:uppercase;margin-bottom:4px;\">"
                + "◈ InsightBloom — Evento cancelado</div>"
                + "<h1 style=\"color:" + TEXT_MAIN + ";font-size:20px;margin:8px 0 20px;line-height:1.3;\">" + safeEventName + "</h1>"
                + "<div style=\"background:" + ACCENT + ";border-radius:12px;padding:16px;color:" + TEXT_MAIN + ";font-size:14px;line-height:1.6;\">"
                + "<strong>El evento ha sido cancelado.</strong> No podrás acceder a él ni realizar nuevas acciones."
                + reasonHtml
                + "</div>"
                + "<p style=\"color:" + TEXT_MUTED + ";font-size:11px;margin-top:20px;\">"
                + "Si tienes dudas, contacta al organizador del evento.</p>"
                + "</td></tr></table></body></html>";
    }

    private static String escapeHtml(final String text) {
        if (text == null || text.isBlank()) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }
}

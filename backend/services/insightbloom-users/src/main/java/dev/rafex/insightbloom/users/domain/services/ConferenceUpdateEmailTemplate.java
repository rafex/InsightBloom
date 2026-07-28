package dev.rafex.insightbloom.users.domain.services;

import java.util.List;

/**
 * Email HTML de notificación de cambios en un evento: muestra el snapshot completo (todos los
 * campos relevantes, no solo los modificados) resaltando las filas que cambiaron. Mismo estilo
 * visual que {@link TicketEmailTemplate}/{@link AttendeeEmailTemplate}.
 */
public final class ConferenceUpdateEmailTemplate {
    private static final String BG = "#1e1e24";
    private static final String ACCENT = "#7c3aed";
    private static final String TEXT_MAIN = "#f8fafc";
    private static final String TEXT_MUTED = "#94a3b8";
    private static final String CHANGED_BG = "#fef3c7";
    private static final String CHANGED_BORDER = "#d97706";

    private ConferenceUpdateEmailTemplate() { }

    /** Una fila de información del evento; {@code changed} indica si esta corrida modificó el valor. */
    public record FieldRow(String label, String oldValue, String newValue, boolean changed) {}

    public static String render(final String eventName, final List<FieldRow> rows) {
        final String safeEventName = escapeHtml(eventName);
        final StringBuilder rowsHtml = new StringBuilder();
        for (final FieldRow row : rows) {
            rowsHtml.append(renderRow(row));
        }
        return "<!DOCTYPE html><html lang=\"es\"><body style=\"margin:0;padding:24px;"
                + "background:#f3f4f6;font-family:system-ui,-apple-system,'Segoe UI',sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:520px;margin:0 auto;\">"
                + "<tr><td style=\"background:" + BG + ";border-radius:16px;padding:28px 24px;\">"
                + "<div style=\"color:" + ACCENT + ";font-weight:700;font-size:13px;letter-spacing:1px;text-transform:uppercase;margin-bottom:4px;\">"
                + "◈ InsightBloom — Cambios en el evento</div>"
                + "<h1 style=\"color:" + TEXT_MAIN + ";font-size:20px;margin:8px 0 20px;line-height:1.3;\">" + safeEventName + "</h1>"
                + "<div style=\"background:#fff;border-radius:12px;padding:8px 16px;\">"
                + rowsHtml
                + "</div>"
                + "<p style=\"color:" + TEXT_MUTED + ";font-size:11px;margin-top:20px;\">"
                + "Las filas resaltadas indican lo que cambió; el resto se muestra tal cual sigue.</p>"
                + "</td></tr></table></body></html>";
    }

    private static String renderRow(final FieldRow row) {
        final String safeLabel = escapeHtml(row.label());
        if (!row.changed()) {
            final String safeValue = escapeHtml(blank(row.newValue()));
            return "<div style=\"padding:10px 0;border-bottom:1px solid #f3f4f6;\">"
                    + "<div style=\"color:#6b7280;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:.04em;\">" + safeLabel + "</div>"
                    + "<div style=\"color:#1f2937;font-size:14px;margin-top:2px;\">" + safeValue + "</div>"
                    + "</div>";
        }
        final String safeOld = escapeHtml(blank(row.oldValue()));
        final String safeNew = escapeHtml(blank(row.newValue()));
        return "<div style=\"padding:10px 8px;margin:4px 0;background:" + CHANGED_BG + ";border-left:4px solid " + CHANGED_BORDER + ";border-radius:6px;\">"
                + "<div style=\"color:#92400e;font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.04em;\">" + safeLabel + " · actualizado</div>"
                + "<div style=\"color:#78350f;font-size:13px;text-decoration:line-through;margin-top:2px;\">" + safeOld + "</div>"
                + "<div style=\"color:#1f2937;font-size:14px;font-weight:600;margin-top:2px;\">" + safeNew + "</div>"
                + "</div>";
    }

    private static String blank(final String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }

    private static String escapeHtml(final String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

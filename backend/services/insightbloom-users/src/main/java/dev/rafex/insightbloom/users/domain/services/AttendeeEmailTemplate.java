package dev.rafex.insightbloom.users.domain.services;

import java.util.regex.Pattern;

/**
 * Email HTML para un mensaje libre del organizador hacia inscritos (masivo o individual),
 * mismo estilo visual que {@link TicketEmailTemplate}.
 *
 * Soporta tres formatos: text (comportamiento actual, escape + br),
 * html (sanitizacion whitelist de tags semanticos), y markdown (tratado como html
 * sanitizado, ya que el frontend convierte MD→HTML antes de enviar).
 */
public final class AttendeeEmailTemplate {
    private static final String BG = "#1e1e24";
    private static final String ACCENT = "#7c3aed";
    private static final String TEXT_MAIN = "#f8fafc";
    private static final String TEXT_MUTED = "#94a3b8";

    private static final Pattern STRIP_TAGS = Pattern.compile(
            "</?(?:html|head|body|script|style|iframe|link|meta|title|base|form|input|button|select|option|textarea"
                    + "|object|embed|param|applet|frame|frameset|noscript)(?:\\s[^>]*)?>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STRIP_ATTRS = Pattern.compile(
            "\\s(?:on\\w+|style|id|class)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)",
            Pattern.CASE_INSENSITIVE);

    private AttendeeEmailTemplate() { }

    public static String render(final String eventName, final String subject, final String message) {
        return render(eventName, subject, message, "text");
    }

    public static String render(final String eventName, final String subject, final String message, final String format) {
        final String safeEventName = escapeHtml(eventName);
        final String safeSubject = escapeHtml(subject);
        final String formattedMessage = formatMessage(message, format);
        return "<!DOCTYPE html><html lang=\"es\"><body style=\"margin:0;padding:24px;"
                + "background:#f3f4f6;font-family:system-ui,-apple-system,'Segoe UI',sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:480px;margin:0 auto;\">"
                + "<tr><td style=\"background:" + BG + ";border-radius:16px;padding:28px 24px;\">"
                + "<div style=\"color:" + ACCENT + ";font-weight:700;font-size:13px;letter-spacing:1px;text-transform:uppercase;margin-bottom:4px;\">"
                + "◈ InsightBloom — " + safeEventName + "</div>"
                + "<h1 style=\"color:" + TEXT_MAIN + ";font-size:20px;margin:8px 0 20px;line-height:1.3;\">" + safeSubject + "</h1>"
                + "<div style=\"background:#fff;border-radius:12px;padding:20px;color:#1f2937;font-size:14px;line-height:1.6;\">"
                + formattedMessage
                + "</div>"
                + "<p style=\"color:" + TEXT_MUTED + ";font-size:11px;margin-top:20px;\">Mensaje enviado por el organizador de " + safeEventName + ".</p>"
                + "</td></tr></table></body></html>";
    }

    private static String formatMessage(final String message, final String format) {
        if (message == null) return "";
        return switch (format) {
            case "text" -> escapeHtml(message).replace("\n", "<br>");
            case "html", "markdown" -> sanitizeHtml(message);
            default -> escapeHtml(message).replace("\n", "<br>");
        };
    }

    static String sanitizeHtml(final String html) {
        if (html == null) return "";
        String result = STRIP_TAGS.matcher(html).replaceAll("");
        result = STRIP_ATTRS.matcher(result).replaceAll("");
        return compressWhitespace(result);
    }

    private static String compressWhitespace(final String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String escapeHtml(final String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

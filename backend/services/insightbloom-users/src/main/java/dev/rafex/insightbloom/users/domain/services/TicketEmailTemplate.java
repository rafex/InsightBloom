package dev.rafex.insightbloom.users.domain.services;

/**
 * Email HTML del boleto (2026-07-27), inspirado en la tarjeta de TicketPage.vue pero
 * simplificado a lo que los clientes de correo soportan de verdad: tabla + estilos inline
 * (nada de CSS custom properties, gradiente radial ni transform 3D). Sin plantillas: es más
 * simple mantener un único método que concatenar fragmentos, dado que todo el resto de los
 * emails de esta app también arma el cuerpo a mano (ver TicketUseCase, JoinConferenceUseCase).
 */
public final class TicketEmailTemplate {
    private static final String BG = "#1e1e24";
    private static final String ACCENT = "#7c3aed";
    private static final String TEXT_MAIN = "#f8fafc";
    private static final String TEXT_MUTED = "#94a3b8";

    private TicketEmailTemplate() { }

    public static String render(final String eventName, final String ticketUrl,
                                 final String qrDataUri, final String ticketCode) {
        final String safeEventName = escapeHtml(eventName);
        final String safeUrl = escapeHtml(ticketUrl);
        final String safeCode = escapeHtml(ticketCode);
        return "<!DOCTYPE html><html lang=\"es\"><body style=\"margin:0;padding:24px;"
                + "background:#f3f4f6;font-family:system-ui,-apple-system,'Segoe UI',sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:420px;margin:0 auto;\">"
                + "<tr><td style=\"background:" + BG + ";border-radius:16px;padding:28px 24px;text-align:center;\">"
                + "<div style=\"color:" + ACCENT + ";font-weight:700;font-size:13px;letter-spacing:1px;text-transform:uppercase;margin-bottom:4px;\">"
                + "◈ InsightBloom — Boleto de acceso</div>"
                + "<h1 style=\"color:" + TEXT_MAIN + ";font-size:22px;margin:8px 0 20px;line-height:1.3;\">" + safeEventName + "</h1>"
                + "<div style=\"background:#fff;border-radius:12px;padding:16px;display:inline-block;\">"
                + "<img src=\"" + qrDataUri + "\" width=\"220\" height=\"220\" alt=\"Código QR del boleto\" style=\"display:block;\">"
                + "</div>"
                + "<p style=\"color:" + TEXT_MUTED + ";font-size:12px;margin:16px 0 4px;\">Código manual / UUID del boleto</p>"
                + "<code style=\"color:" + TEXT_MAIN + ";font-size:13px;word-break:break-all;\">" + safeCode + "</code>"
                + "<div style=\"margin-top:24px;\">"
                + "<a href=\"" + safeUrl + "\" style=\"display:inline-block;background:" + ACCENT + ";color:#fff;"
                + "text-decoration:none;padding:12px 28px;border-radius:8px;font-weight:600;font-size:14px;\">Ver mi boleto</a>"
                + "</div>"
                + "<p style=\"color:" + TEXT_MUTED + ";font-size:11px;margin-top:20px;\">Presenta este QR o el código manual en la entrada.</p>"
                + "</td></tr></table></body></html>";
    }

    private static String escapeHtml(final String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

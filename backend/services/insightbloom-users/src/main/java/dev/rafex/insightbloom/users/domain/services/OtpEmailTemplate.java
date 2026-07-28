package dev.rafex.insightbloom.users.domain.services;

/**
 * Email HTML del codigo de acceso (login OTP, 2026-07-28) -- mismo estilo visual que
 * {@link TicketEmailTemplate} (tabla + estilos inline, sin CSS custom properties ni efectos que
 * los clientes de correo no soportan) para que la plataforma se vea consistente en cualquier
 * correo transaccional.
 */
public final class OtpEmailTemplate {
    private static final String BG = "#1e1e24";
    private static final String ACCENT = "#7c3aed";
    private static final String TEXT_MAIN = "#f8fafc";
    private static final String TEXT_MUTED = "#94a3b8";

    private OtpEmailTemplate() { }

    public static String render(final String code) {
        final String safeCode = escapeHtml(code);
        return "<!DOCTYPE html><html lang=\"es\"><body style=\"margin:0;padding:24px;"
                + "background:#f3f4f6;font-family:system-ui,-apple-system,'Segoe UI',sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:420px;margin:0 auto;\">"
                + "<tr><td style=\"background:" + BG + ";border-radius:16px;padding:28px 24px;text-align:center;\">"
                + "<div style=\"color:" + ACCENT + ";font-weight:700;font-size:13px;letter-spacing:1px;text-transform:uppercase;margin-bottom:4px;\">"
                + "◈ InsightBloom — Código de acceso</div>"
                + "<h1 style=\"color:" + TEXT_MAIN + ";font-size:20px;margin:8px 0 20px;line-height:1.3;\">Iniciar sesión</h1>"
                + "<div style=\"background:#fff;border-radius:12px;padding:20px;display:inline-block;\">"
                + "<span style=\"color:" + BG + ";font-size:32px;font-weight:700;letter-spacing:6px;\">" + safeCode + "</span>"
                + "</div>"
                + "<p style=\"color:" + TEXT_MUTED + ";font-size:12px;margin:20px 0 0;\">Este código vence en 10 minutos.</p>"
                + "<p style=\"color:" + TEXT_MUTED + ";font-size:11px;margin-top:16px;\">Si no intentaste iniciar sesión, podés ignorar este correo.</p>"
                + "</td></tr></table></body></html>";
    }

    private static String escapeHtml(final String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

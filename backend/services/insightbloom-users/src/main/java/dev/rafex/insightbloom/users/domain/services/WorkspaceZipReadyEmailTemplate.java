package dev.rafex.insightbloom.users.domain.services;

/**
 * Email HTML de "tu workspace está listo para descargar" (2026-08), mismo patrón hecho-a-mano
 * que {@link TicketEmailTemplate} (tabla + estilos inline, sin motor de plantillas).
 */
public final class WorkspaceZipReadyEmailTemplate {
    private static final String BG = "#1e1e24";
    private static final String ACCENT = "#7c3aed";
    private static final String TEXT_MAIN = "#f8fafc";
    private static final String TEXT_MUTED = "#94a3b8";

    private WorkspaceZipReadyEmailTemplate() { }

    public static String render(final String conferenceName, final String downloadUrl) {
        final String safeName = escapeHtml(conferenceName);
        final String safeUrl = escapeHtml(downloadUrl);
        return "<!DOCTYPE html><html lang=\"es\"><body style=\"margin:0;padding:24px;"
                + "background:#f3f4f6;font-family:system-ui,-apple-system,'Segoe UI',sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:420px;margin:0 auto;\">"
                + "<tr><td style=\"background:" + BG + ";border-radius:16px;padding:28px 24px;text-align:center;\">"
                + "<div style=\"color:" + ACCENT + ";font-weight:700;font-size:13px;letter-spacing:1px;text-transform:uppercase;margin-bottom:4px;\">"
                + "◈ InsightBloom — Workspace listo</div>"
                + "<h1 style=\"color:" + TEXT_MAIN + ";font-size:22px;margin:8px 0 12px;line-height:1.3;\">Tu descarga está lista</h1>"
                + "<p style=\"color:" + TEXT_MUTED + ";font-size:14px;margin:0 0 20px;\">El ZIP de tu workspace de <strong style=\"color:" + TEXT_MAIN + ";\">" + safeName + "</strong> ya se generó y está disponible por las próximas 2 horas.</p>"
                + "<div>"
                + "<a href=\"" + safeUrl + "\" style=\"display:inline-block;background:" + ACCENT + ";color:#fff;"
                + "text-decoration:none;padding:12px 28px;border-radius:8px;font-weight:600;font-size:14px;\">Descargar workspace</a>"
                + "</div>"
                + "<p style=\"color:" + TEXT_MUTED + ";font-size:11px;margin-top:20px;\">Pasadas las 2 horas, el enlace deja de funcionar y hay que generar un ZIP nuevo desde el IDE.</p>"
                + "</td></tr></table></body></html>";
    }

    private static String escapeHtml(final String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

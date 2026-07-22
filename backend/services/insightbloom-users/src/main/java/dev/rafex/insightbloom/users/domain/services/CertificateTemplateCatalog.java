package dev.rafex.insightbloom.users.domain.services;

import java.util.List;
import java.util.Map;

/** Catálogo inmutable de diseños y variables permitidas para certificados. */
public final class CertificateTemplateCatalog {
    private CertificateTemplateCatalog() {}

    public record Entry(String key, String name, String description, String engine, String documentJson) {}
    public record Variable(String key, String label, String example) {}

    public static List<Entry> entries() {
        return List.of(
                new Entry("classic", "Clásico", "Diseño centrado con borde y datos esenciales.", "HTML_CHROME", classic()),
                new Entry("modern", "Moderno", "Banda de color y composición contemporánea.", "HTML_CHROME", modern()),
                new Entry("minimal", "Minimalista", "Diseño limpio para eventos cortos.", "HTML_CHROME", minimal())
        );
    }

    public static Entry defaultEntry() { return entries().get(0); }

    public static List<Variable> variables() {
        return List.of(
                new Variable("participant.displayName", "Nombre visible", "Ana Pérez"),
                new Variable("participant.firstName", "Nombre", "Ana"),
                new Variable("participant.lastName", "Apellidos", "Pérez"),
                new Variable("participant.email", "Correo", "ana@example.com"),
                new Variable("participant.username", "Usuario", "ana.perez"),
                new Variable("participant.uuid", "ID del participante", "uuid"),
                new Variable("event.name", "Nombre del evento", "Taller de ejemplo"),
                new Variable("event.displayName", "Nombre mostrado del evento", "Taller de ejemplo"),
                new Variable("event.friendlyId", "ID amigable", "taller-ejemplo"),
                new Variable("event.uuid", "ID del evento", "uuid"),
                new Variable("event.date", "Fecha del evento", "22/07/2026"),
                new Variable("event.startTime", "Hora de inicio", "09:00"),
                new Variable("event.endTime", "Hora de cierre", "13:00"),
                new Variable("event.venue", "Lugar", "Auditorio principal"),
                new Variable("event.timezone", "Zona horaria", "America/Mexico_City"),
                new Variable("platform.name", "Nombre de la plataforma", "InsightBloom"),
                new Variable("platform.website", "Sitio de la plataforma", "https://insightbloom.v1.rafex.cloud"),
                new Variable("platform.email", "Correo de contacto", "rafex@rafex.dev"),
                new Variable("platform.github", "GitHub", "https://github.com/rafex"),
                new Variable("platform.linkedin", "LinkedIn", "https://linkedin.com/in/soft-architect-raul-gonzalez"),
                new Variable("platform.telegram", "Telegram", "https://t.me/tabernadelanoche"),
                new Variable("certificate.issuedDate", "Fecha de emisión", "22/07/2026"),
                new Variable("certificate.id", "ID del certificado", "cert-uuid")
        );
    }

    private static String document(final String background, final String accent, final String titleSize) {
        return """
                {"page":{"background":"%s","padding":48},"blocks":[
                {"type":"shape","x":18,"y":18,"width":964,"height":504,"style":{"border":"3px solid %s","borderRadius":18}},
                {"type":"text","x":90,"y":90,"width":820,"height":55,"text":"CERTIFICADO DE ASISTENCIA","style":{"fontSize":%s,"fontWeight":700,"color":"%s","textAlign":"center"}},
                {"type":"text","x":90,"y":175,"width":820,"height":38,"text":"Se otorga el presente certificado a","style":{"fontSize":20,"color":"#4b5563","textAlign":"center"}},
                {"type":"text","x":90,"y":225,"width":820,"height":65,"text":"{{participant.displayName}}","style":{"fontSize":42,"fontWeight":700,"color":"%s","textAlign":"center"}},
                {"type":"text","x":90,"y":320,"width":820,"height":38,"text":"por su participación en","style":{"fontSize":20,"color":"#4b5563","textAlign":"center"}},
                {"type":"text","x":90,"y":365,"width":820,"height":50,"text":"{{event.name}}","style":{"fontSize":28,"fontWeight":600,"color":"%s","textAlign":"center"}},
                {"type":"text","x":90,"y":445,"width":820,"height":25,"text":"Emitido el {{certificate.issuedDate}} · {{platform.name}}","style":{"fontSize":14,"color":"#6b7280","textAlign":"center"}}
                ]}
                """.formatted(background, accent, titleSize, accent, accent, accent).replace("\n", "");
    }

    private static String classic() { return document("#ffffff", "#1e1b4b", "34"); }
    private static String modern() { return document("#f8fafc", "#4f46e5", "36"); }
    private static String minimal() { return document("#ffffff", "#111827", "32"); }
}

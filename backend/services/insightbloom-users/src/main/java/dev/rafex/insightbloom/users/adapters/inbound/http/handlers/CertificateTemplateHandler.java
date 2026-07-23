package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.domain.model.CertificateTemplate;
import dev.rafex.insightbloom.users.domain.model.CertificateSettings;
import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.ports.CertificateTemplateRepository;
import dev.rafex.insightbloom.users.domain.ports.CertificateSettingsRepository;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.services.CertificateTemplateCatalog;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** API de catálogo y edición de plantillas por evento. */
public final class CertificateTemplateHandler extends BaseResourceHandler {
    private static final int MAX_DOCUMENT_LENGTH = 200_000;
    private static final int MAX_BLOCK_TEXT_LENGTH = 5_000;
    private static final Pattern SAFE_COLOR = Pattern.compile("^(#[0-9a-f]{3,8}|rgba?\\([0-9., %]+\\)|transparent)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SAFE_BORDER = Pattern.compile("^(none|\\d{1,3}px\\s+solid\\s+#[0-9a-f]{3,8})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SAFE_IMAGE = Pattern.compile("^data:image/(png|jpeg|gif|webp|svg\\+xml);base64,[a-z0-9+/=]+$", Pattern.CASE_INSENSITIVE);
    private final CertificateTemplateRepository templateRepository;
    private final ConferenceRepository conferenceRepository;
    private final EventPermissionGuard permissionGuard;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final CertificateSettingsRepository certificateSettingsRepository;
    private final JacksonJsonCodec json = JacksonJsonCodec.defaultCodec();

    public CertificateTemplateHandler(final CertificateTemplateRepository templateRepository,
                                      final ConferenceRepository conferenceRepository,
                                      final EventPermissionGuard permissionGuard,
                                      final ValidateTokenUseCase validateTokenUseCase,
                                      final CertificateSettingsRepository certificateSettingsRepository) {
        this.templateRepository = templateRepository;
        this.conferenceRepository = conferenceRepository;
        this.permissionGuard = permissionGuard;
        this.validateTokenUseCase = validateTokenUseCase;
        this.certificateSettingsRepository = certificateSettingsRepository;
    }

    @Override protected String basePath() { return "/api/v1/certificate-templates"; }
    @Override protected List<Route> routes() {
        return List.of(Route.of("/catalog", Set.of("GET")),
                Route.of("/events/{conferenceUuid}", Set.of("GET", "PUT")),
                Route.of("/events/{conferenceUuid}/engine", Set.of("GET", "PUT")),
                Route.of("/events/{conferenceUuid}/legacy", Set.of("GET", "PUT")));
    }
    @Override public Set<String> supportedMethods() { return Set.of("GET", "PUT"); }

    @Override public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        try {
            if (jx.path().endsWith("/catalog")) {
                if (!requireUser(jx)) return true;
                sendOk(jx, Map.of("templates", CertificateTemplateCatalog.entries().stream().map(CertificateTemplateHandler::entryView).toList(),
                        "variables", CertificateTemplateCatalog.variables().stream().map(v -> Map.of(
                                "key", v.key(), "label", v.label(), "example", v.example())).toList()));
                return true;
            }
            final var auth = requireEventManager(jx);
            if (auth == null) return true;
            final String conferenceUuid = jx.pathParam("conferenceUuid");
            if (jx.path().endsWith("/engine")) {
                final var conference = conferenceRepository.findByUuid(conferenceUuid);
                if (conference.isEmpty()) { sendError(jx, 404, "conference_not_found", "Evento no encontrado"); return true; }
                sendOk(jx, Map.of("certificateEngine", conference.get().getCertificateEngine()));
                return true;
            }
            if (jx.path().endsWith("/legacy")) {
                sendOk(jx, legacySettingsView(loadLegacySettings(conferenceUuid)));
                return true;
            }
            final var current = templateRepository.findByConferenceUuid(conferenceUuid);
            final var view = current.map(CertificateTemplateHandler::templateView)
                    .orElseGet(() -> templateView(new CertificateTemplate(conferenceUuid,
                            CertificateTemplateCatalog.defaultEntry().key(),
                            CertificateTemplateCatalog.defaultEntry().name(),
                            CertificateTemplateCatalog.defaultEntry().engine(),
                            CertificateTemplateCatalog.defaultEntry().documentJson(), 1,
                            auth.subjectUuid(), Instant.now())));
            sendOk(jx, view);
        } catch (IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), e.getMessage());
        } catch (Exception e) {
            sendError(jx, 500, "internal_error", "No se pudo cargar la plantilla");
        }
        return true;
    }

    @Override public boolean put(final HttpExchange x) {
        final var jx = asJetty(x);
        final var auth = requireEventManager(jx);
        if (auth == null) return true;
        try {
            final String conferenceUuid = jx.pathParam("conferenceUuid");
            final var conference = conferenceRepository.findByUuid(conferenceUuid);
            if (conference.isEmpty()) {
                sendError(jx, 404, "conference_not_found", "Evento no encontrado"); return true;
            }
            final Map<String, Object> body = parseBody(jx);
            if (jx.path().endsWith("/engine")) {
                final String engine = body.get("certificateEngine") instanceof String s ? s : "INHOUSE";
                if (!"INHOUSE".equals(engine) && !"HTML_CHROME".equals(engine)) {
                    throw new IllegalArgumentException("El motor debe ser INHOUSE o HTML_CHROME");
                }
                if ("HTML_CHROME".equals(engine)) {
                    ensureHtmlTemplate(conferenceUuid, auth.subjectUuid());
                }
                conference.get().setCertificateEngine(engine);
                conferenceRepository.save(conference.get());
                sendOk(jx, conference.get());
                return true;
            }
            if (jx.path().endsWith("/legacy")) {
                final CertificateSettings settings = settingsFromBody(body);
                final String documentJson = json.toJson(legacySettingsView(settings));
                final int version = templateRepository.findByConferenceUuid(conferenceUuid).map(t -> t.getVersion() + 1).orElse(1);
                final CertificateTemplate saved = new CertificateTemplate(conferenceUuid, "inhouse", "Diseño interno",
                        "INHOUSE", documentJson, version, auth.subjectUuid(), Instant.now());
                templateRepository.save(saved);
                conference.get().setCertificateEngine("INHOUSE");
                conferenceRepository.save(conference.get());
                sendOk(jx, legacySettingsView(settings));
                return true;
            }
            final String key = body.get("templateKey") instanceof String s ? s : "custom";
            final var catalog = CertificateTemplateCatalog.entries().stream().filter(e -> e.key().equals(key)).findFirst();
            final String name = body.get("templateName") instanceof String s && !s.isBlank()
                    ? s.trim() : catalog.map(CertificateTemplateCatalog.Entry::name).orElse("Personalizada");
            final String engine = body.get("engine") instanceof String s ? s : "HTML_CHROME";
            final String documentJson = body.get("documentJson") instanceof String s ? s : null;
            validateDocument(engine, documentJson);
            final int version = templateRepository.findByConferenceUuid(conferenceUuid).map(t -> t.getVersion() + 1).orElse(1);
            final CertificateTemplate saved = new CertificateTemplate(conferenceUuid, key, name, engine, documentJson,
                    version, auth.subjectUuid(), Instant.now());
            templateRepository.save(saved);
            // La conferencia es la fuente de verdad que consulta GenerateCertificateUseCase.
            // Mantenerla sincronizada evita que el editor visual guarde HTML_CHROME mientras
            // el certificado público continúa usando el motor legacy INHOUSE.
            conference.get().setCertificateEngine(engine);
            conferenceRepository.save(conference.get());
            sendOk(jx, savedView(saved));
        } catch (IllegalArgumentException e) {
            sendError(jx, 400, "invalid_template", e.getMessage());
        } catch (Exception e) {
            sendError(jx, 500, "internal_error", "No se pudo guardar la plantilla");
        }
        return true;
    }

    private void validateDocument(final String engine, final String documentJson) {
        if (!"HTML_CHROME".equals(engine)) throw new IllegalArgumentException("El motor debe ser HTML_CHROME");
        if (documentJson == null || documentJson.isBlank() || documentJson.length() > MAX_DOCUMENT_LENGTH) {
            throw new IllegalArgumentException("El documento es obligatorio y no puede exceder 200 KB");
        }
        try {
            final JsonNode root = json.readTree(documentJson);
            if (!root.isObject() || !root.path("page").isObject() || !root.path("blocks").isArray()) {
                throw new IllegalArgumentException("El documento debe contener page y blocks");
            }
            if (root.path("blocks").size() > 100) throw new IllegalArgumentException("Máximo 100 bloques");
            validatePage(root.path("page"));
            for (final JsonNode block : root.path("blocks")) validateBlock(block);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("El documento no es JSON válido"); }
    }

    private static void validatePage(final JsonNode page) {
        if (page.has("background") && (!page.path("background").isTextual()
                || !SAFE_COLOR.matcher(page.path("background").asText()).matches())) {
            throw new IllegalArgumentException("El fondo del certificado no es válido");
        }
        if (page.has("padding")) validateNumber(page.path("padding"), 0, 200, "padding");
    }

    private static void validateBlock(final JsonNode block) {
        if (!block.isObject()) throw new IllegalArgumentException("Cada bloque debe ser un objeto");
        final String type = block.path("type").asText("");
        if (!Set.of("text", "image", "shape").contains(type)) {
            throw new IllegalArgumentException("Tipo de bloque no permitido");
        }
        validateNumber(block.path("x"), -2_000, 3_000, "x");
        validateNumber(block.path("y"), -2_000, 3_000, "y");
        validateNumber(block.path("width"), 1, 3_000, "width");
        validateNumber(block.path("height"), 1, 3_000, "height");
        if ("text".equals(type)) {
            if (!block.path("text").isTextual() || block.path("text").asText().length() > MAX_BLOCK_TEXT_LENGTH) {
                throw new IllegalArgumentException("El texto del bloque no es válido");
            }
        }
        if ("image".equals(type)) {
            if (!block.path("src").isTextual() || !SAFE_IMAGE.matcher(block.path("src").asText()).matches()) {
                throw new IllegalArgumentException("La imagen debe ser una data URL local permitida");
            }
        }
        if (block.has("style")) validateStyle(block.path("style"));
    }

    private static void validateStyle(final JsonNode style) {
        if (!style.isObject()) throw new IllegalArgumentException("El estilo del bloque no es válido");
        if (style.has("fontSize")) validateNumber(style.path("fontSize"), 1, 200, "fontSize");
        if (style.has("lineHeight")) validateNumber(style.path("lineHeight"), 0, 200, "lineHeight");
        if (style.has("borderRadius")) validateNumber(style.path("borderRadius"), 0, 500, "borderRadius");
        if (style.has("padding")) validateNumber(style.path("padding"), 0, 500, "padding");
        for (final String key : List.of("color", "background")) {
            if (style.has(key) && (!style.path(key).isTextual() || !SAFE_COLOR.matcher(style.path(key).asText()).matches())) {
                throw new IllegalArgumentException("El color del estilo no es válido");
            }
        }
        if (style.has("border") && (!style.path("border").isTextual() || !SAFE_BORDER.matcher(style.path("border").asText()).matches())) {
            throw new IllegalArgumentException("El borde del estilo no es válido");
        }
        if (style.has("textAlign") && (!style.path("textAlign").isTextual()
                || !Set.of("left", "center", "right").contains(style.path("textAlign").asText()))) {
            throw new IllegalArgumentException("La alineación no es válida");
        }
    }

    private static void validateNumber(final JsonNode value, final double min, final double max, final String field) {
        if (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble() < min || value.asDouble() > max) {
            throw new IllegalArgumentException("El campo " + field + " no es válido");
        }
    }

    private CertificateSettings loadLegacySettings(final String conferenceUuid) {
        final var current = templateRepository.findByConferenceUuid(conferenceUuid);
        if (current.isPresent() && "INHOUSE".equals(current.get().getEngine())) {
            try {
                return settingsFromNode(json.readTree(current.get().getDocumentJson()), certificateSettingsRepository.get());
            } catch (Exception ignored) { /* se usa el respaldo global */ }
        }
        return certificateSettingsRepository.get();
    }

    /**
     * Activar HTML_CHROME debe dejar al evento en un estado renderizable aunque
     * el organizador aún no haya abierto el editor visual. El editor devuelve
     * este mismo diseño como punto de partida y luego lo reemplaza al guardar.
     */
    private void ensureHtmlTemplate(final String conferenceUuid, final String userUuid) {
        final var current = templateRepository.findByConferenceUuid(conferenceUuid);
        if (current.isPresent() && "HTML_CHROME".equals(current.get().getEngine())) return;
        final var entry = CertificateTemplateCatalog.defaultEntry();
        final int version = current.map(t -> t.getVersion() + 1).orElse(1);
        templateRepository.save(new CertificateTemplate(conferenceUuid, entry.key(), entry.name(),
                entry.engine(), entry.documentJson(), version, userUuid, Instant.now()));
    }

    private CertificateSettings settingsFromBody(final Map<String, Object> body) {
        final CertificateSettings defaults = certificateSettingsRepository.get();
        if (body.get("logoBase64") instanceof String s) defaults.setLogoBase64(s.isBlank() ? null : s);
        if (body.get("fontFamily") instanceof String s) defaults.setFontFamily(s);
        if (body.get("titleFontSize") instanceof Number n) defaults.setTitleFontSize(n.intValue());
        if (body.get("bodyFontSize") instanceof Number n) defaults.setBodyFontSize(n.intValue());
        if (body.get("primaryColorHex") instanceof String s) defaults.setPrimaryColorHex(s);
        if (body.get("showVenue") instanceof Boolean b) defaults.setShowVenue(b);
        if (body.get("showSchedule") instanceof Boolean b) defaults.setShowSchedule(b);
        if (body.get("showIssuedDate") instanceof Boolean b) defaults.setShowIssuedDate(b);
        validateLegacySettings(defaults);
        return defaults;
    }

    private static CertificateSettings settingsFromNode(final JsonNode node, final CertificateSettings fallback) {
        final CertificateSettings settings = CertificateSettings.defaults();
        settings.setLogoBase64(node.path("logoBase64").isNull() ? null : node.path("logoBase64").asText(fallback.getLogoBase64()));
        settings.setFontFamily(node.path("fontFamily").asText(fallback.getFontFamily()));
        settings.setTitleFontSize(node.path("titleFontSize").asInt(fallback.getTitleFontSize()));
        settings.setBodyFontSize(node.path("bodyFontSize").asInt(fallback.getBodyFontSize()));
        settings.setPrimaryColorHex(node.path("primaryColorHex").asText(fallback.getPrimaryColorHex()));
        settings.setShowVenue(node.path("showVenue").asBoolean(fallback.isShowVenue()));
        settings.setShowSchedule(node.path("showSchedule").asBoolean(fallback.isShowSchedule()));
        settings.setShowIssuedDate(node.path("showIssuedDate").asBoolean(fallback.isShowIssuedDate()));
        validateLegacySettings(settings);
        return settings;
    }

    private static void validateLegacySettings(final CertificateSettings settings) {
        if (!Set.of("HELVETICA", "TIMES_ROMAN", "COURIER").contains(settings.getFontFamily())) {
            throw new IllegalArgumentException("Tipo de letra no permitido");
        }
        if (settings.getTitleFontSize() < 14 || settings.getTitleFontSize() > 48
                || settings.getBodyFontSize() < 8 || settings.getBodyFontSize() > 24) {
            throw new IllegalArgumentException("Tamaño de texto fuera de rango");
        }
        if (settings.getPrimaryColorHex() == null || !settings.getPrimaryColorHex().matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Color principal inválido");
        }
        if (settings.getLogoBase64() != null && settings.getLogoBase64().length() > 2_000_000) {
            throw new IllegalArgumentException("El logotipo es demasiado grande");
        }
    }

    private static Map<String, Object> legacySettingsView(final CertificateSettings settings) {
        final Map<String, Object> view = new LinkedHashMap<>();
        view.put("logoBase64", settings.getLogoBase64());
        view.put("fontFamily", settings.getFontFamily());
        view.put("titleFontSize", settings.getTitleFontSize());
        view.put("bodyFontSize", settings.getBodyFontSize());
        view.put("primaryColorHex", settings.getPrimaryColorHex());
        view.put("showVenue", settings.isShowVenue());
        view.put("showSchedule", settings.isShowSchedule());
        view.put("showIssuedDate", settings.isShowIssuedDate());
        return view;
    }

    private ValidateTokenUseCase.ValidationResult requireEventManager(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return null; }
        final var auth = validateTokenUseCase.execute(token);
        if (!auth.valid() || !"user".equals(auth.kind())) { sendError(jx, 403, "forbidden", "Se requiere una sesión de usuario"); return null; }
        final String conferenceUuid = jx.pathParam("conferenceUuid");
        final var conference = conferenceRepository.findByUuid(conferenceUuid);
        final boolean ownerOrganizer = conference.map(c -> c.getCreatedByUserUuid().equals(auth.subjectUuid())
                && legacyRoleHasAny(auth.role(), "organizer")).orElse(false);
        final boolean allowed = permissionGuard.hasPermission(conferenceUuid, auth.subjectUuid(), auth.role(), Permission.MANAGE_CERTIFICATE)
                || ownerOrganizer;
        if (!allowed) { sendError(jx, 403, "forbidden", "No tienes permiso para editar el certificado de este evento"); return null; }
        return auth;
    }

    private boolean requireUser(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        final var auth = token == null ? null : validateTokenUseCase.execute(token);
        if (auth == null || !auth.valid() || !"user".equals(auth.kind())) {
            sendError(jx, 401, "token_missing", "Authorization required"); return false;
        }
        return true;
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
    }

    private static Map<String, Object> entryView(final CertificateTemplateCatalog.Entry e) {
        return Map.of("key", e.key(), "name", e.name(), "description", e.description(), "engine", e.engine(), "documentJson", e.documentJson());
    }
    private static Map<String, Object> templateView(final CertificateTemplate t) {
        final Map<String, Object> view = new LinkedHashMap<>();
        view.put("conferenceUuid", t.getConferenceUuid()); view.put("templateKey", t.getTemplateKey());
        view.put("templateName", t.getTemplateName()); view.put("engine", t.getEngine());
        view.put("documentJson", t.getDocumentJson()); view.put("version", t.getVersion());
        view.put("updatedAt", t.getUpdatedAt().toString()); return view;
    }
    private static Map<String, Object> savedView(final CertificateTemplate t) { return templateView(t); }
}

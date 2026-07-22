package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.domain.model.CertificateTemplate;
import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.ports.CertificateTemplateRepository;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.services.CertificateTemplateCatalog;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** API de catálogo y edición de plantillas por evento. */
public final class CertificateTemplateHandler extends BaseResourceHandler {
    private static final int MAX_DOCUMENT_LENGTH = 200_000;
    private final CertificateTemplateRepository templateRepository;
    private final ConferenceRepository conferenceRepository;
    private final EventPermissionGuard permissionGuard;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final JacksonJsonCodec json = JacksonJsonCodec.defaultCodec();

    public CertificateTemplateHandler(final CertificateTemplateRepository templateRepository,
                                      final ConferenceRepository conferenceRepository,
                                      final EventPermissionGuard permissionGuard,
                                      final ValidateTokenUseCase validateTokenUseCase) {
        this.templateRepository = templateRepository;
        this.conferenceRepository = conferenceRepository;
        this.permissionGuard = permissionGuard;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    @Override protected String basePath() { return "/api/v1/certificate-templates"; }
    @Override protected List<Route> routes() {
        return List.of(Route.of("/catalog", Set.of("GET")), Route.of("/events/{conferenceUuid}", Set.of("GET", "PUT")));
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
            if (conferenceRepository.findByUuid(conferenceUuid).isEmpty()) {
                sendError(jx, 404, "conference_not_found", "Evento no encontrado"); return true;
            }
            final Map<String, Object> body = parseBody(jx);
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
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("El documento no es JSON válido"); }
    }

    private ValidateTokenUseCase.ValidationResult requireEventManager(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return null; }
        final var auth = validateTokenUseCase.execute(token);
        if (!auth.valid() || !"user".equals(auth.kind())) { sendError(jx, 403, "forbidden", "Se requiere una sesión de usuario"); return null; }
        final String conferenceUuid = jx.pathParam("conferenceUuid");
        final var conference = conferenceRepository.findByUuid(conferenceUuid);
        final boolean ownerOrganizer = conference.map(c -> c.getCreatedByUserUuid().equals(auth.subjectUuid())
                && auth.role() != null && auth.role().contains("organizer")).orElse(false);
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

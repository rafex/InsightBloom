package dev.rafex.insightbloom.users.application.usecases;

import com.fasterxml.jackson.databind.JsonNode;
import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.users.domain.model.CertificateSettings;
import dev.rafex.insightbloom.users.domain.model.CertificatePlatformData;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.CertificateTemplateRepository;
import dev.rafex.insightbloom.users.domain.ports.CertificateSettingsRepository;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SurveyPort;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.adapters.outbound.presentationsclient.CertificateRenderer;
import dev.rafex.insightbloom.users.domain.services.CertificateTemplateCatalog;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generates an attendance certificate PDF in-memory. The PDF is never persisted to disk;
 * each download is regenerated on demand (ephemeral by design).
 */
public class GenerateCertificateUseCase {
    private static final JacksonJsonCodec JSON = JacksonJsonCodec.defaultCodec();
    private final ConferenceRepository conferenceRepository;
    private final UserRepository userRepository;
    private final SurveyPort surveyPort;
    private final CertificateSettingsRepository certificateSettingsRepository;
    private final CertificateTemplateRepository certificateTemplateRepository;
    private final CertificateRenderer certificateRenderer;
    private final CertificatePlatformData platformData;

    public GenerateCertificateUseCase(final ConferenceRepository conferenceRepository,
                                       final UserRepository userRepository,
                                       final SurveyPort surveyPort,
                                       final CertificateSettingsRepository certificateSettingsRepository) {
        this(conferenceRepository, userRepository, surveyPort, certificateSettingsRepository, null, null,
                new CertificatePlatformData("InsightBloom", "https://insightbloom.v1.rafex.cloud", "", "", "", ""));
    }

    public GenerateCertificateUseCase(final ConferenceRepository conferenceRepository,
                                      final UserRepository userRepository,
                                      final SurveyPort surveyPort,
                                      final CertificateSettingsRepository certificateSettingsRepository,
                                      final CertificateTemplateRepository certificateTemplateRepository,
                                      final CertificateRenderer certificateRenderer,
                                      final CertificatePlatformData platformData) {
        this.conferenceRepository = conferenceRepository;
        this.userRepository = userRepository;
        this.surveyPort = surveyPort;
        this.certificateSettingsRepository = certificateSettingsRepository;
        this.certificateTemplateRepository = certificateTemplateRepository;
        this.certificateRenderer = certificateRenderer;
        this.platformData = platformData;
    }

    public record Result(byte[] pdfBytes, String fileName, boolean profileIncomplete) {}

    public Result execute(final String conferenceUuid, final String userUuid, final String token) {
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        final User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));

        if (!surveyPort.hasResponded(conferenceUuid, token)) {
            throw new IllegalStateException("survey_not_completed");
        }

        final String fullName = buildFullName(user);
        final boolean profileIncomplete = fullName == null;
        final String attendeeName = fullName != null ? fullName
                : (user.getDisplayName() != null ? user.getDisplayName() : "Asistente");
        final CertificateSettings settings = certificateSettingsRepository.get();

        try {
            final byte[] pdf = renderEventTemplateOrLegacy(conference, user, attendeeName, profileIncomplete, settings);
            final String fileName = "certificado-" + conference.getFriendlyId() + ".pdf";
            return new Result(pdf, fileName, profileIncomplete);
        } catch (final IOException e) {
            throw new RuntimeException("certificate_generation_failed", e);
        }
    }

    private byte[] renderEventTemplateOrLegacy(final Conference conference, final User user,
                                                final String attendeeName, final boolean profileIncomplete,
                                                final CertificateSettings settings) throws IOException {
        if ("HTML_CHROME".equals(conference.getCertificateEngine())) {
            if (certificateRenderer == null) {
                throw new IllegalStateException("certificate_html_renderer_unavailable");
            }
            final String documentJson = certificateTemplateRepository == null
                    ? CertificateTemplateCatalog.defaultEntry().documentJson()
                    : certificateTemplateRepository.findByConferenceUuid(conference.getUuid())
                    .filter(t -> "HTML_CHROME".equals(t.getEngine()))
                    .map(t -> t.getDocumentJson())
                    .orElse(CertificateTemplateCatalog.defaultEntry().documentJson());
            return certificateRenderer.render(documentJson, certificateData(conference, user, attendeeName));
        }
        if (certificateTemplateRepository != null) {
            final var template = certificateTemplateRepository.findByConferenceUuid(conference.getUuid());
            if (template.isPresent() && "INHOUSE".equals(template.get().getEngine())) {
                return renderPdf(conference, attendeeName, profileIncomplete,
                        legacySettingsFromJson(template.get().getDocumentJson(), settings));
            }
        }
        return renderPdf(conference, attendeeName, profileIncomplete, settings);
    }

    private static CertificateSettings legacySettingsFromJson(final String documentJson,
                                                               final CertificateSettings fallback) {
        try {
            final JsonNode node = JSON.readTree(documentJson);
            final CertificateSettings settings = CertificateSettings.defaults();
            settings.setLogoBase64(node.path("logoBase64").isNull() ? null
                    : node.path("logoBase64").asText(fallback.getLogoBase64()));
            settings.setFontFamily(node.path("fontFamily").asText(fallback.getFontFamily()));
            settings.setTitleFontSize(node.path("titleFontSize").asInt(fallback.getTitleFontSize()));
            settings.setBodyFontSize(node.path("bodyFontSize").asInt(fallback.getBodyFontSize()));
            settings.setPrimaryColorHex(node.path("primaryColorHex").asText(fallback.getPrimaryColorHex()));
            settings.setShowVenue(node.path("showVenue").asBoolean(fallback.isShowVenue()));
            settings.setShowSchedule(node.path("showSchedule").asBoolean(fallback.isShowSchedule()));
            settings.setShowIssuedDate(node.path("showIssuedDate").asBoolean(fallback.isShowIssuedDate()));
            return settings;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Map<String, Object> certificateData(final Conference conference, final User user, final String attendeeName) {
        final Map<String, Object> participant = new LinkedHashMap<>();
        participant.put("displayName", attendeeName); participant.put("firstName", value(user.getFirstName()));
        participant.put("lastName", value(user.getLastName())); participant.put("email", value(user.getEmail()));
        participant.put("username", value(user.getUsername())); participant.put("uuid", value(user.getUuid()));
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", value(conference.getName())); event.put("displayName", value(conference.getName()));
        event.put("friendlyId", value(conference.getFriendlyId())); event.put("uuid", value(conference.getUuid()));
        event.put("date", value(conference.getEventDate())); event.put("startTime", value(conference.getStartTime()));
        event.put("endTime", value(conference.getEndTime())); event.put("venue", value(conference.getVenue()));
        event.put("timezone", value(conference.getTimezoneId()));
        final Map<String, Object> platform = new LinkedHashMap<>();
        platform.put("name", platformData.name()); platform.put("website", platformData.website());
        platform.put("email", platformData.email()); platform.put("github", platformData.github());
        platform.put("linkedin", platformData.linkedin()); platform.put("telegram", platformData.telegram());
        return Map.of("participant", participant, "event", event, "platform", platform,
                "certificate", Map.of("issuedDate", java.time.LocalDate.now().toString(), "id", UUID.randomUUID().toString()));
    }

    private static String value(final Object value) { return value == null ? "" : String.valueOf(value); }

    private String buildFullName(final User user) {
        final String first = user.getFirstName();
        final String last = user.getLastName();
        if ((first == null || first.isBlank()) && (last == null || last.isBlank())) return null;
        return ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
    }

    /** US Letter (612x792pt) rotated to landscape. */
    private static final PDRectangle LETTER_LANDSCAPE = new PDRectangle(792, 612);

    private byte[] renderPdf(final Conference conference, final String attendeeName,
                              final boolean profileIncomplete, final CertificateSettings settings) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            final PDPage page = new PDPage(LETTER_LANDSCAPE);
            doc.addPage(page);
            final float width = page.getMediaBox().getWidth();
            float y = page.getMediaBox().getHeight() - 130;

            final var fonts = resolveFonts(settings.getFontFamily());
            final PDFont titleFont = fonts[0];
            final PDFont bodyFont = fonts[1];
            final PDFont italicFont = fonts[2];
            final PDColor textColor = parseColor(settings.getPrimaryColorHex());
            final float titleSize = settings.getTitleFontSize();
            final float bodySize = settings.getBodyFontSize();

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawLogo(doc, cs, page, settings.getLogoBase64());
                cs.setNonStrokingColor(textColor);

                centerText(cs, titleFont, titleSize, "Certificado de Asistencia", width, y);
                y -= titleSize + 27;
                centerText(cs, bodyFont, bodySize, "Se otorga el presente certificado a", width, y);
                y -= bodySize + 24;
                centerText(cs, titleFont, titleSize * 0.78f, attendeeName, width, y);
                y -= titleSize * 0.78f + 22;
                centerText(cs, bodyFont, bodySize, "por su asistencia a la conferencia", width, y);
                y -= bodySize + 20;
                centerText(cs, titleFont, titleSize * 0.68f, conference.getName(), width, y);

                if (settings.isShowSchedule()) {
                    final String schedule = buildSchedule(conference);
                    if (schedule != null) {
                        y -= bodySize + 14;
                        centerText(cs, bodyFont, bodySize * 0.85f, schedule, width, y);
                    }
                }
                if (settings.isShowVenue() && conference.getVenue() != null && !conference.getVenue().isBlank()) {
                    y -= bodySize + 8;
                    centerText(cs, bodyFont, bodySize * 0.85f, "Sede: " + conference.getVenue(), width, y);
                }

                if (settings.isShowIssuedDate()) {
                    y -= bodySize + 22;
                    final String issuedAt = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    centerText(cs, bodyFont, bodySize * 0.78f, "Fecha de emisión: " + issuedAt, width, y);
                }

                if (profileIncomplete) {
                    y -= bodySize + 26;
                    centerText(cs, italicFont, bodySize * 0.7f,
                            "Si quieres que este certificado tenga tus datos, complétalos en tu perfil.",
                            width, y);
                }
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private PDFont[] resolveFonts(final String fontFamily) {
        return switch (fontFamily) {
            case "TIMES_ROMAN" -> new PDFont[] { PDType1Font.TIMES_BOLD, PDType1Font.TIMES_ROMAN, PDType1Font.TIMES_ITALIC };
            case "COURIER" -> new PDFont[] { PDType1Font.COURIER_BOLD, PDType1Font.COURIER, PDType1Font.COURIER_OBLIQUE };
            default -> new PDFont[] { PDType1Font.HELVETICA_BOLD, PDType1Font.HELVETICA, PDType1Font.HELVETICA_OBLIQUE };
        };
    }

    private PDColor parseColor(final String hex) {
        try {
            final int rgb = Integer.parseInt(hex.replace("#", ""), 16);
            final float r = ((rgb >> 16) & 0xFF) / 255f;
            final float g = ((rgb >> 8) & 0xFF) / 255f;
            final float b = (rgb & 0xFF) / 255f;
            return new PDColor(new float[] { r, g, b }, PDDeviceRGB.INSTANCE);
        } catch (final Exception e) {
            return new PDColor(new float[] { 0.12f, 0.11f, 0.29f }, PDDeviceRGB.INSTANCE);
        }
    }

    private void drawLogo(final PDDocument doc, final PDPageContentStream cs, final PDPage page,
                           final String logoBase64) throws IOException {
        if (logoBase64 == null || logoBase64.isBlank()) return;
        try {
            final String raw = logoBase64.contains(",") ? logoBase64.substring(logoBase64.indexOf(',') + 1) : logoBase64;
            final byte[] bytes = Base64.getDecoder().decode(raw);
            final PDImageXObject image = PDImageXObject.createFromByteArray(doc, bytes, "logo");
            final float maxHeight = 50;
            final float scale = maxHeight / image.getHeight();
            final float w = image.getWidth() * scale;
            final float pageWidth = page.getMediaBox().getWidth();
            final float pageHeight = page.getMediaBox().getHeight();
            cs.drawImage(image, (pageWidth - w) / 2, pageHeight - maxHeight - 30, w, maxHeight);
        } catch (final Exception e) {
            // best-effort: a broken/invalid logo must not prevent certificate generation
        }
    }

    private String buildSchedule(final Conference conference) {
        final boolean hasDate = conference.getEventDate() != null && !conference.getEventDate().isBlank();
        final boolean hasStart = conference.getStartTime() != null && !conference.getStartTime().isBlank();
        final boolean hasEnd = conference.getEndTime() != null && !conference.getEndTime().isBlank();
        if (!hasDate && !hasStart && !hasEnd) return null;
        final StringBuilder sb = new StringBuilder("Fecha del evento: ");
        if (hasDate) sb.append(conference.getEventDate());
        if (hasStart || hasEnd) {
            sb.append(" (");
            if (hasStart) sb.append(conference.getStartTime());
            if (hasStart && hasEnd) sb.append(" - ");
            if (hasEnd) sb.append(conference.getEndTime());
            sb.append(")");
        }
        return sb.toString();
    }

    private void centerText(final PDPageContentStream cs, final PDFont font, final float size,
                             final String text, final float pageWidth, final float y) throws IOException {
        final float textWidth = font.getStringWidth(text) / 1000 * size;
        final float x = (pageWidth - textWidth) / 2;
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }
}

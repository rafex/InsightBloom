package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.CertificateSettings;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.CertificateSettingsRepository;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SurveyPort;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
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

/**
 * Generates an attendance certificate PDF in-memory. The PDF is never persisted to disk;
 * each download is regenerated on demand (ephemeral by design).
 */
public class GenerateCertificateUseCase {
    private final ConferenceRepository conferenceRepository;
    private final UserRepository userRepository;
    private final SurveyPort surveyPort;
    private final CertificateSettingsRepository certificateSettingsRepository;

    public GenerateCertificateUseCase(final ConferenceRepository conferenceRepository,
                                       final UserRepository userRepository,
                                       final SurveyPort surveyPort,
                                       final CertificateSettingsRepository certificateSettingsRepository) {
        this.conferenceRepository = conferenceRepository;
        this.userRepository = userRepository;
        this.surveyPort = surveyPort;
        this.certificateSettingsRepository = certificateSettingsRepository;
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
            final byte[] pdf = renderPdf(conference, attendeeName, profileIncomplete, settings);
            final String fileName = "certificado-" + conference.getFriendlyId() + ".pdf";
            return new Result(pdf, fileName, profileIncomplete);
        } catch (final IOException e) {
            throw new RuntimeException("certificate_generation_failed", e);
        }
    }

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

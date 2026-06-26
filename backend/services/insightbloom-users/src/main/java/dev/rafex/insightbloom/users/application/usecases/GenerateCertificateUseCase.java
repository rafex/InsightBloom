package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SurveyPort;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates an attendance certificate PDF in-memory. The PDF is never persisted to disk;
 * each download is regenerated on demand (ephemeral by design).
 */
public class GenerateCertificateUseCase {
    private final ConferenceRepository conferenceRepository;
    private final UserRepository userRepository;
    private final SurveyPort surveyPort;

    public GenerateCertificateUseCase(final ConferenceRepository conferenceRepository,
                                       final UserRepository userRepository,
                                       final SurveyPort surveyPort) {
        this.conferenceRepository = conferenceRepository;
        this.userRepository = userRepository;
        this.surveyPort = surveyPort;
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

        try {
            final byte[] pdf = renderPdf(conference, attendeeName, profileIncomplete);
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

    private byte[] renderPdf(final Conference conference, final String attendeeName,
                              final boolean profileIncomplete) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            final PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            final float width = page.getMediaBox().getWidth();
            float y = page.getMediaBox().getHeight() - 140;

            final var titleFont = PDType1Font.HELVETICA_BOLD;
            final var bodyFont = PDType1Font.HELVETICA;
            final var italicFont = PDType1Font.HELVETICA_OBLIQUE;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                centerText(cs, titleFont, 26, "Certificado de Asistencia", width, y);
                y -= 60;
                centerText(cs, bodyFont, 14, "Se otorga el presente certificado a", width, y);
                y -= 40;
                centerText(cs, titleFont, 20, attendeeName, width, y);
                y -= 40;
                centerText(cs, bodyFont, 14, "por su asistencia a la conferencia", width, y);
                y -= 36;
                centerText(cs, titleFont, 18, conference.getName(), width, y);

                if (conference.getVenue() != null && !conference.getVenue().isBlank()) {
                    y -= 30;
                    centerText(cs, bodyFont, 12, "Sede: " + conference.getVenue(), width, y);
                }
                if (conference.getEventDate() != null && !conference.getEventDate().isBlank()) {
                    y -= 24;
                    centerText(cs, bodyFont, 12, "Fecha del evento: " + conference.getEventDate(), width, y);
                }

                y -= 40;
                final String issuedAt = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                centerText(cs, bodyFont, 11, "Fecha de emisión: " + issuedAt, width, y);

                if (profileIncomplete) {
                    y -= 50;
                    centerText(cs, italicFont, 10,
                            "Si quieres que este certificado tenga tus datos, complétalos en tu perfil.",
                            width, y);
                }
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private void centerText(final PDPageContentStream cs, final PDType1Font font, final float size,
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

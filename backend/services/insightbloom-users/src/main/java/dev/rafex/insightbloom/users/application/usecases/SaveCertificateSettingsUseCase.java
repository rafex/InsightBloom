package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.CertificateSettings;
import dev.rafex.insightbloom.users.domain.ports.CertificateSettingsRepository;

import java.util.Set;

public class SaveCertificateSettingsUseCase {
    private static final Set<String> ALLOWED_FONTS = Set.of("HELVETICA", "TIMES_ROMAN", "COURIER");

    private final CertificateSettingsRepository repository;

    public SaveCertificateSettingsUseCase(final CertificateSettingsRepository repository) {
        this.repository = repository;
    }

    public record Request(String logoBase64, String fontFamily, Integer titleFontSize, Integer bodyFontSize,
                          String primaryColorHex, Boolean showVenue, Boolean showSchedule, Boolean showIssuedDate) {}

    public CertificateSettings execute(final Request req) {
        final CertificateSettings s = new CertificateSettings();
        s.setLogoBase64(req.logoBase64());
        s.setFontFamily(ALLOWED_FONTS.contains(req.fontFamily()) ? req.fontFamily() : "HELVETICA");
        s.setTitleFontSize(clamp(req.titleFontSize(), 28, 14, 48));
        s.setBodyFontSize(clamp(req.bodyFontSize(), 14, 8, 24));
        s.setPrimaryColorHex(isValidHexColor(req.primaryColorHex()) ? req.primaryColorHex() : "#1e1b4b");
        s.setShowVenue(req.showVenue() == null || req.showVenue());
        s.setShowSchedule(req.showSchedule() == null || req.showSchedule());
        s.setShowIssuedDate(req.showIssuedDate() == null || req.showIssuedDate());
        repository.save(s);
        return s;
    }

    private static int clamp(final Integer value, final int fallback, final int min, final int max) {
        final int v = value != null ? value : fallback;
        return Math.max(min, Math.min(max, v));
    }

    private static boolean isValidHexColor(final String s) {
        return s != null && s.matches("^#[0-9a-fA-F]{6}$");
    }
}

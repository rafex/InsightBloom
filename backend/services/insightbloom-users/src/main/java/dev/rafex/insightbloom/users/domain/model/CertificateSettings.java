package dev.rafex.insightbloom.users.domain.model;

public class CertificateSettings {
    private String logoBase64; // nullable, data without the "data:image/..." prefix
    private String fontFamily; // HELVETICA | TIMES_ROMAN | COURIER
    private int titleFontSize;
    private int bodyFontSize;
    private String primaryColorHex;
    private boolean showVenue;
    private boolean showSchedule;
    private boolean showIssuedDate;

    public static CertificateSettings defaults() {
        final CertificateSettings s = new CertificateSettings();
        s.fontFamily = "HELVETICA";
        s.titleFontSize = 28;
        s.bodyFontSize = 14;
        s.primaryColorHex = "#1e1b4b";
        s.showVenue = true;
        s.showSchedule = true;
        s.showIssuedDate = true;
        return s;
    }

    public String getLogoBase64() { return logoBase64; }
    public String getFontFamily() { return fontFamily; }
    public int getTitleFontSize() { return titleFontSize; }
    public int getBodyFontSize() { return bodyFontSize; }
    public String getPrimaryColorHex() { return primaryColorHex; }
    public boolean isShowVenue() { return showVenue; }
    public boolean isShowSchedule() { return showSchedule; }
    public boolean isShowIssuedDate() { return showIssuedDate; }

    public void setLogoBase64(final String logoBase64) { this.logoBase64 = logoBase64; }
    public void setFontFamily(final String fontFamily) { this.fontFamily = fontFamily; }
    public void setTitleFontSize(final int titleFontSize) { this.titleFontSize = titleFontSize; }
    public void setBodyFontSize(final int bodyFontSize) { this.bodyFontSize = bodyFontSize; }
    public void setPrimaryColorHex(final String primaryColorHex) { this.primaryColorHex = primaryColorHex; }
    public void setShowVenue(final boolean showVenue) { this.showVenue = showVenue; }
    public void setShowSchedule(final boolean showSchedule) { this.showSchedule = showSchedule; }
    public void setShowIssuedDate(final boolean showIssuedDate) { this.showIssuedDate = showIssuedDate; }
}

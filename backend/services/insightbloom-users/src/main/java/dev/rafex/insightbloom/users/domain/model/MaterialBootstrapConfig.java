package dev.rafex.insightbloom.users.domain.model;

/** Configuración inmutable que viaja únicamente de users al Pod de sandbox. */
public record MaterialBootstrapConfig(
        String sourceUrl, String ref, String kind, String source, String value) {
    public boolean enabled() {
        return sourceUrl != null && !sourceUrl.isBlank()
                && kind != null && !kind.isBlank() && source != null && !source.isBlank()
                && value != null && !value.isBlank();
    }
}

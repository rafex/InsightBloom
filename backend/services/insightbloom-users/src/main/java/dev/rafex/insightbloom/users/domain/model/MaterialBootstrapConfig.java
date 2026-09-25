package dev.rafex.insightbloom.users.domain.model;

/** Configuración inmutable que viaja únicamente de users al Pod de sandbox. */
public record MaterialBootstrapConfig(
        String sourceUrl, String ref, String kind, String source, String value, boolean bootstrapEnabled) {
    public boolean hasMaterials() {
        return sourceUrl != null && !sourceUrl.isBlank() && ref != null && !ref.isBlank();
    }

    public boolean enabled() {
        return bootstrapEnabled && kind != null && !kind.isBlank()
                && source != null && !source.isBlank() && value != null && !value.isBlank()
                && (!"material".equals(source) || hasMaterials());
    }

    public boolean configured() {
        return hasMaterials() || enabled();
    }
}

package dev.rafex.insightbloom.users.domain.model;

/** Catálogo de zonas horarias con offset fijo (sin DST) para conferencias. */
public record Timezone(int id, String ianaName, String label, int utcOffsetMinutes, boolean isDefault) {
}

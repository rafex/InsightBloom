package dev.rafex.insightbloom.ingest.domain.services;
import java.text.Normalizer;
import java.util.regex.Pattern;
public class WordNormalizationService {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    // Simple singular/plural fusion: track canonical by highest frequency (here: prefer singular)
    public String normalize(String word) {
        if (word == null) return "";
        String normalized = Normalizer.normalize(word, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        normalized = normalized.toLowerCase().trim();
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized;
    }
    public String canonical(String normalized, String existing) {
        // Singular/plural fusion: prefer the form without trailing 's' if both exist
        if (existing == null) return normalized;
        // If normalized is plural of existing canonical, return singular
        if (normalized.equals(existing + "s") || normalized.equals(existing + "es")) return existing;
        // If existing canonical is plural of normalized, return singular
        if (existing.equals(normalized + "s") || existing.equals(normalized + "es")) return normalized;
        // Otherwise return the existing canonical
        return existing;
    }
}

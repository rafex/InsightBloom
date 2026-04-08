package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import java.text.Normalizer;
import java.util.regex.Pattern;

public class FriendlyIdService {
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9-]");
    private static final Pattern MULTIPLE_DASHES = Pattern.compile("-{2,}");
    private static final Pattern LEADING_TRAILING_DASHES = Pattern.compile("^-|-$");

    private final ConferenceRepository conferenceRepository;

    public FriendlyIdService(ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    public String generate(String name) {
        String slug = toSlug(name);
        if (slug.isEmpty()) {
            slug = "conf";
        }
        // Trim to max 24 chars
        if (slug.length() > 24) {
            slug = slug.substring(0, 24).replaceAll("-$", "");
        }
        // Ensure min 4 chars
        while (slug.length() < 4) {
            slug = slug + "0";
        }

        String candidate = slug;
        int suffix = 2;
        while (conferenceRepository.existsByFriendlyId(candidate)) {
            candidate = slug + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    static String toSlug(String input) {
        if (input == null || input.isBlank()) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String lower = normalized.toLowerCase();
        String dashed = lower.replace(" ", "-");
        String cleaned = NON_ALPHANUMERIC.matcher(dashed).replaceAll("");
        String deduped = MULTIPLE_DASHES.matcher(cleaned).replaceAll("-");
        return LEADING_TRAILING_DASHES.matcher(deduped).replaceAll("");
    }
}

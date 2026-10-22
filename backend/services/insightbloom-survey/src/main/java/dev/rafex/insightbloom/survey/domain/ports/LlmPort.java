package dev.rafex.insightbloom.survey.domain.ports;

public interface LlmPort {
    boolean isEnabled();

    default boolean isEnabled(final String eventId, final String capability) { return isEnabled(); }

    String complete(String systemPrompt, String userPrompt);

    default String complete(final String eventId, final String capability, final String systemPrompt,
                            final String userPrompt) { return complete(systemPrompt, userPrompt); }
}

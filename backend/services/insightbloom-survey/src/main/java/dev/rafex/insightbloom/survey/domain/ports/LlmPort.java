package dev.rafex.insightbloom.survey.domain.ports;

public interface LlmPort {
    boolean isEnabled();

    String complete(String systemPrompt, String userPrompt);
}

package dev.rafex.insightbloom.users.domain.ports;

public interface LlmPort {
    boolean isEnabled();

    String complete(String systemPrompt, String userPrompt);
}

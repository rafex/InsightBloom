package dev.rafex.insightbloom.users.domain.model;

public class PlatformSettings {
    private boolean chatAiEnabled;
    private String chatSystemPrompt; // null = usa el default embebido en chat/bot.py
    private Double chatTemperature;  // null = usa el default embebido en chat/bot.py

    public static PlatformSettings defaults() {
        final PlatformSettings s = new PlatformSettings();
        s.chatAiEnabled = true;
        return s;
    }

    public boolean isChatAiEnabled() { return chatAiEnabled; }
    public void setChatAiEnabled(final boolean chatAiEnabled) { this.chatAiEnabled = chatAiEnabled; }

    public String getChatSystemPrompt() { return chatSystemPrompt; }
    public void setChatSystemPrompt(final String chatSystemPrompt) { this.chatSystemPrompt = chatSystemPrompt; }

    public Double getChatTemperature() { return chatTemperature; }
    public void setChatTemperature(final Double chatTemperature) { this.chatTemperature = chatTemperature; }
}

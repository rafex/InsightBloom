package dev.rafex.insightbloom.users.domain.model;

public class PlatformSettings {
    private boolean chatAiEnabled;

    public static PlatformSettings defaults() {
        final PlatformSettings s = new PlatformSettings();
        s.chatAiEnabled = true;
        return s;
    }

    public boolean isChatAiEnabled() { return chatAiEnabled; }
    public void setChatAiEnabled(final boolean chatAiEnabled) { this.chatAiEnabled = chatAiEnabled; }
}

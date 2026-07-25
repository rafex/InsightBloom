package dev.rafex.insightbloom.users.domain.model;

public class PlatformSettings {
    private boolean chatAiEnabled;
    private String chatSystemPrompt; // null = usa el default embebido en chat/bot.py
    private Double chatTemperature;  // null = usa el default embebido en chat/bot.py
    private String aiBaseUrl;
    private String aiModel;
    /** Plaintext only in memory; never serialize this field in a public/admin view. */
    private String aiApiKey;
    // Umbrales de PlatformDeviceGuard -- nullable, defaults efectivos en el guard si el admin de
    // plataforma no los configuro todavia (ver DEFAULT_* en PlatformDeviceGuard).
    private Integer maxAccountsPerDevice;
    private Integer maxSessionsPerUser;
    private Integer maxRegistrationsPerDevicePerDay;

    public static PlatformSettings defaults() {
        final PlatformSettings s = new PlatformSettings();
        s.chatAiEnabled = false;
        s.aiBaseUrl = "https://api.groq.com/openai/v1";
        s.aiModel = "openai/gpt-oss-120b";
        return s;
    }

    public boolean isChatAiEnabled() { return chatAiEnabled; }
    public void setChatAiEnabled(final boolean chatAiEnabled) { this.chatAiEnabled = chatAiEnabled; }

    public String getChatSystemPrompt() { return chatSystemPrompt; }
    public void setChatSystemPrompt(final String chatSystemPrompt) { this.chatSystemPrompt = chatSystemPrompt; }

    public Double getChatTemperature() { return chatTemperature; }
    public void setChatTemperature(final Double chatTemperature) { this.chatTemperature = chatTemperature; }

    public String getAiBaseUrl() { return aiBaseUrl; }
    public void setAiBaseUrl(final String aiBaseUrl) { this.aiBaseUrl = aiBaseUrl; }

    public String getAiModel() { return aiModel; }
    public void setAiModel(final String aiModel) { this.aiModel = aiModel; }

    public String getAiApiKey() { return aiApiKey; }
    public void setAiApiKey(final String aiApiKey) { this.aiApiKey = aiApiKey; }

    public Integer getMaxAccountsPerDevice() { return maxAccountsPerDevice; }
    public void setMaxAccountsPerDevice(final Integer maxAccountsPerDevice) { this.maxAccountsPerDevice = maxAccountsPerDevice; }

    public Integer getMaxSessionsPerUser() { return maxSessionsPerUser; }
    public void setMaxSessionsPerUser(final Integer maxSessionsPerUser) { this.maxSessionsPerUser = maxSessionsPerUser; }

    public Integer getMaxRegistrationsPerDevicePerDay() { return maxRegistrationsPerDevicePerDay; }
    public void setMaxRegistrationsPerDevicePerDay(final Integer maxRegistrationsPerDevicePerDay) {
        this.maxRegistrationsPerDevicePerDay = maxRegistrationsPerDevicePerDay;
    }
}

package dev.rafex.insightbloom.users.domain.model;

public class PlatformSettings {
    private boolean chatAiEnabled;
    private String chatSystemPrompt; // null = usa el default embebido en chat/bot.py
    private Double chatTemperature;  // null = usa el default embebido en chat/bot.py
    // Umbrales de PlatformDeviceGuard -- nullable, defaults efectivos en el guard si el admin de
    // plataforma no los configuro todavia (ver DEFAULT_* en PlatformDeviceGuard).
    private Integer maxAccountsPerDevice;
    private Integer maxSessionsPerUser;
    private Integer maxRegistrationsPerDevicePerDay;

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

    public Integer getMaxAccountsPerDevice() { return maxAccountsPerDevice; }
    public void setMaxAccountsPerDevice(final Integer maxAccountsPerDevice) { this.maxAccountsPerDevice = maxAccountsPerDevice; }

    public Integer getMaxSessionsPerUser() { return maxSessionsPerUser; }
    public void setMaxSessionsPerUser(final Integer maxSessionsPerUser) { this.maxSessionsPerUser = maxSessionsPerUser; }

    public Integer getMaxRegistrationsPerDevicePerDay() { return maxRegistrationsPerDevicePerDay; }
    public void setMaxRegistrationsPerDevicePerDay(final Integer maxRegistrationsPerDevicePerDay) {
        this.maxRegistrationsPerDevicePerDay = maxRegistrationsPerDevicePerDay;
    }
}

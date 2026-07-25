package dev.rafex.insightbloom.users.domain.model;

public class PlatformSettings {
    private AiProviderSettings chatAi = AiProviderSettings.defaults(true);
    private AiProviderSettings tutorAi = AiProviderSettings.defaults(false);
    private AiProviderSettings surveyAi = AiProviderSettings.defaults(false);
    private AiProviderSettings seatLayoutAi = AiProviderSettings.defaults(false);
    // Umbrales de PlatformDeviceGuard -- nullable, defaults efectivos en el guard si el admin de
    // plataforma no los configuro todavia (ver DEFAULT_* en PlatformDeviceGuard).
    private Integer maxAccountsPerDevice;
    private Integer maxSessionsPerUser;
    private Integer maxRegistrationsPerDevicePerDay;

    public static PlatformSettings defaults() {
        final PlatformSettings s = new PlatformSettings();
        return s;
    }

    public AiProviderSettings getChatAi() { return chatAi; }
    public void setChatAi(final AiProviderSettings value) { this.chatAi = value; }
    public AiProviderSettings getTutorAi() { return tutorAi; }
    public void setTutorAi(final AiProviderSettings value) { this.tutorAi = value; }
    public AiProviderSettings getSurveyAi() { return surveyAi; }
    public void setSurveyAi(final AiProviderSettings value) { this.surveyAi = value; }
    public AiProviderSettings getSeatLayoutAi() { return seatLayoutAi; }
    public void setSeatLayoutAi(final AiProviderSettings value) { this.seatLayoutAi = value; }

    // Compatibilidad con los casos de uso y endpoints de Chat existentes.
    public boolean isChatAiEnabled() { return chatAi.isEnabled(); }
    public void setChatAiEnabled(final boolean enabled) { chatAi.setEnabled(enabled); }

    public String getChatSystemPrompt() { return chatAi.getSystemPrompt(); }
    public void setChatSystemPrompt(final String prompt) { chatAi.setSystemPrompt(prompt); }

    public Double getChatTemperature() { return chatAi.getTemperature(); }
    public void setChatTemperature(final Double temperature) { chatAi.setTemperature(temperature); }

    public String getAiBaseUrl() { return chatAi.getBaseUrl(); }
    public void setAiBaseUrl(final String baseUrl) { chatAi.setBaseUrl(baseUrl); }

    public String getAiModel() { return chatAi.getModel(); }
    public void setAiModel(final String model) { chatAi.setModel(model); }

    public String getAiApiKey() { return chatAi.getApiKey(); }
    public void setAiApiKey(final String apiKey) { chatAi.setApiKey(apiKey); }

    public Integer getMaxAccountsPerDevice() { return maxAccountsPerDevice; }
    public void setMaxAccountsPerDevice(final Integer maxAccountsPerDevice) { this.maxAccountsPerDevice = maxAccountsPerDevice; }

    public Integer getMaxSessionsPerUser() { return maxSessionsPerUser; }
    public void setMaxSessionsPerUser(final Integer maxSessionsPerUser) { this.maxSessionsPerUser = maxSessionsPerUser; }

    public Integer getMaxRegistrationsPerDevicePerDay() { return maxRegistrationsPerDevicePerDay; }
    public void setMaxRegistrationsPerDevicePerDay(final Integer maxRegistrationsPerDevicePerDay) {
        this.maxRegistrationsPerDevicePerDay = maxRegistrationsPerDevicePerDay;
    }
}

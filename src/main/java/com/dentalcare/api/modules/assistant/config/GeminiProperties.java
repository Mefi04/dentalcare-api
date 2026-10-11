package com.dentalcare.api.modules.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "dentalcare.gemini")
public class GeminiProperties {
    private boolean enabled = true;
    private String apiKey = "";
    private String model = "gemini-1.5-flash";
    private int maxOutputTokens = 1000;
    private Duration requestTimeout = Duration.ofSeconds(10);
    private int dailyRequestLimit = 1000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey.trim(); }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model == null || model.isBlank() ? "gemini-1.5-flash" : model.trim(); }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public int getDailyRequestLimit() { return dailyRequestLimit; }
    public void setDailyRequestLimit(int dailyRequestLimit) { this.dailyRequestLimit = dailyRequestLimit; }
}

package com.dentalcare.api.config.drive;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "dentalcare.google-drive")
public class GoogleDriveProperties {

    private boolean enabled;
    private String folderId = "";
    private String clientId = "";
    private String clientSecret = "";
    private String refreshToken = "";
    private String applicationName = "DentalCare API";

    public void validateRequiredWhenEnabled() {
        if (!enabled) {
            return;
        }

        List<String> missing = new ArrayList<>();
        addIfBlank(missing, "GOOGLE_DRIVE_FOLDER_ID", folderId);
        addIfBlank(missing, "GOOGLE_DRIVE_CLIENT_ID", clientId);
        addIfBlank(missing, "GOOGLE_DRIVE_CLIENT_SECRET", clientSecret);
        addIfBlank(missing, "GOOGLE_DRIVE_REFRESH_TOKEN", refreshToken);

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Google Drive is enabled but required configuration is missing: "
                            + String.join(", ", missing)
            );
        }
    }

    private static void addIfBlank(List<String> missing, String variableName, String value) {
        if (!StringUtils.hasText(value)) {
            missing.add(variableName);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
}

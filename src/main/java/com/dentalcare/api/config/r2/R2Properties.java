package com.dentalcare.api.config.r2;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "dentalcare.r2")
public class R2Properties {

    private boolean enabled;
    private String accountId = "";
    private String bucket = "dentalcare-expedientes";
    private String endpoint = "";
    private String accessKeyId = "";
    private String secretAccessKey = "";
    private Duration apiCallTimeout = Duration.ofSeconds(30);
    private Duration apiCallAttemptTimeout = Duration.ofSeconds(15);

    public void validateRequiredWhenEnabled() {
        if (!enabled) {
            return;
        }

        List<String> missing = new ArrayList<>();
        addIfBlank(missing, "R2_ACCOUNT_ID", accountId);
        addIfBlank(missing, "R2_BUCKET", bucket);
        addIfBlank(missing, "R2_ENDPOINT", endpoint);
        addIfBlank(missing, "R2_ACCESS_KEY_ID", accessKeyId);
        addIfBlank(missing, "R2_SECRET_ACCESS_KEY", secretAccessKey);

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Cloudflare R2 is enabled but required configuration is missing: "
                            + String.join(", ", missing)
            );
        }

        validateEndpoint();
        if (apiCallTimeout == null || apiCallTimeout.isZero() || apiCallTimeout.isNegative()
                || apiCallAttemptTimeout == null || apiCallAttemptTimeout.isZero() || apiCallAttemptTimeout.isNegative()
                || apiCallAttemptTimeout.compareTo(apiCallTimeout) > 0) {
            throw new IllegalStateException("R2 timeouts must be positive and attempt timeout must not exceed call timeout");
        }
    }

    private void validateEndpoint() {
        URI endpointUri;
        try {
            endpointUri = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("R2_ENDPOINT must be a valid HTTPS URI", exception);
        }

        if (!"https".equalsIgnoreCase(endpointUri.getScheme()) || !StringUtils.hasText(endpointUri.getHost())) {
            throw new IllegalStateException("R2_ENDPOINT must be a valid HTTPS URI");
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

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public Duration getApiCallTimeout() { return apiCallTimeout; }
    public void setApiCallTimeout(Duration apiCallTimeout) { this.apiCallTimeout = apiCallTimeout; }
    public Duration getApiCallAttemptTimeout() { return apiCallAttemptTimeout; }
    public void setApiCallAttemptTimeout(Duration apiCallAttemptTimeout) { this.apiCallAttemptTimeout = apiCallAttemptTimeout; }
}

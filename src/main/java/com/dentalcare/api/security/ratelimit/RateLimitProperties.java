package com.dentalcare.api.security.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "dentalcare.security.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private List<String> trustedProxies = new ArrayList<>();
    @Valid private Rule login = new Rule(Duration.ofMinutes(5), 30, 10);
    @Valid private Rule passwordRecovery = new Rule(Duration.ofHours(1), 15, 5);
    @Valid private Rule publicContact = new Rule(Duration.ofMinutes(15), 10, 0);
    @Valid private Rule publicAppointmentRequest = new Rule(Duration.ofMinutes(15), 5, 0);
    @Valid private Rule apiRead = new Rule(Duration.ofMinutes(1), 600, 300);
    @Valid private Rule apiWrite = new Rule(Duration.ofMinutes(1), 100, 50);
    @Valid private Rule reports = new Rule(Duration.ofMinutes(1), 30, 15);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(List<String> trustedProxies) { this.trustedProxies = trustedProxies == null ? new ArrayList<>() : trustedProxies; }
    public Rule getLogin() { return login; }
    public void setLogin(Rule login) { this.login = login; }
    public Rule getPasswordRecovery() { return passwordRecovery; }
    public void setPasswordRecovery(Rule passwordRecovery) { this.passwordRecovery = passwordRecovery; }
    public Rule getPublicContact() { return publicContact; }
    public void setPublicContact(Rule publicContact) { this.publicContact = publicContact; }
    public Rule getPublicAppointmentRequest() { return publicAppointmentRequest; }
    public void setPublicAppointmentRequest(Rule value) { this.publicAppointmentRequest = value; }
    public Rule getApiRead() { return apiRead; }
    public void setApiRead(Rule apiRead) { this.apiRead = apiRead; }
    public Rule getApiWrite() { return apiWrite; }
    public void setApiWrite(Rule apiWrite) { this.apiWrite = apiWrite; }
    public Rule getReports() { return reports; }
    public void setReports(Rule reports) { this.reports = reports; }

    public Rule rule(RateLimitPolicy policy) {
        return switch (policy) {
            case LOGIN -> login;
            case PASSWORD_RECOVERY -> passwordRecovery;
            case PUBLIC_CONTACT -> publicContact;
            case PUBLIC_APPOINTMENT_REQUEST -> publicAppointmentRequest;
            case API_READ -> apiRead;
            case API_WRITE -> apiWrite;
            case REPORTS -> reports;
        };
    }

    public static class Rule {
        @NotNull private Duration window;
        @Positive private int ipLimit;
        private int identityLimit;

        public Rule() { }
        public Rule(Duration window, int ipLimit, int identityLimit) {
            this.window = window;
            this.ipLimit = ipLimit;
            this.identityLimit = identityLimit;
        }
        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }
        public int getIpLimit() { return ipLimit; }
        public void setIpLimit(int ipLimit) { this.ipLimit = ipLimit; }
        public int getIdentityLimit() { return identityLimit; }
        public void setIdentityLimit(int identityLimit) {
            if (identityLimit < 0) throw new IllegalArgumentException("identityLimit cannot be negative");
            this.identityLimit = identityLimit;
        }

        @AssertTrue(message = "rate-limit window must be greater than zero")
        public boolean isWindowPositive() {
            return window != null && !window.isZero() && !window.isNegative();
        }
    }
}

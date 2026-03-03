package com.example.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security.login-protection")
public class LoginProtectionProperties {

    private boolean enabled = true;
    private int maxRequestsPerWindow = 20;
    private int rateLimitWindowSeconds = 60;
    private int maxFailedAttempts = 5;
    private int failedAttemptWindowSeconds = 300;
    private int blockDurationSeconds = 900;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxRequestsPerWindow() {
        return maxRequestsPerWindow;
    }

    public void setMaxRequestsPerWindow(int maxRequestsPerWindow) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
    }

    public int getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    public void setRateLimitWindowSeconds(int rateLimitWindowSeconds) {
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public int getFailedAttemptWindowSeconds() {
        return failedAttemptWindowSeconds;
    }

    public void setFailedAttemptWindowSeconds(int failedAttemptWindowSeconds) {
        this.failedAttemptWindowSeconds = failedAttemptWindowSeconds;
    }

    public int getBlockDurationSeconds() {
        return blockDurationSeconds;
    }

    public void setBlockDurationSeconds(int blockDurationSeconds) {
        this.blockDurationSeconds = blockDurationSeconds;
    }
}

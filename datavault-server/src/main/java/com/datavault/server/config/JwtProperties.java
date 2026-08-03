package com.datavault.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "datavault.jwt")
public class JwtProperties {

    /**
     * Secret key for signing JWT tokens (HMAC SHA256 requires at least 256 bits / 32 chars)
     */
    private String secret = "DataVaultSecretKeyMustBeAtLeast32BytesLongForSecurity!!";

    /**
     * Expiration duration in milliseconds (default 24 hours)
     */
    private long expirationMs = 86400000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}

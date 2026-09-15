package com.showtime.catalog.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Catalog only ever validates tokens — it never issues them. The signing key
 * must match Identity's {@code showtime.jwt.signing-key} exactly; in
 * production this would come from a shared secret store, not a copy-pasted
 * environment variable, but at learning scale both services read the same
 * generated-locally value (see .env.example).
 */
@ConfigurationProperties(prefix = "showtime.jwt")
public class JwtProperties {

    private String signingKey;
    private String issuer = "showtime-identity";

    public String getSigningKey() {
        return signingKey;
    }

    public void setSigningKey(String signingKey) {
        this.signingKey = signingKey;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}

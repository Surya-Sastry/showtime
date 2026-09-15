package com.showtime.booking.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Booking only validates tokens issued by Identity; it never issues its own. */
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

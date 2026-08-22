package com.teramera.backend.auth;

import com.teramera.backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

@Component
public class HttpGoogleVerifier implements GoogleVerifier {

    private static final Logger log = LoggerFactory.getLogger(HttpGoogleVerifier.class);
    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final String clientId;

    public HttpGoogleVerifier(AppProperties props) {
        this.clientId = props.googleClientId();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> verify(String idToken) {
        Map<String, Object> claims;
        try {
            claims = RestClient.create()
                    .get()
                    .uri(TOKENINFO_URL + idToken)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.debug("Google tokeninfo call failed: {}", e.getMessage());
            return null;
        }
        if (claims == null) return null;

        if (!"true".equals(String.valueOf(claims.get("email_verified")))) return null;
        if (clientId != null && !clientId.isBlank() && !clientId.equals(claims.get("aud"))) return null;
        try {
            long exp = Long.parseLong(String.valueOf(claims.get("exp")));
            if (exp < Instant.now().getEpochSecond()) return null;
        } catch (NumberFormatException e) {
            return null;
        }
        return claims;
    }
}

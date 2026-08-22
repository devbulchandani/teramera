package com.teramera.backend.auth;

import java.util.Map;

/**
 * Verifies Google ID tokens without pulling in the heavy Google client:
 * calls the official tokeninfo endpoint and checks audience + expiry.
 */
public interface GoogleVerifier {

    /** @return claims (sub, email, email_verified, name, picture) or null when invalid. */
    Map<String, Object> verify(String idToken);
}

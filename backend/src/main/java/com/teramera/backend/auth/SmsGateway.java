package com.teramera.backend.auth;

/** Sends OTP SMS messages. Implement with a real provider (Twilio, MSG91, etc.) for production. */
public interface SmsGateway {

    void sendOtp(String phoneE164, String code);
}

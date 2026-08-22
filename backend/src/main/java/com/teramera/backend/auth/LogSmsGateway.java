package com.teramera.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Dev gateway: logs the OTP instead of sending an SMS. Swap for Twilio/Messages7 in production. */
@Component
public class LogSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(LogSmsGateway.class);

    @Override
    public void sendOtp(String phoneE164, String code) {
        log.info("[SMS to {}] Your teramera code is {}", phoneE164, code);
    }
}

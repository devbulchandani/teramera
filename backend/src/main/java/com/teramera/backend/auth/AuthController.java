package com.teramera.backend.auth;

import com.teramera.backend.config.AppProperties;
import com.teramera.backend.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    public record OtpRequest(@NotBlank String phone) {}
    public record OtpVerifyRequest(@NotBlank String requestId, @NotBlank String code) {}
    public record GoogleLoginRequest(@NotBlank String idToken) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}

    private final OtpService otpService;
    private final SmsGateway smsGateway;
    private final TokenService tokenService;
    private final UserRepository users;
    private final GoogleVerifier googleVerifier;
    private final AppProperties props;

    public AuthController(
            OtpService otpService,
            SmsGateway smsGateway,
            TokenService tokenService,
            UserRepository users,
            GoogleVerifier googleVerifier,
            AppProperties props
    ) {
        this.otpService = otpService;
        this.smsGateway = smsGateway;
        this.tokenService = tokenService;
        this.users = users;
        this.googleVerifier = googleVerifier;
        this.props = props;
    }

    @PostMapping("/otp/request")
    public Map<String, Object> requestOtp(@Valid @RequestBody OtpRequest request) {
        String phone = normalizePhone(request.phone());
        if (phone == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone must be in E.164 format, e.g. +919876543210");
        }
        OtpService.IssuedOtp issued = otpService.issue(phone);
        smsGateway.sendOtp(phone, issued.code());

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("requestId", issued.requestId());
        response.put("expiresInSeconds", 300);
        // Dev convenience only — must be disabled (expose-dev-otp: false) in production.
        if (props.exposeDevOtp()) {
            response.put("devCode", issued.code());
        }
        return response;
    }

    @PostMapping("/otp/verify")
    public TokenService.AuthTokens verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        otpService.verify(request.requestId(), request.code());
        String phone = otpService.phoneOf(request.requestId());
        var user = users.byPhone(phone).orElseGet(() -> users.createPhoneUser(phone));
        return tokenService.issueFor(user);
    }

    @PostMapping("/google")
    public TokenService.AuthTokens googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        Map<String, Object> claims = googleVerifier.verify(request.idToken());
        if (claims == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google ID token");
        }
        String email = (String) claims.get("email");
        var user = users.byEmail(email).orElseGet(() ->
                users.createGoogleUser(email, (String) claims.get("name"), (String) claims.get("picture")));
        return tokenService.issueFor(user);
    }

    @PostMapping("/refresh")
    public TokenService.AuthTokens refresh(@Valid @RequestBody RefreshRequest request) {
        return tokenService.rotate(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        tokenService.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** Current user profile; Authorization header required outside /auth/**. */
    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader("X-User-Id") String userId) {
        var user = users.byId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return Map.of(
                "id", user.id(),
                "phone", user.phone() == null ? "" : user.phone(),
                "email", user.email() == null ? "" : user.email(),
                "name", user.name() == null ? "" : user.name()
        );
    }

    static String normalizePhone(String raw) {
        String cleaned = raw.replaceAll("[\\s()-]", "");
        if (cleaned.startsWith("+")) {
            cleaned = "+" + cleaned.substring(1).replaceAll("\\D", "");
        } else {
            return null;
        }
        return cleaned.matches("\\+\\d{8,15}") ? cleaned : null;
    }
}

package com.teramera.backend.auth;

import com.teramera.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


/**
 * Issues and rotates credential pairs for any login method (phone OTP, Google).
 */
@Service
public class TokenService {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;

    public TokenService(JwtService jwtService, RefreshTokenRepository refreshTokens, UserRepository users) {
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.users = users;
    }

    public record AuthTokens(
            String accessToken,
            String refreshToken,
            String userId,
            String phone,
            String email,
            String name
    ) {}

    public AuthTokens issueFor(UserRepository.User user) {
        String access = jwtService.issueAccessToken(user.id());
        var refresh = jwtService.issueRefreshToken();
        refreshTokens.save(user.id(), refresh);
        return new AuthTokens(access, refresh.token(), user.id(), user.phone(), user.email(), user.name());
    }

    /** Rotates a refresh token: old one is revoked, a fresh pair is returned. */
    public AuthTokens rotate(String rawRefreshToken) {
        RefreshTokenRepository.ActiveToken active = refreshTokens.findActiveByRawToken(rawRefreshToken);
        if (active == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        refreshTokens.revoke(active.id());
        return issueById(active.userId());
    }

    public void revoke(String rawRefreshToken) {
        RefreshTokenRepository.ActiveToken active = refreshTokens.findActiveByRawToken(rawRefreshToken);
        if (active != null) {
            refreshTokens.revoke(active.id());
        }
    }

    private AuthTokens issueById(String userId) {
        return users.byId(userId)
                .map(this::issueFor)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }
}

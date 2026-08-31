package com.chatter.chatter.user.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Mints and verifies the JWTs that authenticate every request and every STOMP
 * connection.
 *
 * <p>Tokens are self-contained and HMAC-signed: they carry the user id and
 * username, so an authenticated request costs no database read. The trade is
 * that a token cannot be revoked before it expires — keep
 * {@code jwt.expiration-ms} short enough to live with that.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    /**
     * Derives the signing key once at startup, so the HMAC key is not rebuilt
     * per token.
     *
     * <p>{@code jwt.secret} must be at least 256 bits — {@code Keys.hmacShaKeyFor}
     * rejects anything shorter, which is why the application fails to start
     * rather than run with a weak key.
     */
    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                             @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Issues a token for a user who has just proven who they are.
     *
     * <p>Used by {@code AuthController} on both register and login, and nowhere
     * else — nothing mints a token without a password check first.
     *
     * <p>The user id rides in a {@code uid} claim rather than the subject: the
     * subject holds the username, because STOMP resolves {@code /user/...}
     * destinations by name.
     */
    public String generateToken(UUID userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies a token's signature and expiry, and unpacks the principal.
     *
     * <p>The verification point for the whole application: used by
     * {@link JwtAuthenticationFilter} for HTTP requests and by
     * {@code StompAuthChannelInterceptor} on the WebSocket CONNECT frame.
     *
     * <p>Every failure collapses to {@code null} rather than an exception,
     * because both callers treat "no valid token" identically and neither
     * should leak to the client why a token was rejected.
     *
     * @return the principal carried by the token, or {@code null} if the token
     *         is malformed, tampered with, or expired
     */
    public AuthenticatedUser parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return new AuthenticatedUser(UUID.fromString(claims.get("uid", String.class)), claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}

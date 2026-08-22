package com.internship.crm.auth.token;

import com.internship.crm.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private static final String ROLE_CLAIM = "role";
    private static final String USERNAME_CLAIM = "username";

    private final SecretKey signingKey;
    private final Duration expiration;
    private final Clock clock;

    @Autowired
    public JwtTokenService(
            @Value("${security.jwt.secret}") String base64Secret,
            @Value("${security.jwt.expiration-minutes}") long expirationMinutes) {
        this(base64Secret, expirationMinutes, Clock.systemUTC());
    }

    JwtTokenService(String base64Secret, long expirationMinutes, Clock clock) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must contain a Base64-encoded key of at least 32 bytes");
        }
        if (expirationMinutes <= 0) {
            throw new IllegalStateException("JWT expiration must be greater than zero minutes");
        }
        try {
            this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET must contain a valid Base64-encoded key of at least 32 bytes",
                    exception);
        }
        this.expiration = Duration.ofMinutes(expirationMinutes);
        this.clock = clock;
    }

    public String issueToken(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(expiration);
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(USERNAME_CLAIM, user.getUsername())
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long expirationSeconds() {
        return expiration.toSeconds();
    }
}

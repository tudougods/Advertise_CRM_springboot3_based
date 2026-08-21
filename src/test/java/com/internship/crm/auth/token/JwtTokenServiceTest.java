package com.internship.crm.auth.token;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.domain.User;
import com.internship.crm.user.domain.UserRole;
import com.internship.crm.user.domain.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("JWT 签发与校验")
@ExtendWith(ReadableTestResultExtension.class)
class JwtTokenServiceTest {

    private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    @DisplayName("签发的 Token 包含用户主体、用户名、角色和有效期")
    void issuedTokenContainsTheExpectedIdentityClaims() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtTokenService tokenService = new JwtTokenService(
                TEST_SECRET,
                60,
                Clock.fixed(now, ZoneOffset.UTC));

        Claims claims = tokenService.parseClaims(tokenService.issueToken(user()));

        assertAll(
                () -> assertEquals("42", claims.getSubject()),
                () -> assertEquals("admin.user", claims.get("username", String.class)),
                () -> assertEquals("ADMIN", claims.get("role", String.class)),
                () -> assertEquals(now, claims.getIssuedAt().toInstant()),
                () -> assertEquals(now.plusSeconds(3600), claims.getExpiration().toInstant()),
                () -> assertEquals(3600L, tokenService.expirationSeconds()));
    }

    @Test
    @DisplayName("被篡改签名的 Token 无法通过校验")
    void tamperedTokenIsRejected() {
        JwtTokenService tokenService = new JwtTokenService(
                TEST_SECRET,
                60,
                Clock.systemUTC());
        String token = tokenService.issueToken(user());
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        assertThrows(JwtException.class, () -> tokenService.parseClaims(tampered));
    }

    @Test
    @DisplayName("已过期的 Token 无法通过校验")
    void expiredTokenIsRejected() {
        Instant twoHoursAgo = Instant.now().minusSeconds(7200);
        JwtTokenService tokenService = new JwtTokenService(
                TEST_SECRET,
                1,
                Clock.fixed(twoHoursAgo, ZoneOffset.UTC));
        String expiredToken = tokenService.issueToken(user());

        assertThrows(ExpiredJwtException.class, () -> tokenService.parseClaims(expiredToken));
    }

    @Test
    @DisplayName("缺失或过短的签名密钥会在启动阶段被拒绝")
    void invalidSigningSecretsAreRejected() {
        assertAll(
                () -> assertThrows(IllegalStateException.class,
                        () -> new JwtTokenService("", 60, Clock.systemUTC())),
                () -> assertThrows(IllegalStateException.class,
                        () -> new JwtTokenService("c2hvcnQ=", 60, Clock.systemUTC())));
    }

    @Test
    @DisplayName("非正数的 Token 有效期会被拒绝")
    void nonPositiveExpirationIsRejected() {
        assertThrows(
                IllegalStateException.class,
                () -> new JwtTokenService(TEST_SECRET, 0, Clock.systemUTC()));
    }

    private User user() {
        User user = new User();
        user.setId(42L);
        user.setUsername("admin.user");
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}

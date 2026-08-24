package com.internship.crm.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.internship.crm.auth.exception.AuthErrorCode;
import com.internship.crm.common.exception.RateLimitExceededException;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("认证接口内存限流")
@ExtendWith(ReadableTestResultExtension.class)
class AuthRateLimiterTest {

    private MutableClock clock;
    private AuthRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-25T00:00:00Z"));
        rateLimiter = new AuthRateLimiter(
                5,
                Duration.ofMinutes(5),
                3,
                Duration.ofHours(1),
                100,
                clock);
    }

    @Test
    @DisplayName("同一 IP 和用户名的第六次登录在业务处理前被拒绝")
    void sixthLoginAttemptIsRejected() {
        for (int attempt = 0; attempt < 5; attempt++) {
            rateLimiter.consumeLoginAttempt("127.0.0.1", "Operator");
        }

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> rateLimiter.consumeLoginAttempt("127.0.0.1", "operator"));

        assertSame(AuthErrorCode.RATE_LIMITED, exception.errorCode());
        assertEquals(300, exception.retryAfterSeconds());
    }

    @Test
    @DisplayName("不同用户名和不同 IP 使用独立的登录窗口")
    void loginKeysAreIsolated() {
        for (int attempt = 0; attempt < 5; attempt++) {
            rateLimiter.consumeLoginAttempt("127.0.0.1", "operator");
        }

        assertDoesNotThrow(() -> rateLimiter.consumeLoginAttempt("127.0.0.1", "admin"));
        assertDoesNotThrow(() -> rateLimiter.consumeLoginAttempt("127.0.0.2", "operator"));
    }

    @Test
    @DisplayName("成功登录清除对应 IP 和用户名的计数")
    void successfulLoginCanClearAttempts() {
        for (int attempt = 0; attempt < 5; attempt++) {
            rateLimiter.consumeLoginAttempt("127.0.0.1", "operator");
        }

        rateLimiter.clearLoginAttempts("127.0.0.1", "operator");

        assertDoesNotThrow(() -> rateLimiter.consumeLoginAttempt("127.0.0.1", "operator"));
    }

    @Test
    @DisplayName("同一 IP 的第四次注册在业务处理前被拒绝")
    void fourthRegistrationAttemptIsRejected() {
        for (int attempt = 0; attempt < 3; attempt++) {
            rateLimiter.consumeRegistrationAttempt("127.0.0.1");
        }

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> rateLimiter.consumeRegistrationAttempt("127.0.0.1"));

        assertEquals(3600, exception.retryAfterSeconds());
    }

    @Test
    @DisplayName("固定窗口到期后允许重新尝试")
    void expiredWindowAllowsAnotherAttempt() {
        for (int attempt = 0; attempt < 5; attempt++) {
            rateLimiter.consumeLoginAttempt("127.0.0.1", "operator");
        }
        clock.advance(Duration.ofMinutes(5));

        assertDoesNotThrow(() -> rateLimiter.consumeLoginAttempt("127.0.0.1", "operator"));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

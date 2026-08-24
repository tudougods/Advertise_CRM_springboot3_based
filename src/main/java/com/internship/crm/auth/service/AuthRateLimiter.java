package com.internship.crm.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.internship.crm.auth.exception.AuthErrorCode;
import com.internship.crm.common.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Single-instance fixed-window rate limits for public authentication endpoints. */
@Service
public class AuthRateLimiter {

    private final int loginMaxAttempts;
    private final Duration loginWindow;
    private final int registrationMaxAttempts;
    private final Duration registrationWindow;
    private final Clock clock;
    private final Cache<LoginAttemptKey, AttemptWindow> loginAttempts;
    private final Cache<String, AttemptWindow> registrationAttempts;

    @Autowired
    public AuthRateLimiter(
            @Value("${security.auth-rate-limit.login-max-attempts}") int loginMaxAttempts,
            @Value("${security.auth-rate-limit.login-window-minutes}") long loginWindowMinutes,
            @Value("${security.auth-rate-limit.registration-max-attempts}") int registrationMaxAttempts,
            @Value("${security.auth-rate-limit.registration-window-minutes}") long registrationWindowMinutes,
            @Value("${security.auth-rate-limit.maximum-entries}") long maximumEntries) {
        this(
                loginMaxAttempts,
                Duration.ofMinutes(loginWindowMinutes),
                registrationMaxAttempts,
                Duration.ofMinutes(registrationWindowMinutes),
                maximumEntries,
                Clock.systemUTC());
    }

    AuthRateLimiter(
            int loginMaxAttempts,
            Duration loginWindow,
            int registrationMaxAttempts,
            Duration registrationWindow,
            long maximumEntries,
            Clock clock) {
        this.loginMaxAttempts = requirePositive(loginMaxAttempts, "loginMaxAttempts");
        this.loginWindow = requirePositive(loginWindow, "loginWindow");
        this.registrationMaxAttempts = requirePositive(registrationMaxAttempts, "registrationMaxAttempts");
        this.registrationWindow = requirePositive(registrationWindow, "registrationWindow");
        this.clock = clock;
        long cacheSize = requirePositive(maximumEntries, "maximumEntries");
        this.loginAttempts = newCache(cacheSize, loginWindow);
        this.registrationAttempts = newCache(cacheSize, registrationWindow);
    }

    public void consumeLoginAttempt(String clientIp, String username) {
        consume(
                loginAttempts,
                loginAttemptKey(clientIp, username),
                loginMaxAttempts,
                loginWindow);
    }

    public void clearLoginAttempts(String clientIp, String username) {
        loginAttempts.invalidate(loginAttemptKey(clientIp, username));
    }

    public void consumeRegistrationAttempt(String clientIp) {
        consume(registrationAttempts, normalizeClientIp(clientIp), registrationMaxAttempts, registrationWindow);
    }

    private <K> void consume(
            Cache<K, AttemptWindow> cache,
            K key,
            int maximumAttempts,
            Duration windowDuration) {
        Instant now = clock.instant();
        cache.asMap().compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.resetAt())) {
                return new AttemptWindow(1, now.plus(windowDuration));
            }
            if (current.attempts() >= maximumAttempts) {
                long retryAfterSeconds = Math.max(1, Duration.between(now, current.resetAt()).toSeconds());
                throw new RateLimitExceededException(AuthErrorCode.RATE_LIMITED, retryAfterSeconds);
            }
            return new AttemptWindow(current.attempts() + 1, current.resetAt());
        });
    }

    private String normalizeClientIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }

    private LoginAttemptKey loginAttemptKey(String clientIp, String username) {
        return new LoginAttemptKey(
                normalizeClientIp(clientIp),
                username.trim().toLowerCase(Locale.ROOT));
    }

    private static <K, V> Cache<K, V> newCache(long maximumEntries, Duration expiration) {
        return Caffeine.newBuilder()
                .maximumSize(maximumEntries)
                .expireAfterWrite(expiration)
                .build();
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    private record AttemptWindow(int attempts, Instant resetAt) {
    }

    private record LoginAttemptKey(String clientIp, String username) {
    }
}

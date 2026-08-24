package com.internship.crm.common.exception;

/** Expected rejection that tells the client when a rate-limit window resets. */
public class RateLimitExceededException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(ErrorCode errorCode, long retryAfterSeconds) {
        super(errorCode);
        if (retryAfterSeconds <= 0) {
            throw new IllegalArgumentException("retryAfterSeconds must be greater than zero");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}

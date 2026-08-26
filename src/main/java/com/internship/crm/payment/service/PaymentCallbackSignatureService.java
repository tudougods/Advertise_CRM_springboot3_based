package com.internship.crm.payment.service;

import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.exception.PaymentErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Verifies mock-provider callbacks without normalizing or re-serializing the signed body. */
@Service
public class PaymentCallbackSignatureService {

    static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final int MINIMUM_KEY_BYTES = 32;

    private final String base64Secret;
    private final long toleranceSeconds;
    private final Clock clock;

    public PaymentCallbackSignatureService(
            @Value("${app.payment.callback-secret:}") String base64Secret,
            @Value("${app.payment.callback-tolerance-seconds:300}") long toleranceSeconds,
            Clock clock) {
        this.base64Secret = base64Secret;
        this.toleranceSeconds = toleranceSeconds;
        this.clock = clock;
    }

    public void verify(String timestampHeader, String signatureHeader, byte[] rawPayload) {
        requirePayload(rawPayload);
        long timestamp = parseTimestamp(timestampHeader);
        verifyFreshness(timestamp);

        byte[] actualSignature = parseSignature(signatureHeader);
        byte[] expectedSignature = calculate(timestampHeader, rawPayload);
        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_SIGNATURE_INVALID);
        }
    }

    public String sign(String timestampHeader, byte[] rawPayload) {
        requirePayload(rawPayload);
        parseTimestamp(timestampHeader);
        return SIGNATURE_PREFIX + HexFormat.of().formatHex(calculate(timestampHeader, rawPayload));
    }

    public String payloadHash(byte[] rawPayload) {
        requirePayload(rawPayload);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawPayload));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private long parseTimestamp(String timestampHeader) {
        if (timestampHeader == null || timestampHeader.isBlank()) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_TIMESTAMP_INVALID);
        }
        try {
            long timestamp = Long.parseLong(timestampHeader);
            Instant.ofEpochSecond(timestamp);
            return timestamp;
        } catch (NumberFormatException | java.time.DateTimeException exception) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_TIMESTAMP_INVALID, exception);
        }
    }

    private void verifyFreshness(long timestamp) {
        if (toleranceSeconds <= 0) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_CONFIGURATION_ERROR);
        }
        long now = clock.instant().getEpochSecond();
        if (timestamp < now - toleranceSeconds || timestamp > now + toleranceSeconds) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_TIMESTAMP_EXPIRED);
        }
    }

    private byte[] parseSignature(String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_SIGNATURE_INVALID);
        }
        String hexadecimal = signatureHeader.substring(SIGNATURE_PREFIX.length());
        if (hexadecimal.length() != 64) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_SIGNATURE_INVALID);
        }
        try {
            return HexFormat.of().parseHex(hexadecimal);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_SIGNATURE_INVALID, exception);
        }
    }

    private byte[] calculate(String timestampHeader, byte[] rawPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey(), "HmacSHA256"));
            mac.update((timestampHeader + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(rawPayload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private byte[] secretKey() {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_CONFIGURATION_ERROR);
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Secret);
            if (decoded.length < MINIMUM_KEY_BYTES) {
                throw new BusinessException(PaymentErrorCode.CALLBACK_CONFIGURATION_ERROR);
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_CONFIGURATION_ERROR, exception);
        }
    }

    private void requirePayload(byte[] rawPayload) {
        if (rawPayload == null || rawPayload.length == 0 || rawPayload.length > MAX_PAYLOAD_BYTES) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_PAYLOAD_INVALID);
        }
    }
}

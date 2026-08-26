package com.internship.crm.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("模拟支付回调 HMAC 验签")
@ExtendWith(ReadableTestResultExtension.class)
class PaymentCallbackSignatureServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T01:02:03Z");
    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final byte[] PAYLOAD = "{\"eventId\":\"evt-1\"}"
            .getBytes(StandardCharsets.UTF_8);

    private final PaymentCallbackSignatureService service =
            new PaymentCallbackSignatureService(SECRET, 300, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("签名覆盖时间戳、分隔符和原始请求体")
    void verifiesExactSignedBytes() {
        String timestamp = Long.toString(NOW.getEpochSecond());
        String signature = service.sign(timestamp, PAYLOAD);

        service.verify(timestamp, signature, PAYLOAD);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify(
                        timestamp,
                        signature,
                        "{ \"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(PaymentErrorCode.CALLBACK_SIGNATURE_INVALID, exception.errorCode());
    }

    @Test
    @DisplayName("签名使用固定 sha256 前缀和 64 位十六进制摘要")
    void signatureHasCanonicalShape() {
        String signature = service.sign(Long.toString(NOW.getEpochSecond()), PAYLOAD);

        assertEquals(71, signature.length());
        assertEquals("sha256=", signature.substring(0, 7));
    }

    @Test
    @DisplayName("超过容忍窗口的过去或未来时间戳都会被拒绝")
    void rejectsTimestampOutsideTolerance() {
        String oldTimestamp = Long.toString(NOW.minusSeconds(301).getEpochSecond());
        String futureTimestamp = Long.toString(NOW.plusSeconds(301).getEpochSecond());

        assertError(
                PaymentErrorCode.CALLBACK_TIMESTAMP_EXPIRED,
                () -> service.verify(oldTimestamp, service.sign(oldTimestamp, PAYLOAD), PAYLOAD));
        assertError(
                PaymentErrorCode.CALLBACK_TIMESTAMP_EXPIRED,
                () -> service.verify(futureTimestamp, service.sign(futureTimestamp, PAYLOAD), PAYLOAD));
    }

    @Test
    @DisplayName("缺失或非数字时间戳返回稳定错误")
    void rejectsMalformedTimestamp() {
        assertError(
                PaymentErrorCode.CALLBACK_TIMESTAMP_INVALID,
                () -> service.verify(null, "sha256=00", PAYLOAD));
        assertError(
                PaymentErrorCode.CALLBACK_TIMESTAMP_INVALID,
                () -> service.verify("not-a-timestamp", "sha256=00", PAYLOAD));
    }

    @Test
    @DisplayName("缺失、格式错误或长度错误的签名都会被拒绝")
    void rejectsMalformedSignature() {
        String timestamp = Long.toString(NOW.getEpochSecond());

        assertError(
                PaymentErrorCode.CALLBACK_SIGNATURE_INVALID,
                () -> service.verify(timestamp, null, PAYLOAD));
        assertError(
                PaymentErrorCode.CALLBACK_SIGNATURE_INVALID,
                () -> service.verify(timestamp, "md5=abc", PAYLOAD));
        assertError(
                PaymentErrorCode.CALLBACK_SIGNATURE_INVALID,
                () -> service.verify(timestamp, "sha256=xyz", PAYLOAD));
    }

    @Test
    @DisplayName("验签密钥缺失、非 Base64 或不足 32 字节均视为配置错误")
    void rejectsUnsafeSecretConfiguration() {
        String timestamp = Long.toString(NOW.getEpochSecond());

        assertConfigurationError(new PaymentCallbackSignatureService("", 300, fixedClock()), timestamp);
        assertConfigurationError(
                new PaymentCallbackSignatureService("not-base64", 300, fixedClock()), timestamp);
        assertConfigurationError(
                new PaymentCallbackSignatureService(
                        Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8)),
                        300,
                        fixedClock()),
                timestamp);
    }

    @Test
    @DisplayName("空请求体和超过 16 KiB 的请求体在验签前被拒绝")
    void rejectsInvalidPayloadSize() {
        String timestamp = Long.toString(NOW.getEpochSecond());

        assertError(
                PaymentErrorCode.CALLBACK_PAYLOAD_INVALID,
                () -> service.verify(timestamp, "sha256=00", new byte[0]));
        assertError(
                PaymentErrorCode.CALLBACK_PAYLOAD_INVALID,
                () -> service.verify(
                        timestamp,
                        "sha256=00",
                        new byte[PaymentCallbackSignatureService.MAX_PAYLOAD_BYTES + 1]));
    }

    private void assertConfigurationError(
            PaymentCallbackSignatureService unsafeService,
            String timestamp) {
        assertError(
                PaymentErrorCode.CALLBACK_CONFIGURATION_ERROR,
                () -> unsafeService.sign(timestamp, PAYLOAD));
    }

    private void assertError(PaymentErrorCode expected, Runnable operation) {
        BusinessException exception = assertThrows(BusinessException.class, operation::run);
        assertEquals(expected, exception.errorCode());
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}

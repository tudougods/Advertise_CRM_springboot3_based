package com.internship.crm.payment.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.dto.request.MockPaymentCallbackRequest;
import com.internship.crm.payment.dto.response.MockPaymentCallbackResponse;
import com.internship.crm.payment.entity.MockPaymentOutcome;
import com.internship.crm.payment.entity.PaymentCallbackStatus;
import com.internship.crm.payment.entity.RechargeOrder;
import com.internship.crm.payment.entity.RechargePaymentCallback;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.payment.mapper.RechargeOrderMapper;
import com.internship.crm.payment.mapper.RechargePaymentCallbackMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authenticates and records mock-provider callbacks for later atomic processing. */
@Service
public class MockPaymentCallbackService {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentCallbackService.class);

    private final PaymentCallbackSignatureService signatureService;
    private final RechargePaymentCallbackMapper callbackMapper;
    private final RechargeOrderMapper orderMapper;
    private final AdvertiserAccountMapper accountMapper;
    private final RechargePaymentCallbackAuditService auditService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Clock clock;

    public MockPaymentCallbackService(
            PaymentCallbackSignatureService signatureService,
            RechargePaymentCallbackMapper callbackMapper,
            RechargeOrderMapper orderMapper,
            AdvertiserAccountMapper accountMapper,
            RechargePaymentCallbackAuditService auditService,
            ObjectMapper objectMapper,
            Validator validator,
            Clock clock) {
        this.signatureService = signatureService;
        this.callbackMapper = callbackMapper;
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional
    public MockPaymentCallbackResponse receive(
            String timestampHeader,
            String signatureHeader,
            byte[] rawPayload) {
        signatureService.verify(timestampHeader, signatureHeader, rawPayload);
        MockPaymentCallbackRequest request = parseAndValidate(rawPayload);
        String payloadHash = signatureService.payloadHash(rawPayload);

        RechargePaymentCallback existing = callbackMapper
                .findByProviderEventId(request.eventId())
                .orElse(null);
        if (existing != null) {
            return duplicateOrThrow(existing, request, payloadHash);
        }

        RechargeOrder order = orderMapper.findByOrderNo(request.orderNo())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND));
        AdvertiserAccount account = accountMapper.selectById(order.getAdvertiserAccountId());
        if (account == null) {
            throw new BusinessException(PaymentErrorCode.ACCOUNT_NOT_FOUND);
        }

        validateTrustedBusinessFields(request, order, account, payloadHash);

        OffsetDateTime receivedAt = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        RechargePaymentCallback callback = receivedCallback(request, order, payloadHash, receivedAt);
        if (callbackMapper.insertIfProviderEventIdAbsent(callback) == 0) {
            RechargePaymentCallback concurrent = callbackMapper
                    .findByProviderEventId(request.eventId())
                    .orElseThrow(() -> new BusinessException(PaymentErrorCode.CALLBACK_EVENT_CONFLICT));
            return duplicateOrThrow(concurrent, request, payloadHash);
        }

        log.info(
                "Trusted payment callback received: eventId={} orderNo={} outcome={}",
                request.eventId(),
                request.orderNo(),
                request.outcome());
        return response(callback, request.orderNo(), false);
    }

    private MockPaymentCallbackRequest parseAndValidate(byte[] rawPayload) {
        try {
            MockPaymentCallbackRequest request = objectMapper.readerFor(MockPaymentCallbackRequest.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(rawPayload);
            Set<ConstraintViolation<MockPaymentCallbackRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new BusinessException(PaymentErrorCode.CALLBACK_PAYLOAD_INVALID);
            }
            return request;
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_PAYLOAD_INVALID, exception);
        }
    }

    private void validateTrustedBusinessFields(
            MockPaymentCallbackRequest request,
            RechargeOrder order,
            AdvertiserAccount account,
            String payloadHash) {
        if (!account.getAdvertiserId().equals(request.advertiserId())) {
            reject(request, order, payloadHash, PaymentErrorCode.CALLBACK_ADVERTISER_MISMATCH);
        }
        if (order.getAmount().setScale(2, RoundingMode.UNNECESSARY)
                .compareTo(request.amount().setScale(2, RoundingMode.UNNECESSARY)) != 0) {
            reject(request, order, payloadHash, PaymentErrorCode.CALLBACK_AMOUNT_MISMATCH);
        }
        boolean successTransactionInvalid = request.outcome() == MockPaymentOutcome.SUCCESS
                && (request.providerTransactionNo() == null
                        || request.providerTransactionNo().isBlank());
        boolean failedTransactionInvalid = request.outcome() == MockPaymentOutcome.FAILED
                && request.providerTransactionNo() != null;
        if (successTransactionInvalid || failedTransactionInvalid) {
            reject(request, order, payloadHash, PaymentErrorCode.CALLBACK_PAYLOAD_INVALID);
        }
    }

    private void reject(
            MockPaymentCallbackRequest request,
            RechargeOrder order,
            String payloadHash,
            PaymentErrorCode errorCode) {
        boolean eventConsistent = auditService.recordRejected(
                request.eventId(), order.getId(), payloadHash, errorCode.code());
        if (!eventConsistent) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_EVENT_CONFLICT);
        }
        throw new BusinessException(errorCode);
    }

    private RechargePaymentCallback receivedCallback(
            MockPaymentCallbackRequest request,
            RechargeOrder order,
            String payloadHash,
            OffsetDateTime receivedAt) {
        RechargePaymentCallback callback = new RechargePaymentCallback();
        callback.setProviderEventId(request.eventId());
        callback.setRechargeOrderId(order.getId());
        callback.setCallbackStatus(PaymentCallbackStatus.RECEIVED);
        callback.setPayloadHash(payloadHash);
        callback.setReceivedAt(receivedAt);
        return callback;
    }

    private MockPaymentCallbackResponse duplicateOrThrow(
            RechargePaymentCallback existing,
            MockPaymentCallbackRequest request,
            String payloadHash) {
        if (!existing.getPayloadHash().equals(payloadHash)) {
            throw new BusinessException(PaymentErrorCode.CALLBACK_EVENT_CONFLICT);
        }
        return response(existing, request.orderNo(), true);
    }

    private MockPaymentCallbackResponse response(
            RechargePaymentCallback callback,
            String orderNo,
            boolean duplicate) {
        return new MockPaymentCallbackResponse(
                callback.getProviderEventId(),
                orderNo,
                callback.getCallbackStatus(),
                duplicate,
                callback.getReceivedAt());
    }
}

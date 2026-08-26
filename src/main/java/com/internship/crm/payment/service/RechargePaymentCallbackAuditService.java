package com.internship.crm.payment.service;

import com.internship.crm.payment.entity.PaymentCallbackStatus;
import com.internship.crm.payment.entity.RechargePaymentCallback;
import com.internship.crm.payment.mapper.RechargePaymentCallbackMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists rejected trusted callbacks even when the caller returns a business error. */
@Service
public class RechargePaymentCallbackAuditService {

    private final RechargePaymentCallbackMapper callbackMapper;
    private final Clock clock;

    public RechargePaymentCallbackAuditService(
            RechargePaymentCallbackMapper callbackMapper,
            Clock clock) {
        this.callbackMapper = callbackMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordRejected(
            String eventId,
            Long rechargeOrderId,
            String payloadHash,
            String failureReason) {
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        RechargePaymentCallback callback = new RechargePaymentCallback();
        callback.setProviderEventId(eventId);
        callback.setRechargeOrderId(rechargeOrderId);
        callback.setCallbackStatus(PaymentCallbackStatus.REJECTED);
        callback.setPayloadHash(payloadHash);
        callback.setFailureReason(failureReason);
        callback.setReceivedAt(now);
        callback.setProcessedAt(now);
        if (callbackMapper.insertIfProviderEventIdAbsent(callback) == 1) {
            return true;
        }
        return callbackMapper.findByProviderEventId(eventId)
                .map(existing -> existing.getPayloadHash().equals(payloadHash))
                .orElse(false);
    }
}

package com.internship.crm.payment.service;

import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.entity.RechargeOrder;
import com.internship.crm.payment.entity.RechargeOrderStatus;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.payment.validation.PaymentReferenceRules;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Applies the only legal state transitions for recharge orders. */
@Component
public class RechargeOrderStateMachine {

    private static final EnumSet<RechargeOrderStatus> TERMINAL_STATUSES =
            EnumSet.of(
                    RechargeOrderStatus.SUCCESS,
                    RechargeOrderStatus.FAILED,
                    RechargeOrderStatus.CLOSED);

    public void transition(
            RechargeOrder order,
            RechargeOrderStatus targetStatus,
            String providerTransactionNo,
            OffsetDateTime transitionTime) {
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(transitionTime, "transitionTime must not be null");
        if (order.getStatus() != RechargeOrderStatus.PENDING
                || targetStatus == null
                || !TERMINAL_STATUSES.contains(targetStatus)) {
            throw new BusinessException(PaymentErrorCode.INVALID_STATUS_TRANSITION);
        }

        OffsetDateTime normalizedTime = transitionTime.truncatedTo(ChronoUnit.MICROS);
        if (targetStatus == RechargeOrderStatus.SUCCESS) {
            order.setProviderTransactionNo(
                    PaymentReferenceRules.normalizeProviderTransactionNo(providerTransactionNo));
            order.setPaidAt(normalizedTime);
        } else {
            order.setProviderTransactionNo(null);
            order.setPaidAt(null);
        }
        order.setStatus(targetStatus);
        order.setUpdatedAt(normalizedTime);
    }

}

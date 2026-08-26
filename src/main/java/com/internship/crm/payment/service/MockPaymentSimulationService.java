package com.internship.crm.payment.service;

import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.dto.request.SimulateRechargePaymentRequest;
import com.internship.crm.payment.dto.response.RechargeOrderResponse;
import com.internship.crm.payment.entity.MockPaymentOutcome;
import com.internship.crm.payment.entity.RechargeOrder;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.payment.mapper.RechargeOrderMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "test"})
public class MockPaymentSimulationService {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentSimulationService.class);

    private final RechargeOrderMapper rechargeOrderMapper;
    private final RechargePaymentProcessor paymentProcessor;
    private final MockPaymentReferenceGenerator referenceGenerator;
    private final Clock clock;

    public MockPaymentSimulationService(
            RechargeOrderMapper rechargeOrderMapper,
            RechargePaymentProcessor paymentProcessor,
            MockPaymentReferenceGenerator referenceGenerator,
            Clock clock) {
        this.rechargeOrderMapper = rechargeOrderMapper;
        this.paymentProcessor = paymentProcessor;
        this.referenceGenerator = referenceGenerator;
        this.clock = clock;
    }

    @Transactional
    public RechargeOrderResponse simulate(
            String rawOrderNo,
            SimulateRechargePaymentRequest request) {
        String orderNo = normalizeOrderNo(rawOrderNo);
        RechargeOrder order = rechargeOrderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND);
        }

        String providerTransactionNo = request.outcome() == MockPaymentOutcome.SUCCESS
                ? referenceGenerator.nextProviderTransactionNo()
                : null;
        Long advertiserId = paymentProcessor.process(
                order,
                request.outcome(),
                providerTransactionNo,
                OffsetDateTime.now(clock));
        log.info(
                "Mock payment completed: orderNo={} outcome={}",
                order.getOrderNo(),
                request.outcome());
        return RechargeOrderResponse.from(order, advertiserId);
    }

    private String normalizeOrderNo(String orderNo) {
        if (orderNo == null) {
            throw new BusinessException(PaymentErrorCode.INVALID_ORDER_NO);
        }
        String normalized = orderNo.trim();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new BusinessException(PaymentErrorCode.INVALID_ORDER_NO);
        }
        return normalized;
    }
}

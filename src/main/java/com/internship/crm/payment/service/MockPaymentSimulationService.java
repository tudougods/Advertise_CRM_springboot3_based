package com.internship.crm.payment.service;

import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.dto.request.SimulateRechargePaymentRequest;
import com.internship.crm.payment.dto.response.RechargeOrderResponse;
import com.internship.crm.payment.entity.MockPaymentOutcome;
import com.internship.crm.payment.entity.RechargeOrder;
import com.internship.crm.payment.entity.RechargeOrderStatus;
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
    private final AdvertiserAccountMapper accountMapper;
    private final RechargeOrderStateMachine stateMachine;
    private final MockPaymentReferenceGenerator referenceGenerator;
    private final Clock clock;

    public MockPaymentSimulationService(
            RechargeOrderMapper rechargeOrderMapper,
            AdvertiserAccountMapper accountMapper,
            RechargeOrderStateMachine stateMachine,
            MockPaymentReferenceGenerator referenceGenerator,
            Clock clock) {
        this.rechargeOrderMapper = rechargeOrderMapper;
        this.accountMapper = accountMapper;
        this.stateMachine = stateMachine;
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

        RechargeOrderStatus targetStatus = RechargeOrderStatus.valueOf(request.outcome().name());
        String providerTransactionNo = request.outcome() == MockPaymentOutcome.SUCCESS
                ? referenceGenerator.nextProviderTransactionNo()
                : null;
        stateMachine.transition(
                order,
                targetStatus,
                providerTransactionNo,
                OffsetDateTime.now(clock));
        if (rechargeOrderMapper.updateById(order) != 1) {
            throw new BusinessException(PaymentErrorCode.ORDER_UPDATE_CONFLICT);
        }

        AdvertiserAccount account = accountMapper.selectById(order.getAdvertiserAccountId());
        if (account == null) {
            throw new BusinessException(PaymentErrorCode.ACCOUNT_NOT_FOUND);
        }
        log.info(
                "Mock payment completed: orderNo={} outcome={}",
                order.getOrderNo(),
                request.outcome());
        return RechargeOrderResponse.from(order, account.getAdvertiserId());
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

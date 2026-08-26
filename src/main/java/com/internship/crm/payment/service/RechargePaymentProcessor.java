package com.internship.crm.payment.service;

import com.internship.crm.account.entity.AccountTransactionType;
import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.entity.AdvertiserAccountTransaction;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
import com.internship.crm.account.mapper.AdvertiserAccountTransactionMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.entity.MockPaymentOutcome;
import com.internship.crm.payment.entity.RechargeOrder;
import com.internship.crm.payment.entity.RechargeOrderStatus;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.payment.mapper.RechargeOrderMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Completes an already locked order together with its balance and immutable ledger entry. */
@Service
public class RechargePaymentProcessor {

    private final RechargeOrderMapper orderMapper;
    private final AdvertiserAccountMapper accountMapper;
    private final AdvertiserAccountTransactionMapper transactionMapper;
    private final RechargeOrderStateMachine stateMachine;

    public RechargePaymentProcessor(
            RechargeOrderMapper orderMapper,
            AdvertiserAccountMapper accountMapper,
            AdvertiserAccountTransactionMapper transactionMapper,
            RechargeOrderStateMachine stateMachine) {
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
        this.stateMachine = stateMachine;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long process(
            RechargeOrder order,
            MockPaymentOutcome outcome,
            String providerTransactionNo,
            OffsetDateTime processingTime) {
        AdvertiserAccount account = accountMapper.selectById(order.getAdvertiserAccountId());
        if (account == null) {
            throw new BusinessException(PaymentErrorCode.ACCOUNT_NOT_FOUND);
        }

        OffsetDateTime processedAt = processingTime.truncatedTo(ChronoUnit.MICROS);
        stateMachine.transition(
                order,
                RechargeOrderStatus.valueOf(outcome.name()),
                providerTransactionNo,
                processedAt);
        if (orderMapper.updateById(order) != 1) {
            throw new BusinessException(PaymentErrorCode.ORDER_UPDATE_CONFLICT);
        }

        if (outcome == MockPaymentOutcome.SUCCESS) {
            creditAndRecord(order, processedAt);
        }
        return account.getAdvertiserId();
    }

    private void creditAndRecord(RechargeOrder order, OffsetDateTime processedAt) {
        BigDecimal balanceAfter = accountMapper.credit(
                order.getAdvertiserAccountId(), order.getAmount(), processedAt);
        if (balanceAfter == null) {
            throw new BusinessException(PaymentErrorCode.ACCOUNT_NOT_FOUND);
        }

        AdvertiserAccountTransaction transaction = new AdvertiserAccountTransaction();
        transaction.setAdvertiserAccountId(order.getAdvertiserAccountId());
        transaction.setBusinessNo(order.getOrderNo());
        transaction.setTransactionType(AccountTransactionType.RECHARGE);
        transaction.setAmount(order.getAmount());
        transaction.setBalanceAfter(balanceAfter);
        transaction.setRechargeOrderId(order.getId());
        transaction.setRemark("模拟支付充值到账");
        transaction.setCreatedAt(processedAt);
        if (transactionMapper.insertIfBusinessNoAbsent(transaction) != 1) {
            throw new BusinessException(PaymentErrorCode.RECHARGE_PROCESSING_CONFLICT);
        }
    }
}

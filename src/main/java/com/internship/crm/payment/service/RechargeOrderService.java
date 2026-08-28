package com.internship.crm.payment.service;

import com.internship.crm.account.entity.AdvertiserAccount;
import com.internship.crm.account.mapper.AdvertiserAccountMapper;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.payment.dto.request.CreateRechargeOrderRequest;
import com.internship.crm.payment.dto.response.RechargeOrderResponse;
import com.internship.crm.payment.entity.RechargeOrder;
import com.internship.crm.payment.entity.RechargeOrderStatus;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.payment.mapper.RechargeOrderMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RechargeOrderService {

    private static final Logger log = LoggerFactory.getLogger(RechargeOrderService.class);

    private final RechargeOrderMapper rechargeOrderMapper;
    private final AdvertiserAccountMapper accountMapper;
    private final AdvertiserMapper advertiserMapper;
    private final RechargeOrderNumberGenerator orderNumberGenerator;
    private final Clock clock;

    public RechargeOrderService(
            RechargeOrderMapper rechargeOrderMapper,
            AdvertiserAccountMapper accountMapper,
            AdvertiserMapper advertiserMapper,
            RechargeOrderNumberGenerator orderNumberGenerator,
            Clock clock) {
        this.rechargeOrderMapper = rechargeOrderMapper;
        this.accountMapper = accountMapper;
        this.advertiserMapper = advertiserMapper;
        this.orderNumberGenerator = orderNumberGenerator;
        this.clock = clock;
    }

    @Transactional
    public RechargeOrderResponse create(CreateRechargeOrderRequest request) {
        BigDecimal amount = normalizeAmount(request.amount());
        AdvertiserAccount account = requireAccount(request.advertiserId());
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);

        RechargeOrder order = new RechargeOrder();
        order.setOrderNo(orderNumberGenerator.nextOrderNo());
        order.setAdvertiserAccountId(account.getId());
        order.setAmount(amount);
        order.setStatus(RechargeOrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        rechargeOrderMapper.insert(order);

        log.info(
                "Recharge order created: orderNo={} advertiserId={} accountId={}",
                order.getOrderNo(),
                request.advertiserId(),
                account.getId());
        return RechargeOrderResponse.from(order, request.advertiserId());
    }

    @Transactional(readOnly = true)
    public RechargeOrderResponse findByOrderNo(String rawOrderNo) {
        String orderNo = normalizeOrderNo(rawOrderNo);
        RechargeOrder order = rechargeOrderMapper.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND));
        AdvertiserAccount account = accountMapper.selectById(order.getAdvertiserAccountId());
        if (account == null) {
            throw new BusinessException(PaymentErrorCode.ACCOUNT_NOT_FOUND);
        }
        return RechargeOrderResponse.from(order, account.getAdvertiserId());
    }

    private AdvertiserAccount requireAccount(Long advertiserId) {
        if (advertiserId == null || advertiserId <= 0) {
            throw new BusinessException(PaymentErrorCode.ADVERTISER_NOT_FOUND);
        }
        return accountMapper.findByAdvertiserId(advertiserId)
                .orElseThrow(() -> missingAccount(advertiserId));
    }

    private BusinessException missingAccount(Long advertiserId) {
        if (advertiserMapper.selectById(advertiserId) == null) {
            return new BusinessException(PaymentErrorCode.ADVERTISER_NOT_FOUND);
        }
        return new BusinessException(PaymentErrorCode.ACCOUNT_NOT_FOUND);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(PaymentErrorCode.INVALID_AMOUNT);
        }
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 19) {
                throw new BusinessException(PaymentErrorCode.INVALID_AMOUNT);
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new BusinessException(PaymentErrorCode.INVALID_AMOUNT, exception);
        }
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

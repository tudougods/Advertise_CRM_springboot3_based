package com.internship.crm.payment.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.payment.dto.request.CreateRechargeOrderRequest;
import com.internship.crm.payment.dto.response.RechargeOrderResponse;
import com.internship.crm.payment.entity.RechargeOrderStatus;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Transactional
@DisplayName("充值订单创建查询 PostgreSQL 持久化")
@ExtendWith(ReadableTestResultExtension.class)
class RechargeOrderServicePersistenceTest {

    @Autowired
    private AdvertiserService advertiserService;

    @Autowired
    private RechargeOrderService rechargeOrderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("创建后可按订单号查询且数据库保持 PENDING 初始状态")
    void createdOrderCanBeQueriedByGeneratedOrderNumber() {
        AdvertiserResponse advertiser = advertiserService.create(new CreateAdvertiserRequest(
                "payment-order-" + UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                null));

        RechargeOrderResponse created = rechargeOrderService.create(
                new CreateRechargeOrderRequest(advertiser.id(), new BigDecimal("250.5")));
        RechargeOrderResponse found = rechargeOrderService.findByOrderNo(created.orderNo());

        String persistedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM recharge_orders WHERE order_no = ?",
                String.class,
                created.orderNo());
        assertAll(
                () -> assertNotNull(created.id()),
                () -> assertEquals(advertiser.id(), created.advertiserId()),
                () -> assertEquals(new BigDecimal("250.50"), created.amount()),
                () -> assertEquals(RechargeOrderStatus.PENDING, created.status()),
                () -> assertNull(created.providerTransactionNo()),
                () -> assertNull(created.paidAt()),
                () -> assertEquals(created, found),
                () -> assertEquals("PENDING", persistedStatus));
    }
}

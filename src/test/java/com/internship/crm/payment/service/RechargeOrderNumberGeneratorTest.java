package com.internship.crm.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("充值订单号生成规则")
@ExtendWith(ReadableTestResultExtension.class)
class RechargeOrderNumberGeneratorTest {

    @Test
    @DisplayName("订单号使用固定前缀和 32 位大写十六进制随机值")
    void generatesOpaqueUniqueDatabaseSafeOrderNumbers() {
        RechargeOrderNumberGenerator generator = new RechargeOrderNumberGenerator();
        Set<String> orderNumbers = new HashSet<>();

        for (int index = 0; index < 100; index++) {
            String orderNo = generator.nextOrderNo();
            assertTrue(orderNo.matches("RCH-[0-9A-F]{32}"));
            assertTrue(orderNo.length() <= 64);
            orderNumbers.add(orderNo);
        }

        assertEquals(100, orderNumbers.size());
    }

    @Test
    @DisplayName("mock 平台交易号使用独立固定前缀且满足数据库长度")
    void generatesMockProviderTransactionNumbers() {
        MockPaymentReferenceGenerator generator = new MockPaymentReferenceGenerator();

        String reference = generator.nextProviderTransactionNo();

        assertTrue(reference.matches("MOCK-TXN-[0-9A-F]{32}"));
        assertTrue(reference.length() <= 100);
    }
}

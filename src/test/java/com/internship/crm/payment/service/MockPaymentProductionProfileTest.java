package com.internship.crm.payment.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.internship.crm.payment.controller.MockPaymentSimulationController;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@ActiveProfiles("prod")
@DisplayName("生产环境模拟支付隔离")
@ExtendWith(ReadableTestResultExtension.class)
class MockPaymentProductionProfileTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("prod profile 不注册模拟 Controller、Service 或平台交易号生成器")
    void productionDoesNotRegisterMockPaymentComponents() {
        assertTrue(applicationContext.getBeansOfType(MockPaymentSimulationController.class).isEmpty());
        assertTrue(applicationContext.getBeansOfType(MockPaymentSimulationService.class).isEmpty());
        assertTrue(applicationContext.getBeansOfType(MockPaymentReferenceGenerator.class).isEmpty());
    }
}

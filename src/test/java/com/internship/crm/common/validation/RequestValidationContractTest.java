package com.internship.crm.common.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.internship.crm.delivery.dto.request.CreateAdvertisingDeliveryRecordRequest;
import com.internship.crm.delivery.dto.request.UpdateAdvertisingDeliveryRecordRequest;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.dto.request.UpdateUserRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@DisplayName("请求参数校验契约")
@ExtendWith(ReadableTestResultExtension.class)
class RequestValidationContractTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("创建投放记录时在进入 Service 前拒绝非法漏斗关系")
    void createDeliveryRejectsInvalidFunnelMetrics() {
        CreateAdvertisingDeliveryRecordRequest request =
                new CreateAdvertisingDeliveryRecordRequest(
                        "DELIVERY-001",
                        1L,
                        "SEARCH",
                        LocalDate.of(2026, 9, 1),
                        100L,
                        120L,
                        130L,
                        new BigDecimal("10.00"));

        assertEquals(
                Set.of("clicksWithinImpressions", "conversionsWithinClicks"),
                invalidProperties(request));
    }

    @Test
    @DisplayName("局部修正同时提供相关指标时拒绝非法漏斗关系")
    void updateDeliveryRejectsInvalidProvidedMetricPairs() {
        UpdateAdvertisingDeliveryRecordRequest request =
                new UpdateAdvertisingDeliveryRecordRequest(
                        null,
                        null,
                        null,
                        100L,
                        101L,
                        102L,
                        null);

        assertEquals(
                Set.of("clicksWithinImpressions", "conversionsWithinClicks"),
                invalidProperties(request));
    }

    @Test
    @DisplayName("局部修正只提供单个指标时保留 Service 合并校验职责")
    void updateDeliveryAllowsSingleMetricForServiceMergeValidation() {
        UpdateAdvertisingDeliveryRecordRequest request =
                new UpdateAdvertisingDeliveryRecordRequest(
                        null,
                        null,
                        null,
                        null,
                        50L,
                        null,
                        null);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("用户局部修改拒绝只有空白字符的新密码")
    void updateUserRejectsBlankPassword() {
        UpdateUserRequest request = new UpdateUserRequest(null, null, "        ", null, null);

        assertEquals(Set.of("password"), invalidProperties(request));
    }

    private Set<String> invalidProperties(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}

package com.internship.crm.common.exception;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;

import com.internship.crm.account.exception.AccountErrorCode;
import com.internship.crm.advertiser.exception.AdvertiserErrorCode;
import com.internship.crm.auth.exception.AuthErrorCode;
import com.internship.crm.delivery.exception.DeliveryErrorCode;
import com.internship.crm.payment.exception.PaymentErrorCode;
import com.internship.crm.report.exception.ReportErrorCode;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.exception.UserErrorCode;

@DisplayName("全局错误码契约")
@ExtendWith(ReadableTestResultExtension.class)
class ErrorCodeContractTest {

    private static final Map<String, ErrorCode[]> ERROR_CODES_BY_PREFIX = registeredErrorCodes();

    @Test
    @DisplayName("所有错误码全局唯一")
    void everyErrorCodeIsGloballyUnique() {
        Map<String, Long> duplicateCounts = allErrorCodes()
                .collect(Collectors.groupingBy(
                        ErrorCode::code,
                        LinkedHashMap::new,
                        Collectors.counting()));
        duplicateCounts.entrySet().removeIf(entry -> entry.getValue() == 1);

        assertTrue(
                duplicateCounts.isEmpty(),
                () -> "发现重复错误码: " + duplicateCounts);
    }

    @Test
    @DisplayName("所有错误码具有正确前缀和客户端错误状态")
    void everyErrorCodeHasTheExpectedPrefixAndErrorStatus() {
        List<Executable> assertions = new ArrayList<>();
        ERROR_CODES_BY_PREFIX.forEach((prefix, errorCodes) -> Arrays.stream(errorCodes)
                .forEach(errorCode -> {
                    assertions.add(() -> assertTrue(
                            errorCode.code().startsWith(prefix),
                            () -> errorCode.code() + " 应以前缀 " + prefix + " 开头"));
                    assertions.add(() -> assertFalse(
                            errorCode.code().isBlank(),
                            "错误码不能为空白"));
                    assertions.add(() -> assertTrue(
                            errorCode.code().matches("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+"),
                            () -> errorCode.code() + " 必须使用大写下划线格式"));
                    assertions.add(() -> assertFalse(
                            errorCode.message().isBlank(),
                            () -> errorCode.code() + " 的客户端消息不能为空白"));
                    assertions.add(() -> assertTrue(
                            errorCode.status().isError(),
                            () -> errorCode.code() + " 必须映射到 4xx 或 5xx"));
                }));

        assertAll(assertions);
    }

    private Stream<ErrorCode> allErrorCodes() {
        return ERROR_CODES_BY_PREFIX.values().stream().flatMap(Arrays::stream);
    }

    private static Map<String, ErrorCode[]> registeredErrorCodes() {
        Map<String, ErrorCode[]> errorCodes = new LinkedHashMap<>();
        errorCodes.put("COMMON_", CommonErrorCode.values());
        errorCodes.put("AUTH_", AuthErrorCode.values());
        errorCodes.put("USER_", UserErrorCode.values());
        errorCodes.put("ADVERTISER_", AdvertiserErrorCode.values());
        errorCodes.put("DELIVERY_", DeliveryErrorCode.values());
        errorCodes.put("REPORT_", ReportErrorCode.values());
        errorCodes.put("ACCOUNT_", AccountErrorCode.values());
        errorCodes.put("PAYMENT_", PaymentErrorCode.values());
        return Map.copyOf(errorCodes);
    }
}

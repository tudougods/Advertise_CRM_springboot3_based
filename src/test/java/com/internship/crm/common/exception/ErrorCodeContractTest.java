package com.internship.crm.common.exception;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;

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
    private static final Map<ErrorCode, HttpStatus> REVIEWED_HTTP_STATUSES = reviewedHttpStatuses();

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

    @Test
    @DisplayName("所有错误码使用语义审核后的精确 HTTP 状态")
    void everyErrorCodeUsesTheReviewedHttpStatus() {
        Set<ErrorCode> registered = allErrorCodes().collect(Collectors.toSet());

        assertEquals(
                registered,
                REVIEWED_HTTP_STATUSES.keySet(),
                "精确状态清单必须完整覆盖所有已注册错误码，且不能包含失效项");
        assertAll(REVIEWED_HTTP_STATUSES.entrySet().stream()
                .map(entry -> (Executable) () -> assertEquals(
                        entry.getValue(),
                        entry.getKey().status(),
                        () -> entry.getKey().code() + " 的 HTTP 状态与语义审核结果不一致")));
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

    private static Map<ErrorCode, HttpStatus> reviewedHttpStatuses() {
        Map<ErrorCode, HttpStatus> statuses = new LinkedHashMap<>();

        register(statuses, HttpStatus.BAD_REQUEST,
                CommonErrorCode.VALIDATION_ERROR,
                CommonErrorCode.BAD_REQUEST,
                UserErrorCode.NO_FIELDS_TO_UPDATE,
                AdvertiserErrorCode.CATEGORY_DISABLED,
                AdvertiserErrorCode.OWNER_DISABLED,
                AdvertiserErrorCode.NO_FIELDS_TO_UPDATE,
                AdvertiserErrorCode.CATEGORY_NO_FIELDS_TO_UPDATE,
                DeliveryErrorCode.ADVERTISER_DISABLED,
                DeliveryErrorCode.ADVERTISING_TYPE_DISABLED,
                DeliveryErrorCode.INVALID_DATE_RANGE,
                DeliveryErrorCode.DATE_RANGE_TOO_LARGE,
                DeliveryErrorCode.INCOMPLETE_DATE_RANGE,
                DeliveryErrorCode.BLANK_ADVERTISING_TYPE_CODE,
                DeliveryErrorCode.NO_FIELDS_TO_UPDATE,
                DeliveryErrorCode.INVALID_METRICS,
                ReportErrorCode.INVALID_DATE_RANGE,
                ReportErrorCode.DATE_RANGE_TOO_LARGE,
                ReportErrorCode.INCOMPLETE_DATE_RANGE,
                ReportErrorCode.BLANK_ADVERTISING_TYPE_CODE,
                AccountErrorCode.INVALID_BUSINESS_NO,
                AccountErrorCode.INVALID_AMOUNT,
                AccountErrorCode.INCOMPLETE_TRANSACTION_TIME_RANGE,
                AccountErrorCode.INVALID_TRANSACTION_TIME_RANGE,
                AccountErrorCode.TRANSACTION_TIME_RANGE_TOO_LARGE,
                PaymentErrorCode.INVALID_AMOUNT,
                PaymentErrorCode.INVALID_ORDER_NO,
                PaymentErrorCode.INVALID_PROVIDER_TRANSACTION_NO,
                PaymentErrorCode.CALLBACK_TIMESTAMP_INVALID,
                PaymentErrorCode.CALLBACK_PAYLOAD_INVALID);

        register(statuses, HttpStatus.UNAUTHORIZED,
                AuthErrorCode.INVALID_CREDENTIALS,
                AuthErrorCode.UNAUTHORIZED,
                PaymentErrorCode.CALLBACK_TIMESTAMP_EXPIRED,
                PaymentErrorCode.CALLBACK_SIGNATURE_INVALID);

        register(statuses, HttpStatus.FORBIDDEN,
                AuthErrorCode.ACCESS_DENIED);

        register(statuses, HttpStatus.NOT_FOUND,
                CommonErrorCode.NOT_FOUND,
                UserErrorCode.USER_NOT_FOUND,
                AdvertiserErrorCode.ADVERTISER_NOT_FOUND,
                AdvertiserErrorCode.CATEGORY_NOT_FOUND,
                AdvertiserErrorCode.OWNER_NOT_FOUND,
                DeliveryErrorCode.ADVERTISER_NOT_FOUND,
                DeliveryErrorCode.ADVERTISING_TYPE_NOT_FOUND,
                DeliveryErrorCode.DELIVERY_RECORD_NOT_FOUND,
                AccountErrorCode.ADVERTISER_NOT_FOUND,
                AccountErrorCode.ACCOUNT_NOT_FOUND,
                AccountErrorCode.DELIVERY_RECORD_NOT_FOUND,
                PaymentErrorCode.ADVERTISER_NOT_FOUND,
                PaymentErrorCode.ACCOUNT_NOT_FOUND,
                PaymentErrorCode.ORDER_NOT_FOUND);

        register(statuses, HttpStatus.CONFLICT,
                CommonErrorCode.CONFLICT,
                UserErrorCode.USERNAME_ALREADY_EXISTS,
                UserErrorCode.EMAIL_ALREADY_EXISTS,
                UserErrorCode.LAST_ACTIVE_ADMIN_REQUIRED,
                AdvertiserErrorCode.ADVERTISER_NAME_ALREADY_EXISTS,
                AdvertiserErrorCode.REGISTRATION_NO_ALREADY_EXISTS,
                AdvertiserErrorCode.CATEGORY_NAME_ALREADY_EXISTS,
                AdvertiserErrorCode.ADVERTISER_HAS_BUSINESS_DATA,
                DeliveryErrorCode.EXTERNAL_RECORD_NO_ALREADY_EXISTS,
                DeliveryErrorCode.DELIVERY_RECORD_ADVERTISER_LOCKED,
                DeliveryErrorCode.DELIVERY_RECORD_IN_USE,
                AccountErrorCode.BUSINESS_NO_ALREADY_EXISTS,
                AccountErrorCode.INSUFFICIENT_BALANCE,
                AccountErrorCode.DELIVERY_RECORD_ADVERTISER_MISMATCH,
                PaymentErrorCode.INVALID_STATUS_TRANSITION,
                PaymentErrorCode.ORDER_UPDATE_CONFLICT,
                PaymentErrorCode.CALLBACK_EVENT_CONFLICT,
                PaymentErrorCode.CALLBACK_ADVERTISER_MISMATCH,
                PaymentErrorCode.CALLBACK_AMOUNT_MISMATCH,
                PaymentErrorCode.RECHARGE_PROCESSING_CONFLICT);

        register(statuses, HttpStatus.TOO_MANY_REQUESTS,
                AuthErrorCode.RATE_LIMITED);

        register(statuses, HttpStatus.INTERNAL_SERVER_ERROR,
                CommonErrorCode.INTERNAL_ERROR,
                PaymentErrorCode.CALLBACK_CONFIGURATION_ERROR);

        return Map.copyOf(statuses);
    }

    private static void register(
            Map<ErrorCode, HttpStatus> statuses,
            HttpStatus status,
            ErrorCode... errorCodes) {
        Arrays.stream(errorCodes).forEach(errorCode -> {
            HttpStatus previous = statuses.put(errorCode, status);
            if (previous != null) {
                throw new IllegalStateException(errorCode.code() + " 被重复加入精确状态清单");
            }
        });
    }
}

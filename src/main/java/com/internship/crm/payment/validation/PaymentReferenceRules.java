package com.internship.crm.payment.validation;

import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.common.exception.ErrorCode;
import com.internship.crm.payment.exception.PaymentErrorCode;
import java.util.regex.Pattern;

/** Shared syntax and normalization rules for externally visible payment references. */
public final class PaymentReferenceRules {

    public static final String SAFE_REFERENCE_PATTERN = "[A-Za-z0-9._:-]+";
    public static final int ORDER_NO_MAX_LENGTH = 64;
    public static final int EXTERNAL_REFERENCE_MAX_LENGTH = 100;

    private static final Pattern SAFE_REFERENCE = Pattern.compile(SAFE_REFERENCE_PATTERN);

    private PaymentReferenceRules() {
    }

    public static String normalizeOrderNo(String orderNo) {
        return normalizeRequired(
                orderNo,
                ORDER_NO_MAX_LENGTH,
                PaymentErrorCode.INVALID_ORDER_NO);
    }

    public static String normalizeProviderTransactionNo(String providerTransactionNo) {
        return normalizeRequired(
                providerTransactionNo,
                EXTERNAL_REFERENCE_MAX_LENGTH,
                PaymentErrorCode.INVALID_PROVIDER_TRANSACTION_NO);
    }

    private static String normalizeRequired(String value, int maxLength, ErrorCode errorCode) {
        if (value == null) {
            throw new BusinessException(errorCode);
        }
        String normalized = value.trim();
        if (normalized.isEmpty()
                || normalized.length() > maxLength
                || !SAFE_REFERENCE.matcher(normalized).matches()) {
            throw new BusinessException(errorCode);
        }
        return normalized;
    }
}

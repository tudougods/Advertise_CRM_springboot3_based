package com.internship.crm.payment.dto.response;

import com.internship.crm.payment.entity.PaymentCallbackStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "模拟支付回调接收结果")
public record MockPaymentCallbackResponse(
        String eventId,
        String orderNo,
        PaymentCallbackStatus callbackStatus,
        boolean duplicate,
        OffsetDateTime receivedAt) {
}

package com.internship.crm.payment.controller;

import com.internship.crm.common.response.ApiResponse;
import com.internship.crm.payment.dto.request.MockPaymentCallbackRequest;
import com.internship.crm.payment.dto.response.MockPaymentCallbackResponse;
import com.internship.crm.payment.service.MockPaymentCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-callbacks")
@Tag(name = "模拟支付回调", description = "接收经过 HMAC-SHA256 验签的本地支付平台回调")
public class MockPaymentCallbackController {

    public static final String TIMESTAMP_HEADER = "X-Mock-Payment-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Mock-Payment-Signature";

    private final MockPaymentCallbackService callbackService;

    public MockPaymentCallbackController(MockPaymentCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PostMapping(value = "/mock", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "接收模拟支付回调",
            description = "公开端点；签名原文为 timestamp + '.' + HTTP 原始请求体")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = MockPaymentCallbackRequest.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "回调已接收或幂等确认"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "时间戳或请求体不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或回调已过期"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "充值订单不存在"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "事件、广告主或金额冲突")
    })
    public ApiResponse<MockPaymentCallbackResponse> receive(
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody byte[] rawPayload) {
        return ApiResponse.success(callbackService.receive(timestamp, signature, rawPayload));
    }
}

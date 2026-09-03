package com.internship.crm.payment.controller;

import com.internship.crm.common.response.ApiResponse;
import com.internship.crm.payment.dto.request.SimulateRechargePaymentRequest;
import com.internship.crm.payment.dto.response.RechargeOrderResponse;
import com.internship.crm.payment.service.MockPaymentSimulationService;
import com.internship.crm.payment.validation.PaymentReferenceRules;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Profile({"local", "test"})
@RequestMapping("/api/v1/payment-orders")
@Tag(name = "本地模拟支付", description = "仅 local/test profile 可用的支付结果模拟入口")
@SecurityRequirement(name = "bearerAuth")
public class MockPaymentSimulationController {

    private final MockPaymentSimulationService simulationService;

    public MockPaymentSimulationController(MockPaymentSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/{orderNo}/simulate")
    @Operation(summary = "本地模拟支付", description = "仅 ADMIN 可用；生产环境不注册此接口")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "模拟完成"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "订单不存在"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "订单已进入终态")
    })
    public ApiResponse<RechargeOrderResponse> simulate(
            @NotBlank(message = "充值订单号不能为空")
            @Size(max = PaymentReferenceRules.ORDER_NO_MAX_LENGTH,
                    message = "充值订单号不能超过 64 个字符")
            @Pattern(regexp = PaymentReferenceRules.SAFE_REFERENCE_PATTERN,
                    message = "充值订单号格式不合法")
            @PathVariable String orderNo,
            @Valid @RequestBody SimulateRechargePaymentRequest request) {
        return ApiResponse.success(simulationService.simulate(orderNo, request));
    }
}

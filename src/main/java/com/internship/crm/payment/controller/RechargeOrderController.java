package com.internship.crm.payment.controller;

import com.internship.crm.common.response.ApiResponse;
import com.internship.crm.payment.dto.request.CreateRechargeOrderRequest;
import com.internship.crm.payment.dto.response.RechargeOrderResponse;
import com.internship.crm.payment.service.RechargeOrderService;
import com.internship.crm.payment.validation.PaymentReferenceRules;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/payment-orders")
@Tag(name = "模拟支付订单", description = "创建和查询广告主充值订单")
@SecurityRequirement(name = "bearerAuth")
public class RechargeOrderController {

    private final RechargeOrderService rechargeOrderService;

    public RechargeOrderController(RechargeOrderService rechargeOrderService) {
        this.rechargeOrderService = rechargeOrderService;
    }

    @PostMapping
    @Operation(summary = "创建充值订单", description = "仅 ADMIN 可创建；新订单初始状态为 PENDING")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "创建成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "广告主或账户不存在")
    })
    public ResponseEntity<ApiResponse<RechargeOrderResponse>> create(
            @Valid @RequestBody CreateRechargeOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(rechargeOrderService.create(request)));
    }

    @GetMapping("/{orderNo}")
    @Operation(summary = "查询充值订单", description = "ADMIN 和 OPERATOR 可按订单号查询当前状态")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "订单号不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "订单不存在")
    })
    public ApiResponse<RechargeOrderResponse> findByOrderNo(
            @NotBlank(message = "充值订单号不能为空")
            @Size(max = PaymentReferenceRules.ORDER_NO_MAX_LENGTH,
                    message = "充值订单号不能超过 64 个字符")
            @Pattern(regexp = PaymentReferenceRules.SAFE_REFERENCE_PATTERN,
                    message = "充值订单号格式不合法")
            @PathVariable String orderNo) {
        return ApiResponse.success(rechargeOrderService.findByOrderNo(orderNo));
    }
}

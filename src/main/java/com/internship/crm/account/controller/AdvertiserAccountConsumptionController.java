package com.internship.crm.account.controller;

import com.internship.crm.account.dto.request.CreateAccountConsumptionRequest;
import com.internship.crm.account.dto.response.AdvertiserAccountTransactionResponse;
import com.internship.crm.account.service.AdvertiserAccountConsumptionService;
import com.internship.crm.auth.security.AuthenticatedUser;
import com.internship.crm.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/advertisers/{advertiserId}/account/consumptions")
@Tag(name = "广告主账户", description = "广告主账户余额与资金流水")
@SecurityRequirement(name = "bearerAuth")
public class AdvertiserAccountConsumptionController {

    private final AdvertiserAccountConsumptionService consumptionService;

    public AdvertiserAccountConsumptionController(
            AdvertiserAccountConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    @PostMapping
    @Operation(
            summary = "创建账户消费",
            description = "仅 ADMIN 可用；按唯一业务号原子扣减余额并生成消费流水")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "消费成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "广告主、账户或投放记录不存在"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "业务号重复、余额不足或广告主不匹配")
    })
    public ResponseEntity<ApiResponse<AdvertiserAccountTransactionResponse>> consume(
            @Positive @PathVariable Long advertiserId,
            @Valid @RequestBody CreateAccountConsumptionRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(consumptionService.consume(
                        advertiserId, request, authenticatedUser.id())));
    }
}

package com.internship.crm.account.controller;

import com.internship.crm.account.dto.response.AdvertiserAccountTransactionResponse;
import com.internship.crm.account.entity.AccountTransactionType;
import com.internship.crm.account.service.AdvertiserAccountTransactionService;
import com.internship.crm.common.response.ApiResponse;
import com.internship.crm.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/advertisers/{advertiserId}/account/transactions")
@Tag(name = "广告主账户", description = "广告主账户余额与资金流水")
@SecurityRequirement(name = "bearerAuth")
public class AdvertiserAccountTransactionController {

    private final AdvertiserAccountTransactionService transactionService;

    public AdvertiserAccountTransactionController(
            AdvertiserAccountTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(
            summary = "分页查询账户流水",
            description = "ADMIN 和 OPERATOR 均可查询；支持流水类型和最多 366 天的时间范围筛选")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "筛选或分页参数不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "广告主或账户不存在")
    })
    public ApiResponse<PageResponse<AdvertiserAccountTransactionResponse>> findAll(
            @Positive @PathVariable Long advertiserId,
            @RequestParam(required = false) AccountTransactionType transactionType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime endTime,
            @Positive @RequestParam(defaultValue = "1") int page,
            @Positive @Max(100) @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(transactionService.findAll(
                advertiserId, transactionType, startTime, endTime, page, size));
    }
}

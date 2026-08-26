package com.internship.crm.account.controller;

import com.internship.crm.account.dto.response.AdvertiserAccountResponse;
import com.internship.crm.account.service.AdvertiserAccountService;
import com.internship.crm.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/advertisers/{advertiserId}/account")
@Tag(name = "广告主账户", description = "查询广告主账户余额")
@SecurityRequirement(name = "bearerAuth")
public class AdvertiserAccountController {

    private final AdvertiserAccountService advertiserAccountService;

    public AdvertiserAccountController(AdvertiserAccountService advertiserAccountService) {
        this.advertiserAccountService = advertiserAccountService;
    }

    @GetMapping
    @Operation(summary = "查询广告主账户", description = "ADMIN 和 OPERATOR 均可查询账户余额")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "广告主 ID 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "广告主或账户不存在")
    })
    public ApiResponse<AdvertiserAccountResponse> findByAdvertiserId(
            @Positive @PathVariable Long advertiserId) {
        return ApiResponse.success(advertiserAccountService.findByAdvertiserId(advertiserId));
    }
}

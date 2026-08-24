package com.internship.crm.advertiser.controller;

import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.request.UpdateAdvertiserRequest;
import com.internship.crm.advertiser.dto.request.UpdateAdvertiserStatusRequest;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.common.response.ApiResponse;
import com.internship.crm.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/advertisers")
@Tag(name = "广告主管理", description = "维护广告主企业档案、负责人、分类和启用状态")
@SecurityRequirement(name = "bearerAuth")
public class AdvertiserController {

    private final AdvertiserService advertiserService;

    public AdvertiserController(AdvertiserService advertiserService) {
        this.advertiserService = advertiserService;
    }

    @PostMapping
    @Operation(summary = "创建广告主", description = "仅 ADMIN 可用；分类和负责人必须存在且处于启用状态")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "创建成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数或关联状态不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "名称或注册编号已存在")
    })
    public ResponseEntity<ApiResponse<AdvertiserResponse>> create(
            @Valid @RequestBody CreateAdvertiserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(advertiserService.create(request)));
    }

    @GetMapping
    @Operation(summary = "分页查询广告主列表", description = "ADMIN 和 OPERATOR 均可查询，按广告主 ID 升序返回")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效")
    })
    public ApiResponse<PageResponse<AdvertiserResponse>> findAll(
            @Positive @RequestParam(defaultValue = "1") int page,
            @Positive @Max(100) @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(advertiserService.findAll(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询广告主详情", description = "ADMIN 和 OPERATOR 均可查询")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "广告主不存在")
    })
    public ApiResponse<AdvertiserResponse> findById(@Positive @PathVariable Long id) {
        return ApiResponse.success(advertiserService.findById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "局部修改广告主", description = "仅 ADMIN 可用；请求中未提供的字段保持不变")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "修改成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数或关联状态不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "广告主或关联记录不存在"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "名称或注册编号已存在")
    })
    public ApiResponse<AdvertiserResponse> update(
            @Positive @PathVariable Long id,
            @Valid @RequestBody UpdateAdvertiserRequest request) {
        return ApiResponse.success(advertiserService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "启用或禁用广告主", description = "仅 ADMIN 可用；禁用不会删除广告主或解除已有关系")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "状态修改成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "状态参数不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "广告主不存在")
    })
    public ApiResponse<AdvertiserResponse> updateStatus(
            @Positive @PathVariable Long id,
            @Valid @RequestBody UpdateAdvertiserStatusRequest request) {
        return ApiResponse.success(advertiserService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除广告主", description = "仅 ADMIN 可用；此操作会物理删除当前广告主记录")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "广告主不存在")
    })
    public ApiResponse<Void> delete(@Positive @PathVariable Long id) {
        advertiserService.delete(id);
        return ApiResponse.successWithoutData();
    }
}

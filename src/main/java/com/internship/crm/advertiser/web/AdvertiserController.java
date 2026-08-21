package com.internship.crm.advertiser.web;

import com.internship.crm.advertiser.api.AdvertiserResponse;
import com.internship.crm.advertiser.api.CreateAdvertiserRequest;
import com.internship.crm.advertiser.api.UpdateAdvertiserRequest;
import com.internship.crm.advertiser.api.UpdateAdvertiserStatusRequest;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
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
    @Operation(summary = "创建广告主")
    public ResponseEntity<ApiResponse<AdvertiserResponse>> create(
            @Valid @RequestBody CreateAdvertiserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(advertiserService.create(request)));
    }

    @GetMapping
    @Operation(summary = "查询广告主列表")
    public ApiResponse<List<AdvertiserResponse>> findAll() {
        return ApiResponse.success(advertiserService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询广告主详情")
    public ApiResponse<AdvertiserResponse> findById(@Positive @PathVariable Long id) {
        return ApiResponse.success(advertiserService.findById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "局部修改广告主")
    public ApiResponse<AdvertiserResponse> update(
            @Positive @PathVariable Long id,
            @Valid @RequestBody UpdateAdvertiserRequest request) {
        return ApiResponse.success(advertiserService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "启用或禁用广告主")
    public ApiResponse<AdvertiserResponse> updateStatus(
            @Positive @PathVariable Long id,
            @Valid @RequestBody UpdateAdvertiserStatusRequest request) {
        return ApiResponse.success(advertiserService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除广告主")
    public ApiResponse<Void> delete(@Positive @PathVariable Long id) {
        advertiserService.delete(id);
        return ApiResponse.successWithoutData();
    }
}

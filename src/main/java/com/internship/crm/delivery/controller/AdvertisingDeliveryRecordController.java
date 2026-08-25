package com.internship.crm.delivery.controller;

import com.internship.crm.common.response.ApiResponse;
import com.internship.crm.common.response.PageResponse;
import com.internship.crm.delivery.dto.request.CreateAdvertisingDeliveryRecordRequest;
import com.internship.crm.delivery.dto.request.UpdateAdvertisingDeliveryRecordRequest;
import com.internship.crm.delivery.dto.response.AdvertisingDeliveryRecordResponse;
import com.internship.crm.delivery.service.AdvertisingDeliveryRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/delivery-records")
@Tag(name = "广告投放数据", description = "录入和维护广告投放事实数据")
@SecurityRequirement(name = "bearerAuth")
public class AdvertisingDeliveryRecordController {

    private final AdvertisingDeliveryRecordService deliveryRecordService;

    public AdvertisingDeliveryRecordController(AdvertisingDeliveryRecordService deliveryRecordService) {
        this.deliveryRecordService = deliveryRecordService;
    }

    @PostMapping
    @Operation(summary = "录入投放数据", description = "ADMIN 和 OPERATOR 均可录入；外部投放记录号全局唯一")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "录入成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数、关联状态或漏斗指标不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "广告主或广告类型不存在"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "外部投放记录号已存在")
    })
    public ResponseEntity<ApiResponse<AdvertisingDeliveryRecordResponse>> create(
            @Valid @RequestBody CreateAdvertisingDeliveryRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(deliveryRecordService.create(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询投放记录详情", description = "ADMIN 和 OPERATOR 均可查询")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "投放记录不存在")
    })
    public ApiResponse<AdvertisingDeliveryRecordResponse> findById(
            @Positive @PathVariable Long id) {
        return ApiResponse.success(deliveryRecordService.findById(id));
    }

    @GetMapping
    @Operation(
            summary = "分页查询投放记录",
            description = "支持日期、广告主和广告类型组合筛选；按投放日期和 ID 倒序返回")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "分页或日期范围不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效")
    })
    public ApiResponse<PageResponse<AdvertisingDeliveryRecordResponse>> findAll(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            @Positive(message = "广告主 ID 必须为正数")
            @RequestParam(required = false)
            Long advertiserId,
            @Size(max = 30, message = "广告类型编码不能超过 30 个字符")
            @RequestParam(required = false)
            String advertisingTypeCode,
            @Positive @RequestParam(defaultValue = "1") int page,
            @Positive @Max(100) @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(deliveryRecordService.findAll(
                startDate, endDate, advertiserId, advertisingTypeCode, page, size));
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "修正投放记录",
            description = "仅 ADMIN 可用；外部投放记录号不可修改，未提供的字段保持不变")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "修正成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数、关联状态或漏斗指标不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "投放记录或关联对象不存在")
    })
    public ApiResponse<AdvertisingDeliveryRecordResponse> update(
            @Positive @PathVariable Long id,
            @Valid @RequestBody UpdateAdvertisingDeliveryRecordRequest request) {
        return ApiResponse.success(deliveryRecordService.update(id, request));
    }
}

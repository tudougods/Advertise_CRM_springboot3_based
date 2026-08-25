package com.internship.crm.delivery.controller;

import com.internship.crm.common.response.ApiResponse;
import com.internship.crm.delivery.dto.request.CreateAdvertisingDeliveryRecordRequest;
import com.internship.crm.delivery.dto.response.AdvertisingDeliveryRecordResponse;
import com.internship.crm.delivery.service.AdvertisingDeliveryRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}

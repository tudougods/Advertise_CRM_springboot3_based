package com.internship.crm.report.controller;

import com.internship.crm.common.response.ApiResponse;
import com.internship.crm.report.dto.request.DeliveryReportQuery;
import com.internship.crm.report.dto.response.DeliveryMetricsResponse;
import com.internship.crm.report.service.DeliveryReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/delivery")
@Tag(name = "投放统计报表", description = "按时间、广告主和广告类型查询投放汇总指标")
@SecurityRequirement(name = "bearerAuth")
public class DeliveryReportController {

    private final DeliveryReportService deliveryReportService;

    public DeliveryReportController(DeliveryReportService deliveryReportService) {
        this.deliveryReportService = deliveryReportService;
    }

    @GetMapping("/overview")
    @Operation(
            summary = "查询投放指标总览",
            description = "支持日期、广告主和广告类型组合筛选；均不提供日期时默认最近 30 天")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "查询条件不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效")
    })
    public ApiResponse<DeliveryMetricsResponse> overview(
            @Valid @ParameterObject @ModelAttribute DeliveryReportQuery query) {
        return ApiResponse.success(deliveryReportService.overview(query));
    }
}

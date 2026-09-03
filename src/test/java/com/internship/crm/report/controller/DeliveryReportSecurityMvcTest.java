package com.internship.crm.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.internship.crm.auth.security.JwtAuthenticationFilter;
import com.internship.crm.auth.security.RestAccessDeniedHandler;
import com.internship.crm.auth.security.RestAuthenticationEntryPoint;
import com.internship.crm.auth.token.JwtTokenService;
import com.internship.crm.common.exception.GlobalExceptionHandler;
import com.internship.crm.common.filter.RequestLoggingFilter;
import com.internship.crm.common.response.PageResponse;
import com.internship.crm.config.SecurityConfig;
import com.internship.crm.report.dto.request.DeliveryReportQuery;
import com.internship.crm.report.dto.response.AdvertiserDeliveryReportResponse;
import com.internship.crm.report.dto.response.AdvertisingTypeDeliveryReportResponse;
import com.internship.crm.report.dto.response.DeliveryMetricsResponse;
import com.internship.crm.report.dto.response.DeliveryTrendPointResponse;
import com.internship.crm.report.model.AdvertiserReportSortField;
import com.internship.crm.report.model.ReportSortDirection;
import com.internship.crm.report.service.DeliveryReportService;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.entity.User;
import com.internship.crm.user.entity.UserRole;
import com.internship.crm.user.entity.UserStatus;
import com.internship.crm.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DeliveryReportController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class,
        RequestLoggingFilter.class
})
@DisplayName("投放总览报表接口与 RBAC 权限")
@ExtendWith(ReadableTestResultExtension.class)
class DeliveryReportSecurityMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeliveryReportService deliveryReportService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("缺少 JWT 查询总览返回统一 401")
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reports/delivery/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("ADMIN 可以按组合条件查询投放总览")
    void adminCanQueryOverview() throws Exception {
        authorize("admin-report", user(1L, UserRole.ADMIN));
        when(deliveryReportService.overview(any())).thenReturn(metrics());

        mockMvc.perform(get("/api/v1/reports/delivery/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-report")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31")
                        .param("advertiserId", "7")
                        .param("advertisingTypeCode", "SEARCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.impressions").value(1000))
                .andExpect(jsonPath("$.data.spend").value(200.00))
                .andExpect(jsonPath("$.data.ctr").value(0.1000));
        verify(deliveryReportService).overview(new DeliveryReportQuery(
                java.time.LocalDate.of(2026, 8, 1),
                java.time.LocalDate.of(2026, 8, 31),
                7L,
                "SEARCH"));
    }

    @Test
    @DisplayName("OPERATOR 可以查询默认时间范围的投放总览")
    void operatorCanQueryOverview() throws Exception {
        authorize("operator-report", user(2L, UserRole.OPERATOR));
        when(deliveryReportService.overview(any())).thenReturn(DeliveryMetricsResponse.empty());

        mockMvc.perform(get("/api/v1/reports/delivery/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.impressions").value(0));
    }

    @Test
    @DisplayName("空白广告类型编码返回统一参数错误且不进入 Service")
    void blankAdvertisingTypeCodeIsRejected() throws Exception {
        authorize("admin-invalid-report", user(1L, UserRole.ADMIN));

        mockMvc.perform(get("/api/v1/reports/delivery/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-invalid-report")
                        .param("advertisingTypeCode", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"));
        verify(deliveryReportService, never()).overview(any());
    }

    @Test
    @DisplayName("OPERATOR 可以按月查询投放趋势")
    void operatorCanQueryMonthlyTrend() throws Exception {
        authorize("operator-trend", user(2L, UserRole.OPERATOR));
        when(deliveryReportService.trend(any(), any())).thenReturn(List.of(
                new DeliveryTrendPointResponse(LocalDate.of(2026, 8, 1), metrics())));

        mockMvc.perform(get("/api/v1/reports/delivery/trend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-trend")
                        .param("granularity", "MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].periodStart").value("2026-08-01"))
                .andExpect(jsonPath("$.data[0].metrics.impressions").value(1000))
                .andExpect(jsonPath("$.data[0].metrics.ctr").value(0.1000));
    }

    @Test
    @DisplayName("非法时间粒度返回统一参数错误且不进入 Service")
    void invalidGranularityIsRejected() throws Exception {
        authorize("admin-invalid-trend", user(1L, UserRole.ADMIN));

        mockMvc.perform(get("/api/v1/reports/delivery/trend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-invalid-trend")
                        .param("granularity", "YEAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"));
        verify(deliveryReportService, never()).trend(any(), any());
    }

    @Test
    @DisplayName("ADMIN 可以按指标排序并分页查询广告主汇总")
    void adminCanQueryAdvertiserDimension() throws Exception {
        authorize("admin-advertiser-report", user(1L, UserRole.ADMIN));
        AdvertiserDeliveryReportResponse item = new AdvertiserDeliveryReportResponse(
                7L, "示例广告主", metrics());
        when(deliveryReportService.byAdvertiser(
                any(), anyLong(), anyLong(), any(), any()))
                .thenReturn(PageResponse.of(List.of(item), 1, 10, 1));

        mockMvc.perform(get("/api/v1/reports/delivery/by-advertiser")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-advertiser-report")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sortBy", "CTR")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].advertiserId").value(7))
                .andExpect(jsonPath("$.data.items[0].advertiserName").value("示例广告主"))
                .andExpect(jsonPath("$.data.items[0].metrics.ctr").value(0.1000))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
        verify(deliveryReportService).byAdvertiser(
                new DeliveryReportQuery(null, null, null, null),
                1,
                10,
                AdvertiserReportSortField.CTR,
                ReportSortDirection.ASC);
    }

    @Test
    @DisplayName("OPERATOR 可以查询广告类型维度汇总")
    void operatorCanQueryAdvertisingTypeDimension() throws Exception {
        authorize("operator-type-report", user(2L, UserRole.OPERATOR));
        AdvertisingTypeDeliveryReportResponse item =
                new AdvertisingTypeDeliveryReportResponse(1L, "SEARCH", "搜索广告", metrics());
        when(deliveryReportService.byAdvertisingType(any())).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/reports/delivery/by-ad-type")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-type-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].advertisingTypeCode").value("SEARCH"))
                .andExpect(jsonPath("$.data[0].advertisingTypeName").value("搜索广告"))
                .andExpect(jsonPath("$.data[0].metrics.spend").value(200.00));
    }

    @Test
    @DisplayName("非法广告主排序字段返回统一参数错误")
    void invalidAdvertiserSortFieldIsRejected() throws Exception {
        authorize("admin-invalid-sort", user(1L, UserRole.ADMIN));

        mockMvc.perform(get("/api/v1/reports/delivery/by-advertiser")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-invalid-sort")
                        .param("sortBy", "DROP_TABLE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"));
        verify(deliveryReportService, never()).byAdvertiser(
                any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("广告主汇总分页大小超过上限时返回统一参数错误")
    void oversizedAdvertiserPageIsRejected() throws Exception {
        authorize("admin-oversized-page", user(1L, UserRole.ADMIN));

        mockMvc.perform(get("/api/v1/reports/delivery/by-advertiser")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-oversized-page")
                .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data[0].field").value("size"));
        verify(deliveryReportService, never()).byAdvertiser(
                any(), anyLong(), anyLong(), any(), any());
    }

    private DeliveryMetricsResponse metrics() {
        return new DeliveryMetricsResponse(
                1_000L,
                100L,
                10L,
                new BigDecimal("200.00"),
                new BigDecimal("0.1000"),
                new BigDecimal("0.1000"),
                new BigDecimal("2.00"));
    }

    private void authorize(String token, User user) {
        Claims claims = Jwts.claims().subject(user.getId().toString()).build();
        when(jwtTokenService.parseClaims(token)).thenReturn(claims);
        when(userService.findEntityById(user.getId())).thenReturn(Optional.of(user));
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername("report-user-" + id);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}

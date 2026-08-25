package com.internship.crm.delivery.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.internship.crm.auth.security.JwtAuthenticationFilter;
import com.internship.crm.auth.security.RestAccessDeniedHandler;
import com.internship.crm.auth.security.RestAuthenticationEntryPoint;
import com.internship.crm.auth.token.JwtTokenService;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.common.exception.GlobalExceptionHandler;
import com.internship.crm.common.filter.RequestLoggingFilter;
import com.internship.crm.common.response.PageResponse;
import com.internship.crm.config.SecurityConfig;
import com.internship.crm.delivery.dto.request.CreateAdvertisingDeliveryRecordRequest;
import com.internship.crm.delivery.dto.request.UpdateAdvertisingDeliveryRecordRequest;
import com.internship.crm.delivery.dto.response.AdvertisingDeliveryRecordResponse;
import com.internship.crm.delivery.exception.DeliveryErrorCode;
import com.internship.crm.delivery.service.AdvertisingDeliveryRecordService;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.entity.User;
import com.internship.crm.user.entity.UserRole;
import com.internship.crm.user.entity.UserStatus;
import com.internship.crm.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdvertisingDeliveryRecordController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class,
        RequestLoggingFilter.class
})
@DisplayName("广告投放记录接口与 RBAC 权限")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertisingDeliveryRecordSecurityMvcTest {

    private static final @NonNull MediaType JSON = Objects.requireNonNull(MediaType.APPLICATION_JSON);
    private static final String VALID_REQUEST = """
            {
              "externalRecordNo": "DELIVERY-001",
              "advertiserId": 7,
              "advertisingTypeCode": "SEARCH",
              "recordDate": "2026-08-26",
              "impressions": 10000,
              "clicks": 500,
              "conversions": 30,
              "spend": 300.00
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdvertisingDeliveryRecordService deliveryRecordService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("缺少 JWT 录入投放数据返回统一 401")
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/delivery-records")
                        .contentType(JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("ADMIN 可以录入投放数据")
    void adminCanCreateDeliveryRecord() throws Exception {
        authorize("admin-create-delivery", user(1L, UserRole.ADMIN));
        when(deliveryRecordService.create(any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/delivery-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-create-delivery")
                        .contentType(JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.externalRecordNo").value("DELIVERY-001"))
                .andExpect(jsonPath("$.data.advertiserName").value("示例广告主"))
                .andExpect(jsonPath("$.data.advertisingTypeCode").value("SEARCH"));
    }

    @Test
    @DisplayName("OPERATOR 可以录入投放数据")
    void operatorCanCreateDeliveryRecord() throws Exception {
        authorize("operator-create-delivery", user(2L, UserRole.OPERATOR));
        when(deliveryRecordService.create(any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/delivery-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-create-delivery")
                        .contentType(JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(11));
    }

    @Test
    @DisplayName("非法入库参数返回统一 400 且不调用 Service")
    void invalidRequestReturnsValidationError() throws Exception {
        authorize("operator-invalid-delivery", user(2L, UserRole.OPERATOR));

        mockMvc.perform(post("/api/v1/delivery-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-invalid-delivery")
                        .contentType(JSON)
                        .content("""
                                {
                                  "externalRecordNo": " ",
                                  "advertiserId": 0,
                                  "advertisingTypeCode": " ",
                                  "recordDate": null,
                                  "impressions": -1,
                                  "clicks": -1,
                                  "conversions": -1,
                                  "spend": -0.01
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"));

        verify(deliveryRecordService, never()).create(any());
    }

    @Test
    @DisplayName("重复外部记录号返回明确 409")
    void duplicateExternalRecordNumberReturnsConflict() throws Exception {
        authorize("operator-duplicate-delivery", user(2L, UserRole.OPERATOR));
        doThrow(new BusinessException(DeliveryErrorCode.EXTERNAL_RECORD_NO_ALREADY_EXISTS))
                .when(deliveryRecordService).create(any(CreateAdvertisingDeliveryRecordRequest.class));

        mockMvc.perform(post("/api/v1/delivery-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-duplicate-delivery")
                        .contentType(JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DELIVERY_EXTERNAL_RECORD_NO_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("非法漏斗关系返回明确 400")
    void invalidFunnelReturnsBadRequest() throws Exception {
        authorize("operator-invalid-funnel", user(2L, UserRole.OPERATOR));
        doThrow(new BusinessException(DeliveryErrorCode.INVALID_METRICS))
                .when(deliveryRecordService).create(any(CreateAdvertisingDeliveryRecordRequest.class));

        mockMvc.perform(post("/api/v1/delivery-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-invalid-funnel")
                        .contentType(JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DELIVERY_INVALID_METRICS"));
    }

    @Test
    @DisplayName("OPERATOR 可以组合筛选并分页查询投放记录")
    void operatorCanFilterDeliveryRecords() throws Exception {
        authorize("operator-list-delivery", user(2L, UserRole.OPERATOR));
        when(deliveryRecordService.findAll(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 26),
                7L,
                "SEARCH",
                2,
                1)).thenReturn(PageResponse.of(List.of(response()), 2, 1, 3));

        mockMvc.perform(get("/api/v1/delivery-records")
                        .queryParam("startDate", "2026-08-01")
                        .queryParam("endDate", "2026-08-26")
                        .queryParam("advertiserId", "7")
                        .queryParam("advertisingTypeCode", "SEARCH")
                        .queryParam("page", "2")
                        .queryParam("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-list-delivery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(11))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(3));

        verify(deliveryRecordService).findAll(
                eq(LocalDate.of(2026, 8, 1)),
                eq(LocalDate.of(2026, 8, 26)),
                eq(7L),
                eq("SEARCH"),
                eq(2L),
                eq(1L));
    }

    @Test
    @DisplayName("ADMIN 可以查询投放记录详情")
    void adminCanReadDeliveryRecordDetails() throws Exception {
        authorize("admin-detail-delivery", user(1L, UserRole.ADMIN));
        when(deliveryRecordService.findById(11L)).thenReturn(response());

        mockMvc.perform(get("/api/v1/delivery-records/11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-detail-delivery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.advertiserName").value("示例广告主"))
                .andExpect(jsonPath("$.data.advertisingTypeName").value("搜索广告"));
    }

    @Test
    @DisplayName("查询不存在的投放记录返回明确 404")
    void missingDeliveryRecordReturnsNotFound() throws Exception {
        authorize("operator-missing-delivery", user(2L, UserRole.OPERATOR));
        when(deliveryRecordService.findById(404L))
                .thenThrow(new BusinessException(DeliveryErrorCode.DELIVERY_RECORD_NOT_FOUND));

        mockMvc.perform(get("/api/v1/delivery-records/404")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-missing-delivery"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DELIVERY_RECORD_NOT_FOUND"));
    }

    @Test
    @DisplayName("投放记录分页数量超过上限时返回统一 400")
    void pageSizeAboveLimitIsRejected() throws Exception {
        authorize("operator-invalid-delivery-page", user(2L, UserRole.OPERATOR));

        mockMvc.perform(get("/api/v1/delivery-records")
                        .queryParam("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-invalid-delivery-page"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"));

        verify(deliveryRecordService, never()).findAll(any(), any(), any(), any(), any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("ADMIN 可以局部修正投放记录")
    void adminCanUpdateDeliveryRecord() throws Exception {
        authorize("admin-update-delivery", user(1L, UserRole.ADMIN));
        when(deliveryRecordService.update(eq(11L), any(UpdateAdvertisingDeliveryRecordRequest.class)))
                .thenReturn(response());

        mockMvc.perform(patch("/api/v1/delivery-records/11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-update-delivery")
                        .contentType(JSON)
                        .content("""
                                {
                                  "advertisingTypeCode": "VIDEO",
                                  "impressions": 20000,
                                  "clicks": 800,
                                  "conversions": 40,
                                  "spend": 500.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11));
    }

    @Test
    @DisplayName("OPERATOR 不能修正投放记录")
    void operatorCannotUpdateDeliveryRecord() throws Exception {
        authorize("operator-update-delivery", user(2L, UserRole.OPERATOR));

        mockMvc.perform(patch("/api/v1/delivery-records/11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-update-delivery")
                        .contentType(JSON)
                        .content("{\"spend\":500.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        verify(deliveryRecordService, never()).update(any(), any());
    }

    @Test
    @DisplayName("非法局部修正参数返回统一 400")
    void invalidUpdateRequestReturnsValidationError() throws Exception {
        authorize("admin-invalid-update-delivery", user(1L, UserRole.ADMIN));

        mockMvc.perform(patch("/api/v1/delivery-records/11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-invalid-update-delivery")
                        .contentType(JSON)
                        .content("""
                                {
                                  "advertiserId": 0,
                                  "advertisingTypeCode": " ",
                                  "impressions": -1,
                                  "spend": -0.01
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"));

        verify(deliveryRecordService, never()).update(any(), any());
    }

    @Test
    @DisplayName("空的局部修正请求返回明确 400")
    void emptyUpdateRequestReturnsBadRequest() throws Exception {
        authorize("admin-empty-update-delivery", user(1L, UserRole.ADMIN));
        when(deliveryRecordService.update(eq(11L), any(UpdateAdvertisingDeliveryRecordRequest.class)))
                .thenThrow(new BusinessException(DeliveryErrorCode.NO_FIELDS_TO_UPDATE));

        mockMvc.perform(patch("/api/v1/delivery-records/11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-empty-update-delivery")
                        .contentType(JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DELIVERY_NO_FIELDS_TO_UPDATE"));
    }

    @Test
    @DisplayName("ADMIN 可以删除未结算的投放记录")
    void adminCanDeleteDeliveryRecord() throws Exception {
        authorize("admin-delete-delivery", user(1L, UserRole.ADMIN));

        mockMvc.perform(delete("/api/v1/delivery-records/11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-delete-delivery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(deliveryRecordService).delete(11L);
    }

    @Test
    @DisplayName("OPERATOR 不能删除投放记录")
    void operatorCannotDeleteDeliveryRecord() throws Exception {
        authorize("operator-delete-delivery", user(2L, UserRole.OPERATOR));

        mockMvc.perform(delete("/api/v1/delivery-records/11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-delete-delivery"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        verify(deliveryRecordService, never()).delete(any());
    }

    @Test
    @DisplayName("已关联资金流水的投放记录删除返回明确 409")
    void referencedDeliveryRecordReturnsConflict() throws Exception {
        authorize("admin-delete-referenced-delivery", user(1L, UserRole.ADMIN));
        doThrow(new BusinessException(DeliveryErrorCode.DELIVERY_RECORD_IN_USE))
                .when(deliveryRecordService).delete(11L);

        mockMvc.perform(delete("/api/v1/delivery-records/11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-delete-referenced-delivery"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERY_RECORD_IN_USE"));
    }

    @Test
    @DisplayName("删除不存在的投放记录返回明确 404")
    void deletingMissingDeliveryRecordReturnsNotFound() throws Exception {
        authorize("admin-delete-missing-delivery", user(1L, UserRole.ADMIN));
        doThrow(new BusinessException(DeliveryErrorCode.DELIVERY_RECORD_NOT_FOUND))
                .when(deliveryRecordService).delete(404L);

        mockMvc.perform(delete("/api/v1/delivery-records/404")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-delete-missing-delivery"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DELIVERY_RECORD_NOT_FOUND"));
    }

    private void authorize(String token, User user) {
        Claims claims = Jwts.claims().subject(user.getId().toString()).build();
        when(jwtTokenService.parseClaims(token)).thenReturn(claims);
        when(userService.findEntityById(user.getId())).thenReturn(Optional.of(user));
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private AdvertisingDeliveryRecordResponse response() {
        OffsetDateTime now = OffsetDateTime.now();
        return new AdvertisingDeliveryRecordResponse(
                11L,
                "DELIVERY-001",
                7L,
                "示例广告主",
                3L,
                "SEARCH",
                "搜索广告",
                LocalDate.of(2026, 8, 26),
                10_000L,
                500L,
                30L,
                new BigDecimal("300.00"),
                now,
                now);
    }
}

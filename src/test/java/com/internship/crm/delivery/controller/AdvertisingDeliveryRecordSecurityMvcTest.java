package com.internship.crm.delivery.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.internship.crm.auth.security.JwtAuthenticationFilter;
import com.internship.crm.auth.security.RestAccessDeniedHandler;
import com.internship.crm.auth.security.RestAuthenticationEntryPoint;
import com.internship.crm.auth.token.JwtTokenService;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.common.exception.GlobalExceptionHandler;
import com.internship.crm.common.filter.RequestLoggingFilter;
import com.internship.crm.config.SecurityConfig;
import com.internship.crm.delivery.dto.request.CreateAdvertisingDeliveryRecordRequest;
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
@DisplayName("投放数据入库接口与 RBAC 权限")
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

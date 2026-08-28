package com.internship.crm.delivery.controller;

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
import com.internship.crm.config.SecurityConfig;
import com.internship.crm.delivery.dto.response.AdvertisingTypeResponse;
import com.internship.crm.delivery.entity.AdvertisingTypeStatus;
import com.internship.crm.delivery.service.AdvertisingTypeService;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.entity.User;
import com.internship.crm.user.entity.UserRole;
import com.internship.crm.user.entity.UserStatus;
import com.internship.crm.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.OffsetDateTime;
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

@WebMvcTest(controllers = AdvertisingTypeController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class,
        RequestLoggingFilter.class
})
@DisplayName("广告类型查询接口与 RBAC 权限")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertisingTypeSecurityMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdvertisingTypeService advertisingTypeService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("缺少 JWT 查询广告类型返回统一 401")
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/advertising-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("OPERATOR 可以查询广告类型字典")
    void operatorCanListAdvertisingTypes() throws Exception {
        authorize("operator-types", user(2L, UserRole.OPERATOR));
        OffsetDateTime now = OffsetDateTime.now();
        when(advertisingTypeService.findAll()).thenReturn(List.of(
                new AdvertisingTypeResponse(
                        1L, "SEARCH", "搜索广告", AdvertisingTypeStatus.ACTIVE, now, now)));

        mockMvc.perform(get("/api/v1/advertising-types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].code").value("SEARCH"))
                .andExpect(jsonPath("$.data[0].name").value("搜索广告"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("ADMIN 可以查询广告类型字典")
    void adminCanListAdvertisingTypes() throws Exception {
        authorize("admin-types", user(1L, UserRole.ADMIN));
        when(advertisingTypeService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/advertising-types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
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
}

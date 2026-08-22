package com.internship.crm.advertiser.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.internship.crm.advertiser.dto.response.AdvertiserCategoryResponse;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.dto.request.CreateAdvertiserCategoryRequest;
import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.request.UpdateAdvertiserRequest;
import com.internship.crm.advertiser.dto.request.UpdateAdvertiserCategoryRequest;
import com.internship.crm.advertiser.entity.AdvertiserStatus;
import com.internship.crm.advertiser.service.AdvertiserCategoryService;
import com.internship.crm.advertiser.service.AdvertiserService;
import com.internship.crm.auth.security.JwtAuthenticationFilter;
import com.internship.crm.auth.security.RestAccessDeniedHandler;
import com.internship.crm.auth.security.RestAuthenticationEntryPoint;
import com.internship.crm.auth.token.JwtTokenService;
import com.internship.crm.common.exception.GlobalExceptionHandler;
import com.internship.crm.common.filter.RequestLoggingFilter;
import com.internship.crm.config.SecurityConfig;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.entity.User;
import com.internship.crm.user.entity.UserRole;
import com.internship.crm.user.entity.UserStatus;
import com.internship.crm.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.OffsetDateTime;
import java.util.List;
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

@WebMvcTest(controllers = {AdvertiserController.class, AdvertiserCategoryController.class})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class,
        RequestLoggingFilter.class
})
@DisplayName("广告主管理接口与 RBAC 权限")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertiserSecurityMvcTest {

    private static final @NonNull MediaType JSON = Objects.requireNonNull(MediaType.APPLICATION_JSON);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdvertiserService advertiserService;

    @MockitoBean
    private AdvertiserCategoryService categoryService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("缺少 JWT 访问广告主接口返回统一 401")
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/advertisers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("OPERATOR 可以查询广告主列表")
    void operatorCanListAdvertisers() throws Exception {
        authorize("operator-list", user(2L, UserRole.OPERATOR));
        when(advertiserService.findAll()).thenReturn(List.of(advertiserResponse(10L, AdvertiserStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/advertisers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("ADMIN 可以查询广告主详情")
    void adminCanReadAnAdvertiser() throws Exception {
        authorize("admin-read-advertiser", user(1L, UserRole.ADMIN));
        when(advertiserService.findById(14L))
                .thenReturn(advertiserResponse(14L, AdvertiserStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/advertisers/14")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-read-advertiser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(14));
    }

    @Test
    @DisplayName("OPERATOR 不能创建广告主")
    void operatorCannotCreateAdvertisers() throws Exception {
        authorize("operator-create", user(2L, UserRole.OPERATOR));

        mockMvc.perform(post("/api/v1/advertisers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-create")
                        .contentType(JSON)
                        .content("{\"name\":\"示例广告主\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        verify(advertiserService, never()).create(any(CreateAdvertiserRequest.class));
    }

    @Test
    @DisplayName("ADMIN 可以创建广告主")
    void adminCanCreateAnAdvertiser() throws Exception {
        authorize("admin-create", user(1L, UserRole.ADMIN));
        when(advertiserService.create(any(CreateAdvertiserRequest.class)))
                .thenReturn(advertiserResponse(11L, AdvertiserStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/advertisers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-create")
                        .contentType(JSON)
                        .content("{\"name\":\"示例广告主\",\"registrationNo\":\"REG-011\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(11));
    }

    @Test
    @DisplayName("ADMIN 可以启用或禁用广告主")
    void adminCanUpdateAdvertiserStatus() throws Exception {
        authorize("admin-status", user(1L, UserRole.ADMIN));
        when(advertiserService.updateStatus(12L, AdvertiserStatus.DISABLED))
                .thenReturn(advertiserResponse(12L, AdvertiserStatus.DISABLED));

        mockMvc.perform(patch("/api/v1/advertisers/12/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-status")
                        .contentType(JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    @DisplayName("ADMIN 可以局部修改并解除广告主关联")
    void adminCanUpdateAndClearAdvertiserRelations() throws Exception {
        authorize("admin-update-advertiser", user(1L, UserRole.ADMIN));
        when(advertiserService.update(any(Long.class), any(UpdateAdvertiserRequest.class)))
                .thenReturn(advertiserResponse(15L, AdvertiserStatus.ACTIVE));

        mockMvc.perform(patch("/api/v1/advertisers/15")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-update-advertiser")
                        .contentType(JSON)
                        .content("{\"clearCategory\":true,\"clearOwner\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(15));
    }

    @Test
    @DisplayName("ADMIN 可以删除广告主")
    void adminCanDeleteAnAdvertiser() throws Exception {
        authorize("admin-delete", user(1L, UserRole.ADMIN));

        mockMvc.perform(delete("/api/v1/advertisers/13")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(advertiserService).delete(13L);
    }

    @Test
    @DisplayName("非法广告主请求由统一校验响应拒绝")
    void invalidAdvertiserRequestReturnsValidationError() throws Exception {
        authorize("admin-invalid", user(1L, UserRole.ADMIN));

        mockMvc.perform(post("/api/v1/advertisers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-invalid")
                        .contentType(JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("非正数的分类和负责人 ID 被统一校验拒绝")
    void nonPositiveRelationIdsReturnValidationError() throws Exception {
        authorize("admin-invalid-relations", user(1L, UserRole.ADMIN));

        mockMvc.perform(post("/api/v1/advertisers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-invalid-relations")
                        .contentType(JSON)
                        .content("{\"name\":\"非法关联\",\"categoryId\":0,\"ownerUserId\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("换绑和解除同一关系的冲突请求被拒绝")
    void conflictingRelationChangesReturnValidationError() throws Exception {
        authorize("admin-conflicting-relations", user(1L, UserRole.ADMIN));

        mockMvc.perform(patch("/api/v1/advertisers/15")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-conflicting-relations")
                        .contentType(JSON)
                        .content("{\"categoryId\":2,\"clearCategory\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_ERROR"));

        verify(advertiserService, never()).update(any(Long.class), any(UpdateAdvertiserRequest.class));
    }

    @Test
    @DisplayName("OPERATOR 可以查询广告主分类")
    void operatorCanListCategories() throws Exception {
        authorize("operator-categories", user(2L, UserRole.OPERATOR));
        when(categoryService.findAll()).thenReturn(List.of(categoryResponse(20L, AdvertiserStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/advertiser-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(20));
    }

    @Test
    @DisplayName("ADMIN 可以查询广告主分类详情")
    void adminCanReadACategory() throws Exception {
        authorize("admin-category-read", user(1L, UserRole.ADMIN));
        when(categoryService.findById(24L))
                .thenReturn(categoryResponse(24L, AdvertiserStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/advertiser-categories/24")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-category-read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(24));
    }

    @Test
    @DisplayName("OPERATOR 不能维护广告主分类")
    void operatorCannotCreateCategories() throws Exception {
        authorize("operator-category-create", user(2L, UserRole.OPERATOR));

        mockMvc.perform(post("/api/v1/advertiser-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-category-create")
                        .contentType(JSON)
                        .content("{\"name\":\"电商\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        verify(categoryService, never()).create(any(CreateAdvertiserCategoryRequest.class));
    }

    @Test
    @DisplayName("ADMIN 可以创建广告主分类")
    void adminCanCreateACategory() throws Exception {
        authorize("admin-category-create", user(1L, UserRole.ADMIN));
        when(categoryService.create(any(CreateAdvertiserCategoryRequest.class)))
                .thenReturn(categoryResponse(21L, AdvertiserStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/advertiser-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-category-create")
                        .contentType(JSON)
                        .content("{\"name\":\"电商\",\"sortOrder\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(21));
    }

    @Test
    @DisplayName("ADMIN 可以修改广告主分类")
    void adminCanUpdateACategory() throws Exception {
        authorize("admin-category-update", user(1L, UserRole.ADMIN));
        when(categoryService.update(any(Long.class), any(UpdateAdvertiserCategoryRequest.class)))
                .thenReturn(categoryResponse(22L, AdvertiserStatus.DISABLED));

        mockMvc.perform(patch("/api/v1/advertiser-categories/22")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-category-update")
                        .contentType(JSON)
                        .content("{\"status\":\"DISABLED\",\"sortOrder\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    @DisplayName("ADMIN 可以删除广告主分类")
    void adminCanDeleteACategory() throws Exception {
        authorize("admin-category-delete", user(1L, UserRole.ADMIN));

        mockMvc.perform(delete("/api/v1/advertiser-categories/23")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-category-delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(categoryService).delete(23L);
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

    private AdvertiserResponse advertiserResponse(Long id, AdvertiserStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        return new AdvertiserResponse(
                id, "advertiser" + id, "REG-" + id, null, null, status,
                null, null, null, now, now);
    }

    private AdvertiserCategoryResponse categoryResponse(Long id, AdvertiserStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        return new AdvertiserCategoryResponse(id, "category" + id, null, status, 0, now, now);
    }
}

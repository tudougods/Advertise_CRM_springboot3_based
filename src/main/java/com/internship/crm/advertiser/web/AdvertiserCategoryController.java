package com.internship.crm.advertiser.web;

import com.internship.crm.advertiser.api.AdvertiserCategoryResponse;
import com.internship.crm.advertiser.api.CreateAdvertiserCategoryRequest;
import com.internship.crm.advertiser.api.UpdateAdvertiserCategoryRequest;
import com.internship.crm.advertiser.service.AdvertiserCategoryService;
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
@RequestMapping("/api/v1/advertiser-categories")
@Tag(name = "广告主分类管理", description = "维护广告主分类、状态和展示顺序")
@SecurityRequirement(name = "bearerAuth")
public class AdvertiserCategoryController {

    private final AdvertiserCategoryService categoryService;

    public AdvertiserCategoryController(AdvertiserCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @Operation(summary = "创建广告主分类", description = "仅 ADMIN 可用；分类名称不区分大小写唯一")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "创建成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "分类名称已存在")
    })
    public ResponseEntity<ApiResponse<AdvertiserCategoryResponse>> create(
            @Valid @RequestBody CreateAdvertiserCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(categoryService.create(request)));
    }

    @GetMapping
    @Operation(summary = "查询广告主分类列表", description = "ADMIN 和 OPERATOR 均可查询，按展示顺序和 ID 升序返回")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效")
    })
    public ApiResponse<List<AdvertiserCategoryResponse>> findAll() {
        return ApiResponse.success(categoryService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询广告主分类详情", description = "ADMIN 和 OPERATOR 均可查询")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "分类不存在")
    })
    public ApiResponse<AdvertiserCategoryResponse> findById(@Positive @PathVariable Long id) {
        return ApiResponse.success(categoryService.findById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "局部修改广告主分类", description = "仅 ADMIN 可用；可修改名称、说明、状态和展示顺序")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "修改成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数不合法"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "分类不存在"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "分类名称已存在")
    })
    public ApiResponse<AdvertiserCategoryResponse> update(
            @Positive @PathVariable Long id,
            @Valid @RequestBody UpdateAdvertiserCategoryRequest request) {
        return ApiResponse.success(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除广告主分类", description = "仅 ADMIN 可用；已有广告主的 categoryId 将由数据库置为 null")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "分类不存在")
    })
    public ApiResponse<Void> delete(@Positive @PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.successWithoutData();
    }
}

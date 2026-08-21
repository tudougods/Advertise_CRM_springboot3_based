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
    @Operation(summary = "创建广告主分类")
    public ResponseEntity<ApiResponse<AdvertiserCategoryResponse>> create(
            @Valid @RequestBody CreateAdvertiserCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(categoryService.create(request)));
    }

    @GetMapping
    @Operation(summary = "查询广告主分类列表")
    public ApiResponse<List<AdvertiserCategoryResponse>> findAll() {
        return ApiResponse.success(categoryService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询广告主分类详情")
    public ApiResponse<AdvertiserCategoryResponse> findById(@Positive @PathVariable Long id) {
        return ApiResponse.success(categoryService.findById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "局部修改广告主分类")
    public ApiResponse<AdvertiserCategoryResponse> update(
            @Positive @PathVariable Long id,
            @Valid @RequestBody UpdateAdvertiserCategoryRequest request) {
        return ApiResponse.success(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除广告主分类")
    public ApiResponse<Void> delete(@Positive @PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.successWithoutData();
    }
}

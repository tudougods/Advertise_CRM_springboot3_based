package com.internship.crm.delivery.controller;

import com.internship.crm.common.response.ApiResponse;
import com.internship.crm.delivery.dto.response.AdvertisingTypeResponse;
import com.internship.crm.delivery.service.AdvertisingTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/advertising-types")
@Tag(name = "广告类型", description = "查询投放数据使用的广告类型字典")
@SecurityRequirement(name = "bearerAuth")
public class AdvertisingTypeController {

    private final AdvertisingTypeService advertisingTypeService;

    public AdvertisingTypeController(AdvertisingTypeService advertisingTypeService) {
        this.advertisingTypeService = advertisingTypeService;
    }

    @GetMapping
    @Operation(summary = "查询广告类型", description = "ADMIN 和 OPERATOR 均可查询，按广告类型 ID 升序返回")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 JWT 无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "角色权限不足")
    })
    public ApiResponse<List<AdvertisingTypeResponse>> findAll() {
        return ApiResponse.success(advertisingTypeService.findAll());
    }
}

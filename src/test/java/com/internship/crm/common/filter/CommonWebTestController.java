package com.internship.crm.common.filter;

import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import com.internship.crm.common.response.ApiResponse;
import com.internship.crm.common.exception.CommonErrorCode;
import com.internship.crm.common.exception.BusinessException;

/**
 * Test-only endpoints used to verify the common web contract.
 */
@RestController
@Validated
@RequestMapping("/test/common")
public class CommonWebTestController {

    @GetMapping("/success")
    ApiResponse<Map<String, String>> success() {
        return ApiResponse.success(Map.of("name", "crm"));
    }

    @GetMapping("/required-parameter")
    ApiResponse<String> requiredParameter(@RequestParam String query) {
        return ApiResponse.success(query);
    }

    @PostMapping("/validate")
    ApiResponse<TestRequest> validate(@Valid @RequestBody TestRequest request) {
        return ApiResponse.success(request);
    }

    @GetMapping("/type/{id}")
    ApiResponse<Integer> type(@PathVariable int id) {
        return ApiResponse.success(id);
    }

    @GetMapping("/business-error")
    ApiResponse<Void> businessError() {
        throw new BusinessException(CommonErrorCode.CONFLICT, "测试资源已存在");
    }

    @GetMapping("/unexpected-error")
    ApiResponse<Void> unexpectedError() {
        throw new IllegalStateException("sensitive internal detail");
    }

    record TestRequest(@NotBlank(message = "名称不能为空") String name) {
    }
}

@RestController
@RequestMapping("/test/method-validation")
class CommonMethodValidationTestController {

    @GetMapping
    ApiResponse<Integer> validateMethodParameter(
            @Positive(message = "页码必须为正数") @RequestParam int page) {
        return ApiResponse.success(page);
    }
}

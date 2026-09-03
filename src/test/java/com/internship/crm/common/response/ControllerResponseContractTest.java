package com.internship.crm.common.response;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.internship.crm.testsupport.ReadableTestResultExtension;

@DisplayName("Controller 统一响应契约")
@ExtendWith(ReadableTestResultExtension.class)
class ControllerResponseContractTest {

    private static final String APPLICATION_PACKAGE = "com.internship.crm";

    @Test
    @DisplayName("所有业务接口均返回统一 API 响应")
    void everyMappedControllerMethodReturnsTheApiEnvelope() {
        List<Method> endpointMethods = findControllerClasses().stream()
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                .sorted(Comparator.comparing(Method::toGenericString))
                .toList();

        assertFalse(endpointMethods.isEmpty(), "至少应扫描到一个业务接口");
        assertAll(endpointMethods.stream()
                .map(method -> () -> assertTrue(
                        isApiEnvelope(method.getGenericReturnType()),
                        () -> method.toGenericString() + " 必须返回 ApiResponse 或 ResponseEntity<ApiResponse>")));
    }

    private List<Class<?>> findControllerClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        ClassLoader classLoader = ControllerResponseContractTest.class.getClassLoader();
        List<Class<?>> controllerClasses = new ArrayList<>();
        scanner.findCandidateComponents(APPLICATION_PACKAGE).forEach(candidate ->
                controllerClasses.add(loadClass(candidate.getBeanClassName(), classLoader)));
        return List.copyOf(controllerClasses);
    }

    private Class<?> loadClass(String className, ClassLoader classLoader) {
        try {
            return ClassUtils.forName(className, classLoader);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("无法加载 Controller: " + className, exception);
        }
    }

    private boolean isApiEnvelope(Type returnType) {
        if (!(returnType instanceof ParameterizedType parameterizedType)) {
            return false;
        }
        if (parameterizedType.getRawType().equals(ApiResponse.class)) {
            return true;
        }
        if (!parameterizedType.getRawType().equals(ResponseEntity.class)) {
            return false;
        }
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        return typeArguments.length == 1 && isApiEnvelope(typeArguments[0]);
    }
}

package com.internship.crm.advertiser.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.internship.crm.advertiser.api.AdvertiserCategoryResponse;
import com.internship.crm.advertiser.api.CreateAdvertiserCategoryRequest;
import com.internship.crm.advertiser.api.UpdateAdvertiserCategoryRequest;
import com.internship.crm.advertiser.domain.AdvertiserCategory;
import com.internship.crm.advertiser.domain.AdvertiserStatus;
import com.internship.crm.advertiser.error.AdvertiserErrorCode;
import com.internship.crm.advertiser.mapper.AdvertiserCategoryMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("广告主分类 Service 业务规则")
@ExtendWith({MockitoExtension.class, ReadableTestResultExtension.class})
class AdvertiserCategoryServiceTest {

    @Mock
    private AdvertiserCategoryMapper categoryMapper;

    private AdvertiserCategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new AdvertiserCategoryService(categoryMapper);
    }

    @Test
    @DisplayName("创建分类会规范化字段并使用默认状态和顺序")
    void createNormalizesFieldsAndUsesDefaults() {
        when(categoryMapper.insert(any(AdvertiserCategory.class))).thenAnswer(invocation -> {
            AdvertiserCategory inserted = invocation.getArgument(0);
            inserted.setId(1L);
            return 1;
        });

        AdvertiserCategoryResponse response = categoryService.create(
                new CreateAdvertiserCategoryRequest("  电商  ", "   ", null, null));

        ArgumentCaptor<AdvertiserCategory> captor = ArgumentCaptor.forClass(AdvertiserCategory.class);
        verify(categoryMapper).insert(captor.capture());
        AdvertiserCategory inserted = captor.getValue();
        assertAll(
                () -> assertEquals("电商", inserted.getName()),
                () -> assertNull(inserted.getDescription()),
                () -> assertEquals(AdvertiserStatus.ACTIVE, inserted.getStatus()),
                () -> assertEquals(0, inserted.getSortOrder()),
                () -> assertEquals(1L, response.id()));
    }

    @Test
    @DisplayName("重复分类名称被拒绝")
    void duplicateNameIsRejected() {
        when(categoryMapper.findByNameIgnoreCase("电商"))
                .thenReturn(Optional.of(category(1L, "电商", AdvertiserStatus.ACTIVE, 0)));

        BusinessException exception = assertThrows(BusinessException.class, () -> categoryService.create(
                new CreateAdvertiserCategoryRequest("电商", null, null, null)));

        assertSame(AdvertiserErrorCode.CATEGORY_NAME_ALREADY_EXISTS, exception.errorCode());
        verify(categoryMapper, never()).insert(any(AdvertiserCategory.class));
    }

    @Test
    @DisplayName("分类列表和详情返回稳定响应")
    void listAndDetailReturnCategoryResponses() {
        AdvertiserCategory first = category(1L, "教育", AdvertiserStatus.ACTIVE, 1);
        AdvertiserCategory second = category(2L, "游戏", AdvertiserStatus.DISABLED, 2);
        when(categoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));
        when(categoryMapper.selectById(1L)).thenReturn(first);

        List<AdvertiserCategoryResponse> list = categoryService.findAll();
        AdvertiserCategoryResponse detail = categoryService.findById(1L);

        assertAll(
                () -> assertEquals(List.of("教育", "游戏"),
                        list.stream().map(AdvertiserCategoryResponse::name).toList()),
                () -> assertEquals("教育", detail.name()));
    }

    @Test
    @DisplayName("局部修改分类可更新名称、清空说明、禁用并改变顺序")
    void updateChangesProvidedFields() {
        AdvertiserCategory existing = category(3L, "旧分类", AdvertiserStatus.ACTIVE, 1);
        existing.setDescription("旧说明");
        OffsetDateTime originalUpdatedAt = existing.getUpdatedAt();
        when(categoryMapper.selectById(3L)).thenReturn(existing);

        AdvertiserCategoryResponse response = categoryService.update(3L,
                new UpdateAdvertiserCategoryRequest("  新分类  ", " ", AdvertiserStatus.DISABLED, 9));

        assertAll(
                () -> assertEquals("新分类", response.name()),
                () -> assertNull(response.description()),
                () -> assertEquals(AdvertiserStatus.DISABLED, response.status()),
                () -> assertEquals(9, response.sortOrder()),
                () -> assertFalse(existing.getUpdatedAt().isBefore(originalUpdatedAt)));
        verify(categoryMapper).updateById(existing);
    }

    @Test
    @DisplayName("空的分类局部修改请求被拒绝")
    void emptyUpdateIsRejected() {
        UpdateAdvertiserCategoryRequest empty = new UpdateAdvertiserCategoryRequest(null, null, null, null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> categoryService.update(1L, empty));

        assertSame(AdvertiserErrorCode.CATEGORY_NO_FIELDS_TO_UPDATE, exception.errorCode());
        verify(categoryMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("不存在的分类返回明确错误")
    void missingCategoryReturnsNotFound() {
        when(categoryMapper.selectById(404L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> categoryService.findById(404L));

        assertSame(AdvertiserErrorCode.CATEGORY_NOT_FOUND, exception.errorCode());
    }

    @Test
    @DisplayName("删除分类前确认记录存在")
    void deleteChecksExistence() {
        when(categoryMapper.selectById(4L))
                .thenReturn(category(4L, "待删除", AdvertiserStatus.ACTIVE, 0));

        categoryService.delete(4L);

        verify(categoryMapper).deleteById(4L);
    }

    private AdvertiserCategory category(
            Long id, String name, AdvertiserStatus status, int sortOrder) {
        OffsetDateTime now = OffsetDateTime.now().minusMinutes(1);
        AdvertiserCategory category = new AdvertiserCategory();
        category.setId(id);
        category.setName(name);
        category.setStatus(status);
        category.setSortOrder(sortOrder);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        return category;
    }
}

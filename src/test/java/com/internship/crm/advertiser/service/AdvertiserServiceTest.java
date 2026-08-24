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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.internship.crm.advertiser.dto.response.AdvertiserResponse;
import com.internship.crm.advertiser.dto.request.CreateAdvertiserRequest;
import com.internship.crm.advertiser.dto.request.UpdateAdvertiserRequest;
import com.internship.crm.advertiser.entity.Advertiser;
import com.internship.crm.advertiser.entity.AdvertiserCategory;
import com.internship.crm.advertiser.entity.AdvertiserStatus;
import com.internship.crm.advertiser.exception.AdvertiserErrorCode;
import com.internship.crm.advertiser.mapper.AdvertiserCategoryMapper;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.common.response.PageResponse;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.entity.User;
import com.internship.crm.user.entity.UserStatus;
import com.internship.crm.user.mapper.UserMapper;
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

@DisplayName("广告主 Service 业务规则")
@ExtendWith({MockitoExtension.class, ReadableTestResultExtension.class})
class AdvertiserServiceTest {

    @Mock
    private AdvertiserMapper advertiserMapper;

    @Mock
    private AdvertiserCategoryMapper categoryMapper;

    @Mock
    private UserMapper userMapper;

    private AdvertiserService advertiserService;

    @BeforeEach
    void setUp() {
        advertiserService = new AdvertiserService(advertiserMapper, categoryMapper, userMapper);
    }

    @Test
    @DisplayName("创建广告主会规范化字段、使用默认状态并校验关联")
    void createNormalizesFieldsAndValidatesRelations() {
        when(categoryMapper.selectById(2L)).thenReturn(category(2L, AdvertiserStatus.ACTIVE));
        when(userMapper.selectById(3L)).thenReturn(user(3L, UserStatus.ACTIVE));
        when(advertiserMapper.insert(any(Advertiser.class))).thenAnswer(invocation -> {
            Advertiser inserted = invocation.getArgument(0);
            inserted.setId(10L);
            return 1;
        });

        AdvertiserResponse response = advertiserService.create(new CreateAdvertiserRequest(
                "  示例科技  ",
                "  REG-001  ",
                2L,
                3L,
                null,
                "  https://example.com  ",
                "   ",
                "  重点客户  "));

        ArgumentCaptor<Advertiser> captor = ArgumentCaptor.forClass(Advertiser.class);
        verify(advertiserMapper).insert(captor.capture());
        Advertiser inserted = captor.getValue();
        assertAll(
                () -> assertEquals("示例科技", inserted.getName()),
                () -> assertEquals("REG-001", inserted.getRegistrationNo()),
                () -> assertEquals(2L, inserted.getCategoryId()),
                () -> assertEquals(3L, inserted.getOwnerUserId()),
                () -> assertEquals(AdvertiserStatus.ACTIVE, inserted.getStatus()),
                () -> assertEquals("https://example.com", inserted.getWebsite()),
                () -> assertNull(inserted.getAddress()),
                () -> assertEquals("重点客户", inserted.getDescription()),
                () -> assertEquals(10L, response.id()));
    }

    @Test
    @DisplayName("重复广告主名称被拒绝且不会写入数据库")
    void duplicateNameIsRejected() {
        when(advertiserMapper.findByNameIgnoreCase("Existing"))
                .thenReturn(Optional.of(advertiser(1L, "existing", AdvertiserStatus.ACTIVE)));

        BusinessException exception = assertThrows(BusinessException.class, () -> advertiserService.create(
                createRequest("Existing", null, null, null)));

        assertSame(AdvertiserErrorCode.ADVERTISER_NAME_ALREADY_EXISTS, exception.errorCode());
        verify(advertiserMapper, never()).insert(any(Advertiser.class));
    }

    @Test
    @DisplayName("重复注册编号被拒绝且不会写入数据库")
    void duplicateRegistrationNumberIsRejected() {
        when(advertiserMapper.findByRegistrationNo("REG-001"))
                .thenReturn(Optional.of(advertiser(1L, "existing", AdvertiserStatus.ACTIVE)));

        BusinessException exception = assertThrows(BusinessException.class, () -> advertiserService.create(
                createRequest("New", "REG-001", null, null)));

        assertSame(AdvertiserErrorCode.REGISTRATION_NO_ALREADY_EXISTS, exception.errorCode());
        verify(advertiserMapper, never()).insert(any(Advertiser.class));
    }

    @Test
    @DisplayName("不存在或已禁用的分类不能分配给广告主")
    void missingOrDisabledCategoryCannotBeAssigned() {
        when(categoryMapper.selectById(404L)).thenReturn(null);
        when(categoryMapper.selectById(5L)).thenReturn(category(5L, AdvertiserStatus.DISABLED));

        BusinessException missing = assertThrows(BusinessException.class, () -> advertiserService.create(
                createRequest("Missing category", null, 404L, null)));
        BusinessException disabled = assertThrows(BusinessException.class, () -> advertiserService.create(
                createRequest("Disabled category", null, 5L, null)));

        assertAll(
                () -> assertSame(AdvertiserErrorCode.CATEGORY_NOT_FOUND, missing.errorCode()),
                () -> assertSame(AdvertiserErrorCode.CATEGORY_DISABLED, disabled.errorCode()));
        verify(advertiserMapper, never()).insert(any(Advertiser.class));
    }

    @Test
    @DisplayName("不存在或已禁用的负责人不能分配给广告主")
    void missingOrDisabledOwnerCannotBeAssigned() {
        when(userMapper.selectById(404L)).thenReturn(null);
        when(userMapper.selectById(6L)).thenReturn(user(6L, UserStatus.DISABLED));

        BusinessException missing = assertThrows(BusinessException.class, () -> advertiserService.create(
                createRequest("Missing owner", null, null, 404L)));
        BusinessException disabled = assertThrows(BusinessException.class, () -> advertiserService.create(
                createRequest("Disabled owner", null, null, 6L)));

        assertAll(
                () -> assertSame(AdvertiserErrorCode.OWNER_NOT_FOUND, missing.errorCode()),
                () -> assertSame(AdvertiserErrorCode.OWNER_DISABLED, disabled.errorCode()));
        verify(advertiserMapper, never()).insert(any(Advertiser.class));
    }

    @Test
    @DisplayName("列表与详情查询返回广告主响应")
    void listAndDetailReturnAdvertiserResponses() {
        Advertiser first = advertiser(1L, "first", AdvertiserStatus.ACTIVE);
        Advertiser second = advertiser(2L, "second", AdvertiserStatus.DISABLED);
        Page<Advertiser> result = new Page<>(2, 2, 5);
        result.setRecords(List.of(first, second));
        when(advertiserMapper.selectPage(any(), any())).thenReturn(result);
        when(advertiserMapper.selectById(1L)).thenReturn(first);

        PageResponse<AdvertiserResponse> list = advertiserService.findAll(2, 2);
        AdvertiserResponse detail = advertiserService.findById(1L);

        assertAll(
                () -> assertEquals(List.of("first", "second"), list.items().stream()
                        .map(AdvertiserResponse::name).toList()),
                () -> assertEquals(2, list.page()),
                () -> assertEquals(2, list.size()),
                () -> assertEquals(5, list.total()),
                () -> assertEquals(3, list.totalPages()),
                () -> assertEquals("first", detail.name()));
    }

    @Test
    @DisplayName("局部修改只更新提供字段并可用空白字符串清空可选文本")
    void updateChangesProvidedFieldsAndClearsOptionalText() {
        Advertiser existing = advertiser(7L, "old", AdvertiserStatus.ACTIVE);
        existing.setRegistrationNo("OLD-REG");
        existing.setWebsite("https://old.example.com");
        existing.setAddress("旧地址");
        existing.setDescription("旧说明");
        OffsetDateTime originalUpdatedAt = existing.getUpdatedAt();
        when(advertiserMapper.selectById(7L)).thenReturn(existing);

        AdvertiserResponse response = advertiserService.update(7L, new UpdateAdvertiserRequest(
                "  new name  ", " ", null, null, null, null, " ", "新地址", " "));

        assertAll(
                () -> assertEquals("new name", response.name()),
                () -> assertNull(response.registrationNo()),
                () -> assertNull(response.website()),
                () -> assertEquals("新地址", response.address()),
                () -> assertNull(response.description()),
                () -> assertFalse(existing.getUpdatedAt().isBefore(originalUpdatedAt)));
        verify(advertiserMapper).updateById(existing);
    }

    @Test
    @DisplayName("空的广告主局部修改请求被拒绝")
    void emptyUpdateIsRejected() {
        UpdateAdvertiserRequest empty = new UpdateAdvertiserRequest(
                null, null, null, null, null, null, null, null, null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> advertiserService.update(1L, empty));

        assertSame(AdvertiserErrorCode.NO_FIELDS_TO_UPDATE, exception.errorCode());
        verify(advertiserMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("局部修改可以主动解除已有分类和负责人")
    void updateCanClearCategoryAndOwner() {
        Advertiser existing = advertiser(71L, "clear relations", AdvertiserStatus.ACTIVE);
        existing.setCategoryId(2L);
        existing.setOwnerUserId(3L);
        when(advertiserMapper.selectById(71L)).thenReturn(existing);

        AdvertiserResponse response = advertiserService.update(71L, new UpdateAdvertiserRequest(
                null, null, null, true, null, true, null, null, null));

        assertAll(
                () -> assertNull(response.categoryId()),
                () -> assertNull(response.ownerUserId()));
        verify(advertiserMapper).updateById(existing);
        verify(categoryMapper, never()).selectById(any());
        verify(userMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("广告主状态可在启用与禁用之间独立切换")
    void statusCanBeUpdatedIndependently() {
        Advertiser existing = advertiser(8L, "status", AdvertiserStatus.ACTIVE);
        when(advertiserMapper.selectById(8L)).thenReturn(existing);

        AdvertiserResponse response = advertiserService.updateStatus(8L, AdvertiserStatus.DISABLED);

        assertEquals(AdvertiserStatus.DISABLED, response.status());
        verify(advertiserMapper).updateById(existing);
    }

    @Test
    @DisplayName("删除广告主前确认记录存在")
    void deleteChecksExistence() {
        Advertiser existing = advertiser(9L, "delete", AdvertiserStatus.ACTIVE);
        when(advertiserMapper.selectById(9L)).thenReturn(existing);

        advertiserService.delete(9L);

        verify(advertiserMapper).deleteById(9L);
    }

    @Test
    @DisplayName("查询不存在的广告主返回明确错误")
    void missingAdvertiserReturnsNotFound() {
        when(advertiserMapper.selectById(404L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> advertiserService.findById(404L));

        assertSame(AdvertiserErrorCode.ADVERTISER_NOT_FOUND, exception.errorCode());
    }

    private CreateAdvertiserRequest createRequest(
            String name, String registrationNo, Long categoryId, Long ownerUserId) {
        return new CreateAdvertiserRequest(
                name, registrationNo, categoryId, ownerUserId, null, null, null, null);
    }

    private Advertiser advertiser(Long id, String name, AdvertiserStatus status) {
        OffsetDateTime now = OffsetDateTime.now().minusMinutes(1);
        Advertiser advertiser = new Advertiser();
        advertiser.setId(id);
        advertiser.setName(name);
        advertiser.setStatus(status);
        advertiser.setCreatedAt(now);
        advertiser.setUpdatedAt(now);
        return advertiser;
    }

    private AdvertiserCategory category(Long id, AdvertiserStatus status) {
        AdvertiserCategory category = new AdvertiserCategory();
        category.setId(id);
        category.setName("category" + id);
        category.setStatus(status);
        category.setSortOrder(0);
        return category;
    }

    private User user(Long id, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setUsername("owner" + id);
        user.setStatus(status);
        return user;
    }
}

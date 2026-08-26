package com.internship.crm.delivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.internship.crm.delivery.dto.response.AdvertisingTypeResponse;
import com.internship.crm.delivery.entity.AdvertisingType;
import com.internship.crm.delivery.entity.AdvertisingTypeStatus;
import com.internship.crm.delivery.mapper.AdvertisingTypeMapper;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("广告类型 Service 查询规则")
@ExtendWith({MockitoExtension.class, ReadableTestResultExtension.class})
class AdvertisingTypeServiceTest {

    @Mock
    private AdvertisingTypeMapper advertisingTypeMapper;

    private AdvertisingTypeService advertisingTypeService;

    @BeforeEach
    void setUp() {
        advertisingTypeService = new AdvertisingTypeService(advertisingTypeMapper);
    }

    @Test
    @DisplayName("广告类型列表返回完整字典字段")
    void findAllReturnsCompleteDictionaryEntries() {
        when(advertisingTypeMapper.selectList(any())).thenReturn(List.of(
                advertisingType(1L, "SEARCH", "搜索广告", AdvertisingTypeStatus.ACTIVE),
                advertisingType(2L, "DISPLAY", "展示广告", AdvertisingTypeStatus.DISABLED)));

        List<AdvertisingTypeResponse> result = advertisingTypeService.findAll();

        assertEquals(2, result.size());
        assertEquals("SEARCH", result.get(0).code());
        assertEquals("搜索广告", result.get(0).name());
        assertEquals(AdvertisingTypeStatus.ACTIVE, result.get(0).status());
        assertEquals(AdvertisingTypeStatus.DISABLED, result.get(1).status());
        verify(advertisingTypeMapper).selectList(any());
    }

    @Test
    @DisplayName("没有广告类型时返回空列表")
    void findAllReturnsEmptyListWhenDictionaryIsEmpty() {
        when(advertisingTypeMapper.selectList(any())).thenReturn(List.of());

        List<AdvertisingTypeResponse> result = advertisingTypeService.findAll();

        assertEquals(List.of(), result);
    }

    private AdvertisingType advertisingType(
            Long id, String code, String name, AdvertisingTypeStatus status) {
        OffsetDateTime now = OffsetDateTime.now().minusMinutes(1);
        AdvertisingType advertisingType = new AdvertisingType();
        advertisingType.setId(id);
        advertisingType.setCode(code);
        advertisingType.setName(name);
        advertisingType.setStatus(status);
        advertisingType.setCreatedAt(now);
        advertisingType.setUpdatedAt(now);
        return advertisingType;
    }
}

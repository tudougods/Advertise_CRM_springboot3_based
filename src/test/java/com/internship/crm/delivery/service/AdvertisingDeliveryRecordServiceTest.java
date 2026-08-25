package com.internship.crm.delivery.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.internship.crm.advertiser.entity.Advertiser;
import com.internship.crm.advertiser.entity.AdvertiserStatus;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.delivery.dto.request.CreateAdvertisingDeliveryRecordRequest;
import com.internship.crm.delivery.dto.response.AdvertisingDeliveryRecordResponse;
import com.internship.crm.delivery.entity.AdvertisingDeliveryRecord;
import com.internship.crm.delivery.entity.AdvertisingType;
import com.internship.crm.delivery.entity.AdvertisingTypeStatus;
import com.internship.crm.delivery.exception.DeliveryErrorCode;
import com.internship.crm.delivery.mapper.AdvertisingDeliveryRecordMapper;
import com.internship.crm.delivery.mapper.AdvertisingTypeMapper;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("投放数据入库 Service 业务规则")
@ExtendWith({MockitoExtension.class, ReadableTestResultExtension.class})
class AdvertisingDeliveryRecordServiceTest {

    @Mock
    private AdvertisingDeliveryRecordMapper deliveryRecordMapper;

    @Mock
    private AdvertisingTypeMapper advertisingTypeMapper;

    @Mock
    private AdvertiserMapper advertiserMapper;

    private AdvertisingDeliveryRecordService deliveryRecordService;

    @BeforeEach
    void setUp() {
        deliveryRecordService = new AdvertisingDeliveryRecordService(
                deliveryRecordMapper, advertisingTypeMapper, advertiserMapper);
    }

    @Test
    @DisplayName("录入会规范化业务字段并返回广告主和类型信息")
    void createNormalizesAndPersistsDeliveryRecord() {
        Advertiser advertiser = advertiser(7L, AdvertiserStatus.ACTIVE);
        AdvertisingType advertisingType = advertisingType(3L, AdvertisingTypeStatus.ACTIVE);
        when(advertiserMapper.selectById(7L)).thenReturn(advertiser);
        when(advertisingTypeMapper.findByCodeIgnoreCase("search"))
                .thenReturn(Optional.of(advertisingType));
        when(deliveryRecordMapper.insertIfExternalRecordNoAbsent(any()))
                .thenAnswer(invocation -> {
                    AdvertisingDeliveryRecord inserted = invocation.getArgument(0);
                    inserted.setId(11L);
                    return 1;
                });

        AdvertisingDeliveryRecordResponse response = deliveryRecordService.create(
                request("  DELIVERY-001  ", 7L, " search ", 10_000L, 500L, 30L,
                        new BigDecimal("300")));

        ArgumentCaptor<AdvertisingDeliveryRecord> captor =
                ArgumentCaptor.forClass(AdvertisingDeliveryRecord.class);
        verify(deliveryRecordMapper).insertIfExternalRecordNoAbsent(captor.capture());
        AdvertisingDeliveryRecord inserted = captor.getValue();
        assertAll(
                () -> assertEquals("DELIVERY-001", inserted.getExternalRecordNo()),
                () -> assertEquals(3L, inserted.getAdvertisingTypeId()),
                () -> assertEquals(new BigDecimal("300.00"), inserted.getSpend()),
                () -> assertEquals(11L, response.id()),
                () -> assertEquals("示例广告主", response.advertiserName()),
                () -> assertEquals("SEARCH", response.advertisingTypeCode()),
                () -> assertEquals("搜索广告", response.advertisingTypeName()));
    }

    @Test
    @DisplayName("原子插入发现重复外部记录号时返回明确冲突")
    void duplicateExternalRecordNumberIsRejected() {
        when(advertiserMapper.selectById(7L))
                .thenReturn(advertiser(7L, AdvertiserStatus.ACTIVE));
        when(advertisingTypeMapper.findByCodeIgnoreCase("SEARCH"))
                .thenReturn(Optional.of(advertisingType(3L, AdvertisingTypeStatus.ACTIVE)));
        when(deliveryRecordMapper.insertIfExternalRecordNoAbsent(any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                deliveryRecordService.create(validRequest()));

        assertSame(DeliveryErrorCode.EXTERNAL_RECORD_NO_ALREADY_EXISTS, exception.errorCode());
    }

    @Test
    @DisplayName("不存在的广告主不能录入投放数据")
    void missingAdvertiserIsRejected() {
        when(advertiserMapper.selectById(7L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                deliveryRecordService.create(validRequest()));

        assertSame(DeliveryErrorCode.ADVERTISER_NOT_FOUND, exception.errorCode());
        verifyNoInteractions(advertisingTypeMapper);
        verify(deliveryRecordMapper, never()).insertIfExternalRecordNoAbsent(any());
    }

    @Test
    @DisplayName("已禁用的广告主不能录入投放数据")
    void disabledAdvertiserIsRejected() {
        when(advertiserMapper.selectById(7L))
                .thenReturn(advertiser(7L, AdvertiserStatus.DISABLED));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                deliveryRecordService.create(validRequest()));

        assertSame(DeliveryErrorCode.ADVERTISER_DISABLED, exception.errorCode());
        verifyNoInteractions(advertisingTypeMapper);
    }

    @Test
    @DisplayName("不存在的广告类型不能用于投放数据")
    void missingAdvertisingTypeIsRejected() {
        when(advertiserMapper.selectById(7L))
                .thenReturn(advertiser(7L, AdvertiserStatus.ACTIVE));
        when(advertisingTypeMapper.findByCodeIgnoreCase("SEARCH"))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                deliveryRecordService.create(validRequest()));

        assertSame(DeliveryErrorCode.ADVERTISING_TYPE_NOT_FOUND, exception.errorCode());
        verify(deliveryRecordMapper, never()).insertIfExternalRecordNoAbsent(any());
    }

    @Test
    @DisplayName("已禁用的广告类型不能用于投放数据")
    void disabledAdvertisingTypeIsRejected() {
        when(advertiserMapper.selectById(7L))
                .thenReturn(advertiser(7L, AdvertiserStatus.ACTIVE));
        when(advertisingTypeMapper.findByCodeIgnoreCase("SEARCH"))
                .thenReturn(Optional.of(advertisingType(3L, AdvertisingTypeStatus.DISABLED)));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                deliveryRecordService.create(validRequest()));

        assertSame(DeliveryErrorCode.ADVERTISING_TYPE_DISABLED, exception.errorCode());
        verify(deliveryRecordMapper, never()).insertIfExternalRecordNoAbsent(any());
    }

    @ParameterizedTest(name = "{4}")
    @MethodSource("invalidMetrics")
    @DisplayName("非法漏斗指标和金额在查询关联数据前被拒绝")
    void invalidMetricsAreRejected(
            long impressions,
            long clicks,
            long conversions,
            String spend,
            String description) {
        CreateAdvertisingDeliveryRecordRequest request = request(
                "DELIVERY-001", 7L, "SEARCH", impressions, clicks, conversions,
                new BigDecimal(spend));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                deliveryRecordService.create(request), description);

        assertSame(DeliveryErrorCode.INVALID_METRICS, exception.errorCode());
        verifyNoInteractions(advertiserMapper, advertisingTypeMapper, deliveryRecordMapper);
    }

    private static Stream<Arguments> invalidMetrics() {
        return Stream.of(
                Arguments.of(-1L, 0L, 0L, "0.00", "展示量不能为负"),
                Arguments.of(100L, -1L, 0L, "0.00", "点击量不能为负"),
                Arguments.of(100L, 101L, 0L, "0.00", "点击量不能超过展示量"),
                Arguments.of(100L, 10L, -1L, "0.00", "转化量不能为负"),
                Arguments.of(100L, 10L, 11L, "0.00", "转化量不能超过点击量"),
                Arguments.of(100L, 10L, 1L, "-0.01", "花费不能为负"),
                Arguments.of(100L, 10L, 1L, "0.001", "花费不能超过两位小数"),
                Arguments.of(100L, 10L, 1L, "100000000000000000.00",
                        "花费整数部分不能超过十七位"));
    }

    private CreateAdvertisingDeliveryRecordRequest validRequest() {
        return request("DELIVERY-001", 7L, "SEARCH", 10_000L, 500L, 30L,
                new BigDecimal("300.00"));
    }

    private CreateAdvertisingDeliveryRecordRequest request(
            String externalRecordNo,
            Long advertiserId,
            String advertisingTypeCode,
            long impressions,
            long clicks,
            long conversions,
            BigDecimal spend) {
        return new CreateAdvertisingDeliveryRecordRequest(
                externalRecordNo,
                advertiserId,
                advertisingTypeCode,
                LocalDate.of(2026, 8, 26),
                impressions,
                clicks,
                conversions,
                spend);
    }

    private Advertiser advertiser(Long id, AdvertiserStatus status) {
        Advertiser advertiser = new Advertiser();
        advertiser.setId(id);
        advertiser.setName("示例广告主");
        advertiser.setStatus(status);
        return advertiser;
    }

    private AdvertisingType advertisingType(Long id, AdvertisingTypeStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        AdvertisingType advertisingType = new AdvertisingType();
        advertisingType.setId(id);
        advertisingType.setCode("SEARCH");
        advertisingType.setName("搜索广告");
        advertisingType.setStatus(status);
        advertisingType.setCreatedAt(now);
        advertisingType.setUpdatedAt(now);
        return advertisingType;
    }
}

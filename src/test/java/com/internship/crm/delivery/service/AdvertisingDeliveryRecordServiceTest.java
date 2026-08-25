package com.internship.crm.delivery.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.internship.crm.advertiser.entity.Advertiser;
import com.internship.crm.advertiser.entity.AdvertiserStatus;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.common.response.PageResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.internship.crm.delivery.dto.request.CreateAdvertisingDeliveryRecordRequest;
import com.internship.crm.delivery.dto.request.UpdateAdvertisingDeliveryRecordRequest;
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
import java.util.List;
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

@DisplayName("广告投放记录 Service 业务规则")
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
    @DisplayName("详情查询返回包含广告主和类型名称的投放记录")
    void findByIdReturnsDeliveryRecordDetails() {
        AdvertisingDeliveryRecordResponse expected = response(11L, "DELIVERY-001");
        when(deliveryRecordMapper.selectDetailById(11L)).thenReturn(expected);

        AdvertisingDeliveryRecordResponse result = deliveryRecordService.findById(11L);

        assertSame(expected, result);
    }

    @Test
    @DisplayName("查询不存在的投放记录返回明确错误")
    void missingDeliveryRecordReturnsNotFound() {
        when(deliveryRecordMapper.selectDetailById(404L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deliveryRecordService.findById(404L));

        assertSame(DeliveryErrorCode.DELIVERY_RECORD_NOT_FOUND, exception.errorCode());
    }

    @Test
    @DisplayName("组合查询将类型编码解析为 ID 并返回物理分页结果")
    void findAllResolvesTypeCodeAndReturnsPhysicalPage() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2027, 1, 1);
        when(advertisingTypeMapper.findByCodeIgnoreCase("search"))
                .thenReturn(Optional.of(advertisingType(3L, AdvertisingTypeStatus.ACTIVE)));
        Page<AdvertisingDeliveryRecordResponse> mapperPage = new Page<>(2, 1, 3);
        mapperPage.setRecords(List.of(response(12L, "DELIVERY-002")));
        when(deliveryRecordMapper.selectPageWithDetails(
                any(), eq(startDate), eq(endDate), eq(7L), eq(3L)))
                .thenReturn(mapperPage);

        PageResponse<AdvertisingDeliveryRecordResponse> result = deliveryRecordService.findAll(
                startDate, endDate, 7L, " search ", 2, 1);

        assertAll(
                () -> assertEquals(1, result.items().size()),
                () -> assertEquals("DELIVERY-002", result.items().get(0).externalRecordNo()),
                () -> assertEquals(2, result.page()),
                () -> assertEquals(1, result.size()),
                () -> assertEquals(3, result.total()),
                () -> assertEquals(3, result.totalPages()));
    }

    @Test
    @DisplayName("不存在的广告类型筛选条件直接返回空页")
    void unknownTypeFilterReturnsEmptyPage() {
        when(advertisingTypeMapper.findByCodeIgnoreCase("UNKNOWN"))
                .thenReturn(Optional.empty());

        PageResponse<AdvertisingDeliveryRecordResponse> result = deliveryRecordService.findAll(
                null, null, null, "UNKNOWN", 3, 20);

        assertAll(
                () -> assertEquals(List.of(), result.items()),
                () -> assertEquals(3, result.page()),
                () -> assertEquals(20, result.size()),
                () -> assertEquals(0, result.total()),
                () -> assertEquals(0, result.totalPages()));
        verify(deliveryRecordMapper, never()).selectPageWithDetails(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("开始日期晚于结束日期时拒绝查询")
    void reversedDateRangeIsRejected() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                deliveryRecordService.findAll(
                        LocalDate.of(2026, 8, 27), LocalDate.of(2026, 8, 26),
                        null, null, 1, 20));

        assertSame(DeliveryErrorCode.INVALID_DATE_RANGE, exception.errorCode());
        verify(deliveryRecordMapper, never()).selectPageWithDetails(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("超过 366 天的日期范围被拒绝")
    void dateRangeAboveLimitIsRejected() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                deliveryRecordService.findAll(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2),
                        null, null, 1, 20));

        assertSame(DeliveryErrorCode.DATE_RANGE_TOO_LARGE, exception.errorCode());
        verify(deliveryRecordMapper, never()).selectPageWithDetails(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("局部修正只更新提供字段并保持外部记录号不变")
    void updateChangesOnlyProvidedFields() {
        AdvertisingDeliveryRecord existing = deliveryRecord();
        OffsetDateTime originalUpdatedAt = existing.getUpdatedAt();
        AdvertisingDeliveryRecordResponse expected = response(11L, "DELIVERY-001");
        when(deliveryRecordMapper.selectById(11L)).thenReturn(existing);
        when(deliveryRecordMapper.updateById(existing)).thenReturn(1);
        when(deliveryRecordMapper.selectDetailById(11L)).thenReturn(expected);

        AdvertisingDeliveryRecordResponse result = deliveryRecordService.update(
                11L,
                new UpdateAdvertisingDeliveryRecordRequest(
                        null, null, null, 20_000L, null, null, new BigDecimal("450")));

        assertAll(
                () -> assertSame(expected, result),
                () -> assertEquals("DELIVERY-001", existing.getExternalRecordNo()),
                () -> assertEquals(20_000L, existing.getImpressions()),
                () -> assertEquals(500L, existing.getClicks()),
                () -> assertEquals(30L, existing.getConversions()),
                () -> assertEquals(new BigDecimal("450.00"), existing.getSpend()),
                () -> assertTrue(existing.getUpdatedAt().isAfter(originalUpdatedAt)));
        verify(deliveryRecordMapper).updateById(existing);
    }

    @Test
    @DisplayName("修正广告主和广告类型时校验新关联并保存对应 ID")
    void updateChangesValidatedRelations() {
        AdvertisingDeliveryRecord existing = deliveryRecord();
        when(deliveryRecordMapper.selectById(11L)).thenReturn(existing);
        when(advertiserMapper.selectById(8L))
                .thenReturn(advertiser(8L, AdvertiserStatus.ACTIVE));
        AdvertisingType video = advertisingType(4L, AdvertisingTypeStatus.ACTIVE);
        video.setCode("VIDEO");
        video.setName("视频广告");
        when(advertisingTypeMapper.findByCodeIgnoreCase("video"))
                .thenReturn(Optional.of(video));
        when(deliveryRecordMapper.updateById(existing)).thenReturn(1);
        when(deliveryRecordMapper.selectDetailById(11L))
                .thenReturn(response(11L, "DELIVERY-001"));

        deliveryRecordService.update(
                11L,
                new UpdateAdvertisingDeliveryRecordRequest(
                        8L, " video ", LocalDate.of(2026, 8, 27), null, null, null, null));

        assertAll(
                () -> assertEquals(8L, existing.getAdvertiserId()),
                () -> assertEquals(4L, existing.getAdvertisingTypeId()),
                () -> assertEquals(LocalDate.of(2026, 8, 27), existing.getRecordDate()));
    }

    @Test
    @DisplayName("空的局部修正请求被拒绝")
    void emptyUpdateIsRejected() {
        UpdateAdvertisingDeliveryRecordRequest empty =
                new UpdateAdvertisingDeliveryRecordRequest(null, null, null, null, null, null, null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deliveryRecordService.update(11L, empty));

        assertSame(DeliveryErrorCode.NO_FIELDS_TO_UPDATE, exception.errorCode());
        verify(deliveryRecordMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("修正不存在的投放记录返回明确错误")
    void updateMissingRecordReturnsNotFound() {
        when(deliveryRecordMapper.selectById(404L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deliveryRecordService.update(
                        404L,
                        new UpdateAdvertisingDeliveryRecordRequest(
                                null, null, null, 100L, null, null, null)));

        assertSame(DeliveryErrorCode.DELIVERY_RECORD_NOT_FOUND, exception.errorCode());
    }

    @Test
    @DisplayName("局部修正后形成非法漏斗关系时不写入数据库")
    void updateRejectsInvalidMergedMetrics() {
        AdvertisingDeliveryRecord existing = deliveryRecord();
        when(deliveryRecordMapper.selectById(11L)).thenReturn(existing);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deliveryRecordService.update(
                        11L,
                        new UpdateAdvertisingDeliveryRecordRequest(
                                null, null, null, 100L, null, null, null)));

        assertSame(DeliveryErrorCode.INVALID_METRICS, exception.errorCode());
        verify(deliveryRecordMapper, never()).updateById(any(AdvertisingDeliveryRecord.class));
    }

    @Test
    @DisplayName("局部修正不能换绑到已禁用的广告主")
    void updateRejectsDisabledAdvertiser() {
        AdvertisingDeliveryRecord existing = deliveryRecord();
        when(deliveryRecordMapper.selectById(11L)).thenReturn(existing);
        when(advertiserMapper.selectById(8L))
                .thenReturn(advertiser(8L, AdvertiserStatus.DISABLED));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deliveryRecordService.update(
                        11L,
                        new UpdateAdvertisingDeliveryRecordRequest(
                                8L, null, null, null, null, null, null)));

        assertSame(DeliveryErrorCode.ADVERTISER_DISABLED, exception.errorCode());
        verify(deliveryRecordMapper, never()).updateById(any(AdvertisingDeliveryRecord.class));
    }

    @Test
    @DisplayName("局部修正不能换绑到已禁用的广告类型")
    void updateRejectsDisabledAdvertisingType() {
        AdvertisingDeliveryRecord existing = deliveryRecord();
        when(deliveryRecordMapper.selectById(11L)).thenReturn(existing);
        when(advertisingTypeMapper.findByCodeIgnoreCase("VIDEO"))
                .thenReturn(Optional.of(advertisingType(4L, AdvertisingTypeStatus.DISABLED)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deliveryRecordService.update(
                        11L,
                        new UpdateAdvertisingDeliveryRecordRequest(
                                null, "VIDEO", null, null, null, null, null)));

        assertSame(DeliveryErrorCode.ADVERTISING_TYPE_DISABLED, exception.errorCode());
        verify(deliveryRecordMapper, never()).updateById(any(AdvertisingDeliveryRecord.class));
    }

    @Test
    @DisplayName("并发删除导致更新行数为零时返回记录不存在")
    void updateReturningZeroRowsIsTreatedAsNotFound() {
        AdvertisingDeliveryRecord existing = deliveryRecord();
        when(deliveryRecordMapper.selectById(11L)).thenReturn(existing);
        when(deliveryRecordMapper.updateById(existing)).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deliveryRecordService.update(
                        11L,
                        new UpdateAdvertisingDeliveryRecordRequest(
                                null, null, LocalDate.of(2026, 8, 27), null, null, null, null)));

        assertSame(DeliveryErrorCode.DELIVERY_RECORD_NOT_FOUND, exception.errorCode());
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

    private AdvertisingDeliveryRecordResponse response(Long id, String externalRecordNo) {
        OffsetDateTime now = OffsetDateTime.now();
        return new AdvertisingDeliveryRecordResponse(
                id,
                externalRecordNo,
                7L,
                "示例广告主",
                3L,
                "SEARCH",
                "搜索广告",
                LocalDate.of(2026, 8, 26),
                10_000L,
                500L,
                30L,
                new BigDecimal("300.00"),
                now,
                now);
    }

    private AdvertisingDeliveryRecord deliveryRecord() {
        OffsetDateTime now = OffsetDateTime.now().minusMinutes(1);
        AdvertisingDeliveryRecord record = new AdvertisingDeliveryRecord();
        record.setId(11L);
        record.setExternalRecordNo("DELIVERY-001");
        record.setAdvertiserId(7L);
        record.setAdvertisingTypeId(3L);
        record.setRecordDate(LocalDate.of(2026, 8, 26));
        record.setImpressions(10_000L);
        record.setClicks(500L);
        record.setConversions(30L);
        record.setSpend(new BigDecimal("300.00"));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }
}

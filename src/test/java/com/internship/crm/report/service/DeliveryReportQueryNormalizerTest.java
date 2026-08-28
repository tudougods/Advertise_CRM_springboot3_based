package com.internship.crm.report.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.report.dto.request.DeliveryReportQuery;
import com.internship.crm.report.exception.ReportErrorCode;
import com.internship.crm.report.model.DeliveryReportCriteria;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("投放报表查询条件规范化")
@ExtendWith(ReadableTestResultExtension.class)
class DeliveryReportQueryNormalizerTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);

    private final DeliveryReportQueryNormalizer normalizer =
            new DeliveryReportQueryNormalizer(FIXED_CLOCK);

    @Test
    @DisplayName("未提供日期时默认使用包含当天的最近 30 天")
    void defaultsToLastThirtyDays() {
        DeliveryReportCriteria criteria = normalizer.normalize(
                new DeliveryReportQuery(null, null, null, null));

        assertAll(
                () -> assertEquals(LocalDate.of(2026, 7, 28), criteria.startDate()),
                () -> assertEquals(LocalDate.of(2026, 8, 26), criteria.endDate()),
                () -> assertNull(criteria.advertiserId()),
                () -> assertNull(criteria.advertisingTypeCode()));
    }

    @Test
    @DisplayName("默认日期边界使用配置的业务时区")
    void defaultDateBoundaryUsesConfiguredBusinessZone() {
        Clock sydneyClock = Clock.fixed(
                Instant.parse("2026-08-25T14:30:00Z"),
                ZoneId.of("Australia/Sydney"));
        DeliveryReportCriteria criteria = new DeliveryReportQueryNormalizer(sydneyClock)
                .normalize(new DeliveryReportQuery(null, null, null, null));

        assertAll(
                () -> assertEquals(LocalDate.of(2026, 7, 28), criteria.startDate()),
                () -> assertEquals(LocalDate.of(2026, 8, 26), criteria.endDate()));
    }

    @Test
    @DisplayName("保留合法筛选条件并规范化广告类型编码")
    void normalizesValidFilters() {
        DeliveryReportCriteria criteria = normalizer.normalize(new DeliveryReportQuery(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                7L,
                " search "));

        assertAll(
                () -> assertEquals(LocalDate.of(2026, 1, 1), criteria.startDate()),
                () -> assertEquals(LocalDate.of(2026, 12, 31), criteria.endDate()),
                () -> assertEquals(7L, criteria.advertiserId()),
                () -> assertEquals("SEARCH", criteria.advertisingTypeCode()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDateRanges")
    @DisplayName("拒绝不完整、倒序或过大的日期范围")
    void rejectsInvalidDateRanges(
            String scenario,
            LocalDate startDate,
            LocalDate endDate,
            ReportErrorCode expectedError) {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                normalizer.normalize(new DeliveryReportQuery(
                        startDate, endDate, null, null)));

        assertSame(expectedError, exception.errorCode());
    }

    @Test
    @DisplayName("拒绝仅由空白字符组成的广告类型编码")
    void rejectsBlankAdvertisingTypeCode() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                normalizer.normalize(new DeliveryReportQuery(null, null, null, "   ")));

        assertSame(ReportErrorCode.BLANK_ADVERTISING_TYPE_CODE, exception.errorCode());
    }

    private static Stream<Arguments> invalidDateRanges() {
        return Stream.of(
                Arguments.of(
                        "缺少结束日期",
                        LocalDate.of(2026, 1, 1),
                        null,
                        ReportErrorCode.INCOMPLETE_DATE_RANGE),
                Arguments.of(
                        "缺少开始日期",
                        null,
                        LocalDate.of(2026, 1, 1),
                        ReportErrorCode.INCOMPLETE_DATE_RANGE),
                Arguments.of(
                        "开始日期晚于结束日期",
                        LocalDate.of(2026, 1, 2),
                        LocalDate.of(2026, 1, 1),
                        ReportErrorCode.INVALID_DATE_RANGE),
                Arguments.of(
                        "日期跨度超过 366 天",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2027, 1, 2),
                        ReportErrorCode.DATE_RANGE_TOO_LARGE));
    }
}

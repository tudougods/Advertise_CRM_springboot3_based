package com.internship.crm.report.service;

import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.report.dto.request.DeliveryReportQuery;
import com.internship.crm.report.exception.ReportErrorCode;
import com.internship.crm.report.model.DeliveryReportCriteria;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class DeliveryReportQueryNormalizer {

    private final Clock clock;

    public DeliveryReportQueryNormalizer(Clock clock) {
        this.clock = clock;
    }

    public DeliveryReportCriteria normalize(DeliveryReportQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        DateRange dateRange = normalizeDateRange(query.startDate(), query.endDate());
        return new DeliveryReportCriteria(
                dateRange.startDate(),
                dateRange.endDate(),
                query.advertiserId(),
                normalizeAdvertisingTypeCode(query.advertisingTypeCode()));
    }

    private DateRange normalizeDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            LocalDate defaultEndDate = LocalDate.now(clock);
            return new DateRange(defaultEndDate.minusDays(29), defaultEndDate);
        }
        if (startDate == null || endDate == null) {
            throw new BusinessException(ReportErrorCode.INCOMPLETE_DATE_RANGE);
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ReportErrorCode.INVALID_DATE_RANGE);
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 365) {
            throw new BusinessException(ReportErrorCode.DATE_RANGE_TOO_LARGE);
        }
        return new DateRange(startDate, endDate);
    }

    private String normalizeAdvertisingTypeCode(String code) {
        if (code == null) {
            return null;
        }
        String normalizedCode = code.trim();
        if (normalizedCode.isEmpty()) {
            throw new BusinessException(ReportErrorCode.BLANK_ADVERTISING_TYPE_CODE);
        }
        return normalizedCode.toUpperCase(Locale.ROOT);
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}

package com.internship.crm.report.model;

import java.time.LocalDate;
import java.util.Objects;

public record DeliveryReportCriteria(
        LocalDate startDate,
        LocalDate endDate,
        Long advertiserId,
        String advertisingTypeCode) {

    public DeliveryReportCriteria {
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
    }
}

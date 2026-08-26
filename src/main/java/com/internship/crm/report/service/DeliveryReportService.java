package com.internship.crm.report.service;

import com.internship.crm.report.dto.request.DeliveryReportQuery;
import com.internship.crm.report.dto.response.DeliveryMetricsResponse;
import com.internship.crm.report.model.DeliveryReportCriteria;
import com.internship.crm.report.mapper.DeliveryReportMapper;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryReportService {

    private final DeliveryReportMapper deliveryReportMapper;
    private final DeliveryReportQueryNormalizer queryNormalizer;

    public DeliveryReportService(
            DeliveryReportMapper deliveryReportMapper,
            DeliveryReportQueryNormalizer queryNormalizer) {
        this.deliveryReportMapper = deliveryReportMapper;
        this.queryNormalizer = queryNormalizer;
    }

    @Transactional(readOnly = true)
    public DeliveryMetricsResponse overview(DeliveryReportQuery query) {
        DeliveryReportCriteria criteria = queryNormalizer.normalize(query);
        return Objects.requireNonNull(
                deliveryReportMapper.selectOverview(criteria),
                "overview query must return one aggregate row");
    }
}

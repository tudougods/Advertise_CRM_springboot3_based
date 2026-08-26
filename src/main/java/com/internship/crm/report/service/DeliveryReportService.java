package com.internship.crm.report.service;

import com.internship.crm.common.response.PageResponse;
import com.internship.crm.report.dto.request.DeliveryReportQuery;
import com.internship.crm.report.dto.response.AdvertiserDeliveryReportResponse;
import com.internship.crm.report.dto.response.AdvertisingTypeDeliveryReportResponse;
import com.internship.crm.report.dto.response.DeliveryMetricsResponse;
import com.internship.crm.report.dto.response.DeliveryTrendPointResponse;
import com.internship.crm.report.mapper.DeliveryReportMapper;
import com.internship.crm.report.model.AdvertiserReportRow;
import com.internship.crm.report.model.AdvertiserReportSortField;
import com.internship.crm.report.model.AdvertisingTypeReportRow;
import com.internship.crm.report.model.DeliveryReportCriteria;
import com.internship.crm.report.model.DeliveryTrendRow;
import com.internship.crm.report.model.ReportSortDirection;
import com.internship.crm.report.model.ReportTimeGranularity;
import java.util.List;
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

    @Transactional(readOnly = true)
    public List<DeliveryTrendPointResponse> trend(
            DeliveryReportQuery query, ReportTimeGranularity granularity) {
        DeliveryReportCriteria criteria = queryNormalizer.normalize(query);
        List<DeliveryTrendRow> rows = deliveryReportMapper.selectTrend(
                criteria,
                Objects.requireNonNull(granularity, "granularity must not be null").sqlValue());
        return rows.stream().map(DeliveryTrendRow::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdvertiserDeliveryReportResponse> byAdvertiser(
            DeliveryReportQuery query,
            long page,
            long size,
            AdvertiserReportSortField sortField,
            ReportSortDirection sortDirection) {
        DeliveryReportCriteria criteria = queryNormalizer.normalize(query);
        String sortFieldName =
                Objects.requireNonNull(sortField, "sortField must not be null").name();
        String sortDirectionName =
                Objects.requireNonNull(sortDirection, "sortDirection must not be null").name();
        long total = deliveryReportMapper.countByAdvertiser(criteria);
        if (total == 0) {
            return PageResponse.of(List.of(), page, size, 0);
        }
        long offset = (page - 1) * size;
        List<AdvertiserDeliveryReportResponse> items = deliveryReportMapper.selectByAdvertiser(
                        criteria,
                        sortFieldName,
                        sortDirectionName,
                        size,
                        offset)
                .stream()
                .map(AdvertiserReportRow::toResponse)
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public List<AdvertisingTypeDeliveryReportResponse> byAdvertisingType(
            DeliveryReportQuery query) {
        DeliveryReportCriteria criteria = queryNormalizer.normalize(query);
        return deliveryReportMapper.selectByAdvertisingType(criteria)
                .stream()
                .map(AdvertisingTypeReportRow::toResponse)
                .toList();
    }
}

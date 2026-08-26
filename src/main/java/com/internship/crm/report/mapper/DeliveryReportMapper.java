package com.internship.crm.report.mapper;

import com.internship.crm.report.dto.response.DeliveryMetricsResponse;
import com.internship.crm.report.model.AdvertiserReportRow;
import com.internship.crm.report.model.AdvertisingTypeReportRow;
import com.internship.crm.report.model.DeliveryReportCriteria;
import com.internship.crm.report.model.DeliveryTrendRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeliveryReportMapper {

    DeliveryMetricsResponse selectOverview(
            @Param("criteria") DeliveryReportCriteria criteria);

    List<DeliveryTrendRow> selectTrend(
            @Param("criteria") DeliveryReportCriteria criteria,
            @Param("granularity") String granularity);

    List<AdvertiserReportRow> selectByAdvertiser(
            @Param("criteria") DeliveryReportCriteria criteria,
            @Param("sortField") String sortField,
            @Param("sortDirection") String sortDirection,
            @Param("limit") long limit,
            @Param("offset") long offset);

    long countByAdvertiser(@Param("criteria") DeliveryReportCriteria criteria);

    List<AdvertisingTypeReportRow> selectByAdvertisingType(
            @Param("criteria") DeliveryReportCriteria criteria);
}

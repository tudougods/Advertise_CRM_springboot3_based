package com.internship.crm.report.mapper;

import com.internship.crm.report.dto.response.DeliveryMetricsResponse;
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
}

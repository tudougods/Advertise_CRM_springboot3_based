package com.internship.crm.report.mapper;

import com.internship.crm.report.dto.response.DeliveryMetricsResponse;
import com.internship.crm.report.model.DeliveryReportCriteria;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeliveryReportMapper {

    DeliveryMetricsResponse selectOverview(
            @Param("criteria") DeliveryReportCriteria criteria);
}

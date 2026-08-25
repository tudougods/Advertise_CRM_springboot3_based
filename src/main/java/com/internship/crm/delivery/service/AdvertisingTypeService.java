package com.internship.crm.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.internship.crm.delivery.dto.response.AdvertisingTypeResponse;
import com.internship.crm.delivery.entity.AdvertisingType;
import com.internship.crm.delivery.mapper.AdvertisingTypeMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvertisingTypeService {

    private final AdvertisingTypeMapper advertisingTypeMapper;

    public AdvertisingTypeService(AdvertisingTypeMapper advertisingTypeMapper) {
        this.advertisingTypeMapper = advertisingTypeMapper;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    public List<AdvertisingTypeResponse> findAll() {
        return advertisingTypeMapper.selectList(new LambdaQueryWrapper<AdvertisingType>()
                        .orderByAsc(AdvertisingType::getId))
                .stream()
                .map(AdvertisingTypeResponse::from)
                .toList();
    }
}

package com.internship.crm.advertiser.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.internship.crm.advertiser.dto.response.AdvertiserCategoryResponse;
import com.internship.crm.advertiser.dto.request.CreateAdvertiserCategoryRequest;
import com.internship.crm.advertiser.dto.request.UpdateAdvertiserCategoryRequest;
import com.internship.crm.advertiser.entity.AdvertiserCategory;
import com.internship.crm.advertiser.entity.AdvertiserStatus;
import com.internship.crm.advertiser.exception.AdvertiserErrorCode;
import com.internship.crm.advertiser.mapper.AdvertiserCategoryMapper;
import com.internship.crm.common.exception.BusinessException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvertiserCategoryService {

    private final AdvertiserCategoryMapper categoryMapper;

    public AdvertiserCategoryService(AdvertiserCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Transactional
    public AdvertiserCategoryResponse create(CreateAdvertiserCategoryRequest request) {
        String name = request.name().trim();
        ensureNameAvailable(name, null);

        OffsetDateTime now = OffsetDateTime.now();
        AdvertiserCategory category = new AdvertiserCategory();
        category.setName(name);
        category.setDescription(trimToNull(request.description()));
        category.setStatus(request.status() == null ? AdvertiserStatus.ACTIVE : request.status());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        categoryMapper.insert(category);
        return AdvertiserCategoryResponse.from(category);
    }

    @Transactional(readOnly = true)
    public List<AdvertiserCategoryResponse> findAll() {
        LambdaQueryWrapper<AdvertiserCategory> query = new LambdaQueryWrapper<AdvertiserCategory>()
                .orderByAsc(AdvertiserCategory::getSortOrder)
                .orderByAsc(AdvertiserCategory::getId);
        return categoryMapper.selectList(query).stream()
                .map(AdvertiserCategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdvertiserCategoryResponse findById(Long id) {
        return AdvertiserCategoryResponse.from(requireCategory(id));
    }

    @Transactional
    public AdvertiserCategoryResponse update(Long id, UpdateAdvertiserCategoryRequest request) {
        ensureUpdateHasFields(request);
        AdvertiserCategory category = requireCategory(id);

        if (request.name() != null) {
            String name = request.name().trim();
            if (!category.getName().equalsIgnoreCase(name)) {
                ensureNameAvailable(name, id);
                category.setName(name);
            }
        }
        if (request.description() != null) {
            category.setDescription(trimToNull(request.description()));
        }
        if (request.status() != null) {
            category.setStatus(request.status());
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }

        category.setUpdatedAt(OffsetDateTime.now());
        categoryMapper.updateById(category);
        return AdvertiserCategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long id) {
        requireCategory(id);
        categoryMapper.deleteById(id);
    }

    AdvertiserCategory requireCategory(Long id) {
        AdvertiserCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(AdvertiserErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    private void ensureNameAvailable(String name, Long currentId) {
        categoryMapper.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new BusinessException(AdvertiserErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
                });
    }

    private void ensureUpdateHasFields(UpdateAdvertiserCategoryRequest request) {
        if (request.name() == null
                && request.description() == null
                && request.status() == null
                && request.sortOrder() == null) {
            throw new BusinessException(AdvertiserErrorCode.CATEGORY_NO_FIELDS_TO_UPDATE);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

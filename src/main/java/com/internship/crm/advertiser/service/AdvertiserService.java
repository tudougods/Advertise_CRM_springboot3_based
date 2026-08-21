package com.internship.crm.advertiser.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.internship.crm.advertiser.api.AdvertiserResponse;
import com.internship.crm.advertiser.api.CreateAdvertiserRequest;
import com.internship.crm.advertiser.api.UpdateAdvertiserRequest;
import com.internship.crm.advertiser.domain.Advertiser;
import com.internship.crm.advertiser.domain.AdvertiserCategory;
import com.internship.crm.advertiser.domain.AdvertiserStatus;
import com.internship.crm.advertiser.error.AdvertiserErrorCode;
import com.internship.crm.advertiser.mapper.AdvertiserCategoryMapper;
import com.internship.crm.advertiser.mapper.AdvertiserMapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.user.domain.User;
import com.internship.crm.user.domain.UserStatus;
import com.internship.crm.user.mapper.UserMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvertiserService {

    private final AdvertiserMapper advertiserMapper;
    private final AdvertiserCategoryMapper categoryMapper;
    private final UserMapper userMapper;

    public AdvertiserService(
            AdvertiserMapper advertiserMapper,
            AdvertiserCategoryMapper categoryMapper,
            UserMapper userMapper) {
        this.advertiserMapper = advertiserMapper;
        this.categoryMapper = categoryMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    public AdvertiserResponse create(CreateAdvertiserRequest request) {
        String name = request.name().trim();
        String registrationNo = trimToNull(request.registrationNo());
        ensureNameAvailable(name, null);
        ensureRegistrationNoAvailable(registrationNo, null);
        validateCategory(request.categoryId());
        validateOwner(request.ownerUserId());

        OffsetDateTime now = OffsetDateTime.now();
        Advertiser advertiser = new Advertiser();
        advertiser.setName(name);
        advertiser.setRegistrationNo(registrationNo);
        advertiser.setCategoryId(request.categoryId());
        advertiser.setOwnerUserId(request.ownerUserId());
        advertiser.setStatus(request.status() == null ? AdvertiserStatus.ACTIVE : request.status());
        advertiser.setWebsite(trimToNull(request.website()));
        advertiser.setAddress(trimToNull(request.address()));
        advertiser.setDescription(trimToNull(request.description()));
        advertiser.setCreatedAt(now);
        advertiser.setUpdatedAt(now);
        advertiserMapper.insert(advertiser);
        return AdvertiserResponse.from(advertiser);
    }

    @Transactional(readOnly = true)
    public List<AdvertiserResponse> findAll() {
        return advertiserMapper.selectList(new LambdaQueryWrapper<Advertiser>().orderByAsc(Advertiser::getId))
                .stream()
                .map(AdvertiserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdvertiserResponse findById(Long id) {
        return AdvertiserResponse.from(requireAdvertiser(id));
    }

    @Transactional
    public AdvertiserResponse update(Long id, UpdateAdvertiserRequest request) {
        ensureUpdateHasFields(request);
        Advertiser advertiser = requireAdvertiser(id);

        if (request.name() != null) {
            String name = request.name().trim();
            if (!advertiser.getName().equalsIgnoreCase(name)) {
                ensureNameAvailable(name, id);
                advertiser.setName(name);
            }
        }
        if (request.registrationNo() != null) {
            String registrationNo = trimToNull(request.registrationNo());
            if (!Objects.equals(advertiser.getRegistrationNo(), registrationNo)) {
                ensureRegistrationNoAvailable(registrationNo, id);
                advertiser.setRegistrationNo(registrationNo);
            }
        }
        if (Boolean.TRUE.equals(request.clearCategory())) {
            advertiser.setCategoryId(null);
        } else if (request.categoryId() != null
                && !Objects.equals(advertiser.getCategoryId(), request.categoryId())) {
            validateCategory(request.categoryId());
            advertiser.setCategoryId(request.categoryId());
        }
        if (Boolean.TRUE.equals(request.clearOwner())) {
            advertiser.setOwnerUserId(null);
        } else if (request.ownerUserId() != null
                && !Objects.equals(advertiser.getOwnerUserId(), request.ownerUserId())) {
            validateOwner(request.ownerUserId());
            advertiser.setOwnerUserId(request.ownerUserId());
        }
        if (request.website() != null) {
            advertiser.setWebsite(trimToNull(request.website()));
        }
        if (request.address() != null) {
            advertiser.setAddress(trimToNull(request.address()));
        }
        if (request.description() != null) {
            advertiser.setDescription(trimToNull(request.description()));
        }

        advertiser.setUpdatedAt(OffsetDateTime.now());
        advertiserMapper.updateById(advertiser);
        return AdvertiserResponse.from(advertiser);
    }

    @Transactional
    public AdvertiserResponse updateStatus(Long id, AdvertiserStatus status) {
        Advertiser advertiser = requireAdvertiser(id);
        advertiser.setStatus(status);
        advertiser.setUpdatedAt(OffsetDateTime.now());
        advertiserMapper.updateById(advertiser);
        return AdvertiserResponse.from(advertiser);
    }

    @Transactional
    public void delete(Long id) {
        requireAdvertiser(id);
        advertiserMapper.deleteById(id);
    }

    Advertiser requireAdvertiser(Long id) {
        Advertiser advertiser = advertiserMapper.selectById(id);
        if (advertiser == null) {
            throw new BusinessException(AdvertiserErrorCode.ADVERTISER_NOT_FOUND);
        }
        return advertiser;
    }

    private void validateCategory(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        AdvertiserCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(AdvertiserErrorCode.CATEGORY_NOT_FOUND);
        }
        if (category.getStatus() != AdvertiserStatus.ACTIVE) {
            throw new BusinessException(AdvertiserErrorCode.CATEGORY_DISABLED);
        }
    }

    private void validateOwner(Long ownerUserId) {
        if (ownerUserId == null) {
            return;
        }
        User owner = userMapper.selectById(ownerUserId);
        if (owner == null) {
            throw new BusinessException(AdvertiserErrorCode.OWNER_NOT_FOUND);
        }
        if (owner.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(AdvertiserErrorCode.OWNER_DISABLED);
        }
    }

    private void ensureNameAvailable(String name, Long currentId) {
        advertiserMapper.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new BusinessException(AdvertiserErrorCode.ADVERTISER_NAME_ALREADY_EXISTS);
                });
    }

    private void ensureRegistrationNoAvailable(String registrationNo, Long currentId) {
        if (registrationNo == null) {
            return;
        }
        advertiserMapper.findByRegistrationNo(registrationNo)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new BusinessException(AdvertiserErrorCode.REGISTRATION_NO_ALREADY_EXISTS);
                });
    }

    private void ensureUpdateHasFields(UpdateAdvertiserRequest request) {
        if (request.name() == null
                && request.registrationNo() == null
                && request.categoryId() == null
                && !Boolean.TRUE.equals(request.clearCategory())
                && request.ownerUserId() == null
                && !Boolean.TRUE.equals(request.clearOwner())
                && request.website() == null
                && request.address() == null
                && request.description() == null) {
            throw new BusinessException(AdvertiserErrorCode.NO_FIELDS_TO_UPDATE);
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

package com.internship.crm.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.user.dto.request.CreateUserRequest;
import com.internship.crm.user.dto.request.UpdateUserRequest;
import com.internship.crm.user.dto.response.UserResponse;
import com.internship.crm.user.entity.User;
import com.internship.crm.user.entity.UserRole;
import com.internship.crm.user.entity.UserStatus;
import com.internship.crm.user.exception.UserErrorCode;
import com.internship.crm.user.mapper.UserMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        return UserResponse.from(createUser(
                request.username(),
                request.password(),
                request.displayName(),
                request.email(),
                request.role(),
                request.status() == null ? UserStatus.ACTIVE : request.status()));
    }

    @Transactional
    public User registerOperator(String username, String password, String displayName, String email) {
        return createUser(username, password, displayName, email, UserRole.OPERATOR, UserStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userMapper.findByUsernameIgnoreCase(username);
    }

    @Transactional(readOnly = true)
    public Optional<User> findEntityById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }

    @Transactional
    public void recordSuccessfulLogin(User user) {
        OffsetDateTime now = OffsetDateTime.now();
        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        userMapper.updateById(user);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("null") // The ORM's serializable getter references lack nullability metadata.
    public List<UserResponse> findAll() {
        return userMapper.selectList(new LambdaQueryWrapper<User>().orderByAsc(User::getId)).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(requireUser(id));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        ensureUpdateHasFields(request);
        User user = requireUser(id);

        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.email() != null) {
            String email = normalizeEmail(request.email());
            if (!equalsIgnoreCase(user.getEmail(), email)) {
                ensureEmailAvailable(email);
                user.setEmail(email);
            }
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        user.setUpdatedAt(OffsetDateTime.now());
        userMapper.updateById(user);
        return UserResponse.from(user);
    }

    @Transactional
    public void delete(Long id) {
        requireUser(id);
        userMapper.deleteById(id);
    }

    User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private void ensureUsernameAvailable(String username) {
        if (userMapper.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException(UserErrorCode.USERNAME_ALREADY_EXISTS);
        }
    }

    private void ensureEmailAvailable(String email) {
        if (email != null && userMapper.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    private void ensureUpdateHasFields(UpdateUserRequest request) {
        if (request.displayName() == null
                && request.email() == null
                && request.password() == null
                && request.role() == null
                && request.status() == null) {
            throw new BusinessException(UserErrorCode.NO_FIELDS_TO_UPDATE);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean equalsIgnoreCase(String first, String second) {
        return first == null ? second == null : first.equalsIgnoreCase(second);
    }

    private User createUser(
            String rawUsername,
            String password,
            String rawDisplayName,
            String rawEmail,
            UserRole role,
            UserStatus status) {
        String username = rawUsername.trim();
        String email = normalizeEmail(rawEmail);
        ensureUsernameAvailable(username);
        ensureEmailAvailable(email);

        OffsetDateTime now = OffsetDateTime.now();
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(rawDisplayName.trim());
        user.setEmail(email);
        user.setRole(role);
        user.setStatus(status);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return user;
    }
}

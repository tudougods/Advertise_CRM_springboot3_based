package com.internship.crm.user.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.testsupport.ReadableTestResultExtension;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("用户 Service 业务规则")
@ExtendWith({MockitoExtension.class, ReadableTestResultExtension.class})
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, passwordEncoder);
    }

    @Test
    @DisplayName("创建用户时规范化字段并只保存 BCrypt 摘要")
    void createNormalizesFieldsAndStoresOnlyThePasswordHash() {
        when(passwordEncoder.encode("SecurePassword123!")).thenReturn("$2a$10$encoded-password");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            inserted.setId(1L);
            return 1;
        });

        UserResponse response = userService.create(new CreateUserRequest(
                "  Admin.User  ",
                "SecurePassword123!",
                "  管理员  ",
                "  ADMIN@Example.COM  ",
                UserRole.ADMIN,
                null));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User inserted = userCaptor.getValue();
        assertAll(
                () -> assertEquals("Admin.User", inserted.getUsername()),
                () -> assertEquals("$2a$10$encoded-password", inserted.getPasswordHash()),
                () -> assertEquals("管理员", inserted.getDisplayName()),
                () -> assertEquals("admin@example.com", inserted.getEmail()),
                () -> assertEquals(UserRole.ADMIN, inserted.getRole()),
                () -> assertEquals(UserStatus.ACTIVE, inserted.getStatus()),
                () -> assertNotNull(inserted.getCreatedAt()),
                () -> assertEquals(1L, response.id()),
                () -> assertFalse(response.toString().contains("encoded-password")));
    }

    @Test
    @DisplayName("重复用户名被明确拒绝且不会保存用户")
    void duplicateUsernameIsRejectedBeforeInsert() {
        when(userMapper.existsByUsernameIgnoreCase("existing.user")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.create(
                createRequest("existing.user", "new@example.com")));

        assertSame(UserErrorCode.USERNAME_ALREADY_EXISTS, exception.errorCode());
        verify(passwordEncoder, never()).encode(any());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("重复邮箱被明确拒绝且不会保存用户")
    void duplicateEmailIsRejectedBeforeInsert() {
        when(userMapper.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.create(
                createRequest("new.user", "Existing@Example.com")));

        assertSame(UserErrorCode.EMAIL_ALREADY_EXISTS, exception.errorCode());
        verify(passwordEncoder, never()).encode(any());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("列表和详情查询只返回不含密码的用户响应")
    void listAndDetailReturnPublicUserResponses() {
        User first = user(1L, "first", UserRole.ADMIN, UserStatus.ACTIVE);
        User second = user(2L, "second", UserRole.OPERATOR, UserStatus.DISABLED);
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));
        when(userMapper.selectById(1L)).thenReturn(first);

        List<UserResponse> list = userService.findAll();
        UserResponse detail = userService.findById(1L);

        assertAll(
                () -> assertEquals(List.of("first", "second"), list.stream().map(UserResponse::username).toList()),
                () -> assertEquals("first", detail.username()),
                () -> assertFalse(detail.toString().contains("password-hash")));
    }

    @Test
    @DisplayName("局部修改只更新提供的字段并重新加密新密码")
    void updateChangesOnlyProvidedFieldsAndRehashesANewPassword() {
        User existing = user(7L, "operator", UserRole.OPERATOR, UserStatus.ACTIVE);
        existing.setEmail("old@example.com");
        OffsetDateTime originalUpdatedAt = existing.getUpdatedAt();
        when(userMapper.selectById(7L)).thenReturn(existing);
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("new-password-hash");

        UserResponse response = userService.update(7L, new UpdateUserRequest(
                "新名称",
                "NEW@Example.com",
                "NewPassword123!",
                UserRole.ADMIN,
                UserStatus.DISABLED));

        assertAll(
                () -> assertEquals("operator", existing.getUsername()),
                () -> assertEquals("新名称", existing.getDisplayName()),
                () -> assertEquals("new@example.com", existing.getEmail()),
                () -> assertEquals("new-password-hash", existing.getPasswordHash()),
                () -> assertEquals(UserRole.ADMIN, response.role()),
                () -> assertEquals(UserStatus.DISABLED, response.status()),
                () -> assertFalse(existing.getUpdatedAt().isBefore(originalUpdatedAt)));
        verify(userMapper).updateById(existing);
    }

    @Test
    @DisplayName("空的局部修改请求被拒绝")
    void emptyUpdateRequestIsRejected() {
        UpdateUserRequest emptyRequest = new UpdateUserRequest(null, null, null, null, null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.update(1L, emptyRequest));

        assertSame(UserErrorCode.NO_FIELDS_TO_UPDATE, exception.errorCode());
        verify(userMapper, never()).selectById(any());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("查询不存在的用户返回用户不存在错误")
    void missingUserReturnsTheUserNotFoundError() {
        when(userMapper.selectById(404L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.findById(404L));

        assertSame(UserErrorCode.USER_NOT_FOUND, exception.errorCode());
    }

    @Test
    @DisplayName("删除用户前确认存在并执行物理删除")
    void deleteChecksExistenceAndPerformsAPhysicalDelete() {
        User existing = user(9L, "delete.me", UserRole.OPERATOR, UserStatus.ACTIVE);
        when(userMapper.selectById(9L)).thenReturn(existing);

        userService.delete(9L);

        verify(userMapper).deleteById(9L);
    }

    @Test
    @DisplayName("公开注册创建的账号固定为启用的 OPERATOR")
    void registrationAlwaysCreatesAnActiveOperator() {
        when(passwordEncoder.encode("SecurePassword123!")).thenReturn("password-hash");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            inserted.setId(10L);
            return 1;
        });

        User registered = userService.registerOperator(
                "registered.user",
                "SecurePassword123!",
                "注册用户",
                null);

        assertAll(
                () -> assertEquals(UserRole.OPERATOR, registered.getRole()),
                () -> assertEquals(UserStatus.ACTIVE, registered.getStatus()),
                () -> assertNull(registered.getEmail()),
                () -> assertEquals("password-hash", registered.getPasswordHash()));
    }

    private CreateUserRequest createRequest(String username, String email) {
        return new CreateUserRequest(
                username,
                "SecurePassword123!",
                "测试用户",
                email,
                UserRole.OPERATOR,
                UserStatus.ACTIVE);
    }

    private User user(Long id, String username, UserRole role, UserStatus status) {
        OffsetDateTime now = OffsetDateTime.now().minusMinutes(1);
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("password-hash");
        user.setDisplayName(username + " display");
        user.setEmail(username + "@example.com");
        user.setRole(role);
        user.setStatus(status);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }
}

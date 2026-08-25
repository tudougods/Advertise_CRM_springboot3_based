package com.internship.crm.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.dto.request.CreateUserRequest;
import com.internship.crm.user.dto.request.UpdateUserRequest;
import com.internship.crm.user.dto.response.UserResponse;
import com.internship.crm.user.entity.UserRole;
import com.internship.crm.user.entity.UserStatus;
import com.internship.crm.user.exception.UserErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Transactional
@DisplayName("最后一个启用管理员数据库保护")
@ExtendWith(ReadableTestResultExtension.class)
class UserAdminInvariantPersistenceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void removeExistingActiveAdminsFromTheTransactionView() {
        jdbcTemplate.update("""
                UPDATE users
                SET role = 'OPERATOR', updated_at = CURRENT_TIMESTAMP
                WHERE role = 'ADMIN' AND status = 'ACTIVE'
                """);
    }

    @Test
    @DisplayName("数据库中唯一启用管理员不能被禁用")
    void soleActiveAdminCannotBeDisabled() {
        UserResponse soleAdmin = createActiveAdmin("disable");

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.update(
                soleAdmin.id(),
                new UpdateUserRequest(null, null, null, null, UserStatus.DISABLED)));

        assertSame(UserErrorCode.LAST_ACTIVE_ADMIN_REQUIRED, exception.errorCode());
        assertEquals(1L, activeAdminCount());
    }

    @Test
    @DisplayName("数据库中唯一启用管理员不能被删除")
    void soleActiveAdminCannotBeDeleted() {
        UserResponse soleAdmin = createActiveAdmin("delete");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.delete(soleAdmin.id()));

        assertSame(UserErrorCode.LAST_ACTIVE_ADMIN_REQUIRED, exception.errorCode());
        assertEquals(1L, activeAdminCount());
    }

    @Test
    @DisplayName("存在两个启用管理员时允许禁用其中一个")
    void oneOfTwoActiveAdminsCanBeDisabled() {
        UserResponse first = createActiveAdmin("first");
        createActiveAdmin("second");

        userService.update(
                first.id(),
                new UpdateUserRequest(null, null, null, null, UserStatus.DISABLED));

        assertEquals(1L, activeAdminCount());
    }

    private UserResponse createActiveAdmin(String label) {
        String suffix = label + "-" + UUID.randomUUID();
        return userService.create(new CreateUserRequest(
                "admin-" + suffix,
                "SecurePassword123!",
                "管理员 " + label,
                suffix + "@example.com",
                UserRole.ADMIN,
                UserStatus.ACTIVE));
    }

    private Long activeAdminCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE'",
                Long.class);
    }
}

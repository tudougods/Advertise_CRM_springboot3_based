package com.internship.crm.user.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.internship.crm.testsupport.ReadableTestResultExtension;
import com.internship.crm.user.dto.request.CreateUserRequest;
import com.internship.crm.user.entity.UserRole;
import com.internship.crm.user.entity.UserStatus;
import com.internship.crm.user.mapper.UserMapper;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@DisplayName("管理员变更并发锁")
@ExtendWith(ReadableTestResultExtension.class)
class UserAdminLockPersistenceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testAdminId;

    @BeforeEach
    void createAnActiveAdminToLock() {
        String suffix = UUID.randomUUID().toString();
        testAdminId = userService.create(new CreateUserRequest(
                "lock-admin-" + suffix,
                "SecurePassword123!",
                "并发锁测试管理员",
                "lock-admin-" + suffix + "@example.com",
                UserRole.ADMIN,
                UserStatus.ACTIVE)).id();
    }

    @AfterEach
    void deleteTheTestAdmin() {
        if (testAdminId != null) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", testAdminId);
        }
    }

    @Test
    @DisplayName("并发管理员变更会等待前一个事务释放行锁")
    void concurrentAdminMutationsAreSerialized() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondIsCalling = new CountDownLatch(1);
        CountDownLatch secondHasLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> transactions.executeWithoutResult(status -> {
                userMapper.selectActiveAdminIdsForUpdate();
                firstHasLock.countDown();
                await(releaseFirst);
            }));
            assertTrue(firstHasLock.await(5, SECONDS));

            Future<?> second = executor.submit(() -> transactions.executeWithoutResult(status -> {
                secondIsCalling.countDown();
                userMapper.selectActiveAdminIdsForUpdate();
                secondHasLock.countDown();
            }));
            assertTrue(secondIsCalling.await(5, SECONDS));
            assertFalse(secondHasLock.await(300, MILLISECONDS),
                    "第二个事务不应在第一个事务提交前取得管理员行锁");

            releaseFirst.countDown();
            first.get(5, SECONDS);
            second.get(5, SECONDS);
            assertTrue(secondHasLock.await(1, SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, SECONDS)) {
                throw new IllegalStateException("timed out waiting for test coordination");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test coordination was interrupted", exception);
        }
    }
}

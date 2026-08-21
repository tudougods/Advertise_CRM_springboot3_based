package com.internship.crm;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.internship.crm.testsupport.ReadableTestResultExtension;

@SpringBootTest
@DisplayName("应用与数据库初始化")
@ExtendWith(ReadableTestResultExtension.class)
class AdvertiserCrmApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("Spring 配置、PostgreSQL、Flyway V1 和三张核心表均正常")
	void applicationContextAndDatabaseInitializationStateAreValid() throws SQLException {
		MigrationInfo coreTableMigration = Arrays.stream(flyway.info().applied())
				.filter(migration -> migration.getVersion() != null)
				.filter(migration -> "1".equals(migration.getVersion().getVersion()))
				.findFirst()
				.orElse(null);
		Long coreTableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = 'public'
				  AND table_name IN ('users', 'advertiser_categories', 'advertisers')
				""", Long.class);

		assertNotNull(coreTableMigration, "数据库中应当存在 Flyway V1 的迁移记录");

		try (Connection connection = dataSource.getConnection()) {
			assertAll("应用与数据库初始化状态检查",
					() -> assertNotNull(applicationContext, "Spring 应用上下文应当成功加载"),
					() -> assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName(),
							"应用应当成功连接 PostgreSQL"),
					() -> assertEquals(MigrationState.SUCCESS, coreTableMigration.getState(),
							"Flyway V1 的迁移历史状态应当为成功"),
					() -> assertEquals(3L, coreTableCount,
							"Sprint 1 的三张核心表应当全部存在"));
		}

	}

}

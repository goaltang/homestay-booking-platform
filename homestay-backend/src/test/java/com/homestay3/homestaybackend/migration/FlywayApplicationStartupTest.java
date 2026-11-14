package com.homestay3.homestaybackend.migration;

import com.homestay3.homestaybackend.HomestayBackendApplication;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用真实 MySQL 启动完整 Spring 应用，覆盖 Spring Boot 的 Flyway 自动配置。
 *
 * <p>{@link FlywayMySqlMigrationTest} 直接调用 Flyway API，H2 测试关闭 Flyway，二者都不经过
 * {@code FlywayAutoConfiguration}。Spring Boot 与 Flyway 版本不兼容时，只有完整启动应用才能发现。
 *
 * <p>与 {@link FlywayMySqlMigrationTest} 使用同一组环境变量和同样的隔离约束：只接受 13316 端口的
 * 本地独立实例和 {@code homestay_migration_test_} 库名前缀；本类自行创建唯一命名的库并在结束后删除。
 * 未配置环境变量时整个类跳过，不启动 Spring 上下文。
 *
 * <p>同时开启 {@code ddl-auto=validate}，校验 JPA 实体映射与 B49 建出的表结构一致。
 */
@EnabledIfEnvironmentVariable(named = "HOMESTAY_MIGRATION_TEST_URL", matches = ".+")
@SpringBootTest(classes = HomestayBackendApplication.class)
@ActiveProfiles("test")
class FlywayApplicationStartupTest {

    private static String serverUrl;
    private static String databaseName;
    private static String username;
    private static String password;
    private static boolean created;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void isolatedMySql(DynamicPropertyRegistry registry) throws Exception {
        String configuredUrl = System.getenv("HOMESTAY_MIGRATION_TEST_URL");
        assertTrue(configuredUrl.startsWith("jdbc:mysql://"), "Only a dedicated MySQL URL is allowed");
        URI address = URI.create(configuredUrl.substring("jdbc:".length()));
        assertTrue("127.0.0.1".equals(address.getHost()) || "localhost".equals(address.getHost()),
                "Migration tests require a local isolated MySQL instance");
        assertEquals(13316, address.getPort(), "Migration tests require the dedicated port 13316");
        assertNull(address.getUserInfo(), "Credentials must come from dedicated test environment variables");
        assertNull(address.getFragment());
        assertTrue(address.getPath().matches("/homestay_migration_test_[a-zA-Z0-9_]+"),
                "Migration tests require the homestay_migration_test_ database prefix");
        username = System.getenv("HOMESTAY_MIGRATION_TEST_USER");
        password = System.getenv("HOMESTAY_MIGRATION_TEST_PASSWORD");
        assertNotNull(username, "Set HOMESTAY_MIGRATION_TEST_USER");
        assertNotNull(password, "Set HOMESTAY_MIGRATION_TEST_PASSWORD");

        String options = address.getRawQuery() == null ? "" : "?" + address.getRawQuery();
        serverUrl = "jdbc:mysql://" + address.getHost() + ":13316/" + options;
        databaseName = "homestay_migration_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + databaseName + "` CHARACTER SET utf8mb4");
            created = true;
        }

        String databaseUrl = "jdbc:mysql://" + address.getHost() + ":13316/" + databaseName + options;
        registry.add("spring.datasource.url", () -> databaseUrl);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.defer-datasource-initialization", () -> "false");
        // 与生产配置一致：不自动 baseline、启动时校验、不允许乱序、禁用 clean
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.flyway.validate-on-migrate", () -> "true");
        registry.add("spring.flyway.out-of-order", () -> "false");
        registry.add("spring.flyway.clean-disabled", () -> "true");
    }

    @AfterAll
    static void dropOwnDatabase() throws Exception {
        if (created) {
            try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + databaseName + "`");
            }
        }
    }

    @Test
    void applicationStartsAndInstallsBaselineThroughSpringFlyway() {
        MigrationInfo current = flyway.info().current();
        assertNotNull(current, "Spring Boot 的 Flyway 自动配置应在启动时完成迁移");

        // 空库从 B49 基线安装，跳过 V1–V49；之后新增的 V 迁移依次执行，不写死当前最新版本
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '49' AND script = 'B49__initial_schema.sql' AND success = 1",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0", Integer.class));
        assertEquals(0, flyway.info().pending().length, "启动后不应有待执行的迁移");
        MigrationVersion latest = Arrays.stream(flyway.info().all())
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull)
                .max(MigrationVersion::compareTo)
                .orElseThrow();
        assertEquals(latest, current.getVersion(), "应迁移到仓库中最新的版本");

        assertDoesNotThrow(flyway::validate);
        assertEquals(0, flyway.migrate().migrationsExecuted, "重复启动不应再次执行迁移");
    }
}

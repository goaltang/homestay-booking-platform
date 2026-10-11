package com.homestay3.homestaybackend.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in tests against a disposable MySQL on port 13316. Set
 * HOMESTAY_MIGRATION_TEST_URL=jdbc:mysql://127.0.0.1:13316/homestay_migration_test_anchor
 * and HOMESTAY_MIGRATION_TEST_USER / HOMESTAY_MIGRATION_TEST_PASSWORD.
 * Each test creates and drops only its own uniquely named database.
 * Flyway runs with baseline-on-migrate disabled and validation enabled.
 */
class FlywayMySqlMigrationTest {
    private String serverUrl;
    private String databaseUrl;
    private String databaseName;
    private String username;
    private String password;
    private boolean created;

    @TempDir
    Path historicalMigrations;

    @BeforeEach
    void createIsolatedDatabase() throws Exception {
        String configuredUrl = System.getenv("HOMESTAY_MIGRATION_TEST_URL");
        assumeTrue(configuredUrl != null && !configuredUrl.isBlank(),
                "Independent MySQL migration tests require HOMESTAY_MIGRATION_TEST_URL");
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
        databaseUrl = "jdbc:mysql://" + address.getHost() + ":13316/" + databaseName + options;
        try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + databaseName + "` CHARACTER SET utf8mb4");
            created = true;
        }
    }

    @AfterEach
    void dropOwnDatabase() throws Exception {
        if (created) {
            try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + databaseName + "`");
            }
        }
    }

    @Test
    void emptyDatabaseUsesBaselineSnapshotAndRestartPreservesData() throws Exception {
        Flyway flyway = configuredFlyway();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        assertEquals(53, scalar("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                + "AND table_name <> 'flyway_schema_history'"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM information_schema.views "
                + "WHERE table_schema = DATABASE() AND table_name IN "
                + "('v_homestay_audit_stats', 'v_reviewer_workload')"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version = '49' AND script = 'B49__initial_schema.sql' AND success = 1"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM flyway_schema_history"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version = '50' AND script = 'V50__create_auth_sessions_table.sql' AND success = 1"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'completed_at'"));
        assertEquals(1, scalar("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'orders' "
                + "AND index_name = 'idx_orders_idempotency_key' AND non_unique = 0"));
        execute("INSERT INTO users (id, username, email, password) "
                + "VALUES (1, 'migration_guest', 'migration_guest@example.test', 'test-password')");
        assertDoesNotThrow(flyway::validate);
        assertEquals(0, configuredFlyway().migrate().migrationsExecuted);
        assertEquals(1, scalar("SELECT COUNT(*) FROM users WHERE id = 1 AND username = 'migration_guest'"));
    }

    @Test
    void existingVersion48UpgradesWithoutExecutingSnapshotOrLosingData() throws Exception {
        // Model V47 structure from the snapshot, then execute the actual V48 migration.
        // This fixture does not replay migrations older than V48.
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/B49__initial_schema.sql"));
        populator.execute(new DriverManagerDataSource(databaseUrl, username, password));
        execute("ALTER TABLE orders DROP COLUMN completed_at");
        execute("DROP TABLE notification_broadcast_jobs");
        ClassPathResource version48 = new ClassPathResource(
                "db/migration/V48__create_notification_broadcast_jobs_table.sql");
        try (var input = version48.getInputStream()) {
            Files.copy(input, historicalMigrations.resolve(version48.getFilename()));
        }
        Flyway historical = Flyway.configure().dataSource(databaseUrl, username, password)
                .locations("filesystem:" + historicalMigrations)
                .baselineVersion("47").baselineDescription("Isolated V47 structure fixture")
                .baselineOnMigrate(false).validateOnMigrate(true).cleanDisabled(true).load();
        historical.baseline();
        assertEquals(1, historical.migrate().migrationsExecuted);
        assertEquals(1, scalar("SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version = '48' AND type = 'SQL' AND success = 1"));
        execute("INSERT INTO users (id, username, email, password) "
                + "VALUES (1, 'old_guest', 'old_guest@example.test', 'test-password')");
        execute("INSERT INTO orders (id, order_number, homestay_id, guest_id, check_in_date, check_out_date, "
                + "nights, guest_count, price, total_amount, status, updated_at) VALUES "
                + "(1, 'migration-order', 1, 1, '2026-01-01', '2026-01-02', 1, 1, 100, 100, "
                + "'COMPLETED', '2026-01-02 12:00:00')");

        Flyway flyway = configuredFlyway();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        assertEquals(1, scalar("SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version = '50' AND script = 'V50__create_auth_sessions_table.sql' AND success = 1"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE script LIKE 'B49%'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version = '49' AND type = 'SQL' AND script = 'V49__add_completed_at_to_orders.sql' "
                + "AND success = 1"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM orders WHERE id = 1 "
                + "AND order_number = 'migration-order' AND total_amount = 100 "
                + "AND completed_at = '2026-01-02 12:00:00'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM users WHERE id = 1 AND username = 'old_guest'"));
        execute("INSERT INTO auth_sessions (id, user_id, credential_digest, expires_at) "
                + "VALUES ('migration-session', 1, REPEAT('a', 64), CURRENT_TIMESTAMP + INTERVAL 1 DAY)");
        assertEquals(1, scalar("SELECT COUNT(*) FROM auth_sessions WHERE user_id = 1"));
        // A real SQL V49 history row must validate alongside the different B49 resource.
        assertDoesNotThrow(flyway::validate);
        assertEquals(0, configuredFlyway().migrate().migrationsExecuted);
    }

    @Test
    void nonEmptyDatabaseWithoutHistoryIsRejectedWithoutChangingData() throws Exception {
        execute("CREATE TABLE legacy_marker (id BIGINT PRIMARY KEY, value_text VARCHAR(50))");
        execute("INSERT INTO legacy_marker VALUES (1, 'keep this legacy row')");
        assertThrows(FlywayException.class, () -> configuredFlyway().migrate());
        assertEquals(1, scalar("SELECT COUNT(*) FROM legacy_marker WHERE id = 1 "
                + "AND value_text = 'keep this legacy row'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"));
    }

    private Flyway configuredFlyway() throws Exception {
        return Flyway.configure().dataSource(databaseUrl, username, password)
                .locations("classpath:db/migration").baselineOnMigrate(false)
                .validateOnMigrate(true).outOfOrder(false).cleanDisabled(true).load();
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int scalar(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}

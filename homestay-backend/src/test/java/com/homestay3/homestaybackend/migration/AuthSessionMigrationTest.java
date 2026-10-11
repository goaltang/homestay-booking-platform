package com.homestay3.homestaybackend.migration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

/** 独立MySQL，仅创建最小users前置表后验证V50新安装与有既有账号的升级。 */
@Testcontainers(disabledWithoutDocker = true)
class AuthSessionMigrationTest {
    @Container
    // 通过实际 JDBC 查询确认就绪，避免临时服务器及 X Plugin 的日志提前满足条件。
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withUsername("root")
            .withPassword("isolated-auth-migration-test")
            .withDatabaseName("isolated_auth_test")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("useSSL", "false")
            .withStartupTimeout(Duration.ofMinutes(2));

    @Test
    void freshSchemaAndExistingAccountUpgrade() throws Exception {
        String url = mysql.getJdbcUrl();
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V50__create_auth_sessions_table.sql"));
        try (Connection connection = DriverManager.getConnection(url, mysql.getUsername(), mysql.getPassword());
             Statement sql = connection.createStatement()) {
            for (String suffix : new String[]{"fresh", "upgrade"}) {
                sql.execute("CREATE DATABASE auth_" + suffix);
                sql.execute("USE auth_" + suffix);
                sql.execute("CREATE TABLE users (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
                if (suffix.equals("upgrade")) sql.execute("INSERT INTO users VALUES (1)");
                sql.execute(migration);
                if (suffix.equals("fresh")) sql.execute("INSERT INTO users VALUES (1)");
                sql.execute("INSERT INTO auth_sessions VALUES ('test-session',1,REPEAT('a',64),CURRENT_TIMESTAMP + INTERVAL 1 DAY)");
                assertThatThrownBy(() -> sql.execute("INSERT INTO auth_sessions VALUES ('invalid',999,REPEAT('a',64),CURRENT_TIMESTAMP)"))
                        .isInstanceOf(java.sql.SQLException.class);
                sql.execute("DELETE FROM users WHERE id=1");
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM auth_sessions")) {
                    rows.next(); assertThat(rows.getInt(1)).isZero();
                }
            }
        }
    }
}

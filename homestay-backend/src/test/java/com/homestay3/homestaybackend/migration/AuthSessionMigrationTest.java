package com.homestay3.homestaybackend.migration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;
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
    static final GenericContainer<?> mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.0"))
            .withEnv("MYSQL_ROOT_PASSWORD", "isolated-auth-migration-test")
            .withEnv("MYSQL_DATABASE", "isolated_auth_test")
            .withExposedPorts(3306)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*\n", 2))
            .withStartupTimeout(Duration.ofMinutes(2));

    @Test
    void freshSchemaAndExistingAccountUpgrade() throws Exception {
        String url = "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306)
                + "/isolated_auth_test?allowPublicKeyRetrieval=true&useSSL=false";
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V50__create_auth_sessions_table.sql"));
        try (Connection connection = DriverManager.getConnection(url, "root", "isolated-auth-migration-test");
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

package com.homestay3.homestaybackend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestDataSourceSafetyInitializerTest {

    private void initialize(String profile, String url) {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("spring.datasource.url", url);
            environment.setActiveProfiles(profile);
            context.setEnvironment(environment);
            context.registerBean(DataSource.class, () -> new DriverManagerDataSource());
            new TestDataSourceSafetyInitializer().initialize(context);
            context.refresh();
        }
    }

    @Test
    void acceptsIsolatedH2() {
        assertDoesNotThrow(() -> initialize("test", "jdbc:h2:mem:safety_probe"));
    }

    @Test
    void rejectsMissingTestProfile() {
        assertThrows(IllegalStateException.class,
                () -> initialize("local", "jdbc:h2:mem:safety_probe"));
    }

    @Test
    void rejectsExternalOrMissingDataSource() {
        for (String url : new String[]{"jdbc:mysql://localhost/homestay_db", "jdbc:h2:file:./data", ""}) {
            assertThrows(IllegalStateException.class, () -> initialize("test", url));
        }
    }

    @Test
    void bootLoadsSafetyInitializerBeforeRefresh() {
        SpringApplication application = new SpringApplication(EmptyConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        assertThrows(IllegalStateException.class, () -> application.run(
                "--spring.profiles.active=local", "--spring.datasource.url=jdbc:h2:mem:safety_probe"));
    }

    @Test
    void rejectsDynamicExternalOverrideBeforeBeanCreation() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("spring.datasource.url", "jdbc:h2:mem:safety_probe");
            environment.setActiveProfiles("test");
            context.setEnvironment(environment);
            context.registerBean(DataSource.class, () -> new DriverManagerDataSource());
            new TestDataSourceSafetyInitializer().initialize(context);
            environment.setProperty("spring.datasource.url", "jdbc:mysql://localhost/homestay_db");
            assertThrows(IllegalStateException.class, context::refresh);
        }
    }

    @Test
    void rejectsHikariOverride() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("spring.datasource.url", "jdbc:h2:mem:safety_probe")
                    .withProperty("spring.datasource.hikari.jdbc-url", "jdbc:mysql://localhost/homestay_db");
            environment.setActiveProfiles("test");
            context.setEnvironment(environment);
            context.registerBean(DataSource.class, () -> new DriverManagerDataSource());
            new TestDataSourceSafetyInitializer().initialize(context);
            assertThrows(IllegalStateException.class, context::refresh);
        }
    }

    @Test
    void allowsContextWithoutDataSource() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            new TestDataSourceSafetyInitializer().initialize(context);
            assertDoesNotThrow(context::refresh);
        }
    }

    @TestConfiguration
    static class EmptyConfiguration {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource();
        }
    }
}

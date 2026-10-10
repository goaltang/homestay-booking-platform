package com.homestay3.homestaybackend.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.Arrays;

/** 在 Spring Boot 测试数据源 Bean 创建前检查数据库隔离配置。 */
public class TestDataSourceSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        // 自动配置和动态属性就绪后、普通 Bean 创建前检查；MVC 切片无数据源时无需检查。
        context.addBeanFactoryPostProcessor(beanFactory -> {
            if (beanFactory.getBeanNamesForType(DataSource.class, true, false).length > 0) {
                validate(context.getEnvironment());
            }
        });
    }

    private void validate(Environment environment) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            throw new IllegalStateException("Spring Boot 测试必须启用 test profile");
        }
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:h2:mem:")) {
            // 不输出连接地址，避免错误报告包含凭据。
            throw new IllegalStateException("Spring Boot 测试数据源必须是 H2 内存库");
        }
        String hikariUrl = environment.getProperty("spring.datasource.hikari.jdbc-url", url);
        if (!hikariUrl.startsWith("jdbc:h2:mem:")
                || environment.containsProperty("spring.datasource.jndi-name")) {
            throw new IllegalStateException("测试不允许覆盖为外部数据源");
        }
    }
}

package com.homestay3.homestaybackend.config;

import com.homestay3.homestaybackend.repository.HomestayDocumentRepository;
import com.homestay3.homestaybackend.service.search.HomestayIndexingService;
import com.homestay3.homestaybackend.service.search.impl.NoOpHomestayIndexingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** 只加载 ES 仓库自动配置和降级服务，不加载数据源或连接外部服务。 */
class OptionalElasticsearchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("sharedDefaults", new ClassPathResource("application.yml"))
                            .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            })
            .withPropertyValues("spring.profiles.active=test", "spring.elasticsearch.uris=http://127.0.0.1:1")
            .withConfiguration(AutoConfigurations.of(ElasticsearchRestClientAutoConfiguration.class,
                    ElasticsearchClientAutoConfiguration.class, ElasticsearchDataAutoConfiguration.class))
            .withUserConfiguration(RepositoryPackage.class, NoOpHomestayIndexingService.class);

    @Test
    void defaultConfigurationStartsWithoutEsRepositoryAndUsesFallback() {
        runner.withConfiguration(AutoConfigurations.of(ElasticsearchRepositoriesAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(HomestayDocumentRepository.class);
                    assertThat(context).hasSingleBean(HomestayIndexingService.class);
                    assertThat(context.getEnvironment().getProperty("management.health.elasticsearch.enabled"))
                            .isEqualTo("false");
                    assertThat(context.getBean(HomestayIndexingService.class).isElasticsearchAvailable()).isFalse();
                    assertThat(context.getEnvironment().getProperty("spring.data.elasticsearch.repositories.enabled"))
                            .isEqualTo("false");
                });
    }

    @Test
    void explicitFalseDisablesRepositoryInitialization() {
        runner.withPropertyValues("elasticsearch.enabled=false")
                .withConfiguration(AutoConfigurations.of(ElasticsearchRepositoriesAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(HomestayDocumentRepository.class);
                    assertThat(context).hasSingleBean(NoOpHomestayIndexingService.class);
                });
    }

    @Test
    void enablingEsAlsoEnablesRepositoriesAndRemovesFallback() {
        runner.withPropertyValues("ELASTICSEARCH_ENABLED=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("elasticsearch.enabled")).isEqualTo("true");
            assertThat(context.getEnvironment().getProperty("spring.data.elasticsearch.repositories.enabled"))
                    .isEqualTo("true");
            assertThat(context).doesNotHaveBean(NoOpHomestayIndexingService.class);
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackages = "com.homestay3.homestaybackend.repository")
    static class RepositoryPackage {
    }
}

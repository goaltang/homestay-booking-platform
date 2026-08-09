package com.homestay3.homestaybackend.api;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * API 测试专用 MQ 支撑配置（新增，非业务代码改动）
 *
 * 背景：application-test.properties 排除了 RabbitAutoConfiguration，
 * 但主代码中的 MQ 生产/消费者（CouponBatchConsumer、CouponBatchProducer、
 * NotificationPushConsumer、NotificationPushProducer、OrderTimeoutProducer）是普通
 * @Component，构造依赖 RabbitTemplate。若不提供该 Bean，任何 @SpringBootTest 应用上下文都无法加载
 * （这是测试环境已有的缺口，与本次任务无关的旧集成测试同样受影响）。
 *
 * 此处提供一个 Mockito mock 的 RabbitTemplate Bean：降级路径下（三个 mq-enabled=false）
 * 不会被调用，只用于满足 Bean 装配；Mock 无副作用、不连接真实 MQ。
 */
@TestConfiguration
public class ApiTestSupportConfig {

    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate() {
        return mock(RabbitTemplate.class);
    }
}

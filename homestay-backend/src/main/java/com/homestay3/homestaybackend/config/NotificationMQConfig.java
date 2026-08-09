package com.homestay3.homestaybackend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationMQConfig {

    public static final String EXCHANGE_NAME = "homestay.notification.exchange";
    public static final String QUEUE_NAME = "homestay.notification.push.queue";
    public static final String RETRY_QUEUE_NAME = "homestay.notification.push.retry.queue";
    public static final String ROUTING_KEY = "notification.push";
    public static final String RETRY_ROUTING_KEY = "notification.push.retry";
    public static final long RETRY_QUEUE_TTL_MS = 60000L;

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue notificationPushQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    /**
     * 重试队列：消息在此队列等待 TTL(60s) 到期后，通过 DLX 转发回主队列再次消费。
     */
    @Bean
    public Queue notificationPushRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE_NAME)
                .ttl((int) RETRY_QUEUE_TTL_MS)
                .deadLetterExchange(EXCHANGE_NAME)
                .deadLetterRoutingKey(ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding notificationPushBinding(Queue notificationPushQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationPushQueue).to(notificationExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding notificationPushRetryBinding(Queue notificationPushRetryQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationPushRetryQueue).to(notificationExchange).with(RETRY_ROUTING_KEY);
    }
}

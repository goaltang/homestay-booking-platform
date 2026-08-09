package com.homestay3.homestaybackend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CouponBatchMQConfig {

    public static final String EXCHANGE_NAME = "homestay.coupon.batch.exchange";
    public static final String QUEUE_NAME = "homestay.coupon.batch.queue";
    public static final String RETRY_QUEUE_NAME = "homestay.coupon.batch.retry.queue";
    public static final String ROUTING_KEY = "coupon.batch";
    public static final String RETRY_ROUTING_KEY = "coupon.batch.retry";
    public static final long RETRY_QUEUE_TTL_MS = 60000L;

    @Bean
    public DirectExchange couponBatchExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue couponBatchQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    /**
     * 重试队列：消息在此队列等待 TTL(60s) 到期后，通过 DLX 转发回主队列再次消费。
     */
    @Bean
    public Queue couponBatchRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE_NAME)
                .ttl((int) RETRY_QUEUE_TTL_MS)
                .deadLetterExchange(EXCHANGE_NAME)
                .deadLetterRoutingKey(ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding couponBatchBinding(Queue couponBatchQueue, DirectExchange couponBatchExchange) {
        return BindingBuilder.bind(couponBatchQueue).to(couponBatchExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding couponBatchRetryBinding(Queue couponBatchRetryQueue, DirectExchange couponBatchExchange) {
        return BindingBuilder.bind(couponBatchRetryQueue).to(couponBatchExchange).with(RETRY_ROUTING_KEY);
    }
}

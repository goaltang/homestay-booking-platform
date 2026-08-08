package com.homestay3.homestaybackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "homestay.order.timeout.exchange";
    public static final String QUEUE_NAME = "homestay.order.timeout.queue";
    public static final String DELAY_QUEUE_NAME = "homestay.order.timeout.delay.queue";
    public static final String ROUTING_KEY = "order.timeout";

    @Bean
    public DirectExchange timeoutExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue timeoutQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    @Bean
    public Binding timeoutBinding(Queue timeoutQueue, DirectExchange timeoutExchange) {
        return BindingBuilder.bind(timeoutQueue).to(timeoutExchange).with(ROUTING_KEY);
    }

    /**
     * 延迟队列：消息在此队列等待 TTL 到期后，通过 DLX 转发到消费队列。
     * 使用消息级 expiration（不设队列级 TTL），三种超时状态共用一个延迟队列。
     * 理由：当前三种超时时长一致（都是 2h），队头阻塞风险低；
     * 消费者通过消息体中的 orderStatus 字段做幂等校验，即使未来时长不同也不影响正确性。
     */
    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE_NAME)
                .deadLetterExchange(EXCHANGE_NAME)
                .deadLetterRoutingKey(ROUTING_KEY)
                .build();
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }
}

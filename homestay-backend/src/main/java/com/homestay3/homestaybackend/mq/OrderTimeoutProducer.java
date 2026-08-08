package com.homestay3.homestaybackend.mq;

import com.homestay3.homestaybackend.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderTimeoutProducer {

    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;

    public void sendDelayMessage(OrderTimeoutMessage msg, long delayMillis) {
        try {
            Message amqpMessage = messageConverter.toMessage(msg, new MessageProperties());
            Message message = MessageBuilder.fromMessage(amqpMessage)
                    .setExpiration(String.valueOf(delayMillis))
                    .build();

            rabbitTemplate.send(RabbitMQConfig.DELAY_QUEUE_NAME, message);
            log.info("发送订单超时延迟消息: orderId={}, status={}, delayMs={}",
                    msg.getOrderId(), msg.getOrderStatus(), delayMillis);
        } catch (Exception e) {
            log.error("发送订单超时延迟消息失败: orderId={}, error={}", msg.getOrderId(), e.getMessage(), e);
        }
    }
}

package com.homestay3.homestaybackend.mq;

import com.homestay3.homestaybackend.config.NotificationMQConfig;
import com.homestay3.homestaybackend.service.WebSocketNotificationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "notification.push.mq-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationPushConsumer {

    private static final String RETRY_COUNT_HEADER = "x-retry-count";
    private static final int MAX_RETRY_COUNT = 3;

    private final WebSocketNotificationService webSocketNotificationService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${notification.push.mq-enabled:true}")
    private boolean mqEnabled;

    @RabbitListener(queues = NotificationMQConfig.QUEUE_NAME, ackMode = "MANUAL")
    public void onMessage(NotificationPushMessage msg, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          @Header(name = RETRY_COUNT_HEADER, required = false) String retryCountHeader) {
        int retryCount = parseRetryCount(retryCountHeader);
        try {
            if (!mqEnabled) {
                log.debug("通知推送 MQ 消费已禁用，跳过消息: userId={}", msg != null ? msg.getUserId() : "null");
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (msg == null || msg.getUserId() == null) {
                log.warn("收到空的通知推送消息，直接 ack");
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (NotificationPushMessage.TYPE_NOTIFICATION.equals(msg.getType())) {
                if (msg.getNotification() == null) {
                    log.warn("NOTIFICATION 类型消息缺少 notification 字段，直接 ack: userId={}", msg.getUserId());
                    channel.basicAck(deliveryTag, false);
                    return;
                }
                webSocketNotificationService.sendNotificationToUser(msg.getUserId(), msg.getNotification());
            } else if (NotificationPushMessage.TYPE_UNREAD_COUNT.equals(msg.getType())) {
                webSocketNotificationService.sendUnreadCountToUser(
                        msg.getUserId(), msg.getUnreadCount() != null ? msg.getUnreadCount() : 0L);
            } else {
                log.warn("未知的通知推送消息类型: {}, 直接 ack: userId={}", msg.getType(), msg.getUserId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("通知推送消费异常: userId={}, type={}, retryCount={}, error={}",
                    msg != null ? msg.getUserId() : "null", msg != null ? msg.getType() : "null",
                    retryCount, e.getMessage(), e);
            try {
                if (retryCount >= MAX_RETRY_COUNT) {
                    log.error("通知推送重试次数已达上限({})，丢弃消息: userId={}", MAX_RETRY_COUNT,
                            msg != null ? msg.getUserId() : "null");
                    channel.basicAck(deliveryTag, false);
                } else {
                    republishToRetryQueue(msg, retryCount + 1);
                    channel.basicAck(deliveryTag, false);
                }
            } catch (Exception retryEx) {
                log.error("通知推送重试处理失败，basicNack: userId={}, error={}",
                        msg != null ? msg.getUserId() : "null", retryEx.getMessage(), retryEx);
                try {
                    channel.basicNack(deliveryTag, false, false);
                } catch (Exception nackEx) {
                    log.error("basicNack 失败: {}", nackEx.getMessage());
                }
            }
        }
    }

    /**
     * 重新投递到重试队列：带 x-retry-count header，TTL 60s 到期后死信回主队列再次消费。
     */
    private void republishToRetryQueue(NotificationPushMessage msg, int newRetryCount) {
        rabbitTemplate.convertAndSend(
                NotificationMQConfig.EXCHANGE_NAME,
                NotificationMQConfig.RETRY_ROUTING_KEY,
                msg,
                m -> {
                    m.getMessageProperties().setHeader(RETRY_COUNT_HEADER, newRetryCount);
                    return m;
                });
        log.info("通知推送消息已投递到重试队列: userId={}, type={}, newRetryCount={}",
                msg != null ? msg.getUserId() : "null", msg != null ? msg.getType() : "null", newRetryCount);
    }

    private int parseRetryCount(String retryCountHeader) {
        if (retryCountHeader == null || retryCountHeader.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(retryCountHeader.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

package com.homestay3.homestaybackend.mq;

import com.homestay3.homestaybackend.config.NotificationMQConfig;
import com.homestay3.homestaybackend.dto.NotificationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "notification.push.mq-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationPushProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendNotification(Long userId, NotificationDTO notification) {
        try {
            NotificationPushMessage message = new NotificationPushMessage(
                    NotificationPushMessage.TYPE_NOTIFICATION, userId, notification, null);
            rabbitTemplate.convertAndSend(
                    NotificationMQConfig.EXCHANGE_NAME,
                    NotificationMQConfig.ROUTING_KEY,
                    message);
            log.debug("发送通知推送 MQ 消息: userId={}, notificationId={}",
                    userId, notification != null ? notification.getId() : null);
        } catch (Exception e) {
            log.error("发送通知推送 MQ 消息失败: userId={}, error={}", userId, e.getMessage(), e);
        }
    }

    public void sendUnreadCount(Long userId, long unreadCount) {
        try {
            NotificationPushMessage message = new NotificationPushMessage(
                    NotificationPushMessage.TYPE_UNREAD_COUNT, userId, null, unreadCount);
            rabbitTemplate.convertAndSend(
                    NotificationMQConfig.EXCHANGE_NAME,
                    NotificationMQConfig.ROUTING_KEY,
                    message);
            log.debug("发送未读通知数 MQ 消息: userId={}, unreadCount={}", userId, unreadCount);
        } catch (Exception e) {
            log.error("发送未读通知数 MQ 消息失败: userId={}, error={}", userId, e.getMessage(), e);
        }
    }
}

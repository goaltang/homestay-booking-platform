package com.homestay3.homestaybackend.mq;

import com.homestay3.homestaybackend.config.NotificationMQConfig;
import com.homestay3.homestaybackend.dto.NotificationDTO;
import com.homestay3.homestaybackend.service.WebSocketNotificationService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class NotificationPushConsumerTest {

    @Mock
    private WebSocketNotificationService webSocketNotificationService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private Channel channel;

    @InjectMocks
    private NotificationPushConsumer consumer;

    private static final long DELIVERY_TAG = 1L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "mqEnabled", true);
    }

    private NotificationDTO notification(Long id) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(id);
        dto.setUserId(1L);
        dto.setTitle("测试通知");
        dto.setContent("测试内容");
        return dto;
    }

    @Test
    void onMessage_notificationType_shouldPushAndAck() throws Exception {
        NotificationDTO dto = notification(100L);
        NotificationPushMessage msg = new NotificationPushMessage(
                NotificationPushMessage.TYPE_NOTIFICATION, 1L, dto, null);

        consumer.onMessage(msg, channel, DELIVERY_TAG, "0");

        verify(webSocketNotificationService).sendNotificationToUser(1L, dto);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_unreadCountType_shouldPushAndAck() throws Exception {
        NotificationPushMessage msg = new NotificationPushMessage(
                NotificationPushMessage.TYPE_UNREAD_COUNT, 2L, null, 5L);

        consumer.onMessage(msg, channel, DELIVERY_TAG, "0");

        verify(webSocketNotificationService).sendUnreadCountToUser(2L, 5L);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_mqDisabled_shouldAckAndSkip() throws Exception {
        ReflectionTestUtils.setField(consumer, "mqEnabled", false);
        NotificationPushMessage msg = new NotificationPushMessage(
                NotificationPushMessage.TYPE_NOTIFICATION, 1L, notification(200L), null);

        consumer.onMessage(msg, channel, DELIVERY_TAG, "0");

        verify(webSocketNotificationService, never()).sendNotificationToUser(anyLong(), any());
        verify(webSocketNotificationService, never()).sendUnreadCountToUser(anyLong(), anyLong());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_nullMessage_shouldAckAndSkip() throws Exception {
        consumer.onMessage(null, channel, DELIVERY_TAG, null);

        verify(webSocketNotificationService, never()).sendNotificationToUser(anyLong(), any());
        verify(webSocketNotificationService, never()).sendUnreadCountToUser(anyLong(), anyLong());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_userIdNull_shouldAckAndSkip() throws Exception {
        NotificationPushMessage msg = new NotificationPushMessage(
                NotificationPushMessage.TYPE_NOTIFICATION, null, notification(300L), null);

        consumer.onMessage(msg, channel, DELIVERY_TAG, null);

        verify(webSocketNotificationService, never()).sendNotificationToUser(anyLong(), any());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_notificationNull_shouldAckAndSkip() throws Exception {
        NotificationPushMessage msg = new NotificationPushMessage(
                NotificationPushMessage.TYPE_NOTIFICATION, 1L, null, null);

        consumer.onMessage(msg, channel, DELIVERY_TAG, "0");

        verify(webSocketNotificationService, never()).sendNotificationToUser(anyLong(), any());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_unknownType_shouldAckAndSkip() throws Exception {
        NotificationPushMessage msg = new NotificationPushMessage("UNKNOWN_TYPE", 1L, null, null);

        consumer.onMessage(msg, channel, DELIVERY_TAG, "0");

        verify(webSocketNotificationService, never()).sendNotificationToUser(anyLong(), any());
        verify(webSocketNotificationService, never()).sendUnreadCountToUser(anyLong(), anyLong());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_exception_retryCount0_shouldRepublishWithRetry1AndAck() throws Exception {
        NotificationDTO dto = notification(400L);
        NotificationPushMessage msg = new NotificationPushMessage(
                NotificationPushMessage.TYPE_NOTIFICATION, 1L, dto, null);
        doThrow(new RuntimeException("push error"))
                .when(webSocketNotificationService).sendNotificationToUser(1L, dto);

        consumer.onMessage(msg, channel, DELIVERY_TAG, null);

        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(NotificationMQConfig.EXCHANGE_NAME),
                eq(NotificationMQConfig.RETRY_ROUTING_KEY), eq(msg), captor.capture());
        verify(channel).basicAck(DELIVERY_TAG, false);

        MessageProperties props = new MessageProperties();
        Message m = new Message(new byte[0], props);
        captor.getValue().postProcessMessage(m);
        assertEquals(Integer.valueOf(1), props.getHeader("x-retry-count"));
    }

    @Test
    void onMessage_exception_retryCount3_shouldDropAndAck() throws Exception {
        NotificationDTO dto = notification(500L);
        NotificationPushMessage msg = new NotificationPushMessage(
                NotificationPushMessage.TYPE_NOTIFICATION, 1L, dto, null);
        doThrow(new RuntimeException("push error"))
                .when(webSocketNotificationService).sendNotificationToUser(1L, dto);

        consumer.onMessage(msg, channel, DELIVERY_TAG, "3");

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
        verify(channel).basicAck(DELIVERY_TAG, false);
    }
}

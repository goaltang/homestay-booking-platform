package com.homestay3.homestaybackend.mq;

import com.homestay3.homestaybackend.config.CouponBatchMQConfig;
import com.homestay3.homestaybackend.service.CouponBatchIssueService;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponBatchConsumerTest {

    @Mock
    private CouponBatchIssueService couponBatchIssueService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private Channel channel;

    @InjectMocks
    private CouponBatchConsumer consumer;

    private static final long DELIVERY_TAG = 1L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "mqEnabled", true);
    }

    @Test
    void onMessage_success_shouldAck() throws Exception {
        CouponBatchMessage msg = new CouponBatchMessage(100L);

        consumer.onMessage(msg, channel, DELIVERY_TAG, "0");

        verify(couponBatchIssueService).executeBatchTask(100L);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_taskNotExist_serviceNoThrow_shouldAck() throws Exception {
        CouponBatchMessage msg = new CouponBatchMessage(200L);
        doNothing().when(couponBatchIssueService).executeBatchTask(200L);

        consumer.onMessage(msg, channel, DELIVERY_TAG, "0");

        verify(couponBatchIssueService).executeBatchTask(200L);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_mqDisabled_shouldAckAndSkip() throws Exception {
        ReflectionTestUtils.setField(consumer, "mqEnabled", false);
        CouponBatchMessage msg = new CouponBatchMessage(300L);

        consumer.onMessage(msg, channel, DELIVERY_TAG, "0");

        verify(couponBatchIssueService, never()).executeBatchTask(anyLong());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_nullMessage_shouldAckAndSkip() throws Exception {
        consumer.onMessage(null, channel, DELIVERY_TAG, null);

        verify(couponBatchIssueService, never()).executeBatchTask(anyLong());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_taskIdNull_shouldAckAndSkip() throws Exception {
        CouponBatchMessage msg = new CouponBatchMessage(null);

        consumer.onMessage(msg, channel, DELIVERY_TAG, null);

        verify(couponBatchIssueService, never()).executeBatchTask(anyLong());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_exception_retryCount0_shouldRepublishWithRetry1AndAck() throws Exception {
        CouponBatchMessage msg = new CouponBatchMessage(500L);
        doThrow(new RuntimeException("db error")).when(couponBatchIssueService).executeBatchTask(500L);

        consumer.onMessage(msg, channel, DELIVERY_TAG, null);

        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(CouponBatchMQConfig.EXCHANGE_NAME),
                eq(CouponBatchMQConfig.RETRY_ROUTING_KEY), eq(msg), captor.capture());
        verify(channel).basicAck(DELIVERY_TAG, false);

        MessageProperties props = new MessageProperties();
        Message m = new Message(new byte[0], props);
        captor.getValue().postProcessMessage(m);
        assertEquals(Integer.valueOf(1), props.getHeader("x-retry-count"));
    }

    @Test
    void onMessage_exception_retryCount2_shouldRepublishWithRetry3AndAck() throws Exception {        CouponBatchMessage msg = new CouponBatchMessage(600L);
        doThrow(new RuntimeException("db error")).when(couponBatchIssueService).executeBatchTask(600L);

        consumer.onMessage(msg, channel, DELIVERY_TAG, "2");

        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(CouponBatchMQConfig.EXCHANGE_NAME),
                eq(CouponBatchMQConfig.RETRY_ROUTING_KEY), eq(msg), captor.capture());
        verify(channel).basicAck(DELIVERY_TAG, false);

        MessageProperties props = new MessageProperties();
        Message m = new Message(new byte[0], props);
        captor.getValue().postProcessMessage(m);
        assertEquals(Integer.valueOf(3), props.getHeader("x-retry-count"));
    }

    @Test
    void onMessage_exception_retryCount3_shouldDropAndAck() throws Exception {
        CouponBatchMessage msg = new CouponBatchMessage(700L);
        doThrow(new RuntimeException("db error")).when(couponBatchIssueService).executeBatchTask(700L);

        consumer.onMessage(msg, channel, DELIVERY_TAG, "3");

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_exception_retryHeaderInvalid_shouldTreatAs0() throws Exception {
        CouponBatchMessage msg = new CouponBatchMessage(800L);
        doThrow(new RuntimeException("db error")).when(couponBatchIssueService).executeBatchTask(800L);

        consumer.onMessage(msg, channel, DELIVERY_TAG, "abc");

        verify(rabbitTemplate).convertAndSend(eq(CouponBatchMQConfig.EXCHANGE_NAME),
                eq(CouponBatchMQConfig.RETRY_ROUTING_KEY), eq(msg), any(MessagePostProcessor.class));
        verify(channel).basicAck(DELIVERY_TAG, false);
    }
}

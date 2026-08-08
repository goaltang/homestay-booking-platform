package com.homestay3.homestaybackend.mq;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.NotificationService;
import com.homestay3.homestaybackend.service.OrderService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderTimeoutConsumerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Channel channel;

    @InjectMocks
    private OrderTimeoutConsumer consumer;

    private static final long DELIVERY_TAG = 1L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "mqEnabled", true);
    }

    @Test
    void onMessage_statusMatches_shouldCancelOrder() throws Exception {
        OrderTimeoutMessage msg = new OrderTimeoutMessage(100L, "PENDING", LocalDateTime.now());
        Order order = Order.builder().id(100L).orderNumber("HS20260809001").status("PENDING").build();
        order.setGuest(new User());
        order.getGuest().setId(1L);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderService.systemCancelOrder(eq(100L), eq(OrderStatus.CANCELLED_SYSTEM.name()), anyString()))
                .thenReturn(OrderDTO.builder().id(100L).status(OrderStatus.CANCELLED_SYSTEM.name()).build());

        consumer.onMessage(msg, channel, DELIVERY_TAG);

        verify(orderService).systemCancelOrder(eq(100L), eq(OrderStatus.CANCELLED_SYSTEM.name()), anyString());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_statusMismatch_shouldSkipCancel() throws Exception {
        OrderTimeoutMessage msg = new OrderTimeoutMessage(200L, "PENDING", LocalDateTime.now());
        Order order = Order.builder().id(200L).status("PAID").build();

        when(orderRepository.findById(200L)).thenReturn(Optional.of(order));

        consumer.onMessage(msg, channel, DELIVERY_TAG);

        verify(orderService, never()).systemCancelOrder(anyLong(), anyString(), anyString());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_orderNotFound_shouldAckAndSkip() throws Exception {
        OrderTimeoutMessage msg = new OrderTimeoutMessage(300L, "CONFIRMED", LocalDateTime.now());

        when(orderRepository.findById(300L)).thenReturn(Optional.empty());

        consumer.onMessage(msg, channel, DELIVERY_TAG);

        verify(orderService, never()).systemCancelOrder(anyLong(), anyString(), anyString());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_mqDisabled_shouldAckAndSkip() throws Exception {
        ReflectionTestUtils.setField(consumer, "mqEnabled", false);
        OrderTimeoutMessage msg = new OrderTimeoutMessage(400L, "PENDING", LocalDateTime.now());

        consumer.onMessage(msg, channel, DELIVERY_TAG);

        verify(orderRepository, never()).findById(anyLong());
        verify(orderService, never()).systemCancelOrder(anyLong(), anyString(), anyString());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_nullMessage_shouldAckAndSkip() throws Exception {
        consumer.onMessage(null, channel, DELIVERY_TAG);

        verify(orderService, never()).systemCancelOrder(anyLong(), anyString(), anyString());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_confirmedStatus_matches_shouldCancel() throws Exception {
        OrderTimeoutMessage msg = new OrderTimeoutMessage(500L, "CONFIRMED", LocalDateTime.now());
        Order order = Order.builder().id(500L).orderNumber("HS20260809002").status("CONFIRMED").build();
        order.setGuest(new User());
        order.getGuest().setId(2L);

        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(orderService.systemCancelOrder(eq(500L), eq(OrderStatus.CANCELLED_SYSTEM.name()), anyString()))
                .thenReturn(OrderDTO.builder().id(500L).status(OrderStatus.CANCELLED_SYSTEM.name()).build());

        consumer.onMessage(msg, channel, DELIVERY_TAG);

        verify(orderService).systemCancelOrder(eq(500L), eq(OrderStatus.CANCELLED_SYSTEM.name()), contains("MQ延迟队列"));
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void onMessage_paymentPendingStatus_matches_shouldCancel() throws Exception {
        OrderTimeoutMessage msg = new OrderTimeoutMessage(600L, "PAYMENT_PENDING", LocalDateTime.now());
        Order order = Order.builder().id(600L).orderNumber("HS20260809003").status("PAYMENT_PENDING").build();
        order.setGuest(new User());
        order.getGuest().setId(3L);

        when(orderRepository.findById(600L)).thenReturn(Optional.of(order));
        when(orderService.systemCancelOrder(eq(600L), eq(OrderStatus.CANCELLED_SYSTEM.name()), anyString()))
                .thenReturn(OrderDTO.builder().id(600L).status(OrderStatus.CANCELLED_SYSTEM.name()).build());

        consumer.onMessage(msg, channel, DELIVERY_TAG);

        verify(orderService).systemCancelOrder(eq(600L), eq(OrderStatus.CANCELLED_SYSTEM.name()), contains("支付超时"));
        verify(channel).basicAck(DELIVERY_TAG, false);
    }
}

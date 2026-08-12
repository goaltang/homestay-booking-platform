package com.homestay3.homestaybackend.mq;

import com.homestay3.homestaybackend.config.MetricsConfig;
import com.homestay3.homestaybackend.config.RabbitMQConfig;
import com.homestay3.homestaybackend.dto.NotificationCreateCommand;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.notification.OrderNotificationEventType;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.NotificationService;
import com.homestay3.homestaybackend.service.OrderService;
import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "order.timeout.mq-enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutConsumer {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final NotificationService notificationService;

    /**
     * 埋点用 registry。字段注入（非 final）：保持现有构造器不变，
     * 纯 Mockito 单元测试（@InjectMocks）不注入该字段，由 MetricsConfig 静默跳过。
     */
    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${order.timeout.mq-enabled:true}")
    private boolean mqEnabled;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME, ackMode = "MANUAL")
    public void onMessage(OrderTimeoutMessage msg, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            if (!mqEnabled) {
                log.debug("MQ 超时消费已禁用，跳过消息: orderId={}", msg != null ? msg.getOrderId() : "null");
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (msg == null || msg.getOrderId() == null) {
                log.warn("收到空的超时消息，直接 ack");
                channel.basicAck(deliveryTag, false);
                return;
            }

            Order order = orderRepository.findById(msg.getOrderId()).orElse(null);
            if (order == null) {
                log.warn("超时消息对应订单不存在，直接 ack: orderId={}", msg.getOrderId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (!msg.getOrderStatus().equals(order.getStatus())) {
                log.info("订单状态已流转，跳过超时取消: orderId={}, 期望={}, 实际={}",
                        msg.getOrderId(), msg.getOrderStatus(), order.getStatus());
                channel.basicAck(deliveryTag, false);
                return;
            }

            String reason;
            switch (msg.getOrderStatus()) {
                case "PENDING":
                    reason = "系统自动取消：超时未确认（MQ延迟队列）";
                    break;
                case "CONFIRMED":
                    reason = "系统自动取消：超时未支付（MQ延迟队列）";
                    break;
                case "PAYMENT_PENDING":
                    reason = "系统自动取消：支付超时（MQ延迟队列）";
                    break;
                default:
                    reason = "系统自动取消：超时（MQ延迟队列）";
            }

            orderService.systemCancelOrder(msg.getOrderId(), OrderStatus.CANCELLED_SYSTEM.name(), reason);
            log.info("MQ 延迟队列超时取消订单成功: orderId={}, status={}", msg.getOrderId(), msg.getOrderStatus());

            sendOrderNotification(order.getGuest().getId(),
                    OrderNotificationEventType.ORDER_STATUS_CHANGED,
                    "您的订单 " + order.getOrderNumber() + " 因超时已被系统自动取消",
                    msg.getOrderId().toString());

            MetricsConfig.increment(meterRegistry, "homestay.mq.consumed", "scenario", "order.timeout");
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("处理超时消息异常: orderId={}, error={}",
                    msg != null ? msg.getOrderId() : "null", e.getMessage(), e);
            MetricsConfig.increment(meterRegistry, "homestay.mq.retried", "scenario", "order.timeout");
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception nackEx) {
                log.error("basicNack 失败: {}", nackEx.getMessage());
            }
        }
    }

    private void sendOrderNotification(Long userId, OrderNotificationEventType eventType, String content, String entityId) {
        try {
            notificationService.createNotification(
                    NotificationCreateCommand.orderEvent(userId, null, eventType, entityId, content));
        } catch (Exception e) {
            log.error("发送订单超时通知失败: userId={}, error={}", userId, e.getMessage(), e);
        }
    }
}

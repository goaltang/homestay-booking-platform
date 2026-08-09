package com.homestay3.homestaybackend.mq;

import com.homestay3.homestaybackend.config.CouponBatchMQConfig;
import com.homestay3.homestaybackend.service.CouponBatchIssueService;
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
@ConditionalOnProperty(name = "coupon.batch.mq-enabled", havingValue = "true", matchIfMissing = true)
public class CouponBatchConsumer {

    private static final String RETRY_COUNT_HEADER = "x-retry-count";
    private static final int MAX_RETRY_COUNT = 3;

    private final CouponBatchIssueService couponBatchIssueService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${coupon.batch.mq-enabled:true}")
    private boolean mqEnabled;

    @RabbitListener(queues = CouponBatchMQConfig.QUEUE_NAME, ackMode = "MANUAL")
    public void onMessage(CouponBatchMessage msg, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          @Header(name = RETRY_COUNT_HEADER, required = false) String retryCountHeader) {
        int retryCount = parseRetryCount(retryCountHeader);
        try {
            if (!mqEnabled) {
                log.debug("批量发券 MQ 消费已禁用，跳过消息: taskId={}", msg != null ? msg.getTaskId() : "null");
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (msg == null || msg.getTaskId() == null) {
                log.warn("收到空的批量发券消息，直接 ack");
                channel.basicAck(deliveryTag, false);
                return;
            }

            couponBatchIssueService.executeBatchTask(msg.getTaskId());
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("批量发券消费异常: taskId={}, retryCount={}, error={}",
                    msg != null ? msg.getTaskId() : "null", retryCount, e.getMessage(), e);
            try {
                if (retryCount >= MAX_RETRY_COUNT) {
                    log.error("批量发券重试次数已达上限({})，丢弃消息: taskId={}", MAX_RETRY_COUNT,
                            msg != null ? msg.getTaskId() : "null");
                    channel.basicAck(deliveryTag, false);
                } else {
                    republishToRetryQueue(msg, retryCount + 1);
                    channel.basicAck(deliveryTag, false);
                }
            } catch (Exception retryEx) {
                log.error("批量发券重试处理失败，basicNack: taskId={}, error={}",
                        msg != null ? msg.getTaskId() : "null", retryEx.getMessage(), retryEx);
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
    private void republishToRetryQueue(CouponBatchMessage msg, int newRetryCount) {
        rabbitTemplate.convertAndSend(
                CouponBatchMQConfig.EXCHANGE_NAME,
                CouponBatchMQConfig.RETRY_ROUTING_KEY,
                msg,
                m -> {
                    m.getMessageProperties().setHeader(RETRY_COUNT_HEADER, newRetryCount);
                    return m;
                });
        log.info("批量发券消息已投递到重试队列: taskId={}, newRetryCount={}",
                msg != null ? msg.getTaskId() : "null", newRetryCount);
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

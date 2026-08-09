package com.homestay3.homestaybackend.mq;

import com.homestay3.homestaybackend.config.CouponBatchMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CouponBatchProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 首次发送直接发到主队列（无延迟，立即执行），失败重试才走重试队列。
     */
    public void sendTask(Long taskId) {
        try {
            rabbitTemplate.convertAndSend(
                    CouponBatchMQConfig.EXCHANGE_NAME,
                    CouponBatchMQConfig.ROUTING_KEY,
                    new CouponBatchMessage(taskId));
            log.info("发送批量发券 MQ 消息: taskId={}", taskId);
        } catch (Exception e) {
            log.error("发送批量发券 MQ 消息失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }
}

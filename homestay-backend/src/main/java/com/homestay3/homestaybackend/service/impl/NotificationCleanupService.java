package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.annotation.RedisLock;
import com.homestay3.homestaybackend.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationCleanupService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupService.class);

    private final NotificationService notificationService;

    @Value("${notification.cleanup.days:30}")
    private int cleanupDays;

    public NotificationCleanupService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点执行
    @RedisLock(key = "notification:cleanup", timeoutSeconds = 600)
    public void cleanupOldNotifications() {
        log.info("开始清理过期通知...");
        try {
            int deletedCount = notificationService.cleanupOldNotifications(cleanupDays);
            log.info("通知清理完成，删除了 {} 条过期通知", deletedCount);
        } catch (Exception e) {
            log.error("清理通知时发生错误: {}", e.getMessage(), e);
        }
    }
}

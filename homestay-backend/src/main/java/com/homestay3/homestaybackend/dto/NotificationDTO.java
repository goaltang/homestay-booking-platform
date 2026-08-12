package com.homestay3.homestaybackend.dto;

import com.homestay3.homestaybackend.model.enums.EntityType;
import com.homestay3.homestaybackend.model.enums.NotificationType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 通知数据传输对象 (用于 API 响应)
 */
@Data
@NoArgsConstructor
public class NotificationDTO {
    private Long id;
    private Long userId;
    private Long actorId;
    private String actorUsername;
    private NotificationType type;
    private String rawType;
    private EntityType entityType;
    private String rawEntityType;
    private String entityId;
    private String entityTitle;
    private String category;
    private String title;
    private String deepLink;
    private Map<String, Object> payload;
    private String content;
    private boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    // 可以添加 actorUsername 或 entityTitle 等字段，如果需要减少前端查询
    // private String actorUsername;
    // private String entityTitle; // 例如: 房源名称
}

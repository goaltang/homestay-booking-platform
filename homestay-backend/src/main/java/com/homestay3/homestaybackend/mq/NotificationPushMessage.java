package com.homestay3.homestaybackend.mq;

import com.homestay3.homestaybackend.dto.NotificationDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPushMessage {

    public static final String TYPE_NOTIFICATION = "NOTIFICATION";
    public static final String TYPE_UNREAD_COUNT = "UNREAD_COUNT";

    private String type;
    private Long userId;
    private NotificationDTO notification;
    private Long unreadCount;
}

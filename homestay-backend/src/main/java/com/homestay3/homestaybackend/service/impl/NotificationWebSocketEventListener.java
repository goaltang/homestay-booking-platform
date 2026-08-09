package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.event.NotificationCreatedEvent;
import com.homestay3.homestaybackend.event.NotificationUnreadCountChangedEvent;
import com.homestay3.homestaybackend.mq.NotificationPushProducer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationWebSocketEventListener {

    private final ObjectProvider<NotificationPushProducer> notificationPushProducerProvider;

    public NotificationWebSocketEventListener(ObjectProvider<NotificationPushProducer> notificationPushProducerProvider) {
        this.notificationPushProducerProvider = notificationPushProducerProvider;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        NotificationPushProducer producer = notificationPushProducerProvider.getIfAvailable();
        if (producer != null) {
            producer.sendNotification(event.userId(), event.notification());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUnreadCountChanged(NotificationUnreadCountChangedEvent event) {
        NotificationPushProducer producer = notificationPushProducerProvider.getIfAvailable();
        if (producer != null) {
            producer.sendUnreadCount(event.userId(), event.unreadCount());
        }
    }
}

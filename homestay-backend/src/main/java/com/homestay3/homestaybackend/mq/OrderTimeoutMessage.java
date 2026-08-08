package com.homestay3.homestaybackend.mq;

import java.io.Serializable;
import java.time.LocalDateTime;

public class OrderTimeoutMessage implements Serializable {

    private Long orderId;
    private String orderStatus;
    private LocalDateTime expireAt;

    public OrderTimeoutMessage() {
    }

    public OrderTimeoutMessage(Long orderId, String orderStatus, LocalDateTime expireAt) {
        this.orderId = orderId;
        this.orderStatus = orderStatus;
        this.expireAt = expireAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }
}

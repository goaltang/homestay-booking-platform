package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.dto.ReviewDTO;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.Review;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.repository.ReviewRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.SystemConfigService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 装配现有订单响应，保留生命周期、管理列表与支付结果的字段边界。
 */
@Component
public class OrderDtoAssembler {
    private static final Logger log = LoggerFactory.getLogger(OrderDtoAssembler.class);

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<SystemConfigService> systemConfigServiceProvider;

    public OrderDtoAssembler(ReviewRepository reviewRepository,
                             UserRepository userRepository,
                             ObjectProvider<SystemConfigService> systemConfigServiceProvider) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.systemConfigServiceProvider = systemConfigServiceProvider;
    }

    public OrderDTO toLifecycleResult(Order order) {
        if (order == null) {
            return null;
        }
        OrderDTO.OrderDTOBuilder builder = OrderDTO.builder();
        addReview(order, builder);
        addHost(order, builder);
        addGuest(order, builder);
        addRefundDetails(order, builder);
        addBaseFieldsAndFees(order, builder);
        addHomestay(order, builder);
        return builder.imageUrl(order.getHomestay() != null ? order.getHomestay().getCoverImage() : null)
                .build();
    }

    public OrderDTO toAdminListItem(Order order) {
        if (order == null) {
            return null;
        }
        OrderDTO.OrderDTOBuilder builder = OrderDTO.builder();
        addReview(order, builder);
        try {
            addHost(order, builder);
        } catch (EntityNotFoundException e) {
            log.warn("订单 {} 关联的homestay已被删除", order.getId());
        }
        try {
            addGuest(order, builder);
        } catch (EntityNotFoundException e) {
            log.warn("订单 {} 关联的guest用户已被删除", order.getId());
        }
        addRefundDetails(order, builder);
        addBaseFieldsAndFees(order, builder);
        try {
            addHomestay(order, builder);
        } catch (EntityNotFoundException e) {
            log.warn("订单 {} 关联的homestay已被删除", order.getId());
        }
        return builder.build();
    }

    public OrderDTO toPaymentResult(Order order) {
        if (order == null) {
            return null;
        }
        OrderDTO.OrderDTOBuilder builder = OrderDTO.builder();
        addHost(order, builder);
        addGuest(order, builder);
        addBaseFieldsAndFees(order, builder);
        addHomestay(order, builder);
        return builder.isReviewed(false).build();
    }

    private void addBaseFieldsAndFees(Order order, OrderDTO.OrderDTOBuilder builder) {
        BigDecimal baseAmount = order.getPrice() != null
                ? order.getPrice().multiply(BigDecimal.valueOf(order.getNights())) : BigDecimal.ZERO;
        BigDecimal cleaningFeeRate = getPricingConfig("pricing.cleaning_fee", "0.1");
        BigDecimal serviceFeeRate = getPricingConfig("pricing.service_fee", "0.15");
        BigDecimal cleaningFee = order.getPrice() != null
                ? order.getPrice().multiply(cleaningFeeRate) : BigDecimal.ZERO;

        builder.id(order.getId())
                .orderNumber(order.getOrderNumber())
                .guestPhone(order.getGuestPhone())
                .checkInDate(order.getCheckInDate())
                .checkOutDate(order.getCheckOutDate())
                .nights(order.getNights())
                .guestCount(order.getGuestCount())
                .price(order.getPrice())
                .cleaningFee(cleaningFee)
                .serviceFee(baseAmount.multiply(serviceFeeRate))
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null)
                .paymentMethod(order.getPaymentMethod())
                .remark(order.getRemark())
                .createTime(order.getCreatedAt())
                .updateTime(order.getUpdatedAt());
    }

    private void addHost(Order order, OrderDTO.OrderDTOBuilder builder) {
        User host = order.getHomestay() != null ? order.getHomestay().getOwner() : null;
        if (host != null) {
            builder.hostName(displayName(host)).hostId(host.getId());
        }
    }

    private void addGuest(Order order, OrderDTO.OrderDTOBuilder builder) {
        User guest = order.getGuest();
        if (guest != null) {
            builder.guestName(displayName(guest)).guestId(guest.getId());
        }
    }

    private void addHomestay(Order order, OrderDTO.OrderDTOBuilder builder) {
        Homestay homestay = order.getHomestay();
        if (homestay != null) {
            // 分步设置，保留管理列表在标题读取失败前已取得的房源 ID。
            builder.homestayId(homestay.getId());
            builder.homestayTitle(homestay.getTitle());
        }
    }

    private void addReview(Order order, OrderDTO.OrderDTOBuilder builder) {
        boolean isReviewed = reviewRepository.existsByOrder(order);
        builder.isReviewed(isReviewed);
        if (OrderStatus.COMPLETED.name().equals(order.getStatus()) && isReviewed) {
            reviewRepository.findByOrder(order).ifPresent(review -> builder.review(toReviewDTO(review)));
        }
    }

    private void addRefundDetails(Order order, OrderDTO.OrderDTOBuilder builder) {
        String initiatorName = findDisplayName(order.getRefundInitiatedBy());
        String processorName = findDisplayName(order.getRefundProcessedBy());
        builder.completedAt(order.getCompletedAt())
                .refundType(order.getRefundType() != null ? order.getRefundType().name() : null)
                .refundReason(order.getRefundReason())
                .refundAmount(order.getRefundAmount())
                .refundInitiatedBy(order.getRefundInitiatedBy())
                .refundInitiatedByName(initiatorName)
                .refundInitiatedAt(order.getRefundInitiatedAt())
                .refundProcessedBy(order.getRefundProcessedBy())
                .refundProcessedByName(processorName)
                .refundProcessedAt(order.getRefundProcessedAt())
                .refundTransactionId(order.getRefundTransactionId())
                .refundRejectionReason(order.getRefundRejectionReason());
    }

    private String findDisplayName(Long userId) {
        return userId != null ? userRepository.findById(userId).map(this::displayName).orElse(null) : null;
    }

    private String displayName(User user) {
        return user.getNickname() != null ? user.getNickname() : user.getUsername();
    }

    private BigDecimal getPricingConfig(String key, String defaultValue) {
        String value = systemConfigServiceProvider.getObject().getConfigValue(key, defaultValue);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("定价配置 {} 格式错误，使用默认值 {}", key, defaultValue);
            return new BigDecimal(defaultValue);
        }
    }

    private ReviewDTO toReviewDTO(Review review) {
        return ReviewDTO.builder()
                .id(review.getId())
                .userId(review.getUser() != null ? review.getUser().getId() : null)
                .userName(review.getUser() != null ? review.getUser().getUsername() : null)
                .userAvatar(review.getUser() != null ? review.getUser().getAvatar() : null)
                .homestayId(review.getHomestay() != null ? review.getHomestay().getId() : null)
                .homestayTitle(review.getHomestay() != null ? review.getHomestay().getTitle() : null)
                .orderId(review.getOrder() != null ? review.getOrder().getId() : null)
                .rating(review.getRating())
                .content(review.getContent())
                .cleanlinessRating(review.getCleanlinessRating())
                .accuracyRating(review.getAccuracyRating())
                .communicationRating(review.getCommunicationRating())
                .locationRating(review.getLocationRating())
                .checkInRating(review.getCheckInRating())
                .valueRating(review.getValueRating())
                .response(review.getResponse())
                .responseTime(review.getResponseTime())
                .createTime(review.getCreateTime())
                .isPublic(review.getIsPublic())
                .build();
    }
}

package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.dto.PricingResult;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/** 统一计算取消政策对应的退款金额与说明，不修改订单或执行退款。 */
@Service
public class RefundPolicyCalculator {

    private static final Logger log = LoggerFactory.getLogger(RefundPolicyCalculator.class);

    private final PricingService pricingService;
    private final Clock clock;

    @Autowired
    public RefundPolicyCalculator(PricingService pricingService) {
        this(pricingService, Clock.systemDefaultZone());
    }

    public RefundPolicyCalculator(PricingService pricingService, Clock clock) {
        this.pricingService = pricingService;
        this.clock = clock;
    }

    public Quote calculate(Order order) {
        if (order.getCheckInDate() == null || order.getTotalAmount() == null) {
            return new Quote(BigDecimal.ZERO, "无法计算退款金额（缺少入住日期或订单金额）", 2, null);
        }

        BigDecimal baseAmount = resolveBaseAmount(order);
        int policyType = resolvePolicyType(order);
        String policyName = switch (policyType) {
            case 1 -> "宽松政策";
            case 3 -> "严格政策";
            default -> "普通政策";
        };
        long hours = Duration.between(LocalDateTime.now(clock), order.getCheckInDate().atTime(14, 0)).toHours();
        int fullRefundHours = switch (policyType) {
            case 1 -> 24;
            case 3 -> 72;
            default -> 48;
        };

        if (hours >= fullRefundHours) {
            return new Quote(baseAmount, policyName + "：距离入住至少" + fullRefundHours
                    + "小时，可获得全额退款 ¥" + baseAmount, policyType, hours);
        }
        if (policyType == 3 || hours >= 24) {
            BigDecimal amount = baseAmount.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP);
            String timing = policyType == 3
                    ? (hours < 0 ? "已过入住时间" : "距离入住不足72小时")
                    : "距离入住至少24小时且不足48小时";
            return new Quote(amount, policyName + "：" + timing + "，退款50%，预计退款 ¥" + amount,
                    policyType, hours);
        }

        int nights = order.getNights() != null ? order.getNights() : 1;
        String timing = hours < 0 ? "已过入住时间" : "距离入住不足24小时";
        if (nights <= 1) {
            return new Quote(BigDecimal.ZERO, policyName + "：" + timing + "（仅1晚），不予退款", policyType, hours);
        }
        BigDecimal perNight = baseAmount.divide(BigDecimal.valueOf(nights), 2, RoundingMode.HALF_UP);
        BigDecimal amount = baseAmount.subtract(perNight).max(BigDecimal.ZERO);
        return new Quote(amount, policyName + "：" + timing + "，扣除首晚房费，预计退款 ¥" + amount,
                policyType, hours);
    }

    private BigDecimal resolveBaseAmount(Order order) {
        if (order.getId() != null) {
            try {
                PricingResult snapshot = pricingService.getPriceSnapshot(order.getId());
                if (snapshot != null && snapshot.getPayableAmount() != null) {
                    return snapshot.getPayableAmount();
                }
            } catch (Exception e) {
                log.warn("读取订单 {} 的价格快照失败，回退到订单总金额: {}", order.getId(), e.getMessage());
            }
        }
        return order.getTotalAmount();
    }

    private int resolvePolicyType(Order order) {
        try {
            Homestay homestay = order.getHomestay();
            Integer policyType = homestay != null ? homestay.getCancelPolicyType() : null;
            return policyType != null && (policyType == 1 || policyType == 3) ? policyType : 2;
        } catch (EntityNotFoundException e) {
            log.warn("订单 {} 关联的房源已被删除，使用普通取消政策", order.getId());
            return 2;
        }
    }

    public record Quote(BigDecimal amount, String description, int policyType, Long hoursBeforeCheckIn) {
    }
}

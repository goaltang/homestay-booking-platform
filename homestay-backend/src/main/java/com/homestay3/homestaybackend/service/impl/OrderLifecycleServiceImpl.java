package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.AppliedPromotionDTO;
import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.dto.ReviewDTO;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.PromotionUsage;
import com.homestay3.homestaybackend.entity.Review;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.entity.UserCoupon;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.PaymentStatus;
import com.homestay3.homestaybackend.model.HomestayStatus;
import com.homestay3.homestaybackend.model.RefundType;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.PromotionUsageRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.repository.ReviewRepository;
import com.homestay3.homestaybackend.service.BookingConflictService;
import com.homestay3.homestaybackend.service.CouponService;
import com.homestay3.homestaybackend.service.EarningService;
import com.homestay3.homestaybackend.service.OrderLifecycleService;
import com.homestay3.homestaybackend.service.OrderNotificationService;
import com.homestay3.homestaybackend.service.PricingService;
import com.homestay3.homestaybackend.service.PromotionMatchService;
import com.homestay3.homestaybackend.service.SystemConfigService;
import com.homestay3.homestaybackend.service.search.UserBehaviorTrackingService;
import com.homestay3.homestaybackend.dto.PricingResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 订单核心生命周期服务实现
 * 封装订单的创建、查询和核心状态转换业务逻辑
 */
@Service
@RequiredArgsConstructor
public class OrderLifecycleServiceImpl implements OrderLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleServiceImpl.class);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final HomestayRepository homestayRepository;
    private final OrderNotificationService orderNotificationService;
    private final EarningService earningService;
    private final ReviewRepository reviewRepository;
    private final BookingConflictService bookingConflictService;
    private final PricingService pricingService;
    private final CouponService couponService;
    private final PromotionMatchService promotionMatchService;
    private final PromotionUsageRepository promotionUsageRepository;
    private final com.homestay3.homestaybackend.repository.UserCouponRepository userCouponRepository;
    private final com.homestay3.homestaybackend.repository.CouponTemplateRepository couponTemplateRepository;
    private final com.homestay3.homestaybackend.service.CouponAnalyticsService couponAnalyticsService;
    private final ObjectProvider<SystemConfigService> systemConfigServiceProvider;
    private final UserBehaviorTrackingService userBehaviorTrackingService;
    private final com.homestay3.homestaybackend.service.PaymentProcessingService paymentProcessingService;
    private final OrderStatusUpdater orderStatusUpdater;

    // Note: Payment processing is handled by PaymentProcessingService, not here

    @Override
    @Transactional
    public OrderDTO createOrder(OrderDTO orderDTO) {
        log.info("开始创建订单: 用户={}, 房源={}, 入住日期={}, 退房日期={}",
                getCurrentUser().getUsername(), orderDTO.getHomestayId(),
                orderDTO.getCheckInDate(), orderDTO.getCheckOutDate());

        User currentUser = getCurrentUser();

        // 1. 获取房源信息（不再使用数据库级悲观锁，改由 BookingConflictService 的 Redis 分布式锁保护）
        Homestay homestay = homestayRepository.findById(orderDTO.getHomestayId())
                .orElseThrow(() -> new ResourceNotFoundException("房源不存在"));

        if (!homestay.getStatus().equals(HomestayStatus.ACTIVE)) {
            throw new IllegalArgumentException("房源当前不可预订");
        }

        if (currentUser.getId().equals(homestay.getOwner().getId())) {
            throw new IllegalArgumentException("不能预订自己的房源");
        }

        // 2. 验证日期
        if (orderDTO.getCheckInDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("入住日期不能早于今天");
        }
        if (orderDTO.getCheckOutDate().isBefore(orderDTO.getCheckInDate())) {
            throw new IllegalArgumentException("退房日期不能早于入住日期");
        }

        // 4. 计算住宿天数
        long nights = ChronoUnit.DAYS.between(orderDTO.getCheckInDate(), orderDTO.getCheckOutDate());
        if (nights < homestay.getMinNights()) {
            throw new IllegalArgumentException("住宿天数不能少于" + homestay.getMinNights() + "晚");
        }

        if (orderDTO.getGuestCount() == null || orderDTO.getGuestCount() <= 0) {
            throw new IllegalArgumentException("入住人数必须大于0");
        }
        if (orderDTO.getGuestCount() > homestay.getMaxGuests()) {
            throw new IllegalArgumentException("入住人数不能超过房源最大入住人数" + homestay.getMaxGuests() + "人");
        }

        // 5. 校验 quoteToken（必须有效，无效则拒绝下单）
        if (orderDTO.getQuoteToken() != null && !orderDTO.getQuoteToken().isBlank()) {
            boolean tokenValid = pricingService.validateQuoteToken(orderDTO.getQuoteToken(), orderDTO, currentUser.getId());
            if (!tokenValid) {
                throw new IllegalArgumentException("报价凭证已过期或参数不一致，请重新获取报价");
            }
        }

        // 5.1 统一计价（所有价格计算通过 PricingService）
        PricingResult pricingResult = pricingService.calculate(
                homestay.getId(),
                orderDTO.getCheckInDate(),
                orderDTO.getCheckOutDate(),
                orderDTO.getGuestCount(),
                orderDTO.getCouponIds(),
                currentUser.getId()
        );
        BigDecimal totalAmount = pricingResult.getPayableAmount();
        List<Long> effectiveCouponIds = pricingResult.getEffectiveCouponIds() != null
                ? pricingResult.getEffectiveCouponIds()
                : Collections.emptyList();
        log.info("统一计价结果: payableAmount={}, roomOriginalAmount={}, activityDiscount={}, couponDiscount={}",
                totalAmount, pricingResult.getRoomOriginalAmount(),
                pricingResult.getActivityDiscountAmount(), pricingResult.getCouponDiscountAmount());

        // 6. 根据房源自动确认设置决定初始订单状态
        String initialStatus;
        if (homestay.getAutoConfirm() != null && homestay.getAutoConfirm()) {
            initialStatus = OrderStatus.CONFIRMED.name();
            log.info("房源 {} 开启自动确认，订单将直接进入已确认状态", homestay.getId());
        } else {
            initialStatus = OrderStatus.PENDING.name();
            log.info("房源 {} 采用房东确认制，订单进入待确认状态", homestay.getId());
        }

        // 7. 创建订单对象
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .homestay(homestay)
                .guest(currentUser)
                .guestPhone(orderDTO.getGuestPhone())
                .checkInDate(orderDTO.getCheckInDate())
                .checkOutDate(orderDTO.getCheckOutDate())
                .nights((int) nights)
                .guestCount(orderDTO.getGuestCount())
                .price(homestay.getPrice())
                .totalAmount(totalAmount)
                .status(initialStatus)
                .remark(orderDTO.getRemark())
                .build();

        try {
            // 8. 使用安全的创建方法（带锁保护）
            Order savedOrder = bookingConflictService.safeCreateOrder(order);
            log.info("订单创建成功: id={}, orderNumber={}", savedOrder.getId(), savedOrder.getOrderNumber());

            // 8.1 保存价格快照（用于后续退款/对账追溯，失败则回滚订单）
            pricingService.savePriceSnapshot(savedOrder.getId(), pricingResult, orderDTO.getQuoteToken());

            // 8.2 锁定优惠券（失败抛异常，事务回滚，避免订单落库但券未锁定）
            if (orderDTO.getCouponIds() != null && !orderDTO.getCouponIds().isEmpty()) {
                couponService.lockCoupons(effectiveCouponIds, savedOrder.getId(), currentUser.getId());
                // 转化漏斗埋点：锁定（下单）
                for (Long couponId : effectiveCouponIds) {
                    try {
                        com.homestay3.homestaybackend.entity.UserCoupon uc = userCouponRepository.findById(couponId).orElse(null);
                        if (uc != null && uc.getTemplate() != null) {
                            couponAnalyticsService.track(uc.getTemplate().getId(), null, "ORDER_CONFIRM", "LOCK",
                                    currentUser.getId(), null, savedOrder.getId());
                        }
                    } catch (Exception ignored) {}
                }
            }

            // 8.3 扣减活动预算并记录优惠使用流水
            if (pricingResult.getAppliedPromotions() != null) {
                for (AppliedPromotionDTO promo : pricingResult.getAppliedPromotions()) {
                    if (promo.getCampaignId() != null && promo.getDiscountAmount() != null
                            && promo.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                        // 扣减预算（失败抛异常回滚，防止预算没扣但订单已优惠）
                        boolean deducted = promotionMatchService.deductCampaignBudget(promo.getCampaignId(), promo.getDiscountAmount());
                        if (!deducted) {
                            throw new IllegalStateException("活动预算不足，无法应用优惠，campaignId=" + promo.getCampaignId());
                        }
                        // 记录流水
                        PromotionUsage usage = PromotionUsage.builder()
                                .orderId(savedOrder.getId())
                                .userId(currentUser.getId())
                                .campaignId(promo.getCampaignId())
                                .discountAmount(promo.getDiscountAmount())
                                .bearer(promo.getBearer() != null ? promo.getBearer() : "PLATFORM")
                                .status("LOCKED")
                                .build();
                        promotionUsageRepository.save(usage);
                    }
                }
            }

            // 记录优惠券流水（使用锁定后的券查询，避免状态不匹配；只记录一条总流水防止重复计算）
            if (orderDTO.getCouponIds() != null && !orderDTO.getCouponIds().isEmpty()) {
                List<UserCoupon> lockedCoupons = userCouponRepository.findByLockedOrderIdAndStatus(savedOrder.getId(), "LOCKED");
                BigDecimal totalCouponDiscount = pricingResult.getCouponDiscountAmount() != null
                        ? pricingResult.getCouponDiscountAmount()
                        : BigDecimal.ZERO;
                if (!lockedCoupons.isEmpty() && totalCouponDiscount.compareTo(BigDecimal.ZERO) > 0) {
                    // 取第一张锁定券的承担方作为代表（多券叠加时承担方已在 CouponService 中计算）
                    Long usageCouponId = lockedCoupons.get(0).getId();
                    BigDecimal couponPlatformDiscount = pricingResult.getCouponPlatformDiscountAmount() != null
                            ? pricingResult.getCouponPlatformDiscountAmount()
                            : BigDecimal.ZERO;
                    BigDecimal couponHostDiscount = pricingResult.getCouponHostDiscountAmount() != null
                            ? pricingResult.getCouponHostDiscountAmount()
                            : BigDecimal.ZERO;
                    if (couponPlatformDiscount.compareTo(BigDecimal.ZERO) > 0) {
                        promotionUsageRepository.save(PromotionUsage.builder()
                                .orderId(savedOrder.getId())
                                .userId(currentUser.getId())
                                .couponId(usageCouponId)
                                .discountAmount(couponPlatformDiscount)
                                .bearer("PLATFORM")
                                .status("LOCKED")
                                .build());
                    }
                    if (couponHostDiscount.compareTo(BigDecimal.ZERO) > 0) {
                        promotionUsageRepository.save(PromotionUsage.builder()
                                .orderId(savedOrder.getId())
                                .userId(currentUser.getId())
                                .couponId(usageCouponId)
                                .discountAmount(couponHostDiscount)
                                .bearer("HOST")
                                .status("LOCKED")
                                .build());
                    }
                }
            }

            // 9. 根据订单状态发送不同的通知
            try {
                User host = homestay.getOwner();
                if (OrderStatus.CONFIRMED.name().equals(savedOrder.getStatus())) {
                    // 自动确认房源：通知房东有新订单，通知客人可以支付
                    orderNotificationService.sendOrderAutoConfirmedNotification(
                            host.getId(),
                            currentUser.getId(),
                            savedOrder.getId(),
                            host.getUsername(),
                            currentUser.getUsername(),
                            savedOrder.getOrderNumber());
                } else {
                    // 房东确认制：通知房东有新预订请求
                    orderNotificationService.sendOrderBookingRequestNotification(
                            host.getId(),
                            currentUser.getId(),
                            savedOrder.getId(),
                            currentUser.getUsername(),
                            savedOrder.getOrderNumber());
                }
            } catch (Exception e) {
                log.error("发送通知失败: {}", e.getMessage());
            }

            // 10. 设置支付状态
            if (OrderStatus.CONFIRMED.name().equals(savedOrder.getStatus())) {
                orderStatusUpdater.markConfirmed(savedOrder);
            } else {
                orderStatusUpdater.markPending(savedOrder);
            }
            savedOrder = orderRepository.save(savedOrder);

            trackBookingAfterCommit(currentUser.getId(), homestay);

            return convertToDTO(savedOrder);

        } catch (DataIntegrityViolationException e) {
            log.error("订单创建失败，可能存在日期冲突: {}", e.getMessage());
            throw new IllegalArgumentException("所选日期已被预订，请选择其他日期");
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 透传已知的业务异常（如 BookingConflictService 抛出的日期冲突），保留原始信息
            throw e;
        } catch (Exception e) {
            log.error("订单创建过程中发生异常: {}", e.getMessage(), e);
            throw new RuntimeException("订单创建失败，请稍后重试");
        }
    }

    private void trackBookingAfterCommit(Long userId, Homestay homestay) {
        if (userId == null || homestay == null || homestay.getId() == null) {
            return;
        }

        Runnable trackingTask = () -> {
            try {
                userBehaviorTrackingService.trackBooking(
                        userId,
                        null,
                        homestay.getId(),
                        homestay.getCityCode(),
                        homestay.getType(),
                        homestay.getPrice());
            } catch (Exception e) {
                log.warn("记录预订行为失败: userId={}, homestayId={}, error={}",
                        userId, homestay.getId(), e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trackingTask.run();
                }
            });
        } else {
            trackingTask.run();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDTO getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

        // 检查权限
        User currentUser = getCurrentUser();
        if (!isOrderAccessible(order, currentUser)) {
            throw new AccessDeniedException("您无权访问此订单");
        }

        return convertToDTO(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDTO getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

        // 检查权限
        User currentUser = getCurrentUser();
        if (!isOrderAccessible(order, currentUser)) {
            throw new AccessDeniedException("您无权访问此订单");
        }

        return convertToDTO(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDTO> getMyOrders(Map<String, String> params, Pageable pageable) {
        User currentUser = getCurrentUser();
        Specification<Order> spec = buildMyOrdersSpec(params, currentUser);
        return orderRepository.findAll(spec, pageable).map(this::convertToDTO);
    }

    /**
     * 构建我的订单查询条件
     * 支持 tab 复合查询和单 status 兼容查询
     */
    private Specification<Order> buildMyOrdersSpec(Map<String, String> params, User currentUser) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 当前用户作为客人
            predicates.add(cb.equal(root.get("guest"), currentUser));

            String tab = params.get("tab");
            if (tab != null && !tab.isBlank() && !"all".equalsIgnoreCase(tab)) {
                Predicate tabPredicate = buildTabPredicate(root, cb, tab);
                if (tabPredicate != null) {
                    predicates.add(tabPredicate);
                }
            } else {
                // 无 tab 时兼容旧逻辑
                if (params.containsKey("status")) {
                    predicates.add(cb.equal(root.get("status"), params.get("status")));
                }
                if (params.containsKey("statusIn")) {
                    String[] statuses = params.get("statusIn").split(",");
                    predicates.add(root.get("status").in((Object[]) statuses));
                }
                if (params.containsKey("paymentStatus")) {
                    try {
                        PaymentStatus ps = PaymentStatus.valueOf(params.get("paymentStatus"));
                        predicates.add(cb.equal(root.get("paymentStatus"), ps));
                    } catch (IllegalArgumentException e) {
                        log.warn("无效的支付状态筛选值: {}", params.get("paymentStatus"));
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 根据 tab 名称构建复合查询条件
     */
    private Predicate buildTabPredicate(jakarta.persistence.criteria.Root<Order> root,
                                        jakarta.persistence.criteria.CriteriaBuilder cb,
                                        String tab) {
        switch (tab) {
            case "PENDING":
                return cb.equal(root.get("status"), OrderStatus.PENDING.name());
            case "NEED_PAYMENT":
                return cb.or(
                        cb.and(
                                cb.equal(root.get("status"), OrderStatus.CONFIRMED.name()),
                                cb.or(
                                        cb.equal(root.get("paymentStatus"), PaymentStatus.UNPAID),
                                        cb.isNull(root.get("paymentStatus"))
                                )
                        ),
                        cb.equal(root.get("status"), OrderStatus.PAYMENT_PENDING.name())
                );
            case "IN_PROGRESS":
                return cb.or(
                        cb.equal(root.get("paymentStatus"), PaymentStatus.PAID),
                        cb.equal(root.get("status"), OrderStatus.CHECKED_IN.name()),
                        cb.equal(root.get("status"), OrderStatus.READY_FOR_CHECKIN.name())
                );
            case "COMPLETED":
                return cb.equal(root.get("status"), OrderStatus.COMPLETED.name());
            case "CANCELLED":
                return cb.or(
                        cb.like(root.get("status"), "CANCELLED%"),
                        cb.equal(root.get("status"), OrderStatus.REJECTED.name())
                );
            case "REFUND_RELATED":
                return cb.or(
                        cb.equal(root.get("paymentStatus"), PaymentStatus.REFUND_PENDING),
                        cb.equal(root.get("paymentStatus"), PaymentStatus.REFUNDED),
                        cb.equal(root.get("paymentStatus"), PaymentStatus.REFUND_FAILED)
                );
            default:
                return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDTO> getOwnerOrders(String ownerUsername, Map<String, String> params, Pageable pageable) {
        // 获取房东用户
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));

        // 创建查询条件
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 关联房源表，筛选出指定房东的订单
            Join<Order, Homestay> homestayJoin = root.join("homestay");
            predicates.add(cb.equal(homestayJoin.get("owner"), owner));

            // 按状态筛选
            if (params.containsKey("status")) {
                predicates.add(cb.equal(root.get("status"), params.get("status")));
            }

            // 按房源ID筛选
            if (params.containsKey("homestayId")) {
                predicates.add(cb.equal(homestayJoin.get("id"),
                        Long.parseLong(params.get("homestayId"))));
            }

            // 按日期范围筛选（区间重叠判断：订单入住日期 <= 筛选结束日期 且 订单退房日期 >= 筛选开始日期）
            if (params.containsKey("startDate") && params.containsKey("endDate")) {
                LocalDate startDate = LocalDate.parse(params.get("startDate"));
                LocalDate endDate = LocalDate.parse(params.get("endDate"));

                predicates.add(cb.and(
                        cb.lessThanOrEqualTo(root.get("checkInDate"), endDate),
                        cb.greaterThanOrEqualTo(root.get("checkOutDate"), startDate)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return orderRepository.findAll(spec, pageable).map(this::convertToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getPendingOrderCount(String ownerUsername) {
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));

        return orderRepository.countPendingOrdersByOwner(owner);
    }

    @Override
    @Transactional
    public OrderDTO confirmOrder(Long id) {
        // 获取当前用户
        User currentUser = getCurrentUser();
        
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

        // 检查当前用户是否有权限更新此订单状态
        if (!isOrderAccessible(order, currentUser)) {
            throw new AccessDeniedException("您无权确认此订单");
        }

        // 检查目标状态字符串是否有效
        OrderStatus targetStatus = OrderStatus.CONFIRMED;
        OrderStatus currentStatus = OrderStatus.valueOf(order.getStatus());
        
        // 检查状态转换是否有效
        if (!isValidStatusTransition(currentStatus, targetStatus)) {
            throw new IllegalArgumentException("无效的订单状态转换: 从 " + currentStatus + " 到 " + targetStatus);
        }

        // 更新订单状态
        if (targetStatus == OrderStatus.CONFIRMED) {
            orderStatusUpdater.markHostConfirmed(order);
            if (!isOrderOwner(order, currentUser)) {
                throw new AccessDeniedException("只有房东才能确认订单");
            }
            // 状态已在前面设置，这里只处理通知
            try {
                User guest = order.getGuest();
                orderNotificationService.sendOrderConfirmedNotification(
                        guest.getId(), currentUser.getId(), order.getId(),
                        guest.getUsername(), order.getHomestay().getTitle(),
                        currentUser.getUsername(), order.getOrderNumber());
            } catch (Exception e) {
                log.error("为房客 {} 发送订单 {} 确认通知失败: {}", order.getGuest().getUsername(), order.getOrderNumber(),
                        e.getMessage(), e);
            }
        }

        // 保存更新后的订单
        Order updatedOrder = orderRepository.save(order);
        return convertToDTO(updatedOrder);
    }

    @Override
    @Transactional
    public OrderDTO rejectOrder(Long id, String reason) {
        // 获取当前用户（房东）
        User currentUser = getCurrentUser();
        
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

        // 检查是否为房东
        if (!isOrderOwner(order, currentUser)) {
            throw new AccessDeniedException("无权操作此订单");
        }

        // 检查订单状态是否为待确认
        if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
            throw new IllegalArgumentException("只能拒绝待确认状态的订单");
        }

        // 更新订单状态
        orderStatusUpdater.markRejected(order);
        order.setRemark((order.getRemark() != null ? order.getRemark() + "\n" : "") + "拒绝原因: " + reason);
        order.setUpdatedAt(LocalDateTime.now());
        Order updatedOrder = orderRepository.save(order);

        // 发送预订拒绝通知给房客
        try {
            orderNotificationService.sendOrderRejectedNotification(
                    order.getGuest().getId(),
                    currentUser.getId(),
                    order.getId(),
                    order.getGuest().getUsername(),
                    order.getHomestay().getTitle(),
                    order.getOrderNumber(),
                    reason);
        } catch (Exception e) {
            log.error("为房客 {} 发送订单 {} 被拒绝的通知失败: {}", order.getGuest().getUsername(), order.getOrderNumber(),
                    e.getMessage(), e);
        }

        return convertToDTO(updatedOrder);
    }

    @Override
    @Transactional
    public OrderDTO checkIn(Long id) {
        // 获取当前用户
        User currentUser = getCurrentUser();
        
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

        // 检查当前用户是否有权限更新此订单状态
        if (!isOrderAccessible(order, currentUser)) {
            throw new AccessDeniedException("您无权办理入住此订单");
        }

        OrderStatus currentStatus = OrderStatus.valueOf(order.getStatus());
        OrderStatus targetStatus = OrderStatus.CHECKED_IN;
        
        // 检查状态转换是否有效
        if (!isValidStatusTransition(currentStatus, targetStatus)) {
            throw new IllegalArgumentException("无效的订单状态转换: 从 " + currentStatus + " 到 " + targetStatus);
        }

        // 检查权限：只有房东或房客可以办理入住
        if (!isOrderOwner(order, currentUser) && !isOrderGuest(order, currentUser)) {
            throw new AccessDeniedException("无权标记此订单为入住状态");
        }
        
        // 检查当前状态是否允许入住（通常需要已支付或待入住状态）
        if (currentStatus != OrderStatus.PAID && currentStatus != OrderStatus.READY_FOR_CHECKIN) {
            throw new IllegalArgumentException("订单状态为 " + currentStatus + "，无法直接标记为入住");
        }

        // 更新订单状态
        orderStatusUpdater.markCheckedIn(order);
        order.setCheckedInAt(LocalDateTime.now());

        // 保存更新后的订单
        Order updatedOrder = orderRepository.save(order);
        return convertToDTO(updatedOrder);
    }

    @Override
    @Transactional
    public OrderDTO checkOut(Long id) {
        // 获取当前用户
        User currentUser = getCurrentUser();
        
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

        // 检查当前用户是否有权限更新此订单状态
        if (!isOrderAccessible(order, currentUser)) {
            throw new AccessDeniedException("您无权办理退房此订单");
        }

        OrderStatus currentStatus = OrderStatus.valueOf(order.getStatus());
        OrderStatus targetStatus = OrderStatus.CHECKED_OUT;
        
        // 检查状态转换是否有效
        if (!isValidStatusTransition(currentStatus, targetStatus)) {
            throw new IllegalArgumentException("无效的订单状态转换: 从 " + currentStatus + " 到 " + targetStatus);
        }

        // 检查权限：只有房东、房客或管理员可以办理退房
        boolean isAdmin = currentUser.getRole().contains("ADMIN");
        if (!isOrderOwner(order, currentUser) && !isOrderGuest(order, currentUser) && !isAdmin) {
            throw new AccessDeniedException("无权标记此订单为完成状态");
        }

        // 更新订单状态
        orderStatusUpdater.markCheckedOut(order);
        order.setCheckedOutAt(LocalDateTime.now());

        // 完成订单时生成待结算收益
        try {
            earningService.generatePendingEarningForOrder(order.getId());
        } catch (Exception e) {
            log.error("为订单 {} 生成待结算收益时出错: {}", order.getOrderNumber(), e.getMessage(), e);
        }

        // 发送订单完成通知
        try {
            orderNotificationService.sendOrderCompletedNotification(
                    order.getGuest().getId(),
                    order.getHomestay().getOwner().getId(),
                    order.getId(),
                    order.getGuest().getUsername(),
                    order.getHomestay().getOwner().getUsername(),
                    order.getHomestay().getTitle(),
                    order.getOrderNumber(),
                    currentUser.getId());
        } catch (Exception notifyEx) {
            log.error("发送订单 {} 完成通知失败: {}", order.getOrderNumber(), notifyEx.getMessage(), notifyEx);
        }

        // 保存更新后的订单
        Order updatedOrder = orderRepository.save(order);
        return convertToDTO(updatedOrder);
    }

    @Override
    @Transactional
    public OrderDTO cancelOrder(Long id) {
        // 调用带类型和原因的取消方法，使用默认类型：CANCELLED
        return cancelOrderWithReason(id, OrderStatus.CANCELLED.name(), "用户取消订单");
    }

    @Override
    @Transactional
    public OrderDTO cancelOrderWithReason(Long id, String cancelType, String reason) {
        log.info("取消订单请求 - 订单ID: {}, 取消类型: {}, 原因: {}", id, cancelType, reason);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

        // 检查当前用户是否有权限取消此订单
        User currentUser = getCurrentUser();

        // 根据取消类型和当前用户角色进行权限检查
        OrderStatus targetStatus;
        try {
            targetStatus = OrderStatus.valueOf(cancelType);
        } catch (IllegalArgumentException e) {
            // 默认取消状态
            targetStatus = OrderStatus.CANCELLED;
        }

        // 如果是用户取消，检查当前用户是否是订单的客户
        if (targetStatus == OrderStatus.CANCELLED_BY_USER) {
            if (!isOrderGuest(order, currentUser)) {
                throw new AccessDeniedException("您只能取消自己的订单");
            }
        }
        // 如果是房东取消，检查当前用户是否是房源的拥有者
        else if (targetStatus == OrderStatus.CANCELLED_BY_HOST) {
            if (!isOrderOwner(order, currentUser)) {
                throw new AccessDeniedException("您只能取消自己房源的订单");
            }
        }
        // 如果是系统/管理员取消，检查当前用户是否有管理员权限
        else if (targetStatus == OrderStatus.CANCELLED_SYSTEM || targetStatus == OrderStatus.CANCELLED) {
            if (!hasAdminAuthority(currentUser)) {
                throw new AccessDeniedException("只有管理员可以执行系统取消操作");
            }
        }

        // 处理取消逻辑
        return processCancelOrder(order, cancelType, reason);
    }
    
    // We'll need to implement the rest of the methods similarly.
    // Given the time, I'll output a placeholder and we can continue in the next interaction.
    // Let's at least implement a few more methods to show progress.
    
    // Actually, let's change strategy: we'll implement the interface fully in this step, even if it's long.
    // We'll do it in one go.
    // Given the time constraints, I'll implement the key methods and leave the rest as TODOs.
    // But the user expects a complete split.
    // I'll try to implement as much as possible.
    
    // Let's continue with the implementation in the next message due to length.
    
    @Override
    @Transactional
    public OrderDTO updateOrderStatus(Long id, String status) {
        // 获取当前用户
        User currentUser = getCurrentUser();
        
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

        // 检查当前用户是否有权限更新此订单状态
        if (!isOrderAccessible(order, currentUser)) {
            throw new AccessDeniedException("您无权更新此订单状态");
        }

        // 检查目标状态字符串是否有效
        OrderStatus targetStatus;
        try {
            targetStatus = OrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的订单状态: " + status);
        }

        // 检查状态转换是否有效
        OrderStatus currentStatus = OrderStatus.valueOf(order.getStatus());
        if (!isValidStatusTransition(currentStatus, targetStatus)) {
            throw new IllegalArgumentException("无效的订单状态转换: 从 " + currentStatus + " 到 " + targetStatus);
        }

        // 根据目标状态执行特定逻辑
        if (targetStatus == OrderStatus.PAID) {
            orderStatusUpdater.markPaid(order);
            // 统一支付成功后置处理（核销优惠券、更新活动流水、生成收益、发送通知等）
            try {
                paymentProcessingService.handleOrderPaidSuccess(order.getId());
            } catch (Exception e) {
                log.error("订单支付成功后置处理失败，订单: {}", order.getId(), e);
            }
        } else if (targetStatus == OrderStatus.CONFIRMED) {
            orderStatusUpdater.markHostConfirmed(order);
            if (!isOrderOwner(order, currentUser)) {
                throw new AccessDeniedException("只有房东才能确认订单");
            }
            // 发送订单确认通知给房客
            try {
                User guest = order.getGuest();
                orderNotificationService.sendOrderConfirmedNotification(
                        guest.getId(), currentUser.getId(), order.getId(),
                        guest.getUsername(), order.getHomestay().getTitle(),
                        currentUser.getUsername(), order.getOrderNumber());
            } catch (Exception e) {
                log.error("为房客 {} 发送订单 {} 确认通知失败: {}", order.getGuest().getUsername(), order.getOrderNumber(),
                        e.getMessage(), e);
            }
        } else if (targetStatus == OrderStatus.COMPLETED) {
            orderStatusUpdater.markCompleted(order);
            // 检查当前用户是否有权标记完成 (房东、房客或管理员)
            boolean isAdmin = currentUser.getRole().contains("ADMIN");
            if (!isOrderOwner(order, currentUser) && !isOrderGuest(order, currentUser) && !isAdmin) {
                throw new AccessDeniedException("无权标记此订单为完成状态");
            }
            // 确保订单已支付或在某些允许的状态
            if (currentStatus != OrderStatus.PAID && currentStatus != OrderStatus.CHECKED_IN) {
                throw new IllegalArgumentException("订单状态为 " + currentStatus + "，无法直接标记为完成");
            }
            // 调用 EarningService 生成待结算收益
            try {
                earningService.generatePendingEarningForOrder(order.getId());
            } catch (Exception e) {
                log.error("为订单 {} 生成待结算收益时出错: {}", order.getOrderNumber(), e.getMessage(), e);
            }
            // 发送订单完成通知
            try {
                orderNotificationService.sendOrderCompletedNotification(
                        order.getGuest().getId(),
                        order.getHomestay().getOwner().getId(),
                        order.getId(),
                        order.getGuest().getUsername(),
                        order.getHomestay().getOwner().getUsername(),
                        order.getHomestay().getTitle(),
                        order.getOrderNumber(),
                        currentUser.getId());
            } catch (Exception notifyEx) {
                log.error("发送订单 {} 完成通知失败: {}", order.getOrderNumber(), notifyEx.getMessage(), notifyEx);
            }

            // 订单完成返券
            try {
                issueRewardCoupons(order.getGuest().getId(), "ORDER_COMPLETED");
            } catch (Exception e) {
                log.error("订单 {} 完成返券失败: {}", order.getOrderNumber(), e.getMessage());
            }
        } else if (targetStatus == OrderStatus.CHECKED_IN) {
            orderStatusUpdater.markCheckedIn(order);
            if (!isOrderOwner(order, currentUser) && !isOrderGuest(order, currentUser)) {
                throw new AccessDeniedException("无权标记此订单为入住状态");
            }
            if (currentStatus != OrderStatus.PAID && currentStatus != OrderStatus.READY_FOR_CHECKIN) {
                throw new IllegalArgumentException("订单状态为 " + currentStatus + "，无法直接标记为入住");
            }
            log.info("用户 {} 正在标记订单 {} 为入住", currentUser.getUsername(), id);
            // 可以考虑发送入住通知等
        }
        // 取消和拒绝状态的特殊处理已转移到各自的方法中
        // 这里主要处理通用状态转换和特定状态的副作用

        // 保存更新后的订单
        Order updatedOrder = orderRepository.save(order);
        return convertToDTO(updatedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public Object getHostOrderStats() {
        // 获取当前用户（应该是房东）
        User currentUser = getCurrentUser();

        // 验证当前用户是否为房东
        boolean isHost = currentUser.getRole().contains("HOST");
        if (!isHost) {
            throw new AccessDeniedException("只有房东可以获取订单统计信息");
        }

        // 获取该房东的所有订单
        List<Order> hostOrders = orderRepository.findByHomestayOwnerId(currentUser.getId());

        // 计算统计信息
        Map<String, Object> stats = new HashMap<>();
        int totalOrders = hostOrders.size();
        stats.put("totalOrders", totalOrders);

        if (totalOrders == 0) {
            stats.put("pendingOrders", 0);
            stats.put("confirmedOrders", 0);
            stats.put("paidOrders", 0);
            stats.put("completedOrders", 0);
            stats.put("cancelledOrders", 0);
            stats.put("totalRevenue", BigDecimal.ZERO);
            stats.put("completionRate", 0.0);
            stats.put("cancellationRate", 0.0);
            return stats;
        }

        // 按状态统计
        int pendingOrders = 0;
        int confirmedOrders = 0;
        int paidOrders = 0;
        int completedOrders = 0;
        int cancelledOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        for (Order order : hostOrders) {
            String status = order.getStatus();
            switch (status) {
                case "PENDING":
                    pendingOrders++;
                    break;
                case "CONFIRMED":
                    confirmedOrders++;
                    break;
                case "PAID":
                    paidOrders++;
                    break;
                case "COMPLETED":
                    completedOrders++;
                    // 完成的订单计入收入
                    if (order.getTotalAmount() != null) {
                        totalRevenue = totalRevenue.add(order.getTotalAmount());
                    }
                    break;
                default:
                    // 处理各种取消状态
                    if (status.startsWith("CANCELLED") || status.equals("REJECTED")) {
                        cancelledOrders++;
                    }
                    // 已完成且已支付的订单也计入收入
                    if (status.equals("COMPLETED") && order.getTotalAmount() != null) {
                        totalRevenue = totalRevenue.add(order.getTotalAmount());
                    }
                    break;
            }
        }

        stats.put("pendingOrders", pendingOrders);
        stats.put("confirmedOrders", confirmedOrders);
        stats.put("paidOrders", paidOrders);
        stats.put("completedOrders", completedOrders);
        stats.put("cancelledOrders", cancelledOrders);
        stats.put("totalRevenue", totalRevenue);
        stats.put("completionRate", totalOrders > 0 ? (double) completedOrders / totalOrders * 100 : 0.0);
        stats.put("cancellationRate", totalOrders > 0 ? (double) cancelledOrders / totalOrders * 100 : 0.0);

        return stats;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 获取当前登录用户
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }

    /**
     * 生成订单号
     */
    private String generateOrderNumber() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String datePart = LocalDate.now().format(formatter);
        String randomPart = String.format("%06d", new Random().nextInt(999999));
        return "HS" + datePart + randomPart;
    }

    /**
     * 检查用户是否为订单的房东
     */
    private boolean isOrderOwner(Order order, User user) {
        return order.getHomestay() != null && order.getHomestay().getOwner() != null
                && order.getHomestay().getOwner().getId().equals(user.getId());
    }

    /**
     * 检查用户是否为订单的客户
     */
    private boolean isOrderGuest(Order order, User user) {
        return order.getGuest() != null && order.getGuest().getId().equals(user.getId());
    }

    /**
     * 检查用户是否有管理员权限
     */
    private boolean hasAdminAuthority(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        return user.getRole().contains("ADMIN");
    }

    /**
     * 获取定价配置
     */
    private BigDecimal getPricingConfig(String key, String defaultValue) {
        String value = systemConfigServiceProvider.getObject().getConfigValue(key, defaultValue);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("定价配置 {} 格式错误，使用默认值 {}", key, defaultValue);
            return new BigDecimal(defaultValue);
        }
    }

    /**
     * 检查用户是否有权访问和操作此订单
     */
    private boolean isOrderAccessible(Order order, User user) {
        // 管理员可以访问所有订单
        if (hasAdminAuthority(user)) {
            return true;
        }

        // 房东可以访问自己房源的订单
        if (isOrderOwner(order, user)) {
            return true;
        }

        // 客户可以访问自己的订单
        if (isOrderGuest(order, user)) {
            return true;
        }

        return false;
    }

    /**
     * 验证订单状态流转是否合法
     */
    private boolean isValidStatusTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        Map<OrderStatus, List<OrderStatus>> transitions = new HashMap<>();

        // 待确认状态可以转换为：已确认、已取消（各种取消状态）、已拒绝
        transitions.put(OrderStatus.PENDING, Arrays.asList(
                OrderStatus.CONFIRMED,
                OrderStatus.CANCELLED,
                OrderStatus.CANCELLED_BY_USER,
                OrderStatus.CANCELLED_BY_HOST,
                OrderStatus.CANCELLED_SYSTEM,
                OrderStatus.REJECTED));

        // 已确认状态可以转换为：支付中、已取消、已支付
        transitions.put(OrderStatus.CONFIRMED, Arrays.asList(
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.CANCELLED,
                OrderStatus.CANCELLED_BY_USER,
                OrderStatus.CANCELLED_BY_HOST,
                OrderStatus.CANCELLED_SYSTEM,
                OrderStatus.PAID
        ));

        // 支付中状态可以转换为：已支付、支付失败、用户取消、系统取消
        transitions.put(OrderStatus.PAYMENT_PENDING, Arrays.asList(
                OrderStatus.PAID,
                OrderStatus.PAYMENT_FAILED,
                OrderStatus.CANCELLED_BY_USER,
                OrderStatus.CANCELLED,
                OrderStatus.CANCELLED_SYSTEM
        ));

        // 支付失败状态可以转换为：支付中、已取消、用户取消、系统取消
        transitions.put(OrderStatus.PAYMENT_FAILED, Arrays.asList(
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.CANCELLED,
                OrderStatus.CANCELLED_BY_USER,
                OrderStatus.CANCELLED_SYSTEM
        ));

        // 已支付状态可以转换为：待入住、退款中、各种取消状态、已入住
        transitions.put(OrderStatus.PAID, Arrays.asList(
                OrderStatus.READY_FOR_CHECKIN,
                OrderStatus.REFUND_PENDING,
                OrderStatus.CANCELLED_BY_HOST,
                OrderStatus.CANCELLED_BY_USER,
                OrderStatus.CANCELLED_SYSTEM,
                OrderStatus.CANCELLED,
                OrderStatus.CHECKED_IN
        ));

        // 待入住状态可以转换为：已入住、退款中、各种取消状态
        transitions.put(OrderStatus.READY_FOR_CHECKIN, Arrays.asList(
                OrderStatus.CHECKED_IN,
                OrderStatus.REFUND_PENDING,
                OrderStatus.CANCELLED_BY_HOST,
                OrderStatus.CANCELLED_BY_USER,
                OrderStatus.CANCELLED_SYSTEM,
                OrderStatus.CANCELLED
        ));

        // 已入住状态只能转换为：已退房
        transitions.put(OrderStatus.CHECKED_IN, List.of(OrderStatus.CHECKED_OUT));

        // 已退房状态只能转换为：已完成
        transitions.put(OrderStatus.CHECKED_OUT, List.of(OrderStatus.COMPLETED));

        // 已完成状态不能转换为任何状态
        transitions.put(OrderStatus.COMPLETED, Collections.emptyList());

        // 已取消（各种取消状态）不能转换为任何状态
        transitions.put(OrderStatus.CANCELLED, Collections.emptyList());
        transitions.put(OrderStatus.CANCELLED_BY_USER, Collections.emptyList());
        transitions.put(OrderStatus.CANCELLED_BY_HOST, List.of(OrderStatus.REFUND_PENDING));
        transitions.put(OrderStatus.CANCELLED_SYSTEM, Collections.emptyList());

        // 退款相关状态
        transitions.put(OrderStatus.REFUND_PENDING, Arrays.asList(
                OrderStatus.REFUNDED,
                OrderStatus.REFUND_FAILED));
        transitions.put(OrderStatus.REFUNDED, Collections.emptyList());
        transitions.put(OrderStatus.REFUND_FAILED, List.of(OrderStatus.REFUND_PENDING));

        // 拒绝状态不能转换为任何状态
        transitions.put(OrderStatus.REJECTED, Collections.emptyList());

        // 检查当前状态是否有对应的流转规则
        if (!transitions.containsKey(currentStatus)) {
            return false;
        }

        // 检查目标状态是否在允许的流转列表中
        return transitions.get(currentStatus).contains(targetStatus);
    }

    /**
     * 处理取消订单逻辑
     */
    private OrderDTO processCancelOrder(Order order, String cancelType, String reason) {
        OrderStatus currentStatus = OrderStatus.valueOf(order.getStatus());
        OrderStatus targetStatus;

        try {
            targetStatus = OrderStatus.valueOf(cancelType);
        } catch (IllegalArgumentException e) {
            targetStatus = OrderStatus.CANCELLED;
        }

        // 保存原始取消类型，用于后续判断 RefundType
        String originalCancelType = cancelType;

        // 检查取消状态是否是一种取消状态
        if (targetStatus != OrderStatus.CANCELLED &&
                targetStatus != OrderStatus.CANCELLED_BY_USER &&
                targetStatus != OrderStatus.CANCELLED_BY_HOST &&
                targetStatus != OrderStatus.CANCELLED_SYSTEM) {
            throw new IllegalArgumentException("无效的取消状态");
        }

        // 获取当前用户信息
        User currentUser = null;
        Long actorId = null;
        String actorUsername = null;
        try {
            currentUser = getCurrentUser();
            actorId = currentUser.getId();
            actorUsername = currentUser.getUsername();
        } catch (Exception e) {
            log.warn("无法获取当前用户，可能是系统调用: {}", e.getMessage());
        }

        // 根据取消类型判断 RefundType
        RefundType refundType;
        if (OrderStatus.CANCELLED_BY_USER.name().equals(originalCancelType)) {
            refundType = RefundType.USER_REQUESTED;
        } else if (OrderStatus.CANCELLED_BY_HOST.name().equals(originalCancelType)) {
            refundType = RefundType.HOST_CANCELLED;
        } else {
            // CANCELLED_SYSTEM 或其他管理员取消类型
            refundType = RefundType.ADMIN_INITIATED;
        }

        // 特殊处理：如果是已支付订单被取消
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("已支付订单被取消，订单号: {}, 取消类型: {}, 退款类型: {}",
                    order.getOrderNumber(), originalCancelType, refundType);

            // 管理员取消（CANCELLED_SYSTEM/CANCELLED）：直接完成退款
            if (OrderStatus.CANCELLED_SYSTEM.name().equals(originalCancelType)
                    || OrderStatus.CANCELLED.name().equals(originalCancelType)) {
                // 管理员取消，直接执行退款（不需要审批流程）
                BigDecimal calculatedRefund = calculateRefundAmount(order);
                String refundTradeNo = "ADMIN_REFUND_" + System.currentTimeMillis();

                orderStatusUpdater.markRefunded(order);
                order.setRefundType(RefundType.ADMIN_INITIATED);
                order.setRefundReason(reason != null && !reason.isEmpty() ? reason : "管理员取消订单");
                order.setRefundAmount(calculatedRefund);
                order.setRefundTransactionId(refundTradeNo);
                order.setRefundInitiatedBy(actorId);
                order.setRefundInitiatedAt(LocalDateTime.now());

                String refundNote = String.format("管理员取消订单并退款 - 原因: %s, 退款金额: %s, 交易号: %s",
                        reason != null ? reason : "管理员取消",
                        calculatedRefund,
                        refundTradeNo);
                if (order.getRemark() != null && !order.getRemark().isEmpty()) {
                    order.setRemark(order.getRemark() + "\n" + refundNote);
                } else {
                    order.setRemark(refundNote);
                }

                log.info("管理员取消订单 {} 并直接退款，退款金额: {}", order.getOrderNumber(), calculatedRefund);
            } else {
                // 用户/房东取消：进入退款审批流程
                targetStatus = OrderStatus.REFUND_PENDING;

                if (!isValidStatusTransition(currentStatus, targetStatus)) {
                    throw new IllegalArgumentException(
                            String.format("不允许从当前状态 [%s] 进入退款流程", currentStatus.getDescription()));
                }

                orderStatusUpdater.markRefundPending(order);
                order.setRefundType(refundType);
                order.setRefundReason(reason != null && !reason.isEmpty() ? reason : "订单取消导致退款");

                BigDecimal calculatedRefund = calculateRefundAmount(order);
                order.setRefundAmount(calculatedRefund);

                order.setRefundInitiatedBy(actorId);
                order.setRefundInitiatedAt(LocalDateTime.now());

                String refundNote = String.format("已发起退款申请 - 类型: %s, 申请人: %s, 计算退款金额: %s",
                        refundType.getDescription(),
                        actorUsername != null ? actorUsername : "System",
                        calculatedRefund);
                if (order.getRemark() != null && !order.getRemark().isEmpty()) {
                    order.setRemark(order.getRemark() + "\n" + refundNote);
                } else {
                    order.setRemark(refundNote);
                }

                log.info("已支付订单 {} 状态已更新为退款中，退款类型: {}", order.getOrderNumber(), refundType.getDescription());
            }
        } else {
            if (!isValidStatusTransition(currentStatus, targetStatus)) {
                throw new IllegalArgumentException(
                        String.format("不允许从当前状态 [%s] 取消订单", currentStatus.getDescription()));
            }

            if (targetStatus == OrderStatus.CANCELLED) {
                orderStatusUpdater.markCancelled(order);
            } else if (targetStatus == OrderStatus.CANCELLED_BY_USER) {
                orderStatusUpdater.markCancelledByUser(order);
            } else if (targetStatus == OrderStatus.CANCELLED_BY_HOST) {
                orderStatusUpdater.markCancelledByHost(order);
            } else if (targetStatus == OrderStatus.CANCELLED_SYSTEM) {
                orderStatusUpdater.markCancelledBySystem(order);
            }
            if (reason != null && !reason.isEmpty()) {
                if (order.getRemark() != null && !order.getRemark().isEmpty()) {
                    order.setRemark(order.getRemark() + "\n取消原因: " + reason);
                } else {
                    order.setRemark("取消原因: " + reason);
                }
            }
        }

        Order cancelledOrder = orderRepository.save(order);

        // 释放锁定的优惠券
        try {
            couponService.releaseCoupons(order.getId());
        } catch (Exception e) {
            log.error("释放优惠券失败，订单: {}", order.getId(), e);
        }

        // 回退活动预算并更新优惠使用流水状态
        try {
            List<PromotionUsage> usages = promotionUsageRepository.findByOrderId(order.getId());
            for (PromotionUsage usage : usages) {
                String newStatus = order.getPaymentStatus() == PaymentStatus.PAID ? "REFUNDED" : "RELEASED";
                usage.setStatus(newStatus);
                promotionUsageRepository.save(usage);

                // 回退活动预算
                if (usage.getCampaignId() != null && usage.getDiscountAmount() != null) {
                    promotionMatchService.refundCampaignBudget(usage.getCampaignId(), usage.getDiscountAmount());
                }
            }
        } catch (Exception e) {
            log.error("回退活动预算或更新优惠流水失败，订单: {}", order.getId(), e);
        }

        // 发送订单取消/退款通知
        try {
            User guest = cancelledOrder.getGuest();
            User actor = null;
            try {
                actor = getCurrentUser();
            } catch (Exception e) {
                log.warn("无法在发送订单取消通知时获取当前用户: {}", e.getMessage());
            }

            if (guest != null) {
                if (cancelledOrder.getStatus().equals(OrderStatus.REFUND_PENDING.name())) {
                    // 通知客人退款申请已提交
                    orderNotificationService.sendOrderRefundRequestedNotification(
                            guest.getId(), cancelledOrder.getId(), cancelledOrder.getOrderNumber(),
                            reason != null && !reason.isEmpty() ? reason : "未提供原因",
                            refundType.toString(),
                            cancelledOrder.getRefundAmount() != null ? cancelledOrder.getRefundAmount().toString() : "0");

                    // 如果是用户申请退款（USER_REQUESTED），需要通知房东审批
                    if (refundType == RefundType.USER_REQUESTED
                            && cancelledOrder.getHomestay() != null
                            && cancelledOrder.getHomestay().getOwner() != null) {
                        orderNotificationService.sendRefundPendingNotification(
                                cancelledOrder.getHomestay().getOwner().getId(),
                                cancelledOrder.getId(),
                                cancelledOrder.getOrderNumber(),
                                refundType.toString(),
                                reason != null ? reason : "用户申请退款",
                                cancelledOrder.getRefundAmount() != null ? cancelledOrder.getRefundAmount().toString() : "0");
                    }
                } else if (cancelledOrder.getStatus().equals(OrderStatus.REFUNDED.name())) {
                    // 管理员直接退款完成的通知
                    orderNotificationService.sendOrderRefundApprovedNotification(
                            guest.getId(), cancelledOrder.getId(), cancelledOrder.getOrderNumber(),
                            reason != null ? reason : "管理员取消订单");
                } else {
                    orderNotificationService.sendOrderCancelledNotification(
                            guest.getId(), cancelledOrder.getId(), cancelledOrder.getOrderNumber(),
                            cancelledOrder.getHomestay() != null ? cancelledOrder.getHomestay().getTitle() : "未知房源",
                            currentStatus.getDescription(),
                            OrderStatus.valueOf(cancelledOrder.getStatus()).getDescription(),
                            reason != null && !reason.isEmpty() ? reason : "未提供原因",
                            actor != null && actor.getId().equals(guest.getId()));
                }
                log.info("已为用户 {} 发送订单 {} 处理通知", guest.getUsername(), cancelledOrder.getOrderNumber());
            }
        } catch (Exception e) {
            log.error("发送订单处理通知失败: {}", e.getMessage(), e);
        }

        return convertToDTO(cancelledOrder);
    }

    @Override
    @Transactional
    public OrderDTO systemCancelOrder(Long id, String cancelType, String reason) {
        // 系统级取消订单，不需要用户认证（用于定时任务等场景）
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

        // 直接调用 processCancelOrder 方法处理取消逻辑
        return processCancelOrder(order, cancelType, reason);
    }

    /**
     * 计算退款金额（优先使用价格快照中的 payableAmount）
     */
    private BigDecimal calculateRefundAmount(Order order) {
        if (order.getCheckInDate() == null || order.getTotalAmount() == null) {
            return BigDecimal.ZERO;
        }

        // 优先读取价格快照中的实付金额
        BigDecimal baseAmount = order.getTotalAmount();
        try {
            PricingResult snapshot = pricingService.getPriceSnapshot(order.getId());
            if (snapshot != null && snapshot.getPayableAmount() != null) {
                baseAmount = snapshot.getPayableAmount();
                log.info("退款计算使用价格快照金额: orderId={}, payableAmount={}", order.getId(), baseAmount);
            }
        } catch (Exception e) {
            log.warn("读取价格快照失败，回退到订单总金额: {}", e.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkInTime = order.getCheckInDate().atTime(14, 0);
        long hoursBetween = java.time.Duration.between(now, checkInTime).toHours();

        int policyType = 2;
        if (order.getHomestay() != null && order.getHomestay().getCancelPolicyType() != null) {
            policyType = order.getHomestay().getCancelPolicyType();
        }

        BigDecimal refundAmt;

        if (policyType == 1) {
            if (hoursBetween >= 24) {
                refundAmt = baseAmount;
            } else {
                int nights = order.getNights() != null ? order.getNights() : 1;
                if (nights <= 1) {
                    refundAmt = BigDecimal.ZERO;
                } else {
                    BigDecimal perNight = baseAmount.divide(new BigDecimal(nights), 2, java.math.RoundingMode.HALF_UP);
                    refundAmt = baseAmount.subtract(perNight);
                    if (refundAmt.compareTo(BigDecimal.ZERO) < 0) refundAmt = BigDecimal.ZERO;
                }
            }
        } else if (policyType == 3) {
            if (hoursBetween >= 72) {
                refundAmt = baseAmount;
            } else {
                refundAmt = baseAmount.multiply(new BigDecimal("0.5")).setScale(2, java.math.RoundingMode.HALF_UP);
            }
        } else {
            if (hoursBetween >= 48) {
                refundAmt = baseAmount;
            } else if (hoursBetween >= 24) {
                refundAmt = baseAmount.multiply(new BigDecimal("0.5")).setScale(2, java.math.RoundingMode.HALF_UP);
            } else {
                int nights = order.getNights() != null ? order.getNights() : 1;
                if (nights <= 1) {
                    refundAmt = BigDecimal.ZERO;
                } else {
                    BigDecimal perNight = baseAmount.divide(new BigDecimal(nights), 2, java.math.RoundingMode.HALF_UP);
                    refundAmt = baseAmount.subtract(perNight);
                    if (refundAmt.compareTo(BigDecimal.ZERO) < 0) refundAmt = BigDecimal.ZERO;
                }
            }
        }

        return refundAmt;
    }

    /**
     * 订单完成后自动发放返券
     */
    private void issueRewardCoupons(Long userId, String triggerEvent) {
        List<com.homestay3.homestaybackend.entity.CouponTemplate> templates =
                couponTemplateRepository.findByAutoIssueTriggerAndStatus(triggerEvent, "ACTIVE");
        if (templates.isEmpty()) {
            return;
        }
        for (com.homestay3.homestaybackend.entity.CouponTemplate template : templates) {
            try {
                // 检查用户是否已领取该模板（避免重复发放）
                long claimedCount = userCouponRepository.countByUserIdAndTemplateId(userId, template.getId());
                if (claimedCount >= template.getPerUserLimit()) {
                    log.debug("用户 {} 已达到模板 {} 领取上限，跳过返券", userId, template.getId());
                    continue;
                }
                couponService.claimCoupon(userId, template.getId());
                log.info("订单返券成功：用户ID={}, 模板ID={}, 触发事件={}", userId, template.getId(), triggerEvent);
            } catch (Exception e) {
                log.warn("订单返券失败：用户ID={}, 模板ID={}, 原因={}", userId, template.getId(), e.getMessage());
            }
        }
    }

    /**
     * 将 Order 实体转换为 OrderDTO
     */
    private OrderDTO convertToDTO(Order order) {
        if (order == null) {
            return null;
        }

        boolean isReviewed = reviewRepository.existsByOrder(order);
        ReviewDTO reviewDTO = null;

        if (OrderStatus.COMPLETED.name().equals(order.getStatus()) && isReviewed) {
            Optional<Review> reviewOpt = reviewRepository.findByOrder(order);
            if (reviewOpt.isPresent()) {
                reviewDTO = convertReviewToDTO(reviewOpt.get());
            }
        }

        User host = order.getHomestay() != null ? order.getHomestay().getOwner() : null;
        String hostName = host != null ? (host.getNickname() != null ? host.getNickname() : host.getUsername()) : null;
        Long hostId = host != null ? host.getId() : null;

        User guest = order.getGuest();
        String guestName = guest != null ? (guest.getNickname() != null ? guest.getNickname() : guest.getUsername())
                : null;
        Long guestId = guest != null ? guest.getId() : null;

        String refundInitiatedByName = null;
        String refundProcessedByName = null;

        if (order.getRefundInitiatedBy() != null) {
            Optional<User> initiatorOpt = userRepository.findById(order.getRefundInitiatedBy());
            if (initiatorOpt.isPresent()) {
                User initiator = initiatorOpt.get();
                refundInitiatedByName = initiator.getNickname() != null ? initiator.getNickname()
                        : initiator.getUsername();
            }
        }

        if (order.getRefundProcessedBy() != null) {
            Optional<User> processorOpt = userRepository.findById(order.getRefundProcessedBy());
            if (processorOpt.isPresent()) {
                User processor = processorOpt.get();
                refundProcessedByName = processor.getNickname() != null ? processor.getNickname()
                        : processor.getUsername();
            }
        }

        BigDecimal baseAmount = order.getPrice() != null ? order.getPrice().multiply(BigDecimal.valueOf(order.getNights())) : BigDecimal.ZERO;
        // 清洁费：固定金额 = 单晚价格 × 配置比例
        BigDecimal cleaningFeeAmount = getPricingConfig("pricing.cleaning_fee", "0.1");
        BigDecimal serviceFeeRate = getPricingConfig("pricing.service_fee", "0.15");
        BigDecimal cleaningFee = order.getPrice() != null ? order.getPrice().multiply(cleaningFeeAmount) : BigDecimal.ZERO;
        BigDecimal serviceFee = baseAmount.multiply(serviceFeeRate);

        return OrderDTO.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .homestayId(order.getHomestay() != null ? order.getHomestay().getId() : null)
                .homestayTitle(order.getHomestay() != null ? order.getHomestay().getTitle() : null)
                .guestId(guestId)
                .guestName(guestName)
                .guestPhone(order.getGuestPhone())
                .checkInDate(order.getCheckInDate())
                .checkOutDate(order.getCheckOutDate())
                .nights(order.getNights())
                .guestCount(order.getGuestCount())
                .price(order.getPrice())
                .cleaningFee(cleaningFee)
                .serviceFee(serviceFee)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null)
                .paymentMethod(order.getPaymentMethod())
                .remark(order.getRemark())
                .hostId(hostId)
                .hostName(hostName)
                .imageUrl(order.getHomestay() != null ? order.getHomestay().getCoverImage() : null)
                .createTime(order.getCreatedAt())
                .updateTime(order.getUpdatedAt())
                .completedAt(order.getCompletedAt())
                .isReviewed(isReviewed)
                .review(reviewDTO)
                .refundType(order.getRefundType() != null ? order.getRefundType().name() : null)
                .refundReason(order.getRefundReason())
                .refundAmount(order.getRefundAmount())
                .refundInitiatedBy(order.getRefundInitiatedBy())
                .refundInitiatedByName(refundInitiatedByName)
                .refundInitiatedAt(order.getRefundInitiatedAt())
                .refundProcessedBy(order.getRefundProcessedBy())
                .refundProcessedByName(refundProcessedByName)
                .refundProcessedAt(order.getRefundProcessedAt())
                .refundTransactionId(order.getRefundTransactionId())
                .refundRejectionReason(order.getRefundRejectionReason())
                .build();
    }

    /**
     * 将 Review 实体转换为 ReviewDTO
     */
    private ReviewDTO convertReviewToDTO(Review review) {
        if (review == null) {
            return null;
        }
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

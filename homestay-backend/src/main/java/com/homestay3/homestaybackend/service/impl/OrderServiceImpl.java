package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.dto.PriceCalculationRequest;
import com.homestay3.homestaybackend.dto.PriceCalculationResponse;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.PaymentStatus;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.SystemConfigService;
import com.homestay3.homestaybackend.service.OrderService;
import com.homestay3.homestaybackend.service.PaymentProcessingService;
import com.homestay3.homestaybackend.service.OrderLifecycleService;
import com.homestay3.homestaybackend.service.PricingService;
import com.homestay3.homestaybackend.service.RefundPolicyCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final HomestayRepository homestayRepository;
    private final OrderDtoAssembler orderDtoAssembler;
    private final PaymentProcessingService paymentProcessingService;
    private final OrderLifecycleService orderLifecycleService;
    private final PricingService pricingService;
    private final RefundPolicyCalculator refundPolicyCalculator;
    private final ObjectProvider<SystemConfigService> systemConfigServiceProvider;

    public OrderServiceImpl(OrderRepository orderRepository,
                            UserRepository userRepository,
                            HomestayRepository homestayRepository,
                            OrderDtoAssembler orderDtoAssembler,
                            PaymentProcessingService paymentProcessingService,
                            OrderLifecycleService orderLifecycleService,
                            PricingService pricingService,
                            RefundPolicyCalculator refundPolicyCalculator,
                            ObjectProvider<SystemConfigService> systemConfigServiceProvider) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.homestayRepository = homestayRepository;
        this.orderDtoAssembler = orderDtoAssembler;
        this.paymentProcessingService = paymentProcessingService;
        this.orderLifecycleService = orderLifecycleService;
        this.pricingService = pricingService;
        this.refundPolicyCalculator = refundPolicyCalculator;
        this.systemConfigServiceProvider = systemConfigServiceProvider;
    }
    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Override
    public OrderDTO createOrder(OrderDTO orderDTO) {
        // 委托给OrderLifecycleService处理核心生命周期逻辑
        return orderLifecycleService.createOrder(orderDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDTO getOrderById(Long id) {
        // 委托给OrderLifecycleService处理核心生命周期逻辑
        return orderLifecycleService.getOrderById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDTO getOrderByOrderNumber(String orderNumber) {
        // 委托给OrderLifecycleService处理核心生命周期逻辑
        return orderLifecycleService.getOrderByOrderNumber(orderNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDTO> getMyOrders(Map<String, String> params, Pageable pageable) {
        // 委托给OrderLifecycleService处理核心生命周期逻辑
        return orderLifecycleService.getMyOrders(params, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDTO> getOwnerOrders(String ownerUsername, Map<String, String> params, Pageable pageable) {
        // 委托给OrderLifecycleService处理核心生命周期逻辑
        return orderLifecycleService.getOwnerOrders(ownerUsername, params, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getPendingOrderCount(String ownerUsername) {
        // 委托给OrderLifecycleService处理核心生命周期逻辑
        return orderLifecycleService.getPendingOrderCount(ownerUsername);
    }

    @Override
    @Transactional
    public OrderDTO confirmOrder(Long id) {
        // 委托给OrderLifecycleService处理核心生命周期逻辑
        return orderLifecycleService.confirmOrder(id);
    }

    @Override
    @Transactional
    public OrderDTO updateOrderStatus(Long id, String status) {
        // 委托给OrderLifecycleService处理核心生命周期逻辑
        return orderLifecycleService.updateOrderStatus(id, status);
    }

    @Override
    @Transactional
    public OrderDTO cancelOrder(Long id) {
        // 委托给OrderLifecycleService处理核心生命周期逻辑
        return orderLifecycleService.cancelOrder(id);
    }

    @Override
    @Transactional
    public OrderDTO cancelOrderWithReason(Long id, String cancelType, String reason) {
        // 委托给OrderLifecycleService处理
        return orderLifecycleService.cancelOrderWithReason(id, cancelType, reason);
    }

    /**
     * 系统级取消订单方法，用于定时任务等不需要用户认证的场景
     * 此方法不检查用户权限，请谨慎使用
     */
    @Override
    @Transactional
    public OrderDTO systemCancelOrder(Long id, String cancelType, String reason) {
        // 委托给OrderLifecycleService处理
        return orderLifecycleService.systemCancelOrder(id, cancelType, reason);
    }

    @Override
    @Transactional
    public OrderDTO payOrder(Long id) {
        // 委托给PaymentProcessingService处理
        return paymentProcessingService.processPayment(id);
    }

    @Override
    @Transactional
    public OrderDTO payOrder(Long id, String paymentMethod) {
        // 委托给PaymentProcessingService处理
        return paymentProcessingService.processPayment(id, paymentMethod);
    }

    @Override
    public OrderDTO createOrderPreview(OrderDTO orderDTO) {
        // 获取房源信息
        Homestay homestay = homestayRepository.findById(orderDTO.getHomestayId())
                .orElseThrow(() -> new ResourceNotFoundException("房源不存在"));

        // 获取当前用户信息
        User currentUser = getCurrentUser();

        // 检查日期是否有效
        if (orderDTO.getCheckInDate() == null || orderDTO.getCheckOutDate() == null) {
            throw new IllegalArgumentException("入住和退房日期不能为空");
        }

        if (orderDTO.getCheckInDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("入住日期不能早于今天");
        }

        if (orderDTO.getCheckOutDate().isBefore(orderDTO.getCheckInDate())) {
            throw new IllegalArgumentException("退房日期不能早于入住日期");
        }

        // 计算住宿天数
        long nights = ChronoUnit.DAYS.between(orderDTO.getCheckInDate(), orderDTO.getCheckOutDate());
        if (nights < homestay.getMinNights()) {
            throw new IllegalArgumentException("住宿天数不能少于" + homestay.getMinNights() + "晚");
        }

        // 检查是否有重叠的预订
        boolean hasOverlap = orderRepository.existsOverlappingBooking(
                homestay.getId(), orderDTO.getCheckInDate(), orderDTO.getCheckOutDate());
        if (hasOverlap) {
            throw new IllegalArgumentException("所选日期已被预订，请选择其他日期");
        }

        // 计算价格信息
        BigDecimal pricePerNight = homestay.getPrice();
        BigDecimal baseAmount = pricePerNight.multiply(BigDecimal.valueOf(nights));

        // 从系统配置动态读取费用配置
        // 清洁费：固定金额 = 单晚价格 × 配置比例（一次性，不论住多少晚）
        BigDecimal cleaningFee = pricePerNight.multiply(getPricingConfig("pricing.cleaning_fee", "0.1"));
        // 服务费：按订单总价的百分比收取
        BigDecimal serviceFee = baseAmount.multiply(getPricingConfig("pricing.service_fee", "0.15"));

        // 计算总价
        BigDecimal totalAmount = baseAmount.add(cleaningFee).add(serviceFee);

        // 创建预览订单DTO对象
        OrderDTO previewOrderDTO = OrderDTO.builder()
                .homestayId(homestay.getId())
                .homestayTitle(homestay.getTitle())
                .guestId(currentUser.getId())
                .guestName(currentUser.getNickname() != null ? currentUser.getNickname() : currentUser.getUsername())
                .guestPhone(orderDTO.getGuestPhone())
                .checkInDate(orderDTO.getCheckInDate())
                .checkOutDate(orderDTO.getCheckOutDate())
                .nights((int) nights)
                .guestCount(orderDTO.getGuestCount())
                .price(pricePerNight)
                .totalAmount(totalAmount)
                .remark(orderDTO.getRemark())
                .build();

        // 添加额外信息，用于前端显示
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("baseAmount", baseAmount);
        additionalInfo.put("cleaningFee", cleaningFee);
        additionalInfo.put("serviceFee", serviceFee);
        additionalInfo.put("pricePerNight", pricePerNight);
        additionalInfo.put("imageUrl", homestay.getCoverImage());
        additionalInfo.put("addressDetail", homestay.getAddressDetail());
        additionalInfo.put("provinceText", homestay.getProvinceText());
        additionalInfo.put("cityText", homestay.getCityText());
        additionalInfo.put("districtText", homestay.getDistrictText());

        // 这里我们不能直接在OrderDTO添加additionalInfo字段，
        // 因为它没有定义这个字段，我们需要修改前端代码来处理这些额外信息
        // 或者扩展OrderDTO类。这里为了简单，我们返回基本信息。

        return previewOrderDTO;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDTO> getAdminOrders(
            Pageable pageable,
            String orderNumber,
            String guestName,
            String homestayTitle,
            String status,
            String paymentStatus,
            String paymentMethod,
            String hostName,
            LocalDate checkInDateStart,
            LocalDate checkInDateEnd,
            LocalDate createTimeStart,
            LocalDate createTimeEnd) {
        log.info(
                "Admin获取订单列表，筛选条件: orderNumber={}, guestName={}, homestayTitle={}, status={}, paymentStatus={}, paymentMethod={}, hostName={}, checkInDateStart={}, checkInDateEnd={}, createTimeStart={}, createTimeEnd={}",
                orderNumber, guestName, homestayTitle, status, paymentStatus, paymentMethod, hostName, checkInDateStart,
                checkInDateEnd, createTimeStart, createTimeEnd);

        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 订单号
            if (orderNumber != null && !orderNumber.isEmpty()) {
                predicates.add(cb.like(root.get("orderNumber"), "%" + orderNumber + "%"));
            }

            // 订单状态
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 支付状态
            if (paymentStatus != null && !paymentStatus.isEmpty()) {
                try {
                    PaymentStatus ps = PaymentStatus.valueOf(paymentStatus.toUpperCase());
                    predicates.add(cb.equal(root.get("paymentStatus"), ps));
                } catch (IllegalArgumentException e) {
                    log.warn("无效的支付状态筛选值: {}", paymentStatus);
                }
            }

            // 支付方式
            if (paymentMethod != null && !paymentMethod.isEmpty()) {
                predicates.add(cb.equal(root.get("paymentMethod"), paymentMethod));
            }

            // 入住日期范围
            if (checkInDateStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("checkInDate"), checkInDateStart));
            }
            if (checkInDateEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("checkInDate"), checkInDateEnd)); // 通常筛选入住开始日期
            }

            // 创建日期范围
            if (createTimeStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createTimeStart.atStartOfDay()));
            }
            if (createTimeEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createTimeEnd.plusDays(1).atStartOfDay())); // 包含结束当天
            }

            // 关联查询条件
            Join<Order, User> guestJoin = null;
            Join<Order, Homestay> homestayJoin = null;
            Join<Homestay, User> hostJoin = null;

            // 住客姓名
            if (guestName != null && !guestName.isEmpty()) {
                guestJoin = root.join("guest");
                predicates.add(cb.like(guestJoin.get("username"), "%" + guestName + "%")); // 假设按用户名搜索
            }

            // 房源标题
            if (homestayTitle != null && !homestayTitle.isEmpty()) {
                if (homestayJoin == null)
                    homestayJoin = root.join("homestay");
                predicates.add(cb.like(homestayJoin.get("title"), "%" + homestayTitle + "%"));
            }

            // 房东姓名/用户名
            if (hostName != null && !hostName.isEmpty()) {
                if (homestayJoin == null)
                    homestayJoin = root.join("homestay");
                hostJoin = homestayJoin.join("owner");
                predicates.add(cb.like(hostJoin.get("username"), "%" + hostName + "%")); // 假设按用户名搜索
            }

            // 确保不返回重复记录（如果使用了多个Join）
            query.distinct(true);

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return orderRepository.findAll(spec, pageable).map(orderDtoAssembler::toAdminListItem);
    }

    @Override
    @Transactional
    public OrderDTO confirmPayment(Long id) {
        // 委托给PaymentProcessingService处理
        return paymentProcessingService.confirmPayment(id);
    }

    @Override
    @Transactional
    public OrderDTO executeRefund(Long id, String reason) {
        log.info("管理员直接执行退款，订单ID: {}, 原因: {}", id, reason);
        return paymentProcessingService.executeRefund(id, reason);
    }

    @Override
    @Transactional
    public OrderDTO approveRefund(Long id, String refundNote) {
        log.info("审批退款申请，订单ID: {}, 备注: {}", id, refundNote);
        return paymentProcessingService.approveRefund(id, refundNote);
    }

    @Override
    @Transactional
    public OrderDTO rejectOrder(Long id, String reason) {
        // 委托给OrderLifecycleService处理核心生命周期逻辑
        return orderLifecycleService.rejectOrder(id, reason);
    }

    @Override
    @Transactional
    public OrderDTO rejectRefund(Long id, String rejectReason) {
        log.info("拒绝退款申请，订单ID: {}, 拒绝原因: {}", id, rejectReason);
        return paymentProcessingService.rejectRefund(id, rejectReason);
    }

    @Override
    @Transactional
    public OrderDTO requestUserRefund(Long id, String reason) {
        // 委托给PaymentProcessingService处理
        return paymentProcessingService.requestUserRefund(id, reason);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在，ID: " + id));

        // 检查订单状态，只允许删除已完成或已取消的订单
        if (!Arrays.asList("COMPLETED", "CANCELLED", "REJECTED").contains(order.getStatus())) {
            throw new IllegalStateException("只能删除已完成、已取消或已拒绝的订单");
        }

        orderRepository.delete(order);
    }

    @Override
    public byte[] exportOrders(Map<String, String> params) {
        // 获取符合条件的所有订单
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 按订单号筛选
            if (params.containsKey("orderNumber") && !params.get("orderNumber").isEmpty()) {
                predicates.add(cb.like(root.get("orderNumber"), "%" + params.get("orderNumber") + "%"));
            }

            // 按状态筛选
            if (params.containsKey("status") && !params.get("status").isEmpty()) {
                predicates.add(cb.equal(root.get("status"), params.get("status")));
            }

            // 按日期范围筛选
            if (params.containsKey("startDate") && !params.get("startDate").isEmpty()) {
                LocalDate startDate = LocalDate.parse(params.get("startDate"));
                predicates.add(cb.greaterThanOrEqualTo(root.get("checkInDate"), startDate));
            }

            if (params.containsKey("endDate") && !params.get("endDate").isEmpty()) {
                LocalDate endDate = LocalDate.parse(params.get("endDate"));
                predicates.add(cb.lessThanOrEqualTo(root.get("checkOutDate"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Order> orders = orderRepository.findAll(spec);

        // 创建导出数据（使用简单的CSV格式）
        try {
            StringBuilder csv = new StringBuilder();
            // 添加CSV头
            csv.append("订单编号,房源名称,入住日期,退房日期,晚数,房客姓名,房客电话,总金额,状态,创建时间\n");

            // 添加订单数据
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            for (Order order : orders) {
                csv.append(order.getOrderNumber()).append(",");
                csv.append(order.getHomestay().getTitle()).append(",");
                csv.append(order.getCheckInDate().format(dateFormatter)).append(",");
                csv.append(order.getCheckOutDate().format(dateFormatter)).append(",");
                csv.append(order.getNights()).append(",");
                csv.append(order.getGuest().getFullName()).append(",");
                csv.append(order.getGuestPhone()).append(",");
                csv.append(order.getTotalAmount()).append(",");
                csv.append(order.getStatus()).append(",");
                csv.append(order.getCreatedAt().format(dateTimeFormatter)).append("\n");
            }

            return csv.toString().getBytes("UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("导出订单失败", e);
        }
    }

    // ========== 管理员异常订单统计 ==========

    @Override
    public Map<String, Long> getExceptionOrderStats() {
        Map<String, Long> stats = new HashMap<>();

        // 待处理超时订单（PENDING超过24小时）
        LocalDateTime dayAgo = LocalDateTime.now().minusHours(24);
        stats.put("pendingTimeout", orderRepository.countPendingTimeoutOrders(dayAgo));

        // 支付失败订单
        stats.put("paymentFailed", orderRepository.countPaymentFailedOrders());

        // 退款失败订单
        stats.put("refundFailed", orderRepository.countRefundFailedOrders());

        // 已支付但未入住（超过入住日期）
        stats.put("notCheckedIn", orderRepository.countNotCheckedInOrders(LocalDate.now()));

        // 退款处理中
        stats.put("refundPending", orderRepository.countRefundPendingOrders());

        // 争议待处理
        stats.put("disputePending", orderRepository.countDisputePendingOrders());

        // 待确认订单（今天及之前创建的）
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        stats.put("pendingConfirm", orderRepository.countPendingConfirmOrders(startOfToday));

        // 计算异常订单总数
        long total = stats.values().stream().mapToLong(Long::longValue).sum();
        stats.put("total", total);

        return stats;
    }

    // 工具方法：获取当前登录用户
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }

    // 工具方法：检查用户是否为订单的房东
    private boolean isOrderOwner(Order order, User user) {
        return order.getHomestay() != null && order.getHomestay().getOwner() != null
                && order.getHomestay().getOwner().getId().equals(user.getId());
    }

    // 工具方法：检查用户是否为订单的客户
    private boolean isOrderGuest(Order order, User user) {
        return order.getGuest() != null && order.getGuest().getId().equals(user.getId());
    }

    /**
     * 检查用户是否有权访问和操作此订单
     * 
     * @param order 订单对象
     * @param user  当前用户
     * @return 如果用户有权访问此订单则返回true
     */
    private boolean isOrderAccessible(Order order, User user) {
        log.debug("检查订单访问权限 - 订单ID: {}, 用户: {}, 角色: {}",
                order.getId(), user.getUsername(), user.getRole());
        log.debug("订单所属房东ID: {}, 当前用户ID: {}",
                order.getHomestay().getOwner().getId(), user.getId());
        log.debug("订单客人ID: {}", order.getGuest().getId());

        // 检查用户角色 - 考虑可能有前缀或不一致的情况
        boolean isAdmin = user.getRole().contains("ADMIN");

        // 管理员可以访问所有订单
        if (isAdmin) {
            log.debug("用户是管理员，允许访问");
            return true;
        }

        // 房东可以访问自己房源的订单
        boolean isHost = user.getRole().contains("HOST");
        if (isHost) {
            boolean isOwner = isOrderOwner(order, user);
            log.debug("用户是房东，是否为订单所属房东: {}", isOwner);
            if (isOwner) {
                return true;
            }
        }

        // 客户可以访问自己的订单
        boolean isUser = user.getRole().contains("USER");
        if (isUser) {
            boolean isGuest = isOrderGuest(order, user);
            log.debug("用户是普通用户，是否为订单客人: {}", isGuest);
            if (isGuest) {
                return true;
            }
        }

        log.debug("权限检查失败，拒绝访问");
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getRefundPreview(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在: " + orderId));

        // 权限检查：只有订单相关方才能查看退款预览
        User currentUser = getCurrentUser();
        if (!isOrderAccessible(order, currentUser)) {
            throw new AccessDeniedException("您无权查看此订单的退款信息");
        }

        // 检查是否可以发起退款
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            return Map.of(
                "eligible", false,
                "message", "订单未处于可退款状态（当前支付状态: " + (order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "未知") + "）"
            );
        }

        RefundPolicyCalculator.Quote quote = refundPolicyCalculator.calculate(order);
        Map<String, Object> preview = new HashMap<>();
        preview.put("estimatedRefundAmount", quote.amount());
        preview.put("policyDescription", quote.description());
        if (quote.hoursBeforeCheckIn() != null) {
            preview.put("totalAmount", order.getTotalAmount());
            preview.put("policyType", quote.policyType());
            preview.put("hoursBeforeCheckIn", quote.hoursBeforeCheckIn());
        }
        preview.put("eligible", true);
        preview.put("orderId", orderId);
        preview.put("orderNumber", order.getOrderNumber());
        return preview;
    }

    // ========== 价格计算 ==========

    @Override
    public PriceCalculationResponse calculatePrice(PriceCalculationRequest request) {
        // 委托给统一计价服务，保持接口兼容
        com.homestay3.homestaybackend.dto.PricingQuoteRequest quoteRequest =
                com.homestay3.homestaybackend.dto.PricingQuoteRequest.builder()
                        .homestayId(request.getHomestayId())
                        .checkInDate(request.getCheckInDate())
                        .checkOutDate(request.getCheckOutDate())
                        .guestCount(request.getGuestCount())
                        .build();

        com.homestay3.homestaybackend.dto.PricingQuoteResponse quote =
                pricingService.quote(quoteRequest, getCurrentUser() != null ? getCurrentUser().getId() : null);

        return PriceCalculationResponse.builder()
                .homestayId(request.getHomestayId())
                .homestayTitle(quote.getPriceDetails() != null ? null : null)
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .guestCount(request.getGuestCount())
                .nights(quote.getNights())
                .dailyPrices(quote.getDailyPrices())
                .basePrice(quote.getRoomOriginalAmount())
                .discountAmount(quote.getActivityDiscountAmount().add(quote.getCouponDiscountAmount()))
                .finalBasePrice(quote.getRoomOriginalAmount()
                        .subtract(quote.getActivityDiscountAmount())
                        .subtract(quote.getCouponDiscountAmount()))
                .cleaningFee(quote.getCleaningFee())
                .serviceFee(quote.getServiceFee())
                .totalPrice(quote.getPayableAmount())
                .priceDetails(quote.getPriceDetails())
                .build();
    }

    /**
     * 获取定价配置，默认值
     */
    private BigDecimal getPricingConfig(String key, String defaultValue) {
        String value = systemConfigServiceProvider.getObject().getConfigValue(key, defaultValue);
        log.info("读取配置: {} = {} (默认: {})", key, value, defaultValue);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("定价配置 {} 格式错误，使用默认值 {}，错误: {}", key, defaultValue, e.getMessage());
            return new BigDecimal(defaultValue);
        }
    }



}

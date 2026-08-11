package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.dto.PriceCalculationRequest;
import com.homestay3.homestaybackend.dto.PriceCalculationResponse;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.PaymentStatus;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.DisputeService;
import com.homestay3.homestaybackend.service.IOrderTimeoutService;
import com.homestay3.homestaybackend.service.OrderService;
import com.homestay3.homestaybackend.service.PaymentProcessingService;
import com.homestay3.homestaybackend.service.impl.OrderStatusUpdater;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "订单", description = "C端订单创建、查询、支付、退款、评价等接口")
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentProcessingService paymentProcessingService;
    private final DisputeService disputeService;
    private final IOrderTimeoutService orderTimeoutService;
    private final OrderStatusUpdater orderStatusUpdater;
    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    @PostMapping
    @Operation(summary = "创建订单", description = "提交订单并返回订单详情")
    public ResponseEntity<?> createOrder(@RequestBody OrderDTO orderDTO) {
        try {
            OrderDTO createdOrder = orderService.createOrder(orderDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdOrder);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tab,
            Authentication authentication) {
        try {
            Map<String, String> params = new HashMap<>();
            if (status != null) {
                params.put("status", status);
            }
            if (tab != null) {
                params.put("tab", tab);
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<OrderDTO> orders;

            // 根据用户角色返回不同的订单列表
            String role = authentication.getAuthorities().iterator().next().getAuthority();
            if (role.equals("ROLE_HOST")) {
                String username = authentication.getName();
                orders = orderService.getOwnerOrders(username, params, pageable);
            } else {
                orders = orderService.getMyOrders(params, pageable);
            }

            return ResponseEntity.ok(Map.of("data", orders));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrderById(@PathVariable Long id) {
        try {
            OrderDTO order = orderService.getOrderById(id);
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<?> getOrderByNumber(@PathVariable String orderNumber) {
        try {
            OrderDTO order = orderService.getOrderByOrderNumber(orderNumber);
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HOST', 'LANDLORD')")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> statusMap) {
        try {
            String status = statusMap.get("status");
            if (status == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "状态参数不能为空"));
            }

            OrderDTO updatedOrder = orderService.updateOrderStatus(id, status);
            return ResponseEntity.ok(updatedOrder);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> requestBody,
            Authentication authentication) {
        try {
            String reason = requestBody != null ? requestBody.get("reason") : null;

            // 获取用户角色，以确定是用户取消还是房东取消
            String role = authentication.getAuthorities().iterator().next().getAuthority();
            String cancelType;

            if (role.equals("ROLE_HOST")) {
                cancelType = OrderStatus.CANCELLED_BY_HOST.name();
            } else {
                cancelType = OrderStatus.CANCELLED_BY_USER.name();
            }

            OrderDTO cancelledOrder = orderService.cancelOrderWithReason(id, cancelType, reason);
            return ResponseEntity.ok(cancelledOrder);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "errorCode", e instanceof IllegalArgumentException ? "ORDER_CANCELLATION_NOT_ALLOWED"
                            : "INTERNAL_SERVER_ERROR"));
        }
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<?> payOrder(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody) {
        log.info("收到订单 ID: {} 的支付请求，请求体: {}", id, requestBody);
        try {
            String paymentMethod = requestBody.getOrDefault("paymentMethod", "default");
            log.debug("提取支付方式: {}", paymentMethod);

            // 首先将订单状态设置为支付中 (这一步在业务逻辑中可能不是必需的，取决于具体流程)
            // OrderDTO paymentPendingOrder = orderService.updateOrderStatus(id,
            // OrderStatus.PAYMENT_PENDING.name());
            // log.debug("订单 {} 状态更新为 PENDING_PAYMENT (临时)", id);

            // 然后处理支付
            log.info("准备调用 PaymentProcessingService 处理订单 ID: {} 的支付...", id);
            OrderDTO paidOrder = paymentProcessingService.processPayment(id, paymentMethod);
            log.info("PaymentProcessingService 支付处理完成，订单 ID: {}, 返回结果: {}", id, paidOrder);
            return ResponseEntity.ok(paidOrder);
        } catch (IllegalArgumentException | AccessDeniedException e) {
            // 处理业务逻辑或权限错误
            log.warn("处理订单 {} 支付失败 (业务/权限错误): {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "errorCode", "PAYMENT_VALIDATION_ERROR"));
        } catch (Exception e) {
            // 处理其他意外错误
            log.error("处理订单 {} 支付时发生意外错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "支付处理失败，请稍后重试",
                    "errorCode", "INTERNAL_SERVER_ERROR"));
        }
    }

    @GetMapping("/timeout-config")
    public ResponseEntity<?> getTimeoutConfig() {
        try {
            return ResponseEntity.ok(orderTimeoutService.getTimeoutConfig());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/pending/count")
    @PreAuthorize("hasAnyRole('HOST', 'LANDLORD')")
    public ResponseEntity<?> getPendingOrderCount(Authentication authentication) {
        try {
            String username = authentication.getName();
            Long count = orderService.getPendingOrderCount(username);
            return ResponseEntity.ok(Map.of("count", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 创建订单预览，用于在用户确认前生成订单信息
     */
    @PostMapping("/preview")
    public ResponseEntity<?> createOrderPreview(@RequestBody OrderDTO orderDTO) {
        try {
            OrderDTO previewOrder = orderService.createOrderPreview(orderDTO);
            return ResponseEntity.ok(previewOrder);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "创建订单预览失败: " + e.getMessage()));
        }
    }

    /**
     * 计算订单价格（不创建订单）
     * 根据房源ID、入住日期、退房日期和人数计算价格明细
     */
    @PostMapping("/calculate-price")
    public ResponseEntity<?> calculatePrice(@RequestBody PriceCalculationRequest request) {
        try {
            PriceCalculationResponse response = orderService.calculatePrice(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("计算价格失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "计算价格失败: " + e.getMessage()));
        }
    }

    /**
     * 模拟支付成功（仅用于测试，仅限管理员）
     */
    @PostMapping("/{orderId}/payment/mock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> mockPayment(@PathVariable Long orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

            orderStatusUpdater.markPaid(order);
            Order updatedOrder = orderRepository.save(order);

            // 触发统一支付成功后置处理（核销优惠券、生成收益等）
            paymentProcessingService.handleOrderPaidSuccess(updatedOrder.getId());

            OrderDTO orderDTO = convertToDTO(updatedOrder);

            return ResponseEntity.ok(Map.of(
                    "message", "模拟支付成功",
                    "paid", true,
                    "order", orderDTO));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 生成支付二维码
     */
    @PostMapping("/{orderId}/payment/qrcode")
    public ResponseEntity<?> generatePaymentQRCode(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> paymentInfo) {
        log.info("收到为订单 ID: {} 生成支付二维码的请求，支付方式: {}", orderId, paymentInfo.get("method"));
        try {
            String method = paymentInfo.get("method");
            if (method == null) {
                log.warn("订单 {} 生成二维码请求缺少支付方式", orderId);
                return ResponseEntity.badRequest().body(Map.of("error", "支付方式不能为空"));
            }

            // --- 新增：将订单状态更新为待支付 ---
            try {
                log.info("准备将订单 ID: {} 状态更新为 PAYMENT_PENDING...", orderId);
                orderService.updateOrderStatus(orderId, OrderStatus.PAYMENT_PENDING.name());
                log.info("订单 ID: {} 状态已更新为 PAYMENT_PENDING", orderId);
            } catch (Exception e) {
                // 如果更新状态失败，记录错误但仍尝试生成二维码，让用户有机会重试支付
                log.error("更新订单 {} 状态为 PAYMENT_PENDING 时失败: {}，将继续生成二维码", orderId, e.getMessage(), e);
                // 不在此处中断流程，允许用户获取二维码
            }
            // --- 状态更新结束 ---

            // 生成支付ID
            String paymentId = UUID.randomUUID().toString();

            // 生成模拟二维码URL
            String qrCodeUrl;
            if ("wechat".equals(method)) {
                qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?data=wechat:pay:" + orderId + ":" + paymentId
                        + "&size=200x200";
            } else if ("alipay".equals(method)) {
                qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?data=alipay:pay:" + orderId + ":" + paymentId
                        + "&size=200x200";
            } else {
                qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?data=unknown:pay:" + orderId + ":" + paymentId
                        + "&size=200x200";
            }
            log.info("为订单 ID: {} 生成二维码 URL 成功: {}", orderId, qrCodeUrl);

            return ResponseEntity.ok(Map.of("qrCode", qrCodeUrl));
        } catch (Exception e) {
            log.error("为订单 {} 生成支付二维码时发生意外错误: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "生成支付二维码失败，请稍后重试",
                    "errorCode", "QRCODE_GENERATION_ERROR"));
        }
    }

    /**
     * 检查支付状态 (改造为确认支付)
     */
    @GetMapping("/{orderId}/payment/status")
    public ResponseEntity<?> checkPaymentStatus(@PathVariable Long orderId) {
        log.info("收到检查订单 ID: {} 支付状态的请求", orderId);
        try {
            OrderDTO order = orderService.getOrderById(orderId); // 获取最新订单信息
            log.debug("获取到订单 {} 的当前状态: status={}, paymentStatus={}", orderId, order.getStatus(),
                    order.getPaymentStatus());

            // --- 修改核心逻辑：只检查状态，不触发支付 ---
            boolean isPaid = OrderStatus.PAID.name().equals(order.getStatus()) ||
                    (order.getPaymentStatus() != null && PaymentStatus.PAID.name().equals(order.getPaymentStatus())); // 检查
                                                                                                                      // status
                                                                                                                      // 或
                                                                                                                      // paymentStatus
                                                                                                                      // (添加
                                                                                                                      // null
                                                                                                                      // 检查)

            if (isPaid) {
                log.info("订单 {} 状态已为 PAID，返回支付成功", orderId);
                return ResponseEntity.ok(Map.of("paid", true));
            } else {
                // 对于其他所有状态 (PENDING, CONFIRMED, PAYMENT_PENDING, FAILED, UNPAID, CANCELLED 等)
                log.info("订单 {} 状态为 status={}, paymentStatus={}，返回支付未完成", orderId, order.getStatus(),
                        order.getPaymentStatus());
                return ResponseEntity.ok(Map.of("paid", false));
            }
            // --- 移除原先处理 PAYMENT_PENDING 并调用 orderService.payOrder() 的逻辑 ---

        } catch (ResourceNotFoundException e) {
            log.warn("检查支付状态失败，未找到订单 ID: {}", orderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("paid", false, "error", "订单不存在"));
        } catch (Exception e) {
            log.error("检查订单 {} 支付状态时发生意外错误: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "paid", false,
                    "error", "检查支付状态失败，请稍后重试"));
        }
    }

    @GetMapping("/host")
    @PreAuthorize("hasAnyRole('HOST', 'LANDLORD')")
    public ResponseEntity<?> getHostOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long homestayId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            Map<String, String> params = new HashMap<>();
            if (status != null) {
                params.put("status", status);
            }
            if (homestayId != null) {
                params.put("homestayId", homestayId.toString());
            }
            if (startDate != null) {
                params.put("startDate", startDate);
            }
            if (endDate != null) {
                params.put("endDate", endDate);
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<OrderDTO> orders = orderService.getOwnerOrders(username, params, pageable);

            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 验证当前用户是否为订单所属房东
     * 
     * @param order          订单DTO
     * @param authentication 当前用户认证信息
     */
    private void verifyOrderOwnership(OrderDTO order, Authentication authentication) {
        String currentUsername = authentication.getName();

        // 获取当前用户的ID（需要从数据库查询或从token中解析）
        // 这里我们通过比较hostName来验证，更安全的方式是比较hostId
        // 但由于Controller层没有直接访问UserService，我们使用hostName进行验证
        // 注意：生产环境应该使用hostId进行验证

        // 从authentication中获取用户详细信息
        Long currentUserId = null;
        try {
            // 尝试从principal中获取用户ID
            if (authentication.getPrincipal() instanceof com.homestay3.homestaybackend.entity.User) {
                com.homestay3.homestaybackend.entity.User user = (com.homestay3.homestaybackend.entity.User) authentication
                        .getPrincipal();
                currentUserId = user.getId();
            }
        } catch (Exception e) {
            log.warn("无法从authentication获取用户ID: {}", e.getMessage());
        }

        // 验证：如果hostId不为null，比较hostId；否则回退到hostName比较
        if (order.getHostId() != null && currentUserId != null) {
            if (!order.getHostId().equals(currentUserId)) {
                log.warn("用户 {} (ID: {}) 尝试操作不属于自己的订单 {} (房东ID: {})",
                        currentUsername, currentUserId, order.getId(), order.getHostId());
                throw new AccessDeniedException("您无权操作此订单，该订单不属于您的房源");
            }
        } else if (order.getHostName() != null && !order.getHostName().equals(currentUsername)) {
            // 回退方案：比较hostName
            log.warn("用户 {} 尝试操作不属于自己的订单 {} (房东: {})",
                    currentUsername, order.getId(), order.getHostName());
            throw new AccessDeniedException("您无权操作此订单，该订单不属于您的房源");
        }

        log.debug("订单所有权验证通过 - 用户: {}, 订单ID: {}, 房东ID: {}",
                currentUsername, order.getId(), order.getHostId());
    }

    /**
     * 确认订单
     */
    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('HOST', 'LANDLORD')")
    public ResponseEntity<?> confirmOrder(@PathVariable Long id, Authentication authentication) {
        try {
            log.debug("开始确认订单，订单ID: {}", id);
            String username = authentication.getName();
            log.debug("当前用户: {}, 角色: {}", username, authentication.getAuthorities());

            // 先获取订单信息
            OrderDTO order = orderService.getOrderById(id);

            // 验证当前用户是否为该订单的房东
            verifyOrderOwnership(order, authentication);

            OrderDTO confirmedOrder = orderService.updateOrderStatus(id, OrderStatus.CONFIRMED.name());
            log.debug("订单确认成功，订单ID: {}", id);
            return ResponseEntity.ok(confirmedOrder);
        } catch (ResourceNotFoundException e) {
            log.error("订单确认失败，订单不存在: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (AccessDeniedException e) {
            log.error("订单确认失败，权限不足: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.error("订单确认失败，参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("订单确认失败，未知错误: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "确认订单时发生错误: " + e.getMessage()));
        }
    }

    /**
     * 拒绝订单
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('HOST', 'LANDLORD')")
    public ResponseEntity<?> rejectOrder(
            @PathVariable Long id,
            @RequestBody Map<String, String> reasonMap,
            Authentication authentication) {
        try {
            String reason = reasonMap.get("reason");
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "拒绝原因不能为空"));
            }

            // 先获取订单信息
            OrderDTO order = orderService.getOrderById(id);

            // 验证当前用户是否为该订单的房东
            verifyOrderOwnership(order, authentication);

            OrderDTO rejectedOrder = orderService.rejectOrder(id, reason);
            return ResponseEntity.ok(rejectedOrder);
        } catch (AccessDeniedException e) {
            log.warn("拒绝订单权限检查失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取房东订单统计信息
     */
    @GetMapping("/host/stats")
    @PreAuthorize("hasAnyRole('HOST', 'LANDLORD')")
    public ResponseEntity<?> getHostOrderStats(Authentication authentication) {
        try {
            String username = authentication.getName();

            // 一次性分组查询所有状态数量（替代 N+1）
            List<Object[]> grouped = orderRepository.countOrdersByOwnerUsernameGroupByStatus(username);
            Map<String, Long> statusCounts = new HashMap<>();
            long totalCount = 0;
            for (Object[] row : grouped) {
                String status = (String) row[0];
                Long count = (Long) row[1];
                statusCounts.put(status, count);
                totalCount += count;
            }

            // 汇总各类取消状态
            long cancelledCount = statusCounts.getOrDefault(OrderStatus.CANCELLED.name(), 0L)
                    + statusCounts.getOrDefault(OrderStatus.CANCELLED_BY_USER.name(), 0L)
                    + statusCounts.getOrDefault(OrderStatus.CANCELLED_BY_HOST.name(), 0L)
                    + statusCounts.getOrDefault(OrderStatus.CANCELLED_SYSTEM.name(), 0L);

            Map<String, Long> stats = new HashMap<>();
            stats.put("total", totalCount);
            stats.put("pending", statusCounts.getOrDefault(OrderStatus.PENDING.name(), 0L));
            stats.put("confirmed", statusCounts.getOrDefault(OrderStatus.CONFIRMED.name(), 0L));
            stats.put("checkedIn", statusCounts.getOrDefault(OrderStatus.CHECKED_IN.name(), 0L));
            stats.put("paid", statusCounts.getOrDefault(OrderStatus.PAID.name(), 0L));
            stats.put("completed", statusCounts.getOrDefault(OrderStatus.COMPLETED.name(), 0L));
            stats.put("cancelled", cancelledCount);
            stats.put("rejected", statusCounts.getOrDefault(OrderStatus.REJECTED.name(), 0L));

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // check-in 方法已移至 CheckInController.performCheckIn()

    /**
     * 用户申请退款
     */
    @PostMapping("/{id}/refund")
    public ResponseEntity<?> requestRefund(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> requestBody) {
        log.info("用户申请退款，订单ID: {}", id);

        try {
            String reason = null;
            if (requestBody != null) {
                reason = requestBody.get("reason");
            }

            OrderDTO orderDTO = orderService.requestUserRefund(id, reason);
            return ResponseEntity.ok(orderDTO);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("申请退款失败，订单ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "申请退款失败，请稍后重试"));
        }
    }

    /**
     * 获取订单退款详情
     */
    @GetMapping("/{id}/refund-details")
    public ResponseEntity<?> getRefundDetails(@PathVariable Long id) {
        log.info("获取订单退款详情，订单ID: {}", id);

        try {
            OrderDTO orderDTO = orderService.getOrderById(id);
            return ResponseEntity.ok(orderDTO);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 房东审批（同意）用户发起的退款
     */
    @PostMapping("/{id}/refund/approve")
    @PreAuthorize("hasAnyRole('HOST', 'LANDLORD')")
    public ResponseEntity<?> approveRefund(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> requestBody,
            Authentication authentication) {
        log.info("房东尝试同意退款，订单ID: {}", id);
        try {
            String refundNote = requestBody != null ? requestBody.getOrDefault("refundNote", "") : "";

            // 先获取订单信息
            OrderDTO order = orderService.getOrderById(id);
            // 验证当前用户是否为该订单的房东
            verifyOrderOwnership(order, authentication);

            // 权限检查：房东只能审批用户申请的退款
            if (order.getRefundType() != null && !order.getRefundType().equals("USER_REQUESTED")) {
                log.warn("房东 {} 尝试审批非用户申请的退款，订单ID: {}, 类型: {}",
                        authentication.getName(), id, order.getRefundType());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "只有用户申请的退款才能由房东审批，其他类型的退款请联系管理员处理"));
            }

            OrderDTO updatedOrder = orderService.approveRefund(id, refundNote);
            log.info("房东同意退款成功，订单ID: {}", id);
            return ResponseEntity.ok(updatedOrder);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (AccessDeniedException e) {
            log.warn("同意退款权限检查失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("房东同意退款失败，订单ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "房东同意退款失败: " + e.getMessage()));
        }
    }

    /**
     * 房东拒绝用户发起的退款
     */
    @PostMapping("/{id}/refund/reject")
    @PreAuthorize("hasAnyRole('HOST', 'LANDLORD')")
    public ResponseEntity<?> rejectRefund(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody,
            Authentication authentication) {
        log.info("房东尝试拒绝退款，订单ID: {}", id);
        try {
            String rejectReason = requestBody.getOrDefault("rejectReason", "");
            if (rejectReason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "拒绝退款原因不能为空"));
            }

            // 先获取订单信息
            OrderDTO order = orderService.getOrderById(id);
            // 验证当前用户是否为该订单的房东
            verifyOrderOwnership(order, authentication);

            // 权限检查：房东只能拒绝用户申请的退款
            if (order.getRefundType() != null && !order.getRefundType().equals("USER_REQUESTED")) {
                log.warn("房东 {} 尝试拒绝非用户申请的退款，订单ID: {}, 类型: {}",
                        authentication.getName(), id, order.getRefundType());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "只有用户申请的退款才能由房东审批，其他类型的退款请联系管理员处理"));
            }

            OrderDTO updatedOrder = orderService.rejectRefund(id, rejectReason);
            log.info("房东拒绝退款成功，订单ID: {}", id);
            return ResponseEntity.ok(updatedOrder);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (AccessDeniedException e) {
            log.warn("拒绝退款权限检查失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("房东拒绝退款失败，订单ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "房东拒绝退款失败: " + e.getMessage()));
        }
    }

    /**
     * 房东对退款有异议，发起争议
     */
    @PostMapping("/{id}/dispute")
    @PreAuthorize("hasAnyRole('HOST', 'LANDLORD')")
    public ResponseEntity<?> raiseDispute(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody,
            Authentication authentication) {
        log.info("房东发起争议，订单ID: {}", id);
        try {
            String reason = requestBody.getOrDefault("reason", "");
            if (reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "请输入争议原因"));
            }

            // 先获取订单信息
            OrderDTO order = orderService.getOrderById(id);
            // 验证当前用户是否为该订单的房东
            verifyOrderOwnership(order, authentication);

            OrderDTO updatedOrder = disputeService.raiseDispute(id, reason);
            log.info("房东发起争议成功，订单ID: {}", id);
            return ResponseEntity.ok(updatedOrder);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (AccessDeniedException e) {
            log.warn("发起争议权限检查失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("房东发起争议失败，订单ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "发起争议失败: " + e.getMessage()));
        }
    }

    /**
     * 获取退款预览信息（申请退款前查看预计退款金额）
     */
    @GetMapping("/{id}/refund-preview")
    public ResponseEntity<?> getRefundPreview(
            @PathVariable Long id,
            Authentication authentication) {
        log.info("查询退款预览，订单ID: {}", id);
        try {
            Map<String, Object> preview = orderService.getRefundPreview(id);
            return ResponseEntity.ok(preview);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("获取退款预览失败，订单ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取退款预览失败: " + e.getMessage()));
        }
    }

    /**
     * 检查日期可用性（调试用）
     */
    @PostMapping("/check-availability")
    public ResponseEntity<?> checkDateAvailability(@RequestBody Map<String, Object> request) {
        try {
            Long homestayId = Long.valueOf(request.get("homestayId").toString());
            String checkInDate = request.get("checkInDate").toString();
            String checkOutDate = request.get("checkOutDate").toString();

            log.info("检查房源 {} 在 {} 至 {} 的可用性", homestayId, checkInDate, checkOutDate);

            // 查询该房源的所有订单
            List<Order> allOrders = orderRepository.findByHomestayIdAndStatusInOrderByCheckInDate(
                    homestayId,
                    List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PAID, OrderStatus.CHECKED_IN));

            log.info("房源 {} 共有 {} 个有效订单", homestayId, allOrders.size());

            Map<String, Object> result = new HashMap<>();
            result.put("homestayId", homestayId);
            result.put("checkInDate", checkInDate);
            result.put("checkOutDate", checkOutDate);
            result.put("totalOrders", allOrders.size());

            List<Map<String, Object>> orderDetails = allOrders.stream()
                    .map(order -> {
                        Map<String, Object> detail = new HashMap<>();
                        detail.put("orderNumber", order.getOrderNumber());
                        detail.put("status", order.getStatus());
                        detail.put("checkInDate", order.getCheckInDate());
                        detail.put("checkOutDate", order.getCheckOutDate());
                        detail.put("guestCount", order.getGuestCount());
                        return detail;
                    })
                    .collect(java.util.stream.Collectors.toList());

            result.put("existingOrders", orderDetails);

            // 检查是否有冲突
            LocalDate requestCheckIn = LocalDate.parse(checkInDate);
            LocalDate requestCheckOut = LocalDate.parse(checkOutDate);

            List<Order> conflictOrders = allOrders.stream()
                    .filter(o -> !(o.getCheckOutDate().isBefore(requestCheckIn) ||
                            o.getCheckInDate().isAfter(requestCheckOut)))
                    .collect(java.util.stream.Collectors.toList());

            result.put("hasConflict", !conflictOrders.isEmpty());
            result.put("conflictOrders", conflictOrders.stream()
                    .map(o -> String.format("%s[%s] (%s至%s)",
                            o.getOrderNumber(), o.getStatus(), o.getCheckInDate(), o.getCheckOutDate()))
                    .collect(java.util.stream.Collectors.toList()));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("检查日期可用性失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Helper method to convert Order to OrderDTO
    private OrderDTO convertToDTO(Order order) {
        return OrderDTO.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .homestayId(order.getHomestay().getId())
                .homestayTitle(order.getHomestay().getTitle())
                .guestId(order.getGuest().getId())
                .guestName(order.getGuest().getUsername())
                .guestPhone(order.getGuestPhone())
                .checkInDate(order.getCheckInDate())
                .checkOutDate(order.getCheckOutDate())
                .nights(order.getNights())
                .guestCount(order.getGuestCount())
                .price(order.getPrice())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .remark(order.getRemark())
                .imageUrl(order.getHomestay() != null ? order.getHomestay().getCoverImage() : null)
                .createTime(order.getCreatedAt())
                .updateTime(order.getUpdatedAt())
                .build();
    }
}
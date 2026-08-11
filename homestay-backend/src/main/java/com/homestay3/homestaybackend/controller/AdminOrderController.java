package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.annotation.OperationLog;
import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.service.DisputeService;
import com.homestay3.homestaybackend.service.OrderService;
import com.homestay3.homestaybackend.service.PaymentProcessingService;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.util.SortUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理后台-订单", description = "管理员订单查询、退款审批、纠纷处理、导出接口")
public class AdminOrderController {

    private static final Logger logger = LoggerFactory.getLogger(AdminOrderController.class);
    private final OrderService orderService;
    private final PaymentProcessingService paymentProcessingService;
    private final DisputeService disputeService;

    /**
     * 获取订单列表，支持分页和更丰富的筛选
     */
    @GetMapping
    @Operation(summary = "订单列表", description = "分页查询订单，支持状态、支付、房东等多条件筛选")
    public ResponseEntity<?> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) String guestName,
            @RequestParam(required = false) String homestayTitle,
            @RequestParam(required = false) String status, // 订单状态
            @RequestParam(required = false) String paymentStatus, // 支付状态
            @RequestParam(required = false) String paymentMethod, // 支付方式
            @RequestParam(required = false) String hostName, // 房东名称
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkInDateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkInDateEnd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createTimeStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createTimeEnd,
            @RequestParam(defaultValue = "createdAt,desc") String sort // 排序字段和方向
    ) {
        logger.info("管理员获取订单列表，页码: {}, 每页数量: {}, 排序: {}", page, size, sort);
        logger.info("筛选条件 - orderNumber: {}, guestName: {}, homestayTitle: {}, status: {}, paymentStatus: {}, paymentMethod: {}, hostName: {}, checkInDateStart: {}, checkInDateEnd: {}, createTimeStart: {}, createTimeEnd: {}",
                orderNumber, guestName, homestayTitle, status, paymentStatus, paymentMethod, hostName, checkInDateStart, checkInDateEnd, createTimeStart, createTimeEnd);
        
        // 使用 SortUtils 解析多字段排序，支持字段映射和白名单校验
        Map<String, String> orderFieldMapping = Map.of(
                "hostName", "homestay.owner.username",
                "guestName", "guest.username",
                "createTime", "createdAt",
                "checkInDate", "checkInDate",
                "totalAmount", "totalAmount",
                "orderNumber", "orderNumber",
                "status", "status"
        );
        Set<String> orderAllowedFields = Set.of(
                "id", "createdAt", "updatedAt", "totalAmount", "status",
                "orderNumber", "checkInDate", "checkOutDate",
                "homestay", "guest"
        );
        Pageable pageable = SortUtils.buildPageable(page, size, sort, orderFieldMapping, orderAllowedFields);
        
        // 调用更新后的Service方法
        Page<OrderDTO> orders = orderService.getAdminOrders(
                pageable,
                orderNumber, 
                guestName,
                homestayTitle,
                status, 
                paymentStatus,
                paymentMethod,
                hostName, 
                checkInDateStart, 
                checkInDateEnd,
                createTimeStart, 
                createTimeEnd
        );
        
        // 返回格式与之前保持一致
        Map<String, Object> response = new HashMap<>();
        response.put("content", orders.getContent());
        response.put("totalElements", orders.getTotalElements());
        response.put("totalPages", orders.getTotalPages());
        response.put("page", orders.getNumber());
        response.put("size", orders.getSize());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取异常订单统计（用于管理后台仪表盘）
     */
    @GetMapping("/exception-stats")
    public ResponseEntity<?> getExceptionOrderStats() {
        logger.info("获取异常订单统计数据");
        Map<String, Long> stats = orderService.getExceptionOrderStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取订单详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrderDetail(@PathVariable Long id) {
        logger.info("管理员获取订单详情，ID: {}", id);
        OrderDTO order = orderService.getOrderById(id);
        return ResponseEntity.ok(order);
    }

    /**
     * 更新订单状态
     */
    @PutMapping("/{id}/status")
    @OperationLog(operationType = "UPDATE", resource = "ORDER", resourceId = "#id", detail = "更新订单状态")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> statusData
    ) {
        String status = (String) statusData.get("status");
        logger.info("管理员更新订单状态，ID: {}, 状态: {}", id, status);
        OrderDTO updatedOrder = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(updatedOrder);
    }

    /**
     * 管理员手动确认订单支付
     */
    @PutMapping("/{id}/confirm-payment")
    @OperationLog(operationType = "UPDATE", resource = "ORDER", resourceId = "#id", detail = "手动确认支付")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> confirmOrderPayment(@PathVariable Long id) {
        logger.info("管理员手动确认订单支付，ID: {}", id);
        try {
            OrderDTO updatedOrder = paymentProcessingService.confirmPayment(id);
            return ResponseEntity.ok(updatedOrder);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("手动确认支付失败，订单ID: {}", id, e);
            return ResponseEntity.status(500).body(Map.of("error", "确认支付失败: " + e.getMessage()));
        }
    }

    /**
     * 管理员直接执行退款（ADMIN_INITIATED类型，不需要审批）
     */
    @PostMapping("/{id}/refund")
    @OperationLog(operationType = "UPDATE", resource = "ORDER", resourceId = "#id", detail = "直接执行退款")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> executeRefund(@PathVariable Long id, @RequestBody Map<String, String> request) {
        logger.info("管理员直接执行退款，订单ID: {}", id);
        try {
            String reason = request.getOrDefault("reason", "管理员直接退款");
            OrderDTO updatedOrder = paymentProcessingService.executeRefund(id, reason);
            return ResponseEntity.ok(updatedOrder);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("执行退款失败，订单ID: {}", id, e);
            return ResponseEntity.status(500).body(Map.of("error", "执行退款失败: " + e.getMessage()));
        }
    }

    /**
     * 管理员审批退款申请（仅限HOST_CANCELLED类型）
     */
    @PostMapping("/{id}/refund/approve")
    @OperationLog(operationType = "UPDATE", resource = "ORDER", resourceId = "#id", detail = "审批退款")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approveRefund(@PathVariable Long id, @RequestBody Map<String, String> request) {
        logger.info("管理员审批退款申请，订单ID: {}", id);
        try {
            String refundNote = request.getOrDefault("refundNote", "");

            // 先获取订单信息检查退款类型
            OrderDTO order = orderService.getOrderById(id);
            // 管理员只能审批房东取消的退款（HOST_CANCELLED类型）
            if (order.getRefundType() != null && !order.getRefundType().equals("HOST_CANCELLED")) {
                logger.warn("管理员尝试审批非房东取消的退款，订单ID: {}, 类型: {}", id, order.getRefundType());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "管理员只能审批房东取消的退款申请，其他类型的退款请使用直接执行接口"));
            }

            OrderDTO updatedOrder = paymentProcessingService.approveRefund(id, refundNote);
            return ResponseEntity.ok(updatedOrder);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("审批退款失败，订单ID: {}", id, e);
            return ResponseEntity.status(500).body(Map.of("error", "审批退款失败: " + e.getMessage()));
        }
    }

    /**
     * 管理员拒绝退款申请
     */
    @PostMapping("/{id}/refund/reject")
    @OperationLog(operationType = "UPDATE", resource = "ORDER", resourceId = "#id", detail = "拒绝退款")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectRefund(@PathVariable Long id, @RequestBody Map<String, String> request) {
        logger.info("管理员拒绝退款申请，订单ID: {}", id);
        try {
            String rejectReason = request.getOrDefault("rejectReason", "");
            OrderDTO updatedOrder = paymentProcessingService.rejectRefund(id, rejectReason);
            return ResponseEntity.ok(updatedOrder);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("拒绝退款失败，订单ID: {}", id, e);
            return ResponseEntity.status(500).body(Map.of("error", "拒绝退款失败: " + e.getMessage()));
        }
    }

    /**
     * 发起争议（当房东对退款有异议时）
     */
    @PostMapping("/{id}/dispute")
    @OperationLog(operationType = "CREATE", resource = "ORDER", resourceId = "#id", detail = "发起争议")
    @PreAuthorize("hasRole('ADMIN') or hasRole('HOST')")
    public ResponseEntity<?> raiseDispute(@PathVariable Long id, @RequestBody Map<String, String> request) {
        logger.info("发起争议，订单ID: {}", id);
        try {
            String reason = request.getOrDefault("reason", "");
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("error", "请输入争议原因"));
            }
            OrderDTO updatedOrder = disputeService.raiseDispute(id, reason);
            return ResponseEntity.ok(updatedOrder);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("发起争议失败，订单ID: {}", id, e);
            return ResponseEntity.status(500).body(Map.of("error", "发起争议失败: " + e.getMessage()));
        }
    }

    /**
     * 解决争议（管理员仲裁）
     */
    @PostMapping("/{id}/dispute/resolve")
    @OperationLog(operationType = "UPDATE", resource = "ORDER", resourceId = "#id", detail = "解决争议")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resolveDispute(@PathVariable Long id, @RequestBody Map<String, String> request) {
        logger.info("解决争议，订单ID: {}", id);
        try {
            String resolution = request.getOrDefault("resolution", "");
            String note = request.getOrDefault("note", "");

            if (resolution == null || resolution.trim().isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("error", "请选择仲裁结果"));
            }
            if (!resolution.equals("APPROVED") && !resolution.equals("REJECTED")) {
                return ResponseEntity.status(400).body(Map.of("error", "仲裁结果必须是 APPROVED 或 REJECTED"));
            }

            OrderDTO updatedOrder = disputeService.resolveDispute(id, resolution, note);
            return ResponseEntity.ok(updatedOrder);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("解决争议失败，订单ID: {}", id, e);
            return ResponseEntity.status(500).body(Map.of("error", "解决争议失败: " + e.getMessage()));
        }
    }

    /**
     * 删除订单
     */
    @DeleteMapping("/{id}")
    @OperationLog(operationType = "DELETE", resource = "ORDER", resourceId = "#id")
    public ResponseEntity<?> deleteOrder(@PathVariable Long id) {
        logger.info("管理员删除订单，ID: {}", id);
        orderService.deleteOrder(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 导出订单
     */
    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> exportOrders(
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        logger.info("管理员导出订单，订单号: {}, 状态: {}, 开始日期: {}, 结束日期: {}", 
                orderNumber, status, startDate, endDate);
        
        Map<String, String> params = new HashMap<>();
        if (orderNumber != null) params.put("orderNumber", orderNumber);
        if (status != null) params.put("status", status);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);
        
        byte[] data = orderService.exportOrders(params);
        String filename = "orders_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(new ByteArrayResource(data));
    }
} 
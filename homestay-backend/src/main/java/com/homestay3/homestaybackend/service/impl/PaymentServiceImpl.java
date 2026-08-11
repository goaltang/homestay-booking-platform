package com.homestay3.homestaybackend.service.impl;

import com.alibaba.fastjson2.JSON;
import com.homestay3.homestaybackend.annotation.RedisLock;
import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.dto.payment.PaymentNotifyResult;
import com.homestay3.homestaybackend.dto.payment.PaymentRequest;
import com.homestay3.homestaybackend.dto.payment.PaymentResponse;
import com.homestay3.homestaybackend.dto.payment.PaymentStatusResponse;
import com.homestay3.homestaybackend.dto.refund.RefundRequest;
import com.homestay3.homestaybackend.dto.refund.RefundResponse;
import com.homestay3.homestaybackend.entity.RefundRecord;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.PaymentRecord;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.PaymentStatus;
import com.homestay3.homestaybackend.model.RefundStatus;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.PaymentRecordRepository;
import com.homestay3.homestaybackend.repository.RefundRecordRepository;
import com.homestay3.homestaybackend.service.OrderService;
import com.homestay3.homestaybackend.service.PaymentService;
import com.homestay3.homestaybackend.service.gateway.AlipayGateway;
import com.homestay3.homestaybackend.service.gateway.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final RefundRecordRepository refundRecordRepository;

    @Autowired
    @Lazy
    private OrderService orderService;

    @Autowired
    @Lazy
    private com.homestay3.homestaybackend.service.PaymentProcessingService paymentProcessingService;

    private final AlipayGateway alipayGateway;
    private final OrderStatusUpdater orderStatusUpdater;

    @Override
    @Transactional
    public String generatePaymentQRCode(Long orderId, String method) {
        try {
            log.info("开始生成支付，订单ID: {}, 支付方式: {}", orderId, method);

            // 查询订单
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

            // 检查订单状态
            if (OrderStatus.COMPLETED.name().equals(order.getStatus()) ||
                    OrderStatus.CANCELLED.name().equals(order.getStatus())) {
                throw new IllegalArgumentException("订单状态不正确，当前状态：" + order.getStatus());
            }

            // 检查是否已有待支付的支付记录，防止重复创建
            java.util.Optional<PaymentRecord> existingPendingRecord = paymentRecordRepository
                    .findTopByOrderIdAndStatusOrderByCreatedAtDesc(orderId, "PENDING");
            if (existingPendingRecord.isPresent()) {
                PaymentRecord existingRecord = existingPendingRecord.get();
                log.info("订单 {} 已有待支付的支付记录，商户订单号: {}，创建时间: {}",
                        orderId, existingRecord.getOutTradeNo(), existingRecord.getCreatedAt());

                // 检查支付记录是否已过期（支付宝二维码有效期为10分钟）
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime createdAt = existingRecord.getCreatedAt();
                boolean isExpired = createdAt != null &&
                        java.time.Duration.between(createdAt, now).toMinutes() >= 10;

                if (isExpired) {
                    log.info("订单 {} 的待支付记录已超过10分钟，标记为失败并允许创建新支付", orderId);
                    // 将过期记录标记为失败
                    existingRecord.setStatus("EXPIRED");
                    paymentRecordRepository.save(existingRecord);
                    // 不再直接返回，而是继续执行创建新支付的逻辑
                } else {
                    // 返回已有的支付信息，让用户继续之前的支付
                    // 从 responseParams 中解析之前的支付响应
                    String responseParams = existingRecord.getResponseParams();
                    if (responseParams != null && !responseParams.isEmpty()) {
                        try {
                            PaymentResponse existingResponse = JSON.parseObject(responseParams, PaymentResponse.class);
                            String paymentData = existingResponse.getQrCode() != null ?
                                    existingResponse.getQrCode() : existingResponse.getPaymentUrl();
                            return paymentData; // 返回已有的支付二维码或链接
                        } catch (Exception e) {
                            log.warn("解析已有支付记录响应失败，将抛出异常提示用户", e);
                        }
                    }

                    // 如果解析失败，抛出异常提示用户
                    throw new IllegalStateException("订单已有待支付的支付记录，请完成当前支付或等待超时后再试");
                }
            }

            // 生成商户订单号
            String outTradeNo = generateOutTradeNo(orderId);

            // 创建支付请求
            // 获取房源标题（防止懒加载 NPE）
            String homestayTitle = order.getHomestay() != null ? order.getHomestay().getTitle() : "民宿";

            PaymentRequest request = PaymentRequest.builder()
                    .outTradeNo(outTradeNo)
                    .amount(order.getTotalAmount())
                    .subject("民宿预订-" + homestayTitle)
                    .body("订单号：" + order.getOrderNumber())
                    .orderId(orderId)
                    .build();

            // 创建支付
            PaymentResponse response;

            // 使用扫码支付，生成二维码供用户扫码
            log.info("使用支付宝扫码支付，订单ID: {}", orderId);
            response = alipayGateway.createQRCodePayment(request);

            if (response.isSuccess()) {
                // 保存支付记录
                savePaymentRecord(orderId, method, outTradeNo, request, response);

                // 更新订单状态为待支付
                orderStatusUpdater.markPaymentPending(order);
                orderRepository.save(order);

                // 返回二维码或页面跳转URL
                String paymentData = null;
                paymentData = response.getQrCode() != null ? response.getQrCode() : response.getPaymentUrl();
                log.info("支付宝支付生成成功，订单ID: {}, 二维码: {}, 支付URL: {}",
                        orderId, response.getQrCode() != null ? "已生成" : "无",
                        response.getPaymentUrl() != null ? "已生成" : "无");

                return paymentData;
            } else {
                log.error("生成支付失败，订单ID: {}, 错误: {}", orderId, response.getMessage());
                throw new RuntimeException("生成支付失败: " + response.getMessage());
            }

        } catch (RuntimeException e) {
            // 如果已经是带有明确错误信息的 RuntimeException，直接抛出
            throw e;
        } catch (Exception e) {
            log.error("生成支付异常，订单ID: " + orderId, e);
            throw new RuntimeException("生成支付失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public boolean checkPaymentStatus(Long orderId) {
        try {
            log.info("检查支付状态，订单ID: {}", orderId);

            // 查询最新的支付记录
            PaymentRecord paymentRecord = paymentRecordRepository
                    .findTopByOrderIdOrderByCreatedAtDesc(orderId)
                    .orElse(null);

            if (paymentRecord == null) {
                log.info("未找到支付记录，订单ID: {}", orderId);
                return false;
            }

            // 如果支付记录已经是成功状态，直接返回
            if ("SUCCESS".equals(paymentRecord.getStatus())) {
                log.info("支付记录已是成功状态，订单ID: {}", orderId);
                return true;
            }

            // 选择支付网关查询状态
            PaymentGateway gateway = selectPaymentGateway(paymentRecord.getPaymentMethod());
            PaymentStatusResponse response = gateway.queryPayment(paymentRecord.getOutTradeNo());

            if (response.isSuccess() && response.getStatus() == PaymentStatus.PAID) {
                // 更新支付记录
                paymentRecord.setStatus("SUCCESS");
                paymentRecord.setTransactionId(response.getTransactionId());
                paymentRecordRepository.save(paymentRecord);

                // 更新订单状态
                updateOrderPaymentStatus(orderId, PaymentStatus.PAID);

                // 统一支付成功后置处理
                paymentProcessingService.handleOrderPaidSuccess(orderId);

                log.info("支付状态查询成功，订单已支付，订单ID: {}", orderId);
                return true;
            }

            log.info("支付状态查询完成，订单未支付，订单ID: {}", orderId);
            return false;

        } catch (Exception e) {
            log.error("检查支付状态异常，订单ID: " + orderId, e);
            return false;
        }
    }

    @Override
    @Transactional
    public OrderDTO mockSuccessPayment(Long orderId) {
        log.info("模拟支付成功，订单ID: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

        // 确保订单未支付
        if (OrderStatus.PAID.name().equals(order.getStatus())) {
            throw new IllegalArgumentException("订单已支付，无需重复支付");
        }

        if (OrderStatus.COMPLETED.name().equals(order.getStatus()) ||
                OrderStatus.CANCELLED.name().equals(order.getStatus())) {
            throw new IllegalArgumentException("订单状态不正确，无法支付");
        }

        // 更新订单状态为已支付（updateOrderStatus 内部会触发统一支付成功后置处理）
        return orderService.updateOrderStatus(orderId, OrderStatus.PAID.name());
    }

    /**
     * 处理支付回调通知
     * 使用 Redis 分布式锁 + 乐观锁 双重防护，确保回调幂等性
     */
    @Transactional
    @RedisLock(key = "'payment:notify:' + #result.outTradeNo")
    public void handlePaymentNotify(PaymentNotifyResult result) {
        try {
            log.debug("开始处理支付回调，商户订单号: {}", result.getOutTradeNo());

            // 查找支付记录
            PaymentRecord paymentRecord = paymentRecordRepository
                    .findByOutTradeNo(result.getOutTradeNo())
                    .orElseThrow(() -> new RuntimeException("支付记录不存在"));

            // 第一层防护：状态检查
            if ("SUCCESS".equals(paymentRecord.getStatus())) {
                log.debug("支付记录已处理，跳过: {}", result.getOutTradeNo());
                return;
            }

            // 更新支付记录（乐观锁在 save 时生效，并发时只有一个线程成功）
            paymentRecord.setStatus("SUCCESS");
            paymentRecord.setTransactionId(result.getTransactionId());
            paymentRecord.setNotifyParams(JSON.toJSONString(result.getParams()));
            paymentRecordRepository.save(paymentRecord);

            // 更新订单状态
            updateOrderPaymentStatus(paymentRecord.getOrderId(), PaymentStatus.PAID);

            // 统一支付成功后置处理
            paymentProcessingService.handleOrderPaidSuccess(paymentRecord.getOrderId());

            log.debug("支付回调处理成功: orderId={}, outTradeNo={}",
                    paymentRecord.getOrderId(), result.getOutTradeNo());

        } catch (ObjectOptimisticLockingFailureException e) {
            // 乐观锁冲突：另一个线程已成功处理该回调，安全跳过
            log.info("支付回调已被其他线程处理（乐观锁冲突），跳过: {}", result.getOutTradeNo());
        } catch (Exception e) {
            log.error("处理支付回调异常", e);
            throw new RuntimeException("处理支付回调失败", e);
        }
    }

    /**
     * 选择支付网关
     */
    private PaymentGateway selectPaymentGateway(String method) {
        if ("alipay".equalsIgnoreCase(method)) {
            return alipayGateway;
        }
        throw new IllegalArgumentException("不支持的支付方式: " + method);
    }

    /**
     * 生成商户订单号
     */
    private String generateOutTradeNo(Long orderId) {
        return "HST" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                String.format("%06d", orderId) + String.format("%03d", (int) (Math.random() * 1000));
    }

    /**
     * 保存支付记录
     */
    private void savePaymentRecord(Long orderId, String method, String outTradeNo,
            PaymentRequest request, PaymentResponse response) {
        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setOrderId(orderId);
        paymentRecord.setPaymentMethod(method.toUpperCase());
        paymentRecord.setOutTradeNo(outTradeNo);
        paymentRecord.setAmount(request.getAmount());
        paymentRecord.setStatus("PENDING");
        paymentRecord.setRequestParams(JSON.toJSONString(request));
        paymentRecord.setResponseParams(JSON.toJSONString(response));

        paymentRecordRepository.save(paymentRecord);
        log.info("支付记录保存成功，订单ID: {}, 商户订单号: {}", orderId, outTradeNo);
    }

    /**
     * 更新订单支付状态
     */
    private void updateOrderPaymentStatus(Long orderId, PaymentStatus paymentStatus) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

            orderStatusUpdater.markPaid(order);
            orderRepository.save(order);

            log.info("订单支付状态更新成功，订单ID: {}, 支付状态: {}", orderId, paymentStatus);
        } catch (Exception e) {
            log.error("更新订单支付状态失败，订单ID: " + orderId, e);
            throw new RuntimeException("更新订单支付状态失败", e);
        }
    }

    @Override
    @Transactional
    public RefundResponse processRefund(RefundRequest request) {
        log.info("开始处理退款，订单ID: {}, 退款金额: {}", request.getOrderId(), request.getRefundAmount());

        try {
            Order order = orderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));

            if (order.getPaymentStatus() != PaymentStatus.PAID
                    && order.getPaymentStatus() != PaymentStatus.REFUND_PENDING) {
                throw new IllegalStateException("订单支付状态不支持退款");
            }

            // 校验退款金额不超过订单总金额
            if (request.getRefundAmount() != null
                    && request.getRefundAmount().compareTo(order.getTotalAmount()) > 0) {
                throw new IllegalArgumentException(
                        "退款金额(" + request.getRefundAmount() + ")不能超过订单总金额(" + order.getTotalAmount() + ")");
            }

            // 优先查找成功的支付记录，用于退款
            PaymentRecord paymentRecord = paymentRecordRepository
                    .findTopByOrderIdAndStatusOrderByCreatedAtDesc(request.getOrderId(), "SUCCESS")
                    .orElseGet(() -> {
                        // 如果没有成功的记录，查找任何支付记录
                        log.warn("订单 {} 没有成功的支付记录，尝试使用其他记录", request.getOrderId());
                        return paymentRecordRepository.findTopByOrderIdOrderByCreatedAtDesc(request.getOrderId())
                                .orElseThrow(() -> new ResourceNotFoundException("支付记录不存在"));
                    });

            request.setOutTradeNo(paymentRecord.getOutTradeNo());

            // 如果请求的支付方式为空或是字符串"null"，从支付记录中获取（作为兼容历史遗留订单的兜底方案）
            String pm = request.getPaymentMethod();
            if (pm == null || pm.trim().isEmpty() || "null".equalsIgnoreCase(pm.trim())) {
                request.setPaymentMethod(paymentRecord.getPaymentMethod());
            }

            PaymentGateway gateway = selectPaymentGateway(request.getPaymentMethod());
            RefundResponse refundResponse = gateway.processRefund(request);

            RefundRecord refundRecord = RefundRecord.builder()
                    .orderId(request.getOrderId())
                    .paymentMethod(request.getPaymentMethod())
                    .outTradeNo(request.getOutTradeNo())
                    .tradeNo(paymentRecord.getTransactionId())
                    .refundAmount(request.getRefundAmount())
                    .refundReason(request.getRefundReason())
                    .refundType(request.getRefundType())
                    .refundStatus(refundResponse.isSuccess() ? RefundStatus.SUCCESS : RefundStatus.FAILED)
                    .requestParams(JSON.toJSONString(request))
                    .responseParams(JSON.toJSONString(refundResponse))
                    .build();

            if (refundResponse.isSuccess()) {
                refundRecord.setRefundTradeNo(refundResponse.getRefundTradeNo());
                refundRecord.setProcessedAt(LocalDateTime.now());
                log.info("退款成功，订单ID: {}, 退款交易号: {}", request.getOrderId(), refundResponse.getRefundTradeNo());
            } else {
                refundRecord.setErrorMessage(refundResponse.getErrorMessage());
                log.error("退款失败，订单ID: {}, 错误: {}", request.getOrderId(), refundResponse.getMessage());
            }

            refundRecordRepository.save(refundRecord);

            return refundResponse;

        } catch (Exception e) {
            log.error("处理退款异常，订单ID: " + request.getOrderId(), e);
            return RefundResponse.builder()
                    .success(false)
                    .message("退款处理失败: " + e.getMessage())
                    .errorCode("REFUND_ERROR")
                    .build();
        }
    }
}
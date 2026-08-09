package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.dto.OrderDTO;

/**
 * 争议处理服务
 * 处理退款争议的发起和仲裁
 */
public interface DisputeService {

    /**
     * 发起争议
     * 当房东对退款有异议时，可以发起争议，将订单标记为争议状态
     * @param orderId 订单ID
     * @param reason 争议原因
     * @return 发起争议后的订单DTO
     */
    OrderDTO raiseDispute(Long orderId, String reason);

    /**
     * 房客发起争议
     * 房客对"退款被拒/押金扣押/入住体验严重不符"发起争议，订单进入 DISPUTE_PENDING，管理员仲裁
     * @param orderId 订单ID
     * @param reason 争议原因
     * @return 发起争议后的订单DTO
     */
    OrderDTO raiseDisputeByGuest(Long orderId, String reason);

    /**
     * 解决争议（仲裁）
     * 管理员对争议进行仲裁，批准或拒绝退款
     * @param orderId 订单ID
     * @param resolution 仲裁结果：APPROVED(批准退款) 或 REJECTED(拒绝退款)
     * @param note 仲裁备注
     * @return 解决争议后的订单DTO
     */
    OrderDTO resolveDispute(Long orderId, String resolution, String note);
}

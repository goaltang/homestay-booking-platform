package com.homestay3.homestaybackend.repository;

import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.homestay3.homestaybackend.model.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

        Optional<Order> findByOrderNumber(String orderNumber);

        Optional<Order> findByIdempotencyKey(String idempotencyKey);

        // 查找指定房东的订单
        @Query("SELECT o FROM Order o JOIN o.homestay h WHERE h.owner = :owner")
        Page<Order> findByOwner(@Param("owner") User owner, Pageable pageable);

        // 查找指定房东的特定状态订单
        @Query("SELECT o FROM Order o JOIN o.homestay h WHERE h.owner = :owner AND o.status = :status")
        Page<Order> findByOwnerAndStatus(@Param("owner") User owner, @Param("status") String status, Pageable pageable);

        // 获取房东待处理订单数量
        @Query("SELECT COUNT(o) FROM Order o JOIN o.homestay h WHERE h.owner = :owner AND o.status = 'PENDING'")
        Long countPendingOrdersByOwner(@Param("owner") User owner);

        // 查找指定用户的订单
        Page<Order> findByGuest(User guest, Pageable pageable);

        // 查找指定用户的特定状态订单
        Page<Order> findByGuestAndStatus(User guest, String status, Pageable pageable);

        // 检查指定房源在日期范围内是否有订单
        // 正确逻辑：新订单[checkIn, checkOut)和已有订单[o.checkIn, o.checkOut)不重叠的条件是
        // 新订单的退房 <= 已有订单的入住(checkIn >= o.checkOut) 或者 新订单的入住 >= 已有订单的退房(endDate <= o.checkIn)
        // 即：存在冲突的条件是 NOT (endDate <= o.checkIn OR checkIn >= o.checkOut)
        // 简化为：checkIn < o.checkOut AND endDate > o.checkIn
        @Query("SELECT COUNT(o) > 0 FROM Order o WHERE o.homestay.id = :homestayId " +
                        "AND o.status IN ('CONFIRMED', 'PAID') " +
                        "AND (:startDate < o.checkOutDate AND :endDate > o.checkInDate)")
        boolean existsOverlappingBooking(@Param("homestayId") Long homestayId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        // 获取用户的所有订单
        @Query("SELECT o FROM Order o WHERE o.guest.id = :guestId")
        Page<Order> findByGuestId(@Param("guestId") Long guestId, Pageable pageable);

        @Query("SELECT COUNT(o) FROM Order o WHERE o.guest.id = :guestId")
        long countByGuestId(@Param("guestId") Long guestId);

        @Query("SELECT COUNT(o) FROM Order o WHERE o.guest.id = :userId AND o.status = :status")
        Long countByGuestIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

        @Query("SELECT COUNT(o) FROM Order o WHERE o.guest.id = :userId AND o.status = 'COMPLETED' AND o.id NOT IN (SELECT r.order.id FROM Review r WHERE r.deleted = false)")
        Long countPendingReviewOrders(@Param("userId") Long userId);

        // 根据状态获取用户的订单
        @Query("SELECT o FROM Order o WHERE o.guest.id = :guestId AND o.status = :status")
        Page<Order> findByGuestIdAndStatus(@Param("guestId") Long guestId, @Param("status") String status,
                        Pageable pageable);

        // 获取特定房源的所有订单
        @Query("SELECT o FROM Order o WHERE o.homestay.id = :homestayId")
        Page<Order> findByHomestayId(@Param("homestayId") Long homestayId, Pageable pageable);

        // 检查日期范围内的可用性
        @Query("SELECT o FROM Order o WHERE o.homestay.id = :homestayId " +
                        "AND o.status != 'CANCELLED' " +
                        "AND ((o.checkInDate <= :checkOutDate AND o.checkOutDate >= :checkInDate) " +
                        "OR (o.checkInDate >= :checkInDate AND o.checkInDate <= :checkOutDate) " +
                        "OR (o.checkOutDate >= :checkInDate AND o.checkOutDate <= :checkOutDate))")
        List<Order> findConflictingOrders(
                        @Param("homestayId") Long homestayId,
                        @Param("checkInDate") LocalDate checkInDate,
                        @Param("checkOutDate") LocalDate checkOutDate);

        // 房东相关方法

        // 获取房东的所有订单
        @Query("SELECT o FROM Order o JOIN o.homestay h WHERE h.owner.id = :hostId")
        Page<Order> findByHostId(@Param("hostId") Long hostId, Pageable pageable);

        // 根据状态获取房东的订单
        @Query("SELECT o FROM Order o JOIN o.homestay h WHERE h.owner.id = :hostId AND o.status = :status")
        Page<Order> findByHostIdAndStatus(@Param("hostId") Long hostId, @Param("status") String status,
                        Pageable pageable);

        // 统计房东的订单总数
        @Query("SELECT COUNT(o) FROM Order o JOIN o.homestay h WHERE h.owner.id = :hostId")
        Long countByHostId(@Param("hostId") Long hostId);

        // 统计房东的特定状态订单数
        @Query("SELECT COUNT(o) FROM Order o JOIN o.homestay h WHERE h.owner.id = :hostId AND o.status = :status")
        Long countByHostIdAndStatus(@Param("hostId") Long hostId, @Param("status") String status);

        // 获取房东的总收入
        @Query("SELECT SUM(o.totalAmount) FROM Order o JOIN o.homestay h WHERE h.owner.id = :hostId AND o.status = 'COMPLETED'")
        Double getTotalEarningsByHostId(@Param("hostId") Long hostId);

        // 根据房东用户名统计订单总数
        @Query("SELECT COUNT(o) FROM Order o JOIN o.homestay h JOIN h.owner u WHERE u.username = :username")
        Long countByHomestayOwnerUsername(@Param("username") String username);

        // 根据房东用户名和订单状态统计订单数
        @Query("SELECT COUNT(o) FROM Order o JOIN o.homestay h JOIN h.owner u WHERE u.username = :username AND o.status = :status")
        Long countByHomestayOwnerUsernameAndStatus(@Param("username") String username, @Param("status") String status);

        /**
         * 按状态分组统计房东订单数量（一次性查询替代 N+1）
         */
        @Query("SELECT o.status, COUNT(o) FROM Order o JOIN o.homestay h JOIN h.owner u WHERE u.username = :username GROUP BY o.status")
        List<Object[]> countOrdersByOwnerUsernameGroupByStatus(@Param("username") String username);

        // 获取该房东的所有订单
        @Query("SELECT o FROM Order o JOIN o.homestay h WHERE h.owner.id = :ownerId")
        List<Order> findByHomestayOwnerId(@Param("ownerId") Long ownerId);

        List<Order> findByGuest_IdOrderByCreatedAtDesc(Long userId);

        Page<Order> findByHomestayIdOrderByCreatedAtDesc(Long homestayId, Pageable pageable);

        Optional<Order> findByIdAndGuest_Id(Long id, Long userId);

        @Query("SELECT COUNT(o) FROM Order o WHERE o.homestay.id = :homestayId")
        Long countByHomestayId(@Param("homestayId") Long homestayId);

        @Query("SELECT COUNT(o) FROM Order o WHERE o.homestay.id = :homestayId AND o.createdAt > :createdAtAfter")
        Long countByHomestayIdAndCreatedAtAfter(@Param("homestayId") Long homestayId,
                        @Param("createdAtAfter") LocalDateTime createdAtAfter);

        Long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

        @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.createdAt BETWEEN :start AND :end AND o.status = :status")
        Optional<Double> sumTotalPriceByCreatedAtBetweenAndStatus(
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end,
                        @Param("status") String status);

        // 查找超时的待确认订单（创建时间早于指定时间）
        List<Order> findByStatusAndCreatedAtBefore(String status, LocalDateTime createdAt);

        // 查找超时的已确认未支付订单（更新时间早于指定时间）
        List<Order> findByStatusAndUpdatedAtBefore(String status, LocalDateTime updatedAt);

        // 查找指定状态和时间范围内的订单（用于预警和即将超时检查）
        List<Order> findByStatusAndCreatedAtBetween(String status, LocalDateTime startTime, LocalDateTime endTime);

        // 查找指定状态和更新时间范围内的订单（用于支付超时预警）
        List<Order> findByStatusAndUpdatedAtBetween(String status, LocalDateTime startTime, LocalDateTime endTime);

        // 新增：统计房东在指定时间后的订单数（用于活跃度计算）
        @Query("SELECT COUNT(o) FROM Order o JOIN o.homestay h WHERE h.owner.id = :hostId AND o.createdAt > :afterDate")
        Long countByHostIdAndCreatedAtAfter(@Param("hostId") Long hostId, @Param("afterDate") LocalDateTime afterDate);

        // 新增：获取房东最近的订单（用于响应时间分析）
        @Query("SELECT o FROM Order o JOIN o.homestay h WHERE h.owner.id = :hostId ORDER BY o.createdAt DESC")
        Page<Order> findRecentOrdersByHostId(@Param("hostId") Long hostId, Pageable pageable);

        // 新增：统计房东处理订单的平均时间（如果有确认时间字段的话）
        // 注意：这需要在Order实体中添加confirmedAt字段
        // @Query("SELECT AVG(TIMESTAMPDIFF(HOUR, o.createdAt, o.confirmedAt)) FROM
        // Order o JOIN o.homestay h WHERE h.owner.id = :hostId AND o.confirmedAt IS NOT
        // NULL")
        // Double getAverageResponseTimeByHostId(@Param("hostId") Long hostId);

        // 新增：统计特定房源在指定日期范围内，特定状态的订单数量
        @Query("SELECT COUNT(o) FROM Order o WHERE o.homestay.id = :homestayId " +
                        "AND o.status IN :statuses " +
                        "AND o.createdAt >= :startDate AND o.createdAt <= :endDate")
        Long countByHomestayIdAndStatusInAndCreatedAtBetween(
                        @Param("homestayId") Long homestayId,
                        @Param("statuses") List<String> statuses,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        // 新增：查询特定房源在指定日期范围内，特定状态的订单列表（用于周末流行度分析）
        @Query("SELECT o FROM Order o WHERE o.homestay.id = :homestayId " +
                        "AND o.status IN :statuses " +
                        "AND o.createdAt >= :startDate AND o.createdAt <= :endDate")
        List<Order> findByHomestayIdAndStatusInAndCreatedAtBetween(
                        @Param("homestayId") Long homestayId,
                        @Param("statuses") List<String> statuses,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        // ====== 新增：订单自动状态流转相关查询方法 ======

        // 根据状态和入住日期查找订单
        List<Order> findByStatusAndCheckInDate(String status, LocalDate checkInDate);

        // 根据状态和退房日期查找订单
        List<Order> findByStatusAndCheckOutDate(String status, LocalDate checkOutDate);

        // 统计特定状态和入住日期的订单数量
        @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.checkInDate = :checkInDate")
        int countByStatusAndCheckInDate(@Param("status") String status, @Param("checkInDate") LocalDate checkInDate);

        // 统计特定状态和退房日期的订单数量
        @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.checkOutDate = :checkOutDate")
        int countByStatusAndCheckOutDate(@Param("status") String status, @Param("checkOutDate") LocalDate checkOutDate);

        // 查找指定日期范围内需要自动处理的订单
        @Query("SELECT o FROM Order o WHERE o.status IN :statuses " +
                        "AND o.checkInDate >= :startDate AND o.checkInDate <= :endDate")
        List<Order> findAutoProcessOrdersByDateRange(
                        @Param("statuses") List<String> statuses,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        // 查询房源的特定状态订单，按入住日期排序
        @Query("SELECT o FROM Order o WHERE o.homestay.id = :homestayId AND o.status IN :statuses ORDER BY o.checkInDate")
        List<Order> findByHomestayIdAndStatusInOrderByCheckInDate(
                        @Param("homestayId") Long homestayId,
                        @Param("statuses") List<OrderStatus> statuses);

        // ========== 管理员异常订单统计 ==========

        // 待处理订单（PENDING超过指定小时数）
        @Query("SELECT COUNT(o) FROM Order o WHERE o.status = 'PENDING' AND o.createdAt < :cutoffTime")
        Long countPendingTimeoutOrders(@Param("cutoffTime") LocalDateTime cutoffTime);

        // 支付异常订单（支付失败）
        @Query("SELECT COUNT(o) FROM Order o WHERE o.paymentStatus = 'PAYMENT_FAILED'")
        Long countPaymentFailedOrders();

        // 退款异常订单（退款失败）
        @Query("SELECT COUNT(o) FROM Order o WHERE o.paymentStatus = 'REFUND_FAILED'")
        Long countRefundFailedOrders();

        // 已支付但超过入住日期未入住（异常未入住）
        @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN ('PAID', 'READY_FOR_CHECKIN') AND o.paymentStatus = 'PAID' AND o.checkInDate < :today")
        Long countNotCheckedInOrders(@Param("today") LocalDate today);

        // 退款处理中订单
        @Query("SELECT COUNT(o) FROM Order o WHERE o.paymentStatus = 'REFUND_PENDING'")
        Long countRefundPendingOrders();

        // 争议待处理订单
        @Query("SELECT COUNT(o) FROM Order o WHERE o.status = 'DISPUTE_PENDING'")
        Long countDisputePendingOrders();

        // 待确认订单（今天及之前需要确认的）
        @Query("SELECT COUNT(o) FROM Order o WHERE o.status = 'PENDING' AND o.createdAt < :beforeDate")
        Long countPendingConfirmOrders(@Param("beforeDate") LocalDateTime beforeDate);

        /**
         * 批量查询指定房源列表中，在指定日期范围内有冲突订单的房源 ID。
         * 冲突定义：订单状态为 CONFIRMED 或 PAID，且日期范围重叠。
         */
        @Query("SELECT DISTINCT o.homestay.id FROM Order o WHERE o.homestay.id IN :ids " +
               "AND o.status IN ('CONFIRMED', 'PAID') " +
               "AND o.checkOutDate > :checkInDate AND o.checkInDate < :checkOutDate")
        List<Long> findConflictingHomestayIds(
                @Param("ids") List<Long> ids,
                @Param("checkInDate") LocalDate checkInDate,
                @Param("checkOutDate") LocalDate checkOutDate);

        /**
         * 批量统计房源订单数量
         */
        @Query("SELECT o.homestay.id, COUNT(o) FROM Order o WHERE o.homestay.id IN :ids GROUP BY o.homestay.id")
        List<Object[]> countByHomestayIds(@Param("ids") List<Long> ids);

        /**
         * 批量统计房源在指定时间后的订单数量
         */
        @Query("SELECT o.homestay.id, COUNT(o) FROM Order o WHERE o.homestay.id IN :ids AND o.createdAt > :createdAtAfter GROUP BY o.homestay.id")
        List<Object[]> countByHomestayIdsAndCreatedAtAfter(@Param("ids") List<Long> ids,
                        @Param("createdAtAfter") LocalDateTime createdAtAfter);

        /**
         * 房东日历视图 —— 查询指定房东（或指定房源）在日期范围内的所有占用订单。
         */
        @Query("SELECT o FROM Order o JOIN o.homestay h WHERE h.owner.id = :hostId " +
               "AND (:homestayId IS NULL OR h.id = :homestayId) " +
               "AND o.checkOutDate > :startDate AND o.checkInDate < :endDate " +
               "AND o.status IN :statuses")
        List<Order> findHostCalendarOrders(
                @Param("hostId") Long hostId,
                @Param("homestayId") Long homestayId,
                @Param("startDate") LocalDate startDate,
                @Param("endDate") LocalDate endDate,
                @Param("statuses") List<String> statuses);

        /**
         * 根据状态列表和入住日期查询订单（用于入住提醒定时任务）
         */
        List<Order> findByStatusInAndCheckInDate(List<String> statuses, LocalDate checkInDate);
}
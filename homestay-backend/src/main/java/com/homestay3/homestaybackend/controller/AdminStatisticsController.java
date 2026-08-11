package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.service.StatisticsService;
import com.homestay3.homestaybackend.service.HomestayAuditService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatisticsController {

    private static final Logger logger = LoggerFactory.getLogger(AdminStatisticsController.class);
    private final StatisticsService statisticsService;
    private final HomestayAuditService auditService;

    /**
     * 获取统计数据总览
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        logger.info("管理员获取统计数据, 开始日期: {}, 结束日期: {}", startDate, endDate);
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusMonths(1);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        
        Map<String, Object> statistics = statisticsService.getAdminStatistics(start, end);
        return ResponseEntity.ok(statistics);
    }

    /**
     * 获取订单趋势
     */
    @GetMapping("/statistics/order-trend")
    public ResponseEntity<?> getOrderTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        logger.info("管理员获取订单趋势, 开始日期: {}, 结束日期: {}", startDate, endDate);
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusMonths(1);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        
        Map<String, Object> trendData = statisticsService.getOrderTrend(start, end);
        return ResponseEntity.ok(trendData);
    }

    /**
     * 获取收入趋势
     */
    @GetMapping("/statistics/income-trend")
    public ResponseEntity<?> getIncomeTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        logger.info("管理员获取收入趋势, 开始日期: {}, 结束日期: {}", startDate, endDate);
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusMonths(1);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        
        Map<String, Object> trendData = statisticsService.getIncomeTrend(start, end);
        return ResponseEntity.ok(trendData);
    }

    /**
     * 获取用户增长趋势
     */
    @GetMapping("/statistics/user-trend")
    public ResponseEntity<?> getUserTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        logger.info("管理员获取用户增长趋势, 开始日期: {}, 结束日期: {}", startDate, endDate);
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusMonths(1);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        
        Map<String, Object> trendData = statisticsService.getUserGrowthTrend(start, end);
        return ResponseEntity.ok(trendData);
    }

    /**
     * 获取民宿地区分布
     */
    @GetMapping("/statistics/homestay-distribution")
    public ResponseEntity<?> getHomestayDistribution() {
        logger.info("管理员获取民宿地区分布");
        Map<String, Object> distributionData = statisticsService.getHomestayDistribution();
        return ResponseEntity.ok(distributionData);
    }

    /**
     * 获取审核统计数据
     */
    @GetMapping("/statistics/audit")
    public ResponseEntity<?> getAuditStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        logger.info("管理员获取审核统计数据, 开始日期: {}, 结束日期: {}", startDate, endDate);
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now();
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        
        Map<String, Object> auditStats = statisticsService.getAuditStatistics(start, end);
        return ResponseEntity.ok(auditStats);
    }

    /**
     * 获取全局审核历史记录
     */
    @GetMapping("/audit/history")
    public ResponseEntity<?> getAllAuditHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String reviewerName,
            @RequestParam(required = false) String homestayId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime
    ) {
        logger.info("管理员获取全局审核历史, 页码: {}, 大小: {}", page, size);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        // 构建查询条件
        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;
        
        if (startTime != null && !startTime.isEmpty()) {
            startDateTime = LocalDateTime.parse(startTime.replace("T", " "), 
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        
        if (endTime != null && !endTime.isEmpty()) {
            endDateTime = LocalDateTime.parse(endTime.replace("T", " "), 
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        
        // 调用审核服务获取历史记录
        Page<?> auditHistory = auditService.getAllAuditHistory(
            pageable, actionType, reviewerName, homestayId, startDateTime, endDateTime);
        
        return ResponseEntity.ok(auditHistory);
    }

    /**
     * 导出统计数据
     */
    @GetMapping("/statistics/export")
    public ResponseEntity<ByteArrayResource> exportStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        logger.info("管理员导出统计数据, 开始日期: {}, 结束日期: {}", startDate, endDate);
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusMonths(1);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        
        byte[] data = statisticsService.exportStatistics(start, end);
        String filename = "statistics_" + start.format(DateTimeFormatter.ISO_DATE) + 
                "_to_" + end.format(DateTimeFormatter.ISO_DATE) + ".csv";
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .contentLength(data.length)
                .body(new ByteArrayResource(data));
    }
} 
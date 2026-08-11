package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.annotation.OperationLog;
import com.homestay3.homestaybackend.dto.ReviewDTO;
import com.homestay3.homestaybackend.service.ReviewService;
import com.homestay3.homestaybackend.util.SortUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {

    private static final Logger logger = LoggerFactory.getLogger(AdminReviewController.class);
    private final ReviewService reviewService;

    /**
     * 获取评价列表，支持分页和筛选
     */
    @GetMapping
    public ResponseEntity<?> getReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createTime,desc") String sort
    ) {
        logger.info("管理员获取评价列表，页码: {}, 每页数量: {}, 评分: {}, 状态: {}, 排序: {}", page, size, rating, status, sort);
        
        Set<String> allowedFields = Set.of("id", "createTime", "updateTime", "rating");
        Pageable pageable = SortUtils.buildPageable(page, size, sort, null, allowedFields);
        Page<ReviewDTO> reviews = reviewService.getAdminReviews(pageable, rating, status);
        
        Map<String, Object> response = new HashMap<>();
        response.put("content", reviews.getContent());
        response.put("totalElements", reviews.getTotalElements());
        response.put("totalPages", reviews.getTotalPages());
        response.put("page", reviews.getNumber());
        response.put("size", reviews.getSize());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取评价详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getReviewDetail(@PathVariable Long id) {
        logger.info("管理员获取评价详情，ID: {}", id);
        ReviewDTO review = reviewService.getReviewById(id);
        return ResponseEntity.ok(review);
    }

    /**
     * 删除评价
     */
    @OperationLog(operationType = "DELETE", resource = "REVIEW")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(@PathVariable Long id) {
        logger.info("管理员删除评价，ID: {}", id);
        reviewService.deleteReview(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 审核评价 (隐藏/显示)
     */
    @OperationLog(operationType = "CREATE", resource = "REVIEW")
    @PostMapping("/{id}/action")
    public ResponseEntity<?> reviewAction(
            @PathVariable Long id,
            @RequestBody Map<String, Object> action
    ) {
        String actionType = (String) action.get("action");
        logger.info("管理员执行评价操作: {}, ID: {}", actionType, id);
        
        if ("hide".equals(actionType)) {
            reviewService.setReviewVisibility(id, false);
        } else if ("show".equals(actionType)) {
            reviewService.setReviewVisibility(id, true);
        } else {
            return ResponseEntity.badRequest().body("未知操作");
        }
        
        return ResponseEntity.ok().build();
    }

    /**
     * 获取评价统计数据
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getReviewStats() {
        logger.info("管理员获取评价统计数据");
        Map<String, Object> stats = reviewService.getAdminReviewStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 批量设置评价可见性
     */
    @OperationLog(operationType = "CREATE", resource = "REVIEW")
    @PostMapping("/batch/visibility")
    public ResponseEntity<?> batchSetVisibility(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) request.get("ids");
        Boolean isVisible = (Boolean) request.get("isVisible");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body("IDs不能为空");
        }
        if (isVisible == null) {
            return ResponseEntity.badRequest().body("isVisible不能为空");
        }
        logger.info("批量设置评价可见性, IDs数量: {}, isVisible: {}", ids.size(), isVisible);
        reviewService.batchSetVisibility(ids, isVisible);
        return ResponseEntity.ok().build();
    }

    /**
     * 批量删除评价
     */
    @OperationLog(operationType = "CREATE", resource = "REVIEW")
    @PostMapping("/batch/delete")
    public ResponseEntity<?> batchDelete(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) request.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body("IDs不能为空");
        }
        logger.info("批量删除评价, IDs数量: {}", ids.size());
        reviewService.batchDelete(ids);
        return ResponseEntity.ok().build();
    }
} 
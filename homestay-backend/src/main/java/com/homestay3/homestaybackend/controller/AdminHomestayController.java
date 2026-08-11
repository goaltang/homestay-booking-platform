package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.annotation.OperationLog;
import com.homestay3.homestaybackend.dto.HomestayAdminDetailDTO;
import com.homestay3.homestaybackend.dto.HomestayAdminSummaryDTO;
import com.homestay3.homestaybackend.dto.HomestayDetailDTO;
import com.homestay3.homestaybackend.dto.HomestayStatusResponse;
import com.homestay3.homestaybackend.dto.HomestayWriteRequest;
import com.homestay3.homestaybackend.dto.HomestayWriteResponse;
import com.homestay3.homestaybackend.controller.support.HomestayResponseAdapter;
import com.homestay3.homestaybackend.controller.support.HomestayWriteRequestAdapter;
import com.homestay3.homestaybackend.service.HomestayAdminService;
import com.homestay3.homestaybackend.service.HomestayCommandService;
import com.homestay3.homestaybackend.service.HomestayQueryService;
import com.homestay3.homestaybackend.service.search.HomestayIndexingService;
import com.homestay3.homestaybackend.util.SortUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/homestays")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminHomestayController {

    private static final Logger logger = LoggerFactory.getLogger(AdminHomestayController.class);
    private final HomestayAdminService homestayAdminService;
    private final HomestayCommandService homestayCommandService;
    private final HomestayQueryService homestayQueryService;
    private final HomestayResponseAdapter homestayResponseAdapter;
    private final HomestayWriteRequestAdapter homestayWriteRequestAdapter;
    private final HomestayIndexingService homestayIndexingService;

    /**
     * 获取房源列表，支持分页和筛选
     */
    @GetMapping
    public ResponseEntity<?> getHomestays(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        logger.info("管理员获取房源列表，请求页码 (1-based): {}, 每页数量: {}, 标题: {}, 状态: {}, 类型: {}, 省: {}, 市: {}, 价格: {}-{}, 排序: {}",
                page, size, title, status, type, provinceCode, cityCode, minPrice, maxPrice, sort);

        int pageZeroBased = Math.max(0, page - 1);

        Set<String> allowedFields = Set.of("id", "createdAt", "updatedAt", "title", "price", "status");
        Pageable pageable = SortUtils.buildPageable(pageZeroBased, size, sort, null, allowedFields);
        Page<HomestayAdminSummaryDTO> homestays =
                homestayAdminService.getAdminHomestaySummaries(pageable, title, status, type, provinceCode, cityCode, minPrice, maxPrice);
        
        Map<String, Object> response = new HashMap<>();
        response.put("content", homestays.getContent());
        response.put("totalElements", homestays.getTotalElements());
        response.put("totalPages", homestays.getTotalPages());
        response.put("page", homestays.getNumber() + 1);
        response.put("size", homestays.getSize());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取待审核房源列表 - 必须在 /{id} 路由之前定义
     */
    @GetMapping("/pending-review")
    public ResponseEntity<?> getPendingReviewHomestays(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort
    ) {
        logger.info("管理员获取待审核房源列表，页码: {}, 每页数量: {}, 排序: {}", page, size, sort);
        
        Set<String> allowedFields = Set.of("id", "createdAt", "updatedAt", "title", "status");
        Pageable pageable = SortUtils.buildPageable(page, size, sort, null, allowedFields);
        Page<HomestayAdminSummaryDTO> homestays =
                homestayAdminService.getAdminHomestaySummaries(pageable, null, "PENDING", null, null, null, null, null);
        
        Map<String, Object> response = new HashMap<>();
        response.put("content", homestays.getContent());
        response.put("totalElements", homestays.getTotalElements());
        response.put("totalPages", homestays.getTotalPages());
        response.put("page", homestays.getNumber());
        response.put("size", homestays.getSize());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取房源详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getHomestayDetail(@PathVariable Long id) {
        logger.info("管理员获取房源详情，ID: {}", id);
        HomestayDetailDTO homestay = homestayQueryService.getHomestayDetailById(id, null);
        return ResponseEntity.ok(homestay);
    }

    /**
     * 获取房源详情（包含完整房东信息）
     */
    @GetMapping("/{id}/with-owner")
    public ResponseEntity<?> getHomestayDetailWithOwner(@PathVariable Long id) {
        logger.info("管理员获取带完整房东信息的房源详情，ID: {}", id);
        
        try {
            HomestayAdminDetailDTO homestay = homestayAdminService.getHomestayAdminDetailWithOwner(id);
            return ResponseEntity.ok(homestay);
        } catch (Exception e) {
            logger.error("获取带房东信息的房源详情失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取房源详情失败: " + e.getMessage()));
        }
    }

    /**
     * 创建房源
     */
    @PostMapping
    @OperationLog(operationType = "CREATE", resource = "HOMESTAY", detail = "创建房源")
    public ResponseEntity<HomestayWriteResponse> createHomestay(@RequestBody HomestayWriteRequest request) {
        logger.info("管理员创建房源: {}", request.getTitle());
        HomestayWriteResponse createdHomestay = homestayResponseAdapter.toWriteResponse(
                homestayAdminService.createHomestay(homestayWriteRequestAdapter.toDTO(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(createdHomestay);
    }

    /**
     * 更新房源
     */
    @PutMapping("/{id}")
    @OperationLog(operationType = "UPDATE", resource = "HOMESTAY", resourceId = "#id", detail = "更新房源")
    public ResponseEntity<HomestayWriteResponse> updateHomestay(
            @PathVariable Long id,
            @RequestBody HomestayWriteRequest request) {
        logger.info("管理员更新房源，ID: {}", id);
        HomestayWriteResponse updatedHomestay = homestayResponseAdapter.toWriteResponse(
                homestayCommandService.updateHomestay(id, homestayWriteRequestAdapter.toDTO(request)));
        return ResponseEntity.ok(updatedHomestay);
    }

    /**
     * 删除房源
     */
    @DeleteMapping("/{id}")
    @OperationLog(operationType = "DELETE", resource = "HOMESTAY", resourceId = "#id")
    public ResponseEntity<?> deleteHomestay(@PathVariable Long id) {
        logger.info("管理员删除房源，ID: {}", id);
        homestayCommandService.deleteHomestay(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 更新房源状态
     */
    @PutMapping("/{id}/status")
    @OperationLog(operationType = "UPDATE", resource = "HOMESTAY", resourceId = "#id", detail = "更新房源状态")
    public ResponseEntity<HomestayStatusResponse> updateHomestayStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> statusData
    ) {
        String status = (String) statusData.get("status");
        logger.info("管理员更新房源状态，ID: {}, 状态: {}", id, status);
        homestayAdminService.updateHomestayStatus(id, status);
        HomestayDetailDTO homestay = homestayQueryService.getHomestayDetailById(id, null);
        return ResponseEntity.ok(homestayResponseAdapter.toStatusResponse(homestay));
    }

    /**
     * 更新房源首页精选状态
     */
    @PutMapping("/{id}/featured")
    @OperationLog(operationType = "UPDATE", resource = "HOMESTAY", resourceId = "#id", detail = "更新精选状态")
    public ResponseEntity<HomestayWriteResponse> updateHomestayFeatured(
            @PathVariable Long id,
            @RequestBody Map<String, Object> featuredData
    ) {
        Object rawFeatured = featuredData.get("featured");
        Boolean featured = rawFeatured instanceof Boolean
                ? (Boolean) rawFeatured
                : Boolean.parseBoolean(String.valueOf(rawFeatured));

        logger.info("管理员更新房源精选状态，ID: {}, featured: {}", id, featured);
        HomestayWriteResponse updatedHomestay = homestayResponseAdapter.toWriteResponse(
                homestayAdminService.updateHomestayFeatured(id, featured)
        );
        return ResponseEntity.ok(updatedHomestay);
    }

    /**
     * 批量删除房源
     */
    @DeleteMapping("/batch")
    @OperationLog(operationType = "DELETE", resource = "HOMESTAY", detail = "批量删除房源")
    public ResponseEntity<?> batchDeleteHomestays(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request.get("ids");
        
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "房源ID列表不能为空"));
        }
        
        logger.info("管理员批量删除房源，数量: {}, IDs: {}", ids.size(), ids);
        
        try {
            ids.forEach(homestayCommandService::deleteHomestay);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "批量删除成功",
                "deletedCount", ids.size()
            ));
        } catch (Exception e) {
            logger.error("批量删除房源失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 批量更新房源状态
     */
    @PutMapping("/batch/status")
    @OperationLog(operationType = "UPDATE", resource = "HOMESTAY", detail = "批量更新房源状态")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> batchUpdateHomestayStatus(@RequestBody Map<String, Object> request) {
        List<Long> ids = (List<Long>) request.get("ids");
        String status = (String) request.get("status");
        
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "房源ID列表不能为空"));
        }
        
        if (status == null || status.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "房源状态不能为空"));
        }
        
        logger.info("管理员批量更新房源状态，状态: {}, 数量: {}, IDs: {}", status, ids.size(), ids);
        
        try {
            ids.forEach(id -> homestayAdminService.updateHomestayStatus(id, status));
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", String.format("批量更新状态为 %s 成功", status),
                "updatedCount", ids.size()
            ));
        } catch (Exception e) {
            logger.error("批量更新房源状态失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 强制下架房源（因违规）
     */
    @PostMapping("/{id}/force-delist")
    @OperationLog(operationType = "UPDATE", resource = "HOMESTAY", resourceId = "#id", detail = "强制下架房源")
    public ResponseEntity<?> forceDelistHomestay(
            @PathVariable Long id,
            @RequestBody Map<String, String> request
    ) {
        String reason = request.get("reason");
        String notes = request.get("notes");
        String violationType = request.get("violationType");
        
        logger.info("管理员强制下架房源，ID: {}, 原因: {}, 违规类型: {}", id, reason, violationType);
        
        if (reason == null || reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "下架原因不能为空"));
        }
        
        try {
            homestayAdminService.forceDelistHomestay(id, reason, notes, violationType);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "强制下架成功"
            ));
        } catch (Exception e) {
            logger.error("强制下架房源失败，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "强制下架失败: " + e.getMessage()));
        }
    }

    /**
     * 批量补全缺失的地理编码坐标
     */
    @PostMapping("/batch-populate-coordinates")
    @OperationLog(operationType = "UPDATE", resource = "HOMESTAY", detail = "批量补全坐标")
    public ResponseEntity<?> batchPopulateCoordinates(@RequestBody(required = false) Map<String, Object> request) {
        int batchSize = 100;
        if (request != null && request.get("batchSize") instanceof Number) {
            batchSize = ((Number) request.get("batchSize")).intValue();
        }

        logger.info("管理员批量补全房源坐标，batchSize={}", batchSize);
        try {
            int successCount = homestayAdminService.batchPopulateCoordinates(batchSize);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "批量补全坐标完成",
                "successCount", successCount
            ));
        } catch (Exception e) {
            logger.error("批量补全坐标失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 全量重建 ES 搜索索引
     */
    @PostMapping("/rebuild-index")
    @OperationLog(operationType = "UPDATE", resource = "HOMESTAY", detail = "重建ES索引")
    public ResponseEntity<?> rebuildElasticsearchIndex() {
        logger.info("管理员请求重建 ES 房源索引");
        try {
            int indexedCount = homestayIndexingService.rebuildIndex();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "indexedCount", indexedCount,
                    "message", "ES 房源索引重建成功"
            ));
        } catch (Exception e) {
            logger.error("重建 ES 索引失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "重建索引失败: " + e.getMessage()));
        }
    }

    /**
     * 批量强制下架房源
     */
    @PostMapping("/batch/force-delist")
    @OperationLog(operationType = "UPDATE", resource = "HOMESTAY", detail = "批量强制下架房源")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> batchForceDelistHomestays(@RequestBody Map<String, Object> request) {
        List<Long> ids = (List<Long>) request.get("ids");
        String reason = (String) request.get("reason");
        String notes = (String) request.get("notes");
        String violationType = (String) request.get("violationType");
        
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "房源ID列表不能为空"));
        }
        
        if (reason == null || reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "下架原因不能为空"));
        }
        
        logger.info("管理员批量强制下架房源，数量: {}, 原因: {}", ids.size(), reason);
        
        try {
            int successCount = 0;
            for (Long id : ids) {
                try {
                    homestayAdminService.forceDelistHomestay(id, reason, notes, violationType);
                    successCount++;
                } catch (Exception e) {
                    logger.error("强制下架房源失败，ID: {}", id, e);
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "批量强制下架完成",
                "successCount", successCount,
                "totalCount", ids.size()
            ));
        } catch (Exception e) {
            logger.error("批量强制下架房源失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

} 

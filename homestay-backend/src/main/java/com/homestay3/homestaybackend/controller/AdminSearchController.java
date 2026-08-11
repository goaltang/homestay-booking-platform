package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.annotation.OperationLog;
import com.homestay3.homestaybackend.service.search.HomestayIndexingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 搜索管理控制器（管理员）
 */
@RestController
@RequestMapping("/api/admin/search")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSearchController {

    private static final Logger logger = LoggerFactory.getLogger(AdminSearchController.class);
    private final HomestayIndexingService homestayIndexingService;

    /**
     * 全量重建 ES 搜索索引
     */
    @OperationLog(operationType = "CREATE", resource = "SEARCH")
    @PostMapping("/index/rebuild")
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
}

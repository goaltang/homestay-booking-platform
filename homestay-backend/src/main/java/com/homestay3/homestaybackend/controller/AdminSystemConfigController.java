package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.annotation.OperationLog;
import com.homestay3.homestaybackend.entity.SystemConfig;
import com.homestay3.homestaybackend.service.OperationLogService;
import com.homestay3.homestaybackend.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;
    private final OperationLogService operationLogService;

    /**
     * 获取所有配置
     */
    @GetMapping("/configs")
    public ResponseEntity<Map<String, Object>> getAllConfigs() {
        List<SystemConfig> configs = systemConfigService.getAllConfigs();
        return ResponseEntity.ok(Map.of("success", true, "data", configs));
    }

    /**
     * 根据分类获取配置
     */
    @GetMapping("/configs/category/{category}")
    public ResponseEntity<Map<String, Object>> getConfigsByCategory(@PathVariable String category) {
        List<SystemConfig> configs = systemConfigService.getConfigsByCategory(category);
        return ResponseEntity.ok(Map.of("success", true, "data", configs));
    }

    /**
     * 根据key获取单个配置
     */
    @GetMapping("/configs/key/{key}")
    public ResponseEntity<Map<String, Object>> getConfigByKey(@PathVariable String key) {
        return systemConfigService.getConfigByKey(key)
                .map(config -> ResponseEntity.ok(Map.of("success", true, "data", config)))
                .orElse(ResponseEntity.ok(Map.of("success", false, "message", "配置不存在")));
    }

    /**
     * 批量获取配置值
     */
    @PostMapping("/configs/batch")
    @OperationLog(operationType = "CREATE", resource = "SYSTEM_CONFIG", detail = "批量查询配置")
    public ResponseEntity<Map<String, Object>> getConfigsByKeys(@RequestBody List<String> keys) {
        Map<String, String> configs = systemConfigService.getConfigsByKeys(keys);
        return ResponseEntity.ok(Map.of("success", true, "data", configs));
    }

    /**
     * 创建配置
     */
    @PostMapping("/configs")
    @OperationLog(operationType = "CREATE", resource = "SYSTEM_CONFIG", resourceId = "#config.configKey",
            detail = "创建配置: #{#config.configName} = #{#config.configValue}")
    public ResponseEntity<Map<String, Object>> createConfig(@RequestBody SystemConfig config,
                                                              @RequestParam(required = false) String operator,
                                                              @RequestParam(required = false) String ipAddress) {
        try {
            if (systemConfigService.getConfigByKey(config.getConfigKey()).isPresent()) {
                return ResponseEntity.ok(Map.of("success", false, "message", "配置键已存在"));
            }
            SystemConfig saved = systemConfigService.saveConfig(config);

            return ResponseEntity.ok(Map.of("success", true, "data", saved, "message", "创建成功"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "创建失败: " + e.getMessage()));
        }
    }

    /**
     * 更新配置
     */
    @PutMapping("/configs/key/{key}")
    @OperationLog(operationType = "UPDATE", resource = "SYSTEM_CONFIG", resourceId = "#key",
            detail = "修改配置: #{#config.configName}, 值: #{#config.configValue}")
    public ResponseEntity<Map<String, Object>> updateConfig(@PathVariable String key,
                                                              @RequestBody SystemConfig config,
                                                              @RequestParam(required = false) String operator,
                                                              @RequestParam(required = false) String ipAddress) {
        try {
            SystemConfig existing = systemConfigService.getConfigByKey(key)
                    .orElseThrow(() -> new RuntimeException("配置不存在"));

            SystemConfig updated = systemConfigService.updateConfig(key, config);

            return ResponseEntity.ok(Map.of("success", true, "data", updated, "message", "更新成功"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "更新失败: " + e.getMessage()));
        }
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/configs/{id}")
    @OperationLog(operationType = "DELETE", resource = "SYSTEM_CONFIG", resourceId = "#id",
            detail = "删除配置: #{#id}")
    public ResponseEntity<Map<String, Object>> deleteConfig(@PathVariable Long id,
                                                              @RequestParam(required = false) String operator,
                                                              @RequestParam(required = false) String ipAddress) {
        try {
            SystemConfig config = systemConfigService.getAllConfigs().stream()
                    .filter(c -> c.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("配置不存在"));

            systemConfigService.deleteConfig(id);

            return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "删除失败: " + e.getMessage()));
        }
    }

    /**
     * 初始化默认配置
     */
    @PostMapping("/configs/init")
    @OperationLog(operationType = "CREATE", resource = "SYSTEM_CONFIG", detail = "初始化默认配置")
    public ResponseEntity<Map<String, Object>> initDefaultConfigs() {
        try {
            systemConfigService.initDefaultConfigs();
            return ResponseEntity.ok(Map.of("success", true, "message", "初始化成功"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "初始化失败: " + e.getMessage()));
        }
    }

    // ==================== 操作日志接口 ====================

    /**
     * 分页获取操作日志
     */
    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        Sort sort = Sort.by(Sort.Direction.DESC, "operateTime");
        PageRequest pageRequest = PageRequest.of(page, size, sort);

        Page<com.homestay3.homestaybackend.entity.OperationLog> logs;
        if (operator == null && operationType == null && resource == null && startTime == null && endTime == null) {
            logs = operationLogService.getLogs(pageRequest);
        } else {
            LocalDateTime start = startTime != null ? LocalDateTime.parse(startTime + "T00:00:00") : null;
            LocalDateTime end = endTime != null ? LocalDateTime.parse(endTime + "T23:59:59") : null;
            logs = operationLogService.searchLogs(operator, operationType, resource, start, end, pageRequest);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", logs.getContent());
        result.put("total", logs.getTotalElements());
        result.put("totalPages", logs.getTotalPages());
        result.put("currentPage", logs.getNumber());

        return ResponseEntity.ok(result);
    }

    /**
     * 获取最近操作日志
     */
    @GetMapping("/logs/recent")
    public ResponseEntity<Map<String, Object>> getRecentLogs(@RequestParam(defaultValue = "50") int limit) {
        Page<com.homestay3.homestaybackend.entity.OperationLog> logs = operationLogService.getRecentLogs(PageRequest.of(0, limit));
        return ResponseEntity.ok(Map.of("success", true, "data", logs.getContent()));
    }
}

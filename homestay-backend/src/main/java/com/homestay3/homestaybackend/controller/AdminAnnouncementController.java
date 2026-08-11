package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.annotation.OperationLog;
import com.homestay3.homestaybackend.entity.Announcement;
import com.homestay3.homestaybackend.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/announcements")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    /**
     * 分页获取公告列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAnnouncements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        PageRequest pageRequest = PageRequest.of(page, size, sort);

        Page<Announcement> announcements;
        if (status != null && !status.isEmpty()) {
            announcements = announcementService.getAnnouncementsByStatus(status, pageRequest);
        } else {
            announcements = announcementService.getAllAnnouncements(pageRequest);
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", announcements.getContent(),
            "total", announcements.getTotalElements(),
            "totalPages", announcements.getTotalPages(),
            "currentPage", announcements.getNumber()
        ));
    }

    /**
     * 获取公告详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAnnouncement(@PathVariable Long id) {
        try {
            Announcement announcement = announcementService.getById(id);
            return ResponseEntity.ok(Map.of("success", true, "data", announcement));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "公告不存在"));
        }
    }

    /**
     * 创建公告
     */
    @PostMapping
    @OperationLog(operationType = "CREATE", resource = "ANNOUNCEMENT", detail = "'创建公告: ' + #announcement.title")
    public ResponseEntity<Map<String, Object>> createAnnouncement(
            @RequestBody Announcement announcement,
            @RequestParam Long publisherId,
            @RequestParam String publisherName,
            @RequestParam(required = false) String ipAddress) {
        try {
            Announcement created = announcementService.createAnnouncement(announcement, publisherId, publisherName);
            return ResponseEntity.ok(Map.of("success", true, "data", created, "message", "创建成功"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "创建失败: " + e.getMessage()));
        }
    }

    /**
     * 更新公告
     */
    @PutMapping("/{id}")
    @OperationLog(operationType = "UPDATE", resource = "ANNOUNCEMENT", resourceId = "#id",
            detail = "更新公告: #{#announcement.title}")
    public ResponseEntity<Map<String, Object>> updateAnnouncement(
            @PathVariable Long id,
            @RequestBody Announcement announcement,
            @RequestParam String publisherName,
            @RequestParam(required = false) String ipAddress) {
        try {
            Announcement updated = announcementService.updateAnnouncement(id, announcement);
            return ResponseEntity.ok(Map.of("success", true, "data", updated, "message", "更新成功"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "更新失败: " + e.getMessage()));
        }
    }

    /**
     * 删除公告
     */
    @DeleteMapping("/{id}")
    @OperationLog(operationType = "DELETE", resource = "ANNOUNCEMENT", resourceId = "#id", detail = "删除公告")
    public ResponseEntity<Map<String, Object>> deleteAnnouncement(
            @PathVariable Long id,
            @RequestParam String publisherName,
            @RequestParam(required = false) String ipAddress) {
        try {
            announcementService.deleteAnnouncement(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "删除失败: " + e.getMessage()));
        }
    }

    /**
     * 发布公告
     */
    @PostMapping("/{id}/publish")
    @OperationLog(operationType = "UPDATE", resource = "ANNOUNCEMENT", resourceId = "#id", detail = "发布公告")
    public ResponseEntity<Map<String, Object>> publishAnnouncement(
            @PathVariable Long id,
            @RequestParam Long publisherId,
            @RequestParam String publisherName,
            @RequestParam(required = false) String ipAddress) {
        try {
            Announcement published = announcementService.publishAnnouncement(id, publisherId, publisherName);
            return ResponseEntity.ok(Map.of("success", true, "data", published, "message", "发布成功"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "发布失败: " + e.getMessage()));
        }
    }

    /**
     * 下线公告
     */
    @PostMapping("/{id}/offline")
    @OperationLog(operationType = "UPDATE", resource = "ANNOUNCEMENT", resourceId = "#id", detail = "下线公告")
    public ResponseEntity<Map<String, Object>> offlineAnnouncement(
            @PathVariable Long id,
            @RequestParam String publisherName,
            @RequestParam(required = false) String ipAddress) {
        try {
            Announcement offline = announcementService.offlineAnnouncement(id);
            return ResponseEntity.ok(Map.of("success", true, "data", offline, "message", "下线成功"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "下线失败: " + e.getMessage()));
        }
    }
}

package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.annotation.OperationLog;
import com.homestay3.homestaybackend.dto.HomestayGroupDTO;
import com.homestay3.homestaybackend.service.HomestayGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/groups")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminGroupController {

    private final HomestayGroupService groupService;

    @GetMapping
    public ResponseEntity<?> getGroups(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long ownerId) {
        log.info("管理员获取分组列表，页码: {}, 每页: {}, 关键词: {}, 房东ID: {}", page, size, keyword, ownerId);
        int pageZeroBased = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageZeroBased, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<HomestayGroupDTO> groups = groupService.adminGetGroups(pageable, keyword, ownerId);
        Map<String, Object> response = new HashMap<>();
        response.put("content", groups.getContent());
        response.put("totalElements", groups.getTotalElements());
        response.put("totalPages", groups.getTotalPages());
        response.put("page", groups.getNumber() + 1);
        response.put("size", groups.getSize());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getGroup(@PathVariable Long id) {
        log.info("管理员获取分组详情: {}", id);
        return ResponseEntity.ok(groupService.adminGetGroupById(id));
    }

    @PutMapping("/{id}/toggle-enabled")
    @OperationLog(operationType = "UPDATE", resource = "GROUP", resourceId = "#id")
    public ResponseEntity<?> toggleEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        log.info("管理员{}分组: {}", enabled ? "启用" : "禁用", id);
        return ResponseEntity.ok(groupService.adminToggleGroupEnabled(id, enabled));
    }

    @DeleteMapping("/{id}")
    @OperationLog(operationType = "DELETE", resource = "GROUP", resourceId = "#id")
    public ResponseEntity<?> deleteGroup(@PathVariable Long id) {
        log.info("管理员删除分组: {}", id);
        try {
            groupService.adminDeleteGroup(id);
            return ResponseEntity.ok(Map.of("message", "删除分组成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

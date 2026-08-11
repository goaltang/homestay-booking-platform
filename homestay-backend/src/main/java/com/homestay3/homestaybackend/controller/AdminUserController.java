package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.annotation.OperationLog;
import com.homestay3.homestaybackend.dto.UserDTO;
import com.homestay3.homestaybackend.service.UserService;
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
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserController.class);
    private final UserService userService;

    /**
     * 获取用户列表，支持分页和筛选
     */
    @GetMapping
    public ResponseEntity<?> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        logger.info("管理员获取用户列表，页码: {}, 每页数量: {}, 用户名: {}, 邮箱: {}, 角色: {}, 排序: {}", 
                page, size, username, email, role, sort);
        
        // 确保页码是 0-based for Spring Data JPA
        int pageNumber = page > 0 ? page - 1 : 0;
        
        Set<String> allowedFields = Set.of("id", "createdAt", "updatedAt", "username", "email", "status");
        Pageable pageable = SortUtils.buildPageable(pageNumber, size, sort, null, allowedFields);
        Page<UserDTO> users = userService.getAdminUsers(pageable, username, email, role);
        
        Map<String, Object> response = new HashMap<>();
        response.put("content", users.getContent());
        response.put("totalElements", users.getTotalElements());
        response.put("totalPages", users.getTotalPages());
        response.put("page", users.getNumber());
        response.put("size", users.getSize());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserDetail(@PathVariable Long id) {
        logger.info("管理员获取用户详情，ID: {}", id);
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    /**
     * 创建用户
     */
    @OperationLog(operationType = "CREATE", resource = "USER")
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserDTO userDTO) {
        logger.info("管理员创建用户: {}", userDTO.getUsername());
        UserDTO createdUser = userService.createUser(userDTO);
        return ResponseEntity.ok(createdUser);
    }

    /**
     * 更新用户
     */
    @OperationLog(operationType = "UPDATE", resource = "USER")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        logger.info("管理员更新用户，ID: {}", id);
        UserDTO updatedUser = userService.updateUser(id, userDTO);
        return ResponseEntity.ok(updatedUser);
    }

    /**
     * 删除用户
     */
    @OperationLog(operationType = "DELETE", resource = "USER")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        logger.info("管理员删除用户，ID: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 更新用户状态
     */
    @OperationLog(operationType = "UPDATE", resource = "USER")
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> status
    ) {
        boolean enabled = (boolean) status.get("enabled");
        logger.info("管理员更新用户状态，ID: {}, 状态: {}", id, enabled);
        userService.updateUserStatus(id, enabled);
        return ResponseEntity.ok().build();
    }

    /**
     * 重置用户密码
     */
    @OperationLog(operationType = "CREATE", resource = "USER")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetUserPassword(@PathVariable Long id) {
        logger.info("管理员重置用户密码，ID: {}", id);
        String newPassword = userService.resetUserPassword(id);
        return ResponseEntity.ok(Map.of("newPassword", newPassword));
    }
} 
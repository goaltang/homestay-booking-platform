package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.annotation.OperationLog;
import com.homestay3.homestaybackend.dto.VerificationDTO;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.model.VerificationStatus;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.UserService;
import com.homestay3.homestaybackend.service.VerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/verifications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVerificationController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    /**
     * 获取身份验证列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getVerifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status) {
        
        try {
            // 确保页码不小于1
            if (page < 1) {
                page = 1;
            }
            
            // 页码从0开始
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
            
            Page<User> usersPage;
            
            // 根据参数筛选
            if (username != null && !username.isEmpty() && status != null && !status.isEmpty()) {
                VerificationStatus verificationStatus = VerificationStatus.valueOf(status);
                usersPage = userRepository.findByUsernameContainingAndVerificationStatus(
                        username, verificationStatus, pageable);
            } else if (username != null && !username.isEmpty()) {
                usersPage = userRepository.findByUsernameContainingAndVerificationStatusNotNull(
                        username, pageable);
            } else if (status != null && !status.isEmpty()) {
                VerificationStatus verificationStatus = VerificationStatus.valueOf(status);
                usersPage = userRepository.findByVerificationStatus(verificationStatus, pageable);
            } else {
                // 默认查询逻辑：查找所有已上传过身份证照片的用户或有验证状态的用户
                usersPage = userRepository.findByIdCardFrontNotNullOrIdCardBackNotNullOrVerificationStatusNotNull(pageable);
            }
            
            // 转换为DTO并打印日志
            List<VerificationDTO> verificationDtos = usersPage.getContent().stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
            
            System.out.println("找到" + verificationDtos.size() + "条记录");
            verificationDtos.forEach(dto -> {
                System.out.println("ID: " + dto.getId() + ", 用户名: " + dto.getUsername() +
                    ", 状态: " + dto.getStatus() + ", 身份证正面: " + 
                    (dto.getIdCardFront() != null ? "已上传" : "未上传") + 
                    ", 身份证背面: " + (dto.getIdCardBack() != null ? "已上传" : "未上传"));
            });
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", verificationDtos);
            response.put("totalElements", usersPage.getTotalElements());
            response.put("totalPages", usersPage.getTotalPages());
            response.put("page", page);
            response.put("size", size);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取身份验证列表失败");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 获取单个身份验证详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getVerification(@PathVariable Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            // 检查用户是否有身份验证信息
            if (user.getVerificationStatus() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "该用户没有身份验证信息"));
            }
            
            VerificationDTO verificationDto = convertToDto(user);
            return ResponseEntity.ok(verificationDto);
        }
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "未找到指定ID的用户"));
    }

    /**
     * 审核通过
     */
    @OperationLog(operationType = "CREATE", resource = "VERIFICATION")
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveVerification(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        
        Optional<User> userOpt = userRepository.findById(id);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            // 检查当前状态
            if (user.getVerificationStatus() != VerificationStatus.PENDING) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "只能审核状态为PENDING的验证"));
            }
            
            // 更新状态
            user.setVerificationStatus(VerificationStatus.VERIFIED);
            
            // 添加备注（如果有）
            String note = (body != null && body.containsKey("note")) ? body.get("note") : "审核通过";
            
            // 这里可以保存审核备注，如果有相关字段
            // user.setReviewNote(note);
            
            // 设置审核时间
            // user.setReviewTime(LocalDateTime.now());
            
            userRepository.save(user);
            
            return ResponseEntity.ok(Map.of(
                    "message", "审核已通过",
                    "status", user.getVerificationStatus()
            ));
        }
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "未找到指定ID的用户"));
    }

    /**
     * 审核拒绝
     */
    @OperationLog(operationType = "CREATE", resource = "VERIFICATION")
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectVerification(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        
        if (!body.containsKey("note") || body.get("note").trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "拒绝理由不能为空"));
        }
        
        Optional<User> userOpt = userRepository.findById(id);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            // 检查当前状态
            if (user.getVerificationStatus() != VerificationStatus.PENDING) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "只能审核状态为PENDING的验证"));
            }
            
            // 更新状态
            user.setVerificationStatus(VerificationStatus.REJECTED);
            
            // 添加拒绝理由
            String note = body.get("note");
            
            // 这里可以保存审核备注，如果有相关字段
            // user.setReviewNote(note);
            
            // 设置审核时间
            // user.setReviewTime(LocalDateTime.now());
            
            userRepository.save(user);
            
            return ResponseEntity.ok(Map.of(
                    "message", "审核已拒绝",
                    "status", user.getVerificationStatus(),
                    "note", note
            ));
        }
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "未找到指定ID的用户"));
    }

    /**
     * 将User转换为VerificationDTO
     */
    private VerificationDTO convertToDto(User user) {
        if (user == null) {
            return null;
        }
        
        // 确保有身份证照片的用户会显示在列表中，即使状态为null
        VerificationStatus status = user.getVerificationStatus();
        if (status == null) {
            // 如果上传了身份证但状态为空，默认设为待审核
            if (user.getIdCardFront() != null || user.getIdCardBack() != null) {
                status = VerificationStatus.PENDING;
            } else {
                status = VerificationStatus.UNVERIFIED;
            }
        }
        
        return VerificationDTO.builder()
                .id(user.getId())
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .idCard(user.getIdCard())
                .idCardFront(user.getIdCardFront())
                .idCardBack(user.getIdCardBack())
                .status(status)
                .submitTime(user.getUpdatedAt()) // 使用更新时间作为提交时间
                .build();
    }
} 
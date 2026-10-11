package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.AuthResponse;
import com.homestay3.homestaybackend.dto.PasswordChangeRequest;
import com.homestay3.homestaybackend.dto.ProfileUpdateRequest;
import com.homestay3.homestaybackend.dto.UserDTO;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.model.VerificationStatus;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.security.SecureRandom;
import com.homestay3.homestaybackend.security.AccountRole;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return convertToDTO(user);
    }

    @Override
    public UserDTO getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
        return convertToDTO(user);
    }

    @Override
    @Transactional
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // 更新用户信息
        if (userDTO.getFullName() != null) {
            user.setFullName(userDTO.getFullName());
        }
        if (userDTO.getPhone() != null) {
            user.setPhone(userDTO.getPhone());
        }
        if (userDTO.getAvatar() != null) {
            user.setAvatar(userDTO.getAvatar());
        }

        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        return convertToDTO(updatedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    // 头像上传功能已迁移到FileController统一处理
    // 请使用 /api/files/upload?type=avatar 端点

    @Override
    @Transactional
    public UserDTO updateUserRole(Long id, String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setRole(AccountRole.normalize(role));
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        return convertToDTO(updatedUser);
    }

    @Override
    @Transactional
    public UserDTO toggleUserStatus(Long id, boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setEnabled(enabled);
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        return convertToDTO(updatedUser);
    }

    @Override
    @Transactional
    public UserDTO createUser(UserDTO userDTO) {
        // 检查用户名和邮箱是否已存在
        if (userRepository.existsByUsername(userDTO.getUsername())) {
            throw new IllegalArgumentException("用户名已被使用: " + userDTO.getUsername());
        }
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new IllegalArgumentException("邮箱已被使用: " + userDTO.getEmail());
        }

        // 创建新用户
        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setEmail(userDTO.getEmail());
        user.setPhone(userDTO.getPhone());
        user.setFullName(userDTO.getFullName());
        user.setRealName(userDTO.getRealName());
        user.setIdCard(userDTO.getIdCard());

        // 设置默认头像
        if (userDTO.getAvatar() == null || userDTO.getAvatar().isEmpty()) {
            user.setAvatar("/uploads/avatars/default-avatar.png");
        } else {
            user.setAvatar(userDTO.getAvatar());
        }

        // 设置角色
        if (userDTO.getRole() != null && !userDTO.getRole().isEmpty()) {
            user.setRole(AccountRole.normalize(userDTO.getRole()));
        } else {
            user.setRole("ROLE_USER"); // 默认角色
        }

        // 生成随机密码
        String randomPassword = generateRandomPassword(8);
        user.setPassword(passwordEncoder.encode(randomPassword));

        // 设置启用状态
        user.setEnabled(userDTO.isEnabled());

        // 设置时间戳
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        // 保存用户
        User savedUser = userRepository.save(user);

        // 返回DTO，并额外添加生成的密码信息
        UserDTO savedUserDTO = convertToDTO(savedUser);
        // 注意：在实际应用中，应通过邮件发送密码，而不是直接返回
        // 此处仅用于演示

        return savedUserDTO;
    }

    @Override
    @Transactional
    public void updateUserStatus(Long id, boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setEnabled(enabled);
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
    }

    @Override
    @Transactional
    public String resetUserPassword(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // 生成8位随机密码
        String newPassword = generateRandomPassword(8);

        // 更新用户密码
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return newPassword;
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    @Override
    public UserDTO convertToDTO(User user) {
        if (user == null) {
            return null;
        }

        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .realName(user.getRealName())
                .fullName(user.getFullName())
                .idCard(user.getIdCard())
                .role(user.getRole())
                .avatar(user.getAvatar())
                .verificationStatus(user.getVerificationStatus())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLogin(user.getLastLogin())
                .build();
    }

    @Override
    @Transactional
    public void updateAvatar(String username, String avatarUrl) {
        log.debug("updateAvatar开始 - username: {}, avatarUrl: {}", username, avatarUrl);

        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("用户不存在: " + username));

            log.debug("找到用户: id={}, username={}, 当前头像={}", user.getId(), user.getUsername(), user.getAvatar());

            String oldAvatar = user.getAvatar();
            user.setAvatar(avatarUrl);
            user.setUpdatedAt(LocalDateTime.now());

            log.debug("准备保存用户，头像从 [{}] 更新为 [{}]", oldAvatar, avatarUrl);

            User savedUser = userRepository.save(user);

            log.debug("用户保存成功: id={}, 保存后的头像={}, 更新时间={}", savedUser.getId(), savedUser.getAvatar(),
                    savedUser.getUpdatedAt());

        } catch (Exception e) {
            log.error("updateAvatar发生异常: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public void changePassword(PasswordChangeRequest request, String username) {
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("原密码不正确");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public AuthResponse updateProfile(ProfileUpdateRequest request, String currentUsername) {
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!user.getUsername().equals(request.getUsername()) &&
                userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已被使用");
        }

        if (!user.getEmail().equals(request.getEmail()) &&
                userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("邮箱已被使用");
        }

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRealName(request.getRealName());
        user.setIdCard(request.getIdCard());
        user.setNickname(request.getNickname());
        user.setGender(request.getGender());
        user.setBirthday(request.getBirthday());
        user.setOccupation(request.getOccupation());
        user.setIntroduction(request.getIntroduction());
        user.setLanguages(request.getLanguages());
        user.setFrequentGuests(request.getFrequentGuests());

        user = userRepository.save(user);

        AuthResponse response = new AuthResponse();
        response.setUser(convertToDTO(user));
        return response;
    }

    @Override
    public Page<UserDTO> getAdminUsers(Pageable pageable, String username, String email, String role) {
        // 创建动态查询条件
        Specification<User> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 用户名筛选
            if (username != null && !username.isEmpty()) {
                predicates.add(criteriaBuilder.like(root.get("username"), "%" + username + "%"));
            }

            // 邮箱筛选
            if (email != null && !email.isEmpty()) {
                predicates.add(criteriaBuilder.like(root.get("email"), "%" + email + "%"));
            }

            // 角色筛选
            if (role != null && !role.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("role"), role));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        // 执行查询
        Page<User> usersPage = userRepository.findAll(spec, pageable);

        // 转换为DTO
        return usersPage.map(this::convertToDTO);
    }
}
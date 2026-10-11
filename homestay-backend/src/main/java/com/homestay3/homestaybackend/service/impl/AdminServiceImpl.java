package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.entity.Admin;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.UnauthorizedException;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    @Override
    public Admin getAdminByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("管理员不存在"));
        Admin admin = new Admin();
        admin.setId(user.getId());
        admin.setUsername(user.getUsername());
        admin.setPassword(user.getPassword());
        admin.setRole(user.getRole());
        return admin;
    }

    @Override
    @Transactional
    public void resetAdminPassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("管理员不存在"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("管理员 {} 的密码已重置", username);
    }

}

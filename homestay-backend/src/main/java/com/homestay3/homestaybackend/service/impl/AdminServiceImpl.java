package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.entity.Admin;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.UnauthorizedException;
import com.homestay3.homestaybackend.repository.AdminRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {
    private final AdminRepository adminRepository;
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
    public void createDefaultAdminIfNotExists() {
        if (!userRepository.existsByUsername("admin")) {
            User adminUser = new User();
            adminUser.setUsername("admin");
            adminUser.setPassword(passwordEncoder.encode("admin888"));
            adminUser.setRole("ROLE_ADMIN");
            adminUser.setEmail("admin@homestay.local");
            adminUser.setEnabled(true);
            userRepository.save(adminUser);
            log.info("已在 users 表中创建默认管理员账号 admin，密码为 admin888");
        }
    }

    @Override
    public void resetAdminPassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("管理员不存在"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("管理员 {} 的密码已重置", username);
    }

    @Bean
    @org.springframework.context.annotation.Profile("!test & !prod")
    public CommandLineRunner initializeAdminAndUser() {
        return args -> {
            // 创建默认管理员（仅在不存在时创建，不重置密码）——仅开发环境
            if (!userRepository.existsByUsername("admin")) {
                createDefaultAdminIfNotExists();
            }

            // 迁移 admin 表中的所有管理员到 users 表
            var allAdmins = adminRepository.findAll();
            for (Admin admin : allAdmins) {
                if (!userRepository.existsByUsername(admin.getUsername())) {
                    User adminUser = new User();
                    adminUser.setUsername(admin.getUsername());
                    adminUser.setPassword(admin.getPassword());
                    adminUser.setRole("ROLE_ADMIN");
                    adminUser.setEmail(admin.getUsername() + "@homestay.local");
                    adminUser.setEnabled(true);
                    userRepository.save(adminUser);
                    log.info("已将 admin 表中的 {} 迁移到 users 表", admin.getUsername());
                }
            }

            // 创建普通测试用户
            if (!userRepository.existsByUsername("user")) {
                User normalUser = new User();
                normalUser.setUsername("user");
                normalUser.setPassword(passwordEncoder.encode("111111"));
                normalUser.setRole("ROLE_USER");
                normalUser.setEmail("user@homestay.local");
                normalUser.setEnabled(true);
                userRepository.save(normalUser);
                log.info("已创建默认普通测试用户 user，密码为 111111");
            }

            // 创建房东测试用户
            if (!userRepository.existsByUsername("host")) {
                User hostUser = new User();
                hostUser.setUsername("host");
                hostUser.setPassword(passwordEncoder.encode("111111"));
                hostUser.setRole("ROLE_HOST");
                hostUser.setEmail("host@homestay.local");
                hostUser.setEnabled(true);
                userRepository.save(hostUser);
                log.info("已创建默认房东测试用户 host，密码为 111111");
            }
        };
    }

    /**
     * 生产环境：不自动创建任何演示账号。
     * 管理员通过首次启动引导创建——部署时设置环境变量 ADMIN_INIT_PASSWORD，
     * 且 admin 不存在时，用该密码初始化唯一管理员；登录后应立即修改密码。
     */
    @Bean
    @org.springframework.context.annotation.Profile("prod")
    public CommandLineRunner initializeProdAdmin() {
        return args -> {
            String initPassword = System.getenv("ADMIN_INIT_PASSWORD");
            if (initPassword == null || initPassword.isBlank()) {
                log.warn("生产环境未设置 ADMIN_INIT_PASSWORD，跳过初始管理员创建。"
                        + "如需初始化管理员，请设置该环境变量后重启。");
                return;
            }
            if (userRepository.existsByUsername("admin")) {
                log.info("管理员 admin 已存在，跳过初始管理员创建");
                return;
            }
            User adminUser = new User();
            adminUser.setUsername("admin");
            adminUser.setPassword(passwordEncoder.encode(initPassword));
            adminUser.setRole("ROLE_ADMIN");
            adminUser.setEmail("admin@homestay.local");
            adminUser.setEnabled(true);
            userRepository.save(adminUser);
            log.info("已创建初始管理员 admin（密码来自环境变量 ADMIN_INIT_PASSWORD，登录后请立即修改）");
        };
    }
}

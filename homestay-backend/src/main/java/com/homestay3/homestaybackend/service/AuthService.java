package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.dto.*;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(AuthRequest request);
    void forgotPassword(String email);
    void resetPassword(PasswordResetRequest request);
    // 头像上传功能已迁移到FileController统一处理
    AuthResponse getUserInfo(String username);
    void updateUserAvatar(String username, String avatarUrl);
    
    /**
     * 检查用户名是否已存在
     * @param username 用户名
     * @return true如果存在，false如果不存在
     */
    boolean isUsernameExists(String username);
    
    /**
     * 检查邮箱是否已存在
     * @param email 邮箱
     * @return true如果存在，false如果不存在
     */
    boolean isEmailExists(String email);
}

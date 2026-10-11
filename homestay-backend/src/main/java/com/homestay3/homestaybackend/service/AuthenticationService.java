package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.dto.AdminLoginRequest;
import com.homestay3.homestaybackend.dto.AdminLoginResponse;
import com.homestay3.homestaybackend.dto.AdminIdentityResponse;
import com.homestay3.homestaybackend.security.AccountRole;
import com.homestay3.homestaybackend.entity.User;
import org.springframework.security.authentication.BadCredentialsException;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public AdminLoginResponse login(AdminLoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    request.getUsername(), request.getPassword()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            throw new BadCredentialsException("用户名或密码错误", e);
        }

        // 验证是否为管理员
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_ADMIN"));

        if (!isAdmin) {
            throw new BadCredentialsException("无权访问管理后台，仅限管理员登录");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("用户不存在"));

        String jwt = jwtTokenProvider.generateToken(authentication);

        AdminIdentityResponse admin = new AdminIdentityResponse(
                user.getId(), user.getUsername(), AccountRole.normalize(user.getRole()));

        return AdminLoginResponse.builder()
            .token(jwt)
            .admin(admin)
            .build();
    }

    public void logout(String token) {
        jwtTokenProvider.revokeToken(token);
        SecurityContextHolder.clearContext();
    }

    public String getUsernameFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return jwtTokenProvider.getUsernameFromToken(token);
    }
}

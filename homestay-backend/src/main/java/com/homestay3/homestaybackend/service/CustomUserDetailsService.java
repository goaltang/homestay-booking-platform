package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("加载用户: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("用户不存在: {}", username);
                    return new UsernameNotFoundException("用户不存在: " + username);
                });

        String role;
        try {
            role = com.homestay3.homestaybackend.security.AccountRole.normalize(user.getRole());
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("账号角色无效", e);
        }
        return new com.homestay3.homestaybackend.security.CustomUserDetails(
                user.getId(), user.getUsername(), user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(role)), user.isEnabled());
    }
}

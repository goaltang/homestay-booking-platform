package com.homestay3.homestaybackend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 公开演示环境的共享体验账号保护：禁止修改密码和个人资料，避免被访客改掉后其他人无法登录。
 * 未配置 app.demo.locked-usernames 时不生效。
 */
@Component
public class DemoAccountGuard {

    static final String LOCKED_MESSAGE = "演示账号不能修改密码和个人资料";

    private final Set<String> lockedUsernames;

    public DemoAccountGuard(@Value("${app.demo.locked-usernames:}") String lockedUsernames) {
        this.lockedUsernames = Arrays.stream(lockedUsernames.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isLocked(String username) {
        return username != null && lockedUsernames.contains(username);
    }

    /** 演示账号抛出 SecurityException，由全局异常处理转为 403。 */
    public void assertModifiable(String username) {
        if (isLocked(username)) {
            throw new SecurityException(LOCKED_MESSAGE);
        }
    }
}

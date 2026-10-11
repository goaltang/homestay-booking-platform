package com.homestay3.homestaybackend.security;

import com.homestay3.homestaybackend.model.UserRole;
import java.util.Locale;

public final class AccountRole {
    private AccountRole() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("用户角色不能为空");
        }
        String role = value.trim().toUpperCase(Locale.ROOT);
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }
        if ("ROLE_LANDLORD".equals(role)) {
            role = "ROLE_HOST";
        }
        return UserRole.valueOf(role).name();
    }
}

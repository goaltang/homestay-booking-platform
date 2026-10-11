package com.homestay3.homestaybackend.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public final class AuthTokenResolver {
    public static final String COOKIE_NAME = "homestay_token";
    private AuthTokenResolver() {}

    public static String resolve(HttpServletRequest request) {
        if ("/api/auth/logout".equals(path(request))) {
            return cookie(request);
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null) {
            return authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : "";
        }
        if (path(request).startsWith("/api/admin/")) {
            return null;
        }
        return cookie(request);
    }

    public static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    public static String cookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}

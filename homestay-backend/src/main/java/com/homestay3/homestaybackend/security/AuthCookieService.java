package com.homestay3.homestaybackend.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieService {
    private final JwtTokenProvider tokenProvider;

    @Value("${app.auth.cookie.secure:false}")
    private boolean secure;

    public void set(HttpServletResponse response, String token) {
        write(response, token, tokenProvider.getExpirationSeconds());
    }

    public void clear(HttpServletResponse response) {
        write(response, "", 0);
    }

    private void write(HttpServletResponse response, String token, long maxAge) {
        ResponseCookie cookie = ResponseCookie.from(AuthTokenResolver.COOKIE_NAME, token)
                .httpOnly(true).secure(secure).path("/").sameSite("Lax").maxAge(maxAge).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

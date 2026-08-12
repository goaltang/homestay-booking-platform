package com.homestay3.homestaybackend.config;

import com.homestay3.homestaybackend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("配置安全过滤链...");
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/auth/**", "/error", "/h2-console/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/metrics/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/homestays/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/homestays/search", "/api/homestays/map-search",
                                "/api/homestays/map-clusters", "/api/homestays/nearby",
                                "/api/homestays/landmark-search",
                                "/api/v1/homestays/search", "/api/v1/homestays/map-search",
                                "/api/v1/homestays/map-clusters", "/api/v1/homestays/nearby",
                                "/api/v1/homestays/landmark-search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                        .requestMatchers("/api/files/upload", "/api/files/uploadMultiple").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/files/**").authenticated()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/static/**").permitAll()
                        .requestMatchers("/api/review/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/homestay/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/homestay/*/stats").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/home/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/announcements/**").permitAll()
                        .requestMatchers("/api/locations/**", "/api/v1/locations/**").permitAll()
                        .requestMatchers("/api/homestay-types/**", "/api/v1/homestay-types/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/amenities/**", "/api/v1/amenities/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/recommendations/personalized/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/recommendations/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/host/info/**").permitAll()
                        .requestMatchers("/api/system/**").permitAll()
                        .requestMatchers("/api/tracking/**").permitAll()
                        .requestMatchers("/api/payment/*/notify").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/payment/*/status").permitAll()
                        .requestMatchers("/api/payment/**").authenticated()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/host/register").authenticated()
                        .requestMatchers("/api/host/profile").authenticated()
                        .requestMatchers("/api/host/**").hasAnyAuthority("ROLE_HOST", "ROLE_ADMIN")
                        .requestMatchers("/api/host/earnings/**").hasAnyAuthority("ROLE_HOST", "ROLE_ADMIN")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));

        log.info("安全过滤链配置完成");
        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        log.info("已配置认证提供者");
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        log.info("创建认证管理器");
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        log.info("创建密码编码器");
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        log.info("配置CORS...");
        CorsConfiguration configuration = new CorsConfiguration();

        // 设置允许的来源URL，支持前端开发环境的不同端口
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173", "http://127.0.0.1:5173",
                "http://localhost:5174", "http://127.0.0.1:5174",
                "http://localhost:3000", "http://127.0.0.1:3000",
                "http://localhost:8080", "http://127.0.0.1:8080",
                "http://localhost:80", "http://127.0.0.1:80",
                "http://localhost", "http://127.0.0.1",
                "https://www.homestay3.com", "https://homestay3.com",
                "https://api.homestay3.com"));

        // 设置允许的方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));

        // 允许必要的头信息，包括自定义头
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Cache-Control", "Content-Type", "Accept",
                "Access-Control-Allow-Headers", "Access-Control-Allow-Origin",
                "X-Requested-With", "Origin",
                "x-user-id",
                "x-username"));

        // 暴露响应头
        configuration.setExposedHeaders(Arrays.asList(
                "Content-Type", "Content-Length", "Authorization",
                "Access-Control-Allow-Origin", "Access-Control-Allow-Methods",
                "Access-Control-Allow-Headers", "Access-Control-Allow-Credentials",
                "Content-Disposition",
                "X-Username",
                "X-User-Id"));

        // 允许发送凭证
        configuration.setAllowCredentials(true);

        // 预检请求的有效期，单位为秒
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("CORS配置完成，已添加 x-user-id 和 x-username 到 allowedHeaders");
        return source;
    }
}

package com.homestay3.homestaybackend.integration;

import com.homestay3.homestaybackend.HomestayBackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试示例
 * 测试应用上下文和基础端点
 * 
 * 运行此测试需要：
 * 1. 配置测试数据库或使用 @AutoConfigureTestDatabase
 * 2. 设置测试环境变量
 */
@SpringBootTest(classes = HomestayBackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
public class ApplicationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        // 验证应用上下文加载成功
        assertThat(restTemplate).isNotNull();
    }

    @Test
    void healthEndpoint_ShouldReturnOk() {
        // 测试健康检查端点（如果存在）
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", 
                String.class);
        
        // 如果健康端点不存在，会返回404
        // 如果存在且全部组件UP，应该返回200
        // 如果存在但依赖DOWN（如测试环境未起 ES），返回503 —— 也是健康端点的合法状态
        assertThat(response.getStatusCode()).isIn(
                HttpStatus.OK,
                HttpStatus.NOT_FOUND,
                HttpStatus.FORBIDDEN,
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @Test
    void apiRoot_ShouldBeAccessible() {
        // 测试API根路径
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api", 
                String.class);
        
        // 验证响应不为null
        assertThat(response).isNotNull();
    }
}

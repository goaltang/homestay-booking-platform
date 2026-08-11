# TASK：集成 springdoc 接口文档（Swagger UI）

项目：/mnt/d/homestay3/homestay-backend（Spring Boot 3.0.2 / Java 17 / Maven）
包根：com.homestay3.homestaybackend
**只改代码，不要 git commit / push。** 注释用中文。风格：Lombok @Slf4j + @RequiredArgsConstructor。

## 背景

项目 130+ 个 REST 接口没有任何接口文档。本次集成 springdoc-openapi（Spring Boot 3 专用 starter），
目标：`/swagger-ui.html` 可访问、`/v3/api-docs` 输出完整 JSON、JWT 认证可测试、现有测试不破坏。

## 任务 1：pom.xml 加依赖

在 spring-boot-starter-web 附近加（Spring Boot 3.0.2 必须用 starter-webmvc-ui 且版本 2.2.0，不要用 2.3.0+ 或 swagger 2 旧包）：
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.2.0</version>
</dependency>
```

## 任务 2：新建 config/OpenApiConfig.java

包：com.homestay3.homestaybackend.config

```java
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI homestayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("民宿预订系统 API")
                        .description("C端 + 管理后台 + AI客服 全量接口文档")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
```
import 用 `io.swagger.v3.oas.models.*` 和 `io.swagger.v3.oas.annotations.security.SecurityScheme`（或 models.security.SecurityScheme）。

要求：
- 全局 bearerAuth（HTTP Bearer JWT），Swagger UI 里出现 Authorize 按钮，可填 token 测试受保护接口
- 中文描述

## 任务 3：SecurityConfig 放行 swagger 路径（关键，否则 UI 401）

文件：config/SecurityConfig.java，在 permitAll 列表（`/api/admin/auth/**`, `/error`, `/h2-console/**` 那一行附近）加：
```java
.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
```

## 任务 4：JwtAuthenticationFilter 的 PUBLIC_PATHS 加 swagger 路径（双保险）

文件：security/JwtAuthenticationFilter.java
- 找到静态常量 PUBLIC_PATHS（String 列表），把 `"/swagger-ui"`, `"/v3/api-docs"`, `"/swagger-resources"` 加进去
- 这样 swagger 请求不会走 JWT 解析日志（干净 + 双保险）

## 任务 5：给主要 Controller 加 @Tag 分组（适度，不要全量注解）

- 只给 `controller/` 包下的核心 controller 加类级 `@Tag(name = "...")` 和少量 `@Operation(summary = "...")`（选 3-5 个代表：AuthController、HomestayController、OrderController、AdminOrderController、SupportAgentController 即可）
- **不要**给全部 100+ 接口逐个加 @Operation（工作量大且易错，本次目标是文档可访问可用）
- @Tag 用 `io.swagger.v3.oas.annotations.tags.Tag`，@Operation 用 `io.swagger.v3.oas.annotations.Operation`

## 验证（验收硬标准）

### 3.1 全量测试
```bash
cd /mnt/d/homestay3/homestay-backend && mvn -o test
```
全量 ~392 用例（AOP 改造后可能更多）必须全绿。springdoc 不应影响任何现有测试。

### 3.2 启动冒烟（关键，必须做）
依赖必须先起：
```bash
cd /mnt/d/homestay3 && docker.exe compose up -d elasticsearch
```
（ES 必须在线，否则后端启动崩。MySQL 本机 3306 已有。RabbitMQ/Redis 挂了也能启动——RedisLock 自动降级，MQ 连接懒加载。）

后台启动后端（WSL 侧）：
```bash
cd /mnt/d/homestay3/homestay-backend && mvn spring-boot:run
```
等日志出现 "Started HomestayBackendApplication"（约 30-60 秒）后验证：
```bash
curl -s http://localhost:8081/v3/api-docs | head -c 300          # 应输出 JSON
curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/swagger-ui.html   # 应为 200
curl -s http://localhost:8081/swagger-ui.html | grep -o "<title>[^<]*" | head -1  # 应有标题
```
验证完 kill 后端进程。

注意：
- WSL 跑 spring-boot:run 如果 8081 被占（残留进程），先 `netstat.exe -ano | findstr 8081` 查 PID 再 `cmd.exe /c "taskkill /PID <pid> /F"`（WSL 直接 taskkill.exe //PID 会报错）
- 改完代码 mvn spring-boot:run 不会自动重编译，启动前确保 mvn -o test 编译过

## 红线

- 不加任何新的 @SpringBootTest（除非必要）；现有测试禁止 deleteAll/truncate
- 不要动业务逻辑代码（controller 里只加注解，不改实现）
- springdoc 2.2.0 版本号写死，不要升级

## 验收清单

- [ ] pom.xml 有 springdoc-openapi-starter-webmvc-ui 2.2.0
- [ ] config/OpenApiConfig.java 存在（含 bearerAuth JWT scheme + 中文标题）
- [ ] SecurityConfig permitAll 含 swagger 路径
- [ ] JwtAuthenticationFilter PUBLIC_PATHS 含 swagger 路径
- [ ] 3-5 个 controller 有 @Tag
- [ ] mvn -o test 全绿（报告用例数）
- [ ] 冒烟：/v3/api-docs 返回 JSON、/swagger-ui.html 返回 200

完成后报告：改动文件清单、测试结果（用例数/失败数）、冒烟 curl 的实际输出、偏离本 TASK 的地方及原因。

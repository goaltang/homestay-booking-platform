# 两端认证与会话

C 端与管理员后台共用 users 账号表、密码校验和会话校验核心，各自登录、恢复身份和退出。账号共享不表示两端自动登录，没有 SSO 或跨端复制 token。

## 登录与权限

| 操作 | 合同 |
| --- | --- |
| 公开注册 | 只允许 USER / HOST 及 ROLE_ 前缀；缺省为 ROLE_USER；ADMIN 和未知角色返回 400 |
| C 端登录 | 拒绝管理员和禁用账号；正常用户及历史 LANDLORD 房东可登录 |
| 管理员登录 | 只允许当前数据库启用的管理员；返回 token 与 admin 身份 DTO，不返回密码哈希 |
| 后台身份恢复 | Bearer 调用 GET /api/admin/auth/info，身份 DTO 只含 id、username、role |
| 前端首次启动 | 服务端确认前不使用缓存身份授权；路由等待 current / info |
| 网络暂时故障 | 不使用未验证身份授权，保留 Cookie / 后台 Bearer 供刷新重试；已有身份的普通网络错误不清会话 |
| 401 | 清本地身份、聊天状态和连接，不递归调用远端退出 |
| 用户升级房东 | 接收新的内存 token，刷新角色并重连 WebSocket；旧角色 token 失效 |
| C端主动改密 | 使用认证端点并提交确认密码；成功后清本地会话进入登录页，失败保留身份并报告失败 |

后台响应角色为 ROLE_ADMIN，前端在单一入口规范化为内部 ADMIN。历史 LANDLORD / ROLE_LANDLORD 账号规范化为 ROLE_HOST；这个存量兼容不扩大公开注册的角色白名单。

## 持久会话

JWT 的 jti 对应 auth_sessions 主键。会话还保存 user_id、当前密码哈希的 SHA-256 摘要和 expires_at。JWT 不包含密码、密码摘要或重置令牌。

HTTP 与 WebSocket 校验签名、期限、持久会话及数据库当前账号。禁用、改名、角色变化或密码变化都会让不匹配的旧 token 失效。退出只删除当前会话；其他登录会话保留。角色改变后的新 token 使用当前权限，旧低权限 token 不能因为账号升权而获得新增权限。

登录签发比较认证 principal 的密码与角色快照，并用同一份已比较的密码哈希生成会话摘要。认证后发生密码重置时，不能用旧密码验证结果签出绑定新密码的有效会话。

User 使用动态字段更新，资料修改不回写未改变的安全字段。读取到保存的事务覆盖所有相关更新操作，避免脱管实体 merge 恢复旧字段。登录时间采用明确列 UPDATE；密码重置及旧密码验证改密使用同一用户行锁。

## Cookie 与 Bearer

后台 /api/admin/** 只解析 Bearer；C 端 Cookie 不会代替后台身份。C 端退出只撤销 Cookie 对应会话，后台退出保留 Bearer 直到远端请求完成，并且不清 C 端 Cookie。

带认证 Cookie 的写请求必须有可信 Origin 或 Referer，否则返回 403。来源来自同源或统一 CORS allowlist，拒绝 Origin:null、域名前缀伪造及未配置来源。不信任额外 forwarded host。后台纯 Bearer 请求无需 Cookie 来源头。

Compose 中 C 端 8088、后台 8089 通过各自 Nginx 同源代理 API 和 WebSocket。代理使用 `$http_host` 保留浏览器 Host 的端口，让后端识别同源请求；自定义访问域名和映射端口也遵循同一规则，无需扩展跨源白名单。

登录、注册、成为房东和退出使用同一个 Cookie helper，统一 Path=/、HttpOnly、SameSite=Lax、Secure 配置和期限。jwt.expiration 以毫秒计，Cookie Max-Age 由同一配置换算为秒；app.auth.cookie.secure 控制 Secure。

后台默认跨源请求不带 Cookie；同源反代或显式开启凭证时可能同时携带 C 端 Cookie，因此仍需要后台 Bearer 边界。

## 密码重置

POST /api/auth/forgot-password 使用 email 查询参数。已知和未知邮箱使用同一成功提示；非法邮箱返回 400，真实邮件发送失败仍返回失败。

邮件使用 32 字节随机令牌，数据库只存 SHA-256 摘要和 24 小时期限。POST /api/auth/reset-password 请求为 {token,newPassword}，密码至少 6 字符。令牌一次性消费；重新申请使旧链接失效。普通登录 JWT、过期、未知或已消费令牌返回 400。旧版本的 JWT 重置链接需要重新申请。

测试使用邮件 mock，没有发送真实邮件或连接业务数据库。

## 数据库部署与验证

V50__create_auth_sessions_table.sql 新增会话表，不修改历史迁移。结合数据库初始化改动，新库运行 B49 后运行 V50；已有历史沿 V 链升级。参见 [数据库说明](DATABASE.md)。应用旧 JWT 没有持久会话记录，升级后需要重新登录。

后端集成验证必须使用 test profile 与 H2 内存库。H2 中 Flyway 关闭，不能证明 MySQL 迁移通过。MySQL 回归使用独立实例或 Testcontainers；没有实例时明确跳过。

本次最终验证结果由工作目录之外的 integration-result.md 记录；真实浏览器、邮件、部署反代和 MySQL 迁移只有实际运行后才能记为通过。

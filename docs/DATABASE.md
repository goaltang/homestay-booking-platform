# 数据库初始化与升级

数据库结构由 Flyway 统一管理；应用启动不创建开发管理员或演示数据。完整安装步骤见 [INSTALL.md](INSTALL.md)。

## 选择你的数据库场景

| 数据库状态 | 启动行为 | 需要做什么 |
|---|---|---|
| 空库，没有 Flyway 历史 | 原生执行 B49，再执行未来高于 49 的 V 迁移 | 创建空数据库，填写配置后启动 |
| 已有 `flyway_schema_history` | 跳过 B49，校验历史并沿 V 迁移链继续 | 先备份，核对历史状态，保留已应用迁移 |
| 非空库，没有 Flyway 历史 | 拒绝迁移，不自动 baseline | 按下文完成备份、还原验证、结构对比，再显式建基线 |

有历史的旧库即使版本低于 49，也必须继续执行尚未应用的 V 迁移。不能删除历史、重建为版本 49，或手工导入 B49 来跳过升级。

## 结构唯一来源

后端固定使用社区版 `flyway-core` / `flyway-mysql` **9.22.3**。该版本原生识别 `B` 基线迁移文件；不依赖启动时复制 SQL、自定义 Flyway strategy 或商业版功能。

| 文件 / 设置 | 作用 |
|---|---|
| `homestay-backend/src/main/resources/db/migration/B49__initial_schema.sql` | 给没有迁移历史的空库创建截至 V49 的结构 |
| 同目录的 `V*__*.sql` | 已有历史数据库的连续升级；未来版本高于 49 时也用于新库 |
| `spring.flyway.baseline-on-migrate=false` | 禁止自动接管无历史的非空库 |
| `spring.flyway.baseline-version=49` | 显式 baseline 的目标版本，不代表数据库已满足版本 49 |
| `spring.flyway.validate-on-migrate=true` | 启动迁移前校验迁移历史 |
| `spring.flyway.clean-disabled=true` | 禁止 Flyway clean |
| `spring.sql.init.mode=never` | 禁用 Spring SQL init |
| `spring.jpa.hibernate.ddl-auto=none` | 禁用 Hibernate 建表和自动改表 |

B49 创建 **52 张业务表和 2 个视图**，V50 再创建 auth_sessions 会话表；新库最终为 53 张业务表，另由 Flyway 创建自己的历史表。基础数据只有房型字典和入住退房默认配置，没有管理员、演示账号、房源、订单或演示营销数据。设施等基础字典保留业务初始化逻辑。

B49 沿用已有 schema 快照的约束策略：保留主键、唯一索引和普通索引，不在本次收敛中新增外键或 CHECK 约束。表之间的关系仍由现有业务逻辑管理。历史 V 迁移中包含部分外键或 CHECK 定义，因此既有库沿 V 链升级后的约束集合可能与 B49 新库不同；本次不自动补齐或移除这些约束，也不宣称两条路径完全等价。

Docker 不再挂载 schema SQL。`DatabaseInitializer`、开发环境自动 Flyway repair 和默认账号创建 runner 已移除。`ADMIN_INIT_PASSWORD` 不再触发管理员创建。开发、perf 和容器启动都通过 Flyway 管理结构。

### 已有本地配置必须主动更新

`application.properties` 和 `application-local.properties` 被 Git 忽略，不会随代码拉取而更新。在相同配置位置，`.properties` 的优先级高于 `.yml`。仅修改共享 `application.yml` **无法覆盖已有 properties 中的旧值**。

检查实际使用的配置文件、profile 和环境变量，至少保证：

```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=false
spring.flyway.baseline-version=49
spring.flyway.validate-on-migrate=true
spring.flyway.clean-disabled=true
spring.sql.init.mode=never
spring.jpa.hibernate.ddl-auto=none
```

删除旧的 SQL 初始化路径和自动 repair 配置。不得通过 `baseline-on-migrate=true`、`ddl-auto=update` 或关闭校验绕过启动失败。`local` 配置需用 `-Dspring-boot.run.profiles=local` 显式加载。

## 新建空库

创建数据库，不手动导入 schema：

```bash
mysql -u root -p -e "CREATE DATABASE homestay_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

按安装说明配置后启动：

```bash
cd homestay-backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway 先应用 B49，再应用 V50 创建认证会话表。V1 至 V49 不会重复执行，因为其结构已包含在基线迁移中。查看历史确认实际结果：

```sql
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

新库的 B49 是实际执行的 SQL 迁移。它与旧库显式执行 `baseline` 后生成的 `BASELINE` 标记不同。新库没有默认管理员，列表没有演示房源是正常结果。

## 已有 Flyway 历史

1. 停止业务写入，备份数据库，并在独立实例验证备份能还原。
2. 检查 `flyway_schema_history`：版本、校验和、失败记录与当前仓库是否一致。
3. 使用上述安全配置启动。Flyway 跳过 B49，只执行未应用的 V 迁移。
4. 核对启动日志、迁移历史和升级后的业务行为。

历史 V 链中的 `V27__add_coordinates_and_seed_data.sql` 混入了演示房源写入，本次不改写该历史文件。已有历史尚未到 V27 的库，沿 V 链升级时仍会执行其原有行为；新库从 B49 开始，跳过 V27，不导入这些历史演示数据。

不要修改已应用的 V 文件。校验失败时先确定原因；启动不会自动 repair，repair 也不能补齐缺失的表或字段。确有历史修复需求时，单独评估、备份并记录操作，不能把 repair 当作日常启动步骤。

## 无历史的非空旧库

Flyway 默认拒绝这个场景。**只有实际结构已达到 B49 / V49，且备份、还原与对比均完成，才允许显式建立版本 49 的基线。**

版本低于 49、表结构来源不明或存在未解释差异时，不可使用此基线。先制定缺失迁移的独立升级方案，不能用 baseline 标记掩盖差异。

### 1. 停写并备份，验证还原

确认目标数据库地址与名称；备份不要只保存 schema。以下为本地地址示例，换成自己的实际连接参数：

```bash
mysqldump -h 127.0.0.1 -P 3306 -u root -p \
  --single-transaction --routines --triggers --events --hex-blob \
  --no-tablespaces --set-gtid-purged=OFF homestay_db > homestay-before-baseline.sql
```

业务写入及 DDL 必须停住；`--single-transaction` 不能保护迁移期间的并发改表。备份文件包含业务数据，应保存在受控位置，不提交到仓库。

将备份还原到**独立 MySQL 8 实例**，不能在业务实例上建临时库执行验证。以下 `13317` 仅为独立实例示例端口，先核对实例身份；当前项目验证实例 `13316` 不是日常业务库。

```bash
mysql -h 127.0.0.1 -P 13317 -u root -p -e \
  "CREATE DATABASE legacy_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE b49_reference CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 13317 -u root -p legacy_restore < homestay-before-baseline.sql
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 13317 -u root -p b49_reference < homestay-backend/src/main/resources/db/migration/B49__initial_schema.sql
```

在还原库核对各业务表行数、关键订单与账号记录、视图可查询性。检查备份日志和还原错误，不能只看文件存在。

### 2. 通过 information_schema 对比 B49

在独立实例上对比 `legacy_restore` 与 `b49_reference`。只数表无法证明版本已达 49；至少检查表、列、索引、视图和约束。

把下面 SQL 保存到本地临时文件 `/tmp/homestay-schema-compare.sql`。不把临时产物提交到仓库。查询使用当前选中的数据库：

```sql
SELECT TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
ORDER BY TABLE_NAME;

SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
       COLUMN_DEFAULT, EXTRA, CHARACTER_SET_NAME, COLLATION_NAME,
       GENERATION_EXPRESSION
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
ORDER BY TABLE_NAME, ORDINAL_POSITION;

SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME,
       COLLATION, SUB_PART, INDEX_TYPE, IS_VISIBLE, EXPRESSION
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

SELECT TABLE_NAME, VIEW_DEFINITION, CHECK_OPTION, IS_UPDATABLE, SECURITY_TYPE
FROM information_schema.VIEWS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;

SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
ORDER BY TABLE_NAME, CONSTRAINT_NAME;
```

从项目根目录执行：

```bash
mysql -h 127.0.0.1 -P 13317 -u root -p --batch --skip-column-names legacy_restore \
  < /tmp/homestay-schema-compare.sql > /tmp/homestay-legacy-schema.tsv
mysql -h 127.0.0.1 -P 13317 -u root -p --batch --skip-column-names b49_reference \
  < /tmp/homestay-schema-compare.sql > /tmp/homestay-b49-schema.tsv
diff -u /tmp/homestay-b49-schema.tsv /tmp/homestay-legacy-schema.tsv
```

视图定义可能包含数据库名称，需要在核对后只规范化这类名称差异。忽略业务数据引起的 AUTO_INCREMENT 当前值差异，但不能忽略列类型、默认值、索引、缺表或视图逻辑差异。检查房型字典和入住退房默认配置，确认基础数据也符合当前版本。

保留对比结论、备份位置和还原验证结果。存在未解释的差异时停止，不建立版本 49 基线。

### 3. 显式建立基线

在已验证的还原库上先演练，再对停写中的原库执行。使用 **Flyway CLI 9.22.3**，不是任意系统安装版本。先运行 `flyway -v` 确认。

命令通过环境变量提供连接信息。密码用交互读取，避免写入命令、文档或 shell 历史。以下地址先填写为独立演练库；演练成功后再明确替换为已核对的目标库。

```bash
export FLYWAY_URL='jdbc:mysql://127.0.0.1:13317/legacy_restore?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
export FLYWAY_USER='root'
read -r -s -p 'Flyway database password: ' FLYWAY_PASSWORD
printf '\n'
export FLYWAY_PASSWORD
export FLYWAY_LOCATIONS="filesystem:$PWD/homestay-backend/src/main/resources/db/migration"
export FLYWAY_BASELINE_ON_MIGRATE=false
export FLYWAY_VALIDATE_ON_MIGRATE=true
export FLYWAY_CLEAN_DISABLED=true
flyway info
flyway -baselineVersion=49 baseline
flyway validate
flyway info
unset FLYWAY_PASSWORD
```

CLI 的 `baseline` 只写入版本 49 的历史标记，**不会执行 B49，也不会补建表**。原库建立基线后，按正常流程启动应用；Flyway 只执行未来高于 49 的 V 迁移。保留备份，核对历史与关键业务行为后再恢复写入。

## 开发数据按需导入

先完成 Flyway 迁移，并确认目标是独立开发库。导入前停止后端及其他业务写入。脚本只导入数据，不创建业务表。具体参数和可选密码哈希说明见 [tools/database/README.md](../tools/database/README.md)。

### 主动创建管理员

在项目根目录执行，先准备管理员密码哈希所需的 bcrypt 依赖：

```bash
python3 -m venv tools/database/.venv
tools/database/.venv/bin/python -m pip install -r tools/database/requirements.txt
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_DATABASE=homestay_db
export MYSQL_USER=root
tools/database/.venv/bin/python tools/database/import_data.py admin --username admin --email admin@homestay.local
```

`MYSQL_DATABASE` 和 `MYSQL_USER` 必填；`MYSQL_HOST` 默认 `127.0.0.1`，`MYSQL_PORT` 默认 `3306`。未设置 `MYSQL_PASSWORD` 时，脚本交互读取数据库密码。管理员密码默认交互输入两次，并生成 bcrypt 哈希；**没有默认管理员密码**，不使用命令行明文密码参数。

也可通过 `ADMIN_PASSWORD_HASH` 提供完整 `$2a$` / `$2b$` / `$2y$` bcrypt 哈希，详见工具说明。导入角色为 `ROLE_ADMIN`；用户名或邮箱冲突会失败，不覆盖或重置已有账号。导入成功后，用自己选择的管理员密码登录管理端。

### 导入演示账号与房源

停止后端及其他业务写入，再在项目根目录运行。使用与上例相同的数据库环境变量：

```bash
python3 tools/database/import_data.py demo
```

演示导入只接受干净的开发库：`users` 中已有账号必须全部为 `ROLE_ADMIN`，`homestays`、`orders`、`homestay_images` 和 `homestay_amenity` 必须为空。它保留已有管理员，使用自动生成的 ID，导入 `host1`、`host2`、`user1`（演示密码均为 `123456`）、5 个房源和 5 张图片。

重复运行到已有演示数据的库会被拒绝；中途失败回滚本次事务。需要重建演示环境时使用新的独立开发库，不清空业务库。演示账号只用于本地学习，对外部署前修改或移除。

### 使用 Compose 的 MySQL

脚本也可通过 MySQL 容器中的客户端连接。保持上述环境变量；管理员仍需交互设置密码或提供哈希。在项目根目录执行：

```bash
tools/database/.venv/bin/python tools/database/import_data.py --mysql-command "docker compose exec -T -e MYSQL_PWD mysql mysql" admin --username admin --email admin@homestay.local
python3 tools/database/import_data.py --mysql-command "docker compose exec -T -e MYSQL_PWD mysql mysql" demo
```

管理员和演示数据是两个独立入口，按需要选择。不要把导入工具设为应用启动 runner、Flyway 迁移或 Docker 自动初始化挂载。

## 验证状态与范围

下表为数据库初始化任务在加入认证 V50 前的验证记录：使用独立 **MySQL 8.0.32**，宿主端口 **13316**，不连接业务库。认证整合目录当前没有可用 Docker 或 13316 独立实例；B49 + V50 新安装及 V48 经 V49 升级到 V50 未运行，不能沿用下表结果视为整合后通过。认证合同见 [AUTHENTICATION.md](AUTHENTICATION.md)。

| 检查 | 实际结果 |
|---|---|
| 空库 B49 安装 | 通过 |
| 安装后重复启动 | 通过 |
| 管理员与演示数据导入 | 通过；4 个用户、5 个房源、5 张图片，原管理员保留 |
| 重复演示导入 | 按预期拒绝 |
| 注入房源 INSERT 失败后的事务回滚 | 通过；本次用户与房源写入整体回滚 |
| 已有历史升级、无历史拒绝 | 3 项 MySQL 迁移测试通过；真实执行 V48 后升级 V49，保留用户与订单 |
| 核对后显式 baseline 49 | 独立结构副本验证通过；保留管理员，未重跑建表 SQL |
| 后端统一 `mvn clean test` | 通过；522 项，0 失败、0 错误，2 项 ES 集成测试因 Docker 不可用跳过 |
| PowerShell 压测入口 | 未运行 |
| Docker 部署流程 | 未运行 |

可重复执行的迁移回归命令（只接受独立实例的 13316 端口和测试库名前缀）：

```bash
export HOMESTAY_MIGRATION_TEST_URL='jdbc:mysql://127.0.0.1:13316/homestay_migration_test_runner?allowPublicKeyRetrieval=true&useSSL=false'
export HOMESTAY_MIGRATION_TEST_USER='YOUR_TEST_USER'
read -r -s -p 'Test database password: ' HOMESTAY_MIGRATION_TEST_PASSWORD
printf '\n'
export HOMESTAY_MIGRATION_TEST_PASSWORD
cd homestay-backend
mvn -Dtest=FlywayMySqlMigrationTest test
unset HOMESTAY_MIGRATION_TEST_PASSWORD
```

测试自行创建并销毁唯一命名的测试库；未配置独立实例时明确跳过。GitHub CI 的 `database-migrations` 任务提供独立 MySQL 服务，执行迁移回归和 Python 导入脚本测试。

应用的 H2 测试关闭 Flyway，不能证明 MySQL 新库安装或旧库升级成功。

数据库改动应独立验证：空库 B49 安装、已有历史沿 V 链升级、无历史非空库拒绝、核对后的显式 baseline、重复启动和开发数据导入。实际结果在交付说明中报告，不以历史测试数字替代当前验证。

perf 脚本顺序为启动后端并完成健康检查、导入压测数据、执行压测；`OnlyUp` 只启动基础设施。PowerShell 压测入口和 Docker 部署流程本次未运行，不视为通过。

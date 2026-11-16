# 显式导入管理员与演示数据

先完成 MySQL 8 表结构初始化，再按需要运行 `import_data.py`。脚本只插入数据，不覆盖账号、不清理业务表。依赖 Python 3 与 `mysql` 客户端；交互创建管理员还需要 `bcrypt`。

导入时停止后端和其他业务写入，避免空表检查之后有并发写入。所有涉及的业务表必须使用 InnoDB；项目表结构已经使用该引擎。运行位置为仓库根目录。

## 配置连接

```bash
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_DATABASE=homestay_db
export MYSQL_USER=YOUR_DATABASE_USER
```

`MYSQL_DATABASE` 和 `MYSQL_USER` 必填。主机和端口分别默认 `127.0.0.1`、`3306`。`MYSQL_PASSWORD` 可由本地环境提供；未设置时脚本隐藏提示输入。不要将密码作为命令行参数传入。

脚本将数据库密码放入子进程的 `MYSQL_PWD`，不放入参数，也不打印。`MYSQL_PWD` 是 MySQL 客户端环境变量，应在可信的本机环境运行。

## 创建管理员

管理员登录使用 `users` 表中的 `ROLE_ADMIN` 账号。该入口不写旧 `admin` 表。

管理员密码默认通过隐藏输入提示读取两次，再由 `bcrypt` 生成哈希。没有默认密码，不接收明文密码参数。先安装到脚本自己的虚拟环境：

```bash
python3 -m venv tools/database/.venv
tools/database/.venv/bin/python -m pip install -r tools/database/requirements.txt
tools/database/.venv/bin/python tools/database/import_data.py admin --username admin --email admin@homestay.local
```

脚本先提示输入管理员新密码，再提示 MySQL 密码。两次管理员密码必须一致，UTF-8 编码不能超过 BCrypt 的 72 字节限制。

如果已有可信的 BCrypt 哈希，可在本地环境设置 `ADMIN_PASSWORD_HASH`，此时不提示管理员密码，也不需要安装 `bcrypt`。支持 `$2a$`、`$2b$`、`$2y$`；不要把明文密码填入该变量。

成功输出 `导入已提交：admin`。用户名或邮箱已有记录时退出失败，不修改旧账号的密码或角色。

## 导入演示数据

```bash
python3 tools/database/import_data.py demo
```

成功输出 `导入已提交：demo`，插入 3 个用户、5 个房源和 5 张详情图片。图片使用原种子数据中的外部 URL，需要网络才能显示。

| 账号 | 角色 | 密码 |
| --- | --- | --- |
| host1 | 房东 | 123456 |
| host2 | 房东 | 123456 |
| user1 | 普通用户 | 123456 |

该数据仅适用于隔离的开发或演示环境。SQL 改编自旧 `homestay-backend/src/main/resources/db/seed_data.sql`，取消了清表操作和固定主键；房型按 V30 使用标准代码。

`users` 只允许已经存在 `ROLE_ADMIN` 账号，且 `homestays`、`orders`、`homestay_images`、`homestay_amenity` 必须为空。管理员不变，演示用户与房源由数据库自动分配 ID。

空表检查和所有插入在同一事务内。业务表非空时，检查向临时表的无符号列插入负值，触发 MySQL 严格模式错误；客户端停止并断开连接，事务回滚。发生唯一键冲突或后续 SQL 错误时同样回滚，重复运行会失败。

## 导入完整演示数据（demo-full）

`demo_full.sql.gz` 是从作者开发库导出并脱敏的完整演示数据（约 3 MB），用于展示真实规模下的列表、搜索、推荐和统计：

| 内容 | 数量（约） |
| --- | --- |
| 用户（房东 / 房客） | 800 / 4000 |
| 房源 / 图片 | 5000 / 25000 |
| 订单 / 评价 / 聊天消息 / 通知 | 8000 / 2900 / 8600 / 33000 |

```bash
python3 tools/database/import_data.py demo-full
```

使用 Docker 时，`./deploy/demo.sh` 会自动完成启动、导入和创建管理员，不必手动运行上面的命令。

成功输出 `导入已提交：demo-full`。所有演示账号密码均为 `123456`，例如房东 `host_0097`。快照中**不含管理员**，需要另用 `admin` 子命令创建；先建管理员或后建均可，导入会保留已有管理员。

只接受全新部署的库：Flyway 已迁移到快照记录的版本（49）且没有失败的迁移，`users` 中只有 `ROLE_ADMIN`，`homestays`、`orders`、`reviews`、`messages`、`notifications` 为空。条件不满足时整体回滚，提示“目标库不满足导入条件”。

快照对每张表先 `DELETE` 再写入（`users` 除外），以替换 B49 与应用启动时预置的房型、设施、系统配置等字典数据，使其与演示数据的 ID 一致；因此只能用于新库。检查、清理和写入都在同一事务内。

房源图片与头像使用外部 URL（Unsplash、DiceBear），需要网络才能显示。

### 重新生成快照（维护者）

`export_demo.py` 从开发库导出：只读源库，在同一 MySQL 服务器上创建临时库 `homestay_demo_export_*`，执行 [demo_sanitize.sql](demo_sanitize.sql) 脱敏并逐项核对，检查所有房源图片地址可访问，最后导出数据并删除临时库。

```bash
export MYSQL_DATABASE=homestay_db   # 源库
export MYSQL_USER=root              # 需要建库、删库权限
python3 tools/database/export_demo.py
```

脱敏规则：删除管理员和旧 `admin` 表；所有密码改为 `123456`；手机号改为不存在的 `100` 号段；邮箱改为 `用户名@example.com`；清除身份证号、身份证照片和重置令牌；替换门锁密码；不导出登录日志、操作日志与 Flyway 历史，审核日志去掉 IP 和 UA。任何一项核对不通过，或有图片地址不可用，都会中止导出。

快照头部记录导出时源库的 Flyway 版本（当前快照为 49）。导入要求目标库已迁移到该版本，之后新增的迁移不影响导入；快照使用带列名的 `INSERT`，如果后续迁移删除或改名了快照用到的列，导入会报错并整体回滚，此时需要在已迁移的开发库上重新导出。`auth_sessions` 等登录会话表不会导出。

在 WSL 中连接 Windows 上的 MySQL 时，可传入 Windows 客户端，并用 `WSLENV` 把密码环境变量传给它：

```bash
export WSLENV=MYSQL_PWD
python3 tools/database/export_demo.py \
  --mysql-command /mnt/d/mysql-8.0.32-winx64/bin/mysql.exe \
  --mysqldump-command /mnt/d/mysql-8.0.32-winx64/bin/mysqldump.exe
```

### 修复房源区县（已导入旧快照的库）

2026-11-16 之前的快照里，房源区县是生成的假值（如“宁波市区”、编码 `33020001`），与级联选择器的真实编码不一致，按区筛选和按区名搜索都查不到。当前快照已修正。已经导入过旧快照的库，用 `fix_districts.py` 生成按 id 更新的 SQL：

```bash
mysql -N -B homestay_db -e "SELECT id, province_code, city_code, district_code FROM homestays ORDER BY id" > rows.tsv
python3 tools/database/fix_districts.py --input rows.tsv --output fix_districts.sql --cache-dir /tmp/datav-cache
mysql homestay_db < fix_districts.sql
```

- 区县取自后端同一份 `china-pca-code.json`，按房源 id 确定性分配（市辖区权重更高，开发区类统计编码不参与）；没有区县的地级市（东莞、中山、儋州、嘉峪关）用乡镇街道。
- 坐标移到所分配区县的边界内，边界来自阿里云 DataV 公开 GeoJSON（首次运行下载约 660 个文件，较慢，之后读缓存）；拿不到区县边界时用城市边界。
- 把县级市编码存成城市编码的行（大理市 `532901`）归到上级城市“大理白族自治州”。
- 每条 UPDATE 带旧的 8 位假编码作条件，已修复的行不会再改；整份 SQL 在一个事务里。

## 使用 Docker 中的 mysql 客户端

保持上面的 `MYSQL_*` 环境变量。在仓库根目录运行，容器内主机一般仍为 `127.0.0.1`、端口为 `3306`；宿主机映射端口不适用于容器内连接。

```bash
tools/database/.venv/bin/python tools/database/import_data.py --mysql-command 'docker compose exec -T -e MYSQL_PWD mysql mysql' admin --username admin --email admin@homestay.local
python3 tools/database/import_data.py --mysql-command 'docker compose exec -T -e MYSQL_PWD mysql mysql' demo
```

`-e MYSQL_PWD` 将脚本设置的密码环境变量传入容器，不带值，密码不出现在参数中。`--mysql-command` 必须放在 `admin`/`demo` 之前；也可提供本机客户端路径。命令按引号拆分，不通过 shell 执行。

## 查看参数与离线验证

```bash
python3 tools/database/import_data.py --help
python3 tools/database/import_data.py admin --help
python3 -m unittest discover -s tools/database -p 'test_*.py' -v
```

离线测试使用假的 mysql 客户端，覆盖密码传递、哈希校验、插入失败处理及模式选择，不连接任何数据库。真实事务与约束行为应在独立 MySQL 测试实例验证。

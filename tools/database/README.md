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

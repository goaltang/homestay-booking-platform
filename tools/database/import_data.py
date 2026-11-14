#!/usr/bin/env python3
"""Explicit, insert-only data imports for an initialized MySQL 8 database."""

import argparse
import getpass
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys


DEMO_FILE = Path(__file__).with_name("demo_data.sql")
BCRYPT = re.compile(r"\$2[aby]\$(?:0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}")


def sql_text(value):
    # Hex literals avoid dependence on the connection's string escaping mode.
    return "CONVERT(X'" + value.encode("utf-8").hex() + "' USING utf8mb4)"


def admin_sql(username, email, password_hash):
    if not username or len(username) > 50:
        raise ValueError("管理员用户名长度必须为 1–50 个字符")
    if not email or len(email) > 255 or "@" not in email:
        raise ValueError("请提供有效的管理员邮箱（最长 255 个字符）")
    if not BCRYPT.fullmatch(password_hash):
        raise ValueError("ADMIN_PASSWORD_HASH 必须是完整的 BCrypt 哈希（$2a$/$2b$/$2y$）")
    return f"""START TRANSACTION;
INSERT INTO users (username, email, password, role, enabled,
                   verification_status, created_at, updated_at)
VALUES ({sql_text(username)}, {sql_text(email)}, {sql_text(password_hash)},
        'ROLE_ADMIN', 1, 'UNVERIFIED', NOW(), NOW());
COMMIT;
"""


def administrator_password_hash(environ):
    existing_hash = environ.get("ADMIN_PASSWORD_HASH")
    if existing_hash:
        return existing_hash
    try:
        import bcrypt
    except ImportError as exc:
        raise ValueError("管理员交互创建需要 bcrypt；请先安装 tools/database/requirements.txt，"
                         "或提供 ADMIN_PASSWORD_HASH") from exc
    password = getpass.getpass("新管理员密码：")
    if not password or len(password.encode("utf-8")) > 72:
        raise ValueError("管理员密码不能为空，UTF-8 编码长度不能超过 BCrypt 的 72 字节限制")
    if password != getpass.getpass("再次输入新管理员密码："):
        raise ValueError("两次输入的管理员密码不一致")
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=10)).decode("ascii")


def demo_sql():
    return DEMO_FILE.read_text(encoding="utf-8")


def mysql_command(command, environ):
    tokens = shlex.split(command)
    if not tokens:
        raise ValueError("--mysql-command 不能为空")
    # mysql must stop on an error so disconnect rolls back any open transaction.
    if any(token == "--force" or token == "-f" or
           token.startswith("--password") or
           (token.startswith("-p") and not token.startswith("--"))
           for token in tokens):
        raise ValueError("mysql 命令不得包含 --force/-f 或密码参数；密码使用 MYSQL_PASSWORD")
    database = environ.get("MYSQL_DATABASE", "")
    user = environ.get("MYSQL_USER", "")
    if not database or not user:
        raise ValueError("必须明确设置 MYSQL_DATABASE 和 MYSQL_USER")
    try:
        port = int(environ.get("MYSQL_PORT", "3306"))
    except ValueError as exc:
        raise ValueError("MYSQL_PORT 必须是 1–65535 的整数") from exc
    if not 1 <= port <= 65535:
        raise ValueError("MYSQL_PORT 必须是 1–65535 的整数")
    return tokens + ["--protocol=TCP", "--default-character-set=utf8mb4", "--batch", "--skip-force",
                     "--host=" + environ.get("MYSQL_HOST", "127.0.0.1"),
                     "--port=" + str(port), "--user=" + user, "--database=" + database]


def run_import(command, sql, environ):
    child_env = dict(environ)
    password = child_env.pop("MYSQL_PASSWORD", None)
    admin_hash = child_env.pop("ADMIN_PASSWORD_HASH", "")
    if password is None:
        password = getpass.getpass("MySQL 密码：")
    child_env["MYSQL_PWD"] = password
    result = subprocess.run(command, input=sql, text=True, encoding="utf-8",
                            capture_output=True, env=child_env, check=False)
    if result.returncode:
        message = result.stderr.strip() or "mysql 客户端返回错误"
        for secret in (password, admin_hash):
            if secret:
                message = message.replace(secret, "[REDACTED]")
        raise RuntimeError("导入失败，未提交事务：" + message)


def parser():
    cli = argparse.ArgumentParser(
        description="显式导入管理员或演示数据；只插入，不覆盖已有记录。先完成表结构初始化并停止后端。",
        epilog="连接：MYSQL_HOST（默认127.0.0.1）、MYSQL_PORT（默认3306）、"
               "MYSQL_DATABASE、MYSQL_USER、MYSQL_PASSWORD（未设置则安全提示输入）。"
               "admin 默认安全提示输入新密码（需 bcrypt），或读取 ADMIN_PASSWORD_HASH；无默认密码。")
    cli.add_argument("--mysql-command", default="mysql", metavar="COMMAND",
                     help="mysql 客户端命令，按 shell 引号拆分但不经过 shell 执行（默认 mysql）")
    sub = cli.add_subparsers(dest="mode", required=True)
    admin = sub.add_parser("admin", help="在 users 中创建一个 ROLE_ADMIN 账号，冲突即失败")
    admin.add_argument("--username", required=True)
    admin.add_argument("--email", required=True)
    sub.add_parser("demo", help="仅在业务表为空时导入 3 个演示用户、5 个房源与图片，保留管理员")
    return cli


def main(argv=None):
    cli = parser()
    args = cli.parse_args(argv)
    try:
        command = mysql_command(args.mysql_command, os.environ)
        if args.mode == "admin":
            sql = admin_sql(args.username, args.email, administrator_password_hash(os.environ))
        else:
            sql = demo_sql()
        # Strict mode makes the demo's unsigned sentinel fail instead of clipping -1 to 0.
        sql = "SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION';\n" + sql
        run_import(command, sql, os.environ)
    except (ValueError, RuntimeError, OSError, EOFError) as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print("导入已提交：" + args.mode)
    return 0


if __name__ == "__main__":
    sys.exit(main())

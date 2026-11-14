#!/usr/bin/env python3
"""Export sanitized demo data from a development database.

The source database is only read. The data is copied into a temporary database on the
same server, sanitized there with demo_sanitize.sql, verified, dumped (data only) into
demo_full.sql.gz, and the temporary database is dropped.
"""

import argparse
import datetime
import getpass
import gzip
import os
from pathlib import Path
import re
import secrets
import shlex
import subprocess
import sys
import urllib.error
import urllib.request

HERE = Path(__file__).resolve().parent
SANITIZE_FILE = HERE / "demo_sanitize.sql"
DEFAULT_OUTPUT = HERE / "demo_full.sql.gz"
MIN_FLYWAY_VERSION = (49,)
TEMP_PREFIX = "homestay_demo_export_"
DEMO_PASSWORD_HASH = "$2a$10$oMeaCXPR2aItuzgcxsCEhOE1GWjakAlO87xZyyTjmfzFaTrZpDsD6"
# Not exported: schema history belongs to the target's own Flyway run; logs are access traces;
# auth_sessions holds live login credentials.
EXCLUDED_TABLES = frozenset({"flyway_schema_history", "login_log", "operation_log", "admin", "auth_sessions"})
# Imported without a DELETE so administrators created on the target are kept.
KEEP_EXISTING_ROWS = frozenset({"users"})
IDENTIFIER = re.compile(r"[A-Za-z0-9_]+")

# Each query must return 0 after sanitizing; otherwise the export stops.
POSTCONDITIONS = {
    "仍有管理员账号": "SELECT COUNT(*) FROM users WHERE role = 'ROLE_ADMIN'",
    "存在非演示密码": f"SELECT COUNT(*) FROM users WHERE password <> '{DEMO_PASSWORD_HASH}'",
    "身份证或重置令牌未清除": "SELECT COUNT(*) FROM users WHERE id_card IS NOT NULL OR id_card_front IS NOT NULL "
                    "OR id_card_back IS NOT NULL OR reset_token IS NOT NULL",
    "用户手机号未替换": "SELECT COUNT(*) FROM users WHERE phone IS NOT NULL AND phone <> '' AND phone NOT LIKE '100%'",
    "邮箱不是 example.com": "SELECT COUNT(*) FROM users WHERE email NOT LIKE '%@example.com'",
    "订单手机号未替换": "SELECT COUNT(*) FROM orders WHERE guest_phone IS NOT NULL AND guest_phone <> '' "
                "AND guest_phone NOT LIKE '100%'",
    "旧管理员表未清空": "SELECT COUNT(*) FROM admin",
    "审核日志仍含 IP 或 UA": "SELECT COUNT(*) FROM homestay_audit_logs WHERE ip_address IS NOT NULL OR user_agent IS NOT NULL",
}
IMAGE_URL_QUERY = ("SELECT DISTINCT cover_image FROM homestays WHERE cover_image IS NOT NULL AND cover_image <> '' "
                   "UNION SELECT DISTINCT image_url FROM homestay_images WHERE image_url IS NOT NULL AND image_url <> ''")


def client_command(command, environ, database=None, extra=()):
    tokens = shlex.split(command)
    if not tokens:
        raise ValueError("客户端命令不能为空")
    if any(token == "--force" or token == "-f" or token.startswith("--password") or
           (token.startswith("-p") and not token.startswith("--")) for token in tokens):
        raise ValueError("客户端命令不得包含 --force/-f 或密码参数；密码使用 MYSQL_PASSWORD")
    user = environ.get("MYSQL_USER", "")
    if not user:
        raise ValueError("必须明确设置 MYSQL_USER")
    try:
        port = int(environ.get("MYSQL_PORT", "3306"))
    except ValueError as exc:
        raise ValueError("MYSQL_PORT 必须是 1–65535 的整数") from exc
    if not 1 <= port <= 65535:
        raise ValueError("MYSQL_PORT 必须是 1–65535 的整数")
    result = tokens + ["--protocol=TCP", "--default-character-set=utf8mb4",
                       "--host=" + environ.get("MYSQL_HOST", "127.0.0.1"),
                       "--port=" + str(port), "--user=" + user]
    result += list(extra)
    if database:
        result.append(database)
    return result


def check_identifier(name):
    if not IDENTIFIER.fullmatch(name or ""):
        raise ValueError("数据库或表名只能包含字母、数字和下划线")
    return name


class Exporter:
    def __init__(self, mysql, mysqldump, environ, password):
        self.mysql = mysql
        self.mysqldump = mysqldump
        self.environ = environ
        self.password = password
        self.env = {k: v for k, v in environ.items() if k != "MYSQL_PASSWORD"}
        self.env["MYSQL_PWD"] = password

    def _fail(self, what, result):
        message = (result.stderr or "").strip() or "客户端返回错误"
        if self.password:
            message = message.replace(self.password, "[REDACTED]")
        raise RuntimeError(what + "：" + message)

    def execute(self, sql, database=None):
        command = client_command(self.mysql, self.environ, database, ["--batch", "--skip-force"])
        result = subprocess.run(command, input=sql, text=True, encoding="utf-8",
                                capture_output=True, env=self.env, check=False)
        if result.returncode:
            self._fail("SQL 执行失败", result)

    def query(self, sql, database=None):
        command = client_command(self.mysql, self.environ, database,
                                 ["--batch", "--skip-column-names", "--raw", "-e", sql])
        result = subprocess.run(command, text=True, encoding="utf-8",
                                capture_output=True, env=self.env, check=False)
        if result.returncode:
            self._fail("查询失败", result)
        return [line.rstrip("\r").split("\t") for line in result.stdout.splitlines() if line.strip()]

    def scalar(self, sql, database=None):
        rows = self.query(sql, database)
        return int(rows[0][0])

    def base_tables(self, database):
        rows = self.query("SELECT table_name FROM information_schema.tables WHERE table_schema = '"
                          + check_identifier(database) + "' AND table_type = 'BASE TABLE' ORDER BY table_name")
        return [check_identifier(row[0]) for row in rows]

    def dump(self, database, tables, extra, stdout):
        command = client_command(self.mysqldump, self.environ, None,
                                 ["--single-transaction", "--skip-triggers", "--no-tablespaces",
                                  "--set-gtid-purged=OFF", "--hex-blob", "--skip-tz-utc", *extra, database, *tables])
        return subprocess.Popen(command, stdout=stdout, stderr=subprocess.PIPE, env=self.env)


def flyway_version(exporter, database):
    rows = exporter.query("SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL", database)
    versions = [row[0] for row in rows]
    return max(versions, key=lambda v: tuple(int(p) for p in v.split("."))) if versions else None


def copy_tables(exporter, source, target, tables):
    """Copy table structure and data from source to target through mysqldump | mysql."""
    load = client_command(exporter.mysql, exporter.environ, target, ["--batch", "--skip-force"])
    dump = exporter.dump(source, tables, [], subprocess.PIPE)
    loader = subprocess.run(load, stdin=dump.stdout, capture_output=True, env=exporter.env, check=False)
    dump.stdout.close()
    dump_err = dump.stderr.read().decode("utf-8", "replace")
    if dump.wait() or loader.returncode:
        message = dump_err.strip() or loader.stderr.decode("utf-8", "replace").strip()
        raise RuntimeError("复制到临时库失败：" + message.replace(exporter.password or "\0", "[REDACTED]"))


def verify_postconditions(exporter, database):
    problems = [label for label, sql in POSTCONDITIONS.items() if exporter.scalar(sql, database)]
    for table in ("users", "homestays", "orders"):
        if exporter.scalar(f"SELECT COUNT(*) FROM `{table}`", database) == 0:
            problems.append(f"{table} 为空")
    if problems:
        raise RuntimeError("脱敏结果校验未通过：" + "；".join(problems))


def check_images(urls, opener=urllib.request.urlopen, timeout=20):
    """Return the URLs that are not reachable http(s) images."""
    broken = []
    for url in urls:
        if not url.startswith(("http://", "https://")):
            broken.append(url)  # 本地上传文件不会随演示数据分发
            continue
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "homestay-demo-export"})
            with opener(request, timeout=timeout) as response:
                if response.status != 200:
                    broken.append(url)
        except (urllib.error.URLError, OSError, ValueError):
            broken.append(url)
    return broken


def write_output(exporter, database, tables, output, source_version):
    header = (
        "-- homestay3 演示数据（已脱敏），由 tools/database/export_demo.py 生成。\n"
        "-- 只能通过 tools/database/import_data.py demo-full 导入全新的空库；演示账号密码均为 123456。\n"
        f"-- flyway_version: {source_version}\n"
        f"-- generated: {datetime.date.today().isoformat()}\n"
        "SET NAMES utf8mb4;\n"
    )
    deletes = "".join(f"DELETE FROM `{t}`;\n" for t in tables if t not in KEEP_EXISTING_ROWS)
    tmp = output.with_suffix(output.suffix + ".tmp")
    with gzip.open(tmp, "wb", compresslevel=9) as gz:
        gz.write((header + deletes).encode("utf-8"))
        dump = exporter.dump(database, tables,
                             ["--compact", "--no-create-info", "--complete-insert"], subprocess.PIPE)
        for chunk in iter(lambda: dump.stdout.read(1 << 20), b""):
            gz.write(chunk)
        dump.stdout.close()
        err = dump.stderr.read().decode("utf-8", "replace")
        if dump.wait():
            tmp.unlink(missing_ok=True)
            raise RuntimeError("导出数据失败：" + err.strip().replace(exporter.password or "\0", "[REDACTED]"))
    tmp.replace(output)


def export(exporter, source, output, check_image_urls=True, log=print):
    check_identifier(source)
    version = flyway_version(exporter, source)
    if version is None or tuple(int(p) for p in version.split(".")) < MIN_FLYWAY_VERSION:
        raise RuntimeError(f"源库 Flyway 版本为 {version}，需要不低于 B49 基线；先让源库与仓库迁移一致")
    tables = [t for t in exporter.base_tables(source) if t not in EXCLUDED_TABLES]
    temp = TEMP_PREFIX + secrets.token_hex(6)
    exporter.execute(f"CREATE DATABASE `{temp}` CHARACTER SET utf8mb4;")
    try:
        log(f"1/4 复制 {len(tables)} 张表到临时库 {temp}")
        copy_tables(exporter, source, temp, tables + sorted(EXCLUDED_TABLES & set(exporter.base_tables(source))))
        log("2/4 执行脱敏并校验")
        exporter.execute(SANITIZE_FILE.read_text(encoding="utf-8"), temp)
        verify_postconditions(exporter, temp)
        if check_image_urls:
            urls = [row[0] for row in exporter.query(IMAGE_URL_QUERY, temp)]
            log(f"3/4 检查 {len(urls)} 个房源图片地址")
            broken = check_images(urls)
            if broken:
                raise RuntimeError(f"{len(broken)} 个图片地址不可用，示例：" + "，".join(broken[:3]))
        else:
            log("3/4 已按参数跳过图片检查")
        log(f"4/4 写入 {output}")
        write_output(exporter, temp, tables, output, version)
    finally:
        exporter.execute(f"DROP DATABASE IF EXISTS `{temp}`;")


def parser():
    cli = argparse.ArgumentParser(
        description="从开发库导出脱敏演示数据：只读源库，在同一服务器的临时库中脱敏，导出后删除临时库。",
        epilog="连接：MYSQL_HOST（默认127.0.0.1）、MYSQL_PORT（默认3306）、MYSQL_DATABASE（源库）、"
               "MYSQL_USER、MYSQL_PASSWORD（未设置则安全提示输入）。账号需能创建和删除数据库。")
    cli.add_argument("--mysql-command", default="mysql", metavar="COMMAND",
                     help="mysql 客户端命令，按 shell 引号拆分但不经过 shell 执行（默认 mysql）")
    cli.add_argument("--mysqldump-command", default="mysqldump", metavar="COMMAND",
                     help="mysqldump 命令（默认 mysqldump）")
    cli.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="输出文件（默认 tools/database/demo_full.sql.gz）")
    cli.add_argument("--skip-image-check", action="store_true", help="跳过房源图片地址可用性检查（不建议）")
    return cli


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        source = os.environ.get("MYSQL_DATABASE", "")
        if not source:
            raise ValueError("必须明确设置 MYSQL_DATABASE（源库）")
        if source.startswith(TEMP_PREFIX):
            raise ValueError("源库不能是导出临时库")
        password = os.environ.get("MYSQL_PASSWORD")
        if password is None:
            password = getpass.getpass("MySQL 密码：")
        exporter = Exporter(args.mysql_command, args.mysqldump_command, os.environ, password)
        export(exporter, source, args.output, check_image_urls=not args.skip_image_check)
    except (ValueError, RuntimeError, OSError, EOFError) as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print("导出完成：" + str(args.output))
    return 0


if __name__ == "__main__":
    sys.exit(main())

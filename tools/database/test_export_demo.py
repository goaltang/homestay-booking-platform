"""Offline tests for export_demo.py and the demo-full import: fake clients only, no database."""

import gzip
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import export_demo
import import_data

CONFIG = {"MYSQL_USER": "test_user"}


class FakeResponse:
    def __init__(self, status):
        self.status = status

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


class ExportTests(unittest.TestCase):
    def test_client_command_rejects_inline_passwords_and_force(self):
        for command in ("mysql -psecret", "mysql --password=secret", "mysql --force", "mysql -f", ""):
            with self.subTest(command=command), self.assertRaises(ValueError):
                export_demo.client_command(command, CONFIG)

    def test_client_command_appends_connection_and_database(self):
        command = export_demo.client_command("'/mnt/d/My SQL/mysql.exe'", {**CONFIG, "MYSQL_PORT": "13316"},
                                             "demo_db", ["--batch"])
        self.assertEqual("/mnt/d/My SQL/mysql.exe", command[0])
        self.assertIn("--port=13316", command)
        self.assertEqual(["--batch", "demo_db"], command[-2:])
        self.assertFalse(any("password" in token for token in command))

    def test_identifiers_are_restricted(self):
        self.assertEqual("homestay_db", export_demo.check_identifier("homestay_db"))
        for name in ("db`; DROP DATABASE x; --", "a b", "", None):
            with self.subTest(name=name), self.assertRaises(ValueError):
                export_demo.check_identifier(name)

    def test_image_check_reports_local_unreachable_and_non_200(self):
        def opener(request, timeout):
            if "missing" in request.full_url:
                return FakeResponse(404)
            if "down" in request.full_url:
                raise OSError("connection refused")
            return FakeResponse(200)

        urls = ["https://img.test/ok.jpg", "https://img.test/missing.jpg",
                "https://down.test/a.jpg", "/uploads/homestay/local.jpg"]
        broken = export_demo.check_images(urls, opener=opener)
        self.assertEqual(urls[1:], broken)

    def test_postconditions_stop_export_when_anything_remains(self):
        class FakeExporter:
            def __init__(self, failing):
                self.failing = failing

            def scalar(self, sql, database=None):
                if sql in self.failing:
                    return 1
                return 5 if sql.startswith("SELECT COUNT(*) FROM `") else 0

        export_demo.verify_postconditions(FakeExporter(set()), "tmp")
        admin_check = export_demo.POSTCONDITIONS["仍有管理员账号"]
        with self.assertRaisesRegex(RuntimeError, "仍有管理员账号"):
            export_demo.verify_postconditions(FakeExporter({admin_check}), "tmp")

    def test_temporary_database_is_never_the_source(self):
        env = {**CONFIG, "MYSQL_DATABASE": export_demo.TEMP_PREFIX + "abc", "MYSQL_PASSWORD": "x"}
        with patch.dict(os.environ, env, clear=True), patch.object(export_demo, "export") as run, \
                patch("sys.stderr"):
            self.assertEqual(1, export_demo.main([]))
            run.assert_not_called()

    def test_users_are_never_deleted_by_the_snapshot(self):
        self.assertIn("users", export_demo.KEEP_EXISTING_ROWS)
        self.assertTrue({"flyway_schema_history", "login_log", "operation_log", "admin", "auth_sessions"}
                        <= export_demo.EXCLUDED_TABLES)

    def test_sanitize_script_covers_credentials_and_contact_data(self):
        sql = export_demo.SANITIZE_FILE.read_text(encoding="utf-8")
        for fragment in ("DELETE FROM users WHERE role = 'ROLE_ADMIN'", "id_card = NULL", "reset_token = NULL",
                         export_demo.DEMO_PASSWORD_HASH, "guest_phone", "DELETE FROM login_log"):
            self.assertIn(fragment, sql)


class DemoFullImportTests(unittest.TestCase):
    def snapshot(self, text):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "demo_full.sql.gz"
        with gzip.open(path, "wt", encoding="utf-8") as out:
            out.write(text)
        return path

    def test_snapshot_is_wrapped_in_guard_and_single_transaction(self):
        path = self.snapshot("-- flyway_version: 49\nDELETE FROM `amenities`;\nINSERT INTO `users` VALUES (95);\n")
        sql = import_data.demo_full_sql(path)
        guard = sql.index("INSERT INTO demo_full_requires_fresh_database")
        self.assertLess(sql.index("START TRANSACTION;"), guard)
        self.assertLess(guard, sql.index("DELETE FROM `amenities`"))
        self.assertIn("version = '49'", sql)
        self.assertTrue(sql.endswith("COMMIT;\n"))
        self.assertEqual(1, sql.count("START TRANSACTION;"))

    def test_snapshot_with_wrong_version_or_schema_statements_is_rejected(self):
        for text in ("-- flyway_version: 48\nINSERT INTO `users` VALUES (1);\n",
                     "-- flyway_version: 49\nDROP TABLE users;\n",
                     "-- flyway_version: 49\ncreate table x (id int);\n",
                     "-- flyway_version: 49\nUSE other_db;\n"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                import_data.demo_full_sql(self.snapshot(text))

    def test_later_migrations_on_target_do_not_block_import(self):
        sql = import_data.demo_full_sql(self.snapshot("-- flyway_version: 49\nINSERT INTO `amenities` VALUES (1);\n"))
        guard = sql[:sql.index("START TRANSACTION;") + 400]
        self.assertIn("version = '49'", sql)
        self.assertIn("success = 0", sql)
        self.assertNotIn("> 49", guard)

    def test_snapshot_from_newer_source_requires_that_version(self):
        sql = import_data.demo_full_sql(self.snapshot("-- flyway_version: 50\nINSERT INTO `amenities` VALUES (1);\n"))
        self.assertIn("version = '50'", sql)

    def test_missing_snapshot_is_reported(self):
        with self.assertRaisesRegex(ValueError, "export_demo.py"):
            import_data.demo_full_sql(Path(tempfile.gettempdir()) / "definitely_missing_demo_full.sql.gz")

    def test_guard_failure_is_explained(self):
        failure = type("Result", (), {"returncode": 1, "stderr": "ERROR 1264 (22003) at line 6: "
                                      "Out of range value for column 'empty_only' at row 1"})()
        with patch.object(import_data.subprocess, "run", return_value=failure):
            with self.assertRaisesRegex(RuntimeError, "业务表必须为空"):
                import_data.run_import(["mysql"], "SELECT 1;", {"MYSQL_PASSWORD": "pw"})

    def test_repository_snapshot_passes_import_checks(self):
        if not import_data.DEMO_FULL_FILE.exists():
            self.skipTest("demo_full.sql.gz 尚未生成")
        import_data.demo_full_sql()  # 版本标记与“只含数据语句”检查
        with gzip.open(import_data.DEMO_FULL_FILE, "rt", encoding="utf-8") as source:
            body = source.read()
        self.assertNotIn("'ROLE_ADMIN'", body)
        self.assertNotIn("DELETE FROM `users`", body)


if __name__ == "__main__":
    unittest.main()

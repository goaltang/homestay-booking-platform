"""Offline regression tests: fake client only, never connect to a database."""

import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

import import_data


HASH = "$2a$10$oMeaCXPR2aItuzgcxsCEhOE1GWjakAlO87xZyyTjmfzFaTrZpDsD6"
CONFIG = {"MYSQL_DATABASE": "isolated_test", "MYSQL_USER": "test_user"}


class ImportTests(unittest.TestCase):
    def test_admin_insert_quotes_untrusted_values_and_never_overwrites(self):
        username = "admin'); DELETE FROM users; --"
        sql = import_data.admin_sql(username, "a'b@test.local", HASH)
        self.assertNotIn(username, sql)
        self.assertIn(username.encode().hex(), sql)
        self.assertIn("'ROLE_ADMIN'", sql)
        self.assertNotIn("UPDATE", sql)
        self.assertNotIn("IGNORE", sql)
        self.assertTrue(sql.startswith("START TRANSACTION;"))
        self.assertTrue(sql.endswith("COMMIT;\n"))

    def test_plaintext_or_incomplete_hash_is_rejected_before_client_runs(self):
        for password in ("admin888", HASH[:-1], "$2x$" + HASH[4:]):
            with self.subTest(password=password):
                with patch.dict(os.environ, {**CONFIG, "ADMIN_PASSWORD_HASH": password}, clear=True), \
                     patch.object(import_data, "run_import") as client, \
                     contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(1, import_data.main(["admin", "--username", "admin",
                                                         "--email", "admin@test.local"]))
                    client.assert_not_called()

    def test_interactive_admin_hashes_confirmed_password(self):
        bcrypt = types.SimpleNamespace(hashpw=lambda password, salt: HASH.encode(),
                                       gensalt=lambda rounds: b"test_salt")
        with patch.dict(sys.modules, {"bcrypt": bcrypt}), \
             patch.object(import_data.getpass, "getpass", side_effect=["my-new-password", "my-new-password"]):
            self.assertEqual(HASH, import_data.administrator_password_hash({}))

    def test_admin_password_mismatch_empty_or_too_long_is_rejected(self):
        bcrypt = types.SimpleNamespace()
        for passwords in (["one", "two"], [""], ["汉" * 25]):
            with self.subTest(passwords=passwords), patch.dict(sys.modules, {"bcrypt": bcrypt}), \
                 patch.object(import_data.getpass, "getpass", side_effect=passwords):
                with self.assertRaises(ValueError):
                    import_data.administrator_password_hash({})

    def test_existing_hash_does_not_prompt_for_password(self):
        with patch.object(import_data.getpass, "getpass") as prompt:
            self.assertEqual(HASH, import_data.administrator_password_hash({"ADMIN_PASSWORD_HASH": HASH}))
            prompt.assert_not_called()

    def test_connection_requires_explicit_database_and_user(self):
        for config in ({}, {"MYSQL_DATABASE": "test"}, {"MYSQL_USER": "test"}):
            with self.subTest(config=config), self.assertRaises(ValueError):
                import_data.mysql_command("mysql", config)

    def test_docker_command_passes_environment_name_and_connection_flags(self):
        command = import_data.mysql_command(
            "docker compose exec -T -e MYSQL_PWD mysql mysql", CONFIG)
        self.assertEqual(["docker", "compose", "exec", "-T", "-e", "MYSQL_PWD", "mysql", "mysql"], command[:8])
        self.assertIn("--database=isolated_test", command)
        self.assertIn("--protocol=TCP", command)
        self.assertIn("--skip-force", command)

    def test_unsafe_client_options_are_rejected(self):
        for option in ("--force", "-f", "-psecret", "--password=secret"):
            with self.subTest(option=option), self.assertRaises(ValueError):
                import_data.mysql_command("mysql " + option, CONFIG)

    def test_client_receives_sql_stdin_and_password_env_without_argv_or_parent_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            fake = Path(directory) / "fake_mysql.py"
            output = Path(directory) / "received.json"
            fake.write_text(
                "import json, os, sys\n"
                "from pathlib import Path\n"
                "Path(os.environ['CAPTURE']).write_text(json.dumps({"
                "'argv':sys.argv[1:], 'sql':sys.stdin.read(), "
                "'password':os.environ.get('MYSQL_PWD'), "
                "'admin_hash':os.environ.get('ADMIN_PASSWORD_HASH'), "
                "'mysql_password':os.environ.get('MYSQL_PASSWORD')}))\n")
            environ = {**os.environ, **CONFIG, "CAPTURE": str(output),
                       "MYSQL_PASSWORD": "secret-for-test", "ADMIN_PASSWORD_HASH": HASH}
            sql = import_data.admin_sql("admin", "admin@test.local", HASH)
            import_data.run_import([sys.executable, str(fake)], sql, environ)
            received = json.loads(output.read_text())
            self.assertEqual(sql, received["sql"])
            self.assertEqual("secret-for-test", received["password"])
            self.assertNotIn("secret-for-test", received["argv"])
            self.assertIsNone(received["admin_hash"])
            self.assertIsNone(received["mysql_password"])
            self.assertEqual("secret-for-test", environ["MYSQL_PASSWORD"])

    def test_client_failure_is_reported_and_redacts_secrets(self):
        environ = {**CONFIG, "MYSQL_PASSWORD": "sensitive", "ADMIN_PASSWORD_HASH": HASH}
        failure = subprocess.CompletedProcess(["mysql"], 1, "", "failed sensitive " + HASH)
        with patch.object(import_data.subprocess, "run", return_value=failure):
            with self.assertRaises(RuntimeError) as error:
                import_data.run_import(["mysql"], "START TRANSACTION;", environ)
        self.assertNotIn("sensitive", str(error.exception))
        self.assertNotIn(HASH, str(error.exception))

    def test_mysql_password_prompt_and_demo_mode_use_strict_single_batch(self):
        with patch.dict(os.environ, CONFIG, clear=True), \
             patch.object(import_data.getpass, "getpass", return_value="prompted-secret"), \
             patch.object(import_data.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "", "")) as client, \
             contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, import_data.main(["demo"]))
            _, kwargs = client.call_args
            self.assertEqual("prompted-secret", kwargs["env"]["MYSQL_PWD"])
            self.assertIn("STRICT_ALL_TABLES", kwargs["input"])
            self.assertIn("demo_requires_empty_business_tables", kwargs["input"])
            self.assertEqual(1, client.call_count)


if __name__ == "__main__":
    unittest.main()

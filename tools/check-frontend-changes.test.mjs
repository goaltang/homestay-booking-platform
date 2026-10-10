import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const script = fileURLToPath(new URL("./check-frontend-changes.mjs", import.meta.url));

test("检查提交、暂存、未跟踪及空格路径，忽略删除和另一前端，传播失败", () => {
  const root = mkdtempSync(path.join(tmpdir(), "homestay-quality-"));
  try {
    const app = path.join(root, "homestay-admin");
    const bins = path.join(app, "node_modules/.bin");
    mkdirSync(bins, { recursive: true });
    writeFileSync(path.join(root, ".gitignore"), "node_modules/\ncalls.jsonl\n");
    const git = (...args) => execFileSync("git", args, { cwd: root, encoding: "utf8" });
    git("init", "-q");
    git("config", "user.email", "test@example.invalid");
    git("config", "user.name", "Test");
    writeFileSync(path.join(app, "removed.ts"), "export {};\n");
    git("add", ".");
    git("commit", "-qm", "baseline");
    const base = git("rev-parse", "HEAD").trim();
    for (const bin of ["eslint", "prettier"]) {
      writeFileSync(
        path.join(bins, bin),
        `#!/usr/bin/env node
const fs = require('node:fs');
fs.appendFileSync('calls.jsonl', JSON.stringify({ bin: '${bin}', args: process.argv.slice(2) }) + '\\n');
process.exit(Number(process.env.QUALITY_TEST_EXIT || 0));
`,
        { mode: 0o755 }
      );
    }
    writeFileSync(path.join(app, "committed.ts"), "export {};\n");
    git("add", "homestay-admin/committed.ts");
    git("commit", "-qm", "change");
    writeFileSync(path.join(app, "staged.ts"), "export {};\n");
    git("add", "homestay-admin/staged.ts");
    writeFileSync(path.join(app, "新 文件.vue"), "<template />\n");
    writeFileSync(path.join(root, ".prettierrc.json"), "{}\n");
    mkdirSync(path.join(root, "homestay-front"));
    writeFileSync(path.join(root, "homestay-front/other.ts"), "export {};\n");
    rmSync(path.join(app, "removed.ts"));
    const run = (args = [], env = process.env) =>
      spawnSync(process.execPath, [script, ...args], {
        cwd: app,
        env,
        encoding: "utf8",
      });
    const result = run([base]);
    assert.equal(result.status, 0, result.stderr);
    const calls = readFileSync(path.join(app, "calls.jsonl"), "utf8")
      .trim()
      .split("\n")
      .map(JSON.parse);
    assert.equal(calls.length, 2);
    assert.ok(calls.find((call) => call.bin === "prettier").args.includes("../.prettierrc.json"));
    for (const call of calls) {
      assert.ok(call.args.includes("committed.ts"));
      assert.ok(call.args.includes("staged.ts"));
      assert.ok(call.args.includes("新 文件.vue"));
      assert.ok(!call.args.includes("removed.ts"));
      assert.ok(!call.args.includes("other.ts"));
    }
    assert.equal(run([], { ...process.env, QUALITY_TEST_EXIT: "2" }).status, 2);
    assert.notEqual(run(["nonexistent-commit"]).status, 0);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

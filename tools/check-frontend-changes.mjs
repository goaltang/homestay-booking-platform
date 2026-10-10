// CI 传入基准提交；本地默认检查 HEAD 后的修改和未跟踪文件。
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";

const root = execFileSync("git", ["rev-parse", "--show-toplevel"], { encoding: "utf8" }).trim();
const git = (...args) => execFileSync("git", args, { encoding: "utf8", cwd: root });
const app = path.relative(root, process.cwd()).replaceAll(path.sep, "/");
if (!["homestay-front", "homestay-admin"].includes(app)) {
  throw new Error("请在前端子项目目录运行 quality:changed");
}
const base = process.argv[2] || "HEAD";
// --name-only -z 保留空格、中文与换行，避免通过 shell 拼接文件名。
const names = [
  ...git("diff", "--name-only", "--diff-filter=ACMR", "-z", base, "--").split("\0"),
  ...git("ls-files", "--full-name", "--others", "--exclude-standard", "-z").split("\0"),
];
const files = [...new Set(names)]
  .filter((name) => name.startsWith(`${app}/`))
  .map((name) => name.slice(app.length + 1))
  .filter((name) => existsSync(name) && !/\.d\.ts$|\.tsbuildinfo$/.test(name));
const lint = files.filter((name) => /\.(vue|[cm]?ts|tsx|[cm]?js)$/.test(name));
const format = files.filter((name) =>
  /\.(vue|[cm]?[jt]s|tsx|json|css|scss|html|md|ya?ml)$/.test(name)
);
for (const name of [
  ".prettierrc.json",
  "tools/frontend-eslint-rules.mjs",
  "tools/check-frontend-changes.mjs",
  "tools/check-frontend-changes.test.mjs",
]) {
  if (names.includes(name) && existsSync(path.join(root, name))) format.push(`../${name}`);
}

function check(bin, args, targets) {
  if (!targets.length) return;
  const command = path.resolve("node_modules/.bin", bin);
  const result = spawnSync(command, [...args, "--", ...targets], { stdio: "inherit" });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status || 1);
}
console.log(`${app}: 检查 ${files.length} 个改动文件（基准 ${base}）`);
check("eslint", [], lint);
check("prettier", ["--check"], format);

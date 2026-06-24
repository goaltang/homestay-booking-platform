# =============================================================================
# 性能压测一键脚本
# =============================================================================
# 用法：
#   .\tools\perf\run-perf-test.ps1                 # 启动 + 灌数据 + 后端 + 压测 + 保留
#   .\tools\perf\run-perf-test.ps1 -SkipData       # 跳过灌数据
#   .\tools\perf\run-perf-test.ps1 -OnlyDown       # 仅销毁环境
#   .\tools\perf\run-perf-test.ps1 -OnlyUp         # 仅启动环境（不跑压测）
#
# 前置：
#   - Docker Desktop 已启动
#   - Maven 已安装（mvn --version）
#   - k6 已安装（k6 version），或用 winget install k6
# =============================================================================

param(
  [switch]$SkipData,
  [switch]$OnlyDown,
  [switch]$OnlyUp,
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot\..\..

# ---- 颜色输出 ----
function Write-Step($msg)  { Write-Host "[STEP]  $msg" -ForegroundColor Cyan }
function Write-Ok($msg)    { Write-Host "[OK]    $msg" -ForegroundColor Green }
function Write-Warn($msg)  { Write-Host "[WARN]  $msg" -ForegroundColor Yellow }
function Write-Err($msg)   { Write-Host "[ERROR] $msg" -ForegroundColor Red }

# ---- 1. 销毁环境 ----
function Remove-PerfEnv {
  Write-Step "销毁压测环境（连同数据卷）"
  docker compose -f docker-compose.perf.yml down -v 2>&1 | Out-Null
  if ($LASTEXITCODE -ne 0) {
    docker-compose -f docker-compose.perf.yml down -v 2>&1 | Out-Null
  }
  Write-Ok "环境已销毁，3307/6380 端口已释放"
}

# ---- 2. 启动环境 ----
function Start-PerfEnv {
  Write-Step "启动独立 MySQL (3307) + Redis (6380)"
  docker compose -f docker-compose.perf.yml up -d
  if ($LASTEXITCODE -ne 0) {
    throw "docker compose 启动失败"
  }

  Write-Step "等待 MySQL 就绪（最多 60s）"
  $ready = $false
  for ($i = 0; $i -lt 30; $i++) {
    $out = docker exec homestay-mysql-perf mysqladmin ping -h 127.0.0.1 -uperf_user -pperf_pwd 2>&1
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
  }
  if (-not $ready) { throw "MySQL 启动超时" }
  Write-Ok "MySQL 已就绪"

  Write-Step "等待 Redis 就绪（最多 30s）"
  $ready = $false
  for ($i = 0; $i -lt 15; $i++) {
    $out = docker exec homestay-redis-perf redis-cli ping 2>&1
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
  }
  if (-not $ready) { throw "Redis 启动超时" }
  Write-Ok "Redis 已就绪"
}

# ---- 3. 灌入压测数据 ----
function Init-PerfData {
  Write-Step "初始化压测数据（约 2-5 分钟）"
  Get-Content -Raw -LiteralPath "tools\perf\data\init-perf-data.sql" | `
    docker exec -i homestay-mysql-perf mysql -uperf_user -pperf_pwd homestay_perf
  if ($LASTEXITCODE -ne 0) { throw "数据初始化失败" }
  Write-Ok "数据初始化完成"
}

# ---- 4. 启动后端 ----
function Start-Backend {
  Write-Step "启动后端（perf profile，端口 8080）"

  if (-not $SkipBuild) {
    Write-Step "Maven 编译（首次约 1-2 分钟）"
    Push-Location -LiteralPath "homestay-backend"
    mvn -q -DskipTests package
    Pop-Location
    if ($LASTEXITCODE -ne 0) { throw "Maven 编译失败" }
  }

  $jar = Get-ChildItem -LiteralPath "homestay-backend\target" -Filter "*.jar" -ErrorAction SilentlyContinue | `
    Where-Object { $_.Name -notmatch "sources|original" } | Select-Object -First 1
  if (-not $jar) { throw "找不到 jar 包，请先 mvn package" }

  Write-Step "启动 $jar"
  $proc = Start-Process -FilePath "java" `
    -ArgumentList "-jar", "homestay-backend\target\$($jar.Name)", "--spring.profiles.active=perf" `
    -RedirectStandardOutput "tools\perf\reports\backend.log" `
    -RedirectStandardError  "tools\perf\reports\backend.err.log" `
    -PassThru -NoNewWindow
  Write-Ok "后端 PID: $($proc.Id)，日志: tools\perf\reports\backend.log"

  Write-Step "等待后端健康检查（最多 90s）"
  $ready = $false
  for ($i = 0; $i -lt 45; $i++) {
    try {
      $r = Invoke-WebRequest -Uri "http://127.0.0.1:8080/actuator/health" -UseBasicParsing -TimeoutSec 3
      if ($r.StatusCode -eq 200) {
        $body = $r.Content | ConvertFrom-Json
        if ($body.status -eq "UP") { $ready = $true; break }
      }
    } catch { }
    Start-Sleep -Seconds 2
  }
  if (-not $ready) {
    Write-Warn "健康检查超时，但继续尝试（请查看 backend.log）"
  } else {
    Write-Ok "后端健康检查通过"
  }
}

# ---- 5. 运行压测 ----
function Run-LoadTest {
  Write-Step "运行 k6 压测（约 2 分钟）"
  if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
    throw "未检测到 k6，请先安装：winget install k6"
  }
  k6 run tools\perf\load-test.js --out json=tools\perf\reports\k6-result.json
  if ($LASTEXITCODE -ne 0) {
    Write-Warn "k6 退出码非零，查看上方输出"
  }
  Write-Ok "压测完成，报告: tools\perf\reports\"
}

# ---- 主流程 ----
try {
  if ($OnlyDown) {
    Remove-PerfEnv
    exit 0
  }

  if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker 未安装"
  }

  Start-PerfEnv

  if (-not $SkipData) {
    Init-PerfData
  } else {
    Write-Warn "跳过数据初始化（-SkipData）"
  }

  if (-not $OnlyUp) {
    Start-Backend
    Run-LoadTest
  } else {
    Write-Ok "环境已就绪（-OnlyUp），请手动启动后端跑压测"
  }

  Write-Host ""
  Write-Ok "===== 全部完成 ====="
  Write-Host ""
  Write-Host "下一步：" -ForegroundColor Cyan
  Write-Host "  - 查看压测报告：Get-Content tools\perf\reports\k6-result.json | Select-Object -First 50"
  Write-Host "  - 查看汇总：Get-Content tools\perf\reports\summary.json"
  Write-Host "  - 销毁环境：.\tools\perf\run-perf-test.ps1 -OnlyDown"
  Write-Host ""
  Write-Host "Grafana 接入：访问 http://127.0.0.1:8080/actuator/prometheus" -ForegroundColor DarkGray
  Write-Host "Prometheus 抓取 job 示例：metrics_path: /actuator/prometheus, port: 8080" -ForegroundColor DarkGray
}
catch {
  Write-Err $_
  Write-Host ""
  Write-Host "排查步骤：" -ForegroundColor Yellow
  Write-Host "  1. docker ps -a | grep perf         # 看容器状态"
  Write-Host "  2. docker logs homestay-mysql-perf  # 看 MySQL 日志"
  Write-Host "  3. Get-Content tools\perf\reports\backend.err.log -Tail 100"
  Write-Host "  4. 销毁重试：.\tools\perf\run-perf-test.ps1 -OnlyDown"
  exit 1
}

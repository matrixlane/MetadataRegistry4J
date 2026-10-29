@echo off
rem ============================================================================
rem Metadata Registry - baseline data one-click initializer.
rem Double-click to run: reads the current datasource configuration
rem (%USERPROFILE%\.metadata-registry\application-local.yml), collects scripts
rem for that dialect from common and data-standards/ws in package order, then
rem executes each package's .sql scripts in name order. No-op when the system
rem has never been initialized. See README.md (Chinese) in this folder.
rem ============================================================================
set "BASELINE_SCRIPT_DIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command "& { $src = [IO.File]::ReadAllText('%~f0', [Text.Encoding]::UTF8); $parts = $src -split [regex]::Escape('#::PS'+'1_BEGIN'); Invoke-Expression $parts[$parts.Count-1] }"
exit /b %ERRORLEVEL%
#::PS1_BEGIN
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$OutputEncoding = [Text.Encoding]::UTF8

function Pause-Exit([int]$code) {
    Write-Host ''
    # 双击场景暂停等待按键；非交互（输入重定向/自动化）场景直接退出
    try {
        if (-not [Console]::IsInputRedirected) {
            Write-Host '按任意键退出...' -ForegroundColor Yellow
            [void][Console]::ReadKey($true)
        }
    } catch { }
    exit $code
}

Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ' 元数据注册系统 - 基准数据一键初始化' -ForegroundColor Cyan
Write-Host '============================================================' -ForegroundColor Cyan

$scriptDir = $env:BASELINE_SCRIPT_DIR
if (-not $scriptDir -and $MyInvocation.MyCommand.Path) { $scriptDir = [IO.Path]::GetDirectoryName($MyInvocation.MyCommand.Path) }
if (-not $scriptDir) { $scriptDir = (Get-Location).Path }
$scriptsRoot = Join-Path $scriptDir 'scripts'

# ---------- 1. 读取数据源配置 ----------
$configFile = Join-Path $env:USERPROFILE '.metadata-registry\application-local.yml'
if (-not (Test-Path $configFile)) {
    Write-Host "未发现数据源配置：$configFile" -ForegroundColor Red
    Write-Host '系统尚未进行过初始化配置，基准数据初始化不生效。' -ForegroundColor Red
    Write-Host '请先启动应用并完成初始化向导（或保存一次数据源配置）。'
    Pause-Exit 1
}

$yml = Get-Content $configFile -Encoding UTF8
$url = $null; $username = $null; $password = $null
foreach ($line in $yml) {
    $t = $line.Trim()
    if ($t -match '^url\s*:\s*(.+)$' -and -not $url) { $url = $Matches[1].Trim().Trim('"', "'") }
    if ($t -match '^username\s*:\s*(.+)$' -and -not $username) { $username = $Matches[1].Trim().Trim('"', "'") }
    if ($t -match '^password\s*:\s*(.+)$' -and -not $password) { $password = $Matches[1].Trim().Trim('"', "'") }
}
if (-not $url) {
    Write-Host "配置文件 $configFile 中未找到 spring.datasource.url。" -ForegroundColor Red
    Pause-Exit 1
}
# 展开 ${DB_URL:默认值} 占位（环境变量优先）
if ($url -match '^\$\{DB_URL:(.+)\}$') {
    $defaultUrl = $Matches[1]
    if ($env:DB_URL) { $url = $env:DB_URL } else { $url = $defaultUrl }
}
# 展开 ~ 与 ${user.home}
$url = $url -replace [regex]::Escape('${user.home}'), $env:USERPROFILE
if ($url -match 'jdbc:sqlite:~') { $url = $url -replace 'jdbc:sqlite:~', "jdbc:sqlite:$($env:USERPROFILE)" }

Write-Host "数据源：$url"

# ---------- 2. 方言识别 ----------
$dialect = $null
if ($url -like 'jdbc:sqlserver:*') { $dialect = 'sqlserver' }
elseif ($url -like 'jdbc:sqlite:*') { $dialect = 'sqlite' }
elseif ($url -like 'jdbc:dm:*') { $dialect = 'dm' }
else {
    Write-Host "无法识别的数据源方言：$url" -ForegroundColor Red
    Pause-Exit 1
}
$packageDirs = @('common', 'data-standards/ws')
$files = @()
foreach ($packageDir in $packageDirs) {
    $dir = Join-Path (Join-Path $scriptsRoot $packageDir) $dialect
    if (Test-Path $dir) {
        $files += @(Get-ChildItem (Join-Path $dir '*.sql') | Sort-Object Name)
    }
}
if ($files.Count -eq 0) {
    Write-Host "基准脚本目录 $scriptsRoot 下没有当前方言可执行的脚本。" -ForegroundColor Red
    Write-Host '（sqlite/dm 的 WS 标准数据脚本将由 sqlserver 版转换产出，当前迭代仅 00-common 就位。）'
    Pause-Exit 1
}
Write-Host "方言：$dialect，待执行脚本 $($files.Count) 个："
$files | ForEach-Object { Write-Host "  - $($_.Name)" }
Write-Host ''

# ---------- 3. 前置检查与执行 ----------
$failed = $false

if ($dialect -eq 'sqlserver') {
    # 解析连接参数：jdbc:sqlserver://host:port;databaseName=...;integratedSecurity=true
    $server = $null; $database = $null; $integrated = $false
    if ($url -match 'jdbc:sqlserver://([^;]+)') { $server = $Matches[1] }
    if ($url -match 'databaseName=([^;]+)') { $database = $Matches[1] }
    if ($url -match 'integratedSecurity=true') { $integrated = $true }
    if (-not $server -or -not $database) {
        Write-Host "无法从 URL 解析 SQL Server 连接参数：$url" -ForegroundColor Red
        Pause-Exit 1
    }
    # JDBC 的 host:port 在 sqlcmd 中须写作 host,port
    if ($server -match '^([^:]+):(\d+)$') { $server = $Matches[1] + ',' + $Matches[2] }
    $sqlcmd = (Get-Command sqlcmd.exe -ErrorAction SilentlyContinue).Source
    if (-not $sqlcmd) {
        $cand = '%ProgramFiles%\Microsoft SQL Server\Client SDK\ODBC\170\Tools\Binn\sqlcmd.exe'
        if (Test-Path $cand) { $sqlcmd = $cand }
    }
    if (-not $sqlcmd) {
        Write-Host '未找到 sqlcmd.exe（请安装 SQL Server 命令行工具或将其加入 PATH）。' -ForegroundColor Red
        Pause-Exit 1
    }
    $auth = @()
    if ($integrated) { $auth += '-E' }
    elseif ($username) {
        if ($password) { $auth += @('-U', $username, '-P', $password) } else { $auth += @('-U', $username, '-P', '') }
    }
    else {
        Write-Host 'URL 未启用 integratedSecurity 且配置中无用户名，无法认证。' -ForegroundColor Red
        Pause-Exit 1
    }
    # schema 前置检查
    & $sqlcmd @auth -S $server -d $database -l 15 -f 65001 -b -Q "SET NOCOUNT ON; IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='registered_item') THROW 50001, N'表结构未建立：请先启动一次应用再执行本脚本', 1;" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host '前置检查失败：目标库表结构未建立。请先启动一次应用（表结构由应用自动创建），停止应用后重试。' -ForegroundColor Red
        Pause-Exit 1
    }
    foreach ($f in $files) {
        Write-Host "执行 $($f.Name) ..." -ForegroundColor Cyan
        & $sqlcmd @auth -S $server -d $database -l 30 -t 0 -f 65001 -b -I -i $f.FullName | Select-Object -Last 5
        if ($LASTEXITCODE -ne 0) {
            Write-Host "执行失败：$($f.Name)（已中止，修正后重跑本批处理即可，脚本幂等）" -ForegroundColor Red
            $failed = $true; break
        }
        Write-Host "完成 $($f.Name)" -ForegroundColor Green
    }
}
elseif ($dialect -eq 'sqlite') {
    $dbPath = $url -replace '^jdbc:sqlite:', ''
    $dbPath = [IO.Path]::GetFullPath($dbPath)
    Write-Host "SQLite 库文件：$dbPath"
    if (-not (Test-Path $dbPath)) {
        Write-Host '库文件不存在。请先启动一次应用（表结构由应用自动创建），停止应用后重试。' -ForegroundColor Red
        Pause-Exit 1
    }
    $sqlite3 = (Get-Command sqlite3.exe -ErrorAction SilentlyContinue).Source
    $python = $null
    if (-not $sqlite3) { $python = (Get-Command python.exe -ErrorAction SilentlyContinue).Source }
    if (-not $sqlite3 -and -not $python) {
        Write-Host '未找到 sqlite3.exe 或 python.exe，无法执行 SQLite 脚本。' -ForegroundColor Red
        Pause-Exit 1
    }
    # 前置检查与执行统一交给临时 Python 助手（避免 PS 5.1 原生参数引号传递问题）；
    # 有 sqlite3.exe 时改用标准输入喂脚本（.read 的引号在 PS 5.1 下会被吞）
    $pyHelper = Join-Path $env:TEMP 'mdr-baseline-sqlite-helper.py'
    $helperCode = @'
import sqlite3, sys, pathlib
mode, db = sys.argv[1], sys.argv[2]
if mode == "check":
    n = sqlite3.connect(db).execute("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='registered_item'").fetchone()[0]
    sys.exit(0 if n == 1 else 1)
elif mode == "run":
    conn = sqlite3.connect(db)
    conn.executescript(pathlib.Path(sys.argv[3]).read_text(encoding="utf-8"))
    conn.commit()
'@
    # 前置检查：库内有用户表
    $checkOk = $false
    if ($sqlite3) {
        $n = '' ; $n = & $sqlite3 $dbPath 'SELECT COUNT(*) FROM sqlite_master WHERE type=''table'' AND name=''registered_item'';'
        if ($LASTEXITCODE -eq 0 -and $n -eq '1') { $checkOk = $true }
    } else {
        [IO.File]::WriteAllText($pyHelper, $helperCode, [Text.Encoding]::UTF8)
        & $python $pyHelper check $dbPath
        if ($LASTEXITCODE -eq 0) { $checkOk = $true }
    }
    if (-not $checkOk) {
        Write-Host '前置检查失败：库内未找到应用表结构。请先启动一次应用，停止应用后重试。' -ForegroundColor Red
        Pause-Exit 1
    }
    foreach ($f in $files) {
        Write-Host "执行 $($f.Name) ..." -ForegroundColor Cyan
        if ($sqlite3) {
            Get-Content $f.FullName -Raw -Encoding UTF8 | & $sqlite3 -bail $dbPath
        } else {
            & $python $pyHelper run $dbPath $f.FullName
        }
        if ($LASTEXITCODE -ne 0) {
            Write-Host "执行失败：$($f.Name)（已中止，修正后重跑本批处理即可，脚本幂等）" -ForegroundColor Red
            $failed = $true; break
        }
        Write-Host "完成 $($f.Name)" -ForegroundColor Green
    }
}
else {
    Write-Host '达梦方言的基准脚本尚未产出（将由 sqlserver 版转换）。' -ForegroundColor Red
    Pause-Exit 1
}

Write-Host ''
if ($failed) { Pause-Exit 1 }
Write-Host '全部基准脚本执行完成。' -ForegroundColor Green
Write-Host '现在可启动应用使用系统。'
Pause-Exit 0

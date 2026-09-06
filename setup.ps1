# PTMOC 项目环境自动配置脚本
# 使用方法：右键 -> 使用 PowerShell 运行

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   PTMOC 项目环境自动配置" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# JDK 目标安装路径
$jdkDir = "C:\jdk17"
$jdkBinPath = Join-Path $jdkDir "bin\javac.exe"

# 检查是否已有可用JDK
function Test-JavaAvailable {
    try {
        $result = & javac -version 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "检测到可用JDK: $result" -ForegroundColor Green
            return $true
        }
    } catch {}
    return $false
}

function Test-JdkInDir {
    param([string]$dir)
    $javacPath = Join-Path $dir "bin\javac.exe"
    if (Test-Path $javacPath) {
        Write-Host "检测到JDK目录: $dir" -ForegroundColor Green
        return $true
    }
    return $false
}

# 1. 检查现有JDK
Write-Host "步骤 1: 检查现有JDK..." -ForegroundColor Yellow

if (Test-JdkInDir $jdkDir) {
    Write-Host "JDK已安装在 $jdkDir" -ForegroundColor Green
} elseif (Test-JavaAvailable) {
    Write-Host "系统JDK可用" -ForegroundColor Green
} else {
    # 2. 安装JDK
    Write-Host ""
    Write-Host "步骤 2: 安装JDK 17..." -ForegroundColor Yellow
    Write-Host "正在下载 Eclipse Temurin JDK 17，请稍候..." -ForegroundColor White
    
    $jdkZip = Join-Path $env:TEMP "jdk17.zip"
    $downloadUrl = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
    
    try {
        # 使用 .NET 的 HttpClient 进行下载（支持重定向）
        Add-Type -AssemblyName System.Net.Http
        $handler = New-Object System.Net.Http.HttpClientHandler
        $handler.AllowAutoRedirect = $true
        $client = New-Object System.Net.Http.HttpClient($handler)
        $client.Timeout = [TimeSpan]::FromMinutes(10)
        
        $response = $client.GetAsync($downloadUrl).Result
        $stream = $response.Content.ReadAsStreamAsync().Result
        
        $fileStream = [System.IO.File]::Create($jdkZip)
        $stream.CopyTo($fileStream)
        $fileStream.Close()
        $stream.Close()
        
        Write-Host "下载完成！" -ForegroundColor Green
        
        # 解压
        Write-Host "正在解压到 C:\ ..." -ForegroundColor White
        Expand-Archive -Path $jdkZip -DestinationPath "C:\" -Force
        
        # 重命名jdk-17.x.x.x目录为jdk17
        Get-ChildItem "C:\jdk-17*" -Directory | ForEach-Object {
            if ($_.FullName -ne $jdkDir) {
                Move-Item $_.FullName $jdkDir -Force
                Write-Host "JDK已安装到 $jdkDir" -ForegroundColor Green
            }
        }
        
        # 清理下载文件
        Remove-Item $jdkZip -Force -ErrorAction SilentlyContinue
        
    } catch {
        Write-Host "自动下载失败: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
        Write-Host "请手动安装JDK 17:" -ForegroundColor Yellow
        Write-Host "  方式1: 打开浏览器访问 https://adoptium.net/ 下载安装" -ForegroundColor White
        Write-Host "  方式2: 在管理员终端运行: winget install EclipseAdoptium.Temurin.17.JDK" -ForegroundColor White
        Write-Host "  安装后确保 javac 可用，然后重新运行此脚本" -ForegroundColor White
        Write-Host ""
        Read-Host "按Enter键退出"
        exit 1
    }
}

# 3. 设置环境变量
Write-Host ""
Write-Host "步骤 3: 配置环境变量..." -ForegroundColor Yellow

if (Test-Path $jdkDir) {
    $env:JAVA_HOME = $jdkDir
} else {
    # 尝试从系统找到JAVA_HOME
    $sysJavaHome = [System.Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine")
    if ($sysJavaHome -and (Test-Path $sysJavaHome)) {
        $env:JAVA_HOME = $sysJavaHome
    }
}

$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 永久设置环境变量
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $env:JAVA_HOME, "User")
$userPath = [System.Environment]::GetEnvironmentVariable("PATH", "User")
if ($userPath -notlike "*$env:JAVA_HOME\bin*") {
    [System.Environment]::SetEnvironmentVariable("PATH", "$env:JAVA_HOME\bin;$userPath", "User")
}

Write-Host "JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Green

# 验证Java
Write-Host ""
& java -version 2>&1 | ForEach-Object { Write-Host $_ }
& javac -version 2>&1 | ForEach-Object { Write-Host $_ }

# 4. 编译项目
Write-Host ""
Write-Host "步骤 4: 编译PTMOC项目..." -ForegroundColor Yellow

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectDir

# 清理旧编译文件
if (Test-Path "bin\com") {
    Remove-Item "bin\com" -Recurse -Force -ErrorAction SilentlyContinue
}

# 编译顺序：model -> util -> core -> test
$compileOrder = @(
    @{
        Name = "model"
        Files = @(
            "src/com/ptmoc/model/Entity.java",
            "src/com/ptmoc/model/KeyPair.java",
            "src/com/ptmoc/model/Polynomial.java",
            "src/com/ptmoc/model/PublicParameters.java",
            "src/com/ptmoc/model/package-info.java"
        )
    },
    @{
        Name = "util"
        Files = @(
            "src/com/ptmoc/util/CryptoUtil.java",
            "src/com/ptmoc/util/MathUtil.java",
            "src/com/ptmoc/util/package-info.java"
        )
    },
    @{
        Name = "core"
        Files = @(
            "src/com/ptmoc/core/BaseModule.java",
            "src/com/ptmoc/core/CryptoModule.java",
            "src/com/ptmoc/core/EvalModule.java",
            "src/com/ptmoc/core/package-info.java"
        )
    },
    @{
        Name = "test"
        Files = @(
            "src/com/ptmoc/test/SimpleTest.java",
            "src/com/ptmoc/test/package-info.java"
        )
    }
)

$allSuccess = $true
foreach ($module in $compileOrder) {
    Write-Host "  编译 $($module.Name) 模块..." -ForegroundColor White -NoNewline
    $fileArgs = $module.Files -join " "
    $proc = Start-Process -FilePath "javac" -ArgumentList "-cp","src","-d","bin",$fileArgs -NoNewWindow -Wait -PassThru -RedirectStandardError "$env:TEMP\ptmoc_compile_err.txt"
    if ($proc.ExitCode -eq 0) {
        Write-Host " 成功" -ForegroundColor Green
    } else {
        Write-Host " 失败！" -ForegroundColor Red
        Get-Content "$env:TEMP\ptmoc_compile_err.txt" -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
        $allSuccess = $false
        break
    }
}

if (-not $allSuccess) {
    Write-Host ""
    Write-Host "编译失败，请检查错误信息" -ForegroundColor Red
    Read-Host "按Enter键退出"
    exit 1
}

Write-Host ""
Write-Host "编译成功！" -ForegroundColor Green

# 5. 运行测试
Write-Host ""
Write-Host "步骤 5: 运行测试..." -ForegroundColor Yellow
Write-Host ""

& java -cp bin com.ptmoc.test.SimpleTest

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   配置和测试完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "后续使用：" -ForegroundColor Yellow
Write-Host "  编译: build.bat" -ForegroundColor White
Write-Host "  运行: java -cp bin com.ptmoc.test.SimpleTest" -ForegroundColor White
Write-Host ""
Read-Host "按Enter键退出"

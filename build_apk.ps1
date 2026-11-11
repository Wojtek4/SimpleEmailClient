# PowerShell Script to Build Simple Mail Android APK
# Usage: .\build_apk.ps1 [-BuildType Debug|Release] [-AutoInstallDeps]

param(
    [ValidateSet("Debug", "Release")]
    [string]$BuildType = "Debug",
    [switch]$AutoInstallDeps
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Simple Mail - Android APK Build Script   " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

$ProjectDir = $PSScriptRoot
Set-Location $ProjectDir

# 1. Check for Java / JDK
Write-Host "[1/4] Checking Java Development Kit (JDK 17+)..." -ForegroundColor Yellow
$javaCmd = Get-Command java -ErrorAction SilentlyContinue

if (-not $javaCmd) {
    # Check default common locations
    $knownJavaDirs = @(
        "$env:LOCALAPPDATA\Programs\jdk-17",
        "$env:ProgramFiles\Eclipse Adoptium\jdk-17*",
        "$env:ProgramFiles\Microsoft\jdk-17*",
        "$env:ProgramFiles\Java\jdk-17*",
        "$env:ProgramFiles\Android\Android Studio\jbr"
    )
    foreach ($pattern in $knownJavaDirs) {
        $found = Get-Item $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            $env:JAVA_HOME = $found.FullName
            $env:Path = "$($found.FullName)\bin;$env:Path"
            Write-Host "Found Java at: $($found.FullName)" -ForegroundColor Green
            break
        }
    }
}

$javaCmd = Get-Command java -ErrorAction SilentlyContinue

if (-not $javaCmd) {
    Write-Host "ERROR: Java 17+ is not installed or not in PATH." -ForegroundColor Red
    Write-Host "You can install it automatically using winget:" -ForegroundColor Yellow
    Write-Host "  winget install Microsoft.OpenJDK.17" -ForegroundColor White
    Write-Host ""
    if ($AutoInstallDeps) {
        Write-Host "AutoInstallDeps switch detected. Installing OpenJDK 17 via winget..." -ForegroundColor Cyan
        Start-Process winget -ArgumentList "install", "Microsoft.OpenJDK.17", "--silent", "--accept-package-agreements", "--accept-source-agreements" -Wait
        Write-Host "Please restart this terminal after installation to refresh environment variables." -ForegroundColor Yellow
        exit 1
    } else {
        Write-Host "Or run this script with -AutoInstallDeps to install JDK automatically." -ForegroundColor Cyan
        exit 1
    }
} else {
    $javaVer = (& java -version 2>&1 | Out-String)
    Write-Host "Java detected: $javaVer" -ForegroundColor Green
}

# 2. Check for Android SDK
Write-Host "[2/4] Checking Android SDK..." -ForegroundColor Yellow
$androidHome = $env:ANDROID_HOME
if (-not $androidHome -or -not (Test-Path $androidHome)) {
    $possibleSdkPaths = @(
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:ProgramFiles(x86)\Android\android-sdk",
        "$env:ProgramFiles\Android\android-sdk"
    )
    foreach ($sdkPath in $possibleSdkPaths) {
        if (Test-Path $sdkPath) {
            $env:ANDROID_HOME = $sdkPath
            $androidHome = $sdkPath
            Write-Host "Found Android SDK at: $sdkPath" -ForegroundColor Green
            break
        }
    }
}

if (-not $androidHome -or -not (Test-Path $androidHome)) {
    Write-Host "WARNING: ANDROID_HOME environment variable is not set." -ForegroundColor Yellow
    Write-Host "If Android Studio is installed, the SDK is typically at: $env:LOCALAPPDATA\Android\Sdk" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Tip: You can install Android Studio via winget:" -ForegroundColor Cyan
    Write-Host "  winget install Google.AndroidStudio" -ForegroundColor White
    Write-Host ""
    Write-Host "Alternatively, use GitHub Actions (.github/workflows/build-apk.yml) to build the APK" -ForegroundColor Cyan
    Write-Host "in the cloud without needing to install anything locally!" -ForegroundColor Cyan
    Write-Host ""
    
    # Check if user wants to create local.properties manually
    $sdkInput = Read-Host "Enter path to your Android SDK (or press Enter to try building anyway)"
    if ($sdkInput -and (Test-Path $sdkInput)) {
        $env:ANDROID_HOME = $sdkInput
        $escapedPath = $sdkInput -replace '\\', '\\\\'
        "sdk.dir=$escapedPath" | Set-Content "$ProjectDir\local.properties"
        Write-Host "Wrote local.properties with sdk.dir." -ForegroundColor Green
    }
} else {
    $escapedPath = $androidHome -replace '\\', '/'
    "sdk.dir=$escapedPath" | Set-Content "$ProjectDir\local.properties"
}

# 3. Ensure Gradle Wrapper exists
Write-Host "[3/4] Checking Gradle Wrapper..." -ForegroundColor Yellow
$wrapperJar = "$ProjectDir\gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $wrapperJar)) {
    Write-Host "Downloading gradle-wrapper.jar..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Path "$ProjectDir\gradle\wrapper" -Force | Out-Null
    Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar" -OutFile $wrapperJar
}

# 4. Run Gradle Build
$taskName = if ($BuildType -eq "Release") { "assembleRelease" } else { "assembleDebug" }
Write-Host "[4/4] Building APK with Gradle task: $taskName..." -ForegroundColor Cyan
Write-Host "This might take a few minutes on the first build while dependencies are downloaded." -ForegroundColor DarkGray

& "$ProjectDir\gradlew.bat" $taskName

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed with exit code $LASTEXITCODE." -ForegroundColor Red
    exit $LASTEXITCODE
}

# 5. Output Result
$apkSubDir = if ($BuildType -eq "Release") { "release" } else { "debug" }
$sourceApk = "$ProjectDir\app\build\outputs\apk\$apkSubDir\app-$apkSubDir.apk"

if (Test-Path $sourceApk) {
    $destinationApk = "$ProjectDir\SimpleEmailClient.apk"
    Copy-Item -Path $sourceApk -Destination $destinationApk -Force
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host " SUCCESS! APK successfully generated:                      " -ForegroundColor Green
    Write-Host " $destinationApk" -ForegroundColor White
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "How to install on your Android phone:" -ForegroundColor Yellow
    Write-Host "1. Transfer 'SimpleEmailClient.apk' to your phone (via USB, email, Google Drive, etc.)." -ForegroundColor White
    Write-Host "2. Tap on the file in your phone's File Manager or Downloads." -ForegroundColor White
    Write-Host "3. If prompted, allow 'Install unknown apps' for that app/browser." -ForegroundColor White
    Write-Host "4. Tap 'Install' and launch Simple Mail!" -ForegroundColor White
} else {
    Write-Host "Build completed, but could not locate output APK at $sourceApk" -ForegroundColor Yellow
}

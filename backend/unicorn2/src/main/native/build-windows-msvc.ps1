# Build windows_64/unicorn.dll with VS 2022 + a local zhkl0228/unicorn tree.
# Usage (from anywhere):
#   powershell -File backend/unicorn2/src/main/native/build-windows-msvc.ps1
#   powershell -File ...\build-windows-msvc.ps1 -UnicornHome D:\project\unicorn-zhkl
param(
    [string]$UnicornHome = $(if ($env:UNICORN_HOME) { $env:UNICORN_HOME } else { "D:\project\unicorn-zhkl" }),
    [switch]$SkipPatch,
    [switch]$SkipUnicornRebuild
)

$ErrorActionPreference = "Stop"
$NativeDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ResourcesDir = Join-Path $NativeDir "..\resources\natives\windows_64"
$PatchFile = Join-Path $NativeDir "patches\0001-win32-qht-overflow-bucket.patch"
$BuildDir = Join-Path $UnicornHome "build_msvc"

if (-not (Test-Path $UnicornHome)) {
    throw "UNICORN_HOME not found: $UnicornHome"
}
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "include\jni.h"))) {
    throw "JAVA_HOME must point at a JDK with include/jni.h"
}

function Invoke-VsDevCmd {
    $vswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
    if (-not (Test-Path $vswhere)) {
        throw "vswhere.exe not found; install Visual Studio 2022 with C++ tools"
    }
    $vs = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if (-not $vs) {
        throw "Visual Studio with VC tools not found"
    }
    $vsdev = Join-Path $vs "Common7\Tools\VsDevCmd.bat"
    $raw = cmd /c "`"$vsdev`" -arch=amd64 -host_arch=amd64 && set"
    foreach ($line in $raw) {
        if ($line -match "^(.*?)=(.*)$") {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], "Process")
        }
    }
}

if (-not $SkipPatch) {
    Push-Location $UnicornHome
    try {
        $probe = git apply --check --reverse $PatchFile 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Patch already applied in $UnicornHome"
        } else {
            git apply --whitespace=fix $PatchFile
            if ($LASTEXITCODE -ne 0) {
                throw "git apply failed for $PatchFile"
            }
            Write-Host "Applied $PatchFile"
        }
    } finally {
        Pop-Location
    }
}

if (-not $SkipUnicornRebuild) {
    New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
    if (-not (Test-Path (Join-Path $BuildDir "unicorn.sln"))) {
        cmake -S $UnicornHome -B $BuildDir -G "Visual Studio 17 2022" -A x64 `
            -DCMAKE_POLICY_VERSION_MINIMUM=3.5 `
            -DUNICORN_ARCH="arm;aarch64"
        if ($LASTEXITCODE -ne 0) { throw "cmake configure failed" }
    }
    cmake --build $BuildDir --config Release --target unicorn
    if ($LASTEXITCODE -ne 0) { throw "cmake --build unicorn failed" }
}

$bundledLib = Join-Path $BuildDir "unicorn.lib"
if (-not (Test-Path $bundledLib) -or ((Get-Item $bundledLib).Length -lt 1MB)) {
    throw "bundled unicorn.lib missing or too small (need build_msvc\unicorn.lib, not Release\unicorn-static.lib): $bundledLib"
}

Invoke-VsDevCmd
New-Item -ItemType Directory -Force -Path $ResourcesDir | Out-Null
$outDll = Join-Path $ResourcesDir "unicorn.dll"
$work = Join-Path $env:TEMP ("unidbg-unicorn-jni-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $work | Out-Null
try {
    $javaInc = Join-Path $env:JAVA_HOME "include"
    $javaWin = Join-Path $javaInc "win32"
    Push-Location $work
    try {
        cmd /c "cl /nologo /O2 /DNDEBUG /MD /LD /I `"$UnicornHome\include`" /I `"$javaInc`" /I `"$javaWin`" `"$NativeDir\unicorn.c`" `"$NativeDir\sample_arm.c`" `"$NativeDir\sample_arm64.c`" `"$bundledLib`" ws2_32.lib /Fe:unicorn.dll /link /INCREMENTAL:NO"
        if ($LASTEXITCODE -ne 0) { throw "cl /LD unicorn.dll failed" }
        Copy-Item "unicorn.dll" $outDll -Force
    } finally {
        Pop-Location
    }
} finally {
    Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
}

Get-Item $outDll | Format-List FullName, Length, LastWriteTime
Write-Host "Installed patched unicorn.dll. nativelib-loader may still cache an older copy under %TEMP% or the user native-lib cache; delete those if a run still loads the 4.8MB MinGW DLL."

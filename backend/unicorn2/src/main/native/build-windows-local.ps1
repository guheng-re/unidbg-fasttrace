# Build windows_64/unicorn.dll from a local unicorn tree (MinGW via Docker).
# Usage: from this directory, or with -UnicornHome.
param(
    [string]$UnicornHome = "D:\project\unicorn-zhkl"
)

$ErrorActionPreference = "Stop"
$NativeDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ResourcesDir = Join-Path $NativeDir "..\resources\natives\windows_64"
$Stage = Join-Path $env:TEMP ("unidbg-unicorn-win-" + [guid]::NewGuid().ToString("N"))

if (-not (Test-Path $UnicornHome)) {
    throw "UNICORN_HOME not found: $UnicornHome"
}

Write-Host "Staging context in $Stage"
New-Item -ItemType Directory -Path $Stage | Out-Null
try {
    New-Item -ItemType Directory -Path (Join-Path $Stage "jni") | Out-Null
    Copy-Item (Join-Path $NativeDir "Dockerfile.windows.local") (Join-Path $Stage "Dockerfile")
    Copy-Item (Join-Path $NativeDir "*.c") (Join-Path $Stage "jni\")
    Copy-Item (Join-Path $NativeDir "*.h") (Join-Path $Stage "jni\")
    Copy-Item (Join-Path $NativeDir "patches") (Join-Path $Stage "patches") -Recurse

    $src = Join-Path $Stage "unicorn-src"
    New-Item -ItemType Directory -Path $src | Out-Null
    # Copy the unicorn tree without git metadata / previous builds.
    $exclude = @(".git", "build", "build_arm64", "build_x86_64", "msvc\.vs")
    robocopy $UnicornHome $src /E /NFL /NDL /NJH /NJS /nc /ns /np `
        /XD .git build build_arm64 build_x86_64 .vs | Out-Null
    if ($LASTEXITCODE -ge 8) {
        throw "robocopy failed with $LASTEXITCODE"
    }

    Write-Host "docker build (this can take several minutes)..."
    docker build -t unidbg-unicorn2-builder-windows_64-local $Stage
    if ($LASTEXITCODE -ne 0) {
        throw "docker build failed"
    }

    New-Item -ItemType Directory -Force -Path $ResourcesDir | Out-Null
    $cid = docker create unidbg-unicorn2-builder-windows_64-local
    docker cp "${cid}:/build/jni/unicorn.dll" (Join-Path $ResourcesDir "unicorn.dll")
    docker rm $cid | Out-Null
    Get-Item (Join-Path $ResourcesDir "unicorn.dll") | Format-List FullName, Length, LastWriteTime
} finally {
    Remove-Item -Recurse -Force $Stage -ErrorAction SilentlyContinue
}

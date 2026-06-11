param(
    [string]$NdkHome = $env:NDK_HOME
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DefaultNdkHome = "D:\AndroidSDK\ndk\27.0.12077973"
$FallbackNdkHome = "D:\AndroidSDK\ndk\24.0.8215888"

if ([string]::IsNullOrWhiteSpace($NdkHome)) {
    if (Test-Path $DefaultNdkHome) {
        $NdkHome = $DefaultNdkHome
    } elseif (Test-Path $FallbackNdkHome) {
        $NdkHome = $FallbackNdkHome
    }
}

if ([string]::IsNullOrWhiteSpace($NdkHome)) {
    throw "NDK_HOME is not set and no default NDK was found under D:\AndroidSDK\ndk"
}

$NdkBuild = Join-Path $NdkHome "ndk-build.cmd"
if (!(Test-Path $NdkBuild)) {
    throw "ndk-build.cmd not found: $NdkBuild"
}

Push-Location $ScriptDir
try {
    & $NdkBuild "NDK_PROJECT_PATH=." "APP_BUILD_SCRIPT=./Android.mk" "NDK_APPLICATION_MK=./Application.mk"
} finally {
    Pop-Location
}

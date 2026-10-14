$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Sdk = "D:\AndroidSDK"
$BuildTools = Join-Path $Sdk "build-tools\35.0.1"
$Platform = Join-Path $Sdk "platforms\android-34\android.jar"
$JavaHome = "C:\Program Files\Java\jdk-17"
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$BuildTools;$env:Path"

$Aapt2 = Join-Path $BuildTools "aapt2.exe"
$D8 = Join-Path $BuildTools "d8.bat"
$Zipalign = Join-Path $BuildTools "zipalign.exe"
$Apksigner = Join-Path $BuildTools "apksigner.bat"
$Javac = Join-Path $JavaHome "bin\javac.exe"
$Keytool = Join-Path $JavaHome "bin\keytool.exe"

$Work = Join-Path $Root "build"
$Obj = Join-Path $Work "obj"
$DexDir = Join-Path $Work "dex"
$Gen = Join-Path $Work "gen"
$Assets = Join-Path $Root "assets"
$Jni = Join-Path $Work "lib\arm64-v8a"
Remove-Item -Recurse -Force $Work -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Obj, $DexDir, $Gen, $Jni | Out-Null

$NdkClangC = Join-Path $Sdk "ndk\27.0.12077973\toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android26-clang.cmd"
$So = Join-Path $Jni "libenvdump.so"
& $NdkClangC -shared -fPIC -O2 -fvisibility=hidden -llog "-Wl,-soname,libenvdump.so" `
    -o $So (Join-Path $Root "jni\envdump.c")
if ($LASTEXITCODE -ne 0) { throw "envdump compile failed" }
Write-Host "built $So"

$Ks = Join-Path $Root "debug.keystore"
if (-not (Test-Path $Ks)) {
    & $Keytool -genkeypair -keystore $Ks -storepass android -alias androiddebugkey `
        -keypass android -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=Android Debug,O=Android,C=US"
}

& $Aapt2 link -o (Join-Path $Work "base.apk") -I $Platform `
    --manifest (Join-Path $Root "AndroidManifest.xml") --java $Gen -A $Assets
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

$src = Get-ChildItem -Recurse (Join-Path $Root "src") -Filter *.java | ForEach-Object { $_.FullName }
& $Javac --release 8 -encoding UTF-8 -cp $Platform -d $Obj $src
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$classes = Get-ChildItem -Recurse $Obj -Filter *.class | ForEach-Object { $_.FullName }
& $D8 --lib $Platform --output $DexDir $classes
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

$unsigned = Join-Path $Work "unsigned.apk"
Copy-Item (Join-Path $Work "base.apk") $unsigned -Force
$Dex = Join-Path $DexDir "classes.dex"
python -c @"
import zipfile
z = zipfile.ZipFile(r'$unsigned', 'a')
z.write(r'$Dex', 'classes.dex')
z.write(r'$So', 'lib/arm64-v8a/libenvdump.so')
z.close()
"@

$aligned = Join-Path $Work "aligned.apk"
& $Zipalign -p -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

$OutApk = Join-Path $Root "envdump.apk"
& $Apksigner sign --ks $Ks --ks-pass pass:android --key-pass pass:android --out $OutApk $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
Write-Host "built $OutApk"
Get-Item $OutApk | Format-List FullName, Length

# Android 环境 JSON 配置说明

`TraceEnvironmentConfig` 用一个 JSON 文件固定 Android unidbg 运行时环境。没有配置的字段会保留当前 unidbg 默认行为。

示例文件：

```text
example/trace-env.example.json
```

## 加载方式

代码中显式设置，优先级最高：

```java
AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit()
        .setEnvironmentConfig(new File("D:/project/TraceAIagent_v3/unidbg-fasttrace/example/trace-env.example.json"))
        .build();
```

JVM 参数设置：

```text
-Dunidbg.env.config=D:/project/TraceAIagent_v3/unidbg-fasttrace/example/trace-env.example.json
```

如果两种方式同时存在，`setEnvironmentConfig(...)` 优先。

## process

进程与线程身份字段。

| 字段 | 影响位置 |
| --- | --- |
| `process.processName` | 覆盖 `emulator.getProcessName()`，也会影响 loader 初始化时写入栈上的 program name。 |
| `process.pid` | 覆盖 `emulator.getPid()`，影响 `getpid` syscall、`Process.myPid()`、`/proc/<pid>/...` 路径判断、主线程初始化 tid fallback。 |
| `process.ppid` | 覆盖 `getppid` syscall 返回值。 |
| `process.tid` | 覆盖 `gettid` syscall 返回值，并用于 Android TLS 初始化中的 pthread tid。 |
| `process.uid` | 覆盖 32/64 位 Android syscall 中 `getuid` 返回值。 |
| `process.gid` | 覆盖 32 位 Android syscall 中 `getgid` 返回值。 |
| `process.euid` | 覆盖 32/64 位 Android syscall 中 `geteuid` 返回值。 |
| `process.egid` | 覆盖 32 位 Android syscall 中 `getegid` 返回值。 |
| `process.threadName` | 覆盖 32 位 `prctl(PR_GET_NAME)` 返回的线程名。 |

## time

时间字段。当前实现中时间固定不推进，用于可复现运行。

| 字段 | 影响位置 |
| --- | --- |
| `time.currentTimeMillis` | 覆盖 `gettimeofday`、64 位 `gettimeofday64`、`clock_gettime(CLOCK_REALTIME)`、JNI `new Date()`、JNI `Date->allocObject`。 |
| `time.monotonicNanos` | 覆盖 32/64 位 `clock_gettime` 的 monotonic 类时钟，包括 `CLOCK_MONOTONIC`、`CLOCK_MONOTONIC_RAW`、`CLOCK_MONOTONIC_COARSE`、`CLOCK_BOOTTIME`，也覆盖 `/dev/alarm` 的 `ANDROID_ALARM_ELAPSED_REALTIME`。 |
| `time.timezoneMinutesWest` | 覆盖 `gettimeofday` 写入 `timezone.tz_minuteswest` 的值。 |

## random

随机源字段。所有 hex 字段支持空白和冒号分隔。请求长度超过配置字节数时，会从头循环填充。

| 字段 | 影响位置 |
| --- | --- |
| `random.devRandomHex` | 覆盖读取 `/dev/random` 时返回的字节。 |
| `random.devUrandomHex` | 覆盖读取 `/dev/urandom` 时返回的字节。 |
| `random.devSrandomHex` | 覆盖读取 `/dev/srandom` 时返回的字节。 |
| `random.getrandomHex` | 覆盖 `getrandom` syscall 返回的字节。 |
| `random.uuid` | 覆盖 JNI `java/util/UUID->randomUUID()` 返回值。 |
| `random.stackGuardHex` | 覆盖 Android loader 初始化 TLS 时写入的 `__stack_chk_guard` 字节。长度按当前架构指针大小循环填充，32 位为 4 字节，64 位为 8 字节。 |
| `random.atRandomHex` | 覆盖 auxv 中 `AT_RANDOM` 指向的 16 字节随机区。 |
| `random.mediaDrmDeviceUniqueIdHex` | 覆盖 `AMediaDrm_getPropertyByteArray("deviceUniqueId")` 返回的 32 字节设备唯一 ID。 |

## linux.uname

Linux utsname 字段，影响 32/64 位 Android `uname()` syscall 写入的结构体内容。

| 字段 | 影响位置 |
| --- | --- |
| `linux.uname.sysname` | `uname().sysname`，默认类似 `Linux`。 |
| `linux.uname.nodename` | `uname().nodename`，默认类似 `android`。 |
| `linux.uname.release` | `uname().release`，内核 release 字符串。 |
| `linux.uname.version` | `uname().version`，内核 version 字符串。 |
| `linux.uname.machine32` | 32 位 emulator 下 `uname().machine`，默认类似 `armv7l`。 |
| `linux.uname.machine64` | 64 位 emulator 下 `uname().machine`，默认类似 `aarch64`。 |
| `linux.uname.domainname` | `uname().domainname`，默认类似 `(none)`。 |

## linux.files

内存文件映射。配置后，`LinuxFileSystem.open()` 会在真实 rootfs 和内置资源前优先返回这些内容。

| 字段 | 影响位置 |
| --- | --- |
| `linux.files."/proc/cpuinfo"` | native 代码 open/read `/proc/cpuinfo` 时返回配置内容。 |
| `linux.files."/proc/meminfo"` | native 代码 open/read `/proc/meminfo` 时返回配置内容。 |
| `linux.files."/proc/version"` | native 代码 open/read `/proc/version` 时返回配置内容。 |
| `linux.files."/sys/devices/system/cpu/present"` | native 代码读取 CPU present 信息时返回配置内容。 |
| `linux.files."/sys/devices/system/cpu/possible"` | native 代码读取 CPU possible 信息时返回配置内容。 |

也可以增加其他 `/proc/*`、`/sys/*` 绝对路径。读取 `/proc/<pid>/xxx` 时，如果没有直接配置，会尝试映射到 `/proc/self/xxx`。

## android

应用级 Android 信息。

| 字段 | 影响位置 |
| --- | --- |
| `android.packageName` | 覆盖 `BaseVM.getPackageName()`，影响 `Context.getPackageName()`、`ActivityThread.currentPackageName()`、`PackageManager` 查询匹配、APK library file 的 packageName。 |
| `android.versionName` | 覆盖 `BaseVM.getVersionName()`，影响 `PackageInfo.versionName`。 |
| `android.versionCode` | 覆盖 `BaseVM.getVersionCode()`，影响 `PackageInfo.versionCode`。 |
| `android.apkPath` | 覆盖 `ApplicationInfo.sourceDir` 和 `ApplicationInfo.publicSourceDir`。 |
| `android.dataDir` | 覆盖 `ApplicationInfo.dataDir`。 |
| `android.nativeLibraryDir32` | 32 位 emulator 下覆盖 `ApplicationInfo.nativeLibraryDir`。 |
| `android.nativeLibraryDir64` | 64 位 emulator 下覆盖 `ApplicationInfo.nativeLibraryDir`。 |

## android.build

Java 层 `android.os.Build` 和 `android.os.Build.VERSION` 字段。

| 字段 | 影响位置 |
| --- | --- |
| `android.build.BOARD` | `Build.BOARD`。 |
| `android.build.BOOTLOADER` | `Build.BOOTLOADER`。 |
| `android.build.BRAND` | `Build.BRAND`。 |
| `android.build.CPU_ABI` | `Build.CPU_ABI`。 |
| `android.build.CPU_ABI2` | `Build.CPU_ABI2`。 |
| `android.build.DEVICE` | `Build.DEVICE`。 |
| `android.build.DISPLAY` | `Build.DISPLAY`。 |
| `android.build.FINGERPRINT` | `Build.FINGERPRINT`。 |
| `android.build.HARDWARE` | `Build.HARDWARE`。 |
| `android.build.HOST` | `Build.HOST`。 |
| `android.build.ID` | `Build.ID`。 |
| `android.build.MANUFACTURER` | `Build.MANUFACTURER`，也作为 `AMediaDrm_getPropertyString("vendor")` 的优先值。 |
| `android.build.MODEL` | `Build.MODEL`。 |
| `android.build.PRODUCT` | `Build.PRODUCT`。 |
| `android.build.SERIAL` | `Build.SERIAL`。 |
| `android.build.TAGS` | `Build.TAGS`。 |
| `android.build.TYPE` | `Build.TYPE`。 |
| `android.build.USER` | `Build.USER`。 |
| `android.build.VERSION.CODENAME` | `Build.VERSION.CODENAME`。 |
| `android.build.VERSION.INCREMENTAL` | `Build.VERSION.INCREMENTAL`。 |
| `android.build.VERSION.RELEASE` | `Build.VERSION.RELEASE`。 |
| `android.build.VERSION.SDK_INT` | `Build.VERSION.SDK_INT`，这是 int 字段。 |
| `android.build.VERSION.SECURITY_PATCH` | `Build.VERSION.SECURITY_PATCH`。 |

规则：`android/os/Build->FIELD:Ljava/lang/String;` 映射到 `android.build.FIELD`；`android/os/Build$VERSION->FIELD` 映射到 `android.build.VERSION.FIELD`。

## android.properties

native system property 字段。配置存在时，loader 会自动注册 JSON-backed `SystemPropertyHook`。

| 字段 | 影响位置 |
| --- | --- |
| `android.properties.ro.product.board` | `__system_property_get/read/find("ro.product.board")`。 |
| `android.properties.ro.product.brand` | `__system_property_get/read/find("ro.product.brand")`。 |
| `android.properties.ro.product.device` | `__system_property_get/read/find("ro.product.device")`。 |
| `android.properties.ro.product.manufacturer` | `__system_property_get/read/find("ro.product.manufacturer")`。 |
| `android.properties.ro.product.model` | `__system_property_get/read/find("ro.product.model")`。 |
| `android.properties.ro.product.name` | `__system_property_get/read/find("ro.product.name")`。 |
| `android.properties.ro.hardware` | `__system_property_get/read/find("ro.hardware")`。 |
| `android.properties.ro.serialno` | `__system_property_get/read/find("ro.serialno")`。 |
| `android.properties.ro.boot.serialno` | `__system_property_get/read/find("ro.boot.serialno")`。 |
| `android.properties.ro.build.*` | 对应 key 的 `__system_property_get/read/find(...)`。 |
| `android.properties.ro.debuggable` | `__system_property_get/read/find("ro.debuggable")`。 |
| `android.properties.ro.secure` | `__system_property_get/read/find("ro.secure")`。 |
| `android.properties.ro.bootloader` | `__system_property_get/read/find("ro.bootloader")`。 |
| `android.properties.ro.boot.hardware` | `__system_property_get/read/find("ro.boot.hardware")`。 |
| `android.properties.ro.opengles.version` | `__system_property_get/read/find("ro.opengles.version")`。 |
| `android.properties.ro.sf.lcd_density` | `__system_property_get/read/find("ro.sf.lcd_density")`。 |
| `android.properties.persist.sys.timezone` | `__system_property_get/read/find("persist.sys.timezone")`。 |
| `android.properties.net.bt.name` | `__system_property_get/read/find("net.bt.name")`。 |
| `android.properties.wifi.interface` | `__system_property_get/read/find("wifi.interface")`。 |

可以增加任意其他 property key。只要 native 代码查询的 key 和 JSON key 完全一致，就会返回 JSON 值。

## 快速验证点

把样例 JSON 中的值改成明显字符串或数字，然后验证：

| 验证项 | 预期来源 |
| --- | --- |
| `Process.myPid()` | `process.pid` |
| `Build.MODEL` | `android.build.MODEL` |
| `Build.VERSION.SDK_INT` | `android.build.VERSION.SDK_INT` |
| `__system_property_get("ro.hardware")` | `android.properties.ro.hardware` |
| `UUID.randomUUID()` | `random.uuid` |
| `getrandom(...)` | `random.getrandomHex` |
| `open("/proc/cpuinfo")` | `linux.files."/proc/cpuinfo"` |
| `ApplicationInfo.sourceDir` | `android.apkPath` |

## 编译验证

PowerShell：

```powershell
cd D:\project\TraceAIagent_v3\unidbg-fasttrace
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd -pl unidbg-api,unidbg-android -DskipTests compile
.\mvnw.cmd -pl unidbg-api "-Dmaven.test.skip=false" -Dtest=TraceEnvironmentConfigTest test
```

# Android 环境 JSON 配置说明

`TraceEnvironmentConfig` 用一个 JSON 文件固定 Android unidbg 运行时环境。没有配置的字段会保留当前 unidbg 默认行为。

## 环境读取探测（默认开启）

SO / JNI 读取指纹、网卡、`/proc`、`/sys`、随机数、时间等时，**无论模板有没有填**，都会提醒：

- 已配置命中：原 sidecar（`kind=time` / `linux_file` / `network_device` …），并额外打 `[环境探测]` 日志
- 模板未填：`kind=env_probe`，`source=unconfigured`（不编造返回值；Java 仍 UOE / native 仍走原路径）

默认开启。关闭：

```text
-Dunidbg.env.probe=false
```

或 `EnvAccessProbe.setEnabled(false)`。开了 `traceCodeText(..., "trace.log")` 时，这些行也会进 `trace.log.env.jsonl`。

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

快捷设备画像（第一阶段，详见 `docs/device-fingerprint-quick-profile.md`）走独立入口，**不**替代完整配置：

```java
AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit()
        .setEnvironmentProfile(new File("profiles/pixel6-analysis/device-fingerprint.json"))
        .build();
```

```text
-Dunidbg.env.profile=profiles/pixel6-analysis/device-fingerprint.json
```

`profiles/pixel6-analysis/` 第 1 批已集中填写签名算法高频稳定输入（`linux.uname`/`linux.cpu`/`linux.environ`/`linux.auxv`、补全的 `Build`/`ro.*`、`android.identifiers`/`settings.secure.android_id`、`android.packages.signaturesHex`、`android.runtime`、`android.features`、`android.configuration`、`filesystem.statfs`，以及 `files/proc/*` 与 `files/sys/*` 文本）。详见 `docs/device-fingerprint-quick-profile.md`「第 1 批」。不新增后端。

加载器只把已实现字段转换成现有 `TraceEnvironmentConfig`，并挂上 `fileOverlayRoot` 只读覆盖（优先于 `linux.files`；省略该键时默认为画像目录下的 `files`）。仅从文件加载时才解析覆盖根；`DeviceFingerprintProfile.parse(json, null)` 不挂覆盖层。`schemaVersion` 必须是 `traceai-device-fingerprint/v1`，否则整份画像失败。guest 路径与 `fileOverlayRoot` **不**折叠 `//` / `.` / 尾 `/`。写/`O_RDWR` 为 `EACCES`。覆盖层**文件**上的 `O_DIRECTORY` 为 `ENOTDIR`；覆盖层**目录**可 `getdents` 枚举。不回落宿主机，不跟随符号链接，不接管 `/proc/self|pid/fd/*`。`reserved` 字段现为 `graphics.native`、`backendStatus`，以及**旧形状** `network.capabilities`（仅 `internet`/`vpn` 等布尔、无 `transportTypes`）。`android.telephony.cellInfo`、`android.sensors.samples` 与新形状 `network.capabilities`（`transportTypes`/`networkCapabilities`）已转正，不再剥离。其它非法已实现字段仍走现有配置校验并失败。`android.tee.securityLevel` 的整数 `0/1/2` 转为 `SOFTWARE`/`TRUSTED_ENVIRONMENT`/`STRONGBOX`。同一 builder 上若同时调用，**显式** `setEnvironmentConfig(...)` 优先于 `setEnvironmentProfile(...)`；系统属性同样是 `-Dunidbg.env.config` 优先于 `-Dunidbg.env.profile`。画像命中 sidecar 的 source 为 `profile-json`（转换后的 JSON 字段和 `linux.files`）或 `profile-file`（覆盖层 `open/read`）。

## process

进程与线程身份字段。

| 字段 | 影响位置 |
| --- | --- |
| `process.processName` | 覆盖 `emulator.getProcessName()`，也会影响 loader 初始化时写入栈上的 program name。 |
| `process.pid` | 覆盖 `emulator.getPid()`，影响 `getpid` syscall、Java `Process.myPid()`（`callStaticIntMethod` / `callStaticIntMethodV` 两条路径一致）、`/proc/<pid>/...` 路径判断、主线程初始化 tid fallback。 |
| `process.pgid` | 覆盖 ARM32 `getpgrp`（NR 65，无参）以及 ARM32 `getpgid`（NR 132）/ ARM64 `getpgid`（NR 155）的返回值；并写入 `/proc/self\|pid/stat` **字段 5（pgrp）**。精确 JSON 正整数 `1..Integer.MAX_VALUE`。syscall 仅当参数 pid 为 **0** 或当前 `emulator.getPid()` 时命中；其它 pid **不**返回配置值、**不**发 sidecar，保留该架构未知 syscall/旧路径（**不**为此设 errno）。键缺失回落**当前 pid**，**不**从 `ppid`/`sid` 推导。成功读取 sidecar kind=`process_identity`，api=`getpgrp`/`getpgid`，value 仅为该整数，source=`json-config` 仅当本键显式配置否则 `unidbg-default`。AArch64 **无**独立 `getpgrp` syscall。**不**实现 `setpgid`、运行时进程组状态、进程树、其它 pid、Java API。 |
| `process.sid` | 覆盖 ARM32 `getsid`（NR 147）/ ARM64 `getsid`（NR 156）的返回值；并写入 `/proc/self\|pid/stat` **字段 6（session）**。精确 JSON 正整数 `1..Integer.MAX_VALUE`。pid 规则与 `pgid` 相同（仅 0/当前 pid）。键缺失回落**当前 pid**（纠正旧 ARM32 `getsid` 误用 `ppid`），**不**从 `ppid`/`pgid` 推导。sidecar api=`getsid`，其余同 `pgid`。**不**实现 `setsid`、会话状态变更、进程树、其它 pid、Java API。 |
| `process.ppid` | 覆盖 `getppid` syscall 返回值。 |
| `process.tid` | 覆盖 `gettid` syscall 返回值，并用于 Android TLS 初始化中的 pthread tid；另影响 Java `Process.myTid()`（VarArg 与 VaList 一致；无配置时回落 `emulator.getPid()`）。 |
| `process.uid` | 覆盖 32/64 位 Android syscall 中 `getuid` 返回值；另影响 Java `Process.myUid()`（VarArg 与 VaList 一致；无配置时返回 0）。 |
| `process.gid` | 覆盖 32 位 Android syscall 中 `getgid` 返回值。 |
| `process.euid` | 覆盖 32/64 位 Android syscall 中 `geteuid` 返回值。 |
| `process.egid` | 覆盖 32 位 Android syscall 中 `getegid` 返回值。 |
| `process.supplementaryGids` | 只读模拟 ARM32 `getgroups32`（NR **205**）与 ARM64 `getgroups`（NR **158**），以及 `/proc/self\|pid/status` 的 **Groups** 行。必须是 JSON 数组；允许显式 `[]` 表示空补充组；元素为精确 JSON 非负整数 `0..Integer.MAX_VALUE`，按数组顺序保存且不得重复。仅当本键显式存在时接管 getgroups：`size==0` 返回配置组数且不访问 `list`；`size>=组数` 时按顺序写 32 位 `gid_t` 并返回组数（组数为 0 时允许 null `list`；组数>0 且 `list` 为 null 则返回 `-1` 并设 `EFAULT`）；`size<组数`（含负数）返回 `-1` 并设 `EINVAL`，不写 buffer。成功 sidecar kind=`process_identity`，api=`getgroups`，source=`json-config`，value **仅** `count=<n>,requestedSize=<size>`，中文 note，**不**写完整 gid 列表。失败以及未配置不发该事件。键缺失：ARM32 NR=205 继续返回 0；ARM64 NR=158 **不**接管，保留未知 syscall/旧路径。**不**从 `gid`/`egid` 推导，**无**宿主回退。**不**实现 `setgroups`。**不**处理旧 ARM32 NR=80（16 位 gid）。status Groups 仅在 `linux.proc` 已渲染时追加；`linux.files` 优先。 |
| `process.threadName` | 覆盖 ARM32/ARM64 `prctl(PR_GET_NAME)` 返回的线程名（最多 **15 个 UTF-8 字节** + NUL，不拆分多字节码点；缺省回落当前 Java 线程名；无 sidecar）。 |

## time

时间字段。当前实现中时间固定不推进，用于可复现运行。

| 字段 | 影响位置 |
| --- | --- |
| `time.currentTimeMillis` | 覆盖 `gettimeofday`、64 位 `gettimeofday64`、`clock_gettime(CLOCK_REALTIME)`、JNI `new Date()`、JNI `Date->allocObject`；另覆盖静态 JNI **`System.currentTimeMillis()J`**（返回**精确**配置 long；sidecar `api=System.currentTimeMillis`，`value=currentTimeMillis=<n>`；kind=`time`、source=`json-config`；**仅**键存在时命中；键缺失 / 错误签名 / 其它未列出的 System 静态 long API 仍 UOE 无事件；**不**读 JVM/宿主时间）。`time.currentTimeMillis` **仅**接管 `System.currentTimeMillis`；`System.nanoTime` 的支持条件和取值见 `time.monotonicNanos`。 |
| `time.monotonicNanos` | 覆盖 32/64 位 `clock_gettime` 的 monotonic 类时钟，包括 `CLOCK_MONOTONIC`、`CLOCK_MONOTONIC_RAW`、`CLOCK_MONOTONIC_COARSE`、`CLOCK_BOOTTIME`，也覆盖 `/dev/alarm` 的 `ANDROID_ALARM_ELAPSED_REALTIME`；另覆盖静态 JNI **`System.nanoTime()J`**（返回**精确** `monotonicNanos` long 原值，**不**按毫秒截断；sidecar `api=System.nanoTime`，`value=monotonicNanos=<n>`；**不**读 JVM/宿主时间）与静态 JNI **`SystemClock.elapsedRealtime()J`**（返回 `monotonicNanos / 1_000_000`，向零截断；sidecar `api=SystemClock.elapsedRealtime`，`value=monotonicNanos=<n>,resultMillis=<m>`）、**`SystemClock.elapsedRealtimeNanos()J`**（返回**精确** `monotonicNanos`；sidecar `api=SystemClock.elapsedRealtimeNanos`，`value=monotonicNanos=<n>,resultNanos=<n>`）与 **`SystemClock.uptimeMillis()J`**（同样 `monotonicNanos / 1_000_000` 向零截断，与 elapsedRealtime **同值**，因**不**建模深度休眠；sidecar `api=SystemClock.uptimeMillis`，`value=monotonicNanos=<n>,resultMillis=<m>`）。四者均为 kind=`time`、source=`json-config`；**仅**键存在时命中；键缺失 / 错误签名 / 非该 API UOE 无事件。**不**实现 sleep / 时钟推进 / 其它 SystemClock API。 |
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
| `linux.uname.sysname` | `uname().sysname`，默认类似 `Linux`。**键显式配置时**另服务只读 `/proc/sys/kernel/ostype`（UTF-8 配置原文 + 一 LF；**不**用 syscall 默认填充缺失键）。 |
| `linux.uname.nodename` | `uname().nodename`，默认类似 `android`。**键显式配置时**另服务只读 `/proc/sys/kernel/hostname`（UTF-8 配置原文 + 一 LF；**不**用 syscall 默认填充缺失键）。 |
| `linux.uname.release` | `uname().release`，内核 release 字符串。**键显式配置时**另服务只读 `/proc/sys/kernel/osrelease`（UTF-8 + LF）。 |
| `linux.uname.version` | `uname().version`，内核 version 字符串。**键显式配置时**另服务只读 `/proc/sys/kernel/version`（UTF-8 + LF；**不**合成 `/proc/version`）。 |
| `linux.uname.machine32` | 32 位 emulator 下 `uname().machine`，默认类似 `armv7l`。 |
| `linux.uname.machine64` | 64 位 emulator 下 `uname().machine`，默认类似 `aarch64`。 |
| `linux.uname.domainname` | `uname().domainname`，默认类似 `(none)`。**键显式配置时**另服务只读 `/proc/sys/kernel/domainname`（UTF-8 + LF）。 |

**与 `/proc/sys/kernel/*` 自动文件：** 仅当对应 uname 字段**键存在**时，`LinuxFileSystem.open` 在 **`linux.files` 精确（及 pid→self）之后** 返回该内容；缺失键保留旧 open/rootfs，**不发** sidecar。成功自动读取发 kind=`linux_proc`、`api=read("<path>")`、`value=path=…,format=hostname\|osrelease\|version\|domainname\|ostype,bytes=…`、`source=json-config`（**不**写原文）。**不**支持写、其它 sysctl、`/proc/version` 由 uname 推导。`linux.files` 同路径仍优先。

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

## linux.proc

可选 **JSONObject**，结构化描述当前进程在 procfs 中的 status / cmdline / cgroup / stat / statm / **io** 画像，以及可选 **`comm`**（`/proc/self|pid/comm`）、**`wchan`**（`/proc/self|pid/wchan` 固定分析标记）、全局 **`bootId`**（`/proc/sys/kernel/random/boot_id`）、**`randomUuid`**（`/proc/sys/kernel/random/uuid` 固定标记）、**`entropyAvail`**（`/proc/sys/kernel/random/entropy_avail`）、**`randomPoolSize`**（`/proc/sys/kernel/random/poolsize`）、**`writeWakeupThreshold`**（`/proc/sys/kernel/random/write_wakeup_threshold`）与 **`urandomMinReseedSecs`**（`/proc/sys/kernel/random/urandom_min_reseed_secs`）。实现类：`TraceEnvironmentConfig.LinuxProcConfig` + `ConfiguredProcFiles`；`LinuxFileSystem.open` 在 **`linux.files` 精确（及 pid→self）命中之后** 接入。

显式 `linux.proc: {}` 合法且 presence=true（各字段用默认值渲染；**不含** boot_id / random uuid / entropy_avail / poolsize / write_wakeup_threshold / urandom_min_reseed_secs 服务）。缺失 `linux.proc` 键时不启用本路径（回落旧逻辑 / rootfs）。

### 字段表

| 字段 | 必填 | 类型 / 默认 / 范围 | presence 语义 |
| --- | --- | --- | --- |
| `state` | 否 | 单字符 `R\|S\|D\|Z\|T\|t\|X\|I`；默认 `S` | 缺省用默认 |
| `tracerPid` | 否 | 精确 int `0..Integer.MAX_VALUE`；默认 `0` | 缺省用默认 |
| `threadCount` | 否 | 精确 int `1..Integer.MAX_VALUE`；默认 `1` | 缺省用默认 |
| `cmdline` | 否 | JSONArray of String（允许 `[]`、允许空字符串与空格；禁 NUL/CR/LF） | **缺失** → 渲染时由 `process.processName` 派生；**存在**（含 `[]`）→ 用配置数组 |
| `cgroups` | 否 | JSONArray of 非空 String（允许 `[]`；禁 NUL/CR/LF） | **缺失** → 默认 `0::/`；**存在**（含 `[]`）→ 用配置 |
| `startTimeTicks` | 否 | 精确 long `0..Long.MAX_VALUE`；默认 `0` | 写入 `stat` 的 starttime |
| `virtualMemoryBytes` | 否 | 同上；默认 `0` | `stat` 的 vsize；`status` 的 VmSize(kB)=值/1024；**statm 的 size**（按页向上取整，见下） |
| `residentSetPages` | 否 | 同上；默认 `0` | `stat` 的 rss（页）；VmRSS(kB)=**页数×4**（`BigInteger` 防溢出，`Long.MAX_VALUE`→`36893488147419103228 kB`）；**statm 的 resident** |
| `rchar` | 否 | 精确 long `0..Long.MAX_VALUE`；默认 `0` | 写入 `io` 的 `rchar` |
| `wchar` | 否 | 同上；默认 `0` | 写入 `io` 的 `wchar` |
| `syscr` | 否 | 同上；默认 `0` | 写入 `io` 的 `syscr` |
| `syscw` | 否 | 同上；默认 `0` | 写入 `io` 的 `syscw` |
| `readBytes` | 否 | 同上；默认 `0` | 写入 `io` 的 `read_bytes`（配置键 camelCase，渲染键下划线） |
| `writeBytes` | 否 | 同上；默认 `0` | 写入 `io` 的 `write_bytes` |
| `cancelledWriteBytes` | 否 | 同上；默认 `0` | 写入 `io` 的 `cancelled_write_bytes` |
| `bootId` | 否 | 非空 **canonical UUID String**：恰好 **36** 个 ASCII 字符，形如 `8-4-4-4-12` 十六进制 + 连字符（大小写不敏感）；入库 **小写** | **仅当键存在**时服务 `/proc/sys/kernel/random/boot_id`；**缺失**不服务、不发事件；**绝不**从 `random.uuid` / `randomUuid` 推导或生成；**不**用 `UUID.fromString`（其接受 `1-1-1-1-1` 等短形式）；JSON null / 首尾空白 / 短分组 / 额外字符 / 非 ASCII / 非 String → 解析失败 |
| `randomUuid` | 否 | 同 `bootId`：非空 **canonical UUID String** 36 字符 `8-4-4-4-12` 十六进制 + 连字符（大小写不敏感）；入库 **小写** | **仅当键存在**时服务精确 `/proc/sys/kernel/random/uuid`（**固定**可复现标记，**不**每次读生成新 UUID）；**缺失**回落旧 open、不发事件；**绝不**从 `random.uuid` / `bootId` 推导；校验规则与 `bootId` 相同 |
| `entropyAvail` | 否 | 精确 JSON Number 整数 **`0..2147483647`**（无默认值） | **仅当键存在**时只读接管精确 `/proc/sys/kernel/random/entropy_avail`；内容为十进制 ASCII + 单个 LF；**缺失**回落旧 open、不发事件；与 `randomPoolSize` / `writeWakeupThreshold` / `urandomMinReseedSecs` / `bootId` / `randomUuid` **完全独立**，**不**互推、**不**读宿主机熵池；写 / `O_DIRECTORY` / 目录 / 相近路径 **不接管**；**不**实现 entropy 行为 / 随机数生成 / 重播 / 其它 random sysctl；`linux.files` 精确路径优先；JSON 浮点 / 字符串 / 布尔 / null / 负数 / 越界 / 未知字段 → 解析失败（字段路径 `linux.proc.entropyAvail`） |
| `randomPoolSize` | 否 | 精确 JSON Number 整数 **`0..2147483647`**（无默认值） | **仅当键存在**时只读接管精确 `/proc/sys/kernel/random/poolsize`；内容为十进制 ASCII + 单个 LF；**缺失**回落旧 open、不发事件；与 `entropyAvail` / `writeWakeupThreshold` / `urandomMinReseedSecs` / `bootId` / `randomUuid` **完全独立**；**不**读宿主机熵池；写 / `O_DIRECTORY` / 目录 / 相近路径 **不接管**；**不**实现 entropy 行为 / 随机数生成 / 重播 / 其它 random sysctl；`linux.files` 精确路径优先；JSON 浮点 / 字符串 / 布尔 / null / 负数 / 越界 / 未知字段 → 解析失败（字段路径 `linux.proc.randomPoolSize`） |
| `writeWakeupThreshold` | 否 | 精确 JSON Number 整数 **`0..2147483647`**（无默认值） | **仅当键存在**时只读接管精确 `/proc/sys/kernel/random/write_wakeup_threshold`；内容为十进制 ASCII + 单个 LF；**缺失**回落旧 open、不发事件；与 `entropyAvail` / `randomPoolSize` / `urandomMinReseedSecs` / `bootId` / `randomUuid` **完全独立**，**不**互推、**不**读宿主机熵池；写 / `O_DIRECTORY` / 目录 / 相近路径 **不接管**；**不**实现写、阈值行为、随机数生成、重播或其它 random sysctl；`linux.files` 精确路径优先；JSON 浮点 / 字符串 / 布尔 / null / 负数 / 越界 / 未知字段 → 解析失败（字段路径 `linux.proc.writeWakeupThreshold`） |
| `urandomMinReseedSecs` | 否 | 精确 JSON Number 整数 **`0..2147483647`**（无默认值） | **仅当键存在**时只读接管精确 `/proc/sys/kernel/random/urandom_min_reseed_secs`；内容为十进制 ASCII + 单个 LF；**缺失**回落旧 open、不发事件；与 `entropyAvail` / `randomPoolSize` / `writeWakeupThreshold` / `bootId` / `randomUuid` **完全独立**，**不**互推、**不**读宿主机熵池；写 / `O_DIRECTORY` / 目录 / 相近路径 **不接管**；**不**实现写、阈值行为、随机数生成、重播或其它 random sysctl；`linux.files` 精确路径优先；JSON 浮点 / 字符串 / 布尔 / null / 负数 / 越界 / 未知字段 → 解析失败（字段路径 `linux.proc.urandomMinReseedSecs`） |
| `oomScoreAdj` | 否 | 精确 JSON Number 整数 **`-1000..1000`**（无默认值） | **仅当键存在**时服务 `/proc/self/oom_score_adj` 与 `/proc/<emulatorPid>/oom_score_adj`；**缺失**回落旧 open、不发事件；**不**写、**不**与 `oomAdj`/`oomScore` 互相推导、**不**实现策略；JSON null / 小数 / 非 Number / 越界 → 解析失败 |
| `oomScore` | 否 | 精确 JSON Number 整数 **`0..2000`**（无默认值） | **仅当键存在**时服务 `/proc/self/oom_score` 与 `/proc/<emulatorPid>/oom_score`；**缺失**回落旧 open、不发事件；**只读**；**不**从 `oomScoreAdj`/`oomAdj` 推导、**不**动态计算；JSON null / 小数 / 非 Number / 越界 → 解析失败 |
| `oomAdj` | 否 | 精确 JSON Number 整数，合法值仅 **`-17`** 或 **`-16..15`**（无默认值） | **仅当键存在**时服务旧版 `/proc/self/oom_adj` 与 `/proc/<emulatorPid>/oom_adj`；**缺失**回落旧 open、不发事件；**只读**；**不**从 `oomScoreAdj`/`oomScore` 推导、换算或回写；**不**写、**不**实现 OOM 策略或 syscall；JSON null / 小数 / 非 Number / 越界 → 解析失败 |
| `selinuxContext` | 否 | 非空 String：可打印 ASCII（`0x21..0x7E`），**无空白/控制字符**，长度 **1..256**（无默认、不 trim） | **仅当键存在**时：① `/proc/self\|pid/attr/current`；② native **`libselinux.so!getcon`** 返回配置上下文（配对 **`freecon`** 仅释放本 hook 分配）；**缺失**回落旧 open / 不拦截 getcon；**不**与 `fileSelinuxContexts` 互相推导；JSON null / 空串 / 空白 / 控制 / 超长 / 非 String → 解析失败 |
| `fileSelinuxContexts` | 否 | **JSONObject** 路径→上下文 map：键为 **POSIX 绝对路径**（入库归一化，与 `filesystem.stat` 相同规则；重复归一化路径拒绝）；值为与 `selinuxContext` 相同规则的可打印 ASCII 1..256；**允许空对象 `{}`**（无默认、不从 `selinuxContext` 推导） | **仅当键存在**时服务 native **`libselinux.so!getfilecon`** 与 **`lgetfilecon`**（**同一**字面归一化路径 map；**不**解析符号链接；未配置路径 **不拦截**）；配对 **`freecon`** 仅释放本 hook 分配；sidecar 仅长度摘要、**无**路径/上下文原文；**不**实现 fgetfilecon/getpeercon/setcon/xattr/策略；JSON null / 非 Object / 相对路径 / 非法上下文 → 解析失败 |
| `comm` | 否 | 非空 String：可打印 ASCII（`0x20..0x7E`），**无 CR/LF**，长度 **1..15** ASCII 字节（`TASK_COMM_LEN-1`；无默认、不 trim） | **仅当键存在**时服务 `/proc/self/comm`、`/proc/<emulatorPid>/comm`，以及只读 **`/proc/self/task/<tid>/comm`** 与 **`/proc/<emulatorPid>/task/<tid>/comm`**（`tid`=`process.tid`，缺省回落 emulator pid；**仅**该 tid）；**缺失**回落旧 open、不发事件；**绝不**从 `process.processName` / `threadName` 推导；**不**写、**不**枚举 task 目录、**不**服务其它 tid；JSON null / 空串 / 控制 / 非 ASCII / 超长 / 非 String → 解析失败 |
| `wchan` | 否 | 非空 String：可打印 ASCII（`0x20..0x7E`），**无 CR/LF**，长度 **1..255** ASCII 字节（无默认、不 trim；可配置 `"0"`） | **仅当键存在**时服务 `/proc/self/wchan`、`/proc/<emulatorPid>/wchan`，以及只读 **`/proc/self/task/<tid>/wchan`** 与 **`/proc/<emulatorPid>/task/<tid>/wchan`**（`tid`=`process.tid`，缺省回落 emulator pid；**仅**该 tid）；**缺失**回落旧 open、不发事件；**固定分析标记**，**不**实现调度/阻塞行为或从状态推导；**不**写、**不**枚举 task 目录、**不**服务其它 tid；JSON null / 空串 / 控制 / 非 ASCII / 超长 / 非 String → 解析失败 |
| `dumpable` | 否 | 精确 JSON Number 整数 **`0..2`**（无默认值） | **仅当键存在**时，`prctl(PR_GET_DUMPABLE)` 在 ARM32/ARM64 返回该值并发 sidecar；**缺失**时 **ARM32 仍固定返回 0、ARM64 仍 UOE**（历史行为）；**不**实现 `PR_SET_DUMPABLE` 配置化/状态突变/ptrace 语义；JSON null / 小数 / 非 Number / 越界 → 解析失败 |
| `nice` | 否 | 精确 JSON Number 整数 **`-20..19`**（无默认值） | **仅当键存在**时：① ARM32 `getpriority`（NR **96**）与 ARM64 `getpriority`（NR **141**）在 `which==PRIO_PROCESS(0)` 且 `who==0` 或当前 emulator pid 时返回原始 syscall 编码 **`rawResult=20-nice`**（nice `-20..19` → rawResult **40..1**；Bionic exported `getpriority()` 再用 **`20-rawResult`** 转回用户态 nice），并发 sidecar；② `/proc/self\|pid/stat` **字段 18 `priority`=`20+nice`、字段 19 `nice`=`nice`**。**缺失**不得新增默认模型：getpriority 仍历史返回 **0** 且不写事件（**不是** `20-0`）；stat 仍字面 **`priority=20` / `nice=0`**（**不**从其它字段推导）。其它 `which`/`who` 仍历史 0、不写事件。**不**实现 `setpriority`、不维护可变调度状态。JSON null / 字符串 / 小数 / 布尔 / 越界 / 未知键 → 解析失败 |
| `seccompMode` | 否 | 精确 JSON Number 整数 **仅 `0`、`1` 或 `2`**（无默认值；与 dumpable **独立**） | **仅当键存在**时：① `prctl(PR_GET_SECCOMP)`（option **21**）返回该值；② 生成的 `/proc/self\|pid/status` **追加** 行 `Seccomp:\t<0\|1\|2>\n`。键缺失：prctl 历史 UOE；status **不**写 Seccomp 行。**不**实现 `PR_SET_SECCOMP`/BPF/seccomp 系统调用 |
| `noNewPrivs` | 否 | 严格 JSON Boolean（无默认；与 seccompMode/dumpable **独立**） | **仅当键存在**时：① `prctl(PR_GET_NO_NEW_PRIVS)`（option **39**）返回 **1**/ **0**；② status **追加** 行 `NoNewPrivs:\t<0\|1>\n`。键缺失：prctl 历史 UOE；status **不**写 NoNewPrivs 行。**不**改 `PR_SET_NO_NEW_PRIVS`、不从 seccomp 推导 |
| `limits` | 否 | JSONArray of 非空可打印 ASCII 行（`0x20..0x7E`，无 CR/LF/NUL；**允许 `[]`**；无默认、不 trim） | **键存在时优先级最高（结构化源）**：服务 `/proc/self\|pid/limits`，每行 + LF；**`[]` → 空文件且绝不派生 nofile 行**。键缺失时：若 `linux.rlimits.nofile` 显式则派生一行 `Max open files`（见 `linux.rlimits`）；否则不服务、保留旧 open。**不**由本字段实现 `getrlimit`/`setrlimit`/`prlimit`；**不**从本文本反解析 `nofile`（单向派生）。`linux.files` 精确路径优先于本字段与派生；sidecar `format=limits,bytes=…` 无原文；JSON null / 非数组 / 空串 / 控制字符 / 非 String → 解析失败 |
| `signalBlockedHex` | 否 | 与 cap 掩码 **相同校验**：恰好 16 位 hex，入库小写（无默认） | **仅当键存在**时 status 追加 `SigBlk:\t<16hex>\n`（在 CapInh **之前**）。与其它 Sig* **独立**。**不**实现信号投递/`sigprocmask`；`linux.files` 优先；非法值 → 解析失败 |
| `signalIgnoredHex` | 否 | 同上 | **仅当键存在**时 status 追加 `SigIgn:\t…`（SigBlk 之后、SigCgt 之前）。**不**实现 SIG_IGN/`sigaction`；非法值 → 解析失败 |
| `signalCaughtHex` | 否 | 同上 | **仅当键存在**时 status 追加 `SigCgt:\t…`（SigIgn 之后、CapInh 之前）。**不**实现 handler/`pthread_sigmask`；非法值 → 解析失败 |
| `capInheritableHex` | 否 | 与 `capEffectiveHex` **相同校验**：恰好 16 位 hex，入库小写（无默认） | **仅当键存在**时：① status 追加 `CapInh:\t…`；② 参与只读 **`capget` V3**（见下）。与其它 Cap* **独立**。键缺失无 CapInh 行且 capget 槽位写 0。**不**实现 `capset`/prctl ambient；`linux.files` 优先；非法值 → 解析失败 |
| `capEffectiveHex` | 否 | 非空 String：**恰好 16** 位十六进制（`[0-9a-fA-F]`，无 `0x`/空白）；入库 **小写**（无默认、不 trim） | **仅当键存在**时：status `CapEff` + 只读 **`capget` V3** effective 槽。与其它 Cap* **独立**；非法值 → 解析失败 |
| `capPermittedHex` | 否 | 与 `capEffectiveHex` **相同校验**：恰好 16 位 hex，入库小写（无默认） | **仅当键存在**时：status `CapPrm` + 只读 **`capget` V3** permitted 槽。与其它 Cap* **独立**；非法值 → 解析失败 |
| `capBoundingHex` | 否 | 与 `capEffectiveHex` **相同校验**：恰好 16 位 hex，入库小写（无默认） | **仅当键存在**时 status 追加 `CapBnd`（**不**进 `capget`）。与其它 Cap* **独立**；非法值 → 解析失败 |
| `capAmbientHex` | 否 | 与 `capEffectiveHex` **相同校验**：恰好 16 位 hex，入库小写（无默认） | **仅当键存在**时 status 追加 `CapAmb`（**不**进 `capget`）。**不**实现 `capset`、prctl ambient 变更；非法值 → 解析失败 |

进程身份（Name/Pid/PPid/Uid/Gid 等）取自 **`process.*`**，并以 emulator 的 `processName`/`pid` 等为回退：`processName`、`pid`、`ppid`、`pgid`/`sid`（各自缺省当前 pid，互不推导）、`uid`/`euid`、`gid`/`egid`。可选 **`process.supplementaryGids`** 仅在键显式存在时写入 status **Groups** 行（**不**从 `gid`/`egid` 推导；缺键不新增该行）。

### 接入路径（self 与配置 pid）

当 `linux.proc` 已配置时，下列路径返回 `ByteArrayFileIO`（含空字节）；**`boot_id` / `random_uuid` / `entropy_avail` / `random_poolsize` / `write_wakeup_threshold` / `urandom_min_reseed_secs` / `oom_score_adj` / `oom_score` / `oom_adj` / `selinux_context` / `comm` / `wchan` 另需对应键显式存在**。另：**`environ` 不依赖 `linux.proc` 节点**，见 `linux.environ`。另：**`limits` 在 `linux.proc.limits` 缺键且 `linux.rlimits.nofile` 显式时亦不依赖 `linux.proc` 节点**（`linux.proc` 节可完全缺失）：

| format | 路径 |
| --- | --- |
| `status` | `/proc/self/status`、`/proc/<emulatorPid>/status` |
| `cmdline` | `/proc/self/cmdline`、`/proc/<emulatorPid>/cmdline` |
| `cgroup` | `/proc/self/cgroup`、`/proc/<emulatorPid>/cgroup` |
| `stat` | `/proc/self/stat`、`/proc/<emulatorPid>/stat` |
| `statm` | `/proc/self/statm`、`/proc/<emulatorPid>/statm` |
| `io` | `/proc/self/io`、`/proc/<emulatorPid>/io` |
| `oom_score_adj` | `/proc/self/oom_score_adj`、`/proc/<emulatorPid>/oom_score_adj`（**仅当** `oomScoreAdj` 已配置） |
| `oom_score` | `/proc/self/oom_score`、`/proc/<emulatorPid>/oom_score`（**仅当** `oomScore` 已配置） |
| `oom_adj` | `/proc/self/oom_adj`、`/proc/<emulatorPid>/oom_adj`（**仅当** `oomAdj` 已配置；精确路径；**不含**其它 pid / `task/` / 目录 / 写打开） |
| `selinux_context` | `/proc/self/attr/current`、`/proc/<emulatorPid>/attr/current`（**仅当** `selinuxContext` 已配置；**仅** current） |
| `comm` | `/proc/self/comm`、`/proc/<emulatorPid>/comm`；以及 `/proc/self/task/<tid>/comm`、`/proc/<emulatorPid>/task/<tid>/comm`（**仅当** `comm` 已配置；`tid`=`process.tid` 或 emulator pid；**仅**该 tid） |
| `wchan` | `/proc/self/wchan`、`/proc/<emulatorPid>/wchan`；以及 `/proc/self/task/<tid>/wchan`、`/proc/<emulatorPid>/task/<tid>/wchan`（**仅当** `wchan` 已配置；`tid`=`process.tid` 或 emulator pid；**仅**该 tid） |
| `limits` | `/proc/self/limits`、`/proc/<emulatorPid>/limits`（**`linux.proc.limits` 显式数组含 `[]` 优先**；否则仅当 `linux.rlimits.nofile` 显式时派生一行 `Max open files`；`linux.files` 优先；两键皆缺回落旧 open） |
| `environ` | `/proc/self/environ`、`/proc/<emulatorPid>/environ`（**不**依赖 `linux.proc`；与 `linux.environ` / loader 同一有效列表；见 `linux.environ`） |
| `boot_id` | **仅** `/proc/sys/kernel/random/boot_id`（全局路径，非 self/pid；**仅当** `bootId` 已配置） |
| `random_uuid` | **仅** `/proc/sys/kernel/random/uuid`（全局路径；**仅当** `randomUuid` 已配置；固定标记） |
| `entropy_avail` | **仅** `/proc/sys/kernel/random/entropy_avail`（全局路径，非 self/pid；**仅当** `entropyAvail` 已配置；只读精确路径；写 / 目录 / 相近路径不接管） |
| `random_poolsize` | **仅** `/proc/sys/kernel/random/poolsize`（全局路径，非 self/pid；**仅当** `randomPoolSize` 已配置；只读精确路径；写 / 目录 / 相近路径不接管） |
| `write_wakeup_threshold` | **仅** `/proc/sys/kernel/random/write_wakeup_threshold`（全局路径，非 self/pid；**仅当** `writeWakeupThreshold` 已配置；只读精确路径；写 / 目录 / 相近路径不接管） |
| `urandom_min_reseed_secs` | **仅** `/proc/sys/kernel/random/urandom_min_reseed_secs`（全局路径，非 self/pid；**仅当** `urandomMinReseedSecs` 已配置；只读精确路径；写 / 目录 / 相近路径不接管） |

**`linux.files` 优先**：同一路径若命中 `linux.files`，只发 `linux_file` 事件，**不**走 `linux.proc` / environ / nofile 派生 limits 结构化路径、**不**发 `linux_proc` sidecar。

### 渲染规则摘要

- **status：** 至少稳定输出 `Name`、`State`（状态字母 + Linux 英文描述）、`Tgid`、`Pid`、`PPid`、`TracerPid`、`Uid` 四列、`Gid` 四列、`Threads`、`VmSize`/`VmRSS`（kB）。每行 LF。当 **`process.supplementaryGids` 显式存在**时，在 Gid 之后、Threads 之前追加 `Groups:\t<gid1> <gid2>...\n`（空格分隔、配置顺序）；显式 `[]` 为 `Groups:\t\n`。缺该键不新增 Groups 行，status 字节与历史一致。**不**从 `gid`/`egid` 推导。可选追加（均无默认、互不推导）：**`signalBlockedHex`** → `SigBlk`；**`signalIgnoredHex`** → `SigIgn`；**`signalCaughtHex`** → `SigCgt`；**`capInheritableHex`** → `CapInh`；**`capPermittedHex`** → `CapPrm`；**`capEffectiveHex`** → `CapEff`；**`capBoundingHex`** → `CapBnd`；**`capAmbientHex`** → `CapAmb`；**`seccompMode`** → `Seccomp`；**`noNewPrivs`** → `NoNewPrivs`。顺序 SigBlk→SigIgn→SigCgt→CapInh→CapPrm→CapEff→CapBnd→CapAmb→Seccomp→NoNewPrivs。键均缺失时 status 字节与历史一致。
  - Uid：`real=uid`，`effective=euid`，`saved=euid`，`fs=euid`
  - Gid：`real=gid`，其余三列 `egid`
  - 进程名用于行文本时仅把 NUL/CR/LF 替换为 `?`（不改配置本身）
- **cmdline：** 显式数组 → 每参数 UTF-8 后追加 **NUL**（含空参数）；显式 `[]` → 空文件；缺失 → `processName` + NUL
- **cgroup：** 显式每项 + LF；`[]` 空文件；缺失 → `0::/\n`
- **stat：** 恰好 **52** 个空格分隔字段 + 末尾 LF；字段 2 为 `(processName)`（名称可含空格）；**字段 5 `pgrp`** 读 `process.pgid`（键缺失用当前 pid），**字段 6 `session`** 读 `process.sid`（键缺失用当前 pid）；二者**互不推导**、**不**从 `ppid` 推导；`tty`/`tpgid`=0，`flags`=4194304；**字段 18 `priority` / 字段 19 `nice`：** 仅当 `linux.proc.nice` 显式配置时写 `20+nice` / `nice`，**缺键保持历史字面 `20` / `0`**（**不**从其它字段推导）；`num_threads`/`starttime`/`vsize`/`rss` 来自配置，`rsslim` 字面量 `18446744073709551615`，`exit_signal`=17，`processor`=0，其余按位置填 0
- **statm：** **无需新配置字段**。输出标准 **七列** 十进制空格分隔 + 末尾 LF，字段依次为 `size resident shared text lib data dt`：
  - `size`：`virtualMemoryBytes` 按 `ARMEmulator.PAGE_ALIGN`（4KiB）**安全向上取整为页数**（商 + 余数，避免 `Long.MAX_VALUE` 上 `bytes + page - 1` 加法溢出）
  - `resident`：直接使用 `residentSetPages`
  - `shared` / `text` / `lib` / `data` / `dt`：**固定 `0`**（非独立配置，亦非真实拆分）
- **io：** 输出内核风格 **七行** `key: value`（冒号后一空格）+ 每行 LF，顺序固定为：
  - `rchar` ← `rchar`
  - `wchar` ← `wchar`
  - `syscr` ← `syscr`
  - `syscw` ← `syscw`
  - `read_bytes` ← `readBytes`
  - `write_bytes` ← `writeBytes`
  - `cancelled_write_bytes` ← `cancelledWriteBytes`
  - 各键缺省为 `0`；值以十进制输出（含 `Long.MAX_VALUE`）
- **boot_id：** 仅当 `linux.proc.bootId` 显式配置时，对精确路径 `/proc/sys/kernel/random/boot_id` 返回 UTF-8 **`<uuid>\n`**（**37 字节**；UUID 36 字符 + LF）。与 `random.uuid` / `randomUuid` / `entropyAvail` / `randomPoolSize` / `writeWakeupThreshold` / `urandomMinReseedSecs` **完全独立**，**永不**生成或推导。键缺失时该路径回落旧 open 且 **不**发 `linux_proc` 事件。
- **random_uuid：** 仅当 `linux.proc.randomUuid` 显式配置时，对精确路径 `/proc/sys/kernel/random/uuid` 返回 UTF-8 **`<uuid>\n`**（**37 字节**）。**固定可复现标记**（每次 open 相同内容），**不**按读生成新 UUID。与 `random.uuid` / `bootId` / `entropyAvail` / `randomPoolSize` / `writeWakeupThreshold` / `urandomMinReseedSecs` **完全独立**。键缺失回落旧 open / 无事件。
- **entropy_avail：** 仅当 `linux.proc.entropyAvail` 显式配置时，对精确路径 `/proc/sys/kernel/random/entropy_avail` 返回 **十进制 ASCII + 单个 LF**（如 `256\n`）。**无默认**；与 `randomPoolSize` / `writeWakeupThreshold` / `urandomMinReseedSecs` / `bootId` / `randomUuid` **完全独立**，**不**互推。键缺失回落旧 open / 无事件。**只读**；写、`O_DIRECTORY`、目录与相近路径 **不接管**。**不**读宿主机熵池；**不**实现写、随机数生成、entropy 行为、重播或其它 random sysctl。`linux.files` 精确路径优先。
- **random_poolsize：** 仅当 `linux.proc.randomPoolSize` 显式配置时，对精确路径 `/proc/sys/kernel/random/poolsize` 返回 **十进制 ASCII + 单个 LF**（如 `4096\n`）。**无默认**；与 `entropyAvail` / `writeWakeupThreshold` / `urandomMinReseedSecs` / `bootId` / `randomUuid` **完全独立**。键缺失回落旧 open / 无事件。**只读**；写、`O_DIRECTORY`、目录与相近路径 **不接管**。**不**读宿主机熵池；**不**实现写、随机数生成、entropy 行为、重播或其它 random sysctl。`linux.files` 精确路径优先。
- **write_wakeup_threshold：** 仅当 `linux.proc.writeWakeupThreshold` 显式配置时，对精确路径 `/proc/sys/kernel/random/write_wakeup_threshold` 返回 **十进制 ASCII + 单个 LF**（如 `1024\n`）。**无默认**；与 `entropyAvail` / `randomPoolSize` / `urandomMinReseedSecs` / `bootId` / `randomUuid` **完全独立**，**不**互推。键缺失回落旧 open / 无事件。**只读**；写、`O_DIRECTORY`、目录与相近路径 **不接管**。**不**读宿主机熵池；**不**实现写、阈值行为、随机数生成、重播或其它 random sysctl。`linux.files` 精确路径优先。
- **urandom_min_reseed_secs：** 仅当 `linux.proc.urandomMinReseedSecs` 显式配置时，对精确路径 `/proc/sys/kernel/random/urandom_min_reseed_secs` 返回 **十进制 ASCII + 单个 LF**（如 `60\n`）。**无默认**；与 `entropyAvail` / `randomPoolSize` / `writeWakeupThreshold` / `bootId` / `randomUuid` **完全独立**，**不**互推。键缺失回落旧 open / 无事件。**只读**；写、`O_DIRECTORY`、目录与相近路径 **不接管**。**不**读宿主机熵池；**不**实现写、阈值行为、随机数生成、重播或其它 random sysctl。`linux.files` 精确路径优先。
- **oom_score_adj：** 仅当 `linux.proc.oomScoreAdj` 显式配置时，对 `/proc/self/oom_score_adj` 与 `/proc/<emulatorPid>/oom_score_adj` 返回 UTF-8 **十进制整数字符串 + LF**（如 `0\n`、`-1000\n`）。**无默认、不推导**；键缺失回落旧 open / 无事件。**只读**（不写）；**不**与 `oomScore` / `oomAdj` 互相推导；**不**实现 OOM 策略。
- **oom_score：** 仅当 `linux.proc.oomScore` 显式配置时，对 `/proc/self/oom_score` 与 `/proc/<emulatorPid>/oom_score` 返回 UTF-8 **十进制整数字符串 + LF**（如 `0\n`、`2000\n`）。**无默认**；**不**从 `oomScoreAdj` / `oomAdj` 推导、**不**动态计算；键缺失回落旧 open / 无事件。**只读**。
- **oom_adj：** 仅当 `linux.proc.oomAdj` 显式配置时，对精确路径 `/proc/self/oom_adj` 与 `/proc/<emulatorPid>/oom_adj` 返回 UTF-8 **十进制整数字符串 + LF**（如 `-17\n`、`0\n`、`15\n`）。合法值仅 **`-17`** 或 **`-16..15`**。**无默认**；**不**从 `oomScoreAdj` / `oomScore` 推导、换算或回写；键缺失回落旧 open / 无事件。**只读**；写打开、其它 pid、目录与相似路径（如 `oom_score_adj`、`task/<tid>/oom_adj`）**不接管**。**不**实现写入、OOM 策略或 syscall。
- **selinux_context：** 仅当 `linux.proc.selinuxContext` 显式配置时，对 `/proc/self/attr/current` 与 `/proc/<emulatorPid>/attr/current` 返回 UTF-8 **上下文 + LF**。**无默认、不推导**；键缺失回落旧 open / 无事件。**只读**；**不**实现 getenforce、libselinux、写、xattr、其它 `attr/*` 路径或 Root 相关函数。
- **comm：** 仅当 `linux.proc.comm` 显式配置时，对 `/proc/self/comm`、`/proc/<emulatorPid>/comm`，以及 **`/proc/self/task/<tid>/comm`** 与 **`/proc/<emulatorPid>/task/<tid>/comm`** 返回相同 UTF-8 **任务名 + LF**。`tid` 取自 **`process.tid`**，缺省回落 **emulator pid**；**仅**服务该 tid，**不**枚举 task 目录、**不**服务其它 tid。**无默认**；**绝不**从 `process.processName` / `threadName` 推导；键缺失回落旧 open / 无事件。**只读**；**不**改 `PR_SET_NAME` 动态行为。
- **wchan：** 仅当 `linux.proc.wchan` 显式配置时，对 `/proc/self/wchan`、`/proc/<emulatorPid>/wchan`，以及 **`/proc/self/task/<tid>/wchan`** 与 **`/proc/<emulatorPid>/task/<tid>/wchan`** 返回相同 UTF-8 **值 + LF**（如 `0\n`、`futex_wait_queue_me\n`）。`tid` 取自 **`process.tid`**，缺省回落 **emulator pid**；**仅**服务该 tid，**不**枚举 task 目录、**不**服务其它 tid。**无默认、不推导**；键缺失回落旧 open / 无事件。**固定分析标记**；**不**实现调度/阻塞行为或写。
- **dumpable：** 仅当 `linux.proc.dumpable` 显式配置时，`prctl(PR_GET_DUMPABLE)`（ARM32/ARM64）返回配置整数 **0..2**，并发 **一次** sidecar。**无默认**；键缺失保留历史行为（ARM32 固定 `0`、ARM64 `UnsupportedOperationException`）。**不**改 `PR_SET_DUMPABLE`、不维护 dumpable 运行时状态、不改 ptrace 或其它 prctl 选项。
- **nice：** 仅当 `linux.proc.nice` 显式配置时：① `getpriority`（ARM32 NR **96** / ARM64 NR **141**）在 `which==PRIO_PROCESS(0)` 且 `who==0` 或 `emulator.getPid()` 时返回原始 syscall 编码 **`rawResult=20-nice`**（**40..1**），并发 **一次** sidecar。JSON `nice=n` 是用户态 nice；Linux 内核不能用负值表示成功，故 raw 为 `20-n`。native so 经 Bionic exported `getpriority()` 再以 **`20-rawResult`** 得到 `n`。其它 `which`/`who` 仍历史返回 **0** 且**不**写事件。② `/proc/self|pid/stat` 字段 18 `priority`=`20+nice`、字段 19 `nice`=`nice`（stat 写用户态 nice，**不是** raw syscall 编码）。**无默认、不推导**；键缺失 getpriority 仍历史 0（**不是** `20-0`）、stat 仍 `20`/`0`。**不**改 `setpriority`、不维护调度状态、不改 `/proc/status` 或其它 stat 字段。
- **seccompMode：** 仅当显式配置时：① `prctl(PR_GET_SECCOMP)`（option **21**）返回 **0/1/2** 并 sidecar；② status 追加 `Seccomp:\t…\n`。**无默认**；与 dumpable **独立**；键缺失 → prctl UOE、status 无 Seccomp 行。**不**实现 `PR_SET_SECCOMP`/BPF/seccomp 系统调用。
- **noNewPrivs：** 仅当显式配置时：① `prctl(PR_GET_NO_NEW_PRIVS)`（option **39**）返回 **1/0** 并 sidecar；② status 追加 `NoNewPrivs:\t…\n`。**无默认**；与 seccompMode/dumpable **独立**；键缺失 → prctl UOE、status 无 NoNewPrivs 行。**不**改 `PR_SET_NO_NEW_PRIVS`、不从 seccomp 推导。
- **limits：** 优先级：① `linux.files` 精确路径（含 pid→self）；② **`linux.proc.limits` 显式数组**（含 `[]` 空文件）— 存在该键时**绝不**派生 nofile 行；③ 否则仅当 **`linux.rlimits.nofile` 显式**（`linux.proc` 节可缺失）时派生**恰好一行** UTF-8 + 末尾 LF，列对齐为 Android/Linux 常见 `%-25s %-20s %-20s %-10s`：名称固定 `Max open files`，soft/hard 为配置的十进制数字，units 固定 `files`；无表头、无其它限制行、无 unlimited、无地址、无宿主值。两键皆缺 → 回落旧 open / 无事件。**只读**；**不**由 `linux.proc.limits` 实现 `getrlimit`/`setrlimit`/`prlimit`；**不**从 limits 文本反解析 `nofile`（单向派生）。sidecar `format=limits`，摘要无行原文。
- **signalBlockedHex / signalIgnoredHex / signalCaughtHex：** 显式配置时写 status `SigBlk`/`SigIgn`/`SigCgt`（在 Cap* 之前）。**固定分析标记**；**不**实现信号投递、`sigaction`、`pthread_sigmask`/`sigprocmask` 或动态行为。
- **capInheritableHex / capPermittedHex / capEffectiveHex：** 显式配置时写 status 对应 Cap 行；且当 **三者至少其一** 配置时，启用只读 **`capget` V3**（ARM32 NR=184 / ARM64 NR=90）：仅 `version=_LINUX_CAPABILITY_VERSION_3`、`pid=0` 或 emulator/配置 pid、header/data 非 null；写入 **2** 个 12 字节 data 槽（低/高 32 位，字段顺序 effective/permitted/inheritable）；未配置字段槽位 **0**；返回 **0**；sidecar `api=capget` 仅摘要（`result=0,version=3,pid=…,slots=2,fields=…`，**无** mask 原文）。version/pid/指针不满足或三者均未配置 → **不拦截**。
- **capBoundingHex / capAmbientHex：** 仅 status 行；**不**进入 `capget`；**不**实现 `capset`、prctl ambient 变更。

### Sidecar

成功由 `linux.proc`、nofile 派生 `limits` 或 structured `environ` 打开时 emit **一次**（`statm`/`io`/`boot_id`/`random_uuid`/`entropy_avail`/`random_poolsize`/`write_wakeup_threshold`/`urandom_min_reseed_secs`/`oom_score_adj`/`oom_score`/`oom_adj`/`selinux_context`/`comm`/`wchan`/`limits`/`environ` 沿用同一 sidecar 形态）；`prctl(PR_GET_DUMPABLE)` / `prctl(PR_GET_SECCOMP)` / `prctl(PR_GET_NO_NEW_PRIVS)` / **`getpriority`** 命中配置时同 kind：

| 字段 | 值 |
| --- | --- |
| `kind` | `linux_proc` |
| `api` | `read("<原请求绝对路径>")` 或 **`prctl(PR_GET_DUMPABLE)`** / **`prctl(PR_GET_SECCOMP)`** / **`prctl(PR_GET_NO_NEW_PRIVS)`** / **`capget`** / **`getpriority`**（仅对应配置命中时） |
| `source` | `json-config` |
| `value` | 文件：`path=<path>,format=…,bytes=<长度>`；dumpable：`result=<0\|1\|2>`；seccomp：`field=seccompMode,result=<0\|1\|2>`；no_new_privs：`field=noNewPrivs,result=0\|1`；capget：`result=0,version=3,pid=…,slots=2,fields=…`（**无** mask 原文）；getpriority：`field=nice,nice=<n>,rawResult=<20-n>,which=<n>,who=<n>`（**无**地址；**不**把 rawResult 写作 `result`） |
| `note` | 进程文件（含 oom_score_adj / oom_score）：`读取配置的进程 proc 文件 <path>`；limits：`读取配置的进程 limits 文件 <path>`（**不**写行原文 / soft / hard）；oom_adj：`读取配置的 OOM 调整值 <path>`（**不**写 oomAdj 数字）；boot_id：`读取配置的内核 boot_id <path>`；random uuid：`读取配置的内核 random uuid <path>`；entropy_avail / poolsize / write_wakeup_threshold / urandom_min_reseed_secs：`读取配置的随机池状态 <path>`（**不**写配置数值）；selinux：`读取配置的进程 SELinux 上下文 <path>`；dumpable：`读取配置的进程 dumpable 标志`；seccomp：`读取配置的进程 seccomp 模式`；no_new_privs：`读取配置的进程 no_new_privs 标志`；capget：`读取配置的进程能力位（capget V3）`；getpriority：`读取配置的进程 nice 值（原始系统调用编码 20-nice；Bionic exported getpriority 再转回用户态 nice）` |

### 明确未覆盖

- `/proc/self/task` 完整线程树与线程状态文件
- `sched` / `schedstat`
- `status` 中其它未配置扩展行（**已**支持可选 SigBlk/SigIgn/SigCgt + 五条 Cap 行 + 只读 **`capget` V3** 使用 Inh/Prm/Eff；**仍不含** 信号投递/`sigaction`/`pthread_sigmask`、`capset`、CapBnd/CapAmb 的 capget、prctl ambient 变更）
- `statm` 后五列（`shared`/`text`/`lib`/`data`/`dt`）的独立配置或真实拆分
- namespace 文件
- `/proc/net/route` 三别名见 `network.ipv4Routes`；`/proc/net/dev` 三别名见 `network.interfaceStats`；`/proc/net/if_inet6` 三别名见 `network.ipv6Addresses`；`/proc/net/arp` 三别名见 `network.arpEntries`；`/proc/net/igmp` 三别名见 `network.igmpMemberships`；`/proc/net/igmp6` 三别名见 `network.igmp6Memberships`；`/proc/net/dev_mcast` 三别名见 `network.linkLayerMulticastEntries`；`/proc/net/wireless` 三别名见 `network.wirelessProcStats`；其余 `/proc/net/*` 仍可用 `linux.files` 手工提供
- 写熵池、随机数生成、entropy 行为、阈值行为、重播、读宿主机熵池、其它 random sysctl（只读 `entropy_avail`/`poolsize` 见 `entropyAvail`/`randomPoolSize`；只读 `write_wakeup_threshold`/`urandom_min_reseed_secs` 见 `writeWakeupThreshold`/`urandomMinReseedSecs`；`boot_id`/`uuid` 固定标记见 `bootId`/`randomUuid`；其余可用 `linux.files` 手工）
- 写 `oom_adj` / 写 `oom_score_adj`、OOM killer 策略、从 `oomScoreAdj` 动态计算 `oom_score` 或 `oomAdj`（三字段互不推导）
- getenforce / libselinux / 写 SELinux / xattr / `attr/prev` 等其它 attr 路径 / Root 函数
- 写 `comm` / 写 `task/<tid>/comm`、枚举 task 目录、其它 tid 的 comm、从 processName/threadName 推导 comm
- 调度/阻塞语义、写 `wchan` / 写 `task/<tid>/wchan`、其它 tid 的 wchan、从运行状态推导 wchan
- `PR_SET_DUMPABLE` / `PR_SET_SECCOMP` 配置化、dumpable/seccomp 状态机、BPF 过滤器、seccomp 系统调用、`no_new_privs` 策略、ptrace 与其它 prctl 选项
- **`setpriority` / 调度语义**（`linux.proc.nice` **仅**只读 `getpriority` 与 stat 字段 18/19；不维护可变 nice 状态、不实现 `PRIO_PGRP`/`PRIO_USER`、不改其它 pid）
- **`setpgid` / `setsid` 状态变更**、进程组/会话运行时状态、进程树、其它 pid 的 `getpgid`/`getsid`、Java API（`process.pgid`/`sid` **仅**固定读取；**未**实现 set）
## linux.auxv

可选 **JSONObject**，配置 Android ELF 加载时栈上 **auxv** 的 HWCAP / PLATFORM / EXECFN 子集。实现类：`TraceEnvironmentConfig.LinuxAuxvConfig`；接线在 `AndroidElfLoader.initializeTLS`（模拟器 **构造期** 执行）。

**节点缺失 vs 显式空对象：**

| 状态 | `isLinuxAuxvConfigured()` | 行为摘要 |
| --- | --- | --- |
| **节点缺失** | `false` | **严格保留旧布局**：仅写 `AT_RANDOM=25`、`AT_PAGESZ=6`（页面大小仍为 `PAGE_ALIGN=0x1000`）；后续零内存形成 `AT_NULL` |
| **显式 `{}`** | `true` | 四个能力值默认 `0`；`platform32="v7l"`、`platform64="aarch64"`；`execFn` 键缺失 → 加载器用 **进程名** 回退 |
| **有字段** | `true` | 已配字段覆盖默认 |

### 字段说明（仅允许下列 7 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `hwcap32` | 否 | 精确 JSON 非负长整数，`0..0xffffffff`（`4294967295`） | `0` |
| `hwcap2_32` | 否 | 同上（防 32 位 auxv 静默截断） | `0` |
| `hwcap64` | 否 | 精确 JSON 非负长整数，`0..Long.MAX_VALUE` | `0` |
| `hwcap2_64` | 否 | 同上 | `0` |
| `platform32` | 否 | 非空 String，禁 NUL/CR/LF，≤64 UTF-8 字节 | `"v7l"` |
| `platform64` | 否 | 同上 | `"aarch64"` |
| `execFn` | 否 | String 或 JSON `null`；非 null 须非空、禁 NUL/CR/LF、≤4096 UTF-8 字节 | 键缺失：`null` 且 presence=false（回退进程名）；显式 `null`：presence=true、值为 null（同样回退进程名） |

未知键 / 错误类型 / 越界 → `IllegalArgumentException`，路径形如 `linux.auxv.hwcap32`。不保留 JSONObject。

### 对 `AndroidElfLoader.initializeTLS` 的影响

在既有 `AT_RANDOM` / `AT_PAGESZ` 之后，当节点**存在**时按指针宽度连续写入（32/64 分别取 `hwcap32`/`hwcap64` 与对应 platform）：

| 顺序 | type | 值来源 |
| --- | --- | --- |
| 1 | `AT_RANDOM` (25) | 既有随机区指针（`atRandomHex` 或栈保护区） |
| 2 | `AT_PAGESZ` (6) | `ARMEmulator.PAGE_ALIGN`（**不变**） |
| 3 | `AT_HWCAP` (16) | 32 位 `hwcap32` / 64 位 `hwcap64` |
| 4 | `AT_HWCAP2` (26) | 32 位 `hwcap2_32` / 64 位 `hwcap2_64` |
| 5 | `AT_PLATFORM` (15) | `writeStackString(platform32|platform64)` |
| 6 | `AT_EXECFN` (31) | `execFn` 非 null → 配置串；否则 → 进程名（`emulator.getProcessName()` / 已写的 programName） |
| 7 | `AT_NULL` (0) | value=`0`（**仅节点存在时显式写入**） |

写入使用按 `pointerSize` 的成对 helper：32 位 int 位型安全、64 位可写满 `Long.MAX_VALUE`；不改 argv/TLS 其它逻辑（environ 见 `linux.environ`）。

**观测限制：** `initializeTLS` 在 `AndroidEmulator` **构造期**运行，普通 sidecar sink 在 build 之后才注册，**本路径当前无法由后注册的 sidecar 可靠捕获**。

### 明确未实现

- 其它 auxv 项（`AT_PHDR`、`AT_BASE`、`AT_ENTRY`、`AT_UID` 等）
- 完整 CPU 拓扑等见下一节 `linux.cpu`（亲和性掩码已部分实现）

## linux.environ

可选 **JSONArray**，配置 Android ELF 加载时栈上 **environ** 与只读 **`/proc/self|pid/environ`** 使用的同一环境串列表。实现：`TraceEnvironmentConfig.isLinuxEnvironConfigured()` / `getLinuxEnviron()` / `getEffectiveLinuxEnviron()` / `LINUX_ENVIRON_BUILTIN_DEFAULTS`；接线在 `AndroidElfLoader` 构造期 `initializeTLS` 与 `LinuxFileSystem.open`（`ConfiguredProcFiles.renderEnviron`）。

**节点缺失 vs 显式数组：**

| 状态 | `isLinuxEnvironConfigured()` | 有效列表（`getEffectiveLinuxEnviron()`） |
| --- | --- | --- |
| **键缺失** | `false` | **固定四条内建默认**（`LINUX_ENVIRON_BUILTIN_DEFAULTS`）：`ANDROID_DATA=/data`、`ANDROID_ROOT=/system`、`PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin`、`NO_ADDR_COMPAT_LAYOUT_FIXUP=1` |
| **显式 `[]`** | `true` | **空列表**（替换默认） |
| **有条目** | `true` | **整表替换**为配置顺序条目（**不**与默认合并、**不**继承宿主环境） |

### 条目校验

| 规则 | 说明 |
| --- | --- |
| 类型 | 每项必须是 **String** |
| 形式 | `KEY=VALUE`（第一个 `=` 分隔；VALUE 可含 `=`，可为空） |
| KEY | 匹配 `[A-Za-z_][A-Za-z0-9_]*` |
| 去重 | KEY **不可重复**（大小写敏感） |
| 禁字符 | 整串禁 NUL / CR / LF |
| 长度 | 整串 **1..4096** 字符（有 `=` 后至少 `K=` 形式） |

错误路径：`linux.environ`（非数组）或 `linux.environ[i]` / 重复 KEY 说明。配置内保存不可变 `List<String>`；**不**保留 JSONArray。

### 对 `AndroidElfLoader` 的影响

构造时：`initializeTLS(getEffectiveLinuxEnviron())`（`config==null` 时用内建默认）。栈上每项为独立 NUL 结尾串；合法配置项均含 `=`。

### 对 `/proc/self|pid/environ` 的影响

| 路径 | 条件 | 内容 |
| --- | --- | --- |
| `/proc/self/environ`、`/proc/<emulatorPid>/environ` | **仅** self 与当前 emulator pid（**不含** `task/<tid>/environ`） | UTF-8 **每条 + NUL** 拼接（与 loader 同一有效列表；`[]` → 空文件） |

- **`linux.files` 优先**：精确路径及 pid→self 别名命中时只发 `linux_file`，**不**走本路径、**不**发 `linux_proc`。
- **只读**；**不**写、**不**实现 `task/<tid>/environ`。
- **不**依赖 `linux.proc` 节点存在。

### Sidecar（本结构化路径成功 open 时）

| 字段 | 值 |
| --- | --- |
| `kind` | `linux_proc` |
| `api` | `read("/proc/self/environ")` 等 |
| `value` | `path=…,format=environ,bytes=<n>` |
| `source` | **`json-config`** 当 `linux.environ` **显式配置**（含 `[]`）；**`unidbg-default`** 当 config 为 null 或 `linux.environ` 键缺失（返回内建四条默认） |
| `note` | `json-config` →「读取配置的进程环境」+ 路径；`unidbg-default` →「读取模拟器内建默认进程环境」+ 路径 |

（`linux.files` 命中时仍只发 `linux_file`，`source=json-config`，不变。）

### 明确未实现

- 写 environ、`task/<tid>/environ`
- 宿主进程环境继承 / 与默认合并

## linux.cpu

可选 **JSONObject**：**固定 CPU 亲和性掩码**（`sched_getaffinity`）+ 可选 **sysfs CPU-list**（`online` / `offline` / `present` / `possible`）+ 可选 **sysconf 处理器数**（`configuredProcessorCount` / `onlineProcessorCount`）。实现类：`TraceEnvironmentConfig.LinuxCpuConfig`；亲和性接线在 `AndroidSyscallHandler.sched_getaffinity`；CPU-list 接线在 `LinuxFileSystem.open`（`linux.files` 精确映射之后）；sysconf 接线在 `AndroidSyscallHandler.sysconf` + libc 符号钩子 `SysconfHook`（节点存在时注册）。

**节点缺失 vs 显式空对象：**

| 状态 | `isLinuxCpuConfigured()` | 行为摘要 |
| --- | --- | --- |
| **节点缺失** | `false` | **完全保留旧逻辑**：`sched_setaffinity` 写入的 `sched_cpu_mask` 状态仍可由 `sched_getaffinity` 回放；无 CPU-list 自动文件；**无** sysconf 钩子 |
| **显式 `{}`** | `true` | 默认 `affinityMaskHex="01"`（单字节 `0x01`）；配置亲和性路径**优先于**旧状态掩码；无 CPU-list 键 → 对应路径保持旧 open；处理器数字段未配 → 对应 sysconf **不拦截** |
| **有字段** | `true` | 使用已配字段；CPU-list、亲和性与处理器数**互不推导** |

### 字段说明（仅允许下列键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `affinityMaskHex` | 否 | 非空 String，**偶数位**纯十六进制（大小写均可）；解码后长度 **1..1024** 字节 | `"01"` |
| `online` | 否 | **规范** Linux CPU-list String（见下）；独立 presence | 键缺失：`/sys/devices/system/cpu/online` 不走本路径 |
| `offline` | 否 | 同上 | 键缺失：不服务 offline |
| `present` | 否 | 同上 | 键缺失：不服务 present |
| `possible` | 否 | 同上 | 键缺失：不服务 possible |
| `configuredProcessorCount` | 否 | 精确 JSON 整数 **`1..4096`**；独立 presence | 键缺失：`sysconf(_SC_NPROCESSORS_CONF)` 不拦截 |
| `onlineProcessorCount` | 否 | 精确 JSON 整数 **`1..4096`**；独立 presence | 键缺失：`sysconf(_SC_NPROCESSORS_ONLN)` 不拦截 |

**CPU-list 语法（须已为规范形式，parse 不自动合并）：** 非空；逗号分隔十进制 ID 或升序闭区间 `N-M`（`N < M`）；**无**空白；**无**前导零（字面量 `0` 除外）；**无**重复/重叠；**无**未合并的相邻段（如 `0-2,3` 或 `0,1` 非法，须写成 `0-3` / `0-1`）；ID 范围 `0..Integer.MAX_VALUE`。校验失败路径 `linux.cpu.<field>`。入库保留该规范字符串。

未知键 / null / Boolean / Number → 失败。`affinityMaskHex` 奇数长度/非法字符/超长 → `linux.cpu.affinityMaskHex`。配置内保存解码后的私有 `byte[]` 与规范 list 字符串；`getAffinityMaskBytes()` **每次防御性复制**；不暴露 JSONObject。

### 对 `sched_getaffinity` 的影响（配置节点存在时）

| 条件 | 返回 | 是否写 mask |
| --- | --- | --- |
| `cpusetsize <= 0` 或 `> 1024` | `-EINVAL` | 否 |
| `cpusetsize` **短于**配置掩码字节长度 | `-EINVAL` | **否**（不写目标） |
| `mask` 指针为 null | `-EFAULT` | 否 |
| 合法 | `cpusetsize` | **先将缓冲区 `cpusetsize` 字节全部置 0**，再把配置掩码复制到开头 |

- **配置优先**：节点存在时不使用旧 `sched_cpu_mask`。
- **`sched_setaffinity` 保持未配置化**（仍可写内部状态，但配置路径 get 时不读它）。

### 对 `/sys/devices/system/cpu/{online,offline,present,possible}` 的影响

| 精确路径 | 前置条件 | 内容 | 未配置 |
| --- | --- | --- | --- |
| `/sys/devices/system/cpu/online` | `online` 键显式存在 | UTF-8 `<list>\n` 只读 | 旧 open |
| `/sys/devices/system/cpu/offline` | `offline` 键显式存在 | 同上 | 旧 open |
| `/sys/devices/system/cpu/present` | `present` 键显式存在 | 同上 | 旧 open |
| `/sys/devices/system/cpu/possible` | `possible` 键显式存在 | 同上 | 旧 open |

`linux.files` **精确**映射始终优先。字段彼此与 `affinityMaskHex` **独立**，**不**从 CPU 数/宿主状态推导。

### 对 `sysconf(_SC_NPROCESSORS_CONF|_ONLN)` 的影响

| 条件 | 行为 |
| --- | --- |
| `configuredProcessorCount` 键显式存在 | libc `sysconf` 名 = Bionic `_SC_NPROCESSORS_CONF`（**0x60 / 96**，ARM32/ARM64 相同）→ 返回配置值 |
| `onlineProcessorCount` 键显式存在 | 名 = Bionic `_SC_NPROCESSORS_ONLN`（**0x61 / 97**）→ 返回配置值 |
| 对应键缺失 / 节点缺失 / **其它** sysconf name | **不拦截**，保留既有 libc 行为；**不**读宿主 `availableProcessors`；**不发** sidecar |

**不**从 `online`/`present`/`possible`/`affinityMaskHex` 推导计数。**不**实现通用 sysconf 表。

### Sidecar

| 命中 | `kind` | `api` | `value` | `source` |
| --- | --- | --- | --- | --- |
| `sched_getaffinity` 配置路径 | `linux_cpu` | `sched_getaffinity` | 成功：`pid=…,cpusetsize=…,maskHex=…`；错误：`errno=…`；maskHex 最多摘要前 64 字节 | `json-config` |
| 自动 CPU-list 读成功 | `linux_cpu` | `read("<path>")` | `path=…,format=online\|offline\|present\|possible,bytes=…`（**不**写 cpulist 原文） | `json-config` |
| 配置命中的 `sysconf` | `linux_cpu` | `sysconf` | `field=configuredProcessorCount\|onlineProcessorCount,result=<n>`（**不**写无关系统信息） | `json-config` |

### 明确未实现

- `sched_setaffinity` 的 JSON 配置化
- 通用 `sysconf`（除上述两 name）、从 CPU-list 推导计数、拓扑、频率、大小核类型
- hotplug 写、其它 sysfs 节点

## linux.rlimits

可选 **JSONObject**，配置 Linux 资源限制的只读 syscall 子集，以及在 `linux.proc.limits` 缺键时对 `/proc/self|pid/limits` 的单向派生。实现类：`TraceEnvironmentConfig.LinuxRlimitsConfig`；syscall 接线在 ARM64 `getrlimit64`（NR **163**）；proc 文件接线在 `LinuxFileSystem.openConfiguredProcFile` / `ConfiguredProcFiles.renderLimits`。当前**仅**允许键 `nofile`（Linux `RLIMIT_NOFILE`，resource **7**）。

**节点 / 字段缺失 vs 显式配置：**

| 状态 | `isLinuxRlimitsConfigured()` | `isLinuxRlimitsNofileConfigured()` | 行为摘要 |
| --- | --- | --- | --- |
| **`linux.rlimits` 节点缺失** | `false` | `false` | **不**接管 `RLIMIT_NOFILE`；**不**派生 `/proc/self\|pid/limits`；其它未实现 resource 仍 `UnsupportedOperationException` |
| **显式 `{}`** | `true` | `false` | 节点存在但 **`nofile` 未配置**；**不**发明默认 soft/hard；`RLIMIT_NOFILE` 仍 UOE；**不**派生 limits 行 |
| **`nofile` 显式对象** | `true` | `true` | ARM64 `getrlimit64(7)` 向用户 `rlimit64` 写入配置的 soft/hard 并返回 **0**；若 `linux.proc.limits` **缺键**则派生 `/proc/self\|pid/limits` 一行 |

### 字段说明（仅允许 `nofile`）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `nofile` | 否 | **JSONObject**，**仅**允许 `soft` / `hard`；二者均为精确 JSON 非负 long（`0..Long.MAX_VALUE`），且 **`soft <= hard`**；两键均必填 | 键缺失：ARM64 `getrlimit64(RLIMIT_NOFILE)` **不拦截**，保持 UOE；**不**派生 `/proc/self\|pid/limits` |

未知键（`linux.rlimits` 或 `nofile` 内）、错误类型、JSON null、小数、负数、`soft > hard`、缺少 `soft`/`hard` → 加载时 `IllegalArgumentException`。**无**默认值；**不**从 `linux.proc.limits` 文本、宿主 `getrlimit` 或其它 resource 推导。不暴露 JSONObject。

**示例（`example/trace-env.example.json`）：** 同时配置了 `linux.proc.limits`（完整表，含 `Max open files 32768/32768`）与 `linux.rlimits.nofile`（`soft/hard` 同为 32768）。读取 `/proc/self|pid/limits` 时 **`linux.proc.limits` 优先**，不走 nofile 派生；`getrlimit64(RLIMIT_NOFILE)` 仍读 `nofile`。若删除 `limits` 键、仅保留 `nofile`，则 `/proc/self|pid/limits` 变为恰好一行 `Max open files`（列对齐与示例该行相同）。`linux.files."/proc/self/limits"` 若存在则二者皆覆盖。

### 对 `/proc/self|pid/limits` 的影响（单向派生）

仅当 **`linux.proc.limits` 没有显式配置**（键缺失，不是 `[]`）且 **`linux.rlimits.nofile` 显式配置**时，`LinuxFileSystem` 进入既有 limits 分支，服务 `/proc/self/limits` 与 `/proc/<emulatorPid>/limits`：

- 内容恰好一行、UTF-8、末尾 LF；名称 `Max open files`，soft/hard 为配置十进制，units `files`
- 固定空白：`%-25s %-20s %-20s %-10s` 后接 LF（与 Android/Linux 常见表头列对齐）
- **不得**输出表头、其它限制行、地址、宿主值或 unlimited
- `linux.proc` 节可完全缺失；`linux.proc.limits` 含 `[]` 时覆盖为**空文件**
- sidecar 与显式 limits 相同：kind=`linux_proc`，api=`read(path)`，source=`json-config`，`format=limits,bytes=…`；摘要**不含**行原文、数值地址或宿主信息

**不**从 `linux.proc.limits` 文本反解析 `nofile`。`getrlimit64` 行为不变。

### 对 ARM64 `getrlimit64` 的影响

| resource | 条件 | 行为 |
| --- | --- | --- |
| **3** `RLIMIT_STACK` | 始终 | **原样**写入模拟栈大小 `STACK_SIZE_OF_PAGE * pageAlign` 到 `rlim_cur`/`rlim_max`，返回 0；**不**读 JSON；**不**发 sidecar |
| **7** `RLIMIT_NOFILE` | **仅当** `linux.rlimits.nofile` 显式配置 | 写入配置 `soft`→`rlim_cur`、`hard`→`rlim_max`，返回 **0**；成功 sidecar（见下） |
| **7** `RLIMIT_NOFILE` | 未配置 | **保持** `UnsupportedOperationException`（与其它未实现 resource 相同） |
| 其它 resource | — | **保持** `UnsupportedOperationException` |

**不**实现 `setrlimit`、`prlimit64`、ARM32 `getrlimit`、其它 rlimit 资源或运行时强制（打开文件数不会按此截断）。

### Sidecar

仅当已注册 trace 环境 sidecar **且**显式 JSON 配置命中的 `RLIMIT_NOFILE` **成功**返回时 emit **一次**。无 sidecar 时 **无副作用**。

| 字段 | 值 |
| --- | --- |
| `kind` | `linux_proc` |
| `api` | `getrlimit64` |
| `source` | `json-config` |
| `value` | **仅** `resource=<n>,soft=<n>,hard=<n>`（**不**含指针/地址） |
| `note` | `读取配置的 getrlimit64 RLIMIT_NOFILE` |

`RLIMIT_STACK`、未配置 `RLIMIT_NOFILE`、其它 resource、失败路径 **不**发该事件。

### 明确未实现

- `setrlimit` / `prlimit64` / ARM32 `getrlimit`
- 其它 resource（`RLIMIT_CPU`、`RLIMIT_AS` 等；**不含**用 JSON 覆盖 `RLIMIT_STACK`）
- `RLIM_INFINITY`（无符号 `-1` / `2^64-1` 不是非负 signed long）
- 按此限制强制 fd 配额
- 从 `linux.proc.limits` 文本反解析 `nofile`（单向：仅 `nofile` → `/proc/self|pid/limits` 一行，且仅当 `linux.proc.limits` 缺键；显式行与 `[]` 均覆盖派生）

## linux.sysinfo

可选 **JSONObject**，为 ARM32 `sysinfo`（NR **116**）与 ARM64 `sysinfo`（NR **179**）提供固定内核 `struct sysinfo`。实现类：`TraceEnvironmentConfig.LinuxSysinfoConfig`；接线在 `AndroidSyscallHandler.sysinfo`，复用 `SysInfo32` 并补 `SysInfo64`（112 字节）。

**节点缺失 vs 显式配置：**

| 状态 | `isLinuxSysinfoConfigured()` | 行为摘要 |
| --- | --- | --- |
| **`linux.sysinfo` 节点缺失** | `false` | **保持旧零值**：写入全 0 的 `struct sysinfo` 并返回 `0`；**不发** sidecar；**不**读宿主 |
| **节点存在** | `true` | 按配置写入 uptime / loads / RAM / swap / procs / `mem_unit` 并返回 `0`；`totalhigh`/`freehigh`/`pad` 固定 0 |

节点存在时**全部白名单字段必填**。未知键、错误类型、JSON null、小数、负数、`loads` 不是恰好 3 个精确整数、`procs` 越界、`memUnit` &lt; 1、或任何字段超出下表 ARM32 C 范围 → 加载时 `IllegalArgumentException`，路径含 `linux.sysinfo` 或 `linux.sysinfo.<field>`。

公共 JSON **按 ARM32 C `struct sysinfo` 字段宽度**校验（同一份配置 32/64 可用）。**绝不**把更大的 Java `long` 静默截断成 32 位。ARM32 运行时若仍越界（防御）返回 `-1` 并设 `EINVAL`，**不**改用户 buffer、**不发** sidecar。

### 字段说明（仅允许下列 10 键）

| 字段 | 必填 | 类型与校验（ARM32 C 宽度） | 写入内核字段 |
| --- | --- | --- | --- |
| `uptime` | 是 | 精确 JSON 整数 `0..2147483647`（`__kernel_long_t` 有符号 32 位） | `uptime`（秒） |
| `loads` | 是 | JSON 数组，恰好 3 个精确整数 `0..4294967295`（`__kernel_ulong_t`） | `loads[3]`（`SI_LOAD_SHIFT=16` 定点） |
| `totalRam` | 是 | 精确 JSON 整数 `0..4294967295` | `totalram` |
| `freeRam` | 是 | 同上 | `freeram` |
| `sharedRam` | 是 | 同上 | `sharedram` |
| `bufferRam` | 是 | 同上 | `bufferram` |
| `totalSwap` | 是 | 同上 | `totalswap` |
| `freeSwap` | 是 | 同上 | `freeswap` |
| `procs` | 是 | 精确 JSON 整数 `0..65535` | `procs`（`__u16`） |
| `memUnit` | 是 | 精确 JSON 整数 `1..4294967295`（`__u32`） | `mem_unit`（字节） |

`4294967295` 写入 32 位字段时为无符号位型（Java `int` 为 `-1`），**不是**从更大整数截断。样例使用 `memUnit=4096` 的页计数。**不**从 `/proc/meminfo`、`android.runtime` 内存字段或宿主 `sysinfo` 推导。**不**校验 `freeRam <= totalRam`。

### Sidecar

仅配置命中且成功返回时发一次：

| 字段 | 值 |
| --- | --- |
| `kind` | `linux_sys` |
| `api` | `sysinfo` |
| `source` | `json-config` |
| `value` | `uptime=…,loads=a/b/c,totalRam=…,freeRam=…,sharedRam=…,bufferRam=…,totalSwap=…,freeSwap=…,procs=…,memUnit=…`（**无**指针/地址） |
| `note` | `读取配置的 sysinfo` |

未配置、`info==NULL`（`-1`/`EFAULT`）、以及 ARM32 字段越界（`-1`/`EINVAL`）**不发**该事件。

### 明确未实现

- `totalhigh` / `freehigh` 配置（恒为 0）
- 由本节点生成 `/proc/meminfo` 或与 `android.runtime` 互推
- 实时递增 uptime / loads

## android

应用级 Android 信息。

| 字段 | 影响位置 |
| --- | --- |
| `android.packageName` | 覆盖 `BaseVM.getPackageName()`，影响 `Context.getPackageName()`、`ActivityThread.currentPackageName()`、`PackageManager` 查询匹配、APK library file 的 packageName。 |
| `android.versionName` | 覆盖 `BaseVM.getVersionName()`，影响 `PackageInfo.versionName`。 |
| `android.versionCode` | 覆盖 `BaseVM.getVersionCode()`，影响 `PackageInfo.versionCode`。 |
| `android.apkPath` | 覆盖 `ApplicationInfo.sourceDir` 和 `ApplicationInfo.publicSourceDir`。 |
| `android.dataDir` | 覆盖 `ApplicationInfo.dataDir`（`getDataDir(fallback)` 路径）。**另：** 仅当键**显式存在且非 null String**（`isAndroidDataDirConfigured()`）时，接线实例 `Context` / `ContextWrapper` / `Application` 的 **`getDataDir()Ljava/io/File;`** → `new File(dataDir)`；固定子目录 `getFilesDir`/`getCacheDir`/`getNoBackupFilesDir`/`getCodeCacheDir` → `new File(dataDir,"files"|"cache"|"no_backup"|"code_cache")`；以及 **`getDir(Ljava/lang/String;I)Ljava/io/File;`**（name 非空单段、无 `/`\\NUL/CR/LF → `new File(dataDir,"app_"+name)`；**mode 忽略**）（VarArg + VaList；**不**创建宿主目录/FileIO；**不**加载 Dex/代码；**无**权限/mode 语义；**不含** database）。键缺失、JSON null、非法 name → UOE 无事件。sidecar kind=`android_data_dir`；getDataDir/固定子目录 value=`result=…`；getDir value=`name=<n>,result=<dataDir>/app_<n>`。与 `android.packages[].dataDir`、`filesystem.systemDirectories.dataDirectory` **独立**。 |
| `android.nativeLibraryDir32` | 32 位 emulator 下覆盖 `ApplicationInfo.nativeLibraryDir`。 |
| `android.nativeLibraryDir64` | 64 位 emulator 下覆盖 `ApplicationInfo.nativeLibraryDir`。 |

**示例（`example/trace-env.example.json`）：** `android.dataDir=/data/user/0/com.demo.app` 时，`getDataDir()` → `…/com.demo.app`，`getFilesDir()` → `…/files`，`getCacheDir()` → `…/cache`，`getNoBackupFilesDir()` → `…/no_backup`，`getCodeCacheDir()` → `…/code_cache`，`getDir("plugins",0)` → `…/app_plugins`（仅路径对象，**不**建目录、**不**加载代码、**忽略** mode）。

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
| `android.build.MANUFACTURER` | `Build.MANUFACTURER`。仅在 **未配置** `android.drm` 的遗留路径上，作为 `AMediaDrm_getPropertyString("vendor")` 的优先值（缺失则 `Google`）；**已配置** `android.drm` 时以 `android.drm.vendor` 为准。 |
| `android.build.MODEL` | `Build.MODEL`。 |
| `android.build.PRODUCT` | `Build.PRODUCT`。 |
| `android.build.RADIO` | `Build.RADIO` 静态字段；亦用于无参静态方法 `Build.getRadioVersion()`（VarArg + VaList；仅本键且值为 String，返回配置原文；**不**读 `android.properties` 的任何 `ro.*`，**不**模拟权限/调制解调器；缺失/非 String/错误签名则 UOE 无事件；sidecar kind=`android_build`，api=`Build.getRadioVersion`，value=`radio=<值>`）。 |
| `android.build.SERIAL` | `Build.SERIAL` 静态字段；亦用于无参静态方法 `Build.getSerial()`（VarArg + VaList；仅本键，**不**读 `ro.serialno`，**不**模拟权限/`SecurityException`；缺失则 UOE 无事件；sidecar kind=`android_build`，api=`Build.getSerial`，value=`serial=<值>`）。 |
| `android.build.TAGS` | `Build.TAGS`。 |
| `android.build.TYPE` | `Build.TYPE`。 |
| `android.build.USER` | `Build.USER`。 |
| `android.build.TIME` | `Build.TIME`（**long 静态字段**，见下）。 |
| `android.build.VERSION.CODENAME` | `Build.VERSION.CODENAME`。 |
| `android.build.VERSION.INCREMENTAL` | `Build.VERSION.INCREMENTAL`。 |
| `android.build.VERSION.RELEASE` | `Build.VERSION.RELEASE`。 |
| `android.build.VERSION.SDK_INT` | `Build.VERSION.SDK_INT`，这是 int 字段。 |
| `android.build.VERSION.SECURITY_PATCH` | `Build.VERSION.SECURITY_PATCH`。 |
| `android.build.SUPPORTED_ABIS` | `Build.SUPPORTED_ABIS`（**特殊数组字段**，见下）。 |
| `android.build.SUPPORTED_32_BIT_ABIS` | `Build.SUPPORTED_32_BIT_ABIS`（**特殊数组字段**，与其它 ABI 数组 **独立**，见下）。 |
| `android.build.SUPPORTED_64_BIT_ABIS` | `Build.SUPPORTED_64_BIT_ABIS`（**特殊数组字段**，与其它 ABI 数组 **独立**，见下）。 |

规则：`android/os/Build->FIELD:Ljava/lang/String;` 映射到 `android.build.FIELD`；`android/os/Build$VERSION->FIELD` 映射到 `android.build.VERSION.FIELD`。

### android.build.TIME（long 静态字段）

可选 **`android.build.TIME`**：精确 JSON Number 整数 long，范围 **`0..Long.MAX_VALUE`**（含 0）。实现：`isAndroidBuildTimeConfigured()` / `getAndroidBuildTime()`（presence 感知；键缺失时 false/`null`）。JNI 仅 **`getStaticLongField`** 精确签名 `android/os/Build->TIME:J`。

| 状态 | `isAndroidBuildTimeConfigured()` | `getAndroidBuildTime()` | 行为摘要 |
| --- | --- | --- | --- |
| **键缺失** | `false` | `null` | `Build.TIME:J` UOE，**不发** sidecar |
| **已配置** | `true` | 非 null long | 返回配置时间戳；sidecar kind=`android_build` |

**校验：** 拒绝 null / String / Boolean / 小数 / 负数；错误路径 `android.build.TIME`。

| 精确签名 | 条件 | 已配置 | 未配置 / 错误签名 |
| --- | --- | --- | --- |
| `android/os/Build->TIME:J`（`getStaticLongField`） | 键存在且已校验 | 返回配置 long | 键缺失 / 其它签名（含 `TIME:Ljava/lang/String;`）：UOE，**不发**事件 |

**Sidecar（仅成功 `TIME:J`）：** kind=`android_build`，api=`Build.TIME`，value=`time=<n>`，source=`json-config`，note 中文简述（读取配置的 Build.TIME 构建时间戳）。

**隔离：** `TIME` **不**经通用 `Build` String 字段路径返回（错误 object 签名 UOE，**不**把 long 强制转 String）；**不**经 int 字段路径；**不**与 time 配置节推导。

### android.build 数组字段：`SUPPORTED_ABIS` / `SUPPORTED_32_BIT_ABIS` / `SUPPORTED_64_BIT_ABIS`

在既有 **标量** `android.build` 字段之外的**数组子集**（v1）。路径分别为 `android.build.SUPPORTED_ABIS`、`SUPPORTED_32_BIT_ABIS`、`SUPPORTED_64_BIT_ABIS`（可选 **JSONArray**）。三者**互相独立**，**不**从对方推导。实现：`isAndroidBuildSupportedAbisConfigured()` / `getAndroidBuildSupportedAbis()`、`isAndroidBuildSupported32BitAbisConfigured()` / `getAndroidBuildSupported32BitAbis()`、`isAndroidBuildSupported64BitAbisConfigured()` / `getAndroidBuildSupported64BitAbis()`（不可变 `List<String>`，**不**暴露 JSONArray）；JNI 在 `AbstractJni.getStaticObjectField`。

| 状态 | 对应 `is…Configured()` | 对应 getter | 行为摘要 |
| --- | --- | --- | --- |
| **键缺失** | `false` | `null` | 对应静态字段 UOE，**不发** sidecar |
| **已配置** | `true` | 非 null、长度 ≥ 1 | 静态字段返回**新的** `ArrayObject`/`StringObject[]`，顺序与 JSON 一致 |

**校验（三键相同规则）：** 必须是**非空** JSONArray；元素必须是非 blank 的 String（解析时 **trim** 后存入）；拒绝 null / 非数组 / 空数组 / 非 String / 空白串。错误路径：`android.build.<FIELD>` 或 `android.build.<FIELD>[<index>]`。

| 精确签名 | 配置键 | Sidecar `api` |
| --- | --- | --- |
| `android/os/Build->SUPPORTED_ABIS:[Ljava/lang/String;` | `SUPPORTED_ABIS` | `Build.SUPPORTED_ABIS` |
| `android/os/Build->SUPPORTED_32_BIT_ABIS:[Ljava/lang/String;` | `SUPPORTED_32_BIT_ABIS` | `Build.SUPPORTED_32_BIT_ABIS` |
| `android/os/Build->SUPPORTED_64_BIT_ABIS:[Ljava/lang/String;` | `SUPPORTED_64_BIT_ABIS` | `Build.SUPPORTED_64_BIT_ABIS` |

**Sidecar（仅命中时）：** kind=`android_build`，value=`count=<n>,abis=<逗号分隔 ABI 名>`，source=`json-config`，note 中文简述（读取配置的对应 Build ABI 列表）。

其它标量 `android.build` 字段行为不变。

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

**说明：** `android.properties` 服务 **native** `__system_property_*`。Java 层 `System.getProperty` 见 **`android.runtime.systemProperties`**（彼此独立，**不**互相推导）。

## android.runtime

Java 运行时只读子集（v1）。路径为 `android.runtime`（可选 **JSONObject**，允许键：`systemProperties`、`environmentVariables`、**`availableProcessors`**、**`maxMemoryBytes`**、**`totalMemoryBytes`**、**`freeMemoryBytes`**）。字段**彼此独立**（仅当 `maxMemoryBytes` 与 `totalMemoryBytes` **同时显式**时拒绝 `totalMemoryBytes > maxMemoryBytes`；**`freeMemoryBytes` 不能单独出现**，解析期要求 `totalMemoryBytes` 已显式，并拒绝 `free > total`，因此可形成 `0<=free<=total<=max`），也**不**与 `linux.environ` / **`linux.cpu`** / 宿主 JVM 推导。

### android.runtime.systemProperties

可选 **`systemProperties`**：**JSONObject**。键为**非空** String，**无** NUL/CR/LF；值为 **String**（允许空串）或 **JSON null**。显式 `{}` 为已配置空映射。**不**回落宿主 JVM `System.getProperties()`。

| 状态 | `isAndroidRuntimeSystemPropertiesConfigured()` | 行为摘要 |
| --- | --- | --- |
| **`android.runtime` / `systemProperties` 缺失** | `false` | `System.getProperty` / **`getProperties()`** UOE，**不发** sidecar |
| **显式 `systemProperties: {}`** | `true` | `getProperty` 任意键 UOE；**`getProperties()`** → **空 map** + sidecar `count=0` |
| **有键** | `true` | `getProperty` 仅**显式配置的键**；**`getProperties()`** → 非 null 条目快照（JSON 顺序） |

### android.runtime.environmentVariables

可选 **`environmentVariables`**：**JSONObject**。键为**非空** String，**无** NUL/CR/LF；值为 **String**（允许空串）或 **JSON null**。显式 `{}` 为已配置空映射。**不**回落宿主环境；**不**读 `linux.environ`。

| 状态 | `isAndroidRuntimeEnvironmentVariablesConfigured()` | 行为摘要 |
| --- | --- | --- |
| **键缺失** | `false` | `System.getenv(String)` / **`getenv() Map`** UOE，**不发** sidecar |
| **显式 `{}`** | `true` | `getenv(String)` 任意键 UOE；**`getenv() Map`** → **空 map** + sidecar `count=0` |
| **有键** | `true` | `getenv(String)` 仅**显式配置的键**；**`getenv() Map`** → 非 null 条目快照（JSON 顺序） |

### android.runtime.availableProcessors

可选 **`availableProcessors`**：精确 JSON 整数 **`1..4096`**。键显式存在时启用 Java `Runtime.availableProcessors`。`Runtime.getRuntime` 在本字段、`maxMemoryBytes` 或 `totalMemoryBytes` 任一显式配置时返回同 VM marker。**不**从 `linux.cpu.configuredProcessorCount` / `onlineProcessorCount` / 亲和性 / CPU-list 推导；**不**回落宿主 `Runtime.availableProcessors()`。

| 状态 | `isAndroidRuntimeAvailableProcessorsConfigured()` | 行为摘要 |
| --- | --- | --- |
| **键缺失** | `false` | `availableProcessors` UOE；`getRuntime` 仅当 `maxMemoryBytes` 与 `totalMemoryBytes` 也缺失时 UOE；**不发** sidecar |
| **键存在** | `true` | `getRuntime` → **VM 持有** marker（无宿主 Runtime）；`availableProcessors` 仅**同 VM 存活 marker** 且本字段已配置时返回配置值 + 摘要 sidecar |

### android.runtime.maxMemoryBytes

可选 **`maxMemoryBytes`**：精确 JSON 整数 **`1..Long.MAX_VALUE`**。键显式存在时启用 Java `Runtime.maxMemory()J`。`Runtime.getRuntime` 在本字段、`availableProcessors` 或 `totalMemoryBytes` 任一显式配置时返回**同一** VM `ConfiguredRuntime` marker。**不**从 `linux.cpu` / 宿主 `Runtime.maxMemory()` / `totalMemory` / `freeMemory` 推导；**不**实现真实堆限制。

### android.runtime.totalMemoryBytes

可选 **`totalMemoryBytes`**：精确 JSON 整数 **`1..Long.MAX_VALUE`**。键显式存在时启用 Java `Runtime.totalMemory()J`。`Runtime.getRuntime` 在本字段、`availableProcessors` 或 `maxMemoryBytes` 任一显式配置时返回**同一** VM `ConfiguredRuntime` marker。与 maps / `availableProcessors` / `linux.cpu` / 宿主 JVM **独立**；**不**回落宿主 `Runtime.totalMemory()`；**不**实现 GC / 真实堆。当 `maxMemoryBytes` 与本字段**同时显式**时，解析拒绝 `totalMemoryBytes > maxMemoryBytes`（错误带 `android.runtime.totalMemoryBytes`）。

### android.runtime.freeMemoryBytes

可选 **`freeMemoryBytes`**：精确 JSON 整数 **`0..Long.MAX_VALUE`**。键显式存在时启用 Java `Runtime.freeMemory()J`。**不能单独出现**：解析期要求 `android.runtime.totalMemoryBytes` 已显式配置，并拒绝 `freeMemoryBytes > totalMemoryBytes`（错误带 `android.runtime.freeMemoryBytes`）。若 `maxMemoryBytes` 同时存在，已有 `total<=max` 检查继续生效，因此 `0<=free<=total<=max`。`Runtime.getRuntime` **不**因本字段额外门控（free 必然要求 total）。与 maps / `availableProcessors` / `linux.cpu` / 宿主 JVM **独立**；**不**回落宿主 `Runtime.freeMemory()`；**不**实现 GC / 真实堆 / 内存分配。

示例：

```json
{
  "android": {
    "runtime": {
      "availableProcessors": 8,
      "maxMemoryBytes": 268435456,
      "totalMemoryBytes": 134217728,
      "freeMemoryBytes": 67108864
    }
  }
}
```

| 状态 | `isAndroidRuntimeMaxMemoryBytesConfigured()` | 行为摘要 |
| --- | --- | --- |
| **键缺失** | `false` | `maxMemory` UOE；`getRuntime` 仅当 `availableProcessors` 与 `totalMemoryBytes` 也缺失时 UOE；**不发** sidecar |
| **键存在** | `true` | `getRuntime` → **VM 持有** marker（无宿主 Runtime）；`maxMemory` 仅**同 VM 存活 marker** 且本字段已配置时返回配置精确 long + 摘要 sidecar |

| 状态 | `isAndroidRuntimeTotalMemoryBytesConfigured()` | 行为摘要 |
| --- | --- | --- |
| **键缺失** | `false` | `totalMemory` UOE；`getRuntime` 仅当 `availableProcessors` 与 `maxMemoryBytes` 也缺失时 UOE；**不发** sidecar |
| **键存在** | `true` | `getRuntime` → **VM 持有** marker（无宿主 Runtime）；`totalMemory` 仅**同 VM 存活 marker** 且本字段已配置时返回配置精确 long + 摘要 sidecar |

| 状态 | `isAndroidRuntimeFreeMemoryBytesConfigured()` | 行为摘要 |
| --- | --- | --- |
| **键缺失** | `false` | `freeMemory` UOE；**不发** sidecar；`getRuntime` 门控不变 |
| **键存在**（且 total 已显式） | `true` | `freeMemory` 仅**同 VM 存活 marker** 且本字段已配置时返回配置精确 long + 摘要 sidecar |

**校验失败路径：** `android.runtime`、`android.runtime.<未知键>`、`android.runtime.systemProperties` / `environmentVariables` / **`availableProcessors`** / **`maxMemoryBytes`** / **`totalMemoryBytes`** / **`freeMemoryBytes`**、以及各映射下 `.<key>`。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 条件 | 已配置键 | 未配置 / 非法 |
| --- | --- | --- | --- |
| `java/lang/System->getProperty(Ljava/lang/String;)Ljava/lang/String;`（VarArg + VaList） | `systemProperties` 已配置；arg0 `StringObject` 且键**显式存在** | 配置非 null → **新** `StringObject`；配置 null → Java `null` | 节点/键缺失 / 非 String 参数 / 错误签名：UOE 无事件（**无**宿主回落） |
| `java/lang/System->getProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;`（VarArg + VaList） | 同上；arg1 `StringObject` 或 null 引用 | 配置非 null → 配置串；配置 null → **返回默认串**（arg1） | 同上；arg1 非 String 且非 null：UOE |
| **`java/lang/System->getProperties()Ljava/util/Properties;`**（VarArg + VaList） | **`systemProperties` 已配置**（含 `{}`） | **新** Properties 类快照：JSON **顺序**；**仅**非 null String 值；JSON null 键**省略**；`{}` → 空 map | 节点缺失：UOE 无事件；**不**持久化 put 变异；**无** setProperty/宿主回落 |
| **`java/lang/System->getenv(Ljava/lang/String;)Ljava/lang/String;`**（VarArg + VaList） | **`environmentVariables` 已配置**；arg0 `StringObject` 且键**显式存在** | 配置非 null → **新** `StringObject`；配置 null → Java `null` | 节点/键缺失 / 非 String / 错误签名：UOE 无事件（**无**宿主回落） |
| **`java/lang/System->getenv()Ljava/util/Map;`**（VarArg + VaList） | **`environmentVariables` 已配置**（含 `{}`） | **新** `HashMap` 快照：JSON **顺序**；**仅**非 null String 值；JSON null 键**省略**；`{}` → 空 map | 节点缺失：UOE 无事件；**不**持久化 put 变异；**无** setenv/宿主回落 |
| **`java/lang/Runtime->getRuntime()Ljava/lang/Runtime;`**（VarArg + VaList） | **`availableProcessors` / `maxMemoryBytes` / `totalMemoryBytes` 任一已配置** | **新** VM marker（绑定 owner VM + 配置实例；**无**宿主 Runtime） | 三字段都缺失 / 错误签名：UOE 无事件；**不发** sidecar |
| **`java/lang/Runtime->availableProcessors()I`**（VarArg + VaList） | **`availableProcessors` 已配置**；receiver 为**同 VM 存活** marker | 配置精确 int | 键缺失 / 跨 VM / 陈旧/非 marker / 错误签名：UOE 无事件；**无**宿主回落、**不**读 `linux.cpu` |
| **`java/lang/Runtime->maxMemory()J`**（VarArg + VaList） | **`maxMemoryBytes` 已配置**；receiver 为**同 VM 存活** marker | 配置精确 long | 键缺失 / 跨 VM / 陈旧/非 marker / 错误签名 / 其它 Runtime long API：UOE 无事件；**无**宿主回落、**不**实现 gc/真实堆 |
| **`java/lang/Runtime->totalMemory()J`**（VarArg + VaList） | **`totalMemoryBytes` 已配置**；receiver 为**同 VM 存活** marker | 配置精确 long | 键缺失 / 跨 VM / 陈旧/非 marker / 错误签名 / 其它 Runtime long API：UOE 无事件；**无**宿主回落、**不**实现 gc/真实堆 |
| **`java/lang/Runtime->freeMemory()J`**（VarArg + VaList） | **`freeMemoryBytes` 已配置**；receiver 为**同 VM 存活** marker | 配置精确 long | 键缺失 / 跨 VM / 陈旧/非 marker / 错误签名 / 其它 Runtime long API：UOE 无事件；**无**宿主回落、**不**实现 gc/真实堆/内存分配 |

### Sidecar

| 字段 | `getProperty` | **`getProperties()`** | **`getenv(String)`** | **`getenv() Map`** | **`availableProcessors()`** | **`maxMemory()`** | **`totalMemory()`** | **`freeMemory()`** |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `kind` | `android_runtime` | `android_runtime` | `android_runtime` | `android_runtime` | `android_runtime` | `android_runtime` | `android_runtime` | `android_runtime` |
| `api` | `System.getProperty` | **`System.getProperties`** | **`System.getenv`** | **`System.getenv`** | **`Runtime.availableProcessors`** | **`Runtime.maxMemory`** | **`Runtime.totalMemory`** | **`Runtime.freeMemory`** |
| `value` | `key=<propKey>,result=<value\|null>`（不泄露其它键） | **`count=<n>`**（仅条数；**不**写键/值） | **`key=<envKey>,result=<value\|null>`**（不泄露其它键） | **`count=<n>`**（仅条数；**不**写键/值） | **`field=availableProcessors,result=<n>`**（摘要；**不**写宿主 Runtime 细节） | **`field=maxMemoryBytes,result=<n>`**（摘要；**不**写宿主内存） | **`field=totalMemoryBytes,result=<n>`**（摘要；**不**写宿主内存） | **`field=freeMemoryBytes,result=<n>`**（摘要；**不**写宿主内存） |
| `source` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` |
| `note` | 读取配置的 System 属性 + key | 读取配置的 System 属性映射 | 读取配置的环境变量 + key | 读取配置的环境变量映射 | 读取配置的 availableProcessors | 读取配置的 maxMemoryBytes | 读取配置的 totalMemoryBytes | 读取配置的 freeMemoryBytes |

**说明：** `getProperty` / `getenv(String)` 仅成功处理**已配置键**时发事件；`getProperties()` / `getenv() Map` 在节点已配置且成功时发事件；`getRuntime` **不**发 sidecar；`availableProcessors` / `maxMemory` / `totalMemory` / `freeMemory` 仅存活 marker 且对应字段已配置时发摘要事件。

### 明确未实现

- `System.setProperty` / `clearProperty` / setenv / `exit`
- **其它** `Runtime` API（`gc`/`exit`/`exec` 等）、类加载器、ART 堆/调试器、宿主 JVM properties/env 合并
- 与 `android.properties`（native）、**`linux.environ`** 或 **`linux.cpu`** 交叉推导
- Map 快照 **不**持久化 guest `put`/`remove`；**无** setProperty/setenv

## android.telephony

电话与 SIM（用户识别卡）画像配置。节点路径为 `android.telephony`。存在时必须为 JSONObject（JSON 对象），且 **必须** 包含 `phoneCount` 与 `slots`；其余字段均为可选。非法键、类型或范围会在 `TraceEnvironmentConfig` parse（解析）时失败，异常消息带 `android.telephony...` 路径。另支持 **仅** `TelephonyManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService("phone")`（**不**要求 `android.telephony` 节点即可拿到服务标记；lookup **不发** sidecar）。真正的电话读取仍由 `android.telephony` 门控。

**缺失行为：** 整个 `android.telephony` 节点缺失，或某字段/某卡槽未配置时，对应 JNI（Java 原生接口）保持 notHandled，最终仍走原有 `UnsupportedOperationException` 路径（不伪造默认运营商或 IMEI）。类型化 `getSystemService(TelephonyManager.class)` 仍可返回服务标记（lookup **不发** sidecar）。

**显式 null：** 仅 **字符串类字段**（卡槽标识与全局运营商字符串）允许 JSON `null`：配置存在且值为 null 时 JNI 返回 Java `null`。标量字段 `dataNetworkType` / `dataState` / `dataActivity` / `phoneType` / `networkRoaming` / `simState` **不允许** null。

### 字段说明

| 字段路径 | 必填 | 类型与校验 | 影响的 Java 方法（`TelephonyManager`） |
| --- | --- | --- | --- |
| `android.telephony` | — | JSONObject；仅允许白名单顶层键 | 见下表各 API |
| `android.telephony.phoneCount` | 是 | 精确整数 `1..8` | `getPhoneCount()` |
| `android.telephony.slots` | 是 | JSONArray；元素为 JSONObject | 卡槽相关 API |
| `android.telephony.slots[i].slotIndex` | 每项必填 | 精确整数 `0..phoneCount-1`，全局唯一 | 与 `(int slotIndex)` 重载及无参默认 slot 0 对应 |
| `android.telephony.slots[i].imei` | 否 | 非空无首尾空白 String，或 JSON null；允许 TRACEAI marker | `getImei()`（slot 0）、`getImei(int)` |
| `android.telephony.slots[i].meid` | 否 | 同上 | `getMeid()`、`getMeid(int)` |
| `android.telephony.slots[i].deviceId` | 否 | 同上 | `getDeviceId()`、`getDeviceId(int)` |
| `android.telephony.slots[i].subscriberId` | 否 | 同上 | `getSubscriberId()`（slot 0）、`getSubscriberId(int)` |
| `android.telephony.slots[i].simSerialNumber` | 否 | 同上 | `getSimSerialNumber()`（slot 0）、`getSimSerialNumber(int)` |
| `android.telephony.slots[i].simState` | 否 | 精确整数 `0..11`（不可 null） | `getSimState()`（slot 0）、`getSimState(int)` |
| `android.telephony.networkOperator` | 否 | 非空无首尾空白 String，或 JSON null | `getNetworkOperator()` |
| `android.telephony.networkOperatorName` | 否 | 同上 | `getNetworkOperatorName()` |
| `android.telephony.simOperator` | 否 | 同上 | `getSimOperator()` |
| `android.telephony.simOperatorName` | 否 | 同上 | `getSimOperatorName()` |
| `android.telephony.networkCountryIso` | 否 | **仅** JSON String：空串 `""`，或恰好两个 ASCII 字母；非空读出时规范化为小写；**拒绝** null / 非 String / 其它长度 / 非字母；**不**从 `networkOperator` 推导、无默认 | `getNetworkCountryIso()` |
| `android.telephony.simCountryIso` | 否 | 同上规则；**不**从 `simOperator` 推导、无默认 | `getSimCountryIso()` |
| `android.telephony.dataNetworkType` | 否 | 精确整数 `0..20`（不可 null） | `getDataNetworkType()` 与 **`getNetworkType()`**（同值；后者无独立配置键） |
| `android.telephony.dataState` | 否 | 精确整数 `-1..5`（不可 null）；**不**从 `dataNetworkType` / roaming / wifi / links 推导、无默认 | `getDataState()` |
| `android.telephony.dataActivity` | 否 | 精确整数 **仅** `0..4`（`DATA_ACTIVITY_NONE`/`IN`/`OUT`/`INOUT`/`DORMANT`）；**不**从 `dataState` / `dataNetworkType` / wifi / links 推导、无默认 | `getDataActivity()` |
| `android.telephony.phoneType` | 否 | 精确整数 `0..3`（不可 null） | `getPhoneType()` |
| `android.telephony.networkRoaming` | 否 | Boolean `true`/`false`（不可 null） | `isNetworkRoaming()` |
| `android.telephony.cellInfo` | 否 | JSONArray。键缺失不接管；显式 `[]` 为权威空快照。每项必填 `type`=`gsm`/`cdma`/`lte`/`wcdma`/`nr`；可选 `registered`（Boolean）、`mcc`/`mnc`/`alphaLong`/`alphaShort`（String 或 null）、`ci`/`pci`/`tac`/`earfcn`（精确非负整数）。字段独立 presence，**不**从运营商/品牌推导 | `getAllCellInfo()` / `getCellLocation()` |

### JNI 支持的 API

拦截入口在 `AbstractJni`（32/64 位）：

| Java 方法 | 精确签名 | 接入 | 配置来源 |
| --- | --- | --- | --- |
| `Application`/`Context.getSystemService(Class)` | `(Ljava/lang/Class;)Ljava/lang/Object;` | `callObjectMethod` / `callObjectMethodV` | 第 0 参为 **`DvmClass`** 且类名为 `android/telephony/TelephonyManager` 时，返回与 `"phone"` 字符串路径**同一** `SystemService("phone")`；null / 非 DvmClass / 其它 Class → UOE 无 sidecar；**不**要求 `android.telephony`；lookup **不发** sidecar |
| `getDeviceId()` / `getImei()` / `getMeid()` / `getSubscriberId()` / `getSimSerialNumber()` | `()Ljava/lang/String;` | `callObjectMethod` / `callObjectMethodV` | 卡槽 **0** 对应标识字段 |
| `getDeviceId(int)` / `getImei(int)` / `getMeid(int)` / `getSubscriberId(int)` / `getSimSerialNumber(int)` | `(I)Ljava/lang/String;` | 同上 | arg0 为 `slotIndex` |
| `getNetworkOperator()` 等四个运营商 getter | `()Ljava/lang/String;` | 同上 | 全局运营商字符串 |
| `getNetworkCountryIso()` | `()Ljava/lang/String;` | 同上（VarArg + VaList） | `networkCountryIso`（键存在才命中；返回新 `StringObject`，含空串；sidecar `value=key=networkCountryIso,result=<值>`） |
| `getSimCountryIso()` | `()Ljava/lang/String;` | 同上（VarArg + VaList） | `simCountryIso`（键存在才命中；返回新 `StringObject`，含空串；sidecar `value=key=simCountryIso,result=<值>`） |
| `getPhoneCount()` | `()I` | `callIntMethod` / `callIntMethodV` | `phoneCount` |
| `getDataNetworkType()` | `()I` | 同上 | `dataNetworkType`（既有行为不变；sidecar value 为原始数值字符串） |
| `getNetworkType()` | `()I` | 同上（VarArg + VaList） | **复用** `dataNetworkType`（键存在才命中；与 `getDataNetworkType` 同固定值；sidecar `value=key=dataNetworkType,result=<n>`） |
| `getDataState()` | `()I` | 同上（VarArg + VaList） | `dataState`（键存在才命中；sidecar `value=key=dataState,result=<n>`） |
| `getDataActivity()` | `()I` | 同上（VarArg + VaList） | `dataActivity`（键存在才命中；sidecar `value=key=dataActivity,result=<n>`） |
| `getPhoneType()` | `()I` | 同上 | `phoneType` |
| `getSimState()` / `getSimState(int)` | `()I` / `(I)I` | 同上 | 无参用 slot 0；有参用 arg0 |
| `isNetworkRoaming()` | `()Z` | `callBooleanMethod` / `callBooleanMethodV` | `networkRoaming` |
| `getAllCellInfo()` | `()Ljava/util/List;` | `callObjectMethod` / `callObjectMethodV` | `cellInfo` 键存在才接管；返回同 VM `CellInfo*` marker 列表；sidecar `count=<n>` |
| `getCellLocation()` | `()Landroid/telephony/CellLocation;` | 同上 | 首个 `registered=true` 行，否则第一行；空数组 → Java `null`。identity 子集：`CellInfo*.isRegistered` / `getCellIdentity`、`CellIdentity*.getCi`、`GsmCellLocation.getCid` |

Helper **先匹配完整签名再读参数**。命中时 sidecar kind=`telephony`、source=`json-config`、api=`TelephonyManager.<method>`。类型化 / 字符串 `getSystemService` **不**发 sidecar。

### 明确未覆盖（勿与上表混淆）

- 基于 **subscriptionId** 的重载（如部分 `getImei(int)` 语义若被目标当作 subscription 而非 slot，本配置仍按 **slotIndex** 解释）。
- `TelephonyCallback` / `PhoneStateListener`、数据开关、特性探测、subscription 切换。
- 其它 `TelephonyManager` 方法（信号强度等）。

## android.packages

已安装包（installed packages）画像。路径为 `android.packages`（可选 **JSON 数组**）。存在时仅允许白名单字段的 `JSONObject` 元素；`packageName` 全局唯一。非法键/类型/范围在 `TraceEnvironmentConfig` parse（解析）时失败，路径形如 `android.packages[i].uid`、`android.packages[i].signaturesHex[j]`。

**节点缺失 vs 显式空数组（权威语义）：**

| 状态 | `isAndroidPackagesConfigured()` | 列表查询 / 精确查询 |
| --- | --- | --- |
| **节点缺失** | `false` | 相关 JNI（Java 原生接口）保持 notHandled；`getPackageInfo` 等可回落旧 APK/`PackageInfo` 路径；`getInstalledPackages` 等历史无 handler 仍为 `UnsupportedOperationException`（UOE） |
| **显式 `[]`** | `true` | **权威空列表**：`getInstalledPackages` / `getInstalledApplications` 返回空列表；精确包名查询对任意包名 UOE（阻断“任意包名回落当前 APK”） |
| **有条目** | `true` | 仅配置中的精确 `packageName` 命中；缺包 UOE |

**字段缺失 vs 显式空集合（按包条目）：** 对 `permissions` / `signaturesHex` / `signingCertificateHistoryHex` 等，**键缺失**与 **显式 `{}`/`[]`** 可区分（`is*Configured()`）。显式空表示“已配置但为空”，通常仍走配置路径（例如空签名数组、`permissions` 空对象下 `checkPermission` 一律拒绝）；键缺失则该子能力 notHandled 或 UOE（见下表）。

**字符串可空字段：** `versionName` / `sourceDir` / `dataDir` / `installerPackageName` / `initiatingPackageName` / `originatingPackageName` 允许 JSON `null`（configured=true、getter=null）。`versionCode` / `uid` / `enabled` / `systemApp` / 安装时间 **不允许** null。

### 字段说明

| 字段路径 | 必填 | 类型与校验 | 影响摘要 |
| --- | --- | --- | --- |
| `android.packages` | — | JSON 数组；允许 `[]` | 节点存在即进入 packages 权威模式 |
| `android.packages[i].packageName` | 是 | 点分 `[A-Za-z0-9_]` 段，非空、无 trim、全局唯一 | `PackageInfo`/`ApplicationInfo` 包名；查询键 |
| `android.packages[i].versionName` | 否 | 非空无首尾空白 String，或 JSON null | `PackageInfo.versionName` |
| `android.packages[i].versionCode` | 否 | 精确整数 `0..Integer.MAX_VALUE` | `PackageInfo.versionCode` |
| `android.packages[i].sourceDir` | 否 | 绝对路径 String，或 JSON null | `ApplicationInfo.sourceDir` / `publicSourceDir` |
| `android.packages[i].dataDir` | 否 | 绝对路径 String，或 JSON null | `ApplicationInfo.dataDir` |
| `android.packages[i].uid` | 否 | 精确整数 `0..Integer.MAX_VALUE` | `ApplicationInfo.uid`；`getPackagesForUid` / `getNameForUid`；`hasSigningCertificate(uid,…)` |
| `android.packages[i].enabled` | 否 | Boolean | `ApplicationInfo.enabled` |
| `android.packages[i].systemApp` | 否 | Boolean | 投影 `ApplicationInfo.flags`（true→`FLAG_SYSTEM=1`，false→0） |
| `android.packages[i].installerPackageName` | 否 | 包名规则 String，或 JSON null | `getInstallerPackageName`；`InstallSourceInfo.getInstallingPackageName` |
| `android.packages[i].initiatingPackageName` | 否 | 同上 | `InstallSourceInfo.getInitiatingPackageName` |
| `android.packages[i].originatingPackageName` | 否 | 同上 | `InstallSourceInfo.getOriginatingPackageName` |
| `android.packages[i].firstInstallTimeMillis` | 否 | 精确 long `0..Long.MAX_VALUE` | `PackageInfo.firstInstallTime` |
| `android.packages[i].lastUpdateTimeMillis` | 否 | 精确 long；若两者皆配则须 `>= firstInstallTimeMillis` | `PackageInfo.lastUpdateTime` |
| `android.packages[i].permissions` | 否 | JSONObject：permission 名→Boolean；允许 `{}` | `checkPermission` / `checkSelfPermission` **权威 map** |
| `android.packages[i].signaturesHex` | 否 | 字符串数组；每项非空偶数位纯 hex，归一小写、顺序保留、去重；允许 `[]` | `PackageInfo.signatures`；`SigningInfo` 当前签名；`hasSigningCertificate` 候选 |
| `android.packages[i].signingCertificateHistoryHex` | 否 | 规则同 `signaturesHex`；表示证书轮换历史 | `SigningInfo.getSigningCertificateHistory`；UID 重载选包 ranking |

根级 `android.packageName` / `versionName` / `versionCode` / `apkPath` / `dataDir` 仍服务**当前应用**回落路径；`android.packages` 命中后，配置 marker 对象**不会**再回落到根级或 APK 解析值。

### JNI 支持的 API（`PackageManager` / 字段）

拦截入口在 `AbstractJni`（`callObjectMethod`/`V`、`callIntMethod`/`V`、`callBooleanMethod`/`V`、`getObjectField` / `getIntField` / `getLongField` / `getBooleanField`）。Helper **先匹配完整签名再读参数**。

| Java API | 精确签名 / 字段 | 行为摘要 |
| --- | --- | --- |
| `getPackageInfo` | `(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;` | 节点存在时精确包名 → 同 VM `ConfiguredPackageInfo` marker；缺包 UOE |
| `getApplicationInfo` | `(Ljava/lang/String;I)Landroid/content/pm/ApplicationInfo;` | 同上，同 VM `ConfiguredApplicationInfo` marker |
| `getInstalledPackages` / `getInstalledApplications` | `(I)Ljava/util/List;` | 配置顺序列表；`[]` → 空列表；`PackageInfo` / `ApplicationInfo` 元素为同 VM marker |
| `getInstallerPackageName` | `(Ljava/lang/String;)Ljava/lang/String;` | 要求 installer 字段已配置；显式 null → Java null |
| `getInstallSourceInfo` | `(Ljava/lang/String;)Landroid/content/pm/InstallSourceInfo;` | 同 VM `ConfiguredInstallSourceInfo` marker；`getInstallingPackageName` / `getInitiatingPackageName` / `getOriginatingPackageName` 仅接受当前 BaseVM 拥有的 marker（plain/跨 VM → UOE 无事件） |
| `PackageInfo` 字段 | `packageName`/`versionName`/`versionCode`/`firstInstallTime`/`lastUpdateTime`/`signatures`/`signingInfo` | 仅同 VM `ConfiguredPackageInfo` marker；未配置 → UOE（不回落 APK）；跨 VM → UOE 无事件（无关对象仍走历史回落） |
| `ApplicationInfo` 字段 | `packageName`/`sourceDir`/`publicSourceDir`/`dataDir`/`uid`/`flags`/`enabled` | 仅同 VM `ConfiguredApplicationInfo` marker；未配置 → UOE；跨 VM → UOE 无事件（无关对象仍走历史回落） |
| `checkPermission` | `(Ljava/lang/String;Ljava/lang/String;)I` | 需 `permissions` 已配置；true→0，false/缺键/空 map→`-1`；无 `permissions` 键 → UOE |
| `checkSelfPermission` | `(Ljava/lang/String;)I` | 当前 `vm.getPackageName()` + 同上权威 map |
| `getPackagesForUid` / `getNameForUid` | `(I)[Ljava/lang/String;` / `(I)Ljava/lang/String;` | 仅 `uid` 已配置且相等的包，**配置顺序**；无匹配 → 处理为 Java null（覆盖旧“任意 uid 返回当前包”） |
| `PackageInfo.signingInfo` | `Landroid/content/pm/SigningInfo;` | `signaturesHex` 或 `signingCertificateHistoryHex` 任一已配置 → 同 VM `ConfiguredSigningInfo` marker |
| `SigningInfo.getApkContentsSigners` | `()[Landroid/content/pm/Signature;` | 仅同 VM marker；需 `signaturesHex`；按序解码；plain/跨 VM → UOE 无事件 |
| `SigningInfo.hasMultipleSigners` | `()Z` | 仅同 VM marker；需 `signaturesHex`；`size>1` → true；plain/跨 VM → UOE 无事件 |
| `SigningInfo.getSigningCertificateHistory` | `()[Landroid/content/pm/Signature;` | 仅同 VM marker；**多签**（当前 `signaturesHex.size()>1`）→ Java **null**；否则历史已配置用历史数组，否则回落当前 `signaturesHex`；plain/跨 VM → UOE 无事件 |
| `hasSigningCertificate(String,byte[],int)` | `(Ljava/lang/String;[BI)Z` | 候选 = 当前+历史 **有序去重并集**；`type=0` RAW_X509 字节相等；`type=1` 与各候选 **SHA-256** 比较；其它 type → `IllegalArgumentException`；两类签名字段都缺失 → UOE |
| `hasSigningCertificate(int,byte[],int)` | `(I[BI)Z` | 按 **已配置 uid** 精确匹配；无匹配 → **false**；共享 uid 在“有签名配置”的包中选 **newest-signed**：优先 `signingCertificateHistoryHex` **条目数最多**，历史键缺失时用 `signaturesHex` 长度 ranking；**平局保持 `android.packages` 配置顺序**；匹配包均无签名配置 → UOE |

### Sidecar（旁路事件）

| 字段 | 值 |
| --- | --- |
| `kind` | `android_package`（权限检查为 `android_permission`） |
| `source` | `json-config` |
| `api` | `PackageManager.*` / `PackageInfo.*` / `SigningInfo.*` / `ApplicationInfo.*` 等 |
| `value` | 含 package/uid/count/result 等摘要；**不**写入证书原文 |
| `note` | 中文说明 |

### 明确未覆盖

组件/Intent（意图）解析与 `queryIntentActivities`、package visibility（包可见性）、shared library（共享库）、enabled component state（组件启用状态）、`getPackagesHoldingPermissions` 等；见缺失清单第 10 节。

## android.features

系统特性（system features）列表。路径为 `android.features`（可选 **JSON 数组**）。元素为 JSONObject，仅允许 `name` / `version`。

**节点缺失 vs 显式 `[]`：** 缺失时 `hasSystemFeature` / `getSystemAvailableFeatures` 保持旧 UOE；显式空数组为已配置：`hasSystemFeature` 恒 false，可用特性数组长度为 0。

| 字段路径 | 必填 | 类型与校验 | 影响 |
| --- | --- | --- | --- |
| `android.features[i].name` | 是 | 点分 `[A-Za-z0-9_]`，唯一 | `hasSystemFeature` / `FeatureInfo.name` |
| `android.features[i].version` | 否 | 精确整数 `0..Integer.MAX_VALUE` | 版本重载比较；字段 getter 缺省投影为 `0` |

### JNI 支持的 API

| Java 方法 | 签名 | 行为 |
| --- | --- | --- |
| `hasSystemFeature(String)` | `(Ljava/lang/String;)Z` | 精确 name 存在 → true |
| `hasSystemFeature(String,int)` | `(Ljava/lang/String;I)Z` | 存在且 `(配置 version，缺省 0) >= 请求版本`；负请求正常比较 |
| `getSystemAvailableFeatures` | `()[Landroid/content/pm/FeatureInfo;` | 配置顺序 `FeatureInfo`（`ConfiguredFeatureInfo` marker） |
| `FeatureInfo.name` / `version` | 字段 | 仅同 VM `ConfiguredFeatureInfo` marker；跨 VM → UOE 无事件；无关 `FeatureInfo` 仍 UOE |

命中 sidecar kind=`android_feature`、source=`json-config`。

**与 `android.tee` 的关系：** 当 `android.features` **节点存在**（含显式 `[]`）时，`hasSystemFeature` **仅**走 features 权威结果，**不会**再使用 tee 对 `strongbox_keystore` / `hardware_keystore` 的回退。tee 回退仅在 **features 节点缺失** 时生效；见下一节 `android.tee`。

## android.tee

TEE（可信执行环境）/ Keystore 分析型固定标记配置。路径为 `android.tee`（可选 **JSONObject**）。存在时仅允许白名单键；非法键/类型/范围在 `TraceEnvironmentConfig` parse（解析）时失败，路径形如 `android.tee.securityLevel`、`android.tee.keyBlobHex`。

**分析边界（重要）：** 本节只返回**明显的固定标识 / marker**，供动态分析识别“目标是否读取了这些接口”。**不做**真实密钥生成、加密、签名、Key Attestation（密钥证明）；**不**模拟 `libteec` / QSEE / Trusty 调用，也**不**创建 `/dev/tee*`、`/dev/qseecom` 等 TEE 设备节点。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidTeeConfigured()` | 行为摘要 |
| --- | --- | --- |
| **节点缺失** | `false` | `KeyFactory.getKeySpec(..., KeyInfo.class)` 等 tee 路径 notHandled → 通常 UOE |
| **显式 `{}`** | `true` | 节点已配置，但各字段仍按**单独 presence** 决定；未配字段对应 getter UOE |
| **有字段** | `true` | 仅已配置字段驱动对应 API |

### 字段说明

| 字段路径 | 必填 | 类型与校验 | 影响摘要 |
| --- | --- | --- | --- |
| `android.tee` | — | 可选 JSONObject；仅允许下表白名单键 | 节点存在即 `isAndroidTeeConfigured()` |
| `android.tee.available` | 否 | Boolean（不可 null） | 与 `keymasterVersion` 共同驱动 `hardware_keystore` **回退**（见下） |
| `android.tee.securityLevel` | 否 | 字符串枚举，**精确**为 `SOFTWARE` \| `TRUSTED_ENVIRONMENT` \| `STRONGBOX` | `KeyInfo.getSecurityLevel()` / `isInsideSecureHardware()` |
| `android.tee.keymasterVersion` | 否 | 精确整数 `0..100` | `hardware_keystore` 版本重载比较的配置版本 |
| `android.tee.strongBoxAvailable` | 否 | Boolean（不可 null） | `strongbox_keystore` **回退** |
| `android.tee.marker` | 否 | 非空、无首尾空白 String，长度 `≤128` | `KeyInfo.getKeystoreAlias()`；sidecar 中的 marker 摘要 |
| `android.tee.keyBlobHex` | 否 | 非空**偶数长度**纯 hex 字符串（大小写均可；无空格/冒号），长度 `≤2097152` 字符（约 1 MiB 二进制） | `KeyStore.getKey` + `Key.getEncoded` 固定字节；`getAlgorithm`/`getFormat` 的 marker 前置条件 |
| `android.tee.keyAlgorithm` | 否 | 非空、无首尾空白 String，长度 `1..128`；**独立 presence**；**不**从 marker/securityLevel/keyBlobHex/keyFormat 推导 | 仅存活同 VM `ConfiguredTeeKey` 上 `Key.getAlgorithm()` |
| `android.tee.keyFormat` | 否 | 非空、无首尾空白 String，长度 `1..128`；**独立 presence**；**不**从 marker/securityLevel/keyBlobHex/keyAlgorithm 推导 | 仅存活同 VM `ConfiguredTeeKey` 上 `Key.getFormat()` |

### 安全等级映射（`KeyInfo`）

仅当 `getKeySpec` 返回的 **ConfiguredTeeKeyInfo** marker 上调用时生效；无关 `KeyInfo` 仍 UOE。

| `securityLevel` 配置值 | `getSecurityLevel()` | `isInsideSecureHardware()` |
| --- | --- | --- |
| `SOFTWARE` | `0` | `false` |
| `TRUSTED_ENVIRONMENT` | `1` | `true` |
| `STRONGBOX` | `2` | `true` |

- `getKeystoreAlias()`：需要已配置 `marker`，返回该字符串；未配置 marker → UOE。
- `getSecurityLevel` / `isInsideSecureHardware`：需要已配置 `securityLevel`；未配置 → UOE。

### `hasSystemFeature` 回退（仅 features 缺失时）

当 **`android.features` 节点缺失** 时，下列两个特性名可从 tee 字段回退（`AbstractJni` 在 features 权威路径之后尝试）。**只要 `android.features` 存在（含 `[]`），features 结果优先，tee 回退不生效。**

| 特性名 | 配置字段 | 无版本 `hasSystemFeature(name)` | 带版本 `hasSystemFeature(name, req)` |
| --- | --- | --- | --- |
| `android.hardware.strongbox_keystore` | 需已配置 `strongBoxAvailable` | 等于 `strongBoxAvailable` | 回退特性版本视为 `0`：`available && req <= 0` |
| `android.hardware.hardware_keystore` | 需已配置 `available` **或** `keymasterVersion`（至少一个） | `effectiveAvailable`：若配置了 `available` 用其值，否则（仅有 version）视为 `true` | `effectiveAvailable && configuredVersion >= req`（未配 `keymasterVersion` 时 version=`0`） |

其它特性名在 tee 回退路径下仍 notHandled（通常 UOE，除非另有 features 配置）。

### JNI 支持的分析型 API

拦截入口在 `AbstractJni`（`callObjectMethod`/`V`、`callIntMethod`/`V`、`callBooleanMethod`/`V`）。Helper **先匹配完整签名，再读参数**（VarArg / VaList）。

| Java API | 精确签名 | 前置条件 | 行为摘要 |
| --- | --- | --- | --- |
| `KeyFactory.getKeySpec` | `(Ljava/security/Key;Ljava/lang/Class;)Ljava/security/spec/KeySpec;` | `android.tee` 节点存在；arg1 为 `KeyInfo` 的 `DvmClass` | 返回 `ConfiguredTeeKeyInfo` marker（绑定 owner `BaseVM` + `TraceEnvironmentConfig` 身份；**忽略** Key 参数内容）；其它 target Class notHandled；arg1 非 `DvmClass` → IAE |
| `KeyInfo.getSecurityLevel` | `()I` | **存活**同 VM + 同 config 实例 marker + `securityLevel` | 见上表 0/1/2；跨 VM/陈旧 → UOE 无事件 |
| `KeyInfo.isInsideSecureHardware` | `()Z` | **存活**同 VM + 同 config 实例 marker + `securityLevel` | 非 `SOFTWARE` → true；跨 VM/陈旧 → UOE 无事件 |
| `KeyInfo.getKeystoreAlias` | `()Ljava/lang/String;` | **存活**同 VM + 同 config 实例 marker + `marker` 字段 | 返回配置的 marker 字符串；跨 VM/陈旧 → UOE 无事件 |
| `PackageManager.hasSystemFeature` | `(Ljava/lang/String;)Z` / `(Ljava/lang/String;I)Z` | features **未**配置 + 对应 tee 字段 | 仅 `strongbox_keystore` / `hardware_keystore` 回退 |
| `KeyStore.getKey` | `(Ljava/lang/String;[C)Ljava/security/Key;` | 已配置 `keyBlobHex` | arg0 须非 null `StringObject`（alias）；**password 忽略**；返回 `ConfiguredTeeKey` marker（owner VM + config 身份 + alias；不保存可变字节）；arg0 类型错误 → IAE；未配 keyBlob → notHandled |
| `Key.getEncoded` | `()[B` | **存活**同 VM + 同 config 实例 `ConfiguredTeeKey` + `keyBlobHex` | 每次调用 `getTeeKeyBlob()` **防御性副本** → 新 `ByteArray`；跨 VM/陈旧 → UOE 无事件；无关 Key → notHandled/UOE |
| `Key.getAlgorithm` | `()Ljava/lang/String;` | **存活**同 VM `ConfiguredTeeKey` + `keyBlobHex` + **显式** `keyAlgorithm` | 返回配置字符串；sidecar `api=Key.getAlgorithm`，`value=field=keyAlgorithm,result=<value>`；缺省/plain/stale/cross-VM → notHandled/UOE 无事件 |
| `Key.getFormat` | `()Ljava/lang/String;` | **存活**同 VM `ConfiguredTeeKey` + `keyBlobHex` + **显式** `keyFormat` | 返回配置字符串；sidecar `api=Key.getFormat`，`value=field=keyFormat,result=<value>`；缺省/plain/stale/cross-VM → notHandled/UOE 无事件 |

**未实现（请勿假设已有）：** 其它 `KeyStore` 方法（`getCertificate`/`getEntry`/`setKeyEntry` 等）、密钥生成与证明、KeyMint/Keymaster HAL、native TEE 库与设备节点；**不**做真实密码学运算。

### Sidecar（旁路事件）

命中时通过 `TraceEnvironmentEventSink.emit` 写入：

| 字段 | 值 |
| --- | --- |
| `kind` | **`tee`** |
| `source` | `json-config` |
| `api` | 如 `KeyFactory.getKeySpec`、`KeyInfo.getSecurityLevel`、`KeyStore.getKey`、`Key.getEncoded`、`Key.getAlgorithm`、`Key.getFormat`、`PackageManager.hasSystemFeature` |
| `value` | 摘要字段：alias / result / feature / byteLength / marker / `field=keyAlgorithm\|keyFormat,result=…` 等 |
| `note` | 中文说明（强调仅分析 marker、不做生成/加密/签名） |

**绝不**在 sidecar 的 value/note 中写入 `keyBlobHex` 原文或解码后的完整 blob hex；`Key.getEncoded` 仅记录 `byteLength` 与 marker 摘要。

## android.drm

MediaDrm / Widevine **分析型固定标记**配置。路径为 `android.drm`（可选 **JSONObject**）。存在时仅允许白名单键；非法键/类型/枚举/UUID/hex 在 parse 时失败。实现类：`TraceEnvironmentConfig.AndroidDrmConfig`；native 接线在 `MediaNdkModule`（`libmediandk.so` 虚拟模块）。

**分析边界（重要）：** 本节只提供**明显的固定标识 / marker**，用于动态分析识别目标是否调用了相关 NDK 符号。**不是**真实 DRM 许可、密钥交换、解密或证明；**不**实现 Java `MediaDrm` 完整语义，**不**做 provision 请求/响应、key request/response、crypto/decrypt、listener、secure stop、可被远端信任的 license/attestation。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidDrmConfigured()` | 行为摘要 |
| --- | --- | --- |
| **节点缺失** | `false` | `MediaNdkModule` 保持**旧逻辑**（见下「未配置遗留行为」）；**不**注册 `openSession`/`closeSession` 符号 |
| **显式 `{}`** | `true` | 全字段用**默认值**；session API 已注册 |
| **有字段** | `true` | 已配字段覆盖默认；hex 等按 presence |

### 字段说明

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） | presence |
| --- | --- | --- | --- | --- |
| `available` | 否 | Boolean | `true` | 有 |
| `marker` | 否 | 非空 String，禁 NUL/CR/LF，≤128 | `TRACEAI_DRM_MARKER_V1` | 有 |
| `schemeUuids` | 否 | **非空** JSONArray of UUID 字符串；规范化为小写 `UUID.toString()` 并**去重**；列表不可变 | 仅 Widevine：`edef8ba9-79d6-4ace-a3c8-27dcd51d21ed` | 有 |
| `vendor` | 否 | 非空 String，禁 NUL/CR/LF，≤256 | `TraceAI` | 有 |
| `version` | 否 | 同上 | `TRACEAI_DRM_MARKER_V1` | 有 |
| `description` | 否 | 同上 | `TraceAI DRM analysis marker` | 有 |
| `algorithms` | 否 | 同上（单字符串，可含逗号） | `AES/CBC/NoPadding,HmacSHA256` | 有 |
| `securityLevel` | 否 | 枚举 `L1`\|`L2`\|`L3`\|`UNKNOWN` | `L3` | 有 |
| `hdcpLevel` | 否 | 枚举 `HDCP_NONE`\|`HDCP_V1`\|`HDCP_V2`\|`HDCP_V2_1`\|`HDCP_V2_2`\|`HDCP_V2_3`\|`HDCP_NO_DIGITAL_OUTPUT`\|`HDCP_LEVEL_UNKNOWN` | `HDCP_NONE` | 有 |
| `maxHdcpLevel` | 否 | 同上 | `HDCP_NONE` | 有 |
| `provisioned` | 否 | Boolean | `true` | 有 |
| `deviceUniqueIdHex` | 否 | 非空偶数位纯 hex，≤2097152 字符 | 无默认字节；见 byte 属性回退 | **有**（仅键存在时） |
| `sessionIdHex` | 否 | 非空偶数位纯 hex，≤128 字符 | 无默认字节；见 openSession 回退 | **有** |

### 对 `MediaNdkModule` 的精确影响

#### `AMediaDrm_createByUUID`

- **已配置：** 将 16 字节按**大端**解释为 UUID，小写规范串与 `schemeUuids` 比较；`available=false` 或不支持的 UUID → 返回 **0**（不抛）；支持 → 返回可复用 **8 字节** marker handle 的 peer。
- **未配置：** 仅 Widevine UUID 成功；其它 UUID → `UnsupportedOperationException`。

#### `AMediaDrm_getPropertyString`

- **已配置：** 支持 `vendor`、`version`、`description`、`algorithms`、`securityLevel`、`hdcpLevel`、`maxHdcpLevel`（来自 `AndroidDrmConfig`）；未知属性仍抛异常。
- **未配置：** 仅 `vendor`（优先 `android.build.MANUFACTURER`，否则 `Google`）；其它属性抛异常。
- **字符串结构：** 共享容量跟踪块；按 **UTF-8 字节长度+1** 扩容（**禁止**按 Java 字符长度分配）。结构：`pointer`@0；**32 位** `int` 长度@4；**64 位** `long` 长度@8；长度为**不含 NUL** 的 UTF-8 字节数。

#### `AMediaDrm_getPropertyByteArray("deviceUniqueId")`

- **已配置且 `deviceUniqueIdHex` presence：** 返回**精确**配置字节与**精确**长度（不填充/不截断到 32）。
- **已配置但无 `deviceUniqueIdHex`：** 若配置了 `random.mediaDrmDeviceUniqueIdHex` 则走其 32 字节展开；否则返回 **`marker` 的 UTF-8 字节**（分析用确定性标识）。
- **未配置：** 保持旧逻辑——32 字节随机或 `mediaDrmDeviceUniqueIdHex` 展开。
- **结构：** 与字符串相同的 pointer+size_t 布局；使用独立可复用 byte-array 块，`release` 幂等释放。
- **未知属性名：** 仍抛异常。

#### `AMediaDrm_openSession` / `AMediaDrm_closeSession`

- **仅当 `android.drm` 已配置时注册**这两个符号；未配置时不注册（遗留行为不变）。
- `AMediaDrmSessionId`：pointer@0；size_t 为 32 位 int@4 / 64 位 long@8。
- **openSession：** `provisioned=false` → `AMEDIA_DRM_NOT_PROVISIONED`（**-20001**），**不写**输出会话；否则 `AMEDIA_OK`（**0**），写入 `sessionIdHex` 精确字节，或缺失时 **`marker` UTF-8**；容量跟踪 session 块 + 活跃会话状态。
- **closeSession：** 读取并边界检查输入结构；仅当与**当前活跃**会话字节序列完全一致时 `AMEDIA_OK` 并清除状态；否则 `AMEDIA_DRM_SESSION_NOT_OPENED`（**-20005**）。
- **`release`：** 释放 session 块并清除活跃状态（与 handle/string/byte-array 块一并幂等清理）。

### Sidecar（`kind=drm`，`source=json-config`）

| API | value 摘要（**禁止**写入原始 device/session hex 或解码原文） |
| --- | --- |
| `AMediaDrm_createByUUID` | `supported` / `available` / `uuid` / `marker` |
| `AMediaDrm_getPropertyString` | `property` / `value` / `marker` |
| `AMediaDrm_getPropertyByteArray` | `property` / `bytes=<长度>` / `marker` / `source`（**无** ID hex） |
| `AMediaDrm_openSession` / `closeSession` | `status` / `bytes` / `marker` / `source` |

### 明确未实现

- Java 层 `android.media.MediaDrm` / `MediaCrypto` 全量 API
- provision 请求与响应、key request/response
- 解密 / crypto session 运算
- listener 回调、secure stops
- 真实 license 与 attestation

## android.locale

区域与时区配置。路径为 `android.locale`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidLocaleConfig`；JNI 接线在 `AbstractJni`。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidLocaleConfigured()` | `getAndroidLocaleConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | 保持**遗留行为**（见下）；调用方可继续走宿主 fallback |
| **显式 `{}`** | `true` | 非 null | 默认 `languageTag=en-US`、`timezoneId=UTC` |
| **有字段** | `true` | 非 null | 已配字段经严格校验后覆盖默认 |

### 字段说明（仅允许下列键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） | 规范化存储 |
| --- | --- | --- | --- | --- |
| `languageTag` | 否 | 非空 String，无首尾空白，禁 NUL/CR/LF，≤128；**严格** `Locale.Builder.setLanguageTag`（畸形标签拒绝；**不**用 `Locale.forLanguageTag` 静默降级） | `en-US` | `Locale.toLanguageTag()` 规范串 |
| `timezoneId` | 否 | 同上字符串规则；**严格** `ZoneId.of`（未知 ID 拒绝；**不**用 `TimeZone.getTimeZone` 静默退回 GMT） | `UTC` | `ZoneId.getId()` 规范串 |

未知键（如 `language`、`extra`）在 parse 时抛 `IllegalArgumentException`，路径形如 `android.locale.extra`。错误类型/空串/空白/控制字符/超长/畸形 languageTag/未知 timezoneId 均带路径失败。不保留 JSONObject，不暴露可变共享状态；`getLocale()` 返回由规范 `languageTag` 构造的**新** `Locale` 视图。

画像一致性建议：与 `android.properties."persist.sys.timezone"`、`time.timezoneMinutesWest` 对齐（示例中 `Asia/Shanghai` 与 `-480`）。

### 对 `AbstractJni` 的精确影响

#### 静态方法（`callStaticObjectMethod` VarArg / `callStaticObjectMethodV` VaList；经 A 路由到 V 的路径同样命中）

| 精确签名 | 已配置 | 未配置（遗留） |
| --- | --- | --- |
| `java/util/Locale->getDefault()Ljava/util/Locale;` | 返回携带 **私有 provenance marker**（`ConfiguredLocale`）的 DVM 对象，**不是**宿主 `Locale` | VaList：仍返回宿主 `Locale.getDefault()`；**VarArg：仍为 `UnsupportedOperationException`** |
| `java/util/TimeZone->getDefault()Ljava/util/TimeZone;` | 返回携带 **私有 provenance marker**（`ConfiguredTimeZone`）的 DVM 对象，**不是**宿主 `TimeZone` | 仍为 `UnsupportedOperationException` |

#### 仅配置 marker 上的实例方法（VarArg 与 VaList）

对 **ConfiguredLocale** marker，支持并返回 `StringObject`（值来自 `AndroidLocaleConfig.getLocale()`）：

| 精确签名 | 结果来源 |
| --- | --- |
| `java/util/Locale->getLanguage()Ljava/lang/String;` | `Locale.getLanguage()` |
| `java/util/Locale->getCountry()Ljava/lang/String;` | `Locale.getCountry()` |
| `java/util/Locale->getScript()Ljava/lang/String;` | `Locale.getScript()` |
| `java/util/Locale->getVariant()Ljava/lang/String;` | `Locale.getVariant()` |
| `java/util/Locale->toLanguageTag()Ljava/lang/String;` | `Locale.toLanguageTag()` |
| `java/util/Locale->toString()Ljava/lang/String;` | `Locale.toString()` |
| `java/util/Locale->getISO3Language()Ljava/lang/String;` | `Locale.getISO3Language()`（规范化配置 Locale 的 ISO 639-2/T 三字码；**仅** `ConfiguredLocale`） |
| `java/util/Locale->getISO3Country()Ljava/lang/String;` | `Locale.getISO3Country()`（规范化配置 Locale 的 ISO 3166-1 alpha-3 三字码；**仅** `ConfiguredLocale`） |

对 **ConfiguredTimeZone** marker：

| 精确签名 | 路径 | 结果 |
| --- | --- | --- |
| `java/util/TimeZone->getID()Ljava/lang/String;` | 实例对象方法（VarArg / VaList） | 配置的规范 `timezoneId` |
| `java/util/TimeZone->getRawOffset()I` | 实例 int 方法（`callIntMethod` VarArg / `callIntMethodV` VaList） | `timezoneId` 的 **raw offset** 毫秒（`TimeZone.getTimeZone(timezoneId).getRawOffset()`）。**只**返回该 ID 的标准偏移，**不是**按当前时刻的 DST / `getOffset` |
| `java/util/TimeZone->getOffset(J)I` | 实例 int 方法（`callIntMethod` VarArg / `callIntMethodV` VaList；第 0 参数为 long epochMillis） | 按配置 `timezoneId` 的时区规则，对**调用参数** UTC epoch milliseconds 返回实际偏移毫秒（`TimeZone.getTimeZone(timezoneId).getOffset(epochMillis)`）。夏令时生效时包含 DST。**不**读系统默认时区，**不**用 `currentTimeMillis` / 当前系统时间 |

**provenance 隔离：** marker 对象上的**其它**签名一律 `UnsupportedOperationException`，**不会**落入旧逻辑对宿主 `Locale` 的强制转型。`ConfiguredLocale`、普通/外来/跨 VM `TimeZone`、缺 `android.locale` 或错误签名对 `getRawOffset()I` / `getOffset(J)I` 保持既有 UOE/notHandled，**不发** raw-offset / getOffset sidecar。`TimeZone` 的 `getID`/`getRawOffset`/`getOffset` 都要求 marker 的 `objectType` 属于当前 `BaseVM`；跨 VM 不接管且无 sidecar。`Locale` 的 `getLanguage`/`getCountry`/`getScript`/`getVariant`/`toLanguageTag`/`toString`/`getISO3Language`/`getISO3Country` 同样要求 marker 的 `objectType` 属于当前 `BaseVM`；跨 VM 不接管、不读取另一 VM 的配置且无 sidecar。`getISO3Language`/`getISO3Country` **只**服务配置创建的 `ConfiguredLocale`，值来自规范化配置 Locale，**不**引入默认 Locale 或宿主机依赖；缺 `android.locale`、普通/外来 Locale **不接管**，保持既有 UOE/notHandled，**不发** ISO3 sidecar。未配置时，VaList 路径上宿主 `Locale` 的 `getLanguage`/`getCountry` 遗留行为不变。

**`Configuration.locale`：** 不是本节 JSON 键。仅当 `android.configuration` 与 `android.locale` **同时**存在时，`ConfiguredConfiguration` 的 `getObjectField` 返回新的本节 `ConfiguredLocale` marker（随后上表 getter 继续生效）。任一节点缺失**不**回退宿主 Locale。详见 `android.configuration`。

### Sidecar（旁路事件）

命中时 emit：

| 字段 | 值 |
| --- | --- |
| `kind` | `android_locale` |
| `api` | `Locale.getDefault` / `Locale.getLanguage`（及其它已支持 Locale 方法，含 `Locale.getISO3Language` / `Locale.getISO3Country`）/ `TimeZone.getDefault` / `TimeZone.getID` / `TimeZone.getRawOffset` / `TimeZone.getOffset` |
| `value` | 稳定摘要：`languageTag=…,timezoneId=…` 或 `languageTag=…,result=…` / `timezoneId=…,result=…` / `timezoneId=…,rawOffsetMillis=…` / `timezoneId=…,epochMillis=…,offsetMillis=…` |
| `source` | `json-config` |
| `note` | 中文说明，如「读取配置的默认 Locale」/「按配置时区读取指定时刻偏移」 |

### 明确未实现

- `Locale`/`TimeZone` 其它方法（如 `getDisplayName`、`setDefault`、`getOffset` 多参数重载、`inDaylightTime`、`useDaylightTime` 等）。已支持的 `getRawOffset` **只**返回 `timezoneId` 的 raw offset。已支持的 `getOffset(long)` **按配置 `timezoneId` 规则和调用参数 UTC epoch milliseconds** 计算实际偏移（含 DST），**不**依赖当前系统时间 / `currentTimeMillis`；其余 offset API 仍不支持
- 资源本地化、`Configuration.locales` / `LocaleList` / 多 locale（旧字段 `Configuration.locale` 不是本节 JSON 键；仅当 `android.configuration` 与 `android.locale` **同时**存在时，由 Configuration marker 的 `getObjectField` 返回本节 `ConfiguredLocale`；任一节点缺失**不**回退宿主 Locale）
- 显示分辨率/密度/模式见下一节 `android.display`（含 `Display.getMetrics`/`getRealMetrics` v1 同 profile 子集）

## android.display

显示分辨率、密度与模式子集。路径为 `android.display`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidDisplayConfig`；JNI 接线在 `AbstractJni`。另支持 **仅** `WindowManager` / `DisplayManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService("window")` / `SystemService("display")`（**不**要求 `android.display` 节点即可拿到服务标记；lookup **不发** `android_display` sidecar）。显示读取（`WindowManager.getDefaultDisplay`、`DisplayManager.getDisplay`、`DisplayManager.getDisplays` 及 Display 字段）仍由 `android.display` 门控。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidDisplayConfigured()` | `getAndroidDisplayConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | DisplayMetrics / Display 配置路径保持**未支持**遗留（VaList `WindowManager.getDefaultDisplay` 仍返回旧 null-value Display；**VarArg 仍不支持**；`DisplayManager.getDisplay` / `getDisplays` **VarArg 与 VaList 均 UOE**；**不**当作 JSON 成功事件）；类型化 `getSystemService(WindowManager.class)` / `getSystemService(DisplayManager.class)` 仍可返回服务标记（lookup **不发** sidecar） |
| **显式 `{}`** | `true` | 非 null | 使用下列确定性默认值 |
| **有字段** | `true` | 非 null | 已配字段覆盖默认；`density` 始终由 `densityDpi` 推导 |

### 字段说明（仅允许下列 10 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `widthPixels` | 否 | 精确 JSON Number 整数，`1..32768` | `1080` |
| `heightPixels` | 否 | 精确 JSON Number 整数，`1..32768` | `2400` |
| `densityDpi` | 否 | 精确 JSON Number 整数，`1..10000` | `420` |
| `scaledDensity` | 否 | JSON Number → 有限 float，`(0, 10000]`；保留配置的精确 float 值 | 见下「推导规则」 |
| `xdpi` | 否 | 同上有限 float 规则 | `411.0` |
| `ydpi` | 否 | 同上有限 float 规则 | `411.0` |
| `refreshRate` | 否 | JSON Number → 有限 float，`(0, 1000]` | `60.0` |
| `rotation` | 否 | 精确 JSON Number 整数，`0..3`（Surface 旋转常量） | `0` |
| `modeId` | 否 | 精确 JSON Number 整数，`1..Integer.MAX_VALUE` | `1` |
| `uniqueId` | 否 | 非空 String，最长 128，无 NUL/CR/LF | JNI 使用 `local:0`（未配键时） |

未知键（如 `density`、`extra`）在 parse 时抛 `IllegalArgumentException`，路径形如 `android.display.density`。拒绝 Boolean/String/null、整数字段的小数、超出范围、NaN/Infinite，消息带配置路径。不保留 JSONObject，不暴露可变共享状态。

**推导规则：**

- **`density`（非 JSON 键）：** 始终 `densityDpi / 160f`；通过 `getDensity()` / JNI 字段 `density:F` 暴露。
- **`scaledDensity`：** 键存在时用配置 float；**键缺失**时等于有效 `densityDpi` 推导出的 `density`。显式 `{}` 时 `scaledDensity = 2.625`。

### 对 `AbstractJni` 的精确影响

#### DisplayMetrics（`Resources.getDisplayMetrics`）

| 精确签名 | 已配置 | 未配置 |
| --- | --- | --- |
| `android/content/res/Resources->getDisplayMetrics()Landroid/util/DisplayMetrics;` | 返回 **`ConfiguredDisplayMetrics`** marker | `UnsupportedOperationException` |

**`getIntField`：** `widthPixels` / `heightPixels` / `densityDpi`。
**`getFloatField`：** `density` / `scaledDensity` / `xdpi` / `ydpi`。

#### Display / Display.Mode（`WindowManager.getDefaultDisplay` / `DisplayManager.getDisplay` / `DisplayManager.getDisplays` 链）

| 精确签名 | 已配置 | 未配置 |
| --- | --- | --- |
| `Application`/`Context->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;` | 第 0 参为 `android/view/WindowManager` 时返回同一 `SystemService("window")`；第 0 参为 `android/hardware/display/DisplayManager` 时返回同一 `SystemService("display")`（**均不**要求节点；lookup **不发** sidecar） | 同左（仍返回标记；lookup **不发** sidecar） |
| `android/view/WindowManager->getDefaultDisplay()Landroid/view/Display;` | **`ConfiguredDisplay`** marker（VarArg 与 VaList） | VaList：旧逻辑返回 value=`null` 的 Display；**VarArg 仍不支持**；**不**当作 JSON 成功事件 |
| `android/hardware/display/DisplayManager->getDisplay(I)Landroid/view/Display;` | `displayId=0` → 新建 **`ConfiguredDisplay`** marker（复用同一 profile）；非 0 → Java `null`（**不**虚构多显示器；VarArg 与 VaList） | **不拦截**：VarArg 与 VaList 均 UOE；**不发** sidecar |
| `android/hardware/display/DisplayManager->getDisplays()[Landroid/view/Display;` | 长度为 1 的新 **`ArrayObject`**，元素为新 **`ConfiguredDisplay`** marker（唯一默认 profile；VarArg 与 VaList） | **不拦截**：VarArg 与 VaList 均 UOE；**不发** sidecar |
| `android/view/Display->getMode()Landroid/view/Display$Mode;` | **`ConfiguredDisplayMode`** marker | 不支持 |
| `android/view/Display->getDisplayId()I` | 固定 `0`（单默认 profile；**仅** `ConfiguredDisplay` marker；`callIntMethod` / `callIntMethodV`；**不**表示多显示器） | 不支持 |
| `android/view/Display->getRotation()I` | `rotation`（`callIntMethod` / `callIntMethodV`） | 不支持 |
| `android/view/Display->getWidth()I` | `widthPixels`（旧版别名；`callIntMethod` / `callIntMethodV`；**仅** `ConfiguredDisplay` marker） | 不支持 |
| `android/view/Display->getHeight()I` | `heightPixels`（旧版别名；`callIntMethod` / `callIntMethodV`；**仅** `ConfiguredDisplay` marker） | 不支持 |
| `android/view/Display->getMetrics(Landroid/util/DisplayMetrics;)V` | **仅** `ConfiguredDisplay` + 非 null 且类型恰为 `android/util/DisplayMetrics` 的输出对象：将输出 **value** 设为 **`ConfiguredDisplayMetrics`**（与 `getRealMetrics` **同 profile**；`callVoidMethod` / `callVoidMethodV`） | null / 错误类型 / 非 marker → UOE 无事件 |
| `android/view/Display->getRealMetrics(Landroid/util/DisplayMetrics;)V` | 同上（v1 **不**建模 insets/真机差异） | 同上 |
| `android/view/Display->getRefreshRate()F` | `refreshRate`（**仅** `callFloatMethodV`） | 不支持 |
| `android/view/Display$Mode->getModeId()I` | `modeId` | 不支持 |
| `android/view/Display$Mode->getPhysicalWidth()I` | `widthPixels` | 不支持 |
| `android/view/Display$Mode->getPhysicalHeight()I` | `heightPixels` | 不支持 |
| `android/view/Display$Mode->getRefreshRate()F` | `refreshRate`（**仅** `callFloatMethodV`） | 不支持 |

**浮点实例方法说明：** `Display.getRefreshRate` 与 `Display.Mode.getRefreshRate` **仅**接线 `callFloatMethodV`（VaList / 经 A 路由到 V 的路径）；**不**存在 `callFloatMethod(VarArg)` 接线。

**DisplayManager.getDisplay：** 精确签名 `getDisplay(I)Landroid/view/Display;`。`android.display` 已配置时，`displayId=0` 每次新建 `ConfiguredDisplay`（与 `getDefaultDisplay` **同一 profile**），随后既有 Display 读取可用；非 0 返回 Java `null`（**不**虚构多显示器、**不**新增 JSON 字段）。节点缺失时 **不拦截**（VarArg 与 VaList 均 UOE，**不发** sidecar）。

**DisplayManager.getDisplays：** 精确签名 `getDisplays()[Landroid/view/Display;`。`android.display` 已配置时，返回长度为 1 的新 `ArrayObject`，唯一元素为新 `ConfiguredDisplay`（与 `getDefaultDisplay` / `getDisplay(0)` **同一默认 profile**）；**仅**枚举单个默认 profile，**不**虚构多显示器。节点缺失时 **不拦截**（VarArg 与 VaList 均 UOE，**不发** sidecar）。

**getDisplayId：** 精确签名 `getDisplayId()I`。**仅**配置 `ConfiguredDisplay` marker 时固定返回 `0`（与 `getDefaultDisplay` / `getDisplay(0)` / `getDisplays()[0]` 的唯一默认 profile 一致）；**不**新增 JSON 字段、**不**建模多显示器编号。普通 `Display`、节点缺失或错误签名保持既有 UOE / notHandled，**不发** sidecar。

**旧版宽高：** `Display.getWidth` / `Display.getHeight` 分别读取配置的 `widthPixels` / `heightPixels`；**仅当** `android.display` 节点存在且 receiver 为配置 `ConfiguredDisplay` marker 时返回（与其它 Display 整型接口相同的 `callIntMethod` / `callIntMethodV` 路径）。普通 `Display` 或节点缺失仍不支持。

**Point 出参与身份：** `Display.getSize` / `getRealSize` 以及通用 `IWindowManager`/`IDisplayManager` `$Stub.asInterface` marker 上的 `getInitialDisplaySize` / `getBaseDisplaySize` / `getRealDisplaySize` 把 `widthPixels`/`heightPixels` 写入 `android.graphics.Point`（随后 `Point.x`/`Point.y`）。`Display.getName` 固定 `Built-in Screen`；`getUniqueId` 用配置 `uniqueId` 或默认 `local:0`；`isValid`/`hasAccess` 为 true；`getType`=`TYPE_INTERNAL`(1)；`getState`=`STATE_ON`(2)；`getFlags`=`FLAG_SECURE`(2)。节点缺失时这些签名不接管。

**getMetrics / getRealMetrics（v1 同 profile）：** 二者均把接收者配置写入输出 `DisplayMetrics` 的私有 marker，随后现有 `DisplayMetrics` 字段路径可读 `widthPixels`/`heightPixels`/`densityDpi`/`density`/`scaledDensity`/`xdpi`/`ydpi`。**不**区分应用区域与真实区域，**不**建模系统栏 insets。

**provenance 隔离：** `ConfiguredDisplay` / `ConfiguredDisplayMode` / `ConfiguredDisplayMetrics` 上的其它签名一律 `UnsupportedOperationException`。`ConfiguredDisplayMetrics` 必须属于当前 `BaseVM`（创建时绑定 owner）；跨 VM 不接管、不读取另一 VM 的 `android.display` 且无 sidecar。`ConfiguredDisplay` 必须属于当前 `BaseVM`（创建时绑定 owner）；跨 VM 不接管、不创建输出 marker、无 sidecar。`ConfiguredDisplayMode` 必须属于当前 `BaseVM`（创建时绑定 owner）；跨 VM 不接管且无 sidecar。

### Sidecar（旁路事件）

| 字段 | 值 |
| --- | --- |
| `kind` | `android_display` |
| `api` | `Resources.getDisplayMetrics` / `DisplayMetrics.<field>` / `WindowManager.getDefaultDisplay` / **`DisplayManager.getDisplay`** / **`DisplayManager.getDisplays`** / **`Display.getDisplayId`** / `Display.getRotation` / **`Display.getWidth`** / **`Display.getHeight`** / **`Display.getMetrics`** / **`Display.getRealMetrics`** / `Display.getRefreshRate` / `Display.getMode` / `Display.Mode.getModeId` 等 |
| `value` | 稳定摘要（如 `width=…,height=…,modeId=…,refreshRate=…,rotation=…`；getMetrics/getRealMetrics 为 `width=<w>,height=<h>,densityDpi=<dpi>`；getWidth/getHeight 为 `field=widthPixels\|heightPixels,result=<value>`；**`Display.getDisplayId` 为 `field=displayId,result=0`（固定，非多显示器）**；`DisplayManager.getDisplay` 为 `displayId=<n>,result=0\|null`；`DisplayManager.getDisplays` 为 **`count=1`**，**不含**配置原文） |
| `source` | `json-config` |

类型化 / 字符串 `getSystemService` **不**发 sidecar。缺节点时 VaList `getDefaultDisplay` 旧回退与 `DisplayManager.getDisplay` / `getDisplays` UOE **不**发 sidecar。

### 明确未实现

- `Display.getSupportedModes` 等其它 Display API（`getDisplayId` **仅**配置 marker 固定为 `0`，**不**表示显示编号配置或多显示器）
- `DisplayManager` 其它 API（多显示器、监听器、带 category 的 `getDisplays` 等；无参 `getDisplays()` **仅**枚举单个默认 profile）
- getMetrics/getRealMetrics 的 insets / 真机尺寸差分
- 其它 DisplayMetrics 字段（如 `noncompat*`）与 setToDefaults 等 API
- Configuration 见下一节 `android.configuration`（十三字段子集含 `densityDpi`、screen*Dp、`keyboard`/`navigation` 与 hidden-input 三字段；其余 Configuration 字段仍未实现）

## android.configuration

资源 `Configuration` **十三字段**子集。路径为 `android.configuration`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidConfigurationConfig`；JNI 接线在 `AbstractJni`。`android.configuration.uiMode` **同时**驱动 `Configuration.uiMode`（完整整型）与 **仅 SystemService** `UiModeManager.getCurrentModeType()I`（**仅** type 位：`uiMode & 0x0f`，**不含** night 位）。另支持 **仅** `UiModeManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService("uimode")`（**不**要求 `android.configuration` 节点即可拿到服务标记；lookup **不发** sidecar）。旧字段 `Configuration.locale` **不是**本对象 JSON 键：仅当本节点与 `android.locale` **同时**存在、且 receiver 为 `ConfiguredConfiguration` marker 时，`getObjectField` 返回 `android.locale` 的既有 `ConfiguredLocale` marker（复用其规范化 `languageTag`）。任一节点缺失、普通/外来 Configuration **不**回退宿主 Locale。**不**支持 `locales` / `LocaleList` / 多 locale。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidConfigurationConfigured()` | `getAndroidConfigurationConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | 保持遗留：`callObjectMethodV` 下 `Resources.getConfiguration` 仍返回 value=`null` 的 Configuration；**VarArg 仍不支持**；字段读取仍不支持；`UiModeManager.getCurrentModeType` UOE、**不发** sidecar；类型化 `getSystemService(UiModeManager.class)` 仍可返回服务标记（lookup **不发** sidecar） |
| **显式 `{}`** | `true` | 非 null | 使用下列确定性默认值（含 `densityDpi=0`、三 `screen*Dp=0`、`keyboard=0`、`navigation=0`、三 hidden=`0`、`uiMode=17`）；`getCurrentModeType` 返回默认 type 位 `17 & 0x0f = 1` |
| **有字段** | `true` | 非 null | 已配字段覆盖默认 |

### 字段说明（仅允许下列 13 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `orientation` | 否 | 精确 JSON Number 整数，`0..3` | `1` |
| `screenLayout` | 否 | 精确 JSON Number 非负整数，`0..Integer.MAX_VALUE` | `34` |
| `uiMode` | 否 | 精确 JSON Number 非负整数，`0..Integer.MAX_VALUE`；**同时**驱动 `Configuration.uiMode` 与 `UiModeManager.getCurrentModeType`（后者仅 `uiMode & 0x0f` type 位） | `17` |
| `fontScale` | 否 | JSON Number → 有限 float，`(0, 10]`；保留精确 float | `1.0` |
| `densityDpi` | 否 | 精确 JSON Number 整数，`0..1000`；**不**从 `android.display.densityDpi` 推导 | `0` |
| `screenWidthDp` | 否 | 精确 JSON Number 整数，`0..10000`；**不**从 display/density 或其它 screen*Dp 推导 | `0` |
| `screenHeightDp` | 否 | 精确 JSON Number 整数，`0..10000`；**不**从 display/density 或其它 screen*Dp 推导 | `0` |
| `smallestScreenWidthDp` | 否 | 精确 JSON Number 整数，`0..10000`；**不**从 display/density 或其它 screen*Dp 推导 | `0` |
| `keyboard` | 否 | 精确 JSON Number 整数，`0..3`（`KEYBOARD_UNDEFINED`/`NOKEYS`/`QWERTY`/`12KEY`）；**独立 presence**；**不**从其它字段推导 | `0` |
| `navigation` | 否 | 精确 JSON Number 整数，`0..4`（`NAVIGATION_UNDEFINED`/`NONAV`/`DPAD`/`TRACKBALL`/`WHEEL`）；**独立 presence**；**不**从其它字段推导 | `0` |
| `keyboardHidden` | 否 | 精确 JSON Number 整数，`0..2`（`KEYBOARDHIDDEN_UNDEFINED`/`NO`/`YES`）；**独立**；**不**从 `keyboard` 推导 | `0` |
| `hardKeyboardHidden` | 否 | 精确 JSON Number 整数，`0..2`（`HARDKEYBOARDHIDDEN_UNDEFINED`/`NO`/`YES`）；**独立**；**不**从 `keyboard`/`keyboardHidden` 推导 | `0` |
| `navigationHidden` | 否 | 精确 JSON Number 整数，`0..2`（`NAVIGATIONHIDDEN_UNDEFINED`/`NO`/`YES`）；**独立**；**不**从 `navigation` 推导 | `0` |

未知键（如 `locale`、`extra`）在 parse 时抛 `IllegalArgumentException`，路径形如 `android.configuration.locale`（JSON 键 `locale` **仍拒绝**；JNI 旧字段 `Configuration.locale` 复用独立节点 `android.locale`，不写入本对象）。`densityDpi` / 三 `screen*Dp` / `keyboard` / `navigation` / 三 hidden 拒绝 null/Boolean/String/小数/越界，路径 `android.configuration.<field>`。拒绝整数字段的非精确小数、溢出、NaN/Infinite。不保留 JSONObject，配置对象不可变。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 已配置 | 未配置 |
| --- | --- | --- |
| `Application`/`Context.getSystemService(Ljava/lang/Class;)`（**仅** `UiModeManager`；VarArg / VaList） | 返回同一 `SystemService("uimode")`；lookup **不发** sidecar | 仍返回服务标记；lookup **不发** sidecar；随后 `getCurrentModeType` 仍 UOE |
| `android/content/res/Resources->getConfiguration()Landroid/content/res/Configuration;`（`callObjectMethod` VarArg / `callObjectMethodV` VaList） | 返回携带 **私有 provenance marker**（`ConfiguredConfiguration`）的 DVM 对象，**不是**宿主 Configuration | VaList：旧逻辑 value=`null`；**VarArg：仍为 `UnsupportedOperationException`** |
| `android/app/UiModeManager->getCurrentModeType()I`（`callIntMethod` VarArg / `callIntMethodV` VaList；**仅** SystemService `uimode`） | 返回 `getUiMode() & 0x0f`（type 位；night 位不混入），并发 sidecar | 缺环境配置 / 缺 `android.configuration` 节点 / 非 SystemService：`UnsupportedOperationException`，无事件；**不**伪造默认 type |

**仅 `ConfiguredConfiguration` marker 上的字段：**

| 接口 | 精确签名 | 值 |
| --- | --- | --- |
| `getIntField` | `android/content/res/Configuration->orientation:I` | `getOrientation()` |
| `getIntField` | `android/content/res/Configuration->screenLayout:I` | `getScreenLayout()` |
| `getIntField` | `android/content/res/Configuration->uiMode:I` | `getUiMode()` |
| `getIntField` | `android/content/res/Configuration->densityDpi:I` | `getDensityDpi()`（缺省键时 `0`） |
| `getIntField` | `android/content/res/Configuration->screenWidthDp:I` | `getScreenWidthDp()`（缺省键时 `0`） |
| `getIntField` | `android/content/res/Configuration->screenHeightDp:I` | `getScreenHeightDp()`（缺省键时 `0`） |
| `getIntField` | `android/content/res/Configuration->smallestScreenWidthDp:I` | `getSmallestScreenWidthDp()`（缺省键时 `0`） |
| `getIntField` | `android/content/res/Configuration->keyboard:I` | `getKeyboard()`（缺省键时 `0`） |
| `getIntField` | `android/content/res/Configuration->navigation:I` | `getNavigation()`（缺省键时 `0`） |
| `getIntField` | `android/content/res/Configuration->keyboardHidden:I` | `getKeyboardHidden()`（缺省键时 `0`） |
| `getIntField` | `android/content/res/Configuration->hardKeyboardHidden:I` | `getHardKeyboardHidden()`（缺省键时 `0`） |
| `getIntField` | `android/content/res/Configuration->navigationHidden:I` | `getNavigationHidden()`（缺省键时 `0`） |
| `getFloatField` | `android/content/res/Configuration->fontScale:F` | `getFontScale()` |
| `getObjectField` | `android/content/res/Configuration->locale:Ljava/util/Locale;` | **仅当** `android.locale` **同时**存在：新的 `ConfiguredLocale`（`android.locale` 规范化配置）；缺 `android.locale` / 普通 Configuration：既有 UOE，**不**回退宿主 Locale，**不发**本条 sidecar |

（项目实际分发为 `getIntField` / `getFloatField` / `getObjectField` 的 String signature 重载，以及经 `DvmField` 委托的同路径；无单独虚构的 `getIntFieldV` 入口。）

**provenance 隔离：** 仅该 marker 命中字段读取；value=`null` 的普通 Configuration 或无关/过期对象**不会**误命中。`ConfiguredConfiguration` marker 还要求归属当前 `BaseVM`（创建时绑定 owner）；跨 VM 不接管、不读取另一 VM 的配置且无 sidecar。marker 上其它字段签名一律 `UnsupportedOperationException`。

### Sidecar（旁路事件）

| 字段 | 值 |
| --- | --- |
| `kind` | `android_configuration` |
| `api` | `Resources.getConfiguration` 或 `Configuration.orientation` / `screenLayout` / `uiMode` / `densityDpi` / **`screenWidthDp`** / **`screenHeightDp`** / **`smallestScreenWidthDp`** / **`keyboard`** / **`navigation`** / **`keyboardHidden`** / **`hardKeyboardHidden`** / **`navigationHidden`** / `fontScale` / **`Configuration.locale`** / **`UiModeManager.getCurrentModeType`** |
| `value` | 稳定摘要：含 `keyboard=`/`navigation=`/三 hidden 等字段，或 `field=…,result=…`（`getCurrentModeType` 为 `field=uiMode,result=<type>`，`<type>` 为 type 位而非完整 `uiMode`；**`Configuration.locale` 仅为 `languageTag=<normalized-tag>`**） |
| `source` | `json-config` |

### 明确未实现

- `Configuration.locales`、多 locale 列表 / `LocaleList` / `setLocale`（旧字段 `Configuration.locale` **仅**在两节点均存在时返回 `android.locale` 的配置 marker；节点缺失不回退宿主值）
- 硬件输入事件、输入设备枚举、导航栏、WindowMetrics、insets 等其它字段（hidden 三字段仅为配置 marker，**不**枚举输入设备/事件）
- 从 `android.display` 自动填充 `configuration.densityDpi` 或 screen*Dp
- Configuration 方法 API（如 `setTo`、`updateFrom` 等）
- 其它 `UiModeManager` API（night/car mode 切换、`setNightMode`、监听等；**仅** `getCurrentModeType` 读 type 位）

## android.power

电源交互、省电、空闲、低功耗待机、持续性能、用户空间重启与电池优化忽略能力布尔子集（**八字段**：`interactive` / `powerSaveMode` / `deviceIdleMode` / `deviceLightIdleMode` / `lowPowerStandbyEnabled` / `sustainedPerformanceModeSupported` / `rebootingUserspaceSupported` / `ignoringBatteryOptimizations`）。路径为 `android.power`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidPowerConfig`；JNI 接线在 `AbstractJni` 的实例 `callBooleanMethod`（VarArg）与 `callBooleanMethodV`（VaList）。另支持 **仅** `PowerManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService("power")`（**不**要求 `android.power` 节点即可拿到服务标记；lookup **不发** `android_power` / `android_thermal` sidecar）。**说明：** `PowerManager.isScreenOn()Z` 为 `isInteractive()Z` 的兼容别名，二者均读取 `android.power.interactive`（不为 `isScreenOn` 增加独立配置键）。各能力字段相互独立，不可从 idle/light/standby/power-save 互相推导。**`rebootingUserspaceSupported` 仅为旧版用户态重启能力固定标记，不执行实际 reboot**；较新 Android 上该接口已废弃，通常为 `false`（样例亦维持 `false`）。**`ignoringBatteryOptimizations` 为严格 JSON Boolean，默认 `false`**；仅当查询包名等于模拟 `vm` 的 `packageName` 时返回配置值。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidPowerConfigured()` | `getAndroidPowerConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | `isInteractive` / `isScreenOn` / `isPowerSaveMode` / `isDeviceIdleMode` / `isDeviceLightIdleMode` / `isLowPowerStandbyEnabled` / `isSustainedPerformanceModeSupported` / `isRebootingUserspaceSupported` / `isIgnoringBatteryOptimizations(String)` 均保持原先 `UnsupportedOperationException`，**不发** sidecar；类型化 `getSystemService(PowerManager.class)` 仍可返回服务标记（lookup **不发** `android_power` / `android_thermal` sidecar） |
| **显式 `{}`** | `true` | 非 null | 默认 `interactive=true`、其余七布尔能力键均为 `false`（含 `rebootingUserspaceSupported=false`、`ignoringBatteryOptimizations=false`） |
| **有字段** | `true` | 非 null | 已配字段覆盖默认 |

### 字段说明（仅允许下列 8 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `interactive` | 否 | 严格 JSON Boolean | `true` |
| `powerSaveMode` | 否 | 严格 JSON Boolean | `false` |
| `deviceIdleMode` | 否 | 严格 JSON Boolean | `false` |
| `deviceLightIdleMode` | 否 | 严格 JSON Boolean | `false` |
| `lowPowerStandbyEnabled` | 否 | 严格 JSON Boolean | `false` |
| `sustainedPerformanceModeSupported` | 否 | 严格 JSON Boolean | `false` |
| `rebootingUserspaceSupported` | 否 | 严格 JSON Boolean | `false` |
| `ignoringBatteryOptimizations` | 否 | 严格 JSON Boolean | `false` |

未知键（如 `batteryLevel`、`extra`）在 parse 时抛 `IllegalArgumentException`，路径形如 `android.power.batteryLevel`。字段存在时拒绝 null / String / Number，错误路径为 `android.power.<field>`（消息含 “must be a Boolean”；含 `android.power.rebootingUserspaceSupported` / `android.power.ignoringBatteryOptimizations`）。配置对象不可变，提供各布尔 getter 与对应 `*Configured()`（含 `isIgnoringBatteryOptimizations` / `isIgnoringBatteryOptimizationsConfigured`）；**不**保留 JSONObject。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 已配置 | 未配置 |
| --- | --- | --- |
| `Application`/`Context.getSystemService(Ljava/lang/Class;)`（**仅** `PowerManager`；VarArg / VaList） | 返回同一 `SystemService("power")`；lookup **不发** sidecar | 仍返回服务标记；lookup **不发** `android_power` / `android_thermal` sidecar；随后 getter 仍 UOE |
| `android/os/PowerManager->isInteractive()Z`（`callBooleanMethod` VarArg / `callBooleanMethodV` VaList） | 返回 `interactive`，并发 sidecar | `UnsupportedOperationException`，无事件 |
| `android/os/PowerManager->isScreenOn()Z`（同上；**兼容别名**） | 返回 `interactive`（与 `isInteractive` 相同值），并发 sidecar | 同上 |
| `android/os/PowerManager->isPowerSaveMode()Z`（同上） | 返回 `powerSaveMode`，并发 sidecar | 同上 |
| `android/os/PowerManager->isDeviceIdleMode()Z`（同上） | 返回 `deviceIdleMode`，并发 sidecar | 同上 |
| `android/os/PowerManager->isDeviceLightIdleMode()Z`（同上） | 返回 `deviceLightIdleMode`（**不**映射到其它空闲/待机字段），并发 sidecar | 同上 |
| `android/os/PowerManager->isLowPowerStandbyEnabled()Z`（同上） | 返回 `lowPowerStandbyEnabled`（**不**映射到 idle/light），并发 sidecar | 同上 |
| `android/os/PowerManager->isSustainedPerformanceModeSupported()Z`（同上） | 返回 `sustainedPerformanceModeSupported`（**独立能力**），并发 sidecar | 同上 |
| `android/os/PowerManager->isRebootingUserspaceSupported()Z`（同上） | 返回 `rebootingUserspaceSupported`（**固定能力标记**，不实际 reboot，不从其它电源字段推导），并发 sidecar | 同上 |
| `android/os/PowerManager->isIgnoringBatteryOptimizations(Ljava/lang/String;)Z`（同上；VarArg + VaList） | 第 0 参为**非 null** `StringObject` 时：若参数字符串 **等于** `vm.getPackageName()` 则返回 `ignoringBatteryOptimizations`，**否则返回 `false`**；并发 sidecar | 节点缺失，或 arg0 为 null / 非 `StringObject`：`UnsupportedOperationException`，**不发**事件 |

**仅按精确签名命中**；其它未列出的 `PowerManager` 方法（含其它 Low Power Standby 策略/豁免检查等）**不**支持（配置存在时同样 UOE，不发 `android_power` 事件）。不创建额外 marker；**不**为 `isScreenOn` 增加独立配置键。`isIgnoringBatteryOptimizations` **仅**在签名命中后读取 arg0，**不**改动既有无参电源布尔分发。

### Sidecar（旁路事件，仅配置命中时）

| 字段 | `isInteractive` | `isScreenOn`（别名） | `isPowerSaveMode` | `isDeviceIdleMode` | `isDeviceLightIdleMode` | `isLowPowerStandbyEnabled` | `isSustainedPerformanceModeSupported` | `isRebootingUserspaceSupported` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `kind` | `android_power` | `android_power` | `android_power` | `android_power` | `android_power` | `android_power` | `android_power` | `android_power` |
| `api` | `PowerManager.isInteractive` | `PowerManager.isScreenOn` | `PowerManager.isPowerSaveMode` | `PowerManager.isDeviceIdleMode` | `PowerManager.isDeviceLightIdleMode` | `PowerManager.isLowPowerStandbyEnabled` | `PowerManager.isSustainedPerformanceModeSupported` | `PowerManager.isRebootingUserspaceSupported` |
| `value` | `field=interactive,result=true\|false` | `field=interactive,result=true\|false` | `field=powerSaveMode,result=true\|false` | `field=deviceIdleMode,result=true\|false` | `field=deviceLightIdleMode,result=true\|false` | `field=lowPowerStandbyEnabled,result=true\|false` | `field=sustainedPerformanceModeSupported,result=true\|false` | `field=rebootingUserspaceSupported,result=true\|false` |
| `source` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` |
| `note` | 中文简述（读取配置的电源交互状态） | 中文简述（读取配置的屏幕点亮状态） | 中文简述（读取配置的省电模式状态） | 中文简述（读取配置的设备空闲模式状态） | 中文简述（读取配置的设备轻量空闲模式状态） | 中文简述（读取配置的低功耗待机启用状态） | 中文简述（读取配置的持续性能模式支持状态） | 中文简述（读取配置的用户空间重启支持；固定能力标记，不实际重启） |

**`isIgnoringBatteryOptimizations(String)` sidecar（成功处理时）：**

| 字段 | 值 |
| --- | --- |
| `kind` | `android_power` |
| `api` | `PowerManager.isIgnoringBatteryOptimizations` |
| `value` | **精确** `field=ignoringBatteryOptimizations,packageName=<arg>,result=true\|false`（`<arg>` 为入参包名字符串；仅当 `<arg>` 等于 `vm.getPackageName()` 时 `result` 可为配置的 `true`，其它包恒为 `result=false`） |
| `source` | `json-config` |
| `note` | 中文简述（读取配置的电池优化忽略状态） |

节点缺失、null / 非 String 参数路径 **不** emit。

### 明确未实现

- 其它 Low Power Standby 策略 / 豁免检查 / 相关广播，以及其它未列出的 `PowerManager` 方法（**不含**已实现的 `isIgnoringBatteryOptimizations(String)`；热状态见 `android.thermal`；电池容量见 `android.battery`）
- 电源相关广播 extras 与完整 `BatteryManager` 属性（见 `android.battery`）
- `/sys/class/power_supply/*` 自动生成（可用 `linux.files` 手工）

## graphics

GPU / GLES / EGL 查询字符串 **v1 子集**（八字段：可选 **`vendor`** / **`renderer`** / **`version`** / **`shadingLanguageVersion`** / **`extensions`** / **`eglVendor`** / **`eglVersion`** / **`eglExtensions`**）。路径为顶层 **`graphics`**（可选 **JSONObject**；**不是** `android.graphics`，以免与 Bitmap/Canvas 混淆）。实现类：`TraceEnvironmentConfig.GraphicsConfig`；JNI 接线在 `AbstractJni` 的静态 `callStaticObjectMethod` / `callStaticObjectMethodV`。

**仅查询、无上下文：** 命中 `GLES10`/`GLES20`/`GLES30.glGetString(I)` 与 `EGL14.eglQueryString(EGLDisplay,I)`。`AbstractJni` **只**对 `android/opengl/` 前缀的静态签名进入该路径，**不**改变 Settings/Locale/Build 等其它静态 Object 分发。**不**实现 native `libGLES`/`libEGL` 虚拟模块、上下文/surface/swap、`glGetStringi`/`glGetIntegerv`、Vulkan。`eglQueryString` **忽略** display 参数（v1 不物化 `EGLDisplay`）。各字段互不推导，也不从 `android.display`、传感器或网络推导。

**节点缺失 vs 显式空对象：**

| 状态 | `isGraphicsConfigured()` | `getGraphicsConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | 全部 GLES/EGL 查询 UOE，**不发** sidecar |
| **显式 `{}`** | `true` | 非 null | 八字段均未配置；任意 name UOE 无事件 |
| **有字段** | `true` | 非 null | 仅已配字段的对应 name 返回配置字符串 |

### 字段说明（仅允许下列 8 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| **`vendor`** | 否 | 非空 String，`1..256` UTF-16，禁 NUL/CR/LF | 未配置（`GL_VENDOR` UOE） |
| **`renderer`** | 否 | 同上 | 未配置（`GL_RENDERER` UOE） |
| **`version`** | 否 | 同上；应使用合法 OpenGL ES 版本格式 | 未配置（`GL_VERSION` UOE） |
| **`shadingLanguageVersion`** | 否 | 同上；应使用合法 GLSL ES 版本格式 | 未配置（`GL_SHADING_LANGUAGE_VERSION` UOE） |
| **`extensions`** | 否 | JSONArray of 唯一非空 token（`1..256`，禁 NUL/CR/LF/**空白**）；最多 128 项；允许 `[]` | 未配置（`GL_EXTENSIONS` UOE）；`[]` → 空串 |
| **`eglVendor`** | 否 | 与 `vendor` 相同 | 未配置（`EGL_VENDOR` UOE） |
| **`eglVersion`** | 否 | 同上 | 未配置（`EGL_VERSION` UOE） |
| **`eglExtensions`** | 否 | 与 `extensions` 相同 | 未配置（`EGL_EXTENSIONS` UOE）；`[]` → 空串 |

未知键（如 `extra`/`vulkan`）在 parse 时抛 `IllegalArgumentException`，路径形如 `graphics.extra`。节点非对象时错误路径为 `graphics`。扩展 token 含空白或重复时路径为 `graphics.extensions[i]` / `graphics.eglExtensions[i]`。查询时扩展以**单个空格**拼接。配置对象不可变；**不**保留 JSONObject/JSONArray。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 接入 | 已配置且该 name 有字段 | 未配置 |
| --- | --- | --- | --- |
| `android/opengl/GLES10->glGetString(I)Ljava/lang/String;` | `callStaticObjectMethod` / `V` | `0x1F00`/`1F01`/`1F02`/`1F03`/`8B8C` → 对应字段（extensions 为空串合法） | 节点缺失 / 该字段省略 / 未知 name / 错误签名 → UOE，**不发**事件 |
| `android/opengl/GLES20->glGetString(I)Ljava/lang/String;` | 同上 | 同上 | 同上 |
| `android/opengl/GLES30->glGetString(I)Ljava/lang/String;` | 同上 | 同上 | 同上 |
| `android/opengl/EGL14->eglQueryString(Landroid/opengl/EGLDisplay;I)Ljava/lang/String;` | 同上 | `0x3053`/`3054`/`3055` → `eglVendor`/`eglVersion`/`eglExtensions`；**忽略** display | 节点缺失 / 该字段省略 / 未知 name → UOE，**不发**事件 |

### Sidecar（仅命中时）

| 字段 | 值 |
| --- | --- |
| `kind` | `graphics` |
| `api` | `GLES10.glGetString` / `GLES20.glGetString` / `GLES30.glGetString` / `EGL14.eglQueryString` |
| `value` | `name=<int>,field=<vendor\|renderer\|version\|shadingLanguageVersion\|extensions\|eglVendor\|eglVersion\|eglExtensions>,resultLength=<n>`（**不**写原文） |
| `source` | `json-config` |
| `note` | 中文简述（读取配置的图形查询字符串） |

### 明确未实现

- native `libGLES.so` / `libEGL.so` / `libvulkan.so` 虚拟模块与 `glGetString`/`eglQueryString`/`vkGetPhysicalDeviceProperties` 符号
- `glGetStringi` / `glGetIntegerv` / 上下文、surface、swap、makeCurrent
- `EGLDisplay`/`EGLContext`/`EGLConfig` 对象与 create/destroy
- Vulkan 字段与 API

## android.thermal

设备热状态与热余量分析标记子集（两字段：`currentThermalStatus` / `headroom`）。路径为 `android.thermal`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidThermalConfig`；JNI 接线在 `AbstractJni`：`getCurrentThermalStatus` 走 `callIntMethod` / `callIntMethodV`；`getThermalHeadroom` **仅** `callFloatMethodV`（VaList，**无** VarArg float 分发）。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidThermalConfigured()` | `getAndroidThermalConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | `getCurrentThermalStatus` / `getThermalHeadroom` 均保持原先 `UnsupportedOperationException`，**不发** sidecar |
| **显式 `{}`** | `true` | 非 null | 默认 `currentThermalStatus=0`、`headroom=1.0` |
| **有字段** | `true` | 非 null | 已配字段覆盖默认 |

### 字段说明（仅允许下列 2 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `currentThermalStatus` | 否 | 精确 JSON Number 整数，`0..6` | `0` |
| `headroom` | 否 | 严格 JSON Number → 有限 **非负** float（拒绝 null / String / Boolean / NaN / Infinity / 负数） | `1.0` |

未知键（如 `extra`）在 parse 时抛 `IllegalArgumentException`，路径形如 `android.thermal.extra`。`currentThermalStatus` 存在时拒绝 null / String / Boolean / 小数 / 溢出，错误路径 `android.thermal.currentThermalStatus`；`headroom` 错误路径 `android.thermal.headroom`。配置对象不可变，提供 `getCurrentThermalStatus()` / `getHeadroom()` 与对应 `*Configured()`；**不**保留 JSONObject。

### Android 热状态枚举含义概览（合法取值 0..6）

| 值 | 常量（概览） | 含义（简要） |
| --- | --- | --- |
| `0` | `THERMAL_STATUS_NONE` | 无限制 / 正常 |
| `1` | `THERMAL_STATUS_LIGHT` | 轻度节流 |
| `2` | `THERMAL_STATUS_MODERATE` | 中度节流 |
| `3` | `THERMAL_STATUS_SEVERE` | 重度节流 |
| `4` | `THERMAL_STATUS_CRITICAL` | 临界 |
| `5` | `THERMAL_STATUS_EMERGENCY` | 紧急 |
| `6` | `THERMAL_STATUS_SHUTDOWN` | 即将关机级 |

本配置为**分析型固定标记**：固定返回配置的整型状态码与 `headroom` float；**不**模拟真实温度传感器、thermal zone 拓扑或随时间变化的热模型。`getThermalHeadroom` 的 `forecastSeconds` 参数仅记入 sidecar，**不**改变返回值。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 已配置 | 未配置 |
| --- | --- | --- |
| `android/os/PowerManager->getCurrentThermalStatus()I`（`callIntMethod` VarArg / `callIntMethodV` VaList） | 返回 `currentThermalStatus` | `UnsupportedOperationException`，无事件 |
| `android/os/PowerManager->getThermalHeadroom(I)F`（**仅** `callFloatMethodV` VaList） | 返回固定配置 `headroom`（与第 0 个 int 参数 `forecastSeconds` **无关**） | 同上 UOE，**不发** 事件 |

**仅按精确签名命中**；`ThermalStatusListener` 及其它热相关方法**不**支持（配置存在时同样 UOE，不发 `android_thermal` 事件）。不创建额外 marker；**不**改变 `android.power` 布尔分发。

### Sidecar（旁路事件，仅配置命中时）

| 字段 | `getCurrentThermalStatus` | `getThermalHeadroom` |
| --- | --- | --- |
| `kind` | `android_thermal` | `android_thermal` |
| `api` | `PowerManager.getCurrentThermalStatus` | `PowerManager.getThermalHeadroom` |
| `value` | `field=currentThermalStatus,result=<0..6>` | `field=headroom,forecastSeconds=<int>,result=<float>` |
| `source` | `json-config` | `json-config` |
| `note` | 中文简述（如读取配置的当前热状态） | 中文简述（明确为固定配置热余量标记，与 forecastSeconds 无关） |

### 明确未实现

- `ThermalStatusListener` 注册/回调
- 具体温度读数、thermal zones 拓扑
- `/sys/class/thermal/*` 自动生成（可用 `linux.files` 手工）

## android.battery

电池容量、电荷/能量计数、瞬时/平均电流、状态、固定剩余充电时间、充电布尔与 extras 子集（**十一字段**：`capacityPercent` / `charging` / `chargeCounterUah` / `currentNowUa` / `currentAverageUa` / `energyCounterNwh` / `status` / `chargeTimeRemainingMillis` / `plugged` / `health` / `voltageMv` / `temperatureTenthsC`，彼此独立、**不**互相推导）。`health` 1..7；`voltageMv` 毫伏；`temperatureTenthsC` 十分之一摄氏度。键存在时另渲染只读 `/sys/class/power_supply/battery/{health,voltage_now,temp}`。`android.powerProfile.averagePower` 为 name→瓦特 map，仅命中键时接管 `PowerProfile.getAveragePower`。`getIntProperty`/`getLongProperty`：`propertyId=4`→`capacityPercent`；`1`→`chargeCounterUah`；`2`→`currentNowUa`；`3`→`currentAverageUa`；`5`→`energyCounterNwh`（**仅** long）；`6`→`status`（**仅** int）。`computeChargeTimeRemaining()J` **仅**在 `chargeTimeRemainingMillis` 键存在时返回固定 long（`-1` 为官方 unable-to-compute 标记，或非负毫秒；**不**从 charging/capacity/current/status 计算）。`isCharging()Z` 读 `charging`。路径为 `android.battery`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidBatteryConfig`；JNI 在 `AbstractJni`（VarArg + VaList）。另支持 **仅** `BatteryManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService("batterymanager")`（**不**要求 `android.battery` 节点即可拿到服务标记；lookup **不发** `android_battery` sidecar）。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidBatteryConfigured()` | `getAndroidBatteryConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | `getIntProperty` / `getLongProperty` / `isCharging` / `computeChargeTimeRemaining` 均 UOE，**不发** sidecar；类型化 `getSystemService(BatteryManager.class)` 仍可返回服务标记（lookup **不发** `android_battery` sidecar） |
| **显式 `{}`** | `true` | 非 null | 默认 `capacityPercent=73`、`charging=false`；可选键未配置 → 对应 API 仍 UOE |
| **有字段** | `true` | 非 null | 已配键覆盖/启用；未写可选键不默认 |

### 字段说明（仅允许下列 12 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `capacityPercent` | 否 | 精确 JSON Number 整数，`0..100` | `73` |
| `charging` | 否 | 严格 JSON Boolean | `false` |
| `chargeCounterUah` | 否 | 精确 JSON Number 整数，`0..2147483647`；**无**默认；**不**从 capacity/charging 推导 | 未配置（`isChargeCounterUahConfigured()=false`） |
| `currentNowUa` | 否 | 精确 JSON Number 有符号整数，`-2147483648..2147483647`；**无**默认 | 未配置（`isCurrentNowUaConfigured()=false`） |
| `currentAverageUa` | 否 | 精确 JSON Number 有符号整数，`-2147483648..2147483647`；**无**默认 | 未配置（`isCurrentAverageUaConfigured()=false`） |
| `energyCounterNwh` | 否 | 精确 JSON Number 非负 64 位整数，`0..9223372036854775807`；**无**默认 | 未配置（`isEnergyCounterNwhConfigured()=false`） |
| `status` | 否 | 精确 JSON Number 整数，**仅** `1..5`；**无**默认；**不**从 `charging` 推导 | 未配置（`isStatusConfigured()=false`） |
| `chargeTimeRemainingMillis` | 否 | 精确 JSON Number long：`-1`（无法计算）或非负 `0..9223372036854775807`；**无**默认；**不**计算/推导 | 未配置（`isChargeTimeRemainingMillisConfigured()=false`） |
| `plugged` | 否 | 精确 JSON Number 整数，**仅** `0`/`1`/`2`/`4`；**无**默认 | 未配置（`isPluggedConfigured()=false`） |
| `health` | 否 | 精确 JSON Number 整数，**仅** `1..7`；**无**默认；**不**从 status/charging 推导 | 未配置（`isHealthConfigured()=false`） |
| `voltageMv` | 否 | 精确 JSON Number 整数，`0..Integer.MAX_VALUE` 毫伏；**无**默认 | 未配置（`isVoltageMvConfigured()=false`） |
| `temperatureTenthsC` | 否 | 精确 JSON Number 整数，`-2000..2000`（十分之一摄氏度）；**无**默认 | 未配置（`isTemperatureTenthsCConfigured()=false`） |

未知键在 parse 时抛 `IllegalArgumentException`。数值/布尔字段拒绝 null / 错误类型 / 小数 / 越界，路径 `android.battery.<field>`。配置对象不可变；**不**保留 JSONObject。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 条件 | 已配置 | 未配置 / 隔离 |
| --- | --- | --- | --- |
| `Application`/`Context.getSystemService(Ljava/lang/Class;)`（**仅** `BatteryManager`；VarArg / VaList） | **不**要求 `android.battery` 节点 | 返回同一 `SystemService("batterymanager")`；lookup **不发** sidecar | 仍返回服务标记；lookup **不发** `android_battery` sidecar；随后读取器仍 UOE |
| `getIntProperty(I)I` / `getLongProperty(I)J` | 节点存在且 `propertyId==4` | 返回 `capacityPercent`（long 拓宽） | 节点缺失 UOE 无事件 |
| `getIntProperty(I)I` / `getLongProperty(I)J` | 节点存在且 `propertyId==1` **且** `chargeCounterUah` 键存在 | 返回固定 `chargeCounterUah`（long 拓宽） | 键缺失 / 节点缺失 / 其它 propertyId：UOE 无事件 |
| `getIntProperty(I)I` / `getLongProperty(I)J` | 节点存在且 `propertyId==2` **且** `currentNowUa` 键存在 | 返回固定 `currentNowUa`（long 拓宽） | 键缺失 / 节点缺失 / 其它 propertyId：UOE 无事件 |
| `getIntProperty(I)I` / `getLongProperty(I)J` | 节点存在且 `propertyId==3` **且** `currentAverageUa` 键存在 | 返回固定 `currentAverageUa`（long 拓宽） | 键缺失 / 节点缺失 / 其它 propertyId：UOE 无事件 |
| `getLongProperty(I)J` | 节点存在且 `propertyId==5` **且** `energyCounterNwh` 键存在 | 返回固定 `energyCounterNwh` | 键缺失 / 节点缺失：UOE 无事件 |
| `getIntProperty(I)I` | `propertyId==5` | **永不接线**（避免截断；即使键存在也 UOE 无事件） | 始终 UOE 无事件 |
| `getIntProperty(I)I` | 节点存在且 `propertyId==6` **且** `status` 键存在 | 返回固定 `status`（1..5） | 键缺失 / 节点缺失：UOE 无事件 |
| `getLongProperty(I)J` | `propertyId==6` | **永不接线**（status 仅 int；即使键存在也 UOE 无事件） | 始终 UOE 无事件 |
| `computeChargeTimeRemaining()J` | 节点存在 **且** `chargeTimeRemainingMillis` 键存在 | 返回固定 long（`-1` 或非负） | 键缺失 / 节点缺失：UOE 无事件 |
| `isCharging()Z` | 节点存在 | 返回 `charging` | 节点缺失 UOE 无事件 |

**已实现** propertyId `4` 与 **可选** `1`、`2`、`3`，**可选 long-only** `5`，**可选 int-only** `6`，以及 **可选** `computeChargeTimeRemaining` 固定标记。**另：** 可选 `plugged`（仅 `0`/`1`/`2`/`4`）在键存在时接管粘性 `registerReceiver(null, …)` 的 `Intent.getIntExtra("plugged"|"status"|"level"|"health"|"voltage"|"temperature")`。`health`/`voltageMv`/`temperatureTenthsC` 键存在时另渲染只读 `/sys/class/power_supply/battery/{health,voltage_now,temp}`（`voltage_now` 为微伏＝毫伏×1000）。`android.powerProfile.averagePower` 为独立节点：name→有限 double 瓦特 map，仅命中 name 时接管 `PowerProfile.getAveragePower(String)`。**不**支持其它 propertyId、真实充电时间估算/状态机迁移。

### Sidecar（旁路事件，仅命中时）

| api | value 示例 | note |
| --- | --- | --- |
| `BatteryManager.getIntProperty` / `getLongProperty`（capacity） | `propertyId=4,field=capacityPercent,result=<n>` | 读取配置的电池容量百分比（长整型文案用于 long） |
| `BatteryManager.getIntProperty` / `getLongProperty`（charge counter） | `propertyId=1,field=chargeCounterUah,result=<n>` | 读取配置的电池电荷计数（微安时） |
| `BatteryManager.getIntProperty` / `getLongProperty`（current now） | `propertyId=2,field=currentNowUa,result=<n>` | 读取配置的电池瞬时电流（微安） |
| `BatteryManager.getIntProperty` / `getLongProperty`（current average） | `propertyId=3,field=currentAverageUa,result=<n>` | 读取配置的电池平均电流（微安） |
| `BatteryManager.getLongProperty`（energy counter） | `propertyId=5,field=energyCounterNwh,result=<n>` | 读取配置的电池能量计数（纳瓦时） |
| `BatteryManager.getIntProperty`（status） | `propertyId=6,field=status,result=<n>` | 读取配置的电池状态 |
| `BatteryManager.computeChargeTimeRemaining` | `field=chargeTimeRemainingMillis,result=<n>` | 读取配置的电池剩余充电时间（毫秒） |
| `BatteryManager.isCharging` | `field=charging,result=true\|false` | 读取配置的电池充电状态 |

共性：`kind=android_battery`，`source=json-config`。

### 明确未实现

- `getIntProperty` 的 `propertyId=5`（刻意不接线，避免截断）
- `getLongProperty` 的 `propertyId=6`（status 仅 int）
- `getIntProperty` / `getLongProperty` 的其它未列出 propertyId
- 从 capacity/current/charging **实时估算**剩余充电时间（仅固定配置标记）
- 非粘性 `ACTION_BATTERY_CHANGED` 注册（仅 `registerReceiver(null)` + 已配置 extras）、status 状态迁移
- `/sys/class/thermal/*` 与其它 `power_supply` 节点自动生成（battery `health`/`voltage_now`/`temp` 已由 extras 渲染；其余可用 `linux.files` 手工）

## android.cameras

摄像头数量、信息与 Camera1 喂帧子集（**三字段**：`count`、可选 `infos`、可选 **`streams`**）。路径为 `android.cameras`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidCamerasConfig` / `AndroidCameraInfoConfig` / `AndroidCameraStreamConfig`；JNI 接线在 `AbstractJni`：静态 `getNumberOfCameras` / `getCameraInfo` / **`open`**，实例 `getParameters` / preview / `takePicture`，以及 `CameraInfo` / `Camera.Size` 字段。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidCamerasConfigured()` | `getAndroidCamerasConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | `getNumberOfCameras` / `getCameraInfo` / `CameraInfo` 字段保持原先 `UnsupportedOperationException`，**不发** sidecar |
| **显式 `{}`** | `true` | 非 null | 默认 `count=0`，`infos` 未配置 |
| **仅 count** | `true` | 非 null | `getNumberOfCameras` 可用；`open(id)` 在 `0<=id<count` 时返回 Camera marker；**无** `infos` 时 `getCameraInfo` 仍 UOE；**无** `streams` 时 preview/`takePicture` 不接管 |
| **count + infos** | `true` | 非 null | `infos.length` 必须等于 `count`；`getCameraInfo` + `facing`/`orientation` 可用；可选 `canDisableShutterSound` 仅在该条目显式配置时可读 |
| **count + streams** | `true` | 非 null | `streams` 键存在才接管 preview / JPEG；显式 `[]` 为权威空快照（`open` 仍可用，无帧） |

### 字段说明（仅允许下列 3 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `count` | 当存在 `infos` 时**必填**；否则可选 | 精确 JSON Number 整数，`0..16` | `0`（无 `infos` 时） |
| `infos` | 否 | JSONArray，长度必须等于 `count`；每项见下表 | 未配置（空列表，`isInfosConfigured()=false`） |
| `streams` | 否 | JSONArray；键缺失不接管喂帧；显式 `[]` 为空快照；每项见下表 | 未配置（`isStreamsConfigured()=false`） |

#### `infos[i]` 对象（`facing`、`orientation` 必填；可选 `canDisableShutterSound`）

| 字段 | 类型与校验 |
| --- | --- |
| `facing` | 精确 JSON 整数，仅 `0`/`1`/`2`（`CAMERA_FACING_BACK` / `FRONT` / `EXTERNAL`） |
| `orientation` | 精确 JSON 整数，仅 `0`/`90`/`180`/`270` |
| `canDisableShutterSound` | 可选；严格 JSON Boolean；**独立 presence**；键缺失为未配置、**不**默认、**不**从 facing/orientation 或其它摄像头推导 |

#### `streams[i]` 对象（`cameraId`/`width`/`height` 必填；至少一项预览或 JPEG 源）

| 字段 | 类型与校验 |
| --- | --- |
| `cameraId` | 精确整数 `0..count-1`，数组内唯一；**不**从 `infos` 推导 |
| `width` / `height` | 精确整数 `1..8192` |
| `previewHex` | 可选偶数位 hex（空白/`:` 忽略）；解码后必须是 NV21，长度 `width*height*3/2`；此时 width/height 须为偶数；与 `previewFile` **互斥** |
| `previewFile` | 可选绝对 POSIX overlay 路径（`1..256`，与 `fileOverlayRoot` 相同 guest 规则：禁 `..` / 反斜杠 / 盘符 / 空白）；指向 NV21 原始字节；与 `previewHex` **互斥**；parse 不读文件，投递时 `resolveCameraPreview` 校验长度 `width*height*3/2` |
| `jpegHex` | 可选偶数位 hex；解码后至少 1 字节；与 `jpegFile` **互斥** |
| `jpegFile` | 可选绝对 POSIX overlay 路径（规则同 `previewFile`）；与 `jpegHex` **互斥**；parse 不读文件，投递时 `resolveCameraJpeg` 要求非空 |

未知键（如 `ids`、`extra`）在 parse 时抛 `IllegalArgumentException`，路径形如 `android.cameras.ids` / `android.cameras.infos[0].extra` / `android.cameras.streams[0].extra`。`count` 存在时拒绝 null / String / Boolean / 小数 / 越界，错误路径为 `android.cameras.count`。存在 `infos` 但缺 `count` 时错误路径含 `android.cameras.count`；长度不一致错误路径含 `android.cameras.infos`。`canDisableShutterSound` 存在时拒绝 null / String / Number，错误路径为 `android.cameras.infos[i].canDisableShutterSound`。配置对象不可变，提供 `getCount()` / `isCountConfigured()` / `getInfos()` / `isInfosConfigured()` / `getStreams()` / `isStreamsConfigured()` / `findStream` 与 `AndroidCameraInfoConfig.getFacing()` / `getOrientation()` / `isCanDisableShutterSoundConfigured()` / `getCanDisableShutterSound()`；stream 行提供 `getPreviewNv21()` / `getPreviewFile()` / `getJpeg()` / `getJpegFile()`；投递用 `resolveCameraPreview` / `resolveCameraJpeg`（hex 内联或 overlay 文件，二者互斥）；**不**保留 JSONObject/JSONArray。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 接入 | 条件 | 已配置 | 未配置 / 隔离 |
| --- | --- | --- | --- | --- |
| `android/hardware/Camera->getNumberOfCameras()I` | 静态 int VarArg/VaList | 节点存在 | 返回 `count` | 节点缺失：UOE，**不发**事件 |
| `android/hardware/Camera->getCameraInfo(ILandroid/hardware/Camera$CameraInfo;)V` | 静态 void VarArg/VaList | 节点存在且 **`infos` 已配置**，`0<=cameraId<infos.length`，arg1 精确为 `Camera$CameraInfo` 对象 | 向输出对象写入同 VM `ConfiguredCameraInfo` 标记 | 缺 infos / 越界 index / null 或错误输出类型 / 节点缺失 / 错误签名：UOE，**不发**事件 |
| `android/hardware/Camera$CameraInfo->facing:I` | `getIntField` | 存活 `ConfiguredCameraInfo` 标记 | 返回配置 `facing` | 普通/外来/过期标记：UOE 无事件 |
| `android/hardware/Camera$CameraInfo->orientation:I` | `getIntField` | 同上 | 返回配置 `orientation` | 同上；其它 `CameraInfo` 整型字段在存活标记上亦 UOE 无事件 |
| `android/hardware/Camera$CameraInfo->canDisableShutterSound:Z` | `getBooleanField` | 存活同 VM 标记 **且该条目显式配置了该字段** | 返回配置 Boolean | 缺字段 / 普通 / 外来 / 过期标记 / 错误签名 / 其它字段：UOE 无事件 |
| `android/hardware/Camera->open()Landroid/hardware/Camera;` / `open(I)` | 静态 Object VarArg/VaList | 节点存在且 `0<=id<count`（无参视为 0） | 新 `ConfiguredCameraDevice` | 节点缺失 / 越界：不接管 |
| `Camera.release` / `stopPreview` / `setPreviewDisplay` / `setPreviewTexture` / `setDisplayOrientation` / `setParameters` | 实例 void | 存活 Camera marker | 成功空操作 | 普通/外来 Camera：UOE |
| `setPreviewCallback` / `setOneShotPreviewCallback` / `setPreviewCallbackWithBuffer` | 实例 void | 存活 Camera marker | 记录回调；one-shot 在投递后清除 | 缺 marker：UOE |
| `startPreview()V` | 实例 void | 存活 marker **且** 该 `cameraId` 能 `resolveCameraPreview`（`previewHex` 或 overlay `previewFile`） | 若已设回调则投递 NV21 副本到 `onPreviewFrame` | 无 stream / 无预览源 / overlay 缺失或长度不对：不接管 |
| `takePicture`（3/4 参） | 实例 void | 存活 marker **且** 该 `cameraId` 能 `resolveCameraJpeg`（`jpegHex` 或 overlay `jpegFile`） | 末个 `PictureCallback` 收到 JPEG 副本 | 无 JPEG 源 / overlay 缺失或空文件：不接管 |
| `getParameters()` | 实例 Object | 存活 Camera marker | 新 `ConfiguredCameraParameters` | 非 marker：UOE |
| `Parameters.getPreviewSize` / `getPictureSize` / `getSupportedPreviewSizes` / `getSupportedPictureSizes` | 实例 Object | 存活 Parameters **且** 该 id 有 stream | `Camera.Size` 读 `width`/`height`；supported 为单元素列表 | 无 stream：不接管 |
| `Parameters.getPreviewFormat()I` | 实例 int | 存活 Parameters **且** 该 id 有预览源（`previewHex` 或 `previewFile`） | `17`（NV21） | 无预览源：不接管 |
| `Camera.Size.width` / `height` | `getIntField` | 存活 Size 标记 | 配置宽高 | 普通 Size：UOE |

### Sidecar（旁路事件，仅命中时）

| api | value | note |
| --- | --- | --- |
| `Camera.getNumberOfCameras` | `field=count,result=<0..16>` | 读取配置的摄像头数量 |
| `Camera.getCameraInfo` | `cameraId=<n>,facing=<n>,orientation=<n>` | 将配置的摄像头信息写入 CameraInfo |
| `CameraInfo.facing` | `field=facing,result=<n>` | 读取配置的摄像头朝向 |
| `CameraInfo.orientation` | `field=orientation,result=<n>` | 读取配置的摄像头传感器方向 |
| `CameraInfo.canDisableShutterSound` | `field=canDisableShutterSound,result=true\|false` | 读取配置的摄像头快门音关闭能力 |
| `Camera.open` | `cameraId=<n>` | 打开配置的摄像头 |
| `Camera.startPreview` | `cameraId=<n>,format=nv21,bytes=<n>,delivered=true\|false` | 投递配置的预览帧（**不**写 hex） |
| `Camera.takePicture` | `cameraId=<n>,format=jpeg,bytes=<n>,delivered=true\|false` | 投递配置的 JPEG（**不**写 hex） |

共性：`kind=android_camera`，`source=json-config`。

**Camera2 子集（复用同一 `count` / `infos` / `streams`，不新增 JSON 键）：** `Context.CAMERA_SERVICE` / `getSystemService("camera")` / **仅** `CameraManager` 的类型化 `getSystemService(Class)` → 同一 `SystemService("camera")`（**不**要求节点；lookup 无 sidecar）。节点存在时：`getCameraIdList` 返回 `"0".."count-1"`；`getCameraCharacteristics(id)` 在合法 id 时返回 marker；`get(LENS_FACING)` / `get(SENSOR_ORIENTATION)` 仅 `infos` 已配置；`get(SENSOR_INFO_PIXEL_ARRAY_SIZE)` 仅该 id 有 `streams` 行，返回 `android.util.Size`。`openCamera(id, StateCallback, Handler)` 投递 `onOpened`。`createCaptureSession(List, StateCallback, Handler)` 立即 `onConfigured`；`createCaptureRequest` / `Builder.addTarget` / `Builder.set`（忽略 Key）/ `build`；`capture` / `setRepeatingRequest` 再投递 `onImageAvailable` 并调用 `CaptureCallback.onCaptureCompleted`（`TotalCaptureResult` 为空 marker）。`ImageReader.newInstance(w,h,format,max)` **仅**当某条 stream 的宽高匹配且 format 为 JPEG(`256`) 且能 resolve JPEG，或 NV21(`17`)/YUV_420_888(`35`) 且能 resolve preview；`setOnImageAvailableListener` 立即 `onImageAvailable`；`acquireLatestImage` / `acquireNextImage` 返回 Image。JPEG / NV21 为单 plane；**YUV_420_888 拆成 Y/U/V 三 plane**（由 NV21 解交织为 packed I420，`pixelStride=1`，U/V `rowStride=width/2`）。`android.cameras` 存在时，`CaptureRequest`/`CameraMetadata`/`CameraDevice.TEMPLATE_*` 静态常量可读（Key 为 dummy、整型为 `0`/`1`），**不是**指纹源。

**NDK 子集（复用同一 `count` / `streams`，不新增 JSON 键）：** `android.cameras` 存在时自动注册 `libcamera2ndk.so`；`streams` 存在时同时把 `AImageReader`/`AImage` 挂到 `libmediandk.so`（与 DRM 同库，仅注册一次）。`ACameraManager_create` / `getCameraIdList` / `openCamera` / `ACameraDevice_getId`/`close` 读 `count`（id 为 `"0".."count-1"`）。`AImageReader_new(w,h,format,max)` **仅**当某条 stream 宽高匹配且能 resolve 对应帧（JPEG=`0x100` / NV21=`17` / YUV_420_888=`0x23`）；`acquireLatestImage` / `acquireNextImage` 立即返回配置帧。YUV_420_888 三 plane（与 Java ImageReader 相同：NV21→packed I420）；JPEG/NV21 单 plane。`AImage_getPlaneData`/`getPlaneRowStride`/`getPlanePixelStride` 读这些 plane。`ACaptureSession*` / `ACaptureRequest*` 为成功空 stub（不推动 HAL）。sidecar `kind=android_camera`，`source=json-config`，**不**写 hex。缺节点 / 缺 streams / 尺寸不匹配 / overlay 失败：返回 `ACAMERA_ERROR_INVALID_PARAMETER` / `AMEDIA_ERROR_INVALID_PARAMETER`，**不**虚构帧。

### 明确未实现

- 真实 Surface 合成、`SessionConfiguration` / high-speed session
- `ACameraMetadata` / `getCameraCharacteristics`、权限、真实 HAL、主机摄像头、C 回调实际调用
- 连续预览时钟、`addCallbackBuffer` 缓冲语义、其它未列出的 `Camera` / `Parameters` / Camera2 / NDK API

## android.sensors

传感器类型列表子集（**二十二字段**：`types` + 可选 **`dynamicTypes`** + 可选 **`dynamicDiscoverySupported`** + 可选 **`names`** + 可选 **`vendors`** + 可选 **`versions`** + 可选 **`stringTypes`** + 可选 **`maximumRanges`** + 可选 **`resolutions`** + 可选 **`powers`** + 可选 **`minDelaysMicros`** + 可选 **`maxDelaysMicros`** + 可选 **`fifoReservedEventCounts`** + 可选 **`fifoMaxEventCounts`** + 可选 **`wakeUpSensors`** + 可选 **`sensorIds`** + 可选 **`reportingModes`** + 可选 **`dynamicSensors`** + 可选 **`requiredPermissions`** + 可选 **`additionalInfoSupported`** + 可选 **`highestDirectReportRateLevels`** + 可选 **`directChannelTypesSupported`**）。路径为 `android.sensors`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidSensorsConfig`；JNI 接线在 `AbstractJni`：实例 `callObjectMethod` / `callObjectMethodV`（`getDefaultSensor(I)` / **`getDefaultSensor(IZ)`** / `getSensorList` / `getDynamicSensorList` / **`Sensor.getName`**） / **`Sensor.getVendor`** / **`Sensor.getStringType`** / **`Sensor.getRequiredPermission`**）、`callBooleanMethod` / `callBooleanMethodV`（`isDynamicSensorDiscoverySupported` / **`Sensor.isWakeUpSensor`** / **`Sensor.isDynamicSensor`** / **`Sensor.isAdditionalInfoSupported`** / **`Sensor.isDirectChannelTypeSupported`**）、`callIntMethod` / `callIntMethodV`（`getType` / **`Sensor.getVersion`** / **`Sensor.getMinDelay`** / **`Sensor.getMaxDelay`** / **`Sensor.getFifoReservedEventCount`** / **`Sensor.getFifoMaxEventCount`** / **`Sensor.getId`** / **`Sensor.getReportingMode`** / **`Sensor.getHighestDirectReportRateLevel`**）（VarArg 与 VaList）与 **`callFloatMethodV`**（**`Sensor.getMaximumRange`** / **`Sensor.getResolution`** / **`Sensor.getPower`**；无实例 `callFloatMethod` VarArg 路径）。另支持 `Context.SENSOR_SERVICE` 静态字段、`Application.getSystemService("sensor")` 与 **仅** `SensorManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService` 标记（类型 `android/hardware/SensorManager`；**不**要求 `android.sensors` 节点即可拿到服务标记；lookup **不发** sidecar）。**不**从其它配置或系统能力推断任何传感器类型；**不**从 `dynamicTypes` 推断 `dynamicDiscoverySupported`；**不**从 `types`/`dynamicTypes` 推断 `names`/`vendors`/`versions`/`stringTypes`/`maximumRanges`/`resolutions`/`powers`/`minDelaysMicros`/`maxDelaysMicros`/`fifoReservedEventCounts`/`fifoMaxEventCounts`，也**不**从这些映射反推列表；**不**在 `names`、`vendors`、`versions`、`stringTypes`、`maximumRanges`、`resolutions`、`powers`、`minDelaysMicros`、`maxDelaysMicros`、`fifoReservedEventCounts` 与 `fifoMaxEventCounts` 之间互相推导；**`resolutions` 绝不从 `maximumRanges` 或其他字段推导**；**`powers` 绝不从其它传感器字段推导**；**`minDelaysMicros` 绝不从其它传感器字段推导**；**`maxDelaysMicros` 绝不从 `minDelaysMicros` 或其他传感器字段推导，也不校验 `max>=min`**；**`fifoReservedEventCounts` 绝不从 `minDelaysMicros` / `maxDelaysMicros` / `fifoMaxEventCounts` 或其他传感器字段推导，也不校验与 `fifoMaxEventCounts` 的大小关系**；**`fifoMaxEventCounts` 绝不从 `fifoReservedEventCounts` / `minDelaysMicros` / `maxDelaysMicros` 或其他传感器字段推导，也不校验与 `fifoReservedEventCounts` 的大小关系**；**`wakeUpSensors` 绝不从 FIFO / names / 其它传感器字段推导，显式 `false` 与缺失不同**；**`sensorIds` / `reportingModes` / `dynamicSensors` / `requiredPermissions` / `additionalInfoSupported` / `highestDirectReportRateLevels` 互不推导，也绝不从 lists / FIFO / wake-up 推导**；**`dynamicSensors` 绝不从 `dynamicTypes` 推断**；**`requiredPermissions` 允许空串**；**`sensorIds` 允许 `-1`/`0`**；**`reportingModes` 仅 `0..3`**；**`highestDirectReportRateLevels` 仅 `0..3`，绝不从 `reportingModes` 推断**；**`directChannelTypesSupported` 仅通道类型 `1`/`2`，绝不从 `highestDirectReportRateLevels` 推断，也不物化直接通道对象**。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidSensorsConfigured()` | `getAndroidSensorsConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | `getDefaultSensor` / `getSensorList` / `getDynamicSensorList` / `isDynamicSensorDiscoverySupported` / `getType` / **`getName`** / **`getVendor`** / **`getVersion`** / **`getStringType`** / **`getMaximumRange`** / **`getResolution`** / **`getPower`** / **`getMinDelay`** / **`getMaxDelay`** / **`getFifoReservedEventCount`** / **`getFifoMaxEventCount`** / **`isWakeUpSensor`** / **`getId`** / **`getReportingMode`** / **`isDynamicSensor`** / **`getRequiredPermission`** / **`isAdditionalInfoSupported`** / **`getHighestDirectReportRateLevel`** / **`isDirectChannelTypeSupported`** 保持原先 `UnsupportedOperationException`，**不发** sidecar；`getSystemService("sensor")` / 类型化 `getSystemService(Class)` 仍可返回服务标记（lookup **不发** sidecar） |
| **显式 `{}`** | `true` | 非 null | 默认 `types=[]` / `dynamicTypes=[]`（空列表）、`dynamicDiscoverySupported=false`、`names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `powers` / `minDelaysMicros` / `maxDelaysMicros` / `fifoReservedEventCounts` / `fifoMaxEventCounts` / `wakeUpSensors` / `sensorIds` / `reportingModes` / `dynamicSensors` / `requiredPermissions` / `additionalInfoSupported` / `highestDirectReportRateLevels` / `directChannelTypesSupported` 未配置；`getDefaultSensor` 对任意 type 返回 Java `null`；`getSensorList` / `getDynamicSensorList` 返回空列表；`isDynamicSensorDiscoverySupported` 返回 `false`；均发事件；**`getName` / `getVendor` / `getVersion` / `getStringType` / `getMaximumRange` / `getResolution` / `getPower` / `getMinDelay` / `getMaxDelay` / `getFifoReservedEventCount` / `getFifoMaxEventCount` / `isWakeUpSensor` / `getId` / `getReportingMode` / `isDynamicSensor` / `getRequiredPermission` / `isAdditionalInfoSupported` / `getHighestDirectReportRateLevel` / `isDirectChannelTypeSupported` UOE 无事件** |
| **`types: []` / `dynamicTypes: []`** | `true` | 非 null | 显式空列表，行为同上 |
| **有类型** | `true` | 非 null | 列表内 type：`getDefaultSensor(I)` → VM 拥有 `ConfiguredSensor`；**`getDefaultSensor(IZ)` 与 `(I)` 同一接管门：列表内且显式 `wakeUpSensors` 等于参数 → ConfiguredSensor，否则 Java null（含未配 wake-up；不发明默认、不 UOE）**，`getSensorList` / `getDynamicSensorList` → 单元素列表；列表外：null / 空列表；`TYPE_ALL(-1)` → 对应字段的全量有序列表；`isDynamicSensorDiscoverySupported` 只读独立布尔；**`getName` 仅当该 type 在 `names` 中显式配置**；**`getVendor` 仅当该 type 在 `vendors` 中显式配置**；**`getVersion` 仅当该 type 在 `versions` 中显式配置**；**`getStringType` 仅当该 type 在 `stringTypes` 中显式配置**；**`getMaximumRange` 仅当该 type 在 `maximumRanges` 中显式配置**；**`getResolution` 仅当该 type 在 `resolutions` 中显式配置**；**`getPower` 仅当该 type 在 `powers` 中显式配置**；**`getMinDelay` 仅当该 type 在 `minDelaysMicros` 中显式配置**；**`getMaxDelay` 仅当该 type 在 `maxDelaysMicros` 中显式配置**；**`getFifoReservedEventCount` 仅当该 type 在 `fifoReservedEventCounts` 中显式配置**；**`getFifoMaxEventCount` 仅当该 type 在 `fifoMaxEventCounts` 中显式配置**；**`isWakeUpSensor` 仅当该 type 在 `wakeUpSensors` 中显式配置**；**`getId` 仅当该 type 在 `sensorIds` 中显式配置**；**`getReportingMode` 仅当该 type 在 `reportingModes` 中显式配置**；**`isDynamicSensor` 仅当该 type 在 `dynamicSensors` 中显式配置（不从 `dynamicTypes` 推断）**；**`getRequiredPermission` 仅当该 type 在 `requiredPermissions` 中显式配置**；**`isAdditionalInfoSupported` 仅当该 type 在 `additionalInfoSupported` 中显式配置**；**`getHighestDirectReportRateLevel` 仅当该 type 在 `highestDirectReportRateLevels` 中显式配置（不从 `reportingModes` 推断）**；**`isDirectChannelTypeSupported` 仅当该 type 在 `directChannelTypesSupported` 中显式配置（含 `[]`；不从 `highestDirectReportRateLevels` 推断）** |

### 字段说明（仅允许下列 22 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `types` | 否 | JSONArray；元素为精确 JSON Number 整数，唯一，`1..65535`，**保持 JSON 顺序**；允许显式 `[]` | 空列表 |
| `dynamicTypes` | 否 | 同上（独立校验，**可与 `types` 重叠**） | 空列表 |
| `dynamicDiscoverySupported` | 否 | 严格 JSON Boolean（拒绝 null / 字符串 / 数字 / 数组 / 对象） | `false`（**不**从 `dynamicTypes` 推断） |
| **`names`** | 否 | **JSONObject**：键为传感器类型的**严格十进制文本**（仅 `[1-9][0-9]*`，解析后 `1..65535`；禁止前后空白、`+`/`-`、小数、前导零，含 `"0"`/`"01"`）。项目里没有另一套传感器对象键语法；与 `linux.cpu` 十进制 ID（禁前导零、但允许字面 `0`）同族，因类型从 1 起故不保留 `0` 例外。值为非空 String，`1..256` UTF-16 code units，禁止 NUL/CR/LF。每个键必须已出现在 `types` 或 `dynamicTypes`；**不**反向把 type 写入列表。允许显式 `{}`。**不**与 `vendors` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `powers` / `minDelaysMicros` / `maxDelaysMicros` / `fifoReservedEventCounts` / `fifoMaxEventCounts` 互推 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`vendors`** | 否 | **JSONObject**：键值语法与 `names` 相同（严格十进制 `1..65535`，禁空白/正负号/小数/前导零；值非空 String `1..256` UTF-16，禁 NUL/CR/LF）。每个键必须已出现在 `types` 或 `dynamicTypes`；**不**反向把 type 写入列表。允许显式 `{}`。**不**从 `names` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `powers` / `minDelaysMicros` / `maxDelaysMicros` / `fifoReservedEventCounts` / `fifoMaxEventCounts` 推导，也**不**写入这些字段 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`versions`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同（严格十进制 `1..65535`，禁空白/正负号/小数/前导零）。值为**精确 JSON 整数** `0..Integer.MAX_VALUE`（拒绝浮点含 `1.0`、负数、越界、字符串、Boolean、null、数组、对象）。每个键必须已出现在 `types` 或 `dynamicTypes`；**不**反向把 type 写入列表。允许显式 `{}`。**不**从 `names` / `vendors` / `stringTypes` / `maximumRanges` / `resolutions` / `powers` / `minDelaysMicros` / `maxDelaysMicros` / `fifoReservedEventCounts` / `fifoMaxEventCounts` / lists 推导，也**不**写入这些字段。显式 `0` 与键缺失不同 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`stringTypes`** | 否 | **JSONObject**：键值语法与 `names` / `vendors` 相同（严格十进制 `1..65535`，禁空白/正负号/小数/前导零；值非空 String `1..256` UTF-16，禁 NUL/CR/LF）。每个键必须已出现在 `types` 或 `dynamicTypes`；**不**反向把 type 写入列表。允许显式 `{}`。**不**从 `names` / `vendors` / `versions` / `maximumRanges` / `resolutions` / `powers` / `minDelaysMicros` / `maxDelaysMicros` / `fifoReservedEventCounts` / `fifoMaxEventCounts` / lists 推导，也**不**写入这些字段 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`maximumRanges`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同（严格十进制 `1..65535`，禁空白/正负号/小数/前导零）。值为有限 JSON Number，按 Java `Number.floatValue()` 精确转为 `float`，范围 `0.0..Float.MAX_VALUE`（拒绝 null / Boolean / 字符串 / 数组 / 对象 / NaN / Infinity / 负数 / 超过 `Float.MAX_VALUE` / 转换后非有限）。每个键必须已出现在 `types` 或 `dynamicTypes`；**不**反向把 type 写入列表。允许显式 `{}`。**不**从 `names` / `vendors` / `versions` / `stringTypes` / `resolutions` / `powers` / `minDelaysMicros` / `maxDelaysMicros` / `fifoReservedEventCounts` / `fifoMaxEventCounts` / lists 推导，也**不**写入这些字段。显式 `0.0` 与键缺失不同 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`resolutions`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同（严格十进制 `1..65535`，禁空白/正负号/小数/前导零）。值为有限 JSON Number，按 Java `Number.floatValue()` 精确转为 `float`，范围 `0.0..Float.MAX_VALUE`（拒绝 null / Boolean / 字符串 / 数组 / 对象 / NaN / Infinity / 负数 / 超过 `Float.MAX_VALUE` / 转换后非有限）。每个键必须已出现在 `types` 或 `dynamicTypes`；**不**反向把 type 写入列表。允许显式 `{}`。**不**从 `names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `powers` / `minDelaysMicros` / `maxDelaysMicros` / `fifoReservedEventCounts` / `fifoMaxEventCounts` / lists 推导，也**不**写入这些字段。显式 `0.0` 与键缺失不同 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`powers`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同（严格十进制 `1..65535`，禁空白/正负号/小数/前导零）。值为有限 JSON Number，按 Java `Number.floatValue()` 精确转为 `float`，范围 `0.0..Float.MAX_VALUE`（拒绝 null / Boolean / 字符串 / 空串 / 数组 / 对象 / NaN / Infinity / 负数 / 超过 `Float.MAX_VALUE` / 转换后非有限）。每个键必须已出现在 `types` 或 `dynamicTypes`；**不**反向把 type 写入列表。允许显式 `{}`。**不**从 `names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `minDelaysMicros` / `maxDelaysMicros` / `fifoReservedEventCounts` / `fifoMaxEventCounts` / lists 推导，也**不**写入这些字段。显式 `0.0` 与键缺失不同 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`minDelaysMicros`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同（严格十进制 `1..65535`，禁空白/正负号/小数/前导零）。值为**精确 JSON 整数** `-1..Integer.MAX_VALUE`（`-1` 为一次性传感器 `getMinDelay()`；拒绝 `<-1`、浮点含 `1.0`、越界、字符串、Boolean、null、数组、对象）。每个键必须已出现在 `types` 或 `dynamicTypes`；**不**反向把 type 写入列表。允许显式 `{}`。**不**从 `names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `powers` / `maxDelaysMicros` / `fifoReservedEventCounts` / `fifoMaxEventCounts` / `reportingModes` / lists 推导，也**不**写入这些字段。显式 `-1`/`0` 与键缺失不同 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`maxDelaysMicros`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同（严格十进制 `1..65535`，禁空白/正负号/小数/前导零）。值为**精确 JSON 整数** `0..Integer.MAX_VALUE`（拒绝浮点含 `1.0`、负数、越界、字符串、Boolean、null、数组、对象）。每个键必须已出现在 `types` 或 `dynamicTypes`；**不**反向把 type 写入列表。允许显式 `{}`。**不**从 `names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `powers` / `minDelaysMicros` / `fifoReservedEventCounts` / `fifoMaxEventCounts` / lists 推导，也**不**写入这些字段。**不**校验 `max>=min`。显式 `0` 与键缺失不同 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`fifoReservedEventCounts`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同（严格十进制 `1..65535`，禁空白/正负号/小数/前导零）。值为**精确 JSON 整数** `0..Integer.MAX_VALUE`（拒绝浮点含 `1.0`、负数、越界、字符串、Boolean、null、数组、对象）。每个键必须已出现在 `types` 或 `dynamicTypes`；**不**反向把 type 写入列表。允许显式 `{}`。**不**从 `names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `powers` / `minDelaysMicros` / `maxDelaysMicros` / `fifoMaxEventCounts` / lists 推导，也**不**写入这些字段。**不**校验与 `fifoMaxEventCounts` 的大小关系。显式 `0` 与键缺失不同 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`fifoMaxEventCounts`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同（严格十进制 `1..65535`，禁空白/正负号/小数/前导零）。值为**精确 JSON 整数** `0..Integer.MAX_VALUE`（拒绝浮点含 `1.0`、负数、越界、字符串、Boolean、null、数组、对象）。每个键必须已出现在 `types` 或 `dynamicTypes`；**不**反向把 type 写入列表。允许显式 `{}`。**不**从 `names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `powers` / `minDelaysMicros` / `maxDelaysMicros` / `fifoReservedEventCounts` / lists 推导，也**不**写入这些字段。**不**校验与 `fifoReservedEventCounts` 的大小关系。显式 `0` 与键缺失不同 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`wakeUpSensors`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同（严格十进制 `1..65535`，禁空白/正负号/小数/前导零）。值为**严格 JSON Boolean**（拒绝 null / 字符串 / 数字 / 数组 / 对象）。每个键必须已出现在 `types` 或 `dynamicTypes`；**不**反向把 type 写入列表。允许显式 `{}`。**不**从 FIFO / names / 其它传感器字段 / lists 推导。显式 `false` 与键缺失不同 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`sensorIds`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同。值为**精确 JSON 整数** `-1..Integer.MAX_VALUE`（含 `-1`/`0`；拒绝 `<-1`、浮点含 `1.0`、字符串、Boolean、null）。每个键必须已出现在 `types` 或 `dynamicTypes`。允许显式 `{}`。**不**从其它传感器字段推导。显式 `0`/`-1` 与键缺失不同 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`reportingModes`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同。值为**精确 JSON 整数** `0..3`（`REPORTING_MODE_CONTINUOUS`/`ON_CHANGE`/`ONE_SHOT`/`SPECIAL_TRIGGER`；拒绝 `4`/`-1`/浮点）。每个键必须已出现在 `types` 或 `dynamicTypes`。允许显式 `{}`。**不**从其它传感器字段推导 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`dynamicSensors`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同。值为**严格 JSON Boolean**。每个键必须已出现在 `types` 或 `dynamicTypes`。允许显式 `{}`。**不**从 `dynamicTypes` 推断。显式 `false` 与键缺失不同 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`requiredPermissions`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同。值为 String `0..256` UTF-16（**允许空串**，禁 NUL/CR/LF）。每个键必须已出现在 `types` 或 `dynamicTypes`。允许显式 `{}`。**不**从其它传感器字段推导 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`additionalInfoSupported`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同。值为**严格 JSON Boolean**。每个键必须已出现在 `types` 或 `dynamicTypes`。允许显式 `{}`。**不**从其它传感器字段推导。显式 `false` 与键缺失不同 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`highestDirectReportRateLevels`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同。值为**精确 JSON 整数** `0..3`（`RATE_STOP`/`NORMAL`/`FAST`/`VERY_FAST`；拒绝 `4`/`-1`/浮点）。每个键必须已出现在 `types` 或 `dynamicTypes`。允许显式 `{}`。**不**从 `reportingModes` 或其它传感器字段推导 | 键缺失 → 未配置；显式 `{}` → 已配置空映射 |
| **`directChannelTypesSupported`** | 否 | **JSONObject**：键规则与 `names` / `vendors` 相同。值为 **JSONArray** of 唯一精确整数 `1`/`2`（`TYPE_MEMORY_FILE`/`TYPE_HARDWARE_BUFFER`；拒绝 `0`/`3`/`-1`/浮点/重复）。每个键必须已出现在 `types` 或 `dynamicTypes`。允许显式 `{}` 与每 type 显式 `[]`。**不**从 `highestDirectReportRateLevels` 推导。**不**物化 `SensorDirectChannel` / create/configure / 共享内存 | 键缺失 → 未配置；显式 `{}` → 已配置空映射；type 配 `[]` → 查询返回 `false` |

未知键（如 `extra`）在 parse 时抛 `IllegalArgumentException`，路径形如 `android.sensors.extra`。节点非对象时错误路径为 `android.sensors`。`types` / `dynamicTypes` 存在时拒绝 null / 非数组；元素拒绝 null / 非整数 / 小数 / 越界 / 重复，错误路径为 `android.sensors.types` / `android.sensors.types[i]` 或 `android.sensors.dynamicTypes` / `android.sensors.dynamicTypes[i]`（重复消息含 `duplicates sensor type N`）。`dynamicDiscoverySupported` 存在时拒绝非 Boolean，错误路径为 `android.sensors.dynamicDiscoverySupported`。`names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `powers` / `minDelaysMicros` / `maxDelaysMicros` / `fifoReservedEventCounts` / `fifoMaxEventCounts` / `wakeUpSensors` / `sensorIds` / `reportingModes` / `dynamicSensors` / `requiredPermissions` / `additionalInfoSupported` / `highestDirectReportRateLevels` 存在时拒绝非对象；非法键/类型不在 `types` 或 `dynamicTypes` 的错误路径为 `android.sensors.names.<key>` / `android.sensors.vendors.<key>` / `android.sensors.versions.<key>` / `android.sensors.stringTypes.<key>` / `android.sensors.maximumRanges.<key>` / `android.sensors.resolutions.<key>` / `android.sensors.powers.<key>` / `android.sensors.minDelaysMicros.<key>` / `android.sensors.maxDelaysMicros.<key>` / `android.sensors.fifoReservedEventCounts.<key>` / `android.sensors.fifoMaxEventCounts.<key>` / `android.sensors.wakeUpSensors.<key>` / `android.sensors.sensorIds.<key>` / `android.sensors.reportingModes.<key>` / `android.sensors.dynamicSensors.<key>` / `android.sensors.requiredPermissions.<key>` / `android.sensors.additionalInfoSupported.<key>` / `android.sensors.highestDirectReportRateLevels.<key>`（节点本身错误为 `android.sensors.names` / `android.sensors.vendors` / `android.sensors.versions` / `android.sensors.stringTypes` / `android.sensors.maximumRanges` / `android.sensors.resolutions` / `android.sensors.powers` / `android.sensors.minDelaysMicros` / `android.sensors.maxDelaysMicros` / `android.sensors.fifoReservedEventCounts` / `android.sensors.fifoMaxEventCounts` / `android.sensors.wakeUpSensors` / `android.sensors.sensorIds` / `android.sensors.reportingModes` / `android.sensors.dynamicSensors` / `android.sensors.requiredPermissions` / `android.sensors.additionalInfoSupported` / `android.sensors.highestDirectReportRateLevels`）。`names` / `vendors` / `stringTypes` 值拒绝非 String/空/控制字符；`requiredPermissions` 值允许空串，拒绝非 String / 超过 256 UTF-16 / NUL/CR/LF；`versions` / `maxDelaysMicros` / `fifoReservedEventCounts` / `fifoMaxEventCounts` 值拒绝非精确整数（含浮点、负数、越界、字符串）；`minDelaysMicros` 值为精确整数 `-1..Integer.MAX_VALUE`（含一次性传感器 `-1`，拒绝 `<-1`、浮点、越界、字符串）；`sensorIds` 值为精确整数 `-1..Integer.MAX_VALUE`（含 `-1`/`0`，拒绝 `<-1`、浮点含 `1.0`、字符串、Boolean、null）；`reportingModes` 值为精确整数 `0..3`（拒绝 `4`/`-1`/浮点）；`highestDirectReportRateLevels` 值为精确整数 `0..3`（拒绝 `4`/`-1`/浮点，**不**从 `reportingModes` 推断）；`wakeUpSensors` / `dynamicSensors` / `additionalInfoSupported` 值拒绝非严格 Boolean；`maximumRanges` / `resolutions` / `powers` 值拒绝 null / Boolean / 字符串 / 空串 / NaN / Infinity / 负数 / 超过 `Float.MAX_VALUE` / 转换后非有限。配置对象不可变，提供 `getTypes()` / `isTypesConfigured()` / `containsType(int)` / `getDynamicTypes()` / `isDynamicTypesConfigured()` / `containsDynamicType(int)` / `isDynamicDiscoverySupported()` / `isNamesConfigured()` / `isNameConfigured(int)` / `getName(int)` / `isVendorsConfigured()` / `isVendorConfigured(int)` / `getVendor(int)` / `isVersionsConfigured()` / `isVersionConfigured(int)` / `getVersion(int)` / `isStringTypesConfigured()` / `isStringTypeConfigured(int)` / `getStringType(int)` / `isMaximumRangesConfigured()` / `isMaximumRangeConfigured(int)` / `getMaximumRange(int)` / `isResolutionsConfigured()` / `isResolutionConfigured(int)` / `getResolution(int)` / `isPowersConfigured()` / `isPowerConfigured(int)` / `getPower(int)` / `isMinDelaysMicrosConfigured()` / `isMinDelayMicrosConfigured(int)` / `getMinDelayMicros(int)` / `isMaxDelaysMicrosConfigured()` / `isMaxDelayMicrosConfigured(int)` / `getMaxDelayMicros(int)` / `isFifoReservedEventCountsConfigured()` / `isFifoReservedEventCountConfigured(int)` / `getFifoReservedEventCount(int)` / `isFifoMaxEventCountsConfigured()` / `isFifoMaxEventCountConfigured(int)` / `getFifoMaxEventCount(int)` / `isWakeUpSensorsConfigured()` / `isWakeUpSensorConfigured(int)` / `getWakeUpSensor(int)` / `isIdsConfigured()` / `isIdConfigured(int)` / `getId(int)` / `isReportingModesConfigured()` / `isReportingModeConfigured(int)` / `getReportingMode(int)` / `isDynamicSensorsConfigured()` / `isDynamicSensorConfigured(int)` / `getDynamicSensor(int)` / `isRequiredPermissionsConfigured()` / `isRequiredPermissionConfigured(int)` / `getRequiredPermission(int)` / `isAdditionalInfoSupportedConfigured()` / `isAdditionalInfoSupportedConfigured(int)` / `getAdditionalInfoSupported(int)` / `isHighestDirectReportRateLevelsConfigured()` / `isHighestDirectReportRateLevelConfigured(int)` / `getHighestDirectReportRateLevel(int)`；`TraceEnvironmentConfig` 另提供 `isAndroidSensorNamesConfigured()` / `isAndroidSensorNameConfigured(int)` / `getAndroidSensorName(int)` / `isAndroidSensorVendorsConfigured()` / `isAndroidSensorVendorConfigured(int)` / `getAndroidSensorVendor(int)` / `isAndroidSensorVersionsConfigured()` / `isAndroidSensorVersionConfigured(int)` / `getAndroidSensorVersion(int)` / `isAndroidSensorStringTypesConfigured()` / `isAndroidSensorStringTypeConfigured(int)` / `getAndroidSensorStringType(int)` / `isAndroidSensorMaximumRangesConfigured()` / `isAndroidSensorMaximumRangeConfigured(int)` / `getAndroidSensorMaximumRange(int)` / `isAndroidSensorResolutionsConfigured()` / `isAndroidSensorResolutionConfigured(int)` / `getAndroidSensorResolution(int)` / `isAndroidSensorPowersConfigured()` / `isAndroidSensorPowerConfigured(int)` / `getAndroidSensorPower(int)` / `isAndroidSensorMinDelaysMicrosConfigured()` / `isAndroidSensorMinDelayMicrosConfigured(int)` / `getAndroidSensorMinDelayMicros(int)` / `isAndroidSensorMaxDelaysMicrosConfigured()` / `isAndroidSensorMaxDelayMicrosConfigured(int)` / `getAndroidSensorMaxDelayMicros(int)` / `isAndroidSensorFifoReservedEventCountsConfigured()` / `isAndroidSensorFifoReservedEventCountConfigured(int)` / `getAndroidSensorFifoReservedEventCount(int)` / `isAndroidSensorFifoMaxEventCountsConfigured()` / `isAndroidSensorFifoMaxEventCountConfigured(int)` / `getAndroidSensorFifoMaxEventCount(int)` / `isAndroidSensorWakeUpSensorsConfigured()` / `isAndroidSensorWakeUpSensorConfigured(int)` / `getAndroidSensorWakeUpSensor(int)` / `isAndroidSensorIdsConfigured()` / `isAndroidSensorIdConfigured(int)` / `getAndroidSensorId(int)` / `isAndroidSensorReportingModesConfigured()` / `isAndroidSensorReportingModeConfigured(int)` / `getAndroidSensorReportingMode(int)` / `isAndroidSensorDynamicSensorsConfigured()` / `isAndroidSensorDynamicSensorConfigured(int)` / `getAndroidSensorDynamicSensor(int)` / `isAndroidSensorRequiredPermissionsConfigured()` / `isAndroidSensorRequiredPermissionConfigured(int)` / `getAndroidSensorRequiredPermission(int)` / `isAndroidSensorAdditionalInfoSupportedConfigured()` / `isAndroidSensorAdditionalInfoSupportedConfigured(int)` / `getAndroidSensorAdditionalInfoSupported(int)` / `isAndroidSensorHighestDirectReportRateLevelsConfigured()` / `isAndroidSensorHighestDirectReportRateLevelConfigured(int)` / `getAndroidSensorHighestDirectReportRateLevel(int)`；**不**保留 JSONObject/JSONArray。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 接入 | 已配置 | 未配置 |
| --- | --- | --- | --- |
| `android/hardware/SensorManager->getDefaultSensor(I)Landroid/hardware/Sensor;` | `callObjectMethod`（VarArg）/ `callObjectMethodV`（VaList） | **仅** SystemService `SENSOR_SERVICE` 接收者：`types` 列表内 → 新 `ConfiguredSensor`；列表外 → Java `null` | `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/SensorManager->getDefaultSensor(IZ)Landroid/hardware/Sensor;` | `callObjectMethod`（VarArg）/ `callObjectMethodV`（VaList） | **仅** SystemService `SENSOR_SERVICE`：与 `(I)` 同一接管门。复用 `types`（**不**读 `dynamicTypes`）+ 显式 `wakeUpSensors`。列表内且配置 wake-up 等于参数 → 新 `ConfiguredSensor`；列表外 / 不匹配 / 该 type 未配 wake-up → Java `null`。**不**改 `(I)` 重载，**不**发明默认唤醒标记 | 普通/其它服务 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/SensorManager->getSensorList(I)Ljava/util/List;` | `callObjectMethod`（VarArg）/ `callObjectMethodV`（VaList） | **仅** SystemService `SENSOR_SERVICE`：`TYPE_ALL(-1)` → 新 `ArrayListObject`（新 VM 拥有 `ConfiguredSensor`，`types` JSON 顺序）；其它 type → 单元素列表或空列表（每次新建） | `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/SensorManager->getDynamicSensorList(I)Ljava/util/List;` | `callObjectMethod`（VarArg）/ `callObjectMethodV`（VaList） | **仅** SystemService `SENSOR_SERVICE`：`TYPE_ALL(-1)` → 新 `ArrayListObject`（新 VM 拥有 `ConfiguredSensor`，`dynamicTypes` JSON 顺序）；其它 type → 单元素列表或空列表（每次新建）；`dynamicTypes` 键缺失时仍命中并返回空列表 | `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/SensorManager->isDynamicSensorDiscoverySupported()Z` | `callBooleanMethod`（VarArg）/ `callBooleanMethodV`（VaList） | **仅** SystemService `SENSOR_SERVICE`：返回配置的 `dynamicDiscoverySupported`（缺键默认 `false`；**不**从 `dynamicTypes` 推断） | `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getType()I` | `callIntMethod`（VarArg）/ `callIntMethodV`（VaList） | **仅** 本 VM 拥有且绑定当前 `TraceEnvironmentConfig` 实例的存活 `ConfiguredSensor`：返回配置 type | 普通/外来/过期标记 / 节点缺失 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getName()Ljava/lang/String;` | `callObjectMethod`（VarArg）/ `callObjectMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor`（同一配置实例）**且** 该 type 在 `names` 中显式配置：返回新的同 VM `StringObject`；**不**按 type 生成默认名 | 列表内但未配 name / `names` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getVendor()Ljava/lang/String;` | `callObjectMethod`（VarArg）/ `callObjectMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor`（同一配置实例）**且** 该 type 在 `vendors` 中显式配置：返回新的同 VM `StringObject`；**不**按 type / `names` / 宿主生成默认厂商名 | 列表内但未配 vendor / `vendors` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getVersion()I` | `callIntMethod`（VarArg）/ `callIntMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor`（同一配置实例）**且** 该 type 在 `versions` 中显式配置：返回配置整数（含显式 `0` 与 `Integer.MAX_VALUE`）；**不**按 type / `names` / `vendors` / 宿主生成默认版本 | 列表内但未配 version / `versions` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getStringType()Ljava/lang/String;` | `callObjectMethod`（VarArg）/ `callObjectMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor`（同一配置实例）**且** 该 type 在 `stringTypes` 中显式配置：返回新的同 VM `StringObject`；**不**按 type / `names` / `vendors` / `versions` / 宿主生成默认 string type | 列表内但未配 string type / `stringTypes` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getMaximumRange()F` | **仅** `callFloatMethodV`（VaList；无实例 `callFloatMethod` VarArg） | **仅** 存活同 VM `ConfiguredSensor`（同一配置实例）**且** 该 type 在 `maximumRanges` 中显式配置：返回配置 float（含显式 `0.0` 与 `Float.MAX_VALUE`）；**不**按 type / `names` / `vendors` / `versions` / `stringTypes` / `resolutions` / `powers` / 宿主生成默认量程 | 列表内但未配量程 / `maximumRanges` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getResolution()F` | **仅** `callFloatMethodV`（VaList；无实例 `callFloatMethod` VarArg） | **仅** 存活同 VM `ConfiguredSensor`（同一配置实例）**且** 该 type 在 `resolutions` 中显式配置：返回配置 float（含显式 `0.0` 与 `Float.MAX_VALUE`）；**不**按 type / `names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `powers` / 宿主生成默认分辨率 | 列表内但未配分辨率 / `resolutions` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getPower()F` | **仅** `callFloatMethodV`（VaList；无实例 `callFloatMethod` VarArg） | **仅** 存活同 VM `ConfiguredSensor`（同一配置实例）**且** 该 type 在 `powers` 中显式配置：返回配置 float（含显式 `0.0` 与 `Float.MAX_VALUE`）；**不**按 type / `names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `minDelaysMicros` / `maxDelaysMicros` / 宿主生成默认功耗 | 列表内但未配功耗 / `powers` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getMinDelay()I` | `callIntMethod`（VarArg）/ `callIntMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor`（同一配置实例）**且** 该 type 在 `minDelaysMicros` 中显式配置：返回配置整数（含显式 `-1`、`0` 与 `Integer.MAX_VALUE`）；**不**按 type / `names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `powers` / `maxDelaysMicros` / `reportingModes` / 宿主生成默认最小延迟 | 列表内但未配最小延迟 / `minDelaysMicros` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getMaxDelay()I` | `callIntMethod`（VarArg）/ `callIntMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor`（同一配置实例）**且** 该 type 在 `maxDelaysMicros` 中显式配置：返回配置整数（含显式 `0` 与 `Integer.MAX_VALUE`）；**不**按 type / `names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `powers` / `minDelaysMicros` / `fifoReservedEventCounts` / 宿主生成默认最大延迟；**不**校验 `max>=min` | 列表内但未配最大延迟 / `maxDelaysMicros` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getFifoReservedEventCount()I` | `callIntMethod`（VarArg）/ `callIntMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor`（同一配置实例）**且** 该 type 在 `fifoReservedEventCounts` 中显式配置：返回配置整数（含显式 `0` 与 `Integer.MAX_VALUE`）；**不**按 type / `names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `powers` / `minDelaysMicros` / `maxDelaysMicros` / `fifoMaxEventCounts` / 宿主生成默认 FIFO 预留事件数；**不**校验与 `fifoMaxEventCounts` 的大小关系 | 列表内但未配 FIFO 预留事件数 / `fifoReservedEventCounts` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getFifoMaxEventCount()I` | `callIntMethod`（VarArg）/ `callIntMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor`（同一配置实例）**且** 该 type 在 `fifoMaxEventCounts` 中显式配置：返回配置整数（含显式 `0` 与 `Integer.MAX_VALUE`）；**不**按 type / `names` / `vendors` / `versions` / `stringTypes` / `maximumRanges` / `resolutions` / `powers` / `minDelaysMicros` / `maxDelaysMicros` / `fifoReservedEventCounts` / 宿主生成默认 FIFO 最大事件数；**不**校验与 `fifoReservedEventCounts` 的大小关系 | 列表内但未配 FIFO 最大事件数 / `fifoMaxEventCounts` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->isWakeUpSensor()Z` | `callBooleanMethod`（VarArg）/ `callBooleanMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor`（同一配置实例）**且** 该 type 在 `wakeUpSensors` 中显式配置：返回配置 Boolean（含显式 `false`）；**不**按 type / FIFO / names / 宿主生成默认唤醒标记 | 列表内但未配 wake-up / `wakeUpSensors` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getId()I` | `callIntMethod`（VarArg）/ `callIntMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor` **且** 该 type 在 `sensorIds` 中显式配置：返回配置整数（含 `-1`/`0`/`Integer.MAX_VALUE`）；**不**按 type 或其它字段生成默认 id | 列表内但未配 id / `sensorIds` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getReportingMode()I` | `callIntMethod`（VarArg）/ `callIntMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor` **且** 该 type 在 `reportingModes` 中显式配置：返回 `0..3`；**不**按 type 生成默认报告模式 | 列表内但未配 reportingMode / `reportingModes` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->isDynamicSensor()Z` | `callBooleanMethod`（VarArg）/ `callBooleanMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor` **且** 该 type 在 `dynamicSensors` 中显式配置：返回配置 Boolean；**不**从 `dynamicTypes` 推断 | 列表内但未配 / `dynamicTypes` 仅列出 / `dynamicSensors` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getRequiredPermission()Ljava/lang/String;` | `callObjectMethod`（VarArg）/ `callObjectMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor` **且** 该 type 在 `requiredPermissions` 中显式配置：返回新 `StringObject`（空串合法）；sidecar 仅 `permissionLength` | 列表内但未配 / `requiredPermissions` 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->isAdditionalInfoSupported()Z` | `callBooleanMethod`（VarArg）/ `callBooleanMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor` **且** 该 type 在 `additionalInfoSupported` 中显式配置：返回配置 Boolean（含显式 `false`） | 列表内但未配 / 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->getHighestDirectReportRateLevel()I` | `callIntMethod`（VarArg）/ `callIntMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor` **且** 该 type 在 `highestDirectReportRateLevels` 中显式配置：返回 `0..3`；**不**按 type/`reportingModes` 生成默认值 | 列表内但未配 / 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |
| `android/hardware/Sensor->isDirectChannelTypeSupported(I)Z` | `callBooleanMethod`（VarArg）/ `callBooleanMethodV`（VaList） | **仅** 存活同 VM `ConfiguredSensor` **且** 该 type 在 `directChannelTypesSupported` 中显式配置（含 `[]`）：返回集合是否包含 `sharedMemType`；未知通道类型返回 `false`；**不**从 `highestDirectReportRateLevels` 推断；**不**物化 `SensorDirectChannel` / create/configure / 共享内存 | 列表内但未配 / 省略或 `{}` / 普通/外来/过期标记 / 节点缺失 / 错误签名 → `UnsupportedOperationException`，**不发**事件 |

普通 `SensorManager`、其它 `SystemService`、错误签名均不命中传感器路径（UOE，无事件）。`getSensorList` / `getDynamicSensorList` / `getDefaultSensor` 从**当前**配置产生新标记，均可走 `Sensor.getType`；`getName` 另须该 type 的 `names` 显式条目；`getVendor` 另须该 type 的 `vendors` 显式条目；`getVersion` 另须该 type 的 `versions` 显式条目；`getStringType` 另须该 type 的 `stringTypes` 显式条目；`getMaximumRange` 另须该 type 的 `maximumRanges` 显式条目；`getResolution` 另须该 type 的 `resolutions` 显式条目；`getPower` 另须该 type 的 `powers` 显式条目；`getMinDelay` 另须该 type 的 `minDelaysMicros` 显式条目；`getMaxDelay` 另须该 type 的 `maxDelaysMicros` 显式条目；`getFifoReservedEventCount` 另须该 type 的 `fifoReservedEventCounts` 显式条目；`getFifoMaxEventCount` 另须该 type 的 `fifoMaxEventCounts` 显式条目；`isWakeUpSensor` 另须该 type 的 `wakeUpSensors` 显式条目；`getId` 另须该 type 的 `sensorIds` 显式条目；`getReportingMode` 另须该 type 的 `reportingModes` 显式条目；`isDynamicSensor` 另须该 type 的 `dynamicSensors` 显式条目（**不**从 `dynamicTypes` 推断）；`getRequiredPermission` 另须该 type 的 `requiredPermissions` 显式条目；`isAdditionalInfoSupported` 另须该 type 的 `additionalInfoSupported` 显式条目；`getHighestDirectReportRateLevel` 另须该 type 的 `highestDirectReportRateLevels` 显式条目（**不**从 `reportingModes` 推断）；`isDirectChannelTypeSupported` 另须该 type 的 `directChannelTypesSupported` 显式条目（含 `[]`；**不**从 `highestDirectReportRateLevels` 推断）。`ConfiguredSensor` 绑定创建时的 owner `BaseVM` 与当时的 `TraceEnvironmentConfig` 实例（引用相等，不用 equals）。`getType` / `getName` / `getVendor` / `getVersion` / `getStringType` / `getMaximumRange` / `getResolution` / `getPower` / `getMinDelay` / `getMaxDelay` / `getFifoReservedEventCount` / `getFifoMaxEventCount` / `isWakeUpSensor` / `getId` / `getReportingMode` / `isDynamicSensor` / `getRequiredPermission` / `isAdditionalInfoSupported` / `getHighestDirectReportRateLevel` / `isDirectChannelTypeSupported` 仅当 `TraceEnvironmentConfig.get(emulator)` 仍为该实例且 `android.sensors` 节点仍在时命中；`setEnvironmentConfig` 替换后旧标记即使新配置仍含同 type / 同名 / 同厂商值 / 同版本号 / 不同 string type 值 / 不同量程值 / 不同分辨率值 / 不同功耗值 / 不同最小延迟值 / 不同最大延迟值 / 不同 FIFO 预留事件数 / 不同 FIFO 最大事件数 / 不同 wake-up / 不同 id / 不同 reportingMode / 不同 dynamic / 不同 requiredPermission / 不同 additionalInfo / 不同 highestDirectReportRateLevel / 不同 directChannelTypes 也 UOE 无事件，须从当前 `SensorManager` 重新获取。

### Sidecar（旁路事件，仅命中时）

| 字段 | `getDefaultSensor` | `getDefaultSensor(IZ)` | `getSensorList` | `getDynamicSensorList` | `isDynamicSensorDiscoverySupported` | `getType` | `getName` | `getVendor` | `getVersion` | `getStringType` | `getMaximumRange` | `getResolution` | `getPower` | `getMinDelay` | `getMaxDelay` | `getFifoReservedEventCount` | `getFifoMaxEventCount` | `isWakeUpSensor` | `getId` | `getReportingMode` | `isDynamicSensor` | `getRequiredPermission` | `isAdditionalInfoSupported` | `getHighestDirectReportRateLevel` | `isDirectChannelTypeSupported` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `kind` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` | `android_sensor` |
| `api` | `SensorManager.getDefaultSensor` | `SensorManager.getDefaultSensor` | `SensorManager.getSensorList` | `SensorManager.getDynamicSensorList` | `SensorManager.isDynamicSensorDiscoverySupported` | `Sensor.getType` | `Sensor.getName` | `Sensor.getVendor` | `Sensor.getVersion` | `Sensor.getStringType` | `Sensor.getMaximumRange` | `Sensor.getResolution` | `Sensor.getPower` | `Sensor.getMinDelay` | `Sensor.getMaxDelay` | `Sensor.getFifoReservedEventCount` | `Sensor.getFifoMaxEventCount` | `Sensor.isWakeUpSensor` | `Sensor.getId` | `Sensor.getReportingMode` | `Sensor.isDynamicSensor` | `Sensor.getRequiredPermission` | `Sensor.isAdditionalInfoSupported` | `Sensor.getHighestDirectReportRateLevel` | `Sensor.isDirectChannelTypeSupported` |
| `value` | `sensorType=<input>,result=<input\|null>` | `sensorType=<input>,wakeUp=true\|false,result=<input\|null>` | `sensorType=<input>,count=<n>` | `sensorType=<input>,count=<n>` | `result=true\|false` | `sensorType=<type>` | `sensorType=<n>,nameLength=<n>`（**不**写原始名称） | `sensorType=<n>,vendorLength=<n>`（**不**写厂商原文） | `sensorType=<n>,version=<n>`（**只**含这两项，不泄露其它 JSON） | `sensorType=<n>,stringTypeLength=<n>`（**不**写原始 string type） | `sensorType=<n>,maximumRange=<float>`（**只**含这两项，不泄露其它 JSON） | `sensorType=<n>,resolution=<float>`（**只**含这两项，不泄露其它 JSON） | `sensorType=<n>,power=<float>`（**只**含这两项，不泄露其它 JSON） | `sensorType=<n>,minDelayMicros=<n>`（**只**含这两项，不泄露其它 JSON） | `sensorType=<n>,maxDelayMicros=<n>`（**只**含这两项，不泄露其它 JSON） | `sensorType=<n>,fifoReservedEventCount=<n>`（**只**含这两项，不泄露其它 JSON） | `sensorType=<n>,fifoMaxEventCount=<n>`（**只**含这两项，不泄露其它 JSON） | `sensorType=<n>,wakeUp=true\|false`（**只**含这两项，不泄露其它 JSON） | `sensorType=<n>,id=<n>`（**只**含这两项） | `sensorType=<n>,reportingMode=<n>`（**只**含这两项） | `sensorType=<n>,dynamic=true\|false`（**只**含这两项） | `sensorType=<n>,permissionLength=<n>`（**不**写权限原文） | `sensorType=<n>,additionalInfo=true\|false`（**只**含这两项） | `sensorType=<n>,highestDirectReportRateLevel=<n>`（**只**含这两项） | `sensorType=<n>,sharedMemType=<n>,result=true\|false`（**只**含这三项） |
| `source` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` |
| `note` | 中文简述（读取配置的默认传感器） | 中文简述（读取配置的默认传感器（按唤醒标记）） | 中文简述（读取配置的传感器列表） | 中文简述（读取配置的动态传感器列表） | 中文简述（读取配置的动态传感器发现支持标记） | 中文简述（读取配置的传感器类型） | 中文简述（读取配置的传感器名称） | 中文简述（读取配置的传感器厂商名称） | 中文简述（读取配置的传感器版本号） | 中文简述（读取配置的传感器字符串类型） | 中文简述（读取配置的传感器最大量程） | 中文简述（读取配置的传感器分辨率） | 中文简述（读取配置的传感器功耗） | 中文简述（读取配置的传感器最小延迟） | 中文简述（读取配置的传感器最大延迟） | 中文简述（读取配置的传感器 FIFO 预留事件数） | 中文简述（读取配置的传感器 FIFO 最大事件数） | 中文简述（读取配置的传感器唤醒标记） | 中文简述（读取配置的传感器标识） | 中文简述（读取配置的传感器报告模式） | 中文简述（读取配置的传感器动态标记） | 中文简述（读取配置的传感器所需权限） | 中文简述（读取配置的传感器附加信息支持标记） | 中文简述（读取配置的传感器最高直接报告速率等级） | 中文简述（读取配置的传感器直接通道类型支持） |

### 明确未实现

- 动态传感器发现回调（`DynamicSensorCallback`）、监听器
- 其余属性（**`getName` / `getVendor` / `getVersion` / `getStringType` / `getMaximumRange` / `getResolution` / `getPower` / `getMinDelay` / `getMaxDelay` / `getFifoReservedEventCount` / `getFifoMaxEventCount` / `isWakeUpSensor` / `getId` / `getReportingMode` / `isDynamicSensor` / `getRequiredPermission` / `isAdditionalInfoSupported` / `getHighestDirectReportRateLevel` / `isDirectChannelTypeSupported` 已实现**；**不含** `SensorDirectChannel` 对象 / create/configure direct channel / 共享内存 / `toString`；不按 type 生成默认名、默认厂商、默认版本、默认 string type、默认量程、默认分辨率、默认功耗、默认最小延迟、默认最大延迟、默认 FIFO 预留事件数、默认 FIFO 最大事件数、默认唤醒标记、默认 id、默认 reportingMode、默认 isDynamicSensor、默认 requiredPermission、默认 additionalInfo、默认 highestDirectReportRateLevel 或默认 directChannelTypes）
- `registerListener` / 回调 / 固定采样值 / `SensorEvent`
- Camera 或其它与 sensors 无关的 API

## android.audio

音频状态子集（**四标量字段** + 可选 **`properties` 映射** + 可选 **`streamVolumes` 数组**：`musicActive`、`speakerphoneOn`、`ringerMode`、`mode`、`properties`、`streamVolumes`，彼此独立、**不**互相推导）。路径为 `android.audio`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidAudioConfig` / `AndroidStreamVolumeConfig`；JNI 接线在 `AbstractJni`：实例 `callBooleanMethod` / `callBooleanMethodV`、`callIntMethod` / `callIntMethodV` 与 `callObjectMethod` / `callObjectMethodV`（VarArg 与 VaList）。另支持 `Context.AUDIO_SERVICE` 静态字段、`Application.getSystemService("audio")` 与 **仅** `AudioManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService` 标记（类型 `android/media/AudioManager`；**不**要求 `android.audio` 节点即可拿到服务标记；lookup **不发** sidecar）。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidAudioConfigured()` | `getAndroidAudioConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | `isMusicActive` / `isSpeakerphoneOn` / `getRingerMode` / `getMode` / `getProperty` / **`getStreamVolume`/`getStreamMaxVolume`/`getStreamMinVolume`** 保持 UOE，**不发** sidecar；`getSystemService("audio")` / 类型化 `getSystemService(Class)` 仍可返回服务标记（lookup **不发** sidecar） |
| **显式 `{}`** | `true` | 非 null | 默认 `musicActive=false`、`speakerphoneOn=false`、`ringerMode=2`、`mode=0`；`properties` / **`streamVolumes` 未配置** |
| **有字段** | `true` | 非 null | 已配键覆盖默认；`properties` / `streamVolumes` 仅当键存在时配置 |

### 字段说明（仅允许下列 6 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `musicActive` | 否 | 严格 JSON Boolean | `false` |
| `speakerphoneOn` | 否 | 严格 JSON Boolean | `false` |
| `ringerMode` | 否 | 严格 JSON 整型 Number，仅 `0`/`1`/`2`（`RINGER_MODE_SILENT` / `VIBRATE` / `NORMAL`） | `2` |
| `mode` | 否 | 严格 JSON 整型 Number，仅 `0`..`7`（`MODE_NORMAL` … `MODE_ASSISTANT_CONVERSATION`） | `0` |
| `properties` | 否 | **JSONObject**：键为**非空** String；值为 **String** 或 **JSON null** | 键缺失 → 未配置空 map；显式 `{}` → 已配置空 map |
| **`streamVolumes`** | 否 | **JSONArray** of 对象（见下表）；`[]` 合法已配置空列表；**独立**于其它 audio 字段 | 键缺失 → `isStreamVolumesConfigured()=false` |

#### `android.audio.streamVolumes[]`（每项仅允许下列 4 键；前 3 项必填，`minVolume` 可选）

| 字段 | 必填 | 类型与校验 |
| --- | --- | --- |
| `streamType` | **是** | 精确 JSON Number 整数，`>= 0`；数组内 **唯一** |
| `volume` | **是** | 精确 JSON Number 整数，`>= 0`；且 **`volume <= maxVolume`**（既有规则，不因 `minVolume` 改变） |
| `maxVolume` | **是** | 精确 JSON Number 整数，`>= 0` |
| **`minVolume`** | 否 | 精确 JSON Number 整数，`0..Integer.MAX_VALUE`；键缺失 → **未配置**（`isMinVolumeConfigured()=false`，**不**默认为 `0`）；显式时强制 **`minVolume <= volume <= maxVolume`** |

未知顶层键（如 `volume`、`extra`）在 parse 时抛 `IllegalArgumentException`，路径形如 `android.audio.volume`。节点非对象时错误路径为 `android.audio`。布尔/`ringerMode`/`mode`/`properties` 规则同前。`streamVolumes` 非数组、条目缺字段、`streamType` 重复、`volume > maxVolume`、显式 `minVolume > volume`、非整型/负数 → 路径 `android.audio.streamVolumes` / `android.audio.streamVolumes[i].<field>`。配置对象不可变，提供既有 getter + `isStreamVolumesConfigured()` / `getStreamVolumes()` / `findStreamVolume(streamType)`；每条目另有 **`isMinVolumeConfigured()` / `getMinVolume()`**；**不**保留 JSONObject。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 条件 | 已配置 | 未配置 / 隔离 |
| --- | --- | --- | --- |
| `AudioManager->isMusicActive()Z`（VarArg / VaList） | SystemService audio + 节点存在 | 返回固定 `musicActive` | 节点缺失 / 普通 / 其它服务：UOE 无事件 |
| `AudioManager->isSpeakerphoneOn()Z` | 同上 | 固定 `speakerphoneOn` | 同上 |
| `AudioManager->getRingerMode()I` | 同上 | 固定 `ringerMode` | 同上 |
| `AudioManager->getMode()I` | 同上 | 固定 `mode` | 同上 |
| `AudioManager->getProperty(Ljava/lang/String;)Ljava/lang/String;` | 同上；arg0 String | 见 properties 语义 | 同上 / 非 String：UOE |
| **`AudioManager->getStreamVolume(I)I`**（VarArg / VaList） | SystemService audio + 节点存在 + **`streamVolumes` 已配置** + arg0 **精确匹配** 某 `streamType` | 返回该条目 `volume` | **`streamVolumes` 缺失** / **空数组或无匹配 streamType** / 普通 AudioManager / 其它服务 / 错误签名：UOE，**不发**事件 |
| **`AudioManager->getStreamMaxVolume(I)I`**（VarArg / VaList） | 同上 | 返回该条目 `maxVolume` | 同上 UOE 无事件 |
| **`AudioManager->getStreamMinVolume(I)I`**（VarArg / VaList） | 同上，且该条目 **`minVolume` 显式** | 返回该条目 `minVolume`（含合法 `0`） | 同上，以及 **该条目 `minVolume` 缺失**：UOE，**不发**事件；**不**默认为 `0`，**不**读宿主音频值 |

### Sidecar（旁路事件，仅命中时）

| 字段 | 既有 getter | **`getStreamVolume` / `getStreamMaxVolume` / `getStreamMinVolume`** |
| --- | --- | --- |
| `kind` | `android_audio` | `android_audio` |
| `api` | `AudioManager.isMusicActive` 等 | **`AudioManager.getStreamVolume`** / **`AudioManager.getStreamMaxVolume`** / **`AudioManager.getStreamMinVolume`** |
| `value` | 既有 `field=…,result=…` / `key=…` | **`streamType=<n>,result=<n>`** |
| `source` | `json-config` | `json-config` |
| `note` | 中文简述 | 读取配置的流音量 / 流最大音量 / 流最小音量 |

**`getProperty` 事件策略：** 仅当 `properties` 中**存在该键**（含显式 null）时 emit；键缺失返回 Java `null` 且**不发** sidecar。

### 明确未实现

- `setMode` / `setRingerMode` / `setSpeakerphoneOn` / **`setStreamVolume`** 等 setter / 路由
- 设备列表（`getDevices` 等）、播放控制 / audio focus / 回调 / `getParameters`
- 其它 `AudioManager` API

## android.accessibility

无障碍子集：三布尔（`enabled`、`touchExplorationEnabled`、`highContrastTextEnabled`，彼此独立）+ 可选 **`services[]`**（`[]` 合法且表示已配置空列表）。路径为 `android.accessibility`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidAccessibilityConfig` / `AndroidAccessibilityServiceConfig`；JNI：布尔 `callBooleanMethod` / `V`；列表与 `getId` 的 `callObjectMethod` / `V`。既有 `getSystemService("accessibility")` 与 **仅** `AccessibilityManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService` 标记（类型 `android/view/accessibility/AccessibilityManager`；**不**要求 `android.accessibility` 节点即可拿到服务标记；lookup **不发** sidecar）。真正的无障碍读取（三布尔 / 服务列表）仍由 `android.accessibility` 门控。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidAccessibilityConfigured()` | 行为摘要 |
| --- | --- | --- |
| **节点缺失** | `false` | 三布尔 UOE 无事件；因 `services` 未配置：`getEnabledAccessibilityServiceList` **仍固定空列表**（历史、无 sidecar）；`getInstalledAccessibilityServiceList` UOE；`getSystemService("accessibility")` / 类型化 `getSystemService(AccessibilityManager.class)` 仍可返回服务标记（lookup **不发** sidecar） |
| **显式 `{}` 或仅布尔** | `true`；`isServicesConfigured()=false` | 三布尔默认 false；**`services` 键缺失**：`getEnabledAccessibilityServiceList` 仍固定空列表（无 sidecar）；`getInstalledAccessibilityServiceList` UOE |
| **`services` 存在（含 `[]`）** | `true`；`isServicesConfigured()=true` | `[]` 合法且表示已配置空列表；installed JSON 顺序全量；enabled 滤 `enabled=true`（见下） |

### 字段说明

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `enabled` | 否 | 严格 JSON Boolean | `false` |
| `touchExplorationEnabled` | 否 | 严格 JSON Boolean | `false` |
| `highContrastTextEnabled` | 否 | 严格 JSON Boolean | `false` |
| `services` | 否 | JSONArray；每项恰好 `{id,enabled}`；`id` 非空 String **全局唯一**；`enabled` 严格 Boolean | 键缺失：enabled 仍固定空列表、installed UOE；显式 `[]`：已配置空列表 |

未知键 / 重复 id / 错误类型 → parse 失败。**绝不**从服务列表推导三布尔。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 条件 | 行为 |
| --- | --- | --- |
| `Application`/`Context.getSystemService(Ljava/lang/Class;)`（**仅** `AccessibilityManager`；VarArg / VaList） | 第 0 参为 `DvmClass` 且类名为 `android/view/accessibility/AccessibilityManager` | 返回同一 `SystemService("accessibility")`；**不**要求节点；lookup **不发** sidecar |
| 三布尔 `isEnabled` 等 | SystemService accessibility + 节点 | 同前；sidecar 三布尔 |
| `getInstalledAccessibilityServiceList()Ljava/util/List;` | **`services` 已配置**（含 `[]`）+ SystemService accessibility | 新 ArrayListObject，同 VM marker，JSON 顺序全量；sidecar 仅 `count` |
| `getEnabledAccessibilityServiceList(I)Ljava/util/List;` | **`services` 已配置**（含 `[]`）+ SystemService accessibility | 滤 `enabled=true`；int 忽略；sidecar 仅 `count` |
| 同上 enabled 签名 | **`services` 键缺失** + SystemService accessibility | 仍返回固定空列表，无 sidecar；plain/其它 SystemService → UOE |
| 同上 installed 签名 | **`services` 键缺失** + SystemService accessibility | UOE 无事件 |
| `AccessibilityServiceInfo->getId()Ljava/lang/String;` | 仅存活同 VM marker | 配置 `id`；无 sidecar |

错误接收者 / 跨 VM / 非 marker：UOE 无事件。

### Sidecar

| 场景 | `api` | `value` |
| --- | --- | --- |
| 三布尔 | `AccessibilityManager.isEnabled` 等 | `field=…,result=…` |
| 配置列表成功 | `…getInstalledAccessibilityServiceList` / `…getEnabledAccessibilityServiceList` | `count=<n>`（仅 count） |

`kind=android_accessibility`，`source=json-config`。

### 明确未实现

- settings 联动、回调/监听、字幕、feedback 掩码语义、事件派发、服务生命周期
- 其它 `AccessibilityManager` / `AccessibilityServiceInfo` API

## android.clipboard

剪贴板主内容与主文本子集（**四字段**：`hasPrimaryClip`、可选 `primaryText`、可选 `primaryLabel`、可选 `timestampMillis`；**无**其它 JSON 扩展）。路径为 `android.clipboard`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidClipboardConfig`；JNI 接线在 `AbstractJni`：实例 `callBooleanMethod` / `callBooleanMethodV`、`callObjectMethod` / `callObjectMethodV`、`callIntMethod` / `callIntMethodV`、`callLongMethod` / `callLongMethodV`（VarArg 与 VaList）。另支持 `Context.CLIPBOARD_SERVICE` 静态字段、`Application.getSystemService("clipboard")` 与 **仅** `ClipboardManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService` 标记（类型 `android/content/ClipboardManager`；**不**要求 `android.clipboard` 节点即可拿到服务标记；lookup **不发** sidecar）。真正的剪贴板读取（`hasPrimaryClip` / `getPrimaryClip` 及 ClipData/ClipDescription）仍由 `android.clipboard` 门控。存活纯文本 `ConfiguredClipDataItem` 上 **`getText()`** 与 **`coerceToText(Context)`** 均只返回配置 `primaryText`（**不**读取、验证或模拟 `Context`；文本已直接存在，不解析 URI/Intent/HTML；**无** sidecar）。存活 `ConfiguredClipData` 上另提供纯文本 **ClipDescription** 元数据子集（`getDescription` / `getMimeTypeCount` / `getMimeType(0)` / `hasMimeType(String)` / **`getLabel()`** / **`getTimestamp()J`** / **`isStyledText()Z`**），**不**发 sidecar。`isStyledText()Z` 在存活纯文本 marker 上固定返回 `false`（`ConfiguredClipData` 仅为纯 `StringObject` 文本；**无** JSON 字段）；marker 外仍未实现。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidClipboardConfigured()` | `getAndroidClipboardConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | `hasPrimaryClip` / `getPrimaryClip` 等保持原先 `UnsupportedOperationException`，**不发** sidecar；`getSystemService("clipboard")` / 类型化 `getSystemService(ClipboardManager.class)` 仍可返回服务标记（lookup **不发** sidecar） |
| **显式 `{}`** | `true` | 非 null | 默认 `hasPrimaryClip=false`，无 `primaryText` / `primaryLabel` / `timestampMillis` |
| **有字段** | `true` | 非 null | 已配字段覆盖默认；见下表约束 |

### 字段说明（仅允许下列 4 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `hasPrimaryClip` | 否 | 严格 JSON Boolean | `false` |
| `primaryText` | 否 | JSON **String**（允许空串；**拒绝** null / 非 String）；**若键存在，则 `hasPrimaryClip` 必须显式为 `true`** | 键省略：`getPrimaryClip` 路径 notHandled |
| `primaryLabel` | 否 | JSON **String**（允许空串；**拒绝** null / 非 String）；**若键存在，则 `primaryText` 必须显式配置**（因而 `hasPrimaryClip` 亦须显式 `true`）；**不**从 `primaryText` 推导 | 键省略：存活 `ClipDescription.getLabel()` 返回 Java `null`（已处理结果，**不**记 sidecar） |
| `timestampMillis` | 否 | 精确整型 JSON **Number**，范围 `0..Long.MAX_VALUE`（**拒绝** null / 小数 / 非整数科学计数 / String / Boolean / 数组 / 对象 / 负数 / 溢出）；**若键存在，则 `primaryText` 必须显式配置**（因而 `hasPrimaryClip` 亦须显式 `true`）；timestamp-only 非法；**不**从 `primaryText` 或宿主时间推导 | 键省略：存活 `ClipDescription.getTimestamp()J` 返回稳定原语 `0`（Android 文档的未拷贝/无时间戳值；已处理，**不**记 sidecar） |

未知键（如 `text`、`extra`）在 parse 时抛 `IllegalArgumentException`，路径形如 `android.clipboard.text`。`hasPrimaryClip` 存在时拒绝 null / String / Number，错误路径为 `android.clipboard.hasPrimaryClip`。`primaryText` 非 String 或未满足 `hasPrimaryClip===true` 时失败，路径 `android.clipboard.primaryText`。`primaryLabel` 非 String、或未与显式 `primaryText` 同时出现（label-only）时失败，路径 `android.clipboard.primaryLabel`。`timestampMillis` 非精确整型 Number、越界、或未与显式 `primaryText` 同时出现（timestamp-only）时失败，路径 `android.clipboard.timestampMillis`。配置对象不可变；**不**保留 JSONObject。

### 对 `AbstractJni` 的精确影响

| 精确签名 / 字段 | 条件 | 已配置 | 未配置 / 非法接收者 |
| --- | --- | --- | --- |
| `android/content/Context->CLIPBOARD_SERVICE:Ljava/lang/String;` | 静态字段 | 返回 `"clipboard"`（与 `SystemService.CLIPBOARD_SERVICE` 相同） | 无条件支持 |
| `android/app/Application->getSystemService(Ljava/lang/String;)Ljava/lang/Object;`（服务名 `"clipboard"`） | 字符串服务名 | 返回 `SystemService` clipboard 标记（类型 `ClipboardManager`） | 无条件支持（**不**要求 `android.clipboard` 节点） |
| `Application`/`Context.getSystemService(Ljava/lang/Class;)`（**仅** `ClipboardManager`；VarArg / VaList） | 第 0 参为 `DvmClass` 且类名为 `android/content/ClipboardManager` | 返回同一 `SystemService("clipboard")`；lookup **不发** sidecar | **不**要求 `android.clipboard` 节点；仍返回标记；lookup **不发** sidecar；随后 `hasPrimaryClip` / `getPrimaryClip` 仍 UOE |
| `android/content/ClipboardManager->hasPrimaryClip()Z`（VarArg / VaList） | 接收者**必须**为 `SystemService` 且 value=`"clipboard"`，且节点存在 | 返回 `hasPrimaryClip`（键缺省为 `false`） | 节点缺失 / 普通 `ClipboardManager` / 其它 `SystemService` / 错误签名：UOE，**不发**事件 |
| `android/content/ClipboardManager->getPrimaryClip()Landroid/content/ClipData;`（VarArg / VaList） | 同上 + **显式配置** `primaryText` | 返回同 VM **ConfiguredClipData** marker；sidecar（见下） | 无 `primaryText` / plain/stale/cross-VM / 节点缺失：UOE，**不发**事件 |
| `android/content/ClipData->getItemCount()I` | **仅**存活 ConfiguredClipData | 固定 `1`；**无** sidecar | plain/stale/cross-VM：UOE 无事件 |
| `android/content/ClipData->getItemAt(I)Landroid/content/ClipData$Item;` | 存活 ClipData 且 **index 恰为 0** | 新 ConfiguredClipDataItem；**无** sidecar | 其它 index / plain/stale：UOE 无事件 |
| `android/content/ClipData$Item->getText()Ljava/lang/CharSequence;` | **仅**存活 ConfiguredClipDataItem | 配置 `primaryText` 的 `StringObject`；**无** sidecar | plain/stale/cross-VM：UOE 无事件 |
| `android/content/ClipData$Item->coerceToText(Landroid/content/Context;)Ljava/lang/CharSequence;` | **仅**存活 ConfiguredClipDataItem | 与 `getText` 相同：配置 `primaryText` 的 `StringObject`；**不**读取、验证或模拟 `Context`（文本已直接存在，不解析 URI/Intent/HTML）；**无** sidecar | plain/stale/cross-VM：UOE 无事件 |
| `android/content/ClipData->getDescription()Landroid/content/ClipDescription;` | **仅**存活 ConfiguredClipData | 每次新同 VM **ConfiguredClipDescription**（已确立纯文本子集）；**无** sidecar | 无 `primaryText` / plain/stale/cross-VM / 错误签名或接收者：UOE 无事件 |
| `android/content/ClipDescription->getMimeTypeCount()I` | **仅**存活 ConfiguredClipDescription | 固定 `1`；**无** sidecar | plain/stale/cross-VM / 错误签名或接收者：UOE 无事件 |
| `android/content/ClipDescription->getMimeType(I)Ljava/lang/String;` | 存活 ClipDescription 且 **index 恰为 0** | 新 `StringObject` `"text/plain"`；**无** sidecar | 其它 index / plain/stale/cross-VM / 错误签名或接收者：UOE 无事件 |
| `android/content/ClipDescription->hasMimeType(Ljava/lang/String;)Z` | **仅**存活 ConfiguredClipDescription 且参数为非 null `String` | 仅 `"text/plain"` / `"text/*"` / `"*/*"` → true；其它非 null String → false；**无** sidecar | null/非 String / plain/stale/cross-VM / 错误签名或接收者：UOE 无事件 |
| `android/content/ClipDescription->getLabel()Ljava/lang/CharSequence;` | **仅**存活 ConfiguredClipDescription | 键已配：新同 VM `StringObject`（可为空串）；键省略：Java `null`（已处理）；**不**从 `primaryText` 推导；**无** sidecar | plain/stale/cross-VM / 无 `primaryText` / 错误签名或接收者：UOE 无事件 |
| `android/content/ClipDescription->getTimestamp()J` | **仅**存活 ConfiguredClipDescription | 键已配：返回配置 long（含 `0` 与 `Long.MAX_VALUE`）；键省略：稳定原语 `0`（已处理）；**不**从 `primaryText` 或宿主时间推导；**无** sidecar | plain/stale/cross-VM / 无 `primaryText` / 错误签名或接收者：UOE 无事件 |
| `android/content/ClipDescription->isStyledText()Z` | **仅**存活 ConfiguredClipDescription | 固定 `false`（纯文本 `StringObject` 子集；**无** JSON 字段）；**无** sidecar | plain/stale/cross-VM / 无 `primaryText` / 错误签名或接收者：UOE 无事件 |

### Sidecar（旁路事件）

| 命中 | `kind` | `api` | `value` | `source` |
| --- | --- | --- | --- | --- |
| `hasPrimaryClip` | `android_clipboard` | `ClipboardManager.hasPrimaryClip` | `field=hasPrimaryClip,result=true\|false` | `json-config` |
| 成功 `getPrimaryClip` | `android_clipboard` | `ClipboardManager.getPrimaryClip` | `hasPrimaryClip=true,textLength=<Java UTF-16 长度>`（**绝不**写原文） | `json-config` |

`getItemCount` / `getItemAt` / `getText` / **`coerceToText`** / **`getDescription` / `getMimeTypeCount` / `getMimeType` / `hasMimeType` / `getLabel` / `getTimestamp` / `isStyledText`** **不**发 sidecar（不暴露 clip 原文、label 原文或 timestamp 推导）。`getPrimaryClip` sidecar 语义不变。

### 明确未实现

- `setPrimaryClip` / 写入剪贴板
- 剪贴板监听（`OnPrimaryClipChangedListener`）
- extras / styled spans / `coerceToHtmlText` / URI / Intent / HTML / 多 MIME / 多 item / 通用 MIME 解析 / 静态 `compareMimeTypes` / classification（**`isStyledText()Z` 仅存活纯文本 marker 上返回 `false`，marker 外仍未实现**；纯文本 `coerceToText(Context)` 已实现，见上表）
- 其它 `ClipboardManager` / 广义 `ClipData` 行为

## android.location

定位总开关、提供者（含 `hasProvider` / `getProvider` / `LocationProvider.getName` 子集）、可选 **`providerCapabilities`**（目前为 `LocationProvider.requiresNetwork()Z`、`LocationProvider.requiresSatellite()Z`、`LocationProvider.requiresCell()Z`、`LocationProvider.hasMonetaryCost()Z`、`LocationProvider.supportsAltitude()Z`、`LocationProvider.supportsSpeed()Z`、`LocationProvider.supportsBearing()Z`、**`LocationProvider.meetsCriteria(Landroid/location/Criteria;)Z`**（仅固定 JSON 布尔标记，**不**解析/存储/推导 `Criteria` 字段）、**`LocationProvider.getAccuracy()I`**（**不是** `Location.getAccuracy()F`）与 **`LocationProvider.getPowerRequirement()I`**）与最后已知位置子集。路径为 `android.location`（可选 **JSONObject**，允许键：`enabled`、`providers`、`lastKnownLocations`、`providerCapabilities`）。实现类：`TraceEnvironmentConfig.AndroidLocationConfig`（`enabled`）、`AndroidLocationProvidersConfig`（`providers` 下三布尔）、`AndroidLastKnownLocationConfig`（`lastKnownLocations[]` 每项）、`AndroidLocationProviderCapabilitiesConfig` / `AndroidLocationProviderCapabilityEntry`（`providerCapabilities`）。JNI 接线在 `AbstractJni`：实例 `callBooleanMethod` / `callBooleanMethodV`、`callObjectMethod` / `callObjectMethodV`、`callLongMethod` / `callLongMethodV`、`callDoubleMethod`（仅 VarArg；`VaList` 继承 `VarArg`）、`callFloatMethodV`（`Location.getAccuracy()F` 等精度仅 VaList 浮点路径）、**`callIntMethod` / `callIntMethodV`**（`LocationProvider.getAccuracy()I` 与 `LocationProvider.getPowerRequirement()I`）。`Context.LOCATION_SERVICE` / `Application.getSystemService("location")` 已有 `SystemService` 标记；另支持 **仅** `LocationManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService`（`location` → `LocationManager`）（类型 `android/location/LocationManager`；**不**要求 `android.location` 节点即可拿到服务标记；lookup **不发** sidecar）。

**四键独立：** `enabled` **不**从 `providers` / `lastKnownLocations` / `providerCapabilities` 推导；`providers`、`lastKnownLocations` 与 `providerCapabilities` 彼此独立，也**不**从 time 配置推导。`providerCapabilities` **不**改变既有 `providers` / `lastKnownLocations` / `enabled` 行为，也**不能**单独创建 `LocationProvider`（仍须经 `LocationManager.getProvider` 与显式 `providers` 键）。

### 节点缺失 vs 显式空对象

| 状态 | `isAndroidLocationConfigured()` | providers 配置 | lastKnownLocations 配置 | providerCapabilities 配置 | 行为摘要 |
| --- | --- | --- | --- | --- | --- |
| **`android.location` 缺失** | `false` | `false` | `false` | `false` | `isLocationEnabled` / providers / `getLastKnownLocation` / `requiresNetwork` / `requiresSatellite` / `requiresCell` / `hasMonetaryCost` / `supportsAltitude` / `supportsSpeed` / `supportsBearing` / **`meetsCriteria`** / **`getAccuracy()I`** / **`getPowerRequirement()I`** 均为 UOE，**不发** sidecar |
| **显式 `location: {}`** | `true`（`enabled=false`） | `false` | `false` | `false` | `isLocationEnabled` 默认 false；providers / lastKnown / capabilities UOE |
| **仅 `enabled`** | `true` | `false` | `false` | `false` | 同上；providers / lastKnown / capabilities 未配置 |
| **仅 / 含 `providers`** | `true` | `true` | 视键 | 视键 | providers 列表/查询/`hasProvider`/`getProvider` 按既有语义 |
| **显式 `providers: {}`** | `true` | `true` | 视键 | 视键 | providers 默认全 false；列表空；`hasProvider` 均 false；`getProvider` 均 Java `null` |
| **仅 / 含 `lastKnownLocations`** | `true` | 视键 | `true` | 视键 | `getLastKnownLocation` 按数组精确 provider 匹配 |
| **显式 `lastKnownLocations: []`** | `true` | 视键 | `true` | 视键 | 有配置但无条目 → 任意 provider 返回 Java `null` |
| **仅 / 含 `providerCapabilities`** | `true` | 视键 | 视键 | `true` | 能力查询按条目；**不**单独创建 LocationProvider |
| **显式 `providerCapabilities: {}`** | `true` | 视键 | 视键 | `true` | 已配置但无条目；`requiresNetwork` / `requiresSatellite` / `requiresCell` / `hasMonetaryCost` / `supportsAltitude` / `supportsSpeed` / `supportsBearing` / **`meetsCriteria`** / **`getAccuracy()I`** / **`getPowerRequirement()I`** 均为 UOE |

### 字段说明

#### `android.location`（仅允许 `enabled` | `providers` | `lastKnownLocations` | `providerCapabilities`）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `enabled` | 否 | 严格 JSON Boolean | `false` |
| `providers` | 否 | JSONObject（见下表） | 键缺失 → providers **未配置** |
| `lastKnownLocations` | 否 | JSONArray of 对象（见下表） | 键缺失 → lastKnown **未配置** |
| **`providerCapabilities`** | 否 | JSONObject（见下表） | 键缺失 → capabilities **未配置** |

#### `android.location.providers`（仅允许下列 3 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `gps` | 否 | 严格 JSON Boolean | `false` |
| `network` | 否 | 严格 JSON Boolean | `false` |
| `passive` | 否 | 严格 JSON Boolean | `false` |

#### `android.location.providerCapabilities`（仅允许 `gps` | `network` | `passive`）

每个键的值必须为 **JSONObject**。键缺失表示该 provider **无条目**（不是默认能力）。显式空对象 `{}` 表示该条目已配置但字段均未配置。

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `gps` | 否 | JSONObject（见下表） | 键缺失 → 该 provider **无条目** |
| `network` | 否 | JSONObject（见下表） | 同上 |
| `passive` | 否 | JSONObject（见下表） | 同上 |

#### 每个 `android.location.providerCapabilities.<provider>`（仅允许 `requiresNetwork` | `requiresSatellite` | `requiresCell` | `hasMonetaryCost` | `supportsAltitude` | `supportsSpeed` | `supportsBearing` | `meetsCriteria` | `accuracy` | `powerRequirement`）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| **`requiresNetwork`** | 否 | 严格 JSON Boolean | 键缺失 → 该字段 **未配置**；`LocationProvider.requiresNetwork()Z` 保持 UOE（配置视图 `isRequiresNetwork()` 缺省 `false` 且 `isRequiresNetworkConfigured()=false`） |
| **`requiresSatellite`** | 否 | 严格 JSON Boolean | 键缺失 → 该字段 **未配置**；`LocationProvider.requiresSatellite()Z` 保持 UOE（配置视图 `isRequiresSatellite()` 缺省 `false` 且 `isRequiresSatelliteConfigured()=false`）。与 `requiresNetwork` **独立** presence，互不推导 |
| **`requiresCell`** | 否 | 严格 JSON Boolean | 键缺失 → 该字段 **未配置**；`LocationProvider.requiresCell()Z` 保持 UOE（配置视图 `isRequiresCell()` 缺省 `false` 且 `isRequiresCellConfigured()=false`）。与 `requiresNetwork` / `requiresSatellite` **独立** presence，互不推导 |
| **`hasMonetaryCost`** | 否 | 严格 JSON Boolean | 键缺失 → 该字段 **未配置**；`LocationProvider.hasMonetaryCost()Z` 保持 UOE（配置视图 `isHasMonetaryCost()` 缺省 `false` 且 `isHasMonetaryCostConfigured()=false`）。与 `requiresNetwork` / `requiresSatellite` / `requiresCell` **独立** presence，互不推导 |
| **`supportsAltitude`** | 否 | 严格 JSON Boolean | 键缺失 → 该字段 **未配置**；`LocationProvider.supportsAltitude()Z` 保持 UOE（配置视图 `isSupportsAltitude()` 缺省 `false` 且 `isSupportsAltitudeConfigured()=false`）。与 `requiresNetwork` / `requiresSatellite` / `requiresCell` / `hasMonetaryCost` **独立** presence，互不推导 |
| **`supportsSpeed`** | 否 | 严格 JSON Boolean | 键缺失 → 该字段 **未配置**；`LocationProvider.supportsSpeed()Z` 保持 UOE（配置视图 `isSupportsSpeed()` 缺省 `false` 且 `isSupportsSpeedConfigured()=false`）。与 `requiresNetwork` / `requiresSatellite` / `requiresCell` / `hasMonetaryCost` / `supportsAltitude` **独立** presence，互不推导 |
| **`supportsBearing`** | 否 | 严格 JSON Boolean | 键缺失 → 该字段 **未配置**；`LocationProvider.supportsBearing()Z` 保持 UOE（配置视图 `isSupportsBearing()` 缺省 `false` 且 `isSupportsBearingConfigured()=false`）。与 `requiresNetwork` / `requiresSatellite` / `requiresCell` / `hasMonetaryCost` / `supportsAltitude` / `supportsSpeed` **独立** presence，互不推导 |
| **`meetsCriteria`** | 否 | 严格 JSON Boolean（**固定布尔标记**） | 键缺失 → 该字段 **未配置**；`LocationProvider.meetsCriteria(Landroid/location/Criteria;)Z` 保持 UOE（配置视图 `isMeetsCriteria()` 缺省 `false` 且 `isMeetsCriteriaConfigured()=false`）。与其它 Boolean 字段及 **`accuracy`** / **`powerRequirement`** **独立** presence，互不推导。**限制：** 真实 Android 会按 `Criteria` 多字段计算匹配；本实现**只返回该 JSON 布尔**，**不**解析、存储或推导 `Criteria` 内部字段。JNI 参数须为非 null、对象类型精确为 `android/location/Criteria`，**且该 `DvmClass` 所属 VM 必须是当前 BaseVM**（`objectType.vm == 当前 vm`）；其它 `DvmObject` / null / 错误类型 / **跨 VM 同名 `Criteria`** → UOE |
| **`accuracy`** | 否 | 严格 JSON 整数，仅 `ProviderProperties.ACCURACY_FINE=1` 或 `ACCURACY_COARSE=2`（拒 null / Boolean / String / 小数 / NaN / Infinity / `0` / `3` 等） | 键缺失 → 该字段 **未配置**；`LocationProvider.getAccuracy()I` 保持 UOE（配置视图 `getAccuracy()` 缺省 `0` 且 `isAccuracyConfigured()=false`）。与八个 Boolean 字段及 **`powerRequirement`** **独立** presence，互不推导。**不是** `lastKnownLocations[].accuracyMeters`，也**不是** `Location.getAccuracy()F`（位置精度米数） |
| **`powerRequirement`** | 否 | 严格 JSON 整数，仅 `ProviderProperties.POWER_USAGE_LOW=1`、`POWER_USAGE_MEDIUM=2` 或 `POWER_USAGE_HIGH=3`（拒 null / Boolean / String / 小数 / NaN / Infinity / `0` / `4` 等） | 键缺失 → 该字段 **未配置**；`LocationProvider.getPowerRequirement()I` 保持 UOE（配置视图 `getPowerRequirement()` 缺省 `0` 且 `isPowerRequirementConfigured()=false`）。与八个 Boolean 字段及 **`accuracy`** **独立** presence，互不推导 |

`android.location` 非对象、`enabled`/`providers`/`lastKnownLocations`/`providerCapabilities` 类型错误、未知键、条目缺必填、provider 重复、坐标越界、`providerCapabilities` 未知 provider / 非对象 entry / 未知字段 / 非 Boolean `requiresNetwork` / 非 Boolean `requiresSatellite` / 非 Boolean `requiresCell` / 非 Boolean `hasMonetaryCost` / 非 Boolean `supportsAltitude` / 非 Boolean `supportsSpeed` / 非 Boolean `supportsBearing` / 非 Boolean `meetsCriteria` / 非 `1` 或 `2` 的 `accuracy` / 非 `1`、`2` 或 `3` 的 `powerRequirement` 时 parse 抛 `IllegalArgumentException`，错误路径为 `android.location`、`android.location.<key>`、`android.location.lastKnownLocations`、`android.location.lastKnownLocations[i].<field>`、`android.location.providerCapabilities`、`android.location.providerCapabilities.<provider>`、`android.location.providerCapabilities.<provider>.<field>`。允许键会写入错误消息；`accuracy` 非法值消息含该路径与 `1 or 2`；`powerRequirement` 非法值消息含该路径与 `1, 2, or 3`。配置对象不可变；列表不可修改；**不**保留 JSONObject/JSONArray。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 条件 | 已配置 | 未配置 / 非法 |
| --- | --- | --- | --- |
| `Application`/`Context->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;`（VarArg + VaList） | 第 0 参为 **`DvmClass`** 且类名为 `android/location/LocationManager` | 返回与 `"location"` 字符串路径**同一** `SystemService` 标记 | **不**要求 `android.location` 节点；null / 非 DvmClass / 其它 Class → UOE 无 sidecar；lookup **不发** sidecar |
| `LocationManager->isLocationEnabled()Z`（VarArg + VaList） | SystemService location + **`android.location` 存在** | 返回 `enabled`（缺省 false） | 节点缺失 / 普通 / 其它：UOE 无事件 |
| `LocationManager->isProviderEnabled(Ljava/lang/String;)Z` | location SystemService + **`providers` 存在**；arg0 恰好 gps/network/passive | 返回对应配置/默认 false | 缺失 / 普通 / null / 非 String / 未知：UOE 无事件 |
| `LocationManager->hasProvider(Ljava/lang/String;)Z`（VarArg + VaList） | location SystemService + **`providers` 存在**；arg0 非 null String | **显式**键（含 false/disabled）→ true；省略已知键或未知字符串 → false | 键缺失 / 普通 LocationManager / 其它服务 / null / 非 String：UOE 无事件 |
| `LocationManager->getAllProviders()Ljava/util/List;` | location SystemService + `providers` | 显式键列表（含 false）；`{}`→空 | 同上 UOE |
| `LocationManager->getProviders(Z)Ljava/util/List;` | 同上 | `enabledOnly` 过滤 | 同上 UOE |
| `LocationManager->getProvider(Ljava/lang/String;)Landroid/location/LocationProvider;` | location SystemService + **`providers` 存在**；arg0 非 null String | **显式**键（含 false/disabled）→ 每次新同 VM 私有 `ConfiguredLocationProvider`；省略已知键或未知键 → Java `null` | 键缺失 / 普通 LocationManager / 其它服务 / null / 非 String：UOE 无事件；**不**因 `providerCapabilities` 单独创建标记 |
| `LocationProvider->getName()Ljava/lang/String;` | 存活同 VM `ConfiguredLocationProvider` | 配置键名 | 普通 LocationProvider / 跨 VM / 过期：UOE 无事件 |
| **`LocationProvider->requiresNetwork()Z`**（VarArg + VaList） | 存活同 VM `ConfiguredLocationProvider`（同 BaseVM、同当前 `TraceEnvironmentConfig`、provider 仍为显式 `providers` 键）**且**该 provider 的 `requiresNetwork` **显式**配置 | 配置布尔（`0`/`1`） | 缺节点 / 缺条目 / 缺字段 / 普通 LocationProvider / 跨 VM / 过期 marker：UOE **无** sidecar |
| **`LocationProvider->requiresSatellite()Z`**（VarArg + VaList） | 存活同 VM `ConfiguredLocationProvider`（同 BaseVM、同当前 `TraceEnvironmentConfig`、provider 仍为显式 `providers` 键）**且**该 provider 的 `requiresSatellite` **显式**配置 | 配置布尔（`0`/`1`） | 缺节点 / 缺条目 / 缺字段 / 普通 LocationProvider / 跨 VM / 过期 marker：UOE **无** sidecar |
| **`LocationProvider->requiresCell()Z`**（VarArg + VaList） | 存活同 VM `ConfiguredLocationProvider`（同 BaseVM、同当前 `TraceEnvironmentConfig`、provider 仍为显式 `providers` 键）**且**该 provider 的 `requiresCell` **显式**配置 | 配置布尔（`0`/`1`） | 缺节点 / 缺条目 / 缺字段 / 普通 LocationProvider / 跨 VM / 过期 marker：UOE **无** sidecar |
| **`LocationProvider->hasMonetaryCost()Z`**（VarArg + VaList） | 存活同 VM `ConfiguredLocationProvider`（同 BaseVM、同当前 `TraceEnvironmentConfig`、provider 仍为显式 `providers` 键）**且**该 provider 的 `hasMonetaryCost` **显式**配置 | 配置布尔（`0`/`1`） | 缺节点 / 缺条目 / 缺字段 / 普通 LocationProvider / 跨 VM / 过期 marker：UOE **无** sidecar |
| **`LocationProvider->supportsAltitude()Z`**（VarArg + VaList） | 存活同 VM `ConfiguredLocationProvider`（同 BaseVM、同当前 `TraceEnvironmentConfig`、provider 仍为显式 `providers` 键）**且**该 provider 的 `supportsAltitude` **显式**配置 | 配置布尔（`0`/`1`） | 缺节点 / 缺条目 / 缺字段 / 普通 LocationProvider / 跨 VM / 过期 marker：UOE **无** sidecar |
| **`LocationProvider->supportsSpeed()Z`**（VarArg + VaList） | 存活同 VM `ConfiguredLocationProvider`（同 BaseVM、同当前 `TraceEnvironmentConfig`、provider 仍为显式 `providers` 键）**且**该 provider 的 `supportsSpeed` **显式**配置 | 配置布尔（`0`/`1`） | 缺节点 / 缺条目 / 缺字段 / 普通 LocationProvider / 跨 VM / 过期 marker：UOE **无** sidecar |
| **`LocationProvider->supportsBearing()Z`**（VarArg + VaList） | 存活同 VM `ConfiguredLocationProvider`（同 BaseVM、同当前 `TraceEnvironmentConfig`、provider 仍为显式 `providers` 键）**且**该 provider 的 `supportsBearing` **显式**配置 | 配置布尔（`0`/`1`） | 缺节点 / 缺条目 / 缺字段 / 普通 LocationProvider / 跨 VM / 过期 marker：UOE **无** sidecar |
| **`LocationProvider->meetsCriteria(Landroid/location/Criteria;)Z`**（VarArg + VaList） | 存活同 VM `ConfiguredLocationProvider`（同 BaseVM、同当前 `TraceEnvironmentConfig`、provider 仍为显式 `providers` 键）**且**该 provider 的 `meetsCriteria` **显式**配置；arg0 非 null、对象类型**精确**为 `android/location/Criteria`，**且该类型所属 VM 为当前 BaseVM** | 固定 JSON 布尔（`0`/`1`）；**不**读取或推导 `Criteria` 内容 | 缺节点 / 缺条目 / 缺字段 / 普通 LocationProvider / 跨 VM provider / 过期 marker / null `Criteria` / 非 `Criteria` 对象 / **跨 VM 同名 `Criteria`**（provider 留在当前 VM 但参数来自另一 VM）：UOE **无** sidecar。**不是**真实 Android 按 `Criteria` 多字段计算匹配 |
| **`LocationProvider->getAccuracy()I`**（VarArg + VaList） | 存活同 VM `ConfiguredLocationProvider`（同 BaseVM、同当前 `TraceEnvironmentConfig`、provider 仍为显式 `providers` 键）**且**该 provider 的 `accuracy` **显式**配置 | 配置整数 `1`（FINE）或 `2`（COARSE） | 缺节点 / 缺条目 / 缺字段 / 普通 LocationProvider / 跨 VM / 过期 marker：UOE **无** sidecar。**不是** `Location->getAccuracy()F` |
| **`LocationProvider->getPowerRequirement()I`**（VarArg + VaList） | 存活同 VM `ConfiguredLocationProvider`（同 BaseVM、同当前 `TraceEnvironmentConfig`、provider 仍为显式 `providers` 键）**且**该 provider 的 `powerRequirement` **显式**配置 | 配置整数 `1`（LOW）/ `2`（MEDIUM）/ `3`（HIGH） | 缺节点 / 缺条目 / 缺字段 / 普通 LocationProvider / 跨 VM / 过期 marker：UOE **无** sidecar |
| `LocationManager->getLastKnownLocation(Ljava/lang/String;)Landroid/location/Location;` | location SystemService + **`lastKnownLocations` 存在**；arg0 非 null String | **精确** provider 匹配 → 每次新同 VM 私有 `ConfiguredLocation`；无匹配 → Java `null` | 键缺失 / 普通 LocationManager / 其它服务 / null / 非 String：UOE 无事件 |
| `Location->getProvider()Ljava/lang/String;` | 存活同 VM `ConfiguredLocation` | 配置 `provider` | 普通 Location / 跨 VM / 过期：UOE 无事件 |
| `Location->getLatitude()D` / `getLongitude()D` / `getAltitude()D` | 同上；**仅** `callDoubleMethod`（VarArg） | 配置坐标；altitude 缺省 0 | 同上 UOE |
| `Location->hasAltitude()Z` / `hasAccuracy()Z` / **`hasSpeed()Z`** / **`hasBearing()Z`** / **`hasVerticalAccuracy()Z`** / **`hasSpeedAccuracy()Z`** / **`hasBearingAccuracy()Z`** / `isFromMockProvider()Z` / **`isMock()Z`** | 同上（VarArg + VaList） | presence / `mock`；**`isMock` 为 `isFromMockProvider` 只读别名**（同值、无 sidecar） | 同上 UOE |
| `Location->getAccuracy()F` / **`getSpeed()F`** / **`getBearing()F`** / **`getVerticalAccuracyMeters()F`** / **`getSpeedAccuracyMetersPerSecond()F`** / **`getBearingAccuracyDegrees()F`** | 同上；**仅** `callFloatMethodV` | 配置精度 / 速度 / 方位 / 垂直·速度·方位精度；缺省 0 | 同上 UOE |
| `Location->getTime()J` / `getElapsedRealtimeNanos()J` | 同上（VarArg + VaList） | 配置时间；缺省 0 | 同上 UOE |

### Sidecar（旁路事件，仅命中对应 API 且处理成功时）

| 字段 | `isLocationEnabled` | `isProviderEnabled` | **`hasProvider`** | `getAllProviders` / `getProviders` | **`getLastKnownLocation`** | **`getProvider`** | **`LocationProvider.getName`** |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `kind` | `android_location` | `android_location` | `android_location` | `android_location` | `android_location` | `android_location` | `android_location` |
| `api` | `LocationManager.isLocationEnabled` | `LocationManager.isProviderEnabled` | **`LocationManager.hasProvider`** | `LocationManager.getAllProviders` / `getProviders` | **`LocationManager.getLastKnownLocation`** | **`LocationManager.getProvider`** | **`LocationProvider.getName`** |
| `value` | `field=enabled,result=true\|false` | `provider=…,result=true\|false` | **`provider=<name>,result=true\|false`** | `count=…,providers=…` | **`provider=<name>,result=location\|null`（不含坐标）** | **`provider=<name>,result=provider\|null`** | **`provider=<name>,result=<name>`** |
| `source` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` |
| `note` | 读取配置的定位总开关状态 | 读取配置的位置提供者启用状态 | 按显式键查询配置中是否存在该位置提供者 | 返回配置中显式列出的位置提供者 | 按 provider 查询/返回配置的最后已知位置 | 按 provider 查询/返回配置的位置提供者标记 | 读取配置的位置提供者名称 |

**说明：** `Location.*` 实例 getter **不**发 sidecar。**`LocationProvider.requiresNetwork` / `LocationProvider.requiresSatellite` / `LocationProvider.requiresCell` / `LocationProvider.hasMonetaryCost` / `LocationProvider.supportsAltitude` / `LocationProvider.supportsSpeed` / `LocationProvider.supportsBearing` / `LocationProvider.meetsCriteria` / `LocationProvider.getAccuracy` / `LocationProvider.getPowerRequirement` 成功或拒绝均不发 sidecar。** 仅成功处理的 `getLastKnownLocation`（含无匹配返回 `null`）、成功处理的 `getProvider`（含省略/未知返回 `null`）、成功处理的 `hasProvider`（含省略/未知返回 `false`）以及存活标记上的 `LocationProvider.getName` 发事件。

### 明确未实现

- `getCurrentLocation`、位置监听 / 请求更新 / 回调、**最佳提供者**（`getBestProvider` / `getProviders(Criteria, …)`）
- **`LocationProvider.meetsCriteria` 已接入固定 JSON 布尔标记**（`providerCapabilities.<provider>.meetsCriteria`）；**不**按真实 Android 解析 `Criteria` 多字段做匹配，也**不**存储/推导 `Criteria` 内容。参数须精确为当前 VM 的 `android/location/Criteria`（跨 VM 同名 `Criteria` 拒绝）。**已接入** `requiresNetwork()Z`、`requiresSatellite()Z`、`requiresCell()Z`、`hasMonetaryCost()Z`、`supportsAltitude()Z`、`supportsSpeed()Z`、`supportsBearing()Z`、**`getAccuracy()I`**（`providerCapabilities.<provider>.accuracy` 仅 `1`/`2`，与 `Location.getAccuracy()F` / `accuracyMeters` 无关）与 **`getPowerRequirement()I`**（`providerCapabilities.<provider>.powerRequirement` 仅 `1`/`2`/`3`＝`POWER_USAGE_LOW`/`MEDIUM`/`HIGH`）
- 其它未列 Location 字段（速度/方位/垂直·速度·方位精度子集已落地）
- 从 `enabled`/`providers`/time 推导 last-known；GNSS / 卫星数量
- Location setter / mock 注入 API

## android.identifiers

广告标识符（Advertising ID）、可选 **Android ID**，以及可选 **App Set ID**（应用集合标识符）同步 Task marker 子集。路径为 `android.identifiers`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidIdentifiersConfig`；JNI：Advertising ID 见下表；**`androidId`** 经 `Settings.Secure.getString` 精确接线；**`appSetId`** 经 Play services App Set 精确签名接线（仅分析用同步完成 `Task` marker）。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidIdentifiersConfigured()` | 行为摘要 |
| --- | --- | --- |
| **节点缺失** | `false` | Advertising ID、`androidId` 与 App Set ID 路径均为 UOE 无事件 |
| **显式 `{}`** | `true` | 默认 `advertisingId`/`limitAdTracking`；**`androidId` / `appSetId` 未配置**（无默认、不推导） |
| **有字段** | `true` | 已配键覆盖；`androidId` / `appSetId` 仅当键存在时配置 |

### 字段说明（仅允许下列 5 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `advertisingId` | 否 | 非空 String；规范小写 UUID（`UUID.fromString` 后 `toString()` 全等） | `00000000-0000-0000-0000-00000000a001` |
| `limitAdTracking` | 否 | 严格 JSON Boolean | `false` |
| **`androidId`** | 否 | 恰好 **16** 位十六进制 String（`0-9a-fA-F`）；parse 时**规范为小写** | **键缺失 → 未配置**（`isAndroidIdConfigured()=false`，`getAndroidId()=null`；**无**默认值） |
| **`appSetId`** | 否 | 非空 String，长度 **1..150**；仅 ASCII 可打印字符（`0x20..0x7E`），拒绝 NUL/CR/LF；**不**要求 UUID | **键缺失 → 未配置**（`isAppSetIdConfigured()=false`，`getAppSetId()=null`；**不**从 `advertisingId`/`androidId`/`packageName` 推导） |
| **`appSetScope`** | 否（且仅当 `appSetId` 显式存在） | 精确 JSON Number 整数，只允许 **1**（`SCOPE_APP` / app）或 **2**（`SCOPE_DEVELOPER` / developer） | **`appSetId` 已配置且本键缺失 → 固定 1**；**无 `appSetId` 时单独出现 → parse 失败** |

未知键 parse 失败。`androidId` 拒绝 null / 非 String / 长度≠16 / 非 hex。错误路径 `android.identifiers.androidId`。**不**从 `android.settings.secure.android_id` 推导。`appSetId` 拒绝 null / 非 String / 空串 / 超长 / 控制字符与非 ASCII。`appSetScope` 拒绝 String/Boolean/null/分数/0/3 等；错误路径 `android.identifiers.appSetScope`。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 接入 | 已配置 | 未配置 |
| --- | --- | --- | --- |
| `AdvertisingIdClient->getAdvertisingIdInfo(...)` | 静态 Object VarArg/VaList | 私有 marker | UOE 无事件 |
| `AdvertisingIdClient$Info->getId()Ljava/lang/String;` | 实例 Object | marker 上 `advertisingId` | 非 marker UOE |
| `AdvertisingIdClient$Info->isLimitAdTrackingEnabled()Z` | 实例 boolean | marker 上 `limitAdTracking` | 同上 |
| **`Settings$Secure->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;`** | 静态 Object VarArg/VaList | **仅** `androidId` 已配置且 arg1 精确 **`android_id`** → 返回**新** `StringObject`（小写 hex） | 键未配置 / 其它 key / 非 String 参数 / System·Global 签名：UOE，**不发**事件；**无**宿主/默认回落 |
| **`AppSet->getClient(Landroid/content/Context;)Lcom/google/android/gms/appset/AppSetIdClient;`** | 静态 Object VarArg/VaList | **仅** `appSetId` 已显式配置 → 同 VM 私有 `AppSetIdClient` marker（**不发** sidecar） | 键未配置 / 错误签名：既有 UOE，无事件，不读宿主 |
| **`AppSetIdClient->getAppSetIdInfo()Lcom/google/android/gms/tasks/Task;`** | 实例 Object VarArg/VaList | **仅**同 VM client marker → 已完成 `Task` marker | 外来/跨 VM/错误签名：既有 UOE，无事件 |
| **`Task->getResult()Ljava/lang/Object;`** | 实例 Object VarArg/VaList | **仅**本路径创建的已完成 task marker → 同 VM `AppSetIdInfo` marker | 带 `Class` 重载 / `isComplete` / 监听器 / 外来 Task：不接管 |
| **`AppSetIdInfo->getId()Ljava/lang/String;`** | 实例 Object VarArg/VaList | **仅**同 VM info marker → 配置 `appSetId` | 外来/跨 VM/错误签名：既有 UOE，无事件 |
| **`AppSetIdInfo->getScope()I`** | 实例 int VarArg/VaList | **仅**同 VM info marker → 配置 scope（1 或 2） | 外来/跨 VM/错误签名：既有 UOE，无事件 |

Advertising ID 的 `Info` marker 必须属于当前 `BaseVM`；跨 VM 读取 `getId` / `isLimitAdTrackingEnabled` 不接管、不发 sidecar。

**优先级：** `identifiers.androidId` 路径在通用 `android.settings` `getString` 之前匹配 Secure + `android_id`。App Set ID 各签名仅在 `appSetId` 显式配置且接收者为当前 `BaseVM` 绑定的私有 marker 时接管。

此实现只提供**同步完成**的 `Task` marker，用于分析复现 `getResult()`；**不**实现真实 Google Play 服务、监听器、异步调度、异常、缓存或跨包一致性推导。

### Sidecar（旁路事件，仅配置命中时）

| 字段 | Advertising ID | **`androidId` / Secure.getString** | **App Set ID** |
| --- | --- | --- | --- |
| `kind` | `android_identifier` | `android_identifier` | `android_identifier` |
| `api` | `AdvertisingIdClient.getAdvertisingIdInfo` / `AdvertisingIdInfo.getId` / `…isLimitAdTrackingEnabled` | **`Settings.Secure.getString`** | **`AppSetIdClient.getAppSetIdInfo`** / **`Task.getResult`** / **`AppSetIdInfo.getId`** / **`AppSetIdInfo.getScope`**（`getClient` **不发**事件） |
| `value` | 既有格式 | **`key=android_id,result=<小写hex>`** | 仅摘要：`appSetIdLength=<n>`、`scope=<n>`、`resultLength=<n>`；**不**写 `appSetId` 原文 |
| `source` | `json-config` | `json-config` | `json-config` |
| `note` | 中文简述 | 中文简述 | 中文：读取配置的应用集合标识符或其范围 |

### 明确未实现

- Android ID 随机生成、Telephony 设备 ID
- `Settings.System` / `Settings.Global` 上的 `android_id`；ContentResolver 行为模拟
- 真实 Google Play services 绑定、App Set 监听器 / 异步 `Task` / `getResult(Class)` / `isComplete` / `isSuccessful` / 异常与缓存 / 跨包一致性

## android.accounts

账户列表子集（只读）。路径为 `android.accounts`（可选 **JSONArray** of objects）。实现类：`TraceEnvironmentConfig.AndroidAccountConfig`；JNI 接线在 `AbstractJni`：静态 `callStaticObjectMethod` / `V`，实例 `callObjectMethod` / `V`，字段 `getObjectField`。

**节点缺失 vs 显式空数组：**

| 状态 | `isAndroidAccountsConfigured()` | `getAndroidAccounts()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | 空不可变列表 | `AccountManager.get` / `getAccounts` 等保持原先 `UnsupportedOperationException`，**不发** sidecar |
| **显式 `[]`** | `true` | 空不可变列表 | `get` 返回 marker；`getAccounts` 返回空数组并发 sidecar `count=0` |
| **有条目** | `true` | 配置顺序不可变列表 | 同上；`getAccounts` 按 JSON 顺序返回新数组 |

### 元素字段（每项恰好 2 键）

| 字段 | 必填 | 类型与校验 |
| --- | --- | --- |
| `name` | 是 | 非空 String（`Account.name`） |
| `type` | 是 | 非空 String（`Account.type`） |

未知键、缺少 `name`/`type`、空串、null、非 String、节点非数组 → parse 失败，路径形如 `android.accounts` / `android.accounts[i].name`。配置条目不可变；**不**保留 JSONArray。**不**要求 name/type 全局唯一。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 接入 | 已配置 | 未配置 / 错误 marker |
| --- | --- | --- | --- |
| `android/accounts/AccountManager->get(Landroid/content/Context;)Landroid/accounts/AccountManager;` | `callStaticObjectMethod` / `V` | 返回携带 **私有 marker**（`ConfiguredAccountManager`，绑定创建 BaseVM）的 AccountManager | UOE，无事件 |
| `android/accounts/AccountManager->getAccounts()[Landroid/accounts/Account;` | `callObjectMethod` / `V` | **仅**同 VM marker：每次返回 **新的** `Account[]`（元素为同 VM `ConfiguredAccount`，JSON 顺序） | 非 marker / 跨 VM / 节点缺失：UOE；marker 上其它未实现方法 UOE |
| `android/accounts/AccountManager->getAccountsByType(Ljava/lang/String;)[Landroid/accounts/Account;` | `callObjectMethod` / `V` | **仅**同 VM marker + 非 null `StringObject` type：按配置 `type` **精确相等**过滤，JSON 顺序新数组 | null/非 String 参数 / 非 marker / 跨 VM / 节点缺失：UOE 无事件 |
| `android/accounts/Account->name:Ljava/lang/String;` | `getObjectField` | **仅**同 VM `ConfiguredAccount` 返回配置 `name` | 非 marker / 跨 VM：UOE |
| `android/accounts/Account->type:Ljava/lang/String;` | `getObjectField` | **仅**同 VM `ConfiguredAccount` 返回配置 `type` | 非 marker / 跨 VM：UOE |

**provenance 隔离：** 仅本 VM 配置路径产出的 marker 命中 `getAccounts` / `getAccountsByType` / `name` / `type`；plain 或跨 VM marker **不会**误命中。

### Sidecar（仅成功列表读取）

| 字段 | `getAccounts` | `getAccountsByType` |
| --- | --- | --- |
| `kind` | `android_account` | `android_account` |
| `api` | `AccountManager.getAccounts` | `AccountManager.getAccountsByType` |
| `value` | `count=<n>` | `type=<type>,count=<n>` |
| `source` | `json-config` | `json-config` |
| `note` | `返回配置的账户列表` | `返回配置的指定类型账户列表` |

**不**为 `AccountManager.get` 或 `Account.name` / `type` 字段读取发 sidecar。

### 明确未实现

- 账户增删、token / authenticator、监听器
- system-service 路由（`ACCOUNT_SERVICE` / `getSystemService`）、权限模型
- 其它 `AccountManager` / `Account` API

## android.inputMethods

已安装/启用输入法列表子集（只读）。路径为 `android.inputMethods`（可选 **JSONArray** of objects）。实现类：`TraceEnvironmentConfig.AndroidInputMethodConfig`；JNI 接线在 `AbstractJni`：实例 `callObjectMethod` / `V`（SystemService `input_method` 接收者 + 同 VM `InputMethodInfo` marker）。另支持既有 `Application.getSystemService("input_method")` 与 **仅** `InputMethodManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService` 标记（类型 `android/view/inputmethod/InputMethodManager`；**不**要求 `android.inputMethods` 节点即可拿到服务标记；lookup **不发** sidecar）。

**节点缺失 vs 显式空数组：**

| 状态 | `isAndroidInputMethodsConfigured()` | `getAndroidInputMethods()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | 空不可变列表 | `getInputMethodList` / `getEnabledInputMethodList` 保持 UOE，**不发** sidecar（`getSystemService("input_method")` / 类型化 `getSystemService(InputMethodManager.class)` 仍可返回服务标记；lookup **不发** sidecar） |
| **显式 `[]`** | `true` | 空不可变列表 | 两列表 API 返回空 `ArrayListObject` 并发 sidecar `count=0` |
| **有条目** | `true` | 配置顺序不可变列表 | 全量列表 / 仅 `enabled=true` 过滤列表（JSON 顺序） |

### 元素字段（每项恰好 2 键）

| 字段 | 必填 | 类型与校验 |
| --- | --- | --- |
| `id` | 是 | 非空 String（`InputMethodInfo.getId()`） |
| `enabled` | 是 | 严格 JSON Boolean |

未知键、缺少字段、空串、null、非 String/Boolean、节点非数组 → parse 失败。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 接入 | 已配置 | 未配置 / 错误接收者 |
| --- | --- | --- | --- |
| `Application`/`Context->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;`（VarArg + VaList） | 第 0 参为 **`DvmClass`** 且类名为 `android/view/inputmethod/InputMethodManager` | 返回与 `"input_method"` 字符串路径**同一** `SystemService` 标记 | **不**要求 `android.inputMethods` 节点；null / 非 DvmClass / 其它 Class → UOE 无 sidecar；lookup **不发** sidecar |
| `InputMethodManager->getInputMethodList()Ljava/util/List;` | `callObjectMethod` / `V` | **仅** `SystemService("input_method")`：新 `ArrayListObject`，元素为同 VM `ConfiguredInputMethodInfo`（JSON 顺序） | 节点缺失 / 普通 IMM / 其它 SystemService：UOE 无事件 |
| `InputMethodManager->getEnabledInputMethodList()Ljava/util/List;` | 同上 | 同上，**仅** `enabled=true` 条目 | 同上 |
| `InputMethodInfo->getId()Ljava/lang/String;` | `callObjectMethod` / `V` | **仅**同 VM marker 返回配置 `id`（**无** sidecar） | 非 marker / 跨 VM：UOE |

### Sidecar（仅成功列表调用）

| 字段 | 值 |
| --- | --- |
| `kind` | `android_input_method` |
| `api` | `InputMethodManager.getInputMethodList` 或 `InputMethodManager.getEnabledInputMethodList` |
| `value` | `count=<n>` |
| `source` | `json-config` |
| `note` | `返回配置的输入法列表` / `返回配置的已启用输入法列表` |

### 明确未实现

- subtype / 当前输入法、切换、settings 联动、listeners、实际 IME 操作

## android.userState

用户编号、序列号与用户状态布尔子集（**单用户**画像）。路径为 `android.userState`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidUserStateConfig`；JNI（Java 原生接口）接线在 `AbstractJni`：静态 int/对象、实例 int、布尔、long 与实例对象路径。另支持 `Context.USER_SERVICE` 静态字段、`Application`/`Context.getSystemService("user")` 与 **仅** `UserManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService` 标记（类型 `android/os/UserManager`，值为 `"user"`；**不**要求 `android.userState` 节点即可拿到服务标记；lookup **不读**环境、**不发** sidecar）。`Context.getSystemService(String)` 对已知服务名与 `Application` 相同（直接返回 marker）；未知名维持既有 `SystemService` 行为。真正的 `UserManager` 读取仍由 `android.userState` 门控。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidUserStateConfigured()` | `getAndroidUserStateConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | `UserHandle` / `UserManager` 读取签名（含 `getUserHandleForSerialNumber`）保持原先 `UnsupportedOperationException`，**不发** sidecar（旁路事件）；`Context.USER_SERVICE` / `getSystemService("user")` / 类型化 `getSystemService(UserManager.class)` 仍可返回服务标记（lookup **不发** sidecar） |
| **显式 `{}`** | `true` | 非 null | 默认 `userId=0`、`serialNumber=0`、`userUnlocked=true`、`systemUser=true`、`managedProfile=false`、`demoUser=false` |
| **有字段** | `true` | 非 null | 已配字段覆盖默认 |

### 字段说明（仅允许下列 6 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `userId` | 否 | 精确 JSON Number 整数，`0..99999` | `0` |
| `serialNumber` | 否 | 精确 JSON Number 整数，`0..Long.MAX_VALUE`（long 精度） | `0` |
| `userUnlocked` | 否 | 严格 JSON Boolean（布尔） | `true` |
| `systemUser` | 否 | 严格 JSON Boolean | `true` |
| `managedProfile` | 否 | 严格 JSON Boolean | `false` |
| `demoUser` | 否 | 严格 JSON Boolean | `false` |

未知键在 parse（解析）时抛 `IllegalArgumentException`，路径形如 `android.userState.<key>`。`userId` / `serialNumber` 拒绝 null / String / Boolean / 小数 / 溢出 / 负数（serial）；布尔字段拒绝 null / String / Number。配置对象不可变，提供各值 getter 与 `isUserIdConfigured` / `isSerialNumberConfigured` / `isUserUnlockedConfigured` / `isSystemUserConfigured` / `isManagedProfileConfigured` / `isDemoUserConfigured`；**不**保留 JSONObject。**不**为反向映射新增字段：`getUserHandleForSerialNumber` 复用 `serialNumber` / `userId`。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 接入 | 已配置 | 未配置 / 非法参数 |
| --- | --- | --- | --- |
| `android/content/Context->USER_SERVICE:Ljava/lang/String;` | 静态字段 | 返回 `"user"`（与 `SystemService.USER_SERVICE` 相同） | 无条件支持（**不**要求 `android.userState` 节点；lookup **不发** sidecar） |
| `android/app/Application`/`android/content/Context->getSystemService(Ljava/lang/String;)Ljava/lang/Object;`（服务名 `"user"`） | 字符串服务名 | 返回 `SystemService` user 标记（类型 `UserManager`）；**不**读环境、**不发** sidecar | 无条件支持（**不**要求 `android.userState` 节点；lookup **不发** sidecar） |
| `Application`/`Context.getSystemService(Ljava/lang/Class;)`（**仅** `UserManager`；VarArg / VaList） | `callObjectMethod` / `V`；第 0 参为 `DvmClass` 且类名为 `android/os/UserManager` | 返回同一 `SystemService("user")`；lookup **不发** sidecar | **不**要求 `android.userState` 节点；仍返回标记；lookup **不发** sidecar；随后 UserManager getter 仍 UOE |
| `android/os/UserHandle->myUserId()I` | `callStaticIntMethod` / `callStaticIntMethodV` | 返回 `userId` | UOE，无事件 |
| `android/os/UserHandle->myUserHandle()Landroid/os/UserHandle;` | `callStaticObjectMethod` / `callStaticObjectMethodV` | 返回携带**私有** `ConfiguredUserHandle` marker 的 `UserHandle` | UOE，无事件 |
| `android/os/UserHandle->getIdentifier()I` | `callIntMethod` / `callIntMethodV` | **仅**本 VM 的 `ConfiguredUserHandle` 上返回同一 `userId` | 普通/跨 emulator marker / 节点缺失：UOE |
| `android/os/UserManager->isUserUnlocked()Z` | `callBooleanMethod` / `callBooleanMethodV` | 返回 `userUnlocked` | 节点缺失：UOE，无事件 |
| `android/os/UserManager->isUserUnlocked(Landroid/os/UserHandle;)Z` | `callBooleanMethod` / `callBooleanMethodV`（实例；VarArg + VaList） | 第 0 参为**当前 BaseVM** 创建、且 `marker.config` 与 `getAndroidUserStateConfig()` **引用相同**的 `ConfiguredUserHandle` 时返回 `userUnlocked` | 普通 handle / `null` / 跨 VM marker / 节点缺失：UOE，**不发**事件 |
| `android/os/UserManager->isSystemUser()Z` | 同上（无参） | 返回 `systemUser` | UOE |
| `android/os/UserManager->isManagedProfile()Z` | 同上（无参） | 返回 `managedProfile` | UOE |
| `android/os/UserManager->isDemoUser()Z` | 同上（无参；VarArg + VaList） | 返回 `demoUser` | 节点缺失：UOE，**不发**事件 |
| `android/os/UserManager->getSerialNumberForUser(Landroid/os/UserHandle;)J` | `callLongMethod` / `callLongMethodV` | 第 0 参为本 VM 的 `ConfiguredUserHandle` 时返回 `serialNumber` | null / 普通 handle / 跨 emulator marker / 缺失：UOE，无事件 |
| `android/os/UserManager->getUserHandleForSerialNumber(J)Landroid/os/UserHandle;` | `callObjectMethod` / `callObjectMethodV`（实例；VarArg + VaList） | 输入 long **等于** `serialNumber` → 新建**同 VM** 的 `ConfiguredUserHandle`（绑定**同一** config 实例）；**不等** → 作为已处理结果返回 Java `null`（**不** UOE） | 节点缺失：UOE，**不发**事件 |

**`getUserHandleForSerialNumber` 返回的标记**可用于 `getIdentifier()`（得配置 `userId`）、`getSerialNumberForUser` 与 **`isUserUnlocked(UserHandle)`**（得配置 `userUnlocked`），并遵守与 `myUserHandle` 相同的 provenance 规则。

**provenance（来源）隔离：** `ConfiguredUserHandle` **同时绑定**创建它的 `BaseVM`（owner）与 `AndroidUserStateConfig` 引用；校验使用引用相等（`==`，**不用** `equals`）。跨 emulator、普通 `UserHandle`、`null` handle **不得**命中配置路径（含 `isUserUnlocked(UserHandle)`、`getSerialNumberForUser`）。**不**接受也不产生跨 VM 标记。marker 上其它 int 方法一律 UOE，**不**落入通用 fallback（回退）。

### Sidecar（旁路事件，仅配置节点存在且命中对应 API 时）

| 字段 | 值 |
| --- | --- |
| `kind` | `android_user` |
| `api` | `UserHandle.myUserId` / `UserHandle.myUserHandle` / `UserHandle.getIdentifier` / `UserManager.isUserUnlocked` / **`UserManager.isUserUnlocked(UserHandle)`** / `UserManager.isSystemUser` / `UserManager.isManagedProfile` / **`UserManager.isDemoUser`** / `UserManager.getSerialNumberForUser` / `UserManager.getUserHandleForSerialNumber` |
| `value` | 常见：`result=<id>`、`userId=<id>`、`result=true\|false`、`result=<long>`；**带 handle 的 isUserUnlocked 成功** `userId=<配置 userId>,result=true\|false`；**反向映射命中** `serialNumber=<输入>,userId=<配置 userId>`；**反向映射未命中** `serialNumber=<输入>,result=null` |
| `source` | `json-config` |
| `note` | 中文简述（如读取配置的当前用户 ID / UserHandle / 用户解锁状态 / 演示用户状态；带 handle 为「读取 UserHandle 对应配置的用户解锁状态」；反向映射为「根据序列号匹配配置的 UserHandle」或「序列号未匹配配置的用户」） |

**说明：** 只要 `android.userState` 节点存在，每次精确查询 `getUserHandleForSerialNumber`（无论 long 是否命中）均 **emit 一次** sidecar；节点缺失时 **不** emit。`isUserUnlocked(UserHandle)` **仅成功处理时** emit（失败路径无事件）。无参 `isUserUnlocked` 与带 handle 重载的 `api` 字段**严格区分**。`UserManager.isDemoUser` 在节点存在时 `value=result=true|false`、source=`json-config`；节点缺失时 UOE 且**不** emit。`Context.USER_SERVICE` / `getSystemService("user")` / 类型化 `getSystemService(UserManager.class)` **均不发** sidecar。

### 明确未实现

- 多用户列表 / 多序列号映射表（当前仅为**单用户**子集：唯一 `serialNumber` ↔ 唯一 `userId`）
- 账户列表其余能力（增删/token 等；**已实现** `get`/`getAccounts`/`getAccountsByType`/`Account.name|type` 见 `android.accounts`）、剪贴板其余能力（写入/监听/extras/styled spans/`coerceToHtmlText`/URI/Intent/HTML/多 MIME/多 item 等；**已实现** `hasPrimaryClip`+可选 `primaryText`/`primaryLabel`/`timestampMillis`/`getPrimaryClip` 主文本子集及纯文本 `getText`/`coerceToText(Context)`/`getDescription`/`getMimeTypeCount`/`getMimeType(0)`/`hasMimeType`/`getLabel`/`getTimestamp`/`isStyledText`（marker 上 false）见 `android.clipboard`）、App Set ID
- 其它 `UserManager` / 多用户画像字段（**不含**已实现的 `isDemoUser`）

### API 事实说明（非缺口）

- Android 公共 API **不存在** `UserManager.isSystemUser(UserHandle)` / `isManagedProfile(UserHandle)` 重载，**仅**有无参 `isSystemUser()` / `isManagedProfile()`（本配置节已实现）；因此**不**将上述虚构签名列为未实现能力或模拟目标。
- 与之相对，`isUserUnlocked(UserHandle)` **是**真实公共 API，且已按配置子集实现（见上表）。
- `UserManager.isDemoUser()` **是**真实公共 API，且已按 `android.userState.demoUser` 实现（见上表）。
- `isGuestUser` **不是**当前公开的 Android `UserManager` 公共 API，**不**作为与 `isDemoUser` 并列的可实现模拟目标，也**不**列为配置字段缺口。

## android.securitySignals

安全与检测相关信号的统一配置节（分析型一致性画像）。路径为 `android.securitySignals`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidSecuritySignalsConfig`。**当前为七字段子集：** `debuggerConnected`、`waitingForDebugger`、`debuggerTracing`、`selinuxEnabled`、`selinuxEnforced`、`userAMonkey` 与 `userTestHarness`（彼此独立）。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidSecuritySignalsConfigured()` | `getAndroidSecuritySignalsConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | `Debug.isDebuggerConnected` **保留** unidbg 历史硬编码 `false`，**不发** sidecar（旁路事件），**不是** `UnsupportedOperationException`；`Debug.waitingForDebugger` / `Debug.isDebuggerTracing` / `SELinux.isSELinuxEnabled` / `SELinux.isSELinuxEnforced` / `ActivityManager.isUserAMonkey` / `ActivityManager.isRunningInUserTestHarness` **保持历史 UOE**，**不发** 事件 |
| **显式 `{}`** | `true` | 非 null | 默认 `debuggerConnected=false`、`waitingForDebugger=false`、`debuggerTracing=false`、`selinuxEnabled=true`、`selinuxEnforced=true`、`userAMonkey=false`、`userTestHarness=false`；各字段 `*Configured()` 均为 `false` |
| **有字段** | `true` | 非 null | 使用配置值 |

### 字段说明（仅允许下列 7 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `debuggerConnected` | 否 | 严格 JSON Boolean（布尔） | `false` |
| `waitingForDebugger` | 否 | 严格 JSON Boolean（布尔） | `false` |
| `debuggerTracing` | 否 | 严格 JSON Boolean（布尔） | `false` |
| `selinuxEnabled` | 否 | 严格 JSON Boolean（布尔） | `true` |
| `selinuxEnforced` | 否 | 严格 JSON Boolean（布尔） | `true` |
| `userAMonkey` | 否 | 严格 JSON Boolean（布尔） | `false` |
| `userTestHarness` | 否 | 严格 JSON Boolean（布尔） | `false` |

未知键（如 `rooted`、`emulator`）在 parse（解析）时抛 `IllegalArgumentException`，路径形如 `android.securitySignals.<key>`。字段存在时拒绝 null / String / Number。配置对象不可变，提供 `isDebuggerConnected()` / `isWaitingForDebugger()` / `isDebuggerTracing()` / `isSelinuxEnabled()` / `isSelinuxEnforced()` / `isUserAMonkey()` / `isUserTestHarness()` 与对应 `*Configured()`；**不**保留 JSONObject。**`userAMonkey` / `userTestHarness` 互不推导，也不从 debugger / SELinux 字段推导。**

### 对 `AbstractJni` 的精确影响

| 精确签名 | 接入 | 节点存在（含 `{}`） | 节点缺失 |
| --- | --- | --- | --- |
| `android/os/Debug->isDebuggerConnected()Z` | `callStaticBooleanMethod`（VarArg）/ `callStaticBooleanMethodV`（VaList） | 返回 `debuggerConnected`，并发 sidecar | **历史硬编码 `false`**，无事件，**非** UOE |
| `android/os/Debug->waitingForDebugger()Z` | `callStaticBooleanMethod`（VarArg）/ `callStaticBooleanMethodV`（VaList） | 返回 `waitingForDebugger`，并发 sidecar | **保持历史 UOE**，无事件 |
| `android/os/Debug->isDebuggerTracing()Z` | `callStaticBooleanMethod`（VarArg）/ `callStaticBooleanMethodV`（VaList） | 返回 `debuggerTracing`（键缺失时默认 `false`），并发 sidecar | **保持历史 UOE**，无事件 |
| `android/os/SELinux->isSELinuxEnabled()Z` | `callStaticBooleanMethod`（VarArg）/ `callStaticBooleanMethodV`（VaList） | 返回 `selinuxEnabled`（键缺失时默认 `true`），并发 sidecar | **保持历史 UOE**，无事件 |
| `android/os/SELinux->isSELinuxEnforced()Z` | `callStaticBooleanMethod`（VarArg）/ `callStaticBooleanMethodV`（VaList） | 返回 `selinuxEnforced`（键缺失时默认 `true`），并发 sidecar | **保持历史 UOE**，无事件 |
| `android/app/ActivityManager->isUserAMonkey()Z` | `callStaticBooleanMethod`（VarArg）/ `callStaticBooleanMethodV`（VaList） | 返回 `userAMonkey`（键缺失时默认 `false`），并发 sidecar | **保持历史 UOE**，无事件 |
| `android/app/ActivityManager->isRunningInUserTestHarness()Z` | `callStaticBooleanMethod`（VarArg）/ `callStaticBooleanMethodV`（VaList） | 返回 `userTestHarness`（键缺失时默认 `false`），并发 sidecar | **保持历史 UOE**，无事件 |

**仅按精确签名命中**；其它 `Debug` / `ActivityManager` 静态布尔方法**不**支持（含已弃用 `isRunningInTestHarness`；配置存在时同样 UOE）。**不**改变 `TextUtils.isEmpty` 等其它静态布尔路径。各 API 的 sidecar `api` 字段严格区分。

### Native：`libselinux.so` 子集

| 符号 | 调度 | 命中条件 | 节点/字段缺失 |
| --- | --- | --- | --- |
| **`security_getenforce`**（无参，`int`） | `SelinuxGetEnforceHook`（ARM32/ARM64；`android.securitySignals` 存在时注册） | **`1`/`0`** ← `selinuxEnforced`（缺键默认 true→`1`） | **不拦截**；无宿主回落 |
| **`is_selinux_enabled`**（无参，`int`） | `SelinuxIsEnabledHook`（同上） | **`1`/`0`** ← `selinuxEnabled`（缺键默认 true→`1`） | **不拦截** |
| **`getcon(char **context)`** | `SelinuxGetconHook`（`selinuxContext` 或 `fileSelinuxContexts` 任一配置时注册；ARM32/ARM64） | 模拟器内存分配 **NUL 结尾**配置上下文，写入非 null `*context`，返回 **0**；sidecar 仅长度摘要 | 字段缺失 / **null** 输出指针 / 错误库符号：**不拦截** |
| **`getfilecon(const char *path, char **context)`** | 同上 hook | **仅** `fileSelinuxContexts` 中命中路径时：分配 NUL 结尾上下文，写入非 null `*context`，返回 **length+1**（含 NUL，libselinux ABI）；sidecar 仅长度摘要（**无**路径/原文） | map/路径缺失 / null path / null out / 错误符号：**不拦截** |
| **`lgetfilecon(const char *path, char **context)`** | 同上 hook | 与 getfilecon **同一**字面归一化路径 map 与分配/freecon 生命周期；**不**解析符号链接；返回 **length+1**；sidecar `api=lgetfilecon` 仅长度摘要 | 同上；**不**实现 fgetfilecon |
| **`freecon(char *context)`** | 同上 hook 实例 | **仅**释放本 hook `getcon`/`getfilecon`/`lgetfilecon` 跟踪的分配；其它指针 **不拦截**；**无** sidecar | 未跟踪指针 → 原符号路径 |

**仅**精确库名 `libselinux.so` + 上表符号。**不**实现 `security_setenforce`/`setenforce`、`fgetfilecon`/`getpeercon`/`setcon`、`security_check_context`、xattr、策略文件或其它 libselinux API。

| 字段 | `security_getenforce` / `is_selinux_enabled` | `getcon`（成功） | `getfilecon` / `lgetfilecon`（成功） |
| --- | --- | --- | --- |
| `kind` | `linux_security` | `linux_security` | `linux_security` |
| `api` | `security_getenforce` / `is_selinux_enabled` | `getcon` | `getfilecon` / `lgetfilecon` |
| `value` | `result=0\|1` | `result=0,length=<UTF-8 字节数>`（**不**写上下文原文） | `result=<length+1>,length=<UTF-8 字节数>`（**不**写路径/上下文原文） |
| `source` | `json-config` | `json-config` | `json-config` |

**说明：** Java `android.os.SELinux` 两布尔 + native enforce/enabled/getcon/getfilecon/lgetfilecon(+配对 freecon) 子集已落地；其它 libselinux、xattr/策略、ActivityManager 实例/服务等**仍未实现**。

### Sidecar（旁路事件，仅节点存在且命中对应 API 时）

| 字段 | `isDebuggerConnected` | `waitingForDebugger` | `isDebuggerTracing` | `isSELinuxEnabled` | `isSELinuxEnforced` | `isUserAMonkey` | `isRunningInUserTestHarness` |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `kind` | `android_security` | `android_security` | `android_security` | `android_security` | `android_security` | `android_security` | `android_security` |
| `api` | `Debug.isDebuggerConnected` | `Debug.waitingForDebugger` | `Debug.isDebuggerTracing` | `SELinux.isSELinuxEnabled` | `SELinux.isSELinuxEnforced` | `ActivityManager.isUserAMonkey` | `ActivityManager.isRunningInUserTestHarness` |
| `value` | `result=true\|false` | `result=true\|false` | `result=true\|false` | `field=selinuxEnabled,result=true\|false` | `field=selinuxEnforced,result=true\|false` | `field=userAMonkey,result=true\|false` | `field=userTestHarness,result=true\|false` |
| `source` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` |
| `note` | 中文简述（如读取配置的调试器连接状态） | 中文简述（如读取配置的等待调试器状态） | 中文简述（如读取配置的调试器跟踪状态） | 中文简述（如读取配置的 SELinux 启用状态） | 中文简述（如读取配置的 SELinux 强制模式状态） | 中文简述（读取配置的是否为 Monkey 用户） | 中文简述（读取配置的是否运行在用户测试框架中） |

### 明确未实现

- 其它 `Debug` API（除上述 Debug 精确签名外）
- root / Magisk / Xposed / Frida 路径与进程画像
- SELinux 挂载标签 / xattr / 策略、其它 libselinux API（Java 两布尔 + native `security_getenforce` / `is_selinux_enabled` / `getcon` / `getfilecon` / `lgetfilecon`+配对 `freecon` 已落地）
- emulator / QEMU 特征、`ro.kernel.qemu` 等属性组的结构化联动
- ADB（安卓调试桥）/ USB 调试 / bootloader 锁定等一致性组
- `ptrace` / `prctl` / `TracerPid` 与调试端口表现（见缺失清单第 19 节）
- ActivityManager 实例/服务、进程列表、已弃用 `isRunningInTestHarness`、device-farm 行为、真实 Monkey 行为模拟

## android.securityState

锁屏、设备锁定与生物识别标记子集（**六字段**）：四布尔 `keyguardLocked` / `keyguardSecure` / `deviceLocked` / `deviceSecure`（相互独立）+ 整型 `biometricCanAuthenticateResult` + 长整型 `biometricLastAuthenticationElapsedRealtimeMillis`。路径为 `android.securityState`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.AndroidSecurityStateConfig`；JNI 接线在 `AbstractJni`：`callBooleanMethod` / `V`（Keyguard）、`callIntMethod` / `V`（`BiometricManager.canAuthenticate`）、`callLongMethod` / `V`（`BiometricManager.getLastAuthenticationTime`）。另支持 **仅** `KeyguardManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService("keyguard")`（**不**要求 `android.securityState` 节点即可拿到服务标记；lookup **不发** `android_security_state` sidecar）。

**节点缺失 vs 显式空对象：**

| 状态 | `isAndroidSecurityStateConfigured()` | `getAndroidSecurityStateConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | `null` | 下列 `KeyguardManager` / `BiometricManager` 方法保持 `UnsupportedOperationException`，**不发** sidecar；类型化 `getSystemService(KeyguardManager.class)` 仍可返回服务标记（lookup **不发** sidecar） |
| **显式 `{}`** | `true` | 非 null | 默认四布尔均为 `false`；`biometricCanAuthenticateResult=12`（`BIOMETRIC_ERROR_NO_HARDWARE`）；`biometricLastAuthenticationElapsedRealtimeMillis=-1`（`BIOMETRIC_NO_AUTHENTICATION`） |
| **有字段** | `true` | 非 null | 已配字段覆盖默认 |

### 字段说明（仅允许下列 6 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `keyguardLocked` | 否 | 严格 JSON Boolean | `false` |
| `keyguardSecure` | 否 | 严格 JSON Boolean | `false` |
| `deviceLocked` | 否 | 严格 JSON Boolean | `false` |
| `deviceSecure` | 否 | 严格 JSON Boolean | `false` |
| `biometricCanAuthenticateResult` | 否 | 精确 JSON Number 整数，**仅**框架 `BiometricManager.canAuthenticate` 结果码 `{0,1,11,12,15,20,21}` | `12` |
| `biometricLastAuthenticationElapsedRealtimeMillis` | 否 | 精确 JSON Number 整数：仅 **`-1`**（`BIOMETRIC_NO_AUTHENTICATION`）或**非负** | `-1` |

未知键在 parse 时抛 `IllegalArgumentException`，路径形如 `android.securityState.<key>`。布尔字段拒绝 null / String / Number；`biometricCanAuthenticateResult` 拒绝 null / String / Boolean / 小数 / 集合外整数；`biometricLastAuthenticationElapsedRealtimeMillis` 拒绝 null / String / Boolean / 小数 / `<-1`。配置对象不可变；**不**从其它字段或 time 配置推导；**不**校验 authenticator 语义；**不**保留 JSONObject。**不**为 `inKeyguardRestrictedInputMode` 增加独立配置键。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 接入 | 已配置 | 未配置 |
| --- | --- | --- | --- |
| `Application`/`Context.getSystemService(Ljava/lang/Class;)`（**仅** `KeyguardManager`；VarArg / VaList） | `callObjectMethod` / `V` | 返回同一 `SystemService("keyguard")`；lookup **不发** sidecar | 仍返回服务标记；lookup **不发** sidecar；随后 Keyguard getter 仍 UOE |
| `android/app/KeyguardManager->isKeyguardLocked()Z` | `callBooleanMethod` / `V` | 返回 `keyguardLocked` | UOE，无事件 |
| `android/app/KeyguardManager->inKeyguardRestrictedInputMode()Z` | 同上；**兼容别名** | 返回 `keyguardLocked`（与 `isKeyguardLocked` 相同值） | 同上 |
| `android/app/KeyguardManager->isKeyguardSecure()Z` | 同上 | 返回 `keyguardSecure` | 同上 |
| `android/app/KeyguardManager->isDeviceLocked()Z` | 同上 | 返回 `deviceLocked` | 同上 |
| `android/app/KeyguardManager->isDeviceSecure()Z` | 同上 | 返回 `deviceSecure` | 同上 |
| `android/hardware/biometrics/BiometricManager->canAuthenticate()I` | `callIntMethod` / `V` | 返回 `biometricCanAuthenticateResult`（固定标记） | 同上 |
| `android/hardware/biometrics/BiometricManager->canAuthenticate(I)I` | 同上 | 返回**同一** `biometricCanAuthenticateResult`（**不**随 arg0 authenticators 变化） | 同上 |
| `android/hardware/biometrics/BiometricManager->getLastAuthenticationTime(I)J` | `callLongMethod` / `V` | 返回 `biometricLastAuthenticationElapsedRealtimeMillis`（固定标记；**不**随 authenticators 变化） | 同上 |

**仅按精确签名命中**；`inKeyguardRestrictedInputMode` 为 `isKeyguardLocked` 的废弃公共别名。`canAuthenticate` / `getLastAuthenticationTime` 均为**固定结果标记**，**不**实现硬件探测、录入、认证流程、authenticator 语义校验或 AndroidX API。

### Sidecar（旁路事件，仅节点存在且命中时）

| 字段 | Keyguard 无参布尔 | `canAuthenticate()` | `canAuthenticate(I)` | `getLastAuthenticationTime(I)` |
| --- | --- | --- | --- | --- |
| `kind` | `android_security_state` | `android_security_state` | `android_security_state` | `android_security_state` |
| `api` | `KeyguardManager.isKeyguardLocked` / **`inKeyguardRestrictedInputMode`** / `isKeyguardSecure` / `isDeviceLocked` / `isDeviceSecure` | **`BiometricManager.canAuthenticate`** | **`BiometricManager.canAuthenticate`**（与无参 **同名**） | **`BiometricManager.getLastAuthenticationTime`** |
| `value` | **精确** `field=keyguardLocked\|…,result=true\|false` | **精确** `result=<code>` | **精确** `result=<code>,authenticators=<int>`（arg0 仅记入 value） | **精确** `result=<long>,authenticators=<int>`（arg0 仅记入 value） |
| `source` | `json-config` | `json-config` | `json-config` | `json-config` |
| `note` | 中文简述（锁屏锁定 / 兼容别名 / 安全锁屏 / 设备锁定 / 设备安全） | 中文简述（读取配置的生物识别可用性结果；固定标记） | 中文简述（固定标记，与 authenticators 无关） | 中文简述（读取配置的最近生物识别认证时间；固定标记，elapsedRealtime 毫秒；与 authenticators 无关） |

### 明确未实现

- AndroidX `androidx.biometric` 路径
- 硬件探测 / 录入 / 认证流程 / 回调
- authenticator 位语义校验；从 time 配置推导最近认证时间
- 其它 `BiometricManager` API（除上述 canAuthenticate / getLastAuthenticationTime）
- 锁屏回调 / 用户交互解锁
- 字段互相推导（各配置键保持独立）

## android.settings

Android Settings（安卓设置）数据库的可配置命名空间。节点路径为 `android.settings`，其下仅允许三个 namespace（命名空间）：`secure`、`system`、`global`。每个 namespace 是一个 JSONObject（JSON 对象）；每个 key（键）的值必须是 **String（字符串）或 JSON null（空值）**，禁止数字/布尔等其它类型。非法结构或非法值会在 `TraceEnvironmentConfig` parse（解析）时抛出 `IllegalArgumentException`，异常消息带配置路径（例如 `android.settings.secure.android_id`）。

### 字段说明

| 字段路径 | 说明 |
| --- | --- |
| `android.settings` | 总节点。不存在时 Settings 相关 JNI（Java 原生接口）保持未处理，最终仍为 `UnsupportedOperationException`。 |
| `android.settings.secure` | `Settings.Secure`（安全设置）命名空间。 |
| `android.settings.system` | `Settings.System`（系统设置）命名空间。 |
| `android.settings.global` | `Settings.Global`（全局设置）命名空间。 |
| `android.settings.<namespace>.<key>` | 某项设置的配置值。key 须为非空、无首尾空白的字符串。值为字符串时按接口类型解析；值为 `null` 表示显式配置为 null。 |
| 示例 `android.settings.secure.android_id` | Android ID（安卓设备标识符）字符串。 |
| 示例 `android.settings.secure.default_input_method` | 默认输入法组件名。 |
| 示例 `android.settings.system.screen_brightness` | 屏幕亮度（`getInt` 时解析为整数）。 |
| 示例 `android.settings.system.accelerometer_rotation` | 自动旋转开关。 |
| 示例 `android.settings.system.font_scale` | 字体缩放（`getFloat` 时解析为浮点）。 |
| 示例 `android.settings.system.ringtone` | 默认铃声名。键存在时 `Settings.System.getString` 与 `RingtoneManager.getActualDefaultRingtoneUri(TYPE_RINGTONE)` / `getRingtone` / `Ringtone.getTitle` 读该值。 |
| 示例 `android.settings.system.notification_sound` | 默认通知音。对应 `RingtoneManager.TYPE_NOTIFICATION`。 |
| 示例 `android.settings.system.alarm_alert` | 默认闹钟音。对应 `RingtoneManager.TYPE_ALARM`。 |
| 示例 `android.settings.global.adb_enabled` | ADB（安卓调试桥）开关。 |
| 示例 `android.settings.global.development_settings_enabled` | 开发者选项相关开关。 |

铃声回退：上述三个 Settings 键缺失时，可读 `android.properties` 的 `ro.config.ringtone` / `ro.config.notification_sound` / `ro.config.alarm_alert`。Settings 优先于 property。两者都缺时 RingtoneManager 不接管。

### JNI 支持的 API

拦截入口在 `AbstractJni`：

| API | 精确签名（每个 namespace 各一套） | 接入方法 | 行为摘要 |
| --- | --- | --- | --- |
| `getString` | `Settings$Secure/System/Global->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;` | `callStaticObjectMethod` / `callStaticObjectMethodV` | 仅当 config 存在且 key 已配置时处理；字符串原样返回 `StringObject`，显式 null 返回 null；missing key 返回 notHandled。 |
| `getInt` | `getInt(Landroid/content/ContentResolver;Ljava/lang/String;)I` 与 `getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I` | `callStaticIntMethod` / `callStaticIntMethodV` | 非 null 配置串 `Integer.parseInt`；显式 null 且带 default 返回 `args.getIntArg(2)`；显式 null 无 default 或 missing key 为 notHandled。 |
| `getLong` | `getLong(Landroid/content/ContentResolver;Ljava/lang/String;)J` 与 `getLong(Landroid/content/ContentResolver;Ljava/lang/String;J)J` | `callStaticLongMethod`（VarArg）/ `callStaticLongMethodV`（VaList；经 A 路由到 V 的路径同样命中） | 非 null 配置串 `Long.parseLong`（覆盖完整有符号 64 位范围）；显式 JSON null **仅**在带 long default 的重载上返回 `args.getLongArg(2)`，无 default 时 notHandled；**missing key 即使带 default 也始终 notHandled**；非法或超出 long 范围的值抛 `IllegalArgumentException`（消息含 namespace、key、原始 value）。 |
| `getFloat` | `getFloat(Landroid/content/ContentResolver;Ljava/lang/String;)F` 与 `getFloat(Landroid/content/ContentResolver;Ljava/lang/String;F)F` | `callStaticFloatMethod`（VarArg）/ `callStaticFloatMethodV`（VaList）/ `callStaticFloatMethodA`（jvalue→JValueList 后经 `callStaticFloatMethodV` 分发） | 非 null 配置串 `Float.parseFloat`，并拒绝 `Float.isNaN` / `Float.isInfinite`；显式 null 带 default 返回 `args.getFloatArg(2)`；其余 notHandled 规则同 `getInt`。 |

**说明：`getFloat` 的 VarArg、VaList 与 A（JValueList）路径均已接线，三条路径语义一致，均命中 JSON 配置。** 32 位 A 路径返回浮点位（R0），64 位 A 路径经 Q0 返回浮点。

### 命中与未处理规则

- Helper **先匹配完整签名，再读取 arg1**（key），避免零参静态方法因提前 `getObjectArg(1)` 越界。
- 仅当 `isAndroidSettingConfigured(namespace, key)` 为 true 时处理；key 缺失始终 notHandled（即使签名带 default）。
- 返回值用 `handled + result` 区分「合法返回 0 / 0f / 0L」与「未处理」。
- 解析失败（含 `getLong` 非法串与溢出）抛 `IllegalArgumentException`，消息含 namespace / key / value。
- 32 位与 64 位 emulator 均支持上述已接线路径（`getFloat` / `getLong` 均为 VarArg、VaList 与 A）。

### Sidecar（旁路事件）

命中时通过 `TraceEnvironmentEventSink.emit` 写入：

| 字段 | 值 |
| --- | --- |
| `kind` | `android_setting` |
| `api` | `Settings.<namespace>.getString` / `getInt` / `getLong` / `getFloat` |
| `value` | 含 key、原始 config 值与实际 result（`getString` 形如 `key=...`；`getInt`/`getLong`/`getFloat` 形如 `key=...,config=...,result=...`） |
| `source` | `json-config` |
| `note` | 中文说明，如「读取 Android 设置 …」 / 「读取 Android 长整型设置 …」 |

## network.interfaces

网卡接口列表，路径为根节点下的 `network.interfaces`（JSON 数组）。用于 Socket ioctl（输入输出控制）层的网卡枚举与查询，实现位置主要在 `SocketIO` / `AndroidSyscallHandler`。另在 `AbstractJni` 提供 **Java 层子集**：`NetworkInterface.getNetworkInterfaces` / `getByName` / `getByIndex` / `getByInetAddress` / `getName` / `getDisplayName` / `getIndex` / `getHardwareAddress` / `getInetAddresses` / `getMTU` / `isUp` / `isLoopback` / `isPointToPoint` / `supportsMulticast`，以及仅接口地址 marker 的 `InetAddress.getHostAddress`（复用必填 `ipv4` 与 `ConfiguredNetworkInterface` 标记）。节点存在时 `AndroidElfLoader` 另注册 `GetifaddrsHook`，拦截 Bionic `libc.so` 的 `getifaddrs` / `freeifaddrs`：数据**仅**来自本数组的 IPv4 / MAC / flags，**绝不**枚举宿主 `java.net.NetworkInterface`；节点缺失**不拦截**（回落既有 guest libc / netlink）。

### 字段说明

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `network.interfaces` | — | 数组节点。**节点缺失**时：ioctl 保留宿主机 `NetworkInterface` 枚举的旧行为；**Java JNI 子集**保持 `UnsupportedOperationException` 且**不发** sidecar；**native `getifaddrs`/`freeifaddrs` 不拦截**（回落既有 guest libc/netlink）。**显式空数组 `[]`** 表示已配置为空：ioctl 与 Java 枚举均返回空，`getifaddrs` 写 `*ifap=NULL` 并返回 `0`，且**绝不读取宿主机**。 |
| `network.interfaces[i].name` | 是 | 接口名（interface name），全局唯一，非空且无首尾空白。 |
| `network.interfaces[i].index` | 是 | 接口索引（interface index），整数，范围 `[1, Integer.MAX_VALUE]`，全局唯一。接口存在时另服务只读精确路径 `/sys/class/net/<name>/ifindex`（见下）。与可选 `linkIndex`（`/iflink`）**独立配置，不自动相等**。本字段范围**不**因 `network.ipv6Addresses` 收紧；仅当某条 IPv6 地址项引用该接口时，其 `index` 另须落入 `0..255`（见 `network.ipv6Addresses`），以便 `/proc/net/if_inet6` 输出固定两位小写 hex。 |
| `network.interfaces[i].ipv4` | 是 | IPv4（第四版互联网协议）点分十进制地址字符串。 |
| `network.interfaces[i].broadcast` | 否 | **IPv4 广播地址**（IPv4 broadcast address）；省略表示无 IPv4 广播。**不是**链路层广播，**不**服务 `/sys/class/net/<name>/broadcast`，也**不**与 `linkLayerBroadcast` 或 `mac` 互相推导。 |
| `network.interfaces[i].flags` | 否 | 网卡 flags（标志位），无符号整数，范围 `[0, 65535]`；省略时 ioctl 侧可能使用默认/未配置标志行为；**`getifaddrs` 省略则为 `0`，不发明 `IFF_UP`**。键存在时另服务只读精确路径 `/sys/class/net/<name>/flags`（见下）。 |
| `network.interfaces[i].mac` | 否 | MAC（硬件地址），六个冒号分隔的两位十六进制八位组，如 `02:54:52:41:43:45`（parse 入库 Locale.ROOT 小写）。键存在时另服务只读精确路径 `/sys/class/net/<name>/address`（见下）。`ioctl(SIOCGIFHWADDR)` **另须**同条显式 `hardwareType`，缺一不接管。 |
| `network.interfaces[i].mtu` | 否 | MTU（最大传输单元），整数，范围 `[68, 65536]`。键存在时另服务只读精确路径 `/sys/class/net/<name>/mtu`（见下）。 |
| `network.interfaces[i].displayName` | 否 | 显示名。**区分**键省略 vs 显式 `null`：非 null 须为长度 `1..128` 的非空 String；**不**从 `name` 推导。 |
| `network.interfaces[i].virtual` | 否 | 严格 JSON Boolean；**独立 presence**；**不**从 name/flags/地址/index 推导；键缺失时 `isVirtual()` JNI 保持 UOE |
| `network.interfaces[i].operState` | 否 | 可选。省略=未配置（presence false，不服务 sysfs）。显式值必须为 JSON String，且精确小写枚举之一：`unknown`、`notpresent`、`down`、`lowerlayerdown`、`testing`、`dormant`、`up`。`null`、非字符串、大小写变化、未知值均在 parse 时抛 `IllegalArgumentException`，路径含 `network.interfaces[i].operState`。**不**从 `flags` 或 `network.wifi` 推导。键存在时另服务只读精确路径 `/sys/class/net/<name>/operstate`（见下）。 |
| `network.interfaces[i].carrier` | 否 | 可选。省略=未配置（presence false，不服务 sysfs）。显式值必须为 JSON Boolean：`true`/`false` 均表示已配置。`null`、数字、字符串、数组、对象均在 parse 时抛 `IllegalArgumentException`，路径含 `network.interfaces[i].carrier`。**不**从 `operState`、`flags`、`network.wifi` 或其它接口字段推导。键存在时另服务只读精确路径 `/sys/class/net/<name>/carrier`（见下）。 |
| `network.interfaces[i].hardwareType` | 否 | 可选。省略=未配置（presence false，不服务 sysfs）。显式值必须为**精确 JSON Number 整数**，范围 `[0, 65535]`（Linux `ARPHRD_*`，如以太网 `1`、loopback `772`）。`null`、字符串、布尔、分数、越界均在 parse 时抛 `IllegalArgumentException`，路径含 `network.interfaces[i].hardwareType`。**不**从 `name`、`flags`、`mac`、`operState`、`carrier` 或 `network.wifi` 推导。键存在时另服务只读精确路径 `/sys/class/net/<name>/type`（见下）。`ioctl(SIOCGIFHWADDR)` **另须**同条显式有效 `mac`，缺一不接管；**不**按接口名/flags/operState/carrier 推导 family。 |
| `network.interfaces[i].speedMbps` | 否 | 可选。省略=未配置（presence false，不服务 sysfs）。显式值必须为**精确 JSON Number 整数**，范围 `[-1, Integer.MAX_VALUE]`。`-1` 明确表示链路速率未知（Linux `SPEED_UNKNOWN`）。须**区分**键省略与显式 `-1`/`0`/正数。`null`、字符串、布尔、分数、越界均在 parse 时抛 `IllegalArgumentException`，路径含 `network.interfaces[i].speedMbps`。**不**从 `network.wifi.linkSpeedMbps`、接口名、`flags`、`mac`、`hardwareType`、`operState`、`carrier`、`duplex` 或其它字段推导。键存在时另服务只读精确路径 `/sys/class/net/<name>/speed`（见下）。native 该路径与 Java `network.wifi.linkSpeedMbps`（`WifiInfo.getLinkSpeed()`）相互独立。 |
| `network.interfaces[i].duplex` | 否 | 可选。省略=未配置（presence false，不服务 sysfs）。显式值必须为 JSON String，且精确小写枚举之一：`full`、`half`、`unknown`。须**区分**键省略与显式值。`null`、非字符串、大小写变化、未知值均在 parse 时抛 `IllegalArgumentException`，路径含 `network.interfaces[i].duplex`。**不**从 `speedMbps`、`carrier`、`operState`、`hardwareType`、`flags`、接口名、`network.wifi` 或其它字段推导；与 `speedMbps`/`carrier`/`operState` **均独立、不自动推导**。键存在时另服务只读精确路径 `/sys/class/net/<name>/duplex`（见下）。 |
| `network.interfaces[i].linkIndex` | 否 | 可选。省略=未配置（presence false，不服务 sysfs `iflink`）。显式值必须为**精确 JSON Number 整数**，范围 `[1, Integer.MAX_VALUE]`。须**区分**键省略与显式值。`null`、字符串、布尔、分数、`0`、负数、越界均在 parse 时抛 `IllegalArgumentException`，路径含 `network.interfaces[i].linkIndex`。**独立于必填 `index`**：`iflink` 与 `ifindex` **分别独立配置，不自动相等**，也**不**从 `index`、接口名、`hardwareType`、`speedMbps`、`duplex`、`flags`、`carrier`、`operState`、MAC、`network.wifi` 或其它字段推导。键存在时另服务只读精确路径 `/sys/class/net/<name>/iflink`（见下）。示例中 `lo` 的 `linkIndex` 为 `1`、`wlan0` 的 `linkIndex` 为 `2`，即使与各自 `index` 数值相同，实现也不得把二者视为同一字段。 |
| `network.interfaces[i].txQueueLen` | 否 | 可选。省略=未配置（presence false，不服务 sysfs `tx_queue_len`）。显式值必须为**精确 JSON Number 整数**，范围 `[0, Integer.MAX_VALUE]`。须**区分**键省略与显式 `0`/正数。`null`、字符串、布尔、分数、负数、越界均在 parse 时抛 `IllegalArgumentException`，路径含 `network.interfaces[i].txQueueLen`。**与 `mtu`、`speedMbps` 独立、不自动推导**，也**不**从 `duplex`、`hardwareType`、`linkIndex`、`index`、`flags`、`carrier`、`operState`、接口名、`network.wifi` 或其它字段推导。键存在时另服务只读精确路径 `/sys/class/net/<name>/tx_queue_len`（见下）。示例中 `lo` 与 `wlan0` 的 `txQueueLen` 均为 `1000`。 |
| `network.interfaces[i].addressAssignType` | 否 | 可选。省略=未配置（presence false，不服务 sysfs `addr_assign_type`）。显式值必须为**精确 JSON Number 整数**，范围 `[0, 3]`（Linux 地址分配类型：`0`＝permanent、`1`＝random、`2`＝stolen、`3`＝set）。须**区分**键省略与显式 `0`。`null`、字符串、布尔、分数、负数、`4` 及越界均在 parse 时抛 `IllegalArgumentException`，路径含 `network.interfaces[i].addressAssignType`。**独立于 `mac`**：**不**从 `mac`、接口名、`hardwareType`、`flags`、`carrier`、`operState`、`speedMbps`、`duplex`、`linkIndex`、`txQueueLen`、`network.wifi` 或其它字段推导，也**不**根据 MAC 生成方式自动判断。键存在时另服务只读精确路径 `/sys/class/net/<name>/addr_assign_type`（见下）。示例中 `lo` 的 `addressAssignType` 为 `0`、`wlan0` 的 `addressAssignType` 为 `1`。 |
| `network.interfaces[i].nameAssignType` | 否 | 可选。省略=未配置（presence false，不服务 sysfs `name_assign_type`）。显式值必须为**精确 JSON Number 整数**，范围 `[0, 4]`（Linux `NET_NAME_*`：`0`＝unknown、`1`＝enum、`2`＝predictable、`3`＝user、`4`＝renamed）。须**区分**键省略与显式 `0`。`null`、字符串、布尔、分数、负数、`5` 及越界、溢出均在 parse 时抛 `IllegalArgumentException`，路径含 `network.interfaces[i].nameAssignType`。**不**从接口名、`mac`、`addressAssignType`、`hardwareType`、`flags`、`carrier`、`operState`、`speedMbps`、`duplex`、`linkIndex`、`txQueueLen`、`network.wifi` 或其它字段推导。键存在时另服务只读精确路径 `/sys/class/net/<name>/name_assign_type`（见下）。示例中 `lo` 的 `nameAssignType` 为 `0`、`wlan0` 的 `nameAssignType` 为 `4`。 |
| `network.interfaces[i].linkLayerBroadcast` | 否 | 可选。省略=未配置（presence false，**不**服务 sysfs `broadcast`）。显式值必须为**严格六字节 MAC 文本**，合法形式与规范化规则与 `mac` 相同（六个冒号分隔的两位十六进制八位组，parse 入库 Locale.ROOT 小写冒号格式）。`null`、空串、非字符串、错误长度、空白、IPv4、数组、浮点等均在 parse 时抛 `IllegalArgumentException`，路径含 `network.interfaces[i].linkLayerBroadcast`。**与 `mac`、IPv4 `broadcast`、`hardwareType`、`flags` 及其它接口字段完全独立：不推导、不做一致性限制**。键存在时另服务只读精确路径 `/sys/class/net/<name>/broadcast`（见下）。**不**实现写入。示例中 `wlan0` 的 `linkLayerBroadcast` 为 `ff:ff:ff:ff:ff:ff`。 |

parse（解析）时严格校验：非法类型、重复 name/index、非法 IPv4/MAC/范围、`virtual`/`carrier` 非 Boolean、`operState`/`duplex` 非精确小写枚举、`hardwareType`/`speedMbps`/`linkIndex`/`txQueueLen`/`addressAssignType`/`nameAssignType` 非精确整数、`linkLayerBroadcast` 非严格六字节 MAC 文本等会失败，异常消息始终带 `network.interfaces` 或 `network.interfaces[i].field` 路径。

### Java JNI 子集（`AbstractJni`）

**节点存在**（含 `[]`）时生效；**节点缺失** UOE、无事件。**不**读取宿主机 `java.net.NetworkInterface`。

| 精确签名 | 接入 | 已配置 | 未配置 |
| --- | --- | --- | --- |
| `java/net/NetworkInterface->getNetworkInterfaces()Ljava/util/Enumeration;` | `callStaticObjectMethod` / `callStaticObjectMethodV` | 每次返回**新** `Enumeration`，元素为**新** VM 拥有 `ConfiguredNetworkInterface`（配置 JSON 顺序）；`[]` → 空枚举 | UOE，无事件 |
| `java/net/NetworkInterface->getByName(Ljava/lang/String;)Ljava/net/NetworkInterface;` | `callStaticObjectMethod` / `callStaticObjectMethodV` | 节点存在时：`null` 名 → **NPE** 无事件；精确匹配 → **新** marker；未匹配/`[]` → 处理为 Java `null` 并发事件 | 节点缺失 → UOE 无事件；非 String 参数 → UOE 无事件 |
| `java/net/NetworkInterface->getByIndex(I)Ljava/net/NetworkInterface;` | `callStaticObjectMethod` / `callStaticObjectMethodV` | 节点存在时：index&lt;0 → **IAE** 无事件；精确匹配 index → **新** marker；未匹配/`[]` → 处理为 Java `null` 并发事件 | 节点缺失 → UOE 无事件 |
| `java/net/NetworkInterface->getByInetAddress(Ljava/net/InetAddress;)Ljava/net/NetworkInterface;` | `callStaticObjectMethod` / `callStaticObjectMethodV` | 节点存在时：arg **null** → **NPE** 无事件；**仅** 存活同 VM 接口 `getInetAddresses` 产出的 IPv4 marker → **新** `ConfiguredNetworkInterface`（同配置项） | 节点缺失 / DNS marker / 普通 InetAddress / 外来或过期地址 marker / 非 marker → UOE 无事件；**不**接受 String、**不**查宿主机 |
| `java/net/NetworkInterface->getName()Ljava/lang/String;` | `callObjectMethod` / `callObjectMethodV` | **仅** 本 VM 拥有且仍绑定**当前** `NetworkInterfaceConfig` 实例身份的标记 → 配置 `name` | 普通/外来/过期标记 → UOE，无事件 |
| `java/net/NetworkInterface->getDisplayName()Ljava/lang/String;` | `callObjectMethod` / `callObjectMethodV` | 同上且 **`displayName` 键已出现**：非 null → **新** `StringObject`；显式 null → 处理为 Java `null`（仍发事件）；**键省略不发明默认、不回落 name**，UOE 无事件 | 同上 |
| `java/net/NetworkInterface->getIndex()I` | `callIntMethod` / `callIntMethodV` | 同上 → 配置 `index` | 同上 |
| `java/net/NetworkInterface->getHardwareAddress()[B` | `callObjectMethod` / `callObjectMethodV` | 同上：有 `mac` → **新** 6 字节 `ByteArray`（非 String）；无 `mac` → **处理为** Java `null`（仍发事件） | 同上 |
| `java/net/NetworkInterface->getInetAddresses()Ljava/util/Enumeration;` | `callObjectMethod` / `callObjectMethodV` | 同上 → **新** `Enumeration`，恰 1 个 **新** VM 拥有接口 IPv4 `InetAddress` marker（必填 `ipv4`） | 同上 |
| `java/net/InetAddress->getHostAddress()Ljava/lang/String;` | `callObjectMethod` / `callObjectMethodV` | **仅** 上述接口地址 marker（本 VM + 父接口配置身份仍有效）→ 配置 `ipv4`；links DNS marker 仍走 `network_link` | 普通/外来/过期 marker → UOE 无事件 |
| `java/net/NetworkInterface->getMTU()I` | `callIntMethod` / `callIntMethodV` | 同上且 **`mtu` 键已显式配置** → 返回该值；**`mtu` 缺省不发明默认**，UOE 且无事件 | 同上 |
| `java/net/NetworkInterface->isUp()Z` | `callBooleanMethod` / `callBooleanMethodV` | 同上且 **`flags` 键已显式配置** → `(flags & 0x1) != 0`（Linux `IFF_UP`）；**`flags` 缺省不发明默认**，UOE 且无事件 | 同上 |
| `java/net/NetworkInterface->isLoopback()Z` | `callBooleanMethod` / `callBooleanMethodV` | 同上且 **`flags` 键已显式配置** → `(flags & 0x8) != 0`（Linux `IFF_LOOPBACK`）；**不**从 name/地址/index 推导；缺省 UOE 无事件 | 同上 |
| `java/net/NetworkInterface->isPointToPoint()Z` | `callBooleanMethod` / `callBooleanMethodV` | 同上且 **`flags` 键已显式配置** → `(flags & 0x10) != 0`（Linux `IFF_POINTOPOINT`）；**不**从 name/地址推导；缺省 UOE 无事件；sidecar `value=name=<name>,flags=<unsigned>,result=<true\|false>` | 同上 |
| `java/net/NetworkInterface->isVirtual()Z` | `callBooleanMethod` / `callBooleanMethodV` | 同上且 **`virtual` 键已显式配置** → 配置 Boolean；**不**从 name/flags 推导；缺省 UOE 无事件；sidecar `api=NetworkInterface.isVirtual`，`value=name=<name>,result=<true\|false>` | 同上 |
| `java/net/NetworkInterface->supportsMulticast()Z` | `callBooleanMethod` / `callBooleanMethodV` | 同上且 **`flags` 键已显式配置** → `(flags & 0x1000) != 0`（Linux `IFF_MULTICAST`）；缺省 UOE 无事件 | 同上 |

枚举迭代走既有泛型 `java/util/Enumeration->hasMoreElements()Z` / `nextElement()Ljava/lang/Object;`（VarArg 与 VaList）。

#### Sidecar（Java 子集，kind 与 ioctl 不同）

| 字段 | `getNetworkInterfaces` | `getByName` | `getByIndex` | `getByInetAddress` | `getName` | `getDisplayName` | `getIndex` | `getHardwareAddress` | `getInetAddresses` | `InetAddress.getHostAddress`（接口） | `getMTU` / flags 布尔 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `kind` | `network_interface` | `network_interface` | `network_interface` | `network_interface` | `network_interface` | `network_interface` | `network_interface` | `network_interface` | `network_interface` | `network_interface` | `network_interface` |
| `api` | `NetworkInterface.getNetworkInterfaces` | `NetworkInterface.getByName` | `NetworkInterface.getByIndex` | `NetworkInterface.getByInetAddress` | `NetworkInterface.getName` | `NetworkInterface.getDisplayName` | `NetworkInterface.getIndex` | `NetworkInterface.getHardwareAddress` | `NetworkInterface.getInetAddresses` | `InetAddress.getHostAddress` | 对应方法名 |
| `value` | `count=<n>` | `name=<name>,result=found\|null` | `index=<n>,result=found\|null` | `address=<ipv4>,result=found`（null 不发） | `name=<name>` | `displayName=<value>\|null` | `index=<n>` | `mac=<hex>\|null` | `count=1,addresses=[<ipv4>]` | `address=<ipv4>` | 既有格式 |
| `source` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` |
| `note` | 中文简述 | 中文简述 | 中文简述 | 中文（按地址查找配置的网络接口） | 中文简述 | 中文简述 | 中文简述 | 中文简述 | 中文（枚举配置的网络接口地址） | 中文（读取配置的网络接口 IPv4 地址） | 中文简述 |

**说明：** links DNS 的 `InetAddress.getHostAddress` 仍为 kind=`network_link`；`getByInetAddress` **不**识别 DNS marker。

**明确未实现（Java 侧）：** IPv6、broadcast 地址枚举、多地址、`InetAddress.getByName`、主机查找、按任意字符串/原始地址反查、组播 socket、parent/subinterfaces、stream API；不改 SocketIO。

### 支持的 ioctl（输入输出控制）

配置存在时，以下请求优先走 JSON 配置（32 位与 64 位 Android emulator 均支持）：

| ioctl 常量 | 作用 |
| --- | --- |
| `SIOCGIFCONF` | 枚举接口名与地址配置列表。 |
| `SIOCGIFFLAGS` | 按接口名查询 flags（网卡标志）。 |
| `SIOCGIFNAME` | 按索引查询接口名。 |
| `SIOCGIFADDR` | 按接口名查询 IPv4 地址。 |
| `SIOCGIFHWADDR` | 按接口名查询 `ifreq.ifr_hwaddr`。**仅当**目标接口同时具有显式有效 `mac` 与显式 `hardwareType` 时接管；缺一、接口未知或请求名不匹配则**不接管**，保留既有未命中（`ENODEV`）或不支持（`EOPNOTSUPP`）行为。**ARM32/ARM64 真实 `ioctl` 系统调用**在 fd 为 `SocketIO`（或其子类）且 `network.interfaces` 已配置时**同样保留**上述 errno，**不**覆盖为 `ENOTTY`。**不**从 name/flags/operState/carrier 推导 MAC 或 `hardwareType`，**不**读宿主机 `NetworkInterface`。 |
| `SIOCGIFMTU` | 按接口名查询 MTU（最大传输单元）。 |

错误语义（配置路径下）：

- **未知接口名**：返回失败并设置 `ENODEV`（无此设备）。经 ARM32/ARM64 真实 `ioctl` 系统调用入口、且 fd 为 `SocketIO`（或其子类）、`network.interfaces` 已配置时，**保留**该 `ENODEV`，**不**覆盖为 `ENOTTY`。
- **接口存在但 `mac` 或 `hardwareType` 缺一** 且请求 `SIOCGIFHWADDR`：**不接管**，既有不支持行为为 `EOPNOTSUPP`（操作不支持）。**真实系统调用同样保留** `EOPNOTSUPP`，**不**覆盖为 `ENOTTY`。
- **接口存在但 `mtu` 缺失** 且请求 `SIOCGIFMTU`：`EOPNOTSUPP`。

其它 ioctl、非 `SocketIO` 文件描述符、以及未配置 `network.interfaces` 的历史路径：`file.ioctl` 返回 `-1` 时系统调用层仍设为 `ENOTTY`。

未配置 `network.interfaces` 节点时：`SIOCGIFCONF` / `SIOCGIFFLAGS` / `SIOCGIFNAME` / `SIOCGIFADDR` 仍可走宿主机枚举；`SIOCGIFHWADDR` / `SIOCGIFMTU` 保持历史 `super.ioctl` 行为（不强制走配置逻辑）。

示例 `example/trace-env.example.json` 的 `lo` 与 `wlan0` 将 `mac` 与 `hardwareType` 并列写出：`ioctl(SIOCGIFHWADDR)` 需要二者同时存在才接管；只配其中一项则不接管。

### Sidecar（旁路事件）

相关 ioctl 成功/调用后由 `AndroidSyscallHandler` 写入：

| 字段 | 值 |
| --- | --- |
| `kind` | `network_device` |
| `api` | `ioctl(SIOCGIFCONF)` 等，括号内为请求名 |
| `value` | 含 `fd`、`request`、`ret`、接口名及 flags/addr/mtu 等摘要。**`SIOCGIFHWADDR` 成功接管除外**（见下） |
| `source` | 配置存在时为 `json-config`；未配置成功时为 `unidbg-default`；否则可能为 `fallback` |
| `note` | 中文，如「读取网卡信息 SIOCGIFCONF」 |

`ioctl(SIOCGIFHWADDR)` **成功接管**时由 `SocketIO` 写入（不经上述摘要，以免泄露 MAC/`hardwareType`）：

| 字段 | 值 |
| --- | --- |
| `kind` | `network_device` |
| `api` | `ioctl(SIOCGIFHWADDR)` |
| `value` | `name=<ifname>,format=ifreq-hwaddr,bytes=…`（**仅**这三项；**不**写 MAC、`hardwareType`、IPv4、flags、operState、carrier 或宿主信息） |
| `source` | `json-config` |
| `note` | 中文，如「读取配置的网卡 ifreq 硬件地址」（**不**含 MAC、`hardwareType`、IPv4、flags、operState、carrier 或宿主信息） |

缺字段、未知接口或名称不匹配时**不发**本条。

自动生成 `/sys/class/net/<name>/address`、`/mtu`、`/ifindex`、`/flags`、`/operstate`、`/carrier`、`/type`、`/speed`、`/duplex`、`/iflink`、`/tx_queue_len`、`/addr_assign_type`、`/name_assign_type` 或 `/broadcast` 成功时（与 ioctl 同 kind，靠 `api` 区分；**目前仅自动支持这十四条精确路径**）：

| 字段 | `address` | `mtu` | `ifindex` | `flags` | `operstate` | `carrier` | `type` | `speed` | `duplex` | `iflink` | `tx_queue_len` | `addr_assign_type` | `name_assign_type` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `kind` | `network_device` | `network_device` | `network_device` | `network_device` | `network_device` | `network_device` | `network_device` | `network_device` | `network_device` | `network_device` | `network_device` | `network_device` | `network_device` |
| `api` | `read("/sys/class/net/<name>/address")` | `read("/sys/class/net/<name>/mtu")` | `read("/sys/class/net/<name>/ifindex")` | `read("/sys/class/net/<name>/flags")` | `read("/sys/class/net/<name>/operstate")` | `read("/sys/class/net/<name>/carrier")` | `read("/sys/class/net/<name>/type")` | `read("/sys/class/net/<name>/speed")` | `read("/sys/class/net/<name>/duplex")` | `read("/sys/class/net/<name>/iflink")` | `read("/sys/class/net/<name>/tx_queue_len")` | `read("/sys/class/net/<name>/addr_assign_type")` | `read("/sys/class/net/<name>/name_assign_type")` |
| `value` | `path=…,name=<ifname>,format=address,bytes=…`（**不**写 MAC 原文） | `path=…,name=<ifname>,format=mtu,bytes=…`（**不**写 MAC，也**不**带入其它接口配置） | `path=…,name=<ifname>,format=ifindex,bytes=…`（**不**写 MAC、IPv4、flags 或其它接口配置） | `path=…,name=<ifname>,format=flags,bytes=…`（**不**写 flags 数值、MAC、IPv4 或其它接口配置） | `path=…,name=<ifname>,format=operstate,bytes=…`（**不**写 operState 枚举原文、MAC、IPv4、flags 或其它接口配置） | `path=…,name=<ifname>,format=carrier,bytes=…`（**不**写 carrier 的 `0`/`1` 或 `true`/`false`，也**不**写 operState、MAC、IPv4、flags、MTU 或其它接口配置） | `path=…,name=<ifname>,format=type,bytes=…`（**不**写 hardwareType 数值、MAC、IPv4 或其它接口配置） | `path=…,name=<ifname>,format=speed,bytes=…`（**不**写 `speedMbps` 数值，也**不**写 MAC、IPv4、flags、`hardwareType`、`operState`、`carrier`） | `path=…,name=<ifname>,format=duplex,bytes=…`（**不**写 duplex 原文 `full`/`half`/`unknown`，也**不**写 `speedMbps`、MAC、IPv4、flags、`hardwareType`、`operState`、`carrier`） | `path=…,name=<ifname>,format=iflink,bytes=…`（**不**写 `linkIndex` 数值，也**不**写 `index`、MAC、IPv4、flags、`hardwareType`、`speedMbps`、`duplex`、`operState`、`carrier`） | `path=…,name=<ifname>,format=tx_queue_len,bytes=…`（**不**写 `txQueueLen` 数值，也**不**写 IP、MAC、flags、`hardwareType`、`speedMbps`、`duplex`、`linkIndex`、`operState`、`carrier`） | `path=…,name=<ifname>,format=addr_assign_type,bytes=…`（**不**写 `addressAssignType` 数值，也**不**写 IP、MAC、flags、`hardwareType`、`speedMbps`、`duplex`、`linkIndex`、`txQueueLen`、`operState`、`carrier`） | `path=…,name=<ifname>,format=name_assign_type,bytes=…`（**不**写 `nameAssignType` 数值，也**不**写 IP、MAC、flags、`hardwareType`、`speedMbps`、`duplex`、`linkIndex`、`txQueueLen`、`addressAssignType`、`operState`、`carrier`） |
| `source` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` |
| `note` | 中文，如「读取配置的网卡 address 文件 …」 | 中文，如「读取配置的网卡 mtu 文件 …」 | 中文，如「读取配置的网卡 ifindex 文件 …」 | 中文，如「读取配置的网卡 flags 文件 …」（**不**含 flags 数值、MAC、IPv4） | 中文，如「读取配置的网卡 operstate 文件 …」（**不**含枚举原文、MAC、IPv4、flags） | 中文，如「读取配置的网卡 carrier 文件 …」（**不**含 `0`/`1`、`true`/`false`、operState、MAC、IPv4、flags、MTU） | 中文，如「读取配置的网卡 type 文件 …」（**不**含 hardwareType 数值、MAC、IPv4） | 中文，如「读取配置的网卡 speed 文件 …」（**不**含 `speedMbps` 数值、MAC、IPv4、flags、`hardwareType`、`operState`、`carrier`） | 中文，如「读取配置的网卡 duplex 文件 …」（**不**含 duplex 原文、`speedMbps`、MAC、IPv4、flags、`hardwareType`、`operState`、`carrier`） | 中文，如「读取配置的网卡 iflink 文件 …」（**不**含 `linkIndex` 数值、`index`、MAC、IPv4、flags、`hardwareType`、`speedMbps`、`duplex`、`operState`、`carrier`） | 中文，如「读取配置的网卡 tx_queue_len 文件 …」（**不**含 `txQueueLen` 数值、IP、MAC、flags、`hardwareType`、`speedMbps`、`duplex`、`linkIndex`、`operState`、`carrier`） | 中文，如「读取配置的网卡 addr_assign_type 文件 …」（**不**含 `addressAssignType` 数值、IP、MAC、flags、`hardwareType`、`speedMbps`、`duplex`、`linkIndex`、`txQueueLen`、`operState`、`carrier`） | 中文，如「读取配置的网卡 name_assign_type 文件 …」（**不**含 `nameAssignType` 数值、IP、MAC、flags、`hardwareType`、`speedMbps`、`duplex`、`linkIndex`、`txQueueLen`、`addressAssignType`、`operState`、`carrier`） |

`/sys/class/net/<name>/broadcast` 成功接管时（与上表同 kind，靠 `api` 区分；value **不含** path，也**不**写地址）：

| 字段 | 值 |
| --- | --- |
| `kind` | `network_device` |
| `api` | `read("/sys/class/net/<name>/broadcast")` |
| `value` | `name=<ifname>,format=broadcast,bytes=…`（**仅**这三项；**不**写链路层广播原文、IPv4 `broadcast`、MAC、`hardwareType`、`flags` 或其它接口值） |
| `source` | `json-config` |
| `note` | 中文，如「读取配置的网卡 broadcast 文件 …」（**不**含链路层广播原文、IPv4、MAC） |

`linux.files` 命中同路径时只发 `linux_file`，**不**发本条。

### 对 `/sys/class/net/<name>/address` 的影响

当 `network.interfaces` 节点存在，且某条目同时具有 `name` 与 `mac` 时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/address` 返回 UTF-8 小写规范 MAC 文本加一个 LF。只读（`ByteArrayFileIO` 写抛 `UnsupportedOperationException`）；**不**读取宿主机 `java.net.NetworkInterface`。

| 条件 | 行为 |
| --- | --- |
| 接口存在且 `mac` 已配置，路径精确为 `/sys/class/net/<name>/address` | UTF-8 `<canonical-mac>\n`（parse 已规范化小写） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 接口存在但 `mac` 缺省 / 其它 `/sys` 路径（含 `operstate`/`carrier`/`type`/`speed`、目录本身） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`。`operstate`/`carrier`/`type`/`speed`/`duplex` 由独立 `operState`/`carrier`/`hardwareType`/`speedMbps`/`duplex` 字段路径服务（见下），本路径不发明。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。

### 对 `/sys/class/net/<name>/mtu` 的影响

当 `network.interfaces` 节点存在，且某条目同时具有 `name` 与 `mtu` 时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/mtu` 返回 UTF-8 十进制 MTU 文本加一个 LF。只读（`ByteArrayFileIO` 写抛 `UnsupportedOperationException`）；**不**读取宿主机 `java.net.NetworkInterface`。

| 条件 | 行为 |
| --- | --- |
| 接口存在且 `mtu` 已配置，路径精确为 `/sys/class/net/<name>/mtu` | UTF-8 `<decimal-mtu>\n`（无前导零、单个 LF） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 接口存在但 `mtu` 缺省 / 其它 `/sys` 路径（含 `operstate`/`carrier`/`type`/`speed`、目录本身；`address` 仍仅由 mac 路径服务） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`。`operstate`/`carrier`/`type`/`speed`/`duplex` 由独立 `operState`/`carrier`/`hardwareType`/`speedMbps`/`duplex` 字段路径服务（见下），本路径不发明。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。

### 对 `/sys/class/net/<name>/ifindex` 的影响

当 `network.interfaces` 节点存在，且某条目具有 `name`（`index` 为必填配置字段）时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/ifindex` 返回 UTF-8 十进制 index 文本加一个 LF。只读（`ByteArrayFileIO` 写抛 `UnsupportedOperationException`）；**不**读取宿主机 `java.net.NetworkInterface`。本路径**只**服务必填 `index`，与可选 `linkIndex`（`/iflink`）**独立配置，不自动相等**。

| 条件 | 行为 |
| --- | --- |
| 接口存在，路径精确为 `/sys/class/net/<name>/ifindex` | UTF-8 `<decimal-index>\n`（无前导零、单个 LF） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 其它 `/sys` 路径（含 `operstate`/`carrier`/`type`/`speed`、目录本身；`address`/`mtu` 仍仅由各自路径服务） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`。`operstate`/`carrier`/`type`/`speed`/`duplex` 由独立 `operState`/`carrier`/`hardwareType`/`speedMbps`/`duplex` 字段路径服务（见下），本路径不发明。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。

### 对 `/sys/class/net/<name>/flags` 的影响

当 `network.interfaces` 节点存在，且某条目同时具有 `name` 与 `flags` 时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/flags` 返回 UTF-8 小写 `0x` 前缀十六进制文本加一个 LF（对齐 Linux 内核 `net/core/net-sysfs.c` 的 `fmt_hex`=`%#x\n`，无固定补零）。只读（`ByteArrayFileIO` 写抛 `UnsupportedOperationException`）；直接使用已有 `TraceEnvironmentConfig.NetworkInterfaceConfig.getFlags()`（显式字段可为 `null`，解析范围 `0..65535`），**不**新增 JSON 模型字段；**不**读取宿主机 `java.net.NetworkInterface`。

| 条件 | 行为 |
| --- | --- |
| 接口存在且 `flags` 已配置，路径精确为 `/sys/class/net/<name>/flags` | UTF-8 `0x<hex>\n`（小写、无固定补零、单个 LF；例如 `flags=4163` → `0x1043\n`） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 接口存在但 `flags` 缺省 / 其它 `/sys` 路径（含 `operstate`/`carrier`/`type`/`speed`、目录本身；`address`/`mtu`/`ifindex` 仍仅由各自路径服务） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`。`operstate`/`carrier`/`type`/`speed`/`duplex` 由独立 `operState`/`carrier`/`hardwareType`/`speedMbps`/`duplex` 字段路径服务（见下），本路径不发明、**不**从 flags 推导。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。

### 对 `/sys/class/net/<name>/operstate` 的影响

当 `network.interfaces` 节点存在，且某条目同时具有 `name` 与显式 `operState` 时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/operstate`、且**只读**打开时，返回 UTF-8 小写枚举文本加一个 LF（对齐 Linux 内核 `net/core/net-sysfs.c` 的 `operstates[]` + `\n`）。写方式或 `O_DIRECTORY` **不接管**，进入历史 open 链。使用 `NetworkInterfaceConfig.isOperStateConfigured()` / `getOperState()`；**不**从 `flags`、`network.wifi` 或其它接口字段推导；**不**读取宿主机 `java.net.NetworkInterface`。

| 条件 | 行为 |
| --- | --- |
| 接口存在且 `operState` 已显式配置，路径精确为 `/sys/class/net/<name>/operstate`，只读打开 | UTF-8 `<operstate>\n`（精确小写枚举、单个 LF；例如 `operState=up` → `up\n`） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 接口存在但 `operState` 缺省 / 写或目录打开 / 其它 `/sys` 路径（含 address/mtu/ifindex/flags/carrier/type/speed、目录本身） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。

### 对 `/sys/class/net/<name>/carrier` 的影响

当 `network.interfaces` 节点存在，且某条目同时具有 `name` 与显式 `carrier` 时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/carrier`、且**只读**打开时，返回 UTF-8 `1` 或 `0` 加一个 LF（对齐 Linux 内核 `net/core/net-sysfs.c` 的 `netif_carrier_ok` → `%d\n`）。写方式或 `O_DIRECTORY` **不接管**，进入历史 open 链。使用 `NetworkInterfaceConfig.isCarrierConfigured()` / `isCarrier()`；**不**从 `operState`、`flags`、`network.wifi` 或其它接口字段推导；**不**读取宿主机 `java.net.NetworkInterface`。

| 条件 | 行为 |
| --- | --- |
| 接口存在且 `carrier` 已显式配置，路径精确为 `/sys/class/net/<name>/carrier`，只读打开 | UTF-8 `1\n`（`true`）或 `0\n`（`false`）（单个 LF） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 接口存在但 `carrier` 缺省 / 写或目录打开 / 其它 `/sys` 路径（含 address/mtu/ifindex/flags/operstate/type/speed、目录本身） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。

### 对 `/sys/class/net/<name>/type` 的影响

当 `network.interfaces` 节点存在，且某条目同时具有 `name` 与显式 `hardwareType` 时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/type`、且**只读**打开时，返回 UTF-8 十进制整数文本加一个 LF（对齐 Linux 内核 `net/core/net-sysfs.c` 的 `type_show` → `%d\n`）。写方式或 `O_DIRECTORY` **不接管**，进入历史 open 链。使用 `NetworkInterfaceConfig.isHardwareTypeConfigured()` / `getHardwareType()`；**不**从 `name`、`flags`、`mac`、`operState`、`carrier`、`network.wifi` 或其它接口字段推导；**不**读取宿主机 `java.net.NetworkInterface`。

| 条件 | 行为 |
| --- | --- |
| 接口存在且 `hardwareType` 已显式配置，路径精确为 `/sys/class/net/<name>/type`，只读打开 | UTF-8 `<decimal>\n`（无前导零、单个 LF；例如 `hardwareType=1` → `1\n`，`hardwareType=772` → `772\n`） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 接口存在但 `hardwareType` 缺省 / 写或目录打开 / 其它 `/sys` 路径（含 address/mtu/ifindex/flags/operstate/carrier/speed、目录本身） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。

### 对 `/sys/class/net/<name>/speed` 的影响

当 `network.interfaces` 节点存在，且某条目同时具有 `name` 与显式 `speedMbps` 时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/speed`、且**只读**打开时，返回十进制 ASCII 整数文本加一个 LF（对齐 Linux 内核 `net/core/net-sysfs.c` 的 `speed_show` → `%d\n`）。`-1` 表示链路速率未知（`SPEED_UNKNOWN`）。写方式或 `O_DIRECTORY` **不接管**，进入历史 open 链。使用 `NetworkInterfaceConfig.isSpeedMbpsConfigured()` / `getSpeedMbps()`；**不**从 `network.wifi.linkSpeedMbps`、接口名、`flags`、`mac`、`hardwareType`、`operState`、`carrier`、`duplex` 或其它字段推导；**不**读取宿主机 `java.net.NetworkInterface`。native `/sys/class/net/<name>/speed` 与 Java `network.wifi.linkSpeedMbps`（`WifiInfo.getLinkSpeed()`）相互独立：示例中 `wlan0` 的 `866` 可与 Wi-Fi 链路速率写成相同数值，但实现**不得**自动关联。

| 条件 | 行为 |
| --- | --- |
| 接口存在且 `speedMbps` 已显式配置，路径精确为 `/sys/class/net/<name>/speed`，只读打开 | ASCII `<decimal>\n`（无前导零、单个 LF；例如 `speedMbps=866` → `866\n`，`speedMbps=-1` → `-1\n`，`speedMbps=0` → `0\n`） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 接口存在但 `speedMbps` 缺省 / 写或目录打开 / 其它 `/sys` 路径（含 address/mtu/ifindex/flags/operstate/carrier/type/duplex、目录本身） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。

### 对 `/sys/class/net/<name>/duplex` 的影响

当 `network.interfaces` 节点存在，且某条目同时具有 `name` 与显式 `duplex` 时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/duplex`、且**只读**打开时，返回 UTF-8 小写枚举文本加一个 LF（对齐 Linux 内核 `net/core/net-sysfs.c` 的 `duplex_show` → `%s\n`：`full`/`half`/`unknown`）。写方式或 `O_DIRECTORY` **不接管**，进入历史 open 链。使用 `NetworkInterfaceConfig.isDuplexConfigured()` / `getDuplex()`；**不**从 `speedMbps`、`carrier`、`operState`、`hardwareType`、`flags`、接口名、`network.wifi` 或其它字段推导；与 `speedMbps`/`carrier`/`operState` **均独立、不自动推导**；**不**读取宿主机 `java.net.NetworkInterface`。示例中 `lo` 为 `unknown`、`wlan0` 为 `full`，即使同条另有 `speedMbps`/`carrier`/`operState` 也不得据此改写 duplex。

| 条件 | 行为 |
| --- | --- |
| 接口存在且 `duplex` 已显式配置，路径精确为 `/sys/class/net/<name>/duplex`，只读打开 | UTF-8 `<duplex>\n`（精确小写枚举、单个 LF；例如 `duplex=full` → `full\n`，`duplex=unknown` → `unknown\n`，`duplex=half` → `half\n`） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 接口存在但 `duplex` 缺省 / 写或目录打开 / 其它 `/sys` 路径（含 address/mtu/ifindex/flags/operstate/carrier/type/speed、目录本身） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。

### 对 `/sys/class/net/<name>/iflink` 的影响

当 `network.interfaces` 节点存在，且某条目同时具有 `name` 与显式 `linkIndex` 时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/iflink`、且**只读**打开时，返回十进制 ASCII 整数文本加一个 LF（对齐 Linux 内核 `net/core/net-sysfs.c` 的 `iflink` → `%d\n`）。写方式或 `O_DIRECTORY` **不接管**，进入历史 open 链。使用 `NetworkInterfaceConfig.isLinkIndexConfigured()` / `getLinkIndex()`；**不**从必填 `index`、接口名、`hardwareType`、`speedMbps`、`duplex`、`flags`、`carrier`、`operState`、MAC、`network.wifi` 或其它字段推导。`iflink`（`linkIndex`）与 `ifindex`（`index`）**分别独立配置，不自动相等**：缺 `linkIndex` 时即使接口存在且 `index` 已填，也**不**服务 `/iflink`。**不**读取宿主机 `java.net.NetworkInterface`。示例中 `lo` 的 `linkIndex` 为 `1`、`wlan0` 的 `linkIndex` 为 `2`。

| 条件 | 行为 |
| --- | --- |
| 接口存在且 `linkIndex` 已显式配置，路径精确为 `/sys/class/net/<name>/iflink`，只读打开 | ASCII `<decimal>\n`（无前导零、单个 LF；例如 `linkIndex=1` → `1\n`，`linkIndex=2` → `2\n`） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 接口存在但 `linkIndex` 缺省 / 写或目录打开 / 其它 `/sys` 路径（含 address/mtu/ifindex/speed/duplex/type/operstate/carrier、目录本身） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。

### 对 `/sys/class/net/<name>/tx_queue_len` 的影响

当 `network.interfaces` 节点存在，且某条目同时具有 `name` 与显式 `txQueueLen` 时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/tx_queue_len`、且**只读**打开时，返回十进制 ASCII 整数文本加一个 LF（对齐 Linux 内核 `net/core/net-sysfs.c` 的 `tx_queue_len_show` → `%lu\n`）。写方式或 `O_DIRECTORY` **不接管**，进入历史 open 链。使用 `NetworkInterfaceConfig.isTxQueueLenConfigured()` / `getTxQueueLen()`；**与 `mtu`、`speedMbps` 独立、不自动推导**，也**不**从 `duplex`、`hardwareType`、`linkIndex`、必填 `index`、`flags`、`carrier`、`operState`、接口名、`network.wifi` 或其它字段推导。缺 `txQueueLen` 时即使接口存在且 `mtu`/`speedMbps` 已填，也**不**服务 `/tx_queue_len`。显式 `0` 会服务 `0\n`；键省略与显式 `0` 必须区分。**不**读取宿主机 `java.net.NetworkInterface`。示例中 `lo` 与 `wlan0` 的 `txQueueLen` 均为 `1000`。

| 条件 | 行为 |
| --- | --- |
| 接口存在且 `txQueueLen` 已显式配置，路径精确为 `/sys/class/net/<name>/tx_queue_len`，只读打开 | ASCII `<decimal>\n`（无前导零、单个 LF；例如 `txQueueLen=1000` → `1000\n`，`txQueueLen=0` → `0\n`） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 接口存在但 `txQueueLen` 缺省 / 写或目录打开 / 其它 `/sys` 路径（含 address/mtu/ifindex/speed/duplex/iflink/type/operstate/carrier、目录本身） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。

### 对 `/sys/class/net/<name>/addr_assign_type` 的影响

当 `network.interfaces` 节点存在，且某条目同时具有 `name` 与显式 `addressAssignType` 时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/addr_assign_type`、且**只读**打开时，返回十进制 ASCII 整数文本加一个 LF（对齐 Linux 内核 `net/core/net-sysfs.c` 的 `addr_assign_type` → `%d\n`）。写方式或 `O_DIRECTORY` **不接管**，进入历史 open 链。使用 `NetworkInterfaceConfig.isAddressAssignTypeConfigured()` / `getAddressAssignType()`。**独立于 `mac`**：本字段**不**从 `mac`、接口名、`hardwareType`、`flags`、`carrier`、`operState`、`speedMbps`、`duplex`、`linkIndex`、`txQueueLen`、`network.wifi` 或其它字段推导，也**不**根据 MAC 生成方式自动判断。缺 `addressAssignType` 时即使接口存在且 `mac` 已填，也**不**服务 `/addr_assign_type`。显式 `0` 会服务 `0\n`；键省略与显式 `0` 必须区分。含义按 Linux 地址分配类型：`0`＝permanent、`1`＝random、`2`＝stolen、`3`＝set。**不**读取宿主机 `java.net.NetworkInterface`。示例中 `lo` 的 `addressAssignType` 为 `0`、`wlan0` 的 `addressAssignType` 为 `1`。

| 条件 | 行为 |
| --- | --- |
| 接口存在且 `addressAssignType` 已显式配置，路径精确为 `/sys/class/net/<name>/addr_assign_type`，只读打开 | ASCII `<decimal>\n`（无前导零、单个 LF；例如 `addressAssignType=0` → `0\n`，`addressAssignType=1` → `1\n`） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 接口存在但 `addressAssignType` 缺省 / 写或目录打开 / 其它 `/sys` 路径（含 address/mtu/type/speed/duplex/iflink/ifindex/tx_queue_len/operstate/carrier、目录本身） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。


### 对 `/sys/class/net/<name>/name_assign_type` 的影响

当 `network.interfaces` 节点存在，且某条目同时具有 `name` 与显式 `nameAssignType` 时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/name_assign_type`、且**只读**打开时，返回十进制 ASCII 整数文本加一个 LF（对齐 Linux 内核 `net/core/net-sysfs.c` 的 `name_assign_type` → `%d\n`）。写方式或 `O_DIRECTORY` **不接管**，进入历史 open 链。使用 `NetworkInterfaceConfig.isNameAssignTypeConfigured()` / `getNameAssignType()`。本字段**不**从接口名、`mac`、`addressAssignType`、`hardwareType`、`flags`、`carrier`、`operState`、`speedMbps`、`duplex`、`linkIndex`、`txQueueLen`、`network.wifi` 或其它字段推导。缺 `nameAssignType` 时即使接口存在且 `mac`/`addressAssignType` 已填，也**不**服务 `/name_assign_type`。显式 `0` 会服务 `0\n`；键省略与显式 `0` 必须区分。含义按 Linux `NET_NAME_*`：`0`＝unknown、`1`＝enum、`2`＝predictable、`3`＝user、`4`＝renamed。**不**读取宿主机 `java.net.NetworkInterface`。示例中 `lo` 的 `nameAssignType` 为 `0`、`wlan0` 的 `nameAssignType` 为 `4`。

| 条件 | 行为 |
| --- | --- |
| 接口存在且 `nameAssignType` 已显式配置，路径精确为 `/sys/class/net/<name>/name_assign_type`，只读打开 | ASCII `<decimal>\n`（无前导零、单个 LF；例如 `nameAssignType=0` → `0\n`，`nameAssignType=4` → `4\n`） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 接口存在但 `nameAssignType` 缺省 / 写或目录打开 / 其它 `/sys` 路径（含 address/mtu/type/speed/duplex/iflink/ifindex/tx_queue_len/operstate/carrier/addr_assign_type、目录本身） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。

### 对 `/sys/class/net/<name>/broadcast` 的影响

当 `network.interfaces` 节点存在，且某条目同时具有 `name` 与显式 `linkLayerBroadcast` 时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确**路径 `/sys/class/net/<name>/broadcast`、且**只读**打开时，返回 UTF-8 规范小写 MAC 文本加一个 LF。写方式或 `O_DIRECTORY` **不接管**，进入历史 open 链。使用 `NetworkInterfaceConfig.isLinkLayerBroadcastConfigured()` / `getLinkLayerBroadcast()`。本字段是**链路层广播**（sysfs `broadcast`），**不是** IPv4 `broadcast`；**不**从 IPv4 `broadcast`、`mac`、`hardwareType`、`flags` 或其它接口字段推导，也**不做**一致性限制。缺 `linkLayerBroadcast` 时即使接口存在且 `mac` / IPv4 `broadcast` 已填，也**不**服务 `/broadcast`。**不**实现写入。**不**读取宿主机 `java.net.NetworkInterface`。示例中 `wlan0` 的 `linkLayerBroadcast` 为 `ff:ff:ff:ff:ff:ff`。

| 条件 | 行为 |
| --- | --- |
| 接口存在且 `linkLayerBroadcast` 已显式配置，路径精确为 `/sys/class/net/<name>/broadcast`，只读打开 | UTF-8 `<canonical-mac>\n`（parse 已规范化小写冒号格式、单个 LF；例如 `FF:FF:FF:FF:FF:FF` → `ff:ff:ff:ff:ff:ff\n`） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径 |
| 节点缺失 / 接口名不存在 / 接口存在但 `linkLayerBroadcast` 缺省 / 写或目录打开 / 其它 `/sys` 路径（含 address/mtu/type/speed/duplex/iflink/ifindex/tx_queue_len/operstate/carrier/addr_assign_type/name_assign_type、目录本身） | **不接管**，保留旧 open |

**不**实现写、目录枚举、IPv6、`/proc/net/*`，也**不**从 `mac` 或 IPv4 `broadcast` 推导地址。native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`。

### native `getifaddrs` / `freeifaddrs`（Bionic `libc.so`）

当 `network.interfaces` 节点存在（含显式 `[]`）时，`AndroidElfLoader` 注册 `GetifaddrsHook`，精确拦截 `libc.so` 的 `getifaddrs` 与 `freeifaddrs`。数据**只**读本数组，**绝不**调用宿主 `NetworkInterface.getNetworkInterfaces()`，也**不**从 `network.ipv6Addresses` / `network.links` / `network.wifi` 推导。ARM32 与 ARM64 均支持。

| 条件 | 行为 |
| --- | --- |
| 节点缺失 | **不拦截**，回落既有 guest libc / netlink |
| 显式 `[]` | `*ifap = NULL`，返回 `0` |
| 有条目 | 按 JSON 接口顺序构造 Bionic `ifaddrs` 链表 |
| `ifap == NULL` | 返回 `-1`，`errno=EFAULT`，**不**回落宿主 |

链表规则：

- 每个接口：若该条有显式 `mac`，先写一条 `AF_PACKET`（Linux 值 `17`，`sockaddr_ll` 20 字节）；再写一条 `AF_INET`（`sockaddr_in` 16 字节，必填 `ipv4`）。
- `ifa_name` 指向配置 `name`（同一接口的多条记录共享同一名字指针）。
- `ifa_flags`：显式 `flags`；**省略为 `0`，不发明 `IFF_UP`/`IFF_RUNNING`**。
- `ifa_netmask`：恒为 `NULL`（**不**从 `network.links.netmaskIpv4` 或其它字段推导）。
- `ifa_broadaddr`：仅 IPv4 条目且该条有显式 IPv4 `broadcast` 时写入 `sockaddr_in`；否则 `NULL`。**不是** `linkLayerBroadcast`。
- `ifa_data`：`NULL`。
- `sockaddr_ll.sll_ifindex`：必填 `index`；`sll_hatype`：显式 `hardwareType`，省略为 `0`（**不**从 name/mac 推导）；`sll_protocol`/`sll_pkttype` 为 `0`；`sll_halen=6`。
- ARM32 `sizeof(struct ifaddrs)=28`；ARM64 `=56`（`unsigned ifa_flags` 后 4 字节对齐填充）。
- **不**输出 IPv6（`/proc/net/if_inet6` 见 `network.ipv6Addresses`）。

`freeifaddrs`：仅释放本 hook 成功 `getifaddrs` 分配的链表头（单 arena）；`NULL` 为空操作；外来指针回落原符号。**不发** sidecar。

成功 `getifaddrs` 的 sidecar：

| 字段 | 值 |
| --- | --- |
| `kind` | `network_device` |
| `api` | `getifaddrs` |
| `value` | `result=0,interfaces=<接口数>,entries=<链表节点数>`（**仅**这三项；**不**写接口名、IPv4、MAC、flags、`hardwareType` 或宿主信息） |
| `source` | `json-config` |
| `note` | 中文，如「读取配置的网卡地址列表（native getifaddrs）」（**不含** IP/MAC） |

`ifap == NULL` 或节点缺失时**不发**本条。

**明确未实现：** `getifaddrs` 的 IPv6 条目、netmask、`ifa_data` 统计、netlink 快照、多 IPv4、写接口。

### 尚未覆盖（勿与本节点混淆）

本节点**不**覆盖 Java/JNI IPv6、`getifaddrs` 的 IPv6 条目 / netmask / `ifa_data`、Java 层 `status` 及其余 `NetworkInterface` API（仅 `getNetworkInterfaces`/`getByName`/`getByIndex`/`getByInetAddress`/`getName`/`getDisplayName`/`getIndex`/`getHardwareAddress`/`getInetAddresses`/`getMTU`/`isUp`/`isLoopback`/`isPointToPoint`/`isVirtual`/`supportsMulticast` 及接口 `InetAddress.getHostAddress` 子集已落地；native `getifaddrs`/`freeifaddrs` 的 IPv4/MAC/flags 子集见上一小节）、子接口、`network.links`，也不自动生成 `/proc/net/*`（结构化 `/proc/net/route` 见 `network.ipv4Routes`；结构化 `/proc/net/dev` 见 `network.interfaceStats`，**不**从本节点推导任何计数；结构化 `/proc/net/if_inet6` 见 `network.ipv6Addresses`，**仅**复用同名接口必填 `index`（被 `ipv6Addresses` 引用时该 `index` 须 `0..255`；普通未引用接口 `index` 仍为 `[1, Integer.MAX_VALUE]`），地址/前缀/scope/flags **不**从本节点推导；结构化 `/proc/net/igmp` 见 `network.igmpMemberships`，**仅**复用同名接口必填 `index` 作为 Idx，组/Querier/users/timer/reporter **不**从本节点推导；结构化 `/proc/net/igmp6` 见 `network.igmp6Memberships`，**仅**复用同名接口必填 `index`，组/users/flags/timer **不**从本节点推导；结构化 `/proc/net/dev_mcast` 见 `network.linkLayerMulticastEntries`，**仅**复用同名接口必填 `index`，二层多播 MAC/引用数/`globalUse` **不**从本节点 `mac`/`linkLayerBroadcast` 或其它字段推导，也**不**实现真实多播加入；结构化 `/proc/net/wireless` 见 `network.wirelessProcStats`，**不**从本节点或 `network.wifi` 推导，也**不**实现 ioctl/netlink/扫描）与 **除** `/sys/class/net/<name>/address`、`/mtu`、`/ifindex`、`/flags`、`/operstate`、`/carrier`、`/type`、`/speed`、`/duplex`、`/iflink`、`/tx_queue_len`、`/addr_assign_type`、`/name_assign_type` **和** `/broadcast` **以外** 的 `/sys/class/net/*`（native 自动路径**目前仅**这十四条；其余仍可用 `linux.files` 手工提供）。sysfs `operstate` **仅**来自显式 `operState`，sysfs `carrier` **仅**来自显式 `carrier`，sysfs `type` **仅**来自显式 `hardwareType`，sysfs `speed` **仅**来自显式 `speedMbps`，sysfs `duplex` **仅**来自显式 `duplex`，sysfs `iflink` **仅**来自显式 `linkIndex`（与必填 `index` / sysfs `ifindex` **独立、不自动相等**），sysfs `tx_queue_len` **仅**来自显式 `txQueueLen`（与 `mtu`/`speedMbps` **独立、不自动推导**），sysfs `addr_assign_type` **仅**来自显式 `addressAssignType`（与 `mac` **独立、不**根据 MAC 生成方式判断），sysfs `name_assign_type` **仅**来自显式 `nameAssignType`（**不**从接口名/`mac`/`addressAssignType`/`hardwareType`/`flags`/`carrier`/`operState`/`speedMbps`/`duplex`/`linkIndex`/`txQueueLen`/`network.wifi` 推导），sysfs `broadcast` **仅**来自显式 `linkLayerBroadcast`（链路层广播，**不是** IPv4 `broadcast`，**不**从 `mac`/IPv4 `broadcast`/`hardwareType`/`flags` 推导、**不**实现写入），前述字段**不**互相推导，也**不**从 name/flags/wifi（含 `network.wifi.linkSpeedMbps`）推导。Wi-Fi 连接画像见下一节 `network.wifi`；蓝牙适配器子集见 `network.bluetooth`。

## network.wifi

当前连接的 Wi-Fi（无线网络）画像。路径为 `network.wifi`（可选 JSONObject）。存在时仅允许白名单字段；整型字段须为 **精确 JSON Number**（不接受数字字符串）；非法键/类型/范围在 parse 时失败，路径形如 `network.wifi.rssi`。JNI 另支持既有 `Application`/`Context.getSystemService("wifi")` 与 **仅** `WifiManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService`（`wifi` → `WifiManager`）标记（**不**要求 `network.wifi` 节点；lookup **不读**环境、**不发** sidecar）。`Context.getSystemService(String)` 对已知服务名与 `Application` 相同（直接返回 marker）；未知名维持既有 `SystemService` 行为。

**缺失行为：** `network` 或 `wifi` 节点缺失，或某字段未配置时，对应 JNI 保持 notHandled，最终仍为原有 `UnsupportedOperationException`（不伪造默认 SSID/IP/状态）。至少配置任一字段后，`WifiManager.getConnectionInfo()` 才返回 `WifiInfo` 对象。`Application`/`Context.getSystemService(WifiManager.class)` 与 `"wifi"` 字符串路径在节点缺失时仍返回同一 `SystemService` 标记。

**显式 null：** 仅 `ssid` / `bssid` / `macAddress` / `ipv4` 允许 JSON `null`（配置存在则 JNI 对字符串返回 Java `null`；`getIpAddress()` 对 null `ipv4` 返回 `0`）。`enabled`、`state` 与全部其它 int 字段 **不允许** null。

**独立性：** `enabled` 与 `state`（`WifiManager.WIFI_STATE_*`）**彼此独立、不互相推导**；例如可配置 `enabled=false` 且 `state=3`（ENABLED）。

### 字段说明

| 字段路径 | 必填 | 类型与校验 | 影响的 Java 方法 |
| --- | --- | --- | --- |
| `network.wifi` | — | 可选 JSONObject | 见下行 API |
| `network.wifi.enabled` | 否 | Boolean（不可 null） | `WifiManager.isWifiEnabled()` |
| `network.wifi.state` | 否 | 精确整数 `0..4`（`WIFI_STATE_DISABLING`..`UNKNOWN`）；**无**默认；**不**从 `enabled` 推导 | `WifiManager.getWifiState()` |
| `network.wifi.ssid` | 否 | 非空无首尾空白 String，或 JSON null；允许 TRACEAI marker | `WifiInfo.getSSID()` |
| `network.wifi.bssid` | 否 | 六段冒号 MAC 或 JSON null；parse 后 Locale.ROOT 小写 | `WifiInfo.getBSSID()` |
| `network.wifi.macAddress` | 否 | 同上 | `WifiInfo.getMacAddress()` |
| `network.wifi.ipv4` | 否 | 严格点分 IPv4 或 JSON null（无 DNS） | `WifiInfo.getIpAddress()` |
| `network.wifi.rssi` | 否 | 精确整数 `-127..0` | `WifiInfo.getRssi()` |
| `network.wifi.linkSpeedMbps` | 否 | 精确整数 `0..100000` | `WifiInfo.getLinkSpeed()`。**仅** Java 层；与 native `/sys/class/net/<name>/speed`（显式 `network.interfaces[].speedMbps`）相互独立，互不推导 |
| `network.wifi.frequencyMhz` | 否 | 精确整数 `0..100000` | `WifiInfo.getFrequency()` |
| `network.wifi.networkId` | 否 | 精确整数 `-1..Integer.MAX_VALUE` | `WifiInfo.getNetworkId()` |
| `network.wifi.scanResults` | 否 | JSONArray。键缺失不接管；显式 `[]` 为权威空快照。每项可选 `ssid`（String 或 null）、`bssid`（六段冒号 MAC 或 null）、`rssi`（精确整数 `-127..0`）、`frequencyMhz`（精确整数 `0..100000`）。**不**从当前连接 `ssid`/`bssid`/`rssi` 推导 | `WifiManager.getScanResults()` |

### JNI 支持的 API

拦截入口在 `AbstractJni`（`callObjectMethod`/`V`、`callBooleanMethod`/`V`、`callIntMethod`/`V`）：

| Java 方法 | 精确签名 | 行为摘要 |
| --- | --- | --- |
| `Application`/`Context.getSystemService(String)` | `(Ljava/lang/String;)Ljava/lang/Object;` | 参数 `"wifi"` 时返回既有 `SystemService`（类型 `WifiManager`）；**不**要求 `network.wifi`；lookup **不读**环境、**不发** sidecar |
| `Application`/`Context.getSystemService(Class)` | `(Ljava/lang/Class;)Ljava/lang/Object;` | 第 0 参为 **`DvmClass`** 且类名为 `android/net/wifi/WifiManager` 时，返回与字符串路径**同一** `SystemService("wifi")`；null / 非 DvmClass / 其它 Class → UOE 无 sidecar；**不**要求 `network.wifi`；lookup **不发** sidecar |
| `WifiManager.getConnectionInfo()` | `()Landroid/net/wifi/WifiInfo;` | 任一 wifi 字段已配置时返回 `WifiInfo` 实例 |
| `WifiManager.isWifiEnabled()` | `()Z` | 读取 `enabled`（键缺失 UOE） |
| `WifiManager.getWifiState()` | `()I` | 读取固定 `state`（0..4；键缺失 UOE；**不**从 `enabled` 推导） |
| `WifiManager.getScanResults()` | `()Ljava/util/List;` | `scanResults` 键存在才接管；返回同 VM `ScanResult` 列表（字段 `SSID`/`BSSID`/`level`/`frequency`）；sidecar `count=<n>` |
| `WifiInfo.getSSID()` / `getBSSID()` / `getMacAddress()` | `()Ljava/lang/String;` | 读对应字符串；显式 null → Java null |
| `WifiInfo.getIpAddress()` | `()I` | 点分 IPv4 `a.b.c.d` 转为 Android 小端整型 `a\|(b<<8)\|(c<<16)\|(d<<24)`；配置 null → `0` |
| `WifiInfo.getRssi()` | `()I` | `rssi` |
| `WifiInfo.getLinkSpeed()` | `()I` | `linkSpeedMbps`（Mbps） |
| `WifiInfo.getFrequency()` | `()I` | `frequencyMhz`（MHz） |
| `WifiInfo.getNetworkId()` | `()I` | `networkId` |

命中时 sidecar kind=`network_wifi`、source=`json-config`、api=`WifiManager.*` / `WifiInfo.*`。`getWifiState` 的 value 形如 `key=state,result=<n>`，note 为中文「读取配置的 Wi-Fi 状态码」。类型化 / 字符串 `getSystemService` **不**发 sidecar。

### 明确未覆盖

安全类型、已配置网络列表、`setWifiEnabled`、Wi-Fi 广播/`WIFI_STATE_CHANGED`、状态机迁移、`WifiNetworkSpecifier` / `WifiNetworkSuggestion`。扫描结果见上表 `network.wifi.scanResults`（固定 JSON 快照，**不是**真实扫描）。只读 `/proc/net/wireless` 见 `network.wirelessProcStats`（本节点**不**推导该文本，也**不**实现无线 ioctl/netlink）。`ConnectivityManager` / `LinkProperties` / DHCP 投影见下一节 `network.links`（及与 `wifi.ipv4` 的交叉字段）。蓝牙适配器见 `network.bluetooth`。`network.wifi.linkSpeedMbps` **不**生成 native `/sys/class/net/<name>/speed`（该路径仅来自显式 `network.interfaces[].speedMbps`，二者相互独立）。

## network.bluetooth

可选蓝牙适配器画像子集（**六字段**）：`name` / `address` / `enabled` / `state` / `scanMode` / `discovering`，各字段**独立** presence 配置。路径为 `network.bluetooth`（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.NetworkBluetoothConfig`；JNI 接线在 `AbstractJni`：静态 `callStaticObjectMethod` / `V`（`getDefaultAdapter`）与实例 `callObjectMethod` / `V`（`getName`/`getAddress`/`BluetoothManager.getAdapter`）、`callBooleanMethod` / `V`（`isEnabled`/`isDiscovering`）、`callIntMethod` / `V`（`getState`/`getScanMode`）；兼容 `Context.BLUETOOTH_SERVICE`、`Application.getSystemService(String)`，以及**仅** `BluetoothManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService`（`bluetooth` → `BluetoothManager`）标记。

**节点缺失 vs 显式空对象 vs 有字段：**

| 状态 | `isNetworkBluetoothConfigured()` | `hasAnyFieldConfigured()` | 行为摘要 |
| --- | --- | --- | --- |
| **节点缺失** | `false` | — | 下列 `BluetoothAdapter` 方法 UOE，**不发** sidecar |
| **显式 `{}`** | `true` | `false` | `getDefaultAdapter` 仍 UOE（无任一字段），无事件 |
| **至少一字段** | `true` | `true` | `getDefaultAdapter` 返回私有 marker；多数 getter 仅在对应字段已配时命中；**`isDiscovering` 在 marker 上键缺失时默认 `false`** |

### 字段说明（仅允许下列 6 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `name` | 否 | 非空无首尾空白 String，或 JSON null | 字段未配置 → 对应 getter UOE |
| `address` | 否 | 六段冒号 MAC（parse 后 Locale.ROOT 小写），或 JSON null | 同上 |
| `enabled` | 否 | 严格 JSON Boolean（**不可** null） | 同上 |
| `state` | 否 | 精确 JSON Number 整数，**仅** `{10,11,12,13}`（`STATE_OFF` / `TURNING_ON` / `ON` / `TURNING_OFF`） | 同上 |
| `scanMode` | 否 | 精确 JSON Number 整数，**仅** `{20,21,23}`（`SCAN_MODE_NONE` / `CONNECTABLE` / `CONNECTABLE_DISCOVERABLE`） | 同上 |
| `discovering` | 否 | 严格 JSON Boolean（**不可** null）；**不**从 `enabled`/`state` 推导 | marker 上键缺失 → `isDiscovering()` 返回 **`false`**（并发 sidecar） |

未知键在 parse 时抛 `IllegalArgumentException`，路径形如 `network.bluetooth.<key>`。`name` 拒绝 Number/Boolean/空串/首尾空白；`address` 拒绝非法 MAC（须复用与 `network.wifi`/`interfaces` 相同的六段冒号 hex 校验）；`enabled`/`discovering` 拒绝 null / String / Number；`state`/`scanMode` 拒绝 null / String / Boolean / 小数 / 集合外整数。配置对象不可变；**不**从 `enabled` 或其它字段推导 `state`/`scanMode`/`discovering`；**不**保留 JSONObject。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 接入 | 已配置条件 | 行为 |
| --- | --- | --- | --- |
| `android/content/Context->BLUETOOTH_SERVICE:Ljava/lang/String;` | `getStaticObjectField` | 始终 | 返回 **`"bluetooth"`** |
| `android/app/Application->getSystemService(Ljava/lang/String;)Ljava/lang/Object;` | 实例 `callObjectMethod` / `V` | 参数为 `"bluetooth"` | 返回 `SystemService`（类型 `BluetoothManager`）；**不**要求 `network.bluetooth` |
| `android/app/Application->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;` / `android/content/Context->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;` | 实例 `callObjectMethod` / `V` | 第 0 参为 **`DvmClass`** 且类名为 `android/bluetooth/BluetoothManager` | 返回与字符串路径**同一** `SystemService("bluetooth")`；null / 非 DvmClass / 其它 Class → UOE 无 sidecar；**不**要求 `network.bluetooth` |
| `android/bluetooth/BluetoothManager->getAdapter()Landroid/bluetooth/BluetoothAdapter;` | 实例 `callObjectMethod` / `V` | **SystemService bluetooth 接收者** + 至少一字段已配置 | 与 `getDefaultAdapter` **同一**私有 `ConfiguredBluetoothAdapter` 语义 |
| `android/bluetooth/BluetoothAdapter->getDefaultAdapter()Landroid/bluetooth/BluetoothAdapter;` | 静态 `callStaticObjectMethod` / `V` | 至少一字段已配置 | 返回带私有 marker 的 `BluetoothAdapter` |
| `android/bluetooth/BluetoothAdapter->getName()Ljava/lang/String;` | 实例 `callObjectMethod` / `V` | marker + `name` 已配置 | 返回 String；显式 null → Java null |
| `android/bluetooth/BluetoothAdapter->getAddress()Ljava/lang/String;` | 同上 | marker + `address` 已配置 | 返回小写 MAC；显式 null → Java null |
| `android/bluetooth/BluetoothAdapter->isEnabled()Z` | 实例 `callBooleanMethod` / `V` | marker + `enabled` 已配置 | 返回配置布尔 |
| `android/bluetooth/BluetoothAdapter->isDiscovering()Z` | 同上 | **仅** marker（`ConfiguredBluetoothAdapter`） | 返回 `discovering`；键缺失 → **`false`** |
| `android/bluetooth/BluetoothAdapter->getState()I` | 实例 `callIntMethod` / `V` | marker + `state` 已配置 | 返回固定 `state` 标记 |
| `android/bluetooth/BluetoothAdapter->getScanMode()I` | 同上 | marker + `scanMode` 已配置 | 返回固定 `scanMode` 标记 |

**仅按精确签名命中**；非 marker 实例 / 非 SystemService 的 `BluetoothManager` / 节点缺失 → UOE 且**不发** sidecar。`isDiscovering` 与多数其它 getter 不同：在 **marker 存在** 时键缺失返回 `false` 并发事件，**不** UOE。`getState`/`getScanMode`/`isDiscovering` 为**固定结果标记**，**不**随 `enabled` 变化，**不**模拟真实扫描过程。

**provenance 隔离：** `ConfiguredBluetoothAdapter` 必须属于当前 `BaseVM`（创建时绑定 owner）；跨 VM 不接管、不读取另一 VM 的 `network.bluetooth` 且无 sidecar。

### Sidecar（旁路事件，仅命中时）

| 字段 | `getDefaultAdapter` / `BluetoothManager.getAdapter` | `getName` / `getAddress` / `isEnabled` / `isDiscovering` / `getState` / `getScanMode` |
| --- | --- | --- |
| `kind` | `network_bluetooth` | `network_bluetooth` |
| `api` | **`BluetoothAdapter.getDefaultAdapter`** / **`BluetoothManager.getAdapter`** | **`BluetoothAdapter.getName`** / **`getAddress`** / **`isEnabled`** / **`isDiscovering`** / **`getState`** / **`getScanMode`** |
| `value` | **精确** `BluetoothAdapter` | **精确** `key=<key>,value=<value>`（字符串显式 null 写作 `value=null`；int 为十进制） |
| `source` | `json-config` | `json-config` |
| `note` | 中文简述（返回配置的 BluetoothAdapter 标记对象；Manager 路径注明通过 BluetoothManager） | 中文简述（名称 / MAC / 开关状态 / 发现中固定标记缺省 false / 适配器状态 / 扫描模式） |

### 明确未实现

- 真实扫描启动/停止、配对 / 绑定设备列表 / 广播
- 状态机切换（`TURNING_ON`/`OFF` 仅为可配置常量，不驱动真实转换）
- 广义 `getSystemService(Class)`（蓝牙侧仅 `BluetoothManager`；`WifiManager` 见 `network.wifi`；`ConnectivityManager` 见 `network.links`；其它 Class 仍 UOE）
- 其它 `BluetoothManager` 方法（已连设备等）
- AndroidX 蓝牙路径
- 从 `enabled` / Wi-Fi / 其它节点推导 `state`/`scanMode`/`discovering`

## network.links

活动链路（active network link）画像。路径为 `network.links`（可选 JSONObject）。存在时仅允许白名单字段；整型字段须为 **精确 JSON Number**（不接受数字字符串）；非法键/类型/范围在 parse 时失败，路径形如 `network.links.mtu`、`network.links.dnsServers[i]`。JNI 另支持既有 `Application.getSystemService("connectivity")` 与 **仅** `ConnectivityManager` 的类型化 `Application`/`Context.getSystemService(Class)` → 同一 `SystemService`（`connectivity` → `ConnectivityManager`）标记（**不**要求 `network.links` 节点；lookup **不发** sidecar）。

**对象创建门槛：** 只要 `network.links` 节点存在（即使仅部分字段），`ConnectivityManager.getActiveNetworkInfo()` / `getActiveNetwork()` / `getLinkProperties(Network)` 即返回对应对象。各 getter / 字段仍按**单独字段是否配置**决定是否处理。

**缺失行为：** `network` 或 `links` 节点缺失时，上述 Connectivity 对象 API 与各 getter 保持 notHandled → `UnsupportedOperationException`。某字段未配置时，仅该字段对应 API 为 UOE（不伪造默认 DNS/代理/网关）。`Application`/`Context.getSystemService(ConnectivityManager.class)` 与 `"connectivity"` 字符串路径在节点缺失时仍返回同一 `SystemService` 标记；`getActiveNetworkInfo` 等 getter 仍 UOE 且无事件。

**显式 null：** 允许 JSON `null` 的字符串/IPv4 字段：`gatewayIpv4`、`netmaskIpv4`、`privateDnsServerName`、`proxyHost`、`dhcpServerIpv4`、`domains`。配置存在且为 null 时：字符串 getter 返回 Java `null`；`getHttpProxy()` 在 `proxyHost:null` 时返回 Java `null`；DHCP 整型字段对 null IPv4 返回 `0`。`typeName` / `interfaceName` **不允许** null。布尔与 int 字段 **不允许** null。

**DNS 列表：** `dnsServers` 为 JSON 数组；元素必须是唯一的严格点分 IPv4 字符串（不可 null）。**显式空数组 `[]`** 表示已配置为空列表：`getDnsServers()` 返回空 `ArrayList`；`DhcpInfo.dns1`/`dns2` 返回 `0`。数组缺失时 DNS 相关 API 为 notHandled。仅一项时 `dns1` 有值、`dns2` 为 `0`。

**DNS InetAddress 绑定：** `getDnsServers()` 产出的 DNS `InetAddress` 仅可在**创建它的 VM** 且 **同一 `TraceEnvironmentConfig` 实例**仍绑定、并且 `network.links.dnsServers` 仍显式配置时调用 `getHostAddress()`。跨 VM、过期 marker 与普通 `InetAddress` 均为 `UnsupportedOperationException`，且不发 sidecar（无 json-config 事件）。

**ProxyInfo 绑定：** `getHttpProxy()` 产出的 `ProxyInfo` 仅可在**创建它的 VM** 与 **同一 `TraceEnvironmentConfig` 实例**中读取 `getHost()` / `getPort()`。跨 VM 或过期对象为 `UnsupportedOperationException`，且不发 sidecar（无 json-config 事件）。

**小端 IPv4：** 点分 `a.b.c.d` 转为 Android 小端整型 `a|(b<<8)|(c<<16)|(d<<24)`（与 `WifiInfo.getIpAddress` / `DhcpInfo` 字段一致）。

### 字段说明

| 字段路径 | 必填 | 类型与校验 | 影响的 Java 方法 / 字段 |
| --- | --- | --- | --- |
| `network.links` | — | 可选 JSONObject | 见下行 API |
| `network.links.connected` | 否 | Boolean（不可 null） | `NetworkInfo.isConnected()` |
| `network.links.type` | 否 | 精确整数 `-1..17` | `NetworkInfo.getType()` |
| `network.links.typeName` | 否 | 非空无首尾空白 String（不可 null）；允许 TRACEAI marker | `NetworkInfo.getTypeName()` |
| `network.links.interfaceName` | 否 | 非空无首尾空白 String，UTF-8 ≤15 字节（不可 null） | `LinkProperties.getInterfaceName()` |
| `network.links.dnsServers` | 否 | IPv4 字符串数组，元素唯一；允许 `[]` | `LinkProperties.getDnsServers()`；`DhcpInfo.dns1`/`dns2` |
| `network.links.gatewayIpv4` | 否 | 严格点分 IPv4 或 JSON null | `DhcpInfo.gateway`（无 LinkProperties 网关 getter） |
| `network.links.netmaskIpv4` | 否 | 严格点分 IPv4 或 JSON null | `DhcpInfo.netmask`（`getIntField`） |
| `network.links.mtu` | 否 | 精确整数 `68..65536` | `LinkProperties.getMtu()` |
| `network.links.privateDnsActive` | 否 | Boolean（不可 null） | `LinkProperties.isPrivateDnsActive()` |
| `network.links.privateDnsServerName` | 否 | 非空无首尾空白 String 或 JSON null | `LinkProperties.getPrivateDnsServerName()` |
| `network.links.domains` | 否 | 非空无首尾空白 String 或 JSON null | `LinkProperties.getDomains()` |
| `network.links.proxyHost` | 否 | 非空无首尾空白 String 或 JSON null | `LinkProperties.getHttpProxy()`、`ProxyInfo.getHost()` |
| `network.links.proxyPort` | 否 | 精确整数 `0..65535` | `ProxyInfo.getPort()` |
| `network.links.dhcpServerIpv4` | 否 | 严格点分 IPv4 或 JSON null | `DhcpInfo.serverAddress` |
| `network.links.leaseDurationSeconds` | 否 | 精确整数 `0..Integer.MAX_VALUE` | `DhcpInfo.leaseDuration` |

另：`DhcpInfo.ipAddress` 读取 **`network.wifi.ipv4`**（非 links 字段）；见下方 DHCP 小节。

### JNI 支持的 API（链路与代理）

拦截入口在 `AbstractJni`（`callObjectMethod`/`V`、`callIntMethod`/`V`、`callBooleanMethod`/`V`）：

| Java 方法 | 精确签名 | 行为摘要 |
| --- | --- | --- |
| `Application.getSystemService(String)` | `(Ljava/lang/String;)Ljava/lang/Object;` | 参数 `"connectivity"` 时返回既有 `SystemService`（类型 `ConnectivityManager`）；**不**要求 `network.links` |
| `Application`/`Context.getSystemService(Class)` | `(Ljava/lang/Class;)Ljava/lang/Object;` | 第 0 参为 **`DvmClass`** 且类名为 `android/net/ConnectivityManager` 时，返回与字符串路径**同一** `SystemService("connectivity")`；null / 非 DvmClass / 其它 Class → UOE 无 sidecar；**不**要求 `network.links`；lookup **不发** sidecar |
| `ConnectivityManager.getActiveNetworkInfo()` | `()Landroid/net/NetworkInfo;` | `network.links` 存在时返回 `NetworkInfo` |
| `ConnectivityManager.getActiveNetwork()` | `()Landroid/net/Network;` | 同上，返回 `Network` |
| `ConnectivityManager.getLinkProperties(Network)` | `(Landroid/net/Network;)Landroid/net/LinkProperties;` | 签名匹配后返回 `LinkProperties`（**不读** Network 参数内容） |
| `NetworkInfo.isConnected()` | `()Z` | `connected` |
| `NetworkInfo.getType()` | `()I` | `type` |
| `NetworkInfo.getTypeName()` | `()Ljava/lang/String;` | `typeName` |
| `LinkProperties.getInterfaceName()` | `()Ljava/lang/String;` | `interfaceName` |
| `LinkProperties.getMtu()` | `()I` | `mtu` |
| `LinkProperties.getDnsServers()` | `()Ljava/util/List;` | `ArrayList` of `InetAddress`；值持有私有 provenance marker（owner VM + 创建时 config 实例 + 配置 IPv4） |
| `LinkProperties.isPrivateDnsActive()` | `()Z` | `privateDnsActive` |
| `LinkProperties.getPrivateDnsServerName()` | `()Ljava/lang/String;` | 显式 null → Java null |
| `LinkProperties.getDomains()` | `()Ljava/lang/String;` | 显式 null → Java null |
| `LinkProperties.getHttpProxy()` | `()Landroid/net/ProxyInfo;` | 需已配置 `proxyHost`；null → Java null；非 null → `ProxyInfo`（`ConfiguredLinkProxy` marker） |
| `InetAddress.getHostAddress()` | `()Ljava/lang/String;` | **仅** 本 VM + 同一 config 实例仍显式配置 `dnsServers` 的 links DNS marker；跨 VM / 过期 / 普通 `InetAddress` → UOE 无事件 |
| `ProxyInfo.getHost()` | `()Ljava/lang/String;` | **仅** 本 VM + 同一 config 实例仍显式配置 `proxyHost` 的 `getHttpProxy` marker；跨 VM / 过期 / 无关 `ProxyInfo` → UOE 无事件 |
| `ProxyInfo.getPort()` | `()I` | 需 live marker **且** 已配置 `proxyPort`；跨 VM / 过期 / 缺 port → UOE 无事件 |

命中时 sidecar kind=`network_link`、source=`json-config`、api=`ConnectivityManager.*` / `NetworkInfo.*` / `LinkProperties.*` / `InetAddress.getHostAddress` / `ProxyInfo.*`。类型化 / 字符串 `getSystemService` **不**发 sidecar。跨 VM 或过期 DNS `InetAddress.getHostAddress` 为 UOE，**不**发 sidecar。

### JNI 支持的 API（DHCP 投影）

`WifiManager.getDhcpInfo()` 在 **任一** 相关源已配置时返回 `DhcpInfo`（私有 `ConfiguredDhcpInfo` marker）：`network.wifi.ipv4`、`network.links.gatewayIpv4`、`dnsServers`、`dhcpServerIpv4`、**`netmaskIpv4`**、`leaseDurationSeconds`。**仅配置 `netmaskIpv4` 时也会成功返回 `DhcpInfo` 对象。** 字段经 **`getIntField`** 读取；非 marker 的 `DhcpInfo` 与**未配置源**字段均为 UOE。

| Java 字段 | 精确签名 | 配置源 | 行为摘要 |
| --- | --- | --- | --- |
| `DhcpInfo.ipAddress` | `android/net/DhcpInfo->ipAddress:I` | `network.wifi.ipv4` | 小端 IPv4；配置 null → `0` |
| `DhcpInfo.gateway` | `...->gateway:I` | `gatewayIpv4` | 同上 |
| `DhcpInfo.dns1` / `dns2` | `...->dns1:I` / `dns2:I` | `dnsServers[0]` / `[1]` | 需 `dnsServers` 已配置；空/缺槽 → `0` |
| `DhcpInfo.serverAddress` | `...->serverAddress:I` | `dhcpServerIpv4` | 小端 IPv4；null → `0` |
| `DhcpInfo.netmask` | `android/net/DhcpInfo->netmask:I` | `netmaskIpv4` | 小端 IPv4；字段**缺失** → UOE；显式 JSON null → `0` |
| `DhcpInfo.leaseDuration` | `...->leaseDuration:I` | `leaseDurationSeconds` | 整型秒数 |

命中时 sidecar kind=`network_dhcp`、source=`json-config`、api=`WifiManager.getDhcpInfo` / `DhcpInfo.*`。其中 `DhcpInfo.netmask` 的 `value` 为 `key=netmaskIpv4,config=<配置值或 null>,result=<int>`，`note` 为中文简述（如读取 DHCP 子网掩码）。

**行为约束：** `DhcpInfo` 只允许在创建它的 VM 和同一个配置实例内读取；跨 VM 或已过期对象走默认 `UnsupportedOperationException` 且不产生 `json-config` 事件。

### 明确未覆盖

- **Java 路由表**（`RouteInfo`）与 `LinkProperties` 路由相关 API。结构化 IPv4 路由表见下一节 `network.ipv4Routes`（只读 `/proc/net/route` 三别名；**不**从本节点推导）。结构化网卡收发统计见 `network.interfaceStats`（只读 `/proc/net/dev` 三别名；**不**从本节点推导）。结构化 IPv6 接口地址表见 `network.ipv6Addresses`（只读 `/proc/net/if_inet6` 三别名；**不**从本节点推导）。结构化 IPv4 IGMP 成员表见 `network.igmpMemberships`（只读 `/proc/net/igmp` 三别名；**不**从本节点推导）。结构化 IPv6 组播成员表见 `network.igmp6Memberships`（只读 `/proc/net/igmp6` 三别名；**不**从本节点推导）。结构化链路层多播表见 `network.linkLayerMulticastEntries`（只读 `/proc/net/dev_mcast` 三别名；**不**从本节点推导，也**不**实现真实多播加入）。结构化无线统计见 `network.wirelessProcStats`（只读 `/proc/net/wireless` 三别名；**不**从本节点推导）。
- **IPv6** Java/JNI 地址列表与链路字段（`LinkProperties`、`NetworkInterface` IPv6 枚举、`getifaddrs` 的 IPv6 条目、IPv6 socket）。结构化只读 `/proc/net/if_inet6` 见 `network.ipv6Addresses`。native `getifaddrs` 的 IPv4/MAC/flags 子集见 `network.interfaces`。
- **`NetworkCapabilities`**、网络回调 / request / bindProcess 等。
- **VPN**、代理 PAC / 排除列表 / 非 HTTP `ProxyInfo` 细节。
- 由 `network.links` **自动生成** `/proc/net/*`、`/sys/class/net/*`（`/proc/net/route` 三别名见 `network.ipv4Routes`；`/proc/net/dev` 三别名见 `network.interfaceStats`；`/proc/net/if_inet6` 三别名见 `network.ipv6Addresses`；`/proc/net/arp` 三别名见 `network.arpEntries`；`/proc/net/igmp` 三别名见 `network.igmpMemberships`；`/proc/net/igmp6` 三别名见 `network.igmp6Memberships`；`/proc/net/dev_mcast` 三别名见 `network.linkLayerMulticastEntries`；`/proc/net/wireless` 三别名见 `network.wirelessProcStats`；其余仍可用 `linux.files` 手工提供）。
- 广义其它 Class 的 `getSystemService(Class)`（本节点仅 `ConnectivityManager`；`WifiManager` 见 `network.wifi`；`BluetoothManager` 见 `network.bluetooth`）。
- 蓝牙适配器子集见上一节 `network.bluetooth`（本节点不覆盖）。

## network.ipv4Routes

可选 **JSON 数组**，路径为根节点下的 `network.ipv4Routes`。为应用常读的 Linux IPv4（互联网协议第四版）路由表提供**结构化、可复现**的只读文本，由 `TraceEnvironmentConfig.NetworkIpv4RouteConfig` + `ConfiguredIpv4RouteFiles` 渲染；`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 接入。

**节点语义：**

- **键缺失：** **不接管** `/proc/net/route`、`/proc/self/net/route`、`/proc/<emulatorPid>/net/route`，保留既有回退（含 `linux.files` 手工整段文本）。
- **显式 `[]`：** 视为已配置空表，**接管**上述三路径，只输出固定表头（仍以 LF 结尾）。
- **有条目：** 按 JSON 数组顺序输出表头 + 每条标准列。**不**从 `network.interfaces`、`network.wifi`、`network.links` 或其它字段推导任一路由值。

### 字段表（严格白名单，全部必填）

| 字段 | 必填 | 类型与范围 | 说明 |
| --- | --- | --- | --- |
| `interfaceName` | **是** | 与 `network.interfaces[].name` 相同的接口名规则 | 必须是**已配置**且唯一的 `network.interfaces` 条目名称；**不**从 wifi/links 推导 |
| `destination` | **是** | 已有严格点分 IPv4（无空白/正负号，四段 `0..255`） | 路由目的；输出前按该字面规范化为 Linux 小端十六进制 |
| `gateway` | **是** | 同上 | 网关；`0.0.0.0` 表示直连 |
| `flags` | **是** | 精确 JSON 整数 `0..4294967295` | 路由 flags；输出为 Linux `%04X` |
| `refCount` | **是** | 同上 | `RefCnt` 列，十进制 |
| `use` | **是** | 同上 | `Use` 列，十进制 |
| `metric` | **是** | 同上 | `Metric` 列，十进制 |
| `mask` | **是** | 已有严格点分 IPv4 | 子网掩码；小端十六进制 |
| `mtu` | **是** | 精确 JSON 整数 `0..4294967295` | `MTU` 列，十进制 |
| `window` | **是** | 同上 | `Window` 列，十进制 |
| `irtt` | **是** | 同上 | `IRTT` 列，十进制 |

parse 拒绝：负数、小数、数字字符串、布尔、`null`、溢出、未知字段、`interfaceName` 未出现在 `network.interfaces`（含接口名重复配置）、非对象数组条目。异常路径形如 `network.ipv4Routes[i].field`。

### 接管路径与输出

仅**精确只读**打开下列路径时接管（写、`O_DIRECTORY`、目录枚举、相近路径均**不接管**）：

- `/proc/net/route`
- `/proc/self/net/route`
- `/proc/<emulatorPid>/net/route`（`emulator.getPid()`，通常来自 `process.pid`）

表头与列与 Linux `/proc/net/route` 稳定格式一致：`Iface`、`Destination`、`Gateway`、`Flags`、`RefCnt`、`Use`、`Metric`、`Mask`、`MTU`、`Window`、`IRTT`。`Destination`/`Gateway`/`Mask` 由规范 IPv4 确定性写成小端 `%08X`（`a.b.c.d` → `a|(b<<8)|(c<<16)|(d<<24)`，例如 `192.168.50.1` → `0132A8C0`，`192.168.50.0` → `0032A8C0`，`255.255.255.0` → `00FFFFFF`，`0.0.0.0` → `00000000`）。`Flags` 为 `%04X`；其余数值列为无符号十进制。整表以 LF 结尾。

**`linux.files` 优先：** 同一精确路径若已在 `linux.files` 配置，只返回该手工内容并发 `linux_file`，**不**走本渲染、**不**发下方 sidecar。

### Sidecar（旁路事件）

仅只读成功接管时写一次：

| 字段 | 值 |
| --- | --- |
| `kind` | `network_device` |
| `api` | `read("<请求绝对路径>")` |
| `source` | `json-config` |
| `value` | **仅** `path=<请求路径>,format=proc-net-route,routeCount=<条数>,bytes=<字节数>` |
| `note` | 中文：`读取配置的 IPv4 路由表 <path>` |

**脱敏：** sidecar **不**写 `destination`、`gateway`、`mask`、`flags`、接口名或其它网络字段。**绝不**读取宿主机网络状态。

### 明确未实现（相对 `network.ipv4Routes`）

- 写入、目录枚举、相近路径（如 `/proc/net/routes`、`/proc/net/arp`）
- `/proc/net/dev`（见 `network.interfaceStats`；本节点**不**推导收发计数）
- `/proc/net/if_inet6`（见 `network.ipv6Addresses`；本节点**不**推导 IPv6 地址）
- `/proc/net/igmp`（见 `network.igmpMemberships`；本节点**不**推导 IGMP 成员）
- `/proc/net/igmp6`（见 `network.igmp6Memberships`；本节点**不**推导 IPv6 组播成员）
- `/proc/net/dev_mcast`（见 `network.linkLayerMulticastEntries`；本节点**不**推导链路层多播，也**不**实现真实多播加入）
- `/proc/net/wireless`（见 `network.wirelessProcStats`；本节点**不**推导无线统计）
- netlink、路由 ioctl、IPv6 路由、真实网络连接
- Java `RouteInfo` / `LinkProperties` 路由 API（见 `network.links`）
- 由 interfaces/wifi/links **自动推导**本表

## network.interfaceStats

可选 **JSON 数组**，路径为根节点下的 `network.interfaceStats`。为应用常读的 Linux 网卡收发统计提供**结构化、可复现**的只读文本，由 `TraceEnvironmentConfig.NetworkInterfaceStatsConfig` + `ConfiguredInterfaceStatsFiles` 渲染；`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 接入。

**节点语义：**

- **键缺失：** **不接管** `/proc/net/dev`、`/proc/self/net/dev`、`/proc/<emulatorPid>/net/dev`，也**不接管** `/sys/class/net/<name>/statistics/<field>`，保留既有回退（含 `linux.files` 手工整段文本）。
- **显式 `[]`：** 视为已配置空表，**接管**上述 `/proc/net/dev` 三路径，只输出固定两行 `/proc/net/dev` 表头（仍以 LF 结尾）；**不接管**任何 `/sys/class/net/<name>/statistics/<field>`。
- **有条目：** 按 JSON 数组顺序输出两行表头 + 每条接口的十六个十进制无符号计数；并对**同接口存在本数组条目**的接口服务下表十六个 sysfs 单项统计文件。**不**从 `network.interfaces`、`network.ipv4Routes`、`network.wifi`、`network.links` 或其它字段推导任何计数。

### 字段表（严格白名单，全部必填）

| 字段 | 必填 | 类型与范围 | 说明 |
| --- | --- | --- | --- |
| `interfaceName` | **是** | 与 `network.interfaces[].name` 相同的接口名规则 | 必须是**已配置**且唯一的 `network.interfaces` 条目名称；本数组内亦**不得重复**；**不**从 wifi/links/routes 推导 |
| `rxBytes` | **是** | 精确 JSON 整数 `0..Long.MAX_VALUE` | RX `bytes` 列 |
| `rxPackets` | **是** | 同上 | RX `packets` 列 |
| `rxErrors` | **是** | 同上 | RX `errs` 列 |
| `rxDrop` | **是** | 同上 | RX `drop` 列 |
| `rxFifo` | **是** | 同上 | RX `fifo` 列 |
| `rxFrame` | **是** | 同上 | RX `frame` 列 |
| `rxCompressed` | **是** | 同上 | RX `compressed` 列 |
| `rxMulticast` | **是** | 同上 | RX `multicast` 列 |
| `txBytes` | **是** | 同上 | TX `bytes` 列 |
| `txPackets` | **是** | 同上 | TX `packets` 列 |
| `txErrors` | **是** | 同上 | TX `errs` 列 |
| `txDrop` | **是** | 同上 | TX `drop` 列 |
| `txFifo` | **是** | 同上 | TX `fifo` 列 |
| `txCollisions` | **是** | 同上 | TX `colls` 列 |
| `txCarrier` | **是** | 同上 | TX `carrier` 列 |
| `txCompressed` | **是** | 同上 | TX `compressed` 列 |

parse 拒绝：负数、小数、数字字符串、布尔、`null`、溢出（含大于 `Long.MAX_VALUE`）、未知字段、`interfaceName` 未出现在 `network.interfaces`（含接口名重复配置）、本数组内重复 `interfaceName`、非对象数组条目。异常路径形如 `network.interfaceStats[i].field`。

### 接管路径与输出

仅**精确只读**打开下列路径时接管（写、`O_DIRECTORY`、目录枚举、相近路径均**不接管**）：

- `/proc/net/dev`
- `/proc/self/net/dev`
- `/proc/<emulatorPid>/net/dev`（`emulator.getPid()`，通常来自 `process.pid`）

表头与 Linux `/proc/net/dev` 稳定两行格式一致（`Inter-| Receive | Transmit` 与 `face |bytes … multicast|bytes … compressed`）。随后按 JSON 顺序输出每条接口：接口名右对齐至 6 字符后接 `:`，再按空白分隔八个 RX（接收）值与八个 TX（发送）值（内核 `dev_seq_printf_stats` 列宽：`%7d %7d %4d %4d %4d %5d %10d %9d` 然后 `%8d %7d %4d %4d %4d %5d %7d %10d`）。全部为无符号十进制；整表每行以 LF 结尾，Linux parser 可读。

**`linux.files` 优先：** 同一精确路径若已在 `linux.files` 配置，只返回该手工内容并发 `linux_file`，**不**走本渲染、**不**发下方 sidecar。

### Sidecar（旁路事件）

仅只读成功接管时写一次：

| 字段 | 值 |
| --- | --- |
| `kind` | `network_device` |
| `api` | `read("<请求绝对路径>")` |
| `source` | `json-config` |
| `value` | **仅** `path=<请求路径>,format=proc-net-dev,interfaceCount=<条数>,bytes=<字节数>` |
| `note` | 中文：`读取配置的网卡收发统计 <path>` |

**脱敏：** sidecar **不**写接口名、任何收发计数、IP、MAC、flags 或路由值。**绝不**读取宿主机网络状态。

### 对 `/sys/class/net/<name>/statistics/<field>` 的影响

当 `network.interfaceStats` 节点存在，且某条目标接口在本数组中有条目时，`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 对**精确只读**路径 `/sys/class/net/<已配置接口>/statistics/<field>` 返回对应非负 long 的十进制 ASCII 加一个 LF。只复用现有十六个计数，**不**新增 JSON 字段；**不**从 `network.interfaces`、`network.ipv4Routes`、`network.wifi`、`network.links` 或其它值推导统计数；**不**读取宿主机 `java.net.NetworkInterface` 或真实 sysfs。写方式或 `O_DIRECTORY` **不接管**。显式 `[]`、键缺失、未知接口、本数组无该接口条目、未支持内核字段与相近路径均**不接管**，保留既有回退。

| 内核文件 | JSON 字段 |
| --- | --- |
| `rx_bytes` | `rxBytes` |
| `rx_packets` | `rxPackets` |
| `rx_errors` | `rxErrors` |
| `rx_dropped` | `rxDrop` |
| `rx_fifo_errors` | `rxFifo` |
| `rx_frame_errors` | `rxFrame` |
| `rx_compressed` | `rxCompressed` |
| `multicast` | `rxMulticast` |
| `tx_bytes` | `txBytes` |
| `tx_packets` | `txPackets` |
| `tx_errors` | `txErrors` |
| `tx_dropped` | `txDrop` |
| `tx_fifo_errors` | `txFifo` |
| `tx_carrier_errors` | `txCarrier` |
| `tx_compressed` | `txCompressed` |
| `collisions` | `txCollisions` |

| 条件 | 行为 |
| --- | --- |
| 同接口存在 stats 条目，路径精确为上表十六项之一，只读打开 | ASCII `<decimal>\n`（无前导零、单个 LF；`0` → `0\n`） |
| 同路径命中 `linux.files` | **仅**该手工内容并发 `linux_file`；**不**走本自动路径、**不**发下方 sidecar |
| 键缺失 / 显式 `[]` / 未知接口 / 接口存在于 `network.interfaces` 但本数组无条目 / 未支持内核字段 / 写或目录打开 / 相近路径 | **不接管**，保留旧 open |

自动文件 sidecar（与其它 sysfs 网卡字段同约定；仅只读成功接管时写一次）：

| 字段 | 值 |
| --- | --- |
| `kind` | `network_device` |
| `api` | `read("<实际路径>")` |
| `source` | `json-config` |
| `value` | **仅** `path=<请求路径>,name=<接口名>,format=statistics-<field>,bytes=<字节数>` |
| `note` | 中文：`读取配置的网卡统计 <path>` |

**脱敏：** sidecar **不**写计数值、IP、MAC、flags 或其它敏感网络值。

### 明确未实现（相对 `network.interfaceStats`）

- 写入、目录枚举、相近路径（如 `/proc/net/devs`、`/sys/class/net/<name>/statistics/` 目录本身）
- `/proc/net/arp`（见 `network.arpEntries`）、`/proc/net/route`（见 `network.ipv4Routes`）、`/proc/net/if_inet6`（见 `network.ipv6Addresses`；本节点**不**推导 IPv6 地址）、`/proc/net/igmp`（见 `network.igmpMemberships`；本节点**不**推导 IGMP 成员）、`/proc/net/igmp6`（见 `network.igmp6Memberships`；本节点**不**推导 IPv6 组播成员）、`/proc/net/dev_mcast`（见 `network.linkLayerMulticastEntries`；本节点**不**推导链路层多播，也**不**实现真实多播加入）、`/proc/net/wireless`（见 `network.wirelessProcStats`；本节点**不**推导无线统计）
- 内核额外统计文件（`rx_crc_errors`、`rx_length_errors`、`rx_missed_errors`、`rx_nohandler`、`rx_over_errors`、`tx_aborted_errors`、`tx_heartbeat_errors`、`tx_window_errors` 等）
- socket / netlink / 路由 ioctl、IPv6 socket、真实网络连接
- 由 interfaces / ipv4Routes / wifi / links **自动推导**任何计数

## network.ipv6Addresses

可选 **JSON 数组**，路径为根节点下的 `network.ipv6Addresses`。为应用常读的 Linux IPv6 接口地址表提供**结构化、可复现**的只读文本，由 `TraceEnvironmentConfig.NetworkIpv6AddressConfig` + `ConfiguredIpv6AddressFiles` 渲染；`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 接入。

**节点语义：**

- **键缺失：** **不接管** `/proc/net/if_inet6`、`/proc/self/net/if_inet6`、`/proc/<emulatorPid>/net/if_inet6`，保留既有回退（含 `linux.files` 手工整段文本）。
- **显式 `[]`：** 视为已配置空表，**接管**上述三路径，输出**零字节**文件（**不**生成表头）。
- **有条目：** 按 JSON 数组顺序输出每条标准六列。地址、前缀、scope、flags **不**从 `network.interfaces`、`network.ipv4Routes`、`network.interfaceStats`、`network.wifi`、`network.links` 或其它字段推导。接口索引**仅**复用同名 `network.interfaces[].index`。仅当本数组某条地址引用该接口时，其 `index` 必须在 `0..255`（现有 `network.interfaces[].index` 下限仍适用，最大为 255），以便输出固定两位小写 hex；未启用本节点或未被引用的普通接口 `index` 仍为 `[1, Integer.MAX_VALUE]`，**不**因本节点收紧。

### 字段表（严格白名单，全部必填）

| 字段 | 必填 | 类型与范围 | 说明 |
| --- | --- | --- | --- |
| `interfaceName` | **是** | 与 `network.interfaces[].name` 相同的接口名规则 | 必须是**已配置**且唯一的 `network.interfaces` 条目名称；本数组内同一 `interfaceName`+`addressHex` 组合**不得重复**（`addressHex` 先规范化再比较）；**不**从 wifi/links/routes 推导 |
| `addressHex` | **是** | 恰好 32 个 ASCII 十六进制字符的 JSON String | 固定十六进制形式（无冒号、无空白、非压缩 IPv6、非 IPv4、非 DNS）；解析后规范化为小写再输出 |
| `prefixLength` | **是** | 精确 JSON 整数 `0..128` | 前缀长度；输出为两位小写十六进制 |
| `scope` | **是** | 精确 JSON 整数 `0..255` | 范围；输出为两位小写十六进制 |
| `flags` | **是** | 精确 JSON 整数 `0..255` | IPv6 接口地址 flags；输出为两位小写十六进制；**不**复用 `network.interfaces[].flags` |

parse 拒绝：非法长度、非 hex、带冒号/空白/IPv4/DNS、非 String、负数、小数、数字字符串、布尔、`null`、溢出、未知字段、`interfaceName` 未出现在 `network.interfaces`（含接口名重复配置）、本数组内重复 `interfaceName`+`addressHex` 组合、非对象数组条目、被引用接口的 `index` 不在 `0..255`。超限异常路径含 `network.ipv6Addresses[i].interfaceName`。普通未引用接口的 `index` 范围仍为 `[1, Integer.MAX_VALUE]`。异常路径形如 `network.ipv6Addresses[i].field`。

### 接管路径与输出

仅**精确只读**打开下列路径时接管（写、`O_DIRECTORY`、目录枚举、相近路径均**不接管**）：

- `/proc/net/if_inet6`
- `/proc/self/net/if_inet6`
- `/proc/<emulatorPid>/net/if_inet6`（`emulator.getPid()`，通常来自 `process.pid`）

每行标准六列，字段间**单空格**，行以 LF 结尾：32 位小写 `addressHex`、两位小写接口索引、两位小写 `prefixLength`、两位小写 `scope`、两位小写 `flags`、`interfaceName`。接口索引来自同名 `network.interfaces[].index` 的 `%02x`（例如 `lo` 的 `index=1` → `01`，`index=255` → `ff`）。仅被本数组条目引用的接口 `index` 须在 `0..255`，否则 parse 以含 `network.ipv6Addresses[i].interfaceName` 的 `IllegalArgumentException` 拒绝（`index=256` 会变成三字符 `100`，破坏六列格式）。普通未引用的 `interfaces[].index` **不**因此限制。示例：`::1` → `00000000000000000000000000000001`（`prefixLength=128` → `80`，`scope=16` → `10`，`flags=128` → `80`）；`fe80::1` → `fe800000000000000000000000000001`（`prefixLength=64` → `40`，`scope=32` → `20`）。**不**接受压缩形式或冒号分隔地址。整表按 JSON 顺序；空数组为零字节、无表头。

**`linux.files` 优先：** 同一精确路径若已在 `linux.files` 配置，只返回该手工内容并发 `linux_file`，**不**走本渲染、**不**发下方 sidecar。

### Sidecar（旁路事件）

仅只读成功接管时写一次：

| 字段 | 值 |
| --- | --- |
| `kind` | `network_device` |
| `api` | `read("<请求绝对路径>")` |
| `source` | `json-config` |
| `value` | **仅** `path=<请求路径>,format=proc-net-if-inet6,addressCount=<条数>,bytes=<字节数>` |
| `note` | 中文：`读取配置的 IPv6 接口地址表 <path>` |

**脱敏：** sidecar **不**写地址、接口名、前缀、scope、flags、IP、MAC 或路由值。**绝不**读取宿主机网络状态。

### 明确未实现（相对 `network.ipv6Addresses`）

- 写入、目录枚举、相近路径（如 `/proc/net/if_inet6s`、`/proc/net/ipv6_route`、`/proc/net/route`、`/proc/net/dev`）
- IPv6 socket、netlink、`getifaddrs` 的 IPv6 条目、Java `NetworkInterface` 的 IPv6 枚举、真实网络连接
- `/proc/net/igmp`（见 `network.igmpMemberships`；本节点**不**推导 IGMP 成员）
- `/proc/net/igmp6`（见 `network.igmp6Memberships`；本节点**不**推导 IPv6 组播成员）
- `/proc/net/dev_mcast`（见 `network.linkLayerMulticastEntries`；本节点**不**推导链路层多播，也**不**实现真实多播加入）
- 由 interfaces / ipv4Routes / interfaceStats / wifi / links **自动推导**地址、前缀、scope 或 flags（接口索引除外：仅复用同名 `index`）

## network.arpEntries

可选 **JSON 数组**，路径为根节点下的 `network.arpEntries`。为应用常读的 Linux IPv4 ARP 表提供**结构化、可复现**的只读文本，由 `TraceEnvironmentConfig.NetworkArpEntryConfig` + `ConfiguredArpEntryFiles` 渲染；`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 接入。

**节点语义：**

- **键缺失：** **不接管** `/proc/net/arp`、`/proc/self/net/arp`、`/proc/<emulatorPid>/net/arp`，保留既有回退（含 `linux.files` 手工整段文本）。
- **显式 `[]`：** 视为已配置空表，**接管**上述三路径，只输出固定表头（仍以 LF 结尾）。
- **有条目：** 按 JSON 数组顺序输出表头 + 每条标准列。**不**从 `network.interfaces`、`network.ipv4Routes`、`network.interfaceStats`、`network.ipv6Addresses`、`network.wifi`、`network.links` 或其它字段推导任何 ARP 值（含 MAC / `hardwareType` / flags）。

紧凑完整示例：

```json
{
  "network": {
    "interfaces": [
      { "name": "wlan0", "index": 2, "ipv4": "192.168.50.23" }
    ],
    "arpEntries": [
      {
        "interfaceName": "wlan0",
        "ipv4": "192.168.50.1",
        "hardwareType": 1,
        "flags": 2,
        "mac": "02:00:00:00:00:01"
      }
    ]
  }
}
```

### 字段表（严格白名单，全部必填）

| 字段 | 必填 | 类型与范围 | 说明 |
| --- | --- | --- | --- |
| `interfaceName` | **是** | 与 `network.interfaces[].name` 相同的接口名规则 | 必须是**已配置**且唯一的 `network.interfaces` 条目名称；本数组内同一 `interfaceName`+`ipv4` 组合**不得重复**；**不**从 wifi/links/routes 推导 |
| `ipv4` | **是** | 已有严格点分 IPv4（无空白/正负号，四段 `0..255`） | 邻居 IPv4；输出为 `%-16s` 左对齐 |
| `hardwareType` | **是** | 精确 JSON 整数 `0..4294967295` | `HW type` 列，输出为 `0x%-10x`；**不**复用 `network.interfaces[].hardwareType` |
| `flags` | **是** | 精确 JSON 整数 `0..4294967295` | `Flags` 列，输出为 `0x%-10x`；**不**复用 `network.interfaces[].flags` |
| `mac` | **是** | 与 `network.interfaces[].mac` 相同：六段冒号分隔两位十六进制，解析后规范小写 | `HW address` 列，输出小写冒号形式（`aa:bb:cc:dd:ee:ff`）；**不**从接口 `mac` 推导 |

parse 拒绝：非法 IPv4、非法 MAC（连字符/空白/非六段/非 hex）、负数、小数、数字字符串、布尔、`null`、溢出、未知字段、`interfaceName` 未出现在 `network.interfaces`（含接口名重复配置）、本数组内重复 `interfaceName`+`ipv4` 组合、非对象数组条目。异常路径形如 `network.arpEntries[i].field`。

### 接管路径与输出

仅**精确只读**打开下列路径时接管（写、`O_DIRECTORY`、目录枚举、相近路径均**不接管**）：

- `/proc/net/arp`
- `/proc/self/net/arp`
- `/proc/<emulatorPid>/net/arp`（`emulator.getPid()`，通常来自 `process.pid`）

表头与 Linux 内核 `net/ipv4/arp.c` 一致：`IP address       HW type     Flags       HW address            Mask     Device` 加 LF。普通项语义等效于 `%-16s 0x%-10x0x%-10x%-17s     *        %s` 加 LF（`Mask` 恒为 `*`）。例如 `192.168.50.1` / `hardwareType=1` / `flags=2` / `mac=02:00:00:00:00:01` / `wlan0` → `192.168.50.1     0x1         0x2         02:00:00:00:00:01     *        wlan0`。整表以 LF 结尾。

**`linux.files` 优先：** 同一精确路径若已在 `linux.files` 配置，只返回该手工内容并发 `linux_file`，**不**走本渲染、**不**发下方 sidecar。

### Sidecar（旁路事件）

仅只读成功接管时写一次：

| 字段 | 值 |
| --- | --- |
| `kind` | `network_device` |
| `api` | `read("<请求绝对路径>")` |
| `source` | `json-config` |
| `value` | **仅** `path=<请求路径>,format=proc-net-arp,entryCount=<条数>,bytes=<字节数>` |
| `note` | 中文：`读取配置的 ARP 表 <path>` |

**脱敏：** sidecar **不**写接口名、IP、MAC、`hardwareType`、flags 或其它网络字段。**绝不**读取宿主机网络状态。

### 明确未实现（相对 `network.arpEntries`）

- 写入、目录枚举、相近路径（如 `/proc/net/arps`、`/proc/net/route`、`/proc/net/dev`）
- ARP ioctl、ARP 探测/请求、真实网络连接、宿主机 `/proc/net/arp` 或邻居表读取
- `/proc/net/igmp`（见 `network.igmpMemberships`；本节点**不**推导 IGMP 成员）
- `/proc/net/igmp6`（见 `network.igmp6Memberships`；本节点**不**推导 IPv6 组播成员）
- `/proc/net/dev_mcast`（见 `network.linkLayerMulticastEntries`；本节点**不**推导链路层多播，也**不**实现真实多播加入）
- 由 interfaces / ipv4Routes / interfaceStats / ipv6Addresses / wifi / links **自动推导**任何 ARP 字段

## network.igmpMemberships

可选 **JSON 数组**，路径为根节点下的 `network.igmpMemberships`。为应用常读的 Linux IPv4 IGMP 成员表提供**结构化、可复现**的只读文本，由 `TraceEnvironmentConfig.NetworkIgmpMembershipConfig` + `ConfiguredIgmpMembershipFiles` 渲染；`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 接入。只模拟读取 `/proc/net/igmp`（及 self/pid 别名）。

**节点语义：**

- **键缺失：** **不接管** `/proc/net/igmp`、`/proc/self/net/igmp`、`/proc/<emulatorPid>/net/igmp`，保留既有回退（含 `linux.files` 手工整段文本）。
- **显式 `[]`：** 视为已配置空表，**接管**上述三路径，只输出固定表头（仍以 LF 结尾）。
- **有条目：** 每个接口首次出现时输出接口标题行，随后按 JSON 数组顺序输出该条组成员。接口索引**仅**复用同名 `network.interfaces[].index`。结构性 `Count` **仅**等于本数组中该 `interfaceName` 的条目数。组地址、Querier、users、timer、reporter **不**从 `network.interfaces`、`network.ipv4Routes`、`network.interfaceStats`、`network.ipv6Addresses`、`network.arpEntries`、`network.wifi`、`network.links` 或其它字段推导，也**不**读取宿主网络。

紧凑完整示例：

```json
{
  "network": {
    "interfaces": [
      { "name": "lo", "index": 1, "ipv4": "127.0.0.1" },
      { "name": "wlan0", "index": 2, "ipv4": "192.168.50.23" }
    ],
    "igmpMemberships": [
      {
        "interfaceName": "lo",
        "groupIpv4": "224.0.0.1",
        "querierVersion": "V2",
        "users": 1,
        "timerRunning": false,
        "timerClock": 0,
        "reporter": false
      },
      {
        "interfaceName": "wlan0",
        "groupIpv4": "224.0.0.251",
        "querierVersion": "V3",
        "users": 1,
        "timerRunning": false,
        "timerClock": 0,
        "reporter": false
      },
      {
        "interfaceName": "wlan0",
        "groupIpv4": "239.255.255.250",
        "querierVersion": "V3",
        "users": 2,
        "timerRunning": true,
        "timerClock": 10,
        "reporter": true
      }
    ]
  }
}
```

### 字段表（严格白名单，全部必填）

| 字段 | 必填 | 类型与范围 | 说明 |
| --- | --- | --- | --- |
| `interfaceName` | **是** | 与 `network.interfaces[].name` 相同的接口名规则 | 必须是**已配置**且唯一的 `network.interfaces` 条目名称；本数组内同一 `interfaceName`+`groupIpv4` 组合**不得重复**；**不**从 wifi/links/routes 推导 |
| `groupIpv4` | **是** | 已有严格点分 IPv4，且必须落在 `224.0.0.0/4` 多播范围（首段 `224..239`） | 组成员地址；输出为 Android ARM 小端机器上内核打印 `__be32` 的 `%08X`（`a.b.c.d` → `a|(b<<8)|(c<<16)|(d<<24)`，例如 `224.0.0.251` → `FB0000E0`） |
| `querierVersion` | **是** | 精确 JSON String：仅 `V1`、`V2`、`V3` | 接口标题行 `Querier`；**同一接口的全部条目必须相同**（内核每个接口标题只输出一个 Querier） |
| `users` | **是** | 精确 JSON 整数 `0..2147483647` | `Users` 列，十进制；**不**从 socket/组加入推导 |
| `timerRunning` | **是** | JSON boolean | 内核 `Timer` 的运行位，输出 `0` 或 `1` |
| `timerClock` | **是** | 精确 JSON 整数 `0..4294967295` | `Timer` 的八位大写十六进制时钟；`timerRunning=false` 时**必须为 `0`**（内核此时输出 `0`） |
| `reporter` | **是** | JSON boolean | `Reporter` 列，输出 `0` 或 `1` |

parse 拒绝：非多播 IPv4、非法点分、负数、小数、数字字符串、浮点、布尔当数字、`null`、溢出、未知字段、空值、`interfaceName` 未出现在 `network.interfaces`（含接口名重复配置）、本数组内重复 `interfaceName`+`groupIpv4` 组合、同一接口 Querier 不一致、`timerRunning=false` 且 `timerClock≠0`、非对象数组条目。异常路径形如 `network.igmpMemberships[i].field`。

### 接管路径与输出

仅**精确只读**打开下列路径时接管（写、`O_DIRECTORY`、目录枚举、相近路径均**不接管**）：

- `/proc/net/igmp`
- `/proc/self/net/igmp`
- `/proc/<emulatorPid>/net/igmp`（`emulator.getPid()`，通常来自 `process.pid`）

格式来自 Linux 内核 `net/ipv4/igmp.c` `igmp_mc_seq_show`：

1. 表头必须为 `Idx\tDevice    : Count Querier\tGroup    Users Timer\tReporter` 加 LF。
2. 每个接口在 JSON 顺序中**首次出现**时输出 `%d\t%-10s: %5d %7s` 加 LF：`index` 复用同名 `network.interfaces[].index`；`Count` 等于本数组中该接口的成员数；`Querier` 为该接口统一的 `V1`/`V2`/`V3`。
3. 随后按 JSON 数组顺序输出该条 `\t\t\t\t%08X %5d %d:%08X\t\t%d` 加 LF。`groupIpv4` 字节序与内核在 Android ARM 小端上打印 `__be32` 一致；`timerClock` 为八位大写十六进制；`timerRunning`/`reporter` 分别为 `0` 或 `1`。

示例（对应上方配置）：`lo` 的 `224.0.0.1` → `010000E0`；`wlan0` 的 `224.0.0.251` → `FB0000E0`；`239.255.255.250` → `FAFFFFEF`；`wlan0` 标题 `Count=2`。整表以 LF 结尾。

**`linux.files` 优先：** 同一精确路径若已在 `linux.files` 配置，只返回该手工内容并发 `linux_file`，**不**走本渲染、**不**发下方 sidecar。

### Sidecar（旁路事件）

仅只读成功接管时写一次：

| 字段 | 值 |
| --- | --- |
| `kind` | `network_device` |
| `api` | `read("<请求绝对路径>")` |
| `source` | `json-config` |
| `value` | **仅** `path=<请求路径>,format=proc-net-igmp,membershipCount=<条数>,interfaceCount=<接口数>,bytes=<字节数>` |
| `note` | 中文：`读取配置的 IGMP 成员表 <path>` |

**脱敏：** sidecar **不**写接口名、`index`、组地址、querier、users、timer 或 reporter。`interfaceCount` 与每行 `Count` 一样，只统计本配置数组出现过的接口名。**绝不**读取宿主机网络状态。

### 明确未实现（相对 `network.igmpMemberships`）

- 写入、目录枚举、相近路径（如 `/proc/net/igmps`、`/proc/net/arp`、`/proc/net/route`）
- IGMP socket、组加入/离开、ioctl、真实网络探测、宿主机 `/proc/net/igmp` 读取
- IPv6 MLD / `/proc/net/igmp6`（见 `network.igmp6Memberships`；本节点**不**推导 IPv6 组播成员，也**不**实现真实多播加入）
- `/proc/net/dev_mcast`（见 `network.linkLayerMulticastEntries`；本节点**不**推导链路层多播，也**不**实现真实多播加入）
- 由 interfaces / ipv4Routes / interfaceStats / ipv6Addresses / arpEntries / wifi / links **自动推导**任何组、用户、计时器或报告值

## network.igmp6Memberships

可选 **JSON 数组**，路径为根节点下的 `network.igmp6Memberships`。为应用常读的 Linux IPv6 组播成员表提供**结构化、可复现**的只读文本，由 `TraceEnvironmentConfig.NetworkIgmp6MembershipConfig` + `ConfiguredIgmp6MembershipFiles` 渲染；`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 接入。只模拟读取 `/proc/net/igmp6`（及 self/pid 别名）。这是**固定快照**：打开时按 JSON 原样输出，**不**随时间变化，也**不**实现真实 MLD / 多播加入或离开。

**节点语义：**

- **键缺失：** **不接管** `/proc/net/igmp6`、`/proc/self/net/igmp6`、`/proc/<emulatorPid>/net/igmp6`，保留既有回退（含 `linux.files` 手工整段文本）。
- **显式 `[]`：** 视为已配置空表，**接管**上述三路径，输出**零字节**（无表头）。
- **有条目：** 按 JSON 数组顺序输出内核 igmp6 行。接口序号**仅**复用同名 `network.interfaces[].index`。组地址、users、flags、timer **不**从 `network.interfaces`、`network.ipv4Routes`、`network.interfaceStats`、`network.ipv6Addresses`、`network.arpEntries`、`network.igmpMemberships`、`network.wifi`、`network.links` 或其它字段推导，也**不**读取宿主网络。

紧凑完整示例：

```json
{
  "network": {
    "interfaces": [
      { "name": "lo", "index": 1, "ipv4": "127.0.0.1" },
      { "name": "wlan0", "index": 2, "ipv4": "192.168.50.23" }
    ],
    "igmp6Memberships": [
      {
        "interfaceName": "lo",
        "groupIpv6": "ff02::1",
        "users": 1,
        "flags": 0,
        "timer": 0
      },
      {
        "interfaceName": "wlan0",
        "groupIpv6": "ff02::1",
        "users": 1,
        "flags": 0,
        "timer": 0
      },
      {
        "interfaceName": "wlan0",
        "groupIpv6": "ff02::fb",
        "users": 2,
        "flags": 1,
        "timer": 10
      }
    ]
  }
}
```

### 字段表（严格白名单，全部必填）

| 字段 | 必填 | 类型与范围 | 说明 |
| --- | --- | --- | --- |
| `interfaceName` | **是** | 与 `network.interfaces[].name` 相同的接口名规则 | 必须是**已配置**且唯一的 `network.interfaces` 条目名称；本数组内同一 `interfaceName`+解析后的 `groupIpv6`（16 字节网络序地址）组合**不得重复**（大小写/压缩形式视为同一地址）；**不**从 wifi/links/routes 推导 |
| `groupIpv6` | **是** | 严格 IPv6 文本（RFC 4291：1..4 位 hex 的八组，允许单次 `::` 压缩），且必须落在 `ff00::/8` | 组成员地址；拒空白、zone id、点分 IPv4/IPv4 嵌入、方括号、前缀长度、32 位无冒号 hex、DNS；输出为 32 个不含冒号的大写十六进制字符（网络字节序） |
| `users` | **是** | 精确 JSON 整数 `0..2147483647` | `users` 列，十进制；**不**从 socket/组加入推导；拒浮点、数字字符串 |
| `flags` | **是** | 精确 JSON 整数 `0..4294967295` | 内核 flags，输出为八位大写十六进制；与 `timer` **独立**，**不**从 timer 推导；拒浮点、数字字符串 |
| `timer` | **是** | 精确 JSON 整数 `0..9223372036854775807` | 内核 timer，十进制 `%ld`；与 `flags` **独立**；拒浮点、数字字符串 |

parse 拒绝：非法 IPv6 文本、非组播（非 `ff00::/8`）、负数、小数、数字字符串、浮点、布尔当数字、`null`、溢出、未知字段、空值、`interfaceName` 未出现在 `network.interfaces`（含接口名重复配置）、本数组内重复 `interfaceName`+`groupIpv6` 组合（含同一地址的不同写法）、非对象数组条目。异常路径形如 `network.igmp6Memberships[i].field`。

### 接管路径与输出

仅**精确只读**打开下列路径时接管（写、`O_DIRECTORY`、目录枚举、相近路径均**不接管**）：

- `/proc/net/igmp6`
- `/proc/self/net/igmp6`
- `/proc/<emulatorPid>/net/igmp6`（`emulator.getPid()`，通常来自 `process.pid`）

格式来自 Linux 内核 `net/ipv6/mcast.c` `igmp6_mc_seq_show` 的 `"%-4d %-15s %pi6 %5d %08X %ld\n"`：

1. **无表头。** 显式 `[]` 为零字节。
2. 每条按 JSON 顺序输出一行：接口序号为 `%-4d`（复用同名 `network.interfaces[].index`）；接口名为 `%-15s`；IPv6 为 32 个不含冒号的大写十六进制字符（网络字节序，对应 `%pi6` 宽度）；`users` 为 `%5d`；`flags` 为 `%08X`；`timer` 为十进制 `%ld`；行以 LF 结尾。

示例（对应上方配置）：`ff02::1` → `FF020000000000000000000000000001`；`ff02::fb` → `FF0200000000000000000000000000FB`。整表以 LF 结尾（空表除外）。

**`linux.files` 优先：** 同一精确路径若已在 `linux.files` 配置，只返回该手工内容并发 `linux_file`，**不**走本渲染、**不**发下方 sidecar。

### Sidecar（旁路事件）

仅只读成功接管时写一次：

| 字段 | 值 |
| --- | --- |
| `kind` | `network_device` |
| `api` | `read("<请求绝对路径>")` |
| `source` | `json-config` |
| `value` | **仅** `path=<请求路径>,format=proc-net-igmp6,membershipCount=<条数>,interfaceCount=<接口数>,bytes=<字节数>` |
| `note` | 中文：`读取配置的 IGMP6 成员表 <path>` |

**脱敏：** sidecar **不**写接口名、`index`、IPv6 地址、flags 或 timer 细节。`interfaceCount` 只统计本配置数组出现过的接口名。**绝不**读取宿主机网络状态。

### 明确未实现（相对 `network.igmp6Memberships`）

- 写入、目录枚举、相近路径（如 `/proc/net/igmp6s`、`/proc/net/igmp`、`/proc/net/arp`、`/proc/net/route`）
- MLD / IGMP6 socket、真实多播加入/离开、ioctl、真实网络探测、宿主机 `/proc/net/igmp6` 读取
- `/proc/net/dev_mcast`（见 `network.linkLayerMulticastEntries`；本节点**不**推导链路层多播，也**不**实现真实多播加入）
- `/proc/net/wireless`（见 `network.wirelessProcStats`；本节点**不**推导无线统计）
- 由 interfaces / ipv4Routes / interfaceStats / ipv6Addresses / arpEntries / igmpMemberships / wifi / links **自动推导**任何组、用户、标志或计时器值

## network.linkLayerMulticastEntries

可选 **JSON 数组**，路径为根节点下的 `network.linkLayerMulticastEntries`。为应用常读的 Linux 链路层（二层）多播地址表提供**结构化、可复现**的只读文本，由 `TraceEnvironmentConfig.NetworkLinkLayerMulticastEntryConfig` + `ConfiguredLinkLayerMulticastFiles` 渲染；`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 接入。只模拟读取 `/proc/net/dev_mcast`（及 self/pid 别名）。这是**固定快照**：打开时按 JSON 原样输出，**不**随时间变化，也**不**实现真实多播加入、socket、ioctl、netlink 或任何动态网络行为。

**节点语义：**

- **键缺失：** **不接管** `/proc/net/dev_mcast`、`/proc/self/net/dev_mcast`、`/proc/<emulatorPid>/net/dev_mcast`，保留既有回退（含 `linux.files` 手工整段文本）。
- **显式 `[]`：** 视为已配置空表，**接管**上述三路径，输出**零字节**（无表头）。
- **有条目：** 按 JSON 数组顺序输出内核 `dev_mc_seq_show` 行。接口序号**仅**复用同名 `network.interfaces[].index`。MAC、`referenceCount`、`globalUse` **绝不**从 `network.interfaces[].mac`、`linkLayerBroadcast`、`network.wifi`、`network.ipv4Routes`、`network.interfaceStats`、`network.ipv6Addresses`、`network.arpEntries`、`network.igmpMemberships`、`network.igmp6Memberships`、`network.links` 或其它字段推导，也**不**读取宿主网络。

紧凑完整示例：

```json
{
  "network": {
    "interfaces": [
      { "name": "lo", "index": 1, "ipv4": "127.0.0.1" },
      { "name": "wlan0", "index": 2, "ipv4": "192.168.50.23" }
    ],
    "linkLayerMulticastEntries": [
      {
        "interfaceName": "lo",
        "mac": "01:00:5e:00:00:01",
        "referenceCount": 1,
        "globalUse": false
      },
      {
        "interfaceName": "wlan0",
        "mac": "01:00:5e:00:00:01",
        "referenceCount": 1,
        "globalUse": false
      },
      {
        "interfaceName": "wlan0",
        "mac": "01:00:5e:00:00:fb",
        "referenceCount": 2,
        "globalUse": true
      }
    ]
  }
}
```

### 字段表（严格白名单，全部必填）

| 字段 | 必填 | 类型与范围 | 说明 |
| --- | --- | --- | --- |
| `interfaceName` | **是** | 与 `network.interfaces[].name` 相同的接口名规则 | 必须是**已配置**且唯一的 `network.interfaces` 条目名称；本数组内同一 `interfaceName`+规范化 `mac` 组合**不得重复**（大小写视为同一 MAC）；**不**从 wifi/links/routes 推导 |
| `mac` | **是** | 严格六字节 MAC 文本，合法形式与规范化规则与 `network.interfaces[].mac` 相同 | 六个冒号分隔的两位十六进制八位组；parse 入库 Locale.ROOT 小写冒号格式（如 `01:00:5E:00:00:01` → `01:00:5e:00:00:01`）；输出时去掉冒号，写成 12 个连续小写 hex（内核 `%phN`）；**不**从 `interfaces[].mac` 或 `linkLayerBroadcast` 推导 |
| `referenceCount` | **是** | 精确 JSON 整数 `1..2147483647` | 内核 `refcount` 列，十进制左对齐宽度 5；拒 `0`、浮点、数字字符串 |
| `globalUse` | **是** | 严格 JSON Boolean | 内核 `global_use`；输出 `0`/`1`，左对齐宽度 5；拒数字、数字字符串、`null` |

parse 拒绝：非法 MAC（连字符/空白/非六段/非 hex/无冒号 12 hex）、负数、小数、数字字符串、浮点、布尔当数字、`null`、溢出、未知字段、空值、`interfaceName` 未出现在 `network.interfaces`（含接口名重复配置）、本数组内重复 `interfaceName`+规范化 `mac` 组合、非对象数组条目、`referenceCount` 为 `0` 或超出范围、`globalUse` 非严格 Boolean。异常路径形如 `network.linkLayerMulticastEntries[i].field`。

### 接管路径与输出

仅**精确只读**打开下列路径时接管（写、`O_DIRECTORY`、目录枚举、相近路径均**不接管**）：

- `/proc/net/dev_mcast`
- `/proc/self/net/dev_mcast`
- `/proc/<emulatorPid>/net/dev_mcast`（`emulator.getPid()`，通常来自 `process.pid`）

格式来自 Linux 内核 `net/core/net-procfs.c` `dev_mc_seq_show` 的 `"%-4d %-15s %-5d %-5d %phN\n"`：

1. **无表头。** 显式 `[]` 为零字节。
2. 每条按 JSON 顺序输出一行：接口序号为 `%-4d`（复用同名 `network.interfaces[].index`）；接口名为 `%-15s`；`referenceCount` 为 `%-5d`；`globalUse` 为 `0`/`1` 的 `%-5d`；MAC 为去掉冒号后的 12 个连续小写十六进制字符（`%phN` 对 6 字节 MAC，无冒号）；行以 LF 结尾。

示例（对应上方配置）：`01:00:5e:00:00:01` → `01005e000001`；`01:00:5e:00:00:fb` → `01005e0000fb`。整表以 LF 结尾（空表除外）。

**`linux.files` 优先：** 同一精确路径若已在 `linux.files` 配置，只返回该手工内容并发 `linux_file`，**不**走本渲染、**不**发下方 sidecar。

### Sidecar（旁路事件）

仅只读成功接管时写一次：

| 字段 | 值 |
| --- | --- |
| `kind` | `network_device` |
| `api` | `read("<请求绝对路径>")` |
| `source` | `json-config` |
| `value` | **仅** `path=<请求路径>,format=proc-net-dev-mcast,entryCount=<条数>,interfaceCount=<接口数>,bytes=<字节数>` |
| `note` | 中文：`读取配置的链路层多播表 <path>` |

**脱敏：** sidecar **不**写接口名、`index`、MAC、引用数或 `globalUse`。`interfaceCount` 只统计本配置数组出现过的接口名。**绝不**读取宿主机网络状态。

### 明确未实现（相对 `network.linkLayerMulticastEntries`）

- 写入、目录枚举、相近路径（如 `/proc/net/dev_mcasts`、`/proc/net/dev`、`/proc/net/igmp6`、`/proc/net/arp`）
- 真实多播加入/离开、socket、ioctl、netlink、真实网络探测、宿主机 `/proc/net/dev_mcast` 读取
- `/proc/net/wireless`（见 `network.wirelessProcStats`；本节点**不**推导无线统计）
- 由 interfaces.mac / linkLayerBroadcast / ipv4Routes / interfaceStats / ipv6Addresses / arpEntries / igmpMemberships / igmp6Memberships / wifi / links **自动推导**任何二层多播条目

## network.wirelessProcStats

可选 **JSON 对象**，路径为根节点下的 `network.wirelessProcStats`。为应用常读的 Linux `/proc/net/wireless` 提供**结构化、可复现**的只读固定文本快照，由 `TraceEnvironmentConfig.NetworkWirelessProcStatsConfig` + `ConfiguredWirelessProcStatsFiles` 渲染；`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 接入。只模拟读取 `/proc/net/wireless`（及 self/pid 别名）。这是**固定文本快照**：打开时按 JSON 原样输出，**不**随时间变化。`level` 与 `noise` 是最终写入 `/proc` 文本的有符号数，**不**模拟 `iw_statistics` 原始字节或 DBM 转换。**未实现** 无线 ioctl、netlink、真实 Wi-Fi 扫描或任何真实无线能力。

**节点语义：**

- **键缺失：** **不接管** `/proc/net/wireless`、`/proc/self/net/wireless`、`/proc/<emulatorPid>/net/wireless`，保留既有回退（含 `linux.files` 手工整段文本）。
- **显式空 `entries`：** 视为已配置空表，**接管**上述三路径，只输出固定两行表头（第二行 WE 列为 `wirelessExtensionsVersion`）。
- **有条目：** 按 JSON 数组顺序输出内核无线统计行。状态、质量、level/noise、updated 点号与 discard/missed **不**从 `network.interfaces`、`network.ipv4Routes`、`network.interfaceStats`、`network.ipv6Addresses`、`network.arpEntries`、`network.igmpMemberships`、`network.igmp6Memberships`、`network.linkLayerMulticastEntries`、`network.wifi`、`network.links` 或其它字段推导，也**不**读取宿主网络。

对象**只允许且必须有** `wirelessExtensionsVersion` 与 `entries` 两个键。

紧凑完整示例：

```json
{
  "network": {
    "interfaces": [
      { "name": "lo", "index": 1, "ipv4": "127.0.0.1" },
      { "name": "wlan0", "index": 2, "ipv4": "192.168.50.23" }
    ],
    "wirelessProcStats": {
      "wirelessExtensionsVersion": 22,
      "entries": [
        {
          "interfaceName": "lo",
          "status": 0,
          "linkQuality": 0,
          "level": 0,
          "noise": 0,
          "linkUpdated": false,
          "levelUpdated": false,
          "noiseUpdated": false,
          "discardNwid": 0,
          "discardCrypt": 0,
          "discardFragment": 0,
          "discardRetries": 0,
          "discardMisc": 0,
          "missedBeacon": 0
        },
        {
          "interfaceName": "wlan0",
          "status": 0,
          "linkQuality": 70,
          "level": -50,
          "noise": -90,
          "linkUpdated": true,
          "levelUpdated": true,
          "noiseUpdated": true,
          "discardNwid": 0,
          "discardCrypt": 0,
          "discardFragment": 0,
          "discardRetries": 1,
          "discardMisc": 0,
          "missedBeacon": 2
        }
      ]
    }
  }
}
```

### 对象字段

| 字段 | 必填 | 类型与范围 | 说明 |
| --- | --- | --- | --- |
| `wirelessExtensionsVersion` | **是** | 精确 JSON 整数 `0..999` | 写入表头第二行 WE 列（内核 `%d`）；**不**从宿主机或 `network.wifi` 推导 |
| `entries` | **是** | JSON 数组 | 显式 `[]` 只输出两行表头；条目见下表 |

### 条目字段表（严格白名单，全部必填）

| 字段 | 必填 | 类型与范围 | 说明 |
| --- | --- | --- | --- |
| `interfaceName` | **是** | 与 `network.interfaces[].name` 相同的接口名规则 | 必须是**已配置**且唯一的 `network.interfaces` 条目名称；本数组内**不得重复**；**不**从 wifi/links/routes 推导 |
| `status` | **是** | 精确 JSON 整数 `0..65535` | 内核 `%04x` |
| `linkQuality` | **是** | 精确 JSON 整数 `0..255` | 质量列数字 |
| `level` | **是** | 精确 JSON 整数 `-256..255` | **最终** `/proc` 文本有符号数；**不**做 DBM/原始字节转换 |
| `noise` | **是** | 精确 JSON 整数 `-256..255` | **最终** `/proc` 文本有符号数；**不**做 DBM/原始字节转换 |
| `linkUpdated` | **是** | 严格 JSON Boolean | `true` 在质量数字后写 `.`，`false` 写空格 |
| `levelUpdated` | **是** | 严格 JSON Boolean | `true` 在 level 数字后写 `.`，`false` 写空格 |
| `noiseUpdated` | **是** | 严格 JSON Boolean | `true` 在 noise 数字后写 `.`，`false` 写空格 |
| `discardNwid` | **是** | 精确 JSON 整数 `0..4294967295` | discarded nwid |
| `discardCrypt` | **是** | 精确 JSON 整数 `0..4294967295` | discarded crypt |
| `discardFragment` | **是** | 精确 JSON 整数 `0..4294967295` | discarded frag |
| `discardRetries` | **是** | 精确 JSON 整数 `0..4294967295` | discarded retry |
| `discardMisc` | **是** | 精确 JSON 整数 `0..4294967295` | discarded misc |
| `missedBeacon` | **是** | 精确 JSON 整数 `0..4294967295` | missed beacon |

parse 拒绝：浮点、数字字符串、布尔当数字、`null`、溢出、未知字段、非对象对象/条目、非法版本、`interfaceName` 未出现在 `network.interfaces`（含接口名重复配置）、本数组内重复 `interfaceName`。异常路径形如 `network.wirelessProcStats.field` 或 `network.wirelessProcStats.entries[i].field`。

### 接管路径与输出

仅**精确只读**打开下列路径时接管（写、`O_DIRECTORY`、目录枚举、相近路径均**不接管**）：

- `/proc/net/wireless`
- `/proc/self/net/wireless`
- `/proc/<emulatorPid>/net/wireless`（`emulator.getPid()`，通常来自 `process.pid`）

格式来自当前 Linux `net/wireless/wext-proc.c`：

1. 表头严格为：
   `Inter-| sta-|   Quality        |   Discarded packets               | Missed | WE\n`
   ` face | tus | link level noise |  nwid  crypt   frag  retry   misc | beacon | <wirelessExtensionsVersion>\n`
2. 每条为 `"%6s: %04x  %3d%c  %3d%c  %3d%c  %6d %6d %6d %6d %6d   %6d\n"`，按 JSON 数组顺序。只使用 JSON 值，**不**读取宿主机网络。

**`linux.files` 优先：** 同一精确路径若已在 `linux.files` 配置，只返回该手工内容并发 `linux_file`，**不**走本渲染、**不**发下方 sidecar。

### Sidecar（旁路事件）

仅只读成功接管时写一次：

| 字段 | 值 |
| --- | --- |
| `kind` | `network_device` |
| `api` | `read("<请求绝对路径>")` |
| `source` | `json-config` |
| `value` | **仅** `path=<请求路径>,format=proc-net-wireless,interfaceCount=<条数>,bytes=<字节数>` |
| `note` | 中文：`读取配置的无线统计 <path>` |

**脱敏：** sidecar **不**写接口名、无线版本或任何统计值。**绝不**读取宿主机网络状态。

### 明确未实现（相对 `network.wirelessProcStats`）

- 写入、目录枚举、相近路径（如 `/proc/net/wirelesss`、`/proc/net/dev`、`/proc/net/igmp6`）
- 无线 ioctl、netlink、真实 Wi-Fi 扫描、真实无线能力、宿主机 `/proc/net/wireless` 读取
- `/proc/net/dev_mcast`（见 `network.linkLayerMulticastEntries`；本节点**不**推导链路层多播）
- 由 interfaces / ipv4Routes / interfaceStats / ipv6Addresses / arpEntries / igmpMemberships / igmp6Memberships / linkLayerMulticastEntries / wifi / links **自动推导**任何无线统计

## filesystem.stat

按路径覆盖 Linux `struct stat` 元数据（文件属性）。路径为根级 **`filesystem.stat`**（可选 **JSON 对象**，路径键 → 字段对象）。实现类：`TraceEnvironmentConfig.FileStatConfig` + `ConfiguredFileStat`；路径 `stat`/`lstat`/`fstatat64`/`stat64` 与 FD `fstat` 在 `ARM32SyscallHandler` / `ARM64SyscallHandler` 中接线。

**设计边界：** 配置**不会**让原本不存在的路径变成可 resolve。仅当底层文件已能成功打开/解析且原 `fstat` 返回 `0` 后，才对 `StatStructure` 做字段覆盖。通常需配合 `linux.files`（或真实 rootfs 中已有文件）使路径可解析。

### 对象形状

```json
{
  "filesystem": {
    "stat": {
      "/proc/cpuinfo": {
        "device": 1414676803,
        "inode": 1414676804,
        "mode": 33060,
        "uid": 1000,
        "gid": 1000,
        "size": 4242,
        "blockSize": 4096,
        "blocks": 8,
        "atimeMillis": 1718000000000,
        "mtimeMillis": 1718000001000,
        "ctimeMillis": 1718000002000
      }
    }
  }
}
```

根级 `filesystem` 允许白名单键 `stat`、`statfs`、`mounts`、`links`、`externalStorage`、**`systemDirectories`**。`stat` 必须是 **路径键映射的 JSONObject**（不是数组）。每个路径条目必须是 JSONObject，且**至少包含一个** stat 字段（空 `{}` 非法）。

### 路径规范化（POSIX）

解析与查询共用统一 helper：

- 键非 null、非空、无首尾空白，必须以 `/` 开头。
- 禁止 NUL（`\0`）与反斜杠 `\`。
- 按 `/` 分段：忽略空段与 `.`；`..` 弹出一段，**越过根**非法。
- 结果：根为 `/`，其它为 `/` + 规范化段（如 `/data/./user/../user/0` → `/data/user/0`）。
- 规范化后路径**重复**（不同 JSON 键落到同一路径）→ parse 失败，异常路径含当前 `filesystem.stat["…"]` 前缀。

运行时 `getFilesystemStat` 对参数同样规范化：非法路径返回 `null`（不抛异常）；等价路径可命中。

### 节点缺失 vs 显式空

| 状态 | 行为 |
| --- | --- |
| **无 `filesystem` / 无 `stat` 键** | 未配置；`ConfiguredFileStat.apply` 不覆盖 |
| **`filesystem.stat`: `{}`** | 已配置但空映射；权威“无条目” |
| **有路径条目** | 仅配置路径在底层 `fstat` 成功后覆盖 |

### 字段说明

| 字段 | 类型与范围 | 写入目标 |
| --- | --- | --- |
| `device` | 精确 long `0..Long.MAX_VALUE` | `st_dev` |
| `inode` | 同上 | `setSt_ino` |
| `mode` | 精确 long `0..0xffffffff` | `st_mode`（**按位** `intValue`，32 位无符号满值 → 有符号 `-1`） |
| `uid` | 同上 | `st_uid`（按位 `intValue`） |
| `gid` | 同上 | `st_gid`（按位 `intValue`） |
| `size` | 精确 long `0..Long.MAX_VALUE` | `st_size` |
| `blockSize` | 精确 int `1..Integer.MAX_VALUE` | `st_blksize` |
| `blocks` | 精确 long `0..Long.MAX_VALUE` | `st_blocks` |
| `atimeMillis` / `mtimeMillis` / `ctimeMillis` | 精确 long，**完整** `Long.MIN..MAX` | `setSt_atim` / `setSt_mtim` / `setSt_ctim(..., 0)`；毫秒经 `floorDiv`/`floorMod` 转 `tv_sec`/`tv_nsec`（负毫秒合法） |

所有字段均可选；**仅已配置字段覆盖**，未配置字段保留底层 `fstat` 默认值。

### 接入的 syscall

| 路径 | 说明 |
| --- | --- |
| 路径型 `stat` / `stat64` / `lstat` / `fstatat64`（当前实现均落到 protected `stat64`） | resolve 成功且 `io.fstat==0` 后 `ConfiguredFileStat.apply(emulator, pathname, stat)` |
| FD 型 `fstat` | `file.fstat==0` 后 `ConfiguredFileStat.apply(emulator, file, stat)`（经 `FileIO.getPath()`；无 `getPath` 时不覆盖） |

无效 fd 仍为 `EBADF`（-1）；配置**不能**让 resolve 失败的路径变为存在。

### Sidecar（旁路事件）

成功覆盖并 `pack` 后 emit 一次：

| 字段 | 值 |
| --- | --- |
| `kind` | `filesystem_stat` |
| `api` | 路径重载 `stat`；FileIO/`fstat` 重载 `fstat` |
| `source` | `json-config` |
| `value` | 单行摘要：`path=<规范化路径>` + 仅已配置字段（顺序固定：device,inode,mode,uid,gid,size,blockSize,blocks,atimeMillis,mtimeMillis,ctimeMillis） |
| `note` | 中文：`读取配置的文件元数据 <path>` |

未配置、路径未命中、无路径的 FileIO **不**发事件。

### 明确未实现（相对 `filesystem.stat` 章节）

- 由 stat 配置自动创建文件内容或挂载表（路径存在性仍依赖 `linux.files` / 根目录真实文件）
- `statvfs` / `fstatvfs`（见下方 `filesystem.statfs` 与缺失清单）

## filesystem.statfs

按**挂载点**覆盖 Linux `struct statfs` 元数据（文件系统容量/类型等）。路径为根级 **`filesystem.statfs`**（可选 **JSON 对象**，挂载点键 → 字段对象）。实现类：`TraceEnvironmentConfig.FileStatFsConfig` + `ConfiguredFileStatFs`；路径型共享 `AndroidSyscallHandler.statfs64`、FD 型共享 `AndroidSyscallHandler.fstatfs64` 均在底层 `FileIO.statfs` 成功后覆盖。`fstatfs`/`fstatfs64` **复用同一套 JSON 字段**，按 fd 对应 `FileIO.getPath()` 的虚拟路径做最长挂载匹配。

**设计边界：**

- 配置**不会**让原本不存在的路径变成可 resolve；仅当路径已 resolve 且底层 `statfs` 返回 `0` 后才覆盖。
- 配置**不会**创造不存在的 fd，也**不**读宿主机；`fstatfs`/`fstatfs64` 只对 `fdMap` 中已有的 FileIO 生效。
- 路径型与 FD 型均使用**最长路径边界挂载匹配**（`getFilesystemStatFs`）：规范化后，挂载点等于查询路径，或挂载点为 `/`，或查询路径以 `mountPoint + "/"` 开头；**路径边界**保证 `/data` **不会**匹配 `/database`。FD 查询的路径来自 `io.getPath()` 虚拟路径。路径为 **null/空** 时**不**覆盖、**不**发 sidecar，仍返回底层 `statfs` 结果。
- **仅覆盖已配置字段**；缺失字段保留 unidbg 底层 `FileIO.statfs` 默认值（如 `DirectoryFileIO` 的固定容量画像）。
- 另有 `getFilesystemStatFsExact` 仅精确挂载点命中（不做最长前缀）。

### 对象形状

```json
{
  "filesystem": {
    "statfs": {
      "/": {
        "type": 61267,
        "blockSize": 4096,
        "blocks": 8388608,
        "blocksFree": 4194304,
        "blocksAvailable": 3984588,
        "files": 2097152,
        "filesFree": 1887436,
        "fsid": [1414676803, 1414676804],
        "nameLength": 255,
        "fragmentSize": 4096,
        "flags": 1056
      },
      "/data": {
        "type": 61267,
        "blockSize": 4096,
        "blocks": 16777216,
        "blocksFree": 8388608,
        "blocksAvailable": 7969176,
        "files": 4194304,
        "filesFree": 3774872,
        "fsid": [1414676810, 1414676811],
        "nameLength": 255,
        "fragmentSize": 4096,
        "flags": 1056
      }
    }
  }
}
```

`statfs` 必须是 **挂载点键映射的 JSONObject**（不是数组）。挂载点键复用与 `filesystem.stat` 相同的 POSIX 绝对路径规范化规则；规范化重复拒绝。每个条目必须是非空 JSONObject（空 `{}` 非法）。显式顶层 `filesystem.statfs: {}` 合法（已配置、无条目）。

### 字段说明（影响 Linux `statfs` / `statfs64` / `fstatfs` / `fstatfs64` 返回结构）

| 字段 | 类型与范围 | 写入目标（`struct statfs`） |
| --- | --- | --- |
| `type` | 精确 long `0..0xffffffff` | `f_type`（`setType`，**按位** `intValue`，满值 → 有符号 `-1`） |
| `blockSize` | 精确 int `1..Integer.MAX_VALUE` | `f_bsize`（`setBlockSize`） |
| `blocks` | 精确 long `0..Long.MAX_VALUE` | `f_blocks` 总数据块数 |
| `blocksFree` | 同上 | `f_bfree` 空闲块数 |
| `blocksAvailable` | 同上 | `f_bavail` 非特权可用块数 |
| `files` | 同上 | `f_files` 总 inode/文件节点数 |
| `filesFree` | 同上 | `f_ffree` 空闲文件节点数 |
| `fsid` | 长度恰好为 2 的 JSONArray，每项精确 long `0..0xffffffff` | `f_fsid[2]`（每项 **按位** 转 `int` 低 32 位） |
| `nameLength` | 精确 int `1..Integer.MAX_VALUE` | `f_namelen`（`setNameLen`） |
| `fragmentSize` | 同上 | `f_frsize`（`setFrSize`） |
| `flags` | 精确 long `0..0xffffffff` | `f_flags`（`setFlags`，按位 `intValue`） |

所有字段均可选；**仅已配置字段覆盖**。

### 接入的 syscall

| 路径 | 说明 |
| --- | --- |
| `statfs` / `statfs64`（ARM32/64 均调用共享 `AndroidSyscallHandler.statfs64`） | resolve 成功且 `io.statfs==0` 后 `ConfiguredFileStatFs.apply(emulator, pathname, statFS)`（sidecar `api=statfs`） |
| `fstatfs` / `fstatfs64`（ARM64 NR=44；ARM32 NR=267 `fstatfs64`） | 从 `fdMap` 取 FileIO；无效 fd 返回 `-1`/`EBADF`。有效 fd 且 `io.statfs==0` 后按 `io.getPath()` 调用 `ConfiguredFileStatFs.apply(..., "fstatfs")`。ARM32 的 `size` 须等于当前架构 `StatFS32` 大小，否则 `-1`/`EINVAL` 且**不写** buffer |

底层失败、路径不存在或 fd 无效时**原样**返回错误；配置不能创造路径或 fd 存在性。

### Sidecar（旁路事件）

成功覆盖并 `pack` 后 emit 一次：

| 字段 | 值 |
| --- | --- |
| `kind` | `filesystem_statfs` |
| `api` | 路径型 `statfs`；FD 型 `fstatfs` |
| `source` | `json-config` |
| `value` | 单行摘要：`mountPoint=<规范化挂载点>` + 仅已配置字段（顺序固定：type,blockSize,blocks,blocksFree,blocksAvailable,files,filesFree,fsid,nameLength,fragmentSize,flags） |
| `note` | 中文：`读取配置的文件系统 statfs <mountPoint>` |

未配置、无挂载匹配、FD 路径为 null/空 **不**发事件。无 sink 时无副作用。摘要**不含** fd 数值。

### 明确未实现（相对 `filesystem.statfs`）

- **`statvfs` / `fstatvfs` 的结构转换**（本轮让 Bionic `fstatvfs`/`fstatvfs64` 所依赖的底层 `fstatfs`/`fstatfs64` 吃到同一套 JSON 字段；**不**实现 `statvfs` 结构体转换）
- 由 statfs 配置自动创建挂载表、文件内容或不存在的 fd

## filesystem.mounts

按 **JSON 数组** 配置 fstab / mountinfo 风格挂载表，供 `LinuxFileSystem` 生成 proc 文本。路径为根级 **`filesystem.mounts`**（可选 **JSONArray**）。实现类：`TraceEnvironmentConfig.FileSystemMountConfig` + `ConfiguredMountFiles` 渲染；`LinuxFileSystem.open` 在 **`linux.files` 精确命中之后** 再接入本表。

**设计边界：**

- **`linux.files` 优先**：同一路径若已在 `linux.files` 配置，直接返回该内容并发 `linux_file` 事件，**不**走 mounts 渲染、**不**发 `filesystem_mounts` 事件。
- `/proc/mounts`、`/proc/self/mounts`、`/proc/<配置 pid>/mounts` → `ConfiguredMountFiles.renderProcMounts`。
- `/proc/self/mountinfo`、`/proc/<配置 pid>/mountinfo` → `renderMountInfo`。
- **`/etc/mtab`：** 配置挂载表的**兼容只读视图**；仅精确路径、只读 open。内容与 `/proc/mounts` / `renderProcMounts` **完全相同**（UTF-8 fstab 风格，按 JSON 顺序且以 LF 结尾）。**键缺失不接管**；显式 `[]` 接管并返回零字节。写、`O_DIRECTORY`、`/etc`、`/etc/mtab/`、`/etc/mtab.bak`、`/system/etc/mtab` 及其它近似路径 **不接管**。**不**创建真实 symlink，**不**改变 stat/readlink。
- 渲染结果 **非 null**（含显式 `[]` 的空字节）时返回只读 `ByteArrayFileIO`；**null** 时继续旧 fallback（不创建其它路径）。
- **mountinfo 完整性：** 只要存在任一条目未配置完整四元组 `mountId`/`parentId`/`major`/`minor`，则 **整个** `renderMountInfo` 返回 null（绝不输出部分列表）；`/proc/mounts` 仍可对最小条目渲染。
- 不创建真实挂载目录；不做 namespace / 动态挂载 / mount / umount。

### 对象形状

```json
{
  "filesystem": {
    "mounts": [
      {
        "source": "/dev/block/dm-0",
        "target": "/",
        "fileSystemType": "ext4",
        "options": "ro,seclabel,relatime",
        "dump": 1,
        "pass": 1,
        "mountId": 21,
        "parentId": 1,
        "major": 253,
        "minor": 0,
        "root": "/",
        "mountOptions": "ro,seclabel,relatime",
        "optionalFields": ["shared:1"],
        "superOptions": "rw,seclabel"
      },
      {
        "source": "/dev/block/dm-1",
        "target": "/data",
        "fileSystemType": "f2fs",
        "options": "rw,nosuid,nodev,noatime,seclabel",
        "dump": 0,
        "pass": 2,
        "mountId": 45,
        "parentId": 21,
        "major": 253,
        "minor": 1,
        "root": "/",
        "mountOptions": "rw,nosuid,nodev,noatime,seclabel",
        "optionalFields": ["shared:2"],
        "superOptions": "rw,seclabel"
      }
    ]
  }
}
```

- `mounts` 必须是 **JSONArray**（不是对象）；显式 `[]` 合法且 `isFilesystemMountsConfigured=true`，打开上述路径（含精确 `/etc/mtab`）得到**空文件**。
- 每项必须是**非空** JSONObject；数组顺序即输出顺序。
- **target** 经 POSIX 绝对路径规范化后**去重**（重复 → parse 失败）。
- **mountId** 在完整 mountinfo 组内**唯一**（重复 → 错误路径指向后一项 `filesystem.mounts[i].mountId` 且含 `duplicate`）。

### 字段表

| 字段 | 必填 | 类型与范围 / 默认 | 说明 |
| --- | --- | --- | --- |
| `source` | **是** | 非空 String，禁止 NUL/CR/LF | 设备/源（fstab 第 1 列）；输出时对空格/TAB/换行/反斜杠做 Linux 转义 |
| `target` | **是** | POSIX 绝对路径 | 挂载点（第 2 列）；规范化后去重 |
| `fileSystemType` | **是** | 非空 token，禁止空白/NUL | 类型（第 3 列），如 `ext4`/`f2fs` |
| `options` | **是** | 非空 String，禁止空白/NUL/CR/LF | fstab 选项（第 4 列）；逗号列表原样保留 |
| `dump` | 否 | 精确 int `0..Integer.MAX_VALUE`；默认 `0` | fstab 第 5 列 |
| `pass` | 否 | 同上；默认 `0` | fstab 第 6 列 |
| `mountId` | 组 | 精确 int `1..Integer.MAX_VALUE` | mountinfo 挂载 id；**四字段全有或全无** |
| `parentId` | 组 | 同上 | 父挂载 id；可引用未出现的外部 id |
| `major` | 组 | 精确 int `0..Integer.MAX_VALUE` | 设备主号；可重复 |
| `minor` | 组 | 同上 | 设备次号；可重复 |
| `root` | 否 | POSIX 绝对路径；默认 `/` | mountinfo 内 root；规范化 |
| `mountOptions` | 否 | 同 `options` 约束；默认等于 `options` | per-mount 选项 |
| `optionalFields` | 否 | JSONArray of 非空 token；默认不可变空列表 | 如 `shared:1`；出现在 `-` 分隔符前 |
| `superOptions` | 否 | 同 `options` 约束；默认等于 `options` | 超级块选项 |

### 输出格式

**`/proc/mounts` 与精确 `/etc/mtab` 行（每项一条，以 `\n` 结尾；二者字节相同）：**

```text
source target fileSystemType options dump pass
```

**`/proc/.../mountinfo` 行：**

```text
mountId parentId major:minor root target mountOptions [optionalFields...] - fileSystemType source superOptions
```

**路径类字段转义（source / target / root）：** 按 Linux 挂载表逐字符转义——`\`→`\134`、空格→`\040`、TAB→`\011`、LF→`\012`（先处理反斜杠，避免二次转义）。其余 token 已由配置模型校验。

### Sidecar（旁路事件）

成功由配置生成 `ByteArrayFileIO` 时 emit **一次**：

| 字段 | 值 |
| --- | --- |
| `kind` | `filesystem_mounts` |
| `api` | `read("<请求绝对路径>")`（如 `read("/proc/self/mounts")`、`read("/etc/mtab")`） |
| `source` | `json-config` |
| `value` | proc：`path=<请求路径>,format=mounts\|mountinfo,entries=<条数>`；`/etc/mtab`：`path=/etc/mtab,format=mtab,count=<条数>,bytes=<字节数>`（**不**写 source/target/fileSystemType/options 等挂载具体内容） |
| `note` | 中文：`读取配置的挂载表 <path>` |

`linux.files` 命中路径时**不**发本事件。未配置 / 渲染 null **不**发事件。

### 明确未实现（相对 `filesystem.mounts`）

- 挂载目录自动创建 / 真实 mount / umount
- 其它 mtab 别名（如 `/etc/mtab.bak`、`/system/etc/mtab`）；精确 `/etc/mtab` 只读兼容视图**已实现**
- 动态挂载、namespace、bind 语义

## filesystem.links

按**路径键映射**配置符号链接目标，供共享 `UnixSyscallHandler.readlink` 在 **配置命中时** 返回固定字节。路径为根级 **`filesystem.links`**（可选 **JSONObject**：链接绝对路径 → 目标 String）。实现类：`TraceEnvironmentConfig.FileSystemLinkConfig`；运行时在 ARM32/64 的 `readlink` / `readlinkat(AT_FDCWD)` 均落到共享 `readlink`（**已接线**；精确命中 + `/proc/<pid>/`→`/proc/self/` 查询别名；**不**创建真实 symlink 节点）。

### 对象形状

```json
{
  "filesystem": {
    "links": {
      "/proc/self/exe": "/system/bin/app_process64",
      "/proc/self/fd/0": "/dev/null",
      "/data/local/tmp/rel": "../cache/foo"
    }
  }
}
```

- `links` 必须是 **JSONObject**（不是数组）。显式 `{}` 合法且 presence=true（权威空映射）。
- **键（链接路径）**：复用 POSIX 绝对路径规范化；规范化后**去重**（重复 → parse 失败）。
- **值（目标）**：非空 String，**原样保存**（不做路径规范化）；允许**绝对或相对**目标，允许空格与反斜杠；**禁止** NUL / CR / LF。

### 运行时行为（`readlink`）

| 规则 | 说明 |
| --- | --- |
| 精确优先 | `getFilesystemLinkExact(请求 path)` 规范化后精确命中 |
| pid → self 别名 | 未命中且 path 以 `/proc/<emulator pid>/` 开头时，再查等价 `/proc/self/...`（仅查询改写，事件 `api` 仍用**原请求 path**） |
| 配置优先于动态 fd | 配置命中时**先于**原有 `/proc/self/fd/*` 动态 `fdMap` 解析 |
| UTF-8 写入 | 将 `target` 的 UTF-8 字节写入 `buf`，写入数 `min(bufSize, targetBytes.length)` |
| 不追加 NUL | 成功路径**绝不**在结果末尾写 `\\0`（符合 Linux `readlink`） |
| 短缓冲截断 | 按**字节**截断，不保证完整码点 |
| EINVAL | `buf == null` 或 `bufSize <= 0` 时 `errno=EINVAL`，返回 `-1`，**不**发成功 sidecar |
| 回退 | `links` **缺失**、显式 `{}` 无条目、或路径未命中 → **保持**旧动态/默认 `readlink` 逻辑不变 |

### Sidecar

成功配置命中后 emit **一次**：

| 字段 | 值 |
| --- | --- |
| `kind` | `filesystem_link` |
| `api` | `readlink("<原请求 path>")` |
| `source` | `json-config` |
| `value` | 稳定摘要：`path=<原请求 path>,target=<配置目标>` |
| `note` | 中文：`读取配置的符号链接 <path>` |

### 明确未实现 / 设计边界

- **当前只覆盖** `readlink` 与 `readlinkat(AT_FDCWD)`（落到共享 handler）；**不**支持相对 `dirfd` 的 `readlinkat`。
- **不**在文件系统中创建真实 symlink 节点。
- **不**改变 `open` / `stat` 等路径存在性（配置链接**不**使目标或链接路径自动可 open）。
- 不做通用 symlink 跟随 / 路径解析替换。

## filesystem.externalStorage

主外部存储（primary external storage）画像子集（**四字段**：`directory` / `state` / `emulated` / `removable`）。路径为根级 **`filesystem.externalStorage`**（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.FileSystemExternalStorageConfig`；JNI 接线在 `AbstractJni`：**静态** `Environment` API（`callStaticObjectMethod` / `V` 与 `callStaticBooleanMethod` / `V`，VarArg + VaList）的无参四接口 + **仅匹配主目录**的 File 参数重载 + `getExternalStoragePublicDirectory`；以及**实例** `Context` / `ContextWrapper` / `Application` 的 **`getExternalFilesDir(String)`** / **`getExternalFilesDirs(String)`** / **`getExternalCacheDir()`** / **`getExternalCacheDirs()`** / **`getObbDir()`** / **`getObbDirs()`** / **`getExternalMediaDirs()`**（依赖 VM 包名，见下）。**说明：** `Environment.getStorageDirectory` **不**属本节点，见下方 **`filesystem.systemDirectories.storageDirectory`**。

### 节点缺失 vs 显式空对象

| 状态 | `isFilesystemExternalStorageConfigured()` | `getFilesystemExternalStorageConfig()` | 行为摘要 |
| --- | --- | --- | --- |
| **无 `filesystem` / 无 `externalStorage` 键** | `false` | `null` | 无参与 File 重载 `Environment` API 与 `Context.getExternalFilesDir` / `getExternalFilesDirs` / `getExternalCacheDir` / `getExternalCacheDirs` / `getObbDir` / `getObbDirs` / `getExternalMediaDirs` 均保持 `UnsupportedOperationException`，**不发** sidecar |
| **显式 `{}`** | `true` | 非 null | 默认 `directory=/storage/emulated/0`、`state=mounted`、`emulated=true`、`removable=false` |
| **有字段** | `true` | 非 null | 已配字段覆盖默认 |

### 字段说明（仅允许下列 4 键）

| 字段 | 必填 | 类型与校验 | 默认（键缺失时） |
| --- | --- | --- | --- |
| `directory` | 否 | 非 null **String**；POSIX **绝对路径**（与 `filesystem.stat` 相同规范化：须以 `/` 开头，禁 NUL/反斜杠，`..` 不得越根）；存规范化结果 | `/storage/emulated/0` |
| `state` | 否 | 严格 **String** 枚举（大小写敏感）：`unknown` / `removed` / `unmounted` / `checking` / `nofs` / `mounted` / `mounted_ro` / `shared` / `bad_removal` / `unmountable` | `mounted` |
| `emulated` | 否 | 严格 JSON Boolean（拒绝 null / String / Number） | `true` |
| `removable` | 否 | 严格 JSON Boolean | `false` |

未知键在 parse 时抛 `IllegalArgumentException`，路径形如 `filesystem.externalStorage.<key>`。`directory` 非法/非绝对/越根、`state` 非法枚举或非 String、布尔字段类型错误时，错误路径分别为 `filesystem.externalStorage.directory` / `.state` / `.emulated` / `.removable`。配置对象不可变；提供各值 getter 与 `isDirectoryConfigured` / `isStateConfigured` / `isEmulatedConfigured` / `isRemovableConfigured`。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 接入 | 已配置 | 未配置 / 未命中 |
| --- | --- | --- | --- |
| `android/os/Environment->getExternalStorageDirectory()Ljava/io/File;` | `callStaticObjectMethod` / `V` | 返回 `java.io.File`（value=`new File(directory)`，**不**创建宿主目录/FileIO） | UOE，无事件 |
| `android/os/Environment->getExternalStorageState()Ljava/lang/String;` | 同上 | 返回 `StringObject(state)` | 同上 |
| `android/os/Environment->isExternalStorageEmulated()Z` | `callStaticBooleanMethod` / `V` | 返回 `emulated` | 同上 |
| `android/os/Environment->isExternalStorageRemovable()Z` | 同上 | 返回 `removable` | 同上 |
| `android/os/Environment->getExternalStorageState(Ljava/io/File;)Ljava/lang/String;` | `callStaticObjectMethod` / `V` | 第 0 参为 **DvmObject** 且 value 为 **`java.io.File`**，并与 `new File(directory)` **equals** 时返回 `StringObject(state)` | 节点缺失，或 null / 非 File / **非主目录** File：UOE，**不发**事件 |
| `android/os/Environment->isExternalStorageEmulated(Ljava/io/File;)Z` | `callStaticBooleanMethod` / `V` | 同上路径匹配时返回 `emulated` | 同上 |
| `android/os/Environment->isExternalStorageRemovable(Ljava/io/File;)Z` | 同上 | 同上路径匹配时返回 `removable` | 同上 |
| **`android/os/Environment->getExternalStoragePublicDirectory(Ljava/lang/String;)Ljava/io/File;`** | `callStaticObjectMethod` / `V` | arg0 为**非空**单段目录名 String（**无** `/`、`\\`、NUL、CR、LF）→ `java.io.File` value=`new File(directory, type)`（**不**创建宿主目录/FileIO；**不**校验 `DIRECTORY_*` 白名单） | 节点缺失，或 null / 非 String / 空串 / 含分隔符或控制字符：UOE，**不发**事件 |
| **`android/content/Context`/`ContextWrapper`/`Application->getExternalFilesDir(Ljava/lang/String;)Ljava/io/File;`** | `callObjectMethod` / `V` | 需节点存在且 VM **`packageName` 非空**；根路径 `<directory>/Android/data/<packageName>/files`；**type 为 null** → 根路径；**非 null** 须非空单段名（无 `/`\\NUL/CR/LF）再追加（**不**创建宿主目录/FileIO；**无**多卷） | 节点缺失、无包名、空串/非法 type：UOE，**不发**事件 |
| **`android/content/Context`/`ContextWrapper`/`Application->getExternalFilesDirs(Ljava/lang/String;)[Ljava/io/File;`** | `callObjectMethod` / `V` | 需节点存在且 VM **`packageName` 非空**；返回**一元** `File[]`（`ArrayObject`），唯一元素与 `getExternalFilesDir` 同路径规则（null type→根；非 null 单段追加）（**不**创建宿主目录/FileIO；**无**多卷/`StorageManager`/新配置字段） | 节点缺失、无包名、空串/非法 type：UOE，**不发**事件 |
| **`android/content/Context`/`ContextWrapper`/`Application->getExternalCacheDir()Ljava/io/File;`** | `callObjectMethod` / `V` | 需节点存在且 VM **`packageName` 非空**；返回 `java.io.File` value=`<directory>/Android/data/<packageName>/cache`（**不**创建宿主目录/FileIO；**无**多卷拓扑） | 节点缺失、无包名：UOE，**不发**事件 |
| **`android/content/Context`/`ContextWrapper`/`Application->getExternalCacheDirs()[Ljava/io/File;`** | `callObjectMethod` / `V` | 需节点存在且 VM **`packageName` 非空**；返回**一元** `File[]`（`ArrayObject`），唯一元素与 `getExternalCacheDir` 同路径（**不**创建宿主目录/FileIO；**无**多卷/`StorageManager`/新配置字段） | 节点缺失、无包名：UOE，**不发**事件 |
| **`android/content/Context`/`ContextWrapper`/`Application->getObbDir()Ljava/io/File;`** | `callObjectMethod` / `V` | 需节点存在且 VM **`packageName` 非空**；返回 `java.io.File` value=`<directory>/Android/obb/<packageName>`（**仅路径**；**不**创建宿主目录/FileIO/OBB 内容；**无**多卷） | 节点缺失、无包名：UOE，**不发**事件 |
| **`android/content/Context`/`ContextWrapper`/`Application->getObbDirs()[Ljava/io/File;`** | `callObjectMethod` / `V` | 需节点存在且 VM **`packageName` 非空**；返回**一元** `File[]`（`ArrayObject`），唯一元素与 `getObbDir` 同路径（**仅路径**；**不**创建宿主目录/FileIO/OBB 内容；**无**多卷/`StorageManager`/新配置字段） | 节点缺失、无包名：UOE，**不发**事件 |
| **`android/content/Context`/`ContextWrapper`/`Application->getExternalMediaDirs()[Ljava/io/File;`** | `callObjectMethod` / `V` | 需节点存在且 VM **`packageName` 非空**；返回**新**一元 `File[]`（`ArrayObject`），唯一元素 `java.io.File` value=`<directory>/Android/media/<packageName>`（**仅主卷路径**；**不**创建宿主目录/FileIO/媒体扫描；**无**多卷/`StorageManager`/新配置字段） | 节点缺失、无包名：UOE，**不发**事件 |

**File 重载模型：** **仅**配置的**主外部存储目录**（`directory`）；arg0 须为 value=`java.io.File` 且 `new File(directory).equals(file)`。无参行为与 sidecar 值**不变**。

**Public directory 模型：** 使用配置主目录 `directory` 与 type 单段名拼接；type **不**映射为配置字段、**无**新 JSON 键；多卷/`StorageManager` **不**涉及。

**`getExternalFilesDir` / `getExternalFilesDirs` / `getExternalCacheDir` / `getExternalCacheDirs` / `getObbDir` / `getObbDirs` / `getExternalMediaDirs` 模型：** 使用配置 `directory` + VM 包名（`android.packageName` 或 APK）拼 Android 标准应用外部 `files` / `cache` / `obb` / `media` 路径；`getExternalFilesDirs`/`getExternalCacheDirs`/`getObbDirs`/`getExternalMediaDirs` 固定一元数组（主卷）；type **不**映射为配置字段、**无**新 JSON 键；**不含** OBB 内容文件、媒体扫描、多卷拓扑。

**示例（`example/trace-env.example.json`）：** `directory=/storage/emulated/0` 时，`getExternalStoragePublicDirectory("DCIM")` → `new File("/storage/emulated/0","DCIM")`（sidecar `type=DCIM,result=/storage/emulated/0/DCIM`）；若包名 `com.demo.app`，`getExternalFilesDir(null)` → `…/Android/data/com.demo.app/files`，`getExternalFilesDirs(null)` → 一元 `File[]` 同 files 路径，`getExternalFilesDir("Documents")` / `getExternalFilesDirs("Documents")` → `…/files/Documents`，`getExternalCacheDir()` → `…/Android/data/com.demo.app/cache`，`getExternalCacheDirs()` → 一元 `File[]` 同 cache 路径，`getObbDir()` → `…/Android/obb/com.demo.app`，`getObbDirs()` → 一元 `File[]` 同 obb 路径，`getExternalMediaDirs()` → 一元 `File[]` 元素 `…/Android/media/com.demo.app`（**不**创建宿主目录/OBB 内容/媒体扫描）。

### Sidecar（旁路事件，仅配置节点存在且命中对应 API 时）

| 字段 | 无参 `getExternalStorageDirectory` | 无参 `getExternalStorageState` / `isExternalStorageEmulated` / `isExternalStorageRemovable` | File 重载（主目录匹配成功） | **`getExternalStoragePublicDirectory`** | **`Context.getExternalFilesDir`** | **`Context.getExternalFilesDirs`** | **`Context.getExternalCacheDir`** | **`Context.getExternalCacheDirs`** | **`Context.getObbDir`** | **`Context.getObbDirs`** | **`Context.getExternalMediaDirs`** |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `kind` | `filesystem_external_storage` | `filesystem_external_storage` | `filesystem_external_storage` | `filesystem_external_storage` | `filesystem_external_storage` | `filesystem_external_storage` | `filesystem_external_storage` | `filesystem_external_storage` | `filesystem_external_storage` | `filesystem_external_storage` | `filesystem_external_storage` |
| `api` | `Environment.getExternalStorageDirectory` | `Environment.getExternalStorageState` / `isExternalStorageEmulated` / `isExternalStorageRemovable`（与无参**同名**） | 同上（与无参 **同名**） | **`Environment.getExternalStoragePublicDirectory`** | **`Context.getExternalFilesDir`** | **`Context.getExternalFilesDirs`** | **`Context.getExternalCacheDir`** | **`Context.getExternalCacheDirs`** | **`Context.getObbDir`** | **`Context.getObbDirs`** | **`Context.getExternalMediaDirs`** |
| `value` | **精确** `field=directory,result=<path>` | **精确** `field=state,result=<state>` / `field=emulated,result=true\|false` / `field=removable,result=true\|false` | **精确** `field=state,path=<directory>,result=<state>` / `field=emulated,path=<directory>,result=true\|false` / `field=removable,path=<directory>,result=true\|false` | **精确** `type=<type>,result=<directory>/<type>`（POSIX 拼接摘要） | null type：`result=<directory>/Android/data/<pkg>/files`；有 type：`type=<t>,result=…/files/<t>` | null type：`count=1,result=…/files`；有 type：`count=1,type=<t>,result=…/files/<t>` | **精确** `result=<directory>/Android/data/<pkg>/cache` | **精确** `count=1,result=<directory>/Android/data/<pkg>/cache` | **精确** `result=<directory>/Android/obb/<pkg>` | **精确** `count=1,result=<directory>/Android/obb/<pkg>` | **精确** `count=1,result=<directory>/Android/media/<pkg>` |
| `source` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` | `json-config` |
| `note` | 中文简述（读取配置的外部存储目录） | 中文简述（读取配置的外部存储状态 / 是否模拟 / 是否可移除） | 同对应无参 note | 读取配置的外部存储公共目录 | 读取配置的外部应用 files 目录 | 读取配置的外部应用 files 目录数组 | 读取配置的外部应用 cache 目录 | 读取配置的外部应用 cache 目录数组 | 读取配置的外部应用 OBB 目录 | 读取配置的外部应用 OBB 目录数组 | 读取配置的外部应用 media 目录数组 |

null / 非 File / 非主目录 File / 非法 type / 无包名 / 节点缺失路径 **不** emit。

### 明确未实现

- **非主目录** File 卷画像、多路径 `Environment` File 重载语义（当前**仅**主 `directory` 命中）
- `DIRECTORY_*` 常量白名单校验、公共目录枚举列表、多段相对 type
- **真实 FileIO / 目录创建 / 可用空间**（配置 **不**自动创建 `/storage/...` 节点，**不**提供 free space / `StatFs` 派生）
- 多卷次级存储、`StorageManager` 卷列表与可移动卷拓扑
- **媒体扫描 / MediaStore 索引**（`getExternalMediaDirs` **仅**返回主卷路径数组）

## filesystem.systemDirectories

Android 系统目录四路径子集（**可选** `rootDirectory` / `dataDirectory` / `downloadCacheDirectory` / **`storageDirectory`**）。路径为根级 **`filesystem.systemDirectories`**（可选 **JSONObject**）。实现类：`TraceEnvironmentConfig.FileSystemSystemDirectoriesConfig`；JNI 接线在 `AbstractJni` 的静态无参 `Environment` API（`callStaticObjectMethod` / `V`，VarArg + VaList）。**彼此与 `filesystem.externalStorage` 独立**；**不**创建宿主目录或 FileIO 节点；**无**多卷语义。

### 节点缺失 vs 显式空对象 vs 字段

| 状态 | `isFilesystemSystemDirectoriesConfigured()` | 行为摘要 |
| --- | --- | --- |
| **无 `filesystem` / 无 `systemDirectories` 键** | `false` | 四 API 均保持历史 UOE，**不发** sidecar |
| **显式 `{}`** | `true` | 四字段均未配置 → 四 API 仍 UOE（**无**默认路径） |
| **某字段显式存在** | `true` | **仅**该字段对应 API 返回配置路径；其它未配字段仍 UOE |

### 字段说明（仅允许下列 4 键）

| 字段 | 必填 | 类型与校验 | 默认 |
| --- | --- | --- | --- |
| `rootDirectory` | 否 | 非 null **String**；POSIX **绝对路径**（与 `filesystem.stat` / `externalStorage.directory` 相同规范化） | **无**（键缺失则 API 不拦截） |
| `dataDirectory` | 否 | 同上 | **无** |
| `downloadCacheDirectory` | 否 | 同上 | **无** |
| **`storageDirectory`** | 否 | 同上 | **无** |

未知键在 parse 时抛 `IllegalArgumentException`，路径形如 `filesystem.systemDirectories.<key>`。路径非法/非绝对/越根时错误路径分别为 `.rootDirectory` / `.dataDirectory` / `.downloadCacheDirectory` / **`.storageDirectory`**。配置对象不可变；提供 getter 与 `isRootDirectoryConfigured` / `isDataDirectoryConfigured` / `isDownloadCacheDirectoryConfigured` / **`isStorageDirectoryConfigured`**。

### 对 `AbstractJni` 的精确影响

| 精确签名 | 接入 | 已显式配置对应字段 | 未配置 / 节点缺失 |
| --- | --- | --- | --- |
| `android/os/Environment->getRootDirectory()Ljava/io/File;` | `callStaticObjectMethod` / `V` | 返回 `java.io.File`（value=`new File(rootDirectory)`，**不**创建宿主目录/FileIO） | UOE，无事件 |
| `android/os/Environment->getDataDirectory()Ljava/io/File;` | 同上 | value=`new File(dataDirectory)` | 同上 |
| `android/os/Environment->getDownloadCacheDirectory()Ljava/io/File;` | 同上 | value=`new File(downloadCacheDirectory)` | 同上 |
| **`android/os/Environment->getStorageDirectory()Ljava/io/File;`** | 同上 | value=`new File(storageDirectory)` | 同上 |

### Sidecar（仅对应字段显式配置且命中时）

| 字段 | `getRootDirectory` | `getDataDirectory` | `getDownloadCacheDirectory` | **`getStorageDirectory`** |
| --- | --- | --- | --- | --- |
| `kind` | `filesystem_system_directories` | 同左 | 同左 | 同左 |
| `api` | `Environment.getRootDirectory` | `Environment.getDataDirectory` | `Environment.getDownloadCacheDirectory` | **`Environment.getStorageDirectory`** |
| `value` | **精确** `field=rootDirectory,result=<path>` | **精确** `field=dataDirectory,result=<path>` | **精确** `field=downloadCacheDirectory,result=<path>` | **精确** `field=storageDirectory,result=<path>` |
| `source` | `json-config` | `json-config` | `json-config` | `json-config` |
| `note` | 读取配置的系统 root 目录 | 读取配置的系统 data 目录 | 读取配置的系统 download cache 目录 | 读取配置的系统 storage 目录 |

### 明确未实现

- 公共目录变体 / 其它未列出 `Environment` 路径 API
- **真实 FileIO / 目录创建 / 可用空间 / 多卷拓扑**（配置 **不**自动创建 `/system`、`/data`、`/cache`、`/storage` 等节点）
- 与 `linux.files` / `filesystem.stat` / `filesystem.externalStorage` 自动一致性校验

## 快速验证点

把样例 JSON 中的值改成明显字符串或数字，然后验证：

| 验证项 | 预期来源 |
| --- | --- |
| `Process.myPid()` / `Process.myTid()` / `Process.myUid()` | `process.pid` / `process.tid` / `process.uid`（`callStaticIntMethod` 与 `callStaticIntMethodV` 一致；无配置时 myPid/myTid=`emulator.getPid()`、myUid=0） |
| ARM32 `getpgrp` / ARM32+ARM64 `getpgid(0\|pid)` / `getsid(0\|pid)` | `process.pgid` / `process.sid`（仅 0 或当前 pid；缺键回落当前 pid，不从 ppid 或对方字段推导；sidecar kind=`process_identity`；**不**实现 `setpgid`/`setsid`） |
| `/proc/self\|pid/stat` 字段 5/6 | `process.pgid` → pgrp、`process.sid` → session（52 字段；缺键用当前 pid） |
| `Context.getDataDir` / `getFilesDir` / `getCacheDir` / `getNoBackupFilesDir` / `getCodeCacheDir` / `getDir(name,mode)` | 根级 `android.dataDir` 显式非 null 时 → `new File(dataDir)`、固定子目录或 `app_<name>`（name 单段；mode 忽略；VarArg+VaList；sidecar kind=`android_data_dir`；**不**建目录/**不**加载 Dex/**无**权限；缺键/null/非法 name UOE） |
| `Build.MODEL` | `android.build.MODEL` |
| `Build.VERSION.SDK_INT` | `android.build.VERSION.SDK_INT` |
| `Build.TIME` | `android.build.TIME`（精确 long；`getStaticLongField` 仅 `TIME:J`；sidecar kind=`android_build`，api=`Build.TIME`；不经 String 路径） |
| `Build.getSerial()` | `android.build.SERIAL`（静态方法；VarArg+VaList；sidecar kind=`android_build`，api=`Build.getSerial`） |
| `System.getProperty(String)` / `getProperty(String,String)` | `android.runtime.systemProperties`（仅已配置键；无宿主回落；VarArg+VaList；sidecar kind=`android_runtime`，api=`System.getProperty`，`key=…,result=…`） |
| `System.getProperties()` | `android.runtime.systemProperties`（节点已配置含 `{}`；**新**快照 JSON 顺序；**仅**非 null String；JSON null **省略**；sidecar `count=<n>` **不**写键/值；**不**持久化 put；无 setProperty/宿主回落；VarArg+VaList） |
| `System.getenv(String)` | `android.runtime.environmentVariables`（仅已配置键；独立于 systemProperties/`linux.environ`；无宿主回落；VarArg+VaList；sidecar kind=`android_runtime`，api=`System.getenv`，`key=…,result=…`） |
| `System.getenv()`（Map） | `android.runtime.environmentVariables`（节点已配置含 `{}`；**新**快照 JSON 顺序；**仅**非 null String；JSON null **省略**；sidecar `count=<n>` **不**写键/值；**不**持久化 put；无 setenv/宿主回落；VarArg+VaList） |
| `Runtime.getRuntime()` | `android.runtime.availableProcessors` **或** `maxMemoryBytes` **或** `totalMemoryBytes` 任一显式配置 → 同 VM `ConfiguredRuntime` marker；三者都缺失 UOE；无 sidecar；无宿主 Runtime；VarArg+VaList |
| `Runtime.availableProcessors()` | `android.runtime.availableProcessors`（精确 int `1..4096`；仅同 VM 存活 marker 且本字段已配置；**不**从 `linux.cpu` 推导；sidecar `field=availableProcessors,result=<n>`；VarArg+VaList） |
| `Runtime.maxMemory()` | `android.runtime.maxMemoryBytes`（精确 long `1..Long.MAX_VALUE`；仅同 VM 存活 marker 且本字段已配置；sidecar `field=maxMemoryBytes,result=<n>`；无宿主内存；**不**实现 gc/真实堆；VarArg+VaList） |
| `Runtime.totalMemory()` | `android.runtime.totalMemoryBytes`（精确 long `1..Long.MAX_VALUE`；仅同 VM 存活 marker 且本字段已配置；sidecar `field=totalMemoryBytes,result=<n>`；无宿主内存；与 `maxMemoryBytes` 同时显式时拒绝 `total > max`；**不**实现 gc/真实堆；VarArg+VaList） |
| `Runtime.freeMemory()` | `android.runtime.freeMemoryBytes`（精确 long `0..Long.MAX_VALUE`；不能单独出现，解析期要求 `totalMemoryBytes` 且拒绝 `free > total`；仅同 VM 存活 marker 且本字段已配置；sidecar `field=freeMemoryBytes,result=<n>`；无宿主内存；**不**实现 gc/真实堆/内存分配；VarArg+VaList） |
| `Build.SUPPORTED_ABIS` | `android.build.SUPPORTED_ABIS`（非空 String 数组；静态字段；sidecar kind=`android_build`） |
| `Build.SUPPORTED_32_BIT_ABIS` | `android.build.SUPPORTED_32_BIT_ABIS`（独立非空 String 数组；静态字段；sidecar kind=`android_build`） |
| `Build.SUPPORTED_64_BIT_ABIS` | `android.build.SUPPORTED_64_BIT_ABIS`（独立非空 String 数组；静态字段；sidecar kind=`android_build`） |
| `__system_property_get("ro.hardware")` | `android.properties.ro.hardware` |
| `UUID.randomUUID()` | `random.uuid` |
| `getrandom(...)` | `random.getrandomHex` |
| `open("/proc/cpuinfo")` | `linux.files."/proc/cpuinfo"` |
| `open("/proc/self/status")` 等 cmdline/cgroup/stat/statm/io/limits | `linux.proc`（可选 limits 行数组、SigBlk/SigIgn/SigCgt + Cap*；`linux.files` 优先；self 与配置 pid）。**另：** `linux.proc.limits` 缺键且 `linux.rlimits.nofile` 显式时派生一行 `Max open files`（`linux.proc` 可缺失；`[]` 覆盖为空；不从文本反解析 nofile） |
| ARM64 `getrlimit64(RLIMIT_NOFILE)` | `linux.rlimits.nofile`（`{"soft","hard"}` 非负 long 且 `soft<=hard`；仅键存在时写入 rlimit64 并返回 0；sidecar kind=`linux_proc`，api=`getrlimit64`，value 仅 `resource/soft/hard`；未配置保持 UOE；**不**改 `RLIMIT_STACK`；**不含** setrlimit/prlimit64/ARM32/其它资源）。**另：** 同键在 `linux.proc.limits` 缺键时单向派生 `/proc/self\|pid/limits` 一行（见上） |
| `open("/proc/sys/kernel/random/boot_id")` | `linux.proc.bootId`（canonical UUID 小写；`linux.files` 优先；与 `random.uuid`/`randomUuid` 独立；键缺失回落旧 open） |
| `open("/proc/sys/kernel/random/uuid")` | `linux.proc.randomUuid`（固定标记 canonical UUID 小写；`linux.files` 优先；与 `random.uuid`/`bootId` 独立；不按读生成；键缺失回落旧 open） |
| `open("/proc/sys/kernel/random/entropy_avail")` | `linux.proc.entropyAvail`（精确 int `0..2147483647`；十进制 ASCII+LF；`linux.files` 优先；与 `randomPoolSize`/`writeWakeupThreshold`/`urandomMinReseedSecs`/`bootId`/`randomUuid` 独立；键缺失回落旧 open；只读精确路径；不读宿主熵池） |
| `open("/proc/sys/kernel/random/poolsize")` | `linux.proc.randomPoolSize`（精确 int `0..2147483647`；十进制 ASCII+LF；`linux.files` 优先；与 `entropyAvail`/`writeWakeupThreshold`/`urandomMinReseedSecs`/`bootId`/`randomUuid` 独立；键缺失回落旧 open；只读精确路径；不读宿主熵池） |
| `open("/proc/sys/kernel/random/write_wakeup_threshold")` | `linux.proc.writeWakeupThreshold`（精确 int `0..2147483647`；十进制 ASCII+LF；`linux.files` 优先；与 `entropyAvail`/`randomPoolSize`/`urandomMinReseedSecs`/`bootId`/`randomUuid` 独立；键缺失回落旧 open；只读精确路径；不读宿主熵池；不实现写/阈值行为） |
| `open("/proc/sys/kernel/random/urandom_min_reseed_secs")` | `linux.proc.urandomMinReseedSecs`（精确 int `0..2147483647`；十进制 ASCII+LF；`linux.files` 优先；与 `entropyAvail`/`randomPoolSize`/`writeWakeupThreshold`/`bootId`/`randomUuid` 独立；键缺失回落旧 open；只读精确路径；不读宿主熵池；不实现写/阈值行为） |
| `open("/proc/self/oom_score_adj")` 等 | `linux.proc.oomScoreAdj`（精确 int -1000..1000；`linux.files` 优先；键缺失回落旧 open；只读） |
| `open("/proc/self/oom_score")` 等 | `linux.proc.oomScore`（精确 int 0..2000；`linux.files` 优先；与 `oomScoreAdj`/`oomAdj` 独立；键缺失回落旧 open；只读） |
| `open("/proc/self/oom_adj")` 等 | `linux.proc.oomAdj`（精确 int 仅 -17 或 -16..15；`linux.files` 优先；与 `oomScoreAdj`/`oomScore` 独立；键缺失回落旧 open；只读 self/配置 pid；不写） |
| `open("/proc/self/attr/current")` 等 | `linux.proc.selinuxContext`（可打印 ASCII 无空白/控制，1..256；`linux.files` 优先；键缺失回落旧 open；只读 current） |
| `open("/proc/self/comm")` 等 | `linux.proc.comm`（可打印 ASCII 无 CR/LF，1..15；另服务只读 `task/<tid>/comm`，`tid`=`process.tid` 或 emulator pid；`linux.files` 优先；不从 processName/threadName 推导；键缺失回落旧 open；只读 self/pid/配置 tid） |
| `open("/proc/self/wchan")` 等 | `linux.proc.wchan`（可打印 ASCII 无 CR/LF，1..255；固定分析标记；另服务只读 `task/<tid>/wchan`，`tid`=`process.tid` 或 emulator pid；`linux.files` 优先；键缺失回落旧 open；只读 self/pid/配置 tid） |
| `prctl(PR_GET_DUMPABLE)` | `linux.proc.dumpable`（精确 int 0..2；键缺失 ARM32 仍返回 0、ARM64 仍 UOE；sidecar api=`prctl(PR_GET_DUMPABLE)`；不改 SET） |
| `getpriority`（ARM32 NR=96 / ARM64 NR=141） | `linux.proc.nice`（精确 int -20..19；仅 `which==PRIO_PROCESS(0)` 且 `who==0` 或当前 pid；命中返回 **rawResult=20-nice**（40..1），sidecar `field=nice,nice=<n>,rawResult=<20-n>,which=…,who=…`；Bionic wrapper 为 `20-rawResult`；缺键历史 0 无事件；stat 字段 18/19 仅显式键时为 `20+nice`/`nice`，否则历史 20/0；**不含** `setpriority`/调度状态） |
| `prctl(PR_GET_SECCOMP)` | `linux.proc.seccompMode`（精确 int 0\|1\|2；option=21；键缺失 UOE 无事件；sidecar `field=seccompMode,result=<n>`；不改 SET/BPF） |
| `prctl(PR_GET_NO_NEW_PRIVS)` | `linux.proc.noNewPrivs`（严格 Boolean；option=39；true→1/false→0；键缺失 UOE 无事件；sidecar `field=noNewPrivs,result=0\|1`；不改 SET） |
| `capget`（V3 只读） | `capInheritableHex`/`capPermittedHex`/`capEffectiveHex` 至少其一；ARM32 NR=184 / ARM64 NR=90；pid 0 或配置 pid；写 2 槽 Inh/Prm/Eff；sidecar 无 mask 原文；**不**实现 capset/CapBnd/CapAmb |
| 构造期栈 auxv `AT_HWCAP`/`AT_HWCAP2`/`AT_PLATFORM`/`AT_EXECFN` | `linux.auxv`（节点缺失仅旧 `AT_RANDOM`+`AT_PAGESZ`） |
| 构造期栈 environ / `open("/proc/self/environ")` 等 | `linux.environ`（有效列表与 loader 一致；键缺失四条默认；`[]` 空；`linux.files` 优先；sidecar `format=environ`） |
| `stat`/`fstat` 对 `/proc/cpuinfo` 的 device/inode/size 等 | `filesystem.stat."/proc/cpuinfo"`（需路径可 resolve） |
| `statfs`/`statfs64`/`fstatfs`/`fstatfs64` 对已存在路径或已打开 fd 的 type/blocks 等 | `filesystem.statfs` 最长挂载匹配（路径型用 pathname；FD 型用 `io.getPath()` 虚拟路径，如 `/proc/self/fd`、`/`；需底层 `statfs` 成功。**不含** `statvfs` 结构转换） |
| `open("/proc/self/mounts")` / `open("/proc/self/mountinfo")` | `filesystem.mounts[]`（`linux.files` 优先；mountinfo 需四字段齐全） |
| `open("/etc/mtab")` | `filesystem.mounts[]`（配置挂载表的兼容只读视图，内容与 `/proc/mounts` 相同；键缺失不接管；显式 `[]` 零字节；只读精确路径；写/目录/相近路径不接管；`linux.files` 优先；sidecar `format=mtab`，value 仅 path/count/bytes） |
| `readlink("/proc/self/exe")` / `readlink("/proc/<pid>/exe")` | `filesystem.links."/proc/self/exe"`（pid→self 别名；不追加 NUL） |
| `Environment.getExternalStorageDirectory` / `getExternalStorageState` / `isExternalStorageEmulated` / `isExternalStorageRemovable`（无参与主目录 File 重载） / `getExternalStoragePublicDirectory(type)` / `Context.getExternalFilesDir(type)` / `Context.getExternalFilesDirs(type)` / `Context.getExternalCacheDir()` / `Context.getExternalCacheDirs()` / `Context.getObbDir()` / `Context.getObbDirs()` / `Context.getExternalMediaDirs()` | `filesystem.externalStorage`（File 仅匹配 `new File(directory)`；public/files type 须非空单段名；files/cache/obb/media 需包名，null type→`…/files`，filesDirs/cacheDirs/obbDirs/mediaDirs 一元同路径，cache→`…/cache`，obb→`…/obb/<pkg>`，media→`…/media/<pkg>`；null/非法/无包名/其它路径 UOE；**不**创建目录/OBB 内容/媒体扫描/多卷） |
| `Environment.getRootDirectory` / `getDataDirectory` / `getDownloadCacheDirectory` / `getStorageDirectory` | `filesystem.systemDirectories`（各字段显式配置才拦截；返回 `new File(path)`；**不**创建宿主目录/FileIO；sidecar kind=`filesystem_system_directories`） |
| `ApplicationInfo.sourceDir` | `android.apkPath` |
| `Settings.Secure.getString(..., "android_id")` | `android.identifiers.androidId`（优先；kind=`android_identifier`）或 `android.settings.secure.android_id`（settings map） |
| `Settings.System.getInt(..., "screen_brightness")` | `android.settings.system.screen_brightness` |
| `Settings.System.getLong(..., "screen_off_timeout")` 等 | `android.settings.<namespace>.<key>`（`Long.parseLong`；含带 long default 重载） |
| `Settings.System.getFloat(..., "font_scale")` | `android.settings.system.font_scale` |
| `Application`/`Context.getSystemService(TelephonyManager.class)` / `TelephonyManager.getImei()` / `getDeviceId(int)` | 类型化 lookup **不**要求节点、**不发** sidecar；读取仍由 `android.telephony.slots[].imei` / `deviceId` 门控 |
| `TelephonyManager.getNetworkOperatorName()` | `android.telephony.networkOperatorName` |
| `TelephonyManager.getNetworkCountryIso()` | `android.telephony.networkCountryIso`（空串或两位 ISO 字母，小写；无默认、不从 networkOperator 推导） |
| `TelephonyManager.getSimCountryIso()` | `android.telephony.simCountryIso`（空串或两位 ISO 字母，小写；无默认、不从 simOperator 推导） |
| `TelephonyManager.getPhoneCount()` / `getSimState(int)` | `android.telephony.phoneCount` / `slots[].simState` |
| `TelephonyManager.getDataNetworkType()` / `getNetworkType()` | `android.telephony.dataNetworkType`（`getNetworkType` 同键同值） |
| `TelephonyManager.getDataState()` | `android.telephony.dataState`（-1..5；无默认、不从 dataNetworkType 推导） |
| `TelephonyManager.getDataActivity()` | `android.telephony.dataActivity`（0..4；无默认、不从 dataState/dataNetworkType 推导） |
| `TelephonyManager.isNetworkRoaming()` | `android.telephony.networkRoaming` |
| `PackageManager.getPackageInfo` / `getInstalledPackages` | `android.packages[]` |
| `PackageManager.checkPermission` / `checkSelfPermission` | `android.packages[].permissions` |
| `PackageInfo.signatures` / `SigningInfo` / `hasSigningCertificate` | `signaturesHex` / `signingCertificateHistoryHex` |
| `PackageManager.getPackagesForUid` / `hasSigningCertificate(uid,…)` | `android.packages[].uid` |
| `PackageManager.hasSystemFeature` / `getSystemAvailableFeatures` | `android.features[]`（优先于 tee 回退） |
| `KeyFactory.getKeySpec(..., KeyInfo.class)` / `KeyInfo.getSecurityLevel` 等 | `android.tee` 节点 / `securityLevel` / `marker` |
| `hasSystemFeature(strongbox_keystore|hardware_keystore)` 回退 | `android.tee.strongBoxAvailable` / `available`+`keymasterVersion`（仅 features 缺失时） |
| `KeyStore.getKey` / `Key.getEncoded` / `Key.getAlgorithm` / `Key.getFormat` | `android.tee.keyBlobHex`（固定字节；sidecar 不写 hex 原文）+ 可选 `keyAlgorithm`/`keyFormat`（仅存活 marker 且显式字段） |
| `AMediaDrm_createByUUID` / 字符串与字节属性 / openSession | `android.drm`（分析 marker；未配置走遗留 Widevine/vendor） |
| `Locale.getDefault` / `getLanguage`/`getCountry`/`getScript`/`getVariant`/`toLanguageTag`/`toString` / `getISO3Language`/`getISO3Country` | `android.locale.languageTag`（配置 marker；ISO3 **仅** `ConfiguredLocale`，缺节点/普通 Locale **不接管**；未配置 VaList 仍宿主 Locale） |
| `Configuration.locale`（`getObjectField`） | **仅当** `android.configuration` 与 `android.locale` **同时**存在：返回 `android.locale` 的 `ConfiguredLocale` marker（**不**新增 JSON 键；缺任一节点 UOE，**不**回退宿主 Locale） |
| `TimeZone.getDefault` / `getID` / `getRawOffset` / `getOffset(long)` | `android.locale.timezoneId`（仅节点存在时；`getRawOffset` 为该 ID 的 raw offset 毫秒，不含当前时刻 DST；`getOffset(long)` 按该 ID 规则与调用参数 epochMillis 计算实际偏移，含 DST，不依赖当前系统时间） |
| `Resources.getDisplayMetrics` / `DisplayMetrics` 字段 | `android.display` 分辨率/密度 |
| `Application`/`Context.getSystemService(WindowManager.class)` / `getSystemService(DisplayManager.class)` / `WindowManager.getDefaultDisplay` / `DisplayManager.getDisplay` / `DisplayManager.getDisplays` / `Display.getRotation`/`getWidth`/`getHeight`/`getRefreshRate`/`getMode` | `android.display`（`rotation`/`widthPixels`/`heightPixels`/`refreshRate`/`modeId`；刷新率仅 `callFloatMethodV`；getWidth/getHeight 为旧版宽高；类型化 lookup **不**要求节点、**不发** sidecar；`getDisplay(0)` 复用同一 profile，非零返回 null；`getDisplays()` 仅枚举单个默认 profile；显示读取仍由节点门控） |
| `Display.Mode.getModeId`/`getPhysicalWidth`/`getPhysicalHeight`/`getRefreshRate` | `modeId` / `widthPixels` / `heightPixels` / `refreshRate` |
| `Resources.getConfiguration` / `Configuration.orientation`/`screenLayout`/`uiMode`/`fontScale`/`densityDpi`/`screenWidthDp`/`screenHeightDp`/`smallestScreenWidthDp`/`keyboard`/`navigation`/`keyboardHidden`/`hardKeyboardHidden`/`navigationHidden` / **`Configuration.locale`** / `Application`/`Context.getSystemService(UiModeManager.class)` / `UiModeManager.getCurrentModeType` | `android.configuration`（十三字段子集；配置 marker；screen*Dp/输入相关字段独立于 display 与彼此；`uiMode` **同时**驱动 `Configuration.uiMode` 与 `getCurrentModeType` 的 type 位 `uiMode & 0x0f`；**`Configuration.locale` 复用 `android.locale` 的 ConfiguredLocale，两节点均须存在，不回退宿主值**；类型化 lookup **不**要求节点、**不发** sidecar） |
| `GLES10`/`GLES20`/`GLES30.glGetString(I)` / `EGL14.eglQueryString(EGLDisplay,I)` | 顶层 `graphics` 八字段子集（仅静态查询；extensions 空格拼接；**不**实现 native libGLES/libEGL/Vulkan；egl 忽略 display；sidecar kind=`graphics`，value 仅 `name`+`field`+`resultLength`） |
| `BatteryManager.getIntProperty` / `getLongProperty`（`4`/`1`/`2`/`3`；`5` 仅 long；`6` 仅 int） / `computeChargeTimeRemaining` / `isCharging` | `android.battery` 八字段子集（固定 charge-time 标记；不实时估算；VarArg+VaList） |
| `Camera.getNumberOfCameras` / `getCameraInfo` / `CameraInfo.facing`/`orientation`/`canDisableShutterSound` | `android.cameras.count` + 可选 `infos[]`（facing/orientation 必填；可选 `canDisableShutterSound` 严格 Boolean、键缺失未配置；静态 int/void + getIntField + getBooleanField；VarArg+VaList；sidecar kind=`android_camera`） |
| `Context.SENSOR_SERVICE` / `getSystemService("sensor")` / `Application`/`Context.getSystemService(SensorManager.class)` / `SensorManager.getDefaultSensor(I)` / **`getDefaultSensor(IZ)`** / `getSensorList` / `getDynamicSensorList` / `isDynamicSensorDiscoverySupported` / `Sensor.getType` / **`Sensor.getName`** / **`Sensor.getVendor`** / **`Sensor.getVersion`** / **`Sensor.getStringType`** / **`Sensor.getMaximumRange`** / **`Sensor.getResolution`** / **`Sensor.getPower`** / **`Sensor.getMinDelay`** / **`Sensor.getMaxDelay`** / **`Sensor.getFifoReservedEventCount`** / **`Sensor.getFifoMaxEventCount`** / **`Sensor.isWakeUpSensor`** / **`Sensor.getId`** / **`Sensor.getReportingMode`** / **`Sensor.isDynamicSensor`** / **`Sensor.getRequiredPermission`** / **`Sensor.isAdditionalInfoSupported`** / **`Sensor.getHighestDirectReportRateLevel`** / **`Sensor.isDirectChannelTypeSupported`** | `android.sensors.types` / `dynamicTypes` / `dynamicDiscoverySupported` / 可选 **`names`** / 可选 **`vendors`** / 可选 **`versions`** / 可选 **`stringTypes`** / 可选 **`maximumRanges`** / 可选 **`resolutions`** / 可选 **`powers`** / 可选 **`minDelaysMicros`** / 可选 **`maxDelaysMicros`** / 可选 **`fifoReservedEventCounts`** / 可选 **`fifoMaxEventCounts`** / 可选 **`wakeUpSensors`** / 可选 **`sensorIds`** / 可选 **`reportingModes`** / 可选 **`dynamicSensors`** / 可选 **`requiredPermissions`** / 可选 **`additionalInfoSupported`** / 可选 **`highestDirectReportRateLevels`** / 可选 **`directChannelTypesSupported`**（仅 SystemService sensor 标记 + VM 拥有 ConfiguredSensor，**绑定创建时的配置实例**；`getType`/`getName`/`getVendor`/`getVersion`/`getStringType`/`getMaximumRange`/`getResolution`/`getPower`/`getMinDelay`/`getMaxDelay`/`getFifoReservedEventCount`/`getFifoMaxEventCount` 仅存活标记，配置替换后即使同 type/同名/同厂商值/同版本号/不同 string type 值/不同量程值/不同分辨率值/不同功耗值/不同最小延迟值/不同最大延迟值/不同 FIFO 预留事件数/不同 FIFO 最大事件数也 UOE；`getName` 另须该 type 显式名称；`getVendor` 另须该 type 显式厂商；`getVersion` 另须该 type 显式版本；`getStringType` 另须该 type 显式 string type；`getMaximumRange` 另须该 type 显式量程；`getResolution` 另须该 type 显式分辨率；`getPower` 另须该 type 显式功耗；`getMinDelay` 另须该 type 显式最小延迟；`getMaxDelay` 另须该 type 显式最大延迟；`getFifoReservedEventCount` 另须该 type 显式 FIFO 预留事件数；`getFifoMaxEventCount` 另须该 type 显式 FIFO 最大事件数；布尔独立、不从 dynamicTypes 推断；names / vendors / versions / stringTypes / maximumRanges / resolutions / powers / minDelaysMicros / maxDelaysMicros / fifoReservedEventCounts / fifoMaxEventCounts 与 lists **不**互推，彼此也**不**互推；`resolutions` **不**从 `maximumRanges` 推导；`powers` **不**从其它传感器字段推导；`minDelaysMicros` **不**从其它传感器字段推导；`maxDelaysMicros` **不**从 `minDelaysMicros` 或其他传感器字段推导，也不校验 `max>=min`；`fifoReservedEventCounts` **不**从 `minDelaysMicros` / `maxDelaysMicros` / `fifoMaxEventCounts` 或其他传感器字段推导，也不校验与 `fifoMaxEventCounts` 的大小关系；`fifoMaxEventCounts` **不**从 `fifoReservedEventCounts` / `minDelaysMicros` / `maxDelaysMicros` 或其他传感器字段推导，也不校验与 `fifoReservedEventCounts` 的大小关系；VarArg+VaList（`getMaximumRange` / `getResolution` / `getPower` 仅 `callFloatMethodV`）；sidecar kind=`android_sensor`，getName 仅 `sensorType`+`nameLength`，getVendor 仅 `sensorType`+`vendorLength`，getVersion 仅 `sensorType`+`version`，getStringType 仅 `sensorType`+`stringTypeLength`，getMaximumRange 仅 `sensorType`+`maximumRange`，getResolution 仅 `sensorType`+`resolution`，getPower 仅 `sensorType`+`power`，getMinDelay 仅 `sensorType`+`minDelayMicros`，getMaxDelay 仅 `sensorType`+`maxDelayMicros`，getFifoReservedEventCount 仅 `sensorType`+`fifoReservedEventCount`，getFifoMaxEventCount 仅 `sensorType`+`fifoMaxEventCount`；类型化 lookup **不**要求节点、**不发** sidecar） |
| `Context.AUDIO_SERVICE` / `getSystemService("audio")` / `Application`/`Context.getSystemService(AudioManager.class)` / `AudioManager.isMusicActive` / `isSpeakerphoneOn` / `getRingerMode` / `getMode` / `getProperty` / **`getStreamVolume`/`getStreamMaxVolume`/`getStreamMinVolume`** | `android.audio` 四标量 + `properties` + **`streamVolumes[]`**（独立；仅 SystemService audio；精确 streamType；可选 **`minVolume`** 显式才接管 min；VarArg+VaList；sidecar kind=`android_audio`；流音量 value=`streamType=…,result=…`；类型化 lookup **不**要求节点、**不发** sidecar） |
| `Context.CLIPBOARD_SERVICE` / `getSystemService("clipboard")` / `Application`/`Context.getSystemService(ClipboardManager.class)` / `ClipboardManager.hasPrimaryClip` / `getPrimaryClip` / `ClipData.getItemCount`/`getItemAt(0)` / `ClipData$Item.getText` / `ClipData$Item.coerceToText(Context)` / `ClipData.getDescription` / `ClipDescription.getMimeTypeCount`/`getMimeType(0)`/`hasMimeType`/`getLabel`/`getTimestamp`/`isStyledText` | `android.clipboard.hasPrimaryClip` + 可选 `primaryText` + 可选 `primaryLabel` + 可选 `timestampMillis`（仅 SystemService clipboard + 存活 marker；VarArg+VaList；类型化 lookup **不**要求节点、**不发** sidecar；真正的剪贴板读取仍由节点门控；getPrimaryClip sidecar 只记 textLength；`getText`/`coerceToText` 与描述元数据及 `getLabel`/`getTimestamp`/`isStyledText` 无 sidecar；`coerceToText` **不**读 Context；`primaryLabel` 省略 → Java null；`timestampMillis` 省略 → 原语 `0`；`isStyledText` 纯文本 marker 上 false） |
| `AccountManager.get` / `getAccounts` / `getAccountsByType` / `Account.name` / `Account.type` | `android.accounts[]`（`name`+`type`；同 VM marker 隔离；每次新数组；sidecar kind=`android_account` 仅列表 API） |
| `getSystemService("input_method")` / `InputMethodManager.getInputMethodList` / `getEnabledInputMethodList` / `InputMethodInfo.getId` | `android.inputMethods[]`（`id`+`enabled`；仅 SystemService input_method；同 VM marker；sidecar kind=`android_input_method` 仅列表 API） |
| `Context.ACCESSIBILITY_SERVICE` / `getSystemService("accessibility")` / `Application`/`Context.getSystemService(AccessibilityManager.class)` / `AccessibilityManager.isEnabled` / `isTouchExplorationEnabled` / `isHighContrastTextEnabled` / 服务列表 / `AccessibilityServiceInfo.getId` | `android.accessibility` 三布尔 + 可选 `services[]`（`[]` 合法空列表；已配置时 installed JSON 顺序全量、enabled 滤 true；`services` 键缺失时 enabled 仍固定空、installed UOE；`getId` 仅存活同 VM marker；列表 sidecar 仅 count；仅 SystemService accessibility；VarArg+VaList；类型化 lookup **不**要求节点、**不发** sidecar；读取仍由节点门控） |
| `Context.USER_SERVICE` / `Application`/`Context.getSystemService("user")` / `Application`/`Context.getSystemService(UserManager.class)` / `UserManager.isUserUnlocked` / `isSystemUser` / `isManagedProfile` / `isDemoUser` / `getSerialNumberForUser` | `android.userState`（`USER_SERVICE` / 字符串 lookup 与 Application 相同，直接返回 marker，**不**读环境、**不发** sidecar；未知名维持既有 `SystemService` 行为；**仅** `UserManager` 类型化 lookup **不**要求节点、**不发** sidecar；真正 UserManager 读取仍由节点门控；VarArg+VaList） |
| `getSystemService("location")` / `LocationManager.isLocationEnabled` / `isProviderEnabled` / **`hasProvider`** / `getAllProviders` / `getProviders(Z)` / **`getProvider`** / **`LocationProvider.getName`** / **`LocationProvider.requiresNetwork`** / **`LocationProvider.requiresSatellite`** / **`LocationProvider.requiresCell`** / **`LocationProvider.hasMonetaryCost`** / **`LocationProvider.supportsAltitude`** / **`LocationProvider.supportsSpeed`** / **`LocationProvider.supportsBearing`** / **`LocationProvider.meetsCriteria`** / **`LocationProvider.getAccuracy`** / **`LocationProvider.getPowerRequirement`** / **`getLastKnownLocation`** / **`Location` getter 子集** | `android.location.enabled` + `providers.gps\|network\|passive` + 可选 **`providerCapabilities`**（八个严格 Boolean 字段含 **`meetsCriteria`** 固定布尔标记，**不**读取 `Criteria` 字段 + **`accuracy`** 仅 `1`/`2`＝`LocationProvider.getAccuracy()I`，**不是** `Location.getAccuracy()F` + **`powerRequirement`** 仅 `1`/`2`/`3`＝`LocationProvider.getPowerRequirement()I`（`POWER_USAGE_LOW`/`MEDIUM`/`HIGH`）；仅存活同 VM `ConfiguredLocationProvider` 且对应字段显式配置才返回；`meetsCriteria` 另须精确 `android/location/Criteria` 参数；无 sidecar）+ **`lastKnownLocations[]`**（含可选 speed/bearing/vertical·speed·bearing accuracy；仅 SystemService location；显式 provider 键含 disabled → `hasProvider` true / 同 VM `ConfiguredLocationProvider`，省略/未知 → `hasProvider` false / Java null；精确 last-known provider；同 VM `ConfiguredLocation`；double 仅 VarArg；float getters 仅 VaList；sidecar：`getLastKnownLocation` 不含坐标、`hasProvider`/`getProvider`/`getName` 含 provider/result） |
| ARM32 `sysinfo`（NR 116）/ ARM64 `sysinfo`（NR 179） | `linux.sysinfo`（节点缺失保持全 0；公共 JSON 按 ARM32 C 宽度：`uptime` `0..2147483647`，loads/RAM/swap/`memUnit` `0..4294967295`（`memUnit`≥1）；越界加载失败；ARM32 运行时越界 `-1`/`EINVAL`、**不**截断；`SysInfo32`/`SysInfo64`；sidecar kind=`linux_sys`，`api=sysinfo`；**不**读宿主、**不**从 `/proc/meminfo` 推导） |
| `libc.so!getifaddrs` / `freeifaddrs` | `network.interfaces`（节点存在含 `[]` 时接管；数据仅本数组 IPv4/MAC/flags；ARM32/ARM64 Bionic `ifaddrs`；`[]` → `*ifap=NULL` 返回 `0`；节点缺失不拦截；sidecar kind=`network_device`，`api=getifaddrs`，value 仅 `result`/`interfaces`/`entries`，**不**写 IP/MAC；`freeifaddrs` 无 sidecar；**不**读宿主机 `NetworkInterface`；**不含** IPv6/netmask/`ifa_data`/netlink） |
| `ioctl(SIOCGIFCONF)` / `SIOCGIFADDR` | `network.interfaces` |
| `ioctl(SIOCGIFHWADDR)` | `network.interfaces[].mac` **与** `hardwareType`（两者皆须显式有效；缺一/未知接口/名称不匹配**不接管**；Linux `ifreq.ifr_hwaddr`：`IFNAMSIZ=16` 名称 + `sa_family`=`hardwareType` + 6 字节 MAC + 其余 `sockaddr_data` 清零；sidecar kind=`network_device`，`api=ioctl(SIOCGIFHWADDR)`，`name`+`format=ifreq-hwaddr`+`bytes`，**不**写 MAC/`hardwareType`/IPv4/flags；**不**从 name/flags/operState/carrier 推导；**不**读宿主机 `NetworkInterface`；ARM32/ARM64 真实系统调用在已配置 `SocketIO` 上**保留** `ENODEV`/`EOPNOTSUPP`，**不**覆盖为 `ENOTTY`） |
| `ioctl(SIOCGIFMTU)` | `network.interfaces[].mtu` |
| `open("/sys/class/net/<name>/address")` | `network.interfaces[].mac`（接口存在且 mac 已配；UTF-8 小写+LF；只读；`linux.files` 优先；sidecar kind=`network_device`，`api=read(path)`，`name`+`format=address`+`bytes`，**不**写 MAC 原文；未知接口/缺 mac/其它 sys **不接管**；**不**读宿主机 `NetworkInterface`） |
| `open("/sys/class/net/<name>/mtu")` | `network.interfaces[].mtu`（接口存在且 mtu 已配；UTF-8 十进制+单个 LF；只读；`linux.files` 优先；sidecar kind=`network_device`，`api=read(path)`，`name`+`format=mtu`+`bytes`，**不**写 MAC、**不**带入其它接口配置；未知接口/缺 mtu/其它 sys **不接管**；**不**读宿主机 `NetworkInterface`） |
| `open("/sys/class/net/<name>/ifindex")` | `network.interfaces[].index`（接口存在即服务必填 `index`；UTF-8 十进制+单个 LF；只读；`linux.files` 优先；sidecar kind=`network_device`，`api=read(path)`，`name`+`format=ifindex`+`bytes`，**不**写 MAC、IPv4、flags 或其它接口配置；未知接口/其它 sys（含 address/mtu/`iflink`）**不接管**；与可选 `linkIndex` / sysfs `iflink` **独立配置，不自动相等**；**不**读宿主机 `NetworkInterface`） |
| `open("/sys/class/net/<name>/flags")` | `network.interfaces[].flags`（接口存在且 flags 已配；UTF-8 小写 `0x` 前缀十六进制+单个 LF，无固定补零，如 `4163`→`0x1043\n`；只读；`linux.files` 优先；sidecar kind=`network_device`，`api=read(path)`，`name`+`format=flags`+`bytes`，**不**写 flags 数值、MAC、IPv4 或其它接口配置；未知接口/缺 flags/其它 sys **不接管**；**不**读宿主机 `NetworkInterface`） |
| `open("/sys/class/net/<name>/operstate")` | `network.interfaces[].operState`（接口存在且 `operState` 已显式配置；UTF-8 精确小写枚举+单个 LF，如 `up`→`up\n`；**只读**打开才接管；`linux.files` 优先；sidecar kind=`network_device`，`api=read(path)`，`name`+`format=operstate`+`bytes`，**不**写枚举原文、MAC、IPv4、flags 或其它接口配置；未知接口/缺字段/写或目录/其它 sys **不接管**；**不**从 flags/wifi 推导；**不**读宿主机 `NetworkInterface`；native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`） |
| `open("/sys/class/net/<name>/carrier")` | `network.interfaces[].carrier`（接口存在且 `carrier` 已显式配置；UTF-8 `1\n`（true）或 `0\n`（false）；**只读**打开才接管；`linux.files` 优先；sidecar kind=`network_device`，`api=read(path)`，`name`+`format=carrier`+`bytes`，**不**写 carrier 值、operState、MAC、IPv4、flags、MTU 或其它接口配置；未知接口/缺字段/写或目录/其它 sys **不接管**；**不**从 operState/flags/wifi 推导；**不**读宿主机 `NetworkInterface`；native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`） |
| `open("/sys/class/net/<name>/type")` | `network.interfaces[].hardwareType`（接口存在且 `hardwareType` 已显式配置；UTF-8 十进制+单个 LF，如 `1`→`1\n`、`772`→`772\n`；**只读**打开才接管；`linux.files` 优先；sidecar kind=`network_device`，`api=read(path)`，`name`+`format=type`+`bytes`，**不**写 hardwareType 数值、MAC、IPv4 或其它接口配置；未知接口/缺字段/写或目录/其它 sys **不接管**；**不**从 name/flags/mac/operState/carrier 推导；**不**读宿主机 `NetworkInterface`；native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`） |
| `open("/sys/class/net/<name>/speed")` | `network.interfaces[].speedMbps`（接口存在且 `speedMbps` 已显式配置；十进制 ASCII+单个 LF，如 `866`→`866\n`、`-1`→`-1\n`、`0`→`0\n`；`-1`＝未知；**只读**打开才接管；`linux.files` 优先；sidecar kind=`network_device`，`api=read(path)`，`name`+`format=speed`+`bytes`，**不**写 `speedMbps` 数值、MAC、IPv4、flags、`hardwareType`、`operState` 或 `carrier`；未知接口/缺字段/写或目录/其它 sys **不接管**；**不**从 `network.wifi.linkSpeedMbps`/name/flags/mac/hardwareType/operState/carrier/duplex 推导；与 Java `WifiInfo.getLinkSpeed()` 相互独立；**不**读宿主机 `NetworkInterface`；native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`） |
| `open("/sys/class/net/<name>/duplex")` | `network.interfaces[].duplex`（接口存在且 `duplex` 已显式配置；UTF-8 精确小写枚举+单个 LF，如 `full`→`full\n`、`unknown`→`unknown\n`、`half`→`half\n`；**只读**打开才接管；`linux.files` 优先；sidecar kind=`network_device`，`api=read(path)`，`name`+`format=duplex`+`bytes`，**不**写 duplex 原文、`speedMbps`、MAC、IPv4、flags、`hardwareType`、`operState` 或 `carrier`；未知接口/缺字段/写或目录/其它 sys **不接管**；**不**从 `speedMbps`/`carrier`/`operState`/hardwareType/flags/name/`network.wifi` 推导，与三者均独立；**不**读宿主机 `NetworkInterface`；native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`） |
| `open("/sys/class/net/<name>/iflink")` | `network.interfaces[].linkIndex`（接口存在且 `linkIndex` 已显式配置；十进制 ASCII+单个 LF，如 `1`→`1\n`、`2`→`2\n`；**只读**打开才接管；`linux.files` 优先；sidecar kind=`network_device`，`api=read(path)`，`name`+`format=iflink`+`bytes`，**不**写 `linkIndex` 数值、`index`、MAC、IPv4、flags、`hardwareType`、`speedMbps`、`duplex`、`operState` 或 `carrier`；未知接口/缺字段/写或目录/其它 sys（含 ifindex/speed/duplex/address/mtu/type/operstate/carrier）**不接管**；**不**从 `index`/name/hardwareType/speedMbps/duplex/flags/carrier/operState/MAC/`network.wifi` 推导；与必填 `index` / sysfs `ifindex` **独立配置，不自动相等**；**不**读宿主机 `NetworkInterface`；native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`） |
| `open("/sys/class/net/<name>/tx_queue_len")` | `network.interfaces[].txQueueLen`（接口存在且 `txQueueLen` 已显式配置；十进制 ASCII+单个 LF，如 `1000`→`1000\n`、`0`→`0\n`；**只读**打开才接管；`linux.files` 优先；sidecar kind=`network_device`，`api=read(path)`，`name`+`format=tx_queue_len`+`bytes`，**不**写 `txQueueLen` 数值、IP、MAC、flags、`hardwareType`、`speedMbps`、`duplex`、`linkIndex`、`operState` 或 `carrier`；未知接口/缺字段/写或目录/其它 sys（含 mtu/speed/duplex/iflink/ifindex/address/type/operstate/carrier）**不接管**；**不**从 `mtu`/`speedMbps`/duplex/hardwareType/linkIndex/index/flags/carrier/operState/name/`network.wifi` 推导；与 `mtu`/`speedMbps` **独立、不自动推导**；**不**读宿主机 `NetworkInterface`；native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`） |
| `open("/sys/class/net/<name>/addr_assign_type")` | `network.interfaces[].addressAssignType`（接口存在且 `addressAssignType` 已显式配置；十进制 ASCII+单个 LF，如 `0`→`0\n`、`1`→`1\n`；范围 `0..3`＝permanent/random/stolen/set；**只读**打开才接管；`linux.files` 优先；sidecar kind=`network_device`，`api=read(path)`，`name`+`format=addr_assign_type`+`bytes`，**不**写 `addressAssignType` 数值、IP、MAC、flags、`hardwareType`、`speedMbps`、`duplex`、`linkIndex`、`txQueueLen`、`operState` 或 `carrier`；未知接口/缺字段/写或目录/其它 sys（含 address/mtu/type/speed/duplex/iflink/ifindex/tx_queue_len/operstate/carrier）**不接管**；**不**从 `mac`/接口名/`hardwareType`/`flags`/`carrier`/`operState`/`speedMbps`/`duplex`/`linkIndex`/`txQueueLen`/`network.wifi` 推导，**不**根据 MAC 生成方式判断；与 `mac` **独立**；**不**读宿主机 `NetworkInterface`；native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`） |
| `open("/sys/class/net/<name>/name_assign_type")` | `network.interfaces[].nameAssignType`（接口存在且 `nameAssignType` 已显式配置；十进制 ASCII+单个 LF，如 `0`→`0\n`、`4`→`4\n`；范围 `0..4`＝unknown/enum/predictable/user/renamed；**只读**打开才接管；`linux.files` 优先；sidecar kind=`network_device`，`api=read(path)`，`name`+`format=name_assign_type`+`bytes`，**不**写 `nameAssignType` 数值、IP、MAC、flags、`hardwareType`、`speedMbps`、`duplex`、`linkIndex`、`txQueueLen`、`addressAssignType`、`operState` 或 `carrier`；未知接口/缺字段/写或目录/其它 sys（含 address/mtu/type/speed/duplex/iflink/ifindex/tx_queue_len/operstate/carrier/addr_assign_type）**不接管**；**不**从接口名/`mac`/`addressAssignType`/`hardwareType`/`flags`/`carrier`/`operState`/`speedMbps`/`duplex`/`linkIndex`/`txQueueLen`/`network.wifi` 推导；**不**读宿主机 `NetworkInterface`；native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`） |
| `open("/sys/class/net/<name>/broadcast")` | `network.interfaces[].linkLayerBroadcast`（接口存在且 `linkLayerBroadcast` 已显式配置；UTF-8 规范小写 MAC+单个 LF，如 `FF:FF:FF:FF:FF:FF`→`ff:ff:ff:ff:ff:ff\n`；规则与 `mac` 相同；**只读**打开才接管；`linux.files` 优先；sidecar kind=`network_device`，`api=read(实际路径)`，value **仅** `name`+`format=broadcast`+`bytes`，**不**写链路层广播原文、IPv4 `broadcast`、MAC、`hardwareType`、`flags` 或其它接口值；未知接口/缺字段/写或目录/近似路径/其它 sys **不接管**；**不**从 `mac`/IPv4 `broadcast`/`hardwareType`/`flags` 推导，与 IPv4 `broadcast` **完全独立**；**不**实现写入；**不**读宿主机 `NetworkInterface`；native 自动路径**目前仅** `address`、`mtu`、`ifindex`、`flags`、`operstate`、`carrier`、`type`、`speed`、`duplex`、`iflink`、`tx_queue_len`、`addr_assign_type`、`name_assign_type` 与 `broadcast`） |
| `open("/proc/net/route")` / `open("/proc/self/net/route")` / `open("/proc/<emulatorPid>/net/route")` | `network.ipv4Routes[]`（键缺失不接管；显式 `[]` 仅固定表头；条目严格白名单全必填；`interfaceName` 须为已配置 `network.interfaces` 名；Destination/Gateway/Mask 小端 `%08X`；`linux.files` 优先；只读；sidecar kind=`network_device`，`format=proc-net-route`，value 仅 path/format/routeCount/bytes，**不**写 destination/gateway/mask/flags/接口名；**不**从 interfaces/wifi 推导；**不**读宿主机路由；**不含**写/目录/`/proc/net/arp`/`/proc/net/dev`/netlink/路由 ioctl/IPv6 路由） |
| `open("/proc/net/dev")` / `open("/proc/self/net/dev")` / `open("/proc/<emulatorPid>/net/dev")` | `network.interfaceStats[]`（键缺失不接管；显式 `[]` 仅固定两行表头；条目严格白名单全必填；`interfaceName` 须为已配置 `network.interfaces` 名且本数组内唯一；十六个计数为精确 JSON 整数 `0..Long.MAX_VALUE`；按 JSON 顺序输出 `接口名:` + 八个 RX + 八个 TX 十进制；`linux.files` 优先；只读；sidecar kind=`network_device`，`format=proc-net-dev`，value 仅 path/format/interfaceCount/bytes，**不**写接口名或任何计数/IP/MAC/flags/路由值；**不**从 interfaces/ipv4Routes/wifi 推导；**不**读宿主机网络；**不含**写/目录/netlink/路由 ioctl/IPv6；`/proc/net/arp` 见 `network.arpEntries`；`/proc/net/route` 见 `network.ipv4Routes`；单个 sysfs statistics 见下行） |
| `open("/sys/class/net/<name>/statistics/<field>")` | `network.interfaceStats[]`（仅当同接口存在 stats 条目；复用现有十六字段：`rx_bytes`←`rxBytes`、`rx_packets`←`rxPackets`、`rx_errors`←`rxErrors`、`rx_dropped`←`rxDrop`、`rx_fifo_errors`←`rxFifo`、`rx_frame_errors`←`rxFrame`、`rx_compressed`←`rxCompressed`、`multicast`←`rxMulticast`、`tx_bytes`←`txBytes`、`tx_packets`←`txPackets`、`tx_errors`←`txErrors`、`tx_dropped`←`txDrop`、`tx_fifo_errors`←`txFifo`、`tx_carrier_errors`←`txCarrier`、`tx_compressed`←`txCompressed`、`collisions`←`txCollisions`；十进制 ASCII+单个 LF；键缺失/显式 `[]`/未知接口/本数组无该接口/未支持内核字段（`rx_crc_errors` 等）/写/目录/相近路径**不接管**；`linux.files` 优先；sidecar kind=`network_device`，`api=read(实际路径)`，value 仅 path/name/`format=statistics-<field>`/bytes，**不**写计数值/IP/MAC/flags；**不**从 interfaces/routes/wifi 推导；**不**读宿主机 `NetworkInterface` 或真实 sysfs） |
| `open("/proc/net/if_inet6")` / `open("/proc/self/net/if_inet6")` / `open("/proc/<emulatorPid>/net/if_inet6")` | `network.ipv6Addresses[]`（键缺失不接管；显式 `[]` 零字节、无表头；条目严格白名单全必填；`interfaceName` 须为已配置 `network.interfaces` 名；`addressHex` 恰好 32 位 ASCII hex 并规范小写；`prefixLength` `0..128`；`scope`/`flags` `0..255`；接口索引仅复用同名 `interfaces[].index` 的两位小写 hex；**仅**被本数组条目引用的接口 `index` 须 `0..255`（普通未引用 `interfaces[].index` 范围不因此收紧）；按 JSON 顺序输出六列单空格+LF；`linux.files` 优先；只读；sidecar kind=`network_device`，`format=proc-net-if-inet6`，value 仅 path/format/addressCount/bytes，**不**写地址/接口名/前缀/scope/flags/IP/MAC/路由值；**不**从其它字段推导地址；**不**读宿主机网络；**不含**写/目录/`/proc/net/ipv6_route`/`/proc/net/route`/`/proc/net/dev`/IPv6 socket/netlink/`getifaddrs` 的 IPv6 条目/Java IPv6 枚举） |
| `open("/proc/net/arp")` / `open("/proc/self/net/arp")` / `open("/proc/<emulatorPid>/net/arp")` | `network.arpEntries[]`（键缺失不接管；显式 `[]` 仅固定表头；条目严格白名单全必填；`interfaceName` 须为已配置 `network.interfaces` 名；本数组内 `interfaceName`+`ipv4` 组合唯一；IPv4 为严格点分十进制；`hardwareType`/`flags` 精确 JSON 整数 `0..4294967295`；`mac` 与 `network.interfaces[].mac` 相同规则并规范小写冒号形式；内核 `arp.c` 表头 + `%-16s 0x%-10x0x%-10x%-17s     *        %s`；`linux.files` 优先；只读；sidecar kind=`network_device`，`format=proc-net-arp`，value 仅 path/format/entryCount/bytes，**不**写接口名/IP/MAC/flags/`hardwareType`；**不**从 interfaces/wifi 推导；**不**读宿主机 ARP；**不含**写/目录/ARP ioctl/真实探测） |
| `open("/proc/net/igmp")` / `open("/proc/self/net/igmp")` / `open("/proc/<emulatorPid>/net/igmp")` | `network.igmpMemberships[]`（键缺失不接管；显式 `[]` 仅固定表头；条目严格白名单全必填；`interfaceName` 须为已配置 `network.interfaces` 名；本数组内 `interfaceName`+`groupIpv4` 组合唯一；`groupIpv4` 为严格点分 IPv4 且在 `224.0.0.0/4`；`querierVersion` 仅 `V1`/`V2`/`V3` 且同一接口必须相同；`users` 精确 JSON 整数 `0..2147483647`；`timerRunning`/`reporter` 为 JSON boolean；`timerClock` 精确 JSON 整数 `0..4294967295`，`timerRunning=false` 时必须为 `0`；内核 `igmp.c` 表头 + 每接口首次 `%d\t%-10s: %5d %7s`（`index` 复用同名 `interfaces[].index`，`Count` 仅统计本数组）+ 组成员 `\t\t\t\t%08X %5d %d:%08X\t\t%d`（小端 `__be32`，如 `224.0.0.251`→`FB0000E0`）；`linux.files` 优先；只读；sidecar kind=`network_device`，`format=proc-net-igmp`，value 仅 path/format/membershipCount/interfaceCount/bytes，**不**写接口名/`index`/组地址/querier/users/timer/reporter；**不**从 interfaces/wifi/routes 推导；**不**读宿主机 IGMP；**不含**写/目录/IGMP socket/组加入离开/ioctl/真实探测；`/proc/net/igmp6` 见 `network.igmp6Memberships`） |
| `open("/proc/net/igmp6")` / `open("/proc/self/net/igmp6")` / `open("/proc/<emulatorPid>/net/igmp6")` | `network.igmp6Memberships[]`（键缺失不接管；显式 `[]` 零字节、无表头；条目严格白名单全必填；`interfaceName` 须为已配置 `network.interfaces` 名；本数组内 `interfaceName`+解析后 `groupIpv6` 组合唯一；`groupIpv6` 为严格 IPv6 文本且在 `ff00::/8`；`users` 精确 JSON 整数 `0..2147483647`；`flags` 精确 JSON 整数 `0..4294967295`；`timer` 精确 JSON 整数 `0..9223372036854775807`；拒浮点/数字字符串；内核 `mcast.c` `"%-4d %-15s %pi6 %5d %08X %ld\n"`（`index` 复用同名 `interfaces[].index`；IPv6 为 32 位大写 hex、无冒号、网络字节序；flags 为 8 位大写 hex；timer 为十进制）；按 JSON 顺序；`linux.files` 优先；只读；sidecar kind=`network_device`，`format=proc-net-igmp6`，value 仅 path/format/membershipCount/interfaceCount/bytes，**不**写接口名/`index`/IPv6 地址/flags/timer；**不**从 interfaces/wifi/routes/igmpMemberships 推导；**不**读宿主机 MLD；**不含**写/目录/MLD socket/真实多播加入离开/ioctl/真实探测） |
| `open("/proc/net/dev_mcast")` / `open("/proc/self/net/dev_mcast")` / `open("/proc/<emulatorPid>/net/dev_mcast")` | `network.linkLayerMulticastEntries[]`（键缺失不接管；显式 `[]` 零字节、无表头；条目严格白名单全必填；`interfaceName` 须为已配置 `network.interfaces` 名；本数组内 `interfaceName`+规范化 `mac` 组合唯一；`mac` 与 `network.interfaces[].mac` 相同规则并规范小写冒号形式；`referenceCount` 精确 JSON 整数 `1..2147483647`；`globalUse` 为严格 JSON Boolean；拒浮点/数字字符串/`null`/未知字段；内核 `net-procfs.c` `dev_mc_seq_show` `"%-4d %-15s %-5d %-5d %phN\n"`（`index` 复用同名 `interfaces[].index`；`%phN` 为 12 个连续小写 hex、无冒号；无表头；按 JSON 顺序）；`linux.files` 优先；只读；sidecar kind=`network_device`，`format=proc-net-dev-mcast`，value 仅 path/format/entryCount/interfaceCount/bytes，**不**写接口名/`index`/MAC/引用数/`globalUse`；**不**从 interfaces.mac/linkLayerBroadcast/wifi 推导；**不**读宿主机；**不含**写/目录/真实多播加入/socket/ioctl/netlink） |
| `open("/proc/net/wireless")` / `open("/proc/self/net/wireless")` / `open("/proc/<emulatorPid>/net/wireless")` | `network.wirelessProcStats`（可选对象，仅允许且必须有 `wirelessExtensionsVersion` 与 `entries`；键缺失不接管；显式空 `entries` 仅两行表头；`wirelessExtensionsVersion` 精确 JSON 整数 `0..999`；每条严格白名单全必填；`interfaceName` 须为已配置 `network.interfaces` 名且 `entries` 内唯一；`status` `0..65535`；`linkQuality` `0..255`；`level`/`noise` 精确 JSON 整数 `-256..255`（最终 `/proc` 有符号数，**不**做 iw_statistics/DBM 转换）；三个 updated 为严格 JSON Boolean（`.` 或空格）；六个 discard/missed 精确 JSON 整数 `0..4294967295`；拒浮点/数字字符串/布尔当数字/`null`/未知字段；内核 `wext-proc.c` 表头 + `"%6s: %04x  %3d%c  %3d%c  %3d%c  %6d %6d %6d %6d %6d   %6d\n"`；按 JSON 顺序；`linux.files` 优先；只读；sidecar kind=`network_device`，`format=proc-net-wireless`，value 仅 path/format/interfaceCount/bytes，**不**写接口名/无线版本/统计值；**不**从 interfaces/wifi/routes 推导；**不**读宿主机；**不含**写/目录/无线 ioctl/netlink/扫描/真实无线能力） |
| `NetworkInterface.getNetworkInterfaces` / `getByName` / `getByIndex` / `getByInetAddress` / `getName` / `getDisplayName` / `getIndex` / `getHardwareAddress` / `getInetAddresses` / `getMTU` / `isUp` / `isLoopback` / `isPointToPoint` / `isVirtual` / `supportsMulticast` / 接口 `InetAddress.getHostAddress` | `network.interfaces`（Java 子集；`isVirtual` 仅显式 `virtual`；flags 位含 `IFF_POINTOPOINT=0x10`；VarArg+VaList；sidecar kind=`network_interface`；节点缺失 UOE） |
| `WifiManager.isWifiEnabled()` / `getWifiState()` / `getConnectionInfo()` / `Application`/`Context.getSystemService("wifi")` / `Application`/`Context.getSystemService(WifiManager.class)` | `network.wifi.enabled` / `state`（0..4，独立）/ 任一 wifi 字段；字符串与类型化 lookup **不**要求节点、**不读**环境、**不发** sidecar |
| `WifiInfo.getSSID()` / `getIpAddress()` / `getRssi()` | `network.wifi.ssid` / `ipv4` / `rssi` |
| `ConnectivityManager.getActiveNetworkInfo()` / `getActiveNetwork()` / `getLinkProperties` / `Application`/`Context.getSystemService(ConnectivityManager.class)` | `network.links` 节点存在（类型化 lookup **不**要求节点、**不发** sidecar） |
| `NetworkInfo.isConnected()` / `getType()` / `getTypeName()` | `network.links.connected` / `type` / `typeName` |
| `LinkProperties.getInterfaceName()` / `getMtu()` / `getDnsServers()` | `interfaceName` / `mtu` / `dnsServers` |
| `LinkProperties.isPrivateDnsActive()` / `getPrivateDnsServerName()` / `getDomains()` | `privateDnsActive` / `privateDnsServerName` / `domains` |
| `LinkProperties.getHttpProxy()` / `ProxyInfo.getHost()` / `getPort()` | `proxyHost` / `proxyHost` / `proxyPort` |
| `WifiManager.getDhcpInfo()` / `DhcpInfo.ipAddress` / `gateway` / `dns1` / `netmask` | `wifi.ipv4` 等 DHCP 源（含 `netmaskIpv4`） / `wifi.ipv4` / `gatewayIpv4` / `dnsServers[0]` / `netmaskIpv4` |

## 通用环境槽（模板，无个案内容）

动态 `/proc/self/maps` 不是 JSON 模板：它按**真实已映射**区间生成。Android ARM64 使用固定 39-bit 用户态（无 ASLR）：heap `0x7010000000`、mmap/`.so` `0x7100000000`、stack 顶 `0x7fe0000000`、SVC `0x7fffe00000`、LR `0x7ffff00000`。格式与内核 `%08lx` 相同（`7100000000-…`）。默认 **Unicorn1** 可映射并关闭该布局。Windows 上 Unicorn2 的 `uc_close` 在 39-bit 映射后会崩溃，因此 Android64 的 Unicorn2 `destroy` 不调用 `nativeDestroy`。Dynarmic / KVM / Hypervisor 仍是 36-bit 页表，无法映射。32 位仍为 `0x8048000` / `0x12000000` / `0xe5000000`。`linux.files` 或画像 overlay 的精确 `/proc/self/maps` 仍优先于动态生成。

下列节点均为**可填充模板**：缺键不接管，显式 `[]`/`{}` 是权威空快照。引擎不内置端口、厂商或采集器逻辑。

| 节点 | 作用 |
| --- | --- |
| `android.sensors` + NDK `ASensorManager_*` | 配置存在时自动注册 `libandroid.so`；`getSensorList`/`getDefaultSensor`/`ASensor_getName|Vendor|Type|Resolution` 读同一份 `types/names/vendors/resolutions` |
| `android.sensors.samples` | type→float[]；`registerListener` 投递 `onSensorChanged`，`SensorEvent.values` 为配置浮点数组 |
| `network.ipv6Addresses` + `getifaddrs` | 同名接口追加 `AF_INET6` 条目 |
| `android.drm` + Java `MediaDrm.getPropertyByteArray("deviceUniqueId")` | 与 `AMediaDrm` 共用 `resolveMediaDrmDeviceUniqueId()` |
| `network.tcp` / `network.tcp6` | 渲染 `/proc/net/tcp`、`/proc/net/tcp6` 及 self/pid 别名（内核表头；`[]` 仅表头） |
| `network.capabilities` | `transportTypes` / `networkCapabilities`；`hasTransport`/`hasCapability`；可反射 `mTransportTypes`/`mNetworkCapabilities` long bitset |
| `linux.processes` | `/proc` 目录枚举 + `/proc/<pid>/cmdline|comm|exe` |
| `linux.commands` | 精确命令键 → `popen`/`system` stdout；不特化任何二进制名 |
| `linux.mincore.resident` | 配置后 `mincore` 按页写 0/1 |
| `filesystem.directories` 与画像目录 | `getdents` 枚举子名 |
| `android.locale.languageTags` | `LocaleList.getDefault` / `Configuration.getLocales` |
| `android.battery.plugged` | sticky `registerReceiver(null, …)` + `Intent.getIntExtra("plugged")`；取值仅 0/1/2/4 |
| `android.displays[]` | `getDisplays`/`getDisplay(id)`；缺键时仍可用单屏 `android.display` |
| `android.packages[].applicationFlags` | 显式 `ApplicationInfo.flags`；缺省仍可由 `systemApp` 投影 `FLAG_SYSTEM` |
| `android.location.lastKnownLocations` | `requestLocationUpdates(... )V` 投递 `onLocationChanged`（匹配 provider 的最后位置） |
| `android.accessibility.services` | 仅当 `Settings.Secure.enabled_accessibility_services` 省略时单向派生 |

快捷画像：`android.sensors.samples` 已转正。旧形状 `network.capabilities`（仅 `internet`/`vpn` 等布尔、无 `transportTypes`）仍当 reserved 剥离。

### 模板字段（填 JSON，引擎不内置个案内容）

`network.tcp[]` / `network.tcp6[]`：键缺失不接管；显式 `[]` 仅内核表头。每条白名单：`slot`（数组内唯一）+ `localPort`/`remotePort`（0..65535）+ `stateHex`（两位十六进制，如 listen=`0A`）+ 可选 `txQueue`/`rxQueue`/`uid`/`timeout`/`inode`。IPv4 用 `localIpv4`/`remoteIpv4`（点分）；IPv6 用 `localIpv6`/`remoteIpv6`（恰好 32 位 hex）。地址**不**从 `interfaces`/routes 推导。路径：`/proc/net/tcp`、`/proc/self/net/tcp`、`/proc/<pid>/net/tcp`（`tcp6` 同理）。`linux.files` 优先。

`linux.processes[]`：键缺失不接管；显式 `[]` 使 `/proc` 仅含 `self`。每条：`pid` + 可选 `cmdline[]`（NUL 拼接）/ `comm` / `exe`。**不**从 `process.pid` 推导。

`linux.commands`：对象，键为 **精确** 命令字符串（如 `"uptime"`），值为 stdout 文本。`popen`/`system` 精确匹配才接管；引擎**不**按二进制名特化。缺键不接管。

`linux.mincore`：仅允许 `resident` 布尔。键存在时 ARM32 NR 219 / ARM64 NR 232 按页写 `0`/`1`。

`filesystem.directories`：路径 → 子名数组。`open` 后 `getdents` 枚举这些名字；不递归、不推断文件类型。

`network.capabilities`：仅 `transportTypes[]` / `networkCapabilities[]`（精确非负 int）。`hasTransport`/`hasCapability` 与可反射 `mTransportTypes`/`mNetworkCapabilities` bitset。**不**从 wifi/links 推导。

`android.locale.languageTags[]`：BCP 47 标签列表 → `LocaleList.getDefault` / `Configuration.getLocales` / `size`。

`android.battery.plugged`：仅 `0`/`1`/`2`/`4`。键存在时粘性 `registerReceiver(null)` 返回 Intent，`getIntExtra("plugged")` 读该值；`status`/`level` 若已配置一并可读。

`android.displays[]`：`id`/`name`/`flags`/`widthPixels`/`heightPixels`/`densityDpi`。键存在时 `getDisplays` 不再硬编码单屏。

`android.packages[].applicationFlags`：显式 `ApplicationInfo.flags`；缺省仍可由 `systemApp` 投影 `FLAG_SYSTEM`。

`android.sensors.samples`：type 十进制键 → float 数组。键存在时 `SensorManager.registerListener` 按 Sensor type 投递 `SensorEventListener.onSensorChanged`；`SensorEvent.values` 为该数组副本。

`android.drm` + Java `MediaDrm.getPropertyByteArray("deviceUniqueId")` 与 native `AMediaDrm` 共用 `resolveMediaDrmDeviceUniqueId()`（`deviceUniqueIdHex` → `random.mediaDrmDeviceUniqueIdHex` → `marker` UTF-8）。

## 编译验证

PowerShell：

```powershell
cd D:\project\TraceAIagent_v3\unidbg-fasttrace
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd -pl unidbg-api,unidbg-android -DskipTests compile
.\mvnw.cmd -pl unidbg-api "-Dmaven.test.skip=false" -Dtest=TraceEnvironmentConfigTest test
```

# Trace 环境探针 SO

这个目录是一个独立的 Android NDK 小工程，用来编译 `libtrace_env_probe.so`，验证 `example/trace-env.example.json` 中的环境配置是否在 unidbg native 执行路径中生效。

## 编译

默认使用 `D:\AndroidSDK\ndk\27.0.12077973`：

```powershell
powershell -ExecutionPolicy Bypass -File test/build.ps1
```

也可以显式指定 NDK：

```powershell
powershell -ExecutionPolicy Bypass -File test/build.ps1 -NdkHome D:\AndroidSDK\ndk\24.0.8215888
```

编译成功后会生成：

- `test/libs/armeabi-v7a/libtrace_env_probe.so`
- `test/libs/arm64-v8a/libtrace_env_probe.so`

`test/obj/` 是 NDK 中间目录，已通过 `.gitignore` 忽略。

## 运行 unidbg 测试

在子模块根目录执行：

```powershell
.\mvnw.cmd -pl unidbg-android "-Dmaven.test.skip=false" -Dtest=TraceEnvironmentProbeTest test
```

如果本地 Maven 仓库里的 `unidbg-api` 不是当前源码版本，使用 reactor 命令一起编译依赖模块：

```powershell
.\mvnw.cmd -pl unidbg-android -am "-Dmaven.test.skip=false" -DfailIfNoTests=false -Dtest=TraceEnvironmentProbeTest test
```

测试会用 `AndroidEmulatorBuilder.setEnvironmentConfig(new File("example/trace-env.example.json"))` 加载 JSON，然后分别加载 32 位和 64 位的 `libtrace_env_probe.so`。

## 输出字段

`trace_env_probe(char *out, size_t out_size)` 会把探测结果写成 `key=value` 文本，每行一个字段。`/proc/*` 这类文本文件会把换行、制表符和反斜杠转义成 `\n`、`\t`、`\\`，避免破坏行格式。

- `process.pid`、`process.ppid`、`process.tid`、`process.uid`、`process.euid`、`process.gid`、`process.egid`：验证 JSON 中 `process` 段是否影响 native 侧进程和用户组 syscall。
- `time.gettimeofday.*`：验证 `time.currentTimeMillis` 是否影响 `gettimeofday`。
- `time.clock_realtime.*`：验证 `time.currentTimeMillis` 是否影响 `clock_gettime(CLOCK_REALTIME)`。
- `time.clock_monotonic.*`：验证 `time.monotonicNanos` 是否影响 `clock_gettime(CLOCK_MONOTONIC)`。
- `random.getrandom`：验证 `random.getrandomHex` 是否影响 `getrandom` syscall。
- `random.random`、`random.urandom`、`random.srandom`：验证 `/dev/random`、`/dev/urandom`、`/dev/srandom` 是否读取 JSON 中的固定随机字节。
- `uname.*`：验证 `linux.uname` 是否影响 `uname` syscall，32 位应返回 `machine32`，64 位应返回 `machine64`。
- `file.proc_cpuinfo`、`file.proc_meminfo`、`file.proc_version`：验证 `linux.files` 是否替换对应 `/proc/*` 文件内容。
- `property.ro.hardware`、`property.ro.build.version.sdk`、`property.ro.product.model`：验证 `android.properties` 是否影响 `__system_property_get`。
- `property.find.ro.build.version.sdk`、`property.callback.*`：验证 `__system_property_find` 和 `__system_property_read_callback` 路径是否能读取 JSON-backed property。

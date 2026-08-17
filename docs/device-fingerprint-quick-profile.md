# 设备指纹快捷画像文件设计

## 目标

本方案面向算法分析，不追求完整复刻真实 Android 设备，也不要求传感器、TEE、DRM、GPU 等硬件能力真正运行。

目标是：

- 修改一个 JSON 画像文件后，不重新编译 unidbg，下一次运行即可让 APP 读取到新值。
- `/proc`、`/sys` 等长文本或二进制内容可以放在独立文件中，避免 JSON 转义。
- 已经由 `TraceEnvironmentConfig` 实现的能力继续复用，不删除、不改名。
- 暂未实现的常见设备指纹先保留稳定字段，后端接入后无需再次修改画像格式。
- 所有命中继续写入环境 sidecar，便于从目标 so 的调用位置回查主 trace。

本文定义的是新增的快捷画像格式。当前完整配置格式仍以 `example/trace-env.example.json` 和 `docs/android-environment-config.md` 为准。

## 推荐目录

每个设备画像使用一个独立目录：

```text
profiles/
└── pixel6-analysis/
    ├── device-fingerprint.json
    └── files/
        ├── proc/
        │   ├── cpuinfo
        │   ├── meminfo
        │   ├── version
        │   ├── self/
        │   │   ├── cmdline
        │   │   ├── status
        │   │   └── cgroup
        │   └── sys/kernel/random/
        │       └── boot_id
        └── sys/
            ├── devices/system/cpu/
            │   ├── online
            │   ├── present
            │   └── possible
            └── class/net/wlan0/
                ├── address
                ├── mtu
                ├── operstate
                └── carrier
```

`device-fingerprint.json` 用于短字段、数值、布尔值和结构化列表。`files` 目录保存目标程序按文件读取的原始内容。

权威样例是 `profiles/pixel6-analysis/`。第 1 批已把签名算法高频、稳定、且现有后端已支持的输入集中写进该目录；完整字段仍以本文示例和 `docs/android-environment-config.md` 为准。

## 加载方式

快捷画像加载器**已落地**。`schemaVersion` 必须精确等于 `traceai-device-fingerprint/v1`，否则整份画像失败；若写了 `profileName`，必须是非空字符串。省略 `fileOverlayRoot` 时默认为画像目录下的相对子目录 `files`。覆盖层只在从**文件**加载时挂接（`load(File)` / `setEnvironmentProfile` / `-Dunidbg.env.profile`）；`DeviceFingerprintProfile.parse(json, null)` 只做字段转换，**不**挂 `fileOverlayRoot`。

支持：

```java
AndroidEmulatorBuilder.for64Bit()
        .setEnvironmentProfile(new File("profiles/pixel6-analysis/device-fingerprint.json"))
        .build();
```

以及 JVM 参数：

```text
-Dunidbg.env.profile=profiles/pixel6-analysis/device-fingerprint.json
```

现有完整配置继续使用：

```java
AndroidEmulatorBuilder.for64Bit()
        .setEnvironmentConfig(new File("example/trace-env.example.json"))
        .build();
```

或：

```text
-Dunidbg.env.config=example/trace-env.example.json
```

## 完整快捷画像示例

下面的 JSON 与 `profiles/pixel6-analysis/device-fingerprint.json` 相同，作为当前权威样例。`backendStatus` 只用于说明和校验，不应返回给 APP。

```json
{
  "schemaVersion": "traceai-device-fingerprint/v1",
  "profileName": "pixel6-analysis",
  "fileOverlayRoot": "files",
  "process": {
    "processName": "com.demo.app",
    "pid": 12345,
    "pgid": 12345,
    "sid": 12345,
    "ppid": 1,
    "tid": 12345,
    "uid": 10123,
    "gid": 10123,
    "euid": 10123,
    "egid": 10123,
    "supplementaryGids": [10123],
    "threadName": "main"
  },
  "time": {
    "currentTimeMillis": 1718000000000,
    "monotonicNanos": 123456789000,
    "timezoneMinutesWest": -480
  },
  "random": {
    "devRandomHex": "000000f0",
    "devUrandomHex": "010000f1",
    "devSrandomHex": "020000f2",
    "getrandomHex": "00112233445566778899aabbccddeeff",
    "uuid": "00000000-0000-0000-0000-000000000001",
    "stackGuardHex": "0102030405060708",
    "atRandomHex": "00112233445566778899aabbccddeeff"
  },
  "linux": {
    "uname": {
      "sysname": "Linux",
      "nodename": "localhost",
      "release": "5.10.66-TRACEAI",
      "version": "#1 SMP PREEMPT TRACEAI",
      "machine32": "armv7l",
      "machine64": "aarch64",
      "domainname": "(none)"
    },
    "cpu": {
      "affinityMaskHex": "ff",
      "online": "0-7",
      "present": "0-7",
      "possible": "0-7",
      "configuredProcessorCount": 8,
      "onlineProcessorCount": 8
    },
    "auxv": {
      "hwcap32": 1,
      "hwcap2_32": 2,
      "hwcap64": 3219913727,
      "hwcap2_64": 2,
      "platform32": "v7l",
      "platform64": "aarch64",
      "execFn": "/system/bin/app_process64"
    },
    "environ": [
      "ANDROID_DATA=/data",
      "ANDROID_ROOT=/system",
      "PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin",
      "TMPDIR=/data/local/tmp"
    ],
    "rlimits": {
      "nofile": {
        "soft": 32768,
        "hard": 32768
      }
    },
    "proc": {
      "cmdline": ["com.demo.app"],
      "cgroups": ["0::/"],
      "comm": "main",
      "tracerPid": 0,
      "bootId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "randomUuid": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
      "selinuxContext": "u:r:untrusted_app:s0:c512,c768",
      "fileSelinuxContexts": {
        "/data/user/0/com.demo.app": "u:object_r:app_data_file:s0"
      }
    }
  },
  "android": {
    "packageName": "com.demo.app",
    "versionName": "1.0.0",
    "versionCode": 100,
    "apkPath": "/data/app/~~fixed/com.demo.app-fixed/base.apk",
    "dataDir": "/data/user/0/com.demo.app",
    "nativeLibraryDir32": "/data/app/~~fixed/com.demo.app-fixed/lib/arm",
    "nativeLibraryDir64": "/data/app/~~fixed/com.demo.app-fixed/lib/arm64",
    "build": {
      "BRAND": "google",
      "MANUFACTURER": "Google",
      "MODEL": "TRACEAI_PIXEL_6",
      "DEVICE": "raven",
      "PRODUCT": "raven",
      "BOARD": "raven",
      "HARDWARE": "raven",
      "BOOTLOADER": "unknown",
      "DISPLAY": "TRACEAI.PIXEL6.001",
      "ID": "TRACEAI.PIXEL6.001",
      "HOST": "traceai-host",
      "USER": "android-build",
      "TAGS": "release-keys",
      "TYPE": "user",
      "TIME": 1640995200000,
      "FINGERPRINT": "google/raven/raven:12/TRACEAI.PIXEL6.001/1:user/release-keys",
      "CPU_ABI": "arm64-v8a",
      "CPU_ABI2": "armeabi-v7a",
      "SERIAL": "TRACEAI_SERIAL_V1",
      "SUPPORTED_ABIS": ["arm64-v8a", "armeabi-v7a"],
      "SUPPORTED_32_BIT_ABIS": ["armeabi-v7a"],
      "SUPPORTED_64_BIT_ABIS": ["arm64-v8a"],
      "VERSION.RELEASE": "12",
      "VERSION.SDK_INT": 31,
      "VERSION.INCREMENTAL": "1",
      "VERSION.CODENAME": "REL",
      "VERSION.SECURITY_PATCH": "2022-01-05"
    },
    "properties": {
      "ro.product.brand": "google",
      "ro.product.manufacturer": "Google",
      "ro.product.model": "TRACEAI_PIXEL_6",
      "ro.product.device": "raven",
      "ro.product.name": "raven",
      "ro.product.board": "raven",
      "ro.product.cpu.abi": "arm64-v8a",
      "ro.hardware": "raven",
      "ro.serialno": "TRACEAI_SERIAL_V1",
      "ro.boot.serialno": "TRACEAI_SERIAL_V1",
      "ro.bootloader": "unknown",
      "ro.build.id": "TRACEAI.PIXEL6.001",
      "ro.build.display.id": "TRACEAI.PIXEL6.001",
      "ro.build.version.incremental": "1",
      "ro.build.version.sdk": "31",
      "ro.build.version.release": "12",
      "ro.build.version.codename": "REL",
      "ro.build.version.security_patch": "2022-01-05",
      "ro.build.type": "user",
      "ro.build.tags": "release-keys",
      "ro.build.user": "android-build",
      "ro.build.host": "traceai-host",
      "ro.build.fingerprint": "google/raven/raven:12/TRACEAI.PIXEL6.001/1:user/release-keys",
      "ro.debuggable": "0",
      "ro.secure": "1",
      "persist.sys.timezone": "Asia/Shanghai",
      "wifi.interface": "wlan0"
    },
    "settings": {
      "secure": {
        "android_id": "a1b2c3d4e5f60718"
      },
      "global": {
        "adb_enabled": "0",
        "development_settings_enabled": "0"
      }
    },
    "identifiers": {
      "androidId": "a1b2c3d4e5f60718",
      "advertisingId": "00000000-0000-4000-8000-00000000a001",
      "limitAdTracking": false,
      "appSetId": "traceai-app-set-id-000000000001",
      "appSetScope": 1
    },
    "runtime": {
      "systemProperties": {
        "os.name": "Linux",
        "os.arch": "aarch64",
        "java.vm.name": "Dalvik"
      },
      "environmentVariables": {
        "ANDROID_DATA": "/data",
        "ANDROID_ROOT": "/system",
        "PATH": "/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin",
        "TMPDIR": "/data/local/tmp"
      },
      "availableProcessors": 8,
      "maxMemoryBytes": 268435456,
      "totalMemoryBytes": 134217728,
      "freeMemoryBytes": 67108864
    },
    "packages": [
      {
        "packageName": "com.demo.app",
        "versionName": "1.0.0",
        "versionCode": 100,
        "sourceDir": "/data/app/~~fixed/com.demo.app-fixed/base.apk",
        "dataDir": "/data/user/0/com.demo.app",
        "uid": 10123,
        "enabled": true,
        "systemApp": false,
        "permissions": {
          "android.permission.INTERNET": true,
          "android.permission.ACCESS_NETWORK_STATE": true
        },
        "signaturesHex": ["5452414345414901"]
      }
    ],
    "features": [
      { "name": "android.hardware.camera" },
      { "name": "android.hardware.wifi" },
      { "name": "android.hardware.touchscreen" },
      { "name": "android.hardware.telephony" }
    ],
    "locale": {
      "languageTag": "zh-Hans-CN",
      "timezoneId": "Asia/Shanghai"
    },
    "display": {
      "widthPixels": 1080,
      "heightPixels": 2400,
      "densityDpi": 420,
      "scaledDensity": 2.625,
      "xdpi": 411.0,
      "ydpi": 411.0,
      "refreshRate": 60.0,
      "rotation": 0,
      "modeId": 1
    },
    "configuration": {
      "orientation": 1,
      "screenLayout": 34,
      "uiMode": 17,
      "fontScale": 1.0,
      "densityDpi": 420,
      "screenWidthDp": 411,
      "screenHeightDp": 914,
      "smallestScreenWidthDp": 411,
      "keyboard": 1,
      "navigation": 1,
      "keyboardHidden": 1,
      "hardKeyboardHidden": 1,
      "navigationHidden": 1
    },
    "battery": {
      "capacityPercent": 73,
      "charging": false,
      "chargeCounterUah": 2500000,
      "currentNowUa": -450000,
      "currentAverageUa": -320000,
      "energyCounterNwh": 55000000000,
      "status": 3,
      "chargeTimeRemainingMillis": -1
    },
    "power": {
      "interactive": true,
      "powerSaveMode": false,
      "deviceIdleMode": false,
      "deviceLightIdleMode": false
    },
    "thermal": {
      "currentThermalStatus": 0,
      "headroom": 0.75
    },
    "sensors": {
      "types": [1, 4],
      "names": {
        "1": "TRACEAI_ACCELEROMETER",
        "4": "TRACEAI_GYROSCOPE"
      },
      "vendors": {
        "1": "TRACEAI_SENSOR_VENDOR",
        "4": "TRACEAI_SENSOR_VENDOR"
      },
      "versions": {
        "1": 1,
        "4": 1
      },
      "stringTypes": {
        "1": "android.sensor.accelerometer",
        "4": "android.sensor.gyroscope"
      },
      "maximumRanges": {
        "1": 19.6,
        "4": 34.9
      },
      "resolutions": {
        "1": 0.01,
        "4": 0.001
      },
      "powers": {
        "1": 0.13,
        "4": 0.15
      },
      "minDelaysMicros": {
        "1": 5000,
        "4": 5000
      },
      "wakeUpSensors": {
        "1": false,
        "4": false
      },
      "sensorIds": {
        "1": -1,
        "4": -1
      },
      "reportingModes": {
        "1": 0,
        "4": 0
      },
      "samples": {
        "1": [0.0, 0.0, 9.81],
        "4": [0.0, 0.0, 0.0]
      }
    },
    "telephony": {
      "phoneCount": 1,
      "networkOperator": "46000",
      "networkOperatorName": "TRACEAI_OPERATOR",
      "simOperator": "46000",
      "simOperatorName": "TRACEAI_SIM_OPERATOR",
      "networkCountryIso": "cn",
      "simCountryIso": "cn",
      "dataNetworkType": 13,
      "dataState": 2,
      "phoneType": 1,
      "networkRoaming": false,
      "slots": [
        {
          "slotIndex": 0,
          "imei": "TRACEAI_IMEI_SLOT0",
          "deviceId": "TRACEAI_DEVICE_ID_SLOT0",
          "subscriberId": "TRACEAI_IMSI_SLOT0",
          "simSerialNumber": "TRACEAI_ICCID_SLOT0",
          "simState": 5
        }
      ],
      "cellInfo": {
        "marker": "TRACEAI_CELL_INFO_V1"
      }
    },
    "cameras": {
      "count": 2,
      "infos": [
        {
          "facing": 1,
          "orientation": 270,
          "canDisableShutterSound": true
        },
        {
          "facing": 0,
          "orientation": 90,
          "canDisableShutterSound": false
        }
      ]
    },
    "location": {
      "enabled": true,
      "providers": {
        "gps": true,
        "network": false,
        "passive": false
      },
      "lastKnownLocations": [
        {
          "provider": "gps",
          "latitude": 31.2304,
          "longitude": 121.4737,
          "altitude": 12.5,
          "accuracyMeters": 8.0,
          "timeMillis": 1718000000000,
          "elapsedRealtimeNanos": 123456789000,
          "mock": false
        }
      ]
    },
    "securitySignals": {
      "debuggerConnected": false,
      "waitingForDebugger": false,
      "debuggerTracing": false,
      "selinuxEnabled": true,
      "selinuxEnforced": true,
      "userAMonkey": false,
      "userTestHarness": false
    },
    "tee": {
      "available": true,
      "securityLevel": 1,
      "strongBoxAvailable": false,
      "marker": "TRACEAI_TEE_MARKER_V1"
    },
    "drm": {
      "marker": "TRACEAI_DRM_MARKER_V1"
    }
  },
  "network": {
    "interfaces": [
      {
        "name": "lo",
        "index": 1,
        "ipv4": "127.0.0.1",
        "flags": 73,
        "mac": "00:00:00:00:00:00",
        "hardwareType": 772,
        "mtu": 65536,
        "operState": "unknown",
        "carrier": false
      },
      {
        "name": "wlan0",
        "index": 2,
        "ipv4": "192.168.50.23",
        "broadcast": "192.168.50.255",
        "flags": 4163,
        "mac": "02:54:52:41:43:45",
        "hardwareType": 1,
        "mtu": 1500,
        "displayName": "WLAN0",
        "operState": "up",
        "carrier": true,
        "speedMbps": 866,
        "duplex": "full"
      }
    ],
    "wifi": {
      "enabled": true,
      "state": 3,
      "ssid": "TRACEAI_WIFI_SSID",
      "bssid": "02:54:52:41:43:45",
      "macAddress": "02:54:52:41:43:46",
      "ipv4": "192.168.50.23",
      "rssi": -55,
      "linkSpeedMbps": 866,
      "frequencyMhz": 5180,
      "networkId": 42
    },
    "links": {
      "connected": true,
      "type": 1,
      "typeName": "WIFI",
      "interfaceName": "wlan0",
      "dnsServers": ["8.8.8.8", "1.1.1.1"],
      "gatewayIpv4": "192.168.50.1",
      "netmaskIpv4": "255.255.255.0",
      "mtu": 1500
    },
    "capabilities": {
      "validated": true,
      "internet": true,
      "vpn": false,
      "metered": false
    }
  },
  "filesystem": {
    "externalStorage": {
      "directory": "/storage/emulated/0",
      "state": "mounted",
      "emulated": true,
      "removable": false
    },
    "systemDirectories": {
      "rootDirectory": "/system",
      "dataDirectory": "/data",
      "downloadCacheDirectory": "/cache",
      "storageDirectory": "/storage"
    },
    "stat": {
      "/data/app/~~fixed/com.demo.app-fixed/base.apk": {
        "mode": 33188,
        "uid": 1000,
        "gid": 1000,
        "size": 4096000,
        "blockSize": 4096,
        "mtimeMillis": 1718000000000
      },
      "/data/user/0/com.demo.app": {
        "mode": 16877,
        "uid": 10123,
        "gid": 10123,
        "size": 4096,
        "blockSize": 4096,
        "mtimeMillis": 1718000000000
      },
      "/storage/emulated/0": {
        "mode": 16877,
        "uid": 1023,
        "gid": 1023,
        "size": 4096,
        "blockSize": 4096,
        "mtimeMillis": 1718000000000
      }
    },
    "mounts": [
      {
        "source": "/dev/block/dm-0",
        "target": "/system",
        "fileSystemType": "ext4",
        "options": "ro,seclabel,relatime"
      },
      {
        "source": "/dev/block/dm-1",
        "target": "/data",
        "fileSystemType": "f2fs",
        "options": "rw,nosuid,nodev,noatime,seclabel"
      },
      {
        "source": "/dev/fuse",
        "target": "/storage/emulated/0",
        "fileSystemType": "fuse",
        "options": "rw,nosuid,nodev,noexec,noatime"
      }
    ],
    "statfs": {
      "/data": {
        "blockSize": 4096,
        "blocks": 15000000,
        "blocksFree": 8000000,
        "blocksAvailable": 7500000,
        "files": 1000000,
        "filesFree": 900000
      },
      "/storage/emulated/0": {
        "blockSize": 4096,
        "blocks": 30000000,
        "blocksFree": 20000000,
        "blocksAvailable": 19000000,
        "files": 2000000,
        "filesFree": 1800000
      }
    }
  },
  "graphics": {
    "vendor": "TRACEAI_GPU_VENDOR",
    "renderer": "TRACEAI_GPU_RENDERER",
    "version": "OpenGL ES 3.2 TRACEAI",
    "shadingLanguageVersion": "OpenGL ES GLSL ES 3.20",
    "extensions": ["GL_TRACEAI_marker"],
    "eglVendor": "Android",
    "eglVersion": "1.5 TRACEAI",
    "eglExtensions": ["EGL_KHR_image_base"]
  },
  "backendStatus": {
    "android.telephony.cellInfo": "reserved",
    "graphics.native": "reserved"
  }
}
```

## 字段覆盖清单

| 类别 | 快捷画像字段 | APP 常见读取位置 | 当前后端状态 |
| --- | --- | --- | --- |
| 进程 | `process.*` | `getpid`、`getuid`、`Process.myPid`、`/proc/self/*` | 已实现，可直接映射 |
| 时间 | `time.*` | `gettimeofday`、`clock_gettime`、`System.currentTimeMillis`、`SystemClock` | 已实现，可直接映射 |
| 随机数 | `random.*` | `getrandom`、`/dev/urandom`、UUID、栈保护值 | 已实现，可直接映射 |
| 包信息 | `android.packageName`、版本和路径 | `Context`、`PackageManager`、`ApplicationInfo` | 已实现，可直接映射 |
| Build | `android.build.*` | `Build.*`、`Build.VERSION.*` | 已实现，可直接映射 |
| 系统属性 | `android.properties` | `__system_property_get/read/find/read_callback` | 已实现，可直接映射 |
| 电池 | `android.battery.*` | `BatteryManager` 容量、电流、状态、剩余充电时间、`plugged` | 已实现静态读取 + 粘性 `plugged` extras；状态迁移待实现 |
| 电源 | `android.power.*` | `PowerManager` 交互、节电和空闲状态 | 已实现静态读取；状态迁移待实现 |
| 温度 | `android.thermal.*` | 热状态、热余量 | 已实现静态读取；回调待实现 |
| 传感器名称和参数 | `android.sensors.names/vendors/...` | `SensorManager`、`Sensor.getName/getVendor/...` | 已实现静态画像 |
| 传感器采样 | `android.sensors.samples` | `registerListener`、`SensorEvent.values` | 已实现：type→float[]；`registerListener` 在键存在时接管 |
| 网卡 | `network.interfaces` | ioctl、`NetworkInterface`、`/sys/class/net/*` | 已实现主要静态字段 |
| Wi-Fi | `network.wifi` | `WifiManager`、`WifiInfo` | 已实现主要静态字段 |
| 网络链路 | `network.links` | `ConnectivityManager`、`LinkProperties`、DHCP | 已实现主要静态字段 |
| 网络能力 | `network.capabilities` | `NetworkCapabilities.hasTransport`/`hasCapability` | 新形状 `transportTypes`/`networkCapabilities` 已实现；旧布尔形状仍 reserved |
| 电话和 SIM | `android.telephony` | `TelephonyManager` | 基础标识和状态已实现 |
| 小区信息 | `android.telephony.cellInfo` | `getAllCellInfo`、`getCellLocation` | 字段保留，后端待实现 |
| 显示 | `android.display` / `android.displays` | `DisplayMetrics`、`getDisplays` | 单屏 + 可选多屏列表 |
| 区域和时区 | `android.locale` | `Locale`、`TimeZone`、`LocaleList` | 已实现 `languageTag` + 可选 `languageTags[]` |
| 摄像头 | `android.cameras` | 旧版 `Camera.getCameraInfo` | 基础静态信息已实现；Camera2 和打开设备待实现 |
| 位置 | `android.location` | `LocationManager`、最后已知位置、`requestLocationUpdates` | 静态提供者 + 最后位置；`requestLocationUpdates` 在 `lastKnownLocations` 存在时接管；真实 GNSS 待实现 |
| 存储 | `filesystem.*` | `Environment`、`stat`、`statfs`、挂载文件 | **`stat` / `statfs` / `mounts` 已实现**；样例含 APK/data/external 三路径 `stat` 与 `/system` `/data` `/storage/emulated/0` 三 `mounts`。`/proc/self/maps` **不固定**（不进覆盖层） |
| 安全信号 | `android.securitySignals` | Debug、SELinux、测试环境状态 | 已实现部分静态读取 |
| TEE | `android.tee` | `KeyInfo`、硬件密钥标记 | 分析型 marker 已实现；不做真实证明 |
| DRM | `android.drm` | MediaDrm/Media NDK 属性和会话 | Java/native `deviceUniqueId` 共用同一字节源；不做解密和许可交换 |
| 图形 | `graphics` | Java GLES/EGL 查询 | Java 子集部分实现；Native 图形库待实现 |
| `/proc` 和 `/sys` | `fileOverlayRoot` | `open/openat/read/stat/access/readlink` | **只读 `open/read` 覆盖层已落地**（优先于 `linux.files`；`/proc/self`↔配置 pid 别名）。guest 路径须为规范 POSIX 形式（不折叠 `//`、`.`、尾 `/`；禁 `..`/NUL/反斜杠/`:`/空白）。写/RDWR → `EACCES`，`O_DIRECTORY` → `ENOTDIR`，**不**回落宿主机。不跟随符号链接，不接管 `/proc/self\|pid/fd/*`。`stat`/`access`/`readlink` 仍走现有路径 |
| uname / CPU | `linux.uname`、`linux.cpu` | `uname`、`sysconf(_SC_NPROCESSORS_*)`、`/sys/devices/system/cpu/*` | 已实现，可直接映射；第 1 批样例已填写 |
| sysinfo | `linux.sysinfo` | ARM32/ARM64 `sysinfo` | **已实现**；样例含 uptime/loads/RAM/swap/procs/`memUnit=4096` |
| 进程环境 / auxv | `linux.environ`、`linux.auxv` | `getenv`、`AT_HWCAP` / `AT_PLATFORM` / `AT_EXECFN` | 已实现，可直接映射；第 1 批样例已填写 |
| 内核 boot_id | `linux.proc.bootId` + `files/proc/sys/kernel/random/boot_id` | `open/read` `/proc/sys/kernel/random/boot_id` | 覆盖层优先；结构化字段可生成同路径 |
| ANDROID_ID / 广告 ID | `android.identifiers`、`android.settings.secure.android_id` | `Settings.Secure.ANDROID_ID`、广告 ID API | 已实现静态读取；第 1 批样例二者同值 |
| 包签名 | `android.packages[].signaturesHex` | `PackageManager` 签名 | 已实现静态读取；第 1 批样例含当前包 |
| 特性 / Configuration | `android.features`、`android.configuration` | `hasSystemFeature`、`Resources.getConfiguration` | 已实现静态读取；第 1 批样例已填写 |
| Runtime | `android.runtime` | `Runtime.availableProcessors` / 内存 / `System.getProperty` / `getenv` | 已实现静态读取；第 1 批样例已填写 |
| 存储 stat | `filesystem.stat` | `stat`/`lstat`/`fstat` | **已实现**；样例三路径：APK、`dataDir`、external |
| 存储 mounts | `filesystem.mounts` | 挂载表 / mountinfo | **已实现**；样例三 target：`/system`、`/data`、`/storage/emulated/0` |
| 存储 statfs | `filesystem.statfs` | `statfs`、`StatFs` | 已实现主要静态字段；第 1 批样例含 `/data` 与外部存储 |
| `/proc/self/maps` | （无覆盖文件） | `open/read` maps | **不固定**；样例不提供 `files/proc/self/maps` |

## 独立文件覆盖规则

### 路径映射

快捷画像加载器已将模拟系统中的绝对路径映射到画像目录。第 1 批样例已提供：

```text
/proc/cpuinfo
/proc/meminfo
/proc/version
/proc/self/cmdline
/proc/self/status
/proc/self/cgroup
/proc/sys/kernel/random/boot_id
/sys/devices/system/cpu/online
/sys/devices/system/cpu/present
/sys/devices/system/cpu/possible
/sys/class/net/wlan0/address
/sys/class/net/wlan0/mtu
/sys/class/net/wlan0/operstate
/sys/class/net/wlan0/carrier
```

例如：

```text
/proc/cpuinfo
→ <profile>/files/proc/cpuinfo

/proc/self/status
→ <profile>/files/proc/self/status

/sys/class/net/wlan0/address
→ <profile>/files/sys/class/net/wlan0/address
```

文件内容按原始字节返回，不自动追加换行，不解析内容，也不从宿主机读取同名文件。

### PID 别名

当 `process.pid=12345` 时，下列路径可以复用同一文件：

```text
/proc/self/status
/proc/12345/status

/proc/self/cmdline
/proc/12345/cmdline
```

只为当前配置的 pid 建立别名，不模拟其它进程。

### 优先级

同一路径存在多个来源时按以下优先级：

1. `files` 独立文件覆盖。
2. 现有 `linux.files` 精确路径内容。
3. `TraceEnvironmentConfig` 根据结构化字段生成的内容。
4. unidbg 当前默认行为。

任何一级命中后不再读取宿主机文件。

### 安全限制

- 画像中的绝对路径由 `normalizeGuestPath` 解析：仅 POSIX `/` 分段；**不**把 `//`、`.`、尾 `/` 折叠成规范路径，这些一律拒绝。同时禁止 `..`、NUL、反斜杠、盘符/`:`、控制字符、空白分段。
- `fileOverlayRoot` 由 `splitOverlayRoot` 解析，必须是画像目录下的相对子目录。禁止 `.` / `..` / 绝对路径 / 尾 `/` / 空段 / 空白。覆盖根若为符号链接，或真实路径不严格位于画像目录内，读取时不接管。
- 已覆盖路径默认只允许读取。`LinuxFileSystem` 对写/`O_RDWR` 返回 `EACCES`。覆盖层**文件**上的 `O_DIRECTORY` 返回 `ENOTDIR`；覆盖层**目录**可 `getdents` 枚举。**不**回落宿主机，也**不**跟随符号链接。
- 覆盖层**不**跟随路径上的宿主符号链接；`/proc/self/fd`、`/proc/self/fd/*` 与 `/proc/<pid>/fd/*` 不接管，仍走显式 fd 配置。
- 二进制文件保持原始字节（含 `NUL`），不进行 UTF-8 转换。sidecar `profile-file` 的 value 仅 `path`+`bytes`，不写原文。覆盖层未命中、但命中转换后的 `linux.files` 时，source 为 `profile-json`（现有 `json-config` 在 `isProfileLoaded()` 时改写）。

## 快捷画像转换规则

快捷画像加载器不应重新实现一套设备环境后端。它只负责：

1. 校验 `schemaVersion` 必须是 `traceai-device-fingerprint/v1`。
2. 读取 `device-fingerprint.json`。
3. 将已经支持的字段转换为现有 `TraceEnvironmentConfig`。`android.tee.securityLevel` 的整数 `0/1/2` 转为 `SOFTWARE`/`TRUSTED_ENVIRONMENT`/`STRONGBOX`；其它已是上述枚举字符串的值原样保留；无法识别的整数或类型会从转换结果中剥离并记入 reserved 警告，不使整份画像失败。
4. 从文件加载时注册 `fileOverlayRoot` 文件覆盖层（缺省相对目录 `files`）。
5. 对仍 `reserved` 的字段（`android.telephony.cellInfo`、`graphics.native`、`backendStatus`，以及旧形状 `network.capabilities`）先保留原文，再从转换 JSON 中剥离；一次启动警告，不接入当前后端。`android.sensors.samples` 与新形状 `network.capabilities`（`transportTypes`/`networkCapabilities`）已转正，随已实现字段解析。其它非法的已实现字段仍走 `TraceEnvironmentConfig.parse` 并失败。
6. 将配置命中事件的 `source` 统一标记为 `profile-json`（转换后的 JSON 字段与 `linux.files`）或 `profile-file`（覆盖层 `open/read`）。

原有 `setEnvironmentConfig(...)`、`-Dunidbg.env.config` 和完整 JSON 格式必须继续可用。

## 一致性建议

快捷修改不要求数据完全真实，但同一画像中容易被交叉读取的字段应一致：

- `android.packageName`、进程名、APK 路径和数据目录使用同一包名。
- `android.build.MODEL` 与 `ro.product.model` 保持一致。
- `android.build.HARDWARE` 与 `ro.hardware` 保持一致。
- `VERSION.SDK_INT` 与 `ro.build.version.sdk` 保持一致。
- `android.locale.timezoneId` 与 `persist.sys.timezone` 保持一致。
- `wifi.interface`、`network.links.interfaceName` 和网卡名称保持一致。
- Wi-Fi IPv4、网卡 IPv4、网关和子网掩码属于同一网段。
- 电池容量、电流方向、充电状态和状态码不要互相矛盾。
- 传感器 `types`、名称、厂商及其它按 type 映射使用相同的 type 编号。
- `android.build.SERIAL` 与 `ro.serialno` / `ro.boot.serialno` 保持一致。
- `android.identifiers.androidId` 与 `android.settings.secure.android_id` 保持一致。
- `linux.cpu.online` / `present` / `possible` 与 `files/sys/devices/system/cpu/` 对应文件一致。
- `android.runtime.availableProcessors` 与 `linux.cpu.onlineProcessorCount` 保持一致。
- `linux.uname.release` 与 `files/proc/version` 使用同一内核版本串。
- `linux.proc.bootId` 与 `files/proc/sys/kernel/random/boot_id` 保持一致。
- `android.packages[当前包]` 的包名、版本、路径与顶层 `android.packageName` 等保持一致。
- `android.build.SUPPORTED_ABIS[0]` 与 `CPU_ABI` / `ro.product.cpu.abi` 保持一致。

加载器会输出一致性警告，但不会擅自修改用户填写的标记值。

## 第 1 批：签名算法高频稳定输入

面向常见签名/设备指纹算法第一次采集就会读到、且值相对稳定的输入。只复用现有 `TraceEnvironmentConfig` 能力，**不**新增后端、**不**接入 `reserved` 字段。

样例目录 `profiles/pixel6-analysis/` 第 1 批已集中提供：

| 类别 | 样例写入位置 | 典型读取 |
| --- | --- | --- |
| 进程身份补全 | `process.pgid` / `sid` / `supplementaryGids` | `getpgid`、`getsid`、`getgroups` |
| 栈/AT_RANDOM | `random.stackGuardHex`、`random.atRandomHex` | 栈金丝雀、`AT_RANDOM` |
| uname / CPU | `linux.uname`、`linux.cpu` | `uname`、`sysconf`、CPU 列表 |
| environ / auxv | `linux.environ`、`linux.auxv` | `getenv`、auxv |
| rlimit | `linux.rlimits.nofile` | `getrlimit(NOFILE)` |
| sysinfo | `linux.sysinfo` | `sysinfo`（uptime/loads/RAM/swap/procs/`mem_unit`） |
| Build / 属性补全 | `android.build.ID/DISPLAY/TIME/SUPPORTED_ABIS` 与对应 `ro.*` | `Build.*`、`__system_property_get` |
| 序列号交叉 | `SERIAL`、`ro.serialno`、`ro.boot.serialno` | 属性与 `Build.getSerial` |
| ANDROID_ID | `android.identifiers.androidId`、`settings.secure.android_id` | Settings / AdvertisingId |
| Runtime | `android.runtime.availableProcessors` 与内存、少量 `System.getProperty` | `Runtime` / `System` |
| 包与签名 | `android.packages`（当前包 + `signaturesHex`） | `PackageManager` |
| 特性 / Configuration | `android.features`、`android.configuration` | `hasSystemFeature`、`Configuration` |
| so 路径 | `android.nativeLibraryDir32/64` | `ApplicationInfo.nativeLibraryDir` |
| stat | `filesystem.stat`（APK、dataDir、external 三路径） | `stat`/`lstat`/`fstat` |
| mounts | `filesystem.mounts`（`/system`、`/data`、`/storage/emulated/0`） | 挂载表 |
| statfs | `filesystem.statfs."/data"` 与外部存储 | `statfs` |
| EGL 字符串 | `graphics.eglVendor/eglVersion/eglExtensions` | `eglQueryString` |
| 文本文件 | `files/proc/*`、`files/sys/*`（见上节清单；**不含** maps） | `open/read` |

`/proc/self/maps` **不固定**，样例不提供覆盖文件。本批故意不写入：小区信息、clipboard / audio / accessibility、完整 `linux.proc` IO 计数、动态回调。传感器采样与新形状 `NetworkCapabilities` 已可填模板。

## 后端接入顺序

### 第一阶段：快捷修改基础

**已落地：** `DeviceFingerprintProfile` + `setEnvironmentProfile(File)` / `-Dunidbg.env.profile`；已实现字段转换为现有 `TraceEnvironmentConfig`（`android.tee.securityLevel` 的 `0/1/2` 会转成 `SOFTWARE`/`TRUSTED_ENVIRONMENT`/`STRONGBOX`，其它标记值不改写）；仍 `reserved` 的字段（`android.telephony.cellInfo`、`graphics.native`、`backendStatus`、旧形状 `network.capabilities`）**保留原文**并一次启动警告；`android.sensors.samples` 与新形状 capabilities 已转正；`fileOverlayRoot` 只读覆盖与 `/proc/self`↔配置 pid 别名，目录可枚举；sidecar source=`profile-json`/`profile-file`。同一 builder 上显式 `setEnvironmentConfig` 优先于 `setEnvironmentProfile`；`-Dunidbg.env.config` 优先于 `-Dunidbg.env.profile`。样例：`profiles/pixel6-analysis/`。

- 实现 `setEnvironmentProfile(File)` 和 `-Dunidbg.env.profile`。
- 将快捷画像中的已实现字段转换到现有配置模型。
- 实现 `fileOverlayRoot` 和 `/proc/self` 与配置 pid 的别名。
- sidecar 增加 `profile-json`、`profile-file` 来源。

### 第二阶段：常见保留字段

- 传感器固定采样值和最小可用监听回调。
- `NetworkCapabilities` 的固定布尔能力。
- 电话小区信息 marker。
- 电池、电源、温度和网络的单次确定性回调。

### 第三阶段：Native 扩展

- `getifaddrs` 的 IPv4/MAC/flags 子集已由 `network.interfaces` 落地；仍缺 IPv6 条目与必要的 netlink 固定快照。
- Native GLES/EGL/Vulkan 查询 marker。
- 动态链接器模块和路径覆盖。
- 更多 `/proc`、`/sys`、SELinux 和文件标签读取。

## 验收标准

- 修改 `device-fingerprint.json` 中的值后，不重新编译，重新运行即可读到新值。
- 修改 `files/proc/cpuinfo` 后，APP 通过 `open/read` 读到完全相同的字节。
- 已实现字段继续走现有后端和现有测试，不复制实现。
- 未实现的 `reserved` 字段不会导致解析整个画像失败，并能明确报告为 `reserved`。
- 已实现字段的非法值仍使整份画像解析失败。
- 配置缺失时保持当前 unidbg 默认行为。
- APP 读取配置内容时，sidecar 能定位到目标 so 中的调用位置。

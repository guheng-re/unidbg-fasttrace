package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.api.SystemService;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Coverage for {@code android.sensors} + {@code SensorManager.getDefaultSensor(I)} /
 * {@code SensorManager.getDefaultSensor(IZ)} /
 * {@code getSensorList(I)} / {@code getDynamicSensorList(I)} /
 * {@code isDynamicSensorDiscoverySupported()Z} / {@code Sensor.getType()I} /
 * {@code Sensor.getName()Ljava/lang/String;} /
 * {@code Sensor.getVendor()Ljava/lang/String;} /
 * {@code Sensor.getVersion()I} /
 * {@code Sensor.getStringType()Ljava/lang/String;} /
 * {@code Sensor.getMaximumRange()F} /
 * {@code Sensor.getResolution()F} /
 * {@code Sensor.getPower()F} /
 * {@code Sensor.getMinDelay()I} /
 * {@code Sensor.getMaxDelay()I} /
 * {@code Sensor.getFifoReservedEventCount()I} /
 * {@code Sensor.getFifoMaxEventCount()I} /
 * {@code Sensor.isWakeUpSensor()Z} /
 * {@code Sensor.getId()I} /
 * {@code Sensor.getReportingMode()I} /
 * {@code Sensor.isDynamicSensor()Z} /
 * {@code Sensor.getRequiredPermission()Ljava/lang/String;} /
 * {@code Sensor.isAdditionalInfoSupported()Z} /
 * {@code Sensor.getHighestDirectReportRateLevel()I} /
 * {@code Sensor.isDirectChannelTypeSupported(I)Z}
 * (SystemService sensor marker + ConfiguredSensor; VarArg + VaList；
 * {@code getMaximumRange} / {@code getResolution} / {@code getPower} 仅 {@code callFloatMethodV}), plus typed
 * {@code Application}/{@code Context.getSystemService(Class)} for {@code SensorManager}.
 */
public class AndroidSensorsJniTest {

    private static final String SENSOR_MANAGER_CLASS = "android/hardware/SensorManager";
    private static final String SENSOR_CLASS = "android/hardware/Sensor";
    private static final int TYPE_ALL = -1;

    private static final String SENSORS_TYPES_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4]}"
            + "}"
            + "}";

    private static final String SENSORS_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{}"
            + "}"
            + "}";

    private static final String SENSORS_EMPTY_ARRAY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[]}"
            + "}"
            + "}";

    /** dynamicTypes overlaps types (1) and is order-preserving; 4 is types-only. */
    private static final String SENSORS_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"dynamicTypes\":[7,1,65535]}"
            + "}"
            + "}";

    /** dynamicTypes omitted under present sensors → configured-empty dynamic list. */
    private static final String SENSORS_DYNAMIC_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1]}"
            + "}"
            + "}";

    /** Flag true is independent of dynamicTypes (here omitted). */
    private static final String SENSORS_DISCOVERY_TRUE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"dynamicDiscoverySupported\":true}"
            + "}"
            + "}";

    /** Explicit false with nonempty dynamicTypes — must not be inferred true. */
    private static final String SENSORS_DISCOVERY_FALSE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"dynamicTypes\":[7],\"dynamicDiscoverySupported\":false}"
            + "}"
            + "}";

    private static final String NO_SENSORS_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    /** Static type 1 named; type 4 listed but unnamed. */
    private static final String SENSORS_NAMES_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"names\":{\"1\":\"Accel\"}}"
            + "}"
            + "}";

    /** Dynamic-only type 7 named; type 1 is types-only and unnamed. */
    private static final String SENSORS_NAMES_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],\"names\":{\"7\":\"DynGyro\"}}"
            + "}"
            + "}";

    /** Explicit empty names object under present types. */
    private static final String SENSORS_NAMES_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"names\":{}}"
            + "}"
            + "}";

    /** Same type=1 as B, different name — used to prove config-instance binding. */
    private static final String SENSORS_NAMES_ACCEL_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"names\":{\"1\":\"Accel-A\"}}"
            + "}"
            + "}";

    /** Same type=1 as A, different name — replacement must stale old markers. */
    private static final String SENSORS_NAMES_ACCEL_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"names\":{\"1\":\"Accel-B\"}}"
            + "}"
            + "}";

    /** 静态 type 1 配置厂商；type 4 已列出但未配 vendors。不配置 names，证明二者不互推。 */
    private static final String SENSORS_VENDORS_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"vendors\":{\"1\":\"VendorX\"}}"
            + "}"
            + "}";

    /** 仅动态 type 7 配置厂商；type 1 仅在 types 中且无 vendor。 */
    private static final String SENSORS_VENDORS_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],\"vendors\":{\"7\":\"DynVendor\"}}"
            + "}"
            + "}";

    /** 已有 types 时显式空 vendors 对象。 */
    private static final String SENSORS_VENDORS_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"vendors\":{}}"
            + "}"
            + "}";

    /** 与 B 同 type=1，不同厂商名 — 用于证明配置实例绑定。 */
    private static final String SENSORS_VENDORS_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"vendors\":{\"1\":\"Vendor-A\"}}"
            + "}"
            + "}";

    /** 与 A 同 type=1，不同厂商名 — 替换后旧标记必须过期。 */
    private static final String SENSORS_VENDORS_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"vendors\":{\"1\":\"Vendor-B\"}}"
            + "}"
            + "}";

    /** 静态 type 1 版本为 0；type 4 已列出但未配 versions。不配置 names/vendors，证明不互推。 */
    private static final String SENSORS_VERSIONS_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"versions\":{\"1\":0}}"
            + "}"
            + "}";

    /** 仅动态 type 7 配置最大 int 版本；type 1 仅在 types 中且无 version。 */
    private static final String SENSORS_VERSIONS_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"versions\":{\"7\":" + Integer.MAX_VALUE + "}}"
            + "}"
            + "}";

    /** 已有 types 时显式空 versions 对象。 */
    private static final String SENSORS_VERSIONS_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"versions\":{}}"
            + "}"
            + "}";

    /** 与 B 同 type=1，不同版本号 — 用于证明配置实例绑定。 */
    private static final String SENSORS_VERSIONS_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"versions\":{\"1\":1}}"
            + "}"
            + "}";

    /** 与 A 同 type=1，不同版本号 — 替换后旧标记必须过期。 */
    private static final String SENSORS_VERSIONS_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"versions\":{\"1\":2}}"
            + "}"
            + "}";

    /** 静态 type 1 配置 string type；type 4 已列出但未配 stringTypes。不配置 names/vendors/versions。 */
    private static final String SENSORS_STRING_TYPES_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"stringTypes\":{\"1\":\"android.sensor.accelerometer\"}}"
            + "}"
            + "}";

    /** 仅动态 type 7 配置 string type；type 1 仅在 types 中且无 string type。 */
    private static final String SENSORS_STRING_TYPES_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"stringTypes\":{\"7\":\"android.sensor.dynamic\"}}"
            + "}"
            + "}";

    /** 已有 types 时显式空 stringTypes 对象。 */
    private static final String SENSORS_STRING_TYPES_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"stringTypes\":{}}"
            + "}"
            + "}";

    /** 与 B 同 type=1，不同 string type — 用于证明配置实例绑定。 */
    private static final String SENSORS_STRING_TYPES_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"stringTypes\":{\"1\":\"android.sensor.type-a\"}}"
            + "}"
            + "}";

    /** 与 A 同 type=1，不同 string type — 替换后旧标记必须过期。 */
    private static final String SENSORS_STRING_TYPES_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"stringTypes\":{\"1\":\"android.sensor.type-b\"}}"
            + "}"
            + "}";

    /** 静态 type 1 量程为 0.0；type 4 已列出但未配 maximumRanges。不配置其它映射。 */
    private static final String SENSORS_MAXIMUM_RANGES_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"maximumRanges\":{\"1\":0.0}}"
            + "}"
            + "}";

    /** 仅动态 type 7 配置 Float.MAX_VALUE；type 1 仅在 types 中且无量程。 */
    private static final String SENSORS_MAXIMUM_RANGES_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"maximumRanges\":{\"7\":" + Float.MAX_VALUE + "}}"
            + "}"
            + "}";

    /** 已有 types 时显式空 maximumRanges 对象。 */
    private static final String SENSORS_MAXIMUM_RANGES_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"maximumRanges\":{}}"
            + "}"
            + "}";

    /** 与 B 同 type=1，一般小数 39.24 — 用于证明配置实例绑定。 */
    private static final String SENSORS_MAXIMUM_RANGES_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1\":39.24}}"
            + "}"
            + "}";

    /** 与 A 同 type=1，不同量程 — 替换后旧标记必须过期。 */
    private static final String SENSORS_MAXIMUM_RANGES_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1\":78.4}}"
            + "}"
            + "}";

    private static final float MAXIMUM_RANGE_DECIMAL_A = 39.24f;
    private static final float MAXIMUM_RANGE_DECIMAL_B = 78.4f;

    /** 静态 type 1 分辨率为 0.0；type 4 已列出但未配 resolutions。不配置其它映射。 */
    private static final String SENSORS_RESOLUTIONS_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"resolutions\":{\"1\":0.0}}"
            + "}"
            + "}";

    /** 仅动态 type 7 配置 Float.MAX_VALUE；type 1 仅在 types 中且无分辨率。 */
    private static final String SENSORS_RESOLUTIONS_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"resolutions\":{\"7\":" + Float.MAX_VALUE + "}}"
            + "}"
            + "}";

    /** 已有 types 时显式空 resolutions 对象。 */
    private static final String SENSORS_RESOLUTIONS_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"resolutions\":{}}"
            + "}"
            + "}";

    /** 与 B 同 type=1，一般小数 0.01 — 用于证明配置实例绑定。 */
    private static final String SENSORS_RESOLUTIONS_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"resolutions\":{\"1\":0.01}}"
            + "}"
            + "}";

    /** 与 A 同 type=1，不同分辨率 — 替换后旧标记必须过期。 */
    private static final String SENSORS_RESOLUTIONS_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"resolutions\":{\"1\":0.02}}"
            + "}"
            + "}";

    private static final float RESOLUTION_DECIMAL_A = 0.01f;
    private static final float RESOLUTION_DECIMAL_B = 0.02f;

    /** 静态 type 1 功耗为 0.0；type 4 已列出但未配 powers。不配置其它映射。 */
    private static final String SENSORS_POWERS_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"powers\":{\"1\":0.0}}"
            + "}"
            + "}";

    /** 仅动态 type 7 配置 Float.MAX_VALUE；type 1 仅在 types 中且无功耗。 */
    private static final String SENSORS_POWERS_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"powers\":{\"7\":" + Float.MAX_VALUE + "}}"
            + "}"
            + "}";

    /** 已有 types 时显式空 powers 对象。 */
    private static final String SENSORS_POWERS_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"powers\":{}}"
            + "}"
            + "}";

    /** 与 B 同 type=1，一般小数 0.13 — 用于证明配置实例绑定。 */
    private static final String SENSORS_POWERS_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"powers\":{\"1\":0.13}}"
            + "}"
            + "}";

    /** 与 A 同 type=1，不同功耗 — 替换后旧标记必须过期。 */
    private static final String SENSORS_POWERS_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"powers\":{\"1\":0.26}}"
            + "}"
            + "}";

    private static final float POWER_DECIMAL_A = 0.13f;
    private static final float POWER_DECIMAL_B = 0.26f;

    /** 静态 type 1 最小延迟为 0；type 4 已列出但未配 minDelaysMicros。不配置其它映射。 */
    private static final String SENSORS_MIN_DELAYS_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"minDelaysMicros\":{\"1\":0}}"
            + "}"
            + "}";

    /** 仅动态 type 7 配置最大 int 最小延迟；type 1 仅在 types 中且无 minDelay。 */
    private static final String SENSORS_MIN_DELAYS_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"minDelaysMicros\":{\"7\":" + Integer.MAX_VALUE + "}}"
            + "}"
            + "}";

    /** 已有 types 时显式空 minDelaysMicros 对象。 */
    private static final String SENSORS_MIN_DELAYS_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"minDelaysMicros\":{}}"
            + "}"
            + "}";

    /** 与 B 同 type=1，一般最小延迟 5000 — 用于证明配置实例绑定。 */
    private static final String SENSORS_MIN_DELAYS_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":5000}}"
            + "}"
            + "}";

    /** 与 A 同 type=1，不同最小延迟 — 替换后旧标记必须过期。 */
    private static final String SENSORS_MIN_DELAYS_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":10000}}"
            + "}"
            + "}";

    private static final int MIN_DELAY_MICROS_GENERAL_A = 5000;
    private static final int MIN_DELAY_MICROS_GENERAL_B = 10000;

    /** 静态 type 1 最大延迟为 0；type 4 已列出但未配 maxDelaysMicros。不配置其它映射。 */
    private static final String SENSORS_MAX_DELAYS_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"maxDelaysMicros\":{\"1\":0}}"
            + "}"
            + "}";

    /** 仅动态 type 7 配置最大 int 最大延迟；type 1 仅在 types 中且无 maxDelay。 */
    private static final String SENSORS_MAX_DELAYS_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"maxDelaysMicros\":{\"7\":" + Integer.MAX_VALUE + "}}"
            + "}"
            + "}";

    /** 已有 types 时显式空 maxDelaysMicros 对象。 */
    private static final String SENSORS_MAX_DELAYS_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{}}"
            + "}"
            + "}";

    /** 与 B 同 type=1，一般最大延迟 20000 — 用于证明配置实例绑定。 */
    private static final String SENSORS_MAX_DELAYS_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1\":20000}}"
            + "}"
            + "}";

    /** 与 A 同 type=1，不同最大延迟 — 替换后旧标记必须过期。 */
    private static final String SENSORS_MAX_DELAYS_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1\":40000}}"
            + "}"
            + "}";

    /** min 与 max 同时配置且 max<min：证明不校验 max>=min，也不互推。 */
    private static final String SENSORS_MIN_MAX_INDEPENDENT_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],"
            + "\"minDelaysMicros\":{\"1\":10000},"
            + "\"maxDelaysMicros\":{\"1\":1000}}"
            + "}"
            + "}";

    private static final int MAX_DELAY_MICROS_GENERAL_A = 20000;
    private static final int MAX_DELAY_MICROS_GENERAL_B = 40000;
    private static final int MIN_DELAY_MICROS_INDEPENDENT = 10000;
    private static final int MAX_DELAY_MICROS_INDEPENDENT = 1000;

    /** 静态 type 1 FIFO 预留事件数为 0；type 4 已列出但未配 fifoReservedEventCounts。不配置其它映射。 */
    private static final String SENSORS_FIFO_RESERVED_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"fifoReservedEventCounts\":{\"1\":0}}"
            + "}"
            + "}";

    /** 仅动态 type 7 配置最大 int FIFO 预留事件数；type 1 仅在 types 中且无 fifoReserved。 */
    private static final String SENSORS_FIFO_RESERVED_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"fifoReservedEventCounts\":{\"7\":" + Integer.MAX_VALUE + "}}"
            + "}"
            + "}";

    /** 已有 types 时显式空 fifoReservedEventCounts 对象。 */
    private static final String SENSORS_FIFO_RESERVED_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{}}"
            + "}"
            + "}";

    /** 与 B 同 type=1，一般 FIFO 预留事件数 128 — 用于证明配置实例绑定。 */
    private static final String SENSORS_FIFO_RESERVED_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1\":128}}"
            + "}"
            + "}";

    /** 与 A 同 type=1，不同 FIFO 预留事件数 — 替换后旧标记必须过期。 */
    private static final String SENSORS_FIFO_RESERVED_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1\":256}}"
            + "}"
            + "}";

    private static final int FIFO_RESERVED_GENERAL_A = 128;
    private static final int FIFO_RESERVED_GENERAL_B = 256;

    /** 静态 type 1 FIFO 最大事件数为 0；type 4 已列出但未配 fifoMaxEventCounts。不配置其它映射。 */
    private static final String SENSORS_FIFO_MAX_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"fifoMaxEventCounts\":{\"1\":0}}"
            + "}"
            + "}";

    /** 仅动态 type 7 配置最大 int FIFO 最大事件数；type 1 仅在 types 中且无 fifoMax。 */
    private static final String SENSORS_FIFO_MAX_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"fifoMaxEventCounts\":{\"7\":" + Integer.MAX_VALUE + "}}"
            + "}"
            + "}";

    /** 已有 types 时显式空 fifoMaxEventCounts 对象。 */
    private static final String SENSORS_FIFO_MAX_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{}}"
            + "}"
            + "}";

    /** 与 B 同 type=1，一般 FIFO 最大事件数 512 — 用于证明配置实例绑定。 */
    private static final String SENSORS_FIFO_MAX_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1\":512}}"
            + "}"
            + "}";

    /** 与 A 同 type=1，不同 FIFO 最大事件数 — 替换后旧标记必须过期。 */
    private static final String SENSORS_FIFO_MAX_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1\":1024}}"
            + "}"
            + "}";

    /** reserved 与 max 同时配置且 max<reserved：证明不校验相对大小，也不互推。 */
    private static final String SENSORS_FIFO_RESERVED_MAX_INDEPENDENT_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],"
            + "\"fifoReservedEventCounts\":{\"1\":256},"
            + "\"fifoMaxEventCounts\":{\"1\":128}}"
            + "}"
            + "}";

    private static final int FIFO_MAX_GENERAL_A = 512;
    private static final int FIFO_MAX_GENERAL_B = 1024;
    private static final int FIFO_RESERVED_INDEPENDENT = 256;
    private static final int FIFO_MAX_INDEPENDENT = 128;

    /** 静态 type 1 显式 false；type 4 已列出但未配 wakeUpSensors。不配置其它映射。 */
    private static final String SENSORS_WAKE_UP_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"wakeUpSensors\":{\"1\":false}}"
            + "}"
            + "}";

    /** 仅动态 type 7 配置 true；type 1 仅在 types 中且无 wake-up。 */
    private static final String SENSORS_WAKE_UP_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"wakeUpSensors\":{\"7\":true}}"
            + "}"
            + "}";

    /** 已有 types 时显式空 wakeUpSensors 对象。 */
    private static final String SENSORS_WAKE_UP_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"wakeUpSensors\":{}}"
            + "}"
            + "}";

    /** 与 B 同 type=1，wake-up false — 用于证明配置实例绑定。 */
    private static final String SENSORS_WAKE_UP_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"wakeUpSensors\":{\"1\":false}}"
            + "}"
            + "}";

    /** 与 A 同 type=1，wake-up true — 替换后旧标记必须过期。 */
    private static final String SENSORS_WAKE_UP_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"wakeUpSensors\":{\"1\":true}}"
            + "}"
            + "}";

    /** 静态 type 1 id=-1；type 4 已列出但未配 sensorIds。 */
    private static final String SENSORS_IDS_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"sensorIds\":{\"1\":-1}}"
            + "}"
            + "}";

    /** 仅动态 type 7 配置 Integer.MAX_VALUE id。 */
    private static final String SENSORS_IDS_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"sensorIds\":{\"7\":" + Integer.MAX_VALUE + "}}"
            + "}"
            + "}";

    private static final String SENSORS_IDS_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"sensorIds\":{}}"
            + "}"
            + "}";

    private static final String SENSORS_IDS_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"sensorIds\":{\"1\":0}}"
            + "}"
            + "}";

    private static final String SENSORS_IDS_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"sensorIds\":{\"1\":42}}"
            + "}"
            + "}";

    /** 静态 type 1 reportingMode=0（CONTINUOUS）；type 4 已列出但未配。 */
    private static final String SENSORS_REPORTING_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"reportingModes\":{\"1\":0}}"
            + "}"
            + "}";

    private static final String SENSORS_REPORTING_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"reportingModes\":{\"7\":3}}"
            + "}"
            + "}";

    private static final String SENSORS_REPORTING_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"reportingModes\":{}}"
            + "}"
            + "}";

    private static final String SENSORS_REPORTING_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"reportingModes\":{\"1\":1}}"
            + "}"
            + "}";

    private static final String SENSORS_REPORTING_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"reportingModes\":{\"1\":2}}"
            + "}"
            + "}";

    private static final String SENSORS_DIRECT_RATE_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"highestDirectReportRateLevels\":{\"1\":0}}"
            + "}"
            + "}";

    private static final String SENSORS_DIRECT_RATE_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"highestDirectReportRateLevels\":{\"7\":3}}"
            + "}"
            + "}";

    private static final String SENSORS_DIRECT_RATE_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"highestDirectReportRateLevels\":{}}"
            + "}"
            + "}";

    private static final String SENSORS_DIRECT_RATE_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"highestDirectReportRateLevels\":{\"1\":1}}"
            + "}"
            + "}";

    private static final String SENSORS_DIRECT_RATE_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"highestDirectReportRateLevels\":{\"1\":2}}"
            + "}"
            + "}";

    /** 静态 type 1 仅支持 TYPE_MEMORY_FILE(1)；type 4 已列出但未配通道集合。 */
    private static final String SENSORS_DIRECT_CHANNEL_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"directChannelTypesSupported\":{\"1\":[1]}}"
            + "}"
            + "}";

    private static final String SENSORS_DIRECT_CHANNEL_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"directChannelTypesSupported\":{\"7\":[1,2]}}"
            + "}"
            + "}";

    private static final String SENSORS_DIRECT_CHANNEL_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{}}"
            + "}"
            + "}";

    /** 显式空数组：type 已配置，任意通道查询返回 false。 */
    private static final String SENSORS_DIRECT_CHANNEL_EMPTY_ARRAY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{\"1\":[]}}"
            + "}"
            + "}";

    private static final String SENSORS_DIRECT_CHANNEL_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{\"1\":[1]}}"
            + "}"
            + "}";

    private static final String SENSORS_DIRECT_CHANNEL_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{\"1\":[2]}}"
            + "}"
            + "}";

    /** 静态 type 1 显式 false；type 4 已列出但未配 dynamicSensors。 */
    private static final String SENSORS_DYNAMIC_FLAG_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"dynamicSensors\":{\"1\":false}}"
            + "}"
            + "}";

    private static final String SENSORS_DYNAMIC_FLAG_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"dynamicSensors\":{\"7\":true}}"
            + "}"
            + "}";

    /** dynamicTypes 含 7 但未配 dynamicSensors：不得推断 isDynamicSensor。 */
    private static final String SENSORS_DYNAMIC_TYPES_WITHOUT_FLAG_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7]}"
            + "}"
            + "}";

    private static final String SENSORS_DYNAMIC_FLAG_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],\"dynamicSensors\":{}}"
            + "}"
            + "}";

    private static final String SENSORS_DYNAMIC_FLAG_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicSensors\":{\"1\":false}}"
            + "}"
            + "}";

    private static final String SENSORS_DYNAMIC_FLAG_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicSensors\":{\"1\":true}}"
            + "}"
            + "}";

    /** 静态 type 1 空权限串；type 4 已列出但未配 requiredPermissions。 */
    private static final String SENSORS_PERM_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"requiredPermissions\":{\"1\":\"\"}}"
            + "}"
            + "}";

    private static final String SENSORS_PERM_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"requiredPermissions\":{\"7\":\"android.permission.BODY_SENSORS\"}}"
            + "}"
            + "}";

    private static final String SENSORS_PERM_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"requiredPermissions\":{}}"
            + "}"
            + "}";

    private static final String SENSORS_PERM_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],"
            + "\"requiredPermissions\":{\"1\":\"android.permission.BODY_SENSORS\"}}"
            + "}"
            + "}";

    private static final String SENSORS_PERM_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],"
            + "\"requiredPermissions\":{\"1\":\"android.permission.HIGH_SAMPLING_RATE_SENSORS\"}}"
            + "}"
            + "}";

    private static final String SENSORS_ADDITIONAL_STATIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1,4],\"additionalInfoSupported\":{\"1\":false}}"
            + "}"
            + "}";

    private static final String SENSORS_ADDITIONAL_DYNAMIC_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"dynamicTypes\":[7],"
            + "\"additionalInfoSupported\":{\"7\":true}}"
            + "}"
            + "}";

    private static final String SENSORS_ADDITIONAL_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"additionalInfoSupported\":{}}"
            + "}"
            + "}";

    private static final String SENSORS_ADDITIONAL_A_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"additionalInfoSupported\":{\"1\":false}}"
            + "}"
            + "}";

    private static final String SENSORS_ADDITIONAL_B_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"sensors\":{\"types\":[1],\"additionalInfoSupported\":{\"1\":true}}"
            + "}"
            + "}";

    @Test
    public void testGetDefaultSensorFoundVarArg32() throws Exception {
        runGetDefaultSensorFound(false, false);
    }

    @Test
    public void testGetDefaultSensorFoundVaList64() throws Exception {
        runGetDefaultSensorFound(true, true);
    }

    @Test
    public void testGetDefaultSensorUnlistedNullVarArg32() throws Exception {
        runGetDefaultSensorNull(false, false, SENSORS_TYPES_JSON, 2);
    }

    @Test
    public void testGetDefaultSensorUnlistedNullVaList64() throws Exception {
        runGetDefaultSensorNull(true, true, SENSORS_TYPES_JSON, 2);
    }

    @Test
    public void testGetDefaultSensorEmptyObjectNullVarArg32() throws Exception {
        runGetDefaultSensorNull(false, false, SENSORS_EMPTY_JSON, 1);
    }

    @Test
    public void testGetDefaultSensorEmptyArrayNullVaList64() throws Exception {
        runGetDefaultSensorNull(true, true, SENSORS_EMPTY_ARRAY_JSON, 1);
    }

    @Test
    public void testGetDefaultSensorAbsentVarArg32() throws Exception {
        runAbsentGetDefaultSensor(false, false);
    }

    @Test
    public void testGetDefaultSensorAbsentVaList64() throws Exception {
        runAbsentGetDefaultSensor(true, true);
    }

    @Test
    public void testGetDefaultSensorByWakeUpMatchVarArg32() throws Exception {
        runGetDefaultSensorByWakeUpMatch(false, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpMatchVaList64() throws Exception {
        runGetDefaultSensorByWakeUpMatch(true, true);
    }

    @Test
    public void testGetDefaultSensorByWakeUpMismatchNullVarArg32() throws Exception {
        runGetDefaultSensorByWakeUpMismatchNull(false, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpMismatchNullVaList64() throws Exception {
        runGetDefaultSensorByWakeUpMismatchNull(true, true);
    }

    @Test
    public void testGetDefaultSensorByWakeUpUnlistedNullVarArg32() throws Exception {
        runGetDefaultSensorByWakeUpUnlistedNull(false, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpUnlistedNullVaList64() throws Exception {
        runGetDefaultSensorByWakeUpUnlistedNull(true, true);
    }

    @Test
    public void testGetDefaultSensorByWakeUpEmptyObjectNullVarArg32() throws Exception {
        runGetDefaultSensorByWakeUpNull(false, false, SENSORS_EMPTY_JSON, 1, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpEmptyArrayNullVaList64() throws Exception {
        runGetDefaultSensorByWakeUpNull(true, true, SENSORS_EMPTY_ARRAY_JSON, 1, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpListedWithoutFlagVarArg32() throws Exception {
        runGetDefaultSensorByWakeUpNull(false, false, SENSORS_WAKE_UP_STATIC_JSON, 4, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpListedWithoutFlagVaList64() throws Exception {
        runGetDefaultSensorByWakeUpNull(true, true, SENSORS_WAKE_UP_STATIC_JSON, 4, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpOmittedVarArg32() throws Exception {
        runGetDefaultSensorByWakeUpNull(false, false, SENSORS_TYPES_JSON, 1, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpOmittedVaList64() throws Exception {
        runGetDefaultSensorByWakeUpNull(true, true, SENSORS_TYPES_JSON, 1, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpEmptyWakeUpObjectVarArg32() throws Exception {
        runGetDefaultSensorByWakeUpNull(false, false, SENSORS_WAKE_UP_EMPTY_JSON, 1, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpEmptyWakeUpObjectVaList64() throws Exception {
        runGetDefaultSensorByWakeUpNull(true, true, SENSORS_WAKE_UP_EMPTY_JSON, 1, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpDynamicTypeNotDefaultVarArg32() throws Exception {
        runGetDefaultSensorByWakeUpNull(false, false, SENSORS_WAKE_UP_DYNAMIC_JSON, 7, true);
    }

    @Test
    public void testGetDefaultSensorByWakeUpDynamicTypeNotDefaultVaList64() throws Exception {
        runGetDefaultSensorByWakeUpNull(true, true, SENSORS_WAKE_UP_DYNAMIC_JSON, 7, true);
    }

    @Test
    public void testGetDefaultSensorByWakeUpAbsentVarArg32() throws Exception {
        runGetDefaultSensorByWakeUpAbsent(false, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpAbsentVaList64() throws Exception {
        runGetDefaultSensorByWakeUpAbsent(true, true);
    }

    @Test
    public void testGetDefaultSensorByWakeUpIsolationVarArg32() throws Exception {
        runGetDefaultSensorByWakeUpIsolation(false, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpIsolationVaList64() throws Exception {
        runGetDefaultSensorByWakeUpIsolation(true, true);
    }

    @Test
    public void testGetDefaultSensorByWakeUpCurrentConfigReplaceVarArg32() throws Exception {
        runGetDefaultSensorByWakeUpCurrentConfigReplace(false, false);
    }

    @Test
    public void testGetDefaultSensorByWakeUpCurrentConfigReplaceVaList64() throws Exception {
        runGetDefaultSensorByWakeUpCurrentConfigReplace(true, true);
    }

    @Test
    public void testSensorGetTypeAndProvenanceVarArg32() throws Exception {
        runSensorGetTypeAndProvenance(false, false);
    }

    @Test
    public void testSensorGetTypeAndProvenanceVaList64() throws Exception {
        runSensorGetTypeAndProvenance(true, true);
    }

    @Test
    public void testPlainAndOtherSystemServiceIsolationVarArg32() throws Exception {
        runPlainAndOtherIsolation(false, false);
    }

    @Test
    public void testPlainAndOtherSystemServiceIsolationVaList64() throws Exception {
        runPlainAndOtherIsolation(true, true);
    }

    @Test
    public void testSensorServiceNameAndValueVarArg32() throws Exception {
        runServiceNameAndValue(false, false);
    }

    @Test
    public void testSensorServiceNameAndValueVaList64() throws Exception {
        runServiceNameAndValue(true, true);
    }

    @Test
    public void testSensorsTypedGetSystemServiceVarArg32() throws Exception {
        runTypedGetSystemService(false, false);
    }

    @Test
    public void testSensorsTypedGetSystemServiceVaList64() throws Exception {
        runTypedGetSystemService(true, true);
    }

    @Test
    public void testGetSensorListAllVarArg32() throws Exception {
        runGetSensorListAll(false, false);
    }

    @Test
    public void testGetSensorListAllVaList64() throws Exception {
        runGetSensorListAll(true, true);
    }

    @Test
    public void testGetSensorListSingleFoundVarArg32() throws Exception {
        runGetSensorListSingle(false, false, 1, 1);
    }

    @Test
    public void testGetSensorListSingleFoundVaList64() throws Exception {
        runGetSensorListSingle(true, true, 4, 1);
    }

    @Test
    public void testGetSensorListSingleMissingEmptyVarArg32() throws Exception {
        runGetSensorListSingle(false, false, 2, 0);
    }

    @Test
    public void testGetSensorListSingleMissingEmptyVaList64() throws Exception {
        runGetSensorListSingle(true, true, 99, 0);
    }

    @Test
    public void testGetSensorListEmptyConfigAllVarArg32() throws Exception {
        runGetSensorListEmptyConfig(false, false, SENSORS_EMPTY_JSON);
    }

    @Test
    public void testGetSensorListEmptyArrayAllVaList64() throws Exception {
        runGetSensorListEmptyConfig(true, true, SENSORS_EMPTY_ARRAY_JSON);
    }

    @Test
    public void testGetSensorListAbsentVarArg32() throws Exception {
        runAbsentGetSensorList(false, false);
    }

    @Test
    public void testGetSensorListAbsentVaList64() throws Exception {
        runAbsentGetSensorList(true, true);
    }

    @Test
    public void testGetSensorListFreshnessAndGetTypeVarArg32() throws Exception {
        runGetSensorListFreshnessAndGetType(false, false);
    }

    @Test
    public void testGetSensorListFreshnessAndGetTypeVaList64() throws Exception {
        runGetSensorListFreshnessAndGetType(true, true);
    }

    @Test
    public void testGetDynamicSensorListAllVarArg32() throws Exception {
        runGetDynamicSensorListAll(false, false);
    }

    @Test
    public void testGetDynamicSensorListAllVaList64() throws Exception {
        runGetDynamicSensorListAll(true, true);
    }

    @Test
    public void testGetDynamicSensorListSingleFoundVarArg32() throws Exception {
        runGetDynamicSensorListSingle(false, false, 1, 1);
    }

    @Test
    public void testGetDynamicSensorListSingleFoundVaList64() throws Exception {
        runGetDynamicSensorListSingle(true, true, 1, 1);
    }

    @Test
    public void testGetDynamicSensorListSingleMissingVarArg32() throws Exception {
        runGetDynamicSensorListSingle(false, false, 4, 0);
    }

    @Test
    public void testGetDynamicSensorListSingleMissingVaList64() throws Exception {
        runGetDynamicSensorListSingle(true, true, 4, 0);
    }

    @Test
    public void testGetDynamicSensorListEmptyConfigVarArg32() throws Exception {
        runGetDynamicSensorListEmptyConfig(false, false);
    }

    @Test
    public void testGetDynamicSensorListEmptyConfigVaList64() throws Exception {
        runGetDynamicSensorListEmptyConfig(true, true);
    }

    @Test
    public void testGetDynamicSensorListAbsentVarArg32() throws Exception {
        runAbsentGetDynamicSensorList(false, false);
    }

    @Test
    public void testGetDynamicSensorListAbsentVaList64() throws Exception {
        runAbsentGetDynamicSensorList(true, true);
    }

    @Test
    public void testIsDynamicSensorDiscoverySupportedTrueVarArg32() throws Exception {
        runIsDynamicSensorDiscoverySupported(false, false, SENSORS_DISCOVERY_TRUE_JSON, true);
    }

    @Test
    public void testIsDynamicSensorDiscoverySupportedTrueVaList64() throws Exception {
        runIsDynamicSensorDiscoverySupported(true, true, SENSORS_DISCOVERY_TRUE_JSON, true);
    }

    @Test
    public void testIsDynamicSensorDiscoverySupportedFalseVarArg32() throws Exception {
        runIsDynamicSensorDiscoverySupported(false, false, SENSORS_DISCOVERY_FALSE_JSON, false);
    }

    @Test
    public void testIsDynamicSensorDiscoverySupportedFalseVaList64() throws Exception {
        runIsDynamicSensorDiscoverySupported(true, true, SENSORS_DISCOVERY_FALSE_JSON, false);
    }

    @Test
    public void testIsDynamicSensorDiscoverySupportedDefaultFalseVarArg32() throws Exception {
        // dynamicTypes present, flag omitted → false (never inferred)
        runIsDynamicSensorDiscoverySupported(false, false, SENSORS_DYNAMIC_JSON, false);
    }

    @Test
    public void testIsDynamicSensorDiscoverySupportedDefaultFalseVaList64() throws Exception {
        runIsDynamicSensorDiscoverySupported(true, true, SENSORS_EMPTY_JSON, false);
    }

    @Test
    public void testIsDynamicSensorDiscoverySupportedAbsentVarArg32() throws Exception {
        runAbsentIsDynamicSensorDiscoverySupported(false, false);
    }

    @Test
    public void testIsDynamicSensorDiscoverySupportedAbsentVaList64() throws Exception {
        runAbsentIsDynamicSensorDiscoverySupported(true, true);
    }

    @Test
    public void testIsDynamicSensorDiscoverySupportedIsolationVarArg32() throws Exception {
        runIsDynamicSensorDiscoverySupportedIsolation(false, false);
    }

    @Test
    public void testIsDynamicSensorDiscoverySupportedIsolationVaList64() throws Exception {
        runIsDynamicSensorDiscoverySupportedIsolation(true, true);
    }

    @Test
    public void testSensorGetNameStaticTypeVarArg32() throws Exception {
        runSensorGetNameStaticType(false, false);
    }

    @Test
    public void testSensorGetNameStaticTypeVaList64() throws Exception {
        runSensorGetNameStaticType(true, true);
    }

    @Test
    public void testSensorGetNameDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetNameDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetNameDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetNameDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetNameNamesOmittedVarArg32() throws Exception {
        runSensorGetNameNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetNameNamesOmittedVaList64() throws Exception {
        runSensorGetNameNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetNameEmptyNamesObjectVarArg32() throws Exception {
        runSensorGetNameNotHandledOnLiveMarker(false, false, SENSORS_NAMES_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetNameEmptyNamesObjectVaList64() throws Exception {
        runSensorGetNameNotHandledOnLiveMarker(true, true, SENSORS_NAMES_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetNameListedTypeWithoutNameVarArg32() throws Exception {
        runSensorGetNameNotHandledOnLiveMarker(false, false, SENSORS_NAMES_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetNameListedTypeWithoutNameVaList64() throws Exception {
        runSensorGetNameNotHandledOnLiveMarker(true, true, SENSORS_NAMES_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetNameUnknownTypeVarArg32() throws Exception {
        runSensorGetNameUnknownType(false, false);
    }

    @Test
    public void testSensorGetNameUnknownTypeVaList64() throws Exception {
        runSensorGetNameUnknownType(true, true);
    }

    @Test
    public void testSensorGetNameIsolationVarArg32() throws Exception {
        runSensorGetNameIsolation(false, false);
    }

    @Test
    public void testSensorGetNameIsolationVaList64() throws Exception {
        runSensorGetNameIsolation(true, true);
    }

    @Test
    public void testSensorMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetNameParseRejects() {
        runSensorGetNameParseRejects();
    }

    @Test
    public void testSensorGetVendorStaticTypeVarArg32() throws Exception {
        runSensorGetVendorStaticType(false, false);
    }

    @Test
    public void testSensorGetVendorStaticTypeVaList64() throws Exception {
        runSensorGetVendorStaticType(true, true);
    }

    @Test
    public void testSensorGetVendorDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetVendorDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetVendorDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetVendorDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetVendorVendorsOmittedVarArg32() throws Exception {
        runSensorGetVendorNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetVendorVendorsOmittedVaList64() throws Exception {
        runSensorGetVendorNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetVendorEmptyVendorsObjectVarArg32() throws Exception {
        runSensorGetVendorNotHandledOnLiveMarker(false, false, SENSORS_VENDORS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetVendorEmptyVendorsObjectVaList64() throws Exception {
        runSensorGetVendorNotHandledOnLiveMarker(true, true, SENSORS_VENDORS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetVendorListedTypeWithoutVendorVarArg32() throws Exception {
        runSensorGetVendorNotHandledOnLiveMarker(false, false, SENSORS_VENDORS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetVendorListedTypeWithoutVendorVaList64() throws Exception {
        runSensorGetVendorNotHandledOnLiveMarker(true, true, SENSORS_VENDORS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetVendorNamesDoNotImplyVendorVarArg32() throws Exception {
        runSensorGetVendorNotHandledOnLiveMarker(false, false, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetVendorNamesDoNotImplyVendorVaList64() throws Exception {
        runSensorGetVendorNotHandledOnLiveMarker(true, true, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetVendorUnknownTypeVarArg32() throws Exception {
        runSensorGetVendorUnknownType(false, false);
    }

    @Test
    public void testSensorGetVendorUnknownTypeVaList64() throws Exception {
        runSensorGetVendorUnknownType(true, true);
    }

    @Test
    public void testSensorGetVendorIsolationVarArg32() throws Exception {
        runSensorGetVendorIsolation(false, false);
    }

    @Test
    public void testSensorGetVendorIsolationVaList64() throws Exception {
        runSensorGetVendorIsolation(true, true);
    }

    @Test
    public void testSensorVendorMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorVendorMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorVendorMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorVendorMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetVendorParseRejects() {
        runSensorGetVendorParseRejects();
    }

    @Test
    public void testSensorGetVersionStaticTypeVarArg32() throws Exception {
        runSensorGetVersionStaticType(false, false);
    }

    @Test
    public void testSensorGetVersionStaticTypeVaList64() throws Exception {
        runSensorGetVersionStaticType(true, true);
    }

    @Test
    public void testSensorGetVersionDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetVersionDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetVersionDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetVersionDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetVersionVersionsOmittedVarArg32() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetVersionVersionsOmittedVaList64() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetVersionEmptyVersionsObjectVarArg32() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(false, false, SENSORS_VERSIONS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetVersionEmptyVersionsObjectVaList64() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(true, true, SENSORS_VERSIONS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetVersionListedTypeWithoutVersionVarArg32() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(false, false, SENSORS_VERSIONS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetVersionListedTypeWithoutVersionVaList64() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(true, true, SENSORS_VERSIONS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetVersionNamesDoNotImplyVersionVarArg32() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(false, false, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetVersionNamesDoNotImplyVersionVaList64() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(true, true, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetVersionVendorsDoNotImplyVersionVarArg32() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(false, false, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetVersionVendorsDoNotImplyVersionVaList64() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(true, true, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetVersionUnknownTypeVarArg32() throws Exception {
        runSensorGetVersionUnknownType(false, false);
    }

    @Test
    public void testSensorGetVersionUnknownTypeVaList64() throws Exception {
        runSensorGetVersionUnknownType(true, true);
    }

    @Test
    public void testSensorGetVersionIsolationVarArg32() throws Exception {
        runSensorGetVersionIsolation(false, false);
    }

    @Test
    public void testSensorGetVersionIsolationVaList64() throws Exception {
        runSensorGetVersionIsolation(true, true);
    }

    @Test
    public void testSensorVersionMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorVersionMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorVersionMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorVersionMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetVersionParseRejects() {
        runSensorGetVersionParseRejects();
    }

    @Test
    public void testSensorGetStringTypeStaticTypeVarArg32() throws Exception {
        runSensorGetStringTypeStaticType(false, false);
    }

    @Test
    public void testSensorGetStringTypeStaticTypeVaList64() throws Exception {
        runSensorGetStringTypeStaticType(true, true);
    }

    @Test
    public void testSensorGetStringTypeDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetStringTypeDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetStringTypeDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetStringTypeDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetStringTypeStringTypesOmittedVarArg32() throws Exception {
        runSensorGetStringTypeNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetStringTypeStringTypesOmittedVaList64() throws Exception {
        runSensorGetStringTypeNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetStringTypeEmptyStringTypesObjectVarArg32() throws Exception {
        runSensorGetStringTypeNotHandledOnLiveMarker(false, false, SENSORS_STRING_TYPES_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetStringTypeEmptyStringTypesObjectVaList64() throws Exception {
        runSensorGetStringTypeNotHandledOnLiveMarker(true, true, SENSORS_STRING_TYPES_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetStringTypeListedTypeWithoutStringTypeVarArg32() throws Exception {
        runSensorGetStringTypeNotHandledOnLiveMarker(false, false, SENSORS_STRING_TYPES_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetStringTypeListedTypeWithoutStringTypeVaList64() throws Exception {
        runSensorGetStringTypeNotHandledOnLiveMarker(true, true, SENSORS_STRING_TYPES_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetStringTypeNamesDoNotImplyStringTypeVarArg32() throws Exception {
        runSensorGetStringTypeNotHandledOnLiveMarker(false, false, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetStringTypeNamesDoNotImplyStringTypeVaList64() throws Exception {
        runSensorGetStringTypeNotHandledOnLiveMarker(true, true, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetStringTypeVendorsDoNotImplyStringTypeVarArg32() throws Exception {
        runSensorGetStringTypeNotHandledOnLiveMarker(false, false, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetStringTypeVendorsDoNotImplyStringTypeVaList64() throws Exception {
        runSensorGetStringTypeNotHandledOnLiveMarker(true, true, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetStringTypeVersionsDoNotImplyStringTypeVarArg32() throws Exception {
        runSensorGetStringTypeNotHandledOnLiveMarker(false, false, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetStringTypeVersionsDoNotImplyStringTypeVaList64() throws Exception {
        runSensorGetStringTypeNotHandledOnLiveMarker(true, true, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetStringTypeUnknownTypeVarArg32() throws Exception {
        runSensorGetStringTypeUnknownType(false, false);
    }

    @Test
    public void testSensorGetStringTypeUnknownTypeVaList64() throws Exception {
        runSensorGetStringTypeUnknownType(true, true);
    }

    @Test
    public void testSensorGetStringTypeIsolationVarArg32() throws Exception {
        runSensorGetStringTypeIsolation(false, false);
    }

    @Test
    public void testSensorGetStringTypeIsolationVaList64() throws Exception {
        runSensorGetStringTypeIsolation(true, true);
    }

    @Test
    public void testSensorStringTypeMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorStringTypeMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorStringTypeMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorStringTypeMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetStringTypeParseRejects() {
        runSensorGetStringTypeParseRejects();
    }

    @Test
    public void testSensorGetMaximumRangeStaticTypeVarArg32() throws Exception {
        runSensorGetMaximumRangeStaticType(false, false);
    }

    @Test
    public void testSensorGetMaximumRangeStaticTypeVaList64() throws Exception {
        runSensorGetMaximumRangeStaticType(true, true);
    }

    @Test
    public void testSensorGetMaximumRangeDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetMaximumRangeDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetMaximumRangeDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetMaximumRangeDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetMaximumRangeOmittedVarArg32() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangeOmittedVaList64() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangeEmptyObjectVarArg32() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(false, false, SENSORS_MAXIMUM_RANGES_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangeEmptyObjectVaList64() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(true, true, SENSORS_MAXIMUM_RANGES_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangeListedTypeWithoutRangeVarArg32() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(false, false, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetMaximumRangeListedTypeWithoutRangeVaList64() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(true, true, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetMaximumRangeNamesDoNotImplyVarArg32() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(false, false, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangeNamesDoNotImplyVaList64() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(true, true, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangeVendorsDoNotImplyVarArg32() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(false, false, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangeVendorsDoNotImplyVaList64() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(true, true, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangeVersionsDoNotImplyVarArg32() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(false, false, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangeVersionsDoNotImplyVaList64() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(true, true, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangeStringTypesDoNotImplyVarArg32() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(false, false, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangeStringTypesDoNotImplyVaList64() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(true, true, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangeUnknownTypeVarArg32() throws Exception {
        runSensorGetMaximumRangeUnknownType(false, false);
    }

    @Test
    public void testSensorGetMaximumRangeUnknownTypeVaList64() throws Exception {
        runSensorGetMaximumRangeUnknownType(true, true);
    }

    @Test
    public void testSensorGetMaximumRangeIsolationVarArg32() throws Exception {
        runSensorGetMaximumRangeIsolation(false, false);
    }

    @Test
    public void testSensorGetMaximumRangeIsolationVaList64() throws Exception {
        runSensorGetMaximumRangeIsolation(true, true);
    }

    @Test
    public void testSensorMaximumRangeMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorMaximumRangeMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorMaximumRangeMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorMaximumRangeMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetMaximumRangeParseRejects() {
        runSensorGetMaximumRangeParseRejects();
    }

    @Test
    public void testSensorGetMaximumRangeResolutionsDoNotImplyVarArg32() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(false, false, SENSORS_RESOLUTIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangeResolutionsDoNotImplyVaList64() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(true, true, SENSORS_RESOLUTIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionStaticTypeVarArg32() throws Exception {
        runSensorGetResolutionStaticType(false, false);
    }

    @Test
    public void testSensorGetResolutionStaticTypeVaList64() throws Exception {
        runSensorGetResolutionStaticType(true, true);
    }

    @Test
    public void testSensorGetResolutionDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetResolutionDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetResolutionDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetResolutionDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetResolutionOmittedVarArg32() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionOmittedVaList64() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionEmptyObjectVarArg32() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(false, false, SENSORS_RESOLUTIONS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionEmptyObjectVaList64() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(true, true, SENSORS_RESOLUTIONS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionListedTypeWithoutResolutionVarArg32() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(false, false, SENSORS_RESOLUTIONS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetResolutionListedTypeWithoutResolutionVaList64() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(true, true, SENSORS_RESOLUTIONS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetResolutionNamesDoNotImplyVarArg32() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(false, false, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionNamesDoNotImplyVaList64() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(true, true, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionVendorsDoNotImplyVarArg32() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(false, false, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionVendorsDoNotImplyVaList64() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(true, true, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionVersionsDoNotImplyVarArg32() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(false, false, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionVersionsDoNotImplyVaList64() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(true, true, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionStringTypesDoNotImplyVarArg32() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(false, false, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionStringTypesDoNotImplyVaList64() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(true, true, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionMaximumRangesDoNotImplyVarArg32() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(false, false, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionMaximumRangesDoNotImplyVaList64() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(true, true, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionUnknownTypeVarArg32() throws Exception {
        runSensorGetResolutionUnknownType(false, false);
    }

    @Test
    public void testSensorGetResolutionUnknownTypeVaList64() throws Exception {
        runSensorGetResolutionUnknownType(true, true);
    }

    @Test
    public void testSensorGetResolutionIsolationVarArg32() throws Exception {
        runSensorGetResolutionIsolation(false, false);
    }

    @Test
    public void testSensorGetResolutionIsolationVaList64() throws Exception {
        runSensorGetResolutionIsolation(true, true);
    }

    @Test
    public void testSensorResolutionMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorResolutionMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorResolutionMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorResolutionMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetResolutionParseRejects() {
        runSensorGetResolutionParseRejects();
    }

    @Test
    public void testSensorGetMaximumRangePowersDoNotImplyVarArg32() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(false, false, SENSORS_POWERS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaximumRangePowersDoNotImplyVaList64() throws Exception {
        runSensorGetMaximumRangeNotHandledOnLiveMarker(true, true, SENSORS_POWERS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionPowersDoNotImplyVarArg32() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(false, false, SENSORS_POWERS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetResolutionPowersDoNotImplyVaList64() throws Exception {
        runSensorGetResolutionNotHandledOnLiveMarker(true, true, SENSORS_POWERS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerStaticTypeVarArg32() throws Exception {
        runSensorGetPowerStaticType(false, false);
    }

    @Test
    public void testSensorGetPowerStaticTypeVaList64() throws Exception {
        runSensorGetPowerStaticType(true, true);
    }

    @Test
    public void testSensorGetPowerDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetPowerDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetPowerDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetPowerDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetPowerOmittedVarArg32() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetPowerOmittedVaList64() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetPowerEmptyObjectVarArg32() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(false, false, SENSORS_POWERS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetPowerEmptyObjectVaList64() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(true, true, SENSORS_POWERS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetPowerListedTypeWithoutPowerVarArg32() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(false, false, SENSORS_POWERS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetPowerListedTypeWithoutPowerVaList64() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(true, true, SENSORS_POWERS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetPowerNamesDoNotImplyVarArg32() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(false, false, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerNamesDoNotImplyVaList64() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(true, true, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerVendorsDoNotImplyVarArg32() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(false, false, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerVendorsDoNotImplyVaList64() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(true, true, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerVersionsDoNotImplyVarArg32() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(false, false, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerVersionsDoNotImplyVaList64() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(true, true, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerStringTypesDoNotImplyVarArg32() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(false, false, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerStringTypesDoNotImplyVaList64() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(true, true, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerMaximumRangesDoNotImplyVarArg32() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(false, false, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerMaximumRangesDoNotImplyVaList64() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(true, true, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerResolutionsDoNotImplyVarArg32() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(false, false, SENSORS_RESOLUTIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerResolutionsDoNotImplyVaList64() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(true, true, SENSORS_RESOLUTIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerUnknownTypeVarArg32() throws Exception {
        runSensorGetPowerUnknownType(false, false);
    }

    @Test
    public void testSensorGetPowerUnknownTypeVaList64() throws Exception {
        runSensorGetPowerUnknownType(true, true);
    }

    @Test
    public void testSensorGetPowerIsolationVarArg32() throws Exception {
        runSensorGetPowerIsolation(false, false);
    }

    @Test
    public void testSensorGetPowerIsolationVaList64() throws Exception {
        runSensorGetPowerIsolation(true, true);
    }

    @Test
    public void testSensorPowerMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorPowerMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorPowerMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorPowerMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetPowerParseRejects() {
        runSensorGetPowerParseRejects();
    }

    @Test
    public void testSensorGetMinDelayStaticTypeVarArg32() throws Exception {
        runSensorGetMinDelayStaticType(false, false);
    }

    @Test
    public void testSensorGetMinDelayStaticTypeVaList64() throws Exception {
        runSensorGetMinDelayStaticType(true, true);
    }

    @Test
    public void testSensorGetMinDelayDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetMinDelayDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetMinDelayDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetMinDelayDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetMinDelayGeneralValueVarArg32() throws Exception {
        runSensorGetMinDelayGeneralValue(false, false);
    }

    @Test
    public void testSensorGetMinDelayGeneralValueVaList64() throws Exception {
        runSensorGetMinDelayGeneralValue(true, true);
    }

    @Test
    public void testSensorGetMinDelayOmittedVarArg32() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayOmittedVaList64() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayEmptyObjectVarArg32() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(false, false, SENSORS_MIN_DELAYS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayEmptyObjectVaList64() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(true, true, SENSORS_MIN_DELAYS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayListedTypeWithoutMinDelayVarArg32() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(false, false, SENSORS_MIN_DELAYS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetMinDelayListedTypeWithoutMinDelayVaList64() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(true, true, SENSORS_MIN_DELAYS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetMinDelayNamesDoNotImplyVarArg32() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(false, false, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayNamesDoNotImplyVaList64() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(true, true, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayVendorsDoNotImplyVarArg32() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(false, false, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayVendorsDoNotImplyVaList64() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(true, true, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayVersionsDoNotImplyVarArg32() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(false, false, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayVersionsDoNotImplyVaList64() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(true, true, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayStringTypesDoNotImplyVarArg32() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(false, false, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayStringTypesDoNotImplyVaList64() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(true, true, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayMaximumRangesDoNotImplyVarArg32() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(false, false, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayMaximumRangesDoNotImplyVaList64() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(true, true, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayResolutionsDoNotImplyVarArg32() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(false, false, SENSORS_RESOLUTIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayResolutionsDoNotImplyVaList64() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(true, true, SENSORS_RESOLUTIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayPowersDoNotImplyVarArg32() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(false, false, SENSORS_POWERS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayPowersDoNotImplyVaList64() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(true, true, SENSORS_POWERS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayUnknownTypeVarArg32() throws Exception {
        runSensorGetMinDelayUnknownType(false, false);
    }

    @Test
    public void testSensorGetMinDelayUnknownTypeVaList64() throws Exception {
        runSensorGetMinDelayUnknownType(true, true);
    }

    @Test
    public void testSensorGetMinDelayIsolationVarArg32() throws Exception {
        runSensorGetMinDelayIsolation(false, false);
    }

    @Test
    public void testSensorGetMinDelayIsolationVaList64() throws Exception {
        runSensorGetMinDelayIsolation(true, true);
    }

    @Test
    public void testSensorMinDelayMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorMinDelayMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorMinDelayMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorMinDelayMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetMinDelayParseRejects() {
        runSensorGetMinDelayParseRejects();
    }

    @Test
    public void testSensorGetVersionMinDelaysDoNotImplyVarArg32() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(false, false, SENSORS_MIN_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetVersionMinDelaysDoNotImplyVaList64() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(true, true, SENSORS_MIN_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerMinDelaysDoNotImplyVarArg32() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(false, false, SENSORS_MIN_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerMinDelaysDoNotImplyVaList64() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(true, true, SENSORS_MIN_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayStaticTypeVarArg32() throws Exception {
        runSensorGetMaxDelayStaticType(false, false);
    }

    @Test
    public void testSensorGetMaxDelayStaticTypeVaList64() throws Exception {
        runSensorGetMaxDelayStaticType(true, true);
    }

    @Test
    public void testSensorGetMaxDelayDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetMaxDelayDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetMaxDelayDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetMaxDelayDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetMaxDelayGeneralValueVarArg32() throws Exception {
        runSensorGetMaxDelayGeneralValue(false, false);
    }

    @Test
    public void testSensorGetMaxDelayGeneralValueVaList64() throws Exception {
        runSensorGetMaxDelayGeneralValue(true, true);
    }

    @Test
    public void testSensorGetMaxDelayOmittedVarArg32() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayOmittedVaList64() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayEmptyObjectVarArg32() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(false, false, SENSORS_MAX_DELAYS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayEmptyObjectVaList64() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(true, true, SENSORS_MAX_DELAYS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayListedTypeWithoutMaxDelayVarArg32() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(false, false, SENSORS_MAX_DELAYS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetMaxDelayListedTypeWithoutMaxDelayVaList64() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(true, true, SENSORS_MAX_DELAYS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetMaxDelayNamesDoNotImplyVarArg32() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(false, false, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayNamesDoNotImplyVaList64() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(true, true, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayVendorsDoNotImplyVarArg32() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(false, false, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayVendorsDoNotImplyVaList64() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(true, true, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayVersionsDoNotImplyVarArg32() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(false, false, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayVersionsDoNotImplyVaList64() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(true, true, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayStringTypesDoNotImplyVarArg32() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(false, false, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayStringTypesDoNotImplyVaList64() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(true, true, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayMaximumRangesDoNotImplyVarArg32() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(false, false, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayMaximumRangesDoNotImplyVaList64() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(true, true, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayResolutionsDoNotImplyVarArg32() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(false, false, SENSORS_RESOLUTIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayResolutionsDoNotImplyVaList64() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(true, true, SENSORS_RESOLUTIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayPowersDoNotImplyVarArg32() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(false, false, SENSORS_POWERS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayPowersDoNotImplyVaList64() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(true, true, SENSORS_POWERS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayMinDelaysDoNotImplyVarArg32() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(false, false, SENSORS_MIN_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayMinDelaysDoNotImplyVaList64() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(true, true, SENSORS_MIN_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayMaxDelaysDoNotImplyVarArg32() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(false, false, SENSORS_MAX_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayMaxDelaysDoNotImplyVaList64() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(true, true, SENSORS_MAX_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayIndependentOfMinVarArg32() throws Exception {
        runSensorGetMaxDelayIndependentOfMin(false, false);
    }

    @Test
    public void testSensorGetMaxDelayIndependentOfMinVaList64() throws Exception {
        runSensorGetMaxDelayIndependentOfMin(true, true);
    }

    @Test
    public void testSensorGetMaxDelayUnknownTypeVarArg32() throws Exception {
        runSensorGetMaxDelayUnknownType(false, false);
    }

    @Test
    public void testSensorGetMaxDelayUnknownTypeVaList64() throws Exception {
        runSensorGetMaxDelayUnknownType(true, true);
    }

    @Test
    public void testSensorGetMaxDelayIsolationVarArg32() throws Exception {
        runSensorGetMaxDelayIsolation(false, false);
    }

    @Test
    public void testSensorGetMaxDelayIsolationVaList64() throws Exception {
        runSensorGetMaxDelayIsolation(true, true);
    }

    @Test
    public void testSensorMaxDelayMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorMaxDelayMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorMaxDelayMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorMaxDelayMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetMaxDelayParseRejects() {
        runSensorGetMaxDelayParseRejects();
    }

    @Test
    public void testSensorGetFifoReservedEventCountStaticTypeVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountStaticType(false, false);
    }

    @Test
    public void testSensorGetFifoReservedEventCountStaticTypeVaList64() throws Exception {
        runSensorGetFifoReservedEventCountStaticType(true, true);
    }

    @Test
    public void testSensorGetFifoReservedEventCountDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetFifoReservedEventCountDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetFifoReservedEventCountDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetFifoReservedEventCountGeneralValueVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountGeneralValue(false, false);
    }

    @Test
    public void testSensorGetFifoReservedEventCountGeneralValueVaList64() throws Exception {
        runSensorGetFifoReservedEventCountGeneralValue(true, true);
    }

    @Test
    public void testSensorGetFifoReservedEventCountOmittedVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountOmittedVaList64() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountEmptyObjectVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_FIFO_RESERVED_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountEmptyObjectVaList64() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_FIFO_RESERVED_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountListedTypeWithoutCountVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_FIFO_RESERVED_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetFifoReservedEventCountListedTypeWithoutCountVaList64() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_FIFO_RESERVED_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetFifoReservedEventCountNamesDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountNamesDoNotImplyVaList64() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountVendorsDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountVendorsDoNotImplyVaList64() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountVersionsDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountVersionsDoNotImplyVaList64() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountStringTypesDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountStringTypesDoNotImplyVaList64() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountMaximumRangesDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountMaximumRangesDoNotImplyVaList64() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountResolutionsDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_RESOLUTIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountResolutionsDoNotImplyVaList64() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_RESOLUTIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountPowersDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_POWERS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountPowersDoNotImplyVaList64() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_POWERS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountMinDelaysDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_MIN_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountMinDelaysDoNotImplyVaList64() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_MIN_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountMaxDelaysDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_MAX_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountMaxDelaysDoNotImplyVaList64() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_MAX_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayFifoReservedDoNotImplyVarArg32() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(false, false, SENSORS_FIFO_RESERVED_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayFifoReservedDoNotImplyVaList64() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(true, true, SENSORS_FIFO_RESERVED_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayFifoReservedDoNotImplyVarArg32() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(false, false, SENSORS_FIFO_RESERVED_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayFifoReservedDoNotImplyVaList64() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(true, true, SENSORS_FIFO_RESERVED_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountUnknownTypeVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountUnknownType(false, false);
    }

    @Test
    public void testSensorGetFifoReservedEventCountUnknownTypeVaList64() throws Exception {
        runSensorGetFifoReservedEventCountUnknownType(true, true);
    }

    @Test
    public void testSensorGetFifoReservedEventCountIsolationVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountIsolation(false, false);
    }

    @Test
    public void testSensorGetFifoReservedEventCountIsolationVaList64() throws Exception {
        runSensorGetFifoReservedEventCountIsolation(true, true);
    }

    @Test
    public void testSensorFifoReservedMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorFifoReservedMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorFifoReservedMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorFifoReservedMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetFifoReservedEventCountParseRejects() {
        runSensorGetFifoReservedEventCountParseRejects();
    }

    @Test
    public void testSensorGetFifoMaxEventCountStaticTypeVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountStaticType(false, false);
    }

    @Test
    public void testSensorGetFifoMaxEventCountStaticTypeVaList64() throws Exception {
        runSensorGetFifoMaxEventCountStaticType(true, true);
    }

    @Test
    public void testSensorGetFifoMaxEventCountDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetFifoMaxEventCountDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetFifoMaxEventCountDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetFifoMaxEventCountGeneralValueVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountGeneralValue(false, false);
    }

    @Test
    public void testSensorGetFifoMaxEventCountGeneralValueVaList64() throws Exception {
        runSensorGetFifoMaxEventCountGeneralValue(true, true);
    }

    @Test
    public void testSensorGetFifoMaxEventCountOmittedVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountOmittedVaList64() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountEmptyObjectVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_FIFO_MAX_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountEmptyObjectVaList64() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_FIFO_MAX_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountListedTypeWithoutCountVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_FIFO_MAX_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetFifoMaxEventCountListedTypeWithoutCountVaList64() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_FIFO_MAX_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetFifoMaxEventCountNamesDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountNamesDoNotImplyVaList64() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_NAMES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountVendorsDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountVendorsDoNotImplyVaList64() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_VENDORS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountVersionsDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountVersionsDoNotImplyVaList64() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_VERSIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountStringTypesDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountStringTypesDoNotImplyVaList64() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_STRING_TYPES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountMaximumRangesDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountMaximumRangesDoNotImplyVaList64() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_MAXIMUM_RANGES_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountResolutionsDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_RESOLUTIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountResolutionsDoNotImplyVaList64() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_RESOLUTIONS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountPowersDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_POWERS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountPowersDoNotImplyVaList64() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_POWERS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountMinDelaysDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_MIN_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountMinDelaysDoNotImplyVaList64() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_MIN_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountMaxDelaysDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_MAX_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountMaxDelaysDoNotImplyVaList64() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_MAX_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountFifoReservedDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_FIFO_RESERVED_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountFifoReservedDoNotImplyVaList64() throws Exception {
        runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_FIFO_RESERVED_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountFifoMaxDoNotImplyVarArg32() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                false, false, SENSORS_FIFO_MAX_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoReservedEventCountFifoMaxDoNotImplyVaList64() throws Exception {
        runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
                true, true, SENSORS_FIFO_MAX_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayFifoMaxDoNotImplyVarArg32() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(false, false, SENSORS_FIFO_MAX_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMaxDelayFifoMaxDoNotImplyVaList64() throws Exception {
        runSensorGetMaxDelayNotHandledOnLiveMarker(true, true, SENSORS_FIFO_MAX_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayFifoMaxDoNotImplyVarArg32() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(false, false, SENSORS_FIFO_MAX_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetMinDelayFifoMaxDoNotImplyVaList64() throws Exception {
        runSensorGetMinDelayNotHandledOnLiveMarker(true, true, SENSORS_FIFO_MAX_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetFifoMaxEventCountUnknownTypeVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountUnknownType(false, false);
    }

    @Test
    public void testSensorGetFifoMaxEventCountUnknownTypeVaList64() throws Exception {
        runSensorGetFifoMaxEventCountUnknownType(true, true);
    }

    @Test
    public void testSensorGetFifoMaxEventCountIsolationVarArg32() throws Exception {
        runSensorGetFifoMaxEventCountIsolation(false, false);
    }

    @Test
    public void testSensorGetFifoMaxEventCountIsolationVaList64() throws Exception {
        runSensorGetFifoMaxEventCountIsolation(true, true);
    }

    @Test
    public void testSensorFifoMaxMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorFifoMaxMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorFifoMaxMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorFifoMaxMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetFifoMaxEventCountParseRejects() {
        runSensorGetFifoMaxEventCountParseRejects();
    }

    @Test
    public void testSensorFifoReservedAndMaxIndependentSizeVarArg32() throws Exception {
        runSensorFifoReservedAndMaxIndependentSize(false, false);
    }

    @Test
    public void testSensorFifoReservedAndMaxIndependentSizeVaList64() throws Exception {
        runSensorFifoReservedAndMaxIndependentSize(true, true);
    }

    @Test
    public void testSensorIsWakeUpSensorStaticTypeVarArg32() throws Exception {
        runSensorIsWakeUpSensorStaticType(false, false);
    }

    @Test
    public void testSensorIsWakeUpSensorStaticTypeVaList64() throws Exception {
        runSensorIsWakeUpSensorStaticType(true, true);
    }

    @Test
    public void testSensorIsWakeUpSensorDynamicOnlyTypeVarArg32() throws Exception {
        runSensorIsWakeUpSensorDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorIsWakeUpSensorDynamicOnlyTypeVaList64() throws Exception {
        runSensorIsWakeUpSensorDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorIsWakeUpSensorOmittedVarArg32() throws Exception {
        runSensorIsWakeUpSensorNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorIsWakeUpSensorOmittedVaList64() throws Exception {
        runSensorIsWakeUpSensorNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorIsWakeUpSensorEmptyObjectVarArg32() throws Exception {
        runSensorIsWakeUpSensorNotHandledOnLiveMarker(false, false, SENSORS_WAKE_UP_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorIsWakeUpSensorEmptyObjectVaList64() throws Exception {
        runSensorIsWakeUpSensorNotHandledOnLiveMarker(true, true, SENSORS_WAKE_UP_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorIsWakeUpSensorListedTypeWithoutFlagVarArg32() throws Exception {
        runSensorIsWakeUpSensorNotHandledOnLiveMarker(false, false, SENSORS_WAKE_UP_STATIC_JSON, 4);
    }

    @Test
    public void testSensorIsWakeUpSensorListedTypeWithoutFlagVaList64() throws Exception {
        runSensorIsWakeUpSensorNotHandledOnLiveMarker(true, true, SENSORS_WAKE_UP_STATIC_JSON, 4);
    }

    @Test
    public void testSensorIsWakeUpSensorFifoDoesNotImplyVarArg32() throws Exception {
        runSensorIsWakeUpSensorNotHandledOnLiveMarker(
                false, false, SENSORS_FIFO_RESERVED_STATIC_JSON, 1);
    }

    @Test
    public void testSensorIsWakeUpSensorFifoDoesNotImplyVaList64() throws Exception {
        runSensorIsWakeUpSensorNotHandledOnLiveMarker(
                true, true, SENSORS_FIFO_RESERVED_STATIC_JSON, 1);
    }

    @Test
    public void testSensorIsWakeUpSensorIsolationVarArg32() throws Exception {
        runSensorIsWakeUpSensorIsolation(false, false);
    }

    @Test
    public void testSensorIsWakeUpSensorIsolationVaList64() throws Exception {
        runSensorIsWakeUpSensorIsolation(true, true);
    }

    @Test
    public void testSensorWakeUpMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorWakeUpMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorWakeUpMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorWakeUpMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorIsWakeUpSensorParseRejects() {
        runSensorIsWakeUpSensorParseRejects();
    }

    @Test
    public void testSensorGetIdStaticTypeVarArg32() throws Exception {
        runSensorGetIdStaticType(false, false);
    }

    @Test
    public void testSensorGetIdStaticTypeVaList64() throws Exception {
        runSensorGetIdStaticType(true, true);
    }

    @Test
    public void testSensorGetIdDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetIdDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetIdDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetIdDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetIdOmittedVarArg32() throws Exception {
        runSensorGetIdNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetIdOmittedVaList64() throws Exception {
        runSensorGetIdNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetIdEmptyObjectVarArg32() throws Exception {
        runSensorGetIdNotHandledOnLiveMarker(false, false, SENSORS_IDS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetIdEmptyObjectVaList64() throws Exception {
        runSensorGetIdNotHandledOnLiveMarker(true, true, SENSORS_IDS_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetIdListedTypeWithoutIdVarArg32() throws Exception {
        runSensorGetIdNotHandledOnLiveMarker(false, false, SENSORS_IDS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetIdListedTypeWithoutIdVaList64() throws Exception {
        runSensorGetIdNotHandledOnLiveMarker(true, true, SENSORS_IDS_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetIdWakeUpDoesNotImplyVarArg32() throws Exception {
        runSensorGetIdNotHandledOnLiveMarker(false, false, SENSORS_WAKE_UP_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetIdWakeUpDoesNotImplyVaList64() throws Exception {
        runSensorGetIdNotHandledOnLiveMarker(true, true, SENSORS_WAKE_UP_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetIdIsolationVarArg32() throws Exception {
        runSensorGetIdIsolation(false, false);
    }

    @Test
    public void testSensorGetIdIsolationVaList64() throws Exception {
        runSensorGetIdIsolation(true, true);
    }

    @Test
    public void testSensorGetIdMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorGetIdMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorGetIdMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorGetIdMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetIdParseRejects() {
        runSensorGetIdParseRejects();
    }

    @Test
    public void testSensorGetReportingModeStaticTypeVarArg32() throws Exception {
        runSensorGetReportingModeStaticType(false, false);
    }

    @Test
    public void testSensorGetReportingModeStaticTypeVaList64() throws Exception {
        runSensorGetReportingModeStaticType(true, true);
    }

    @Test
    public void testSensorGetReportingModeDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetReportingModeDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetReportingModeDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetReportingModeDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetReportingModeOmittedVarArg32() throws Exception {
        runSensorGetReportingModeNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetReportingModeOmittedVaList64() throws Exception {
        runSensorGetReportingModeNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetReportingModeEmptyObjectVarArg32() throws Exception {
        runSensorGetReportingModeNotHandledOnLiveMarker(false, false, SENSORS_REPORTING_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetReportingModeEmptyObjectVaList64() throws Exception {
        runSensorGetReportingModeNotHandledOnLiveMarker(true, true, SENSORS_REPORTING_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetReportingModeListedTypeWithoutModeVarArg32() throws Exception {
        runSensorGetReportingModeNotHandledOnLiveMarker(false, false, SENSORS_REPORTING_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetReportingModeListedTypeWithoutModeVaList64() throws Exception {
        runSensorGetReportingModeNotHandledOnLiveMarker(true, true, SENSORS_REPORTING_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetReportingModeIsolationVarArg32() throws Exception {
        runSensorGetReportingModeIsolation(false, false);
    }

    @Test
    public void testSensorGetReportingModeIsolationVaList64() throws Exception {
        runSensorGetReportingModeIsolation(true, true);
    }

    @Test
    public void testSensorGetReportingModeMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorGetReportingModeMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorGetReportingModeMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorGetReportingModeMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetReportingModeParseRejects() {
        runSensorGetReportingModeParseRejects();
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelStaticTypeVarArg32() throws Exception {
        runSensorGetHighestDirectReportRateLevelStaticType(false, false);
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelStaticTypeVaList64() throws Exception {
        runSensorGetHighestDirectReportRateLevelStaticType(true, true);
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetHighestDirectReportRateLevelDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetHighestDirectReportRateLevelDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelOmittedVarArg32() throws Exception {
        runSensorGetHighestDirectReportRateLevelNotHandled(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelOmittedVaList64() throws Exception {
        runSensorGetHighestDirectReportRateLevelNotHandled(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelEmptyObjectVarArg32() throws Exception {
        runSensorGetHighestDirectReportRateLevelNotHandled(
                false, false, SENSORS_DIRECT_RATE_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelEmptyObjectVaList64() throws Exception {
        runSensorGetHighestDirectReportRateLevelNotHandled(
                true, true, SENSORS_DIRECT_RATE_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelListedTypeWithoutRateVarArg32()
            throws Exception {
        runSensorGetHighestDirectReportRateLevelNotHandled(
                false, false, SENSORS_DIRECT_RATE_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelListedTypeWithoutRateVaList64()
            throws Exception {
        runSensorGetHighestDirectReportRateLevelNotHandled(
                true, true, SENSORS_DIRECT_RATE_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelIsolationVarArg32() throws Exception {
        runSensorIntIsolation(false, false, SENSORS_DIRECT_RATE_STATIC_JSON,
                "getHighestDirectReportRateLevel", "Sensor.getHighestDirectReportRateLevel");
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelIsolationVaList64() throws Exception {
        runSensorIntIsolation(true, true, SENSORS_DIRECT_RATE_STATIC_JSON,
                "getHighestDirectReportRateLevel", "Sensor.getHighestDirectReportRateLevel");
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelMarkerConfigInstanceStaleVarArg32()
            throws Exception {
        runSensorIntMarkerConfigInstanceStale(false, false, SENSORS_DIRECT_RATE_A_JSON,
                SENSORS_DIRECT_RATE_B_JSON, "getHighestDirectReportRateLevel",
                "Sensor.getHighestDirectReportRateLevel", 1, 2);
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelMarkerConfigInstanceStaleVaList64()
            throws Exception {
        runSensorIntMarkerConfigInstanceStale(true, true, SENSORS_DIRECT_RATE_A_JSON,
                SENSORS_DIRECT_RATE_B_JSON, "getHighestDirectReportRateLevel",
                "Sensor.getHighestDirectReportRateLevel", 1, 2);
    }

    @Test
    public void testSensorGetHighestDirectReportRateLevelParseRejects() {
        runSensorGetHighestDirectReportRateLevelParseRejects();
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedStaticTypeVarArg32() throws Exception {
        runSensorIsDirectChannelTypeSupportedStaticType(false, false);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedStaticTypeVaList64() throws Exception {
        runSensorIsDirectChannelTypeSupportedStaticType(true, true);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedDynamicOnlyTypeVarArg32() throws Exception {
        runSensorIsDirectChannelTypeSupportedDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedDynamicOnlyTypeVaList64() throws Exception {
        runSensorIsDirectChannelTypeSupportedDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedEmptyArrayVarArg32() throws Exception {
        runSensorIsDirectChannelTypeSupportedEmptyArray(false, false);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedEmptyArrayVaList64() throws Exception {
        runSensorIsDirectChannelTypeSupportedEmptyArray(true, true);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedOmittedVarArg32() throws Exception {
        runSensorIsDirectChannelTypeSupportedNotHandled(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedOmittedVaList64() throws Exception {
        runSensorIsDirectChannelTypeSupportedNotHandled(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedEmptyObjectVarArg32() throws Exception {
        runSensorIsDirectChannelTypeSupportedNotHandled(
                false, false, SENSORS_DIRECT_CHANNEL_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedEmptyObjectVaList64() throws Exception {
        runSensorIsDirectChannelTypeSupportedNotHandled(
                true, true, SENSORS_DIRECT_CHANNEL_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedListedTypeWithoutChannelsVarArg32()
            throws Exception {
        runSensorIsDirectChannelTypeSupportedNotHandled(
                false, false, SENSORS_DIRECT_CHANNEL_STATIC_JSON, 4);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedListedTypeWithoutChannelsVaList64()
            throws Exception {
        runSensorIsDirectChannelTypeSupportedNotHandled(
                true, true, SENSORS_DIRECT_CHANNEL_STATIC_JSON, 4);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedRateDoesNotImplyVarArg32() throws Exception {
        runSensorIsDirectChannelTypeSupportedNotHandled(
                false, false, SENSORS_DIRECT_RATE_STATIC_JSON, 1);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedRateDoesNotImplyVaList64() throws Exception {
        runSensorIsDirectChannelTypeSupportedNotHandled(
                true, true, SENSORS_DIRECT_RATE_STATIC_JSON, 1);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedIsolationVarArg32() throws Exception {
        runSensorIsDirectChannelTypeSupportedIsolation(false, false);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedIsolationVaList64() throws Exception {
        runSensorIsDirectChannelTypeSupportedIsolation(true, true);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedMarkerConfigInstanceStaleVarArg32()
            throws Exception {
        runSensorIsDirectChannelTypeSupportedMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedMarkerConfigInstanceStaleVaList64()
            throws Exception {
        runSensorIsDirectChannelTypeSupportedMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorIsDirectChannelTypeSupportedParseRejects() {
        runSensorIsDirectChannelTypeSupportedParseRejects();
    }

    @Test
    public void testSensorIsDynamicSensorStaticTypeVarArg32() throws Exception {
        runSensorIsDynamicSensorStaticType(false, false);
    }

    @Test
    public void testSensorIsDynamicSensorStaticTypeVaList64() throws Exception {
        runSensorIsDynamicSensorStaticType(true, true);
    }

    @Test
    public void testSensorIsDynamicSensorDynamicOnlyTypeVarArg32() throws Exception {
        runSensorIsDynamicSensorDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorIsDynamicSensorDynamicOnlyTypeVaList64() throws Exception {
        runSensorIsDynamicSensorDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorIsDynamicSensorNotInferredFromDynamicTypesVarArg32() throws Exception {
        runSensorIsDynamicSensorNotHandledOnLiveDynamicType(
                false, false, SENSORS_DYNAMIC_TYPES_WITHOUT_FLAG_JSON, 7);
    }

    @Test
    public void testSensorIsDynamicSensorNotInferredFromDynamicTypesVaList64() throws Exception {
        runSensorIsDynamicSensorNotHandledOnLiveDynamicType(
                true, true, SENSORS_DYNAMIC_TYPES_WITHOUT_FLAG_JSON, 7);
    }

    @Test
    public void testSensorIsDynamicSensorOmittedVarArg32() throws Exception {
        runSensorIsDynamicSensorNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorIsDynamicSensorOmittedVaList64() throws Exception {
        runSensorIsDynamicSensorNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorIsDynamicSensorEmptyObjectVarArg32() throws Exception {
        runSensorIsDynamicSensorNotHandledOnLiveMarker(false, false, SENSORS_DYNAMIC_FLAG_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorIsDynamicSensorEmptyObjectVaList64() throws Exception {
        runSensorIsDynamicSensorNotHandledOnLiveMarker(true, true, SENSORS_DYNAMIC_FLAG_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorIsDynamicSensorListedTypeWithoutFlagVarArg32() throws Exception {
        runSensorIsDynamicSensorNotHandledOnLiveMarker(false, false, SENSORS_DYNAMIC_FLAG_STATIC_JSON, 4);
    }

    @Test
    public void testSensorIsDynamicSensorListedTypeWithoutFlagVaList64() throws Exception {
        runSensorIsDynamicSensorNotHandledOnLiveMarker(true, true, SENSORS_DYNAMIC_FLAG_STATIC_JSON, 4);
    }

    @Test
    public void testSensorIsDynamicSensorIsolationVarArg32() throws Exception {
        runSensorIsDynamicSensorIsolation(false, false);
    }

    @Test
    public void testSensorIsDynamicSensorIsolationVaList64() throws Exception {
        runSensorIsDynamicSensorIsolation(true, true);
    }

    @Test
    public void testSensorIsDynamicSensorMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorIsDynamicSensorMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorIsDynamicSensorMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorIsDynamicSensorMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorIsDynamicSensorParseRejects() {
        runSensorIsDynamicSensorParseRejects();
    }

    @Test
    public void testSensorGetRequiredPermissionStaticTypeVarArg32() throws Exception {
        runSensorGetRequiredPermissionStaticType(false, false);
    }

    @Test
    public void testSensorGetRequiredPermissionStaticTypeVaList64() throws Exception {
        runSensorGetRequiredPermissionStaticType(true, true);
    }

    @Test
    public void testSensorGetRequiredPermissionDynamicOnlyTypeVarArg32() throws Exception {
        runSensorGetRequiredPermissionDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorGetRequiredPermissionDynamicOnlyTypeVaList64() throws Exception {
        runSensorGetRequiredPermissionDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorGetRequiredPermissionOmittedVarArg32() throws Exception {
        runSensorGetRequiredPermissionNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetRequiredPermissionOmittedVaList64() throws Exception {
        runSensorGetRequiredPermissionNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorGetRequiredPermissionEmptyObjectVarArg32() throws Exception {
        runSensorGetRequiredPermissionNotHandledOnLiveMarker(false, false, SENSORS_PERM_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetRequiredPermissionEmptyObjectVaList64() throws Exception {
        runSensorGetRequiredPermissionNotHandledOnLiveMarker(true, true, SENSORS_PERM_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorGetRequiredPermissionListedTypeWithoutPermVarArg32() throws Exception {
        runSensorGetRequiredPermissionNotHandledOnLiveMarker(false, false, SENSORS_PERM_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetRequiredPermissionListedTypeWithoutPermVaList64() throws Exception {
        runSensorGetRequiredPermissionNotHandledOnLiveMarker(true, true, SENSORS_PERM_STATIC_JSON, 4);
    }

    @Test
    public void testSensorGetRequiredPermissionIsolationVarArg32() throws Exception {
        runSensorGetRequiredPermissionIsolation(false, false);
    }

    @Test
    public void testSensorGetRequiredPermissionIsolationVaList64() throws Exception {
        runSensorGetRequiredPermissionIsolation(true, true);
    }

    @Test
    public void testSensorGetRequiredPermissionMarkerConfigInstanceStaleVarArg32() throws Exception {
        runSensorGetRequiredPermissionMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorGetRequiredPermissionMarkerConfigInstanceStaleVaList64() throws Exception {
        runSensorGetRequiredPermissionMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorGetRequiredPermissionParseRejects() {
        runSensorGetRequiredPermissionParseRejects();
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedStaticTypeVarArg32() throws Exception {
        runSensorIsAdditionalInfoSupportedStaticType(false, false);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedStaticTypeVaList64() throws Exception {
        runSensorIsAdditionalInfoSupportedStaticType(true, true);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedDynamicOnlyTypeVarArg32() throws Exception {
        runSensorIsAdditionalInfoSupportedDynamicOnlyType(false, false);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedDynamicOnlyTypeVaList64() throws Exception {
        runSensorIsAdditionalInfoSupportedDynamicOnlyType(true, true);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedOmittedVarArg32() throws Exception {
        runSensorIsAdditionalInfoSupportedNotHandledOnLiveMarker(false, false, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedOmittedVaList64() throws Exception {
        runSensorIsAdditionalInfoSupportedNotHandledOnLiveMarker(true, true, SENSORS_TYPES_JSON, 1);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedEmptyObjectVarArg32() throws Exception {
        runSensorIsAdditionalInfoSupportedNotHandledOnLiveMarker(
                false, false, SENSORS_ADDITIONAL_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedEmptyObjectVaList64() throws Exception {
        runSensorIsAdditionalInfoSupportedNotHandledOnLiveMarker(
                true, true, SENSORS_ADDITIONAL_EMPTY_JSON, 1);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedListedTypeWithoutFlagVarArg32() throws Exception {
        runSensorIsAdditionalInfoSupportedNotHandledOnLiveMarker(
                false, false, SENSORS_ADDITIONAL_STATIC_JSON, 4);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedListedTypeWithoutFlagVaList64() throws Exception {
        runSensorIsAdditionalInfoSupportedNotHandledOnLiveMarker(
                true, true, SENSORS_ADDITIONAL_STATIC_JSON, 4);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedIsolationVarArg32() throws Exception {
        runSensorIsAdditionalInfoSupportedIsolation(false, false);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedIsolationVaList64() throws Exception {
        runSensorIsAdditionalInfoSupportedIsolation(true, true);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedMarkerConfigInstanceStaleVarArg32()
            throws Exception {
        runSensorIsAdditionalInfoSupportedMarkerConfigInstanceStale(false, false);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedMarkerConfigInstanceStaleVaList64()
            throws Exception {
        runSensorIsAdditionalInfoSupportedMarkerConfigInstanceStale(true, true);
    }

    @Test
    public void testSensorIsAdditionalInfoSupportedParseRejects() {
        runSensorIsAdditionalInfoSupportedParseRejects();
    }

    @Test
    public void testSensorGetVersionMaxDelaysDoNotImplyVarArg32() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(false, false, SENSORS_MAX_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetVersionMaxDelaysDoNotImplyVaList64() throws Exception {
        runSensorGetVersionNotHandledOnLiveMarker(true, true, SENSORS_MAX_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerMaxDelaysDoNotImplyVarArg32() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(false, false, SENSORS_MAX_DELAYS_STATIC_JSON, 1);
    }

    @Test
    public void testSensorGetPowerMaxDelaysDoNotImplyVaList64() throws Exception {
        runSensorGetPowerNotHandledOnLiveMarker(true, true, SENSORS_MAX_DELAYS_STATIC_JSON, 1);
    }

    private static void runGetDefaultSensorFound(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(SENSOR_CLASS, sensor.getObjectType().getClassName());
            assertNotNull(sensor.getValue());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            CapturedEvent getDefault = findLastEvent(sink.events, "android_sensor",
                    "SensorManager.getDefaultSensor");
            assertNotNull(getDefault);
            assertEquals("json-config", getDefault.source);
            assertEquals("sensorType=1,result=1", String.valueOf(getDefault.value));
            assertNotNull(getDefault.note);
            assertFalse(getDefault.note.isEmpty());

            CapturedEvent getType = findLastEvent(sink.events, "android_sensor", "Sensor.getType");
            assertNotNull(getType);
            assertEquals("json-config", getType.source);
            assertEquals("sensorType=1", String.valueOf(getType.value));
            assertNotNull(getType.note);
            assertFalse(getType.note.isEmpty());

            DvmObject<?> sensor4 = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 4);
            assertNotNull(sensor4);
            assertEquals(4, invokeGetType(jni, baseVM, useVaList, sensor4));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDefaultSensorNull(boolean is64Bit, boolean useVaList,
                                                String json, int sensorType) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNull(sensor);

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "SensorManager.getDefaultSensor");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=" + sensorType + ",result=null", String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_sensor",
                    "SensorManager.getDefaultSensor"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentGetDefaultSensor(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            try {
                invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
                fail("expected UnsupportedOperationException without sensors config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDefaultSensor"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_sensor event when config absent: " + e.api,
                        "android_sensor".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDefaultSensorByWakeUpMatch(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_WAKE_UP_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> byInt = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(byInt);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, byInt));
            DvmObject<?> sensor = invokeGetDefaultSensorByWakeUp(
                    jni, baseVM, useVaList, manager, 1, false);
            assertNotNull(sensor);
            assertEquals(SENSOR_CLASS, sensor.getObjectType().getClassName());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));
            assertFalse(invokeIsWakeUpSensor(jni, baseVM, useVaList, sensor));
            DvmClass dvmClass = vm.resolveClass(SENSOR_MANAGER_CLASS);
            DvmMethod method = new DvmMethod(dvmClass, "getDefaultSensor",
                    "(IZ)Landroid/hardware/Sensor;", false);
            DvmObject<?> byMethod = useVaList
                    ? jni.callObjectMethodV(baseVM, manager, method,
                            new TestIntBooleanVaList(baseVM, method, 1, false))
                    : jni.callObjectMethod(baseVM, manager, method,
                            new TestIntBooleanVarArg(baseVM, method, 1, false));
            assertNotNull(byMethod);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, byMethod));
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "SensorManager.getDefaultSensor");
            assertEquals("sensorType=1,wakeUp=false,result=1", String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDefaultSensorByWakeUpMismatchNull(boolean is64Bit, boolean useVaList)
            throws Exception {
        runGetDefaultSensorByWakeUpNull(is64Bit, useVaList, SENSORS_WAKE_UP_STATIC_JSON, 1, true);
    }

    private static void runGetDefaultSensorByWakeUpUnlistedNull(boolean is64Bit, boolean useVaList)
            throws Exception {
        runGetDefaultSensorByWakeUpNull(is64Bit, useVaList, SENSORS_WAKE_UP_STATIC_JSON, 2, false);
    }

    private static void runGetDefaultSensorByWakeUpNull(boolean is64Bit, boolean useVaList,
                                                        String json, int sensorType, boolean wakeUp)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensorByWakeUp(
                    jni, baseVM, useVaList, manager, sensorType, wakeUp));
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "SensorManager.getDefaultSensor");
            assertEquals("sensorType=" + sensorType + ",wakeUp=" + wakeUp + ",result=null",
                    String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDefaultSensorByWakeUpAbsent(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            expectGetDefaultSensorByWakeUpUoe(jni, baseVM, useVaList, manager, 1, false, sink);
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_sensor event when config absent: " + e.api,
                        "android_sensor".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDefaultSensorByWakeUpIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_WAKE_UP_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> plain = vm.resolveClass(SENSOR_MANAGER_CLASS).newObject(null);
            expectGetDefaultSensorByWakeUpUoe(jni, baseVM, useVaList, plain, 1, false, sink);
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> wifi = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            expectGetDefaultSensorByWakeUpUoe(jni, baseVM, useVaList, wifi, 1, false, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDefaultSensorByWakeUpCurrentConfigReplace(
            boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_WAKE_UP_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_WAKE_UP_B_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> matchA = invokeGetDefaultSensorByWakeUp(
                    jni, baseVM, useVaList, manager, 1, false);
            assertNotNull(matchA);
            assertNull(invokeGetDefaultSensorByWakeUp(jni, baseVM, useVaList, manager, 1, true));
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            assertNull(invokeGetDefaultSensorByWakeUp(jni, baseVM, useVaList, manager, 1, false));
            DvmObject<?> matchB = invokeGetDefaultSensorByWakeUp(
                    jni, baseVM, useVaList, manager, 1, true);
            assertNotNull(matchB);
            assertTrue(invokeIsWakeUpSensor(jni, baseVM, useVaList, matchB));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetTypeAndProvenance(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            // plain Sensor (no ConfiguredSensor marker) → UOE, no getType event
            int before = countEvents(sink.events, "android_sensor", "Sensor.getType");
            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            try {
                invokeGetType(jni, baseVM, useVaList, plain);
                fail("expected UOE for getType on plain Sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getType"));

            // foreign marker (value object without this BaseVM owner) → UOE
            // create a second VM so owner identity fails
            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreignSensor = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                assertNotNull(foreignSensor);
                try {
                    invokeGetType(jni, baseVM, useVaList, foreignSensor);
                    fail("expected UOE for getType on foreign ConfiguredSensor");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getType"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runPlainAndOtherIsolation(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            // plain SensorManager → UOE, no event
            DvmObject<?> plain = vm.resolveClass(SENSOR_MANAGER_CLASS).newObject(null);
            try {
                invokeGetDefaultSensor(jni, baseVM, useVaList, plain, 1);
                fail("expected UOE for getDefaultSensor on plain SensorManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDefaultSensor"));
            }

            // other SystemService (wifi) must not hit sensor path
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> wifi = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            assertTrue(wifi instanceof SystemService);
            try {
                invokeGetDefaultSensor(jni, baseVM, useVaList, wifi, 1);
                fail("expected UOE for getDefaultSensor on non-sensor SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDefaultSensor"));
            }

            // wrong signature on sensor marker → UOE, no event
            // (getDynamicSensorList(I)Ljava/util/List; is implemented; a non-matching
            // signature of the same method name must still fall through to UOE)
            DvmObject<?> sensorManager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            try {
                DvmClass dvmClass = sensorManager.getObjectType();
                DvmMethod method = new DvmMethod(dvmClass, "getDynamicSensorList",
                        "(I)Ljava/lang/String;", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, sensorManager, signature,
                            new TestIntVaList(baseVM, method, 1));
                } else {
                    jni.callObjectMethod(baseVM, sensorManager, signature,
                            new TestIntVarArg(baseVM, method, 1));
                }
                fail("expected UOE for wrong getDynamicSensorList signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDynamicSensorList"));
            }

            // getSensorList on plain / other SystemService → UOE, no event
            try {
                invokeGetSensorList(jni, baseVM, useVaList, plain, TYPE_ALL);
                fail("expected UOE for getSensorList on plain SensorManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSensorList"));
            }
            try {
                invokeGetSensorList(jni, baseVM, useVaList, wifi, TYPE_ALL);
                fail("expected UOE for getSensorList on non-sensor SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSensorList"));
            }

            // isDynamicSensorDiscoverySupported on plain / other SystemService → UOE, no event
            try {
                invokeIsDynamicSensorDiscoverySupported(jni, baseVM, useVaList, plain);
                fail("expected UOE for isDynamicSensorDiscoverySupported on plain SensorManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isDynamicSensorDiscoverySupported"));
            }
            try {
                invokeIsDynamicSensorDiscoverySupported(jni, baseVM, useVaList, wifi);
                fail("expected UOE for isDynamicSensorDiscoverySupported on non-sensor SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isDynamicSensorDiscoverySupported"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_sensor event on isolation paths: " + e.api,
                        "android_sensor".equals(e.kind));
            }

            // control: real sensor SystemService still works after isolation checks
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, sensorManager, 1);
            assertNotNull(sensor);
            assertEquals(1, countEvents(sink.events, "android_sensor",
                    "SensorManager.getDefaultSensor"));
            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, sensorManager, TYPE_ALL);
            assertNotNull(list);
            assertEquals(2, list.size());
            assertEquals(1, countEvents(sink.events, "android_sensor",
                    "SensorManager.getSensorList"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetSensorListAll(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, TYPE_ALL);
            assertNotNull(list);
            assertEquals(2, list.size());
            DvmObject<?> s0 = list.getValue().get(0);
            DvmObject<?> s1 = list.getValue().get(1);
            assertEquals(SENSOR_CLASS, s0.getObjectType().getClassName());
            assertEquals(SENSOR_CLASS, s1.getObjectType().getClassName());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, s0));
            assertEquals(4, invokeGetType(jni, baseVM, useVaList, s1));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "SensorManager.getSensorList");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=-1,count=2", String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_sensor",
                    "SensorManager.getSensorList"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetSensorListSingle(boolean is64Bit, boolean useVaList,
                                               int sensorType, int expectedCount) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(list);
            assertEquals(expectedCount, list.size());
            if (expectedCount == 1) {
                assertEquals(sensorType, invokeGetType(jni, baseVM, useVaList, list.getValue().get(0)));
            }

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "SensorManager.getSensorList");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=" + sensorType + ",count=" + expectedCount,
                    String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetSensorListEmptyConfig(boolean is64Bit, boolean useVaList, String json)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            ArrayListObject all = invokeGetSensorList(jni, baseVM, useVaList, manager, TYPE_ALL);
            assertNotNull(all);
            assertEquals(0, all.size());
            ArrayListObject one = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertNotNull(one);
            assertEquals(0, one.size());

            assertEquals(2, countEvents(sink.events, "android_sensor",
                    "SensorManager.getSensorList"));
            CapturedEvent last = findLastEvent(sink.events, "android_sensor",
                    "SensorManager.getSensorList");
            assertNotNull(last);
            assertEquals("sensorType=1,count=0", String.valueOf(last.value));
            assertEquals("json-config", last.source);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentGetSensorList(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            try {
                invokeGetSensorList(jni, baseVM, useVaList, manager, TYPE_ALL);
                fail("expected UnsupportedOperationException without sensors config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSensorList"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_sensor event when config absent: " + e.api,
                        "android_sensor".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDynamicSensorListAll(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_DYNAMIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, TYPE_ALL);
            assertNotNull(list);
            // dynamicTypes JSON order [7,1,65535], including overlap with types
            assertEquals(3, list.size());
            DvmObject<?> s0 = list.getValue().get(0);
            DvmObject<?> s1 = list.getValue().get(1);
            DvmObject<?> s2 = list.getValue().get(2);
            assertEquals(SENSOR_CLASS, s0.getObjectType().getClassName());
            assertEquals(SENSOR_CLASS, s1.getObjectType().getClassName());
            assertEquals(SENSOR_CLASS, s2.getObjectType().getClassName());
            assertEquals(7, invokeGetType(jni, baseVM, useVaList, s0));
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, s1));
            assertEquals(65535, invokeGetType(jni, baseVM, useVaList, s2));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "SensorManager.getDynamicSensorList");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=-1,count=3", String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_sensor",
                    "SensorManager.getDynamicSensorList"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDynamicSensorListSingle(boolean is64Bit, boolean useVaList,
                                                      int sensorType, int expectedCount) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_DYNAMIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(list);
            assertEquals(expectedCount, list.size());
            if (expectedCount == 1) {
                assertEquals(sensorType, invokeGetType(jni, baseVM, useVaList, list.getValue().get(0)));
            }

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "SensorManager.getDynamicSensorList");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=" + sensorType + ",count=" + expectedCount,
                    String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDynamicSensorListEmptyConfig(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_DYNAMIC_EMPTY_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            ArrayListObject all = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, TYPE_ALL);
            assertNotNull(all);
            assertEquals(0, all.size());
            ArrayListObject one = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 1);
            assertNotNull(one);
            assertEquals(0, one.size());

            assertEquals(2, countEvents(sink.events, "android_sensor",
                    "SensorManager.getDynamicSensorList"));
            CapturedEvent last = findLastEvent(sink.events, "android_sensor",
                    "SensorManager.getDynamicSensorList");
            assertNotNull(last);
            assertEquals("sensorType=1,count=0", String.valueOf(last.value));
            assertEquals("json-config", last.source);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentGetDynamicSensorList(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            try {
                invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, TYPE_ALL);
                fail("expected UnsupportedOperationException without sensors config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDynamicSensorList"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_sensor event when config absent: " + e.api,
                        "android_sensor".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsDynamicSensorDiscoverySupported(boolean is64Bit, boolean useVaList,
                                                             String json, boolean expected) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertEquals(expected, invokeIsDynamicSensorDiscoverySupported(jni, baseVM, useVaList, manager));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "SensorManager.isDynamicSensorDiscoverySupported");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_sensor",
                    "SensorManager.isDynamicSensorDiscoverySupported"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentIsDynamicSensorDiscoverySupported(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            try {
                invokeIsDynamicSensorDiscoverySupported(jni, baseVM, useVaList, manager);
                fail("expected UnsupportedOperationException without sensors config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isDynamicSensorDiscoverySupported"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_sensor event when config absent: " + e.api,
                        "android_sensor".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsDynamicSensorDiscoverySupportedIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_DISCOVERY_TRUE_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> plain = vm.resolveClass(SENSOR_MANAGER_CLASS).newObject(null);
            try {
                invokeIsDynamicSensorDiscoverySupported(jni, baseVM, useVaList, plain);
                fail("expected UOE for isDynamicSensorDiscoverySupported on plain SensorManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isDynamicSensorDiscoverySupported"));
            }

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> wifi = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            assertTrue(wifi instanceof SystemService);
            try {
                invokeIsDynamicSensorDiscoverySupported(jni, baseVM, useVaList, wifi);
                fail("expected UOE for isDynamicSensorDiscoverySupported on non-sensor SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isDynamicSensorDiscoverySupported"));
            }

            DvmObject<?> sensorManager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            try {
                DvmClass dvmClass = sensorManager.getObjectType();
                DvmMethod method = new DvmMethod(dvmClass, "isDynamicSensorDiscoverySupported",
                        "()I", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callIntMethodV(baseVM, sensorManager, signature,
                            new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callIntMethod(baseVM, sensorManager, signature,
                            new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong isDynamicSensorDiscoverySupported signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isDynamicSensorDiscoverySupported"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_sensor event on isolation paths: " + e.api,
                        "android_sensor".equals(e.kind));
            }

            assertTrue(invokeIsDynamicSensorDiscoverySupported(jni, baseVM, useVaList, sensorManager));
            assertEquals(1, countEvents(sink.events, "android_sensor",
                    "SensorManager.isDynamicSensorDiscoverySupported"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetSensorListFreshnessAndGetType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            ArrayListObject list1 = invokeGetSensorList(jni, baseVM, useVaList, manager, TYPE_ALL);
            ArrayListObject list2 = invokeGetSensorList(jni, baseVM, useVaList, manager, TYPE_ALL);
            assertNotNull(list1);
            assertNotNull(list2);
            assertTrue("each getSensorList must return a fresh list object", list1 != list2);
            assertEquals(2, list1.size());
            assertEquals(2, list2.size());
            DvmObject<?> a0 = list1.getValue().get(0);
            DvmObject<?> b0 = list2.getValue().get(0);
            assertTrue("each call must create new ConfiguredSensor markers", a0 != b0);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, a0));
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, b0));
            assertEquals(4, invokeGetType(jni, baseVM, useVaList, list1.getValue().get(1)));

            // singleton freshness
            ArrayListObject s1 = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            ArrayListObject s2 = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertTrue(s1 != s2);
            assertEquals(1, s1.size());
            assertTrue(s1.getValue().get(0) != s2.getValue().get(0));

            assertEquals(4, countEvents(sink.events, "android_sensor",
                    "SensorManager.getSensorList"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runServiceNameAndValue(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmClass contextClass = vm.resolveClass("android/content/Context");
            DvmObject<?> serviceName = jni.getStaticObjectField(baseVM, contextClass,
                    "android/content/Context->SENSOR_SERVICE:Ljava/lang/String;");
            assertTrue(serviceName instanceof StringObject);
            assertEquals(SystemService.SENSOR_SERVICE, ((StringObject) serviceName).getValue());
            assertEquals("sensor", ((StringObject) serviceName).getValue());

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> manager = invokeGetSystemService(jni, baseVM, useVaList, app,
                    ((StringObject) serviceName).getValue());
            assertNotNull(manager);
            assertTrue(manager instanceof SystemService);
            assertEquals(SENSOR_MANAGER_CLASS, manager.getObjectType().getClassName());
            assertEquals(SystemService.SENSOR_SERVICE, manager.getValue());

            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "SensorManager.getDefaultSensor");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,result=1", String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runTypedGetSystemService(boolean is64Bit, boolean useVaList) throws Exception {
        runTypedConfigured(is64Bit, useVaList);
        runTypedAbsent(is64Bit, useVaList);
    }

    private static void runTypedConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass sensorManagerClass = vm.resolveClass(SENSOR_MANAGER_CLASS);

            DvmObject<?> fromString = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            int eventsBeforeApp = sink.events.size();
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app,
                    sensorManagerClass);
            assertSensorManagerMarker(fromApp);
            assertEquals(fromString.getObjectType().getClassName(), fromApp.getObjectType().getClassName());
            assertEquals(fromString.getValue(), fromApp.getValue());
            assertNoAndroidSensorSince(sink, eventsBeforeApp);
            DvmObject<?> appSensor = invokeGetDefaultSensor(jni, baseVM, useVaList, fromApp, 1);
            assertNotNull(appSensor);
            assertEquals(SENSOR_CLASS, appSensor.getObjectType().getClassName());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, appSensor));

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            int eventsBeforeCtx = sink.events.size();
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context,
                    sensorManagerClass);
            assertSensorManagerMarker(fromCtx);
            assertNoAndroidSensorSince(sink, eventsBeforeCtx);
            DvmObject<?> ctxSensor = invokeGetDefaultSensor(jni, baseVM, useVaList, fromCtx, 1);
            assertNotNull(ctxSensor);
            assertEquals(SENSOR_CLASS, ctxSensor.getObjectType().getClassName());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, ctxSensor));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runTypedAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass sensorManagerClass = vm.resolveClass(SENSOR_MANAGER_CLASS);

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app,
                    sensorManagerClass);
            assertSensorManagerMarker(fromApp);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context,
                    sensorManagerClass);
            assertSensorManagerMarker(fromCtx);
            try {
                invokeGetDefaultSensor(jni, baseVM, useVaList, fromApp, 1);
                fail("expected UOE for getDefaultSensor without android.sensors");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDefaultSensor"));
            }
            try {
                invokeGetDefaultSensor(jni, baseVM, useVaList, fromCtx, 1);
                fail("expected UOE for getDefaultSensor without android.sensors");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDefaultSensor"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_sensor event on typed lookup: " + e.api,
                        "android_sensor".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertSensorManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals(SENSOR_MANAGER_CLASS, manager.getObjectType().getClassName());
        assertEquals(SystemService.SENSOR_SERVICE, manager.getValue());
    }

    private static void assertNoAndroidSensorSince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            assertFalse("unexpected android_sensor event on typed lookup: " + sink.events.get(i).api,
                    "android_sensor".equals(sink.events.get(i).kind));
        }
    }

    private static void runSensorGetNameStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        assertTrue(config.isAndroidSensorNameConfigured(1));
        assertEquals("Accel", config.getAndroidSensorName(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            DvmObject<?> name = invokeGetName(jni, baseVM, useVaList, sensor);
            assertTrue(name instanceof StringObject);
            assertEquals("Accel", ((StringObject) name).getValue());
            assertSame(baseVM, name.getObjectType().vm);
            DvmObject<?> name2 = invokeGetName(jni, baseVM, useVaList, sensor);
            assertNotSame(name, name2);
            assertEquals("Accel", ((StringObject) name2).getValue());
            assertSame(baseVM, name2.getObjectType().vm);

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getName");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,nameLength=5", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("Accel"));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getName"));

            // getType / list regression with names present
            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertEquals(1, list.size());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, list.getValue().get(0)));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetNameDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_NAMES_DYNAMIC_JSON);
        assertTrue(config.isAndroidSensorNameConfigured(7));
        assertFalse(config.isAndroidSensorNameConfigured(1));
        assertEquals("DynGyro", config.getAndroidSensorName(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 7));
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7);
            assertEquals(1, list.size());
            DvmObject<?> sensor = list.getValue().get(0);
            assertEquals(7, invokeGetType(jni, baseVM, useVaList, sensor));

            DvmObject<?> name = invokeGetName(jni, baseVM, useVaList, sensor);
            assertTrue(name instanceof StringObject);
            assertEquals("DynGyro", ((StringObject) name).getValue());
            assertSame(baseVM, name.getObjectType().vm);

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getName");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=7,nameLength=7", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("DynGyro"));
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getName"));

            // types-only sensor still has getType; getName UOE
            DvmObject<?> typeOnly = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(typeOnly);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, typeOnly));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getName");
            try {
                invokeGetName(jni, baseVM, useVaList, typeOnly);
                fail("expected UOE for getName on types-only unnamed sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getName"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetNameNotHandledOnLiveMarker(boolean is64Bit, boolean useVaList,
                                                               String json, int sensorType)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            assertEquals(sensorType, invokeGetType(jni, baseVM, useVaList, sensor));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getName");
            try {
                invokeGetName(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getName when name is not explicitly configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getName"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetNameUnknownType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 99));
            ArrayListObject empty = invokeGetSensorList(jni, baseVM, useVaList, manager, 99);
            assertEquals(0, empty.size());
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getName for unknown type: " + e.api,
                        "Sensor.getName".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetNameIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);

            // plain Sensor
            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            try {
                invokeGetName(jni, baseVM, useVaList, plain);
                fail("expected UOE for getName on plain Sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }

            // wrong signature
            try {
                DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getName", "()I", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callIntMethodV(baseVM, sensor, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callIntMethod(baseVM, sensor, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong getName signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }

            // missing sensors node
            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                BaseVM absentBase = (BaseVM) absentVm;
                DvmObject<?> absentManager = resolveSensorSystemService(
                        absentJni, absentBase, useVaList, absentVm);
                try {
                    invokeGetDefaultSensor(absentJni, absentBase, useVaList, absentManager, 1);
                    fail("expected UOE for getDefaultSensor without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDefaultSensor"));
                }
                DvmObject<?> absentPlain = absentVm.resolveClass(SENSOR_CLASS).newObject(null);
                try {
                    invokeGetName(absentJni, absentBase, useVaList, absentPlain);
                    fail("expected UOE for getName without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getName"));
                }
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_sensor event without sensors node: " + e.api,
                            "android_sensor".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }

            // foreign / cross-VM marker
            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreign = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                assertNotNull(foreign);
                try {
                    invokeGetName(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for getName on foreign ConfiguredSensor");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getName"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getName on isolation paths: " + e.api,
                        "Sensor.getName".equals(e.api));
            }

            // stale: replace with types-only (names omitted). Both getType and getName UOE —
            // not because names vanished, but because the config instance changed.
            int typeBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int nameBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getName");
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            try {
                invokeGetType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getType after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetName(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getName after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            assertEquals(typeBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(nameBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getName"));

            // restore the same config instance: original marker is live again
            emulator.set(TraceEnvironmentConfig.KEY, config);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));
            DvmObject<?> restored = invokeGetName(jni, baseVM, useVaList, sensor);
            assertEquals("Accel", ((StringObject) restored).getValue());
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getName"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * Config replacement with the same type and a different name must stale the old marker.
     * A fresh marker from the current manager is required; restoring A does not revive B's marker.
     */
    private static void runSensorMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_NAMES_ACCEL_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_NAMES_ACCEL_B_JSON);
        assertNotSame(configA, configB);
        assertEquals("Accel-A", configA.getAndroidSensorName(1));
        assertEquals("Accel-B", configB.getAndroidSensorName(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> markerA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA));
            DvmObject<?> nameA = invokeGetName(jni, baseVM, useVaList, markerA);
            assertEquals("Accel-A", ((StringObject) nameA).getValue());

            int typeBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int nameBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getName");
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            try {
                invokeGetType(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getType on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetName(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getName on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            assertEquals(typeBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(nameBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getName"));

            DvmObject<?> markerB = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerB);
            assertNotSame(markerA, markerB);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerB));
            DvmObject<?> nameB = invokeGetName(jni, baseVM, useVaList, markerB);
            assertEquals("Accel-B", ((StringObject) nameB).getValue());

            int typeBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int nameBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getName");
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            try {
                invokeGetType(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getType on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetName(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getName on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            assertEquals(typeBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(nameBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getName"));

            DvmObject<?> markerA2 = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA2);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA2));
            DvmObject<?> nameA2 = invokeGetName(jni, baseVM, useVaList, markerA2);
            assertEquals("Accel-A", ((StringObject) nameA2).getValue());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetNameParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"names\":[]}}}",
                "android.sensors.names");
        assertParseInvalid("{\"android\":{\"sensors\":{\"names\":1}}}",
                "android.sensors.names");
        assertParseInvalid("{\"android\":{\"sensors\":{\"names\":null}}}",
                "android.sensors.names");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"01\":\"A\"}}}}",
                "android.sensors.names.01");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"0\":\"A\"}}}}",
                "android.sensors.names.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"+1\":\"A\"}}}}",
                "android.sensors.names.+1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"-1\":\"A\"}}}}",
                "android.sensors.names.-1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1.0\":\"A\"}}}}",
                "android.sensors.names.1.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\" 1\":\"A\"}}}}",
                "android.sensors.names. 1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1 \":\"A\"}}}}",
                "android.sensors.names.1 ");
        assertParseInvalid("{\"android\":{\"sensors\":{\"names\":{\"65536\":\"A\"}}}}",
                "android.sensors.names.65536");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":null}}}}",
                "android.sensors.names.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":1}}}}",
                "android.sensors.names.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":\"\"}}}}",
                "android.sensors.names.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":\"a\\nb\"}}}}",
                "android.sensors.names.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":\"a\\rb\"}}}}",
                "android.sensors.names.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":\"a\\u0000b\"}}}}",
                "android.sensors.names.1");
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 257; i++) {
            tooLong.append('N');
        }
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":\""
                        + tooLong + "\"}}}}",
                "android.sensors.names.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"4\":\"A\"}}}}",
                "android.sensors.names.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"names\":{\"1\":\"A\"}}}}",
                "android.sensors.names.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"names\":{\"1\":\"A\"}}}}",
                "android.sensors.names.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"extra\":1}}}",
                "android.sensors.extra");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        assertTrue(ok.isAndroidSensorNamesConfigured());
        assertTrue(ok.isAndroidSensorNameConfigured(1));
        assertFalse(ok.isAndroidSensorNameConfigured(4));
        assertEquals("Accel", ok.getAndroidSensorName(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_NAMES_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorNamesConfigured());
        assertFalse(empty.isAndroidSensorNameConfigured(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorNamesConfigured());
        assertFalse(omitted.isAndroidSensorNameConfigured(1));
    }

    private static void runSensorGetVendorStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_VENDORS_STATIC_JSON);
        assertTrue(config.isAndroidSensorVendorConfigured(1));
        assertEquals("VendorX", config.getAndroidSensorVendor(1));
        assertFalse(config.isAndroidSensorNameConfigured(1));
        assertFalse(config.isAndroidSensorNamesConfigured());
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            DvmObject<?> vendor = invokeGetVendor(jni, baseVM, useVaList, sensor);
            assertTrue(vendor instanceof StringObject);
            assertEquals("VendorX", ((StringObject) vendor).getValue());
            assertSame(baseVM, vendor.getObjectType().vm);
            DvmObject<?> vendor2 = invokeGetVendor(jni, baseVM, useVaList, sensor);
            assertNotSame(vendor, vendor2);
            assertEquals("VendorX", ((StringObject) vendor2).getValue());
            assertSame(baseVM, vendor2.getObjectType().vm);

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getVendor");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,vendorLength=7", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("VendorX"));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

            int nameBefore = countEvents(sink.events, "android_sensor", "Sensor.getName");
            try {
                invokeGetName(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getName when only vendors is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            assertEquals(nameBefore, countEvents(sink.events, "android_sensor", "Sensor.getName"));

            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertEquals(1, list.size());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, list.getValue().get(0)));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetVendorDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_VENDORS_DYNAMIC_JSON);
        assertTrue(config.isAndroidSensorVendorConfigured(7));
        assertFalse(config.isAndroidSensorVendorConfigured(1));
        assertEquals("DynVendor", config.getAndroidSensorVendor(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 7));
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7);
            assertEquals(1, list.size());
            DvmObject<?> sensor = list.getValue().get(0);
            assertEquals(7, invokeGetType(jni, baseVM, useVaList, sensor));

            DvmObject<?> vendor = invokeGetVendor(jni, baseVM, useVaList, sensor);
            assertTrue(vendor instanceof StringObject);
            assertEquals("DynVendor", ((StringObject) vendor).getValue());
            assertSame(baseVM, vendor.getObjectType().vm);

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getVendor");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=7,vendorLength=9", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("DynVendor"));
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

            DvmObject<?> typeOnly = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(typeOnly);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, typeOnly));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
            try {
                invokeGetVendor(jni, baseVM, useVaList, typeOnly);
                fail("expected UOE for getVendor on types-only sensor without vendor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVendor"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetVendorNotHandledOnLiveMarker(boolean is64Bit, boolean useVaList,
                                                                 String json, int sensorType)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            assertEquals(sensorType, invokeGetType(jni, baseVM, useVaList, sensor));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
            try {
                invokeGetVendor(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getVendor when vendor is not explicitly configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVendor"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetVendorUnknownType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_VENDORS_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 99));
            ArrayListObject empty = invokeGetSensorList(jni, baseVM, useVaList, manager, 99);
            assertEquals(0, empty.size());
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getVendor for unknown type: " + e.api,
                        "Sensor.getVendor".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetVendorIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_VENDORS_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);

            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            try {
                invokeGetVendor(jni, baseVM, useVaList, plain);
                fail("expected UOE for getVendor on plain Sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVendor"));
            }

            try {
                DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getVendor", "()I", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callIntMethodV(baseVM, sensor, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callIntMethod(baseVM, sensor, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong getVendor signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVendor"));
            }

            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                BaseVM absentBase = (BaseVM) absentVm;
                DvmObject<?> absentManager = resolveSensorSystemService(
                        absentJni, absentBase, useVaList, absentVm);
                try {
                    invokeGetDefaultSensor(absentJni, absentBase, useVaList, absentManager, 1);
                    fail("expected UOE for getDefaultSensor without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDefaultSensor"));
                }
                DvmObject<?> absentPlain = absentVm.resolveClass(SENSOR_CLASS).newObject(null);
                try {
                    invokeGetVendor(absentJni, absentBase, useVaList, absentPlain);
                    fail("expected UOE for getVendor without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getVendor"));
                }
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_sensor event without sensors node: " + e.api,
                            "android_sensor".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreign = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                assertNotNull(foreign);
                try {
                    invokeGetVendor(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for getVendor on foreign ConfiguredSensor");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getVendor"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getVendor on isolation paths: " + e.api,
                        "Sensor.getVendor".equals(e.api));
            }

            int typeBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int vendorBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            try {
                invokeGetType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getType after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetVendor(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getVendor after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVendor"));
            }
            assertEquals(typeBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(vendorBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

            emulator.set(TraceEnvironmentConfig.KEY, config);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));
            DvmObject<?> restored = invokeGetVendor(jni, baseVM, useVaList, sensor);
            assertEquals("VendorX", ((StringObject) restored).getValue());
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * 配置替换后即使 type 相同、厂商值改变，旧 marker 也必须被拒绝；
     * 须从当前 SensorManager 重新获取新 marker 才能读到新值。恢复 A 不会复活 B 的 marker。
     */
    private static void runSensorVendorMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_VENDORS_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_VENDORS_B_JSON);
        assertNotSame(configA, configB);
        assertEquals("Vendor-A", configA.getAndroidSensorVendor(1));
        assertEquals("Vendor-B", configB.getAndroidSensorVendor(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> markerA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA));
            DvmObject<?> vendorA = invokeGetVendor(jni, baseVM, useVaList, markerA);
            assertEquals("Vendor-A", ((StringObject) vendorA).getValue());

            int typeBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int vendorBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            try {
                invokeGetType(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getType on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetVendor(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getVendor on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVendor"));
            }
            assertEquals(typeBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(vendorBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

            DvmObject<?> markerB = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerB);
            assertNotSame(markerA, markerB);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerB));
            DvmObject<?> vendorB = invokeGetVendor(jni, baseVM, useVaList, markerB);
            assertEquals("Vendor-B", ((StringObject) vendorB).getValue());

            int typeBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int vendorBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            try {
                invokeGetType(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getType on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetVendor(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getVendor on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVendor"));
            }
            assertEquals(typeBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(vendorBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

            DvmObject<?> markerA2 = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA2);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA2));
            DvmObject<?> vendorA2 = invokeGetVendor(jni, baseVM, useVaList, markerA2);
            assertEquals("Vendor-A", ((StringObject) vendorA2).getValue());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetVendorParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"vendors\":[]}}}",
                "android.sensors.vendors");
        assertParseInvalid("{\"android\":{\"sensors\":{\"vendors\":1}}}",
                "android.sensors.vendors");
        assertParseInvalid("{\"android\":{\"sensors\":{\"vendors\":null}}}",
                "android.sensors.vendors");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"01\":\"A\"}}}}",
                "android.sensors.vendors.01");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"0\":\"A\"}}}}",
                "android.sensors.vendors.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"+1\":\"A\"}}}}",
                "android.sensors.vendors.+1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"-1\":\"A\"}}}}",
                "android.sensors.vendors.-1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"1.0\":\"A\"}}}}",
                "android.sensors.vendors.1.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\" 1\":\"A\"}}}}",
                "android.sensors.vendors. 1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"1 \":\"A\"}}}}",
                "android.sensors.vendors.1 ");
        assertParseInvalid("{\"android\":{\"sensors\":{\"vendors\":{\"65536\":\"A\"}}}}",
                "android.sensors.vendors.65536");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"1\":null}}}}",
                "android.sensors.vendors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"1\":1}}}}",
                "android.sensors.vendors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"1\":\"\"}}}}",
                "android.sensors.vendors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"1\":\"a\\nb\"}}}}",
                "android.sensors.vendors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"1\":\"a\\rb\"}}}}",
                "android.sensors.vendors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"1\":\"a\\u0000b\"}}}}",
                "android.sensors.vendors.1");
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 257; i++) {
            tooLong.append('V');
        }
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"1\":\""
                        + tooLong + "\"}}}}",
                "android.sensors.vendors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"vendors\":{\"4\":\"A\"}}}}",
                "android.sensors.vendors.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"vendors\":{\"1\":\"A\"}}}}",
                "android.sensors.vendors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"vendors\":{\"1\":\"A\"}}}}",
                "android.sensors.vendors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"extra\":1}}}",
                "android.sensors.extra");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_VENDORS_STATIC_JSON);
        assertTrue(ok.isAndroidSensorVendorsConfigured());
        assertTrue(ok.isAndroidSensorVendorConfigured(1));
        assertFalse(ok.isAndroidSensorVendorConfigured(4));
        assertEquals("VendorX", ok.getAndroidSensorVendor(1));
        assertFalse(ok.isAndroidSensorNamesConfigured());
        assertFalse(ok.isAndroidSensorNameConfigured(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_VENDORS_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorVendorsConfigured());
        assertFalse(empty.isAndroidSensorVendorConfigured(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorVendorsConfigured());
        assertFalse(omitted.isAndroidSensorVendorConfigured(1));
        TraceEnvironmentConfig namesOnly = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        assertFalse(namesOnly.isAndroidSensorVendorsConfigured());
        assertFalse(namesOnly.isAndroidSensorVendorConfigured(1));
        assertNull(namesOnly.getAndroidSensorVendor(1));
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"sensors\":{\"types\":[1],"
                + "\"names\":{\"1\":\"Accel\"},\"vendors\":{\"1\":\"VendorX\"}}}}");
        assertEquals("Accel", both.getAndroidSensorName(1));
        assertEquals("VendorX", both.getAndroidSensorVendor(1));
        assertTrue(both.isAndroidSensorNameConfigured(1));
        assertTrue(both.isAndroidSensorVendorConfigured(1));
    }

    private static void runSensorGetVersionStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_STATIC_JSON);
        assertTrue(config.isAndroidSensorVersionConfigured(1));
        assertEquals(Integer.valueOf(0), config.getAndroidSensorVersion(1));
        assertFalse(config.isAndroidSensorVersionConfigured(4));
        assertFalse(config.isAndroidSensorNameConfigured(1));
        assertFalse(config.isAndroidSensorVendorConfigured(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(0, invokeGetVersion(jni, baseVM, useVaList, sensor));
            assertEquals(0, invokeGetVersion(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getVersion");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,version=0", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("Vendor"));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));

            int nameBefore = countEvents(sink.events, "android_sensor", "Sensor.getName");
            try {
                invokeGetName(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getName when only versions is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            assertEquals(nameBefore, countEvents(sink.events, "android_sensor", "Sensor.getName"));

            int vendorBefore = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
            try {
                invokeGetVendor(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getVendor when only versions is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVendor"));
            }
            assertEquals(vendorBefore, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertEquals(1, list.size());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, list.getValue().get(0)));
            assertEquals(0, invokeGetVersion(jni, baseVM, useVaList, list.getValue().get(0)));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetVersionDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_DYNAMIC_JSON);
        assertTrue(config.isAndroidSensorVersionConfigured(7));
        assertFalse(config.isAndroidSensorVersionConfigured(1));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), config.getAndroidSensorVersion(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 7));
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7);
            assertEquals(1, list.size());
            DvmObject<?> sensor = list.getValue().get(0);
            assertEquals(7, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(Integer.MAX_VALUE, invokeGetVersion(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getVersion");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=7,version=" + Integer.MAX_VALUE, String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));

            DvmObject<?> typeOnly = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(typeOnly);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, typeOnly));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getVersion");
            try {
                invokeGetVersion(jni, baseVM, useVaList, typeOnly);
                fail("expected UOE for getVersion on types-only sensor without version");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVersion"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetVersionNotHandledOnLiveMarker(boolean is64Bit, boolean useVaList,
                                                                  String json, int sensorType)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            assertEquals(sensorType, invokeGetType(jni, baseVM, useVaList, sensor));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getVersion");
            try {
                invokeGetVersion(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getVersion when version is not explicitly configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVersion"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetVersionUnknownType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 99));
            ArrayListObject empty = invokeGetSensorList(jni, baseVM, useVaList, manager, 99);
            assertEquals(0, empty.size());
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getVersion for unknown type: " + e.api,
                        "Sensor.getVersion".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetVersionIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);

            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            try {
                invokeGetVersion(jni, baseVM, useVaList, plain);
                fail("expected UOE for getVersion on plain Sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVersion"));
            }

            try {
                DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getVersion", "()Ljava/lang/String;", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, sensor, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callObjectMethod(baseVM, sensor, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong getVersion signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVersion"));
            }

            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                BaseVM absentBase = (BaseVM) absentVm;
                DvmObject<?> absentManager = resolveSensorSystemService(
                        absentJni, absentBase, useVaList, absentVm);
                try {
                    invokeGetDefaultSensor(absentJni, absentBase, useVaList, absentManager, 1);
                    fail("expected UOE for getDefaultSensor without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDefaultSensor"));
                }
                DvmObject<?> absentPlain = absentVm.resolveClass(SENSOR_CLASS).newObject(null);
                try {
                    invokeGetVersion(absentJni, absentBase, useVaList, absentPlain);
                    fail("expected UOE for getVersion without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getVersion"));
                }
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_sensor event without sensors node: " + e.api,
                            "android_sensor".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreign = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                assertNotNull(foreign);
                try {
                    invokeGetVersion(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for getVersion on foreign ConfiguredSensor");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getVersion"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getVersion on isolation paths: " + e.api,
                        "Sensor.getVersion".equals(e.api));
            }

            int typeBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int versionBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getVersion");
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            try {
                invokeGetType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getType after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetVersion(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getVersion after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVersion"));
            }
            assertEquals(typeBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(versionBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));

            emulator.set(TraceEnvironmentConfig.KEY, config);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));
            assertEquals(0, invokeGetVersion(jni, baseVM, useVaList, sensor));
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * 配置替换后即使 type 相同、版本值改变，旧 marker 也必须被拒绝；
     * 须从当前 SensorManager 重新获取新 marker 才能读到新版本。恢复 A 不会复活 B 的 marker。
     */
    private static void runSensorVersionMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_B_JSON);
        assertNotSame(configA, configB);
        assertEquals(Integer.valueOf(1), configA.getAndroidSensorVersion(1));
        assertEquals(Integer.valueOf(2), configB.getAndroidSensorVersion(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> markerA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA));
            assertEquals(1, invokeGetVersion(jni, baseVM, useVaList, markerA));

            int typeBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int versionBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getVersion");
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            try {
                invokeGetType(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getType on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetVersion(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getVersion on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVersion"));
            }
            assertEquals(typeBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(versionBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));

            DvmObject<?> markerB = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerB);
            assertNotSame(markerA, markerB);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerB));
            assertEquals(2, invokeGetVersion(jni, baseVM, useVaList, markerB));

            int typeBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int versionBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getVersion");
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            try {
                invokeGetType(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getType on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetVersion(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getVersion on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVersion"));
            }
            assertEquals(typeBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(versionBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));

            DvmObject<?> markerA2 = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA2);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA2));
            assertEquals(1, invokeGetVersion(jni, baseVM, useVaList, markerA2));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetVersionParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"versions\":[]}}}",
                "android.sensors.versions");
        assertParseInvalid("{\"android\":{\"sensors\":{\"versions\":1}}}",
                "android.sensors.versions");
        assertParseInvalid("{\"android\":{\"sensors\":{\"versions\":null}}}",
                "android.sensors.versions");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"01\":1}}}}",
                "android.sensors.versions.01");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"0\":1}}}}",
                "android.sensors.versions.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"+1\":1}}}}",
                "android.sensors.versions.+1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"-1\":1}}}}",
                "android.sensors.versions.-1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"1.0\":1}}}}",
                "android.sensors.versions.1.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\" 1\":1}}}}",
                "android.sensors.versions. 1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"1 \":1}}}}",
                "android.sensors.versions.1 ");
        assertParseInvalid("{\"android\":{\"sensors\":{\"versions\":{\"65536\":1}}}}",
                "android.sensors.versions.65536");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"1\":null}}}}",
                "android.sensors.versions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"1\":1.5}}}}",
                "android.sensors.versions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"1\":1.0}}}}",
                "android.sensors.versions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"1\":-1}}}}",
                "android.sensors.versions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"1\":2147483648}}}}",
                "android.sensors.versions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"1\":\"1\"}}}}",
                "android.sensors.versions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"1\":true}}}}",
                "android.sensors.versions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"1\":[]}}}}",
                "android.sensors.versions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"1\":{}}}}}",
                "android.sensors.versions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"versions\":{\"4\":1}}}}",
                "android.sensors.versions.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"versions\":{\"1\":1}}}}",
                "android.sensors.versions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"versions\":{\"1\":1}}}}",
                "android.sensors.versions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"extra\":1}}}",
                "android.sensors.extra");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_STATIC_JSON);
        assertTrue(ok.isAndroidSensorVersionsConfigured());
        assertTrue(ok.isAndroidSensorVersionConfigured(1));
        assertFalse(ok.isAndroidSensorVersionConfigured(4));
        assertEquals(Integer.valueOf(0), ok.getAndroidSensorVersion(1));
        assertFalse(ok.isAndroidSensorNamesConfigured());
        assertFalse(ok.isAndroidSensorVendorsConfigured());
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorVersionsConfigured());
        assertFalse(empty.isAndroidSensorVersionConfigured(1));
        assertNull(empty.getAndroidSensorVersion(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorVersionsConfigured());
        assertFalse(omitted.isAndroidSensorVersionConfigured(1));
        assertNull(omitted.getAndroidSensorVersion(1));
        TraceEnvironmentConfig namesOnly = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        assertFalse(namesOnly.isAndroidSensorVersionsConfigured());
        assertFalse(namesOnly.isAndroidSensorVersionConfigured(1));
        assertNull(namesOnly.getAndroidSensorVersion(1));
        TraceEnvironmentConfig vendorsOnly = TraceEnvironmentConfig.parse(SENSORS_VENDORS_STATIC_JSON);
        assertFalse(vendorsOnly.isAndroidSensorVersionsConfigured());
        assertFalse(vendorsOnly.isAndroidSensorVersionConfigured(1));
        assertNull(vendorsOnly.getAndroidSensorVersion(1));
        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_DYNAMIC_JSON);
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), max.getAndroidSensorVersion(7));
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"sensors\":{\"types\":[1],"
                + "\"names\":{\"1\":\"Accel\"},\"vendors\":{\"1\":\"VendorX\"},"
                + "\"versions\":{\"1\":3}}}}");
        assertEquals("Accel", both.getAndroidSensorName(1));
        assertEquals("VendorX", both.getAndroidSensorVendor(1));
        assertEquals(Integer.valueOf(3), both.getAndroidSensorVersion(1));
        assertTrue(both.isAndroidSensorNameConfigured(1));
        assertTrue(both.isAndroidSensorVendorConfigured(1));
        assertTrue(both.isAndroidSensorVersionConfigured(1));
    }

    private static void runSensorGetStringTypeStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_STATIC_JSON);
        assertTrue(config.isAndroidSensorStringTypeConfigured(1));
        assertEquals("android.sensor.accelerometer", config.getAndroidSensorStringType(1));
        assertFalse(config.isAndroidSensorStringTypeConfigured(4));
        assertFalse(config.isAndroidSensorNameConfigured(1));
        assertFalse(config.isAndroidSensorVendorConfigured(1));
        assertFalse(config.isAndroidSensorVersionConfigured(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            DvmObject<?> stringType = invokeGetStringType(jni, baseVM, useVaList, sensor);
            assertTrue(stringType instanceof StringObject);
            assertEquals("android.sensor.accelerometer", ((StringObject) stringType).getValue());
            assertSame(baseVM, stringType.getObjectType().vm);
            DvmObject<?> stringType2 = invokeGetStringType(jni, baseVM, useVaList, sensor);
            assertNotSame(stringType, stringType2);
            assertEquals("android.sensor.accelerometer", ((StringObject) stringType2).getValue());
            assertSame(baseVM, stringType2.getObjectType().vm);

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getStringType");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,stringTypeLength="
                    + "android.sensor.accelerometer".length(), String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("android.sensor.accelerometer"));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));

            int nameBefore = countEvents(sink.events, "android_sensor", "Sensor.getName");
            try {
                invokeGetName(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getName when only stringTypes is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            assertEquals(nameBefore, countEvents(sink.events, "android_sensor", "Sensor.getName"));

            int vendorBefore = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
            try {
                invokeGetVendor(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getVendor when only stringTypes is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVendor"));
            }
            assertEquals(vendorBefore, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

            int versionBefore = countEvents(sink.events, "android_sensor", "Sensor.getVersion");
            try {
                invokeGetVersion(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getVersion when only stringTypes is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVersion"));
            }
            assertEquals(versionBefore, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));

            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertEquals(1, list.size());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, list.getValue().get(0)));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetStringTypeDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_DYNAMIC_JSON);
        assertTrue(config.isAndroidSensorStringTypeConfigured(7));
        assertFalse(config.isAndroidSensorStringTypeConfigured(1));
        assertEquals("android.sensor.dynamic", config.getAndroidSensorStringType(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 7));
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7);
            assertEquals(1, list.size());
            DvmObject<?> sensor = list.getValue().get(0);
            assertEquals(7, invokeGetType(jni, baseVM, useVaList, sensor));

            DvmObject<?> stringType = invokeGetStringType(jni, baseVM, useVaList, sensor);
            assertTrue(stringType instanceof StringObject);
            assertEquals("android.sensor.dynamic", ((StringObject) stringType).getValue());
            assertSame(baseVM, stringType.getObjectType().vm);

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getStringType");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=7,stringTypeLength="
                    + "android.sensor.dynamic".length(), String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("android.sensor.dynamic"));
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));

            DvmObject<?> typeOnly = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(typeOnly);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, typeOnly));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getStringType");
            try {
                invokeGetStringType(jni, baseVM, useVaList, typeOnly);
                fail("expected UOE for getStringType on types-only sensor without string type");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStringType"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetStringTypeNotHandledOnLiveMarker(boolean is64Bit, boolean useVaList,
                                                                     String json, int sensorType)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            assertEquals(sensorType, invokeGetType(jni, baseVM, useVaList, sensor));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getStringType");
            try {
                invokeGetStringType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getStringType when string type is not explicitly configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStringType"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetStringTypeUnknownType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 99));
            ArrayListObject empty = invokeGetSensorList(jni, baseVM, useVaList, manager, 99);
            assertEquals(0, empty.size());
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getStringType for unknown type: " + e.api,
                        "Sensor.getStringType".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetStringTypeIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);

            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            try {
                invokeGetStringType(jni, baseVM, useVaList, plain);
                fail("expected UOE for getStringType on plain Sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStringType"));
            }

            try {
                DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getStringType", "()I", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callIntMethodV(baseVM, sensor, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callIntMethod(baseVM, sensor, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong getStringType signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStringType"));
            }

            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                BaseVM absentBase = (BaseVM) absentVm;
                DvmObject<?> absentManager = resolveSensorSystemService(
                        absentJni, absentBase, useVaList, absentVm);
                try {
                    invokeGetDefaultSensor(absentJni, absentBase, useVaList, absentManager, 1);
                    fail("expected UOE for getDefaultSensor without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDefaultSensor"));
                }
                DvmObject<?> absentPlain = absentVm.resolveClass(SENSOR_CLASS).newObject(null);
                try {
                    invokeGetStringType(absentJni, absentBase, useVaList, absentPlain);
                    fail("expected UOE for getStringType without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getStringType"));
                }
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_sensor event without sensors node: " + e.api,
                            "android_sensor".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreign = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                assertNotNull(foreign);
                try {
                    invokeGetStringType(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for getStringType on foreign ConfiguredSensor");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getStringType"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getStringType on isolation paths: " + e.api,
                        "Sensor.getStringType".equals(e.api));
            }

            int typeBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int stringTypeBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getStringType");
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            try {
                invokeGetType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getType after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetStringType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getStringType after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStringType"));
            }
            assertEquals(typeBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(stringTypeBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));

            emulator.set(TraceEnvironmentConfig.KEY, config);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));
            DvmObject<?> restored = invokeGetStringType(jni, baseVM, useVaList, sensor);
            assertEquals("android.sensor.accelerometer", ((StringObject) restored).getValue());
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * 配置替换后即使 type 相同、string type 值不同，旧 marker 也必须被拒绝；
     * 须从当前 SensorManager 重新获取新 marker 才能读到新值。恢复 A 不会复活 B 的 marker。
     */
    private static void runSensorStringTypeMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_B_JSON);
        assertNotSame(configA, configB);
        assertEquals("android.sensor.type-a", configA.getAndroidSensorStringType(1));
        assertEquals("android.sensor.type-b", configB.getAndroidSensorStringType(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> markerA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA));
            DvmObject<?> stringTypeA = invokeGetStringType(jni, baseVM, useVaList, markerA);
            assertEquals("android.sensor.type-a", ((StringObject) stringTypeA).getValue());

            int typeBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int stringTypeBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getStringType");
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            try {
                invokeGetType(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getType on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetStringType(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getStringType on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStringType"));
            }
            assertEquals(typeBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(stringTypeBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));

            DvmObject<?> markerB = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerB);
            assertNotSame(markerA, markerB);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerB));
            DvmObject<?> stringTypeB = invokeGetStringType(jni, baseVM, useVaList, markerB);
            assertEquals("android.sensor.type-b", ((StringObject) stringTypeB).getValue());
            assertNotSame(stringTypeA, stringTypeB);

            int typeBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int stringTypeBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getStringType");
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            try {
                invokeGetType(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getType on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetStringType(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getStringType on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStringType"));
            }
            assertEquals(typeBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(stringTypeBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));

            DvmObject<?> markerA2 = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA2);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA2));
            DvmObject<?> stringTypeA2 = invokeGetStringType(jni, baseVM, useVaList, markerA2);
            assertEquals("android.sensor.type-a", ((StringObject) stringTypeA2).getValue());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetStringTypeParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"stringTypes\":[]}}}",
                "android.sensors.stringTypes");
        assertParseInvalid("{\"android\":{\"sensors\":{\"stringTypes\":1}}}",
                "android.sensors.stringTypes");
        assertParseInvalid("{\"android\":{\"sensors\":{\"stringTypes\":null}}}",
                "android.sensors.stringTypes");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"01\":\"A\"}}}}",
                "android.sensors.stringTypes.01");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"0\":\"A\"}}}}",
                "android.sensors.stringTypes.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"+1\":\"A\"}}}}",
                "android.sensors.stringTypes.+1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"-1\":\"A\"}}}}",
                "android.sensors.stringTypes.-1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"1.0\":\"A\"}}}}",
                "android.sensors.stringTypes.1.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\" 1\":\"A\"}}}}",
                "android.sensors.stringTypes. 1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"1 \":\"A\"}}}}",
                "android.sensors.stringTypes.1 ");
        assertParseInvalid("{\"android\":{\"sensors\":{\"stringTypes\":{\"65536\":\"A\"}}}}",
                "android.sensors.stringTypes.65536");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"1\":null}}}}",
                "android.sensors.stringTypes.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"1\":1}}}}",
                "android.sensors.stringTypes.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"1\":\"\"}}}}",
                "android.sensors.stringTypes.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"1\":\"a\\nb\"}}}}",
                "android.sensors.stringTypes.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"1\":\"a\\rb\"}}}}",
                "android.sensors.stringTypes.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"1\":\"a\\u0000b\"}}}}",
                "android.sensors.stringTypes.1");
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 257; i++) {
            tooLong.append('S');
        }
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"1\":\""
                        + tooLong + "\"}}}}",
                "android.sensors.stringTypes.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"stringTypes\":{\"4\":\"A\"}}}}",
                "android.sensors.stringTypes.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"stringTypes\":{\"1\":\"A\"}}}}",
                "android.sensors.stringTypes.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"stringTypes\":{\"1\":\"A\"}}}}",
                "android.sensors.stringTypes.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"extra\":1}}}",
                "android.sensors.extra");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_STATIC_JSON);
        assertTrue(ok.isAndroidSensorStringTypesConfigured());
        assertTrue(ok.isAndroidSensorStringTypeConfigured(1));
        assertFalse(ok.isAndroidSensorStringTypeConfigured(4));
        assertEquals("android.sensor.accelerometer", ok.getAndroidSensorStringType(1));
        assertFalse(ok.isAndroidSensorNamesConfigured());
        assertFalse(ok.isAndroidSensorVendorsConfigured());
        assertFalse(ok.isAndroidSensorVersionsConfigured());
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorStringTypesConfigured());
        assertFalse(empty.isAndroidSensorStringTypeConfigured(1));
        assertNull(empty.getAndroidSensorStringType(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorStringTypesConfigured());
        assertFalse(omitted.isAndroidSensorStringTypeConfigured(1));
        assertNull(omitted.getAndroidSensorStringType(1));
        TraceEnvironmentConfig namesOnly = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        assertFalse(namesOnly.isAndroidSensorStringTypesConfigured());
        assertFalse(namesOnly.isAndroidSensorStringTypeConfigured(1));
        assertNull(namesOnly.getAndroidSensorStringType(1));
        TraceEnvironmentConfig vendorsOnly = TraceEnvironmentConfig.parse(SENSORS_VENDORS_STATIC_JSON);
        assertFalse(vendorsOnly.isAndroidSensorStringTypesConfigured());
        assertFalse(vendorsOnly.isAndroidSensorStringTypeConfigured(1));
        assertNull(vendorsOnly.getAndroidSensorStringType(1));
        TraceEnvironmentConfig versionsOnly = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_STATIC_JSON);
        assertFalse(versionsOnly.isAndroidSensorStringTypesConfigured());
        assertFalse(versionsOnly.isAndroidSensorStringTypeConfigured(1));
        assertNull(versionsOnly.getAndroidSensorStringType(1));
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"sensors\":{\"types\":[1],"
                + "\"names\":{\"1\":\"Accel\"},\"vendors\":{\"1\":\"VendorX\"},"
                + "\"versions\":{\"1\":3},"
                + "\"stringTypes\":{\"1\":\"android.sensor.accelerometer\"}}}}");
        assertEquals("Accel", both.getAndroidSensorName(1));
        assertEquals("VendorX", both.getAndroidSensorVendor(1));
        assertEquals(Integer.valueOf(3), both.getAndroidSensorVersion(1));
        assertEquals("android.sensor.accelerometer", both.getAndroidSensorStringType(1));
        assertTrue(both.isAndroidSensorNameConfigured(1));
        assertTrue(both.isAndroidSensorVendorConfigured(1));
        assertTrue(both.isAndroidSensorVersionConfigured(1));
        assertTrue(both.isAndroidSensorStringTypeConfigured(1));
        assertFalse(both.isAndroidSensorMaximumRangesConfigured());
        assertFalse(both.isAndroidSensorMaximumRangeConfigured(1));
        assertNull(both.getAndroidSensorMaximumRange(1));
    }

    private static void runSensorGetMaximumRangeStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_STATIC_JSON);
        assertTrue(config.isAndroidSensorMaximumRangeConfigured(1));
        assertEquals(Float.valueOf(0.0f), config.getAndroidSensorMaximumRange(1));
        assertFalse(config.isAndroidSensorMaximumRangeConfigured(4));
        assertFalse(config.isAndroidSensorNameConfigured(1));
        assertFalse(config.isAndroidSensorVendorConfigured(1));
        assertFalse(config.isAndroidSensorVersionConfigured(1));
        assertFalse(config.isAndroidSensorStringTypeConfigured(1));
        assertFalse(config.isAndroidSensorPowerConfigured(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(0.0f, invokeGetMaximumRange(jni, baseVM, sensor), 0f);
            assertEquals(0.0f, invokeGetMaximumRangeByMethod(jni, baseVM, sensor), 0f);

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getMaximumRange");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,maximumRange=" + 0.0f, String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("versions"));
            assertFalse(String.valueOf(ev.value).contains("stringTypes"));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));

            int nameBefore = countEvents(sink.events, "android_sensor", "Sensor.getName");
            try {
                invokeGetName(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getName when only maximumRanges is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            assertEquals(nameBefore, countEvents(sink.events, "android_sensor", "Sensor.getName"));

            int vendorBefore = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
            try {
                invokeGetVendor(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getVendor when only maximumRanges is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVendor"));
            }
            assertEquals(vendorBefore, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

            int versionBefore = countEvents(sink.events, "android_sensor", "Sensor.getVersion");
            try {
                invokeGetVersion(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getVersion when only maximumRanges is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVersion"));
            }
            assertEquals(versionBefore, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));

            int stringTypeBefore = countEvents(sink.events, "android_sensor", "Sensor.getStringType");
            try {
                invokeGetStringType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getStringType when only maximumRanges is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStringType"));
            }
            assertEquals(stringTypeBefore, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));

            int resolutionBefore = countEvents(sink.events, "android_sensor", "Sensor.getResolution");
            try {
                invokeGetResolution(jni, baseVM, sensor);
                fail("expected UOE for getResolution when only maximumRanges is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getResolution"));
            }
            assertEquals(resolutionBefore, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));

            int powerBefore = countEvents(sink.events, "android_sensor", "Sensor.getPower");
            try {
                invokeGetPower(jni, baseVM, sensor);
                fail("expected UOE for getPower when only maximumRanges is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPower"));
            }
            assertEquals(powerBefore, countEvents(sink.events, "android_sensor", "Sensor.getPower"));

            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertEquals(1, list.size());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, list.getValue().get(0)));
            assertEquals(0.0f, invokeGetMaximumRange(jni, baseVM, list.getValue().get(0)), 0f);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMaximumRangeDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_DYNAMIC_JSON);
        assertTrue(config.isAndroidSensorMaximumRangeConfigured(7));
        assertFalse(config.isAndroidSensorMaximumRangeConfigured(1));
        assertEquals(Float.valueOf(Float.MAX_VALUE), config.getAndroidSensorMaximumRange(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 7));
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7);
            assertEquals(1, list.size());
            DvmObject<?> sensor = list.getValue().get(0);
            assertEquals(7, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(Float.MAX_VALUE, invokeGetMaximumRange(jni, baseVM, sensor), 0f);

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getMaximumRange");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=7,maximumRange=" + Float.MAX_VALUE, String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));

            DvmObject<?> typeOnly = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(typeOnly);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, typeOnly));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange");
            try {
                invokeGetMaximumRange(jni, baseVM, typeOnly);
                fail("expected UOE for getMaximumRange on types-only sensor without range");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaximumRange"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMaximumRangeNotHandledOnLiveMarker(boolean is64Bit, boolean useVaList,
                                                                       String json, int sensorType)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            assertEquals(sensorType, invokeGetType(jni, baseVM, useVaList, sensor));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange");
            try {
                invokeGetMaximumRange(jni, baseVM, sensor);
                fail("expected UOE for getMaximumRange when maximum range is not explicitly configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaximumRange"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMaximumRangeUnknownType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 99));
            ArrayListObject empty = invokeGetSensorList(jni, baseVM, useVaList, manager, 99);
            assertEquals(0, empty.size());
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getMaximumRange for unknown type: " + e.api,
                        "Sensor.getMaximumRange".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMaximumRangeIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);

            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            try {
                invokeGetMaximumRange(jni, baseVM, plain);
                fail("expected UOE for getMaximumRange on plain Sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaximumRange"));
            }

            try {
                DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getMaximumRange", "()I", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callIntMethodV(baseVM, sensor, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callIntMethod(baseVM, sensor, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong getMaximumRange signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaximumRange"));
            }

            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                BaseVM absentBase = (BaseVM) absentVm;
                DvmObject<?> absentManager = resolveSensorSystemService(
                        absentJni, absentBase, useVaList, absentVm);
                try {
                    invokeGetDefaultSensor(absentJni, absentBase, useVaList, absentManager, 1);
                    fail("expected UOE for getDefaultSensor without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDefaultSensor"));
                }
                DvmObject<?> absentPlain = absentVm.resolveClass(SENSOR_CLASS).newObject(null);
                try {
                    invokeGetMaximumRange(absentJni, absentBase, absentPlain);
                    fail("expected UOE for getMaximumRange without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getMaximumRange"));
                }
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_sensor event without sensors node: " + e.api,
                            "android_sensor".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreign = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                assertNotNull(foreign);
                try {
                    invokeGetMaximumRange(jni, baseVM, foreign);
                    fail("expected UOE for getMaximumRange on foreign ConfiguredSensor");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getMaximumRange"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getMaximumRange on isolation paths: " + e.api,
                        "Sensor.getMaximumRange".equals(e.api));
            }

            int typeBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int rangeBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange");
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            try {
                invokeGetType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getType after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetMaximumRange(jni, baseVM, sensor);
                fail("expected UOE for getMaximumRange after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaximumRange"));
            }
            assertEquals(typeBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(rangeBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));

            emulator.set(TraceEnvironmentConfig.KEY, config);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));
            assertEquals(0.0f, invokeGetMaximumRange(jni, baseVM, sensor), 0f);
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * 配置替换后即使 type 相同、量程值不同，旧 marker 也必须被拒绝；
     * 须从当前 SensorManager 重新获取新 marker 才能读到新量程。恢复 A 不会复活 B 的 marker。
     */
    private static void runSensorMaximumRangeMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_B_JSON);
        assertNotSame(configA, configB);
        assertEquals(Float.valueOf(MAXIMUM_RANGE_DECIMAL_A), configA.getAndroidSensorMaximumRange(1));
        assertEquals(Float.valueOf(MAXIMUM_RANGE_DECIMAL_B), configB.getAndroidSensorMaximumRange(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> markerA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA));
            assertEquals(MAXIMUM_RANGE_DECIMAL_A, invokeGetMaximumRange(jni, baseVM, markerA), 0f);

            int typeBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int rangeBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange");
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            try {
                invokeGetType(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getType on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetMaximumRange(jni, baseVM, markerA);
                fail("expected UOE for getMaximumRange on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaximumRange"));
            }
            assertEquals(typeBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(rangeBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));

            DvmObject<?> markerB = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerB);
            assertNotSame(markerA, markerB);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerB));
            assertEquals(MAXIMUM_RANGE_DECIMAL_B, invokeGetMaximumRange(jni, baseVM, markerB), 0f);

            CapturedEvent evB = findLastEvent(sink.events, "android_sensor", "Sensor.getMaximumRange");
            assertNotNull(evB);
            assertEquals("sensorType=1,maximumRange=" + MAXIMUM_RANGE_DECIMAL_B, String.valueOf(evB.value));

            int typeBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int rangeBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange");
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            try {
                invokeGetType(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getType on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetMaximumRange(jni, baseVM, markerB);
                fail("expected UOE for getMaximumRange on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaximumRange"));
            }
            assertEquals(typeBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(rangeBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));

            DvmObject<?> markerA2 = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA2);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA2));
            assertEquals(MAXIMUM_RANGE_DECIMAL_A, invokeGetMaximumRange(jni, baseVM, markerA2), 0f);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMaximumRangeParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"maximumRanges\":[]}}}",
                "android.sensors.maximumRanges");
        assertParseInvalid("{\"android\":{\"sensors\":{\"maximumRanges\":1}}}",
                "android.sensors.maximumRanges");
        assertParseInvalid("{\"android\":{\"sensors\":{\"maximumRanges\":null}}}",
                "android.sensors.maximumRanges");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"01\":1.0}}}}",
                "android.sensors.maximumRanges.01");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"0\":1.0}}}}",
                "android.sensors.maximumRanges.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"+1\":1.0}}}}",
                "android.sensors.maximumRanges.+1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"-1\":1.0}}}}",
                "android.sensors.maximumRanges.-1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1.0\":1.0}}}}",
                "android.sensors.maximumRanges.1.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\" 1\":1.0}}}}",
                "android.sensors.maximumRanges. 1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1 \":1.0}}}}",
                "android.sensors.maximumRanges.1 ");
        assertParseInvalid("{\"android\":{\"sensors\":{\"maximumRanges\":{\"65536\":1.0}}}}",
                "android.sensors.maximumRanges.65536");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1\":null}}}}",
                "android.sensors.maximumRanges.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1\":true}}}}",
                "android.sensors.maximumRanges.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1\":\"1.0\"}}}}",
                "android.sensors.maximumRanges.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1\":[]}}}}",
                "android.sensors.maximumRanges.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1\":{}}}}}",
                "android.sensors.maximumRanges.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1\":-1}}}}",
                "android.sensors.maximumRanges.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1\":-0.1}}}}",
                "android.sensors.maximumRanges.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1\":1e309}}}}",
                "android.sensors.maximumRanges.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1\":3.5e38}}}}",
                "android.sensors.maximumRanges.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"1\":NaN}}}}",
                "android.sensors.maximumRanges.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maximumRanges\":{\"4\":1.0}}}}",
                "android.sensors.maximumRanges.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"maximumRanges\":{\"1\":1.0}}}}",
                "android.sensors.maximumRanges.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"maximumRanges\":{\"1\":1.0}}}}",
                "android.sensors.maximumRanges.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"extra\":1}}}",
                "android.sensors.extra");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_STATIC_JSON);
        assertTrue(ok.isAndroidSensorMaximumRangesConfigured());
        assertTrue(ok.isAndroidSensorMaximumRangeConfigured(1));
        assertFalse(ok.isAndroidSensorMaximumRangeConfigured(4));
        assertEquals(Float.valueOf(0.0f), ok.getAndroidSensorMaximumRange(1));
        assertFalse(ok.isAndroidSensorNamesConfigured());
        assertFalse(ok.isAndroidSensorVendorsConfigured());
        assertFalse(ok.isAndroidSensorVersionsConfigured());
        assertFalse(ok.isAndroidSensorStringTypesConfigured());
        assertFalse(ok.isAndroidSensorResolutionsConfigured());
        assertFalse(ok.isAndroidSensorResolutionConfigured(1));
        assertNull(ok.getAndroidSensorResolution(1));
        assertFalse(ok.isAndroidSensorPowersConfigured());
        assertFalse(ok.isAndroidSensorPowerConfigured(1));
        assertNull(ok.getAndroidSensorPower(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorMaximumRangesConfigured());
        assertFalse(empty.isAndroidSensorMaximumRangeConfigured(1));
        assertNull(empty.getAndroidSensorMaximumRange(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorMaximumRangesConfigured());
        assertFalse(omitted.isAndroidSensorMaximumRangeConfigured(1));
        assertNull(omitted.getAndroidSensorMaximumRange(1));
        TraceEnvironmentConfig namesOnly = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        assertFalse(namesOnly.isAndroidSensorMaximumRangesConfigured());
        assertFalse(namesOnly.isAndroidSensorMaximumRangeConfigured(1));
        assertNull(namesOnly.getAndroidSensorMaximumRange(1));
        TraceEnvironmentConfig vendorsOnly = TraceEnvironmentConfig.parse(SENSORS_VENDORS_STATIC_JSON);
        assertFalse(vendorsOnly.isAndroidSensorMaximumRangesConfigured());
        assertFalse(vendorsOnly.isAndroidSensorMaximumRangeConfigured(1));
        assertNull(vendorsOnly.getAndroidSensorMaximumRange(1));
        TraceEnvironmentConfig versionsOnly = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_STATIC_JSON);
        assertFalse(versionsOnly.isAndroidSensorMaximumRangesConfigured());
        assertFalse(versionsOnly.isAndroidSensorMaximumRangeConfigured(1));
        assertNull(versionsOnly.getAndroidSensorMaximumRange(1));
        TraceEnvironmentConfig stringTypesOnly = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_STATIC_JSON);
        assertFalse(stringTypesOnly.isAndroidSensorMaximumRangesConfigured());
        assertFalse(stringTypesOnly.isAndroidSensorMaximumRangeConfigured(1));
        assertNull(stringTypesOnly.getAndroidSensorMaximumRange(1));
        TraceEnvironmentConfig resolutionsOnly = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_STATIC_JSON);
        assertFalse(resolutionsOnly.isAndroidSensorMaximumRangesConfigured());
        assertFalse(resolutionsOnly.isAndroidSensorMaximumRangeConfigured(1));
        assertNull(resolutionsOnly.getAndroidSensorMaximumRange(1));
        assertTrue(resolutionsOnly.isAndroidSensorResolutionsConfigured());
        assertEquals(Float.valueOf(0.0f), resolutionsOnly.getAndroidSensorResolution(1));
        assertFalse(resolutionsOnly.isAndroidSensorPowersConfigured());
        assertFalse(resolutionsOnly.isAndroidSensorPowerConfigured(1));
        assertNull(resolutionsOnly.getAndroidSensorPower(1));
        TraceEnvironmentConfig powersOnly = TraceEnvironmentConfig.parse(SENSORS_POWERS_STATIC_JSON);
        assertFalse(powersOnly.isAndroidSensorMaximumRangesConfigured());
        assertFalse(powersOnly.isAndroidSensorMaximumRangeConfigured(1));
        assertNull(powersOnly.getAndroidSensorMaximumRange(1));
        assertTrue(powersOnly.isAndroidSensorPowersConfigured());
        assertEquals(Float.valueOf(0.0f), powersOnly.getAndroidSensorPower(1));
        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_DYNAMIC_JSON);
        assertEquals(Float.valueOf(Float.MAX_VALUE), max.getAndroidSensorMaximumRange(7));
        TraceEnvironmentConfig decimal = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_A_JSON);
        assertEquals(Float.valueOf(MAXIMUM_RANGE_DECIMAL_A), decimal.getAndroidSensorMaximumRange(1));
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"sensors\":{\"types\":[1],"
                + "\"names\":{\"1\":\"Accel\"},\"vendors\":{\"1\":\"VendorX\"},"
                + "\"versions\":{\"1\":3},"
                + "\"stringTypes\":{\"1\":\"android.sensor.accelerometer\"},"
                + "\"maximumRanges\":{\"1\":39.24},"
                + "\"resolutions\":{\"1\":0.01},"
                + "\"powers\":{\"1\":0.13}}}}");
        assertEquals("Accel", both.getAndroidSensorName(1));
        assertEquals("VendorX", both.getAndroidSensorVendor(1));
        assertEquals(Integer.valueOf(3), both.getAndroidSensorVersion(1));
        assertEquals("android.sensor.accelerometer", both.getAndroidSensorStringType(1));
        assertEquals(Float.valueOf(MAXIMUM_RANGE_DECIMAL_A), both.getAndroidSensorMaximumRange(1));
        assertEquals(Float.valueOf(RESOLUTION_DECIMAL_A), both.getAndroidSensorResolution(1));
        assertEquals(Float.valueOf(POWER_DECIMAL_A), both.getAndroidSensorPower(1));
        assertTrue(both.isAndroidSensorNameConfigured(1));
        assertTrue(both.isAndroidSensorVendorConfigured(1));
        assertTrue(both.isAndroidSensorVersionConfigured(1));
        assertTrue(both.isAndroidSensorStringTypeConfigured(1));
        assertTrue(both.isAndroidSensorMaximumRangeConfigured(1));
        assertTrue(both.isAndroidSensorResolutionConfigured(1));
        assertTrue(both.isAndroidSensorPowerConfigured(1));
        assertFalse(Float.valueOf(MAXIMUM_RANGE_DECIMAL_A).equals(both.getAndroidSensorResolution(1)));
        assertFalse(Float.valueOf(MAXIMUM_RANGE_DECIMAL_A).equals(both.getAndroidSensorPower(1)));
        assertFalse(Float.valueOf(RESOLUTION_DECIMAL_A).equals(both.getAndroidSensorPower(1)));
    }

    private static void runSensorGetResolutionStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_STATIC_JSON);
        assertTrue(config.isAndroidSensorResolutionConfigured(1));
        assertEquals(Float.valueOf(0.0f), config.getAndroidSensorResolution(1));
        assertFalse(config.isAndroidSensorResolutionConfigured(4));
        assertFalse(config.isAndroidSensorNameConfigured(1));
        assertFalse(config.isAndroidSensorVendorConfigured(1));
        assertFalse(config.isAndroidSensorVersionConfigured(1));
        assertFalse(config.isAndroidSensorStringTypeConfigured(1));
        assertFalse(config.isAndroidSensorMaximumRangeConfigured(1));
        assertFalse(config.isAndroidSensorPowerConfigured(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(0.0f, invokeGetResolution(jni, baseVM, sensor), 0f);
            assertEquals(0.0f, invokeGetResolutionByMethod(jni, baseVM, sensor), 0f);

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getResolution");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,resolution=" + 0.0f, String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("versions"));
            assertFalse(String.valueOf(ev.value).contains("stringTypes"));
            assertFalse(String.valueOf(ev.value).contains("maximumRange"));
            assertFalse(String.valueOf(ev.value).contains("maximumRanges"));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));

            int nameBefore = countEvents(sink.events, "android_sensor", "Sensor.getName");
            try {
                invokeGetName(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getName when only resolutions is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            assertEquals(nameBefore, countEvents(sink.events, "android_sensor", "Sensor.getName"));

            int vendorBefore = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
            try {
                invokeGetVendor(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getVendor when only resolutions is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVendor"));
            }
            assertEquals(vendorBefore, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

            int versionBefore = countEvents(sink.events, "android_sensor", "Sensor.getVersion");
            try {
                invokeGetVersion(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getVersion when only resolutions is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVersion"));
            }
            assertEquals(versionBefore, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));

            int stringTypeBefore = countEvents(sink.events, "android_sensor", "Sensor.getStringType");
            try {
                invokeGetStringType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getStringType when only resolutions is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStringType"));
            }
            assertEquals(stringTypeBefore, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));

            int rangeBefore = countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange");
            try {
                invokeGetMaximumRange(jni, baseVM, sensor);
                fail("expected UOE for getMaximumRange when only resolutions is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaximumRange"));
            }
            assertEquals(rangeBefore, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));

            int powerBefore = countEvents(sink.events, "android_sensor", "Sensor.getPower");
            try {
                invokeGetPower(jni, baseVM, sensor);
                fail("expected UOE for getPower when only resolutions is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPower"));
            }
            assertEquals(powerBefore, countEvents(sink.events, "android_sensor", "Sensor.getPower"));

            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertEquals(1, list.size());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, list.getValue().get(0)));
            assertEquals(0.0f, invokeGetResolution(jni, baseVM, list.getValue().get(0)), 0f);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetResolutionDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_DYNAMIC_JSON);
        assertTrue(config.isAndroidSensorResolutionConfigured(7));
        assertFalse(config.isAndroidSensorResolutionConfigured(1));
        assertEquals(Float.valueOf(Float.MAX_VALUE), config.getAndroidSensorResolution(7));
        assertFalse(config.isAndroidSensorMaximumRangeConfigured(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 7));
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7);
            assertEquals(1, list.size());
            DvmObject<?> sensor = list.getValue().get(0);
            assertEquals(7, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(Float.MAX_VALUE, invokeGetResolution(jni, baseVM, sensor), 0f);

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getResolution");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=7,resolution=" + Float.MAX_VALUE, String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("maximumRange"));
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));

            DvmObject<?> typeOnly = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(typeOnly);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, typeOnly));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getResolution");
            try {
                invokeGetResolution(jni, baseVM, typeOnly);
                fail("expected UOE for getResolution on types-only sensor without resolution");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getResolution"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetResolutionNotHandledOnLiveMarker(boolean is64Bit, boolean useVaList,
                                                                     String json, int sensorType)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            assertEquals(sensorType, invokeGetType(jni, baseVM, useVaList, sensor));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getResolution");
            try {
                invokeGetResolution(jni, baseVM, sensor);
                fail("expected UOE for getResolution when resolution is not explicitly configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getResolution"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetResolutionUnknownType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 99));
            ArrayListObject empty = invokeGetSensorList(jni, baseVM, useVaList, manager, 99);
            assertEquals(0, empty.size());
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getResolution for unknown type: " + e.api,
                        "Sensor.getResolution".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetResolutionIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);

            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            try {
                invokeGetResolution(jni, baseVM, plain);
                fail("expected UOE for getResolution on plain Sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getResolution"));
            }

            try {
                DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getResolution", "()I", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callIntMethodV(baseVM, sensor, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callIntMethod(baseVM, sensor, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong getResolution signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getResolution"));
            }

            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                BaseVM absentBase = (BaseVM) absentVm;
                DvmObject<?> absentManager = resolveSensorSystemService(
                        absentJni, absentBase, useVaList, absentVm);
                try {
                    invokeGetDefaultSensor(absentJni, absentBase, useVaList, absentManager, 1);
                    fail("expected UOE for getDefaultSensor without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDefaultSensor"));
                }
                DvmObject<?> absentPlain = absentVm.resolveClass(SENSOR_CLASS).newObject(null);
                try {
                    invokeGetResolution(absentJni, absentBase, absentPlain);
                    fail("expected UOE for getResolution without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getResolution"));
                }
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_sensor event without sensors node: " + e.api,
                            "android_sensor".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreign = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                assertNotNull(foreign);
                try {
                    invokeGetResolution(jni, baseVM, foreign);
                    fail("expected UOE for getResolution on foreign ConfiguredSensor");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getResolution"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getResolution on isolation paths: " + e.api,
                        "Sensor.getResolution".equals(e.api));
            }

            int typeBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int resolutionBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getResolution");
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            try {
                invokeGetType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getType after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetResolution(jni, baseVM, sensor);
                fail("expected UOE for getResolution after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getResolution"));
            }
            assertEquals(typeBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(resolutionBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));

            emulator.set(TraceEnvironmentConfig.KEY, config);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));
            assertEquals(0.0f, invokeGetResolution(jni, baseVM, sensor), 0f);
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * 配置替换后即使 type 相同、分辨率值不同，旧 marker 也必须被拒绝；
     * 须从当前 SensorManager 重新获取新 marker 才能读到新分辨率。恢复 A 不会复活 B 的 marker。
     */
    private static void runSensorResolutionMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_B_JSON);
        assertNotSame(configA, configB);
        assertEquals(Float.valueOf(RESOLUTION_DECIMAL_A), configA.getAndroidSensorResolution(1));
        assertEquals(Float.valueOf(RESOLUTION_DECIMAL_B), configB.getAndroidSensorResolution(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> markerA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA));
            assertEquals(RESOLUTION_DECIMAL_A, invokeGetResolution(jni, baseVM, markerA), 0f);

            int typeBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int resolutionBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getResolution");
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            try {
                invokeGetType(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getType on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetResolution(jni, baseVM, markerA);
                fail("expected UOE for getResolution on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getResolution"));
            }
            assertEquals(typeBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(resolutionBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));

            DvmObject<?> markerB = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerB);
            assertNotSame(markerA, markerB);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerB));
            assertEquals(RESOLUTION_DECIMAL_B, invokeGetResolution(jni, baseVM, markerB), 0f);

            CapturedEvent evB = findLastEvent(sink.events, "android_sensor", "Sensor.getResolution");
            assertNotNull(evB);
            assertEquals("sensorType=1,resolution=" + RESOLUTION_DECIMAL_B, String.valueOf(evB.value));

            int typeBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int resolutionBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getResolution");
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            try {
                invokeGetType(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getType on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetResolution(jni, baseVM, markerB);
                fail("expected UOE for getResolution on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getResolution"));
            }
            assertEquals(typeBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(resolutionBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));

            DvmObject<?> markerA2 = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA2);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA2));
            assertEquals(RESOLUTION_DECIMAL_A, invokeGetResolution(jni, baseVM, markerA2), 0f);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetResolutionParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"resolutions\":[]}}}",
                "android.sensors.resolutions");
        assertParseInvalid("{\"android\":{\"sensors\":{\"resolutions\":1}}}",
                "android.sensors.resolutions");
        assertParseInvalid("{\"android\":{\"sensors\":{\"resolutions\":null}}}",
                "android.sensors.resolutions");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"01\":1.0}}}}",
                "android.sensors.resolutions.01");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"0\":1.0}}}}",
                "android.sensors.resolutions.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"+1\":1.0}}}}",
                "android.sensors.resolutions.+1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"-1\":1.0}}}}",
                "android.sensors.resolutions.-1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"1.0\":1.0}}}}",
                "android.sensors.resolutions.1.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\" 1\":1.0}}}}",
                "android.sensors.resolutions. 1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"1 \":1.0}}}}",
                "android.sensors.resolutions.1 ");
        assertParseInvalid("{\"android\":{\"sensors\":{\"resolutions\":{\"65536\":1.0}}}}",
                "android.sensors.resolutions.65536");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"1\":null}}}}",
                "android.sensors.resolutions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"1\":true}}}}",
                "android.sensors.resolutions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"1\":\"1.0\"}}}}",
                "android.sensors.resolutions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"1\":[]}}}}",
                "android.sensors.resolutions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"1\":{}}}}}",
                "android.sensors.resolutions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"1\":-1}}}}",
                "android.sensors.resolutions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"1\":-0.1}}}}",
                "android.sensors.resolutions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"1\":1e309}}}}",
                "android.sensors.resolutions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"1\":3.5e38}}}}",
                "android.sensors.resolutions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"1\":NaN}}}}",
                "android.sensors.resolutions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"4\":1.0}}}}",
                "android.sensors.resolutions.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"resolutions\":{\"1\":1.0}}}}",
                "android.sensors.resolutions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"resolutions\":{\"1\":1.0}}}}",
                "android.sensors.resolutions.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"extra\":1}}}",
                "android.sensors.extra");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_STATIC_JSON);
        assertTrue(ok.isAndroidSensorResolutionsConfigured());
        assertTrue(ok.isAndroidSensorResolutionConfigured(1));
        assertFalse(ok.isAndroidSensorResolutionConfigured(4));
        assertEquals(Float.valueOf(0.0f), ok.getAndroidSensorResolution(1));
        assertFalse(ok.isAndroidSensorNamesConfigured());
        assertFalse(ok.isAndroidSensorVendorsConfigured());
        assertFalse(ok.isAndroidSensorVersionsConfigured());
        assertFalse(ok.isAndroidSensorStringTypesConfigured());
        assertFalse(ok.isAndroidSensorMaximumRangesConfigured());
        assertFalse(ok.isAndroidSensorMaximumRangeConfigured(1));
        assertNull(ok.getAndroidSensorMaximumRange(1));
        assertFalse(ok.isAndroidSensorPowersConfigured());
        assertFalse(ok.isAndroidSensorPowerConfigured(1));
        assertNull(ok.getAndroidSensorPower(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorResolutionsConfigured());
        assertFalse(empty.isAndroidSensorResolutionConfigured(1));
        assertNull(empty.getAndroidSensorResolution(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorResolutionsConfigured());
        assertFalse(omitted.isAndroidSensorResolutionConfigured(1));
        assertNull(omitted.getAndroidSensorResolution(1));
        TraceEnvironmentConfig namesOnly = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        assertFalse(namesOnly.isAndroidSensorResolutionsConfigured());
        assertFalse(namesOnly.isAndroidSensorResolutionConfigured(1));
        assertNull(namesOnly.getAndroidSensorResolution(1));
        TraceEnvironmentConfig vendorsOnly = TraceEnvironmentConfig.parse(SENSORS_VENDORS_STATIC_JSON);
        assertFalse(vendorsOnly.isAndroidSensorResolutionsConfigured());
        assertFalse(vendorsOnly.isAndroidSensorResolutionConfigured(1));
        assertNull(vendorsOnly.getAndroidSensorResolution(1));
        TraceEnvironmentConfig versionsOnly = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_STATIC_JSON);
        assertFalse(versionsOnly.isAndroidSensorResolutionsConfigured());
        assertFalse(versionsOnly.isAndroidSensorResolutionConfigured(1));
        assertNull(versionsOnly.getAndroidSensorResolution(1));
        TraceEnvironmentConfig stringTypesOnly = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_STATIC_JSON);
        assertFalse(stringTypesOnly.isAndroidSensorResolutionsConfigured());
        assertFalse(stringTypesOnly.isAndroidSensorResolutionConfigured(1));
        assertNull(stringTypesOnly.getAndroidSensorResolution(1));
        TraceEnvironmentConfig maximumRangesOnly = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_STATIC_JSON);
        assertFalse(maximumRangesOnly.isAndroidSensorResolutionsConfigured());
        assertFalse(maximumRangesOnly.isAndroidSensorResolutionConfigured(1));
        assertNull(maximumRangesOnly.getAndroidSensorResolution(1));
        TraceEnvironmentConfig powersOnly = TraceEnvironmentConfig.parse(SENSORS_POWERS_STATIC_JSON);
        assertFalse(powersOnly.isAndroidSensorResolutionsConfigured());
        assertFalse(powersOnly.isAndroidSensorResolutionConfigured(1));
        assertNull(powersOnly.getAndroidSensorResolution(1));
        assertTrue(powersOnly.isAndroidSensorPowersConfigured());
        assertEquals(Float.valueOf(0.0f), powersOnly.getAndroidSensorPower(1));
        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_DYNAMIC_JSON);
        assertEquals(Float.valueOf(Float.MAX_VALUE), max.getAndroidSensorResolution(7));
        TraceEnvironmentConfig decimal = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_A_JSON);
        assertEquals(Float.valueOf(RESOLUTION_DECIMAL_A), decimal.getAndroidSensorResolution(1));
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"sensors\":{\"types\":[1],"
                + "\"names\":{\"1\":\"Accel\"},\"vendors\":{\"1\":\"VendorX\"},"
                + "\"versions\":{\"1\":3},"
                + "\"stringTypes\":{\"1\":\"android.sensor.accelerometer\"},"
                + "\"maximumRanges\":{\"1\":39.24},"
                + "\"resolutions\":{\"1\":0.01},"
                + "\"powers\":{\"1\":0.13}}}}");
        assertEquals("Accel", both.getAndroidSensorName(1));
        assertEquals("VendorX", both.getAndroidSensorVendor(1));
        assertEquals(Integer.valueOf(3), both.getAndroidSensorVersion(1));
        assertEquals("android.sensor.accelerometer", both.getAndroidSensorStringType(1));
        assertEquals(Float.valueOf(MAXIMUM_RANGE_DECIMAL_A), both.getAndroidSensorMaximumRange(1));
        assertEquals(Float.valueOf(RESOLUTION_DECIMAL_A), both.getAndroidSensorResolution(1));
        assertEquals(Float.valueOf(POWER_DECIMAL_A), both.getAndroidSensorPower(1));
        assertTrue(both.isAndroidSensorNameConfigured(1));
        assertTrue(both.isAndroidSensorVendorConfigured(1));
        assertTrue(both.isAndroidSensorVersionConfigured(1));
        assertTrue(both.isAndroidSensorStringTypeConfigured(1));
        assertTrue(both.isAndroidSensorMaximumRangeConfigured(1));
        assertTrue(both.isAndroidSensorResolutionConfigured(1));
        assertTrue(both.isAndroidSensorPowerConfigured(1));
        assertFalse(Float.valueOf(MAXIMUM_RANGE_DECIMAL_A).equals(both.getAndroidSensorResolution(1)));
        assertFalse(Float.valueOf(RESOLUTION_DECIMAL_A).equals(both.getAndroidSensorMaximumRange(1)));
        assertFalse(Float.valueOf(POWER_DECIMAL_A).equals(both.getAndroidSensorMaximumRange(1)));
        assertFalse(Float.valueOf(POWER_DECIMAL_A).equals(both.getAndroidSensorResolution(1)));
    }

    private static void runSensorGetPowerStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_POWERS_STATIC_JSON);
        assertTrue(config.isAndroidSensorPowerConfigured(1));
        assertEquals(Float.valueOf(0.0f), config.getAndroidSensorPower(1));
        assertFalse(config.isAndroidSensorPowerConfigured(4));
        assertFalse(config.isAndroidSensorNameConfigured(1));
        assertFalse(config.isAndroidSensorVendorConfigured(1));
        assertFalse(config.isAndroidSensorVersionConfigured(1));
        assertFalse(config.isAndroidSensorStringTypeConfigured(1));
        assertFalse(config.isAndroidSensorMaximumRangeConfigured(1));
        assertFalse(config.isAndroidSensorResolutionConfigured(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(0.0f, invokeGetPower(jni, baseVM, sensor), 0f);
            assertEquals(0.0f, invokeGetPowerByMethod(jni, baseVM, sensor), 0f);

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getPower");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,power=" + 0.0f, String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("versions"));
            assertFalse(String.valueOf(ev.value).contains("stringTypes"));
            assertFalse(String.valueOf(ev.value).contains("maximumRange"));
            assertFalse(String.valueOf(ev.value).contains("maximumRanges"));
            assertFalse(String.valueOf(ev.value).contains("resolution"));
            assertFalse(String.valueOf(ev.value).contains("resolutions"));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getPower"));

            int nameBefore = countEvents(sink.events, "android_sensor", "Sensor.getName");
            try {
                invokeGetName(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getName when only powers is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            assertEquals(nameBefore, countEvents(sink.events, "android_sensor", "Sensor.getName"));

            int vendorBefore = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
            try {
                invokeGetVendor(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getVendor when only powers is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVendor"));
            }
            assertEquals(vendorBefore, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

            int versionBefore = countEvents(sink.events, "android_sensor", "Sensor.getVersion");
            try {
                invokeGetVersion(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getVersion when only powers is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVersion"));
            }
            assertEquals(versionBefore, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));

            int stringTypeBefore = countEvents(sink.events, "android_sensor", "Sensor.getStringType");
            try {
                invokeGetStringType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getStringType when only powers is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStringType"));
            }
            assertEquals(stringTypeBefore, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));

            int rangeBefore = countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange");
            try {
                invokeGetMaximumRange(jni, baseVM, sensor);
                fail("expected UOE for getMaximumRange when only powers is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaximumRange"));
            }
            assertEquals(rangeBefore, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));

            int resolutionBefore = countEvents(sink.events, "android_sensor", "Sensor.getResolution");
            try {
                invokeGetResolution(jni, baseVM, sensor);
                fail("expected UOE for getResolution when only powers is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getResolution"));
            }
            assertEquals(resolutionBefore, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));

            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertEquals(1, list.size());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, list.getValue().get(0)));
            assertEquals(0.0f, invokeGetPower(jni, baseVM, list.getValue().get(0)), 0f);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetPowerDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_POWERS_DYNAMIC_JSON);
        assertTrue(config.isAndroidSensorPowerConfigured(7));
        assertFalse(config.isAndroidSensorPowerConfigured(1));
        assertEquals(Float.valueOf(Float.MAX_VALUE), config.getAndroidSensorPower(7));
        assertFalse(config.isAndroidSensorMaximumRangeConfigured(7));
        assertFalse(config.isAndroidSensorResolutionConfigured(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 7));
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7);
            assertEquals(1, list.size());
            DvmObject<?> sensor = list.getValue().get(0);
            assertEquals(7, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(Float.MAX_VALUE, invokeGetPower(jni, baseVM, sensor), 0f);

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getPower");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=7,power=" + Float.MAX_VALUE, String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("maximumRange"));
            assertFalse(String.valueOf(ev.value).contains("resolution"));
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getPower"));

            DvmObject<?> typeOnly = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(typeOnly);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, typeOnly));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getPower");
            try {
                invokeGetPower(jni, baseVM, typeOnly);
                fail("expected UOE for getPower on types-only sensor without power");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPower"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getPower"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetPowerNotHandledOnLiveMarker(boolean is64Bit, boolean useVaList,
                                                                String json, int sensorType)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            assertEquals(sensorType, invokeGetType(jni, baseVM, useVaList, sensor));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getPower");
            try {
                invokeGetPower(jni, baseVM, sensor);
                fail("expected UOE for getPower when power is not explicitly configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPower"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getPower"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetPowerUnknownType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_POWERS_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 99));
            ArrayListObject empty = invokeGetSensorList(jni, baseVM, useVaList, manager, 99);
            assertEquals(0, empty.size());
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getPower for unknown type: " + e.api,
                        "Sensor.getPower".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetPowerIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_POWERS_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);

            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            try {
                invokeGetPower(jni, baseVM, plain);
                fail("expected UOE for getPower on plain Sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPower"));
            }

            try {
                DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getPower", "()I", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callIntMethodV(baseVM, sensor, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callIntMethod(baseVM, sensor, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong getPower signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPower"));
            }

            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                BaseVM absentBase = (BaseVM) absentVm;
                DvmObject<?> absentManager = resolveSensorSystemService(
                        absentJni, absentBase, useVaList, absentVm);
                try {
                    invokeGetDefaultSensor(absentJni, absentBase, useVaList, absentManager, 1);
                    fail("expected UOE for getDefaultSensor without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDefaultSensor"));
                }
                DvmObject<?> absentPlain = absentVm.resolveClass(SENSOR_CLASS).newObject(null);
                try {
                    invokeGetPower(absentJni, absentBase, absentPlain);
                    fail("expected UOE for getPower without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getPower"));
                }
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_sensor event without sensors node: " + e.api,
                            "android_sensor".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreign = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                assertNotNull(foreign);
                try {
                    invokeGetPower(jni, baseVM, foreign);
                    fail("expected UOE for getPower on foreign ConfiguredSensor");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getPower"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getPower on isolation paths: " + e.api,
                        "Sensor.getPower".equals(e.api));
            }

            int typeBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int powerBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getPower");
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            try {
                invokeGetType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getType after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetPower(jni, baseVM, sensor);
                fail("expected UOE for getPower after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPower"));
            }
            assertEquals(typeBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(powerBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getPower"));

            emulator.set(TraceEnvironmentConfig.KEY, config);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));
            assertEquals(0.0f, invokeGetPower(jni, baseVM, sensor), 0f);
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getPower"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * 配置替换后即使 type 相同、功耗值不同，旧 marker 也必须被拒绝；
     * 须从当前 SensorManager 重新获取新 marker 才能读到新功耗。恢复 A 不会复活 B 的 marker。
     */
    private static void runSensorPowerMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_POWERS_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_POWERS_B_JSON);
        assertNotSame(configA, configB);
        assertEquals(Float.valueOf(POWER_DECIMAL_A), configA.getAndroidSensorPower(1));
        assertEquals(Float.valueOf(POWER_DECIMAL_B), configB.getAndroidSensorPower(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> markerA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA));
            assertEquals(POWER_DECIMAL_A, invokeGetPower(jni, baseVM, markerA), 0f);

            int typeBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int powerBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getPower");
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            try {
                invokeGetType(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getType on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetPower(jni, baseVM, markerA);
                fail("expected UOE for getPower on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPower"));
            }
            assertEquals(typeBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(powerBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getPower"));

            DvmObject<?> markerB = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerB);
            assertNotSame(markerA, markerB);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerB));
            assertEquals(POWER_DECIMAL_B, invokeGetPower(jni, baseVM, markerB), 0f);

            CapturedEvent evB = findLastEvent(sink.events, "android_sensor", "Sensor.getPower");
            assertNotNull(evB);
            assertEquals("sensorType=1,power=" + POWER_DECIMAL_B, String.valueOf(evB.value));

            int typeBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int powerBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getPower");
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            try {
                invokeGetType(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getType on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetPower(jni, baseVM, markerB);
                fail("expected UOE for getPower on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPower"));
            }
            assertEquals(typeBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(powerBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getPower"));

            DvmObject<?> markerA2 = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA2);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA2));
            assertEquals(POWER_DECIMAL_A, invokeGetPower(jni, baseVM, markerA2), 0f);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetPowerParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"powers\":[]}}}",
                "android.sensors.powers");
        assertParseInvalid("{\"android\":{\"sensors\":{\"powers\":1}}}",
                "android.sensors.powers");
        assertParseInvalid("{\"android\":{\"sensors\":{\"powers\":null}}}",
                "android.sensors.powers");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"01\":1.0}}}}",
                "android.sensors.powers.01");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"0\":1.0}}}}",
                "android.sensors.powers.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"+1\":1.0}}}}",
                "android.sensors.powers.+1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"-1\":1.0}}}}",
                "android.sensors.powers.-1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"1.0\":1.0}}}}",
                "android.sensors.powers.1.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\" 1\":1.0}}}}",
                "android.sensors.powers. 1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"1 \":1.0}}}}",
                "android.sensors.powers.1 ");
        assertParseInvalid("{\"android\":{\"sensors\":{\"powers\":{\"65536\":1.0}}}}",
                "android.sensors.powers.65536");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"1\":null}}}}",
                "android.sensors.powers.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"1\":true}}}}",
                "android.sensors.powers.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"1\":\"\"}}}}",
                "android.sensors.powers.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"1\":\"1.0\"}}}}",
                "android.sensors.powers.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"1\":[]}}}}",
                "android.sensors.powers.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"1\":{}}}}}",
                "android.sensors.powers.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"1\":-1}}}}",
                "android.sensors.powers.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"1\":-0.1}}}}",
                "android.sensors.powers.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"1\":1e309}}}}",
                "android.sensors.powers.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"1\":3.5e38}}}}",
                "android.sensors.powers.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"1\":NaN}}}}",
                "android.sensors.powers.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"powers\":{\"4\":1.0}}}}",
                "android.sensors.powers.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"powers\":{\"1\":1.0}}}}",
                "android.sensors.powers.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"powers\":{\"1\":1.0}}}}",
                "android.sensors.powers.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"extra\":1}}}",
                "android.sensors.extra");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_POWERS_STATIC_JSON);
        assertTrue(ok.isAndroidSensorPowersConfigured());
        assertTrue(ok.isAndroidSensorPowerConfigured(1));
        assertFalse(ok.isAndroidSensorPowerConfigured(4));
        assertEquals(Float.valueOf(0.0f), ok.getAndroidSensorPower(1));
        assertFalse(ok.isAndroidSensorNamesConfigured());
        assertFalse(ok.isAndroidSensorVendorsConfigured());
        assertFalse(ok.isAndroidSensorVersionsConfigured());
        assertFalse(ok.isAndroidSensorStringTypesConfigured());
        assertFalse(ok.isAndroidSensorMaximumRangesConfigured());
        assertFalse(ok.isAndroidSensorMaximumRangeConfigured(1));
        assertNull(ok.getAndroidSensorMaximumRange(1));
        assertFalse(ok.isAndroidSensorResolutionsConfigured());
        assertFalse(ok.isAndroidSensorResolutionConfigured(1));
        assertNull(ok.getAndroidSensorResolution(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_POWERS_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorPowersConfigured());
        assertFalse(empty.isAndroidSensorPowerConfigured(1));
        assertNull(empty.getAndroidSensorPower(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorPowersConfigured());
        assertFalse(omitted.isAndroidSensorPowerConfigured(1));
        assertNull(omitted.getAndroidSensorPower(1));
        TraceEnvironmentConfig namesOnly = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        assertFalse(namesOnly.isAndroidSensorPowersConfigured());
        assertFalse(namesOnly.isAndroidSensorPowerConfigured(1));
        assertNull(namesOnly.getAndroidSensorPower(1));
        TraceEnvironmentConfig vendorsOnly = TraceEnvironmentConfig.parse(SENSORS_VENDORS_STATIC_JSON);
        assertFalse(vendorsOnly.isAndroidSensorPowersConfigured());
        assertFalse(vendorsOnly.isAndroidSensorPowerConfigured(1));
        assertNull(vendorsOnly.getAndroidSensorPower(1));
        TraceEnvironmentConfig versionsOnly = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_STATIC_JSON);
        assertFalse(versionsOnly.isAndroidSensorPowersConfigured());
        assertFalse(versionsOnly.isAndroidSensorPowerConfigured(1));
        assertNull(versionsOnly.getAndroidSensorPower(1));
        TraceEnvironmentConfig stringTypesOnly = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_STATIC_JSON);
        assertFalse(stringTypesOnly.isAndroidSensorPowersConfigured());
        assertFalse(stringTypesOnly.isAndroidSensorPowerConfigured(1));
        assertNull(stringTypesOnly.getAndroidSensorPower(1));
        TraceEnvironmentConfig maximumRangesOnly = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_STATIC_JSON);
        assertFalse(maximumRangesOnly.isAndroidSensorPowersConfigured());
        assertFalse(maximumRangesOnly.isAndroidSensorPowerConfigured(1));
        assertNull(maximumRangesOnly.getAndroidSensorPower(1));
        TraceEnvironmentConfig resolutionsOnly = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_STATIC_JSON);
        assertFalse(resolutionsOnly.isAndroidSensorPowersConfigured());
        assertFalse(resolutionsOnly.isAndroidSensorPowerConfigured(1));
        assertNull(resolutionsOnly.getAndroidSensorPower(1));
        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(SENSORS_POWERS_DYNAMIC_JSON);
        assertEquals(Float.valueOf(Float.MAX_VALUE), max.getAndroidSensorPower(7));
        TraceEnvironmentConfig decimal = TraceEnvironmentConfig.parse(SENSORS_POWERS_A_JSON);
        assertEquals(Float.valueOf(POWER_DECIMAL_A), decimal.getAndroidSensorPower(1));
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"sensors\":{\"types\":[1],"
                + "\"names\":{\"1\":\"Accel\"},\"vendors\":{\"1\":\"VendorX\"},"
                + "\"versions\":{\"1\":3},"
                + "\"stringTypes\":{\"1\":\"android.sensor.accelerometer\"},"
                + "\"maximumRanges\":{\"1\":39.24},"
                + "\"resolutions\":{\"1\":0.01},"
                + "\"powers\":{\"1\":0.13}}}}");
        assertEquals("Accel", both.getAndroidSensorName(1));
        assertEquals("VendorX", both.getAndroidSensorVendor(1));
        assertEquals(Integer.valueOf(3), both.getAndroidSensorVersion(1));
        assertEquals("android.sensor.accelerometer", both.getAndroidSensorStringType(1));
        assertEquals(Float.valueOf(MAXIMUM_RANGE_DECIMAL_A), both.getAndroidSensorMaximumRange(1));
        assertEquals(Float.valueOf(RESOLUTION_DECIMAL_A), both.getAndroidSensorResolution(1));
        assertEquals(Float.valueOf(POWER_DECIMAL_A), both.getAndroidSensorPower(1));
        assertTrue(both.isAndroidSensorNameConfigured(1));
        assertTrue(both.isAndroidSensorVendorConfigured(1));
        assertTrue(both.isAndroidSensorVersionConfigured(1));
        assertTrue(both.isAndroidSensorStringTypeConfigured(1));
        assertTrue(both.isAndroidSensorMaximumRangeConfigured(1));
        assertTrue(both.isAndroidSensorResolutionConfigured(1));
        assertTrue(both.isAndroidSensorPowerConfigured(1));
        assertFalse(Float.valueOf(MAXIMUM_RANGE_DECIMAL_A).equals(both.getAndroidSensorPower(1)));
        assertFalse(Float.valueOf(RESOLUTION_DECIMAL_A).equals(both.getAndroidSensorPower(1)));
        assertFalse(Float.valueOf(POWER_DECIMAL_A).equals(both.getAndroidSensorMaximumRange(1)));
        assertFalse(Float.valueOf(POWER_DECIMAL_A).equals(both.getAndroidSensorResolution(1)));
    }

    private static void runSensorGetMinDelayStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_STATIC_JSON);
        assertTrue(config.isAndroidSensorMinDelayMicrosConfigured(1));
        assertEquals(Integer.valueOf(0), config.getAndroidSensorMinDelayMicros(1));
        assertFalse(config.isAndroidSensorMinDelayMicrosConfigured(4));
        assertFalse(config.isAndroidSensorNameConfigured(1));
        assertFalse(config.isAndroidSensorVendorConfigured(1));
        assertFalse(config.isAndroidSensorVersionConfigured(1));
        assertFalse(config.isAndroidSensorStringTypeConfigured(1));
        assertFalse(config.isAndroidSensorMaximumRangeConfigured(1));
        assertFalse(config.isAndroidSensorResolutionConfigured(1));
        assertFalse(config.isAndroidSensorPowerConfigured(1));
        assertFalse(config.isAndroidSensorMaxDelayMicrosConfigured(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(0, invokeGetMinDelay(jni, baseVM, useVaList, sensor));
            assertEquals(0, invokeGetMinDelayByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getMinDelay");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,minDelayMicros=0", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("versions"));
            assertFalse(String.valueOf(ev.value).contains("stringTypes"));
            assertFalse(String.valueOf(ev.value).contains("maximumRange"));
            assertFalse(String.valueOf(ev.value).contains("resolution"));
            assertFalse(String.valueOf(ev.value).contains("power"));
            assertFalse(String.valueOf(ev.value).contains("version="));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getMinDelay"));

            assertOtherSensorGettersUoeWhenOnlyMinDelay(jni, baseVM, useVaList, sensor, sink);

            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertEquals(1, list.size());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, list.getValue().get(0)));
            assertEquals(0, invokeGetMinDelay(jni, baseVM, useVaList, list.getValue().get(0)));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMinDelayGeneralValue(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_A_JSON);
        assertEquals(Integer.valueOf(MIN_DELAY_MICROS_GENERAL_A),
                config.getAndroidSensorMinDelayMicros(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(MIN_DELAY_MICROS_GENERAL_A, invokeGetMinDelay(jni, baseVM, useVaList, sensor));
            assertEquals(MIN_DELAY_MICROS_GENERAL_A,
                    invokeGetMinDelayByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getMinDelay");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,minDelayMicros=" + MIN_DELAY_MICROS_GENERAL_A,
                    String.valueOf(ev.value));
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getMinDelay"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMinDelayDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_DYNAMIC_JSON);
        assertTrue(config.isAndroidSensorMinDelayMicrosConfigured(7));
        assertFalse(config.isAndroidSensorMinDelayMicrosConfigured(1));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), config.getAndroidSensorMinDelayMicros(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 7));
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7);
            assertEquals(1, list.size());
            DvmObject<?> sensor = list.getValue().get(0);
            assertEquals(7, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(Integer.MAX_VALUE, invokeGetMinDelay(jni, baseVM, useVaList, sensor));
            assertEquals(Integer.MAX_VALUE, invokeGetMinDelayByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getMinDelay");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=7,minDelayMicros=" + Integer.MAX_VALUE, String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("version="));
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getMinDelay"));

            DvmObject<?> typeOnly = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(typeOnly);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, typeOnly));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getMinDelay");
            try {
                invokeGetMinDelay(jni, baseVM, useVaList, typeOnly);
                fail("expected UOE for getMinDelay on types-only sensor without minDelay");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMinDelay"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getMinDelay"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMinDelayNotHandledOnLiveMarker(boolean is64Bit, boolean useVaList,
                                                                   String json, int sensorType)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            assertEquals(sensorType, invokeGetType(jni, baseVM, useVaList, sensor));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getMinDelay");
            try {
                invokeGetMinDelay(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getMinDelay when minDelay is not explicitly configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMinDelay"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getMinDelay"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMinDelayUnknownType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 99));
            ArrayListObject empty = invokeGetSensorList(jni, baseVM, useVaList, manager, 99);
            assertEquals(0, empty.size());
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getMinDelay for unknown type: " + e.api,
                        "Sensor.getMinDelay".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMinDelayIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);

            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            try {
                invokeGetMinDelay(jni, baseVM, useVaList, plain);
                fail("expected UOE for getMinDelay on plain Sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMinDelay"));
            }

            try {
                DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getMinDelay", "()Ljava/lang/String;", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, sensor, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callObjectMethod(baseVM, sensor, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong getMinDelay signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMinDelay"));
            }

            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                BaseVM absentBase = (BaseVM) absentVm;
                DvmObject<?> absentManager = resolveSensorSystemService(
                        absentJni, absentBase, useVaList, absentVm);
                try {
                    invokeGetDefaultSensor(absentJni, absentBase, useVaList, absentManager, 1);
                    fail("expected UOE for getDefaultSensor without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDefaultSensor"));
                }
                DvmObject<?> absentPlain = absentVm.resolveClass(SENSOR_CLASS).newObject(null);
                try {
                    invokeGetMinDelay(absentJni, absentBase, useVaList, absentPlain);
                    fail("expected UOE for getMinDelay without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getMinDelay"));
                }
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_sensor event without sensors node: " + e.api,
                            "android_sensor".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreign = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                assertNotNull(foreign);
                try {
                    invokeGetMinDelay(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for getMinDelay on foreign ConfiguredSensor");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getMinDelay"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getMinDelay on isolation paths: " + e.api,
                        "Sensor.getMinDelay".equals(e.api));
            }

            int typeBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int minDelayBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getMinDelay");
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            try {
                invokeGetType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getType after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetMinDelay(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getMinDelay after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMinDelay"));
            }
            assertEquals(typeBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(minDelayBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getMinDelay"));

            emulator.set(TraceEnvironmentConfig.KEY, config);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));
            assertEquals(0, invokeGetMinDelay(jni, baseVM, useVaList, sensor));
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getMinDelay"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * 配置替换后即使 type 相同、最小延迟值改变，旧 marker 也必须被拒绝；
     * 须从当前 SensorManager 重新获取新 marker 才能读到新最小延迟。恢复 A 不会复活 B 的 marker。
     */
    private static void runSensorMinDelayMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_B_JSON);
        assertNotSame(configA, configB);
        assertEquals(Integer.valueOf(MIN_DELAY_MICROS_GENERAL_A), configA.getAndroidSensorMinDelayMicros(1));
        assertEquals(Integer.valueOf(MIN_DELAY_MICROS_GENERAL_B), configB.getAndroidSensorMinDelayMicros(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> markerA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA));
            assertEquals(MIN_DELAY_MICROS_GENERAL_A, invokeGetMinDelay(jni, baseVM, useVaList, markerA));

            int typeBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int minDelayBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getMinDelay");
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            try {
                invokeGetType(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getType on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetMinDelay(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getMinDelay on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMinDelay"));
            }
            assertEquals(typeBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(minDelayBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getMinDelay"));

            DvmObject<?> markerB = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerB);
            assertNotSame(markerA, markerB);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerB));
            assertEquals(MIN_DELAY_MICROS_GENERAL_B, invokeGetMinDelay(jni, baseVM, useVaList, markerB));

            int typeBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int minDelayBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getMinDelay");
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            try {
                invokeGetType(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getType on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetMinDelay(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getMinDelay on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMinDelay"));
            }
            assertEquals(typeBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(minDelayBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getMinDelay"));

            DvmObject<?> markerA2 = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA2);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA2));
            assertEquals(MIN_DELAY_MICROS_GENERAL_A, invokeGetMinDelay(jni, baseVM, useVaList, markerA2));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMinDelayParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"minDelaysMicros\":[]}}}",
                "android.sensors.minDelaysMicros");
        assertParseInvalid("{\"android\":{\"sensors\":{\"minDelaysMicros\":1}}}",
                "android.sensors.minDelaysMicros");
        assertParseInvalid("{\"android\":{\"sensors\":{\"minDelaysMicros\":null}}}",
                "android.sensors.minDelaysMicros");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"01\":1}}}}",
                "android.sensors.minDelaysMicros.01");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"0\":1}}}}",
                "android.sensors.minDelaysMicros.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"+1\":1}}}}",
                "android.sensors.minDelaysMicros.+1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"-1\":1}}}}",
                "android.sensors.minDelaysMicros.-1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1.0\":1}}}}",
                "android.sensors.minDelaysMicros.1.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\" 1\":1}}}}",
                "android.sensors.minDelaysMicros. 1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1 \":1}}}}",
                "android.sensors.minDelaysMicros.1 ");
        assertParseInvalid("{\"android\":{\"sensors\":{\"minDelaysMicros\":{\"65536\":1}}}}",
                "android.sensors.minDelaysMicros.65536");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":null}}}}",
                "android.sensors.minDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":1.5}}}}",
                "android.sensors.minDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":1.0}}}}",
                "android.sensors.minDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":-2}}}}",
                "android.sensors.minDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":2147483648}}}}",
                "android.sensors.minDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":\"1\"}}}}",
                "android.sensors.minDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":true}}}}",
                "android.sensors.minDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":false}}}}",
                "android.sensors.minDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":[]}}}}",
                "android.sensors.minDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":{}}}}}",
                "android.sensors.minDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"4\":1}}}}",
                "android.sensors.minDelaysMicros.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"minDelaysMicros\":{\"1\":1}}}}",
                "android.sensors.minDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"minDelaysMicros\":{\"1\":1}}}}",
                "android.sensors.minDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"extra\":1}}}",
                "android.sensors.extra");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_STATIC_JSON);
        assertTrue(ok.isAndroidSensorMinDelaysMicrosConfigured());
        assertTrue(ok.isAndroidSensorMinDelayMicrosConfigured(1));
        assertFalse(ok.isAndroidSensorMinDelayMicrosConfigured(4));
        assertEquals(Integer.valueOf(0), ok.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig oneShot = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":-1}}}}");
        assertEquals(Integer.valueOf(-1), oneShot.getAndroidSensorMinDelayMicros(1));
        assertFalse(ok.isAndroidSensorNamesConfigured());
        assertFalse(ok.isAndroidSensorVendorsConfigured());
        assertFalse(ok.isAndroidSensorVersionsConfigured());
        assertFalse(ok.isAndroidSensorStringTypesConfigured());
        assertFalse(ok.isAndroidSensorMaximumRangesConfigured());
        assertFalse(ok.isAndroidSensorResolutionsConfigured());
        assertFalse(ok.isAndroidSensorPowersConfigured());
        assertFalse(ok.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(ok.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(ok.getAndroidSensorMaxDelayMicros(1));
        assertFalse(ok.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(ok.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(ok.getAndroidSensorFifoReservedEventCount(1));
        assertFalse(ok.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(ok.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(ok.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(empty.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(empty.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(omitted.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(omitted.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig namesOnly = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        assertFalse(namesOnly.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(namesOnly.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(namesOnly.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig vendorsOnly = TraceEnvironmentConfig.parse(SENSORS_VENDORS_STATIC_JSON);
        assertFalse(vendorsOnly.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(vendorsOnly.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(vendorsOnly.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig versionsOnly = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_STATIC_JSON);
        assertFalse(versionsOnly.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(versionsOnly.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(versionsOnly.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig stringTypesOnly = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_STATIC_JSON);
        assertFalse(stringTypesOnly.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(stringTypesOnly.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(stringTypesOnly.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig maximumRangesOnly = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_STATIC_JSON);
        assertFalse(maximumRangesOnly.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(maximumRangesOnly.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(maximumRangesOnly.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig resolutionsOnly = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_STATIC_JSON);
        assertFalse(resolutionsOnly.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(resolutionsOnly.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(resolutionsOnly.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig powersOnly = TraceEnvironmentConfig.parse(SENSORS_POWERS_STATIC_JSON);
        assertFalse(powersOnly.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(powersOnly.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(powersOnly.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig maxDelaysOnly = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_STATIC_JSON);
        assertFalse(maxDelaysOnly.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(maxDelaysOnly.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(maxDelaysOnly.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig fifoOnly = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_STATIC_JSON);
        assertFalse(fifoOnly.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(fifoOnly.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(fifoOnly.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig fifoMaxOnly = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_STATIC_JSON);
        assertFalse(fifoMaxOnly.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(fifoMaxOnly.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(fifoMaxOnly.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_DYNAMIC_JSON);
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), max.getAndroidSensorMinDelayMicros(7));
        TraceEnvironmentConfig general = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_A_JSON);
        assertEquals(Integer.valueOf(MIN_DELAY_MICROS_GENERAL_A), general.getAndroidSensorMinDelayMicros(1));
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"sensors\":{\"types\":[1],"
                + "\"names\":{\"1\":\"Accel\"},\"vendors\":{\"1\":\"VendorX\"},"
                + "\"versions\":{\"1\":3},"
                + "\"stringTypes\":{\"1\":\"android.sensor.accelerometer\"},"
                + "\"maximumRanges\":{\"1\":39.24},"
                + "\"resolutions\":{\"1\":0.01},"
                + "\"powers\":{\"1\":0.13},"
                + "\"minDelaysMicros\":{\"1\":5000},"
                + "\"maxDelaysMicros\":{\"1\":1000}}}}");
        assertEquals("Accel", both.getAndroidSensorName(1));
        assertEquals("VendorX", both.getAndroidSensorVendor(1));
        assertEquals(Integer.valueOf(3), both.getAndroidSensorVersion(1));
        assertEquals("android.sensor.accelerometer", both.getAndroidSensorStringType(1));
        assertEquals(Float.valueOf(MAXIMUM_RANGE_DECIMAL_A), both.getAndroidSensorMaximumRange(1));
        assertEquals(Float.valueOf(RESOLUTION_DECIMAL_A), both.getAndroidSensorResolution(1));
        assertEquals(Float.valueOf(POWER_DECIMAL_A), both.getAndroidSensorPower(1));
        assertEquals(Integer.valueOf(MIN_DELAY_MICROS_GENERAL_A), both.getAndroidSensorMinDelayMicros(1));
        assertEquals(Integer.valueOf(MAX_DELAY_MICROS_INDEPENDENT), both.getAndroidSensorMaxDelayMicros(1));
        assertTrue(both.isAndroidSensorNameConfigured(1));
        assertTrue(both.isAndroidSensorVendorConfigured(1));
        assertTrue(both.isAndroidSensorVersionConfigured(1));
        assertTrue(both.isAndroidSensorStringTypeConfigured(1));
        assertTrue(both.isAndroidSensorMaximumRangeConfigured(1));
        assertTrue(both.isAndroidSensorResolutionConfigured(1));
        assertTrue(both.isAndroidSensorPowerConfigured(1));
        assertTrue(both.isAndroidSensorMinDelayMicrosConfigured(1));
        assertTrue(both.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertFalse(Integer.valueOf(3).equals(both.getAndroidSensorMinDelayMicros(1)));
        assertFalse(Integer.valueOf(MIN_DELAY_MICROS_GENERAL_A).equals(both.getAndroidSensorMaxDelayMicros(1)));
    }

    private static void runSensorGetMaxDelayStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_STATIC_JSON);
        assertTrue(config.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertEquals(Integer.valueOf(0), config.getAndroidSensorMaxDelayMicros(1));
        assertFalse(config.isAndroidSensorMaxDelayMicrosConfigured(4));
        assertFalse(config.isAndroidSensorNameConfigured(1));
        assertFalse(config.isAndroidSensorVendorConfigured(1));
        assertFalse(config.isAndroidSensorVersionConfigured(1));
        assertFalse(config.isAndroidSensorStringTypeConfigured(1));
        assertFalse(config.isAndroidSensorMaximumRangeConfigured(1));
        assertFalse(config.isAndroidSensorResolutionConfigured(1));
        assertFalse(config.isAndroidSensorPowerConfigured(1));
        assertFalse(config.isAndroidSensorMinDelayMicrosConfigured(1));
        assertFalse(config.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertFalse(config.isAndroidSensorFifoMaxEventCountConfigured(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(0, invokeGetMaxDelay(jni, baseVM, useVaList, sensor));
            assertEquals(0, invokeGetMaxDelayByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getMaxDelay");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,maxDelayMicros=0", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("versions"));
            assertFalse(String.valueOf(ev.value).contains("stringTypes"));
            assertFalse(String.valueOf(ev.value).contains("maximumRange"));
            assertFalse(String.valueOf(ev.value).contains("resolution"));
            assertFalse(String.valueOf(ev.value).contains("power"));
            assertFalse(String.valueOf(ev.value).contains("minDelay"));
            assertFalse(String.valueOf(ev.value).contains("fifo"));
            assertFalse(String.valueOf(ev.value).contains("version="));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay"));

            assertOtherSensorGettersUoeWhenOnlyMaxDelay(jni, baseVM, useVaList, sensor, sink);

            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertEquals(1, list.size());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, list.getValue().get(0)));
            assertEquals(0, invokeGetMaxDelay(jni, baseVM, useVaList, list.getValue().get(0)));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMaxDelayGeneralValue(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_A_JSON);
        assertEquals(Integer.valueOf(MAX_DELAY_MICROS_GENERAL_A),
                config.getAndroidSensorMaxDelayMicros(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(MAX_DELAY_MICROS_GENERAL_A, invokeGetMaxDelay(jni, baseVM, useVaList, sensor));
            assertEquals(MAX_DELAY_MICROS_GENERAL_A,
                    invokeGetMaxDelayByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getMaxDelay");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,maxDelayMicros=" + MAX_DELAY_MICROS_GENERAL_A,
                    String.valueOf(ev.value));
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMaxDelayDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_DYNAMIC_JSON);
        assertTrue(config.isAndroidSensorMaxDelayMicrosConfigured(7));
        assertFalse(config.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), config.getAndroidSensorMaxDelayMicros(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 7));
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7);
            assertEquals(1, list.size());
            DvmObject<?> sensor = list.getValue().get(0);
            assertEquals(7, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(Integer.MAX_VALUE, invokeGetMaxDelay(jni, baseVM, useVaList, sensor));
            assertEquals(Integer.MAX_VALUE, invokeGetMaxDelayByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getMaxDelay");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=7,maxDelayMicros=" + Integer.MAX_VALUE, String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("version="));
            assertFalse(String.valueOf(ev.value).contains("minDelay"));
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay"));

            DvmObject<?> typeOnly = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(typeOnly);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, typeOnly));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay");
            try {
                invokeGetMaxDelay(jni, baseVM, useVaList, typeOnly);
                fail("expected UOE for getMaxDelay on types-only sensor without maxDelay");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaxDelay"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMaxDelayNotHandledOnLiveMarker(boolean is64Bit, boolean useVaList,
                                                                   String json, int sensorType)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            assertEquals(sensorType, invokeGetType(jni, baseVM, useVaList, sensor));
            int before = countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay");
            try {
                invokeGetMaxDelay(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getMaxDelay when maxDelay is not explicitly configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaxDelay"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMaxDelayIndependentOfMin(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MIN_MAX_INDEPENDENT_JSON);
        assertEquals(Integer.valueOf(MIN_DELAY_MICROS_INDEPENDENT),
                config.getAndroidSensorMinDelayMicros(1));
        assertEquals(Integer.valueOf(MAX_DELAY_MICROS_INDEPENDENT),
                config.getAndroidSensorMaxDelayMicros(1));
        assertTrue(MAX_DELAY_MICROS_INDEPENDENT < MIN_DELAY_MICROS_INDEPENDENT);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(MIN_DELAY_MICROS_INDEPENDENT, invokeGetMinDelay(jni, baseVM, useVaList, sensor));
            assertEquals(MAX_DELAY_MICROS_INDEPENDENT, invokeGetMaxDelay(jni, baseVM, useVaList, sensor));
            assertEquals(MAX_DELAY_MICROS_INDEPENDENT,
                    invokeGetMaxDelayByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent maxEv = findLastEvent(sink.events, "android_sensor", "Sensor.getMaxDelay");
            assertNotNull(maxEv);
            assertEquals("json-config", maxEv.source);
            assertEquals("sensorType=1,maxDelayMicros=" + MAX_DELAY_MICROS_INDEPENDENT,
                    String.valueOf(maxEv.value));
            assertFalse(String.valueOf(maxEv.value).contains("minDelay"));
            CapturedEvent minEv = findLastEvent(sink.events, "android_sensor", "Sensor.getMinDelay");
            assertNotNull(minEv);
            assertEquals("sensorType=1,minDelayMicros=" + MIN_DELAY_MICROS_INDEPENDENT,
                    String.valueOf(minEv.value));
            assertFalse(String.valueOf(minEv.value).contains("maxDelay"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMaxDelayUnknownType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 99));
            ArrayListObject empty = invokeGetSensorList(jni, baseVM, useVaList, manager, 99);
            assertEquals(0, empty.size());
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getMaxDelay for unknown type: " + e.api,
                        "Sensor.getMaxDelay".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMaxDelayIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);

            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            try {
                invokeGetMaxDelay(jni, baseVM, useVaList, plain);
                fail("expected UOE for getMaxDelay on plain Sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaxDelay"));
            }

            try {
                DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getMaxDelay", "()Ljava/lang/String;", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, sensor, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callObjectMethod(baseVM, sensor, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong getMaxDelay signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaxDelay"));
            }

            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                BaseVM absentBase = (BaseVM) absentVm;
                DvmObject<?> absentManager = resolveSensorSystemService(
                        absentJni, absentBase, useVaList, absentVm);
                try {
                    invokeGetDefaultSensor(absentJni, absentBase, useVaList, absentManager, 1);
                    fail("expected UOE for getDefaultSensor without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDefaultSensor"));
                }
                DvmObject<?> absentPlain = absentVm.resolveClass(SENSOR_CLASS).newObject(null);
                try {
                    invokeGetMaxDelay(absentJni, absentBase, useVaList, absentPlain);
                    fail("expected UOE for getMaxDelay without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getMaxDelay"));
                }
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_sensor event without sensors node: " + e.api,
                            "android_sensor".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreign = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                assertNotNull(foreign);
                try {
                    invokeGetMaxDelay(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for getMaxDelay on foreign ConfiguredSensor");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getMaxDelay"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getMaxDelay on isolation paths: " + e.api,
                        "Sensor.getMaxDelay".equals(e.api));
            }

            int typeBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int maxDelayBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay");
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            try {
                invokeGetType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getType after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetMaxDelay(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getMaxDelay after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaxDelay"));
            }
            assertEquals(typeBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(maxDelayBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay"));

            emulator.set(TraceEnvironmentConfig.KEY, config);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));
            assertEquals(0, invokeGetMaxDelay(jni, baseVM, useVaList, sensor));
            assertEquals(1, countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * 配置替换后即使 type 相同、最大延迟值改变，旧 marker 也必须被拒绝；
     * 须从当前 SensorManager 重新获取新 marker 才能读到新最大延迟。恢复 A 不会复活 B 的 marker。
     */
    private static void runSensorMaxDelayMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_B_JSON);
        assertNotSame(configA, configB);
        assertEquals(Integer.valueOf(MAX_DELAY_MICROS_GENERAL_A), configA.getAndroidSensorMaxDelayMicros(1));
        assertEquals(Integer.valueOf(MAX_DELAY_MICROS_GENERAL_B), configB.getAndroidSensorMaxDelayMicros(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> markerA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA));
            assertEquals(MAX_DELAY_MICROS_GENERAL_A, invokeGetMaxDelay(jni, baseVM, useVaList, markerA));

            int typeBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int maxDelayBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay");
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            try {
                invokeGetType(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getType on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetMaxDelay(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getMaxDelay on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaxDelay"));
            }
            assertEquals(typeBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(maxDelayBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay"));

            DvmObject<?> markerB = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerB);
            assertNotSame(markerA, markerB);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerB));
            assertEquals(MAX_DELAY_MICROS_GENERAL_B, invokeGetMaxDelay(jni, baseVM, useVaList, markerB));

            int typeBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int maxDelayBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay");
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            try {
                invokeGetType(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getType on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetMaxDelay(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getMaxDelay on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMaxDelay"));
            }
            assertEquals(typeBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(maxDelayBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay"));

            DvmObject<?> markerA2 = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA2);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA2));
            assertEquals(MAX_DELAY_MICROS_GENERAL_A, invokeGetMaxDelay(jni, baseVM, useVaList, markerA2));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetMaxDelayParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"maxDelaysMicros\":[]}}}",
                "android.sensors.maxDelaysMicros");
        assertParseInvalid("{\"android\":{\"sensors\":{\"maxDelaysMicros\":1}}}",
                "android.sensors.maxDelaysMicros");
        assertParseInvalid("{\"android\":{\"sensors\":{\"maxDelaysMicros\":null}}}",
                "android.sensors.maxDelaysMicros");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"01\":1}}}}",
                "android.sensors.maxDelaysMicros.01");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"0\":1}}}}",
                "android.sensors.maxDelaysMicros.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"+1\":1}}}}",
                "android.sensors.maxDelaysMicros.+1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"-1\":1}}}}",
                "android.sensors.maxDelaysMicros.-1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1.0\":1}}}}",
                "android.sensors.maxDelaysMicros.1.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\" 1\":1}}}}",
                "android.sensors.maxDelaysMicros. 1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1 \":1}}}}",
                "android.sensors.maxDelaysMicros.1 ");
        assertParseInvalid("{\"android\":{\"sensors\":{\"maxDelaysMicros\":{\"65536\":1}}}}",
                "android.sensors.maxDelaysMicros.65536");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1\":null}}}}",
                "android.sensors.maxDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1\":1.5}}}}",
                "android.sensors.maxDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1\":1.0}}}}",
                "android.sensors.maxDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1\":-1}}}}",
                "android.sensors.maxDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1\":2147483648}}}}",
                "android.sensors.maxDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1\":\"1\"}}}}",
                "android.sensors.maxDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1\":true}}}}",
                "android.sensors.maxDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1\":false}}}}",
                "android.sensors.maxDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1\":[]}}}}",
                "android.sensors.maxDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"1\":{}}}}}",
                "android.sensors.maxDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"maxDelaysMicros\":{\"4\":1}}}}",
                "android.sensors.maxDelaysMicros.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"maxDelaysMicros\":{\"1\":1}}}}",
                "android.sensors.maxDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"maxDelaysMicros\":{\"1\":1}}}}",
                "android.sensors.maxDelaysMicros.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"extra\":1}}}",
                "android.sensors.extra");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_STATIC_JSON);
        assertTrue(ok.isAndroidSensorMaxDelaysMicrosConfigured());
        assertTrue(ok.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertFalse(ok.isAndroidSensorMaxDelayMicrosConfigured(4));
        assertEquals(Integer.valueOf(0), ok.getAndroidSensorMaxDelayMicros(1));
        assertFalse(ok.isAndroidSensorNamesConfigured());
        assertFalse(ok.isAndroidSensorVendorsConfigured());
        assertFalse(ok.isAndroidSensorVersionsConfigured());
        assertFalse(ok.isAndroidSensorStringTypesConfigured());
        assertFalse(ok.isAndroidSensorMaximumRangesConfigured());
        assertFalse(ok.isAndroidSensorResolutionsConfigured());
        assertFalse(ok.isAndroidSensorPowersConfigured());
        assertFalse(ok.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(ok.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(ok.getAndroidSensorMinDelayMicros(1));
        assertFalse(ok.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(ok.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(ok.getAndroidSensorFifoReservedEventCount(1));
        assertFalse(ok.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(ok.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(ok.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(empty.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(empty.getAndroidSensorMaxDelayMicros(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(omitted.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(omitted.getAndroidSensorMaxDelayMicros(1));
        TraceEnvironmentConfig namesOnly = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        assertFalse(namesOnly.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(namesOnly.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(namesOnly.getAndroidSensorMaxDelayMicros(1));
        TraceEnvironmentConfig vendorsOnly = TraceEnvironmentConfig.parse(SENSORS_VENDORS_STATIC_JSON);
        assertFalse(vendorsOnly.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(vendorsOnly.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(vendorsOnly.getAndroidSensorMaxDelayMicros(1));
        TraceEnvironmentConfig versionsOnly = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_STATIC_JSON);
        assertFalse(versionsOnly.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(versionsOnly.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(versionsOnly.getAndroidSensorMaxDelayMicros(1));
        TraceEnvironmentConfig stringTypesOnly = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_STATIC_JSON);
        assertFalse(stringTypesOnly.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(stringTypesOnly.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(stringTypesOnly.getAndroidSensorMaxDelayMicros(1));
        TraceEnvironmentConfig maximumRangesOnly = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_STATIC_JSON);
        assertFalse(maximumRangesOnly.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(maximumRangesOnly.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(maximumRangesOnly.getAndroidSensorMaxDelayMicros(1));
        TraceEnvironmentConfig resolutionsOnly = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_STATIC_JSON);
        assertFalse(resolutionsOnly.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(resolutionsOnly.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(resolutionsOnly.getAndroidSensorMaxDelayMicros(1));
        TraceEnvironmentConfig powersOnly = TraceEnvironmentConfig.parse(SENSORS_POWERS_STATIC_JSON);
        assertFalse(powersOnly.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(powersOnly.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(powersOnly.getAndroidSensorMaxDelayMicros(1));
        TraceEnvironmentConfig minDelaysOnly = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_STATIC_JSON);
        assertFalse(minDelaysOnly.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(minDelaysOnly.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(minDelaysOnly.getAndroidSensorMaxDelayMicros(1));
        TraceEnvironmentConfig fifoOnly = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_STATIC_JSON);
        assertFalse(fifoOnly.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(fifoOnly.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(fifoOnly.getAndroidSensorMaxDelayMicros(1));
        TraceEnvironmentConfig fifoMaxOnly = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_STATIC_JSON);
        assertFalse(fifoMaxOnly.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(fifoMaxOnly.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(fifoMaxOnly.getAndroidSensorMaxDelayMicros(1));
        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_DYNAMIC_JSON);
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), max.getAndroidSensorMaxDelayMicros(7));
        TraceEnvironmentConfig general = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_A_JSON);
        assertEquals(Integer.valueOf(MAX_DELAY_MICROS_GENERAL_A), general.getAndroidSensorMaxDelayMicros(1));
        TraceEnvironmentConfig independent = TraceEnvironmentConfig.parse(SENSORS_MIN_MAX_INDEPENDENT_JSON);
        assertEquals(Integer.valueOf(MIN_DELAY_MICROS_INDEPENDENT),
                independent.getAndroidSensorMinDelayMicros(1));
        assertEquals(Integer.valueOf(MAX_DELAY_MICROS_INDEPENDENT),
                independent.getAndroidSensorMaxDelayMicros(1));
        assertFalse(Integer.valueOf(MIN_DELAY_MICROS_INDEPENDENT)
                .equals(independent.getAndroidSensorMaxDelayMicros(1)));
    }

    private static void runSensorGetFifoReservedEventCountStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_STATIC_JSON);
        assertTrue(config.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertEquals(Integer.valueOf(0), config.getAndroidSensorFifoReservedEventCount(1));
        assertFalse(config.isAndroidSensorFifoReservedEventCountConfigured(4));
        assertFalse(config.isAndroidSensorNameConfigured(1));
        assertFalse(config.isAndroidSensorVendorConfigured(1));
        assertFalse(config.isAndroidSensorVersionConfigured(1));
        assertFalse(config.isAndroidSensorStringTypeConfigured(1));
        assertFalse(config.isAndroidSensorMaximumRangeConfigured(1));
        assertFalse(config.isAndroidSensorResolutionConfigured(1));
        assertFalse(config.isAndroidSensorPowerConfigured(1));
        assertFalse(config.isAndroidSensorMinDelayMicrosConfigured(1));
        assertFalse(config.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertFalse(config.isAndroidSensorFifoMaxEventCountConfigured(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(0, invokeGetFifoReservedEventCount(jni, baseVM, useVaList, sensor));
            assertEquals(0, invokeGetFifoReservedEventCountByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,fifoReservedEventCount=0", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("versions"));
            assertFalse(String.valueOf(ev.value).contains("stringTypes"));
            assertFalse(String.valueOf(ev.value).contains("maximumRange"));
            assertFalse(String.valueOf(ev.value).contains("resolution"));
            assertFalse(String.valueOf(ev.value).contains("power"));
            assertFalse(String.valueOf(ev.value).contains("minDelay"));
            assertFalse(String.valueOf(ev.value).contains("maxDelay"));
            assertFalse(String.valueOf(ev.value).contains("version="));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount"));

            assertOtherSensorGettersUoeWhenOnlyFifoReserved(jni, baseVM, useVaList, sensor, sink);

            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertEquals(1, list.size());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, list.getValue().get(0)));
            assertEquals(0, invokeGetFifoReservedEventCount(jni, baseVM, useVaList,
                    list.getValue().get(0)));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetFifoReservedEventCountGeneralValue(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_A_JSON);
        assertEquals(Integer.valueOf(FIFO_RESERVED_GENERAL_A),
                config.getAndroidSensorFifoReservedEventCount(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(FIFO_RESERVED_GENERAL_A,
                    invokeGetFifoReservedEventCount(jni, baseVM, useVaList, sensor));
            assertEquals(FIFO_RESERVED_GENERAL_A,
                    invokeGetFifoReservedEventCountByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,fifoReservedEventCount=" + FIFO_RESERVED_GENERAL_A,
                    String.valueOf(ev.value));
            assertEquals(2, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetFifoReservedEventCountDynamicOnlyType(boolean is64Bit,
                                                                          boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_DYNAMIC_JSON);
        assertTrue(config.isAndroidSensorFifoReservedEventCountConfigured(7));
        assertFalse(config.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE),
                config.getAndroidSensorFifoReservedEventCount(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 7));
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7);
            assertEquals(1, list.size());
            DvmObject<?> sensor = list.getValue().get(0);
            assertEquals(7, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(Integer.MAX_VALUE,
                    invokeGetFifoReservedEventCount(jni, baseVM, useVaList, sensor));
            assertEquals(Integer.MAX_VALUE,
                    invokeGetFifoReservedEventCountByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=7,fifoReservedEventCount=" + Integer.MAX_VALUE,
                    String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("version="));
            assertFalse(String.valueOf(ev.value).contains("minDelay"));
            assertFalse(String.valueOf(ev.value).contains("maxDelay"));
            assertEquals(2, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount"));

            DvmObject<?> typeOnly = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(typeOnly);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, typeOnly));
            int before = countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount");
            try {
                invokeGetFifoReservedEventCount(jni, baseVM, useVaList, typeOnly);
                fail("expected UOE for getFifoReservedEventCount on types-only sensor without fifo");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoReservedEventCount"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetFifoReservedEventCountNotHandledOnLiveMarker(
            boolean is64Bit, boolean useVaList, String json, int sensorType) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            assertEquals(sensorType, invokeGetType(jni, baseVM, useVaList, sensor));
            int before = countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount");
            try {
                invokeGetFifoReservedEventCount(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getFifoReservedEventCount when fifo is not explicitly configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoReservedEventCount"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetFifoReservedEventCountUnknownType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 99));
            ArrayListObject empty = invokeGetSensorList(jni, baseVM, useVaList, manager, 99);
            assertEquals(0, empty.size());
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getFifoReservedEventCount for unknown type: " + e.api,
                        "Sensor.getFifoReservedEventCount".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetFifoReservedEventCountIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);

            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            try {
                invokeGetFifoReservedEventCount(jni, baseVM, useVaList, plain);
                fail("expected UOE for getFifoReservedEventCount on plain Sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoReservedEventCount"));
            }

            try {
                DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getFifoReservedEventCount",
                        "()Ljava/lang/String;", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, sensor, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callObjectMethod(baseVM, sensor, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong getFifoReservedEventCount signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoReservedEventCount"));
            }

            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                BaseVM absentBase = (BaseVM) absentVm;
                DvmObject<?> absentManager = resolveSensorSystemService(
                        absentJni, absentBase, useVaList, absentVm);
                try {
                    invokeGetDefaultSensor(absentJni, absentBase, useVaList, absentManager, 1);
                    fail("expected UOE for getDefaultSensor without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDefaultSensor"));
                }
                DvmObject<?> absentPlain = absentVm.resolveClass(SENSOR_CLASS).newObject(null);
                try {
                    invokeGetFifoReservedEventCount(absentJni, absentBase, useVaList, absentPlain);
                    fail("expected UOE for getFifoReservedEventCount without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getFifoReservedEventCount"));
                }
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_sensor event without sensors node: " + e.api,
                            "android_sensor".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreign = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                assertNotNull(foreign);
                try {
                    invokeGetFifoReservedEventCount(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for getFifoReservedEventCount on foreign ConfiguredSensor");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getFifoReservedEventCount"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getFifoReservedEventCount on isolation paths: " + e.api,
                        "Sensor.getFifoReservedEventCount".equals(e.api));
            }

            int typeBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int fifoBeforeStale = countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount");
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            try {
                invokeGetType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getType after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetFifoReservedEventCount(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getFifoReservedEventCount after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoReservedEventCount"));
            }
            assertEquals(typeBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(fifoBeforeStale, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount"));

            emulator.set(TraceEnvironmentConfig.KEY, config);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));
            assertEquals(0, invokeGetFifoReservedEventCount(jni, baseVM, useVaList, sensor));
            assertEquals(1, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * 配置替换后即使 type 相同、FIFO 预留事件数改变，旧 marker 也必须被拒绝；
     * 须从当前 SensorManager 重新获取新 marker 才能读到新值。恢复 A 不会复活 B 的 marker。
     */
    private static void runSensorFifoReservedMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_B_JSON);
        assertNotSame(configA, configB);
        assertEquals(Integer.valueOf(FIFO_RESERVED_GENERAL_A),
                configA.getAndroidSensorFifoReservedEventCount(1));
        assertEquals(Integer.valueOf(FIFO_RESERVED_GENERAL_B),
                configB.getAndroidSensorFifoReservedEventCount(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> markerA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA));
            assertEquals(FIFO_RESERVED_GENERAL_A,
                    invokeGetFifoReservedEventCount(jni, baseVM, useVaList, markerA));

            int typeBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int fifoBeforeB = countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount");
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            try {
                invokeGetType(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getType on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetFifoReservedEventCount(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getFifoReservedEventCount on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoReservedEventCount"));
            }
            assertEquals(typeBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(fifoBeforeB, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount"));

            DvmObject<?> markerB = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerB);
            assertNotSame(markerA, markerB);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerB));
            assertEquals(FIFO_RESERVED_GENERAL_B,
                    invokeGetFifoReservedEventCount(jni, baseVM, useVaList, markerB));

            int typeBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int fifoBeforeA = countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount");
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            try {
                invokeGetType(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getType on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetFifoReservedEventCount(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getFifoReservedEventCount on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoReservedEventCount"));
            }
            assertEquals(typeBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(fifoBeforeA, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount"));

            DvmObject<?> markerA2 = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA2);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA2));
            assertEquals(FIFO_RESERVED_GENERAL_A,
                    invokeGetFifoReservedEventCount(jni, baseVM, useVaList, markerA2));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetFifoReservedEventCountParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"fifoReservedEventCounts\":[]}}}",
                "android.sensors.fifoReservedEventCounts");
        assertParseInvalid("{\"android\":{\"sensors\":{\"fifoReservedEventCounts\":1}}}",
                "android.sensors.fifoReservedEventCounts");
        assertParseInvalid("{\"android\":{\"sensors\":{\"fifoReservedEventCounts\":null}}}",
                "android.sensors.fifoReservedEventCounts");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"01\":1}}}}",
                "android.sensors.fifoReservedEventCounts.01");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"0\":1}}}}",
                "android.sensors.fifoReservedEventCounts.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"+1\":1}}}}",
                "android.sensors.fifoReservedEventCounts.+1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"-1\":1}}}}",
                "android.sensors.fifoReservedEventCounts.-1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1.0\":1}}}}",
                "android.sensors.fifoReservedEventCounts.1.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\" 1\":1}}}}",
                "android.sensors.fifoReservedEventCounts. 1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1 \":1}}}}",
                "android.sensors.fifoReservedEventCounts.1 ");
        assertParseInvalid("{\"android\":{\"sensors\":{\"fifoReservedEventCounts\":{\"65536\":1}}}}",
                "android.sensors.fifoReservedEventCounts.65536");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1\":null}}}}",
                "android.sensors.fifoReservedEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1\":1.5}}}}",
                "android.sensors.fifoReservedEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1\":1.0}}}}",
                "android.sensors.fifoReservedEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1\":-1}}}}",
                "android.sensors.fifoReservedEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1\":2147483648}}}}",
                "android.sensors.fifoReservedEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1\":\"1\"}}}}",
                "android.sensors.fifoReservedEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1\":true}}}}",
                "android.sensors.fifoReservedEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1\":false}}}}",
                "android.sensors.fifoReservedEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1\":[]}}}}",
                "android.sensors.fifoReservedEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"1\":{}}}}}",
                "android.sensors.fifoReservedEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoReservedEventCounts\":{\"4\":1}}}}",
                "android.sensors.fifoReservedEventCounts.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"fifoReservedEventCounts\":{\"1\":1}}}}",
                "android.sensors.fifoReservedEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"fifoReservedEventCounts\":{\"1\":1}}}}",
                "android.sensors.fifoReservedEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"extra\":1}}}",
                "android.sensors.extra");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_STATIC_JSON);
        assertTrue(ok.isAndroidSensorFifoReservedEventCountsConfigured());
        assertTrue(ok.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertFalse(ok.isAndroidSensorFifoReservedEventCountConfigured(4));
        assertEquals(Integer.valueOf(0), ok.getAndroidSensorFifoReservedEventCount(1));
        assertFalse(ok.isAndroidSensorNamesConfigured());
        assertFalse(ok.isAndroidSensorVendorsConfigured());
        assertFalse(ok.isAndroidSensorVersionsConfigured());
        assertFalse(ok.isAndroidSensorStringTypesConfigured());
        assertFalse(ok.isAndroidSensorMaximumRangesConfigured());
        assertFalse(ok.isAndroidSensorResolutionsConfigured());
        assertFalse(ok.isAndroidSensorPowersConfigured());
        assertFalse(ok.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(ok.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(ok.getAndroidSensorMinDelayMicros(1));
        assertFalse(ok.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(ok.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(ok.getAndroidSensorMaxDelayMicros(1));
        assertFalse(ok.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(ok.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(ok.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(empty.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(empty.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(omitted.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(omitted.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig namesOnly = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        assertFalse(namesOnly.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(namesOnly.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(namesOnly.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig vendorsOnly = TraceEnvironmentConfig.parse(SENSORS_VENDORS_STATIC_JSON);
        assertFalse(vendorsOnly.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(vendorsOnly.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(vendorsOnly.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig versionsOnly = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_STATIC_JSON);
        assertFalse(versionsOnly.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(versionsOnly.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(versionsOnly.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig stringTypesOnly = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_STATIC_JSON);
        assertFalse(stringTypesOnly.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(stringTypesOnly.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(stringTypesOnly.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig maximumRangesOnly = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_STATIC_JSON);
        assertFalse(maximumRangesOnly.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(maximumRangesOnly.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(maximumRangesOnly.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig resolutionsOnly = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_STATIC_JSON);
        assertFalse(resolutionsOnly.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(resolutionsOnly.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(resolutionsOnly.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig powersOnly = TraceEnvironmentConfig.parse(SENSORS_POWERS_STATIC_JSON);
        assertFalse(powersOnly.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(powersOnly.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(powersOnly.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig minDelaysOnly = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_STATIC_JSON);
        assertFalse(minDelaysOnly.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(minDelaysOnly.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(minDelaysOnly.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig maxDelaysOnly = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_STATIC_JSON);
        assertFalse(maxDelaysOnly.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(maxDelaysOnly.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(maxDelaysOnly.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig fifoMaxOnly = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_STATIC_JSON);
        assertFalse(fifoMaxOnly.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(fifoMaxOnly.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(fifoMaxOnly.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_DYNAMIC_JSON);
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), max.getAndroidSensorFifoReservedEventCount(7));
        TraceEnvironmentConfig general = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_A_JSON);
        assertEquals(Integer.valueOf(FIFO_RESERVED_GENERAL_A),
                general.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"sensors\":{\"types\":[1],"
                + "\"maxDelaysMicros\":{\"1\":1000},"
                + "\"fifoReservedEventCounts\":{\"1\":128}}}}");
        assertEquals(Integer.valueOf(MAX_DELAY_MICROS_INDEPENDENT),
                both.getAndroidSensorMaxDelayMicros(1));
        assertEquals(Integer.valueOf(FIFO_RESERVED_GENERAL_A),
                both.getAndroidSensorFifoReservedEventCount(1));
        assertTrue(both.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertTrue(both.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertFalse(Integer.valueOf(MAX_DELAY_MICROS_INDEPENDENT)
                .equals(both.getAndroidSensorFifoReservedEventCount(1)));
    }

    private static void assertOtherSensorGettersUoeWhenOnlyMinDelay(AbstractJni jni, BaseVM baseVM,
                                                                   boolean useVaList,
                                                                   DvmObject<?> sensor,
                                                                   CapturingSink sink) {
        int nameBefore = countEvents(sink.events, "android_sensor", "Sensor.getName");
        try {
            invokeGetName(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getName when only minDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getName"));
        }
        assertEquals(nameBefore, countEvents(sink.events, "android_sensor", "Sensor.getName"));

        int vendorBefore = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
        try {
            invokeGetVendor(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getVendor when only minDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getVendor"));
        }
        assertEquals(vendorBefore, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

        int versionBefore = countEvents(sink.events, "android_sensor", "Sensor.getVersion");
        try {
            invokeGetVersion(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getVersion when only minDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getVersion"));
        }
        assertEquals(versionBefore, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));

        int stringTypeBefore = countEvents(sink.events, "android_sensor", "Sensor.getStringType");
        try {
            invokeGetStringType(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getStringType when only minDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getStringType"));
        }
        assertEquals(stringTypeBefore, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));

        int rangeBefore = countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange");
        try {
            invokeGetMaximumRange(jni, baseVM, sensor);
            fail("expected UOE for getMaximumRange when only minDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getMaximumRange"));
        }
        assertEquals(rangeBefore, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));

        int resolutionBefore = countEvents(sink.events, "android_sensor", "Sensor.getResolution");
        try {
            invokeGetResolution(jni, baseVM, sensor);
            fail("expected UOE for getResolution when only minDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getResolution"));
        }
        assertEquals(resolutionBefore, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));

        int powerBefore = countEvents(sink.events, "android_sensor", "Sensor.getPower");
        try {
            invokeGetPower(jni, baseVM, sensor);
            fail("expected UOE for getPower when only minDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getPower"));
        }
        assertEquals(powerBefore, countEvents(sink.events, "android_sensor", "Sensor.getPower"));

        int maxDelayBefore = countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay");
        try {
            invokeGetMaxDelay(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getMaxDelay when only minDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getMaxDelay"));
        }
        assertEquals(maxDelayBefore, countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay"));

        int fifoBefore = countEvents(sink.events, "android_sensor", "Sensor.getFifoReservedEventCount");
        try {
            invokeGetFifoReservedEventCount(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getFifoReservedEventCount when only minDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getFifoReservedEventCount"));
        }
        assertEquals(fifoBefore, countEvents(sink.events, "android_sensor",
                "Sensor.getFifoReservedEventCount"));

        int fifoMaxBefore = countEvents(sink.events, "android_sensor", "Sensor.getFifoMaxEventCount");
        try {
            invokeGetFifoMaxEventCount(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getFifoMaxEventCount when only minDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getFifoMaxEventCount"));
        }
        assertEquals(fifoMaxBefore, countEvents(sink.events, "android_sensor",
                "Sensor.getFifoMaxEventCount"));
    }

    private static void assertOtherSensorGettersUoeWhenOnlyMaxDelay(AbstractJni jni, BaseVM baseVM,
                                                                   boolean useVaList,
                                                                   DvmObject<?> sensor,
                                                                   CapturingSink sink) {
        int nameBefore = countEvents(sink.events, "android_sensor", "Sensor.getName");
        try {
            invokeGetName(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getName when only maxDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getName"));
        }
        assertEquals(nameBefore, countEvents(sink.events, "android_sensor", "Sensor.getName"));

        int vendorBefore = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
        try {
            invokeGetVendor(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getVendor when only maxDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getVendor"));
        }
        assertEquals(vendorBefore, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

        int versionBefore = countEvents(sink.events, "android_sensor", "Sensor.getVersion");
        try {
            invokeGetVersion(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getVersion when only maxDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getVersion"));
        }
        assertEquals(versionBefore, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));

        int stringTypeBefore = countEvents(sink.events, "android_sensor", "Sensor.getStringType");
        try {
            invokeGetStringType(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getStringType when only maxDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getStringType"));
        }
        assertEquals(stringTypeBefore, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));

        int rangeBefore = countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange");
        try {
            invokeGetMaximumRange(jni, baseVM, sensor);
            fail("expected UOE for getMaximumRange when only maxDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getMaximumRange"));
        }
        assertEquals(rangeBefore, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));

        int resolutionBefore = countEvents(sink.events, "android_sensor", "Sensor.getResolution");
        try {
            invokeGetResolution(jni, baseVM, sensor);
            fail("expected UOE for getResolution when only maxDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getResolution"));
        }
        assertEquals(resolutionBefore, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));

        int powerBefore = countEvents(sink.events, "android_sensor", "Sensor.getPower");
        try {
            invokeGetPower(jni, baseVM, sensor);
            fail("expected UOE for getPower when only maxDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getPower"));
        }
        assertEquals(powerBefore, countEvents(sink.events, "android_sensor", "Sensor.getPower"));

        int minDelayBefore = countEvents(sink.events, "android_sensor", "Sensor.getMinDelay");
        try {
            invokeGetMinDelay(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getMinDelay when only maxDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getMinDelay"));
        }
        assertEquals(minDelayBefore, countEvents(sink.events, "android_sensor", "Sensor.getMinDelay"));

        int fifoBefore = countEvents(sink.events, "android_sensor", "Sensor.getFifoReservedEventCount");
        try {
            invokeGetFifoReservedEventCount(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getFifoReservedEventCount when only maxDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getFifoReservedEventCount"));
        }
        assertEquals(fifoBefore, countEvents(sink.events, "android_sensor",
                "Sensor.getFifoReservedEventCount"));

        int fifoMaxBefore = countEvents(sink.events, "android_sensor", "Sensor.getFifoMaxEventCount");
        try {
            invokeGetFifoMaxEventCount(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getFifoMaxEventCount when only maxDelaysMicros is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getFifoMaxEventCount"));
        }
        assertEquals(fifoMaxBefore, countEvents(sink.events, "android_sensor",
                "Sensor.getFifoMaxEventCount"));
    }

    private static void assertOtherSensorGettersUoeWhenOnlyFifoReserved(AbstractJni jni, BaseVM baseVM,
                                                                       boolean useVaList,
                                                                       DvmObject<?> sensor,
                                                                       CapturingSink sink) {
        int nameBefore = countEvents(sink.events, "android_sensor", "Sensor.getName");
        try {
            invokeGetName(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getName when only fifoReservedEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getName"));
        }
        assertEquals(nameBefore, countEvents(sink.events, "android_sensor", "Sensor.getName"));

        int vendorBefore = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
        try {
            invokeGetVendor(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getVendor when only fifoReservedEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getVendor"));
        }
        assertEquals(vendorBefore, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

        int versionBefore = countEvents(sink.events, "android_sensor", "Sensor.getVersion");
        try {
            invokeGetVersion(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getVersion when only fifoReservedEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getVersion"));
        }
        assertEquals(versionBefore, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));

        int stringTypeBefore = countEvents(sink.events, "android_sensor", "Sensor.getStringType");
        try {
            invokeGetStringType(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getStringType when only fifoReservedEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getStringType"));
        }
        assertEquals(stringTypeBefore, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));

        int rangeBefore = countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange");
        try {
            invokeGetMaximumRange(jni, baseVM, sensor);
            fail("expected UOE for getMaximumRange when only fifoReservedEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getMaximumRange"));
        }
        assertEquals(rangeBefore, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));

        int resolutionBefore = countEvents(sink.events, "android_sensor", "Sensor.getResolution");
        try {
            invokeGetResolution(jni, baseVM, sensor);
            fail("expected UOE for getResolution when only fifoReservedEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getResolution"));
        }
        assertEquals(resolutionBefore, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));

        int powerBefore = countEvents(sink.events, "android_sensor", "Sensor.getPower");
        try {
            invokeGetPower(jni, baseVM, sensor);
            fail("expected UOE for getPower when only fifoReservedEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getPower"));
        }
        assertEquals(powerBefore, countEvents(sink.events, "android_sensor", "Sensor.getPower"));

        int minDelayBefore = countEvents(sink.events, "android_sensor", "Sensor.getMinDelay");
        try {
            invokeGetMinDelay(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getMinDelay when only fifoReservedEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getMinDelay"));
        }
        assertEquals(minDelayBefore, countEvents(sink.events, "android_sensor", "Sensor.getMinDelay"));

        int maxDelayBefore = countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay");
        try {
            invokeGetMaxDelay(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getMaxDelay when only fifoReservedEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getMaxDelay"));
        }
        assertEquals(maxDelayBefore, countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay"));

        int fifoMaxBefore = countEvents(sink.events, "android_sensor", "Sensor.getFifoMaxEventCount");
        try {
            invokeGetFifoMaxEventCount(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getFifoMaxEventCount when only fifoReservedEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getFifoMaxEventCount"));
        }
        assertEquals(fifoMaxBefore, countEvents(sink.events, "android_sensor",
                "Sensor.getFifoMaxEventCount"));

        int wakeUpBefore = countEvents(sink.events, "android_sensor", "Sensor.isWakeUpSensor");
        try {
            invokeIsWakeUpSensor(jni, baseVM, useVaList, sensor);
            fail("expected UOE for isWakeUpSensor when only fifoReservedEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("isWakeUpSensor"));
        }
        assertEquals(wakeUpBefore, countEvents(sink.events, "android_sensor",
                "Sensor.isWakeUpSensor"));

        expectSensorIntUoe(jni, baseVM, useVaList, sensor, "getId", "Sensor.getId", sink);
        expectSensorIntUoe(jni, baseVM, useVaList, sensor, "getReportingMode",
                "Sensor.getReportingMode", sink);
        expectSensorBooleanUoe(jni, baseVM, useVaList, sensor, "isDynamicSensor",
                "Sensor.isDynamicSensor", sink);
        expectSensorObjectUoe(jni, baseVM, useVaList, sensor, "getRequiredPermission",
                "()Ljava/lang/String;", "Sensor.getRequiredPermission", sink);
        expectSensorBooleanUoe(jni, baseVM, useVaList, sensor, "isAdditionalInfoSupported",
                "Sensor.isAdditionalInfoSupported", sink);
    }

    private static void runSensorGetFifoMaxEventCountStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_STATIC_JSON);
        assertTrue(config.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertEquals(Integer.valueOf(0), config.getAndroidSensorFifoMaxEventCount(1));
        assertFalse(config.isAndroidSensorFifoMaxEventCountConfigured(4));
        assertFalse(config.isAndroidSensorNameConfigured(1));
        assertFalse(config.isAndroidSensorVendorConfigured(1));
        assertFalse(config.isAndroidSensorVersionConfigured(1));
        assertFalse(config.isAndroidSensorStringTypeConfigured(1));
        assertFalse(config.isAndroidSensorMaximumRangeConfigured(1));
        assertFalse(config.isAndroidSensorResolutionConfigured(1));
        assertFalse(config.isAndroidSensorPowerConfigured(1));
        assertFalse(config.isAndroidSensorMinDelayMicrosConfigured(1));
        assertFalse(config.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertFalse(config.isAndroidSensorFifoReservedEventCountConfigured(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(0, invokeGetFifoMaxEventCount(jni, baseVM, useVaList, sensor));
            assertEquals(0, invokeGetFifoMaxEventCountByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,fifoMaxEventCount=0", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("versions"));
            assertFalse(String.valueOf(ev.value).contains("stringTypes"));
            assertFalse(String.valueOf(ev.value).contains("maximumRange"));
            assertFalse(String.valueOf(ev.value).contains("resolution"));
            assertFalse(String.valueOf(ev.value).contains("power"));
            assertFalse(String.valueOf(ev.value).contains("minDelay"));
            assertFalse(String.valueOf(ev.value).contains("maxDelay"));
            assertFalse(String.valueOf(ev.value).contains("fifoReserved"));
            assertFalse(String.valueOf(ev.value).contains("version="));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount"));

            assertOtherSensorGettersUoeWhenOnlyFifoMax(jni, baseVM, useVaList, sensor, sink);

            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertEquals(1, list.size());
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, list.getValue().get(0)));
            assertEquals(0, invokeGetFifoMaxEventCount(jni, baseVM, useVaList,
                    list.getValue().get(0)));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetFifoMaxEventCountGeneralValue(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_A_JSON);
        assertEquals(Integer.valueOf(FIFO_MAX_GENERAL_A),
                config.getAndroidSensorFifoMaxEventCount(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(FIFO_MAX_GENERAL_A,
                    invokeGetFifoMaxEventCount(jni, baseVM, useVaList, sensor));
            assertEquals(FIFO_MAX_GENERAL_A,
                    invokeGetFifoMaxEventCountByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,fifoMaxEventCount=" + FIFO_MAX_GENERAL_A,
                    String.valueOf(ev.value));
            assertEquals(2, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetFifoMaxEventCountDynamicOnlyType(boolean is64Bit,
                                                                     boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_DYNAMIC_JSON);
        assertTrue(config.isAndroidSensorFifoMaxEventCountConfigured(7));
        assertFalse(config.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE),
                config.getAndroidSensorFifoMaxEventCount(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 7));
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7);
            assertEquals(1, list.size());
            DvmObject<?> sensor = list.getValue().get(0);
            assertEquals(7, invokeGetType(jni, baseVM, useVaList, sensor));

            assertEquals(Integer.MAX_VALUE,
                    invokeGetFifoMaxEventCount(jni, baseVM, useVaList, sensor));
            assertEquals(Integer.MAX_VALUE,
                    invokeGetFifoMaxEventCountByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=7,fifoMaxEventCount=" + Integer.MAX_VALUE,
                    String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertFalse(String.valueOf(ev.value).contains("vendors"));
            assertFalse(String.valueOf(ev.value).contains("version="));
            assertFalse(String.valueOf(ev.value).contains("minDelay"));
            assertFalse(String.valueOf(ev.value).contains("maxDelay"));
            assertFalse(String.valueOf(ev.value).contains("fifoReserved"));
            assertEquals(2, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount"));

            DvmObject<?> typeOnly = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(typeOnly);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, typeOnly));
            int before = countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount");
            try {
                invokeGetFifoMaxEventCount(jni, baseVM, useVaList, typeOnly);
                fail("expected UOE for getFifoMaxEventCount on types-only sensor without fifo max");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoMaxEventCount"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetFifoMaxEventCountNotHandledOnLiveMarker(
            boolean is64Bit, boolean useVaList, String json, int sensorType) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            assertEquals(sensorType, invokeGetType(jni, baseVM, useVaList, sensor));
            int before = countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount");
            try {
                invokeGetFifoMaxEventCount(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getFifoMaxEventCount when fifo max is not explicitly configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoMaxEventCount"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetFifoMaxEventCountUnknownType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 99));
            ArrayListObject empty = invokeGetSensorList(jni, baseVM, useVaList, manager, 99);
            assertEquals(0, empty.size());
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getFifoMaxEventCount for unknown type: " + e.api,
                        "Sensor.getFifoMaxEventCount".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetFifoMaxEventCountIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);

            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            try {
                invokeGetFifoMaxEventCount(jni, baseVM, useVaList, plain);
                fail("expected UOE for getFifoMaxEventCount on plain Sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoMaxEventCount"));
            }

            try {
                DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getFifoMaxEventCount",
                        "()Ljava/lang/String;", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, sensor, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callObjectMethod(baseVM, sensor, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong getFifoMaxEventCount signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoMaxEventCount"));
            }

            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                BaseVM absentBase = (BaseVM) absentVm;
                DvmObject<?> absentManager = resolveSensorSystemService(
                        absentJni, absentBase, useVaList, absentVm);
                try {
                    invokeGetDefaultSensor(absentJni, absentBase, useVaList, absentManager, 1);
                    fail("expected UOE for getDefaultSensor without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDefaultSensor"));
                }
                DvmObject<?> absentPlain = absentVm.resolveClass(SENSOR_CLASS).newObject(null);
                try {
                    invokeGetFifoMaxEventCount(absentJni, absentBase, useVaList, absentPlain);
                    fail("expected UOE for getFifoMaxEventCount without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getFifoMaxEventCount"));
                }
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_sensor event without sensors node: " + e.api,
                            "android_sensor".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreign = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                assertNotNull(foreign);
                try {
                    invokeGetFifoMaxEventCount(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for getFifoMaxEventCount on foreign ConfiguredSensor");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getFifoMaxEventCount"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected Sensor.getFifoMaxEventCount on isolation paths: " + e.api,
                        "Sensor.getFifoMaxEventCount".equals(e.api));
            }

            int typeBeforeStale = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int fifoBeforeStale = countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount");
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            try {
                invokeGetType(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getType after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetFifoMaxEventCount(jni, baseVM, useVaList, sensor);
                fail("expected UOE for getFifoMaxEventCount after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoMaxEventCount"));
            }
            assertEquals(typeBeforeStale, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(fifoBeforeStale, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount"));

            emulator.set(TraceEnvironmentConfig.KEY, config);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));
            assertEquals(0, invokeGetFifoMaxEventCount(jni, baseVM, useVaList, sensor));
            assertEquals(1, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * 配置替换后即使 type 相同、FIFO 最大事件数改变，旧 marker 也必须被拒绝；
     * 须从当前 SensorManager 重新获取新 marker 才能读到新值。恢复 A 不会复活 B 的 marker。
     */
    private static void runSensorFifoMaxMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_B_JSON);
        assertNotSame(configA, configB);
        assertEquals(Integer.valueOf(FIFO_MAX_GENERAL_A),
                configA.getAndroidSensorFifoMaxEventCount(1));
        assertEquals(Integer.valueOf(FIFO_MAX_GENERAL_B),
                configB.getAndroidSensorFifoMaxEventCount(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> markerA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA));
            assertEquals(FIFO_MAX_GENERAL_A,
                    invokeGetFifoMaxEventCount(jni, baseVM, useVaList, markerA));

            int typeBeforeB = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int fifoBeforeB = countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount");
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            try {
                invokeGetType(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getType on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetFifoMaxEventCount(jni, baseVM, useVaList, markerA);
                fail("expected UOE for getFifoMaxEventCount on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoMaxEventCount"));
            }
            assertEquals(typeBeforeB, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(fifoBeforeB, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount"));

            DvmObject<?> markerB = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerB);
            assertNotSame(markerA, markerB);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerB));
            assertEquals(FIFO_MAX_GENERAL_B,
                    invokeGetFifoMaxEventCount(jni, baseVM, useVaList, markerB));

            int typeBeforeA = countEvents(sink.events, "android_sensor", "Sensor.getType");
            int fifoBeforeA = countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount");
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            try {
                invokeGetType(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getType on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getType"));
            }
            try {
                invokeGetFifoMaxEventCount(jni, baseVM, useVaList, markerB);
                fail("expected UOE for getFifoMaxEventCount on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getFifoMaxEventCount"));
            }
            assertEquals(typeBeforeA, countEvents(sink.events, "android_sensor", "Sensor.getType"));
            assertEquals(fifoBeforeA, countEvents(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount"));

            DvmObject<?> markerA2 = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(markerA2);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, markerA2));
            assertEquals(FIFO_MAX_GENERAL_A,
                    invokeGetFifoMaxEventCount(jni, baseVM, useVaList, markerA2));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetFifoMaxEventCountParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"fifoMaxEventCounts\":[]}}}",
                "android.sensors.fifoMaxEventCounts");
        assertParseInvalid("{\"android\":{\"sensors\":{\"fifoMaxEventCounts\":1}}}",
                "android.sensors.fifoMaxEventCounts");
        assertParseInvalid("{\"android\":{\"sensors\":{\"fifoMaxEventCounts\":null}}}",
                "android.sensors.fifoMaxEventCounts");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"01\":1}}}}",
                "android.sensors.fifoMaxEventCounts.01");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"0\":1}}}}",
                "android.sensors.fifoMaxEventCounts.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"+1\":1}}}}",
                "android.sensors.fifoMaxEventCounts.+1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"-1\":1}}}}",
                "android.sensors.fifoMaxEventCounts.-1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1.0\":1}}}}",
                "android.sensors.fifoMaxEventCounts.1.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\" 1\":1}}}}",
                "android.sensors.fifoMaxEventCounts. 1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1 \":1}}}}",
                "android.sensors.fifoMaxEventCounts.1 ");
        assertParseInvalid("{\"android\":{\"sensors\":{\"fifoMaxEventCounts\":{\"65536\":1}}}}",
                "android.sensors.fifoMaxEventCounts.65536");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1\":null}}}}",
                "android.sensors.fifoMaxEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1\":1.5}}}}",
                "android.sensors.fifoMaxEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1\":1.0}}}}",
                "android.sensors.fifoMaxEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1\":-1}}}}",
                "android.sensors.fifoMaxEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1\":2147483648}}}}",
                "android.sensors.fifoMaxEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1\":\"1\"}}}}",
                "android.sensors.fifoMaxEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1\":true}}}}",
                "android.sensors.fifoMaxEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1\":false}}}}",
                "android.sensors.fifoMaxEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1\":[]}}}}",
                "android.sensors.fifoMaxEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"1\":{}}}}}",
                "android.sensors.fifoMaxEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"fifoMaxEventCounts\":{\"4\":1}}}}",
                "android.sensors.fifoMaxEventCounts.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"fifoMaxEventCounts\":{\"1\":1}}}}",
                "android.sensors.fifoMaxEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"fifoMaxEventCounts\":{\"1\":1}}}}",
                "android.sensors.fifoMaxEventCounts.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"extra\":1}}}",
                "android.sensors.extra");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_STATIC_JSON);
        assertTrue(ok.isAndroidSensorFifoMaxEventCountsConfigured());
        assertTrue(ok.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertFalse(ok.isAndroidSensorFifoMaxEventCountConfigured(4));
        assertEquals(Integer.valueOf(0), ok.getAndroidSensorFifoMaxEventCount(1));
        assertFalse(ok.isAndroidSensorNamesConfigured());
        assertFalse(ok.isAndroidSensorVendorsConfigured());
        assertFalse(ok.isAndroidSensorVersionsConfigured());
        assertFalse(ok.isAndroidSensorStringTypesConfigured());
        assertFalse(ok.isAndroidSensorMaximumRangesConfigured());
        assertFalse(ok.isAndroidSensorResolutionsConfigured());
        assertFalse(ok.isAndroidSensorPowersConfigured());
        assertFalse(ok.isAndroidSensorMinDelaysMicrosConfigured());
        assertFalse(ok.isAndroidSensorMinDelayMicrosConfigured(1));
        assertNull(ok.getAndroidSensorMinDelayMicros(1));
        assertFalse(ok.isAndroidSensorMaxDelaysMicrosConfigured());
        assertFalse(ok.isAndroidSensorMaxDelayMicrosConfigured(1));
        assertNull(ok.getAndroidSensorMaxDelayMicros(1));
        assertFalse(ok.isAndroidSensorFifoReservedEventCountsConfigured());
        assertFalse(ok.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertNull(ok.getAndroidSensorFifoReservedEventCount(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(empty.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(empty.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(omitted.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(omitted.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig namesOnly = TraceEnvironmentConfig.parse(SENSORS_NAMES_STATIC_JSON);
        assertFalse(namesOnly.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(namesOnly.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(namesOnly.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig vendorsOnly = TraceEnvironmentConfig.parse(SENSORS_VENDORS_STATIC_JSON);
        assertFalse(vendorsOnly.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(vendorsOnly.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(vendorsOnly.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig versionsOnly = TraceEnvironmentConfig.parse(SENSORS_VERSIONS_STATIC_JSON);
        assertFalse(versionsOnly.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(versionsOnly.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(versionsOnly.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig stringTypesOnly = TraceEnvironmentConfig.parse(SENSORS_STRING_TYPES_STATIC_JSON);
        assertFalse(stringTypesOnly.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(stringTypesOnly.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(stringTypesOnly.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig maximumRangesOnly = TraceEnvironmentConfig.parse(SENSORS_MAXIMUM_RANGES_STATIC_JSON);
        assertFalse(maximumRangesOnly.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(maximumRangesOnly.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(maximumRangesOnly.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig resolutionsOnly = TraceEnvironmentConfig.parse(SENSORS_RESOLUTIONS_STATIC_JSON);
        assertFalse(resolutionsOnly.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(resolutionsOnly.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(resolutionsOnly.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig powersOnly = TraceEnvironmentConfig.parse(SENSORS_POWERS_STATIC_JSON);
        assertFalse(powersOnly.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(powersOnly.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(powersOnly.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig minDelaysOnly = TraceEnvironmentConfig.parse(SENSORS_MIN_DELAYS_STATIC_JSON);
        assertFalse(minDelaysOnly.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(minDelaysOnly.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(minDelaysOnly.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig maxDelaysOnly = TraceEnvironmentConfig.parse(SENSORS_MAX_DELAYS_STATIC_JSON);
        assertFalse(maxDelaysOnly.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(maxDelaysOnly.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(maxDelaysOnly.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig fifoReservedOnly = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_STATIC_JSON);
        assertFalse(fifoReservedOnly.isAndroidSensorFifoMaxEventCountsConfigured());
        assertFalse(fifoReservedOnly.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertNull(fifoReservedOnly.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_DYNAMIC_JSON);
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), max.getAndroidSensorFifoMaxEventCount(7));
        TraceEnvironmentConfig general = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_A_JSON);
        assertEquals(Integer.valueOf(FIFO_MAX_GENERAL_A),
                general.getAndroidSensorFifoMaxEventCount(1));
        TraceEnvironmentConfig independent = TraceEnvironmentConfig.parse(
                SENSORS_FIFO_RESERVED_MAX_INDEPENDENT_JSON);
        assertEquals(Integer.valueOf(FIFO_RESERVED_INDEPENDENT),
                independent.getAndroidSensorFifoReservedEventCount(1));
        assertEquals(Integer.valueOf(FIFO_MAX_INDEPENDENT),
                independent.getAndroidSensorFifoMaxEventCount(1));
        assertTrue(independent.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertTrue(independent.isAndroidSensorFifoMaxEventCountConfigured(1));
        assertFalse(Integer.valueOf(FIFO_RESERVED_INDEPENDENT)
                .equals(independent.getAndroidSensorFifoMaxEventCount(1)));
        assertTrue(FIFO_MAX_INDEPENDENT < FIFO_RESERVED_INDEPENDENT);
    }

    private static void runSensorFifoReservedAndMaxIndependentSize(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                SENSORS_FIFO_RESERVED_MAX_INDEPENDENT_JSON);
        assertEquals(Integer.valueOf(FIFO_RESERVED_INDEPENDENT),
                config.getAndroidSensorFifoReservedEventCount(1));
        assertEquals(Integer.valueOf(FIFO_MAX_INDEPENDENT),
                config.getAndroidSensorFifoMaxEventCount(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(FIFO_RESERVED_INDEPENDENT,
                    invokeGetFifoReservedEventCount(jni, baseVM, useVaList, sensor));
            assertEquals(FIFO_MAX_INDEPENDENT,
                    invokeGetFifoMaxEventCount(jni, baseVM, useVaList, sensor));
            assertEquals(FIFO_RESERVED_INDEPENDENT,
                    invokeGetFifoReservedEventCountByMethod(jni, baseVM, useVaList, sensor));
            assertEquals(FIFO_MAX_INDEPENDENT,
                    invokeGetFifoMaxEventCountByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent reservedEv = findLastEvent(sink.events, "android_sensor",
                    "Sensor.getFifoReservedEventCount");
            assertNotNull(reservedEv);
            assertEquals("sensorType=1,fifoReservedEventCount=" + FIFO_RESERVED_INDEPENDENT,
                    String.valueOf(reservedEv.value));
            assertFalse(String.valueOf(reservedEv.value).contains("fifoMax"));
            CapturedEvent maxEv = findLastEvent(sink.events, "android_sensor",
                    "Sensor.getFifoMaxEventCount");
            assertNotNull(maxEv);
            assertEquals("sensorType=1,fifoMaxEventCount=" + FIFO_MAX_INDEPENDENT,
                    String.valueOf(maxEv.value));
            assertFalse(String.valueOf(maxEv.value).contains("fifoReserved"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertOtherSensorGettersUoeWhenOnlyFifoMax(AbstractJni jni, BaseVM baseVM,
                                                                  boolean useVaList,
                                                                  DvmObject<?> sensor,
                                                                  CapturingSink sink) {
        int nameBefore = countEvents(sink.events, "android_sensor", "Sensor.getName");
        try {
            invokeGetName(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getName when only fifoMaxEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getName"));
        }
        assertEquals(nameBefore, countEvents(sink.events, "android_sensor", "Sensor.getName"));

        int vendorBefore = countEvents(sink.events, "android_sensor", "Sensor.getVendor");
        try {
            invokeGetVendor(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getVendor when only fifoMaxEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getVendor"));
        }
        assertEquals(vendorBefore, countEvents(sink.events, "android_sensor", "Sensor.getVendor"));

        int versionBefore = countEvents(sink.events, "android_sensor", "Sensor.getVersion");
        try {
            invokeGetVersion(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getVersion when only fifoMaxEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getVersion"));
        }
        assertEquals(versionBefore, countEvents(sink.events, "android_sensor", "Sensor.getVersion"));

        int stringTypeBefore = countEvents(sink.events, "android_sensor", "Sensor.getStringType");
        try {
            invokeGetStringType(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getStringType when only fifoMaxEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getStringType"));
        }
        assertEquals(stringTypeBefore, countEvents(sink.events, "android_sensor", "Sensor.getStringType"));

        int rangeBefore = countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange");
        try {
            invokeGetMaximumRange(jni, baseVM, sensor);
            fail("expected UOE for getMaximumRange when only fifoMaxEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getMaximumRange"));
        }
        assertEquals(rangeBefore, countEvents(sink.events, "android_sensor", "Sensor.getMaximumRange"));

        int resolutionBefore = countEvents(sink.events, "android_sensor", "Sensor.getResolution");
        try {
            invokeGetResolution(jni, baseVM, sensor);
            fail("expected UOE for getResolution when only fifoMaxEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getResolution"));
        }
        assertEquals(resolutionBefore, countEvents(sink.events, "android_sensor", "Sensor.getResolution"));

        int powerBefore = countEvents(sink.events, "android_sensor", "Sensor.getPower");
        try {
            invokeGetPower(jni, baseVM, sensor);
            fail("expected UOE for getPower when only fifoMaxEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getPower"));
        }
        assertEquals(powerBefore, countEvents(sink.events, "android_sensor", "Sensor.getPower"));

        int minDelayBefore = countEvents(sink.events, "android_sensor", "Sensor.getMinDelay");
        try {
            invokeGetMinDelay(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getMinDelay when only fifoMaxEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getMinDelay"));
        }
        assertEquals(minDelayBefore, countEvents(sink.events, "android_sensor", "Sensor.getMinDelay"));

        int maxDelayBefore = countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay");
        try {
            invokeGetMaxDelay(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getMaxDelay when only fifoMaxEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getMaxDelay"));
        }
        assertEquals(maxDelayBefore, countEvents(sink.events, "android_sensor", "Sensor.getMaxDelay"));

        int fifoReservedBefore = countEvents(sink.events, "android_sensor",
                "Sensor.getFifoReservedEventCount");
        try {
            invokeGetFifoReservedEventCount(jni, baseVM, useVaList, sensor);
            fail("expected UOE for getFifoReservedEventCount when only fifoMaxEventCounts is configured");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getFifoReservedEventCount"));
        }
        assertEquals(fifoReservedBefore, countEvents(sink.events, "android_sensor",
                "Sensor.getFifoReservedEventCount"));

        expectSensorIntUoe(jni, baseVM, useVaList, sensor, "getId", "Sensor.getId", sink);
        expectSensorIntUoe(jni, baseVM, useVaList, sensor, "getReportingMode",
                "Sensor.getReportingMode", sink);
        expectSensorBooleanUoe(jni, baseVM, useVaList, sensor, "isDynamicSensor",
                "Sensor.isDynamicSensor", sink);
        expectSensorObjectUoe(jni, baseVM, useVaList, sensor, "getRequiredPermission",
                "()Ljava/lang/String;", "Sensor.getRequiredPermission", sink);
        expectSensorBooleanUoe(jni, baseVM, useVaList, sensor, "isAdditionalInfoSupported",
                "Sensor.isAdditionalInfoSupported", sink);
    }

    private static void assertParseInvalid(String json, String expectedPath) {
        try {
            TraceEnvironmentConfig.parse(json);
            fail("expected IllegalArgumentException containing path: " + expectedPath
                    + " for json: " + json);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue("message missing path " + expectedPath + ", was: " + message,
                    message != null && message.contains(expectedPath));
        }
    }

    private static DvmObject<?> resolveSensorSystemService(AbstractJni jni, BaseVM vm,
                                                           boolean useVaList, VM dalvikVm) {
        DvmObject<?> app = dalvikVm.resolveClass("android/app/Application").newObject(null);
        return invokeGetSystemService(jni, vm, useVaList, app, "sensor");
    }

    private static DvmObject<?> invokeGetSystemService(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> app, String serviceName) {
        int nameHash = vm.addLocalObject(new StringObject(vm, serviceName));
        DvmClass dvmClass = app.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/String;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, app, signature, new TestObjectVaList(vm, method, nameHash));
        }
        return jni.callObjectMethod(vm, app, signature, new TestObjectVarArg(vm, method, nameHash));
    }

    private static DvmObject<?> invokeGetSystemServiceClass(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                            DvmObject<?> receiver, DvmClass serviceClass) {
        int classHash = vm.addLocalObject(serviceClass);
        DvmClass dvmClass = receiver.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature,
                    new TestObjectVaList(vm, method, classHash));
        }
        return jni.callObjectMethod(vm, receiver, signature,
                new TestObjectVarArg(vm, method, classHash));
    }

    private static DvmObject<?> invokeGetDefaultSensor(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> target, int sensorType) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getDefaultSensor",
                "(I)Landroid/hardware/Sensor;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature,
                    new TestIntVaList(vm, method, sensorType));
        }
        return jni.callObjectMethod(vm, target, signature,
                new TestIntVarArg(vm, method, sensorType));
    }

    private static DvmObject<?> invokeGetDefaultSensorByWakeUp(AbstractJni jni, BaseVM vm,
                                                               boolean useVaList,
                                                               DvmObject<?> target, int sensorType,
                                                               boolean wakeUp) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getDefaultSensor",
                "(IZ)Landroid/hardware/Sensor;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature,
                    new TestIntBooleanVaList(vm, method, sensorType, wakeUp));
        }
        return jni.callObjectMethod(vm, target, signature,
                new TestIntBooleanVarArg(vm, method, sensorType, wakeUp));
    }

    private static void expectGetDefaultSensorByWakeUpUoe(AbstractJni jni, BaseVM vm,
                                                          boolean useVaList, DvmObject<?> target,
                                                          int sensorType, boolean wakeUp,
                                                          CapturingSink sink) {
        int before = countEvents(sink.events, "android_sensor", "SensorManager.getDefaultSensor");
        try {
            invokeGetDefaultSensorByWakeUp(jni, vm, useVaList, target, sensorType, wakeUp);
            fail("expected UOE for getDefaultSensor(IZ)");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getDefaultSensor"));
        }
        assertEquals(before, countEvents(sink.events, "android_sensor",
                "SensorManager.getDefaultSensor"));
    }

    private static ArrayListObject invokeGetSensorList(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> target, int sensorType) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getSensorList",
                "(I)Ljava/util/List;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, target, signature,
                    new TestIntVaList(vm, method, sensorType));
        } else {
            result = jni.callObjectMethod(vm, target, signature,
                    new TestIntVarArg(vm, method, sensorType));
        }
        assertTrue(result instanceof ArrayListObject);
        return (ArrayListObject) result;
    }

    private static ArrayListObject invokeGetDynamicSensorList(AbstractJni jni, BaseVM vm,
                                                              boolean useVaList,
                                                              DvmObject<?> target, int sensorType) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getDynamicSensorList",
                "(I)Ljava/util/List;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, target, signature,
                    new TestIntVaList(vm, method, sensorType));
        } else {
            result = jni.callObjectMethod(vm, target, signature,
                    new TestIntVarArg(vm, method, sensorType));
        }
        assertTrue(result instanceof ArrayListObject);
        return (ArrayListObject) result;
    }

    private static int invokeGetType(AbstractJni jni, BaseVM vm, boolean useVaList,
                                     DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getType", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetName(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getName", "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callObjectMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetVendor(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getVendor", "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callObjectMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static int invokeGetVersion(AbstractJni jni, BaseVM vm, boolean useVaList,
                                        DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getVersion", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static int invokeGetMinDelay(AbstractJni jni, BaseVM vm, boolean useVaList,
                                         DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getMinDelay", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    /** Same signature via the {@link DvmMethod} {@code callIntMethod}/{@code callIntMethodV} overload. */
    private static int invokeGetMinDelayByMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getMinDelay", "()I", false);
        if (useVaList) {
            return jni.callIntMethodV(vm, target, method, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, method, new TestNoArgVarArg(vm, method));
    }

    private static int invokeGetMaxDelay(AbstractJni jni, BaseVM vm, boolean useVaList,
                                         DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getMaxDelay", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    /** Same signature via the {@link DvmMethod} {@code callIntMethod}/{@code callIntMethodV} overload. */
    private static int invokeGetMaxDelayByMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getMaxDelay", "()I", false);
        if (useVaList) {
            return jni.callIntMethodV(vm, target, method, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, method, new TestNoArgVarArg(vm, method));
    }

    private static int invokeGetFifoReservedEventCount(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getFifoReservedEventCount", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    /** Same signature via the {@link DvmMethod} {@code callIntMethod}/{@code callIntMethodV} overload. */
    private static int invokeGetFifoReservedEventCountByMethod(AbstractJni jni, BaseVM vm,
                                                               boolean useVaList,
                                                               DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getFifoReservedEventCount", "()I", false);
        if (useVaList) {
            return jni.callIntMethodV(vm, target, method, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, method, new TestNoArgVarArg(vm, method));
    }

    private static int invokeGetFifoMaxEventCount(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getFifoMaxEventCount", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    /** Same signature via the {@link DvmMethod} {@code callIntMethod}/{@code callIntMethodV} overload. */
    private static int invokeGetFifoMaxEventCountByMethod(AbstractJni jni, BaseVM vm,
                                                          boolean useVaList,
                                                          DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getFifoMaxEventCount", "()I", false);
        if (useVaList) {
            return jni.callIntMethodV(vm, target, method, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, method, new TestNoArgVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetStringType(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                    DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getStringType", "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callObjectMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    /**
     * {@code Sensor.getMaximumRange()F} is only wired through {@code callFloatMethodV}
     * (no instance VarArg float path).
     */
    private static float invokeGetMaximumRange(AbstractJni jni, BaseVM vm, DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getMaximumRange", "()F", false);
        return jni.callFloatMethodV(vm, target, method.getSignature(),
                new TestNoArgVaList(vm, method));
    }

    /** Same signature via the {@link DvmMethod} {@code callFloatMethodV} overload. */
    private static float invokeGetMaximumRangeByMethod(AbstractJni jni, BaseVM vm,
                                                       DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getMaximumRange", "()F", false);
        return jni.callFloatMethodV(vm, target, method, new TestNoArgVaList(vm, method));
    }

    /**
     * {@code Sensor.getResolution()F} is only wired through {@code callFloatMethodV}
     * (no instance VarArg float path).
     */
    private static float invokeGetResolution(AbstractJni jni, BaseVM vm, DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getResolution", "()F", false);
        return jni.callFloatMethodV(vm, target, method.getSignature(),
                new TestNoArgVaList(vm, method));
    }

    /** Same signature via the {@link DvmMethod} {@code callFloatMethodV} overload. */
    private static float invokeGetResolutionByMethod(AbstractJni jni, BaseVM vm,
                                                     DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getResolution", "()F", false);
        return jni.callFloatMethodV(vm, target, method, new TestNoArgVaList(vm, method));
    }

    /**
     * {@code Sensor.getPower()F} is only wired through {@code callFloatMethodV}
     * (no instance VarArg float path).
     */
    private static float invokeGetPower(AbstractJni jni, BaseVM vm, DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getPower", "()F", false);
        return jni.callFloatMethodV(vm, target, method.getSignature(),
                new TestNoArgVaList(vm, method));
    }

    /** Same signature via the {@link DvmMethod} {@code callFloatMethodV} overload. */
    private static float invokeGetPowerByMethod(AbstractJni jni, BaseVM vm,
                                                DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getPower", "()F", false);
        return jni.callFloatMethodV(vm, target, method, new TestNoArgVaList(vm, method));
    }

    private static void runSensorIsWakeUpSensorStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_WAKE_UP_STATIC_JSON);
        assertTrue(config.isAndroidSensorWakeUpSensorsConfigured());
        assertTrue(config.isAndroidSensorWakeUpSensorConfigured(1));
        assertEquals(Boolean.FALSE, config.getAndroidSensorWakeUpSensor(1));
        assertFalse(config.isAndroidSensorWakeUpSensorConfigured(4));
        assertNull(config.getAndroidSensorWakeUpSensor(4));
        assertFalse(config.isAndroidSensorFifoReservedEventCountConfigured(1));
        assertFalse(config.isAndroidSensorFifoMaxEventCountConfigured(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);
            assertEquals(1, invokeGetType(jni, baseVM, useVaList, sensor));

            assertFalse(invokeIsWakeUpSensor(jni, baseVM, useVaList, sensor));
            assertFalse(invokeIsWakeUpSensorByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.isWakeUpSensor");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,wakeUp=false", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("fifo"));
            assertFalse(String.valueOf(ev.value).contains("names"));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.isWakeUpSensor"));

            ArrayListObject list = invokeGetSensorList(jni, baseVM, useVaList, manager, 1);
            assertEquals(1, list.size());
            assertFalse(invokeIsWakeUpSensor(jni, baseVM, useVaList, list.getValue().get(0)));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsWakeUpSensorDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_WAKE_UP_DYNAMIC_JSON);
        assertTrue(config.isAndroidSensorWakeUpSensorConfigured(7));
        assertFalse(config.isAndroidSensorWakeUpSensorConfigured(1));
        assertEquals(Boolean.TRUE, config.getAndroidSensorWakeUpSensor(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 7));
            ArrayListObject list = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7);
            assertEquals(1, list.size());
            DvmObject<?> sensor = list.getValue().get(0);
            assertEquals(7, invokeGetType(jni, baseVM, useVaList, sensor));
            assertTrue(invokeIsWakeUpSensor(jni, baseVM, useVaList, sensor));
            assertTrue(invokeIsWakeUpSensorByMethod(jni, baseVM, useVaList, sensor));

            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.isWakeUpSensor");
            assertNotNull(ev);
            assertEquals("sensorType=7,wakeUp=true", String.valueOf(ev.value));
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.isWakeUpSensor"));

            DvmObject<?> typeOnly = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(typeOnly);
            int before = countEvents(sink.events, "android_sensor", "Sensor.isWakeUpSensor");
            try {
                invokeIsWakeUpSensor(jni, baseVM, useVaList, typeOnly);
                fail("expected UOE for isWakeUpSensor on types-only sensor without wakeUp");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isWakeUpSensor"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.isWakeUpSensor"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsWakeUpSensorNotHandledOnLiveMarker(
            boolean is64Bit, boolean useVaList, String json, int sensorType) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            int before = countEvents(sink.events, "android_sensor", "Sensor.isWakeUpSensor");
            try {
                invokeIsWakeUpSensor(jni, baseVM, useVaList, sensor);
                fail("expected UOE for isWakeUpSensor when wakeUpSensors is not explicit");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isWakeUpSensor"));
            }
            assertEquals(before, countEvents(sink.events, "android_sensor", "Sensor.isWakeUpSensor"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsWakeUpSensorIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_WAKE_UP_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertNotNull(sensor);

            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            try {
                invokeIsWakeUpSensor(jni, baseVM, useVaList, plain);
                fail("expected UOE for isWakeUpSensor on plain Sensor");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isWakeUpSensor"));
            }

            try {
                DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "isWakeUpSensor",
                        "()Ljava/lang/String;", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, sensor, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callObjectMethod(baseVM, sensor, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong isWakeUpSensor signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isWakeUpSensor"));
            }

            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                BaseVM absentBase = (BaseVM) absentVm;
                DvmObject<?> absentPlain = absentVm.resolveClass(SENSOR_CLASS).newObject(null);
                try {
                    invokeIsWakeUpSensor(absentJni, absentBase, useVaList, absentPlain);
                    fail("expected UOE for isWakeUpSensor without android.sensors");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("isWakeUpSensor"));
                }
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_sensor event without sensors node: " + e.api,
                            "android_sensor".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreign = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                assertNotNull(foreign);
                try {
                    invokeIsWakeUpSensor(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for isWakeUpSensor on foreign ConfiguredSensor");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("isWakeUpSensor"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            int beforeStale = countEvents(sink.events, "android_sensor", "Sensor.isWakeUpSensor");
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            try {
                invokeIsWakeUpSensor(jni, baseVM, useVaList, sensor);
                fail("expected UOE for isWakeUpSensor after stale setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isWakeUpSensor"));
            }
            assertEquals(beforeStale, countEvents(sink.events, "android_sensor",
                    "Sensor.isWakeUpSensor"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * 须从当前 SensorManager 重新获取新 marker 才能读到新 wake-up。恢复 A 不会复活 B 的 marker。
     */
    private static void runSensorWakeUpMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_WAKE_UP_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_WAKE_UP_B_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensorA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertFalse(invokeIsWakeUpSensor(jni, baseVM, useVaList, sensorA));

            emulator.set(TraceEnvironmentConfig.KEY, configB);
            int beforeB = countEvents(sink.events, "android_sensor", "Sensor.isWakeUpSensor");
            try {
                invokeIsWakeUpSensor(jni, baseVM, useVaList, sensorA);
                fail("expected UOE for isWakeUpSensor on A marker after setEnvironmentConfig(B)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isWakeUpSensor"));
            }
            assertEquals(beforeB, countEvents(sink.events, "android_sensor", "Sensor.isWakeUpSensor"));

            DvmObject<?> managerB = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensorB = invokeGetDefaultSensor(jni, baseVM, useVaList, managerB, 1);
            assertTrue(invokeIsWakeUpSensor(jni, baseVM, useVaList, sensorB));

            emulator.set(TraceEnvironmentConfig.KEY, configA);
            int beforeA = countEvents(sink.events, "android_sensor", "Sensor.isWakeUpSensor");
            try {
                invokeIsWakeUpSensor(jni, baseVM, useVaList, sensorB);
                fail("expected UOE for isWakeUpSensor on B marker after setEnvironmentConfig(A)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isWakeUpSensor"));
            }
            assertEquals(beforeA, countEvents(sink.events, "android_sensor", "Sensor.isWakeUpSensor"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsWakeUpSensorParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"wakeUpSensors\":[]}}}",
                "android.sensors.wakeUpSensors");
        assertParseInvalid("{\"android\":{\"sensors\":{\"wakeUpSensors\":1}}}",
                "android.sensors.wakeUpSensors");
        assertParseInvalid("{\"android\":{\"sensors\":{\"wakeUpSensors\":null}}}",
                "android.sensors.wakeUpSensors");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"wakeUpSensors\":{\"01\":true}}}}",
                "android.sensors.wakeUpSensors.01");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"wakeUpSensors\":{\"0\":true}}}}",
                "android.sensors.wakeUpSensors.0");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"wakeUpSensors\":{\"+1\":true}}}}",
                "android.sensors.wakeUpSensors.+1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"wakeUpSensors\":{\"1\":null}}}}",
                "android.sensors.wakeUpSensors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"wakeUpSensors\":{\"1\":1}}}}",
                "android.sensors.wakeUpSensors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"wakeUpSensors\":{\"1\":\"true\"}}}}",
                "android.sensors.wakeUpSensors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"wakeUpSensors\":{\"1\":[]}}}}",
                "android.sensors.wakeUpSensors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"wakeUpSensors\":{\"4\":true}}}}",
                "android.sensors.wakeUpSensors.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"wakeUpSensors\":{\"1\":true}}}}",
                "android.sensors.wakeUpSensors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"wakeUpSensors\":{\"1\":true}}}}",
                "android.sensors.wakeUpSensors.1");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_WAKE_UP_STATIC_JSON);
        assertTrue(ok.isAndroidSensorWakeUpSensorsConfigured());
        assertEquals(Boolean.FALSE, ok.getAndroidSensorWakeUpSensor(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_WAKE_UP_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorWakeUpSensorsConfigured());
        assertFalse(empty.isAndroidSensorWakeUpSensorConfigured(1));
        assertNull(empty.getAndroidSensorWakeUpSensor(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorWakeUpSensorsConfigured());
        assertFalse(omitted.isAndroidSensorWakeUpSensorConfigured(1));
        assertNull(omitted.getAndroidSensorWakeUpSensor(1));
        TraceEnvironmentConfig fifoOnly = TraceEnvironmentConfig.parse(SENSORS_FIFO_RESERVED_STATIC_JSON);
        assertFalse(fifoOnly.isAndroidSensorWakeUpSensorsConfigured());
        assertFalse(fifoOnly.isAndroidSensorWakeUpSensorConfigured(1));
        assertNull(fifoOnly.getAndroidSensorWakeUpSensor(1));
        TraceEnvironmentConfig fifoMaxOnly = TraceEnvironmentConfig.parse(SENSORS_FIFO_MAX_STATIC_JSON);
        assertFalse(fifoMaxOnly.isAndroidSensorWakeUpSensorsConfigured());
        assertNull(fifoMaxOnly.getAndroidSensorWakeUpSensor(1));
    }

    private static void runSensorGetIdStaticType(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_IDS_STATIC_JSON);
        assertTrue(config.isAndroidSensorIdConfigured(1));
        assertEquals(Integer.valueOf(-1), config.getAndroidSensorId(1));
        assertFalse(config.isAndroidSensorIdConfigured(4));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertEquals(-1, invokeGetId(jni, baseVM, useVaList, sensor));
            assertEquals(-1, invokeGetIdByMethod(jni, baseVM, useVaList, sensor));
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getId");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("sensorType=1,id=-1", String.valueOf(ev.value));
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getId"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetIdDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_IDS_DYNAMIC_JSON);
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), config.getAndroidSensorId(7));
        assertFalse(config.isAndroidSensorIdConfigured(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7)
                    .getValue().get(0);
            assertEquals(Integer.MAX_VALUE, invokeGetId(jni, baseVM, useVaList, sensor));
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getId");
            assertEquals("sensorType=7,id=" + Integer.MAX_VALUE, String.valueOf(ev.value));
            DvmObject<?> typeOnly = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            expectSensorIntUoe(jni, baseVM, useVaList, typeOnly, "getId", "Sensor.getId", sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetIdNotHandledOnLiveMarker(boolean is64Bit, boolean useVaList,
                                                             String json, int sensorType)
            throws Exception {
        runSensorIntNotHandledOnLiveMarker(is64Bit, useVaList, json, sensorType, "getId",
                "Sensor.getId");
    }

    private static void runSensorGetIdIsolation(boolean is64Bit, boolean useVaList) throws Exception {
        runSensorIntIsolation(is64Bit, useVaList, SENSORS_IDS_STATIC_JSON, "getId", "Sensor.getId");
    }

    private static void runSensorGetIdMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        runSensorIntMarkerConfigInstanceStale(is64Bit, useVaList, SENSORS_IDS_A_JSON,
                SENSORS_IDS_B_JSON, "getId", "Sensor.getId", 0, 42);
    }

    private static void runSensorGetIdParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"sensorIds\":[]}}}", "android.sensors.sensorIds");
        assertParseInvalid("{\"android\":{\"sensors\":{\"sensorIds\":1}}}", "android.sensors.sensorIds");
        assertParseInvalid("{\"android\":{\"sensors\":{\"sensorIds\":null}}}", "android.sensors.sensorIds");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"sensorIds\":{\"01\":1}}}}",
                "android.sensors.sensorIds.01");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"sensorIds\":{\"1\":1.0}}}}",
                "android.sensors.sensorIds.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"sensorIds\":{\"1\":-2}}}}",
                "android.sensors.sensorIds.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"sensorIds\":{\"1\":null}}}}",
                "android.sensors.sensorIds.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"sensorIds\":{\"1\":\"-1\"}}}}",
                "android.sensors.sensorIds.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"sensorIds\":{\"4\":1}}}}",
                "android.sensors.sensorIds.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"sensorIds\":{\"1\":1}}}}",
                "android.sensors.sensorIds.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"sensorIds\":{\"1\":1}}}}",
                "android.sensors.sensorIds.1");
        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_IDS_STATIC_JSON);
        assertTrue(ok.isAndroidSensorIdsConfigured());
        assertEquals(Integer.valueOf(-1), ok.getAndroidSensorId(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_IDS_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorIdsConfigured());
        assertFalse(empty.isAndroidSensorIdConfigured(1));
        assertNull(empty.getAndroidSensorId(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorIdsConfigured());
        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse(SENSORS_IDS_A_JSON);
        assertEquals(Integer.valueOf(0), zero.getAndroidSensorId(1));
    }

    private static void runSensorGetReportingModeStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_REPORTING_STATIC_JSON);
        assertEquals(Integer.valueOf(0), config.getAndroidSensorReportingMode(1));
        assertFalse(config.isAndroidSensorReportingModeConfigured(4));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertEquals(0, invokeGetReportingMode(jni, baseVM, useVaList, sensor));
            assertEquals(0, invokeGetReportingModeByMethod(jni, baseVM, useVaList, sensor));
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getReportingMode");
            assertEquals("sensorType=1,reportingMode=0", String.valueOf(ev.value));
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getReportingMode"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetReportingModeDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_REPORTING_DYNAMIC_JSON);
        assertEquals(Integer.valueOf(3), config.getAndroidSensorReportingMode(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7)
                    .getValue().get(0);
            assertEquals(3, invokeGetReportingMode(jni, baseVM, useVaList, sensor));
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.getReportingMode");
            assertEquals("sensorType=7,reportingMode=3", String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetReportingModeNotHandledOnLiveMarker(
            boolean is64Bit, boolean useVaList, String json, int sensorType) throws Exception {
        runSensorIntNotHandledOnLiveMarker(is64Bit, useVaList, json, sensorType, "getReportingMode",
                "Sensor.getReportingMode");
    }

    private static void runSensorGetReportingModeIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runSensorIntIsolation(is64Bit, useVaList, SENSORS_REPORTING_STATIC_JSON, "getReportingMode",
                "Sensor.getReportingMode");
    }

    private static void runSensorGetReportingModeMarkerConfigInstanceStale(
            boolean is64Bit, boolean useVaList) throws Exception {
        runSensorIntMarkerConfigInstanceStale(is64Bit, useVaList, SENSORS_REPORTING_A_JSON,
                SENSORS_REPORTING_B_JSON, "getReportingMode", "Sensor.getReportingMode", 1, 2);
    }

    private static void runSensorGetReportingModeParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"reportingModes\":[]}}}",
                "android.sensors.reportingModes");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"reportingModes\":{\"1\":4}}}}",
                "android.sensors.reportingModes.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"reportingModes\":{\"1\":-1}}}}",
                "android.sensors.reportingModes.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"reportingModes\":{\"1\":1.0}}}}",
                "android.sensors.reportingModes.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"reportingModes\":{\"4\":0}}}}",
                "android.sensors.reportingModes.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"reportingModes\":{\"1\":0}}}}",
                "android.sensors.reportingModes.1");
        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_REPORTING_STATIC_JSON);
        assertTrue(ok.isAndroidSensorReportingModesConfigured());
        assertEquals(Integer.valueOf(0), ok.getAndroidSensorReportingMode(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_REPORTING_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorReportingModesConfigured());
        assertFalse(empty.isAndroidSensorReportingModeConfigured(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorReportingModesConfigured());
    }

    private static void runSensorGetHighestDirectReportRateLevelStaticType(
            boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_DIRECT_RATE_STATIC_JSON);
        assertEquals(Integer.valueOf(0), config.getAndroidSensorHighestDirectReportRateLevel(1));
        assertFalse(config.isAndroidSensorHighestDirectReportRateLevelConfigured(4));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertEquals(0, invokeSensorInt(jni, baseVM, useVaList, sensor,
                    "getHighestDirectReportRateLevel"));
            DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
            DvmMethod method = new DvmMethod(dvmClass, "getHighestDirectReportRateLevel", "()I", false);
            int byMethod = useVaList
                    ? jni.callIntMethodV(baseVM, sensor, method, new TestNoArgVaList(baseVM, method))
                    : jni.callIntMethod(baseVM, sensor, method, new TestNoArgVarArg(baseVM, method));
            assertEquals(0, byMethod);
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.getHighestDirectReportRateLevel");
            assertEquals("sensorType=1,highestDirectReportRateLevel=0", String.valueOf(ev.value));
            assertEquals(2, countEvents(sink.events, "android_sensor",
                    "Sensor.getHighestDirectReportRateLevel"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetHighestDirectReportRateLevelDynamicOnlyType(
            boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_DIRECT_RATE_DYNAMIC_JSON);
        assertEquals(Integer.valueOf(3), config.getAndroidSensorHighestDirectReportRateLevel(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7)
                    .getValue().get(0);
            assertEquals(3, invokeSensorInt(jni, baseVM, useVaList, sensor,
                    "getHighestDirectReportRateLevel"));
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.getHighestDirectReportRateLevel");
            assertEquals("sensorType=7,highestDirectReportRateLevel=3", String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetHighestDirectReportRateLevelNotHandled(
            boolean is64Bit, boolean useVaList, String json, int sensorType) throws Exception {
        runSensorIntNotHandledOnLiveMarker(is64Bit, useVaList, json, sensorType,
                "getHighestDirectReportRateLevel", "Sensor.getHighestDirectReportRateLevel");
    }

    private static void runSensorGetHighestDirectReportRateLevelParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"highestDirectReportRateLevels\":[]}}}",
                "android.sensors.highestDirectReportRateLevels");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"highestDirectReportRateLevels\":{\"1\":4}}}}",
                "android.sensors.highestDirectReportRateLevels.1");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"highestDirectReportRateLevels\":{\"1\":-1}}}}",
                "android.sensors.highestDirectReportRateLevels.1");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"highestDirectReportRateLevels\":{\"1\":1.0}}}}",
                "android.sensors.highestDirectReportRateLevels.1");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"highestDirectReportRateLevels\":{\"4\":0}}}}",
                "android.sensors.highestDirectReportRateLevels.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"highestDirectReportRateLevels\":{\"1\":0}}}}",
                "android.sensors.highestDirectReportRateLevels.1");
        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_DIRECT_RATE_STATIC_JSON);
        assertTrue(ok.isAndroidSensorHighestDirectReportRateLevelsConfigured());
        assertEquals(Integer.valueOf(0), ok.getAndroidSensorHighestDirectReportRateLevel(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_DIRECT_RATE_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorHighestDirectReportRateLevelsConfigured());
        assertFalse(empty.isAndroidSensorHighestDirectReportRateLevelConfigured(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorHighestDirectReportRateLevelsConfigured());
        TraceEnvironmentConfig reportingOnly = TraceEnvironmentConfig.parse(
                SENSORS_REPORTING_STATIC_JSON);
        assertFalse(reportingOnly.isAndroidSensorHighestDirectReportRateLevelsConfigured());
    }

    private static void runSensorIsDirectChannelTypeSupportedStaticType(
            boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_DIRECT_CHANNEL_STATIC_JSON);
        assertEquals(Boolean.TRUE, config.isAndroidSensorDirectChannelTypeSupported(1, 1));
        assertEquals(Boolean.FALSE, config.isAndroidSensorDirectChannelTypeSupported(1, 2));
        assertEquals(Boolean.FALSE, config.isAndroidSensorDirectChannelTypeSupported(1, 0));
        assertFalse(config.isAndroidSensorDirectChannelTypeSupportedConfigured(4));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertTrue(invokeIsDirectChannelTypeSupported(jni, baseVM, useVaList, sensor, 1));
            assertFalse(invokeIsDirectChannelTypeSupported(jni, baseVM, useVaList, sensor, 2));
            assertFalse(invokeIsDirectChannelTypeSupported(jni, baseVM, useVaList, sensor, 0));
            DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
            DvmMethod method = new DvmMethod(dvmClass, "isDirectChannelTypeSupported", "(I)Z", false);
            boolean byMethod = useVaList
                    ? jni.callBooleanMethodV(baseVM, sensor, method,
                            new TestIntVaList(baseVM, method, 1))
                    : jni.callBooleanMethod(baseVM, sensor, method,
                            new TestIntVarArg(baseVM, method, 1));
            assertTrue(byMethod);
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.isDirectChannelTypeSupported");
            assertEquals("sensorType=1,sharedMemType=1,result=true", String.valueOf(ev.value));
            assertEquals(4, countEvents(sink.events, "android_sensor",
                    "Sensor.isDirectChannelTypeSupported"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsDirectChannelTypeSupportedDynamicOnlyType(
            boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_DIRECT_CHANNEL_DYNAMIC_JSON);
        assertEquals(Boolean.TRUE, config.isAndroidSensorDirectChannelTypeSupported(7, 1));
        assertEquals(Boolean.TRUE, config.isAndroidSensorDirectChannelTypeSupported(7, 2));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7)
                    .getValue().get(0);
            assertTrue(invokeIsDirectChannelTypeSupported(jni, baseVM, useVaList, sensor, 1));
            assertTrue(invokeIsDirectChannelTypeSupported(jni, baseVM, useVaList, sensor, 2));
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.isDirectChannelTypeSupported");
            assertEquals("sensorType=7,sharedMemType=2,result=true", String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsDirectChannelTypeSupportedEmptyArray(
            boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                SENSORS_DIRECT_CHANNEL_EMPTY_ARRAY_JSON);
        assertTrue(config.isAndroidSensorDirectChannelTypeSupportedConfigured(1));
        assertEquals(Boolean.FALSE, config.isAndroidSensorDirectChannelTypeSupported(1, 1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertFalse(invokeIsDirectChannelTypeSupported(jni, baseVM, useVaList, sensor, 1));
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.isDirectChannelTypeSupported");
            assertEquals("sensorType=1,sharedMemType=1,result=false", String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsDirectChannelTypeSupportedNotHandled(
            boolean is64Bit, boolean useVaList, String json, int sensorType) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            expectSensorDirectChannelUoe(jni, baseVM, useVaList, sensor, 1, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsDirectChannelTypeSupportedIsolation(
            boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_DIRECT_CHANNEL_STATIC_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            expectSensorDirectChannelUoe(jni, baseVM, useVaList, plain, 1, sink);
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            expectSensorDirectChannelUoe(jni, baseVM, useVaList, sensor, 1, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsDirectChannelTypeSupportedMarkerConfigInstanceStale(
            boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(SENSORS_DIRECT_CHANNEL_A_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(SENSORS_DIRECT_CHANNEL_B_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensorA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertTrue(invokeIsDirectChannelTypeSupported(jni, baseVM, useVaList, sensorA, 1));
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            expectSensorDirectChannelUoe(jni, baseVM, useVaList, sensorA, 1, sink);
            DvmObject<?> managerB = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensorB = invokeGetDefaultSensor(jni, baseVM, useVaList, managerB, 1);
            assertFalse(invokeIsDirectChannelTypeSupported(jni, baseVM, useVaList, sensorB, 1));
            assertTrue(invokeIsDirectChannelTypeSupported(jni, baseVM, useVaList, sensorB, 2));
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            expectSensorDirectChannelUoe(jni, baseVM, useVaList, sensorB, 1, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsDirectChannelTypeSupportedParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"directChannelTypesSupported\":[]}}}",
                "android.sensors.directChannelTypesSupported");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{\"1\":{}}}}}",
                "android.sensors.directChannelTypesSupported.1");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{\"1\":1}}}}",
                "android.sensors.directChannelTypesSupported.1");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{\"1\":[0]}}}}",
                "android.sensors.directChannelTypesSupported.1[0]");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{\"1\":[3]}}}}",
                "android.sensors.directChannelTypesSupported.1[0]");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{\"1\":[-1]}}}}",
                "android.sensors.directChannelTypesSupported.1[0]");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{\"1\":[1.0]}}}}",
                "android.sensors.directChannelTypesSupported.1[0]");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{\"1\":[1,1]}}}}",
                "android.sensors.directChannelTypesSupported.1[1]");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{\"4\":[1]}}}}",
                "android.sensors.directChannelTypesSupported.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"directChannelTypesSupported\":{\"1\":[1]}}}}",
                "android.sensors.directChannelTypesSupported.1");
        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_DIRECT_CHANNEL_STATIC_JSON);
        assertTrue(ok.isAndroidSensorDirectChannelTypesSupportedConfigured());
        assertEquals(Boolean.TRUE, ok.isAndroidSensorDirectChannelTypeSupported(1, 1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_DIRECT_CHANNEL_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorDirectChannelTypesSupportedConfigured());
        assertFalse(empty.isAndroidSensorDirectChannelTypeSupportedConfigured(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorDirectChannelTypesSupportedConfigured());
        TraceEnvironmentConfig rateOnly = TraceEnvironmentConfig.parse(SENSORS_DIRECT_RATE_STATIC_JSON);
        assertFalse(rateOnly.isAndroidSensorDirectChannelTypesSupportedConfigured());
    }

    private static void runSensorIsDynamicSensorStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_DYNAMIC_FLAG_STATIC_JSON);
        assertEquals(Boolean.FALSE, config.getAndroidSensorDynamicSensor(1));
        assertFalse(config.isAndroidSensorDynamicSensorConfigured(4));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertFalse(invokeIsDynamicSensor(jni, baseVM, useVaList, sensor));
            assertFalse(invokeIsDynamicSensorByMethod(jni, baseVM, useVaList, sensor));
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.isDynamicSensor");
            assertEquals("sensorType=1,dynamic=false", String.valueOf(ev.value));
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.isDynamicSensor"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsDynamicSensorDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_DYNAMIC_FLAG_DYNAMIC_JSON);
        assertEquals(Boolean.TRUE, config.getAndroidSensorDynamicSensor(7));
        assertFalse(config.isAndroidSensorDynamicSensorConfigured(1));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7)
                    .getValue().get(0);
            assertTrue(invokeIsDynamicSensor(jni, baseVM, useVaList, sensor));
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor", "Sensor.isDynamicSensor");
            assertEquals("sensorType=7,dynamic=true", String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsDynamicSensorNotHandledOnLiveMarker(
            boolean is64Bit, boolean useVaList, String json, int sensorType) throws Exception {
        runSensorBooleanNotHandledOnLiveMarker(is64Bit, useVaList, json, sensorType,
                "isDynamicSensor", "Sensor.isDynamicSensor", false);
    }

    private static void runSensorIsDynamicSensorNotHandledOnLiveDynamicType(
            boolean is64Bit, boolean useVaList, String json, int sensorType) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        assertFalse(config.isAndroidSensorDynamicSensorConfigured(sensorType));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, sensorType)
                    .getValue().get(0);
            expectSensorBooleanUoe(jni, baseVM, useVaList, sensor, "isDynamicSensor",
                    "Sensor.isDynamicSensor", sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsDynamicSensorIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runSensorBooleanIsolation(is64Bit, useVaList, SENSORS_DYNAMIC_FLAG_STATIC_JSON,
                "isDynamicSensor", "Sensor.isDynamicSensor");
    }

    private static void runSensorIsDynamicSensorMarkerConfigInstanceStale(
            boolean is64Bit, boolean useVaList) throws Exception {
        runSensorBooleanMarkerConfigInstanceStale(is64Bit, useVaList, SENSORS_DYNAMIC_FLAG_A_JSON,
                SENSORS_DYNAMIC_FLAG_B_JSON, "isDynamicSensor", "Sensor.isDynamicSensor",
                false, true);
    }

    private static void runSensorIsDynamicSensorParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicSensors\":[]}}}",
                "android.sensors.dynamicSensors");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"dynamicSensors\":{\"1\":1}}}}",
                "android.sensors.dynamicSensors.1");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"dynamicSensors\":{\"1\":\"true\"}}}}",
                "android.sensors.dynamicSensors.1");
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"dynamicSensors\":{\"4\":true}}}}",
                "android.sensors.dynamicSensors.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"dynamicSensors\":{\"1\":true}}}}",
                "android.sensors.dynamicSensors.1");
        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_DYNAMIC_FLAG_STATIC_JSON);
        assertTrue(ok.isAndroidSensorDynamicSensorsConfigured());
        assertEquals(Boolean.FALSE, ok.getAndroidSensorDynamicSensor(1));
        TraceEnvironmentConfig listedOnly = TraceEnvironmentConfig.parse(
                SENSORS_DYNAMIC_TYPES_WITHOUT_FLAG_JSON);
        assertFalse(listedOnly.isAndroidSensorDynamicSensorsConfigured());
        assertFalse(listedOnly.isAndroidSensorDynamicSensorConfigured(7));
        assertNull(listedOnly.getAndroidSensorDynamicSensor(7));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_DYNAMIC_FLAG_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorDynamicSensorsConfigured());
        assertFalse(empty.isAndroidSensorDynamicSensorConfigured(7));
    }

    private static void runSensorGetRequiredPermissionStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_PERM_STATIC_JSON);
        assertEquals("", config.getAndroidSensorRequiredPermission(1));
        assertFalse(config.isAndroidSensorRequiredPermissionConfigured(4));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            DvmObject<?> perm = invokeGetRequiredPermission(jni, baseVM, useVaList, sensor);
            assertTrue(perm instanceof StringObject);
            assertEquals("", ((StringObject) perm).getValue());
            DvmObject<?> perm2 = invokeGetRequiredPermissionByMethod(jni, baseVM, useVaList, sensor);
            assertEquals("", ((StringObject) perm2).getValue());
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.getRequiredPermission");
            assertEquals("sensorType=1,permissionLength=0", String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains("android.permission"));
            assertEquals(2, countEvents(sink.events, "android_sensor", "Sensor.getRequiredPermission"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetRequiredPermissionDynamicOnlyType(boolean is64Bit, boolean useVaList)
            throws Exception {
        final String expected = "android.permission.BODY_SENSORS";
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_PERM_DYNAMIC_JSON);
        assertEquals(expected, config.getAndroidSensorRequiredPermission(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7)
                    .getValue().get(0);
            DvmObject<?> perm = invokeGetRequiredPermission(jni, baseVM, useVaList, sensor);
            assertEquals(expected, ((StringObject) perm).getValue());
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.getRequiredPermission");
            assertEquals("sensorType=7,permissionLength=" + expected.length(),
                    String.valueOf(ev.value));
            assertFalse(String.valueOf(ev.value).contains(expected));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorGetRequiredPermissionNotHandledOnLiveMarker(
            boolean is64Bit, boolean useVaList, String json, int sensorType) throws Exception {
        runSensorObjectNotHandledOnLiveMarker(is64Bit, useVaList, json, sensorType,
                "getRequiredPermission", "()Ljava/lang/String;", "Sensor.getRequiredPermission");
    }

    private static void runSensorGetRequiredPermissionIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runSensorObjectIsolation(is64Bit, useVaList, SENSORS_PERM_STATIC_JSON,
                "getRequiredPermission", "()Ljava/lang/String;", "Sensor.getRequiredPermission");
    }

    private static void runSensorGetRequiredPermissionMarkerConfigInstanceStale(
            boolean is64Bit, boolean useVaList) throws Exception {
        runSensorObjectMarkerConfigInstanceStale(is64Bit, useVaList, SENSORS_PERM_A_JSON,
                SENSORS_PERM_B_JSON, "getRequiredPermission", "()Ljava/lang/String;",
                "Sensor.getRequiredPermission",
                "android.permission.BODY_SENSORS",
                "android.permission.HIGH_SAMPLING_RATE_SENSORS");
    }

    private static void runSensorGetRequiredPermissionParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"requiredPermissions\":[]}}}",
                "android.sensors.requiredPermissions");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"requiredPermissions\":{\"1\":1}}}}",
                "android.sensors.requiredPermissions.1");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"requiredPermissions\":{\"1\":null}}}}",
                "android.sensors.requiredPermissions.1");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"requiredPermissions\":{\"1\":\"a\\nb\"}}}}",
                "android.sensors.requiredPermissions.1");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"requiredPermissions\":{\"4\":\"x\"}}}}",
                "android.sensors.requiredPermissions.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"requiredPermissions\":{\"1\":\"x\"}}}}",
                "android.sensors.requiredPermissions.1");
        String tooLong = new String(new char[257]).replace('\0', 'p');
        assertParseInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"requiredPermissions\":{\"1\":\""
                + tooLong + "\"}}}}", "android.sensors.requiredPermissions.1");
        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_PERM_STATIC_JSON);
        assertTrue(ok.isAndroidSensorRequiredPermissionsConfigured());
        assertEquals("", ok.getAndroidSensorRequiredPermission(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_PERM_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorRequiredPermissionsConfigured());
        assertFalse(empty.isAndroidSensorRequiredPermissionConfigured(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorRequiredPermissionsConfigured());
    }

    private static void runSensorIsAdditionalInfoSupportedStaticType(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_ADDITIONAL_STATIC_JSON);
        assertEquals(Boolean.FALSE, config.getAndroidSensorAdditionalInfoSupported(1));
        assertFalse(config.isAndroidSensorAdditionalInfoSupportedConfigured(4));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertFalse(invokeIsAdditionalInfoSupported(jni, baseVM, useVaList, sensor));
            assertFalse(invokeIsAdditionalInfoSupportedByMethod(jni, baseVM, useVaList, sensor));
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.isAdditionalInfoSupported");
            assertEquals("sensorType=1,additionalInfo=false", String.valueOf(ev.value));
            assertEquals(2, countEvents(sink.events, "android_sensor",
                    "Sensor.isAdditionalInfoSupported"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsAdditionalInfoSupportedDynamicOnlyType(
            boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SENSORS_ADDITIONAL_DYNAMIC_JSON);
        assertEquals(Boolean.TRUE, config.getAndroidSensorAdditionalInfoSupported(7));
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDynamicSensorList(jni, baseVM, useVaList, manager, 7)
                    .getValue().get(0);
            assertTrue(invokeIsAdditionalInfoSupported(jni, baseVM, useVaList, sensor));
            CapturedEvent ev = findLastEvent(sink.events, "android_sensor",
                    "Sensor.isAdditionalInfoSupported");
            assertEquals("sensorType=7,additionalInfo=true", String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIsAdditionalInfoSupportedNotHandledOnLiveMarker(
            boolean is64Bit, boolean useVaList, String json, int sensorType) throws Exception {
        runSensorBooleanNotHandledOnLiveMarker(is64Bit, useVaList, json, sensorType,
                "isAdditionalInfoSupported", "Sensor.isAdditionalInfoSupported", false);
    }

    private static void runSensorIsAdditionalInfoSupportedIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runSensorBooleanIsolation(is64Bit, useVaList, SENSORS_ADDITIONAL_STATIC_JSON,
                "isAdditionalInfoSupported", "Sensor.isAdditionalInfoSupported");
    }

    private static void runSensorIsAdditionalInfoSupportedMarkerConfigInstanceStale(
            boolean is64Bit, boolean useVaList) throws Exception {
        runSensorBooleanMarkerConfigInstanceStale(is64Bit, useVaList, SENSORS_ADDITIONAL_A_JSON,
                SENSORS_ADDITIONAL_B_JSON, "isAdditionalInfoSupported",
                "Sensor.isAdditionalInfoSupported", false, true);
    }

    private static void runSensorIsAdditionalInfoSupportedParseRejects() {
        assertParseInvalid("{\"android\":{\"sensors\":{\"additionalInfoSupported\":[]}}}",
                "android.sensors.additionalInfoSupported");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"additionalInfoSupported\":{\"1\":1}}}}",
                "android.sensors.additionalInfoSupported.1");
        assertParseInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"additionalInfoSupported\":{\"4\":true}}}}",
                "android.sensors.additionalInfoSupported.4");
        assertParseInvalid("{\"android\":{\"sensors\":{\"additionalInfoSupported\":{\"1\":true}}}}",
                "android.sensors.additionalInfoSupported.1");
        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(SENSORS_ADDITIONAL_STATIC_JSON);
        assertTrue(ok.isAndroidSensorAdditionalInfoSupportedConfigured());
        assertEquals(Boolean.FALSE, ok.getAndroidSensorAdditionalInfoSupported(1));
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(SENSORS_ADDITIONAL_EMPTY_JSON);
        assertTrue(empty.isAndroidSensorAdditionalInfoSupportedConfigured());
        assertFalse(empty.isAndroidSensorAdditionalInfoSupportedConfigured(1));
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON);
        assertFalse(omitted.isAndroidSensorAdditionalInfoSupportedConfigured());
    }

    private static void runSensorIntNotHandledOnLiveMarker(boolean is64Bit, boolean useVaList,
                                                           String json, int sensorType,
                                                           String methodName, String api)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            expectSensorIntUoe(jni, baseVM, useVaList, sensor, methodName, api, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorBooleanNotHandledOnLiveMarker(boolean is64Bit, boolean useVaList,
                                                               String json, int sensorType,
                                                               String methodName, String api,
                                                               boolean unused) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            expectSensorBooleanUoe(jni, baseVM, useVaList, sensor, methodName, api, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorObjectNotHandledOnLiveMarker(boolean is64Bit, boolean useVaList,
                                                              String json, int sensorType,
                                                              String methodName, String args,
                                                              String api) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, sensorType);
            assertNotNull(sensor);
            expectSensorObjectUoe(jni, baseVM, useVaList, sensor, methodName, args, api, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIntIsolation(boolean is64Bit, boolean useVaList, String json,
                                              String methodName, String api) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            expectSensorIntUoe(jni, baseVM, useVaList, plain, methodName, api, sink);
            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_SENSORS_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                DvmObject<?> absentPlain = absentVm.resolveClass(SENSOR_CLASS).newObject(null);
                expectSensorIntUoe(absentJni, (BaseVM) absentVm, useVaList, absentPlain, methodName,
                        api, absentSink);
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_sensor event without sensors node: " + e.api,
                            "android_sensor".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }
            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveSensorSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> foreign = invokeGetDefaultSensor(jni2, baseVM2, useVaList, manager2, 1);
                expectSensorIntUoe(jni, baseVM, useVaList, foreign, methodName, api, sink);
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            expectSensorIntUoe(jni, baseVM, useVaList, sensor, methodName, api, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorBooleanIsolation(boolean is64Bit, boolean useVaList, String json,
                                                  String methodName, String api) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            expectSensorBooleanUoe(jni, baseVM, useVaList, plain, methodName, api, sink);
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            expectSensorBooleanUoe(jni, baseVM, useVaList, sensor, methodName, api, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorObjectIsolation(boolean is64Bit, boolean useVaList, String json,
                                                 String methodName, String args, String api)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensor = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            DvmObject<?> plain = vm.resolveClass(SENSOR_CLASS).newObject(null);
            expectSensorObjectUoe(jni, baseVM, useVaList, plain, methodName, args, api, sink);
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(SENSORS_TYPES_JSON));
            expectSensorObjectUoe(jni, baseVM, useVaList, sensor, methodName, args, api, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorIntMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList,
                                                              String jsonA, String jsonB,
                                                              String methodName, String api,
                                                              int expectedA, int expectedB)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(jsonA);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(jsonB);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensorA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertEquals(expectedA, invokeSensorInt(jni, baseVM, useVaList, sensorA, methodName));
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            expectSensorIntUoe(jni, baseVM, useVaList, sensorA, methodName, api, sink);
            DvmObject<?> managerB = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensorB = invokeGetDefaultSensor(jni, baseVM, useVaList, managerB, 1);
            assertEquals(expectedB, invokeSensorInt(jni, baseVM, useVaList, sensorB, methodName));
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            expectSensorIntUoe(jni, baseVM, useVaList, sensorB, methodName, api, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorBooleanMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList,
                                                                  String jsonA, String jsonB,
                                                                  String methodName, String api,
                                                                  boolean expectedA, boolean expectedB)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(jsonA);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(jsonB);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensorA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            assertEquals(expectedA, invokeSensorBoolean(jni, baseVM, useVaList, sensorA, methodName));
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            expectSensorBooleanUoe(jni, baseVM, useVaList, sensorA, methodName, api, sink);
            DvmObject<?> managerB = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensorB = invokeGetDefaultSensor(jni, baseVM, useVaList, managerB, 1);
            assertEquals(expectedB, invokeSensorBoolean(jni, baseVM, useVaList, sensorB, methodName));
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            expectSensorBooleanUoe(jni, baseVM, useVaList, sensorB, methodName, api, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSensorObjectMarkerConfigInstanceStale(boolean is64Bit, boolean useVaList,
                                                                 String jsonA, String jsonB,
                                                                 String methodName, String args,
                                                                 String api,
                                                                 String expectedA, String expectedB)
            throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(jsonA);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(jsonB);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensorA = invokeGetDefaultSensor(jni, baseVM, useVaList, manager, 1);
            DvmObject<?> valueA = invokeSensorObject(jni, baseVM, useVaList, sensorA, methodName, args);
            assertEquals(expectedA, ((StringObject) valueA).getValue());
            emulator.set(TraceEnvironmentConfig.KEY, configB);
            expectSensorObjectUoe(jni, baseVM, useVaList, sensorA, methodName, args, api, sink);
            DvmObject<?> managerB = resolveSensorSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> sensorB = invokeGetDefaultSensor(jni, baseVM, useVaList, managerB, 1);
            DvmObject<?> valueB = invokeSensorObject(jni, baseVM, useVaList, sensorB, methodName, args);
            assertEquals(expectedB, ((StringObject) valueB).getValue());
            emulator.set(TraceEnvironmentConfig.KEY, configA);
            expectSensorObjectUoe(jni, baseVM, useVaList, sensorB, methodName, args, api, sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void expectSensorIntUoe(AbstractJni jni, BaseVM vm, boolean useVaList,
                                           DvmObject<?> target, String methodName, String api,
                                           CapturingSink sink) {
        int before = countEvents(sink.events, "android_sensor", api);
        try {
            invokeSensorInt(jni, vm, useVaList, target, methodName);
            fail("expected UOE for " + methodName);
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null && expected.getMessage().contains(methodName));
        }
        assertEquals(before, countEvents(sink.events, "android_sensor", api));
    }

    private static void expectSensorBooleanUoe(AbstractJni jni, BaseVM vm, boolean useVaList,
                                               DvmObject<?> target, String methodName, String api,
                                               CapturingSink sink) {
        int before = countEvents(sink.events, "android_sensor", api);
        try {
            invokeSensorBoolean(jni, vm, useVaList, target, methodName);
            fail("expected UOE for " + methodName);
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null && expected.getMessage().contains(methodName));
        }
        assertEquals(before, countEvents(sink.events, "android_sensor", api));
    }

    private static void expectSensorObjectUoe(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> target, String methodName, String args,
                                              String api, CapturingSink sink) {
        int before = countEvents(sink.events, "android_sensor", api);
        try {
            invokeSensorObject(jni, vm, useVaList, target, methodName, args);
            fail("expected UOE for " + methodName);
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null && expected.getMessage().contains(methodName));
        }
        assertEquals(before, countEvents(sink.events, "android_sensor", api));
    }

    private static int invokeSensorInt(AbstractJni jni, BaseVM vm, boolean useVaList,
                                       DvmObject<?> target, String methodName) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeSensorBoolean(AbstractJni jni, BaseVM vm, boolean useVaList,
                                               DvmObject<?> target, String methodName) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static DvmObject<?> invokeSensorObject(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmObject<?> target, String methodName,
                                                   String args) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callObjectMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static int invokeGetId(AbstractJni jni, BaseVM vm, boolean useVaList, DvmObject<?> target) {
        return invokeSensorInt(jni, vm, useVaList, target, "getId");
    }

    private static int invokeGetIdByMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                           DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getId", "()I", false);
        if (useVaList) {
            return jni.callIntMethodV(vm, target, method, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, method, new TestNoArgVarArg(vm, method));
    }

    private static int invokeGetReportingMode(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> target) {
        return invokeSensorInt(jni, vm, useVaList, target, "getReportingMode");
    }

    private static int invokeGetReportingModeByMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                      DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getReportingMode", "()I", false);
        if (useVaList) {
            return jni.callIntMethodV(vm, target, method, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, method, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsDynamicSensor(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> target) {
        return invokeSensorBoolean(jni, vm, useVaList, target, "isDynamicSensor");
    }

    private static boolean invokeIsDynamicSensorByMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                         DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isDynamicSensor", "()Z", false);
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, method, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, method, new TestNoArgVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetRequiredPermission(AbstractJni jni, BaseVM vm,
                                                            boolean useVaList, DvmObject<?> target) {
        return invokeSensorObject(jni, vm, useVaList, target, "getRequiredPermission",
                "()Ljava/lang/String;");
    }

    private static DvmObject<?> invokeGetRequiredPermissionByMethod(AbstractJni jni, BaseVM vm,
                                                                    boolean useVaList,
                                                                    DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getRequiredPermission",
                "()Ljava/lang/String;", false);
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, method, new TestNoArgVaList(vm, method));
        }
        return jni.callObjectMethod(vm, target, method, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsAdditionalInfoSupported(AbstractJni jni, BaseVM vm,
                                                           boolean useVaList, DvmObject<?> target) {
        return invokeSensorBoolean(jni, vm, useVaList, target, "isAdditionalInfoSupported");
    }

    private static boolean invokeIsDirectChannelTypeSupported(AbstractJni jni, BaseVM vm,
                                                              boolean useVaList, DvmObject<?> target,
                                                              int sharedMemType) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isDirectChannelTypeSupported", "(I)Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature,
                    new TestIntVaList(vm, method, sharedMemType));
        }
        return jni.callBooleanMethod(vm, target, signature,
                new TestIntVarArg(vm, method, sharedMemType));
    }

    private static void expectSensorDirectChannelUoe(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                     DvmObject<?> target, int sharedMemType,
                                                     CapturingSink sink) {
        int before = countEvents(sink.events, "android_sensor",
                "Sensor.isDirectChannelTypeSupported");
        try {
            invokeIsDirectChannelTypeSupported(jni, vm, useVaList, target, sharedMemType);
            fail("expected UOE for isDirectChannelTypeSupported");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("isDirectChannelTypeSupported"));
        }
        assertEquals(before, countEvents(sink.events, "android_sensor",
                "Sensor.isDirectChannelTypeSupported"));
    }

    private static boolean invokeIsAdditionalInfoSupportedByMethod(AbstractJni jni, BaseVM vm,
                                                                   boolean useVaList,
                                                                   DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isAdditionalInfoSupported", "()Z", false);
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, method, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, method, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsWakeUpSensor(AbstractJni jni, BaseVM vm, boolean useVaList,
                                               DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isWakeUpSensor", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    /** Same signature via the {@link DvmMethod} {@code callBooleanMethod}/{@code V} overload. */
    private static boolean invokeIsWakeUpSensorByMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isWakeUpSensor", "()Z", false);
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, method, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, method, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsDynamicSensorDiscoverySupported(AbstractJni jni, BaseVM vm,
                                                                  boolean useVaList,
                                                                  DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(SENSOR_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isDynamicSensorDiscoverySupported",
                "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static CapturedEvent findLastEvent(List<CapturedEvent> events, String kind, String api) {
        CapturedEvent found = null;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)) {
                found = e;
            }
        }
        return found;
    }

    private static int countEvents(List<CapturedEvent> events, String kind, String api) {
        int n = 0;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)) {
                n++;
            }
        }
        return n;
    }

    private static final class TestObjectVarArg extends VarArg {
        TestObjectVarArg(BaseVM vm, DvmMethod method, int objectHash) {
            super(vm, method);
            args.add(objectHash);
        }
    }

    private static final class TestObjectVaList extends VaList {
        TestObjectVaList(BaseVM vm, DvmMethod method, int objectHash) {
            super(vm, method);
            args.add(objectHash);
        }
    }

    private static final class TestIntVarArg extends VarArg {
        TestIntVarArg(BaseVM vm, DvmMethod method, int value) {
            super(vm, method);
            args.add(value);
        }
    }

    private static final class TestIntVaList extends VaList {
        TestIntVaList(BaseVM vm, DvmMethod method, int value) {
            super(vm, method);
            args.add(value);
        }
    }

    private static final class TestIntBooleanVarArg extends VarArg {
        TestIntBooleanVarArg(BaseVM vm, DvmMethod method, int sensorType, boolean wakeUp) {
            super(vm, method);
            args.add(Integer.valueOf(sensorType));
            args.add(Integer.valueOf(wakeUp ? 1 : 0));
        }
    }

    private static final class TestIntBooleanVaList extends VaList {
        TestIntBooleanVaList(BaseVM vm, DvmMethod method, int sensorType, boolean wakeUp) {
            super(vm, method);
            args.add(Integer.valueOf(sensorType));
            args.add(Integer.valueOf(wakeUp ? 1 : 0));
        }
    }

    private static final class TestNoArgVarArg extends VarArg {
        TestNoArgVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class TestNoArgVaList extends VaList {
        TestNoArgVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class CapturedEvent {
        final String kind;
        final String api;
        final Object value;
        final String source;
        final String note;

        CapturedEvent(String kind, String api, Object value, String source, String note) {
            this.kind = kind;
            this.api = api;
            this.value = value;
            this.source = source;
            this.note = note;
        }
    }

    private static final class CapturingSink implements TraceEnvironmentEventSink {
        final List<CapturedEvent> events = new ArrayList<CapturedEvent>();

        @Override
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}

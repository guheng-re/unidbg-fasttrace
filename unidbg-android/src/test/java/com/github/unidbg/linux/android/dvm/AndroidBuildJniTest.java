package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Coverage for {@code android.build} static field ABI arrays, {@code Build.getSerial()},
 * {@code Build.TIME:J}, and sidecar isolation.
 */
public class AndroidBuildJniTest {

    private static final String SUPPORTED_ABIS_SIGNATURE =
            "android/os/Build->SUPPORTED_ABIS:[Ljava/lang/String;";
    private static final String SUPPORTED_32_BIT_ABIS_SIGNATURE =
            "android/os/Build->SUPPORTED_32_BIT_ABIS:[Ljava/lang/String;";
    private static final String SUPPORTED_64_BIT_ABIS_SIGNATURE =
            "android/os/Build->SUPPORTED_64_BIT_ABIS:[Ljava/lang/String;";
    private static final String GET_SERIAL_SIGNATURE =
            "android/os/Build->getSerial()Ljava/lang/String;";
    private static final String TIME_SIGNATURE =
            "android/os/Build->TIME:J";
    private static final String TIME_STRING_SIGNATURE =
            "android/os/Build->TIME:Ljava/lang/String;";

    private static final String ABIS_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"build\":{"
            + "\"MODEL\":\"Pixel 6\","
            + "\"SUPPORTED_ABIS\":[\"arm64-v8a\",\"armeabi-v7a\",\"armeabi\"]"
            + "}"
            + "}"
            + "}";

    private static final String SERIAL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"build\":{"
            + "\"MODEL\":\"Pixel 6\","
            + "\"SERIAL\":\"ABCDEF0123456789\""
            + "}"
            + "}"
            + "}";

    private static final String SERIAL_MISSING_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"build\":{\"MODEL\":\"Pixel 6\"}"
            + "}"
            + "}";

    private static final String ABIS_32_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"build\":{"
            + "\"MODEL\":\"Pixel 6\","
            + "\"SUPPORTED_32_BIT_ABIS\":[\"armeabi-v7a\",\"armeabi\"]"
            + "}"
            + "}"
            + "}";

    private static final String ABIS_64_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"build\":{"
            + "\"MODEL\":\"Pixel 6\","
            + "\"SUPPORTED_64_BIT_ABIS\":[\"arm64-v8a\"]"
            + "}"
            + "}"
            + "}";

    private static final String ALL_THREE_ABIS_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"build\":{"
            + "\"SUPPORTED_ABIS\":[\"arm64-v8a\",\"armeabi-v7a\"],"
            + "\"SUPPORTED_32_BIT_ABIS\":[\"armeabi-v7a\",\"armeabi\"],"
            + "\"SUPPORTED_64_BIT_ABIS\":[\"arm64-v8a\"]"
            + "}"
            + "}"
            + "}";

    private static final String NO_ABIS_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"build\":{\"MODEL\":\"Pixel 6\"}"
            + "}"
            + "}";

    private static final String NO_BUILD_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    private static final String TIME_ZERO_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"build\":{\"TIME\":0}"
            + "}"
            + "}";

    private static final String TIME_POSITIVE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"build\":{\"TIME\":1640995200000,\"MODEL\":\"Pixel 6\"}"
            + "}"
            + "}";

    private static final String TIME_ABSENT_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"build\":{\"MODEL\":\"Pixel 6\"}"
            + "}"
            + "}";

    @Test
    public void testBuildTimeZero32() throws Exception {
        runBuildTimeConfigured(false, TIME_ZERO_JSON, 0L);
    }

    @Test
    public void testBuildTimeZero64() throws Exception {
        runBuildTimeConfigured(true, TIME_ZERO_JSON, 0L);
    }

    @Test
    public void testBuildTimePositive32() throws Exception {
        runBuildTimeConfigured(false, TIME_POSITIVE_JSON, 1640995200000L);
    }

    @Test
    public void testBuildTimePositive64() throws Exception {
        runBuildTimeConfigured(true, TIME_POSITIVE_JSON, 1640995200000L);
    }

    @Test
    public void testBuildTimeAbsentNoEvent32() throws Exception {
        runBuildTimeAbsent(false, TIME_ABSENT_JSON);
        runBuildTimeAbsent(false, NO_BUILD_JSON);
    }

    @Test
    public void testBuildTimeAbsentNoEvent64() throws Exception {
        runBuildTimeAbsent(true, TIME_ABSENT_JSON);
        runBuildTimeAbsent(true, NO_BUILD_JSON);
    }

    @Test
    public void testBuildTimeWrongStringSignatureNoCoercion32() throws Exception {
        runBuildTimeWrongStringSignature(false);
    }

    @Test
    public void testBuildTimeWrongStringSignatureNoCoercion64() throws Exception {
        runBuildTimeWrongStringSignature(true);
    }

    @Test
    public void testSupportedAbisConfigured32() throws Exception {
        runConfiguredSupportedAbis(false);
    }

    @Test
    public void testSupportedAbisConfigured64() throws Exception {
        runConfiguredSupportedAbis(true);
    }

    @Test
    public void testSupported32BitAbisConfigured32() throws Exception {
        runConfiguredSupported32BitAbis(false);
    }

    @Test
    public void testSupported32BitAbisConfigured64() throws Exception {
        runConfiguredSupported32BitAbis(true);
    }

    @Test
    public void testSupported64BitAbisConfigured32() throws Exception {
        runConfiguredSupported64BitAbis(false);
    }

    @Test
    public void testSupported64BitAbisConfigured64() throws Exception {
        runConfiguredSupported64BitAbis(true);
    }

    @Test
    public void testThreeWayAbisIndependenceAndFreshArrays() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ALL_THREE_ABIS_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit()
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            DvmObject<?> all1 = jni.getStaticObjectField(baseVM, buildClass, SUPPORTED_ABIS_SIGNATURE);
            DvmObject<?> bit32_1 = jni.getStaticObjectField(baseVM, buildClass,
                    SUPPORTED_32_BIT_ABIS_SIGNATURE);
            DvmObject<?> bit64_1 = jni.getStaticObjectField(baseVM, buildClass,
                    SUPPORTED_64_BIT_ABIS_SIGNATURE);
            assertAbis((ArrayObject) all1, new String[] {"arm64-v8a", "armeabi-v7a"});
            assertAbis((ArrayObject) bit32_1, new String[] {"armeabi-v7a", "armeabi"});
            assertAbis((ArrayObject) bit64_1, new String[] {"arm64-v8a"});
            assertNotSame(all1, bit32_1);
            assertNotSame(all1, bit64_1);
            assertNotSame(bit32_1, bit64_1);

            DvmObject<?> all2 = jni.getStaticObjectField(baseVM, buildClass, SUPPORTED_ABIS_SIGNATURE);
            DvmObject<?> bit32_2 = jni.getStaticObjectField(baseVM, buildClass,
                    SUPPORTED_32_BIT_ABIS_SIGNATURE);
            DvmObject<?> bit64_2 = jni.getStaticObjectField(baseVM, buildClass,
                    SUPPORTED_64_BIT_ABIS_SIGNATURE);
            assertNotSame(all1, all2);
            assertNotSame(bit32_1, bit32_2);
            assertNotSame(bit64_1, bit64_2);

            assertEvent(sink, "Build.SUPPORTED_ABIS",
                    "count=2,abis=arm64-v8a,armeabi-v7a");
            assertEvent(sink, "Build.SUPPORTED_32_BIT_ABIS",
                    "count=2,abis=armeabi-v7a,armeabi");
            assertEvent(sink, "Build.SUPPORTED_64_BIT_ABIS",
                    "count=1,abis=arm64-v8a");
            assertEquals(2, countEvents(sink.events, "android_build", "Build.SUPPORTED_ABIS"));
            assertEquals(2, countEvents(sink.events, "android_build", "Build.SUPPORTED_32_BIT_ABIS"));
            assertEquals(2, countEvents(sink.events, "android_build", "Build.SUPPORTED_64_BIT_ABIS"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testSupportedAbisUnconfiguredIsolation32() throws Exception {
        runUnconfiguredIsolation(false, NO_ABIS_JSON, SUPPORTED_ABIS_SIGNATURE);
        runUnconfiguredIsolation(false, NO_BUILD_JSON, SUPPORTED_ABIS_SIGNATURE);
        runUnconfiguredIsolation(false, ABIS_32_JSON, SUPPORTED_ABIS_SIGNATURE);
        runUnconfiguredIsolation(false, ABIS_64_JSON, SUPPORTED_ABIS_SIGNATURE);
    }

    @Test
    public void testSupportedAbisUnconfiguredIsolation64() throws Exception {
        runUnconfiguredIsolation(true, NO_ABIS_JSON, SUPPORTED_ABIS_SIGNATURE);
        runUnconfiguredIsolation(true, NO_BUILD_JSON, SUPPORTED_ABIS_SIGNATURE);
        runUnconfiguredIsolation(true, ABIS_32_JSON, SUPPORTED_ABIS_SIGNATURE);
        runUnconfiguredIsolation(true, ABIS_64_JSON, SUPPORTED_ABIS_SIGNATURE);
    }

    @Test
    public void testSupported32BitAbisUnconfiguredIsolation32() throws Exception {
        runUnconfiguredIsolation(false, NO_ABIS_JSON, SUPPORTED_32_BIT_ABIS_SIGNATURE);
        runUnconfiguredIsolation(false, NO_BUILD_JSON, SUPPORTED_32_BIT_ABIS_SIGNATURE);
        runUnconfiguredIsolation(false, ABIS_JSON, SUPPORTED_32_BIT_ABIS_SIGNATURE);
        runUnconfiguredIsolation(false, ABIS_64_JSON, SUPPORTED_32_BIT_ABIS_SIGNATURE);
    }

    @Test
    public void testSupported32BitAbisUnconfiguredIsolation64() throws Exception {
        runUnconfiguredIsolation(true, NO_ABIS_JSON, SUPPORTED_32_BIT_ABIS_SIGNATURE);
        runUnconfiguredIsolation(true, NO_BUILD_JSON, SUPPORTED_32_BIT_ABIS_SIGNATURE);
        runUnconfiguredIsolation(true, ABIS_JSON, SUPPORTED_32_BIT_ABIS_SIGNATURE);
        runUnconfiguredIsolation(true, ABIS_64_JSON, SUPPORTED_32_BIT_ABIS_SIGNATURE);
    }

    @Test
    public void testSupported64BitAbisUnconfiguredIsolation32() throws Exception {
        runUnconfiguredIsolation(false, NO_ABIS_JSON, SUPPORTED_64_BIT_ABIS_SIGNATURE);
        runUnconfiguredIsolation(false, NO_BUILD_JSON, SUPPORTED_64_BIT_ABIS_SIGNATURE);
        // neither SUPPORTED_ABIS nor 32-bit configure 64-bit
        runUnconfiguredIsolation(false, ABIS_JSON, SUPPORTED_64_BIT_ABIS_SIGNATURE);
        runUnconfiguredIsolation(false, ABIS_32_JSON, SUPPORTED_64_BIT_ABIS_SIGNATURE);
    }

    @Test
    public void testSupported64BitAbisUnconfiguredIsolation64() throws Exception {
        runUnconfiguredIsolation(true, NO_ABIS_JSON, SUPPORTED_64_BIT_ABIS_SIGNATURE);
        runUnconfiguredIsolation(true, NO_BUILD_JSON, SUPPORTED_64_BIT_ABIS_SIGNATURE);
        runUnconfiguredIsolation(true, ABIS_JSON, SUPPORTED_64_BIT_ABIS_SIGNATURE);
        runUnconfiguredIsolation(true, ABIS_32_JSON, SUPPORTED_64_BIT_ABIS_SIGNATURE);
    }

    @Test
    public void testGetSerialConfiguredVarArg32() throws Exception {
        runGetSerialConfigured(false, false);
    }

    @Test
    public void testGetSerialConfiguredVaList64() throws Exception {
        runGetSerialConfigured(true, true);
    }

    @Test
    public void testGetSerialMissingNoEventVarArg32() throws Exception {
        runGetSerialMissingNoEvent(false, false);
    }

    @Test
    public void testGetSerialMissingNoEventVaList64() throws Exception {
        runGetSerialMissingNoEvent(true, true);
    }

    private static void runGetSerialConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SERIAL_JSON);
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
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            DvmObject<?> serialObj = invokeGetSerial(jni, baseVM, useVaList, buildClass);
            assertNotNull(serialObj);
            assertTrue(serialObj instanceof StringObject);
            assertEquals("ABCDEF0123456789", ((StringObject) serialObj).getValue());

            CapturedEvent ev = findLastEvent(sink.events, "android_build", "Build.getSerial");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("serial=ABCDEF0123456789", String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_build", "Build.getSerial"));

            // generic static field Build.SERIAL still works (no getSerial event for field path)
            int eventsBeforeField = sink.events.size();
            DvmObject<?> fieldSerial = jni.getStaticObjectField(baseVM, buildClass,
                    "android/os/Build->SERIAL:Ljava/lang/String;");
            assertTrue(fieldSerial instanceof StringObject);
            assertEquals("ABCDEF0123456789", ((StringObject) fieldSerial).getValue());
            assertEquals(1, countEvents(sink.events, "android_build", "Build.getSerial"));
            for (int i = eventsBeforeField; i < sink.events.size(); i++) {
                assertFalse("field SERIAL must not emit Build.getSerial",
                        "Build.getSerial".equals(sink.events.get(i).api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetSerialMissingNoEvent(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SERIAL_MISSING_JSON);
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
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            try {
                invokeGetSerial(jni, baseVM, useVaList, buildClass);
                fail("expected UOE for getSerial without SERIAL");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSerial"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_build event when SERIAL missing: " + e.api,
                        "android_build".equals(e.kind));
            }
            assertEquals(0, countEvents(sink.events, "android_build", "Build.getSerial"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeGetSerial(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmClass buildClass) {
        DvmMethod method = new DvmMethod(buildClass, "getSerial", "()Ljava/lang/String;", true);
        String signature = method.getSignature();
        assertEquals(GET_SERIAL_SIGNATURE, signature);
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, buildClass, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, buildClass, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static void runBuildTimeConfigured(boolean is64Bit, String json, long expected)
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
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            long time = jni.getStaticLongField(baseVM, buildClass, TIME_SIGNATURE);
            assertEquals(expected, time);

            CapturedEvent ev = findLastEvent(sink.events, "android_build", "Build.TIME");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("time=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_build", "Build.TIME"));

            // second read emits again
            assertEquals(expected, jni.getStaticLongField(baseVM, buildClass, TIME_SIGNATURE));
            assertEquals(2, countEvents(sink.events, "android_build", "Build.TIME"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runBuildTimeAbsent(boolean is64Bit, String json) throws Exception {
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
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            try {
                jni.getStaticLongField(baseVM, buildClass, TIME_SIGNATURE);
                fail("expected UOE for Build.TIME without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("TIME"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_build event: " + e.api,
                        "android_build".equals(e.kind));
            }
            assertEquals(0, countEvents(sink.events, "android_build", "Build.TIME"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runBuildTimeWrongStringSignature(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(TIME_POSITIVE_JSON);
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
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            // Wrong object signature must UOE; must not coerce TIME to StringObject
            try {
                jni.getStaticObjectField(baseVM, buildClass, TIME_STRING_SIGNATURE);
                fail("expected UOE for Build.TIME as String field");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("TIME"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("string path must not emit Build.TIME sidecar: " + e.api,
                        "Build.TIME".equals(e.api));
            }

            // correct long path still works
            assertEquals(1640995200000L,
                    jni.getStaticLongField(baseVM, buildClass, TIME_SIGNATURE));
            assertEquals(1, countEvents(sink.events, "android_build", "Build.TIME"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredSupportedAbis(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ABIS_JSON);
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
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            DvmObject<?> first = jni.getStaticObjectField(baseVM, buildClass, SUPPORTED_ABIS_SIGNATURE);
            assertNotNull(first);
            assertTrue(first instanceof ArrayObject);
            assertAbis((ArrayObject) first, new String[] {"arm64-v8a", "armeabi-v7a", "armeabi"});

            assertEvent(sink, "Build.SUPPORTED_ABIS",
                    "count=3,abis=arm64-v8a,armeabi-v7a,armeabi");
            assertEquals(1, countEvents(sink.events, "android_build", "Build.SUPPORTED_ABIS"));

            DvmObject<?> second = jni.getStaticObjectField(baseVM, buildClass, SUPPORTED_ABIS_SIGNATURE);
            assertTrue(second instanceof ArrayObject);
            assertNotSame(first, second);
            assertAbis((ArrayObject) second, new String[] {"arm64-v8a", "armeabi-v7a", "armeabi"});
            assertEquals(2, countEvents(sink.events, "android_build", "Build.SUPPORTED_ABIS"));

            DvmObject<?> model = jni.getStaticObjectField(baseVM, buildClass,
                    "android/os/Build->MODEL:Ljava/lang/String;");
            assertTrue(model instanceof StringObject);
            assertEquals("Pixel 6", ((StringObject) model).getValue());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredSupported32BitAbis(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ABIS_32_JSON);
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
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            DvmObject<?> first = jni.getStaticObjectField(baseVM, buildClass,
                    SUPPORTED_32_BIT_ABIS_SIGNATURE);
            assertNotNull(first);
            assertTrue(first instanceof ArrayObject);
            assertAbis((ArrayObject) first, new String[] {"armeabi-v7a", "armeabi"});

            assertEvent(sink, "Build.SUPPORTED_32_BIT_ABIS",
                    "count=2,abis=armeabi-v7a,armeabi");
            assertEquals(1, countEvents(sink.events, "android_build", "Build.SUPPORTED_32_BIT_ABIS"));
            assertEquals(0, countEvents(sink.events, "android_build", "Build.SUPPORTED_ABIS"));
            assertEquals(0, countEvents(sink.events, "android_build", "Build.SUPPORTED_64_BIT_ABIS"));

            DvmObject<?> second = jni.getStaticObjectField(baseVM, buildClass,
                    SUPPORTED_32_BIT_ABIS_SIGNATURE);
            assertNotSame(first, second);
            assertAbis((ArrayObject) second, new String[] {"armeabi-v7a", "armeabi"});
            assertEquals(2, countEvents(sink.events, "android_build", "Build.SUPPORTED_32_BIT_ABIS"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredSupported64BitAbis(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ABIS_64_JSON);
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
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            DvmObject<?> first = jni.getStaticObjectField(baseVM, buildClass,
                    SUPPORTED_64_BIT_ABIS_SIGNATURE);
            assertNotNull(first);
            assertTrue(first instanceof ArrayObject);
            assertAbis((ArrayObject) first, new String[] {"arm64-v8a"});

            assertEvent(sink, "Build.SUPPORTED_64_BIT_ABIS",
                    "count=1,abis=arm64-v8a");
            assertEquals(1, countEvents(sink.events, "android_build", "Build.SUPPORTED_64_BIT_ABIS"));
            assertEquals(0, countEvents(sink.events, "android_build", "Build.SUPPORTED_ABIS"));
            assertEquals(0, countEvents(sink.events, "android_build", "Build.SUPPORTED_32_BIT_ABIS"));

            DvmObject<?> second = jni.getStaticObjectField(baseVM, buildClass,
                    SUPPORTED_64_BIT_ABIS_SIGNATURE);
            assertNotSame(first, second);
            assertAbis((ArrayObject) second, new String[] {"arm64-v8a"});
            assertEquals(2, countEvents(sink.events, "android_build", "Build.SUPPORTED_64_BIT_ABIS"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runUnconfiguredIsolation(boolean is64Bit, String json, String signature)
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
            DvmClass buildClass = vm.resolveClass("android/os/Build");

            try {
                jni.getStaticObjectField(baseVM, buildClass, signature);
                fail("expected UOE without config for " + signature);
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("SUPPORTED_"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_build event when unconfigured: " + e.api,
                        "android_build".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertAbis(ArrayObject array, String[] expected) {
        assertEquals(expected.length, array.length());
        DvmObject<?>[] items = array.getValue();
        assertNotNull(items);
        assertEquals(expected.length, items.length);
        for (int i = 0; i < expected.length; i++) {
            assertTrue(items[i] instanceof StringObject);
            assertEquals(expected[i], ((StringObject) items[i]).getValue());
        }
    }

    private static void assertEvent(CapturingSink sink, String api, String expectedValue) {
        CapturedEvent ev = findLastEvent(sink.events, "android_build", api);
        assertNotNull(ev);
        assertEquals("json-config", ev.source);
        assertEquals(expectedValue, String.valueOf(ev.value));
        assertNotNull(ev.note);
        assertFalse(ev.note.isEmpty());
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

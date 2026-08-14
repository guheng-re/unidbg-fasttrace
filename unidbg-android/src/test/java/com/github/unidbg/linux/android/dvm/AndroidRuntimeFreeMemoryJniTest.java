package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Coverage for {@code android.runtime.freeMemoryBytes} + instance
 * {@code Runtime.freeMemory()J}
 * （VarArg 32 位 + VaList 64 位；VM 持有 marker；无宿主 Runtime；
 * 解析期要求 totalMemoryBytes；与 availableProcessors / maxMemoryBytes / linux.cpu 独立）。
 */
public class AndroidRuntimeFreeMemoryJniTest {

    private static final String RUNTIME_CLASS = "java/lang/Runtime";
    private static final String GET_RUNTIME =
            "java/lang/Runtime->getRuntime()Ljava/lang/Runtime;";
    private static final String FREE_MEMORY = "java/lang/Runtime->freeMemory()J";
    private static final String TOTAL_MEMORY = "java/lang/Runtime->totalMemory()J";
    private static final String MAX_MEMORY = "java/lang/Runtime->maxMemory()J";
    private static final String AVAILABLE_PROCESSORS =
            "java/lang/Runtime->availableProcessors()I";

    private static final long FREE_MEMORY_BYTES = 67108864L;
    private static final long TOTAL_MEMORY_BYTES = 134217728L;
    private static final long MAX_MEMORY_BYTES = 268435456L;

    private static final String FM_TM_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"freeMemoryBytes\":" + FREE_MEMORY_BYTES
            + ",\"totalMemoryBytes\":" + TOTAL_MEMORY_BYTES + "}"
            + "}"
            + "}";

    private static final String FM_TM_MM_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"freeMemoryBytes\":" + FREE_MEMORY_BYTES
            + ",\"totalMemoryBytes\":" + TOTAL_MEMORY_BYTES
            + ",\"maxMemoryBytes\":" + MAX_MEMORY_BYTES + "}"
            + "}"
            + "}";

    private static final String FM_EQ_TM_EQ_MM_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"freeMemoryBytes\":" + TOTAL_MEMORY_BYTES
            + ",\"totalMemoryBytes\":" + TOTAL_MEMORY_BYTES
            + ",\"maxMemoryBytes\":" + TOTAL_MEMORY_BYTES + "}"
            + "}"
            + "}";

    private static final String FM_ZERO_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"freeMemoryBytes\":0"
            + ",\"totalMemoryBytes\":" + TOTAL_MEMORY_BYTES + "}"
            + "}"
            + "}";

    private static final String ALL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"freeMemoryBytes\":" + FREE_MEMORY_BYTES
            + ",\"totalMemoryBytes\":" + TOTAL_MEMORY_BYTES
            + ",\"maxMemoryBytes\":" + MAX_MEMORY_BYTES
            + ",\"availableProcessors\":8}"
            + "}"
            + "}";

    private static final String TM_ONLY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"totalMemoryBytes\":" + TOTAL_MEMORY_BYTES + "}"
            + "}"
            + "}";

    private static final String MM_ONLY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"maxMemoryBytes\":" + MAX_MEMORY_BYTES + "}"
            + "}"
            + "}";

    private static final String AP_ONLY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"availableProcessors\":8}"
            + "}"
            + "}";

    private static final String NO_RUNTIME_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    private static final String PROPS_ONLY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"systemProperties\":{\"os.name\":\"Linux\"}}"
            + "}"
            + "}";

    private static final String EMPTY_RUNTIME_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{}"
            + "}"
            + "}";

    @Test
    public void testFreeMemoryValueAndSidecarVarArg32() throws Exception {
        runValueAndSidecar(false, false);
    }

    @Test
    public void testFreeMemoryValueAndSidecarVaList64() throws Exception {
        runValueAndSidecar(true, true);
    }

    @Test
    public void testFreeMemoryZeroValueVarArg32() throws Exception {
        runZeroValue(false, false);
    }

    @Test
    public void testFreeMemoryZeroValueVaList64() throws Exception {
        runZeroValue(true, true);
    }

    @Test
    public void testNoHostLeakVarArg32() throws Exception {
        runNoHostLeak(false, false);
    }

    @Test
    public void testNoHostLeakVaList64() throws Exception {
        runNoHostLeak(true, true);
    }

    @Test
    public void testFreeTotalMaxLegalVarArg32() throws Exception {
        runFreeTotalMaxLegal(false, false);
    }

    @Test
    public void testFreeTotalMaxLegalVaList64() throws Exception {
        runFreeTotalMaxLegal(true, true);
    }

    @Test
    public void testParseRejection() {
        assertParseInvalid("{\"android\":{\"runtime\":{\"freeMemoryBytes\":1}}}",
                "android.runtime.freeMemoryBytes");
        assertParseInvalid("{\"android\":{\"runtime\":{"
                + "\"freeMemoryBytes\":134217729,\"totalMemoryBytes\":134217728}}}",
                "android.runtime.freeMemoryBytes");
        assertParseInvalid("{\"android\":{\"runtime\":{"
                + "\"freeMemoryBytes\":1,\"totalMemoryBytes\":268435457,"
                + "\"maxMemoryBytes\":268435456}}}",
                "android.runtime.totalMemoryBytes");
        assertParseInvalid("{\"android\":{\"runtime\":{"
                + "\"freeMemoryBytes\":1,\"maxMemoryBytes\":268435456}}}",
                "android.runtime.freeMemoryBytes");
        assertParseInvalid("{\"android\":{\"runtime\":{"
                + "\"freeMemoryBytes\":-1,\"totalMemoryBytes\":134217728}}}",
                "android.runtime.freeMemoryBytes");
        assertParseInvalid("{\"android\":{\"runtime\":{"
                + "\"freeMemoryBytes\":9223372036854775808,\"totalMemoryBytes\":134217728}}}",
                "android.runtime.freeMemoryBytes");
        assertParseInvalid("{\"android\":{\"runtime\":{"
                + "\"freeMemoryBytes\":1.5,\"totalMemoryBytes\":134217728}}}",
                "android.runtime.freeMemoryBytes");
        assertParseInvalid("{\"android\":{\"runtime\":{"
                + "\"freeMemoryBytes\":\"67108864\",\"totalMemoryBytes\":134217728}}}",
                "android.runtime.freeMemoryBytes");
        assertParseInvalid("{\"android\":{\"runtime\":{"
                + "\"freeMemoryBytes\":null,\"totalMemoryBytes\":134217728}}}",
                "android.runtime.freeMemoryBytes");
        assertParseInvalid("{\"android\":{\"runtime\":{"
                + "\"freeMemoryBytes\":true,\"totalMemoryBytes\":134217728}}}",
                "android.runtime.freeMemoryBytes");
        assertParseInvalid("{\"android\":{\"runtime\":{"
                + "\"freeMemoryBytes\":[],\"totalMemoryBytes\":134217728}}}",
                "android.runtime.freeMemoryBytes");

        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse(NO_RUNTIME_JSON);
        assertFalse(missing.isAndroidRuntimeFreeMemoryBytesConfigured());
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(EMPTY_RUNTIME_JSON);
        assertFalse(empty.isAndroidRuntimeFreeMemoryBytesConfigured());
        TraceEnvironmentConfig totalOnly = TraceEnvironmentConfig.parse(TM_ONLY_JSON);
        assertFalse(totalOnly.isAndroidRuntimeFreeMemoryBytesConfigured());
        assertTrue(totalOnly.isAndroidRuntimeTotalMemoryBytesConfigured());
    }

    @Test
    public void testTotalOnlyFreeMemoryUoeVarArg32() throws Exception {
        runFieldOnlyFreeMemoryUoe(TM_ONLY_JSON, false, false);
    }

    @Test
    public void testTotalOnlyFreeMemoryUoeVaList64() throws Exception {
        runFieldOnlyFreeMemoryUoe(TM_ONLY_JSON, true, true);
    }

    @Test
    public void testMaxOnlyFreeMemoryUoeVarArg32() throws Exception {
        runFieldOnlyFreeMemoryUoe(MM_ONLY_JSON, false, false);
    }

    @Test
    public void testMaxOnlyFreeMemoryUoeVaList64() throws Exception {
        runFieldOnlyFreeMemoryUoe(MM_ONLY_JSON, true, true);
    }

    @Test
    public void testAvailableOnlyFreeMemoryUoeVarArg32() throws Exception {
        runFieldOnlyFreeMemoryUoe(AP_ONLY_JSON, false, false);
    }

    @Test
    public void testAvailableOnlyFreeMemoryUoeVaList64() throws Exception {
        runFieldOnlyFreeMemoryUoe(AP_ONLY_JSON, true, true);
    }

    @Test
    public void testGetRuntimeAndAdjacentUnchangedVarArg32() throws Exception {
        runGetRuntimeAndAdjacent(false, false);
    }

    @Test
    public void testGetRuntimeAndAdjacentUnchangedVaList64() throws Exception {
        runGetRuntimeAndAdjacent(true, true);
    }

    @Test
    public void testCrossVmOrdinaryAndStaleVarArg32() throws Exception {
        runCrossVmOrdinaryAndStale(false, false);
    }

    @Test
    public void testCrossVmOrdinaryAndStaleVaList64() throws Exception {
        runCrossVmOrdinaryAndStale(true, true);
    }

    private static void runValueAndSidecar(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FM_TM_JSON);
        assertTrue(config.isAndroidRuntimeFreeMemoryBytesConfigured());
        assertEquals(FREE_MEMORY_BYTES, config.getAndroidRuntimeFreeMemoryBytes());
        assertTrue(config.isAndroidRuntimeTotalMemoryBytesConfigured());
        assertEquals(TOTAL_MEMORY_BYTES, config.getAndroidRuntimeTotalMemoryBytes());

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

            DvmObject<?> runtime = invokeGetRuntime(jni, baseVM, useVaList);
            assertNotNull(runtime);
            assertFalse("must not carry host java.lang.Runtime",
                    runtime.getValue() instanceof java.lang.Runtime);
            assertTrue(runtime.getValue().getClass().getName().contains("ConfiguredRuntime"));

            assertEquals(0, countEvents(sink.events, "android_runtime", "Runtime.getRuntime"));
            assertEquals(0, countEvents(sink.events, "android_runtime", "Runtime.freeMemory"));

            long n = invokeFreeMemory(jni, baseVM, useVaList, runtime);
            assertEquals(FREE_MEMORY_BYTES, n);

            CapturedEvent ev = findLastEvent(sink.events, "android_runtime", "Runtime.freeMemory");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=freeMemoryBytes,result=" + FREE_MEMORY_BYTES, String.valueOf(ev.value));
            assertEquals(1, countEvents(sink.events, "android_runtime", "Runtime.freeMemory"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runZeroValue(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FM_ZERO_JSON);
        assertTrue(config.isAndroidRuntimeFreeMemoryBytesConfigured());
        assertEquals(0L, config.getAndroidRuntimeFreeMemoryBytes());

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

            DvmObject<?> runtime = invokeGetRuntime(jni, baseVM, useVaList);
            assertEquals(0L, invokeFreeMemory(jni, baseVM, useVaList, runtime));
            CapturedEvent ev = findLastEvent(sink.events, "android_runtime", "Runtime.freeMemory");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=freeMemoryBytes,result=0", String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runNoHostLeak(boolean is64Bit, boolean useVaList) throws Exception {
        long hostMax = java.lang.Runtime.getRuntime().maxMemory();
        long hostTotal = java.lang.Runtime.getRuntime().totalMemory();
        long hostFree = java.lang.Runtime.getRuntime().freeMemory();
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FM_TM_JSON);
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

            DvmObject<?> runtime = invokeGetRuntime(jni, baseVM, useVaList);
            assertFalse(runtime.getValue() instanceof java.lang.Runtime);
            long n = invokeFreeMemory(jni, baseVM, useVaList, runtime);
            assertEquals(FREE_MEMORY_BYTES, n);
            assertTrue("configured value must not be a host heap leak",
                    n != hostFree || n == FREE_MEMORY_BYTES);

            assertEquals(1, countEvents(sink.events, "android_runtime", "Runtime.freeMemory"));
            for (CapturedEvent e : sink.events) {
                String v = String.valueOf(e.value);
                assertFalse(v.contains("java.lang.Runtime"));
                assertFalse(v.contains("host"));
                if ("Runtime.freeMemory".equals(e.api)) {
                    assertEquals("field=freeMemoryBytes,result=" + FREE_MEMORY_BYTES, v);
                    if (hostFree != FREE_MEMORY_BYTES) {
                        assertFalse(v.contains(String.valueOf(hostFree)));
                    }
                    if (hostTotal != FREE_MEMORY_BYTES) {
                        assertFalse(v.contains(String.valueOf(hostTotal)));
                    }
                    if (hostMax != FREE_MEMORY_BYTES) {
                        assertFalse(v.contains(String.valueOf(hostMax)));
                    }
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runFreeTotalMaxLegal(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FM_TM_MM_JSON);
        assertTrue(config.isAndroidRuntimeFreeMemoryBytesConfigured());
        assertTrue(config.isAndroidRuntimeTotalMemoryBytesConfigured());
        assertTrue(config.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertEquals(FREE_MEMORY_BYTES, config.getAndroidRuntimeFreeMemoryBytes());
        assertEquals(TOTAL_MEMORY_BYTES, config.getAndroidRuntimeTotalMemoryBytes());
        assertEquals(MAX_MEMORY_BYTES, config.getAndroidRuntimeMaxMemoryBytes());

        TraceEnvironmentConfig equal = TraceEnvironmentConfig.parse(FM_EQ_TM_EQ_MM_JSON);
        assertEquals(TOTAL_MEMORY_BYTES, equal.getAndroidRuntimeFreeMemoryBytes());
        assertEquals(TOTAL_MEMORY_BYTES, equal.getAndroidRuntimeTotalMemoryBytes());
        assertEquals(TOTAL_MEMORY_BYTES, equal.getAndroidRuntimeMaxMemoryBytes());

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

            DvmObject<?> runtime = invokeGetRuntime(jni, baseVM, useVaList);
            assertEquals(FREE_MEMORY_BYTES, invokeFreeMemory(jni, baseVM, useVaList, runtime));
            assertEquals(TOTAL_MEMORY_BYTES,
                    invokeRuntimeLong(jni, baseVM, useVaList, runtime, "totalMemory", TOTAL_MEMORY));
            assertEquals(MAX_MEMORY_BYTES,
                    invokeRuntimeLong(jni, baseVM, useVaList, runtime, "maxMemory", MAX_MEMORY));
            assertEquals(1, countEvents(sink.events, "android_runtime", "Runtime.freeMemory"));
            assertEquals(1, countEvents(sink.events, "android_runtime", "Runtime.totalMemory"));
            assertEquals(1, countEvents(sink.events, "android_runtime", "Runtime.maxMemory"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runFieldOnlyFreeMemoryUoe(String json, boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        assertFalse(config.isAndroidRuntimeFreeMemoryBytesConfigured());

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

            DvmObject<?> runtime = invokeGetRuntime(jni, baseVM, useVaList);
            assertNotNull(runtime);
            try {
                invokeFreeMemory(jni, baseVM, useVaList, runtime);
                fail("expected UOE for freeMemory when freeMemoryBytes absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("freeMemory"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("android_runtime".equals(e.kind) && e.api != null
                        && e.api.contains("freeMemory"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetRuntimeAndAdjacent(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig all = TraceEnvironmentConfig.parse(ALL_JSON);
        assertTrue(all.isAndroidRuntimeFreeMemoryBytesConfigured());
        assertTrue(all.isAndroidRuntimeTotalMemoryBytesConfigured());
        assertTrue(all.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertTrue(all.isAndroidRuntimeAvailableProcessorsConfigured());

        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(all)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> runtime = invokeGetRuntime(jni, baseVM, useVaList);
            assertNotNull(runtime);
            assertEquals(0, countEvents(sink.events, "android_runtime", "Runtime.getRuntime"));
            assertEquals(FREE_MEMORY_BYTES, invokeFreeMemory(jni, baseVM, useVaList, runtime));
            assertEquals(TOTAL_MEMORY_BYTES,
                    invokeRuntimeLong(jni, baseVM, useVaList, runtime, "totalMemory", TOTAL_MEMORY));
            assertEquals(MAX_MEMORY_BYTES,
                    invokeRuntimeLong(jni, baseVM, useVaList, runtime, "maxMemory", MAX_MEMORY));
            assertEquals(8, invokeAvailableProcessors(jni, baseVM, useVaList, runtime));
            assertEquals(1, countEvents(sink.events, "android_runtime", "Runtime.freeMemory"));
            assertEquals(1, countEvents(sink.events, "android_runtime", "Runtime.totalMemory"));
            assertEquals(1, countEvents(sink.events, "android_runtime", "Runtime.maxMemory"));
            assertEquals(1, countEvents(sink.events, "android_runtime",
                    "Runtime.availableProcessors"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        for (String json : new String[]{NO_RUNTIME_JSON, PROPS_ONLY_JSON, EMPTY_RUNTIME_JSON}) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
            assertFalse(config.isAndroidRuntimeFreeMemoryBytesConfigured());
            AndroidEmulator missingEmu = null;
            CapturingSink missingSink = new CapturingSink();
            try {
                missingEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                        : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                TraceEnvironmentEventSink.register(missingEmu, missingSink);
                VM vm = missingEmu.createDalvikVM();
                AbstractJni jni = new AbstractJni() {
                };
                vm.setJni(jni);
                BaseVM baseVM = (BaseVM) vm;
                try {
                    invokeGetRuntime(jni, baseVM, useVaList);
                    fail("expected UOE for getRuntime when runtime fields absent: " + json);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getRuntime"));
                }
                for (CapturedEvent e : missingSink.events) {
                    assertFalse("android_runtime".equals(e.kind));
                }
            } finally {
                if (missingEmu != null) {
                    TraceEnvironmentEventSink.unregister(missingEmu, missingSink);
                    missingEmu.close();
                }
            }
        }
    }

    private static void runCrossVmOrdinaryAndStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig shared = TraceEnvironmentConfig.parse(FM_TM_JSON);
        AndroidEmulator emuA = null;
        AndroidEmulator emuB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            AndroidEmulatorBuilder builderA = is64Bit
                    ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
            AndroidEmulatorBuilder builderB = is64Bit
                    ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
            emuA = builderA.setEnvironmentConfig(shared).build();
            emuB = builderB.setEnvironmentConfig(shared).build();
            TraceEnvironmentEventSink.register(emuA, sinkA);
            TraceEnvironmentEventSink.register(emuB, sinkB);

            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            VM vmA = emuA.createDalvikVM();
            VM vmB = emuB.createDalvikVM();
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> runtimeA = invokeGetRuntime(jniA, baseA, useVaList);
            assertEquals(FREE_MEMORY_BYTES, invokeFreeMemory(jniA, baseA, useVaList, runtimeA));
            assertEquals(1, countEvents(sinkA.events, "android_runtime", "Runtime.freeMemory"));

            try {
                invokeFreeMemory(jniB, baseB, useVaList, runtimeA);
                fail("expected UOE for cross-VM Runtime marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("freeMemory"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("android_runtime".equals(e.kind) && e.api != null
                        && e.api.contains("freeMemory"));
            }

            DvmObject<?> ordinary = vmA.resolveClass(RUNTIME_CLASS).newObject(null);
            try {
                invokeFreeMemory(jniA, baseA, useVaList, ordinary);
                fail("expected UOE for ordinary non-marker Runtime");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("freeMemory"));
            }

            DvmObject<?> hostWrapped = vmA.resolveClass(RUNTIME_CLASS)
                    .newObject(java.lang.Runtime.getRuntime());
            try {
                invokeFreeMemory(jniA, baseA, useVaList, hostWrapped);
                fail("expected UOE for host Runtime payload");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("freeMemory"));
            }

            emuA.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(FM_TM_JSON));
            try {
                invokeFreeMemory(jniA, baseA, useVaList, runtimeA);
                fail("expected UOE for stale Runtime marker after setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("freeMemory"));
            }
            assertEquals(1, countEvents(sinkA.events, "android_runtime", "Runtime.freeMemory"));
        } finally {
            if (emuA != null) {
                TraceEnvironmentEventSink.unregister(emuA, sinkA);
                emuA.close();
            }
            if (emuB != null) {
                TraceEnvironmentEventSink.unregister(emuB, sinkB);
                emuB.close();
            }
        }
    }

    private static void assertParseInvalid(String json, String expectedPath) {
        try {
            TraceEnvironmentConfig.parse(json);
            fail("expected IllegalArgumentException for json: " + json);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains(expectedPath));
        }
    }

    private static DvmObject<?> invokeGetRuntime(AbstractJni jni, BaseVM vm, boolean useVaList) {
        DvmClass runtimeClass = vm.resolveClass(RUNTIME_CLASS);
        DvmMethod method = new DvmMethod(runtimeClass, "getRuntime",
                "()Ljava/lang/Runtime;", true);
        assertEquals(GET_RUNTIME, method.getSignature());
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, runtimeClass, method.getSignature(),
                    new TestNoArgVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, runtimeClass, method.getSignature(),
                new TestNoArgVarArg(vm, method));
    }

    private static long invokeFreeMemory(AbstractJni jni, BaseVM vm, boolean useVaList,
                                         DvmObject<?> runtime) {
        return invokeRuntimeLong(jni, vm, useVaList, runtime, "freeMemory", FREE_MEMORY);
    }

    private static long invokeRuntimeLong(AbstractJni jni, BaseVM vm, boolean useVaList,
                                          DvmObject<?> runtime, String name, String expectedSig) {
        DvmMethod method = new DvmMethod(runtime.getObjectType(), name, "()J", false);
        assertEquals(expectedSig, method.getSignature());
        if (useVaList) {
            return jni.callLongMethodV(vm, runtime, method.getSignature(),
                    new TestNoArgVaList(vm, method));
        }
        return jni.callLongMethod(vm, runtime, method.getSignature(),
                new TestNoArgVarArg(vm, method));
    }

    private static int invokeAvailableProcessors(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> runtime) {
        DvmMethod method = new DvmMethod(runtime.getObjectType(), "availableProcessors", "()I",
                false);
        assertEquals(AVAILABLE_PROCESSORS, method.getSignature());
        if (useVaList) {
            return jni.callIntMethodV(vm, runtime, method.getSignature(),
                    new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, runtime, method.getSignature(),
                new TestNoArgVarArg(vm, method));
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source,
                                         String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}

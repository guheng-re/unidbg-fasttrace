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
 * Coverage for {@code android.runtime.maxMemoryBytes} + static
 * {@code Runtime.getRuntime()} / instance {@code Runtime.maxMemory()}
 * （VarArg 32 位 + VaList 64 位；VM 持有 marker；无宿主 Runtime；
 * 与 availableProcessors / linux.cpu 独立）。
 */
public class AndroidRuntimeMaxMemoryJniTest {

    private static final String RUNTIME_CLASS = "java/lang/Runtime";
    private static final String GET_RUNTIME =
            "java/lang/Runtime->getRuntime()Ljava/lang/Runtime;";
    private static final String MAX_MEMORY = "java/lang/Runtime->maxMemory()J";
    private static final String AVAILABLE_PROCESSORS =
            "java/lang/Runtime->availableProcessors()I";
    private static final String TOTAL_MEMORY = "java/lang/Runtime->totalMemory()J";
    private static final String FREE_MEMORY = "java/lang/Runtime->freeMemory()J";

    private static final long MAX_MEMORY_BYTES = 268435456L;

    private static final String MM_JSON = "{"
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

    private static final String BOTH_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"availableProcessors\":8,\"maxMemoryBytes\":" + MAX_MEMORY_BYTES + "}"
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
    public void testMaxMemoryValueAndSidecarVarArg32() throws Exception {
        runValueAndSidecar(false, false);
    }

    @Test
    public void testMaxMemoryValueAndSidecarVaList64() throws Exception {
        runValueAndSidecar(true, true);
    }

    @Test
    public void testMaxMemoryBytesOnlyGetRuntimeVarArg32() throws Exception {
        runMaxMemoryBytesOnly(false, false);
    }

    @Test
    public void testMaxMemoryBytesOnlyGetRuntimeVaList64() throws Exception {
        runMaxMemoryBytesOnly(true, true);
    }

    @Test
    public void testAvailableProcessorsOnlyMaxMemoryUoeVarArg32() throws Exception {
        runAvailableProcessorsOnly(false, false);
    }

    @Test
    public void testAvailableProcessorsOnlyMaxMemoryUoeVaList64() throws Exception {
        runAvailableProcessorsOnly(true, true);
    }

    @Test
    public void testParseRejection() {
        assertParseInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":0}}}");
        assertParseInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":-1}}}");
        assertParseInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":9223372036854775808}}}");
        assertParseInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":1.5}}}");
        assertParseInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":\"268435456\"}}}");
        assertParseInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":null}}}");
        assertParseInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":true}}}");
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse(NO_RUNTIME_JSON);
        assertFalse(missing.isAndroidRuntimeMaxMemoryBytesConfigured());
    }

    @Test
    public void testMissingCallRejectionVarArg32() throws Exception {
        runMissing(false, false);
    }

    @Test
    public void testMissingCallRejectionVaList64() throws Exception {
        runMissing(true, true);
    }

    @Test
    public void testCrossVmOrdinaryAndStaleVarArg32() throws Exception {
        runCrossVmOrdinaryAndStale(false, false);
    }

    @Test
    public void testCrossVmOrdinaryAndStaleVaList64() throws Exception {
        runCrossVmOrdinaryAndStale(true, true);
    }

    @Test
    public void testNoHostLeakVarArg32() throws Exception {
        runNoHostLeak(false, false);
    }

    @Test
    public void testNoHostLeakVaList64() throws Exception {
        runNoHostLeak(true, true);
    }

    private static void runValueAndSidecar(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(BOTH_JSON);
        assertTrue(config.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertEquals(MAX_MEMORY_BYTES, config.getAndroidRuntimeMaxMemoryBytes());

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
            assertEquals(0, countEvents(sink.events, "android_runtime", "Runtime.maxMemory"));

            long n = invokeMaxMemory(jni, baseVM, useVaList, runtime);
            assertEquals(MAX_MEMORY_BYTES, n);

            CapturedEvent ev = findLastEvent(sink.events, "android_runtime", "Runtime.maxMemory");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=maxMemoryBytes,result=" + MAX_MEMORY_BYTES, String.valueOf(ev.value));
            assertEquals(1, countEvents(sink.events, "android_runtime", "Runtime.maxMemory"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMaxMemoryBytesOnly(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MM_JSON);
        assertTrue(config.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertFalse(config.isAndroidRuntimeAvailableProcessorsConfigured());

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
            assertEquals(MAX_MEMORY_BYTES, invokeMaxMemory(jni, baseVM, useVaList, runtime));

            try {
                invokeAvailableProcessors(jni, baseVM, useVaList, runtime);
                fail("expected UOE for availableProcessors when maxMemoryBytes-only");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("availableProcessors"));
            }
            assertEquals(0, countEvents(sink.events, "android_runtime",
                    "Runtime.availableProcessors"));
            assertEquals(1, countEvents(sink.events, "android_runtime", "Runtime.maxMemory"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAvailableProcessorsOnly(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(AP_ONLY_JSON);
        assertTrue(config.isAndroidRuntimeAvailableProcessorsConfigured());
        assertFalse(config.isAndroidRuntimeMaxMemoryBytesConfigured());

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
                invokeMaxMemory(jni, baseVM, useVaList, runtime);
                fail("expected UOE for maxMemory when availableProcessors-only");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("maxMemory"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("android_runtime".equals(e.kind) && "Runtime.maxMemory".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMissing(boolean is64Bit, boolean useVaList) throws Exception {
        for (String json : new String[]{NO_RUNTIME_JSON, PROPS_ONLY_JSON, EMPTY_RUNTIME_JSON}) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
            assertFalse(config.isAndroidRuntimeMaxMemoryBytesConfigured());
            AndroidEmulator emulator = null;
            CapturingSink sink = new CapturingSink();
            try {
                emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                        : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                TraceEnvironmentEventSink.register(emulator, sink);
                VM vm = emulator.createDalvikVM();
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

                DvmObject<?> fake = vm.resolveClass(RUNTIME_CLASS).newObject(null);
                try {
                    invokeMaxMemory(jni, baseVM, useVaList, fake);
                    fail("expected UOE for maxMemory on non-marker");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("maxMemory"));
                }
                for (CapturedEvent e : sink.events) {
                    assertFalse("android_runtime".equals(e.kind));
                }
            } finally {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                    emulator.close();
                }
            }
        }
    }

    private static void runCrossVmOrdinaryAndStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig shared = TraceEnvironmentConfig.parse(MM_JSON);
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
            assertEquals(MAX_MEMORY_BYTES, invokeMaxMemory(jniA, baseA, useVaList, runtimeA));
            assertEquals(1, countEvents(sinkA.events, "android_runtime", "Runtime.maxMemory"));

            try {
                invokeMaxMemory(jniB, baseB, useVaList, runtimeA);
                fail("expected UOE for cross-VM Runtime marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("maxMemory"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("android_runtime".equals(e.kind));
            }

            DvmObject<?> ordinary = vmA.resolveClass(RUNTIME_CLASS).newObject(null);
            try {
                invokeMaxMemory(jniA, baseA, useVaList, ordinary);
                fail("expected UOE for ordinary non-marker Runtime");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("maxMemory"));
            }

            DvmObject<?> hostWrapped = vmA.resolveClass(RUNTIME_CLASS)
                    .newObject(java.lang.Runtime.getRuntime());
            try {
                invokeMaxMemory(jniA, baseA, useVaList, hostWrapped);
                fail("expected UOE for host Runtime payload");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("maxMemory"));
            }

            emuA.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(MM_JSON));
            try {
                invokeMaxMemory(jniA, baseA, useVaList, runtimeA);
                fail("expected UOE for stale Runtime marker after setEnvironmentConfig");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("maxMemory"));
            }
            assertEquals(1, countEvents(sinkA.events, "android_runtime", "Runtime.maxMemory"));
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

    private static void runNoHostLeak(boolean is64Bit, boolean useVaList) throws Exception {
        long hostMax = java.lang.Runtime.getRuntime().maxMemory();
        long hostTotal = java.lang.Runtime.getRuntime().totalMemory();
        long hostFree = java.lang.Runtime.getRuntime().freeMemory();
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MM_JSON);
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
            long n = invokeMaxMemory(jni, baseVM, useVaList, runtime);
            assertEquals(MAX_MEMORY_BYTES, n);
            assertTrue("configured value must not be a host heap leak", n != hostMax || n == MAX_MEMORY_BYTES);

            try {
                invokeRuntimeLong(jni, baseVM, useVaList, runtime, "totalMemory", TOTAL_MEMORY);
                fail("expected UOE for totalMemory");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("totalMemory"));
            }
            try {
                invokeRuntimeLong(jni, baseVM, useVaList, runtime, "freeMemory", FREE_MEMORY);
                fail("expected UOE for freeMemory");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("freeMemory"));
            }

            assertEquals(1, countEvents(sink.events, "android_runtime", "Runtime.maxMemory"));
            for (CapturedEvent e : sink.events) {
                String v = String.valueOf(e.value);
                assertFalse(v.contains("java.lang.Runtime"));
                assertFalse(v.contains("totalMemory"));
                assertFalse(v.contains("freeMemory"));
                if ("Runtime.maxMemory".equals(e.api)) {
                    assertEquals("field=maxMemoryBytes,result=" + MAX_MEMORY_BYTES, v);
                    assertFalse(v.contains("host"));
                    if (hostTotal != MAX_MEMORY_BYTES) {
                        assertFalse(v.contains(String.valueOf(hostTotal)));
                    }
                    if (hostFree != MAX_MEMORY_BYTES) {
                        assertFalse(v.contains(String.valueOf(hostFree)));
                    }
                    if (hostMax != MAX_MEMORY_BYTES) {
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

    private static void assertParseInvalid(String json) {
        try {
            TraceEnvironmentConfig.parse(json);
            fail("expected IllegalArgumentException for json: " + json);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null
                    && e.getMessage().contains("android.runtime.maxMemoryBytes"));
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

    private static long invokeMaxMemory(AbstractJni jni, BaseVM vm, boolean useVaList,
                                        DvmObject<?> runtime) {
        return invokeRuntimeLong(jni, vm, useVaList, runtime, "maxMemory", MAX_MEMORY);
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

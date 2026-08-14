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
 * Coverage for {@code android.runtime.availableProcessors} + static
 * {@code Runtime.getRuntime()} / instance {@code Runtime.availableProcessors()}
 * (VarArg + VaList; VM-owned marker only; no host Runtime; independent of linux.cpu).
 */
public class AndroidRuntimeAvailableProcessorsJniTest {

    private static final String RUNTIME_CLASS = "java/lang/Runtime";
    private static final String GET_RUNTIME =
            "java/lang/Runtime->getRuntime()Ljava/lang/Runtime;";
    private static final String AVAILABLE_PROCESSORS =
            "java/lang/Runtime->availableProcessors()I";

    private static final String AP_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"availableProcessors\":8}"
            + "}"
            + "}";

    private static final String AP_WITH_LINUX_CPU_JSON = "{"
            + "\"linux\":{\"cpu\":{"
            + "\"configuredProcessorCount\":4,"
            + "\"onlineProcessorCount\":2"
            + "}},"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"runtime\":{\"availableProcessors\":16}"
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
    public void testAvailableProcessorsValueVarArg32() throws Exception {
        runValue(false, false, AP_JSON, 8);
    }

    @Test
    public void testAvailableProcessorsValueVaList64() throws Exception {
        runValue(true, true, AP_JSON, 8);
    }

    @Test
    public void testAvailableProcessorsIndependentOfLinuxCpuVarArg32() throws Exception {
        runValue(false, false, AP_WITH_LINUX_CPU_JSON, 16);
    }

    @Test
    public void testAvailableProcessorsIndependentOfLinuxCpuVaList64() throws Exception {
        runValue(true, true, AP_WITH_LINUX_CPU_JSON, 16);
    }

    @Test
    public void testAvailableProcessorsAbsenceVarArg32() throws Exception {
        runAbsence(false, false);
    }

    @Test
    public void testAvailableProcessorsAbsenceVaList64() throws Exception {
        runAbsence(true, true);
    }

    @Test
    public void testAvailableProcessorsCrossVmAndStaleVarArg32() throws Exception {
        runCrossVmAndStale(false, false);
    }

    @Test
    public void testAvailableProcessorsCrossVmAndStaleVaList64() throws Exception {
        runCrossVmAndStale(true, true);
    }

    @Test
    public void testAvailableProcessorsSidecarSummaryOnlyVarArg32() throws Exception {
        runSidecarPrivacy(false, false);
    }

    @Test
    public void testAvailableProcessorsSidecarSummaryOnlyVaList64() throws Exception {
        runSidecarPrivacy(true, true);
    }

    private static void runValue(boolean is64Bit, boolean useVaList, String json, int expected)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        assertTrue(config.isAndroidRuntimeAvailableProcessorsConfigured());
        assertEquals(expected, config.getAndroidRuntimeAvailableProcessors());

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

            // getRuntime: no sidecar
            assertEquals(0, countEvents(sink.events, "android_runtime", "Runtime.getRuntime"));
            assertEquals(0, countEvents(sink.events, "android_runtime",
                    "Runtime.availableProcessors"));

            int n = invokeAvailableProcessors(jni, baseVM, useVaList, runtime);
            assertEquals(expected, n);

            CapturedEvent ev = findLastEvent(sink.events, "android_runtime",
                    "Runtime.availableProcessors");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=availableProcessors,result=" + expected, String.valueOf(ev.value));
            assertEquals(1, countEvents(sink.events, "android_runtime",
                    "Runtime.availableProcessors"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsence(boolean is64Bit, boolean useVaList) throws Exception {
        for (String json : new String[]{NO_RUNTIME_JSON, PROPS_ONLY_JSON, EMPTY_RUNTIME_JSON}) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
            assertFalse(config.isAndroidRuntimeAvailableProcessorsConfigured());
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
                    fail("expected UOE for getRuntime when availableProcessors absent: " + json);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getRuntime"));
                }

                // non-marker Runtime → availableProcessors UOE, no event
                DvmClass runtimeClass = vm.resolveClass(RUNTIME_CLASS);
                DvmObject<?> fake = runtimeClass.newObject(null);
                try {
                    invokeAvailableProcessors(jni, baseVM, useVaList, fake);
                    fail("expected UOE for availableProcessors on non-marker");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("availableProcessors"));
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

    private static void runCrossVmAndStale(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig shared = TraceEnvironmentConfig.parse(AP_JSON);
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
            assertEquals(8, invokeAvailableProcessors(jniA, baseA, useVaList, runtimeA));
            assertEquals(1, countEvents(sinkA.events, "android_runtime",
                    "Runtime.availableProcessors"));

            // Cross-VM: B must not accept A's Runtime marker
            try {
                invokeAvailableProcessors(jniB, baseB, useVaList, runtimeA);
                fail("expected UOE for cross-VM Runtime marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("availableProcessors"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("android_runtime".equals(e.kind));
            }

            // Stale / non-marker on configured VM: null payload
            DvmObject<?> stale = vmA.resolveClass(RUNTIME_CLASS).newObject(null);
            try {
                invokeAvailableProcessors(jniA, baseA, useVaList, stale);
                fail("expected UOE for stale non-marker Runtime");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("availableProcessors"));
            }
            // only the one successful call on A
            assertEquals(1, countEvents(sinkA.events, "android_runtime",
                    "Runtime.availableProcessors"));
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

    private static void runSidecarPrivacy(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(AP_JSON);
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
            invokeAvailableProcessors(jni, baseVM, useVaList, runtime);
            invokeAvailableProcessors(jni, baseVM, useVaList, runtime);

            assertEquals(2, countEvents(sink.events, "android_runtime",
                    "Runtime.availableProcessors"));
            for (CapturedEvent e : sink.events) {
                if ("Runtime.availableProcessors".equals(e.api)) {
                    String v = String.valueOf(e.value);
                    assertEquals("field=availableProcessors,result=8", v);
                    // summary only: no host Runtime dump / no extra keys
                    assertFalse(v.contains("java.lang.Runtime"));
                    assertFalse(v.contains("maxMemory"));
                    assertFalse(v.contains("totalMemory"));
                    assertFalse(v.contains("freeMemory"));
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
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

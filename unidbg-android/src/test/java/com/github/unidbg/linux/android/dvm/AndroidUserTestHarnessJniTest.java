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
 * Coverage for {@code android.securitySignals.userTestHarness} + static
 * {@code ActivityManager.isRunningInUserTestHarness()Z} (VarArg + VaList).
 */
public class AndroidUserTestHarnessJniTest {

    private static final String ACTIVITY_MANAGER_CLASS = "android/app/ActivityManager";

    private static final String HARNESS_TRUE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"securitySignals\":{\"userTestHarness\":true}"
            + "}"
            + "}";

    private static final String HARNESS_FALSE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"securitySignals\":{\"userTestHarness\":false}"
            + "}"
            + "}";

    private static final String EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"securitySignals\":{}"
            + "}"
            + "}";

    private static final String MONKEY_ONLY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"securitySignals\":{\"userAMonkey\":true}"
            + "}"
            + "}";

    private static final String ABSENT_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testUserTestHarnessTrueVarArg32() throws Exception {
        runConfigured(false, false, HARNESS_TRUE_JSON, true);
    }

    @Test
    public void testUserTestHarnessTrueVaList64() throws Exception {
        runConfigured(true, true, HARNESS_TRUE_JSON, true);
    }

    @Test
    public void testUserTestHarnessFalseVarArg32() throws Exception {
        runConfigured(false, false, HARNESS_FALSE_JSON, false);
    }

    @Test
    public void testUserTestHarnessFalseVaList64() throws Exception {
        runConfigured(true, true, HARNESS_FALSE_JSON, false);
    }

    @Test
    public void testUserTestHarnessDefaultFalseEmptyNodeVarArg32() throws Exception {
        runConfigured(false, false, EMPTY_JSON, false);
    }

    @Test
    public void testUserTestHarnessDefaultFalseEmptyNodeVaList64() throws Exception {
        runConfigured(true, true, EMPTY_JSON, false);
    }

    @Test
    public void testUserTestHarnessIndependentOfMonkeyVarArg32() throws Exception {
        runConfigured(false, false, MONKEY_ONLY_JSON, false);
    }

    @Test
    public void testUserTestHarnessIndependentOfMonkeyVaList64() throws Exception {
        runConfigured(true, true, MONKEY_ONLY_JSON, false);
    }

    @Test
    public void testUserTestHarnessAbsentUoeNoEventVarArg32() throws Exception {
        runAbsentUoe(false, false);
    }

    @Test
    public void testUserTestHarnessAbsentUoeNoEventVaList64() throws Exception {
        runAbsentUoe(true, true);
    }

    @Test
    public void testWrongSignatureUoeNoEventVarArg32() throws Exception {
        runWrongSignature(false, false);
    }

    @Test
    public void testWrongSignatureUoeNoEventVaList64() throws Exception {
        runWrongSignature(true, true);
    }

    @Test
    public void testDeprecatedIsRunningInTestHarnessNotImplementedVarArg32() throws Exception {
        runDeprecatedNotImplemented(false, false);
    }

    @Test
    public void testDeprecatedIsRunningInTestHarnessNotImplementedVaList64() throws Exception {
        runDeprecatedNotImplemented(true, true);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList, String json,
                                      boolean expected) throws Exception {
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

            assertEquals(expected, invokeIsRunningInUserTestHarness(jni, baseVM, useVaList, vm));

            CapturedEvent ev = findLastEvent(sink.events, "android_security",
                    "ActivityManager.isRunningInUserTestHarness");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=userTestHarness,result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertTrue(ev.note.contains("测试"));
            assertEquals(1, countEvents(sink.events, "android_security",
                    "ActivityManager.isRunningInUserTestHarness"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentUoe(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ABSENT_JSON);
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

            try {
                invokeIsRunningInUserTestHarness(jni, baseVM, useVaList, vm);
                fail("expected UnsupportedOperationException without securitySignals");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isRunningInUserTestHarness"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_security event when config absent: " + e.api,
                        "android_security".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runWrongSignature(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(HARNESS_TRUE_JSON);
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

            DvmClass dvmClass = vm.resolveClass(ACTIVITY_MANAGER_CLASS);
            DvmMethod method = new DvmMethod(dvmClass, "isRunningInUserTestHarnessForTest", "()Z", true);
            String signature = method.getSignature();
            try {
                if (useVaList) {
                    jni.callStaticBooleanMethodV(baseVM, dvmClass, signature,
                            new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callStaticBooleanMethod(baseVM, dvmClass, signature,
                            new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isRunningInUserTestHarnessForTest"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected harness event on wrong signature: " + e.api,
                        "android_security".equals(e.kind)
                                && "ActivityManager.isRunningInUserTestHarness".equals(e.api));
            }

            assertTrue(invokeIsRunningInUserTestHarness(jni, baseVM, useVaList, vm));
            assertEquals(1, countEvents(sink.events, "android_security",
                    "ActivityManager.isRunningInUserTestHarness"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runDeprecatedNotImplemented(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(HARNESS_TRUE_JSON);
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

            DvmClass dvmClass = vm.resolveClass(ACTIVITY_MANAGER_CLASS);
            // deprecated isRunningInTestHarness must NOT be implemented
            DvmMethod method = new DvmMethod(dvmClass, "isRunningInTestHarness", "()Z", true);
            String signature = method.getSignature();
            try {
                if (useVaList) {
                    jni.callStaticBooleanMethodV(baseVM, dvmClass, signature,
                            new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callStaticBooleanMethod(baseVM, dvmClass, signature,
                            new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for deprecated isRunningInTestHarness");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isRunningInTestHarness"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_security event for deprecated API: " + e.api,
                        "android_security".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static boolean invokeIsRunningInUserTestHarness(AbstractJni jni, BaseVM vm,
                                                            boolean useVaList, VM dalvikVm) {
        DvmClass dvmClass = dalvikVm.resolveClass(ACTIVITY_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isRunningInUserTestHarness", "()Z", true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticBooleanMethodV(vm, dvmClass, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callStaticBooleanMethod(vm, dvmClass, signature,
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}

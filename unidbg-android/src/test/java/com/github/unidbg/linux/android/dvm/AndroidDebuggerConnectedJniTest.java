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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidDebuggerConnectedJniTest {

    private static final String DEBUG_CLASS = "android/os/Debug";
    private static final String SELINUX_CLASS = "android/os/SELinux";
    private static final String BOOL_NO_ARGS = "()Z";

    /** debuggerConnected=true; waiting/tracing omitted → default false. */
    private static final String DEBUGGER_TRUE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"securitySignals\":{\"debuggerConnected\":true}"
            + "}"
            + "}";

    /** debuggerTracing=true only. */
    private static final String TRACING_TRUE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"securitySignals\":{\"debuggerTracing\":true}"
            + "}"
            + "}";

    /** Three fields with mixed values. */
    private static final String THREE_FIELDS_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"securitySignals\":{"
            + "\"debuggerConnected\":true,"
            + "\"waitingForDebugger\":false,"
            + "\"debuggerTracing\":true"
            + "}"
            + "}"
            + "}";

    /** SELinux mixed: enabled=false, enforced=true. */
    private static final String SELINUX_MIXED_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"securitySignals\":{"
            + "\"selinuxEnabled\":false,"
            + "\"selinuxEnforced\":true"
            + "}"
            + "}"
            + "}";

    private static final String EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"securitySignals\":{}"
            + "}"
            + "}";

    private static final String ABSENT_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testDebuggerTracingTrueVarArg32() throws Exception {
        runThreeApis(false, false, TRACING_TRUE_JSON, false, false, true);
    }

    @Test
    public void testDebuggerTracingTrueVaList64() throws Exception {
        runThreeApis(true, true, TRACING_TRUE_JSON, false, false, true);
    }

    @Test
    public void testEmptyDefaultsAllFalseVarArg32() throws Exception {
        runThreeApis(false, false, EMPTY_JSON, false, false, false);
    }

    @Test
    public void testEmptyDefaultsAllFalseVaList64() throws Exception {
        runThreeApis(true, true, EMPTY_JSON, false, false, false);
    }

    @Test
    public void testNodePresentTracingOmittedDefaultsFalseVarArg32() throws Exception {
        // securitySignals present, debuggerTracing key missing → default false
        runThreeApis(false, false, DEBUGGER_TRUE_JSON, true, false, false);
    }

    @Test
    public void testNodePresentTracingOmittedDefaultsFalseVaList64() throws Exception {
        runThreeApis(true, true, DEBUGGER_TRUE_JSON, true, false, false);
    }

    @Test
    public void testThreeFieldsCoexistVarArg32() throws Exception {
        runThreeApis(false, false, THREE_FIELDS_JSON, true, false, true);
    }

    @Test
    public void testThreeFieldsCoexistVaList64() throws Exception {
        runThreeApis(true, true, THREE_FIELDS_JSON, true, false, true);
    }

    @Test
    public void testAbsentLegacyDebuggerFalseWaitingAndTracingUoeVarArg32() throws Exception {
        runAbsentLegacy(false, false);
    }

    @Test
    public void testAbsentLegacyDebuggerFalseWaitingAndTracingUoeVaList64() throws Exception {
        runAbsentLegacy(true, true);
    }

    @Test
    public void testOtherUnknownDebugStaticBooleanStillUoeVarArg32() throws Exception {
        runOtherUnknownDebugStaticBooleanUoe(false, false);
    }

    @Test
    public void testOtherUnknownDebugStaticBooleanStillUoeVaList64() throws Exception {
        runOtherUnknownDebugStaticBooleanUoe(true, true);
    }

    @Test
    public void testSelinuxMixedVarArg32() throws Exception {
        runSelinuxMixed(false, false);
    }

    @Test
    public void testSelinuxMixedVaList64() throws Exception {
        runSelinuxMixed(true, true);
    }

    private static void runThreeApis(boolean is64Bit, boolean useVaList, String json,
                                     boolean expectedDebugger, boolean expectedWaiting,
                                     boolean expectedTracing) throws Exception {
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

            assertEquals(expectedDebugger, invokeIsDebuggerConnected(jni, baseVM, useVaList));
            assertEquals(expectedWaiting, invokeWaitingForDebugger(jni, baseVM, useVaList));
            assertEquals(expectedTracing, invokeIsDebuggerTracing(jni, baseVM, useVaList));

            CapturedEvent connectedEv = findLastEvent(sink.events, "android_security",
                    "Debug.isDebuggerConnected");
            assertNotNull(connectedEv);
            assertEquals("json-config", connectedEv.source);
            assertEquals("result=" + expectedDebugger, String.valueOf(connectedEv.value));
            assertNotNull(connectedEv.note);
            assertFalse(connectedEv.note.isEmpty());

            CapturedEvent waitingEv = findLastEvent(sink.events, "android_security",
                    "Debug.waitingForDebugger");
            assertNotNull(waitingEv);
            assertEquals("json-config", waitingEv.source);
            assertEquals("result=" + expectedWaiting, String.valueOf(waitingEv.value));
            assertNotNull(waitingEv.note);
            assertFalse(waitingEv.note.isEmpty());

            CapturedEvent tracingEv = findLastEvent(sink.events, "android_security",
                    "Debug.isDebuggerTracing");
            assertNotNull(tracingEv);
            assertEquals("json-config", tracingEv.source);
            assertEquals("result=" + expectedTracing, String.valueOf(tracingEv.value));
            assertNotNull(tracingEv.note);
            assertFalse(tracingEv.note.isEmpty());

            // three sidecar apis strictly distinct; each exactly once
            assertEquals("Debug.isDebuggerConnected", connectedEv.api);
            assertEquals("Debug.waitingForDebugger", waitingEv.api);
            assertEquals("Debug.isDebuggerTracing", tracingEv.api);
            assertFalse(connectedEv.api.equals(waitingEv.api));
            assertFalse(connectedEv.api.equals(tracingEv.api));
            assertFalse(waitingEv.api.equals(tracingEv.api));

            assertEquals(1, countEvents(sink.events, "android_security",
                    "Debug.isDebuggerConnected"));
            assertEquals(1, countEvents(sink.events, "android_security",
                    "Debug.waitingForDebugger"));
            assertEquals(1, countEvents(sink.events, "android_security",
                    "Debug.isDebuggerTracing"));
            assertEquals(3, sink.events.size());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentLegacy(boolean is64Bit, boolean useVaList) throws Exception {
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

            // historical hard-coded false — not UOE
            assertFalse(invokeIsDebuggerConnected(jni, baseVM, useVaList));

            for (CapturedEvent e : sink.events) {
                assertFalse("absent securitySignals must not emit android_security: " + e.api,
                        "android_security".equals(e.kind));
            }

            try {
                invokeWaitingForDebugger(jni, baseVM, useVaList);
                fail("expected UnsupportedOperationException for waitingForDebugger without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("waitingForDebugger"));
            }

            try {
                invokeIsDebuggerTracing(jni, baseVM, useVaList);
                fail("expected UnsupportedOperationException for isDebuggerTracing without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isDebuggerTracing"));
            }

            try {
                invokeIsSELinuxEnabled(jni, baseVM, useVaList);
                fail("expected UnsupportedOperationException for isSELinuxEnabled without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isSELinuxEnabled"));
            }

            try {
                invokeIsSELinuxEnforced(jni, baseVM, useVaList);
                fail("expected UnsupportedOperationException for isSELinuxEnforced without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isSELinuxEnforced"));
            }

            // still no android_security after UOE paths
            for (CapturedEvent e : sink.events) {
                assertFalse("absent securitySignals must not emit android_security: " + e.api,
                        "android_security".equals(e.kind));
            }
            assertEquals(0, countEvents(sink.events, "android_security",
                    "Debug.isDebuggerTracing"));
            assertEquals(0, countEvents(sink.events, "android_security",
                    "SELinux.isSELinuxEnabled"));
            assertEquals(0, countEvents(sink.events, "android_security",
                    "SELinux.isSELinuxEnforced"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSelinuxMixed(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(SELINUX_MIXED_JSON);
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

            assertFalse(invokeIsSELinuxEnabled(jni, baseVM, useVaList));
            assertTrue(invokeIsSELinuxEnforced(jni, baseVM, useVaList));

            CapturedEvent enabledEv = findLastEvent(sink.events, "android_security",
                    "SELinux.isSELinuxEnabled");
            assertNotNull(enabledEv);
            assertEquals("json-config", enabledEv.source);
            assertEquals("field=selinuxEnabled,result=false", String.valueOf(enabledEv.value));
            assertNotNull(enabledEv.note);
            assertFalse(enabledEv.note.isEmpty());

            CapturedEvent enforcedEv = findLastEvent(sink.events, "android_security",
                    "SELinux.isSELinuxEnforced");
            assertNotNull(enforcedEv);
            assertEquals("json-config", enforcedEv.source);
            assertEquals("field=selinuxEnforced,result=true", String.valueOf(enforcedEv.value));
            assertNotNull(enforcedEv.note);
            assertFalse(enforcedEv.note.isEmpty());

            assertEquals(1, countEvents(sink.events, "android_security",
                    "SELinux.isSELinuxEnabled"));
            assertEquals(1, countEvents(sink.events, "android_security",
                    "SELinux.isSELinuxEnforced"));
            assertEquals(2, sink.events.size());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runOtherUnknownDebugStaticBooleanUoe(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(THREE_FIELDS_JSON);
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

            // three known methods work
            assertTrue(invokeIsDebuggerConnected(jni, baseVM, useVaList));
            assertFalse(invokeWaitingForDebugger(jni, baseVM, useVaList));
            assertTrue(invokeIsDebuggerTracing(jni, baseVM, useVaList));

            // unrelated Debug static boolean remains UOE (not isDebuggerTracing)
            try {
                invokeStaticBoolean(jni, baseVM, useVaList, DEBUG_CLASS, "unknownDebugFlag",
                        BOOL_NO_ARGS);
                fail("expected UnsupportedOperationException for unknownDebugFlag");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("unknownDebugFlag"));
            }

            assertEquals(1, countEvents(sink.events, "android_security",
                    "Debug.isDebuggerConnected"));
            assertEquals(1, countEvents(sink.events, "android_security",
                    "Debug.waitingForDebugger"));
            assertEquals(1, countEvents(sink.events, "android_security",
                    "Debug.isDebuggerTracing"));
            assertNull(findLastEvent(sink.events, "android_security", "Debug.unknownDebugFlag"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static boolean invokeIsDebuggerConnected(AbstractJni jni, BaseVM vm, boolean useVaList) {
        return invokeStaticBoolean(jni, vm, useVaList, DEBUG_CLASS, "isDebuggerConnected",
                BOOL_NO_ARGS);
    }

    private static boolean invokeWaitingForDebugger(AbstractJni jni, BaseVM vm, boolean useVaList) {
        return invokeStaticBoolean(jni, vm, useVaList, DEBUG_CLASS, "waitingForDebugger",
                BOOL_NO_ARGS);
    }

    private static boolean invokeIsDebuggerTracing(AbstractJni jni, BaseVM vm, boolean useVaList) {
        return invokeStaticBoolean(jni, vm, useVaList, DEBUG_CLASS, "isDebuggerTracing",
                BOOL_NO_ARGS);
    }

    private static boolean invokeIsSELinuxEnabled(AbstractJni jni, BaseVM vm, boolean useVaList) {
        return invokeStaticBoolean(jni, vm, useVaList, SELINUX_CLASS, "isSELinuxEnabled",
                BOOL_NO_ARGS);
    }

    private static boolean invokeIsSELinuxEnforced(AbstractJni jni, BaseVM vm, boolean useVaList) {
        return invokeStaticBoolean(jni, vm, useVaList, SELINUX_CLASS, "isSELinuxEnforced",
                BOOL_NO_ARGS);
    }

    private static boolean invokeStaticBoolean(AbstractJni jni, BaseVM vm, boolean useVaList,
                                               String className, String methodName, String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticBooleanMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticBooleanMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
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

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
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

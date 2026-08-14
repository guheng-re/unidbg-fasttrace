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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Focused JNI tests for {@code android.accessibility.services} lists and
 * {@code AccessibilityServiceInfo.getId} (SystemService accessibility; VarArg + VaList).
 */
public class AndroidAccessibilityServicesJniTest {

    private static final String A11Y_MANAGER_CLASS =
            "android/view/accessibility/AccessibilityManager";
    private static final String SERVICE_INFO_CLASS =
            "android/accessibilityservice/AccessibilityServiceInfo";

    private static final String FULL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accessibility\":{"
            + "\"enabled\":true,"
            + "\"services\":["
            + "{\"id\":\"com.demo/.SvcA\",\"enabled\":true},"
            + "{\"id\":\"com.demo/.SvcB\",\"enabled\":false},"
            + "{\"id\":\"com.demo/.SvcC\",\"enabled\":true}"
            + "]"
            + "}"
            + "}"
            + "}";

    private static final String EMPTY_SERVICES_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accessibility\":{\"services\":[]}"
            + "}"
            + "}";

    private static final String NO_SERVICES_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accessibility\":{\"enabled\":true}"
            + "}"
            + "}";

    private static final String ABSENT_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testServicesFullVarArg32() throws Exception {
        runConfigured(false, false, FULL_JSON, 3, 2);
    }

    @Test
    public void testServicesFullVaList64() throws Exception {
        runConfigured(true, true, FULL_JSON, 3, 2);
    }

    @Test
    public void testServicesEmptyVarArg32() throws Exception {
        runConfigured(false, false, EMPTY_SERVICES_JSON, 0, 0);
    }

    @Test
    public void testServicesEmptyVaList64() throws Exception {
        runConfigured(true, true, EMPTY_SERVICES_JSON, 0, 0);
    }

    @Test
    public void testServicesMissingKeepsLegacyEnabledEmptyVarArg32() throws Exception {
        runLegacyEnabledEmpty(false, false, NO_SERVICES_JSON);
    }

    @Test
    public void testServicesMissingKeepsLegacyEnabledEmptyVaList64() throws Exception {
        runLegacyEnabledEmpty(true, true, NO_SERVICES_JSON);
    }

    @Test
    public void testAccessibilityAbsentLegacyEnabledEmptyVarArg32() throws Exception {
        runLegacyEnabledEmpty(false, false, ABSENT_JSON);
    }

    @Test
    public void testAccessibilityAbsentLegacyEnabledEmptyVaList64() throws Exception {
        runLegacyEnabledEmpty(true, true, ABSENT_JSON);
    }

    /** services absent: plain AccessibilityManager / other SystemService must UOE, no event. */
    @Test
    public void testServicesAbsentWrongReceiverUoeVarArg32() throws Exception {
        runLegacyWrongReceiverUoe(false, false, NO_SERVICES_JSON);
    }

    @Test
    public void testServicesAbsentWrongReceiverUoeVaList64() throws Exception {
        runLegacyWrongReceiverUoe(true, true, NO_SERVICES_JSON);
    }

    @Test
    public void testAccessibilityAbsentWrongReceiverUoeVarArg32() throws Exception {
        runLegacyWrongReceiverUoe(false, false, ABSENT_JSON);
    }

    @Test
    public void testAccessibilityAbsentWrongReceiverUoeVaList64() throws Exception {
        runLegacyWrongReceiverUoe(true, true, ABSENT_JSON);
    }

    @Test
    public void testServicesCrossVmRejectedVarArg32() throws Exception {
        runCrossVmRejected(false, false);
    }

    @Test
    public void testServicesCrossVmRejectedVaList64() throws Exception {
        runCrossVmRejected(true, true);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList, String json,
                                      int expectedInstalled, int expectedEnabled) throws Exception {
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

            DvmObject<?> manager = resolveA11y(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            assertEquals(SystemService.ACCESSIBILITY_SERVICE, manager.getValue());

            ArrayListObject installed1 = invokeInstalled(jni, baseVM, useVaList, manager);
            ArrayListObject installed2 = invokeInstalled(jni, baseVM, useVaList, manager);
            assertNotSame(installed1, installed2);
            assertEquals(expectedInstalled, installed1.size());

            ArrayListObject enabled1 = invokeEnabled(jni, baseVM, useVaList, manager, 0);
            ArrayListObject enabled2 = invokeEnabled(jni, baseVM, useVaList, manager, -1);
            assertNotSame(enabled1, enabled2);
            assertEquals(expectedEnabled, enabled1.size());

            if (expectedInstalled >= 3) {
                assertEquals("com.demo/.SvcA",
                        invokeGetId(jni, baseVM, useVaList, installed1.getValue().get(0)));
                assertEquals("com.demo/.SvcB",
                        invokeGetId(jni, baseVM, useVaList, installed1.getValue().get(1)));
                assertEquals("com.demo/.SvcC",
                        invokeGetId(jni, baseVM, useVaList, installed1.getValue().get(2)));
                assertEquals("com.demo/.SvcA",
                        invokeGetId(jni, baseVM, useVaList, enabled1.getValue().get(0)));
                assertEquals("com.demo/.SvcC",
                        invokeGetId(jni, baseVM, useVaList, enabled1.getValue().get(1)));
            }

            DvmObject<?> plainMgr = vm.resolveClass(A11Y_MANAGER_CLASS).newObject(null);
            try {
                invokeInstalled(jni, baseVM, useVaList, plainMgr);
                fail("expected UOE for plain AccessibilityManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInstalledAccessibilityServiceList"));
            }

            DvmObject<?> plainInfo = vm.resolveClass(SERVICE_INFO_CLASS).newObject(null);
            try {
                invokeGetId(jni, baseVM, useVaList, plainInfo);
                fail("expected UOE for plain AccessibilityServiceInfo.getId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getId"));
            }

            DvmObject<?> audio = new SystemService(vm, SystemService.AUDIO_SERVICE);
            try {
                invokeEnabled(jni, baseVM, useVaList, audio, 0);
                fail("expected UOE for wrong SystemService when services configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getEnabledAccessibilityServiceList"));
            }

            assertEquals(2, countEvents(sink.events, "android_accessibility",
                    "AccessibilityManager.getInstalledAccessibilityServiceList"));
            assertEquals(2, countEvents(sink.events, "android_accessibility",
                    "AccessibilityManager.getEnabledAccessibilityServiceList"));
            CapturedEvent lastInstalled = findLastEvent(sink.events, "android_accessibility",
                    "AccessibilityManager.getInstalledAccessibilityServiceList");
            assertNotNull(lastInstalled);
            assertEquals("json-config", lastInstalled.source);
            assertEquals("count=" + expectedInstalled, String.valueOf(lastInstalled.value));

            for (CapturedEvent e : sink.events) {
                if ("android_accessibility".equals(e.kind)
                        && e.api != null
                        && e.api.contains("ServiceList")) {
                    assertTrue(e.api.equals(
                            "AccessibilityManager.getInstalledAccessibilityServiceList")
                            || e.api.equals(
                            "AccessibilityManager.getEnabledAccessibilityServiceList"));
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runLegacyEnabledEmpty(boolean is64Bit, boolean useVaList, String json)
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

            DvmObject<?> manager = resolveA11y(jni, baseVM, useVaList, vm);
            ArrayListObject enabled = invokeEnabled(jni, baseVM, useVaList, manager, 0);
            assertTrue(enabled.getValue().isEmpty());
            // fresh empty each call
            ArrayListObject enabled2 = invokeEnabled(jni, baseVM, useVaList, manager, 0);
            assertNotSame(enabled, enabled2);

            try {
                invokeInstalled(jni, baseVM, useVaList, manager);
                fail("expected UOE for installed list when services not configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInstalledAccessibilityServiceList"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("android_accessibility".equals(e.kind)
                        && e.api != null
                        && e.api.contains("ServiceList"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runLegacyWrongReceiverUoe(boolean is64Bit, boolean useVaList, String json)
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

            DvmObject<?> plainMgr = vm.resolveClass(A11Y_MANAGER_CLASS).newObject(null);
            try {
                invokeEnabled(jni, baseVM, useVaList, plainMgr, 0);
                fail("expected UOE for plain AccessibilityManager when services absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getEnabledAccessibilityServiceList"));
            }
            try {
                invokeInstalled(jni, baseVM, useVaList, plainMgr);
                fail("expected UOE for plain AccessibilityManager installed list");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInstalledAccessibilityServiceList"));
            }

            DvmObject<?> audio = new SystemService(vm, SystemService.AUDIO_SERVICE);
            try {
                invokeEnabled(jni, baseVM, useVaList, audio, 0);
                fail("expected UOE for non-accessibility SystemService when services absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getEnabledAccessibilityServiceList"));
            }
            try {
                invokeInstalled(jni, baseVM, useVaList, audio);
                fail("expected UOE for non-accessibility SystemService installed list");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInstalledAccessibilityServiceList"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("android_accessibility".equals(e.kind)
                        && e.api != null
                        && e.api.contains("ServiceList"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runCrossVmRejected(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig shared = TraceEnvironmentConfig.parse(FULL_JSON);
        AndroidEmulator emuA = null;
        AndroidEmulator emuB = null;
        CapturingSink sinkB = new CapturingSink();
        try {
            AndroidEmulatorBuilder builderA = is64Bit
                    ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
            AndroidEmulatorBuilder builderB = is64Bit
                    ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
            emuA = builderA.setEnvironmentConfig(shared).build();
            emuB = builderB.setEnvironmentConfig(shared).build();
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

            DvmObject<?> managerA = resolveA11y(jniA, baseA, useVaList, vmA);
            ArrayListObject listA = invokeInstalled(jniA, baseA, useVaList, managerA);
            DvmObject<?> infoA = listA.getValue().get(0);

            try {
                invokeGetId(jniB, baseB, useVaList, infoA);
                fail("expected UOE for cross-VM AccessibilityServiceInfo.getId");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getId"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("android_accessibility".equals(e.kind)
                        && e.api != null
                        && e.api.contains("ServiceList"));
            }
        } finally {
            if (emuA != null) {
                emuA.close();
            }
            if (emuB != null) {
                TraceEnvironmentEventSink.unregister(emuB, sinkB);
                emuB.close();
            }
        }
    }

    private static DvmObject<?> resolveA11y(AbstractJni jni, BaseVM vm, boolean useVaList, VM dalvik) {
        DvmObject<?> app = dalvik.resolveClass("android/app/Application").newObject(null);
        int nameHash = vm.addLocalObject(new StringObject(vm, SystemService.ACCESSIBILITY_SERVICE));
        DvmClass dvmClass = app.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/String;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, app, signature,
                    new TestObjectVaList(vm, method, nameHash));
        }
        return jni.callObjectMethod(vm, app, signature, new TestObjectVarArg(vm, method, nameHash));
    }

    private static ArrayListObject invokeInstalled(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmObject<?> manager) {
        DvmObject<?> result = invokeObjectMethod(jni, vm, useVaList, manager, A11Y_MANAGER_CLASS,
                "getInstalledAccessibilityServiceList", "()Ljava/util/List;");
        assertTrue(result instanceof ArrayListObject);
        return (ArrayListObject) result;
    }

    private static ArrayListObject invokeEnabled(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> manager, int feedbackType) {
        DvmClass dvmClass = vm.resolveClass(A11Y_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getEnabledAccessibilityServiceList",
                "(I)Ljava/util/List;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, manager, signature,
                    new TestIntVaList(vm, method, feedbackType));
        } else {
            result = jni.callObjectMethod(vm, manager, signature,
                    new TestIntVarArg(vm, method, feedbackType));
        }
        assertTrue(result instanceof ArrayListObject);
        return (ArrayListObject) result;
    }

    private static String invokeGetId(AbstractJni jni, BaseVM vm, boolean useVaList,
                                      DvmObject<?> info) {
        DvmObject<?> result = invokeObjectMethod(jni, vm, useVaList, info, SERVICE_INFO_CLASS,
                "getId", "()Ljava/lang/String;");
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static DvmObject<?> invokeObjectMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmObject<?> receiver, String className,
                                                   String methodName, String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, receiver, signature, new TestVarArg(vm, method));
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

    private static final class TestObjectVarArg extends VarArg {
        TestObjectVarArg(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }
    }

    private static final class TestObjectVaList extends VaList {
        TestObjectVaList(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }
    }

    private static final class TestIntVarArg extends VarArg {
        TestIntVarArg(BaseVM vm, DvmMethod method, int int0) {
            super(vm, method);
            args.add(int0);
        }
    }

    private static final class TestIntVaList extends VaList {
        TestIntVaList(BaseVM vm, DvmMethod method, int int0) {
            super(vm, method);
            args.add(int0);
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

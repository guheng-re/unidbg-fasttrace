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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 覆盖类型化 {@code Application}/{@code Context.getSystemService(AccessibilityManager.class)}：
 * 返回既有 {@code SystemService} accessibility 标记；lookup 不发 sidecar；
 * 三布尔与服务列表读取仍由 {@code android.accessibility} 门控。
 */
public class AndroidAccessibilityTypedServiceJniTest {

    private static final String ACCESSIBILITY_MANAGER_CLASS =
            "android/view/accessibility/AccessibilityManager";
    private static final String SERVICE_INFO_CLASS =
            "android/accessibilityservice/AccessibilityServiceInfo";
    private static final String ENABLED_SERVICE_ID = "com.demo/.SvcEnabled";
    private static final String DISABLED_SERVICE_ID = "com.demo/.SvcDisabled";

    private static final String ACCESSIBILITY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accessibility\":{"
            + "\"enabled\":true,"
            + "\"touchExplorationEnabled\":false,"
            + "\"highContrastTextEnabled\":true,"
            + "\"services\":["
            + "{\"id\":\"" + ENABLED_SERVICE_ID + "\",\"enabled\":true},"
            + "{\"id\":\"" + DISABLED_SERVICE_ID + "\",\"enabled\":false}"
            + "]"
            + "}"
            + "}"
            + "}";

    private static final String NO_ACCESSIBILITY_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testAccessibilityTypedGetSystemServiceVarArg32() throws Exception {
        runTypedGetSystemService(false, false);
    }

    @Test
    public void testAccessibilityTypedGetSystemServiceVaList64() throws Exception {
        runTypedGetSystemService(true, true);
    }

    private static void runTypedGetSystemService(boolean is64Bit, boolean useVaList) throws Exception {
        runTypedConfigured(is64Bit, useVaList);
        runTypedAbsent(is64Bit, useVaList);
    }

    private static void runTypedConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ACCESSIBILITY_JSON);
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
            DvmClass accessibilityClass = vm.resolveClass(ACCESSIBILITY_MANAGER_CLASS);

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            int eventsBeforeApp = sink.events.size();
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app,
                    accessibilityClass);
            assertAccessibilityManagerMarker(fromApp);
            assertNoAndroidAccessibilitySince(sink, eventsBeforeApp);
            assertConfiguredAccessibilityFromMarker(jni, baseVM, useVaList, fromApp, sink);

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            int eventsBeforeCtx = sink.events.size();
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context,
                    accessibilityClass);
            assertAccessibilityManagerMarker(fromCtx);
            assertNoAndroidAccessibilitySince(sink, eventsBeforeCtx);
            assertConfiguredAccessibilityFromMarker(jni, baseVM, useVaList, fromCtx, sink);

            assertEquals(2, countEvents(sink.events, "android_accessibility",
                    "AccessibilityManager.isEnabled"));
            assertEquals(2, countEvents(sink.events, "android_accessibility",
                    "AccessibilityManager.isTouchExplorationEnabled"));
            assertEquals(2, countEvents(sink.events, "android_accessibility",
                    "AccessibilityManager.isHighContrastTextEnabled"));
            assertEquals(2, countEvents(sink.events, "android_accessibility",
                    "AccessibilityManager.getInstalledAccessibilityServiceList"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runTypedAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_ACCESSIBILITY_JSON);
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
            DvmClass accessibilityClass = vm.resolveClass(ACCESSIBILITY_MANAGER_CLASS);

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app,
                    accessibilityClass);
            assertAccessibilityManagerMarker(fromApp);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context,
                    accessibilityClass);
            assertAccessibilityManagerMarker(fromCtx);
            assertAccessibilityReadersUnsupported(jni, baseVM, useVaList, fromApp);
            assertAccessibilityReadersUnsupported(jni, baseVM, useVaList, fromCtx);
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_accessibility sidecar: " + e.api,
                        "android_accessibility".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertAccessibilityManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals(ACCESSIBILITY_MANAGER_CLASS, manager.getObjectType().getClassName());
        assertEquals(SystemService.ACCESSIBILITY_SERVICE, manager.getValue());
    }

    private static void assertNoAndroidAccessibilitySince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            assertFalse("unexpected android_accessibility sidecar on typed lookup: "
                            + sink.events.get(i).api,
                    "android_accessibility".equals(sink.events.get(i).kind));
        }
    }

    private static void assertConfiguredAccessibilityFromMarker(AbstractJni jni, BaseVM vm,
                                                                boolean useVaList,
                                                                DvmObject<?> manager,
                                                                CapturingSink sink) {
        assertTrue(invokeBooleanGetter(jni, vm, useVaList, manager, "isEnabled"));
        CapturedEvent enabled = findLastEvent(sink.events, "android_accessibility",
                "AccessibilityManager.isEnabled");
        assertNotNull(enabled);
        assertEquals("json-config", enabled.source);
        assertEquals("field=enabled,result=true", String.valueOf(enabled.value));
        assertNotNull(enabled.note);
        assertFalse(enabled.note.isEmpty());

        assertFalse(invokeBooleanGetter(jni, vm, useVaList, manager, "isTouchExplorationEnabled"));
        CapturedEvent touch = findLastEvent(sink.events, "android_accessibility",
                "AccessibilityManager.isTouchExplorationEnabled");
        assertNotNull(touch);
        assertEquals("json-config", touch.source);
        assertEquals("field=touchExplorationEnabled,result=false", String.valueOf(touch.value));
        assertNotNull(touch.note);
        assertFalse(touch.note.isEmpty());

        assertTrue(invokeBooleanGetter(jni, vm, useVaList, manager, "isHighContrastTextEnabled"));
        CapturedEvent contrast = findLastEvent(sink.events, "android_accessibility",
                "AccessibilityManager.isHighContrastTextEnabled");
        assertNotNull(contrast);
        assertEquals("json-config", contrast.source);
        assertEquals("field=highContrastTextEnabled,result=true", String.valueOf(contrast.value));
        assertNotNull(contrast.note);
        assertFalse(contrast.note.isEmpty());

        ArrayListObject installed = invokeInstalled(jni, vm, useVaList, manager);
        assertEquals(2, installed.size());
        assertEquals(ENABLED_SERVICE_ID, invokeGetId(jni, vm, useVaList, installed.getValue().get(0)));
        assertEquals(DISABLED_SERVICE_ID, invokeGetId(jni, vm, useVaList, installed.getValue().get(1)));
        CapturedEvent listEvent = findLastEvent(sink.events, "android_accessibility",
                "AccessibilityManager.getInstalledAccessibilityServiceList");
        assertNotNull(listEvent);
        assertEquals("json-config", listEvent.source);
        assertEquals("count=2", String.valueOf(listEvent.value));
        assertNotNull(listEvent.note);
        assertFalse(listEvent.note.isEmpty());
    }

    private static void assertAccessibilityReadersUnsupported(AbstractJni jni, BaseVM vm,
                                                              boolean useVaList,
                                                              DvmObject<?> manager) {
        assertBooleanUnsupported(jni, vm, useVaList, manager, "isEnabled");
        assertBooleanUnsupported(jni, vm, useVaList, manager, "isTouchExplorationEnabled");
        assertBooleanUnsupported(jni, vm, useVaList, manager, "isHighContrastTextEnabled");
        try {
            invokeInstalled(jni, vm, useVaList, manager);
            fail("expected UOE for getInstalledAccessibilityServiceList without android.accessibility");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getInstalledAccessibilityServiceList"));
        }
    }

    private static void assertBooleanUnsupported(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> manager, String methodName) {
        try {
            invokeBooleanGetter(jni, vm, useVaList, manager, methodName);
            fail("expected UOE for " + methodName + " without android.accessibility");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains(methodName));
        }
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
                    new TestVaList(vm, method, classHash));
        }
        return jni.callObjectMethod(vm, receiver, signature,
                new TestVarArg(vm, method, classHash));
    }

    private static boolean invokeBooleanGetter(AbstractJni jni, BaseVM vm, boolean useVaList,
                                               DvmObject<?> target, String methodName) {
        DvmClass dvmClass = vm.resolveClass(ACCESSIBILITY_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestVarArg(vm, method));
    }

    private static ArrayListObject invokeInstalled(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmObject<?> manager) {
        DvmClass dvmClass = vm.resolveClass(ACCESSIBILITY_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getInstalledAccessibilityServiceList",
                "()Ljava/util/List;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, manager, signature, new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, manager, signature, new TestVarArg(vm, method));
        }
        assertTrue(result instanceof ArrayListObject);
        return (ArrayListObject) result;
    }

    private static String invokeGetId(AbstractJni jni, BaseVM vm, boolean useVaList,
                                      DvmObject<?> info) {
        DvmClass dvmClass = vm.resolveClass(SERVICE_INFO_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getId", "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, info, signature, new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, info, signature, new TestVarArg(vm, method));
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
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

        TestVarArg(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVaList(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
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

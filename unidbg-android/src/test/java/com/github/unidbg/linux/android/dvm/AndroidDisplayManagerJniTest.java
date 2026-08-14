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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 覆盖类型化 {@code Application}/{@code Context.getSystemService(DisplayManager.class)}：
 * 返回既有 {@code SystemService} display 标记；lookup 不发 sidecar；
 * {@code getDisplay(0)} 复用 {@code android.display} profile，非零 id 返回 Java null；
 * 节点缺失时 lookup 仍成功，{@code getDisplay} 保持 UOE 且无 sidecar。
 */
public class AndroidDisplayManagerJniTest {

    private static final String DISPLAY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"display\":{"
            + "\"widthPixels\":1440,"
            + "\"heightPixels\":3200,"
            + "\"densityDpi\":560,"
            + "\"scaledDensity\":3.5,"
            + "\"xdpi\":513.0,"
            + "\"ydpi\":512.5,"
            + "\"refreshRate\":90.0,"
            + "\"rotation\":1,"
            + "\"modeId\":2"
            + "}"
            + "}"
            + "}";

    private static final String NO_DISPLAY_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    private static final String DISPLAY_MANAGER_CLASS = "android/hardware/display/DisplayManager";
    private static final String GET_DISPLAY = "(I)Landroid/view/Display;";
    private static final String INT_NO_ARGS = "()I";

    @Test
    public void testDisplayManagerTypedGetSystemServiceVarArg32() throws Exception {
        runTypedGetSystemService(false, false);
    }

    @Test
    public void testDisplayManagerTypedGetSystemServiceVaList64() throws Exception {
        runTypedGetSystemService(true, true);
    }

    private static void runTypedGetSystemService(boolean is64Bit, boolean useVaList) throws Exception {
        runTypedConfigured(is64Bit, useVaList);
        runTypedAbsent(is64Bit, useVaList);
    }

    private static void runTypedConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(DISPLAY_JSON);
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
            DvmClass displayManagerClass = vm.resolveClass(DISPLAY_MANAGER_CLASS);

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            int eventsBeforeApp = sink.events.size();
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, displayManagerClass);
            assertDisplayManagerMarker(fromApp);
            assertNoAndroidDisplaySince(sink, eventsBeforeApp);
            assertConfiguredDisplayFromManager(jni, baseVM, useVaList, fromApp, sink);

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            int eventsBeforeCtx = sink.events.size();
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, displayManagerClass);
            assertDisplayManagerMarker(fromCtx);
            assertNoAndroidDisplaySince(sink, eventsBeforeCtx);
            assertConfiguredDisplayFromManager(jni, baseVM, useVaList, fromCtx, sink);

            assertEquals(2, countEvents(sink.events, "android_display", "DisplayManager.getDisplay",
                    "displayId=0,result=0"));
            assertEquals(2, countEvents(sink.events, "android_display", "DisplayManager.getDisplay",
                    "displayId=1,result=null"));
            assertEquals(2, countEvents(sink.events, "android_display", "Display.getRotation"));
            assertEquals(2, countEvents(sink.events, "android_display", "Display.getWidth"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runTypedAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_DISPLAY_JSON);
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
            DvmClass displayManagerClass = vm.resolveClass(DISPLAY_MANAGER_CLASS);

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, displayManagerClass);
            assertDisplayManagerMarker(fromApp);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, displayManagerClass);
            assertDisplayManagerMarker(fromCtx);
            assertGetDisplayFallback(jni, baseVM, useVaList, fromApp);
            assertGetDisplayFallback(jni, baseVM, useVaList, fromCtx);
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_display sidecar: " + e.api,
                        "android_display".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertDisplayManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals(DISPLAY_MANAGER_CLASS, manager.getObjectType().getClassName());
        assertEquals(SystemService.DISPLAY_SERVICE, manager.getValue());
        assertEquals("display", manager.getValue());
    }

    private static void assertNoAndroidDisplaySince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            assertFalse("unexpected android_display sidecar on typed lookup: "
                            + sink.events.get(i).api,
                    "android_display".equals(sink.events.get(i).kind));
        }
    }

    private static void assertConfiguredDisplayFromManager(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                           DvmObject<?> displayManager, CapturingSink sink) {
        DvmObject<?> display = invokeGetDisplay(jni, vm, useVaList, displayManager, 0);
        assertNotNull(display);
        assertTrue(display.getValue().getClass().getName().contains("ConfiguredDisplay"));
        assertEquals(1, invokeIntMethod(jni, vm, useVaList, display,
                "android/view/Display", "getRotation"));
        assertEquals(1440, invokeIntMethod(jni, vm, useVaList, display,
                "android/view/Display", "getWidth"));

        DvmObject<?> missing = invokeGetDisplay(jni, vm, useVaList, displayManager, 1);
        assertNull(missing);

        CapturedEvent get0 = findLastEvent(sink.events, "android_display", "DisplayManager.getDisplay",
                "displayId=0,result=0");
        assertNotNull(get0);
        assertEquals("json-config", get0.source);
        assertEquals("displayId=0,result=0", String.valueOf(get0.value));

        CapturedEvent get1 = findLastEvent(sink.events, "android_display", "DisplayManager.getDisplay",
                "displayId=1,result=null");
        assertNotNull(get1);
        assertEquals("json-config", get1.source);
        assertEquals("displayId=1,result=null", String.valueOf(get1.value));

        CapturedEvent rot = findLastEvent(sink.events, "android_display", "Display.getRotation");
        assertNotNull(rot);
        assertEquals("json-config", rot.source);
        assertTrue(String.valueOf(rot.value).contains("result=1"));

        CapturedEvent widthEv = findLastEvent(sink.events, "android_display", "Display.getWidth");
        assertNotNull(widthEv);
        assertEquals("json-config", widthEv.source);
        assertEquals("field=widthPixels,result=1440", String.valueOf(widthEv.value));
    }

    private static void assertGetDisplayFallback(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> displayManager) {
        try {
            invokeGetDisplay(jni, vm, useVaList, displayManager, 0);
            fail("expected UnsupportedOperationException for getDisplay without android.display");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getDisplay"));
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
                    new TestObjectVaList(vm, method, classHash));
        }
        return jni.callObjectMethod(vm, receiver, signature,
                new TestObjectVarArg(vm, method, classHash));
    }

    private static DvmObject<?> invokeGetDisplay(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> receiver, int displayId) {
        DvmClass dvmClass = vm.resolveClass(DISPLAY_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getDisplay", GET_DISPLAY, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature,
                    new TestIntVaList(vm, method, displayId));
        }
        return jni.callObjectMethod(vm, receiver, signature,
                new TestIntVarArg(vm, method, displayId));
    }

    private static int invokeIntMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                       DvmObject<?> receiver, String className, String methodName) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, INT_NO_ARGS, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, receiver, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, receiver, signature, new TestNoArgVarArg(vm, method));
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

    private static CapturedEvent findLastEvent(List<CapturedEvent> events, String kind, String api,
                                               String value) {
        CapturedEvent found = null;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api) && value.equals(String.valueOf(e.value))) {
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

    private static int countEvents(List<CapturedEvent> events, String kind, String api, String value) {
        int n = 0;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api) && value.equals(String.valueOf(e.value))) {
                n++;
            }
        }
        return n;
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

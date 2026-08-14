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
 * 覆盖类型化 {@code Application}/{@code Context.getSystemService(WindowManager.class)}：
 * 返回既有 {@code SystemService} window 标记；lookup 不发 sidecar；
 * {@code getDefaultDisplay} 与 Display 读取仍由 {@code android.display} 门控。
 */
public class AndroidWindowTypedServiceJniTest {

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

    private static final String GET_DEFAULT_DISPLAY = "()Landroid/view/Display;";
    private static final String GET_MODE = "()Landroid/view/Display$Mode;";
    private static final String INT_NO_ARGS = "()I";
    private static final String FLOAT_NO_ARGS = "()F";

    @Test
    public void testWindowTypedGetSystemServiceVarArg32() throws Exception {
        runTypedGetSystemService(false, false);
    }

    @Test
    public void testWindowTypedGetSystemServiceVaList64() throws Exception {
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
            DvmClass windowClass = vm.resolveClass("android/view/WindowManager");

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            int eventsBeforeApp = sink.events.size();
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, windowClass);
            assertWindowManagerMarker(fromApp);
            assertNoAndroidDisplaySince(sink, eventsBeforeApp);
            assertConfiguredDisplayFromMarker(jni, baseVM, useVaList, fromApp, sink);

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            int eventsBeforeCtx = sink.events.size();
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, windowClass);
            assertWindowManagerMarker(fromCtx);
            assertNoAndroidDisplaySince(sink, eventsBeforeCtx);
            assertConfiguredDisplayFromMarker(jni, baseVM, useVaList, fromCtx, sink);

            assertEquals(2, countEvents(sink.events, "android_display",
                    "WindowManager.getDefaultDisplay"));
            assertEquals(2, countEvents(sink.events, "android_display", "Display.getWidth"));
            assertEquals(2, countEvents(sink.events, "android_display", "Display.getHeight"));
            assertEquals(2, countEvents(sink.events, "android_display", "Display.getRotation"));
            assertEquals(2, countEvents(sink.events, "android_display", "Display.getRefreshRate"));
            assertEquals(2, countEvents(sink.events, "android_display", "Display.getMode"));
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
            DvmClass windowClass = vm.resolveClass("android/view/WindowManager");

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, windowClass);
            assertWindowManagerMarker(fromApp);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, windowClass);
            assertWindowManagerMarker(fromCtx);
            assertGetDefaultDisplayFallback(jni, baseVM, useVaList, fromApp);
            assertGetDefaultDisplayFallback(jni, baseVM, useVaList, fromCtx);
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

    private static void assertWindowManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals("android/view/WindowManager", manager.getObjectType().getClassName());
        assertEquals(SystemService.WINDOW_SERVICE, manager.getValue());
    }

    private static void assertNoAndroidDisplaySince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            assertFalse("unexpected android_display sidecar on typed lookup: "
                            + sink.events.get(i).api,
                    "android_display".equals(sink.events.get(i).kind));
        }
    }

    private static void assertConfiguredDisplayFromMarker(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                          DvmObject<?> windowManager, CapturingSink sink) {
        DvmObject<?> display = invokeObjectMethod(jni, vm, useVaList, windowManager,
                "android/view/WindowManager", "getDefaultDisplay", GET_DEFAULT_DISPLAY);
        assertNotNull(display);
        assertTrue(display.getValue().getClass().getName().contains("ConfiguredDisplay"));

        assertEquals(1440, invokeIntMethod(jni, vm, useVaList, display,
                "android/view/Display", "getWidth"));
        assertEquals(3200, invokeIntMethod(jni, vm, useVaList, display,
                "android/view/Display", "getHeight"));
        assertEquals(1, invokeIntMethod(jni, vm, useVaList, display,
                "android/view/Display", "getRotation"));
        assertEquals(90.0f, invokeFloatMethodV(jni, vm, display,
                "android/view/Display", "getRefreshRate"), 0f);

        DvmObject<?> mode = invokeObjectMethod(jni, vm, useVaList, display,
                "android/view/Display", "getMode", GET_MODE);
        assertNotNull(mode);
        assertTrue(mode.getValue().getClass().getName().contains("ConfiguredDisplayMode"));

        CapturedEvent def = findLastEvent(sink.events, "android_display", "WindowManager.getDefaultDisplay");
        assertNotNull(def);
        assertEquals("json-config", def.source);
        assertTrue(String.valueOf(def.value).contains("modeId=2"));
        assertTrue(String.valueOf(def.value).contains("refreshRate=90.0"));
        assertTrue(String.valueOf(def.value).contains("rotation=1"));

        CapturedEvent widthEv = findLastEvent(sink.events, "android_display", "Display.getWidth");
        assertNotNull(widthEv);
        assertEquals("json-config", widthEv.source);
        assertEquals("field=widthPixels,result=1440", String.valueOf(widthEv.value));

        CapturedEvent heightEv = findLastEvent(sink.events, "android_display", "Display.getHeight");
        assertNotNull(heightEv);
        assertEquals("json-config", heightEv.source);
        assertEquals("field=heightPixels,result=3200", String.valueOf(heightEv.value));

        CapturedEvent rot = findLastEvent(sink.events, "android_display", "Display.getRotation");
        assertNotNull(rot);
        assertEquals("json-config", rot.source);
        assertTrue(String.valueOf(rot.value).contains("result=1"));

        CapturedEvent rr = findLastEvent(sink.events, "android_display", "Display.getRefreshRate");
        assertNotNull(rr);
        assertEquals("json-config", rr.source);
        assertTrue(String.valueOf(rr.value).contains("result=90.0"));

        CapturedEvent modeEv = findLastEvent(sink.events, "android_display", "Display.getMode");
        assertNotNull(modeEv);
        assertEquals("json-config", modeEv.source);
        assertTrue(String.valueOf(modeEv.value).contains("modeId=2"));
        assertTrue(String.valueOf(modeEv.value).contains("width=1440"));
        assertTrue(String.valueOf(modeEv.value).contains("height=3200"));
    }

    private static void assertGetDefaultDisplayFallback(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                        DvmObject<?> windowManager) {
        if (useVaList) {
            DvmObject<?> display = invokeObjectMethod(jni, vm, true, windowManager,
                    "android/view/WindowManager", "getDefaultDisplay", GET_DEFAULT_DISPLAY);
            assertNotNull(display);
            assertNull(display.getValue());
        } else {
            try {
                invokeObjectMethod(jni, vm, false, windowManager,
                        "android/view/WindowManager", "getDefaultDisplay", GET_DEFAULT_DISPLAY);
                fail("expected UnsupportedOperationException for VarArg getDefaultDisplay without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDefaultDisplay"));
            }
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

    private static int invokeIntMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                       DvmObject<?> receiver, String className, String methodName) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, INT_NO_ARGS, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, receiver, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, receiver, signature, new TestVarArg(vm, method));
    }

    private static float invokeFloatMethodV(AbstractJni jni, BaseVM vm, DvmObject<?> receiver,
                                            String className, String methodName) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, FLOAT_NO_ARGS, false);
        return jni.callFloatMethodV(vm, receiver, method.getSignature(), new TestVaList(vm, method));
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

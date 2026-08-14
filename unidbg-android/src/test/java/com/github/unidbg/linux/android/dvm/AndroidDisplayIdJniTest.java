package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.api.SystemService;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
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
 * 覆盖 {@code Display.getDisplayId()I}：仅配置 {@code ConfiguredDisplay} marker 固定返回 0；
 * 普通 Display 保持 UOE 且无 {@code android_display} sidecar。不表示多显示器建模。
 */
public class AndroidDisplayIdJniTest {

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
    private static final String GET_DEFAULT_DISPLAY = "()Landroid/view/Display;";
    private static final String GET_DISPLAY = "(I)Landroid/view/Display;";
    private static final String GET_DISPLAYS = "()[Landroid/view/Display;";
    private static final String INT_NO_ARGS = "()I";
    private static final String GET_DISPLAY_ID_VALUE = "field=displayId,result=0";

    @Test
    public void testDisplayIdVarArg32() throws Exception {
        runDisplayId(false, false);
    }

    @Test
    public void testDisplayIdVaList64() throws Exception {
        runDisplayId(true, true);
    }

    private static void runDisplayId(boolean is64Bit, boolean useVaList) throws Exception {
        runConfigured(is64Bit, useVaList);
        runAbsent(is64Bit, useVaList);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList) throws Exception {
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

            DvmObject<?> windowManager = vm.resolveClass("android/view/WindowManager").newObject(null);
            DvmObject<?> fromWindow = invokeObjectMethod(jni, baseVM, useVaList, windowManager,
                    "android/view/WindowManager", "getDefaultDisplay", GET_DEFAULT_DISPLAY);
            assertConfiguredDisplay(fromWindow);
            assertDisplayIdZero(jni, baseVM, useVaList, fromWindow, sink);

            DvmObject<?> displayManager = new SystemService(vm, SystemService.DISPLAY_SERVICE);
            DvmObject<?> fromGetDisplay = invokeGetDisplay(jni, baseVM, useVaList, displayManager, 0);
            assertConfiguredDisplay(fromGetDisplay);
            assertDisplayIdZero(jni, baseVM, useVaList, fromGetDisplay, sink);

            DvmObject<?> displays = invokeGetDisplays(jni, baseVM, useVaList, displayManager);
            assertTrue(displays instanceof ArrayObject);
            DvmObject<?> fromGetDisplays = ((ArrayObject) displays).getValue()[0];
            assertConfiguredDisplay(fromGetDisplays);
            assertDisplayIdZero(jni, baseVM, useVaList, fromGetDisplays, sink);

            assertEquals(3, countEvents(sink.events, "android_display", "Display.getDisplayId",
                    GET_DISPLAY_ID_VALUE));

            int eventsBeforePlain = sink.events.size();
            DvmObject<?> plainDisplay = vm.resolveClass("android/view/Display").newObject(null);
            assertGetDisplayIdUnsupported(jni, baseVM, useVaList, plainDisplay);
            assertNoDisplayIdSidecarSince(sink, eventsBeforePlain);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsent(boolean is64Bit, boolean useVaList) throws Exception {
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

            DvmObject<?> plainDisplay = vm.resolveClass("android/view/Display").newObject(null);
            assertGetDisplayIdUnsupported(jni, baseVM, useVaList, plainDisplay);
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

    private static void assertConfiguredDisplay(DvmObject<?> display) {
        assertNotNull(display);
        assertTrue(display.getValue().getClass().getName().contains("ConfiguredDisplay"));
    }

    private static void assertDisplayIdZero(AbstractJni jni, BaseVM vm, boolean useVaList,
                                            DvmObject<?> display, CapturingSink sink) {
        int before = sink.events.size();
        assertEquals(0, invokeIntMethod(jni, vm, useVaList, display,
                "android/view/Display", "getDisplayId"));
        assertEquals(1, countEventsSince(sink.events, before, "android_display",
                "Display.getDisplayId", GET_DISPLAY_ID_VALUE));
        CapturedEvent ev = findLastEvent(sink.events, "android_display", "Display.getDisplayId",
                GET_DISPLAY_ID_VALUE);
        assertNotNull(ev);
        assertEquals("json-config", ev.source);
        assertEquals(GET_DISPLAY_ID_VALUE, String.valueOf(ev.value));
    }

    private static void assertGetDisplayIdUnsupported(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                      DvmObject<?> display) {
        try {
            invokeIntMethod(jni, vm, useVaList, display, "android/view/Display", "getDisplayId");
            fail("expected UnsupportedOperationException for Display.getDisplayId");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getDisplayId"));
        }
    }

    private static void assertNoDisplayIdSidecarSince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            CapturedEvent e = sink.events.get(i);
            assertFalse("unexpected Display.getDisplayId sidecar: " + e.value,
                    "android_display".equals(e.kind) && "Display.getDisplayId".equals(e.api));
        }
    }

    private static DvmObject<?> invokeObjectMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmObject<?> receiver, String className,
                                                   String methodName, String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callObjectMethod(vm, receiver, signature, new TestNoArgVarArg(vm, method));
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

    private static DvmObject<?> invokeGetDisplays(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> receiver) {
        DvmClass dvmClass = vm.resolveClass(DISPLAY_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getDisplays", GET_DISPLAYS, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callObjectMethod(vm, receiver, signature, new TestNoArgVarArg(vm, method));
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

    private static int countEvents(List<CapturedEvent> events, String kind, String api, String value) {
        return countEventsSince(events, 0, kind, api, value);
    }

    private static int countEventsSince(List<CapturedEvent> events, int fromIndex, String kind,
                                        String api, String value) {
        int n = 0;
        for (int i = fromIndex; i < events.size(); i++) {
            CapturedEvent e = events.get(i);
            if (kind.equals(e.kind) && api.equals(e.api) && value.equals(String.valueOf(e.value))) {
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

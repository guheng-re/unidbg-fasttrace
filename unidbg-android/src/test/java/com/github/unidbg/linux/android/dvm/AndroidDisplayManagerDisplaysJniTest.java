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
 * 覆盖 {@code DisplayManager.getDisplays()}：已配置时从 {@code SystemService("display")}
 * 枚举长度为 1 的默认 {@code ConfiguredDisplay}；缺节点保持 UOE 且无 sidecar。
 */
public class AndroidDisplayManagerDisplaysJniTest {

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
    private static final String GET_DISPLAYS = "()[Landroid/view/Display;";
    private static final String INT_NO_ARGS = "()I";

    @Test
    public void testGetDisplaysVarArg32() throws Exception {
        runGetDisplays(false, false);
    }

    @Test
    public void testGetDisplaysVaList64() throws Exception {
        runGetDisplays(true, true);
    }

    private static void runGetDisplays(boolean is64Bit, boolean useVaList) throws Exception {
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

            DvmObject<?> displayManager = new SystemService(vm, SystemService.DISPLAY_SERVICE);
            DvmObject<?> result = invokeGetDisplays(jni, baseVM, useVaList, displayManager);
            assertTrue(result instanceof ArrayObject);
            ArrayObject array = (ArrayObject) result;
            assertEquals(1, array.length());
            DvmObject<?> display = array.getValue()[0];
            assertNotNull(display);
            assertTrue(display.getValue().getClass().getName().contains("ConfiguredDisplay"));
            assertEquals(1, invokeIntMethod(jni, baseVM, useVaList, display,
                    "android/view/Display", "getRotation"));
            assertEquals(1440, invokeIntMethod(jni, baseVM, useVaList, display,
                    "android/view/Display", "getWidth"));

            CapturedEvent getDisplays = findLastEvent(sink.events, "android_display",
                    "DisplayManager.getDisplays");
            assertNotNull(getDisplays);
            assertEquals("json-config", getDisplays.source);
            assertEquals("count=1", String.valueOf(getDisplays.value));
            assertEquals(1, countEvents(sink.events, "android_display",
                    "DisplayManager.getDisplays"));
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

            DvmObject<?> displayManager = new SystemService(vm, SystemService.DISPLAY_SERVICE);
            try {
                invokeGetDisplays(jni, baseVM, useVaList, displayManager);
                fail("expected UnsupportedOperationException for getDisplays without android.display");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDisplays"));
            }
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

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

public class AndroidThermalJniTest {

    private static final String THERMAL_VALUE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"thermal\":{\"currentThermalStatus\":4,\"headroom\":0.75}"
            + "}"
            + "}";

    private static final String THERMAL_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"thermal\":{}"
            + "}"
            + "}";

    private static final String NO_THERMAL_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testThermalValueVarArg32() throws Exception {
        runConfiguredThermal(false, false, THERMAL_VALUE_JSON, 4, 0.75f);
    }

    @Test
    public void testThermalValueVaList64() throws Exception {
        runConfiguredThermal(true, true, THERMAL_VALUE_JSON, 4, 0.75f);
    }

    @Test
    public void testThermalEmptyDefaultVarArg32() throws Exception {
        runConfiguredThermal(false, false, THERMAL_EMPTY_JSON, 0, 1.0f);
    }

    @Test
    public void testThermalEmptyDefaultVaList64() throws Exception {
        runConfiguredThermal(true, true, THERMAL_EMPTY_JSON, 0, 1.0f);
    }

    @Test
    public void testThermalAbsentVarArg32() throws Exception {
        runAbsentThermal(false, false);
    }

    @Test
    public void testThermalAbsentVaList64() throws Exception {
        runAbsentThermal(true, true);
    }

    private static void runConfiguredThermal(boolean is64Bit, boolean useVaList,
                                             String json, int expectedStatus,
                                             float expectedHeadroom) throws Exception {
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

            DvmObject<?> powerManager = vm.resolveClass("android/os/PowerManager").newObject(null);
            assertEquals(expectedStatus,
                    invokeNoArgInt(jni, baseVM, useVaList, powerManager, "getCurrentThermalStatus"));

            // getThermalHeadroom only via callFloatMethodV; fixed marker ignores forecastSeconds
            assertEquals(expectedHeadroom,
                    invokeGetThermalHeadroom(jni, baseVM, powerManager, 0), 0f);
            assertEquals(expectedHeadroom,
                    invokeGetThermalHeadroom(jni, baseVM, powerManager, 10), 0f);

            CapturedEvent statusEv = findLastEvent(sink.events, "android_thermal",
                    "PowerManager.getCurrentThermalStatus");
            assertNotNull(statusEv);
            assertEquals("json-config", statusEv.source);
            assertEquals("field=currentThermalStatus,result=" + expectedStatus,
                    String.valueOf(statusEv.value));
            assertNotNull(statusEv.note);
            assertFalse(statusEv.note.isEmpty());

            CapturedEvent headroom0 = findEventWithValuePrefix(sink.events, "android_thermal",
                    "PowerManager.getThermalHeadroom",
                    "field=headroom,forecastSeconds=0,result=");
            assertNotNull(headroom0);
            assertEquals("json-config", headroom0.source);
            assertEquals("field=headroom,forecastSeconds=0,result=" + expectedHeadroom,
                    String.valueOf(headroom0.value));
            assertNotNull(headroom0.note);
            assertFalse(headroom0.note.isEmpty());
            assertTrue(headroom0.note.contains("固定") || headroom0.note.contains("配置"));

            CapturedEvent headroom10 = findEventWithValuePrefix(sink.events, "android_thermal",
                    "PowerManager.getThermalHeadroom",
                    "field=headroom,forecastSeconds=10,result=");
            assertNotNull(headroom10);
            assertEquals("json-config", headroom10.source);
            assertEquals("field=headroom,forecastSeconds=10,result=" + expectedHeadroom,
                    String.valueOf(headroom10.value));

            assertEquals(1, countEvents(sink.events, "android_thermal",
                    "PowerManager.getCurrentThermalStatus"));
            assertEquals(2, countEvents(sink.events, "android_thermal",
                    "PowerManager.getThermalHeadroom"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentThermal(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_THERMAL_JSON);
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

            DvmObject<?> powerManager = vm.resolveClass("android/os/PowerManager").newObject(null);
            try {
                invokeNoArgInt(jni, baseVM, useVaList, powerManager, "getCurrentThermalStatus");
                fail("expected UnsupportedOperationException without thermal config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PowerManager->getCurrentThermalStatus"));
            }
            try {
                invokeGetThermalHeadroom(jni, baseVM, powerManager, 0);
                fail("expected UnsupportedOperationException for getThermalHeadroom without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("PowerManager->getThermalHeadroom"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_thermal event when config absent: " + e.api,
                        "android_thermal".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static int invokeNoArgInt(AbstractJni jni, BaseVM vm, boolean useVaList,
                                      DvmObject<?> target, String methodName) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestVarArg(vm, method));
    }

    /**
     * getThermalHeadroom(I)F is only wired through callFloatMethodV (no VarArg float path).
     */
    private static float invokeGetThermalHeadroom(AbstractJni jni, BaseVM vm,
                                                   DvmObject<?> target, int forecastSeconds) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getThermalHeadroom", "(I)F", false);
        return jni.callFloatMethodV(vm, target, method.getSignature(),
                new TestIntVaList(vm, method, forecastSeconds));
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

    private static CapturedEvent findEventWithValuePrefix(List<CapturedEvent> events, String kind,
                                                          String api, String valuePrefix) {
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)
                    && String.valueOf(e.value).startsWith(valuePrefix)) {
                return e;
            }
        }
        return null;
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}

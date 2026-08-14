package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidTimeZoneRawOffsetJniTest {

    private static final String LOCALE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"locale\":{"
            + "\"languageTag\":\"zh-Hans-CN\","
            + "\"timezoneId\":\"Asia/Shanghai\""
            + "}"
            + "}"
            + "}";

    private static final String TIMEZONE_GET_DEFAULT = "()Ljava/util/TimeZone;";
    private static final String STRING_NO_ARGS = "()Ljava/lang/String;";
    private static final int ASIA_SHANGHAI_RAW_OFFSET = 28800000;

    @Test
    public void testTimeZoneRawOffsetVarArg32() throws Exception {
        runConfiguredRawOffset(false, false);
    }

    @Test
    public void testTimeZoneRawOffsetVaList64() throws Exception {
        runConfiguredRawOffset(true, true);
    }

    @Test
    public void testTimeZoneRawOffsetAbsentVarArg32() throws Exception {
        runAbsentLocale(false, false);
    }

    @Test
    public void testTimeZoneRawOffsetAbsentVaList64() throws Exception {
        runAbsentLocale(true, true);
    }

    private static void runConfiguredRawOffset(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LOCALE_JSON);
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

            DvmObject<?> tzObj = invokeStaticObject(jni, baseVM, useVaList,
                    "java/util/TimeZone", "getDefault", TIMEZONE_GET_DEFAULT);
            assertNotNull(tzObj);
            assertFalse("configured TimeZone must not carry host TimeZone",
                    tzObj.getValue() instanceof TimeZone);
            assertEquals("Asia/Shanghai", invokeStringMethod(jni, baseVM, useVaList, tzObj,
                    "java/util/TimeZone", "getID"));
            assertEquals(ASIA_SHANGHAI_RAW_OFFSET, invokeIntMethod(jni, baseVM, useVaList, tzObj,
                    "java/util/TimeZone", "getRawOffset"));

            CapturedEvent tzRaw = findLastEvent(sink.events, "android_locale", "TimeZone.getRawOffset");
            assertNotNull(tzRaw);
            assertEquals("json-config", tzRaw.source);
            assertEquals("timezoneId=Asia/Shanghai,rawOffsetMillis=28800000",
                    String.valueOf(tzRaw.value));

            int rawEventsAfterMarker = countEvents(sink.events, "android_locale", "TimeZone.getRawOffset");
            DvmObject<?> ordinary = vm.resolveClass("java/util/TimeZone")
                    .newObject(TimeZone.getTimeZone("Asia/Shanghai"));
            try {
                invokeIntMethod(jni, baseVM, useVaList, ordinary,
                        "java/util/TimeZone", "getRawOffset");
                fail("expected UnsupportedOperationException for ordinary TimeZone getRawOffset");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("TimeZone->getRawOffset"));
            }
            assertEquals(rawEventsAfterMarker,
                    countEvents(sink.events, "android_locale", "TimeZone.getRawOffset"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentLocale(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
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
                invokeStaticObject(jni, baseVM, useVaList,
                        "java/util/TimeZone", "getDefault", TIMEZONE_GET_DEFAULT);
                fail("expected UnsupportedOperationException for TimeZone.getDefault without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("TimeZone->getDefault"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_locale sidecar without android.locale: " + e.api,
                        "android_locale".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeStaticObject(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   String className, String methodName, String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, true);
        String signature = method.getSignature();
        if (useVaList) {
            TestVaList vaList = new TestVaList(vm, method);
            return jni.callStaticObjectMethodV(vm, dvmClass, signature, vaList);
        }
        TestVarArg varArg = new TestVarArg(vm, method);
        return jni.callStaticObjectMethod(vm, dvmClass, signature, varArg);
    }

    private static String invokeStringMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             DvmObject<?> receiver, String className, String methodName) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, STRING_NO_ARGS, false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            TestVaList vaList = new TestVaList(vm, method);
            result = jni.callObjectMethodV(vm, receiver, signature, vaList);
        } else {
            TestVarArg varArg = new TestVarArg(vm, method);
            result = jni.callObjectMethod(vm, receiver, signature, varArg);
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static int invokeIntMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                       DvmObject<?> receiver, String className, String methodName) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, receiver, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, receiver, signature, new TestVarArg(vm, method));
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

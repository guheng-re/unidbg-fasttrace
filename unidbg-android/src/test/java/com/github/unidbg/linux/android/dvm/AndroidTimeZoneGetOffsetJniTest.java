package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidTimeZoneGetOffsetJniTest {

    private static final String LOCALE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"locale\":{"
            + "\"languageTag\":\"en-US\","
            + "\"timezoneId\":\"America/New_York\""
            + "}"
            + "}"
            + "}";

    private static final String LOCALE_JSON_B = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"locale\":{"
            + "\"languageTag\":\"zh-Hans-CN\","
            + "\"timezoneId\":\"Asia/Shanghai\""
            + "}"
            + "}"
            + "}";

    private static final String TIMEZONE_GET_DEFAULT = "()Ljava/util/TimeZone;";
    private static final String LOCALE_GET_DEFAULT = "()Ljava/util/Locale;";
    private static final String GET_ID = "()Ljava/lang/String;";
    private static final String GET_RAW_OFFSET = "()I";
    private static final String GET_OFFSET_J = "(J)I";
    private static final String GET_OFFSET_NO_ARGS = "()I";
    private static final String GET_OFFSET_SIX_INTS = "(IIIIII)I";

    private static final long EPOCH_2024_01_15_12Z =
            Instant.parse("2024-01-15T12:00:00Z").toEpochMilli();
    private static final long EPOCH_2024_07_15_12Z =
            Instant.parse("2024-07-15T12:00:00Z").toEpochMilli();
    private static final int NY_WINTER_OFFSET = -18000000;
    private static final int NY_SUMMER_OFFSET = -14400000;

    @Test
    public void testTimeZoneGetOffsetVarArg32() throws Exception {
        runConfiguredGetOffset(false, false);
    }

    @Test
    public void testTimeZoneGetOffsetVaList64() throws Exception {
        runConfiguredGetOffset(true, true);
    }

    @Test
    public void testTimeZoneGetOffsetRejectedVarArg32() throws Exception {
        runRejectedReceivers(false, false);
    }

    @Test
    public void testTimeZoneGetOffsetRejectedVaList64() throws Exception {
        runRejectedReceivers(true, true);
    }

    @Test
    public void testTimeZoneGetOffsetAbsentVarArg32() throws Exception {
        runAbsentLocale(false, false);
    }

    @Test
    public void testTimeZoneGetOffsetAbsentVaList64() throws Exception {
        runAbsentLocale(true, true);
    }

    @Test
    public void testTimeZoneGetOffsetCrossVmVarArg32() throws Exception {
        runCrossVmRejected(false, false);
    }

    @Test
    public void testTimeZoneGetOffsetCrossVmVaList64() throws Exception {
        runCrossVmRejected(true, true);
    }

    private static void runConfiguredGetOffset(boolean is64Bit, boolean useVaList) throws Exception {
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

            assertEquals(NY_WINTER_OFFSET, invokeGetOffset(jni, baseVM, useVaList, tzObj,
                    EPOCH_2024_01_15_12Z));
            assertEquals(NY_SUMMER_OFFSET, invokeGetOffset(jni, baseVM, useVaList, tzObj,
                    EPOCH_2024_07_15_12Z));

            CapturedEvent winter = findEventWithEpoch(sink.events, EPOCH_2024_01_15_12Z);
            assertNotNull(winter);
            assertEquals("android_locale", winter.kind);
            assertEquals("TimeZone.getOffset", winter.api);
            assertEquals("json-config", winter.source);
            assertEquals("timezoneId=America/New_York,epochMillis=" + EPOCH_2024_01_15_12Z
                            + ",offsetMillis=" + NY_WINTER_OFFSET,
                    String.valueOf(winter.value));
            assertEquals("按配置时区读取指定时刻偏移", winter.note);

            CapturedEvent summer = findEventWithEpoch(sink.events, EPOCH_2024_07_15_12Z);
            assertNotNull(summer);
            assertEquals("android_locale", summer.kind);
            assertEquals("TimeZone.getOffset", summer.api);
            assertEquals("json-config", summer.source);
            assertEquals("timezoneId=America/New_York,epochMillis=" + EPOCH_2024_07_15_12Z
                            + ",offsetMillis=" + NY_SUMMER_OFFSET,
                    String.valueOf(summer.value));
            assertEquals("按配置时区读取指定时刻偏移", summer.note);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runRejectedReceivers(boolean is64Bit, boolean useVaList) throws Exception {
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
            DvmObject<?> localeObj = invokeStaticObject(jni, baseVM, useVaList,
                    "java/util/Locale", "getDefault", LOCALE_GET_DEFAULT);
            int offsetEventsAfterMarker = countEvents(sink.events, "android_locale", "TimeZone.getOffset");

            DvmObject<?> ordinary = vm.resolveClass("java/util/TimeZone")
                    .newObject(TimeZone.getTimeZone("America/New_York"));
            expectGetOffsetUoe(jni, baseVM, useVaList, ordinary, EPOCH_2024_01_15_12Z,
                    "ordinary TimeZone getOffset");

            DvmObject<?> foreign = vm.resolveClass("java/util/TimeZone").newObject("foreign");
            expectGetOffsetUoe(jni, baseVM, useVaList, foreign, EPOCH_2024_01_15_12Z,
                    "foreign TimeZone marker getOffset");

            expectGetOffsetUoe(jni, baseVM, useVaList, localeObj, EPOCH_2024_01_15_12Z,
                    "ConfiguredLocale getOffset");

            try {
                invokeIntMethod(jni, baseVM, useVaList, tzObj,
                        "java/util/TimeZone", "getOffset", GET_OFFSET_NO_ARGS);
                fail("expected UnsupportedOperationException for getOffset()I");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("TimeZone->getOffset()I"));
            }
            try {
                invokeIntMethodSixInts(jni, baseVM, useVaList, tzObj);
                fail("expected UnsupportedOperationException for getOffset(IIIIII)I");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("TimeZone->getOffset(IIIIII)I"));
            }

            assertEquals(offsetEventsAfterMarker,
                    countEvents(sink.events, "android_locale", "TimeZone.getOffset"));
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

            DvmObject<?> ordinary = vm.resolveClass("java/util/TimeZone")
                    .newObject(TimeZone.getTimeZone("America/New_York"));
            expectGetOffsetUoe(jni, baseVM, useVaList, ordinary, EPOCH_2024_01_15_12Z,
                    "getOffset without android.locale");

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

    private static void runCrossVmRejected(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(LOCALE_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(LOCALE_JSON_B);
        AndroidEmulator emuA = null;
        AndroidEmulator emuB = null;
        CapturingSink sinkB = new CapturingSink();
        try {
            emuA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            emuB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configB)
                    .build();
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

            DvmObject<?> tzA = invokeStaticObject(jniA, baseA, useVaList,
                    "java/util/TimeZone", "getDefault", TIMEZONE_GET_DEFAULT);
            expectObjectMethodUoe(jniB, baseB, useVaList, tzA,
                    "java/util/TimeZone", "getID", GET_ID, "TimeZone->getID",
                    "cross-VM TimeZone getID");
            expectIntMethodUoe(jniB, baseB, useVaList, tzA,
                    "java/util/TimeZone", "getRawOffset", GET_RAW_OFFSET, "TimeZone->getRawOffset",
                    "cross-VM TimeZone getRawOffset");
            expectGetOffsetUoe(jniB, baseB, useVaList, tzA, EPOCH_2024_01_15_12Z,
                    "cross-VM TimeZone getOffset");
            for (CapturedEvent e : sinkB.events) {
                assertFalse("leak to VM B: " + e.api, "android_locale".equals(e.kind));
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

    private static void expectGetOffsetUoe(AbstractJni jni, BaseVM vm, boolean useVaList,
                                           DvmObject<?> receiver, long epochMillis, String label) {
        try {
            invokeGetOffset(jni, vm, useVaList, receiver, epochMillis);
            fail("expected UnsupportedOperationException for " + label);
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("TimeZone->getOffset"));
        }
    }

    private static void expectObjectMethodUoe(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> receiver, String className, String methodName,
                                              String args, String messageNeedle, String label) {
        try {
            invokeObjectMethod(jni, vm, useVaList, receiver, className, methodName, args);
            fail("expected UnsupportedOperationException for " + label);
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains(messageNeedle));
        }
    }

    private static void expectIntMethodUoe(AbstractJni jni, BaseVM vm, boolean useVaList,
                                           DvmObject<?> receiver, String className, String methodName,
                                           String args, String messageNeedle, String label) {
        try {
            invokeIntMethod(jni, vm, useVaList, receiver, className, methodName, args);
            fail("expected UnsupportedOperationException for " + label);
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains(messageNeedle));
        }
    }

    private static DvmObject<?> invokeStaticObject(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   String className, String methodName, String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
    }

    private static int invokeGetOffset(AbstractJni jni, BaseVM vm, boolean useVaList,
                                       DvmObject<?> receiver, long epochMillis) {
        DvmClass dvmClass = vm.resolveClass("java/util/TimeZone");
        DvmMethod method = new DvmMethod(dvmClass, "getOffset", GET_OFFSET_J, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, receiver, signature, new TestVaList(vm, method, epochMillis));
        }
        return jni.callIntMethod(vm, receiver, signature, new TestVarArg(vm, method, epochMillis));
    }

    private static DvmObject<?> invokeObjectMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmObject<?> receiver, String className, String methodName,
                                                   String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, receiver, signature, new TestVarArg(vm, method));
    }

    private static int invokeIntMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                       DvmObject<?> receiver, String className, String methodName,
                                       String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, receiver, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, receiver, signature, new TestVarArg(vm, method));
    }

    private static int invokeIntMethodSixInts(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> receiver) {
        DvmClass dvmClass = vm.resolveClass("java/util/TimeZone");
        DvmMethod method = new DvmMethod(dvmClass, "getOffset", GET_OFFSET_SIX_INTS, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, receiver, signature, new TestVaList(vm, method, 1, 2024, 6, 15, 1, 0));
        }
        return jni.callIntMethod(vm, receiver, signature, new TestVarArg(vm, method, 1, 2024, 6, 15, 1, 0));
    }

    private static CapturedEvent findEventWithEpoch(List<CapturedEvent> events, long epochMillis) {
        String needle = "epochMillis=" + epochMillis;
        CapturedEvent found = null;
        for (CapturedEvent e : events) {
            if ("android_locale".equals(e.kind) && "TimeZone.getOffset".equals(e.api)
                    && String.valueOf(e.value).contains(needle)) {
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

        TestVarArg(BaseVM vm, DvmMethod method, long epochMillis) {
            super(vm, method);
            args.add(epochMillis);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int a0, int a1, int a2, int a3, int a4, int a5) {
            super(vm, method);
            args.add(a0);
            args.add(a1);
            args.add(a2);
            args.add(a3);
            args.add(a4);
            args.add(a5);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVaList(BaseVM vm, DvmMethod method, long epochMillis) {
            super(vm, method);
            args.add(epochMillis);
        }

        TestVaList(BaseVM vm, DvmMethod method, int a0, int a1, int a2, int a3, int a4, int a5) {
            super(vm, method);
            args.add(a0);
            args.add(a1);
            args.add(a2);
            args.add(a3);
            args.add(a4);
            args.add(a5);
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

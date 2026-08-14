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

/**
 * Coverage for {@code time.monotonicNanos} → static
 * {@code System.nanoTime()J} (VarArg + VaList). Returns the exact long
 * (not truncated to milliseconds). Isolated from {@code currentTimeMillis}
 * and SystemClock APIs.
 */
public class AndroidSystemNanoTimeJniTest {

    private static final String NANO_TIME_SIGNATURE =
            "java/lang/System->nanoTime()J";

    /** Not divisible by 1_000_000: 1_234_567_890_001 / 1_000_000 = 1_234_567. */
    private static final long NON_DIVISIBLE_NANOS = 1234567890001L;
    private static final long TRUNCATED_MILLIS = 1234567L;
    private static final long CURRENT_TIME_MILLIS = 1718000000000L;

    private static final String CONFIGURED_JSON = "{"
            + "\"time\":{\"monotonicNanos\":" + NON_DIVISIBLE_NANOS + "}"
            + "}";

    private static final String BOTH_TIME_JSON = "{"
            + "\"time\":{\"currentTimeMillis\":" + CURRENT_TIME_MILLIS
            + ",\"monotonicNanos\":" + NON_DIVISIBLE_NANOS + "}"
            + "}";

    private static final String NO_MONOTONIC_JSON = "{"
            + "\"time\":{\"currentTimeMillis\":" + CURRENT_TIME_MILLIS + "}"
            + "}";

    private static final String NO_TIME_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testNanoTimeConfiguredVarArg32() throws Exception {
        runConfigured(false, false);
    }

    @Test
    public void testNanoTimeConfiguredVaList64() throws Exception {
        runConfigured(true, true);
    }

    @Test
    public void testNanoTimeMissingFieldVarArg32() throws Exception {
        runMissing(false, false, NO_MONOTONIC_JSON);
    }

    @Test
    public void testNanoTimeMissingTimeVaList64() throws Exception {
        runMissing(true, true, NO_TIME_JSON);
    }

    @Test
    public void testNanoTimeWrongAndAdjacentVarArg32() throws Exception {
        runWrongAndAdjacent(false, false);
    }

    @Test
    public void testNanoTimeWrongAndAdjacentVaList64() throws Exception {
        runWrongAndAdjacent(true, true);
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
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
            DvmClass systemClass = vm.resolveClass("java/lang/System");

            assertEquals(NON_DIVISIBLE_NANOS,
                    invokeNanoTime(jni, baseVM, useVaList, systemClass));

            CapturedEvent ev = findLastEvent(sink.events, "time", "System.nanoTime");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("monotonicNanos=" + NON_DIVISIBLE_NANOS, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "time", "System.nanoTime"));
            assertEquals(0, countEvents(sink.events, "time", "System.currentTimeMillis"));
            assertEquals(0, countEvents(sink.events, "time", "SystemClock.elapsedRealtime"));
            assertEquals(0, countEvents(sink.events, "time", "SystemClock.elapsedRealtimeNanos"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMissing(boolean is64Bit, boolean useVaList, String json) throws Exception {
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
            DvmClass systemClass = vm.resolveClass("java/lang/System");

            try {
                invokeNanoTime(jni, baseVM, useVaList, systemClass);
                fail("expected UnsupportedOperationException without time.monotonicNanos");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("nanoTime"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected System.nanoTime event when config missing: " + e.api,
                        "System.nanoTime".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runWrongAndAdjacent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(BOTH_TIME_JSON);
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
            DvmClass systemClass = vm.resolveClass("java/lang/System");
            DvmClass clockClass = vm.resolveClass("android/os/SystemClock");

            DvmMethod wrong = new DvmMethod(systemClass, "nanoTime", "(J)J", true);
            String wrongSignature = wrong.getSignature();
            try {
                invokeStaticLong(jni, baseVM, useVaList, systemClass, wrongSignature, wrong);
                fail("expected UnsupportedOperationException for nanoTime(J)J");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("nanoTime"));
            }

            DvmMethod getProperty = new DvmMethod(systemClass, "getProperty",
                    "(Ljava/lang/String;)Ljava/lang/String;", true);
            String getPropertySignature = getProperty.getSignature();
            try {
                if (useVaList) {
                    jni.callStaticObjectMethodV(baseVM, systemClass, getPropertySignature,
                            new TestNoArgVaList(baseVM, getProperty));
                } else {
                    jni.callStaticObjectMethod(baseVM, systemClass, getPropertySignature,
                            new TestNoArgVarArg(baseVM, getProperty));
                }
                fail("expected UnsupportedOperationException for System.getProperty");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProperty"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected System.nanoTime event for non-API: " + e.api,
                        "System.nanoTime".equals(e.api));
            }

            DvmMethod currentTimeMillis = new DvmMethod(systemClass, "currentTimeMillis", "()J", true);
            assertEquals(CURRENT_TIME_MILLIS,
                    invokeStaticLong(jni, baseVM, useVaList, systemClass,
                            currentTimeMillis.getSignature(), currentTimeMillis));
            assertEquals(1, countEvents(sink.events, "time", "System.currentTimeMillis"));

            DvmMethod elapsedRealtime = new DvmMethod(clockClass, "elapsedRealtime", "()J", true);
            assertEquals(TRUNCATED_MILLIS,
                    invokeStaticLong(jni, baseVM, useVaList, clockClass,
                            elapsedRealtime.getSignature(), elapsedRealtime));
            assertEquals(1, countEvents(sink.events, "time", "SystemClock.elapsedRealtime"));

            DvmMethod elapsedRealtimeNanos = new DvmMethod(clockClass, "elapsedRealtimeNanos", "()J", true);
            assertEquals(NON_DIVISIBLE_NANOS,
                    invokeStaticLong(jni, baseVM, useVaList, clockClass,
                            elapsedRealtimeNanos.getSignature(), elapsedRealtimeNanos));
            assertEquals(1, countEvents(sink.events, "time", "SystemClock.elapsedRealtimeNanos"));

            assertEquals(0, countEvents(sink.events, "time", "System.nanoTime"));

            assertEquals(NON_DIVISIBLE_NANOS,
                    invokeNanoTime(jni, baseVM, useVaList, systemClass));
            assertEquals(1, countEvents(sink.events, "time", "System.nanoTime"));
            assertEquals(1, countEvents(sink.events, "time", "System.currentTimeMillis"));
            assertEquals(1, countEvents(sink.events, "time", "SystemClock.elapsedRealtime"));
            assertEquals(1, countEvents(sink.events, "time", "SystemClock.elapsedRealtimeNanos"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static long invokeNanoTime(AbstractJni jni, BaseVM vm, boolean useVaList,
                                       DvmClass systemClass) {
        DvmMethod method = new DvmMethod(systemClass, "nanoTime", "()J", true);
        String signature = method.getSignature();
        assertEquals(NANO_TIME_SIGNATURE, signature);
        return invokeStaticLong(jni, vm, useVaList, systemClass, signature, method);
    }

    private static long invokeStaticLong(AbstractJni jni, BaseVM vm, boolean useVaList,
                                         DvmClass dvmClass, String signature, DvmMethod method) {
        if (useVaList) {
            return jni.callStaticLongMethodV(vm, dvmClass, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callStaticLongMethod(vm, dvmClass, signature,
                new TestNoArgVarArg(vm, method));
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

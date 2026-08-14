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
 * {@code SystemClock.elapsedRealtime()J} / {@code elapsedRealtimeNanos()J} /
 * {@code uptimeMillis()J} (VarArg + VaList).
 */
public class AndroidSystemClockJniTest {

    private static final String ELAPSED_REALTIME_SIGNATURE =
            "android/os/SystemClock->elapsedRealtime()J";
    private static final String ELAPSED_REALTIME_NANOS_SIGNATURE =
            "android/os/SystemClock->elapsedRealtimeNanos()J";
    private static final String UPTIME_MILLIS_SIGNATURE =
            "android/os/SystemClock->uptimeMillis()J";

    /** Not divisible by 1_000_000: 1_234_567_890_001 / 1_000_000 = 1_234_567. */
    private static final long NON_DIVISIBLE_NANOS = 1234567890001L;
    private static final long EXPECTED_MILLIS = 1234567L;

    private static final String MONOTONIC_JSON = "{"
            + "\"time\":{\"monotonicNanos\":" + NON_DIVISIBLE_NANOS + "}"
            + "}";

    private static final String NO_MONOTONIC_JSON = "{"
            + "\"time\":{\"currentTimeMillis\":1718000000000}"
            + "}";

    private static final String NO_TIME_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testElapsedRealtimeNonDivisibleVarArg32() throws Exception {
        runConfiguredElapsedRealtime(false, false, MONOTONIC_JSON,
                NON_DIVISIBLE_NANOS, EXPECTED_MILLIS);
    }

    @Test
    public void testElapsedRealtimeNonDivisibleVaList64() throws Exception {
        runConfiguredElapsedRealtime(true, true, MONOTONIC_JSON,
                NON_DIVISIBLE_NANOS, EXPECTED_MILLIS);
    }

    @Test
    public void testElapsedRealtimeMissingMonotonicVarArg32() throws Exception {
        runMissingElapsedRealtime(false, false, NO_MONOTONIC_JSON);
    }

    @Test
    public void testElapsedRealtimeMissingMonotonicVaList64() throws Exception {
        runMissingElapsedRealtime(true, true, NO_MONOTONIC_JSON);
    }

    @Test
    public void testElapsedRealtimeMissingTimeVarArg32() throws Exception {
        runMissingElapsedRealtime(false, false, NO_TIME_JSON);
    }

    @Test
    public void testElapsedRealtimeMissingTimeVaList64() throws Exception {
        runMissingElapsedRealtime(true, true, NO_TIME_JSON);
    }

    @Test
    public void testElapsedRealtimeNanosExactVarArg32() throws Exception {
        runConfiguredElapsedRealtimeNanos(false, false, MONOTONIC_JSON, NON_DIVISIBLE_NANOS);
    }

    @Test
    public void testElapsedRealtimeNanosExactVaList64() throws Exception {
        runConfiguredElapsedRealtimeNanos(true, true, MONOTONIC_JSON, NON_DIVISIBLE_NANOS);
    }

    @Test
    public void testElapsedRealtimeNanosMissingMonotonicVarArg32() throws Exception {
        runMissingElapsedRealtimeNanos(false, false, NO_MONOTONIC_JSON);
    }

    @Test
    public void testElapsedRealtimeNanosMissingMonotonicVaList64() throws Exception {
        runMissingElapsedRealtimeNanos(true, true, NO_MONOTONIC_JSON);
    }

    @Test
    public void testElapsedRealtimeNanosMissingTimeVarArg32() throws Exception {
        runMissingElapsedRealtimeNanos(false, false, NO_TIME_JSON);
    }

    @Test
    public void testElapsedRealtimeNanosMissingTimeVaList64() throws Exception {
        runMissingElapsedRealtimeNanos(true, true, NO_TIME_JSON);
    }

    @Test
    public void testUptimeMillisNonDivisibleVarArg32() throws Exception {
        runConfiguredUptimeMillis(false, false, MONOTONIC_JSON,
                NON_DIVISIBLE_NANOS, EXPECTED_MILLIS);
    }

    @Test
    public void testUptimeMillisNonDivisibleVaList64() throws Exception {
        runConfiguredUptimeMillis(true, true, MONOTONIC_JSON,
                NON_DIVISIBLE_NANOS, EXPECTED_MILLIS);
    }

    @Test
    public void testUptimeMillisMissingMonotonicVarArg32() throws Exception {
        runMissingUptimeMillis(false, false, NO_MONOTONIC_JSON);
    }

    @Test
    public void testUptimeMillisMissingMonotonicVaList64() throws Exception {
        runMissingUptimeMillis(true, true, NO_MONOTONIC_JSON);
    }

    @Test
    public void testUptimeMillisNotThisApiVarArg32() throws Exception {
        runUptimeMillisNotThisApi(false, false);
    }

    @Test
    public void testUptimeMillisNotThisApiVaList64() throws Exception {
        runUptimeMillisNotThisApi(true, true);
    }

    private static void runConfiguredElapsedRealtime(boolean is64Bit, boolean useVaList,
                                                     String json, long nanos, long expectedMillis)
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
            DvmClass clockClass = vm.resolveClass("android/os/SystemClock");

            assertEquals(expectedMillis,
                    invokeElapsedRealtime(jni, baseVM, useVaList, clockClass));

            CapturedEvent ev = findLastEvent(sink.events, "time", "SystemClock.elapsedRealtime");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("monotonicNanos=" + nanos + ",resultMillis=" + expectedMillis,
                    String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "time", "SystemClock.elapsedRealtime"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMissingElapsedRealtime(boolean is64Bit, boolean useVaList, String json)
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
            DvmClass clockClass = vm.resolveClass("android/os/SystemClock");

            try {
                invokeElapsedRealtime(jni, baseVM, useVaList, clockClass);
                fail("expected UnsupportedOperationException without time.monotonicNanos");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("elapsedRealtime"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected SystemClock.elapsedRealtime event when config missing: "
                                + e.api,
                        "SystemClock.elapsedRealtime".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredElapsedRealtimeNanos(boolean is64Bit, boolean useVaList,
                                                          String json, long expectedNanos)
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
            DvmClass clockClass = vm.resolveClass("android/os/SystemClock");

            assertEquals(expectedNanos,
                    invokeElapsedRealtimeNanos(jni, baseVM, useVaList, clockClass));

            CapturedEvent ev = findLastEvent(sink.events, "time",
                    "SystemClock.elapsedRealtimeNanos");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("monotonicNanos=" + expectedNanos + ",resultNanos=" + expectedNanos,
                    String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "time",
                    "SystemClock.elapsedRealtimeNanos"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMissingElapsedRealtimeNanos(boolean is64Bit, boolean useVaList,
                                                       String json) throws Exception {
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
            DvmClass clockClass = vm.resolveClass("android/os/SystemClock");

            try {
                invokeElapsedRealtimeNanos(jni, baseVM, useVaList, clockClass);
                fail("expected UnsupportedOperationException without time.monotonicNanos");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("elapsedRealtimeNanos"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected SystemClock.elapsedRealtimeNanos event when config missing: "
                                + e.api,
                        "SystemClock.elapsedRealtimeNanos".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredUptimeMillis(boolean is64Bit, boolean useVaList,
                                                  String json, long nanos, long expectedMillis)
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
            DvmClass clockClass = vm.resolveClass("android/os/SystemClock");

            assertEquals(expectedMillis,
                    invokeUptimeMillis(jni, baseVM, useVaList, clockClass));

            CapturedEvent ev = findLastEvent(sink.events, "time", "SystemClock.uptimeMillis");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("monotonicNanos=" + nanos + ",resultMillis=" + expectedMillis,
                    String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "time", "SystemClock.uptimeMillis"));
            assertEquals(0, countEvents(sink.events, "time", "SystemClock.elapsedRealtime"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMissingUptimeMillis(boolean is64Bit, boolean useVaList, String json)
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
            DvmClass clockClass = vm.resolveClass("android/os/SystemClock");

            try {
                invokeUptimeMillis(jni, baseVM, useVaList, clockClass);
                fail("expected UnsupportedOperationException without time.monotonicNanos");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("uptimeMillis"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected SystemClock.uptimeMillis event when config missing: "
                                + e.api,
                        "SystemClock.uptimeMillis".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runUptimeMillisNotThisApi(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MONOTONIC_JSON);
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
            DvmClass clockClass = vm.resolveClass("android/os/SystemClock");

            DvmMethod other = new DvmMethod(clockClass, "currentThreadTimeMillis", "()J", true);
            String otherSignature = other.getSignature();
            try {
                if (useVaList) {
                    jni.callStaticLongMethodV(baseVM, clockClass, otherSignature,
                            new TestNoArgVaList(baseVM, other));
                } else {
                    jni.callStaticLongMethod(baseVM, clockClass, otherSignature,
                            new TestNoArgVarArg(baseVM, other));
                }
                fail("expected UnsupportedOperationException for currentThreadTimeMillis");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("currentThreadTimeMillis"));
            }

            DvmMethod wrong = new DvmMethod(clockClass, "uptimeMillis", "(J)J", true);
            String wrongSignature = wrong.getSignature();
            try {
                if (useVaList) {
                    jni.callStaticLongMethodV(baseVM, clockClass, wrongSignature,
                            new TestNoArgVaList(baseVM, wrong));
                } else {
                    jni.callStaticLongMethod(baseVM, clockClass, wrongSignature,
                            new TestNoArgVarArg(baseVM, wrong));
                }
                fail("expected UnsupportedOperationException for uptimeMillis(J)J");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("uptimeMillis"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected SystemClock.uptimeMillis event for non-API: " + e.api,
                        "SystemClock.uptimeMillis".equals(e.api));
            }

            assertEquals(EXPECTED_MILLIS,
                    invokeUptimeMillis(jni, baseVM, useVaList, clockClass));
            assertEquals(1, countEvents(sink.events, "time", "SystemClock.uptimeMillis"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static long invokeElapsedRealtime(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmClass clockClass) {
        DvmMethod method = new DvmMethod(clockClass, "elapsedRealtime", "()J", true);
        String signature = method.getSignature();
        assertEquals(ELAPSED_REALTIME_SIGNATURE, signature);
        if (useVaList) {
            return jni.callStaticLongMethodV(vm, clockClass, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callStaticLongMethod(vm, clockClass, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static long invokeElapsedRealtimeNanos(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmClass clockClass) {
        DvmMethod method = new DvmMethod(clockClass, "elapsedRealtimeNanos", "()J", true);
        String signature = method.getSignature();
        assertEquals(ELAPSED_REALTIME_NANOS_SIGNATURE, signature);
        if (useVaList) {
            return jni.callStaticLongMethodV(vm, clockClass, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callStaticLongMethod(vm, clockClass, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static long invokeUptimeMillis(AbstractJni jni, BaseVM vm, boolean useVaList,
                                           DvmClass clockClass) {
        DvmMethod method = new DvmMethod(clockClass, "uptimeMillis", "()J", true);
        String signature = method.getSignature();
        assertEquals(UPTIME_MILLIS_SIGNATURE, signature);
        if (useVaList) {
            return jni.callStaticLongMethodV(vm, clockClass, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callStaticLongMethod(vm, clockClass, signature,
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

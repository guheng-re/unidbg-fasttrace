package com.github.unidbg.linux.android;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Focused ARM32/ARM64 tests for {@code libselinux.so!getcon}/{@code freecon} backed by
 * explicit {@code linux.proc.selinuxContext}.
 */
public class SelinuxGetconHookTest {

    private static final String CONTEXT = "u:r:untrusted_app:s0:c512,c768";
    private static final String CONTEXT_JSON = "{"
            + "\"linux\":{\"proc\":{\"selinuxContext\":\"" + CONTEXT + "\"}}"
            + "}";
    private static final String ABSENT_PROC_JSON =
            "{\"linux\":{\"proc\":{\"state\":\"S\"}}}";
    private static final String ABSENT_JSON =
            "{\"android\":{\"packageName\":\"com.demo.app\"}}";

    @Test
    public void testGetconBytesAndResultArm32() throws Exception {
        runGetconSuccess(false);
    }

    @Test
    public void testGetconBytesAndResultArm64() throws Exception {
        runGetconSuccess(true);
    }

    @Test
    public void testFreeconLifecycleArm32() throws Exception {
        runFreeconLifecycle(false);
    }

    @Test
    public void testFreeconLifecycleArm64() throws Exception {
        runFreeconLifecycle(true);
    }

    @Test
    public void testAbsenceArm32() throws Exception {
        runAbsence(false);
    }

    @Test
    public void testAbsenceArm64() throws Exception {
        runAbsence(true);
    }

    @Test
    public void testExactMatchAndNullOutArm32() throws Exception {
        runExactMatchAndNullOut(false);
    }

    @Test
    public void testExactMatchAndNullOutArm64() throws Exception {
        runExactMatchAndNullOut(true);
    }

    @Test
    public void testIsolationAndNoRawSidecarArm32() throws Exception {
        runIsolation(false);
    }

    @Test
    public void testIsolationAndNoRawSidecarArm64() throws Exception {
        runIsolation(true);
    }

    private static void runGetconSuccess(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONTEXT_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            SelinuxGetconHook hook = new SelinuxGetconHook(emulator);

            MemoryBlock outSlot = emulator.getMemory().malloc(emulator.getPointerSize(), true);
            blocks.add(outSlot);
            // poison so we can see write
            outSlot.getPointer().setPointer(0, null);

            Integer rc = hook.tryGetcon(emulator, outSlot.getPointer());
            assertNotNull(rc);
            assertEquals(0, rc.intValue());

            Pointer written = outSlot.getPointer().getPointer(0);
            assertNotNull(written);
            long peer = UnidbgPointer.nativeValue(written);
            assertTrue(hook.isTracked(peer));
            assertEquals(1, hook.trackedCount());

            byte[] expected = CONTEXT.getBytes(StandardCharsets.UTF_8);
            byte[] actual = written.getByteArray(0, expected.length);
            assertArrayEquals(expected, actual);
            assertEquals(0, written.getByte(expected.length));

            CapturedEvent ev = findLast(sink.events, "linux_security", "getcon");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("result=0,length=" + expected.length, String.valueOf(ev.value));
            assertFalse("sidecar must not contain raw context",
                    String.valueOf(ev.value).contains(CONTEXT));
            assertFalse(ev.note != null && ev.note.contains(CONTEXT));
            assertEquals(1, countApi(sink.events, "getcon"));
            assertEquals(0, countApi(sink.events, "freecon"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runFreeconLifecycle(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONTEXT_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            SelinuxGetconHook hook = new SelinuxGetconHook(emulator);

            MemoryBlock outSlot = emulator.getMemory().malloc(emulator.getPointerSize(), true);
            blocks.add(outSlot);
            assertEquals(Integer.valueOf(0), hook.tryGetcon(emulator, outSlot.getPointer()));
            Pointer ctx = outSlot.getPointer().getPointer(0);
            long peer = UnidbgPointer.nativeValue(ctx);
            assertTrue(hook.isTracked(peer));

            // freecon of our pointer succeeds; no freecon event
            assertTrue(hook.tryFreecon(ctx));
            assertFalse(hook.isTracked(peer));
            assertEquals(0, hook.trackedCount());
            assertEquals(0, countApi(sink.events, "freecon"));

            // double free / foreign pointer → not ours
            assertFalse(hook.tryFreecon(ctx));
            MemoryBlock foreign = emulator.getMemory().malloc(8, true);
            blocks.add(foreign);
            assertFalse(hook.tryFreecon(foreign.getPointer()));
            assertFalse(hook.tryFreecon(null));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runAbsence(boolean is64Bit) throws Exception {
        for (String json : new String[]{ABSENT_JSON, ABSENT_PROC_JSON}) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
            AndroidEmulator emulator = null;
            CapturingSink sink = new CapturingSink();
            List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
            try {
                emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                TraceEnvironmentEventSink.register(emulator, sink);
                assertNull(SelinuxGetconHook.configuredContext(emulator));

                SelinuxGetconHook hook = new SelinuxGetconHook(emulator);
                MemoryBlock outSlot = emulator.getMemory().malloc(emulator.getPointerSize(), true);
                blocks.add(outSlot);
                assertNull(hook.tryGetcon(emulator, outSlot.getPointer()));
                for (CapturedEvent e : sink.events) {
                    assertFalse("unexpected getcon event",
                            "linux_security".equals(e.kind) && "getcon".equals(e.api));
                }
            } finally {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                }
                freeAll(blocks);
                if (emulator != null) {
                    emulator.close();
                }
            }
        }
    }

    private static void runExactMatchAndNullOut(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONTEXT_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            SelinuxGetconHook hook = new SelinuxGetconHook(emulator);
            long old = 0x4000L;

            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libc.so", "getcon", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "getfilecon", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "getpeercon", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "setcon", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "security_getenforce", old));

            long getconAddr = hook.hook(emulator.getSvcMemory(),
                    SelinuxGetconHook.LIBRARY, SelinuxGetconHook.GETCON, old);
            assertTrue(getconAddr != 0L);
            long freeconAddr = hook.hook(emulator.getSvcMemory(),
                    SelinuxGetconHook.LIBRARY, SelinuxGetconHook.FREECON, old);
            assertTrue(freeconAddr != 0L);

            // null output pointer → not handled, no event
            assertNull(hook.tryGetcon(emulator, null));
            for (CapturedEvent e : sink.events) {
                assertFalse("getcon event on null out",
                        "linux_security".equals(e.kind) && "getcon".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsolation(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(CONTEXT_JSON);
        TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(ABSENT_PROC_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        List<MemoryBlock> blocksA = new ArrayList<MemoryBlock>();
        List<MemoryBlock> blocksB = new ArrayList<MemoryBlock>();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configA)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(configB)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            SelinuxGetconHook hookA = new SelinuxGetconHook(emulatorA);
            SelinuxGetconHook hookB = new SelinuxGetconHook(emulatorB);

            MemoryBlock outA = emulatorA.getMemory().malloc(emulatorA.getPointerSize(), true);
            blocksA.add(outA);
            assertEquals(Integer.valueOf(0), hookA.tryGetcon(emulatorA, outA.getPointer()));
            CapturedEvent evA = findLast(sinkA.events, "linux_security", "getcon");
            assertNotNull(evA);
            assertFalse(String.valueOf(evA.value).contains(CONTEXT));
            assertEquals(1, countApi(sinkA.events, "getcon"));

            MemoryBlock outB = emulatorB.getMemory().malloc(emulatorB.getPointerSize(), true);
            blocksB.add(outB);
            assertNull(hookB.tryGetcon(emulatorB, outB.getPointer()));
            for (CapturedEvent e : sinkB.events) {
                assertFalse("leak getcon to B",
                        "linux_security".equals(e.kind) && "getcon".equals(e.api));
            }
            // B freecon must not free A's allocation tracking
            Pointer ctxA = outA.getPointer().getPointer(0);
            assertFalse(hookB.tryFreecon(ctxA));
            assertTrue(hookA.isTracked(UnidbgPointer.nativeValue(ctxA)));
            assertTrue(hookA.tryFreecon(ctxA));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
            }
            freeAll(blocksA);
            freeAll(blocksB);
            if (emulatorA != null) {
                emulatorA.close();
            }
            if (emulatorB != null) {
                emulatorB.close();
            }
        }
    }

    private static void freeAll(List<MemoryBlock> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            try {
                blocks.get(i).free();
            } catch (Throwable ignored) {
                // best-effort
            }
        }
        blocks.clear();
    }

    private static CapturedEvent findLast(List<CapturedEvent> events, String kind, String api) {
        CapturedEvent found = null;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)) {
                found = e;
            }
        }
        return found;
    }

    private static int countApi(List<CapturedEvent> events, String api) {
        int n = 0;
        for (CapturedEvent e : events) {
            if (api.equals(e.api)) {
                n++;
            }
        }
        return n;
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
        public void emitEnvironmentEvent(String kind, String api, Object value, String source,
                                         String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}

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
 * Focused ARM32/ARM64 tests for {@code libselinux.so!getfilecon}/{@code freecon}
 * backed by explicit {@code linux.proc.fileSelinuxContexts}.
 */
public class SelinuxGetfileconHookTest {

    private static final String PATH = "/data/data/com.demo.app";
    private static final String CONTEXT = "u:object_r:app_data_file:s0";
    private static final String OTHER_PATH = "/system/bin/app_process64";
    private static final String OTHER_CONTEXT = "u:object_r:zygote_exec:s0";
    private static final String UNCONFIGURED_PATH = "/data/local/tmp/x";

    private static final String MAP_JSON = "{"
            + "\"linux\":{\"proc\":{\"fileSelinuxContexts\":{"
            + "\"" + PATH + "\":\"" + CONTEXT + "\","
            + "\"" + OTHER_PATH + "\":\"" + OTHER_CONTEXT + "\""
            + "}}}}";
    private static final String ABSENT_PROC_JSON =
            "{\"linux\":{\"proc\":{\"state\":\"S\"}}}";
    private static final String ABSENT_JSON =
            "{\"android\":{\"packageName\":\"com.demo.app\"}}";
    private static final String SELINUX_ONLY_JSON = "{"
            + "\"linux\":{\"proc\":{\"selinuxContext\":\"u:r:untrusted_app:s0\"}}"
            + "}";

    @Test
    public void testGetfileconBytesAndResultArm32() throws Exception {
        runGetfileconSuccess(false);
    }

    @Test
    public void testGetfileconBytesAndResultArm64() throws Exception {
        runGetfileconSuccess(true);
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
    public void testAbsenceAndUnconfiguredPathArm32() throws Exception {
        runAbsenceAndUnconfigured(false);
    }

    @Test
    public void testAbsenceAndUnconfiguredPathArm64() throws Exception {
        runAbsenceAndUnconfigured(true);
    }

    @Test
    public void testNullArgsAndExactMatchArm32() throws Exception {
        runNullAndExactMatch(false);
    }

    @Test
    public void testNullArgsAndExactMatchArm64() throws Exception {
        runNullAndExactMatch(true);
    }

    @Test
    public void testIsolationAndNoRawSidecarArm32() throws Exception {
        runIsolation(false);
    }

    @Test
    public void testIsolationAndNoRawSidecarArm64() throws Exception {
        runIsolation(true);
    }

    private static void runGetfileconSuccess(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MAP_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertTrue(SelinuxGetconHook.shouldRegister(emulator));
            SelinuxGetconHook hook = new SelinuxGetconHook(emulator);

            MemoryBlock pathBlock = writeCString(emulator, PATH, blocks);
            MemoryBlock outSlot = emulator.getMemory().malloc(emulator.getPointerSize(), true);
            blocks.add(outSlot);
            outSlot.getPointer().setPointer(0, null);

            Integer rc = hook.tryGetfilecon(emulator, pathBlock.getPointer(), outSlot.getPointer());
            assertNotNull(rc);
            byte[] expected = CONTEXT.getBytes(StandardCharsets.UTF_8);
            assertEquals(expected.length + 1, rc.intValue());

            Pointer written = outSlot.getPointer().getPointer(0);
            assertNotNull(written);
            long peer = UnidbgPointer.nativeValue(written);
            assertTrue(hook.isTracked(peer));
            assertEquals(1, hook.trackedCount());

            byte[] actual = written.getByteArray(0, expected.length);
            assertArrayEquals(expected, actual);
            assertEquals(0, written.getByte(expected.length));

            CapturedEvent ev = findLast(sink.events, "linux_security", "getfilecon");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("result=" + (expected.length + 1) + ",length=" + expected.length,
                    String.valueOf(ev.value));
            assertFalse("sidecar must not contain raw context",
                    String.valueOf(ev.value).contains(CONTEXT));
            assertFalse("sidecar must not contain path",
                    String.valueOf(ev.value).contains(PATH));
            assertFalse(ev.note != null && ev.note.contains(CONTEXT));
            assertEquals(1, countApi(sink.events, "getfilecon"));
            assertEquals(0, countApi(sink.events, "freecon"));
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runFreeconLifecycle(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MAP_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            SelinuxGetconHook hook = new SelinuxGetconHook(emulator);

            MemoryBlock pathBlock = writeCString(emulator, PATH, blocks);
            MemoryBlock outSlot = emulator.getMemory().malloc(emulator.getPointerSize(), true);
            blocks.add(outSlot);
            assertEquals(Integer.valueOf(CONTEXT.getBytes(StandardCharsets.UTF_8).length + 1),
                    hook.tryGetfilecon(emulator, pathBlock.getPointer(), outSlot.getPointer()));
            Pointer ctx = outSlot.getPointer().getPointer(0);
            long peer = UnidbgPointer.nativeValue(ctx);
            assertTrue(hook.isTracked(peer));

            assertTrue(hook.tryFreecon(ctx));
            assertFalse(hook.isTracked(peer));
            assertEquals(0, hook.trackedCount());
            assertEquals(0, countApi(sink.events, "freecon"));

            assertFalse(hook.tryFreecon(ctx));
            MemoryBlock foreign = emulator.getMemory().malloc(8, true);
            blocks.add(foreign);
            assertFalse(hook.tryFreecon(foreign.getPointer()));
            assertFalse(hook.tryFreecon(null));
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runAbsenceAndUnconfigured(boolean is64Bit) throws Exception {
        for (String json : new String[]{ABSENT_JSON, ABSENT_PROC_JSON, SELINUX_ONLY_JSON}) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
            AndroidEmulator emulator = null;
            CapturingSink sink = new CapturingSink();
            List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
            try {
                emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                TraceEnvironmentEventSink.register(emulator, sink);
                assertNull(SelinuxGetconHook.configuredFileContext(emulator, PATH));

                SelinuxGetconHook hook = new SelinuxGetconHook(emulator);
                MemoryBlock pathBlock = writeCString(emulator, PATH, blocks);
                MemoryBlock outSlot = emulator.getMemory().malloc(emulator.getPointerSize(), true);
                blocks.add(outSlot);
                assertNull(hook.tryGetfilecon(emulator, pathBlock.getPointer(), outSlot.getPointer()));
                for (CapturedEvent e : sink.events) {
                    assertFalse("unexpected getfilecon event",
                            "linux_security".equals(e.kind) && "getfilecon".equals(e.api));
                }
            } finally {
                cleanup(emulator, sink, blocks);
            }
        }

        // map present but path unconfigured → no intercept
        TraceEnvironmentConfig mapConfig = TraceEnvironmentConfig.parse(MAP_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(mapConfig)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            SelinuxGetconHook hook = new SelinuxGetconHook(emulator);
            MemoryBlock pathBlock = writeCString(emulator, UNCONFIGURED_PATH, blocks);
            MemoryBlock outSlot = emulator.getMemory().malloc(emulator.getPointerSize(), true);
            blocks.add(outSlot);
            assertNull(hook.tryGetfilecon(emulator, pathBlock.getPointer(), outSlot.getPointer()));
            for (CapturedEvent e : sink.events) {
                assertFalse("getfilecon on unconfigured path",
                        "linux_security".equals(e.kind) && "getfilecon".equals(e.api));
            }
            assertEquals(0, hook.trackedCount());
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runNullAndExactMatch(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MAP_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            SelinuxGetconHook hook = new SelinuxGetconHook(emulator);
            long old = 0x4000L;

            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libc.so", "getfilecon", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "getpeercon", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "setcon", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "fgetfilecon", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "security_getenforce", old));

            long getfileconAddr = hook.hook(emulator.getSvcMemory(),
                    SelinuxGetconHook.LIBRARY, SelinuxGetconHook.GETFILECON, old);
            assertTrue(getfileconAddr != 0L);
            long lgetfileconAddr = hook.hook(emulator.getSvcMemory(),
                    SelinuxGetconHook.LIBRARY, SelinuxGetconHook.LGETFILECON, old);
            assertTrue(lgetfileconAddr != 0L);
            long freeconAddr = hook.hook(emulator.getSvcMemory(),
                    SelinuxGetconHook.LIBRARY, SelinuxGetconHook.FREECON, old);
            assertTrue(freeconAddr != 0L);

            MemoryBlock pathBlock = writeCString(emulator, PATH, blocks);
            MemoryBlock outSlot = emulator.getMemory().malloc(emulator.getPointerSize(), true);
            blocks.add(outSlot);

            // null path or null out → not handled
            assertNull(hook.tryGetfilecon(emulator, null, outSlot.getPointer()));
            assertNull(hook.tryGetfilecon(emulator, pathBlock.getPointer(), null));
            for (CapturedEvent e : sink.events) {
                assertFalse("getfilecon event on null args",
                        "linux_security".equals(e.kind) && "getfilecon".equals(e.api));
            }
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runIsolation(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig configA = TraceEnvironmentConfig.parse(MAP_JSON);
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

            MemoryBlock pathA = writeCString(emulatorA, PATH, blocksA);
            MemoryBlock outA = emulatorA.getMemory().malloc(emulatorA.getPointerSize(), true);
            blocksA.add(outA);
            assertNotNull(hookA.tryGetfilecon(emulatorA, pathA.getPointer(), outA.getPointer()));
            CapturedEvent evA = findLast(sinkA.events, "linux_security", "getfilecon");
            assertNotNull(evA);
            assertFalse(String.valueOf(evA.value).contains(CONTEXT));
            assertFalse(String.valueOf(evA.value).contains(PATH));
            assertEquals(1, countApi(sinkA.events, "getfilecon"));

            MemoryBlock pathB = writeCString(emulatorB, PATH, blocksB);
            MemoryBlock outB = emulatorB.getMemory().malloc(emulatorB.getPointerSize(), true);
            blocksB.add(outB);
            assertNull(hookB.tryGetfilecon(emulatorB, pathB.getPointer(), outB.getPointer()));
            for (CapturedEvent e : sinkB.events) {
                assertFalse("leak getfilecon to B",
                        "linux_security".equals(e.kind) && "getfilecon".equals(e.api));
            }
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

    private static MemoryBlock writeCString(AndroidEmulator emulator, String text,
                                            List<MemoryBlock> blocks) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        MemoryBlock block = emulator.getMemory().malloc(bytes.length + 1, true);
        blocks.add(block);
        block.getPointer().write(0, bytes, 0, bytes.length);
        block.getPointer().setByte(bytes.length, (byte) 0);
        return block;
    }

    private static void cleanup(AndroidEmulator emulator, CapturingSink sink,
                                List<MemoryBlock> blocks) throws Exception {
        if (emulator != null) {
            TraceEnvironmentEventSink.unregister(emulator, sink);
        }
        freeAll(blocks);
        if (emulator != null) {
            emulator.close();
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

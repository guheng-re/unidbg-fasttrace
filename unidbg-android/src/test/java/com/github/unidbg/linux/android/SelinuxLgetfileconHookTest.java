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
 * Focused ARM32/ARM64 tests for {@code libselinux.so!lgetfilecon}/{@code freecon}
 * backed by explicit {@code linux.proc.fileSelinuxContexts} (literal path map; no symlink
 * resolution).
 */
public class SelinuxLgetfileconHookTest {

    private static final String PATH = "/data/data/com.demo.app";
    private static final String CONTEXT = "u:object_r:app_data_file:s0";
    private static final String UNCONFIGURED_PATH = "/data/local/tmp/x";

    private static final String MAP_JSON = "{"
            + "\"linux\":{\"proc\":{\"fileSelinuxContexts\":{"
            + "\"" + PATH + "\":\"" + CONTEXT + "\""
            + "}}}}";
    private static final String ABSENT_PROC_JSON =
            "{\"linux\":{\"proc\":{\"state\":\"S\"}}}";
    private static final String ABSENT_JSON =
            "{\"android\":{\"packageName\":\"com.demo.app\"}}";
    private static final String SELINUX_ONLY_JSON = "{"
            + "\"linux\":{\"proc\":{\"selinuxContext\":\"u:r:untrusted_app:s0\"}}"
            + "}";

    @Test
    public void testLgetfileconBytesAndResultArm32() throws Exception {
        runSuccess(false);
    }

    @Test
    public void testLgetfileconBytesAndResultArm64() throws Exception {
        runSuccess(true);
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
    public void testNoRawPathOrContextInSidecarArm32() throws Exception {
        runNoRawSidecar(false);
    }

    @Test
    public void testNoRawPathOrContextInSidecarArm64() throws Exception {
        runNoRawSidecar(true);
    }

    private static void runSuccess(boolean is64Bit) throws Exception {
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

            Integer rc = hook.tryLgetfilecon(emulator, pathBlock.getPointer(), outSlot.getPointer());
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

            CapturedEvent ev = findLast(sink.events, "linux_security", "lgetfilecon");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("result=" + (expected.length + 1) + ",length=" + expected.length,
                    String.valueOf(ev.value));
            assertFalse("sidecar must not contain raw context",
                    String.valueOf(ev.value).contains(CONTEXT));
            assertFalse("sidecar must not contain path",
                    String.valueOf(ev.value).contains(PATH));
            assertFalse(ev.note != null && ev.note.contains(CONTEXT));
            assertEquals(1, countApi(sink.events, "lgetfilecon"));
            assertEquals(0, countApi(sink.events, "getfilecon"));
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
                    hook.tryLgetfilecon(emulator, pathBlock.getPointer(), outSlot.getPointer()));
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
                assertNull(hook.tryLgetfilecon(emulator, pathBlock.getPointer(), outSlot.getPointer()));
                for (CapturedEvent e : sink.events) {
                    assertFalse("unexpected lgetfilecon event",
                            "linux_security".equals(e.kind) && "lgetfilecon".equals(e.api));
                }
            } finally {
                cleanup(emulator, sink, blocks);
            }
        }

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
            assertNull(hook.tryLgetfilecon(emulator, pathBlock.getPointer(), outSlot.getPointer()));
            for (CapturedEvent e : sink.events) {
                assertFalse("lgetfilecon on unconfigured path",
                        "linux_security".equals(e.kind) && "lgetfilecon".equals(e.api));
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

            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libc.so", "lgetfilecon", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "fgetfilecon", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "getpeercon", old));
            assertEquals(0L, hook.hook(emulator.getSvcMemory(), "libselinux.so",
                    "setcon", old));

            long lgetAddr = hook.hook(emulator.getSvcMemory(),
                    SelinuxGetconHook.LIBRARY, SelinuxGetconHook.LGETFILECON, old);
            assertTrue(lgetAddr != 0L);
            long freeconAddr = hook.hook(emulator.getSvcMemory(),
                    SelinuxGetconHook.LIBRARY, SelinuxGetconHook.FREECON, old);
            assertTrue(freeconAddr != 0L);

            MemoryBlock pathBlock = writeCString(emulator, PATH, blocks);
            MemoryBlock outSlot = emulator.getMemory().malloc(emulator.getPointerSize(), true);
            blocks.add(outSlot);

            assertNull(hook.tryLgetfilecon(emulator, null, outSlot.getPointer()));
            assertNull(hook.tryLgetfilecon(emulator, pathBlock.getPointer(), null));
            for (CapturedEvent e : sink.events) {
                assertFalse("lgetfilecon event on null args",
                        "linux_security".equals(e.kind) && "lgetfilecon".equals(e.api));
            }
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runNoRawSidecar(boolean is64Bit) throws Exception {
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
            assertNotNull(hook.tryLgetfilecon(emulator, pathBlock.getPointer(), outSlot.getPointer()));

            CapturedEvent ev = findLast(sink.events, "linux_security", "lgetfilecon");
            assertNotNull(ev);
            String value = String.valueOf(ev.value);
            assertFalse(value.contains(CONTEXT));
            assertFalse(value.contains(PATH));
            assertFalse(value.contains("/data"));
            if (ev.note != null) {
                assertFalse(ev.note.contains(CONTEXT));
                assertFalse(ev.note.contains(PATH));
            }
            // freecon must not emit
            Pointer ctx = outSlot.getPointer().getPointer(0);
            assertTrue(hook.tryFreecon(ctx));
            assertEquals(0, countApi(sink.events, "freecon"));
        } finally {
            cleanup(emulator, sink, blocks);
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

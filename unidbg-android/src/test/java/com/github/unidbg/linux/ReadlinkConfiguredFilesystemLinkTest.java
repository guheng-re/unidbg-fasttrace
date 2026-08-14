package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.file.ByteArrayFileIO;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.unix.UnixEmulator;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Shared {@code UnixSyscallHandler#readlink} wiring for {@code filesystem.links}.
 * freeAll before emulator.close.
 */
public class ReadlinkConfiguredFilesystemLinkTest {

    private static final int CONFIG_PID = 4242;

    private static final String LINKS_JSON = "{"
            + "\"process\":{\"pid\":" + CONFIG_PID + "},"
            + "\"filesystem\":{\"links\":{"
            + "\"/proc/self/exe\":\"/system/bin/app_process64\","
            + "\"/data/local/tmp/rel\":\"../cache/foo\","
            + "\"/data/utf8\":\"中文目标\","
            + "\"/proc/self/fd/0\":\"/configured/stdin\""
            + "}}}";

    @Test
    public void testAbsoluteRelativeUtf8TruncationAndNoNul() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LINKS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            assertEquals(CONFIG_PID, emulator.getPid());

            // absolute target, full buffer — no trailing NUL
            byte[] absExpected = "/system/bin/app_process64".getBytes(StandardCharsets.UTF_8);
            MemoryBlock absBuf = malloc(emulator, blocks, absExpected.length + 8);
            fill(absBuf.getPointer(), absExpected.length + 8, (byte) 0xAB);
            int n = callReadlink(emulator, "/proc/self/exe", absBuf.getPointer(), absExpected.length + 8);
            assertEquals(absExpected.length, n);
            assertArrayEquals(absExpected, absBuf.getPointer().getByteArray(0, n));
            // byte after written range stays sentinel (no NUL written at n)
            assertEquals((byte) 0xAB, absBuf.getPointer().getByte(n));

            // relative target
            byte[] relExpected = "../cache/foo".getBytes(StandardCharsets.UTF_8);
            MemoryBlock relBuf = malloc(emulator, blocks, 64);
            n = callReadlink(emulator, "/data/local/tmp/rel", relBuf.getPointer(), 64);
            assertEquals(relExpected.length, n);
            assertArrayEquals(relExpected, relBuf.getPointer().getByteArray(0, n));

            // UTF-8 by bytes
            byte[] utf8 = "中文目标".getBytes(StandardCharsets.UTF_8);
            assertTrue(utf8.length > "中文目标".length()); // multi-byte
            MemoryBlock uBuf = malloc(emulator, blocks, utf8.length);
            n = callReadlink(emulator, "/data/utf8", uBuf.getPointer(), utf8.length);
            assertEquals(utf8.length, n);
            assertArrayEquals(utf8, uBuf.getPointer().getByteArray(0, n));

            // short buffer truncates by byte count
            MemoryBlock shortBuf = malloc(emulator, blocks, 4);
            fill(shortBuf.getPointer(), 4, (byte) 0);
            n = callReadlink(emulator, "/proc/self/exe", shortBuf.getPointer(), 4);
            assertEquals(4, n);
            assertArrayEquals(Arrays.copyOf(absExpected, 4), shortBuf.getPointer().getByteArray(0, 4));
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testPidSelfAliasAndConfigOverridesFdDynamic() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LINKS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();

            // pid path resolves via /proc/self/ config key
            byte[] expected = "/system/bin/app_process64".getBytes(StandardCharsets.UTF_8);
            MemoryBlock buf = malloc(emulator, blocks, 128);
            int n = callReadlink(emulator, "/proc/" + CONFIG_PID + "/exe", buf.getPointer(), 128);
            assertEquals(expected.length, n);
            assertArrayEquals(expected, buf.getPointer().getByteArray(0, n));

            // config wins over fdMap dynamic for /proc/self/fd/0
            TestARM64SyscallHandler handler =
                    new TestARM64SyscallHandler(emulator.getSvcMemory());
            handler.addFileIO(new ByteArrayFileIO(IOConstants.O_RDONLY, "/dynamic/path", new byte[]{'x'}));
            // fd 0 is first min fd
            MemoryBlock fdBuf = malloc(emulator, blocks, 64);
            n = handler.exposeReadlink(emulator, "/proc/self/fd/0", fdBuf.getPointer(), 64);
            byte[] cfg = "/configured/stdin".getBytes(StandardCharsets.UTF_8);
            assertEquals(cfg.length, n);
            assertArrayEquals(cfg, fdBuf.getPointer().getByteArray(0, n));
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testEmptyAndMissingFallbackToLegacy() throws Exception {
        // explicit empty links: configured but no entries → legacy behavior
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"links\":{}}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(empty).build();
            String path = "/some/path";
            MemoryBlock buf = malloc(emulator, blocks, 64);
            int n = callReadlink(emulator, path, buf.getPointer(), 64);
            // legacy setString includes trailing NUL → length + 1
            assertEquals(path.length() + 1, n);
            assertEquals(path, buf.getPointer().getString(0));
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }

        // not configured
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            String path = "/other";
            MemoryBlock buf = malloc(emulator, blocks, 64);
            int n = callReadlink(emulator, path, buf.getPointer(), 64);
            assertEquals(path.length() + 1, n);
            assertEquals(path, buf.getPointer().getString(0));
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testEinvalAndSingleEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LINKS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            int ret = callReadlink(emulator, "/proc/self/exe", null, 16);
            assertEquals(-1, ret);
            assertEquals(UnixEmulator.EINVAL, emulator.getMemory().getLastErrno());
            assertEquals(0, sink.events.size()); // no success event

            MemoryBlock buf = malloc(emulator, blocks, 64);
            ret = callReadlink(emulator, "/proc/self/exe", buf.getPointer(), 0);
            assertEquals(-1, ret);
            assertEquals(UnixEmulator.EINVAL, emulator.getMemory().getLastErrno());
            assertEquals(0, sink.events.size());

            ret = callReadlink(emulator, "/proc/self/exe", buf.getPointer(), 64);
            assertTrue(ret > 0);
            assertEquals(1, sink.events.size());
            CapturedEvent e = sink.events.get(0);
            assertEquals("filesystem_link", e.kind);
            assertEquals("readlink(\"/proc/self/exe\")", e.api);
            assertEquals("json-config", e.source);
            assertEquals("path=/proc/self/exe,target=/system/bin/app_process64",
                    String.valueOf(e.value));
            assertTrue(e.note.contains("读取配置的符号链接"));
            assertTrue(e.note.contains("/proc/self/exe"));

            // second success → second event (one per successful config hit)
            callReadlink(emulator, "/data/local/tmp/rel", buf.getPointer(), 64);
            assertEquals(2, sink.events.size());
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

    private static int callReadlink(AndroidEmulator emulator, String path, Pointer buf, int bufSize) {
        return new TestARM64SyscallHandler(emulator.getSvcMemory())
                .exposeReadlink(emulator, path, buf, bufSize);
    }

    private static MemoryBlock malloc(AndroidEmulator emulator, List<MemoryBlock> blocks, int size) {
        MemoryBlock block = emulator.getMemory().malloc(size, true);
        blocks.add(block);
        return block;
    }

    private static void fill(Pointer p, int len, byte value) {
        byte[] fill = new byte[len];
        Arrays.fill(fill, value);
        p.write(0, fill, 0, len);
    }

    private static void freeAll(List<MemoryBlock> blocks) {
        for (MemoryBlock block : blocks) {
            try {
                block.free();
            } catch (Exception ignored) {
                // teardown
            }
        }
        blocks.clear();
    }

    private static final class TestARM64SyscallHandler extends ARM64SyscallHandler {
        TestARM64SyscallHandler(SvcMemory svcMemory) {
            super(svcMemory);
        }

        int exposeReadlink(Emulator<?> emulator, String path, Pointer buf, int bufSize) {
            return readlink(emulator, path, buf, bufSize);
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

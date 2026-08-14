package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.EmulatorBuilder;
import com.github.unidbg.arm.ARMEmulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.file.ByteArrayFileIO;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.unix.UnixEmulator;
import com.sun.jna.Pointer;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Focused ARM32/ARM64 tests for read-only {@code process.supplementaryGids}:
 * {@code getgroups32}/{@code getgroups} and {@code /proc/self|pid/status} Groups.
 * Uses {@link Unicorn2Factory}{@code (true)} and real {@code hook(EXCP_SWI, swi=0)}.
 * Does not exercise {@code setgroups} or ARM32 NR 80. Each helper owns one emulator and
 * closes it in {@code try/finally}: unregister sink, free that helper's {@code MemoryBlock}s,
 * then {@code emulator.close()}.
 */
public class AndroidSupplementaryGroupsConfigTest {

    private static final int CONFIG_PID = 12345;
    private static final int GID_A = 3000;
    private static final int GID_B = 4001;

    /** Linux arm {@code __NR_getgroups32}. */
    private static final int NR_GETGROUPS_ARM32 = 205;
    /** Linux aarch64 {@code __NR_getgroups}. */
    private static final int NR_GETGROUPS_ARM64 = 158;

    private static final String CONFIGURED_JSON = "{"
            + "\"process\":{"
            + "\"pid\":" + CONFIG_PID + ","
            + "\"gid\":1000,"
            + "\"egid\":1000,"
            + "\"supplementaryGids\":[" + GID_A + "," + GID_B + "]"
            + "},"
            + "\"linux\":{\"proc\":{}}"
            + "}";

    private static final String EMPTY_JSON = "{"
            + "\"process\":{"
            + "\"pid\":" + CONFIG_PID + ","
            + "\"supplementaryGids\":[]"
            + "},"
            + "\"linux\":{\"proc\":{}}"
            + "}";

    private static final String MISSING_JSON = "{"
            + "\"process\":{"
            + "\"pid\":" + CONFIG_PID + ","
            + "\"gid\":1000,"
            + "\"egid\":1000"
            + "},"
            + "\"linux\":{\"proc\":{}}"
            + "}";

    @Test
    public void testInvalidParseRejected() {
        assertInvalid("{\"process\":{\"supplementaryGids\":null}}", "process.supplementaryGids");
        assertInvalid("{\"process\":{\"supplementaryGids\":1}}", "process.supplementaryGids");
        assertInvalid("{\"process\":{\"supplementaryGids\":{}}}", "process.supplementaryGids");
        assertInvalid("{\"process\":{\"supplementaryGids\":\"1000\"}}", "process.supplementaryGids");
        assertInvalid("{\"process\":{\"supplementaryGids\":true}}", "process.supplementaryGids");
        assertInvalid("{\"process\":{\"supplementaryGids\":[1.5]}}", "process.supplementaryGids[0]");
        assertInvalid("{\"process\":{\"supplementaryGids\":[\"1000\"]}}", "process.supplementaryGids[0]");
        assertInvalid("{\"process\":{\"supplementaryGids\":[true]}}", "process.supplementaryGids[0]");
        assertInvalid("{\"process\":{\"supplementaryGids\":[null]}}", "process.supplementaryGids[0]");
        assertInvalid("{\"process\":{\"supplementaryGids\":[-1]}}", "process.supplementaryGids[0]");
        assertInvalid("{\"process\":{\"supplementaryGids\":[2147483648]}}", "process.supplementaryGids[0]");
        assertInvalid("{\"process\":{\"supplementaryGids\":[1,1]}}", "process.supplementaryGids[1]");
        assertInvalid("{\"process\":{\"supplementaryGids\":[0,2,0]}}", "process.supplementaryGids[2]");

        TraceEnvironmentConfig ok = TraceEnvironmentConfig.parse(
                "{\"process\":{\"supplementaryGids\":[0," + Integer.MAX_VALUE + "]}}");
        assertTrue(ok.isSupplementaryGidsConfigured());
        assertEquals(Arrays.asList(Integer.valueOf(0), Integer.valueOf(Integer.MAX_VALUE)),
                ok.getSupplementaryGids());
        List<Integer> snapshot = ok.getSupplementaryGids();
        try {
            snapshot.add(Integer.valueOf(9));
        } catch (UnsupportedOperationException ignored) {
            // unmodifiable
        }
        assertEquals(2, ok.getSupplementaryGids().size());

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"process\":{\"supplementaryGids\":[]}}");
        assertTrue(empty.isSupplementaryGidsConfigured());
        assertTrue(empty.getSupplementaryGids().isEmpty());

        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{\"process\":{\"pid\":9}}");
        assertFalse(missing.isSupplementaryGidsConfigured());
        assertTrue(missing.getSupplementaryGids().isEmpty());

        TraceEnvironmentConfig noProcess = TraceEnvironmentConfig.parse("{}");
        assertFalse(noProcess.isSupplementaryGidsConfigured());
        assertTrue(noProcess.getSupplementaryGids().isEmpty());
    }

    @Test
    public void testConfiguredSyscallAndStatus32() throws Exception {
        runConfiguredSyscallAndStatus(false);
    }

    @Test
    public void testConfiguredSyscallAndStatus64() throws Exception {
        runConfiguredSyscallAndStatus(true);
    }

    @Test
    public void testEmptyGroups32() throws Exception {
        runEmptyGroups(false);
    }

    @Test
    public void testEmptyGroups64() throws Exception {
        runEmptyGroups(true);
    }

    @Test
    public void testUnconfigured32() throws Exception {
        runUnconfigured(false);
    }

    @Test
    public void testUnconfigured64() throws Exception {
        runUnconfigured(true);
    }

    private static void runConfiguredSyscallAndStatus(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIGURED_JSON);
        assertTrue(config.isSupplementaryGidsConfigured());
        assertEquals(Arrays.asList(Integer.valueOf(GID_A), Integer.valueOf(GID_B)),
                config.getSupplementaryGids());
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            int nr = is64Bit ? NR_GETGROUPS_ARM64 : NR_GETGROUPS_ARM32;

            int queried = invokeGetgroups(emulator, nr, 0, null);
            assertEquals(2, queried);
            assertGetgroupsEvent(findLast(sink.events, "process_identity", "getgroups"),
                    2, 0);

            MemoryBlock writeBlock = malloc(emulator, blocks, 16);
            Pointer list = writeBlock.getPointer();
            byte[] writeSentinel = new byte[16];
            Arrays.fill(writeSentinel, (byte) 0xaa);
            list.write(0, writeSentinel, 0, writeSentinel.length);
            int written = invokeGetgroups(emulator, nr, 4, list);
            assertEquals(2, written);
            assertEquals(GID_A, list.getInt(0));
            assertEquals(GID_B, list.getInt(4));
            byte[] rest = list.getByteArray(8, 8);
            for (int i = 0; i < rest.length; i++) {
                assertEquals((byte) 0xaa, rest[i]);
            }
            assertGetgroupsEvent(findLast(sink.events, "process_identity", "getgroups"),
                    2, 4);
            assertEquals(2, countApi(sink.events, "getgroups"));

            MemoryBlock einvalBlock = malloc(emulator, blocks, 8);
            Pointer einvalList = einvalBlock.getPointer();
            byte[] einvalSentinel = new byte[8];
            Arrays.fill(einvalSentinel, (byte) 0xcc);
            einvalList.write(0, einvalSentinel, 0, einvalSentinel.length);
            assertEquals(-1, invokeGetgroups(emulator, nr, 1, einvalList));
            assertEquals(UnixEmulator.EINVAL, emulator.getMemory().getLastErrno());
            assertArrayEqualsBytes(einvalSentinel, einvalList.getByteArray(0, 8));
            assertEquals(-1, invokeGetgroups(emulator, nr, -1, einvalList));
            assertEquals(UnixEmulator.EINVAL, emulator.getMemory().getLastErrno());
            assertArrayEqualsBytes(einvalSentinel, einvalList.getByteArray(0, 8));

            assertEquals(-1, invokeGetgroups(emulator, nr, 2, null));
            assertEquals(UnixEmulator.EFAULT, emulator.getMemory().getLastErrno());
            assertEquals(2, countApi(sink.events, "getgroups"));

            String selfStatus = readOpenText(emulator, blocks, "/proc/self/status");
            String pidStatus = readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/status");
            assertGroupsLine(selfStatus);
            assertGroupsLine(pidStatus);
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runEmptyGroups(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(EMPTY_JSON);
        assertTrue(config.isSupplementaryGidsConfigured());
        assertTrue(config.getSupplementaryGids().isEmpty());
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            int nr = is64Bit ? NR_GETGROUPS_ARM64 : NR_GETGROUPS_ARM32;

            assertEquals(0, invokeGetgroups(emulator, nr, 0, null));
            assertGetgroupsEvent(findLast(sink.events, "process_identity", "getgroups"),
                    0, 0);

            MemoryBlock block = malloc(emulator, blocks, 8);
            Pointer list = block.getPointer();
            byte[] sentinel = new byte[]{(byte) 0xbb, (byte) 0xbb, (byte) 0xbb, (byte) 0xbb,
                    (byte) 0xbb, (byte) 0xbb, (byte) 0xbb, (byte) 0xbb};
            list.write(0, sentinel, 0, sentinel.length);
            assertEquals(0, invokeGetgroups(emulator, nr, 2, list));
            assertArrayEqualsBytes(sentinel, list.getByteArray(0, 8));
            assertGetgroupsEvent(findLast(sink.events, "process_identity", "getgroups"),
                    0, 2);

            assertEquals(0, invokeGetgroups(emulator, nr, 2, null));
            assertGetgroupsEvent(findLast(sink.events, "process_identity", "getgroups"),
                    0, 2);
            assertEquals(3, countApi(sink.events, "getgroups"));

            String selfStatus = readOpenText(emulator, blocks, "/proc/self/status");
            String pidStatus = readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/status");
            assertTrue(selfStatus.contains("Groups:\t\n"));
            assertTrue(pidStatus.contains("Groups:\t\n"));
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static void runUnconfigured(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MISSING_JSON);
        assertFalse(config.isSupplementaryGidsConfigured());
        CapturingSink sink = new CapturingSink();
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        AndroidEmulator emulator = null;
        try {
            emulator = emulatorBuilder(is64Bit).setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            int nr = is64Bit ? NR_GETGROUPS_ARM64 : NR_GETGROUPS_ARM32;
            MemoryBlock block = malloc(emulator, blocks, 8);
            Pointer list = block.getPointer();
            byte[] sentinel = new byte[8];
            Arrays.fill(sentinel, (byte) 0xdd);
            list.write(0, sentinel, 0, sentinel.length);
            int ret = invokeGetgroups(emulator, nr, 2, list);
            if (!is64Bit) {
                assertEquals(0, ret);
            }
            assertArrayEqualsBytes(sentinel, list.getByteArray(0, 8));
            assertEquals(0, countApi(sink.events, "getgroups"));

            String selfStatus = readOpenText(emulator, blocks, "/proc/self/status");
            String pidStatus = readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/status");
            assertFalse(selfStatus.contains("Groups:"));
            assertFalse(pidStatus.contains("Groups:"));
            assertTrue(selfStatus.contains("Gid:\t"));
        } finally {
            cleanup(emulator, sink, blocks);
        }
    }

    private static int invokeGetgroups(AndroidEmulator emulator, int nr, int size, Pointer list) {
        Backend backend = emulator.getBackend();
        long listPeer = list == null ? 0L : ((UnidbgPointer) list).peer;
        if (emulator.is32Bit()) {
            backend.reg_write(ArmConst.UC_ARM_REG_R0, size);
            backend.reg_write(ArmConst.UC_ARM_REG_R1, (int) listPeer);
            backend.reg_write(ArmConst.UC_ARM_REG_R7, nr);
            backend.reg_write(ArmConst.UC_ARM_REG_R5, 0);
        } else {
            backend.reg_write(Arm64Const.UC_ARM64_REG_X0, size);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X1, listPeer);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X8, nr);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X16, 0L);
        }
        AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();
        handler.hook(backend, ARMEmulator.EXCP_SWI, 0, emulator);
        if (emulator.is32Bit()) {
            return backend.reg_read(ArmConst.UC_ARM_REG_R0).intValue();
        }
        return backend.reg_read(Arm64Const.UC_ARM64_REG_X0).intValue();
    }

    private static void assertGetgroupsEvent(CapturedEvent ev, int count, int requestedSize) {
        assertNotNull(ev);
        assertEquals("process_identity", ev.kind);
        assertEquals("getgroups", ev.api);
        assertEquals("json-config", ev.source);
        String value = String.valueOf(ev.value);
        assertEquals("count=" + count + ",requestedSize=" + requestedSize, value);
        assertFalse(value.contains(Integer.toString(GID_A)));
        assertFalse(value.contains(Integer.toString(GID_B)));
        assertNotNull(ev.note);
        assertTrue("note should be Chinese and mention getgroups, was: " + ev.note,
                ev.note.contains("读取") && ev.note.contains("getgroups"));
        assertFalse(ev.note.contains(Integer.toString(GID_A)));
        assertFalse(ev.note.contains(Integer.toString(GID_B)));
    }

    private static void assertGroupsLine(String statusText) {
        assertTrue(statusText.contains("Groups:\t" + GID_A + " " + GID_B + "\n"));
        int gidIdx = statusText.indexOf("Gid:\t");
        int groupsIdx = statusText.indexOf("Groups:\t");
        int threadsIdx = statusText.indexOf("Threads:\t");
        assertTrue(gidIdx >= 0 && groupsIdx > gidIdx && threadsIdx > groupsIdx);
    }

    private static String readOpenText(AndroidEmulator emulator, List<MemoryBlock> blocks, String path)
            throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        assertNotNull(result);
        assertTrue("open failed for " + path, result.isSuccess());
        assertNotNull(result.io);
        assertTrue(result.io instanceof ByteArrayFileIO);
        MemoryBlock block = emulator.getMemory().malloc(8192, true);
        blocks.add(block);
        Pointer ptr = block.getPointer();
        int n = result.io.read(emulator.getBackend(), ptr, 8192);
        assertTrue(n > 0);
        return new String(ptr.getByteArray(0, n), StandardCharsets.UTF_8);
    }

    private static MemoryBlock malloc(AndroidEmulator emulator, List<MemoryBlock> blocks, int size) {
        MemoryBlock block = emulator.getMemory().malloc(size, true);
        blocks.add(block);
        return block;
    }

    private static void cleanup(AndroidEmulator emulator, CapturingSink sink, List<MemoryBlock> blocks)
            throws Exception {
        try {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
        } finally {
            try {
                if (blocks != null) {
                    for (int i = blocks.size() - 1; i >= 0; i--) {
                        blocks.get(i).free();
                    }
                    blocks.clear();
                }
            } finally {
                if (emulator != null) {
                    emulator.close();
                }
            }
        }
    }

    private static void assertInvalid(String json, String expectedPath) {
        try {
            TraceEnvironmentConfig.parse(json);
            fail("expected IllegalArgumentException containing path: " + expectedPath
                    + " for json: " + json);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue("message missing path " + expectedPath + ", was: " + message,
                    message != null && message.contains(expectedPath));
        }
    }

    private static void assertArrayEqualsBytes(byte[] expected, byte[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
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

    private static EmulatorBuilder<AndroidEmulator> emulatorBuilder(boolean is64Bit) {
        return (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                .addBackendFactory(new Unicorn2Factory(true));
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

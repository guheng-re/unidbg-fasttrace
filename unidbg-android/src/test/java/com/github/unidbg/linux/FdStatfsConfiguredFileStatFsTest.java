package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.file.DirectoryFileIO;
import com.github.unidbg.linux.struct.StatFS32;
import com.github.unidbg.linux.struct.StatFS64;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.pointer.UnidbgStructure;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.unix.UnixEmulator;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * FD {@code fstatfs}/{@code fstatfs64} wiring: after successful {@code FileIO.statfs},
 * apply {@code filesystem.statfs} using {@code FileIO.getPath()} longest-mount overlay.
 * Opens virtual {@code /proc/self/fd} via the test handler {@code open} — no host filesystem.
 */
public class FdStatfsConfiguredFileStatFsTest {

    private static final String PATH_FD = "/proc/self/fd";
    /** resolve() only accepts the trailing-slash form for task dir. */
    private static final String PATH_TASK = "/proc/self/task/";

    /** DirectoryFileIO.statfs defaults (baseline without config overlay). */
    private static final int DEFAULT_TYPE = 0xef53;
    private static final int DEFAULT_BSIZE = 0x1000;
    private static final long DEFAULT_BLOCKS = 0x3235afL;
    private static final long DEFAULT_BFREE = 0x2b5763L;
    private static final long DEFAULT_BAVAIL = 0x2b5763L;
    private static final long DEFAULT_FILES = 0xcccb0L;
    private static final long DEFAULT_FFREE = 0xcbd2eL;
    private static final int[] DEFAULT_FSID = new int[]{0xd3609fe8, 0x4970d6b};
    private static final int DEFAULT_NAMELEN = 0xff;
    private static final int DEFAULT_FRSIZE = 0x1000;
    private static final int DEFAULT_FLAGS = 0x426;

    /**
     * {@code /proc/self/fd}: full field overlay.
     * {@code /proc/self}: partial (blocks only) — longest for {@code /proc/self/task}.
     * {@code /}: root fallback (not used for /proc/self/* when longer mounts exist).
     */
    private static final String CONFIG_JSON = "{"
            + "\"filesystem\":{\"statfs\":{"
            + "\"/\":{\"type\":1,\"blocks\":11},"
            + "\"/proc/self\":{\"blocks\":77},"
            + "\"/proc/self/fd\":{"
            + "\"type\":61267,"
            + "\"blockSize\":4096,"
            + "\"blocks\":1000000,"
            + "\"blocksFree\":500000,"
            + "\"blocksAvailable\":450000,"
            + "\"files\":200000,"
            + "\"filesFree\":150000,"
            + "\"fsid\":[1,2],"
            + "\"nameLength\":255,"
            + "\"fragmentSize\":4096,"
            + "\"flags\":1"
            + "}"
            + "}}"
            + "}";

    @Test
    public void testFstatfsConfiguredOverlay32() throws Exception {
        runConfiguredOverlay(false);
    }

    @Test
    public void testFstatfsConfiguredOverlay64() throws Exception {
        runConfiguredOverlay(true);
    }

    @Test
    public void testFstatfsUnconfiguredKeepsDefaults32() throws Exception {
        runUnconfiguredDefaults(false);
    }

    @Test
    public void testFstatfsUnconfiguredKeepsDefaults64() throws Exception {
        runUnconfiguredDefaults(true);
    }

    @Test
    public void testFstatfsInvalidFdEbadf32() throws Exception {
        runInvalidFd(false);
    }

    @Test
    public void testFstatfsInvalidFdEbadf64() throws Exception {
        runInvalidFd(true);
    }

    @Test
    public void testArm32SizeMismatchEinvalDoesNotWriteBuffer() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIG_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for32Bit().setEnvironmentConfig(config).build();
            int structSize = UnidbgStructure.calculateSize(StatFS32.class);
            MemoryBlock mem = emulator.getMemory().malloc(structSize, true);
            blocks.add(mem);
            Pointer buf = mem.getPointer();
            byte[] sentinel = new byte[structSize];
            Arrays.fill(sentinel, (byte) 0x5a);
            buf.write(0, sentinel, 0, structSize);

            TestARM32SyscallHandler handler = new TestARM32SyscallHandler(emulator.getSvcMemory());
            Emulator<AndroidFileIO> emu = cast(emulator);
            int fd = handler.open(emu, PATH_FD, IOConstants.O_RDONLY);
            assertTrue(fd >= 0);

            emulator.getMemory().setErrno(0);
            long ret = handler.exposeFstatfs64(emu, fd, structSize - 1, buf);
            assertEquals(-1L, ret);
            assertEquals(UnixEmulator.EINVAL, emulator.getMemory().getLastErrno());
            assertArrayEquals(sentinel, buf.getByteArray(0, structSize));

            emulator.getMemory().setErrno(0);
            ret = handler.exposeFstatfs64(emu, fd, structSize + 1, buf);
            assertEquals(-1L, ret);
            assertEquals(UnixEmulator.EINVAL, emulator.getMemory().getLastErrno());
            assertArrayEquals(sentinel, buf.getByteArray(0, structSize));
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testSingleSidecarEventOnSuccess() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIG_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            int size = UnidbgStructure.calculateSize(StatFS64.class);
            MemoryBlock mem = emulator.getMemory().malloc(size, true);
            blocks.add(mem);
            Pointer buf = mem.getPointer();

            TestARM64SyscallHandler handler = new TestARM64SyscallHandler(emulator.getSvcMemory());
            Emulator<AndroidFileIO> emu = cast(emulator);
            int fd = handler.open(emu, PATH_FD, IOConstants.O_RDONLY);
            assertTrue(fd >= 0);

            long ret = handler.exposeFstatfs64(emu, fd, buf);
            assertEquals(0L, ret);
            assertEquals(1, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("filesystem_statfs", e0.kind);
            assertEquals("fstatfs", e0.api);
            assertEquals("json-config", e0.source);
            String v0 = String.valueOf(e0.value);
            assertTrue(v0.startsWith("mountPoint=/proc/self/fd"));
            assertTrue(v0.contains("type=61267"));
            assertTrue(v0.contains("blocks=1000000"));
            assertTrue(v0.contains("fsid=[1,2]"));
            assertFalse(v0.contains("fd="));
            assertFalse(v0.contains("fd=" + fd));
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

    @Test
    public void testEmptyPathSkipsOverlayKeepsUnderlying() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIG_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            int size = UnidbgStructure.calculateSize(StatFS64.class);
            MemoryBlock mem = emulator.getMemory().malloc(size, true);
            blocks.add(mem);
            Pointer buf = mem.getPointer();

            TestARM64SyscallHandler handler = new TestARM64SyscallHandler(emulator.getSvcMemory());
            int emptyFd = handler.addFileIO(new DirectoryFileIO(IOConstants.O_RDONLY, ""));
            long ret = handler.exposeFstatfs64(cast(emulator), emptyFd, buf);
            assertEquals(0L, ret);
            assertEquals(0, sink.events.size());
            StatFS64 s = new StatFS64(buf);
            s.unpack();
            assertEquals((long) DEFAULT_TYPE, s.f_type);
            assertEquals(DEFAULT_BLOCKS, s.f_blocks);

            int nullFd = handler.addFileIO(new DirectoryFileIO(IOConstants.O_RDONLY, PATH_FD) {
                @Override
                public String getPath() {
                    return null;
                }
            });
            ret = handler.exposeFstatfs64(cast(emulator), nullFd, buf);
            assertEquals(0L, ret);
            assertEquals(0, sink.events.size());
            s = new StatFS64(buf);
            s.unpack();
            assertEquals((long) DEFAULT_TYPE, s.f_type);
            assertEquals(DEFAULT_BLOCKS, s.f_blocks);
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

    private static void runConfiguredOverlay(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIG_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();

            int structSize = is64Bit
                    ? UnidbgStructure.calculateSize(StatFS64.class)
                    : UnidbgStructure.calculateSize(StatFS32.class);
            MemoryBlock mem = emulator.getMemory().malloc(structSize, true);
            blocks.add(mem);
            Pointer buf = mem.getPointer();
            Emulator<AndroidFileIO> emu = cast(emulator);

            if (is64Bit) {
                TestARM64SyscallHandler handler = new TestARM64SyscallHandler(emulator.getSvcMemory());
                int fd = handler.open(emu, PATH_FD, IOConstants.O_RDONLY);
                assertTrue(fd >= 0);
                assertEquals(0L, handler.exposeFstatfs64(emu, fd, buf));
                StatFS64 s = new StatFS64(buf);
                s.unpack();
                assertEquals(61267L, s.f_type);
                assertEquals(4096L, s.f_bsize);
                assertEquals(1000000L, s.f_blocks);
                assertEquals(500000L, s.f_bfree);
                assertEquals(450000L, s.f_bavail);
                assertEquals(200000L, s.f_files);
                assertEquals(150000L, s.f_ffree);
                assertArrayEquals(new int[]{1, 2}, s.f_fsid);
                assertEquals(255L, s.f_namelen);
                assertEquals(4096L, s.f_frsize);
                assertEquals(1L, s.f_flags);

                int taskFd = handler.open(emu, PATH_TASK, IOConstants.O_RDONLY);
                assertTrue(taskFd >= 0);
                assertEquals(0L, handler.exposeFstatfs64(emu, taskFd, buf));
                s = new StatFS64(buf);
                s.unpack();
                assertEquals(77L, s.f_blocks);
                assertEquals((long) DEFAULT_TYPE, s.f_type);
                assertEquals((long) DEFAULT_BSIZE, s.f_bsize);
                assertEquals(DEFAULT_BFREE, s.f_bfree);
                assertEquals(DEFAULT_BAVAIL, s.f_bavail);
                assertEquals(DEFAULT_FILES, s.f_files);
                assertEquals(DEFAULT_FFREE, s.f_ffree);
                assertArrayEquals(DEFAULT_FSID, s.f_fsid);
                assertEquals((long) DEFAULT_NAMELEN, s.f_namelen);
                assertEquals((long) DEFAULT_FRSIZE, s.f_frsize);
                assertEquals((long) DEFAULT_FLAGS, s.f_flags);
            } else {
                TestARM32SyscallHandler handler = new TestARM32SyscallHandler(emulator.getSvcMemory());
                int fd = handler.open(emu, PATH_FD, IOConstants.O_RDONLY);
                assertTrue(fd >= 0);
                assertEquals(0L, handler.exposeFstatfs64(emu, fd, structSize, buf));
                StatFS32 s = new StatFS32(buf);
                s.unpack();
                assertEquals(61267, s.f_type);
                assertEquals(4096, s.f_bsize);
                assertEquals(1000000L, s.f_blocks);
                assertEquals(500000L, s.f_bfree);
                assertEquals(450000L, s.f_bavail);
                assertEquals(200000L, s.f_files);
                assertEquals(150000L, s.f_ffree);
                assertArrayEquals(new int[]{1, 2}, s.f_fsid);
                assertEquals(255, s.f_namelen);
                assertEquals(4096, s.f_frsize);
                assertEquals(1, s.f_flags);

                int taskFd = handler.open(emu, PATH_TASK, IOConstants.O_RDONLY);
                assertTrue(taskFd >= 0);
                assertEquals(0L, handler.exposeFstatfs64(emu, taskFd, structSize, buf));
                s = new StatFS32(buf);
                s.unpack();
                assertEquals(77L, s.f_blocks);
                assertEquals(DEFAULT_TYPE, s.f_type);
                assertEquals(DEFAULT_BSIZE, s.f_bsize);
                assertEquals(DEFAULT_BFREE, s.f_bfree);
                assertEquals(DEFAULT_BAVAIL, s.f_bavail);
                assertEquals(DEFAULT_FILES, s.f_files);
                assertEquals(DEFAULT_FFREE, s.f_ffree);
                assertArrayEquals(DEFAULT_FSID, s.f_fsid);
                assertEquals(DEFAULT_NAMELEN, s.f_namelen);
                assertEquals(DEFAULT_FRSIZE, s.f_frsize);
                assertEquals(DEFAULT_FLAGS, s.f_flags);
            }
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runUnconfiguredDefaults(boolean is64Bit) throws Exception {
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .build();
            int structSize = is64Bit
                    ? UnidbgStructure.calculateSize(StatFS64.class)
                    : UnidbgStructure.calculateSize(StatFS32.class);
            MemoryBlock mem = emulator.getMemory().malloc(structSize, true);
            blocks.add(mem);
            Pointer buf = mem.getPointer();
            Emulator<AndroidFileIO> emu = cast(emulator);

            long ret;
            if (is64Bit) {
                TestARM64SyscallHandler handler = new TestARM64SyscallHandler(emulator.getSvcMemory());
                int fd = handler.open(emu, PATH_FD, IOConstants.O_RDONLY);
                assertTrue(fd >= 0);
                ret = handler.exposeFstatfs64(emu, fd, buf);
                assertEquals(0L, ret);
                StatFS64 s = new StatFS64(buf);
                s.unpack();
                assertEquals((long) DEFAULT_TYPE, s.f_type);
                assertEquals((long) DEFAULT_BSIZE, s.f_bsize);
                assertEquals(DEFAULT_BLOCKS, s.f_blocks);
                assertEquals(DEFAULT_BFREE, s.f_bfree);
                assertEquals(DEFAULT_BAVAIL, s.f_bavail);
                assertEquals(DEFAULT_FILES, s.f_files);
                assertEquals(DEFAULT_FFREE, s.f_ffree);
                assertArrayEquals(DEFAULT_FSID, s.f_fsid);
                assertEquals((long) DEFAULT_NAMELEN, s.f_namelen);
                assertEquals((long) DEFAULT_FRSIZE, s.f_frsize);
                assertEquals((long) DEFAULT_FLAGS, s.f_flags);
            } else {
                TestARM32SyscallHandler handler = new TestARM32SyscallHandler(emulator.getSvcMemory());
                int fd = handler.open(emu, PATH_FD, IOConstants.O_RDONLY);
                assertTrue(fd >= 0);
                ret = handler.exposeFstatfs64(emu, fd, structSize, buf);
                assertEquals(0L, ret);
                StatFS32 s = new StatFS32(buf);
                s.unpack();
                assertEquals(DEFAULT_TYPE, s.f_type);
                assertEquals(DEFAULT_BSIZE, s.f_bsize);
                assertEquals(DEFAULT_BLOCKS, s.f_blocks);
                assertEquals(DEFAULT_BFREE, s.f_bfree);
                assertEquals(DEFAULT_BAVAIL, s.f_bavail);
                assertEquals(DEFAULT_FILES, s.f_files);
                assertEquals(DEFAULT_FFREE, s.f_ffree);
                assertArrayEquals(DEFAULT_FSID, s.f_fsid);
                assertEquals(DEFAULT_NAMELEN, s.f_namelen);
                assertEquals(DEFAULT_FRSIZE, s.f_frsize);
                assertEquals(DEFAULT_FLAGS, s.f_flags);
            }
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runInvalidFd(boolean is64Bit) throws Exception {
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .build();
            int structSize = is64Bit
                    ? UnidbgStructure.calculateSize(StatFS64.class)
                    : UnidbgStructure.calculateSize(StatFS32.class);
            MemoryBlock mem = emulator.getMemory().malloc(structSize, true);
            blocks.add(mem);
            Pointer buf = mem.getPointer();
            byte[] sentinel = new byte[structSize];
            Arrays.fill(sentinel, (byte) 0x3c);
            buf.write(0, sentinel, 0, structSize);
            Emulator<AndroidFileIO> emu = cast(emulator);

            emulator.getMemory().setErrno(0);
            long ret;
            if (is64Bit) {
                TestARM64SyscallHandler handler = new TestARM64SyscallHandler(emulator.getSvcMemory());
                ret = handler.exposeFstatfs64(emu, -1, buf);
            } else {
                TestARM32SyscallHandler handler = new TestARM32SyscallHandler(emulator.getSvcMemory());
                ret = handler.exposeFstatfs64(emu, -1, structSize, buf);
            }
            assertEquals(-1L, ret);
            assertEquals(UnixEmulator.EBADF, emulator.getMemory().getLastErrno());
            assertArrayEquals(sentinel, buf.getByteArray(0, structSize));
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Emulator<AndroidFileIO> cast(AndroidEmulator emulator) {
        return (Emulator<AndroidFileIO>) (Emulator<?>) emulator;
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

    private static final class TestARM32SyscallHandler extends ARM32SyscallHandler {
        TestARM32SyscallHandler(SvcMemory svcMemory) {
            super(svcMemory);
        }

        long exposeFstatfs64(Emulator<AndroidFileIO> emulator, int fd, int size, Pointer buf) {
            return fstatfs64(emulator, fd, size, buf);
        }
    }

    private static final class TestARM64SyscallHandler extends ARM64SyscallHandler {
        TestARM64SyscallHandler(SvcMemory svcMemory) {
            super(svcMemory);
        }

        long exposeFstatfs64(Emulator<AndroidFileIO> emulator, int fd, Pointer buf) {
            return fstatfs64(emulator, fd, buf);
        }
    }

    private static final class CapturedEvent {
        final String kind;
        final String api;
        final Object value;
        final String source;

        CapturedEvent(String kind, String api, Object value, String source) {
            this.kind = kind;
            this.api = api;
            this.value = value;
            this.source = source;
        }
    }

    private static final class CapturingSink implements TraceEnvironmentEventSink {
        final List<CapturedEvent> events = new ArrayList<CapturedEvent>();

        @Override
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source));
        }
    }
}

package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.struct.Stat32;
import com.github.unidbg.linux.struct.Stat64;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.pointer.UnidbgStructure;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Path {@code stat64} wiring: after successful {@code fstat}, apply {@code filesystem.stat} overlay.
 */
public class PathStatConfiguredFileStatTest {

    private static final String PATH = "/proc/trace-stat-test";
    private static final String MISSING = "/proc/trace-stat-missing";

    private static final long DEVICE = 0x54524143L;
    private static final long INODE = 0x54524144L;
    private static final int MODE = 33188;
    private static final int UID = 1000;
    private static final int GID = 1000;
    private static final long SIZE = 1234L;
    private static final int BLOCK_SIZE = 4096;
    private static final long BLOCKS = 8L;
    private static final long ATIME_MS = 1000L;
    private static final long MTIME_MS = 2000L;
    private static final long CTIME_MS = 3000L;

    private static final String CONFIG_JSON = "{"
            + "\"linux\":{\"files\":{\"" + PATH + "\":\"TRACE_STAT_TEST\\n\"}},"
            + "\"filesystem\":{\"stat\":{\"" + PATH + "\":{"
            + "\"device\":" + DEVICE + ","
            + "\"inode\":" + INODE + ","
            + "\"mode\":" + MODE + ","
            + "\"uid\":" + UID + ","
            + "\"gid\":" + GID + ","
            + "\"size\":" + SIZE + ","
            + "\"blockSize\":" + BLOCK_SIZE + ","
            + "\"blocks\":" + BLOCKS + ","
            + "\"atimeMillis\":" + ATIME_MS + ","
            + "\"mtimeMillis\":" + MTIME_MS + ","
            + "\"ctimeMillis\":" + CTIME_MS
            + "}}}"
            + "}";

    @Test
    public void testPathStatConfiguredOverlay32() throws Exception {
        runPathStat(false);
    }

    @Test
    public void testPathStatConfiguredOverlay64() throws Exception {
        runPathStat(true);
    }

    private static void runPathStat(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CONFIG_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();

            int structSize = is64Bit
                    ? UnidbgStructure.calculateSize(Stat64.class)
                    : UnidbgStructure.calculateSize(Stat32.class);
            MemoryBlock mem = emulator.getMemory().malloc(structSize, true);
            blocks.add(mem);
            Pointer statbuf = mem.getPointer();

            int ret = callStat64(emulator, is64Bit, PATH, statbuf);
            assertEquals(0, ret);

            if (is64Bit) {
                Stat64 stat = new Stat64(statbuf);
                stat.unpack();
                assertEquals(DEVICE, stat.st_dev);
                assertEquals(INODE, stat.st_ino);
                assertEquals(MODE, stat.st_mode);
                assertEquals(UID, stat.st_uid);
                assertEquals(GID, stat.st_gid);
                assertEquals(SIZE, stat.st_size);
                assertEquals(BLOCK_SIZE, stat.st_blksize);
                assertEquals(BLOCKS, stat.st_blocks);
                assertEquals(Math.floorDiv(ATIME_MS, 1000L), stat.st_atim.tv_sec);
                assertEquals(Math.floorMod(ATIME_MS, 1000L) * 1000000L, stat.st_atim.tv_nsec);
                assertEquals(Math.floorDiv(MTIME_MS, 1000L), stat.st_mtim.tv_sec);
                assertEquals(Math.floorMod(MTIME_MS, 1000L) * 1000000L, stat.st_mtim.tv_nsec);
                assertEquals(Math.floorDiv(CTIME_MS, 1000L), stat.st_ctim.tv_sec);
                assertEquals(Math.floorMod(CTIME_MS, 1000L) * 1000000L, stat.st_ctim.tv_nsec);
            } else {
                Stat32 stat = new Stat32(statbuf);
                stat.unpack();
                assertEquals(DEVICE, stat.st_dev);
                assertEquals(INODE, stat.st_ino);
                assertEquals(MODE, stat.st_mode);
                assertEquals(UID, stat.st_uid);
                assertEquals(GID, stat.st_gid);
                assertEquals(SIZE, stat.st_size);
                assertEquals(BLOCK_SIZE, stat.st_blksize);
                assertEquals(BLOCKS, stat.st_blocks);
                assertEquals((int) Math.floorDiv(ATIME_MS, 1000L), stat.st_atim.tv_sec);
                assertEquals((int) (Math.floorMod(ATIME_MS, 1000L) * 1000000L), stat.st_atim.tv_nsec);
                assertEquals((int) Math.floorDiv(MTIME_MS, 1000L), stat.st_mtim.tv_sec);
                assertEquals((int) (Math.floorMod(MTIME_MS, 1000L) * 1000000L), stat.st_mtim.tv_nsec);
                assertEquals((int) Math.floorDiv(CTIME_MS, 1000L), stat.st_ctim.tv_sec);
                assertEquals((int) (Math.floorMod(CTIME_MS, 1000L) * 1000000L), stat.st_ctim.tv_nsec);
            }

            // missing path still fails resolve (config overlay must not create presence)
            int missingRet = callStat64(emulator, is64Bit, MISSING, statbuf);
            assertEquals(-1, missingRet);
        } finally {
            for (MemoryBlock block : blocks) {
                try {
                    block.free();
                } catch (Exception ignored) {
                    // teardown
                }
            }
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static int callStat64(AndroidEmulator emulator, boolean is64Bit, String pathname, Pointer statbuf) {
        SvcMemory svcMemory = emulator.getSvcMemory();
        Emulator<AndroidFileIO> emu = (Emulator<AndroidFileIO>) (Emulator<?>) emulator;
        if (is64Bit) {
            return new TestARM64SyscallHandler(svcMemory).exposeStat64(emu, pathname, statbuf);
        }
        return new TestARM32SyscallHandler(svcMemory).exposeStat64(emu, pathname, statbuf);
    }

    private static final class TestARM32SyscallHandler extends ARM32SyscallHandler {
        TestARM32SyscallHandler(SvcMemory svcMemory) {
            super(svcMemory);
        }

        int exposeStat64(Emulator<AndroidFileIO> emulator, String pathname, Pointer statbuf) {
            return stat64(emulator, pathname, statbuf);
        }
    }

    private static final class TestARM64SyscallHandler extends ARM64SyscallHandler {
        TestARM64SyscallHandler(SvcMemory svcMemory) {
            super(svcMemory);
        }

        int exposeStat64(Emulator<AndroidFileIO> emulator, String pathname, Pointer statbuf) {
            return stat64(emulator, pathname, statbuf);
        }
    }
}

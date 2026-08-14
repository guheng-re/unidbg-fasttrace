package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.BaseAndroidFileIO;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.file.ByteArrayFileIO;
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
import static org.junit.Assert.fail;

/**
 * FD {@code fstat} wiring: after successful {@code file.fstat}, apply {@code filesystem.stat} via FileIO path.
 */
public class FdStatConfiguredFileStatTest {

    private static final String PATH = "/proc/trace-fd-stat-test";

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
    public void testFdStatConfiguredOverlay32() throws Exception {
        runFdStat(false);
    }

    @Test
    public void testFdStatConfiguredOverlay64() throws Exception {
        runFdStat(true);
    }

    private static void runFdStat(boolean is64Bit) throws Exception {
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

            SvcMemory svcMemory = emulator.getSvcMemory();
            if (is64Bit) {
                TestARM64SyscallHandler handler = new TestARM64SyscallHandler(svcMemory);
                int fd = handler.addFileIO(new ByteArrayFileIO(IOConstants.O_RDONLY, PATH, new byte[]{'x'}));
                int ret = handler.exposeFstat(emulator, fd, statbuf);
                assertEquals(0, ret);

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

                // FileIO without getPath: default fstat throws; config path logic must not swallow it
                int noPathFd = handler.addFileIO(new BaseAndroidFileIO(IOConstants.O_RDONLY) {
                });
                try {
                    handler.exposeFstat(emulator, noPathFd, statbuf);
                    fail("expected UnsupportedOperationException from default BaseAndroidFileIO.fstat");
                } catch (UnsupportedOperationException expected) {
                    // unchanged default behavior
                }

                assertEquals(-1, handler.exposeFstat(emulator, -1, statbuf));
            } else {
                TestARM32SyscallHandler handler = new TestARM32SyscallHandler(svcMemory);
                int fd = handler.addFileIO(new ByteArrayFileIO(IOConstants.O_RDONLY, PATH, new byte[]{'x'}));
                int ret = handler.exposeFstat(emulator, fd, statbuf);
                assertEquals(0, ret);

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

                int noPathFd = handler.addFileIO(new BaseAndroidFileIO(IOConstants.O_RDONLY) {
                });
                try {
                    handler.exposeFstat(emulator, noPathFd, statbuf);
                    fail("expected UnsupportedOperationException from default BaseAndroidFileIO.fstat");
                } catch (UnsupportedOperationException expected) {
                    // unchanged default behavior
                }

                assertEquals(-1, handler.exposeFstat(emulator, -1, statbuf));
            }
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

    private static final class TestARM32SyscallHandler extends ARM32SyscallHandler {
        TestARM32SyscallHandler(SvcMemory svcMemory) {
            super(svcMemory);
        }

        int exposeFstat(Emulator<?> emulator, int fd, Pointer stat) {
            return fstat(emulator, fd, stat);
        }
    }

    private static final class TestARM64SyscallHandler extends ARM64SyscallHandler {
        TestARM64SyscallHandler(SvcMemory svcMemory) {
            super(svcMemory);
        }

        int exposeFstat(Emulator<?> emulator, int fd, Pointer stat) {
            return fstat(emulator, fd, stat);
        }
    }
}

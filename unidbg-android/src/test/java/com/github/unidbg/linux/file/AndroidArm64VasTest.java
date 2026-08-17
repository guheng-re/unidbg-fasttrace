package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.arm.AndroidArm64Addresses;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.memory.MemoryBlock;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.UnicornConst;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AndroidArm64VasTest {

    @Test
    public void unicorn2Maps39BitAndroidWindows() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit()
                .addBackendFactory(new Unicorn2Factory(true))
                .build();
        try {
            Backend backend = emulator.getBackend();
            long[] alreadyMapped = new long[] {
                    AndroidArm64Addresses.STACK_BASE - 0x1000,
                    AndroidArm64Addresses.SVC_BASE,
                    AndroidArm64Addresses.LR
            };
            for (long page : alreadyMapped) {
                assertEquals(1, backend.mem_read(page, 1).length);
            }
            emulator.getMemory().brk(0);
            emulator.getMemory().brk(AndroidArm64Addresses.HEAP_BASE + emulator.getPageAlign());
            assertEquals(1, backend.mem_read(AndroidArm64Addresses.HEAP_BASE, 1).length);
            long mapped = emulator.getMemory().mmap(emulator.getPageAlign(),
                    UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE).peer;
            assertTrue(mapped >= AndroidArm64Addresses.MMAP_BASE);
            assertEquals(1, backend.mem_read(mapped, 1).length);
        } finally {
            emulator.close();
        }
    }

    @Test
    public void arm64MapsAndBrkUse39BitLayout() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().build();
        try {
            Memory memory = emulator.getMemory();
            assertEquals(AndroidArm64Addresses.STACK_BASE, memory.getStackBase());
            assertEquals(AndroidArm64Addresses.LR, emulator.getReturnAddress());
            long sp = emulator.getBackend().reg_read(Arm64Const.UC_ARM64_REG_SP).longValue();
            assertTrue(sp <= AndroidArm64Addresses.STACK_BASE);
            assertTrue(sp > AndroidArm64Addresses.STACK_BASE - memory.getStackSize());

            long heap = memory.brk(0);
            assertEquals(AndroidArm64Addresses.HEAP_BASE, heap);
            int page = emulator.getPageAlign();
            assertEquals(AndroidArm64Addresses.HEAP_BASE + page, memory.brk(AndroidArm64Addresses.HEAP_BASE + page));

            long mapped = memory.mmap(page, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE).peer;
            assertTrue("mmap=" + Long.toHexString(mapped), mapped >= AndroidArm64Addresses.MMAP_BASE);

            String maps = readMaps(emulator);
            assertTrue(maps, maps.contains("7fe0000000"));
            assertTrue(maps, maps.contains("7fdfb00000") || maps.contains("7fe"));
            assertTrue(maps, maps.contains("7100000000") || Long.toHexString(mapped).startsWith("71"));
            assertFalse(maps, maps.contains("0e500000-"));
            assertFalse(maps, maps.contains("01200000-"));
            for (String line : maps.split("\n")) {
                if (line.isEmpty()) {
                    continue;
                }
                int dash = line.indexOf('-');
                assertTrue(line, dash > 0);
                long start = Long.parseLong(line.substring(0, dash), 16);
                assertTrue(line, start >= 0x7000000000L);
            }
        } finally {
            emulator.close();
        }
    }

    @Test
    public void arm32MapsStayInHistoricalWindow() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for32Bit().build();
        try {
            Memory memory = emulator.getMemory();
            assertEquals(Memory.STACK_BASE, memory.getStackBase());
            int page = emulator.getPageAlign();
            memory.mmap(page, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE);
            String maps = readMaps(emulator);
            assertTrue(maps, maps.contains("e5000000"));
            assertTrue(maps, maps.contains("01200000") || maps.contains("12000000"));
            assertFalse(maps, maps.contains("7fe0000000"));
            assertFalse(maps, maps.contains("7100000000"));
        } finally {
            emulator.close();
        }
    }

    private static String readMaps(AndroidEmulator emulator) {
        FileResult<AndroidFileIO> opened = emulator.getFileSystem().open("/proc/self/maps", IOConstants.O_RDONLY);
        assertTrue(opened.isSuccess());
        MemoryBlock block = emulator.getMemory().malloc(8192, true);
        try {
            int n = opened.io.read(emulator.getBackend(), block.getPointer(), 8192);
            assertTrue(n > 0);
            return new String(block.getPointer().getByteArray(0, n), StandardCharsets.UTF_8);
        } finally {
            block.free();
        }
    }
}

package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Symbol;
import com.github.unidbg.arm.ARMEmulator;
import com.github.unidbg.arm.AndroidArm64Addresses;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.pointer.UnidbgPointer;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.UnicornConst;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * After the 39-bit ARM64 VAS lift, guest C pointers must not be truncated to 32 bits.
 */
public class AndroidArm64PointerWidthAuditTest {

    private static final int NR_MREMAP = 216;

    @Test
    public void dlsymEnvironKeepsFullPeer() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().build();
        try {
            emulator.getMemory().setLibraryResolver(new AndroidResolver(23));
            Symbol environ = emulator.getMemory().dlsym(0, "environ");
            assertNotNull(environ);
            long address = environ.getAddress();
            assertTrue("environ=0x" + Long.toHexString(address), address > 0xffffffffL);
            assertTrue("environ not in 39-bit VAS 0x" + Long.toHexString(address),
                    address >= 0x7000000000L);
            assertEquals(1, emulator.getBackend().mem_read(address, 1).length);
        } finally {
            emulator.close();
        }
    }

    @Test
    public void mremapReturnsFullPeer() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().build();
        try {
            Memory memory = emulator.getMemory();
            int page = emulator.getPageAlign();
            UnidbgPointer old = memory.mmap(page, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE);
            old.setByte(0, (byte) 0x5a);

            Backend backend = emulator.getBackend();
            backend.reg_write(Arm64Const.UC_ARM64_REG_X0, old.peer);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X1, page);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X2, page * 2);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X3, AndroidSyscallHandler.MREMAP_MAYMOVE);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X4, 0L);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X8, NR_MREMAP);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X16, 0L);
            ((AndroidSyscallHandler) emulator.getSyscallHandler())
                    .hook(backend, ARMEmulator.EXCP_SWI, 0, emulator);

            long remapped = backend.reg_read(Arm64Const.UC_ARM64_REG_X0).longValue();
            assertTrue("mremap=0x" + Long.toHexString(remapped), remapped > 0xffffffffL);
            assertTrue("mremap not in 39-bit VAS 0x" + Long.toHexString(remapped),
                    remapped >= AndroidArm64Addresses.MMAP_BASE);
            assertEquals(0x5a, emulator.getBackend().mem_read(remapped, 1)[0] & 0xff);
        } finally {
            emulator.close();
        }
    }
}

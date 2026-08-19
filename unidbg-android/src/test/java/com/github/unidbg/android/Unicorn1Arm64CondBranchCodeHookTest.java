package com.github.unidbg.android;

import junit.framework.TestCase;
import unicorn.Arm64Const;
import unicorn.Unicorn;
import unicorn.UnicornConst;
import unicorn.UnicornException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Same B.cond + code-hook repro against the default Unicorn1 backend.
 */
public class Unicorn1Arm64CondBranchCodeHookTest extends TestCase {

    private static byte[] le(int... words) {
        ByteBuffer buf = ByteBuffer.allocate(words.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int w : words) {
            buf.putInt(w);
        }
        return buf.array();
    }

    private static final byte[] CODE = le(
            0x52800001,
            0x11000421,
            0x7100083f,
            0x54000040,
            0x17fffffd,
            0x52800840,
            0xd503201f
    );

    public void testLowAddressWithCodeHook() {
        runCase(0x10000L, true);
    }

    public void testHighAddressWithCodeHook() {
        runCase(0x7100000000L, true);
    }

    public void testHighAddressWithoutCodeHook() {
        runCase(0x7100000000L, false);
    }

    private void runCase(long base, boolean hook) {
        Unicorn u = new Unicorn(UnicornConst.UC_ARCH_ARM64, UnicornConst.UC_MODE_ARM);
        try {
            u.mem_map(base, 0x1000, UnicornConst.UC_PROT_ALL);
            u.mem_write(base, CODE);
            u.reg_write(Arm64Const.UC_ARM64_REG_SP, base + 0x800);
            final int[] hits = new int[1];
            final long[] last = new long[1];
            if (hook) {
                u.hook_add((unicorn.CodeHook) (uc, address, size, user) -> {
                    hits[0]++;
                    last[0] = address;
                }, base, base + CODE.length, null);
            }
            try {
                u.emu_start(base, base + CODE.length - 4, 0, 0);
            } catch (UnicornException e) {
                long pc = u.reg_read(Arm64Const.UC_ARM64_REG_PC);
                fail(String.format("FETCH at base=0x%x hook=%s pc=0x%x lastHook=0x%x hits=%d err=%s",
                        base, hook, pc, last[0], hits[0], e.getMessage()));
            }
            long x0 = u.reg_read(Arm64Const.UC_ARM64_REG_X0);
            assertEquals("w0 should be 0x42 at base=0x" + Long.toHexString(base), 0x42L, x0);
            if (hook) {
                assertTrue(hits[0] > 0);
            }
        } finally {
            u.closeAll();
        }
    }
}

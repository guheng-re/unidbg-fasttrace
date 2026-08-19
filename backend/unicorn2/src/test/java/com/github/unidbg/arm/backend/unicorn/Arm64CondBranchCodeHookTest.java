package com.github.unidbg.arm.backend.unicorn;

import junit.framework.TestCase;
import unicorn.Arm64Const;
import unicorn.UnicornConst;
import unicorn.UnicornException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Minimal repro for Unicorn ARM64 taken B.cond + UC_HOOK_CODE
 * reporting UC_ERR_FETCH_UNMAPPED while the target page is mapped.
 */
public class Arm64CondBranchCodeHookTest extends TestCase {

    static {
        try {
            org.scijava.nativelib.NativeLoader.loadLibrary("unicorn");
        } catch (IOException ignored) {
        }
    }

    private static byte[] le(int... words) {
        ByteBuffer buf = ByteBuffer.allocate(words.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int w : words) {
            buf.putInt(w);
        }
        return buf.array();
    }

    /**
     * w1=0; loop: w1++; if (w1==2) goto done; goto loop; done: w0=0x42
     */
    private static final byte[] CODE = le(
            0x52800001, // mov w1, #0
            0x11000421, // add w1, w1, #1
            0x7100083f, // cmp w1, #2
            0x54000040, // b.eq #+8 -> done
            0x17fffffd, // b #-12 -> add
            0x52800840, // done: mov w0, #0x42
            0xd503201f  // nop (until)
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
                u.hook_add_new(new CodeHook() {
                    @Override
                    public void hook(Unicorn uc, long address, int size, Object user) {
                        hits[0]++;
                        last[0] = address;
                    }
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
                assertTrue("code hook should have fired", hits[0] > 0);
            }
        } finally {
            u.closeAll();
        }
    }
}

package com.github.unidbg.android;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.BackendException;
import com.github.unidbg.arm.backend.CodeHook;
import com.github.unidbg.arm.backend.UnHook;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.pointer.UnidbgPointer;
import junit.framework.TestCase;
import unicorn.Arm64Const;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Exact libdidiwsg dispatcher fragment at +0x2e1768, including the
 * taken {@code b.eq +0x2e190c} that full-module tracing dies on.
 */
public class Arm64DispatcherBeqHookTest extends TestCase {

    /** File bytes 0x2e1768..0x2e1910 from libdidiwsg.so */
    private static final int[] DISPATCHER = {
            0xb85cc3a8, 0x528aa0a9, 0x72a748e9, 0x6b09011f,
            0x540001ec, 0x52952929, 0x72b41e09, 0x6b09011f,
            0x540003ad, 0x52952949, 0x72b41e09, 0x6b09011f,
            0x54000560, 0x529d6749, 0x72b9ea89, 0x6b09011f,
            0x54fffe01, 0xb81cc3b8, 0x17ffffee, 0x528aa0c9,
            0x72a748e9, 0x6b09011f, 0x54000800, 0x529cd789,
            0x72ac2769, 0x6b09011f, 0x540009e0, 0x528c7949,
            0x72ac02c9, 0x6b09011f, 0x54fffc41, 0x52952948,
            0xf100027f, 0x72b41e08, 0x1a8802c8, 0xb81cc3a8,
            0x17ffffdc, 0x52876de9, 0x72b2e669, 0x6b09011f,
            0x5402eec1, 0xb00002c8, 0xb00002c9, 0xf9461908,
            0xb9400108, 0xf9461d29, 0x5100050a, 0xb9400129,
            0x1b0a7d08, 0x7100293f, 0x12000108, 0x7a40a904,
            0x1a950308, 0xb81cc3a8, 0x17ffffca, 0xaa1403e8,
            0xaa1403e9, 0xaa1403ec, 0xb8404d0a, 0xf81c03a8,
            0xb8408d28, 0xf81b83a9, 0xaa1403e9, 0xaa1403ee,
            0xb81cc3b7, 0xb840cd2b, 0xf81b03a9, 0xb8410d89,
            0xf81a83ac, 0xaa1403ec, 0x292f2fa8, 0xf9400fe8,
            0xb8414d8d, 0xf81a03ac, 0xb8418dcc, 0xf81983ae,
            0xaa1403ee, 0x293037a9, 0xb9400289, 0xb841cdcf,
            0xf81903ae, 0x292e2ba9, 0x29313fac, 0xf81683b3,
            0xf90057e8, 0x17ffffab, 0xb00002c8, 0xb00002c9,
            0xf9461908, 0xb9400108, 0xf9461d29, 0xb9400129,
            0x5100050a, 0x1b0a7d08, 0x7100253f, 0x1a9fd7e9,
            0x4a09010a, 0x2a090108, 0x2a280148, 0x7200011f,
            0x52906788, 0x72b260e8, 0x1a951108, 0xb81cc3a8,
            0x17ffff98, 0x296f07a2
    };

    public void testDefaultBackendIsUnicorn2() {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setProcessName("default-backend").build();
        try {
            assertEquals("Unicorn2Backend", emulator.getBackend().getClass().getSimpleName());
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            try {
                emulator.close();
            } catch (Exception ignored) {
            }
        }
    }

    public void testUnicorn1TakenBeqWithCodeHook() throws Exception {
        String prev = System.setProperty("unidbg.backend", "unicorn1");
        try {
            runCase(false);
        } finally {
            if (prev == null) {
                System.clearProperty("unidbg.backend");
            } else {
                System.setProperty("unidbg.backend", prev);
            }
        }
    }

    public void testUnicorn2TakenBeqWithCodeHook() throws Exception {
        runCase(true);
    }

    private void runCase(boolean unicorn2) throws Exception {
        AndroidEmulator emulator = unicorn2
                ? AndroidEmulatorBuilder.for64Bit().setProcessName("beq-hook")
                    .addBackendFactory(new Unicorn2Factory(true)).build()
                : AndroidEmulatorBuilder.for64Bit().setProcessName("beq-hook").build();
        try {
            Memory memory = emulator.getMemory();
            UnidbgPointer block = memory.mmap(0x1000, 7);
            long base = block.peer;
            ByteBuffer buf = ByteBuffer.allocate(DISPATCHER.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int w : DISPATCHER) {
                buf.putInt(w);
            }
            block.write(0, buf.array(), 0, buf.array().length);

            long fp = base + 0x800;
            emulator.getBackend().reg_write(Arm64Const.UC_ARM64_REG_SP, fp - 0x100);
            emulator.getBackend().reg_write(Arm64Const.UC_ARM64_REG_FP, fp);
            // ldur w8, [x29, #-0x34] must load 0x613be6bc so b.eq at +0x68 is taken
            emulator.getBackend().mem_write(fp - 0x34, new byte[]{(byte) 0xbc, (byte) 0xe6, 0x3b, 0x61});

            long begin = base;                 // 0x2e1768
            long beq = base + 0x68;            // 0x2e17d0
            long target = base + 0x1a4;        // 0x2e190c
            // Must actually FETCH the taken target under the code hook.
            // Stopping at `until=target` would hide the Unicorn bug.
            emulator.getBackend().mem_write(target, new byte[]{0x1f, 0x20, 0x03, (byte) 0xd5});
            long until = target + 4;

            final int[] hits = new int[1];
            final long[] last = new long[1];
            emulator.getBackend().hook_add_new(new CodeHook() {
                @Override
                public void hook(Backend backend, long address, int size, Object user) {
                    hits[0]++;
                    last[0] = address;
                }

                @Override
                public void onAttach(UnHook unHook) {
                }

                @Override
                public void detach() {
                }
            }, begin, begin + DISPATCHER.length * 4L, emulator);

            try {
                emulator.getBackend().emu_start(begin, until, 0, 0);
            } catch (BackendException e) {
                long pc = emulator.getBackend().reg_read(Arm64Const.UC_ARM64_REG_PC).longValue();
                fail(String.format("%s FETCH pc=0x%x lastHook=0x%x beq=0x%x target=0x%x hits=%d err=%s",
                        unicorn2 ? "unicorn2" : "unicorn1", pc, last[0], beq, target, hits[0], e.getMessage()));
            }
            long pc = emulator.getBackend().reg_read(Arm64Const.UC_ARM64_REG_PC).longValue();
            assertEquals("should execute taken target then hit until", target + 4, pc);
            assertTrue("hook should see the b.eq", hits[0] > 0);
        } finally {
            emulator.close();
        }
    }
}

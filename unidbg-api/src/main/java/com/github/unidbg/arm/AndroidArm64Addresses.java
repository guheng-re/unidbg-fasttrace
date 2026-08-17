package com.github.unidbg.arm;

import com.github.unidbg.Emulator;
import com.github.unidbg.Family;

/**
 * Fixed 39-bit userspace for Android ARM64. No ASLR.
 * Dynarmic / KVM / Hypervisor stay at 36-bit page tables and cannot host this layout.
 */
public final class AndroidArm64Addresses {

    public static final long HEAP_BASE = 0x7010000000L;
    public static final long MMAP_BASE = 0x7100000000L;
    public static final long STACK_BASE = 0x7fe0000000L;
    public static final long SVC_BASE = 0x7fffe00000L;
    public static final long LR = 0x7ffff00000L;
    public static final long USER_TOP = 0x8000000000L;

    private AndroidArm64Addresses() {
    }

    public static boolean isAndroid64(Emulator<?> emulator) {
        return emulator != null && emulator.getFamily() == Family.Android64;
    }

    public static String unicornRequiredMessage(long address) {
        return "Android ARM64 39-bit VAS requires Unicorn; failed at 0x" + Long.toHexString(address);
    }
}

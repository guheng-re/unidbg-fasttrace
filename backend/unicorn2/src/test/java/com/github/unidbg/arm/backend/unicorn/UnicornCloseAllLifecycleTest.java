package com.github.unidbg.arm.backend.unicorn;

import org.junit.Test;
import unicorn.UnicornConst;

import java.io.IOException;

/**
 * Verifies Unicorn.closeAll is destroy-once: a second call must not pass the
 * native handle to nativeDestroy (which would crash the JVM). After close,
 * a retained UnHook.unhook is a no-op and must not touch the freed handle.
 */
public class UnicornCloseAllLifecycleTest {

    static {
        try {
            org.scijava.nativelib.NativeLoader.loadLibrary("unicorn");
        } catch (IOException ignored) {
        }
    }

    @Test
    public void closeAllTwiceDoesNotDestroyHandleAgain() {
        Unicorn unicorn = new Unicorn(UnicornConst.UC_ARCH_ARM64, UnicornConst.UC_MODE_ARM);
        Unicorn.UnHook unHook = unicorn.hook_add_new(new InterruptHook() {
            @Override
            public void hook(Unicorn u, int intno, Object user) {
            }
        }, null);
        unicorn.closeAll();
        unicorn.closeAll();
        unHook.unhook();
    }

    @Test
    public void closeAllTwiceWithoutHooksDoesNotDestroyHandleAgain() {
        Unicorn unicorn = new Unicorn(UnicornConst.UC_ARCH_ARM, UnicornConst.UC_MODE_ARM);
        unicorn.closeAll();
        unicorn.closeAll();
    }

    @Test
    public void closeAllTwiceWithHookOnArm32ThenUnhookIsNoOp() {
        Unicorn unicorn = new Unicorn(UnicornConst.UC_ARCH_ARM, UnicornConst.UC_MODE_ARM);
        Unicorn.UnHook unHook = unicorn.hook_add_new(new InterruptHook() {
            @Override
            public void hook(Unicorn u, int intno, Object user) {
            }
        }, null);
        unicorn.hook_add_new(new EventMemHook() {
            @Override
            public boolean hook(Unicorn u, long address, int size, long value, Object user) {
                return false;
            }
        }, UnicornConst.UC_HOOK_MEM_READ_UNMAPPED
                | UnicornConst.UC_HOOK_MEM_WRITE_UNMAPPED
                | UnicornConst.UC_HOOK_MEM_FETCH_UNMAPPED, null);
        unicorn.closeAll();
        unicorn.closeAll();
        unHook.unhook();
    }

}

package com.github.unidbg.arm.backend;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import org.junit.Test;

/**
 * Unicorn2Backend.destroy has no local guard and always calls Unicorn.closeAll.
 * After emulator.close, a second backend.destroy must not crash the JVM.
 */
public class Unicorn2BackendLifecycleTest {

    @Test
    public void emulatorCloseThenBackendDestroyTwiceDoesNotCrash() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit()
                .addBackendFactory(new Unicorn2Factory(true))
                .build();
        Backend backend = emulator.getBackend();
        emulator.close();
        backend.destroy();
        backend.destroy();
    }

    @Test
    public void emulatorCloseThenBackendDestroyTwiceDoesNotCrash32() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for32Bit()
                .addBackendFactory(new Unicorn2Factory(true))
                .build();
        Backend backend = emulator.getBackend();
        emulator.close();
        backend.destroy();
        backend.destroy();
    }

}

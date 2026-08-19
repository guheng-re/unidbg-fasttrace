package com.github.unidbg.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.unix.UnixEmulator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@code /proc/self/fd/} (trailing slash) and {@code /proc/self/task} must
 * resolve as directories. A non-numeric fd component is ENOENT, not a throw.
 */
public class ProcSelfFdDirResolveTest {

    @Test
    public void trailingSlashAndTaskResolve() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().build();
        try {
            int fdDir = emulator.getSyscallHandler().open(emulator, "/proc/self/fd/", IOConstants.O_RDONLY);
            assertTrue(fdDir >= 0);

            int taskDir = emulator.getSyscallHandler().open(emulator, "/proc/self/task", IOConstants.O_RDONLY);
            assertTrue(taskDir >= 0);

            int junk = emulator.getSyscallHandler().open(emulator, "/proc/self/fd/not-a-number", IOConstants.O_RDONLY);
            assertEquals(-1, junk);
            assertEquals(UnixEmulator.ENOENT, emulator.getMemory().getLastErrno());
        } finally {
            emulator.close();
        }
    }
}

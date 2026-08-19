package com.github.unidbg.file.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.unix.UnixEmulator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@code open(O_CREAT)} must not mkdir -p missing parents. Packers probe
 * {@code /data/data/<other-pkg>/...}; inventing that tree makes the probe
 * succeed and looks like a dumper is installed.
 */
public class LinuxFileSystemCreatParentTest {

    @Test
    public void creatWithoutParentIsEnoent() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit()
                .setProcessName("com.example.app")
                .build();
        try {
            FileResult<AndroidFileIO> foreign = emulator.getFileSystem().open(
                    "/data/data/some.other.package/.a",
                    IOConstants.O_RDWR | IOConstants.O_CREAT);
            assertNotNull(foreign);
            assertFalse(foreign.isSuccess());
            assertEquals(UnixEmulator.ENOENT, foreign.errno);

            FileResult<AndroidFileIO> own = emulator.getFileSystem().open(
                    "/data/data/com.example.app/files/marker",
                    IOConstants.O_RDWR | IOConstants.O_CREAT);
            assertNotNull(own);
            assertTrue(own.isSuccess());
        } finally {
            emulator.close();
        }
    }
}

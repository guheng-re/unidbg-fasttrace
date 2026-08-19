package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.struct.Stat64;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgStructure;
import com.github.unidbg.unix.IO;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;

public class AndroidFileOwnerTest {

    @Test
    public void pathRules() {
        assertEquals(AndroidFileOwner.AID_SYSTEM,
                AndroidFileOwner.uid("/data/app/com.foo-1/base.apk", 0));
        assertEquals(AndroidFileOwner.AID_SYSTEM,
                AndroidFileOwner.uid("/system/lib64/libc.so", 10123));
        assertEquals(0, AndroidFileOwner.uid("/data/data/com.foo/files/x", 0));
        assertEquals(10123, AndroidFileOwner.uid("/data/data/com.foo/files/x", 10123));
        assertEquals(0, AndroidFileOwner.uid("/tmp/x", 10123));
    }

    @Test
    public void apkFstatUsesSystemUid() throws Exception {
        File tmp = File.createTempFile("base", ".apk");
        FileOutputStream fos = new FileOutputStream(tmp);
        try {
            fos.write(new byte[]{'P', 'K', 3, 4});
        } finally {
            fos.close();
        }
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit()
                .setProcessName("com.example.app")
                .build();
        try {
            MemoryBlock mem = emulator.getMemory().malloc(UnidbgStructure.calculateSize(Stat64.class), true);
            try {
                Stat64 stat = new Stat64(mem.getPointer());
                new SimpleFileIO(0, tmp, "/data/app/com.example.app-1/base.apk")
                        .fstat(emulator, stat);
                assertEquals(AndroidFileOwner.AID_SYSTEM, stat.st_uid);
                assertEquals(AndroidFileOwner.AID_SYSTEM, stat.st_gid);
                assertEquals(IO.S_IFREG | 0644, stat.st_mode);
            } finally {
                mem.free();
            }
        } finally {
            emulator.close();
            tmp.delete();
        }
    }
}

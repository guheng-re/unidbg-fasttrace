package com.ss.android.ugc.aweme;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.Module;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.DalvikModule;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.sun.jna.Pointer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Load libmetasec_ml.so (Douyin 37.4.0 MetaSec) and call CRC64 at offset 0x24BD18.
 * This validates the native path used by Ladon signing without requiring full sub_2A45F0 CFF.
 *
 * Run from unidbg-fasttrace root:
 *   mvn -pl unidbg-android -Dtest=com.ss.android.ugc.aweme.MetaSecMlCrc64Test test
 */
public class MetaSecMlCrc64Test {

    private static final long OFF_CRC64 = 0x24BD18L;
    private static final long OFF_JNI_ONLOAD = 0x27BFB0L;
    /** Frida/offline constant: crc64("1588093228") */
    private static final long EXPECT_KEY_CRC = 0x934507fb6e509873L;

    public static void main(String[] args) throws Exception {
        int code = run();
        System.exit(code);
    }

    public static int run() throws Exception {
        Path so = resolveSo();
        System.out.println("[MetaSec] SO path=" + so.toAbsolutePath() + " exists=" + Files.exists(so));
        if (!Files.exists(so)) {
            System.err.println("[MetaSec] FAIL: libmetasec_ml.so not found");
            return 2;
        }

        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit()
                .setProcessName("com.ss.android.ugc.aweme")
                .addBackendFactory(new Unicorn2Factory(true))
                .build();
        Memory memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));

        VM vm = emulator.createDalvikVM();
        vm.setVerbose(false);

        try {
            DalvikModule dm = vm.loadLibrary(so.toFile(), false);
            Module module = dm.getModule();
            System.out.printf("[MetaSec] module base=0x%x size=0x%x name=%s%n",
                    module.base, module.size, module.name);

            // Call CRC64(data,len) at base+0x24BD18
            byte[] key = "1588093228".getBytes(StandardCharsets.US_ASCII);
            UnidbgPointer buf = memory.malloc(key.length + 16, true).getPointer();
            buf.write(0, key, 0, key.length);

            Number ret = module.callFunction(emulator, OFF_CRC64, buf.peer, key.length);
            long crc = ret.longValue();
            // Java sign-extends; normalize to unsigned 64
            System.out.printf("[MetaSec] CRC64(\"1588093228\") native=0x%x expect=0x%x%n", crc, EXPECT_KEY_CRC);

            boolean ok = (crc == EXPECT_KEY_CRC) || ((crc & 0xffffffffL) == (EXPECT_KEY_CRC & 0xffffffffL)
                    && ((crc >>> 32) & 0xffffffffL) == ((EXPECT_KEY_CRC >>> 32) & 0xffffffffL));
            // Also accept if only low equals due to Number conversion quirks — compare full via unsigned
            ok = Long.compareUnsigned(crc, EXPECT_KEY_CRC) == 0
                    || crc == EXPECT_KEY_CRC
                    || (crc & 0xFFFFFFFFFFFFFFFFL) == EXPECT_KEY_CRC;

            // Second vector: empty
            Number retEmpty = module.callFunction(emulator, OFF_CRC64, buf.peer, 0);
            System.out.printf("[MetaSec] CRC64(empty) native=0x%x%n", retEmpty.longValue());

            // Optional: try JNI_OnLoad — may crash on missing JNI env; catch
            try {
                System.out.println("[MetaSec] attempting JNI_OnLoad offset note only (not calling with null VM)");
                System.out.printf("[MetaSec] JNI_OnLoad file offset 0x%x (see IDA)%n", OFF_JNI_ONLOAD);
            } catch (Throwable t) {
                System.out.println("[MetaSec] JNI_OnLoad skip: " + t);
            }

            if (Long.compareUnsigned(crc, EXPECT_KEY_CRC) == 0) {
                System.out.println("[MetaSec] RESULT=SUCCESS native CRC64 matches Frida/Python");
                return 0;
            }
            System.out.println("[MetaSec] RESULT=MISMATCH crc");
            return 1;
        } catch (Throwable t) {
            System.err.println("[MetaSec] RESULT=ERROR " + t);
            t.printStackTrace(System.err);
            return 3;
        } finally {
            try {
                emulator.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static Path resolveSo() {
        // Prefer test resources copy
        Path a = Paths.get("unidbg-android/src/test/resources/example_binaries/douyin/libmetasec_ml.so");
        if (Files.exists(a)) return a;
        Path b = Paths.get("src/test/resources/example_binaries/douyin/libmetasec_ml.so");
        if (Files.exists(b)) return b;
        // Project Douyin tree
        Path c = Paths.get("D:/project/抖音/抖音_37.4.0/lib/arm64-v8a/libmetasec_ml.so");
        if (Files.exists(c)) return c;
        Path d = Paths.get("D:/project/抖音/抖音_37.4.0/lib/arm64-v8a/libmetasec_ml.so");
        return d;
    }
}

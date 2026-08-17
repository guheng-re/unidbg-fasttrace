package com.ss.android.ugc.aweme;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Module;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.DalvikModule;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.sun.jna.Pointer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Attempt full call of libmetasec_ml.so sub_2A45F0 (offset 0x2A45F0).
 * Frida showed: x0=URL cstring, x1=extra headers block, ret=header text block.
 */
public class MetaSecMlSignTest {

    private static final long OFF_SIGN = 0x2A45F0L;
    private static final long OFF_CRC64 = 0x24BD18L;

    public static void main(String[] args) throws Exception {
        System.exit(run(args));
    }

    public static int run(String[] args) throws Exception {
        Path so = resolveSo();
        System.out.println("[Sign] SO=" + so + " exists=" + Files.exists(so));
        if (!Files.exists(so)) return 2;

        String url = args.length > 0 ? args[0]
                : "https://log0-misc-lf.amemv.com/service/2/app_log/?version_code=370400&device_platform=android&device_id=4390792082765212&aid=1128&iid=4390792083015068&tt_data=a";
        String extra = args.length > 1 ? args[1]
                : "cookie\nstore-region=cn-hn\nuser-agent\ncom.ss.android.ugc.aweme/370401\n";

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
            System.out.printf("[Sign] base=0x%x size=0x%x%n", module.base, module.size);

            // Sanity CRC64
            byte[] key = "1588093228".getBytes(StandardCharsets.US_ASCII);
            UnidbgPointer kbuf = memory.malloc(32, true).getPointer();
            kbuf.write(0, key, 0, key.length);
            Number crc = module.callFunction(emulator, OFF_CRC64, kbuf.peer, key.length);
            System.out.printf("[Sign] CRC64 key native=0x%x%n", crc.longValue());

            byte[] urlBytes = url.getBytes(StandardCharsets.UTF_8);
            byte[] extraBytes = extra.getBytes(StandardCharsets.UTF_8);
            UnidbgPointer urlPtr = memory.malloc(urlBytes.length + 8, true).getPointer();
            UnidbgPointer extraPtr = memory.malloc(extraBytes.length + 8, true).getPointer();
            urlPtr.write(0, urlBytes, 0, urlBytes.length);
            extraPtr.write(0, extraBytes, 0, extraBytes.length);

            System.out.println("[Sign] calling sub_2A45F0 ...");
            Number ret;
            try {
                ret = module.callFunction(emulator, OFF_SIGN, urlPtr.peer, extraPtr.peer);
            } catch (Throwable t) {
                System.err.println("[Sign] call threw: " + t);
                t.printStackTrace(System.err);
                System.out.println("[Sign] RESULT=ERROR_CALL");
                return 3;
            }

            long retAddr = ret.longValue();
            System.out.printf("[Sign] ret=0x%x%n", retAddr);
            if (retAddr == 0) {
                System.out.println("[Sign] RESULT=NULL_RET");
                return 4;
            }

            Pointer p = UnidbgPointer.pointer(emulator, retAddr);
            if (p == null) {
                System.out.println("[Sign] RESULT=BAD_PTR");
                return 5;
            }
            byte[] raw = p.getByteArray(0, 4096);
            int end = 0;
            while (end < raw.length && raw[end] != 0) end++;
            String block = new String(raw, 0, end, StandardCharsets.UTF_8);
            System.out.println("[Sign] header block len=" + end);
            System.out.println(block.length() > 2000 ? block.substring(0, 2000) : block);

            Map<String, String> hdrs = parseHeaderBlock(block);
            for (Map.Entry<String, String> e : hdrs.entrySet()) {
                String v = e.getValue();
                System.out.println(">> " + e.getKey() + " = " + (v.length() > 120 ? v.substring(0, 120) + "..." : v));
            }

            boolean hasAll = hdrs.containsKey("X-Khronos") && hdrs.containsKey("X-Argus")
                    && hdrs.containsKey("X-Ladon") && hdrs.containsKey("X-Gorgon")
                    && hdrs.containsKey("X-Helios") && hdrs.containsKey("X-Medusa")
                    && hdrs.containsKey("X-Perseus");
            System.out.println(hasAll ? "[Sign] RESULT=SUCCESS_ALL_HEADERS" : "[Sign] RESULT=PARTIAL_HEADERS n=" + hdrs.size());
            return hasAll ? 0 : 6;
        } catch (Throwable t) {
            System.err.println("[Sign] RESULT=ERROR " + t);
            t.printStackTrace(System.err);
            return 7;
        } finally {
            try { emulator.close(); } catch (Exception ignored) {}
        }
    }

    private static Map<String, String> parseHeaderBlock(String block) {
        Map<String, String> m = new LinkedHashMap<>();
        String[] parts = block.split("\r\n");
        for (int i = 0; i < parts.length; i++) {
            String name = parts[i];
            if (name.startsWith("X-") || name.startsWith("x-")) {
                String val = (i + 1 < parts.length) ? parts[i + 1] : "";
                m.put(name, val);
                i++;
            }
        }
        return m;
    }

    private static Path resolveSo() {
        Path a = Paths.get("unidbg-android/src/test/resources/example_binaries/douyin/libmetasec_ml.so");
        if (Files.exists(a)) return a;
        Path b = Paths.get("src/test/resources/example_binaries/douyin/libmetasec_ml.so");
        if (Files.exists(b)) return b;
        return Paths.get("D:/project/抖音/抖音_37.4.0/lib/arm64-v8a/libmetasec_ml.so");
    }
}

package com.ss.android.ugc.aweme;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Module;
import com.github.unidbg.arm.backend.CodeHook;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.DalvikModule;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.sun.jna.Pointer;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Instruction-level / breakpoint trace of MetaSec Medusa-related native offsets
 * after loading libmetasec_ml.so. Tries:
 * 1) CRC64 sanity
 * 2) AES setkey + block decrypt with Frida-captured device key/blob
 * 3) base64 encode of known buffer (ABI: dst,cap,&out_len,src,src_len)
 * 4) full sub_2A45F0 with selective CodeHook counts on Medusa path offsets
 *
 * Run from unidbg-fasttrace root:
 *   mvnw -pl unidbg-android -Dtest=com.ss.android.ugc.aweme.MetaSecMlMedusaTraceTest test
 * or main():
 *   java -cp ... com.ss.android.ugc.aweme.MetaSecMlMedusaTraceTest [logPath]
 */
public class MetaSecMlMedusaTraceTest {

    private static final long OFF_CRC64 = 0x24BD18L;
    private static final long OFF_SIGN = 0x2A45F0L;
    private static final long OFF_B64 = 0x2460f0L;
    private static final long OFF_SETKEY = 0x2428e0L;
    private static final long OFF_AES_DEC = 0x243084L;
    private static final long OFF_AES_ENC = 0x242d30L;
    private static final long OFF_AES_CBC = 0x2434b4L;
    private static final long OFF_PACK = 0x2597b0L;
    private static final long OFF_PB = 0x254d40L;
    private static final long OFF_B64_LR1 = 0x2596e4L;
    private static final long OFF_B64_LR2 = 0x25974cL;
    private static final long OFF_PRE_ENC = 0x25ae40L;
    private static final long OFF_POST_ENC = 0x25ae8cL;

    // Frida-captured device AES key + first 16B of blob
    private static final byte[] DEVICE_KEY = hexToBytes("6381401680f4706b583bbaa229621e6f");
    private static final byte[] DEVICE_CT16 = hexToBytes("98b30f5585715d37cceb8bccde4f9f5b");
    private static final byte[] DEVICE_PT16 = hexToBytes("c9a450ece981f93d175c570090f37951");

    public static void main(String[] args) throws Exception {
        String logPath = args.length > 0 ? args[0]
                : "C:/Users/TCLX/AppData/Local/Temp/grok-goal-a80b3dcc4723/implementer/unidbg_medusa_trace.log";
        System.exit(run(logPath));
    }

    public static int run(String logPath) throws Exception {
        Path so = resolveSo();
        Path logFile = Paths.get(logPath);
        Files.createDirectories(logFile.getParent());
        PrintStream log = new PrintStream(new FileOutputStream(logFile.toFile()), true, StandardCharsets.UTF_8);
        PrintStream out = System.out;
        // tee
        System.setOut(new TeeStream(out, log));
        System.setErr(new TeeStream(System.err, log));

        System.out.println("[MedusaTrace] SO=" + so + " exists=" + Files.exists(so));
        System.out.println("[MedusaTrace] log=" + logFile.toAbsolutePath());
        if (!Files.exists(so)) return 2;

        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit()
                .setProcessName("com.ss.android.ugc.aweme")
                .addBackendFactory(new Unicorn2Factory(true))
                .build();
        Memory memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));
        VM vm = emulator.createDalvikVM();
        vm.setVerbose(false);

        Map<String, AtomicInteger> hit = new LinkedHashMap<>();
        hit.put("setkey", new AtomicInteger());
        hit.put("aes_dec", new AtomicInteger());
        hit.put("aes_enc", new AtomicInteger());
        hit.put("aes_cbc", new AtomicInteger());
        hit.put("b64", new AtomicInteger());
        hit.put("pack", new AtomicInteger());
        hit.put("pb", new AtomicInteger());
        hit.put("pre_enc", new AtomicInteger());
        hit.put("post_enc", new AtomicInteger());
        hit.put("b64_lr1", new AtomicInteger());
        hit.put("b64_lr2", new AtomicInteger());
        AtomicLong lastB64Src = new AtomicLong();
        AtomicInteger lastB64Len = new AtomicInteger();

        try {
            DalvikModule dm = vm.loadLibrary(so.toFile(), false);
            Module module = dm.getModule();
            long base = module.base;
            System.out.printf("[MedusaTrace] base=0x%x size=0x%x%n", base, module.size);

            // --- CRC64 sanity ---
            byte[] keyStr = "1588093228".getBytes(StandardCharsets.US_ASCII);
            UnidbgPointer kbuf = memory.malloc(32, true).getPointer();
            kbuf.write(0, keyStr, 0, keyStr.length);
            Number crc = module.callFunction(emulator, OFF_CRC64, kbuf.peer, keyStr.length);
            System.out.printf("[MedusaTrace] CRC64=0x%x%n", crc.longValue());

            // --- AES setkey + decrypt one block ---
            try {
                UnidbgPointer ctx = memory.malloc(0x400, true).getPointer();
                UnidbgPointer keyPtr = memory.malloc(16, true).getPointer();
                keyPtr.write(0, DEVICE_KEY, 0, 16);
                // setkey(ctx, key, 16) — from disasm x0=ctx x1=key x2=klen
                module.callFunction(emulator, OFF_SETKEY, ctx.peer, keyPtr.peer, 16);
                System.out.println("[MedusaTrace] setkey ok");

                UnidbgPointer in = memory.malloc(16, true).getPointer();
                UnidbgPointer outb = memory.malloc(16, true).getPointer();
                in.write(0, DEVICE_CT16, 0, 16);
                // AES decrypt block: x0=ctx x1=in x2=out?
                module.callFunction(emulator, OFF_AES_DEC, ctx.peer, in.peer, outb.peer);
                byte[] got = outb.getByteArray(0, 16);
                System.out.println("[MedusaTrace] aes_dec out=" + toHex(got));
                System.out.println("[MedusaTrace] aes_dec expect=" + toHex(DEVICE_PT16));
                System.out.println("[MedusaTrace] aes_dec_match=" + java.util.Arrays.equals(got, DEVICE_PT16));
            } catch (Throwable t) {
                System.out.println("[MedusaTrace] AES block path error: " + t);
                t.printStackTrace(System.out);
            }

            // --- base64 encode small buffer ---
            try {
                byte[] src = new byte[]{0x01, 0x02, 0x03, 0x04};
                UnidbgPointer srcP = memory.malloc(16, true).getPointer();
                srcP.write(0, src, 0, src.length);
                UnidbgPointer dstP = memory.malloc(64, true).getPointer();
                UnidbgPointer outLenP = memory.malloc(8, true).getPointer();
                // b64(dst, cap, &out_len, src, src_len)
                module.callFunction(emulator, OFF_B64, dstP.peer, 64, outLenP.peer, srcP.peer, src.length);
                int outLen = outLenP.getInt(0);
                byte[] enc = dstP.getByteArray(0, Math.max(0, Math.min(outLen, 64)));
                System.out.println("[MedusaTrace] b64 outLen=" + outLen + " str=" + new String(enc, StandardCharsets.US_ASCII));
            } catch (Throwable t) {
                System.out.println("[MedusaTrace] b64 error: " + t);
            }

            // Install code hooks on Medusa path offsets (counts only)
            long[][] hooks = {
                    {OFF_SETKEY, 0}, {OFF_AES_DEC, 1}, {OFF_AES_ENC, 2}, {OFF_AES_CBC, 3},
                    {OFF_B64, 4}, {OFF_PACK, 5}, {OFF_PB, 6},
                    {OFF_PRE_ENC, 7}, {OFF_POST_ENC, 8},
                    {OFF_B64_LR1, 9}, {OFF_B64_LR2, 10}
            };
            String[] names = {"setkey", "aes_dec", "aes_enc", "aes_cbc", "b64", "pack", "pb", "pre_enc", "post_enc", "b64_lr1", "b64_lr2"};
            for (int i = 0; i < hooks.length; i++) {
                final String name = names[i];
                final long off = hooks[i][0];
                final boolean isB64 = "b64".equals(name);
                try {
                    emulator.getBackend().hook_add_new(new CodeHook() {
                        @Override
                        public void hook(com.github.unidbg.arm.backend.Backend backend, long address, int size, Object user) {
                            hit.get(name).incrementAndGet();
                            if (isB64) {
                                try {
                                    RegisterContext ctx = emulator.getContext();
                                    // x3=src x4=len on AArch64 for our ABI
                                    long x3 = ctx.getLongByReg(3);
                                    long x4 = ctx.getLongByReg(4);
                                    lastB64Src.set(x3);
                                    lastB64Len.set((int) x4);
                                    if (x4 > 30 && x4 < 2000 && hit.get(name).get() <= 8) {
                                        Pointer p = UnidbgPointer.pointer(emulator, x3);
                                        if (p != null) {
                                            byte[] head = p.getByteArray(0, (int) Math.min(x4, 32));
                                            System.out.printf("[hook b64] n=%d head=%s%n", (int) x4, toHex(head));
                                        }
                                    }
                                } catch (Throwable ignored) {
                                }
                            } else if (hit.get(name).get() <= 4) {
                                System.out.printf("[hook %s] pc=0x%x%n", name, address - base);
                            }
                        }

                        @Override
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {
                        }

                        @Override
                        public void detach() {
                        }
                    }, base + off, base + off + 4, null);
                } catch (Throwable t) {
                    System.out.println("[MedusaTrace] hook fail " + name + ": " + t);
                }
            }

            // Optional narrow text trace around AES/b64 (small range to avoid multi-GB)
            Path codeTrace = logFile.getParent().resolve("unidbg_medusa_code_narrow.log");
            try {
                emulator.traceCodeText(base + 0x2428e0L, base + 0x243600L, codeTrace.toString());
                System.out.println("[MedusaTrace] narrow AES-range code trace -> " + codeTrace);
            } catch (Throwable t) {
                System.out.println("[MedusaTrace] traceCodeText skip: " + t);
            }

            // --- full sign attempt ---
            String url = "https://log0-misc-lf.amemv.com/service/2/app_log/?version_code=370400&device_platform=android&aid=1128";
            String extra = "cookie\nstore-region=cn-hn\nuser-agent\ncom.ss.android.ugc.aweme/370401\n";
            byte[] urlBytes = url.getBytes(StandardCharsets.UTF_8);
            byte[] extraBytes = extra.getBytes(StandardCharsets.UTF_8);
            UnidbgPointer urlPtr = memory.malloc(urlBytes.length + 8, true).getPointer();
            UnidbgPointer extraPtr = memory.malloc(extraBytes.length + 8, true).getPointer();
            urlPtr.write(0, urlBytes, 0, urlBytes.length);
            extraPtr.write(0, extraBytes, 0, extraBytes.length);

            System.out.println("[MedusaTrace] calling sub_2A45F0 ...");
            try {
                Number ret = module.callFunction(emulator, OFF_SIGN, urlPtr.peer, extraPtr.peer);
                long retAddr = ret.longValue();
                System.out.printf("[MedusaTrace] ret=0x%x%n", retAddr);
                if (retAddr != 0) {
                    Pointer p = UnidbgPointer.pointer(emulator, retAddr);
                    if (p != null) {
                        byte[] raw = p.getByteArray(0, 8192);
                        int end = 0;
                        while (end < raw.length && raw[end] != 0) end++;
                        String block = new String(raw, 0, end, StandardCharsets.UTF_8);
                        System.out.println("[MedusaTrace] header_block_len=" + end);
                        System.out.println(block.length() > 2500 ? block.substring(0, 2500) : block);
                        Map<String, String> hdrs = parseHeaderBlock(block);
                        for (Map.Entry<String, String> e : hdrs.entrySet()) {
                            String v = e.getValue();
                            System.out.println(">> " + e.getKey() + " = " + (v.length() > 100 ? v.substring(0, 100) + "..." : v));
                        }
                        System.out.println("[MedusaTrace] RESULT=SIGN_OK headers=" + hdrs.size());
                    }
                } else {
                    System.out.println("[MedusaTrace] RESULT=NULL_RET");
                }
            } catch (Throwable t) {
                System.out.println("[MedusaTrace] RESULT=SIGN_ERROR " + t);
                t.printStackTrace(System.out);
            }

            System.out.println("[MedusaTrace] hook_hits=" + hit);
            System.out.println("[MedusaTrace] lastB64 n=" + lastB64Len.get() + " src=0x" + Long.toHexString(lastB64Src.get()));
            System.out.println("[MedusaTrace] DONE");
            return 0;
        } catch (Throwable t) {
            System.out.println("[MedusaTrace] RESULT=ERROR " + t);
            t.printStackTrace(System.out);
            return 3;
        } finally {
            try {
                emulator.close();
            } catch (Exception ignored) {
            }
            System.setOut(out);
            log.close();
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

    private static byte[] hexToBytes(String h) {
        int n = h.length() / 2;
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte v : b) sb.append(String.format("%02x", v & 0xff));
        return sb.toString();
    }

    /** Simple tee PrintStream */
    private static class TeeStream extends PrintStream {
        private final PrintStream a;
        private final PrintStream b;

        TeeStream(PrintStream a, PrintStream b) {
            super(a);
            this.a = a;
            this.b = b;
        }

        @Override
        public void write(int c) {
            a.write(c);
            b.write(c);
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            a.write(buf, off, len);
            b.write(buf, off, len);
        }

        @Override
        public void flush() {
            a.flush();
            b.flush();
        }
    }
}

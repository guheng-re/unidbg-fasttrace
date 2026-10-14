package com.github.unidbg.linux.android;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.Arm64Hook;
import com.github.unidbg.arm.ArmHook;
import com.github.unidbg.arm.HookStatus;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.hook.HookListener;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic {@code libc.so} {@code popen}/{@code system} snapshot from {@code linux.commands}.
 * Command keys are exact user-supplied strings; the engine never special-cases a binary name.
 * Snapshot FILE* is not a real bionic {@code FILE}: {@code fread}/{@code fgets}/{@code fgetc}/
 * {@code fclose}/{@code pclose}/{@code feof} are hooked so glibc/bionic stream reads do not
 * dereference the fake object (which used to {@code UC_ERR_READ_UNMAPPED}).
 */
public class LinuxCommandHook implements HookListener {

    public static final String LIBRARY = "libc.so";
    public static final String POPEN = "popen";
    public static final String SYSTEM = "system";
    public static final String FREAD = "fread";
    public static final String FGETS = "fgets";
    public static final String FGETC = "fgetc";
    public static final String FCLOSE = "fclose";
    public static final String PCLOSE = "pclose";
    public static final String FEOF = "feof";
    public static final String FERROR = "ferror";

    private static final int FILE_TOTAL = 0;
    private static final int FILE_POS = 4;
    private static final int FILE_EOF = 8;
    private static final int FILE_PAYLOAD = 16;
    private static final int EOF = -1;

    private final Emulator<?> emulator;
    private final Map<Long, byte[]> popenStdout = new ConcurrentHashMap<Long, byte[]>();
    private final Map<Long, MemoryBlock> popenFiles = new ConcurrentHashMap<Long, MemoryBlock>();

    public LinuxCommandHook(Emulator<?> emulator) {
        this.emulator = emulator;
    }

    public static boolean shouldRegister(Emulator<?> emulator) {
        if (emulator == null) {
            return false;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config != null && config.isLinuxCommandsConfigured();
    }

    /**
     * When {@code command} is an exact configured key, allocate a FILE-like block whose
     * payload is the configured stdout and return its peer. Missing key returns {@code null}.
     */
    public Long tryPopen(Emulator<?> emulator, String command) {
        if (emulator == null || command == null) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isLinuxCommandsConfigured()) {
            return null;
        }
        String stdout = config.getLinuxCommandStdout(command);
        if (stdout == null) {
            return null;
        }
        byte[] bytes = stdout.getBytes(StandardCharsets.UTF_8);
        MemoryBlock file = emulator.getMemory().malloc(Math.max(bytes.length, 1) + FILE_PAYLOAD, true);
        UnidbgPointer pointer = file.getPointer();
        if (bytes.length > 0) {
            pointer.write(FILE_PAYLOAD, bytes, 0, bytes.length);
        }
        pointer.setInt(FILE_TOTAL, bytes.length);
        pointer.setInt(FILE_POS, 0);
        pointer.setInt(FILE_EOF, 0);
        long peer = pointer.peer;
        popenStdout.put(peer, bytes);
        popenFiles.put(peer, file);
        TraceEnvironmentEventSink.emit(emulator, "linux_command", POPEN,
                "cmdLength=" + command.length() + ",bytes=" + bytes.length,
                "json-config", "读取配置的命令快照");
        return Long.valueOf(peer);
    }

    public byte[] drainPopen(long filePeer) {
        return popenStdout.get(filePeer);
    }

    public boolean isPopenFile(long filePeer) {
        return popenStdout.containsKey(Long.valueOf(filePeer));
    }

    /**
     * Copy up to {@code size-1} bytes of the next line (including {@code \n}) into {@code dest}.
     * Returns bytes written excluding the NUL, or {@code -1} on EOF.
     */
    public int tryFgets(long filePeer, Pointer dest, int size) {
        byte[] bytes = popenStdout.get(Long.valueOf(filePeer));
        MemoryBlock block = popenFiles.get(Long.valueOf(filePeer));
        if (bytes == null || block == null || dest == null || size <= 1) {
            return -1;
        }
        UnidbgPointer file = block.getPointer();
        int pos = file.getInt(FILE_POS);
        if (pos >= bytes.length) {
            file.setInt(FILE_EOF, 1);
            return -1;
        }
        int max = size - 1;
        int n = 0;
        while (n < max && pos < bytes.length) {
            byte b = bytes[pos++];
            dest.setByte(n, b);
            n++;
            if (b == (byte) '\n') {
                break;
            }
        }
        dest.setByte(n, (byte) 0);
        file.setInt(FILE_POS, pos);
        if (pos >= bytes.length) {
            file.setInt(FILE_EOF, 1);
        }
        TraceEnvironmentEventSink.emit(emulator, "linux_command", FGETS,
                "bytes=" + n + ",pos=" + pos + ",total=" + bytes.length,
                "json-config", "读取配置的命令快照行");
        return n;
    }

    /**
     * {@code fread}-style copy. Returns the number of complete {@code size}-byte items.
     */
    public int tryFread(long filePeer, Pointer dest, int size, int nmemb) {
        byte[] bytes = popenStdout.get(Long.valueOf(filePeer));
        MemoryBlock block = popenFiles.get(Long.valueOf(filePeer));
        if (bytes == null || block == null || dest == null || size <= 0 || nmemb <= 0) {
            return 0;
        }
        UnidbgPointer file = block.getPointer();
        int pos = file.getInt(FILE_POS);
        if (pos >= bytes.length) {
            file.setInt(FILE_EOF, 1);
            return 0;
        }
        long want = (long) size * (long) nmemb;
        int remain = bytes.length - pos;
        int copy = remain < want ? remain : (int) want;
        dest.write(0, bytes, pos, copy);
        pos += copy;
        file.setInt(FILE_POS, pos);
        if (pos >= bytes.length) {
            file.setInt(FILE_EOF, 1);
        }
        return copy / size;
    }

    public int tryFgetc(long filePeer) {
        byte[] bytes = popenStdout.get(Long.valueOf(filePeer));
        MemoryBlock block = popenFiles.get(Long.valueOf(filePeer));
        if (bytes == null || block == null) {
            return EOF;
        }
        UnidbgPointer file = block.getPointer();
        int pos = file.getInt(FILE_POS);
        if (pos >= bytes.length) {
            file.setInt(FILE_EOF, 1);
            return EOF;
        }
        int value = bytes[pos] & 0xff;
        pos++;
        file.setInt(FILE_POS, pos);
        if (pos >= bytes.length) {
            file.setInt(FILE_EOF, 1);
        }
        return value;
    }

    public int tryFeof(long filePeer) {
        MemoryBlock block = popenFiles.get(Long.valueOf(filePeer));
        if (block == null) {
            return 0;
        }
        return block.getPointer().getInt(FILE_EOF) != 0 ? 1 : 0;
    }

    public boolean tryFclose(long filePeer) {
        popenStdout.remove(Long.valueOf(filePeer));
        MemoryBlock block = popenFiles.remove(Long.valueOf(filePeer));
        if (block != null) {
            block.free();
            return true;
        }
        return false;
    }

    public Integer trySystem(Emulator<?> emulator, String command) {
        if (emulator == null || command == null) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isLinuxCommandsConfigured()) {
            return null;
        }
        if (!config.getLinuxCommands().containsKey(command)) {
            return null;
        }
        TraceEnvironmentEventSink.emit(emulator, "linux_command", SYSTEM,
                "cmdLength=" + command.length(),
                "json-config", "命中配置的命令快照");
        return Integer.valueOf(0);
    }

    @Override
    public long hook(SvcMemory svcMemory, String libraryName, String symbolName, final long old) {
        if (!LIBRARY.equals(libraryName)) {
            return 0;
        }
        if (!shouldRegister(emulator)) {
            return 0;
        }
        if (POPEN.equals(symbolName)) {
            return register(svcMemory, old, new StreamOp() {
                @Override
                public HookStatus run(Emulator<?> emulator, long oldAddr) {
                    return handlePopen(emulator, oldAddr);
                }
            });
        }
        if (SYSTEM.equals(symbolName)) {
            return register(svcMemory, old, new StreamOp() {
                @Override
                public HookStatus run(Emulator<?> emulator, long oldAddr) {
                    return handleSystem(emulator, oldAddr);
                }
            });
        }
        if (FREAD.equals(symbolName)) {
            return register(svcMemory, old, new StreamOp() {
                @Override
                public HookStatus run(Emulator<?> emulator, long oldAddr) {
                    return handleFread(emulator, oldAddr);
                }
            });
        }
        if (FGETS.equals(symbolName)) {
            return register(svcMemory, old, new StreamOp() {
                @Override
                public HookStatus run(Emulator<?> emulator, long oldAddr) {
                    return handleFgets(emulator, oldAddr);
                }
            });
        }
        if (FGETC.equals(symbolName)) {
            return register(svcMemory, old, new StreamOp() {
                @Override
                public HookStatus run(Emulator<?> emulator, long oldAddr) {
                    return handleFgetc(emulator, oldAddr);
                }
            });
        }
        if (FCLOSE.equals(symbolName) || PCLOSE.equals(symbolName)) {
            return register(svcMemory, old, new StreamOp() {
                @Override
                public HookStatus run(Emulator<?> emulator, long oldAddr) {
                    return handleFclose(emulator, oldAddr);
                }
            });
        }
        if (FEOF.equals(symbolName)) {
            return register(svcMemory, old, new StreamOp() {
                @Override
                public HookStatus run(Emulator<?> emulator, long oldAddr) {
                    return handleFeof(emulator, oldAddr);
                }
            });
        }
        if (FERROR.equals(symbolName)) {
            return register(svcMemory, old, new StreamOp() {
                @Override
                public HookStatus run(Emulator<?> emulator, long oldAddr) {
                    return handleFerror(emulator, oldAddr);
                }
            });
        }
        return 0;
    }

    private interface StreamOp {
        HookStatus run(Emulator<?> emulator, long oldAddr);
    }

    private long register(SvcMemory svcMemory, final long old, final StreamOp op) {
        if (emulator.is64Bit()) {
            return svcMemory.registerSvc(new Arm64Hook() {
                @Override
                protected HookStatus hook(Emulator<?> emulator) {
                    return op.run(emulator, old);
                }
            }).peer;
        }
        return svcMemory.registerSvc(new ArmHook() {
            @Override
            protected HookStatus hook(Emulator<?> emulator) {
                return op.run(emulator, old);
            }
        }).peer;
    }

    private HookStatus handlePopen(Emulator<?> emulator, long old) {
        RegisterContext context = emulator.getContext();
        Pointer cmdPtr = context.getPointerArg(0);
        if (cmdPtr == null) {
            return HookStatus.RET(emulator, old);
        }
        Long peer = tryPopen(emulator, cmdPtr.getString(0));
        if (peer == null) {
            return HookStatus.RET(emulator, old);
        }
        return HookStatus.LR(emulator, peer.longValue());
    }

    private HookStatus handleSystem(Emulator<?> emulator, long old) {
        RegisterContext context = emulator.getContext();
        Pointer cmdPtr = context.getPointerArg(0);
        if (cmdPtr == null) {
            return HookStatus.RET(emulator, old);
        }
        Integer rc = trySystem(emulator, cmdPtr.getString(0));
        if (rc == null) {
            return HookStatus.RET(emulator, old);
        }
        return HookStatus.LR(emulator, rc.intValue() & 0xffffffffL);
    }

    private HookStatus handleFread(Emulator<?> emulator, long old) {
        RegisterContext context = emulator.getContext();
        Pointer dest = context.getPointerArg(0);
        int size = context.getIntArg(1);
        int nmemb = context.getIntArg(2);
        Pointer file = context.getPointerArg(3);
        if (file == null) {
            return HookStatus.RET(emulator, old);
        }
        long filePeer = UnidbgPointer.nativeValue(file);
        if (!isPopenFile(filePeer)) {
            return HookStatus.RET(emulator, old);
        }
        int n = tryFread(filePeer, dest, size, nmemb);
        return HookStatus.LR(emulator, n & 0xffffffffL);
    }

    private HookStatus handleFgets(Emulator<?> emulator, long old) {
        RegisterContext context = emulator.getContext();
        Pointer dest = context.getPointerArg(0);
        int size = context.getIntArg(1);
        Pointer file = context.getPointerArg(2);
        if (file == null) {
            return HookStatus.RET(emulator, old);
        }
        long filePeer = UnidbgPointer.nativeValue(file);
        if (!isPopenFile(filePeer)) {
            return HookStatus.RET(emulator, old);
        }
        int n = tryFgets(filePeer, dest, size);
        if (n < 0) {
            return HookStatus.LR(emulator, 0);
        }
        return HookStatus.LR(emulator, UnidbgPointer.nativeValue(dest));
    }

    private HookStatus handleFgetc(Emulator<?> emulator, long old) {
        RegisterContext context = emulator.getContext();
        Pointer file = context.getPointerArg(0);
        if (file == null) {
            return HookStatus.RET(emulator, old);
        }
        long filePeer = UnidbgPointer.nativeValue(file);
        if (!isPopenFile(filePeer)) {
            return HookStatus.RET(emulator, old);
        }
        int value = tryFgetc(filePeer);
        return HookStatus.LR(emulator, value & 0xffffffffL);
    }

    private HookStatus handleFclose(Emulator<?> emulator, long old) {
        RegisterContext context = emulator.getContext();
        Pointer file = context.getPointerArg(0);
        if (file == null) {
            return HookStatus.RET(emulator, old);
        }
        long filePeer = UnidbgPointer.nativeValue(file);
        if (!isPopenFile(filePeer)) {
            return HookStatus.RET(emulator, old);
        }
        tryFclose(filePeer);
        return HookStatus.LR(emulator, 0);
    }

    private HookStatus handleFeof(Emulator<?> emulator, long old) {
        RegisterContext context = emulator.getContext();
        Pointer file = context.getPointerArg(0);
        if (file == null) {
            return HookStatus.RET(emulator, old);
        }
        long filePeer = UnidbgPointer.nativeValue(file);
        if (!isPopenFile(filePeer)) {
            return HookStatus.RET(emulator, old);
        }
        return HookStatus.LR(emulator, tryFeof(filePeer) & 0xffffffffL);
    }

    private HookStatus handleFerror(Emulator<?> emulator, long old) {
        RegisterContext context = emulator.getContext();
        Pointer file = context.getPointerArg(0);
        if (file == null) {
            return HookStatus.RET(emulator, old);
        }
        long filePeer = UnidbgPointer.nativeValue(file);
        if (!isPopenFile(filePeer)) {
            return HookStatus.RET(emulator, old);
        }
        return HookStatus.LR(emulator, 0);
    }
}

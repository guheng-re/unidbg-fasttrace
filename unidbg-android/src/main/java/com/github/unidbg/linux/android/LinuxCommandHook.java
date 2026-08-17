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
 */
public class LinuxCommandHook implements HookListener {

    public static final String LIBRARY = "libc.so";
    public static final String POPEN = "popen";
    public static final String SYSTEM = "system";

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
        MemoryBlock file = emulator.getMemory().malloc(Math.max(bytes.length, 1) + 16, true);
        UnidbgPointer pointer = file.getPointer();
        if (bytes.length > 0) {
            pointer.write(16, bytes, 0, bytes.length);
        }
        pointer.setInt(0, bytes.length);
        pointer.setInt(4, 0);
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
            if (emulator.is64Bit()) {
                return svcMemory.registerSvc(new Arm64Hook() {
                    @Override
                    protected HookStatus hook(Emulator<?> emulator) {
                        return handlePopen(emulator, old);
                    }
                }).peer;
            }
            return svcMemory.registerSvc(new ArmHook() {
                @Override
                protected HookStatus hook(Emulator<?> emulator) {
                    return handlePopen(emulator, old);
                }
            }).peer;
        }
        if (SYSTEM.equals(symbolName)) {
            if (emulator.is64Bit()) {
                return svcMemory.registerSvc(new Arm64Hook() {
                    @Override
                    protected HookStatus hook(Emulator<?> emulator) {
                        return handleSystem(emulator, old);
                    }
                }).peer;
            }
            return svcMemory.registerSvc(new ArmHook() {
                @Override
                protected HookStatus hook(Emulator<?> emulator) {
                    return handleSystem(emulator, old);
                }
            }).peer;
        }
        return 0;
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
}

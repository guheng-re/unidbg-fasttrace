package com.github.unidbg.linux.android;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.Arm64Hook;
import com.github.unidbg.arm.ArmHook;
import com.github.unidbg.arm.HookStatus;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.hook.HookListener;
import com.github.unidbg.linux.AndroidSyscallHandler;
import com.github.unidbg.memory.SvcMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Intercepts libc {@code sysconf} for configured {@code linux.cpu} processor-count fields only.
 * Unconfigured names return to the original symbol (no host fallback, no sidecar).
 */
public class SysconfHook implements HookListener {

    private static final Logger log = LoggerFactory.getLogger(SysconfHook.class);

    private final Emulator<?> emulator;

    public SysconfHook(Emulator<?> emulator) {
        this.emulator = emulator;
    }

    @Override
    public long hook(SvcMemory svcMemory, String libraryName, String symbolName, final long old) {
        if (!"libc.so".equals(libraryName) || !"sysconf".equals(symbolName)) {
            return 0;
        }
        log.debug("Hook {}", symbolName);
        if (emulator.is64Bit()) {
            return svcMemory.registerSvc(new Arm64Hook() {
                @Override
                protected HookStatus hook(Emulator<?> emulator) {
                    return handleSysconf(emulator, old);
                }
            }).peer;
        }
        return svcMemory.registerSvc(new ArmHook() {
            @Override
            protected HookStatus hook(Emulator<?> emulator) {
                return handleSysconf(emulator, old);
            }
        }).peer;
    }

    private HookStatus handleSysconf(Emulator<?> emulator, long old) {
        RegisterContext context = emulator.getContext();
        int name = context.getIntArg(0);
        if (!(emulator.getSyscallHandler() instanceof AndroidSyscallHandler)) {
            return HookStatus.RET(emulator, old);
        }
        AndroidSyscallHandler handler = (AndroidSyscallHandler) emulator.getSyscallHandler();
        Long configured = handler.trySysconf(emulator, name);
        if (configured != null) {
            if (log.isDebugEnabled()) {
                log.debug("sysconf name={}, result={}", name, configured);
            }
            return HookStatus.LR(emulator, configured.longValue());
        }
        return HookStatus.RET(emulator, old);
    }
}

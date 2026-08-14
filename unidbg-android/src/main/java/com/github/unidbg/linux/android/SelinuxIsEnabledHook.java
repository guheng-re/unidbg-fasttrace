package com.github.unidbg.linux.android;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.Arm64Hook;
import com.github.unidbg.arm.ArmHook;
import com.github.unidbg.arm.HookStatus;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.hook.HookListener;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native {@code libselinux.so} {@code is_selinux_enabled(void)} only.
 * When {@code android.securitySignals} is configured, returns {@code 1} if
 * {@code selinuxEnabled} is true else {@code 0} and emits one {@code linux_security}
 * sidecar. When the node is absent, does not intercept (no host fallback).
 * Independent of {@link SelinuxGetEnforceHook}; does not implement setenforce, getfilecon,
 * xattr, getcon, or other libselinux APIs.
 */
public class SelinuxIsEnabledHook implements HookListener {

    private static final Logger log = LoggerFactory.getLogger(SelinuxIsEnabledHook.class);

    public static final String LIBRARY = "libselinux.so";
    public static final String SYMBOL = "is_selinux_enabled";

    private final Emulator<?> emulator;

    public SelinuxIsEnabledHook(Emulator<?> emulator) {
        this.emulator = emulator;
    }

    /**
     * Config-backed {@code is_selinux_enabled}: returns {@code 0} or {@code 1} when
     * {@code android.securitySignals} is present; {@code null} when absent (caller keeps
     * prior path; no host fallback; no sidecar).
     */
    public static Integer tryIsSelinuxEnabled(Emulator<?> emulator) {
        if (emulator == null) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isAndroidSecuritySignalsConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.AndroidSecuritySignalsConfig signals =
                config.getAndroidSecuritySignalsConfig();
        if (signals == null) {
            return null;
        }
        int result = signals.isSelinuxEnabled() ? 1 : 0;
        TraceEnvironmentEventSink.emit(emulator, "linux_security",
                "is_selinux_enabled",
                "result=" + result,
                "json-config", "读取配置的 SELinux 启用状态（native is_selinux_enabled）");
        return Integer.valueOf(result);
    }

    @Override
    public long hook(SvcMemory svcMemory, String libraryName, String symbolName, final long old) {
        if (!LIBRARY.equals(libraryName) || !SYMBOL.equals(symbolName)) {
            return 0;
        }
        log.debug("Hook {}!{}", libraryName, symbolName);
        if (emulator.is64Bit()) {
            return svcMemory.registerSvc(new Arm64Hook() {
                @Override
                protected HookStatus hook(Emulator<?> emulator) {
                    return handleIsEnabled(emulator, old);
                }
            }).peer;
        }
        return svcMemory.registerSvc(new ArmHook() {
            @Override
            protected HookStatus hook(Emulator<?> emulator) {
                return handleIsEnabled(emulator, old);
            }
        }).peer;
    }

    private static HookStatus handleIsEnabled(Emulator<?> emulator, long old) {
        Integer result = tryIsSelinuxEnabled(emulator);
        if (result != null) {
            if (log.isDebugEnabled()) {
                log.debug("is_selinux_enabled result={}", result);
            }
            return HookStatus.LR(emulator, result.intValue() & 0xffffffffL);
        }
        return HookStatus.RET(emulator, old);
    }
}

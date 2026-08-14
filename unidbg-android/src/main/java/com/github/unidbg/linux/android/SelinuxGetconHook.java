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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native {@code libselinux.so} {@code getcon(char **context)},
 * {@code getfilecon}/{@code lgetfilecon(const char *path, char **context)}, and paired
 * {@code freecon(char *context)} only.
 * <ul>
 *   <li>{@code getcon} — explicit {@code linux.proc.selinuxContext}</li>
 *   <li>{@code getfilecon} / {@code lgetfilecon} — explicit
 *       {@code linux.proc.fileSelinuxContexts} literal normalized path map (no symlink
 *       resolution; same map for both symbols)</li>
 *   <li>{@code freecon} — releases only allocations created by this hook instance</li>
 * </ul>
 * Does not implement fgetfilecon, getpeercon, setcon, security_check_context, xattr, or
 * policy APIs.
 */
public class SelinuxGetconHook implements HookListener {

    private static final Logger log = LoggerFactory.getLogger(SelinuxGetconHook.class);

    public static final String LIBRARY = "libselinux.so";
    public static final String GETCON = "getcon";
    public static final String GETFILECON = "getfilecon";
    public static final String LGETFILECON = "lgetfilecon";
    public static final String FREECON = "freecon";

    private final Emulator<?> emulator;
    /** peer address → block allocated by getcon/getfilecon/lgetfilecon for this emulator. */
    private final Map<Long, MemoryBlock> trackedAllocations = new ConcurrentHashMap<Long, MemoryBlock>();

    public SelinuxGetconHook(Emulator<?> emulator) {
        this.emulator = emulator;
    }

    /**
     * Configured process SELinux context when {@code linux.proc.selinuxContext} is present;
     * {@code null} when absent (no host fallback).
     */
    public static String configuredContext(Emulator<?> emulator) {
        if (emulator == null) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (proc == null || !proc.isSelinuxContextConfigured()) {
            return null;
        }
        return proc.getSelinuxContext();
    }

    /**
     * Configured file SELinux context for {@code path} when
     * {@code linux.proc.fileSelinuxContexts} is present and contains the path;
     * {@code null} when the map/path is absent (no host fallback).
     */
    public static String configuredFileContext(Emulator<?> emulator, String path) {
        if (emulator == null || path == null) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (proc == null || !proc.isFileSelinuxContextsConfigured()) {
            return null;
        }
        return proc.lookupFileSelinuxContext(path);
    }

    /**
     * Whether this hook should be registered: either process or file SELinux contexts
     * are explicitly configured under {@code linux.proc}.
     */
    public static boolean shouldRegister(Emulator<?> emulator) {
        return isSelinuxContextConfigured(emulator) || isFileSelinuxContextsConfigured(emulator);
    }

    public static boolean isSelinuxContextConfigured(Emulator<?> emulator) {
        return configuredContext(emulator) != null;
    }

    public static boolean isFileSelinuxContextsConfigured(Emulator<?> emulator) {
        if (emulator == null) {
            return false;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isLinuxProcConfigured()) {
            return false;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        return proc != null && proc.isFileSelinuxContextsConfigured();
    }

    /**
     * {@code getcon}: when configured and {@code contextOut} non-null, allocate NUL-terminated
     * context, write pointer to {@code *contextOut}, track for freecon, emit sidecar, return
     * {@code 0}. Returns {@code null} when not handled (absence / null out → no interception).
     */
    public Integer tryGetcon(Emulator<?> emulator, Pointer contextOut) {
        String context = configuredContext(emulator);
        if (context == null) {
            return null;
        }
        if (contextOut == null) {
            return null;
        }
        int length = allocateAndWriteContext(emulator, contextOut, context);
        if (length < 0) {
            return null;
        }
        TraceEnvironmentEventSink.emit(emulator, "linux_security",
                "getcon",
                "result=0,length=" + length,
                "json-config", "读取配置的进程 SELinux 上下文（native getcon）");
        if (log.isDebugEnabled()) {
            log.debug("getcon length={}", length);
        }
        return Integer.valueOf(0);
    }

    /**
     * {@code getfilecon}: when path is configured and both pointers non-null, allocate
     * NUL-terminated context, write pointer to {@code *contextOut}, track for freecon, emit
     * sidecar, return length including NUL (libselinux ABI). Returns {@code null} when not
     * handled (map/path absence, null path/out → no interception). Does not resolve symlinks
     * (literal path map only).
     */
    public Integer tryGetfilecon(Emulator<?> emulator, Pointer pathPtr, Pointer contextOut) {
        return tryPathFilecon(emulator, pathPtr, contextOut, GETFILECON,
                "读取配置的文件 SELinux 上下文（native getfilecon）");
    }

    /**
     * {@code lgetfilecon}: same configured path map and allocation/freecon lifecycle as
     * {@link #tryGetfilecon}; does <strong>not</strong> resolve symlinks (literal normalized
     * path lookup only). Sidecar {@code api=lgetfilecon}. Returns {@code null} when not handled.
     */
    public Integer tryLgetfilecon(Emulator<?> emulator, Pointer pathPtr, Pointer contextOut) {
        return tryPathFilecon(emulator, pathPtr, contextOut, LGETFILECON,
                "读取配置的文件 SELinux 上下文（native lgetfilecon，不解析符号链接）");
    }

    /**
     * Shared path-keyed file context for getfilecon/lgetfilecon: literal map lookup only
     * (no symlink resolution, no fgetfilecon/xattr/policy).
     */
    private Integer tryPathFilecon(Emulator<?> emulator, Pointer pathPtr, Pointer contextOut,
                                   String api, String note) {
        if (pathPtr == null || contextOut == null) {
            return null;
        }
        String path;
        try {
            path = pathPtr.getString(0);
        } catch (RuntimeException e) {
            return null;
        }
        if (path == null || path.isEmpty()) {
            return null;
        }
        String context = configuredFileContext(emulator, path);
        if (context == null) {
            return null;
        }
        int length = allocateAndWriteContext(emulator, contextOut, context);
        if (length < 0) {
            return null;
        }
        // libselinux getfilecon/lgetfilecon: return length including terminating NUL
        int result = length + 1;
        TraceEnvironmentEventSink.emit(emulator, "linux_security",
                api,
                "result=" + result + ",length=" + length,
                "json-config", note);
        if (log.isDebugEnabled()) {
            log.debug("{} pathLength={}, contextLength={}, result={}",
                    api, path.length(), length, result);
        }
        return Integer.valueOf(result);
    }

    /**
     * Allocate NUL-terminated UTF-8 context, write pointer to {@code contextOut}, track peer.
     * @return UTF-8 byte length of context (excluding NUL), or {@code -1} on failure
     */
    private int allocateAndWriteContext(Emulator<?> emulator, Pointer contextOut, String context) {
        byte[] data = context.getBytes(StandardCharsets.UTF_8);
        MemoryBlock block = emulator.getMemory().malloc(data.length + 1, true);
        UnidbgPointer buf = block.getPointer();
        buf.write(0, data, 0, data.length);
        buf.setByte(data.length, (byte) 0);
        contextOut.setPointer(0, buf);
        long peer = UnidbgPointer.nativeValue(buf);
        trackedAllocations.put(peer, block);
        return data.length;
    }

    /**
     * {@code freecon}: frees only allocations created by {@link #tryGetcon}/
     * {@link #tryGetfilecon}/{@link #tryLgetfilecon} on this hook instance.
     * Returns {@code true} if released by us; {@code false} if not tracked (caller keeps prior path).
     * Emits no sidecar.
     */
    public boolean tryFreecon(Pointer context) {
        if (context == null) {
            return false;
        }
        long peer = UnidbgPointer.nativeValue(context);
        if (peer == 0L) {
            return false;
        }
        MemoryBlock block = trackedAllocations.remove(peer);
        if (block == null) {
            return false;
        }
        block.free();
        if (log.isDebugEnabled()) {
            log.debug("freecon peer=0x{}", Long.toHexString(peer));
        }
        return true;
    }

    /** Test helper: whether a pointer peer is currently tracked as a hook allocation. */
    public boolean isTracked(long peer) {
        return trackedAllocations.containsKey(peer);
    }

    public int trackedCount() {
        return trackedAllocations.size();
    }

    @Override
    public long hook(SvcMemory svcMemory, String libraryName, String symbolName, final long old) {
        if (!LIBRARY.equals(libraryName)) {
            return 0;
        }
        if (GETCON.equals(symbolName)) {
            // Only intercept when process context is configured.
            if (!isSelinuxContextConfigured(emulator)) {
                return 0;
            }
            log.debug("Hook {}!{}", libraryName, symbolName);
            if (emulator.is64Bit()) {
                return svcMemory.registerSvc(new Arm64Hook() {
                    @Override
                    protected HookStatus hook(Emulator<?> emulator) {
                        return handleGetcon(emulator, old);
                    }
                }).peer;
            }
            return svcMemory.registerSvc(new ArmHook() {
                @Override
                protected HookStatus hook(Emulator<?> emulator) {
                    return handleGetcon(emulator, old);
                }
            }).peer;
        }
        if (GETFILECON.equals(symbolName) || LGETFILECON.equals(symbolName)) {
            // Only intercept when file context map is configured (paths still may miss).
            if (!isFileSelinuxContextsConfigured(emulator)) {
                return 0;
            }
            final boolean lget = LGETFILECON.equals(symbolName);
            log.debug("Hook {}!{}", libraryName, symbolName);
            if (emulator.is64Bit()) {
                return svcMemory.registerSvc(new Arm64Hook() {
                    @Override
                    protected HookStatus hook(Emulator<?> emulator) {
                        return handlePathFilecon(emulator, old, lget);
                    }
                }).peer;
            }
            return svcMemory.registerSvc(new ArmHook() {
                @Override
                protected HookStatus hook(Emulator<?> emulator) {
                    return handlePathFilecon(emulator, old, lget);
                }
            }).peer;
        }
        if (FREECON.equals(symbolName)) {
            log.debug("Hook {}!{}", libraryName, symbolName);
            if (emulator.is64Bit()) {
                return svcMemory.registerSvc(new Arm64Hook() {
                    @Override
                    protected HookStatus hook(Emulator<?> emulator) {
                        return handleFreecon(emulator, old);
                    }
                }).peer;
            }
            return svcMemory.registerSvc(new ArmHook() {
                @Override
                protected HookStatus hook(Emulator<?> emulator) {
                    return handleFreecon(emulator, old);
                }
            }).peer;
        }
        return 0;
    }

    private HookStatus handleGetcon(Emulator<?> emulator, long old) {
        RegisterContext context = emulator.getContext();
        Pointer contextOut = context.getPointerArg(0);
        Integer result = tryGetcon(emulator, contextOut);
        if (result != null) {
            return HookStatus.LR(emulator, result.intValue() & 0xffffffffL);
        }
        return HookStatus.RET(emulator, old);
    }

    private HookStatus handlePathFilecon(Emulator<?> emulator, long old, boolean lget) {
        RegisterContext context = emulator.getContext();
        Pointer pathPtr = context.getPointerArg(0);
        Pointer contextOut = context.getPointerArg(1);
        Integer result = lget
                ? tryLgetfilecon(emulator, pathPtr, contextOut)
                : tryGetfilecon(emulator, pathPtr, contextOut);
        if (result != null) {
            return HookStatus.LR(emulator, result.intValue() & 0xffffffffL);
        }
        return HookStatus.RET(emulator, old);
    }

    private HookStatus handleFreecon(Emulator<?> emulator, long old) {
        RegisterContext context = emulator.getContext();
        Pointer ptr = context.getPointerArg(0);
        if (tryFreecon(ptr)) {
            // freecon is void; return to LR without chaining to original free
            return HookStatus.LR(emulator, 0);
        }
        return HookStatus.RET(emulator, old);
    }
}

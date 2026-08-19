package com.github.unidbg.linux;

import com.github.unidbg.AbstractEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.arm.backend.BackendException;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileIO;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.ConfiguredFileStatFs;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.file.DirectoryFileIO;
import com.github.unidbg.linux.file.EventFD;
import com.github.unidbg.linux.file.PipedReadFileIO;
import com.github.unidbg.linux.file.PipedWriteFileIO;
import com.github.unidbg.linux.file.SocketIO;
import com.github.unidbg.linux.signal.SigAction;
import com.github.unidbg.linux.signal.SignalFunction;
import com.github.unidbg.linux.signal.SignalTask;
import com.github.unidbg.linux.struct.StatFS;
import com.github.unidbg.linux.struct.StatFS32;
import com.github.unidbg.linux.struct.StatFS64;
import com.github.unidbg.linux.struct.SysInfo32;
import com.github.unidbg.linux.struct.SysInfo64;
import com.github.unidbg.linux.thread.FutexIndefinitelyWaiter;
import com.github.unidbg.linux.thread.FutexNanoSleepWaiter;
import com.github.unidbg.linux.thread.FutexWaiter;
import com.github.unidbg.linux.thread.MarshmallowThread;
import com.github.unidbg.linux.thread.NanoSleepWaiter;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.pointer.UnidbgStructure;
import com.github.unidbg.signal.SigSet;
import com.github.unidbg.signal.SignalOps;
import com.github.unidbg.signal.UnixSigSet;
import com.github.unidbg.spi.SyscallHandler;
import com.github.unidbg.thread.MainTask;
import com.github.unidbg.thread.RunnableTask;
import com.github.unidbg.thread.Task;
import com.github.unidbg.thread.ThreadContextSwitchException;
import com.github.unidbg.thread.ThreadDispatcher;
import com.github.unidbg.thread.ThreadTask;
import com.github.unidbg.thread.Waiter;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.unix.IO;
import com.github.unidbg.unix.UnixEmulator;
import com.github.unidbg.unix.UnixSyscallHandler;
import com.github.unidbg.unix.struct.TimeSpec;
import com.github.unidbg.utils.Inspector;
import com.sun.jna.Pointer;
import net.dongliu.apk.parser.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AndroidSyscallHandler extends UnixSyscallHandler<AndroidFileIO> implements SyscallHandler<AndroidFileIO> {

    private static final Logger log = LoggerFactory.getLogger(AndroidSyscallHandler.class);

    static final int MREMAP_MAYMOVE = 1;
    static final int MREMAP_FIXED = 2;

    private byte[] sched_cpu_mask;

    protected final int getConfiguredPpid(Emulator<?> emulator, int fallback) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config == null ? fallback : config.getPpid(fallback);
    }

    protected final int getConfiguredPgid(Emulator<?> emulator, int fallback) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config == null ? fallback : config.getPgid(fallback);
    }

    protected final int getConfiguredSid(Emulator<?> emulator, int fallback) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config == null ? fallback : config.getSid(fallback);
    }

    /**
     * Read-only {@code getpgrp} for the current process (ARM32 NR 65). AArch64 has no
     * independent {@code getpgrp} syscall.
     */
    protected final int readGetpgrp(Emulator<?> emulator) {
        int selfPid = emulator.getPid();
        return emitProcessIdentity(emulator, "getpgrp", getConfiguredPgid(emulator, selfPid));
    }

    /**
     * Read-only {@code getpgid(pid)}: hits config only for {@code pid==0} or the current
     * {@code emulator.getPid()}. Other pids return {@code null} so the architecture keeps
     * its unknown-syscall / old path (no errno rewrite).
     */
    protected final Integer tryGetpgid(Emulator<?> emulator) {
        if (emulator == null) {
            return null;
        }
        int pid = emulator.getContext().getIntArg(0);
        int selfPid = emulator.getPid();
        if (pid != 0 && pid != selfPid) {
            return null;
        }
        return Integer.valueOf(emitProcessIdentity(emulator, "getpgid",
                getConfiguredPgid(emulator, selfPid)));
    }

    /**
     * Read-only {@code getsid(pid)}: same 0/current-pid rule as {@link #tryGetpgid}.
     * Missing {@code process.sid} defaults to current pid, never ppid.
     */
    protected final Integer tryGetsid(Emulator<?> emulator) {
        if (emulator == null) {
            return null;
        }
        int pid = emulator.getContext().getIntArg(0);
        int selfPid = emulator.getPid();
        if (pid != 0 && pid != selfPid) {
            return null;
        }
        return Integer.valueOf(emitProcessIdentity(emulator, "getsid",
                getConfiguredSid(emulator, selfPid)));
    }

    /**
     * Read-only {@code getgroups} / {@code getgroups32} backed by explicit
     * {@code process.supplementaryGids}. ARM32 NR 205 and ARM64 NR 158 only (not ARM32 NR 80).
     * Missing key returns {@code null} so ARM32 keeps returning 0 and ARM64 keeps the unknown
     * syscall path. Does not implement {@code setgroups} or host fallback.
     *
     * @return group count or {@code -1} when handled; {@code null} when the key is absent
     */
    protected final Integer tryGetgroups(Emulator<?> emulator) {
        if (emulator == null) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isSupplementaryGidsConfigured()) {
            return null;
        }
        List<Integer> gids = config.getSupplementaryGids();
        int count = gids.size();
        RegisterContext ctx = emulator.getContext();
        int size = ctx.getIntArg(0);
        if (size == 0) {
            emitGetgroupsSuccess(emulator, count, size);
            return Integer.valueOf(count);
        }
        if (size < count) {
            emulator.getMemory().setErrno(UnixEmulator.EINVAL);
            return Integer.valueOf(-1);
        }
        Pointer list = ctx.getPointerArg(1);
        if (count > 0 && list == null) {
            emulator.getMemory().setErrno(UnixEmulator.EFAULT);
            return Integer.valueOf(-1);
        }
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                list.setInt((long) i * 4, gids.get(i).intValue());
            }
        }
        emitGetgroupsSuccess(emulator, count, size);
        return Integer.valueOf(count);
    }

    private static void emitGetgroupsSuccess(Emulator<?> emulator, int count, int requestedSize) {
        TraceEnvironmentEventSink.emit(emulator, "process_identity", "getgroups",
                "count=" + count + ",requestedSize=" + requestedSize,
                "json-config",
                "读取配置的补充组 getgroups");
    }

    protected final int getConfiguredTid(Emulator<?> emulator, int fallback) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config == null ? fallback : config.getTid(fallback);
    }

    protected final int getConfiguredUid(Emulator<?> emulator, int fallback) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config == null ? fallback : config.getUid(fallback);
    }

    protected final int getConfiguredGid(Emulator<?> emulator, int fallback) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config == null ? fallback : config.getGid(fallback);
    }

    protected final int getConfiguredEuid(Emulator<?> emulator, int fallback) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config == null ? fallback : config.getEuid(fallback);
    }

    protected final int getConfiguredEgid(Emulator<?> emulator, int fallback) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config == null ? fallback : config.getEgid(fallback);
    }

    protected final String getConfiguredThreadName(Emulator<?> emulator, String fallback) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config == null ? fallback : config.getThreadName(fallback);
    }

    /**
     * Truncate a thread name for {@code prctl(PR_GET_NAME)} to at most {@code 15} UTF-8 bytes
     * without splitting a multibyte UTF-8 code point (TASK_COMM_LEN-1). ASCII of length ≤15 is
     * unchanged; pure-ASCII longer names match historical 15-character truncation.
     */
    protected static String truncatePrctlThreadNameUtf8(String name) {
        if (name == null || name.isEmpty()) {
            return name == null ? "" : name;
        }
        byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
        if (utf8.length <= 15) {
            return name;
        }
        int end = 0;
        int byteCount = 0;
        int i = 0;
        while (i < name.length()) {
            int cp = name.codePointAt(i);
            int charCount = Character.charCount(cp);
            // UTF-8 length of one Unicode scalar value
            int pieceBytes;
            if (cp <= 0x7F) {
                pieceBytes = 1;
            } else if (cp <= 0x7FF) {
                pieceBytes = 2;
            } else if (cp <= 0xFFFF) {
                pieceBytes = 3;
            } else {
                pieceBytes = 4;
            }
            if (byteCount + pieceBytes > 15) {
                break;
            }
            byteCount += pieceBytes;
            i += charCount;
            end = i;
        }
        return name.substring(0, end);
    }

    protected final int emitProcessIdentity(Emulator<?> emulator, String api, int value) {
        TraceEnvironmentEventSink.emit(emulator, "process_identity", api, String.valueOf(value),
                processIdentitySource(emulator, api), "读取进程身份 " + api);
        return value;
    }

    /**
     * Linux {@code _LINUX_CAPABILITY_VERSION_3} ({@code linux/capability.h}).
     * V3 uses two {@code __user_cap_data_struct} slots (64-bit masks).
     */
    public static final int LINUX_CAPABILITY_VERSION_3 = 0x20080522;
    /** Number of {@code __u32} capability words for V3. */
    public static final int LINUX_CAPABILITY_U32S_3 = 2;
    /** Size of one {@code __user_cap_data_struct} (effective/permitted/inheritable u32s). */
    private static final int CAP_USER_DATA_SIZE = 12;

    /**
     * Read-only {@code capget} for capability V3 only, backed by explicit
     * {@code linux.proc.capInheritableHex}/{@code capPermittedHex}/{@code capEffectiveHex}.
     * Handles only pid {@code 0} or the emulator/configured process pid and non-null
     * V3 header/data pointers. Writes two 32-bit data slots from the 64-bit masks
     * (unconfigured fields → 0). Does not implement {@code capset}, CapBnd/CapAmb, or
     * host fallback.
     *
     * @return {@code 0} when handled; {@code null} when not intercepted (absence /
     *         wrong version / bad pid / null pointers → keep prior path)
     */
    protected final Integer tryCapget(Emulator<?> emulator) {
        if (emulator == null) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (proc == null) {
            return null;
        }
        boolean inhCfg = proc.isCapInheritableHexConfigured();
        boolean prmCfg = proc.isCapPermittedHexConfigured();
        boolean effCfg = proc.isCapEffectiveHexConfigured();
        if (!inhCfg && !prmCfg && !effCfg) {
            return null;
        }

        RegisterContext ctx = emulator.getContext();
        Pointer header = ctx.getPointerArg(0);
        Pointer data = ctx.getPointerArg(1);
        if (header == null || data == null) {
            return null;
        }

        int version = header.getInt(0);
        int pid = header.getInt(4);
        if (version != LINUX_CAPABILITY_VERSION_3) {
            return null;
        }
        int selfPid = emulator.getPid();
        if (pid != 0 && pid != selfPid) {
            return null;
        }

        long inh = parseCapMask64(inhCfg ? proc.getCapInheritableHex() : null);
        long prm = parseCapMask64(prmCfg ? proc.getCapPermittedHex() : null);
        long eff = parseCapMask64(effCfg ? proc.getCapEffectiveHex() : null);

        // data[0]: lower 32 bits; data[1]: upper 32 bits (V3 layout)
        writeCapDataSlot(data, 0, eff, prm, inh, false);
        writeCapDataSlot(data, CAP_USER_DATA_SIZE, eff, prm, inh, true);

        StringBuilder fields = new StringBuilder();
        if (inhCfg) {
            fields.append("inh");
        }
        if (prmCfg) {
            if (fields.length() > 0) {
                fields.append('|');
            }
            fields.append("prm");
        }
        if (effCfg) {
            if (fields.length() > 0) {
                fields.append('|');
            }
            fields.append("eff");
        }
        TraceEnvironmentEventSink.emit(emulator, "linux_proc",
                "capget",
                "result=0,version=3,pid=" + pid + ",slots=2,fields=" + fields,
                "json-config",
                "读取配置的进程能力位（capget V3）");
        if (log.isDebugEnabled()) {
            log.debug("capget V3 pid={} fields={}", pid, fields);
        }
        return Integer.valueOf(0);
    }

    /** Parse 16-hex lowercase mask to unsigned 64-bit; null/empty → 0. */
    private static long parseCapMask64(String hex) {
        if (hex == null || hex.length() != 16) {
            return 0L;
        }
        // high 8 hex = bits 32..63, low 8 = bits 0..31
        long high = Long.parseLong(hex.substring(0, 8), 16);
        long low = Long.parseLong(hex.substring(8, 16), 16);
        return ((high & 0xffffffffL) << 32) | (low & 0xffffffffL);
    }

    private static void writeCapDataSlot(Pointer data, int base, long eff, long prm, long inh,
                                         boolean highWord) {
        int shift = highWord ? 32 : 0;
        data.setInt(base, (int) ((eff >>> shift) & 0xffffffffL));
        data.setInt(base + 4, (int) ((prm >>> shift) & 0xffffffffL));
        data.setInt(base + 8, (int) ((inh >>> shift) & 0xffffffffL));
    }

    private static String processIdentitySource(Emulator<?> emulator, String api) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null) {
            return "unidbg-default";
        }
        final int sentinel = Integer.MIN_VALUE;
        boolean configured;
        if ("getpid".equals(api)) {
            configured = config.getPid(sentinel) != sentinel;
        } else if ("getppid".equals(api)) {
            configured = config.getPpid(sentinel) != sentinel;
        } else if ("gettid".equals(api)) {
            configured = config.getTid(sentinel) != sentinel;
        } else if ("getuid".equals(api)) {
            configured = config.getUid(sentinel) != sentinel;
        } else if ("getgid".equals(api)) {
            configured = config.getGid(sentinel) != sentinel;
        } else if ("geteuid".equals(api)) {
            configured = config.getEuid(sentinel) != sentinel;
        } else if ("getegid".equals(api)) {
            configured = config.getEgid(sentinel) != sentinel;
        } else if ("getpgrp".equals(api) || "getpgid".equals(api)) {
            configured = config.getPgid(sentinel) != sentinel;
        } else if ("getsid".equals(api)) {
            configured = config.getSid(sentinel) != sentinel;
        } else {
            configured = false;
        }
        return configured ? "json-config" : "unidbg-default";
    }

    /**
     * Keep errno already set by {@code file.ioctl} only for configured
     * {@code SocketIO} {@code SIOCGIFHWADDR}. All other {@code ret == -1}
     * results still become {@link UnixEmulator#ENOTTY}.
     */
    protected static boolean preserveConfiguredSocketHwaddrErrno(Emulator<?> emulator,
                                                                 FileIO file, long request) {
        if (request != AndroidFileIO.SIOCGIFHWADDR) {
            return false;
        }
        if (!(file instanceof SocketIO)) {
            return false;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        return config != null && config.isNetworkInterfacesConfigured();
    }

    protected final void emitNetworkDeviceIoctl(Emulator<?> emulator, int fd, long request, long argp, int ret) {
        String requestName = networkDeviceRequestName(request);
        if (requestName == null) {
            return;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        boolean fromConfig = config != null && config.isNetworkInterfacesConfigured();
        // SocketIO emits name/format=ifreq-hwaddr/bytes on takeover; do not leak MAC or hardwareType.
        if (request == AndroidFileIO.SIOCGIFHWADDR && fromConfig) {
            return;
        }
        StringBuilder value = new StringBuilder();
        value.append("fd=").append(fd).append(",request=0x").append(Long.toHexString(request)).append(",ret=").append(ret);
        Pointer ifreq = UnidbgPointer.pointer(emulator, argp);
        if (ifreq != null) {
            try {
                String ifname = ifreq.getString(0);
                if (ifname != null && !ifname.isEmpty()) {
                    value.append(",ifname=").append(ifname);
                }
                if (request == AndroidFileIO.SIOCGIFFLAGS && ret == 0) {
                    value.append(",flags=0x").append(Integer.toHexString(ifreq.getShort(16) & 0xffff));
                } else if (request == AndroidFileIO.SIOCGIFADDR && ret == 0) {
                    value.append(",addr=").append(toHex(ifreq.getByteArray(16, 16)));
                } else if (request == AndroidFileIO.SIOCGIFHWADDR && ret == 0) {
                    int family = ifreq.getShort(16) & 0xffff;
                    value.append(",family=").append(family);
                    value.append(",mac=").append(toHex(ifreq.getByteArray(18, 6)));
                } else if (request == AndroidFileIO.SIOCGIFMTU && ret == 0) {
                    value.append(",mtu=").append(ifreq.getInt(16));
                }
            } catch (Throwable ignored) {
            }
        }
        String source;
        if (fromConfig) {
            source = "json-config";
        } else if (ret == 0) {
            source = "unidbg-default";
        } else {
            source = "fallback";
        }
        TraceEnvironmentEventSink.emit(emulator, "network_device", "ioctl(" + requestName + ")",
                value.toString(), source, "读取网卡信息 " + requestName);
    }

    private static String networkDeviceRequestName(long request) {
        if (request == AndroidFileIO.SIOCGIFFLAGS) {
            return "SIOCGIFFLAGS";
        }
        if (request == AndroidFileIO.SIOCGIFADDR) {
            return "SIOCGIFADDR";
        }
        if (request == AndroidFileIO.SIOCGIFCONF) {
            return "SIOCGIFCONF";
        }
        if (request == AndroidFileIO.SIOCGIFNAME) {
            return "SIOCGIFNAME";
        }
        if (request == AndroidFileIO.SIOCGIFHWADDR) {
            return "SIOCGIFHWADDR";
        }
        if (request == AndroidFileIO.SIOCGIFMTU) {
            return "SIOCGIFMTU";
        }
        return null;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes == null ? 0 : bytes.length * 2);
        if (bytes != null) {
            for (byte b : bytes) {
                int v = b & 0xff;
                if (v < 0x10) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(v));
            }
        }
        return sb.toString();
    }

    final int mlock(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer addr = context.getPointerArg(0);
        int len = context.getIntArg(1);
        if (log.isDebugEnabled()) {
            log.debug("mlock addr={}, len={}", addr, len);
        }
        return 0;
    }

    final int munlock(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer addr = context.getPointerArg(0);
        int len = context.getIntArg(1);
        if (log.isDebugEnabled()) {
            log.debug("munlock addr={}, len={}", addr, len);
        }
        return 0;
    }

    final long sched_setaffinity(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        int pid = context.getIntArg(0);
        int cpusetsize = context.getIntArg(1);
        Pointer mask = context.getPointerArg(2);
        if (mask != null) {
            sched_cpu_mask = mask.getByteArray(0, cpusetsize);
        }
        if (log.isDebugEnabled()) {
            log.debug(Inspector.inspectString(sched_cpu_mask, "sched_setaffinity pid=" + pid + ", cpusetsize=" + cpusetsize + ", mask=" + mask));
        }
        return 0;
    }

    /**
     * Android Bionic {@code bits/sysconf.h} name for {@code _SC_NPROCESSORS_CONF}
     * (same numeric value on ARM32 and ARM64).
     */
    public static final int SC_NPROCESSORS_CONF = 0x60;

    /**
     * Android Bionic {@code bits/sysconf.h} name for {@code _SC_NPROCESSORS_ONLN}
     * (same numeric value on ARM32 and ARM64).
     */
    public static final int SC_NPROCESSORS_ONLN = 0x61;

    /**
     * Config-backed {@code sysconf(name)} for processor counts only.
     * Reads {@code name} from arg0. Returns configured count when the matching
     * {@code linux.cpu} field is present; {@code null} when not handled (caller keeps
     * prior libc/syscall path; no host fallback and no sidecar).
     */
    public final Long sysconf(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        int name = context.getIntArg(0);
        return trySysconf(emulator, name);
    }

    /**
     * Config-backed {@code sysconf(name)} for {@link #SC_NPROCESSORS_CONF} /
     * {@link #SC_NPROCESSORS_ONLN} only. Other names and missing fields return {@code null}.
     */
    public final Long trySysconf(Emulator<?> emulator, int name) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isLinuxCpuConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxCpuConfig cpu = config.getLinuxCpuConfig();
        if (cpu == null) {
            return null;
        }
        if (name == SC_NPROCESSORS_CONF && cpu.isConfiguredProcessorCountConfigured()) {
            int result = cpu.getConfiguredProcessorCount();
            TraceEnvironmentEventSink.emit(emulator, "linux_cpu", "sysconf",
                    "field=configuredProcessorCount,result=" + result,
                    "json-config", "读取配置的 sysconf(_SC_NPROCESSORS_CONF) 处理器数");
            return (long) result;
        }
        if (name == SC_NPROCESSORS_ONLN && cpu.isOnlineProcessorCountConfigured()) {
            int result = cpu.getOnlineProcessorCount();
            TraceEnvironmentEventSink.emit(emulator, "linux_cpu", "sysconf",
                    "field=onlineProcessorCount,result=" + result,
                    "json-config", "读取配置的 sysconf(_SC_NPROCESSORS_ONLN) 在线处理器数");
            return (long) result;
        }
        return null;
    }

    final long sched_getaffinity(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        int pid = context.getIntArg(0);
        int cpusetsize = context.getIntArg(1);
        Pointer mask = context.getPointerArg(2);

        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.isLinuxCpuConfigured()) {
            // Configured affinity ignores stateful sched_cpu_mask.
            TraceEnvironmentConfig.LinuxCpuConfig cpuConfig = config.getLinuxCpuConfig();
            byte[] affinityMask = cpuConfig.getAffinityMaskBytes();
            if (cpusetsize <= 0 || cpusetsize > 1024) {
                TraceEnvironmentEventSink.emit(emulator, "linux_cpu", "sched_getaffinity",
                        "pid=" + pid + ",cpusetsize=" + cpusetsize
                                + ",errno=EINVAL,reason=invalid_cpusetsize",
                        "json-config", "配置的 CPU 亲和性 cpusetsize 非法");
                return -UnixEmulator.EINVAL;
            }
            if (cpusetsize < affinityMask.length) {
                TraceEnvironmentEventSink.emit(emulator, "linux_cpu", "sched_getaffinity",
                        "pid=" + pid + ",cpusetsize=" + cpusetsize
                                + ",maskBytes=" + affinityMask.length
                                + ",errno=EINVAL,reason=cpusetsize_too_small",
                        "json-config", "配置的 CPU 亲和性缓冲区过短");
                return -UnixEmulator.EINVAL;
            }
            if (mask == null) {
                TraceEnvironmentEventSink.emit(emulator, "linux_cpu", "sched_getaffinity",
                        "pid=" + pid + ",cpusetsize=" + cpusetsize
                                + ",errno=EFAULT,reason=null_mask",
                        "json-config", "配置的 CPU 亲和性 mask 指针为空");
                return -UnixEmulator.EFAULT;
            }
            byte[] out = new byte[cpusetsize];
            System.arraycopy(affinityMask, 0, out, 0, affinityMask.length);
            mask.write(0, out, 0, cpusetsize);
            TraceEnvironmentEventSink.emit(emulator, "linux_cpu", "sched_getaffinity",
                    "pid=" + pid + ",cpusetsize=" + cpusetsize
                            + ",maskHex=" + formatAffinityMaskHex(affinityMask),
                    "json-config", "读取配置的 CPU 亲和性掩码");
            return cpusetsize;
        }

        int ret = 0;
        if (mask != null && sched_cpu_mask != null) {
            mask.write(0, sched_cpu_mask, 0, cpusetsize);
            ret = cpusetsize;
        }
        if (log.isDebugEnabled()) {
            log.debug(Inspector.inspectString(sched_cpu_mask, "sched_getaffinity pid=" + pid + ", cpusetsize=" + cpusetsize + ", mask=" + mask));
        }
        return ret;
    }

    /**
     * Hex for sidecar: at most the first 64 bytes, with total length / truncation markers when longer.
     */
    private static String formatAffinityMaskHex(byte[] mask) {
        if (mask == null) {
            return "null";
        }
        int show = Math.min(mask.length, 64);
        StringBuilder sb = new StringBuilder(show * 2 + 32);
        for (int i = 0; i < show; i++) {
            int b = mask[i] & 0xff;
            if (b < 0x10) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(b));
        }
        if (mask.length > 64) {
            sb.append(",maskBytes=").append(mask.length).append(",truncated=true");
        }
        return sb.toString();
    }

    private static final int EFD_SEMAPHORE = 1;
    private static final int EFD_NONBLOCK = IOConstants.O_NONBLOCK;
    private static final int EFD_CLOEXEC = IOConstants.O_CLOEXEC;

    final int eventfd2(Emulator<?> emulator) {
        RegisterContext ctx = emulator.getContext();
        int initval = ctx.getIntArg(0);
        int flags = ctx.getIntArg(1);
        if (log.isDebugEnabled()) {
            log.debug("eventfd2 initval={}, flags=0x{}", initval, Integer.toHexString(flags));
        }
        if ((flags & EFD_CLOEXEC) != 0) {
            throw new UnsupportedOperationException("eventfd2 flags=0x" + Integer.toHexString(flags));
        }
        boolean nonblock = (flags & EFD_NONBLOCK) != 0;
        boolean semaphore = (flags & EFD_SEMAPHORE) != 0;
        AndroidFileIO fileIO = new EventFD(initval, semaphore, nonblock);
        int minFd = this.getMinFd();
        this.fdMap.put(minFd, fileIO);
        if (verbose) {
            System.out.printf("eventfd(%d) with flags=0x%x fd=%d from %s%n", initval, flags, minFd, emulator.getContext().getLRPointer());
        }
        return minFd;
    }

    protected int sched_setscheduler(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        int pid = context.getIntArg(0);
        int policy = context.getIntArg(1);
        Pointer param = context.getPointerArg(2);
        if (log.isDebugEnabled()) {
            log.debug("sched_setscheduler pid={}, policy={}, param={}", pid, policy, param);
        }
        return 0;
    }

    protected int getcwd(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        UnidbgPointer buf = context.getPointerArg(0);
        int size = context.getIntArg(1);
        File workDir = emulator.getFileSystem().createWorkDir();
        String path = workDir.getPath();
        if (log.isDebugEnabled()) {
            log.debug("getcwd buf={}, size={}, path={}", buf, size, path);
        }
        buf.setString(0, ".");
        return (int) buf.peer;
    }

    private static final int SCHED_OTHER = 0;

    protected int sched_getscheduler(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        int pid = context.getIntArg(0);
        if (log.isDebugEnabled()) {
            log.debug("sched_getscheduler pid={}", pid);
        }
        return SCHED_OTHER;
    }

    protected int sched_getparam(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        int pid = context.getIntArg(0);
        Pointer param = context.getPointerArg(1);
        if (log.isDebugEnabled()) {
            log.debug("sched_getparam pid={}, param={}", pid, param);
        }
        param.setInt(0, ANDROID_PRIORITY_NORMAL);
        return 0;
    }

    protected int sched_yield(Emulator<AndroidFileIO> emulator) {
        if (log.isDebugEnabled()) {
            log.debug("sched_yield");
        }
        if (emulator.getThreadDispatcher().getTaskCount() <= 1) {
            return 0;
        } else {
            throw new ThreadContextSwitchException().setReturnValue(0);
        }
    }

    private static final int ANDROID_PRIORITY_NORMAL = 0; /* most threads run at normal priority */
    /** Linux {@code PRIO_PROCESS} ({@code sys/resource.h}). */
    private static final int PRIO_PROCESS = 0;

    protected int getpriority(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        int which = context.getIntArg(0);
        int who = context.getIntArg(1);
        if (log.isDebugEnabled()) {
            log.debug("getpriority which={}, who={}", which, who);
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.isLinuxProcConfigured()
                && which == PRIO_PROCESS
                && (who == 0 || who == emulator.getPid())) {
            TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
            if (proc != null && proc.isNiceConfigured()) {
                int nice = proc.getNice();
                // Linux raw getpriority success is 20-nice (40..1 for nice -20..19).
                // Bionic exported getpriority() converts back with 20-rawResult.
                int rawResult = 20 - nice;
                TraceEnvironmentEventSink.emit(emulator, "linux_proc", "getpriority",
                        "field=nice,nice=" + nice + ",rawResult=" + rawResult
                                + ",which=" + which + ",who=" + who,
                        "json-config",
                        "读取配置的进程 nice 值（原始系统调用编码 20-nice；Bionic exported getpriority 再转回用户态 nice）");
                return rawResult;
            }
        }
        return ANDROID_PRIORITY_NORMAL;
    }

    /**
     * ARM32 NR 116 / ARM64 NR 179 {@code sysinfo}. Missing {@code linux.sysinfo} keeps
     * the historical all-zero struct and emits no sidecar. Configured values never come
     * from the host or {@code /proc/meminfo}.
     */
    protected int sysinfo(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer info = context.getPointerArg(0);
        if (log.isDebugEnabled()) {
            log.debug("sysinfo info={}", info);
        }
        if (info == null) {
            emulator.getMemory().setErrno(UnixEmulator.EFAULT);
            return -1;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        TraceEnvironmentConfig.LinuxSysinfoConfig sys = (config != null
                && config.isLinuxSysinfoConfigured()) ? config.getLinuxSysinfoConfig() : null;
        if (sys != null && emulator.is32Bit() && !sys.fitsArm32NativeFields()) {
            emulator.getMemory().setErrno(UnixEmulator.EINVAL);
            return -1;
        }
        // Zero the native struct first so alignment holes (ARM64 pad→totalhigh) stay 0.
        int structSize = emulator.is64Bit()
                ? UnidbgStructure.calculateSize(SysInfo64.class)
                : UnidbgStructure.calculateSize(SysInfo32.class);
        info.write(0, new byte[structSize], 0, structSize);
        if (emulator.is64Bit()) {
            SysInfo64 packed = new SysInfo64(info);
            if (sys != null) {
                applySysinfo64(packed, sys);
            }
            packed.pack();
        } else {
            SysInfo32 packed = new SysInfo32(info);
            if (sys != null) {
                applySysinfo32(packed, sys);
            }
            packed.pack();
        }
        if (sys != null) {
            long[] loads = sys.getLoads();
            TraceEnvironmentEventSink.emit(emulator, "linux_sys", "sysinfo",
                    "uptime=" + sys.getUptime()
                            + ",loads=" + loads[0] + "/" + loads[1] + "/" + loads[2]
                            + ",totalRam=" + sys.getTotalRam()
                            + ",freeRam=" + sys.getFreeRam()
                            + ",sharedRam=" + sys.getSharedRam()
                            + ",bufferRam=" + sys.getBufferRam()
                            + ",totalSwap=" + sys.getTotalSwap()
                            + ",freeSwap=" + sys.getFreeSwap()
                            + ",procs=" + sys.getProcs()
                            + ",memUnit=" + sys.getMemUnit(),
                    "json-config",
                    "读取配置的 sysinfo");
        }
        return 0;
    }

    private static void applySysinfo32(SysInfo32 packed, TraceEnvironmentConfig.LinuxSysinfoConfig sys) {
        if (!sys.fitsArm32NativeFields()) {
            throw new IllegalStateException("linux.sysinfo exceeds ARM32 native field range");
        }
        long[] loads = sys.getLoads();
        packed.uptime = toArm32Signed32(sys.getUptime());
        packed.loads[0] = toArm32Unsigned32(loads[0]);
        packed.loads[1] = toArm32Unsigned32(loads[1]);
        packed.loads[2] = toArm32Unsigned32(loads[2]);
        packed.totalRam = toArm32Unsigned32(sys.getTotalRam());
        packed.freeRam = toArm32Unsigned32(sys.getFreeRam());
        packed.sharedRam = toArm32Unsigned32(sys.getSharedRam());
        packed.bufferRam = toArm32Unsigned32(sys.getBufferRam());
        packed.totalSwap = toArm32Unsigned32(sys.getTotalSwap());
        packed.freeSwap = toArm32Unsigned32(sys.getFreeSwap());
        packed.procs = (short) sys.getProcs();
        packed.pad = 0;
        packed.totalHigh = 0;
        packed.freeHigh = 0;
        packed.mem_unit = toArm32Unsigned32(sys.getMemUnit());
    }

    private static void applySysinfo64(SysInfo64 packed, TraceEnvironmentConfig.LinuxSysinfoConfig sys) {
        packed.uptime = sys.getUptime();
        packed.loads = sys.getLoads();
        packed.totalRam = sys.getTotalRam();
        packed.freeRam = sys.getFreeRam();
        packed.sharedRam = sys.getSharedRam();
        packed.bufferRam = sys.getBufferRam();
        packed.totalSwap = sys.getTotalSwap();
        packed.freeSwap = sys.getFreeSwap();
        packed.procs = (short) sys.getProcs();
        packed.pad = 0;
        packed.totalHigh = 0;
        packed.freeHigh = 0;
        packed.mem_unit = toArm32Unsigned32(sys.getMemUnit());
    }

    /** Exact signed 32-bit; rejects overflow instead of narrowing. */
    private static int toArm32Signed32(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalStateException("linux.sysinfo value exceeds ARM32 signed 32-bit: " + value);
        }
        return (int) value;
    }

    /** Exact unsigned 32-bit bit pattern; rejects values outside {@code 0..4294967295}. */
    private static int toArm32Unsigned32(long value) {
        if (!TraceEnvironmentConfig.fitsArm32Unsigned32(value)) {
            throw new IllegalStateException("linux.sysinfo value exceeds ARM32 unsigned 32-bit: " + value);
        }
        return (int) value;
    }

    protected int setpriority(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        int which = context.getIntArg(0);
        int who = context.getIntArg(1);
        int prio = context.getIntArg(2);
        if (log.isDebugEnabled()) {
            log.debug("setpriority which={}, who={}, prio={}", which, who, prio);
        }
        return 0;
    }

    private static final int SIG_BLOCK = 0;
    private static final int SIG_UNBLOCK = 1;
    private static final int SIG_SETMASK = 2;

    @Override
    protected int sigprocmask(Emulator<?> emulator, int how, Pointer set, Pointer oldset) {
        Task task = emulator.get(Task.TASK_KEY);
        SignalOps signalOps = task.isMainThread() ? emulator.getThreadDispatcher() : task;
        SigSet old = signalOps.getSigMaskSet();
        if (oldset != null && old != null) {
            if (emulator.is32Bit()) {
                oldset.setInt(0, (int) old.getMask());
            } else {
                oldset.setLong(0, old.getMask());
            }
        }
        if (set == null) {
            return 0;
        }
        long mask = emulator.is32Bit() ? set.getInt(0) : set.getLong(0);
        switch (how) {
            case SIG_BLOCK:
                if (old == null) {
                    SigSet sigSet = new UnixSigSet(mask);
                    SigSet sigPendingSet = new UnixSigSet(0);
                    signalOps.setSigMaskSet(sigSet);
                    signalOps.setSigPendingSet(sigPendingSet);
                } else {
                    old.blockSigSet(mask);
                }
                return 0;
            case SIG_UNBLOCK:
                if (old != null) {
                    old.unblockSigSet(mask);
                }
                return 0;
            case SIG_SETMASK:
                SigSet sigSet = new UnixSigSet(mask);
                SigSet sigPendingSet = new UnixSigSet(0);
                signalOps.setSigMaskSet(sigSet);
                signalOps.setSigPendingSet(sigPendingSet);
                return 0;
        }
        return super.sigprocmask(emulator, how, set, oldset);
    }

    protected int rt_sigpending(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer set = context.getPointerArg(0);
        if (log.isDebugEnabled()) {
            log.debug("rt_sigpending set={}", set);
        }
        Task task = emulator.get(Task.TASK_KEY);
        SignalOps signalOps = task.isMainThread() ? emulator.getThreadDispatcher() : task;
        SigSet sigSet = signalOps.getSigPendingSet();
        if (set != null && sigSet != null) {
            if (emulator.is32Bit()) {
                set.setInt(0, (int) sigSet.getMask());
            } else {
                set.setLong(0, sigSet.getMask());
            }
        }
        return 0;
    }

    private static final int FUTEX_CMD_MASK = 0x7f;
    private static final int FUTEX_PRIVATE_FLAG = 0x80;
    private static final int MUTEX_SHARED_MASK = 0x2000;
    private static final int MUTEX_TYPE_MASK = 0xc000;
    private static final int FUTEX_WAIT = 0;
    private static final int FUTEX_WAKE = 1;
    private static final int FUTEX_FD = 2;
    private static final int FUTEX_REQUEUE = 3;
    private static final int FUTEX_CMP_REQUEUE = 4;

    public static final int ETIMEDOUT = 110;

    protected int futex(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer uaddr = context.getPointerArg(0);
        int futex_op = context.getIntArg(1);
        int val = context.getIntArg(2);
        int old = uaddr.getInt(0);
        boolean isPrivate = (futex_op & FUTEX_PRIVATE_FLAG) != 0;
        int cmd = futex_op & FUTEX_CMD_MASK;
        if (log.isDebugEnabled()) {
            log.debug("futex uaddr={}, isPrivate={}, cmd={}, val=0x{}, old=0x{}, LR={}", uaddr, isPrivate, cmd, Integer.toHexString(val), Integer.toHexString(old), context.getLRPointer());
        }

        Task task = emulator.get(Task.TASK_KEY);
        switch (cmd) {
            case FUTEX_WAIT:
                if (old != val) {
                    return -UnixEmulator.EAGAIN;
                }
                Pointer timeout = context.getPointerArg(3);
                TimeSpec timeSpec = timeout == null ? null : TimeSpec.createTimeSpec(emulator, timeout);
                int mtype = val & MUTEX_TYPE_MASK;
                int shared = val & MUTEX_SHARED_MASK;
                if (log.isDebugEnabled()) {
                    log.debug("futex FUTEX_WAIT mtype=0x{}, shared={}, timeSpec={}, test={}, task={}", Integer.toHexString(mtype), shared, timeSpec, mtype | shared, task);
                }
                RunnableTask runningTask = emulator.getThreadDispatcher().getRunningTask();
                if (threadDispatcherEnabled && runningTask != null) {
                    if (timeSpec == null) {
                        runningTask.setWaiter(emulator, new FutexIndefinitelyWaiter(uaddr, val));
                    } else {
                        runningTask.setWaiter(emulator, new FutexNanoSleepWaiter(uaddr, val, timeSpec));
                    }
                    throw new ThreadContextSwitchException();
                }
                if (threadDispatcherEnabled && emulator.getThreadDispatcher().getTaskCount() > 1) {
                    throw new ThreadContextSwitchException().setReturnValue(-ETIMEDOUT);
                } else {
                    return 0;
                }
            case FUTEX_WAKE:
                if (log.isDebugEnabled()) {
                    log.debug("futex FUTEX_WAKE val=0x{}, old={}, task={}", Integer.toHexString(val), old, task);
                }
                if (emulator.getThreadDispatcher().getTaskCount() <= 1) {
                    return 0;
                }
                int count = 0;
                for (Task t : emulator.getThreadDispatcher().getTaskList()) {
                    Waiter waiter = t.getWaiter();
                    if (waiter instanceof FutexWaiter) {
                        if (((FutexWaiter) waiter).wakeUp(uaddr)) {
                            if (++count >= val) {
                                break;
                            }
                        }
                    }
                }
                if (count > 0) {
                    throw new ThreadContextSwitchException().setReturnValue(count);
                }
                if (threadDispatcherEnabled && task != null) {
                    throw new ThreadContextSwitchException().setReturnValue(1);
                }
                return 0;
            case FUTEX_CMP_REQUEUE:
                if (log.isDebugEnabled()) {
                    log.debug("futex FUTEX_CMP_REQUEUE val=0x{}, old={}, task={}", Integer.toHexString(val), old, task);
                }
                return 0;
            default:
                if (log.isDebugEnabled()) {
                    emulator.attach().debug("Unsupported futex_op=0x" + Integer.toHexString(futex_op));
                }
                throw new AbstractMethodError("futex_op=0x" + Integer.toHexString(futex_op));
        }
    }

    protected int rt_sigtimedwait(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer set = context.getPointerArg(0);
        Pointer info = context.getPointerArg(1);
        Pointer timeout = context.getPointerArg(2);
        int sigsetsize = context.getIntArg(3);
        long mask = emulator.is32Bit() ? set.getInt(0) : set.getLong(0);
        Task task = emulator.get(Task.TASK_KEY);
        SigSet sigSet = new UnixSigSet(mask);
        SignalOps signalOps = task.isMainThread() ? emulator.getThreadDispatcher() : task;
        SigSet sigPendingSet = signalOps.getSigPendingSet();
        if (sigPendingSet != null) {
            for (Integer signum : sigSet) {
                if (sigPendingSet.containsSigNumber(signum)) {
                    sigPendingSet.removeSigNumber(signum);
                    return signum;
                }
            }
        }
        if (!task.isMainThread()) {
            throw new ThreadContextSwitchException().setReturnValue(-UnixEmulator.EINTR);
        }
        log.info("rt_sigtimedwait set={}, info={}, timeout={}, sigsetsize={}, sigSet={}, task={}", set, info, timeout, sigsetsize, sigSet, task);
        Logger log = LoggerFactory.getLogger(AbstractEmulator.class);
        if (log.isDebugEnabled()) {
            emulator.attach().debug("rt_sigtimedwait sigSet=" + sigSet);
        }
        return 0;
    }

    protected int rt_sigqueue(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        int tgid = context.getIntArg(0);
        int sig = context.getIntArg(1);
        UnidbgPointer info = context.getPointerArg(2);
        if (log.isDebugEnabled()) {
            log.debug("rt_sigqueue tgid={}, sig={}", tgid, sig);
        }
        Task task = emulator.get(Task.TASK_KEY);
        // 检查pid是有匹配进程存在
        if (!(tgid == 0 || tgid == -1 || Math.abs(tgid) == emulator.getPid())) {
            return -UnixEmulator.ESRCH;
        }
        // 检查进程是否存在, 无需发送信号
        if (sig == 0) {
            return 0;
        }
        if (sig < 0 || sig > 64) {
            return -UnixEmulator.EINVAL;
        }
        if (task != null) {
            SigAction sigAction = sigActionMap.get(sig);
            return processSignal(emulator.getThreadDispatcher(), sig, task, sigAction, info);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    protected FileResult<AndroidFileIO> createFdDir(int oflags, String pathname) {
        List<DirectoryFileIO.DirectoryEntry> list = new ArrayList<>();
        for (Map.Entry<Integer, AndroidFileIO> entry : fdMap.entrySet()) {
            list.add(new DirectoryFileIO.DirectoryEntry(DirectoryFileIO.DirentType.DT_LNK, entry.getKey().toString()));
        }
        return FileResult.<AndroidFileIO>success(new DirectoryFileIO(oflags, pathname, list.toArray(new DirectoryFileIO.DirectoryEntry[0])));
    }

    @Override
    protected FileResult<AndroidFileIO> createTaskDir(Emulator<AndroidFileIO> emulator, int oflags, String pathname) {
        return FileResult.<AndroidFileIO>success(new DirectoryFileIO(oflags, pathname, new DirectoryFileIO.DirectoryEntry(false, Integer.toString(emulator.getPid()))));
    }

    protected long statfs64(Emulator<AndroidFileIO> emulator, String path, Pointer buf) {
        FileResult<AndroidFileIO> result = resolve(emulator, path, IOConstants.O_RDONLY);
        if (result == null) {
            log.info("statfs64 buf={}, path={}", buf, path);
            emulator.getMemory().setErrno(UnixEmulator.ENOENT);
            return -1;
        }
        if (result.isSuccess()) {
            StatFS statFS = emulator.is64Bit() ? new StatFS64(buf) : new StatFS32(buf);
            int ret = result.io.statfs(statFS);
            if (ret != 0) {
                log.info("statfs64 buf={}, path={}, ret={}", buf, path, ret);
            } else {
                // Overlay filesystem.statfs only after underlying FileIO.statfs succeeds.
                // Config does not create missing paths; apply packs once and emits one sidecar.
                ConfiguredFileStatFs.apply(emulator, path, statFS);
                if (verbose) {
                    System.out.printf("File statfs '%s' from %s%n", result.io, emulator.getContext().getLRPointer());
                }
                if (log.isDebugEnabled()) {
                    log.debug("statfs64 buf={}, path={}", buf, path);
                }
            }
            return ret;
        } else {
            log.info("statfs64 buf={}, path={}", buf, path);
            emulator.getMemory().setErrno(result.errno);
            return -1;
        }
    }

    /**
     * FD {@code fstatfs}/{@code fstatfs64}: look up {@code fdMap}, then overlay
     * {@code filesystem.statfs} using {@link AndroidFileIO#getPath()} after a successful
     * {@link AndroidFileIO#statfs}. Config does not create missing fds and does not read the host.
     * Null/empty virtual paths skip overlay and sidecar but still return the underlying result.
     */
    protected long fstatfs64(Emulator<AndroidFileIO> emulator, int fd, Pointer buf) {
        AndroidFileIO io = fdMap.get(fd);
        if (io == null) {
            if (log.isDebugEnabled()) {
                log.debug("fstatfs64 fd={}, buf={}, errno=" + UnixEmulator.EBADF, fd, buf);
            }
            emulator.getMemory().setErrno(UnixEmulator.EBADF);
            return -1;
        }
        StatFS statFS = emulator.is64Bit() ? new StatFS64(buf) : new StatFS32(buf);
        int ret = io.statfs(statFS);
        if (ret != 0) {
            log.info("fstatfs64 fd={}, buf={}, ret={}", fd, buf, ret);
        } else {
            String path = io.getPath();
            if (path != null && !path.isEmpty()) {
                ConfiguredFileStatFs.apply(emulator, path, statFS, "fstatfs");
            }
            if (verbose) {
                System.out.printf("File fstatfs '%s' from %s%n", io, emulator.getContext().getLRPointer());
            }
            if (log.isDebugEnabled()) {
                log.debug("fstatfs64 fd={}, buf={}", fd, buf);
            }
        }
        return ret;
    }

    protected int pipe2(Emulator<?> emulator) {
        try {
            RegisterContext context = emulator.getContext();
            Pointer pipefd = context.getPointerArg(0);
            int flags = context.getIntArg(1);
            int writefd = getMinFd();
            Pair<AndroidFileIO, AndroidFileIO> pair = getPipePair(emulator, writefd);
            this.fdMap.put(writefd, pair.getLeft());
            int readfd = getMinFd();
            this.fdMap.put(readfd, pair.getRight());
            pipefd.setInt(0, readfd);
            pipefd.setInt(4, writefd);
            if (log.isDebugEnabled()) {
                log.debug("pipe2 pipefd={}, flags=0x{}, readfd={}, writefd={}", pipefd, flags, readfd, writefd);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return 0;
    }

    protected Pair<AndroidFileIO, AndroidFileIO> getPipePair(Emulator<?> emulator, int writefd) throws IOException {
        PipedInputStream inputStream = new PipedInputStream();
        PipedOutputStream outputStream = new PipedOutputStream(inputStream);
        AndroidFileIO writeIO = new PipedWriteFileIO(outputStream, writefd);
        AndroidFileIO readIO = new PipedReadFileIO(inputStream, writefd);
        log.info("Return default pipe pair.");
        return new Pair<>(writeIO, readIO);
    }

    protected int fchmodat(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        int dirfd = context.getIntArg(0);
        Pointer pathname_p = context.getPointerArg(1);
        int mode = context.getIntArg(2);
        int flags = context.getIntArg(3);
        String pathname = pathname_p.getString(0);
        if (log.isDebugEnabled()) {
            log.debug("fchmodat dirfd={}, pathname={}, mode=0x{}, flags=0x{}", dirfd, pathname, Integer.toHexString(mode), Integer.toHexString(flags));
        }
        return 0;
    }

    protected int fchownat(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        int dirfd = context.getIntArg(0);
        Pointer pathname_p = context.getPointerArg(1);
        int owner = context.getIntArg(2);
        int group = context.getIntArg(3);
        int flags = context.getIntArg(4);
        String pathname = pathname_p.getString(0);
        if (log.isDebugEnabled()) {
            log.debug("fchownat dirfd={}, pathname={}, owner={}, group={}, flags=0x{}", dirfd, pathname, owner, group, Integer.toHexString(flags));
        }
        return 0;
    }

    protected int mkdirat(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        int dirfd = context.getIntArg(0);
        Pointer pathname_p = context.getPointerArg(1);
        int mode = context.getIntArg(2);
        String pathname = pathname_p.getString(0);
        if (log.isDebugEnabled()) {
            log.debug("mkdirat dirfd={}, pathname={}, mode={}", dirfd, pathname, Integer.toHexString(mode));
        }
        if (dirfd != IO.AT_FDCWD) {
            throw new BackendException();
        }
        if (emulator.getFileSystem().mkdir(pathname, mode)) {
            if (log.isDebugEnabled()) {
                log.debug("mkdir pathname={}, mode={}", pathname, mode);
            }
            return 0;
        } else {
            log.info("mkdir pathname={}, mode={}", pathname, mode);
            emulator.getMemory().setErrno(UnixEmulator.EACCES);
            return -1;
        }
    }

    final int select(int nfds, Pointer checkfds, Pointer clearfds, boolean checkRead) {
        int count = 0;
        for (int i = 0; i < nfds; i++) {
            int mask = checkfds.getInt(i / 32);
            if (((mask >> i) & 1) == 1) {
                AndroidFileIO io = fdMap.get(i);
                if (!checkRead || io.canRead()) {
                    count++;
                } else {
                    mask &= ~(1 << i);
                    checkfds.setInt(i / 32, mask);
                }
            }
        }
        if (count > 0) {
            if (clearfds != null) {
                for (int i = 0; i < nfds; i++) {
                    clearfds.setInt(i / 32, 0);
                }
            }
        }
        return count;
    }

    protected int sigaltstack(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer ss = context.getPointerArg(0);
        Pointer old_ss = context.getPointerArg(1);
        if (log.isDebugEnabled()) {
            log.debug("sigaltstack ss={}, old_ss={}", ss, old_ss);
        }
        return 0;
    }

    protected int renameat(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        int olddirfd = context.getIntArg(0);
        String oldpath = context.getPointerArg(1).getString(0);
        int newdirfd = context.getIntArg(2);
        String newpath = context.getPointerArg(3).getString(0);
        int ret = emulator.getFileSystem().rename(oldpath, newpath);
        if (ret != 0) {
            log.info("renameat olddirfd={}, oldpath={}, newdirfd={}, newpath={}", olddirfd, oldpath, newdirfd, newpath);
        } else {
            log.debug("renameat olddirfd={}, oldpath={}, newdirfd={}, newpath={}", olddirfd, oldpath, newdirfd, newpath);
        }
        return 0;
    }

    protected int unlinkat(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        int dirfd = context.getIntArg(0);
        Pointer pathname = context.getPointerArg(1);
        int flags = context.getIntArg(2);
        emulator.getFileSystem().unlink(pathname.getString(0));
        if (log.isDebugEnabled()) {
            log.info("unlinkat dirfd={}, pathname={}, flags={}", dirfd, pathname.getString(0), flags);
        }
        return 0;
    }

    protected void exit(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        int status = context.getIntArg(0);
        Task task = emulator.get(Task.TASK_KEY);
        if (task instanceof ThreadTask) {
            ThreadTask threadTask = (ThreadTask) task;
            threadTask.setExitStatus(status);
            throw new ThreadContextSwitchException().setReturnValue(0);
        }
        System.out.println("exit status=" + status);
        if (LoggerFactory.getLogger(AbstractEmulator.class).isDebugEnabled()) {
            emulator.attach().debug("exit status=" + status);
        }
        emulator.getBackend().emu_stop();
    }

    private static final int SIGKILL = 9;
    private static final int SIGSTOP = 19;
    private static final int SIG_ERR = -1;

    private final Map<Integer, SigAction> sigActionMap = new HashMap<>();

    @Override
    public MainTask createSignalHandlerTask(Emulator<?> emulator, int sig) {
        SigAction action = sigActionMap.get(sig);
        if (action != null) {
            return new SignalFunction(emulator, sig, action);
        }
        return super.createSignalHandlerTask(emulator, sig);
    }

    @Override
    protected int sigaction(Emulator<?> emulator, int signum, Pointer act, Pointer oldact) {
        SigAction action = SigAction.create(emulator, act);
        SigAction oldAction = SigAction.create(emulator, oldact);
        if (log.isDebugEnabled()) {
            log.debug("sigaction signum={}, action={}, oldAction={}", signum, action, oldAction);
        }
        if (SIGKILL == signum || SIGSTOP == signum) {
            if (oldAction != null) {
                oldAction.setSaHandler(SIG_ERR);
                oldAction.pack();
            }
            return -UnixEmulator.EINVAL;
        }
        SigAction lastAction = sigActionMap.put(signum, action);
        if (oldAction != null) {
            if (lastAction == null) {
                oldact.write(0, new byte[oldAction.size()], 0, oldAction.size());
            } else {
                oldAction.setSaHandler(lastAction.getSaHandler());
                oldAction.setSaRestorer(lastAction.getSaRestorer());
                oldAction.setFlags(lastAction.getFlags());
                oldAction.setMask(lastAction.getMask());
                oldAction.pack();
            }
        }
        return 0;
    }

    protected int kill(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        int pid = context.getIntArg(0);
        int sig = context.getIntArg(1);
        if (log.isDebugEnabled()) {
            log.debug("kill pid={}, sig={}", pid, sig);
        }
        if (sig == 0) {
            return 0;
        }
        if (sig < 0 || sig > 64) {
            return -UnixEmulator.EINVAL;
        }
        Task task = emulator.get(Task.TASK_KEY);
        if ((pid == 0 || pid == emulator.getPid()) && task != null) {
            SigAction action = sigActionMap.get(sig);
            return processSignal(emulator.getThreadDispatcher(), sig, task, action, null);
        }
        throw new UnsupportedOperationException("kill pid=" + pid + ", sig=" + sig + ", LR=" + context.getLRPointer());
    }

    private int processSignal(ThreadDispatcher threadDispatcher, int sig, Task task, SigAction action, Pointer sig_info) {
        if (action != null) {
            SignalOps signalOps = task.isMainThread() ? threadDispatcher : task;
            SigSet sigMaskSet = signalOps.getSigMaskSet();
            SigSet sigPendingSet = signalOps.getSigPendingSet();
            if (sigMaskSet == null || !sigMaskSet.containsSigNumber(sig)) {
                task.addSignalTask(new SignalTask(sig, action, sig_info));
                throw new ThreadContextSwitchException().setReturnValue(0);
            } else if (sigPendingSet != null) {
                sigPendingSet.addSigNumber(sig);
            }
        }
        return 0;
    }

    protected int tgkill(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        int tgid = context.getIntArg(0);
        int tid = context.getIntArg(1);
        int sig = context.getIntArg(2);
        if (log.isDebugEnabled()) {
            log.debug("tgkill tgid={}, tid={}, sig={}", tgid, tid, sig);
        }
        if (sig == 0) {
            return 0;
        }
        if (sig < 0 || sig > 64) {
            return -UnixEmulator.EINVAL;
        }
        SigAction action = sigActionMap.get(sig);
        if (threadDispatcherEnabled &&
                emulator.getThreadDispatcher().sendSignal(tid, sig, action == null || action.getSaHandler() == 0L ? null : new SignalTask(sig, action))) {
            throw new ThreadContextSwitchException().setReturnValue(0);
        }
        return 0;
    }

    protected int set_tid_address(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer tidptr = context.getPointerArg(0);
        if (log.isDebugEnabled()) {
            log.debug("set_tid_address tidptr={}", tidptr);
        }
        Task task = emulator.get(Task.TASK_KEY);
        if (task instanceof MarshmallowThread) {
            MarshmallowThread thread = (MarshmallowThread) task;
            thread.set_tid_address(tidptr);
        }
        return 0;
    }

    private int threadId;

    protected final int incrementThreadId(Emulator<?> emulator) {
        if (threadId == 0) {
            threadId = emulator.getPid();
        }
        return (++threadId) & 0xffff; // http://androidxref.com/6.0.1_r10/xref/bionic/libc/bionic/pthread_mutex.cpp#215
    }

    protected int nanosleep(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer req = context.getPointerArg(0);
        Pointer rem = context.getPointerArg(1);
        TimeSpec timeSpec = TimeSpec.createTimeSpec(emulator, req);
        if (log.isDebugEnabled()) {
            log.debug("nanosleep req={}, rem={}, timeSpec={}", req, rem, timeSpec);
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.getMonotonicNanos() != null) {
            long ms = timeSpec.toMillis();
            if (ms > 0L) {
                config.addVirtualTime(ms);
            }
            return 0;
        }
        RunnableTask runningTask = emulator.getThreadDispatcher().getRunningTask();
        if (threadDispatcherEnabled && runningTask != null) {
            runningTask.setWaiter(emulator, new NanoSleepWaiter(emulator, rem, timeSpec));
            throw new ThreadContextSwitchException().setReturnValue(0);
        } else {
            try {
                java.lang.Thread.sleep(timeSpec.toMillis());
            } catch (InterruptedException ignored) {
            }
            return 0;
        }
    }

    protected int fallocate(Emulator<AndroidFileIO> emulator) {
        RegisterContext context = emulator.getContext();
        int fd = context.getIntArg(0);
        int mode = context.getIntArg(1);
        int offset = context.getIntArg(2);
        int len = context.getIntArg(3);
        if (log.isDebugEnabled()) {
            log.debug("fallocate fd={}, mode=0x{}, offset={}, len={}", fd, Integer.toHexString(mode), offset, len);
        }
        return 0;
    }

    /**
     * {@code mincore}: when {@code linux.mincore} is configured, write one byte per page
     * ({@code 1} if {@code resident} is true, else {@code 0}). Missing node does not take over.
     */
    public Integer tryMincore(Emulator<?> emulator) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isLinuxMincoreConfigured()) {
            return null;
        }
        RegisterContext context = emulator.getContext();
        long start = context.getLongArg(0);
        long length = context.getLongArg(1);
        Pointer vec = context.getPointerArg(2);
        if (vec == null) {
            emulator.getMemory().setErrno(UnixEmulator.EFAULT);
            return Integer.valueOf(-1);
        }
        int pageSize = emulator.getPageAlign();
        if (pageSize <= 0) {
            pageSize = 4096;
        }
        long pages = (length + pageSize - 1) / pageSize;
        byte fill = (byte) (config.isLinuxMincoreResident() ? 1 : 0);
        for (long i = 0; i < pages; i++) {
            vec.setByte(i, fill);
        }
        TraceEnvironmentEventSink.emit(emulator, "linux_sys", "mincore",
                "pages=" + pages + ",resident=" + config.isLinuxMincoreResident(),
                "json-config", "写入配置的 mincore 驻留标记");
        return Integer.valueOf(0);
    }

}

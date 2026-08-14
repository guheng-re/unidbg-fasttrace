package com.github.unidbg.file.linux;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.ARMEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Pure static renderer for proc status, cmdline, cgroup, stat, statm, io, boot_id,
 * random_uuid, entropy_avail, poolsize, write_wakeup_threshold, urandom_min_reseed_secs,
 * oom_score_adj, oom_score, oom_adj, SELinux attr/current, comm, wchan, limits, environ from
 * {@code linux.proc} / {@code linux.environ} plus {@code process} identity (with emulator
 * fallbacks), optional {@code linux.uname} → {@code /proc/sys/kernel/hostname|osrelease|
 * version|domainname|ostype}, and optional one-line {@code limits} derivation from
 * {@code linux.rlimits.nofile} when {@code linux.proc.limits} is absent. No sidecar, no filesystem I/O.
 */
public final class ConfiguredProcFiles {

    private static final String RSSLIM_LITERAL = "18446744073709551615";
    /** Exact path for kernel boot_id (global; not self/pid). */
    public static final String BOOT_ID_PATH = "/proc/sys/kernel/random/boot_id";
    /** Exact path for kernel random uuid fixed marker (global; not self/pid). */
    public static final String RANDOM_UUID_PATH = "/proc/sys/kernel/random/uuid";
    /** Exact path for kernel entropy_avail (global; not self/pid). */
    public static final String ENTROPY_AVAIL_PATH = "/proc/sys/kernel/random/entropy_avail";
    /** Exact path for kernel random poolsize (global; not self/pid). */
    public static final String RANDOM_POOLSIZE_PATH = "/proc/sys/kernel/random/poolsize";
    /** Exact path for kernel write_wakeup_threshold (global; not self/pid). */
    public static final String WRITE_WAKEUP_THRESHOLD_PATH =
            "/proc/sys/kernel/random/write_wakeup_threshold";
    /** Exact path for kernel urandom_min_reseed_secs (global; not self/pid). */
    public static final String URANDOM_MIN_RESEED_SECS_PATH =
            "/proc/sys/kernel/random/urandom_min_reseed_secs";
    /** Exact path mirrored from explicit {@code linux.uname.nodename}. */
    public static final String HOSTNAME_PATH = "/proc/sys/kernel/hostname";
    /** Exact path mirrored from explicit {@code linux.uname.release}. */
    public static final String OSRELEASE_PATH = "/proc/sys/kernel/osrelease";
    /** Exact path mirrored from explicit {@code linux.uname.version}. */
    public static final String KERNEL_VERSION_PATH = "/proc/sys/kernel/version";
    /** Exact path mirrored from explicit {@code linux.uname.domainname}. */
    public static final String DOMAINNAME_PATH = "/proc/sys/kernel/domainname";
    /** Exact path mirrored from explicit {@code linux.uname.sysname}. */
    public static final String OSTYPE_PATH = "/proc/sys/kernel/ostype";

    private ConfiguredProcFiles() {
    }

    /**
     * {@code /proc/sys/kernel/ostype}: UTF-8 configured sysname + LF.
     *
     * @return {@code null} when {@code linux.uname.sysname} is not explicitly present —
     *         never invents syscall fallbacks
     */
    public static byte[] renderUnameOstype(TraceEnvironmentConfig config) {
        if (config == null || !config.isUnameSysnameConfigured()) {
            return null;
        }
        String value = config.getUnameSysname(null);
        if (value == null) {
            return null;
        }
        return (value + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/sys/kernel/hostname}: UTF-8 configured nodename + LF.
     *
     * @return {@code null} when {@code linux.uname.nodename} is not explicitly present —
     *         never invents syscall fallbacks
     */
    public static byte[] renderUnameHostname(TraceEnvironmentConfig config) {
        if (config == null || !config.isUnameNodenameConfigured()) {
            return null;
        }
        String value = config.getUnameNodename(null);
        if (value == null) {
            return null;
        }
        return (value + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/sys/kernel/osrelease}: UTF-8 configured release + LF.
     *
     * @return {@code null} when {@code linux.uname.release} is not explicitly present
     */
    public static byte[] renderUnameOsrelease(TraceEnvironmentConfig config) {
        if (config == null || !config.isUnameReleaseConfigured()) {
            return null;
        }
        String value = config.getUnameRelease(null);
        if (value == null) {
            return null;
        }
        return (value + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/sys/kernel/version}: UTF-8 configured version + LF.
     * Not {@code /proc/version}; does not synthesize from other fields.
     *
     * @return {@code null} when {@code linux.uname.version} is not explicitly present
     */
    public static byte[] renderUnameVersion(TraceEnvironmentConfig config) {
        if (config == null || !config.isUnameVersionConfigured()) {
            return null;
        }
        String value = config.getUnameVersion(null);
        if (value == null) {
            return null;
        }
        return (value + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/sys/kernel/domainname}: UTF-8 configured domainname + LF.
     *
     * @return {@code null} when {@code linux.uname.domainname} is not explicitly present
     */
    public static byte[] renderUnameDomainname(TraceEnvironmentConfig config) {
        if (config == null || !config.isUnameDomainnameConfigured()) {
            return null;
        }
        String value = config.getUnameDomainname(null);
        if (value == null) {
            return null;
        }
        return (value + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/sys/kernel/random/boot_id} content: UTF-8 {@code <uuid>\n} (37 bytes).
     *
     * @return {@code null} when {@code linux.proc} is not configured or {@code bootId} key absent
     */
    public static byte[] renderBootId(TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (!proc.isBootIdConfigured()) {
            return null;
        }
        String id = proc.getBootId();
        if (id == null) {
            return null;
        }
        return (id + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/sys/kernel/random/uuid} content: UTF-8 {@code <uuid>\n} (37 bytes).
     * Fixed reproducibility marker — same bytes every render; never generates a new UUID.
     *
     * @return {@code null} when {@code linux.proc} is not configured or {@code randomUuid}
     *         key absent
     */
    public static byte[] renderRandomUuid(TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (!proc.isRandomUuidConfigured()) {
            return null;
        }
        String id = proc.getRandomUuid();
        if (id == null) {
            return null;
        }
        return (id + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/sys/kernel/random/entropy_avail} content: decimal ASCII + LF.
     * Never reads the host entropy pool; never derived from {@code randomPoolSize},
     * {@code writeWakeupThreshold}, or {@code urandomMinReseedSecs}.
     *
     * @return {@code null} when {@code linux.proc} is not configured or {@code entropyAvail}
     *         key absent
     */
    public static byte[] renderEntropyAvail(TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (!proc.isEntropyAvailConfigured()) {
            return null;
        }
        return (Integer.toString(proc.getEntropyAvail()) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/sys/kernel/random/poolsize} content: decimal ASCII + LF.
     * Never reads the host entropy pool; never derived from {@code entropyAvail},
     * {@code writeWakeupThreshold}, or {@code urandomMinReseedSecs}.
     *
     * @return {@code null} when {@code linux.proc} is not configured or {@code randomPoolSize}
     *         key absent
     */
    public static byte[] renderRandomPoolSize(TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (!proc.isRandomPoolSizeConfigured()) {
            return null;
        }
        return (Integer.toString(proc.getRandomPoolSize()) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/sys/kernel/random/write_wakeup_threshold} content: decimal ASCII + LF.
     * Never reads the host entropy pool; never derived from {@code entropyAvail},
     * {@code randomPoolSize}, or {@code urandomMinReseedSecs}.
     *
     * @return {@code null} when {@code linux.proc} is not configured or
     *         {@code writeWakeupThreshold} key absent
     */
    public static byte[] renderWriteWakeupThreshold(TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (!proc.isWriteWakeupThresholdConfigured()) {
            return null;
        }
        return (Integer.toString(proc.getWriteWakeupThreshold()) + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/sys/kernel/random/urandom_min_reseed_secs} content: decimal ASCII + LF.
     * Never reads the host entropy pool; never derived from {@code entropyAvail},
     * {@code randomPoolSize}, or {@code writeWakeupThreshold}.
     *
     * @return {@code null} when {@code linux.proc} is not configured or
     *         {@code urandomMinReseedSecs} key absent
     */
    public static byte[] renderUrandomMinReseedSecs(TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (!proc.isUrandomMinReseedSecsConfigured()) {
            return null;
        }
        return (Integer.toString(proc.getUrandomMinReseedSecs()) + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/self|pid/oom_score_adj} content: UTF-8 decimal + LF (no default).
     *
     * @return {@code null} when {@code linux.proc} is not configured or {@code oomScoreAdj}
     *         key absent — never infers a value
     */
    public static byte[] renderOomScoreAdj(TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (!proc.isOomScoreAdjConfigured()) {
            return null;
        }
        return (Integer.toString(proc.getOomScoreAdj()) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/self|pid/oom_score} content: UTF-8 decimal + LF (no default).
     * Never derived from {@code oomScoreAdj}.
     *
     * @return {@code null} when {@code linux.proc} is not configured or {@code oomScore}
     *         key absent — never infers a value
     */
    public static byte[] renderOomScore(TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (!proc.isOomScoreConfigured()) {
            return null;
        }
        return (Integer.toString(proc.getOomScore()) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Legacy {@code /proc/self|pid/oom_adj} content: UTF-8 decimal + LF (no default).
     * Never derived from {@code oomScoreAdj} or {@code oomScore}.
     *
     * @return {@code null} when {@code linux.proc} is not configured or {@code oomAdj}
     *         key absent — never infers a value
     */
    public static byte[] renderOomAdj(TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (!proc.isOomAdjConfigured()) {
            return null;
        }
        return (Integer.toString(proc.getOomAdj()) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/self|pid/attr/current} content: UTF-8 context + LF (no default).
     *
     * @return {@code null} when {@code linux.proc} is not configured or {@code selinuxContext}
     *         key absent — never infers a value
     */
    public static byte[] renderSelinuxContext(TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (!proc.isSelinuxContextConfigured()) {
            return null;
        }
        String ctx = proc.getSelinuxContext();
        if (ctx == null) {
            return null;
        }
        return (ctx + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/self|pid/comm} and matching {@code .../task/<tid>/comm} content:
     * UTF-8 task name + LF (no default). Same bytes for process-level and configured-tid
     * task paths. Never derived from processName or threadName.
     *
     * @return {@code null} when {@code linux.proc} is not configured or {@code comm}
     *         key absent — never infers a value
     */
    public static byte[] renderComm(TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (!proc.isCommConfigured()) {
            return null;
        }
        String name = proc.getComm();
        if (name == null) {
            return null;
        }
        return (name + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/self|pid/wchan} and matching {@code .../task/<tid>/wchan} content:
     * UTF-8 wait-channel marker + LF (no default). Same bytes for process-level and
     * configured-tid task paths. Fixed analysis marker only — never derived from
     * scheduler/blocking state.
     *
     * @return {@code null} when {@code linux.proc} is not configured or {@code wchan}
     *         key absent — never infers a value
     */
    public static byte[] renderWchan(TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (!proc.isWchanConfigured()) {
            return null;
        }
        String value = proc.getWchan();
        if (value == null) {
            return null;
        }
        return (value + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/self|pid/environ} content: UTF-8 entries each followed by NUL, same list as
     * {@code AndroidElfLoader} stack environ. When {@code linux.environ} is absent (or
     * {@code config} is null), uses {@link TraceEnvironmentConfig#LINUX_ENVIRON_BUILTIN_DEFAULTS};
     * when present (including {@code []}), uses the configured replacement list.
     * Never derived from host process environment. Always returns a new buffer (empty when list empty).
     */
    public static byte[] renderEnviron(TraceEnvironmentConfig config) {
        List<String> entries = config != null
                ? config.getEffectiveLinuxEnviron()
                : TraceEnvironmentConfig.LINUX_ENVIRON_BUILTIN_DEFAULTS;
        if (entries == null || entries.isEmpty()) {
            return new byte[0];
        }
        int size = 0;
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            size += entry.getBytes(StandardCharsets.UTF_8).length + 1;
        }
        if (size == 0) {
            return new byte[0];
        }
        ByteBuffer buf = ByteBuffer.allocate(size);
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            buf.put(entry.getBytes(StandardCharsets.UTF_8));
            buf.put((byte) 0);
        }
        return buf.array();
    }

    /**
     * @return {@code null} when {@code linux.proc} is not configured; otherwise a new UTF-8 buffer
     */
    public static byte[] renderStatus(Emulator<?> emulator, TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        Identity id = resolveIdentity(emulator, config);

        String name = sanitizeName(id.processName);
        String state = proc.getState();
        StringBuilder sb = new StringBuilder(256);
        sb.append("Name:\t").append(name).append('\n');
        sb.append("State:\t").append(state).append(" (").append(stateDescription(state)).append(")\n");
        sb.append("Tgid:\t").append(id.pid).append('\n');
        sb.append("Pid:\t").append(id.pid).append('\n');
        sb.append("PPid:\t").append(id.ppid).append('\n');
        sb.append("TracerPid:\t").append(proc.getTracerPid()).append('\n');
        // real effective saved fs
        // real, effective, saved_set, filesystem
        sb.append("Uid:\t").append(id.uid).append('\t').append(id.euid).append('\t')
                .append(id.euid).append('\t').append(id.euid).append('\n');
        sb.append("Gid:\t").append(id.gid).append('\t').append(id.egid).append('\t')
                .append(id.egid).append('\t').append(id.egid).append('\n');
        // Optional Groups: only when process.supplementaryGids is explicit (including []).
        // Never derived from gid/egid. Missing key keeps historical status bytes.
        if (config.isSupplementaryGidsConfigured()) {
            sb.append("Groups:\t");
            List<Integer> gids = config.getSupplementaryGids();
            for (int i = 0; i < gids.size(); i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(gids.get(i).intValue());
            }
            sb.append('\n');
        }
        sb.append("Threads:\t").append(proc.getThreadCount()).append('\n');
        long vmKb = proc.getVirtualMemoryBytes() / 1024L;
        // kB = pages * 4096 / 1024 = pages * 4; use BigInteger to avoid long overflow
        String rssKb = BigInteger.valueOf(proc.getResidentSetPages())
                .multiply(BigInteger.valueOf(4L))
                .toString();
        sb.append("VmSize:\t").append(vmKb).append(" kB\n");
        sb.append("VmRSS:\t").append(rssKb).append(" kB\n");
        // Optional SigBlk / SigIgn / SigCgt (before Cap*; status markers only —
        // no delivery/sigaction/pthread_sigmask).
        if (proc.isSignalBlockedHexConfigured()) {
            sb.append("SigBlk:\t").append(proc.getSignalBlockedHex()).append('\n');
        }
        if (proc.isSignalIgnoredHexConfigured()) {
            sb.append("SigIgn:\t").append(proc.getSignalIgnoredHex()).append('\n');
        }
        if (proc.isSignalCaughtHexConfigured()) {
            sb.append("SigCgt:\t").append(proc.getSignalCaughtHex()).append('\n');
        }
        // Optional CapInh / CapPrm / CapEff / CapBnd / CapAmb (kernel Cap* order;
        // no capget/capset/prctl ambient; fields independent).
        if (proc.isCapInheritableHexConfigured()) {
            sb.append("CapInh:\t").append(proc.getCapInheritableHex()).append('\n');
        }
        if (proc.isCapPermittedHexConfigured()) {
            sb.append("CapPrm:\t").append(proc.getCapPermittedHex()).append('\n');
        }
        if (proc.isCapEffectiveHexConfigured()) {
            sb.append("CapEff:\t").append(proc.getCapEffectiveHex()).append('\n');
        }
        if (proc.isCapBoundingHexConfigured()) {
            sb.append("CapBnd:\t").append(proc.getCapBoundingHex()).append('\n');
        }
        if (proc.isCapAmbientHexConfigured()) {
            sb.append("CapAmb:\t").append(proc.getCapAmbientHex()).append('\n');
        }
        // Optional prctl-aligned fields: only when explicitly configured (no defaults/derivation).
        if (proc.isSeccompModeConfigured()) {
            sb.append("Seccomp:\t").append(proc.getSeccompMode()).append('\n');
        }
        if (proc.isNoNewPrivsConfigured()) {
            sb.append("NoNewPrivs:\t").append(proc.isNoNewPrivs() ? 1 : 0).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Cmdline: configured array → each arg UTF-8 + NUL (empty args allowed);
     * explicit {@code []} → empty bytes; missing key → processName + NUL.
     */
    public static byte[] renderCmdline(Emulator<?> emulator, TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        Identity id = resolveIdentity(emulator, config);
        if (proc.isCmdlineConfigured()) {
            List<String> args = proc.getCmdline();
            if (args.isEmpty()) {
                return new byte[0];
            }
            ByteBuffer buf = ByteBuffer.allocate(estimateCmdlineSize(args));
            for (String arg : args) {
                byte[] part = (arg == null ? "" : arg).getBytes(StandardCharsets.UTF_8);
                buf.put(part);
                buf.put((byte) 0);
            }
            byte[] out = new byte[buf.position()];
            System.arraycopy(buf.array(), 0, out, 0, out.length);
            return out;
        }
        byte[] name = id.processName.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[name.length + 1];
        System.arraycopy(name, 0, out, 0, name.length);
        out[name.length] = 0;
        return out;
    }

    /**
     * {@code /proc/self|pid/limits} bytes, or {@code null} to preserve the prior open path.
     * <ol>
     *   <li>{@code linux.proc.limits} explicit (including {@code []}) always wins: each line + LF;
     *       empty array → empty bytes. Never derives a nofile row.</li>
     *   <li>Else when {@code linux.rlimits.nofile} is explicit ({@code linux.proc} may be absent):
     *       exactly one Android/Linux column-aligned {@code Max open files} row, UTF-8, trailing LF.
     *       Soft/hard are the configured decimals; units {@code files}. No header, no other rows,
     *       no addresses, no host values.</li>
     *   <li>Else {@code null}.</li>
     * </ol>
     * Unidirectional: does not parse nofile from limits text. No getrlimit/setrlimit/prlimit.
     */
    public static byte[] renderLimits(TraceEnvironmentConfig config) {
        if (config == null) {
            return null;
        }
        if (config.isLinuxProcConfigured()) {
            TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
            if (proc != null && proc.isLimitsConfigured()) {
                List<String> lines = proc.getLimits();
                if (lines.isEmpty()) {
                    return new byte[0];
                }
                StringBuilder sb = new StringBuilder(lines.size() * 48);
                for (String line : lines) {
                    sb.append(line).append('\n');
                }
                return sb.toString().getBytes(StandardCharsets.UTF_8);
            }
        }
        if (config.isLinuxRlimitsNofileConfigured()) {
            TraceEnvironmentConfig.LinuxRlimitsConfig rlimits = config.getLinuxRlimitsConfig();
            return renderNofileLimitsLine(rlimits.getNofileSoft(), rlimits.getNofileHard());
        }
        return null;
    }

    /** Name column for the derived {@code RLIMIT_NOFILE} row in {@code /proc/self|pid/limits}. */
    private static final String LIMITS_NOFILE_NAME = "Max open files";
    /** Units column for the derived {@code RLIMIT_NOFILE} row. */
    private static final String LIMITS_NOFILE_UNITS = "files";
    /** Name width before the inter-column space (printf-style left align 25). */
    private static final int LIMITS_NAME_WIDTH = 25;
    /** Soft/hard width before the inter-column space (printf-style left align 20). */
    private static final int LIMITS_VALUE_WIDTH = 20;
    /** Units width (printf-style left align 10). */
    private static final int LIMITS_UNITS_WIDTH = 10;

    /**
     * One {@code Max open files} row matching Android/Linux {@code /proc/self/limits}
     * column alignment: name 25, space, soft 20, space, hard 20, space, units 10, then LF.
     * Locale-independent decimals.
     */
    private static byte[] renderNofileLimitsLine(long soft, long hard) {
        StringBuilder sb = new StringBuilder(LIMITS_NAME_WIDTH + 1
                + LIMITS_VALUE_WIDTH + 1 + LIMITS_VALUE_WIDTH + 1 + LIMITS_UNITS_WIDTH + 1);
        appendPadded(sb, LIMITS_NOFILE_NAME, LIMITS_NAME_WIDTH);
        sb.append(' ');
        appendPadded(sb, Long.toString(soft), LIMITS_VALUE_WIDTH);
        sb.append(' ');
        appendPadded(sb, Long.toString(hard), LIMITS_VALUE_WIDTH);
        sb.append(' ');
        appendPadded(sb, LIMITS_NOFILE_UNITS, LIMITS_UNITS_WIDTH);
        sb.append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Left-align {@code value} into a minimum {@code width} using spaces (printf-style). */
    private static void appendPadded(StringBuilder sb, String value, int width) {
        sb.append(value);
        for (int i = value.length(); i < width; i++) {
            sb.append(' ');
        }
    }

    /**
     * Cgroup: configured lines each + LF; explicit {@code []} → empty; missing → {@code 0::/\n}.
     */
    public static byte[] renderCgroup(Emulator<?> emulator, TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        if (proc.isCgroupsConfigured()) {
            List<String> lines = proc.getCgroups();
            if (lines.isEmpty()) {
                return new byte[0];
            }
            StringBuilder sb = new StringBuilder(lines.size() * 16);
            for (String line : lines) {
                sb.append(line).append('\n');
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
        return "0::/\n".getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/pid/stat}: exactly 52 space-separated fields, trailing LF.
     * Field 2 is {@code (comm)} and may contain spaces.
     */
    public static byte[] renderStat(Emulator<?> emulator, TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        Identity id = resolveIdentity(emulator, config);
        String comm = sanitizeName(id.processName);

        String[] f = new String[52];
        f[0] = Integer.toString(id.pid);
        f[1] = "(" + comm + ")";
        f[2] = proc.getState();
        f[3] = Integer.toString(id.ppid);
        // Field 5 pgrp / field 6 session: explicit process.pgid / process.sid;
        // each missing key uses current pid. Never derived from ppid or each other.
        f[4] = Integer.toString(config.getPgid(id.pid));
        f[5] = Integer.toString(config.getSid(id.pid));
        f[6] = "0"; // tty_nr
        f[7] = "0"; // tpgid
        f[8] = "4194304"; // flags
        for (int i = 9; i <= 16; i++) {
            f[i] = "0"; // minflt..cstime
        }
        f[17] = "20"; // priority (historical when linux.proc.nice absent)
        f[18] = "0"; // nice (historical when linux.proc.nice absent)
        if (proc.isNiceConfigured()) {
            int nice = proc.getNice();
            f[17] = Integer.toString(20 + nice); // field 18 priority = 20 + nice
            f[18] = Integer.toString(nice); // field 19 nice
        }
        f[19] = Integer.toString(proc.getThreadCount()); // num_threads
        f[20] = "0"; // itrealvalue
        f[21] = Long.toString(proc.getStartTimeTicks()); // starttime
        f[22] = Long.toString(proc.getVirtualMemoryBytes()); // vsize
        f[23] = Long.toString(proc.getResidentSetPages()); // rss (pages)
        f[24] = RSSLIM_LITERAL; // rsslim
        for (int i = 25; i <= 36; i++) {
            f[i] = "0";
        }
        f[37] = "17"; // exit_signal
        f[38] = "0"; // processor
        for (int i = 39; i <= 51; i++) {
            f[i] = "0";
        }

        StringBuilder sb = new StringBuilder(128);
        for (int i = 0; i < 52; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(f[i]);
        }
        sb.append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code /proc/pid/statm}: seven space-separated decimal columns ending with LF —
     * {@code size resident shared text lib data dt}.
     * <ul>
     *   <li>{@code size}: pages from {@code virtualMemoryBytes}, ceil-divided by
     *       {@link ARMEmulator#PAGE_ALIGN} using quotient+remainder (avoids
     *       {@code Long.MAX_VALUE} overflow from {@code bytes + page - 1})</li>
     *   <li>{@code resident}: {@code residentSetPages}</li>
     *   <li>{@code shared}, {@code text}, {@code lib}, {@code data}, {@code dt}: fixed {@code 0}</li>
     * </ul>
     *
     * @return {@code null} when {@code linux.proc} is not configured; otherwise a new UTF-8 buffer
     */
    public static byte[] renderStatm(Emulator<?> emulator, TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        long sizePages = pagesFromVirtualMemoryBytes(proc.getVirtualMemoryBytes());
        long resident = proc.getResidentSetPages();
        StringBuilder sb = new StringBuilder(48);
        sb.append(sizePages).append(' ')
                .append(resident).append(' ')
                .append('0').append(' ') // shared
                .append('0').append(' ') // text
                .append('0').append(' ') // lib
                .append('0').append(' ') // data
                .append('0') // dt
                .append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Ceil-divide {@code virtualMemoryBytes} by {@link ARMEmulator#PAGE_ALIGN} without
     * overflowing near {@link Long#MAX_VALUE}.
     */
    static long pagesFromVirtualMemoryBytes(long virtualMemoryBytes) {
        long pageAlign = ARMEmulator.PAGE_ALIGN;
        long quot = virtualMemoryBytes / pageAlign;
        long rem = virtualMemoryBytes % pageAlign;
        return rem == 0L ? quot : quot + 1L;
    }

    /**
     * {@code /proc/pid/io}: seven {@code key: value} lines ending with LF, matching the kernel
     * order and names ({@code read_bytes}/{@code write_bytes}/{@code cancelled_write_bytes}).
     * Values come from {@code linux.proc} fields {@code rchar}/{@code wchar}/{@code syscr}/
     * {@code syscw}/{@code readBytes}/{@code writeBytes}/{@code cancelledWriteBytes}
     * (default {@code 0} when keys are absent).
     *
     * @return {@code null} when {@code linux.proc} is not configured; otherwise a new UTF-8 buffer
     */
    public static byte[] renderIo(Emulator<?> emulator, TraceEnvironmentConfig config) {
        if (config == null || !config.isLinuxProcConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcConfig proc = config.getLinuxProcConfig();
        StringBuilder sb = new StringBuilder(128);
        appendIoLine(sb, "rchar", proc.getRchar());
        appendIoLine(sb, "wchar", proc.getWchar());
        appendIoLine(sb, "syscr", proc.getSyscr());
        appendIoLine(sb, "syscw", proc.getSyscw());
        appendIoLine(sb, "read_bytes", proc.getReadBytes());
        appendIoLine(sb, "write_bytes", proc.getWriteBytes());
        appendIoLine(sb, "cancelled_write_bytes", proc.getCancelledWriteBytes());
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendIoLine(StringBuilder sb, String key, long value) {
        sb.append(key).append(": ").append(value).append('\n');
    }

    private static int estimateCmdlineSize(List<String> args) {
        int n = 0;
        for (String arg : args) {
            n += (arg == null ? 0 : arg.getBytes(StandardCharsets.UTF_8).length) + 1;
        }
        return Math.max(n, 1);
    }

    private static Identity resolveIdentity(Emulator<?> emulator, TraceEnvironmentConfig config) {
        String fallbackName = emulator != null ? emulator.getProcessName() : "unidbg";
        if (fallbackName == null || fallbackName.isEmpty()) {
            fallbackName = "unidbg";
        }
        int fallbackPid = emulator != null ? emulator.getPid() : 1;
        String processName = config.getProcessName(fallbackName);
        if (processName == null) {
            processName = fallbackName;
        }
        int pid = config.getPid(fallbackPid);
        int ppid = config.getPpid(0);
        int uid = config.getUid(0);
        int euid = config.getEuid(uid);
        int gid = config.getGid(0);
        int egid = config.getEgid(gid);
        return new Identity(processName, pid, ppid, uid, euid, gid, egid);
    }

    /** Only for line rendering: replace NUL/CR/LF with '?' without mutating config. */
    static String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder sb = null;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\0' || c == '\r' || c == '\n') {
                if (sb == null) {
                    sb = new StringBuilder(name.length());
                    sb.append(name, 0, i);
                }
                sb.append('?');
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? name : sb.toString();
    }

    static String stateDescription(String state) {
        if (state == null || state.isEmpty()) {
            return "unknown";
        }
        switch (state.charAt(0)) {
            case 'R':
                return "running";
            case 'S':
                return "sleeping";
            case 'D':
                return "disk sleep";
            case 'Z':
                return "zombie";
            case 'T':
                return "stopped";
            case 't':
                return "tracing stop";
            case 'X':
                return "dead";
            case 'I':
                return "idle";
            default:
                return "unknown";
        }
    }

    private static final class Identity {
        final String processName;
        final int pid;
        final int ppid;
        final int uid;
        final int euid;
        final int gid;
        final int egid;

        Identity(String processName, int pid, int ppid, int uid, int euid, int gid, int egid) {
            this.processName = processName;
            this.pid = pid;
            this.ppid = ppid;
            this.uid = uid;
            this.euid = euid;
            this.gid = gid;
            this.egid = egid;
        }
    }
}

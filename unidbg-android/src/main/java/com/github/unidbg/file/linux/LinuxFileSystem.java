package com.github.unidbg.file.linux;

import com.github.unidbg.Emulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.BaseFileSystem;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.FileSystem;
import com.github.unidbg.linux.file.ByteArrayFileIO;
import com.github.unidbg.linux.android.LogCatHandler;
import com.github.unidbg.linux.file.DirectoryFileIO;
import com.github.unidbg.linux.file.MapsFileIO;
import com.github.unidbg.linux.file.NullFileIO;
import com.github.unidbg.linux.file.SimpleFileIO;
import com.github.unidbg.linux.file.Stdin;
import com.github.unidbg.linux.file.Stdout;
import com.github.unidbg.trace.EnvAccessProbe;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.unix.IO;
import com.github.unidbg.unix.UnixEmulator;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LinuxFileSystem extends BaseFileSystem<AndroidFileIO> implements FileSystem<AndroidFileIO>, IOConstants {

    public LinuxFileSystem(Emulator<AndroidFileIO> emulator, File rootDir) {
        super(emulator, rootDir);
    }

    @Override
    public FileResult<AndroidFileIO> open(String pathname, int oflags) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        // 0) device-fingerprint fileOverlayRoot. Higher than linux.files. Read-only:
        // write/RDWR do not fall through to the host; O_DIRECTORY is not a file.
        if (config != null) {
            byte[] overlayFile = config.readProfileOverlayFile(pathname);
            if (overlayFile != null) {
                if ((oflags & 3) != O_RDONLY) {
                    return FileResult.failed(UnixEmulator.EACCES);
                }
                if ((oflags & O_DIRECTORY) != 0) {
                    return FileResult.failed(UnixEmulator.ENOTDIR);
                }
                TraceEnvironmentEventSink.emit(emulator, "linux_file", "open(\"" + pathname + "\")",
                        "path=" + pathname + ",bytes=" + overlayFile.length, "profile-file",
                        "读取画像文件 " + pathname);
                return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, overlayFile));
            }
            String[] overlayDir = config.listProfileOverlayDirectory(pathname);
            if (overlayDir != null) {
                if ((oflags & 3) != O_RDONLY) {
                    return FileResult.failed(UnixEmulator.EACCES);
                }
                DirectoryFileIO.DirectoryEntry[] entries =
                        new DirectoryFileIO.DirectoryEntry[overlayDir.length];
                for (int i = 0; i < overlayDir.length; i++) {
                    entries[i] = new DirectoryFileIO.DirectoryEntry(true, overlayDir[i]);
                }
                TraceEnvironmentEventSink.emit(emulator, "linux_file", "open(\"" + pathname + "\")",
                        "path=" + pathname + ",count=" + overlayDir.length, "profile-file",
                        "枚举画像目录 " + pathname);
                return FileResult.<AndroidFileIO>success(new DirectoryFileIO(oflags, pathname, entries));
            }
        }
        if (config != null && config.isFilesystemDirectoriesConfigured()) {
            java.util.List<String> names = config.getFilesystemDirectoryEntries(pathname);
            if (names != null) {
                if ((oflags & 3) != O_RDONLY) {
                    return FileResult.failed(UnixEmulator.EACCES);
                }
                DirectoryFileIO.DirectoryEntry[] entries =
                        new DirectoryFileIO.DirectoryEntry[names.size()];
                for (int i = 0; i < names.size(); i++) {
                    entries[i] = new DirectoryFileIO.DirectoryEntry(true, names.get(i));
                }
                TraceEnvironmentEventSink.emit(emulator, "filesystem_directory", "open(\"" + pathname + "\")",
                        "path=" + pathname + ",count=" + names.size(), "json-config",
                        "枚举配置目录 " + pathname);
                return FileResult.<AndroidFileIO>success(new DirectoryFileIO(oflags, pathname, entries));
            }
        }
        // 1) linux.files exact (and pid→self). Below overlay; still wins over generated / default.
        byte[] configuredFile = config == null ? null : config.getLinuxFileBytes(pathname);
        if (configuredFile == null && config != null) {
            String pidPrefix = "/proc/" + emulator.getPid() + "/";
            if (pathname.startsWith(pidPrefix)) {
                configuredFile = config.getLinuxFileBytes("/proc/self/" + pathname.substring(pidPrefix.length()));
            }
        }
        if (configuredFile != null) {
            TraceEnvironmentEventSink.emit(emulator, "linux_file", "open(\"" + pathname + "\")",
                    new String(configuredFile, StandardCharsets.UTF_8), "json-config",
                    "读取 Linux 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, configuredFile));
        }

        // 2) /proc/self|pid/environ — same effective list as AndroidElfLoader (linux.environ or
        //    built-in defaults); not gated on linux.proc; linux.files remains higher priority
        if (isProcLeafPath(pathname, "environ")) {
            byte[] environData = ConfiguredProcFiles.renderEnviron(config);
            String value = "path=" + pathname + ",format=environ,bytes=" + environData.length;
            // json-config only when linux.environ is explicitly present; else unidbg-default
            boolean environConfigured = config != null && config.isLinuxEnvironConfigured();
            String environSource = environConfigured ? "json-config" : "unidbg-default";
            String environNote = environConfigured
                    ? ("读取配置的进程环境 " + pathname)
                    : ("读取模拟器内建默认进程环境 " + pathname);
            TraceEnvironmentEventSink.emit(emulator, "linux_proc",
                    "read(\"" + pathname + "\")", value, environSource, environNote);
            return FileResult.<AndroidFileIO>success(
                    new ByteArrayFileIO(oflags, pathname, environData));
        }

        // 3) linux.proc → /proc/self|pid status|cmdline|cgroup|stat|statm|io|oom_score_adj|oom_score|oom_adj|attr/current
        //    and optional global boot_id / random uuid / entropy_avail / poolsize /
        //    write_wakeup_threshold / urandom_min_reseed_secs when those keys are configured.
        //    /proc/self|pid/limits also when linux.rlimits.nofile is explicit and linux.proc.limits
        //    is absent (linux.proc section may be missing entirely). linux.files remains higher.
        if (config != null && (config.isLinuxProcConfigured()
                || (config.isLinuxRlimitsNofileConfigured() && isProcLeafPath(pathname, "limits")))) {
            FileResult<AndroidFileIO> procResult = openConfiguredProcFile(config, pathname, oflags);
            if (procResult != null) {
                return procResult;
            }
        }

        // 4) linux.uname explicit fields → /proc/sys/kernel/hostname|osrelease|version|domainname|ostype
        //    (only when the matching uname key is present; never invents syscall fallbacks;
        //    linux.files remains higher priority above)
        if (config != null) {
            FileResult<AndroidFileIO> unameResult = openConfiguredUnameKernelFile(config, pathname, oflags);
            if (unameResult != null) {
                return unameResult;
            }
        }

        // 5) linux.cpu explicit online|offline|present|possible → /sys/devices/system/cpu/*
        //    (only when matching key present; independent of affinityMaskHex; linux.files higher)
        if (config != null && config.isLinuxCpuConfigured()) {
            FileResult<AndroidFileIO> cpuListResult = openConfiguredCpuListFile(config, pathname, oflags);
            if (cpuListResult != null) {
                return cpuListResult;
            }
        }

        // 6) filesystem.mounts → /proc/mounts, mountinfo, and exact read-only /etc/mtab
        //    (null keeps legacy fallback; missing filesystem.mounts does not take over mtab)
        if (config != null && config.isFilesystemMountsConfigured()) {
            FileResult<AndroidFileIO> mountsResult = openConfiguredMountFile(config, pathname, oflags);
            if (mountsResult != null) {
                return mountsResult;
            }
        }

        // 7) network.interfaces → exact /sys/class/net/<name>/address (mac), /mtu, /ifindex,
        //    /flags, /operstate, /carrier, /type, /speed, /duplex, /iflink, /tx_queue_len,
        //    /addr_assign_type, /name_assign_type, and /broadcast (linkLayerBroadcast).
        //    linux.files remains higher priority; missing node / unknown iface / missing
        //    field / write or directory / other sys paths keep legacy open. Never reads
        //    host NetworkInterface; never infers operstate, carrier, hardwareType,
        //    speedMbps, duplex, iflink, tx_queue_len, addr_assign_type, name_assign_type,
        //    or link-layer broadcast (including from mac, IPv4 broadcast, mtu, speedMbps,
        //    index, wifi, addressAssignType, or MAC generation style).
        if (config != null && config.isNetworkInterfacesConfigured()) {
            FileResult<AndroidFileIO> addressResult = openConfiguredNetworkAddressFile(config, pathname, oflags);
            if (addressResult != null) {
                return addressResult;
            }
            FileResult<AndroidFileIO> mtuResult = openConfiguredNetworkMtuFile(config, pathname, oflags);
            if (mtuResult != null) {
                return mtuResult;
            }
            FileResult<AndroidFileIO> ifindexResult = openConfiguredNetworkIfindexFile(config, pathname, oflags);
            if (ifindexResult != null) {
                return ifindexResult;
            }
            FileResult<AndroidFileIO> flagsResult = openConfiguredNetworkFlagsFile(config, pathname, oflags);
            if (flagsResult != null) {
                return flagsResult;
            }
            FileResult<AndroidFileIO> operstateResult = openConfiguredNetworkOperstateFile(config, pathname, oflags);
            if (operstateResult != null) {
                return operstateResult;
            }
            FileResult<AndroidFileIO> carrierResult = openConfiguredNetworkCarrierFile(config, pathname, oflags);
            if (carrierResult != null) {
                return carrierResult;
            }
            FileResult<AndroidFileIO> typeResult = openConfiguredNetworkTypeFile(config, pathname, oflags);
            if (typeResult != null) {
                return typeResult;
            }
            FileResult<AndroidFileIO> speedResult = openConfiguredNetworkSpeedFile(config, pathname, oflags);
            if (speedResult != null) {
                return speedResult;
            }
            FileResult<AndroidFileIO> duplexResult = openConfiguredNetworkDuplexFile(config, pathname, oflags);
            if (duplexResult != null) {
                return duplexResult;
            }
            FileResult<AndroidFileIO> iflinkResult = openConfiguredNetworkIflinkFile(config, pathname, oflags);
            if (iflinkResult != null) {
                return iflinkResult;
            }
            FileResult<AndroidFileIO> txQueueLenResult = openConfiguredNetworkTxQueueLenFile(config, pathname, oflags);
            if (txQueueLenResult != null) {
                return txQueueLenResult;
            }
            FileResult<AndroidFileIO> addrAssignTypeResult =
                    openConfiguredNetworkAddrAssignTypeFile(config, pathname, oflags);
            if (addrAssignTypeResult != null) {
                return addrAssignTypeResult;
            }
            FileResult<AndroidFileIO> nameAssignTypeResult =
                    openConfiguredNetworkNameAssignTypeFile(config, pathname, oflags);
            if (nameAssignTypeResult != null) {
                return nameAssignTypeResult;
            }
            FileResult<AndroidFileIO> broadcastResult =
                    openConfiguredNetworkBroadcastFile(config, pathname, oflags);
            if (broadcastResult != null) {
                return broadcastResult;
            }
        }

        // 8) network.ipv4Routes → exact /proc/net/route, /proc/self/net/route,
        //    /proc/<emulatorPid>/net/route. Missing node does not take over.
        //    Explicit [] serves the stable header only. linux.files remains higher.
        //    Read-only; never inferred from interfaces/wifi; never reads host routes.
        if (config != null && config.isNetworkIpv4RoutesConfigured()) {
            FileResult<AndroidFileIO> ipv4RouteResult = openConfiguredIpv4RouteFile(config, pathname, oflags);
            if (ipv4RouteResult != null) {
                return ipv4RouteResult;
            }
        }

        // 9) network.interfaceStats → exact /proc/net/dev, /proc/self/net/dev,
        //    /proc/<emulatorPid>/net/dev, and exact /sys/class/net/<name>/statistics/<field>
        //    for the sixteen existing counters when that interface has a stats entry.
        //    Missing node does not take over. Explicit [] serves the /proc/net/dev header
        //    only and does not take over sysfs statistic files. linux.files remains higher.
        //    Read-only; never inferred from interfaces/wifi/routes; never reads host
        //    NetworkInterface or real sysfs.
        if (config != null && config.isNetworkInterfaceStatsConfigured()) {
            FileResult<AndroidFileIO> interfaceStatsResult =
                    openConfiguredInterfaceStatsFile(config, pathname, oflags);
            if (interfaceStatsResult != null) {
                return interfaceStatsResult;
            }
            FileResult<AndroidFileIO> interfaceStatsSysfsResult =
                    openConfiguredInterfaceStatsSysfsFile(config, pathname, oflags);
            if (interfaceStatsSysfsResult != null) {
                return interfaceStatsSysfsResult;
            }
        }

        // 10) network.ipv6Addresses → exact /proc/net/if_inet6, /proc/self/net/if_inet6,
        //     /proc/<emulatorPid>/net/if_inet6. Missing node does not take over.
        //     Explicit [] serves a zero-byte file (no header). linux.files remains higher.
        //     Read-only; index reused from matching interfaces[].index; address/prefix/
        //     scope/flags never inferred; never reads host network.
        if (config != null && config.isNetworkIpv6AddressesConfigured()) {
            FileResult<AndroidFileIO> ipv6AddressResult =
                    openConfiguredIpv6AddressFile(config, pathname, oflags);
            if (ipv6AddressResult != null) {
                return ipv6AddressResult;
            }
        }

        // 11) network.arpEntries → exact /proc/net/arp, /proc/self/net/arp,
        //     /proc/<emulatorPid>/net/arp. Missing node does not take over.
        //     Explicit [] serves the stable header only. linux.files remains higher.
        //     Read-only; never inferred from interfaces/wifi/routes; never reads host ARP.
        if (config != null && config.isNetworkArpEntriesConfigured()) {
            FileResult<AndroidFileIO> arpEntryResult = openConfiguredArpEntryFile(config, pathname, oflags);
            if (arpEntryResult != null) {
                return arpEntryResult;
            }
        }

        // 12) network.igmpMemberships → exact /proc/net/igmp, /proc/self/net/igmp,
        //     /proc/<emulatorPid>/net/igmp. Missing node does not take over.
        //     Explicit [] serves the stable header only. linux.files remains higher.
        //     Read-only; never inferred from interfaces/wifi/routes; never reads host IGMP.
        if (config != null && config.isNetworkIgmpMembershipsConfigured()) {
            FileResult<AndroidFileIO> igmpResult = openConfiguredIgmpMembershipFile(config, pathname, oflags);
            if (igmpResult != null) {
                return igmpResult;
            }
        }

        // 13) network.igmp6Memberships → exact /proc/net/igmp6, /proc/self/net/igmp6,
        //     /proc/<emulatorPid>/net/igmp6. Missing node does not take over.
        //     Explicit [] serves a zero-byte file (no header). linux.files remains higher.
        //     Read-only; never inferred from interfaces/wifi/routes; never reads host MLD.
        if (config != null && config.isNetworkIgmp6MembershipsConfigured()) {
            FileResult<AndroidFileIO> igmp6Result = openConfiguredIgmp6MembershipFile(config, pathname, oflags);
            if (igmp6Result != null) {
                return igmp6Result;
            }
        }

        // 14) network.wirelessProcStats → exact /proc/net/wireless, /proc/self/net/wireless,
        //     /proc/<emulatorPid>/net/wireless. Missing node does not take over.
        //     Explicit empty entries serves the two header lines only. linux.files remains
        //     higher. Read-only; never inferred from interfaces/wifi/routes; never reads
        //     host wireless state; never implements ioctl/netlink/scan.
        if (config != null && config.isNetworkWirelessProcStatsConfigured()) {
            FileResult<AndroidFileIO> wirelessResult =
                    openConfiguredWirelessProcStatsFile(config, pathname, oflags);
            if (wirelessResult != null) {
                return wirelessResult;
            }
        }

        // 15) network.linkLayerMulticastEntries → exact /proc/net/dev_mcast,
        //     /proc/self/net/dev_mcast, /proc/<emulatorPid>/net/dev_mcast.
        //     Missing node does not take over. Explicit [] serves a zero-byte file
        //     (no header). linux.files remains higher. Read-only; never inferred
        //     from interfaces.mac/linkLayerBroadcast/wifi; never reads host multicast.
        if (config != null && config.isNetworkLinkLayerMulticastEntriesConfigured()) {
            FileResult<AndroidFileIO> mcastResult =
                    openConfiguredLinkLayerMulticastFile(config, pathname, oflags);
            if (mcastResult != null) {
                return mcastResult;
            }
        }

        if (config != null && config.isNetworkTcpConfigured()) {
            FileResult<AndroidFileIO> tcpResult = openConfiguredTcpFile(config, pathname, oflags, false);
            if (tcpResult != null) {
                return tcpResult;
            }
        }
        if (config != null && config.isNetworkTcp6Configured()) {
            FileResult<AndroidFileIO> tcp6Result = openConfiguredTcpFile(config, pathname, oflags, true);
            if (tcp6Result != null) {
                return tcp6Result;
            }
        }
        if (config != null && config.isLinuxProcessesConfigured()) {
            FileResult<AndroidFileIO> processResult = openConfiguredProcessFile(config, pathname, oflags);
            if (processResult != null) {
                return processResult;
            }
        }
        if (config != null && config.isAndroidBatteryConfigured()) {
            FileResult<AndroidFileIO> powerSupply = openConfiguredPowerSupplyFile(config, pathname, oflags);
            if (powerSupply != null) {
                return powerSupply;
            }
        }

        if ("/dev/tty".equals(pathname)) {
            return FileResult.<AndroidFileIO>success(new NullFileIO(pathname));
        }
        if ("/proc/self/maps".equals(pathname) || ("/proc/" + emulator.getPid() + "/maps").equals(pathname) ||
                ("/proc/self/task/" + emulator.getPid() + "/maps").equals(pathname)) {
            EnvAccessProbe.miss(emulator, "open(\"" + pathname + "\")",
                    "path=" + pathname + ",source=maps",
                    "目标读取 maps（非模板） " + pathname);
            return FileResult.<AndroidFileIO>success(new MapsFileIO(emulator, oflags, pathname, emulator.getMemory().getLoadedModules()));
        }

        if (EnvAccessProbe.isInterestingPath(pathname)) {
            EnvAccessProbe.miss(emulator, "open(\"" + pathname + "\")",
                    "path=" + pathname, "目标读取未配置的文件 " + pathname);
        }
        return super.open(pathname, oflags);
    }

    /**
     * When {@code linux.proc} is configured and {@code pathname} is a self/pid status-family path
     * (status/cmdline/cgroup/stat/statm/io/oom_score_adj/oom_score/oom_adj/attr/current/comm/wchan/limits),
     * configured-tid task leaf ({@code /proc/self|pid/task/<tid>/comm|wchan} when that key is set),
     * or exact global boot_id / random uuid / entropy_avail / poolsize /
     * write_wakeup_threshold / urandom_min_reseed_secs path with the matching key configured, render bytes via
     * {@link ConfiguredProcFiles} (including empty content for process files; keyed paths only when
     * that key is present) and emit one {@code linux_proc} sidecar.
     * The {@code limits} leaf is also entered when {@code linux.proc} is absent but
     * {@code linux.rlimits.nofile} is explicit ({@link ConfiguredProcFiles#renderLimits} derives
     * one {@code Max open files} row; explicit {@code linux.proc.limits} including {@code []} wins).
     * Returns {@code null} for non-matching paths so the open chain continues.
     * {@code linux.files} exact/pid→self remains higher priority in {@link #open}.
     */
    private FileResult<AndroidFileIO> openConfiguredProcFile(TraceEnvironmentConfig config,
                                                             String pathname, int oflags) {
        final String format;
        final byte[] data;
        final String note;
        if (ConfiguredProcFiles.BOOT_ID_PATH.equals(pathname)) {
            format = "boot_id";
            data = ConfiguredProcFiles.renderBootId(config);
            note = "读取配置的内核 boot_id " + pathname;
        } else if (ConfiguredProcFiles.RANDOM_UUID_PATH.equals(pathname)) {
            format = "random_uuid";
            data = ConfiguredProcFiles.renderRandomUuid(config);
            note = "读取配置的内核 random uuid " + pathname;
        } else if (ConfiguredProcFiles.ENTROPY_AVAIL_PATH.equals(pathname)) {
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            format = "entropy_avail";
            data = ConfiguredProcFiles.renderEntropyAvail(config);
            note = "读取配置的随机池状态 " + pathname;
        } else if (ConfiguredProcFiles.RANDOM_POOLSIZE_PATH.equals(pathname)) {
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            format = "random_poolsize";
            data = ConfiguredProcFiles.renderRandomPoolSize(config);
            note = "读取配置的随机池状态 " + pathname;
        } else if (ConfiguredProcFiles.WRITE_WAKEUP_THRESHOLD_PATH.equals(pathname)) {
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            format = "write_wakeup_threshold";
            data = ConfiguredProcFiles.renderWriteWakeupThreshold(config);
            note = "读取配置的随机池状态 " + pathname;
        } else if (ConfiguredProcFiles.URANDOM_MIN_RESEED_SECS_PATH.equals(pathname)) {
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            format = "urandom_min_reseed_secs";
            data = ConfiguredProcFiles.renderUrandomMinReseedSecs(config);
            note = "读取配置的随机池状态 " + pathname;
        } else if (isProcLeafPath(pathname, "status")) {
            format = "status";
            data = ConfiguredProcFiles.renderStatus(emulator, config);
            note = "读取配置的进程 proc 文件 " + pathname;
        } else if (isProcLeafPath(pathname, "cmdline")) {
            format = "cmdline";
            data = ConfiguredProcFiles.renderCmdline(emulator, config);
            note = "读取配置的进程 proc 文件 " + pathname;
        } else if (isProcLeafPath(pathname, "cgroup")) {
            format = "cgroup";
            data = ConfiguredProcFiles.renderCgroup(emulator, config);
            note = "读取配置的进程 proc 文件 " + pathname;
        } else if (isProcLeafPath(pathname, "stat")) {
            format = "stat";
            data = ConfiguredProcFiles.renderStat(emulator, config);
            note = "读取配置的进程 proc 文件 " + pathname;
        } else if (isProcLeafPath(pathname, "statm")) {
            format = "statm";
            data = ConfiguredProcFiles.renderStatm(emulator, config);
            note = "读取配置的进程 proc 文件 " + pathname;
        } else if (isProcLeafPath(pathname, "io")) {
            format = "io";
            data = ConfiguredProcFiles.renderIo(emulator, config);
            note = "读取配置的进程 proc 文件 " + pathname;
        } else if (isProcLeafPath(pathname, "oom_score_adj")) {
            format = "oom_score_adj";
            data = ConfiguredProcFiles.renderOomScoreAdj(config);
            note = "读取配置的进程 proc 文件 " + pathname;
        } else if (isProcLeafPath(pathname, "oom_score")) {
            format = "oom_score";
            data = ConfiguredProcFiles.renderOomScore(config);
            note = "读取配置的进程 proc 文件 " + pathname;
        } else if (isProcLeafPath(pathname, "oom_adj")) {
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            format = "oom_adj";
            data = ConfiguredProcFiles.renderOomAdj(config);
            note = "读取配置的 OOM 调整值 " + pathname;
        } else if (isProcLeafPath(pathname, "attr/current")) {
            format = "selinux_context";
            data = ConfiguredProcFiles.renderSelinuxContext(config);
            note = "读取配置的进程 SELinux 上下文 " + pathname;
        } else if (isProcLeafPath(pathname, "comm") || isProcTaskLeafPath(config, pathname, "comm")) {
            format = "comm";
            data = ConfiguredProcFiles.renderComm(config);
            note = "读取配置的进程 proc 文件 " + pathname;
        } else if (isProcLeafPath(pathname, "wchan") || isProcTaskLeafPath(config, pathname, "wchan")) {
            format = "wchan";
            data = ConfiguredProcFiles.renderWchan(config);
            note = "读取配置的进程 proc 文件 " + pathname;
        } else if (isProcLeafPath(pathname, "limits")) {
            format = "limits";
            data = ConfiguredProcFiles.renderLimits(config);
            note = "读取配置的进程 limits 文件 " + pathname;
        } else {
            return null;
        }
        if (data == null) {
            return null;
        }
        String value = "path=" + pathname + ",format=" + format + ",bytes=" + data.length;
        TraceEnvironmentEventSink.emit(emulator, "linux_proc",
                "read(\"" + pathname + "\")", value, "json-config", note);
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    /**
     * When the matching {@code linux.uname} field is explicitly configured, serve exact
     * {@code /proc/sys/kernel/hostname|osrelease|version|domainname} as UTF-8 text + LF and emit
     * one {@code linux_proc} sidecar (format hostname/osrelease/version/domainname/ostype; no raw text).
     * Returns {@code null} when the matching key is absent so the open chain continues (legacy).
     * Does not write, synthesize {@code /proc/version}, or invent values from syscall defaults.
     */
    private FileResult<AndroidFileIO> openConfiguredUnameKernelFile(TraceEnvironmentConfig config,
                                                                    String pathname, int oflags) {
        final String format;
        final byte[] data;
        final String note;
        if (ConfiguredProcFiles.HOSTNAME_PATH.equals(pathname)) {
            format = "hostname";
            data = ConfiguredProcFiles.renderUnameHostname(config);
            note = "读取配置的 uname 节点名 " + pathname;
        } else if (ConfiguredProcFiles.OSRELEASE_PATH.equals(pathname)) {
            format = "osrelease";
            data = ConfiguredProcFiles.renderUnameOsrelease(config);
            note = "读取配置的 uname 内核 release " + pathname;
        } else if (ConfiguredProcFiles.KERNEL_VERSION_PATH.equals(pathname)) {
            format = "version";
            data = ConfiguredProcFiles.renderUnameVersion(config);
            note = "读取配置的 uname 内核 version " + pathname;
        } else if (ConfiguredProcFiles.DOMAINNAME_PATH.equals(pathname)) {
            format = "domainname";
            data = ConfiguredProcFiles.renderUnameDomainname(config);
            note = "读取配置的 uname 域名 " + pathname;
        } else if (ConfiguredProcFiles.OSTYPE_PATH.equals(pathname)) {
            format = "ostype";
            data = ConfiguredProcFiles.renderUnameOstype(config);
            note = "读取配置的 uname 系统类型 " + pathname;
        } else {
            return null;
        }
        if (data == null) {
            return null;
        }
        String value = "path=" + pathname + ",format=" + format + ",bytes=" + data.length;
        TraceEnvironmentEventSink.emit(emulator, "linux_proc",
                "read(\"" + pathname + "\")", value, "json-config", note);
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    /** Exact sysfs paths for {@code linux.cpu} CPU-list fields. */
    private static final String CPU_ONLINE_PATH = "/sys/devices/system/cpu/online";
    private static final String CPU_OFFLINE_PATH = "/sys/devices/system/cpu/offline";
    private static final String CPU_PRESENT_PATH = "/sys/devices/system/cpu/present";
    private static final String CPU_POSSIBLE_PATH = "/sys/devices/system/cpu/possible";

    /**
     * When the matching {@code linux.cpu} online/offline/present/possible field is explicitly
     * configured, serve exact UTF-8 {@code <cpulist>\n} and emit one {@code linux_cpu} sidecar
     * (path+format+bytes only; no raw cpulist). Missing field → null (legacy open).
     * Does not write, hotplug, or invent lists from affinity/host.
     */
    private FileResult<AndroidFileIO> openConfiguredCpuListFile(TraceEnvironmentConfig config,
                                                                String pathname, int oflags) {
        TraceEnvironmentConfig.LinuxCpuConfig cpu = config.getLinuxCpuConfig();
        if (cpu == null) {
            return null;
        }
        final String format;
        final String list;
        final String note;
        if (CPU_ONLINE_PATH.equals(pathname)) {
            if (!cpu.isOnlineConfigured()) {
                return null;
            }
            format = "online";
            list = cpu.getOnline();
            note = "读取配置的 CPU online 列表 " + pathname;
        } else if (CPU_OFFLINE_PATH.equals(pathname)) {
            if (!cpu.isOfflineConfigured()) {
                return null;
            }
            format = "offline";
            list = cpu.getOffline();
            note = "读取配置的 CPU offline 列表 " + pathname;
        } else if (CPU_PRESENT_PATH.equals(pathname)) {
            if (!cpu.isPresentConfigured()) {
                return null;
            }
            format = "present";
            list = cpu.getPresent();
            note = "读取配置的 CPU present 列表 " + pathname;
        } else if (CPU_POSSIBLE_PATH.equals(pathname)) {
            if (!cpu.isPossibleConfigured()) {
                return null;
            }
            format = "possible";
            list = cpu.getPossible();
            note = "读取配置的 CPU possible 列表 " + pathname;
        } else {
            return null;
        }
        if (list == null) {
            return null;
        }
        byte[] data = (list + "\n").getBytes(StandardCharsets.UTF_8);
        String value = "path=" + pathname + ",format=" + format + ",bytes=" + data.length;
        TraceEnvironmentEventSink.emit(emulator, "linux_cpu",
                "read(\"" + pathname + "\")", value, "json-config", note);
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    /** {@code /proc/self/<leaf>} or {@code /proc/<emulatorPid>/<leaf>} only. */
    private boolean isProcLeafPath(String pathname, String leaf) {
        if (("/proc/self/" + leaf).equals(pathname)) {
            return true;
        }
        return ("/proc/" + emulator.getPid() + "/" + leaf).equals(pathname);
    }

    /**
     * {@code /proc/self/task/<tid>/<leaf>} or {@code /proc/<emulatorPid>/task/<tid>/<leaf>}
     * where {@code tid} is {@code process.tid} with {@link Emulator#getPid()} fallback.
     * Only the configured tid is served — no task directory enumeration or other tids.
     * Used for configured {@code comm} / {@code wchan} task paths.
     */
    private boolean isProcTaskLeafPath(TraceEnvironmentConfig config, String pathname, String leaf) {
        if (config == null || pathname == null || leaf == null) {
            return false;
        }
        int tid = config.getTid(emulator.getPid());
        String suffix = "/task/" + tid + "/" + leaf;
        if (("/proc/self" + suffix).equals(pathname)) {
            return true;
        }
        return ("/proc/" + emulator.getPid() + suffix).equals(pathname);
    }

    /**
     * When {@code filesystem.mounts} renders non-null bytes (including empty content), return a
     * read-only {@link ByteArrayFileIO} and emit one {@code filesystem_mounts} sidecar.
     * Exact {@code /etc/mtab} is a compatible view of the same {@code renderProcMounts} bytes
     * and is taken over only for a read-only open (write or {@code O_DIRECTORY} fall through).
     * Returns {@code null} to continue the legacy open chain when render yields {@code null}
     * (e.g. incomplete mountinfo) or the path is not a mounts table path.
     */
    private FileResult<AndroidFileIO> openConfiguredMountFile(TraceEnvironmentConfig config,
                                                              String pathname, int oflags) {
        final String format;
        final byte[] data;
        final boolean mtab;
        if (isProcMountsPath(pathname)) {
            format = "mounts";
            data = ConfiguredMountFiles.renderProcMounts(config);
            mtab = false;
        } else if (isProcMountInfoPath(pathname)) {
            format = "mountinfo";
            data = ConfiguredMountFiles.renderMountInfo(config);
            mtab = false;
        } else if ("/etc/mtab".equals(pathname)) {
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            format = "mtab";
            data = ConfiguredMountFiles.renderProcMounts(config);
            mtab = true;
        } else {
            return null;
        }
        if (data == null) {
            return null;
        }
        int entries = config.getFilesystemMounts().size();
        String value;
        if (mtab) {
            value = "path=" + pathname + ",format=mtab,count=" + entries + ",bytes=" + data.length;
        } else {
            value = "path=" + pathname + ",format=" + format + ",entries=" + entries;
        }
        TraceEnvironmentEventSink.emit(emulator, "filesystem_mounts",
                "read(\"" + pathname + "\")", value, "json-config",
                "读取配置的挂载表 " + pathname);
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    private boolean isProcMountsPath(String pathname) {
        if ("/proc/mounts".equals(pathname) || "/proc/self/mounts".equals(pathname)) {
            return true;
        }
        return ("/proc/" + emulator.getPid() + "/mounts").equals(pathname);
    }

    private boolean isProcMountInfoPath(String pathname) {
        if ("/proc/self/mountinfo".equals(pathname)) {
            return true;
        }
        return ("/proc/" + emulator.getPid() + "/mountinfo").equals(pathname);
    }

    private static final String SYS_CLASS_NET_PREFIX = "/sys/class/net/";
    private static final String SYS_NET_ADDRESS_SUFFIX = "/address";
    private static final String SYS_NET_MTU_SUFFIX = "/mtu";
    private static final String SYS_NET_IFINDEX_SUFFIX = "/ifindex";
    private static final String SYS_NET_FLAGS_SUFFIX = "/flags";
    private static final String SYS_NET_OPERSTATE_SUFFIX = "/operstate";
    private static final String SYS_NET_CARRIER_SUFFIX = "/carrier";
    private static final String SYS_NET_TYPE_SUFFIX = "/type";
    private static final String SYS_NET_SPEED_SUFFIX = "/speed";
    private static final String SYS_NET_DUPLEX_SUFFIX = "/duplex";
    private static final String SYS_NET_IFLINK_SUFFIX = "/iflink";
    private static final String SYS_NET_TX_QUEUE_LEN_SUFFIX = "/tx_queue_len";
    private static final String SYS_NET_ADDR_ASSIGN_TYPE_SUFFIX = "/addr_assign_type";
    private static final String SYS_NET_NAME_ASSIGN_TYPE_SUFFIX = "/name_assign_type";
    private static final String SYS_NET_BROADCAST_SUFFIX = "/broadcast";
    private static final String SYS_NET_STATISTICS_INFIX = "/statistics/";

    /**
     * When {@code network.interfaces} lists an entry with both {@code name} and {@code mac},
     * serve exact {@code /sys/class/net/<name>/address} as UTF-8 lowercase canonical MAC + LF.
     * Read-only {@link ByteArrayFileIO}; sidecar kind {@code network_device} with path/name/format/bytes
     * only (never the MAC text). Returns {@code null} for unknown names, missing mac, or any other
     * sys path so the legacy open chain continues. {@code linux.files} exact map remains higher
     * priority in {@link #open}. Does not enumerate directories, write, or invent operstate/IPv6.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkAddressFile(TraceEnvironmentConfig config,
                                                                       String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_ADDRESS_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_ADDRESS_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            String mac = iface.getMac();
            if (mac == null) {
                return null;
            }
            byte[] data = (mac + "\n").getBytes(StandardCharsets.UTF_8);
            String value = "path=" + pathname + ",name=" + name + ",format=address,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 address 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.interfaces} lists an entry with both {@code name} and {@code mtu},
     * serve exact {@code /sys/class/net/<name>/mtu} as UTF-8 decimal MTU + LF.
     * Read-only {@link ByteArrayFileIO}; sidecar kind {@code network_device} with path/name/format/bytes
     * only (never MAC or other interface fields). Returns {@code null} for unknown names, missing mtu,
     * or any other sys path so the legacy open chain continues. {@code linux.files} exact map remains
     * higher priority in {@link #open}. Does not enumerate directories, write, or invent operstate/IPv6.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkMtuFile(TraceEnvironmentConfig config,
                                                                  String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_MTU_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_MTU_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            Integer mtu = iface.getMtu();
            if (mtu == null) {
                return null;
            }
            byte[] data = (Integer.toString(mtu.intValue()) + "\n").getBytes(StandardCharsets.UTF_8);
            String value = "path=" + pathname + ",name=" + name + ",format=mtu,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 mtu 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.interfaces} lists an entry with {@code name}, serve exact
     * {@code /sys/class/net/<name>/ifindex} as UTF-8 decimal {@code index} + LF.
     * {@code index} is a required interface field; this path is read-only
     * {@link ByteArrayFileIO}. Sidecar kind {@code network_device} with path/name/format/bytes
     * only (never MAC, IPv4, flags, or other interface fields). Returns {@code null} for
     * unknown names or any other sys path so the legacy open chain continues.
     * {@code linux.files} exact map remains higher priority in {@link #open}. Does not
     * enumerate directories, write, or invent operstate/IPv6.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkIfindexFile(TraceEnvironmentConfig config,
                                                                      String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_IFINDEX_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_IFINDEX_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            byte[] data = (Integer.toString(iface.getIndex()) + "\n").getBytes(StandardCharsets.UTF_8);
            String value = "path=" + pathname + ",name=" + name + ",format=ifindex,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 ifindex 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.interfaces} lists an entry with both {@code name} and {@code flags},
     * serve exact {@code /sys/class/net/<name>/flags} as UTF-8 lowercase {@code 0x}-prefixed hex
     * (kernel {@code fmt_hex} {@code %#x}, no zero-padding) + LF, via
     * {@link TraceEnvironmentConfig.NetworkInterfaceConfig#getFlags()}.
     * Read-only {@link ByteArrayFileIO}; sidecar kind {@code network_device} with path/name/format/bytes
     * only (never flags numeric value, MAC, IPv4, or other interface fields). Returns {@code null}
     * for unknown names, missing flags, or any other sys path so the legacy open chain continues.
     * {@code linux.files} exact map remains higher priority in {@link #open}. Does not enumerate
     * directories, write, or invent operstate/IPv6/{@code /proc/net}.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkFlagsFile(TraceEnvironmentConfig config,
                                                                    String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_FLAGS_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_FLAGS_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            Integer flags = iface.getFlags();
            if (flags == null) {
                return null;
            }
            byte[] data = ("0x" + Integer.toHexString(flags.intValue()) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            String value = "path=" + pathname + ",name=" + name + ",format=flags,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 flags 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.interfaces} lists an entry with both {@code name} and explicit
     * {@code operState}, serve exact {@code /sys/class/net/<name>/operstate} as UTF-8
     * lowercase IF_OPER_* token + LF, via
     * {@link TraceEnvironmentConfig.NetworkInterfaceConfig#getOperState()}.
     * Read-only: write or {@code O_DIRECTORY} returns {@code null} so the legacy open chain
     * continues. Sidecar kind {@code network_device} with path/name/format/bytes only
     * (never the operstate text, MAC, IPv4, flags, or other interface fields). Returns
     * {@code null} for unknown names, missing {@code operState}, other sys paths, or
     * non-read-only opens. {@code linux.files} exact map remains higher priority in
     * {@link #open}. Never inferred from flags or wifi.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkOperstateFile(TraceEnvironmentConfig config,
                                                                        String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_OPERSTATE_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_OPERSTATE_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            if (!iface.isOperStateConfigured()) {
                return null;
            }
            String operState = iface.getOperState();
            if (operState == null) {
                return null;
            }
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            byte[] data = (operState + "\n").getBytes(StandardCharsets.UTF_8);
            String value = "path=" + pathname + ",name=" + name + ",format=operstate,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 operstate 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.interfaces} lists an entry with both {@code name} and explicit
     * {@code carrier}, serve exact {@code /sys/class/net/<name>/carrier} as UTF-8
     * {@code 1} or {@code 0} + LF, via
     * {@link TraceEnvironmentConfig.NetworkInterfaceConfig#isCarrier()}.
     * Read-only: write or {@code O_DIRECTORY} returns {@code null} so the legacy open chain
     * continues. Sidecar kind {@code network_device} with path/name/format/bytes only
     * (never the carrier 0/1 or true/false, operState, MAC, IPv4, flags, MTU, or other
     * interface fields). Returns {@code null} for unknown names, missing {@code carrier},
     * other sys paths, or non-read-only opens. {@code linux.files} exact map remains
     * higher priority in {@link #open}. Never inferred from operState, flags, or wifi.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkCarrierFile(TraceEnvironmentConfig config,
                                                                      String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_CARRIER_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_CARRIER_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            if (!iface.isCarrierConfigured()) {
                return null;
            }
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            byte[] data = ((iface.isCarrier() ? "1" : "0") + "\n").getBytes(StandardCharsets.UTF_8);
            String value = "path=" + pathname + ",name=" + name + ",format=carrier,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 carrier 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.interfaces} lists an entry with both {@code name} and explicit
     * {@code hardwareType}, serve exact {@code /sys/class/net/<name>/type} as UTF-8
     * decimal ARPHRD value + LF, via
     * {@link TraceEnvironmentConfig.NetworkInterfaceConfig#getHardwareType()}.
     * Read-only: write or {@code O_DIRECTORY} returns {@code null} so the legacy open chain
     * continues. Sidecar kind {@code network_device} with path/name/format/bytes only
     * (never the hardwareType number, MAC, IPv4, flags, operState, carrier, MTU, or other
     * interface fields). Returns {@code null} for unknown names, missing {@code hardwareType},
     * other sys paths, or non-read-only opens. {@code linux.files} exact map remains
     * higher priority in {@link #open}. Never inferred from name, flags, MAC, operState,
     * or carrier.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkTypeFile(TraceEnvironmentConfig config,
                                                                   String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_TYPE_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_TYPE_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            if (!iface.isHardwareTypeConfigured()) {
                return null;
            }
            Integer hardwareType = iface.getHardwareType();
            if (hardwareType == null) {
                return null;
            }
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            byte[] data = (Integer.toString(hardwareType.intValue()) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            String value = "path=" + pathname + ",name=" + name + ",format=type,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 type 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.interfaces} lists an entry with both {@code name} and explicit
     * {@code speedMbps}, serve exact {@code /sys/class/net/<name>/speed} as decimal ASCII
     * Mbps + LF, via
     * {@link TraceEnvironmentConfig.NetworkInterfaceConfig#getSpeedMbps()}.
     * {@code -1} is unknown link speed (Linux {@code SPEED_UNKNOWN}). Read-only: write or
     * {@code O_DIRECTORY} returns {@code null} so the legacy open chain continues.
     * Sidecar kind {@code network_device} with path/name/format=speed/bytes only
     * (never the speedMbps number, MAC, IPv4, flags, hardwareType, operState, carrier,
     * or other interface fields). Returns {@code null} for unknown names, missing
     * {@code speedMbps}, other sys paths, or non-read-only opens. {@code linux.files}
     * exact map remains higher priority in {@link #open}. Never inferred from
     * {@code network.wifi.linkSpeedMbps}, name, flags, MAC, hardwareType, operState,
     * or carrier.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkSpeedFile(TraceEnvironmentConfig config,
                                                                    String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_SPEED_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_SPEED_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            if (!iface.isSpeedMbpsConfigured()) {
                return null;
            }
            Integer speedMbps = iface.getSpeedMbps();
            if (speedMbps == null) {
                return null;
            }
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            byte[] data = (Integer.toString(speedMbps.intValue()) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            String value = "path=" + pathname + ",name=" + name + ",format=speed,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 speed 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.interfaces} lists an entry with both {@code name} and explicit
     * {@code duplex}, serve exact {@code /sys/class/net/<name>/duplex} as UTF-8
     * lowercase {@code full}/{@code half}/{@code unknown} + LF, via
     * {@link TraceEnvironmentConfig.NetworkInterfaceConfig#getDuplex()}.
     * Read-only: write or {@code O_DIRECTORY} returns {@code null} so the legacy open chain
     * continues. Sidecar kind {@code network_device} with path/name/format=duplex/bytes only
     * (never the duplex text, speedMbps, MAC, IPv4, flags, hardwareType, operState, carrier,
     * or other interface fields). Returns {@code null} for unknown names, missing
     * {@code duplex}, other sys paths, or non-read-only opens. {@code linux.files}
     * exact map remains higher priority in {@link #open}. Never inferred from
     * {@code speedMbps}, carrier, operState, hardwareType, flags, name, or
     * {@code network.wifi}.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkDuplexFile(TraceEnvironmentConfig config,
                                                                     String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_DUPLEX_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_DUPLEX_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            if (!iface.isDuplexConfigured()) {
                return null;
            }
            String duplex = iface.getDuplex();
            if (duplex == null) {
                return null;
            }
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            byte[] data = (duplex + "\n").getBytes(StandardCharsets.UTF_8);
            String value = "path=" + pathname + ",name=" + name + ",format=duplex,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 duplex 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.interfaces} lists an entry with both {@code name} and explicit
     * {@code linkIndex}, serve exact {@code /sys/class/net/<name>/iflink} as decimal ASCII
     * {@code linkIndex} + LF, via
     * {@link TraceEnvironmentConfig.NetworkInterfaceConfig#getLinkIndex()}.
     * Read-only: write or {@code O_DIRECTORY} returns {@code null} so the legacy open chain
     * continues. Sidecar kind {@code network_device} with path/name/format=iflink/bytes only
     * (never the linkIndex number, MAC, IPv4, flags, hardwareType, speedMbps, duplex,
     * operState, carrier, or other interface fields). Returns {@code null} for unknown
     * names, missing {@code linkIndex}, other sys paths (including {@code /ifindex}), or
     * non-read-only opens. {@code linux.files} exact map remains higher priority in
     * {@link #open}. Never inferred from required {@code index}, name, hardwareType,
     * speedMbps, duplex, flags, carrier, operState, MAC, or wifi; {@code iflink} and
     * {@code ifindex} are independently configured and never auto-equalized.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkIflinkFile(TraceEnvironmentConfig config,
                                                                     String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_IFLINK_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_IFLINK_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            if (!iface.isLinkIndexConfigured()) {
                return null;
            }
            Integer linkIndex = iface.getLinkIndex();
            if (linkIndex == null) {
                return null;
            }
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            byte[] data = (Integer.toString(linkIndex.intValue()) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            String value = "path=" + pathname + ",name=" + name + ",format=iflink,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 iflink 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.interfaces} lists an entry with both {@code name} and explicit
     * {@code txQueueLen}, serve exact {@code /sys/class/net/<name>/tx_queue_len} as decimal
     * ASCII {@code txQueueLen} + LF, via
     * {@link TraceEnvironmentConfig.NetworkInterfaceConfig#getTxQueueLen()}.
     * Read-only: write or {@code O_DIRECTORY} returns {@code null} so the legacy open chain
     * continues. Sidecar kind {@code network_device} with path/name/format=tx_queue_len/bytes
     * only (never the txQueueLen number, MAC, IPv4, flags, hardwareType, speedMbps, duplex,
     * linkIndex, operState, carrier, or other interface fields). Returns {@code null} for
     * unknown names, missing {@code txQueueLen}, other sys paths (including mtu/speed/duplex/
     * iflink/ifindex/address/type/operstate/carrier), or non-read-only opens.
     * {@code linux.files} exact map remains higher priority in {@link #open}. Never inferred
     * from mtu, speedMbps, duplex, hardwareType, linkIndex, index, flags, carrier, operState,
     * name, or wifi; independent of {@code mtu} and {@code speedMbps}. Explicit {@code 0} is
     * served; omitted key is not.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkTxQueueLenFile(TraceEnvironmentConfig config,
                                                                         String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_TX_QUEUE_LEN_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_TX_QUEUE_LEN_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            if (!iface.isTxQueueLenConfigured()) {
                return null;
            }
            Integer txQueueLen = iface.getTxQueueLen();
            if (txQueueLen == null) {
                return null;
            }
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            byte[] data = (Integer.toString(txQueueLen.intValue()) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            String value = "path=" + pathname + ",name=" + name + ",format=tx_queue_len,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 tx_queue_len 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.interfaces} lists an entry with both {@code name} and explicit
     * {@code addressAssignType}, serve exact {@code /sys/class/net/<name>/addr_assign_type}
     * as decimal ASCII {@code addressAssignType} + LF, via
     * {@link TraceEnvironmentConfig.NetworkInterfaceConfig#getAddressAssignType()}.
     * Read-only: write or {@code O_DIRECTORY} returns {@code null} so the legacy open chain
     * continues. Sidecar kind {@code network_device} with path/name/format=addr_assign_type/bytes
     * only (never the addressAssignType number, IP, MAC, flags, hardwareType, speedMbps, duplex,
     * linkIndex, txQueueLen, operState, carrier, or other interface fields). Returns {@code null}
     * for unknown names, missing {@code addressAssignType}, other sys paths (including address/
     * mtu/type/speed/duplex/iflink/ifindex/tx_queue_len/operstate/carrier), or non-read-only
     * opens. {@code linux.files} exact map remains higher priority in {@link #open}. Never
     * inferred from mac, name, hardwareType, flags, carrier, operState, speedMbps, duplex,
     * linkIndex, txQueueLen, wifi, or MAC generation style; independent of {@code mac}.
     * Explicit {@code 0} is served; omitted key is not. Linux {@code NET_ADDR_*}:
     * {@code 0} permanent, {@code 1} random, {@code 2} stolen, {@code 3} set.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkAddrAssignTypeFile(TraceEnvironmentConfig config,
                                                                            String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_ADDR_ASSIGN_TYPE_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_ADDR_ASSIGN_TYPE_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            if (!iface.isAddressAssignTypeConfigured()) {
                return null;
            }
            Integer addressAssignType = iface.getAddressAssignType();
            if (addressAssignType == null) {
                return null;
            }
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            byte[] data = (Integer.toString(addressAssignType.intValue()) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            String value = "path=" + pathname + ",name=" + name + ",format=addr_assign_type,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 addr_assign_type 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.interfaces} lists an entry with both {@code name} and explicit
     * {@code nameAssignType}, serve exact {@code /sys/class/net/<name>/name_assign_type}
     * as decimal ASCII {@code nameAssignType} + LF, via
     * {@link TraceEnvironmentConfig.NetworkInterfaceConfig#getNameAssignType()}.
     * Read-only: write or {@code O_DIRECTORY} returns {@code null} so the legacy open chain
     * continues. Sidecar kind {@code network_device} with path/name/format=name_assign_type/bytes
     * only (never the nameAssignType number, IP, MAC, flags, hardwareType, speedMbps, duplex,
     * linkIndex, txQueueLen, addressAssignType, operState, carrier, or other interface fields).
     * Returns {@code null} for unknown names, missing {@code nameAssignType}, other sys paths
     * (including address/mtu/type/speed/duplex/iflink/ifindex/tx_queue_len/operstate/carrier/
     * addr_assign_type), or non-read-only opens. {@code linux.files} exact map remains higher
     * priority in {@link #open}. Never inferred from name, mac, addressAssignType, hardwareType,
     * flags, carrier, operState, speedMbps, duplex, linkIndex, txQueueLen, or wifi.
     * Explicit {@code 0} is served; omitted key is not. Linux {@code NET_NAME_*}:
     * {@code 0} unknown, {@code 1} enum, {@code 2} predictable, {@code 3} user,
     * {@code 4} renamed.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkNameAssignTypeFile(TraceEnvironmentConfig config,
                                                                            String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_NAME_ASSIGN_TYPE_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_NAME_ASSIGN_TYPE_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            if (!iface.isNameAssignTypeConfigured()) {
                return null;
            }
            Integer nameAssignType = iface.getNameAssignType();
            if (nameAssignType == null) {
                return null;
            }
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            byte[] data = (Integer.toString(nameAssignType.intValue()) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            String value = "path=" + pathname + ",name=" + name + ",format=name_assign_type,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 name_assign_type 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.interfaces} lists an entry with both {@code name} and explicit
     * {@code linkLayerBroadcast}, serve exact {@code /sys/class/net/<name>/broadcast}
     * as UTF-8 lowercase canonical MAC + LF, via
     * {@link TraceEnvironmentConfig.NetworkInterfaceConfig#getLinkLayerBroadcast()}.
     * Read-only: write or {@code O_DIRECTORY} returns {@code null} so the legacy open chain
     * continues. Sidecar kind {@code network_device} with name/format=broadcast/bytes only
     * (never the link-layer broadcast text, IPv4 broadcast, MAC, hardwareType, flags, or
     * other interface fields). Returns {@code null} for unknown names, missing
     * {@code linkLayerBroadcast}, other sys paths (including address/mtu/type/speed/duplex/
     * iflink/ifindex/tx_queue_len/operstate/carrier/addr_assign_type/name_assign_type),
     * or non-read-only opens. {@code linux.files} exact map remains higher priority in
     * {@link #open}. Never inferred from mac, IPv4 broadcast, hardwareType, flags, or
     * other interface fields. Omitted key is not served.
     */
    private FileResult<AndroidFileIO> openConfiguredNetworkBroadcastFile(TraceEnvironmentConfig config,
                                                                        String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || !pathname.endsWith(SYS_NET_BROADCAST_SUFFIX)) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
            String name = iface.getName();
            if (name == null) {
                continue;
            }
            String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_BROADCAST_SUFFIX;
            if (!expected.equals(pathname)) {
                continue;
            }
            if (!iface.isLinkLayerBroadcastConfigured()) {
                return null;
            }
            String linkLayerBroadcast = iface.getLinkLayerBroadcast();
            if (linkLayerBroadcast == null) {
                return null;
            }
            // read-only: do not take over write or directory opens
            if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                return null;
            }
            byte[] data = (linkLayerBroadcast + "\n").getBytes(StandardCharsets.UTF_8);
            String value = "name=" + name + ",format=broadcast,bytes=" + data.length;
            TraceEnvironmentEventSink.emit(emulator, "network_device",
                    "read(\"" + pathname + "\")", value, "json-config",
                    "读取配置的网卡 broadcast 文件 " + pathname);
            return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
        }
        return null;
    }

    /**
     * When {@code network.ipv4Routes} is present, serve exact
     * {@code /proc/net/route}, {@code /proc/self/net/route}, and
     * {@code /proc/<emulatorPid>/net/route} as UTF-8 Linux route table text + LF.
     * Read-only: write or {@code O_DIRECTORY} returns {@code null} so the legacy open
     * chain continues. Sidecar kind {@code network_device} with path/format=proc-net-route/
     * routeCount/bytes only (never destination, gateway, mask, flags, interface names,
     * or other network fields). {@code linux.files} exact map remains higher priority
     * in {@link #open}. Never reads host network state; never infers from interfaces/wifi.
     */
    private FileResult<AndroidFileIO> openConfiguredIpv4RouteFile(TraceEnvironmentConfig config,
                                                                 String pathname, int oflags) {
        if (!isProcNetRoutePath(pathname)) {
            return null;
        }
        if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
            return null;
        }
        byte[] data = ConfiguredIpv4RouteFiles.renderProcNetRoute(config);
        if (data == null) {
            return null;
        }
        int routeCount = config.getNetworkIpv4Routes().size();
        String value = "path=" + pathname + ",format=proc-net-route,routeCount=" + routeCount
                + ",bytes=" + data.length;
        TraceEnvironmentEventSink.emit(emulator, "network_device",
                "read(\"" + pathname + "\")", value, "json-config",
                "读取配置的 IPv4 路由表 " + pathname);
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    private FileResult<AndroidFileIO> openConfiguredTcpFile(TraceEnvironmentConfig config,
                                                           String pathname, int oflags,
                                                           boolean ipv6) {
        if (!isProcNetTcpPath(pathname, ipv6)) {
            return null;
        }
        if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
            return null;
        }
        byte[] data = ipv6
                ? ConfiguredTcpFiles.renderProcNetTcp6(config)
                : ConfiguredTcpFiles.renderProcNetTcp(config);
        if (data == null) {
            return null;
        }
        int count = ipv6 ? config.getNetworkTcp6().size() : config.getNetworkTcp().size();
        String format = ipv6 ? "proc-net-tcp6" : "proc-net-tcp";
        String value = "path=" + pathname + ",format=" + format + ",rowCount=" + count
                + ",bytes=" + data.length;
        TraceEnvironmentEventSink.emit(emulator, "network_device",
                "read(\"" + pathname + "\")", value, "json-config",
                "读取配置的 TCP 表 " + pathname);
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    private FileResult<AndroidFileIO> openConfiguredPowerSupplyFile(TraceEnvironmentConfig config,
                                                                    String pathname, int oflags) {
        byte[] data = ConfiguredPowerSupplyFiles.render(config, pathname);
        if (data == null) {
            return null;
        }
        if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
            return null;
        }
        String format;
        if (ConfiguredPowerSupplyFiles.HEALTH_PATH.equals(pathname)) {
            format = "health";
        } else if (ConfiguredPowerSupplyFiles.VOLTAGE_NOW_PATH.equals(pathname)) {
            format = "voltage_now";
        } else {
            format = "temp";
        }
        TraceEnvironmentEventSink.emit(emulator, "android_battery",
                "read(\"" + pathname + "\")",
                "path=" + pathname + ",format=" + format + ",bytes=" + data.length,
                "json-config", "读取配置的 power_supply 文件");
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    private boolean isProcNetTcpPath(String pathname, boolean ipv6) {
        String leaf = ipv6 ? "tcp6" : "tcp";
        if (("/proc/net/" + leaf).equals(pathname) || ("/proc/self/net/" + leaf).equals(pathname)) {
            return true;
        }
        return ("/proc/" + emulator.getPid() + "/net/" + leaf).equals(pathname);
    }

    private FileResult<AndroidFileIO> openConfiguredProcessFile(TraceEnvironmentConfig config,
                                                               String pathname, int oflags) {
        if (pathname == null) {
            return null;
        }
        if ("/proc".equals(pathname)) {
            if ((oflags & 3) != 0) {
                return FileResult.failed(UnixEmulator.EACCES);
            }
            java.util.List<TraceEnvironmentConfig.LinuxProcessConfig> processes = config.getLinuxProcesses();
            DirectoryFileIO.DirectoryEntry[] entries =
                    new DirectoryFileIO.DirectoryEntry[processes.size() + 1];
            entries[0] = new DirectoryFileIO.DirectoryEntry(false, "self");
            for (int i = 0; i < processes.size(); i++) {
                entries[i + 1] = new DirectoryFileIO.DirectoryEntry(false,
                        Integer.toString(processes.get(i).getPid()));
            }
            TraceEnvironmentEventSink.emit(emulator, "linux_proc", "open(\"/proc\")",
                    "count=" + processes.size(), "json-config", "枚举配置的进程列表");
            return FileResult.<AndroidFileIO>success(new DirectoryFileIO(oflags, pathname, entries));
        }
        if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
            return null;
        }
        Integer pid = parseConfiguredProcPid(pathname, config);
        if (pid == null) {
            return null;
        }
        TraceEnvironmentConfig.LinuxProcessConfig process = config.findLinuxProcess(pid.intValue());
        if (process == null) {
            return null;
        }
        String suffix = pathname.substring(pathname.lastIndexOf('/') + 1);
        byte[] data;
        if ("cmdline".equals(suffix)) {
            data = ConfiguredProcessFiles.renderCmdline(process);
        } else if ("comm".equals(suffix)) {
            data = ConfiguredProcessFiles.renderComm(process);
        } else if ("exe".equals(suffix)) {
            if (process.getExe() == null) {
                return null;
            }
            data = process.getExe().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } else {
            return null;
        }
        if (data == null) {
            return null;
        }
        TraceEnvironmentEventSink.emit(emulator, "linux_proc",
                "read(\"" + pathname + "\")",
                "path=" + pathname + ",format=" + suffix + ",bytes=" + data.length,
                "json-config", "读取配置的进程文件");
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    private Integer parseConfiguredProcPid(String pathname, TraceEnvironmentConfig config) {
        if (pathname.startsWith("/proc/self/")) {
            return Integer.valueOf(emulator.getPid());
        }
        if (!pathname.startsWith("/proc/")) {
            return null;
        }
        String rest = pathname.substring("/proc/".length());
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        String pidText = rest.substring(0, slash);
        try {
            return Integer.valueOf(Integer.parseInt(pidText));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Exact {@code /proc/net/route} and self/emulator-pid net-namespace aliases only. */
    private boolean isProcNetRoutePath(String pathname) {
        if ("/proc/net/route".equals(pathname) || "/proc/self/net/route".equals(pathname)) {
            return true;
        }
        return ("/proc/" + emulator.getPid() + "/net/route").equals(pathname);
    }

    /**
     * When {@code network.interfaceStats} is present, serve exact
     * {@code /proc/net/dev}, {@code /proc/self/net/dev}, and
     * {@code /proc/<emulatorPid>/net/dev} as UTF-8 Linux {@code /proc/net/dev} text + LF.
     * Read-only: write or {@code O_DIRECTORY} returns {@code null} so the legacy open
     * chain continues. Sidecar kind {@code network_device} with path/format=proc-net-dev/
     * interfaceCount/bytes only (never interface names, counters, IP, MAC, flags, or
     * routes). {@code linux.files} exact map remains higher priority in {@link #open}.
     * Never reads host network state; never infers from interfaces/wifi/ipv4Routes.
     */
    private FileResult<AndroidFileIO> openConfiguredInterfaceStatsFile(TraceEnvironmentConfig config,
                                                                      String pathname, int oflags) {
        if (!isProcNetDevPath(pathname)) {
            return null;
        }
        if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
            return null;
        }
        byte[] data = ConfiguredInterfaceStatsFiles.renderProcNetDev(config);
        if (data == null) {
            return null;
        }
        int interfaceCount = config.getNetworkInterfaceStats().size();
        String value = "path=" + pathname + ",format=proc-net-dev,interfaceCount=" + interfaceCount
                + ",bytes=" + data.length;
        TraceEnvironmentEventSink.emit(emulator, "network_device",
                "read(\"" + pathname + "\")", value, "json-config",
                "读取配置的网卡收发统计 " + pathname);
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    /** Exact {@code /proc/net/dev} and self/emulator-pid net-namespace aliases only. */
    private boolean isProcNetDevPath(String pathname) {
        if ("/proc/net/dev".equals(pathname) || "/proc/self/net/dev".equals(pathname)) {
            return true;
        }
        return ("/proc/" + emulator.getPid() + "/net/dev").equals(pathname);
    }

    /**
     * When {@code network.interfaceStats} lists an entry for {@code name}, serve exact
     * {@code /sys/class/net/<name>/statistics/<field>} for the sixteen existing counters
     * as decimal ASCII + LF via {@link ConfiguredInterfaceStatsFiles#renderSysfsStatistic}.
     * Only that same-interface stats row is used; never inferred from interfaces, routes,
     * wifi, or other fields. Read-only: write or {@code O_DIRECTORY} returns {@code null}
     * so the legacy open chain continues. Sidecar kind {@code network_device} with
     * path/name/{@code format=statistics-<field>}/bytes only (never counters, IP, MAC, flags,
     * or other network values). Returns {@code null} for unknown names, missing stats
     * rows (including explicit {@code []}), unsupported kernel fields, other sys paths,
     * or non-read-only opens. {@code linux.files} exact map remains higher priority in
     * {@link #open}. Never reads host {@code NetworkInterface} or real sysfs.
     */
    private FileResult<AndroidFileIO> openConfiguredInterfaceStatsSysfsFile(
            TraceEnvironmentConfig config, String pathname, int oflags) {
        if (pathname == null || !pathname.startsWith(SYS_CLASS_NET_PREFIX)
                || pathname.indexOf(SYS_NET_STATISTICS_INFIX) < 0) {
            return null;
        }
        for (TraceEnvironmentConfig.NetworkInterfaceStatsConfig row
                : config.getNetworkInterfaceStats()) {
            String name = row.getInterfaceName();
            if (name == null) {
                continue;
            }
            String[] fields = ConfiguredInterfaceStatsFiles.SYSFS_STATISTIC_FIELDS;
            for (int i = 0; i < fields.length; i++) {
                String field = fields[i];
                String expected = SYS_CLASS_NET_PREFIX + name + SYS_NET_STATISTICS_INFIX + field;
                if (!expected.equals(pathname)) {
                    continue;
                }
                if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
                    return null;
                }
                byte[] data = ConfiguredInterfaceStatsFiles.renderSysfsStatistic(row, field);
                if (data == null) {
                    return null;
                }
                String value = "path=" + pathname + ",name=" + name
                        + ",format=statistics-" + field + ",bytes=" + data.length;
                TraceEnvironmentEventSink.emit(emulator, "network_device",
                        "read(\"" + pathname + "\")", value, "json-config",
                        "读取配置的网卡统计 " + pathname);
                return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
            }
        }
        return null;
    }

    /**
     * When {@code network.ipv6Addresses} is present, serve exact
     * {@code /proc/net/if_inet6}, {@code /proc/self/net/if_inet6}, and
     * {@code /proc/<emulatorPid>/net/if_inet6} as UTF-8 Linux {@code if_inet6} text
     * (explicit {@code []} is zero bytes, no header). Read-only: write or
     * {@code O_DIRECTORY} returns {@code null} so the legacy open chain continues.
     * Sidecar kind {@code network_device} with path/format=proc-net-if-inet6/
     * addressCount/bytes only (never address, interface name, prefix, scope, flags,
     * IP, MAC, or routes). {@code linux.files} exact map remains higher priority
     * in {@link #open}. Never reads host network state; never infers address/prefix/
     * scope/flags; interface index is reused from matching {@code interfaces[].index}.
     */
    private FileResult<AndroidFileIO> openConfiguredIpv6AddressFile(TraceEnvironmentConfig config,
                                                                   String pathname, int oflags) {
        if (!isProcNetIfInet6Path(pathname)) {
            return null;
        }
        if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
            return null;
        }
        byte[] data = ConfiguredIpv6AddressFiles.renderProcNetIfInet6(config);
        if (data == null) {
            return null;
        }
        int addressCount = config.getNetworkIpv6Addresses().size();
        String value = "path=" + pathname + ",format=proc-net-if-inet6,addressCount=" + addressCount
                + ",bytes=" + data.length;
        TraceEnvironmentEventSink.emit(emulator, "network_device",
                "read(\"" + pathname + "\")", value, "json-config",
                "读取配置的 IPv6 接口地址表 " + pathname);
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    /** Exact {@code /proc/net/if_inet6} and self/emulator-pid net-namespace aliases only. */
    private boolean isProcNetIfInet6Path(String pathname) {
        if ("/proc/net/if_inet6".equals(pathname) || "/proc/self/net/if_inet6".equals(pathname)) {
            return true;
        }
        return ("/proc/" + emulator.getPid() + "/net/if_inet6").equals(pathname);
    }

    /**
     * When {@code network.arpEntries} is present, serve exact
     * {@code /proc/net/arp}, {@code /proc/self/net/arp}, and
     * {@code /proc/<emulatorPid>/net/arp} as UTF-8 Linux ARP table text + LF.
     * Read-only: write or {@code O_DIRECTORY} returns {@code null} so the legacy open
     * chain continues. Sidecar kind {@code network_device} with path/format=proc-net-arp/
     * entryCount/bytes only (never interface names, IP, MAC, flags, hardware type, or
     * other network fields). {@code linux.files} exact map remains higher priority
     * in {@link #open}. Never reads host network state; never infers from
     * interfaces/wifi/ipv4Routes/interfaceStats/ipv6Addresses. Never implements ARP ioctl.
     */
    private FileResult<AndroidFileIO> openConfiguredArpEntryFile(TraceEnvironmentConfig config,
                                                                String pathname, int oflags) {
        if (!isProcNetArpPath(pathname)) {
            return null;
        }
        if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
            return null;
        }
        byte[] data = ConfiguredArpEntryFiles.renderProcNetArp(config);
        if (data == null) {
            return null;
        }
        int entryCount = config.getNetworkArpEntries().size();
        String value = "path=" + pathname + ",format=proc-net-arp,entryCount=" + entryCount
                + ",bytes=" + data.length;
        TraceEnvironmentEventSink.emit(emulator, "network_device",
                "read(\"" + pathname + "\")", value, "json-config",
                "读取配置的 ARP 表 " + pathname);
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    /** Exact {@code /proc/net/arp} and self/emulator-pid net-namespace aliases only. */
    private boolean isProcNetArpPath(String pathname) {
        if ("/proc/net/arp".equals(pathname) || "/proc/self/net/arp".equals(pathname)) {
            return true;
        }
        return ("/proc/" + emulator.getPid() + "/net/arp").equals(pathname);
    }

    /**
     * When {@code network.igmpMemberships} is present, serve exact
     * {@code /proc/net/igmp}, {@code /proc/self/net/igmp}, and
     * {@code /proc/<emulatorPid>/net/igmp} as UTF-8 Linux IGMP membership text + LF.
     * Read-only: write or {@code O_DIRECTORY} returns {@code null} so the legacy open
     * chain continues. Sidecar kind {@code network_device} with path/format=proc-net-igmp/
     * membershipCount/interfaceCount/bytes only (never interface names, index, group
     * addresses, querier, users, timer, or reporter). {@code linux.files} exact map
     * remains higher priority in {@link #open}. Never reads host network state; never
     * infers from interfaces/wifi/ipv4Routes/interfaceStats/ipv6Addresses/arpEntries.
     * Never implements IGMP sockets, group join/leave, ioctl, writes, or probes.
     */
    private FileResult<AndroidFileIO> openConfiguredIgmpMembershipFile(TraceEnvironmentConfig config,
                                                                      String pathname, int oflags) {
        if (!isProcNetIgmpPath(pathname)) {
            return null;
        }
        if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
            return null;
        }
        byte[] data = ConfiguredIgmpMembershipFiles.renderProcNetIgmp(config);
        if (data == null) {
            return null;
        }
        int membershipCount = config.getNetworkIgmpMemberships().size();
        int interfaceCount = ConfiguredIgmpMembershipFiles.countDistinctInterfaces(config);
        String value = "path=" + pathname + ",format=proc-net-igmp,membershipCount="
                + membershipCount + ",interfaceCount=" + interfaceCount
                + ",bytes=" + data.length;
        TraceEnvironmentEventSink.emit(emulator, "network_device",
                "read(\"" + pathname + "\")", value, "json-config",
                "读取配置的 IGMP 成员表 " + pathname);
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    /** Exact {@code /proc/net/igmp} and self/emulator-pid net-namespace aliases only. */
    private boolean isProcNetIgmpPath(String pathname) {
        if ("/proc/net/igmp".equals(pathname) || "/proc/self/net/igmp".equals(pathname)) {
            return true;
        }
        return ("/proc/" + emulator.getPid() + "/net/igmp").equals(pathname);
    }

    /**
     * When {@code network.igmp6Memberships} is present, serve exact
     * {@code /proc/net/igmp6}, {@code /proc/self/net/igmp6}, and
     * {@code /proc/<emulatorPid>/net/igmp6} as UTF-8 Linux igmp6 membership text
     * (explicit {@code []} is zero bytes, no header). Read-only: write or
     * {@code O_DIRECTORY} returns {@code null} so the legacy open chain continues.
     * Sidecar kind {@code network_device} with path/format=proc-net-igmp6/
     * membershipCount/interfaceCount/bytes only (never interface names, index,
     * IPv6 group addresses, flags, or timer). {@code linux.files} exact map
     * remains higher priority in {@link #open}. Never reads host network state;
     * never infers from interfaces/wifi/ipv4Routes/interfaceStats/ipv6Addresses/
     * arpEntries/igmpMemberships. Never implements MLD sockets, group join/leave,
     * ioctl, writes, or probes.
     */
    private FileResult<AndroidFileIO> openConfiguredIgmp6MembershipFile(TraceEnvironmentConfig config,
                                                                       String pathname, int oflags) {
        if (!isProcNetIgmp6Path(pathname)) {
            return null;
        }
        if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
            return null;
        }
        byte[] data = ConfiguredIgmp6MembershipFiles.renderProcNetIgmp6(config);
        if (data == null) {
            return null;
        }
        int membershipCount = config.getNetworkIgmp6Memberships().size();
        int interfaceCount = ConfiguredIgmp6MembershipFiles.countDistinctInterfaces(config);
        String value = "path=" + pathname + ",format=proc-net-igmp6,membershipCount="
                + membershipCount + ",interfaceCount=" + interfaceCount
                + ",bytes=" + data.length;
        TraceEnvironmentEventSink.emit(emulator, "network_device",
                "read(\"" + pathname + "\")", value, "json-config",
                "读取配置的 IGMP6 成员表 " + pathname);
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    /** Exact {@code /proc/net/igmp6} and self/emulator-pid net-namespace aliases only. */
    private boolean isProcNetIgmp6Path(String pathname) {
        if ("/proc/net/igmp6".equals(pathname) || "/proc/self/net/igmp6".equals(pathname)) {
            return true;
        }
        return ("/proc/" + emulator.getPid() + "/net/igmp6").equals(pathname);
    }

    /**
     * When {@code network.wirelessProcStats} is present, serve exact
     * {@code /proc/net/wireless}, {@code /proc/self/net/wireless}, and
     * {@code /proc/<emulatorPid>/net/wireless} as UTF-8 Linux wireless table text
     * (explicit empty {@code entries} is the two header lines only). Read-only:
     * write or {@code O_DIRECTORY} returns {@code null} so the legacy open chain
     * continues. Sidecar kind {@code network_device} with
     * path/format=proc-net-wireless/interfaceCount/bytes only (never interface
     * names, wireless version, or statistic values). {@code linux.files} exact
     * map remains higher priority in {@link #open}. Never reads host network
     * state; never infers from interfaces/wifi/ipv4Routes/interfaceStats/
     * ipv6Addresses/arpEntries/igmpMemberships/igmp6Memberships/
     * linkLayerMulticastEntries. Never implements wireless ioctl, netlink, scan,
     * or real Wi-Fi.
     */
    private FileResult<AndroidFileIO> openConfiguredWirelessProcStatsFile(
            TraceEnvironmentConfig config, String pathname, int oflags) {
        if (!isProcNetWirelessPath(pathname)) {
            return null;
        }
        if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
            return null;
        }
        byte[] data = ConfiguredWirelessProcStatsFiles.renderProcNetWireless(config);
        if (data == null) {
            return null;
        }
        int interfaceCount = config.getNetworkWirelessProcStatsEntries().size();
        String value = "path=" + pathname + ",format=proc-net-wireless,interfaceCount="
                + interfaceCount + ",bytes=" + data.length;
        TraceEnvironmentEventSink.emit(emulator, "network_device",
                "read(\"" + pathname + "\")", value, "json-config",
                "读取配置的无线统计 " + pathname);
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    /** Exact {@code /proc/net/wireless} and self/emulator-pid net-namespace aliases only. */
    private boolean isProcNetWirelessPath(String pathname) {
        if ("/proc/net/wireless".equals(pathname) || "/proc/self/net/wireless".equals(pathname)) {
            return true;
        }
        return ("/proc/" + emulator.getPid() + "/net/wireless").equals(pathname);
    }

    /**
     * When {@code network.linkLayerMulticastEntries} is present, serve exact
     * {@code /proc/net/dev_mcast}, {@code /proc/self/net/dev_mcast}, and
     * {@code /proc/<emulatorPid>/net/dev_mcast} as UTF-8 Linux device multicast
     * text (explicit {@code []} is zero bytes, no header). Read-only: write or
     * {@code O_DIRECTORY} returns {@code null} so the legacy open chain continues.
     * Sidecar kind {@code network_device} with path/format=proc-net-dev-mcast/
     * entryCount/interfaceCount/bytes only (never interface names, index, MAC,
     * reference count, or globalUse). {@code linux.files} exact map remains higher
     * priority in {@link #open}. Never reads host network state; never infers from
     * interfaces.mac/linkLayerBroadcast/wifi/ipv4Routes/interfaceStats/ipv6Addresses/
     * arpEntries/igmpMemberships/igmp6Memberships. Never implements multicast join,
     * socket, ioctl, netlink, writes, or probes.
     */
    private FileResult<AndroidFileIO> openConfiguredLinkLayerMulticastFile(
            TraceEnvironmentConfig config, String pathname, int oflags) {
        if (!isProcNetDevMcastPath(pathname)) {
            return null;
        }
        if ((oflags & 3) != 0 || (oflags & O_DIRECTORY) != 0) {
            return null;
        }
        byte[] data = ConfiguredLinkLayerMulticastFiles.renderProcNetDevMcast(config);
        if (data == null) {
            return null;
        }
        int entryCount = config.getNetworkLinkLayerMulticastEntries().size();
        int interfaceCount = ConfiguredLinkLayerMulticastFiles.countDistinctInterfaces(config);
        String value = "path=" + pathname + ",format=proc-net-dev-mcast,entryCount="
                + entryCount + ",interfaceCount=" + interfaceCount
                + ",bytes=" + data.length;
        TraceEnvironmentEventSink.emit(emulator, "network_device",
                "read(\"" + pathname + "\")", value, "json-config",
                "读取配置的链路层多播表 " + pathname);
        return FileResult.<AndroidFileIO>success(new ByteArrayFileIO(oflags, pathname, data));
    }

    /** Exact {@code /proc/net/dev_mcast} and self/emulator-pid net-namespace aliases only. */
    private boolean isProcNetDevMcastPath(String pathname) {
        if ("/proc/net/dev_mcast".equals(pathname) || "/proc/self/net/dev_mcast".equals(pathname)) {
            return true;
        }
        return ("/proc/" + emulator.getPid() + "/net/dev_mcast").equals(pathname);
    }

    public LogCatHandler getLogCatHandler() {
        return null;
    }

    @Override
    protected void initialize(File rootDir) throws IOException {
        super.initialize(rootDir);

        FileUtils.forceMkdir(new File(rootDir, "system"));
        FileUtils.forceMkdir(new File(rootDir, "data"));
    }

    @Override
    public AndroidFileIO createSimpleFileIO(File file, int oflags, String path) {
        return new SimpleFileIO(oflags, file, path);
    }

    @Override
    public AndroidFileIO createDirectoryFileIO(File file, int oflags, String path) {
        return new DirectoryFileIO(oflags, path, file);
    }

    @Override
    protected AndroidFileIO createStdin(int oflags) {
        return new Stdin(oflags);
    }

    @Override
    protected AndroidFileIO createStdout(int oflags, File stdio, String pathname) {
        return new Stdout(oflags, stdio, pathname, IO.STDERR.equals(pathname), null);
    }

    @Override
    protected boolean hasCreat(int oflags) {
        return (oflags & O_CREAT) != 0;
    }

    @Override
    protected boolean hasDirectory(int oflags) {
        return (oflags & O_DIRECTORY) != 0;
    }

    @Override
    protected boolean hasAppend(int oflags) {
        return (oflags & O_APPEND) != 0;
    }

    @Override
    protected boolean hasExcl(int oflags) {
        return (oflags & O_EXCL) != 0;
    }
}

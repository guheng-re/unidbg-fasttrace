package com.github.unidbg.file.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link ConfiguredProcFiles}. No filesystem I/O; free/close via emulator only.
 */
public class ConfiguredProcFilesTest {

    private static final String FULL_JSON = "{"
            + "\"process\":{"
            + "\"processName\":\"com.demo.app\","
            + "\"pid\":12345,"
            + "\"ppid\":1,"
            + "\"uid\":1000,"
            + "\"euid\":1000,"
            + "\"gid\":1000,"
            + "\"egid\":1000"
            + "},"
            + "\"linux\":{\"proc\":{"
            + "\"state\":\"R\","
            + "\"tracerPid\":0,"
            + "\"threadCount\":8,"
            + "\"cmdline\":[\"com.demo.app\",\"--flag\",\"\"],"
            + "\"cgroups\":[\"0::/\",\"1:name=systemd:/\"],"
            + "\"startTimeTicks\":999,"
            + "\"virtualMemoryBytes\":4194304,"
            + "\"residentSetPages\":100"
            + "}}}";

    @Test
    public void testNotConfiguredReturnsNull() throws Exception {
        AndroidEmulator emulator = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
            assertNull(ConfiguredProcFiles.renderStatus(emulator, missing));
            assertNull(ConfiguredProcFiles.renderCmdline(emulator, missing));
            assertNull(ConfiguredProcFiles.renderCgroup(emulator, missing));
            assertNull(ConfiguredProcFiles.renderStat(emulator, missing));
            assertNull(ConfiguredProcFiles.renderStatm(emulator, missing));
            assertNull(ConfiguredProcFiles.renderIo(emulator, missing));
            assertNull(ConfiguredProcFiles.renderBootId(missing));
            assertNull(ConfiguredProcFiles.renderRandomUuid(missing));
            assertNull(ConfiguredProcFiles.renderOomScoreAdj(missing));
            assertNull(ConfiguredProcFiles.renderOomScore(missing));
            assertNull(ConfiguredProcFiles.renderSelinuxContext(missing));
            assertNull(ConfiguredProcFiles.renderComm(missing));
            assertNull(ConfiguredProcFiles.renderWchan(missing));
            assertNull(ConfiguredProcFiles.renderUnameHostname(missing));
            assertNull(ConfiguredProcFiles.renderUnameOsrelease(missing));
            assertNull(ConfiguredProcFiles.renderUnameVersion(missing));
            assertNull(ConfiguredProcFiles.renderUnameDomainname(missing));
            assertNull(ConfiguredProcFiles.renderUnameOstype(missing));
            // environ always renders (defaults when linux.environ absent)
            byte[] defaultEnviron = ConfiguredProcFiles.renderEnviron(missing);
            assertNotNull(defaultEnviron);
            assertTrue(defaultEnviron.length > 0);
            byte[] nullEnviron = ConfiguredProcFiles.renderEnviron(null);
            assertNotNull(nullEnviron);
            assertArrayEquals(defaultEnviron, nullEnviron);
            assertNull(ConfiguredProcFiles.renderStatus(emulator, null));
            assertNull(ConfiguredProcFiles.renderStatm(emulator, null));
            assertNull(ConfiguredProcFiles.renderIo(emulator, null));
            assertNull(ConfiguredProcFiles.renderBootId(null));
            assertNull(ConfiguredProcFiles.renderRandomUuid(null));
            assertNull(ConfiguredProcFiles.renderOomScoreAdj(null));
            assertNull(ConfiguredProcFiles.renderOomScore(null));
            assertNull(ConfiguredProcFiles.renderSelinuxContext(null));
            assertNull(ConfiguredProcFiles.renderComm(null));
            assertNull(ConfiguredProcFiles.renderWchan(null));
            assertNull(ConfiguredProcFiles.renderUnameHostname(null));
            assertNull(ConfiguredProcFiles.renderUnameOsrelease(null));
            assertNull(ConfiguredProcFiles.renderUnameVersion(null));
            assertNull(ConfiguredProcFiles.renderUnameDomainname(null));
            assertNull(ConfiguredProcFiles.renderUnameOstype(null));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testExplicitEmptyProcUsesDefaults() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        AndroidEmulator emulator = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();

            String status = new String(ConfiguredProcFiles.renderStatus(emulator, config),
                    StandardCharsets.UTF_8);
            assertTrue(status.contains("Name:\tdemo\n"));
            assertTrue(status.contains("State:\tS (sleeping)\n"));
            assertTrue(status.contains("Tgid:\t42\n"));
            assertTrue(status.contains("Pid:\t42\n"));
            assertTrue(status.contains("PPid:\t1\n"));
            assertTrue(status.contains("TracerPid:\t0\n"));
            assertTrue(status.contains("Uid:\t100\t101\t101\t101\n"));
            assertTrue(status.contains("Gid:\t200\t201\t201\t201\n"));
            assertTrue(status.contains("Threads:\t1\n"));
            assertTrue(status.contains("VmSize:\t0 kB\n"));
            assertTrue(status.contains("VmRSS:\t0 kB\n"));

            // missing cmdline → processName + NUL
            byte[] cmd = ConfiguredProcFiles.renderCmdline(emulator, config);
            assertArrayEquals(new byte[]{'d', 'e', 'm', 'o', 0}, cmd);

            // missing cgroups → 0::/\n
            assertEquals("0::/\n", new String(ConfiguredProcFiles.renderCgroup(emulator, config),
                    StandardCharsets.UTF_8));

            String[] fields = parseStatFields(ConfiguredProcFiles.renderStat(emulator, config));
            assertEquals(52, fields.length);
            assertEquals("42", fields[0]);
            assertEquals("(demo)", fields[1]);
            assertEquals("S", fields[2]);
            assertEquals("1", fields[3]);
            assertEquals("42", fields[4]);
            assertEquals("42", fields[5]);
            assertEquals("0", fields[6]);
            assertEquals("0", fields[7]);
            assertEquals("4194304", fields[8]);
            assertEquals("20", fields[17]);
            assertEquals("0", fields[18]);
            assertEquals("1", fields[19]);
            assertEquals("0", fields[21]);
            assertEquals("0", fields[22]);
            assertEquals("0", fields[23]);
            assertEquals("18446744073709551615", fields[24]);
            assertEquals("17", fields[37]);
            assertEquals("0", fields[38]);

            // empty proc → seven zeros (size/resident/shared/text/lib/data/dt)
            assertEquals("0 0 0 0 0 0 0\n",
                    new String(ConfiguredProcFiles.renderStatm(emulator, config),
                            StandardCharsets.UTF_8));

            // empty proc → seven io lines with 0
            assertEquals("rchar: 0\n"
                            + "wchar: 0\n"
                            + "syscr: 0\n"
                            + "syscw: 0\n"
                            + "read_bytes: 0\n"
                            + "write_bytes: 0\n"
                            + "cancelled_write_bytes: 0\n",
                    new String(ConfiguredProcFiles.renderIo(emulator, config),
                            StandardCharsets.UTF_8));
            // neither seccompMode nor noNewPrivs nor Cap* / Sig* → historical status has no those lines
            assertFalse(status.contains("Seccomp:"));
            assertFalse(status.contains("NoNewPrivs:"));
            assertFalse(status.contains("CapEff:"));
            assertFalse(status.contains("CapInh:"));
            assertFalse(status.contains("CapPrm:"));
            assertFalse(status.contains("CapBnd:"));
            assertFalse(status.contains("CapAmb:"));
            assertFalse(status.contains("SigBlk:"));
            assertFalse(status.contains("SigIgn:"));
            assertFalse(status.contains("SigCgt:"));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testRenderLimitsArm32() throws Exception {
        runRenderLimits(false);
    }

    @Test
    public void testRenderLimitsArm64() throws Exception {
        runRenderLimits(true);
    }

    private static void runRenderLimits(boolean is64Bit) throws Exception {
        AndroidEmulator emulator = null;
        try {
            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(
                    "{\"linux\":{\"proc\":{\"state\":\"S\"}}}");
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(absent)
                    .build();
            assertNull(ConfiguredProcFiles.renderLimits(absent));
            assertNull(ConfiguredProcFiles.renderLimits(
                    TraceEnvironmentConfig.parse("{}")));

            TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                    "{\"linux\":{\"proc\":{\"limits\":[]}}}");
            byte[] emptyBytes = ConfiguredProcFiles.renderLimits(empty);
            assertNotNull(emptyBytes);
            assertEquals(0, emptyBytes.length);

            TraceEnvironmentConfig ordered = TraceEnvironmentConfig.parse("{"
                    + "\"linux\":{\"proc\":{\"limits\":["
                    + "\"Limit                     Soft Limit           Hard Limit           Units     \","
                    + "\"Max cpu time              unlimited            unlimited            seconds   \","
                    + "\"Max file size             unlimited            unlimited            bytes     \""
                    + "]}}}"
            );
            String text = new String(ConfiguredProcFiles.renderLimits(ordered),
                    StandardCharsets.UTF_8);
            assertEquals(
                    "Limit                     Soft Limit           Hard Limit           Units     \n"
                            + "Max cpu time              unlimited            unlimited            seconds   \n"
                            + "Max file size             unlimited            unlimited            bytes     \n",
                    text);
            // order preserved
            assertTrue(text.indexOf("Max cpu time") < text.indexOf("Max file size"));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testStatusSignalHexArm32() throws Exception {
        runStatusSignalHex(false);
    }

    @Test
    public void testStatusSignalHexArm64() throws Exception {
        runStatusSignalHex(true);
    }

    private static void runStatusSignalHex(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig base = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(base)
                    .build();
            byte[] historical = ConfiguredProcFiles.renderStatus(emulator, base);
            String historicalText = new String(historical, StandardCharsets.UTF_8);
            assertFalse(historicalText.contains("SigBlk:"));
            assertFalse(historicalText.contains("SigIgn:"));
            assertFalse(historicalText.contains("SigCgt:"));
            assertTrue(historicalText.endsWith("VmRSS:\t0 kB\n"));

            // individual fields
            TraceEnvironmentConfig blkOnly = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{\"signalBlockedHex\":\"0000000000000000\"}}"
                    + "}");
            String blkText = new String(ConfiguredProcFiles.renderStatus(emulator, blkOnly),
                    StandardCharsets.UTF_8);
            assertTrue(blkText.startsWith(historicalText));
            assertTrue(blkText.endsWith("SigBlk:\t0000000000000000\n"));
            assertFalse(blkText.contains("SigIgn:"));
            assertFalse(blkText.contains("SigCgt:"));
            assertFalse(blkText.contains("CapInh:"));

            TraceEnvironmentConfig ignOnly = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42},"
                    + "\"linux\":{\"proc\":{\"signalIgnoredHex\":\"0000000000001000\"}}}");
            assertTrue(new String(ConfiguredProcFiles.renderStatus(emulator, ignOnly),
                    StandardCharsets.UTF_8).contains("SigIgn:\t0000000000001000\n"));

            TraceEnvironmentConfig cgtOnly = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42},"
                    + "\"linux\":{\"proc\":{\"signalCaughtHex\":\"0000000180000000\"}}}");
            assertTrue(new String(ConfiguredProcFiles.renderStatus(emulator, cgtOnly),
                    StandardCharsets.UTF_8).contains("SigCgt:\t0000000180000000\n"));

            // uppercase normalize
            TraceEnvironmentConfig upper = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42},"
                    + "\"linux\":{\"proc\":{\"signalBlockedHex\":\"00000000FFFFFFFF\"}}}");
            assertTrue(new String(ConfiguredProcFiles.renderStatus(emulator, upper),
                    StandardCharsets.UTF_8).contains("SigBlk:\t00000000ffffffff\n"));

            // full order: SigBlk → SigIgn → SigCgt → CapInh → … before Seccomp
            TraceEnvironmentConfig all = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{"
                    + "\"signalBlockedHex\":\"1111111111111111\","
                    + "\"signalIgnoredHex\":\"2222222222222222\","
                    + "\"signalCaughtHex\":\"3333333333333333\","
                    + "\"capInheritableHex\":\"0000000000000000\","
                    + "\"seccompMode\":1,"
                    + "\"noNewPrivs\":false}}"
                    + "}");
            String allText = new String(ConfiguredProcFiles.renderStatus(emulator, all),
                    StandardCharsets.UTF_8);
            assertTrue(allText.startsWith(historicalText));
            assertTrue(allText.endsWith(
                    "SigBlk:\t1111111111111111\n"
                            + "SigIgn:\t2222222222222222\n"
                            + "SigCgt:\t3333333333333333\n"
                            + "CapInh:\t0000000000000000\n"
                            + "Seccomp:\t1\n"
                            + "NoNewPrivs:\t0\n"));
            int blkIdx = allText.indexOf("SigBlk:\t");
            int ignIdx = allText.indexOf("SigIgn:\t");
            int cgtIdx = allText.indexOf("SigCgt:\t");
            int inhIdx = allText.indexOf("CapInh:\t");
            assertTrue(blkIdx >= 0 && ignIdx > blkIdx && cgtIdx > ignIdx && inhIdx > cgtIdx);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testStatusCapEffectiveHexArm32() throws Exception {
        runStatusCapEffectiveHex(false);
    }

    @Test
    public void testStatusCapEffectiveHexArm64() throws Exception {
        runStatusCapEffectiveHex(true);
    }

    private static void runStatusCapEffectiveHex(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig base = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(base)
                    .build();
            byte[] historical = ConfiguredProcFiles.renderStatus(emulator, base);
            String historicalText = new String(historical, StandardCharsets.UTF_8);
            assertFalse(historicalText.contains("CapEff:"));
            assertFalse(historicalText.contains("CapInh:"));
            assertTrue(historicalText.endsWith("VmRSS:\t0 kB\n"));

            TraceEnvironmentConfig capOnly = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{\"capEffectiveHex\":\"0000001fffffffff\"}}"
                    + "}");
            String capText = new String(ConfiguredProcFiles.renderStatus(emulator, capOnly),
                    StandardCharsets.UTF_8);
            assertTrue(capText.startsWith(historicalText));
            assertTrue(capText.endsWith("CapEff:\t0000001fffffffff\n"));
            assertFalse(capText.contains("CapInh:"));
            assertFalse(capText.contains("CapPrm:"));
            assertFalse(capText.contains("CapBnd:"));
            assertFalse(capText.contains("CapAmb:"));
            assertFalse(capText.contains("Seccomp:"));
            assertFalse(capText.contains("NoNewPrivs:"));

            // uppercase input normalized to lowercase in CapEff line
            TraceEnvironmentConfig capUpper = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42},"
                    + "\"linux\":{\"proc\":{\"capEffectiveHex\":\"ABCDEF0123456789\"}}}");
            assertTrue(new String(ConfiguredProcFiles.renderStatus(emulator, capUpper),
                    StandardCharsets.UTF_8).contains("CapEff:\tabcdef0123456789\n"));

            // CapEff before Seccomp/NoNewPrivs
            TraceEnvironmentConfig all = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{"
                    + "\"capEffectiveHex\":\"0000000000000000\","
                    + "\"seccompMode\":1,"
                    + "\"noNewPrivs\":false}}"
                    + "}");
            String allText = new String(ConfiguredProcFiles.renderStatus(emulator, all),
                    StandardCharsets.UTF_8);
            assertTrue(allText.startsWith(historicalText));
            assertTrue(allText.endsWith(
                    "CapEff:\t0000000000000000\nSeccomp:\t1\nNoNewPrivs:\t0\n"));
            int capIdx = allText.indexOf("CapEff:\t");
            int secIdx = allText.indexOf("Seccomp:\t");
            int nnpIdx = allText.indexOf("NoNewPrivs:\t");
            assertTrue(capIdx >= 0 && secIdx > capIdx && nnpIdx > secIdx);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testStatusCapPermittedHexArm32() throws Exception {
        runStatusCapPermittedHex(false);
    }

    @Test
    public void testStatusCapPermittedHexArm64() throws Exception {
        runStatusCapPermittedHex(true);
    }

    private static void runStatusCapPermittedHex(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig base = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(base)
                    .build();
            byte[] historical = ConfiguredProcFiles.renderStatus(emulator, base);
            String historicalText = new String(historical, StandardCharsets.UTF_8);
            assertFalse(historicalText.contains("CapPrm:"));
            assertFalse(historicalText.contains("CapEff:"));
            assertTrue(historicalText.endsWith("VmRSS:\t0 kB\n"));

            // CapPrm only — independent of CapEff
            TraceEnvironmentConfig prmOnly = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{\"capPermittedHex\":\"0000001fffffffff\"}}"
                    + "}");
            String prmText = new String(ConfiguredProcFiles.renderStatus(emulator, prmOnly),
                    StandardCharsets.UTF_8);
            assertTrue(prmText.startsWith(historicalText));
            assertTrue(prmText.endsWith("CapPrm:\t0000001fffffffff\n"));
            assertFalse(prmText.contains("CapEff:"));
            assertFalse(prmText.contains("CapInh:"));
            assertFalse(prmText.contains("CapBnd:"));
            assertFalse(prmText.contains("CapAmb:"));

            // uppercase normalize
            TraceEnvironmentConfig prmUpper = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42},"
                    + "\"linux\":{\"proc\":{\"capPermittedHex\":\"ABCDEF0123456789\"}}}");
            assertTrue(new String(ConfiguredProcFiles.renderStatus(emulator, prmUpper),
                    StandardCharsets.UTF_8).contains("CapPrm:\tabcdef0123456789\n"));

            // CapPrm before CapEff before Seccomp/NoNewPrivs
            TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{"
                    + "\"capPermittedHex\":\"1111111111111111\","
                    + "\"capEffectiveHex\":\"2222222222222222\","
                    + "\"seccompMode\":1,"
                    + "\"noNewPrivs\":false}}"
                    + "}");
            String bothText = new String(ConfiguredProcFiles.renderStatus(emulator, both),
                    StandardCharsets.UTF_8);
            assertTrue(bothText.startsWith(historicalText));
            assertTrue(bothText.endsWith(
                    "CapPrm:\t1111111111111111\n"
                            + "CapEff:\t2222222222222222\n"
                            + "Seccomp:\t1\n"
                            + "NoNewPrivs:\t0\n"));
            int prmIdx = bothText.indexOf("CapPrm:\t");
            int effIdx = bothText.indexOf("CapEff:\t");
            int secIdx = bothText.indexOf("Seccomp:\t");
            assertTrue(prmIdx >= 0 && effIdx > prmIdx && secIdx > effIdx);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testStatusCapBoundingHexArm32() throws Exception {
        runStatusCapBoundingHex(false);
    }

    @Test
    public void testStatusCapBoundingHexArm64() throws Exception {
        runStatusCapBoundingHex(true);
    }

    private static void runStatusCapBoundingHex(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig base = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(base)
                    .build();
            byte[] historical = ConfiguredProcFiles.renderStatus(emulator, base);
            String historicalText = new String(historical, StandardCharsets.UTF_8);
            assertFalse(historicalText.contains("CapBnd:"));
            assertFalse(historicalText.contains("CapPrm:"));
            assertFalse(historicalText.contains("CapEff:"));
            assertTrue(historicalText.endsWith("VmRSS:\t0 kB\n"));

            // CapBnd only — independent
            TraceEnvironmentConfig bndOnly = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{\"capBoundingHex\":\"0000003fffffffff\"}}"
                    + "}");
            String bndText = new String(ConfiguredProcFiles.renderStatus(emulator, bndOnly),
                    StandardCharsets.UTF_8);
            assertTrue(bndText.startsWith(historicalText));
            assertTrue(bndText.endsWith("CapBnd:\t0000003fffffffff\n"));
            assertFalse(bndText.contains("CapPrm:"));
            assertFalse(bndText.contains("CapEff:"));
            assertFalse(bndText.contains("CapInh:"));
            assertFalse(bndText.contains("CapAmb:"));

            TraceEnvironmentConfig bndUpper = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42},"
                    + "\"linux\":{\"proc\":{\"capBoundingHex\":\"ABCDEF0123456789\"}}}");
            assertTrue(new String(ConfiguredProcFiles.renderStatus(emulator, bndUpper),
                    StandardCharsets.UTF_8).contains("CapBnd:\tabcdef0123456789\n"));

            // CapPrm → CapEff → CapBnd → Seccomp → NoNewPrivs
            TraceEnvironmentConfig all = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{"
                    + "\"capPermittedHex\":\"1111111111111111\","
                    + "\"capEffectiveHex\":\"2222222222222222\","
                    + "\"capBoundingHex\":\"3333333333333333\","
                    + "\"seccompMode\":1,"
                    + "\"noNewPrivs\":false}}"
                    + "}");
            String allText = new String(ConfiguredProcFiles.renderStatus(emulator, all),
                    StandardCharsets.UTF_8);
            assertTrue(allText.startsWith(historicalText));
            assertTrue(allText.endsWith(
                    "CapPrm:\t1111111111111111\n"
                            + "CapEff:\t2222222222222222\n"
                            + "CapBnd:\t3333333333333333\n"
                            + "Seccomp:\t1\n"
                            + "NoNewPrivs:\t0\n"));
            int prmIdx = allText.indexOf("CapPrm:\t");
            int effIdx = allText.indexOf("CapEff:\t");
            int bndIdx = allText.indexOf("CapBnd:\t");
            int secIdx = allText.indexOf("Seccomp:\t");
            assertTrue(prmIdx >= 0 && effIdx > prmIdx && bndIdx > effIdx && secIdx > bndIdx);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testStatusCapInheritableHexArm32() throws Exception {
        runStatusCapInheritableHex(false);
    }

    @Test
    public void testStatusCapInheritableHexArm64() throws Exception {
        runStatusCapInheritableHex(true);
    }

    private static void runStatusCapInheritableHex(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig base = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(base)
                    .build();
            byte[] historical = ConfiguredProcFiles.renderStatus(emulator, base);
            String historicalText = new String(historical, StandardCharsets.UTF_8);
            assertFalse(historicalText.contains("CapInh:"));
            assertFalse(historicalText.contains("CapPrm:"));
            assertTrue(historicalText.endsWith("VmRSS:\t0 kB\n"));

            // CapInh only — independent
            TraceEnvironmentConfig inhOnly = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{\"capInheritableHex\":\"0000000000000000\"}}"
                    + "}");
            String inhText = new String(ConfiguredProcFiles.renderStatus(emulator, inhOnly),
                    StandardCharsets.UTF_8);
            assertTrue(inhText.startsWith(historicalText));
            assertTrue(inhText.endsWith("CapInh:\t0000000000000000\n"));
            assertFalse(inhText.contains("CapPrm:"));
            assertFalse(inhText.contains("CapEff:"));
            assertFalse(inhText.contains("CapBnd:"));
            assertFalse(inhText.contains("CapAmb:"));

            TraceEnvironmentConfig inhUpper = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42},"
                    + "\"linux\":{\"proc\":{\"capInheritableHex\":\"ABCDEF0123456789\"}}}");
            assertTrue(new String(ConfiguredProcFiles.renderStatus(emulator, inhUpper),
                    StandardCharsets.UTF_8).contains("CapInh:\tabcdef0123456789\n"));

            // CapInh → CapPrm → CapEff → CapBnd → Seccomp → NoNewPrivs
            TraceEnvironmentConfig all = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{"
                    + "\"capInheritableHex\":\"0000000000000000\","
                    + "\"capPermittedHex\":\"1111111111111111\","
                    + "\"capEffectiveHex\":\"2222222222222222\","
                    + "\"capBoundingHex\":\"3333333333333333\","
                    + "\"seccompMode\":1,"
                    + "\"noNewPrivs\":false}}"
                    + "}");
            String allText = new String(ConfiguredProcFiles.renderStatus(emulator, all),
                    StandardCharsets.UTF_8);
            assertTrue(allText.startsWith(historicalText));
            assertTrue(allText.endsWith(
                    "CapInh:\t0000000000000000\n"
                            + "CapPrm:\t1111111111111111\n"
                            + "CapEff:\t2222222222222222\n"
                            + "CapBnd:\t3333333333333333\n"
                            + "Seccomp:\t1\n"
                            + "NoNewPrivs:\t0\n"));
            int inhIdx = allText.indexOf("CapInh:\t");
            int prmIdx = allText.indexOf("CapPrm:\t");
            int effIdx = allText.indexOf("CapEff:\t");
            int bndIdx = allText.indexOf("CapBnd:\t");
            int secIdx = allText.indexOf("Seccomp:\t");
            assertTrue(inhIdx >= 0 && prmIdx > inhIdx && effIdx > prmIdx
                    && bndIdx > effIdx && secIdx > bndIdx);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testStatusCapAmbientHexArm32() throws Exception {
        runStatusCapAmbientHex(false);
    }

    @Test
    public void testStatusCapAmbientHexArm64() throws Exception {
        runStatusCapAmbientHex(true);
    }

    private static void runStatusCapAmbientHex(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig base = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(base)
                    .build();
            byte[] historical = ConfiguredProcFiles.renderStatus(emulator, base);
            String historicalText = new String(historical, StandardCharsets.UTF_8);
            assertFalse(historicalText.contains("CapAmb:"));
            assertFalse(historicalText.contains("CapBnd:"));
            assertTrue(historicalText.endsWith("VmRSS:\t0 kB\n"));

            // CapAmb only — independent
            TraceEnvironmentConfig ambOnly = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{\"capAmbientHex\":\"0000000000000000\"}}"
                    + "}");
            String ambText = new String(ConfiguredProcFiles.renderStatus(emulator, ambOnly),
                    StandardCharsets.UTF_8);
            assertTrue(ambText.startsWith(historicalText));
            assertTrue(ambText.endsWith("CapAmb:\t0000000000000000\n"));
            assertFalse(ambText.contains("CapInh:"));
            assertFalse(ambText.contains("CapPrm:"));
            assertFalse(ambText.contains("CapEff:"));
            assertFalse(ambText.contains("CapBnd:"));

            TraceEnvironmentConfig ambUpper = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42},"
                    + "\"linux\":{\"proc\":{\"capAmbientHex\":\"ABCDEF0123456789\"}}}");
            assertTrue(new String(ConfiguredProcFiles.renderStatus(emulator, ambUpper),
                    StandardCharsets.UTF_8).contains("CapAmb:\tabcdef0123456789\n"));

            // full five Cap* order then Seccomp/NoNewPrivs
            TraceEnvironmentConfig all = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{"
                    + "\"capInheritableHex\":\"0000000000000000\","
                    + "\"capPermittedHex\":\"1111111111111111\","
                    + "\"capEffectiveHex\":\"2222222222222222\","
                    + "\"capBoundingHex\":\"3333333333333333\","
                    + "\"capAmbientHex\":\"4444444444444444\","
                    + "\"seccompMode\":1,"
                    + "\"noNewPrivs\":false}}"
                    + "}");
            String allText = new String(ConfiguredProcFiles.renderStatus(emulator, all),
                    StandardCharsets.UTF_8);
            assertTrue(allText.startsWith(historicalText));
            assertTrue(allText.endsWith(
                    "CapInh:\t0000000000000000\n"
                            + "CapPrm:\t1111111111111111\n"
                            + "CapEff:\t2222222222222222\n"
                            + "CapBnd:\t3333333333333333\n"
                            + "CapAmb:\t4444444444444444\n"
                            + "Seccomp:\t1\n"
                            + "NoNewPrivs:\t0\n"));
            int inhIdx = allText.indexOf("CapInh:\t");
            int prmIdx = allText.indexOf("CapPrm:\t");
            int effIdx = allText.indexOf("CapEff:\t");
            int bndIdx = allText.indexOf("CapBnd:\t");
            int ambIdx = allText.indexOf("CapAmb:\t");
            int secIdx = allText.indexOf("Seccomp:\t");
            assertTrue(inhIdx >= 0 && prmIdx > inhIdx && effIdx > prmIdx
                    && bndIdx > effIdx && ambIdx > bndIdx && secIdx > ambIdx);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testStatusSeccompModeAndNoNewPrivsLines() throws Exception {
        TraceEnvironmentConfig base = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        AndroidEmulator emulator = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(base).build();
            byte[] historical = ConfiguredProcFiles.renderStatus(emulator, base);
            String historicalText = new String(historical, StandardCharsets.UTF_8);
            assertFalse(historicalText.contains("Seccomp:"));
            assertFalse(historicalText.contains("NoNewPrivs:"));
            assertTrue(historicalText.endsWith("VmRSS:\t0 kB\n"));

            // seccompMode only
            TraceEnvironmentConfig secOnly = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{\"seccompMode\":2}}"
                    + "}");
            String secText = new String(ConfiguredProcFiles.renderStatus(emulator, secOnly),
                    StandardCharsets.UTF_8);
            assertTrue(secText.contains("Seccomp:\t2\n"));
            assertFalse(secText.contains("NoNewPrivs:"));
            assertTrue(secText.startsWith(historicalText));
            assertTrue(secText.endsWith("Seccomp:\t2\n"));
            // modes 0 and 1
            TraceEnvironmentConfig sec0 = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42},"
                    + "\"linux\":{\"proc\":{\"seccompMode\":0}}}");
            assertTrue(new String(ConfiguredProcFiles.renderStatus(emulator, sec0),
                    StandardCharsets.UTF_8).contains("Seccomp:\t0\n"));
            TraceEnvironmentConfig sec1 = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42},"
                    + "\"linux\":{\"proc\":{\"seccompMode\":1}}}");
            assertTrue(new String(ConfiguredProcFiles.renderStatus(emulator, sec1),
                    StandardCharsets.UTF_8).contains("Seccomp:\t1\n"));

            // noNewPrivs only
            TraceEnvironmentConfig nnpTrue = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{\"noNewPrivs\":true}}"
                    + "}");
            String nnpText = new String(ConfiguredProcFiles.renderStatus(emulator, nnpTrue),
                    StandardCharsets.UTF_8);
            assertTrue(nnpText.contains("NoNewPrivs:\t1\n"));
            assertFalse(nnpText.contains("Seccomp:"));
            assertTrue(nnpText.startsWith(historicalText));
            TraceEnvironmentConfig nnpFalse = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42},"
                    + "\"linux\":{\"proc\":{\"noNewPrivs\":false}}}");
            assertTrue(new String(ConfiguredProcFiles.renderStatus(emulator, nnpFalse),
                    StandardCharsets.UTF_8).contains("NoNewPrivs:\t0\n"));

            // both: Seccomp then NoNewPrivs after historical body
            TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"demo\",\"pid\":42,\"ppid\":1,"
                    + "\"uid\":100,\"euid\":101,\"gid\":200,\"egid\":201},"
                    + "\"linux\":{\"proc\":{\"seccompMode\":1,\"noNewPrivs\":false}}"
                    + "}");
            String bothText = new String(ConfiguredProcFiles.renderStatus(emulator, both),
                    StandardCharsets.UTF_8);
            assertTrue(bothText.startsWith(historicalText));
            assertTrue(bothText.endsWith("Seccomp:\t1\nNoNewPrivs:\t0\n"));
            int secIdx = bothText.indexOf("Seccomp:\t1\n");
            int nnpIdx = bothText.indexOf("NoNewPrivs:\t0\n");
            assertTrue(secIdx >= 0 && nnpIdx > secIdx);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testFullRenderStatusCmdlineCgroup() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_JSON);
        AndroidEmulator emulator = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();

            String status = new String(ConfiguredProcFiles.renderStatus(emulator, config),
                    StandardCharsets.UTF_8);
            assertTrue(status.contains("Name:\tcom.demo.app\n"));
            assertTrue(status.contains("State:\tR (running)\n"));
            assertTrue(status.contains("Threads:\t8\n"));
            // 4194304/1024=4096; 100*4096/1024=400
            assertTrue(status.contains("VmSize:\t4096 kB\n"));
            assertTrue(status.contains("VmRSS:\t400 kB\n"));

            byte[] cmd = ConfiguredProcFiles.renderCmdline(emulator, config);
            // com.demo.app\0--flag\0\0
            byte[] expected = concat(
                    "com.demo.app".getBytes(StandardCharsets.UTF_8), new byte[]{0},
                    "--flag".getBytes(StandardCharsets.UTF_8), new byte[]{0},
                    new byte[]{0}
            );
            assertArrayEquals(expected, cmd);

            assertEquals("0::/\n1:name=systemd:/\n",
                    new String(ConfiguredProcFiles.renderCgroup(emulator, config),
                            StandardCharsets.UTF_8));

            // explicit empty arrays
            TraceEnvironmentConfig emptyArr = TraceEnvironmentConfig.parse("{"
                    + "\"process\":{\"processName\":\"x\",\"pid\":1},"
                    + "\"linux\":{\"proc\":{\"cmdline\":[],\"cgroups\":[]}}"
                    + "}");
            assertEquals(0, ConfiguredProcFiles.renderCmdline(emulator, emptyArr).length);
            assertEquals(0, ConfiguredProcFiles.renderCgroup(emulator, emptyArr).length);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testStatExactly52FieldsIncludingSpacedName() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{"
                + "\"processName\":\"my app name\","
                + "\"pid\":77,"
                + "\"ppid\":9,"
                + "\"uid\":1,\"euid\":1,\"gid\":1,\"egid\":1"
                + "},"
                + "\"linux\":{\"proc\":{"
                + "\"state\":\"S\","
                + "\"threadCount\":3,"
                + "\"startTimeTicks\":55,"
                + "\"virtualMemoryBytes\":8000,"
                + "\"residentSetPages\":10"
                + "}}}"
        );
        AndroidEmulator emulator = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            byte[] raw = ConfiguredProcFiles.renderStat(emulator, config);
            String text = new String(raw, StandardCharsets.UTF_8);
            assertTrue(text.endsWith("\n"));

            String[] fields = parseStatFields(raw);
            assertEquals(52, fields.length);
            assertEquals("77", fields[0]);
            assertEquals("(my app name)", fields[1]);
            assertEquals("S", fields[2]);
            assertEquals("9", fields[3]);
            assertEquals("77", fields[4]);
            assertEquals("77", fields[5]);
            assertEquals("0", fields[6]);
            assertEquals("0", fields[7]);
            assertEquals("4194304", fields[8]);
            for (int i = 9; i <= 16; i++) {
                assertEquals("0", fields[i]);
            }
            assertEquals("20", fields[17]);
            assertEquals("0", fields[18]);
            assertEquals("3", fields[19]);
            assertEquals("0", fields[20]);
            assertEquals("55", fields[21]);
            assertEquals("8000", fields[22]);
            assertEquals("10", fields[23]);
            assertEquals("18446744073709551615", fields[24]);
            for (int i = 25; i <= 36; i++) {
                assertEquals("0", fields[i]);
            }
            assertEquals("17", fields[37]);
            assertEquals("0", fields[38]);
            for (int i = 39; i <= 51; i++) {
                assertEquals("0", fields[i]);
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testVmRssNoOverflowAndUidGidColumns() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"p\",\"pid\":1,\"ppid\":0,"
                + "\"uid\":10,\"euid\":20,\"gid\":30,\"egid\":40},"
                + "\"linux\":{\"proc\":{"
                + "\"residentSetPages\":9223372036854775807,"
                + "\"virtualMemoryBytes\":1024"
                + "}}}"
        );
        AndroidEmulator emulator = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            String status = new String(ConfiguredProcFiles.renderStatus(emulator, config),
                    StandardCharsets.UTF_8);
            // Long.MAX_VALUE * 4 as exact decimal kB
            assertTrue(status.contains("VmRSS:\t36893488147419103228 kB\n"));
            assertTrue(status.contains("VmSize:\t1 kB\n"));
            assertTrue(status.contains("Uid:\t10\t20\t20\t20\n"));
            assertTrue(status.contains("Gid:\t30\t40\t40\t40\n"));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }

        // ordinary 100 pages → 400 kB
        TraceEnvironmentConfig ordinary = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"p\",\"pid\":1},"
                + "\"linux\":{\"proc\":{\"residentSetPages\":100}}"
                + "}");
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(ordinary).build();
            String status = new String(ConfiguredProcFiles.renderStatus(emulator, ordinary),
                    StandardCharsets.UTF_8);
            assertTrue(status.contains("VmRSS:\t400 kB\n"));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testNameSanitizeAndIndependentBuffers() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"a\\nb\\rc\\u0000d\",\"pid\":5},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        AndroidEmulator emulator = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            String status = new String(ConfiguredProcFiles.renderStatus(emulator, config),
                    StandardCharsets.UTF_8);
            assertTrue(status.contains("Name:\ta?b?c?d\n"));
            // config itself unchanged
            assertEquals("a\nb\rc\u0000d", config.getProcessName(null));

            byte[] a = ConfiguredProcFiles.renderStatus(emulator, config);
            byte[] b = ConfiguredProcFiles.renderStatus(emulator, config);
            assertFalse(a == b);
            assertArrayEquals(a, b);
            a[0] = 'Z';
            assertFalse(Arrays.equals(a, b));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testRenderStatmPagesAndStability() throws Exception {
        // 4194304 bytes / 4096 = 1024 pages; resident=100; rest fixed 0
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse(FULL_JSON);
        AndroidEmulator emulator = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(full).build();
            assertEquals("1024 100 0 0 0 0 0\n",
                    new String(ConfiguredProcFiles.renderStatm(emulator, full),
                            StandardCharsets.UTF_8));

            byte[] a = ConfiguredProcFiles.renderStatm(emulator, full);
            byte[] b = ConfiguredProcFiles.renderStatm(emulator, full);
            assertFalse(a == b);
            assertArrayEquals(a, b);
            a[0] = 'Z';
            assertFalse(Arrays.equals(a, b));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }

        // 4097 bytes → ceil to 2 pages
        TraceEnvironmentConfig ceil = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"p\",\"pid\":1},"
                + "\"linux\":{\"proc\":{"
                + "\"virtualMemoryBytes\":4097,"
                + "\"residentSetPages\":0"
                + "}}}"
        );
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(ceil).build();
            assertEquals("2 0 0 0 0 0 0\n",
                    new String(ConfiguredProcFiles.renderStatm(emulator, ceil),
                            StandardCharsets.UTF_8));
            // unit helper: exact page boundary stays exact
            assertEquals(1L, ConfiguredProcFiles.pagesFromVirtualMemoryBytes(4096L));
            assertEquals(2L, ConfiguredProcFiles.pagesFromVirtualMemoryBytes(4097L));
            assertEquals(0L, ConfiguredProcFiles.pagesFromVirtualMemoryBytes(0L));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }

        // Long.MAX_VALUE: div+mod must not overflow (no bytes+page-1)
        TraceEnvironmentConfig maxVm = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"p\",\"pid\":1},"
                + "\"linux\":{\"proc\":{"
                + "\"virtualMemoryBytes\":9223372036854775807,"
                + "\"residentSetPages\":7"
                + "}}}"
        );
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(maxVm).build();
            long expectedPages = ConfiguredProcFiles.pagesFromVirtualMemoryBytes(Long.MAX_VALUE);
            // Long.MAX_VALUE % 4096 == 4095 → quot+1
            assertEquals((Long.MAX_VALUE / 4096L) + 1L, expectedPages);
            assertEquals(expectedPages + " 7 0 0 0 0 0\n",
                    new String(ConfiguredProcFiles.renderStatm(emulator, maxVm),
                            StandardCharsets.UTF_8));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testRenderIoFullPartialAndStability() throws Exception {
        // all seven counters
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"p\",\"pid\":1},"
                + "\"linux\":{\"proc\":{"
                + "\"rchar\":11,"
                + "\"wchar\":22,"
                + "\"syscr\":33,"
                + "\"syscw\":44,"
                + "\"readBytes\":55,"
                + "\"writeBytes\":66,"
                + "\"cancelledWriteBytes\":77"
                + "}}}"
        );
        AndroidEmulator emulator = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(full).build();
            String expected = "rchar: 11\n"
                    + "wchar: 22\n"
                    + "syscr: 33\n"
                    + "syscw: 44\n"
                    + "read_bytes: 55\n"
                    + "write_bytes: 66\n"
                    + "cancelled_write_bytes: 77\n";
            assertEquals(expected, new String(ConfiguredProcFiles.renderIo(emulator, full),
                    StandardCharsets.UTF_8));

            byte[] a = ConfiguredProcFiles.renderIo(emulator, full);
            byte[] b = ConfiguredProcFiles.renderIo(emulator, full);
            assertFalse(a == b);
            assertArrayEquals(a, b);
            a[0] = 'Z';
            assertFalse(Arrays.equals(a, b));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }

        // partial: only rchar + writeBytes; rest default 0
        TraceEnvironmentConfig partial = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"p\",\"pid\":1},"
                + "\"linux\":{\"proc\":{"
                + "\"rchar\":100,"
                + "\"writeBytes\":200"
                + "}}}"
        );
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(partial).build();
            assertEquals("rchar: 100\n"
                            + "wchar: 0\n"
                            + "syscr: 0\n"
                            + "syscw: 0\n"
                            + "read_bytes: 0\n"
                            + "write_bytes: 200\n"
                            + "cancelled_write_bytes: 0\n",
                    new String(ConfiguredProcFiles.renderIo(emulator, partial),
                            StandardCharsets.UTF_8));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }

        // Long.MAX_VALUE boundary on one field
        TraceEnvironmentConfig maxIo = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"p\",\"pid\":1},"
                + "\"linux\":{\"proc\":{"
                + "\"cancelledWriteBytes\":9223372036854775807"
                + "}}}"
        );
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(maxIo).build();
            assertEquals("rchar: 0\n"
                            + "wchar: 0\n"
                            + "syscr: 0\n"
                            + "syscw: 0\n"
                            + "read_bytes: 0\n"
                            + "write_bytes: 0\n"
                            + "cancelled_write_bytes: 9223372036854775807\n",
                    new String(ConfiguredProcFiles.renderIo(emulator, maxIo),
                            StandardCharsets.UTF_8));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testRenderBootId() throws Exception {
        // empty linux.proc → bootId not configured → null (no inference)
        TraceEnvironmentConfig emptyProc = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{}}}");
        assertNull(ConfiguredProcFiles.renderBootId(emptyProc));

        // random.uuid must not drive boot_id content
        TraceEnvironmentConfig randomOnly = TraceEnvironmentConfig.parse("{"
                + "\"random\":{\"uuid\":\"11111111-1111-1111-1111-111111111111\"},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        assertNull(ConfiguredProcFiles.renderBootId(randomOnly));

        // configured: UTF-8 "<uuid>\\n" = 37 bytes, canonical lowercase
        TraceEnvironmentConfig withBoot = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"bootId\":\"A1B2C3D4-E5F6-7890-ABCD-EF1234567890\""
                + "}}}"
        );
        byte[] raw = ConfiguredProcFiles.renderBootId(withBoot);
        assertNotNull(raw);
        assertEquals(37, raw.length);
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890\n",
                new String(raw, StandardCharsets.UTF_8));
        byte[] a = ConfiguredProcFiles.renderBootId(withBoot);
        byte[] b = ConfiguredProcFiles.renderBootId(withBoot);
        assertFalse(a == b);
        assertArrayEquals(a, b);
        a[0] = 'Z';
        assertFalse(Arrays.equals(a, b));
    }

    @Test
    public void testRenderRandomUuid() throws Exception {
        TraceEnvironmentConfig emptyProc = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{}}}");
        assertNull(ConfiguredProcFiles.renderRandomUuid(emptyProc));

        // random.uuid / bootId must not drive random_uuid content
        TraceEnvironmentConfig randomOnly = TraceEnvironmentConfig.parse("{"
                + "\"random\":{\"uuid\":\"11111111-1111-1111-1111-111111111111\"},"
                + "\"linux\":{\"proc\":{"
                + "\"bootId\":\"A1B2C3D4-E5F6-7890-ABCD-EF1234567890\""
                + "}}}"
        );
        assertNull(ConfiguredProcFiles.renderRandomUuid(randomOnly));
        assertNotNull(ConfiguredProcFiles.renderBootId(randomOnly));

        TraceEnvironmentConfig withRand = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"randomUuid\":\"B2C3D4E5-F6A7-8901-BCDE-F12345678901\""
                + "}}}"
        );
        byte[] raw = ConfiguredProcFiles.renderRandomUuid(withRand);
        assertNotNull(raw);
        assertEquals(37, raw.length);
        assertEquals("b2c3d4e5-f6a7-8901-bcde-f12345678901\n",
                new String(raw, StandardCharsets.UTF_8));
        // fixed marker: same content every call
        byte[] a = ConfiguredProcFiles.renderRandomUuid(withRand);
        byte[] b = ConfiguredProcFiles.renderRandomUuid(withRand);
        assertFalse(a == b);
        assertArrayEquals(a, b);
    }

    @Test
    public void testRenderOomScoreAdj() throws Exception {
        // missing key → null (no default/inference, including explicit 0 only when present)
        TraceEnvironmentConfig emptyProc = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{}}}");
        assertNull(ConfiguredProcFiles.renderOomScoreAdj(emptyProc));

        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScoreAdj\":0}}}");
        assertEquals("0\n", new String(ConfiguredProcFiles.renderOomScoreAdj(zero),
                StandardCharsets.UTF_8));

        TraceEnvironmentConfig neg = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScoreAdj\":-1000}}}");
        assertEquals("-1000\n", new String(ConfiguredProcFiles.renderOomScoreAdj(neg),
                StandardCharsets.UTF_8));

        TraceEnvironmentConfig pos = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScoreAdj\":1000}}}");
        byte[] raw = ConfiguredProcFiles.renderOomScoreAdj(pos);
        assertEquals("1000\n", new String(raw, StandardCharsets.UTF_8));
        byte[] a = ConfiguredProcFiles.renderOomScoreAdj(pos);
        byte[] b = ConfiguredProcFiles.renderOomScoreAdj(pos);
        assertFalse(a == b);
        assertArrayEquals(a, b);
    }

    @Test
    public void testRenderOomScore() throws Exception {
        TraceEnvironmentConfig emptyProc = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{}}}");
        assertNull(ConfiguredProcFiles.renderOomScore(emptyProc));

        // oomScoreAdj alone must not drive oom_score
        TraceEnvironmentConfig adjOnly = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScoreAdj\":100}}}");
        assertNull(ConfiguredProcFiles.renderOomScore(adjOnly));
        assertNotNull(ConfiguredProcFiles.renderOomScoreAdj(adjOnly));

        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScore\":0}}}");
        assertEquals("0\n", new String(ConfiguredProcFiles.renderOomScore(zero),
                StandardCharsets.UTF_8));

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScore\":2000}}}");
        assertEquals("2000\n", new String(ConfiguredProcFiles.renderOomScore(max),
                StandardCharsets.UTF_8));

        TraceEnvironmentConfig mid = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScore\":150}}}");
        byte[] raw = ConfiguredProcFiles.renderOomScore(mid);
        assertEquals("150\n", new String(raw, StandardCharsets.UTF_8));
        byte[] a = ConfiguredProcFiles.renderOomScore(mid);
        byte[] b = ConfiguredProcFiles.renderOomScore(mid);
        assertFalse(a == b);
        assertArrayEquals(a, b);
    }

    @Test
    public void testRenderSelinuxContext() throws Exception {
        TraceEnvironmentConfig emptyProc = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{}}}");
        assertNull(ConfiguredProcFiles.renderSelinuxContext(emptyProc));

        TraceEnvironmentConfig with = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"selinuxContext\":\"u:r:untrusted_app:s0:c512,c768\""
                + "}}}"
        );
        byte[] raw = ConfiguredProcFiles.renderSelinuxContext(with);
        assertNotNull(raw);
        assertEquals("u:r:untrusted_app:s0:c512,c768\n",
                new String(raw, StandardCharsets.UTF_8));
        byte[] a = ConfiguredProcFiles.renderSelinuxContext(with);
        byte[] b = ConfiguredProcFiles.renderSelinuxContext(with);
        assertFalse(a == b);
        assertArrayEquals(a, b);
    }

    @Test
    public void testRenderComm() throws Exception {
        TraceEnvironmentConfig emptyProc = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{}}}");
        assertNull(ConfiguredProcFiles.renderComm(emptyProc));

        // processName alone must not drive comm
        TraceEnvironmentConfig nameOnly = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\"},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        assertNull(ConfiguredProcFiles.renderComm(nameOnly));

        TraceEnvironmentConfig min = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"comm\":\"a\"}}}");
        assertEquals("a\n", new String(ConfiguredProcFiles.renderComm(min),
                StandardCharsets.UTF_8));

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"comm\":\"123456789012345\"}}}");
        byte[] raw = ConfiguredProcFiles.renderComm(max);
        assertEquals("123456789012345\n", new String(raw, StandardCharsets.UTF_8));
        assertEquals(16, raw.length); // 15 + LF
        byte[] a = ConfiguredProcFiles.renderComm(max);
        byte[] b = ConfiguredProcFiles.renderComm(max);
        assertFalse(a == b);
        assertArrayEquals(a, b);

        TraceEnvironmentConfig spaced = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"comm\":\"my app\"}}}");
        assertEquals("my app\n", new String(ConfiguredProcFiles.renderComm(spaced),
                StandardCharsets.UTF_8));
    }

    @Test
    public void testRenderWchan() throws Exception {
        TraceEnvironmentConfig emptyProc = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{}}}");
        assertNull(ConfiguredProcFiles.renderWchan(emptyProc));

        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"wchan\":\"0\"}}}");
        assertEquals("0\n", new String(ConfiguredProcFiles.renderWchan(zero),
                StandardCharsets.UTF_8));

        TraceEnvironmentConfig sym = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"wchan\":\"futex_wait_queue_me\"}}}");
        byte[] raw = ConfiguredProcFiles.renderWchan(sym);
        assertEquals("futex_wait_queue_me\n", new String(raw, StandardCharsets.UTF_8));
        byte[] a = ConfiguredProcFiles.renderWchan(sym);
        byte[] b = ConfiguredProcFiles.renderWchan(sym);
        assertFalse(a == b);
        assertArrayEquals(a, b);
    }

    @Test
    public void testRenderEnviron() throws Exception {
        // absent linux.environ → four built-in defaults, NUL-separated
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        byte[] defaults = ConfiguredProcFiles.renderEnviron(missing);
        assertNotNull(defaults);
        byte[] expectedDefaults = concat(
                "ANDROID_DATA=/data\0".getBytes(StandardCharsets.UTF_8),
                "ANDROID_ROOT=/system\0".getBytes(StandardCharsets.UTF_8),
                "PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin\0"
                        .getBytes(StandardCharsets.UTF_8),
                "NO_ADDR_COMPAT_LAYOUT_FIXUP=1\0".getBytes(StandardCharsets.UTF_8)
        );
        assertArrayEquals(expectedDefaults, defaults);
        assertArrayEquals(defaults, ConfiguredProcFiles.renderEnviron(null));

        // explicit empty → empty bytes
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"environ\":[]}}");
        byte[] emptyBytes = ConfiguredProcFiles.renderEnviron(empty);
        assertNotNull(emptyBytes);
        assertEquals(0, emptyBytes.length);

        // configured list
        TraceEnvironmentConfig with = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"environ\":[\"A=1\",\"B=x=y\",\"EMPTY=\"]}}"
        );
        byte[] raw = ConfiguredProcFiles.renderEnviron(with);
        assertArrayEquals(concat(
                "A=1\0".getBytes(StandardCharsets.UTF_8),
                "B=x=y\0".getBytes(StandardCharsets.UTF_8),
                "EMPTY=\0".getBytes(StandardCharsets.UTF_8)
        ), raw);
        byte[] a = ConfiguredProcFiles.renderEnviron(with);
        byte[] b = ConfiguredProcFiles.renderEnviron(with);
        assertFalse(a == b);
        assertArrayEquals(a, b);
    }

    @Test
    public void testRenderUnameKernelFiles() {
        // empty uname object: no auto files (do not invent syscall defaults)
        TraceEnvironmentConfig emptyUname = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"uname\":{}}}");
        assertNull(ConfiguredProcFiles.renderUnameHostname(emptyUname));
        assertNull(ConfiguredProcFiles.renderUnameOsrelease(emptyUname));
        assertNull(ConfiguredProcFiles.renderUnameVersion(emptyUname));
        assertNull(ConfiguredProcFiles.renderUnameDomainname(emptyUname));
        assertNull(ConfiguredProcFiles.renderUnameOstype(emptyUname));

        // partial: only present keys render
        TraceEnvironmentConfig partial = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"uname\":{\"nodename\":\"host1\",\"release\":\"5.10.0\"}}}"
        );
        assertEquals("host1\n", new String(ConfiguredProcFiles.renderUnameHostname(partial),
                StandardCharsets.UTF_8));
        assertEquals("5.10.0\n", new String(ConfiguredProcFiles.renderUnameOsrelease(partial),
                StandardCharsets.UTF_8));
        assertNull(ConfiguredProcFiles.renderUnameVersion(partial));
        assertNull(ConfiguredProcFiles.renderUnameDomainname(partial));
        assertNull(ConfiguredProcFiles.renderUnameOstype(partial));

        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"uname\":{"
                + "\"sysname\":\"Linux\","
                + "\"nodename\":\"android\","
                + "\"release\":\"5.4.210\","
                + "\"version\":\"#1 SMP\","
                + "\"domainname\":\"(none)\""
                + "}}}"
        );
        assertEquals("Linux\n", new String(ConfiguredProcFiles.renderUnameOstype(full),
                StandardCharsets.UTF_8));
        assertEquals("android\n", new String(ConfiguredProcFiles.renderUnameHostname(full),
                StandardCharsets.UTF_8));
        assertEquals("5.4.210\n", new String(ConfiguredProcFiles.renderUnameOsrelease(full),
                StandardCharsets.UTF_8));
        assertEquals("#1 SMP\n", new String(ConfiguredProcFiles.renderUnameVersion(full),
                StandardCharsets.UTF_8));
        assertEquals("(none)\n", new String(ConfiguredProcFiles.renderUnameDomainname(full),
                StandardCharsets.UTF_8));
        byte[] a = ConfiguredProcFiles.renderUnameOstype(full);
        byte[] b = ConfiguredProcFiles.renderUnameOstype(full);
        assertFalse(a == b);
        assertArrayEquals(a, b);
    }

    /**
     * Parse /proc/stat line: field0, (comm which may contain spaces), remaining fields.
     */
    private static String[] parseStatFields(byte[] raw) {
        String line = new String(raw, StandardCharsets.UTF_8).trim();
        int open = line.indexOf('(');
        int close = line.lastIndexOf(')');
        assertTrue(open > 0 && close > open);
        String pid = line.substring(0, open).trim();
        String comm = line.substring(open, close + 1);
        String rest = line.substring(close + 1).trim();
        String[] tail = rest.isEmpty() ? new String[0] : rest.split(" ", -1);
        String[] all = new String[2 + tail.length];
        all[0] = pid;
        all[1] = comm;
        System.arraycopy(tail, 0, all, 2, tail.length);
        return all;
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) {
            n += p.length;
        }
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }
}

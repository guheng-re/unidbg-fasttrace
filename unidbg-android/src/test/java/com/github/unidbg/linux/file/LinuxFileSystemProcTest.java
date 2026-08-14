package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * LinuxFileSystem wiring for {@code linux.proc} → /proc/self|pid status|cmdline|cgroup|stat.
 * freeAll before emulator.close.
 */
public class LinuxFileSystemProcTest {

    private static final int CONFIG_PID = 4242;

    private static final String FULL_PROC_JSON = "{"
            + "\"process\":{"
            + "\"processName\":\"com.demo.app\","
            + "\"pid\":" + CONFIG_PID + ","
            + "\"ppid\":1,"
            + "\"uid\":1000,\"euid\":1000,\"gid\":1000,\"egid\":1000"
            + "},"
            + "\"linux\":{\"proc\":{"
            + "\"state\":\"R\","
            + "\"tracerPid\":0,"
            + "\"threadCount\":4,"
            + "\"cmdline\":[\"com.demo.app\",\"--x\",\"\"],"
            + "\"cgroups\":[\"0::/\"],"
            + "\"startTimeTicks\":10,"
            + "\"virtualMemoryBytes\":4096,"
            + "\"residentSetPages\":2"
            + "}}}";

    @Test
    public void testSelfAndPidAliasesFourFormats() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_PROC_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            assertEquals(CONFIG_PID, emulator.getPid());

            String statusSelf = readOpenText(emulator, blocks, "/proc/self/status");
            String statusPid = readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/status");
            assertEquals(statusSelf, statusPid);
            assertTrue(statusSelf.contains("Name:\tcom.demo.app\n"));
            assertTrue(statusSelf.contains("State:\tR (running)\n"));
            assertTrue(statusSelf.contains("Pid:\t" + CONFIG_PID + "\n"));
            assertTrue(statusSelf.contains("Threads:\t4\n"));

            byte[] cmdSelf = readOpenBytes(emulator, blocks, "/proc/self/cmdline");
            byte[] cmdPid = readOpenBytes(emulator, blocks, "/proc/" + CONFIG_PID + "/cmdline");
            assertArrayEquals(cmdSelf, cmdPid);
            byte[] expectedCmd = concat(
                    "com.demo.app".getBytes(StandardCharsets.UTF_8), new byte[]{0},
                    "--x".getBytes(StandardCharsets.UTF_8), new byte[]{0},
                    new byte[]{0}
            );
            assertArrayEquals(expectedCmd, cmdSelf);

            assertEquals("0::/\n", readOpenText(emulator, blocks, "/proc/self/cgroup"));
            assertEquals("0::/\n",
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/cgroup"));

            String stat = readOpenText(emulator, blocks, "/proc/self/stat");
            assertTrue(stat.startsWith(CONFIG_PID + " (com.demo.app) R "));
            assertTrue(stat.endsWith("\n"));
            assertEquals(stat, readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/stat"));
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testExplicitEmptyCmdlineAndCgroups() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"cmdline\":[],\"cgroups\":[]}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            assertEquals(0, readOpenBytes(emulator, blocks, "/proc/self/cmdline").length);
            assertEquals(0, readOpenBytes(emulator, blocks, "/proc/self/cgroup").length);
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testNotConfiguredFallsThrough() throws Exception {
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open("/proc/self/status", IOConstants.O_RDONLY);
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 4096);
                assertFalse(text.contains("Name:\tcom.demo.app\n"));
            }
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testWrongPidDoesNotHit() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_PROC_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            int wrong = CONFIG_PID + 1;
            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open("/proc/" + wrong + "/status", IOConstants.O_RDONLY);
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 4096);
                assertFalse(text.contains("Name:\tcom.demo.app\n"));
            }
            // configured pid still works
            assertTrue(readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/status")
                    .contains("Name:\tcom.demo.app\n"));
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testStatusSeccompAndNoNewPrivsSelfPidAliasAndFilesOverride() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{"
                + "\"processName\":\"com.demo.app\","
                + "\"pid\":" + CONFIG_PID + ","
                + "\"ppid\":1,"
                + "\"uid\":1000,\"euid\":1000,\"gid\":1000,\"egid\":1000"
                + "},"
                + "\"linux\":{\"proc\":{"
                + "\"state\":\"S\","
                + "\"seccompMode\":2,"
                + "\"noNewPrivs\":true"
                + "}}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String statusSelf = readOpenText(emulator, blocks, "/proc/self/status");
            String statusPid = readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/status");
            assertEquals(statusSelf, statusPid);
            assertTrue(statusSelf.contains("Seccomp:\t2\n"));
            assertTrue(statusSelf.contains("NoNewPrivs:\t1\n"));
            assertTrue(statusSelf.indexOf("Seccomp:\t2\n")
                    < statusSelf.indexOf("NoNewPrivs:\t1\n"));

            // sidecar still format=status with full byte length (unchanged event shape)
            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            assertEquals("read(\"/proc/self/status\")", e0.api);
            assertTrue(String.valueOf(e0.value).startsWith(
                    "path=/proc/self/status,format=status,bytes="));
            assertTrue(String.valueOf(e0.value).contains(
                    "bytes=" + statusSelf.getBytes(StandardCharsets.UTF_8).length));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }

        // linux.files exact map still wins over structured status (including Seccomp lines)
        TraceEnvironmentConfig filesOverride = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/status\":\"CUSTOM_STATUS\\n\"},"
                + "\"proc\":{\"state\":\"S\",\"seccompMode\":1,\"noNewPrivs\":false}"
                + "}}"
        );
        AndroidEmulator emulator2 = null;
        List<MemoryBlock> blocks2 = new ArrayList<MemoryBlock>();
        try {
            emulator2 = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(filesOverride).build();
            assertEquals("CUSTOM_STATUS\n", readOpenText(emulator2, blocks2, "/proc/self/status"));
            assertEquals("CUSTOM_STATUS\n",
                    readOpenText(emulator2, blocks2, "/proc/" + CONFIG_PID + "/status"));
        } finally {
            freeAll(blocks2);
            if (emulator2 != null) {
                emulator2.close();
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
        final String CAP = "0000001fffffffff";
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{"
                + "\"processName\":\"com.demo.app\","
                + "\"pid\":" + CONFIG_PID + ","
                + "\"ppid\":1,"
                + "\"uid\":1000,\"euid\":1000,\"gid\":1000,\"egid\":1000"
                + "},"
                + "\"linux\":{\"proc\":{"
                + "\"state\":\"S\","
                + "\"capEffectiveHex\":\"" + CAP + "\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String statusSelf = readOpenText(emulator, blocks, "/proc/self/status");
            String statusPid = readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/status");
            // self / configured-pid alias
            assertEquals(statusSelf, statusPid);
            assertTrue(statusSelf.contains("CapEff:\t" + CAP + "\n"));
            assertFalse(statusSelf.contains("CapInh:"));
            assertFalse(statusSelf.contains("CapPrm:"));
            assertFalse(statusSelf.contains("CapBnd:"));
            assertFalse(statusSelf.contains("CapAmb:"));

            // sidecar: length summary only — no raw CapEff hex in value
            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            assertEquals("read(\"/proc/self/status\")", e0.api);
            String v0 = String.valueOf(e0.value);
            assertTrue(v0.startsWith("path=/proc/self/status,format=status,bytes="));
            assertTrue(v0.contains(
                    "bytes=" + statusSelf.getBytes(StandardCharsets.UTF_8).length));
            assertFalse("sidecar must not embed raw CapEff hex", v0.contains(CAP));
            assertFalse(v0.contains("CapEff"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }

        // absence: no CapEff line (byte-stable vs empty proc extras)
        TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"state\":\"S\"}}"
                + "}");
        AndroidEmulator emulatorAbs = null;
        List<MemoryBlock> blocksAbs = new ArrayList<MemoryBlock>();
        try {
            emulatorAbs = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(absent)
                    .build();
            String noCap = readOpenText(emulatorAbs, blocksAbs, "/proc/self/status");
            assertFalse(noCap.contains("CapEff:"));
            assertEquals(noCap, readOpenText(emulatorAbs, blocksAbs, "/proc/" + CONFIG_PID + "/status"));
        } finally {
            freeAll(blocksAbs);
            if (emulatorAbs != null) {
                emulatorAbs.close();
            }
        }

        // linux.files exact map wins over structured CapEff
        TraceEnvironmentConfig filesOverride = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/status\":\"CUSTOM_STATUS\\n\"},"
                + "\"proc\":{\"state\":\"S\",\"capEffectiveHex\":\"" + CAP + "\"}"
                + "}}"
        );
        AndroidEmulator emulator2 = null;
        List<MemoryBlock> blocks2 = new ArrayList<MemoryBlock>();
        CapturingSink sink2 = new CapturingSink();
        try {
            emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(filesOverride)
                    .build();
            TraceEnvironmentEventSink.register(emulator2, sink2);
            assertEquals("CUSTOM_STATUS\n", readOpenText(emulator2, blocks2, "/proc/self/status"));
            assertEquals("CUSTOM_STATUS\n",
                    readOpenText(emulator2, blocks2, "/proc/" + CONFIG_PID + "/status"));
            for (CapturedEvent e : sink2.events) {
                assertFalse("linux_proc".equals(e.kind));
            }
        } finally {
            if (emulator2 != null) {
                TraceEnvironmentEventSink.unregister(emulator2, sink2);
            }
            freeAll(blocks2);
            if (emulator2 != null) {
                emulator2.close();
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
        final String PRM = "0000001fffffffff";
        final String EFF = "0000000000000001";
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{"
                + "\"processName\":\"com.demo.app\","
                + "\"pid\":" + CONFIG_PID + ","
                + "\"ppid\":1,"
                + "\"uid\":1000,\"euid\":1000,\"gid\":1000,\"egid\":1000"
                + "},"
                + "\"linux\":{\"proc\":{"
                + "\"state\":\"S\","
                + "\"capPermittedHex\":\"" + PRM + "\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String statusSelf = readOpenText(emulator, blocks, "/proc/self/status");
            String statusPid = readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/status");
            assertEquals(statusSelf, statusPid);
            assertTrue(statusSelf.contains("CapPrm:\t" + PRM + "\n"));
            assertFalse(statusSelf.contains("CapEff:"));
            assertFalse(statusSelf.contains("CapInh:"));
            assertFalse(statusSelf.contains("CapBnd:"));
            assertFalse(statusSelf.contains("CapAmb:"));

            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            String v0 = String.valueOf(e0.value);
            assertTrue(v0.startsWith("path=/proc/self/status,format=status,bytes="));
            assertTrue(v0.contains(
                    "bytes=" + statusSelf.getBytes(StandardCharsets.UTF_8).length));
            assertFalse("sidecar must not embed raw CapPrm hex", v0.contains(PRM));
            assertFalse(v0.contains("CapPrm"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }

        // CapPrm + CapEff combination: CapPrm before CapEff; self/pid alias
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{"
                + "\"capPermittedHex\":\"" + PRM + "\","
                + "\"capEffectiveHex\":\"" + EFF + "\""
                + "}}}"
        );
        AndroidEmulator emulatorBoth = null;
        List<MemoryBlock> blocksBoth = new ArrayList<MemoryBlock>();
        CapturingSink sinkBoth = new CapturingSink();
        try {
            emulatorBoth = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(both)
                    .build();
            TraceEnvironmentEventSink.register(emulatorBoth, sinkBoth);
            String s = readOpenText(emulatorBoth, blocksBoth, "/proc/self/status");
            assertEquals(s, readOpenText(emulatorBoth, blocksBoth, "/proc/" + CONFIG_PID + "/status"));
            assertTrue(s.contains("CapPrm:\t" + PRM + "\n"));
            assertTrue(s.contains("CapEff:\t" + EFF + "\n"));
            assertTrue(s.indexOf("CapPrm:\t") < s.indexOf("CapEff:\t"));
            for (CapturedEvent e : sinkBoth.events) {
                String v = String.valueOf(e.value);
                assertFalse(v.contains(PRM));
                assertFalse(v.contains(EFF));
                assertFalse(v.contains("CapPrm"));
                assertFalse(v.contains("CapEff"));
            }
        } finally {
            if (emulatorBoth != null) {
                TraceEnvironmentEventSink.unregister(emulatorBoth, sinkBoth);
            }
            freeAll(blocksBoth);
            if (emulatorBoth != null) {
                emulatorBoth.close();
            }
        }

        // absence of both Cap*
        TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"state\":\"S\"}}"
                + "}");
        AndroidEmulator emulatorAbs = null;
        List<MemoryBlock> blocksAbs = new ArrayList<MemoryBlock>();
        try {
            emulatorAbs = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(absent)
                    .build();
            String noCap = readOpenText(emulatorAbs, blocksAbs, "/proc/self/status");
            assertFalse(noCap.contains("CapPrm:"));
            assertFalse(noCap.contains("CapEff:"));
            assertEquals(noCap, readOpenText(emulatorAbs, blocksAbs, "/proc/" + CONFIG_PID + "/status"));
        } finally {
            freeAll(blocksAbs);
            if (emulatorAbs != null) {
                emulatorAbs.close();
            }
        }

        // linux.files overrides CapPrm
        TraceEnvironmentConfig filesOverride = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/status\":\"CUSTOM_STATUS\\n\"},"
                + "\"proc\":{\"state\":\"S\",\"capPermittedHex\":\"" + PRM + "\"}"
                + "}}"
        );
        AndroidEmulator emulator2 = null;
        List<MemoryBlock> blocks2 = new ArrayList<MemoryBlock>();
        CapturingSink sink2 = new CapturingSink();
        try {
            emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(filesOverride)
                    .build();
            TraceEnvironmentEventSink.register(emulator2, sink2);
            assertEquals("CUSTOM_STATUS\n", readOpenText(emulator2, blocks2, "/proc/self/status"));
            assertEquals("CUSTOM_STATUS\n",
                    readOpenText(emulator2, blocks2, "/proc/" + CONFIG_PID + "/status"));
            for (CapturedEvent e : sink2.events) {
                assertFalse("linux_proc".equals(e.kind));
            }
        } finally {
            if (emulator2 != null) {
                TraceEnvironmentEventSink.unregister(emulator2, sink2);
            }
            freeAll(blocks2);
            if (emulator2 != null) {
                emulator2.close();
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
        final String BND = "0000003fffffffff";
        final String PRM = "0000001fffffffff";
        final String EFF = "0000000000000001";
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{"
                + "\"processName\":\"com.demo.app\","
                + "\"pid\":" + CONFIG_PID + ","
                + "\"ppid\":1,"
                + "\"uid\":1000,\"euid\":1000,\"gid\":1000,\"egid\":1000"
                + "},"
                + "\"linux\":{\"proc\":{"
                + "\"state\":\"S\","
                + "\"capBoundingHex\":\"" + BND + "\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String statusSelf = readOpenText(emulator, blocks, "/proc/self/status");
            String statusPid = readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/status");
            assertEquals(statusSelf, statusPid);
            assertTrue(statusSelf.contains("CapBnd:\t" + BND + "\n"));
            assertFalse(statusSelf.contains("CapPrm:"));
            assertFalse(statusSelf.contains("CapEff:"));
            assertFalse(statusSelf.contains("CapInh:"));
            assertFalse(statusSelf.contains("CapAmb:"));

            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            String v0 = String.valueOf(e0.value);
            assertTrue(v0.startsWith("path=/proc/self/status,format=status,bytes="));
            assertTrue(v0.contains(
                    "bytes=" + statusSelf.getBytes(StandardCharsets.UTF_8).length));
            assertFalse("sidecar must not embed raw CapBnd hex", v0.contains(BND));
            assertFalse(v0.contains("CapBnd"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }

        // CapPrm → CapEff → CapBnd order; self/pid alias
        TraceEnvironmentConfig allCaps = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{"
                + "\"capPermittedHex\":\"" + PRM + "\","
                + "\"capEffectiveHex\":\"" + EFF + "\","
                + "\"capBoundingHex\":\"" + BND + "\""
                + "}}}"
        );
        AndroidEmulator emulatorAll = null;
        List<MemoryBlock> blocksAll = new ArrayList<MemoryBlock>();
        CapturingSink sinkAll = new CapturingSink();
        try {
            emulatorAll = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(allCaps)
                    .build();
            TraceEnvironmentEventSink.register(emulatorAll, sinkAll);
            String s = readOpenText(emulatorAll, blocksAll, "/proc/self/status");
            assertEquals(s, readOpenText(emulatorAll, blocksAll, "/proc/" + CONFIG_PID + "/status"));
            assertTrue(s.contains("CapPrm:\t" + PRM + "\n"));
            assertTrue(s.contains("CapEff:\t" + EFF + "\n"));
            assertTrue(s.contains("CapBnd:\t" + BND + "\n"));
            assertTrue(s.indexOf("CapPrm:\t") < s.indexOf("CapEff:\t"));
            assertTrue(s.indexOf("CapEff:\t") < s.indexOf("CapBnd:\t"));
            for (CapturedEvent e : sinkAll.events) {
                String v = String.valueOf(e.value);
                assertFalse(v.contains(PRM));
                assertFalse(v.contains(EFF));
                assertFalse(v.contains(BND));
                assertFalse(v.contains("CapPrm"));
                assertFalse(v.contains("CapEff"));
                assertFalse(v.contains("CapBnd"));
            }
        } finally {
            if (emulatorAll != null) {
                TraceEnvironmentEventSink.unregister(emulatorAll, sinkAll);
            }
            freeAll(blocksAll);
            if (emulatorAll != null) {
                emulatorAll.close();
            }
        }

        // absence of CapBnd
        TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"state\":\"S\"}}"
                + "}");
        AndroidEmulator emulatorAbs = null;
        List<MemoryBlock> blocksAbs = new ArrayList<MemoryBlock>();
        try {
            emulatorAbs = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(absent)
                    .build();
            String noCap = readOpenText(emulatorAbs, blocksAbs, "/proc/self/status");
            assertFalse(noCap.contains("CapBnd:"));
            assertFalse(noCap.contains("CapPrm:"));
            assertFalse(noCap.contains("CapEff:"));
            assertEquals(noCap, readOpenText(emulatorAbs, blocksAbs, "/proc/" + CONFIG_PID + "/status"));
        } finally {
            freeAll(blocksAbs);
            if (emulatorAbs != null) {
                emulatorAbs.close();
            }
        }

        // linux.files overrides CapBnd
        TraceEnvironmentConfig filesOverride = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/status\":\"CUSTOM_STATUS\\n\"},"
                + "\"proc\":{\"state\":\"S\",\"capBoundingHex\":\"" + BND + "\"}"
                + "}}"
        );
        AndroidEmulator emulator2 = null;
        List<MemoryBlock> blocks2 = new ArrayList<MemoryBlock>();
        CapturingSink sink2 = new CapturingSink();
        try {
            emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(filesOverride)
                    .build();
            TraceEnvironmentEventSink.register(emulator2, sink2);
            assertEquals("CUSTOM_STATUS\n", readOpenText(emulator2, blocks2, "/proc/self/status"));
            assertEquals("CUSTOM_STATUS\n",
                    readOpenText(emulator2, blocks2, "/proc/" + CONFIG_PID + "/status"));
            for (CapturedEvent e : sink2.events) {
                assertFalse("linux_proc".equals(e.kind));
            }
        } finally {
            if (emulator2 != null) {
                TraceEnvironmentEventSink.unregister(emulator2, sink2);
            }
            freeAll(blocks2);
            if (emulator2 != null) {
                emulator2.close();
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
        final String INH = "0000000000000000";
        final String PRM = "0000001fffffffff";
        final String EFF = "0000000000000001";
        final String BND = "0000003fffffffff";
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{"
                + "\"processName\":\"com.demo.app\","
                + "\"pid\":" + CONFIG_PID + ","
                + "\"ppid\":1,"
                + "\"uid\":1000,\"euid\":1000,\"gid\":1000,\"egid\":1000"
                + "},"
                + "\"linux\":{\"proc\":{"
                + "\"state\":\"S\","
                + "\"capInheritableHex\":\"" + INH + "\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String statusSelf = readOpenText(emulator, blocks, "/proc/self/status");
            String statusPid = readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/status");
            assertEquals(statusSelf, statusPid);
            assertTrue(statusSelf.contains("CapInh:\t" + INH + "\n"));
            assertFalse(statusSelf.contains("CapPrm:"));
            assertFalse(statusSelf.contains("CapEff:"));
            assertFalse(statusSelf.contains("CapBnd:"));
            assertFalse(statusSelf.contains("CapAmb:"));

            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            String v0 = String.valueOf(e0.value);
            assertTrue(v0.startsWith("path=/proc/self/status,format=status,bytes="));
            assertTrue(v0.contains(
                    "bytes=" + statusSelf.getBytes(StandardCharsets.UTF_8).length));
            assertFalse("sidecar must not embed raw CapInh hex", v0.contains(INH));
            assertFalse(v0.contains("CapInh"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }

        // CapInh → CapPrm → CapEff → CapBnd order; self/pid alias
        TraceEnvironmentConfig allCaps = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{"
                + "\"capInheritableHex\":\"" + INH + "\","
                + "\"capPermittedHex\":\"" + PRM + "\","
                + "\"capEffectiveHex\":\"" + EFF + "\","
                + "\"capBoundingHex\":\"" + BND + "\""
                + "}}}"
        );
        AndroidEmulator emulatorAll = null;
        List<MemoryBlock> blocksAll = new ArrayList<MemoryBlock>();
        CapturingSink sinkAll = new CapturingSink();
        try {
            emulatorAll = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(allCaps)
                    .build();
            TraceEnvironmentEventSink.register(emulatorAll, sinkAll);
            String s = readOpenText(emulatorAll, blocksAll, "/proc/self/status");
            assertEquals(s, readOpenText(emulatorAll, blocksAll, "/proc/" + CONFIG_PID + "/status"));
            assertTrue(s.contains("CapInh:\t" + INH + "\n"));
            assertTrue(s.contains("CapPrm:\t" + PRM + "\n"));
            assertTrue(s.contains("CapEff:\t" + EFF + "\n"));
            assertTrue(s.contains("CapBnd:\t" + BND + "\n"));
            assertTrue(s.indexOf("CapInh:\t") < s.indexOf("CapPrm:\t"));
            assertTrue(s.indexOf("CapPrm:\t") < s.indexOf("CapEff:\t"));
            assertTrue(s.indexOf("CapEff:\t") < s.indexOf("CapBnd:\t"));
            for (CapturedEvent e : sinkAll.events) {
                String v = String.valueOf(e.value);
                assertFalse(v.contains(INH));
                assertFalse(v.contains(PRM));
                assertFalse(v.contains(EFF));
                assertFalse(v.contains(BND));
                assertFalse(v.contains("CapInh"));
                assertFalse(v.contains("CapPrm"));
                assertFalse(v.contains("CapEff"));
                assertFalse(v.contains("CapBnd"));
            }
        } finally {
            if (emulatorAll != null) {
                TraceEnvironmentEventSink.unregister(emulatorAll, sinkAll);
            }
            freeAll(blocksAll);
            if (emulatorAll != null) {
                emulatorAll.close();
            }
        }

        // absence of CapInh
        TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"state\":\"S\"}}"
                + "}");
        AndroidEmulator emulatorAbs = null;
        List<MemoryBlock> blocksAbs = new ArrayList<MemoryBlock>();
        try {
            emulatorAbs = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(absent)
                    .build();
            String noCap = readOpenText(emulatorAbs, blocksAbs, "/proc/self/status");
            assertFalse(noCap.contains("CapInh:"));
            assertFalse(noCap.contains("CapPrm:"));
            assertFalse(noCap.contains("CapEff:"));
            assertFalse(noCap.contains("CapBnd:"));
            assertEquals(noCap, readOpenText(emulatorAbs, blocksAbs, "/proc/" + CONFIG_PID + "/status"));
        } finally {
            freeAll(blocksAbs);
            if (emulatorAbs != null) {
                emulatorAbs.close();
            }
        }

        // linux.files overrides CapInh
        TraceEnvironmentConfig filesOverride = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/status\":\"CUSTOM_STATUS\\n\"},"
                + "\"proc\":{\"state\":\"S\",\"capInheritableHex\":\"" + INH + "\"}"
                + "}}"
        );
        AndroidEmulator emulator2 = null;
        List<MemoryBlock> blocks2 = new ArrayList<MemoryBlock>();
        CapturingSink sink2 = new CapturingSink();
        try {
            emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(filesOverride)
                    .build();
            TraceEnvironmentEventSink.register(emulator2, sink2);
            assertEquals("CUSTOM_STATUS\n", readOpenText(emulator2, blocks2, "/proc/self/status"));
            assertEquals("CUSTOM_STATUS\n",
                    readOpenText(emulator2, blocks2, "/proc/" + CONFIG_PID + "/status"));
            for (CapturedEvent e : sink2.events) {
                assertFalse("linux_proc".equals(e.kind));
            }
        } finally {
            if (emulator2 != null) {
                TraceEnvironmentEventSink.unregister(emulator2, sink2);
            }
            freeAll(blocks2);
            if (emulator2 != null) {
                emulator2.close();
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
        final String AMB = "000000000000000a";
        final String INH = "0000000000000000";
        final String PRM = "0000001fffffffff";
        final String EFF = "0000000000000001";
        final String BND = "0000003fffffffff";
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{"
                + "\"processName\":\"com.demo.app\","
                + "\"pid\":" + CONFIG_PID + ","
                + "\"ppid\":1,"
                + "\"uid\":1000,\"euid\":1000,\"gid\":1000,\"egid\":1000"
                + "},"
                + "\"linux\":{\"proc\":{"
                + "\"state\":\"S\","
                + "\"capAmbientHex\":\"" + AMB + "\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String statusSelf = readOpenText(emulator, blocks, "/proc/self/status");
            String statusPid = readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/status");
            assertEquals(statusSelf, statusPid);
            assertTrue(statusSelf.contains("CapAmb:\t" + AMB + "\n"));
            assertFalse(statusSelf.contains("CapInh:"));
            assertFalse(statusSelf.contains("CapPrm:"));
            assertFalse(statusSelf.contains("CapEff:"));
            assertFalse(statusSelf.contains("CapBnd:"));

            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            String v0 = String.valueOf(e0.value);
            assertTrue(v0.startsWith("path=/proc/self/status,format=status,bytes="));
            assertTrue(v0.contains(
                    "bytes=" + statusSelf.getBytes(StandardCharsets.UTF_8).length));
            assertFalse("sidecar must not embed raw CapAmb hex", v0.contains(AMB));
            assertFalse(v0.contains("CapAmb"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }

        // full five Cap* order; self/pid alias
        TraceEnvironmentConfig allCaps = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{"
                + "\"capInheritableHex\":\"" + INH + "\","
                + "\"capPermittedHex\":\"" + PRM + "\","
                + "\"capEffectiveHex\":\"" + EFF + "\","
                + "\"capBoundingHex\":\"" + BND + "\","
                + "\"capAmbientHex\":\"" + AMB + "\""
                + "}}}"
        );
        AndroidEmulator emulatorAll = null;
        List<MemoryBlock> blocksAll = new ArrayList<MemoryBlock>();
        CapturingSink sinkAll = new CapturingSink();
        try {
            emulatorAll = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(allCaps)
                    .build();
            TraceEnvironmentEventSink.register(emulatorAll, sinkAll);
            String s = readOpenText(emulatorAll, blocksAll, "/proc/self/status");
            assertEquals(s, readOpenText(emulatorAll, blocksAll, "/proc/" + CONFIG_PID + "/status"));
            assertTrue(s.contains("CapInh:\t" + INH + "\n"));
            assertTrue(s.contains("CapPrm:\t" + PRM + "\n"));
            assertTrue(s.contains("CapEff:\t" + EFF + "\n"));
            assertTrue(s.contains("CapBnd:\t" + BND + "\n"));
            assertTrue(s.contains("CapAmb:\t" + AMB + "\n"));
            assertTrue(s.indexOf("CapInh:\t") < s.indexOf("CapPrm:\t"));
            assertTrue(s.indexOf("CapPrm:\t") < s.indexOf("CapEff:\t"));
            assertTrue(s.indexOf("CapEff:\t") < s.indexOf("CapBnd:\t"));
            assertTrue(s.indexOf("CapBnd:\t") < s.indexOf("CapAmb:\t"));
            for (CapturedEvent e : sinkAll.events) {
                String v = String.valueOf(e.value);
                assertFalse(v.contains(INH));
                assertFalse(v.contains(PRM));
                assertFalse(v.contains(EFF));
                assertFalse(v.contains(BND));
                assertFalse(v.contains(AMB));
                assertFalse(v.contains("CapInh"));
                assertFalse(v.contains("CapPrm"));
                assertFalse(v.contains("CapEff"));
                assertFalse(v.contains("CapBnd"));
                assertFalse(v.contains("CapAmb"));
            }
        } finally {
            if (emulatorAll != null) {
                TraceEnvironmentEventSink.unregister(emulatorAll, sinkAll);
            }
            freeAll(blocksAll);
            if (emulatorAll != null) {
                emulatorAll.close();
            }
        }

        // absence of CapAmb
        TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"state\":\"S\"}}"
                + "}");
        AndroidEmulator emulatorAbs = null;
        List<MemoryBlock> blocksAbs = new ArrayList<MemoryBlock>();
        try {
            emulatorAbs = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(absent)
                    .build();
            String noCap = readOpenText(emulatorAbs, blocksAbs, "/proc/self/status");
            assertFalse(noCap.contains("CapAmb:"));
            assertFalse(noCap.contains("CapInh:"));
            assertEquals(noCap, readOpenText(emulatorAbs, blocksAbs, "/proc/" + CONFIG_PID + "/status"));
        } finally {
            freeAll(blocksAbs);
            if (emulatorAbs != null) {
                emulatorAbs.close();
            }
        }

        // linux.files overrides CapAmb
        TraceEnvironmentConfig filesOverride = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/status\":\"CUSTOM_STATUS\\n\"},"
                + "\"proc\":{\"state\":\"S\",\"capAmbientHex\":\"" + AMB + "\"}"
                + "}}"
        );
        AndroidEmulator emulator2 = null;
        List<MemoryBlock> blocks2 = new ArrayList<MemoryBlock>();
        CapturingSink sink2 = new CapturingSink();
        try {
            emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(filesOverride)
                    .build();
            TraceEnvironmentEventSink.register(emulator2, sink2);
            assertEquals("CUSTOM_STATUS\n", readOpenText(emulator2, blocks2, "/proc/self/status"));
            assertEquals("CUSTOM_STATUS\n",
                    readOpenText(emulator2, blocks2, "/proc/" + CONFIG_PID + "/status"));
            for (CapturedEvent e : sink2.events) {
                assertFalse("linux_proc".equals(e.kind));
            }
        } finally {
            if (emulator2 != null) {
                TraceEnvironmentEventSink.unregister(emulator2, sink2);
            }
            freeAll(blocks2);
            if (emulator2 != null) {
                emulator2.close();
            }
        }
    }

    @Test
    public void testLimitsArm32() throws Exception {
        runLimits(false);
    }

    @Test
    public void testLimitsArm64() throws Exception {
        runLimits(true);
    }

    private static void runLimits(boolean is64Bit) throws Exception {
        final String LINE0 =
                "Limit                     Soft Limit           Hard Limit           Units     ";
        final String LINE1 =
                "Max cpu time              unlimited            unlimited            seconds   ";
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{"
                + "\"processName\":\"com.demo.app\","
                + "\"pid\":" + CONFIG_PID + ","
                + "\"ppid\":1,"
                + "\"uid\":1000,\"euid\":1000,\"gid\":1000,\"egid\":1000"
                + "},"
                + "\"linux\":{\"proc\":{"
                + "\"state\":\"S\","
                + "\"limits\":[\"" + LINE0 + "\",\"" + LINE1 + "\"]"
                + "}}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String self = readOpenText(emulator, blocks, "/proc/self/limits");
            String pid = readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/limits");
            assertEquals(self, pid);
            assertEquals(LINE0 + "\n" + LINE1 + "\n", self);

            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            assertEquals("read(\"/proc/self/limits\")", e0.api);
            String v0 = String.valueOf(e0.value);
            assertTrue(v0.startsWith("path=/proc/self/limits,format=limits,bytes="));
            assertTrue(v0.contains(
                    "bytes=" + self.getBytes(StandardCharsets.UTF_8).length));
            assertFalse("sidecar must not embed raw limits lines", v0.contains("Max cpu time"));
            assertFalse(v0.contains(LINE0));
            assertFalse(v0.contains("unlimited"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }

        // explicit empty array → empty file
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"limits\":[]}}"
                + "}");
        AndroidEmulator emulatorEmpty = null;
        List<MemoryBlock> blocksEmpty = new ArrayList<MemoryBlock>();
        try {
            emulatorEmpty = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(empty)
                    .build();
            assertEquals("", readOpenText(emulatorEmpty, blocksEmpty, "/proc/self/limits"));
            assertEquals("", readOpenText(emulatorEmpty, blocksEmpty, "/proc/" + CONFIG_PID + "/limits"));
        } finally {
            freeAll(blocksEmpty);
            if (emulatorEmpty != null) {
                emulatorEmpty.close();
            }
        }

        // absence → not served by structured limits (no linux_proc limits event)
        TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"state\":\"S\"}}"
                + "}");
        AndroidEmulator emulatorAbs = null;
        List<MemoryBlock> blocksAbs = new ArrayList<MemoryBlock>();
        CapturingSink sinkAbs = new CapturingSink();
        try {
            emulatorAbs = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(absent)
                    .build();
            TraceEnvironmentEventSink.register(emulatorAbs, sinkAbs);
            // open may fall back to other paths; must not emit format=limits
            try {
                readOpenText(emulatorAbs, blocksAbs, "/proc/self/limits");
            } catch (Throwable ignored) {
                // open may fail if no legacy content — acceptable for absence
            }
            for (CapturedEvent e : sinkAbs.events) {
                assertFalse(String.valueOf(e.value).contains("format=limits"));
            }
        } finally {
            if (emulatorAbs != null) {
                TraceEnvironmentEventSink.unregister(emulatorAbs, sinkAbs);
            }
            freeAll(blocksAbs);
            if (emulatorAbs != null) {
                emulatorAbs.close();
            }
        }

        // linux.files wins
        TraceEnvironmentConfig filesOverride = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/limits\":\"CUSTOM_LIMITS\\n\"},"
                + "\"proc\":{\"limits\":[\"" + LINE0 + "\"]}"
                + "}}"
        );
        AndroidEmulator emulator2 = null;
        List<MemoryBlock> blocks2 = new ArrayList<MemoryBlock>();
        CapturingSink sink2 = new CapturingSink();
        try {
            emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(filesOverride)
                    .build();
            TraceEnvironmentEventSink.register(emulator2, sink2);
            assertEquals("CUSTOM_LIMITS\n",
                    readOpenText(emulator2, blocks2, "/proc/self/limits"));
            assertEquals("CUSTOM_LIMITS\n",
                    readOpenText(emulator2, blocks2, "/proc/" + CONFIG_PID + "/limits"));
            for (CapturedEvent e : sink2.events) {
                assertFalse("linux_proc".equals(e.kind));
            }
        } finally {
            if (emulator2 != null) {
                TraceEnvironmentEventSink.unregister(emulator2, sink2);
            }
            freeAll(blocks2);
            if (emulator2 != null) {
                emulator2.close();
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
        final String BLK = "0000000000000000";
        final String IGN = "0000000000001000";
        final String CGT = "0000000180000000";
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{"
                + "\"processName\":\"com.demo.app\","
                + "\"pid\":" + CONFIG_PID + ","
                + "\"ppid\":1,"
                + "\"uid\":1000,\"euid\":1000,\"gid\":1000,\"egid\":1000"
                + "},"
                + "\"linux\":{\"proc\":{"
                + "\"state\":\"S\","
                + "\"signalBlockedHex\":\"" + BLK + "\","
                + "\"signalIgnoredHex\":\"" + IGN + "\","
                + "\"signalCaughtHex\":\"" + CGT + "\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String statusSelf = readOpenText(emulator, blocks, "/proc/self/status");
            String statusPid = readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/status");
            assertEquals(statusSelf, statusPid);
            assertTrue(statusSelf.contains("SigBlk:\t" + BLK + "\n"));
            assertTrue(statusSelf.contains("SigIgn:\t" + IGN + "\n"));
            assertTrue(statusSelf.contains("SigCgt:\t" + CGT + "\n"));
            assertTrue(statusSelf.indexOf("SigBlk:\t") < statusSelf.indexOf("SigIgn:\t"));
            assertTrue(statusSelf.indexOf("SigIgn:\t") < statusSelf.indexOf("SigCgt:\t"));

            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            String v0 = String.valueOf(e0.value);
            assertTrue(v0.startsWith("path=/proc/self/status,format=status,bytes="));
            assertTrue(v0.contains(
                    "bytes=" + statusSelf.getBytes(StandardCharsets.UTF_8).length));
            assertFalse("sidecar must not embed raw SigBlk", v0.contains(BLK));
            assertFalse(v0.contains(IGN));
            assertFalse(v0.contains(CGT));
            assertFalse(v0.contains("SigBlk"));
            assertFalse(v0.contains("SigIgn"));
            assertFalse(v0.contains("SigCgt"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }

        // absence
        TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"state\":\"S\"}}"
                + "}");
        AndroidEmulator emulatorAbs = null;
        List<MemoryBlock> blocksAbs = new ArrayList<MemoryBlock>();
        try {
            emulatorAbs = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(absent)
                    .build();
            String noSig = readOpenText(emulatorAbs, blocksAbs, "/proc/self/status");
            assertFalse(noSig.contains("SigBlk:"));
            assertFalse(noSig.contains("SigIgn:"));
            assertFalse(noSig.contains("SigCgt:"));
            assertEquals(noSig, readOpenText(emulatorAbs, blocksAbs, "/proc/" + CONFIG_PID + "/status"));
        } finally {
            freeAll(blocksAbs);
            if (emulatorAbs != null) {
                emulatorAbs.close();
            }
        }

        // linux.files override
        TraceEnvironmentConfig filesOverride = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/status\":\"CUSTOM_STATUS\\n\"},"
                + "\"proc\":{\"state\":\"S\",\"signalBlockedHex\":\"" + BLK + "\","
                + "\"signalIgnoredHex\":\"" + IGN + "\",\"signalCaughtHex\":\"" + CGT + "\"}"
                + "}}"
        );
        AndroidEmulator emulator2 = null;
        List<MemoryBlock> blocks2 = new ArrayList<MemoryBlock>();
        CapturingSink sink2 = new CapturingSink();
        try {
            emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(filesOverride)
                    .build();
            TraceEnvironmentEventSink.register(emulator2, sink2);
            assertEquals("CUSTOM_STATUS\n", readOpenText(emulator2, blocks2, "/proc/self/status"));
            assertEquals("CUSTOM_STATUS\n",
                    readOpenText(emulator2, blocks2, "/proc/" + CONFIG_PID + "/status"));
            for (CapturedEvent e : sink2.events) {
                assertFalse("linux_proc".equals(e.kind));
            }
        } finally {
            if (emulator2 != null) {
                TraceEnvironmentEventSink.unregister(emulator2, sink2);
            }
            freeAll(blocks2);
            if (emulator2 != null) {
                emulator2.close();
            }
        }
    }

    @Test
    public void testLinuxFilesPriorityNoLinuxProcEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/status\":\"CUSTOM_STATUS\\n\"},"
                + "\"proc\":{\"state\":\"S\"}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_STATUS\n", readOpenText(emulator, blocks, "/proc/self/status"));
            assertEquals("CUSTOM_STATUS\n",
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/status"));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testSingleSidecarEventPerSuccessfulOpen() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_PROC_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] status = readOpenBytes(emulator, blocks, "/proc/self/status");
            assertEquals(1, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            assertEquals("read(\"/proc/self/status\")", e0.api);
            assertEquals("json-config", e0.source);
            assertEquals("path=/proc/self/status,format=status,bytes=" + status.length,
                    String.valueOf(e0.value));
            assertTrue(e0.note.contains("读取配置的进程 proc 文件"));
            assertTrue(e0.note.contains("/proc/self/status"));

            byte[] cmd = readOpenBytes(emulator, blocks, "/proc/self/cmdline");
            assertEquals(2, sink.events.size());
            CapturedEvent e1 = sink.events.get(1);
            assertEquals("linux_proc", e1.kind);
            assertEquals("read(\"/proc/self/cmdline\")", e1.api);
            assertEquals("path=/proc/self/cmdline,format=cmdline,bytes=" + cmd.length,
                    String.valueOf(e1.value));

            readOpenBytes(emulator, blocks, "/proc/self/cgroup");
            assertEquals(3, sink.events.size());
            assertEquals("format=cgroup",
                    String.valueOf(sink.events.get(2).value).split(",")[1].replace("format=", "format="));
            assertTrue(String.valueOf(sink.events.get(2).value).contains("format=cgroup"));

            readOpenBytes(emulator, blocks, "/proc/" + CONFIG_PID + "/stat");
            assertEquals(4, sink.events.size());
            CapturedEvent e3 = sink.events.get(3);
            assertEquals("read(\"/proc/" + CONFIG_PID + "/stat\")", e3.api);
            assertTrue(String.valueOf(e3.value).contains("format=stat"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static final String BOOT_ID_PATH = "/proc/sys/kernel/random/boot_id";
    private static final String BOOT_ID_UUID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    @Test
    public void testBootIdConfiguredOpenAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"bootId\":\"A1B2C3D4-E5F6-7890-ABCD-EF1234567890\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] raw = readOpenBytes(emulator, blocks, BOOT_ID_PATH);
            assertEquals(37, raw.length);
            assertEquals(BOOT_ID_UUID + "\n", new String(raw, StandardCharsets.UTF_8));

            assertEquals(1, sink.events.size());
            CapturedEvent e = sink.events.get(0);
            assertEquals("linux_proc", e.kind);
            assertEquals("read(\"" + BOOT_ID_PATH + "\")", e.api);
            assertEquals("json-config", e.source);
            assertEquals("path=" + BOOT_ID_PATH + ",format=boot_id,bytes=37",
                    String.valueOf(e.value));
            assertTrue(e.note.contains("读取配置的内核 boot_id"));
            assertTrue(e.note.contains(BOOT_ID_PATH));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testBootIdMissingLeavesLegacyNoEvent() throws Exception {
        // linux.proc present but bootId key absent → no configured boot_id open / no event
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"state\":\"S\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open(BOOT_ID_PATH, IOConstants.O_RDONLY);
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 64);
                assertFalse(text.startsWith(BOOT_ID_UUID));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind)
                        && String.valueOf(e.api).contains("boot_id"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testBootIdLinuxFilesPriorityNoLinuxProcEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{"
                + "\"files\":{\"" + BOOT_ID_PATH + "\":\"CUSTOM_BOOT\\n\"},"
                + "\"proc\":{\"bootId\":\"A1B2C3D4-E5F6-7890-ABCD-EF1234567890\"}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_BOOT\n", readOpenText(emulator, blocks, BOOT_ID_PATH));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static final String RANDOM_UUID_PATH = "/proc/sys/kernel/random/uuid";
    private static final String RANDOM_UUID_VALUE = "b2c3d4e5-f6a7-8901-bcde-f12345678901";

    @Test
    public void testRandomUuidConfiguredOpenAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"randomUuid\":\"B2C3D4E5-F6A7-8901-BCDE-F12345678901\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] raw = readOpenBytes(emulator, blocks, RANDOM_UUID_PATH);
            assertEquals(37, raw.length);
            assertEquals(RANDOM_UUID_VALUE + "\n", new String(raw, StandardCharsets.UTF_8));

            // fixed marker: second open yields same content
            assertEquals(RANDOM_UUID_VALUE + "\n",
                    readOpenText(emulator, blocks, RANDOM_UUID_PATH));

            assertEquals(2, sink.events.size());
            CapturedEvent e = sink.events.get(0);
            assertEquals("linux_proc", e.kind);
            assertEquals("read(\"" + RANDOM_UUID_PATH + "\")", e.api);
            assertEquals("json-config", e.source);
            assertEquals("path=" + RANDOM_UUID_PATH + ",format=random_uuid,bytes=37",
                    String.valueOf(e.value));
            assertTrue(e.note.contains("读取配置的内核 random uuid"));
            assertTrue(e.note.contains(RANDOM_UUID_PATH));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testRandomUuidMissingLeavesLegacyNoEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"bootId\":\"A1B2C3D4-E5F6-7890-ABCD-EF1234567890\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open(RANDOM_UUID_PATH, IOConstants.O_RDONLY);
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 64);
                assertFalse(text.startsWith(RANDOM_UUID_VALUE));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind)
                        && String.valueOf(e.api).contains("random/uuid"));
            }
            // boot_id still works independently
            assertEquals(BOOT_ID_UUID + "\n", readOpenText(emulator, blocks, BOOT_ID_PATH));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testRandomUuidLinuxFilesPriorityNoLinuxProcEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{"
                + "\"files\":{\"" + RANDOM_UUID_PATH + "\":\"CUSTOM_UUID\\n\"},"
                + "\"proc\":{\"randomUuid\":\"B2C3D4E5-F6A7-8901-BCDE-F12345678901\"}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_UUID\n", readOpenText(emulator, blocks, RANDOM_UUID_PATH));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testOomScoreAdjSelfAndPidAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"oomScoreAdj\":-100}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            assertEquals("-100\n", readOpenText(emulator, blocks, "/proc/self/oom_score_adj"));
            assertEquals("-100\n",
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/oom_score_adj"));

            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            assertEquals("read(\"/proc/self/oom_score_adj\")", e0.api);
            assertEquals("json-config", e0.source);
            assertEquals("path=/proc/self/oom_score_adj,format=oom_score_adj,bytes=5",
                    String.valueOf(e0.value));
            assertTrue(e0.note.contains("读取配置的进程 proc 文件"));
            assertTrue(e0.note.contains("/proc/self/oom_score_adj"));

            CapturedEvent e1 = sink.events.get(1);
            assertEquals("linux_proc", e1.kind);
            assertEquals("read(\"/proc/" + CONFIG_PID + "/oom_score_adj\")", e1.api);
            assertTrue(String.valueOf(e1.value).contains("format=oom_score_adj"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testOomScoreAdjMissingLeavesLegacyNoEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"state\":\"S\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open("/proc/self/oom_score_adj", IOConstants.O_RDONLY);
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 64);
                assertFalse("-100\n".equals(text));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind)
                        && String.valueOf(e.api).contains("oom_score_adj"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testOomScoreAdjWrongPidDoesNotHit() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"oomScoreAdj\":42}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            int wrong = CONFIG_PID + 1;
            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open("/proc/" + wrong + "/oom_score_adj", IOConstants.O_RDONLY);
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 64);
                assertFalse("42\n".equals(text));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind)
                        && String.valueOf(e.api).contains("oom_score_adj"));
            }
            assertEquals("42\n",
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/oom_score_adj"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testOomScoreAdjLinuxFilesPriorityNoLinuxProcEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/oom_score_adj\":\"CUSTOM_OOM\\n\"},"
                + "\"proc\":{\"oomScoreAdj\":-100}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_OOM\n",
                    readOpenText(emulator, blocks, "/proc/self/oom_score_adj"));
            assertEquals("CUSTOM_OOM\n",
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/oom_score_adj"));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testOomScoreSelfAndPidAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"oomScore\":150}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            assertEquals("150\n", readOpenText(emulator, blocks, "/proc/self/oom_score"));
            assertEquals("150\n",
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/oom_score"));

            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            assertEquals("read(\"/proc/self/oom_score\")", e0.api);
            assertEquals("json-config", e0.source);
            assertEquals("path=/proc/self/oom_score,format=oom_score,bytes=4",
                    String.valueOf(e0.value));
            assertTrue(e0.note.contains("读取配置的进程 proc 文件"));
            assertTrue(e0.note.contains("/proc/self/oom_score"));

            CapturedEvent e1 = sink.events.get(1);
            assertEquals("linux_proc", e1.kind);
            assertEquals("read(\"/proc/" + CONFIG_PID + "/oom_score\")", e1.api);
            assertTrue(String.valueOf(e1.value).contains("format=oom_score"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testOomScoreMissingLeavesLegacyNoEvent() throws Exception {
        // oomScoreAdj present must not serve oom_score
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"oomScoreAdj\":100}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open("/proc/self/oom_score", IOConstants.O_RDONLY);
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 64);
                assertFalse("150\n".equals(text));
                assertFalse("100\n".equals(text));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind)
                        && String.valueOf(e.api).contains("oom_score")
                        && !String.valueOf(e.api).contains("oom_score_adj"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testOomScoreLinuxFilesPriorityNoLinuxProcEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/oom_score\":\"CUSTOM_SCORE\\n\"},"
                + "\"proc\":{\"oomScore\":150}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_SCORE\n",
                    readOpenText(emulator, blocks, "/proc/self/oom_score"));
            assertEquals("CUSTOM_SCORE\n",
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/oom_score"));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static final String SELINUX_CTX = "u:r:untrusted_app:s0:c512,c768";

    @Test
    public void testSelinuxContextSelfAndPidAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"selinuxContext\":\"" + SELINUX_CTX + "\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String expected = SELINUX_CTX + "\n";
            assertEquals(expected, readOpenText(emulator, blocks, "/proc/self/attr/current"));
            assertEquals(expected,
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/attr/current"));

            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            assertEquals("read(\"/proc/self/attr/current\")", e0.api);
            assertEquals("json-config", e0.source);
            assertEquals("path=/proc/self/attr/current,format=selinux_context,bytes="
                            + expected.getBytes(StandardCharsets.UTF_8).length,
                    String.valueOf(e0.value));
            assertTrue(e0.note.contains("读取配置的进程 SELinux 上下文"));
            assertTrue(e0.note.contains("/proc/self/attr/current"));

            CapturedEvent e1 = sink.events.get(1);
            assertEquals("linux_proc", e1.kind);
            assertEquals("read(\"/proc/" + CONFIG_PID + "/attr/current\")", e1.api);
            assertTrue(String.valueOf(e1.value).contains("format=selinux_context"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testSelinuxContextMissingLeavesLegacyNoEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"state\":\"S\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open("/proc/self/attr/current", IOConstants.O_RDONLY);
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 256);
                assertFalse(text.startsWith(SELINUX_CTX));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind)
                        && String.valueOf(e.api).contains("attr/current"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testSelinuxContextWrongPidDoesNotHit() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"selinuxContext\":\"" + SELINUX_CTX + "\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            int wrong = CONFIG_PID + 1;
            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open("/proc/" + wrong + "/attr/current", IOConstants.O_RDONLY);
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 256);
                assertFalse((SELINUX_CTX + "\n").equals(text));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind)
                        && String.valueOf(e.api).contains("attr/current"));
            }
            assertEquals(SELINUX_CTX + "\n",
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/attr/current"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testSelinuxContextLinuxFilesPriorityNoLinuxProcEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/attr/current\":\"CUSTOM_SE\\n\"},"
                + "\"proc\":{\"selinuxContext\":\"" + SELINUX_CTX + "\"}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_SE\n",
                    readOpenText(emulator, blocks, "/proc/self/attr/current"));
            assertEquals("CUSTOM_SE\n",
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/attr/current"));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static final String COMM_NAME = "demo_app";

    @Test
    public void testCommSelfAndPidAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"comm\":\"" + COMM_NAME + "\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String expected = COMM_NAME + "\n";
            assertEquals(expected, readOpenText(emulator, blocks, "/proc/self/comm"));
            assertEquals(expected,
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/comm"));

            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            assertEquals("read(\"/proc/self/comm\")", e0.api);
            assertEquals("json-config", e0.source);
            assertEquals("path=/proc/self/comm,format=comm,bytes="
                            + expected.getBytes(StandardCharsets.UTF_8).length,
                    String.valueOf(e0.value));
            assertTrue(e0.note.contains("读取配置的进程 proc 文件"));
            assertTrue(e0.note.contains("/proc/self/comm"));

            CapturedEvent e1 = sink.events.get(1);
            assertEquals("linux_proc", e1.kind);
            assertEquals("read(\"/proc/" + CONFIG_PID + "/comm\")", e1.api);
            assertTrue(String.valueOf(e1.value).contains("format=comm"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testCommMissingLeavesLegacyNoEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"state\":\"S\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open("/proc/self/comm", IOConstants.O_RDONLY);
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 256);
                assertFalse(text.startsWith(COMM_NAME));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind)
                        && String.valueOf(e.api).contains("/comm"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testCommLinuxFilesPriorityNoLinuxProcEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/comm\":\"CUSTOM_COMM\\n\"},"
                + "\"proc\":{\"comm\":\"" + COMM_NAME + "\"}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_COMM\n",
                    readOpenText(emulator, blocks, "/proc/self/comm"));
            assertEquals("CUSTOM_COMM\n",
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/comm"));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static final int CONFIG_TID = 7777;

    @Test
    public void testTaskCommMatchingTidSelfAndPidAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID
                + ",\"tid\":" + CONFIG_TID + "},"
                + "\"linux\":{\"proc\":{\"comm\":\"" + COMM_NAME + "\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String expected = COMM_NAME + "\n";
            String selfTask = "/proc/self/task/" + CONFIG_TID + "/comm";
            String pidTask = "/proc/" + CONFIG_PID + "/task/" + CONFIG_TID + "/comm";
            assertEquals(expected, readOpenText(emulator, blocks, selfTask));
            assertEquals(expected, readOpenText(emulator, blocks, pidTask));
            // process-level still works
            assertEquals(expected, readOpenText(emulator, blocks, "/proc/self/comm"));

            assertEquals(3, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            assertEquals("read(\"" + selfTask + "\")", e0.api);
            assertEquals("json-config", e0.source);
            assertEquals("path=" + selfTask + ",format=comm,bytes="
                            + expected.getBytes(StandardCharsets.UTF_8).length,
                    String.valueOf(e0.value));
            assertTrue(e0.note.contains(selfTask));

            CapturedEvent e1 = sink.events.get(1);
            assertEquals("linux_proc", e1.kind);
            assertEquals("read(\"" + pidTask + "\")", e1.api);
            assertTrue(String.valueOf(e1.value).contains("format=comm"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testTaskCommTidFallbackToEmulatorPid() throws Exception {
        // process.tid absent → tid falls back to emulator pid
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"comm\":\"" + COMM_NAME + "\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String expected = COMM_NAME + "\n";
            String selfTask = "/proc/self/task/" + CONFIG_PID + "/comm";
            String pidTask = "/proc/" + CONFIG_PID + "/task/" + CONFIG_PID + "/comm";
            assertEquals(expected, readOpenText(emulator, blocks, selfTask));
            assertEquals(expected, readOpenText(emulator, blocks, pidTask));
            assertEquals(2, sink.events.size());
            assertEquals("linux_proc", sink.events.get(0).kind);
            assertTrue(String.valueOf(sink.events.get(0).value).contains("format=comm"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testTaskCommNonMatchingTidNotServed() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID
                + ",\"tid\":" + CONFIG_TID + "},"
                + "\"linux\":{\"proc\":{\"comm\":\"" + COMM_NAME + "\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            int otherTid = CONFIG_TID + 1;
            String otherPath = "/proc/self/task/" + otherTid + "/comm";
            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open(otherPath, IOConstants.O_RDONLY);
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 256);
                assertFalse(text.startsWith(COMM_NAME));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind)
                        && String.valueOf(e.api).contains("/task/" + otherTid + "/comm"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testTaskCommLinuxFilesPriorityNoLinuxProcEvent() throws Exception {
        String selfTask = "/proc/self/task/" + CONFIG_TID + "/comm";
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID
                + ",\"tid\":" + CONFIG_TID + "},"
                + "\"linux\":{"
                + "\"files\":{\"" + selfTask + "\":\"CUSTOM_TASK_COMM\\n\"},"
                + "\"proc\":{\"comm\":\"" + COMM_NAME + "\"}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_TASK_COMM\n",
                    readOpenText(emulator, blocks, selfTask));
            // pid→self files mapping for same task path
            assertEquals("CUSTOM_TASK_COMM\n",
                    readOpenText(emulator, blocks,
                            "/proc/" + CONFIG_PID + "/task/" + CONFIG_TID + "/comm"));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static final String WCHAN_VALUE = "0";

    @Test
    public void testWchanSelfAndPidAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"wchan\":\"" + WCHAN_VALUE + "\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String expected = WCHAN_VALUE + "\n";
            assertEquals(expected, readOpenText(emulator, blocks, "/proc/self/wchan"));
            assertEquals(expected,
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/wchan"));

            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            assertEquals("read(\"/proc/self/wchan\")", e0.api);
            assertEquals("json-config", e0.source);
            assertEquals("path=/proc/self/wchan,format=wchan,bytes="
                            + expected.getBytes(StandardCharsets.UTF_8).length,
                    String.valueOf(e0.value));
            assertTrue(e0.note.contains("读取配置的进程 proc 文件"));
            assertTrue(e0.note.contains("/proc/self/wchan"));

            CapturedEvent e1 = sink.events.get(1);
            assertEquals("linux_proc", e1.kind);
            assertEquals("read(\"/proc/" + CONFIG_PID + "/wchan\")", e1.api);
            assertTrue(String.valueOf(e1.value).contains("format=wchan"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testWchanMissingLeavesLegacyNoEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"state\":\"S\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            // key absent: may fall back to legacy open; must not emit linux_proc for wchan
            emulator.getFileSystem().open("/proc/self/wchan", IOConstants.O_RDONLY);
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind)
                        && String.valueOf(e.api).contains("/wchan"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testWchanLinuxFilesPriorityNoLinuxProcEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/wchan\":\"CUSTOM_WCHAN\\n\"},"
                + "\"proc\":{\"wchan\":\"" + WCHAN_VALUE + "\"}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_WCHAN\n",
                    readOpenText(emulator, blocks, "/proc/self/wchan"));
            assertEquals("CUSTOM_WCHAN\n",
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/wchan"));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testTaskWchanMatchingTidSelfAndPidAndSidecar() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID
                + ",\"tid\":" + CONFIG_TID + "},"
                + "\"linux\":{\"proc\":{\"wchan\":\"" + WCHAN_VALUE + "\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String expected = WCHAN_VALUE + "\n";
            String selfTask = "/proc/self/task/" + CONFIG_TID + "/wchan";
            String pidTask = "/proc/" + CONFIG_PID + "/task/" + CONFIG_TID + "/wchan";
            assertEquals(expected, readOpenText(emulator, blocks, selfTask));
            assertEquals(expected, readOpenText(emulator, blocks, pidTask));
            assertEquals(expected, readOpenText(emulator, blocks, "/proc/self/wchan"));

            assertEquals(3, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            assertEquals("read(\"" + selfTask + "\")", e0.api);
            assertEquals("json-config", e0.source);
            assertEquals("path=" + selfTask + ",format=wchan,bytes="
                            + expected.getBytes(StandardCharsets.UTF_8).length,
                    String.valueOf(e0.value));
            assertTrue(e0.note.contains(selfTask));

            CapturedEvent e1 = sink.events.get(1);
            assertEquals("linux_proc", e1.kind);
            assertEquals("read(\"" + pidTask + "\")", e1.api);
            assertTrue(String.valueOf(e1.value).contains("format=wchan"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testTaskWchanTidFallbackToEmulatorPid() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"proc\":{\"wchan\":\"" + WCHAN_VALUE + "\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            String expected = WCHAN_VALUE + "\n";
            String selfTask = "/proc/self/task/" + CONFIG_PID + "/wchan";
            String pidTask = "/proc/" + CONFIG_PID + "/task/" + CONFIG_PID + "/wchan";
            assertEquals(expected, readOpenText(emulator, blocks, selfTask));
            assertEquals(expected, readOpenText(emulator, blocks, pidTask));
            assertEquals(2, sink.events.size());
            assertEquals("linux_proc", sink.events.get(0).kind);
            assertTrue(String.valueOf(sink.events.get(0).value).contains("format=wchan"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testTaskWchanNonMatchingTidNotServed() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID
                + ",\"tid\":" + CONFIG_TID + "},"
                + "\"linux\":{\"proc\":{\"wchan\":\"" + WCHAN_VALUE + "\"}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            int otherTid = CONFIG_TID + 1;
            String otherPath = "/proc/self/task/" + otherTid + "/wchan";
            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open(otherPath, IOConstants.O_RDONLY);
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 256);
                assertFalse(text.startsWith(WCHAN_VALUE));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind)
                        && String.valueOf(e.api).contains("/task/" + otherTid + "/wchan"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testTaskWchanLinuxFilesPriorityNoLinuxProcEvent() throws Exception {
        String selfTask = "/proc/self/task/" + CONFIG_TID + "/wchan";
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID
                + ",\"tid\":" + CONFIG_TID + "},"
                + "\"linux\":{"
                + "\"files\":{\"" + selfTask + "\":\"CUSTOM_TASK_WCHAN\\n\"},"
                + "\"proc\":{\"wchan\":\"" + WCHAN_VALUE + "\"}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_TASK_WCHAN\n",
                    readOpenText(emulator, blocks, selfTask));
            assertEquals("CUSTOM_TASK_WCHAN\n",
                    readOpenText(emulator, blocks,
                            "/proc/" + CONFIG_PID + "/task/" + CONFIG_TID + "/wchan"));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static final byte[] DEFAULT_ENVIRON_BYTES = concat(
            "ANDROID_DATA=/data\0".getBytes(StandardCharsets.UTF_8),
            "ANDROID_ROOT=/system\0".getBytes(StandardCharsets.UTF_8),
            "PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin\0"
                    .getBytes(StandardCharsets.UTF_8),
            "NO_ADDR_COMPAT_LAYOUT_FIXUP=1\0".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    public void testEnvironDefaultsSelfAndPidAndSidecar64() throws Exception {
        runEnvironDefaults(true);
    }

    @Test
    public void testEnvironDefaultsSelfAndPidAndSidecar32() throws Exception {
        runEnvironDefaults(false);
    }

    @Test
    public void testEnvironConfiguredReplaceAndEmpty64() throws Exception {
        runEnvironConfigured(true);
    }

    @Test
    public void testEnvironConfiguredReplaceAndEmpty32() throws Exception {
        runEnvironConfigured(false);
    }

    @Test
    public void testEnvironLinuxFilesPriorityNoLinuxProcEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"x\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{"
                + "\"files\":{\"/proc/self/environ\":\"FROM_FILES\\u0000\"},"
                + "\"environ\":[\"A=1\"]"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("FROM_FILES\0",
                    new String(readOpenBytes(emulator, blocks, "/proc/self/environ"),
                            StandardCharsets.UTF_8));
            // pid→self alias for linux.files
            assertEquals("FROM_FILES\0",
                    new String(readOpenBytes(emulator, blocks,
                            "/proc/" + CONFIG_PID + "/environ"), StandardCharsets.UTF_8));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runEnvironDefaults(boolean is64Bit) throws Exception {
        // no linux.environ — structured path still serves built-in four defaults + sidecar
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(CONFIG_PID, emulator.getPid());

            assertArrayEquals(DEFAULT_ENVIRON_BYTES,
                    readOpenBytes(emulator, blocks, "/proc/self/environ"));
            assertArrayEquals(DEFAULT_ENVIRON_BYTES,
                    readOpenBytes(emulator, blocks, "/proc/" + CONFIG_PID + "/environ"));

            assertEquals(2, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("linux_proc", e0.kind);
            assertEquals("read(\"/proc/self/environ\")", e0.api);
            assertEquals("unidbg-default", e0.source);
            assertEquals("path=/proc/self/environ,format=environ,bytes="
                            + DEFAULT_ENVIRON_BYTES.length,
                    String.valueOf(e0.value));
            assertTrue(e0.note.contains("读取模拟器内建默认进程环境"));
            assertTrue(e0.note.contains("/proc/self/environ"));

            CapturedEvent e1 = sink.events.get(1);
            assertEquals("linux_proc", e1.kind);
            assertEquals("read(\"/proc/" + CONFIG_PID + "/environ\")", e1.api);
            assertEquals("unidbg-default", e1.source);
            assertTrue(e1.note.contains("读取模拟器内建默认进程环境"));
            assertTrue(String.valueOf(e1.value).contains("format=environ"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runEnvironConfigured(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"environ\":[\"CUSTOM_A=1\",\"CUSTOM_B=x=y\"]}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] expected = concat(
                    "CUSTOM_A=1\0".getBytes(StandardCharsets.UTF_8),
                    "CUSTOM_B=x=y\0".getBytes(StandardCharsets.UTF_8)
            );
            assertArrayEquals(expected, readOpenBytes(emulator, blocks, "/proc/self/environ"));
            assertArrayEquals(expected,
                    readOpenBytes(emulator, blocks, "/proc/" + CONFIG_PID + "/environ"));

            CapturedEvent e0 = findLastEvent(sink.events, "linux_proc",
                    "read(\"/proc/self/environ\")");
            assertNotNull(e0);
            assertEquals("json-config", e0.source);
            assertTrue(e0.note.contains("读取配置的进程环境"));
            assertEquals("path=/proc/self/environ,format=environ,bytes=" + expected.length,
                    String.valueOf(e0.value));

            // empty array → empty content + sidecar with json-config (key present)
            sink.events.clear();
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }

        TraceEnvironmentConfig emptyCfg = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"environ\":[]}"
                + "}");
        sink = new CapturingSink();
        emulator = null;
        blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(emptyCfg)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(0, readOpenBytes(emulator, blocks, "/proc/self/environ").length);
            CapturedEvent ev = findLastEvent(sink.events, "linux_proc",
                    "read(\"/proc/self/environ\")");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertTrue(ev.note.contains("读取配置的进程环境"));
            assertEquals("path=/proc/self/environ,format=environ,bytes=0",
                    String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static CapturedEvent findLastEvent(List<CapturedEvent> events, String kind, String api) {
        CapturedEvent found = null;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)) {
                found = e;
            }
        }
        return found;
    }

    private static String readOpenText(AndroidEmulator emulator, List<MemoryBlock> blocks, String path)
            throws Exception {
        return new String(readOpenBytes(emulator, blocks, path), StandardCharsets.UTF_8);
    }

    private static byte[] readOpenBytes(AndroidEmulator emulator, List<MemoryBlock> blocks, String path)
            throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        assertNotNull(result);
        assertTrue("open failed for " + path, result.isSuccess());
        assertNotNull(result.io);
        assertTrue(result.io instanceof ByteArrayFileIO);
        return readIoBytes(emulator, blocks, result.io, 8192);
    }

    private static String readIoText(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                     AndroidFileIO io, int max) throws Exception {
        return new String(readIoBytes(emulator, blocks, io, max), StandardCharsets.UTF_8);
    }

    private static byte[] readIoBytes(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                      AndroidFileIO io, int max) throws Exception {
        MemoryBlock block = emulator.getMemory().malloc(max, true);
        blocks.add(block);
        Pointer ptr = block.getPointer();
        int n = io.read(emulator.getBackend(), ptr, max);
        assertTrue(n >= 0);
        if (n == 0) {
            return new byte[0];
        }
        return ptr.getByteArray(0, n);
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

    private static final String UNAME_HOSTNAME_PATH = "/proc/sys/kernel/hostname";
    private static final String UNAME_OSRELEASE_PATH = "/proc/sys/kernel/osrelease";
    private static final String UNAME_VERSION_PATH = "/proc/sys/kernel/version";
    private static final String UNAME_DOMAINNAME_PATH = "/proc/sys/kernel/domainname";
    private static final String UNAME_OSTYPE_PATH = "/proc/sys/kernel/ostype";

    @Test
    public void testUnameKernelFilesConfiguredOpenAndSidecar64() throws Exception {
        runUnameKernelFilesConfigured(true);
    }

    @Test
    public void testUnameKernelFilesConfiguredOpenAndSidecar32() throws Exception {
        runUnameKernelFilesConfigured(false);
    }

    private static void runUnameKernelFilesConfigured(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"uname\":{"
                + "\"sysname\":\"Linux\","
                + "\"nodename\":\"trace-host\","
                + "\"release\":\"5.4.210-test\","
                + "\"version\":\"#1 SMP TEST\","
                + "\"domainname\":\"(none)\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("trace-host\n",
                    readOpenText(emulator, blocks, UNAME_HOSTNAME_PATH));
            assertEquals("5.4.210-test\n",
                    readOpenText(emulator, blocks, UNAME_OSRELEASE_PATH));
            assertEquals("#1 SMP TEST\n",
                    readOpenText(emulator, blocks, UNAME_VERSION_PATH));
            assertEquals("(none)\n",
                    readOpenText(emulator, blocks, UNAME_DOMAINNAME_PATH));
            assertEquals("Linux\n",
                    readOpenText(emulator, blocks, UNAME_OSTYPE_PATH));

            assertEquals(5, sink.events.size());
            assertUnameSidecar(sink.events.get(0), UNAME_HOSTNAME_PATH, "hostname",
                    "trace-host\n".getBytes(StandardCharsets.UTF_8).length);
            assertUnameSidecar(sink.events.get(1), UNAME_OSRELEASE_PATH, "osrelease",
                    "5.4.210-test\n".getBytes(StandardCharsets.UTF_8).length);
            assertUnameSidecar(sink.events.get(2), UNAME_VERSION_PATH, "version",
                    "#1 SMP TEST\n".getBytes(StandardCharsets.UTF_8).length);
            assertUnameSidecar(sink.events.get(3), UNAME_DOMAINNAME_PATH, "domainname",
                    "(none)\n".getBytes(StandardCharsets.UTF_8).length);
            assertUnameSidecar(sink.events.get(4), UNAME_OSTYPE_PATH, "ostype",
                    "Linux\n".getBytes(StandardCharsets.UTF_8).length);
            for (CapturedEvent e : sink.events) {
                assertFalse(String.valueOf(e.value).contains("trace-host"));
                assertFalse(String.valueOf(e.value).contains("5.4.210-test"));
                assertFalse(String.valueOf(e.value).contains("Linux"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void assertUnameSidecar(CapturedEvent e, String path, String format, int bytes) {
        assertEquals("linux_proc", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=" + format + ",bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains(path));
    }

    @Test
    public void testUnameKernelFilesAbsentFieldsLegacyNoEvent() throws Exception {
        // empty uname: no auto files; partial: only configured keys
        TraceEnvironmentConfig emptyUname = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"uname\":{}}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(emptyUname).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            for (String path : new String[]{
                    UNAME_HOSTNAME_PATH, UNAME_OSRELEASE_PATH,
                    UNAME_VERSION_PATH, UNAME_DOMAINNAME_PATH, UNAME_OSTYPE_PATH
            }) {
                FileResult<AndroidFileIO> result = emulator.getFileSystem()
                        .open(path, IOConstants.O_RDONLY);
                if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                    String text = readIoText(emulator, blocks, result.io, 64);
                    assertFalse(text.startsWith("trace-host"));
                }
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("linux_proc".equals(e.kind)
                        && (String.valueOf(e.api).contains("hostname")
                        || String.valueOf(e.api).contains("osrelease")
                        || String.valueOf(e.api).contains("domainname")
                        || String.valueOf(e.api).contains("ostype")
                        || String.valueOf(e.value).contains("format=version")
                        || String.valueOf(e.value).contains("format=ostype")));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }

        TraceEnvironmentConfig partial = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"uname\":{\"nodename\":\"only-host\"}}}"
        );
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(partial).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("only-host\n",
                    readOpenText(emulator, blocks, UNAME_HOSTNAME_PATH));
            FileResult<AndroidFileIO> osrel = emulator.getFileSystem()
                    .open(UNAME_OSRELEASE_PATH, IOConstants.O_RDONLY);
            if (osrel != null && osrel.isSuccess() && osrel.io instanceof ByteArrayFileIO) {
                assertFalse(readIoText(emulator, blocks, osrel.io, 64).startsWith("5.4.210-test"));
            }
            assertEquals(1, sink.events.size());
            assertEquals("hostname", formatFromValue(sink.events.get(0)));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static String formatFromValue(CapturedEvent e) {
        String v = String.valueOf(e.value);
        int i = v.indexOf("format=");
        if (i < 0) {
            return null;
        }
        int start = i + "format=".length();
        int end = v.indexOf(',', start);
        return end < 0 ? v.substring(start) : v.substring(start, end);
    }

    @Test
    public void testUnameKernelFilesLinuxFilesPriorityNoLinuxProcEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{"
                + "\"files\":{"
                + "\"" + UNAME_HOSTNAME_PATH + "\":\"CUSTOM_HOST\\n\","
                + "\"" + UNAME_OSTYPE_PATH + "\":\"CUSTOM_OSTYPE\\n\""
                + "},"
                + "\"uname\":{"
                + "\"sysname\":\"Linux\","
                + "\"nodename\":\"trace-host\","
                + "\"release\":\"5.4.210-test\""
                + "}"
                + "}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals("CUSTOM_HOST\n",
                    readOpenText(emulator, blocks, UNAME_HOSTNAME_PATH));
            assertEquals("CUSTOM_OSTYPE\n",
                    readOpenText(emulator, blocks, UNAME_OSTYPE_PATH));
            // release still auto-served from uname
            assertEquals("5.4.210-test\n",
                    readOpenText(emulator, blocks, UNAME_OSRELEASE_PATH));

            boolean sawLinuxFile = false;
            boolean sawOsreleaseProc = false;
            for (CapturedEvent e : sink.events) {
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
                if ("linux_proc".equals(e.kind)
                        && String.valueOf(e.value).contains("format=hostname")) {
                    fail("hostname should not emit linux_proc when linux.files wins");
                }
                if ("linux_proc".equals(e.kind)
                        && String.valueOf(e.value).contains("format=ostype")) {
                    fail("ostype should not emit linux_proc when linux.files wins");
                }
                if ("linux_proc".equals(e.kind)
                        && String.valueOf(e.value).contains("format=osrelease")) {
                    sawOsreleaseProc = true;
                }
            }
            assertTrue(sawLinuxFile);
            assertTrue(sawOsreleaseProc);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void freeAll(List<MemoryBlock> blocks) {
        for (MemoryBlock block : blocks) {
            try {
                block.free();
            } catch (Exception ignored) {
                // teardown
            }
        }
        blocks.clear();
    }

    private static final class CapturedEvent {
        final String kind;
        final String api;
        final Object value;
        final String source;
        final String note;

        CapturedEvent(String kind, String api, Object value, String source, String note) {
            this.kind = kind;
            this.api = api;
            this.value = value;
            this.source = source;
            this.note = note;
        }
    }

    private static final class CapturingSink implements TraceEnvironmentEventSink {
        final List<CapturedEvent> events = new ArrayList<CapturedEvent>();

        @Override
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}

package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.DeviceFingerprintProfile;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.file.ByteArrayFileIO;
import com.github.unidbg.unix.UnixEmulator;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Phase-1 device-fingerprint overlay + sidecar source. Does not change setEnvironmentConfig.
 */
public class DeviceFingerprintProfileOverlayTest {

    private static final int GL_VENDOR = 0x1F00;

    @Test
    public void testOverlayBytesAndPidAlias() throws Exception {
        File sample = locateSampleProfile();
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.load(sample);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit()
                    .setEnvironmentProfile(sample)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals(12345, emulator.getPid());
            assertTrue(profile.getEnvironmentConfig().isProfileLoaded());

            byte[] expectedCpu = Files.readAllBytes(
                    new File(sample.getParentFile(), "files/proc/cpuinfo").toPath());
            assertArrayEquals(expectedCpu, readOpenBytes(emulator, blocks, "/proc/cpuinfo"));
            byte[] statusSelf = readOpenBytes(emulator, blocks, "/proc/self/status");
            byte[] statusPid = readOpenBytes(emulator, blocks, "/proc/12345/status");
            assertArrayEquals(statusSelf, statusPid);
            byte[] expectedStatus = Files.readAllBytes(
                    new File(sample.getParentFile(), "files/proc/self/status").toPath());
            assertArrayEquals(expectedStatus, statusSelf);

            File wlanOnDisk = new File(sample.getParentFile(),
                    "files/sys/class/net/wlan0/address");
            byte[] expectedWlan = Files.readAllBytes(wlanOnDisk.toPath());
            assertArrayEquals(expectedWlan,
                    readOpenBytes(emulator, blocks, "/sys/class/net/wlan0/address"));
            CapturedEvent wlanEv = findLastEvent(sink.events, "linux_file",
                    "open(\"/sys/class/net/wlan0/address\")");
            assertNotNull(wlanEv);
            assertEquals("profile-file", wlanEv.source);
            assertEquals("path=/sys/class/net/wlan0/address,bytes=" + expectedWlan.length,
                    String.valueOf(wlanEv.value));

            FileResult<AndroidFileIO> wrongPid = emulator.getFileSystem()
                    .open("/proc/99999/status", IOConstants.O_RDONLY);
            if (wrongPid != null && wrongPid.isSuccess() && wrongPid.io instanceof ByteArrayFileIO) {
                byte[] leaked = readIoBytes(emulator, blocks, wrongPid.io, 256);
                assertFalse(new String(leaked, StandardCharsets.UTF_8)
                        .contains("traceai-profile"));
            }

            FileResult<AndroidFileIO> escaped = emulator.getFileSystem()
                    .open("/proc/self/../cpuinfo", IOConstants.O_RDONLY);
            if (escaped != null && escaped.isSuccess() && escaped.io instanceof ByteArrayFileIO) {
                fail("escaped path must not hit overlay");
            }
            FileResult<AndroidFileIO> nulled = emulator.getFileSystem()
                    .open("/proc/cpuinfo\0/../secret", IOConstants.O_RDONLY);
            if (nulled != null && nulled.isSuccess() && nulled.io instanceof ByteArrayFileIO) {
                fail("NUL guest path must not hit overlay");
            }
            FileResult<AndroidFileIO> slashed = emulator.getFileSystem()
                    .open("/proc/cpuinfo\\..\\secret", IOConstants.O_RDONLY);
            if (slashed != null && slashed.isSuccess() && slashed.io instanceof ByteArrayFileIO) {
                fail("backslash guest path must not hit overlay");
            }

            CapturedEvent ev = findLastEvent(sink.events, "linux_file", "open(\"/proc/cpuinfo\")");
            assertNotNull(ev);
            assertEquals("profile-file", ev.source);
            assertEquals("path=/proc/cpuinfo,bytes=" + expectedCpu.length, String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testProfileJsonSidecarDoesNotChangePlainConfig() throws Exception {
        TraceEnvironmentConfig plain = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"build\":{\"SERIAL\":\"PLAIN_SERIAL\"}}"
                + "}");
        assertFalse(plain.isProfileLoaded());
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(plain).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            String serial = callBuildGetSerial(emulator.createDalvikVM(), new AbstractJni() {
            });
            assertEquals("PLAIN_SERIAL", serial);
            CapturedEvent ev = findLastEvent(sink.events, "android_build", "Build.getSerial");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testProfileJsonSidecarAndGraphics() throws Exception {
        File sample = locateSampleProfile();
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(sample).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            String serial = callBuildGetSerial(vm, jni);
            assertEquals("TRACEAI_SERIAL_V1", serial);
            CapturedEvent serialEv = findLastEvent(sink.events, "android_build", "Build.getSerial");
            assertNotNull(serialEv);
            assertEquals("profile-json", serialEv.source);

            String vendor = callGlesGetString(vm, jni, GL_VENDOR);
            assertEquals("TRACEAI_GPU_VENDOR", vendor);
            CapturedEvent gfx = findLastEvent(sink.events, "graphics", "GLES10.glGetString");
            assertNotNull(gfx);
            assertEquals("profile-json", gfx.source);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testExplicitConfigBeatsProfile() throws Exception {
        File sample = locateSampleProfile();
        TraceEnvironmentConfig plain = TraceEnvironmentConfig.parse(
                "{\"android\":{\"build\":{\"SERIAL\":\"PLAIN_SERIAL\"}}}");
        AndroidEmulator emulator = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit()
                    .setEnvironmentProfile(sample)
                    .setEnvironmentConfig(plain)
                    .build();
            TraceEnvironmentConfig attached = TraceEnvironmentConfig.get(emulator);
            assertNotNull(attached);
            assertFalse(attached.isProfileLoaded());
            assertEquals("PLAIN_SERIAL", attached.getAndroidBuildString("SERIAL"));
            emulator.close();
            emulator = AndroidEmulatorBuilder.for64Bit()
                    .setEnvironmentConfig(plain)
                    .setEnvironmentProfile(sample)
                    .build();
            attached = TraceEnvironmentConfig.get(emulator);
            assertFalse(attached.isProfileLoaded());
            assertEquals("PLAIN_SERIAL", attached.getAndroidBuildString("SERIAL"));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testOverlayBeatsLinuxFiles() throws Exception {
        File dir = Files.createTempDirectory("traceai-profile-").toFile();
        File overlayCpu = new File(dir, "files/proc/cpuinfo");
        assertTrue(overlayCpu.getParentFile().mkdirs());
        Files.write(overlayCpu.toPath(), "OVERLAY_CPU".getBytes(StandardCharsets.UTF_8));
        File json = new File(dir, "device-fingerprint.json");
        String body = "{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"fileOverlayRoot\":\"files\","
                + "\"linux\":{\"files\":{\"/proc/cpuinfo\":\"INLINE_CPU\"}}"
                + "}";
        Files.write(json.toPath(), body.getBytes(StandardCharsets.UTF_8));

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(json).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("OVERLAY_CPU",
                    new String(readOpenBytes(emulator, blocks, "/proc/cpuinfo"), StandardCharsets.UTF_8));
            CapturedEvent ev = findLastEvent(sink.events, "linux_file", "open(\"/proc/cpuinfo\")");
            assertNotNull(ev);
            assertEquals("profile-file", ev.source);

            FileResult<AndroidFileIO> rdwr = emulator.getFileSystem()
                    .open("/proc/cpuinfo", IOConstants.O_RDWR);
            assertNotNull(rdwr);
            assertFalse(rdwr.isSuccess());
            assertEquals(UnixEmulator.EACCES, rdwr.errno);
            assertEquals("profile-file",
                    findLastEvent(sink.events, "linux_file", "open(\"/proc/cpuinfo\")").source);
            assertEquals("path=/proc/cpuinfo,bytes=11",
                    String.valueOf(findLastEvent(sink.events, "linux_file",
                            "open(\"/proc/cpuinfo\")").value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testWriteOpenDoesNotServeOverlay() throws Exception {
        File sample = locateSampleProfile();
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(sample).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open("/proc/cpuinfo", IOConstants.O_WRONLY);
            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertEquals(UnixEmulator.EACCES, result.errno);

            FileResult<AndroidFileIO> rdwr = emulator.getFileSystem()
                    .open("/proc/cpuinfo", IOConstants.O_RDWR);
            assertNotNull(rdwr);
            assertFalse(rdwr.isSuccess());
            assertEquals(UnixEmulator.EACCES, rdwr.errno);

            FileResult<AndroidFileIO> dir = emulator.getFileSystem()
                    .open("/proc/cpuinfo", IOConstants.O_RDONLY | IOConstants.O_DIRECTORY);
            assertNotNull(dir);
            assertFalse(dir.isSuccess());
            assertEquals(UnixEmulator.ENOTDIR, dir.errno);

            assertTrue(findLastEvent(sink.events, "linux_file", "open(\"/proc/cpuinfo\")") == null);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testBinaryOverlayExactBytes() throws Exception {
        File dir = Files.createTempDirectory("traceai-profile-bin-").toFile();
        File overlayBin = new File(dir, "files/proc/self/auxv");
        assertTrue(overlayBin.getParentFile().mkdirs());
        byte[] binary = new byte[] { 0x00, (byte) 0xFF, 0x0A, 0x0D, 0x7F, 'E', 'L', 'F' };
        Files.write(overlayBin.toPath(), binary);
        File empty = new File(dir, "files/proc/empty");
        Files.write(empty.toPath(), new byte[0]);
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"process\":{\"pid\":77},"
                + "\"fileOverlayRoot\":\"files\","
                + "\"linux\":{\"files\":{\"/proc/self/auxv\":\"INLINE_AUXV\"}}"
                + "}").getBytes(StandardCharsets.UTF_8));

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(json).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertArrayEquals(binary, readOpenBytes(emulator, blocks, "/proc/self/auxv"));
            assertArrayEquals(binary, readOpenBytes(emulator, blocks, "/proc/77/auxv"));
            assertArrayEquals(new byte[0], readOpenBytes(emulator, blocks, "/proc/empty"));
            CapturedEvent ev = findLastEvent(sink.events, "linux_file", "open(\"/proc/self/auxv\")");
            assertNotNull(ev);
            assertEquals("profile-file", ev.source);
            assertEquals("path=/proc/self/auxv,bytes=" + binary.length, String.valueOf(ev.value));
            CapturedEvent emptyEv = findLastEvent(sink.events, "linux_file", "open(\"/proc/empty\")");
            assertNotNull(emptyEv);
            assertEquals("profile-file", emptyEv.source);
            assertEquals("path=/proc/empty,bytes=0", String.valueOf(emptyEv.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testOverlayMissUsesLinuxFilesWithProfileJsonSidecar() throws Exception {
        File dir = Files.createTempDirectory("traceai-profile-miss-").toFile();
        File overlayDir = new File(dir, "files/proc");
        assertTrue(overlayDir.mkdirs());
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"fileOverlayRoot\":\"files\","
                + "\"linux\":{\"files\":{\"/proc/cpuinfo\":\"INLINE_CPU\"}}"
                + "}").getBytes(StandardCharsets.UTF_8));

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(json).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("INLINE_CPU",
                    new String(readOpenBytes(emulator, blocks, "/proc/cpuinfo"), StandardCharsets.UTF_8));
            CapturedEvent ev = findLastEvent(sink.events, "linux_file", "open(\"/proc/cpuinfo\")");
            assertNotNull(ev);
            assertEquals("profile-json", ev.source);
            assertFalse(String.valueOf(ev.value).startsWith("path=/proc/cpuinfo,bytes="));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testSampleBatch1OverlayFilesReadable() throws Exception {
        File sample = locateSampleProfile();
        File filesRoot = new File(sample.getParentFile(), "files");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(sample).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertSampleFileOpen(emulator, blocks, filesRoot, "/proc/meminfo");
            assertSampleFileOpen(emulator, blocks, filesRoot, "/proc/version");
            assertSampleFileOpen(emulator, blocks, filesRoot, "/proc/self/cmdline");
            assertSampleFileOpen(emulator, blocks, filesRoot, "/proc/12345/cmdline");
            assertSampleFileOpen(emulator, blocks, filesRoot, "/proc/self/cgroup");
            assertSampleFileOpen(emulator, blocks, filesRoot, "/proc/self/maps");
            assertSampleFileOpen(emulator, blocks, filesRoot, "/proc/sys/kernel/random/boot_id");
            assertSampleFileOpen(emulator, blocks, filesRoot, "/sys/devices/system/cpu/online");
            assertSampleFileOpen(emulator, blocks, filesRoot, "/sys/class/net/wlan0/mtu");
            assertSampleFileOpen(emulator, blocks, filesRoot, "/sys/class/net/wlan0/operstate");
            assertSampleFileOpen(emulator, blocks, filesRoot, "/sys/class/net/wlan0/carrier");
            CapturedEvent boot = findLastEvent(sink.events, "linux_file",
                    "open(\"/proc/sys/kernel/random/boot_id\")");
            assertNotNull(boot);
            assertEquals("profile-file", boot.source);
            CapturedEvent cpuOnline = findLastEvent(sink.events, "linux_file",
                    "open(\"/sys/devices/system/cpu/online\")");
            assertNotNull(cpuOnline);
            assertEquals("profile-file", cpuOnline.source);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testSetEnvironmentProfileStringPath() throws Exception {
        File sample = locateSampleProfile();
        AndroidEmulator emulator = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit()
                    .setEnvironmentProfile(sample.getAbsolutePath())
                    .build();
            TraceEnvironmentConfig attached = TraceEnvironmentConfig.get(emulator);
            assertTrue(attached.isProfileLoaded());
            assertEquals("TRACEAI_SERIAL_V1", attached.getAndroidBuildString("SERIAL"));
            assertEquals(12345, emulator.getPid());
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testSystemPropertyConfigBeatsProfile() throws Exception {
        File sample = locateSampleProfile();
        File dir = Files.createTempDirectory("traceai-sysprop-config-").toFile();
        File configFile = new File(dir, "trace-env.json");
        Files.write(configFile.toPath(),
                "{\"android\":{\"build\":{\"SERIAL\":\"SYS_CONFIG_SERIAL\"}}}"
                        .getBytes(StandardCharsets.UTF_8));
        String previousConfig = System.getProperty(TraceEnvironmentConfig.SYSTEM_PROPERTY);
        String previousProfile = System.getProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY);
        AndroidEmulator emulator = null;
        try {
            System.setProperty(TraceEnvironmentConfig.SYSTEM_PROPERTY, configFile.getAbsolutePath());
            System.setProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY, sample.getAbsolutePath());
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            TraceEnvironmentConfig attached = TraceEnvironmentConfig.get(emulator);
            assertNotNull(attached);
            assertFalse(attached.isProfileLoaded());
            assertEquals("SYS_CONFIG_SERIAL", attached.getAndroidBuildString("SERIAL"));
        } finally {
            restoreProperty(TraceEnvironmentConfig.SYSTEM_PROPERTY, previousConfig);
            restoreProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY, previousProfile);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testSystemPropertyProfileAloneLoads() throws Exception {
        File sample = locateSampleProfile();
        String previousConfig = System.getProperty(TraceEnvironmentConfig.SYSTEM_PROPERTY);
        String previousProfile = System.getProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            System.clearProperty(TraceEnvironmentConfig.SYSTEM_PROPERTY);
            System.setProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY, sample.getAbsolutePath());
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            TraceEnvironmentEventSink.register(emulator, sink);
            TraceEnvironmentConfig attached = TraceEnvironmentConfig.get(emulator);
            assertTrue(attached.isProfileLoaded());
            assertEquals("TRACEAI_SERIAL_V1", attached.getAndroidBuildString("SERIAL"));
            String serial = callBuildGetSerial(emulator.createDalvikVM(), new AbstractJni() {
            });
            assertEquals("TRACEAI_SERIAL_V1", serial);
            CapturedEvent ev = findLastEvent(sink.events, "android_build", "Build.getSerial");
            assertNotNull(ev);
            assertEquals("profile-json", ev.source);
        } finally {
            restoreProperty(TraceEnvironmentConfig.SYSTEM_PROPERTY, previousConfig);
            restoreProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY, previousProfile);
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testCollapsedGuestPathsDoNotHitOverlay() throws Exception {
        File sample = locateSampleProfile();
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(sample).build();
            byte[] overlayCpu = Files.readAllBytes(
                    new File(sample.getParentFile(), "files/proc/cpuinfo").toPath());
            assertNotOverlayHit(emulator, blocks, "/proc//cpuinfo", overlayCpu);
            assertNotOverlayHit(emulator, blocks, "/proc/cpuinfo/", overlayCpu);
            assertNotOverlayHit(emulator, blocks, "/proc/./cpuinfo", overlayCpu);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testEmptyOverlayBeatsLinuxFiles() throws Exception {
        File dir = Files.createTempDirectory("traceai-empty-overlay-").toFile();
        File overlayCpu = new File(dir, "files/proc/cpuinfo");
        assertTrue(overlayCpu.getParentFile().mkdirs());
        Files.write(overlayCpu.toPath(), new byte[0]);
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"fileOverlayRoot\":\"files\","
                + "\"linux\":{\"files\":{\"/proc/cpuinfo\":\"INLINE_CPU\"}}"
                + "}").getBytes(StandardCharsets.UTF_8));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(json).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertArrayEquals(new byte[0], readOpenBytes(emulator, blocks, "/proc/cpuinfo"));
            CapturedEvent ev = findLastEvent(sink.events, "linux_file", "open(\"/proc/cpuinfo\")");
            assertNotNull(ev);
            assertEquals("profile-file", ev.source);
            assertEquals("path=/proc/cpuinfo,bytes=0", String.valueOf(ev.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testWriteOpenDoesNotFallThroughToLinuxFiles() throws Exception {
        File dir = Files.createTempDirectory("traceai-write-nofall-").toFile();
        File overlayCpu = new File(dir, "files/proc/cpuinfo");
        assertTrue(overlayCpu.getParentFile().mkdirs());
        Files.write(overlayCpu.toPath(), "OVERLAY_CPU".getBytes(StandardCharsets.UTF_8));
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"fileOverlayRoot\":\"files\","
                + "\"linux\":{\"files\":{\"/proc/cpuinfo\":\"INLINE_CPU\"}}"
                + "}").getBytes(StandardCharsets.UTF_8));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(json).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            FileResult<AndroidFileIO> write = emulator.getFileSystem()
                    .open("/proc/cpuinfo", IOConstants.O_WRONLY);
            assertNotNull(write);
            assertFalse(write.isSuccess());
            assertEquals(UnixEmulator.EACCES, write.errno);
            assertTrue(findLastEvent(sink.events, "linux_file", "open(\"/proc/cpuinfo\")") == null);
            assertEquals("OVERLAY_CPU",
                    new String(readOpenBytes(emulator, blocks, "/proc/cpuinfo"), StandardCharsets.UTF_8));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testReadOnlyExtraFlagsStillServeOverlay() throws Exception {
        File sample = locateSampleProfile();
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(sample).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            byte[] expected = Files.readAllBytes(
                    new File(sample.getParentFile(), "files/proc/cpuinfo").toPath());
            FileResult<AndroidFileIO> append = emulator.getFileSystem()
                    .open("/proc/cpuinfo", IOConstants.O_RDONLY | IOConstants.O_APPEND);
            assertNotNull(append);
            assertTrue(append.isSuccess());
            assertArrayEquals(expected, readIoBytes(emulator, blocks, append.io, 8192));
            FileResult<AndroidFileIO> cloexec = emulator.getFileSystem()
                    .open("/proc/cpuinfo", IOConstants.O_RDONLY | IOConstants.O_CLOEXEC);
            assertTrue(cloexec.isSuccess());
            assertArrayEquals(expected, readIoBytes(emulator, blocks, cloexec.io, 8192));
            CapturedEvent ev = findLastEvent(sink.events, "linux_file", "open(\"/proc/cpuinfo\")");
            assertNotNull(ev);
            assertEquals("profile-file", ev.source);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testRdwrDirectoryIsEaccesNotEnotdir() throws Exception {
        File sample = locateSampleProfile();
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(sample).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open("/proc/cpuinfo", IOConstants.O_RDWR | IOConstants.O_DIRECTORY);
            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertEquals(UnixEmulator.EACCES, result.errno);
            assertTrue(findLastEvent(sink.events, "linux_file", "open(\"/proc/cpuinfo\")") == null);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testNullSetEnvironmentProfileDoesNotClearPrevious() throws Exception {
        File sample = locateSampleProfile();
        AndroidEmulator emulator = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit()
                    .setEnvironmentProfile(sample)
                    .setEnvironmentProfile((File) null)
                    .setEnvironmentProfile((String) null)
                    .build();
            TraceEnvironmentConfig attached = TraceEnvironmentConfig.get(emulator);
            assertTrue(attached.isProfileLoaded());
            assertEquals("TRACEAI_SERIAL_V1", attached.getAndroidBuildString("SERIAL"));
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testNumericOverlayAliasesSelfOnOpen() throws Exception {
        File dir = Files.createTempDirectory("traceai-open-pid-alias-").toFile();
        File numeric = new File(dir, "files/proc/12345/status");
        assertTrue(numeric.getParentFile().mkdirs());
        Files.write(numeric.toPath(), "ONLY_NUMERIC".getBytes(StandardCharsets.UTF_8));
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"process\":{\"pid\":12345},"
                + "\"fileOverlayRoot\":\"files\""
                + "}").getBytes(StandardCharsets.UTF_8));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(json).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("ONLY_NUMERIC",
                    new String(readOpenBytes(emulator, blocks, "/proc/self/status"),
                            StandardCharsets.UTF_8));
            assertEquals("ONLY_NUMERIC",
                    new String(readOpenBytes(emulator, blocks, "/proc/12345/status"),
                            StandardCharsets.UTF_8));
            FileResult<AndroidFileIO> prefix = emulator.getFileSystem()
                    .open("/proc/123456/status", IOConstants.O_RDONLY);
            if (prefix != null && prefix.isSuccess() && prefix.io instanceof ByteArrayFileIO) {
                byte[] leaked = readIoBytes(emulator, blocks, prefix.io, 256);
                assertFalse(new String(leaked, StandardCharsets.UTF_8).contains("ONLY_NUMERIC"));
            }
            CapturedEvent ev = findLastEvent(sink.events, "linux_file", "open(\"/proc/self/status\")");
            assertNotNull(ev);
            assertEquals("profile-file", ev.source);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testLinuxFilesPidAliasUnderProfileIsProfileJson() throws Exception {
        File dir = Files.createTempDirectory("traceai-linuxfiles-alias-").toFile();
        File overlayDir = new File(dir, "files/proc");
        assertTrue(overlayDir.mkdirs());
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"process\":{\"pid\":42},"
                + "\"fileOverlayRoot\":\"files\","
                + "\"linux\":{\"files\":{\"/proc/self/status\":\"INLINE_STATUS\"}}"
                + "}").getBytes(StandardCharsets.UTF_8));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(json).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertEquals("INLINE_STATUS",
                    new String(readOpenBytes(emulator, blocks, "/proc/42/status"),
                            StandardCharsets.UTF_8));
            CapturedEvent ev = findLastEvent(sink.events, "linux_file", "open(\"/proc/42/status\")");
            assertNotNull(ev);
            assertEquals("profile-json", ev.source);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    @Test
    public void testOverlayDoesNotServeProcFd() throws Exception {
        File dir = Files.createTempDirectory("traceai-profile-fd-").toFile();
        File fd0 = new File(dir, "files/proc/self/fd/0");
        assertTrue(fd0.getParentFile().mkdirs());
        Files.write(fd0.toPath(), "FD_LEAK".getBytes(StandardCharsets.UTF_8));
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"process\":{\"pid\":12345},"
                + "\"fileOverlayRoot\":\"files\""
                + "}").getBytes(StandardCharsets.UTF_8));
        AndroidEmulator emulator = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(json).build();
            FileResult<AndroidFileIO> selfFd = emulator.getFileSystem()
                    .open("/proc/self/fd/0", IOConstants.O_RDONLY);
            if (selfFd != null && selfFd.isSuccess() && selfFd.io instanceof ByteArrayFileIO) {
                fail("overlay must not serve /proc/self/fd/*");
            }
            FileResult<AndroidFileIO> pidFd = emulator.getFileSystem()
                    .open("/proc/12345/fd/0", IOConstants.O_RDONLY);
            if (pidFd != null && pidFd.isSuccess() && pidFd.io instanceof ByteArrayFileIO) {
                fail("overlay must not serve /proc/<pid>/fd/*");
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testOverlayDoesNotServeProcFdFile() throws Exception {
        File dir = Files.createTempDirectory("traceai-profile-fdfile-").toFile();
        File fdFile = new File(dir, "files/proc/self/fd");
        assertTrue(fdFile.getParentFile().mkdirs());
        Files.write(fdFile.toPath(), "FD_DIR_LEAK".getBytes(StandardCharsets.UTF_8));
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"process\":{\"pid\":12345},"
                + "\"fileOverlayRoot\":\"files\""
                + "}").getBytes(StandardCharsets.UTF_8));
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentProfile(json).build();
            assertNotOverlayContent(emulator, blocks, "/proc/self/fd", "FD_DIR_LEAK");
            assertNotOverlayContent(emulator, blocks, "/proc/12345/fd", "FD_DIR_LEAK");
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static String callBuildGetSerial(VM vm, AbstractJni jni) {
        vm.setJni(jni);
        BaseVM baseVM = (BaseVM) vm;
        DvmClass dvmClass = vm.resolveClass("android/os/Build");
        DvmMethod method = new DvmMethod(dvmClass, "getSerial", "()Ljava/lang/String;", true);
        StringObject value = (StringObject) jni.callStaticObjectMethod(baseVM, dvmClass,
                method.getSignature(), new EmptyVarArg(baseVM, method));
        return value.getValue();
    }

    private static String callGlesGetString(VM vm, AbstractJni jni, int name) {
        vm.setJni(jni);
        BaseVM baseVM = (BaseVM) vm;
        DvmClass dvmClass = vm.resolveClass("android/opengl/GLES10");
        DvmMethod method = new DvmMethod(dvmClass, "glGetString", "(I)Ljava/lang/String;", true);
        StringObject value = (StringObject) jni.callStaticObjectMethod(baseVM, dvmClass,
                method.getSignature(), new IntVarArg(baseVM, method, name));
        return value.getValue();
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

    private static byte[] readOpenBytes(AndroidEmulator emulator, List<MemoryBlock> blocks, String path)
            throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        assertNotNull(result);
        assertTrue("open failed for " + path, result.isSuccess());
        assertTrue(result.io instanceof ByteArrayFileIO);
        return readIoBytes(emulator, blocks, result.io, 8192);
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

    private static void assertNotOverlayHit(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                            String path, byte[] overlayBytes) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            byte[] got = readIoBytes(emulator, blocks, result.io, 8192);
            assertFalse("collapsed path must not hit overlay: " + path,
                    java.util.Arrays.equals(overlayBytes, got));
        }
    }

    private static void assertNotOverlayContent(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                                String path, String leaked) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            byte[] got = readIoBytes(emulator, blocks, result.io, 256);
            assertFalse(new String(got, StandardCharsets.UTF_8).contains(leaked));
        }
    }

    private static void assertSampleFileOpen(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                             File filesRoot, String guestPath) throws Exception {
        String relative = guestPath.startsWith("/proc/12345/")
                ? "proc/self/" + guestPath.substring("/proc/12345/".length())
                : guestPath.substring(1);
        File onDisk = new File(filesRoot, relative.replace('/', File.separatorChar));
        byte[] expected = Files.readAllBytes(onDisk.toPath());
        assertArrayEquals(expected, readOpenBytes(emulator, blocks, guestPath));
    }

    private static void restoreProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    private static File locateSampleProfile() {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++) {
            File candidate = new File(dir, "profiles/pixel6-analysis/device-fingerprint.json");
            if (candidate.isFile()) {
                return candidate;
            }
            dir = dir.getParentFile();
        }
        fail("profiles/pixel6-analysis/device-fingerprint.json not found from "
                + System.getProperty("user.dir"));
        return null;
    }

    private static final class EmptyVarArg extends VarArg {
        EmptyVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class IntVarArg extends VarArg {
        IntVarArg(BaseVM vm, DvmMethod method, int value) {
            super(vm, method);
            args.add(Integer.valueOf(value));
        }
    }

    private static final class CapturedEvent {
        final String kind;
        final String api;
        final Object value;
        final String source;

        CapturedEvent(String kind, String api, Object value, String source) {
            this.kind = kind;
            this.api = api;
            this.value = value;
            this.source = source;
        }
    }

    private static final class CapturingSink implements TraceEnvironmentEventSink {
        final List<CapturedEvent> events = new ArrayList<CapturedEvent>();

        @Override
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source));
        }
    }
}

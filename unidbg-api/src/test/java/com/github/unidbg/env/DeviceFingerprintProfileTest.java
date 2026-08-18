package com.github.unidbg.env;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DeviceFingerprintProfileTest {

    @Test
    public void testExistingConfigParseStillWorksWithoutProfile() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":7,\"processName\":\"com.demo.app\"}}");
        assertFalse(config.isProfileLoaded());
        assertNull(config.getProfileFileOverlay());
        assertTrue(config.getProfileReservedFields().isEmpty());
        assertEquals(7, config.getPid(-1));
        assertNull(config.readProfileOverlayFile("/proc/cpuinfo"));
    }

    @Test
    public void testSchemaVersionRequired() {
        try {
            DeviceFingerprintProfile.parse("{\"process\":{\"pid\":1}}", null);
            fail("expected schemaVersion failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage().contains("schemaVersion"));
        }
    }

    @Test
    public void testReservedFieldsDoNotFailParse() {
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.parse("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"profileName\":\"unit\","
                + "\"android\":{"
                + "\"sensors\":{\"types\":[1],\"samples\":{\"1\":[0.0,0.0,9.81]}},"
                + "\"telephony\":{\"phoneCount\":1,\"slots\":[{\"slotIndex\":0,\"imei\":\"1\","
                + "\"deviceId\":\"1\",\"subscriberId\":\"1\",\"simSerialNumber\":\"1\",\"simState\":5}],"
                + "\"cellInfo\":[{\"type\":\"lte\",\"mcc\":\"460\",\"mnc\":\"00\",\"ci\":1}]},"
                + "\"tee\":{\"available\":true,\"securityLevel\":1}"
                + "},"
                + "\"network\":{\"capabilities\":{\"internet\":true}},"
                + "\"backendStatus\":{\"android.sensors.samples\":\"reserved\"}"
                + "}", null);
        TraceEnvironmentConfig config = profile.getEnvironmentConfig();
        assertTrue(config.isProfileLoaded());
        assertEquals("unit", config.getProfileName());
        assertTrue(config.isAndroidSensorsConfigured());
        assertTrue(config.isAndroidSensorSamplesConfigured());
        assertNotNull(config.getAndroidSensorSample(1));
        assertTrue(config.isAndroidTeeConfigured());
        assertEquals("TRUSTED_ENVIRONMENT", config.getTeeSecurityLevel());
        assertTrue(profile.getEnvironmentConfig().isAndroidCellInfoConfigured());
        assertEquals(1, profile.getEnvironmentConfig().getAndroidCellInfo().size());
        assertEquals("lte", profile.getEnvironmentConfig().getAndroidCellInfo().get(0).getType());
        assertTrue(containsFragment(profile.getReservedWarnings(), "network.capabilities"));
        assertNotNull(profile.getReservedField("network.capabilities"));
        assertNotNull(profile.getReservedField("backendStatus"));
    }

    @Test
    public void testSampleProfileConvertsImplementedFields() throws Exception {
        File sample = locateSampleProfile();
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.load(sample);
        TraceEnvironmentConfig config = profile.getEnvironmentConfig();
        assertEquals("pixel6-analysis", profile.getProfileName());
        assertTrue(config.isProfileLoaded());
        assertEquals(12345, config.getPid(-1));
        assertEquals("com.demo.app", config.getProcessName(null));
        assertEquals("TRACEAI_PIXEL_6", config.getAndroidBuildString("MODEL"));
        assertEquals("TRACEAI_SERIAL_V1", config.getAndroidBuildString("SERIAL"));
        assertEquals(Integer.valueOf(31), config.getAndroidBuildInt("VERSION.SDK_INT"));
        assertTrue(config.isAndroidSensorsConfigured());
        assertEquals("TRACEAI_ACCELEROMETER", config.getAndroidSensorName(1));
        assertTrue(config.isGraphicsConfigured());
        assertEquals("TRACEAI_GPU_VENDOR", config.getGraphicsConfig().getVendor());
        assertTrue(config.isAndroidTeeConfigured());
        assertEquals("TRUSTED_ENVIRONMENT", config.getTeeSecurityLevel());
        assertTrue(config.isAndroidCellInfoConfigured());
        assertEquals("lte", config.getAndroidCellInfo().get(0).getType());
        assertTrue(containsFragment(profile.getReservedWarnings(), "backendStatus"));
        byte[] cpuinfo = config.readProfileOverlayFile("/proc/cpuinfo");
        assertNotNull(cpuinfo);
        assertEquals(new String(Files.readAllBytes(
                new File(sample.getParentFile(), "files/proc/cpuinfo").toPath()),
                StandardCharsets.UTF_8), new String(cpuinfo, StandardCharsets.UTF_8));
        byte[] statusSelf = config.readProfileOverlayFile("/proc/self/status");
        byte[] statusPid = config.readProfileOverlayFile("/proc/12345/status");
        assertNotNull(statusSelf);
        assertEquals(new String(statusSelf, StandardCharsets.UTF_8),
                new String(statusPid, StandardCharsets.UTF_8));
        assertNull(config.readProfileOverlayFile("/proc/99999/status"));
        assertNull(config.readProfileOverlayFile("/proc/self/../cpuinfo"));
    }

    @Test
    public void testSampleProfileBatch1StableInputs() throws Exception {
        File sample = locateSampleProfile();
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.load(sample);
        TraceEnvironmentConfig config = profile.getEnvironmentConfig();

        assertEquals(12345, config.getPgid(-1));
        assertEquals(12345, config.getSid(-1));
        assertTrue(config.isSupplementaryGidsConfigured());
        assertEquals(Collections.singletonList(Integer.valueOf(10123)),
                config.getSupplementaryGids());
        assertArrayEquals(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 },
                config.getRandomBytes("stackGuardHex", 8));
        assertArrayEquals(new byte[] {
                0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77
        }, config.getRandomBytes("atRandomHex", 8));

        assertEquals("Linux", config.getUnameSysname(null));
        assertEquals("5.10.66-TRACEAI", config.getUnameRelease(null));
        assertEquals("aarch64", config.getUnameMachine(true, null));
        assertTrue(config.isLinuxCpuConfigured());
        assertEquals("0-7", config.getLinuxCpuConfig().getOnline());
        assertEquals(8, config.getLinuxCpuConfig().getConfiguredProcessorCount());
        assertEquals(8, config.getLinuxCpuConfig().getOnlineProcessorCount());
        assertTrue(config.isLinuxAuxvConfigured());
        assertEquals(3219913727L, config.getLinuxAuxvConfig().getHwcap64());
        assertEquals("aarch64", config.getLinuxAuxvConfig().getPlatform64());
        assertTrue(config.isLinuxEnvironConfigured());
        assertTrue(config.getLinuxEnviron().contains("ANDROID_DATA=/data"));
        assertTrue(config.isLinuxRlimitsNofileConfigured());
        assertEquals(32768L, config.getLinuxRlimitsConfig().getNofileSoft());
        assertTrue(config.isLinuxSysinfoConfigured());
        assertEquals(384221L, config.getLinuxSysinfoConfig().getUptime());
        assertEquals(1907344L, config.getLinuxSysinfoConfig().getTotalRam());
        assertEquals(524288L, config.getLinuxSysinfoConfig().getFreeRam());
        assertEquals(412, config.getLinuxSysinfoConfig().getProcs());
        assertEquals(4096L, config.getLinuxSysinfoConfig().getMemUnit());
        assertArrayEquals(new long[] {65536L, 58982L, 52428L},
                config.getLinuxSysinfoConfig().getLoads());
        assertTrue(config.isLinuxProcConfigured());
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                config.getLinuxProcConfig().getBootId());
        assertEquals("u:r:untrusted_app:s0:c512,c768",
                config.getLinuxProcConfig().getSelinuxContext());
        assertEquals(Collections.singletonList("com.demo.app"),
                config.getLinuxProcConfig().getCmdline());

        assertEquals("TRACEAI.PIXEL6.001", config.getAndroidBuildString("ID"));
        assertEquals("TRACEAI.PIXEL6.001", config.getAndroidBuildString("DISPLAY"));
        assertEquals(Long.valueOf(1640995200000L), config.getAndroidBuildTime());
        assertEquals(Arrays.asList("arm64-v8a", "armeabi-v7a"),
                config.getAndroidBuildSupportedAbis());
        assertEquals("TRACEAI_SERIAL_V1", config.getAndroidProperty("ro.serialno"));
        assertEquals("TRACEAI_SERIAL_V1", config.getAndroidProperty("ro.boot.serialno"));
        assertEquals("TRACEAI.PIXEL6.001", config.getAndroidProperty("ro.build.id"));
        assertEquals("/data/app/~~fixed/com.demo.app-fixed/lib/arm64",
                config.getNativeLibraryDir(true, null));

        assertTrue(config.isAndroidIdentifiersConfigured());
        assertEquals("a1b2c3d4e5f60718", config.getAndroidIdentifiersConfig().getAndroidId());
        assertEquals("00000000-0000-4000-8000-00000000a001",
                config.getAndroidIdentifiersConfig().getAdvertisingId());
        assertFalse(config.getAndroidIdentifiersConfig().isLimitAdTracking());
        assertEquals("a1b2c3d4e5f60718",
                config.getAndroidSettingString("secure", "android_id"));
        assertEquals("0", config.getAndroidSettingString("global", "adb_enabled"));
        assertTrue(config.isAndroidRuntimeAvailableProcessorsConfigured());
        assertEquals(8, config.getAndroidRuntimeAvailableProcessors());
        assertEquals("/data", config.getAndroidRuntimeEnvironmentVariables().get("ANDROID_DATA"));
        assertEquals("/data/local/tmp",
                config.getAndroidRuntimeEnvironmentVariables().get("TMPDIR"));
        assertEquals("PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin",
                findEnvironEntry(config.getLinuxEnviron(), "PATH"));
        assertEquals("/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin",
                config.getAndroidRuntimeEnvironmentVariables().get("PATH"));
        assertTrue(config.getLinuxEnviron().contains("TMPDIR=/data/local/tmp"));

        TraceEnvironmentConfig.PackageConfig pkg = config.getAndroidPackage("com.demo.app");
        assertNotNull(pkg);
        assertEquals(Integer.valueOf(100), pkg.getVersionCode());
        assertEquals(Collections.singletonList("5452414345414901"), pkg.getSignatureHexes());
        assertNotNull(config.getAndroidFeature("android.hardware.wifi"));
        assertTrue(config.isAndroidConfigurationConfigured());
        assertEquals(1, config.getAndroidConfigurationConfig().getOrientation());
        assertEquals(420, config.getAndroidConfigurationConfig().getDensityDpi());
        assertEquals(411, config.getAndroidConfigurationConfig().getScreenWidthDp());
        assertEquals(914, config.getAndroidConfigurationConfig().getScreenHeightDp());
        assertEquals(411, config.getAndroidConfigurationConfig().getSmallestScreenWidthDp());
        assertEquals(1080, config.getAndroidDisplayConfig().getWidthPixels());
        assertEquals(2400, config.getAndroidDisplayConfig().getHeightPixels());
        assertEquals(420, config.getAndroidDisplayConfig().getDensityDpi());
        assertEquals("Android", config.getGraphicsConfig().getEglVendor());
        assertEquals(config.getAndroidBuildString("FINGERPRINT"),
                config.getAndroidProperty("ro.build.fingerprint"));
        assertEquals("arm64-v8a", config.getAndroidBuildSupportedAbis().get(0));
        assertEquals(config.getAndroidBuildString("CPU_ABI"),
                config.getAndroidProperty("ro.product.cpu.abi"));

        assertTrue(config.isFilesystemStatConfigured());
        TraceEnvironmentConfig.FileStatConfig apkStat = config.getFilesystemStat(
                "/data/app/~~fixed/com.demo.app-fixed/base.apk");
        assertNotNull(apkStat);
        assertEquals(Long.valueOf(4096000L), apkStat.getSize());
        TraceEnvironmentConfig.FileStatConfig dataStat = config.getFilesystemStat(
                "/data/user/0/com.demo.app");
        assertNotNull(dataStat);
        assertEquals(Long.valueOf(10123L), dataStat.getUid());
        TraceEnvironmentConfig.FileStatConfig extStat = config.getFilesystemStat(
                "/storage/emulated/0");
        assertNotNull(extStat);
        assertEquals(Long.valueOf(16877L), extStat.getMode());
        assertEquals(3, config.getFilesystemStats().size());

        assertTrue(config.isFilesystemMountsConfigured());
        assertEquals(3, config.getFilesystemMounts().size());
        assertEquals("/system", config.getFilesystemMounts().get(0).getTarget());
        assertEquals("ext4", config.getFilesystemMounts().get(0).getFileSystemType());
        assertEquals("/data", config.getFilesystemMounts().get(1).getTarget());
        assertEquals("f2fs", config.getFilesystemMounts().get(1).getFileSystemType());
        assertEquals("/storage/emulated/0", config.getFilesystemMounts().get(2).getTarget());
        assertEquals("fuse", config.getFilesystemMounts().get(2).getFileSystemType());

        TraceEnvironmentConfig.FileStatFsConfig dataFs = config.getFilesystemStatFsExact("/data");
        assertNotNull(dataFs);
        assertEquals(Integer.valueOf(4096), dataFs.getBlockSize());
        assertEquals(Long.valueOf(15000000L), dataFs.getBlocks());

        File filesRoot = new File(sample.getParentFile(), "files");
        assertSampleOverlayMatches(config, filesRoot, "/proc/cpuinfo");
        assertSampleOverlayMatches(config, filesRoot, "/proc/self/status");
        assertSampleOverlayMatches(config, filesRoot, "/sys/class/net/wlan0/address");
        assertSampleOverlayMatches(config, filesRoot, "/proc/meminfo");
        assertSampleOverlayMatches(config, filesRoot, "/proc/version");
        assertSampleOverlayMatches(config, filesRoot, "/proc/self/cmdline");
        assertSampleOverlayMatches(config, filesRoot, "/proc/self/cgroup");
        assertNull(config.readProfileOverlayFile("/proc/self/maps"));
        assertSampleOverlayMatches(config, filesRoot, "/proc/sys/kernel/random/boot_id");
        assertSampleOverlayMatches(config, filesRoot, "/sys/devices/system/cpu/online");
        assertSampleOverlayMatches(config, filesRoot, "/sys/devices/system/cpu/present");
        assertSampleOverlayMatches(config, filesRoot, "/sys/devices/system/cpu/possible");
        assertSampleOverlayMatches(config, filesRoot, "/sys/class/net/wlan0/mtu");
        assertSampleOverlayMatches(config, filesRoot, "/sys/class/net/wlan0/operstate");
        assertSampleOverlayMatches(config, filesRoot, "/sys/class/net/wlan0/carrier");
        assertArrayEquals(config.readProfileOverlayFile("/proc/self/cmdline"),
                config.readProfileOverlayFile("/proc/12345/cmdline"));
        byte[] cpuOnline = config.readProfileOverlayFile("/sys/devices/system/cpu/online");
        assertArrayEquals("0-7\n".getBytes(StandardCharsets.UTF_8), cpuOnline);
        byte[] bootId = config.readProfileOverlayFile("/proc/sys/kernel/random/boot_id");
        assertArrayEquals((config.getLinuxProcConfig().getBootId() + "\n")
                .getBytes(StandardCharsets.UTF_8), bootId);
        assertEquals(37, bootId.length);
        byte[] cmdline = config.readProfileOverlayFile("/proc/self/cmdline");
        assertArrayEquals(new byte[] {
                'c', 'o', 'm', '.', 'd', 'e', 'm', 'o', '.', 'a', 'p', 'p', 0
        }, cmdline);
        assertTrue(new String(config.readProfileOverlayFile("/proc/version"),
                StandardCharsets.UTF_8).contains(config.getUnameRelease(null)));
    }

    @Test
    public void testRejectsOverlayEscape() {
        File sample = new File("profiles/pixel6-analysis/device-fingerprint.json");
        assertOverlayRootRejected("../secret", sample);
        assertOverlayRootRejected(".", sample);
        assertOverlayRootRejected("files/../secret", sample);
        assertOverlayRootRejected("files\\..\\secret", sample);
        assertOverlayRootRejected("C:/windows", sample);
        try {
            DeviceFingerprintProfile.splitOverlayRoot("files/foo\0bar");
            fail("expected NUL overlay root failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("fileOverlayRoot"));
        }
        assertFalse(ProfileFileOverlay.isSafeGuestPathChars("/proc/cpuinfo\0x"));
        assertFalse(ProfileFileOverlay.isSafePathComponent(".."));
        assertNull(ProfileFileOverlay.normalizeGuestPath("/proc/self/../cpuinfo"));
        assertNull(ProfileFileOverlay.normalizeGuestPath("/proc/cpuinfo\\x"));
        assertNull(ProfileFileOverlay.normalizeGuestPath("/"));
        assertNull(ProfileFileOverlay.normalizeGuestPath("/proc//cpuinfo"));
        assertNull(ProfileFileOverlay.normalizeGuestPath("/proc/cpuinfo/"));
        assertNull(ProfileFileOverlay.normalizeGuestPath("/proc/./cpuinfo"));
        assertNull(ProfileFileOverlay.normalizeGuestPath("/proc/cpu info"));
        assertEquals("/proc/cpuinfo", ProfileFileOverlay.normalizeGuestPath("/proc/cpuinfo"));
        assertOverlayRootRejected("files/", sample);
        assertOverlayRootRejected("files//sub", sample);
        assertOverlayRootRejected("files/./sub", sample);
        assertOverlayRootRejected("files/my dir", sample);
        List<String> filesOnly = DeviceFingerprintProfile.splitOverlayRoot("files");
        assertEquals(1, filesOnly.size());
        assertEquals("files", filesOnly.get(0));
        List<String> nested = DeviceFingerprintProfile.splitOverlayRoot("files/proc");
        assertEquals(2, nested.size());
        assertEquals("proc", nested.get(1));
    }

    @Test
    public void testOverlayRejectsUnsafeGuestPaths() throws Exception {
        File sample = locateSampleProfile();
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.load(sample);
        TraceEnvironmentConfig config = profile.getEnvironmentConfig();
        assertNotNull(config.readProfileOverlayFile("/proc/cpuinfo"));
        assertNull(config.readProfileOverlayFile("/proc/self/../cpuinfo"));
        assertNull(config.readProfileOverlayFile("/proc/cpuinfo\0ignored"));
        assertNull(config.readProfileOverlayFile("/proc/cpuinfo\\..\\..\\secret"));
        assertNull(config.readProfileOverlayFile("proc/cpuinfo"));
        assertNull(config.readProfileOverlayFile("/c:windows/win.ini"));
        assertNull(config.readProfileOverlayFile("/proc/cpuinfo:ads"));
        assertNull(config.readProfileOverlayFile("/"));
        assertNull(config.readProfileOverlayFile("/proc/self/fd/0"));
        assertNull(config.readProfileOverlayFile("/proc/12345/fd/1"));
        assertTrue(ProfileFileOverlay.isBlockedProcFd("/proc/self/fd"));
        assertTrue(ProfileFileOverlay.isBlockedProcFd("/proc/12345/fd/0"));
        assertFalse(ProfileFileOverlay.isBlockedProcFd("/proc/self/status"));
    }

    @Test
    public void testConsistencyWarningDoesNotRewriteValues() {
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.parse("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"process\":{\"processName\":\"com.other.app\"},"
                + "\"android\":{\"packageName\":\"com.demo.app\","
                + "\"build\":{\"MODEL\":\"A\"},"
                + "\"properties\":{\"ro.product.model\":\"B\"}}"
                + "}", null);
        assertEquals("com.other.app", profile.getEnvironmentConfig().getProcessName(null));
        assertEquals("A", profile.getEnvironmentConfig().getAndroidBuildString("MODEL"));
        assertTrue(containsFragment(profile.getConsistencyWarnings(), "process.processName"));
        assertTrue(containsFragment(profile.getConsistencyWarnings(), "MODEL"));
    }

    @Test
    public void testSchemaVersionEmptyOrWrongRejected() {
        assertSchemaRejected("{\"schemaVersion\":\"\"}");
        assertSchemaRejected("{\"schemaVersion\":\"traceai-device-fingerprint/v2\"}");
        assertSchemaRejected("{\"schemaVersion\":\"TRACEAI-DEVICE-FINGERPRINT/V1\"}");
        assertSchemaRejected("{\"schemaVersion\":1}");
        try {
            DeviceFingerprintProfile.parse("", null);
            fail("expected empty json failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage().contains("empty"));
        }
        try {
            DeviceFingerprintProfile.parse("   ", null);
            fail("expected blank json failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage().contains("empty"));
        }
    }

    @Test
    public void testEmptyProfileNameRejected() {
        try {
            DeviceFingerprintProfile.parse("{"
                    + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                    + "\"profileName\":\"\"}", null);
            fail("expected empty profileName failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage().contains("profileName"));
        }
        try {
            DeviceFingerprintProfile.parse("{"
                    + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                    + "\"profileName\":1}", null);
            fail("expected non-string profileName failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage().contains("profileName"));
        }
    }

    @Test
    public void testTeeSecurityLevelIntegerAndStringMapping() {
        assertEquals("SOFTWARE", parseMinimalProfile(
                "\"android\":{\"tee\":{\"securityLevel\":0}}")
                .getEnvironmentConfig().getTeeSecurityLevel());
        assertEquals("TRUSTED_ENVIRONMENT", parseMinimalProfile(
                "\"android\":{\"tee\":{\"securityLevel\":1}}")
                .getEnvironmentConfig().getTeeSecurityLevel());
        assertEquals("STRONGBOX", parseMinimalProfile(
                "\"android\":{\"tee\":{\"securityLevel\":2}}")
                .getEnvironmentConfig().getTeeSecurityLevel());
        DeviceFingerprintProfile asString = parseMinimalProfile(
                "\"android\":{\"tee\":{\"securityLevel\":\"SOFTWARE\"}}");
        assertEquals("SOFTWARE", asString.getEnvironmentConfig().getTeeSecurityLevel());
        assertTrue(asString.getEnvironmentConfig().isTeeSecurityLevelConfigured());
    }

    @Test
    public void testUnsupportedTeeSecurityLevelIsStrippedNotFatal() {
        DeviceFingerprintProfile unsupported = parseMinimalProfile(
                "\"android\":{\"tee\":{\"available\":true,\"securityLevel\":99}}");
        TraceEnvironmentConfig config = unsupported.getEnvironmentConfig();
        assertTrue(config.isAndroidTeeConfigured());
        assertTrue(config.getTeeAvailable(false));
        assertFalse(config.isTeeSecurityLevelConfigured());
        assertNull(config.getTeeSecurityLevel());
        assertTrue(containsFragment(unsupported.getReservedWarnings(),
                "android.tee.securityLevel"));
        assertNull(unsupported.getReservedField("android.tee.securityLevel"));

        DeviceFingerprintProfile badType = parseMinimalProfile(
                "\"android\":{\"tee\":{\"securityLevel\":true}}");
        assertFalse(badType.getEnvironmentConfig().isTeeSecurityLevelConfigured());
        assertTrue(containsFragment(badType.getReservedWarnings(),
                "android.tee.securityLevel"));
    }

    @Test
    public void testGraphicsNativeReservedDoesNotFailParse() {
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.parse("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"graphics\":{\"vendor\":\"TRACEAI_GPU_VENDOR\","
                + "\"native\":{\"glGetString\":\"marker\"}}"
                + "}", null);
        TraceEnvironmentConfig config = profile.getEnvironmentConfig();
        assertTrue(config.isGraphicsConfigured());
        assertEquals("TRACEAI_GPU_VENDOR", config.getGraphicsConfig().getVendor());
        assertNotNull(profile.getReservedField("graphics.native"));
        assertTrue(containsFragment(profile.getReservedWarnings(), "graphics.native"));
        assertSameReservedOnConfig(profile, "graphics.native");
    }

    @Test
    public void testParseWithoutProfileFileHasNoOverlay() {
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.parse("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"profileName\":\"memory-only\","
                + "\"fileOverlayRoot\":\"files\","
                + "\"process\":{\"pid\":12345}"
                + "}", null);
        assertEquals("memory-only", profile.getProfileName());
        assertNull(profile.getProfileFile());
        assertNull(profile.getOverlayRoot());
        TraceEnvironmentConfig config = profile.getEnvironmentConfig();
        assertTrue(config.isProfileLoaded());
        assertEquals("memory-only", config.getProfileName());
        assertNull(config.getProfileFileOverlay());
        assertNull(config.readProfileOverlayFile("/proc/cpuinfo"));
        assertEquals(12345, config.getPid(-1));
    }

    @Test
    public void testOmittedOverlayRootDefaultsToFiles() throws Exception {
        File dir = Files.createTempDirectory("traceai-default-overlay-").toFile();
        File overlayCpu = new File(dir, "files/proc/cpuinfo");
        assertTrue(overlayCpu.getParentFile().mkdirs());
        Files.write(overlayCpu.toPath(), "DEFAULT_FILES_ROOT".getBytes(StandardCharsets.UTF_8));
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"profileName\":\"default-overlay\""
                + "}").getBytes(StandardCharsets.UTF_8));

        DeviceFingerprintProfile profile = DeviceFingerprintProfile.load(json);
        assertEquals(new File(dir, "files"), profile.getOverlayRoot());
        byte[] cpuinfo = profile.getEnvironmentConfig().readProfileOverlayFile("/proc/cpuinfo");
        assertNotNull(cpuinfo);
        assertEquals("DEFAULT_FILES_ROOT", new String(cpuinfo, StandardCharsets.UTF_8));
    }

    @Test
    public void testInvalidImplementedFieldStillFailsParse() {
        try {
            DeviceFingerprintProfile.parse("{"
                    + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                    + "\"android\":{\"sensors\":{\"notARealKey\":true}}"
                    + "}", null);
            fail("expected implemented-field validation failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage().contains("android.sensors.notARealKey"));
        }
        try {
            DeviceFingerprintProfile.parse("{"
                    + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                    + "\"graphics\":{\"native\":{\"ok\":true},\"notARealKey\":\"x\"}"
                    + "}", null);
            fail("expected graphics allowed-key failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage().contains("graphics.notARealKey")
                            || expected.getMessage().contains("not an allowed key"));
        }
    }

    @Test
    public void testSampleSysWlanAddressOverlayBytes() throws Exception {
        File sample = locateSampleProfile();
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.load(sample);
        File onDisk = new File(sample.getParentFile(), "files/sys/class/net/wlan0/address");
        byte[] expected = Files.readAllBytes(onDisk.toPath());
        byte[] overlay = profile.getEnvironmentConfig()
                .readProfileOverlayFile("/sys/class/net/wlan0/address");
        assertNotNull(overlay);
        assertArrayEquals(expected, overlay);
        assertNull(profile.getReservedField("graphics.native"));
        assertNotNull(profile.getReservedField("backendStatus"));
        assertTrue(containsFragment(profile.getReservedWarnings(), "backendStatus"));
    }

    @Test
    public void testMoreConsistencyWarningsDoNotRewrite() {
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.parse("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"android\":{"
                + "\"build\":{\"HARDWARE\":\"raven\",\"VERSION.SDK_INT\":31},"
                + "\"properties\":{\"ro.hardware\":\"oriole\","
                + "\"ro.build.version.sdk\":\"30\",\"persist.sys.timezone\":\"UTC\"},"
                + "\"locale\":{\"timezoneId\":\"Asia/Shanghai\"}"
                + "}}", null);
        TraceEnvironmentConfig config = profile.getEnvironmentConfig();
        assertEquals("raven", config.getAndroidBuildString("HARDWARE"));
        assertEquals(Integer.valueOf(31), config.getAndroidBuildInt("VERSION.SDK_INT"));
        assertEquals("oriole", config.getAndroidProperty("ro.hardware"));
        assertEquals("30", config.getAndroidProperty("ro.build.version.sdk"));
        assertEquals("Asia/Shanghai", config.getAndroidLocaleConfig().getTimezoneId());
        assertTrue(containsFragment(profile.getConsistencyWarnings(), "HARDWARE"));
        assertTrue(containsFragment(profile.getConsistencyWarnings(), "VERSION.SDK_INT"));
        assertTrue(containsFragment(profile.getConsistencyWarnings(), "timezoneId"));
    }

    @Test
    public void testBinaryOverlayExactBytesAndMissingPath() throws Exception {
        File dir = Files.createTempDirectory("traceai-binary-overlay-").toFile();
        File overlayBin = new File(dir, "files/proc/self/auxv");
        assertTrue(overlayBin.getParentFile().mkdirs());
        byte[] binary = new byte[] { 0x00, (byte) 0xFF, 0x0A, 0x0D, 0x7F, 'E', 'L', 'F' };
        Files.write(overlayBin.toPath(), binary);
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"process\":{\"pid\":77},"
                + "\"fileOverlayRoot\":\"files\""
                + "}").getBytes(StandardCharsets.UTF_8));

        DeviceFingerprintProfile profile = DeviceFingerprintProfile.load(json);
        TraceEnvironmentConfig config = profile.getEnvironmentConfig();
        assertArrayEquals(binary, config.readProfileOverlayFile("/proc/self/auxv"));
        assertArrayEquals(binary, config.readProfileOverlayFile("/proc/77/auxv"));
        assertNull(config.readProfileOverlayFile("/proc/cpuinfo"));
        assertNull(config.readProfileOverlayFile("/proc/self/maps"));
    }

    @Test
    public void testSymlinkOverlayFileIsNotFollowed() throws Exception {
        File dir = Files.createTempDirectory("traceai-symlink-overlay-").toFile();
        File files = new File(dir, "files");
        File proc = new File(files, "proc");
        assertTrue(proc.mkdirs());
        File secret = new File(dir, "outside.bin");
        Files.write(secret.toPath(), "SECRET_OUTSIDE".getBytes(StandardCharsets.UTF_8));
        File link = new File(proc, "cpuinfo");
        if (!tryCreateSymbolicLink(link, secret)) {
            return;
        }
        ProfileFileOverlay overlay = new ProfileFileOverlay(files, dir, 1);
        assertNull(overlay.read("/proc/cpuinfo"));
    }

    @Test
    public void testOverlayRootThatIsAFileRejected() throws Exception {
        File dir = Files.createTempDirectory("traceai-overlay-file-").toFile();
        File notDir = new File(dir, "files");
        Files.write(notDir.toPath(), "not-a-directory".getBytes(StandardCharsets.UTF_8));
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\""
                + "}").getBytes(StandardCharsets.UTF_8));
        try {
            DeviceFingerprintProfile.load(json);
            fail("expected fileOverlayRoot directory failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage().contains("fileOverlayRoot"));
        }
    }

    @Test
    public void testEmptyFileOverlayRootRejected() {
        File sample = locateSampleProfile();
        try {
            DeviceFingerprintProfile.parse("{"
                    + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                    + "\"fileOverlayRoot\":\"\"}", sample);
            fail("expected empty fileOverlayRoot failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage() != null
                            && expected.getMessage().contains("fileOverlayRoot"));
        }
        try {
            DeviceFingerprintProfile.parse("{"
                    + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                    + "\"fileOverlayRoot\":1}", sample);
            fail("expected non-string fileOverlayRoot failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage() != null
                            && expected.getMessage().contains("fileOverlayRoot"));
        }
    }

    @Test
    public void testFromSystemPropertyUnsetIsNull() {
        String previous = System.getProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY);
        try {
            System.clearProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY);
            assertNull(DeviceFingerprintProfile.fromSystemProperty());
        } finally {
            if (previous == null) {
                System.clearProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY);
            } else {
                System.setProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY, previous);
            }
        }
    }

    @Test
    public void testReservedFieldPayloadPreservedExactly() {
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.parse("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"android\":{"
                + "\"sensors\":{\"types\":[1],\"samples\":{\"1\":[0.0,0.0,9.81]}},"
                + "\"telephony\":{\"phoneCount\":1,\"slots\":[{\"slotIndex\":0,\"imei\":\"1\","
                + "\"deviceId\":\"1\",\"subscriberId\":\"1\",\"simSerialNumber\":\"1\",\"simState\":5}],"
                + "\"cellInfo\":[{\"type\":\"lte\",\"ci\":42}]}"
                + "},"
                + "\"network\":{\"capabilities\":{\"internet\":true,\"vpn\":false}},"
                + "\"backendStatus\":{\"android.sensors.samples\":\"reserved\"}"
                + "}", null);
        assertTrue(profile.getEnvironmentConfig().isAndroidSensorSamplesConfigured());
        assertEquals(3, profile.getEnvironmentConfig().getAndroidSensorSample(1).length);
        assertTrue(profile.getEnvironmentConfig().isAndroidCellInfoConfigured());
        assertEquals(42, profile.getEnvironmentConfig().getAndroidCellInfo().get(0).getCi());
        assertTrue(String.valueOf(profile.getReservedField("network.capabilities")).contains("internet"));
        assertTrue(String.valueOf(profile.getReservedField("backendStatus"))
                .contains("android.sensors.samples"));
        assertTrue(profile.getEnvironmentConfig().isAndroidSensorsConfigured());
        assertNull(profile.getEnvironmentConfig().getAndroidSensorName(1));
        assertNull(profile.getReservedField("graphics.native"));
    }

    @Test
    public void testExplicitNullReservedFieldsDoNotFailParse() {
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.parse("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[{"
                + "\"slotIndex\":0,\"imei\":\"1\",\"deviceId\":\"1\",\"subscriberId\":\"1\","
                + "\"simSerialNumber\":\"1\",\"simState\":5}],\"cellInfo\":[]}},"
                + "\"graphics\":{\"native\":null}"
                + "}", null);
        assertTrue(profile.getEnvironmentConfig().isAndroidCellInfoConfigured());
        assertTrue(profile.getEnvironmentConfig().getAndroidCellInfo().isEmpty());
        assertTrue(profile.getReservedFields().containsKey("graphics.native"));
        assertNull(profile.getReservedField("graphics.native"));
        assertTrue(profile.getEnvironmentConfig().isGraphicsConfigured());
        assertNull(profile.getEnvironmentConfig().getGraphicsConfig().getVendor());
    }

    @Test
    public void testReservedAndConsistencyCollectionsAreUnmodifiable() {
        DeviceFingerprintProfile profile = parseMinimalProfile(
                "\"process\":{\"processName\":\"com.other.app\"},"
                        + "\"android\":{\"packageName\":\"com.demo.app\"},"
                        + "\"backendStatus\":{\"x\":\"reserved\"}");
        try {
            profile.getReservedFields().put("x", "y");
            fail("reserved fields must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            profile.getReservedWarnings().add("nope");
            fail("reserved warnings must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            profile.getConsistencyWarnings().add("nope");
            fail("consistency warnings must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            profile.getEnvironmentConfig().getProfileReservedFields().put("x", "y");
            fail("config reserved fields must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    public void testLoadPathNullAndMissingFile() throws Exception {
        assertEquals("traceai-device-fingerprint/v1", DeviceFingerprintProfile.SCHEMA_VERSION);
        assertEquals("unidbg.env.profile", DeviceFingerprintProfile.SYSTEM_PROPERTY);
        File sample = locateSampleProfile();
        DeviceFingerprintProfile viaPath = DeviceFingerprintProfile.load(sample.getAbsolutePath());
        assertEquals("pixel6-analysis", viaPath.getProfileName());
        try {
            DeviceFingerprintProfile.load((File) null);
            fail("expected null profile file failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage().contains("null"));
        }
        File missing = new File(Files.createTempDirectory("traceai-missing-profile-").toFile(),
                "missing.json");
        try {
            DeviceFingerprintProfile.load(missing);
            fail("expected missing profile load failure");
        } catch (IllegalStateException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage().contains("load device fingerprint profile failed"));
        }
        try {
            DeviceFingerprintProfile.parse(null, null);
            fail("expected null json failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage().contains("empty"));
        }
    }

    @Test
    public void testFromSystemPropertyLoadsSample() {
        File sample = locateSampleProfile();
        String previous = System.getProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY);
        try {
            System.setProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY, sample.getAbsolutePath());
            DeviceFingerprintProfile profile = DeviceFingerprintProfile.fromSystemProperty();
            assertNotNull(profile);
            assertEquals("pixel6-analysis", profile.getProfileName());
            assertEquals(12345, profile.getEnvironmentConfig().getPid(-1));
        } finally {
            if (previous == null) {
                System.clearProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY);
            } else {
                System.setProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY, previous);
            }
        }
    }

    @Test
    public void testInvalidTeeStringStillFailsParse() {
        try {
            parseMinimalProfile("\"android\":{\"tee\":{\"securityLevel\":\"NOT_AN_ENUM\"}}");
            fail("expected invalid tee string to fail existing config parse");
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage().contains("securityLevel"));
        }
        DeviceFingerprintProfile fractional = parseMinimalProfile(
                "\"android\":{\"tee\":{\"securityLevel\":1.5}}");
        assertFalse(fractional.getEnvironmentConfig().isTeeSecurityLevelConfigured());
        assertTrue(containsFragment(fractional.getReservedWarnings(),
                "android.tee.securityLevel"));
    }

    @Test
    public void testOmittedProfileNameIsNullButLoaded() {
        DeviceFingerprintProfile profile = parseMinimalProfile(null);
        assertNull(profile.getProfileName());
        assertTrue(profile.getEnvironmentConfig().isProfileLoaded());
        assertNull(profile.getEnvironmentConfig().getProfileName());
        assertTrue(profile.getReservedFields().isEmpty());
        assertTrue(profile.getReservedWarnings().isEmpty());
        assertTrue(profile.getConsistencyWarnings().isEmpty());
    }

    @Test
    public void testProfileFileWithoutParentHasNoOverlay() {
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.parse("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"fileOverlayRoot\":\"files\","
                + "\"process\":{\"pid\":9}"
                + "}", new File("just-name.json"));
        assertNull(profile.getOverlayRoot());
        assertNull(profile.getEnvironmentConfig().getProfileFileOverlay());
        assertNull(profile.getEnvironmentConfig().readProfileOverlayFile("/proc/cpuinfo"));
        assertEquals(9, profile.getEnvironmentConfig().getPid(-1));
        assertTrue(profile.getEnvironmentConfig().isProfileLoaded());
    }

    @Test
    public void testNoConfiguredPidDoesNotAliasNumericProc() throws Exception {
        File dir = Files.createTempDirectory("traceai-nopid-alias-").toFile();
        File status = new File(dir, "files/proc/self/status");
        assertTrue(status.getParentFile().mkdirs());
        Files.write(status.toPath(), "NO_PID_STATUS".getBytes(StandardCharsets.UTF_8));
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"fileOverlayRoot\":\"files\""
                + "}").getBytes(StandardCharsets.UTF_8));
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.load(json);
        TraceEnvironmentConfig config = profile.getEnvironmentConfig();
        assertEquals("NO_PID_STATUS",
                new String(config.readProfileOverlayFile("/proc/self/status"), StandardCharsets.UTF_8));
        assertNull(config.readProfileOverlayFile("/proc/12345/status"));
        assertNull(config.readProfileOverlayFile("/proc/1/status"));
    }

    @Test
    public void testMissingOverlayDirectoryStillLoads() throws Exception {
        File dir = Files.createTempDirectory("traceai-missing-overlay-").toFile();
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"profileName\":\"no-files\""
                + "}").getBytes(StandardCharsets.UTF_8));
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.load(json);
        assertEquals(new File(dir, "files"), profile.getOverlayRoot());
        assertFalse(profile.getOverlayRoot().exists());
        assertNull(profile.getEnvironmentConfig().readProfileOverlayFile("/proc/cpuinfo"));
    }

    @Test
    public void testOverlayDirectoryAndNestedRoot() throws Exception {
        File dir = Files.createTempDirectory("traceai-nested-overlay-").toFile();
        File cpuAsDir = new File(dir, "files/proc/cpuinfo");
        assertTrue(cpuAsDir.mkdirs());
        File nestedCpu = new File(dir, "files/proc/cpuinfo-file");
        Files.write(nestedCpu.toPath(), "NESTED_CPU".getBytes(StandardCharsets.UTF_8));
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"fileOverlayRoot\":\"files/proc\""
                + "}").getBytes(StandardCharsets.UTF_8));
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.load(json);
        TraceEnvironmentConfig config = profile.getEnvironmentConfig();
        assertNull(config.readProfileOverlayFile("/cpuinfo"));
        assertEquals("NESTED_CPU",
                new String(config.readProfileOverlayFile("/cpuinfo-file"), StandardCharsets.UTF_8));
        assertNull(config.readProfileOverlayFile("/proc/cpuinfo"));
        assertNull(config.readProfileOverlayFile("/proc/cpuinfo-file"));
    }

    @Test
    public void testLinuxFilesRemainOnConfigWhenOverlayPresent() throws Exception {
        File dir = Files.createTempDirectory("traceai-both-sources-").toFile();
        File overlayCpu = new File(dir, "files/proc/cpuinfo");
        assertTrue(overlayCpu.getParentFile().mkdirs());
        Files.write(overlayCpu.toPath(), "OVERLAY_CPU".getBytes(StandardCharsets.UTF_8));
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"fileOverlayRoot\":\"files\","
                + "\"linux\":{\"files\":{\"/proc/cpuinfo\":\"INLINE_CPU\"}}"
                + "}").getBytes(StandardCharsets.UTF_8));
        TraceEnvironmentConfig config = DeviceFingerprintProfile.load(json).getEnvironmentConfig();
        assertEquals("OVERLAY_CPU",
                new String(config.readProfileOverlayFile("/proc/cpuinfo"), StandardCharsets.UTF_8));
        assertEquals("INLINE_CPU",
                new String(config.getLinuxFileBytes("/proc/cpuinfo"), StandardCharsets.UTF_8));
    }

    @Test
    public void testConsistentFieldsDoNotWarn() {
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.parse("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"process\":{\"processName\":\"com.demo.app\"},"
                + "\"android\":{\"packageName\":\"com.demo.app\","
                + "\"build\":{\"MODEL\":\"TRACEAI_PIXEL_6\",\"HARDWARE\":\"raven\","
                + "\"VERSION.SDK_INT\":31},"
                + "\"properties\":{\"ro.product.model\":\"TRACEAI_PIXEL_6\","
                + "\"ro.hardware\":\"raven\",\"ro.build.version.sdk\":\"31\","
                + "\"persist.sys.timezone\":\"Asia/Shanghai\"},"
                + "\"locale\":{\"timezoneId\":\"Asia/Shanghai\"}}"
                + "}", null);
        assertTrue(profile.getConsistencyWarnings().isEmpty());
    }

    @Test
    public void testSplitOverlayRootRejectsAbsoluteAndBackslash() {
        File sample = locateSampleProfile();
        assertOverlayRootRejected("/files", sample);
        assertOverlayRootRejected("files\\proc", sample);
        assertOverlayRootRejected("files/sub ", sample);
    }

    @Test
    public void testIntermediateOverlayComponentMustBeDirectory() throws Exception {
        File dir = Files.createTempDirectory("traceai-overlay-midfile-").toFile();
        File procAsFile = new File(dir, "files/proc");
        assertTrue(procAsFile.getParentFile().mkdirs());
        Files.write(procAsFile.toPath(), "NOT_A_DIR".getBytes(StandardCharsets.UTF_8));
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"fileOverlayRoot\":\"files\""
                + "}").getBytes(StandardCharsets.UTF_8));
        assertNull(DeviceFingerprintProfile.load(json).getEnvironmentConfig()
                .readProfileOverlayFile("/proc/cpuinfo"));
    }

    @Test
    public void testFromSystemPropertyBlankIsNull() {
        String previous = System.getProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY);
        try {
            System.setProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY, "   ");
            assertNull(DeviceFingerprintProfile.fromSystemProperty());
            System.setProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY, "");
            assertNull(DeviceFingerprintProfile.fromSystemProperty());
        } finally {
            if (previous == null) {
                System.clearProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY);
            } else {
                System.setProperty(DeviceFingerprintProfile.SYSTEM_PROPERTY, previous);
            }
        }
    }

    @Test
    public void testZeroPidDoesNotAliasNumericProc() throws Exception {
        File dir = Files.createTempDirectory("traceai-pid0-alias-").toFile();
        File status = new File(dir, "files/proc/self/status");
        assertTrue(status.getParentFile().mkdirs());
        Files.write(status.toPath(), "PID0_SELF".getBytes(StandardCharsets.UTF_8));
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"process\":{\"pid\":0},"
                + "\"fileOverlayRoot\":\"files\""
                + "}").getBytes(StandardCharsets.UTF_8));
        TraceEnvironmentConfig config = DeviceFingerprintProfile.load(json).getEnvironmentConfig();
        assertEquals(0, config.getPid(-1));
        assertEquals("PID0_SELF",
                new String(config.readProfileOverlayFile("/proc/self/status"), StandardCharsets.UTF_8));
        assertNull(config.readProfileOverlayFile("/proc/0/status"));
    }

    @Test
    public void testNumericProcFileAliasesSelfAndRejectsPrefixPid() throws Exception {
        File dir = Files.createTempDirectory("traceai-pid-prefix-").toFile();
        File numeric = new File(dir, "files/proc/12345/status");
        assertTrue(numeric.getParentFile().mkdirs());
        Files.write(numeric.toPath(), "ONLY_NUMERIC".getBytes(StandardCharsets.UTF_8));
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"process\":{\"pid\":12345},"
                + "\"fileOverlayRoot\":\"files\""
                + "}").getBytes(StandardCharsets.UTF_8));
        TraceEnvironmentConfig config = DeviceFingerprintProfile.load(json).getEnvironmentConfig();
        assertEquals("ONLY_NUMERIC",
                new String(config.readProfileOverlayFile("/proc/12345/status"), StandardCharsets.UTF_8));
        assertEquals("ONLY_NUMERIC",
                new String(config.readProfileOverlayFile("/proc/self/status"), StandardCharsets.UTF_8));
        assertNull(config.readProfileOverlayFile("/proc/123456/status"));
        assertNull(config.readProfileOverlayFile("/proc/1234/status"));
        assertNull(config.readProfileOverlayFile("/proc/012345/status"));
    }

    @Test
    public void testNegativeTeeIntegerStrippedAndSchemaWhitespaceRejected() {
        DeviceFingerprintProfile negative = parseMinimalProfile(
                "\"android\":{\"tee\":{\"securityLevel\":-1}}");
        assertFalse(negative.getEnvironmentConfig().isTeeSecurityLevelConfigured());
        assertTrue(containsFragment(negative.getReservedWarnings(),
                "android.tee.securityLevel"));
        assertSchemaRejected("{\"schemaVersion\":\" traceai-device-fingerprint/v1\"}");
        assertSchemaRejected("{\"schemaVersion\":\"traceai-device-fingerprint/v1 \"}");
        try {
            DeviceFingerprintProfile.parse("[]", null);
            fail("expected non-object json failure");
        } catch (RuntimeException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void testBackendStatusStringAndAbsentReservedKeys() {
        DeviceFingerprintProfile withStatus = parseMinimalProfile(
                "\"backendStatus\":\"reserved\"");
        assertEquals("reserved", withStatus.getReservedField("backendStatus"));
        assertTrue(containsFragment(withStatus.getReservedWarnings(), "backendStatus"));
        DeviceFingerprintProfile bare = parseMinimalProfile(
                "\"android\":{\"packageName\":\"com.demo.app\"}");
        assertFalse(bare.getReservedFields().containsKey("android.sensors.samples"));
        assertFalse(bare.getReservedFields().containsKey("android.telephony.cellInfo"));
        assertFalse(bare.getReservedFields().containsKey("network.capabilities"));
        assertFalse(bare.getReservedFields().containsKey("graphics.native"));
        assertFalse(bare.getReservedFields().containsKey("backendStatus"));
    }

    @Test
    public void testOneSidedConsistencyDoesNotWarn() {
        DeviceFingerprintProfile onlyModel = parseMinimalProfile(
                "\"android\":{\"build\":{\"MODEL\":\"A\"}}");
        assertTrue(onlyModel.getConsistencyWarnings().isEmpty());
        assertEquals("A", onlyModel.getEnvironmentConfig().getAndroidBuildString("MODEL"));
        DeviceFingerprintProfile onlyProcess = parseMinimalProfile(
                "\"process\":{\"processName\":\"com.demo.app\"}");
        assertTrue(onlyProcess.getConsistencyWarnings().isEmpty());
    }

    @Test
    public void testOverlayRootColonRejectedAndLoadKeepsProfileFile() {
        File sample = locateSampleProfile();
        assertOverlayRootRejected("files:secret", sample);
        DeviceFingerprintProfile profile = DeviceFingerprintProfile.load(sample);
        assertEquals(sample.getAbsoluteFile(), profile.getProfileFile().getAbsoluteFile());
        assertEquals(new File(sample.getParentFile(), "files").getAbsoluteFile(),
                profile.getOverlayRoot().getAbsoluteFile());
    }

    @Test
    public void testResolveContainedDirectoryAndSelfPath() throws Exception {
        File dir = Files.createTempDirectory("traceai-contained-").toFile();
        File files = new File(dir, "files");
        File proc = new File(files, "proc");
        assertTrue(proc.mkdirs());
        assertNotNull(ProfileFileOverlay.resolveContainedDirectory(files, dir));
        assertNull(ProfileFileOverlay.resolveContainedDirectory(dir, files));
        assertNull(ProfileFileOverlay.resolveContainedDirectory(files, files));
        assertTrue(ProfileFileOverlay.isInside(files.toPath(), dir.toPath()));
        assertFalse(ProfileFileOverlay.isInside(dir.toPath(), dir.toPath()));
        assertFalse(ProfileFileOverlay.isInside(dir.toPath(), files.toPath()));
        File json = new File(dir, "device-fingerprint.json");
        Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"fileOverlayRoot\":\"files\""
                + "}").getBytes(StandardCharsets.UTF_8));
        assertNull(DeviceFingerprintProfile.load(json).getEnvironmentConfig()
                .readProfileOverlayFile("/proc/self"));
        assertNull(DeviceFingerprintProfile.load(json).getEnvironmentConfig()
                .readProfileOverlayFile("/proc"));
    }

    @Test
    public void testNormalizeGuestPathAdditionalRejects() {
        assertNull(ProfileFileOverlay.normalizeGuestPath(null));
        assertNull(ProfileFileOverlay.normalizeGuestPath(""));
        assertNull(ProfileFileOverlay.normalizeGuestPath("/proc/\tcpuinfo"));
        assertNull(ProfileFileOverlay.normalizeGuestPath("/proc/\ncpuinfo"));
        assertNull(ProfileFileOverlay.normalizeGuestPath("/proc/cpuinfo "));
        assertNull(ProfileFileOverlay.normalizeGuestPath("/proc//"));
        assertEquals("/proc/self/fdinfo",
                ProfileFileOverlay.normalizeGuestPath("/proc/self/fdinfo"));
        assertFalse(ProfileFileOverlay.isBlockedProcFd("/proc/self/fdinfo"));
        assertTrue(ProfileFileOverlay.isBlockedProcFd("/proc/self/fd"));
        assertTrue(ProfileFileOverlay.isBlockedProcFd("/proc/0123/fd"));
        assertFalse(ProfileFileOverlay.isBlockedProcFd("/proc/self/fdmore"));
    }

    private static DeviceFingerprintProfile parseMinimalProfile(String extraJsonFields) {
        String json = "{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\"";
        if (extraJsonFields != null && !extraJsonFields.isEmpty()) {
            json += "," + extraJsonFields;
        }
        json += "}";
        return DeviceFingerprintProfile.parse(json, null);
    }

    private static void assertSchemaRejected(String json) {
        try {
            DeviceFingerprintProfile.parse(json, null);
            fail("expected schemaVersion failure for " + json);
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage().contains("schemaVersion"));
        }
    }

    private static void assertSameReservedOnConfig(DeviceFingerprintProfile profile, String path) {
        Object fromProfile = profile.getReservedField(path);
        Map<String, Object> attached = profile.getEnvironmentConfig().getProfileReservedFields();
        assertNotNull(attached.get(path));
        assertEquals(String.valueOf(fromProfile), String.valueOf(attached.get(path)));
    }

    private static boolean tryCreateSymbolicLink(File link, File target) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath());
            return Files.isSymbolicLink(link.toPath());
        } catch (UnsupportedOperationException ignored) {
            return false;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void assertOverlayRootRejected(String overlayRoot, File profileFile) {
        try {
            DeviceFingerprintProfile.parse("{"
                    + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                    + "\"fileOverlayRoot\":\"" + overlayRoot.replace("\\", "\\\\").replace("\0", "\\u0000")
                    + "\"}", profileFile);
            fail("expected overlay escape failure for " + overlayRoot);
        } catch (IllegalArgumentException expected) {
            assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage() != null
                            && expected.getMessage().contains("fileOverlayRoot"));
        }
    }

    private static String findEnvironEntry(List<String> environ, String key) {
        String prefix = key + "=";
        for (int i = 0; i < environ.size(); i++) {
            String entry = environ.get(i);
            if (entry != null && entry.startsWith(prefix)) {
                return entry;
            }
        }
        return null;
    }

    private static void assertSampleOverlayMatches(TraceEnvironmentConfig config, File filesRoot,
                                                   String guestPath) throws Exception {
        File onDisk = new File(filesRoot, guestPath.substring(1).replace('/', File.separatorChar));
        byte[] expected = Files.readAllBytes(onDisk.toPath());
        byte[] overlay = config.readProfileOverlayFile(guestPath);
        assertNotNull(guestPath, overlay);
        assertArrayEquals(guestPath, expected, overlay);
    }

    private static boolean containsFragment(List<String> items, String fragment) {
        for (String item : items) {
            if (item != null && item.contains(fragment)) {
                return true;
            }
        }
        return false;
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
}

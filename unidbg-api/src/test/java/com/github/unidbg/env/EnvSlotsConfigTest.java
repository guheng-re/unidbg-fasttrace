package com.github.unidbg.env;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Parse-level coverage for generic environment slots: present / absent / empty / illegal.
 */
public class EnvSlotsConfigTest {

    @Test
    public void testTcpPresentEmptyAbsent() {
        assertFalse(TraceEnvironmentConfig.parse("{}").isNetworkTcpConfigured());
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{\"network\":{\"tcp\":[]}}");
        assertTrue(empty.isNetworkTcpConfigured());
        assertTrue(empty.getNetworkTcp().isEmpty());
        TraceEnvironmentConfig one = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"tcp\":[{\"slot\":0,\"localIpv4\":\"127.0.0.1\",\"localPort\":8080,"
                + "\"remoteIpv4\":\"0.0.0.0\",\"remotePort\":0,\"stateHex\":\"0A\",\"inode\":1}]}}");
        assertEquals(1, one.getNetworkTcp().size());
        assertEquals("127.0.0.1", one.getNetworkTcp().get(0).getLocalAddress());
        assertEquals(8080, one.getNetworkTcp().get(0).getLocalPort());
        try {
            TraceEnvironmentConfig.parse("{\"network\":{\"tcp\":[{\"slot\":0}]}}");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("localIpv4"));
        }
    }

    @Test
    public void testTcp6PresentEmptyAbsent() {
        assertFalse(TraceEnvironmentConfig.parse("{}").isNetworkTcp6Configured());
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{\"network\":{\"tcp6\":[]}}");
        assertTrue(empty.isNetworkTcp6Configured());
        assertTrue(empty.getNetworkTcp6().isEmpty());
        TraceEnvironmentConfig one = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"tcp6\":[{\"slot\":0,"
                + "\"localIpv6\":\"00000000000000000000000000000001\",\"localPort\":80,"
                + "\"remoteIpv6\":\"00000000000000000000000000000000\",\"remotePort\":0,"
                + "\"stateHex\":\"0A\"}]}}");
        assertEquals(32, one.getNetworkTcp6().get(0).getLocalAddress().length());
        assertTrue(one.getNetworkTcp6().get(0).isIpv6());
    }

    @Test
    public void testProcessesCommandsMincoreDirectories() {
        assertFalse(TraceEnvironmentConfig.parse("{}").isLinuxProcessesConfigured());
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{\"linux\":{\"processes\":[]}}");
        assertTrue(empty.isLinuxProcessesConfigured());
        assertTrue(empty.getLinuxProcesses().isEmpty());
        TraceEnvironmentConfig one = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"processes\":[{\"pid\":1,\"cmdline\":[\"init\"],\"comm\":\"init\","
                + "\"exe\":\"/system/bin/init\"}]}}");
        assertEquals(1, one.findLinuxProcess(1).getPid());
        assertEquals("init", one.findLinuxProcess(1).getComm());

        assertFalse(TraceEnvironmentConfig.parse("{}").isLinuxCommandsConfigured());
        TraceEnvironmentConfig cmds = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"commands\":{\"uptime\":\"0.00 0.00 0.00\\n\"}}}");
        assertEquals("0.00 0.00 0.00\n", cmds.getLinuxCommandStdout("uptime"));
        assertNull(cmds.getLinuxCommandStdout("missing"));

        assertFalse(TraceEnvironmentConfig.parse("{}").isLinuxMincoreConfigured());
        TraceEnvironmentConfig mincore = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"mincore\":{\"resident\":true}}}");
        assertTrue(mincore.isLinuxMincoreResident());

        TraceEnvironmentConfig dirs = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"directories\":{\"/system/fonts\":[\"A.ttf\",\"B.ttf\"]}}}");
        assertEquals(2, dirs.getFilesystemDirectoryEntries("/system/fonts").size());
        assertEquals("A.ttf", dirs.getFilesystemDirectoryEntries("/system/fonts").get(0));
    }

    @Test
    public void testCapabilitiesLocalePluggedDisplaysFlagsSamples() {
        assertFalse(TraceEnvironmentConfig.parse("{}").isNetworkCapabilitiesConfigured());
        TraceEnvironmentConfig caps = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"capabilities\":{\"transportTypes\":[1],\"networkCapabilities\":[12,16]}}}");
        assertTrue(caps.getNetworkCapabilitiesConfig().hasTransport(1));
        assertFalse(caps.getNetworkCapabilitiesConfig().hasTransport(0));
        assertEquals(2L, caps.getNetworkCapabilitiesConfig().transportBitset());
        assertTrue(caps.getNetworkCapabilitiesConfig().hasCapability(12));

        TraceEnvironmentConfig locale = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"locale\":{\"languageTags\":[\"zh-Hans-CN\",\"en-US\"]}}}");
        assertTrue(locale.getAndroidLocaleConfig().isLanguageTagsConfigured());
        assertEquals(2, locale.getAndroidLocaleConfig().getLanguageTags().size());

        TraceEnvironmentConfig battery = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"plugged\":2}}}");
        assertTrue(battery.getAndroidBatteryConfig().isPluggedConfigured());
        assertEquals(2, battery.getAndroidBatteryConfig().getPlugged());
        try {
            TraceEnvironmentConfig.parse("{\"android\":{\"battery\":{\"plugged\":3}}}");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("plugged"));
        }

        TraceEnvironmentConfig displays = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"displays\":["
                + "{\"id\":0,\"name\":\"built-in\",\"flags\":2,\"widthPixels\":1080,\"heightPixels\":2400,\"densityDpi\":420},"
                + "{\"id\":1,\"name\":\"hdmi\",\"flags\":0,\"widthPixels\":1920,\"heightPixels\":1080,\"densityDpi\":160}"
                + "]}}");
        assertEquals(2, displays.getAndroidDisplays().size());
        assertEquals("hdmi", displays.findAndroidDisplay(1).getName());

        TraceEnvironmentConfig pkg = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"packages\":[{\"packageName\":\"com.demo.app\",\"applicationFlags\":8388673}]}}");
        assertTrue(pkg.getAndroidPackages().get(0).isApplicationFlagsConfigured());
        assertEquals(Integer.valueOf(8388673), pkg.getAndroidPackages().get(0).getApplicationFlags());

        TraceEnvironmentConfig samples = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"sensors\":{\"types\":[1],\"samples\":{\"1\":[0.0,0.0,9.81]}}}}");
        assertTrue(samples.isAndroidSensorSamplesConfigured());
        assertArrayEquals(new float[]{0f, 0f, 9.81f}, samples.getAndroidSensorSample(1), 0.001f);
        assertNull(samples.getAndroidSensorSample(4));
    }

    @Test
    public void testMediaDrmSharedResolver() {
        TraceEnvironmentConfig hex = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"drm\":{\"deviceUniqueIdHex\":\"01020304\"}}}");
        assertArrayEquals(new byte[]{1, 2, 3, 4}, hex.resolveMediaDrmDeviceUniqueId());
        TraceEnvironmentConfig random = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"drm\":{\"marker\":\"MARK\"}},"
                + "\"random\":{\"mediaDrmDeviceUniqueIdHex\":\"aabb\"}}");
        byte[] repeated = random.resolveMediaDrmDeviceUniqueId();
        assertEquals(0x20, repeated.length);
        assertEquals((byte) 0xaa, repeated[0]);
        assertEquals((byte) 0xbb, repeated[1]);
        assertNull(TraceEnvironmentConfig.parse("{}").resolveMediaDrmDeviceUniqueId());
    }
}

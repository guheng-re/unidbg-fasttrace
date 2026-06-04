package com.github.unidbg.env;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class TraceEnvironmentConfigTest {

    @Test
    public void testParseConfiguredValues() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\",\"pid\":12345},"
                + "\"time\":{\"currentTimeMillis\":1718000000000,\"monotonicNanos\":123456789000,\"timezoneMinutesWest\":-480},"
                + "\"random\":{\"getrandomHex\":\"0011\",\"uuid\":\"00000000-0000-0000-0000-000000000001\"},"
                + "\"linux\":{\"uname\":{\"machine64\":\"aarch64\"},\"files\":{\"/proc/cpuinfo\":\"Hardware\\t: raven\\n\"}},"
                + "\"android\":{\"packageName\":\"com.demo.app\",\"versionName\":\"1.0.0\",\"versionCode\":100,"
                + "\"build\":{\"MODEL\":\"Pixel 6\",\"VERSION.SDK_INT\":31},"
                + "\"properties\":{\"ro.hardware\":\"raven\"}}"
                + "}");

        assertEquals("com.demo.app", config.getProcessName(null));
        assertEquals(12345, config.getPid(1));
        assertEquals(1718000000000L, config.getCurrentTimeMillis(0));
        assertEquals(123456789000L, config.getMonotonicNanos(0));
        assertEquals(-480, config.getTimezoneMinutesWest(0));
        assertArrayEquals(new byte[]{0x00, 0x11, 0x00, 0x11}, config.getRandomBytes("getrandomHex", 4));
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), config.getUuid(null));
        assertEquals("aarch64", config.getUnameMachine(true, "fallback"));
        assertEquals("Hardware\t: raven\n", new String(config.getLinuxFileBytes("/proc/cpuinfo")));
        assertEquals("com.demo.app", config.getAndroidPackageName(null));
        assertEquals("1.0.0", config.getVersionName(null));
        assertEquals(100, config.getVersionCode(0));
        assertEquals("Pixel 6", config.getAndroidBuildString("MODEL"));
        assertEquals(Integer.valueOf(31), config.getAndroidBuildInt("VERSION.SDK_INT"));
        assertEquals("raven", config.getAndroidProperty("ro.hardware"));
    }

    @Test
    public void testMissingFieldsFallback() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{}");

        assertEquals("fallback", config.getProcessName("fallback"));
        assertEquals(7, config.getPid(7));
        assertEquals(8L, config.getCurrentTimeMillis(8L));
        assertEquals("armv7l", config.getUnameMachine(false, "armv7l"));
        assertEquals("pkg", config.getAndroidPackageName("pkg"));
        assertEquals(9L, config.getVersionCode(9L));
        assertEquals(null, config.getLinuxFileBytes("/proc/cpuinfo"));
        assertEquals(null, config.getAndroidProperty("ro.hardware"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidHexFailsDuringParse() {
        TraceEnvironmentConfig.parse("{\"random\":{\"getrandomHex\":\"abc\"}}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidUuidFailsDuringParse() {
        TraceEnvironmentConfig.parse("{\"random\":{\"uuid\":\"not-a-uuid\"}}");
    }
}

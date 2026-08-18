package com.github.unidbg.env;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
        assertFalse(config.isAndroidBuildSupportedAbisConfigured());
        assertNull(config.getAndroidBuildSupportedAbis());
        assertFalse(config.isAndroidBuildSupported32BitAbisConfigured());
        assertNull(config.getAndroidBuildSupported32BitAbis());
        assertFalse(config.isAndroidBuildSupported64BitAbisConfigured());
        assertNull(config.getAndroidBuildSupported64BitAbis());
        assertFalse(config.isUnameSysnameConfigured());
        assertFalse(config.isUnameNodenameConfigured());
        assertFalse(config.isUnameReleaseConfigured());
        assertFalse(config.isUnameVersionConfigured());
        assertFalse(config.isUnameDomainnameConfigured());
        assertFalse(config.isAndroidDataDirConfigured());
        assertNull(config.getAndroidDataDir());
    }

    @Test
    public void testAndroidDataDirConfiguredPresence() {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missing.isAndroidDataDirConfigured());
        assertNull(missing.getAndroidDataDir());
        // getDataDir(fallback) still returns fallback when key absent
        assertEquals("/fallback", missing.getDataDir("/fallback"));

        TraceEnvironmentConfig nullDataDir = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\",\"dataDir\":null}}");
        assertFalse(nullDataDir.isAndroidDataDirConfigured());
        assertNull(nullDataDir.getAndroidDataDir());
        // explicit null still falls through getDataDir(fallback)
        assertEquals("/fallback", nullDataDir.getDataDir("/fallback"));

        TraceEnvironmentConfig configured = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\","
                        + "\"dataDir\":\"/data/user/0/com.demo.app\"}}");
        assertTrue(configured.isAndroidDataDirConfigured());
        assertEquals("/data/user/0/com.demo.app", configured.getAndroidDataDir());
        assertEquals("/data/user/0/com.demo.app",
                configured.getDataDir("/fallback"));
    }

    @Test
    public void testUnamePresenceAwareAccessors() {
        TraceEnvironmentConfig emptyUname = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"uname\":{}}}");
        assertFalse(emptyUname.isUnameSysnameConfigured());
        assertFalse(emptyUname.isUnameNodenameConfigured());
        assertFalse(emptyUname.isUnameReleaseConfigured());
        assertFalse(emptyUname.isUnameVersionConfigured());
        assertFalse(emptyUname.isUnameDomainnameConfigured());
        // getters still offer syscall-style fallbacks when key absent
        assertEquals("Linux", emptyUname.getUnameSysname("Linux"));
        assertEquals("android", emptyUname.getUnameNodename("android"));
        assertEquals("rel", emptyUname.getUnameRelease("rel"));

        TraceEnvironmentConfig partial = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"uname\":{"
                + "\"nodename\":\"host1\","
                + "\"release\":\"5.10.0\""
                + "}}}"
        );
        assertFalse(partial.isUnameSysnameConfigured());
        assertTrue(partial.isUnameNodenameConfigured());
        assertTrue(partial.isUnameReleaseConfigured());
        assertFalse(partial.isUnameVersionConfigured());
        assertFalse(partial.isUnameDomainnameConfigured());
        assertEquals("Linux", partial.getUnameSysname("Linux"));
        assertEquals("host1", partial.getUnameNodename(null));
        assertEquals("5.10.0", partial.getUnameRelease(null));
        assertEquals("fallback", partial.getUnameVersion("fallback"));
        assertEquals("(none)", partial.getUnameDomainname("(none)"));

        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"uname\":{"
                + "\"sysname\":\"Linux\","
                + "\"nodename\":\"android\","
                + "\"release\":\"5.4.210\","
                + "\"version\":\"#1 SMP\","
                + "\"domainname\":\"(none)\""
                + "}}}"
        );
        assertTrue(full.isUnameSysnameConfigured());
        assertTrue(full.isUnameNodenameConfigured());
        assertTrue(full.isUnameReleaseConfigured());
        assertTrue(full.isUnameVersionConfigured());
        assertTrue(full.isUnameDomainnameConfigured());
        assertEquals("Linux", full.getUnameSysname(null));
        assertEquals("android", full.getUnameNodename(null));
        assertEquals("5.4.210", full.getUnameRelease(null));
        assertEquals("#1 SMP", full.getUnameVersion(null));
        assertEquals("(none)", full.getUnameDomainname(null));
    }

    @Test
    public void testAndroidRuntimeSystemPropertiesConfig() {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidRuntimeSystemPropertiesConfigured());
        assertTrue(missing.getAndroidRuntimeSystemProperties().isEmpty());
        assertFalse(missing.isAndroidRuntimeSystemPropertyConfigured("os.name"));

        TraceEnvironmentConfig noRuntime = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(noRuntime.isAndroidRuntimeSystemPropertiesConfigured());

        TraceEnvironmentConfig runtimeEmpty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{}}}");
        assertFalse(runtimeEmpty.isAndroidRuntimeSystemPropertiesConfigured());
        assertTrue(runtimeEmpty.getAndroidRuntimeSystemProperties().isEmpty());

        // explicit empty object configured
        TraceEnvironmentConfig emptyProps = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"systemProperties\":{}}}}");
        assertTrue(emptyProps.isAndroidRuntimeSystemPropertiesConfigured());
        assertTrue(emptyProps.getAndroidRuntimeSystemProperties().isEmpty());
        assertFalse(emptyProps.isAndroidRuntimeSystemPropertyConfigured("os.name"));

        // string + explicit null + empty string value
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"runtime\":{\"systemProperties\":{"
                + "\"os.name\":\"Linux\","
                + "\"java.vm.name\":null,"
                + "\"custom.empty\":\"\""
                + "}}}}"
        );
        assertTrue(full.isAndroidRuntimeSystemPropertiesConfigured());
        assertEquals(3, full.getAndroidRuntimeSystemProperties().size());
        assertTrue(full.isAndroidRuntimeSystemPropertyConfigured("os.name"));
        assertEquals("Linux", full.getAndroidRuntimeSystemProperty("os.name"));
        assertTrue(full.isAndroidRuntimeSystemPropertyConfigured("java.vm.name"));
        assertNull(full.getAndroidRuntimeSystemProperty("java.vm.name"));
        assertTrue(full.isAndroidRuntimeSystemPropertyConfigured("custom.empty"));
        assertEquals("", full.getAndroidRuntimeSystemProperty("custom.empty"));
        assertFalse(full.isAndroidRuntimeSystemPropertyConfigured("absent.key"));
        assertNull(full.getAndroidRuntimeSystemProperty("absent.key"));

        // immutability
        try {
            full.getAndroidRuntimeSystemProperties().put("x", "y");
            fail("expected UnsupportedOperationException for systemProperties map");
        } catch (UnsupportedOperationException expected) {
            // ok
        }

        // invalid runtime / systemProperties
        assertInvalid("{\"android\":{\"runtime\":[]}}", "android.runtime");
        assertInvalid("{\"android\":{\"runtime\":1}}", "android.runtime");
        assertInvalid("{\"android\":{\"runtime\":{\"extra\":1}}}", "android.runtime.extra");
        assertInvalid("{\"android\":{\"runtime\":{\"systemProperties\":[]}}}",
                "android.runtime.systemProperties");
        assertInvalid("{\"android\":{\"runtime\":{\"systemProperties\":1}}}",
                "android.runtime.systemProperties");
        assertInvalid("{\"android\":{\"runtime\":{\"systemProperties\":null}}}",
                "android.runtime.systemProperties");
        assertInvalid("{\"android\":{\"runtime\":{\"systemProperties\":{\"\": \"x\"}}}}",
                "android.runtime.systemProperties.");
        assertInvalid("{\"android\":{\"runtime\":{\"systemProperties\":{\"a\\nb\": \"x\"}}}}",
                "android.runtime.systemProperties.");
        assertInvalid("{\"android\":{\"runtime\":{\"systemProperties\":{\"k\":1}}}}",
                "android.runtime.systemProperties.k");
        assertInvalid("{\"android\":{\"runtime\":{\"systemProperties\":{\"k\":true}}}}",
                "android.runtime.systemProperties.k");
        assertInvalid("{\"android\":{\"runtime\":{\"systemProperties\":{\"k\":{}}}}}",
                "android.runtime.systemProperties.k");
    }

    @Test
    public void testAndroidRuntimeAvailableProcessorsConfig() {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidRuntimeAvailableProcessorsConfigured());

        TraceEnvironmentConfig runtimeEmpty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{}}}");
        assertFalse(runtimeEmpty.isAndroidRuntimeAvailableProcessorsConfigured());
        assertFalse(runtimeEmpty.isAndroidRuntimeSystemPropertiesConfigured());

        TraceEnvironmentConfig propsOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"systemProperties\":{}}}}");
        assertFalse(propsOnly.isAndroidRuntimeAvailableProcessorsConfigured());

        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"availableProcessors\":8}}}");
        assertTrue(full.isAndroidRuntimeAvailableProcessorsConfigured());
        assertEquals(8, full.getAndroidRuntimeAvailableProcessors());

        TraceEnvironmentConfig min = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"availableProcessors\":1}}}");
        assertTrue(min.isAndroidRuntimeAvailableProcessorsConfigured());
        assertEquals(1, min.getAndroidRuntimeAvailableProcessors());

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"availableProcessors\":4096}}}");
        assertTrue(max.isAndroidRuntimeAvailableProcessorsConfigured());
        assertEquals(4096, max.getAndroidRuntimeAvailableProcessors());

        // independent of maps and of linux.cpu counts
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"cpu\":{\"configuredProcessorCount\":4,\"onlineProcessorCount\":2}},"
                + "\"android\":{\"runtime\":{"
                + "\"systemProperties\":{\"os.name\":\"Linux\"},"
                + "\"availableProcessors\":16"
                + "}}"
                + "}");
        assertTrue(both.isAndroidRuntimeAvailableProcessorsConfigured());
        assertEquals(16, both.getAndroidRuntimeAvailableProcessors());
        assertTrue(both.isAndroidRuntimeSystemPropertiesConfigured());
        assertTrue(both.isLinuxCpuConfigured());
        assertEquals(4, both.getLinuxCpuConfig().getConfiguredProcessorCount());

        assertInvalid("{\"android\":{\"runtime\":{\"availableProcessors\":null}}}",
                "android.runtime.availableProcessors");
        assertInvalid("{\"android\":{\"runtime\":{\"availableProcessors\":true}}}",
                "android.runtime.availableProcessors");
        assertInvalid("{\"android\":{\"runtime\":{\"availableProcessors\":\"8\"}}}",
                "android.runtime.availableProcessors");
        assertInvalid("{\"android\":{\"runtime\":{\"availableProcessors\":0}}}",
                "android.runtime.availableProcessors");
        assertInvalid("{\"android\":{\"runtime\":{\"availableProcessors\":4097}}}",
                "android.runtime.availableProcessors");
        assertInvalid("{\"android\":{\"runtime\":{\"availableProcessors\":1.5}}}",
                "android.runtime.availableProcessors");
        assertInvalid("{\"android\":{\"runtime\":{\"availableProcessors\":[]}}}",
                "android.runtime.availableProcessors");
    }

    @Test
    public void testAndroidRuntimeMaxMemoryBytesConfig() {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidRuntimeMaxMemoryBytesConfigured());

        TraceEnvironmentConfig runtimeEmpty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{}}}");
        assertFalse(runtimeEmpty.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertFalse(runtimeEmpty.isAndroidRuntimeAvailableProcessorsConfigured());

        TraceEnvironmentConfig propsOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"systemProperties\":{}}}}");
        assertFalse(propsOnly.isAndroidRuntimeMaxMemoryBytesConfigured());

        TraceEnvironmentConfig apOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"availableProcessors\":8}}}");
        assertFalse(apOnly.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertTrue(apOnly.isAndroidRuntimeAvailableProcessorsConfigured());

        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"maxMemoryBytes\":268435456}}}");
        assertTrue(full.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertEquals(268435456L, full.getAndroidRuntimeMaxMemoryBytes());
        assertFalse(full.isAndroidRuntimeAvailableProcessorsConfigured());

        TraceEnvironmentConfig min = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"maxMemoryBytes\":1}}}");
        assertTrue(min.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertEquals(1L, min.getAndroidRuntimeMaxMemoryBytes());

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"maxMemoryBytes\":" + Long.MAX_VALUE + "}}}");
        assertTrue(max.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertEquals(Long.MAX_VALUE, max.getAndroidRuntimeMaxMemoryBytes());

        // 与 maps / availableProcessors / linux.cpu 独立
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"cpu\":{\"configuredProcessorCount\":4,\"onlineProcessorCount\":2}},"
                + "\"android\":{\"runtime\":{"
                + "\"systemProperties\":{\"os.name\":\"Linux\"},"
                + "\"availableProcessors\":16,"
                + "\"maxMemoryBytes\":512"
                + "}}"
                + "}");
        assertTrue(both.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertEquals(512L, both.getAndroidRuntimeMaxMemoryBytes());
        assertTrue(both.isAndroidRuntimeAvailableProcessorsConfigured());
        assertEquals(16, both.getAndroidRuntimeAvailableProcessors());
        assertTrue(both.isAndroidRuntimeSystemPropertiesConfigured());
        assertTrue(both.isLinuxCpuConfigured());
        assertEquals(4, both.getLinuxCpuConfig().getConfiguredProcessorCount());

        assertInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":null}}}",
                "android.runtime.maxMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":true}}}",
                "android.runtime.maxMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":\"268435456\"}}}",
                "android.runtime.maxMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":0}}}",
                "android.runtime.maxMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":-1}}}",
                "android.runtime.maxMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":9223372036854775808}}}",
                "android.runtime.maxMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":1.5}}}",
                "android.runtime.maxMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"maxMemoryBytes\":[]}}}",
                "android.runtime.maxMemoryBytes");
    }

    @Test
    public void testAndroidRuntimeTotalMemoryBytesConfig() {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidRuntimeTotalMemoryBytesConfigured());

        TraceEnvironmentConfig runtimeEmpty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{}}}");
        assertFalse(runtimeEmpty.isAndroidRuntimeTotalMemoryBytesConfigured());
        assertFalse(runtimeEmpty.isAndroidRuntimeMaxMemoryBytesConfigured());

        TraceEnvironmentConfig mmOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"maxMemoryBytes\":268435456}}}");
        assertFalse(mmOnly.isAndroidRuntimeTotalMemoryBytesConfigured());
        assertTrue(mmOnly.isAndroidRuntimeMaxMemoryBytesConfigured());

        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"totalMemoryBytes\":134217728}}}");
        assertTrue(full.isAndroidRuntimeTotalMemoryBytesConfigured());
        assertEquals(134217728L, full.getAndroidRuntimeTotalMemoryBytes());
        assertFalse(full.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertFalse(full.isAndroidRuntimeAvailableProcessorsConfigured());

        TraceEnvironmentConfig min = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"totalMemoryBytes\":1}}}");
        assertTrue(min.isAndroidRuntimeTotalMemoryBytesConfigured());
        assertEquals(1L, min.getAndroidRuntimeTotalMemoryBytes());

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"totalMemoryBytes\":" + Long.MAX_VALUE + "}}}");
        assertTrue(max.isAndroidRuntimeTotalMemoryBytesConfigured());
        assertEquals(Long.MAX_VALUE, max.getAndroidRuntimeTotalMemoryBytes());

        TraceEnvironmentConfig bothOk = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"cpu\":{\"configuredProcessorCount\":4,\"onlineProcessorCount\":2}},"
                + "\"android\":{\"runtime\":{"
                + "\"systemProperties\":{\"os.name\":\"Linux\"},"
                + "\"availableProcessors\":16,"
                + "\"maxMemoryBytes\":512,"
                + "\"totalMemoryBytes\":256"
                + "}}"
                + "}");
        assertTrue(bothOk.isAndroidRuntimeTotalMemoryBytesConfigured());
        assertEquals(256L, bothOk.getAndroidRuntimeTotalMemoryBytes());
        assertTrue(bothOk.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertEquals(512L, bothOk.getAndroidRuntimeMaxMemoryBytes());
        assertTrue(bothOk.isAndroidRuntimeAvailableProcessorsConfigured());
        assertEquals(16, bothOk.getAndroidRuntimeAvailableProcessors());
        assertTrue(bothOk.isAndroidRuntimeSystemPropertiesConfigured());
        assertTrue(bothOk.isLinuxCpuConfigured());
        assertEquals(4, bothOk.getLinuxCpuConfig().getConfiguredProcessorCount());

        TraceEnvironmentConfig equal = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"totalMemoryBytes\":512,\"maxMemoryBytes\":512}}}");
        assertEquals(512L, equal.getAndroidRuntimeTotalMemoryBytes());
        assertEquals(512L, equal.getAndroidRuntimeMaxMemoryBytes());

        assertInvalid("{\"android\":{\"runtime\":{\"totalMemoryBytes\":null}}}",
                "android.runtime.totalMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"totalMemoryBytes\":true}}}",
                "android.runtime.totalMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"totalMemoryBytes\":\"134217728\"}}}",
                "android.runtime.totalMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"totalMemoryBytes\":0}}}",
                "android.runtime.totalMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"totalMemoryBytes\":-1}}}",
                "android.runtime.totalMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"totalMemoryBytes\":9223372036854775808}}}",
                "android.runtime.totalMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"totalMemoryBytes\":1.5}}}",
                "android.runtime.totalMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"totalMemoryBytes\":[]}}}",
                "android.runtime.totalMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"totalMemoryBytes\":513,\"maxMemoryBytes\":512}}}",
                "android.runtime.totalMemoryBytes");
    }

    @Test
    public void testAndroidRuntimeFreeMemoryBytesConfig() {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidRuntimeFreeMemoryBytesConfigured());

        TraceEnvironmentConfig runtimeEmpty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{}}}");
        assertFalse(runtimeEmpty.isAndroidRuntimeFreeMemoryBytesConfigured());
        assertFalse(runtimeEmpty.isAndroidRuntimeTotalMemoryBytesConfigured());

        TraceEnvironmentConfig tmOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"totalMemoryBytes\":134217728}}}");
        assertFalse(tmOnly.isAndroidRuntimeFreeMemoryBytesConfigured());
        assertTrue(tmOnly.isAndroidRuntimeTotalMemoryBytesConfigured());

        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"freeMemoryBytes\":67108864,"
                        + "\"totalMemoryBytes\":134217728}}}");
        assertTrue(full.isAndroidRuntimeFreeMemoryBytesConfigured());
        assertEquals(67108864L, full.getAndroidRuntimeFreeMemoryBytes());
        assertTrue(full.isAndroidRuntimeTotalMemoryBytesConfigured());
        assertEquals(134217728L, full.getAndroidRuntimeTotalMemoryBytes());
        assertFalse(full.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertFalse(full.isAndroidRuntimeAvailableProcessorsConfigured());

        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"freeMemoryBytes\":0,\"totalMemoryBytes\":1}}}");
        assertTrue(zero.isAndroidRuntimeFreeMemoryBytesConfigured());
        assertEquals(0L, zero.getAndroidRuntimeFreeMemoryBytes());

        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"freeMemoryBytes\":" + Long.MAX_VALUE
                        + ",\"totalMemoryBytes\":" + Long.MAX_VALUE + "}}}");
        assertTrue(max.isAndroidRuntimeFreeMemoryBytesConfigured());
        assertEquals(Long.MAX_VALUE, max.getAndroidRuntimeFreeMemoryBytes());

        TraceEnvironmentConfig bothOk = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"cpu\":{\"configuredProcessorCount\":4,\"onlineProcessorCount\":2}},"
                + "\"android\":{\"runtime\":{"
                + "\"systemProperties\":{\"os.name\":\"Linux\"},"
                + "\"availableProcessors\":16,"
                + "\"maxMemoryBytes\":512,"
                + "\"totalMemoryBytes\":256,"
                + "\"freeMemoryBytes\":128"
                + "}}"
                + "}");
        assertTrue(bothOk.isAndroidRuntimeFreeMemoryBytesConfigured());
        assertEquals(128L, bothOk.getAndroidRuntimeFreeMemoryBytes());
        assertTrue(bothOk.isAndroidRuntimeTotalMemoryBytesConfigured());
        assertEquals(256L, bothOk.getAndroidRuntimeTotalMemoryBytes());
        assertTrue(bothOk.isAndroidRuntimeMaxMemoryBytesConfigured());
        assertEquals(512L, bothOk.getAndroidRuntimeMaxMemoryBytes());
        assertTrue(bothOk.isAndroidRuntimeAvailableProcessorsConfigured());
        assertEquals(16, bothOk.getAndroidRuntimeAvailableProcessors());
        assertTrue(bothOk.isAndroidRuntimeSystemPropertiesConfigured());
        assertTrue(bothOk.isLinuxCpuConfigured());
        assertEquals(4, bothOk.getLinuxCpuConfig().getConfiguredProcessorCount());

        TraceEnvironmentConfig equal = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"freeMemoryBytes\":512,"
                        + "\"totalMemoryBytes\":512,\"maxMemoryBytes\":512}}}");
        assertEquals(512L, equal.getAndroidRuntimeFreeMemoryBytes());
        assertEquals(512L, equal.getAndroidRuntimeTotalMemoryBytes());
        assertEquals(512L, equal.getAndroidRuntimeMaxMemoryBytes());

        assertInvalid("{\"android\":{\"runtime\":{\"freeMemoryBytes\":1}}}",
                "android.runtime.freeMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"freeMemoryBytes\":129,\"totalMemoryBytes\":128}}}",
                "android.runtime.freeMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"freeMemoryBytes\":1,\"maxMemoryBytes\":512}}}",
                "android.runtime.freeMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"freeMemoryBytes\":1,"
                        + "\"totalMemoryBytes\":513,\"maxMemoryBytes\":512}}}",
                "android.runtime.totalMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"freeMemoryBytes\":null,\"totalMemoryBytes\":128}}}",
                "android.runtime.freeMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"freeMemoryBytes\":true,\"totalMemoryBytes\":128}}}",
                "android.runtime.freeMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"freeMemoryBytes\":\"64\",\"totalMemoryBytes\":128}}}",
                "android.runtime.freeMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"freeMemoryBytes\":-1,\"totalMemoryBytes\":128}}}",
                "android.runtime.freeMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"freeMemoryBytes\":9223372036854775808,"
                        + "\"totalMemoryBytes\":128}}}",
                "android.runtime.freeMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"freeMemoryBytes\":1.5,\"totalMemoryBytes\":128}}}",
                "android.runtime.freeMemoryBytes");
        assertInvalid("{\"android\":{\"runtime\":{\"freeMemoryBytes\":[],\"totalMemoryBytes\":128}}}",
                "android.runtime.freeMemoryBytes");
    }

    @Test
    public void testAndroidRuntimeEnvironmentVariablesConfig() {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidRuntimeEnvironmentVariablesConfigured());
        assertTrue(missing.getAndroidRuntimeEnvironmentVariables().isEmpty());
        assertFalse(missing.isAndroidRuntimeEnvironmentVariableConfigured("PATH"));

        TraceEnvironmentConfig runtimeEmpty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{}}}");
        assertFalse(runtimeEmpty.isAndroidRuntimeEnvironmentVariablesConfigured());
        assertFalse(runtimeEmpty.isAndroidRuntimeSystemPropertiesConfigured());

        // empty map configured
        TraceEnvironmentConfig emptyEnv = TraceEnvironmentConfig.parse(
                "{\"android\":{\"runtime\":{\"environmentVariables\":{}}}}");
        assertTrue(emptyEnv.isAndroidRuntimeEnvironmentVariablesConfigured());
        assertTrue(emptyEnv.getAndroidRuntimeEnvironmentVariables().isEmpty());
        assertFalse(emptyEnv.isAndroidRuntimeSystemPropertiesConfigured());

        // full + null + empty string
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"runtime\":{\"environmentVariables\":{"
                + "\"PATH\":\"/system/bin\","
                + "\"TMPDIR\":null,"
                + "\"EMPTY\":\"\""
                + "}}}}"
        );
        assertTrue(full.isAndroidRuntimeEnvironmentVariablesConfigured());
        assertEquals(3, full.getAndroidRuntimeEnvironmentVariables().size());
        assertEquals("/system/bin", full.getAndroidRuntimeEnvironmentVariable("PATH"));
        assertTrue(full.isAndroidRuntimeEnvironmentVariableConfigured("TMPDIR"));
        assertNull(full.getAndroidRuntimeEnvironmentVariable("TMPDIR"));
        assertEquals("", full.getAndroidRuntimeEnvironmentVariable("EMPTY"));
        assertFalse(full.isAndroidRuntimeEnvironmentVariableConfigured("HOME"));
        // independent of systemProperties
        assertFalse(full.isAndroidRuntimeSystemPropertiesConfigured());
        assertFalse(full.isAndroidRuntimeSystemPropertyConfigured("PATH"));

        // coexist with systemProperties; maps independent
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"runtime\":{"
                + "\"systemProperties\":{\"os.name\":\"Linux\"},"
                + "\"environmentVariables\":{\"PATH\":\"/vendor/bin\"}"
                + "}}}"
        );
        assertTrue(both.isAndroidRuntimeSystemPropertiesConfigured());
        assertEquals("Linux", both.getAndroidRuntimeSystemProperty("os.name"));
        assertFalse(both.isAndroidRuntimeSystemPropertyConfigured("PATH"));
        assertTrue(both.isAndroidRuntimeEnvironmentVariablesConfigured());
        assertEquals("/vendor/bin", both.getAndroidRuntimeEnvironmentVariable("PATH"));
        assertFalse(both.isAndroidRuntimeEnvironmentVariableConfigured("os.name"));

        // independent of linux.environ
        TraceEnvironmentConfig withLinux = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"environ\":[\"PATH=/linux/environ\"]},"
                + "\"android\":{\"runtime\":{\"environmentVariables\":{"
                + "\"PATH\":\"/android/runtime\""
                + "}}}}"
        );
        assertTrue(withLinux.isLinuxEnvironConfigured());
        assertEquals(1, withLinux.getLinuxEnviron().size());
        assertEquals("PATH=/linux/environ", withLinux.getLinuxEnviron().get(0));
        assertEquals("/android/runtime",
                withLinux.getAndroidRuntimeEnvironmentVariable("PATH"));

        try {
            full.getAndroidRuntimeEnvironmentVariables().put("x", "y");
            fail("expected UnsupportedOperationException for environmentVariables map");
        } catch (UnsupportedOperationException expected) {
            // ok
        }

        assertInvalid("{\"android\":{\"runtime\":{\"environmentVariables\":[]}}}",
                "android.runtime.environmentVariables");
        assertInvalid("{\"android\":{\"runtime\":{\"environmentVariables\":1}}}",
                "android.runtime.environmentVariables");
        assertInvalid("{\"android\":{\"runtime\":{\"environmentVariables\":null}}}",
                "android.runtime.environmentVariables");
        assertInvalid("{\"android\":{\"runtime\":{\"environmentVariables\":{\"\": \"x\"}}}}",
                "android.runtime.environmentVariables.");
        assertInvalid("{\"android\":{\"runtime\":{\"environmentVariables\":{\"a\\nb\": \"x\"}}}}",
                "android.runtime.environmentVariables.");
        assertInvalid("{\"android\":{\"runtime\":{\"environmentVariables\":{\"k\":1}}}}",
                "android.runtime.environmentVariables.k");
        assertInvalid("{\"android\":{\"runtime\":{\"environmentVariables\":{\"k\":true}}}}",
                "android.runtime.environmentVariables.k");
    }

    @Test
    public void testAndroidBuildTimeConfig() {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidBuildTimeConfigured());
        assertNull(missing.getAndroidBuildTime());

        TraceEnvironmentConfig buildNoKey = TraceEnvironmentConfig.parse(
                "{\"android\":{\"build\":{\"MODEL\":\"Pixel 6\"}}}");
        assertFalse(buildNoKey.isAndroidBuildTimeConfigured());
        assertNull(buildNoKey.getAndroidBuildTime());
        assertEquals("Pixel 6", buildNoKey.getAndroidBuildString("MODEL"));

        // zero allowed
        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse(
                "{\"android\":{\"build\":{\"TIME\":0}}}");
        assertTrue(zero.isAndroidBuildTimeConfigured());
        assertEquals(Long.valueOf(0L), zero.getAndroidBuildTime());
        // TIME must not appear via generic string path
        assertNull(zero.getAndroidBuildString("TIME"));
        assertNull(zero.getAndroidBuildInt("TIME"));

        // positive
        TraceEnvironmentConfig positive = TraceEnvironmentConfig.parse(
                "{\"android\":{\"build\":{\"TIME\":1640995200000,\"MODEL\":\"Pixel 6\"}}}");
        assertTrue(positive.isAndroidBuildTimeConfigured());
        assertEquals(Long.valueOf(1640995200000L), positive.getAndroidBuildTime());
        assertEquals("Pixel 6", positive.getAndroidBuildString("MODEL"));
        assertNull(positive.getAndroidBuildString("TIME"));

        // invalid types / bounds
        assertInvalid("{\"android\":{\"build\":{\"TIME\":null}}}", "android.build.TIME");
        assertInvalid("{\"android\":{\"build\":{\"TIME\":\"0\"}}}", "android.build.TIME");
        assertInvalid("{\"android\":{\"build\":{\"TIME\":true}}}", "android.build.TIME");
        assertInvalid("{\"android\":{\"build\":{\"TIME\":1.5}}}", "android.build.TIME");
        assertInvalid("{\"android\":{\"build\":{\"TIME\":-1}}}", "android.build.TIME");
    }

    @Test
    public void testAndroidBuildSupportedAbisConfig() {
        // missing build / missing key
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidBuildSupportedAbisConfigured());
        assertNull(missing.getAndroidBuildSupportedAbis());
        assertFalse(missing.isAndroidBuildSupported32BitAbisConfigured());
        assertNull(missing.getAndroidBuildSupported32BitAbis());
        TraceEnvironmentConfig buildNoKey = TraceEnvironmentConfig.parse(
                "{\"android\":{\"build\":{\"MODEL\":\"Pixel 6\"}}}");
        assertFalse(buildNoKey.isAndroidBuildSupportedAbisConfigured());
        assertNull(buildNoKey.getAndroidBuildSupportedAbis());
        assertFalse(buildNoKey.isAndroidBuildSupported32BitAbisConfigured());
        assertEquals("Pixel 6", buildNoKey.getAndroidBuildString("MODEL"));

        // valid multi-entry order + trim + coexist with scalars
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"build\":{"
                + "\"MODEL\":\"Pixel 6\","
                + "\"SUPPORTED_ABIS\":[\" arm64-v8a \",\"armeabi-v7a\",\"armeabi\"]"
                + "}}}"
        );
        assertTrue(full.isAndroidBuildSupportedAbisConfigured());
        List<String> abis = full.getAndroidBuildSupportedAbis();
        assertNotNull(abis);
        assertEquals(3, abis.size());
        assertEquals("arm64-v8a", abis.get(0));
        assertEquals("armeabi-v7a", abis.get(1));
        assertEquals("armeabi", abis.get(2));
        assertEquals("Pixel 6", full.getAndroidBuildString("MODEL"));
        // string accessor does not coerce array fields
        assertNull(full.getAndroidBuildString("SUPPORTED_ABIS"));
        // 32-bit independent: not inferred from SUPPORTED_ABIS
        assertFalse(full.isAndroidBuildSupported32BitAbisConfigured());
        assertNull(full.getAndroidBuildSupported32BitAbis());
        // immutable / stable
        assertEquals(abis, full.getAndroidBuildSupportedAbis());
        try {
            abis.add("x86");
            fail("expected UnsupportedOperationException for immutable abis list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }

        // single element
        TraceEnvironmentConfig single = TraceEnvironmentConfig.parse(
                "{\"android\":{\"build\":{\"SUPPORTED_ABIS\":[\"arm64-v8a\"]}}}");
        assertTrue(single.isAndroidBuildSupportedAbisConfigured());
        assertEquals(1, single.getAndroidBuildSupportedAbis().size());
        assertEquals("arm64-v8a", single.getAndroidBuildSupportedAbis().get(0));

        // null / non-array / empty
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_ABIS\":null}}}",
                "android.build.SUPPORTED_ABIS");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_ABIS\":\"arm64-v8a\"}}}",
                "android.build.SUPPORTED_ABIS");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_ABIS\":1}}}",
                "android.build.SUPPORTED_ABIS");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_ABIS\":{}}}}",
                "android.build.SUPPORTED_ABIS");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_ABIS\":[]}}}",
                "android.build.SUPPORTED_ABIS");

        // non-string / blank elements (indexed path)
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_ABIS\":[null]}}}",
                "android.build.SUPPORTED_ABIS[0]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_ABIS\":[1]}}}",
                "android.build.SUPPORTED_ABIS[0]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_ABIS\":[true]}}}",
                "android.build.SUPPORTED_ABIS[0]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_ABIS\":[\"\"]}}}",
                "android.build.SUPPORTED_ABIS[0]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_ABIS\":[\"   \"]}}}",
                "android.build.SUPPORTED_ABIS[0]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_ABIS\":[\"arm64-v8a\",null]}}}",
                "android.build.SUPPORTED_ABIS[1]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_ABIS\":[\"arm64-v8a\",\"\"]}}}",
                "android.build.SUPPORTED_ABIS[1]");
    }

    @Test
    public void testAndroidBuildSupported32BitAbisConfig() {
        // missing
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidBuildSupported32BitAbisConfigured());
        assertNull(missing.getAndroidBuildSupported32BitAbis());

        // only SUPPORTED_ABIS does not configure 32-bit
        TraceEnvironmentConfig onlyAll = TraceEnvironmentConfig.parse(
                "{\"android\":{\"build\":{\"SUPPORTED_ABIS\":[\"arm64-v8a\",\"armeabi-v7a\"]}}}");
        assertTrue(onlyAll.isAndroidBuildSupportedAbisConfigured());
        assertFalse(onlyAll.isAndroidBuildSupported32BitAbisConfigured());
        assertNull(onlyAll.getAndroidBuildSupported32BitAbis());

        // only 32-bit does not configure SUPPORTED_ABIS
        TraceEnvironmentConfig only32 = TraceEnvironmentConfig.parse(
                "{\"android\":{\"build\":{\"SUPPORTED_32_BIT_ABIS\":[\" armeabi-v7a \",\"armeabi\"]}}}");
        assertFalse(only32.isAndroidBuildSupportedAbisConfigured());
        assertNull(only32.getAndroidBuildSupportedAbis());
        assertTrue(only32.isAndroidBuildSupported32BitAbisConfigured());
        List<String> abis32 = only32.getAndroidBuildSupported32BitAbis();
        assertNotNull(abis32);
        assertEquals(2, abis32.size());
        assertEquals("armeabi-v7a", abis32.get(0));
        assertEquals("armeabi", abis32.get(1));
        assertNull(only32.getAndroidBuildString("SUPPORTED_32_BIT_ABIS"));
        try {
            abis32.add("x86");
            fail("expected UnsupportedOperationException for immutable 32-bit abis list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }

        // both independent
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"build\":{"
                + "\"SUPPORTED_ABIS\":[\"arm64-v8a\",\"armeabi-v7a\"],"
                + "\"SUPPORTED_32_BIT_ABIS\":[\"armeabi-v7a\",\"armeabi\"]"
                + "}}}"
        );
        assertTrue(both.isAndroidBuildSupportedAbisConfigured());
        assertEquals(2, both.getAndroidBuildSupportedAbis().size());
        assertEquals("arm64-v8a", both.getAndroidBuildSupportedAbis().get(0));
        assertTrue(both.isAndroidBuildSupported32BitAbisConfigured());
        assertEquals(2, both.getAndroidBuildSupported32BitAbis().size());
        assertEquals("armeabi-v7a", both.getAndroidBuildSupported32BitAbis().get(0));
        assertEquals("armeabi", both.getAndroidBuildSupported32BitAbis().get(1));

        // invalid parsing paths
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_32_BIT_ABIS\":null}}}",
                "android.build.SUPPORTED_32_BIT_ABIS");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_32_BIT_ABIS\":\"armeabi-v7a\"}}}",
                "android.build.SUPPORTED_32_BIT_ABIS");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_32_BIT_ABIS\":1}}}",
                "android.build.SUPPORTED_32_BIT_ABIS");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_32_BIT_ABIS\":[]}}}",
                "android.build.SUPPORTED_32_BIT_ABIS");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_32_BIT_ABIS\":[null]}}}",
                "android.build.SUPPORTED_32_BIT_ABIS[0]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_32_BIT_ABIS\":[1]}}}",
                "android.build.SUPPORTED_32_BIT_ABIS[0]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_32_BIT_ABIS\":[\"\"]}}}",
                "android.build.SUPPORTED_32_BIT_ABIS[0]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_32_BIT_ABIS\":[\"  \"]}}}",
                "android.build.SUPPORTED_32_BIT_ABIS[0]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_32_BIT_ABIS\":[\"armeabi\",null]}}}",
                "android.build.SUPPORTED_32_BIT_ABIS[1]");
    }

    @Test
    public void testAndroidBuildSupported64BitAbisConfig() {
        // missing
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidBuildSupported64BitAbisConfigured());
        assertNull(missing.getAndroidBuildSupported64BitAbis());

        // only SUPPORTED_ABIS does not configure 64-bit
        TraceEnvironmentConfig onlyAll = TraceEnvironmentConfig.parse(
                "{\"android\":{\"build\":{\"SUPPORTED_ABIS\":[\"arm64-v8a\",\"armeabi-v7a\"]}}}");
        assertTrue(onlyAll.isAndroidBuildSupportedAbisConfigured());
        assertFalse(onlyAll.isAndroidBuildSupported64BitAbisConfigured());
        assertNull(onlyAll.getAndroidBuildSupported64BitAbis());

        // only 32-bit does not configure 64-bit
        TraceEnvironmentConfig only32 = TraceEnvironmentConfig.parse(
                "{\"android\":{\"build\":{\"SUPPORTED_32_BIT_ABIS\":[\"armeabi-v7a\"]}}}");
        assertTrue(only32.isAndroidBuildSupported32BitAbisConfigured());
        assertFalse(only32.isAndroidBuildSupported64BitAbisConfigured());
        assertNull(only32.getAndroidBuildSupported64BitAbis());

        // only 64-bit does not configure SUPPORTED_ABIS / 32-bit
        TraceEnvironmentConfig only64 = TraceEnvironmentConfig.parse(
                "{\"android\":{\"build\":{\"SUPPORTED_64_BIT_ABIS\":[\" arm64-v8a \"]}}}");
        assertFalse(only64.isAndroidBuildSupportedAbisConfigured());
        assertNull(only64.getAndroidBuildSupportedAbis());
        assertFalse(only64.isAndroidBuildSupported32BitAbisConfigured());
        assertNull(only64.getAndroidBuildSupported32BitAbis());
        assertTrue(only64.isAndroidBuildSupported64BitAbisConfigured());
        List<String> abis64 = only64.getAndroidBuildSupported64BitAbis();
        assertNotNull(abis64);
        assertEquals(1, abis64.size());
        assertEquals("arm64-v8a", abis64.get(0));
        assertNull(only64.getAndroidBuildString("SUPPORTED_64_BIT_ABIS"));
        try {
            abis64.add("x86_64");
            fail("expected UnsupportedOperationException for immutable 64-bit abis list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }

        // three-way independence
        TraceEnvironmentConfig allThree = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"build\":{"
                + "\"SUPPORTED_ABIS\":[\"arm64-v8a\",\"armeabi-v7a\"],"
                + "\"SUPPORTED_32_BIT_ABIS\":[\"armeabi-v7a\",\"armeabi\"],"
                + "\"SUPPORTED_64_BIT_ABIS\":[\"arm64-v8a\"]"
                + "}}}"
        );
        assertTrue(allThree.isAndroidBuildSupportedAbisConfigured());
        assertEquals(2, allThree.getAndroidBuildSupportedAbis().size());
        assertEquals("arm64-v8a", allThree.getAndroidBuildSupportedAbis().get(0));
        assertTrue(allThree.isAndroidBuildSupported32BitAbisConfigured());
        assertEquals(2, allThree.getAndroidBuildSupported32BitAbis().size());
        assertEquals("armeabi-v7a", allThree.getAndroidBuildSupported32BitAbis().get(0));
        assertTrue(allThree.isAndroidBuildSupported64BitAbisConfigured());
        assertEquals(1, allThree.getAndroidBuildSupported64BitAbis().size());
        assertEquals("arm64-v8a", allThree.getAndroidBuildSupported64BitAbis().get(0));

        // invalid parsing paths
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_64_BIT_ABIS\":null}}}",
                "android.build.SUPPORTED_64_BIT_ABIS");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_64_BIT_ABIS\":\"arm64-v8a\"}}}",
                "android.build.SUPPORTED_64_BIT_ABIS");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_64_BIT_ABIS\":1}}}",
                "android.build.SUPPORTED_64_BIT_ABIS");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_64_BIT_ABIS\":[]}}}",
                "android.build.SUPPORTED_64_BIT_ABIS");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_64_BIT_ABIS\":[null]}}}",
                "android.build.SUPPORTED_64_BIT_ABIS[0]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_64_BIT_ABIS\":[1]}}}",
                "android.build.SUPPORTED_64_BIT_ABIS[0]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_64_BIT_ABIS\":[\"\"]}}}",
                "android.build.SUPPORTED_64_BIT_ABIS[0]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_64_BIT_ABIS\":[\"  \"]}}}",
                "android.build.SUPPORTED_64_BIT_ABIS[0]");
        assertInvalid("{\"android\":{\"build\":{\"SUPPORTED_64_BIT_ABIS\":[\"arm64-v8a\",null]}}}",
                "android.build.SUPPORTED_64_BIT_ABIS[1]");
    }

    @Test
    public void testNetworkInterfacesConfig() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"flags\":73},"
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\",\"broadcast\":\"192.168.1.255\","
                + "\"mac\":\"02:00:00:00:00:01\",\"mtu\":1500},"
                + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\",\"mac\":\"AA:BB:CC:DD:EE:FF\",\"mtu\":9000}"
                + "]}}"
        );
        assertTrue(config.isNetworkInterfacesConfigured());
        assertTrue(config.hasNetworkInterfaces());
        List<TraceEnvironmentConfig.NetworkInterfaceConfig> list = config.getNetworkInterfaces();
        assertEquals(3, list.size());

        TraceEnvironmentConfig.NetworkInterfaceConfig lo = list.get(0);
        assertEquals("lo", lo.getName());
        assertEquals(1, lo.getIndex());
        assertEquals("127.0.0.1", lo.getIpv4());
        assertNull(lo.getBroadcast());
        assertEquals(Integer.valueOf(73), lo.getFlags());
        assertNull(lo.getMac());
        assertNull(lo.getMtu());
        assertFalse(lo.isDisplayNameConfigured());
        assertNull(lo.getDisplayName());
        assertFalse(lo.isVirtualConfigured());
        assertFalse(lo.isVirtual());

        TraceEnvironmentConfig.NetworkInterfaceConfig wlan0 = list.get(1);
        assertEquals("wlan0", wlan0.getName());
        assertEquals(2, wlan0.getIndex());
        assertEquals("192.168.1.100", wlan0.getIpv4());
        assertEquals("192.168.1.255", wlan0.getBroadcast());
        assertNull(wlan0.getFlags());
        assertEquals("02:00:00:00:00:01", wlan0.getMac());
        assertEquals(Integer.valueOf(1500), wlan0.getMtu());
        assertFalse(wlan0.isDisplayNameConfigured());
        assertFalse(wlan0.isVirtualConfigured());

        // uppercase MAC is normalized to Locale.ROOT lowercase
        TraceEnvironmentConfig.NetworkInterfaceConfig eth0 = list.get(2);
        assertEquals("aa:bb:cc:dd:ee:ff", eth0.getMac());
        assertEquals(Integer.valueOf(9000), eth0.getMtu());
        assertFalse(eth0.isVirtualConfigured());

        // displayName: string / explicit null / omitted (never inferred from name)
        TraceEnvironmentConfig displayNames = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"displayName\":\"Loopback\"},"
                + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.1\",\"displayName\":null},"
                + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\"}"
                + "]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig d0 =
                displayNames.getNetworkInterfaces().get(0);
        assertTrue(d0.isDisplayNameConfigured());
        assertEquals("Loopback", d0.getDisplayName());
        TraceEnvironmentConfig.NetworkInterfaceConfig d1 =
                displayNames.getNetworkInterfaces().get(1);
        assertTrue(d1.isDisplayNameConfigured());
        assertNull(d1.getDisplayName());
        TraceEnvironmentConfig.NetworkInterfaceConfig d2 =
                displayNames.getNetworkInterfaces().get(2);
        assertFalse(d2.isDisplayNameConfigured());
        assertNull(d2.getDisplayName());
        // no inference from name
        assertEquals("eth0", d2.getName());

        // virtual: true / false / omitted; independent of flags/name
        TraceEnvironmentConfig virtuals = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\",\"virtual\":false},"
                + "{\"name\":\"veth0\",\"index\":2,\"ipv4\":\"10.0.0.1\",\"virtual\":true,\"flags\":1},"
                + "{\"name\":\"wlan0\",\"index\":3,\"ipv4\":\"192.168.1.1\"}"
                + "]}}"
        );
        TraceEnvironmentConfig.NetworkInterfaceConfig v0 =
                virtuals.getNetworkInterfaces().get(0);
        assertTrue(v0.isVirtualConfigured());
        assertFalse(v0.isVirtual());
        TraceEnvironmentConfig.NetworkInterfaceConfig v1 =
                virtuals.getNetworkInterfaces().get(1);
        assertTrue(v1.isVirtualConfigured());
        assertTrue(v1.isVirtual());
        assertEquals(Integer.valueOf(1), v1.getFlags());
        TraceEnvironmentConfig.NetworkInterfaceConfig v2 =
                virtuals.getNetworkInterfaces().get(2);
        assertFalse(v2.isVirtualConfigured());
        assertFalse(v2.isVirtual());

        try {
            list.add(lo);
            fail("expected UnsupportedOperationException on list.add");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable
        }

        assertFalse(TraceEnvironmentConfig.parse("{}").isNetworkInterfacesConfigured());
        assertFalse(TraceEnvironmentConfig.parse("{\"network\":{}}").isNetworkInterfacesConfigured());

        TraceEnvironmentConfig emptyIfs = TraceEnvironmentConfig.parse("{\"network\":{\"interfaces\":[]}}");
        assertTrue(emptyIfs.isNetworkInterfacesConfigured());
        assertFalse(emptyIfs.hasNetworkInterfaces());
        assertTrue(emptyIfs.getNetworkInterfaces().isEmpty());
        try {
            emptyIfs.getNetworkInterfaces().add(lo);
            fail("expected UnsupportedOperationException on empty list.add");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable
        }
    }

    @Test
    public void testNetworkInterfacesValidation() {
        assertInvalid("{\"network\":{\"interfaces\":{}}}", "network.interfaces");
        assertInvalid("{\"network\":{\"interfaces\":[\"wlan0\"]}}", "network.interfaces[0]");

        assertInvalid("{\"network\":{\"interfaces\":[{\"index\":1,\"ipv4\":\"127.0.0.1\"}]}}",
                "network.interfaces[0].name");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":1,\"index\":1,\"ipv4\":\"127.0.0.1\"}]}}",
                "network.interfaces[0].name");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"\",\"index\":1,\"ipv4\":\"127.0.0.1\"}]}}",
                "network.interfaces[0].name");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\" lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"}]}}",
                "network.interfaces[0].name");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo \",\"index\":1,\"ipv4\":\"127.0.0.1\"}]}}",
                "network.interfaces[0].name");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"abcdefghijklmnop\",\"index\":1,\"ipv4\":\"127.0.0.1\"}]}}",
                "network.interfaces[0].name");

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"ipv4\":\"127.0.0.1\"}]}}",
                "network.interfaces[0].index");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1.5,\"ipv4\":\"127.0.0.1\"}]}}",
                "network.interfaces[0].index");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":0,\"ipv4\":\"127.0.0.1\"}]}}",
                "network.interfaces[0].index");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":\"-1\",\"ipv4\":\"127.0.0.1\"}]}}",
                "network.interfaces[0].index");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":true,\"ipv4\":\"127.0.0.1\"}]}}",
                "network.interfaces[0].index");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":99999999999999999999999999999,"
                        + "\"ipv4\":\"127.0.0.1\"}]}}",
                "network.interfaces[0].index");

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1}]}}",
                "network.interfaces[0].ipv4");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":123}]}}",
                "network.interfaces[0].ipv4");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"localhost\"}]}}",
                "network.interfaces[0].ipv4");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\" 127.0.0.1\"}]}}",
                "network.interfaces[0].ipv4");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.256\"}]}}",
                "network.interfaces[0].ipv4");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.-1\"}]}}",
                "network.interfaces[0].ipv4");

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"broadcast\":\"bad\"}]}}",
                "network.interfaces[0].broadcast");

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"flags\":1.5}]}}",
                "network.interfaces[0].flags");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"flags\":-1}]}}",
                "network.interfaces[0].flags");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"flags\":65536}]}}",
                "network.interfaces[0].flags");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"flags\":99999999999999999999999999999}]}}",
                "network.interfaces[0].flags");

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"mac\":1}]}}",
                "network.interfaces[0].mac");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"mac\":\"02:00:00:00:00\"}]}}",
                "network.interfaces[0].mac");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"mac\":\"02:00:00:00:00:GG\"}]}}",
                "network.interfaces[0].mac");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"mac\":\"02 00 00 00 00 01\"}]}}",
                "network.interfaces[0].mac");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"mac\":\" 02:00:00:00:00:01\"}]}}",
                "network.interfaces[0].mac");

        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"mtu\":1.5}]}}",
                "network.interfaces[0].mtu");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"mtu\":67}]}}",
                "network.interfaces[0].mtu");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"mtu\":65537}]}}",
                "network.interfaces[0].mtu");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"mtu\":99999999999999999999999999999}]}}",
                "network.interfaces[0].mtu");

        // displayName validation
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"displayName\":1}]}}",
                "network.interfaces[0].displayName");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"displayName\":true}]}}",
                "network.interfaces[0].displayName");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"displayName\":\"\"}]}}",
                "network.interfaces[0].displayName");
        StringBuilder longDn = new StringBuilder();
        for (int i = 0; i < 129; i++) {
            longDn.append('x');
        }
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"displayName\":\"" + longDn + "\"}]}}",
                "network.interfaces[0].displayName");
        // boundary: 128 chars ok
        StringBuilder ok128 = new StringBuilder();
        for (int i = 0; i < 128; i++) {
            ok128.append('y');
        }
        TraceEnvironmentConfig okDn = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                + "\"displayName\":\"" + ok128 + "\"}]}}");
        assertTrue(okDn.getNetworkInterfaces().get(0).isDisplayNameConfigured());
        assertEquals(128, okDn.getNetworkInterfaces().get(0).getDisplayName().length());

        // virtual: strict Boolean only
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"virtual\":null}]}}",
                "network.interfaces[0].virtual");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"virtual\":1}]}}",
                "network.interfaces[0].virtual");
        assertInvalid("{\"network\":{\"interfaces\":[{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\","
                        + "\"virtual\":\"true\"}]}}",
                "network.interfaces[0].virtual");

        assertInvalid("{\"network\":{\"interfaces\":["
                        + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"},"
                        + "{\"name\":\"lo\",\"index\":2,\"ipv4\":\"10.0.0.1\"}"
                        + "]}}",
                "network.interfaces[1].name");
        assertInvalid("{\"network\":{\"interfaces\":["
                        + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"},"
                        + "{\"name\":\"wlan0\",\"index\":1,\"ipv4\":\"10.0.0.1\"}"
                        + "]}}",
                "network.interfaces[1].index");
    }

    @Test
    public void testAndroidSettingsConfig() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"settings\":{"
                + "\"secure\":{\"android_id\":\"a1b2c3d4e5f67890\",\"explicit_null\":null},"
                + "\"system\":{\"screen_brightness\":\"128\"},"
                + "\"global\":{\"adb_enabled\":\"0\"}"
                + "}}}"
        );

        assertTrue(config.isAndroidSettingConfigured("secure", "android_id"));
        assertEquals("a1b2c3d4e5f67890", config.getAndroidSettingString("secure", "android_id"));
        assertTrue(config.isAndroidSettingConfigured("system", "screen_brightness"));
        assertEquals("128", config.getAndroidSettingString("system", "screen_brightness"));
        assertTrue(config.isAndroidSettingConfigured("global", "adb_enabled"));
        assertEquals("0", config.getAndroidSettingString("global", "adb_enabled"));

        // missing key
        assertFalse(config.isAndroidSettingConfigured("secure", "missing_key"));
        assertNull(config.getAndroidSettingString("secure", "missing_key"));

        // explicit JSON null: configured but get returns null
        assertTrue(config.isAndroidSettingConfigured("secure", "explicit_null"));
        assertNull(config.getAndroidSettingString("secure", "explicit_null"));

        // settings entirely missing
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{}");
        assertFalse(empty.isAndroidSettingConfigured("secure", "android_id"));
        assertNull(empty.getAndroidSettingString("secure", "android_id"));
    }

    @Test
    public void testAndroidSettingsValidation() {
        assertInvalid("{\"android\":{\"settings\":[]}}", "android.settings");
        assertInvalid("{\"android\":{\"settings\":\"x\"}}", "android.settings");
        assertInvalid("{\"android\":{\"settings\":{\"secure\":{},\"other\":{}}}}", "android.settings.other");
        assertInvalid("{\"android\":{\"settings\":{\"secure\":[]}}}", "android.settings.secure");
        assertInvalid("{\"android\":{\"settings\":{\"system\":1}}}", "android.settings.system");
        assertInvalid("{\"android\":{\"settings\":{\"global\":true}}}", "android.settings.global");

        assertInvalid("{\"android\":{\"settings\":{\"secure\":{\"\": \"x\"}}}}",
                "android.settings.secure.");
        assertInvalid("{\"android\":{\"settings\":{\"secure\":{\" leading\":\"x\"}}}}",
                "android.settings.secure. leading");
        assertInvalid("{\"android\":{\"settings\":{\"secure\":{\"trailing \":\"x\"}}}}",
                "android.settings.secure.trailing ");

        assertInvalid("{\"android\":{\"settings\":{\"secure\":{\"android_id\":1}}}}",
                "android.settings.secure.android_id");
        assertInvalid("{\"android\":{\"settings\":{\"system\":{\"screen_brightness\":true}}}}",
                "android.settings.system.screen_brightness");
        assertInvalid("{\"android\":{\"settings\":{\"global\":{\"adb_enabled\":{}}}}}",
                "android.settings.global.adb_enabled");
        assertInvalid("{\"android\":{\"settings\":{\"secure\":{\"android_id\":[]}}}}",
                "android.settings.secure.android_id");
    }

    @Test
    public void testAndroidSettingsApiArgs() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"android\":{\"settings\":{\"secure\":{\"android_id\":\"x\"}}}}");
        try {
            config.isAndroidSettingConfigured("clipboard", "android_id");
            fail("expected IllegalArgumentException for namespace");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("namespace"));
        }
        try {
            config.getAndroidSettingString("secure", null);
            fail("expected IllegalArgumentException for null key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getAndroidSettingString("secure", "");
            fail("expected IllegalArgumentException for empty key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.isAndroidSettingConfigured("secure", " spaced");
            fail("expected IllegalArgumentException for whitespace key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
    }

    @Test
    public void testAndroidTelephonySlotIdentifiersConfig() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":2,"
                + "\"slots\":["
                + "{\"slotIndex\":0,"
                + "\"imei\":\"860123456789012\","
                + "\"meid\":\"A100000ABCD123\","
                + "\"deviceId\":\"860123456789012\","
                + "\"subscriberId\":\"460001234567890\","
                + "\"simSerialNumber\":\"89860012345678901234\"},"
                + "{\"slotIndex\":1,"
                + "\"imei\":\"TRACEAI_IMEI_MARKER_V1\","
                + "\"meid\":\"NotOnlyDigitsMeid\","
                + "\"deviceId\":\"ABC-DEVICE\","
                + "\"subscriberId\":null,"
                + "\"simSerialNumber\":\"UPPERCASE_SERIAL_XYZ\"}"
                + "]}}}"
        );

        assertTrue(config.isTelephonyPhoneCountConfigured());
        assertEquals(2, config.getTelephonyPhoneCount(9));

        assertTrue(config.isTelephonySlotIdentifierConfigured(0, "imei"));
        assertEquals("860123456789012", config.getTelephonySlotIdentifier(0, "imei"));
        assertEquals("A100000ABCD123", config.getTelephonySlotIdentifier(0, "meid"));
        assertEquals("860123456789012", config.getTelephonySlotIdentifier(0, "deviceId"));
        assertEquals("460001234567890", config.getTelephonySlotIdentifier(0, "subscriberId"));
        assertEquals("89860012345678901234", config.getTelephonySlotIdentifier(0, "simSerialNumber"));

        // marker and non-digit / mixed-case strings allowed (no IMEI Luhn check)
        assertEquals("TRACEAI_IMEI_MARKER_V1", config.getTelephonySlotIdentifier(1, "imei"));
        assertEquals("NotOnlyDigitsMeid", config.getTelephonySlotIdentifier(1, "meid"));
        assertEquals("ABC-DEVICE", config.getTelephonySlotIdentifier(1, "deviceId"));
        assertEquals("UPPERCASE_SERIAL_XYZ", config.getTelephonySlotIdentifier(1, "simSerialNumber"));

        // explicit JSON null: configured but get returns null
        assertTrue(config.isTelephonySlotIdentifierConfigured(1, "subscriberId"));
        assertNull(config.getTelephonySlotIdentifier(1, "subscriberId"));

        // missing key on a present slot
        TraceEnvironmentConfig partial = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{\"phoneCount\":1,"
                + "\"slots\":[{\"slotIndex\":0,\"imei\":\"1\"}]}}}"
        );
        assertTrue(partial.isTelephonySlotIdentifierConfigured(0, "imei"));
        assertFalse(partial.isTelephonySlotIdentifierConfigured(0, "meid"));
        assertNull(partial.getTelephonySlotIdentifier(0, "meid"));
        // missing slot index
        assertFalse(partial.isTelephonySlotIdentifierConfigured(1, "imei"));
        assertNull(partial.getTelephonySlotIdentifier(1, "imei"));

        // telephony entirely missing
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{}");
        assertFalse(empty.isTelephonyPhoneCountConfigured());
        assertEquals(7, empty.getTelephonyPhoneCount(7));
        assertFalse(empty.isTelephonySlotIdentifierConfigured(0, "imei"));
        assertNull(empty.getTelephonySlotIdentifier(0, "imei"));
        assertFalse(empty.isTelephonyStringConfigured("networkOperator"));
        assertNull(empty.getTelephonyString("networkOperator"));
    }

    @Test
    public void testAndroidTelephonyGlobalOperatorStrings() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,"
                + "\"slots\":[{\"slotIndex\":0}],"
                + "\"networkOperator\":\"46000\","
                + "\"networkOperatorName\":\"TRACEAI_OPERATOR_MARKER_V1\","
                + "\"simOperator\":null,"
                + "\"simOperatorName\":\"China Mobile\""
                + "}}}"
        );

        assertTrue(config.isTelephonyStringConfigured("networkOperator"));
        assertEquals("46000", config.getTelephonyString("networkOperator"));
        assertTrue(config.isTelephonyStringConfigured("networkOperatorName"));
        assertEquals("TRACEAI_OPERATOR_MARKER_V1", config.getTelephonyString("networkOperatorName"));
        assertTrue(config.isTelephonyStringConfigured("simOperatorName"));
        assertEquals("China Mobile", config.getTelephonyString("simOperatorName"));

        // explicit JSON null: configured but get returns null
        assertTrue(config.isTelephonyStringConfigured("simOperator"));
        assertNull(config.getTelephonyString("simOperator"));

        // missing key (only phoneCount/slots present)
        TraceEnvironmentConfig minimal = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[]}}}"
        );
        assertFalse(minimal.isTelephonyStringConfigured("networkOperator"));
        assertNull(minimal.getTelephonyString("networkOperator"));
        assertFalse(minimal.isTelephonyStringConfigured("networkOperatorName"));
        assertFalse(minimal.isTelephonyStringConfigured("simOperator"));
        assertFalse(minimal.isTelephonyStringConfigured("simOperatorName"));
        assertFalse(minimal.isTelephonyStringConfigured("networkCountryIso"));
        assertNull(minimal.getTelephonyString("networkCountryIso"));
        assertFalse(minimal.isTelephonyStringConfigured("simCountryIso"));
        assertNull(minimal.getTelephonyString("simCountryIso"));
    }

    @Test
    public void testAndroidTelephonyNetworkCountryIso() {
        // lowercase preserved
        TraceEnvironmentConfig lower = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"slots\":[{\"slotIndex\":0}],"
                + "\"networkCountryIso\":\"cn\"}}}"
        );
        assertTrue(lower.isTelephonyStringConfigured("networkCountryIso"));
        assertEquals("cn", lower.getTelephonyString("networkCountryIso"));
        // not derived from networkOperator (absent)
        assertFalse(lower.isTelephonyStringConfigured("networkOperator"));
        assertNull(lower.getTelephonyString("networkOperator"));

        // uppercase / mixed normalized to lowercase ASCII
        TraceEnvironmentConfig upper = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"slots\":[{\"slotIndex\":0}],"
                + "\"networkCountryIso\":\"CN\"}}}"
        );
        assertEquals("cn", upper.getTelephonyString("networkCountryIso"));
        TraceEnvironmentConfig mixed = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"slots\":[{\"slotIndex\":0}],"
                + "\"networkCountryIso\":\"Us\"}}}"
        );
        assertEquals("us", mixed.getTelephonyString("networkCountryIso"));

        // empty string allowed
        TraceEnvironmentConfig emptyIso = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"slots\":[{\"slotIndex\":0}],"
                + "\"networkCountryIso\":\"\"}}}"
        );
        assertTrue(emptyIso.isTelephonyStringConfigured("networkCountryIso"));
        assertEquals("", emptyIso.getTelephonyString("networkCountryIso"));

        // independent of networkOperator
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"slots\":[{\"slotIndex\":0}],"
                + "\"networkOperator\":\"46000\","
                + "\"networkCountryIso\":\"cn\"}}}"
        );
        assertEquals("46000", both.getTelephonyString("networkOperator"));
        assertEquals("cn", both.getTelephonyString("networkCountryIso"));

        // missing key
        TraceEnvironmentConfig minimal = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[]}}}"
        );
        assertFalse(minimal.isTelephonyStringConfigured("networkCountryIso"));
        assertNull(minimal.getTelephonyString("networkCountryIso"));

        // invalid: null / non-String / wrong length / non-letters
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkCountryIso\":null}}}",
                "android.telephony.networkCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkCountryIso\":1}}}",
                "android.telephony.networkCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkCountryIso\":true}}}",
                "android.telephony.networkCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkCountryIso\":\"c\"}}}",
                "android.telephony.networkCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkCountryIso\":\"chn\"}}}",
                "android.telephony.networkCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkCountryIso\":\"12\"}}}",
                "android.telephony.networkCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkCountryIso\":\"c1\"}}}",
                "android.telephony.networkCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkCountryIso\":\" c\"}}}",
                "android.telephony.networkCountryIso");
    }

    @Test
    public void testAndroidTelephonySimCountryIso() {
        // lowercase preserved
        TraceEnvironmentConfig lower = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"slots\":[{\"slotIndex\":0}],"
                + "\"simCountryIso\":\"cn\"}}}"
        );
        assertTrue(lower.isTelephonyStringConfigured("simCountryIso"));
        assertEquals("cn", lower.getTelephonyString("simCountryIso"));
        // not derived from simOperator (absent)
        assertFalse(lower.isTelephonyStringConfigured("simOperator"));
        assertNull(lower.getTelephonyString("simOperator"));

        // uppercase / mixed normalized to lowercase ASCII
        TraceEnvironmentConfig upper = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"slots\":[{\"slotIndex\":0}],"
                + "\"simCountryIso\":\"CN\"}}}"
        );
        assertEquals("cn", upper.getTelephonyString("simCountryIso"));
        TraceEnvironmentConfig mixed = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"slots\":[{\"slotIndex\":0}],"
                + "\"simCountryIso\":\"Us\"}}}"
        );
        assertEquals("us", mixed.getTelephonyString("simCountryIso"));

        // empty string allowed
        TraceEnvironmentConfig emptyIso = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"slots\":[{\"slotIndex\":0}],"
                + "\"simCountryIso\":\"\"}}}"
        );
        assertTrue(emptyIso.isTelephonyStringConfigured("simCountryIso"));
        assertEquals("", emptyIso.getTelephonyString("simCountryIso"));

        // independent of simOperator and networkCountryIso
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"slots\":[{\"slotIndex\":0}],"
                + "\"simOperator\":\"46000\","
                + "\"networkCountryIso\":\"us\","
                + "\"simCountryIso\":\"cn\"}}}"
        );
        assertEquals("46000", both.getTelephonyString("simOperator"));
        assertEquals("us", both.getTelephonyString("networkCountryIso"));
        assertEquals("cn", both.getTelephonyString("simCountryIso"));

        // missing key
        TraceEnvironmentConfig minimal = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[]}}}"
        );
        assertFalse(minimal.isTelephonyStringConfigured("simCountryIso"));
        assertNull(minimal.getTelephonyString("simCountryIso"));

        // invalid: null / non-String / wrong length / non-letters
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"simCountryIso\":null}}}",
                "android.telephony.simCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"simCountryIso\":1}}}",
                "android.telephony.simCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"simCountryIso\":true}}}",
                "android.telephony.simCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"simCountryIso\":\"c\"}}}",
                "android.telephony.simCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"simCountryIso\":\"chn\"}}}",
                "android.telephony.simCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"simCountryIso\":\"12\"}}}",
                "android.telephony.simCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"simCountryIso\":\"c1\"}}}",
                "android.telephony.simCountryIso");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"simCountryIso\":\" c\"}}}",
                "android.telephony.simCountryIso");
    }

    @Test
    public void testAndroidTelephonyScalarState() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":2,"
                + "\"dataNetworkType\":13,"
                + "\"dataState\":2,"
                + "\"dataActivity\":3,"
                + "\"phoneType\":1,"
                + "\"networkRoaming\":true,"
                + "\"slots\":["
                + "{\"slotIndex\":0,\"simState\":5},"
                + "{\"slotIndex\":1,\"simState\":0}"
                + "]}}}"
        );

        assertTrue(config.isTelephonyIntConfigured("dataNetworkType"));
        assertEquals(13, config.getTelephonyInt("dataNetworkType", 99));
        assertTrue(config.isTelephonyIntConfigured("dataState"));
        assertEquals(2, config.getTelephonyInt("dataState", 99));
        assertTrue(config.isTelephonyIntConfigured("dataActivity"));
        assertEquals(3, config.getTelephonyInt("dataActivity", 99));
        assertTrue(config.isTelephonyIntConfigured("phoneType"));
        assertEquals(1, config.getTelephonyInt("phoneType", 99));
        assertTrue(config.isTelephonyBooleanConfigured("networkRoaming"));
        assertTrue(config.getTelephonyBoolean("networkRoaming", false));
        assertTrue(config.isTelephonySlotSimStateConfigured(0));
        assertEquals(5, config.getTelephonySlotSimState(0, 99));
        assertTrue(config.isTelephonySlotSimStateConfigured(1));
        assertEquals(0, config.getTelephonySlotSimState(1, 99));

        // boundaries
        TraceEnvironmentConfig edges = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,"
                + "\"dataNetworkType\":0,"
                + "\"dataState\":-1,"
                + "\"dataActivity\":0,"
                + "\"phoneType\":3,"
                + "\"networkRoaming\":false,"
                + "\"slots\":[{\"slotIndex\":0,\"simState\":11}]}}}"
        );
        assertEquals(0, edges.getTelephonyInt("dataNetworkType", 1));
        assertEquals(-1, edges.getTelephonyInt("dataState", 0));
        assertEquals(0, edges.getTelephonyInt("dataActivity", 1));
        assertEquals(3, edges.getTelephonyInt("phoneType", 0));
        assertFalse(edges.getTelephonyBoolean("networkRoaming", true));
        assertEquals(11, edges.getTelephonySlotSimState(0, 1));

        // max dataNetworkType / dataState / dataActivity
        TraceEnvironmentConfig maxData = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"dataNetworkType\":20,\"dataState\":5,\"dataActivity\":4,"
                + "\"slots\":[{\"slotIndex\":0}]}}}"
        );
        assertEquals(20, maxData.getTelephonyInt("dataNetworkType", 0));
        assertEquals(5, maxData.getTelephonyInt("dataState", 0));
        assertEquals(4, maxData.getTelephonyInt("dataActivity", 0));

        // dataState independent of dataNetworkType / roaming
        TraceEnvironmentConfig dataStateOnly = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"dataState\":0,"
                + "\"slots\":[{\"slotIndex\":0}]}}}"
        );
        assertTrue(dataStateOnly.isTelephonyIntConfigured("dataState"));
        assertEquals(0, dataStateOnly.getTelephonyInt("dataState", 99));
        assertFalse(dataStateOnly.isTelephonyIntConfigured("dataNetworkType"));
        assertFalse(dataStateOnly.isTelephonyIntConfigured("dataActivity"));
        assertFalse(dataStateOnly.isTelephonyBooleanConfigured("networkRoaming"));

        // dataActivity independent of dataState / dataNetworkType / roaming
        TraceEnvironmentConfig dataActivityOnly = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"dataActivity\":1,"
                + "\"slots\":[{\"slotIndex\":0}]}}}"
        );
        assertTrue(dataActivityOnly.isTelephonyIntConfigured("dataActivity"));
        assertEquals(1, dataActivityOnly.getTelephonyInt("dataActivity", 99));
        assertFalse(dataActivityOnly.isTelephonyIntConfigured("dataState"));
        assertFalse(dataActivityOnly.isTelephonyIntConfigured("dataNetworkType"));
        assertFalse(dataActivityOnly.isTelephonyBooleanConfigured("networkRoaming"));
        // can set dataActivity independent of dataState
        TraceEnvironmentConfig activityIndep = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,\"dataState\":0,\"dataActivity\":3,"
                + "\"slots\":[{\"slotIndex\":0}]}}}"
        );
        assertEquals(0, activityIndep.getTelephonyInt("dataState", 99));
        assertEquals(3, activityIndep.getTelephonyInt("dataActivity", 99));

        // missing -> fallback / configured=false
        TraceEnvironmentConfig minimal = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[{\"slotIndex\":0}]}}}"
        );
        assertFalse(minimal.isTelephonyIntConfigured("dataNetworkType"));
        assertEquals(7, minimal.getTelephonyInt("dataNetworkType", 7));
        assertFalse(minimal.isTelephonyIntConfigured("dataState"));
        assertEquals(9, minimal.getTelephonyInt("dataState", 9));
        assertFalse(minimal.isTelephonyIntConfigured("dataActivity"));
        assertEquals(8, minimal.getTelephonyInt("dataActivity", 8));
        assertFalse(minimal.isTelephonyIntConfigured("phoneType"));
        assertEquals(2, minimal.getTelephonyInt("phoneType", 2));
        assertFalse(minimal.isTelephonyBooleanConfigured("networkRoaming"));
        assertTrue(minimal.getTelephonyBoolean("networkRoaming", true));
        assertFalse(minimal.isTelephonySlotSimStateConfigured(0));
        assertEquals(9, minimal.getTelephonySlotSimState(0, 9));
        assertFalse(minimal.isTelephonySlotSimStateConfigured(1));
        assertEquals(8, minimal.getTelephonySlotSimState(1, 8));

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{}");
        assertFalse(empty.isTelephonyIntConfigured("dataNetworkType"));
        assertFalse(empty.isTelephonyIntConfigured("dataState"));
        assertFalse(empty.isTelephonyIntConfigured("dataActivity"));
        assertEquals(3, empty.getTelephonyInt("phoneType", 3));
        assertFalse(empty.isTelephonyBooleanConfigured("networkRoaming"));
        assertFalse(empty.getTelephonyBoolean("networkRoaming", false));
        assertFalse(empty.isTelephonySlotSimStateConfigured(0));
        assertEquals(1, empty.getTelephonySlotSimState(0, 1));
    }

    @Test
    public void testAndroidTelephonyValidation() {
        // illegal telephony type
        assertInvalid("{\"android\":{\"telephony\":[]}}", "android.telephony");
        assertInvalid("{\"android\":{\"telephony\":\"x\"}}", "android.telephony");
        assertInvalid("{\"android\":{\"telephony\":1}}", "android.telephony");

        // unknown top-level key
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],\"operator\":\"cn\"}}}",
                "android.telephony.operator");

        // missing phoneCount / slots
        assertInvalid("{\"android\":{\"telephony\":{\"slots\":[]}}}", "android.telephony.phoneCount");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1}}}", "android.telephony.slots");

        // phoneCount range / exact integer
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":0,\"slots\":[]}}}",
                "android.telephony.phoneCount");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":9,\"slots\":[]}}}",
                "android.telephony.phoneCount");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1.5,\"slots\":[]}}}",
                "android.telephony.phoneCount");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":true,\"slots\":[]}}}",
                "android.telephony.phoneCount");

        // slots not array
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":{}}}}",
                "android.telephony.slots");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":\"x\"}}}",
                "android.telephony.slots");

        // item not object
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[\"slot0\"]}}}",
                "android.telephony.slots[0]");

        // unknown item field
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"networkOperator\":\"46000\"}]}}}",
                "android.telephony.slots[0].networkOperator");

        // missing / duplicate / out-of-range slotIndex
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[{\"imei\":\"1\"}]}}}",
                "android.telephony.slots[0].slotIndex");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":2,\"slots\":["
                        + "{\"slotIndex\":0,\"imei\":\"1\"},{\"slotIndex\":0,\"imei\":\"2\"}]}}}",
                "android.telephony.slots[1].slotIndex");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[{\"slotIndex\":1}]}}}",
                "android.telephony.slots[0].slotIndex");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":2,\"slots\":[{\"slotIndex\":2}]}}}",
                "android.telephony.slots[0].slotIndex");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[{\"slotIndex\":-1}]}}}",
                "android.telephony.slots[0].slotIndex");

        // identifier wrong type / blank / whitespace
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"imei\":1}]}}}",
                "android.telephony.slots[0].imei");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"meid\":true}]}}}",
                "android.telephony.slots[0].meid");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"deviceId\":{}}]}}}",
                "android.telephony.slots[0].deviceId");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"subscriberId\":[]}]}}}",
                "android.telephony.slots[0].subscriberId");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"simSerialNumber\":\"\"}]}}}",
                "android.telephony.slots[0].simSerialNumber");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"imei\":\" 8601\"}]}}}",
                "android.telephony.slots[0].imei");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"imei\":\"8601 \"}]}}}",
                "android.telephony.slots[0].imei");

        // global operator string wrong type / blank / whitespace
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkOperator\":1}}}",
                "android.telephony.networkOperator");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkOperatorName\":true}}}",
                "android.telephony.networkOperatorName");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"simOperator\":{}}}}",
                "android.telephony.simOperator");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"simOperatorName\":[]}}}",
                "android.telephony.simOperatorName");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkOperator\":\"\"}}}",
                "android.telephony.networkOperator");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"simOperatorName\":\" China\"}}}",
                "android.telephony.simOperatorName");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"simOperatorName\":\"China \"}}}",
                "android.telephony.simOperatorName");

        // scalar state wrong type / null / fractional / overflow / out of range
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataNetworkType\":null}}}",
                "android.telephony.dataNetworkType");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataNetworkType\":true}}}",
                "android.telephony.dataNetworkType");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataNetworkType\":1.5}}}",
                "android.telephony.dataNetworkType");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataNetworkType\":21}}}",
                "android.telephony.dataNetworkType");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataNetworkType\":-1}}}",
                "android.telephony.dataNetworkType");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataNetworkType\":99999999999999999999999999999}}}",
                "android.telephony.dataNetworkType");
        // dataState wrong type / null / fractional / out of range -1..5
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataState\":null}}}",
                "android.telephony.dataState");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataState\":\"2\"}}}",
                "android.telephony.dataState");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataState\":true}}}",
                "android.telephony.dataState");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataState\":1.5}}}",
                "android.telephony.dataState");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataState\":-2}}}",
                "android.telephony.dataState");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataState\":6}}}",
                "android.telephony.dataState");
        // dataActivity wrong type / null / fractional / out of range 0..4
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataActivity\":null}}}",
                "android.telephony.dataActivity");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataActivity\":\"2\"}}}",
                "android.telephony.dataActivity");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataActivity\":true}}}",
                "android.telephony.dataActivity");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataActivity\":1.5}}}",
                "android.telephony.dataActivity");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataActivity\":-1}}}",
                "android.telephony.dataActivity");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataActivity\":5}}}",
                "android.telephony.dataActivity");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"phoneType\":null}}}",
                "android.telephony.phoneType");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"phoneType\":4}}}",
                "android.telephony.phoneType");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"phoneType\":1.1}}}",
                "android.telephony.phoneType");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkRoaming\":null}}}",
                "android.telephony.networkRoaming");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkRoaming\":1}}}",
                "android.telephony.networkRoaming");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"networkRoaming\":\"true\"}}}",
                "android.telephony.networkRoaming");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"simState\":null}]}}}",
                "android.telephony.slots[0].simState");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"simState\":12}]}}}",
                "android.telephony.slots[0].simState");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"simState\":-1}]}}}",
                "android.telephony.slots[0].simState");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"simState\":2.5}]}}}",
                "android.telephony.slots[0].simState");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"simState\":true}]}}}",
                "android.telephony.slots[0].simState");
    }

    @Test
    public void testAndroidTelephonyApiArgs() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{\"phoneCount\":1,"
                + "\"slots\":[{\"slotIndex\":0,\"imei\":\"1\"}]}}}"
        );
        try {
            config.isTelephonySlotIdentifierConfigured(-1, "imei");
            fail("expected IllegalArgumentException for negative slotIndex");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("slotIndex"));
        }
        try {
            config.getTelephonySlotIdentifier(-1, "imei");
            fail("expected IllegalArgumentException for negative slotIndex on get");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("slotIndex"));
        }
        try {
            config.isTelephonySlotIdentifierConfigured(0, "networkOperator");
            fail("expected IllegalArgumentException for illegal key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getTelephonySlotIdentifier(0, null);
            fail("expected IllegalArgumentException for null key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getTelephonySlotIdentifier(0, "slotIndex");
            fail("expected IllegalArgumentException for slotIndex as identifier key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.isTelephonyStringConfigured("imei");
            fail("expected IllegalArgumentException for slot key on global string API");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getTelephonyString(null);
            fail("expected IllegalArgumentException for null global string key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getTelephonyString("phoneCount");
            fail("expected IllegalArgumentException for phoneCount as global string key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.isTelephonyIntConfigured("phoneCount");
            fail("expected IllegalArgumentException for phoneCount on int API");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getTelephonyInt("networkRoaming", 0);
            fail("expected IllegalArgumentException for networkRoaming on int API");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.isTelephonyBooleanConfigured("dataNetworkType");
            fail("expected IllegalArgumentException for dataNetworkType on boolean API");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getTelephonyBoolean(null, false);
            fail("expected IllegalArgumentException for null boolean key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.isTelephonySlotSimStateConfigured(-1);
            fail("expected IllegalArgumentException for negative slotIndex on simState");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("slotIndex"));
        }
        try {
            config.getTelephonySlotSimState(-1, 0);
            fail("expected IllegalArgumentException for negative slotIndex on get simState");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("slotIndex"));
        }
    }

    @Test
    public void testNetworkWifiConfig() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"wifi\":{"
                + "\"enabled\":true,"
                + "\"state\":3,"
                + "\"ssid\":\"TRACEAI_WIFI_SSID_V1\","
                + "\"bssid\":\"AA:BB:CC:DD:EE:FF\","
                + "\"macAddress\":\"02:00:00:00:00:01\","
                + "\"ipv4\":\"192.168.1.10\","
                + "\"rssi\":-55,"
                + "\"linkSpeedMbps\":433,"
                + "\"frequencyMhz\":5180,"
                + "\"networkId\":1"
                + "}}}"
        );

        assertTrue(config.isWifiEnabledConfigured());
        assertTrue(config.getWifiEnabled(false));
        assertTrue(config.isWifiStateConfigured());
        assertEquals(3, config.getWifiState(-1));
        assertTrue(config.isWifiStringConfigured("ssid"));
        assertEquals("TRACEAI_WIFI_SSID_V1", config.getWifiString("ssid"));
        // MAC normalized to lowercase
        assertEquals("aa:bb:cc:dd:ee:ff", config.getWifiString("bssid"));
        assertEquals("02:00:00:00:00:01", config.getWifiString("macAddress"));
        assertEquals("192.168.1.10", config.getWifiString("ipv4"));
        assertTrue(config.isWifiIntConfigured("rssi"));
        assertEquals(-55, config.getWifiInt("rssi", 0));
        assertEquals(433, config.getWifiInt("linkSpeedMbps", 0));
        assertEquals(5180, config.getWifiInt("frequencyMhz", 0));
        assertEquals(1, config.getWifiInt("networkId", -1));

        // explicit null strings: configured but get returns null
        TraceEnvironmentConfig withNulls = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"wifi\":{"
                + "\"ssid\":null,\"bssid\":null,\"macAddress\":null,\"ipv4\":null,"
                + "\"enabled\":false,\"rssi\":-127,\"networkId\":-1"
                + "}}}"
        );
        assertTrue(withNulls.isWifiStringConfigured("ssid"));
        assertNull(withNulls.getWifiString("ssid"));
        assertTrue(withNulls.isWifiStringConfigured("bssid"));
        assertNull(withNulls.getWifiString("bssid"));
        assertTrue(withNulls.isWifiStringConfigured("ipv4"));
        assertNull(withNulls.getWifiString("ipv4"));
        assertFalse(withNulls.getWifiEnabled(true));
        assertFalse(withNulls.isWifiStateConfigured());
        assertEquals(-127, withNulls.getWifiInt("rssi", 0));
        assertEquals(-1, withNulls.getWifiInt("networkId", 0));

        // boundaries
        TraceEnvironmentConfig edges = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"wifi\":{"
                + "\"rssi\":0,\"linkSpeedMbps\":0,\"frequencyMhz\":100000,\"networkId\":2147483647"
                + "}}}"
        );
        assertEquals(0, edges.getWifiInt("rssi", 1));
        assertEquals(0, edges.getWifiInt("linkSpeedMbps", 1));
        assertEquals(100000, edges.getWifiInt("frequencyMhz", 0));
        assertEquals(Integer.MAX_VALUE, edges.getWifiInt("networkId", 0));

        // state bounds 0..4 (WIFI_STATE_DISABLING..UNKNOWN); independent of enabled
        assertEquals(0, TraceEnvironmentConfig.parse(
                "{\"network\":{\"wifi\":{\"state\":0}}}")
                .getWifiState(-1));
        assertEquals(4, TraceEnvironmentConfig.parse(
                "{\"network\":{\"wifi\":{\"state\":4}}}")
                .getWifiState(-1));
        TraceEnvironmentConfig stateOnly = TraceEnvironmentConfig.parse(
                "{\"network\":{\"wifi\":{\"state\":1}}}");
        assertTrue(stateOnly.isWifiStateConfigured());
        assertEquals(1, stateOnly.getWifiState(-1));
        assertFalse(stateOnly.isWifiEnabledConfigured());
        // enabled true but state DISABLED (1) — not inferred either way
        TraceEnvironmentConfig independent = TraceEnvironmentConfig.parse(
                "{\"network\":{\"wifi\":{\"enabled\":true,\"state\":1}}}");
        assertTrue(independent.getWifiEnabled(false));
        assertEquals(1, independent.getWifiState(-1));
        TraceEnvironmentConfig enabledFalseStateOn = TraceEnvironmentConfig.parse(
                "{\"network\":{\"wifi\":{\"enabled\":false,\"state\":3}}}");
        assertFalse(enabledFalseStateOn.getWifiEnabled(true));
        assertEquals(3, enabledFalseStateOn.getWifiState(-1));

        // missing network / wifi / key
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{}");
        assertFalse(empty.isWifiEnabledConfigured());
        assertTrue(empty.getWifiEnabled(true));
        assertFalse(empty.isWifiStateConfigured());
        assertEquals(9, empty.getWifiState(9));
        assertFalse(empty.isWifiStringConfigured("ssid"));
        assertNull(empty.getWifiString("ssid"));
        assertFalse(empty.isWifiIntConfigured("rssi"));
        assertEquals(9, empty.getWifiInt("rssi", 9));

        TraceEnvironmentConfig noWifi = TraceEnvironmentConfig.parse(
                "{\"network\":{\"interfaces\":[]}}");
        assertFalse(noWifi.isWifiEnabledConfigured());
        assertFalse(noWifi.isWifiStateConfigured());
        assertFalse(noWifi.isWifiStringConfigured("ssid"));
        // interfaces still parse
        assertTrue(noWifi.isNetworkInterfacesConfigured());

        TraceEnvironmentConfig partial = TraceEnvironmentConfig.parse(
                "{\"network\":{\"wifi\":{\"ssid\":\"only\"}}}");
        assertTrue(partial.isWifiStringConfigured("ssid"));
        assertFalse(partial.isWifiStringConfigured("bssid"));
        assertNull(partial.getWifiString("bssid"));
        assertFalse(partial.isWifiIntConfigured("rssi"));
        assertEquals(3, partial.getWifiInt("rssi", 3));
        assertFalse(partial.isWifiStateConfigured());
        assertEquals(7, partial.getWifiState(7));
    }

    @Test
    public void testNetworkWifiValidation() {
        assertInvalid("{\"network\":{\"wifi\":[]}}", "network.wifi");
        assertInvalid("{\"network\":{\"wifi\":\"x\"}}", "network.wifi");
        assertInvalid("{\"network\":{\"wifi\":{\"channel\":6}}}", "network.wifi.channel");

        assertInvalid("{\"network\":{\"wifi\":{\"enabled\":null}}}", "network.wifi.enabled");
        assertInvalid("{\"network\":{\"wifi\":{\"enabled\":1}}}", "network.wifi.enabled");
        assertInvalid("{\"network\":{\"wifi\":{\"enabled\":\"true\"}}}", "network.wifi.enabled");

        assertInvalid("{\"network\":{\"wifi\":{\"ssid\":\"\"}}}", "network.wifi.ssid");
        assertInvalid("{\"network\":{\"wifi\":{\"ssid\":\" x\"}}}", "network.wifi.ssid");
        assertInvalid("{\"network\":{\"wifi\":{\"ssid\":1}}}", "network.wifi.ssid");

        assertInvalid("{\"network\":{\"wifi\":{\"bssid\":\"not-a-mac\"}}}", "network.wifi.bssid");
        assertInvalid("{\"network\":{\"wifi\":{\"bssid\":1}}}", "network.wifi.bssid");
        assertInvalid("{\"network\":{\"wifi\":{\"macAddress\":\"aa:bb:cc:dd:ee\"}}}",
                "network.wifi.macAddress");

        assertInvalid("{\"network\":{\"wifi\":{\"ipv4\":\"localhost\"}}}", "network.wifi.ipv4");
        assertInvalid("{\"network\":{\"wifi\":{\"ipv4\":\" 1.2.3.4\"}}}", "network.wifi.ipv4");
        assertInvalid("{\"network\":{\"wifi\":{\"ipv4\":1}}}", "network.wifi.ipv4");

        assertInvalid("{\"network\":{\"wifi\":{\"rssi\":null}}}", "network.wifi.rssi");
        assertInvalid("{\"network\":{\"wifi\":{\"rssi\":1}}}", "network.wifi.rssi");
        assertInvalid("{\"network\":{\"wifi\":{\"rssi\":-128}}}", "network.wifi.rssi");
        assertInvalid("{\"network\":{\"wifi\":{\"rssi\":-55.5}}}", "network.wifi.rssi");
        assertInvalid("{\"network\":{\"wifi\":{\"rssi\":true}}}", "network.wifi.rssi");

        assertInvalid("{\"network\":{\"wifi\":{\"linkSpeedMbps\":-1}}}", "network.wifi.linkSpeedMbps");
        assertInvalid("{\"network\":{\"wifi\":{\"linkSpeedMbps\":100001}}}",
                "network.wifi.linkSpeedMbps");
        assertInvalid("{\"network\":{\"wifi\":{\"linkSpeedMbps\":1.1}}}",
                "network.wifi.linkSpeedMbps");
        assertInvalid("{\"network\":{\"wifi\":{\"linkSpeedMbps\":null}}}",
                "network.wifi.linkSpeedMbps");

        assertInvalid("{\"network\":{\"wifi\":{\"frequencyMhz\":100001}}}",
                "network.wifi.frequencyMhz");
        assertInvalid("{\"network\":{\"wifi\":{\"frequencyMhz\":null}}}",
                "network.wifi.frequencyMhz");

        assertInvalid("{\"network\":{\"wifi\":{\"networkId\":-2}}}", "network.wifi.networkId");
        assertInvalid("{\"network\":{\"wifi\":{\"networkId\":null}}}", "network.wifi.networkId");
        assertInvalid("{\"network\":{\"wifi\":{\"networkId\":99999999999999999999999999999}}}",
                "network.wifi.networkId");

        // state exact int 0..4 only; no null/string/fraction
        assertInvalid("{\"network\":{\"wifi\":{\"state\":null}}}", "network.wifi.state");
        assertInvalid("{\"network\":{\"wifi\":{\"state\":\"3\"}}}", "network.wifi.state");
        assertInvalid("{\"network\":{\"wifi\":{\"state\":true}}}", "network.wifi.state");
        assertInvalid("{\"network\":{\"wifi\":{\"state\":1.5}}}", "network.wifi.state");
        assertInvalid("{\"network\":{\"wifi\":{\"state\":-1}}}", "network.wifi.state");
        assertInvalid("{\"network\":{\"wifi\":{\"state\":5}}}", "network.wifi.state");
    }

    @Test
    public void testNetworkBluetoothConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isNetworkBluetoothConfigured());
        assertNull(missing.getNetworkBluetoothConfig());
        TraceEnvironmentConfig noBt = TraceEnvironmentConfig.parse(
                "{\"network\":{\"wifi\":{\"enabled\":true}}}");
        assertFalse(noBt.isNetworkBluetoothConfigured());
        assertNull(noBt.getNetworkBluetoothConfig());

        // explicit empty → node configured, no field flags
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"network\":{\"bluetooth\":{}}}");
        assertTrue(empty.isNetworkBluetoothConfigured());
        TraceEnvironmentConfig.NetworkBluetoothConfig e = empty.getNetworkBluetoothConfig();
        assertNotNull(e);
        assertFalse(e.isNameConfigured());
        assertNull(e.getName());
        assertFalse(e.isAddressConfigured());
        assertNull(e.getAddress());
        assertFalse(e.isEnabledConfigured());
        assertFalse(e.isEnabled());
        assertFalse(e.isStateConfigured());
        assertFalse(e.isScanModeConfigured());
        assertFalse(e.isDiscoveringConfigured());
        assertFalse(e.isDiscovering());
        assertFalse(e.hasAnyFieldConfigured());

        // full values; MAC normalized to lowercase
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"bluetooth\":{"
                + "\"name\":\"TRACEAI_BT_V1\","
                + "\"address\":\"AA:BB:CC:DD:EE:FF\","
                + "\"enabled\":true,"
                + "\"state\":12,"
                + "\"scanMode\":21,"
                + "\"discovering\":true"
                + "}}}"
        );
        assertTrue(full.isNetworkBluetoothConfigured());
        TraceEnvironmentConfig.NetworkBluetoothConfig b = full.getNetworkBluetoothConfig();
        assertTrue(b.isNameConfigured());
        assertEquals("TRACEAI_BT_V1", b.getName());
        assertTrue(b.isAddressConfigured());
        assertEquals("aa:bb:cc:dd:ee:ff", b.getAddress());
        assertTrue(b.isEnabledConfigured());
        assertTrue(b.isEnabled());
        assertTrue(b.isStateConfigured());
        assertEquals(12, b.getState());
        assertTrue(b.isScanModeConfigured());
        assertEquals(21, b.getScanMode());
        assertTrue(b.isDiscoveringConfigured());
        assertTrue(b.isDiscovering());
        assertTrue(b.hasAnyFieldConfigured());
        assertEquals(b.getName(), full.getNetworkBluetoothConfig().getName());
        assertEquals(b.getAddress(), full.getNetworkBluetoothConfig().getAddress());
        assertEquals(b.getState(), full.getNetworkBluetoothConfig().getState());
        assertEquals(b.getScanMode(), full.getNetworkBluetoothConfig().getScanMode());
        assertEquals(b.isDiscovering(), full.getNetworkBluetoothConfig().isDiscovering());

        // explicit null strings still presence-configured
        TraceEnvironmentConfig withNulls = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"bluetooth\":{"
                + "\"name\":null,\"address\":null,\"enabled\":false"
                + "}}}"
        );
        TraceEnvironmentConfig.NetworkBluetoothConfig n = withNulls.getNetworkBluetoothConfig();
        assertTrue(n.isNameConfigured());
        assertNull(n.getName());
        assertTrue(n.isAddressConfigured());
        assertNull(n.getAddress());
        assertTrue(n.isEnabledConfigured());
        assertFalse(n.isEnabled());
        assertFalse(n.isStateConfigured());
        assertFalse(n.isScanModeConfigured());
        assertFalse(n.isDiscoveringConfigured());
        assertFalse(n.isDiscovering());
        assertTrue(n.hasAnyFieldConfigured());

        // partial independent presence
        TraceEnvironmentConfig nameOnly = TraceEnvironmentConfig.parse(
                "{\"network\":{\"bluetooth\":{\"name\":\"only\"}}}");
        assertTrue(nameOnly.getNetworkBluetoothConfig().isNameConfigured());
        assertEquals("only", nameOnly.getNetworkBluetoothConfig().getName());
        assertFalse(nameOnly.getNetworkBluetoothConfig().isAddressConfigured());
        assertFalse(nameOnly.getNetworkBluetoothConfig().isEnabledConfigured());
        assertFalse(nameOnly.getNetworkBluetoothConfig().isStateConfigured());
        assertFalse(nameOnly.getNetworkBluetoothConfig().isDiscoveringConfigured());
        assertFalse(nameOnly.getNetworkBluetoothConfig().isDiscovering());
        assertTrue(nameOnly.getNetworkBluetoothConfig().hasAnyFieldConfigured());

        TraceEnvironmentConfig enabledOnly = TraceEnvironmentConfig.parse(
                "{\"network\":{\"bluetooth\":{\"enabled\":false}}}");
        assertTrue(enabledOnly.getNetworkBluetoothConfig().isEnabledConfigured());
        assertFalse(enabledOnly.getNetworkBluetoothConfig().isEnabled());
        assertFalse(enabledOnly.getNetworkBluetoothConfig().isNameConfigured());
        assertFalse(enabledOnly.getNetworkBluetoothConfig().isStateConfigured());
        assertTrue(enabledOnly.getNetworkBluetoothConfig().hasAnyFieldConfigured());

        // discovering alone; not inferred from enabled/state
        TraceEnvironmentConfig discoveringOnly = TraceEnvironmentConfig.parse(
                "{\"network\":{\"bluetooth\":{\"discovering\":true}}}");
        assertTrue(discoveringOnly.getNetworkBluetoothConfig().isDiscoveringConfigured());
        assertTrue(discoveringOnly.getNetworkBluetoothConfig().isDiscovering());
        assertFalse(discoveringOnly.getNetworkBluetoothConfig().isEnabledConfigured());
        assertTrue(discoveringOnly.getNetworkBluetoothConfig().hasAnyFieldConfigured());
        TraceEnvironmentConfig discoveringFalse = TraceEnvironmentConfig.parse(
                "{\"network\":{\"bluetooth\":{\"discovering\":false}}}");
        assertTrue(discoveringFalse.getNetworkBluetoothConfig().isDiscoveringConfigured());
        assertFalse(discoveringFalse.getNetworkBluetoothConfig().isDiscovering());

        // state/scanMode alone enable hasAnyFieldConfigured; not inferred from enabled
        for (int state : new int[] {10, 11, 12, 13}) {
            TraceEnvironmentConfig sc = TraceEnvironmentConfig.parse(
                    "{\"network\":{\"bluetooth\":{\"state\":" + state + "}}}");
            assertTrue(sc.getNetworkBluetoothConfig().isStateConfigured());
            assertEquals(state, sc.getNetworkBluetoothConfig().getState());
            assertFalse(sc.getNetworkBluetoothConfig().isEnabledConfigured());
            assertTrue(sc.getNetworkBluetoothConfig().hasAnyFieldConfigured());
        }
        for (int mode : new int[] {20, 21, 23}) {
            TraceEnvironmentConfig sc = TraceEnvironmentConfig.parse(
                    "{\"network\":{\"bluetooth\":{\"scanMode\":" + mode + "}}}");
            assertTrue(sc.getNetworkBluetoothConfig().isScanModeConfigured());
            assertEquals(mode, sc.getNetworkBluetoothConfig().getScanMode());
            assertTrue(sc.getNetworkBluetoothConfig().hasAnyFieldConfigured());
        }
    }

    @Test
    public void testNetworkBluetoothValidation() {
        assertInvalid("{\"network\":{\"bluetooth\":[]}}", "network.bluetooth");
        assertInvalid("{\"network\":{\"bluetooth\":\"x\"}}", "network.bluetooth");
        assertInvalid("{\"network\":{\"bluetooth\":1}}", "network.bluetooth");
        assertInvalid("{\"network\":{\"bluetooth\":{\"bondState\":12}}}",
                "network.bluetooth.bondState");

        assertInvalid("{\"network\":{\"bluetooth\":{\"name\":1}}}", "network.bluetooth.name");
        assertInvalid("{\"network\":{\"bluetooth\":{\"name\":true}}}", "network.bluetooth.name");
        assertInvalid("{\"network\":{\"bluetooth\":{\"name\":\"\"}}}", "network.bluetooth.name");
        assertInvalid("{\"network\":{\"bluetooth\":{\"name\":\" x\"}}}", "network.bluetooth.name");

        assertInvalid("{\"network\":{\"bluetooth\":{\"address\":1}}}", "network.bluetooth.address");
        assertInvalid("{\"network\":{\"bluetooth\":{\"address\":true}}}",
                "network.bluetooth.address");
        assertInvalid("{\"network\":{\"bluetooth\":{\"address\":\"not-a-mac\"}}}",
                "network.bluetooth.address");
        assertInvalid("{\"network\":{\"bluetooth\":{\"address\":\"aa:bb:cc:dd:ee\"}}}",
                "network.bluetooth.address");
        assertInvalid("{\"network\":{\"bluetooth\":{\"address\":\"aa-bb-cc-dd-ee-ff\"}}}",
                "network.bluetooth.address");

        assertInvalid("{\"network\":{\"bluetooth\":{\"enabled\":null}}}",
                "network.bluetooth.enabled");
        assertInvalid("{\"network\":{\"bluetooth\":{\"enabled\":1}}}",
                "network.bluetooth.enabled");
        assertInvalid("{\"network\":{\"bluetooth\":{\"enabled\":\"true\"}}}",
                "network.bluetooth.enabled");

        assertInvalid("{\"network\":{\"bluetooth\":{\"state\":null}}}", "network.bluetooth.state");
        assertInvalid("{\"network\":{\"bluetooth\":{\"state\":\"12\"}}}", "network.bluetooth.state");
        assertInvalid("{\"network\":{\"bluetooth\":{\"state\":true}}}", "network.bluetooth.state");
        assertInvalid("{\"network\":{\"bluetooth\":{\"state\":12.5}}}", "network.bluetooth.state");
        assertInvalid("{\"network\":{\"bluetooth\":{\"state\":0}}}", "network.bluetooth.state");
        assertInvalid("{\"network\":{\"bluetooth\":{\"state\":14}}}", "network.bluetooth.state");

        assertInvalid("{\"network\":{\"bluetooth\":{\"scanMode\":null}}}",
                "network.bluetooth.scanMode");
        assertInvalid("{\"network\":{\"bluetooth\":{\"scanMode\":\"21\"}}}",
                "network.bluetooth.scanMode");
        assertInvalid("{\"network\":{\"bluetooth\":{\"scanMode\":true}}}",
                "network.bluetooth.scanMode");
        assertInvalid("{\"network\":{\"bluetooth\":{\"scanMode\":20.1}}}",
                "network.bluetooth.scanMode");
        assertInvalid("{\"network\":{\"bluetooth\":{\"scanMode\":0}}}",
                "network.bluetooth.scanMode");
        assertInvalid("{\"network\":{\"bluetooth\":{\"scanMode\":22}}}",
                "network.bluetooth.scanMode");

        assertInvalid("{\"network\":{\"bluetooth\":{\"discovering\":null}}}",
                "network.bluetooth.discovering");
        assertInvalid("{\"network\":{\"bluetooth\":{\"discovering\":1}}}",
                "network.bluetooth.discovering");
        assertInvalid("{\"network\":{\"bluetooth\":{\"discovering\":\"true\"}}}",
                "network.bluetooth.discovering");
    }

    @Test
    public void testNetworkWifiApiArgs() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"network\":{\"wifi\":{\"ssid\":\"x\"}}}");
        try {
            config.isWifiStringConfigured("enabled");
            fail("expected IllegalArgumentException for enabled on string API");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getWifiString(null);
            fail("expected IllegalArgumentException for null string key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.isWifiIntConfigured("ssid");
            fail("expected IllegalArgumentException for ssid on int API");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getWifiInt("rssiX", 0);
            fail("expected IllegalArgumentException for illegal int key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
    }

    @Test
    public void testNetworkLinksConfig() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"links\":{"
                + "\"connected\":true,"
                + "\"type\":1,"
                + "\"typeName\":\"WIFI\","
                + "\"interfaceName\":\"wlan0\","
                + "\"dnsServers\":[\"8.8.8.8\",\"1.1.1.1\"],"
                + "\"gatewayIpv4\":\"192.168.50.1\","
                + "\"netmaskIpv4\":\"255.255.255.0\","
                + "\"mtu\":1500"
                + "}}}"
        );
        assertTrue(config.isNetworkLinksConfigured());
        assertTrue(config.isLinkBooleanConfigured("connected"));
        assertTrue(config.getLinkBoolean("connected", false));
        assertTrue(config.isLinkIntConfigured("type"));
        assertEquals(1, config.getLinkInt("type", 9));
        assertEquals(1500, config.getLinkInt("mtu", 0));
        assertEquals("WIFI", config.getLinkString("typeName"));
        assertEquals("wlan0", config.getLinkString("interfaceName"));
        assertEquals("192.168.50.1", config.getLinkString("gatewayIpv4"));
        assertTrue(config.isLinkStringConfigured("netmaskIpv4"));
        assertEquals("255.255.255.0", config.getLinkString("netmaskIpv4"));
        assertTrue(config.isLinkDnsServersConfigured());
        List<String> dns = config.getLinkDnsServers();
        assertEquals(2, dns.size());
        assertEquals("8.8.8.8", dns.get(0));
        assertEquals("1.1.1.1", dns.get(1));
        try {
            dns.add("9.9.9.9");
            fail("expected UnsupportedOperationException on dns list add");
        } catch (UnsupportedOperationException expected) {
            // immutable
        }

        // empty DNS array: configured but empty list
        TraceEnvironmentConfig emptyDns = TraceEnvironmentConfig.parse(
                "{\"network\":{\"links\":{\"dnsServers\":[]}}}");
        assertTrue(emptyDns.isLinkDnsServersConfigured());
        assertTrue(emptyDns.getLinkDnsServers().isEmpty());

        // explicit null gateway / netmask
        TraceEnvironmentConfig nullGw = TraceEnvironmentConfig.parse(
                "{\"network\":{\"links\":{\"gatewayIpv4\":null,\"netmaskIpv4\":null,\"type\":-1,\"mtu\":68}}}");
        assertTrue(nullGw.isLinkStringConfigured("gatewayIpv4"));
        assertNull(nullGw.getLinkString("gatewayIpv4"));
        assertTrue(nullGw.isLinkStringConfigured("netmaskIpv4"));
        assertNull(nullGw.getLinkString("netmaskIpv4"));
        assertEquals(-1, nullGw.getLinkInt("type", 0));
        assertEquals(68, nullGw.getLinkInt("mtu", 0));

        // boundaries type 17, mtu 65536
        TraceEnvironmentConfig edges = TraceEnvironmentConfig.parse(
                "{\"network\":{\"links\":{\"type\":17,\"mtu\":65536,\"interfaceName\":\"abcdefghijklmno\"}}}");
        assertEquals(17, edges.getLinkInt("type", 0));
        assertEquals(65536, edges.getLinkInt("mtu", 0));
        assertEquals("abcdefghijklmno", edges.getLinkString("interfaceName"));

        // missing
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{}");
        assertFalse(empty.isNetworkLinksConfigured());
        assertFalse(empty.isLinkBooleanConfigured("connected"));
        assertFalse(empty.getLinkBoolean("connected", false));
        assertFalse(empty.isLinkIntConfigured("type"));
        assertEquals(3, empty.getLinkInt("type", 3));
        assertFalse(empty.isLinkStringConfigured("typeName"));
        assertNull(empty.getLinkString("typeName"));
        assertFalse(empty.isLinkStringConfigured("netmaskIpv4"));
        assertNull(empty.getLinkString("netmaskIpv4"));
        assertFalse(empty.isLinkDnsServersConfigured());
        assertTrue(empty.getLinkDnsServers().isEmpty());

        TraceEnvironmentConfig noLinks = TraceEnvironmentConfig.parse(
                "{\"network\":{\"wifi\":{\"enabled\":true}}}");
        assertFalse(noLinks.isNetworkLinksConfigured());
        assertTrue(noLinks.isWifiEnabledConfigured());
        assertFalse(noLinks.isLinkStringConfigured("netmaskIpv4"));
        assertNull(noLinks.getLinkString("netmaskIpv4"));

        // private DNS / proxy / DHCP scalars
        TraceEnvironmentConfig extras = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"links\":{"
                + "\"privateDnsActive\":true,"
                + "\"privateDnsServerName\":\"dns.google\","
                + "\"proxyHost\":\"proxy.example.com\","
                + "\"proxyPort\":8080,"
                + "\"dhcpServerIpv4\":\"192.168.50.1\","
                + "\"netmaskIpv4\":\"255.255.255.0\","
                + "\"leaseDurationSeconds\":3600,"
                + "\"domains\":\"lan.local\""
                + "}}}"
        );
        assertTrue(extras.isLinkBooleanConfigured("privateDnsActive"));
        assertTrue(extras.getLinkBoolean("privateDnsActive", false));
        assertEquals("dns.google", extras.getLinkString("privateDnsServerName"));
        assertEquals("proxy.example.com", extras.getLinkString("proxyHost"));
        assertEquals(8080, extras.getLinkInt("proxyPort", 0));
        assertEquals("192.168.50.1", extras.getLinkString("dhcpServerIpv4"));
        assertEquals("255.255.255.0", extras.getLinkString("netmaskIpv4"));
        assertEquals(3600, extras.getLinkInt("leaseDurationSeconds", 0));
        assertEquals("lan.local", extras.getLinkString("domains"));

        TraceEnvironmentConfig nullExtras = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"links\":{"
                + "\"privateDnsServerName\":null,"
                + "\"proxyHost\":null,"
                + "\"dhcpServerIpv4\":null,"
                + "\"netmaskIpv4\":null,"
                + "\"domains\":null,"
                + "\"proxyPort\":0,"
                + "\"leaseDurationSeconds\":0"
                + "}}}"
        );
        assertTrue(nullExtras.isLinkStringConfigured("privateDnsServerName"));
        assertNull(nullExtras.getLinkString("privateDnsServerName"));
        assertTrue(nullExtras.isLinkStringConfigured("proxyHost"));
        assertNull(nullExtras.getLinkString("proxyHost"));
        assertTrue(nullExtras.isLinkStringConfigured("dhcpServerIpv4"));
        assertNull(nullExtras.getLinkString("dhcpServerIpv4"));
        assertTrue(nullExtras.isLinkStringConfigured("netmaskIpv4"));
        assertNull(nullExtras.getLinkString("netmaskIpv4"));
        assertTrue(nullExtras.isLinkStringConfigured("domains"));
        assertNull(nullExtras.getLinkString("domains"));
        assertEquals(0, nullExtras.getLinkInt("proxyPort", 1));
        assertEquals(0, nullExtras.getLinkInt("leaseDurationSeconds", 1));

        assertFalse(empty.isLinkBooleanConfigured("privateDnsActive"));
        assertTrue(empty.getLinkBoolean("privateDnsActive", true));
        assertFalse(empty.isLinkIntConfigured("proxyPort"));
        assertEquals(9, empty.getLinkInt("proxyPort", 9));
        assertFalse(empty.isLinkStringConfigured("domains"));
        assertNull(empty.getLinkString("domains"));
        assertFalse(empty.isLinkStringConfigured("netmaskIpv4"));
        assertNull(empty.getLinkString("netmaskIpv4"));
    }

    @Test
    public void testNetworkLinksValidation() {
        assertInvalid("{\"network\":{\"links\":[]}}", "network.links");
        assertInvalid("{\"network\":{\"links\":\"x\"}}", "network.links");
        assertInvalid("{\"network\":{\"links\":{\"proxy\":\"http\"}}}", "network.links.proxy");

        assertInvalid("{\"network\":{\"links\":{\"connected\":null}}}", "network.links.connected");
        assertInvalid("{\"network\":{\"links\":{\"connected\":1}}}", "network.links.connected");

        assertInvalid("{\"network\":{\"links\":{\"type\":\"1\"}}}", "network.links.type");
        assertInvalid("{\"network\":{\"links\":{\"type\":1.5}}}", "network.links.type");
        assertInvalid("{\"network\":{\"links\":{\"type\":null}}}", "network.links.type");
        assertInvalid("{\"network\":{\"links\":{\"type\":18}}}", "network.links.type");
        assertInvalid("{\"network\":{\"links\":{\"type\":-2}}}", "network.links.type");

        assertInvalid("{\"network\":{\"links\":{\"typeName\":\"\"}}}", "network.links.typeName");
        assertInvalid("{\"network\":{\"links\":{\"typeName\":\" WIFI\"}}}", "network.links.typeName");
        assertInvalid("{\"network\":{\"links\":{\"typeName\":1}}}", "network.links.typeName");
        assertInvalid("{\"network\":{\"links\":{\"typeName\":null}}}", "network.links.typeName");

        assertInvalid("{\"network\":{\"links\":{\"interfaceName\":\"\"}}}", "network.links.interfaceName");
        assertInvalid("{\"network\":{\"links\":{\"interfaceName\":\" wlan0\"}}}",
                "network.links.interfaceName");
        assertInvalid("{\"network\":{\"links\":{\"interfaceName\":\"abcdefghijklmnop\"}}}",
                "network.links.interfaceName");
        assertInvalid("{\"network\":{\"links\":{\"interfaceName\":1}}}", "network.links.interfaceName");

        assertInvalid("{\"network\":{\"links\":{\"dnsServers\":\"8.8.8.8\"}}}",
                "network.links.dnsServers");
        assertInvalid("{\"network\":{\"links\":{\"dnsServers\":[1]}}}",
                "network.links.dnsServers[0]");
        assertInvalid("{\"network\":{\"links\":{\"dnsServers\":[null]}}}",
                "network.links.dnsServers[0]");
        assertInvalid("{\"network\":{\"links\":{\"dnsServers\":[\"localhost\"]}}}",
                "network.links.dnsServers[0]");
        assertInvalid("{\"network\":{\"links\":{\"dnsServers\":[\"8.8.8.8\",\"8.8.8.8\"]}}}",
                "network.links.dnsServers[1]");

        assertInvalid("{\"network\":{\"links\":{\"gatewayIpv4\":\"bad\"}}}",
                "network.links.gatewayIpv4");
        assertInvalid("{\"network\":{\"links\":{\"gatewayIpv4\":1}}}",
                "network.links.gatewayIpv4");

        assertInvalid("{\"network\":{\"links\":{\"mtu\":\"1500\"}}}", "network.links.mtu");
        assertInvalid("{\"network\":{\"links\":{\"mtu\":null}}}", "network.links.mtu");
        assertInvalid("{\"network\":{\"links\":{\"mtu\":67}}}", "network.links.mtu");
        assertInvalid("{\"network\":{\"links\":{\"mtu\":65537}}}", "network.links.mtu");
        assertInvalid("{\"network\":{\"links\":{\"mtu\":1500.5}}}", "network.links.mtu");

        // private DNS / proxy / DHCP
        assertInvalid("{\"network\":{\"links\":{\"privateDnsActive\":null}}}",
                "network.links.privateDnsActive");
        assertInvalid("{\"network\":{\"links\":{\"privateDnsActive\":1}}}",
                "network.links.privateDnsActive");
        assertInvalid("{\"network\":{\"links\":{\"privateDnsServerName\":\"\"}}}",
                "network.links.privateDnsServerName");
        assertInvalid("{\"network\":{\"links\":{\"privateDnsServerName\":\" dns\"}}}",
                "network.links.privateDnsServerName");
        assertInvalid("{\"network\":{\"links\":{\"privateDnsServerName\":1}}}",
                "network.links.privateDnsServerName");
        assertInvalid("{\"network\":{\"links\":{\"proxyHost\":\"\"}}}", "network.links.proxyHost");
        assertInvalid("{\"network\":{\"links\":{\"proxyHost\":\" host\"}}}", "network.links.proxyHost");
        assertInvalid("{\"network\":{\"links\":{\"proxyPort\":null}}}", "network.links.proxyPort");
        assertInvalid("{\"network\":{\"links\":{\"proxyPort\":\"80\"}}}", "network.links.proxyPort");
        assertInvalid("{\"network\":{\"links\":{\"proxyPort\":1.5}}}", "network.links.proxyPort");
        assertInvalid("{\"network\":{\"links\":{\"proxyPort\":65536}}}", "network.links.proxyPort");
        assertInvalid("{\"network\":{\"links\":{\"proxyPort\":-1}}}", "network.links.proxyPort");
        assertInvalid("{\"network\":{\"links\":{\"dhcpServerIpv4\":\"bad\"}}}",
                "network.links.dhcpServerIpv4");
        assertInvalid("{\"network\":{\"links\":{\"dhcpServerIpv4\":1}}}",
                "network.links.dhcpServerIpv4");
        // netmaskIpv4: same optional nullable IPv4 rules as gatewayIpv4/dhcpServerIpv4
        assertInvalid("{\"network\":{\"links\":{\"netmaskIpv4\":\"bad\"}}}",
                "network.links.netmaskIpv4");
        assertInvalid("{\"network\":{\"links\":{\"netmaskIpv4\":\"\"}}}",
                "network.links.netmaskIpv4");
        assertInvalid("{\"network\":{\"links\":{\"netmaskIpv4\":\" 255.255.255.0\"}}}",
                "network.links.netmaskIpv4");
        assertInvalid("{\"network\":{\"links\":{\"netmaskIpv4\":\"::1\"}}}",
                "network.links.netmaskIpv4");
        assertInvalid("{\"network\":{\"links\":{\"netmaskIpv4\":1}}}",
                "network.links.netmaskIpv4");
        assertInvalid("{\"network\":{\"links\":{\"netmaskIpv4\":true}}}",
                "network.links.netmaskIpv4");
        assertInvalid("{\"network\":{\"links\":{\"netmaskIpv4\":[]}}}",
                "network.links.netmaskIpv4");
        assertInvalid("{\"network\":{\"links\":{\"netmaskIpv4\":{}}}}",
                "network.links.netmaskIpv4");
        assertInvalid("{\"network\":{\"links\":{\"leaseDurationSeconds\":null}}}",
                "network.links.leaseDurationSeconds");
        assertInvalid("{\"network\":{\"links\":{\"leaseDurationSeconds\":\"1\"}}}",
                "network.links.leaseDurationSeconds");
        assertInvalid("{\"network\":{\"links\":{\"leaseDurationSeconds\":-1}}}",
                "network.links.leaseDurationSeconds");
        assertInvalid("{\"network\":{\"links\":{\"leaseDurationSeconds\":1.1}}}",
                "network.links.leaseDurationSeconds");
        assertInvalid("{\"network\":{\"links\":{\"domains\":\"\"}}}", "network.links.domains");
        assertInvalid("{\"network\":{\"links\":{\"domains\":\" a\"}}}", "network.links.domains");
        assertInvalid("{\"network\":{\"links\":{\"domains\":true}}}", "network.links.domains");
    }

    @Test
    public void testNetworkLinksApiArgs() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"network\":{\"links\":{\"connected\":true}}}");
        try {
            config.isLinkBooleanConfigured("enabled");
            fail("expected IllegalArgumentException for enabled on link boolean API");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getLinkInt("rssi", 0);
            fail("expected IllegalArgumentException for rssi on link int API");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getLinkString("ssid");
            fail("expected IllegalArgumentException for ssid on link string API");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getLinkString(null);
            fail("expected IllegalArgumentException for null link string key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getLinkBoolean("proxyPort", false);
            fail("expected IllegalArgumentException for proxyPort on boolean API");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
        try {
            config.getLinkInt("privateDnsActive", 0);
            fail("expected IllegalArgumentException for privateDnsActive on int API");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("key"));
        }
    }

    @Test
    public void testExactJsonNumberIntRejectsDecimalStrings() {
        // android.telephony: exact JSON Number only (decimal strings rejected)
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":\"2\",\"slots\":[]}}}",
                "android.telephony.phoneCount");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":2,\"slots\":["
                        + "{\"slotIndex\":\"0\"}]}}}",
                "android.telephony.slots[0].slotIndex");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":["
                        + "{\"slotIndex\":0,\"simState\":\"5\"}]}}}",
                "android.telephony.slots[0].simState");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"dataNetworkType\":\"13\"}}}",
                "android.telephony.dataNetworkType");
        assertInvalid("{\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[],"
                        + "\"phoneType\":\"1\"}}}",
                "android.telephony.phoneType");

        // network.wifi: exact JSON Number only
        assertInvalid("{\"network\":{\"wifi\":{\"rssi\":\"-55\"}}}", "network.wifi.rssi");
        assertInvalid("{\"network\":{\"wifi\":{\"rssi\":\"0\"}}}", "network.wifi.rssi");
        assertInvalid("{\"network\":{\"wifi\":{\"linkSpeedMbps\":\"100\"}}}",
                "network.wifi.linkSpeedMbps");
        assertInvalid("{\"network\":{\"wifi\":{\"frequencyMhz\":\"5180\"}}}",
                "network.wifi.frequencyMhz");
        assertInvalid("{\"network\":{\"wifi\":{\"networkId\":\"1\"}}}", "network.wifi.networkId");
        assertInvalid("{\"network\":{\"wifi\":{\"networkId\":\"-1\"}}}", "network.wifi.networkId");

        // valid numeric Number boundaries still pass after the stricter helper
        TraceEnvironmentConfig tel = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{"
                + "\"phoneCount\":1,"
                + "\"dataNetworkType\":0,"
                + "\"phoneType\":3,"
                + "\"slots\":[{\"slotIndex\":0,\"simState\":11}]}}}"
        );
        assertEquals(1, tel.getTelephonyPhoneCount(0));
        assertEquals(0, tel.getTelephonyInt("dataNetworkType", 9));
        assertEquals(3, tel.getTelephonyInt("phoneType", 9));
        assertEquals(11, tel.getTelephonySlotSimState(0, 9));

        TraceEnvironmentConfig wifi = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"wifi\":{"
                + "\"rssi\":-127,\"linkSpeedMbps\":0,\"frequencyMhz\":100000,\"networkId\":-1}}}"
        );
        assertEquals(-127, wifi.getWifiInt("rssi", 0));
        assertEquals(0, wifi.getWifiInt("linkSpeedMbps", 1));
        assertEquals(100000, wifi.getWifiInt("frequencyMhz", 0));
        assertEquals(-1, wifi.getWifiInt("networkId", 0));

        // network.interfaces still accepts unsigned decimal integer strings (unchanged helper)
        TraceEnvironmentConfig ifaces = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"interfaces\":["
                + "{\"name\":\"lo\",\"index\":\"1\",\"ipv4\":\"127.0.0.1\",\"flags\":\"73\"}"
                + "]}}"
        );
        assertTrue(ifaces.isNetworkInterfacesConfigured());
        assertEquals(1, ifaces.getNetworkInterfaces().get(0).getIndex());
        assertEquals(Integer.valueOf(73), ifaces.getNetworkInterfaces().get(0).getFlags());
    }

    @Test
    public void testAndroidPackagesConfig() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"packageName\":\"com.demo.app\","
                + "\"packages\":["
                + "{\"packageName\":\"com.demo.app\","
                + "\"versionName\":\"1.0.0\","
                + "\"versionCode\":100,"
                + "\"sourceDir\":\"/data/app/~~fixed/com.demo.app-fixed/base.apk\","
                + "\"dataDir\":\"/data/user/0/com.demo.app\","
                + "\"uid\":10086,"
                + "\"enabled\":true,"
                + "\"systemApp\":false,"
                + "\"installerPackageName\":\"com.traceai.installer\","
                + "\"initiatingPackageName\":\"com.traceai.initiator\","
                + "\"originatingPackageName\":\"com.traceai.originator\","
                + "\"firstInstallTimeMillis\":1000,"
                + "\"lastUpdateTimeMillis\":2000,"
                + "\"permissions\":{"
                + "\"android.permission.INTERNET\":true,"
                + "\"android.permission.CAMERA\":false,"
                + "\"com.traceai.permission.CUSTOM\":true},"
                + "\"signaturesHex\":[\"DEADBEEF\",\"0011aAbB\"],"
                + "\"signingCertificateHistoryHex\":[\"FFEEDDCC\",\"BbAa0099\"]},"
                + "{\"packageName\":\"com.traceai.marker\","
                + "\"versionName\":\"TRACEAI_PKG_VERSION_MARKER_V1\","
                + "\"versionCode\":0,"
                + "\"sourceDir\":null,"
                + "\"dataDir\":null,"
                + "\"uid\":0,"
                + "\"enabled\":false,"
                + "\"systemApp\":true,"
                + "\"installerPackageName\":null,"
                + "\"initiatingPackageName\":null,"
                + "\"originatingPackageName\":null,"
                + "\"firstInstallTimeMillis\":0,"
                + "\"lastUpdateTimeMillis\":0,"
                + "\"permissions\":{},"
                + "\"signaturesHex\":[],"
                + "\"signingCertificateHistoryHex\":[]}"
                + "]}}"
        );

        // root android.packageName behavior unchanged
        assertEquals("com.demo.app", config.getAndroidPackageName(null));

        assertTrue(config.isAndroidPackagesConfigured());
        List<TraceEnvironmentConfig.PackageConfig> list = config.getAndroidPackages();
        assertEquals(2, list.size());

        TraceEnvironmentConfig.PackageConfig demo = list.get(0);
        assertEquals("com.demo.app", demo.getPackageName());
        assertEquals("1.0.0", demo.getVersionName());
        assertEquals(Integer.valueOf(100), demo.getVersionCode());
        assertEquals("/data/app/~~fixed/com.demo.app-fixed/base.apk", demo.getSourceDir());
        assertEquals("/data/user/0/com.demo.app", demo.getDataDir());
        assertEquals(Integer.valueOf(10086), demo.getUid());
        assertEquals(Boolean.TRUE, demo.getEnabled());
        assertEquals(Boolean.FALSE, demo.getSystemApp());
        // all optional fields present with values
        assertTrue(demo.isVersionNameConfigured());
        assertTrue(demo.isVersionCodeConfigured());
        assertTrue(demo.isSourceDirConfigured());
        assertTrue(demo.isDataDirConfigured());
        assertTrue(demo.isUidConfigured());
        assertTrue(demo.isEnabledConfigured());
        assertTrue(demo.isSystemAppConfigured());
        assertEquals("com.traceai.installer", demo.getInstallerPackageName());
        assertEquals("com.traceai.initiator", demo.getInitiatingPackageName());
        assertEquals("com.traceai.originator", demo.getOriginatingPackageName());
        assertEquals(Long.valueOf(1000L), demo.getFirstInstallTimeMillis());
        assertEquals(Long.valueOf(2000L), demo.getLastUpdateTimeMillis());
        assertTrue(demo.isInstallerPackageNameConfigured());
        assertTrue(demo.isInitiatingPackageNameConfigured());
        assertTrue(demo.isOriginatingPackageNameConfigured());
        assertTrue(demo.isFirstInstallTimeMillisConfigured());
        assertTrue(demo.isLastUpdateTimeMillisConfigured());
        // permissions: granted / denied / custom
        assertTrue(demo.isPermissionsConfigured());
        Map<String, Boolean> demoPerms = demo.getPermissions();
        assertEquals(3, demoPerms.size());
        assertTrue(demo.isPermissionConfigured("android.permission.INTERNET"));
        assertTrue(demo.getPermissionGranted("android.permission.INTERNET", false));
        assertTrue(demo.isPermissionConfigured("android.permission.CAMERA"));
        assertFalse(demo.getPermissionGranted("android.permission.CAMERA", true));
        assertTrue(demo.isPermissionConfigured("com.traceai.permission.CUSTOM"));
        assertTrue(demo.getPermissionGranted("com.traceai.permission.CUSTOM", false));
        assertFalse(demo.isPermissionConfigured("android.permission.READ_SMS"));
        assertFalse(demo.getPermissionGranted("android.permission.READ_SMS", false));
        assertTrue(demo.getPermissionGranted("android.permission.READ_SMS", true));
        // signaturesHex: order preserved, lowercased, configured
        assertTrue(demo.isSignaturesConfigured());
        List<String> demoSigs = demo.getSignatureHexes();
        assertEquals(2, demoSigs.size());
        assertEquals("deadbeef", demoSigs.get(0));
        assertEquals("0011aabb", demoSigs.get(1));
        // signingCertificateHistoryHex: rotation history (current still signaturesHex)
        assertTrue(demo.isSigningCertificateHistoryConfigured());
        List<String> demoHistory = demo.getSigningCertificateHistoryHexes();
        assertEquals(2, demoHistory.size());
        assertEquals("ffeeddcc", demoHistory.get(0));
        assertEquals("bbaa0099", demoHistory.get(1));

        TraceEnvironmentConfig.PackageConfig marker = config.getAndroidPackage("com.traceai.marker");
        assertTrue(marker != null);
        assertEquals("com.traceai.marker", marker.getPackageName());
        assertEquals("TRACEAI_PKG_VERSION_MARKER_V1", marker.getVersionName());
        assertEquals(Integer.valueOf(0), marker.getVersionCode());
        assertNull(marker.getSourceDir());
        assertNull(marker.getDataDir());
        assertEquals(Integer.valueOf(0), marker.getUid());
        assertEquals(Boolean.FALSE, marker.getEnabled());
        assertEquals(Boolean.TRUE, marker.getSystemApp());
        // two-package entry: present scalars + explicit null paths/names
        assertTrue(marker.isVersionNameConfigured());
        assertTrue(marker.isVersionCodeConfigured());
        assertTrue(marker.isSourceDirConfigured());
        assertTrue(marker.isDataDirConfigured());
        assertTrue(marker.isUidConfigured());
        assertTrue(marker.isEnabledConfigured());
        assertTrue(marker.isSystemAppConfigured());
        assertNull(marker.getSourceDir());
        assertNull(marker.getDataDir());
        assertTrue(marker.isInstallerPackageNameConfigured());
        assertNull(marker.getInstallerPackageName());
        assertTrue(marker.isInitiatingPackageNameConfigured());
        assertNull(marker.getInitiatingPackageName());
        assertTrue(marker.isOriginatingPackageNameConfigured());
        assertNull(marker.getOriginatingPackageName());
        assertTrue(marker.isFirstInstallTimeMillisConfigured());
        assertEquals(Long.valueOf(0L), marker.getFirstInstallTimeMillis());
        assertTrue(marker.isLastUpdateTimeMillisConfigured());
        assertEquals(Long.valueOf(0L), marker.getLastUpdateTimeMillis());
        // explicit empty permissions object: configured=true, empty map
        assertTrue(marker.isPermissionsConfigured());
        assertTrue(marker.getPermissions().isEmpty());
        assertFalse(marker.isPermissionConfigured("android.permission.INTERNET"));
        assertFalse(marker.getPermissionGranted("android.permission.INTERNET", false));
        // explicit empty signaturesHex: configured=true, empty list
        assertTrue(marker.isSignaturesConfigured());
        assertTrue(marker.getSignatureHexes().isEmpty());
        // explicit empty signingCertificateHistoryHex: configured=true, empty list
        assertTrue(marker.isSigningCertificateHistoryConfigured());
        assertTrue(marker.getSigningCertificateHistoryHexes().isEmpty());

        assertNull(config.getAndroidPackage("com.missing.app"));

        // long max boundary (JSON integer form of Long.MAX remains valid)
        TraceEnvironmentConfig longMax = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"packages\":[{\"packageName\":\"com.long.max\","
                + "\"firstInstallTimeMillis\":" + Long.MAX_VALUE + ","
                + "\"lastUpdateTimeMillis\":" + Long.MAX_VALUE + "}]}}"
        );
        TraceEnvironmentConfig.PackageConfig longMaxPkg = longMax.getAndroidPackage("com.long.max");
        assertTrue(longMaxPkg != null);
        assertEquals(Long.valueOf(Long.MAX_VALUE), longMaxPkg.getFirstInstallTimeMillis());
        assertEquals(Long.valueOf(Long.MAX_VALUE), longMaxPkg.getLastUpdateTimeMillis());

        // valid exact integral scientific notation within long range
        TraceEnvironmentConfig sciOk = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"packages\":[{\"packageName\":\"com.sci.ok\","
                + "\"firstInstallTimeMillis\":1.71e12,"
                + "\"lastUpdateTimeMillis\":1.72e12}]}}"
        );
        TraceEnvironmentConfig.PackageConfig sciOkPkg = sciOk.getAndroidPackage("com.sci.ok");
        assertTrue(sciOkPkg != null);
        assertEquals(Long.valueOf(1710000000000L), sciOkPkg.getFirstInstallTimeMillis());
        assertEquals(Long.valueOf(1720000000000L), sciOkPkg.getLastUpdateTimeMillis());

        // explicit null versionName/sourceDir/dataDir/installer names → configured=true, getter=null
        TraceEnvironmentConfig sparse = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"packages\":["
                + "{\"packageName\":\"com.only.name\","
                + "\"versionName\":null,\"sourceDir\":null,\"dataDir\":null,"
                + "\"installerPackageName\":null,\"initiatingPackageName\":null,"
                + "\"originatingPackageName\":null}"
                + "]}}"
        );
        assertTrue(sparse.isAndroidPackagesConfigured());
        TraceEnvironmentConfig.PackageConfig only = sparse.getAndroidPackage("com.only.name");
        assertTrue(only != null);
        assertNull(only.getVersionName());
        assertTrue(only.isVersionNameConfigured());
        assertNull(only.getSourceDir());
        assertTrue(only.isSourceDirConfigured());
        assertNull(only.getDataDir());
        assertTrue(only.isDataDirConfigured());
        assertNull(only.getInstallerPackageName());
        assertTrue(only.isInstallerPackageNameConfigured());
        assertNull(only.getInitiatingPackageName());
        assertTrue(only.isInitiatingPackageNameConfigured());
        assertNull(only.getOriginatingPackageName());
        assertTrue(only.isOriginatingPackageNameConfigured());
        // omitted scalar optionals → configured=false, getter=null
        assertNull(only.getVersionCode());
        assertFalse(only.isVersionCodeConfigured());
        assertNull(only.getUid());
        assertFalse(only.isUidConfigured());
        assertNull(only.getEnabled());
        assertFalse(only.isEnabledConfigured());
        assertNull(only.getSystemApp());
        assertFalse(only.isSystemAppConfigured());
        assertNull(only.getFirstInstallTimeMillis());
        assertFalse(only.isFirstInstallTimeMillisConfigured());
        assertNull(only.getLastUpdateTimeMillis());
        assertFalse(only.isLastUpdateTimeMillisConfigured());

        // fully missing optionals on a package entry
        TraceEnvironmentConfig bare = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"packages\":[{\"packageName\":\"com.bare.pkg\"}]}}"
        );
        TraceEnvironmentConfig.PackageConfig barePkg = bare.getAndroidPackage("com.bare.pkg");
        assertTrue(barePkg != null);
        assertFalse(barePkg.isVersionNameConfigured());
        assertNull(barePkg.getVersionName());
        assertFalse(barePkg.isVersionCodeConfigured());
        assertNull(barePkg.getVersionCode());
        assertFalse(barePkg.isSourceDirConfigured());
        assertNull(barePkg.getSourceDir());
        assertFalse(barePkg.isDataDirConfigured());
        assertNull(barePkg.getDataDir());
        assertFalse(barePkg.isUidConfigured());
        assertNull(barePkg.getUid());
        assertFalse(barePkg.isEnabledConfigured());
        assertNull(barePkg.getEnabled());
        assertFalse(barePkg.isSystemAppConfigured());
        assertNull(barePkg.getSystemApp());
        assertFalse(barePkg.isInstallerPackageNameConfigured());
        assertNull(barePkg.getInstallerPackageName());
        assertFalse(barePkg.isInitiatingPackageNameConfigured());
        assertNull(barePkg.getInitiatingPackageName());
        assertFalse(barePkg.isOriginatingPackageNameConfigured());
        assertNull(barePkg.getOriginatingPackageName());
        assertFalse(barePkg.isFirstInstallTimeMillisConfigured());
        assertNull(barePkg.getFirstInstallTimeMillis());
        assertFalse(barePkg.isLastUpdateTimeMillisConfigured());
        assertNull(barePkg.getLastUpdateTimeMillis());
        // missing permissions node: configured=false, empty immutable map
        assertFalse(barePkg.isPermissionsConfigured());
        assertTrue(barePkg.getPermissions().isEmpty());
        assertFalse(barePkg.isPermissionConfigured("android.permission.INTERNET"));
        assertTrue(barePkg.getPermissionGranted("android.permission.INTERNET", true));
        // missing signaturesHex: configured=false, empty immutable list
        assertFalse(barePkg.isSignaturesConfigured());
        assertTrue(barePkg.getSignatureHexes().isEmpty());
        // missing signingCertificateHistoryHex: configured=false, empty immutable list
        assertFalse(barePkg.isSigningCertificateHistoryConfigured());
        assertTrue(barePkg.getSigningCertificateHistoryHexes().isEmpty());

        // signaturesHex immutability
        try {
            demo.getSignatureHexes().add("00");
            fail("expected UnsupportedOperationException for signatureHexes list");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable
        }
        try {
            barePkg.getSignatureHexes().add("00");
            fail("expected UnsupportedOperationException for missing signatureHexes list");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable
        }
        try {
            marker.getSignatureHexes().add("00");
            fail("expected UnsupportedOperationException for empty signatureHexes list");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable
        }
        // signingCertificateHistoryHex immutability
        try {
            demo.getSigningCertificateHistoryHexes().add("00");
            fail("expected UnsupportedOperationException for history hex list");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable
        }
        try {
            barePkg.getSigningCertificateHistoryHexes().add("00");
            fail("expected UnsupportedOperationException for missing history hex list");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable
        }
        try {
            marker.getSigningCertificateHistoryHexes().add("00");
            fail("expected UnsupportedOperationException for empty history hex list");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable
        }

        // empty array is configured
        TraceEnvironmentConfig emptyArr = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packages\":[]}}");
        assertTrue(emptyArr.isAndroidPackagesConfigured());
        assertTrue(emptyArr.getAndroidPackages().isEmpty());

        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidPackagesConfigured());
        assertTrue(missing.getAndroidPackages().isEmpty());
        assertEquals("fallback.pkg", missing.getAndroidPackageName("fallback.pkg"));
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.keep.me\"}}");
        assertFalse(missingAndroid.isAndroidPackagesConfigured());
        assertEquals("com.keep.me", missingAndroid.getAndroidPackageName(null));

        // immutability
        try {
            list.add(demo);
            fail("expected UnsupportedOperationException on list.add");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable
        }
        try {
            emptyArr.getAndroidPackages().add(demo);
            fail("expected UnsupportedOperationException on empty list.add");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable
        }
        try {
            missing.getAndroidPackages().add(demo);
            fail("expected UnsupportedOperationException on missing list.add");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable
        }
        try {
            demo.getPermissions().put("android.permission.INTERNET", false);
            fail("expected UnsupportedOperationException on permissions map put");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable
        }
        try {
            barePkg.getPermissions().put("android.permission.INTERNET", true);
            fail("expected UnsupportedOperationException on missing permissions map put");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable
        }
    }

    @Test
    public void testAndroidPackagesValidation() {
        assertInvalid("{\"android\":{\"packages\":{}}}", "android.packages");
        assertInvalid("{\"android\":{\"packages\":\"x\"}}", "android.packages");
        assertInvalid("{\"android\":{\"packages\":[\"com.demo.app\"]}}", "android.packages[0]");

        // missing required packageName
        assertInvalid("{\"android\":{\"packages\":[{\"versionCode\":1}]}}",
                "android.packages[0].packageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":1}]}}",
                "android.packages[0].packageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"\"}]}}",
                "android.packages[0].packageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\" com.demo\"}]}}",
                "android.packages[0].packageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.demo \"}]}}",
                "android.packages[0].packageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com..demo\"}]}}",
                "android.packages[0].packageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\".com.demo\"}]}}",
                "android.packages[0].packageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.demo.\"}]}}",
                "android.packages[0].packageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.demo-app\"}]}}",
                "android.packages[0].packageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.demo app\"}]}}",
                "android.packages[0].packageName");

        // duplicates
        assertInvalid("{\"android\":{\"packages\":["
                        + "{\"packageName\":\"com.demo.app\"},"
                        + "{\"packageName\":\"com.demo.app\"}"
                        + "]}}",
                "android.packages[1].packageName");

        // versionName
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"versionName\":1}]}}",
                "android.packages[0].versionName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"versionName\":\"\"}]}}",
                "android.packages[0].versionName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"versionName\":\" 1.0\"}]}}",
                "android.packages[0].versionName");

        // versionCode exact number
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"versionCode\":\"1\"}]}}",
                "android.packages[0].versionCode");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"versionCode\":1.5}]}}",
                "android.packages[0].versionCode");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"versionCode\":-1}]}}",
                "android.packages[0].versionCode");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"versionCode\":null}]}}",
                "android.packages[0].versionCode");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"versionCode\":99999999999999999999999999999}]}}",
                "android.packages[0].versionCode");

        // paths
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"sourceDir\":\"data/app\"}]}}",
                "android.packages[0].sourceDir");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"sourceDir\":\"\"}]}}",
                "android.packages[0].sourceDir");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"sourceDir\":\" /data\"}]}}",
                "android.packages[0].sourceDir");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"sourceDir\":1}]}}",
                "android.packages[0].sourceDir");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"dataDir\":\"relative\"}]}}",
                "android.packages[0].dataDir");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"dataDir\":\"\"}]}}",
                "android.packages[0].dataDir");

        // uid
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"uid\":\"1000\"}]}}",
                "android.packages[0].uid");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"uid\":-1}]}}",
                "android.packages[0].uid");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"uid\":1.2}]}}",
                "android.packages[0].uid");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"uid\":null}]}}",
                "android.packages[0].uid");

        // booleans
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"enabled\":1}]}}",
                "android.packages[0].enabled");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"enabled\":null}]}}",
                "android.packages[0].enabled");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"enabled\":\"true\"}]}}",
                "android.packages[0].enabled");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"systemApp\":0}]}}",
                "android.packages[0].systemApp");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"systemApp\":null}]}}",
                "android.packages[0].systemApp");

        // unknown keys
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"sharedUserId\":\"x\"}]}}",
                "android.packages[0].sharedUserId");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"flags\":1}]}}",
                "android.packages[0].flags");

        // installer / initiating package names
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"installerPackageName\":1}]}}",
                "android.packages[0].installerPackageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"installerPackageName\":\"\"}]}}",
                "android.packages[0].installerPackageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"installerPackageName\":\"com..bad\"}]}}",
                "android.packages[0].installerPackageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"initiatingPackageName\":\" bad.name\"}]}}",
                "android.packages[0].initiatingPackageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"initiatingPackageName\":true}]}}",
                "android.packages[0].initiatingPackageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"originatingPackageName\":1}]}}",
                "android.packages[0].originatingPackageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"originatingPackageName\":\"\"}]}}",
                "android.packages[0].originatingPackageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"originatingPackageName\":\"com..bad\"}]}}",
                "android.packages[0].originatingPackageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"originatingPackageName\":\" bad.name\"}]}}",
                "android.packages[0].originatingPackageName");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"originatingPackageName\":true}]}}",
                "android.packages[0].originatingPackageName");

        // install times: strings / null / fractions / overflow / order
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"firstInstallTimeMillis\":\"1\"}]}}",
                "android.packages[0].firstInstallTimeMillis");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"firstInstallTimeMillis\":null}]}}",
                "android.packages[0].firstInstallTimeMillis");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"lastUpdateTimeMillis\":1.5}]}}",
                "android.packages[0].lastUpdateTimeMillis");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"firstInstallTimeMillis\":-1}]}}",
                "android.packages[0].firstInstallTimeMillis");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"lastUpdateTimeMillis\":99999999999999999999999999999}]}}",
                "android.packages[0].lastUpdateTimeMillis");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"firstInstallTimeMillis\":2000,\"lastUpdateTimeMillis\":1000}]}}",
                "android.packages[0].lastUpdateTimeMillis");
        // scientific-notation 2^63 (Long.MAX+1) must not saturate through double cast
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"firstInstallTimeMillis\":9.223372036854776e18}]}}",
                "android.packages[0].firstInstallTimeMillis");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"lastUpdateTimeMillis\":9.223372036854776e18}]}}",
                "android.packages[0].lastUpdateTimeMillis");

        // permissions object structure / keys / values
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"permissions\":[]}]}}",
                "android.packages[0].permissions");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"permissions\":\"x\"}]}}",
                "android.packages[0].permissions");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"permissions\":{\"\":true}}]}}",
                "android.packages[0].permissions.");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"permissions\":{\" android.permission.INTERNET\":true}}]}}",
                "android.packages[0].permissions. android.permission.INTERNET");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"permissions\":{\"android..permission\":true}}]}}",
                "android.packages[0].permissions.android..permission");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"permissions\":{\"android.permission.INTERNET\":1}}]}}",
                "android.packages[0].permissions.android.permission.INTERNET");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"permissions\":{\"android.permission.INTERNET\":null}}]}}",
                "android.packages[0].permissions.android.permission.INTERNET");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"permissions\":{\"android.permission.INTERNET\":\"true\"}}]}}",
                "android.packages[0].permissions.android.permission.INTERNET");

        // signaturesHex array structure / items
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signaturesHex\":{}}]}}",
                "android.packages[0].signaturesHex");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signaturesHex\":\"deadbeef\"}]}}",
                "android.packages[0].signaturesHex");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signaturesHex\":1}]}}",
                "android.packages[0].signaturesHex");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signaturesHex\":[1]}]}}",
                "android.packages[0].signaturesHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signaturesHex\":[null]}]}}",
                "android.packages[0].signaturesHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signaturesHex\":[\"\"]}]}}",
                "android.packages[0].signaturesHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signaturesHex\":[\"abc\"]}]}}",
                "android.packages[0].signaturesHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signaturesHex\":[\"zz\"]}]}}",
                "android.packages[0].signaturesHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signaturesHex\":[\"de ad\"]}]}}",
                "android.packages[0].signaturesHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signaturesHex\":[\"AA:BB\"]}]}}",
                "android.packages[0].signaturesHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signaturesHex\":[\"aa\",\"AA\"]}]}}",
                "android.packages[0].signaturesHex[1]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signaturesHex\":[\"deadbeef\",\"deadbeef\"]}]}}",
                "android.packages[0].signaturesHex[1]");

        // signingCertificateHistoryHex: same strict rules as signaturesHex
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signingCertificateHistoryHex\":{}}]}}",
                "android.packages[0].signingCertificateHistoryHex");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signingCertificateHistoryHex\":\"deadbeef\"}]}}",
                "android.packages[0].signingCertificateHistoryHex");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signingCertificateHistoryHex\":1}]}}",
                "android.packages[0].signingCertificateHistoryHex");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signingCertificateHistoryHex\":[1]}]}}",
                "android.packages[0].signingCertificateHistoryHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signingCertificateHistoryHex\":[null]}]}}",
                "android.packages[0].signingCertificateHistoryHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signingCertificateHistoryHex\":[\"\"]}]}}",
                "android.packages[0].signingCertificateHistoryHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signingCertificateHistoryHex\":[\"abc\"]}]}}",
                "android.packages[0].signingCertificateHistoryHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signingCertificateHistoryHex\":[\"zz\"]}]}}",
                "android.packages[0].signingCertificateHistoryHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signingCertificateHistoryHex\":[\"de ad\"]}]}}",
                "android.packages[0].signingCertificateHistoryHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signingCertificateHistoryHex\":[\"AA:BB\"]}]}}",
                "android.packages[0].signingCertificateHistoryHex[0]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signingCertificateHistoryHex\":[\"aa\",\"AA\"]}]}}",
                "android.packages[0].signingCertificateHistoryHex[1]");
        assertInvalid("{\"android\":{\"packages\":[{\"packageName\":\"com.a\","
                        + "\"signingCertificateHistoryHex\":[\"deadbeef\",\"deadbeef\"]}]}}",
                "android.packages[0].signingCertificateHistoryHex[1]");
    }

    @Test
    public void testAndroidPackagesApiArgs() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"packages\":[{\"packageName\":\"com.demo.app\","
                + "\"permissions\":{\"android.permission.INTERNET\":true}}]}}"
        );
        try {
            config.getAndroidPackage(null);
            fail("expected IllegalArgumentException for null packageName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("packageName"));
        }
        try {
            config.getAndroidPackage("");
            fail("expected IllegalArgumentException for empty packageName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("packageName"));
        }
        try {
            config.getAndroidPackage("   ");
            fail("expected IllegalArgumentException for blank packageName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("packageName"));
        }

        TraceEnvironmentConfig.PackageConfig pkg = config.getAndroidPackage("com.demo.app");
        assertTrue(pkg != null);
        try {
            pkg.isPermissionConfigured(null);
            fail("expected IllegalArgumentException for null permission");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("permission"));
        }
        try {
            pkg.getPermissionGranted("", false);
            fail("expected IllegalArgumentException for empty permission");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("permission"));
        }
        try {
            pkg.isPermissionConfigured("   ");
            fail("expected IllegalArgumentException for blank permission");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("permission"));
        }
        try {
            pkg.getPermissionGranted("android..permission", false);
            fail("expected IllegalArgumentException for invalid permission syntax");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("permission"));
        }
        try {
            pkg.isPermissionConfigured(" android.permission.INTERNET");
            fail("expected IllegalArgumentException for leading whitespace permission");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("permission"));
        }
    }

    @Test
    public void testAndroidFeaturesConfig() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"features\":["
                + "{\"name\":\"android.hardware.camera\",\"version\":1},"
                + "{\"name\":\"android.hardware.wifi\"},"
                + "{\"name\":\"com.traceai.feature.CUSTOM\",\"version\":0},"
                + "{\"name\":\"android.software.vulkan.deqp.level\",\"version\":" + Integer.MAX_VALUE + "}"
                + "]}}"
        );

        assertTrue(config.isAndroidFeaturesConfigured());
        List<TraceEnvironmentConfig.FeatureConfig> list = config.getAndroidFeatures();
        assertEquals(4, list.size());

        // config order preserved
        assertEquals("android.hardware.camera", list.get(0).getName());
        assertEquals(Integer.valueOf(1), list.get(0).getVersion());
        assertTrue(list.get(0).isVersionConfigured());

        assertEquals("android.hardware.wifi", list.get(1).getName());
        assertNull(list.get(1).getVersion());
        assertFalse(list.get(1).isVersionConfigured());

        assertEquals("com.traceai.feature.CUSTOM", list.get(2).getName());
        assertEquals(Integer.valueOf(0), list.get(2).getVersion());
        assertTrue(list.get(2).isVersionConfigured());

        assertEquals("android.software.vulkan.deqp.level", list.get(3).getName());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), list.get(3).getVersion());
        assertTrue(list.get(3).isVersionConfigured());

        // exact lookup
        TraceEnvironmentConfig.FeatureConfig camera = config.getAndroidFeature("android.hardware.camera");
        assertTrue(camera != null);
        assertEquals("android.hardware.camera", camera.getName());
        assertEquals(Integer.valueOf(1), camera.getVersion());
        assertTrue(camera.isVersionConfigured());

        TraceEnvironmentConfig.FeatureConfig wifi = config.getAndroidFeature("android.hardware.wifi");
        assertTrue(wifi != null);
        assertNull(wifi.getVersion());
        assertFalse(wifi.isVersionConfigured());

        assertNull(config.getAndroidFeature("android.hardware.absent"));

        // empty array configured
        TraceEnvironmentConfig emptyArr = TraceEnvironmentConfig.parse(
                "{\"android\":{\"features\":[]}}");
        assertTrue(emptyArr.isAndroidFeaturesConfigured());
        assertTrue(emptyArr.getAndroidFeatures().isEmpty());

        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missing.isAndroidFeaturesConfigured());
        assertTrue(missing.getAndroidFeatures().isEmpty());

        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse("{}");
        assertFalse(missingAndroid.isAndroidFeaturesConfigured());
        assertTrue(missingAndroid.getAndroidFeatures().isEmpty());

        // immutability
        try {
            emptyArr.getAndroidFeatures().add(camera);
            fail("expected UnsupportedOperationException for empty features list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        try {
            missing.getAndroidFeatures().add(camera);
            fail("expected UnsupportedOperationException for missing features list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        try {
            config.getAndroidFeatures().add(camera);
            fail("expected UnsupportedOperationException for configured features list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        try {
            config.getAndroidFeatures().clear();
            fail("expected UnsupportedOperationException for clear features list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void testAndroidFeaturesValidation() {
        // type of features node
        assertInvalid("{\"android\":{\"features\":{}}}", "android.features");
        assertInvalid("{\"android\":{\"features\":\"x\"}}", "android.features");
        assertInvalid("{\"android\":{\"features\":1}}", "android.features");
        assertInvalid("{\"android\":{\"features\":[\"android.hardware.camera\"]}}",
                "android.features[0]");

        // name required / type / empty / no-trim / invalid segments
        assertInvalid("{\"android\":{\"features\":[{\"version\":1}]}}",
                "android.features[0].name");
        assertInvalid("{\"android\":{\"features\":[{\"name\":1}]}}",
                "android.features[0].name");
        assertInvalid("{\"android\":{\"features\":[{\"name\":true}]}}",
                "android.features[0].name");
        assertInvalid("{\"android\":{\"features\":[{\"name\":null}]}}",
                "android.features[0].name");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"\"}]}}",
                "android.features[0].name");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\" android.hardware.camera\"}]}}",
                "android.features[0].name");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android.hardware.camera \"}]}}",
                "android.features[0].name");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android..hardware\"}]}}",
                "android.features[0].name");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\".android.hardware\"}]}}",
                "android.features[0].name");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android.hardware.\"}]}}",
                "android.features[0].name");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android.hardware.camera-front\"}]}}",
                "android.features[0].name");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android.hardware camera\"}]}}",
                "android.features[0].name");

        // duplicate names
        assertInvalid("{\"android\":{\"features\":["
                        + "{\"name\":\"android.hardware.camera\"},"
                        + "{\"name\":\"android.hardware.camera\"}]}}",
                "android.features[1].name");

        // version type / null / string / fraction / overflow / range
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android.hardware.camera\","
                        + "\"version\":null}]}}",
                "android.features[0].version");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android.hardware.camera\","
                        + "\"version\":\"1\"}]}}",
                "android.features[0].version");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android.hardware.camera\","
                        + "\"version\":true}]}}",
                "android.features[0].version");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android.hardware.camera\","
                        + "\"version\":1.5}]}}",
                "android.features[0].version");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android.hardware.camera\","
                        + "\"version\":-1}]}}",
                "android.features[0].version");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android.hardware.camera\","
                        + "\"version\":" + (Integer.MAX_VALUE + 1L) + "}]}}",
                "android.features[0].version");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android.hardware.camera\","
                        + "\"version\":99999999999999999999999999999}]}}",
                "android.features[0].version");

        // unknown keys
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android.hardware.camera\","
                        + "\"extra\":1}]}}",
                "android.features[0].extra");
        assertInvalid("{\"android\":{\"features\":[{\"name\":\"android.hardware.camera\","
                        + "\"required\":true}]}}",
                "android.features[0].required");
    }

    @Test
    public void testAndroidFeaturesApiArgs() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"features\":[{\"name\":\"android.hardware.camera\",\"version\":1}]}}"
        );
        try {
            config.getAndroidFeature(null);
            fail("expected IllegalArgumentException for null name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("name"));
        }
        try {
            config.getAndroidFeature("");
            fail("expected IllegalArgumentException for empty name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("name"));
        }
        try {
            config.getAndroidFeature("   ");
            fail("expected IllegalArgumentException for blank name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("name"));
        }
    }

    @Test
    public void testAndroidTeeConfig() {
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"tee\":{"
                + "\"available\":true,"
                + "\"securityLevel\":\"TRUSTED_ENVIRONMENT\","
                + "\"keymasterVersion\":41,"
                + "\"strongBoxAvailable\":false,"
                + "\"marker\":\"TRACEAI_TEE_MARKER_V1\","
                + "\"keyBlobHex\":\"DEADBEEF\","
                + "\"keyAlgorithm\":\"AES\","
                + "\"keyFormat\":\"RAW\""
                + "}}}"
        );
        assertTrue(full.isAndroidTeeConfigured());
        assertTrue(full.isTeeAvailableConfigured());
        assertTrue(full.getTeeAvailable(false));
        assertTrue(full.isTeeSecurityLevelConfigured());
        assertEquals("TRUSTED_ENVIRONMENT", full.getTeeSecurityLevel());
        assertTrue(full.isTeeKeymasterVersionConfigured());
        assertEquals(41, full.getTeeKeymasterVersion(-1));
        assertTrue(full.isTeeStrongBoxAvailableConfigured());
        assertFalse(full.getTeeStrongBoxAvailable(true));
        assertTrue(full.isTeeMarkerConfigured());
        assertEquals("TRACEAI_TEE_MARKER_V1", full.getTeeMarker());
        assertTrue(full.isTeeKeyBlobConfigured());
        assertArrayEquals(new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF},
                full.getTeeKeyBlob());
        assertTrue(full.isTeeKeyAlgorithmConfigured());
        assertEquals("AES", full.getTeeKeyAlgorithm());
        assertTrue(full.isTeeKeyFormatConfigured());
        assertEquals("RAW", full.getTeeKeyFormat());

        // hex normalization to lowercase
        TraceEnvironmentConfig lower = TraceEnvironmentConfig.parse(
                "{\"android\":{\"tee\":{\"keyBlobHex\":\"AaBb\"}}}");
        assertTrue(lower.isTeeKeyBlobConfigured());
        assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xBB}, lower.getTeeKeyBlob());

        // defensive copy of key blob
        byte[] blob = full.getTeeKeyBlob();
        blob[0] = 0x00;
        assertArrayEquals(new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF},
                full.getTeeKeyBlob());

        // keyAlgorithm / keyFormat independent of each other and of keyBlobHex/marker
        TraceEnvironmentConfig algoOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"tee\":{\"keyAlgorithm\":\"HmacSHA256\"}}}");
        assertTrue(algoOnly.isTeeKeyAlgorithmConfigured());
        assertEquals("HmacSHA256", algoOnly.getTeeKeyAlgorithm());
        assertFalse(algoOnly.isTeeKeyFormatConfigured());
        assertNull(algoOnly.getTeeKeyFormat());
        assertFalse(algoOnly.isTeeKeyBlobConfigured());
        assertFalse(algoOnly.isTeeMarkerConfigured());
        TraceEnvironmentConfig formatOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"tee\":{\"keyFormat\":\"PKCS#8\"}}}");
        assertTrue(formatOnly.isTeeKeyFormatConfigured());
        assertEquals("PKCS#8", formatOnly.getTeeKeyFormat());
        assertFalse(formatOnly.isTeeKeyAlgorithmConfigured());
        assertNull(formatOnly.getTeeKeyAlgorithm());

        // explicit empty object: configured, no fields
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{\"android\":{\"tee\":{}}}");
        assertTrue(empty.isAndroidTeeConfigured());
        assertFalse(empty.isTeeAvailableConfigured());
        assertFalse(empty.getTeeAvailable(false));
        assertTrue(empty.getTeeAvailable(true));
        assertFalse(empty.isTeeSecurityLevelConfigured());
        assertNull(empty.getTeeSecurityLevel());
        assertFalse(empty.isTeeKeymasterVersionConfigured());
        assertEquals(7, empty.getTeeKeymasterVersion(7));
        assertFalse(empty.isTeeStrongBoxAvailableConfigured());
        assertTrue(empty.getTeeStrongBoxAvailable(true));
        assertFalse(empty.isTeeMarkerConfigured());
        assertNull(empty.getTeeMarker());
        assertFalse(empty.isTeeKeyBlobConfigured());
        assertNull(empty.getTeeKeyBlob());
        assertFalse(empty.isTeeKeyAlgorithmConfigured());
        assertNull(empty.getTeeKeyAlgorithm());
        assertFalse(empty.isTeeKeyFormatConfigured());
        assertNull(empty.getTeeKeyFormat());

        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missing.isAndroidTeeConfigured());
        assertFalse(missing.isTeeAvailableConfigured());
        assertNull(missing.getTeeSecurityLevel());
        assertNull(missing.getTeeKeyBlob());
        assertFalse(missing.isTeeKeyAlgorithmConfigured());
        assertFalse(missing.isTeeKeyFormatConfigured());

        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse("{}");
        assertFalse(missingAndroid.isAndroidTeeConfigured());

        // enum extremes and int bounds
        TraceEnvironmentConfig software = TraceEnvironmentConfig.parse(
                "{\"android\":{\"tee\":{\"securityLevel\":\"SOFTWARE\",\"keymasterVersion\":0}}}");
        assertEquals("SOFTWARE", software.getTeeSecurityLevel());
        assertEquals(0, software.getTeeKeymasterVersion(9));
        TraceEnvironmentConfig strongbox = TraceEnvironmentConfig.parse(
                "{\"android\":{\"tee\":{\"securityLevel\":\"STRONGBOX\",\"keymasterVersion\":100}}}");
        assertEquals("STRONGBOX", strongbox.getTeeSecurityLevel());
        assertEquals(100, strongbox.getTeeKeymasterVersion(0));

        // marker length boundary 128
        StringBuilder marker128 = new StringBuilder();
        for (int i = 0; i < 128; i++) {
            marker128.append('A');
        }
        TraceEnvironmentConfig markerOk = TraceEnvironmentConfig.parse(
                "{\"android\":{\"tee\":{\"marker\":\"" + marker128 + "\"}}}");
        assertEquals(128, markerOk.getTeeMarker().length());

        // keyAlgorithm / keyFormat length boundary 128
        StringBuilder meta128 = new StringBuilder();
        for (int i = 0; i < 128; i++) {
            meta128.append('K');
        }
        TraceEnvironmentConfig metaOk = TraceEnvironmentConfig.parse(
                "{\"android\":{\"tee\":{\"keyAlgorithm\":\"" + meta128
                        + "\",\"keyFormat\":\"" + meta128 + "\"}}}");
        assertEquals(128, metaOk.getTeeKeyAlgorithm().length());
        assertEquals(128, metaOk.getTeeKeyFormat().length());
    }

    @Test
    public void testAndroidTeeValidation() {
        assertInvalid("{\"android\":{\"tee\":[]}}", "android.tee");
        assertInvalid("{\"android\":{\"tee\":\"x\"}}", "android.tee");
        assertInvalid("{\"android\":{\"tee\":1}}", "android.tee");
        assertInvalid("{\"android\":{\"tee\":true}}", "android.tee");

        // unknown keys
        assertInvalid("{\"android\":{\"tee\":{\"extra\":true}}}", "android.tee.extra");
        assertInvalid("{\"android\":{\"tee\":{\"available\":true,\"foo\":1}}}", "android.tee.foo");

        // available
        assertInvalid("{\"android\":{\"tee\":{\"available\":1}}}", "android.tee.available");
        assertInvalid("{\"android\":{\"tee\":{\"available\":\"true\"}}}", "android.tee.available");
        assertInvalid("{\"android\":{\"tee\":{\"available\":null}}}", "android.tee.available");

        // securityLevel
        assertInvalid("{\"android\":{\"tee\":{\"securityLevel\":1}}}", "android.tee.securityLevel");
        assertInvalid("{\"android\":{\"tee\":{\"securityLevel\":null}}}", "android.tee.securityLevel");
        assertInvalid("{\"android\":{\"tee\":{\"securityLevel\":\"\"}}}", "android.tee.securityLevel");
        assertInvalid("{\"android\":{\"tee\":{\"securityLevel\":\"software\"}}}",
                "android.tee.securityLevel");
        assertInvalid("{\"android\":{\"tee\":{\"securityLevel\":\"HARDWARE\"}}}",
                "android.tee.securityLevel");
        assertInvalid("{\"android\":{\"tee\":{\"securityLevel\":\"TRUSTED_ENVIRONMENT \"}}}",
                "android.tee.securityLevel");

        // keymasterVersion
        assertInvalid("{\"android\":{\"tee\":{\"keymasterVersion\":\"1\"}}}",
                "android.tee.keymasterVersion");
        assertInvalid("{\"android\":{\"tee\":{\"keymasterVersion\":1.5}}}",
                "android.tee.keymasterVersion");
        assertInvalid("{\"android\":{\"tee\":{\"keymasterVersion\":null}}}",
                "android.tee.keymasterVersion");
        assertInvalid("{\"android\":{\"tee\":{\"keymasterVersion\":-1}}}",
                "android.tee.keymasterVersion");
        assertInvalid("{\"android\":{\"tee\":{\"keymasterVersion\":101}}}",
                "android.tee.keymasterVersion");
        assertInvalid("{\"android\":{\"tee\":{\"keymasterVersion\":true}}}",
                "android.tee.keymasterVersion");

        // strongBoxAvailable
        assertInvalid("{\"android\":{\"tee\":{\"strongBoxAvailable\":0}}}",
                "android.tee.strongBoxAvailable");
        assertInvalid("{\"android\":{\"tee\":{\"strongBoxAvailable\":\"false\"}}}",
                "android.tee.strongBoxAvailable");
        assertInvalid("{\"android\":{\"tee\":{\"strongBoxAvailable\":null}}}",
                "android.tee.strongBoxAvailable");

        // marker
        assertInvalid("{\"android\":{\"tee\":{\"marker\":1}}}", "android.tee.marker");
        assertInvalid("{\"android\":{\"tee\":{\"marker\":null}}}", "android.tee.marker");
        assertInvalid("{\"android\":{\"tee\":{\"marker\":\"\"}}}", "android.tee.marker");
        assertInvalid("{\"android\":{\"tee\":{\"marker\":\" leading\"}}}", "android.tee.marker");
        assertInvalid("{\"android\":{\"tee\":{\"marker\":\"trailing \"}}}", "android.tee.marker");
        StringBuilder marker129 = new StringBuilder();
        for (int i = 0; i < 129; i++) {
            marker129.append('B');
        }
        assertInvalid("{\"android\":{\"tee\":{\"marker\":\"" + marker129 + "\"}}}",
                "android.tee.marker");

        // keyBlobHex
        assertInvalid("{\"android\":{\"tee\":{\"keyBlobHex\":1}}}", "android.tee.keyBlobHex");
        assertInvalid("{\"android\":{\"tee\":{\"keyBlobHex\":null}}}", "android.tee.keyBlobHex");
        assertInvalid("{\"android\":{\"tee\":{\"keyBlobHex\":true}}}", "android.tee.keyBlobHex");
        assertInvalid("{\"android\":{\"tee\":{\"keyBlobHex\":\"\"}}}", "android.tee.keyBlobHex");
        assertInvalid("{\"android\":{\"tee\":{\"keyBlobHex\":\"abc\"}}}", "android.tee.keyBlobHex");
        assertInvalid("{\"android\":{\"tee\":{\"keyBlobHex\":\"zz\"}}}", "android.tee.keyBlobHex");
        assertInvalid("{\"android\":{\"tee\":{\"keyBlobHex\":\"de ad\"}}}", "android.tee.keyBlobHex");
        assertInvalid("{\"android\":{\"tee\":{\"keyBlobHex\":\"AA:BB\"}}}", "android.tee.keyBlobHex");
        // length > 2097152 hex chars
        assertInvalid("{\"android\":{\"tee\":{\"keyBlobHex\":\""
                        + new String(new char[2097154]).replace('\0', 'a') + "\"}}}",
                "android.tee.keyBlobHex");

        // keyAlgorithm
        assertInvalid("{\"android\":{\"tee\":{\"keyAlgorithm\":1}}}", "android.tee.keyAlgorithm");
        assertInvalid("{\"android\":{\"tee\":{\"keyAlgorithm\":null}}}", "android.tee.keyAlgorithm");
        assertInvalid("{\"android\":{\"tee\":{\"keyAlgorithm\":true}}}", "android.tee.keyAlgorithm");
        assertInvalid("{\"android\":{\"tee\":{\"keyAlgorithm\":\"\"}}}", "android.tee.keyAlgorithm");
        assertInvalid("{\"android\":{\"tee\":{\"keyAlgorithm\":\" leading\"}}}",
                "android.tee.keyAlgorithm");
        assertInvalid("{\"android\":{\"tee\":{\"keyAlgorithm\":\"trailing \"}}}",
                "android.tee.keyAlgorithm");
        StringBuilder algo129 = new StringBuilder();
        for (int i = 0; i < 129; i++) {
            algo129.append('A');
        }
        assertInvalid("{\"android\":{\"tee\":{\"keyAlgorithm\":\"" + algo129 + "\"}}}",
                "android.tee.keyAlgorithm");

        // keyFormat
        assertInvalid("{\"android\":{\"tee\":{\"keyFormat\":1}}}", "android.tee.keyFormat");
        assertInvalid("{\"android\":{\"tee\":{\"keyFormat\":null}}}", "android.tee.keyFormat");
        assertInvalid("{\"android\":{\"tee\":{\"keyFormat\":true}}}", "android.tee.keyFormat");
        assertInvalid("{\"android\":{\"tee\":{\"keyFormat\":\"\"}}}", "android.tee.keyFormat");
        assertInvalid("{\"android\":{\"tee\":{\"keyFormat\":\" leading\"}}}",
                "android.tee.keyFormat");
        assertInvalid("{\"android\":{\"tee\":{\"keyFormat\":\"trailing \"}}}",
                "android.tee.keyFormat");
        StringBuilder format129 = new StringBuilder();
        for (int i = 0; i < 129; i++) {
            format129.append('F');
        }
        assertInvalid("{\"android\":{\"tee\":{\"keyFormat\":\"" + format129 + "\"}}}",
                "android.tee.keyFormat");
    }

    @Test
    public void testFilesystemStatConfig() {
        // path-keyed map; descriptive fields device/inode/blockSize (not dev/ino/blksize)
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"stat\":{"
                + "\"/data/user/0/com.demo.app\":{"
                + "\"device\":33,"
                + "\"inode\":1001,"
                + "\"mode\":16877,"
                + "\"uid\":1000,"
                + "\"gid\":1000,"
                + "\"size\":4096,"
                + "\"blockSize\":4096,"
                + "\"blocks\":8,"
                + "\"atimeMillis\":1718000000000,"
                + "\"mtimeMillis\":1718000001000,"
                + "\"ctimeMillis\":1718000002000"
                + "},"
                + "\"/proc/self/maps\":{"
                + "\"mode\":33060,"
                + "\"size\":0"
                + "}"
                + "}}}"
        );
        assertTrue(full.isFilesystemStatConfigured());
        assertEquals(2, full.getFilesystemStats().size());

        TraceEnvironmentConfig.FileStatConfig app = full.getFilesystemStat("/data/user/0/com.demo.app");
        assertNotNull(app);
        assertEquals("/data/user/0/com.demo.app", app.getPath());
        assertTrue(app.isDeviceConfigured());
        assertEquals(Long.valueOf(33L), app.getDevice());
        assertTrue(app.isInodeConfigured());
        assertEquals(Long.valueOf(1001L), app.getInode());
        assertTrue(app.isModeConfigured());
        assertEquals(Long.valueOf(16877L), app.getMode());
        assertTrue(app.isUidConfigured());
        assertEquals(Long.valueOf(1000L), app.getUid());
        assertTrue(app.isGidConfigured());
        assertEquals(Long.valueOf(1000L), app.getGid());
        assertTrue(app.isSizeConfigured());
        assertEquals(Long.valueOf(4096L), app.getSize());
        assertTrue(app.isBlockSizeConfigured());
        assertEquals(Integer.valueOf(4096), app.getBlockSize());
        assertTrue(app.isBlocksConfigured());
        assertEquals(Long.valueOf(8L), app.getBlocks());
        assertTrue(app.isAtimeMillisConfigured());
        assertEquals(Long.valueOf(1718000000000L), app.getAtimeMillis());
        assertTrue(app.isMtimeMillisConfigured());
        assertEquals(Long.valueOf(1718000001000L), app.getMtimeMillis());
        assertTrue(app.isCtimeMillisConfigured());
        assertEquals(Long.valueOf(1718000002000L), app.getCtimeMillis());

        TraceEnvironmentConfig.FileStatConfig maps = full.getFilesystemStat("/proc/self/maps");
        assertNotNull(maps);
        assertEquals("/proc/self/maps", maps.getPath());
        assertFalse(maps.isDeviceConfigured());
        assertNull(maps.getDevice());
        assertTrue(maps.isModeConfigured());
        assertEquals(Long.valueOf(33060L), maps.getMode());
        assertTrue(maps.isSizeConfigured());
        assertEquals(Long.valueOf(0L), maps.getSize());
        assertFalse(maps.isInodeConfigured());
        assertFalse(maps.isUidConfigured());
        assertFalse(maps.isBlocksConfigured());
        assertNull(maps.getBlocks());
        assertFalse(maps.isAtimeMillisConfigured());
        assertNull(maps.getAtimeMillis());

        assertNull(full.getFilesystemStat("/missing"));

        // POSIX normalize: store collapsed path; equivalent query paths hit
        TraceEnvironmentConfig norm = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"stat\":{"
                + "\"/data/./user/../user/0/com.demo.app\":{\"size\":0},"
                + "\"/proc//self/./maps\":{\"size\":0},"
                + "\"/\":{\"size\":0},"
                + "\"/tmp/foo/bar/../baz\":{\"size\":0}"
                + "}}}"
        );
        assertEquals(4, norm.getFilesystemStats().size());
        TraceEnvironmentConfig.FileStatConfig nApp =
                norm.getFilesystemStat("/data/user/0/com.demo.app");
        assertNotNull(nApp);
        assertEquals("/data/user/0/com.demo.app", nApp.getPath());
        assertNotNull(norm.getFilesystemStat("/data/./user/0/com.demo.app"));
        assertNotNull(norm.getFilesystemStat("/data/user/../user/0/com.demo.app"));
        TraceEnvironmentConfig.FileStatConfig nMaps =
                norm.getFilesystemStat("/proc/self/maps");
        assertNotNull(nMaps);
        assertEquals("/proc/self/maps", nMaps.getPath());
        assertNotNull(norm.getFilesystemStat("/"));
        assertEquals("/", norm.getFilesystemStat("/").getPath());
        assertNotNull(norm.getFilesystemStat("/tmp/foo/./baz"));
        assertEquals("/tmp/foo/baz", norm.getFilesystemStat("/tmp/foo/baz").getPath());

        // explicit empty path map (top-level) still legal
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"stat\":{}}}");
        assertTrue(empty.isFilesystemStatConfigured());
        assertTrue(empty.getFilesystemStats().isEmpty());
        assertNull(empty.getFilesystemStat("/any"));

        // filesystem present without stat key
        TraceEnvironmentConfig noStatKey = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{}}");
        assertFalse(noStatKey.isFilesystemStatConfigured());
        assertTrue(noStatKey.getFilesystemStats().isEmpty());

        // missing filesystem node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missing.isFilesystemStatConfigured());
        assertTrue(missing.getFilesystemStats().isEmpty());

        // zero / min boundary values: blockSize min is 1
        TraceEnvironmentConfig zeros = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"stat\":{\"/z\":{"
                + "\"device\":0,\"inode\":0,\"mode\":0,\"uid\":0,\"gid\":0,"
                + "\"size\":0,\"blockSize\":1,\"blocks\":0,"
                + "\"atimeMillis\":0,\"mtimeMillis\":0,\"ctimeMillis\":0"
                + "}}}}");
        TraceEnvironmentConfig.FileStatConfig z = zeros.getFilesystemStat("/z");
        assertNotNull(z);
        assertEquals(Long.valueOf(0L), z.getDevice());
        assertEquals(Long.valueOf(0L), z.getInode());
        assertEquals(Long.valueOf(0L), z.getMode());
        assertEquals(Long.valueOf(0L), z.getUid());
        assertEquals(Long.valueOf(0L), z.getGid());
        assertEquals(Integer.valueOf(1), z.getBlockSize());
        assertEquals(Long.valueOf(0L), z.getBlocks());
        assertEquals(Long.valueOf(0L), z.getAtimeMillis());

        // mode/uid/gid are exact long (not int): values above Integer.MAX_VALUE accepted
        TraceEnvironmentConfig wide = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"stat\":{\"/w\":{"
                + "\"mode\":4294967295,"
                + "\"uid\":3000000000,"
                + "\"gid\":3000000001"
                + "}}}}");
        TraceEnvironmentConfig.FileStatConfig w = wide.getFilesystemStat("/w");
        assertNotNull(w);
        assertEquals(Long.valueOf(4294967295L), w.getMode());
        assertEquals(Long.valueOf(3000000000L), w.getUid());
        assertEquals(Long.valueOf(3000000001L), w.getGid());

        // atimeMillis/mtimeMillis/ctimeMillis: full Java long range
        TraceEnvironmentConfig times = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"stat\":{\"/t\":{"
                + "\"atimeMillis\":-1,"
                + "\"mtimeMillis\":" + Long.MIN_VALUE + ","
                + "\"ctimeMillis\":" + Long.MAX_VALUE
                + "}}}}");
        TraceEnvironmentConfig.FileStatConfig t = times.getFilesystemStat("/t");
        assertNotNull(t);
        assertEquals(Long.valueOf(-1L), t.getAtimeMillis());
        assertEquals(Long.valueOf(Long.MIN_VALUE), t.getMtimeMillis());
        assertEquals(Long.valueOf(Long.MAX_VALUE), t.getCtimeMillis());

        // list is unmodifiable
        try {
            full.getFilesystemStats().add(null);
            fail("expected UnsupportedOperationException for unmodifiable list");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void testFilesystemStatApiArgs() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"stat\":{\"/data\":{\"size\":0}}}}");
        // illegal lookup paths return null (do not throw)
        assertNull(config.getFilesystemStat(null));
        assertNull(config.getFilesystemStat(""));
        assertNull(config.getFilesystemStat("   "));
        assertNull(config.getFilesystemStat("data"));
        assertNull(config.getFilesystemStat("relative/path"));
        assertNull(config.getFilesystemStat("/../x"));
        assertNull(config.getFilesystemStat("/data\\x"));
        assertNull(config.getFilesystemStat("/data\u0000x"));
        // legal hit
        assertNotNull(config.getFilesystemStat("/data"));
        assertNotNull(config.getFilesystemStat("/data/."));
        assertNotNull(config.getFilesystemStat("/data/foo/.."));
    }

    @Test
    public void testFilesystemStatInvalid() {
        // structure
        assertInvalid("{\"filesystem\":[]}", "filesystem");
        assertInvalid("{\"filesystem\":1}", "filesystem");
        assertInvalid("{\"filesystem\":\"x\"}", "filesystem");
        // array form is not accepted
        assertInvalid("{\"filesystem\":{\"stat\":[]}}", "filesystem.stat");
        assertInvalid("{\"filesystem\":{\"stat\":1}}", "filesystem.stat");
        assertInvalid("{\"filesystem\":{\"stat\":\"/data\"}}", "filesystem.stat");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":1}}}",
                "filesystem.stat[\"/data\"]");
        // filesystem.statfs/mounts/links are allowed (see dedicated tests); unknown keys still fail
        assertInvalid("{\"filesystem\":{\"stat\":{},\"extra\":1}}", "filesystem.extra");
        // mounts must be JSONArray (object shape illegal)
        assertInvalid("{\"filesystem\":{\"mounts\":{}}}", "filesystem.mounts");
        assertInvalid("{\"filesystem\":{\"mounts\":1}}", "filesystem.mounts");
        // links must be JSONObject (array shape illegal)
        assertInvalid("{\"filesystem\":{\"links\":[]}}", "filesystem.links");
        assertInvalid("{\"filesystem\":{\"links\":1}}", "filesystem.links");

        // empty per-path entry illegal (top-level empty map still legal)
        assertInvalid("{\"filesystem\":{\"stat\":{\"/\":{}}}}",
                "filesystem.stat[\"/\"]");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{}}}}",
                "filesystem.stat[\"/data\"]");

        // path keys: each entry uses size=0 to satisfy non-empty entry rule
        assertInvalid("{\"filesystem\":{\"stat\":{\"\":{\"size\":0}}}}",
                "filesystem.stat[\"\"]");
        assertInvalid("{\"filesystem\":{\"stat\":{\" /data\":{\"size\":0}}}}",
                "filesystem.stat[\" /data\"]");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data \":{\"size\":0}}}}",
                "filesystem.stat[\"/data \"]");
        assertInvalid("{\"filesystem\":{\"stat\":{\"data\":{\"size\":0}}}}",
                "filesystem.stat[\"data\"]");
        assertInvalid("{\"filesystem\":{\"stat\":{\"relative/path\":{\"size\":0}}}}",
                "filesystem.stat[\"relative/path\"]");
        // above root / backslash / NUL illegal
        assertInvalid("{\"filesystem\":{\"stat\":{\"/../x\":{\"size\":0}}}}",
                "filesystem.stat[\"/../x\"]");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/a/../../b\":{\"size\":0}}}}",
                "filesystem.stat[\"/a/../../b\"]");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\\\\x\":{\"size\":0}}}}",
                "filesystem.stat[\"/data\\x\"]");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data" + "\\" + "u0000x\":{\"size\":0}}}}",
                "filesystem.stat[\"/data");
        // normalized duplicates rejected (pathPrefix is whichever key is processed second)
        try {
            TraceEnvironmentConfig.parse("{\"filesystem\":{\"stat\":{"
                    + "\"/data/foo\":{\"size\":0},"
                    + "\"/data/./foo\":{\"size\":0}"
                    + "}}}");
            fail("expected IllegalArgumentException for normalized duplicate path");
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue("message missing duplicate marker, was: " + message,
                    message != null && message.contains("normalizes to a duplicate path"));
            assertTrue("message missing pathPrefix, was: " + message,
                    message.contains("filesystem.stat[\"/data/foo\"]")
                            || message.contains("filesystem.stat[\"/data/./foo\"]"));
            assertTrue(message.contains("/data/foo"));
        }
        try {
            TraceEnvironmentConfig.parse("{\"filesystem\":{\"stat\":{"
                    + "\"/tmp/a/b\":{\"size\":0},"
                    + "\"/tmp/a/c/../b\":{\"size\":0}"
                    + "}}}");
            fail("expected IllegalArgumentException for normalized duplicate path");
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue("message missing duplicate marker, was: " + message,
                    message != null && message.contains("normalizes to a duplicate path"));
            assertTrue("message missing pathPrefix, was: " + message,
                    message.contains("filesystem.stat[\"/tmp/a/b\"]")
                            || message.contains("filesystem.stat[\"/tmp/a/c/../b\"]"));
        }

        // unknown / legacy C short names rejected
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"extra\":1}}}}",
                "filesystem.stat[\"/data\"].extra");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"dev\":1}}}}",
                "filesystem.stat[\"/data\"].dev");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"ino\":1}}}}",
                "filesystem.stat[\"/data\"].ino");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"blksize\":4096}}}}",
                "filesystem.stat[\"/data\"].blksize");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"nlink\":1}}}}",
                "filesystem.stat[\"/data\"].nlink");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"rdev\":0}}}}",
                "filesystem.stat[\"/data\"].rdev");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"atime\":1}}}}",
                "filesystem.stat[\"/data\"].atime");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"path\":\"/other\"}}}}",
                "filesystem.stat[\"/data\"].path");

        // mode / uid / gid: exact non-negative long (not int / string / null / fraction)
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"mode\":null}}}}",
                "filesystem.stat[\"/data\"].mode");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"mode\":true}}}}",
                "filesystem.stat[\"/data\"].mode");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"mode\":\"16877\"}}}}",
                "filesystem.stat[\"/data\"].mode");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"mode\":1.5}}}}",
                "filesystem.stat[\"/data\"].mode");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"mode\":-1}}}}",
                "filesystem.stat[\"/data\"].mode");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"mode\":4294967296}}}}",
                "filesystem.stat[\"/data\"].mode");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"uid\":-1}}}}",
                "filesystem.stat[\"/data\"].uid");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"uid\":null}}}}",
                "filesystem.stat[\"/data\"].uid");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"uid\":\"1000\"}}}}",
                "filesystem.stat[\"/data\"].uid");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"uid\":4294967296}}}}",
                "filesystem.stat[\"/data\"].uid");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"gid\":-1}}}}",
                "filesystem.stat[\"/data\"].gid");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"gid\":1.5}}}}",
                "filesystem.stat[\"/data\"].gid");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"gid\":4294967296}}}}",
                "filesystem.stat[\"/data\"].gid");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"blockSize\":-1}}}}",
                "filesystem.stat[\"/data\"].blockSize");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"blockSize\":0}}}}",
                "filesystem.stat[\"/data\"].blockSize");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"blockSize\":null}}}}",
                "filesystem.stat[\"/data\"].blockSize");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"blockSize\":\"4096\"}}}}",
                "filesystem.stat[\"/data\"].blockSize");

        // long fields: device / inode / size / times
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"device\":null}}}}",
                "filesystem.stat[\"/data\"].device");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"device\":true}}}}",
                "filesystem.stat[\"/data\"].device");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"device\":\"1\"}}}}",
                "filesystem.stat[\"/data\"].device");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"device\":-1}}}}",
                "filesystem.stat[\"/data\"].device");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"inode\":-1}}}}",
                "filesystem.stat[\"/data\"].inode");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"inode\":1.5}}}}",
                "filesystem.stat[\"/data\"].inode");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"size\":-1}}}}",
                "filesystem.stat[\"/data\"].size");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"blocks\":-1}}}}",
                "filesystem.stat[\"/data\"].blocks");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"blocks\":null}}}}",
                "filesystem.stat[\"/data\"].blocks");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"blocks\":\"8\"}}}}",
                "filesystem.stat[\"/data\"].blocks");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"blocks\":1.5}}}}",
                "filesystem.stat[\"/data\"].blocks");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"blocks\":9223372036854775808}}}}",
                "filesystem.stat[\"/data\"].blocks");
        // times allow full long range; still reject non-exact / non-number / outside long
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"atimeMillis\":1.5}}}}",
                "filesystem.stat[\"/data\"].atimeMillis");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"atimeMillis\":-9223372036854775809}}}}",
                "filesystem.stat[\"/data\"].atimeMillis");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"mtimeMillis\":1.5}}}}",
                "filesystem.stat[\"/data\"].mtimeMillis");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"mtimeMillis\":9223372036854775808}}}}",
                "filesystem.stat[\"/data\"].mtimeMillis");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"ctimeMillis\":null}}}}",
                "filesystem.stat[\"/data\"].ctimeMillis");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"ctimeMillis\":9223372036854775808}}}}",
                "filesystem.stat[\"/data\"].ctimeMillis");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"atimeMillis\":true}}}}",
                "filesystem.stat[\"/data\"].atimeMillis");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"mtimeMillis\":\"1\"}}}}",
                "filesystem.stat[\"/data\"].mtimeMillis");
        assertInvalid("{\"filesystem\":{\"stat\":{\"/data\":{\"size\":99999999999999999999999999999}}}}",
                "filesystem.stat[\"/data\"].size");
    }

    @Test
    public void testFilesystemStatFsConfig() {
        // full entry: all fields + second partial entry; also coexists with stat only on one side
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"statfs\":{"
                + "\"/data\":{"
                + "\"type\":61267,"
                + "\"blockSize\":4096,"
                + "\"blocks\":1000000,"
                + "\"blocksFree\":500000,"
                + "\"blocksAvailable\":450000,"
                + "\"files\":200000,"
                + "\"filesFree\":150000,"
                + "\"fsid\":[1,2],"
                + "\"nameLength\":255,"
                + "\"fragmentSize\":4096,"
                + "\"flags\":1"
                + "},"
                + "\"/\":{"
                + "\"blockSize\":4096,"
                + "\"blocks\":0"
                + "}"
                + "}}}"
        );
        assertTrue(full.isFilesystemStatFsConfigured());
        assertFalse(full.isFilesystemStatConfigured());
        assertEquals(2, full.getFilesystemStatFsEntries().size());

        TraceEnvironmentConfig.FileStatFsConfig data = full.getFilesystemStatFsExact("/data");
        assertNotNull(data);
        assertEquals("/data", data.getMountPoint());
        assertTrue(data.isTypeConfigured());
        assertEquals(Long.valueOf(61267L), data.getType());
        assertTrue(data.isBlockSizeConfigured());
        assertEquals(Integer.valueOf(4096), data.getBlockSize());
        assertTrue(data.isBlocksConfigured());
        assertEquals(Long.valueOf(1000000L), data.getBlocks());
        assertTrue(data.isBlocksFreeConfigured());
        assertEquals(Long.valueOf(500000L), data.getBlocksFree());
        assertTrue(data.isBlocksAvailableConfigured());
        assertEquals(Long.valueOf(450000L), data.getBlocksAvailable());
        assertTrue(data.isFilesConfigured());
        assertEquals(Long.valueOf(200000L), data.getFiles());
        assertTrue(data.isFilesFreeConfigured());
        assertEquals(Long.valueOf(150000L), data.getFilesFree());
        assertTrue(data.isFsidConfigured());
        assertArrayEquals(new long[]{1L, 2L}, data.getFsid());
        assertTrue(data.isNameLengthConfigured());
        assertEquals(Integer.valueOf(255), data.getNameLength());
        assertTrue(data.isFragmentSizeConfigured());
        assertEquals(Integer.valueOf(4096), data.getFragmentSize());
        assertTrue(data.isFlagsConfigured());
        assertEquals(Long.valueOf(1L), data.getFlags());

        TraceEnvironmentConfig.FileStatFsConfig rootFs = full.getFilesystemStatFsExact("/");
        assertNotNull(rootFs);
        assertEquals("/", rootFs.getMountPoint());
        assertFalse(rootFs.isTypeConfigured());
        assertNull(rootFs.getType());
        assertTrue(rootFs.isBlockSizeConfigured());
        assertEquals(Integer.valueOf(4096), rootFs.getBlockSize());
        assertTrue(rootFs.isBlocksConfigured());
        assertEquals(Long.valueOf(0L), rootFs.getBlocks());
        assertFalse(rootFs.isBlocksFreeConfigured());
        assertNull(rootFs.getBlocksFree());
        assertFalse(rootFs.isFsidConfigured());
        assertNull(rootFs.getFsid());
        assertFalse(rootFs.isFlagsConfigured());
        assertNull(rootFs.getFlags());

        assertNull(full.getFilesystemStatFsExact("/missing"));
        // exact only: no longest-prefix match for nested path
        assertNull(full.getFilesystemStatFsExact("/data/user/0"));

        // stat missing must not skip statfs; both may coexist
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{"
                + "\"stat\":{\"/proc/cpuinfo\":{\"size\":1}},"
                + "\"statfs\":{\"/data\":{\"blocks\":10}}"
                + "}}"
        );
        assertTrue(both.isFilesystemStatConfigured());
        assertTrue(both.isFilesystemStatFsConfigured());
        assertNotNull(both.getFilesystemStat("/proc/cpuinfo"));
        assertNotNull(both.getFilesystemStatFsExact("/data"));
        assertEquals(Long.valueOf(10L), both.getFilesystemStatFsExact("/data").getBlocks());

        // POSIX normalize store + equivalent exact query
        TraceEnvironmentConfig norm = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"statfs\":{"
                + "\"/data/./user/../user/0\":{\"blocks\":1},"
                + "\"/\":{\"blocks\":2},"
                + "\"/tmp/foo/bar/../baz\":{\"blocks\":3}"
                + "}}}"
        );
        assertEquals(3, norm.getFilesystemStatFsEntries().size());
        TraceEnvironmentConfig.FileStatFsConfig nData =
                norm.getFilesystemStatFsExact("/data/user/0");
        assertNotNull(nData);
        assertEquals("/data/user/0", nData.getMountPoint());
        assertNotNull(norm.getFilesystemStatFsExact("/data/./user/0"));
        assertNotNull(norm.getFilesystemStatFsExact("/data/user/../user/0"));
        assertNotNull(norm.getFilesystemStatFsExact("/"));
        assertEquals("/", norm.getFilesystemStatFsExact("/").getMountPoint());
        assertNotNull(norm.getFilesystemStatFsExact("/tmp/foo/baz"));
        assertEquals("/tmp/foo/baz",
                norm.getFilesystemStatFsExact("/tmp/foo/./baz").getMountPoint());

        // explicit empty top-level map is configured
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"statfs\":{}}}");
        assertTrue(empty.isFilesystemStatFsConfigured());
        assertTrue(empty.getFilesystemStatFsEntries().isEmpty());
        assertNull(empty.getFilesystemStatFsExact("/any"));

        // filesystem present without statfs key
        TraceEnvironmentConfig noStatFsKey = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"stat\":{}}}");
        assertFalse(noStatFsKey.isFilesystemStatFsConfigured());
        assertTrue(noStatFsKey.getFilesystemStatFsEntries().isEmpty());

        // missing filesystem node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isFilesystemStatFsConfigured());
        assertTrue(missing.getFilesystemStatFsEntries().isEmpty());

        // lower bounds (zeros / min positive ints)
        TraceEnvironmentConfig zeros = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"statfs\":{\"/z\":{"
                + "\"type\":0,\"blockSize\":1,\"blocks\":0,\"blocksFree\":0,\"blocksAvailable\":0,"
                + "\"files\":0,\"filesFree\":0,\"fsid\":[0,0],"
                + "\"nameLength\":1,\"fragmentSize\":1,\"flags\":0"
                + "}}}}"
        );
        TraceEnvironmentConfig.FileStatFsConfig z = zeros.getFilesystemStatFsExact("/z");
        assertNotNull(z);
        assertEquals(Long.valueOf(0L), z.getType());
        assertEquals(Integer.valueOf(1), z.getBlockSize());
        assertEquals(Long.valueOf(0L), z.getBlocks());
        assertEquals(Long.valueOf(0L), z.getBlocksFree());
        assertEquals(Long.valueOf(0L), z.getBlocksAvailable());
        assertEquals(Long.valueOf(0L), z.getFiles());
        assertEquals(Long.valueOf(0L), z.getFilesFree());
        assertArrayEquals(new long[]{0L, 0L}, z.getFsid());
        assertEquals(Integer.valueOf(1), z.getNameLength());
        assertEquals(Integer.valueOf(1), z.getFragmentSize());
        assertEquals(Long.valueOf(0L), z.getFlags());

        // upper bounds
        TraceEnvironmentConfig wide = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"statfs\":{\"/w\":{"
                + "\"type\":4294967295,"
                + "\"blockSize\":2147483647,"
                + "\"blocks\":9223372036854775807,"
                + "\"blocksFree\":9223372036854775807,"
                + "\"blocksAvailable\":9223372036854775807,"
                + "\"files\":9223372036854775807,"
                + "\"filesFree\":9223372036854775807,"
                + "\"fsid\":[4294967295,4294967295],"
                + "\"nameLength\":2147483647,"
                + "\"fragmentSize\":2147483647,"
                + "\"flags\":4294967295"
                + "}}}}"
        );
        TraceEnvironmentConfig.FileStatFsConfig w = wide.getFilesystemStatFsExact("/w");
        assertNotNull(w);
        assertEquals(Long.valueOf(0xffffffffL), w.getType());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), w.getBlockSize());
        assertEquals(Long.valueOf(Long.MAX_VALUE), w.getBlocks());
        assertEquals(Long.valueOf(Long.MAX_VALUE), w.getBlocksFree());
        assertEquals(Long.valueOf(Long.MAX_VALUE), w.getBlocksAvailable());
        assertEquals(Long.valueOf(Long.MAX_VALUE), w.getFiles());
        assertEquals(Long.valueOf(Long.MAX_VALUE), w.getFilesFree());
        assertArrayEquals(new long[]{0xffffffffL, 0xffffffffL}, w.getFsid());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), w.getNameLength());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), w.getFragmentSize());
        assertEquals(Long.valueOf(0xffffffffL), w.getFlags());

        // fsid defensive copy
        long[] fsidCopy = data.getFsid();
        assertNotNull(fsidCopy);
        fsidCopy[0] = 999L;
        assertArrayEquals(new long[]{1L, 2L}, data.getFsid());

        // collection immutability
        try {
            full.getFilesystemStatFsEntries().add(null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }

        // exact query illegal paths -> null
        TraceEnvironmentConfig query = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"statfs\":{\"/data\":{\"blocks\":0}}}}");
        assertNull(query.getFilesystemStatFsExact(null));
        assertNull(query.getFilesystemStatFsExact(""));
        assertNull(query.getFilesystemStatFsExact("   "));
        assertNull(query.getFilesystemStatFsExact("data"));
        assertNull(query.getFilesystemStatFsExact("relative/path"));
        assertNull(query.getFilesystemStatFsExact("/../x"));
        assertNull(query.getFilesystemStatFsExact("/data\\x"));
        assertNull(query.getFilesystemStatFsExact("/data\u0000x"));
        assertNotNull(query.getFilesystemStatFsExact("/data"));
        assertNotNull(query.getFilesystemStatFsExact("/data/."));
        assertNotNull(query.getFilesystemStatFsExact("/data/foo/.."));

        // shape: not object / old array shape / entry types
        assertInvalid("{\"filesystem\":{\"statfs\":[]}}", "filesystem.statfs");
        assertInvalid("{\"filesystem\":{\"statfs\":1}}", "filesystem.statfs");
        assertInvalid("{\"filesystem\":{\"statfs\":\"/data\"}}", "filesystem.statfs");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":1}}}",
                "filesystem.statfs[\"/data\"]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":[]}}}",
                "filesystem.statfs[\"/data\"]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":null}}}",
                "filesystem.statfs[\"/data\"]");
        // empty entry rejected
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{}}}}",
                "filesystem.statfs[\"/data\"]");
        // path rules
        assertInvalid("{\"filesystem\":{\"statfs\":{\"\":{\"blocks\":0}}}}",
                "filesystem.statfs[\"\"]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\" /data\":{\"blocks\":0}}}}",
                "filesystem.statfs[\" /data\"]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data \":{\"blocks\":0}}}}",
                "filesystem.statfs[\"/data \"]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"data\":{\"blocks\":0}}}}",
                "filesystem.statfs[\"data\"]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/../x\":{\"blocks\":0}}}}",
                "filesystem.statfs[\"/../x\"]");
        // normalize duplicate
        try {
            TraceEnvironmentConfig.parse("{\"filesystem\":{\"statfs\":{"
                    + "\"/data/foo\":{\"blocks\":1},"
                    + "\"/data/./foo\":{\"blocks\":2}"
                    + "}}}");
            fail("expected duplicate normalize");
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue(message != null && (
                    message.contains("filesystem.statfs[\"/data/foo\"]")
                            || message.contains("filesystem.statfs[\"/data/./foo\"]")));
        }
        // unknown field
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"extra\":1}}}}",
                "filesystem.statfs[\"/data\"].extra");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"bsize\":4096}}}}",
                "filesystem.statfs[\"/data\"].bsize");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"f_type\":1}}}}",
                "filesystem.statfs[\"/data\"].f_type");

        // type / flags range and illegal types
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"type\":null}}}}",
                "filesystem.statfs[\"/data\"].type");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"type\":true}}}}",
                "filesystem.statfs[\"/data\"].type");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"type\":\"1\"}}}}",
                "filesystem.statfs[\"/data\"].type");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"type\":1.5}}}}",
                "filesystem.statfs[\"/data\"].type");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"type\":-1}}}}",
                "filesystem.statfs[\"/data\"].type");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"type\":4294967296}}}}",
                "filesystem.statfs[\"/data\"].type");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"flags\":-1}}}}",
                "filesystem.statfs[\"/data\"].flags");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"flags\":4294967296}}}}",
                "filesystem.statfs[\"/data\"].flags");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"flags\":null}}}}",
                "filesystem.statfs[\"/data\"].flags");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"flags\":1.5}}}}",
                "filesystem.statfs[\"/data\"].flags");

        // blockSize / nameLength / fragmentSize
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"blockSize\":0}}}}",
                "filesystem.statfs[\"/data\"].blockSize");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"blockSize\":-1}}}}",
                "filesystem.statfs[\"/data\"].blockSize");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"blockSize\":null}}}}",
                "filesystem.statfs[\"/data\"].blockSize");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"blockSize\":\"4096\"}}}}",
                "filesystem.statfs[\"/data\"].blockSize");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"nameLength\":0}}}}",
                "filesystem.statfs[\"/data\"].nameLength");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"nameLength\":1.5}}}}",
                "filesystem.statfs[\"/data\"].nameLength");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fragmentSize\":0}}}}",
                "filesystem.statfs[\"/data\"].fragmentSize");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fragmentSize\":null}}}}",
                "filesystem.statfs[\"/data\"].fragmentSize");

        // long counters
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"blocks\":-1}}}}",
                "filesystem.statfs[\"/data\"].blocks");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"blocks\":null}}}}",
                "filesystem.statfs[\"/data\"].blocks");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"blocks\":\"8\"}}}}",
                "filesystem.statfs[\"/data\"].blocks");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"blocks\":1.5}}}}",
                "filesystem.statfs[\"/data\"].blocks");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"blocks\":9223372036854775808}}}}",
                "filesystem.statfs[\"/data\"].blocks");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"blocksFree\":-1}}}}",
                "filesystem.statfs[\"/data\"].blocksFree");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"blocksAvailable\":1.5}}}}",
                "filesystem.statfs[\"/data\"].blocksAvailable");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"files\":null}}}}",
                "filesystem.statfs[\"/data\"].files");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"filesFree\":-1}}}}",
                "filesystem.statfs[\"/data\"].filesFree");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"filesFree\":9223372036854775808}}}}",
                "filesystem.statfs[\"/data\"].filesFree");

        // fsid length / element types / range
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":null}}}}",
                "filesystem.statfs[\"/data\"].fsid");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":1}}}}",
                "filesystem.statfs[\"/data\"].fsid");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":{}}}}}",
                "filesystem.statfs[\"/data\"].fsid");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":[]}}}}",
                "filesystem.statfs[\"/data\"].fsid");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":[1]}}}}",
                "filesystem.statfs[\"/data\"].fsid");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":[1,2,3]}}}}",
                "filesystem.statfs[\"/data\"].fsid");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":[null,0]}}}}",
                "filesystem.statfs[\"/data\"].fsid[0]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":[true,0]}}}}",
                "filesystem.statfs[\"/data\"].fsid[0]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":[\"1\",0]}}}}",
                "filesystem.statfs[\"/data\"].fsid[0]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":[1.5,0]}}}}",
                "filesystem.statfs[\"/data\"].fsid[0]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":[-1,0]}}}}",
                "filesystem.statfs[\"/data\"].fsid[0]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":[4294967296,0]}}}}",
                "filesystem.statfs[\"/data\"].fsid[0]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":[0,null]}}}}",
                "filesystem.statfs[\"/data\"].fsid[1]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":[0,-1]}}}}",
                "filesystem.statfs[\"/data\"].fsid[1]");
        assertInvalid("{\"filesystem\":{\"statfs\":{\"/data\":{\"fsid\":[0,4294967296]}}}}",
                "filesystem.statfs[\"/data\"].fsid[1]");
    }

    @Test
    public void testFilesystemStatFsLongestMountMatch() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"statfs\":{"
                + "\"/\":{\"blocks\":1},"
                + "\"/data\":{\"blocks\":2},"
                + "\"/data/user/0\":{\"blocks\":3},"
                + "\"/system\":{\"blocks\":4}"
                + "}}}"
        );

        // exact mount hit
        assertEquals("/", config.getFilesystemStatFs("/").getMountPoint());
        assertEquals(Long.valueOf(1L), config.getFilesystemStatFs("/").getBlocks());
        assertEquals("/data", config.getFilesystemStatFs("/data").getMountPoint());
        assertEquals(Long.valueOf(2L), config.getFilesystemStatFs("/data").getBlocks());
        assertEquals("/data/user/0", config.getFilesystemStatFs("/data/user/0").getMountPoint());
        assertEquals("/system", config.getFilesystemStatFs("/system").getMountPoint());

        // nested longest hit
        assertEquals("/data/user/0",
                config.getFilesystemStatFs("/data/user/0/com.demo.app").getMountPoint());
        assertEquals(Long.valueOf(3L),
                config.getFilesystemStatFs("/data/user/0/com.demo.app/files").getBlocks());
        assertEquals("/data", config.getFilesystemStatFs("/data/local/tmp").getMountPoint());
        assertEquals("/system", config.getFilesystemStatFs("/system/lib64").getMountPoint());

        // root fallback for non-nested paths
        assertEquals("/", config.getFilesystemStatFs("/proc/cpuinfo").getMountPoint());
        assertEquals("/", config.getFilesystemStatFs("/vendor").getMountPoint());
        assertEquals(Long.valueOf(1L), config.getFilesystemStatFs("/tmp/x").getBlocks());

        // path-boundary: /data must not match /database
        assertEquals("/", config.getFilesystemStatFs("/database").getMountPoint());
        assertEquals("/", config.getFilesystemStatFs("/data2").getMountPoint());
        assertEquals("/", config.getFilesystemStatFs("/dataprefix").getMountPoint());
        assertEquals("/", config.getFilesystemStatFs("/system32").getMountPoint());

        // normalized query paths hit the same mounts
        assertEquals("/data/user/0",
                config.getFilesystemStatFs("/data/./user/../user/0/com.app").getMountPoint());
        assertEquals("/data", config.getFilesystemStatFs("/data/./local").getMountPoint());
        assertEquals("/system",
                config.getFilesystemStatFs("/system/./lib/../lib64").getMountPoint());
        assertEquals("/", config.getFilesystemStatFs("/proc/./self/maps").getMountPoint());

        // illegal query -> null (no throw)
        assertNull(config.getFilesystemStatFs(null));
        assertNull(config.getFilesystemStatFs(""));
        assertNull(config.getFilesystemStatFs("   "));
        assertNull(config.getFilesystemStatFs("data"));
        assertNull(config.getFilesystemStatFs("relative/path"));
        assertNull(config.getFilesystemStatFs("/../x"));
        assertNull(config.getFilesystemStatFs("/data\\x"));
        assertNull(config.getFilesystemStatFs("/data\u0000x"));

        // exact API unchanged: nested path still exact-misses
        assertNull(config.getFilesystemStatFsExact("/data/user/0/com.demo.app"));
        assertNotNull(config.getFilesystemStatFsExact("/data/user/0"));
        assertEquals(config.getFilesystemStatFsExact("/data"), config.getFilesystemStatFs("/data"));

        // empty configured map
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"statfs\":{}}}");
        assertTrue(empty.isFilesystemStatFsConfigured());
        assertNull(empty.getFilesystemStatFs("/data"));
        assertNull(empty.getFilesystemStatFs("/"));

        // not configured
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isFilesystemStatFsConfigured());
        assertNull(missing.getFilesystemStatFs("/data"));
        assertNull(missing.getFilesystemStatFs("/"));

        // without root: unmatched paths return null (no root fallback entry)
        TraceEnvironmentConfig noRoot = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"statfs\":{"
                + "\"/data\":{\"blocks\":10},"
                + "\"/data/user\":{\"blocks\":20}"
                + "}}}"
        );
        assertEquals("/data/user", noRoot.getFilesystemStatFs("/data/user/0").getMountPoint());
        assertEquals("/data", noRoot.getFilesystemStatFs("/data/local").getMountPoint());
        assertNull(noRoot.getFilesystemStatFs("/system"));
        assertNull(noRoot.getFilesystemStatFs("/"));
        // boundary without root
        assertNull(noRoot.getFilesystemStatFs("/database"));
    }

    @Test
    public void testFilesystemMountsConfig() {
        // full entry with mountinfo + optional fields; second minimal entry
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"mounts\":["
                + "{"
                + "\"source\":\"/dev/block/dm-0\","
                + "\"target\":\"/\","
                + "\"fileSystemType\":\"ext4\","
                + "\"options\":\"ro,seclabel,relatime\","
                + "\"dump\":1,"
                + "\"pass\":1,"
                + "\"mountId\":21,"
                + "\"parentId\":1,"
                + "\"major\":253,"
                + "\"minor\":0,"
                + "\"root\":\"/\","
                + "\"mountOptions\":\"ro,seclabel,relatime\","
                + "\"optionalFields\":[\"shared:1\"],"
                + "\"superOptions\":\"rw,seclabel\""
                + "},"
                + "{"
                + "\"source\":\"/dev/block/dm-1\","
                + "\"target\":\"/data\","
                + "\"fileSystemType\":\"f2fs\","
                + "\"options\":\"rw,nosuid,nodev,noatime\""
                + "}"
                + "]}}"
        );
        assertTrue(full.isFilesystemMountsConfigured());
        assertEquals(2, full.getFilesystemMounts().size());

        TraceEnvironmentConfig.FileSystemMountConfig root = full.getFilesystemMounts().get(0);
        assertEquals("/dev/block/dm-0", root.getSource());
        assertEquals("/", root.getTarget());
        assertEquals("ext4", root.getFileSystemType());
        assertEquals("ro,seclabel,relatime", root.getOptions());
        assertTrue(root.isDumpConfigured());
        assertEquals(1, root.getDump());
        assertTrue(root.isPassConfigured());
        assertEquals(1, root.getPass());
        assertTrue(root.isMountInfoConfigured());
        assertEquals(Integer.valueOf(21), root.getMountId());
        assertEquals(Integer.valueOf(1), root.getParentId());
        assertEquals(Integer.valueOf(253), root.getMajor());
        assertEquals(Integer.valueOf(0), root.getMinor());
        assertTrue(root.isRootConfigured());
        assertEquals("/", root.getRoot());
        assertTrue(root.isMountOptionsConfigured());
        assertEquals("ro,seclabel,relatime", root.getMountOptions());
        assertTrue(root.isOptionalFieldsConfigured());
        assertEquals(1, root.getOptionalFields().size());
        assertEquals("shared:1", root.getOptionalFields().get(0));
        assertTrue(root.isSuperOptionsConfigured());
        assertEquals("rw,seclabel", root.getSuperOptions());

        // minimal: required only → defaults
        TraceEnvironmentConfig.FileSystemMountConfig data = full.getFilesystemMounts().get(1);
        assertEquals("/dev/block/dm-1", data.getSource());
        assertEquals("/data", data.getTarget());
        assertEquals("f2fs", data.getFileSystemType());
        assertEquals("rw,nosuid,nodev,noatime", data.getOptions());
        assertFalse(data.isDumpConfigured());
        assertEquals(0, data.getDump());
        assertFalse(data.isPassConfigured());
        assertEquals(0, data.getPass());
        assertFalse(data.isMountInfoConfigured());
        assertNull(data.getMountId());
        assertNull(data.getParentId());
        assertNull(data.getMajor());
        assertNull(data.getMinor());
        assertFalse(data.isRootConfigured());
        assertEquals("/", data.getRoot());
        assertFalse(data.isMountOptionsConfigured());
        assertEquals(data.getOptions(), data.getMountOptions());
        assertFalse(data.isOptionalFieldsConfigured());
        assertTrue(data.getOptionalFields().isEmpty());
        assertFalse(data.isSuperOptionsConfigured());
        assertEquals(data.getOptions(), data.getSuperOptions());

        // target POSIX normalize store
        TraceEnvironmentConfig norm = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"mounts\":[{"
                + "\"source\":\"tmpfs\","
                + "\"target\":\"/data/./user/../user/0\","
                + "\"fileSystemType\":\"tmpfs\","
                + "\"options\":\"rw\","
                + "\"root\":\"/./\""
                + "}]}}"
        );
        TraceEnvironmentConfig.FileSystemMountConfig n0 = norm.getFilesystemMounts().get(0);
        assertEquals("/data/user/0", n0.getTarget());
        assertEquals("/", n0.getRoot());
        assertTrue(n0.isRootConfigured());

        // explicit empty array configured
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"mounts\":[]}}");
        assertTrue(empty.isFilesystemMountsConfigured());
        assertTrue(empty.getFilesystemMounts().isEmpty());

        // missing mounts key
        TraceEnvironmentConfig noMounts = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"stat\":{}}}");
        assertFalse(noMounts.isFilesystemMountsConfigured());
        assertTrue(noMounts.getFilesystemMounts().isEmpty());

        // missing filesystem
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isFilesystemMountsConfigured());
        assertTrue(missing.getFilesystemMounts().isEmpty());

        // coexist with stat + statfs
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{"
                + "\"stat\":{\"/proc/cpuinfo\":{\"size\":1}},"
                + "\"statfs\":{\"/data\":{\"blocks\":10}},"
                + "\"mounts\":[{\"source\":\"/dev/block/sda1\",\"target\":\"/data\","
                + "\"fileSystemType\":\"ext4\",\"options\":\"rw\"}]"
                + "}}"
        );
        assertTrue(both.isFilesystemStatConfigured());
        assertTrue(both.isFilesystemStatFsConfigured());
        assertTrue(both.isFilesystemMountsConfigured());
        assertEquals(1, both.getFilesystemMounts().size());
        assertEquals("/data", both.getFilesystemMounts().get(0).getTarget());

        // dump/pass bounds
        TraceEnvironmentConfig bounds = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"mounts\":[{"
                + "\"source\":\"s\",\"target\":\"/z\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                + "\"dump\":0,\"pass\":0,"
                + "\"mountId\":1,\"parentId\":1,\"major\":0,\"minor\":0"
                + "}]}}"
        );
        TraceEnvironmentConfig.FileSystemMountConfig b0 = bounds.getFilesystemMounts().get(0);
        assertEquals(0, b0.getDump());
        assertEquals(0, b0.getPass());
        assertEquals(Integer.valueOf(1), b0.getMountId());
        assertEquals(Integer.valueOf(0), b0.getMajor());

        TraceEnvironmentConfig wide = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"mounts\":[{"
                + "\"source\":\"s\",\"target\":\"/w\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                + "\"dump\":2147483647,\"pass\":2147483647,"
                + "\"mountId\":2147483647,\"parentId\":2147483647,"
                + "\"major\":2147483647,\"minor\":2147483647"
                + "}]}}"
        );
        TraceEnvironmentConfig.FileSystemMountConfig w0 = wide.getFilesystemMounts().get(0);
        assertEquals(Integer.MAX_VALUE, w0.getDump());
        assertEquals(Integer.MAX_VALUE, w0.getPass());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), w0.getMountId());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), w0.getMinor());

        // optionalFields empty array when present
        TraceEnvironmentConfig optEmpty = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"mounts\":[{"
                + "\"source\":\"s\",\"target\":\"/o\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                + "\"optionalFields\":[]"
                + "}]}}"
        );
        assertTrue(optEmpty.getFilesystemMounts().get(0).isOptionalFieldsConfigured());
        assertTrue(optEmpty.getFilesystemMounts().get(0).getOptionalFields().isEmpty());

        // collection immutability
        try {
            full.getFilesystemMounts().add(null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        try {
            full.getFilesystemMounts().get(0).getOptionalFields().add("x");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }

        // shape
        assertInvalid("{\"filesystem\":{\"mounts\":{}}}", "filesystem.mounts");
        assertInvalid("{\"filesystem\":{\"mounts\":1}}", "filesystem.mounts");
        assertInvalid("{\"filesystem\":{\"mounts\":\"x\"}}", "filesystem.mounts");
        assertInvalid("{\"filesystem\":{\"mounts\":[1]}}", "filesystem.mounts[0]");
        assertInvalid("{\"filesystem\":{\"mounts\":[null]}}", "filesystem.mounts[0]");
        assertInvalid("{\"filesystem\":{\"mounts\":[{}]}}", "filesystem.mounts[0]");

        // unknown field
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"extra\":1}]}}",
                "filesystem.mounts[0].extra");

        // required missing
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\"}]}}",
                "filesystem.mounts[0].source");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"fileSystemType\":\"ext4\",\"options\":\"rw\"}]}}",
                "filesystem.mounts[0].target");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"options\":\"rw\"}]}}",
                "filesystem.mounts[0].fileSystemType");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\"}]}}",
                "filesystem.mounts[0].options");

        // type / null / empty for required strings
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":null,\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\"}]}}",
                "filesystem.mounts[0].source");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\"}]}}",
                "filesystem.mounts[0].source");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":1,\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\"}]}}",
                "filesystem.mounts[0].source");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\\ns\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\"}]}}",
                "filesystem.mounts[0].source");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":1,\"fileSystemType\":\"ext4\",\"options\":\"rw\"}]}}",
                "filesystem.mounts[0].target");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"data\",\"fileSystemType\":\"ext4\",\"options\":\"rw\"}]}}",
                "filesystem.mounts[0].target");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/../x\",\"fileSystemType\":\"ext4\",\"options\":\"rw\"}]}}",
                "filesystem.mounts[0].target");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"\",\"options\":\"rw\"}]}}",
                "filesystem.mounts[0].fileSystemType");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext 4\",\"options\":\"rw\"}]}}",
                "filesystem.mounts[0].fileSystemType");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"\"}]}}",
                "filesystem.mounts[0].options");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw, noatime\"}]}}",
                "filesystem.mounts[0].options");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\\n\"}]}}",
                "filesystem.mounts[0].options");

        // normalize duplicate target
        try {
            TraceEnvironmentConfig.parse("{\"filesystem\":{\"mounts\":["
                    + "{\"source\":\"a\",\"target\":\"/data/foo\",\"fileSystemType\":\"ext4\",\"options\":\"rw\"},"
                    + "{\"source\":\"b\",\"target\":\"/data/./foo\",\"fileSystemType\":\"ext4\",\"options\":\"rw\"}"
                    + "]}}");
            fail("expected duplicate target");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("filesystem.mounts[1].target"));
        }

        // mountId uniqueness only among mountinfo-complete entries
        TraceEnvironmentConfig distinctIds = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"mounts\":["
                + "{\"source\":\"a\",\"target\":\"/a\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                + "\"mountId\":10,\"parentId\":1,\"major\":1,\"minor\":0},"
                + "{\"source\":\"b\",\"target\":\"/b\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                + "\"mountId\":11,\"parentId\":10,\"major\":1,\"minor\":0},"
                + "{\"source\":\"c\",\"target\":\"/c\",\"fileSystemType\":\"tmpfs\",\"options\":\"rw\"}"
                + "]}}"
        );
        assertEquals(3, distinctIds.getFilesystemMounts().size());
        assertEquals(Integer.valueOf(10), distinctIds.getFilesystemMounts().get(0).getMountId());
        assertEquals(Integer.valueOf(11), distinctIds.getFilesystemMounts().get(1).getMountId());
        // parentId may reference external or same tree id; major/minor may repeat
        assertEquals(Integer.valueOf(10), distinctIds.getFilesystemMounts().get(1).getParentId());
        assertEquals(Integer.valueOf(1), distinctIds.getFilesystemMounts().get(0).getMajor());
        assertEquals(Integer.valueOf(1), distinctIds.getFilesystemMounts().get(1).getMajor());
        assertFalse(distinctIds.getFilesystemMounts().get(2).isMountInfoConfigured());

        try {
            TraceEnvironmentConfig.parse("{\"filesystem\":{\"mounts\":["
                    + "{\"source\":\"a\",\"target\":\"/a\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                    + "\"mountId\":21,\"parentId\":1,\"major\":0,\"minor\":0},"
                    + "{\"source\":\"b\",\"target\":\"/b\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                    + "\"mountId\":21,\"parentId\":1,\"major\":0,\"minor\":1}"
                    + "]}}");
            fail("expected duplicate mountId");
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue(message != null && message.contains("filesystem.mounts[1].mountId"));
            assertTrue(message.contains("duplicate"));
        }

        // dump/pass range and types
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"dump\":-1}]}}",
                "filesystem.mounts[0].dump");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"dump\":1.5}]}}",
                "filesystem.mounts[0].dump");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"pass\":null}]}}",
                "filesystem.mounts[0].pass");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"pass\":-1}]}}",
                "filesystem.mounts[0].pass");

        // mountinfo all-or-nothing
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"mountId\":1}]}}",
                "filesystem.mounts[0]");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"mountId\":1,\"parentId\":1,\"major\":0}]}}",
                "filesystem.mounts[0]");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"mountId\":0,\"parentId\":1,\"major\":0,\"minor\":0}]}}",
                "filesystem.mounts[0].mountId");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"mountId\":1,\"parentId\":0,\"major\":0,\"minor\":0}]}}",
                "filesystem.mounts[0].parentId");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"mountId\":1,\"parentId\":1,\"major\":-1,\"minor\":0}]}}",
                "filesystem.mounts[0].major");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"mountId\":1,\"parentId\":1,\"major\":0,\"minor\":-1}]}}",
                "filesystem.mounts[0].minor");

        // root / mountOptions / superOptions / optionalFields invalid
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"root\":\"relative\"}]}}",
                "filesystem.mounts[0].root");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"mountOptions\":\"rw noatime\"}]}}",
                "filesystem.mounts[0].mountOptions");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"superOptions\":\"\"}]}}",
                "filesystem.mounts[0].superOptions");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"optionalFields\":1}]}}",
                "filesystem.mounts[0].optionalFields");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"optionalFields\":[\"\"]}]}}",
                "filesystem.mounts[0].optionalFields[0]");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"optionalFields\":[\"shared 1\"]}]}}",
                "filesystem.mounts[0].optionalFields[0]");
        assertInvalid("{\"filesystem\":{\"mounts\":[{"
                        + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                        + "\"optionalFields\":[1]}]}}",
                "filesystem.mounts[0].optionalFields[0]");
    }

    @Test
    public void testFilesystemLinksConfig() {
        // full: absolute target + relative + raw chars (spaces, backslash) preserved
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"links\":{"
                + "\"/proc/self/exe\":\"/system/bin/app_process64\","
                + "\"/data/local/tmp/rel\":\"../cache/foo\","
                + "\"/data/./link with space\":\"target with space\\\\and\\\\slash\""
                + "}}}"
        );
        assertTrue(full.isFilesystemLinksConfigured());
        assertEquals(3, full.getFilesystemLinks().size());

        TraceEnvironmentConfig.FileSystemLinkConfig exe = full.getFilesystemLinkExact("/proc/self/exe");
        assertNotNull(exe);
        assertEquals("/proc/self/exe", exe.getPath());
        assertEquals("/system/bin/app_process64", exe.getTarget());

        TraceEnvironmentConfig.FileSystemLinkConfig rel = full.getFilesystemLinkExact("/data/local/tmp/rel");
        assertNotNull(rel);
        assertEquals("../cache/foo", rel.getTarget());

        TraceEnvironmentConfig.FileSystemLinkConfig spaced =
                full.getFilesystemLinkExact("/data/link with space");
        assertNotNull(spaced);
        assertEquals("/data/link with space", spaced.getPath());
        assertEquals("target with space\\and\\slash", spaced.getTarget());

        // normalized query hits stored key
        assertNotNull(full.getFilesystemLinkExact("/data/./link with space"));
        assertNotNull(full.getFilesystemLinkExact("/proc/self/./exe"));
        assertNull(full.getFilesystemLinkExact("/missing"));
        assertNull(full.getFilesystemLinkExact(null));
        assertNull(full.getFilesystemLinkExact(""));
        assertNull(full.getFilesystemLinkExact("relative"));
        assertNull(full.getFilesystemLinkExact("/../x"));
        assertNull(full.getFilesystemLinkExact("/data\\x"));
        // no pid/self rewrite: only exact normalized path
        assertNull(full.getFilesystemLinkExact("/proc/1234/exe"));

        // explicit empty object
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"links\":{}}}");
        assertTrue(empty.isFilesystemLinksConfigured());
        assertTrue(empty.getFilesystemLinks().isEmpty());
        assertNull(empty.getFilesystemLinkExact("/any"));

        // missing links key
        TraceEnvironmentConfig noLinks = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"stat\":{}}}");
        assertFalse(noLinks.isFilesystemLinksConfigured());
        assertTrue(noLinks.getFilesystemLinks().isEmpty());

        // missing filesystem
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isFilesystemLinksConfigured());
        assertTrue(missing.getFilesystemLinks().isEmpty());

        // coexist with other filesystem subsections
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{"
                + "\"stat\":{\"/proc/cpuinfo\":{\"size\":1}},"
                + "\"statfs\":{\"/data\":{\"blocks\":10}},"
                + "\"mounts\":[{\"source\":\"s\",\"target\":\"/data\","
                + "\"fileSystemType\":\"ext4\",\"options\":\"rw\"}],"
                + "\"links\":{\"/proc/self/exe\":\"/system/bin/app_process\"}"
                + "}}"
        );
        assertTrue(both.isFilesystemStatConfigured());
        assertTrue(both.isFilesystemStatFsConfigured());
        assertTrue(both.isFilesystemMountsConfigured());
        assertTrue(both.isFilesystemLinksConfigured());
        assertEquals("/system/bin/app_process",
                both.getFilesystemLinkExact("/proc/self/exe").getTarget());

        // collection immutability
        try {
            full.getFilesystemLinks().add(null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }

        // shape
        assertInvalid("{\"filesystem\":{\"links\":[]}}", "filesystem.links");
        assertInvalid("{\"filesystem\":{\"links\":1}}", "filesystem.links");
        assertInvalid("{\"filesystem\":{\"links\":\"/x\"}}", "filesystem.links");

        // unknown filesystem key still fails
        assertInvalid("{\"filesystem\":{\"links\":{},\"extra\":1}}", "filesystem.extra");

        // illegal keys
        assertInvalid("{\"filesystem\":{\"links\":{\"\": \"/t\"}}}", "filesystem.links[\"\"]");
        assertInvalid("{\"filesystem\":{\"links\":{\"data\": \"/t\"}}}", "filesystem.links[\"data\"]");
        assertInvalid("{\"filesystem\":{\"links\":{\"/../x\": \"/t\"}}}", "filesystem.links[\"/../x\"]");
        assertInvalid("{\"filesystem\":{\"links\":{\"/a\\\\b\": \"/t\"}}}", "filesystem.links[\"/a\\b\"]");

        // duplicate normalized path
        try {
            TraceEnvironmentConfig.parse("{\"filesystem\":{\"links\":{"
                    + "\"/data/foo\":\"a\","
                    + "\"/data/./foo\":\"b\""
                    + "}}}");
            fail("expected duplicate link path");
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue(message != null && (
                    message.contains("filesystem.links[\"/data/foo\"]")
                            || message.contains("filesystem.links[\"/data/./foo\"]")));
            assertTrue(message.contains("duplicate"));
        }

        // illegal targets
        assertInvalid("{\"filesystem\":{\"links\":{\"/x\":null}}}", "filesystem.links[\"/x\"]");
        assertInvalid("{\"filesystem\":{\"links\":{\"/x\":1}}}", "filesystem.links[\"/x\"]");
        assertInvalid("{\"filesystem\":{\"links\":{\"/x\":true}}}", "filesystem.links[\"/x\"]");
        assertInvalid("{\"filesystem\":{\"links\":{\"/x\":\"\"}}}", "filesystem.links[\"/x\"]");
        assertInvalid("{\"filesystem\":{\"links\":{\"/x\":\"a\\nb\"}}}", "filesystem.links[\"/x\"]");
        assertInvalid("{\"filesystem\":{\"links\":{\"/x\":\"a\\rb\"}}}", "filesystem.links[\"/x\"]");
    }

    @Test
    public void testFilesystemSystemDirectoriesConfig() {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isFilesystemSystemDirectoriesConfigured());
        assertNull(missing.getFilesystemSystemDirectoriesConfig());

        TraceEnvironmentConfig missingKey = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"externalStorage\":{}}}");
        assertFalse(missingKey.isFilesystemSystemDirectoriesConfigured());
        assertNull(missingKey.getFilesystemSystemDirectoriesConfig());

        // explicit empty: node configured, all fields unconfigured (no defaults)
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"systemDirectories\":{}}}");
        assertTrue(empty.isFilesystemSystemDirectoriesConfigured());
        TraceEnvironmentConfig.FileSystemSystemDirectoriesConfig e =
                empty.getFilesystemSystemDirectoriesConfig();
        assertNotNull(e);
        assertFalse(e.isRootDirectoryConfigured());
        assertNull(e.getRootDirectory());
        assertFalse(e.isDataDirectoryConfigured());
        assertNull(e.getDataDirectory());
        assertFalse(e.isDownloadCacheDirectoryConfigured());
        assertNull(e.getDownloadCacheDirectory());
        assertFalse(e.isStorageDirectoryConfigured());
        assertNull(e.getStorageDirectory());

        // full explicit values + path normalization
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"systemDirectories\":{"
                + "\"rootDirectory\":\"/system\","
                + "\"dataDirectory\":\"/data/./user/0\","
                + "\"downloadCacheDirectory\":\"/cache\","
                + "\"storageDirectory\":\"/storage\""
                + "}}}"
        );
        assertTrue(full.isFilesystemSystemDirectoriesConfigured());
        TraceEnvironmentConfig.FileSystemSystemDirectoriesConfig x =
                full.getFilesystemSystemDirectoriesConfig();
        assertNotNull(x);
        assertTrue(x.isRootDirectoryConfigured());
        assertEquals("/system", x.getRootDirectory());
        assertTrue(x.isDataDirectoryConfigured());
        assertEquals("/data/user/0", x.getDataDirectory());
        assertTrue(x.isDownloadCacheDirectoryConfigured());
        assertEquals("/cache", x.getDownloadCacheDirectory());
        assertTrue(x.isStorageDirectoryConfigured());
        assertEquals("/storage", x.getStorageDirectory());
        assertSame(full.getFilesystemSystemDirectoriesConfig(),
                full.getFilesystemSystemDirectoriesConfig());

        // partial: only root
        TraceEnvironmentConfig partial = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"systemDirectories\":{"
                + "\"rootDirectory\":\"/system\""
                + "}}}"
        );
        assertTrue(partial.isFilesystemSystemDirectoriesConfigured());
        assertTrue(partial.getFilesystemSystemDirectoriesConfig().isRootDirectoryConfigured());
        assertFalse(partial.getFilesystemSystemDirectoriesConfig().isDataDirectoryConfigured());
        assertFalse(partial.getFilesystemSystemDirectoriesConfig()
                .isDownloadCacheDirectoryConfigured());
        assertFalse(partial.getFilesystemSystemDirectoriesConfig().isStorageDirectoryConfigured());

        // coexist with externalStorage
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{"
                + "\"externalStorage\":{\"directory\":\"/storage/emulated/0\"},"
                + "\"systemDirectories\":{"
                + "\"rootDirectory\":\"/system\","
                + "\"dataDirectory\":\"/data\","
                + "\"storageDirectory\":\"/storage\""
                + "}"
                + "}}"
        );
        assertTrue(both.isFilesystemExternalStorageConfigured());
        assertTrue(both.isFilesystemSystemDirectoriesConfigured());
        assertEquals("/system", both.getFilesystemSystemDirectoriesConfig().getRootDirectory());
        assertEquals("/data", both.getFilesystemSystemDirectoriesConfig().getDataDirectory());
        assertEquals("/storage", both.getFilesystemSystemDirectoriesConfig().getStorageDirectory());

        assertInvalid("{\"filesystem\":{\"systemDirectories\":[]}}",
                "filesystem.systemDirectories");
        assertInvalid("{\"filesystem\":{\"systemDirectories\":1}}",
                "filesystem.systemDirectories");
        assertInvalid("{\"filesystem\":{\"systemDirectories\":null}}",
                "filesystem.systemDirectories");
        assertInvalid("{\"filesystem\":{\"systemDirectories\":{\"extra\":1}}}",
                "filesystem.systemDirectories.extra");
        assertInvalid("{\"filesystem\":{\"systemDirectories\":{\"rootDirectory\":null}}}",
                "filesystem.systemDirectories.rootDirectory");
        assertInvalid("{\"filesystem\":{\"systemDirectories\":{\"rootDirectory\":1}}}",
                "filesystem.systemDirectories.rootDirectory");
        assertInvalid("{\"filesystem\":{\"systemDirectories\":{\"rootDirectory\":\"system\"}}}",
                "filesystem.systemDirectories.rootDirectory");
        assertInvalid("{\"filesystem\":{\"systemDirectories\":{\"dataDirectory\":\"relative\"}}}",
                "filesystem.systemDirectories.dataDirectory");
        assertInvalid("{\"filesystem\":{\"systemDirectories\":{\"downloadCacheDirectory\":\"/../x\"}}}",
                "filesystem.systemDirectories.downloadCacheDirectory");
        assertInvalid("{\"filesystem\":{\"systemDirectories\":{\"dataDirectory\":\"/a/../../b\"}}}",
                "filesystem.systemDirectories.dataDirectory");
        assertInvalid("{\"filesystem\":{\"systemDirectories\":{\"storageDirectory\":null}}}",
                "filesystem.systemDirectories.storageDirectory");
        assertInvalid("{\"filesystem\":{\"systemDirectories\":{\"storageDirectory\":1}}}",
                "filesystem.systemDirectories.storageDirectory");
        assertInvalid("{\"filesystem\":{\"systemDirectories\":{\"storageDirectory\":\"storage\"}}}",
                "filesystem.systemDirectories.storageDirectory");
        assertInvalid("{\"filesystem\":{\"systemDirectories\":{\"storageDirectory\":\"/../x\"}}}",
                "filesystem.systemDirectories.storageDirectory");
    }

    @Test
    public void testFilesystemExternalStorageConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isFilesystemExternalStorageConfigured());
        assertNull(missing.getFilesystemExternalStorageConfig());
        TraceEnvironmentConfig missingKey = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"links\":{}}}");
        assertFalse(missingKey.isFilesystemExternalStorageConfigured());
        assertNull(missingKey.getFilesystemExternalStorageConfig());

        // explicit empty → defaults
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"externalStorage\":{}}}");
        assertTrue(empty.isFilesystemExternalStorageConfigured());
        TraceEnvironmentConfig.FileSystemExternalStorageConfig e =
                empty.getFilesystemExternalStorageConfig();
        assertNotNull(e);
        assertFalse(e.isDirectoryConfigured());
        assertEquals("/storage/emulated/0", e.getDirectory());
        assertFalse(e.isStateConfigured());
        assertEquals("mounted", e.getState());
        assertFalse(e.isEmulatedConfigured());
        assertTrue(e.isEmulated());
        assertFalse(e.isRemovableConfigured());
        assertFalse(e.isRemovable());

        // full explicit values
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"externalStorage\":{"
                + "\"directory\":\"/storage/sdcard1\","
                + "\"state\":\"mounted_ro\","
                + "\"emulated\":false,"
                + "\"removable\":true"
                + "}}}"
        );
        assertTrue(full.isFilesystemExternalStorageConfigured());
        TraceEnvironmentConfig.FileSystemExternalStorageConfig x =
                full.getFilesystemExternalStorageConfig();
        assertNotNull(x);
        assertTrue(x.isDirectoryConfigured());
        assertEquals("/storage/sdcard1", x.getDirectory());
        assertTrue(x.isStateConfigured());
        assertEquals("mounted_ro", x.getState());
        assertTrue(x.isEmulatedConfigured());
        assertFalse(x.isEmulated());
        assertTrue(x.isRemovableConfigured());
        assertTrue(x.isRemovable());

        // path normalization for directory
        TraceEnvironmentConfig normalized = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"externalStorage\":{"
                + "\"directory\":\"/storage/./emulated/0\""
                + "}}}"
        );
        assertEquals("/storage/emulated/0",
                normalized.getFilesystemExternalStorageConfig().getDirectory());
        assertTrue(normalized.getFilesystemExternalStorageConfig().isDirectoryConfigured());

        // immutable / stable access
        assertEquals(x.getDirectory(), full.getFilesystemExternalStorageConfig().getDirectory());
        assertEquals(x.getState(), full.getFilesystemExternalStorageConfig().getState());
        assertEquals(x.isEmulated(), full.getFilesystemExternalStorageConfig().isEmulated());
        assertEquals(x.isRemovable(), full.getFilesystemExternalStorageConfig().isRemovable());
        assertSame(full.getFilesystemExternalStorageConfig(),
                full.getFilesystemExternalStorageConfig());

        // coexist with stat / statfs / mounts / links
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{"
                + "\"stat\":{\"/proc/cpuinfo\":{\"size\":1}},"
                + "\"statfs\":{\"/data\":{\"blocks\":10}},"
                + "\"mounts\":[{\"source\":\"s\",\"target\":\"/data\","
                + "\"fileSystemType\":\"ext4\",\"options\":\"rw\"}],"
                + "\"links\":{\"/proc/self/exe\":\"/system/bin/app_process\"},"
                + "\"externalStorage\":{\"state\":\"shared\"}"
                + "}}"
        );
        assertTrue(both.isFilesystemStatConfigured());
        assertTrue(both.isFilesystemStatFsConfigured());
        assertTrue(both.isFilesystemMountsConfigured());
        assertTrue(both.isFilesystemLinksConfigured());
        assertTrue(both.isFilesystemExternalStorageConfigured());
        assertEquals("shared", both.getFilesystemExternalStorageConfig().getState());
        assertTrue(both.getFilesystemExternalStorageConfig().isStateConfigured());
        assertEquals("/storage/emulated/0",
                both.getFilesystemExternalStorageConfig().getDirectory());
        assertEquals("/system/bin/app_process",
                both.getFilesystemLinkExact("/proc/self/exe").getTarget());

        // wrong node type
        assertInvalid("{\"filesystem\":{\"externalStorage\":[]}}",
                "filesystem.externalStorage");
        assertInvalid("{\"filesystem\":{\"externalStorage\":1}}",
                "filesystem.externalStorage");
        assertInvalid("{\"filesystem\":{\"externalStorage\":\"mounted\"}}",
                "filesystem.externalStorage");

        // unknown keys
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"extra\":1}}}",
                "filesystem.externalStorage.extra");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"path\":\"/x\"}}}",
                "filesystem.externalStorage.path");

        // directory invalid
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"directory\":null}}}",
                "filesystem.externalStorage.directory");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"directory\":1}}}",
                "filesystem.externalStorage.directory");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"directory\":true}}}",
                "filesystem.externalStorage.directory");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"directory\":\"storage/emulated/0\"}}}",
                "filesystem.externalStorage.directory");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"directory\":\"relative/path\"}}}",
                "filesystem.externalStorage.directory");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"directory\":\"/data\\\\x\"}}}",
                "filesystem.externalStorage.directory");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"directory\":\"/../x\"}}}",
                "filesystem.externalStorage.directory");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"directory\":\"/a/../../b\"}}}",
                "filesystem.externalStorage.directory");

        // state invalid
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"state\":null}}}",
                "filesystem.externalStorage.state");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"state\":1}}}",
                "filesystem.externalStorage.state");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"state\":true}}}",
                "filesystem.externalStorage.state");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"state\":\"MOUNTED\"}}}",
                "filesystem.externalStorage.state");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"state\":\"available\"}}}",
                "filesystem.externalStorage.state");

        // emulated / removable wrong types
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"emulated\":null}}}",
                "filesystem.externalStorage.emulated");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"emulated\":\"true\"}}}",
                "filesystem.externalStorage.emulated");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"emulated\":1}}}",
                "filesystem.externalStorage.emulated");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"removable\":null}}}",
                "filesystem.externalStorage.removable");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"removable\":\"false\"}}}",
                "filesystem.externalStorage.removable");
        assertInvalid("{\"filesystem\":{\"externalStorage\":{\"removable\":0}}}",
                "filesystem.externalStorage.removable");
    }

    @Test
    public void testLinuxProcConfig() {
        // full (existing fields + all seven io counters)
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"state\":\"R\","
                + "\"tracerPid\":99,"
                + "\"threadCount\":8,"
                + "\"cmdline\":[\"com.demo.app\",\"--flag\",\"\"],"
                + "\"cgroups\":[\"0::/\",\"1:name=systemd:/\"],"
                + "\"startTimeTicks\":123456,"
                + "\"virtualMemoryBytes\":4096000,"
                + "\"residentSetPages\":1024,"
                + "\"rchar\":11,"
                + "\"wchar\":22,"
                + "\"syscr\":33,"
                + "\"syscw\":44,"
                + "\"readBytes\":55,"
                + "\"writeBytes\":66,"
                + "\"cancelledWriteBytes\":77,"
                + "\"bootId\":\"A1B2C3D4-E5F6-7890-ABCD-EF1234567890\","
                + "\"randomUuid\":\"B2C3D4E5-F6A7-8901-BCDE-F12345678901\","
                + "\"oomScoreAdj\":-100,"
                + "\"oomScore\":150,"
                + "\"selinuxContext\":\"u:r:untrusted_app:s0:c512,c768\","
                + "\"comm\":\"demo_app\","
                + "\"wchan\":\"0\","
                + "\"dumpable\":1"
                + "}}}"
        );
        assertTrue(full.isLinuxProcConfigured());
        TraceEnvironmentConfig.LinuxProcConfig p = full.getLinuxProcConfig();
        assertNotNull(p);
        assertTrue(p.isStateConfigured());
        assertEquals("R", p.getState());
        assertTrue(p.isTracerPidConfigured());
        assertEquals(99, p.getTracerPid());
        assertTrue(p.isThreadCountConfigured());
        assertEquals(8, p.getThreadCount());
        assertTrue(p.isCmdlineConfigured());
        assertEquals(3, p.getCmdline().size());
        assertEquals("com.demo.app", p.getCmdline().get(0));
        assertEquals("--flag", p.getCmdline().get(1));
        assertEquals("", p.getCmdline().get(2));
        assertTrue(p.isCgroupsConfigured());
        assertEquals(2, p.getCgroups().size());
        assertEquals("0::/", p.getCgroups().get(0));
        assertTrue(p.isStartTimeTicksConfigured());
        assertEquals(123456L, p.getStartTimeTicks());
        assertTrue(p.isVirtualMemoryBytesConfigured());
        assertEquals(4096000L, p.getVirtualMemoryBytes());
        assertTrue(p.isResidentSetPagesConfigured());
        assertEquals(1024L, p.getResidentSetPages());
        assertTrue(p.isRcharConfigured());
        assertEquals(11L, p.getRchar());
        assertTrue(p.isWcharConfigured());
        assertEquals(22L, p.getWchar());
        assertTrue(p.isSyscrConfigured());
        assertEquals(33L, p.getSyscr());
        assertTrue(p.isSyscwConfigured());
        assertEquals(44L, p.getSyscw());
        assertTrue(p.isReadBytesConfigured());
        assertEquals(55L, p.getReadBytes());
        assertTrue(p.isWriteBytesConfigured());
        assertEquals(66L, p.getWriteBytes());
        assertTrue(p.isCancelledWriteBytesConfigured());
        assertEquals(77L, p.getCancelledWriteBytes());
        assertTrue(p.isBootIdConfigured());
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", p.getBootId());
        assertTrue(p.isRandomUuidConfigured());
        assertEquals("b2c3d4e5-f6a7-8901-bcde-f12345678901", p.getRandomUuid());
        assertTrue(p.isOomScoreAdjConfigured());
        assertEquals(-100, p.getOomScoreAdj());
        assertTrue(p.isOomScoreConfigured());
        assertEquals(150, p.getOomScore());
        assertTrue(p.isSelinuxContextConfigured());
        assertEquals("u:r:untrusted_app:s0:c512,c768", p.getSelinuxContext());
        assertTrue(p.isCommConfigured());
        assertEquals("demo_app", p.getComm());
        assertTrue(p.isWchanConfigured());
        assertEquals("0", p.getWchan());
        assertTrue(p.isDumpableConfigured());
        assertEquals(1, p.getDumpable());
        // full fixture without seccompMode → unconfigured
        assertFalse(p.isSeccompModeConfigured());

        // explicit empty object → defaults, arrays/io/bootId/randomUuid/oom*/selinux/comm/wchan/dumpable/seccompMode not configured
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{}}}");
        assertTrue(empty.isLinuxProcConfigured());
        TraceEnvironmentConfig.LinuxProcConfig e = empty.getLinuxProcConfig();
        assertNotNull(e);
        assertFalse(e.isStateConfigured());
        assertEquals("S", e.getState());
        assertFalse(e.isTracerPidConfigured());
        assertEquals(0, e.getTracerPid());
        assertFalse(e.isThreadCountConfigured());
        assertEquals(1, e.getThreadCount());
        assertFalse(e.isCmdlineConfigured());
        assertTrue(e.getCmdline().isEmpty());
        assertFalse(e.isCgroupsConfigured());
        assertTrue(e.getCgroups().isEmpty());
        assertFalse(e.isStartTimeTicksConfigured());
        assertEquals(0L, e.getStartTimeTicks());
        assertFalse(e.isVirtualMemoryBytesConfigured());
        assertEquals(0L, e.getVirtualMemoryBytes());
        assertFalse(e.isResidentSetPagesConfigured());
        assertEquals(0L, e.getResidentSetPages());
        assertFalse(e.isRcharConfigured());
        assertEquals(0L, e.getRchar());
        assertFalse(e.isWcharConfigured());
        assertEquals(0L, e.getWchar());
        assertFalse(e.isSyscrConfigured());
        assertEquals(0L, e.getSyscr());
        assertFalse(e.isSyscwConfigured());
        assertEquals(0L, e.getSyscw());
        assertFalse(e.isReadBytesConfigured());
        assertEquals(0L, e.getReadBytes());
        assertFalse(e.isWriteBytesConfigured());
        assertEquals(0L, e.getWriteBytes());
        assertFalse(e.isCancelledWriteBytesConfigured());
        assertEquals(0L, e.getCancelledWriteBytes());
        assertFalse(e.isBootIdConfigured());
        assertNull(e.getBootId());
        assertFalse(e.isRandomUuidConfigured());
        assertNull(e.getRandomUuid());
        assertFalse(e.isOomScoreAdjConfigured());
        assertFalse(e.isOomScoreConfigured());
        assertFalse(e.isSelinuxContextConfigured());
        assertNull(e.getSelinuxContext());
        assertFalse(e.isFileSelinuxContextsConfigured());
        assertTrue(e.getFileSelinuxContexts().isEmpty());
        assertFalse(e.isCommConfigured());
        assertNull(e.getComm());
        assertFalse(e.isWchanConfigured());
        assertNull(e.getWchan());
        assertFalse(e.isDumpableConfigured());
        assertFalse(e.isSeccompModeConfigured());
        assertFalse(e.isNoNewPrivsConfigured());
        assertFalse(e.isCapEffectiveHexConfigured());
        assertNull(e.getCapEffectiveHex());
        assertFalse(e.isCapPermittedHexConfigured());
        assertNull(e.getCapPermittedHex());
        assertFalse(e.isCapBoundingHexConfigured());
        assertNull(e.getCapBoundingHex());
        assertFalse(e.isCapInheritableHexConfigured());
        assertNull(e.getCapInheritableHex());
        assertFalse(e.isCapAmbientHexConfigured());
        assertNull(e.getCapAmbientHex());
        assertFalse(e.isSignalBlockedHexConfigured());
        assertNull(e.getSignalBlockedHex());
        assertFalse(e.isSignalIgnoredHexConfigured());
        assertNull(e.getSignalIgnoredHex());
        assertFalse(e.isSignalCaughtHexConfigured());
        assertNull(e.getSignalCaughtHex());
        assertFalse(e.isLimitsConfigured());
        assertTrue(e.getLimits().isEmpty());

        // partial io fields: only rchar + writeBytes configured
        TraceEnvironmentConfig partialIo = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"rchar\":100,"
                + "\"writeBytes\":200"
                + "}}}"
        );
        TraceEnvironmentConfig.LinuxProcConfig pi = partialIo.getLinuxProcConfig();
        assertTrue(pi.isRcharConfigured());
        assertEquals(100L, pi.getRchar());
        assertTrue(pi.isWriteBytesConfigured());
        assertEquals(200L, pi.getWriteBytes());
        assertFalse(pi.isWcharConfigured());
        assertEquals(0L, pi.getWchar());
        assertFalse(pi.isSyscrConfigured());
        assertEquals(0L, pi.getSyscr());
        assertFalse(pi.isSyscwConfigured());
        assertEquals(0L, pi.getSyscw());
        assertFalse(pi.isReadBytesConfigured());
        assertEquals(0L, pi.getReadBytes());
        assertFalse(pi.isCancelledWriteBytesConfigured());
        assertEquals(0L, pi.getCancelledWriteBytes());

        // empty arrays when present
        TraceEnvironmentConfig emptyArr = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"cmdline\":[],\"cgroups\":[]}}}"
        );
        assertTrue(emptyArr.getLinuxProcConfig().isCmdlineConfigured());
        assertTrue(emptyArr.getLinuxProcConfig().getCmdline().isEmpty());
        assertTrue(emptyArr.getLinuxProcConfig().isCgroupsConfigured());
        assertTrue(emptyArr.getLinuxProcConfig().getCgroups().isEmpty());

        // missing linux.proc
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isLinuxProcConfigured());
        assertNull(missing.getLinuxProcConfig());

        TraceEnvironmentConfig noProc = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"uname\":{\"machine64\":\"aarch64\"}}}");
        assertFalse(noProc.isLinuxProcConfigured());
        assertNull(noProc.getLinuxProcConfig());

        // coexist with uname + files + existing proc fields + io
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{"
                + "\"uname\":{\"machine64\":\"aarch64\"},"
                + "\"files\":{\"/proc/cpuinfo\":\"x\"},"
                + "\"proc\":{\"state\":\"S\",\"threadCount\":2,\"rchar\":9,\"readBytes\":8}"
                + "}}"
        );
        assertTrue(both.isLinuxProcConfigured());
        assertEquals("aarch64", both.getUnameMachine(true, null));
        assertEquals("x", new String(both.getLinuxFileBytes("/proc/cpuinfo")));
        assertEquals(2, both.getLinuxProcConfig().getThreadCount());
        assertEquals(9L, both.getLinuxProcConfig().getRchar());
        assertEquals(8L, both.getLinuxProcConfig().getReadBytes());
        assertFalse(both.getLinuxProcConfig().isWcharConfigured());

        // all legal states
        String[] states = {"R", "S", "D", "Z", "T", "t", "X", "I"};
        for (String st : states) {
            TraceEnvironmentConfig c = TraceEnvironmentConfig.parse(
                    "{\"linux\":{\"proc\":{\"state\":\"" + st + "\"}}}");
            assertEquals(st, c.getLinuxProcConfig().getState());
        }

        // bounds (including all io Long.MAX_VALUE)
        TraceEnvironmentConfig zeros = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"tracerPid\":0,\"threadCount\":1,"
                + "\"startTimeTicks\":0,\"virtualMemoryBytes\":0,\"residentSetPages\":0,"
                + "\"rchar\":0,\"wchar\":0,\"syscr\":0,\"syscw\":0,"
                + "\"readBytes\":0,\"writeBytes\":0,\"cancelledWriteBytes\":0"
                + "}}}"
        );
        assertEquals(0, zeros.getLinuxProcConfig().getTracerPid());
        assertEquals(1, zeros.getLinuxProcConfig().getThreadCount());
        assertEquals(0L, zeros.getLinuxProcConfig().getRchar());
        assertTrue(zeros.getLinuxProcConfig().isRcharConfigured());

        TraceEnvironmentConfig wide = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"tracerPid\":2147483647,\"threadCount\":2147483647,"
                + "\"startTimeTicks\":9223372036854775807,"
                + "\"virtualMemoryBytes\":9223372036854775807,"
                + "\"residentSetPages\":9223372036854775807,"
                + "\"rchar\":9223372036854775807,"
                + "\"wchar\":9223372036854775807,"
                + "\"syscr\":9223372036854775807,"
                + "\"syscw\":9223372036854775807,"
                + "\"readBytes\":9223372036854775807,"
                + "\"writeBytes\":9223372036854775807,"
                + "\"cancelledWriteBytes\":9223372036854775807"
                + "}}}"
        );
        assertEquals(Integer.MAX_VALUE, wide.getLinuxProcConfig().getTracerPid());
        assertEquals(Long.MAX_VALUE, wide.getLinuxProcConfig().getStartTimeTicks());
        assertEquals(Long.MAX_VALUE, wide.getLinuxProcConfig().getRchar());
        assertEquals(Long.MAX_VALUE, wide.getLinuxProcConfig().getWchar());
        assertEquals(Long.MAX_VALUE, wide.getLinuxProcConfig().getSyscr());
        assertEquals(Long.MAX_VALUE, wide.getLinuxProcConfig().getSyscw());
        assertEquals(Long.MAX_VALUE, wide.getLinuxProcConfig().getReadBytes());
        assertEquals(Long.MAX_VALUE, wide.getLinuxProcConfig().getWriteBytes());
        assertEquals(Long.MAX_VALUE, wide.getLinuxProcConfig().getCancelledWriteBytes());

        // immutability
        try {
            full.getLinuxProcConfig().getCmdline().add("x");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        try {
            full.getLinuxProcConfig().getCgroups().add("x");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }

        // illegal shape / keys / types
        assertInvalid("{\"linux\":{\"proc\":[]}}", "linux.proc");
        assertInvalid("{\"linux\":{\"proc\":1}}", "linux.proc");
        assertInvalid("{\"linux\":{\"proc\":{\"extra\":1}}}", "linux.proc.extra");
        assertInvalid("{\"linux\":{\"proc\":{\"state\":null}}}", "linux.proc.state");
        assertInvalid("{\"linux\":{\"proc\":{\"state\":\"\"}}}", "linux.proc.state");
        assertInvalid("{\"linux\":{\"proc\":{\"state\":\"SS\"}}}", "linux.proc.state");
        assertInvalid("{\"linux\":{\"proc\":{\"state\":\"Q\"}}}", "linux.proc.state");
        assertInvalid("{\"linux\":{\"proc\":{\"tracerPid\":-1}}}", "linux.proc.tracerPid");
        assertInvalid("{\"linux\":{\"proc\":{\"tracerPid\":1.5}}}", "linux.proc.tracerPid");
        assertInvalid("{\"linux\":{\"proc\":{\"threadCount\":0}}}", "linux.proc.threadCount");
        assertInvalid("{\"linux\":{\"proc\":{\"threadCount\":null}}}", "linux.proc.threadCount");
        assertInvalid("{\"linux\":{\"proc\":{\"cmdline\":{}}}}", "linux.proc.cmdline");
        assertInvalid("{\"linux\":{\"proc\":{\"cmdline\":[1]}}}", "linux.proc.cmdline[0]");
        assertInvalid("{\"linux\":{\"proc\":{\"cmdline\":[\"a\\nb\"]}}}", "linux.proc.cmdline[0]");
        assertInvalid("{\"linux\":{\"proc\":{\"cgroups\":1}}}", "linux.proc.cgroups");
        assertInvalid("{\"linux\":{\"proc\":{\"cgroups\":[\"\"]}}}", "linux.proc.cgroups[0]");
        assertInvalid("{\"linux\":{\"proc\":{\"cgroups\":[\"a\\rb\"]}}}", "linux.proc.cgroups[0]");
        assertInvalid("{\"linux\":{\"proc\":{\"startTimeTicks\":-1}}}", "linux.proc.startTimeTicks");
        assertInvalid("{\"linux\":{\"proc\":{\"virtualMemoryBytes\":1.5}}}",
                "linux.proc.virtualMemoryBytes");
        assertInvalid("{\"linux\":{\"proc\":{\"residentSetPages\":-1}}}",
                "linux.proc.residentSetPages");
        assertInvalid("{\"linux\":{\"proc\":{\"residentSetPages\":9223372036854775808}}}",
                "linux.proc.residentSetPages");

        // io field illegal types / negatives / decimals / overflow / unknown key
        assertInvalid("{\"linux\":{\"proc\":{\"rchar\":null}}}", "linux.proc.rchar");
        assertInvalid("{\"linux\":{\"proc\":{\"rchar\":\"1\"}}}", "linux.proc.rchar");
        assertInvalid("{\"linux\":{\"proc\":{\"rchar\":true}}}", "linux.proc.rchar");
        assertInvalid("{\"linux\":{\"proc\":{\"wchar\":1.5}}}", "linux.proc.wchar");
        assertInvalid("{\"linux\":{\"proc\":{\"syscr\":-1}}}", "linux.proc.syscr");
        assertInvalid("{\"linux\":{\"proc\":{\"syscw\":-2}}}", "linux.proc.syscw");
        assertInvalid("{\"linux\":{\"proc\":{\"readBytes\":1.5}}}", "linux.proc.readBytes");
        assertInvalid("{\"linux\":{\"proc\":{\"writeBytes\":9223372036854775808}}}",
                "linux.proc.writeBytes");
        assertInvalid("{\"linux\":{\"proc\":{\"cancelledWriteBytes\":-1}}}",
                "linux.proc.cancelledWriteBytes");
        assertInvalid("{\"linux\":{\"proc\":{\"cancelledWriteBytes\":null}}}",
                "linux.proc.cancelledWriteBytes");
        assertInvalid("{\"linux\":{\"proc\":{\"read_bytes\":1}}}", "linux.proc.read_bytes");
        assertInvalid("{\"linux\":{\"proc\":{\"io\":1}}}", "linux.proc.io");

        // bootId: normalize lowercase; reject null/blank/non-UUID/non-String; never from random.uuid
        TraceEnvironmentConfig bootUpper = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"bootId\":\"FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF\""
                + "}}}"
        );
        assertTrue(bootUpper.getLinuxProcConfig().isBootIdConfigured());
        assertEquals("ffffffff-ffff-ffff-ffff-ffffffffffff",
                bootUpper.getLinuxProcConfig().getBootId());

        TraceEnvironmentConfig bootLower = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"bootId\":\"00000000-0000-0000-0000-000000000001\""
                + "}}}"
        );
        assertEquals("00000000-0000-0000-0000-000000000001",
                bootLower.getLinuxProcConfig().getBootId());

        // random.uuid must not populate bootId
        TraceEnvironmentConfig randomOnly = TraceEnvironmentConfig.parse("{"
                + "\"random\":{\"uuid\":\"11111111-1111-1111-1111-111111111111\"},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        assertTrue(randomOnly.isLinuxProcConfigured());
        assertFalse(randomOnly.getLinuxProcConfig().isBootIdConfigured());
        assertNull(randomOnly.getLinuxProcConfig().getBootId());
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                randomOnly.getUuid(null));

        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":null}}}", "linux.proc.bootId");
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":\"\"}}}", "linux.proc.bootId");
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":\"   \"}}}", "linux.proc.bootId");
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":\"not-a-uuid\"}}}", "linux.proc.bootId");
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":1}}}", "linux.proc.bootId");
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":true}}}", "linux.proc.bootId");
        // UUID.fromString accepts short form "1-1-1-1-1" — bootId must reject it
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":\"1-1-1-1-1\"}}}", "linux.proc.bootId");
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":\"0-0-0-0-0\"}}}", "linux.proc.bootId");
        // leading/trailing whitespace (exact 36 form only; no trim)
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":\" a1b2c3d4-e5f6-7890-abcd-ef1234567890\"}}}",
                "linux.proc.bootId");
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":\"a1b2c3d4-e5f6-7890-abcd-ef1234567890 \"}}}",
                "linux.proc.bootId");
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":\" a1b2c3d4-e5f6-7890-abcd-ef1234567890 \"}}}",
                "linux.proc.bootId");
        // extra text / short groups with padding attempts
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":\"a1b2c3d4-e5f6-7890-abcd-ef1234567890x\"}}}",
                "linux.proc.bootId");
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":\"xa1b2c3d4-e5f6-7890-abcd-ef1234567890\"}}}",
                "linux.proc.bootId");
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":\"a1b2c3d4-e5f6-7890-abcd-ef123456789\"}}}",
                "linux.proc.bootId");
        assertInvalid("{\"linux\":{\"proc\":{\"bootId\":\"a1b2c3d-e5f6-7890-abcd-ef1234567890\"}}}",
                "linux.proc.bootId");

        // randomUuid: same canonical UUID rules; independent of random.uuid and bootId
        TraceEnvironmentConfig randUpper = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"randomUuid\":\"FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFE\""
                + "}}}"
        );
        assertTrue(randUpper.getLinuxProcConfig().isRandomUuidConfigured());
        assertEquals("ffffffff-ffff-ffff-ffff-fffffffffffe",
                randUpper.getLinuxProcConfig().getRandomUuid());
        assertFalse(randUpper.getLinuxProcConfig().isBootIdConfigured());

        // random.uuid must not populate randomUuid
        TraceEnvironmentConfig randomUuidOnly = TraceEnvironmentConfig.parse("{"
                + "\"random\":{\"uuid\":\"11111111-1111-1111-1111-111111111111\"},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        assertFalse(randomUuidOnly.getLinuxProcConfig().isRandomUuidConfigured());
        assertNull(randomUuidOnly.getLinuxProcConfig().getRandomUuid());

        // bootId alone does not set randomUuid
        TraceEnvironmentConfig bootOnly = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"bootId\":\"00000000-0000-0000-0000-000000000001\""
                + "}}}"
        );
        assertTrue(bootOnly.getLinuxProcConfig().isBootIdConfigured());
        assertFalse(bootOnly.getLinuxProcConfig().isRandomUuidConfigured());
        assertNull(bootOnly.getLinuxProcConfig().getRandomUuid());

        // both can coexist with different values
        TraceEnvironmentConfig bothUuid = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"bootId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"randomUuid\":\"00000000-0000-0000-0000-000000000002\""
                + "}}}"
        );
        assertEquals("00000000-0000-0000-0000-000000000001",
                bothUuid.getLinuxProcConfig().getBootId());
        assertEquals("00000000-0000-0000-0000-000000000002",
                bothUuid.getLinuxProcConfig().getRandomUuid());

        assertInvalid("{\"linux\":{\"proc\":{\"randomUuid\":null}}}", "linux.proc.randomUuid");
        assertInvalid("{\"linux\":{\"proc\":{\"randomUuid\":\"\"}}}", "linux.proc.randomUuid");
        assertInvalid("{\"linux\":{\"proc\":{\"randomUuid\":\"   \"}}}", "linux.proc.randomUuid");
        assertInvalid("{\"linux\":{\"proc\":{\"randomUuid\":\"not-a-uuid\"}}}",
                "linux.proc.randomUuid");
        assertInvalid("{\"linux\":{\"proc\":{\"randomUuid\":1}}}", "linux.proc.randomUuid");
        assertInvalid("{\"linux\":{\"proc\":{\"randomUuid\":\"1-1-1-1-1\"}}}",
                "linux.proc.randomUuid");
        assertInvalid("{\"linux\":{\"proc\":{\"randomUuid\":\" a1b2c3d4-e5f6-7890-abcd-ef1234567890\"}}}",
                "linux.proc.randomUuid");
        assertInvalid("{\"linux\":{\"proc\":{\"randomUuid\":\"a1b2c3d4-e5f6-7890-abcd-ef1234567890x\"}}}",
                "linux.proc.randomUuid");

        // oomScoreAdj: exact int -1000..1000; no default; reject type/bounds
        TraceEnvironmentConfig oomMin = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScoreAdj\":-1000}}}");
        assertTrue(oomMin.getLinuxProcConfig().isOomScoreAdjConfigured());
        assertEquals(-1000, oomMin.getLinuxProcConfig().getOomScoreAdj());
        TraceEnvironmentConfig oomMax = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScoreAdj\":1000}}}");
        assertEquals(1000, oomMax.getLinuxProcConfig().getOomScoreAdj());
        TraceEnvironmentConfig oomZero = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScoreAdj\":0}}}");
        assertTrue(oomZero.getLinuxProcConfig().isOomScoreAdjConfigured());
        assertEquals(0, oomZero.getLinuxProcConfig().getOomScoreAdj());
        assertInvalid("{\"linux\":{\"proc\":{\"oomScoreAdj\":-1001}}}", "linux.proc.oomScoreAdj");
        assertInvalid("{\"linux\":{\"proc\":{\"oomScoreAdj\":1001}}}", "linux.proc.oomScoreAdj");
        assertInvalid("{\"linux\":{\"proc\":{\"oomScoreAdj\":1.5}}}", "linux.proc.oomScoreAdj");
        assertInvalid("{\"linux\":{\"proc\":{\"oomScoreAdj\":null}}}", "linux.proc.oomScoreAdj");
        assertInvalid("{\"linux\":{\"proc\":{\"oomScoreAdj\":\"0\"}}}", "linux.proc.oomScoreAdj");
        assertInvalid("{\"linux\":{\"proc\":{\"oomScoreAdj\":true}}}", "linux.proc.oomScoreAdj");
        assertInvalid("{\"linux\":{\"proc\":{\"oom_score_adj\":0}}}", "linux.proc.oom_score_adj");

        // oomScore: exact int 0..2000; no default; independent of oomScoreAdj
        TraceEnvironmentConfig scoreMin = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScore\":0}}}");
        assertTrue(scoreMin.getLinuxProcConfig().isOomScoreConfigured());
        assertEquals(0, scoreMin.getLinuxProcConfig().getOomScore());
        assertFalse(scoreMin.getLinuxProcConfig().isOomScoreAdjConfigured());
        TraceEnvironmentConfig scoreMax = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScore\":2000}}}");
        assertEquals(2000, scoreMax.getLinuxProcConfig().getOomScore());
        TraceEnvironmentConfig scoreMid = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScore\":500}}}");
        assertEquals(500, scoreMid.getLinuxProcConfig().getOomScore());
        // oomScoreAdj alone does not set oomScore
        TraceEnvironmentConfig adjOnly = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"oomScoreAdj\":100}}}");
        assertTrue(adjOnly.getLinuxProcConfig().isOomScoreAdjConfigured());
        assertFalse(adjOnly.getLinuxProcConfig().isOomScoreConfigured());
        assertInvalid("{\"linux\":{\"proc\":{\"oomScore\":-1}}}", "linux.proc.oomScore");
        assertInvalid("{\"linux\":{\"proc\":{\"oomScore\":2001}}}", "linux.proc.oomScore");
        assertInvalid("{\"linux\":{\"proc\":{\"oomScore\":1.5}}}", "linux.proc.oomScore");
        assertInvalid("{\"linux\":{\"proc\":{\"oomScore\":null}}}", "linux.proc.oomScore");
        assertInvalid("{\"linux\":{\"proc\":{\"oomScore\":\"0\"}}}", "linux.proc.oomScore");
        assertInvalid("{\"linux\":{\"proc\":{\"oomScore\":true}}}", "linux.proc.oomScore");
        assertInvalid("{\"linux\":{\"proc\":{\"oom_score\":0}}}", "linux.proc.oom_score");

        // selinuxContext: nonempty printable ASCII no whitespace/controls, max 256; no default
        TraceEnvironmentConfig se = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"selinuxContext\":\"u:r:untrusted_app:s0:c512,c768\""
                + "}}}"
        );
        assertTrue(se.getLinuxProcConfig().isSelinuxContextConfigured());
        assertEquals("u:r:untrusted_app:s0:c512,c768", se.getLinuxProcConfig().getSelinuxContext());
        // max length 256
        StringBuilder maxSe = new StringBuilder(256);
        for (int i = 0; i < 256; i++) {
            maxSe.append('a');
        }
        TraceEnvironmentConfig seMax = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"selinuxContext\":\"" + maxSe + "\"}}}"
        );
        assertEquals(256, seMax.getLinuxProcConfig().getSelinuxContext().length());
        assertInvalid("{\"linux\":{\"proc\":{\"selinuxContext\":null}}}",
                "linux.proc.selinuxContext");
        assertInvalid("{\"linux\":{\"proc\":{\"selinuxContext\":\"\"}}}",
                "linux.proc.selinuxContext");
        assertInvalid("{\"linux\":{\"proc\":{\"selinuxContext\":\"u:r:app s0\"}}}",
                "linux.proc.selinuxContext");
        assertInvalid("{\"linux\":{\"proc\":{\"selinuxContext\":\"u:r:app\\ts0\"}}}",
                "linux.proc.selinuxContext");
        assertInvalid("{\"linux\":{\"proc\":{\"selinuxContext\":\"u:r:app\\ns0\"}}}",
                "linux.proc.selinuxContext");
        assertInvalid("{\"linux\":{\"proc\":{\"selinuxContext\":\" leading\"}}}",
                "linux.proc.selinuxContext");
        assertInvalid("{\"linux\":{\"proc\":{\"selinuxContext\":\"trailing \"}}}",
                "linux.proc.selinuxContext");
        assertInvalid("{\"linux\":{\"proc\":{\"selinuxContext\":1}}}",
                "linux.proc.selinuxContext");
        assertInvalid("{\"linux\":{\"proc\":{\"selinuxContext\":true}}}",
                "linux.proc.selinuxContext");
        // 257 chars
        StringBuilder tooLong = new StringBuilder(257);
        for (int i = 0; i < 257; i++) {
            tooLong.append('x');
        }
        assertInvalid("{\"linux\":{\"proc\":{\"selinuxContext\":\"" + tooLong + "\"}}}",
                "linux.proc.selinuxContext");

        // fileSelinuxContexts: optional path→context map; absolute paths; no default
        TraceEnvironmentConfig fse = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"fileSelinuxContexts\":{"
                + "\"/data/data/com.demo.app\":\"u:object_r:app_data_file:s0\","
                + "\"/system/bin/app_process64\":\"u:object_r:zygote_exec:s0\""
                + "}}}}"
        );
        assertTrue(fse.getLinuxProcConfig().isFileSelinuxContextsConfigured());
        assertEquals(2, fse.getLinuxProcConfig().getFileSelinuxContexts().size());
        assertEquals("u:object_r:app_data_file:s0",
                fse.getLinuxProcConfig().lookupFileSelinuxContext("/data/data/com.demo.app"));
        assertEquals("u:object_r:zygote_exec:s0",
                fse.getLinuxProcConfig().lookupFileSelinuxContext(
                        "/system/bin/./app_process64"));
        assertNull(fse.getLinuxProcConfig().lookupFileSelinuxContext("/unconfigured"));
        assertFalse(fse.getLinuxProcConfig().isSelinuxContextConfigured());
        TraceEnvironmentConfig fseEmpty = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"fileSelinuxContexts\":{}}}}");
        assertTrue(fseEmpty.getLinuxProcConfig().isFileSelinuxContextsConfigured());
        assertTrue(fseEmpty.getLinuxProcConfig().getFileSelinuxContexts().isEmpty());
        assertFalse(se.getLinuxProcConfig().isFileSelinuxContextsConfigured());
        assertInvalid("{\"linux\":{\"proc\":{\"fileSelinuxContexts\":null}}}",
                "linux.proc.fileSelinuxContexts");
        assertInvalid("{\"linux\":{\"proc\":{\"fileSelinuxContexts\":[]}}}",
                "linux.proc.fileSelinuxContexts");
        assertInvalid("{\"linux\":{\"proc\":{\"fileSelinuxContexts\":\"x\"}}}",
                "linux.proc.fileSelinuxContexts");
        assertInvalid("{\"linux\":{\"proc\":{\"fileSelinuxContexts\":{\"rel\":\"u:r:x:s0\"}}}}",
                "linux.proc.fileSelinuxContexts");
        assertInvalid("{\"linux\":{\"proc\":{\"fileSelinuxContexts\":{\"/a\":\"\"}}}}",
                "linux.proc.fileSelinuxContexts");
        assertInvalid("{\"linux\":{\"proc\":{\"fileSelinuxContexts\":{\"/a\":null}}}}",
                "linux.proc.fileSelinuxContexts");
        assertInvalid("{\"linux\":{\"proc\":{\"fileSelinuxContexts\":{"
                        + "\"/data/app\":\"u:object_r:x:s0\","
                        + "\"/data/./app\":\"u:object_r:y:s0\"}}}}",
                "duplicate");

        // comm: nonempty printable ASCII (0x20..0x7E), no CR/LF, max 15; no default;
        // never from processName/threadName
        TraceEnvironmentConfig commOk = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"comm\":\"a\"}}}"
        );
        assertTrue(commOk.getLinuxProcConfig().isCommConfigured());
        assertEquals("a", commOk.getLinuxProcConfig().getComm());
        // max 15 printable ASCII (incl. space)
        TraceEnvironmentConfig commMax = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"comm\":\"123456789012345\"}}}"
        );
        assertEquals("123456789012345", commMax.getLinuxProcConfig().getComm());
        TraceEnvironmentConfig commSpace = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"comm\":\"my app\"}}}"
        );
        assertEquals("my app", commSpace.getLinuxProcConfig().getComm());
        // processName alone must not set comm
        TraceEnvironmentConfig nameOnly = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"processName\":\"com.demo.app\"},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        assertFalse(nameOnly.getLinuxProcConfig().isCommConfigured());
        assertNull(nameOnly.getLinuxProcConfig().getComm());
        TraceEnvironmentConfig threadOnly = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"threadName\":\"main\"},"
                + "\"linux\":{\"proc\":{}}"
                + "}");
        assertFalse(threadOnly.getLinuxProcConfig().isCommConfigured());
        assertNull(threadOnly.getLinuxProcConfig().getComm());
        assertInvalid("{\"linux\":{\"proc\":{\"comm\":null}}}", "linux.proc.comm");
        assertInvalid("{\"linux\":{\"proc\":{\"comm\":\"\"}}}", "linux.proc.comm");
        assertInvalid("{\"linux\":{\"proc\":{\"comm\":\"a\\nb\"}}}", "linux.proc.comm");
        assertInvalid("{\"linux\":{\"proc\":{\"comm\":\"a\\rb\"}}}", "linux.proc.comm");
        assertInvalid("{\"linux\":{\"proc\":{\"comm\":\"a\\tb\"}}}", "linux.proc.comm");
        assertInvalid("{\"linux\":{\"proc\":{\"comm\":\"café\"}}}", "linux.proc.comm");
        assertInvalid("{\"linux\":{\"proc\":{\"comm\":\"1234567890123456\"}}}",
                "linux.proc.comm");
        assertInvalid("{\"linux\":{\"proc\":{\"comm\":1}}}", "linux.proc.comm");
        assertInvalid("{\"linux\":{\"proc\":{\"comm\":true}}}", "linux.proc.comm");

        // wchan: nonempty printable ASCII (0x20..0x7E), no CR/LF, max 255; no default/derivation
        TraceEnvironmentConfig wchanZero = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"wchan\":\"0\"}}}"
        );
        assertTrue(wchanZero.getLinuxProcConfig().isWchanConfigured());
        assertEquals("0", wchanZero.getLinuxProcConfig().getWchan());
        TraceEnvironmentConfig wchanSym = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"wchan\":\"futex_wait_queue_me\"}}}"
        );
        assertEquals("futex_wait_queue_me", wchanSym.getLinuxProcConfig().getWchan());
        // max 255
        StringBuilder maxWchan = new StringBuilder(255);
        for (int i = 0; i < 255; i++) {
            maxWchan.append('w');
        }
        TraceEnvironmentConfig wchanMax = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"wchan\":\"" + maxWchan + "\"}}}"
        );
        assertEquals(255, wchanMax.getLinuxProcConfig().getWchan().length());
        assertInvalid("{\"linux\":{\"proc\":{\"wchan\":null}}}", "linux.proc.wchan");
        assertInvalid("{\"linux\":{\"proc\":{\"wchan\":\"\"}}}", "linux.proc.wchan");
        assertInvalid("{\"linux\":{\"proc\":{\"wchan\":\"a\\nb\"}}}", "linux.proc.wchan");
        assertInvalid("{\"linux\":{\"proc\":{\"wchan\":\"a\\rb\"}}}", "linux.proc.wchan");
        assertInvalid("{\"linux\":{\"proc\":{\"wchan\":\"a\\tb\"}}}", "linux.proc.wchan");
        assertInvalid("{\"linux\":{\"proc\":{\"wchan\":\"café\"}}}", "linux.proc.wchan");
        StringBuilder tooLongWchan = new StringBuilder(256);
        for (int i = 0; i < 256; i++) {
            tooLongWchan.append('x');
        }
        assertInvalid("{\"linux\":{\"proc\":{\"wchan\":\"" + tooLongWchan + "\"}}}",
                "linux.proc.wchan");
        assertInvalid("{\"linux\":{\"proc\":{\"wchan\":1}}}", "linux.proc.wchan");
        assertInvalid("{\"linux\":{\"proc\":{\"wchan\":true}}}", "linux.proc.wchan");

        // dumpable: exact int 0..2; no default; only when key present
        TraceEnvironmentConfig dump0 = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"dumpable\":0}}}");
        assertTrue(dump0.getLinuxProcConfig().isDumpableConfigured());
        assertEquals(0, dump0.getLinuxProcConfig().getDumpable());
        TraceEnvironmentConfig dump2 = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"dumpable\":2}}}");
        assertEquals(2, dump2.getLinuxProcConfig().getDumpable());
        TraceEnvironmentConfig dump1 = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"dumpable\":1}}}");
        assertEquals(1, dump1.getLinuxProcConfig().getDumpable());
        assertInvalid("{\"linux\":{\"proc\":{\"dumpable\":-1}}}", "linux.proc.dumpable");
        assertInvalid("{\"linux\":{\"proc\":{\"dumpable\":3}}}", "linux.proc.dumpable");
        assertInvalid("{\"linux\":{\"proc\":{\"dumpable\":1.5}}}", "linux.proc.dumpable");
        assertInvalid("{\"linux\":{\"proc\":{\"dumpable\":null}}}", "linux.proc.dumpable");
        assertInvalid("{\"linux\":{\"proc\":{\"dumpable\":\"0\"}}}", "linux.proc.dumpable");
        assertInvalid("{\"linux\":{\"proc\":{\"dumpable\":true}}}", "linux.proc.dumpable");

        // seccompMode: exact int 0..2; no default; independent of dumpable
        TraceEnvironmentConfig sc0 = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"seccompMode\":0}}}");
        assertTrue(sc0.getLinuxProcConfig().isSeccompModeConfigured());
        assertEquals(0, sc0.getLinuxProcConfig().getSeccompMode());
        assertFalse(sc0.getLinuxProcConfig().isDumpableConfigured());
        TraceEnvironmentConfig sc1 = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"seccompMode\":1}}}");
        assertEquals(1, sc1.getLinuxProcConfig().getSeccompMode());
        TraceEnvironmentConfig sc2 = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"seccompMode\":2}}}");
        assertEquals(2, sc2.getLinuxProcConfig().getSeccompMode());
        TraceEnvironmentConfig scWithDump = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"dumpable\":1,\"seccompMode\":2}}}");
        assertEquals(1, scWithDump.getLinuxProcConfig().getDumpable());
        assertEquals(2, scWithDump.getLinuxProcConfig().getSeccompMode());
        assertTrue(scWithDump.getLinuxProcConfig().isDumpableConfigured());
        assertTrue(scWithDump.getLinuxProcConfig().isSeccompModeConfigured());
        TraceEnvironmentConfig scAbsent = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"state\":\"S\"}}}");
        assertFalse(scAbsent.getLinuxProcConfig().isSeccompModeConfigured());
        assertInvalid("{\"linux\":{\"proc\":{\"seccompMode\":-1}}}", "linux.proc.seccompMode");
        assertInvalid("{\"linux\":{\"proc\":{\"seccompMode\":3}}}", "linux.proc.seccompMode");
        assertInvalid("{\"linux\":{\"proc\":{\"seccompMode\":1.5}}}", "linux.proc.seccompMode");
        assertInvalid("{\"linux\":{\"proc\":{\"seccompMode\":null}}}", "linux.proc.seccompMode");
        assertInvalid("{\"linux\":{\"proc\":{\"seccompMode\":\"0\"}}}", "linux.proc.seccompMode");
        assertInvalid("{\"linux\":{\"proc\":{\"seccompMode\":true}}}", "linux.proc.seccompMode");

        // noNewPrivs: strict Boolean; no default; independent of seccompMode/dumpable
        TraceEnvironmentConfig nnpTrue = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"noNewPrivs\":true}}}");
        assertTrue(nnpTrue.getLinuxProcConfig().isNoNewPrivsConfigured());
        assertTrue(nnpTrue.getLinuxProcConfig().isNoNewPrivs());
        assertFalse(nnpTrue.getLinuxProcConfig().isSeccompModeConfigured());
        assertFalse(nnpTrue.getLinuxProcConfig().isDumpableConfigured());
        TraceEnvironmentConfig nnpFalse = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"noNewPrivs\":false}}}");
        assertTrue(nnpFalse.getLinuxProcConfig().isNoNewPrivsConfigured());
        assertFalse(nnpFalse.getLinuxProcConfig().isNoNewPrivs());
        TraceEnvironmentConfig nnpWithOthers = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"dumpable\":1,\"seccompMode\":2,\"noNewPrivs\":true}}}");
        assertEquals(1, nnpWithOthers.getLinuxProcConfig().getDumpable());
        assertEquals(2, nnpWithOthers.getLinuxProcConfig().getSeccompMode());
        assertTrue(nnpWithOthers.getLinuxProcConfig().isNoNewPrivs());
        assertFalse(scAbsent.getLinuxProcConfig().isNoNewPrivsConfigured());
        assertInvalid("{\"linux\":{\"proc\":{\"noNewPrivs\":null}}}", "linux.proc.noNewPrivs");
        assertInvalid("{\"linux\":{\"proc\":{\"noNewPrivs\":1}}}", "linux.proc.noNewPrivs");
        assertInvalid("{\"linux\":{\"proc\":{\"noNewPrivs\":\"true\"}}}", "linux.proc.noNewPrivs");
        assertInvalid("{\"linux\":{\"proc\":{\"noNewPrivs\":0}}}", "linux.proc.noNewPrivs");

        // capEffectiveHex: fixed-width 16 hex, store lowercase; no default; only CapEff
        TraceEnvironmentConfig capOk = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"capEffectiveHex\":\"0000000000000000\"}}}");
        assertTrue(capOk.getLinuxProcConfig().isCapEffectiveHexConfigured());
        assertEquals("0000000000000000", capOk.getLinuxProcConfig().getCapEffectiveHex());
        assertFalse(capOk.getLinuxProcConfig().isSeccompModeConfigured());
        assertFalse(capOk.getLinuxProcConfig().isNoNewPrivsConfigured());
        TraceEnvironmentConfig capUpper = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"capEffectiveHex\":\"0000001FFFFFFFFF\"}}}");
        assertEquals("0000001fffffffff",
                capUpper.getLinuxProcConfig().getCapEffectiveHex());
        TraceEnvironmentConfig capMixed = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"capEffectiveHex\":\"AbCdEf0123456789\"}}}");
        assertEquals("abcdef0123456789",
                capMixed.getLinuxProcConfig().getCapEffectiveHex());
        assertFalse(scAbsent.getLinuxProcConfig().isCapEffectiveHexConfigured());
        assertNull(scAbsent.getLinuxProcConfig().getCapEffectiveHex());
        assertInvalid("{\"linux\":{\"proc\":{\"capEffectiveHex\":null}}}",
                "linux.proc.capEffectiveHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capEffectiveHex\":\"\"}}}",
                "linux.proc.capEffectiveHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capEffectiveHex\":\"000000000000000\"}}}",
                "linux.proc.capEffectiveHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capEffectiveHex\":\"00000000000000000\"}}}",
                "linux.proc.capEffectiveHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capEffectiveHex\":\"0x0000000000000000\"}}}",
                "linux.proc.capEffectiveHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capEffectiveHex\":\"00000000 00000000\"}}}",
                "linux.proc.capEffectiveHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capEffectiveHex\":\"gggggggggggggggg\"}}}",
                "linux.proc.capEffectiveHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capEffectiveHex\":1}}}",
                "linux.proc.capEffectiveHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capEffectiveHex\":true}}}",
                "linux.proc.capEffectiveHex");

        // capPermittedHex: same 16-hex rules as CapEff; independent; only CapPrm
        TraceEnvironmentConfig prmOk = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"capPermittedHex\":\"0000000000000000\"}}}");
        assertTrue(prmOk.getLinuxProcConfig().isCapPermittedHexConfigured());
        assertEquals("0000000000000000", prmOk.getLinuxProcConfig().getCapPermittedHex());
        assertFalse(prmOk.getLinuxProcConfig().isCapEffectiveHexConfigured());
        assertNull(prmOk.getLinuxProcConfig().getCapEffectiveHex());
        TraceEnvironmentConfig prmUpper = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"capPermittedHex\":\"0000001FFFFFFFFF\"}}}");
        assertEquals("0000001fffffffff",
                prmUpper.getLinuxProcConfig().getCapPermittedHex());
        TraceEnvironmentConfig bothCaps = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"capPermittedHex\":\"1111111111111111\","
                + "\"capEffectiveHex\":\"2222222222222222\""
                + "}}}");
        assertEquals("1111111111111111",
                bothCaps.getLinuxProcConfig().getCapPermittedHex());
        assertEquals("2222222222222222",
                bothCaps.getLinuxProcConfig().getCapEffectiveHex());
        assertTrue(bothCaps.getLinuxProcConfig().isCapPermittedHexConfigured());
        assertTrue(bothCaps.getLinuxProcConfig().isCapEffectiveHexConfigured());
        assertFalse(scAbsent.getLinuxProcConfig().isCapPermittedHexConfigured());
        assertNull(scAbsent.getLinuxProcConfig().getCapPermittedHex());
        assertInvalid("{\"linux\":{\"proc\":{\"capPermittedHex\":null}}}",
                "linux.proc.capPermittedHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capPermittedHex\":\"\"}}}",
                "linux.proc.capPermittedHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capPermittedHex\":\"000000000000000\"}}}",
                "linux.proc.capPermittedHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capPermittedHex\":\"00000000000000000\"}}}",
                "linux.proc.capPermittedHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capPermittedHex\":\"0x0000000000000000\"}}}",
                "linux.proc.capPermittedHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capPermittedHex\":\"00000000 00000000\"}}}",
                "linux.proc.capPermittedHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capPermittedHex\":\"gggggggggggggggg\"}}}",
                "linux.proc.capPermittedHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capPermittedHex\":1}}}",
                "linux.proc.capPermittedHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capPermittedHex\":true}}}",
                "linux.proc.capPermittedHex");

        // capBoundingHex: same 16-hex rules; independent of CapPrm/CapEff; only CapBnd
        TraceEnvironmentConfig bndOk = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"capBoundingHex\":\"0000000000000000\"}}}");
        assertTrue(bndOk.getLinuxProcConfig().isCapBoundingHexConfigured());
        assertEquals("0000000000000000", bndOk.getLinuxProcConfig().getCapBoundingHex());
        assertFalse(bndOk.getLinuxProcConfig().isCapEffectiveHexConfigured());
        assertFalse(bndOk.getLinuxProcConfig().isCapPermittedHexConfigured());
        TraceEnvironmentConfig bndUpper = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"capBoundingHex\":\"0000003FFFFFFFFF\"}}}");
        assertEquals("0000003fffffffff",
                bndUpper.getLinuxProcConfig().getCapBoundingHex());
        TraceEnvironmentConfig threeCaps = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"capPermittedHex\":\"1111111111111111\","
                + "\"capEffectiveHex\":\"2222222222222222\","
                + "\"capBoundingHex\":\"3333333333333333\""
                + "}}}");
        assertEquals("1111111111111111",
                threeCaps.getLinuxProcConfig().getCapPermittedHex());
        assertEquals("2222222222222222",
                threeCaps.getLinuxProcConfig().getCapEffectiveHex());
        assertEquals("3333333333333333",
                threeCaps.getLinuxProcConfig().getCapBoundingHex());
        assertFalse(scAbsent.getLinuxProcConfig().isCapBoundingHexConfigured());
        assertNull(scAbsent.getLinuxProcConfig().getCapBoundingHex());
        assertInvalid("{\"linux\":{\"proc\":{\"capBoundingHex\":null}}}",
                "linux.proc.capBoundingHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capBoundingHex\":\"\"}}}",
                "linux.proc.capBoundingHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capBoundingHex\":\"000000000000000\"}}}",
                "linux.proc.capBoundingHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capBoundingHex\":\"00000000000000000\"}}}",
                "linux.proc.capBoundingHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capBoundingHex\":\"0x0000000000000000\"}}}",
                "linux.proc.capBoundingHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capBoundingHex\":\"00000000 00000000\"}}}",
                "linux.proc.capBoundingHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capBoundingHex\":\"gggggggggggggggg\"}}}",
                "linux.proc.capBoundingHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capBoundingHex\":1}}}",
                "linux.proc.capBoundingHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capBoundingHex\":true}}}",
                "linux.proc.capBoundingHex");

        // capInheritableHex: same 16-hex rules; independent; only CapInh
        TraceEnvironmentConfig inhOk = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"capInheritableHex\":\"0000000000000000\"}}}");
        assertTrue(inhOk.getLinuxProcConfig().isCapInheritableHexConfigured());
        assertEquals("0000000000000000", inhOk.getLinuxProcConfig().getCapInheritableHex());
        assertFalse(inhOk.getLinuxProcConfig().isCapEffectiveHexConfigured());
        assertFalse(inhOk.getLinuxProcConfig().isCapPermittedHexConfigured());
        assertFalse(inhOk.getLinuxProcConfig().isCapBoundingHexConfigured());
        TraceEnvironmentConfig inhUpper = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"capInheritableHex\":\"0000001FFFFFFFFF\"}}}");
        assertEquals("0000001fffffffff",
                inhUpper.getLinuxProcConfig().getCapInheritableHex());
        TraceEnvironmentConfig fourCaps = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"capInheritableHex\":\"0000000000000000\","
                + "\"capPermittedHex\":\"1111111111111111\","
                + "\"capEffectiveHex\":\"2222222222222222\","
                + "\"capBoundingHex\":\"3333333333333333\""
                + "}}}");
        assertEquals("0000000000000000",
                fourCaps.getLinuxProcConfig().getCapInheritableHex());
        assertEquals("1111111111111111",
                fourCaps.getLinuxProcConfig().getCapPermittedHex());
        assertEquals("2222222222222222",
                fourCaps.getLinuxProcConfig().getCapEffectiveHex());
        assertEquals("3333333333333333",
                fourCaps.getLinuxProcConfig().getCapBoundingHex());
        assertFalse(scAbsent.getLinuxProcConfig().isCapInheritableHexConfigured());
        assertNull(scAbsent.getLinuxProcConfig().getCapInheritableHex());
        assertInvalid("{\"linux\":{\"proc\":{\"capInheritableHex\":null}}}",
                "linux.proc.capInheritableHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capInheritableHex\":\"\"}}}",
                "linux.proc.capInheritableHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capInheritableHex\":\"000000000000000\"}}}",
                "linux.proc.capInheritableHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capInheritableHex\":\"00000000000000000\"}}}",
                "linux.proc.capInheritableHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capInheritableHex\":\"0x0000000000000000\"}}}",
                "linux.proc.capInheritableHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capInheritableHex\":\"00000000 00000000\"}}}",
                "linux.proc.capInheritableHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capInheritableHex\":\"gggggggggggggggg\"}}}",
                "linux.proc.capInheritableHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capInheritableHex\":1}}}",
                "linux.proc.capInheritableHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capInheritableHex\":true}}}",
                "linux.proc.capInheritableHex");

        // capAmbientHex: same 16-hex rules; independent; only CapAmb; no prctl ambient
        TraceEnvironmentConfig ambOk = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"capAmbientHex\":\"0000000000000000\"}}}");
        assertTrue(ambOk.getLinuxProcConfig().isCapAmbientHexConfigured());
        assertEquals("0000000000000000", ambOk.getLinuxProcConfig().getCapAmbientHex());
        assertFalse(ambOk.getLinuxProcConfig().isCapInheritableHexConfigured());
        assertFalse(ambOk.getLinuxProcConfig().isCapEffectiveHexConfigured());
        assertFalse(ambOk.getLinuxProcConfig().isCapBoundingHexConfigured());
        TraceEnvironmentConfig ambUpper = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"capAmbientHex\":\"0000001FFFFFFFFF\"}}}");
        assertEquals("0000001fffffffff",
                ambUpper.getLinuxProcConfig().getCapAmbientHex());
        TraceEnvironmentConfig fiveCaps = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"capInheritableHex\":\"0000000000000000\","
                + "\"capPermittedHex\":\"1111111111111111\","
                + "\"capEffectiveHex\":\"2222222222222222\","
                + "\"capBoundingHex\":\"3333333333333333\","
                + "\"capAmbientHex\":\"4444444444444444\""
                + "}}}");
        assertEquals("0000000000000000",
                fiveCaps.getLinuxProcConfig().getCapInheritableHex());
        assertEquals("1111111111111111",
                fiveCaps.getLinuxProcConfig().getCapPermittedHex());
        assertEquals("2222222222222222",
                fiveCaps.getLinuxProcConfig().getCapEffectiveHex());
        assertEquals("3333333333333333",
                fiveCaps.getLinuxProcConfig().getCapBoundingHex());
        assertEquals("4444444444444444",
                fiveCaps.getLinuxProcConfig().getCapAmbientHex());
        assertFalse(scAbsent.getLinuxProcConfig().isCapAmbientHexConfigured());
        assertNull(scAbsent.getLinuxProcConfig().getCapAmbientHex());
        assertInvalid("{\"linux\":{\"proc\":{\"capAmbientHex\":null}}}",
                "linux.proc.capAmbientHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capAmbientHex\":\"\"}}}",
                "linux.proc.capAmbientHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capAmbientHex\":\"000000000000000\"}}}",
                "linux.proc.capAmbientHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capAmbientHex\":\"00000000000000000\"}}}",
                "linux.proc.capAmbientHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capAmbientHex\":\"0x0000000000000000\"}}}",
                "linux.proc.capAmbientHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capAmbientHex\":\"00000000 00000000\"}}}",
                "linux.proc.capAmbientHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capAmbientHex\":\"gggggggggggggggg\"}}}",
                "linux.proc.capAmbientHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capAmbientHex\":1}}}",
                "linux.proc.capAmbientHex");
        assertInvalid("{\"linux\":{\"proc\":{\"capAmbientHex\":true}}}",
                "linux.proc.capAmbientHex");

        // signalBlockedHex / signalIgnoredHex / signalCaughtHex: same 16-hex rules; independent
        TraceEnvironmentConfig sigBlk = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"signalBlockedHex\":\"0000000000000000\"}}}");
        assertTrue(sigBlk.getLinuxProcConfig().isSignalBlockedHexConfigured());
        assertEquals("0000000000000000", sigBlk.getLinuxProcConfig().getSignalBlockedHex());
        assertFalse(sigBlk.getLinuxProcConfig().isSignalIgnoredHexConfigured());
        assertFalse(sigBlk.getLinuxProcConfig().isSignalCaughtHexConfigured());
        TraceEnvironmentConfig sigIgn = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"signalIgnoredHex\":\"0000000000001000\"}}}");
        assertTrue(sigIgn.getLinuxProcConfig().isSignalIgnoredHexConfigured());
        assertEquals("0000000000001000", sigIgn.getLinuxProcConfig().getSignalIgnoredHex());
        TraceEnvironmentConfig sigCgt = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"signalCaughtHex\":\"0000000180000000\"}}}");
        assertTrue(sigCgt.getLinuxProcConfig().isSignalCaughtHexConfigured());
        assertEquals("0000000180000000", sigCgt.getLinuxProcConfig().getSignalCaughtHex());
        TraceEnvironmentConfig sigUpper = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"signalBlockedHex\":\"00000000FFFFFFFF\"}}}");
        assertEquals("00000000ffffffff",
                sigUpper.getLinuxProcConfig().getSignalBlockedHex());
        TraceEnvironmentConfig threeSig = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{"
                + "\"signalBlockedHex\":\"1111111111111111\","
                + "\"signalIgnoredHex\":\"2222222222222222\","
                + "\"signalCaughtHex\":\"3333333333333333\""
                + "}}}");
        assertEquals("1111111111111111",
                threeSig.getLinuxProcConfig().getSignalBlockedHex());
        assertEquals("2222222222222222",
                threeSig.getLinuxProcConfig().getSignalIgnoredHex());
        assertEquals("3333333333333333",
                threeSig.getLinuxProcConfig().getSignalCaughtHex());
        assertFalse(scAbsent.getLinuxProcConfig().isSignalBlockedHexConfigured());
        assertNull(scAbsent.getLinuxProcConfig().getSignalBlockedHex());
        for (String key : new String[]{
                "signalBlockedHex", "signalIgnoredHex", "signalCaughtHex"
        }) {
            String path = "linux.proc." + key;
            assertInvalid("{\"linux\":{\"proc\":{\"" + key + "\":null}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + key + "\":\"\"}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + key + "\":\"000000000000000\"}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + key + "\":\"00000000000000000\"}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + key + "\":\"0x0000000000000000\"}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + key + "\":\"00000000 00000000\"}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + key + "\":\"gggggggggggggggg\"}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + key + "\":1}}}", path);
            assertInvalid("{\"linux\":{\"proc\":{\"" + key + "\":true}}}", path);
        }

        // limits: ordered nonempty printable ASCII lines; [] empty; no default
        TraceEnvironmentConfig limOk = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"limits\":["
                + "\"Limit                     Soft Limit           Hard Limit           Units     \","
                + "\"Max cpu time              unlimited            unlimited            seconds   \""
                + "]}}}"
        );
        assertTrue(limOk.getLinuxProcConfig().isLimitsConfigured());
        assertEquals(2, limOk.getLinuxProcConfig().getLimits().size());
        assertEquals("Limit                     Soft Limit           Hard Limit           Units     ",
                limOk.getLinuxProcConfig().getLimits().get(0));
        TraceEnvironmentConfig limEmpty = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"limits\":[]}}}");
        assertTrue(limEmpty.getLinuxProcConfig().isLimitsConfigured());
        assertTrue(limEmpty.getLinuxProcConfig().getLimits().isEmpty());
        assertFalse(scAbsent.getLinuxProcConfig().isLimitsConfigured());
        assertTrue(scAbsent.getLinuxProcConfig().getLimits().isEmpty());
        assertInvalid("{\"linux\":{\"proc\":{\"limits\":null}}}", "linux.proc.limits");
        assertInvalid("{\"linux\":{\"proc\":{\"limits\":\"x\"}}}", "linux.proc.limits");
        assertInvalid("{\"linux\":{\"proc\":{\"limits\":1}}}", "linux.proc.limits");
        assertInvalid("{\"linux\":{\"proc\":{\"limits\":[\"\"]}}}", "linux.proc.limits[0]");
        assertInvalid("{\"linux\":{\"proc\":{\"limits\":[\"a\\nb\"]}}}", "linux.proc.limits[0]");
        assertInvalid("{\"linux\":{\"proc\":{\"limits\":[\"a\\rb\"]}}}", "linux.proc.limits[0]");
        assertInvalid("{\"linux\":{\"proc\":{\"limits\":[\"a\\u0000b\"]}}}", "linux.proc.limits[0]");
        assertInvalid("{\"linux\":{\"proc\":{\"limits\":[\"\\t\"]}}}", "linux.proc.limits[0]");
        assertInvalid("{\"linux\":{\"proc\":{\"limits\":[1]}}}", "linux.proc.limits[0]");

        // cmdline may contain spaces
        TraceEnvironmentConfig spaced = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"proc\":{\"cmdline\":[\"a b\",\"  \"]}}}"
        );
        assertEquals("a b", spaced.getLinuxProcConfig().getCmdline().get(0));
        assertEquals("  ", spaced.getLinuxProcConfig().getCmdline().get(1));
    }

    @Test
    public void testAndroidDrmConfig() {
        // full
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"drm\":{"
                + "\"available\":false,"
                + "\"marker\":\"MY_MARKER\","
                + "\"schemeUuids\":[\"EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED\","
                + "\"1077efec-c0b2-4d02-ace3-3c1e52e2fb4b\"],"
                + "\"vendor\":\"Acme\","
                + "\"version\":\"1.2.3\","
                + "\"description\":\"desc\","
                + "\"algorithms\":\"AES/CTR/NoPadding\","
                + "\"securityLevel\":\"L1\","
                + "\"hdcpLevel\":\"HDCP_V2_2\","
                + "\"maxHdcpLevel\":\"HDCP_V2_3\","
                + "\"provisioned\":false,"
                + "\"deviceUniqueIdHex\":\"0011aabb\","
                + "\"sessionIdHex\":\"dead\""
                + "}}}"
        );
        assertTrue(full.isAndroidDrmConfigured());
        TraceEnvironmentConfig.AndroidDrmConfig d = full.getAndroidDrmConfig();
        assertNotNull(d);
        assertTrue(d.isAvailableConfigured());
        assertFalse(d.isAvailable());
        assertTrue(d.isMarkerConfigured());
        assertEquals("MY_MARKER", d.getMarker());
        assertTrue(d.isSchemeUuidsConfigured());
        assertEquals(2, d.getSchemeUuids().size());
        assertEquals("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed", d.getSchemeUuids().get(0));
        assertEquals("1077efec-c0b2-4d02-ace3-3c1e52e2fb4b", d.getSchemeUuids().get(1));
        assertEquals("Acme", d.getVendor());
        assertEquals("1.2.3", d.getVersion());
        assertEquals("desc", d.getDescription());
        assertEquals("AES/CTR/NoPadding", d.getAlgorithms());
        assertEquals("L1", d.getSecurityLevel());
        assertEquals("HDCP_V2_2", d.getHdcpLevel());
        assertEquals("HDCP_V2_3", d.getMaxHdcpLevel());
        assertTrue(d.isProvisionedConfigured());
        assertFalse(d.isProvisioned());
        assertTrue(d.isDeviceUniqueIdConfigured());
        assertArrayEquals(new byte[]{0x00, 0x11, (byte) 0xaa, (byte) 0xbb}, d.getDeviceUniqueId());
        assertTrue(d.isSessionIdConfigured());
        assertArrayEquals(new byte[]{(byte) 0xde, (byte) 0xad}, d.getSessionId());

        // explicit empty → defaults
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"drm\":{}}}");
        assertTrue(empty.isAndroidDrmConfigured());
        TraceEnvironmentConfig.AndroidDrmConfig e = empty.getAndroidDrmConfig();
        assertFalse(e.isAvailableConfigured());
        assertTrue(e.isAvailable());
        assertFalse(e.isMarkerConfigured());
        assertEquals("TRACEAI_DRM_MARKER_V1", e.getMarker());
        assertFalse(e.isSchemeUuidsConfigured());
        assertEquals(1, e.getSchemeUuids().size());
        assertEquals("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed", e.getSchemeUuids().get(0));
        assertEquals("TraceAI", e.getVendor());
        assertEquals("TRACEAI_DRM_MARKER_V1", e.getVersion());
        assertEquals("TraceAI DRM analysis marker", e.getDescription());
        assertEquals("AES/CBC/NoPadding,HmacSHA256", e.getAlgorithms());
        assertEquals("L3", e.getSecurityLevel());
        assertEquals("HDCP_NONE", e.getHdcpLevel());
        assertEquals("HDCP_NONE", e.getMaxHdcpLevel());
        assertTrue(e.isProvisioned());
        assertFalse(e.isDeviceUniqueIdConfigured());
        assertNull(e.getDeviceUniqueId());
        assertFalse(e.isSessionIdConfigured());
        assertNull(e.getSessionId());

        // missing
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidDrmConfigured());
        assertNull(missing.getAndroidDrmConfig());

        // coexist with tee + build
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"build\":{\"MODEL\":\"Pixel 6\"},"
                + "\"tee\":{\"available\":true},"
                + "\"drm\":{\"securityLevel\":\"L2\"}"
                + "}}"
        );
        assertTrue(both.isAndroidDrmConfigured());
        assertTrue(both.isAndroidTeeConfigured());
        assertEquals("Pixel 6", both.getAndroidBuildString("MODEL"));
        assertEquals("L2", both.getAndroidDrmConfig().getSecurityLevel());

        // security / hdcp enums
        String[] secs = {"L1", "L2", "L3", "UNKNOWN"};
        for (String s : secs) {
            TraceEnvironmentConfig c = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"drm\":{\"securityLevel\":\"" + s + "\"}}}");
            assertEquals(s, c.getAndroidDrmConfig().getSecurityLevel());
        }
        String[] hdcps = {"HDCP_NONE", "HDCP_V1", "HDCP_V2", "HDCP_V2_1", "HDCP_V2_2",
                "HDCP_V2_3", "HDCP_NO_DIGITAL_OUTPUT", "HDCP_LEVEL_UNKNOWN"};
        for (String h : hdcps) {
            TraceEnvironmentConfig c = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"drm\":{\"hdcpLevel\":\"" + h + "\"}}}");
            assertEquals(h, c.getAndroidDrmConfig().getHdcpLevel());
        }

        // immutability
        try {
            full.getAndroidDrmConfig().getSchemeUuids().add("x");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        byte[] idCopy = full.getAndroidDrmConfig().getDeviceUniqueId();
        idCopy[0] = 0x7f;
        assertArrayEquals(new byte[]{0x00, 0x11, (byte) 0xaa, (byte) 0xbb},
                full.getAndroidDrmConfig().getDeviceUniqueId());
        byte[] sidCopy = full.getAndroidDrmConfig().getSessionId();
        sidCopy[0] = 0x00;
        assertArrayEquals(new byte[]{(byte) 0xde, (byte) 0xad},
                full.getAndroidDrmConfig().getSessionId());

        // invalid
        assertInvalid("{\"android\":{\"drm\":[]}}", "android.drm");
        assertInvalid("{\"android\":{\"drm\":1}}", "android.drm");
        assertInvalid("{\"android\":{\"drm\":{\"extra\":1}}}", "android.drm.extra");
        assertInvalid("{\"android\":{\"drm\":{\"available\":1}}}", "android.drm.available");
        assertInvalid("{\"android\":{\"drm\":{\"marker\":\"\"}}}", "android.drm.marker");
        assertInvalid("{\"android\":{\"drm\":{\"marker\":\"a\\nb\"}}}", "android.drm.marker");
        assertInvalid("{\"android\":{\"drm\":{\"marker\":\""
                        + new String(new char[129]).replace('\0', 'a') + "\"}}}",
                "android.drm.marker");
        assertInvalid("{\"android\":{\"drm\":{\"vendor\":\""
                        + new String(new char[257]).replace('\0', 'a') + "\"}}}",
                "android.drm.vendor");
        assertInvalid("{\"android\":{\"drm\":{\"schemeUuids\":[]}}}", "android.drm.schemeUuids");
        assertInvalid("{\"android\":{\"drm\":{\"schemeUuids\":1}}}", "android.drm.schemeUuids");
        assertInvalid("{\"android\":{\"drm\":{\"schemeUuids\":[\"not-a-uuid\"]}}}",
                "android.drm.schemeUuids[0]");
        assertInvalid("{\"android\":{\"drm\":{\"schemeUuids\":["
                        + "\"edef8ba9-79d6-4ace-a3c8-27dcd51d21ed\","
                        + "\"EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED\"]}}}",
                "android.drm.schemeUuids[1]");
        assertInvalid("{\"android\":{\"drm\":{\"securityLevel\":\"L0\"}}}",
                "android.drm.securityLevel");
        assertInvalid("{\"android\":{\"drm\":{\"hdcpLevel\":\"HDCP_V9\"}}}",
                "android.drm.hdcpLevel");
        assertInvalid("{\"android\":{\"drm\":{\"maxHdcpLevel\":1}}}",
                "android.drm.maxHdcpLevel");
        assertInvalid("{\"android\":{\"drm\":{\"provisioned\":\"true\"}}}",
                "android.drm.provisioned");
        assertInvalid("{\"android\":{\"drm\":{\"deviceUniqueIdHex\":\"abc\"}}}",
                "android.drm.deviceUniqueIdHex");
        assertInvalid("{\"android\":{\"drm\":{\"deviceUniqueIdHex\":\"\"}}}",
                "android.drm.deviceUniqueIdHex");
        assertInvalid("{\"android\":{\"drm\":{\"deviceUniqueIdHex\":\"zz\"}}}",
                "android.drm.deviceUniqueIdHex");
        assertInvalid("{\"android\":{\"drm\":{\"sessionIdHex\":\""
                        + new String(new char[130]).replace('\0', 'a') + "\"}}}",
                "android.drm.sessionIdHex");
        assertInvalid("{\"android\":{\"drm\":{\"deviceUniqueIdHex\":\""
                        + new String(new char[2097154]).replace('\0', 'a') + "\"}}}",
                "android.drm.deviceUniqueIdHex");
    }

    @Test
    public void testAndroidLocaleConfig() {
        // absent node → not configured, getter null (host fallback remains possible)
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidLocaleConfigured());
        assertNull(missing.getAndroidLocaleConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidLocaleConfigured());
        assertNull(missingAndroid.getAndroidLocaleConfig());

        // explicit empty → defaults en-US / UTC
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"locale\":{}}}");
        assertTrue(empty.isAndroidLocaleConfigured());
        TraceEnvironmentConfig.AndroidLocaleConfig e = empty.getAndroidLocaleConfig();
        assertNotNull(e);
        assertFalse(e.isLanguageTagConfigured());
        assertEquals("en-US", e.getLanguageTag());
        assertEquals("en-US", e.getLocale().toLanguageTag());
        assertFalse(e.isTimezoneIdConfigured());
        assertEquals("UTC", e.getTimezoneId());

        // full valid zh-Hans-CN + Asia/Shanghai
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"locale\":{"
                + "\"languageTag\":\"zh-Hans-CN\","
                + "\"timezoneId\":\"Asia/Shanghai\""
                + "}}}"
        );
        assertTrue(full.isAndroidLocaleConfigured());
        TraceEnvironmentConfig.AndroidLocaleConfig d = full.getAndroidLocaleConfig();
        assertTrue(d.isLanguageTagConfigured());
        assertEquals("zh-Hans-CN", d.getLanguageTag());
        assertEquals("zh-Hans-CN", d.getLocale().toLanguageTag());
        assertEquals("zh", d.getLocale().getLanguage());
        assertTrue(d.isTimezoneIdConfigured());
        assertEquals("Asia/Shanghai", d.getTimezoneId());

        // valid UTC explicit
        TraceEnvironmentConfig utc = TraceEnvironmentConfig.parse(
                "{\"android\":{\"locale\":{\"timezoneId\":\"UTC\"}}}");
        assertTrue(utc.isAndroidLocaleConfigured());
        assertTrue(utc.getAndroidLocaleConfig().isTimezoneIdConfigured());
        assertEquals("UTC", utc.getAndroidLocaleConfig().getTimezoneId());
        assertFalse(utc.getAndroidLocaleConfig().isLanguageTagConfigured());
        assertEquals("en-US", utc.getAndroidLocaleConfig().getLanguageTag());

        // canonicalization: region case normalized via Locale.Builder
        TraceEnvironmentConfig canon = TraceEnvironmentConfig.parse(
                "{\"android\":{\"locale\":{\"languageTag\":\"en-us\"}}}");
        assertEquals("en-US", canon.getAndroidLocaleConfig().getLanguageTag());

        // getLocale returns a fresh view (not same shared identity assumed)
        Locale localeA = full.getAndroidLocaleConfig().getLocale();
        Locale localeB = full.getAndroidLocaleConfig().getLocale();
        assertEquals(localeA, localeB);
        assertEquals("zh-Hans-CN", localeA.toLanguageTag());

        // coexist with drm / tee
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"locale\":{\"languageTag\":\"en-GB\"},"
                + "\"drm\":{\"securityLevel\":\"L2\"},"
                + "\"tee\":{\"available\":true}"
                + "}}"
        );
        assertTrue(both.isAndroidLocaleConfigured());
        assertEquals("en-GB", both.getAndroidLocaleConfig().getLanguageTag());
        assertTrue(both.isAndroidDrmConfigured());
        assertTrue(both.isAndroidTeeConfigured());

        // wrong node type
        assertInvalid("{\"android\":{\"locale\":[]}}", "android.locale");
        assertInvalid("{\"android\":{\"locale\":1}}", "android.locale");
        assertInvalid("{\"android\":{\"locale\":\"en-US\"}}", "android.locale");

        // unknown key
        assertInvalid("{\"android\":{\"locale\":{\"extra\":1}}}", "android.locale.extra");
        assertInvalid("{\"android\":{\"locale\":{\"language\":\"en\"}}}", "android.locale.language");

        // null / non-string / empty / whitespace / control / overlength languageTag
        assertInvalid("{\"android\":{\"locale\":{\"languageTag\":null}}}",
                "android.locale.languageTag");
        assertInvalid("{\"android\":{\"locale\":{\"languageTag\":1}}}",
                "android.locale.languageTag");
        assertInvalid("{\"android\":{\"locale\":{\"languageTag\":true}}}",
                "android.locale.languageTag");
        assertInvalid("{\"android\":{\"locale\":{\"languageTag\":\"\"}}}",
                "android.locale.languageTag");
        assertInvalid("{\"android\":{\"locale\":{\"languageTag\":\" en-US\"}}}",
                "android.locale.languageTag");
        assertInvalid("{\"android\":{\"locale\":{\"languageTag\":\"en-US \"}}}",
                "android.locale.languageTag");
        assertInvalid("{\"android\":{\"locale\":{\"languageTag\":\"en\\nUS\"}}}",
                "android.locale.languageTag");
        assertInvalid("{\"android\":{\"locale\":{\"languageTag\":\"en\\rUS\"}}}",
                "android.locale.languageTag");
        assertInvalid("{\"android\":{\"locale\":{\"languageTag\":\""
                        + new String(new char[129]).replace('\0', 'a') + "\"}}}",
                "android.locale.languageTag");

        // null / non-string / empty / whitespace / control / overlength timezoneId
        assertInvalid("{\"android\":{\"locale\":{\"timezoneId\":null}}}",
                "android.locale.timezoneId");
        assertInvalid("{\"android\":{\"locale\":{\"timezoneId\":1}}}",
                "android.locale.timezoneId");
        assertInvalid("{\"android\":{\"locale\":{\"timezoneId\":\"\"}}}",
                "android.locale.timezoneId");
        assertInvalid("{\"android\":{\"locale\":{\"timezoneId\":\" UTC\"}}}",
                "android.locale.timezoneId");
        assertInvalid("{\"android\":{\"locale\":{\"timezoneId\":\"UTC \"}}}",
                "android.locale.timezoneId");
        assertInvalid("{\"android\":{\"locale\":{\"timezoneId\":\"UTC\\n\"}}}",
                "android.locale.timezoneId");
        assertInvalid("{\"android\":{\"locale\":{\"timezoneId\":\""
                        + new String(new char[129]).replace('\0', 'a') + "\"}}}",
                "android.locale.timezoneId");

        // ill-formed language tags (Locale.Builder rejects; forLanguageTag would degrade)
        assertInvalid("{\"android\":{\"locale\":{\"languageTag\":\"not_a_valid_tag!!\"}}}",
                "android.locale.languageTag");
        assertInvalid("{\"android\":{\"locale\":{\"languageTag\":\"en_US\"}}}",
                "android.locale.languageTag");
        assertInvalid("{\"android\":{\"locale\":{\"languageTag\":\"zh--CN\"}}}",
                "android.locale.languageTag");
        assertInvalid("{\"android\":{\"locale\":{\"languageTag\":\"-en\"}}}",
                "android.locale.languageTag");

        // unknown timezone IDs (ZoneId.of rejects; TimeZone.getTimeZone would return GMT)
        assertInvalid("{\"android\":{\"locale\":{\"timezoneId\":\"Not/AZone\"}}}",
                "android.locale.timezoneId");
        assertInvalid("{\"android\":{\"locale\":{\"timezoneId\":\"GMT+99:00\"}}}",
                "android.locale.timezoneId");
        assertInvalid("{\"android\":{\"locale\":{\"timezoneId\":\"Invalid/Timezone\"}}}",
                "android.locale.timezoneId");
    }

    @Test
    public void testAndroidDisplayConfig() {
        // absent → not configured
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidDisplayConfigured());
        assertNull(missing.getAndroidDisplayConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidDisplayConfigured());
        assertNull(missingAndroid.getAndroidDisplayConfig());

        // explicit empty → deterministic defaults
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"display\":{}}}");
        assertTrue(empty.isAndroidDisplayConfigured());
        TraceEnvironmentConfig.AndroidDisplayConfig e = empty.getAndroidDisplayConfig();
        assertNotNull(e);
        assertFalse(e.isWidthPixelsConfigured());
        assertEquals(1080, e.getWidthPixels());
        assertFalse(e.isHeightPixelsConfigured());
        assertEquals(2400, e.getHeightPixels());
        assertFalse(e.isDensityDpiConfigured());
        assertEquals(420, e.getDensityDpi());
        assertEquals(420 / 160f, e.getDensity(), 0f);
        assertFalse(e.isScaledDensityConfigured());
        assertEquals(420 / 160f, e.getScaledDensity(), 0f);
        assertFalse(e.isXdpiConfigured());
        assertEquals(411.0f, e.getXdpi(), 0f);
        assertFalse(e.isYdpiConfigured());
        assertEquals(411.0f, e.getYdpi(), 0f);
        assertFalse(e.isRefreshRateConfigured());
        assertEquals(60.0f, e.getRefreshRate(), 0f);
        assertFalse(e.isRotationConfigured());
        assertEquals(0, e.getRotation());
        assertFalse(e.isModeIdConfigured());
        assertEquals(1, e.getModeId());

        // full explicit values
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"display\":{"
                + "\"widthPixels\":1440,"
                + "\"heightPixels\":3200,"
                + "\"densityDpi\":560,"
                + "\"scaledDensity\":3.5,"
                + "\"xdpi\":513.0,"
                + "\"ydpi\":512.5,"
                + "\"refreshRate\":90.0,"
                + "\"rotation\":1,"
                + "\"modeId\":2"
                + "}}}"
        );
        assertTrue(full.isAndroidDisplayConfigured());
        TraceEnvironmentConfig.AndroidDisplayConfig d = full.getAndroidDisplayConfig();
        assertTrue(d.isWidthPixelsConfigured());
        assertEquals(1440, d.getWidthPixels());
        assertTrue(d.isHeightPixelsConfigured());
        assertEquals(3200, d.getHeightPixels());
        assertTrue(d.isDensityDpiConfigured());
        assertEquals(560, d.getDensityDpi());
        assertEquals(560 / 160f, d.getDensity(), 0f);
        assertTrue(d.isScaledDensityConfigured());
        assertEquals(3.5f, d.getScaledDensity(), 0f);
        assertTrue(d.isXdpiConfigured());
        assertEquals(513.0f, d.getXdpi(), 0f);
        assertTrue(d.isYdpiConfigured());
        assertEquals(512.5f, d.getYdpi(), 0f);
        assertTrue(d.isRefreshRateConfigured());
        assertEquals(90.0f, d.getRefreshRate(), 0f);
        assertTrue(d.isRotationConfigured());
        assertEquals(1, d.getRotation());
        assertTrue(d.isModeIdConfigured());
        assertEquals(2, d.getModeId());

        // densityDpi changed, scaledDensity absent → scaledDensity derived from effective densityDpi
        TraceEnvironmentConfig derived = TraceEnvironmentConfig.parse(
                "{\"android\":{\"display\":{\"densityDpi\":480}}}");
        TraceEnvironmentConfig.AndroidDisplayConfig der = derived.getAndroidDisplayConfig();
        assertTrue(der.isDensityDpiConfigured());
        assertEquals(480, der.getDensityDpi());
        assertEquals(480 / 160f, der.getDensity(), 0f);
        assertFalse(der.isScaledDensityConfigured());
        assertEquals(480 / 160f, der.getScaledDensity(), 0f);
        // defaults for other fields
        assertEquals(1080, der.getWidthPixels());
        assertEquals(2400, der.getHeightPixels());
        assertEquals(411.0f, der.getXdpi(), 0f);

        // densityDpi default, explicit scaledDensity only
        TraceEnvironmentConfig scaledOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"display\":{\"scaledDensity\":1.25}}}");
        assertEquals(420, scaledOnly.getAndroidDisplayConfig().getDensityDpi());
        assertEquals(420 / 160f, scaledOnly.getAndroidDisplayConfig().getDensity(), 0f);
        assertEquals(1.25f, scaledOnly.getAndroidDisplayConfig().getScaledDensity(), 0f);

        // coexist with locale
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"display\":{\"widthPixels\":720},"
                + "\"locale\":{\"languageTag\":\"en-US\"}"
                + "}}"
        );
        assertTrue(both.isAndroidDisplayConfigured());
        assertEquals(720, both.getAndroidDisplayConfig().getWidthPixels());
        assertTrue(both.isAndroidLocaleConfigured());

        // wrong node type
        assertInvalid("{\"android\":{\"display\":[]}}", "android.display");
        assertInvalid("{\"android\":{\"display\":1}}", "android.display");
        assertInvalid("{\"android\":{\"display\":\"1080\"}}", "android.display");

        // unknown keys
        assertInvalid("{\"android\":{\"display\":{\"density\":2.625}}}", "android.display.density");
        assertInvalid("{\"android\":{\"display\":{\"extra\":1}}}", "android.display.extra");

        // non-number / null / boolean / string for integers
        assertInvalid("{\"android\":{\"display\":{\"widthPixels\":null}}}",
                "android.display.widthPixels");
        assertInvalid("{\"android\":{\"display\":{\"widthPixels\":true}}}",
                "android.display.widthPixels");
        assertInvalid("{\"android\":{\"display\":{\"widthPixels\":\"1080\"}}}",
                "android.display.widthPixels");
        assertInvalid("{\"android\":{\"display\":{\"heightPixels\":false}}}",
                "android.display.heightPixels");
        assertInvalid("{\"android\":{\"display\":{\"densityDpi\":\"420\"}}}",
                "android.display.densityDpi");

        // fractional integer fields rejected
        assertInvalid("{\"android\":{\"display\":{\"widthPixels\":1080.5}}}",
                "android.display.widthPixels");
        assertInvalid("{\"android\":{\"display\":{\"heightPixels\":2400.1}}}",
                "android.display.heightPixels");
        assertInvalid("{\"android\":{\"display\":{\"densityDpi\":420.5}}}",
                "android.display.densityDpi");

        // integer range / overflow
        assertInvalid("{\"android\":{\"display\":{\"widthPixels\":0}}}",
                "android.display.widthPixels");
        assertInvalid("{\"android\":{\"display\":{\"widthPixels\":32769}}}",
                "android.display.widthPixels");
        assertInvalid("{\"android\":{\"display\":{\"heightPixels\":-1}}}",
                "android.display.heightPixels");
        assertInvalid("{\"android\":{\"display\":{\"densityDpi\":0}}}",
                "android.display.densityDpi");
        assertInvalid("{\"android\":{\"display\":{\"densityDpi\":10001}}}",
                "android.display.densityDpi");
        assertInvalid("{\"android\":{\"display\":{\"widthPixels\":99999999999999999999999999999}}}",
                "android.display.widthPixels");

        // floating invalid / range
        assertInvalid("{\"android\":{\"display\":{\"scaledDensity\":null}}}",
                "android.display.scaledDensity");
        assertInvalid("{\"android\":{\"display\":{\"scaledDensity\":true}}}",
                "android.display.scaledDensity");
        assertInvalid("{\"android\":{\"display\":{\"scaledDensity\":\"2.5\"}}}",
                "android.display.scaledDensity");
        assertInvalid("{\"android\":{\"display\":{\"scaledDensity\":0}}}",
                "android.display.scaledDensity");
        assertInvalid("{\"android\":{\"display\":{\"scaledDensity\":-1}}}",
                "android.display.scaledDensity");
        assertInvalid("{\"android\":{\"display\":{\"scaledDensity\":10000.5}}}",
                "android.display.scaledDensity");
        assertInvalid("{\"android\":{\"display\":{\"xdpi\":0}}}",
                "android.display.xdpi");
        assertInvalid("{\"android\":{\"display\":{\"xdpi\":10001}}}",
                "android.display.xdpi");
        assertInvalid("{\"android\":{\"display\":{\"ydpi\":null}}}",
                "android.display.ydpi");
        assertInvalid("{\"android\":{\"display\":{\"ydpi\":\"411\"}}}",
                "android.display.ydpi");

        // boundary accepted
        TraceEnvironmentConfig bounds = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"display\":{"
                + "\"widthPixels\":1,"
                + "\"heightPixels\":32768,"
                + "\"densityDpi\":1,"
                + "\"scaledDensity\":10000,"
                + "\"xdpi\":0.0001,"
                + "\"ydpi\":10000,"
                + "\"refreshRate\":1000,"
                + "\"rotation\":3,"
                + "\"modeId\":" + Integer.MAX_VALUE
                + "}}}"
        );
        TraceEnvironmentConfig.AndroidDisplayConfig b = bounds.getAndroidDisplayConfig();
        assertEquals(1, b.getWidthPixels());
        assertEquals(32768, b.getHeightPixels());
        assertEquals(1, b.getDensityDpi());
        assertEquals(1 / 160f, b.getDensity(), 0f);
        assertEquals(10000f, b.getScaledDensity(), 0f);
        assertEquals(0.0001f, b.getXdpi(), 0f);
        assertEquals(10000f, b.getYdpi(), 0f);
        assertEquals(1000f, b.getRefreshRate(), 0f);
        assertEquals(3, b.getRotation());
        assertEquals(Integer.MAX_VALUE, b.getModeId());

        // refreshRate / rotation / modeId: coexistence with metrics defaults
        TraceEnvironmentConfig modeOnly = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"display\":{"
                + "\"refreshRate\":120.5,"
                + "\"rotation\":2,"
                + "\"modeId\":7"
                + "}}}"
        );
        TraceEnvironmentConfig.AndroidDisplayConfig m = modeOnly.getAndroidDisplayConfig();
        assertTrue(m.isRefreshRateConfigured());
        assertEquals(120.5f, m.getRefreshRate(), 0f);
        assertTrue(m.isRotationConfigured());
        assertEquals(2, m.getRotation());
        assertTrue(m.isModeIdConfigured());
        assertEquals(7, m.getModeId());
        assertEquals(1080, m.getWidthPixels());
        assertEquals(420, m.getDensityDpi());
        assertEquals(420 / 160f, m.getDensity(), 0f);

        // refreshRate wrong types / range
        assertInvalid("{\"android\":{\"display\":{\"refreshRate\":null}}}",
                "android.display.refreshRate");
        assertInvalid("{\"android\":{\"display\":{\"refreshRate\":true}}}",
                "android.display.refreshRate");
        assertInvalid("{\"android\":{\"display\":{\"refreshRate\":\"60\"}}}",
                "android.display.refreshRate");
        assertInvalid("{\"android\":{\"display\":{\"refreshRate\":0}}}",
                "android.display.refreshRate");
        assertInvalid("{\"android\":{\"display\":{\"refreshRate\":-1}}}",
                "android.display.refreshRate");
        assertInvalid("{\"android\":{\"display\":{\"refreshRate\":1000.5}}}",
                "android.display.refreshRate");
        // subnormal double that floatValue underflows to 0
        assertInvalid("{\"android\":{\"display\":{\"refreshRate\":1e-50}}}",
                "android.display.refreshRate");

        // rotation wrong types / fractional / bounds / overflow
        assertInvalid("{\"android\":{\"display\":{\"rotation\":null}}}",
                "android.display.rotation");
        assertInvalid("{\"android\":{\"display\":{\"rotation\":true}}}",
                "android.display.rotation");
        assertInvalid("{\"android\":{\"display\":{\"rotation\":\"0\"}}}",
                "android.display.rotation");
        assertInvalid("{\"android\":{\"display\":{\"rotation\":1.5}}}",
                "android.display.rotation");
        assertInvalid("{\"android\":{\"display\":{\"rotation\":-1}}}",
                "android.display.rotation");
        assertInvalid("{\"android\":{\"display\":{\"rotation\":4}}}",
                "android.display.rotation");
        assertInvalid("{\"android\":{\"display\":{\"rotation\":99999999999999999999999999999}}}",
                "android.display.rotation");

        // modeId wrong types / fractional / bounds / overflow
        assertInvalid("{\"android\":{\"display\":{\"modeId\":null}}}",
                "android.display.modeId");
        assertInvalid("{\"android\":{\"display\":{\"modeId\":false}}}",
                "android.display.modeId");
        assertInvalid("{\"android\":{\"display\":{\"modeId\":\"1\"}}}",
                "android.display.modeId");
        assertInvalid("{\"android\":{\"display\":{\"modeId\":1.1}}}",
                "android.display.modeId");
        assertInvalid("{\"android\":{\"display\":{\"modeId\":0}}}",
                "android.display.modeId");
        assertInvalid("{\"android\":{\"display\":{\"modeId\":-5}}}",
                "android.display.modeId");
        assertInvalid("{\"android\":{\"display\":{\"modeId\":99999999999999999999999999999}}}",
                "android.display.modeId");

        // lower bound accepted for new fields
        TraceEnvironmentConfig low = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"display\":{"
                + "\"refreshRate\":0.0001,"
                + "\"rotation\":0,"
                + "\"modeId\":1"
                + "}}}"
        );
        assertEquals(0.0001f, low.getAndroidDisplayConfig().getRefreshRate(), 0f);
        assertEquals(0, low.getAndroidDisplayConfig().getRotation());
        assertEquals(1, low.getAndroidDisplayConfig().getModeId());
    }

    @Test
    public void testAndroidConfigurationConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidConfigurationConfigured());
        assertNull(missing.getAndroidConfigurationConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidConfigurationConfigured());
        assertNull(missingAndroid.getAndroidConfigurationConfig());

        // explicit empty → deterministic defaults
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{}}}");
        assertTrue(empty.isAndroidConfigurationConfigured());
        TraceEnvironmentConfig.AndroidConfigurationConfig e = empty.getAndroidConfigurationConfig();
        assertNotNull(e);
        assertFalse(e.isOrientationConfigured());
        assertEquals(1, e.getOrientation());
        assertFalse(e.isScreenLayoutConfigured());
        assertEquals(34, e.getScreenLayout());
        assertFalse(e.isUiModeConfigured());
        assertEquals(17, e.getUiMode());
        assertFalse(e.isFontScaleConfigured());
        assertEquals(1.0f, e.getFontScale(), 0f);
        assertFalse(e.isDensityDpiConfigured());
        assertEquals(0, e.getDensityDpi());
        assertFalse(e.isScreenWidthDpConfigured());
        assertEquals(0, e.getScreenWidthDp());
        assertFalse(e.isScreenHeightDpConfigured());
        assertEquals(0, e.getScreenHeightDp());
        assertFalse(e.isSmallestScreenWidthDpConfigured());
        assertEquals(0, e.getSmallestScreenWidthDp());
        assertFalse(e.isKeyboardConfigured());
        assertEquals(0, e.getKeyboard());
        assertFalse(e.isNavigationConfigured());
        assertEquals(0, e.getNavigation());
        assertFalse(e.isKeyboardHiddenConfigured());
        assertEquals(0, e.getKeyboardHidden());
        assertFalse(e.isHardKeyboardHiddenConfigured());
        assertEquals(0, e.getHardKeyboardHidden());
        assertFalse(e.isNavigationHiddenConfigured());
        assertEquals(0, e.getNavigationHidden());

        // full explicit values
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"configuration\":{"
                + "\"orientation\":2,"
                + "\"screenLayout\":268435810,"
                + "\"uiMode\":33,"
                + "\"fontScale\":1.25,"
                + "\"densityDpi\":420,"
                + "\"screenWidthDp\":360,"
                + "\"screenHeightDp\":640,"
                + "\"smallestScreenWidthDp\":360,"
                + "\"keyboard\":2,"
                + "\"navigation\":2,"
                + "\"keyboardHidden\":1,"
                + "\"hardKeyboardHidden\":2,"
                + "\"navigationHidden\":1"
                + "}}}"
        );
        assertTrue(full.isAndroidConfigurationConfigured());
        TraceEnvironmentConfig.AndroidConfigurationConfig c = full.getAndroidConfigurationConfig();
        assertTrue(c.isOrientationConfigured());
        assertEquals(2, c.getOrientation());
        assertTrue(c.isScreenLayoutConfigured());
        assertEquals(268435810, c.getScreenLayout());
        assertTrue(c.isUiModeConfigured());
        assertEquals(33, c.getUiMode());
        assertTrue(c.isFontScaleConfigured());
        assertEquals(1.25f, c.getFontScale(), 0f);
        assertTrue(c.isDensityDpiConfigured());
        assertEquals(420, c.getDensityDpi());
        assertTrue(c.isScreenWidthDpConfigured());
        assertEquals(360, c.getScreenWidthDp());
        assertTrue(c.isScreenHeightDpConfigured());
        assertEquals(640, c.getScreenHeightDp());
        assertTrue(c.isSmallestScreenWidthDpConfigured());
        assertEquals(360, c.getSmallestScreenWidthDp());
        assertTrue(c.isKeyboardConfigured());
        assertEquals(2, c.getKeyboard());
        assertTrue(c.isNavigationConfigured());
        assertEquals(2, c.getNavigation());
        assertTrue(c.isKeyboardHiddenConfigured());
        assertEquals(1, c.getKeyboardHidden());
        assertTrue(c.isHardKeyboardHiddenConfigured());
        assertEquals(2, c.getHardKeyboardHidden());
        assertTrue(c.isNavigationHiddenConfigured());
        assertEquals(1, c.getNavigationHidden());

        // immutable access: repeated getters are stable (primitive-backed config)
        assertEquals(c.getOrientation(), full.getAndroidConfigurationConfig().getOrientation());
        assertEquals(c.getFontScale(), full.getAndroidConfigurationConfig().getFontScale(), 0f);
        assertEquals(c.getDensityDpi(), full.getAndroidConfigurationConfig().getDensityDpi());
        assertEquals(c.getScreenWidthDp(), full.getAndroidConfigurationConfig().getScreenWidthDp());
        assertEquals(c.getKeyboard(), full.getAndroidConfigurationConfig().getKeyboard());
        assertEquals(c.getNavigation(), full.getAndroidConfigurationConfig().getNavigation());
        assertEquals(c.getKeyboardHidden(),
                full.getAndroidConfigurationConfig().getKeyboardHidden());
        assertEquals(c.getHardKeyboardHidden(),
                full.getAndroidConfigurationConfig().getHardKeyboardHidden());
        assertEquals(c.getNavigationHidden(),
                full.getAndroidConfigurationConfig().getNavigationHidden());
        assertNotNull(full.getAndroidConfigurationConfig());

        // partial field + coexist with display (configuration densityDpi/screen*Dp independent)
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"configuration\":{\"orientation\":0},"
                + "\"display\":{\"widthPixels\":720,\"densityDpi\":560}"
                + "}}"
        );
        assertTrue(both.isAndroidConfigurationConfigured());
        assertEquals(0, both.getAndroidConfigurationConfig().getOrientation());
        assertEquals(34, both.getAndroidConfigurationConfig().getScreenLayout());
        assertFalse(both.getAndroidConfigurationConfig().isDensityDpiConfigured());
        assertEquals(0, both.getAndroidConfigurationConfig().getDensityDpi());
        assertFalse(both.getAndroidConfigurationConfig().isScreenWidthDpConfigured());
        assertEquals(0, both.getAndroidConfigurationConfig().getScreenWidthDp());
        assertFalse(both.getAndroidConfigurationConfig().isKeyboardConfigured());
        assertEquals(0, both.getAndroidConfigurationConfig().getKeyboard());
        assertFalse(both.getAndroidConfigurationConfig().isNavigationConfigured());
        assertEquals(0, both.getAndroidConfigurationConfig().getNavigation());
        assertFalse(both.getAndroidConfigurationConfig().isKeyboardHiddenConfigured());
        assertEquals(0, both.getAndroidConfigurationConfig().getKeyboardHidden());
        assertFalse(both.getAndroidConfigurationConfig().isHardKeyboardHiddenConfigured());
        assertEquals(0, both.getAndroidConfigurationConfig().getHardKeyboardHidden());
        assertFalse(both.getAndroidConfigurationConfig().isNavigationHiddenConfigured());
        assertEquals(0, both.getAndroidConfigurationConfig().getNavigationHidden());
        assertTrue(both.isAndroidDisplayConfigured());
        assertEquals(720, both.getAndroidDisplayConfig().getWidthPixels());
        assertEquals(560, both.getAndroidDisplayConfig().getDensityDpi());

        // densityDpi alone + bounds
        assertEquals(0, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"densityDpi\":0}}}")
                .getAndroidConfigurationConfig().getDensityDpi());
        assertEquals(1000, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"densityDpi\":1000}}}")
                .getAndroidConfigurationConfig().getDensityDpi());

        // screen*Dp alone + bounds + independence
        assertEquals(0, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"screenWidthDp\":0}}}")
                .getAndroidConfigurationConfig().getScreenWidthDp());
        assertEquals(10000, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"screenWidthDp\":10000}}}")
                .getAndroidConfigurationConfig().getScreenWidthDp());
        TraceEnvironmentConfig widthOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"screenWidthDp\":411}}}");
        assertTrue(widthOnly.getAndroidConfigurationConfig().isScreenWidthDpConfigured());
        assertEquals(411, widthOnly.getAndroidConfigurationConfig().getScreenWidthDp());
        assertFalse(widthOnly.getAndroidConfigurationConfig().isScreenHeightDpConfigured());
        assertEquals(0, widthOnly.getAndroidConfigurationConfig().getScreenHeightDp());
        assertFalse(widthOnly.getAndroidConfigurationConfig().isSmallestScreenWidthDpConfigured());
        assertEquals(0, widthOnly.getAndroidConfigurationConfig().getSmallestScreenWidthDp());
        assertFalse(widthOnly.getAndroidConfigurationConfig().isDensityDpiConfigured());
        assertEquals(0, widthOnly.getAndroidConfigurationConfig().getDensityDpi());

        TraceEnvironmentConfig heightOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"screenHeightDp\":731}}}");
        assertEquals(731, heightOnly.getAndroidConfigurationConfig().getScreenHeightDp());
        assertEquals(0, heightOnly.getAndroidConfigurationConfig().getScreenWidthDp());
        assertEquals(0, heightOnly.getAndroidConfigurationConfig().getSmallestScreenWidthDp());

        TraceEnvironmentConfig smallestOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"smallestScreenWidthDp\":360}}}");
        assertEquals(360, smallestOnly.getAndroidConfigurationConfig().getSmallestScreenWidthDp());
        assertEquals(0, smallestOnly.getAndroidConfigurationConfig().getScreenWidthDp());
        assertEquals(0, smallestOnly.getAndroidConfigurationConfig().getScreenHeightDp());

        // keyboard / navigation alone + bounds + independence
        TraceEnvironmentConfig keyboardOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"keyboard\":2}}}");
        assertTrue(keyboardOnly.getAndroidConfigurationConfig().isKeyboardConfigured());
        assertEquals(2, keyboardOnly.getAndroidConfigurationConfig().getKeyboard());
        assertFalse(keyboardOnly.getAndroidConfigurationConfig().isNavigationConfigured());
        assertEquals(0, keyboardOnly.getAndroidConfigurationConfig().getNavigation());
        assertFalse(keyboardOnly.getAndroidConfigurationConfig().isOrientationConfigured());
        assertEquals(1, keyboardOnly.getAndroidConfigurationConfig().getOrientation());
        TraceEnvironmentConfig navigationOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"navigation\":3}}}");
        assertTrue(navigationOnly.getAndroidConfigurationConfig().isNavigationConfigured());
        assertEquals(3, navigationOnly.getAndroidConfigurationConfig().getNavigation());
        assertFalse(navigationOnly.getAndroidConfigurationConfig().isKeyboardConfigured());
        assertEquals(0, navigationOnly.getAndroidConfigurationConfig().getKeyboard());
        assertEquals(0, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"keyboard\":0}}}")
                .getAndroidConfigurationConfig().getKeyboard());
        assertEquals(3, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"keyboard\":3}}}")
                .getAndroidConfigurationConfig().getKeyboard());
        assertEquals(0, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"navigation\":0}}}")
                .getAndroidConfigurationConfig().getNavigation());
        assertEquals(4, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"navigation\":4}}}")
                .getAndroidConfigurationConfig().getNavigation());

        // hidden-input fields alone + bounds + independence (no leak to keyboard/navigation)
        TraceEnvironmentConfig khOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"keyboardHidden\":2}}}");
        assertTrue(khOnly.getAndroidConfigurationConfig().isKeyboardHiddenConfigured());
        assertEquals(2, khOnly.getAndroidConfigurationConfig().getKeyboardHidden());
        assertFalse(khOnly.getAndroidConfigurationConfig().isHardKeyboardHiddenConfigured());
        assertEquals(0, khOnly.getAndroidConfigurationConfig().getHardKeyboardHidden());
        assertFalse(khOnly.getAndroidConfigurationConfig().isNavigationHiddenConfigured());
        assertEquals(0, khOnly.getAndroidConfigurationConfig().getNavigationHidden());
        assertFalse(khOnly.getAndroidConfigurationConfig().isKeyboardConfigured());
        assertEquals(0, khOnly.getAndroidConfigurationConfig().getKeyboard());
        assertEquals(0, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"keyboardHidden\":0}}}")
                .getAndroidConfigurationConfig().getKeyboardHidden());
        assertEquals(2, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"hardKeyboardHidden\":2}}}")
                .getAndroidConfigurationConfig().getHardKeyboardHidden());
        assertEquals(1, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"navigationHidden\":1}}}")
                .getAndroidConfigurationConfig().getNavigationHidden());

        // wrong node type
        assertInvalid("{\"android\":{\"configuration\":[]}}", "android.configuration");
        assertInvalid("{\"android\":{\"configuration\":1}}", "android.configuration");
        assertInvalid("{\"android\":{\"configuration\":\"portrait\"}}", "android.configuration");

        // unknown keys
        assertInvalid("{\"android\":{\"configuration\":{\"locale\":\"zh\"}}}",
                "android.configuration.locale");
        assertInvalid("{\"android\":{\"configuration\":{\"extra\":1}}}",
                "android.configuration.extra");

        // orientation invalid
        assertInvalid("{\"android\":{\"configuration\":{\"orientation\":null}}}",
                "android.configuration.orientation");
        assertInvalid("{\"android\":{\"configuration\":{\"orientation\":true}}}",
                "android.configuration.orientation");
        assertInvalid("{\"android\":{\"configuration\":{\"orientation\":\"1\"}}}",
                "android.configuration.orientation");
        assertInvalid("{\"android\":{\"configuration\":{\"orientation\":1.5}}}",
                "android.configuration.orientation");
        assertInvalid("{\"android\":{\"configuration\":{\"orientation\":-1}}}",
                "android.configuration.orientation");
        assertInvalid("{\"android\":{\"configuration\":{\"orientation\":4}}}",
                "android.configuration.orientation");
        assertInvalid("{\"android\":{\"configuration\":{\"orientation\":99999999999999999999999999999}}}",
                "android.configuration.orientation");

        // screenLayout invalid
        assertInvalid("{\"android\":{\"configuration\":{\"screenLayout\":null}}}",
                "android.configuration.screenLayout");
        assertInvalid("{\"android\":{\"configuration\":{\"screenLayout\":true}}}",
                "android.configuration.screenLayout");
        assertInvalid("{\"android\":{\"configuration\":{\"screenLayout\":\"34\"}}}",
                "android.configuration.screenLayout");
        assertInvalid("{\"android\":{\"configuration\":{\"screenLayout\":34.1}}}",
                "android.configuration.screenLayout");
        assertInvalid("{\"android\":{\"configuration\":{\"screenLayout\":-1}}}",
                "android.configuration.screenLayout");
        assertInvalid("{\"android\":{\"configuration\":{\"screenLayout\":99999999999999999999999999999}}}",
                "android.configuration.screenLayout");

        // uiMode invalid
        assertInvalid("{\"android\":{\"configuration\":{\"uiMode\":null}}}",
                "android.configuration.uiMode");
        assertInvalid("{\"android\":{\"configuration\":{\"uiMode\":\"17\"}}}",
                "android.configuration.uiMode");
        assertInvalid("{\"android\":{\"configuration\":{\"uiMode\":1.5}}}",
                "android.configuration.uiMode");
        assertInvalid("{\"android\":{\"configuration\":{\"uiMode\":-5}}}",
                "android.configuration.uiMode");

        // fontScale invalid
        assertInvalid("{\"android\":{\"configuration\":{\"fontScale\":null}}}",
                "android.configuration.fontScale");
        assertInvalid("{\"android\":{\"configuration\":{\"fontScale\":true}}}",
                "android.configuration.fontScale");
        assertInvalid("{\"android\":{\"configuration\":{\"fontScale\":\"1.0\"}}}",
                "android.configuration.fontScale");
        assertInvalid("{\"android\":{\"configuration\":{\"fontScale\":0}}}",
                "android.configuration.fontScale");
        assertInvalid("{\"android\":{\"configuration\":{\"fontScale\":-0.5}}}",
                "android.configuration.fontScale");
        assertInvalid("{\"android\":{\"configuration\":{\"fontScale\":10.0001}}}",
                "android.configuration.fontScale");
        assertInvalid("{\"android\":{\"configuration\":{\"fontScale\":1e-50}}}",
                "android.configuration.fontScale");

        // densityDpi invalid
        assertInvalid("{\"android\":{\"configuration\":{\"densityDpi\":null}}}",
                "android.configuration.densityDpi");
        assertInvalid("{\"android\":{\"configuration\":{\"densityDpi\":true}}}",
                "android.configuration.densityDpi");
        assertInvalid("{\"android\":{\"configuration\":{\"densityDpi\":\"420\"}}}",
                "android.configuration.densityDpi");
        assertInvalid("{\"android\":{\"configuration\":{\"densityDpi\":420.5}}}",
                "android.configuration.densityDpi");
        assertInvalid("{\"android\":{\"configuration\":{\"densityDpi\":-1}}}",
                "android.configuration.densityDpi");
        assertInvalid("{\"android\":{\"configuration\":{\"densityDpi\":1001}}}",
                "android.configuration.densityDpi");

        // screenWidthDp invalid
        assertInvalid("{\"android\":{\"configuration\":{\"screenWidthDp\":null}}}",
                "android.configuration.screenWidthDp");
        assertInvalid("{\"android\":{\"configuration\":{\"screenWidthDp\":true}}}",
                "android.configuration.screenWidthDp");
        assertInvalid("{\"android\":{\"configuration\":{\"screenWidthDp\":\"360\"}}}",
                "android.configuration.screenWidthDp");
        assertInvalid("{\"android\":{\"configuration\":{\"screenWidthDp\":360.5}}}",
                "android.configuration.screenWidthDp");
        assertInvalid("{\"android\":{\"configuration\":{\"screenWidthDp\":-1}}}",
                "android.configuration.screenWidthDp");
        assertInvalid("{\"android\":{\"configuration\":{\"screenWidthDp\":10001}}}",
                "android.configuration.screenWidthDp");

        // screenHeightDp invalid
        assertInvalid("{\"android\":{\"configuration\":{\"screenHeightDp\":null}}}",
                "android.configuration.screenHeightDp");
        assertInvalid("{\"android\":{\"configuration\":{\"screenHeightDp\":\"640\"}}}",
                "android.configuration.screenHeightDp");
        assertInvalid("{\"android\":{\"configuration\":{\"screenHeightDp\":1.5}}}",
                "android.configuration.screenHeightDp");
        assertInvalid("{\"android\":{\"configuration\":{\"screenHeightDp\":-1}}}",
                "android.configuration.screenHeightDp");
        assertInvalid("{\"android\":{\"configuration\":{\"screenHeightDp\":10001}}}",
                "android.configuration.screenHeightDp");

        // smallestScreenWidthDp invalid
        assertInvalid("{\"android\":{\"configuration\":{\"smallestScreenWidthDp\":null}}}",
                "android.configuration.smallestScreenWidthDp");
        assertInvalid("{\"android\":{\"configuration\":{\"smallestScreenWidthDp\":true}}}",
                "android.configuration.smallestScreenWidthDp");
        assertInvalid("{\"android\":{\"configuration\":{\"smallestScreenWidthDp\":\"360\"}}}",
                "android.configuration.smallestScreenWidthDp");
        assertInvalid("{\"android\":{\"configuration\":{\"smallestScreenWidthDp\":0.5}}}",
                "android.configuration.smallestScreenWidthDp");
        assertInvalid("{\"android\":{\"configuration\":{\"smallestScreenWidthDp\":-1}}}",
                "android.configuration.smallestScreenWidthDp");
        assertInvalid("{\"android\":{\"configuration\":{\"smallestScreenWidthDp\":10001}}}",
                "android.configuration.smallestScreenWidthDp");

        // keyboard invalid
        assertInvalid("{\"android\":{\"configuration\":{\"keyboard\":null}}}",
                "android.configuration.keyboard");
        assertInvalid("{\"android\":{\"configuration\":{\"keyboard\":true}}}",
                "android.configuration.keyboard");
        assertInvalid("{\"android\":{\"configuration\":{\"keyboard\":\"2\"}}}",
                "android.configuration.keyboard");
        assertInvalid("{\"android\":{\"configuration\":{\"keyboard\":1.5}}}",
                "android.configuration.keyboard");
        assertInvalid("{\"android\":{\"configuration\":{\"keyboard\":-1}}}",
                "android.configuration.keyboard");
        assertInvalid("{\"android\":{\"configuration\":{\"keyboard\":4}}}",
                "android.configuration.keyboard");

        // navigation invalid
        assertInvalid("{\"android\":{\"configuration\":{\"navigation\":null}}}",
                "android.configuration.navigation");
        assertInvalid("{\"android\":{\"configuration\":{\"navigation\":true}}}",
                "android.configuration.navigation");
        assertInvalid("{\"android\":{\"configuration\":{\"navigation\":\"2\"}}}",
                "android.configuration.navigation");
        assertInvalid("{\"android\":{\"configuration\":{\"navigation\":2.5}}}",
                "android.configuration.navigation");
        assertInvalid("{\"android\":{\"configuration\":{\"navigation\":-1}}}",
                "android.configuration.navigation");
        assertInvalid("{\"android\":{\"configuration\":{\"navigation\":5}}}",
                "android.configuration.navigation");

        // keyboardHidden / hardKeyboardHidden / navigationHidden invalid
        assertInvalid("{\"android\":{\"configuration\":{\"keyboardHidden\":null}}}",
                "android.configuration.keyboardHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"keyboardHidden\":true}}}",
                "android.configuration.keyboardHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"keyboardHidden\":\"1\"}}}",
                "android.configuration.keyboardHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"keyboardHidden\":1.5}}}",
                "android.configuration.keyboardHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"keyboardHidden\":-1}}}",
                "android.configuration.keyboardHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"keyboardHidden\":3}}}",
                "android.configuration.keyboardHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"hardKeyboardHidden\":null}}}",
                "android.configuration.hardKeyboardHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"hardKeyboardHidden\":\"2\"}}}",
                "android.configuration.hardKeyboardHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"hardKeyboardHidden\":-1}}}",
                "android.configuration.hardKeyboardHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"hardKeyboardHidden\":3}}}",
                "android.configuration.hardKeyboardHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"navigationHidden\":null}}}",
                "android.configuration.navigationHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"navigationHidden\":true}}}",
                "android.configuration.navigationHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"navigationHidden\":0.5}}}",
                "android.configuration.navigationHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"navigationHidden\":-1}}}",
                "android.configuration.navigationHidden");
        assertInvalid("{\"android\":{\"configuration\":{\"navigationHidden\":3}}}",
                "android.configuration.navigationHidden");

        // boundary accepted
        TraceEnvironmentConfig bounds = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"configuration\":{"
                + "\"orientation\":0,"
                + "\"screenLayout\":0,"
                + "\"uiMode\":" + Integer.MAX_VALUE + ","
                + "\"fontScale\":10,"
                + "\"densityDpi\":1000,"
                + "\"screenWidthDp\":0,"
                + "\"screenHeightDp\":10000,"
                + "\"smallestScreenWidthDp\":0,"
                + "\"keyboard\":0,"
                + "\"navigation\":0,"
                + "\"keyboardHidden\":0,"
                + "\"hardKeyboardHidden\":0,"
                + "\"navigationHidden\":0"
                + "}}}"
        );
        TraceEnvironmentConfig.AndroidConfigurationConfig b = bounds.getAndroidConfigurationConfig();
        assertEquals(0, b.getOrientation());
        assertEquals(0, b.getScreenLayout());
        assertEquals(Integer.MAX_VALUE, b.getUiMode());
        assertEquals(10f, b.getFontScale(), 0f);
        assertEquals(1000, b.getDensityDpi());
        assertEquals(0, b.getScreenWidthDp());
        assertEquals(10000, b.getScreenHeightDp());
        assertEquals(0, b.getSmallestScreenWidthDp());
        assertEquals(0, b.getKeyboard());
        assertEquals(0, b.getNavigation());
        assertEquals(0, b.getKeyboardHidden());
        assertEquals(0, b.getHardKeyboardHidden());
        assertEquals(0, b.getNavigationHidden());

        TraceEnvironmentConfig fontLow = TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"fontScale\":0.0001}}}");
        assertEquals(0.0001f, fontLow.getAndroidConfigurationConfig().getFontScale(), 0f);
        assertEquals(3, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"orientation\":3}}}")
                .getAndroidConfigurationConfig().getOrientation());
        assertEquals(3, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"keyboard\":3}}}")
                .getAndroidConfigurationConfig().getKeyboard());
        assertEquals(4, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"navigation\":4}}}")
                .getAndroidConfigurationConfig().getNavigation());
        assertEquals(2, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"keyboardHidden\":2}}}")
                .getAndroidConfigurationConfig().getKeyboardHidden());
        assertEquals(2, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"hardKeyboardHidden\":2}}}")
                .getAndroidConfigurationConfig().getHardKeyboardHidden());
        assertEquals(2, TraceEnvironmentConfig.parse(
                "{\"android\":{\"configuration\":{\"navigationHidden\":2}}}")
                .getAndroidConfigurationConfig().getNavigationHidden());
    }

    @Test
    public void testLinuxAuxvConfig() {
        // missing
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isLinuxAuxvConfigured());
        assertNull(missing.getLinuxAuxvConfig());
        TraceEnvironmentConfig missingLinux = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"uname\":{\"machine64\":\"aarch64\"}}}");
        assertFalse(missingLinux.isLinuxAuxvConfigured());
        assertNull(missingLinux.getLinuxAuxvConfig());

        // explicit empty → defaults
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"auxv\":{}}}");
        assertTrue(empty.isLinuxAuxvConfigured());
        TraceEnvironmentConfig.LinuxAuxvConfig e = empty.getLinuxAuxvConfig();
        assertNotNull(e);
        assertFalse(e.isHwcap32Configured());
        assertEquals(0L, e.getHwcap32());
        assertFalse(e.isHwcap2_32Configured());
        assertEquals(0L, e.getHwcap2_32());
        assertFalse(e.isHwcap64Configured());
        assertEquals(0L, e.getHwcap64());
        assertFalse(e.isHwcap2_64Configured());
        assertEquals(0L, e.getHwcap2_64());
        assertFalse(e.isPlatform32Configured());
        assertEquals("v7l", e.getPlatform32());
        assertFalse(e.isPlatform64Configured());
        assertEquals("aarch64", e.getPlatform64());
        assertFalse(e.isExecFnConfigured());
        assertNull(e.getExecFn());

        // full values
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"auxv\":{"
                + "\"hwcap32\":1,"
                + "\"hwcap2_32\":2,"
                + "\"hwcap64\":3,"
                + "\"hwcap2_64\":4,"
                + "\"platform32\":\"v7l\","
                + "\"platform64\":\"aarch64\","
                + "\"execFn\":\"/system/bin/app_process64\""
                + "}}}"
        );
        assertTrue(full.isLinuxAuxvConfigured());
        TraceEnvironmentConfig.LinuxAuxvConfig a = full.getLinuxAuxvConfig();
        assertTrue(a.isHwcap32Configured());
        assertEquals(1L, a.getHwcap32());
        assertTrue(a.isHwcap2_32Configured());
        assertEquals(2L, a.getHwcap2_32());
        assertTrue(a.isHwcap64Configured());
        assertEquals(3L, a.getHwcap64());
        assertTrue(a.isHwcap2_64Configured());
        assertEquals(4L, a.getHwcap2_64());
        assertTrue(a.isPlatform32Configured());
        assertEquals("v7l", a.getPlatform32());
        assertTrue(a.isPlatform64Configured());
        assertEquals("aarch64", a.getPlatform64());
        assertTrue(a.isExecFnConfigured());
        assertEquals("/system/bin/app_process64", a.getExecFn());

        // partial + execFn explicit null
        TraceEnvironmentConfig partial = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"auxv\":{"
                + "\"hwcap64\":255,"
                + "\"execFn\":null"
                + "}}}"
        );
        TraceEnvironmentConfig.LinuxAuxvConfig p = partial.getLinuxAuxvConfig();
        assertTrue(p.isHwcap64Configured());
        assertEquals(255L, p.getHwcap64());
        assertFalse(p.isHwcap32Configured());
        assertEquals(0L, p.getHwcap32());
        assertTrue(p.isExecFnConfigured());
        assertNull(p.getExecFn());
        assertEquals("v7l", p.getPlatform32());

        // immutable-style access
        assertEquals(a.getHwcap64(), full.getLinuxAuxvConfig().getHwcap64());
        assertEquals(a.getExecFn(), full.getLinuxAuxvConfig().getExecFn());

        // coexist with linux.proc and linux.files
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{"
                + "\"files\":{\"/proc/version\":\"Linux\\n\"},"
                + "\"proc\":{\"state\":\"R\"},"
                + "\"auxv\":{\"platform64\":\"aarch64\"}"
                + "}}"
        );
        assertTrue(both.isLinuxAuxvConfigured());
        assertEquals("aarch64", both.getLinuxAuxvConfig().getPlatform64());
        assertTrue(both.isLinuxProcConfigured());
        assertEquals("R", both.getLinuxProcConfig().getState());
        assertEquals("Linux\n", new String(both.getLinuxFileBytes("/proc/version")));

        // wrong node type
        assertInvalid("{\"linux\":{\"auxv\":[]}}", "linux.auxv");
        assertInvalid("{\"linux\":{\"auxv\":1}}", "linux.auxv");
        assertInvalid("{\"linux\":{\"auxv\":\"x\"}}", "linux.auxv");

        // unknown keys
        assertInvalid("{\"linux\":{\"auxv\":{\"extra\":1}}}", "linux.auxv.extra");
        assertInvalid("{\"linux\":{\"auxv\":{\"hwcap\":1}}}", "linux.auxv.hwcap");

        // capability wrong types / fractional / negative / overflow
        assertInvalid("{\"linux\":{\"auxv\":{\"hwcap32\":null}}}", "linux.auxv.hwcap32");
        assertInvalid("{\"linux\":{\"auxv\":{\"hwcap32\":true}}}", "linux.auxv.hwcap32");
        assertInvalid("{\"linux\":{\"auxv\":{\"hwcap32\":\"1\"}}}", "linux.auxv.hwcap32");
        assertInvalid("{\"linux\":{\"auxv\":{\"hwcap32\":1.5}}}", "linux.auxv.hwcap32");
        assertInvalid("{\"linux\":{\"auxv\":{\"hwcap2_32\":-1}}}", "linux.auxv.hwcap2_32");
        // 32-bit hwcap must fit unsigned 32-bit range (0..0xffffffff)
        assertInvalid("{\"linux\":{\"auxv\":{\"hwcap32\":4294967296}}}", "linux.auxv.hwcap32");
        assertInvalid("{\"linux\":{\"auxv\":{\"hwcap2_32\":4294967296}}}", "linux.auxv.hwcap2_32");
        assertInvalid("{\"linux\":{\"auxv\":{\"hwcap64\":99999999999999999999999999999}}}",
                "linux.auxv.hwcap64");
        assertInvalid("{\"linux\":{\"auxv\":{\"hwcap2_64\":1.5}}}", "linux.auxv.hwcap2_64");

        // platform invalid
        assertInvalid("{\"linux\":{\"auxv\":{\"platform32\":null}}}", "linux.auxv.platform32");
        assertInvalid("{\"linux\":{\"auxv\":{\"platform32\":1}}}", "linux.auxv.platform32");
        assertInvalid("{\"linux\":{\"auxv\":{\"platform32\":\"\"}}}", "linux.auxv.platform32");
        assertInvalid("{\"linux\":{\"auxv\":{\"platform32\":\"a\\nb\"}}}", "linux.auxv.platform32");
        assertInvalid("{\"linux\":{\"auxv\":{\"platform64\":\"a\\rb\"}}}", "linux.auxv.platform64");
        assertInvalid("{\"linux\":{\"auxv\":{\"platform32\":\""
                        + new String(new char[65]).replace('\0', 'a') + "\"}}}",
                "linux.auxv.platform32");

        // execFn invalid
        assertInvalid("{\"linux\":{\"auxv\":{\"execFn\":1}}}", "linux.auxv.execFn");
        assertInvalid("{\"linux\":{\"auxv\":{\"execFn\":true}}}", "linux.auxv.execFn");
        assertInvalid("{\"linux\":{\"auxv\":{\"execFn\":\"\"}}}", "linux.auxv.execFn");
        assertInvalid("{\"linux\":{\"auxv\":{\"execFn\":\"/bin\\nsh\"}}}", "linux.auxv.execFn");
        assertInvalid("{\"linux\":{\"auxv\":{\"execFn\":\""
                        + new String(new char[4097]).replace('\0', 'a') + "\"}}}",
                "linux.auxv.execFn");

        // boundaries accepted
        TraceEnvironmentConfig bounds = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"auxv\":{"
                + "\"hwcap32\":0,"
                + "\"hwcap2_32\":4294967295,"
                + "\"hwcap64\":" + Long.MAX_VALUE + ","
                + "\"platform32\":\"" + new String(new char[64]).replace('\0', 'p') + "\","
                + "\"execFn\":\"" + new String(new char[4096]).replace('\0', 'e') + "\""
                + "}}}"
        );
        TraceEnvironmentConfig.LinuxAuxvConfig b = bounds.getLinuxAuxvConfig();
        assertEquals(0L, b.getHwcap32());
        assertEquals(0xffffffffL, b.getHwcap2_32());
        assertEquals(Long.MAX_VALUE, b.getHwcap64());
        assertEquals(64, b.getPlatform32().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertEquals(4096, b.getExecFn().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);

        TraceEnvironmentConfig max32 = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"auxv\":{\"hwcap32\":4294967295}}}");
        assertEquals(0xffffffffL, max32.getLinuxAuxvConfig().getHwcap32());
    }

    @Test
    public void testLinuxCpuConfig() {
        // missing
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isLinuxCpuConfigured());
        assertNull(missing.getLinuxCpuConfig());
        TraceEnvironmentConfig missingLinux = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"uname\":{\"machine64\":\"aarch64\"}}}");
        assertFalse(missingLinux.isLinuxCpuConfigured());
        assertNull(missingLinux.getLinuxCpuConfig());

        // explicit empty → default affinityMaskHex=01; no CPU-list / processor-count keys
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"cpu\":{}}}");
        assertTrue(empty.isLinuxCpuConfigured());
        TraceEnvironmentConfig.LinuxCpuConfig e = empty.getLinuxCpuConfig();
        assertNotNull(e);
        assertFalse(e.isAffinityMaskConfigured());
        assertArrayEquals(new byte[]{0x01}, e.getAffinityMaskBytes());
        assertFalse(e.isOnlineConfigured());
        assertNull(e.getOnline());
        assertFalse(e.isOfflineConfigured());
        assertNull(e.getOffline());
        assertFalse(e.isPresentConfigured());
        assertNull(e.getPresent());
        assertFalse(e.isPossibleConfigured());
        assertNull(e.getPossible());
        assertFalse(e.isConfiguredProcessorCountConfigured());
        assertFalse(e.isOnlineProcessorCountConfigured());

        // full value + case normalization on decode
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"cpu\":{\"affinityMaskHex\":\"0aBf\"}}}");
        assertTrue(full.isLinuxCpuConfigured());
        TraceEnvironmentConfig.LinuxCpuConfig c = full.getLinuxCpuConfig();
        assertTrue(c.isAffinityMaskConfigured());
        assertArrayEquals(new byte[]{0x0a, (byte) 0xbf}, c.getAffinityMaskBytes());

        // defensive copy
        byte[] copy = c.getAffinityMaskBytes();
        copy[0] = 0x7f;
        assertArrayEquals(new byte[]{0x0a, (byte) 0xbf}, c.getAffinityMaskBytes());

        // length bounds: 1 byte and 1024 bytes
        TraceEnvironmentConfig one = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"cpu\":{\"affinityMaskHex\":\"ff\"}}}");
        assertEquals(1, one.getLinuxCpuConfig().getAffinityMaskBytes().length);
        assertEquals((byte) 0xff, one.getLinuxCpuConfig().getAffinityMaskBytes()[0]);

        StringBuilder maxHex = new StringBuilder(2048);
        for (int i = 0; i < 1024; i++) {
            maxHex.append("ab");
        }
        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"cpu\":{\"affinityMaskHex\":\"" + maxHex + "\"}}}");
        assertEquals(1024, max.getLinuxCpuConfig().getAffinityMaskBytes().length);
        assertEquals((byte) 0xab, max.getLinuxCpuConfig().getAffinityMaskBytes()[0]);

        // canonical CPU lists: independent fields + valid grammar
        TraceEnvironmentConfig lists = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"cpu\":{"
                + "\"online\":\"0-3,8\","
                + "\"offline\":\"4-7\","
                + "\"present\":\"0-7\","
                + "\"possible\":\"0-7\""
                + "}}}"
        );
        TraceEnvironmentConfig.LinuxCpuConfig lc = lists.getLinuxCpuConfig();
        assertTrue(lc.isOnlineConfigured());
        assertEquals("0-3,8", lc.getOnline());
        assertTrue(lc.isOfflineConfigured());
        assertEquals("4-7", lc.getOffline());
        assertTrue(lc.isPresentConfigured());
        assertEquals("0-7", lc.getPresent());
        assertTrue(lc.isPossibleConfigured());
        assertEquals("0-7", lc.getPossible());
        // single id and zero
        TraceEnvironmentConfig single = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"cpu\":{\"online\":\"0\"}}}");
        assertEquals("0", single.getLinuxCpuConfig().getOnline());
        assertFalse(single.getLinuxCpuConfig().isOfflineConfigured());
        // only present — independent
        TraceEnvironmentConfig presentOnly = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"cpu\":{\"present\":\"0-3,5,7-9\"}}}");
        assertTrue(presentOnly.getLinuxCpuConfig().isPresentConfigured());
        assertEquals("0-3,5,7-9", presentOnly.getLinuxCpuConfig().getPresent());
        assertFalse(presentOnly.getLinuxCpuConfig().isOnlineConfigured());
        assertFalse(presentOnly.getLinuxCpuConfig().isAffinityMaskConfigured());

        // sysconf processor counts: independent of lists/affinity; presence-aware
        TraceEnvironmentConfig counts = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"cpu\":{"
                + "\"configuredProcessorCount\":8,"
                + "\"onlineProcessorCount\":4"
                + "}}}"
        );
        TraceEnvironmentConfig.LinuxCpuConfig countsCfg = counts.getLinuxCpuConfig();
        assertTrue(countsCfg.isConfiguredProcessorCountConfigured());
        assertEquals(8, countsCfg.getConfiguredProcessorCount());
        assertTrue(countsCfg.isOnlineProcessorCountConfigured());
        assertEquals(4, countsCfg.getOnlineProcessorCount());
        assertFalse(countsCfg.isOnlineConfigured());
        assertFalse(countsCfg.isAffinityMaskConfigured());
        // only configuredProcessorCount
        TraceEnvironmentConfig confOnly = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"cpu\":{\"configuredProcessorCount\":1}}}");
        assertTrue(confOnly.getLinuxCpuConfig().isConfiguredProcessorCountConfigured());
        assertEquals(1, confOnly.getLinuxCpuConfig().getConfiguredProcessorCount());
        assertFalse(confOnly.getLinuxCpuConfig().isOnlineProcessorCountConfigured());
        // only onlineProcessorCount at max
        TraceEnvironmentConfig onlnOnly = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"cpu\":{\"onlineProcessorCount\":4096}}}");
        assertTrue(onlnOnly.getLinuxCpuConfig().isOnlineProcessorCountConfigured());
        assertEquals(4096, onlnOnly.getLinuxCpuConfig().getOnlineProcessorCount());
        assertFalse(onlnOnly.getLinuxCpuConfig().isConfiguredProcessorCountConfigured());
        // coexist with list without deriving counts
        TraceEnvironmentConfig countsWithList = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"cpu\":{"
                + "\"online\":\"0-1\","
                + "\"configuredProcessorCount\":16,"
                + "\"onlineProcessorCount\":2"
                + "}}}"
        );
        assertEquals("0-1", countsWithList.getLinuxCpuConfig().getOnline());
        assertEquals(16, countsWithList.getLinuxCpuConfig().getConfiguredProcessorCount());
        assertEquals(2, countsWithList.getLinuxCpuConfig().getOnlineProcessorCount());

        // coexist with auxv / proc / files
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{"
                + "\"files\":{\"/proc/version\":\"Linux\\n\"},"
                + "\"proc\":{\"state\":\"S\"},"
                + "\"auxv\":{\"hwcap64\":1},"
                + "\"cpu\":{\"affinityMaskHex\":\"03\",\"online\":\"0-1\"}"
                + "}}"
        );
        assertTrue(both.isLinuxCpuConfigured());
        assertArrayEquals(new byte[]{0x03}, both.getLinuxCpuConfig().getAffinityMaskBytes());
        assertEquals("0-1", both.getLinuxCpuConfig().getOnline());
        assertTrue(both.isLinuxAuxvConfigured());
        assertEquals(1L, both.getLinuxAuxvConfig().getHwcap64());
        assertTrue(both.isLinuxProcConfigured());
        assertEquals("S", both.getLinuxProcConfig().getState());
        assertEquals("Linux\n", new String(both.getLinuxFileBytes("/proc/version")));

        // wrong node type
        assertInvalid("{\"linux\":{\"cpu\":[]}}", "linux.cpu");
        assertInvalid("{\"linux\":{\"cpu\":1}}", "linux.cpu");
        assertInvalid("{\"linux\":{\"cpu\":\"01\"}}", "linux.cpu");

        // unknown keys
        assertInvalid("{\"linux\":{\"cpu\":{\"extra\":1}}}", "linux.cpu.extra");
        assertInvalid("{\"linux\":{\"cpu\":{\"cores\":8}}}", "linux.cpu.cores");

        // processor count validation
        assertInvalid("{\"linux\":{\"cpu\":{\"configuredProcessorCount\":null}}}",
                "linux.cpu.configuredProcessorCount");
        assertInvalid("{\"linux\":{\"cpu\":{\"configuredProcessorCount\":true}}}",
                "linux.cpu.configuredProcessorCount");
        assertInvalid("{\"linux\":{\"cpu\":{\"configuredProcessorCount\":\"8\"}}}",
                "linux.cpu.configuredProcessorCount");
        assertInvalid("{\"linux\":{\"cpu\":{\"configuredProcessorCount\":0}}}",
                "linux.cpu.configuredProcessorCount");
        assertInvalid("{\"linux\":{\"cpu\":{\"configuredProcessorCount\":4097}}}",
                "linux.cpu.configuredProcessorCount");
        assertInvalid("{\"linux\":{\"cpu\":{\"configuredProcessorCount\":1.5}}}",
                "linux.cpu.configuredProcessorCount");
        assertInvalid("{\"linux\":{\"cpu\":{\"onlineProcessorCount\":0}}}",
                "linux.cpu.onlineProcessorCount");
        assertInvalid("{\"linux\":{\"cpu\":{\"onlineProcessorCount\":4097}}}",
                "linux.cpu.onlineProcessorCount");
        assertInvalid("{\"linux\":{\"cpu\":{\"onlineProcessorCount\":null}}}",
                "linux.cpu.onlineProcessorCount");

        // affinityMaskHex wrong types / empty / odd / bad char / too long
        assertInvalid("{\"linux\":{\"cpu\":{\"affinityMaskHex\":null}}}",
                "linux.cpu.affinityMaskHex");
        assertInvalid("{\"linux\":{\"cpu\":{\"affinityMaskHex\":true}}}",
                "linux.cpu.affinityMaskHex");
        assertInvalid("{\"linux\":{\"cpu\":{\"affinityMaskHex\":1}}}",
                "linux.cpu.affinityMaskHex");
        assertInvalid("{\"linux\":{\"cpu\":{\"affinityMaskHex\":\"\"}}}",
                "linux.cpu.affinityMaskHex");
        assertInvalid("{\"linux\":{\"cpu\":{\"affinityMaskHex\":\"a\"}}}",
                "linux.cpu.affinityMaskHex");
        assertInvalid("{\"linux\":{\"cpu\":{\"affinityMaskHex\":\"0g\"}}}",
                "linux.cpu.affinityMaskHex");
        assertInvalid("{\"linux\":{\"cpu\":{\"affinityMaskHex\":\""
                        + maxHex + "ab\"}}}",
                "linux.cpu.affinityMaskHex");

        // CPU-list invalid grammar
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":null}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":true}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":1}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\" 0\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"0 \"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"0, 1\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"01\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"0-07\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"2-0\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"1-1\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"0,1\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"0-2,3\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"0-3,2\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"0,0\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"3,1\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"0-\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"-1\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"online\":\"0,\"}}}", "linux.cpu.online");
        assertInvalid("{\"linux\":{\"cpu\":{\"offline\":\"a\"}}}", "linux.cpu.offline");
        assertInvalid("{\"linux\":{\"cpu\":{\"present\":\"0--1\"}}}", "linux.cpu.present");
        assertInvalid("{\"linux\":{\"cpu\":{\"possible\":\"0-2,2-4\"}}}", "linux.cpu.possible");
    }

    @Test
    public void testLinuxEnvironConfig() {
        // missing key → not configured (loaders keep built-in defaults)
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isLinuxEnvironConfigured());
        assertTrue(missing.getLinuxEnviron().isEmpty());
        assertEquals(TraceEnvironmentConfig.LINUX_ENVIRON_BUILTIN_DEFAULTS,
                missing.getEffectiveLinuxEnviron());
        assertEquals(4, missing.getEffectiveLinuxEnviron().size());
        assertEquals("ANDROID_DATA=/data", missing.getEffectiveLinuxEnviron().get(0));
        TraceEnvironmentConfig missingLinux = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"proc\":{\"state\":\"S\"}}}");
        assertFalse(missingLinux.isLinuxEnvironConfigured());
        assertTrue(missingLinux.getLinuxEnviron().isEmpty());
        assertEquals(TraceEnvironmentConfig.LINUX_ENVIRON_BUILTIN_DEFAULTS,
                missingLinux.getEffectiveLinuxEnviron());

        // explicit empty array → configured empty (replaces defaults)
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"linux\":{\"environ\":[]}}");
        assertTrue(empty.isLinuxEnvironConfigured());
        assertTrue(empty.getLinuxEnviron().isEmpty());
        assertTrue(empty.getEffectiveLinuxEnviron().isEmpty());

        // full valid entries (value may contain '=')
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"environ\":["
                + "\"ANDROID_DATA=/data\","
                + "\"PATH=/sbin:/system/bin\","
                + "\"CUSTOM=a=b=c\","
                + "\"EMPTY=\""
                + "]}}"
        );
        assertTrue(full.isLinuxEnvironConfigured());
        assertEquals(4, full.getLinuxEnviron().size());
        assertEquals("ANDROID_DATA=/data", full.getLinuxEnviron().get(0));
        assertEquals("PATH=/sbin:/system/bin", full.getLinuxEnviron().get(1));
        assertEquals("CUSTOM=a=b=c", full.getLinuxEnviron().get(2));
        assertEquals("EMPTY=", full.getLinuxEnviron().get(3));

        // key forms: letter/underscore start; digits allowed after first
        TraceEnvironmentConfig keys = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"environ\":[\"_A=1\",\"a1_b=2\",\"Z9=3\"]}}"
        );
        assertEquals(3, keys.getLinuxEnviron().size());

        // max length 4096
        StringBuilder maxEntry = new StringBuilder(4096);
        maxEntry.append("K=");
        while (maxEntry.length() < 4096) {
            maxEntry.append('x');
        }
        TraceEnvironmentConfig maxOk = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{\"environ\":[\"" + maxEntry + "\"]}}"
        );
        assertEquals(4096, maxOk.getLinuxEnviron().get(0).length());

        // immutable list
        try {
            full.getLinuxEnviron().add("X=1");
            fail("expected UnsupportedOperationException for environ list mutation");
        } catch (UnsupportedOperationException expected) {
            // ok
        }

        // coexist with other linux nodes
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{"
                + "\"environ\":[\"A=1\"],"
                + "\"proc\":{\"state\":\"R\"},"
                + "\"auxv\":{\"hwcap64\":1},"
                + "\"cpu\":{\"affinityMaskHex\":\"01\"}"
                + "}}"
        );
        assertTrue(both.isLinuxEnvironConfigured());
        assertEquals("A=1", both.getLinuxEnviron().get(0));
        assertTrue(both.isLinuxProcConfigured());
        assertTrue(both.isLinuxAuxvConfigured());
        assertTrue(both.isLinuxCpuConfigured());

        // wrong node type
        assertInvalid("{\"linux\":{\"environ\":{}}}", "linux.environ");
        assertInvalid("{\"linux\":{\"environ\":1}}", "linux.environ");
        assertInvalid("{\"linux\":{\"environ\":\"A=1\"}}", "linux.environ");
        assertInvalid("{\"linux\":{\"environ\":null}}", "linux.environ");

        // element type / form
        assertInvalid("{\"linux\":{\"environ\":[null]}}", "linux.environ[0]");
        assertInvalid("{\"linux\":{\"environ\":[1]}}", "linux.environ[0]");
        assertInvalid("{\"linux\":{\"environ\":[true]}}", "linux.environ[0]");
        assertInvalid("{\"linux\":{\"environ\":[\"\"]}}", "linux.environ[0]");
        assertInvalid("{\"linux\":{\"environ\":[\"NOEQ\"]}}", "linux.environ[0]");
        assertInvalid("{\"linux\":{\"environ\":[\"=noval\"]}}", "linux.environ[0]");
        assertInvalid("{\"linux\":{\"environ\":[\"1BAD=1\"]}}", "linux.environ[0]");
        assertInvalid("{\"linux\":{\"environ\":[\"BAD-KEY=1\"]}}", "linux.environ[0]");
        assertInvalid("{\"linux\":{\"environ\":[\"A=1\",\"A=2\"]}}", "linux.environ[1]");
        assertInvalid("{\"linux\":{\"environ\":[\"A\\n=1\"]}}", "linux.environ[0]");
        assertInvalid("{\"linux\":{\"environ\":[\"A\\r=1\"]}}", "linux.environ[0]");
        // too long
        StringBuilder tooLong = new StringBuilder(4097);
        tooLong.append("K=");
        while (tooLong.length() < 4097) {
            tooLong.append('y');
        }
        assertInvalid("{\"linux\":{\"environ\":[\"" + tooLong + "\"]}}", "linux.environ[0]");
    }

    @Test
    public void testAndroidPowerConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidPowerConfigured());
        assertNull(missing.getAndroidPowerConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidPowerConfigured());
        assertNull(missingAndroid.getAndroidPowerConfig());

        // explicit empty → defaults interactive=true, powerSaveMode=false,
        // idle/standby/sustained/rebootingUserspace/ignoringBatteryOptimizations false
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"power\":{}}}");
        assertTrue(empty.isAndroidPowerConfigured());
        TraceEnvironmentConfig.AndroidPowerConfig e = empty.getAndroidPowerConfig();
        assertNotNull(e);
        assertFalse(e.isInteractiveConfigured());
        assertTrue(e.isInteractive());
        assertFalse(e.isPowerSaveModeConfigured());
        assertFalse(e.isPowerSaveMode());
        assertFalse(e.isDeviceIdleModeConfigured());
        assertFalse(e.isDeviceIdleMode());
        assertFalse(e.isDeviceLightIdleModeConfigured());
        assertFalse(e.isDeviceLightIdleMode());
        assertFalse(e.isLowPowerStandbyEnabledConfigured());
        assertFalse(e.isLowPowerStandbyEnabled());
        assertFalse(e.isSustainedPerformanceModeSupportedConfigured());
        assertFalse(e.isSustainedPerformanceModeSupported());
        assertFalse(e.isRebootingUserspaceSupportedConfigured());
        assertFalse(e.isRebootingUserspaceSupported());
        assertFalse(e.isIgnoringBatteryOptimizationsConfigured());
        assertFalse(e.isIgnoringBatteryOptimizations());

        // full: independent flags; rebootingUserspaceSupported=true custom
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"power\":{"
                + "\"interactive\":false,"
                + "\"powerSaveMode\":true,"
                + "\"deviceIdleMode\":false,"
                + "\"deviceLightIdleMode\":true,"
                + "\"lowPowerStandbyEnabled\":false,"
                + "\"sustainedPerformanceModeSupported\":true,"
                + "\"rebootingUserspaceSupported\":true"
                + "}}}"
        );
        assertTrue(full.isAndroidPowerConfigured());
        TraceEnvironmentConfig.AndroidPowerConfig p = full.getAndroidPowerConfig();
        assertTrue(p.isInteractiveConfigured());
        assertFalse(p.isInteractive());
        assertTrue(p.isPowerSaveModeConfigured());
        assertTrue(p.isPowerSaveMode());
        assertTrue(p.isDeviceIdleModeConfigured());
        assertFalse(p.isDeviceIdleMode());
        assertTrue(p.isDeviceLightIdleModeConfigured());
        assertTrue(p.isDeviceLightIdleMode());
        assertTrue(p.isLowPowerStandbyEnabledConfigured());
        assertFalse(p.isLowPowerStandbyEnabled());
        assertTrue(p.isSustainedPerformanceModeSupportedConfigured());
        assertTrue(p.isSustainedPerformanceModeSupported());
        assertTrue(p.isRebootingUserspaceSupportedConfigured());
        assertTrue(p.isRebootingUserspaceSupported());

        // immutable / stable access
        assertEquals(p.isInteractive(), full.getAndroidPowerConfig().isInteractive());
        assertEquals(p.isPowerSaveMode(), full.getAndroidPowerConfig().isPowerSaveMode());
        assertEquals(p.isDeviceIdleMode(), full.getAndroidPowerConfig().isDeviceIdleMode());
        assertEquals(p.isDeviceLightIdleMode(), full.getAndroidPowerConfig().isDeviceLightIdleMode());
        assertEquals(p.isLowPowerStandbyEnabled(), full.getAndroidPowerConfig().isLowPowerStandbyEnabled());
        assertEquals(p.isSustainedPerformanceModeSupported(),
                full.getAndroidPowerConfig().isSustainedPerformanceModeSupported());
        assertEquals(p.isRebootingUserspaceSupported(),
                full.getAndroidPowerConfig().isRebootingUserspaceSupported());
        assertNotNull(full.getAndroidPowerConfig());

        // partial: only interactive
        TraceEnvironmentConfig interactiveOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"power\":{\"interactive\":false}}}");
        assertTrue(interactiveOnly.isAndroidPowerConfigured());
        TraceEnvironmentConfig.AndroidPowerConfig io = interactiveOnly.getAndroidPowerConfig();
        assertTrue(io.isInteractiveConfigured());
        assertFalse(io.isInteractive());
        assertFalse(io.isPowerSaveModeConfigured());
        assertFalse(io.isPowerSaveMode());
        assertFalse(io.isDeviceIdleModeConfigured());
        assertFalse(io.isDeviceIdleMode());
        assertFalse(io.isDeviceLightIdleModeConfigured());
        assertFalse(io.isDeviceLightIdleMode());
        assertFalse(io.isLowPowerStandbyEnabledConfigured());
        assertFalse(io.isLowPowerStandbyEnabled());
        assertFalse(io.isSustainedPerformanceModeSupportedConfigured());
        assertFalse(io.isSustainedPerformanceModeSupported());
        assertFalse(io.isRebootingUserspaceSupportedConfigured());
        assertFalse(io.isRebootingUserspaceSupported());

        // partial: only powerSaveMode
        TraceEnvironmentConfig saveOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"power\":{\"powerSaveMode\":true}}}");
        assertTrue(saveOnly.isAndroidPowerConfigured());
        TraceEnvironmentConfig.AndroidPowerConfig so = saveOnly.getAndroidPowerConfig();
        assertFalse(so.isInteractiveConfigured());
        assertTrue(so.isInteractive());
        assertTrue(so.isPowerSaveModeConfigured());
        assertTrue(so.isPowerSaveMode());
        assertFalse(so.isDeviceIdleModeConfigured());
        assertFalse(so.isDeviceIdleMode());
        assertFalse(so.isDeviceLightIdleModeConfigured());
        assertFalse(so.isDeviceLightIdleMode());
        assertFalse(so.isLowPowerStandbyEnabledConfigured());
        assertFalse(so.isLowPowerStandbyEnabled());
        assertFalse(so.isSustainedPerformanceModeSupportedConfigured());
        assertFalse(so.isSustainedPerformanceModeSupported());
        assertFalse(so.isRebootingUserspaceSupportedConfigured());
        assertFalse(so.isRebootingUserspaceSupported());

        // partial: only deviceIdleMode=true
        TraceEnvironmentConfig idleOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"power\":{\"deviceIdleMode\":true}}}");
        assertTrue(idleOnly.isAndroidPowerConfigured());
        TraceEnvironmentConfig.AndroidPowerConfig idle = idleOnly.getAndroidPowerConfig();
        assertFalse(idle.isInteractiveConfigured());
        assertTrue(idle.isInteractive());
        assertFalse(idle.isPowerSaveModeConfigured());
        assertFalse(idle.isPowerSaveMode());
        assertTrue(idle.isDeviceIdleModeConfigured());
        assertTrue(idle.isDeviceIdleMode());
        assertFalse(idle.isDeviceLightIdleModeConfigured());
        assertFalse(idle.isDeviceLightIdleMode());
        assertFalse(idle.isLowPowerStandbyEnabledConfigured());
        assertFalse(idle.isLowPowerStandbyEnabled());
        assertFalse(idle.isSustainedPerformanceModeSupportedConfigured());
        assertFalse(idle.isSustainedPerformanceModeSupported());
        assertFalse(idle.isRebootingUserspaceSupportedConfigured());
        assertFalse(idle.isRebootingUserspaceSupported());

        // partial: only deviceLightIdleMode=true
        TraceEnvironmentConfig lightOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"power\":{\"deviceLightIdleMode\":true}}}");
        assertTrue(lightOnly.isAndroidPowerConfigured());
        TraceEnvironmentConfig.AndroidPowerConfig light = lightOnly.getAndroidPowerConfig();
        assertFalse(light.isInteractiveConfigured());
        assertTrue(light.isInteractive());
        assertFalse(light.isPowerSaveModeConfigured());
        assertFalse(light.isPowerSaveMode());
        assertFalse(light.isDeviceIdleModeConfigured());
        assertFalse(light.isDeviceIdleMode());
        assertTrue(light.isDeviceLightIdleModeConfigured());
        assertTrue(light.isDeviceLightIdleMode());
        assertFalse(light.isLowPowerStandbyEnabledConfigured());
        assertFalse(light.isLowPowerStandbyEnabled());
        assertFalse(light.isSustainedPerformanceModeSupportedConfigured());
        assertFalse(light.isSustainedPerformanceModeSupported());
        assertFalse(light.isRebootingUserspaceSupportedConfigured());
        assertFalse(light.isRebootingUserspaceSupported());

        // partial: only lowPowerStandbyEnabled=true (independent of idle modes)
        TraceEnvironmentConfig standbyOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"power\":{\"lowPowerStandbyEnabled\":true}}}");
        assertTrue(standbyOnly.isAndroidPowerConfigured());
        TraceEnvironmentConfig.AndroidPowerConfig standby = standbyOnly.getAndroidPowerConfig();
        assertFalse(standby.isInteractiveConfigured());
        assertTrue(standby.isInteractive());
        assertFalse(standby.isPowerSaveModeConfigured());
        assertFalse(standby.isPowerSaveMode());
        assertFalse(standby.isDeviceIdleModeConfigured());
        assertFalse(standby.isDeviceIdleMode());
        assertFalse(standby.isDeviceLightIdleModeConfigured());
        assertFalse(standby.isDeviceLightIdleMode());
        assertTrue(standby.isLowPowerStandbyEnabledConfigured());
        assertTrue(standby.isLowPowerStandbyEnabled());
        assertFalse(standby.isSustainedPerformanceModeSupportedConfigured());
        assertFalse(standby.isSustainedPerformanceModeSupported());
        assertFalse(standby.isRebootingUserspaceSupportedConfigured());
        assertFalse(standby.isRebootingUserspaceSupported());

        // partial: only sustainedPerformanceModeSupported=true (not derived from idle/standby/save)
        TraceEnvironmentConfig sustainedOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"power\":{\"sustainedPerformanceModeSupported\":true}}}");
        assertTrue(sustainedOnly.isAndroidPowerConfigured());
        TraceEnvironmentConfig.AndroidPowerConfig sustained = sustainedOnly.getAndroidPowerConfig();
        assertFalse(sustained.isInteractiveConfigured());
        assertTrue(sustained.isInteractive());
        assertFalse(sustained.isPowerSaveModeConfigured());
        assertFalse(sustained.isPowerSaveMode());
        assertFalse(sustained.isDeviceIdleModeConfigured());
        assertFalse(sustained.isDeviceIdleMode());
        assertFalse(sustained.isDeviceLightIdleModeConfigured());
        assertFalse(sustained.isDeviceLightIdleMode());
        assertFalse(sustained.isLowPowerStandbyEnabledConfigured());
        assertFalse(sustained.isLowPowerStandbyEnabled());
        assertTrue(sustained.isSustainedPerformanceModeSupportedConfigured());
        assertTrue(sustained.isSustainedPerformanceModeSupported());
        assertFalse(sustained.isRebootingUserspaceSupportedConfigured());
        assertFalse(sustained.isRebootingUserspaceSupported());

        // partial: only rebootingUserspaceSupported=true (fixed capability marker)
        TraceEnvironmentConfig rebootOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"power\":{\"rebootingUserspaceSupported\":true}}}");
        assertTrue(rebootOnly.isAndroidPowerConfigured());
        TraceEnvironmentConfig.AndroidPowerConfig reboot = rebootOnly.getAndroidPowerConfig();
        assertFalse(reboot.isInteractiveConfigured());
        assertTrue(reboot.isInteractive());
        assertFalse(reboot.isPowerSaveModeConfigured());
        assertFalse(reboot.isPowerSaveMode());
        assertFalse(reboot.isDeviceIdleModeConfigured());
        assertFalse(reboot.isDeviceIdleMode());
        assertFalse(reboot.isDeviceLightIdleModeConfigured());
        assertFalse(reboot.isDeviceLightIdleMode());
        assertFalse(reboot.isLowPowerStandbyEnabledConfigured());
        assertFalse(reboot.isLowPowerStandbyEnabled());
        assertFalse(reboot.isSustainedPerformanceModeSupportedConfigured());
        assertFalse(reboot.isSustainedPerformanceModeSupported());
        assertTrue(reboot.isRebootingUserspaceSupportedConfigured());
        assertTrue(reboot.isRebootingUserspaceSupported());

        // partial: only ignoringBatteryOptimizations true / false
        TraceEnvironmentConfig ignoringTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"power\":{\"ignoringBatteryOptimizations\":true}}}");
        assertTrue(ignoringTrue.isAndroidPowerConfigured());
        TraceEnvironmentConfig.AndroidPowerConfig ignTrue = ignoringTrue.getAndroidPowerConfig();
        assertTrue(ignTrue.isIgnoringBatteryOptimizationsConfigured());
        assertTrue(ignTrue.isIgnoringBatteryOptimizations());
        assertFalse(ignTrue.isRebootingUserspaceSupportedConfigured());
        assertFalse(ignTrue.isRebootingUserspaceSupported());

        TraceEnvironmentConfig ignoringFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"power\":{\"ignoringBatteryOptimizations\":false}}}");
        assertTrue(ignoringFalse.getAndroidPowerConfig().isIgnoringBatteryOptimizationsConfigured());
        assertFalse(ignoringFalse.getAndroidPowerConfig().isIgnoringBatteryOptimizations());

        // coexist with display / configuration / settings
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"power\":{\"interactive\":false},"
                + "\"display\":{\"widthPixels\":720},"
                + "\"configuration\":{\"orientation\":0},"
                + "\"settings\":{\"secure\":{\"android_id\":\"abc\"}}"
                + "}}"
        );
        assertTrue(both.isAndroidPowerConfigured());
        assertFalse(both.getAndroidPowerConfig().isInteractive());
        assertTrue(both.isAndroidDisplayConfigured());
        assertEquals(720, both.getAndroidDisplayConfig().getWidthPixels());
        assertTrue(both.isAndroidConfigurationConfigured());
        assertEquals(0, both.getAndroidConfigurationConfig().getOrientation());
        assertTrue(both.isAndroidSettingConfigured("secure", "android_id"));
        assertEquals("abc", both.getAndroidSettingString("secure", "android_id"));

        // wrong node type
        assertInvalid("{\"android\":{\"power\":[]}}", "android.power");
        assertInvalid("{\"android\":{\"power\":1}}", "android.power");
        assertInvalid("{\"android\":{\"power\":\"true\"}}", "android.power");

        // unknown keys
        assertInvalid("{\"android\":{\"power\":{\"batteryLevel\":50}}}",
                "android.power.batteryLevel");
        assertInvalid("{\"android\":{\"power\":{\"extra\":true}}}",
                "android.power.extra");

        // interactive wrong types
        assertInvalid("{\"android\":{\"power\":{\"interactive\":null}}}",
                "android.power.interactive");
        assertInvalid("{\"android\":{\"power\":{\"interactive\":\"true\"}}}",
                "android.power.interactive");
        assertInvalid("{\"android\":{\"power\":{\"interactive\":1}}}",
                "android.power.interactive");
        assertInvalid("{\"android\":{\"power\":{\"interactive\":0}}}",
                "android.power.interactive");

        // powerSaveMode wrong types
        assertInvalid("{\"android\":{\"power\":{\"powerSaveMode\":null}}}",
                "android.power.powerSaveMode");
        assertInvalid("{\"android\":{\"power\":{\"powerSaveMode\":\"false\"}}}",
                "android.power.powerSaveMode");
        assertInvalid("{\"android\":{\"power\":{\"powerSaveMode\":0}}}",
                "android.power.powerSaveMode");
        assertInvalid("{\"android\":{\"power\":{\"powerSaveMode\":1.0}}}",
                "android.power.powerSaveMode");

        // deviceIdleMode wrong types
        assertInvalid("{\"android\":{\"power\":{\"deviceIdleMode\":null}}}",
                "android.power.deviceIdleMode");
        assertInvalid("{\"android\":{\"power\":{\"deviceIdleMode\":\"true\"}}}",
                "android.power.deviceIdleMode");
        assertInvalid("{\"android\":{\"power\":{\"deviceIdleMode\":1}}}",
                "android.power.deviceIdleMode");
        assertInvalid("{\"android\":{\"power\":{\"deviceIdleMode\":0}}}",
                "android.power.deviceIdleMode");

        // deviceLightIdleMode wrong types
        assertInvalid("{\"android\":{\"power\":{\"deviceLightIdleMode\":null}}}",
                "android.power.deviceLightIdleMode");
        assertInvalid("{\"android\":{\"power\":{\"deviceLightIdleMode\":\"true\"}}}",
                "android.power.deviceLightIdleMode");
        assertInvalid("{\"android\":{\"power\":{\"deviceLightIdleMode\":1}}}",
                "android.power.deviceLightIdleMode");
        assertInvalid("{\"android\":{\"power\":{\"deviceLightIdleMode\":0}}}",
                "android.power.deviceLightIdleMode");

        // lowPowerStandbyEnabled wrong types
        assertInvalid("{\"android\":{\"power\":{\"lowPowerStandbyEnabled\":null}}}",
                "android.power.lowPowerStandbyEnabled");
        assertInvalid("{\"android\":{\"power\":{\"lowPowerStandbyEnabled\":\"true\"}}}",
                "android.power.lowPowerStandbyEnabled");
        assertInvalid("{\"android\":{\"power\":{\"lowPowerStandbyEnabled\":1}}}",
                "android.power.lowPowerStandbyEnabled");
        assertInvalid("{\"android\":{\"power\":{\"lowPowerStandbyEnabled\":0}}}",
                "android.power.lowPowerStandbyEnabled");

        // sustainedPerformanceModeSupported wrong types
        assertInvalid("{\"android\":{\"power\":{\"sustainedPerformanceModeSupported\":null}}}",
                "android.power.sustainedPerformanceModeSupported");
        assertInvalid("{\"android\":{\"power\":{\"sustainedPerformanceModeSupported\":\"true\"}}}",
                "android.power.sustainedPerformanceModeSupported");
        assertInvalid("{\"android\":{\"power\":{\"sustainedPerformanceModeSupported\":1}}}",
                "android.power.sustainedPerformanceModeSupported");
        assertInvalid("{\"android\":{\"power\":{\"sustainedPerformanceModeSupported\":0}}}",
                "android.power.sustainedPerformanceModeSupported");

        // rebootingUserspaceSupported wrong types
        assertInvalid("{\"android\":{\"power\":{\"rebootingUserspaceSupported\":null}}}",
                "android.power.rebootingUserspaceSupported");
        assertInvalid("{\"android\":{\"power\":{\"rebootingUserspaceSupported\":\"true\"}}}",
                "android.power.rebootingUserspaceSupported");
        assertInvalid("{\"android\":{\"power\":{\"rebootingUserspaceSupported\":1}}}",
                "android.power.rebootingUserspaceSupported");
        assertInvalid("{\"android\":{\"power\":{\"rebootingUserspaceSupported\":0}}}",
                "android.power.rebootingUserspaceSupported");

        // ignoringBatteryOptimizations wrong types
        assertInvalid("{\"android\":{\"power\":{\"ignoringBatteryOptimizations\":null}}}",
                "android.power.ignoringBatteryOptimizations");
        assertInvalid("{\"android\":{\"power\":{\"ignoringBatteryOptimizations\":\"true\"}}}",
                "android.power.ignoringBatteryOptimizations");
        assertInvalid("{\"android\":{\"power\":{\"ignoringBatteryOptimizations\":1}}}",
                "android.power.ignoringBatteryOptimizations");
    }

    @Test
    public void testAndroidThermalConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidThermalConfigured());
        assertNull(missing.getAndroidThermalConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidThermalConfigured());
        assertNull(missingAndroid.getAndroidThermalConfig());

        // explicit empty → default currentThermalStatus=0, headroom=1.0
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"thermal\":{}}}");
        assertTrue(empty.isAndroidThermalConfigured());
        TraceEnvironmentConfig.AndroidThermalConfig e = empty.getAndroidThermalConfig();
        assertNotNull(e);
        assertFalse(e.isCurrentThermalStatusConfigured());
        assertEquals(0, e.getCurrentThermalStatus());
        assertFalse(e.isHeadroomConfigured());
        assertEquals(1.0f, e.getHeadroom(), 0f);

        // full explicit values (status + custom headroom 0.75)
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse(
                "{\"android\":{\"thermal\":{\"currentThermalStatus\":3,\"headroom\":0.75}}}");
        assertTrue(full.isAndroidThermalConfigured());
        TraceEnvironmentConfig.AndroidThermalConfig t = full.getAndroidThermalConfig();
        assertTrue(t.isCurrentThermalStatusConfigured());
        assertEquals(3, t.getCurrentThermalStatus());
        assertTrue(t.isHeadroomConfigured());
        assertEquals(0.75f, t.getHeadroom(), 0f);

        // immutable / stable access
        assertEquals(t.getCurrentThermalStatus(),
                full.getAndroidThermalConfig().getCurrentThermalStatus());
        assertEquals(t.getHeadroom(), full.getAndroidThermalConfig().getHeadroom(), 0f);
        assertNotNull(full.getAndroidThermalConfig());

        // partial: only currentThermalStatus → default headroom
        TraceEnvironmentConfig statusOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"thermal\":{\"currentThermalStatus\":2}}}");
        assertTrue(statusOnly.isAndroidThermalConfigured());
        assertTrue(statusOnly.getAndroidThermalConfig().isCurrentThermalStatusConfigured());
        assertEquals(2, statusOnly.getAndroidThermalConfig().getCurrentThermalStatus());
        assertFalse(statusOnly.getAndroidThermalConfig().isHeadroomConfigured());
        assertEquals(1.0f, statusOnly.getAndroidThermalConfig().getHeadroom(), 0f);

        // partial: only headroom → default status 0
        TraceEnvironmentConfig headroomOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"thermal\":{\"headroom\":0.75}}}");
        assertTrue(headroomOnly.isAndroidThermalConfigured());
        assertFalse(headroomOnly.getAndroidThermalConfig().isCurrentThermalStatusConfigured());
        assertEquals(0, headroomOnly.getAndroidThermalConfig().getCurrentThermalStatus());
        assertTrue(headroomOnly.getAndroidThermalConfig().isHeadroomConfigured());
        assertEquals(0.75f, headroomOnly.getAndroidThermalConfig().getHeadroom(), 0f);

        // bounds 0 / 6 for status; headroom allows 0.0
        assertEquals(0, TraceEnvironmentConfig.parse(
                "{\"android\":{\"thermal\":{\"currentThermalStatus\":0}}}")
                .getAndroidThermalConfig().getCurrentThermalStatus());
        assertEquals(6, TraceEnvironmentConfig.parse(
                "{\"android\":{\"thermal\":{\"currentThermalStatus\":6}}}")
                .getAndroidThermalConfig().getCurrentThermalStatus());
        assertEquals(0f, TraceEnvironmentConfig.parse(
                "{\"android\":{\"thermal\":{\"headroom\":0}}}")
                .getAndroidThermalConfig().getHeadroom(), 0f);

        // coexist with android.power
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"thermal\":{\"currentThermalStatus\":4,\"headroom\":0.75},"
                + "\"power\":{\"interactive\":false,\"powerSaveMode\":true}"
                + "}}"
        );
        assertTrue(both.isAndroidThermalConfigured());
        assertEquals(4, both.getAndroidThermalConfig().getCurrentThermalStatus());
        assertEquals(0.75f, both.getAndroidThermalConfig().getHeadroom(), 0f);
        assertTrue(both.isAndroidPowerConfigured());
        assertFalse(both.getAndroidPowerConfig().isInteractive());
        assertTrue(both.getAndroidPowerConfig().isPowerSaveMode());

        // wrong node type
        assertInvalid("{\"android\":{\"thermal\":[]}}", "android.thermal");
        assertInvalid("{\"android\":{\"thermal\":1}}", "android.thermal");
        assertInvalid("{\"android\":{\"thermal\":\"none\"}}", "android.thermal");

        // unknown keys
        assertInvalid("{\"android\":{\"thermal\":{\"extra\":1}}}",
                "android.thermal.extra");

        // wrong types / range / overflow for currentThermalStatus
        assertInvalid("{\"android\":{\"thermal\":{\"currentThermalStatus\":null}}}",
                "android.thermal.currentThermalStatus");
        assertInvalid("{\"android\":{\"thermal\":{\"currentThermalStatus\":\"0\"}}}",
                "android.thermal.currentThermalStatus");
        assertInvalid("{\"android\":{\"thermal\":{\"currentThermalStatus\":true}}}",
                "android.thermal.currentThermalStatus");
        assertInvalid("{\"android\":{\"thermal\":{\"currentThermalStatus\":1.5}}}",
                "android.thermal.currentThermalStatus");
        assertInvalid("{\"android\":{\"thermal\":{\"currentThermalStatus\":-1}}}",
                "android.thermal.currentThermalStatus");
        assertInvalid("{\"android\":{\"thermal\":{\"currentThermalStatus\":7}}}",
                "android.thermal.currentThermalStatus");
        assertInvalid("{\"android\":{\"thermal\":{\"currentThermalStatus\":99999999999999999999999999999}}}",
                "android.thermal.currentThermalStatus");

        // headroom wrong types / NaN / Infinity / negative
        assertInvalid("{\"android\":{\"thermal\":{\"headroom\":null}}}",
                "android.thermal.headroom");
        assertInvalid("{\"android\":{\"thermal\":{\"headroom\":\"0.75\"}}}",
                "android.thermal.headroom");
        assertInvalid("{\"android\":{\"thermal\":{\"headroom\":true}}}",
                "android.thermal.headroom");
        assertInvalid("{\"android\":{\"thermal\":{\"headroom\":-0.1}}}",
                "android.thermal.headroom");
        // 1e309 → Double/Float Infinity via Number.floatValue()
        assertInvalid("{\"android\":{\"thermal\":{\"headroom\":1e309}}}",
                "android.thermal.headroom");
        // non-standard token; accepted by project JSON parser as Number.NaN when present
        assertInvalid("{\"android\":{\"thermal\":{\"headroom\":NaN}}}",
                "android.thermal.headroom");
    }

    @Test
    public void testAndroidBatteryConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidBatteryConfigured());
        assertNull(missing.getAndroidBatteryConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidBatteryConfigured());
        assertNull(missingAndroid.getAndroidBatteryConfig());

        // explicit empty → default capacityPercent=73, charging=false;
        // chargeCounter / currentNow / currentAverage / energy / status unconfigured
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{}}}");
        assertTrue(empty.isAndroidBatteryConfigured());
        TraceEnvironmentConfig.AndroidBatteryConfig e = empty.getAndroidBatteryConfig();
        assertNotNull(e);
        assertFalse(e.isCapacityPercentConfigured());
        assertEquals(73, e.getCapacityPercent());
        assertFalse(e.isChargingConfigured());
        assertFalse(e.isCharging());
        assertFalse(e.isChargeCounterUahConfigured());
        assertFalse(e.isCurrentNowUaConfigured());
        assertFalse(e.isCurrentAverageUaConfigured());
        assertFalse(e.isEnergyCounterNwhConfigured());
        assertFalse(e.isStatusConfigured());
        assertFalse(e.isChargeTimeRemainingMillisConfigured());

        // full explicit value
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"capacityPercent\":42}}}");
        assertTrue(full.isAndroidBatteryConfigured());
        TraceEnvironmentConfig.AndroidBatteryConfig b = full.getAndroidBatteryConfig();
        assertTrue(b.isCapacityPercentConfigured());
        assertEquals(42, b.getCapacityPercent());
        assertFalse(b.isChargeCounterUahConfigured());
        assertFalse(b.isCurrentNowUaConfigured());
        assertFalse(b.isCurrentAverageUaConfigured());
        assertFalse(b.isEnergyCounterNwhConfigured());
        assertFalse(b.isStatusConfigured());
        assertFalse(b.isChargeTimeRemainingMillisConfigured());

        // immutable / stable access
        assertEquals(b.getCapacityPercent(), full.getAndroidBatteryConfig().getCapacityPercent());
        assertNotNull(full.getAndroidBatteryConfig());

        // bounds 0 / 100
        assertEquals(0, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"capacityPercent\":0}}}")
                .getAndroidBatteryConfig().getCapacityPercent());
        assertEquals(100, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"capacityPercent\":100}}}")
                .getAndroidBatteryConfig().getCapacityPercent());

        // partial: only charging true / false
        TraceEnvironmentConfig chargingTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"charging\":true}}}");
        assertTrue(chargingTrue.isAndroidBatteryConfigured());
        assertTrue(chargingTrue.getAndroidBatteryConfig().isChargingConfigured());
        assertTrue(chargingTrue.getAndroidBatteryConfig().isCharging());
        assertFalse(chargingTrue.getAndroidBatteryConfig().isCapacityPercentConfigured());
        assertEquals(73, chargingTrue.getAndroidBatteryConfig().getCapacityPercent());
        assertFalse(chargingTrue.getAndroidBatteryConfig().isChargeCounterUahConfigured());
        assertFalse(chargingTrue.getAndroidBatteryConfig().isCurrentNowUaConfigured());
        assertFalse(chargingTrue.getAndroidBatteryConfig().isCurrentAverageUaConfigured());
        assertFalse(chargingTrue.getAndroidBatteryConfig().isEnergyCounterNwhConfigured());

        TraceEnvironmentConfig chargingFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"charging\":false}}}");
        assertTrue(chargingFalse.getAndroidBatteryConfig().isChargingConfigured());
        assertFalse(chargingFalse.getAndroidBatteryConfig().isCharging());

        // chargeCounterUah independent; no default / not inferred from capacity or charging
        TraceEnvironmentConfig counterOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"chargeCounterUah\":2500000}}}");
        assertTrue(counterOnly.getAndroidBatteryConfig().isChargeCounterUahConfigured());
        assertEquals(2500000, counterOnly.getAndroidBatteryConfig().getChargeCounterUah());
        assertFalse(counterOnly.getAndroidBatteryConfig().isCapacityPercentConfigured());
        assertEquals(73, counterOnly.getAndroidBatteryConfig().getCapacityPercent());
        assertFalse(counterOnly.getAndroidBatteryConfig().isChargingConfigured());
        assertFalse(counterOnly.getAndroidBatteryConfig().isCharging());
        assertFalse(counterOnly.getAndroidBatteryConfig().isCurrentNowUaConfigured());
        assertFalse(counterOnly.getAndroidBatteryConfig().isCurrentAverageUaConfigured());
        assertFalse(counterOnly.getAndroidBatteryConfig().isEnergyCounterNwhConfigured());
        assertEquals(0, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"chargeCounterUah\":0}}}")
                .getAndroidBatteryConfig().getChargeCounterUah());
        assertEquals(2147483647, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"chargeCounterUah\":2147483647}}}")
                .getAndroidBatteryConfig().getChargeCounterUah());
        TraceEnvironmentConfig allThree = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"capacityPercent\":42,\"charging\":true,"
                        + "\"chargeCounterUah\":1000}}}");
        assertEquals(42, allThree.getAndroidBatteryConfig().getCapacityPercent());
        assertTrue(allThree.getAndroidBatteryConfig().isCharging());
        assertEquals(1000, allThree.getAndroidBatteryConfig().getChargeCounterUah());
        assertFalse(allThree.getAndroidBatteryConfig().isCurrentNowUaConfigured());
        assertFalse(allThree.getAndroidBatteryConfig().isCurrentAverageUaConfigured());

        // currentNowUa independent; signed range; no default / not inferred
        TraceEnvironmentConfig currentOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"currentNowUa\":-450000}}}");
        assertTrue(currentOnly.getAndroidBatteryConfig().isCurrentNowUaConfigured());
        assertEquals(-450000, currentOnly.getAndroidBatteryConfig().getCurrentNowUa());
        assertFalse(currentOnly.getAndroidBatteryConfig().isCapacityPercentConfigured());
        assertEquals(73, currentOnly.getAndroidBatteryConfig().getCapacityPercent());
        assertFalse(currentOnly.getAndroidBatteryConfig().isChargingConfigured());
        assertFalse(currentOnly.getAndroidBatteryConfig().isCharging());
        assertFalse(currentOnly.getAndroidBatteryConfig().isChargeCounterUahConfigured());
        assertFalse(currentOnly.getAndroidBatteryConfig().isCurrentAverageUaConfigured());
        assertEquals(0, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"currentNowUa\":0}}}")
                .getAndroidBatteryConfig().getCurrentNowUa());
        assertEquals(2147483647, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"currentNowUa\":2147483647}}}")
                .getAndroidBatteryConfig().getCurrentNowUa());
        assertEquals(-2147483648, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"currentNowUa\":-2147483648}}}")
                .getAndroidBatteryConfig().getCurrentNowUa());
        assertEquals(350000, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"currentNowUa\":350000}}}")
                .getAndroidBatteryConfig().getCurrentNowUa());
        TraceEnvironmentConfig allFour = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"capacityPercent\":42,\"charging\":true,"
                        + "\"chargeCounterUah\":1000,\"currentNowUa\":-1200}}}");
        assertEquals(42, allFour.getAndroidBatteryConfig().getCapacityPercent());
        assertTrue(allFour.getAndroidBatteryConfig().isCharging());
        assertEquals(1000, allFour.getAndroidBatteryConfig().getChargeCounterUah());
        assertEquals(-1200, allFour.getAndroidBatteryConfig().getCurrentNowUa());
        assertFalse(allFour.getAndroidBatteryConfig().isCurrentAverageUaConfigured());

        // currentAverageUa independent; signed range; no default / not inferred from currentNow
        TraceEnvironmentConfig avgOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"currentAverageUa\":-320000}}}");
        assertTrue(avgOnly.getAndroidBatteryConfig().isCurrentAverageUaConfigured());
        assertEquals(-320000, avgOnly.getAndroidBatteryConfig().getCurrentAverageUa());
        assertFalse(avgOnly.getAndroidBatteryConfig().isCapacityPercentConfigured());
        assertEquals(73, avgOnly.getAndroidBatteryConfig().getCapacityPercent());
        assertFalse(avgOnly.getAndroidBatteryConfig().isChargingConfigured());
        assertFalse(avgOnly.getAndroidBatteryConfig().isCharging());
        assertFalse(avgOnly.getAndroidBatteryConfig().isChargeCounterUahConfigured());
        assertFalse(avgOnly.getAndroidBatteryConfig().isCurrentNowUaConfigured());
        assertEquals(0, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"currentAverageUa\":0}}}")
                .getAndroidBatteryConfig().getCurrentAverageUa());
        assertEquals(2147483647, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"currentAverageUa\":2147483647}}}")
                .getAndroidBatteryConfig().getCurrentAverageUa());
        assertEquals(-2147483648, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"currentAverageUa\":-2147483648}}}")
                .getAndroidBatteryConfig().getCurrentAverageUa());
        assertEquals(280000, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"currentAverageUa\":280000}}}")
                .getAndroidBatteryConfig().getCurrentAverageUa());
        // currentNow and currentAverage are independent (not inferred from each other)
        TraceEnvironmentConfig nowAndAvg = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"currentNowUa\":-450000,\"currentAverageUa\":-320000}}}");
        assertEquals(-450000, nowAndAvg.getAndroidBatteryConfig().getCurrentNowUa());
        assertEquals(-320000, nowAndAvg.getAndroidBatteryConfig().getCurrentAverageUa());
        TraceEnvironmentConfig allFive = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"capacityPercent\":42,\"charging\":true,"
                        + "\"chargeCounterUah\":1000,\"currentNowUa\":-1200,"
                        + "\"currentAverageUa\":-800}}}");
        assertEquals(42, allFive.getAndroidBatteryConfig().getCapacityPercent());
        assertTrue(allFive.getAndroidBatteryConfig().isCharging());
        assertEquals(1000, allFive.getAndroidBatteryConfig().getChargeCounterUah());
        assertEquals(-1200, allFive.getAndroidBatteryConfig().getCurrentNowUa());
        assertEquals(-800, allFive.getAndroidBatteryConfig().getCurrentAverageUa());
        assertFalse(allFive.getAndroidBatteryConfig().isEnergyCounterNwhConfigured());

        // energyCounterNwh independent; non-negative long; no default / not inferred
        TraceEnvironmentConfig energyOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"energyCounterNwh\":55000000000}}}");
        assertTrue(energyOnly.getAndroidBatteryConfig().isEnergyCounterNwhConfigured());
        assertEquals(55000000000L, energyOnly.getAndroidBatteryConfig().getEnergyCounterNwh());
        assertFalse(energyOnly.getAndroidBatteryConfig().isCapacityPercentConfigured());
        assertEquals(73, energyOnly.getAndroidBatteryConfig().getCapacityPercent());
        assertFalse(energyOnly.getAndroidBatteryConfig().isChargingConfigured());
        assertFalse(energyOnly.getAndroidBatteryConfig().isChargeCounterUahConfigured());
        assertFalse(energyOnly.getAndroidBatteryConfig().isCurrentNowUaConfigured());
        assertFalse(energyOnly.getAndroidBatteryConfig().isCurrentAverageUaConfigured());
        assertEquals(0L, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"energyCounterNwh\":0}}}")
                .getAndroidBatteryConfig().getEnergyCounterNwh());
        assertEquals(9223372036854775807L, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"energyCounterNwh\":9223372036854775807}}}")
                .getAndroidBatteryConfig().getEnergyCounterNwh());
        TraceEnvironmentConfig allSix = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"capacityPercent\":42,\"charging\":true,"
                        + "\"chargeCounterUah\":1000,\"currentNowUa\":-1200,"
                        + "\"currentAverageUa\":-800,\"energyCounterNwh\":55000000000}}}");
        assertEquals(42, allSix.getAndroidBatteryConfig().getCapacityPercent());
        assertTrue(allSix.getAndroidBatteryConfig().isCharging());
        assertEquals(1000, allSix.getAndroidBatteryConfig().getChargeCounterUah());
        assertEquals(-1200, allSix.getAndroidBatteryConfig().getCurrentNowUa());
        assertEquals(-800, allSix.getAndroidBatteryConfig().getCurrentAverageUa());
        assertEquals(55000000000L, allSix.getAndroidBatteryConfig().getEnergyCounterNwh());
        assertFalse(allSix.getAndroidBatteryConfig().isStatusConfigured());

        // status independent; exact 1..5; no default / not inferred from charging
        TraceEnvironmentConfig statusOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"status\":3}}}");
        assertTrue(statusOnly.getAndroidBatteryConfig().isStatusConfigured());
        assertEquals(3, statusOnly.getAndroidBatteryConfig().getStatus());
        assertFalse(statusOnly.getAndroidBatteryConfig().isChargingConfigured());
        assertFalse(statusOnly.getAndroidBatteryConfig().isCharging());
        assertFalse(statusOnly.getAndroidBatteryConfig().isCapacityPercentConfigured());
        assertFalse(statusOnly.getAndroidBatteryConfig().isChargeCounterUahConfigured());
        assertFalse(statusOnly.getAndroidBatteryConfig().isCurrentNowUaConfigured());
        assertFalse(statusOnly.getAndroidBatteryConfig().isCurrentAverageUaConfigured());
        assertFalse(statusOnly.getAndroidBatteryConfig().isEnergyCounterNwhConfigured());
        // valid bounds 1..5 (UNKNOWN..FULL)
        assertEquals(1, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"status\":1}}}")
                .getAndroidBatteryConfig().getStatus());
        assertEquals(2, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"status\":2}}}")
                .getAndroidBatteryConfig().getStatus());
        assertEquals(5, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"status\":5}}}")
                .getAndroidBatteryConfig().getStatus());
        // status independent of charging boolean
        TraceEnvironmentConfig chargingButStatusDischarging = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"charging\":true,\"status\":3}}}");
        assertTrue(chargingButStatusDischarging.getAndroidBatteryConfig().isCharging());
        assertEquals(3, chargingButStatusDischarging.getAndroidBatteryConfig().getStatus());
        TraceEnvironmentConfig allSeven = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"capacityPercent\":42,\"charging\":true,"
                        + "\"chargeCounterUah\":1000,\"currentNowUa\":-1200,"
                        + "\"currentAverageUa\":-800,\"energyCounterNwh\":55000000000,"
                        + "\"status\":2}}}");
        assertEquals(42, allSeven.getAndroidBatteryConfig().getCapacityPercent());
        assertTrue(allSeven.getAndroidBatteryConfig().isCharging());
        assertEquals(1000, allSeven.getAndroidBatteryConfig().getChargeCounterUah());
        assertEquals(-1200, allSeven.getAndroidBatteryConfig().getCurrentNowUa());
        assertEquals(-800, allSeven.getAndroidBatteryConfig().getCurrentAverageUa());
        assertEquals(55000000000L, allSeven.getAndroidBatteryConfig().getEnergyCounterNwh());
        assertEquals(2, allSeven.getAndroidBatteryConfig().getStatus());
        assertFalse(allSeven.getAndroidBatteryConfig().isChargeTimeRemainingMillisConfigured());

        // chargeTimeRemainingMillis independent; -1 marker or nonnegative; no default / not inferred
        TraceEnvironmentConfig chargeTimeOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"chargeTimeRemainingMillis\":3600000}}}");
        assertTrue(chargeTimeOnly.getAndroidBatteryConfig().isChargeTimeRemainingMillisConfigured());
        assertEquals(3600000L, chargeTimeOnly.getAndroidBatteryConfig().getChargeTimeRemainingMillis());
        assertFalse(chargeTimeOnly.getAndroidBatteryConfig().isCapacityPercentConfigured());
        assertEquals(73, chargeTimeOnly.getAndroidBatteryConfig().getCapacityPercent());
        assertFalse(chargeTimeOnly.getAndroidBatteryConfig().isChargingConfigured());
        assertFalse(chargeTimeOnly.getAndroidBatteryConfig().isCharging());
        assertFalse(chargeTimeOnly.getAndroidBatteryConfig().isChargeCounterUahConfigured());
        assertFalse(chargeTimeOnly.getAndroidBatteryConfig().isCurrentNowUaConfigured());
        assertFalse(chargeTimeOnly.getAndroidBatteryConfig().isCurrentAverageUaConfigured());
        assertFalse(chargeTimeOnly.getAndroidBatteryConfig().isEnergyCounterNwhConfigured());
        assertFalse(chargeTimeOnly.getAndroidBatteryConfig().isStatusConfigured());
        // -1 unable-to-compute marker
        assertEquals(-1L, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"chargeTimeRemainingMillis\":-1}}}")
                .getAndroidBatteryConfig().getChargeTimeRemainingMillis());
        assertEquals(0L, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"chargeTimeRemainingMillis\":0}}}")
                .getAndroidBatteryConfig().getChargeTimeRemainingMillis());
        assertEquals(9223372036854775807L, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"chargeTimeRemainingMillis\":9223372036854775807}}}")
                .getAndroidBatteryConfig().getChargeTimeRemainingMillis());
        // independent of charging/status (not inferred)
        TraceEnvironmentConfig chargeTimeIndep = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"charging\":false,\"status\":3,"
                        + "\"chargeTimeRemainingMillis\":7200000}}}");
        assertFalse(chargeTimeIndep.getAndroidBatteryConfig().isCharging());
        assertEquals(3, chargeTimeIndep.getAndroidBatteryConfig().getStatus());
        assertEquals(7200000L, chargeTimeIndep.getAndroidBatteryConfig().getChargeTimeRemainingMillis());
        TraceEnvironmentConfig allEight = TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"capacityPercent\":42,\"charging\":true,"
                        + "\"chargeCounterUah\":1000,\"currentNowUa\":-1200,"
                        + "\"currentAverageUa\":-800,\"energyCounterNwh\":55000000000,"
                        + "\"status\":2,\"chargeTimeRemainingMillis\":1800000}}}");
        assertEquals(42, allEight.getAndroidBatteryConfig().getCapacityPercent());
        assertTrue(allEight.getAndroidBatteryConfig().isCharging());
        assertEquals(1000, allEight.getAndroidBatteryConfig().getChargeCounterUah());
        assertEquals(-1200, allEight.getAndroidBatteryConfig().getCurrentNowUa());
        assertEquals(-800, allEight.getAndroidBatteryConfig().getCurrentAverageUa());
        assertEquals(55000000000L, allEight.getAndroidBatteryConfig().getEnergyCounterNwh());
        assertEquals(2, allEight.getAndroidBatteryConfig().getStatus());
        assertEquals(1800000L, allEight.getAndroidBatteryConfig().getChargeTimeRemainingMillis());

        // coexist with android.power / android.thermal
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"battery\":{\"capacityPercent\":88},"
                + "\"power\":{\"interactive\":false},"
                + "\"thermal\":{\"currentThermalStatus\":2}"
                + "}}"
        );
        assertTrue(both.isAndroidBatteryConfigured());
        assertEquals(88, both.getAndroidBatteryConfig().getCapacityPercent());
        assertTrue(both.isAndroidPowerConfigured());
        assertFalse(both.getAndroidPowerConfig().isInteractive());
        assertTrue(both.isAndroidThermalConfigured());
        assertEquals(2, both.getAndroidThermalConfig().getCurrentThermalStatus());

        // wrong node type
        assertInvalid("{\"android\":{\"battery\":[]}}", "android.battery");
        assertInvalid("{\"android\":{\"battery\":1}}", "android.battery");
        assertInvalid("{\"android\":{\"battery\":\"full\"}}", "android.battery");

        // unknown keys
        assertInvalid("{\"android\":{\"battery\":{\"extra\":1}}}",
                "android.battery.extra");
        assertEquals(2, TraceEnvironmentConfig.parse(
                "{\"android\":{\"battery\":{\"health\":2}}}")
                .getAndroidBatteryConfig().getHealth());
        assertInvalid("{\"android\":{\"battery\":{\"health\":0}}}",
                "android.battery.health");

        // wrong types / range / overflow
        assertInvalid("{\"android\":{\"battery\":{\"capacityPercent\":null}}}",
                "android.battery.capacityPercent");
        assertInvalid("{\"android\":{\"battery\":{\"capacityPercent\":\"73\"}}}",
                "android.battery.capacityPercent");
        assertInvalid("{\"android\":{\"battery\":{\"capacityPercent\":true}}}",
                "android.battery.capacityPercent");
        assertInvalid("{\"android\":{\"battery\":{\"capacityPercent\":50.5}}}",
                "android.battery.capacityPercent");
        assertInvalid("{\"android\":{\"battery\":{\"capacityPercent\":-1}}}",
                "android.battery.capacityPercent");
        assertInvalid("{\"android\":{\"battery\":{\"capacityPercent\":101}}}",
                "android.battery.capacityPercent");
        assertInvalid("{\"android\":{\"battery\":{\"capacityPercent\":99999999999999999999999999999}}}",
                "android.battery.capacityPercent");

        // charging wrong types
        assertInvalid("{\"android\":{\"battery\":{\"charging\":null}}}",
                "android.battery.charging");
        assertInvalid("{\"android\":{\"battery\":{\"charging\":\"true\"}}}",
                "android.battery.charging");
        assertInvalid("{\"android\":{\"battery\":{\"charging\":1}}}",
                "android.battery.charging");

        // chargeCounterUah wrong types / range
        assertInvalid("{\"android\":{\"battery\":{\"chargeCounterUah\":null}}}",
                "android.battery.chargeCounterUah");
        assertInvalid("{\"android\":{\"battery\":{\"chargeCounterUah\":\"1\"}}}",
                "android.battery.chargeCounterUah");
        assertInvalid("{\"android\":{\"battery\":{\"chargeCounterUah\":true}}}",
                "android.battery.chargeCounterUah");
        assertInvalid("{\"android\":{\"battery\":{\"chargeCounterUah\":1.5}}}",
                "android.battery.chargeCounterUah");
        assertInvalid("{\"android\":{\"battery\":{\"chargeCounterUah\":-1}}}",
                "android.battery.chargeCounterUah");
        assertInvalid("{\"android\":{\"battery\":{\"chargeCounterUah\":2147483648}}}",
                "android.battery.chargeCounterUah");

        // currentNowUa wrong types / range (signed full int; reject out-of-range / non-int)
        assertInvalid("{\"android\":{\"battery\":{\"currentNowUa\":null}}}",
                "android.battery.currentNowUa");
        assertInvalid("{\"android\":{\"battery\":{\"currentNowUa\":\"1\"}}}",
                "android.battery.currentNowUa");
        assertInvalid("{\"android\":{\"battery\":{\"currentNowUa\":true}}}",
                "android.battery.currentNowUa");
        assertInvalid("{\"android\":{\"battery\":{\"currentNowUa\":1.5}}}",
                "android.battery.currentNowUa");
        assertInvalid("{\"android\":{\"battery\":{\"currentNowUa\":2147483648}}}",
                "android.battery.currentNowUa");
        assertInvalid("{\"android\":{\"battery\":{\"currentNowUa\":-2147483649}}}",
                "android.battery.currentNowUa");

        // currentAverageUa wrong types / range (signed full int; reject out-of-range / non-int)
        assertInvalid("{\"android\":{\"battery\":{\"currentAverageUa\":null}}}",
                "android.battery.currentAverageUa");
        assertInvalid("{\"android\":{\"battery\":{\"currentAverageUa\":\"1\"}}}",
                "android.battery.currentAverageUa");
        assertInvalid("{\"android\":{\"battery\":{\"currentAverageUa\":true}}}",
                "android.battery.currentAverageUa");
        assertInvalid("{\"android\":{\"battery\":{\"currentAverageUa\":1.5}}}",
                "android.battery.currentAverageUa");
        assertInvalid("{\"android\":{\"battery\":{\"currentAverageUa\":2147483648}}}",
                "android.battery.currentAverageUa");
        assertInvalid("{\"android\":{\"battery\":{\"currentAverageUa\":-2147483649}}}",
                "android.battery.currentAverageUa");

        // energyCounterNwh wrong types / range (non-negative long 0..Long.MAX_VALUE)
        assertInvalid("{\"android\":{\"battery\":{\"energyCounterNwh\":null}}}",
                "android.battery.energyCounterNwh");
        assertInvalid("{\"android\":{\"battery\":{\"energyCounterNwh\":\"1\"}}}",
                "android.battery.energyCounterNwh");
        assertInvalid("{\"android\":{\"battery\":{\"energyCounterNwh\":true}}}",
                "android.battery.energyCounterNwh");
        assertInvalid("{\"android\":{\"battery\":{\"energyCounterNwh\":1.5}}}",
                "android.battery.energyCounterNwh");
        assertInvalid("{\"android\":{\"battery\":{\"energyCounterNwh\":-1}}}",
                "android.battery.energyCounterNwh");
        assertInvalid("{\"android\":{\"battery\":{\"energyCounterNwh\":9223372036854775808}}}",
                "android.battery.energyCounterNwh");

        // status wrong types / range (exact int 1..5 only)
        assertInvalid("{\"android\":{\"battery\":{\"status\":null}}}",
                "android.battery.status");
        assertInvalid("{\"android\":{\"battery\":{\"status\":\"2\"}}}",
                "android.battery.status");
        assertInvalid("{\"android\":{\"battery\":{\"status\":true}}}",
                "android.battery.status");
        assertInvalid("{\"android\":{\"battery\":{\"status\":2.5}}}",
                "android.battery.status");
        assertInvalid("{\"android\":{\"battery\":{\"status\":0}}}",
                "android.battery.status");
        assertInvalid("{\"android\":{\"battery\":{\"status\":6}}}",
                "android.battery.status");
        assertInvalid("{\"android\":{\"battery\":{\"status\":-1}}}",
                "android.battery.status");

        // chargeTimeRemainingMillis: -1 or nonnegative long; reject below -1 / non-int
        assertInvalid("{\"android\":{\"battery\":{\"chargeTimeRemainingMillis\":null}}}",
                "android.battery.chargeTimeRemainingMillis");
        assertInvalid("{\"android\":{\"battery\":{\"chargeTimeRemainingMillis\":\"1\"}}}",
                "android.battery.chargeTimeRemainingMillis");
        assertInvalid("{\"android\":{\"battery\":{\"chargeTimeRemainingMillis\":true}}}",
                "android.battery.chargeTimeRemainingMillis");
        assertInvalid("{\"android\":{\"battery\":{\"chargeTimeRemainingMillis\":1.5}}}",
                "android.battery.chargeTimeRemainingMillis");
        assertInvalid("{\"android\":{\"battery\":{\"chargeTimeRemainingMillis\":-2}}}",
                "android.battery.chargeTimeRemainingMillis");
        assertInvalid("{\"android\":{\"battery\":{\"chargeTimeRemainingMillis\":9223372036854775808}}}",
                "android.battery.chargeTimeRemainingMillis");
    }

    @Test
    public void testAndroidCamerasConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidCamerasConfigured());
        assertNull(missing.getAndroidCamerasConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidCamerasConfigured());
        assertNull(missingAndroid.getAndroidCamerasConfig());

        // explicit empty → default count=0, infos unconfigured
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"cameras\":{}}}");
        assertTrue(empty.isAndroidCamerasConfigured());
        TraceEnvironmentConfig.AndroidCamerasConfig e = empty.getAndroidCamerasConfig();
        assertNotNull(e);
        assertFalse(e.isCountConfigured());
        assertEquals(0, e.getCount());
        assertFalse(e.isInfosConfigured());
        assertNotNull(e.getInfos());
        assertTrue(e.getInfos().isEmpty());

        // explicit zero / nonzero count without infos
        assertEquals(0, TraceEnvironmentConfig.parse(
                "{\"android\":{\"cameras\":{\"count\":0}}}")
                .getAndroidCamerasConfig().getCount());
        TraceEnvironmentConfig two = TraceEnvironmentConfig.parse(
                "{\"android\":{\"cameras\":{\"count\":2}}}");
        assertTrue(two.isAndroidCamerasConfigured());
        assertTrue(two.getAndroidCamerasConfig().isCountConfigured());
        assertEquals(2, two.getAndroidCamerasConfig().getCount());
        assertFalse(two.getAndroidCamerasConfig().isInfosConfigured());
        assertTrue(two.getAndroidCamerasConfig().getInfos().isEmpty());
        assertEquals(16, TraceEnvironmentConfig.parse(
                "{\"android\":{\"cameras\":{\"count\":16}}}")
                .getAndroidCamerasConfig().getCount());

        // infos empty array requires count=0
        TraceEnvironmentConfig infosEmpty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"cameras\":{\"count\":0,\"infos\":[]}}}");
        assertTrue(infosEmpty.getAndroidCamerasConfig().isInfosConfigured());
        assertEquals(0, infosEmpty.getAndroidCamerasConfig().getCount());
        assertTrue(infosEmpty.getAndroidCamerasConfig().getInfos().isEmpty());

        // infos with two entries matching count
        TraceEnvironmentConfig withInfos = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"cameras\":{"
                + "\"count\":2,"
                + "\"infos\":["
                + "{\"facing\":0,\"orientation\":90},"
                + "{\"facing\":1,\"orientation\":270}"
                + "]"
                + "}}}"
        );
        assertTrue(withInfos.getAndroidCamerasConfig().isInfosConfigured());
        assertEquals(2, withInfos.getAndroidCamerasConfig().getCount());
        assertEquals(2, withInfos.getAndroidCamerasConfig().getInfos().size());
        TraceEnvironmentConfig.AndroidCameraInfoConfig i0 =
                withInfos.getAndroidCamerasConfig().getInfos().get(0);
        TraceEnvironmentConfig.AndroidCameraInfoConfig i1 =
                withInfos.getAndroidCamerasConfig().getInfos().get(1);
        assertEquals(0, i0.getFacing());
        assertEquals(90, i0.getOrientation());
        assertFalse(i0.isCanDisableShutterSoundConfigured());
        assertEquals(1, i1.getFacing());
        assertEquals(270, i1.getOrientation());
        assertFalse(i1.isCanDisableShutterSoundConfigured());

        // optional canDisableShutterSound: true / false / absent (never defaulted)
        TraceEnvironmentConfig shutter = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"cameras\":{"
                + "\"count\":3,"
                + "\"infos\":["
                + "{\"facing\":0,\"orientation\":90,\"canDisableShutterSound\":true},"
                + "{\"facing\":1,\"orientation\":270,\"canDisableShutterSound\":false},"
                + "{\"facing\":2,\"orientation\":0}"
                + "]"
                + "}}}"
        );
        TraceEnvironmentConfig.AndroidCameraInfoConfig s0 =
                shutter.getAndroidCamerasConfig().getInfos().get(0);
        TraceEnvironmentConfig.AndroidCameraInfoConfig s1 =
                shutter.getAndroidCamerasConfig().getInfos().get(1);
        TraceEnvironmentConfig.AndroidCameraInfoConfig s2 =
                shutter.getAndroidCamerasConfig().getInfos().get(2);
        assertTrue(s0.isCanDisableShutterSoundConfigured());
        assertTrue(s0.getCanDisableShutterSound());
        assertEquals(0, s0.getFacing());
        assertTrue(s1.isCanDisableShutterSoundConfigured());
        assertFalse(s1.getCanDisableShutterSound());
        assertFalse(s2.isCanDisableShutterSoundConfigured());

        // facing=2 and orientation=0/180 allowed
        TraceEnvironmentConfig extremes = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"cameras\":{"
                + "\"count\":2,"
                + "\"infos\":["
                + "{\"facing\":2,\"orientation\":0},"
                + "{\"facing\":0,\"orientation\":180}"
                + "]"
                + "}}}"
        );
        assertEquals(2, extremes.getAndroidCamerasConfig().getInfos().get(0).getFacing());
        assertEquals(0, extremes.getAndroidCamerasConfig().getInfos().get(0).getOrientation());
        assertEquals(180, extremes.getAndroidCamerasConfig().getInfos().get(1).getOrientation());

        // immutable list
        try {
            withInfos.getAndroidCamerasConfig().getInfos().add(
                    withInfos.getAndroidCamerasConfig().getInfos().get(0));
            fail("infos list should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // ok
        }

        // immutable / stable
        assertEquals(2, two.getAndroidCamerasConfig().getCount());
        assertNotNull(two.getAndroidCamerasConfig());
        assertEquals(0, withInfos.getAndroidCamerasConfig().getInfos().get(0).getFacing());

        // coexist with battery
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"cameras\":{\"count\":1,\"infos\":[{\"facing\":1,\"orientation\":90}]},"
                + "\"battery\":{\"capacityPercent\":88}"
                + "}}"
        );
        assertTrue(both.isAndroidCamerasConfigured());
        assertEquals(1, both.getAndroidCamerasConfig().getCount());
        assertTrue(both.getAndroidCamerasConfig().isInfosConfigured());
        assertEquals(1, both.getAndroidCamerasConfig().getInfos().get(0).getFacing());
        assertTrue(both.isAndroidBatteryConfigured());
        assertEquals(88, both.getAndroidBatteryConfig().getCapacityPercent());

        // wrong node type
        assertInvalid("{\"android\":{\"cameras\":[]}}", "android.cameras");
        assertInvalid("{\"android\":{\"cameras\":1}}", "android.cameras");
        assertInvalid("{\"android\":{\"cameras\":\"two\"}}", "android.cameras");

        // unknown keys
        assertInvalid("{\"android\":{\"cameras\":{\"ids\":[]}}}",
                "android.cameras.ids");
        assertInvalid("{\"android\":{\"cameras\":{\"extra\":1}}}",
                "android.cameras.extra");

        // count wrong types / range
        assertInvalid("{\"android\":{\"cameras\":{\"count\":null}}}",
                "android.cameras.count");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":\"2\"}}}",
                "android.cameras.count");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":true}}}",
                "android.cameras.count");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1.5}}}",
                "android.cameras.count");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":-1}}}",
                "android.cameras.count");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":17}}}",
                "android.cameras.count");

        // infos requires count
        assertInvalid("{\"android\":{\"cameras\":{\"infos\":[]}}}",
                "android.cameras.count");
        // length mismatch
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":[]}}}",
                "android.cameras.infos");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":0,\"infos\":"
                + "[{\"facing\":0,\"orientation\":0}]}}}",
                "android.cameras.infos");
        // infos wrong type
        assertInvalid("{\"android\":{\"cameras\":{\"count\":0,\"infos\":null}}}",
                "android.cameras.infos");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":0,\"infos\":{}}}}",
                "android.cameras.infos");
        // entry wrong type / unknown key
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":[1]}}}",
                "android.cameras.infos[0]");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":"
                + "[{\"facing\":0,\"orientation\":0,\"extra\":1}]}}}",
                "android.cameras.infos[0].extra");
        // facing required / wrong / out of range
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":"
                + "[{\"orientation\":0}]}}}",
                "android.cameras.infos[0].facing");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":"
                + "[{\"facing\":null,\"orientation\":0}]}}}",
                "android.cameras.infos[0].facing");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":"
                + "[{\"facing\":3,\"orientation\":0}]}}}",
                "android.cameras.infos[0].facing");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":"
                + "[{\"facing\":-1,\"orientation\":0}]}}}",
                "android.cameras.infos[0].facing");
        // orientation required / wrong / not in {0,90,180,270}
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":"
                + "[{\"facing\":0}]}}}",
                "android.cameras.infos[0].orientation");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":"
                + "[{\"facing\":0,\"orientation\":null}]}}}",
                "android.cameras.infos[0].orientation");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":"
                + "[{\"facing\":0,\"orientation\":45}]}}}",
                "android.cameras.infos[0].orientation");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":"
                + "[{\"facing\":0,\"orientation\":1.5}]}}}",
                "android.cameras.infos[0].orientation");
        // canDisableShutterSound optional; reject non-Boolean
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":"
                + "[{\"facing\":0,\"orientation\":0,\"canDisableShutterSound\":null}]}}}",
                "android.cameras.infos[0].canDisableShutterSound");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":"
                + "[{\"facing\":0,\"orientation\":0,\"canDisableShutterSound\":\"true\"}]}}}",
                "android.cameras.infos[0].canDisableShutterSound");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":"
                + "[{\"facing\":0,\"orientation\":0,\"canDisableShutterSound\":1}]}}}",
                "android.cameras.infos[0].canDisableShutterSound");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"infos\":"
                + "[{\"facing\":0,\"orientation\":0,\"canDisableShutterSound\":1.0}]}}}",
                "android.cameras.infos[0].canDisableShutterSound");

        TraceEnvironmentConfig noStreams = TraceEnvironmentConfig.parse(
                "{\"android\":{\"cameras\":{\"count\":1}}}");
        assertFalse(noStreams.getAndroidCamerasConfig().isStreamsConfigured());
        assertTrue(noStreams.getAndroidCamerasConfig().getStreams().isEmpty());
        assertNull(noStreams.getAndroidCamerasConfig().findStream(0));

        TraceEnvironmentConfig emptyStreams = TraceEnvironmentConfig.parse(
                "{\"android\":{\"cameras\":{\"count\":1,\"streams\":[]}}}");
        assertTrue(emptyStreams.getAndroidCamerasConfig().isStreamsConfigured());
        assertTrue(emptyStreams.getAndroidCamerasConfig().getStreams().isEmpty());

        TraceEnvironmentConfig oneStream = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":2,\"height\":2,"
                + "\"previewHex\":\"000102030405\",\"jpegHex\":\"ffd8ffd9\"}]}}}");
        assertTrue(oneStream.getAndroidCamerasConfig().isStreamsConfigured());
        assertEquals(1, oneStream.getAndroidCamerasConfig().getStreams().size());
        TraceEnvironmentConfig.AndroidCameraStreamConfig stream =
                oneStream.getAndroidCamerasConfig().findStream(0);
        assertNotNull(stream);
        assertEquals(0, stream.getCameraId());
        assertEquals(2, stream.getWidth());
        assertEquals(2, stream.getHeight());
        assertTrue(stream.isPreviewConfigured());
        assertEquals(6, stream.getPreviewNv21().length);
        assertNull(stream.getPreviewFile());
        assertTrue(stream.isJpegConfigured());
        assertEquals(4, stream.getJpeg().length);
        assertNull(stream.getJpegFile());
        assertArrayEquals(stream.getPreviewNv21(), oneStream.resolveCameraPreview(stream));
        assertArrayEquals(stream.getJpeg(), oneStream.resolveCameraJpeg(stream));
        assertNull(oneStream.getAndroidCamerasConfig().findStream(1));

        TraceEnvironmentConfig fileOnly = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":2,\"height\":2,"
                + "\"previewFile\":\"/data/local/tmp/preview.nv21\","
                + "\"jpegFile\":\"/data/local/tmp/still.jpg\"}]}}}");
        TraceEnvironmentConfig.AndroidCameraStreamConfig fileStream =
                fileOnly.getAndroidCamerasConfig().findStream(0);
        assertNotNull(fileStream);
        assertTrue(fileStream.isPreviewConfigured());
        assertNull(fileStream.getPreviewNv21());
        assertEquals("/data/local/tmp/preview.nv21", fileStream.getPreviewFile());
        assertTrue(fileStream.isJpegConfigured());
        assertNull(fileStream.getJpeg());
        assertEquals("/data/local/tmp/still.jpg", fileStream.getJpegFile());
        assertNull(fileOnly.resolveCameraPreview(fileStream));
        assertNull(fileOnly.resolveCameraJpeg(fileStream));

        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"streams\":{}}}}",
                "android.cameras.streams");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":0,\"streams\":[{"
                + "\"cameraId\":0,\"width\":2,\"height\":2,\"jpegHex\":\"ff\"}]}}}",
                "android.cameras.streams[0].cameraId");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"width\":2,\"height\":2,\"jpegHex\":\"ff\"}]}}}",
                "android.cameras.streams[0].cameraId");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":2,\"height\":2}]}}}",
                "previewHex, previewFile, jpegHex and/or jpegFile");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":2,\"height\":2,\"previewHex\":\"00\"}]}}}",
                "previewHex length");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":1,\"height\":2,\"previewHex\":\"000102\"}]}}}",
                "even width");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":1,\"height\":2,"
                + "\"previewFile\":\"/data/local/tmp/preview.nv21\"}]}}}",
                "even width");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":2,\"height\":2,"
                + "\"previewHex\":\"000102030405\","
                + "\"previewFile\":\"/data/local/tmp/preview.nv21\"}]}}}",
                "mutually exclusive");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":2,\"height\":2,"
                + "\"jpegHex\":\"ff\",\"jpegFile\":\"/data/local/tmp/still.jpg\"}]}}}",
                "mutually exclusive");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":2,\"height\":2,\"previewFile\":\"preview.nv21\"}]}}}",
                "absolute POSIX overlay path");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":2,\"height\":2,"
                + "\"previewFile\":\"/data/local/tmp/../preview.nv21\"}]}}}",
                "absolute POSIX overlay path");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":2,\"height\":2,\"previewFile\":\"\"}]}}}",
                "absolute POSIX overlay path");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":2,\"streams\":["
                + "{\"cameraId\":0,\"width\":2,\"height\":2,\"jpegHex\":\"ff\"},"
                + "{\"cameraId\":0,\"width\":2,\"height\":2,\"jpegHex\":\"aa\"}]}}}",
                "unique");
        assertInvalid("{\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":2,\"height\":2,\"jpegHex\":\"ff\",\"extra\":1}]}}}",
                "android.cameras.streams[0].extra");
    }

    @Test
    public void testAndroidCameraStreamOverlayFiles() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("traceai-camera-overlay-");
        java.io.File preview = new java.io.File(dir.toFile(), "files/data/local/tmp/preview.nv21");
        java.io.File jpeg = new java.io.File(dir.toFile(), "files/data/local/tmp/still.jpg");
        assertTrue(preview.getParentFile().mkdirs());
        byte[] previewBytes = new byte[] { 0, 1, 2, 3, 4, 5 };
        byte[] jpegBytes = new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9 };
        java.nio.file.Files.write(preview.toPath(), previewBytes);
        java.nio.file.Files.write(jpeg.toPath(), jpegBytes);
        java.io.File json = new java.io.File(dir.toFile(), "device-fingerprint.json");
        java.nio.file.Files.write(json.toPath(), ("{"
                + "\"schemaVersion\":\"traceai-device-fingerprint/v1\","
                + "\"android\":{\"cameras\":{\"count\":1,\"streams\":[{"
                + "\"cameraId\":0,\"width\":2,\"height\":2,"
                + "\"previewFile\":\"/data/local/tmp/preview.nv21\","
                + "\"jpegFile\":\"/data/local/tmp/still.jpg\"}]}}}"
                ).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        DeviceFingerprintProfile profile = DeviceFingerprintProfile.load(json);
        TraceEnvironmentConfig config = profile.getEnvironmentConfig();
        TraceEnvironmentConfig.AndroidCameraStreamConfig stream =
                config.getAndroidCamerasConfig().findStream(0);
        assertNotNull(stream);
        assertNull(stream.getPreviewNv21());
        assertNull(stream.getJpeg());
        assertArrayEquals(previewBytes, config.resolveCameraPreview(stream));
        assertArrayEquals(jpegBytes, config.resolveCameraJpeg(stream));

        java.nio.file.Files.write(preview.toPath(), new byte[] { 0, 1 });
        assertNull(config.resolveCameraPreview(stream));
    }

    @Test
    public void testAndroidSensorsConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidSensorsConfigured());
        assertNull(missing.getAndroidSensorsConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidSensorsConfigured());
        assertNull(missingAndroid.getAndroidSensorsConfig());

        // explicit empty → default empty types list
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{}}}");
        assertTrue(empty.isAndroidSensorsConfigured());
        TraceEnvironmentConfig.AndroidSensorsConfig e = empty.getAndroidSensorsConfig();
        assertNotNull(e);
        assertFalse(e.isTypesConfigured());
        assertTrue(e.getTypes().isEmpty());
        assertFalse(e.containsType(1));
        assertFalse(e.isDynamicTypesConfigured());
        assertTrue(e.getDynamicTypes().isEmpty());
        assertFalse(e.isDynamicDiscoverySupported());
        assertFalse(e.isNamesConfigured());
        assertFalse(e.isVendorsConfigured());
        assertFalse(e.isVersionsConfigured());
        assertFalse(e.isStringTypesConfigured());
        assertFalse(e.isMaximumRangesConfigured());
        assertFalse(e.isResolutionsConfigured());
        assertFalse(e.isPowersConfigured());
        assertFalse(e.isMinDelaysMicrosConfigured());
        assertFalse(e.isMaxDelaysMicrosConfigured());
        assertFalse(e.isFifoReservedEventCountsConfigured());
        assertFalse(e.isFifoMaxEventCountsConfigured());
        assertFalse(e.isWakeUpSensorsConfigured());
        assertFalse(e.isSensorIdsConfigured());
        assertFalse(e.isReportingModesConfigured());
        assertFalse(e.isDynamicSensorsConfigured());
        assertFalse(e.isRequiredPermissionsConfigured());
        assertFalse(e.isAdditionalInfoSupportedConfigured());
        assertFalse(e.isHighestDirectReportRateLevelsConfigured());
        assertFalse(empty.isAndroidSensorHighestDirectReportRateLevelsConfigured());
        assertFalse(empty.isAndroidSensorHighestDirectReportRateLevelConfigured(1));
        assertNull(empty.getAndroidSensorHighestDirectReportRateLevel(1));
        assertFalse(empty.isAndroidSensorIdsConfigured());
        assertFalse(empty.isAndroidSensorIdConfigured(1));
        assertNull(empty.getAndroidSensorId(1));
        assertFalse(empty.isAndroidSensorReportingModesConfigured());
        assertFalse(empty.isAndroidSensorReportingModeConfigured(1));
        assertNull(empty.getAndroidSensorReportingMode(1));
        assertFalse(empty.isAndroidSensorDynamicSensorsConfigured());
        assertFalse(empty.isAndroidSensorDynamicSensorConfigured(1));
        assertNull(empty.getAndroidSensorDynamicSensor(1));
        assertFalse(empty.isAndroidSensorRequiredPermissionsConfigured());
        assertFalse(empty.isAndroidSensorRequiredPermissionConfigured(1));
        assertNull(empty.getAndroidSensorRequiredPermission(1));
        assertFalse(empty.isAndroidSensorAdditionalInfoSupportedConfigured());
        assertFalse(empty.isAndroidSensorAdditionalInfoSupportedConfigured(1));
        assertNull(empty.getAndroidSensorAdditionalInfoSupported(1));

        // explicit empty array
        TraceEnvironmentConfig emptyArr = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[]}}}");
        assertTrue(emptyArr.isAndroidSensorsConfigured());
        assertTrue(emptyArr.getAndroidSensorsConfig().isTypesConfigured());
        assertTrue(emptyArr.getAndroidSensorsConfig().getTypes().isEmpty());

        // nonempty order-preserving unique types
        TraceEnvironmentConfig types = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[1,4,65535]}}}");
        assertTrue(types.isAndroidSensorsConfigured());
        TraceEnvironmentConfig.AndroidSensorsConfig sc = types.getAndroidSensorsConfig();
        assertNotNull(sc);
        assertTrue(sc.isTypesConfigured());
        assertEquals(3, sc.getTypes().size());
        assertEquals(Integer.valueOf(1), sc.getTypes().get(0));
        assertEquals(Integer.valueOf(4), sc.getTypes().get(1));
        assertEquals(Integer.valueOf(65535), sc.getTypes().get(2));
        assertTrue(sc.containsType(1));
        assertTrue(sc.containsType(4));
        assertTrue(sc.containsType(65535));
        assertFalse(sc.containsType(2));

        // immutable / stable
        assertEquals(3, types.getAndroidSensorsConfig().getTypes().size());

        // dynamicTypes omitted under present sensors → configured-empty dynamic list
        assertFalse(types.getAndroidSensorsConfig().isDynamicTypesConfigured());
        assertTrue(types.getAndroidSensorsConfig().getDynamicTypes().isEmpty());
        assertFalse(types.getAndroidSensorsConfig().containsDynamicType(1));

        // explicit empty dynamicTypes array
        TraceEnvironmentConfig dynEmptyArr = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"dynamicTypes\":[]}}}");
        assertTrue(dynEmptyArr.isAndroidSensorsConfigured());
        TraceEnvironmentConfig.AndroidSensorsConfig de = dynEmptyArr.getAndroidSensorsConfig();
        assertTrue(de.isDynamicTypesConfigured());
        assertTrue(de.getDynamicTypes().isEmpty());
        assertFalse(de.isTypesConfigured());

        // nonempty order-preserving unique dynamicTypes, may overlap types
        TraceEnvironmentConfig dyn = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[1,4],\"dynamicTypes\":[7,1,65535]}}}");
        assertTrue(dyn.isAndroidSensorsConfigured());
        TraceEnvironmentConfig.AndroidSensorsConfig dc = dyn.getAndroidSensorsConfig();
        assertNotNull(dc);
        assertTrue(dc.isDynamicTypesConfigured());
        assertEquals(3, dc.getDynamicTypes().size());
        assertEquals(Integer.valueOf(7), dc.getDynamicTypes().get(0));
        assertEquals(Integer.valueOf(1), dc.getDynamicTypes().get(1));
        assertEquals(Integer.valueOf(65535), dc.getDynamicTypes().get(2));
        assertTrue(dc.containsDynamicType(7));
        assertTrue(dc.containsDynamicType(1)); // overlaps types
        assertFalse(dc.containsDynamicType(4)); // types-only
        // types list itself unchanged by dynamicTypes
        assertEquals(2, dc.getTypes().size());
        assertEquals(Integer.valueOf(1), dc.getTypes().get(0));
        assertEquals(Integer.valueOf(4), dc.getTypes().get(1));
        // immutable / stable
        assertEquals(3, dyn.getAndroidSensorsConfig().getDynamicTypes().size());

        // dynamicTypes wrong type / null / element / range / duplicate
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":null}}}",
                "android.sensors.dynamicTypes");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":1}}}",
                "android.sensors.dynamicTypes");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":\"1\"}}}",
                "android.sensors.dynamicTypes");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":{}}}}",
                "android.sensors.dynamicTypes");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[null]}}}",
                "android.sensors.dynamicTypes[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[\"1\"]}}}",
                "android.sensors.dynamicTypes[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[true]}}}",
                "android.sensors.dynamicTypes[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[1.5]}}}",
                "android.sensors.dynamicTypes[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[0]}}}",
                "android.sensors.dynamicTypes[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[-1]}}}",
                "android.sensors.dynamicTypes[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[65536]}}}",
                "android.sensors.dynamicTypes[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[1,1]}}}",
                "android.sensors.dynamicTypes[1]");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7,1,7]}}}",
                "android.sensors.dynamicTypes[2]");

        // dynamicDiscoverySupported missing under present sensors → default false
        assertFalse(dyn.getAndroidSensorsConfig().isDynamicDiscoverySupported());
        assertFalse(empty.getAndroidSensorsConfig().isDynamicDiscoverySupported());

        // explicit true / false
        TraceEnvironmentConfig dynTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"dynamicDiscoverySupported\":true}}}");
        assertTrue(dynTrue.isAndroidSensorsConfigured());
        assertTrue(dynTrue.getAndroidSensorsConfig().isDynamicDiscoverySupported());
        TraceEnvironmentConfig dynFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"dynamicDiscoverySupported\":false}}}");
        assertTrue(dynFalse.isAndroidSensorsConfigured());
        assertFalse(dynFalse.getAndroidSensorsConfig().isDynamicDiscoverySupported());

        // independent of dynamicTypes: true with no dynamicTypes; false with nonempty dynamicTypes
        TraceEnvironmentConfig dynFlagOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[1],\"dynamicDiscoverySupported\":true}}}");
        assertTrue(dynFlagOnly.getAndroidSensorsConfig().isDynamicDiscoverySupported());
        assertTrue(dynFlagOnly.getAndroidSensorsConfig().getDynamicTypes().isEmpty());
        TraceEnvironmentConfig dynFlagFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"dynamicDiscoverySupported\":false}}}");
        assertFalse(dynFlagFalse.getAndroidSensorsConfig().isDynamicDiscoverySupported());
        assertEquals(1, dynFlagFalse.getAndroidSensorsConfig().getDynamicTypes().size());

        // dynamicDiscoverySupported non-Boolean → rejected
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicDiscoverySupported\":1}}}",
                "android.sensors.dynamicDiscoverySupported");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicDiscoverySupported\":\"yes\"}}}",
                "android.sensors.dynamicDiscoverySupported");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicDiscoverySupported\":null}}}",
                "android.sensors.dynamicDiscoverySupported");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicDiscoverySupported\":[]}}}",
                "android.sensors.dynamicDiscoverySupported");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicDiscoverySupported\":{}}}}",
                "android.sensors.dynamicDiscoverySupported");

        // coexist with cameras
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"sensors\":{\"types\":[1]},"
                + "\"cameras\":{\"count\":1}"
                + "}}"
        );
        assertTrue(both.isAndroidSensorsConfigured());
        assertEquals(1, both.getAndroidSensorsConfig().getTypes().size());
        assertTrue(both.isAndroidCamerasConfigured());
        assertEquals(1, both.getAndroidCamerasConfig().getCount());

        // wrong node type
        assertInvalid("{\"android\":{\"sensors\":[]}}", "android.sensors");
        assertInvalid("{\"android\":{\"sensors\":1}}", "android.sensors");
        assertInvalid("{\"android\":{\"sensors\":\"yes\"}}", "android.sensors");

        // unknown keys
        assertInvalid("{\"android\":{\"sensors\":{\"extra\":1}}}",
                "android.sensors.extra");

        // names omitted under present sensors → not configured; no reverse inference
        assertFalse(types.isAndroidSensorNamesConfigured());
        assertFalse(types.isAndroidSensorNameConfigured(1));
        assertNull(types.getAndroidSensorName(1));
        assertFalse(types.getAndroidSensorsConfig().isNamesConfigured());
        assertFalse(types.getAndroidSensorsConfig().isNameConfigured(1));
        assertNull(types.getAndroidSensorsConfig().getName(1));

        // explicit empty names object
        TraceEnvironmentConfig namesEmpty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[1],\"names\":{}}}}");
        assertTrue(namesEmpty.isAndroidSensorsConfigured());
        assertTrue(namesEmpty.isAndroidSensorNamesConfigured());
        assertFalse(namesEmpty.isAndroidSensorNameConfigured(1));
        assertNull(namesEmpty.getAndroidSensorName(1));
        assertEquals(1, namesEmpty.getAndroidSensorsConfig().getTypes().size());

        // static type name
        TraceEnvironmentConfig namedStatic = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[1,4],\"names\":{\"1\":\"Accel\"}}}}");
        assertTrue(namedStatic.isAndroidSensorNamesConfigured());
        assertTrue(namedStatic.isAndroidSensorNameConfigured(1));
        assertFalse(namedStatic.isAndroidSensorNameConfigured(4));
        assertEquals("Accel", namedStatic.getAndroidSensorName(1));
        assertNull(namedStatic.getAndroidSensorName(4));
        assertEquals(2, namedStatic.getAndroidSensorsConfig().getTypes().size());
        assertTrue(namedStatic.getAndroidSensorsConfig().getDynamicTypes().isEmpty());

        // dynamic-only type name; does not add to types
        TraceEnvironmentConfig namedDyn = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"names\":{\"7\":\"DynGyro\"}}}}");
        assertTrue(namedDyn.isAndroidSensorNameConfigured(7));
        assertEquals("DynGyro", namedDyn.getAndroidSensorName(7));
        assertTrue(namedDyn.getAndroidSensorsConfig().getTypes().isEmpty());
        assertFalse(namedDyn.getAndroidSensorsConfig().containsType(7));
        assertTrue(namedDyn.getAndroidSensorsConfig().containsDynamicType(7));

        // overlap type may be named once; lists unchanged
        TraceEnvironmentConfig namedOverlap = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[1],\"dynamicTypes\":[1],\"names\":{\"1\":\"X\"}}}}");
        assertEquals("X", namedOverlap.getAndroidSensorName(1));
        assertEquals(1, namedOverlap.getAndroidSensorsConfig().getTypes().size());
        assertEquals(1, namedOverlap.getAndroidSensorsConfig().getDynamicTypes().size());

        // 256 UTF-16 code units accepted
        StringBuilder name256 = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            name256.append('A');
        }
        TraceEnvironmentConfig namedMax = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":\""
                        + name256 + "\"}}}}");
        assertEquals(256, namedMax.getAndroidSensorName(1).length());

        // missing sensors node → names APIs are safe false/null
        assertFalse(missing.isAndroidSensorNamesConfigured());
        assertFalse(missing.isAndroidSensorNameConfigured(1));
        assertNull(missing.getAndroidSensorName(1));

        // names wrong node type
        assertInvalid("{\"android\":{\"sensors\":{\"names\":[]}}}",
                "android.sensors.names");
        assertInvalid("{\"android\":{\"sensors\":{\"names\":1}}}",
                "android.sensors.names");
        assertInvalid("{\"android\":{\"sensors\":{\"names\":\"yes\"}}}",
                "android.sensors.names");
        assertInvalid("{\"android\":{\"sensors\":{\"names\":null}}}",
                "android.sensors.names");

        // illegal keys (whitespace / sign / decimal / leading zero / range)
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"01\":\"A\"}}}}",
                "android.sensors.names.01");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"0\":\"A\"}}}}",
                "android.sensors.names.0");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"+1\":\"A\"}}}}",
                "android.sensors.names.+1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"-1\":\"A\"}}}}",
                "android.sensors.names.-1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1.0\":\"A\"}}}}",
                "android.sensors.names.1.0");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\" 1\":\"A\"}}}}",
                "android.sensors.names. 1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1 \":\"A\"}}}}",
                "android.sensors.names.1 ");
        assertInvalid("{\"android\":{\"sensors\":{\"names\":{\"65536\":\"A\"}}}}",
                "android.sensors.names.65536");

        // value not a non-empty String without NUL/CR/LF; 257 rejected
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":null}}}}",
                "android.sensors.names.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":1}}}}",
                "android.sensors.names.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":true}}}}",
                "android.sensors.names.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":[]}}}}",
                "android.sensors.names.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":{}}}}}",
                "android.sensors.names.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":\"\"}}}}",
                "android.sensors.names.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":\"a\\nb\"}}}}",
                "android.sensors.names.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":\"a\\rb\"}}}}",
                "android.sensors.names.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":\"a\\u0000b\"}}}}",
                "android.sensors.names.1");
        StringBuilder name257 = new StringBuilder();
        for (int i = 0; i < 257; i++) {
            name257.append('B');
        }
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"1\":\""
                        + name257 + "\"}}}}",
                "android.sensors.names.1");

        // names type must already appear in types or dynamicTypes
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"names\":{\"4\":\"A\"}}}}",
                "android.sensors.names.4");
        assertInvalid("{\"android\":{\"sensors\":{\"names\":{\"1\":\"A\"}}}}",
                "android.sensors.names.1");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"names\":{\"1\":\"A\"}}}}",
                "android.sensors.names.1");

        // types wrong type / null
        assertInvalid("{\"android\":{\"sensors\":{\"types\":null}}}",
                "android.sensors.types");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":1}}}",
                "android.sensors.types");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":\"1\"}}}",
                "android.sensors.types");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":{}}}}",
                "android.sensors.types");

        // element wrong type / null / range / duplicate
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[null]}}}",
                "android.sensors.types[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[\"1\"]}}}",
                "android.sensors.types[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[true]}}}",
                "android.sensors.types[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1.5]}}}",
                "android.sensors.types[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[0]}}}",
                "android.sensors.types[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[-1]}}}",
                "android.sensors.types[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[65536]}}}",
                "android.sensors.types[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1,1]}}}",
                "android.sensors.types[1]");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1,4,1]}}}",
                "android.sensors.types[2]");
    }

    @Test
    public void testAndroidSensorsStaticDescriptorMaps() {
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[1,4],\"dynamicTypes\":[7]}}}");
        assertFalse(omitted.isAndroidSensorIdsConfigured());
        assertFalse(omitted.isAndroidSensorReportingModesConfigured());
        assertFalse(omitted.isAndroidSensorDynamicSensorsConfigured());
        assertFalse(omitted.isAndroidSensorRequiredPermissionsConfigured());
        assertFalse(omitted.isAndroidSensorAdditionalInfoSupportedConfigured());
        assertFalse(omitted.isAndroidSensorHighestDirectReportRateLevelsConfigured());
        assertFalse(omitted.isAndroidSensorDirectChannelTypesSupportedConfigured());
        assertFalse(omitted.isAndroidSensorDynamicSensorConfigured(7));
        assertNull(omitted.getAndroidSensorDynamicSensor(7));

        TraceEnvironmentConfig emptyMaps = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[1],"
                        + "\"sensorIds\":{},\"reportingModes\":{},\"dynamicSensors\":{},"
                        + "\"requiredPermissions\":{},\"additionalInfoSupported\":{},"
                        + "\"highestDirectReportRateLevels\":{},\"directChannelTypesSupported\":{}}}}");
        assertTrue(emptyMaps.isAndroidSensorIdsConfigured());
        assertFalse(emptyMaps.isAndroidSensorIdConfigured(1));
        assertNull(emptyMaps.getAndroidSensorId(1));
        assertTrue(emptyMaps.isAndroidSensorReportingModesConfigured());
        assertFalse(emptyMaps.isAndroidSensorReportingModeConfigured(1));
        assertTrue(emptyMaps.isAndroidSensorDynamicSensorsConfigured());
        assertFalse(emptyMaps.isAndroidSensorDynamicSensorConfigured(1));
        assertTrue(emptyMaps.isAndroidSensorRequiredPermissionsConfigured());
        assertFalse(emptyMaps.isAndroidSensorRequiredPermissionConfigured(1));
        assertTrue(emptyMaps.isAndroidSensorAdditionalInfoSupportedConfigured());
        assertFalse(emptyMaps.isAndroidSensorAdditionalInfoSupportedConfigured(1));
        assertTrue(emptyMaps.isAndroidSensorHighestDirectReportRateLevelsConfigured());
        assertFalse(emptyMaps.isAndroidSensorHighestDirectReportRateLevelConfigured(1));
        assertTrue(emptyMaps.isAndroidSensorDirectChannelTypesSupportedConfigured());
        assertFalse(emptyMaps.isAndroidSensorDirectChannelTypeSupportedConfigured(1));

        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[1,4],\"dynamicTypes\":[7],"
                        + "\"sensorIds\":{\"1\":-1,\"7\":" + Integer.MAX_VALUE + "},"
                        + "\"reportingModes\":{\"1\":0,\"7\":3},"
                        + "\"dynamicSensors\":{\"1\":false,\"7\":true},"
                        + "\"requiredPermissions\":{\"1\":\"\",\"7\":\"android.permission.BODY_SENSORS\"},"
                        + "\"additionalInfoSupported\":{\"1\":false,\"7\":true},"
                        + "\"highestDirectReportRateLevels\":{\"1\":0,\"7\":3},"
                        + "\"directChannelTypesSupported\":{\"1\":[1],\"7\":[1,2]}}}}");
        assertEquals(Integer.valueOf(-1), full.getAndroidSensorId(1));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), full.getAndroidSensorId(7));
        assertFalse(full.isAndroidSensorIdConfigured(4));
        assertNull(full.getAndroidSensorId(4));
        assertEquals(Integer.valueOf(0), full.getAndroidSensorReportingMode(1));
        assertEquals(Integer.valueOf(3), full.getAndroidSensorReportingMode(7));
        assertEquals(Boolean.FALSE, full.getAndroidSensorDynamicSensor(1));
        assertEquals(Boolean.TRUE, full.getAndroidSensorDynamicSensor(7));
        assertFalse(full.isAndroidSensorDynamicSensorConfigured(4));
        assertEquals("", full.getAndroidSensorRequiredPermission(1));
        assertEquals("android.permission.BODY_SENSORS", full.getAndroidSensorRequiredPermission(7));
        assertEquals(Boolean.FALSE, full.getAndroidSensorAdditionalInfoSupported(1));
        assertEquals(Boolean.TRUE, full.getAndroidSensorAdditionalInfoSupported(7));
        assertEquals(Integer.valueOf(0), full.getAndroidSensorHighestDirectReportRateLevel(1));
        assertEquals(Integer.valueOf(3), full.getAndroidSensorHighestDirectReportRateLevel(7));
        assertEquals(Boolean.TRUE, full.isAndroidSensorDirectChannelTypeSupported(1, 1));
        assertEquals(Boolean.FALSE, full.isAndroidSensorDirectChannelTypeSupported(1, 2));
        assertEquals(Boolean.TRUE, full.isAndroidSensorDirectChannelTypeSupported(7, 2));
        assertFalse(full.isAndroidSensorDirectChannelTypeSupportedConfigured(4));
        assertFalse(full.isAndroidSensorNamesConfigured());
        assertFalse(full.isAndroidSensorWakeUpSensorsConfigured());

        TraceEnvironmentConfig zeroId = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[1],\"sensorIds\":{\"1\":0}}}}");
        assertEquals(Integer.valueOf(0), zeroId.getAndroidSensorId(1));
        TraceEnvironmentConfig oneShotMinDelay = TraceEnvironmentConfig.parse(
                "{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":-1}}}}");
        assertEquals(Integer.valueOf(-1), oneShotMinDelay.getAndroidSensorMinDelayMicros(1));
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"minDelaysMicros\":{\"1\":-2}}}}",
                "android.sensors.minDelaysMicros.1");

        assertInvalid("{\"android\":{\"sensors\":{\"sensorIds\":[]}}}", "android.sensors.sensorIds");
        assertInvalid("{\"android\":{\"sensors\":{\"sensorIds\":1}}}", "android.sensors.sensorIds");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"sensorIds\":{\"1\":1.0}}}}",
                "android.sensors.sensorIds.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"sensorIds\":{\"1\":-2}}}}",
                "android.sensors.sensorIds.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"sensorIds\":{\"4\":1}}}}",
                "android.sensors.sensorIds.4");
        assertInvalid("{\"android\":{\"sensors\":{\"sensorIds\":{\"1\":1}}}}",
                "android.sensors.sensorIds.1");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicTypes\":[7],\"sensorIds\":{\"1\":1}}}}",
                "android.sensors.sensorIds.1");
        assertInvalid("{\"android\":{\"sensors\":{\"reportingModes\":[]}}}",
                "android.sensors.reportingModes");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"reportingModes\":{\"1\":4}}}}",
                "android.sensors.reportingModes.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"reportingModes\":{\"1\":-1}}}}",
                "android.sensors.reportingModes.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"reportingModes\":{\"1\":1.0}}}}",
                "android.sensors.reportingModes.1");
        assertInvalid("{\"android\":{\"sensors\":{\"highestDirectReportRateLevels\":[]}}}",
                "android.sensors.highestDirectReportRateLevels");
        assertInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"highestDirectReportRateLevels\":{\"1\":4}}}}",
                "android.sensors.highestDirectReportRateLevels.1");
        assertInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"highestDirectReportRateLevels\":{\"1\":-1}}}}",
                "android.sensors.highestDirectReportRateLevels.1");
        assertInvalid("{\"android\":{\"sensors\":{\"directChannelTypesSupported\":[]}}}",
                "android.sensors.directChannelTypesSupported");
        assertInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{\"1\":[3]}}}}",
                "android.sensors.directChannelTypesSupported.1[0]");
        assertInvalid(
                "{\"android\":{\"sensors\":{\"types\":[1],\"directChannelTypesSupported\":{\"1\":[-1]}}}}",
                "android.sensors.directChannelTypesSupported.1[0]");
        assertInvalid("{\"android\":{\"sensors\":{\"dynamicSensors\":[]}}}",
                "android.sensors.dynamicSensors");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"dynamicSensors\":{\"1\":1}}}}",
                "android.sensors.dynamicSensors.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"dynamicSensors\":{\"4\":true}}}}",
                "android.sensors.dynamicSensors.4");
        assertInvalid("{\"android\":{\"sensors\":{\"requiredPermissions\":[]}}}",
                "android.sensors.requiredPermissions");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"requiredPermissions\":{\"1\":1}}}}",
                "android.sensors.requiredPermissions.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"requiredPermissions\":{\"1\":null}}}}",
                "android.sensors.requiredPermissions.1");
        assertInvalid("{\"android\":{\"sensors\":{\"additionalInfoSupported\":[]}}}",
                "android.sensors.additionalInfoSupported");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"additionalInfoSupported\":{\"1\":1}}}}",
                "android.sensors.additionalInfoSupported.1");
        assertInvalid("{\"android\":{\"sensors\":{\"types\":[1],\"additionalInfoSupported\":{\"4\":true}}}}",
                "android.sensors.additionalInfoSupported.4");
    }

    @Test
    public void testExampleJsonSensorsStaticDescriptorMaps() throws IOException {
        File example = locateExampleJson();
        String json = new String(Files.readAllBytes(example.toPath()), StandardCharsets.UTF_8);
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        assertTrue(config.isAndroidSensorsConfigured());
        assertTrue(config.isAndroidSensorIdsConfigured());
        assertEquals(Integer.valueOf(-1), config.getAndroidSensorId(1));
        assertTrue(config.isAndroidSensorReportingModesConfigured());
        assertEquals(Integer.valueOf(0), config.getAndroidSensorReportingMode(1));
        assertTrue(config.isAndroidSensorDynamicSensorsConfigured());
        assertEquals(Boolean.TRUE, config.getAndroidSensorDynamicSensor(7));
        assertFalse(config.isAndroidSensorDynamicSensorConfigured(1));
        assertTrue(config.isAndroidSensorRequiredPermissionsConfigured());
        assertEquals("", config.getAndroidSensorRequiredPermission(1));
        assertTrue(config.isAndroidSensorAdditionalInfoSupportedConfigured());
        assertEquals(Boolean.FALSE, config.getAndroidSensorAdditionalInfoSupported(1));
        assertTrue(config.isAndroidSensorHighestDirectReportRateLevelsConfigured());
        assertEquals(Integer.valueOf(1), config.getAndroidSensorHighestDirectReportRateLevel(1));
        assertTrue(config.isAndroidSensorDirectChannelTypesSupportedConfigured());
        assertEquals(Boolean.TRUE, config.isAndroidSensorDirectChannelTypeSupported(1, 1));
        assertEquals(Boolean.FALSE, config.isAndroidSensorDirectChannelTypeSupported(1, 2));
        assertTrue(config.isAndroidSensorWakeUpSensorsConfigured());
        assertEquals(Boolean.FALSE, config.getAndroidSensorWakeUpSensor(1));
        assertEquals(Integer.valueOf(5000), config.getAndroidSensorMinDelayMicros(1));
        assertEquals(Integer.valueOf(-1), config.getAndroidSensorMinDelayMicros(4));
    }

    @Test
    public void testGraphicsV1Contract() {
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse("{}");
        assertFalse(omitted.isGraphicsConfigured());
        assertNull(omitted.getGraphicsConfig());

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{\"graphics\":{}}");
        assertTrue(empty.isGraphicsConfigured());
        assertFalse(empty.getGraphicsConfig().isVendorConfigured());
        assertFalse(empty.getGraphicsConfig().isExtensionsConfigured());
        assertNull(empty.getGraphicsConfig().getExtensionsJoined());

        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"graphics\":{"
                + "\"vendor\":\"Qualcomm\","
                + "\"renderer\":\"Adreno (TM) 730\","
                + "\"version\":\"OpenGL ES 3.2\","
                + "\"shadingLanguageVersion\":\"OpenGL ES GLSL ES 3.20\","
                + "\"extensions\":[\"GL_OES_EGL_image\"],"
                + "\"eglVendor\":\"Android\","
                + "\"eglVersion\":\"1.5 Android META-EGL\","
                + "\"eglExtensions\":[]"
                + "}}");
        assertEquals("Qualcomm", full.getGraphicsConfig().getVendor());
        assertEquals("GL_OES_EGL_image", full.getGraphicsConfig().getExtensionsJoined());
        assertEquals("", full.getGraphicsConfig().getEglExtensionsJoined());
        assertTrue(full.getGraphicsConfig().isRendererConfigured());
        assertEquals("Adreno (TM) 730", full.getGraphicsConfig().getRenderer());

        assertInvalid("{\"graphics\":[]}", "graphics");
        assertInvalid("{\"graphics\":{\"extra\":1}}", "graphics.extra");
        assertInvalid("{\"graphics\":{\"vendor\":\"\"}}", "graphics.vendor");
        assertInvalid("{\"graphics\":{\"extensions\":[\"GL A\"]}}", "graphics.extensions[0]");
        assertInvalid("{\"graphics\":{\"extensions\":[\"A\",\"A\"]}}", "graphics.extensions[1]");
        assertInvalid("{\"graphics\":{\"extensions\":[\"GL\\tOES\"]}}", "graphics.extensions[0]");
    }

    @Test
    public void testExampleJsonGraphics() throws IOException {
        File example = locateExampleJson();
        String json = new String(Files.readAllBytes(example.toPath()), StandardCharsets.UTF_8);
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
        assertTrue(config.isGraphicsConfigured());
        assertEquals("Qualcomm", config.getGraphicsConfig().getVendor());
        assertEquals("Adreno (TM) 730", config.getGraphicsConfig().getRenderer());
        assertEquals("OpenGL ES 3.2", config.getGraphicsConfig().getVersion());
        assertEquals("OpenGL ES GLSL ES 3.20", config.getGraphicsConfig().getShadingLanguageVersion());
        assertTrue(config.getGraphicsConfig().isExtensionsConfigured());
        assertEquals("GL_OES_EGL_image GL_EXT_texture_format_BGRA8888",
                config.getGraphicsConfig().getExtensionsJoined());
        assertEquals("Android", config.getGraphicsConfig().getEglVendor());
        assertEquals("1.5 Android META-EGL", config.getGraphicsConfig().getEglVersion());
        assertEquals("EGL_KHR_image_base EGL_ANDROID_image_native_buffer",
                config.getGraphicsConfig().getEglExtensionsJoined());
    }

    private static File locateExampleJson() {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++) {
            File candidate = new File(dir, "example/trace-env.example.json");
            if (candidate.isFile()) {
                return candidate;
            }
            dir = dir.getParentFile();
        }
        fail("example/trace-env.example.json not found from " + System.getProperty("user.dir"));
        return null;
    }

    @Test
    public void testAndroidClipboardConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidClipboardConfigured());
        assertNull(missing.getAndroidClipboardConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidClipboardConfigured());
        assertNull(missingAndroid.getAndroidClipboardConfig());

        // explicit empty → default hasPrimaryClip=false, no primaryText
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{}}}");
        assertTrue(empty.isAndroidClipboardConfigured());
        TraceEnvironmentConfig.AndroidClipboardConfig e = empty.getAndroidClipboardConfig();
        assertNotNull(e);
        assertFalse(e.isHasPrimaryClipConfigured());
        assertFalse(e.hasPrimaryClip());
        assertFalse(e.isPrimaryTextConfigured());
        assertNull(e.getPrimaryText());

        // explicit true
        TraceEnvironmentConfig clipTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true}}}");
        assertTrue(clipTrue.isAndroidClipboardConfigured());
        TraceEnvironmentConfig.AndroidClipboardConfig t = clipTrue.getAndroidClipboardConfig();
        assertTrue(t.isHasPrimaryClipConfigured());
        assertTrue(t.hasPrimaryClip());
        assertFalse(t.isPrimaryTextConfigured());
        assertNull(t.getPrimaryText());

        // explicit false
        TraceEnvironmentConfig clipFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":false}}}");
        assertTrue(clipFalse.getAndroidClipboardConfig().isHasPrimaryClipConfigured());
        assertFalse(clipFalse.getAndroidClipboardConfig().hasPrimaryClip());
        assertFalse(clipFalse.getAndroidClipboardConfig().isPrimaryTextConfigured());

        // primaryText with explicit hasPrimaryClip=true (including empty string)
        TraceEnvironmentConfig withText = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"hello\\uD83D\\uDE00\"}}}");
        assertTrue(withText.getAndroidClipboardConfig().isPrimaryTextConfigured());
        assertEquals("hello\uD83D\uDE00", withText.getAndroidClipboardConfig().getPrimaryText());
        assertTrue(withText.getAndroidClipboardConfig().hasPrimaryClip());
        TraceEnvironmentConfig emptyText = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,\"primaryText\":\"\"}}}");
        assertTrue(emptyText.getAndroidClipboardConfig().isPrimaryTextConfigured());
        assertEquals("", emptyText.getAndroidClipboardConfig().getPrimaryText());

        // immutable / stable access
        assertEquals(t.hasPrimaryClip(), clipTrue.getAndroidClipboardConfig().hasPrimaryClip());
        assertEquals(withText.getAndroidClipboardConfig().getPrimaryText(),
                withText.getAndroidClipboardConfig().getPrimaryText());
        assertNotNull(clipTrue.getAndroidClipboardConfig());

        // coexist with battery
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"clipboard\":{\"hasPrimaryClip\":true,\"primaryText\":\"x\"},"
                + "\"battery\":{\"capacityPercent\":88}"
                + "}}"
        );
        assertTrue(both.isAndroidClipboardConfigured());
        assertTrue(both.getAndroidClipboardConfig().hasPrimaryClip());
        assertEquals("x", both.getAndroidClipboardConfig().getPrimaryText());
        assertTrue(both.isAndroidBatteryConfigured());
        assertEquals(88, both.getAndroidBatteryConfig().getCapacityPercent());

        // wrong node type
        assertInvalid("{\"android\":{\"clipboard\":[]}}", "android.clipboard");
        assertInvalid("{\"android\":{\"clipboard\":1}}", "android.clipboard");
        assertInvalid("{\"android\":{\"clipboard\":\"yes\"}}", "android.clipboard");

        // unknown keys
        assertInvalid("{\"android\":{\"clipboard\":{\"text\":\"x\"}}}",
                "android.clipboard.text");
        assertInvalid("{\"android\":{\"clipboard\":{\"extra\":1}}}",
                "android.clipboard.extra");

        // hasPrimaryClip wrong types
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":null}}}",
                "android.clipboard.hasPrimaryClip");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":\"true\"}}}",
                "android.clipboard.hasPrimaryClip");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":1}}}",
                "android.clipboard.hasPrimaryClip");

        // primaryText wrong types
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,\"primaryText\":null}}}",
                "android.clipboard.primaryText");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,\"primaryText\":1}}}",
                "android.clipboard.primaryText");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,\"primaryText\":true}}}",
                "android.clipboard.primaryText");

        // primaryText requires hasPrimaryClip explicitly true
        assertInvalid("{\"android\":{\"clipboard\":{\"primaryText\":\"x\"}}}",
                "android.clipboard.primaryText");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":false,\"primaryText\":\"x\"}}}",
                "android.clipboard.primaryText");
    }

    @Test
    public void testAndroidClipboardPrimaryLabelConfig() {
        // missing key: empty object / hasPrimaryClip only / primaryText only
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{}}}");
        assertFalse(empty.getAndroidClipboardConfig().isPrimaryLabelConfigured());
        assertNull(empty.getAndroidClipboardConfig().getPrimaryLabel());
        TraceEnvironmentConfig clipTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true}}}");
        assertFalse(clipTrue.getAndroidClipboardConfig().isPrimaryLabelConfigured());
        assertNull(clipTrue.getAndroidClipboardConfig().getPrimaryLabel());
        TraceEnvironmentConfig withText = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,\"primaryText\":\"hello\"}}}");
        assertTrue(withText.getAndroidClipboardConfig().isPrimaryTextConfigured());
        assertFalse(withText.getAndroidClipboardConfig().isPrimaryLabelConfigured());
        assertNull(withText.getAndroidClipboardConfig().getPrimaryLabel());

        // valid with explicit primaryText (including unicode)
        TraceEnvironmentConfig withLabel = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"hello\",\"primaryLabel\":\"clip\\uD83D\\uDE00\"}}}");
        assertTrue(withLabel.getAndroidClipboardConfig().isPrimaryLabelConfigured());
        assertEquals("clip\uD83D\uDE00", withLabel.getAndroidClipboardConfig().getPrimaryLabel());
        assertEquals("hello", withLabel.getAndroidClipboardConfig().getPrimaryText());
        assertTrue(withLabel.getAndroidClipboardConfig().hasPrimaryClip());

        // empty label allowed when primaryText is configured (including empty primaryText)
        TraceEnvironmentConfig emptyLabel = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"primaryLabel\":\"\"}}}");
        assertTrue(emptyLabel.getAndroidClipboardConfig().isPrimaryLabelConfigured());
        assertEquals("", emptyLabel.getAndroidClipboardConfig().getPrimaryLabel());
        TraceEnvironmentConfig emptyBoth = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"\",\"primaryLabel\":\"\"}}}");
        assertTrue(emptyBoth.getAndroidClipboardConfig().isPrimaryLabelConfigured());
        assertEquals("", emptyBoth.getAndroidClipboardConfig().getPrimaryLabel());
        assertTrue(emptyBoth.getAndroidClipboardConfig().isPrimaryTextConfigured());
        assertEquals("", emptyBoth.getAndroidClipboardConfig().getPrimaryText());

        // immutable / stable access
        assertEquals(withLabel.getAndroidClipboardConfig().getPrimaryLabel(),
                withLabel.getAndroidClipboardConfig().getPrimaryLabel());

        // primaryLabel wrong types
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"primaryLabel\":null}}}",
                "android.clipboard.primaryLabel");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"primaryLabel\":1}}}",
                "android.clipboard.primaryLabel");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"primaryLabel\":true}}}",
                "android.clipboard.primaryLabel");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"primaryLabel\":[]}}}",
                "android.clipboard.primaryLabel");

        // label-only: requires explicitly configured primaryText
        assertInvalid("{\"android\":{\"clipboard\":{\"primaryLabel\":\"x\"}}}",
                "android.clipboard.primaryLabel");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,\"primaryLabel\":\"x\"}}}",
                "android.clipboard.primaryLabel");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":false,\"primaryLabel\":\"x\"}}}",
                "android.clipboard.primaryLabel");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,\"primaryLabel\":\"\"}}}",
                "android.clipboard.primaryLabel");
    }

    @Test
    public void testAndroidClipboardTimestampConfig() {
        // missing key: empty object / hasPrimaryClip only / primaryText only
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{}}}");
        assertFalse(empty.getAndroidClipboardConfig().isTimestampMillisConfigured());
        assertEquals(0L, empty.getAndroidClipboardConfig().getTimestampMillis());
        TraceEnvironmentConfig clipTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true}}}");
        assertFalse(clipTrue.getAndroidClipboardConfig().isTimestampMillisConfigured());
        assertEquals(0L, clipTrue.getAndroidClipboardConfig().getTimestampMillis());
        TraceEnvironmentConfig withText = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,\"primaryText\":\"hello\"}}}");
        assertTrue(withText.getAndroidClipboardConfig().isPrimaryTextConfigured());
        assertFalse(withText.getAndroidClipboardConfig().isTimestampMillisConfigured());
        assertEquals(0L, withText.getAndroidClipboardConfig().getTimestampMillis());

        // valid zero / positive / max
        TraceEnvironmentConfig zero = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestampMillis\":0}}}");
        assertTrue(zero.getAndroidClipboardConfig().isTimestampMillisConfigured());
        assertEquals(0L, zero.getAndroidClipboardConfig().getTimestampMillis());
        assertEquals("x", zero.getAndroidClipboardConfig().getPrimaryText());
        TraceEnvironmentConfig positive = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestampMillis\":1710000000000}}}");
        assertTrue(positive.getAndroidClipboardConfig().isTimestampMillisConfigured());
        assertEquals(1710000000000L, positive.getAndroidClipboardConfig().getTimestampMillis());
        TraceEnvironmentConfig max = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestampMillis\":" + Long.MAX_VALUE + "}}}");
        assertTrue(max.getAndroidClipboardConfig().isTimestampMillisConfigured());
        assertEquals(Long.MAX_VALUE, max.getAndroidClipboardConfig().getTimestampMillis());

        // immutable / stable access
        assertEquals(positive.getAndroidClipboardConfig().getTimestampMillis(),
                positive.getAndroidClipboardConfig().getTimestampMillis());

        // coexist with primaryLabel
        TraceEnvironmentConfig withLabel = TraceEnvironmentConfig.parse(
                "{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"primaryLabel\":\"lab\","
                        + "\"timestampMillis\":1}}}");
        assertEquals(1L, withLabel.getAndroidClipboardConfig().getTimestampMillis());
        assertEquals("lab", withLabel.getAndroidClipboardConfig().getPrimaryLabel());

        // invalid forms: null, fractional, scientific non-integral, string, boolean,
        // array, object, negative, overflow
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestampMillis\":null}}}",
                "android.clipboard.timestampMillis");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestampMillis\":1.5}}}",
                "android.clipboard.timestampMillis");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestampMillis\":1e-1}}}",
                "android.clipboard.timestampMillis");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestampMillis\":\"1\"}}}",
                "android.clipboard.timestampMillis");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestampMillis\":true}}}",
                "android.clipboard.timestampMillis");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestampMillis\":[]}}}",
                "android.clipboard.timestampMillis");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestampMillis\":{}}}}",
                "android.clipboard.timestampMillis");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestampMillis\":-1}}}",
                "android.clipboard.timestampMillis");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestampMillis\":9223372036854775808}}}",
                "android.clipboard.timestampMillis");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestampMillis\":9.223372036854776e18}}}",
                "android.clipboard.timestampMillis");

        // timestamp-only: requires explicitly configured primaryText
        assertInvalid("{\"android\":{\"clipboard\":{\"timestampMillis\":1}}}",
                "android.clipboard.timestampMillis");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,\"timestampMillis\":1}}}",
                "android.clipboard.timestampMillis");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":false,\"timestampMillis\":1}}}",
                "android.clipboard.timestampMillis");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,\"timestampMillis\":0}}}",
                "android.clipboard.timestampMillis");

        // old JSON keys timestamp and primaryTimestamp are not aliases
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"timestamp\":1}}}",
                "android.clipboard.timestamp is not an allowed key");
        assertInvalid("{\"android\":{\"clipboard\":{\"timestamp\":1}}}",
                "android.clipboard.timestamp is not an allowed key");
        assertInvalid("{\"android\":{\"clipboard\":{\"hasPrimaryClip\":true,"
                        + "\"primaryText\":\"x\",\"primaryTimestamp\":1}}}",
                "android.clipboard.primaryTimestamp is not an allowed key");
        assertInvalid("{\"android\":{\"clipboard\":{\"primaryTimestamp\":1}}}",
                "android.clipboard.primaryTimestamp is not an allowed key");
    }

    @Test
    public void testAndroidAccessibilityConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidAccessibilityConfigured());
        assertNull(missing.getAndroidAccessibilityConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidAccessibilityConfigured());
        assertNull(missingAndroid.getAndroidAccessibilityConfig());

        // explicit empty → defaults enabled=false, touchExplorationEnabled=false,
        // highContrastTextEnabled=false
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"accessibility\":{}}}");
        assertTrue(empty.isAndroidAccessibilityConfigured());
        TraceEnvironmentConfig.AndroidAccessibilityConfig e = empty.getAndroidAccessibilityConfig();
        assertNotNull(e);
        assertFalse(e.isEnabledConfigured());
        assertFalse(e.isEnabled());
        assertFalse(e.isTouchExplorationEnabledConfigured());
        assertFalse(e.isTouchExplorationEnabled());
        assertFalse(e.isHighContrastTextEnabledConfigured());
        assertFalse(e.isHighContrastTextEnabled());
        assertFalse(e.isServicesConfigured());
        assertTrue(e.getServices().isEmpty());

        // services empty array configured
        TraceEnvironmentConfig servicesEmpty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"accessibility\":{\"services\":[]}}}");
        assertTrue(servicesEmpty.getAndroidAccessibilityConfig().isServicesConfigured());
        assertTrue(servicesEmpty.getAndroidAccessibilityConfig().getServices().isEmpty());

        // services order + enabled flags
        TraceEnvironmentConfig servicesFull = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"accessibility\":{\"services\":["
                + "{\"id\":\"com.demo/.SvcA\",\"enabled\":true},"
                + "{\"id\":\"com.demo/.SvcB\",\"enabled\":false},"
                + "{\"id\":\"com.demo/.SvcC\",\"enabled\":true}"
                + "]}}}"
        );
        List<TraceEnvironmentConfig.AndroidAccessibilityServiceConfig> svcs =
                servicesFull.getAndroidAccessibilityConfig().getServices();
        assertTrue(servicesFull.getAndroidAccessibilityConfig().isServicesConfigured());
        assertEquals(3, svcs.size());
        assertEquals("com.demo/.SvcA", svcs.get(0).getId());
        assertTrue(svcs.get(0).isEnabled());
        assertEquals("com.demo/.SvcB", svcs.get(1).getId());
        assertFalse(svcs.get(1).isEnabled());
        assertEquals("com.demo/.SvcC", svcs.get(2).getId());
        assertTrue(svcs.get(2).isEnabled());
        try {
            svcs.clear();
            fail("expected UnsupportedOperationException for services list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }

        // explicit true
        TraceEnvironmentConfig accTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"accessibility\":{\"enabled\":true}}}");
        assertTrue(accTrue.isAndroidAccessibilityConfigured());
        TraceEnvironmentConfig.AndroidAccessibilityConfig t = accTrue.getAndroidAccessibilityConfig();
        assertTrue(t.isEnabledConfigured());
        assertTrue(t.isEnabled());
        assertFalse(t.isTouchExplorationEnabledConfigured());
        assertFalse(t.isTouchExplorationEnabled());
        assertFalse(t.isHighContrastTextEnabledConfigured());
        assertFalse(t.isHighContrastTextEnabled());

        // explicit false
        TraceEnvironmentConfig accFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"accessibility\":{\"enabled\":false}}}");
        assertTrue(accFalse.getAndroidAccessibilityConfig().isEnabledConfigured());
        assertFalse(accFalse.getAndroidAccessibilityConfig().isEnabled());

        // touchExplorationEnabled independent of enabled
        TraceEnvironmentConfig touchOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"accessibility\":{\"touchExplorationEnabled\":true}}}");
        assertTrue(touchOnly.getAndroidAccessibilityConfig().isTouchExplorationEnabledConfigured());
        assertTrue(touchOnly.getAndroidAccessibilityConfig().isTouchExplorationEnabled());
        assertFalse(touchOnly.getAndroidAccessibilityConfig().isEnabledConfigured());
        assertFalse(touchOnly.getAndroidAccessibilityConfig().isEnabled());
        assertFalse(touchOnly.getAndroidAccessibilityConfig().isHighContrastTextEnabledConfigured());
        assertFalse(touchOnly.getAndroidAccessibilityConfig().isHighContrastTextEnabled());

        TraceEnvironmentConfig enabledTrueTouchFalse = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"accessibility\":{"
                + "\"enabled\":true,\"touchExplorationEnabled\":false"
                + "}}}"
        );
        assertTrue(enabledTrueTouchFalse.getAndroidAccessibilityConfig().isEnabled());
        assertFalse(enabledTrueTouchFalse.getAndroidAccessibilityConfig().isTouchExplorationEnabled());

        TraceEnvironmentConfig enabledFalseTouchTrue = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"accessibility\":{"
                + "\"enabled\":false,\"touchExplorationEnabled\":true"
                + "}}}"
        );
        assertFalse(enabledFalseTouchTrue.getAndroidAccessibilityConfig().isEnabled());
        assertTrue(enabledFalseTouchTrue.getAndroidAccessibilityConfig().isTouchExplorationEnabled());

        // highContrastTextEnabled independent of enabled / touchExplorationEnabled
        TraceEnvironmentConfig highOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"accessibility\":{\"highContrastTextEnabled\":true}}}");
        assertTrue(highOnly.getAndroidAccessibilityConfig().isHighContrastTextEnabledConfigured());
        assertTrue(highOnly.getAndroidAccessibilityConfig().isHighContrastTextEnabled());
        assertFalse(highOnly.getAndroidAccessibilityConfig().isEnabledConfigured());
        assertFalse(highOnly.getAndroidAccessibilityConfig().isEnabled());
        assertFalse(highOnly.getAndroidAccessibilityConfig().isTouchExplorationEnabledConfigured());
        assertFalse(highOnly.getAndroidAccessibilityConfig().isTouchExplorationEnabled());

        TraceEnvironmentConfig highFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"accessibility\":{\"highContrastTextEnabled\":false}}}");
        assertTrue(highFalse.getAndroidAccessibilityConfig().isHighContrastTextEnabledConfigured());
        assertFalse(highFalse.getAndroidAccessibilityConfig().isHighContrastTextEnabled());

        TraceEnvironmentConfig threeIndep = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"accessibility\":{"
                + "\"enabled\":true,"
                + "\"touchExplorationEnabled\":false,"
                + "\"highContrastTextEnabled\":true"
                + "}}}"
        );
        assertTrue(threeIndep.getAndroidAccessibilityConfig().isEnabled());
        assertFalse(threeIndep.getAndroidAccessibilityConfig().isTouchExplorationEnabled());
        assertTrue(threeIndep.getAndroidAccessibilityConfig().isHighContrastTextEnabled());

        // immutable / stable access
        assertEquals(t.isEnabled(), accTrue.getAndroidAccessibilityConfig().isEnabled());
        assertNotNull(accTrue.getAndroidAccessibilityConfig());

        // coexist with clipboard (no inference either way)
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"accessibility\":{\"enabled\":true,\"touchExplorationEnabled\":true,"
                + "\"highContrastTextEnabled\":true},"
                + "\"clipboard\":{\"hasPrimaryClip\":false}"
                + "}}"
        );
        assertTrue(both.isAndroidAccessibilityConfigured());
        assertTrue(both.getAndroidAccessibilityConfig().isEnabled());
        assertTrue(both.getAndroidAccessibilityConfig().isTouchExplorationEnabled());
        assertTrue(both.getAndroidAccessibilityConfig().isHighContrastTextEnabled());
        assertTrue(both.isAndroidClipboardConfigured());
        assertFalse(both.getAndroidClipboardConfig().hasPrimaryClip());

        // wrong node type
        assertInvalid("{\"android\":{\"accessibility\":[]}}", "android.accessibility");
        assertInvalid("{\"android\":{\"accessibility\":1}}", "android.accessibility");
        assertInvalid("{\"android\":{\"accessibility\":\"yes\"}}", "android.accessibility");

        // unknown keys
        assertInvalid("{\"android\":{\"accessibility\":{\"extra\":1}}}",
                "android.accessibility.extra");

        // services validation
        assertInvalid("{\"android\":{\"accessibility\":{\"services\":{}}}}",
                "android.accessibility.services");
        assertInvalid("{\"android\":{\"accessibility\":{\"services\":[\"x\"]}}}",
                "android.accessibility.services[0]");
        assertInvalid("{\"android\":{\"accessibility\":{\"services\":[{\"enabled\":true}]}}}",
                "android.accessibility.services[0].id");
        assertInvalid("{\"android\":{\"accessibility\":{\"services\":[{\"id\":\"a\"}]}}}",
                "android.accessibility.services[0].enabled");
        assertInvalid("{\"android\":{\"accessibility\":{\"services\":[{\"id\":\"\",\"enabled\":true}]}}}",
                "android.accessibility.services[0].id");
        assertInvalid("{\"android\":{\"accessibility\":{\"services\":[{\"id\":1,\"enabled\":true}]}}}",
                "android.accessibility.services[0].id");
        assertInvalid("{\"android\":{\"accessibility\":{\"services\":[{\"id\":\"a\",\"enabled\":1}]}}}",
                "android.accessibility.services[0].enabled");
        assertInvalid("{\"android\":{\"accessibility\":{\"services\":["
                        + "{\"id\":\"a\",\"enabled\":true},{\"id\":\"a\",\"enabled\":false}]}}}",
                "android.accessibility.services[1].id");
        assertInvalid("{\"android\":{\"accessibility\":{\"services\":["
                        + "{\"id\":\"a\",\"enabled\":true,\"extra\":1}]}}}",
                "android.accessibility.services[0].extra");

        // enabled wrong types
        assertInvalid("{\"android\":{\"accessibility\":{\"enabled\":null}}}",
                "android.accessibility.enabled");
        assertInvalid("{\"android\":{\"accessibility\":{\"enabled\":\"true\"}}}",
                "android.accessibility.enabled");
        assertInvalid("{\"android\":{\"accessibility\":{\"enabled\":1}}}",
                "android.accessibility.enabled");

        // touchExplorationEnabled wrong types
        assertInvalid("{\"android\":{\"accessibility\":{\"touchExplorationEnabled\":null}}}",
                "android.accessibility.touchExplorationEnabled");
        assertInvalid("{\"android\":{\"accessibility\":{\"touchExplorationEnabled\":\"true\"}}}",
                "android.accessibility.touchExplorationEnabled");
        assertInvalid("{\"android\":{\"accessibility\":{\"touchExplorationEnabled\":1}}}",
                "android.accessibility.touchExplorationEnabled");

        // highContrastTextEnabled wrong types
        assertInvalid("{\"android\":{\"accessibility\":{\"highContrastTextEnabled\":null}}}",
                "android.accessibility.highContrastTextEnabled");
        assertInvalid("{\"android\":{\"accessibility\":{\"highContrastTextEnabled\":\"true\"}}}",
                "android.accessibility.highContrastTextEnabled");
        assertInvalid("{\"android\":{\"accessibility\":{\"highContrastTextEnabled\":1}}}",
                "android.accessibility.highContrastTextEnabled");
    }

    @Test
    public void testAndroidAudioConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidAudioConfigured());
        assertNull(missing.getAndroidAudioConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidAudioConfigured());
        assertNull(missingAndroid.getAndroidAudioConfig());

        // explicit empty → defaults musicActive=false, speakerphoneOn=false, ringerMode=2, mode=0;
        // properties not configured
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{}}}");
        assertTrue(empty.isAndroidAudioConfigured());
        TraceEnvironmentConfig.AndroidAudioConfig e = empty.getAndroidAudioConfig();
        assertNotNull(e);
        assertFalse(e.isMusicActiveConfigured());
        assertFalse(e.isMusicActive());
        assertFalse(e.isSpeakerphoneOnConfigured());
        assertFalse(e.isSpeakerphoneOn());
        assertFalse(e.isRingerModeConfigured());
        assertEquals(2, e.getRingerMode());
        assertFalse(e.isModeConfigured());
        assertEquals(0, e.getMode());
        assertFalse(e.isPropertiesConfigured());
        assertTrue(e.getProperties().isEmpty());
        assertFalse(e.isPropertyConfigured("android.media.property.OUTPUT_SAMPLE_RATE"));
        assertNull(e.getProperty("android.media.property.OUTPUT_SAMPLE_RATE"));

        // explicit true musicActive; defaults for other fields (independent)
        TraceEnvironmentConfig audioTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"musicActive\":true}}}");
        assertTrue(audioTrue.isAndroidAudioConfigured());
        TraceEnvironmentConfig.AndroidAudioConfig t = audioTrue.getAndroidAudioConfig();
        assertTrue(t.isMusicActiveConfigured());
        assertTrue(t.isMusicActive());
        assertFalse(t.isSpeakerphoneOnConfigured());
        assertFalse(t.isSpeakerphoneOn());
        assertFalse(t.isRingerModeConfigured());
        assertEquals(2, t.getRingerMode());
        assertFalse(t.isModeConfigured());
        assertEquals(0, t.getMode());

        // explicit false musicActive
        TraceEnvironmentConfig audioFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"musicActive\":false}}}");
        assertTrue(audioFalse.getAndroidAudioConfig().isMusicActiveConfigured());
        assertFalse(audioFalse.getAndroidAudioConfig().isMusicActive());

        // speakerphoneOn true independent of musicActive
        TraceEnvironmentConfig speakerTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"speakerphoneOn\":true}}}");
        assertTrue(speakerTrue.getAndroidAudioConfig().isSpeakerphoneOnConfigured());
        assertTrue(speakerTrue.getAndroidAudioConfig().isSpeakerphoneOn());
        assertFalse(speakerTrue.getAndroidAudioConfig().isMusicActiveConfigured());
        assertFalse(speakerTrue.getAndroidAudioConfig().isMusicActive());
        assertFalse(speakerTrue.getAndroidAudioConfig().isRingerModeConfigured());
        assertEquals(2, speakerTrue.getAndroidAudioConfig().getRingerMode());
        assertFalse(speakerTrue.getAndroidAudioConfig().isModeConfigured());
        assertEquals(0, speakerTrue.getAndroidAudioConfig().getMode());

        // both boolean fields independent combinations
        TraceEnvironmentConfig bothFields = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"musicActive\":true,\"speakerphoneOn\":false}}}");
        assertTrue(bothFields.getAndroidAudioConfig().isMusicActive());
        assertFalse(bothFields.getAndroidAudioConfig().isSpeakerphoneOn());
        TraceEnvironmentConfig bothTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"musicActive\":false,\"speakerphoneOn\":true}}}");
        assertFalse(bothTrue.getAndroidAudioConfig().isMusicActive());
        assertTrue(bothTrue.getAndroidAudioConfig().isSpeakerphoneOn());

        // ringerMode each legal value 0/1/2; presence independent of booleans
        TraceEnvironmentConfig ringerSilent = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"ringerMode\":0}}}");
        assertTrue(ringerSilent.getAndroidAudioConfig().isRingerModeConfigured());
        assertEquals(0, ringerSilent.getAndroidAudioConfig().getRingerMode());
        assertFalse(ringerSilent.getAndroidAudioConfig().isMusicActiveConfigured());
        assertFalse(ringerSilent.getAndroidAudioConfig().isMusicActive());
        assertFalse(ringerSilent.getAndroidAudioConfig().isSpeakerphoneOnConfigured());
        assertFalse(ringerSilent.getAndroidAudioConfig().isSpeakerphoneOn());
        TraceEnvironmentConfig ringerVibrate = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"ringerMode\":1}}}");
        assertTrue(ringerVibrate.getAndroidAudioConfig().isRingerModeConfigured());
        assertEquals(1, ringerVibrate.getAndroidAudioConfig().getRingerMode());
        TraceEnvironmentConfig ringerNormal = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"ringerMode\":2}}}");
        assertTrue(ringerNormal.getAndroidAudioConfig().isRingerModeConfigured());
        assertEquals(2, ringerNormal.getAndroidAudioConfig().getRingerMode());

        // mode representative legal values including 0 and 7; presence independent
        TraceEnvironmentConfig modeNormal = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"mode\":0}}}");
        assertTrue(modeNormal.getAndroidAudioConfig().isModeConfigured());
        assertEquals(0, modeNormal.getAndroidAudioConfig().getMode());
        assertFalse(modeNormal.getAndroidAudioConfig().isMusicActiveConfigured());
        assertFalse(modeNormal.getAndroidAudioConfig().isRingerModeConfigured());
        assertEquals(2, modeNormal.getAndroidAudioConfig().getRingerMode());
        TraceEnvironmentConfig modeInCall = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"mode\":2}}}");
        assertTrue(modeInCall.getAndroidAudioConfig().isModeConfigured());
        assertEquals(2, modeInCall.getAndroidAudioConfig().getMode());
        TraceEnvironmentConfig modeAssistant = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"mode\":7}}}");
        assertTrue(modeAssistant.getAndroidAudioConfig().isModeConfigured());
        assertEquals(7, modeAssistant.getAndroidAudioConfig().getMode());

        // all four fields independent
        TraceEnvironmentConfig allFour = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"musicActive\":true,\"speakerphoneOn\":false,"
                        + "\"ringerMode\":1,\"mode\":3}}}");
        assertTrue(allFour.getAndroidAudioConfig().isMusicActive());
        assertFalse(allFour.getAndroidAudioConfig().isSpeakerphoneOn());
        assertEquals(1, allFour.getAndroidAudioConfig().getRingerMode());
        assertTrue(allFour.getAndroidAudioConfig().isRingerModeConfigured());
        assertEquals(3, allFour.getAndroidAudioConfig().getMode());
        assertTrue(allFour.getAndroidAudioConfig().isModeConfigured());

        // immutable / stable access
        assertEquals(t.isMusicActive(), audioTrue.getAndroidAudioConfig().isMusicActive());
        assertEquals(2, audioTrue.getAndroidAudioConfig().getRingerMode());
        assertEquals(0, audioTrue.getAndroidAudioConfig().getMode());
        assertNotNull(audioTrue.getAndroidAudioConfig());

        // coexist with clipboard
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"audio\":{\"musicActive\":true,\"speakerphoneOn\":true,\"ringerMode\":0,\"mode\":7},"
                + "\"clipboard\":{\"hasPrimaryClip\":true}"
                + "}}"
        );
        assertTrue(both.isAndroidAudioConfigured());
        assertTrue(both.getAndroidAudioConfig().isMusicActive());
        assertTrue(both.getAndroidAudioConfig().isSpeakerphoneOn());
        assertEquals(0, both.getAndroidAudioConfig().getRingerMode());
        assertEquals(7, both.getAndroidAudioConfig().getMode());
        assertTrue(both.isAndroidClipboardConfigured());
        assertTrue(both.getAndroidClipboardConfig().hasPrimaryClip());

        // wrong node type
        assertInvalid("{\"android\":{\"audio\":[]}}", "android.audio");
        assertInvalid("{\"android\":{\"audio\":1}}", "android.audio");
        assertInvalid("{\"android\":{\"audio\":\"yes\"}}", "android.audio");

        // unknown keys
        assertInvalid("{\"android\":{\"audio\":{\"volume\":1}}}",
                "android.audio.volume");
        assertInvalid("{\"android\":{\"audio\":{\"extra\":true}}}",
                "android.audio.extra");

        // musicActive wrong types
        assertInvalid("{\"android\":{\"audio\":{\"musicActive\":null}}}",
                "android.audio.musicActive");
        assertInvalid("{\"android\":{\"audio\":{\"musicActive\":\"true\"}}}",
                "android.audio.musicActive");
        assertInvalid("{\"android\":{\"audio\":{\"musicActive\":1}}}",
                "android.audio.musicActive");

        // speakerphoneOn wrong types
        assertInvalid("{\"android\":{\"audio\":{\"speakerphoneOn\":null}}}",
                "android.audio.speakerphoneOn");
        assertInvalid("{\"android\":{\"audio\":{\"speakerphoneOn\":\"true\"}}}",
                "android.audio.speakerphoneOn");
        assertInvalid("{\"android\":{\"audio\":{\"speakerphoneOn\":1}}}",
                "android.audio.speakerphoneOn");

        // ringerMode wrong types / non-integral / out of range
        assertInvalid("{\"android\":{\"audio\":{\"ringerMode\":null}}}",
                "android.audio.ringerMode");
        assertInvalid("{\"android\":{\"audio\":{\"ringerMode\":\"2\"}}}",
                "android.audio.ringerMode");
        assertInvalid("{\"android\":{\"audio\":{\"ringerMode\":true}}}",
                "android.audio.ringerMode");
        assertInvalid("{\"android\":{\"audio\":{\"ringerMode\":1.5}}}",
                "android.audio.ringerMode");
        assertInvalid("{\"android\":{\"audio\":{\"ringerMode\":-1}}}",
                "android.audio.ringerMode");
        assertInvalid("{\"android\":{\"audio\":{\"ringerMode\":3}}}",
                "android.audio.ringerMode");

        // mode wrong types / non-integral / out of range including -1/-2
        assertInvalid("{\"android\":{\"audio\":{\"mode\":null}}}",
                "android.audio.mode");
        assertInvalid("{\"android\":{\"audio\":{\"mode\":\"0\"}}}",
                "android.audio.mode");
        assertInvalid("{\"android\":{\"audio\":{\"mode\":true}}}",
                "android.audio.mode");
        assertInvalid("{\"android\":{\"audio\":{\"mode\":1.5}}}",
                "android.audio.mode");
        assertInvalid("{\"android\":{\"audio\":{\"mode\":-1}}}",
                "android.audio.mode");
        assertInvalid("{\"android\":{\"audio\":{\"mode\":-2}}}",
                "android.audio.mode");
        assertInvalid("{\"android\":{\"audio\":{\"mode\":8}}}",
                "android.audio.mode");

        // properties: nonempty String keys → String or explicit null; independent of four fields
        TraceEnvironmentConfig props = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"audio\":{\"properties\":{"
                + "\"android.media.property.OUTPUT_SAMPLE_RATE\":\"48000\","
                + "\"android.media.property.OUTPUT_FRAMES_PER_BUFFER\":null,"
                + "\"custom.empty\":\"\""
                + "}}}}"
        );
        TraceEnvironmentConfig.AndroidAudioConfig pc = props.getAndroidAudioConfig();
        assertTrue(pc.isPropertiesConfigured());
        assertEquals(3, pc.getProperties().size());
        assertTrue(pc.isPropertyConfigured("android.media.property.OUTPUT_SAMPLE_RATE"));
        assertEquals("48000", pc.getProperty("android.media.property.OUTPUT_SAMPLE_RATE"));
        assertTrue(pc.isPropertyConfigured("android.media.property.OUTPUT_FRAMES_PER_BUFFER"));
        assertNull(pc.getProperty("android.media.property.OUTPUT_FRAMES_PER_BUFFER"));
        assertTrue(pc.isPropertyConfigured("custom.empty"));
        assertEquals("", pc.getProperty("custom.empty"));
        assertFalse(pc.isPropertyConfigured("absent.key"));
        assertNull(pc.getProperty("absent.key"));
        // four scalar fields keep defaults when only properties set
        assertFalse(pc.isMusicActiveConfigured());
        assertFalse(pc.isMusicActive());
        assertEquals(2, pc.getRingerMode());
        assertEquals(0, pc.getMode());

        // explicit empty properties object
        TraceEnvironmentConfig propsEmpty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"properties\":{}}}}");
        assertTrue(propsEmpty.getAndroidAudioConfig().isPropertiesConfigured());
        assertTrue(propsEmpty.getAndroidAudioConfig().getProperties().isEmpty());

        // immutable map view
        try {
            pc.getProperties().put("x", "y");
            fail("expected UnsupportedOperationException for properties map mutation");
        } catch (UnsupportedOperationException expected) {
            // ok
        }

        // coexist with four fields
        TraceEnvironmentConfig propsWithFields = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"audio\":{"
                + "\"musicActive\":true,"
                + "\"properties\":{\"k\":\"v\"}"
                + "}}}"
        );
        assertTrue(propsWithFields.getAndroidAudioConfig().isMusicActive());
        assertEquals("v", propsWithFields.getAndroidAudioConfig().getProperty("k"));

        // malformed properties
        assertInvalid("{\"android\":{\"audio\":{\"properties\":[]}}}",
                "android.audio.properties");
        assertInvalid("{\"android\":{\"audio\":{\"properties\":1}}}",
                "android.audio.properties");
        assertInvalid("{\"android\":{\"audio\":{\"properties\":\"x\"}}}",
                "android.audio.properties");
        assertInvalid("{\"android\":{\"audio\":{\"properties\":{\"\": \"x\"}}}}",
                "android.audio.properties.");
        assertInvalid("{\"android\":{\"audio\":{\"properties\":{\"k\":1}}}}",
                "android.audio.properties.k");
        assertInvalid("{\"android\":{\"audio\":{\"properties\":{\"k\":true}}}}",
                "android.audio.properties.k");
        assertInvalid("{\"android\":{\"audio\":{\"properties\":{\"k\":{}}}}}",
                "android.audio.properties.k");
        assertInvalid("{\"android\":{\"audio\":{\"properties\":{\"k\":[]}}}}",
                "android.audio.properties.k");

        // streamVolumes independent of other audio fields
        TraceEnvironmentConfig emptyAudio = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{}}}");
        assertFalse(emptyAudio.getAndroidAudioConfig().isStreamVolumesConfigured());
        assertTrue(emptyAudio.getAndroidAudioConfig().getStreamVolumes().isEmpty());

        TraceEnvironmentConfig emptyVolumes = TraceEnvironmentConfig.parse(
                "{\"android\":{\"audio\":{\"streamVolumes\":[]}}}");
        assertTrue(emptyVolumes.getAndroidAudioConfig().isStreamVolumesConfigured());
        assertTrue(emptyVolumes.getAndroidAudioConfig().getStreamVolumes().isEmpty());
        assertNull(emptyVolumes.getAndroidAudioConfig().findStreamVolume(3));

        TraceEnvironmentConfig volumes = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"audio\":{\"streamVolumes\":["
                + "{\"streamType\":3,\"volume\":7,\"maxVolume\":15},"
                + "{\"streamType\":0,\"volume\":0,\"maxVolume\":7}"
                + "]}}}"
        );
        TraceEnvironmentConfig.AndroidAudioConfig av = volumes.getAndroidAudioConfig();
        assertTrue(av.isStreamVolumesConfigured());
        assertEquals(2, av.getStreamVolumes().size());
        assertEquals(3, av.getStreamVolumes().get(0).getStreamType());
        assertEquals(7, av.getStreamVolumes().get(0).getVolume());
        assertEquals(15, av.getStreamVolumes().get(0).getMaxVolume());
        assertFalse(av.getStreamVolumes().get(0).isMinVolumeConfigured());
        assertEquals(0, av.getStreamVolumes().get(1).getStreamType());
        assertEquals(0, av.getStreamVolumes().get(1).getVolume());
        assertEquals(7, av.getStreamVolumes().get(1).getMaxVolume());
        assertFalse(av.getStreamVolumes().get(1).isMinVolumeConfigured());
        assertNotNull(av.findStreamVolume(3));
        assertEquals(7, av.findStreamVolume(3).getVolume());
        assertNull(av.findStreamVolume(1));
        // scalars remain defaults when only streamVolumes set
        assertFalse(av.isMusicActiveConfigured());
        assertEquals(2, av.getRingerMode());
        assertEquals(0, av.getMode());
        assertFalse(av.isPropertiesConfigured());

        // volume == maxVolume allowed; streamType 0 allowed
        TraceEnvironmentConfig boundsOk = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"audio\":{\"streamVolumes\":["
                + "{\"streamType\":0,\"volume\":5,\"maxVolume\":5}"
                + "]}}}"
        );
        assertEquals(5, boundsOk.getAndroidAudioConfig().getStreamVolumes().get(0).getVolume());
        assertEquals(5, boundsOk.getAndroidAudioConfig().getStreamVolumes().get(0).getMaxVolume());
        assertFalse(boundsOk.getAndroidAudioConfig().getStreamVolumes().get(0).isMinVolumeConfigured());

        TraceEnvironmentConfig withMin = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"audio\":{\"streamVolumes\":["
                + "{\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":0},"
                + "{\"streamType\":0,\"volume\":4,\"maxVolume\":7}"
                + "]}}}"
        );
        assertTrue(withMin.getAndroidAudioConfig().getStreamVolumes().get(0).isMinVolumeConfigured());
        assertEquals(0, withMin.getAndroidAudioConfig().getStreamVolumes().get(0).getMinVolume());
        assertFalse(withMin.getAndroidAudioConfig().getStreamVolumes().get(1).isMinVolumeConfigured());
        assertEquals(7, withMin.getAndroidAudioConfig().getStreamVolumes().get(0).getVolume());
        assertEquals(15, withMin.getAndroidAudioConfig().getStreamVolumes().get(0).getMaxVolume());

        // immutability
        try {
            volumes.getAndroidAudioConfig().getStreamVolumes().clear();
            fail("expected UnsupportedOperationException for streamVolumes list");
        } catch (UnsupportedOperationException expected) {
            // ok
        }

        // coexist with other audio fields
        TraceEnvironmentConfig mixed = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"audio\":{"
                + "\"musicActive\":true,"
                + "\"streamVolumes\":[{\"streamType\":3,\"volume\":1,\"maxVolume\":15}]"
                + "}}}"
        );
        assertTrue(mixed.getAndroidAudioConfig().isMusicActive());
        assertTrue(mixed.getAndroidAudioConfig().isStreamVolumesConfigured());
        assertEquals(1, mixed.getAndroidAudioConfig().getStreamVolumes().size());

        // invalid streamVolumes node / entries
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":{}}}}",
                "android.audio.streamVolumes");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":1}}}",
                "android.audio.streamVolumes");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":null}}}",
                "android.audio.streamVolumes");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[1]}}}",
                "android.audio.streamVolumes[0]");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{}]}}}",
                "android.audio.streamVolumes[0].streamType");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"volume\":1,\"maxVolume\":2}]}}}",
                "android.audio.streamVolumes[0].streamType");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":3,\"maxVolume\":2}]}}}",
                "android.audio.streamVolumes[0].volume");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":3,\"volume\":1}]}}}",
                "android.audio.streamVolumes[0].maxVolume");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":-1,\"volume\":0,\"maxVolume\":1}]}}}",
                "android.audio.streamVolumes[0].streamType");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":3,\"volume\":-1,\"maxVolume\":1}]}}}",
                "android.audio.streamVolumes[0].volume");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":3,\"volume\":0,\"maxVolume\":-1}]}}}",
                "android.audio.streamVolumes[0].maxVolume");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":3,\"volume\":8,\"maxVolume\":7}]}}}",
                "android.audio.streamVolumes[0].volume");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":1.5,\"volume\":0,\"maxVolume\":1}]}}}",
                "android.audio.streamVolumes[0].streamType");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":\"3\",\"volume\":0,\"maxVolume\":1}]}}}",
                "android.audio.streamVolumes[0].streamType");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":3,\"volume\":0,\"maxVolume\":1},"
                        + "{\"streamType\":3,\"volume\":1,\"maxVolume\":2}]}}}",
                "android.audio.streamVolumes[1].streamType");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":3,\"volume\":0,\"maxVolume\":1,\"extra\":1}]}}}",
                "android.audio.streamVolumes[0].extra");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":8}]}}}",
                "android.audio.streamVolumes[0].minVolume");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":-1}]}}}",
                "android.audio.streamVolumes[0].minVolume");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":1.5}]}}}",
                "android.audio.streamVolumes[0].minVolume");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":\"0\"}]}}}",
                "android.audio.streamVolumes[0].minVolume");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":null}]}}}",
                "android.audio.streamVolumes[0].minVolume");
        assertInvalid("{\"android\":{\"audio\":{\"streamVolumes\":[{"
                        + "\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":[]}]}}}",
                "android.audio.streamVolumes[0].minVolume");
    }

    @Test
    public void testAndroidLocationProvidersConfig() {
        // missing android / location / providers
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidLocationConfigured());
        assertNull(missing.getAndroidLocationConfig());
        assertFalse(missing.isAndroidLocationProvidersConfigured());
        assertNull(missing.getAndroidLocationProvidersConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidLocationConfigured());
        assertFalse(missingAndroid.isAndroidLocationProvidersConfigured());
        assertNull(missingAndroid.getAndroidLocationProvidersConfig());

        // location present without providers → enabled default false; providers unconfigured
        TraceEnvironmentConfig locationEmpty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"location\":{}}}");
        assertTrue(locationEmpty.isAndroidLocationConfigured());
        assertNotNull(locationEmpty.getAndroidLocationConfig());
        assertFalse(locationEmpty.getAndroidLocationConfig().isEnabledConfigured());
        assertFalse(locationEmpty.getAndroidLocationConfig().isEnabled());
        assertFalse(locationEmpty.isAndroidLocationProvidersConfigured());
        assertNull(locationEmpty.getAndroidLocationProvidersConfig());

        // enabled explicit true without providers
        TraceEnvironmentConfig enabledOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"location\":{\"enabled\":true}}}");
        assertTrue(enabledOnly.isAndroidLocationConfigured());
        assertTrue(enabledOnly.getAndroidLocationConfig().isEnabledConfigured());
        assertTrue(enabledOnly.getAndroidLocationConfig().isEnabled());
        assertFalse(enabledOnly.isAndroidLocationProvidersConfigured());

        // enabled explicit false
        TraceEnvironmentConfig enabledFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"location\":{\"enabled\":false}}}");
        assertTrue(enabledFalse.getAndroidLocationConfig().isEnabledConfigured());
        assertFalse(enabledFalse.getAndroidLocationConfig().isEnabled());

        // providers true does not infer location.enabled
        TraceEnvironmentConfig providersOnlyTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"location\":{\"providers\":{\"gps\":true}}}}");
        assertTrue(providersOnlyTrue.isAndroidLocationConfigured());
        assertFalse(providersOnlyTrue.getAndroidLocationConfig().isEnabled());
        assertFalse(providersOnlyTrue.getAndroidLocationConfig().isEnabledConfigured());
        assertTrue(providersOnlyTrue.isAndroidLocationProvidersConfigured());
        assertTrue(providersOnlyTrue.getAndroidLocationProvidersConfig().isGps());

        // explicit empty providers → all default false; parent enabled default false
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"location\":{\"providers\":{}}}}");
        assertTrue(empty.isAndroidLocationConfigured());
        assertFalse(empty.getAndroidLocationConfig().isEnabled());
        assertTrue(empty.isAndroidLocationProvidersConfigured());
        TraceEnvironmentConfig.AndroidLocationProvidersConfig e =
                empty.getAndroidLocationProvidersConfig();
        assertNotNull(e);
        assertFalse(e.isGpsConfigured());
        assertFalse(e.isGps());
        assertFalse(e.isNetworkConfigured());
        assertFalse(e.isNetwork());
        assertFalse(e.isPassiveConfigured());
        assertFalse(e.isPassive());

        // full explicit values with enabled true
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse(
                "{\"android\":{\"location\":{\"enabled\":true,\"providers\":{"
                        + "\"gps\":true,\"network\":false,\"passive\":true}}}}");
        assertTrue(full.isAndroidLocationConfigured());
        assertTrue(full.getAndroidLocationConfig().isEnabled());
        assertTrue(full.isAndroidLocationProvidersConfigured());
        TraceEnvironmentConfig.AndroidLocationProvidersConfig p =
                full.getAndroidLocationProvidersConfig();
        assertTrue(p.isGpsConfigured());
        assertTrue(p.isGps());
        assertTrue(p.isNetworkConfigured());
        assertFalse(p.isNetwork());
        assertTrue(p.isPassiveConfigured());
        assertTrue(p.isPassive());

        // partial: only gps true, others default false
        TraceEnvironmentConfig partial = TraceEnvironmentConfig.parse(
                "{\"android\":{\"location\":{\"providers\":{\"gps\":true}}}}");
        TraceEnvironmentConfig.AndroidLocationProvidersConfig part =
                partial.getAndroidLocationProvidersConfig();
        assertTrue(part.isGpsConfigured());
        assertTrue(part.isGps());
        assertFalse(part.isNetworkConfigured());
        assertFalse(part.isNetwork());
        assertFalse(part.isPassiveConfigured());
        assertFalse(part.isPassive());

        // immutable / stable access
        assertEquals(p.isGps(), full.getAndroidLocationProvidersConfig().isGps());
        assertEquals(full.getAndroidLocationConfig().isEnabled(),
                full.getAndroidLocationConfig().isEnabled());
        assertNotNull(full.getAndroidLocationProvidersConfig());

        // coexist with clipboard
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"location\":{\"enabled\":true,\"providers\":{\"network\":true}},"
                + "\"clipboard\":{\"hasPrimaryClip\":true}"
                + "}}"
        );
        assertTrue(both.isAndroidLocationConfigured());
        assertTrue(both.getAndroidLocationConfig().isEnabled());
        assertTrue(both.isAndroidLocationProvidersConfigured());
        assertTrue(both.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(both.isAndroidClipboardConfigured());
        assertTrue(both.getAndroidClipboardConfig().hasPrimaryClip());

        // wrong location / providers node type
        assertInvalid("{\"android\":{\"location\":[]}}", "android.location");
        assertInvalid("{\"android\":{\"location\":1}}", "android.location");
        assertInvalid("{\"android\":{\"location\":\"here\"}}", "android.location");
        assertInvalid("{\"android\":{\"location\":{\"providers\":[]}}}",
                "android.location.providers");
        assertInvalid("{\"android\":{\"location\":{\"providers\":1}}}",
                "android.location.providers");
        assertInvalid("{\"android\":{\"location\":{\"providers\":\"gps\"}}}",
                "android.location.providers");
        assertInvalid("{\"android\":{\"location\":{\"providers\":null}}}",
                "android.location.providers");

        // unknown keys under location / providers
        assertInvalid("{\"android\":{\"location\":{\"latitude\":1.0}}}",
                "android.location.latitude");
        assertInvalid("{\"android\":{\"location\":{\"providers\":{\"fused\":true}}}}",
                "android.location.providers.fused");
        assertInvalid("{\"android\":{\"location\":{\"providers\":{\"extra\":1}}}}",
                "android.location.providers.extra");

        // enabled wrong types
        assertInvalid("{\"android\":{\"location\":{\"enabled\":null}}}",
                "android.location.enabled");
        assertInvalid("{\"android\":{\"location\":{\"enabled\":\"true\"}}}",
                "android.location.enabled");
        assertInvalid("{\"android\":{\"location\":{\"enabled\":1}}}",
                "android.location.enabled");

        // non-boolean provider values
        assertInvalid("{\"android\":{\"location\":{\"providers\":{\"gps\":null}}}}",
                "android.location.providers.gps");
        assertInvalid("{\"android\":{\"location\":{\"providers\":{\"gps\":\"true\"}}}}",
                "android.location.providers.gps");
        assertInvalid("{\"android\":{\"location\":{\"providers\":{\"gps\":1}}}}",
                "android.location.providers.gps");
        assertInvalid("{\"android\":{\"location\":{\"providers\":{\"network\":null}}}}",
                "android.location.providers.network");
        assertInvalid("{\"android\":{\"location\":{\"providers\":{\"passive\":\"false\"}}}}",
                "android.location.providers.passive");
    }

    @Test
    public void testAndroidLocationLastKnownLocationsConfig() {
        // missing key
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse(
                "{\"android\":{\"location\":{}}}");
        assertTrue(missing.isAndroidLocationConfigured());
        assertFalse(missing.isAndroidLocationLastKnownLocationsConfigured());
        assertTrue(missing.getAndroidLocationLastKnownLocations().isEmpty());

        TraceEnvironmentConfig noLocation = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(noLocation.isAndroidLocationLastKnownLocationsConfigured());
        assertTrue(noLocation.getAndroidLocationLastKnownLocations().isEmpty());

        // empty array configured
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"location\":{\"lastKnownLocations\":[]}}}");
        assertTrue(empty.isAndroidLocationLastKnownLocationsConfigured());
        assertTrue(empty.getAndroidLocationLastKnownLocations().isEmpty());

        // full entry + optional fields
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"location\":{\"lastKnownLocations\":[{"
                + "\"provider\":\"gps\","
                + "\"latitude\":31.2304,"
                + "\"longitude\":121.4737,"
                + "\"altitude\":12.5,"
                + "\"accuracyMeters\":8.0,"
                + "\"timeMillis\":1700000000000,"
                + "\"elapsedRealtimeNanos\":9000000000000,"
                + "\"mock\":false,"
                + "\"speedMetersPerSecond\":1.5,"
                + "\"bearingDegrees\":90.0,"
                + "\"verticalAccuracyMeters\":2.5,"
                + "\"speedAccuracyMetersPerSecond\":0.25,"
                + "\"bearingAccuracyDegrees\":5.0"
                + "},{"
                + "\"provider\":\"network\","
                + "\"latitude\":-33.8688,"
                + "\"longitude\":151.2093,"
                + "\"mock\":true"
                + "}]}}"
                + "}");
        assertTrue(full.isAndroidLocationLastKnownLocationsConfigured());
        List<TraceEnvironmentConfig.AndroidLastKnownLocationConfig> list =
                full.getAndroidLocationLastKnownLocations();
        assertEquals(2, list.size());
        TraceEnvironmentConfig.AndroidLastKnownLocationConfig gps = list.get(0);
        assertEquals("gps", gps.getProvider());
        assertEquals(31.2304d, gps.getLatitude(), 0.0d);
        assertEquals(121.4737d, gps.getLongitude(), 0.0d);
        assertTrue(gps.isAltitudeConfigured());
        assertEquals(12.5d, gps.getAltitude(), 0.0d);
        assertTrue(gps.isAccuracyMetersConfigured());
        assertEquals(8.0f, gps.getAccuracyMeters(), 0.0f);
        assertTrue(gps.isTimeMillisConfigured());
        assertEquals(1700000000000L, gps.getTimeMillis());
        assertTrue(gps.isElapsedRealtimeNanosConfigured());
        assertEquals(9000000000000L, gps.getElapsedRealtimeNanos());
        assertTrue(gps.isMockConfigured());
        assertFalse(gps.isMock());
        assertTrue(gps.isSpeedMetersPerSecondConfigured());
        assertEquals(1.5f, gps.getSpeedMetersPerSecond(), 0.0f);
        assertTrue(gps.isBearingDegreesConfigured());
        assertEquals(90.0f, gps.getBearingDegrees(), 0.0f);
        assertTrue(gps.isVerticalAccuracyMetersConfigured());
        assertEquals(2.5f, gps.getVerticalAccuracyMeters(), 0.0f);
        assertTrue(gps.isSpeedAccuracyMetersPerSecondConfigured());
        assertEquals(0.25f, gps.getSpeedAccuracyMetersPerSecond(), 0.0f);
        assertTrue(gps.isBearingAccuracyDegreesConfigured());
        assertEquals(5.0f, gps.getBearingAccuracyDegrees(), 0.0f);

        TraceEnvironmentConfig.AndroidLastKnownLocationConfig network = list.get(1);
        assertEquals("network", network.getProvider());
        assertEquals(-33.8688d, network.getLatitude(), 0.0d);
        assertEquals(151.2093d, network.getLongitude(), 0.0d);
        assertFalse(network.isAltitudeConfigured());
        assertEquals(0.0d, network.getAltitude(), 0.0d);
        assertFalse(network.isAccuracyMetersConfigured());
        assertEquals(0.0f, network.getAccuracyMeters(), 0.0f);
        assertFalse(network.isTimeMillisConfigured());
        assertEquals(0L, network.getTimeMillis());
        assertFalse(network.isElapsedRealtimeNanosConfigured());
        assertEquals(0L, network.getElapsedRealtimeNanos());
        assertTrue(network.isMockConfigured());
        assertTrue(network.isMock());
        assertFalse(network.isSpeedMetersPerSecondConfigured());
        assertEquals(0.0f, network.getSpeedMetersPerSecond(), 0.0f);
        assertFalse(network.isBearingDegreesConfigured());
        assertEquals(0.0f, network.getBearingDegrees(), 0.0f);
        assertFalse(network.isVerticalAccuracyMetersConfigured());
        assertEquals(0.0f, network.getVerticalAccuracyMeters(), 0.0f);
        assertFalse(network.isSpeedAccuracyMetersPerSecondConfigured());
        assertEquals(0.0f, network.getSpeedAccuracyMetersPerSecond(), 0.0f);
        assertFalse(network.isBearingAccuracyDegreesConfigured());
        assertEquals(0.0f, network.getBearingAccuracyDegrees(), 0.0f);

        // independent of enabled / providers
        TraceEnvironmentConfig onlyLast = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"location\":{\"lastKnownLocations\":[{"
                + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0}]}}}");
        assertTrue(onlyLast.isAndroidLocationLastKnownLocationsConfigured());
        assertFalse(onlyLast.getAndroidLocationConfig().isEnabled());
        assertFalse(onlyLast.isAndroidLocationProvidersConfigured());

        // immutability
        try {
            full.getAndroidLocationLastKnownLocations().clear();
            fail("expected UnsupportedOperationException for lastKnownLocations list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }

        // wrong array type
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":{}}}}",
                "android.location.lastKnownLocations");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":1}}}",
                "android.location.lastKnownLocations");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":null}}}",
                "android.location.lastKnownLocations");

        // entry not object
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[1]}}}",
                "android.location.lastKnownLocations[0]");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[\"gps\"]}}}",
                "android.location.lastKnownLocations[0]");

        // required fields
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{}]}}}",
                "android.location.lastKnownLocations[0].provider");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"latitude\":1,\"longitude\":1}]}}}",
                "android.location.lastKnownLocations[0].provider");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"longitude\":1}]}}}",
                "android.location.lastKnownLocations[0].latitude");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":1}]}}}",
                "android.location.lastKnownLocations[0].longitude");

        // provider validation
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"\",\"latitude\":0,\"longitude\":0}]}}}",
                "android.location.lastKnownLocations[0].provider");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":null,\"latitude\":0,\"longitude\":0}]}}}",
                "android.location.lastKnownLocations[0].provider");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":1,\"latitude\":0,\"longitude\":0}]}}}",
                "android.location.lastKnownLocations[0].provider");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"a\\nb\",\"latitude\":0,\"longitude\":0}]}}}",
                "android.location.lastKnownLocations[0].provider");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0},{"
                        + "\"provider\":\"gps\",\"latitude\":1,\"longitude\":1}]}}}",
                "android.location.lastKnownLocations[1].provider");

        // latitude / longitude bounds and types
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":90.1,\"longitude\":0}]}}}",
                "android.location.lastKnownLocations[0].latitude");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":-90.1,\"longitude\":0}]}}}",
                "android.location.lastKnownLocations[0].latitude");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":180.1}]}}}",
                "android.location.lastKnownLocations[0].longitude");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":-180.1}]}}}",
                "android.location.lastKnownLocations[0].longitude");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":\"0\",\"longitude\":0}]}}}",
                "android.location.lastKnownLocations[0].latitude");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":true,\"longitude\":0}]}}}",
                "android.location.lastKnownLocations[0].latitude");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":null,\"longitude\":0}]}}}",
                "android.location.lastKnownLocations[0].latitude");

        // optional field invalid
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"altitude\":\"1\"}]}}}",
                "android.location.lastKnownLocations[0].altitude");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"accuracyMeters\":-0.1}]}}}",
                "android.location.lastKnownLocations[0].accuracyMeters");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"timeMillis\":-1}]}}}",
                "android.location.lastKnownLocations[0].timeMillis");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"timeMillis\":1.5}]}}}",
                "android.location.lastKnownLocations[0].timeMillis");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"elapsedRealtimeNanos\":-1}]}}}",
                "android.location.lastKnownLocations[0].elapsedRealtimeNanos");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"mock\":1}]}}}",
                "android.location.lastKnownLocations[0].mock");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"speedMetersPerSecond\":-0.1}]}}}",
                "android.location.lastKnownLocations[0].speedMetersPerSecond");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"speedMetersPerSecond\":\"1\"}]}}}",
                "android.location.lastKnownLocations[0].speedMetersPerSecond");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"bearingDegrees\":360}]}}}",
                "android.location.lastKnownLocations[0].bearingDegrees");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"bearingDegrees\":-0.1}]}}}",
                "android.location.lastKnownLocations[0].bearingDegrees");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"bearingDegrees\":true}]}}}",
                "android.location.lastKnownLocations[0].bearingDegrees");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"verticalAccuracyMeters\":-0.1}]}}}",
                "android.location.lastKnownLocations[0].verticalAccuracyMeters");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"verticalAccuracyMeters\":\"1\"}]}}}",
                "android.location.lastKnownLocations[0].verticalAccuracyMeters");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"speedAccuracyMetersPerSecond\":-1}]}}}",
                "android.location.lastKnownLocations[0].speedAccuracyMetersPerSecond");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"speedAccuracyMetersPerSecond\":true}]}}}",
                "android.location.lastKnownLocations[0].speedAccuracyMetersPerSecond");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"bearingAccuracyDegrees\":-0.01}]}}}",
                "android.location.lastKnownLocations[0].bearingAccuracyDegrees");
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"bearingAccuracyDegrees\":null}]}}}",
                "android.location.lastKnownLocations[0].bearingAccuracyDegrees");

        // unknown entry key
        assertInvalid("{\"android\":{\"location\":{\"lastKnownLocations\":[{"
                        + "\"provider\":\"gps\",\"latitude\":0,\"longitude\":0,"
                        + "\"speed\":1}]}}}",
                "android.location.lastKnownLocations[0].speed");

        // boundary ok
        TraceEnvironmentConfig bounds = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"location\":{\"lastKnownLocations\":[{"
                + "\"provider\":\"gps\",\"latitude\":90,\"longitude\":180,"
                + "\"speedMetersPerSecond\":0,\"bearingDegrees\":0},{"
                + "\"provider\":\"network\",\"latitude\":-90,\"longitude\":-180,"
                + "\"speedMetersPerSecond\":0,\"bearingDegrees\":359.999}]}}}");
        assertEquals(90.0d, bounds.getAndroidLocationLastKnownLocations().get(0).getLatitude(), 0.0d);
        assertEquals(180.0d, bounds.getAndroidLocationLastKnownLocations().get(0).getLongitude(), 0.0d);
        assertEquals(-90.0d, bounds.getAndroidLocationLastKnownLocations().get(1).getLatitude(), 0.0d);
        assertEquals(-180.0d, bounds.getAndroidLocationLastKnownLocations().get(1).getLongitude(), 0.0d);
        assertTrue(bounds.getAndroidLocationLastKnownLocations().get(0)
                .isSpeedMetersPerSecondConfigured());
        assertEquals(0.0f, bounds.getAndroidLocationLastKnownLocations().get(0)
                .getSpeedMetersPerSecond(), 0.0f);
        assertEquals(0.0f, bounds.getAndroidLocationLastKnownLocations().get(0)
                .getBearingDegrees(), 0.0f);
        assertEquals(359.999f, bounds.getAndroidLocationLastKnownLocations().get(1)
                .getBearingDegrees(), 0.0f);
    }

    @Test
    public void testAndroidAccountsConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidAccountsConfigured());
        assertTrue(missing.getAndroidAccounts().isEmpty());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidAccountsConfigured());
        assertTrue(missingAndroid.getAndroidAccounts().isEmpty());

        // explicit empty array configured
        TraceEnvironmentConfig emptyArr = TraceEnvironmentConfig.parse(
                "{\"android\":{\"accounts\":[]}}");
        assertTrue(emptyArr.isAndroidAccountsConfigured());
        assertTrue(emptyArr.getAndroidAccounts().isEmpty());

        // full list, order preserved
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"accounts\":["
                + "{\"name\":\"alice@demo.com\",\"type\":\"com.google\"},"
                + "{\"name\":\"bob\",\"type\":\"com.demo.account\"}"
                + "]}}"
        );
        assertTrue(full.isAndroidAccountsConfigured());
        List<TraceEnvironmentConfig.AndroidAccountConfig> list = full.getAndroidAccounts();
        assertEquals(2, list.size());
        assertEquals("alice@demo.com", list.get(0).getName());
        assertEquals("com.google", list.get(0).getType());
        assertEquals("bob", list.get(1).getName());
        assertEquals("com.demo.account", list.get(1).getType());

        // immutability
        try {
            full.getAndroidAccounts().clear();
            fail("expected UnsupportedOperationException for accounts list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        try {
            emptyArr.getAndroidAccounts().add(list.get(0));
            fail("expected UnsupportedOperationException for empty accounts list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }

        // wrong node type
        assertInvalid("{\"android\":{\"accounts\":{}}}", "android.accounts");
        assertInvalid("{\"android\":{\"accounts\":1}}", "android.accounts");
        assertInvalid("{\"android\":{\"accounts\":\"x\"}}", "android.accounts");
        assertInvalid("{\"android\":{\"accounts\":[\"alice\"]}}", "android.accounts[0]");

        // element must have exactly name and type (nonempty String)
        assertInvalid("{\"android\":{\"accounts\":[{\"type\":\"com.google\"}]}}",
                "android.accounts[0].name");
        assertInvalid("{\"android\":{\"accounts\":[{\"name\":\"alice\"}]}}",
                "android.accounts[0].type");
        assertInvalid("{\"android\":{\"accounts\":[{\"name\":\"\",\"type\":\"com.google\"}]}}",
                "android.accounts[0].name");
        assertInvalid("{\"android\":{\"accounts\":[{\"name\":\"alice\",\"type\":\"\"}]}}",
                "android.accounts[0].type");
        assertInvalid("{\"android\":{\"accounts\":[{\"name\":null,\"type\":\"com.google\"}]}}",
                "android.accounts[0].name");
        assertInvalid("{\"android\":{\"accounts\":[{\"name\":1,\"type\":\"com.google\"}]}}",
                "android.accounts[0].name");
        assertInvalid("{\"android\":{\"accounts\":[{\"name\":\"alice\",\"type\":true}]}}",
                "android.accounts[0].type");
        assertInvalid("{\"android\":{\"accounts\":[{\"name\":\"alice\",\"type\":\"com.google\","
                        + "\"extra\":1}]}}",
                "android.accounts[0].extra");
    }

    @Test
    public void testAndroidInputMethodsConfig() {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidInputMethodsConfigured());
        assertTrue(missing.getAndroidInputMethods().isEmpty());

        TraceEnvironmentConfig emptyArr = TraceEnvironmentConfig.parse(
                "{\"android\":{\"inputMethods\":[]}}");
        assertTrue(emptyArr.isAndroidInputMethodsConfigured());
        assertTrue(emptyArr.getAndroidInputMethods().isEmpty());

        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"inputMethods\":["
                + "{\"id\":\"com.demo/.ImeA\",\"enabled\":true},"
                + "{\"id\":\"com.demo/.ImeB\",\"enabled\":false}"
                + "]}}"
        );
        assertTrue(full.isAndroidInputMethodsConfigured());
        List<TraceEnvironmentConfig.AndroidInputMethodConfig> list = full.getAndroidInputMethods();
        assertEquals(2, list.size());
        assertEquals("com.demo/.ImeA", list.get(0).getId());
        assertTrue(list.get(0).isEnabled());
        assertEquals("com.demo/.ImeB", list.get(1).getId());
        assertFalse(list.get(1).isEnabled());

        try {
            full.getAndroidInputMethods().clear();
            fail("expected UnsupportedOperationException for inputMethods list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }

        assertInvalid("{\"android\":{\"inputMethods\":{}}}", "android.inputMethods");
        assertInvalid("{\"android\":{\"inputMethods\":1}}", "android.inputMethods");
        assertInvalid("{\"android\":{\"inputMethods\":[\"x\"]}}", "android.inputMethods[0]");
        assertInvalid("{\"android\":{\"inputMethods\":[{\"enabled\":true}]}}",
                "android.inputMethods[0].id");
        assertInvalid("{\"android\":{\"inputMethods\":[{\"id\":\"com.demo/.Ime\"}]}}",
                "android.inputMethods[0].enabled");
        assertInvalid("{\"android\":{\"inputMethods\":[{\"id\":\"\",\"enabled\":true}]}}",
                "android.inputMethods[0].id");
        assertInvalid("{\"android\":{\"inputMethods\":[{\"id\":null,\"enabled\":true}]}}",
                "android.inputMethods[0].id");
        assertInvalid("{\"android\":{\"inputMethods\":[{\"id\":1,\"enabled\":true}]}}",
                "android.inputMethods[0].id");
        assertInvalid("{\"android\":{\"inputMethods\":[{\"id\":\"com.demo/.Ime\","
                        + "\"enabled\":1}]}}",
                "android.inputMethods[0].enabled");
        assertInvalid("{\"android\":{\"inputMethods\":[{\"id\":\"com.demo/.Ime\","
                        + "\"enabled\":null}]}}",
                "android.inputMethods[0].enabled");
        assertInvalid("{\"android\":{\"inputMethods\":[{\"id\":\"com.demo/.Ime\","
                        + "\"enabled\":true,\"extra\":1}]}}",
                "android.inputMethods[0].extra");
    }

    @Test
    public void testAndroidIdentifiersConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidIdentifiersConfigured());
        assertNull(missing.getAndroidIdentifiersConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidIdentifiersConfigured());
        assertNull(missingAndroid.getAndroidIdentifiersConfig());

        // explicit empty → defaults for advertising; androidId stays unconfigured (no default)
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"identifiers\":{}}}");
        assertTrue(empty.isAndroidIdentifiersConfigured());
        TraceEnvironmentConfig.AndroidIdentifiersConfig e = empty.getAndroidIdentifiersConfig();
        assertNotNull(e);
        assertFalse(e.isAdvertisingIdConfigured());
        assertEquals("00000000-0000-0000-0000-00000000a001", e.getAdvertisingId());
        assertFalse(e.isLimitAdTrackingConfigured());
        assertFalse(e.isLimitAdTracking());
        assertFalse(e.isAndroidIdConfigured());
        assertNull(e.getAndroidId());
        assertFalse(e.isAppSetIdConfigured());
        assertNull(e.getAppSetId());

        // full explicit values including androidId
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"identifiers\":{"
                + "\"advertisingId\":\"a1b2c3d4-e5f6-7890-abcd-ef1234567890\","
                + "\"limitAdTracking\":true,"
                + "\"androidId\":\"9774D56D682E549C\""
                + "}}}"
        );
        assertTrue(full.isAndroidIdentifiersConfigured());
        TraceEnvironmentConfig.AndroidIdentifiersConfig id = full.getAndroidIdentifiersConfig();
        assertTrue(id.isAdvertisingIdConfigured());
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", id.getAdvertisingId());
        assertTrue(id.isLimitAdTrackingConfigured());
        assertTrue(id.isLimitAdTracking());
        assertTrue(id.isAndroidIdConfigured());
        assertEquals("9774d56d682e549c", id.getAndroidId());

        // immutable / stable
        assertEquals(id.getAdvertisingId(), full.getAndroidIdentifiersConfig().getAdvertisingId());
        assertEquals(id.isLimitAdTracking(), full.getAndroidIdentifiersConfig().isLimitAdTracking());

        // partial: only advertisingId
        TraceEnvironmentConfig adOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"identifiers\":{"
                        + "\"advertisingId\":\"11111111-2222-3333-4444-555555555555\"}}}");
        assertTrue(adOnly.getAndroidIdentifiersConfig().isAdvertisingIdConfigured());
        assertEquals("11111111-2222-3333-4444-555555555555",
                adOnly.getAndroidIdentifiersConfig().getAdvertisingId());
        assertFalse(adOnly.getAndroidIdentifiersConfig().isLimitAdTrackingConfigured());
        assertFalse(adOnly.getAndroidIdentifiersConfig().isLimitAdTracking());

        // partial: only limitAdTracking
        TraceEnvironmentConfig latOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"identifiers\":{\"limitAdTracking\":true}}}");
        assertFalse(latOnly.getAndroidIdentifiersConfig().isAdvertisingIdConfigured());
        assertEquals("00000000-0000-0000-0000-00000000a001",
                latOnly.getAndroidIdentifiersConfig().getAdvertisingId());
        assertTrue(latOnly.getAndroidIdentifiersConfig().isLimitAdTrackingConfigured());
        assertTrue(latOnly.getAndroidIdentifiersConfig().isLimitAdTracking());

        // coexist with battery / power
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"identifiers\":{\"limitAdTracking\":true},"
                + "\"battery\":{\"capacityPercent\":88},"
                + "\"power\":{\"interactive\":false}"
                + "}}"
        );
        assertTrue(both.isAndroidIdentifiersConfigured());
        assertTrue(both.getAndroidIdentifiersConfig().isLimitAdTracking());
        assertTrue(both.isAndroidBatteryConfigured());
        assertEquals(88, both.getAndroidBatteryConfig().getCapacityPercent());
        assertTrue(both.isAndroidPowerConfigured());
        assertFalse(both.getAndroidPowerConfig().isInteractive());

        // wrong node type
        assertInvalid("{\"android\":{\"identifiers\":[]}}", "android.identifiers");
        assertInvalid("{\"android\":{\"identifiers\":1}}", "android.identifiers");
        assertInvalid("{\"android\":{\"identifiers\":\"x\"}}", "android.identifiers");

        // appSetId is an allowed optional key (not derived; default scope=1)
        TraceEnvironmentConfig appSetOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"identifiers\":{\"appSetId\":\"x\"}}}");
        assertTrue(appSetOnly.getAndroidIdentifiersConfig().isAppSetIdConfigured());
        assertEquals("x", appSetOnly.getAndroidIdentifiersConfig().getAppSetId());
        assertEquals(1, appSetOnly.getAndroidIdentifiersConfig().getAppSetScope());

        // unknown keys
        assertInvalid("{\"android\":{\"identifiers\":{\"extra\":true}}}",
                "android.identifiers.extra");

        // advertisingId invalid: null / empty / types / uppercase / non-canonical / illegal
        assertInvalid("{\"android\":{\"identifiers\":{\"advertisingId\":null}}}",
                "android.identifiers.advertisingId");
        assertInvalid("{\"android\":{\"identifiers\":{\"advertisingId\":\"\"}}}",
                "android.identifiers.advertisingId");
        assertInvalid("{\"android\":{\"identifiers\":{\"advertisingId\":true}}}",
                "android.identifiers.advertisingId");
        assertInvalid("{\"android\":{\"identifiers\":{\"advertisingId\":1}}}",
                "android.identifiers.advertisingId");
        assertInvalid("{\"android\":{\"identifiers\":{"
                        + "\"advertisingId\":\"A1B2C3D4-E5F6-7890-ABCD-EF1234567890\"}}}",
                "android.identifiers.advertisingId");
        assertInvalid("{\"android\":{\"identifiers\":{"
                        + "\"advertisingId\":\"a1b2c3d4e5f67890abcdef1234567890\"}}}",
                "android.identifiers.advertisingId");
        assertInvalid("{\"android\":{\"identifiers\":{\"advertisingId\":\"not-a-uuid\"}}}",
                "android.identifiers.advertisingId");
        assertInvalid("{\"android\":{\"identifiers\":{"
                        + "\"advertisingId\":\"a1b2c3d4-e5f6-7890-abcd-ef1234567890\\n\"}}}",
                "android.identifiers.advertisingId");

        // limitAdTracking wrong types
        assertInvalid("{\"android\":{\"identifiers\":{\"limitAdTracking\":null}}}",
                "android.identifiers.limitAdTracking");
        assertInvalid("{\"android\":{\"identifiers\":{\"limitAdTracking\":\"true\"}}}",
                "android.identifiers.limitAdTracking");
        assertInvalid("{\"android\":{\"identifiers\":{\"limitAdTracking\":1}}}",
                "android.identifiers.limitAdTracking");
        assertInvalid("{\"android\":{\"identifiers\":{\"limitAdTracking\":0}}}",
                "android.identifiers.limitAdTracking");

        // androidId: lowercase / uppercase normalize; absent vs configured
        TraceEnvironmentConfig androidIdLower = TraceEnvironmentConfig.parse(
                "{\"android\":{\"identifiers\":{\"androidId\":\"9774d56d682e549c\"}}}");
        assertTrue(androidIdLower.getAndroidIdentifiersConfig().isAndroidIdConfigured());
        assertEquals("9774d56d682e549c",
                androidIdLower.getAndroidIdentifiersConfig().getAndroidId());
        TraceEnvironmentConfig androidIdUpper = TraceEnvironmentConfig.parse(
                "{\"android\":{\"identifiers\":{\"androidId\":\"9774D56D682E549C\"}}}");
        assertEquals("9774d56d682e549c",
                androidIdUpper.getAndroidIdentifiersConfig().getAndroidId());
        TraceEnvironmentConfig androidIdAbsent = TraceEnvironmentConfig.parse(
                "{\"android\":{\"identifiers\":{\"limitAdTracking\":false}}}");
        assertFalse(androidIdAbsent.getAndroidIdentifiersConfig().isAndroidIdConfigured());
        assertNull(androidIdAbsent.getAndroidIdentifiersConfig().getAndroidId());

        assertInvalid("{\"android\":{\"identifiers\":{\"androidId\":null}}}",
                "android.identifiers.androidId");
        assertInvalid("{\"android\":{\"identifiers\":{\"androidId\":1}}}",
                "android.identifiers.androidId");
        assertInvalid("{\"android\":{\"identifiers\":{\"androidId\":true}}}",
                "android.identifiers.androidId");
        assertInvalid("{\"android\":{\"identifiers\":{\"androidId\":\"\"}}}",
                "android.identifiers.androidId");
        assertInvalid("{\"android\":{\"identifiers\":{\"androidId\":\"9774d56d682e549\"}}}",
                "android.identifiers.androidId");
        assertInvalid("{\"android\":{\"identifiers\":{\"androidId\":\"9774d56d682e549c0\"}}}",
                "android.identifiers.androidId");
        assertInvalid("{\"android\":{\"identifiers\":{\"androidId\":\"9774d56d682e549g\"}}}",
                "android.identifiers.androidId");
        assertInvalid("{\"android\":{\"identifiers\":{\"androidId\":\"9774-d56d-682e-549c\"}}}",
                "android.identifiers.androidId");
    }

    @Test
    public void testAndroidUserStateConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidUserStateConfigured());
        assertNull(missing.getAndroidUserStateConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidUserStateConfigured());
        assertNull(missingAndroid.getAndroidUserStateConfig());

        // explicit empty → defaults
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"userState\":{}}}");
        assertTrue(empty.isAndroidUserStateConfigured());
        TraceEnvironmentConfig.AndroidUserStateConfig e = empty.getAndroidUserStateConfig();
        assertNotNull(e);
        assertFalse(e.isUserIdConfigured());
        assertEquals(0, e.getUserId());
        assertFalse(e.isSerialNumberConfigured());
        assertEquals(0L, e.getSerialNumber());
        assertFalse(e.isUserUnlockedConfigured());
        assertTrue(e.isUserUnlocked());
        assertFalse(e.isSystemUserConfigured());
        assertTrue(e.isSystemUser());
        assertFalse(e.isManagedProfileConfigured());
        assertFalse(e.isManagedProfile());
        assertFalse(e.isDemoUserConfigured());
        assertFalse(e.isDemoUser());

        // full explicit values
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"userState\":{"
                + "\"userId\":10,"
                + "\"serialNumber\":42,"
                + "\"userUnlocked\":false,"
                + "\"systemUser\":false,"
                + "\"managedProfile\":true"
                + "}}}"
        );
        assertTrue(full.isAndroidUserStateConfigured());
        TraceEnvironmentConfig.AndroidUserStateConfig u = full.getAndroidUserStateConfig();
        assertTrue(u.isUserIdConfigured());
        assertEquals(10, u.getUserId());
        assertTrue(u.isSerialNumberConfigured());
        assertEquals(42L, u.getSerialNumber());
        assertTrue(u.isUserUnlockedConfigured());
        assertFalse(u.isUserUnlocked());
        assertTrue(u.isSystemUserConfigured());
        assertFalse(u.isSystemUser());
        assertTrue(u.isManagedProfileConfigured());
        assertTrue(u.isManagedProfile());

        // immutable / stable
        assertEquals(u.getUserId(), full.getAndroidUserStateConfig().getUserId());
        assertEquals(u.getSerialNumber(), full.getAndroidUserStateConfig().getSerialNumber());
        assertEquals(u.isManagedProfile(), full.getAndroidUserStateConfig().isManagedProfile());

        // partial: only userId
        TraceEnvironmentConfig userIdOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"userState\":{\"userId\":7}}}");
        assertTrue(userIdOnly.getAndroidUserStateConfig().isUserIdConfigured());
        assertEquals(7, userIdOnly.getAndroidUserStateConfig().getUserId());
        assertFalse(userIdOnly.getAndroidUserStateConfig().isSerialNumberConfigured());
        assertEquals(0L, userIdOnly.getAndroidUserStateConfig().getSerialNumber());
        assertTrue(userIdOnly.getAndroidUserStateConfig().isUserUnlocked());
        assertTrue(userIdOnly.getAndroidUserStateConfig().isSystemUser());
        assertFalse(userIdOnly.getAndroidUserStateConfig().isManagedProfile());

        // partial: only managedProfile
        TraceEnvironmentConfig profileOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"userState\":{\"managedProfile\":true}}}");
        assertFalse(profileOnly.getAndroidUserStateConfig().isUserIdConfigured());
        assertEquals(0, profileOnly.getAndroidUserStateConfig().getUserId());
        assertTrue(profileOnly.getAndroidUserStateConfig().isManagedProfileConfigured());
        assertTrue(profileOnly.getAndroidUserStateConfig().isManagedProfile());

        // partial: only demoUser true / false
        TraceEnvironmentConfig demoUserTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"userState\":{\"demoUser\":true}}}");
        assertTrue(demoUserTrue.isAndroidUserStateConfigured());
        assertTrue(demoUserTrue.getAndroidUserStateConfig().isDemoUserConfigured());
        assertTrue(demoUserTrue.getAndroidUserStateConfig().isDemoUser());
        assertFalse(demoUserTrue.getAndroidUserStateConfig().isManagedProfileConfigured());
        assertFalse(demoUserTrue.getAndroidUserStateConfig().isManagedProfile());

        TraceEnvironmentConfig demoUserFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"userState\":{\"demoUser\":false}}}");
        assertTrue(demoUserFalse.getAndroidUserStateConfig().isDemoUserConfigured());
        assertFalse(demoUserFalse.getAndroidUserStateConfig().isDemoUser());

        // bounds
        assertEquals(0, TraceEnvironmentConfig.parse(
                "{\"android\":{\"userState\":{\"userId\":0}}}")
                .getAndroidUserStateConfig().getUserId());
        assertEquals(99999, TraceEnvironmentConfig.parse(
                "{\"android\":{\"userState\":{\"userId\":99999}}}")
                .getAndroidUserStateConfig().getUserId());
        assertEquals(0L, TraceEnvironmentConfig.parse(
                "{\"android\":{\"userState\":{\"serialNumber\":0}}}")
                .getAndroidUserStateConfig().getSerialNumber());
        assertEquals(Long.MAX_VALUE, TraceEnvironmentConfig.parse(
                "{\"android\":{\"userState\":{\"serialNumber\":" + Long.MAX_VALUE + "}}}")
                .getAndroidUserStateConfig().getSerialNumber());

        // coexist with identifiers
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"userState\":{\"userId\":11,\"managedProfile\":true},"
                + "\"identifiers\":{\"limitAdTracking\":true}"
                + "}}"
        );
        assertTrue(both.isAndroidUserStateConfigured());
        assertEquals(11, both.getAndroidUserStateConfig().getUserId());
        assertTrue(both.getAndroidUserStateConfig().isManagedProfile());
        assertTrue(both.isAndroidIdentifiersConfigured());
        assertTrue(both.getAndroidIdentifiersConfig().isLimitAdTracking());

        // wrong node type
        assertInvalid("{\"android\":{\"userState\":[]}}", "android.userState");
        assertInvalid("{\"android\":{\"userState\":1}}", "android.userState");
        assertInvalid("{\"android\":{\"userState\":\"primary\"}}", "android.userState");

        // unknown keys
        assertInvalid("{\"android\":{\"userState\":{\"guest\":true}}}",
                "android.userState.guest");
        assertInvalid("{\"android\":{\"userState\":{\"extra\":1}}}",
                "android.userState.extra");

        // userId invalid
        assertInvalid("{\"android\":{\"userState\":{\"userId\":null}}}",
                "android.userState.userId");
        assertInvalid("{\"android\":{\"userState\":{\"userId\":\"0\"}}}",
                "android.userState.userId");
        assertInvalid("{\"android\":{\"userState\":{\"userId\":true}}}",
                "android.userState.userId");
        assertInvalid("{\"android\":{\"userState\":{\"userId\":1.5}}}",
                "android.userState.userId");
        assertInvalid("{\"android\":{\"userState\":{\"userId\":-1}}}",
                "android.userState.userId");
        assertInvalid("{\"android\":{\"userState\":{\"userId\":100000}}}",
                "android.userState.userId");
        assertInvalid("{\"android\":{\"userState\":{\"userId\":99999999999999999999999999999}}}",
                "android.userState.userId");

        // serialNumber invalid
        assertInvalid("{\"android\":{\"userState\":{\"serialNumber\":null}}}",
                "android.userState.serialNumber");
        assertInvalid("{\"android\":{\"userState\":{\"serialNumber\":\"0\"}}}",
                "android.userState.serialNumber");
        assertInvalid("{\"android\":{\"userState\":{\"serialNumber\":false}}}",
                "android.userState.serialNumber");
        assertInvalid("{\"android\":{\"userState\":{\"serialNumber\":1.5}}}",
                "android.userState.serialNumber");
        assertInvalid("{\"android\":{\"userState\":{\"serialNumber\":-1}}}",
                "android.userState.serialNumber");
        assertInvalid("{\"android\":{\"userState\":{\"serialNumber\":99999999999999999999999999999}}}",
                "android.userState.serialNumber");

        // boolean fields invalid
        assertInvalid("{\"android\":{\"userState\":{\"userUnlocked\":null}}}",
                "android.userState.userUnlocked");
        assertInvalid("{\"android\":{\"userState\":{\"userUnlocked\":\"true\"}}}",
                "android.userState.userUnlocked");
        assertInvalid("{\"android\":{\"userState\":{\"userUnlocked\":1}}}",
                "android.userState.userUnlocked");
        assertInvalid("{\"android\":{\"userState\":{\"systemUser\":null}}}",
                "android.userState.systemUser");
        assertInvalid("{\"android\":{\"userState\":{\"systemUser\":\"false\"}}}",
                "android.userState.systemUser");
        assertInvalid("{\"android\":{\"userState\":{\"systemUser\":0}}}",
                "android.userState.systemUser");
        assertInvalid("{\"android\":{\"userState\":{\"managedProfile\":null}}}",
                "android.userState.managedProfile");
        assertInvalid("{\"android\":{\"userState\":{\"managedProfile\":\"true\"}}}",
                "android.userState.managedProfile");
        assertInvalid("{\"android\":{\"userState\":{\"managedProfile\":1}}}",
                "android.userState.managedProfile");
        assertInvalid("{\"android\":{\"userState\":{\"demoUser\":null}}}",
                "android.userState.demoUser");
        assertInvalid("{\"android\":{\"userState\":{\"demoUser\":\"true\"}}}",
                "android.userState.demoUser");
        assertInvalid("{\"android\":{\"userState\":{\"demoUser\":1}}}",
                "android.userState.demoUser");
    }

    @Test
    public void testAndroidSecurityStateConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidSecurityStateConfigured());
        assertNull(missing.getAndroidSecurityStateConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidSecurityStateConfigured());
        assertNull(missingAndroid.getAndroidSecurityStateConfig());

        // explicit empty → all four false; biometric defaults 12 / -1; flags unconfigured
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securityState\":{}}}");
        assertTrue(empty.isAndroidSecurityStateConfigured());
        TraceEnvironmentConfig.AndroidSecurityStateConfig e =
                empty.getAndroidSecurityStateConfig();
        assertNotNull(e);
        assertFalse(e.isKeyguardLockedConfigured());
        assertFalse(e.isKeyguardLocked());
        assertFalse(e.isKeyguardSecureConfigured());
        assertFalse(e.isKeyguardSecure());
        assertFalse(e.isDeviceLockedConfigured());
        assertFalse(e.isDeviceLocked());
        assertFalse(e.isDeviceSecureConfigured());
        assertFalse(e.isDeviceSecure());
        assertFalse(e.isBiometricCanAuthenticateResultConfigured());
        assertEquals(12, e.getBiometricCanAuthenticateResult());
        assertFalse(e.isBiometricLastAuthenticationElapsedRealtimeMillisConfigured());
        assertEquals(-1L, e.getBiometricLastAuthenticationElapsedRealtimeMillis());

        // full explicit independent values
        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"securityState\":{"
                + "\"keyguardLocked\":true,"
                + "\"keyguardSecure\":false,"
                + "\"deviceLocked\":true,"
                + "\"deviceSecure\":false,"
                + "\"biometricCanAuthenticateResult\":0,"
                + "\"biometricLastAuthenticationElapsedRealtimeMillis\":1234567890"
                + "}}}"
        );
        assertTrue(full.isAndroidSecurityStateConfigured());
        TraceEnvironmentConfig.AndroidSecurityStateConfig s =
                full.getAndroidSecurityStateConfig();
        assertTrue(s.isKeyguardLockedConfigured());
        assertTrue(s.isKeyguardLocked());
        assertTrue(s.isKeyguardSecureConfigured());
        assertFalse(s.isKeyguardSecure());
        assertTrue(s.isDeviceLockedConfigured());
        assertTrue(s.isDeviceLocked());
        assertTrue(s.isDeviceSecureConfigured());
        assertFalse(s.isDeviceSecure());
        assertTrue(s.isBiometricCanAuthenticateResultConfigured());
        assertEquals(0, s.getBiometricCanAuthenticateResult());
        assertTrue(s.isBiometricLastAuthenticationElapsedRealtimeMillisConfigured());
        assertEquals(1234567890L, s.getBiometricLastAuthenticationElapsedRealtimeMillis());

        // stable access
        assertEquals(s.isKeyguardLocked(), full.getAndroidSecurityStateConfig().isKeyguardLocked());
        assertEquals(s.isDeviceSecure(), full.getAndroidSecurityStateConfig().isDeviceSecure());
        assertEquals(s.getBiometricCanAuthenticateResult(),
                full.getAndroidSecurityStateConfig().getBiometricCanAuthenticateResult());
        assertEquals(s.getBiometricLastAuthenticationElapsedRealtimeMillis(),
                full.getAndroidSecurityStateConfig()
                        .getBiometricLastAuthenticationElapsedRealtimeMillis());

        // partial true / false
        TraceEnvironmentConfig lockedOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securityState\":{\"keyguardLocked\":true}}}");
        assertTrue(lockedOnly.getAndroidSecurityStateConfig().isKeyguardLockedConfigured());
        assertTrue(lockedOnly.getAndroidSecurityStateConfig().isKeyguardLocked());
        assertFalse(lockedOnly.getAndroidSecurityStateConfig().isKeyguardSecureConfigured());
        assertFalse(lockedOnly.getAndroidSecurityStateConfig().isKeyguardSecure());
        assertEquals(12, lockedOnly.getAndroidSecurityStateConfig().getBiometricCanAuthenticateResult());
        assertEquals(-1L, lockedOnly.getAndroidSecurityStateConfig()
                .getBiometricLastAuthenticationElapsedRealtimeMillis());

        TraceEnvironmentConfig secureFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securityState\":{\"deviceSecure\":false}}}");
        assertTrue(secureFalse.getAndroidSecurityStateConfig().isDeviceSecureConfigured());
        assertFalse(secureFalse.getAndroidSecurityStateConfig().isDeviceSecure());

        // allowed biometric codes
        for (int code : new int[] {0, 1, 11, 12, 15, 20, 21}) {
            assertEquals(code, TraceEnvironmentConfig.parse(
                    "{\"android\":{\"securityState\":{\"biometricCanAuthenticateResult\":" + code + "}}}")
                    .getAndroidSecurityStateConfig().getBiometricCanAuthenticateResult());
        }

        // last-auth: -1, 0, nonnegative
        assertEquals(-1L, TraceEnvironmentConfig.parse(
                "{\"android\":{\"securityState\":{\"biometricLastAuthenticationElapsedRealtimeMillis\":-1}}}")
                .getAndroidSecurityStateConfig().getBiometricLastAuthenticationElapsedRealtimeMillis());
        assertEquals(0L, TraceEnvironmentConfig.parse(
                "{\"android\":{\"securityState\":{\"biometricLastAuthenticationElapsedRealtimeMillis\":0}}}")
                .getAndroidSecurityStateConfig().getBiometricLastAuthenticationElapsedRealtimeMillis());
        assertEquals(42L, TraceEnvironmentConfig.parse(
                "{\"android\":{\"securityState\":{\"biometricLastAuthenticationElapsedRealtimeMillis\":42}}}")
                .getAndroidSecurityStateConfig().getBiometricLastAuthenticationElapsedRealtimeMillis());

        // wrong node type / unknown keys / type errors
        assertInvalid("{\"android\":{\"securityState\":[]}}", "android.securityState");
        assertInvalid("{\"android\":{\"securityState\":1}}", "android.securityState");
        assertInvalid("{\"android\":{\"securityState\":{\"extra\":true}}}",
                "android.securityState.extra");
        assertInvalid("{\"android\":{\"securityState\":{\"keyguardLocked\":null}}}",
                "android.securityState.keyguardLocked");
        assertInvalid("{\"android\":{\"securityState\":{\"keyguardSecure\":\"true\"}}}",
                "android.securityState.keyguardSecure");
        assertInvalid("{\"android\":{\"securityState\":{\"deviceLocked\":1}}}",
                "android.securityState.deviceLocked");
        assertInvalid("{\"android\":{\"securityState\":{\"deviceSecure\":0}}}",
                "android.securityState.deviceSecure");
        assertInvalid("{\"android\":{\"securityState\":{\"biometricCanAuthenticateResult\":null}}}",
                "android.securityState.biometricCanAuthenticateResult");
        assertInvalid("{\"android\":{\"securityState\":{\"biometricCanAuthenticateResult\":\"12\"}}}",
                "android.securityState.biometricCanAuthenticateResult");
        assertInvalid("{\"android\":{\"securityState\":{\"biometricCanAuthenticateResult\":true}}}",
                "android.securityState.biometricCanAuthenticateResult");
        assertInvalid("{\"android\":{\"securityState\":{\"biometricCanAuthenticateResult\":1.5}}}",
                "android.securityState.biometricCanAuthenticateResult");
        assertInvalid("{\"android\":{\"securityState\":{\"biometricCanAuthenticateResult\":2}}}",
                "android.securityState.biometricCanAuthenticateResult");
        assertInvalid("{\"android\":{\"securityState\":{\"biometricCanAuthenticateResult\":14}}}",
                "android.securityState.biometricCanAuthenticateResult");
        assertInvalid("{\"android\":{\"securityState\":"
                        + "{\"biometricLastAuthenticationElapsedRealtimeMillis\":null}}}",
                "android.securityState.biometricLastAuthenticationElapsedRealtimeMillis");
        assertInvalid("{\"android\":{\"securityState\":"
                        + "{\"biometricLastAuthenticationElapsedRealtimeMillis\":\"-1\"}}}",
                "android.securityState.biometricLastAuthenticationElapsedRealtimeMillis");
        assertInvalid("{\"android\":{\"securityState\":"
                        + "{\"biometricLastAuthenticationElapsedRealtimeMillis\":true}}}",
                "android.securityState.biometricLastAuthenticationElapsedRealtimeMillis");
        assertInvalid("{\"android\":{\"securityState\":"
                        + "{\"biometricLastAuthenticationElapsedRealtimeMillis\":1.5}}}",
                "android.securityState.biometricLastAuthenticationElapsedRealtimeMillis");
        assertInvalid("{\"android\":{\"securityState\":"
                        + "{\"biometricLastAuthenticationElapsedRealtimeMillis\":-2}}}",
                "android.securityState.biometricLastAuthenticationElapsedRealtimeMillis");
    }

    @Test
    public void testAndroidSecuritySignalsConfig() {
        // missing node
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertFalse(missing.isAndroidSecuritySignalsConfigured());
        assertNull(missing.getAndroidSecuritySignalsConfig());
        TraceEnvironmentConfig missingAndroid = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
        assertFalse(missingAndroid.isAndroidSecuritySignalsConfigured());
        assertNull(missingAndroid.getAndroidSecuritySignalsConfig());

        // explicit empty → debugger/monkey fields default false; SELinux fields default true;
        // none of the field-configured flags are set
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{}}}");
        assertTrue(empty.isAndroidSecuritySignalsConfigured());
        TraceEnvironmentConfig.AndroidSecuritySignalsConfig e =
                empty.getAndroidSecuritySignalsConfig();
        assertNotNull(e);
        assertFalse(e.isDebuggerConnectedConfigured());
        assertFalse(e.isDebuggerConnected());
        assertFalse(e.isWaitingForDebuggerConfigured());
        assertFalse(e.isWaitingForDebugger());
        assertFalse(e.isDebuggerTracingConfigured());
        assertFalse(e.isDebuggerTracing());
        assertFalse(e.isSelinuxEnabledConfigured());
        assertTrue(e.isSelinuxEnabled());
        assertFalse(e.isSelinuxEnforcedConfigured());
        assertTrue(e.isSelinuxEnforced());
        assertFalse(e.isUserAMonkeyConfigured());
        assertFalse(e.isUserAMonkey());
        assertFalse(e.isUserTestHarnessConfigured());
        assertFalse(e.isUserTestHarness());

        // explicit debuggerConnected true / false
        TraceEnvironmentConfig trueCfg = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"debuggerConnected\":true}}}");
        assertTrue(trueCfg.isAndroidSecuritySignalsConfigured());
        assertTrue(trueCfg.getAndroidSecuritySignalsConfig().isDebuggerConnectedConfigured());
        assertTrue(trueCfg.getAndroidSecuritySignalsConfig().isDebuggerConnected());
        assertFalse(trueCfg.getAndroidSecuritySignalsConfig().isWaitingForDebuggerConfigured());
        assertFalse(trueCfg.getAndroidSecuritySignalsConfig().isWaitingForDebugger());
        assertFalse(trueCfg.getAndroidSecuritySignalsConfig().isDebuggerTracingConfigured());
        assertFalse(trueCfg.getAndroidSecuritySignalsConfig().isDebuggerTracing());

        TraceEnvironmentConfig falseCfg = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"debuggerConnected\":false}}}");
        assertTrue(falseCfg.getAndroidSecuritySignalsConfig().isDebuggerConnectedConfigured());
        assertFalse(falseCfg.getAndroidSecuritySignalsConfig().isDebuggerConnected());
        assertFalse(falseCfg.getAndroidSecuritySignalsConfig().isWaitingForDebuggerConfigured());
        assertFalse(falseCfg.getAndroidSecuritySignalsConfig().isWaitingForDebugger());
        assertFalse(falseCfg.getAndroidSecuritySignalsConfig().isDebuggerTracingConfigured());
        assertFalse(falseCfg.getAndroidSecuritySignalsConfig().isDebuggerTracing());

        // explicit waitingForDebugger true / false only
        TraceEnvironmentConfig waitingTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"waitingForDebugger\":true}}}");
        assertTrue(waitingTrue.isAndroidSecuritySignalsConfigured());
        assertTrue(waitingTrue.getAndroidSecuritySignalsConfig().isWaitingForDebuggerConfigured());
        assertTrue(waitingTrue.getAndroidSecuritySignalsConfig().isWaitingForDebugger());
        assertFalse(waitingTrue.getAndroidSecuritySignalsConfig().isDebuggerConnectedConfigured());
        assertFalse(waitingTrue.getAndroidSecuritySignalsConfig().isDebuggerConnected());
        assertFalse(waitingTrue.getAndroidSecuritySignalsConfig().isDebuggerTracingConfigured());
        assertFalse(waitingTrue.getAndroidSecuritySignalsConfig().isDebuggerTracing());

        TraceEnvironmentConfig waitingFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"waitingForDebugger\":false}}}");
        assertTrue(waitingFalse.getAndroidSecuritySignalsConfig().isWaitingForDebuggerConfigured());
        assertFalse(waitingFalse.getAndroidSecuritySignalsConfig().isWaitingForDebugger());
        assertFalse(waitingFalse.getAndroidSecuritySignalsConfig().isDebuggerConnectedConfigured());
        assertFalse(waitingFalse.getAndroidSecuritySignalsConfig().isDebuggerConnected());
        assertFalse(waitingFalse.getAndroidSecuritySignalsConfig().isDebuggerTracingConfigured());
        assertFalse(waitingFalse.getAndroidSecuritySignalsConfig().isDebuggerTracing());

        // explicit debuggerTracing true / false only
        TraceEnvironmentConfig tracingTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"debuggerTracing\":true}}}");
        assertTrue(tracingTrue.isAndroidSecuritySignalsConfigured());
        assertTrue(tracingTrue.getAndroidSecuritySignalsConfig().isDebuggerTracingConfigured());
        assertTrue(tracingTrue.getAndroidSecuritySignalsConfig().isDebuggerTracing());
        assertFalse(tracingTrue.getAndroidSecuritySignalsConfig().isDebuggerConnectedConfigured());
        assertFalse(tracingTrue.getAndroidSecuritySignalsConfig().isDebuggerConnected());
        assertFalse(tracingTrue.getAndroidSecuritySignalsConfig().isWaitingForDebuggerConfigured());
        assertFalse(tracingTrue.getAndroidSecuritySignalsConfig().isWaitingForDebugger());

        TraceEnvironmentConfig tracingFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"debuggerTracing\":false}}}");
        assertTrue(tracingFalse.getAndroidSecuritySignalsConfig().isDebuggerTracingConfigured());
        assertFalse(tracingFalse.getAndroidSecuritySignalsConfig().isDebuggerTracing());
        assertFalse(tracingFalse.getAndroidSecuritySignalsConfig().isDebuggerConnectedConfigured());
        assertFalse(tracingFalse.getAndroidSecuritySignalsConfig().isDebuggerConnected());
        assertFalse(tracingFalse.getAndroidSecuritySignalsConfig().isWaitingForDebuggerConfigured());
        assertFalse(tracingFalse.getAndroidSecuritySignalsConfig().isWaitingForDebugger());

        // explicit selinuxEnabled true / false only
        TraceEnvironmentConfig selinuxEnabledTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"selinuxEnabled\":true}}}");
        assertTrue(selinuxEnabledTrue.isAndroidSecuritySignalsConfigured());
        assertTrue(selinuxEnabledTrue.getAndroidSecuritySignalsConfig().isSelinuxEnabledConfigured());
        assertTrue(selinuxEnabledTrue.getAndroidSecuritySignalsConfig().isSelinuxEnabled());
        assertFalse(selinuxEnabledTrue.getAndroidSecuritySignalsConfig().isSelinuxEnforcedConfigured());
        assertTrue(selinuxEnabledTrue.getAndroidSecuritySignalsConfig().isSelinuxEnforced());
        assertFalse(selinuxEnabledTrue.getAndroidSecuritySignalsConfig().isDebuggerConnectedConfigured());
        assertFalse(selinuxEnabledTrue.getAndroidSecuritySignalsConfig().isDebuggerConnected());

        TraceEnvironmentConfig selinuxEnabledFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"selinuxEnabled\":false}}}");
        assertTrue(selinuxEnabledFalse.getAndroidSecuritySignalsConfig().isSelinuxEnabledConfigured());
        assertFalse(selinuxEnabledFalse.getAndroidSecuritySignalsConfig().isSelinuxEnabled());
        assertFalse(selinuxEnabledFalse.getAndroidSecuritySignalsConfig().isSelinuxEnforcedConfigured());
        assertTrue(selinuxEnabledFalse.getAndroidSecuritySignalsConfig().isSelinuxEnforced());

        // explicit selinuxEnforced true / false only
        TraceEnvironmentConfig selinuxEnforcedTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"selinuxEnforced\":true}}}");
        assertTrue(selinuxEnforcedTrue.isAndroidSecuritySignalsConfigured());
        assertTrue(selinuxEnforcedTrue.getAndroidSecuritySignalsConfig().isSelinuxEnforcedConfigured());
        assertTrue(selinuxEnforcedTrue.getAndroidSecuritySignalsConfig().isSelinuxEnforced());
        assertFalse(selinuxEnforcedTrue.getAndroidSecuritySignalsConfig().isSelinuxEnabledConfigured());
        assertTrue(selinuxEnforcedTrue.getAndroidSecuritySignalsConfig().isSelinuxEnabled());
        assertFalse(selinuxEnforcedTrue.getAndroidSecuritySignalsConfig().isDebuggerConnectedConfigured());
        assertFalse(selinuxEnforcedTrue.getAndroidSecuritySignalsConfig().isDebuggerConnected());

        TraceEnvironmentConfig selinuxEnforcedFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"selinuxEnforced\":false}}}");
        assertTrue(selinuxEnforcedFalse.getAndroidSecuritySignalsConfig().isSelinuxEnforcedConfigured());
        assertFalse(selinuxEnforcedFalse.getAndroidSecuritySignalsConfig().isSelinuxEnforced());
        assertFalse(selinuxEnforcedFalse.getAndroidSecuritySignalsConfig().isSelinuxEnabledConfigured());
        assertTrue(selinuxEnforcedFalse.getAndroidSecuritySignalsConfig().isSelinuxEnabled());

        // explicit userAMonkey true / false only (independent of debugger/SELinux)
        TraceEnvironmentConfig monkeyTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"userAMonkey\":true}}}");
        assertTrue(monkeyTrue.isAndroidSecuritySignalsConfigured());
        assertTrue(monkeyTrue.getAndroidSecuritySignalsConfig().isUserAMonkeyConfigured());
        assertTrue(monkeyTrue.getAndroidSecuritySignalsConfig().isUserAMonkey());
        assertFalse(monkeyTrue.getAndroidSecuritySignalsConfig().isDebuggerConnectedConfigured());
        assertFalse(monkeyTrue.getAndroidSecuritySignalsConfig().isDebuggerConnected());
        assertFalse(monkeyTrue.getAndroidSecuritySignalsConfig().isSelinuxEnabledConfigured());
        assertTrue(monkeyTrue.getAndroidSecuritySignalsConfig().isSelinuxEnabled());

        TraceEnvironmentConfig monkeyFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"userAMonkey\":false}}}");
        assertTrue(monkeyFalse.getAndroidSecuritySignalsConfig().isUserAMonkeyConfigured());
        assertFalse(monkeyFalse.getAndroidSecuritySignalsConfig().isUserAMonkey());

        // independence: debugger true does not set monkey / harness
        TraceEnvironmentConfig debuggerOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"debuggerConnected\":true}}}");
        assertTrue(debuggerOnly.getAndroidSecuritySignalsConfig().isDebuggerConnected());
        assertFalse(debuggerOnly.getAndroidSecuritySignalsConfig().isUserAMonkeyConfigured());
        assertFalse(debuggerOnly.getAndroidSecuritySignalsConfig().isUserAMonkey());
        assertFalse(debuggerOnly.getAndroidSecuritySignalsConfig().isUserTestHarnessConfigured());
        assertFalse(debuggerOnly.getAndroidSecuritySignalsConfig().isUserTestHarness());

        // explicit userTestHarness true / false only (independent of monkey/debugger/SELinux)
        TraceEnvironmentConfig harnessTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"userTestHarness\":true}}}");
        assertTrue(harnessTrue.isAndroidSecuritySignalsConfigured());
        assertTrue(harnessTrue.getAndroidSecuritySignalsConfig().isUserTestHarnessConfigured());
        assertTrue(harnessTrue.getAndroidSecuritySignalsConfig().isUserTestHarness());
        assertFalse(harnessTrue.getAndroidSecuritySignalsConfig().isUserAMonkeyConfigured());
        assertFalse(harnessTrue.getAndroidSecuritySignalsConfig().isUserAMonkey());
        assertFalse(harnessTrue.getAndroidSecuritySignalsConfig().isDebuggerConnectedConfigured());
        assertFalse(harnessTrue.getAndroidSecuritySignalsConfig().isDebuggerConnected());

        TraceEnvironmentConfig harnessFalse = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"userTestHarness\":false}}}");
        assertTrue(harnessFalse.getAndroidSecuritySignalsConfig().isUserTestHarnessConfigured());
        assertFalse(harnessFalse.getAndroidSecuritySignalsConfig().isUserTestHarness());

        // independence: monkey true does not set harness
        TraceEnvironmentConfig monkeyOnly = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{\"userAMonkey\":true}}}");
        assertTrue(monkeyOnly.getAndroidSecuritySignalsConfig().isUserAMonkey());
        assertFalse(monkeyOnly.getAndroidSecuritySignalsConfig().isUserTestHarnessConfigured());
        assertFalse(monkeyOnly.getAndroidSecuritySignalsConfig().isUserTestHarness());

        // fields coexist
        TraceEnvironmentConfig bothFields = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{"
                        + "\"debuggerConnected\":true,"
                        + "\"waitingForDebugger\":false,"
                        + "\"debuggerTracing\":true,"
                        + "\"userAMonkey\":true,"
                        + "\"userTestHarness\":false"
                        + "}}}");
        assertTrue(bothFields.getAndroidSecuritySignalsConfig().isDebuggerConnectedConfigured());
        assertTrue(bothFields.getAndroidSecuritySignalsConfig().isDebuggerConnected());
        assertTrue(bothFields.getAndroidSecuritySignalsConfig().isWaitingForDebuggerConfigured());
        assertFalse(bothFields.getAndroidSecuritySignalsConfig().isWaitingForDebugger());
        assertTrue(bothFields.getAndroidSecuritySignalsConfig().isDebuggerTracingConfigured());
        assertTrue(bothFields.getAndroidSecuritySignalsConfig().isDebuggerTracing());
        assertTrue(bothFields.getAndroidSecuritySignalsConfig().isUserAMonkeyConfigured());
        assertTrue(bothFields.getAndroidSecuritySignalsConfig().isUserAMonkey());
        assertTrue(bothFields.getAndroidSecuritySignalsConfig().isUserTestHarnessConfigured());
        assertFalse(bothFields.getAndroidSecuritySignalsConfig().isUserTestHarness());

        TraceEnvironmentConfig bothTrue = TraceEnvironmentConfig.parse(
                "{\"android\":{\"securitySignals\":{"
                        + "\"debuggerConnected\":false,"
                        + "\"waitingForDebugger\":true"
                        + "}}}");
        assertTrue(bothTrue.getAndroidSecuritySignalsConfig().isDebuggerConnectedConfigured());
        assertFalse(bothTrue.getAndroidSecuritySignalsConfig().isDebuggerConnected());
        assertTrue(bothTrue.getAndroidSecuritySignalsConfig().isWaitingForDebuggerConfigured());
        assertTrue(bothTrue.getAndroidSecuritySignalsConfig().isWaitingForDebugger());
        assertFalse(bothTrue.getAndroidSecuritySignalsConfig().isDebuggerTracingConfigured());
        assertFalse(bothTrue.getAndroidSecuritySignalsConfig().isDebuggerTracing());

        // getter stability
        assertEquals(trueCfg.getAndroidSecuritySignalsConfig().isDebuggerConnected(),
                trueCfg.getAndroidSecuritySignalsConfig().isDebuggerConnected());
        assertEquals(waitingTrue.getAndroidSecuritySignalsConfig().isWaitingForDebugger(),
                waitingTrue.getAndroidSecuritySignalsConfig().isWaitingForDebugger());
        assertEquals(waitingTrue.getAndroidSecuritySignalsConfig().isWaitingForDebuggerConfigured(),
                waitingTrue.getAndroidSecuritySignalsConfig().isWaitingForDebuggerConfigured());
        assertEquals(tracingTrue.getAndroidSecuritySignalsConfig().isDebuggerTracing(),
                tracingTrue.getAndroidSecuritySignalsConfig().isDebuggerTracing());
        assertEquals(tracingTrue.getAndroidSecuritySignalsConfig().isDebuggerTracingConfigured(),
                tracingTrue.getAndroidSecuritySignalsConfig().isDebuggerTracingConfigured());
        assertEquals(selinuxEnabledFalse.getAndroidSecuritySignalsConfig().isSelinuxEnabled(),
                selinuxEnabledFalse.getAndroidSecuritySignalsConfig().isSelinuxEnabled());
        assertEquals(selinuxEnforcedFalse.getAndroidSecuritySignalsConfig().isSelinuxEnforced(),
                selinuxEnforcedFalse.getAndroidSecuritySignalsConfig().isSelinuxEnforced());
        assertEquals(monkeyTrue.getAndroidSecuritySignalsConfig().isUserAMonkey(),
                monkeyTrue.getAndroidSecuritySignalsConfig().isUserAMonkey());
        assertEquals(monkeyTrue.getAndroidSecuritySignalsConfig().isUserAMonkeyConfigured(),
                monkeyTrue.getAndroidSecuritySignalsConfig().isUserAMonkeyConfigured());
        assertEquals(harnessTrue.getAndroidSecuritySignalsConfig().isUserTestHarness(),
                harnessTrue.getAndroidSecuritySignalsConfig().isUserTestHarness());
        assertEquals(harnessTrue.getAndroidSecuritySignalsConfig().isUserTestHarnessConfigured(),
                harnessTrue.getAndroidSecuritySignalsConfig().isUserTestHarnessConfigured());
        assertNotNull(trueCfg.getAndroidSecuritySignalsConfig());
        assertSame(waitingTrue.getAndroidSecuritySignalsConfig(),
                waitingTrue.getAndroidSecuritySignalsConfig());
        assertSame(tracingTrue.getAndroidSecuritySignalsConfig(),
                tracingTrue.getAndroidSecuritySignalsConfig());

        // coexist with userState / properties
        TraceEnvironmentConfig both = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"securitySignals\":{\"debuggerConnected\":true,\"waitingForDebugger\":true,"
                + "\"debuggerTracing\":false},"
                + "\"userState\":{\"userId\":10},"
                + "\"properties\":{\"ro.debuggable\":\"0\"}"
                + "}}"
        );
        assertTrue(both.isAndroidSecuritySignalsConfigured());
        assertTrue(both.getAndroidSecuritySignalsConfig().isDebuggerConnected());
        assertTrue(both.getAndroidSecuritySignalsConfig().isWaitingForDebugger());
        assertTrue(both.getAndroidSecuritySignalsConfig().isDebuggerTracingConfigured());
        assertFalse(both.getAndroidSecuritySignalsConfig().isDebuggerTracing());
        assertTrue(both.isAndroidUserStateConfigured());
        assertEquals(10, both.getAndroidUserStateConfig().getUserId());
        assertTrue(both.hasAndroidProperties());

        // wrong node type
        assertInvalid("{\"android\":{\"securitySignals\":[]}}", "android.securitySignals");
        assertInvalid("{\"android\":{\"securitySignals\":1}}", "android.securitySignals");
        assertInvalid("{\"android\":{\"securitySignals\":\"x\"}}", "android.securitySignals");

        // unknown keys still reject
        assertInvalid("{\"android\":{\"securitySignals\":{\"rooted\":false}}}",
                "android.securitySignals.rooted");
        assertInvalid("{\"android\":{\"securitySignals\":{\"extra\":true}}}",
                "android.securitySignals.extra");

        // illegal field types — debuggerConnected
        assertInvalid("{\"android\":{\"securitySignals\":{\"debuggerConnected\":null}}}",
                "android.securitySignals.debuggerConnected");
        assertInvalid("{\"android\":{\"securitySignals\":{\"debuggerConnected\":\"true\"}}}",
                "android.securitySignals.debuggerConnected");
        assertInvalid("{\"android\":{\"securitySignals\":{\"debuggerConnected\":1}}}",
                "android.securitySignals.debuggerConnected");
        assertInvalid("{\"android\":{\"securitySignals\":{\"debuggerConnected\":0}}}",
                "android.securitySignals.debuggerConnected");

        // illegal field types — waitingForDebugger (strict JSON Boolean)
        assertInvalid("{\"android\":{\"securitySignals\":{\"waitingForDebugger\":null}}}",
                "android.securitySignals.waitingForDebugger");
        assertInvalid("{\"android\":{\"securitySignals\":{\"waitingForDebugger\":\"true\"}}}",
                "android.securitySignals.waitingForDebugger");
        assertInvalid("{\"android\":{\"securitySignals\":{\"waitingForDebugger\":1}}}",
                "android.securitySignals.waitingForDebugger");
        assertInvalid("{\"android\":{\"securitySignals\":{\"waitingForDebugger\":0}}}",
                "android.securitySignals.waitingForDebugger");

        // illegal field types — debuggerTracing (strict JSON Boolean)
        assertInvalid("{\"android\":{\"securitySignals\":{\"debuggerTracing\":null}}}",
                "android.securitySignals.debuggerTracing");
        assertInvalid("{\"android\":{\"securitySignals\":{\"debuggerTracing\":\"true\"}}}",
                "android.securitySignals.debuggerTracing");
        assertInvalid("{\"android\":{\"securitySignals\":{\"debuggerTracing\":1}}}",
                "android.securitySignals.debuggerTracing");
        assertInvalid("{\"android\":{\"securitySignals\":{\"debuggerTracing\":0}}}",
                "android.securitySignals.debuggerTracing");

        // illegal field types — selinuxEnabled (strict JSON Boolean)
        assertInvalid("{\"android\":{\"securitySignals\":{\"selinuxEnabled\":null}}}",
                "android.securitySignals.selinuxEnabled");
        assertInvalid("{\"android\":{\"securitySignals\":{\"selinuxEnabled\":\"true\"}}}",
                "android.securitySignals.selinuxEnabled");
        assertInvalid("{\"android\":{\"securitySignals\":{\"selinuxEnabled\":1}}}",
                "android.securitySignals.selinuxEnabled");

        // illegal field types — selinuxEnforced (strict JSON Boolean)
        assertInvalid("{\"android\":{\"securitySignals\":{\"selinuxEnforced\":null}}}",
                "android.securitySignals.selinuxEnforced");
        assertInvalid("{\"android\":{\"securitySignals\":{\"selinuxEnforced\":\"false\"}}}",
                "android.securitySignals.selinuxEnforced");
        assertInvalid("{\"android\":{\"securitySignals\":{\"selinuxEnforced\":0}}}",
                "android.securitySignals.selinuxEnforced");

        // illegal field types — userAMonkey (strict JSON Boolean)
        assertInvalid("{\"android\":{\"securitySignals\":{\"userAMonkey\":null}}}",
                "android.securitySignals.userAMonkey");
        assertInvalid("{\"android\":{\"securitySignals\":{\"userAMonkey\":\"true\"}}}",
                "android.securitySignals.userAMonkey");
        assertInvalid("{\"android\":{\"securitySignals\":{\"userAMonkey\":1}}}",
                "android.securitySignals.userAMonkey");
        assertInvalid("{\"android\":{\"securitySignals\":{\"userAMonkey\":0}}}",
                "android.securitySignals.userAMonkey");

        // illegal field types — userTestHarness (strict JSON Boolean)
        assertInvalid("{\"android\":{\"securitySignals\":{\"userTestHarness\":null}}}",
                "android.securitySignals.userTestHarness");
        assertInvalid("{\"android\":{\"securitySignals\":{\"userTestHarness\":\"true\"}}}",
                "android.securitySignals.userTestHarness");
        assertInvalid("{\"android\":{\"securitySignals\":{\"userTestHarness\":1}}}",
                "android.securitySignals.userTestHarness");
        assertInvalid("{\"android\":{\"securitySignals\":{\"userTestHarness\":0}}}",
                "android.securitySignals.userTestHarness");
    }

    private static void assertInvalid(String json, String expectedPath) {
        try {
            TraceEnvironmentConfig.parse(json);
            fail("expected IllegalArgumentException containing path: " + expectedPath + " for json: " + json);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue("message missing path " + expectedPath + ", was: " + message,
                    message != null && message.contains(expectedPath));
        }
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

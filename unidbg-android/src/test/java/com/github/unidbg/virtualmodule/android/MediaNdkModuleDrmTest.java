package com.github.unidbg.virtualmodule.android;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * MediaNdkModule android.drm wiring. Uses package-visible helpers that SVC path also uses.
 * Lifecycle: unregister sink → module.release → free test blocks → emulator.close.
 */
public class MediaNdkModuleDrmTest {

    private static final String DRM_JSON = "{"
            + "\"android\":{\"drm\":{"
            + "\"available\":true,"
            + "\"marker\":\"M1\","
            + "\"schemeUuids\":[\"edef8ba9-79d6-4ace-a3c8-27dcd51d21ed\"],"
            + "\"vendor\":\"TraceAI\","
            + "\"version\":\"V1\","
            + "\"description\":\"短\","
            + "\"algorithms\":\"AES/CBC/NoPadding,HmacSHA256\","
            + "\"securityLevel\":\"L3\","
            + "\"hdcpLevel\":\"HDCP_NONE\","
            + "\"maxHdcpLevel\":\"HDCP_V2_2\","
            + "\"provisioned\":true"
            + "}}}";

    @Test
    public void testPropertyString32And64StructAndExpand() throws Exception {
        runStructAndExpand(false);
        runStructAndExpand(true);
    }

    private static void runStructAndExpand(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(DRM_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        MediaNdkModule module = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());

            MemoryBlock out = emulator.getMemory().malloc(32, true);
            blocks.add(out);
            Pointer prop = out.getPointer();

            // config-backed string property (UTF-8 length, not Java char length)
            module.getPropertyStringByName(emulator, "description", prop);
            int shortUtf8 = "短".getBytes(StandardCharsets.UTF_8).length;
            assertEquals(shortUtf8, readLength(emulator, prop));
            assertEquals("短", prop.getPointer(0).getString(0));

            // same-module capacity growth/reuse via writePropertyString
            String longDesc = "这是一段较长的描述用于扩容测试ABC";
            byte[] longUtf8 = longDesc.getBytes(StandardCharsets.UTF_8);
            assertFalse(longUtf8.length == longDesc.length());

            module.writePropertyString(emulator, prop, "x");
            int capAfterTiny = module.getPropertyStringCapacityForTest();
            MemoryBlock stringBlockAfterTiny = module.getPropertyStringBlockForTest();
            assertNotNull(stringBlockAfterTiny);
            assertTrue(capAfterTiny >= 2);

            module.writePropertyString(emulator, prop, longDesc);
            assertEquals(longUtf8.length, readLength(emulator, prop));
            assertEquals(longDesc, prop.getPointer(0).getString(0));
            int capAfterLong = module.getPropertyStringCapacityForTest();
            MemoryBlock stringBlockAfterLong = module.getPropertyStringBlockForTest();
            assertTrue(capAfterLong >= longUtf8.length + 1);
            assertTrue(capAfterLong > capAfterTiny);
            assertNotNull(stringBlockAfterLong);

            module.writePropertyString(emulator, prop, "y");
            assertEquals(1, readLength(emulator, prop));
            assertEquals("y", prop.getPointer(0).getString(0));
            assertEquals(capAfterLong, module.getPropertyStringCapacityForTest());
            assertSame(stringBlockAfterLong, module.getPropertyStringBlockForTest());

            module.getPropertyStringByName(emulator, "vendor", prop);
            assertEquals(7, readLength(emulator, prop));
            assertEquals("TraceAI", prop.getPointer(0).getString(0));
            module.getPropertyStringByName(emulator, "algorithms", prop);
            int algoLen = "AES/CBC/NoPadding,HmacSHA256".getBytes(StandardCharsets.UTF_8).length;
            assertEquals(algoLen, readLength(emulator, prop));
        } finally {
            teardown(module, null, emulator, blocks);
        }
    }

    @Test
    public void testCreateSupportedUnsupportedAvailableFalse() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(DRM_JSON);
        AndroidEmulator emulator = null;
        MediaNdkModule module = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());

            long h1 = module.createByUuidBytes(emulator, MediaNdkModule.WIDE_VINE_UUID);
            assertNotEquals(0L, h1);
            long h2 = module.createByUuidBytes(emulator, MediaNdkModule.WIDE_VINE_UUID);
            assertEquals(h1, h2);
            assertEquals(2, countKind(sink, "drm"));

            byte[] other = new byte[16];
            other[0] = 0x11;
            assertEquals(0L, module.createByUuidBytes(emulator, other));
            assertEquals(3, countKind(sink, "drm"));
        } finally {
            teardown(module, sink, emulator, null);
        }

        TraceEnvironmentConfig off = TraceEnvironmentConfig.parse(
                "{\"android\":{\"drm\":{\"available\":false}}}");
        emulator = null;
        module = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(off).build();
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());
            assertEquals(0L, module.createByUuidBytes(emulator, MediaNdkModule.WIDE_VINE_UUID));
        } finally {
            teardown(module, null, emulator, null);
        }
    }

    @Test
    public void testUnconfiguredLegacyBehavior() throws Exception {
        AndroidEmulator emulator = null;
        MediaNdkModule module = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());

            long h = module.createByUuidBytes(emulator, MediaNdkModule.WIDE_VINE_UUID);
            assertNotEquals(0L, h);

            try {
                module.createByUuidBytes(emulator, new byte[16]);
                fail("expected UnsupportedOperationException");
            } catch (UnsupportedOperationException expected) {
                // ok
            }

            MemoryBlock out = emulator.getMemory().malloc(32, true);
            blocks.add(out);
            module.getPropertyStringByName(emulator, "vendor", out.getPointer());
            assertEquals("Google", out.getPointer().getPointer(0).getString(0));

            try {
                module.getPropertyStringByName(emulator, "version", out.getPointer());
                fail("expected UnsupportedOperationException for unconfigured version");
            } catch (UnsupportedOperationException expected) {
                // ok
            }
        } finally {
            teardown(module, null, emulator, blocks);
        }
    }

    @Test
    public void testSidecarAndRepeatRelease() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(DRM_JSON);
        AndroidEmulator emulator = null;
        MediaNdkModule module = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());

            module.createByUuidBytes(emulator, MediaNdkModule.WIDE_VINE_UUID);
            assertEquals(1, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("drm", e0.kind);
            assertEquals("AMediaDrm_createByUUID", e0.api);
            assertEquals("json-config", e0.source);
            String v0 = String.valueOf(e0.value);
            assertTrue(v0.contains("supported=true"));
            assertTrue(v0.contains("available=true"));
            assertTrue(v0.contains("uuid=edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"));
            assertTrue(v0.contains("marker=M1"));
            assertTrue(e0.note.contains("配置的 DRM create"));

            MemoryBlock out = emulator.getMemory().malloc(32, true);
            blocks.add(out);
            module.getPropertyStringByName(emulator, "securityLevel", out.getPointer());
            assertEquals(2, sink.events.size());
            CapturedEvent e1 = sink.events.get(1);
            assertEquals("AMediaDrm_getPropertyString", e1.api);
            assertEquals("property=securityLevel,value=L3,marker=M1", String.valueOf(e1.value));
            assertTrue(e1.note.contains("读取配置的 DRM 属性"));

            module.release();
            assertNull(module.getHandleBlockForTest());
            assertNull(module.getPropertyStringBlockForTest());
            assertNull(module.getPropertyByteArrayBlockForTest());
            module.release();
            assertNull(module.getHandleBlockForTest());
            module = null; // already released; teardown must not double-free incorrectly
        } finally {
            teardown(module, sink, emulator, blocks);
        }
    }

    @Test
    public void testPropertyByteArrayExactAndStruct32And64() throws Exception {
        runByteArrayCases(false);
        runByteArrayCases(true);
    }

    private static void runByteArrayCases(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig exactCfg = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"drm\":{"
                + "\"marker\":\"MARK\","
                + "\"deviceUniqueIdHex\":\"0011aabb\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        MediaNdkModule module = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(exactCfg)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());
            MemoryBlock out = emulator.getMemory().malloc(32, true);
            blocks.add(out);
            Pointer prop = out.getPointer();

            // exact configured bytes + struct layout + redacted sidecar
            module.getPropertyByteArrayByName(emulator, "deviceUniqueId", prop);
            assertEquals(4, readLength(emulator, prop));
            assertArrayEquals(new byte[]{0x00, 0x11, (byte) 0xaa, (byte) 0xbb},
                    prop.getPointer(0).getByteArray(0, 4));
            assertTrue(module.getPropertyByteArrayCapacityForTest() >= 4);
            assertEquals(1, sink.events.size());
            CapturedEvent e = sink.events.get(0);
            assertEquals("drm", e.kind);
            assertEquals("AMediaDrm_getPropertyByteArray", e.api);
            assertEquals("json-config", e.source);
            String v = String.valueOf(e.value);
            assertTrue(v.contains("property=deviceUniqueId"));
            assertTrue(v.contains("bytes=4"));
            assertTrue(v.contains("marker=MARK"));
            assertFalse(v.contains("0011"));
            assertFalse(v.contains("aabb"));

            // same-module capacity growth/reuse via writePropertyByteArray
            byte[] shortId = new byte[]{0x01, 0x02};
            byte[] longId = new byte[]{
                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
                    (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb,
                    (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff
            };
            byte[] shorterAgain = new byte[]{0x0a, 0x0b, 0x0c};

            module.writePropertyByteArray(emulator, prop, shortId);
            assertEquals(2, readLength(emulator, prop));
            assertArrayEquals(shortId, prop.getPointer(0).getByteArray(0, 2));
            int capShort = module.getPropertyByteArrayCapacityForTest();
            MemoryBlock blockShort = module.getPropertyByteArrayBlockForTest();
            assertNotNull(blockShort);
            assertTrue(capShort >= 2);

            module.writePropertyByteArray(emulator, prop, longId);
            assertEquals(16, readLength(emulator, prop));
            assertArrayEquals(longId, prop.getPointer(0).getByteArray(0, 16));
            int capLong = module.getPropertyByteArrayCapacityForTest();
            MemoryBlock blockLong = module.getPropertyByteArrayBlockForTest();
            assertTrue(capLong >= 16);
            assertTrue(capLong > capShort);
            assertNotNull(blockLong);

            module.writePropertyByteArray(emulator, prop, shorterAgain);
            assertEquals(3, readLength(emulator, prop));
            assertArrayEquals(shorterAgain, prop.getPointer(0).getByteArray(0, 3));
            assertEquals(capLong, module.getPropertyByteArrayCapacityForTest());
            assertSame(blockLong, module.getPropertyByteArrayBlockForTest());

            module.release();
            assertNull(module.getPropertyByteArrayBlockForTest());
            module.release();
            module = null;
        } finally {
            teardown(module, sink, emulator, blocks);
        }
    }

    @Test
    public void testPropertyByteArrayMarkerFallbackAndLegacy() throws Exception {
        // drm configured, no deviceUniqueIdHex, no random → marker UTF-8
        TraceEnvironmentConfig markerCfg = TraceEnvironmentConfig.parse(
                "{\"android\":{\"drm\":{\"marker\":\"TRACE_ID\"}}}");
        AndroidEmulator emulator = null;
        MediaNdkModule module = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(markerCfg).build();
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());
            MemoryBlock out = emulator.getMemory().malloc(32, true);
            blocks.add(out);
            Pointer prop = out.getPointer();
            module.getPropertyByteArrayByName(emulator, "deviceUniqueId", prop);
            byte[] expected = "TRACE_ID".getBytes(StandardCharsets.UTF_8);
            assertEquals(expected.length, readLength(emulator, prop));
            assertArrayEquals(expected, prop.getPointer(0).getByteArray(0, expected.length));
        } finally {
            teardown(module, null, emulator, blocks);
        }

        // legacy unconfigured → always length 32
        emulator = null;
        module = null;
        blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());
            MemoryBlock out = emulator.getMemory().malloc(32, true);
            blocks.add(out);
            Pointer prop = out.getPointer();
            module.getPropertyByteArrayByName(emulator, "deviceUniqueId", prop);
            assertEquals(32, readLength(emulator, prop));
            assertNotNull(prop.getPointer(0).getByteArray(0, 32));

            try {
                module.getPropertyByteArrayByName(emulator, "unknown", prop);
                fail("expected UnsupportedOperationException");
            } catch (UnsupportedOperationException expectedEx) {
                // ok
            }
        } finally {
            teardown(module, null, emulator, blocks);
        }

        // legacy with random hex → still 32 via getRandomBytes pad
        TraceEnvironmentConfig rnd = TraceEnvironmentConfig.parse(
                "{\"random\":{\"mediaDrmDeviceUniqueIdHex\":\"aa\"}}");
        emulator = null;
        module = null;
        blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(rnd).build();
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());
            MemoryBlock out = emulator.getMemory().malloc(32, true);
            blocks.add(out);
            Pointer prop = out.getPointer();
            module.getPropertyByteArrayByName(emulator, "deviceUniqueId", prop);
            assertEquals(32, readLength(emulator, prop));
            assertEquals((byte) 0xaa, prop.getPointer(0).getByte(0));
        } finally {
            teardown(module, null, emulator, blocks);
        }
    }

    @Test
    public void testOpenCloseSession32And64() throws Exception {
        runSessionCases(false);
        runSessionCases(true);
    }

    private static void runSessionCases(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig cfg = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"drm\":{"
                + "\"marker\":\"M1\","
                + "\"provisioned\":true,"
                + "\"sessionIdHex\":\"0a0b0c\""
                + "}}}"
        );
        AndroidEmulator emulator = null;
        MediaNdkModule module = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(cfg)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());
            assertTrue(module.isSessionApiRegisteredForTest());

            MemoryBlock sessionStruct = emulator.getMemory().malloc(32, true);
            blocks.add(sessionStruct);
            Pointer sid = sessionStruct.getPointer();
            // poison length so not-provisioned / failed paths don't leave success-looking lengths
            if (is64Bit) {
                sid.setLong(8, 0xdeadL);
            } else {
                sid.setInt(4, 0xdead);
            }

            int st = module.openSessionTo(emulator, sid);
            assertEquals(MediaNdkModule.AMEDIA_OK, st);
            assertEquals(3, readLength(emulator, sid));
            assertArrayEquals(new byte[]{0x0a, 0x0b, 0x0c},
                    sid.getPointer(0).getByteArray(0, 3));
            assertArrayEquals(new byte[]{0x0a, 0x0b, 0x0c}, module.getActiveSessionIdForTest());
            assertTrue(sink.events.size() >= 1);
            CapturedEvent openEv = sink.events.get(sink.events.size() - 1);
            assertEquals("AMediaDrm_openSession", openEv.api);
            assertTrue(String.valueOf(openEv.value).contains("status=OK"));
            assertTrue(String.valueOf(openEv.value).contains("bytes=3"));
            assertTrue(String.valueOf(openEv.value).contains("marker=M1"));
            assertFalse(String.valueOf(openEv.value).contains("0a0b"));

            int closeSt = module.closeSessionFrom(emulator, sid);
            assertEquals(MediaNdkModule.AMEDIA_OK, closeSt);
            assertNull(module.getActiveSessionIdForTest());
            CapturedEvent closeEv = sink.events.get(sink.events.size() - 1);
            assertEquals("AMediaDrm_closeSession", closeEv.api);
            assertTrue(String.valueOf(closeEv.value).contains("status=OK"));
            assertFalse(String.valueOf(closeEv.value).contains("0a0b"));

            // double close
            assertEquals(MediaNdkModule.AMEDIA_DRM_SESSION_NOT_OPENED,
                    module.closeSessionFrom(emulator, sid));

            // reopen then wrong session bytes (struct points to different buffer, not shared session block)
            assertEquals(MediaNdkModule.AMEDIA_OK, module.openSessionTo(emulator, sid));
            MemoryBlock wrong = emulator.getMemory().malloc(32, true);
            blocks.add(wrong);
            Pointer wrongSid = wrong.getPointer();
            MemoryBlock wrongData = emulator.getMemory().malloc(4, true);
            blocks.add(wrongData);
            wrongData.getPointer().write(0, new byte[]{0x11, 0x22, 0x33}, 0, 3);
            wrongSid.setPointer(0, wrongData.getPointer());
            if (is64Bit) {
                wrongSid.setLong(8, 3);
            } else {
                wrongSid.setInt(4, 3);
            }
            assertEquals(MediaNdkModule.AMEDIA_DRM_SESSION_NOT_OPENED,
                    module.closeSessionFrom(emulator, wrongSid));
            assertNotNull(module.getActiveSessionIdForTest());

            // correct close still works
            assertEquals(MediaNdkModule.AMEDIA_OK, module.closeSessionFrom(emulator, sid));
        } finally {
            teardown(module, sink, emulator, blocks);
        }
    }

    @Test
    public void testOpenSessionMarkerFallbackNotProvisionedAndNoSymbols() throws Exception {
        // marker fallback when sessionIdHex absent
        TraceEnvironmentConfig markerCfg = TraceEnvironmentConfig.parse(
                "{\"android\":{\"drm\":{\"marker\":\"SID\",\"provisioned\":true}}}");
        AndroidEmulator emulator = null;
        MediaNdkModule module = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(markerCfg).build();
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());
            MemoryBlock sessionStruct = emulator.getMemory().malloc(32, true);
            blocks.add(sessionStruct);
            Pointer sid = sessionStruct.getPointer();
            assertEquals(MediaNdkModule.AMEDIA_OK, module.openSessionTo(emulator, sid));
            byte[] expected = "SID".getBytes(StandardCharsets.UTF_8);
            assertEquals(expected.length, readLength(emulator, sid));
            assertArrayEquals(expected, sid.getPointer(0).getByteArray(0, expected.length));
            assertEquals(MediaNdkModule.AMEDIA_OK, module.closeSessionFrom(emulator, sid));
        } finally {
            teardown(module, null, emulator, blocks);
        }

        // not provisioned: no write
        TraceEnvironmentConfig notProv = TraceEnvironmentConfig.parse(
                "{\"android\":{\"drm\":{\"provisioned\":false,\"sessionIdHex\":\"ff\"}}}");
        emulator = null;
        module = null;
        blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(notProv).build();
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());
            MemoryBlock sessionStruct = emulator.getMemory().malloc(32, true);
            blocks.add(sessionStruct);
            Pointer sid = sessionStruct.getPointer();
            sid.setPointer(0, null);
            sid.setLong(8, 0xbeefL);
            assertEquals(MediaNdkModule.AMEDIA_DRM_NOT_PROVISIONED,
                    module.openSessionTo(emulator, sid));
            assertEquals(0xbeefL, sid.getLong(8)); // not overwritten
            assertNull(module.getActiveSessionIdForTest());
            assertNull(module.getSessionIdBlockForTest());
        } finally {
            teardown(module, null, emulator, blocks);
        }

        // no android.drm → session symbols not registered
        emulator = null;
        module = null;
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());
            assertFalse(module.isSessionApiRegisteredForTest());
        } finally {
            teardown(module, null, emulator, null);
        }
    }

    @Test
    public void testSessionReleaseClearsState() throws Exception {
        TraceEnvironmentConfig cfg = TraceEnvironmentConfig.parse(
                "{\"android\":{\"drm\":{\"sessionIdHex\":\"aa\",\"provisioned\":true}}}");
        AndroidEmulator emulator = null;
        MediaNdkModule module = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(cfg).build();
            module = new MediaNdkModule(emulator, emulator.createDalvikVM());
            MemoryBlock sessionStruct = emulator.getMemory().malloc(32, true);
            blocks.add(sessionStruct);
            Pointer sid = sessionStruct.getPointer();
            assertEquals(MediaNdkModule.AMEDIA_OK, module.openSessionTo(emulator, sid));
            assertNotNull(module.getSessionIdBlockForTest());
            assertNotNull(module.getActiveSessionIdForTest());
            module.release();
            assertNull(module.getSessionIdBlockForTest());
            assertNull(module.getActiveSessionIdForTest());
            module.release();
            module = null;
        } finally {
            teardown(module, null, emulator, blocks);
        }
    }

    @Test
    public void testUuidHelperAndSchemeMatch() {
        assertEquals("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed",
                MediaNdkModule.uuidBytesToCanonical(MediaNdkModule.WIDE_VINE_UUID));
        assertTrue(MediaNdkModule.schemeSupported(
                Arrays.asList("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"),
                "edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"));
        assertFalse(MediaNdkModule.schemeSupported(
                Arrays.asList("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"),
                "00000000-0000-0000-0000-000000000000"));
        assertEquals("L3", MediaNdkModule.resolveConfiguredProperty(
                TraceEnvironmentConfig.parse("{\"android\":{\"drm\":{}}}").getAndroidDrmConfig(),
                "securityLevel"));
        assertNull(MediaNdkModule.resolveConfiguredProperty(
                TraceEnvironmentConfig.parse("{\"android\":{\"drm\":{}}}").getAndroidDrmConfig(),
                "unknownProp"));
    }

    /**
     * unregister sink → module.release → free test-owned blocks → emulator.close
     */
    private static void teardown(MediaNdkModule module, CapturingSink sink,
                                 AndroidEmulator emulator, List<MemoryBlock> blocks) {
        if (emulator != null && sink != null) {
            TraceEnvironmentEventSink.unregister(emulator, sink);
        }
        if (module != null) {
            module.release();
        }
        freeAll(blocks);
        if (emulator != null) {
            try {
                emulator.close();
            } catch (Exception ignored) {
                // teardown
            }
        }
    }

    private static int readLength(AndroidEmulator emulator, Pointer propStruct) {
        if (emulator.is32Bit()) {
            return propStruct.getInt(4);
        }
        return (int) propStruct.getLong(8);
    }

    private static int countKind(CapturingSink sink, String kind) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (kind.equals(e.kind)) {
                n++;
            }
        }
        return n;
    }

    private static void freeAll(List<MemoryBlock> blocks) {
        if (blocks == null) {
            return;
        }
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

package com.github.unidbg.file.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.struct.StatFS;
import com.github.unidbg.linux.struct.StatFS32;
import com.github.unidbg.linux.struct.StatFS64;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgStructure;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link ConfiguredFileStatFs}. Uses in-emulator malloc'd StatFS buffers only —
 * no real filesystem dependency.
 */
public class ConfiguredFileStatFsTest {

    private static final String FULL_STATFS_JSON = "{"
            + "\"filesystem\":{\"statfs\":{"
            + "\"/\":{"
            + "\"type\":1,"
            + "\"blockSize\":1024,"
            + "\"blocks\":100,"
            + "\"blocksFree\":50,"
            + "\"blocksAvailable\":40,"
            + "\"files\":200,"
            + "\"filesFree\":150,"
            + "\"fsid\":[9,8],"
            + "\"nameLength\":255,"
            + "\"fragmentSize\":1024,"
            + "\"flags\":0"
            + "},"
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
            + "\"/data/user/0\":{"
            + "\"blocks\":77"
            + "},"
            + "\"/partial\":{"
            + "\"blocks\":42,"
            + "\"blockSize\":512"
            + "},"
            + "\"/wide\":{"
            + "\"type\":4294967295,"
            + "\"flags\":4294967295,"
            + "\"fsid\":[4294967295,4294967295]"
            + "}"
            + "}}"
            + "}";

    @Test
    public void testApplyFullAndPartial32() throws Exception {
        runFullPartialAndLongest(false);
    }

    @Test
    public void testApplyFullAndPartial64() throws Exception {
        runFullPartialAndLongest(true);
    }

    @Test
    public void testWideTypeFlagsFsid32() throws Exception {
        runWide(false);
    }

    @Test
    public void testWideTypeFlagsFsid64() throws Exception {
        runWide(true);
    }

    @Test
    public void testNoConfigAndMissDoNotChange() throws Exception {
        // Two emulator phases must not share a MemoryBlock list: free each phase's
        // blocks while that emulator is still alive (before close).
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            StatFS64 statFS = newStatFS64(emulator, blocks);
            fillSentinel(statFS);
            assertFalse(ConfiguredFileStatFs.apply(emulator, "/any", statFS));
            assertSentinelUnchanged(statFS);
            freeAll(blocks);
            emulator.close();
            emulator = null;

            // empty map configured but no mounts
            TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                    "{\"filesystem\":{\"statfs\":{}}}");
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(empty).build();
            StatFS64 emptyBuf = newStatFS64(emulator, blocks);
            fillSentinel(emptyBuf);
            assertFalse(ConfiguredFileStatFs.apply(emulator, "/data", emptyBuf));
            assertSentinelUnchanged(emptyBuf);
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testUnmatchedWithoutRootDoesNotChange() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"statfs\":{\"/data\":{\"blocks\":10}}}"
                + "}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            StatFS64 miss = newStatFS64(emulator, blocks);
            fillSentinel(miss);
            assertFalse(ConfiguredFileStatFs.apply(emulator, "/system", miss));
            assertSentinelUnchanged(miss);
            // boundary: /database must not match /data
            StatFS64 boundary = newStatFS64(emulator, blocks);
            fillSentinel(boundary);
            assertFalse(ConfiguredFileStatFs.apply(emulator, "/database", boundary));
            assertSentinelUnchanged(boundary);
            // hit
            StatFS64 hit = newStatFS64(emulator, blocks);
            fillSentinel(hit);
            assertTrue(ConfiguredFileStatFs.apply(emulator, "/data/local/tmp", hit));
            assertEquals(10L, hit.f_blocks);
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testNullStatFsThrows() throws Exception {
        AndroidEmulator emulator = null;
        try {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_STATFS_JSON);
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            try {
                ConfiguredFileStatFs.apply(emulator, "/data", null);
                fail("expected IllegalArgumentException for null statFS");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage() != null && expected.getMessage().contains("statFS"));
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testSidecarEventSummary() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_STATFS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            StatFS64 full = newStatFS64(emulator, blocks);
            fillSentinel(full);
            assertTrue(ConfiguredFileStatFs.apply(emulator, "/data/./local", full));
            assertEquals(1, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("filesystem_statfs", e0.kind);
            assertEquals("statfs", e0.api);
            assertEquals("json-config", e0.source);
            assertTrue(e0.note.contains("读取配置的文件系统 statfs"));
            assertTrue(e0.note.contains("/data"));
            String v0 = String.valueOf(e0.value);
            assertTrue(v0.startsWith("mountPoint=/data"));
            assertTrue(v0.contains("type=61267"));
            assertTrue(v0.contains("blockSize=4096"));
            assertTrue(v0.contains("blocks=1000000"));
            assertTrue(v0.contains("blocksFree=500000"));
            assertTrue(v0.contains("blocksAvailable=450000"));
            assertTrue(v0.contains("files=200000"));
            assertTrue(v0.contains("filesFree=150000"));
            assertTrue(v0.contains("fsid=[1,2]"));
            assertTrue(v0.contains("nameLength=255"));
            assertTrue(v0.contains("fragmentSize=4096"));
            assertTrue(v0.contains("flags=1"));
            assertTrue(v0.indexOf("type=") < v0.indexOf("blockSize="));
            assertTrue(v0.indexOf("blockSize=") < v0.indexOf("blocks="));
            assertTrue(v0.indexOf("filesFree=") < v0.indexOf("fsid="));
            assertTrue(v0.indexOf("fsid=") < v0.indexOf("nameLength="));
            assertTrue(v0.indexOf("fragmentSize=") < v0.indexOf("flags="));

            // partial: only configured fields in value; mountPoint present
            StatFS64 partial = newStatFS64(emulator, blocks);
            fillSentinel(partial);
            assertTrue(ConfiguredFileStatFs.apply(emulator, "/partial", partial));
            assertEquals(2, sink.events.size());
            CapturedEvent e1 = sink.events.get(1);
            assertEquals("statfs", e1.api);
            String v1 = String.valueOf(e1.value);
            assertTrue(v1.startsWith("mountPoint=/partial"));
            assertTrue(v1.contains("blockSize=512"));
            assertTrue(v1.contains("blocks=42"));
            assertFalse(v1.contains("type="));
            assertFalse(v1.contains("files="));
            assertFalse(v1.contains("fsid="));
            assertFalse(v1.contains("flags="));

            // longest nested mount uses /data/user/0 entry
            StatFS64 nested = newStatFS64(emulator, blocks);
            fillSentinel(nested);
            assertTrue(ConfiguredFileStatFs.apply(emulator, "/data/user/0/com.app", nested));
            assertEquals(3, sink.events.size());
            CapturedEvent e2 = sink.events.get(2);
            String v2 = String.valueOf(e2.value);
            assertTrue(v2.startsWith("mountPoint=/data/user/0"));
            assertTrue(v2.contains("blocks=77"));
            assertFalse(v2.contains("type="));

            // miss: no extra event
            StatFS64 miss = newStatFS64(emulator, blocks);
            fillSentinel(miss);
            // without only-data config, /database falls to root /
            assertTrue(ConfiguredFileStatFs.apply(emulator, "/database", miss));
            assertEquals(4, sink.events.size());
            assertTrue(String.valueOf(sink.events.get(3).value).startsWith("mountPoint=/"));
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
    public void testNoSinkHasNoSideEffect() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_STATFS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            // no sink registered
            StatFS64 statFS = newStatFS64(emulator, blocks);
            fillSentinel(statFS);
            assertTrue(ConfiguredFileStatFs.apply(emulator, "/data", statFS));
            assertEquals(1000000L, statFS.f_blocks);
            assertEquals(4096L, ((StatFS64) statFS).f_bsize);
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runFullPartialAndLongest(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_STATFS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();

            StatFS full = is64Bit ? newStatFS64(emulator, blocks) : newStatFS32(emulator, blocks);
            fillSentinel(full);
            assertTrue(ConfiguredFileStatFs.apply(emulator, "/data", full));
            assertType(full, 61267);
            assertBlockSize(full, 4096);
            assertEquals(1000000L, full.f_blocks);
            assertEquals(500000L, full.f_bfree);
            assertEquals(450000L, full.f_bavail);
            assertEquals(200000L, full.f_files);
            assertEquals(150000L, full.f_ffree);
            assertArrayEquals(new int[]{1, 2}, full.f_fsid);
            assertNameLen(full, 255);
            assertFrSize(full, 4096);
            assertFlags(full, 1);

            // partial: only configured fields overwritten
            StatFS partial = is64Bit ? newStatFS64(emulator, blocks) : newStatFS32(emulator, blocks);
            fillSentinel(partial);
            assertTrue(ConfiguredFileStatFs.apply(emulator, "/partial", partial));
            assertEquals(42L, partial.f_blocks);
            assertBlockSize(partial, 512);
            // unconfigured keep sentinel
            assertType(partial, 0x0abc);
            assertEquals(0x2222222222222222L, partial.f_bfree);
            assertEquals(0x3333333333333333L, partial.f_bavail);
            assertEquals(0x4444444444444444L, partial.f_files);
            assertEquals(0x5555555555555555L, partial.f_ffree);
            assertArrayEquals(new int[]{0x6666, 0x7777}, partial.f_fsid);
            assertNameLen(partial, 0x8888);
            assertFrSize(partial, 0x9999);
            assertFlags(partial, 0xaaaa);

            // longest nested mount
            StatFS nested = is64Bit ? newStatFS64(emulator, blocks) : newStatFS32(emulator, blocks);
            fillSentinel(nested);
            assertTrue(ConfiguredFileStatFs.apply(emulator, "/data/user/0/com.demo", nested));
            assertEquals(77L, nested.f_blocks);
            assertType(nested, 0x0abc); // only blocks configured on /data/user/0
            assertBlockSize(nested, 0x1111);

            // root fallback
            StatFS rootHit = is64Bit ? newStatFS64(emulator, blocks) : newStatFS32(emulator, blocks);
            fillSentinel(rootHit);
            assertTrue(ConfiguredFileStatFs.apply(emulator, "/proc/cpuinfo", rootHit));
            assertType(rootHit, 1);
            assertEquals(100L, rootHit.f_blocks);
            assertBlockSize(rootHit, 1024);

            // normalized path
            StatFS norm = is64Bit ? newStatFS64(emulator, blocks) : newStatFS32(emulator, blocks);
            fillSentinel(norm);
            assertTrue(ConfiguredFileStatFs.apply(emulator, "/data/./user/../user/0/x", norm));
            assertEquals(77L, norm.f_blocks);
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runWide(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_STATFS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();

            StatFS wide = is64Bit ? newStatFS64(emulator, blocks) : newStatFS32(emulator, blocks);
            fillSentinel(wide);
            assertTrue(ConfiguredFileStatFs.apply(emulator, "/wide", wide));
            assertType(wide, -1);
            assertFlags(wide, -1);
            assertArrayEquals(new int[]{-1, -1}, wide.f_fsid);
            // unconfigured remain sentinel
            assertEquals(0x1111111111111111L, wide.f_blocks);
            assertBlockSize(wide, 0x1111);
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void fillSentinel(StatFS statFS) {
        statFS.setType(0x0abc);
        statFS.setBlockSize(0x1111);
        statFS.f_blocks = 0x1111111111111111L;
        statFS.f_bfree = 0x2222222222222222L;
        statFS.f_bavail = 0x3333333333333333L;
        statFS.f_files = 0x4444444444444444L;
        statFS.f_ffree = 0x5555555555555555L;
        statFS.f_fsid = new int[]{0x6666, 0x7777};
        statFS.setNameLen(0x8888);
        statFS.setFrSize(0x9999);
        statFS.setFlags(0xaaaa);
    }

    private static void assertSentinelUnchanged(StatFS statFS) {
        assertType(statFS, 0x0abc);
        assertBlockSize(statFS, 0x1111);
        assertEquals(0x1111111111111111L, statFS.f_blocks);
        assertEquals(0x2222222222222222L, statFS.f_bfree);
        assertEquals(0x3333333333333333L, statFS.f_bavail);
        assertEquals(0x4444444444444444L, statFS.f_files);
        assertEquals(0x5555555555555555L, statFS.f_ffree);
        assertArrayEquals(new int[]{0x6666, 0x7777}, statFS.f_fsid);
        assertNameLen(statFS, 0x8888);
        assertFrSize(statFS, 0x9999);
        assertFlags(statFS, 0xaaaa);
    }

    private static void assertType(StatFS statFS, int expected) {
        if (statFS instanceof StatFS32) {
            assertEquals(expected, ((StatFS32) statFS).f_type);
        } else {
            assertEquals((long) expected, ((StatFS64) statFS).f_type);
        }
    }

    private static void assertBlockSize(StatFS statFS, int expected) {
        if (statFS instanceof StatFS32) {
            assertEquals(expected, ((StatFS32) statFS).f_bsize);
        } else {
            assertEquals((long) expected, ((StatFS64) statFS).f_bsize);
        }
    }

    private static void assertNameLen(StatFS statFS, int expected) {
        if (statFS instanceof StatFS32) {
            assertEquals(expected, ((StatFS32) statFS).f_namelen);
        } else {
            assertEquals((long) expected, ((StatFS64) statFS).f_namelen);
        }
    }

    private static void assertFrSize(StatFS statFS, int expected) {
        if (statFS instanceof StatFS32) {
            assertEquals(expected, ((StatFS32) statFS).f_frsize);
        } else {
            assertEquals((long) expected, ((StatFS64) statFS).f_frsize);
        }
    }

    private static void assertFlags(StatFS statFS, int expected) {
        if (statFS instanceof StatFS32) {
            assertEquals(expected, ((StatFS32) statFS).f_flags);
        } else {
            assertEquals((long) expected, ((StatFS64) statFS).f_flags);
        }
    }

    private static StatFS32 newStatFS32(Emulator<?> emulator, List<MemoryBlock> blocks) {
        int size = UnidbgStructure.calculateSize(StatFS32.class);
        MemoryBlock block = emulator.getMemory().malloc(size, true);
        blocks.add(block);
        return new StatFS32(block.getPointer());
    }

    private static StatFS64 newStatFS64(Emulator<?> emulator, List<MemoryBlock> blocks) {
        int size = UnidbgStructure.calculateSize(StatFS64.class);
        MemoryBlock block = emulator.getMemory().malloc(size, true);
        blocks.add(block);
        return new StatFS64(block.getPointer());
    }

    private static void freeAll(List<MemoryBlock> blocks) {
        for (MemoryBlock block : blocks) {
            try {
                block.free();
            } catch (Exception ignored) {
                // ignore free failures in teardown
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

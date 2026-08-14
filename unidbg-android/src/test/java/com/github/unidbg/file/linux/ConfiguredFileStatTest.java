package com.github.unidbg.file.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.file.ByteArrayFileIO;
import com.github.unidbg.linux.struct.Stat32;
import com.github.unidbg.linux.struct.Stat64;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgStructure;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfiguredFileStatTest {

    private static final String FULL_STAT_JSON = "{"
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
            + "\"/partial\":{"
            + "\"size\":42,"
            + "\"mode\":33188"
            + "},"
            + "\"/wide\":{"
            + "\"mode\":4294967295,"
            + "\"uid\":4294967295,"
            + "\"gid\":4294967295"
            + "},"
            + "\"/negtime\":{"
            + "\"atimeMillis\":-1,"
            + "\"mtimeMillis\":-1001,"
            + "\"ctimeMillis\":1001"
            + "}"
            + "}}"
            + "}";

    @Test
    public void testApplyFullAndPartial32() throws Exception {
        runFullAndPartial(false);
    }

    @Test
    public void testApplyFullAndPartial64() throws Exception {
        runFullAndPartial(true);
    }

    @Test
    public void testNormalizedPathHitAndMiss32() throws Exception {
        runNormalizedAndMiss(false);
    }

    @Test
    public void testNormalizedPathHitAndMiss64() throws Exception {
        runNormalizedAndMiss(true);
    }

    @Test
    public void testWideModeUidGidAndNegativeTimes32() throws Exception {
        runWideAndNegTimes(false);
    }

    @Test
    public void testWideModeUidGidAndNegativeTimes64() throws Exception {
        runWideAndNegTimes(true);
    }

    @Test
    public void testNoConfigDoesNotChangeStat() throws Exception {
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            Stat64 stat = newStat64(emulator, blocks);
            fillSentinel(stat);
            assertFalse(ConfiguredFileStat.apply(emulator, "/any", stat));
            assertSentinelUnchanged(stat);
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testNullStatThrows() throws Exception {
        AndroidEmulator emulator = null;
        try {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_STAT_JSON);
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            try {
                ConfiguredFileStat.apply(emulator, "/data/user/0/com.demo.app", null);
                fail("expected IllegalArgumentException for null stat");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage() != null && expected.getMessage().contains("stat"));
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testFileIoOverloadHitAndSafeMiss() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_STAT_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();

            // ByteArrayFileIO with configured path: overlay hits
            Stat64 hit = newStat64(emulator, blocks);
            fillSentinel(hit);
            ByteArrayFileIO io = new ByteArrayFileIO(IOConstants.O_RDONLY,
                    "/data/user/0/com.demo.app", new byte[]{'x'});
            assertTrue(ConfiguredFileStat.apply(emulator, io, hit));
            assertEquals(33L, hit.st_dev);
            assertEquals(1001L, hit.st_ino);
            assertEquals(16877, hit.st_mode);
            assertEquals(4096L, hit.st_size);

            // minimal FileIO without getPath override: false, structure unchanged, no throw
            Stat64 miss = newStat64(emulator, blocks);
            fillSentinel(miss);
            BaseAndroidFileIO noPath = new BaseAndroidFileIO(IOConstants.O_RDONLY) {
            };
            assertFalse(ConfiguredFileStat.apply(emulator, noPath, miss));
            assertSentinelUnchanged(miss);

            // null file: false, no throw; null stat still IAE via String path overload
            assertFalse(ConfiguredFileStat.apply(emulator, (com.github.unidbg.file.FileIO) null, miss));
            assertSentinelUnchanged(miss);
            try {
                ConfiguredFileStat.apply(emulator, io, null);
                fail("expected IllegalArgumentException for null stat via FileIO overload");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage() != null && expected.getMessage().contains("stat"));
            }
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testSidecarEventsForStatAndFstat() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_STAT_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            // path apply → api=stat, full field summary
            Stat64 full = newStat64(emulator, blocks);
            fillSentinel(full);
            assertTrue(ConfiguredFileStat.apply(emulator, "/data/./user/0/com.demo.app", full));
            assertEquals(1, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("filesystem_stat", e0.kind);
            assertEquals("stat", e0.api);
            assertEquals("json-config", e0.source);
            assertTrue(e0.note.contains("读取配置的文件元数据"));
            assertTrue(e0.note.contains("/data/user/0/com.demo.app"));
            String v0 = String.valueOf(e0.value);
            assertTrue(v0.startsWith("path=/data/user/0/com.demo.app"));
            assertTrue(v0.contains("device=33"));
            assertTrue(v0.contains("inode=1001"));
            assertTrue(v0.contains("mode=16877"));
            assertTrue(v0.contains("uid=1000"));
            assertTrue(v0.contains("gid=1000"));
            assertTrue(v0.contains("size=4096"));
            assertTrue(v0.contains("blockSize=4096"));
            assertTrue(v0.contains("blocks=8"));
            assertTrue(v0.contains("atimeMillis=1718000000000"));
            assertTrue(v0.contains("mtimeMillis=1718000001000"));
            assertTrue(v0.contains("ctimeMillis=1718000002000"));
            // fixed field order: device before inode before mode ...
            assertTrue(v0.indexOf("device=") < v0.indexOf("inode="));
            assertTrue(v0.indexOf("inode=") < v0.indexOf("mode="));
            assertTrue(v0.indexOf("size=") < v0.indexOf("blockSize="));
            assertTrue(v0.indexOf("blockSize=") < v0.indexOf("blocks="));

            // partial apply → only configured fields in value
            Stat64 partial = newStat64(emulator, blocks);
            fillSentinel(partial);
            assertTrue(ConfiguredFileStat.apply(emulator, "/partial", partial));
            assertEquals(2, sink.events.size());
            CapturedEvent e1 = sink.events.get(1);
            assertEquals("stat", e1.api);
            String v1 = String.valueOf(e1.value);
            assertTrue(v1.startsWith("path=/partial"));
            assertTrue(v1.contains("mode=33188"));
            assertTrue(v1.contains("size=42"));
            assertFalse(v1.contains("device="));
            assertFalse(v1.contains("inode="));
            assertFalse(v1.contains("uid="));
            assertFalse(v1.contains("blockSize="));
            assertFalse(v1.contains("atimeMillis="));

            // FileIO overload → api=fstat
            Stat64 fstatHit = newStat64(emulator, blocks);
            fillSentinel(fstatHit);
            ByteArrayFileIO io = new ByteArrayFileIO(IOConstants.O_RDONLY,
                    "/data/user/0/com.demo.app", new byte[]{'x'});
            assertTrue(ConfiguredFileStat.apply(emulator, io, fstatHit));
            assertEquals(3, sink.events.size());
            CapturedEvent e2 = sink.events.get(2);
            assertEquals("filesystem_stat", e2.kind);
            assertEquals("fstat", e2.api);
            assertEquals("json-config", e2.source);
            assertTrue(String.valueOf(e2.value).startsWith("path=/data/user/0/com.demo.app"));
            assertTrue(e2.note.contains("读取配置的文件元数据 /data/user/0/com.demo.app"));

            // miss: no extra event
            Stat64 miss = newStat64(emulator, blocks);
            fillSentinel(miss);
            assertFalse(ConfiguredFileStat.apply(emulator, "/missing", miss));
            assertEquals(3, sink.events.size());

            // no-getPath FileIO: no event
            BaseAndroidFileIO noPath = new BaseAndroidFileIO(IOConstants.O_RDONLY) {
            };
            assertFalse(ConfiguredFileStat.apply(emulator, noPath, miss));
            assertEquals(3, sink.events.size());
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

    private static void runFullAndPartial(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_STAT_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();

            StatStructure full = is64Bit ? newStat64(emulator, blocks) : newStat32(emulator, blocks);
            fillSentinel(full);
            assertTrue(ConfiguredFileStat.apply(emulator, "/data/user/0/com.demo.app", full));
            assertEquals(33L, full.st_dev);
            assertEquals(1001L, full.st_ino);
            assertEquals(16877, full.st_mode);
            assertEquals(1000, full.st_uid);
            assertEquals(1000, full.st_gid);
            assertEquals(4096L, full.st_size);
            assertEquals(4096, full.st_blksize);
            assertEquals(8L, full.st_blocks);
            assertTimes(full, 1718000000000L, 1718000001000L, 1718000002000L);

            StatStructure partial = is64Bit ? newStat64(emulator, blocks) : newStat32(emulator, blocks);
            fillSentinel(partial);
            assertTrue(ConfiguredFileStat.apply(emulator, "/partial", partial));
            assertEquals(42L, partial.st_size);
            assertEquals(33188, partial.st_mode);
            // unconfigured fields keep sentinel
            assertEquals(0x1111111111111111L, partial.st_dev);
            assertEquals(0x2222222222222222L, partial.st_ino);
            assertEquals(0x3333, partial.st_uid);
            assertEquals(0x4444, partial.st_gid);
            assertEquals(0x5555, partial.st_blksize);
            assertEquals(0x6666666666666666L, partial.st_blocks);
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runNormalizedAndMiss(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_STAT_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();

            StatStructure hit = is64Bit ? newStat64(emulator, blocks) : newStat32(emulator, blocks);
            fillSentinel(hit);
            assertTrue(ConfiguredFileStat.apply(emulator,
                    "/data/./user/../user/0/com.demo.app", hit));
            assertEquals(33L, hit.st_dev);
            assertEquals(1001L, hit.st_ino);
            assertEquals(4096L, hit.st_size);

            StatStructure miss = is64Bit ? newStat64(emulator, blocks) : newStat32(emulator, blocks);
            fillSentinel(miss);
            assertFalse(ConfiguredFileStat.apply(emulator, "/missing", miss));
            assertSentinelUnchanged(miss);
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void runWideAndNegTimes(boolean is64Bit) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_STAT_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();

            StatStructure wide = is64Bit ? newStat64(emulator, blocks) : newStat32(emulator, blocks);
            fillSentinel(wide);
            assertTrue(ConfiguredFileStat.apply(emulator, "/wide", wide));
            assertEquals(-1, wide.st_mode);
            assertEquals(-1, wide.st_uid);
            assertEquals(-1, wide.st_gid);

            StatStructure neg = is64Bit ? newStat64(emulator, blocks) : newStat32(emulator, blocks);
            fillSentinel(neg);
            assertTrue(ConfiguredFileStat.apply(emulator, "/negtime", neg));
            assertTimes(neg, -1L, -1001L, 1001L);
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void assertTimes(StatStructure stat, long atimeMs, long mtimeMs, long ctimeMs)
            throws Exception {
        long[] a = readTimespec(stat, "st_atim");
        long[] m = readTimespec(stat, "st_mtim");
        long[] c = readTimespec(stat, "st_ctim");
        assertEquals(Math.floorDiv(atimeMs, 1000L), a[0]);
        assertEquals(Math.floorMod(atimeMs, 1000L) * 1000000L, a[1]);
        assertEquals(Math.floorDiv(mtimeMs, 1000L), m[0]);
        assertEquals(Math.floorMod(mtimeMs, 1000L) * 1000000L, m[1]);
        assertEquals(Math.floorDiv(ctimeMs, 1000L), c[0]);
        assertEquals(Math.floorMod(ctimeMs, 1000L) * 1000000L, c[1]);
        assertTrue(a[1] >= 0 && a[1] <= 999_999_999L);
        assertTrue(m[1] >= 0 && m[1] <= 999_999_999L);
        assertTrue(c[1] >= 0 && c[1] <= 999_999_999L);
    }

    private static long[] readTimespec(StatStructure stat, String fieldName) throws Exception {
        Field field = stat.getClass().getField(fieldName);
        Object ts = field.get(stat);
        Field sec = ts.getClass().getField("tv_sec");
        Field nsec = ts.getClass().getField("tv_nsec");
        return new long[]{((Number) sec.get(ts)).longValue(), ((Number) nsec.get(ts)).longValue()};
    }

    private static void fillSentinel(StatStructure stat) {
        stat.st_dev = 0x1111111111111111L;
        stat.setSt_ino(0x2222222222222222L);
        stat.st_mode = 0x0abc;
        stat.st_uid = 0x3333;
        stat.st_gid = 0x4444;
        stat.st_size = 0x7777777777777777L;
        stat.st_blksize = 0x5555;
        stat.st_blocks = 0x6666666666666666L;
        if (stat instanceof Stat32) {
            Stat32 s = (Stat32) stat;
            s.st_atim.tv_sec = 7;
            s.st_atim.tv_nsec = 7;
            s.st_mtim.tv_sec = 8;
            s.st_mtim.tv_nsec = 8;
            s.st_ctim.tv_sec = 9;
            s.st_ctim.tv_nsec = 9;
        } else if (stat instanceof Stat64) {
            Stat64 s = (Stat64) stat;
            s.st_atim.tv_sec = 7L;
            s.st_atim.tv_nsec = 7L;
            s.st_mtim.tv_sec = 8L;
            s.st_mtim.tv_nsec = 8L;
            s.st_ctim.tv_sec = 9L;
            s.st_ctim.tv_nsec = 9L;
        }
    }

    private static void assertSentinelUnchanged(StatStructure stat) {
        assertEquals(0x1111111111111111L, stat.st_dev);
        assertEquals(0x2222222222222222L, stat.st_ino);
        assertEquals(0x0abc, stat.st_mode);
        assertEquals(0x3333, stat.st_uid);
        assertEquals(0x4444, stat.st_gid);
        assertEquals(0x7777777777777777L, stat.st_size);
        assertEquals(0x5555, stat.st_blksize);
        assertEquals(0x6666666666666666L, stat.st_blocks);
    }

    private static Stat32 newStat32(Emulator<?> emulator, List<MemoryBlock> blocks) {
        int size = UnidbgStructure.calculateSize(Stat32.class);
        MemoryBlock block = emulator.getMemory().malloc(size, true);
        blocks.add(block);
        return new Stat32(block.getPointer());
    }

    private static Stat64 newStat64(Emulator<?> emulator, List<MemoryBlock> blocks) {
        int size = UnidbgStructure.calculateSize(Stat64.class);
        MemoryBlock block = emulator.getMemory().malloc(size, true);
        blocks.add(block);
        return new Stat64(block.getPointer());
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
}

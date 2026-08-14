package com.github.unidbg.linux.struct;

import com.github.unidbg.file.linux.StatStructure;
import com.github.unidbg.pointer.UnidbgStructure;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies millis → (tv_sec, tv_nsec) conversion used by {@link Stat32}/{@link Stat64}.
 */
public class StatTimeConversionTest {

    @Test
    public void testMillisToSecondsAndNanosHelpers() throws Exception {
        Method toSec = StatStructure.class.getDeclaredMethod("millisToSeconds", long.class);
        toSec.setAccessible(true);
        Method toNsec = StatStructure.class.getDeclaredMethod("millisToNanos", long.class, long.class);
        toNsec.setAccessible(true);

        assertEquals(-1L, (long) toSec.invoke(null, -1L));
        assertEquals(999000000L, (long) toNsec.invoke(null, -1L, 0L));

        assertEquals(-2L, (long) toSec.invoke(null, -1001L));
        assertEquals(999000000L, (long) toNsec.invoke(null, -1001L, 0L));

        assertEquals(1L, (long) toSec.invoke(null, 1001L));
        assertEquals(1000000L, (long) toNsec.invoke(null, 1001L, 0L));

        // tv_nsec adjustment folds into 0..999_999_999
        assertEquals(0L, (long) toSec.invoke(null, 0L));
        assertEquals(500000L, (long) toNsec.invoke(null, 0L, 1_500_000L));

        long nsecNeg = (long) toNsec.invoke(null, -1L, 0L);
        long nsecNeg2 = (long) toNsec.invoke(null, -1001L, 0L);
        assertTrue(nsecNeg >= 0 && nsecNeg <= 999_999_999L);
        assertTrue(nsecNeg2 >= 0 && nsecNeg2 <= 999_999_999L);
    }

    @Test
    public void testStat32AtimMtimCtim() throws Exception {
        Stat32 stat = newStat32();
        assertTimes32(stat, -1L, -1, 999000000);
        assertTimes32(stat, -1001L, -2, 999000000);
        assertTimes32(stat, 1001L, 1, 1000000);
    }

    @Test
    public void testStat64AtimMtimCtim() throws Exception {
        Stat64 stat = newStat64();
        assertTimes64(stat, -1L, -1L, 999000000L);
        assertTimes64(stat, -1001L, -2L, 999000000L);
        assertTimes64(stat, 1001L, 1L, 1000000L);
    }

    private static void assertTimes32(Stat32 stat, long millis, int expectedSec, int expectedNsec) {
        stat.setSt_atim(millis, 0L);
        assertEquals("atim.sec millis=" + millis, expectedSec, stat.st_atim.tv_sec);
        assertEquals("atim.nsec millis=" + millis, expectedNsec, stat.st_atim.tv_nsec);

        stat.setSt_mtim(millis, 0L);
        assertEquals("mtim.sec millis=" + millis, expectedSec, stat.st_mtim.tv_sec);
        assertEquals("mtim.nsec millis=" + millis, expectedNsec, stat.st_mtim.tv_nsec);

        stat.setSt_ctim(millis, 0L);
        assertEquals("ctim.sec millis=" + millis, expectedSec, stat.st_ctim.tv_sec);
        assertEquals("ctim.nsec millis=" + millis, expectedNsec, stat.st_ctim.tv_nsec);
    }

    private static void assertTimes64(Stat64 stat, long millis, long expectedSec, long expectedNsec) {
        stat.setSt_atim(millis, 0L);
        assertEquals("atim.sec millis=" + millis, expectedSec, stat.st_atim.tv_sec);
        assertEquals("atim.nsec millis=" + millis, expectedNsec, stat.st_atim.tv_nsec);

        stat.setSt_mtim(millis, 0L);
        assertEquals("mtim.sec millis=" + millis, expectedSec, stat.st_mtim.tv_sec);
        assertEquals("mtim.nsec millis=" + millis, expectedNsec, stat.st_mtim.tv_nsec);

        stat.setSt_ctim(millis, 0L);
        assertEquals("ctim.sec millis=" + millis, expectedSec, stat.st_ctim.tv_sec);
        assertEquals("ctim.nsec millis=" + millis, expectedNsec, stat.st_ctim.tv_nsec);
    }

    private static Stat32 newStat32() throws Exception {
        Pointer p = placeholderMemory();
        Stat32 stat = new Stat32(p);
        Field atim = Stat32.class.getField("st_atim");
        Field mtim = Stat32.class.getField("st_mtim");
        Field ctim = Stat32.class.getField("st_ctim");
        if (atim.get(stat) == null || mtim.get(stat) == null || ctim.get(stat) == null) {
            Method ensure = UnidbgStructure.class.getSuperclass().getDeclaredMethod("ensureAllocated");
            ensure.setAccessible(true);
            ensure.invoke(stat);
        }
        return stat;
    }

    private static Stat64 newStat64() throws Exception {
        Pointer p = placeholderMemory();
        Stat64 stat = new Stat64(p);
        Field atim = Stat64.class.getField("st_atim");
        Field mtim = Stat64.class.getField("st_mtim");
        Field ctim = Stat64.class.getField("st_ctim");
        if (atim.get(stat) == null || mtim.get(stat) == null || ctim.get(stat) == null) {
            Method ensure = UnidbgStructure.class.getSuperclass().getDeclaredMethod("ensureAllocated");
            ensure.setAccessible(true);
            ensure.invoke(stat);
        }
        return stat;
    }

    private static Pointer placeholderMemory() throws Exception {
        Field field = UnidbgStructure.class.getDeclaredField("PLACEHOLDER_MEMORY");
        field.setAccessible(true);
        return (Pointer) field.get(null);
    }
}

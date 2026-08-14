package com.github.unidbg.file.linux;

import com.github.unidbg.env.TraceEnvironmentConfig;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link ConfiguredMountFiles}. Config-only; no Emulator/FileIO.
 */
public class ConfiguredMountFilesTest {

    @Test
    public void testRenderProcMountsFullOrderAndEmptyOptional() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"mounts\":["
                + "{\"source\":\"/dev/block/dm-0\",\"target\":\"/\",\"fileSystemType\":\"ext4\","
                + "\"options\":\"ro,seclabel,relatime\",\"dump\":1,\"pass\":1,"
                + "\"mountId\":21,\"parentId\":1,\"major\":253,\"minor\":0,"
                + "\"root\":\"/\",\"mountOptions\":\"ro,seclabel,relatime\","
                + "\"optionalFields\":[],\"superOptions\":\"rw,seclabel\"},"
                + "{\"source\":\"/dev/block/dm-1\",\"target\":\"/data\",\"fileSystemType\":\"f2fs\","
                + "\"options\":\"rw,nosuid,nodev,noatime\",\"dump\":0,\"pass\":2,"
                + "\"mountId\":45,\"parentId\":21,\"major\":253,\"minor\":1,"
                + "\"root\":\"/\",\"mountOptions\":\"rw,nosuid,nodev,noatime\","
                + "\"optionalFields\":[\"shared:2\",\"master:1\"],\"superOptions\":\"rw\"}"
                + "]}}"
        );

        byte[] mountsBytes = ConfiguredMountFiles.renderProcMounts(config);
        assertNotNull(mountsBytes);
        String mountsText = new String(mountsBytes, StandardCharsets.UTF_8);
        assertEquals(
                "/dev/block/dm-0 / ext4 ro,seclabel,relatime 1 1\n"
                        + "/dev/block/dm-1 /data f2fs rw,nosuid,nodev,noatime 0 2\n",
                mountsText);

        byte[] infoBytes = ConfiguredMountFiles.renderMountInfo(config);
        assertNotNull(infoBytes);
        String infoText = new String(infoBytes, StandardCharsets.UTF_8);
        assertEquals(
                "21 1 253:0 / / ro,seclabel,relatime - ext4 /dev/block/dm-0 rw,seclabel\n"
                        + "45 21 253:1 / /data rw,nosuid,nodev,noatime shared:2 master:1 - f2fs /dev/block/dm-1 rw\n",
                infoText);
    }

    @Test
    public void testEscapeSpecialCharsInSourceTargetRoot() {
        // source may contain space/backslash (not path-normalized); target/root are POSIX paths
        // (backslash/NUL illegal in path keys) so use space/tab there.
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"mounts\":[{"
                + "\"source\":\"mapper name\\\\dev\","
                + "\"target\":\"/mnt/foo bar\","
                + "\"fileSystemType\":\"ext4\","
                + "\"options\":\"rw\","
                + "\"dump\":0,"
                + "\"pass\":0,"
                + "\"mountId\":1,"
                + "\"parentId\":1,"
                + "\"major\":8,"
                + "\"minor\":1,"
                + "\"root\":\"/x\\ty\","
                + "\"mountOptions\":\"rw\","
                + "\"superOptions\":\"rw\""
                + "}]}}"
        );

        String mountsText = new String(ConfiguredMountFiles.renderProcMounts(config),
                StandardCharsets.UTF_8);
        assertEquals("mapper\\040name\\134dev /mnt/foo\\040bar ext4 rw 0 0\n", mountsText);

        String infoText = new String(ConfiguredMountFiles.renderMountInfo(config),
                StandardCharsets.UTF_8);
        assertEquals(
                "1 1 8:1 /x\\011y /mnt/foo\\040bar rw - ext4 mapper\\040name\\134dev rw\n",
                infoText);

        // unit: char-by-char order avoids double-escaping backslash in escape sequences
        assertEquals("\\134040", ConfiguredMountFiles.escapeMountField("\\040"));
        assertEquals("a\\040b", ConfiguredMountFiles.escapeMountField("a b"));
        assertEquals("a\\011b", ConfiguredMountFiles.escapeMountField("a\tb"));
        assertEquals("a\\012b", ConfiguredMountFiles.escapeMountField("a\nb"));
        assertEquals("a\\134b", ConfiguredMountFiles.escapeMountField("a\\b"));
    }

    @Test
    public void testNotConfiguredReturnsNull() {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse("{}");
        assertNull(ConfiguredMountFiles.renderProcMounts(missing));
        assertNull(ConfiguredMountFiles.renderMountInfo(missing));

        TraceEnvironmentConfig noMountsKey = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"stat\":{}}}");
        assertNull(ConfiguredMountFiles.renderProcMounts(noMountsKey));
        assertNull(ConfiguredMountFiles.renderMountInfo(noMountsKey));

        assertNull(ConfiguredMountFiles.renderProcMounts(null));
        assertNull(ConfiguredMountFiles.renderMountInfo(null));
    }

    @Test
    public void testExplicitEmptyArrayReturnsEmptyBytes() {
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"filesystem\":{\"mounts\":[]}}");
        assertTrue(empty.isFilesystemMountsConfigured());

        byte[] mounts = ConfiguredMountFiles.renderProcMounts(empty);
        assertNotNull(mounts);
        assertEquals(0, mounts.length);

        byte[] info = ConfiguredMountFiles.renderMountInfo(empty);
        assertNotNull(info);
        assertEquals(0, info.length);

        // independent empty arrays (clone EMPTY)
        byte[] mounts2 = ConfiguredMountFiles.renderProcMounts(empty);
        byte[] info2 = ConfiguredMountFiles.renderMountInfo(empty);
        assertEquals(0, mounts2.length);
        assertEquals(0, info2.length);
        assertFalse(mounts == mounts2);
        assertFalse(info == info2);
    }

    @Test
    public void testMountInfoIncompleteReturnsNullButProcMountsStillRenders() {
        // minimal entries without mountinfo group
        TraceEnvironmentConfig minimal = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"mounts\":["
                + "{\"source\":\"tmpfs\",\"target\":\"/dev\",\"fileSystemType\":\"tmpfs\","
                + "\"options\":\"rw,seclabel\"},"
                + "{\"source\":\"/dev/block/dm-1\",\"target\":\"/data\",\"fileSystemType\":\"f2fs\","
                + "\"options\":\"rw\"}"
                + "]}}"
        );

        byte[] mounts = ConfiguredMountFiles.renderProcMounts(minimal);
        assertNotNull(mounts);
        assertEquals(
                "tmpfs /dev tmpfs rw,seclabel 0 0\n"
                        + "/dev/block/dm-1 /data f2fs rw 0 0\n",
                new String(mounts, StandardCharsets.UTF_8));

        assertNull(ConfiguredMountFiles.renderMountInfo(minimal));

        // mix: first complete, second incomplete → whole mountinfo null
        TraceEnvironmentConfig mixed = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"mounts\":["
                + "{\"source\":\"a\",\"target\":\"/a\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                + "\"mountId\":1,\"parentId\":1,\"major\":0,\"minor\":0},"
                + "{\"source\":\"b\",\"target\":\"/b\",\"fileSystemType\":\"ext4\",\"options\":\"rw\"}"
                + "]}}"
        );
        assertNotNull(ConfiguredMountFiles.renderProcMounts(mixed));
        assertNull(ConfiguredMountFiles.renderMountInfo(mixed));
    }

    @Test
    public void testReturnedBytesAreIndependentCopies() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"filesystem\":{\"mounts\":[{"
                + "\"source\":\"s\",\"target\":\"/t\",\"fileSystemType\":\"ext4\",\"options\":\"rw\","
                + "\"mountId\":2,\"parentId\":1,\"major\":8,\"minor\":0"
                + "}]}}"
        );

        byte[] a = ConfiguredMountFiles.renderProcMounts(config);
        byte[] b = ConfiguredMountFiles.renderProcMounts(config);
        assertNotNull(a);
        assertNotNull(b);
        assertFalse(a == b);
        assertArrayEquals(a, b);
        a[0] = (byte) ('Z');
        assertFalse(Arrays.equals(a, b));

        byte[] i1 = ConfiguredMountFiles.renderMountInfo(config);
        byte[] i2 = ConfiguredMountFiles.renderMountInfo(config);
        assertNotNull(i1);
        assertNotNull(i2);
        assertFalse(i1 == i2);
        assertArrayEquals(i1, i2);
        i1[0] = (byte) ('Z');
        assertFalse(Arrays.equals(i1, i2));
    }
}

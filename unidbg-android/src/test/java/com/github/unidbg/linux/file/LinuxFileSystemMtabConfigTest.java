package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.ConfiguredMountFiles;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * LinuxFileSystem wiring for {@code filesystem.mounts} → exact read-only {@code /etc/mtab}
 * as a compatible view of {@code ConfiguredMountFiles.renderProcMounts}. Default backend.
 * Every emulator is closed in {@code finally} without swallowing {@code close}.
 */
public class LinuxFileSystemMtabConfigTest {

    private static final int CONFIG_PID = 4242;
    private static final String MTAB_PATH = "/etc/mtab";
    private static final String PROC_MOUNTS_PATH = "/proc/mounts";

    private static final String FULL_MOUNTS_JSON = "{"
            + "\"process\":{\"pid\":" + CONFIG_PID + "},"
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
            + "\"optionalFields\":[\"shared:2\"],\"superOptions\":\"rw\"}"
            + "]}}";

    private static final String EXPECTED_MOUNTS =
            "/dev/block/dm-0 / ext4 ro,seclabel,relatime 1 1\n"
                    + "/dev/block/dm-1 /data f2fs rw,nosuid,nodev,noatime 0 2\n";

    private static final String FILES_OVERRIDE = "CUSTOM_MTAB\n";

    @Test
    public void testMtabBytesMatchProcMountsAndSidecarSummary() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_MOUNTS_JSON);
        byte[] rendered = ConfiguredMountFiles.renderProcMounts(config);
        assertNotNull(rendered);
        assertEquals(EXPECTED_MOUNTS, new String(rendered, StandardCharsets.UTF_8));

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            byte[] mtab = readOpenBytes(emulator, blocks, MTAB_PATH);
            byte[] procMounts = readOpenBytes(emulator, blocks, PROC_MOUNTS_PATH);
            assertArrayEqualsBytes(rendered, mtab);
            assertArrayEqualsBytes(procMounts, mtab);
            assertEquals(EXPECTED_MOUNTS, new String(mtab, StandardCharsets.UTF_8));
            assertEquals('\n', mtab[mtab.length - 1] & 0xff);

            CapturedEvent e = findMtabRead(sink, MTAB_PATH);
            assertMtabSidecar(e, MTAB_PATH, 2, mtab.length);
            assertSidecarRedacted(e);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testExplicitEmptyArrayZeroBytes() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":" + CONFIG_PID + "},\"filesystem\":{\"mounts\":[]}}");
        assertTrue(config.isFilesystemMountsConfigured());
        byte[] rendered = ConfiguredMountFiles.renderProcMounts(config);
        assertNotNull(rendered);
        assertEquals(0, rendered.length);

        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open(MTAB_PATH, IOConstants.O_RDONLY);
            assertNotNull(result);
            assertTrue("explicit [] must take over /etc/mtab", result.isSuccess());
            assertNotNull(result.io);
            assertTrue("empty mounts must serve ByteArrayFileIO, not host fallback",
                    result.io instanceof ByteArrayFileIO);
            byte[] mtab = readIoBytes(emulator, blocks, result.io, 8192);
            assertEquals(0, mtab.length);
            assertArrayEqualsBytes(rendered, mtab);
            assertEquals("", readOpenText(emulator, blocks, PROC_MOUNTS_PATH));

            CapturedEvent e = findMtabRead(sink, MTAB_PATH);
            assertMtabSidecar(e, MTAB_PATH, 0, 0);
            assertSidecarRedacted(e);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testMissingDoesNotTakeOver() throws Exception {
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredMtab(emulator, blocks, sink, MTAB_PATH);
            assertEquals(0, countMtabReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }

        TraceEnvironmentConfig filesystemNoMounts = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":" + CONFIG_PID + "},\"filesystem\":{\"stat\":{}}}");
        assertFalse(filesystemNoMounts.isFilesystemMountsConfigured());
        sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit()
                    .setEnvironmentConfig(filesystemNoMounts).build();
            TraceEnvironmentEventSink.register(emulator, sink);
            assertNotTakenOverAsConfiguredMtab(emulator, blocks, sink, MTAB_PATH);
            assertEquals(0, countMtabReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testLinuxFilesTakesPriorityWithoutMountsEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"files\":{\"" + MTAB_PATH + "\":\""
                + FILES_OVERRIDE.replace("\n", "\\n") + "\"}},"
                + "\"filesystem\":{\"mounts\":["
                + "{\"source\":\"/dev/block/dm-0\",\"target\":\"/\",\"fileSystemType\":\"ext4\","
                + "\"options\":\"rw\",\"mountId\":1,\"parentId\":1,\"major\":0,\"minor\":0}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertEquals(FILES_OVERRIDE, readOpenText(emulator, blocks, MTAB_PATH));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                if (isFilesystemMountsRead(e, MTAB_PATH)) {
                    fail("filesystem_mounts sidecar must not fire when linux.files wins");
                }
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                    assertEquals("json-config", e.source);
                }
            }
            assertTrue(sawLinuxFile);
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    @Test
    public void testWriteDirectoryAndNearPathsNotTakenOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_MOUNTS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            assertWriteOpenNotTakenOver(emulator, blocks, sink, MTAB_PATH, IOConstants.O_WRONLY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, MTAB_PATH, IOConstants.O_RDWR);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, MTAB_PATH, IOConstants.O_DIRECTORY);
            assertWriteOpenNotTakenOver(emulator, blocks, sink, MTAB_PATH,
                    IOConstants.O_RDONLY | IOConstants.O_DIRECTORY);

            assertNotTakenOverAsConfiguredMtab(emulator, blocks, sink, "/etc");
            assertNotTakenOverAsConfiguredMtab(emulator, blocks, sink, "/etc/mtab/");
            assertNotTakenOverAsConfiguredMtab(emulator, blocks, sink, "/etc/mtab.bak");
            assertNotTakenOverAsConfiguredMtab(emulator, blocks, sink, "/system/etc/mtab");
            assertNotTakenOverAsConfiguredMtab(emulator, blocks, sink, "/etc/fstab");
            assertNotTakenOverAsConfiguredMtab(emulator, blocks, sink, "/etc/Mtab");
            assertEquals(0, countMtabReads(sink));
        } finally {
            closeEmulator(emulator, sink, blocks);
        }
    }

    private static void assertMtabSidecar(CapturedEvent e, String path, int count, int bytes) {
        assertNotNull(e);
        assertEquals("filesystem_mounts", e.kind);
        assertEquals("read(\"" + path + "\")", e.api);
        assertEquals("json-config", e.source);
        assertEquals("path=" + path + ",format=mtab,count=" + count + ",bytes=" + bytes,
                String.valueOf(e.value));
        assertNotNull(e.note);
        assertTrue(e.note.contains("读取配置的挂载表"));
        assertTrue(e.note.contains(path));
    }

    private static void assertSidecarRedacted(CapturedEvent e) {
        String value = String.valueOf(e.value);
        String note = String.valueOf(e.note);
        String[] leaks = {
                "/dev/block/dm-0", "/dev/block/dm-1", "fileSystemType", "seclabel", "relatime",
                "nosuid", "f2fs", "ext4", "source=", "target=", "options=", "mountId",
                "superOptions", "mountOptions"
        };
        for (int i = 0; i < leaks.length; i++) {
            assertFalse("sidecar value leaked " + leaks[i], value.contains(leaks[i]));
            assertFalse("sidecar note leaked " + leaks[i], note.contains(leaks[i]));
        }
        assertFalse(value.contains("entries="));
    }

    /**
     * Fallback success is allowed. Takeover is identified by the mtab sidecar and/or
     * configured fstab bytes — not by a successful open alone.
     */
    private static void assertNotTakenOverAsConfiguredMtab(AndroidEmulator emulator,
                                                           List<MemoryBlock> blocks,
                                                           CapturingSink sink,
                                                           String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 8192);
            assertFalse(path + " must not serve configured mounts table",
                    EXPECTED_MOUNTS.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected filesystem_mounts sidecar for " + path,
                    isFilesystemMountsRead(e, path));
        }
    }

    private static void assertWriteOpenNotTakenOver(AndroidEmulator emulator,
                                                    List<MemoryBlock> blocks,
                                                    CapturingSink sink,
                                                    String path, int oflags) throws Exception {
        FileResult<AndroidFileIO> result;
        try {
            result = emulator.getFileSystem().open(path, oflags);
        } catch (RuntimeException ignored) {
            return;
        }
        if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
            String text = readIoText(emulator, blocks, result.io, 8192);
            assertFalse("write/directory open must not serve configured mtab",
                    EXPECTED_MOUNTS.equals(text));
        }
        for (CapturedEvent e : sink.events) {
            assertFalse(isFilesystemMountsRead(e, path));
        }
    }

    private static void assertArrayEqualsBytes(byte[] expected, byte[] actual) {
        assertEquals(new String(expected, StandardCharsets.UTF_8),
                new String(actual, StandardCharsets.UTF_8));
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("byte[" + i + "]", expected[i], actual[i]);
        }
    }

    private static void closeEmulator(AndroidEmulator emulator, CapturingSink sink,
                                      List<MemoryBlock> blocks) throws Exception {
        try {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
            }
            freeAll(blocks);
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static String readOpenText(AndroidEmulator emulator, List<MemoryBlock> blocks, String path)
            throws Exception {
        return new String(readOpenBytes(emulator, blocks, path), StandardCharsets.UTF_8);
    }

    private static byte[] readOpenBytes(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                        String path) throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        assertNotNull(result);
        assertTrue("open failed for " + path, result.isSuccess());
        assertNotNull(result.io);
        assertTrue(result.io instanceof ByteArrayFileIO);
        return readIoBytes(emulator, blocks, result.io, 8192);
    }

    private static String readIoText(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                     AndroidFileIO io, int max) throws Exception {
        return new String(readIoBytes(emulator, blocks, io, max), StandardCharsets.UTF_8);
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

    private static void freeAll(List<MemoryBlock> blocks) {
        for (MemoryBlock block : blocks) {
            try {
                block.free();
            } catch (Exception ignored) {
                // teardown
            }
        }
        blocks.clear();
    }

    private static int countMtabReads(CapturingSink sink) {
        int n = 0;
        for (CapturedEvent e : sink.events) {
            if (isFilesystemMountsRead(e, MTAB_PATH)
                    || isFilesystemMountsRead(e, "/etc/mtab/")
                    || isFilesystemMountsRead(e, "/etc/mtab.bak")
                    || isFilesystemMountsRead(e, "/system/etc/mtab")
                    || isFilesystemMountsRead(e, "/etc")
                    || isFilesystemMountsRead(e, "/etc/fstab")
                    || isFilesystemMountsRead(e, "/etc/Mtab")) {
                n++;
            }
        }
        return n;
    }

    private static CapturedEvent findMtabRead(CapturingSink sink, String path) {
        CapturedEvent last = null;
        for (CapturedEvent e : sink.events) {
            if (isMtabRead(e, path)) {
                last = e;
            }
        }
        return last;
    }

    private static boolean isMtabRead(CapturedEvent e, String path) {
        return isFilesystemMountsRead(e, path)
                && String.valueOf(e.value).contains("format=mtab");
    }

    private static boolean isFilesystemMountsRead(CapturedEvent e, String path) {
        return "filesystem_mounts".equals(e.kind)
                && ("read(\"" + path + "\")").equals(e.api);
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

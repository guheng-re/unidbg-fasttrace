package com.github.unidbg.linux.file;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
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

/**
 * LinuxFileSystem wiring for {@code filesystem.mounts} → /proc mounts tables.
 * freeAll before emulator.close in every finally.
 */
public class LinuxFileSystemMountsTest {

    private static final int CONFIG_PID = 4242;

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

    private static final String EXPECTED_MOUNTINFO =
            "21 1 253:0 / / ro,seclabel,relatime - ext4 /dev/block/dm-0 rw,seclabel\n"
                    + "45 21 253:1 / /data rw,nosuid,nodev,noatime shared:2 - f2fs /dev/block/dm-1 rw\n";

    @Test
    public void testSelfAndPidAliasesContent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_MOUNTS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            assertEquals(CONFIG_PID, emulator.getPid());

            assertEquals(EXPECTED_MOUNTS, readOpenText(emulator, blocks, "/proc/mounts"));
            assertEquals(EXPECTED_MOUNTS, readOpenText(emulator, blocks, "/proc/self/mounts"));
            assertEquals(EXPECTED_MOUNTS, readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/mounts"));

            assertEquals(EXPECTED_MOUNTINFO, readOpenText(emulator, blocks, "/proc/self/mountinfo"));
            assertEquals(EXPECTED_MOUNTINFO, readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/mountinfo"));
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testExplicitEmptyArraySucceedsWithEmptyContent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"process\":{\"pid\":" + CONFIG_PID + "},\"filesystem\":{\"mounts\":[]}}");
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            assertEquals("", readOpenText(emulator, blocks, "/proc/self/mounts"));
            assertEquals("", readOpenText(emulator, blocks, "/proc/self/mountinfo"));
            assertEquals("", readOpenText(emulator, blocks, "/proc/mounts"));
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testNotConfiguredFallsThrough() throws Exception {
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().build();
            FileResult<AndroidFileIO> result = emulator.getFileSystem()
                    .open("/proc/self/mounts", IOConstants.O_RDONLY);
            // no filesystem.mounts and typically no host file → not our ByteArray content success
            // may fail or hit rootfs; must not be the FULL_MOUNTS content
            if (result != null && result.isSuccess() && result.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, result.io, 4096);
                assertFalse(text.contains("/dev/block/dm-0"));
            }
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testIncompleteMountInfoFallsBackButMountsStillWork() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"filesystem\":{\"mounts\":["
                + "{\"source\":\"tmpfs\",\"target\":\"/dev\",\"fileSystemType\":\"tmpfs\","
                + "\"options\":\"rw\"}"
                + "]}}"
        );
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();

            assertEquals("tmpfs /dev tmpfs rw 0 0\n",
                    readOpenText(emulator, blocks, "/proc/self/mounts"));

            FileResult<AndroidFileIO> info = emulator.getFileSystem()
                    .open("/proc/self/mountinfo", IOConstants.O_RDONLY);
            if (info != null && info.isSuccess() && info.io instanceof ByteArrayFileIO) {
                String text = readIoText(emulator, blocks, info.io, 4096);
                // must not be generated from incomplete mounts config
                assertFalse(text.startsWith("1 "));
                assertFalse(text.contains("tmpfs /dev"));
            }
            // preferred: open does not succeed as config mountinfo (null render → fallback)
            // when rootfs has no mountinfo, result is failure/null-ish
        } finally {
            freeAll(blocks);
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    @Test
    public void testLinuxFilesTakesPriorityWithoutMountsEvent() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"process\":{\"pid\":" + CONFIG_PID + "},"
                + "\"linux\":{\"files\":{\"/proc/self/mounts\":\"CUSTOM_MOUNTS\\n\"}},"
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

            assertEquals("CUSTOM_MOUNTS\n", readOpenText(emulator, blocks, "/proc/self/mounts"));
            // pid alias still hits linux.files via self rewrite
            assertEquals("CUSTOM_MOUNTS\n",
                    readOpenText(emulator, blocks, "/proc/" + CONFIG_PID + "/mounts"));

            boolean sawLinuxFile = false;
            for (CapturedEvent e : sink.events) {
                assertFalse("filesystem_mounts".equals(e.kind));
                if ("linux_file".equals(e.kind)) {
                    sawLinuxFile = true;
                }
            }
            assertTrue(sawLinuxFile);
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
    public void testSingleSidecarEventPerSuccessfulOpen() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(FULL_MOUNTS_JSON);
        AndroidEmulator emulator = null;
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        CapturingSink sink = new CapturingSink();
        try {
            emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
            TraceEnvironmentEventSink.register(emulator, sink);

            readOpenText(emulator, blocks, "/proc/self/mounts");
            assertEquals(1, sink.events.size());
            CapturedEvent e0 = sink.events.get(0);
            assertEquals("filesystem_mounts", e0.kind);
            assertEquals("read(\"/proc/self/mounts\")", e0.api);
            assertEquals("json-config", e0.source);
            assertEquals("path=/proc/self/mounts,format=mounts,entries=2", String.valueOf(e0.value));
            assertTrue(e0.note.contains("读取配置的挂载表"));
            assertTrue(e0.note.contains("/proc/self/mounts"));

            readOpenText(emulator, blocks, "/proc/self/mountinfo");
            assertEquals(2, sink.events.size());
            CapturedEvent e1 = sink.events.get(1);
            assertEquals("filesystem_mounts", e1.kind);
            assertEquals("read(\"/proc/self/mountinfo\")", e1.api);
            assertEquals("path=/proc/self/mountinfo,format=mountinfo,entries=2",
                    String.valueOf(e1.value));
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

    private static String readOpenText(AndroidEmulator emulator, List<MemoryBlock> blocks, String path)
            throws Exception {
        FileResult<AndroidFileIO> result = emulator.getFileSystem()
                .open(path, IOConstants.O_RDONLY);
        assertNotNull(result);
        assertTrue("open failed for " + path, result.isSuccess());
        assertNotNull(result.io);
        assertTrue(result.io instanceof ByteArrayFileIO);
        return readIoText(emulator, blocks, result.io, 8192);
    }

    private static String readIoText(AndroidEmulator emulator, List<MemoryBlock> blocks,
                                     AndroidFileIO io, int max) throws Exception {
        MemoryBlock block = emulator.getMemory().malloc(max, true);
        blocks.add(block);
        Pointer ptr = block.getPointer();
        int n = io.read(emulator.getBackend(), ptr, max);
        assertTrue(n >= 0);
        if (n == 0) {
            return "";
        }
        return new String(ptr.getByteArray(0, n), StandardCharsets.UTF_8);
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

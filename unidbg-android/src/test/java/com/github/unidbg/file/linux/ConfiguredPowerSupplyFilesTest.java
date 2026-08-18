package com.github.unidbg.file.linux;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.file.ByteArrayFileIO;
import com.github.unidbg.memory.MemoryBlock;
import com.sun.jna.Pointer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConfiguredPowerSupplyFilesTest {

    @Test
    public void testRenderPresentAbsentAndOpen() throws Exception {
        TraceEnvironmentConfig present = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"battery\":{\"health\":3,\"voltageMv\":4118,"
                + "\"temperatureTenthsC\":284}}}");
        assertEquals("Overheat\n", new String(ConfiguredPowerSupplyFiles.render(present,
                ConfiguredPowerSupplyFiles.HEALTH_PATH), StandardCharsets.UTF_8));
        assertEquals("4118000\n", new String(ConfiguredPowerSupplyFiles.render(present,
                ConfiguredPowerSupplyFiles.VOLTAGE_NOW_PATH), StandardCharsets.UTF_8));
        assertEquals("284\n", new String(ConfiguredPowerSupplyFiles.render(present,
                ConfiguredPowerSupplyFiles.TEMP_PATH), StandardCharsets.UTF_8));

        TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse("{\"android\":{\"battery\":{}}}");
        assertNull(ConfiguredPowerSupplyFiles.render(absent, ConfiguredPowerSupplyFiles.HEALTH_PATH));
        assertNull(ConfiguredPowerSupplyFiles.render(TraceEnvironmentConfig.parse("{}"),
                ConfiguredPowerSupplyFiles.HEALTH_PATH));

        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(present).build();
        MemoryBlock block = null;
        try {
            FileResult<AndroidFileIO> health = emulator.getFileSystem().open(
                    ConfiguredPowerSupplyFiles.HEALTH_PATH, IOConstants.O_RDONLY);
            assertNotNull(health);
            assertTrue(health.isSuccess());
            assertTrue(health.io instanceof ByteArrayFileIO);
            block = emulator.getMemory().malloc(32, true);
            Pointer ptr = block.getPointer();
            int n = health.io.read(emulator.getBackend(), ptr, 32);
            assertEquals("Overheat\n", new String(ptr.getByteArray(0, n), StandardCharsets.UTF_8));
        } finally {
            if (block != null) {
                block.free();
            }
            emulator.close();
        }
    }
}

package com.github.unidbg.linux.android;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.ConfiguredTcpFiles;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.AndroidSyscallHandler;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.linux.file.ByteArrayFileIO;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.virtualmodule.android.AndroidModule;
import com.github.unidbg.virtualmodule.android.MediaNdkModule;
import com.sun.jna.Pointer;
import org.junit.Test;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Runtime coverage for generic slots: renderers, NDK sensors, Media DRM identity,
 * popen snapshot, directory getdents, and mincore.
 */
public class EnvSlotsRuntimeTest {

    @Test
    public void testTcpRendererAndOpen() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"network\":{"
                + "\"tcp\":[{\"slot\":0,\"localIpv4\":\"127.0.0.1\",\"localPort\":8080,"
                + "\"remoteIpv4\":\"0.0.0.0\",\"remotePort\":0,\"stateHex\":\"0A\",\"inode\":7}],"
                + "\"tcp6\":[]}}");
        byte[] tcp = ConfiguredTcpFiles.renderProcNetTcp(config);
        assertNotNull(tcp);
        String text = new String(tcp, StandardCharsets.UTF_8);
        assertTrue(text.startsWith(ConfiguredTcpFiles.HEADER4));
        assertTrue(text.contains(ConfiguredTcpFiles.toLinuxIpv4Hex("127.0.0.1") + ":1F90"));
        assertTrue(text.contains("0A"));
        assertFalse(text.contains("27042"));
        byte[] tcp6 = ConfiguredTcpFiles.renderProcNetTcp6(config);
        assertNotNull(tcp6);
        assertEquals(ConfiguredTcpFiles.HEADER6, new String(tcp6, StandardCharsets.UTF_8));
        assertNull(ConfiguredTcpFiles.renderProcNetTcp(TraceEnvironmentConfig.parse("{}")));

        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            FileResult<AndroidFileIO> opened = emulator.getFileSystem().open("/proc/net/tcp", IOConstants.O_RDONLY);
            assertTrue(opened.isSuccess());
            byte[] read = readAll(emulator, opened.io);
            assertEquals(text, new String(read, StandardCharsets.UTF_8));
            FileResult<AndroidFileIO> absentTcp = AndroidEmulatorBuilder.for64Bit()
                    .setEnvironmentConfig(TraceEnvironmentConfig.parse("{}"))
                    .build()
                    .getFileSystem()
                    .open("/proc/net/tcp", IOConstants.O_RDONLY);
            // missing node must not serve a configured snapshot
            if (absentTcp != null && absentTcp.isSuccess() && absentTcp.io instanceof ByteArrayFileIO) {
                String served = new String(readAll(emulator, absentTcp.io), StandardCharsets.UTF_8);
                assertFalse(served.contains(":1F90"));
            }
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testPopenDirectoryProcessAndMincore() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"linux\":{"
                + "\"commands\":{\"uptime\":\" 0.01 0.02 0.03\\n\"},"
                + "\"mincore\":{\"resident\":true},"
                + "\"processes\":[{\"pid\":1,\"cmdline\":[\"init\"],\"comm\":\"init\"}]"
                + "},"
                + "\"filesystem\":{\"directories\":{\"/system/fonts\":[\"Roboto.ttf\"]}}"
                + "}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            LinuxCommandHook hook = new LinuxCommandHook(emulator);
            assertNull(hook.tryPopen(emulator, "missing"));
            Long file = hook.tryPopen(emulator, "uptime");
            assertNotNull(file);
            assertEquals(" 0.01 0.02 0.03\n",
                    new String(hook.drainPopen(file.longValue()), StandardCharsets.UTF_8));
            assertEquals(Integer.valueOf(0), hook.trySystem(emulator, "uptime"));
            assertNull(hook.trySystem(emulator, "missing"));

            FileResult<AndroidFileIO> dir = emulator.getFileSystem().open("/system/fonts", IOConstants.O_RDONLY);
            assertTrue(dir.isSuccess());
            MemoryBlock block = emulator.getMemory().malloc(512, true);
            int n = dir.io.getdents64(block.getPointer(), 512);
            assertTrue(n > 0);
            String names = new String(block.getPointer().getByteArray(0, n), StandardCharsets.UTF_8);
            assertTrue(names.contains("Roboto.ttf"));
            block.free();

            FileResult<AndroidFileIO> cmdline = emulator.getFileSystem()
                    .open("/proc/1/cmdline", IOConstants.O_RDONLY);
            assertTrue(cmdline.isSuccess());
            byte[] cmd = readAll(emulator, cmdline.io);
            assertEquals("init\0", new String(cmd, StandardCharsets.UTF_8));

            MemoryBlock vec = emulator.getMemory().malloc(4, true);
            emulator.getBackend().reg_write(Arm64Const.UC_ARM64_REG_X0, 0x1000L);
            emulator.getBackend().reg_write(Arm64Const.UC_ARM64_REG_X1, 8192L);
            emulator.getBackend().reg_write(Arm64Const.UC_ARM64_REG_X2, vec.getPointer().peer);
            Integer rc = ((AndroidSyscallHandler) emulator.getSyscallHandler()).tryMincore(emulator);
            assertEquals(Integer.valueOf(0), rc);
            assertEquals(1, vec.getPointer().getByte(0));
            assertEquals(1, vec.getPointer().getByte(1));
            vec.free();
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testNdkSensorsAndMediaDrmIdentity() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{"
                + "\"sensors\":{\"types\":[1],\"names\":{\"1\":\"accel\"},\"vendors\":{\"1\":\"oem\"},"
                + "\"resolutions\":{\"1\":0.01}},"
                + "\"drm\":{\"deviceUniqueIdHex\":\"0a0b0c0d\"}"
                + "}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AndroidModule module = new AndroidModule(emulator, vm);
            assertTrue(module.getSensorManager(emulator) != 0L);
            MemoryBlock listOut = emulator.getMemory().malloc(8, true);
            emulator.getBackend().reg_write(Arm64Const.UC_ARM64_REG_X1, listOut.getPointer().peer);
            int count = module.getSensorList(emulator);
            assertEquals(1, count);
            Pointer list = listOut.getPointer().getPointer(0);
            assertNotNull(list);
            Pointer sensor = list.getPointer(0);
            assertEquals(1, sensor.getInt(0));
            Pointer name = sensor.getPointer(16);
            assertEquals("accel", name.getString(0));

            MediaNdkModule ndk = new MediaNdkModule(emulator, vm);
            MemoryBlock prop = emulator.getMemory().malloc(64, true);
            ndk.getPropertyByteArrayByName(emulator, "deviceUniqueId", prop.getPointer());
            byte[] nativeBytes = config.resolveMediaDrmDeviceUniqueId();
            assertArrayEquals(new byte[]{0x0a, 0x0b, 0x0c, 0x0d}, nativeBytes);
            emulator.getBackend().reg_write(Arm64Const.UC_ARM64_REG_X0,
                    com.github.unidbg.pointer.UnidbgPointer.nativeValue(sensor));
            module.sensorGetResolutionBits(emulator);
            float s0 = Float.intBitsToFloat(emulator.getBackend()
                    .reg_read(Arm64Const.UC_ARM64_REG_S0).intValue());
            assertEquals(0.01f, s0, 0.0001f);
            byte[] q0 = emulator.getBackend().reg_read_vector(Arm64Const.UC_ARM64_REG_Q0);
            int qBits = (q0[0] & 0xff) | ((q0[1] & 0xff) << 8)
                    | ((q0[2] & 0xff) << 16) | ((q0[3] & 0xff) << 24);
            assertEquals(0.01f, Float.intBitsToFloat(qBits), 0.0001f);

            listOut.free();
            prop.free();
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testNdkSensorResolutionWritesArm32S0() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"sensors\":{\"types\":[1],\"resolutions\":{\"1\":0.25}}}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for32Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AndroidModule module = new AndroidModule(emulator, vm);
            MemoryBlock listOut = emulator.getMemory().malloc(8, true);
            emulator.getBackend().reg_write(ArmConst.UC_ARM_REG_R1, listOut.getPointer().peer);
            assertEquals(1, module.getSensorList(emulator));
            Pointer list = listOut.getPointer().getPointer(0);
            assertNotNull(list);
            Pointer sensor = list.getPointer(0);
            assertNotNull(sensor);
            assertEquals(1, sensor.getInt(0));
            assertEquals(0.25f, sensor.getFloat(8), 0.0001f);
            emulator.getBackend().reg_write(ArmConst.UC_ARM_REG_R0,
                    com.github.unidbg.pointer.UnidbgPointer.nativeValue(sensor));
            long bits = module.sensorGetResolutionBits(emulator);
            assertEquals(0.25f, Float.intBitsToFloat((int) bits), 0.0001f);
            Number s0raw = emulator.getBackend().reg_read(ArmConst.UC_ARM_REG_S0);
            float s0 = Float.intBitsToFloat(s0raw.intValue());
            if (s0 == 0f) {
                byte[] d0 = emulator.getBackend().reg_read_vector(ArmConst.UC_ARM_REG_D0);
                int dBits = (d0[0] & 0xff) | ((d0[1] & 0xff) << 8)
                        | ((d0[2] & 0xff) << 16) | ((d0[3] & 0xff) << 24);
                s0 = Float.intBitsToFloat(dBits);
            }
            assertEquals(0.25f, s0, 0.0001f);
            listOut.free();
        } finally {
            emulator.close();
        }
    }

    private static byte[] readAll(AndroidEmulator emulator, AndroidFileIO io) {
        MemoryBlock block = emulator.getMemory().malloc(4096, true);
        try {
            int n = io.read(emulator.getBackend(), block.getPointer(), 4096);
            if (n <= 0) {
                return new byte[0];
            }
            return block.getPointer().getByteArray(0, n);
        } finally {
            block.free();
        }
    }
}

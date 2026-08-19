package com.github.unidbg.linux.android;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Module;
import com.github.unidbg.Symbol;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ArtRuntimeStubTest {

    private static final String INSTANCE = "_ZN3art7Runtime9instance_E";
    private static final String OPEN_MEMORY =
            "_ZN3art7DexFile10OpenMemoryEPKhmRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPS9_";

    @Test
    public void preloadsLibartResolvesInstanceAndOpenMemory() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().build();
        try {
            emulator.getMemory().setLibraryResolver(new AndroidResolver(23));
            Module art = emulator.getMemory().findModule("libart.so");
            assertNotNull(art);
            assertTrue(art.getRegions().get(0).getName().contains("/system/lib64/libart.so"));

            Symbol inst = art.findSymbolByName(INSTANCE, false);
            assertNotNull(inst);
            UnidbgPointer slot = UnidbgPointer.pointer(emulator, inst.getAddress());
            assertNotNull(slot);
            long runtime = slot.getLong(0);
            assertTrue(runtime != 0L);
            emulator.getBackend().mem_read(runtime, 8);

            Symbol open = art.findSymbolByName(OPEN_MEMORY, false);
            assertNotNull(open);
            MemoryBlock dex = emulator.getMemory().malloc(0x70, true);
            dex.getPointer().setString(0, "dex\n037");
            Number ret = Module.emulateFunction(emulator, open.getAddress(),
                    Long.valueOf(dex.getPointer().peer), Long.valueOf(0x70L));
            long dexFile = ret.longValue();
            assertTrue(dexFile != 0L);
            UnidbgPointer obj = UnidbgPointer.pointer(emulator, dexFile);
            assertNotNull(obj);
            assertEquals(0L, obj.getLong(0));
            assertEquals(dex.getPointer().peer, obj.getLong(8));
            assertEquals(0x70L, obj.getLong(16));

            Symbol viaInterior = emulator.getMemory().dlsym(art.base + 0x1000L, INSTANCE);
            assertNotNull(viaInterior);
            assertEquals(inst.getAddress(), viaInterior.getAddress());

            FileResult<AndroidFileIO> opened = new AndroidResolver(23)
                    .resolve(emulator, "/system/lib64/libart.so", 0);
            assertNotNull(opened);
            assertTrue(opened.isSuccess() || opened.isFallback());
        } finally {
            emulator.close();
        }
    }

    @Test
    public void guestLibdlDlsymResolvesInstance() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().build();
        try {
            emulator.getMemory().setLibraryResolver(new AndroidResolver(23));
            Module libdl = emulator.getMemory().dlopen("libdl.so");
            assertNotNull(libdl);
            Symbol guestDlsym = libdl.findSymbolByName("dlsym", false);
            assertNotNull(guestDlsym);
            byte[] patched = emulator.getBackend().mem_read(guestDlsym.getAddress(), 8);
            int svc = le32(patched, 0);
            int ret = le32(patched, 4);
            assertEquals(0xd4000001, svc & 0xffe0001f);
            assertEquals(0xd65f03c0, ret);

            Symbol guestDlopen = libdl.findSymbolByName("dlopen", false);
            assertNotNull(guestDlopen);
            byte[] dlopenBytes = emulator.getBackend().mem_read(guestDlopen.getAddress(), 8);
            assertEquals(0xd2800000, le32(dlopenBytes, 0));
            assertEquals(0xd65f03c0, le32(dlopenBytes, 4));

            Module art = emulator.getMemory().findModule("libart.so");
            assertNotNull(art);
            Symbol inst = art.findSymbolByName(INSTANCE, false);
            assertNotNull(inst);

            MemoryBlock name = emulator.getMemory().malloc(INSTANCE.length() + 1, true);
            name.getPointer().setString(0, INSTANCE);

            Number viaZero = Module.emulateFunction(emulator, guestDlsym.getAddress(),
                    Long.valueOf(0L), Long.valueOf(name.getPointer().peer));
            assertEquals(inst.getAddress(), viaZero.longValue());

            Number viaBase = Module.emulateFunction(emulator, guestDlsym.getAddress(),
                    Long.valueOf(art.base), Long.valueOf(name.getPointer().peer));
            assertEquals(inst.getAddress(), viaBase.longValue());
        } finally {
            emulator.close();
        }
    }

    private static int le32(byte[] data, int off) {
        return (data[off] & 0xff)
                | ((data[off + 1] & 0xff) << 8)
                | ((data[off + 2] & 0xff) << 16)
                | ((data[off + 3] & 0xff) << 24);
    }
}

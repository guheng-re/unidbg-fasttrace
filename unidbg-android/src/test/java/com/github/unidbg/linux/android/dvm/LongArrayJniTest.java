package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.array.LongArray;
import com.github.unidbg.pointer.UnidbgPointer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LongArrayJniTest {

    @Test
    public void getArrayCriticalWritesLongs() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().build();
        try {
            VM vm = emulator.createDalvikVM();
            LongArray array = new LongArray(vm, new long[]{0L, 0x7100680000L});
            UnidbgPointer elems = array._GetArrayCritical(emulator, null);
            assertNotNull(elems);
            assertEquals(0L, elems.getLong(0));
            assertEquals(0x7100680000L, elems.getLong(8));
            array._ReleaseArrayCritical(elems, VM.JNI_ABORT);
        } finally {
            emulator.close();
        }
    }

    @Test
    public void dexFileCookieIsLongArray() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().build();
        try {
            BaseVM vm = (BaseVM) emulator.createDalvikVM();
            DvmObject<?> cookie = vm.getOrCreateDexFileCookie();
            assertTrue(cookie instanceof LongArray);
            long[] values = ((LongArray) cookie).getValue();
            assertEquals(2, values.length);
            // Both slots are the same DexFile* so [0]-as-DexFile* and ART O+ [1] agree.
            // No APK in this VM → both 0.
            assertEquals(values[0], values[1]);
            assertEquals(0L, vm.getArtDexFilePointer());
        } finally {
            emulator.close();
        }
    }
}

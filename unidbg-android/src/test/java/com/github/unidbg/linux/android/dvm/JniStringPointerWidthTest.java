package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.arm.AndroidArm64Addresses;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.pointer.UnidbgPointer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * GetStringUTFChars / GetStringChars return a guest C pointer.
 * ARM64 must keep the full 64-bit peer after the 39-bit VAS lift;
 * ARM32 still returns a 32-bit address.
 */
public class JniStringPointerWidthTest {

    private static final String SAMPLE = "native-pointer-width";
    private static final int JNI_GET_STRING_UTF_CHARS_32 = 0x2a4;
    private static final int JNI_GET_STRING_UTF_CHARS_64 = 0x548;
    private static final int JNI_GET_STRING_CHARS_32 = 0x294;
    private static final int JNI_GET_STRING_CHARS_64 = 0x528;

    @Test
    public void utfChars64ReturnsFullPeer() throws Exception {
        assertGuestCString(true, JNI_GET_STRING_UTF_CHARS_64, true);
    }

    @Test
    public void utfChars32StaysIn32BitWindow() throws Exception {
        assertGuestCString(false, JNI_GET_STRING_UTF_CHARS_32, true);
    }

    @Test
    public void stringChars64ReturnsFullPeer() throws Exception {
        assertGuestCString(true, JNI_GET_STRING_CHARS_64, false);
    }

    @Test
    public void stringChars32StaysIn32BitWindow() throws Exception {
        assertGuestCString(false, JNI_GET_STRING_CHARS_32, false);
    }

    private static void assertGuestCString(boolean is64Bit, int tableOffset, boolean utf8) throws Exception {
        AndroidEmulator emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                .build();
        try {
            VM vm = emulator.createDalvikVM();
            vm.setJni(new AbstractJni() { });
            BaseVM baseVM = (BaseVM) vm;
            StringObject string = new StringObject(vm, SAMPLE);
            int stringHash = baseVM.addLocalObject(string);

            UnidbgPointer env = (UnidbgPointer) vm.getJNIEnv();
            UnidbgPointer slot = (UnidbgPointer) env.getPointer(0).getPointer(tableOffset);
            UnidbgPointer isCopy = emulator.getSvcMemory().allocate(4, "isCopy");
            isCopy.setInt(0, 0);

            final Number envArg;
            final Number isCopyArg;
            final long begin;
            if (is64Bit) {
                envArg = Long.valueOf(env.peer);
                isCopyArg = Long.valueOf(isCopy.peer);
                begin = slot.peer;
            } else {
                envArg = Integer.valueOf((int) env.toUIntPeer());
                isCopyArg = Integer.valueOf((int) isCopy.toUIntPeer());
                begin = slot.toUIntPeer();
            }
            Number ret = emulator.eFunc(begin, envArg, Integer.valueOf(stringHash), isCopyArg);

            long guest = is64Bit ? ret.longValue() : (ret.intValue() & 0xffffffffL);
            assertTrue("guest=0x" + Long.toHexString(guest), guest != 0);
            if (is64Bit) {
                assertTrue("truncated 32-bit pointer 0x" + Long.toHexString(guest),
                        guest > 0xffffffffL);
                assertTrue("not in 39-bit VAS 0x" + Long.toHexString(guest),
                        guest >= AndroidArm64Addresses.HEAP_BASE);
            } else {
                assertTrue("32-bit pointer escaped 4G 0x" + Long.toHexString(guest),
                        guest <= 0xffffffffL);
                assertTrue("32-bit pointer in 64-bit VAS 0x" + Long.toHexString(guest),
                        guest < 0x7000000000L);
            }

            UnidbgPointer pointer = UnidbgPointer.pointer(emulator, guest);
            assertTrue("unmapped 0x" + Long.toHexString(guest), pointer != null);
            if (utf8) {
                assertEquals(SAMPLE, pointer.getString(0));
            } else {
                char[] chars = new char[SAMPLE.length()];
                for (int i = 0; i < chars.length; i++) {
                    chars[i] = pointer.getChar(i * 2L);
                }
                assertEquals(SAMPLE, new String(chars));
            }
        } finally {
            emulator.close();
        }
    }
}

package com.github.unidbg.linux.android.dvm.api;

import com.github.unidbg.linux.android.dvm.DvmObject;
import com.github.unidbg.linux.android.dvm.VM;
import net.dongliu.apk.parser.bean.CertificateMeta;
import org.apache.commons.codec.binary.Hex;

import java.util.Arrays;

public class Signature extends DvmObject<CertificateMeta> {

    public Signature(VM vm, CertificateMeta meta) {
        super(vm.resolveClass("android/content/pm/Signature"), meta);
    }

    /**
     * Data-only Signature for configured {@code signaturesHex} (and similar) paths.
     * Builds a {@link CertificateMeta} that only carries a defensive copy of {@code data};
     * other certificate fields are null. Does not alter the {@link CertificateMeta}-based constructor.
     */
    public Signature(VM vm, byte[] data) {
        super(vm.resolveClass("android/content/pm/Signature"),
                new CertificateMeta(null, null, null, null,
                        data == null ? new byte[0] : Arrays.copyOf(data, data.length),
                        null, null));
    }

    public int getHashCode() {
        return Arrays.hashCode(value.getData());
    }

    public byte[] toByteArray() {
        return value.getData();
    }

    public String toCharsString() {
        return Hex.encodeHexString(value.getData());
    }

}

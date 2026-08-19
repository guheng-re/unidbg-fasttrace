package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.Emulator;
import com.github.unidbg.Module;
import com.github.unidbg.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DalvikModule {

    private static final Logger log = LoggerFactory.getLogger(DalvikModule.class);

    private final BaseVM vm;
    private final Module module;

    DalvikModule(BaseVM vm, Module module) {
        this.vm = vm;
        this.module = module;
    }

    public Module getModule() {
        return module;
    }

    public void callJNI_OnLoad(Emulator<?> emulator) {
        Symbol onLoad = module.findSymbolByName("JNI_OnLoad", false);
        if (onLoad != null) {
            invokeJNI_OnLoad(emulator, onLoad.getAddress());
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("[{}] JNI_OnLoad symbol not found; use callJNI_OnLoad(emulator, offset) for a hidden entry",
                    module.name);
        }
    }

    /**
     * Call {@code JNI_OnLoad(JavaVM*, void*)} at a module-relative offset.
     * Packers often strip the dynsym export; the real function may live at a
     * decrypted VA. Do not assume ELF {@code e_entry} is JNI_OnLoad — on many
     * images it is a PLT stub.
     */
    public void callJNI_OnLoad(Emulator<?> emulator, long offset) {
        if (offset <= 0) {
            throw new IllegalArgumentException("JNI_OnLoad offset must be positive: 0x" + Long.toHexString(offset));
        }
        invokeJNI_OnLoad(emulator, module.base + offset);
    }

    private void invokeJNI_OnLoad(Emulator<?> emulator, long address) {
        try {
            long start = System.currentTimeMillis();
            if (log.isDebugEnabled()) {
                log.debug("Call [{}]JNI_OnLoad: 0x{}", module.name, Long.toHexString(address));
            }
            Number ret = Module.emulateFunction(emulator, address, vm.getJavaVM(), null);
            int version = ret.intValue();
            if (log.isDebugEnabled()) {
                log.debug("Call [{}]JNI_OnLoad finished: version=0x{}, offset={}ms",
                        module.name, Integer.toHexString(version), System.currentTimeMillis() - start);
            }
            vm.checkVersion(version);
        } finally {
            vm.deleteLocalRefs();
        }
    }

}

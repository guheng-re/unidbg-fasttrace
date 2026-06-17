package com.sdu.didi.gsui;

import capstone.Capstone;
import capstone.api.Instruction;
import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.Module;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.CodeHook;
import com.github.unidbg.arm.backend.UnHook;
import com.github.unidbg.debugger.BreakPointCallback;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.IOResolver;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.linux.file.SimpleFileIO;
import com.github.unidbg.memory.Memory;
import unicorn.Arm64Const;

import java.io.File;
import java.io.IOException;

public class fuxian extends AbstractJni implements IOResolver<AndroidFileIO> {
    private static final boolean ENABLE_INSTRUMENTATION = false;

    private final AndroidEmulator emulator;
    private final VM vm;
    private final Module module;
    private final Memory memory;
    private final DalvikModule dm;

    public fuxian(String apkFilePath, String soFilePath, String apkProcessname) throws IOException {
        emulator = AndroidEmulatorBuilder.for64Bit().setProcessName(apkProcessname).build();
        memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));
        vm = emulator.createDalvikVM(new File(apkFilePath));
        vm.setVerbose(false);
        dm = vm.loadLibrary(new File(soFilePath), true);
        module = dm.getModule();
        vm.setJni(this);
        emulator.getSyscallHandler().addIOResolver(this);
        dm.callJNI_OnLoad(emulator);
    }

    public String func_sig() {
        DvmObject<?> context = vm.resolveClass("android.content.Context").newObject(null);
        long arg1 = 1744781882714L;
        String arg2 = "18912346543";
        byte[] arg3 = {118,49,46,48,46,48,105,112,118,54,49,54,56,54,102,55,51,55,52,55,51,51,100,54,56,54,52,50,101,55,56,54,57,54,49,54,102,54,97,55,53,54,98,54,53,54,97,54,57,50,101,54,51,54,102,54,100,50,53,51,50,51,48,54,100,55,51,54,55,54,55,54,49,55,52,54,53,50,101,55,56,54,57,54,49,54,102,54,97,55,53,54,98,54,53,54,97,54,57,50,101,54,51,54,102,54,100,50,53,51,50,51,48,54,102,54,54,54,54,54,99,54,57,54,101,54,53,50,100,55,48,54,98,54,55,50,101,54,52,54,57,54,52,54,57,50,101,54,51,54,101,50,53,51,50,51,48,54,53,55,48,54,49,55,51,55,51,55,48,54,102,55,50,55,52,50,101,54,52,54,57,54,52,54,57,55,52,54,49,55,56,54,57,50,101,54,51,54,102,54,100,50,101,54,51,54,101};
        StringObject nativeUpdate_str = new StringObject(vm, "0");
        StringObject nativeUpdate2_str = new StringObject(vm, "01q8gLV04Gl/etNrf+xjq1Uf+HQJ5/R+GodTyQzhxNw0eq5OQDEqgcw93k5GLcEwFJRpRijzUcqbefvO1RiVGFSaHKJGzg9C6tNOLEWvqqHJqnPudf/u7rMZgwFTW+QoxBT7bOkucwe1PPYZF0p908MsBdrOjFRyVUdWkQ5VUojbS&&sYEnzyF4+Zoq2t2fsqp0a3pv7kCDvheCCjivJrWpSNk");
        StringObject nativeCollect_str = new StringObject(vm, "hd.xiaojukeji.com/d");
        DvmObject<?> object = vm.resolveClass("com.didi.security.wireless.SecurityLib").newObject(null);
        object.callJniMethodObject(emulator, "nativeInit(Landroid/content/Context;)I", context);
        object.callJniMethodObject(emulator, "nativeUpdate(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", nativeUpdate_str, null, null, null);
        object.callJniMethodObject(emulator, "nativeUpdate2(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", null, null, null, nativeUpdate2_str);
        object.callJniMethodObject(emulator, "nativeCollect(Ljava/lang/String;)Ljava/lang/String;", nativeCollect_str);
        if (ENABLE_INSTRUMENTATION) {
            emulator.traceCode(module.base, module.base + module.size);
        }
        DvmObject<?> object1 = object.callJniMethodObject(emulator, "nativeSig(Landroid/content/Context;JLjava/lang/String;[B)Ljava/lang/String;", context, arg1, arg2, arg3);
        return object1.getValue().toString();
    }

    @Override
    public boolean callStaticBooleanMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        if (signature.equals("com/didi/security/wireless/SecurityLib->ApolloGetToggle(Landroid/content/Context;Ljava/lang/String;Z)Z")) {
            return false;
        }
        if (signature.equals("com/didi/security/wireless/StatUtils->isNetworkAvailable(Landroid/content/Context;)Z")) {
            return true;
        }
        return super.callBooleanMethod(vm, dvmClass, signature, varArg);
    }

    @Override
    public DvmObject<?> callObjectMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        if (signature.equals("android/content/Context->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;")) {
            return vm.resolveClass("android/content/SharedPreferences").newObject(varArg.getObjectArg(0));
        }
        if (signature.equals("android/content/SharedPreferences->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")) {
            return new StringObject(vm, "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");
        }
        if (signature.equals("android/content/Context->getPackageCodePath()Ljava/lang/String;")) {
            return new StringObject(vm, "/data/app/com.sdu.didi.gsui-mGlj-PzyfIpeLiDCnY-Qcw==/base.apk");
        }
        if (signature.equals("android/content/Context->getFilesDir()Ljava/io/File;")) {
            return vm.resolveClass("java/io/File").newObject("/data/data/com.sdu.didi.gsui/files/");
        }
        if (signature.equals("java/io/File->getCanonicalPath()Ljava/lang/String;")) {
            return new StringObject(vm, "/data/data/com.sdu.didi.gsui/files");
        }
        return super.callObjectMethod(vm, dvmObject, signature, varArg);
    }

    @Override
    public DvmObject<?> callStaticObjectMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        if (signature.equals("com/didi/security/wireless/SecurityLib->getFeature(I)Ljava/lang/Object;")) {
            if (varArg.getIntArg(0) == 8197) {
                return new StringObject(vm, "com.sdu.didi.gsui");
            }
            if (varArg.getIntArg(0) == 8198) {
                return new StringObject(vm, "9.0.14");
            }
            if (varArg.getIntArg(0) == 8195) {
                return new StringObject(vm, "Android");
            }
            if (varArg.getIntArg(0) == 8196) {
                return new StringObject(vm, "10");
            }
        }
        if (signature.equals("java/lang/System->getProperty(Ljava/lang/String;)Ljava/lang/String;")) {
            return null;
        }
        if (signature.equals("android/os/ServiceManager->getService(Ljava/lang/String;)Landroid/os/IBinder;")) {
            return vm.resolveClass("android/os/IBinder").newObject(null);
        }
        return super.callStaticObjectMethod(vm, dvmClass, signature, varArg);
    }

    @Override
    public int callStaticIntMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        if (signature.equals("com/didi/security/wireless/SecurityLib->getUserMode()I")) {
            return 0;
        }
        return super.callStaticIntMethod(vm, dvmClass, signature, varArg);
    }

    @Override
    public FileResult<AndroidFileIO> resolve(Emulator<AndroidFileIO> emulator, String pathname, int oflags) {
        if (pathname.equals("/data/app/com.sdu.didi.gsui-mGlj-PzyfIpeLiDCnY-Qcw==/base.apk")) {
            File apk = new File("D:\\project\\TraceAIagent_v3\\workspace\\manual-inputs\\fuxian\\滴滴车主.apk");
            return FileResult.<AndroidFileIO>success(new SimpleFileIO(oflags, apk, pathname));
        }
        return null;
    }

    private void debugger(long offset) {
        if (!ENABLE_INSTRUMENTATION) {
            return;
        }
        emulator.attach().addBreakPoint(module, offset, new BreakPointCallback() {
            @Override
            public boolean onHit(Emulator<?> emulator, long address) {
                return false;
            }
        });
    }

    private void debugger_with_reg(long offset, int reg, long reg_value) {
        if (!ENABLE_INSTRUMENTATION) {
            return;
        }
        emulator.attach().addBreakPoint(module, offset, new BreakPointCallback() {
            @Override
            public boolean onHit(Emulator<?> emulator, long address) {
                long x = emulator.getContext().getLongByReg(reg);
                return x != reg_value;
            }
        });
    }

    private void debugger_by_mem(long offset, int reg, long mem_value) {
        if (!ENABLE_INSTRUMENTATION) {
            return;
        }
        emulator.attach().addBreakPoint(module, offset, new BreakPointCallback() {
            @Override
            public boolean onHit(Emulator<?> emulator, long address) {
                long x = emulator.getContext().getLongByReg(reg);
                long mem = emulator.getMemory().pointer(x).getShort(0);
                return mem != mem_value;
            }
        });
    }

    private void debugger_x0(long offset, long x0_offset) {
        if (!ENABLE_INSTRUMENTATION) {
            return;
        }
        emulator.attach().addBreakPoint(module, offset, new BreakPointCallback() {
            @Override
            public boolean onHit(Emulator<?> emulator, long address) {
                long x0 = emulator.getContext().getLongByReg(Arm64Const.UC_ARM64_REG_X0);
                return x0 != x0_offset;
            }
        });
    }

    private void debugger3(long offset) {
        if (!ENABLE_INSTRUMENTATION) {
            return;
        }
        emulator.attach().addBreakPoint(module, offset, new BreakPointCallback() {
            @Override
            public boolean onHit(Emulator<?> emulator, long address) {
                long x1 = emulator.getContext().getLongByReg(Arm64Const.UC_ARM64_REG_X0);
                byte[] ret = emulator.getMemory().pointer(x1).getByteBuffer(0, 0x100).array();
                StringBuilder hexString = new StringBuilder();
                for (byte b : ret) {
                    hexString.append("0x");
                    hexString.append(Integer.toHexString(b & 0xFF).toUpperCase());
                    hexString.append(",");
                }
                System.out.println(hexString);
                return true;
            }
        });
    }

    private void trace(int start, int end, boolean showregs) {
        if (!ENABLE_INSTRUMENTATION) {
            return;
        }
        emulator.getBackend().hook_add_new(new CodeHook() {
            @Override
            public void hook(Backend backend, long address, int size, Object user) {
                Capstone capstone = new Capstone(Capstone.CS_ARCH_ARM64, Capstone.CS_MODE_ARM);
                byte[] bytes = emulator.getBackend().mem_read(address, 4);
                Instruction[] disasm = capstone.disasm(bytes, 0);
                long offset = address - module.base;
                int[] regs = {Arm64Const.UC_ARM64_REG_X0, Arm64Const.UC_ARM64_REG_X1, Arm64Const.UC_ARM64_REG_X2,
                        Arm64Const.UC_ARM64_REG_X3, Arm64Const.UC_ARM64_REG_X4, Arm64Const.UC_ARM64_REG_X5,
                        Arm64Const.UC_ARM64_REG_X6, Arm64Const.UC_ARM64_REG_X7, Arm64Const.UC_ARM64_REG_X8,
                        Arm64Const.UC_ARM64_REG_X9, Arm64Const.UC_ARM64_REG_X10, Arm64Const.UC_ARM64_REG_X11,
                        Arm64Const.UC_ARM64_REG_X12, Arm64Const.UC_ARM64_REG_X13, Arm64Const.UC_ARM64_REG_X14,
                        Arm64Const.UC_ARM64_REG_X15, Arm64Const.UC_ARM64_REG_X16, Arm64Const.UC_ARM64_REG_X17,
                        Arm64Const.UC_ARM64_REG_X18, Arm64Const.UC_ARM64_REG_X19, Arm64Const.UC_ARM64_REG_X20,
                        Arm64Const.UC_ARM64_REG_X21, Arm64Const.UC_ARM64_REG_X22, Arm64Const.UC_ARM64_REG_X23,
                        Arm64Const.UC_ARM64_REG_X24, Arm64Const.UC_ARM64_REG_X25, Arm64Const.UC_ARM64_REG_X26,
                        Arm64Const.UC_ARM64_REG_X27, Arm64Const.UC_ARM64_REG_X28};
                if (showregs) {
                    emulator.showRegs(regs);
                }
                System.out.printf("0x%x 0x%x %s %s\n", address, offset, disasm[0].getMnemonic(), disasm[0].getOpStr());
            }

            @Override
            public void onAttach(UnHook unHook) {
            }

            @Override
            public void detach() {
            }
        }, module.base + start, module.base + end, null);
    }

    public static void main(String[] args) throws IOException {
        String soFilePath = "D:\\project\\TraceAIagent_v3\\workspace\\manual-inputs\\fuxian\\libdidiwsg.so";
        String apkFilePath = "D:\\project\\TraceAIagent_v3\\workspace\\manual-inputs\\fuxian\\滴滴车主.apk";
        String apkProcessname = "com.sdu.didi.gsui";
        fuxian myapp = new fuxian(apkFilePath, soFilePath, apkProcessname);
        System.out.println("__BASELINE_RESULT__=" + myapp.func_sig());
    }
}

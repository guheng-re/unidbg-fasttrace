import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Module;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.DalvikModule;
import com.github.unidbg.linux.android.dvm.DvmClass;
import com.github.unidbg.linux.android.dvm.DvmObject;
import com.github.unidbg.linux.android.dvm.StringObject;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.memory.Memory;

import java.io.File;

public class TraceAiCaseUnidbgScript {
    private static final boolean ENABLE_INSTRUMENTATION = false;

    private final AndroidEmulator emulator;
    private final VM vm;
    private final Module module;
    private final Memory memory;
    private final DalvikModule dm;
    private static final String INPUT_TEXT = "TraceAI case input";

    public TraceAiCaseUnidbgScript(String apkFilePath, String soFilePath, String processName) {
        emulator = AndroidEmulatorBuilder.for64Bit().setProcessName(processName).build();
        memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));
        if (apkFilePath != null) {
            vm = emulator.createDalvikVM(new File(apkFilePath));
        } else {
            vm = emulator.createDalvikVM();
        }
        vm.setVerbose(false);
        dm = vm.loadLibrary(new File(soFilePath), true);
        module = dm.getModule();
    }

    public String func_run() {
        DvmClass entryClass = vm.resolveClass("com/traceai/case002/NativeEntry");
                if (ENABLE_INSTRUMENTATION) {
            emulator.traceCodeText(module.base, module.base + module.size, "D:\\project\\TraceAIagent_v3\\tests\\case_unidbg_001_CFF\\out\\case-trace.log");
        }
        DvmObject<?> result = entryClass.callStaticJniMethodObject(
                emulator,
                "process(Ljava/lang/String;)Ljava/lang/String;",
                new StringObject(vm, INPUT_TEXT));
        return result.getValue().toString();
    }

    public static void main(String[] args) {
        String soFilePath = "D:\\project\\TraceAIagent_v3\\tests\\case_unidbg_001_CFF\\libs\\arm64-v8a\\libtraceai_case_002.so";
        String processName = "com.traceai.case002";
        TraceAiCaseUnidbgScript myapp = new TraceAiCaseUnidbgScript(null, soFilePath, processName);
        String result = myapp.func_run();
        System.out.println("__BASELINE_RESULT__=" + String.valueOf(result));
    }
}

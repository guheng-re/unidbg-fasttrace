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



public class TraceAiBase64UnidbgScript {

    private static final boolean ENABLE_INSTRUMENTATION = false;

    private final AndroidEmulator emulator; //android模拟器

    private final VM vm;//vm虚拟机

    private final Module module;

    private  final Memory memory;

    private  final DalvikModule dm;

    private static final String PLAINTEXT = "TraceAI standard base64";



    public TraceAiBase64UnidbgScript(String apkFilePath,String soFilePath,String apkProcessname) {

        emulator = AndroidEmulatorBuilder.for64Bit().setProcessName(apkProcessname).build();

                memory = emulator.getMemory();

        memory.setLibraryResolver(new AndroidResolver(23));

        if (apkFilePath != null) {

            vm = emulator.createDalvikVM(new File(apkFilePath));

        } else {

            vm = emulator.createDalvikVM();

        }

        vm.setVerbose(false); // 打印日志，会在调用初始化JNI_onload打印一些信息，默认：false

        // 加载目标SO

        dm = vm.loadLibrary(new File(soFilePath), true); // 加载so到虚拟内存，第二个参数：是否需要初始化

        //获取本SO模块的句柄

        module = dm.getModule();

    }



    public String func_run(){

        DvmClass base64NativeClass = vm.resolveClass("com/traceai/base64/Base64Native");



        if (ENABLE_INSTRUMENTATION) {

            emulator.traceCodeText(module.base, module.base + module.size, "D:\\project\\TraceAIagent_v3\\tests\\base64_unidbg\\out\\base64-trace.log");

        }

        DvmObject<?> result = base64NativeClass.callStaticJniMethodObject(

                emulator,

                "encode(Ljava/lang/String;)Ljava/lang/String;",

                new StringObject(vm, PLAINTEXT));



        return result.getValue().toString();

    }



    public static void main(String[] args) {

        // 1、需要调用的so文件所在路径

        String soFilePath = "D:\\project\\TraceAIagent_v3\\tests\\base64_unidbg\\libs\\arm64-v8a\\libtraceai_base64_standard.so";

        // 2、APK的路径

        // String apkFilePath="D:\\unidbg\\unidbg-0.9.8\\unidbg-android\\src\\test\\java\\com\\sdu\\didi\\gsui\\滴滴车主.apk";

        // 3、apk进程名

        String apkProcessname="com.traceai.base64";

        TraceAiBase64UnidbgScript myapp = new TraceAiBase64UnidbgScript(null, soFilePath, apkProcessname);

        String result = myapp.func_run();

        System.out.println("__BASELINE_RESULT__=" + result);

    }

}


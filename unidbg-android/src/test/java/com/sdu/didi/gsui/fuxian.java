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
import com.github.unidbg.debugger.DebuggerType;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.IOResolver;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.linux.file.SimpleFileIO;
import com.github.unidbg.listener.TraceWriteListener;
import com.github.unidbg.memory.Memory;
import unicorn.Arm64Const;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class fuxian extends AbstractJni implements IOResolver<AndroidFileIO> {
    private static final boolean ENABLE_INSTRUMENTATION = false;

    private final AndroidEmulator emulator; //android模拟器a
    private final VM vm;//vm虚拟机
    private final Module module;
    private  final Memory memory;
    private  final DalvikModule dm;
    //将该类封装起来，以后直接套用模板
    public fuxian(String apkFilePath,String soFilePath,String apkProcessname) throws IOException {
        // 创建模拟器实例,进程名建议依照实际进程名填写，可以规避针对进程名的校验
        emulator = AndroidEmulatorBuilder.for64Bit().setProcessName(apkProcessname).build();
        memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));
        vm = emulator.createDalvikVM(new File(apkFilePath));
        vm.setVerbose(false); // 打印日志，会在调用初始化JNI_onload打印一些信息，默认：false
        // 加载目标SO
        dm = vm.loadLibrary(new File(soFilePath), true); // 加载so到虚拟内存，第二个参数：是否需要初始化
        //获取本SO模块的句柄
        module = dm.getModule();
        vm.setJni(this); //设置Jni，防止报错
        //创建完后，需要调用JNI_onload函数
        emulator.getSyscallHandler().addIOResolver(this);
        dm.callJNI_OnLoad(emulator); // 调用JNI OnLoad，进行动态注册某些函数。如果都是静态注册，那就不用调用这个函数

//        debugger(0x1F9DA8); //转字符串前
//        debugger(0x1AF284); //获取时间断点
//        debugger(0x21a668); //进第一次0x27C880前
//        debugger(0x21A6F4); //第一次0x27C880后，有生成的数据
//        debugger(0x1c51e0); //含有最终结果
//        debugger(0x280A60); //sha1数据填充
//        debugger_x0(0x27C880, 0x4077a600);//疑似魔改sha1
//        debugger(0x280C18);//四字节逆序(最后一轮，第三次)
//        debugger_x0(0x1CD994, 0x407ac00f);//memcpy,0xb-0x22最后一轮
//        debugger_x0(0x1EC2A4, 0x406139f0); //0x4b-0x5d十六进制转字符串
//        debugger_x0(0x1EC3B4, 0x407a00e0); //0x4b-0x5d字符串转十六进制
//        debugger(0x280c28);//字节逆序
//        debugger3(0x27C880);//疑似sha1,x1
//        trace(0x26DDE0, 0x270044);
// ----------------------------------固定值----------------------------------------------------------------
//        debugger(0x1F0D54);
//        debugger_with_reg(0x1F0D98, Arm64Const.UC_ARM64_REG_X9, 0x402b23da);
//        debugger_by_mem(0x1ED7D4, Arm64Const.UC_ARM64_REG_X1, 0x3130);
//        debugger_with_reg(0x1EC37C, Arm64Const.UC_ARM64_REG_X19, 0x40617628); //0x31 '1' -> 0x1
//        debugger(0x1AD258); //0a 0x1写入
//        debugger(0x1CD5E8); //0a 0x1 -> 0xa
//        debugger(0x1C22B8); //0x12 0x2写入
//        debugger(0x1CDACC); //0x12 0x2 -> 0x12
//        debugger(0x1B7100);
//        debugger(0x1ABEE8);
//        debugger(0x1C22F0);
//        debugger(0x1CDB84);
//        debugger(0x1C1820); //0x1a 0x3写入
//        debugger(0x1CE0C8); //0x1a 0x3 -> 0x1a
//        debugger(0x1B7158);
//        debugger(0x1A8590);
//        debugger(0x1C1854);
//        debugger(0x1CE1DC);
//        debugger(0x1D0438); // x0
//        debugger(0x1D0A18);
//        debugger(0x1D1280);
//        emulator.traceWrite(0x4079219d, 0x4079219d);
//        trace(0x1BDC88, 0x1BE65C);
// ----------------------------------长度相关----------------------------------------------------------------
// ----------------------------------固定值----------------------------------------------------------------
//        debugger_with_reg(0x1d1294, Arm64Const.UC_ARM64_REG_X11, 0x407ac00e);
//        debugger(0x1CF5F4); //0x5 -> 0x2e
//        debugger(0x1C6038);

//        debugger(0x1F9C50);
//        debugger(0x1f9f54);
//        debugger(0x1F9DA8);
//        debugger(0x1A54DC);
//        debugger(0x1CF628);
//        debugger_with_reg(0x1EABB4, Arm64Const.UC_ARM64_REG_X20, 0x14);
//        debugger_with_reg(0x1D05A4, Arm64Const.UC_ARM64_REG_X8, 0x14);
//        debugger(0x1D0A5C);
//        debugger(0x1D1280);
//        emulator.traceWrite(0x402a64c8, 0x402a64c8);

        // 固定值0x36
//        debugger_with_reg(0x1C3978, Arm64Const.UC_ARM64_REG_X9, 0x6);
//        debugger(0x1CCB38);
//        debugger_with_reg(0x1CCB58, Arm64Const.UC_ARM64_REG_X0, 0x36);
//        debugger_with_reg(0x1d08c4, Arm64Const.UC_ARM64_REG_X8, 0x36);
//        debugger_with_reg(0x1CD688, Arm64Const.UC_ARM64_REG_X0, 0x36);
//        debugger_with_reg(0x1D05A0, Arm64Const.UC_ARM64_REG_X8, 0x36);
//        debugger_with_reg(0x1d1294, Arm64Const.UC_ARM64_REG_X11, 0x407ac023);
//        emulator.traceWrite(0x40ac9090, 0x40ac9090);

        // 固定值0x6
//        debugger(0x1CCB80);
//        debugger_with_reg(0x1CD688, Arm64Const.UC_ARM64_REG_X0, 0x06);
//        debugger_with_reg(0x1D05A0, Arm64Const.UC_ARM64_REG_X8, 0x06);
//        debugger_with_reg(0x1d1294, Arm64Const.UC_ARM64_REG_X11, 0x407ac024);

        // 固定值0x3e
//        debugger_with_reg(0x1C3978, Arm64Const.UC_ARM64_REG_X9, 0x7);
//        debugger(0x1CCB38); // 0x7->0x3e
//        debugger_with_reg(0x1CCB58, Arm64Const.UC_ARM64_REG_X0, 0x3e);
//        debugger_with_reg(0x1CD688, Arm64Const.UC_ARM64_REG_X0, 0x3e);
//        debugger_with_reg(0x1D05A0, Arm64Const.UC_ARM64_REG_X8, 0x3e);
//        debugger_with_reg(0x1d1294, Arm64Const.UC_ARM64_REG_X11, 0x407ac02b);
//        emulator.traceWrite(0x40ac90a8, 0x40ac90a8);
// ----------------------------------固定值----------------------------------------------------------------
        // 固定值0x42
//        debugger(0x1D05A4);
//        debugger(0x1CCD08);
//        debugger(0x1CD5E8); // 8 -> 0x42
//        debugger_with_reg(0x1BE65C, Arm64Const.UC_ARM64_REG_X1, 0x8);
//        debugger_with_reg(0x1CD688, Arm64Const.UC_ARM64_REG_X0, 0x42);
//        debugger_with_reg(0x1D05A0, Arm64Const.UC_ARM64_REG_X8, 0x42);
//        debugger_with_reg(0x1d1294, Arm64Const.UC_ARM64_REG_X11, 0x407ac033);
//        emulator.traceWrite(0x40ac90C0,0x40ac90C0);
//        trace(0x1D0418, 0x1D1A14, false);

        // 固定值0x2
//        debugger(0x1CD6A0);
//        debugger(0x1ae674); //0x2
//        debugger_with_reg(0x1BE684, Arm64Const.UC_ARM64_REG_X3, 2);
//        debugger_with_reg(0x1CD6A8, Arm64Const.UC_ARM64_REG_X8, 2);
//        emulator.traceWrite(0x40ac90c8, 0x40ac90c8);

        // 固定值0x4e
//        debugger_with_reg(0x1C3978, Arm64Const.UC_ARM64_REG_X9, 0x9);
//        debugger(0x1CCB38); // 0x9 -> 0x4e
//        debugger_with_reg(0x1ccb58, Arm64Const.UC_ARM64_REG_X0, 0x4e);
//        debugger_with_reg(0x1D08C4, Arm64Const.UC_ARM64_REG_X8, 0x4e);
//        debugger_with_reg(0x1D05A0, Arm64Const.UC_ARM64_REG_X8, 0x4e);
//        debugger_with_reg(0x1d1294, Arm64Const.UC_ARM64_REG_X11, 0x407ac035);
//        emulator.traceWrite(0x40ac90d8, 0x40ac90d8);

        // 固定值0x11
//        debugger(0x1CCB80);
//        debugger_with_reg(0x1ccc04, Arm64Const.UC_ARM64_REG_X0, 0x11);
//        debugger_with_reg(0x1D08C4, Arm64Const.UC_ARM64_REG_X8, 0x11);
//        debugger_with_reg(0x1D05A0, Arm64Const.UC_ARM64_REG_X8, 0x11);
//        debugger_with_reg(0x1d1294, Arm64Const.UC_ARM64_REG_X11, 0x407ac036);
//        emulator.traceWrite(0xbfffea98L, 0xbfffea98L);

        // 固定值0x56
//        debugger_with_reg(0x1C6038, Arm64Const.UC_ARM64_REG_X10, 0xa);
//        debugger(0x1CF5FC);
//        debugger_with_reg(0x1cf608, Arm64Const.UC_ARM64_REG_X0, 0x56);
//        debugger_with_reg(0x1D08C4, Arm64Const.UC_ARM64_REG_X8, 0x56);
//        debugger_with_reg(0x1D05A0, Arm64Const.UC_ARM64_REG_X8, 0x56);
//        debugger_with_reg(0x1d1294, Arm64Const.UC_ARM64_REG_X11, 0x407ac048);
//        emulator.traceWrite(0x40ac90f0, 0x40ac90f0);

        // 固定值0x14
//        debugger(0x1F9C54);
//        debugger_with_reg(0x1F9F54, Arm64Const.UC_ARM64_REG_X0, 0x14);
//        debugger_with_reg(0x1EABB4, Arm64Const.UC_ARM64_REG_X20, 0x14);
//        debugger_with_reg(0x1F9DA8, Arm64Const.UC_ARM64_REG_X1, 0x14);
//        debugger_with_reg(0x1EAC2C, Arm64Const.UC_ARM64_REG_X21, 0x14);
//        debugger_with_reg(0x1A54DC, Arm64Const.UC_ARM64_REG_X4, 0x14);
//        debugger_with_reg(0x1C6068, Arm64Const.UC_ARM64_REG_X9, 0x14);
//        debugger(0x1CF628);
//        debugger_with_reg(0x1D08C4, Arm64Const.UC_ARM64_REG_X8, 0x14);
//        debugger_with_reg(0x1D05A0, Arm64Const.UC_ARM64_REG_X8, 0x14);
//        debugger_with_reg(0x1d1294, Arm64Const.UC_ARM64_REG_X11, 0x407ac049);
//        emulator.traceWrite(0x4063f270, 0x4063f270);

        // 固定值0x5e
//        debugger_with_reg(0x1C3978, Arm64Const.UC_ARM64_REG_X9, 0xb);
//        debugger(0x1CCB38); //0xb -> 0x5e
//        debugger_with_reg(0x1D08C4, Arm64Const.UC_ARM64_REG_X8, 0x5e);
//        debugger_with_reg(0x1D05A0, Arm64Const.UC_ARM64_REG_X8, 0x5e);
//        debugger_with_reg(0x1d1294, Arm64Const.UC_ARM64_REG_X11, 0x407ac05e);
//        emulator.traceWrite(0x40ac9108, 0x40ac9108);

        // 固定值0x00

        // 固定值0x76
//        debugger_with_reg(0x1C3978, Arm64Const.UC_ARM64_REG_X9, 0xe);
//        debugger(0x1CCB38); // 0xe -> 0x76
//        debugger_with_reg(0x1D08C4, Arm64Const.UC_ARM64_REG_X8, 0x76);
//        debugger_with_reg(0x1D05A0, Arm64Const.UC_ARM64_REG_X8, 0x76);
//        debugger_with_reg(0x1d1294, Arm64Const.UC_ARM64_REG_X11, 0x407ac060);
//        emulator.traceWrite(0x40ac9120, 0x40ac9120);

        // 固定值0x20
//        debugger(0x1CCB80);
//        debugger_with_reg(0x1D08C4, Arm64Const.UC_ARM64_REG_X8, 0x20);
//        debugger_with_reg(0x1D05A0, Arm64Const.UC_ARM64_REG_X8, 0x20);
//        debugger_with_reg(0x1d1294, Arm64Const.UC_ARM64_REG_X11, 0x407ac061);

        // 固定值0x83
//        debugger(0x1D0A18);
//        debugger_with_reg(0x1C22B8, Arm64Const.UC_ARM64_REG_X10, 0x10);
//        debugger(0x1CF6B4); // 0x10 -> 0x83
//        debugger_with_reg(0x1CF6C0, Arm64Const.UC_ARM64_REG_X0, 0x83);
//        debugger_with_reg(0x1D05A4, Arm64Const.UC_ARM64_REG_X8, 0x83);
//        debugger_with_reg(0x1d0a1c, Arm64Const.UC_ARM64_REG_X8, 0x83); // 0x83 -> 0x3
//        debugger_with_reg(0x1D0A5C, Arm64Const.UC_ARM64_REG_X8, 0x3);
//        debugger(0x1D17C8); // 0x3 -> 0x83
//        debugger_with_reg(0x1D17DC, Arm64Const.UC_ARM64_REG_X8, 0x83);
//        emulator.traceWrite(0x40ac9138, 0x40ac9138);

        // 固定值0x1
//        debugger(0x1D1198);
//        debugger_with_reg(0x1d119c, Arm64Const.UC_ARM64_REG_X8, 0x83);
//        debugger_with_reg(0x1D11AC, Arm64Const.UC_ARM64_REG_X8, 0x1);
//        debugger_with_reg(0x1D08C4, Arm64Const.UC_ARM64_REG_X8, 0x1);
//        debugger_with_reg(0x1D05A0, Arm64Const.UC_ARM64_REG_X8, 0x1);
//        debugger_with_reg(0x1d1294, Arm64Const.UC_ARM64_REG_X11, 0x407ac083);
//        emulator.traceWrite(0xbfffeab8L, 0xbfffeab8L);

        // 固定值0x6
//        debugger(0x1A0BD0);
//        debugger_with_reg(0x1A0BDC, Arm64Const.UC_ARM64_REG_X8, 0x0);
//        debugger(0x19E0AC);
//        debugger_with_reg(0x19E15C, Arm64Const.UC_ARM64_REG_X8, 0x2);
//        debugger_with_reg(0x1A2E40, Arm64Const.UC_ARM64_REG_X8, 0x2);
//        debugger(0x1A0818);
//        debugger_with_reg(0x1A0824, Arm64Const.UC_ARM64_REG_X8, 0x2);
//        debugger(0x1A35C8);
//        debugger_with_reg(0x1A35E8, Arm64Const.UC_ARM64_REG_X8, 0x6);
//        debugger(0x19FCA8);
//        debugger_with_reg(0x19FCB4, Arm64Const.UC_ARM64_REG_X8, 0x6);
//        debugger_with_reg(0x1A19F0, Arm64Const.UC_ARM64_REG_X9, 0x6);
//        debugger(0x19FBD8);
//        debugger_with_reg(0x19FBE4, Arm64Const.UC_ARM64_REG_X8, 0x6);
//        debugger_with_reg(0x1A1B90, Arm64Const.UC_ARM64_REG_X8, 0x6);
//        debugger(0x1A0EF4);
//        debugger_with_reg(0x1A0EFC, Arm64Const.UC_ARM64_REG_X9, 0x6);
//        debugger_with_reg(0x1A17D4, Arm64Const.UC_ARM64_REG_X9, 0x6);
//        debugger(0x19FA58);
//        debugger_with_reg(0x19FA64, Arm64Const.UC_ARM64_REG_X8, 0x6);
//        debugger_with_reg(0x1A09E8, Arm64Const.UC_ARM64_REG_X9, 0x6);
//        debugger(0x19F308);
//        debugger_with_reg(0x19F314, Arm64Const.UC_ARM64_REG_X8, 0x6);
//        debugger_with_reg(0x1A2228, Arm64Const.UC_ARM64_REG_X8, 0x6);
//        debugger(0x1A1F84);
//        debugger_with_reg(0x1A1F90, Arm64Const.UC_ARM64_REG_X8, 0x6);
//        debugger(0x1A2A74);
//        debugger_with_reg(0x1A2A7C, Arm64Const.UC_ARM64_REG_X9, 0x6);
//        debugger(0x1A15DC);
//        debugger_with_reg(0x1A15E4, Arm64Const.UC_ARM64_REG_X9, 0x6);
//        debugger(0x19F4EC); //0x6 -> 0x206
//        debugger_with_reg(0x19F504, Arm64Const.UC_ARM64_REG_X8, 0x206);
//        debugger(0x1A1194);
//        debugger_with_reg(0x1A11A4, Arm64Const.UC_ARM64_REG_X10, 0x206);
//        debugger_with_reg(0x1A1240, Arm64Const.UC_ARM64_REG_X8, 0x206);
//        debugger(0x1A0850);
//        debugger_with_reg(0x1A086C, Arm64Const.UC_ARM64_REG_X8, 0x206);
//        debugger(0x1A0194);
//        debugger_with_reg(0x1A01A0, Arm64Const.UC_ARM64_REG_X8, 0x206);
//        debugger(0x19FEC4);
//        debugger_with_reg(0x19FED4, Arm64Const.UC_ARM64_REG_X8, 0x206);
//        debugger_with_reg(0x1A1F7C, Arm64Const.UC_ARM64_REG_X8, 0x206);
//        debugger(0x1A182C);
//        debugger_with_reg(0x1A183C, Arm64Const.UC_ARM64_REG_X8, 0x206);
//        debugger(0x1AA470);
//        debugger_with_reg(0x1C22FC, Arm64Const.UC_ARM64_REG_X9, 0x206);
//        debugger_with_reg(0x1CA2E0, Arm64Const.UC_ARM64_REG_X21, 0x6);
//        emulator.traceWrite(0xbfffeea4L, 0xbfffeea4L);
// ----------------------------------md5----------------------------------------------------------------
//        debugger(0x65698); //获取需要md5的数据
//        debugger_x0(0x1eac50, 0x40796f10);//memcpy
//        debugger_x0(0x27BB58, 0xbfffea78L);//memcpy
//        debugger(0x279A60);//md5,x1是输入
//        debugger_x0(0x1EAC50, 0x4063f270);//memcpy
//        debugger_x0(0x1EC2D8, 0x40613ab0);//十六进制转字符串
//        debugger_x0(0x1ED7D4, 0x40613ae0);//memcpy
//        debugger_x0(0x1C3DCC, 0x40613b10);//strcpy
//        debugger_x0(0x1CEAB0, 0x407ac062);//strcpy
//        emulator.traceWrite(0x40796f00, 0x40796f10);
// -----------------------------------sha1-----------------------------------------------
//        debugger_x0(0x1EAC50, 0x4063f2c0);
//        debugger(0x21ADCC);
//        debugger_with_reg(0x219AA8, Arm64Const.UC_ARM64_REG_X10, 0x5f);
//        debugger_x0(0x27C880, 0x4077a600);
//        debugger(0x280404);
//        debugger_with_reg(0x27FCA8, Arm64Const.UC_ARM64_REG_X8, 0x739d9111);
//        debugger(0x280C18);
//        debugger_with_reg(0x280C28, Arm64Const.UC_ARM64_REG_X19, 0x407a00c0);
//        debugger_x0(0x1EAC50, 0x407a0080);
//        debugger_x0(0x1C51E0, 0x407a00c0);
//        debugger_x0(0x1CD994, 0x407ac00f);
//        emulator.traceWrite(0x407a0080, 0x407a008f);
//----------------------------------时间加密--------------------------------------------------------------
//        debugger(0x1b03a4);//时间加密，x8
//        emulator.traceWrite(0x407ac000, 0x407ac090); //最后的数据记录
//----------------------------------base64---------------------------------------------------------------
//        debugger(0x1AB408);//最后的变表base64
//        debugger(0x207CC4);
//        emulator.traceCode(0x207CC4 + module.base, 0x208A64 + module.base);
//        debugger(0x1a6290);
//        emulator.traceWrite(0x4063f250, 0x4063f260);
//        trace(0x10768, 0x11180, true);
    }

    public String func_sig(){
        DvmObject<?> context = vm.resolveClass("android.content.Context").newObject(null);
//        long arg1 = System.currentTimeMillis();
        long arg1 = 1744781882714L;
//        byte[] arg3 = {118,49,46,48,46,48,105,112,118,54,49,54,56,54,102,55,51,55,52,55,51,51,100,54,56,54,52,50,101,55,56,54,57,54,49,54,102,54,97,55,53,54,98,54,53,54,97,54,57,50,101,54,51,54,102,54,100,50,53,51,50,51,48,54,100,55,51,54,55,54,55,54,49,55,52,54,53,50,101,55,56,54,57,54,49,54,102,54,97,55,53,54,98,54,53,54,97,54,57,50,101,54,51,54,102,54,100,50,53,51,50,51,48,54,102,54,54,54,54,54,99,54,57,54,101,54,53,50,100,55,48,54,98,54,55,50,101,54,52,54,57,54,52,54,57,50,101,54,51,54,101,50,53,51,50,51,48,54,53,55,48,54,49,55,51,55,51,55,48,54,102,55,50,55,52,50,101,54,52,54,57,54,52,54,57,55,52,54,49,55,56,54,57,50,101,54,51,54,102,54,100,50,101,54,51,54,101};
//        byte[] arg3 = {118,49,46,48,46,48,105,112,118,54,49,54,56,54,102,55,51,55,52,55,51,51,100,54,56,54,52,50,101,55,56,54,57,54,49,54,102,54,97,55,53,54,98,54,53,54,97,54,57,50,101,54,51,54,102,54,100,50,53,51,50,51,48,54,100,55,51,54,55,54,55,54,49,55,52,54,53,50,101,55,56,54,57,54,49,54,102,54,97,55,53,54,98,54,53,54,97,54,57,50,101,54,51,54,102,54,100,50,53,51,50,51,48,54,102,54,54,54,54,54,99,54,57,54,101,54,53,50,100,55,48,54,98,54,55,50,101,54,52,54,57,54,52,54,57,50,101,54,51,54,101,50,53,51,50,51,48,54,53,55,48,54,49,55,51,55,51,55,48,54,102,55,50,55,52,50,101,54,52,54,57,54,52,54,57,55,52,54,49,55,56,54,57,50,101,54,51,54,102,54,100,50,101,54,51,54,101};
//        byte[] arg3 = {118,49,46,48,46,48,105,112,118,54,49,54,56,54,102,55,51,55,52,55,51,51,100,54,56,54,52,50,101,55,56,54,57,54,49,54,102,54,97,55,53,54,98,54,53,54,97,54,57,50,101,54,51,54,102,54,100,50,53,51,50,51,48,54,100,55,51,54,55,54,55,54,49,55,52,54,53,50,101,55,56,54,57,54,49,54,102,54,97,55,53,54,98,54,53,54,97,54,57,50,101,54,51,54,102,54,100,50,53,51,50,51,48,54,102,54,54,54,54,54,99,54,57,54,101,54,53,50,100,55,48,54,98,54,55,50,101,54,52,54,57,54,52,54,57,50,101,54,51,54,101,50,53,51,50,51,48,54,53,55,48,54,49,55,51,55,51,55,48,54,102,55,50,55,52,50,101,54,52,54,57,54,52,54,57,55,52,54,49,55,56,54,57,50,101,54,51,54,102,54,100,50,101,54,51,54,101};
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
//        System.out.println(arg1);
//        new MemMonitor(emulator).init();
                if (ENABLE_INSTRUMENTATION) {
            emulator.traceCode(module.base, module.base + module.size);
        }
        DvmObject object1=object.callJniMethodObject(emulator, "nativeSig(Landroid/content/Context;JLjava/lang/String;[B)Ljava/lang/String;", context, arg1, arg2, arg3);
        return object1.getValue().toString();
    }

    @Override
    public boolean callStaticBooleanMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg){
        if(signature.equals("com/didi/security/wireless/SecurityLib->ApolloGetToggle(Landroid/content/Context;Ljava/lang/String;Z)Z")){
            return false;
        }
        if(signature.equals("com/didi/security/wireless/StatUtils->isNetworkAvailable(Landroid/content/Context;)Z")){
            return true;
        }
        return super.callBooleanMethod(vm, dvmClass, signature, varArg);
    }

    @Override
    public DvmObject<?> callObjectMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg){
        if(signature.equals("android/content/Context->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;")){
            return vm.resolveClass("android/content/SharedPreferences").newObject(varArg.getObjectArg(0));
        }
        if(signature.equals("android/content/SharedPreferences->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")){
            return new StringObject(vm, "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");
        }
        if(signature.equals("android/content/Context->getPackageCodePath()Ljava/lang/String;")){
            return new StringObject(vm, "/data/app/com.sdu.didi.gsui-mGlj-PzyfIpeLiDCnY-Qcw==/base.apk");
        }
        if(signature.equals("android/content/Context->getFilesDir()Ljava/io/File;")){
            return vm.resolveClass("java/io/File").newObject("/data/data/com.sdu.didi.gsui/files/");
        }
        if(signature.equals("java/io/File->getCanonicalPath()Ljava/lang/String;")) {
            return new StringObject(vm, "/data/data/com.sdu.didi.gsui/files");
        }
        return super.callObjectMethod(vm, dvmObject, signature, varArg);
    }

    @Override
    public DvmObject<?> callStaticObjectMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg){
        if(signature.equals("com/didi/security/wireless/SecurityLib->getFeature(I)Ljava/lang/Object;")){
            if(varArg.getIntArg(0) == 8197){
                return new StringObject(vm, "com.sdu.didi.gsui");
            }
            if(varArg.getIntArg(0) == 8198){
                return new StringObject(vm, "9.0.14");
            }
            if(varArg.getIntArg(0) == 8195){
                return new StringObject(vm, "Android");
            }
            if(varArg.getIntArg(0) == 8196){
                return new StringObject(vm, "10");
            }
        }
        if(signature.equals("java/lang/System->getProperty(Ljava/lang/String;)Ljava/lang/String;")){
//            System.out.println("getProperty:" + varArg.getObjectArg(0).toString());
            return null;
        }
        if(signature.equals("android/os/ServiceManager->getService(Ljava/lang/String;)Landroid/os/IBinder;")){
            return vm.resolveClass("android/os/IBinder").newObject(null);
        }
        return super.callStaticObjectMethod(vm, dvmClass, signature, varArg);
    }

    @Override
    public int callStaticIntMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        if(signature.equals("com/didi/security/wireless/SecurityLib->getUserMode()I")){
            return 0;
        }
        return super.callStaticIntMethod(vm, dvmClass, signature, varArg);
    }

    @Override
    public FileResult<AndroidFileIO> resolve(Emulator<AndroidFileIO> emulator, String pathname, int oflags) {
//        System.out.println("path: " + pathname);
        if(pathname.equals("/data/app/com.sdu.didi.gsui-mGlj-PzyfIpeLiDCnY-Qcw==/base.apk")){
            File apk = new File("D:\\project\\TraceAIagent_v3\\tests\\nativeSig\\滴滴车主.apk");
            return FileResult.<AndroidFileIO>success(new SimpleFileIO(oflags, apk, pathname));
        }
        return null;
    }

    private void debugger(long offset){
//        emulator.attach(DebuggerType.ANDROID_SERVER_V7).addBreakPoint(module, offset);
                if (ENABLE_INSTRUMENTATION) {
            emulator.attach().addBreakPoint(module, offset, new BreakPointCallback() {
                        @Override
                        public boolean onHit(Emulator<?> emulator, long address) {
            //                System.out.println("0x1B9D2C:");
            //                emulator.showRegs();
                            return false;
                        }
                    });
        }
    }

    private void debugger_with_reg(long offset, int reg, long reg_value){
                if (ENABLE_INSTRUMENTATION) {
            emulator.attach().addBreakPoint(module, offset, new BreakPointCallback() {
                        @Override
                        public boolean onHit(Emulator<?> emulator, long address) {
                            long x = emulator.getContext().getLongByReg(reg);
            //                System.out.println(x0);
                            return x != reg_value;
                        }
                    });
        }
    }

    private void debugger_by_mem(long offset, int reg, long mem_value){
                if (ENABLE_INSTRUMENTATION) {
            emulator.attach().addBreakPoint(module, offset, new BreakPointCallback() {
                        @Override
                        public boolean onHit(Emulator<?> emulator, long address) {
                            long x = emulator.getContext().getLongByReg(reg);
                            long mem = emulator.getMemory().pointer(x).getShort(0);
            //                System.out.println(mem);
                            return mem != mem_value;
                        }
                    });
        }
    }

    private void debugger_x0(long offset, long x0_offset){
                if (ENABLE_INSTRUMENTATION) {
            emulator.attach().addBreakPoint(module, offset, new BreakPointCallback() {
                        @Override
                        public boolean onHit(Emulator<?> emulator, long address) {
                            long x0 = emulator.getContext().getLongByReg(Arm64Const.UC_ARM64_REG_X0);
            //                System.out.println(x0);
                            return x0 != x0_offset;
                        }
                    });
        }
    }

    private void debugger3(long offset){
                if (ENABLE_INSTRUMENTATION) {
            emulator.attach().addBreakPoint(module, offset, new BreakPointCallback() {
                        @Override
                        public boolean onHit(Emulator<?> emulator, long address) {
                            long x1 = emulator.getContext().getLongByReg(Arm64Const.UC_ARM64_REG_X0);
            //                System.out.println(x0);
                            byte[] ret = emulator.getMemory().pointer(x1).getByteBuffer(0, 0x100).array();
                            StringBuilder hexString = new StringBuilder();
                            for (byte b : ret) {
                                // & 0xFF 将字节转换为无符号整数
                                hexString.append("0x");
                                hexString.append(Integer.toHexString(b & 0xFF).toUpperCase());
                                hexString.append(",");
                            }
                            System.out.println(hexString);
                            return true;
                        }
                    });
        }
    }

    private void trace(int start, int end, boolean showregs){
                if (ENABLE_INSTRUMENTATION) {
            emulator.getBackend().hook_add_new(new CodeHook() {
                        @Override
                        public void hook(Backend backend, long address, int size, Object user) {
                            Capstone capstone = new Capstone(Capstone.CS_ARCH_ARM64, Capstone.CS_MODE_ARM);
                            byte[] bytes = emulator.getBackend().mem_read(address, 4);
                            Instruction[] disasm = capstone.disasm(bytes, 0);
                            long offset = address-module.base;
                            int[] regs = {Arm64Const.UC_ARM64_REG_X0,
                                    Arm64Const.UC_ARM64_REG_X1,
                                    Arm64Const.UC_ARM64_REG_X2,
                                    Arm64Const.UC_ARM64_REG_X3,
                                    Arm64Const.UC_ARM64_REG_X4,
                                    Arm64Const.UC_ARM64_REG_X5,
                                    Arm64Const.UC_ARM64_REG_X6,
                                    Arm64Const.UC_ARM64_REG_X7,
                                    Arm64Const.UC_ARM64_REG_X8,
                                    Arm64Const.UC_ARM64_REG_X9,
                                    Arm64Const.UC_ARM64_REG_X10,
                                    Arm64Const.UC_ARM64_REG_X11,
                                    Arm64Const.UC_ARM64_REG_X12,
                                    Arm64Const.UC_ARM64_REG_X13,
                                    Arm64Const.UC_ARM64_REG_X14,
                                    Arm64Const.UC_ARM64_REG_X15,
                                    Arm64Const.UC_ARM64_REG_X16,
                                    Arm64Const.UC_ARM64_REG_X17,
                                    Arm64Const.UC_ARM64_REG_X18,
                                    Arm64Const.UC_ARM64_REG_X19,
                                    Arm64Const.UC_ARM64_REG_X20,
                                    Arm64Const.UC_ARM64_REG_X21,
                                    Arm64Const.UC_ARM64_REG_X22,
                                    Arm64Const.UC_ARM64_REG_X23,
                                    Arm64Const.UC_ARM64_REG_X24,
                                    Arm64Const.UC_ARM64_REG_X25,
                                    Arm64Const.UC_ARM64_REG_X26,
                                    Arm64Const.UC_ARM64_REG_X27,
                                    Arm64Const.UC_ARM64_REG_X28};
                            if(showregs){
                                emulator.showRegs(regs);
                            }
                            System.out.printf("0x%x 0x%x %s %s\n",address, offset,disasm[0].getMnemonic(),disasm[0].getOpStr());
                        }
            
                        @Override
                        public void onAttach(UnHook unHook) {
            
                        }
            
                        @Override
                        public void detach() {
            
                        }
                    }, module.base + start, module.base + end, null);
        }
    }

    public static void main(String[] args) throws IOException {
        // 1、需要调用的so文件所在路径
        String soFilePath = "D:\\project\\TraceAIagent_v3\\tests\\nativeSig\\libdidiwsg.so";
        // 2、APK的路径
        String apkFilePath="D:\\project\\TraceAIagent_v3\\tests\\nativeSig\\滴滴车主.apk";
        // 3、apk进程名
        String apkProcessname="com.sdu.didi.gsui";
        fuxian myapp=new fuxian(apkFilePath,soFilePath,apkProcessname);
        System.out.println("__BASELINE_RESULT__=" + String.valueOf(myapp.func_sig()));
    }
}

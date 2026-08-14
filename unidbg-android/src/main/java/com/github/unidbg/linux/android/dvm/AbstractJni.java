package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.linux.android.dvm.api.ApplicationInfo;
import com.github.unidbg.linux.android.dvm.api.AssetManager;
import com.github.unidbg.linux.android.dvm.api.Binder;
import com.github.unidbg.linux.android.dvm.api.Bundle;
import com.github.unidbg.linux.android.dvm.api.ClassLoader;
import com.github.unidbg.linux.android.dvm.api.PackageInfo;
import com.github.unidbg.linux.android.dvm.api.ServiceManager;
import com.github.unidbg.linux.android.dvm.api.Signature;
import com.github.unidbg.linux.android.dvm.api.SystemService;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.linux.android.dvm.array.CharArray;
import com.github.unidbg.linux.android.dvm.jni.ProxyDvmObject;
import com.github.unidbg.linux.android.dvm.wrapper.DvmBoolean;
import com.github.unidbg.linux.android.dvm.wrapper.DvmInteger;
import com.github.unidbg.linux.android.dvm.wrapper.DvmLong;
import net.dongliu.apk.parser.bean.CertificateMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.*;
import java.util.*;

public abstract class AbstractJni implements Jni {

    private static final Logger log = LoggerFactory.getLogger(AbstractJni.class);

    @Override
    public DvmObject<?> getStaticObjectField(BaseVM vm, DvmClass dvmClass, DvmField dvmField) {
        return getStaticObjectField(vm, dvmClass, dvmField.getSignature());
    }

    @Override
    public DvmObject<?> getStaticObjectField(BaseVM vm, DvmClass dvmClass, String signature) {
        log.info("getStaticObjectField [Unidbg]: {}", signature);
        DvmObject<?> supportedAbis = tryAndroidBuildSupportedAbis(vm, signature);
        if (supportedAbis != null) {
            return supportedAbis;
        }
        String androidBuildString = getAndroidBuildString(vm, signature);
        if (androidBuildString != null) {
            return new StringObject(vm, androidBuildString);
        }
        switch (signature) {
            // ==================== Android 系统服务名称常量 ====================
            // 这些字符串用于 context.getSystemService(name) 获取系统服务
            case "android/content/Context->TELEPHONY_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.TELEPHONY_SERVICE);  // "phone"
            case "android/content/Context->WIFI_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.WIFI_SERVICE);       // "wifi"
            case "android/content/Context->CONNECTIVITY_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.CONNECTIVITY_SERVICE); // "connectivity"
            case "android/content/Context->ACCESSIBILITY_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.ACCESSIBILITY_SERVICE); // "accessibility"
            case "android/content/Context->KEYGUARD_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.KEYGUARD_SERVICE);   // "keyguard"
            case "android/content/Context->ACTIVITY_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.ACTIVITY_SERVICE);   // "activity"
            case "android/content/Context->LOCATION_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.LOCATION_SERVICE);   // "location"
            case "android/content/Context->WINDOW_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.WINDOW_SERVICE);     // "window"
            case "android/content/Context->SENSOR_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.SENSOR_SERVICE);     // "sensor"
            case "android/content/Context->UI_MODE_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.UI_MODE_SERVICE);    // "uimode"
            case "android/content/Context->DISPLAY_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.DISPLAY_SERVICE);    // "display"
            case "android/content/Context->AUDIO_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.AUDIO_SERVICE);      // "audio"
            case "android/content/Context->BLUETOOTH_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.BLUETOOTH_SERVICE);  // "bluetooth"
            case "android/content/Context->CLIPBOARD_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.CLIPBOARD_SERVICE);  // "clipboard"
            case "android/content/Context->USER_SERVICE:Ljava/lang/String;":
                return new StringObject(vm, SystemService.USER_SERVICE);      // "user"
            
            // ==================== Java 基本类型包装类的 TYPE 字段 ====================
            // 用于反射获取原始类型的 Class 对象，如 int.class == Integer.TYPE
            case "java/lang/Void->TYPE:Ljava/lang/Class;":
                return vm.resolveClass("java/lang/Void");
            case "java/lang/Boolean->TYPE:Ljava/lang/Class;":
                return vm.resolveClass("java/lang/Boolean");
            case "java/lang/Byte->TYPE:Ljava/lang/Class;":
                return vm.resolveClass("java/lang/Byte");
            case "java/lang/Character->TYPE:Ljava/lang/Class;":
                return vm.resolveClass("java/lang/Character");
            case "java/lang/Short->TYPE:Ljava/lang/Class;":
                return vm.resolveClass("java/lang/Short");
            case "java/lang/Integer->TYPE:Ljava/lang/Class;":
                return vm.resolveClass("java/lang/Integer");
            case "java/lang/Long->TYPE:Ljava/lang/Class;":
                return vm.resolveClass("java/lang/Long");
            case "java/lang/Float->TYPE:Ljava/lang/Class;":
                return vm.resolveClass("java/lang/Float");
            case "java/lang/Double->TYPE:Ljava/lang/Class;":
                return vm.resolveClass("java/lang/Double");
        }

        throw new UnsupportedOperationException(signature);
    }

    @Override
    public boolean getStaticBooleanField(BaseVM vm, DvmClass dvmClass, DvmField dvmField) {
        return getStaticBooleanField(vm, dvmClass, dvmField.getSignature());
    }

    @Override
    public boolean getStaticBooleanField(BaseVM vm, DvmClass dvmClass, String signature) {
        log.info("getStaticBooleanField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public byte getStaticByteField(BaseVM vm, DvmClass dvmClass, DvmField dvmField) {
        return getStaticByteField(vm, dvmClass, dvmField.getSignature());
    }

    @Override
    public byte getStaticByteField(BaseVM vm, DvmClass dvmClass, String signature) {
        log.info("getStaticByteField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public int getStaticIntField(BaseVM vm, DvmClass dvmClass, DvmField dvmField) {
        return getStaticIntField(vm, dvmClass, dvmField.getSignature());
    }


    @Override
    public int getStaticIntField(BaseVM vm, DvmClass dvmClass, String signature) {
        log.info("getStaticIntField [Unidbg]: {}", signature);
        Integer androidBuildInt = getAndroidBuildInt(vm, signature);
        if (androidBuildInt != null) {
            return androidBuildInt;
        }
        switch (signature) {
            // MODE_PRIVATE=0: 文件私有模式，只有本应用可访问
            // 其他值: MODE_WORLD_READABLE=1(废弃), MODE_WORLD_WRITEABLE=2(废弃), MODE_MULTI_PROCESS=4(废弃), MODE_APPEND=32768
            case "android/app/Application->MODE_PRIVATE:I":
                return 0;
            
            // GET_SIGNATURES=0x40: 获取应用签名信息的 flag
            // 其他常用值: GET_ACTIVITIES=0x1, GET_SERVICES=0x4, GET_META_DATA=0x80, GET_SIGNING_CERTIFICATES=0x8000000(API28+)
            case "android/content/pm/PackageManager->GET_SIGNATURES:I":
                return 0x40;
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public DvmObject<?> getObjectField(BaseVM vm, DvmObject<?> dvmObject, DvmField dvmField) {
        return getObjectField(vm, dvmObject, dvmField.getSignature());
    }


    @Override
    public DvmObject<?> getObjectField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        log.info("getObjectField [Unidbg]: {}", signature);
        AndroidPackageObjectFieldResult packageField = tryAndroidPackageObjectField(vm, dvmObject, signature);
        if (packageField.handled) {
            return packageField.value;
        }
        AndroidPackageObjectFieldResult featureField = tryAndroidFeatureObjectField(vm, dvmObject, signature);
        if (featureField.handled) {
            return featureField.value;
        }
        AndroidAccountObjectResult accountField = tryAndroidAccountObjectField(vm, dvmObject, signature);
        if (accountField.handled) {
            return accountField.value;
        }
        AndroidConfigurationObjectResult configurationLocaleField =
                tryAndroidConfigurationLocaleField(vm, dvmObject, signature);
        if (configurationLocaleField.handled) {
            return configurationLocaleField.value;
        }
        if (isConfiguredPackageInfo(dvmObject)
                && signature != null
                && signature.startsWith("android/content/pm/PackageInfo->")) {
            // Live missing/unsupported field, or foreign-VM marker: never fall through
            // to api.PackageInfo casts (no sidecar). Live configured values were already
            // returned by isLiveConfiguredPackageInfo readers.
            throw new UnsupportedOperationException(signature);
        }
        if (dvmObject != null && dvmObject.getValue() instanceof ConfiguredApplicationInfo
                && signature != null
                && signature.startsWith("android/content/pm/ApplicationInfo->")) {
            // Live missing/unsupported field, or foreign-VM marker: never fall through
            // to current-app ApplicationInfo defaults (no sidecar). Live configured
            // values were already returned by isLiveConfiguredApplicationInfo readers.
            throw new UnsupportedOperationException(signature);
        }
        if (isConfiguredFeatureInfo(dvmObject)
                && signature != null
                && signature.startsWith("android/content/pm/FeatureInfo->")) {
            // Live missing/unsupported field, or foreign-VM marker: never fall through
            // (no sidecar). Live configured values were already returned by
            // isLiveConfiguredFeatureInfo readers.
            throw new UnsupportedOperationException(signature);
        }
        switch (signature) {
            // APK安装路径，用于签名校验、读取APK资源
            // 格式参照真机的: /data/app/~~{randomSuffix}==/{packageName}-{randomSuffix}-{randomSuffix}==/base.apk (Android 5.0+)
            // 如 "/data/app/~~qpHWxYkAy6LDczEHNqq4AA==/cn.ys1231.appproxy-8yo317Fk7bcx-AAPKMuTjg==/base.apk"
            // 指纹风险: 低。随机后缀每次安装都不同，不算设备指纹
            // 注意: 如果SO校验路径存在会失败，需配合文件系统模拟
            case "android/content/pm/ApplicationInfo->sourceDir:Ljava/lang/String;":
            case "android/content/pm/ApplicationInfo->publicSourceDir:Ljava/lang/String;": {
                // 两个字段合并处理，返回完全一样的、且做过缓存的路径
                TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
                String fallback = "/data/app/~~qpHWxYkAy6LDczEHNqq4AA==/" + vm.getPackageName() + "-8yo317Fk7bcx-AAPKMuTjg==/base.apk";
                String apkPath = config == null ? fallback : config.getApkPath(fallback);
                log.info("注意：这里在 读取 APK 路径， 已被固定为: {}", apkPath);
                return new StringObject(vm, apkPath);
            }
            case "android/content/pm/ApplicationInfo->dataDir:Ljava/lang/String;": {
                TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
                String fallback = "/data/user/0/" + vm.getPackageName();
                String dataDir = config == null ? fallback : config.getDataDir(fallback);
                return new StringObject(vm, dataDir);
            }
            case "android/content/pm/ApplicationInfo->nativeLibraryDir:Ljava/lang/String;": {
                TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
                String abi = vm.getEmulator().is64Bit() ? "arm64" : "arm";
                String fallback = "/data/app/~~qpHWxYkAy6LDczEHNqq4AA==/" + vm.getPackageName() + "-8yo317Fk7bcx-AAPKMuTjg==/lib/" + abi;
                String nativeLibraryDir = config == null ? fallback : config.getNativeLibraryDir(vm.getEmulator().is64Bit(), fallback);
                return new StringObject(vm, nativeLibraryDir);
            }
            case "android/content/pm/ApplicationInfo->packageName:Ljava/lang/String;": {
                String packageName = vm.getPackageName();
                if (packageName != null) {
                    return new StringObject(vm, packageName);
                }
                break;
            }

            // 应用签名数组，用于签名校验/防篡改检测
            // unidbg 已实现，源码在 ApkFile.java:
            //   ApkFile apkFile = new ApkFile(this.apkFile);
            //   for (ApkSigner signer : apkFile.getApkSingers()) {
            //       signatures.addAll(signer.getCertificateMetas());
            //   }
            // 使用 apk-parser 库解析 APK 的 META-INF/*.RSA 获取 v1 签名
            // 指纹风险: 极高！必须使用真实APK，否则签名校验失败
            case "android/content/pm/PackageInfo->signatures:[Landroid/content/pm/Signature;":
                PackageInfo packageInfo = (PackageInfo) dvmObject;
                if (packageInfo.getPackageName().equals(vm.getPackageName())) {
                    CertificateMeta[] metas = vm.getSignatures();
                    if (metas != null) {
                        Signature[] signatures = new Signature[metas.length];
                        for (int i = 0; i < metas.length; i++) {
                            signatures[i] = new Signature(vm, metas[i]);
                        }
                        return new ArrayObject(signatures);
                    }
                    log.info("这里是在读处理apk签名!!");
                }
            
            // app版本名称，如 "1.0.0"
            // unidbg 已实现，源码在 ApkFile.java:
            //   apkMeta = apkFile.getApkMeta();
            //   return apkMeta.getVersionName();
            // 使用 apk-parser 库解析 AndroidManifest.xml 获取
            // 指纹风险: 中。版本号可能参与签名计算
            case "android/content/pm/PackageInfo->versionName:Ljava/lang/String;":
                PackageInfo packageInfo_tmp = (PackageInfo) dvmObject;
                if (packageInfo_tmp.getPackageName().equals(vm.getPackageName())) {
                    String versionName = vm.getVersionName();
                    if (versionName != null) {
                        return new StringObject(vm, versionName);
                    }
                }
        }

        throw new UnsupportedOperationException(signature);
    }

    @Override
    public boolean callStaticBooleanMethod(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VarArg varArg) {
        return callStaticBooleanMethod(vm, dvmClass, dvmMethod.getSignature(), varArg);
    }

    @Override
    public boolean callStaticBooleanMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        log.info("callStaticBooleanMethod [Unidbg]: {}", signature);
        if ("android/os/Debug->isDebuggerConnected()Z".equals(signature)) {
            return resolveDebugIsDebuggerConnected(vm);
        }
        if ("android/os/Debug->waitingForDebugger()Z".equals(signature)) {
            Boolean waiting = tryResolveDebugWaitingForDebugger(vm);
            if (waiting != null) {
                return waiting.booleanValue();
            }
        }
        if ("android/os/Debug->isDebuggerTracing()Z".equals(signature)) {
            Boolean tracing = tryResolveDebugIsDebuggerTracing(vm);
            if (tracing != null) {
                return tracing.booleanValue();
            }
        }
        Boolean selinux = tryResolveAndroidSelinuxBoolean(vm, signature);
        if (selinux != null) {
            return selinux.booleanValue();
        }
        Boolean userAMonkey = tryResolveActivityManagerIsUserAMonkey(vm, signature);
        if (userAMonkey != null) {
            return userAMonkey.booleanValue();
        }
        Boolean userTestHarness = tryResolveActivityManagerIsRunningInUserTestHarness(vm, signature);
        if (userTestHarness != null) {
            return userTestHarness.booleanValue();
        }
        FileSystemExternalStorageBooleanResult externalStorageBool =
                tryFilesystemExternalStorageStaticBoolean(vm, signature, varArg);
        if (externalStorageBool.handled) {
            return externalStorageBool.value;
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public boolean callStaticBooleanMethodV(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VaList vaList) {
        return callStaticBooleanMethodV(vm, dvmClass, dvmMethod.getSignature(), vaList);
    }


    @Override
    public boolean callStaticBooleanMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        log.info("callStaticBooleanMethodV [Unidbg]: {}", signature);
        if ("android/os/Debug->isDebuggerConnected()Z".equals(signature)) {
            return resolveDebugIsDebuggerConnected(vm);
        }
        if ("android/os/Debug->waitingForDebugger()Z".equals(signature)) {
            Boolean waiting = tryResolveDebugWaitingForDebugger(vm);
            if (waiting != null) {
                return waiting.booleanValue();
            }
        }
        if ("android/os/Debug->isDebuggerTracing()Z".equals(signature)) {
            Boolean tracing = tryResolveDebugIsDebuggerTracing(vm);
            if (tracing != null) {
                return tracing.booleanValue();
            }
        }
        Boolean selinux = tryResolveAndroidSelinuxBoolean(vm, signature);
        if (selinux != null) {
            return selinux.booleanValue();
        }
        Boolean userAMonkey = tryResolveActivityManagerIsUserAMonkey(vm, signature);
        if (userAMonkey != null) {
            return userAMonkey.booleanValue();
        }
        Boolean userTestHarness = tryResolveActivityManagerIsRunningInUserTestHarness(vm, signature);
        if (userTestHarness != null) {
            return userTestHarness.booleanValue();
        }
        FileSystemExternalStorageBooleanResult externalStorageBool =
                tryFilesystemExternalStorageStaticBoolean(vm, signature, vaList);
        if (externalStorageBool.handled) {
            return externalStorageBool.value;
        }
        switch (signature) {
            // TextUtils.isEmpty: 判断 str==null || str.length()==0
            case "android/text/TextUtils->isEmpty(Ljava/lang/CharSequence;)Z": {
                DvmObject<?> obj = vaList.getObjectArg(0);
                if (obj == null) return true;
                Object value = obj.getValue();
                if (value == null) return true;
                return value.toString().isEmpty();
            }
        }
        throw new UnsupportedOperationException(signature);
    }

    /**
     * {@code Debug.isDebuggerConnected()}: when {@code android.securitySignals} is present, return
     * configured {@code debuggerConnected} and emit sidecar; when the node is absent, keep the
     * historical hard-coded {@code false} (no event, not UOE).
     */
    private static boolean resolveDebugIsDebuggerConnected(BaseVM vm) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config != null && config.isAndroidSecuritySignalsConfigured()) {
            boolean value = config.getAndroidSecuritySignalsConfig().isDebuggerConnected();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_security",
                    "Debug.isDebuggerConnected",
                    "result=" + value,
                    "json-config", "读取配置的调试器连接状态");
            return value;
        }
        return false;
    }

    /**
     * {@code Debug.waitingForDebugger()}: when {@code android.securitySignals} is present, return
     * configured {@code waitingForDebugger} and emit sidecar; when the node is absent, return
     * {@code null} so the caller keeps the existing UOE path (no event).
     */
    private static Boolean tryResolveDebugWaitingForDebugger(BaseVM vm) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config != null && config.isAndroidSecuritySignalsConfigured()) {
            boolean value = config.getAndroidSecuritySignalsConfig().isWaitingForDebugger();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_security",
                    "Debug.waitingForDebugger",
                    "result=" + value,
                    "json-config", "读取配置的等待调试器状态");
            return Boolean.valueOf(value);
        }
        return null;
    }

    /**
     * {@code Debug.isDebuggerTracing()}: when {@code android.securitySignals} is present, return
     * configured {@code debuggerTracing} and emit sidecar; when the node is absent, return
     * {@code null} so the caller keeps the existing UOE path (no event).
     */
    private static Boolean tryResolveDebugIsDebuggerTracing(BaseVM vm) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config != null && config.isAndroidSecuritySignalsConfigured()) {
            boolean value = config.getAndroidSecuritySignalsConfig().isDebuggerTracing();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_security",
                    "Debug.isDebuggerTracing",
                    "result=" + value,
                    "json-config", "读取配置的调试器跟踪状态");
            return Boolean.valueOf(value);
        }
        return null;
    }

    /**
     * {@code SELinux.isSELinuxEnabled()} / {@code isSELinuxEnforced()}: when
     * {@code android.securitySignals} is present, return configured
     * {@code selinuxEnabled} / {@code selinuxEnforced} and emit sidecar; when the node is absent,
     * return {@code null} so the caller keeps the existing UOE path (no event).
     */
    private static Boolean tryResolveAndroidSelinuxBoolean(BaseVM vm, String signature) {
        final String field;
        final String api;
        final String note;
        if ("android/os/SELinux->isSELinuxEnabled()Z".equals(signature)) {
            field = "selinuxEnabled";
            api = "SELinux.isSELinuxEnabled";
            note = "读取配置的 SELinux 启用状态";
        } else if ("android/os/SELinux->isSELinuxEnforced()Z".equals(signature)) {
            field = "selinuxEnforced";
            api = "SELinux.isSELinuxEnforced";
            note = "读取配置的 SELinux 强制模式状态";
        } else {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidSecuritySignalsConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.AndroidSecuritySignalsConfig signals =
                config.getAndroidSecuritySignalsConfig();
        boolean value = "selinuxEnabled".equals(field)
                ? signals.isSelinuxEnabled()
                : signals.isSelinuxEnforced();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_security",
                api,
                "field=" + field + ",result=" + value,
                "json-config", note);
        return Boolean.valueOf(value);
    }

    /**
     * {@code ActivityManager.isUserAMonkey()}: when {@code android.securitySignals} is present,
     * return configured {@code userAMonkey} and emit sidecar; when the node is absent or the
     * signature does not match, return {@code null} so the caller keeps UOE (no event).
     * Independent of debugger/SELinux fields; does not implement test harness, ActivityManager
     * instance services, process lists, or monkey behavior.
     */
    private static Boolean tryResolveActivityManagerIsUserAMonkey(BaseVM vm, String signature) {
        if (!"android/app/ActivityManager->isUserAMonkey()Z".equals(signature)) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidSecuritySignalsConfigured()) {
            return null;
        }
        boolean value = config.getAndroidSecuritySignalsConfig().isUserAMonkey();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_security",
                "ActivityManager.isUserAMonkey",
                "field=userAMonkey,result=" + value,
                "json-config", "读取配置的是否为 Monkey 用户");
        return Boolean.valueOf(value);
    }

    /**
     * {@code ActivityManager.isRunningInUserTestHarness()}: when {@code android.securitySignals}
     * is present, return configured {@code userTestHarness} and emit sidecar; when the node is
     * absent or the signature does not match, return {@code null} so the caller keeps UOE (no
     * event). Independent of {@code userAMonkey}/debugger/SELinux; does not implement deprecated
     * {@code isRunningInTestHarness}, ActivityManager instance APIs, or device-farm behavior.
     */
    private static Boolean tryResolveActivityManagerIsRunningInUserTestHarness(BaseVM vm,
                                                                               String signature) {
        if (!"android/app/ActivityManager->isRunningInUserTestHarness()Z".equals(signature)) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidSecuritySignalsConfigured()) {
            return null;
        }
        boolean value = config.getAndroidSecuritySignalsConfig().isUserTestHarness();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_security",
                "ActivityManager.isRunningInUserTestHarness",
                "field=userTestHarness,result=" + value,
                "json-config", "读取配置的是否运行在用户测试框架中");
        return Boolean.valueOf(value);
    }

    @Override
    public int callStaticIntMethod(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VarArg varArg) {
        return callStaticIntMethod(vm, dvmClass, dvmMethod.getSignature(), varArg);
    }


    @Override
    public int callStaticIntMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        log.info("callStaticIntMethod [Unidbg]: {}", signature);
        AndroidSettingsGetIntResult settingsResult = tryAndroidSettingsGetInt(vm, signature, varArg);
        if (settingsResult.handled) {
            return settingsResult.value;
        }
        AndroidUserHandleIntResult userHandleStaticInt = tryAndroidUserHandleStaticInt(vm, signature);
        if (userHandleStaticInt.handled) {
            return userHandleStaticInt.value;
        }
        AndroidCamerasIntResult camerasInt = tryAndroidCamerasStaticInt(vm, signature);
        if (camerasInt.handled) {
            return camerasInt.value;
        }
        Integer processIdentity = tryAndroidProcessIdentityStaticInt(vm, signature);
        if (processIdentity != null) {
            return processIdentity.intValue();
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public int callStaticIntMethodV(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VaList vaList) {
        return callStaticIntMethodV(vm, dvmClass, dvmMethod.getSignature(), vaList);
    }

    @Override
    public int callStaticIntMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        log.info("callStaticIntMethodV [Unidbg]: {}", signature);
        AndroidSettingsGetIntResult settingsResult = tryAndroidSettingsGetInt(vm, signature, vaList);
        if (settingsResult.handled) {
            return settingsResult.value;
        }
        AndroidUserHandleIntResult userHandleStaticInt = tryAndroidUserHandleStaticInt(vm, signature);
        if (userHandleStaticInt.handled) {
            return userHandleStaticInt.value;
        }
        AndroidCamerasIntResult camerasInt = tryAndroidCamerasStaticInt(vm, signature);
        if (camerasInt.handled) {
            return camerasInt.value;
        }
        Integer processIdentity = tryAndroidProcessIdentityStaticInt(vm, signature);
        if (processIdentity != null) {
            return processIdentity.intValue();
        }
        throw new UnsupportedOperationException(signature);
    }

    /**
     * Shared Process identity for exact static {@code myPid}/{@code myTid}/{@code myUid}.
     * VarArg and VaList must behave identically. No sidecar; no host pid/uid reads.
     * {@code myPid} uses {@code emulator.getPid()} (already reflects {@code process.pid}).
     */
    private static Integer tryAndroidProcessIdentityStaticInt(BaseVM vm, String signature) {
        switch (signature) {
            case "android/os/Process->myPid()I":
                return vm.getEmulator().getPid();
            case "android/os/Process->myTid()I": {
                TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
                int pid = vm.getEmulator().getPid();
                return Integer.valueOf(config == null ? pid : config.getTid(pid));
            }
            case "android/os/Process->myUid()I": {
                TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
                return Integer.valueOf(config == null ? 0 : config.getUid(0));
            }
            default:
                return null;
        }
    }

    @Override
    public long callLongMethod(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VarArg varArg) {
        return callLongMethod(vm, dvmObject, dvmMethod.getSignature(), varArg);
    }

    @Override
    public long callLongMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        log.info("callLongMethod [Unidbg]: {}", signature);
        AndroidUserManagerLongResult serialResult =
                tryAndroidUserManagerGetSerialNumberForUser(vm, signature, varArg);
        if (serialResult.handled) {
            return serialResult.value;
        }
        AndroidBatteryLongResult batteryLongResult = tryAndroidBatteryLong(vm, signature, varArg);
        if (batteryLongResult.handled) {
            return batteryLongResult.value;
        }
        AndroidSecurityStateLongResult securityStateLong =
                tryAndroidSecurityStateLong(vm, signature, varArg);
        if (securityStateLong.handled) {
            return securityStateLong.value;
        }
        AndroidLocationLongResult locationLong =
                tryAndroidLocationLong(vm, dvmObject, signature);
        if (locationLong.handled) {
            return locationLong.value;
        }
        AndroidClipboardLongResult clipboardLong =
                tryAndroidClipboardLongMethod(vm, dvmObject, signature);
        if (clipboardLong.handled) {
            return clipboardLong.value;
        }
        AndroidRuntimeLongResult runtimeMaxMemory =
                tryAndroidRuntimeMaxMemory(vm, dvmObject, signature);
        if (runtimeMaxMemory.handled) {
            return runtimeMaxMemory.value;
        }
        AndroidRuntimeLongResult runtimeTotalMemory =
                tryAndroidRuntimeTotalMemory(vm, dvmObject, signature);
        if (runtimeTotalMemory.handled) {
            return runtimeTotalMemory.value;
        }
        AndroidRuntimeLongResult runtimeFreeMemory =
                tryAndroidRuntimeFreeMemory(vm, dvmObject, signature);
        if (runtimeFreeMemory.handled) {
            return runtimeFreeMemory.value;
        }
        if ("java/lang/Long->longValue()J".equals(signature)) {
            DvmLong val = (DvmLong) dvmObject;
            return val.value;
        }

        throw new UnsupportedOperationException(signature);
    }

    @Override
    public long callLongMethodV(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VaList vaList) {
        return callLongMethodV(vm, dvmObject, dvmMethod.getSignature(), vaList);
    }


    @Override
    public long callLongMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        log.info("callLongMethodV [Unidbg]: {}", signature);
        AndroidUserManagerLongResult serialResult =
                tryAndroidUserManagerGetSerialNumberForUser(vm, signature, vaList);
        if (serialResult.handled) {
            return serialResult.value;
        }
        AndroidBatteryLongResult batteryLongResult = tryAndroidBatteryLong(vm, signature, vaList);
        if (batteryLongResult.handled) {
            return batteryLongResult.value;
        }
        AndroidSecurityStateLongResult securityStateLong =
                tryAndroidSecurityStateLong(vm, signature, vaList);
        if (securityStateLong.handled) {
            return securityStateLong.value;
        }
        AndroidLocationLongResult locationLong =
                tryAndroidLocationLong(vm, dvmObject, signature);
        if (locationLong.handled) {
            return locationLong.value;
        }
        AndroidClipboardLongResult clipboardLong =
                tryAndroidClipboardLongMethod(vm, dvmObject, signature);
        if (clipboardLong.handled) {
            return clipboardLong.value;
        }
        AndroidRuntimeLongResult runtimeMaxMemory =
                tryAndroidRuntimeMaxMemory(vm, dvmObject, signature);
        if (runtimeMaxMemory.handled) {
            return runtimeMaxMemory.value;
        }
        AndroidRuntimeLongResult runtimeTotalMemory =
                tryAndroidRuntimeTotalMemory(vm, dvmObject, signature);
        if (runtimeTotalMemory.handled) {
            return runtimeTotalMemory.value;
        }
        AndroidRuntimeLongResult runtimeFreeMemory =
                tryAndroidRuntimeFreeMemory(vm, dvmObject, signature);
        if (runtimeFreeMemory.handled) {
            return runtimeFreeMemory.value;
        }
        switch (signature) {
            // Date.getTime(): 返回时间戳毫秒值
            // 变化点: 每次调用返回当前系统时间，结果不固定
            // 如果需要固定结果，可在子类覆盖返回固定时间戳
            case "java/util/Date->getTime()J": {
                java.util.Date date = (java.util.Date) dvmObject.getValue();
                long time = date.getTime();
                log.info("[随机点] Date.getTime() 时间戳: {}", time);
                return time;
            }
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public char callCharMethodV(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VaList vaList) {
        return callCharMethodV(vm, dvmObject, dvmMethod.getSignature(), vaList);
    }

    @Override
    public char callCharMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        log.info("callCharMethodV [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }


    @Override
    public float callFloatMethodV(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VaList vaList) {
        return callFloatMethodV(vm, dvmObject, dvmMethod.getSignature(), vaList);
    }

    @Override
    public float callFloatMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        log.info("callFloatMethodV [Unidbg]: {}", signature);
        AndroidDisplayFloatFieldResult displayFloat = tryAndroidDisplayFloatMethod(vm, dvmObject, signature);
        if (displayFloat.handled) {
            return displayFloat.value;
        }
        AndroidThermalFloatResult thermalFloat = tryAndroidThermalFloat(vm, signature, vaList);
        if (thermalFloat.handled) {
            return thermalFloat.value;
        }
        AndroidLocationFloatResult locationFloat =
                tryAndroidLocationFloat(vm, dvmObject, signature);
        if (locationFloat.handled) {
            return locationFloat.value;
        }
        AndroidSensorFloatResult sensorFloat = tryAndroidSensorFloat(vm, dvmObject, signature);
        if (sensorFloat.handled) {
            return sensorFloat.value;
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public DvmObject<?> callObjectMethodV(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VaList vaList) {
        return callObjectMethodV(vm, dvmObject, dvmMethod.getSignature(), vaList);
    }

    @Override
    public DvmObject<?> callObjectMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        log.info("callObjectMethodV [Unidbg]: {}", signature);
        AndroidTelephonyIdentifierResult telephonyResult = tryAndroidTelephonyIdentifier(vm, signature, vaList);
        if (telephonyResult.handled) {
            return telephonyResult.value;
        }
        AndroidWifiObjectResult wifiResult = tryAndroidWifiObject(vm, signature);
        if (wifiResult.handled) {
            return wifiResult.value;
        }
        NetworkBluetoothObjectResult bluetoothObjectResult =
                tryNetworkBluetoothObjectMethod(vm, dvmObject, signature);
        if (bluetoothObjectResult.handled) {
            return bluetoothObjectResult.value;
        }
        AndroidDhcpObjectResult dhcpResult = tryAndroidDhcpObject(vm, signature);
        if (dhcpResult.handled) {
            return dhcpResult.value;
        }
        AndroidNetworkLinkObjectResult linkResult = tryAndroidNetworkLinkObject(vm, dvmObject, signature);
        if (linkResult.handled) {
            return linkResult.value;
        }
        AndroidPackageObjectResult packageResult = tryAndroidPackageGetPackageInfo(vm, signature, vaList);
        if (packageResult.handled) {
            return packageResult.value;
        }
        AndroidPackageObjectResult applicationInfoResult = tryAndroidPackageGetApplicationInfo(vm, signature, vaList);
        if (applicationInfoResult.handled) {
            return applicationInfoResult.value;
        }
        AndroidPackageObjectResult installedListResult = tryAndroidPackageGetInstalledList(vm, signature, vaList);
        if (installedListResult.handled) {
            return installedListResult.value;
        }
        AndroidPackageObjectResult installerResult = tryAndroidPackageGetInstallerPackageName(vm, signature, vaList);
        if (installerResult.handled) {
            return installerResult.value;
        }
        AndroidPackageObjectResult installSourceInfoResult = tryAndroidPackageGetInstallSourceInfo(vm, signature, vaList);
        if (installSourceInfoResult.handled) {
            return installSourceInfoResult.value;
        }
        AndroidPackageObjectResult installSourceMethodResult = tryAndroidPackageInstallSourceInfoMethod(vm, dvmObject, signature);
        if (installSourceMethodResult.handled) {
            return installSourceMethodResult.value;
        }
        AndroidPackageObjectResult signingInfoMethodResult =
                tryAndroidPackageSigningInfoMethod(vm, dvmObject, signature);
        if (signingInfoMethodResult.handled) {
            return signingInfoMethodResult.value;
        }
        AndroidPackageObjectResult uidMapResult = tryAndroidPackageUidMapping(vm, signature, vaList);
        if (uidMapResult.handled) {
            return uidMapResult.value;
        }
        AndroidPackageObjectResult availableFeaturesResult =
                tryAndroidFeatureGetSystemAvailableFeatures(vm, signature);
        if (availableFeaturesResult.handled) {
            return availableFeaturesResult.value;
        }
        AndroidPackageObjectResult teeKeySpecResult = tryAndroidTeeKeyFactoryGetKeySpec(vm, signature, vaList);
        if (teeKeySpecResult.handled) {
            return teeKeySpecResult.value;
        }
        AndroidPackageObjectResult teeKeyInfoMethodResult = tryAndroidTeeKeyInfoObjectMethod(vm, dvmObject, signature);
        if (teeKeyInfoMethodResult.handled) {
            return teeKeyInfoMethodResult.value;
        }
        AndroidPackageObjectResult teeKeyStoreGetKey = tryAndroidTeeKeyStoreGetKey(vm, signature, vaList);
        if (teeKeyStoreGetKey.handled) {
            return teeKeyStoreGetKey.value;
        }
        AndroidPackageObjectResult teeKeyEncoded = tryAndroidTeeKeyGetEncoded(vm, dvmObject, signature);
        if (teeKeyEncoded.handled) {
            return teeKeyEncoded.value;
        }
        AndroidPackageObjectResult teeKeyAlgoFormat =
                tryAndroidTeeKeyGetAlgorithmOrFormat(vm, dvmObject, signature);
        if (teeKeyAlgoFormat.handled) {
            return teeKeyAlgoFormat.value;
        }
        AndroidLocaleObjectResult localeObjectResult = tryAndroidLocaleObjectMethod(vm, dvmObject, signature);
        if (localeObjectResult.handled) {
            return localeObjectResult.value;
        }
        AndroidAdvertisingIdObjectResult advertisingIdObjectResult =
                tryAndroidAdvertisingIdObjectMethod(vm, dvmObject, signature);
        if (advertisingIdObjectResult.handled) {
            return advertisingIdObjectResult.value;
        }
        AndroidAppSetIdObjectResult appSetIdObjectResult =
                tryAndroidAppSetIdObjectMethod(vm, dvmObject, signature);
        if (appSetIdObjectResult.handled) {
            return appSetIdObjectResult.value;
        }
        AndroidAccountObjectResult accountsObjectResult =
                tryAndroidAccountManagerObjectMethod(vm, dvmObject, signature, vaList);
        if (accountsObjectResult.handled) {
            return accountsObjectResult.value;
        }
        AndroidInputMethodObjectResult inputMethodObjectResult =
                tryAndroidInputMethodObjectMethod(vm, dvmObject, signature);
        if (inputMethodObjectResult.handled) {
            return inputMethodObjectResult.value;
        }
        AndroidAccessibilityServiceObjectResult a11yServiceObjectResult =
                tryAndroidAccessibilityServiceObjectMethod(vm, dvmObject, signature);
        if (a11yServiceObjectResult.handled) {
            return a11yServiceObjectResult.value;
        }
        AndroidClipboardObjectResult clipboardObjectResult =
                tryAndroidClipboardObjectMethod(vm, dvmObject, signature, vaList);
        if (clipboardObjectResult.handled) {
            return clipboardObjectResult.value;
        }
        AndroidDisplayObjectResult displayObjectResult = tryAndroidDisplayObjectMethod(vm, dvmObject, signature, vaList);
        if (displayObjectResult.handled) {
            return displayObjectResult.value;
        }
        AndroidConfigurationObjectResult configurationObjectResult =
                tryAndroidConfigurationObjectMethod(vm, signature);
        if (configurationObjectResult.handled) {
            return configurationObjectResult.value;
        }
        AndroidLocationObjectResult locationObjectResult =
                tryAndroidLocationObjectMethod(vm, dvmObject, signature, vaList);
        if (locationObjectResult.handled) {
            return locationObjectResult.value;
        }
        AndroidSensorObjectResult sensorObjectResult =
                tryAndroidSensorObjectMethod(vm, dvmObject, signature, vaList);
        if (sensorObjectResult.handled) {
            return sensorObjectResult.value;
        }
        AndroidAudioObjectResult audioObjectResult =
                tryAndroidAudioGetProperty(vm, dvmObject, signature, vaList);
        if (audioObjectResult.handled) {
            return audioObjectResult.value;
        }
        NetworkInterfaceObjectResult networkInterfaceObjectResult =
                tryNetworkInterfaceObjectMethod(vm, dvmObject, signature);
        if (networkInterfaceObjectResult.handled) {
            return networkInterfaceObjectResult.value;
        }
        AndroidUserHandleObjectResult userHandleForSerialV =
                tryAndroidUserManagerGetUserHandleForSerialNumber(vm, signature, vaList);
        if (userHandleForSerialV.handled) {
            return userHandleForSerialV.value;
        }
        AndroidDataDirObjectResult dataDirDirsV = tryAndroidDataDirContextDirs(vm, signature, vaList);
        if (dataDirDirsV.handled) {
            return dataDirDirsV.value;
        }
        FileSystemExternalStorageObjectResult externalAppDirsV =
                tryFilesystemExternalStorageContextAppExternalDirs(vm, signature, vaList);
        if (externalAppDirsV.handled) {
            return externalAppDirsV.value;
        }
        switch (signature) {
            // ==================== Android Context/Application 方法 ====================
            // 获取 AssetManager，主要是为了读取 assets 目录下的资源文件
            case "android/app/Application->getAssets()Landroid/content/res/AssetManager;":
                return new AssetManager(vm, signature);
            
            // 获取 ClassLoader，用于动态加载类、反射调用
            case "android/app/Application->getClassLoader()Ljava/lang/ClassLoader;":
            case "java/lang/Class->getClassLoader()Ljava/lang/ClassLoader;":
                return new ClassLoader(vm, signature);
            
            // 获取 ContentResolver，用于访问 ContentProvider 数据
            case "android/app/Application->getContentResolver()Landroid/content/ContentResolver;":
                return vm.resolveClass("android/content/ContentResolver").newObject(signature);
            
            // ==================== Java 集合类方法 ====================
            case "java/util/ArrayList->get(I)Ljava/lang/Object;": {
                int index = vaList.getIntArg(0);
                ArrayListObject arrayList = (ArrayListObject) dvmObject;
                return arrayList.getValue().get(index);
            }
            
            // ==================== Android 系统服务 ====================
            // getSystemService: 根据服务名获取系统服务
            // 常用服务: TELEPHONY_SERVICE(phone), WIFI_SERVICE(wifi), CONNECTIVITY_SERVICE(connectivity)
            // 这里unidbg的SystemService帮我们做了占位处理，如下：
            // case ACTIVITY_SERVICE:
            //     return vm.resolveClass("android/os/BinderProxy"); // android/app/ActivityManager
            case "android/app/Application->getSystemService(Ljava/lang/String;)Ljava/lang/Object;":
            case "android/content/Context->getSystemService(Ljava/lang/String;)Ljava/lang/Object;": {
                StringObject serviceName = vaList.getObjectArg(0);
                assert serviceName != null;
                return new SystemService(vm, serviceName.getValue());
            }
            // Typed getSystemService(Class): only BluetoothManager / WifiManager /
            // ConnectivityManager / LocationManager / AudioManager / InputMethodManager /
            // SensorManager / PowerManager / BatteryManager / UiModeManager /
            // KeyguardManager / WindowManager / DisplayManager / ClipboardManager /
            // AccessibilityManager / UserManager / TelephonyManager → same
            // SystemService marker as the string lookup. No network.bluetooth /
            // network.wifi / network.links / android.location / android.audio /
            // android.inputMethods / android.sensors / android.power / android.thermal /
            // android.battery / android.configuration / android.securityState /
            // android.display / android.clipboard / android.accessibility /
            // android.userState / android.telephony
            // required here; getters remain
            // config-gated. Lookup itself emits no sidecar.
            case "android/app/Application->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;":
            case "android/content/Context->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;": {
                return resolveLimitedTypedSystemService(vm, signature, vaList.getObjectArg(0));
            }
            
            // ==================== Java String 方法 ====================
            // String.toString(): 返回自身，Java 规范要求
            case "java/lang/String->toString()Ljava/lang/String;":
                return dvmObject;
            
            // Class.getName(): 返回类的全限定名，如 "java.lang.String"
            case "java/lang/Class->getName()Ljava/lang/String;":
                return new StringObject(vm, ((DvmClass) dvmObject).getName());
            
            // getEnabledAccessibilityServiceList / getInstalledAccessibilityServiceList:
            // handled above via tryAndroidAccessibilityServiceObjectMethod (configured services
            // or legacy empty enabled list).
            
            // ==================== Java 枚举/迭代器 ====================
            // Enumeration.nextElement(): 返回下一个元素
            case "java/util/Enumeration->nextElement()Ljava/lang/Object;":
                return ((Enumeration) dvmObject).nextElement();
            
            // ==================== Java Locale 方法 ====================
            // Locale.getLanguage(): 返回语言代码，如 "zh", "en"  语言设置可能暴露用户偏好，但通常不用于设备指纹
            case "java/util/Locale->getLanguage()Ljava/lang/String;": {
                Locale locale = (Locale) dvmObject.getValue();
                log.info("[指纹信息] 这里在获取指纹信息, locale的语言代码: {}",locale.getLanguage());
                return new StringObject(vm, locale.getLanguage());
            }
            // Locale.getCountry(): 返回国家代码，如 "CN", "US"
            case "java/util/Locale->getCountry()Ljava/lang/String;":
                Locale locale = (Locale) dvmObject.getValue();
                log.info("[指纹信息] 这里在获取指纹信息, locale的国家代码: {}",locale.getCountry());
                return new StringObject(vm, locale.getCountry());
            
            // ==================== Android Binder/ServiceManager ====================
            // IServiceManager.getService: 获取系统服务的 Binder 对象
            // 指纹风险: 高！通过 Binder 可以获取各种系统信息
            case "android/os/IServiceManager->getService(Ljava/lang/String;)Landroid/os/IBinder;": {
                ServiceManager serviceManager = (ServiceManager) dvmObject;
                StringObject serviceName = vaList.getObjectArg(0);
                assert serviceName != null;
                log.info("[指纹信息] 这里在获取指纹信息, 系统服务的binder对象: {}",serviceName.getValue());
                return serviceManager.getService(vm, serviceName.getValue());
            }
            
            // ==================== Java File 方法 ====================
            // File.getAbsolutePath(): 返回文件的绝对路径
            // 指纹风险: 低。路径本身不含设备信息，但可能暴露目录结构
            case "java/io/File->getAbsolutePath()Ljava/lang/String;":
                File file = (File) dvmObject.getValue();
                return new StringObject(vm, file.getAbsolutePath());
            
            // ==================== Android PackageManager ====================
            // getPackageManager(): 获取包管理器，用于查询应用信息
            case "android/app/Application->getPackageManager()Landroid/content/pm/PackageManager;":
            case "android/content/ContextWrapper->getPackageManager()Landroid/content/pm/PackageManager;":
            case "android/content/Context->getPackageManager()Landroid/content/pm/PackageManager;":
                return vm.resolveClass("android/content/pm/PackageManager").newObject(null);
            
            // getApplicationInfo(): 获取应用信息，包含 APK 路径、数据目录等
            case "android/app/Application->getApplicationInfo()Landroid/content/pm/ApplicationInfo;":
            case "android/content/ContextWrapper->getApplicationInfo()Landroid/content/pm/ApplicationInfo;":
            case "android/content/Context->getApplicationInfo()Landroid/content/pm/ApplicationInfo;":
                return new ApplicationInfo(vm);
            
            // getPackageInfo(String, int): 获取包信息，flags 决定返回哪些信息
            // 常用 flags: GET_SIGNATURES=0x40(获取签名), GET_META_DATA=0x80(获取meta-data)
            case "android/content/pm/PackageManager->getPackageInfo(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;": {
                StringObject packageName = vaList.getObjectArg(0);
                assert packageName != null;
                int flags = vaList.getIntArg(1);
                log.info("callObjectMethodV getPackageInfo packageName={}, flags=0x{}", packageName.getValue(), Integer.toHexString(flags));
                if (flags == 0x40){
                    log.info("注意，这里准备获取apk签名！");
                }else if(flags == 0x80){
                    log.info("注意，这里准备获取apk的meta-data！");
                }
                return new PackageInfo(vm, packageName.value, flags);
            }
            
            // getPackageName(): 返回当前应用的包名
            case "android/app/Application->getPackageName()Ljava/lang/String;":
            case "android/content/ContextWrapper->getPackageName()Ljava/lang/String;":
            case "android/content/Context->getPackageName()Ljava/lang/String;": {
                String packageName = vm.getPackageName();
                if (packageName != null) {
                    return new StringObject(vm, packageName);
                }
                break;
            }
            case "android/content/pm/Signature->toByteArray()[B":
                if (dvmObject instanceof Signature) {
                    Signature sig = (Signature) dvmObject;
                    return new ByteArray(vm, sig.toByteArray());
                }
                break;
            case "android/content/pm/Signature->toCharsString()Ljava/lang/String;":
                if (dvmObject instanceof Signature) {
                    Signature sig = (Signature) dvmObject;
                    return new StringObject(vm, sig.toCharsString());
                }
                break;
            case "java/lang/String->getBytes()[B": {
                String str = (String) dvmObject.getValue();
                return new ByteArray(vm, str.getBytes());
            }
            case "java/lang/String->getBytes(Ljava/lang/String;)[B":
                String str = (String) dvmObject.getValue();
                StringObject charsetName = vaList.getObjectArg(0);
                assert charsetName != null;
                try {
                    return new ByteArray(vm, str.getBytes(charsetName.value));
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalStateException(e);
                }
            case "java/security/cert/CertificateFactory->generateCertificate(Ljava/io/InputStream;)Ljava/security/cert/Certificate;":
                CertificateFactory factory = (CertificateFactory) dvmObject.value;
                DvmObject<?> stream = vaList.getObjectArg(0);
                assert stream != null;
                InputStream inputStream = (InputStream) stream.value;
                try {
                    log.info("这里可能和签名有关!");
                    return vm.resolveClass("java/security/cert/Certificate").newObject(factory.generateCertificate(inputStream));
                } catch (CertificateException e) {
                    throw new IllegalStateException(e);
                }
            case "java/security/cert/Certificate->getEncoded()[B": {
                Certificate certificate = (Certificate) dvmObject.value;
                try {
                    log.info("这里可能和签名有关!");
                    return new ByteArray(vm, certificate.getEncoded());
                } catch (CertificateEncodingException e) {
                    throw new IllegalStateException(e);
                }
            }
            case "java/security/MessageDigest->digest([B)[B": {
                MessageDigest messageDigest = (MessageDigest) dvmObject.value;
                ByteArray array = vaList.getObjectArg(0);
                assert array != null;
                byte[] digest_hash = (byte[]) messageDigest.digest(array.value);
                log.info("监听到计算hash, 最终字节数组为: {} -> 转十六进制: {}", digest_hash, bytesToHex(digest_hash));
                return new ByteArray(vm, digest_hash);
            }
            case "java/util/ArrayList->remove(I)Ljava/lang/Object;": {
                int index = vaList.getIntArg(0);
                ArrayListObject list = (ArrayListObject) dvmObject;
                return list.value.remove(index);
            }
            case "java/util/List->get(I)Ljava/lang/Object;":
                List<?> list = (List<?>) dvmObject.getValue();
                return (DvmObject<?>) list.get(vaList.getIntArg(0));
            case "java/util/Map->entrySet()Ljava/util/Set;":
                Map<?, ?> map = (Map<?, ?>) dvmObject.getValue();
                return vm.resolveClass("java/util/Set").newObject(map.entrySet());
            case "java/util/Set->iterator()Ljava/util/Iterator;":
                Set<?> set = (Set<?>) dvmObject.getValue();
                return vm.resolveClass("java/util/Iterator").newObject(set.iterator());
            case "java/util/Iterator->next()Ljava/lang/Object;": {
                Iterator<?> it = (Iterator<?>) dvmObject.getValue();
                return vm.resolveClass("java/util/Map$Entry").newObject(it.next());
            }
            case "java/util/Map$Entry->getKey()Ljava/lang/Object;": {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) dvmObject.getValue();
                Object key = entry.getKey();
                return ProxyDvmObject.createObject(vm, key);
            }
            case "java/util/Map$Entry->getValue()Ljava/lang/Object;": {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) dvmObject.getValue();
                Object value = entry.getValue();
                return ProxyDvmObject.createObject(vm, value);
            }
            case "java/util/UUID->toString()Ljava/lang/String;": {
                UUID uuid = (UUID) dvmObject.getValue();
                return new StringObject(vm, uuid.toString());
            }
            case "java/lang/CharSequence->toString()Ljava/lang/String;": {
                return new StringObject(vm, dvmObject.value.toString());
            }
            case "java/lang/String->toLowerCase()Ljava/lang/String;": {
                return new StringObject(vm, dvmObject.value.toString().toLowerCase());
            }
            case "android/content/pm/PackageManager->getApplicationInfo(Ljava/lang/String;I)Landroid/content/pm/ApplicationInfo;":
                StringObject packageName = vaList.getObjectArg(0);
                if (packageName.value.equals(vm.getPackageName())) {
                    return new ApplicationInfo(vm);
                } else {
                    throw new UnsupportedOperationException(signature);
                }
            case "java/lang/String->trim()Ljava/lang/String;": {
                StringObject stringObject = (StringObject) dvmObject;
                return new StringObject(vm, stringObject.value.trim());
            }
            case "java/util/Map->keySet()Ljava/util/Set;": {
                Map<?, ?> map_temp = (Map<?, ?>) dvmObject.getValue();
                return ProxyDvmObject.createObject(vm, map_temp.keySet());
            }
            case "java/util/Set->toArray()[Ljava/lang/Object;": {
                Set<?> set_temp = (Set<?>) dvmObject.getValue();
                return ProxyDvmObject.createObject(vm, set_temp.toArray());
            }
            case "java/util/Map->get(Ljava/lang/Object;)Ljava/lang/Object;": {
                Map<?, ?> mapGet = (Map<?, ?>) dvmObject.getValue();
                Object key = vaList.getObjectArg(0).getValue();
                return ProxyDvmObject.createObject(vm, mapGet.get(key));
            }
            case "java/lang/String->replaceAll(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;":
                String original_str = (String) dvmObject.getValue();
                String new_str = original_str.replaceAll(vaList.getObjectArg(0).toString(), vaList.getObjectArg(1).toString());
                return new StringObject(vm, new_str);
            case "android/app/ActivityThread->getApplication()Landroid/app/Application;":
                return vm.resolveClass("android/app/Application", vm.resolveClass("android/content/ContextWrapper", vm.resolveClass("android/content/Context"))).newObject(signature);
                // return vm.resolveClass("android/app/Activity", vm.resolveClass("android/content/ContextWrapper", vm.resolveClass("android/content/Context"))).newObject(signature); // 亦可
            // StringBuilder 系列
            case "java/lang/StringBuilder->toString()Ljava/lang/String;":
                return new StringObject(vm, dvmObject.getValue().toString());
            case "java/lang/StringBuilder->append(Ljava/lang/String;)Ljava/lang/StringBuilder;": {
                StringObject appendStr = vaList.getObjectArg(0);
                StringBuilder sb = (StringBuilder) dvmObject.getValue();
                sb.append(appendStr != null ? appendStr.getValue() : "null");
                return dvmObject;
            }
            case "java/lang/StringBuilder->append(I)Ljava/lang/StringBuilder;":
                ((StringBuilder) dvmObject.getValue()).append(vaList.getIntArg(0));
                return dvmObject;
            case "java/lang/StringBuilder->append(J)Ljava/lang/StringBuilder;":
                ((StringBuilder) dvmObject.getValue()).append(vaList.getLongArg(0));
                return dvmObject;
            case "java/lang/StringBuilder->append(C)Ljava/lang/StringBuilder;":
                ((StringBuilder) dvmObject.getValue()).append((char) vaList.getIntArg(0));
                return dvmObject;
            case "java/lang/StringBuilder->append(Ljava/lang/CharSequence;II)Ljava/lang/StringBuilder;": {
                CharSequence cs = (CharSequence) vaList.getObjectArg(0).getValue();
                int start = vaList.getIntArg(1);
                int end = vaList.getIntArg(2);
                ((StringBuilder) dvmObject.getValue()).append(cs, start, end);
                return dvmObject;
            }
            // String.substring 系列
            case "java/lang/String->substring(I)Ljava/lang/String;": {
                String subStr = dvmObject.getValue().toString();
                int beginIndex = vaList.getIntArg(0);
                return new StringObject(vm, subStr.substring(beginIndex));
            }
            case "java/lang/String->substring(II)Ljava/lang/String;": {
                String subStr = dvmObject.getValue().toString();
                int beginIdx = vaList.getIntArg(0);
                int endIdx = vaList.getIntArg(1);
                return new StringObject(vm, subStr.substring(beginIdx, endIdx));
            }
            // Context 桥接方法
            case "android/content/Context->getApplicationContext()Landroid/content/Context;":
                return vm.resolveClass("android/content/Context").newObject(null);
            case "android/content/Context->getResources()Landroid/content/res/Resources;":
                return vm.resolveClass("android/content/res/Resources").newObject(null);
            case "android/content/res/Resources->getConfiguration()Landroid/content/res/Configuration;":
                // only when android.configuration is absent (configured path handled above)
                return vm.resolveClass("android/content/res/Configuration").newObject(null);
            case "android/view/WindowManager->getDefaultDisplay()Landroid/view/Display;":
                // only when android.display is absent (configured path handled above)
                return vm.resolveClass("android/view/Display").newObject(null);
            case "java/lang/StringBuffer->append(Ljava/lang/String;)Ljava/lang/StringBuffer;":
                StringBuffer stringBuffer = (StringBuffer) dvmObject.getValue();
                DvmObject<?> dvmObject1 = vaList.getObjectArg(0);
                stringBuffer.append(dvmObject1.getValue().toString());
                return vm.resolveClass("java/lang/StringBuffer").newObject(stringBuffer);
            case "java/lang/Integer->toString()Ljava/lang/String;":
                return new StringObject(vm, ((Integer)dvmObject.getValue()).toString());
            case "java/lang/StringBuffer->toString()Ljava/lang/String;":
                return new StringObject(vm, ((StringBuffer)dvmObject.getValue()).toString());
            case "java/lang/Class->getSimpleName()Ljava/lang/String;":
                String className = ((DvmClass) dvmObject).getClassName();
                String[] name = className.split("/");
                return new StringObject(vm, name[name.length - 1]);
            case "android/content/ContextWrapper->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;":
                return vm.resolveClass("android/content/SharedPreferences").newObject(null);
            case "android/content/pm/Signature->toChars()[C":
                CertificateMeta certificateMeta = (CertificateMeta) dvmObject.getValue();
                byte[] bytes = certificateMeta.getData();
                char[] chars = new char[bytes.length];
                for (int i = 0; i < bytes.length; i++) {
                    chars[i] = (char) bytes[i];
                }
                log.info("这里在做签名校验");
                return new CharArray(vm, chars);
            case "android/content/Context->getAssets()Landroid/content/res/AssetManager;":
                return vm.resolveClass("android/content/res/AssetManager").newObject(signature);
        }

        throw new UnsupportedOperationException(signature);
    }

    @Override
    public DvmObject<?> callStaticObjectMethod(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VarArg varArg) {
        return callStaticObjectMethod(vm, dvmClass, dvmMethod.getSignature(), varArg);
    }


    @Override
    public DvmObject<?> callStaticObjectMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        log.info("callStaticObjectMethod [Unidbg]: {}", signature);
        if (signature.startsWith("android/opengl/")) {
            GraphicsStringResult graphicsResult = tryGraphicsStaticString(vm, signature, varArg);
            if (graphicsResult.handled) {
                return graphicsResult.value;
            }
        }
        AndroidSettingsGetStringResult identifiersAndroidId =
                tryAndroidIdentifiersAndroidIdSecureGetString(vm, signature, varArg);
        if (identifiersAndroidId.handled) {
            return identifiersAndroidId.value;
        }
        AndroidSettingsGetStringResult settingsResult = tryAndroidSettingsGetString(vm, signature, varArg);
        if (settingsResult.handled) {
            return settingsResult.value;
        }
        AndroidLocaleObjectResult localeStaticResult = tryAndroidLocaleStaticObject(vm, dvmClass, signature);
        if (localeStaticResult.handled) {
            return localeStaticResult.value;
        }
        AndroidAdvertisingIdObjectResult advertisingIdStaticResult =
                tryAndroidAdvertisingIdStaticObject(vm, signature);
        if (advertisingIdStaticResult.handled) {
            return advertisingIdStaticResult.value;
        }
        AndroidAppSetIdObjectResult appSetIdStaticResult = tryAndroidAppSetIdStaticObject(vm, signature);
        if (appSetIdStaticResult.handled) {
            return appSetIdStaticResult.value;
        }
        AndroidAccountObjectResult accountsStaticResult =
                tryAndroidAccountManagerStaticObject(vm, signature);
        if (accountsStaticResult.handled) {
            return accountsStaticResult.value;
        }
        NetworkBluetoothObjectResult bluetoothStaticResult =
                tryNetworkBluetoothStaticObject(vm, signature);
        if (bluetoothStaticResult.handled) {
            return bluetoothStaticResult.value;
        }
        AndroidUserHandleObjectResult userHandleStaticObject =
                tryAndroidUserHandleStaticObject(vm, dvmClass, signature);
        if (userHandleStaticObject.handled) {
            return userHandleStaticObject.value;
        }
        NetworkInterfaceObjectResult networkInterfaceStatic =
                tryNetworkInterfaceStaticObject(vm, signature, varArg);
        if (networkInterfaceStatic.handled) {
            return networkInterfaceStatic.value;
        }
        FileSystemExternalStorageObjectResult externalStorageObject =
                tryFilesystemExternalStorageStaticObject(vm, signature, varArg);
        if (externalStorageObject.handled) {
            return externalStorageObject.value;
        }
        FileSystemSystemDirectoriesObjectResult systemDirectoriesObject =
                tryFilesystemSystemDirectoriesStaticObject(vm, signature);
        if (systemDirectoriesObject.handled) {
            return systemDirectoriesObject.value;
        }
        DvmObject<?> buildGetSerial = tryAndroidBuildGetSerial(vm, signature);
        if (buildGetSerial != null) {
            return buildGetSerial;
        }
        DvmObject<?> buildGetRadioVersion = tryAndroidBuildGetRadioVersion(vm, signature);
        if (buildGetRadioVersion != null) {
            return buildGetRadioVersion;
        }
        AndroidRuntimeSystemPropertyResult runtimeProp =
                tryAndroidRuntimeSystemGetProperty(vm, signature, varArg);
        if (runtimeProp.handled) {
            return runtimeProp.value;
        }
        AndroidRuntimeSystemPropertyResult runtimeEnv =
                tryAndroidRuntimeSystemGetenv(vm, signature, varArg);
        if (runtimeEnv.handled) {
            return runtimeEnv.value;
        }
        AndroidRuntimeSystemPropertyResult runtimeGetRuntime =
                tryAndroidRuntimeGetRuntime(vm, signature);
        if (runtimeGetRuntime.handled) {
            return runtimeGetRuntime.value;
        }
        if ("android/app/ActivityThread->currentPackageName()Ljava/lang/String;".equals(signature)) {
            String packageName = vm.getPackageName();
            if (packageName != null) {
                return new StringObject(vm, packageName);
            }
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public DvmObject<?> callStaticObjectMethodV(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VaList vaList) {
        return callStaticObjectMethodV(vm, dvmClass, dvmMethod.getSignature(), vaList);
    }


    @Override
    public DvmObject<?> callStaticObjectMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        log.info("callStaticObjectMethodV [Unidbg]: {}", signature);
        if (signature.startsWith("android/opengl/")) {
            GraphicsStringResult graphicsResult = tryGraphicsStaticString(vm, signature, vaList);
            if (graphicsResult.handled) {
                return graphicsResult.value;
            }
        }
        AndroidSettingsGetStringResult identifiersAndroidId =
                tryAndroidIdentifiersAndroidIdSecureGetString(vm, signature, vaList);
        if (identifiersAndroidId.handled) {
            return identifiersAndroidId.value;
        }
        AndroidSettingsGetStringResult settingsResult = tryAndroidSettingsGetString(vm, signature, vaList);
        if (settingsResult.handled) {
            return settingsResult.value;
        }
        AndroidLocaleObjectResult localeStaticResult = tryAndroidLocaleStaticObject(vm, dvmClass, signature);
        if (localeStaticResult.handled) {
            return localeStaticResult.value;
        }
        AndroidAdvertisingIdObjectResult advertisingIdStaticResult =
                tryAndroidAdvertisingIdStaticObject(vm, signature);
        if (advertisingIdStaticResult.handled) {
            return advertisingIdStaticResult.value;
        }
        AndroidAppSetIdObjectResult appSetIdStaticResult = tryAndroidAppSetIdStaticObject(vm, signature);
        if (appSetIdStaticResult.handled) {
            return appSetIdStaticResult.value;
        }
        AndroidAccountObjectResult accountsStaticResult =
                tryAndroidAccountManagerStaticObject(vm, signature);
        if (accountsStaticResult.handled) {
            return accountsStaticResult.value;
        }
        NetworkBluetoothObjectResult bluetoothStaticResult =
                tryNetworkBluetoothStaticObject(vm, signature);
        if (bluetoothStaticResult.handled) {
            return bluetoothStaticResult.value;
        }
        AndroidUserHandleObjectResult userHandleStaticObject =
                tryAndroidUserHandleStaticObject(vm, dvmClass, signature);
        if (userHandleStaticObject.handled) {
            return userHandleStaticObject.value;
        }
        NetworkInterfaceObjectResult networkInterfaceStatic =
                tryNetworkInterfaceStaticObject(vm, signature, vaList);
        if (networkInterfaceStatic.handled) {
            return networkInterfaceStatic.value;
        }
        FileSystemExternalStorageObjectResult externalStorageObject =
                tryFilesystemExternalStorageStaticObject(vm, signature, vaList);
        if (externalStorageObject.handled) {
            return externalStorageObject.value;
        }
        FileSystemSystemDirectoriesObjectResult systemDirectoriesObject =
                tryFilesystemSystemDirectoriesStaticObject(vm, signature);
        if (systemDirectoriesObject.handled) {
            return systemDirectoriesObject.value;
        }
        DvmObject<?> buildGetSerial = tryAndroidBuildGetSerial(vm, signature);
        if (buildGetSerial != null) {
            return buildGetSerial;
        }
        DvmObject<?> buildGetRadioVersion = tryAndroidBuildGetRadioVersion(vm, signature);
        if (buildGetRadioVersion != null) {
            return buildGetRadioVersion;
        }
        AndroidRuntimeSystemPropertyResult runtimeProp =
                tryAndroidRuntimeSystemGetProperty(vm, signature, vaList);
        if (runtimeProp.handled) {
            return runtimeProp.value;
        }
        AndroidRuntimeSystemPropertyResult runtimeEnv =
                tryAndroidRuntimeSystemGetenv(vm, signature, vaList);
        if (runtimeEnv.handled) {
            return runtimeEnv.value;
        }
        AndroidRuntimeSystemPropertyResult runtimeGetRuntime =
                tryAndroidRuntimeGetRuntime(vm, signature);
        if (runtimeGetRuntime.handled) {
            return runtimeGetRuntime.value;
        }
        switch (signature) {
            // 获取 Binder 上下文对象，用于 IPC 通信
            // 注: signature 参数仅用于调试日志，传 null 也可以
            case "com/android/internal/os/BinderInternal->getContextObject()Landroid/os/IBinder;":
                return new Binder(vm, signature);
            case "android/app/ActivityThread->currentActivityThread()Landroid/app/ActivityThread;":
                return dvmClass.newObject(null);
            // 注: signature 参数仅用于调试日志，传 null 也可以
            case "android/app/ActivityThread->currentApplication()Landroid/app/Application;":
                return vm.resolveClass("android/app/Application", vm.resolveClass("android/content/ContextWrapper", vm.resolveClass("android/content/Context"))).newObject(signature);
            case "java/util/Locale->getDefault()Ljava/util/Locale;":
                // only when android.locale is absent (configured path handled above)
                return dvmClass.newObject(Locale.getDefault());
            // 获取 ServiceManager，用于获取系统服务
            // 注: signature 参数仅用于调试日志，传 null 也可以
            case "android/os/ServiceManagerNative->asInterface(Landroid/os/IBinder;)Landroid/os/IServiceManager;":
                return new ServiceManager(vm, signature);
            case "com/android/internal/telephony/ITelephony$Stub->asInterface(Landroid/os/IBinder;)Lcom/android/internal/telephony/ITelephony;":
                return vaList.getObjectArg(0);
            case "java/security/cert/CertificateFactory->getInstance(Ljava/lang/String;)Ljava/security/cert/CertificateFactory;": {
                StringObject type = vaList.getObjectArg(0);
                assert type != null;
                try {
                    return dvmClass.newObject(CertificateFactory.getInstance(type.value));
                } catch (CertificateException e) {
                    throw new IllegalStateException(e);
                }
            }
            case "java/security/KeyFactory->getInstance(Ljava/lang/String;)Ljava/security/KeyFactory;": {
                StringObject algorithm = vaList.getObjectArg(0);
                assert algorithm != null;
                try {
                    return dvmClass.newObject(KeyFactory.getInstance(algorithm.value));
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            }
            case "javax/crypto/Cipher->getInstance(Ljava/lang/String;)Ljavax/crypto/Cipher;": {
                StringObject transformation = vaList.getObjectArg(0);
                assert transformation != null;
                try {
                    return dvmClass.newObject(Cipher.getInstance(transformation.value));
                } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                    throw new IllegalStateException(e);
                }
            }
            case "java/security/MessageDigest->getInstance(Ljava/lang/String;)Ljava/security/MessageDigest;": {
                StringObject type = vaList.getObjectArg(0);
                assert type != null;
                try {
                    return dvmClass.newObject(MessageDigest.getInstance(type.value));
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            }
            case "java/util/UUID->randomUUID()Ljava/util/UUID;": {
                TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
                UUID uuid = config == null ? null : config.getUuid(null);
                if (uuid == null) {
                    uuid = UUID.randomUUID();
                }
                log.info("[随机点] 随机uuid: {}", uuid.toString());
                return dvmClass.newObject(uuid);
            }
            case "android/app/ActivityThread->currentPackageName()Ljava/lang/String;": {
                String packageName = vm.getPackageName();
                if (packageName != null) {
                    return new StringObject(vm, packageName);
                }
                break;
            }
            // String.valueOf 系列
            case "java/lang/String->valueOf(I)Ljava/lang/String;":
                return new StringObject(vm, String.valueOf(vaList.getIntArg(0)));
            case "java/lang/String->valueOf(J)Ljava/lang/String;":
                return new StringObject(vm, String.valueOf(vaList.getLongArg(0)));
            case "java/lang/String->valueOf(Z)Ljava/lang/String;":
                return new StringObject(vm, String.valueOf(vaList.getIntArg(0) != 0));
            case "java/lang/Integer->toString(I)Ljava/lang/String;":
                return new StringObject(vm, Integer.toString(vaList.getIntArg(0)));
        }

        throw new UnsupportedOperationException(signature);
    }

    @Override
    public byte callByteMethodV(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VaList vaList) {
        return callByteMethodV(vm, dvmObject, dvmMethod.getSignature(), vaList);
    }

    @Override
    public byte callByteMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        log.info("callByteMethodV [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public short callShortMethodV(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VaList vaList) {
        return callShortMethodV(vm, dvmObject, dvmMethod.getSignature(), vaList);
    }


    @Override
    public short callShortMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        log.info("callShortMethodV [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public int callIntMethodV(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VaList vaList) {
        return callIntMethodV(vm, dvmObject, dvmMethod.getSignature(), vaList);
    }

    @Override
    public int callIntMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        log.info("callIntMethodV [Unidbg]: {}", signature);
        AndroidTelephonyIntResult telephonyResult = tryAndroidTelephonyInt(vm, signature, vaList);
        if (telephonyResult.handled) {
            return telephonyResult.value;
        }
        AndroidWifiIntResult wifiResult = tryAndroidWifiInt(vm, signature);
        if (wifiResult.handled) {
            return wifiResult.value;
        }
        NetworkBluetoothIntResult bluetoothIntResult =
                tryNetworkBluetoothInt(vm, dvmObject, signature);
        if (bluetoothIntResult.handled) {
            return bluetoothIntResult.value;
        }
        AndroidNetworkLinkIntResult linkResult = tryAndroidNetworkLinkInt(vm, dvmObject, signature);
        if (linkResult.handled) {
            return linkResult.value;
        }
        AndroidPackageIntFieldResult permissionResult = tryAndroidPackageCheckPermission(vm, signature, vaList);
        if (permissionResult.handled) {
            return permissionResult.value;
        }
        AndroidPackageIntFieldResult teeKeyInfoInt = tryAndroidTeeKeyInfoIntMethod(vm, dvmObject, signature);
        if (teeKeyInfoInt.handled) {
            return teeKeyInfoInt.value;
        }
        AndroidDisplayIntFieldResult displayIntMethod = tryAndroidDisplayIntMethod(vm, dvmObject, signature);
        if (displayIntMethod.handled) {
            return displayIntMethod.value;
        }
        AndroidThermalIntResult thermalResult = tryAndroidThermalInt(vm, signature);
        if (thermalResult.handled) {
            return thermalResult.value;
        }
        AndroidBatteryIntResult batteryResult = tryAndroidBatteryInt(vm, signature, vaList);
        if (batteryResult.handled) {
            return batteryResult.value;
        }
        AndroidUiModeIntResult uiModeResult = tryAndroidUiModeInt(vm, dvmObject, signature);
        if (uiModeResult.handled) {
            return uiModeResult.value;
        }
        AndroidSecurityStateIntResult securityStateInt =
                tryAndroidSecurityStateInt(vm, signature, vaList);
        if (securityStateInt.handled) {
            return securityStateInt.value;
        }
        AndroidUserHandleIntResult userHandleInt = tryAndroidUserHandleInt(vm, dvmObject, signature);
        if (userHandleInt.handled) {
            return userHandleInt.value;
        }
        AndroidSensorIntResult sensorIntResult = tryAndroidSensorInt(vm, dvmObject, signature);
        if (sensorIntResult.handled) {
            return sensorIntResult.value;
        }
        NetworkInterfaceIntResult networkInterfaceIntResult =
                tryNetworkInterfaceInt(vm, dvmObject, signature);
        if (networkInterfaceIntResult.handled) {
            return networkInterfaceIntResult.value;
        }
        AndroidAudioIntResult audioIntResult = tryAndroidAudioInt(vm, dvmObject, signature, vaList);
        if (audioIntResult.handled) {
            return audioIntResult.value;
        }
        AndroidClipboardIntResult clipboardIntResult =
                tryAndroidClipboardIntMethod(vm, dvmObject, signature);
        if (clipboardIntResult.handled) {
            return clipboardIntResult.value;
        }
        AndroidRuntimeIntResult runtimeAvailableProcessors =
                tryAndroidRuntimeAvailableProcessors(vm, dvmObject, signature);
        if (runtimeAvailableProcessors.handled) {
            return runtimeAvailableProcessors.value;
        }
        AndroidLocaleIntResult localeIntResult = tryAndroidLocaleIntMethod(vm, dvmObject, signature, vaList);
        if (localeIntResult.handled) {
            return localeIntResult.value;
        }
        AndroidAppSetIdIntResult appSetIdIntResult = tryAndroidAppSetIdIntMethod(vm, dvmObject, signature);
        if (appSetIdIntResult.handled) {
            return appSetIdIntResult.value;
        }
        AndroidLocationIntResult locationIntResult =
                tryAndroidLocationInt(vm, dvmObject, signature);
        if (locationIntResult.handled) {
            return locationIntResult.value;
        }
        switch (signature) {
            case "android/os/Bundle->getInt(Ljava/lang/String;)I":
                Bundle bundle = (Bundle) dvmObject;
                StringObject key = vaList.getObjectArg(0);
                assert key != null;
                return bundle.getInt(key.getValue());
            case "java/util/ArrayList->size()I": {
                ArrayListObject list = (ArrayListObject) dvmObject;
                return list.size();
            }
            case "android/content/pm/Signature->hashCode()I": {
                if (dvmObject instanceof Signature) {
                    Signature sig = (Signature) dvmObject;
                    return sig.getHashCode();
                }
                break;
            }
            case "java/lang/Integer->intValue()I": {
                Integer integer = (Integer) dvmObject.getValue();
                return integer.intValue();
            }
            case "java/util/List->size()I":
                List<?> list = (List<?>) dvmObject.getValue();
                return list.size();
            case "java/util/Map->size()I":
                Map<?, ?> map = (Map<?, ?>) dvmObject.getValue();
                return map.size();
            case "java/lang/String->hashCode()I":
                String string = (String) dvmObject.getValue();
                return string.hashCode();
            case "java/lang/String->compareToIgnoreCase(Ljava/lang/String;)I": {
                String str = (String) dvmObject.getValue();
                StringObject other = vaList.getObjectArg(0);
                return str.compareToIgnoreCase(other != null ? other.getValue() : "");
            }
        }

        throw new UnsupportedOperationException(signature);
    }

    @Override
    public long callStaticLongMethod(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VarArg varArg) {
        return callStaticLongMethod(vm, dvmClass, dvmMethod.getSignature(), varArg);
    }

    @Override
    public long callStaticLongMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        log.info("callStaticLongMethod [Unidbg]: {}", signature);
        AndroidSettingsGetLongResult settingsResult = tryAndroidSettingsGetLong(vm, signature, varArg);
        if (settingsResult.handled) {
            return settingsResult.value;
        }
        SystemClockLongResult systemClockLong = trySystemClockStaticLong(vm, signature);
        if (systemClockLong.handled) {
            return systemClockLong.value;
        }
        Long currentTimeMillis = trySystemCurrentTimeMillisStaticLong(vm, signature);
        if (currentTimeMillis != null) {
            return currentTimeMillis.longValue();
        }
        Long nanoTime = trySystemNanoTimeStaticLong(vm, signature);
        if (nanoTime != null) {
            return nanoTime.longValue();
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public long callStaticLongMethodV(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VaList vaList) {
        return callStaticLongMethodV(vm, dvmClass, dvmMethod.getSignature(), vaList);
    }


    @Override
    public long callStaticLongMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        log.info("callStaticLongMethodV [Unidbg]: {}", signature);
        AndroidSettingsGetLongResult settingsResult = tryAndroidSettingsGetLong(vm, signature, vaList);
        if (settingsResult.handled) {
            return settingsResult.value;
        }
        SystemClockLongResult systemClockLong = trySystemClockStaticLong(vm, signature);
        if (systemClockLong.handled) {
            return systemClockLong.value;
        }
        Long currentTimeMillis = trySystemCurrentTimeMillisStaticLong(vm, signature);
        if (currentTimeMillis != null) {
            return currentTimeMillis.longValue();
        }
        Long nanoTime = trySystemNanoTimeStaticLong(vm, signature);
        if (nanoTime != null) {
            return nanoTime.longValue();
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public boolean callBooleanMethod(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VarArg varArg) {
        return callBooleanMethod(vm, dvmObject, dvmMethod.getSignature(), varArg);
    }

    @Override
    public boolean callBooleanMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        log.info("callBooleanMethod [Unidbg]: {}", signature);
        AndroidTelephonyBooleanResult telephonyResult = tryAndroidTelephonyBoolean(vm, signature);
        if (telephonyResult.handled) {
            return telephonyResult.value;
        }
        AndroidSensorBooleanResult sensorBoolResult =
                tryAndroidSensorBoolean(vm, dvmObject, signature, varArg);
        if (sensorBoolResult.handled) {
            return sensorBoolResult.value;
        }
        AndroidWifiBooleanResult wifiResult = tryAndroidWifiBoolean(vm, signature);
        if (wifiResult.handled) {
            return wifiResult.value;
        }
        NetworkBluetoothBooleanResult bluetoothBoolResult =
                tryNetworkBluetoothBoolean(vm, dvmObject, signature);
        if (bluetoothBoolResult.handled) {
            return bluetoothBoolResult.value;
        }
        AndroidNetworkLinkBooleanResult linkResult = tryAndroidNetworkLinkBoolean(vm, signature);
        if (linkResult.handled) {
            return linkResult.value;
        }
        AndroidPackageBooleanFieldResult featureResult = tryAndroidFeatureHasSystemFeature(vm, signature, varArg);
        if (featureResult.handled) {
            return featureResult.value;
        }
        AndroidPackageBooleanFieldResult teeStrongBoxFeature =
                tryAndroidTeeStrongBoxHasSystemFeature(vm, signature, varArg);
        if (teeStrongBoxFeature.handled) {
            return teeStrongBoxFeature.value;
        }
        AndroidPackageBooleanFieldResult teeHardwareKeystoreFeature =
                tryAndroidTeeHardwareKeystoreHasSystemFeature(vm, signature, varArg);
        if (teeHardwareKeystoreFeature.handled) {
            return teeHardwareKeystoreFeature.value;
        }
        AndroidPackageBooleanFieldResult signingInfoBool =
                tryAndroidPackageSigningInfoBoolean(vm, dvmObject, signature);
        if (signingInfoBool.handled) {
            return signingInfoBool.value;
        }
        AndroidPackageBooleanFieldResult hasSigningCert =
                tryAndroidPackageHasSigningCertificate(vm, signature, varArg);
        if (hasSigningCert.handled) {
            return hasSigningCert.value;
        }
        AndroidPackageBooleanFieldResult teeKeyInfoBool = tryAndroidTeeKeyInfoBooleanMethod(vm, dvmObject, signature);
        if (teeKeyInfoBool.handled) {
            return teeKeyInfoBool.value;
        }
        AndroidPowerBooleanResult powerResult = tryAndroidPowerBoolean(vm, signature, varArg);
        if (powerResult.handled) {
            return powerResult.value;
        }
        AndroidBatteryBooleanResult batteryBoolResult = tryAndroidBatteryBoolean(vm, signature);
        if (batteryBoolResult.handled) {
            return batteryBoolResult.value;
        }
        AndroidClipboardBooleanResult clipboardBoolResult =
                tryAndroidClipboardBoolean(vm, dvmObject, signature, varArg);
        if (clipboardBoolResult.handled) {
            return clipboardBoolResult.value;
        }
        AndroidAccessibilityBooleanResult accessibilityBoolResult =
                tryAndroidAccessibilityBoolean(vm, dvmObject, signature);
        if (accessibilityBoolResult.handled) {
            return accessibilityBoolResult.value;
        }
        AndroidAudioBooleanResult audioBoolResult =
                tryAndroidAudioBoolean(vm, dvmObject, signature);
        if (audioBoolResult.handled) {
            return audioBoolResult.value;
        }
        AndroidLocationBooleanResult locationBoolResult =
                tryAndroidLocationBoolean(vm, dvmObject, signature, varArg);
        if (locationBoolResult.handled) {
            return locationBoolResult.value;
        }
        AndroidSecurityStateBooleanResult securityStateBool =
                tryAndroidSecurityStateBoolean(vm, signature);
        if (securityStateBool.handled) {
            return securityStateBool.value;
        }
        AndroidUserManagerBooleanResult userManagerBool = tryAndroidUserManagerBoolean(vm, signature, varArg);
        if (userManagerBool.handled) {
            return userManagerBool.value;
        }
        AndroidAdvertisingIdBooleanResult advertisingIdBool =
                tryAndroidAdvertisingIdBoolean(vm, dvmObject, signature);
        if (advertisingIdBool.handled) {
            return advertisingIdBool.value;
        }
        NetworkInterfaceBooleanResult networkInterfaceBool =
                tryNetworkInterfaceBoolean(vm, dvmObject, signature);
        if (networkInterfaceBool.handled) {
            return networkInterfaceBool.value;
        }
        switch (signature) {
            case "java/util/Enumeration->hasMoreElements()Z":
                return ((Enumeration) dvmObject).hasMoreElements();
            case "java/lang/Boolean->booleanValue()Z":
                DvmBoolean dvmBoolean = (DvmBoolean) dvmObject;
                return dvmBoolean.value;
            case "java/util/Map->isEmpty()Z":
                Map<?, ?> map = (Map<?, ?>) dvmObject.getValue();
                return map.isEmpty();
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public boolean callBooleanMethodV(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VaList vaList) {
        return callBooleanMethodV(vm, dvmObject, dvmMethod.getSignature(), vaList);
    }

    @Override
    public boolean callBooleanMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        log.info("callBooleanMethodV [Unidbg]: {}", signature);
        AndroidTelephonyBooleanResult telephonyResult = tryAndroidTelephonyBoolean(vm, signature);
        if (telephonyResult.handled) {
            return telephonyResult.value;
        }
        AndroidSensorBooleanResult sensorBoolResult =
                tryAndroidSensorBoolean(vm, dvmObject, signature, vaList);
        if (sensorBoolResult.handled) {
            return sensorBoolResult.value;
        }
        AndroidWifiBooleanResult wifiResult = tryAndroidWifiBoolean(vm, signature);
        if (wifiResult.handled) {
            return wifiResult.value;
        }
        NetworkBluetoothBooleanResult bluetoothBoolResult =
                tryNetworkBluetoothBoolean(vm, dvmObject, signature);
        if (bluetoothBoolResult.handled) {
            return bluetoothBoolResult.value;
        }
        AndroidNetworkLinkBooleanResult linkResult = tryAndroidNetworkLinkBoolean(vm, signature);
        if (linkResult.handled) {
            return linkResult.value;
        }
        AndroidPackageBooleanFieldResult featureResult = tryAndroidFeatureHasSystemFeature(vm, signature, vaList);
        if (featureResult.handled) {
            return featureResult.value;
        }
        AndroidPackageBooleanFieldResult teeStrongBoxFeature =
                tryAndroidTeeStrongBoxHasSystemFeature(vm, signature, vaList);
        if (teeStrongBoxFeature.handled) {
            return teeStrongBoxFeature.value;
        }
        AndroidPackageBooleanFieldResult teeHardwareKeystoreFeature =
                tryAndroidTeeHardwareKeystoreHasSystemFeature(vm, signature, vaList);
        if (teeHardwareKeystoreFeature.handled) {
            return teeHardwareKeystoreFeature.value;
        }
        AndroidPackageBooleanFieldResult signingInfoBool =
                tryAndroidPackageSigningInfoBoolean(vm, dvmObject, signature);
        if (signingInfoBool.handled) {
            return signingInfoBool.value;
        }
        AndroidPackageBooleanFieldResult hasSigningCert =
                tryAndroidPackageHasSigningCertificate(vm, signature, vaList);
        if (hasSigningCert.handled) {
            return hasSigningCert.value;
        }
        AndroidPackageBooleanFieldResult teeKeyInfoBool = tryAndroidTeeKeyInfoBooleanMethod(vm, dvmObject, signature);
        if (teeKeyInfoBool.handled) {
            return teeKeyInfoBool.value;
        }
        AndroidPowerBooleanResult powerResult = tryAndroidPowerBoolean(vm, signature, vaList);
        if (powerResult.handled) {
            return powerResult.value;
        }
        AndroidBatteryBooleanResult batteryBoolResult = tryAndroidBatteryBoolean(vm, signature);
        if (batteryBoolResult.handled) {
            return batteryBoolResult.value;
        }
        AndroidClipboardBooleanResult clipboardBoolResult =
                tryAndroidClipboardBoolean(vm, dvmObject, signature, vaList);
        if (clipboardBoolResult.handled) {
            return clipboardBoolResult.value;
        }
        AndroidAccessibilityBooleanResult accessibilityBoolResult =
                tryAndroidAccessibilityBoolean(vm, dvmObject, signature);
        if (accessibilityBoolResult.handled) {
            return accessibilityBoolResult.value;
        }
        AndroidAudioBooleanResult audioBoolResult =
                tryAndroidAudioBoolean(vm, dvmObject, signature);
        if (audioBoolResult.handled) {
            return audioBoolResult.value;
        }
        AndroidLocationBooleanResult locationBoolResult =
                tryAndroidLocationBoolean(vm, dvmObject, signature, vaList);
        if (locationBoolResult.handled) {
            return locationBoolResult.value;
        }
        AndroidSecurityStateBooleanResult securityStateBool =
                tryAndroidSecurityStateBoolean(vm, signature);
        if (securityStateBool.handled) {
            return securityStateBool.value;
        }
        AndroidUserManagerBooleanResult userManagerBool = tryAndroidUserManagerBoolean(vm, signature, vaList);
        if (userManagerBool.handled) {
            return userManagerBool.value;
        }
        AndroidAdvertisingIdBooleanResult advertisingIdBool =
                tryAndroidAdvertisingIdBoolean(vm, dvmObject, signature);
        if (advertisingIdBool.handled) {
            return advertisingIdBool.value;
        }
        NetworkInterfaceBooleanResult networkInterfaceBool =
                tryNetworkInterfaceBoolean(vm, dvmObject, signature);
        if (networkInterfaceBool.handled) {
            return networkInterfaceBool.value;
        }
        switch (signature) {
            case "java/util/Enumeration->hasMoreElements()Z":
                return ((Enumeration) dvmObject).hasMoreElements();
            case "java/util/ArrayList->isEmpty()Z":
                return ((ArrayListObject) dvmObject).isEmpty();
            case "java/util/Iterator->hasNext()Z":
                Object iterator = dvmObject.getValue();
                if (iterator instanceof Iterator) {
                    return ((Iterator<?>) iterator).hasNext();
                }
            case "java/lang/String->startsWith(Ljava/lang/String;)Z": {
                String str = (String) dvmObject.getValue();
                StringObject prefix = vaList.getObjectArg(0);
                return str.startsWith(prefix.value);
            }
            // 字符串忽略大小写比较
            case "java/lang/String->equalsIgnoreCase(Ljava/lang/String;)Z": {
                String str = (String) dvmObject.getValue();
                StringObject other = vaList.getObjectArg(0);
                return str.equalsIgnoreCase(other != null ? other.getValue() : null);
            }
            // 文件存在性检查
            case "java/io/File->exists()Z": {
                Object value = dvmObject.getValue();
                if (value instanceof File) {
                    return ((File) value).exists();
                }
                return false;
            }
            // Boolean 包装类的 booleanValue 方法
            case "java/lang/Boolean->booleanValue()Z": {
                Boolean value = (Boolean) dvmObject.getValue();
                return value.booleanValue();
            }
                
        }

        throw new UnsupportedOperationException(signature);
    }

    @Override
    public byte getByteField(BaseVM vm, DvmObject<?> dvmObject, DvmField dvmField) {
        return getByteField(vm, dvmObject, dvmField.getSignature());
    }

    @Override
    public byte getByteField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        log.info("getByteField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public int getIntField(BaseVM vm, DvmObject<?> dvmObject, DvmField dvmField) {
        return getIntField(vm, dvmObject, dvmField.getSignature());
    }

    @Override
    public int getIntField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        log.info("getIntField [Unidbg]: {}", signature);
        AndroidDhcpIntFieldResult dhcpField = tryAndroidDhcpIntField(vm, dvmObject, signature);
        if (dhcpField.handled) {
            return dhcpField.value;
        }
        AndroidPackageIntFieldResult packageField = tryAndroidPackageIntField(vm, dvmObject, signature);
        if (packageField.handled) {
            return packageField.value;
        }
        AndroidPackageIntFieldResult featureField = tryAndroidFeatureIntField(vm, dvmObject, signature);
        if (featureField.handled) {
            return featureField.value;
        }
        AndroidDisplayIntFieldResult displayIntField = tryAndroidDisplayIntField(vm, dvmObject, signature);
        if (displayIntField.handled) {
            return displayIntField.value;
        }
        AndroidCameraInfoIntFieldResult cameraInfoIntField =
                tryAndroidCameraInfoIntField(vm, dvmObject, signature);
        if (cameraInfoIntField.handled) {
            return cameraInfoIntField.value;
        }
        AndroidConfigurationIntFieldResult configurationIntField =
                tryAndroidConfigurationIntField(vm, dvmObject, signature);
        if (configurationIntField.handled) {
            return configurationIntField.value;
        }
        if (isConfiguredPackageInfo(dvmObject)
                && "android/content/pm/PackageInfo->versionCode:I".equals(signature)) {
            // Live marker without configured versionCode, or foreign-VM marker:
            // must not fall through to APK versionCode (no sidecar).
            throw new UnsupportedOperationException(signature);
        }
        if (isLiveConfiguredApplicationInfo(vm, dvmObject)
                && signature != null
                && signature.startsWith("android/content/pm/ApplicationInfo->")
                && signature.endsWith(":I")) {
            // Live marker without configured uid/flags must not fall through to unrelated defaults.
            throw new UnsupportedOperationException(signature);
        }
        if (isConfiguredFeatureInfo(dvmObject)
                && signature != null
                && signature.startsWith("android/content/pm/FeatureInfo->")) {
            // Live missing/unsupported field, or foreign-VM marker: never fall through
            // (no sidecar). Live configured values were already returned by
            // isLiveConfiguredFeatureInfo readers.
            throw new UnsupportedOperationException(signature);
        }
        // versionCode: 整数版本号，每次发布必须递增
        // unidbg 已实现，源码在 ApkFile.java:
        //   apkMeta = apkFile.getApkMeta();
        //   return apkMeta.getVersionCode();
        // 使用 apk-parser/jadx 解析 AndroidManifest.xml 获取
        switch (signature) {
            case "android/content/pm/PackageInfo->versionCode:I":
                log.info("这里在获取apk的整数版本号！");
                return (int) vm.getVersionCode();
        }

        throw new UnsupportedOperationException(signature);
    }

    @Override
    public long getLongField(BaseVM vm, DvmObject<?> dvmObject, DvmField dvmField) {
        return getLongField(vm, dvmObject, dvmField.getSignature());
    }

    @Override
    public long getLongField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        log.info("getLongField [Unidbg]: {}", signature);
        AndroidPackageLongFieldResult packageField = tryAndroidPackageLongField(vm, dvmObject, signature);
        if (packageField.handled) {
            return packageField.value;
        }
        if (isConfiguredPackageInfo(dvmObject)
                && signature != null
                && signature.startsWith("android/content/pm/PackageInfo->")
                && signature.endsWith(":J")) {
            // Live marker without configured install times, or foreign-VM marker:
            // must not invent defaults (no sidecar).
            throw new UnsupportedOperationException(signature);
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public float getFloatField(BaseVM vm, DvmObject<?> dvmObject, DvmField dvmField) {
        return getFloatField(vm, dvmObject, dvmField.getSignature());
    }

    @Override
    public float getFloatField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        log.info("getFloatField [Unidbg]: {}", signature);
        AndroidDisplayFloatFieldResult displayFloatField = tryAndroidDisplayFloatField(vm, dvmObject, signature);
        if (displayFloatField.handled) {
            return displayFloatField.value;
        }
        AndroidConfigurationFloatFieldResult configurationFloatField =
                tryAndroidConfigurationFloatField(vm, dvmObject, signature);
        if (configurationFloatField.handled) {
            return configurationFloatField.value;
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public float callStaticFloatMethod(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VarArg varArg) {
        return callStaticFloatMethod(vm, dvmClass, dvmMethod.getSignature(), varArg);
    }

    @Override
    public float callStaticFloatMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        log.info("callStaticFloatMethod [Unidbg]: {}", signature);
        AndroidSettingsGetFloatResult settingsResult = tryAndroidSettingsGetFloat(vm, signature, varArg);
        if (settingsResult.handled) {
            return settingsResult.value;
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public float callStaticFloatMethodV(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VaList vaList) {
        return callStaticFloatMethodV(vm, dvmClass, dvmMethod.getSignature(), vaList);
    }

    @Override
    public float callStaticFloatMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        log.info("callStaticFloatMethodV [Unidbg]: {}", signature);
        AndroidSettingsGetFloatResult settingsResult = tryAndroidSettingsGetFloat(vm, signature, vaList);
        if (settingsResult.handled) {
            return settingsResult.value;
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public double callStaticDoubleMethod(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VarArg varArg) {
        return callStaticDoubleMethod(vm, dvmClass, dvmMethod.getSignature(), varArg);
    }

    @Override
    public double callStaticDoubleMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        log.info("callStaticDoubleMethod [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void callStaticVoidMethod(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VarArg varArg) {
        callStaticVoidMethod(vm, dvmClass, dvmMethod.getSignature(), varArg);
    }

    @Override
    public void callStaticVoidMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        log.info("callStaticVoidMethod [Unidbg]: {}", signature);
        AndroidCamerasVoidResult camerasVoid = tryAndroidCamerasStaticVoid(vm, signature, varArg);
        if (camerasVoid.handled) {
            return;
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void callStaticVoidMethodV(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VaList vaList) {
        callStaticVoidMethodV(vm, dvmClass, dvmMethod.getSignature(), vaList);
    }

    @Override
    public void callStaticVoidMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        log.info("callStaticVoidMethodV [Unidbg]: {}", signature);
        AndroidCamerasVoidResult camerasVoid = tryAndroidCamerasStaticVoid(vm, signature, vaList);
        if (camerasVoid.handled) {
            return;
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void setObjectField(BaseVM vm, DvmObject<?> dvmObject, DvmField dvmField, DvmObject<?> value) {
        setObjectField(vm, dvmObject, dvmField.getSignature(), value);
    }

    @Override
    public void setObjectField(BaseVM vm, DvmObject<?> dvmObject, String signature, DvmObject<?> value) {
        log.info("setObjectField [Unidbg]: {}", signature);
        System.out.println(bytesToHex((byte[]) value.getValue()));
        // throw new UnsupportedOperationException(signature); // TODO: 暂时注释
    }
    @Override
    public boolean getBooleanField(BaseVM vm, DvmObject<?> dvmObject, DvmField dvmField) {
        return getBooleanField(vm, dvmObject, dvmField.getSignature());
    }


    @Override
    public boolean getBooleanField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        log.info("getBooleanField [Unidbg]: {}", signature);
        AndroidPackageBooleanFieldResult packageField = tryAndroidPackageBooleanField(vm, dvmObject, signature);
        if (packageField.handled) {
            return packageField.value;
        }
        AndroidCameraInfoBooleanFieldResult cameraInfoBooleanField =
                tryAndroidCameraInfoBooleanField(vm, dvmObject, signature);
        if (cameraInfoBooleanField.handled) {
            return cameraInfoBooleanField.value;
        }
        if (isLiveConfiguredApplicationInfo(vm, dvmObject)
                && signature != null
                && signature.startsWith("android/content/pm/ApplicationInfo->")
                && signature.endsWith(":Z")) {
            // Live marker without configured enabled must not invent a default.
            throw new UnsupportedOperationException(signature);
        }
        if (isLiveConfiguredCameraInfo(vm, dvmObject)
                && signature != null
                && signature.startsWith("android/hardware/Camera$CameraInfo->")
                && signature.endsWith(":Z")) {
            // Marker without configured canDisableShutterSound must not invent a default.
            throw new UnsupportedOperationException(signature);
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public DvmObject<?> newObject(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VarArg varArg) {
        return newObject(vm, dvmClass, dvmMethod.getSignature(), varArg);
    }

    @Override
    public DvmObject<?> newObject(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        log.info("newObject [Unidbg]: {}", signature);
        switch (signature) {
            case "java/lang/String-><init>([B)V": {
                ByteArray array = varArg.getObjectArg(0);
                return new StringObject(vm, new String(array.getValue()));
            }
            case "java/lang/String-><init>([BLjava/lang/String;)V":
                ByteArray array = varArg.getObjectArg(0);
                StringObject string = varArg.getObjectArg(1);
                try {
                    return new StringObject(vm, new String(array.getValue(), string.getValue()));
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalStateException(e);
                }
            case "java/lang/Throwable-><init>()V":
                Throwable throwable = new Throwable();
                return vm.resolveClass("java/lang/Throwable").newObject(throwable);
            case "java/io/ByteArrayOutputStream-><init>()V":
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                return vm.resolveClass("java/io/ByteArrayOutputStream").newObject(byteArrayOutputStream);
            case "java/util/zip/GZIPOutputStream-><init>(Ljava/io/OutputStream;)V":
                DvmObject<?> outputStream = varArg.getObjectArg(0);
                java.io.OutputStream os = (java.io.OutputStream) outputStream.getValue();
                try {
                    log.info("这里使用了gzip压缩，注意与python库的版本区别");
                    return vm.resolveClass("java/util/zip/GZIPOutputStream").newObject(new java.util.zip.GZIPOutputStream(os));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create GZIPOutputStream", e);
                }
            // 这里用jdk1.8即可
            case "sun/security/pkcs/PKCS7-><init>([B)V":
                    DvmObject<?> objectArg = varArg.getObjectArg(0);
                    try{
                        byte[] bytes = (byte[]) objectArg.getValue();
                        Object pkcs7 = Class.forName("sun.security.pkcs.PKCS7").getConstructor(byte[].class).newInstance(bytes);
                        log.info("[风控高危] 拦截到实例化 PKCS7，SO 正在注入原始签名数据，大小: {} 字节", bytes.length);
                        return vm.resolveClass("sun/security/pkcs/PKCS7").newObject(pkcs7);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
            case "java/lang/StringBuffer-><init>()V":
                StringBuffer stringBuffer = new StringBuffer();
                return vm.resolveClass("java/lang/StringBuffer").newObject(stringBuffer);

            case "java/util/HashMap-><init>(I)V":
                int size_tmp = (int) varArg.getObjectArg(0).getValue();
                return vm.resolveClass("java/util/HashMap").newObject(new HashMap<>(size_tmp));
        }

        throw new UnsupportedOperationException(signature);
    }

    @Override
    public DvmObject<?> newObjectV(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VaList vaList) {
        return newObjectV(vm, dvmClass, dvmMethod.getSignature(), vaList);
    }


    @Override
    public DvmObject<?> newObjectV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        log.info("newObjectV [Unidbg]: {}", signature);
        switch (signature) {
            case "java/io/ByteArrayInputStream-><init>([B)V": {
                ByteArray array = vaList.getObjectArg(0);
                assert array != null;
                return vm.resolveClass("java/io/ByteArrayInputStream").newObject(new ByteArrayInputStream(array.value));
            }
            case "java/lang/String-><init>([B)V": {
                ByteArray array = vaList.getObjectArg(0);
                assert array != null;
                return new StringObject(vm, new String(array.value));
            }
            case "java/lang/String-><init>([BLjava/lang/String;)V": {
                ByteArray array = vaList.getObjectArg(0);
                assert array != null;
                StringObject charsetName = vaList.getObjectArg(1);
                assert charsetName != null;
                try {
                    return new StringObject(vm, new String(array.value, charsetName.value));
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalStateException(e);
                }
            }
            case "javax/crypto/spec/SecretKeySpec-><init>([BLjava/lang/String;)V": {
                byte[] key = (byte[]) vaList.getObjectArg(0).value;
                StringObject algorithm = vaList.getObjectArg(1);
                assert algorithm != null;
                SecretKeySpec secretKeySpec = new SecretKeySpec(key, algorithm.value);
                return dvmClass.newObject(secretKeySpec);
            }
            case "java/lang/Integer-><init>(I)V": {
                return DvmInteger.valueOf(vm, vaList.getIntArg(0));
            }
            case "java/lang/Boolean-><init>(Z)V": {
                boolean b;
                b = vaList.getIntArg(0) != 0;
                return DvmBoolean.valueOf(vm, b);
            }
            // new Date(): 创建当前时间的 Date 对象
            // 变化点: 使用当前系统时间，每次调用结果不同
            // 如果需要固定时间，可在子类覆盖返回 new Date(固定时间戳)
            case "java/util/Date-><init>()V": {
                java.util.Date date = new java.util.Date(getConfiguredCurrentTimeMillis(vm));
                log.info("[随机点] new Date() 时间戳: {}", date.getTime());
                return ProxyDvmObject.createObject(vm, date);
            }
            case "java/lang/StringBuffer-><init>()V": {
                StringBuffer stringBuffer = new StringBuffer();
                return vm.resolveClass("java/lang/StringBuffer").newObject(stringBuffer);
            }
        }

        throw new UnsupportedOperationException(signature);
    }


    @Override
    public DvmObject<?> allocObject(BaseVM vm, DvmClass dvmClass, String signature) {
        log.info("allocObject [Unidbg]: {}", signature);
        switch (signature) {
            // HashMap: 有无参构造，直接创建空实例
            case "java/util/HashMap->allocObject":
                return dvmClass.newObject(new HashMap<>());
            
            // ArrayList: 有无参构造，直接创建空实例
            case "java/util/ArrayList->allocObject":
                return dvmClass.newObject(new java.util.ArrayList<>());
            
            // StringBuilder: 有无参构造，直接创建空实例
            case "java/lang/StringBuilder->allocObject":
                return dvmClass.newObject(new StringBuilder());
            
            // StringBuffer: 有无参构造，直接创建空实例
            case "java/lang/StringBuffer->allocObject":
                return dvmClass.newObject(new StringBuffer());
            
            // Date: 有无参构造，创建当前时间实例
            // 指纹风险: 低。时间戳可能参与签名，但通常不校验
            case "java/util/Date->allocObject":
                return dvmClass.newObject(new java.util.Date(getConfiguredCurrentTimeMillis(vm)));
            
            // SimpleDateFormat: 有无参构造，创建默认格式实例
            case "java/text/SimpleDateFormat->allocObject":
                return dvmClass.newObject(new java.text.SimpleDateFormat());
            
            // File: 有 File(String) 构造，先创建空路径，<init>(String) 时 setValue 替换
            case "java/io/File->allocObject":
                return dvmClass.newObject(new File(""));
            
            // FileInputStream: 无无参构造，必须有文件路径
            // 先 null 占位，<init>(String) 或 <init>(File) 时 setValue 创建真实实例
            case "java/io/FileInputStream->allocObject":
            // FileReader: 同上，无无参构造
            case "java/io/FileReader->allocObject":
            // InputStreamReader: 同上，需要 InputStream 参数
            case "java/io/InputStreamReader->allocObject":
            // BufferedReader: 同上，需要 Reader 参数
            case "java/io/BufferedReader->allocObject":
                return dvmClass.newObject(null);
        }

        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void setIntField(BaseVM vm, DvmObject<?> dvmObject, DvmField dvmField, int value) {
        setIntField(vm, dvmObject, dvmField.getSignature(), value);
    }

    @Override
    public void setIntField(BaseVM vm, DvmObject<?> dvmObject, String signature, int value) {
        log.info("setIntField [Unidbg]: {}", signature);
        log.info("setIntField [Unidbg] -> value: {}", value);
        // throw new UnsupportedOperationException(signature); // TODO: 暂时不报错了
    }

    @Override
    public void setLongField(BaseVM vm, DvmObject<?> dvmObject, DvmField dvmField, long value) {
        setLongField(vm, dvmObject, dvmField.getSignature(), value);
    }

    @Override
    public void setLongField(BaseVM vm, DvmObject<?> dvmObject, String signature, long value) {
        log.info("setLongField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void setBooleanField(BaseVM vm, DvmObject<?> dvmObject, DvmField dvmField, boolean value) {
        setBooleanField(vm, dvmObject, dvmField.getSignature(), value);
    }

    @Override
    public void setBooleanField(BaseVM vm, DvmObject<?> dvmObject, String signature, boolean value) {
        log.info("setBooleanField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void setFloatField(BaseVM vm, DvmObject<?> dvmObject, DvmField dvmField, float value) {
        setFloatField(vm, dvmObject, dvmField.getSignature(), value);
    }

    @Override
    public void setFloatField(BaseVM vm, DvmObject<?> dvmObject, String signature, float value) {
        log.info("setFloatField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void setDoubleField(BaseVM vm, DvmObject<?> dvmObject, DvmField dvmField, double value) {
        setDoubleField(vm, dvmObject, dvmField.getSignature(), value);
    }


    @Override
    public void setDoubleField(BaseVM vm, DvmObject<?> dvmObject, String signature, double value) {
        log.info("setDoubleField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public DvmObject<?> callObjectMethod(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VarArg varArg) {
        return callObjectMethod(vm, dvmObject, dvmMethod.getSignature(), varArg);
    }


    @Override
    public DvmObject<?> callObjectMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        log.info("callObjectMethod [Unidbg]: {}", signature);
        AndroidTelephonyIdentifierResult telephonyResult = tryAndroidTelephonyIdentifier(vm, signature, varArg);
        if (telephonyResult.handled) {
            return telephonyResult.value;
        }
        AndroidWifiObjectResult wifiResult = tryAndroidWifiObject(vm, signature);
        if (wifiResult.handled) {
            return wifiResult.value;
        }
        NetworkBluetoothObjectResult bluetoothObjectResult =
                tryNetworkBluetoothObjectMethod(vm, dvmObject, signature);
        if (bluetoothObjectResult.handled) {
            return bluetoothObjectResult.value;
        }
        AndroidDhcpObjectResult dhcpResult = tryAndroidDhcpObject(vm, signature);
        if (dhcpResult.handled) {
            return dhcpResult.value;
        }
        AndroidNetworkLinkObjectResult linkResult = tryAndroidNetworkLinkObject(vm, dvmObject, signature);
        if (linkResult.handled) {
            return linkResult.value;
        }
        AndroidPackageObjectResult packageResult = tryAndroidPackageGetPackageInfo(vm, signature, varArg);
        if (packageResult.handled) {
            return packageResult.value;
        }
        AndroidPackageObjectResult applicationInfoResult = tryAndroidPackageGetApplicationInfo(vm, signature, varArg);
        if (applicationInfoResult.handled) {
            return applicationInfoResult.value;
        }
        AndroidPackageObjectResult installedListResult = tryAndroidPackageGetInstalledList(vm, signature, varArg);
        if (installedListResult.handled) {
            return installedListResult.value;
        }
        AndroidPackageObjectResult installerResult = tryAndroidPackageGetInstallerPackageName(vm, signature, varArg);
        if (installerResult.handled) {
            return installerResult.value;
        }
        AndroidPackageObjectResult installSourceInfoResult = tryAndroidPackageGetInstallSourceInfo(vm, signature, varArg);
        if (installSourceInfoResult.handled) {
            return installSourceInfoResult.value;
        }
        AndroidPackageObjectResult installSourceMethodResult = tryAndroidPackageInstallSourceInfoMethod(vm, dvmObject, signature);
        if (installSourceMethodResult.handled) {
            return installSourceMethodResult.value;
        }
        AndroidPackageObjectResult signingInfoMethodResult =
                tryAndroidPackageSigningInfoMethod(vm, dvmObject, signature);
        if (signingInfoMethodResult.handled) {
            return signingInfoMethodResult.value;
        }
        AndroidPackageObjectResult uidMapResult = tryAndroidPackageUidMapping(vm, signature, varArg);
        if (uidMapResult.handled) {
            return uidMapResult.value;
        }
        AndroidPackageObjectResult availableFeaturesResult =
                tryAndroidFeatureGetSystemAvailableFeatures(vm, signature);
        if (availableFeaturesResult.handled) {
            return availableFeaturesResult.value;
        }
        AndroidPackageObjectResult teeKeySpecResult = tryAndroidTeeKeyFactoryGetKeySpec(vm, signature, varArg);
        if (teeKeySpecResult.handled) {
            return teeKeySpecResult.value;
        }
        AndroidPackageObjectResult teeKeyInfoMethodResult = tryAndroidTeeKeyInfoObjectMethod(vm, dvmObject, signature);
        if (teeKeyInfoMethodResult.handled) {
            return teeKeyInfoMethodResult.value;
        }
        AndroidPackageObjectResult teeKeyStoreGetKey = tryAndroidTeeKeyStoreGetKey(vm, signature, varArg);
        if (teeKeyStoreGetKey.handled) {
            return teeKeyStoreGetKey.value;
        }
        AndroidPackageObjectResult teeKeyEncoded = tryAndroidTeeKeyGetEncoded(vm, dvmObject, signature);
        if (teeKeyEncoded.handled) {
            return teeKeyEncoded.value;
        }
        AndroidPackageObjectResult teeKeyAlgoFormat =
                tryAndroidTeeKeyGetAlgorithmOrFormat(vm, dvmObject, signature);
        if (teeKeyAlgoFormat.handled) {
            return teeKeyAlgoFormat.value;
        }
        AndroidLocaleObjectResult localeObjectResult = tryAndroidLocaleObjectMethod(vm, dvmObject, signature);
        if (localeObjectResult.handled) {
            return localeObjectResult.value;
        }
        AndroidAdvertisingIdObjectResult advertisingIdObjectResult =
                tryAndroidAdvertisingIdObjectMethod(vm, dvmObject, signature);
        if (advertisingIdObjectResult.handled) {
            return advertisingIdObjectResult.value;
        }
        AndroidAppSetIdObjectResult appSetIdObjectResult =
                tryAndroidAppSetIdObjectMethod(vm, dvmObject, signature);
        if (appSetIdObjectResult.handled) {
            return appSetIdObjectResult.value;
        }
        AndroidAccountObjectResult accountsObjectResult =
                tryAndroidAccountManagerObjectMethod(vm, dvmObject, signature, varArg);
        if (accountsObjectResult.handled) {
            return accountsObjectResult.value;
        }
        AndroidInputMethodObjectResult inputMethodObjectResult =
                tryAndroidInputMethodObjectMethod(vm, dvmObject, signature);
        if (inputMethodObjectResult.handled) {
            return inputMethodObjectResult.value;
        }
        AndroidAccessibilityServiceObjectResult a11yServiceObjectResult =
                tryAndroidAccessibilityServiceObjectMethod(vm, dvmObject, signature);
        if (a11yServiceObjectResult.handled) {
            return a11yServiceObjectResult.value;
        }
        AndroidClipboardObjectResult clipboardObjectResult =
                tryAndroidClipboardObjectMethod(vm, dvmObject, signature, varArg);
        if (clipboardObjectResult.handled) {
            return clipboardObjectResult.value;
        }
        AndroidDisplayObjectResult displayObjectResult = tryAndroidDisplayObjectMethod(vm, dvmObject, signature, varArg);
        if (displayObjectResult.handled) {
            return displayObjectResult.value;
        }
        AndroidConfigurationObjectResult configurationObjectResult =
                tryAndroidConfigurationObjectMethod(vm, signature);
        if (configurationObjectResult.handled) {
            return configurationObjectResult.value;
        }
        AndroidLocationObjectResult locationObjectResult =
                tryAndroidLocationObjectMethod(vm, dvmObject, signature, varArg);
        if (locationObjectResult.handled) {
            return locationObjectResult.value;
        }
        AndroidSensorObjectResult sensorObjectResult =
                tryAndroidSensorObjectMethod(vm, dvmObject, signature, varArg);
        if (sensorObjectResult.handled) {
            return sensorObjectResult.value;
        }
        AndroidAudioObjectResult audioObjectResult =
                tryAndroidAudioGetProperty(vm, dvmObject, signature, varArg);
        if (audioObjectResult.handled) {
            return audioObjectResult.value;
        }
        NetworkInterfaceObjectResult networkInterfaceObjectResult =
                tryNetworkInterfaceObjectMethod(vm, dvmObject, signature);
        if (networkInterfaceObjectResult.handled) {
            return networkInterfaceObjectResult.value;
        }
        AndroidUserHandleObjectResult userHandleForSerial =
                tryAndroidUserManagerGetUserHandleForSerialNumber(vm, signature, varArg);
        if (userHandleForSerial.handled) {
            return userHandleForSerial.value;
        }
        AndroidDataDirObjectResult dataDirDirs = tryAndroidDataDirContextDirs(vm, signature, varArg);
        if (dataDirDirs.handled) {
            return dataDirDirs.value;
        }
        FileSystemExternalStorageObjectResult externalAppDirs =
                tryFilesystemExternalStorageContextAppExternalDirs(vm, signature, varArg);
        if (externalAppDirs.handled) {
            return externalAppDirs.value;
        }
        switch (signature) {
            case "java/util/Enumeration->nextElement()Ljava/lang/Object;":
                return ((Enumeration) dvmObject).nextElement();
            case "java/lang/String->getBytes(Ljava/lang/String;)[B": {
                StringObject string = (StringObject) dvmObject;
                StringObject encoding = varArg.getObjectArg(0);
                System.err.println("string=" + string.getValue() + ", encoding=" + encoding.getValue());
                try {
                    return new ByteArray(vm, string.getValue().getBytes(encoding.value));
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalStateException(e);
                }
            }
            case "android/content/Context->getPackageManager()Landroid/content/pm/PackageManager;":
            case "android/app/Activity->getPackageManager()Landroid/content/pm/PackageManager;":
            case "android/content/ContextWrapper->getPackageManager()Landroid/content/pm/PackageManager;":
                return vm.resolveClass("android/content/pm/PackageManager").newObject(null);
            case "android/content/Context->getApplicationInfo()Landroid/content/pm/ApplicationInfo;":
            case "android/app/Activity->getApplicationInfo()Landroid/content/pm/ApplicationInfo;":
                return new ApplicationInfo(vm);
            case "android/app/Application->getPackageName()Ljava/lang/String;":
            case "android/content/ContextWrapper->getPackageName()Ljava/lang/String;":
            case "android/app/Activity->getPackageName()Ljava/lang/String;":
            case "android/content/Context->getPackageName()Ljava/lang/String;": {
                String packageName = vm.getPackageName();
                if (packageName != null) {
                    return new StringObject(vm, packageName);
                }
                break;
            }
            case "android/app/Application->getSystemService(Ljava/lang/String;)Ljava/lang/Object;":
            case "android/content/Context->getSystemService(Ljava/lang/String;)Ljava/lang/Object;": {
                StringObject serviceName = varArg.getObjectArg(0);
                assert serviceName != null;
                return new SystemService(vm, serviceName.getValue());
            }
            // Typed getSystemService(Class): only BluetoothManager / WifiManager /
            // ConnectivityManager / LocationManager / AudioManager / InputMethodManager /
            // SensorManager / PowerManager / BatteryManager / UiModeManager /
            // KeyguardManager / WindowManager / DisplayManager / ClipboardManager /
            // AccessibilityManager / UserManager / TelephonyManager → same
            // SystemService marker as the string lookup. No network.bluetooth /
            // network.wifi / network.links / android.location / android.audio /
            // android.inputMethods / android.sensors / android.power / android.thermal /
            // android.battery / android.configuration / android.securityState /
            // android.display / android.clipboard / android.accessibility /
            // android.userState / android.telephony
            // required here; getters remain
            // config-gated. Lookup itself emits no sidecar.
            case "android/app/Application->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;":
            case "android/content/Context->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;": {
                return resolveLimitedTypedSystemService(vm, signature, varArg.getObjectArg(0));
            }
            case "android/content/pm/PackageManager->getPackageInfo(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;": {
                StringObject packageName = varArg.getObjectArg(0);
                int flags = varArg.getIntArg(1);
                if (log.isDebugEnabled()) {
                    log.debug("getPackageInfo packageName={}, flags=0x{}", packageName.getValue(), Integer.toHexString(flags));
                }
                return new PackageInfo(vm, packageName.value, flags);
            }
            case "android/content/pm/PackageManager->getPackagesForUid(I)[Ljava/lang/String;": {
                String packageName = vm.getPackageName();
                if (packageName != null) {
                    return new ArrayObject(new StringObject(vm, packageName));
                }
                break;
            }
            case "android/content/pm/Signature->toByteArray()[B": {
                if (dvmObject instanceof Signature) {
                    Signature sig = (Signature) dvmObject;
                    return new ByteArray(vm, sig.toByteArray());
                }
                break;
            }
            case "android/content/pm/Signature->toCharsString()Ljava/lang/String;": {
                if (dvmObject instanceof Signature) {
                    Signature sig = (Signature) dvmObject;
                    return new StringObject(vm, sig.toCharsString());
                }
                break;
            }
            case "java/lang/Class->getName()Ljava/lang/String;": {
                DvmClass clazz = (DvmClass) dvmObject;
                return new StringObject(vm, clazz.getName());
            }
            case "java/lang/String->getClass()Ljava/lang/Class;":
            case "java/lang/Integer->getClass()Ljava/lang/Class;": {
                return dvmObject.getObjectType();
            }
            // 注: signature 参数仅用于调试日志，传 null 也可以
            case "java/lang/Class->getClassLoader()Ljava/lang/ClassLoader;":
                return new ClassLoader(vm, signature);
            case "java/io/File->getAbsolutePath()Ljava/lang/String;":
                File file = (File) dvmObject.getValue();
                return new StringObject(vm, file.getAbsolutePath());
            case "java/util/HashMap->keySet()Ljava/util/Set;":
                Map<?, ?> map = (Map<?, ?>) dvmObject.getValue();
                return vm.resolveClass("java/util/HashSet").newObject(map.keySet());
            case "java/io/ByteArrayOutputStream->toByteArray()[B":
                log.info("监控到 GZIP压缩后的数据 转字节数组");
                java.io.ByteArrayOutputStream baos = (java.io.ByteArrayOutputStream) dvmObject.getValue();
                byte[] gzipData = baos.toByteArray();
                return ProxyDvmObject.createObject(vm, gzipData);
            case "java/lang/Throwable->getStackTrace()[Ljava/lang/StackTraceElement;":
                // 获取异常的堆栈信息
                log.info("监控到 获取堆栈信息, 注意要获取正常堆栈!!! 注意x1a0f3n9这里可能补错了");
                /* 这部分代码有问题
                StackTraceElement[] elements = {
                        new StackTraceElement("java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1167)","","",0),
                        new StackTraceElement("java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:641)","","",0),
                        new StackTraceElement("java.lang.Thread.run(Thread.java:919)","","",0),
                };
                DvmObject<?>[] objs = new DvmObject[elements.length];
                for (int i = 0; i < elements.length; i++) {
                    objs[i] = vm.resolveClass("java/lang/StackTraceElement").newObject(elements[i]);
                }
                return new ArrayObject(objs);
                */
                Throwable throwable = (Throwable) dvmObject.getValue();
                if (throwable != null && throwable.getMessage() != null) {
                    log.info("Throwable message: {}", throwable.getMessage());
                }
                // 返回空数组，避免暴露真实的调用栈信息
                return ProxyDvmObject.createObject(vm, new StackTraceElement[0]);
            case "java/util/Map->get(Ljava/lang/Object;)Ljava/lang/Object;":
                Map<?, ?> map_temp = (Map<?, ?>) dvmObject.getValue();
                Object key = varArg.getObjectArg(0).getValue();
                return ProxyDvmObject.createObject(vm, map_temp.get(key));
            case "java/util/Map->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;":
                Map map_temp2 = (Map) dvmObject.getValue();
                Object key_2 = varArg.getObjectArg(0).getValue();
                Object value_2 = varArg.getObjectArg(1).getValue();
                return ProxyDvmObject.createObject(vm, map_temp2.put(key_2, value_2));

            // jdk1.8
            case "sun/security/pkcs/PKCS7->getCertificates()[Ljava/security/cert/X509Certificate;":
                try {
                    Object pkcs7 = dvmObject.getValue();
                    X509Certificate[] certificates = (X509Certificate[]) pkcs7.getClass().getMethod("getCertificates").invoke(pkcs7);
                    log.info("注意，这里 拦截到 SO 正在获取 APK 签名证书 (X509Certificate)，可能了为了检测apk签名！");
                    return ProxyDvmObject.createObject(vm, certificates);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to get certificates via reflection", e);
                }
        }


        throw new UnsupportedOperationException(signature);
    }

    @Override
    public int callIntMethod(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VarArg varArg) {
        return callIntMethod(vm, dvmObject, dvmMethod.getSignature(), varArg);
    }

    @Override
    public int callIntMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        log.info("callIntMethod [Unidbg]: {}", signature);
        AndroidTelephonyIntResult telephonyResult = tryAndroidTelephonyInt(vm, signature, varArg);
        if (telephonyResult.handled) {
            return telephonyResult.value;
        }
        AndroidWifiIntResult wifiResult = tryAndroidWifiInt(vm, signature);
        if (wifiResult.handled) {
            return wifiResult.value;
        }
        NetworkBluetoothIntResult bluetoothIntResult =
                tryNetworkBluetoothInt(vm, dvmObject, signature);
        if (bluetoothIntResult.handled) {
            return bluetoothIntResult.value;
        }
        AndroidNetworkLinkIntResult linkResult = tryAndroidNetworkLinkInt(vm, dvmObject, signature);
        if (linkResult.handled) {
            return linkResult.value;
        }
        AndroidPackageIntFieldResult permissionResult = tryAndroidPackageCheckPermission(vm, signature, varArg);
        if (permissionResult.handled) {
            return permissionResult.value;
        }
        AndroidPackageIntFieldResult teeKeyInfoInt = tryAndroidTeeKeyInfoIntMethod(vm, dvmObject, signature);
        if (teeKeyInfoInt.handled) {
            return teeKeyInfoInt.value;
        }
        AndroidDisplayIntFieldResult displayIntMethod = tryAndroidDisplayIntMethod(vm, dvmObject, signature);
        if (displayIntMethod.handled) {
            return displayIntMethod.value;
        }
        AndroidThermalIntResult thermalResult = tryAndroidThermalInt(vm, signature);
        if (thermalResult.handled) {
            return thermalResult.value;
        }
        AndroidBatteryIntResult batteryResult = tryAndroidBatteryInt(vm, signature, varArg);
        if (batteryResult.handled) {
            return batteryResult.value;
        }
        AndroidUiModeIntResult uiModeResult = tryAndroidUiModeInt(vm, dvmObject, signature);
        if (uiModeResult.handled) {
            return uiModeResult.value;
        }
        AndroidSecurityStateIntResult securityStateInt =
                tryAndroidSecurityStateInt(vm, signature, varArg);
        if (securityStateInt.handled) {
            return securityStateInt.value;
        }
        AndroidUserHandleIntResult userHandleInt = tryAndroidUserHandleInt(vm, dvmObject, signature);
        if (userHandleInt.handled) {
            return userHandleInt.value;
        }
        AndroidSensorIntResult sensorIntResult = tryAndroidSensorInt(vm, dvmObject, signature);
        if (sensorIntResult.handled) {
            return sensorIntResult.value;
        }
        NetworkInterfaceIntResult networkInterfaceIntResult =
                tryNetworkInterfaceInt(vm, dvmObject, signature);
        if (networkInterfaceIntResult.handled) {
            return networkInterfaceIntResult.value;
        }
        AndroidAudioIntResult audioIntResult = tryAndroidAudioInt(vm, dvmObject, signature, varArg);
        if (audioIntResult.handled) {
            return audioIntResult.value;
        }
        AndroidClipboardIntResult clipboardIntResult =
                tryAndroidClipboardIntMethod(vm, dvmObject, signature);
        if (clipboardIntResult.handled) {
            return clipboardIntResult.value;
        }
        AndroidRuntimeIntResult runtimeAvailableProcessors =
                tryAndroidRuntimeAvailableProcessors(vm, dvmObject, signature);
        if (runtimeAvailableProcessors.handled) {
            return runtimeAvailableProcessors.value;
        }
        AndroidLocaleIntResult localeIntResult = tryAndroidLocaleIntMethod(vm, dvmObject, signature, varArg);
        if (localeIntResult.handled) {
            return localeIntResult.value;
        }
        AndroidAppSetIdIntResult appSetIdIntResult = tryAndroidAppSetIdIntMethod(vm, dvmObject, signature);
        if (appSetIdIntResult.handled) {
            return appSetIdIntResult.value;
        }
        AndroidLocationIntResult locationIntResult =
                tryAndroidLocationInt(vm, dvmObject, signature);
        if (locationIntResult.handled) {
            return locationIntResult.value;
        }
        switch (signature) {
            case "java/lang/Integer->intValue()I":
                DvmInteger integer = (DvmInteger) dvmObject;
                return integer.value;
            case "java/io/InputStream->read([B)I": {
                try {
                    java.io.InputStream inputStream = (java.io.InputStream) dvmObject.getValue();
                    ByteArray array = varArg.getObjectArg(0);
                    return inputStream.read(array.getValue());
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            case "android/content/pm/Signature->hashCode()I": {
                if (dvmObject instanceof Signature) {
                    Signature sig = (Signature) dvmObject;
                    return sig.getHashCode();
                }
                break;
            }
            case "java/lang/String->hashCode()I":
                if (dvmObject.getValue() != null) {
                    return dvmObject.getValue().hashCode();
                }
                break;
            case "java/util/HashMap->size()I":
                if (dvmObject.getValue() != null) {
                    Map<?, ?> map_temp = (Map<?, ?>) dvmObject.getValue();
                    return map_temp.size();
                }
                break;
        }

        throw new UnsupportedOperationException(signature);
    }

    @Override
    public double callDoubleMethod(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VarArg varArg) {
        return callDoubleMethod(vm, dvmObject, dvmMethod.getSignature(), varArg);
    }

    @Override
    public double callDoubleMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        log.info("callDoubleMethod [Unidbg]: {}", signature);
        AndroidLocationDoubleResult locationDouble =
                tryAndroidLocationDouble(vm, dvmObject, signature);
        if (locationDouble.handled) {
            return locationDouble.value;
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void callVoidMethod(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VarArg varArg) {
        callVoidMethod(vm, dvmObject, dvmMethod.getSignature(), varArg);
    }

    @Override
    public void callVoidMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        log.info("callVoidMethod [Unidbg]: {}", signature);
        AndroidDisplayVoidResult displayVoid = tryAndroidDisplayVoidMethod(vm, dvmObject, signature, varArg);
        if (displayVoid.handled) {
            return;
        }
        switch (signature) {
            case "java/util/zip/GZIPOutputStream->write([B)V":
                log.info("[*] 监控到 GZIP 压缩流写入数据 - 这是压缩前的原始数据");
                java.util.zip.GZIPOutputStream gzipOutputStream = (java.util.zip.GZIPOutputStream) dvmObject.getValue();
                byte[] data = (byte[]) varArg.getObjectArg(0).getValue();
                try {
                    gzipOutputStream.write(data);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to write to GZIPOutputStream", e);
                }
                return;
            case "java/util/zip/GZIPOutputStream->finish()V":
                log.info("[*] 监控到 完成 GZIP 压缩");
                java.util.zip.GZIPOutputStream gzipStream = (java.util.zip.GZIPOutputStream) dvmObject.getValue();
                try {
                    gzipStream.finish();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to finish GZIPOutputStream", e);
                }
                return;
            case "java/util/zip/GZIPOutputStream->close()V":
                log.info("[*] 监控到 关闭 GZIP 压缩流");
                java.util.zip.GZIPOutputStream gzipOut = (java.util.zip.GZIPOutputStream) dvmObject.getValue();
                try {
                    gzipOut.close();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to close GZIPOutputStream", e);
                }
                return;
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void callVoidMethodV(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VaList vaList) {
        callVoidMethodV(vm, dvmObject, dvmMethod.getSignature(), vaList);
    }

    @Override
    public void callVoidMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        log.info("callVoidMethodV [Unidbg]: {}", signature);
        AndroidDisplayVoidResult displayVoid = tryAndroidDisplayVoidMethod(vm, dvmObject, signature, vaList);
        if (displayVoid.handled) {
            return;
        }
        switch (signature) {
            case "javax/crypto/Cipher->init(ILjava/security/Key;)V":
                Cipher cipher = (Cipher) dvmObject.getValue();
                int opmode = vaList.getIntArg(0);
                Key key = (Key) vaList.getObjectArg(1).getValue();
                assert key != null;
                try {
                    cipher.init(opmode, key);
                } catch (InvalidKeyException e) {
                    throw new IllegalStateException(e);
                }
                return;
            case "java/security/MessageDigest->update([B)V":
                MessageDigest messageDigest = (MessageDigest) dvmObject.getValue();
                byte[] message = (byte[]) vaList.getObjectArg(0).getValue();
                log.info("监控到哈希调用: {}, 输入字节数组: {} -> 十六进制为: {}", signature, message, bytesToHex(message));
                messageDigest.update(message);

                // 也可以用getIntArg获取Number类型的int，然后用vm.getObject获取DvmObject，然后getValue转为java对象;
                // int intArg = vaList.getIntArg(0);
                // Object object = vm.getObject(intArg).getValue();
                // messageDigest.update((byte[]) object);
                return;
            // StringBuilder 初始化 - allocObject 已创建实例
            case "java/lang/StringBuilder-><init>()V":
            case "java/util/Date-><init>()V":
                return;
            // IO 类初始化 - 真正创建实例
            case "java/io/BufferedReader-><init>(Ljava/io/Reader;)V": {
                DvmObject<?> reader = vaList.getObjectArg(0);
                if (reader != null && reader.getValue() instanceof java.io.Reader) {
                    dvmObject.setValue(new java.io.BufferedReader((java.io.Reader) reader.getValue()));
                }
                return;
            }
            case "java/io/BufferedReader->close()V": {
                Object value = dvmObject.getValue();
                if (value instanceof java.io.BufferedReader) {
                    try {
                        ((java.io.BufferedReader) value).close();
                    } catch (IOException ignored) {}
                }
                return;
            }
            case "java/io/FileReader-><init>(Ljava/lang/String;)V": {
                StringObject path = vaList.getObjectArg(0);
                if (path != null) {
                    try {
                        dvmObject.setValue(new java.io.FileReader(path.getValue()));
                    } catch (FileNotFoundException e) {
                        log.warn("FileReader file not found: {}", path.getValue());
                    }
                }
                return;
            }
            case "java/io/InputStreamReader-><init>(Ljava/io/InputStream;)V": {
                DvmObject<?> stream = vaList.getObjectArg(0);
                if (stream != null && stream.getValue() instanceof java.io.InputStream) {
                    dvmObject.setValue(new java.io.InputStreamReader((java.io.InputStream) stream.getValue()));
                }
                return;
            }
            case "java/io/FileInputStream-><init>(Ljava/lang/String;)V": {
                StringObject path = vaList.getObjectArg(0);
                if (path != null) {
                    try {
                        dvmObject.setValue(new java.io.FileInputStream(path.getValue()));
                    } catch (FileNotFoundException e) {
                        log.warn("FileInputStream file not found: {}", path.getValue());
                    }
                }
                return;
            }
            case "java/io/FileInputStream-><init>(Ljava/io/File;)V": {
                DvmObject<?> fileObj = vaList.getObjectArg(0);
                if (fileObj != null && fileObj.getValue() instanceof File) {
                    try {
                        dvmObject.setValue(new java.io.FileInputStream((File) fileObj.getValue()));
                    } catch (FileNotFoundException e) {
                        log.warn("FileInputStream file not found: {}", fileObj.getValue());
                    }
                }
                return;
            }
            case "java/io/File-><init>(Ljava/lang/String;)V": {
                StringObject path = vaList.getObjectArg(0);
                if (path != null) {
                    dvmObject.setValue(new File(path.getValue()));
                }
                return;
            }
        }
        throw new UnsupportedOperationException(signature);
    }

    protected static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b)); // 大写十六进制
        }
        return sb.toString();
    }

    @Override
    public void setStaticBooleanField(BaseVM vm, DvmClass dvmClass, DvmField dvmField, boolean value) {
        setStaticBooleanField(vm, dvmClass, dvmField.getSignature(), value);
    }

    @Override
    public void setStaticBooleanField(BaseVM vm, DvmClass dvmClass, String signature, boolean value) {
        log.info("setStaticBooleanField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void setStaticIntField(BaseVM vm, DvmClass dvmClass, DvmField dvmField, int value) {
        setStaticIntField(vm, dvmClass, dvmField.getSignature(), value);
    }

    @Override
    public void setStaticIntField(BaseVM vm, DvmClass dvmClass, String signature, int value) {
        log.info("setStaticIntField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    public void setStaticObjectField(BaseVM vm, DvmClass dvmClass, DvmField dvmField, DvmObject<?> value) {
        setStaticObjectField(vm, dvmClass, dvmField.getSignature(), value);
    }

    public void setStaticObjectField(BaseVM vm, DvmClass dvmClass, String signature, DvmObject<?> value) {
        log.info("setStaticObjectField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void setStaticLongField(BaseVM vm, DvmClass dvmClass, DvmField dvmField, long value) {
        setStaticLongField(vm, dvmClass, dvmField.getSignature(), value);
    }

    @Override
    public void setStaticLongField(BaseVM vm, DvmClass dvmClass, String signature, long value) {
        log.info("setStaticLongField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void setStaticFloatField(BaseVM vm, DvmClass dvmClass, DvmField dvmField, float value) {
        setStaticFloatField(vm, dvmClass, dvmField.getSignature(), value);
    }

    @Override
    public void setStaticFloatField(BaseVM vm, DvmClass dvmClass, String signature, float value) {
        log.info("setStaticFloatField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void setStaticDoubleField(BaseVM vm, DvmClass dvmClass, DvmField dvmField, double value) {
        setStaticDoubleField(vm, dvmClass, dvmField.getSignature(), value);
    }

    @Override
    public void setStaticDoubleField(BaseVM vm, DvmClass dvmClass, String signature, double value) {
        log.info("setStaticDoubleField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public long getStaticLongField(BaseVM vm, DvmClass dvmClass, DvmField dvmField) {
        return getStaticLongField(vm, dvmClass, dvmField.getSignature());
    }

    @Override
    public long getStaticLongField(BaseVM vm, DvmClass dvmClass, String signature) {
        log.info("getStaticLongField [Unidbg]: {}", signature);
        Long buildTime = tryAndroidBuildTime(vm, signature);
        if (buildTime != null) {
            return buildTime.longValue();
        }
        throw new UnsupportedOperationException(signature);
    }

    private static final class AndroidSettingsGetStringResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidSettingsGetStringResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidSettingsGetStringResult notHandled() {
            return new AndroidSettingsGetStringResult(false, null);
        }

        static AndroidSettingsGetStringResult of(DvmObject<?> value) {
            return new AndroidSettingsGetStringResult(true, value);
        }
    }

    private static final class AndroidSettingsGetIntResult {
        final boolean handled;
        final int value;

        private AndroidSettingsGetIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidSettingsGetIntResult notHandled() {
            return new AndroidSettingsGetIntResult(false, 0);
        }

        static AndroidSettingsGetIntResult of(int value) {
            return new AndroidSettingsGetIntResult(true, value);
        }
    }

    private static final class AndroidSettingsGetIntMatch {
        final String namespace;
        final boolean hasDefault;

        private AndroidSettingsGetIntMatch(String namespace, boolean hasDefault) {
            this.namespace = namespace;
            this.hasDefault = hasDefault;
        }
    }

    private static final class AndroidSettingsGetFloatResult {
        final boolean handled;
        final float value;

        private AndroidSettingsGetFloatResult(boolean handled, float value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidSettingsGetFloatResult notHandled() {
            return new AndroidSettingsGetFloatResult(false, 0f);
        }

        static AndroidSettingsGetFloatResult of(float value) {
            return new AndroidSettingsGetFloatResult(true, value);
        }
    }

    private static final class AndroidSettingsGetFloatMatch {
        final String namespace;
        final boolean hasDefault;

        private AndroidSettingsGetFloatMatch(String namespace, boolean hasDefault) {
            this.namespace = namespace;
            this.hasDefault = hasDefault;
        }
    }

    private static final class AndroidSettingsGetLongResult {
        final boolean handled;
        final long value;

        private AndroidSettingsGetLongResult(boolean handled, long value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidSettingsGetLongResult notHandled() {
            return new AndroidSettingsGetLongResult(false, 0L);
        }

        static AndroidSettingsGetLongResult of(long value) {
            return new AndroidSettingsGetLongResult(true, value);
        }
    }

    private static final class AndroidSettingsGetLongMatch {
        final String namespace;
        final boolean hasDefault;

        private AndroidSettingsGetLongMatch(String namespace, boolean hasDefault) {
            this.namespace = namespace;
            this.hasDefault = hasDefault;
        }
    }

    private static String androidSettingsNamespaceForGetString(String signature) {
        if ("android/provider/Settings$Secure->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;"
                .equals(signature)) {
            return "secure";
        }
        if ("android/provider/Settings$System->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;"
                .equals(signature)) {
            return "system";
        }
        if ("android/provider/Settings$Global->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;"
                .equals(signature)) {
            return "global";
        }
        return null;
    }

    private static AndroidSettingsGetIntMatch matchAndroidSettingsGetInt(String signature) {
        if ("android/provider/Settings$Secure->getInt(Landroid/content/ContentResolver;Ljava/lang/String;)I"
                .equals(signature)) {
            return new AndroidSettingsGetIntMatch("secure", false);
        }
        if ("android/provider/Settings$Secure->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I"
                .equals(signature)) {
            return new AndroidSettingsGetIntMatch("secure", true);
        }
        if ("android/provider/Settings$System->getInt(Landroid/content/ContentResolver;Ljava/lang/String;)I"
                .equals(signature)) {
            return new AndroidSettingsGetIntMatch("system", false);
        }
        if ("android/provider/Settings$System->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I"
                .equals(signature)) {
            return new AndroidSettingsGetIntMatch("system", true);
        }
        if ("android/provider/Settings$Global->getInt(Landroid/content/ContentResolver;Ljava/lang/String;)I"
                .equals(signature)) {
            return new AndroidSettingsGetIntMatch("global", false);
        }
        if ("android/provider/Settings$Global->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I"
                .equals(signature)) {
            return new AndroidSettingsGetIntMatch("global", true);
        }
        return null;
    }

    private static AndroidSettingsGetFloatMatch matchAndroidSettingsGetFloat(String signature) {
        if ("android/provider/Settings$Secure->getFloat(Landroid/content/ContentResolver;Ljava/lang/String;)F"
                .equals(signature)) {
            return new AndroidSettingsGetFloatMatch("secure", false);
        }
        if ("android/provider/Settings$Secure->getFloat(Landroid/content/ContentResolver;Ljava/lang/String;F)F"
                .equals(signature)) {
            return new AndroidSettingsGetFloatMatch("secure", true);
        }
        if ("android/provider/Settings$System->getFloat(Landroid/content/ContentResolver;Ljava/lang/String;)F"
                .equals(signature)) {
            return new AndroidSettingsGetFloatMatch("system", false);
        }
        if ("android/provider/Settings$System->getFloat(Landroid/content/ContentResolver;Ljava/lang/String;F)F"
                .equals(signature)) {
            return new AndroidSettingsGetFloatMatch("system", true);
        }
        if ("android/provider/Settings$Global->getFloat(Landroid/content/ContentResolver;Ljava/lang/String;)F"
                .equals(signature)) {
            return new AndroidSettingsGetFloatMatch("global", false);
        }
        if ("android/provider/Settings$Global->getFloat(Landroid/content/ContentResolver;Ljava/lang/String;F)F"
                .equals(signature)) {
            return new AndroidSettingsGetFloatMatch("global", true);
        }
        return null;
    }

    private static AndroidSettingsGetLongMatch matchAndroidSettingsGetLong(String signature) {
        if ("android/provider/Settings$Secure->getLong(Landroid/content/ContentResolver;Ljava/lang/String;)J"
                .equals(signature)) {
            return new AndroidSettingsGetLongMatch("secure", false);
        }
        if ("android/provider/Settings$Secure->getLong(Landroid/content/ContentResolver;Ljava/lang/String;J)J"
                .equals(signature)) {
            return new AndroidSettingsGetLongMatch("secure", true);
        }
        if ("android/provider/Settings$System->getLong(Landroid/content/ContentResolver;Ljava/lang/String;)J"
                .equals(signature)) {
            return new AndroidSettingsGetLongMatch("system", false);
        }
        if ("android/provider/Settings$System->getLong(Landroid/content/ContentResolver;Ljava/lang/String;J)J"
                .equals(signature)) {
            return new AndroidSettingsGetLongMatch("system", true);
        }
        if ("android/provider/Settings$Global->getLong(Landroid/content/ContentResolver;Ljava/lang/String;)J"
                .equals(signature)) {
            return new AndroidSettingsGetLongMatch("global", false);
        }
        if ("android/provider/Settings$Global->getLong(Landroid/content/ContentResolver;Ljava/lang/String;J)J"
                .equals(signature)) {
            return new AndroidSettingsGetLongMatch("global", true);
        }
        return null;
    }

    /**
     * {@code Settings.Secure.getString(resolver, "android_id")} when
     * {@code android.identifiers.androidId} is explicitly configured. Exact Secure getString
     * signature only; key must equal {@code android_id}. Missing config / other keys / invalid
     * key arg / wrong signature → notHandled (UOE path, no event, no host/default fallback).
     * Independent of {@code android.settings.secure.android_id}. Sidecar kind=
     * {@code android_identifier}, api={@code Settings.Secure.getString}.
     */
    private static AndroidSettingsGetStringResult tryAndroidIdentifiersAndroidIdSecureGetString(
            BaseVM vm, String signature, VarArg args) {
        if (!"android/provider/Settings$Secure->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;"
                .equals(signature)) {
            return AndroidSettingsGetStringResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidIdentifiersConfigured()) {
            return AndroidSettingsGetStringResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidIdentifiersConfig identifiers =
                config.getAndroidIdentifiersConfig();
        if (identifiers == null || !identifiers.isAndroidIdConfigured()) {
            return AndroidSettingsGetStringResult.notHandled();
        }
        DvmObject<?> keyArg = args.getObjectArg(1);
        if (!(keyArg instanceof StringObject)) {
            return AndroidSettingsGetStringResult.notHandled();
        }
        String key = ((StringObject) keyArg).getValue();
        if (!"android_id".equals(key)) {
            return AndroidSettingsGetStringResult.notHandled();
        }
        String androidId = identifiers.getAndroidId();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_identifier",
                "Settings.Secure.getString",
                "key=android_id,result=" + androidId,
                "json-config", "读取配置的 Android ID（Settings.Secure.android_id）");
        return AndroidSettingsGetStringResult.of(new StringObject(vm, androidId));
    }

    /**
     * Handles Settings.{Secure,System,Global}.getString when JSON configures the key.
     * Reads arg1 only after the signature is recognized as a Settings getString API.
     * Not handled when signature is unrelated or the setting is unconfigured (caller keeps default path).
     */
    private static AndroidSettingsGetStringResult tryAndroidSettingsGetString(BaseVM vm, String signature,
                                                                              VarArg args) {
        String namespace = androidSettingsNamespaceForGetString(signature);
        if (namespace == null) {
            return AndroidSettingsGetStringResult.notHandled();
        }
        DvmObject<?> keyArg = args.getObjectArg(1);
        if (!(keyArg instanceof StringObject)) {
            throw new IllegalArgumentException("signature=" + signature + ", arg1 must be StringObject, was=" + keyArg);
        }
        String key = ((StringObject) keyArg).getValue();
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidSettingConfigured(namespace, key)) {
            return AndroidSettingsGetStringResult.notHandled();
        }
        String value = config.getAndroidSettingString(namespace, key);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_setting",
                "Settings." + namespace + ".getString", key + "=" + String.valueOf(value),
                "json-config", "读取 Android 设置 " + key);
        if (value == null) {
            return AndroidSettingsGetStringResult.of(null);
        }
        return AndroidSettingsGetStringResult.of(new StringObject(vm, value));
    }

    /**
     * Handles Settings.{Secure,System,Global}.getInt when JSON configures the key.
     * Matches signature first (no-arg / non-settings methods must not read arg1).
     * Missing key always notHandled (even with default overload). Explicit JSON null with default
     * returns arg2; without default returns notHandled. Non-null string is Integer.parseInt.
     */
    private static AndroidSettingsGetIntResult tryAndroidSettingsGetInt(BaseVM vm, String signature, VarArg args) {
        AndroidSettingsGetIntMatch match = matchAndroidSettingsGetInt(signature);
        if (match == null) {
            return AndroidSettingsGetIntResult.notHandled();
        }
        DvmObject<?> keyArg = args.getObjectArg(1);
        if (!(keyArg instanceof StringObject)) {
            throw new IllegalArgumentException("signature=" + signature + ", arg1 must be StringObject, was=" + keyArg);
        }
        String key = ((StringObject) keyArg).getValue();
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidSettingConfigured(match.namespace, key)) {
            return AndroidSettingsGetIntResult.notHandled();
        }
        String rawValue = config.getAndroidSettingString(match.namespace, key);
        final int result;
        if (rawValue == null) {
            if (!match.hasDefault) {
                return AndroidSettingsGetIntResult.notHandled();
            }
            result = args.getIntArg(2);
        } else {
            try {
                result = Integer.parseInt(rawValue);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "namespace=" + match.namespace + ", key=" + key + ", value=" + rawValue, e);
            }
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_setting",
                "Settings." + match.namespace + ".getInt",
                "key=" + key + ",config=" + String.valueOf(rawValue) + ",result=" + result,
                "json-config", "读取 Android 整型设置 " + key);
        return AndroidSettingsGetIntResult.of(result);
    }

    /**
     * Handles Settings.{Secure,System,Global}.getFloat when JSON configures the key.
     * Matches signature first (no-arg / non-settings methods must not read arg1).
     * Missing key always notHandled (even with default overload). Explicit JSON null with default
     * returns arg2; without default returns notHandled. Non-null string is Float.parseFloat;
     * NaN/Infinite values are rejected.
     */
    private static AndroidSettingsGetFloatResult tryAndroidSettingsGetFloat(BaseVM vm, String signature, VarArg args) {
        AndroidSettingsGetFloatMatch match = matchAndroidSettingsGetFloat(signature);
        if (match == null) {
            return AndroidSettingsGetFloatResult.notHandled();
        }
        DvmObject<?> keyArg = args.getObjectArg(1);
        if (!(keyArg instanceof StringObject)) {
            throw new IllegalArgumentException("signature=" + signature + ", arg1 must be StringObject, was=" + keyArg);
        }
        String key = ((StringObject) keyArg).getValue();
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidSettingConfigured(match.namespace, key)) {
            return AndroidSettingsGetFloatResult.notHandled();
        }
        String rawValue = config.getAndroidSettingString(match.namespace, key);
        final float result;
        if (rawValue == null) {
            if (!match.hasDefault) {
                return AndroidSettingsGetFloatResult.notHandled();
            }
            result = args.getFloatArg(2);
        } else {
            final float parsed;
            try {
                parsed = Float.parseFloat(rawValue);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "namespace=" + match.namespace + ", key=" + key + ", value=" + rawValue, e);
            }
            if (Float.isNaN(parsed) || Float.isInfinite(parsed)) {
                throw new IllegalArgumentException(
                        "namespace=" + match.namespace + ", key=" + key + ", value=" + rawValue);
            }
            result = parsed;
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_setting",
                "Settings." + match.namespace + ".getFloat",
                "key=" + key + ",config=" + String.valueOf(rawValue) + ",result=" + result,
                "json-config", "读取 Android 浮点设置 " + key);
        return AndroidSettingsGetFloatResult.of(result);
    }

    /**
     * VaList entry for {@link #tryAndroidSettingsGetFloat(BaseVM, String, VarArg)}.
     * Delegates to the VarArg implementation ({@code VaList extends VarArg}) so signature
     * matching, arg1 validation, configured/missing/null/default behavior, finite Float
     * parsing, exception messages and sidecar are identical on both dispatch paths.
     */
    private static AndroidSettingsGetFloatResult tryAndroidSettingsGetFloat(BaseVM vm, String signature, VaList vaList) {
        return tryAndroidSettingsGetFloat(vm, signature, (VarArg) vaList);
    }

    /**
     * Handles Settings.{Secure,System,Global}.getLong when JSON configures the key.
     * Matches signature first (no-arg / non-settings methods must not read arg1).
     * Missing key always notHandled (even with default overload). Explicit JSON null with default
     * returns arg2; without default returns notHandled. Non-null string is Long.parseLong.
     */
    private static AndroidSettingsGetLongResult tryAndroidSettingsGetLong(BaseVM vm, String signature, VarArg args) {
        AndroidSettingsGetLongMatch match = matchAndroidSettingsGetLong(signature);
        if (match == null) {
            return AndroidSettingsGetLongResult.notHandled();
        }
        DvmObject<?> keyArg = args.getObjectArg(1);
        if (!(keyArg instanceof StringObject)) {
            throw new IllegalArgumentException("signature=" + signature + ", arg1 must be StringObject, was=" + keyArg);
        }
        String key = ((StringObject) keyArg).getValue();
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidSettingConfigured(match.namespace, key)) {
            return AndroidSettingsGetLongResult.notHandled();
        }
        String rawValue = config.getAndroidSettingString(match.namespace, key);
        final long result;
        if (rawValue == null) {
            if (!match.hasDefault) {
                return AndroidSettingsGetLongResult.notHandled();
            }
            result = args.getLongArg(2);
        } else {
            try {
                result = Long.parseLong(rawValue);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "namespace=" + match.namespace + ", key=" + key + ", value=" + rawValue, e);
            }
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_setting",
                "Settings." + match.namespace + ".getLong",
                "key=" + key + ",config=" + String.valueOf(rawValue) + ",result=" + result,
                "json-config", "读取 Android 长整型设置 " + key);
        return AndroidSettingsGetLongResult.of(result);
    }

    private static final class SystemClockLongResult {
        final boolean handled;
        final long value;

        private SystemClockLongResult(boolean handled, long value) {
            this.handled = handled;
            this.value = value;
        }

        static SystemClockLongResult notHandled() {
            return new SystemClockLongResult(false, 0L);
        }

        static SystemClockLongResult of(long value) {
            return new SystemClockLongResult(true, value);
        }
    }

    /**
     * Static SystemClock long getters when {@code time.monotonicNanos} is configured:
     * <ul>
     *   <li>{@code elapsedRealtime()J} → {@code monotonicNanos / 1_000_000L} (toward zero)</li>
     *   <li>{@code elapsedRealtimeNanos()J} → exact {@code monotonicNanos}</li>
     *   <li>{@code uptimeMillis()J} → same truncated millis as {@code elapsedRealtime}
     *       (deep sleep / sleep / clock advance are not modeled)</li>
     * </ul>
     * Missing config / wrong signature is notHandled (UOE path, no event).
     */
    private static SystemClockLongResult trySystemClockStaticLong(BaseVM vm, String signature) {
        final boolean elapsedRealtime =
                "android/os/SystemClock->elapsedRealtime()J".equals(signature);
        final boolean elapsedRealtimeNanos =
                "android/os/SystemClock->elapsedRealtimeNanos()J".equals(signature);
        final boolean uptimeMillis =
                "android/os/SystemClock->uptimeMillis()J".equals(signature);
        if (!elapsedRealtime && !elapsedRealtimeNanos && !uptimeMillis) {
            return SystemClockLongResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null) {
            return SystemClockLongResult.notHandled();
        }
        Long monotonicNanos = config.getMonotonicNanos();
        if (monotonicNanos == null) {
            return SystemClockLongResult.notHandled();
        }
        long nanos = monotonicNanos.longValue();
        if (elapsedRealtimeNanos) {
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "time",
                    "SystemClock.elapsedRealtimeNanos",
                    "monotonicNanos=" + nanos + ",resultNanos=" + nanos,
                    "json-config", "读取配置的已开机经过时间（纳秒）");
            return SystemClockLongResult.of(nanos);
        }
        long resultMillis = nanos / 1000000L;
        if (uptimeMillis) {
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "time",
                    "SystemClock.uptimeMillis",
                    "monotonicNanos=" + nanos + ",resultMillis=" + resultMillis,
                    "json-config", "读取配置的开机运行时间（毫秒；不建模深度休眠）");
            return SystemClockLongResult.of(resultMillis);
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "time",
                "SystemClock.elapsedRealtime",
                "monotonicNanos=" + nanos + ",resultMillis=" + resultMillis,
                "json-config", "读取配置的已开机经过时间（毫秒）");
        return SystemClockLongResult.of(resultMillis);
    }

    /**
     * Static {@code System.currentTimeMillis()J} when {@code time.currentTimeMillis} is configured.
     * Returns the exact configured long. Missing config / wrong signature / other System static
     * long APIs (e.g. {@code nanoTime}) are notHandled (UOE path, no event). Does not read
     * JVM or host time; does not implement {@code nanoTime} or other time APIs.
     */
    private static Long trySystemCurrentTimeMillisStaticLong(BaseVM vm, String signature) {
        if (!"java/lang/System->currentTimeMillis()J".equals(signature)) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null) {
            return null;
        }
        Long configured = config.getCurrentTimeMillis();
        if (configured == null) {
            return null;
        }
        long value = configured.longValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "time",
                "System.currentTimeMillis",
                "currentTimeMillis=" + value,
                "json-config", "读取配置的当前时间（毫秒）");
        return configured;
    }

    /**
     * Static {@code System.nanoTime()J} when {@code time.monotonicNanos} is configured.
     * Returns the exact configured long (not truncated to milliseconds). Missing config /
     * wrong signature / other System static long APIs going through this helper are
     * notHandled (UOE path, no event). Does not read JVM or host time; does not affect
     * {@code currentTimeMillis} or SystemClock APIs.
     */
    private static Long trySystemNanoTimeStaticLong(BaseVM vm, String signature) {
        if (!"java/lang/System->nanoTime()J".equals(signature)) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null) {
            return null;
        }
        Long configured = config.getMonotonicNanos();
        if (configured == null) {
            return null;
        }
        long value = configured.longValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "time",
                "System.nanoTime",
                "monotonicNanos=" + value,
                "json-config", "读取配置的单调时钟（纳秒）");
        return configured;
    }

    private static final class AndroidTelephonyIdentifierResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidTelephonyIdentifierResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidTelephonyIdentifierResult notHandled() {
            return new AndroidTelephonyIdentifierResult(false, null);
        }

        static AndroidTelephonyIdentifierResult of(DvmObject<?> value) {
            return new AndroidTelephonyIdentifierResult(true, value);
        }
    }

    private static final class AndroidTelephonyIdentifierMatch {
        final String methodName;
        final String configKey;
        final boolean hasSlotArg;

        private AndroidTelephonyIdentifierMatch(String methodName, String configKey, boolean hasSlotArg) {
            this.methodName = methodName;
            this.configKey = configKey;
            this.hasSlotArg = hasSlotArg;
        }
    }

    /**
     * Matches TelephonyManager slot-identifier getters. No-arg forms use slot 0;
     * (I) forms read slot from arg0 only after the signature is recognized.
     */
    private static AndroidTelephonyIdentifierMatch matchAndroidTelephonyIdentifier(String signature) {
        if ("android/telephony/TelephonyManager->getDeviceId()Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getDeviceId", "deviceId", false);
        }
        if ("android/telephony/TelephonyManager->getImei()Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getImei", "imei", false);
        }
        if ("android/telephony/TelephonyManager->getMeid()Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getMeid", "meid", false);
        }
        if ("android/telephony/TelephonyManager->getSubscriberId()Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getSubscriberId", "subscriberId", false);
        }
        if ("android/telephony/TelephonyManager->getSimSerialNumber()Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getSimSerialNumber", "simSerialNumber", false);
        }
        if ("android/telephony/TelephonyManager->getDeviceId(I)Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getDeviceId", "deviceId", true);
        }
        if ("android/telephony/TelephonyManager->getImei(I)Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getImei", "imei", true);
        }
        if ("android/telephony/TelephonyManager->getMeid(I)Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getMeid", "meid", true);
        }
        if ("android/telephony/TelephonyManager->getSubscriberId(I)Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getSubscriberId", "subscriberId", true);
        }
        if ("android/telephony/TelephonyManager->getSimSerialNumber(I)Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getSimSerialNumber", "simSerialNumber", true);
        }
        return null;
    }

    /**
     * Matches TelephonyManager global string getters (no-arg only).
     * Config keys: networkOperator|networkOperatorName|simOperator|simOperatorName|
     * networkCountryIso|simCountryIso.
     */
    private static AndroidTelephonyIdentifierMatch matchAndroidTelephonyGlobalString(String signature) {
        if ("android/telephony/TelephonyManager->getNetworkOperator()Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getNetworkOperator", "networkOperator", false);
        }
        if ("android/telephony/TelephonyManager->getNetworkOperatorName()Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getNetworkOperatorName", "networkOperatorName", false);
        }
        if ("android/telephony/TelephonyManager->getSimOperator()Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getSimOperator", "simOperator", false);
        }
        if ("android/telephony/TelephonyManager->getSimOperatorName()Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getSimOperatorName", "simOperatorName", false);
        }
        if ("android/telephony/TelephonyManager->getNetworkCountryIso()Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getNetworkCountryIso", "networkCountryIso", false);
        }
        if ("android/telephony/TelephonyManager->getSimCountryIso()Ljava/lang/String;".equals(signature)) {
            return new AndroidTelephonyIdentifierMatch("getSimCountryIso", "simCountryIso", false);
        }
        return null;
    }

    /**
     * Handles TelephonyManager slot-identifier and global string getters when JSON configures them.
     * Signature is matched before any arg is read. Explicit JSON null (operator strings) is handled
     * and returns Java null. {@code networkCountryIso} / {@code simCountryIso} never return null
     * when configured (empty string allowed). Missing key/slot/config is notHandled so the caller
     * keeps UOE.
     */
    private static AndroidTelephonyIdentifierResult tryAndroidTelephonyIdentifier(BaseVM vm, String signature,
                                                                                  VarArg args) {
        AndroidTelephonyIdentifierMatch slotMatch = matchAndroidTelephonyIdentifier(signature);
        if (slotMatch != null) {
            int slotIndex = slotMatch.hasSlotArg ? args.getIntArg(0) : 0;
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isTelephonySlotIdentifierConfigured(slotIndex, slotMatch.configKey)) {
                return AndroidTelephonyIdentifierResult.notHandled();
            }
            String value = config.getTelephonySlotIdentifier(slotIndex, slotMatch.configKey);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "telephony",
                    "TelephonyManager." + slotMatch.methodName,
                    "slot=" + slotIndex + ",key=" + slotMatch.configKey + ",value=" + String.valueOf(value),
                    "json-config", "读取电话卡槽标识 " + slotMatch.configKey + " (slot " + slotIndex + ")");
            if (value == null) {
                return AndroidTelephonyIdentifierResult.of(null);
            }
            return AndroidTelephonyIdentifierResult.of(new StringObject(vm, value));
        }

        AndroidTelephonyIdentifierMatch globalMatch = matchAndroidTelephonyGlobalString(signature);
        if (globalMatch == null) {
            return AndroidTelephonyIdentifierResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isTelephonyStringConfigured(globalMatch.configKey)) {
            return AndroidTelephonyIdentifierResult.notHandled();
        }
        String value = config.getTelephonyString(globalMatch.configKey);
        final boolean networkCountryIso = "networkCountryIso".equals(globalMatch.configKey);
        final boolean simCountryIso = "simCountryIso".equals(globalMatch.configKey);
        if (networkCountryIso || simCountryIso) {
            // configured country ISO is always a non-null String (empty or two letters)
            String result = value == null ? "" : value;
            String note = networkCountryIso ? "读取配置的网络国家代码" : "读取配置的 SIM 国家代码";
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "telephony",
                    "TelephonyManager." + globalMatch.methodName,
                    "key=" + globalMatch.configKey + ",result=" + result,
                    "json-config", note);
            return AndroidTelephonyIdentifierResult.of(new StringObject(vm, result));
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "telephony",
                "TelephonyManager." + globalMatch.methodName,
                "key=" + globalMatch.configKey + ",value=" + String.valueOf(value),
                "json-config", "读取电话运营商字段 " + globalMatch.configKey);
        if (value == null) {
            return AndroidTelephonyIdentifierResult.of(null);
        }
        return AndroidTelephonyIdentifierResult.of(new StringObject(vm, value));
    }

    private static final class AndroidTelephonyIntResult {
        final boolean handled;
        final int value;

        private AndroidTelephonyIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidTelephonyIntResult notHandled() {
            return new AndroidTelephonyIntResult(false, 0);
        }

        static AndroidTelephonyIntResult of(int value) {
            return new AndroidTelephonyIntResult(true, value);
        }
    }

    private static final class AndroidTelephonyBooleanResult {
        final boolean handled;
        final boolean value;

        private AndroidTelephonyBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidTelephonyBooleanResult notHandled() {
            return new AndroidTelephonyBooleanResult(false, false);
        }

        static AndroidTelephonyBooleanResult of(boolean value) {
            return new AndroidTelephonyBooleanResult(true, value);
        }
    }

    /**
     * TelephonyManager scalar int getters: phoneCount, dataNetworkType (also getNetworkType),
     * dataState, phoneType, simState. Signature is matched before any arg is read. Missing config
     * is notHandled (UOE path).
     */
    private static AndroidTelephonyIntResult tryAndroidTelephonyInt(BaseVM vm, String signature, VarArg args) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if ("android/telephony/TelephonyManager->getPhoneCount()I".equals(signature)) {
            if (config == null || !config.isTelephonyPhoneCountConfigured()) {
                return AndroidTelephonyIntResult.notHandled();
            }
            int value = config.getTelephonyPhoneCount(0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "telephony",
                    "TelephonyManager.getPhoneCount", String.valueOf(value),
                    "json-config", "读取电话卡槽数量");
            return AndroidTelephonyIntResult.of(value);
        }
        if ("android/telephony/TelephonyManager->getDataNetworkType()I".equals(signature)) {
            if (config == null || !config.isTelephonyIntConfigured("dataNetworkType")) {
                return AndroidTelephonyIntResult.notHandled();
            }
            int value = config.getTelephonyInt("dataNetworkType", 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "telephony",
                    "TelephonyManager.getDataNetworkType", String.valueOf(value),
                    "json-config", "读取数据网络类型");
            return AndroidTelephonyIntResult.of(value);
        }
        // Legacy alias of getDataNetworkType: same config key, distinct api/sidecar value format.
        if ("android/telephony/TelephonyManager->getNetworkType()I".equals(signature)) {
            if (config == null || !config.isTelephonyIntConfigured("dataNetworkType")) {
                return AndroidTelephonyIntResult.notHandled();
            }
            int value = config.getTelephonyInt("dataNetworkType", 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "telephony",
                    "TelephonyManager.getNetworkType",
                    "key=dataNetworkType,result=" + value,
                    "json-config", "读取配置的网络类型");
            return AndroidTelephonyIntResult.of(value);
        }
        if ("android/telephony/TelephonyManager->getDataState()I".equals(signature)) {
            if (config == null || !config.isTelephonyIntConfigured("dataState")) {
                return AndroidTelephonyIntResult.notHandled();
            }
            int value = config.getTelephonyInt("dataState", 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "telephony",
                    "TelephonyManager.getDataState",
                    "key=dataState,result=" + value,
                    "json-config", "读取配置的数据连接状态");
            return AndroidTelephonyIntResult.of(value);
        }
        if ("android/telephony/TelephonyManager->getDataActivity()I".equals(signature)) {
            if (config == null || !config.isTelephonyIntConfigured("dataActivity")) {
                return AndroidTelephonyIntResult.notHandled();
            }
            int value = config.getTelephonyInt("dataActivity", 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "telephony",
                    "TelephonyManager.getDataActivity",
                    "key=dataActivity,result=" + value,
                    "json-config", "读取配置的数据活动状态");
            return AndroidTelephonyIntResult.of(value);
        }
        if ("android/telephony/TelephonyManager->getPhoneType()I".equals(signature)) {
            if (config == null || !config.isTelephonyIntConfigured("phoneType")) {
                return AndroidTelephonyIntResult.notHandled();
            }
            int value = config.getTelephonyInt("phoneType", 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "telephony",
                    "TelephonyManager.getPhoneType", String.valueOf(value),
                    "json-config", "读取电话类型");
            return AndroidTelephonyIntResult.of(value);
        }
        if ("android/telephony/TelephonyManager->getSimState()I".equals(signature)) {
            if (config == null || !config.isTelephonySlotSimStateConfigured(0)) {
                return AndroidTelephonyIntResult.notHandled();
            }
            int value = config.getTelephonySlotSimState(0, 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "telephony",
                    "TelephonyManager.getSimState", "slot=0,value=" + value,
                    "json-config", "读取 SIM 状态 (slot 0)");
            return AndroidTelephonyIntResult.of(value);
        }
        if ("android/telephony/TelephonyManager->getSimState(I)I".equals(signature)) {
            int slotIndex = args.getIntArg(0);
            if (config == null || !config.isTelephonySlotSimStateConfigured(slotIndex)) {
                return AndroidTelephonyIntResult.notHandled();
            }
            int value = config.getTelephonySlotSimState(slotIndex, 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "telephony",
                    "TelephonyManager.getSimState", "slot=" + slotIndex + ",value=" + value,
                    "json-config", "读取 SIM 状态 (slot " + slotIndex + ")");
            return AndroidTelephonyIntResult.of(value);
        }
        return AndroidTelephonyIntResult.notHandled();
    }

    /**
     * TelephonyManager.isNetworkRoaming() when android.telephony.networkRoaming is configured.
     */
    private static AndroidTelephonyBooleanResult tryAndroidTelephonyBoolean(BaseVM vm, String signature) {
        if (!"android/telephony/TelephonyManager->isNetworkRoaming()Z".equals(signature)) {
            return AndroidTelephonyBooleanResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isTelephonyBooleanConfigured("networkRoaming")) {
            return AndroidTelephonyBooleanResult.notHandled();
        }
        boolean value = config.getTelephonyBoolean("networkRoaming", false);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "telephony",
                "TelephonyManager.isNetworkRoaming", String.valueOf(value),
                "json-config", "读取网络漫游状态");
        return AndroidTelephonyBooleanResult.of(value);
    }

    private static final class AndroidWifiObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidWifiObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidWifiObjectResult notHandled() {
            return new AndroidWifiObjectResult(false, null);
        }

        static AndroidWifiObjectResult of(DvmObject<?> value) {
            return new AndroidWifiObjectResult(true, value);
        }
    }

    private static final class AndroidWifiBooleanResult {
        final boolean handled;
        final boolean value;

        private AndroidWifiBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidWifiBooleanResult notHandled() {
            return new AndroidWifiBooleanResult(false, false);
        }

        static AndroidWifiBooleanResult of(boolean value) {
            return new AndroidWifiBooleanResult(true, value);
        }
    }

    /** True when any network.wifi field is present (including explicit null strings). */
    private static boolean isAnyWifiFieldConfigured(TraceEnvironmentConfig config) {
        if (config == null) {
            return false;
        }
        if (config.isWifiEnabledConfigured() || config.isWifiStateConfigured()) {
            return true;
        }
        if (config.isWifiStringConfigured("ssid")
                || config.isWifiStringConfigured("bssid")
                || config.isWifiStringConfigured("macAddress")
                || config.isWifiStringConfigured("ipv4")) {
            return true;
        }
        return config.isWifiIntConfigured("rssi")
                || config.isWifiIntConfigured("linkSpeedMbps")
                || config.isWifiIntConfigured("frequencyMhz")
                || config.isWifiIntConfigured("networkId");
    }

    /**
     * WifiManager.getConnectionInfo and WifiInfo string getters when network.wifi is configured.
     * Signature matched before any args. Explicit JSON null strings return Java null.
     */
    private static AndroidWifiObjectResult tryAndroidWifiObject(BaseVM vm, String signature) {
        if ("android/net/wifi/WifiManager->getConnectionInfo()Landroid/net/wifi/WifiInfo;".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (!isAnyWifiFieldConfigured(config)) {
                return AndroidWifiObjectResult.notHandled();
            }
            DvmObject<?> wifiInfo = vm.resolveClass("android/net/wifi/WifiInfo").newObject(null);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_wifi",
                    "WifiManager.getConnectionInfo", "WifiInfo",
                    "json-config", "返回配置的 WifiInfo 连接对象");
            return AndroidWifiObjectResult.of(wifiInfo);
        }
        String configKey = null;
        String methodName = null;
        if ("android/net/wifi/WifiInfo->getSSID()Ljava/lang/String;".equals(signature)) {
            configKey = "ssid";
            methodName = "getSSID";
        } else if ("android/net/wifi/WifiInfo->getBSSID()Ljava/lang/String;".equals(signature)) {
            configKey = "bssid";
            methodName = "getBSSID";
        } else if ("android/net/wifi/WifiInfo->getMacAddress()Ljava/lang/String;".equals(signature)) {
            configKey = "macAddress";
            methodName = "getMacAddress";
        }
        if (configKey == null) {
            return AndroidWifiObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isWifiStringConfigured(configKey)) {
            return AndroidWifiObjectResult.notHandled();
        }
        String value = config.getWifiString(configKey);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_wifi",
                "WifiInfo." + methodName,
                "key=" + configKey + ",value=" + String.valueOf(value),
                "json-config", "读取 Wi-Fi 字段 " + configKey);
        if (value == null) {
            return AndroidWifiObjectResult.of(null);
        }
        return AndroidWifiObjectResult.of(new StringObject(vm, value));
    }

    /**
     * WifiManager.isWifiEnabled() when network.wifi.enabled is configured.
     */
    private static AndroidWifiBooleanResult tryAndroidWifiBoolean(BaseVM vm, String signature) {
        if (!"android/net/wifi/WifiManager->isWifiEnabled()Z".equals(signature)) {
            return AndroidWifiBooleanResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isWifiEnabledConfigured()) {
            return AndroidWifiBooleanResult.notHandled();
        }
        boolean value = config.getWifiEnabled(false);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_wifi",
                "WifiManager.isWifiEnabled", String.valueOf(value),
                "json-config", "读取 Wi-Fi 开关状态");
        return AndroidWifiBooleanResult.of(value);
    }

    /**
     * Provenance marker for {@code BluetoothAdapter} from configured {@code network.bluetooth}.
     * Binds the creating {@link BaseVM}; instance getters require {@code owner ==} current VM.
     * Instance methods must use the configured path only.
     */
    private static final class ConfiguredBluetoothAdapter {
        final BaseVM owner;
        final TraceEnvironmentConfig.NetworkBluetoothConfig config;

        private ConfiguredBluetoothAdapter(BaseVM owner,
                                           TraceEnvironmentConfig.NetworkBluetoothConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    private static final class NetworkBluetoothObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private NetworkBluetoothObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static NetworkBluetoothObjectResult notHandled() {
            return new NetworkBluetoothObjectResult(false, null);
        }

        static NetworkBluetoothObjectResult of(DvmObject<?> value) {
            return new NetworkBluetoothObjectResult(true, value);
        }
    }

    private static final class NetworkBluetoothBooleanResult {
        final boolean handled;
        final boolean value;

        private NetworkBluetoothBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static NetworkBluetoothBooleanResult notHandled() {
            return new NetworkBluetoothBooleanResult(false, false);
        }

        static NetworkBluetoothBooleanResult of(boolean value) {
            return new NetworkBluetoothBooleanResult(true, value);
        }
    }

    private static final String BLUETOOTH_ADAPTER_CLASS = "android/bluetooth/BluetoothAdapter";
    private static final String BLUETOOTH_GET_DEFAULT_ADAPTER_SIGNATURE =
            "android/bluetooth/BluetoothAdapter->getDefaultAdapter()Landroid/bluetooth/BluetoothAdapter;";
    private static final String BLUETOOTH_MANAGER_GET_ADAPTER_SIGNATURE =
            "android/bluetooth/BluetoothManager->getAdapter()Landroid/bluetooth/BluetoothAdapter;";
    private static final String BLUETOOTH_GET_NAME_SIGNATURE =
            "android/bluetooth/BluetoothAdapter->getName()Ljava/lang/String;";
    private static final String BLUETOOTH_GET_ADDRESS_SIGNATURE =
            "android/bluetooth/BluetoothAdapter->getAddress()Ljava/lang/String;";
    private static final String BLUETOOTH_IS_ENABLED_SIGNATURE =
            "android/bluetooth/BluetoothAdapter->isEnabled()Z";
    private static final String BLUETOOTH_IS_DISCOVERING_SIGNATURE =
            "android/bluetooth/BluetoothAdapter->isDiscovering()Z";
    private static final String BLUETOOTH_GET_STATE_SIGNATURE =
            "android/bluetooth/BluetoothAdapter->getState()I";
    private static final String BLUETOOTH_GET_SCAN_MODE_SIGNATURE =
            "android/bluetooth/BluetoothAdapter->getScanMode()I";

    /**
     * True only when {@code dvmObject} holds a {@link ConfiguredBluetoothAdapter} created by
     * this {@code vm} (reference identity on {@code owner}, not class name). Cross-VM markers,
     * ordinary/foreign objects, and nulls are rejected.
     */
    private static boolean isConfiguredBluetoothAdapter(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredBluetoothAdapter)) {
            return false;
        }
        ConfiguredBluetoothAdapter marker = (ConfiguredBluetoothAdapter) dvmObject.getValue();
        return marker.owner == vm;
    }

    /** True when receiver is {@code SystemService} for {@code Context.BLUETOOTH_SERVICE}. */
    private static boolean isSystemServiceBluetoothManager(DvmObject<?> dvmObject) {
        return dvmObject instanceof SystemService
                && SystemService.BLUETOOTH_SERVICE.equals(dvmObject.getValue());
    }

    /**
     * Limited typed {@code Context/Application.getSystemService(Class)}: only accepts a
     * {@link DvmClass} for {@code android/bluetooth/BluetoothManager},
     * {@code android/net/wifi/WifiManager}, {@code android/net/ConnectivityManager},
     * {@code android/location/LocationManager}, {@code android/media/AudioManager},
     * {@code android/view/inputmethod/InputMethodManager},
     * {@code android/hardware/SensorManager},
     * {@code android/os/PowerManager},
     * {@code android/os/BatteryManager},
     * {@code android/app/UiModeManager},
     * {@code android/app/KeyguardManager},
     * {@code android/view/WindowManager},
     * {@code android/hardware/display/DisplayManager},
     * {@code android/content/ClipboardManager},
     * {@code android/view/accessibility/AccessibilityManager},
     * {@code android/os/UserManager},
     * or {@code android/telephony/TelephonyManager}
     * and returns the same {@link SystemService} marker as the corresponding string
     * service lookup. Not a general class-to-service registry. Null, non-DvmClass, and
     * any other class throw UOE (no sidecar). Does not require {@code network.bluetooth},
     * {@code network.wifi}, {@code network.links}, {@code android.location},
     * {@code android.audio}, {@code android.inputMethods}, {@code android.sensors},
     * {@code android.power}, {@code android.thermal}, {@code android.battery},
     * {@code android.configuration},
     * {@code android.securityState},
     * {@code android.display},
     * {@code android.clipboard},
     * {@code android.accessibility},
     * {@code android.userState},
     * or {@code android.telephony}.
     */
    private static DvmObject<?> resolveLimitedTypedSystemService(BaseVM vm, String signature,
                                                                 DvmObject<?> classArg) {
        if (!(classArg instanceof DvmClass)) {
            throw new UnsupportedOperationException(signature);
        }
        DvmClass requested = (DvmClass) classArg;
        String className = requested.getClassName();
        if ("android/bluetooth/BluetoothManager".equals(className)) {
            return new SystemService(vm, SystemService.BLUETOOTH_SERVICE);
        }
        if ("android/net/wifi/WifiManager".equals(className)) {
            return new SystemService(vm, SystemService.WIFI_SERVICE);
        }
        if ("android/net/ConnectivityManager".equals(className)) {
            return new SystemService(vm, SystemService.CONNECTIVITY_SERVICE);
        }
        if ("android/location/LocationManager".equals(className)) {
            return new SystemService(vm, SystemService.LOCATION_SERVICE);
        }
        if ("android/media/AudioManager".equals(className)) {
            return new SystemService(vm, SystemService.AUDIO_SERVICE);
        }
        if ("android/view/inputmethod/InputMethodManager".equals(className)) {
            return new SystemService(vm, SystemService.INPUT_METHOD_SERVICE);
        }
        if ("android/hardware/SensorManager".equals(className)) {
            return new SystemService(vm, SystemService.SENSOR_SERVICE);
        }
        if ("android/os/PowerManager".equals(className)) {
            return new SystemService(vm, SystemService.POWER_SERVICE);
        }
        if ("android/os/BatteryManager".equals(className)) {
            return new SystemService(vm, SystemService.BATTERY_SERVICE);
        }
        if ("android/app/UiModeManager".equals(className)) {
            return new SystemService(vm, SystemService.UI_MODE_SERVICE);
        }
        if ("android/app/KeyguardManager".equals(className)) {
            return new SystemService(vm, SystemService.KEYGUARD_SERVICE);
        }
        if ("android/view/WindowManager".equals(className)) {
            return new SystemService(vm, SystemService.WINDOW_SERVICE);
        }
        if ("android/hardware/display/DisplayManager".equals(className)) {
            return new SystemService(vm, SystemService.DISPLAY_SERVICE);
        }
        if ("android/content/ClipboardManager".equals(className)) {
            return new SystemService(vm, SystemService.CLIPBOARD_SERVICE);
        }
        if ("android/view/accessibility/AccessibilityManager".equals(className)) {
            return new SystemService(vm, SystemService.ACCESSIBILITY_SERVICE);
        }
        if ("android/os/UserManager".equals(className)) {
            return new SystemService(vm, SystemService.USER_SERVICE);
        }
        if ("android/telephony/TelephonyManager".equals(className)) {
            return new SystemService(vm, SystemService.TELEPHONY_SERVICE);
        }
        throw new UnsupportedOperationException(signature);
    }

    /**
     * Shared marker construction for {@code getDefaultAdapter} / {@code BluetoothManager.getAdapter}.
     * Requires at least one {@code network.bluetooth} field; otherwise notHandled (UOE, no event).
     */
    private static NetworkBluetoothObjectResult createConfiguredBluetoothAdapterMarker(
            BaseVM vm, String api, String note) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isNetworkBluetoothConfigured()) {
            return NetworkBluetoothObjectResult.notHandled();
        }
        TraceEnvironmentConfig.NetworkBluetoothConfig bluetoothConfig =
                config.getNetworkBluetoothConfig();
        if (bluetoothConfig == null || !bluetoothConfig.hasAnyFieldConfigured()) {
            return NetworkBluetoothObjectResult.notHandled();
        }
        DvmObject<?> adapter = vm.resolveClass(BLUETOOTH_ADAPTER_CLASS)
                .newObject(new ConfiguredBluetoothAdapter(vm, bluetoothConfig));
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_bluetooth",
                api,
                "BluetoothAdapter",
                "json-config", note);
        return NetworkBluetoothObjectResult.of(adapter);
    }

    /**
     * Static {@code BluetoothAdapter.getDefaultAdapter()} when at least one
     * {@code network.bluetooth} field is configured. Missing node / empty fields: notHandled
     * (UOE, no event). No scan/pairing/state inference.
     */
    private static NetworkBluetoothObjectResult tryNetworkBluetoothStaticObject(BaseVM vm,
                                                                                String signature) {
        if (!BLUETOOTH_GET_DEFAULT_ADAPTER_SIGNATURE.equals(signature)) {
            return NetworkBluetoothObjectResult.notHandled();
        }
        return createConfiguredBluetoothAdapterMarker(vm,
                "BluetoothAdapter.getDefaultAdapter",
                "返回配置的 BluetoothAdapter 标记对象");
    }

    /**
     * Instance object methods for bluetooth:
     * <ul>
     *   <li>{@code BluetoothManager.getAdapter()} only when receiver is SystemService
     *       {@code bluetooth} manager — same marker as getDefaultAdapter when any field configured</li>
     *   <li>{@code getName}/{@code getAddress} on ConfiguredBluetoothAdapter only</li>
     * </ul>
     * Missing field / non-service receiver / non-marker: notHandled (UOE, no event).
     */
    private static NetworkBluetoothObjectResult tryNetworkBluetoothObjectMethod(BaseVM vm,
                                                                                DvmObject<?> dvmObject,
                                                                                String signature) {
        if (BLUETOOTH_MANAGER_GET_ADAPTER_SIGNATURE.equals(signature)) {
            if (!isSystemServiceBluetoothManager(dvmObject)) {
                return NetworkBluetoothObjectResult.notHandled();
            }
            return createConfiguredBluetoothAdapterMarker(vm,
                    "BluetoothManager.getAdapter",
                    "通过 BluetoothManager 返回配置的 BluetoothAdapter 标记对象");
        }
        if (!isConfiguredBluetoothAdapter(vm, dvmObject)) {
            return NetworkBluetoothObjectResult.notHandled();
        }
        ConfiguredBluetoothAdapter marker = (ConfiguredBluetoothAdapter) dvmObject.getValue();
        TraceEnvironmentConfig.NetworkBluetoothConfig bluetoothConfig = marker.config;
        if (BLUETOOTH_GET_NAME_SIGNATURE.equals(signature)) {
            if (!bluetoothConfig.isNameConfigured()) {
                return NetworkBluetoothObjectResult.notHandled();
            }
            String value = bluetoothConfig.getName();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_bluetooth",
                    "BluetoothAdapter.getName",
                    "key=name,value=" + String.valueOf(value),
                    "json-config", "读取配置的蓝牙名称");
            if (value == null) {
                return NetworkBluetoothObjectResult.of(null);
            }
            return NetworkBluetoothObjectResult.of(new StringObject(vm, value));
        }
        if (BLUETOOTH_GET_ADDRESS_SIGNATURE.equals(signature)) {
            if (!bluetoothConfig.isAddressConfigured()) {
                return NetworkBluetoothObjectResult.notHandled();
            }
            String value = bluetoothConfig.getAddress();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_bluetooth",
                    "BluetoothAdapter.getAddress",
                    "key=address,value=" + String.valueOf(value),
                    "json-config", "读取配置的蓝牙 MAC 地址");
            if (value == null) {
                return NetworkBluetoothObjectResult.of(null);
            }
            return NetworkBluetoothObjectResult.of(new StringObject(vm, value));
        }
        // Marker objects only support the wired getters; unknown methods throw.
        throw new UnsupportedOperationException(signature);
    }

    /**
     * Instance boolean getters for ConfiguredBluetoothAdapter only:
     * <ul>
     *   <li>{@code isEnabled()Z} when {@code enabled} is configured (field absent → notHandled)</li>
     *   <li>{@code isDiscovering()Z} when marker present: configured value, or {@code false}
     *       if {@code discovering} key absent (not inferred from enabled/state)</li>
     * </ul>
     * Non-marker / missing node: notHandled (UOE, no event).
     */
    private static NetworkBluetoothBooleanResult tryNetworkBluetoothBoolean(BaseVM vm,
                                                                            DvmObject<?> dvmObject,
                                                                            String signature) {
        if (BLUETOOTH_IS_DISCOVERING_SIGNATURE.equals(signature)) {
            if (!isConfiguredBluetoothAdapter(vm, dvmObject)) {
                return NetworkBluetoothBooleanResult.notHandled();
            }
            ConfiguredBluetoothAdapter marker = (ConfiguredBluetoothAdapter) dvmObject.getValue();
            // Marker implies network.bluetooth present; default false when discovering key omitted.
            boolean value = marker.config.isDiscovering();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_bluetooth",
                    "BluetoothAdapter.isDiscovering",
                    "key=discovering,value=" + value,
                    "json-config", "读取配置的蓝牙扫描发现中状态（固定标记，缺省 false）");
            return NetworkBluetoothBooleanResult.of(value);
        }
        if (!BLUETOOTH_IS_ENABLED_SIGNATURE.equals(signature)) {
            return NetworkBluetoothBooleanResult.notHandled();
        }
        if (!isConfiguredBluetoothAdapter(vm, dvmObject)) {
            return NetworkBluetoothBooleanResult.notHandled();
        }
        ConfiguredBluetoothAdapter marker = (ConfiguredBluetoothAdapter) dvmObject.getValue();
        if (!marker.config.isEnabledConfigured()) {
            return NetworkBluetoothBooleanResult.notHandled();
        }
        boolean value = marker.config.isEnabled();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_bluetooth",
                "BluetoothAdapter.isEnabled",
                "key=enabled,value=" + value,
                "json-config", "读取配置的蓝牙开关状态");
        return NetworkBluetoothBooleanResult.of(value);
    }

    private static final class NetworkBluetoothIntResult {
        final boolean handled;
        final int value;

        private NetworkBluetoothIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static NetworkBluetoothIntResult notHandled() {
            return new NetworkBluetoothIntResult(false, 0);
        }

        static NetworkBluetoothIntResult of(int value) {
            return new NetworkBluetoothIntResult(true, value);
        }
    }

    /**
     * Instance {@code getState()I} / {@code getScanMode()I} for ConfiguredBluetoothAdapter only
     * when the exact field is configured. Fixed markers; not inferred from enabled. Missing
     * field / non-marker: notHandled (UOE, no event).
     */
    private static NetworkBluetoothIntResult tryNetworkBluetoothInt(BaseVM vm,
                                                                    DvmObject<?> dvmObject,
                                                                    String signature) {
        if (!isConfiguredBluetoothAdapter(vm, dvmObject)) {
            return NetworkBluetoothIntResult.notHandled();
        }
        ConfiguredBluetoothAdapter marker = (ConfiguredBluetoothAdapter) dvmObject.getValue();
        if (BLUETOOTH_GET_STATE_SIGNATURE.equals(signature)) {
            if (!marker.config.isStateConfigured()) {
                return NetworkBluetoothIntResult.notHandled();
            }
            int value = marker.config.getState();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_bluetooth",
                    "BluetoothAdapter.getState",
                    "key=state,value=" + value,
                    "json-config", "读取配置的蓝牙适配器状态（固定标记）");
            return NetworkBluetoothIntResult.of(value);
        }
        if (BLUETOOTH_GET_SCAN_MODE_SIGNATURE.equals(signature)) {
            if (!marker.config.isScanModeConfigured()) {
                return NetworkBluetoothIntResult.notHandled();
            }
            int value = marker.config.getScanMode();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_bluetooth",
                    "BluetoothAdapter.getScanMode",
                    "key=scanMode,value=" + value,
                    "json-config", "读取配置的蓝牙扫描模式（固定标记）");
            return NetworkBluetoothIntResult.of(value);
        }
        return NetworkBluetoothIntResult.notHandled();
    }

    private static final class AndroidPowerBooleanResult {
        final boolean handled;
        final boolean value;

        private AndroidPowerBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidPowerBooleanResult notHandled() {
            return new AndroidPowerBooleanResult(false, false);
        }

        static AndroidPowerBooleanResult of(boolean value) {
            return new AndroidPowerBooleanResult(true, value);
        }
    }

    /**
     * PowerManager.isInteractive / isScreenOn (both map to {@code interactive}) /
     * isPowerSaveMode / isDeviceIdleMode / isDeviceLightIdleMode / isLowPowerStandbyEnabled /
     * isSustainedPerformanceModeSupported / isRebootingUserspaceSupported (no-arg) and
     * isIgnoringBatteryOptimizations(String) when {@code android.power} is configured.
     * Exact signatures only; missing node is notHandled (UOE path, no event). Capability fields
     * are independent (never cross-mapped); does not simulate actual reboot.
     * {@code isIgnoringBatteryOptimizations} reads arg0 only after signature match; non-null
     * {@link StringObject} required, else notHandled.
     */
    private static AndroidPowerBooleanResult tryAndroidPowerBoolean(BaseVM vm, String signature,
                                                                    VarArg args) {
        if ("android/os/PowerManager->isIgnoringBatteryOptimizations(Ljava/lang/String;)Z"
                .equals(signature)) {
            return tryAndroidPowerIsIgnoringBatteryOptimizations(vm, args);
        }
        final String field;
        final String api;
        final String note;
        if ("android/os/PowerManager->isInteractive()Z".equals(signature)) {
            field = "interactive";
            api = "PowerManager.isInteractive";
            note = "读取配置的电源交互状态";
        } else if ("android/os/PowerManager->isScreenOn()Z".equals(signature)) {
            // Legacy alias of interactive (pre-API 20 isScreenOn)
            field = "interactive";
            api = "PowerManager.isScreenOn";
            note = "读取配置的屏幕点亮状态";
        } else if ("android/os/PowerManager->isPowerSaveMode()Z".equals(signature)) {
            field = "powerSaveMode";
            api = "PowerManager.isPowerSaveMode";
            note = "读取配置的省电模式状态";
        } else if ("android/os/PowerManager->isDeviceIdleMode()Z".equals(signature)) {
            field = "deviceIdleMode";
            api = "PowerManager.isDeviceIdleMode";
            note = "读取配置的设备空闲模式状态";
        } else if ("android/os/PowerManager->isDeviceLightIdleMode()Z".equals(signature)) {
            field = "deviceLightIdleMode";
            api = "PowerManager.isDeviceLightIdleMode";
            note = "读取配置的设备轻量空闲模式状态";
        } else if ("android/os/PowerManager->isLowPowerStandbyEnabled()Z".equals(signature)) {
            field = "lowPowerStandbyEnabled";
            api = "PowerManager.isLowPowerStandbyEnabled";
            note = "读取配置的低功耗待机启用状态";
        } else if ("android/os/PowerManager->isSustainedPerformanceModeSupported()Z".equals(signature)) {
            field = "sustainedPerformanceModeSupported";
            api = "PowerManager.isSustainedPerformanceModeSupported";
            note = "读取配置的持续性能模式支持状态";
        } else if ("android/os/PowerManager->isRebootingUserspaceSupported()Z".equals(signature)) {
            field = "rebootingUserspaceSupported";
            api = "PowerManager.isRebootingUserspaceSupported";
            note = "读取配置的用户空间重启支持（固定能力标记，不实际重启）";
        } else {
            return AndroidPowerBooleanResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidPowerConfigured()) {
            return AndroidPowerBooleanResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidPowerConfig powerConfig = config.getAndroidPowerConfig();
        final boolean value;
        if ("interactive".equals(field)) {
            value = powerConfig.isInteractive();
        } else if ("powerSaveMode".equals(field)) {
            value = powerConfig.isPowerSaveMode();
        } else if ("deviceIdleMode".equals(field)) {
            value = powerConfig.isDeviceIdleMode();
        } else if ("deviceLightIdleMode".equals(field)) {
            value = powerConfig.isDeviceLightIdleMode();
        } else if ("lowPowerStandbyEnabled".equals(field)) {
            value = powerConfig.isLowPowerStandbyEnabled();
        } else if ("sustainedPerformanceModeSupported".equals(field)) {
            value = powerConfig.isSustainedPerformanceModeSupported();
        } else {
            value = powerConfig.isRebootingUserspaceSupported();
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_power",
                api, "field=" + field + ",result=" + value,
                "json-config", note);
        return AndroidPowerBooleanResult.of(value);
    }

    /**
     * {@code PowerManager.isIgnoringBatteryOptimizations(String)} when {@code android.power}
     * is configured. Non-null {@link StringObject} arg0 required; returns configured
     * {@code ignoringBatteryOptimizations} only when the argument equals {@link BaseVM#getPackageName()},
     * otherwise {@code false}. Missing node / null / non-String arg → notHandled (no event).
     */
    private static AndroidPowerBooleanResult tryAndroidPowerIsIgnoringBatteryOptimizations(
            BaseVM vm, VarArg args) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidPowerConfigured()) {
            return AndroidPowerBooleanResult.notHandled();
        }
        DvmObject<?> packageArg = args.getObjectArg(0);
        if (!(packageArg instanceof StringObject)) {
            return AndroidPowerBooleanResult.notHandled();
        }
        String packageName = ((StringObject) packageArg).getValue();
        if (packageName == null) {
            return AndroidPowerBooleanResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidPowerConfig powerConfig = config.getAndroidPowerConfig();
        boolean value = packageName.equals(vm.getPackageName())
                && powerConfig.isIgnoringBatteryOptimizations();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_power",
                "PowerManager.isIgnoringBatteryOptimizations",
                "field=ignoringBatteryOptimizations,packageName=" + packageName + ",result=" + value,
                "json-config", "读取配置的电池优化忽略状态");
        return AndroidPowerBooleanResult.of(value);
    }

    private static final class AndroidUserManagerBooleanResult {
        final boolean handled;
        final boolean value;

        private AndroidUserManagerBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidUserManagerBooleanResult notHandled() {
            return new AndroidUserManagerBooleanResult(false, false);
        }

        static AndroidUserManagerBooleanResult of(boolean value) {
            return new AndroidUserManagerBooleanResult(true, value);
        }
    }

    private static final String USER_MANAGER_IS_USER_UNLOCKED_NO_ARG_SIGNATURE =
            "android/os/UserManager->isUserUnlocked()Z";
    private static final String USER_MANAGER_IS_USER_UNLOCKED_HANDLE_SIGNATURE =
            "android/os/UserManager->isUserUnlocked(Landroid/os/UserHandle;)Z";

    /**
     * UserManager.isUserUnlocked / isSystemUser / isManagedProfile / isDemoUser (no-arg) and
     * isUserUnlocked(UserHandle) when {@code android.userState} is configured.
     * No-arg semantics and sidecar values unchanged. Handle overload requires a
     * {@link ConfiguredUserHandle} owned by this {@link BaseVM} with matching config identity;
     * null/plain/foreign markers and missing node are notHandled (UOE, no event).
     */
    private static AndroidUserManagerBooleanResult tryAndroidUserManagerBoolean(BaseVM vm, String signature,
                                                                                VarArg args) {
        if (USER_MANAGER_IS_USER_UNLOCKED_HANDLE_SIGNATURE.equals(signature)) {
            return tryAndroidUserManagerIsUserUnlockedWithHandle(vm, args);
        }
        final String api;
        final String note;
        if (USER_MANAGER_IS_USER_UNLOCKED_NO_ARG_SIGNATURE.equals(signature)) {
            api = "UserManager.isUserUnlocked";
            note = "读取配置的用户解锁状态";
        } else if ("android/os/UserManager->isSystemUser()Z".equals(signature)) {
            api = "UserManager.isSystemUser";
            note = "读取配置的系统用户状态";
        } else if ("android/os/UserManager->isManagedProfile()Z".equals(signature)) {
            api = "UserManager.isManagedProfile";
            note = "读取配置的受管配置文件状态";
        } else if ("android/os/UserManager->isDemoUser()Z".equals(signature)) {
            api = "UserManager.isDemoUser";
            note = "读取配置的演示用户状态";
        } else {
            return AndroidUserManagerBooleanResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidUserStateConfigured()) {
            return AndroidUserManagerBooleanResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidUserStateConfig userState = config.getAndroidUserStateConfig();
        final boolean value;
        if ("UserManager.isUserUnlocked".equals(api)) {
            value = userState.isUserUnlocked();
        } else if ("UserManager.isSystemUser".equals(api)) {
            value = userState.isSystemUser();
        } else if ("UserManager.isManagedProfile".equals(api)) {
            value = userState.isManagedProfile();
        } else {
            value = userState.isDemoUser();
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_user",
                api, "result=" + value,
                "json-config", note);
        return AndroidUserManagerBooleanResult.of(value);
    }

    /**
     * {@code UserManager.isUserUnlocked(UserHandle)} when {@code android.userState} is configured
     * and arg0 is a {@link ConfiguredUserHandle} for this VM with matching config instance.
     */
    private static AndroidUserManagerBooleanResult tryAndroidUserManagerIsUserUnlockedWithHandle(
            BaseVM vm, VarArg args) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidUserStateConfigured()) {
            return AndroidUserManagerBooleanResult.notHandled();
        }
        DvmObject<?> handleArg = args.getObjectArg(0);
        if (!isConfiguredUserHandle(vm, handleArg)) {
            return AndroidUserManagerBooleanResult.notHandled();
        }
        ConfiguredUserHandle marker = (ConfiguredUserHandle) handleArg.getValue();
        TraceEnvironmentConfig.AndroidUserStateConfig userStateConfig = config.getAndroidUserStateConfig();
        if (marker.config != userStateConfig) {
            return AndroidUserManagerBooleanResult.notHandled();
        }
        boolean value = userStateConfig.isUserUnlocked();
        int userId = userStateConfig.getUserId();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_user",
                "UserManager.isUserUnlocked(UserHandle)",
                "userId=" + userId + ",result=" + value,
                "json-config", "读取 UserHandle 对应配置的用户解锁状态");
        return AndroidUserManagerBooleanResult.of(value);
    }

    private static final class GraphicsStringResult {
        final boolean handled;
        final DvmObject<?> value;

        private GraphicsStringResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static GraphicsStringResult notHandled() {
            return new GraphicsStringResult(false, null);
        }

        static GraphicsStringResult of(DvmObject<?> value) {
            return new GraphicsStringResult(true, value);
        }
    }

    private static final String GLES10_GL_GET_STRING =
            "android/opengl/GLES10->glGetString(I)Ljava/lang/String;";
    private static final String GLES20_GL_GET_STRING =
            "android/opengl/GLES20->glGetString(I)Ljava/lang/String;";
    private static final String GLES30_GL_GET_STRING =
            "android/opengl/GLES30->glGetString(I)Ljava/lang/String;";
    private static final String EGL14_EGL_QUERY_STRING =
            "android/opengl/EGL14->eglQueryString(Landroid/opengl/EGLDisplay;I)Ljava/lang/String;";
    private static final int GL_VENDOR = 0x1F00;
    private static final int GL_RENDERER = 0x1F01;
    private static final int GL_VERSION = 0x1F02;
    private static final int GL_EXTENSIONS = 0x1F03;
    private static final int GL_SHADING_LANGUAGE_VERSION = 0x8B8C;
    private static final int EGL_VENDOR = 0x3053;
    private static final int EGL_VERSION = 0x3054;
    private static final int EGL_EXTENSIONS = 0x3055;

    /**
     * Static GLES {@code glGetString(I)} / EGL {@code eglQueryString(EGLDisplay,I)} when
     * top-level {@code graphics} is present and the queried name has an explicit field.
     * Does not implement native libGLES/libEGL, contexts, surfaces, or Vulkan.
     * {@code eglQueryString} ignores the display argument (v1; no EGLDisplay object).
     */
    private static GraphicsStringResult tryGraphicsStaticString(BaseVM vm, String signature,
                                                                VarArg args) {
        final boolean gles = GLES10_GL_GET_STRING.equals(signature)
                || GLES20_GL_GET_STRING.equals(signature)
                || GLES30_GL_GET_STRING.equals(signature);
        final boolean egl = EGL14_EGL_QUERY_STRING.equals(signature);
        if (!gles && !egl) {
            return GraphicsStringResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isGraphicsConfigured()) {
            return GraphicsStringResult.notHandled();
        }
        TraceEnvironmentConfig.GraphicsConfig graphics = config.getGraphicsConfig();
        if (graphics == null) {
            return GraphicsStringResult.notHandled();
        }
        int name = gles ? args.getIntArg(0) : args.getIntArg(1);
        final String field;
        final String result;
        if (gles) {
            if (name == GL_VENDOR && graphics.isVendorConfigured()) {
                field = "vendor";
                result = graphics.getVendor();
            } else if (name == GL_RENDERER && graphics.isRendererConfigured()) {
                field = "renderer";
                result = graphics.getRenderer();
            } else if (name == GL_VERSION && graphics.isVersionConfigured()) {
                field = "version";
                result = graphics.getVersion();
            } else if (name == GL_SHADING_LANGUAGE_VERSION
                    && graphics.isShadingLanguageVersionConfigured()) {
                field = "shadingLanguageVersion";
                result = graphics.getShadingLanguageVersion();
            } else if (name == GL_EXTENSIONS && graphics.isExtensionsConfigured()) {
                field = "extensions";
                result = graphics.getExtensionsJoined();
            } else {
                return GraphicsStringResult.notHandled();
            }
        } else if (name == EGL_VENDOR && graphics.isEglVendorConfigured()) {
            field = "eglVendor";
            result = graphics.getEglVendor();
        } else if (name == EGL_VERSION && graphics.isEglVersionConfigured()) {
            field = "eglVersion";
            result = graphics.getEglVersion();
        } else if (name == EGL_EXTENSIONS && graphics.isEglExtensionsConfigured()) {
            field = "eglExtensions";
            result = graphics.getEglExtensionsJoined();
        } else {
            return GraphicsStringResult.notHandled();
        }
        if (result == null) {
            return GraphicsStringResult.notHandled();
        }
        final String api;
        if (GLES10_GL_GET_STRING.equals(signature)) {
            api = "GLES10.glGetString";
        } else if (GLES20_GL_GET_STRING.equals(signature)) {
            api = "GLES20.glGetString";
        } else if (GLES30_GL_GET_STRING.equals(signature)) {
            api = "GLES30.glGetString";
        } else {
            api = "EGL14.eglQueryString";
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "graphics", api,
                "name=" + name + ",field=" + field + ",resultLength=" + result.length(),
                "json-config", "读取配置的图形查询字符串");
        return GraphicsStringResult.of(new StringObject(vm, result));
    }

    private static final class AndroidThermalIntResult {
        final boolean handled;
        final int value;

        private AndroidThermalIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidThermalIntResult notHandled() {
            return new AndroidThermalIntResult(false, 0);
        }

        static AndroidThermalIntResult of(int value) {
            return new AndroidThermalIntResult(true, value);
        }
    }

    /**
     * PowerManager.getCurrentThermalStatus when {@code android.thermal} is configured.
     * Exact signature only; missing node is notHandled (UOE path, no event).
     * Does not alter {@code android.power} boolean dispatch.
     */
    private static AndroidThermalIntResult tryAndroidThermalInt(BaseVM vm, String signature) {
        if (!"android/os/PowerManager->getCurrentThermalStatus()I".equals(signature)) {
            return AndroidThermalIntResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidThermalConfigured()) {
            return AndroidThermalIntResult.notHandled();
        }
        int value = config.getAndroidThermalConfig().getCurrentThermalStatus();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_thermal",
                "PowerManager.getCurrentThermalStatus",
                "field=currentThermalStatus,result=" + value,
                "json-config", "读取配置的当前热状态");
        return AndroidThermalIntResult.of(value);
    }

    private static final class AndroidThermalFloatResult {
        final boolean handled;
        final float value;

        private AndroidThermalFloatResult(boolean handled, float value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidThermalFloatResult notHandled() {
            return new AndroidThermalFloatResult(false, 0f);
        }

        static AndroidThermalFloatResult of(float value) {
            return new AndroidThermalFloatResult(true, value);
        }
    }

    /**
     * PowerManager.getThermalHeadroom(I)F when {@code android.thermal} is configured.
     * Fixed-marker model: returns configured headroom regardless of forecastSeconds (arg0 is
     * recorded in sidecar only). Wired only via {@code callFloatMethodV} (no VarArg float path).
     * Missing node is notHandled (UOE path, no event). Does not alter getCurrentThermalStatus.
     */
    private static AndroidThermalFloatResult tryAndroidThermalFloat(BaseVM vm, String signature, VaList args) {
        if (!"android/os/PowerManager->getThermalHeadroom(I)F".equals(signature)) {
            return AndroidThermalFloatResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidThermalConfigured()) {
            return AndroidThermalFloatResult.notHandled();
        }
        int forecastSeconds = args.getIntArg(0);
        float value = config.getAndroidThermalConfig().getHeadroom();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_thermal",
                "PowerManager.getThermalHeadroom",
                "field=headroom,forecastSeconds=" + forecastSeconds + ",result=" + value,
                "json-config", "读取配置的热余量（固定标记，与 forecastSeconds 无关）");
        return AndroidThermalFloatResult.of(value);
    }

    private static final class AndroidBatteryBooleanResult {
        final boolean handled;
        final boolean value;

        private AndroidBatteryBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidBatteryBooleanResult notHandled() {
            return new AndroidBatteryBooleanResult(false, false);
        }

        static AndroidBatteryBooleanResult of(boolean value) {
            return new AndroidBatteryBooleanResult(true, value);
        }
    }

    private static final class AndroidCamerasIntResult {
        final boolean handled;
        final int value;

        private AndroidCamerasIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidCamerasIntResult notHandled() {
            return new AndroidCamerasIntResult(false, 0);
        }

        static AndroidCamerasIntResult of(int value) {
            return new AndroidCamerasIntResult(true, value);
        }
    }

    /**
     * Static {@code Camera.getNumberOfCameras()I} when {@code android.cameras} is configured.
     * Exact signature only; returns configured/default {@code count}. Missing node is notHandled
     * (UOE path, no event). Does not create Camera instances or implement camera2/CameraManager.
     */
    private static AndroidCamerasIntResult tryAndroidCamerasStaticInt(BaseVM vm, String signature) {
        if (!"android/hardware/Camera->getNumberOfCameras()I".equals(signature)) {
            return AndroidCamerasIntResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidCamerasConfigured()) {
            return AndroidCamerasIntResult.notHandled();
        }
        int value = config.getAndroidCamerasConfig().getCount();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_camera",
                "Camera.getNumberOfCameras",
                "field=count,result=" + value,
                "json-config", "读取配置的摄像头数量");
        return AndroidCamerasIntResult.of(value);
    }

    private static final class AndroidCamerasVoidResult {
        final boolean handled;

        private AndroidCamerasVoidResult(boolean handled) {
            this.handled = handled;
        }

        static AndroidCamerasVoidResult notHandled() {
            return new AndroidCamerasVoidResult(false);
        }

        static AndroidCamerasVoidResult handled() {
            return new AndroidCamerasVoidResult(true);
        }
    }

    /**
     * Same-VM provenance marker written onto {@code Camera$CameraInfo} by
     * {@code Camera.getCameraInfo}. Binds creating {@link BaseVM} and the exact
     * {@link TraceEnvironmentConfig.AndroidCameraInfoConfig} instance identity.
     */
    private static final class ConfiguredCameraInfo {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidCameraInfoConfig infoConfig;
        final int cameraId;

        private ConfiguredCameraInfo(BaseVM owner,
                                     TraceEnvironmentConfig.AndroidCameraInfoConfig infoConfig,
                                     int cameraId) {
            this.owner = owner;
            this.infoConfig = infoConfig;
            this.cameraId = cameraId;
        }
    }

    private static final String CAMERA_GET_CAMERA_INFO_SIGNATURE =
            "android/hardware/Camera->getCameraInfo(ILandroid/hardware/Camera$CameraInfo;)V";
    private static final String CAMERA_INFO_FACING_SIGNATURE =
            "android/hardware/Camera$CameraInfo->facing:I";
    private static final String CAMERA_INFO_ORIENTATION_SIGNATURE =
            "android/hardware/Camera$CameraInfo->orientation:I";
    private static final String CAMERA_INFO_CAN_DISABLE_SHUTTER_SOUND_SIGNATURE =
            "android/hardware/Camera$CameraInfo->canDisableShutterSound:Z";
    private static final String CAMERA_INFO_CLASS = "android/hardware/Camera$CameraInfo";

    /**
     * Static {@code Camera.getCameraInfo(ILandroid/hardware/Camera$CameraInfo;)V} when
     * {@code android.cameras} is configured and {@code infos} is present. Writes a private
     * same-VM {@link ConfiguredCameraInfo} marker onto the output CameraInfo object.
     * Invalid index / null / wrong output type / missing infos / missing node is notHandled
     * (UOE path, no event). Does not open Camera or implement camera2/CameraManager/permissions.
     */
    private static AndroidCamerasVoidResult tryAndroidCamerasStaticVoid(BaseVM vm, String signature,
                                                                        VarArg args) {
        if (!CAMERA_GET_CAMERA_INFO_SIGNATURE.equals(signature)) {
            return AndroidCamerasVoidResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidCamerasConfigured()) {
            return AndroidCamerasVoidResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidCamerasConfig cameras = config.getAndroidCamerasConfig();
        if (!cameras.isInfosConfigured()) {
            return AndroidCamerasVoidResult.notHandled();
        }
        List<TraceEnvironmentConfig.AndroidCameraInfoConfig> infos = cameras.getInfos();
        int cameraId = args.getIntArg(0);
        if (cameraId < 0 || cameraId >= infos.size()) {
            return AndroidCamerasVoidResult.notHandled();
        }
        DvmObject<?> out = args.getObjectArg(1);
        if (out == null || out.getObjectType() == null
                || !CAMERA_INFO_CLASS.equals(out.getObjectType().getClassName())) {
            return AndroidCamerasVoidResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidCameraInfoConfig info = infos.get(cameraId);
        out.setValue(new ConfiguredCameraInfo(vm, info, cameraId));
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_camera",
                "Camera.getCameraInfo",
                "cameraId=" + cameraId
                        + ",facing=" + info.getFacing()
                        + ",orientation=" + info.getOrientation(),
                "json-config", "将配置的摄像头信息写入 CameraInfo");
        return AndroidCamerasVoidResult.handled();
    }

    private static final class AndroidCameraInfoIntFieldResult {
        final boolean handled;
        final int value;

        private AndroidCameraInfoIntFieldResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidCameraInfoIntFieldResult notHandled() {
            return new AndroidCameraInfoIntFieldResult(false, 0);
        }

        static AndroidCameraInfoIntFieldResult of(int value) {
            return new AndroidCameraInfoIntFieldResult(true, value);
        }
    }

    /**
     * True only when {@code dvmObject} holds a live {@link ConfiguredCameraInfo} created by this
     * {@code vm} whose info config is still present in the current {@code android.cameras.infos}
     * list (reference identity).
     */
    private static boolean isLiveConfiguredCameraInfo(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredCameraInfo)) {
            return false;
        }
        ConfiguredCameraInfo marker = (ConfiguredCameraInfo) dvmObject.getValue();
        if (marker.owner != vm || marker.infoConfig == null) {
            return false;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidCamerasConfigured()) {
            return false;
        }
        TraceEnvironmentConfig.AndroidCamerasConfig cameras = config.getAndroidCamerasConfig();
        if (!cameras.isInfosConfigured()) {
            return false;
        }
        for (TraceEnvironmentConfig.AndroidCameraInfoConfig entry : cameras.getInfos()) {
            if (entry == marker.infoConfig) {
                return true;
            }
        }
        return false;
    }

    /**
     * Int fields for live {@link ConfiguredCameraInfo} only: {@code facing} and {@code orientation}.
     * Plain / foreign / stale marker is notHandled. Other CameraInfo field signatures on a live
     * marker throw UOE (no event).
     */
    private static AndroidCameraInfoIntFieldResult tryAndroidCameraInfoIntField(
            BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (!(dvmObject != null && dvmObject.getValue() instanceof ConfiguredCameraInfo)) {
            return AndroidCameraInfoIntFieldResult.notHandled();
        }
        if (!isLiveConfiguredCameraInfo(vm, dvmObject)) {
            return AndroidCameraInfoIntFieldResult.notHandled();
        }
        ConfiguredCameraInfo marker = (ConfiguredCameraInfo) dvmObject.getValue();
        TraceEnvironmentConfig.AndroidCameraInfoConfig info = marker.infoConfig;
        final int result;
        final String field;
        final String note;
        if (CAMERA_INFO_FACING_SIGNATURE.equals(signature)) {
            result = info.getFacing();
            field = "facing";
            note = "读取配置的摄像头朝向";
        } else if (CAMERA_INFO_ORIENTATION_SIGNATURE.equals(signature)) {
            result = info.getOrientation();
            field = "orientation";
            note = "读取配置的摄像头传感器方向";
        } else {
            throw new UnsupportedOperationException(signature);
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_camera",
                "CameraInfo." + field,
                "field=" + field + ",result=" + result,
                "json-config", note);
        return AndroidCameraInfoIntFieldResult.of(result);
    }

    private static final class AndroidCameraInfoBooleanFieldResult {
        final boolean handled;
        final boolean value;

        private AndroidCameraInfoBooleanFieldResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidCameraInfoBooleanFieldResult notHandled() {
            return new AndroidCameraInfoBooleanFieldResult(false, false);
        }

        static AndroidCameraInfoBooleanFieldResult of(boolean value) {
            return new AndroidCameraInfoBooleanFieldResult(true, value);
        }
    }

    /**
     * {@code Camera$CameraInfo.canDisableShutterSound:Z} for a live same-VM
     * {@link ConfiguredCameraInfo} only when that entry explicitly configured the field.
     * Absent key / plain / foreign / stale marker is notHandled (UOE path, no event).
     * Does not alter facing/orientation, Camera open, CameraManager/camera2, or permissions.
     */
    private static AndroidCameraInfoBooleanFieldResult tryAndroidCameraInfoBooleanField(
            BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (!CAMERA_INFO_CAN_DISABLE_SHUTTER_SOUND_SIGNATURE.equals(signature)) {
            return AndroidCameraInfoBooleanFieldResult.notHandled();
        }
        if (!isLiveConfiguredCameraInfo(vm, dvmObject)) {
            return AndroidCameraInfoBooleanFieldResult.notHandled();
        }
        ConfiguredCameraInfo marker = (ConfiguredCameraInfo) dvmObject.getValue();
        TraceEnvironmentConfig.AndroidCameraInfoConfig info = marker.infoConfig;
        if (!info.isCanDisableShutterSoundConfigured()) {
            return AndroidCameraInfoBooleanFieldResult.notHandled();
        }
        boolean result = info.getCanDisableShutterSound();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_camera",
                "CameraInfo.canDisableShutterSound",
                "field=canDisableShutterSound,result=" + result,
                "json-config", "读取配置的摄像头快门音关闭能力");
        return AndroidCameraInfoBooleanFieldResult.of(result);
    }

    /**
     * BatteryManager.isCharging()Z when {@code android.battery} is configured.
     * Exact signature only; missing node is notHandled (UOE path, no event).
     * Does not alter getIntProperty / getLongProperty or unrelated boolean handlers.
     */
    private static AndroidBatteryBooleanResult tryAndroidBatteryBoolean(BaseVM vm, String signature) {
        if (!"android/os/BatteryManager->isCharging()Z".equals(signature)) {
            return AndroidBatteryBooleanResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidBatteryConfigured()) {
            return AndroidBatteryBooleanResult.notHandled();
        }
        boolean value = config.getAndroidBatteryConfig().isCharging();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_battery",
                "BatteryManager.isCharging",
                "field=charging,result=" + value,
                "json-config", "读取配置的电池充电状态");
        return AndroidBatteryBooleanResult.of(value);
    }

    private static final class AndroidClipboardBooleanResult {
        final boolean handled;
        final boolean value;

        private AndroidClipboardBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidClipboardBooleanResult notHandled() {
            return new AndroidClipboardBooleanResult(false, false);
        }

        static AndroidClipboardBooleanResult of(boolean value) {
            return new AndroidClipboardBooleanResult(true, value);
        }
    }

    /** True when receiver is {@code SystemService} for {@code Context.CLIPBOARD_SERVICE}. */
    private static boolean isSystemServiceClipboardManager(DvmObject<?> dvmObject) {
        return dvmObject instanceof SystemService
                && SystemService.CLIPBOARD_SERVICE.equals(dvmObject.getValue());
    }

    /**
     * Exact boolean signatures for the clipboard subset:
     * <ul>
     *   <li>{@code ClipboardManager.hasPrimaryClip()Z} on the SystemService clipboard marker
     *       when {@code android.clipboard} is configured (sidecar)</li>
     *   <li>{@code ClipDescription.hasMimeType(String)} on a live same-VM
     *       {@link ConfiguredClipDescription}: {@code true} only for the three standard
     *       patterns that match the sole advertised {@code text/plain} type
     *       ({@code text/plain}, {@code text/*}, and the match-all {@code *}{@code /*}
     *       pattern); {@code false} for every other non-null String. No sidecar.</li>
     *   <li>{@code ClipDescription.isStyledText()Z} on a live same-VM
     *       {@link ConfiguredClipDescription}: always {@code false} because
     *       {@link ConfiguredClipData} is strictly plain {@link StringObject} text.
     *       No sidecar. No JSON field.</li>
     * </ul>
     * Missing node / plain / stale / cross-VM / null or non-String argument / wrong
     * signature or receiver is notHandled (UOE path, no event).
     * Does not implement setPrimaryClip / listeners / extras /
     * styled spans / URI / HTML / classification / writes / general MIME parsing /
     * static compareMimeTypes. {@code ClipDescription.getLabel()} is handled by
     * {@link #tryAndroidClipboardObjectMethod}. {@code ClipDescription.getTimestamp()}
     * is handled by {@link #tryAndroidClipboardLongMethod}.
     */
    private static AndroidClipboardBooleanResult tryAndroidClipboardBoolean(BaseVM vm,
                                                                            DvmObject<?> dvmObject,
                                                                            String signature,
                                                                            VarArg args) {
        if ("android/content/ClipboardManager->hasPrimaryClip()Z".equals(signature)) {
            if (!isSystemServiceClipboardManager(dvmObject)) {
                return AndroidClipboardBooleanResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidClipboardConfigured()) {
                return AndroidClipboardBooleanResult.notHandled();
            }
            boolean value = config.getAndroidClipboardConfig().hasPrimaryClip();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_clipboard",
                    "ClipboardManager.hasPrimaryClip",
                    "field=hasPrimaryClip,result=" + value,
                    "json-config", "读取配置的剪贴板是否有主内容");
            return AndroidClipboardBooleanResult.of(value);
        }
        if ("android/content/ClipDescription->hasMimeType(Ljava/lang/String;)Z"
                .equals(signature)) {
            if (!isLiveConfiguredClipDescription(vm, dvmObject)) {
                return AndroidClipboardBooleanResult.notHandled();
            }
            DvmObject<?> mimeArg = args.getObjectArg(0);
            if (!(mimeArg instanceof StringObject)) {
                return AndroidClipboardBooleanResult.notHandled();
            }
            String mimeType = ((StringObject) mimeArg).getValue();
            if (mimeType == null) {
                return AndroidClipboardBooleanResult.notHandled();
            }
            return AndroidClipboardBooleanResult.of(matchesConfiguredClipPlainTextMime(mimeType));
        }
        if ("android/content/ClipDescription->isStyledText()Z".equals(signature)) {
            if (!isLiveConfiguredClipDescription(vm, dvmObject)) {
                return AndroidClipboardBooleanResult.notHandled();
            }
            return AndroidClipboardBooleanResult.of(false);
        }
        return AndroidClipboardBooleanResult.notHandled();
    }

    /**
     * Provenance marker for {@code ClipData} from configured {@code android.clipboard.primaryText}.
     * Same-VM + config identity; not a host ClipData.
     */
    private static final class ConfiguredClipData {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidClipboardConfig config;

        private ConfiguredClipData(BaseVM owner,
                                   TraceEnvironmentConfig.AndroidClipboardConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    /**
     * Provenance marker for {@code ClipData.Item} from configured primary text clip.
     * Same-VM + config identity; not a host Item.
     */
    private static final class ConfiguredClipDataItem {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidClipboardConfig config;

        private ConfiguredClipDataItem(BaseVM owner,
                                       TraceEnvironmentConfig.AndroidClipboardConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    /**
     * Provenance marker for {@code ClipDescription} of the configured plain-text clip subset.
     * Same-VM + config identity; not a host ClipDescription.
     */
    private static final class ConfiguredClipDescription {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidClipboardConfig config;

        private ConfiguredClipDescription(BaseVM owner,
                                          TraceEnvironmentConfig.AndroidClipboardConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    /** {@code ClipDescription.MIMETYPE_TEXT_PLAIN} for the established primary-text subset. */
    private static final String CONFIGURED_CLIP_MIMETYPE_TEXT_PLAIN = "text/plain";
    /** Type-wildcard pattern that matches the sole advertised {@code text/plain} type. */
    private static final String CONFIGURED_CLIP_MIMETYPE_TEXT_WILDCARD = "text/*";
    /** Match-all MIME pattern that matches the sole advertised {@code text/plain} type. */
    private static final String CONFIGURED_CLIP_MIMETYPE_ANY = "*/*";

    /**
     * Exact equality against the three standard patterns that match the sole advertised
     * {@code text/plain} type. Not a general MIME parser and not static compareMimeTypes.
     */
    private static boolean matchesConfiguredClipPlainTextMime(String mimeType) {
        return CONFIGURED_CLIP_MIMETYPE_TEXT_PLAIN.equals(mimeType)
                || CONFIGURED_CLIP_MIMETYPE_TEXT_WILDCARD.equals(mimeType)
                || CONFIGURED_CLIP_MIMETYPE_ANY.equals(mimeType);
    }

    private static final class AndroidClipboardObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidClipboardObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidClipboardObjectResult notHandled() {
            return new AndroidClipboardObjectResult(false, null);
        }

        static AndroidClipboardObjectResult of(DvmObject<?> value) {
            return new AndroidClipboardObjectResult(true, value);
        }
    }

    private static final class AndroidClipboardIntResult {
        final boolean handled;
        final int value;

        private AndroidClipboardIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidClipboardIntResult notHandled() {
            return new AndroidClipboardIntResult(false, 0);
        }

        static AndroidClipboardIntResult of(int value) {
            return new AndroidClipboardIntResult(true, value);
        }
    }

    private static boolean isLiveConfiguredClipData(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredClipData)) {
            return false;
        }
        ConfiguredClipData marker = (ConfiguredClipData) dvmObject.getValue();
        if (marker.owner != vm || marker.config == null) {
            return false;
        }
        TraceEnvironmentConfig current = TraceEnvironmentConfig.get(vm.getEmulator());
        return current != null
                && current.isAndroidClipboardConfigured()
                && current.getAndroidClipboardConfig() == marker.config
                && marker.config.isPrimaryTextConfigured();
    }

    private static boolean isLiveConfiguredClipDataItem(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredClipDataItem)) {
            return false;
        }
        ConfiguredClipDataItem marker = (ConfiguredClipDataItem) dvmObject.getValue();
        if (marker.owner != vm || marker.config == null) {
            return false;
        }
        TraceEnvironmentConfig current = TraceEnvironmentConfig.get(vm.getEmulator());
        return current != null
                && current.isAndroidClipboardConfigured()
                && current.getAndroidClipboardConfig() == marker.config
                && marker.config.isPrimaryTextConfigured();
    }

    private static boolean isLiveConfiguredClipDescription(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredClipDescription)) {
            return false;
        }
        ConfiguredClipDescription marker = (ConfiguredClipDescription) dvmObject.getValue();
        if (marker.owner != vm || marker.config == null) {
            return false;
        }
        TraceEnvironmentConfig current = TraceEnvironmentConfig.get(vm.getEmulator());
        return current != null
                && current.isAndroidClipboardConfigured()
                && current.getAndroidClipboardConfig() == marker.config
                && marker.config.isPrimaryTextConfigured();
    }

    /**
     * Exact object signatures for text clipboard subset:
     * <ul>
     *   <li>{@code ClipboardManager.getPrimaryClip()} on SystemService clipboard when
     *       {@code primaryText} is configured → same-VM {@link ConfiguredClipData}</li>
     *   <li>{@code ClipData.getItemAt(I)} on live ClipData, index 0 only →
     *       {@link ConfiguredClipDataItem}</li>
     *   <li>{@code ClipData$Item.getText()} on live Item → configured primaryText StringObject</li>
     *   <li>{@code ClipData$Item.coerceToText(Context)} on live Item → configured
     *       primaryText StringObject. Context is not read, validated, or simulated
     *       (plain text is already present; no URI/Intent/HTML resolution). No sidecar.</li>
     *   <li>{@code ClipData.getDescription()} on live ClipData → fresh same-VM
     *       {@link ConfiguredClipDescription} (plain-text subset)</li>
     *   <li>{@code ClipDescription.getMimeType(I)} on live description, index 0 only →
     *       fresh StringObject {@code text/plain}</li>
     *   <li>{@code ClipDescription.getLabel()} on live description: configured
     *       {@code primaryLabel} → fresh same-VM StringObject; omitted key → Java null
     *       (handled). Never derived from {@code primaryText}. No sidecar.</li>
     * </ul>
     * Sidecar only for successful getPrimaryClip (no raw text). Metadata getters emit no sidecar.
     * Missing primaryText / plain / stale / cross-VM / invalid index / wrong signature →
     * notHandled (no event). Does not implement extras /
     * styled spans / coerceToHtmlText / URI / Intent / HTML / classification /
     * multiple MIME types / writes / general MIME parsing / static compareMimeTypes.
     * {@code hasMimeType(String)} and {@code isStyledText()} are handled by
     * {@link #tryAndroidClipboardBoolean}.
     * {@code getTimestamp()} is handled by {@link #tryAndroidClipboardLongMethod}.
     */
    private static AndroidClipboardObjectResult tryAndroidClipboardObjectMethod(BaseVM vm,
                                                                                DvmObject<?> dvmObject,
                                                                                String signature,
                                                                                VarArg args) {
        if ("android/content/ClipboardManager->getPrimaryClip()Landroid/content/ClipData;"
                .equals(signature)) {
            if (!isSystemServiceClipboardManager(dvmObject)) {
                return AndroidClipboardObjectResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidClipboardConfigured()) {
                return AndroidClipboardObjectResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidClipboardConfig clipConfig =
                    config.getAndroidClipboardConfig();
            if (clipConfig == null || !clipConfig.isPrimaryTextConfigured()) {
                return AndroidClipboardObjectResult.notHandled();
            }
            String text = clipConfig.getPrimaryText();
            if (text == null) {
                return AndroidClipboardObjectResult.notHandled();
            }
            DvmObject<?> clipData = vm.resolveClass("android/content/ClipData")
                    .newObject(new ConfiguredClipData(vm, clipConfig));
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_clipboard",
                    "ClipboardManager.getPrimaryClip",
                    "hasPrimaryClip=true,textLength=" + text.length(),
                    "json-config", "读取配置的剪贴板主文本 ClipData（不记录原文）");
            return AndroidClipboardObjectResult.of(clipData);
        }
        if ("android/content/ClipData->getItemAt(I)Landroid/content/ClipData$Item;"
                .equals(signature)) {
            if (!isLiveConfiguredClipData(vm, dvmObject)) {
                return AndroidClipboardObjectResult.notHandled();
            }
            int index = args.getIntArg(0);
            if (index != 0) {
                return AndroidClipboardObjectResult.notHandled();
            }
            ConfiguredClipData marker = (ConfiguredClipData) dvmObject.getValue();
            DvmObject<?> item = vm.resolveClass("android/content/ClipData$Item")
                    .newObject(new ConfiguredClipDataItem(vm, marker.config));
            return AndroidClipboardObjectResult.of(item);
        }
        if ("android/content/ClipData$Item->getText()Ljava/lang/CharSequence;"
                .equals(signature)) {
            if (!isLiveConfiguredClipDataItem(vm, dvmObject)) {
                return AndroidClipboardObjectResult.notHandled();
            }
            ConfiguredClipDataItem marker = (ConfiguredClipDataItem) dvmObject.getValue();
            String text = marker.config.getPrimaryText();
            if (text == null) {
                return AndroidClipboardObjectResult.notHandled();
            }
            return AndroidClipboardObjectResult.of(new StringObject(vm, text));
        }
        if ("android/content/ClipData$Item->coerceToText(Landroid/content/Context;)Ljava/lang/CharSequence;"
                .equals(signature)) {
            if (!isLiveConfiguredClipDataItem(vm, dvmObject)) {
                return AndroidClipboardObjectResult.notHandled();
            }
            ConfiguredClipDataItem marker = (ConfiguredClipDataItem) dvmObject.getValue();
            String text = marker.config.getPrimaryText();
            if (text == null) {
                return AndroidClipboardObjectResult.notHandled();
            }
            return AndroidClipboardObjectResult.of(new StringObject(vm, text));
        }
        if ("android/content/ClipData->getDescription()Landroid/content/ClipDescription;"
                .equals(signature)) {
            if (!isLiveConfiguredClipData(vm, dvmObject)) {
                return AndroidClipboardObjectResult.notHandled();
            }
            ConfiguredClipData marker = (ConfiguredClipData) dvmObject.getValue();
            DvmObject<?> description = vm.resolveClass("android/content/ClipDescription")
                    .newObject(new ConfiguredClipDescription(vm, marker.config));
            return AndroidClipboardObjectResult.of(description);
        }
        if ("android/content/ClipDescription->getMimeType(I)Ljava/lang/String;"
                .equals(signature)) {
            if (!isLiveConfiguredClipDescription(vm, dvmObject)) {
                return AndroidClipboardObjectResult.notHandled();
            }
            int index = args.getIntArg(0);
            if (index != 0) {
                return AndroidClipboardObjectResult.notHandled();
            }
            return AndroidClipboardObjectResult.of(
                    new StringObject(vm, CONFIGURED_CLIP_MIMETYPE_TEXT_PLAIN));
        }
        if ("android/content/ClipDescription->getLabel()Ljava/lang/CharSequence;"
                .equals(signature)) {
            if (!isLiveConfiguredClipDescription(vm, dvmObject)) {
                return AndroidClipboardObjectResult.notHandled();
            }
            ConfiguredClipDescription marker = (ConfiguredClipDescription) dvmObject.getValue();
            if (!marker.config.isPrimaryLabelConfigured()) {
                return AndroidClipboardObjectResult.of(null);
            }
            String label = marker.config.getPrimaryLabel();
            if (label == null) {
                return AndroidClipboardObjectResult.of(null);
            }
            return AndroidClipboardObjectResult.of(new StringObject(vm, label));
        }
        return AndroidClipboardObjectResult.notHandled();
    }

    /**
     * Int getters on live same-VM configured clip markers only:
     * {@code ClipData.getItemCount()I} → {@code 1};
     * {@code ClipDescription.getMimeTypeCount()I} → {@code 1}.
     * No sidecar. Plain/stale/cross-VM/missing primaryText/wrong signature → notHandled.
     */
    private static AndroidClipboardIntResult tryAndroidClipboardIntMethod(BaseVM vm,
                                                                          DvmObject<?> dvmObject,
                                                                          String signature) {
        if ("android/content/ClipData->getItemCount()I".equals(signature)) {
            if (!isLiveConfiguredClipData(vm, dvmObject)) {
                return AndroidClipboardIntResult.notHandled();
            }
            return AndroidClipboardIntResult.of(1);
        }
        if ("android/content/ClipDescription->getMimeTypeCount()I".equals(signature)) {
            if (!isLiveConfiguredClipDescription(vm, dvmObject)) {
                return AndroidClipboardIntResult.notHandled();
            }
            return AndroidClipboardIntResult.of(1);
        }
        return AndroidClipboardIntResult.notHandled();
    }

    private static final class AndroidClipboardLongResult {
        final boolean handled;
        final long value;

        private AndroidClipboardLongResult(boolean handled, long value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidClipboardLongResult notHandled() {
            return new AndroidClipboardLongResult(false, 0L);
        }

        static AndroidClipboardLongResult of(long value) {
            return new AndroidClipboardLongResult(true, value);
        }
    }

    /**
     * Exact {@code ClipDescription.getTimestamp()J} on a live same-VM
     * {@link ConfiguredClipDescription}. Configured {@code timestampMillis} is returned
     * exactly; omitted key returns stable primitive {@code 0} (Android not-copied / no
     * timestamp). Never derived from {@code primaryText} or host time. No sidecar.
     * Plain/stale/cross-VM/missing primaryText/wrong signature → notHandled.
     */
    private static AndroidClipboardLongResult tryAndroidClipboardLongMethod(BaseVM vm,
                                                                            DvmObject<?> dvmObject,
                                                                            String signature) {
        if ("android/content/ClipDescription->getTimestamp()J".equals(signature)) {
            if (!isLiveConfiguredClipDescription(vm, dvmObject)) {
                return AndroidClipboardLongResult.notHandled();
            }
            ConfiguredClipDescription marker = (ConfiguredClipDescription) dvmObject.getValue();
            if (marker.config.isTimestampMillisConfigured()) {
                return AndroidClipboardLongResult.of(marker.config.getTimestampMillis());
            }
            return AndroidClipboardLongResult.of(0L);
        }
        return AndroidClipboardLongResult.notHandled();
    }

    private static final class AndroidAccessibilityBooleanResult {
        final boolean handled;
        final boolean value;

        private AndroidAccessibilityBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidAccessibilityBooleanResult notHandled() {
            return new AndroidAccessibilityBooleanResult(false, false);
        }

        static AndroidAccessibilityBooleanResult of(boolean value) {
            return new AndroidAccessibilityBooleanResult(true, value);
        }
    }

    /** True when receiver is {@code SystemService} for {@code Context.ACCESSIBILITY_SERVICE}. */
    private static boolean isSystemServiceAccessibilityManager(DvmObject<?> dvmObject) {
        return dvmObject instanceof SystemService
                && SystemService.ACCESSIBILITY_SERVICE.equals(dvmObject.getValue());
    }

    /**
     * AccessibilityManager boolean getters on the SystemService accessibility marker when
     * {@code android.accessibility} is configured. Exact signatures only:
     * <ul>
     *   <li>{@code isEnabled()Z} → fixed {@code enabled}</li>
     *   <li>{@code isTouchExplorationEnabled()Z} → fixed {@code touchExplorationEnabled}
     *       (independent of enabled / highContrastTextEnabled)</li>
     *   <li>{@code isHighContrastTextEnabled()Z} → fixed {@code highContrastTextEnabled}
     *       (independent of enabled / touchExplorationEnabled)</li>
     * </ul>
     * Missing node / plain AccessibilityManager / other SystemService is notHandled (UOE path,
     * no event). Does not change getEnabledAccessibilityServiceList fixed empty list, and does not
     * implement services, listeners, callbacks, captions, or other APIs.
     */
    private static AndroidAccessibilityBooleanResult tryAndroidAccessibilityBoolean(BaseVM vm,
                                                                                    DvmObject<?> dvmObject,
                                                                                    String signature) {
        final boolean isEnabledSig =
                "android/view/accessibility/AccessibilityManager->isEnabled()Z".equals(signature);
        final boolean isTouchExplorationSig =
                "android/view/accessibility/AccessibilityManager->isTouchExplorationEnabled()Z"
                        .equals(signature);
        final boolean isHighContrastTextSig =
                "android/view/accessibility/AccessibilityManager->isHighContrastTextEnabled()Z"
                        .equals(signature);
        if (!isEnabledSig && !isTouchExplorationSig && !isHighContrastTextSig) {
            return AndroidAccessibilityBooleanResult.notHandled();
        }
        if (!isSystemServiceAccessibilityManager(dvmObject)) {
            return AndroidAccessibilityBooleanResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidAccessibilityConfigured()) {
            return AndroidAccessibilityBooleanResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidAccessibilityConfig acc = config.getAndroidAccessibilityConfig();
        if (isEnabledSig) {
            boolean value = acc.isEnabled();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_accessibility",
                    "AccessibilityManager.isEnabled",
                    "field=enabled,result=" + value,
                    "json-config", "读取配置的无障碍是否启用");
            return AndroidAccessibilityBooleanResult.of(value);
        }
        if (isTouchExplorationSig) {
            boolean value = acc.isTouchExplorationEnabled();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_accessibility",
                    "AccessibilityManager.isTouchExplorationEnabled",
                    "field=touchExplorationEnabled,result=" + value,
                    "json-config", "读取配置的触摸探索是否启用");
            return AndroidAccessibilityBooleanResult.of(value);
        }
        boolean value = acc.isHighContrastTextEnabled();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_accessibility",
                "AccessibilityManager.isHighContrastTextEnabled",
                "field=highContrastTextEnabled,result=" + value,
                "json-config", "读取配置的高对比度文本是否启用");
        return AndroidAccessibilityBooleanResult.of(value);
    }

    private static final class AndroidAudioBooleanResult {
        final boolean handled;
        final boolean value;

        private AndroidAudioBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidAudioBooleanResult notHandled() {
            return new AndroidAudioBooleanResult(false, false);
        }

        static AndroidAudioBooleanResult of(boolean value) {
            return new AndroidAudioBooleanResult(true, value);
        }
    }

    /** True when receiver is {@code SystemService} for {@code Context.AUDIO_SERVICE}. */
    private static boolean isSystemServiceAudioManager(DvmObject<?> dvmObject) {
        return dvmObject instanceof SystemService
                && SystemService.AUDIO_SERVICE.equals(dvmObject.getValue());
    }

    /**
     * AudioManager boolean getters on the SystemService audio marker when {@code android.audio}
     * is configured. Exact signatures only:
     * <ul>
     *   <li>{@code isMusicActive()Z} → fixed {@code musicActive}</li>
     *   <li>{@code isSpeakerphoneOn()Z} → fixed {@code speakerphoneOn} (independent of musicActive)</li>
     * </ul>
     * Missing node / plain AudioManager / other SystemService is notHandled (UOE path, no event).
     * Does not implement setters / routing / volumes / devices / playback / focus / callbacks
     * ({@code getProperty} is handled separately).
     */
    private static AndroidAudioBooleanResult tryAndroidAudioBoolean(BaseVM vm,
                                                                    DvmObject<?> dvmObject,
                                                                    String signature) {
        final boolean musicActive =
                "android/media/AudioManager->isMusicActive()Z".equals(signature);
        final boolean speakerphoneOn =
                "android/media/AudioManager->isSpeakerphoneOn()Z".equals(signature);
        if (!musicActive && !speakerphoneOn) {
            return AndroidAudioBooleanResult.notHandled();
        }
        if (!isSystemServiceAudioManager(dvmObject)) {
            return AndroidAudioBooleanResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidAudioConfigured()) {
            return AndroidAudioBooleanResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidAudioConfig audio = config.getAndroidAudioConfig();
        if (musicActive) {
            boolean value = audio.isMusicActive();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_audio",
                    "AudioManager.isMusicActive",
                    "field=musicActive,result=" + value,
                    "json-config", "读取配置的音乐是否正在播放");
            return AndroidAudioBooleanResult.of(value);
        }
        boolean value = audio.isSpeakerphoneOn();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_audio",
                "AudioManager.isSpeakerphoneOn",
                "field=speakerphoneOn,result=" + value,
                "json-config", "读取配置的扬声器是否开启");
        return AndroidAudioBooleanResult.of(value);
    }

    private static final class AndroidAudioIntResult {
        final boolean handled;
        final int value;

        private AndroidAudioIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidAudioIntResult notHandled() {
            return new AndroidAudioIntResult(false, 0);
        }

        static AndroidAudioIntResult of(int value) {
            return new AndroidAudioIntResult(true, value);
        }
    }

    /**
     * AudioManager int getters on the SystemService audio marker when {@code android.audio}
     * is configured. Exact signatures only:
     * <ul>
     *   <li>{@code getRingerMode()I} → fixed {@code ringerMode} (0/1/2; default 2 when key omitted)</li>
     *   <li>{@code getMode()I} → fixed {@code mode} (0..7; default 0 when key omitted)</li>
     *   <li>{@code getStreamVolume(I)I} / {@code getStreamMaxVolume(I)I} only when
     *       {@code streamVolumes} is configured and {@code streamType} has an exact match</li>
     *   <li>{@code getStreamMinVolume(I)I} same match, and only when that entry has explicit
     *       {@code minVolume} (omitted key stays unconfigured; never defaults to {@code 0})</li>
     * </ul>
     * Missing node / absent streamVolumes / unmatched streamType / omitted minVolume /
     * plain AudioManager / other SystemService / wrong signature is notHandled (UOE path,
     * no event). Does not implement setters / devices / routing / playback / focus / callbacks.
     */
    private static AndroidAudioIntResult tryAndroidAudioInt(BaseVM vm,
                                                            DvmObject<?> dvmObject,
                                                            String signature,
                                                            VarArg args) {
        final boolean ringerMode =
                "android/media/AudioManager->getRingerMode()I".equals(signature);
        final boolean mode =
                "android/media/AudioManager->getMode()I".equals(signature);
        final boolean streamVolume =
                "android/media/AudioManager->getStreamVolume(I)I".equals(signature);
        final boolean streamMaxVolume =
                "android/media/AudioManager->getStreamMaxVolume(I)I".equals(signature);
        final boolean streamMinVolume =
                "android/media/AudioManager->getStreamMinVolume(I)I".equals(signature);
        if (!ringerMode && !mode && !streamVolume && !streamMaxVolume && !streamMinVolume) {
            return AndroidAudioIntResult.notHandled();
        }
        if (!isSystemServiceAudioManager(dvmObject)) {
            return AndroidAudioIntResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidAudioConfigured()) {
            return AndroidAudioIntResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidAudioConfig audio = config.getAndroidAudioConfig();
        if (ringerMode) {
            int value = audio.getRingerMode();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_audio",
                    "AudioManager.getRingerMode",
                    "field=ringerMode,result=" + value,
                    "json-config", "读取配置的响铃模式");
            return AndroidAudioIntResult.of(value);
        }
        if (mode) {
            int value = audio.getMode();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_audio",
                    "AudioManager.getMode",
                    "field=mode,result=" + value,
                    "json-config", "读取配置的音频模式");
            return AndroidAudioIntResult.of(value);
        }
        // getStreamVolume / getStreamMaxVolume / getStreamMinVolume: require independent
        // streamVolumes configuration. minVolume is per-entry and never defaults to 0.
        if (!audio.isStreamVolumesConfigured()) {
            return AndroidAudioIntResult.notHandled();
        }
        int streamType = args.getIntArg(0);
        TraceEnvironmentConfig.AndroidStreamVolumeConfig entry = audio.findStreamVolume(streamType);
        if (entry == null) {
            return AndroidAudioIntResult.notHandled();
        }
        if (streamVolume) {
            int value = entry.getVolume();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_audio",
                    "AudioManager.getStreamVolume",
                    "streamType=" + streamType + ",result=" + value,
                    "json-config", "读取配置的流音量");
            return AndroidAudioIntResult.of(value);
        }
        if (streamMaxVolume) {
            int value = entry.getMaxVolume();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_audio",
                    "AudioManager.getStreamMaxVolume",
                    "streamType=" + streamType + ",result=" + value,
                    "json-config", "读取配置的流最大音量");
            return AndroidAudioIntResult.of(value);
        }
        if (!entry.isMinVolumeConfigured()) {
            return AndroidAudioIntResult.notHandled();
        }
        int value = entry.getMinVolume();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_audio",
                "AudioManager.getStreamMinVolume",
                "streamType=" + streamType + ",result=" + value,
                "json-config", "读取配置的流最小音量");
        return AndroidAudioIntResult.of(value);
    }

    private static final class AndroidAudioObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidAudioObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidAudioObjectResult notHandled() {
            return new AndroidAudioObjectResult(false, null);
        }

        static AndroidAudioObjectResult of(DvmObject<?> value) {
            return new AndroidAudioObjectResult(true, value);
        }
    }

    /**
     * {@code AudioManager.getProperty(String)} on the SystemService audio marker when
     * {@code android.audio} is configured. Exact signature only:
     * {@code getProperty(Ljava/lang/String;)Ljava/lang/String;}.
     * <ul>
     *   <li>Configured non-null property → {@link StringObject}; sidecar emitted</li>
     *   <li>Configured explicit null property → Java {@code null}; sidecar emitted</li>
     *   <li>Absent property key → Java {@code null}; <strong>no</strong> sidecar</li>
     * </ul>
     * Missing node / plain AudioManager / other SystemService / wrong signature / non-String arg
     * is notHandled (UOE path, no event). Does not implement volume / routing / devices / setters /
     * playback / focus / other AudioManager APIs.
     */
    private static AndroidAudioObjectResult tryAndroidAudioGetProperty(BaseVM vm,
                                                                       DvmObject<?> dvmObject,
                                                                       String signature,
                                                                       VarArg args) {
        if (!"android/media/AudioManager->getProperty(Ljava/lang/String;)Ljava/lang/String;"
                .equals(signature)) {
            return AndroidAudioObjectResult.notHandled();
        }
        if (!isSystemServiceAudioManager(dvmObject)) {
            return AndroidAudioObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidAudioConfigured()) {
            return AndroidAudioObjectResult.notHandled();
        }
        DvmObject<?> keyArg = args.getObjectArg(0);
        if (!(keyArg instanceof StringObject)) {
            return AndroidAudioObjectResult.notHandled();
        }
        String key = ((StringObject) keyArg).getValue();
        TraceEnvironmentConfig.AndroidAudioConfig audio = config.getAndroidAudioConfig();
        if (!audio.isPropertyConfigured(key)) {
            // absent key: Java null, no sidecar (do not leak other property data)
            return AndroidAudioObjectResult.of(null);
        }
        String value = audio.getProperty(key);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_audio",
                "AudioManager.getProperty",
                "key=" + key + ",result=" + value,
                "json-config", "读取配置的音频属性 " + key);
        if (value == null) {
            return AndroidAudioObjectResult.of(null);
        }
        return AndroidAudioObjectResult.of(new StringObject(vm, value));
    }

    private static final class AndroidLocationBooleanResult {
        final boolean handled;
        final boolean value;

        private AndroidLocationBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidLocationBooleanResult notHandled() {
            return new AndroidLocationBooleanResult(false, false);
        }

        static AndroidLocationBooleanResult of(boolean value) {
            return new AndroidLocationBooleanResult(true, value);
        }
    }

    /** True when receiver is {@code SystemService} for {@code Context.LOCATION_SERVICE}. */
    private static boolean isSystemServiceLocationManager(DvmObject<?> dvmObject) {
        return dvmObject instanceof SystemService
                && SystemService.LOCATION_SERVICE.equals(dvmObject.getValue());
    }

    /**
     * LocationManager / Location boolean getters:
     * <ul>
     *   <li>{@code isLocationEnabled()Z} when {@code android.location} is present (providers
     *       optional); returns independent {@code enabled} (default false). Never inferred from
     *       providers.</li>
     *   <li>{@code isProviderEnabled(String)} when {@code android.location.providers} is
     *       configured; provider must be exactly gps/network/passive.</li>
     *   <li>{@code hasProvider(String)} when {@code android.location.providers} is configured;
     *       explicit gps/network/passive key (including disabled) → {@code true}; omitted known
     *       key or any unknown string → {@code false}. Sidecar on both handled paths.</li>
     *   <li>{@code LocationProvider.requiresNetwork()Z} only on a live same-VM
     *       {@link ConfiguredLocationProvider} (still an explicit {@code providers} key) when
     *       that provider's {@code providerCapabilities.requiresNetwork} is explicitly configured.
     *       No sidecar. Missing node/entry/field, plain LocationProvider, cross-VM, or stale
     *       marker is notHandled.</li>
     *   <li>{@code LocationProvider.requiresSatellite()Z} same live-marker boundary as
     *       {@code requiresNetwork}, gated on that provider's
     *       {@code providerCapabilities.requiresSatellite} being explicitly configured.
     *       No sidecar.</li>
     *   <li>{@code LocationProvider.requiresCell()Z} same live-marker boundary as
     *       {@code requiresNetwork}, gated on that provider's
     *       {@code providerCapabilities.requiresCell} being explicitly configured.
     *       No sidecar.</li>
     *   <li>{@code LocationProvider.hasMonetaryCost()Z} same live-marker boundary as
     *       {@code requiresNetwork}, gated on that provider's
     *       {@code providerCapabilities.hasMonetaryCost} being explicitly configured.
     *       No sidecar.</li>
     *   <li>{@code LocationProvider.supportsAltitude()Z} same live-marker boundary as
     *       {@code requiresNetwork}, gated on that provider's
     *       {@code providerCapabilities.supportsAltitude} being explicitly configured.
     *       No sidecar.</li>
     *   <li>{@code LocationProvider.supportsSpeed()Z} same live-marker boundary as
     *       {@code requiresNetwork}, gated on that provider's
     *       {@code providerCapabilities.supportsSpeed} being explicitly configured.
     *       No sidecar.</li>
     *   <li>{@code LocationProvider.supportsBearing()Z} same live-marker boundary as
     *       {@code requiresNetwork}, gated on that provider's
     *       {@code providerCapabilities.supportsBearing} being explicitly configured.
     *       No sidecar.</li>
     *   <li>{@code LocationProvider.meetsCriteria(Landroid/location/Criteria;)Z} same
     *       live-marker boundary as {@code requiresNetwork}, gated on that provider's
     *       {@code providerCapabilities.meetsCriteria} being explicitly configured.
     *       Argument must be non-null, exactly {@code android/location/Criteria}, and
     *       that {@code DvmClass} must belong to the current {@code vm}
     *       ({@code objectType.vm == vm}); other {@code DvmObject}, null, wrong type,
     *       or cross-VM Criteria is notHandled. Success returns the fixed JSON Boolean
     *       only and does not read or derive Criteria fields.
     *       No sidecar on success or rejection.</li>
     *   <li>{@code LocationProvider.getAccuracy()I} and
     *       {@code LocationProvider.getPowerRequirement()I} are the int capability paths (see
     *       {@link #tryAndroidLocationInt}); not this boolean handler.
     *       {@code getAccuracy()I} is distinct from {@code Location.getAccuracy()F}.</li>
     *   <li>{@code Location.hasAltitude()Z} / {@code hasAccuracy()Z} / {@code hasSpeed()Z} /
     *       {@code hasBearing()Z} / {@code hasVerticalAccuracy()Z} /
     *       {@code hasSpeedAccuracy()Z} / {@code hasBearingAccuracy()Z} /
     *       {@code isFromMockProvider()Z} / {@code isMock()Z}
     *       (read-only alias of mock / isFromMockProvider) only on live same-VM
     *       {@link ConfiguredLocation} (no sidecar).</li>
     * </ul>
     * Missing node / plain LocationManager / other SystemService / invalid args / dead marker is
     * notHandled (UOE path, no event). Does not implement listeners / updates / GNSS / setters.
     */
    private static AndroidLocationBooleanResult tryAndroidLocationBoolean(BaseVM vm,
                                                                          DvmObject<?> dvmObject,
                                                                          String signature,
                                                                          VarArg args) {
        if ("android/location/LocationManager->isLocationEnabled()Z".equals(signature)) {
            if (!isSystemServiceLocationManager(dvmObject)) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidLocationConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            boolean value = config.getAndroidLocationConfig().isEnabled();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_location",
                    "LocationManager.isLocationEnabled",
                    "field=enabled,result=" + value,
                    "json-config", "读取配置的定位总开关状态");
            return AndroidLocationBooleanResult.of(value);
        }
        if ("android/location/LocationManager->isProviderEnabled(Ljava/lang/String;)Z"
                .equals(signature)) {
            if (!isSystemServiceLocationManager(dvmObject)) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidLocationProvidersConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            DvmObject<?> providerArg = args.getObjectArg(0);
            if (!(providerArg instanceof StringObject)) {
                return AndroidLocationBooleanResult.notHandled();
            }
            String provider = ((StringObject) providerArg).getValue();
            if (provider == null) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidLocationProvidersConfig providers =
                    config.getAndroidLocationProvidersConfig();
            final boolean value;
            if ("gps".equals(provider)) {
                value = providers.isGps();
            } else if ("network".equals(provider)) {
                value = providers.isNetwork();
            } else if ("passive".equals(provider)) {
                value = providers.isPassive();
            } else {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_location",
                    "LocationManager.isProviderEnabled",
                    "provider=" + provider + ",result=" + value,
                    "json-config", "读取配置的位置提供者启用状态");
            return AndroidLocationBooleanResult.of(value);
        }
        if ("android/location/LocationManager->hasProvider(Ljava/lang/String;)Z"
                .equals(signature)) {
            if (!isSystemServiceLocationManager(dvmObject)) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidLocationProvidersConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            DvmObject<?> providerArg = args.getObjectArg(0);
            if (!(providerArg instanceof StringObject)) {
                return AndroidLocationBooleanResult.notHandled();
            }
            String provider = ((StringObject) providerArg).getValue();
            if (provider == null) {
                return AndroidLocationBooleanResult.notHandled();
            }
            boolean value = isExplicitLocationProviderKey(
                    config.getAndroidLocationProvidersConfig(), provider);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_location",
                    "LocationManager.hasProvider",
                    "provider=" + provider + ",result=" + value,
                    "json-config", "按显式键查询配置中是否存在该位置提供者");
            return AndroidLocationBooleanResult.of(value);
        }
        if ("android/location/LocationProvider->requiresNetwork()Z".equals(signature)) {
            String name = liveConfiguredLocationProviderName(vm, dvmObject);
            if (name == null) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidLocationProviderCapabilitiesConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidLocationProviderCapabilityEntry capability =
                    config.getAndroidLocationProviderCapability(name);
            if (capability == null || !capability.isRequiresNetworkConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            return AndroidLocationBooleanResult.of(capability.isRequiresNetwork());
        }
        if ("android/location/LocationProvider->requiresSatellite()Z".equals(signature)) {
            String name = liveConfiguredLocationProviderName(vm, dvmObject);
            if (name == null) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidLocationProviderCapabilitiesConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidLocationProviderCapabilityEntry capability =
                    config.getAndroidLocationProviderCapability(name);
            if (capability == null || !capability.isRequiresSatelliteConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            return AndroidLocationBooleanResult.of(capability.isRequiresSatellite());
        }
        if ("android/location/LocationProvider->requiresCell()Z".equals(signature)) {
            String name = liveConfiguredLocationProviderName(vm, dvmObject);
            if (name == null) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidLocationProviderCapabilitiesConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidLocationProviderCapabilityEntry capability =
                    config.getAndroidLocationProviderCapability(name);
            if (capability == null || !capability.isRequiresCellConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            return AndroidLocationBooleanResult.of(capability.isRequiresCell());
        }
        if ("android/location/LocationProvider->hasMonetaryCost()Z".equals(signature)) {
            String name = liveConfiguredLocationProviderName(vm, dvmObject);
            if (name == null) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidLocationProviderCapabilitiesConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidLocationProviderCapabilityEntry capability =
                    config.getAndroidLocationProviderCapability(name);
            if (capability == null || !capability.isHasMonetaryCostConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            return AndroidLocationBooleanResult.of(capability.isHasMonetaryCost());
        }
        if ("android/location/LocationProvider->supportsAltitude()Z".equals(signature)) {
            String name = liveConfiguredLocationProviderName(vm, dvmObject);
            if (name == null) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidLocationProviderCapabilitiesConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidLocationProviderCapabilityEntry capability =
                    config.getAndroidLocationProviderCapability(name);
            if (capability == null || !capability.isSupportsAltitudeConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            return AndroidLocationBooleanResult.of(capability.isSupportsAltitude());
        }
        if ("android/location/LocationProvider->supportsSpeed()Z".equals(signature)) {
            String name = liveConfiguredLocationProviderName(vm, dvmObject);
            if (name == null) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidLocationProviderCapabilitiesConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidLocationProviderCapabilityEntry capability =
                    config.getAndroidLocationProviderCapability(name);
            if (capability == null || !capability.isSupportsSpeedConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            return AndroidLocationBooleanResult.of(capability.isSupportsSpeed());
        }
        if ("android/location/LocationProvider->supportsBearing()Z".equals(signature)) {
            String name = liveConfiguredLocationProviderName(vm, dvmObject);
            if (name == null) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidLocationProviderCapabilitiesConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidLocationProviderCapabilityEntry capability =
                    config.getAndroidLocationProviderCapability(name);
            if (capability == null || !capability.isSupportsBearingConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            return AndroidLocationBooleanResult.of(capability.isSupportsBearing());
        }
        if ("android/location/LocationProvider->meetsCriteria(Landroid/location/Criteria;)Z"
                .equals(signature)) {
            String name = liveConfiguredLocationProviderName(vm, dvmObject);
            if (name == null) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidLocationProviderCapabilitiesConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidLocationProviderCapabilityEntry capability =
                    config.getAndroidLocationProviderCapability(name);
            if (capability == null || !capability.isMeetsCriteriaConfigured()) {
                return AndroidLocationBooleanResult.notHandled();
            }
            DvmObject<?> criteriaArg = args.getObjectArg(0);
            DvmClass criteriaType = criteriaArg == null ? null : criteriaArg.getObjectType();
            if (criteriaType == null
                    || !"android/location/Criteria".equals(criteriaType.getClassName())
                    || criteriaType.vm != vm) {
                return AndroidLocationBooleanResult.notHandled();
            }
            return AndroidLocationBooleanResult.of(capability.isMeetsCriteria());
        }
        TraceEnvironmentConfig.AndroidLastKnownLocationConfig entry =
                liveConfiguredLocationEntry(vm, dvmObject);
        if (entry == null) {
            return AndroidLocationBooleanResult.notHandled();
        }
        if ("android/location/Location->hasAltitude()Z".equals(signature)) {
            return AndroidLocationBooleanResult.of(entry.isAltitudeConfigured());
        }
        if ("android/location/Location->hasAccuracy()Z".equals(signature)) {
            return AndroidLocationBooleanResult.of(entry.isAccuracyMetersConfigured());
        }
        if ("android/location/Location->hasSpeed()Z".equals(signature)) {
            return AndroidLocationBooleanResult.of(entry.isSpeedMetersPerSecondConfigured());
        }
        if ("android/location/Location->hasBearing()Z".equals(signature)) {
            return AndroidLocationBooleanResult.of(entry.isBearingDegreesConfigured());
        }
        if ("android/location/Location->hasVerticalAccuracy()Z".equals(signature)) {
            return AndroidLocationBooleanResult.of(entry.isVerticalAccuracyMetersConfigured());
        }
        if ("android/location/Location->hasSpeedAccuracy()Z".equals(signature)) {
            return AndroidLocationBooleanResult.of(
                    entry.isSpeedAccuracyMetersPerSecondConfigured());
        }
        if ("android/location/Location->hasBearingAccuracy()Z".equals(signature)) {
            return AndroidLocationBooleanResult.of(entry.isBearingAccuracyDegreesConfigured());
        }
        if ("android/location/Location->isFromMockProvider()Z".equals(signature)
                || "android/location/Location->isMock()Z".equals(signature)) {
            // isMock is a read-only API alias of configured mock / isFromMockProvider.
            return AndroidLocationBooleanResult.of(entry.isMock());
        }
        return AndroidLocationBooleanResult.notHandled();
    }

    private static final class AndroidLocationObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidLocationObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidLocationObjectResult notHandled() {
            return new AndroidLocationObjectResult(false, null);
        }

        static AndroidLocationObjectResult of(DvmObject<?> value) {
            return new AndroidLocationObjectResult(true, value);
        }
    }

    /**
     * LocationManager object APIs and Location/LocationProvider getters on configured markers:
     * <ul>
     *   <li>{@code getAllProviders()}/{@code getProviders(Z)} when providers configured
     *       (existing semantics).</li>
     *   <li>{@code getProvider(String)} when {@code android.location.providers} is configured:
     *       explicit key (including disabled) → fresh same-VM {@link ConfiguredLocationProvider};
     *       omitted known key or unknown key → Java {@code null}. Sidecar both handled paths.</li>
     *   <li>{@code getLastKnownLocation(String)} when {@code lastKnownLocations} is configured:
     *       exact provider match → fresh same-VM {@link ConfiguredLocation}; no match → Java
     *       {@code null}. Sidecar only on this success path (no raw coordinates).</li>
     *   <li>{@code Location.getProvider()} on live same-VM location marker only (no sidecar).</li>
     *   <li>{@code LocationProvider.getName()} on live same-VM provider marker only (sidecar).</li>
     * </ul>
     * Missing node / plain LocationManager / dead marker / invalid args is notHandled (UOE, no
     * event). Does not implement listeners / updates / GNSS / other provider capabilities /
     * setters. {@code LocationProvider.requiresNetwork()} /
     * {@code LocationProvider.requiresSatellite()} /
     * {@code LocationProvider.requiresCell()} /
     * {@code LocationProvider.hasMonetaryCost()} /
     * {@code LocationProvider.supportsAltitude()} /
     * {@code LocationProvider.supportsSpeed()} /
     * {@code LocationProvider.supportsBearing()} /
     * {@code LocationProvider.meetsCriteria(Criteria)} are the boolean capability paths.
     * {@code LocationProvider.getAccuracy()I} and
     * {@code LocationProvider.getPowerRequirement()I} are the int capability paths
     * (no sidecar; {@code getAccuracy()I} is distinct from {@code Location.getAccuracy()F}).
     * {@code meetsCriteria} returns a fixed JSON Boolean and does not read Criteria fields.
     */
    private static AndroidLocationObjectResult tryAndroidLocationObjectMethod(BaseVM vm,
                                                                              DvmObject<?> dvmObject,
                                                                              String signature,
                                                                              VarArg args) {
        if ("android/location/LocationManager->getLastKnownLocation(Ljava/lang/String;)Landroid/location/Location;"
                .equals(signature)) {
            if (!isSystemServiceLocationManager(dvmObject)) {
                return AndroidLocationObjectResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidLocationLastKnownLocationsConfigured()) {
                return AndroidLocationObjectResult.notHandled();
            }
            DvmObject<?> providerArg = args.getObjectArg(0);
            if (!(providerArg instanceof StringObject)) {
                return AndroidLocationObjectResult.notHandled();
            }
            String provider = ((StringObject) providerArg).getValue();
            if (provider == null) {
                return AndroidLocationObjectResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidLastKnownLocationConfig match = null;
            List<TraceEnvironmentConfig.AndroidLastKnownLocationConfig> entries =
                    config.getAndroidLocationLastKnownLocations();
            for (TraceEnvironmentConfig.AndroidLastKnownLocationConfig entry : entries) {
                if (provider.equals(entry.getProvider())) {
                    match = entry;
                    break;
                }
            }
            if (match == null) {
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_location",
                        "LocationManager.getLastKnownLocation",
                        "provider=" + provider + ",result=null",
                        "json-config", "按 provider 查询配置的最后已知位置（无匹配）");
                return AndroidLocationObjectResult.of(null);
            }
            DvmObject<?> location = vm.resolveClass("android/location/Location")
                    .newObject(new ConfiguredLocation(vm, config, match));
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_location",
                    "LocationManager.getLastKnownLocation",
                    "provider=" + provider + ",result=location",
                    "json-config", "按 provider 返回配置的最后已知位置标记");
            return AndroidLocationObjectResult.of(location);
        }
        if ("android/location/LocationManager->getProvider(Ljava/lang/String;)Landroid/location/LocationProvider;"
                .equals(signature)) {
            if (!isSystemServiceLocationManager(dvmObject)) {
                return AndroidLocationObjectResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidLocationProvidersConfigured()) {
                return AndroidLocationObjectResult.notHandled();
            }
            DvmObject<?> providerArg = args.getObjectArg(0);
            if (!(providerArg instanceof StringObject)) {
                return AndroidLocationObjectResult.notHandled();
            }
            String provider = ((StringObject) providerArg).getValue();
            if (provider == null) {
                return AndroidLocationObjectResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidLocationProvidersConfig providers =
                    config.getAndroidLocationProvidersConfig();
            if (isExplicitLocationProviderKey(providers, provider)) {
                DvmObject<?> marker = vm.resolveClass("android/location/LocationProvider")
                        .newObject(new ConfiguredLocationProvider(vm, config, provider));
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_location",
                        "LocationManager.getProvider",
                        "provider=" + provider + ",result=provider",
                        "json-config", "按 provider 返回配置的位置提供者标记");
                return AndroidLocationObjectResult.of(marker);
            }
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_location",
                    "LocationManager.getProvider",
                    "provider=" + provider + ",result=null",
                    "json-config", "按 provider 查询配置的位置提供者（无匹配）");
            return AndroidLocationObjectResult.of(null);
        }
        if ("android/location/Location->getProvider()Ljava/lang/String;".equals(signature)) {
            TraceEnvironmentConfig.AndroidLastKnownLocationConfig entry =
                    liveConfiguredLocationEntry(vm, dvmObject);
            if (entry == null) {
                return AndroidLocationObjectResult.notHandled();
            }
            return AndroidLocationObjectResult.of(new StringObject(vm, entry.getProvider()));
        }
        if ("android/location/LocationProvider->getName()Ljava/lang/String;".equals(signature)) {
            String name = liveConfiguredLocationProviderName(vm, dvmObject);
            if (name == null) {
                return AndroidLocationObjectResult.notHandled();
            }
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_location",
                    "LocationProvider.getName",
                    "provider=" + name + ",result=" + name,
                    "json-config", "读取配置的位置提供者名称");
            return AndroidLocationObjectResult.of(new StringObject(vm, name));
        }
        final boolean allProviders =
                "android/location/LocationManager->getAllProviders()Ljava/util/List;".equals(signature);
        final boolean filteredProviders =
                "android/location/LocationManager->getProviders(Z)Ljava/util/List;".equals(signature);
        if (!allProviders && !filteredProviders) {
            return AndroidLocationObjectResult.notHandled();
        }
        if (!isSystemServiceLocationManager(dvmObject)) {
            return AndroidLocationObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidLocationProvidersConfigured()) {
            return AndroidLocationObjectResult.notHandled();
        }
        // getAllProviders is equivalent to getProviders(false): all explicit keys.
        final boolean enabledOnly = filteredProviders && args.getIntArg(0) != 0;
        TraceEnvironmentConfig.AndroidLocationProvidersConfig providers =
                config.getAndroidLocationProvidersConfig();
        List<DvmObject<?>> elements = new ArrayList<DvmObject<?>>(3);
        StringBuilder names = new StringBuilder();
        appendExplicitLocationProvider(vm, elements, names, "gps",
                providers.isGpsConfigured(), providers.isGps(), enabledOnly);
        appendExplicitLocationProvider(vm, elements, names, "network",
                providers.isNetworkConfigured(), providers.isNetwork(), enabledOnly);
        appendExplicitLocationProvider(vm, elements, names, "passive",
                providers.isPassiveConfigured(), providers.isPassive(), enabledOnly);
        ArrayListObject list = new ArrayListObject(vm, elements);
        if (allProviders) {
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_location",
                    "LocationManager.getAllProviders",
                    "count=" + elements.size() + ",providers=" + names,
                    "json-config", "返回配置中显式列出的位置提供者");
        } else {
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_location",
                    "LocationManager.getProviders",
                    "enabledOnly=" + enabledOnly + ",count=" + elements.size()
                            + ",providers=" + names,
                    "json-config", "按 enabledOnly 返回配置中显式列出的位置提供者");
        }
        return AndroidLocationObjectResult.of(list);
    }

    /**
     * Append one provider name if the JSON key was explicit and passes the enabledOnly filter.
     */
    private static void appendExplicitLocationProvider(BaseVM vm, List<DvmObject<?>> elements,
                                                       StringBuilder names, String name,
                                                       boolean keyConfigured, boolean enabled,
                                                       boolean enabledOnly) {
        if (!keyConfigured) {
            return;
        }
        if (enabledOnly && !enabled) {
            return;
        }
        elements.add(new StringObject(vm, name));
        if (names.length() > 0) {
            names.append(',');
        }
        names.append(name);
    }

    /** True when {@code name} is an explicit {@code gps}/{@code network}/{@code passive} JSON key. */
    private static boolean isExplicitLocationProviderKey(
            TraceEnvironmentConfig.AndroidLocationProvidersConfig providers, String name) {
        if (providers == null || name == null) {
            return false;
        }
        if ("gps".equals(name)) {
            return providers.isGpsConfigured();
        }
        if ("network".equals(name)) {
            return providers.isNetworkConfigured();
        }
        if ("passive".equals(name)) {
            return providers.isPassiveConfigured();
        }
        return false;
    }

    /**
     * Same-VM private marker for {@code android.location.Location} from
     * {@code android.location.lastKnownLocations}. Binds creating VM, parent env config, and entry.
     */
    private static final class ConfiguredLocation {
        final BaseVM owner;
        final TraceEnvironmentConfig config;
        final TraceEnvironmentConfig.AndroidLastKnownLocationConfig entry;

        private ConfiguredLocation(BaseVM owner,
                                   TraceEnvironmentConfig config,
                                   TraceEnvironmentConfig.AndroidLastKnownLocationConfig entry) {
            this.owner = owner;
            this.config = config;
            this.entry = entry;
        }
    }

    /**
     * Same-VM private marker for {@code android.location.LocationProvider} from an explicit
     * {@code android.location.providers} key (including disabled). Binds creating VM, parent env
     * config, and provider name.
     */
    private static final class ConfiguredLocationProvider {
        final BaseVM owner;
        final TraceEnvironmentConfig config;
        final String name;

        private ConfiguredLocationProvider(BaseVM owner, TraceEnvironmentConfig config, String name) {
            this.owner = owner;
            this.config = config;
            this.name = name;
        }
    }

    private static final class AndroidLocationDoubleResult {
        final boolean handled;
        final double value;

        private AndroidLocationDoubleResult(boolean handled, double value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidLocationDoubleResult notHandled() {
            return new AndroidLocationDoubleResult(false, 0.0d);
        }

        static AndroidLocationDoubleResult of(double value) {
            return new AndroidLocationDoubleResult(true, value);
        }
    }

    private static final class AndroidLocationLongResult {
        final boolean handled;
        final long value;

        private AndroidLocationLongResult(boolean handled, long value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidLocationLongResult notHandled() {
            return new AndroidLocationLongResult(false, 0L);
        }

        static AndroidLocationLongResult of(long value) {
            return new AndroidLocationLongResult(true, value);
        }
    }

    private static final class AndroidLocationFloatResult {
        final boolean handled;
        final float value;

        private AndroidLocationFloatResult(boolean handled, float value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidLocationFloatResult notHandled() {
            return new AndroidLocationFloatResult(false, 0.0f);
        }

        static AndroidLocationFloatResult of(float value) {
            return new AndroidLocationFloatResult(true, value);
        }
    }

    /**
     * Live same-VM {@link ConfiguredLocation}: owner matches, env config instance still bound, and
     * entry remains in the configured lastKnownLocations list.
     */
    private static TraceEnvironmentConfig.AndroidLastKnownLocationConfig liveConfiguredLocationEntry(
            BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredLocation)) {
            return null;
        }
        ConfiguredLocation marker = (ConfiguredLocation) dvmObject.getValue();
        if (marker.owner != vm || marker.config == null || marker.entry == null) {
            return null;
        }
        TraceEnvironmentConfig current = TraceEnvironmentConfig.get(vm.getEmulator());
        if (current == null
                || current != marker.config
                || !current.isAndroidLocationLastKnownLocationsConfigured()) {
            return null;
        }
        List<TraceEnvironmentConfig.AndroidLastKnownLocationConfig> entries =
                current.getAndroidLocationLastKnownLocations();
        for (TraceEnvironmentConfig.AndroidLastKnownLocationConfig entry : entries) {
            if (entry == marker.entry) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Live same-VM {@link ConfiguredLocationProvider}: owner matches, env config instance still
     * bound, providers still configured, and the named key remains explicit.
     */
    private static String liveConfiguredLocationProviderName(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredLocationProvider)) {
            return null;
        }
        ConfiguredLocationProvider marker = (ConfiguredLocationProvider) dvmObject.getValue();
        if (marker.owner != vm || marker.config == null || marker.name == null) {
            return null;
        }
        TraceEnvironmentConfig current = TraceEnvironmentConfig.get(vm.getEmulator());
        if (current == null
                || current != marker.config
                || !current.isAndroidLocationProvidersConfigured()) {
            return null;
        }
        if (!isExplicitLocationProviderKey(current.getAndroidLocationProvidersConfig(),
                marker.name)) {
            return null;
        }
        return marker.name;
    }

    /**
     * Exact Location double getters on live {@link ConfiguredLocation} only (VarArg path;
     * VaList extends VarArg). No sidecar. getAltitude returns 0 when altitude was omitted.
     */
    private static AndroidLocationDoubleResult tryAndroidLocationDouble(BaseVM vm,
                                                                        DvmObject<?> dvmObject,
                                                                        String signature) {
        TraceEnvironmentConfig.AndroidLastKnownLocationConfig entry =
                liveConfiguredLocationEntry(vm, dvmObject);
        if (entry == null) {
            return AndroidLocationDoubleResult.notHandled();
        }
        if ("android/location/Location->getLatitude()D".equals(signature)) {
            return AndroidLocationDoubleResult.of(entry.getLatitude());
        }
        if ("android/location/Location->getLongitude()D".equals(signature)) {
            return AndroidLocationDoubleResult.of(entry.getLongitude());
        }
        if ("android/location/Location->getAltitude()D".equals(signature)) {
            return AndroidLocationDoubleResult.of(entry.getAltitude());
        }
        return AndroidLocationDoubleResult.notHandled();
    }

    /**
     * Exact Location long getters on live {@link ConfiguredLocation} only (VarArg + VaList).
     * No sidecar. Defaults 0 when time fields were omitted.
     */
    private static AndroidLocationLongResult tryAndroidLocationLong(BaseVM vm,
                                                                    DvmObject<?> dvmObject,
                                                                    String signature) {
        TraceEnvironmentConfig.AndroidLastKnownLocationConfig entry =
                liveConfiguredLocationEntry(vm, dvmObject);
        if (entry == null) {
            return AndroidLocationLongResult.notHandled();
        }
        if ("android/location/Location->getTime()J".equals(signature)) {
            return AndroidLocationLongResult.of(entry.getTimeMillis());
        }
        if ("android/location/Location->getElapsedRealtimeNanos()J".equals(signature)) {
            return AndroidLocationLongResult.of(entry.getElapsedRealtimeNanos());
        }
        return AndroidLocationLongResult.notHandled();
    }

    /**
     * Exact Location float getters on live {@link ConfiguredLocation} only
     * ({@code callFloatMethodV} path): {@code Location.getAccuracy()F}, {@code getSpeed()F},
     * {@code getBearing()F}, {@code getVerticalAccuracyMeters()F},
     * {@code getSpeedAccuracyMetersPerSecond()F}, {@code getBearingAccuracyDegrees()F}.
     * No sidecar. Returns 0 when the corresponding field was omitted.
     * Distinct from {@code LocationProvider.getAccuracy()I} (provider fine/coarse enum).
     */
    private static AndroidLocationFloatResult tryAndroidLocationFloat(BaseVM vm,
                                                                      DvmObject<?> dvmObject,
                                                                      String signature) {
        TraceEnvironmentConfig.AndroidLastKnownLocationConfig entry =
                liveConfiguredLocationEntry(vm, dvmObject);
        if (entry == null) {
            return AndroidLocationFloatResult.notHandled();
        }
        if ("android/location/Location->getAccuracy()F".equals(signature)) {
            return AndroidLocationFloatResult.of(entry.getAccuracyMeters());
        }
        if ("android/location/Location->getSpeed()F".equals(signature)) {
            return AndroidLocationFloatResult.of(entry.getSpeedMetersPerSecond());
        }
        if ("android/location/Location->getBearing()F".equals(signature)) {
            return AndroidLocationFloatResult.of(entry.getBearingDegrees());
        }
        if ("android/location/Location->getVerticalAccuracyMeters()F".equals(signature)) {
            return AndroidLocationFloatResult.of(entry.getVerticalAccuracyMeters());
        }
        if ("android/location/Location->getSpeedAccuracyMetersPerSecond()F".equals(signature)) {
            return AndroidLocationFloatResult.of(entry.getSpeedAccuracyMetersPerSecond());
        }
        if ("android/location/Location->getBearingAccuracyDegrees()F".equals(signature)) {
            return AndroidLocationFloatResult.of(entry.getBearingAccuracyDegrees());
        }
        return AndroidLocationFloatResult.notHandled();
    }

    private static final class AndroidLocationIntResult {
        final boolean handled;
        final int value;

        private AndroidLocationIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidLocationIntResult notHandled() {
            return new AndroidLocationIntResult(false, 0);
        }

        static AndroidLocationIntResult of(int value) {
            return new AndroidLocationIntResult(true, value);
        }
    }

    /**
     * Exact {@code LocationProvider.getAccuracy()I} or
     * {@code LocationProvider.getPowerRequirement()I} on a live same-VM
     * {@link ConfiguredLocationProvider} (still an explicit {@code providers} key) when that
     * provider's matching {@code providerCapabilities} int field is explicitly configured
     * ({@code accuracy}: 1 or 2; {@code powerRequirement}: 1, 2, or 3).
     * No sidecar on success or rejection. Missing node/entry/field, plain LocationProvider,
     * cross-VM, or stale marker is notHandled.
     * {@code LocationProvider.meetsCriteria(Criteria)} is the boolean capability path
     * (fixed JSON flag; does not read Criteria fields).
     * {@code getAccuracy()I} is distinct from {@code Location.getAccuracy()F}.
     */
    private static AndroidLocationIntResult tryAndroidLocationInt(BaseVM vm,
                                                                  DvmObject<?> dvmObject,
                                                                  String signature) {
        boolean accuracy = "android/location/LocationProvider->getAccuracy()I".equals(signature);
        boolean powerRequirement =
                "android/location/LocationProvider->getPowerRequirement()I".equals(signature);
        if (!accuracy && !powerRequirement) {
            return AndroidLocationIntResult.notHandled();
        }
        String name = liveConfiguredLocationProviderName(vm, dvmObject);
        if (name == null) {
            return AndroidLocationIntResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidLocationProviderCapabilitiesConfigured()) {
            return AndroidLocationIntResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidLocationProviderCapabilityEntry capability =
                config.getAndroidLocationProviderCapability(name);
        if (accuracy) {
            if (capability == null || !capability.isAccuracyConfigured()) {
                return AndroidLocationIntResult.notHandled();
            }
            return AndroidLocationIntResult.of(capability.getAccuracy());
        }
        if (capability == null || !capability.isPowerRequirementConfigured()) {
            return AndroidLocationIntResult.notHandled();
        }
        return AndroidLocationIntResult.of(capability.getPowerRequirement());
    }

    /**
     * Provenance marker for {@code android.hardware.Sensor} from configured {@code android.sensors}.
     * Binds the creating {@link BaseVM} (owner identity), the creating
     * {@link TraceEnvironmentConfig} instance (reference identity), and the sensor type integer.
     * Instance methods must use the configured path only.
     */
    private static final class ConfiguredSensor {
        final BaseVM owner;
        final TraceEnvironmentConfig config;
        final int sensorType;

        private ConfiguredSensor(BaseVM owner, TraceEnvironmentConfig config, int sensorType) {
            this.owner = owner;
            this.config = config;
            this.sensorType = sensorType;
        }
    }

    private static final class AndroidSensorObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidSensorObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidSensorObjectResult notHandled() {
            return new AndroidSensorObjectResult(false, null);
        }

        static AndroidSensorObjectResult of(DvmObject<?> value) {
            return new AndroidSensorObjectResult(true, value);
        }
    }

    private static final class AndroidSensorIntResult {
        final boolean handled;
        final int value;

        private AndroidSensorIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidSensorIntResult notHandled() {
            return new AndroidSensorIntResult(false, 0);
        }

        static AndroidSensorIntResult of(int value) {
            return new AndroidSensorIntResult(true, value);
        }
    }

    private static final class AndroidSensorBooleanResult {
        final boolean handled;
        final boolean value;

        private AndroidSensorBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidSensorBooleanResult notHandled() {
            return new AndroidSensorBooleanResult(false, false);
        }

        static AndroidSensorBooleanResult of(boolean value) {
            return new AndroidSensorBooleanResult(true, value);
        }
    }

    private static final class AndroidSensorFloatResult {
        final boolean handled;
        final float value;

        private AndroidSensorFloatResult(boolean handled, float value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidSensorFloatResult notHandled() {
            return new AndroidSensorFloatResult(false, 0f);
        }

        static AndroidSensorFloatResult of(float value) {
            return new AndroidSensorFloatResult(true, value);
        }
    }

    private static final String SENSOR_MANAGER_GET_DEFAULT_SENSOR_SIGNATURE =
            "android/hardware/SensorManager->getDefaultSensor(I)Landroid/hardware/Sensor;";
    private static final String SENSOR_MANAGER_GET_DEFAULT_SENSOR_WAKE_UP_SIGNATURE =
            "android/hardware/SensorManager->getDefaultSensor(IZ)Landroid/hardware/Sensor;";
    private static final String SENSOR_MANAGER_GET_SENSOR_LIST_SIGNATURE =
            "android/hardware/SensorManager->getSensorList(I)Ljava/util/List;";
    private static final String SENSOR_MANAGER_GET_DYNAMIC_SENSOR_LIST_SIGNATURE =
            "android/hardware/SensorManager->getDynamicSensorList(I)Ljava/util/List;";
    private static final String SENSOR_MANAGER_IS_DYNAMIC_SENSOR_DISCOVERY_SUPPORTED_SIGNATURE =
            "android/hardware/SensorManager->isDynamicSensorDiscoverySupported()Z";
    private static final String SENSOR_GET_TYPE_SIGNATURE =
            "android/hardware/Sensor->getType()I";
    private static final String SENSOR_GET_NAME_SIGNATURE =
            "android/hardware/Sensor->getName()Ljava/lang/String;";
    private static final String SENSOR_GET_VENDOR_SIGNATURE =
            "android/hardware/Sensor->getVendor()Ljava/lang/String;";
    private static final String SENSOR_GET_VERSION_SIGNATURE =
            "android/hardware/Sensor->getVersion()I";
    private static final String SENSOR_GET_STRING_TYPE_SIGNATURE =
            "android/hardware/Sensor->getStringType()Ljava/lang/String;";
    private static final String SENSOR_GET_MAXIMUM_RANGE_SIGNATURE =
            "android/hardware/Sensor->getMaximumRange()F";
    private static final String SENSOR_GET_RESOLUTION_SIGNATURE =
            "android/hardware/Sensor->getResolution()F";
    private static final String SENSOR_GET_POWER_SIGNATURE =
            "android/hardware/Sensor->getPower()F";
    private static final String SENSOR_GET_MIN_DELAY_SIGNATURE =
            "android/hardware/Sensor->getMinDelay()I";
    private static final String SENSOR_GET_MAX_DELAY_SIGNATURE =
            "android/hardware/Sensor->getMaxDelay()I";
    private static final String SENSOR_GET_FIFO_RESERVED_EVENT_COUNT_SIGNATURE =
            "android/hardware/Sensor->getFifoReservedEventCount()I";
    private static final String SENSOR_GET_FIFO_MAX_EVENT_COUNT_SIGNATURE =
            "android/hardware/Sensor->getFifoMaxEventCount()I";
    private static final String SENSOR_IS_WAKE_UP_SENSOR_SIGNATURE =
            "android/hardware/Sensor->isWakeUpSensor()Z";
    private static final String SENSOR_GET_ID_SIGNATURE =
            "android/hardware/Sensor->getId()I";
    private static final String SENSOR_GET_REPORTING_MODE_SIGNATURE =
            "android/hardware/Sensor->getReportingMode()I";
    private static final String SENSOR_IS_DYNAMIC_SENSOR_SIGNATURE =
            "android/hardware/Sensor->isDynamicSensor()Z";
    private static final String SENSOR_GET_REQUIRED_PERMISSION_SIGNATURE =
            "android/hardware/Sensor->getRequiredPermission()Ljava/lang/String;";
    private static final String SENSOR_IS_ADDITIONAL_INFO_SUPPORTED_SIGNATURE =
            "android/hardware/Sensor->isAdditionalInfoSupported()Z";
    private static final String SENSOR_GET_HIGHEST_DIRECT_REPORT_RATE_LEVEL_SIGNATURE =
            "android/hardware/Sensor->getHighestDirectReportRateLevel()I";
    private static final String SENSOR_IS_DIRECT_CHANNEL_TYPE_SUPPORTED_SIGNATURE =
            "android/hardware/Sensor->isDirectChannelTypeSupported(I)Z";
    /** {@code Sensor.TYPE_ALL}: return every configured type in JSON order. */
    private static final int SENSOR_TYPE_ALL = -1;

    /** True when receiver is {@code SystemService} for {@code Context.SENSOR_SERVICE}. */
    private static boolean isSystemServiceSensorManager(DvmObject<?> dvmObject) {
        return dvmObject instanceof SystemService
                && SystemService.SENSOR_SERVICE.equals(dvmObject.getValue());
    }

    /**
     * Live {@link ConfiguredSensor} only: same owner {@link BaseVM}, current
     * {@link TraceEnvironmentConfig#get} is the same instance as {@code marker.config}
     * (reference identity, not equals/hash/string), and that config still has
     * {@code android.sensors}. Plain / foreign / stale / missing-node → {@code null}.
     */
    private static ConfiguredSensor liveConfiguredSensor(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null || !(dvmObject.getValue() instanceof ConfiguredSensor)) {
            return null;
        }
        ConfiguredSensor marker = (ConfiguredSensor) dvmObject.getValue();
        if (marker.owner != vm || marker.config == null) {
            return null;
        }
        TraceEnvironmentConfig current = TraceEnvironmentConfig.get(vm.getEmulator());
        if (current == null || current != marker.config || !current.isAndroidSensorsConfigured()) {
            return null;
        }
        return marker;
    }

    private static DvmObject<?> newConfiguredSensorObject(BaseVM vm, TraceEnvironmentConfig config,
                                                          int sensorType) {
        return vm.resolveClass("android/hardware/Sensor")
                .newObject(new ConfiguredSensor(vm, config, sensorType));
    }

    /**
     * Sensor / SensorManager object methods. Exact signatures only:
     * <ul>
     *   <li>{@code Sensor.getName()} — only a live VM-owned {@link ConfiguredSensor} (same
     *       {@link TraceEnvironmentConfig} instance still current, {@code android.sensors} present)
     *       whose type has an explicit {@code android.sensors.names} entry. Fresh same-VM
     *       {@link StringObject}. No default name from the type integer.</li>
     *   <li>{@code Sensor.getVendor()} — 仅存活、同 VM、且创建时绑定的 {@link TraceEnvironmentConfig}
     *       实例仍是当前配置的 {@link ConfiguredSensor}，并且该 type 在 {@code android.sensors.vendors}
     *       中有显式条目。返回新的同 VM {@link StringObject}。该 type 无显式厂商值时不处理，
     *       不返回默认/宿主值，也不从 {@code names} 推导。</li>
     *   <li>{@code Sensor.getStringType()} — 仅存活、同 VM、且创建时绑定的 {@link TraceEnvironmentConfig}
     *       实例仍是当前配置的 {@link ConfiguredSensor}，并且该 type 在 {@code android.sensors.stringTypes}
     *       中有显式条目。返回新的同 VM {@link StringObject}。该 type 无显式 string type 时不处理，
     *       不返回默认/宿主值，也不从 {@code names} / {@code vendors} / {@code versions} 推导。</li>
     *   <li>{@code Sensor.getRequiredPermission()} — 仅存活、同 VM、且创建时绑定的
     *       {@link TraceEnvironmentConfig} 实例仍是当前配置的 {@link ConfiguredSensor}，
     *       并且该 type 在 {@code android.sensors.requiredPermissions} 中有显式条目。
     *       返回新的同 VM {@link StringObject}（空串合法）。不从其它字段推导。</li>
     *   <li>{@code getDefaultSensor(I)} — listed type → new VM-owned {@link ConfiguredSensor};
     *       unlisted → Java null. Sidecar both paths. Requires SystemService sensor + node.</li>
     *   <li>{@code getDefaultSensor(IZ)} — same handle gate as {@code getDefaultSensor(I)}
     *       (SystemService sensor + {@code android.sensors}). Reuses {@code types} + explicit
     *       {@code wakeUpSensors}: listed + matching wake-up → new {@link ConfiguredSensor};
     *       unlisted / mismatch / listed without wake-up entry → Java null. Does not invent a
     *       default wake-up flag and does not UOE once the node is present.
     *       **不**改 {@code getDefaultSensor(I)}，**不**从 {@code dynamicTypes} 取默认传感器。</li>
     *   <li>{@code getSensorList(I)} — {@code TYPE_ALL} ({@code -1}) → fresh list of new markers
     *       in configured JSON order; other type → fresh singleton if configured else empty list.
     *       Sidecar all handled paths.</li>
     *   <li>{@code getDynamicSensorList(I)} — same shape as {@code getSensorList(I)} but driven
     *       by configured {@code dynamicTypes} (missing key under present {@code android.sensors}
     *       yields a handled empty list; may overlap {@code types}).</li>
     * </ul>
     * Missing node / plain / foreign / stale marker / listed type without name, vendor or
     * string type / plain SensorManager / other SystemService is notHandled (UOE path, no event).
     * 不实现动态发现回调 / registerListener / 采样值。{@code Sensor.getVersion} /
     * {@code Sensor.getMinDelay} / {@code Sensor.getMaxDelay} /
     * {@code Sensor.getFifoReservedEventCount} / {@code Sensor.getFifoMaxEventCount}
     * 见 {@link #tryAndroidSensorInt}。
     * {@code Sensor.getMaximumRange} / {@code Sensor.getResolution} /
     * {@code Sensor.getPower} 见 {@link #tryAndroidSensorFloat}。
     */
    private static AndroidSensorObjectResult tryAndroidSensorObjectMethod(BaseVM vm,
                                                                          DvmObject<?> dvmObject,
                                                                          String signature,
                                                                          VarArg args) {
        if (SENSOR_GET_NAME_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetName(vm, dvmObject);
        }
        if (SENSOR_GET_VENDOR_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetVendor(vm, dvmObject);
        }
        if (SENSOR_GET_STRING_TYPE_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetStringType(vm, dvmObject);
        }
        if (SENSOR_GET_REQUIRED_PERMISSION_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetRequiredPermission(vm, dvmObject);
        }
        if (SENSOR_MANAGER_GET_DEFAULT_SENSOR_WAKE_UP_SIGNATURE.equals(signature)) {
            return tryAndroidSensorManagerGetDefaultSensorByWakeUp(vm, dvmObject, args);
        }
        final boolean getDefault =
                SENSOR_MANAGER_GET_DEFAULT_SENSOR_SIGNATURE.equals(signature);
        final boolean getList =
                SENSOR_MANAGER_GET_SENSOR_LIST_SIGNATURE.equals(signature);
        final boolean getDynamicList =
                SENSOR_MANAGER_GET_DYNAMIC_SENSOR_LIST_SIGNATURE.equals(signature);
        if (!getDefault && !getList && !getDynamicList) {
            return AndroidSensorObjectResult.notHandled();
        }
        if (!isSystemServiceSensorManager(dvmObject)) {
            return AndroidSensorObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidSensorsConfigured()) {
            return AndroidSensorObjectResult.notHandled();
        }
        int sensorType = args.getIntArg(0);
        TraceEnvironmentConfig.AndroidSensorsConfig sensors = config.getAndroidSensorsConfig();
        if (getList || getDynamicList) {
            List<DvmObject<?>> elements = new ArrayList<DvmObject<?>>();
            if (sensorType == SENSOR_TYPE_ALL) {
                List<Integer> configured = getDynamicList
                        ? sensors.getDynamicTypes() : sensors.getTypes();
                for (Integer type : configured) {
                    elements.add(newConfiguredSensorObject(vm, config, type.intValue()));
                }
            } else if ((getDynamicList ? sensors.containsDynamicType(sensorType)
                    : sensors.containsType(sensorType))) {
                elements.add(newConfiguredSensorObject(vm, config, sensorType));
            }
            ArrayListObject list = new ArrayListObject(vm, elements);
            final String api = getDynamicList
                    ? "SensorManager.getDynamicSensorList" : "SensorManager.getSensorList";
            final String note = getDynamicList ? "读取配置的动态传感器列表" : "读取配置的传感器列表";
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor", api,
                    "sensorType=" + sensorType + ",count=" + elements.size(),
                    "json-config", note);
            return AndroidSensorObjectResult.of(list);
        }
        // getDefaultSensor
        if (sensors.containsType(sensorType)) {
            DvmObject<?> sensor = newConfiguredSensorObject(vm, config, sensorType);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                    "SensorManager.getDefaultSensor",
                    "sensorType=" + sensorType + ",result=" + sensorType,
                    "json-config", "读取配置的默认传感器");
            return AndroidSensorObjectResult.of(sensor);
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "SensorManager.getDefaultSensor",
                "sensorType=" + sensorType + ",result=null",
                "json-config", "读取配置的默认传感器");
        return AndroidSensorObjectResult.of(null);
    }

    /**
     * {@code SensorManager.getDefaultSensor(IZ)}：与 {@code getDefaultSensor(I)} 同一接管门
     * （仅 SystemService sensor + {@code android.sensors}）。复用 {@code types}（不读
     * {@code dynamicTypes}）与显式 {@code wakeUpSensors}。列表内且配置 wake-up 等于参数 →
     * 新 {@link ConfiguredSensor}；列表外 / 不匹配 / 该 type 无显式 wake-up → Java null。
     * 节点已存在时不 UOE、不发明默认唤醒标记。不改 {@code getDefaultSensor(I)}。
     * sidecar 写 {@code sensorType}、{@code wakeUp} 与 {@code result}。
     */
    private static AndroidSensorObjectResult tryAndroidSensorManagerGetDefaultSensorByWakeUp(
            BaseVM vm, DvmObject<?> dvmObject, VarArg args) {
        if (!isSystemServiceSensorManager(dvmObject)) {
            return AndroidSensorObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidSensorsConfigured()) {
            return AndroidSensorObjectResult.notHandled();
        }
        int sensorType = args.getIntArg(0);
        boolean wakeUp = args.getIntArg(1) != 0;
        TraceEnvironmentConfig.AndroidSensorsConfig sensors = config.getAndroidSensorsConfig();
        boolean match = false;
        if (sensors.containsType(sensorType)
                && config.isAndroidSensorWakeUpSensorConfigured(sensorType)) {
            Boolean configuredWakeUp = config.getAndroidSensorWakeUpSensor(sensorType);
            match = configuredWakeUp != null && configuredWakeUp.booleanValue() == wakeUp;
        }
        if (!match) {
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                    "SensorManager.getDefaultSensor",
                    "sensorType=" + sensorType + ",wakeUp=" + wakeUp + ",result=null",
                    "json-config", "读取配置的默认传感器（按唤醒标记）");
            return AndroidSensorObjectResult.of(null);
        }
        DvmObject<?> sensor = newConfiguredSensorObject(vm, config, sensorType);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "SensorManager.getDefaultSensor",
                "sensorType=" + sensorType + ",wakeUp=" + wakeUp + ",result=" + sensorType,
                "json-config", "读取配置的默认传感器（按唤醒标记）");
        return AndroidSensorObjectResult.of(sensor);
    }

    /**
     * Sensor.getName()Ljava/lang/String; only for a live {@link ConfiguredSensor} when that type
     * has an explicit {@code names} entry on the bound config. Returns a fresh same-VM
     * {@link StringObject}. Sidecar records type and nameLength only (never the raw name).
     * Listed type without name / omitted or empty names / missing node / plain / foreign /
     * stale marker (config instance replaced, even if the new config still has the same type
     * or name) is notHandled (UOE, no event). Does not invent a default name.
     */
    private static AndroidSensorObjectResult tryAndroidSensorGetName(BaseVM vm,
                                                                     DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null || !marker.config.isAndroidSensorNameConfigured(marker.sensorType)) {
            return AndroidSensorObjectResult.notHandled();
        }
        String name = marker.config.getAndroidSensorName(marker.sensorType);
        if (name == null) {
            return AndroidSensorObjectResult.notHandled();
        }
        StringObject result = new StringObject(vm, name);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getName",
                "sensorType=" + marker.sensorType + ",nameLength=" + name.length(),
                "json-config", "读取配置的传感器名称");
        return AndroidSensorObjectResult.of(result);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式 {@code vendors} 条目时
     * 处理 {@code Sensor.getVendor()Ljava/lang/String;}。返回新的同 VM {@link StringObject}。
     * sidecar 只记录 type 与 vendorLength（不写厂商原文）。
     * 列表内但未配 vendor / 省略或空 {@code vendors} / 节点缺失 / 普通、外来、过期标记
     *（配置实例被替换，即使新配置仍含同 type 或同厂商值）均 notHandled（UOE，无事件）。
     * 不发明默认/宿主厂商名，也不从 {@code names} 推导。
     */
    private static AndroidSensorObjectResult tryAndroidSensorGetVendor(BaseVM vm,
                                                                       DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null || !marker.config.isAndroidSensorVendorConfigured(marker.sensorType)) {
            return AndroidSensorObjectResult.notHandled();
        }
        String vendor = marker.config.getAndroidSensorVendor(marker.sensorType);
        if (vendor == null) {
            return AndroidSensorObjectResult.notHandled();
        }
        StringObject result = new StringObject(vm, vendor);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getVendor",
                "sensorType=" + marker.sensorType + ",vendorLength=" + vendor.length(),
                "json-config", "读取配置的传感器厂商名称");
        return AndroidSensorObjectResult.of(result);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式 {@code stringTypes} 条目时
     * 处理 {@code Sensor.getStringType()Ljava/lang/String;}。返回新的同 VM {@link StringObject}。
     * sidecar 只记录 type 与 stringTypeLength（不写 string type 原文）。
     * 列表内但未配 string type / 省略或空 {@code stringTypes} / 节点缺失 / 普通、外来、过期标记
     *（配置实例被替换，即使新配置仍含同 type 或不同 string type 值）均 notHandled（UOE，无事件）。
     * 不发明默认/宿主 string type，也不从 {@code names} / {@code vendors} / {@code versions} 推导。
     */
    private static AndroidSensorObjectResult tryAndroidSensorGetStringType(BaseVM vm,
                                                                           DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null || !marker.config.isAndroidSensorStringTypeConfigured(marker.sensorType)) {
            return AndroidSensorObjectResult.notHandled();
        }
        String stringType = marker.config.getAndroidSensorStringType(marker.sensorType);
        if (stringType == null) {
            return AndroidSensorObjectResult.notHandled();
        }
        StringObject result = new StringObject(vm, stringType);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getStringType",
                "sensorType=" + marker.sensorType + ",stringTypeLength=" + stringType.length(),
                "json-config", "读取配置的传感器字符串类型");
        return AndroidSensorObjectResult.of(result);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式 {@code requiredPermissions}
     * 条目时处理 {@code Sensor.getRequiredPermission()Ljava/lang/String;}。
     * 返回新的同 VM {@link StringObject}（空串合法）。sidecar 只记录 type 与 permissionLength
     *（不写权限原文）。列表内但未配 permission / 省略或空 {@code requiredPermissions} /
     * 节点缺失 / 普通、外来、过期标记均 notHandled（UOE，无事件）。
     * 不发明默认/宿主权限，也不从其它传感器字段推导。
     */
    private static AndroidSensorObjectResult tryAndroidSensorGetRequiredPermission(BaseVM vm,
                                                                                   DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorRequiredPermissionConfigured(marker.sensorType)) {
            return AndroidSensorObjectResult.notHandled();
        }
        String permission = marker.config.getAndroidSensorRequiredPermission(marker.sensorType);
        if (permission == null) {
            return AndroidSensorObjectResult.notHandled();
        }
        StringObject result = new StringObject(vm, permission);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getRequiredPermission",
                "sensorType=" + marker.sensorType + ",permissionLength=" + permission.length(),
                "json-config", "读取配置的传感器所需权限");
        return AndroidSensorObjectResult.of(result);
    }

    /**
     * Sensor int methods. Exact signatures only:
     * <ul>
     *   <li>{@code Sensor.getType()I} — live {@link ConfiguredSensor} only.</li>
     *   <li>{@code Sensor.getVersion()I} — 仅存活、同 VM、且创建时绑定的
     *       {@link TraceEnvironmentConfig} 实例仍是当前配置的 {@link ConfiguredSensor}，
     *       并且该 type 在 {@code android.sensors.versions} 中有显式条目。
     *       sidecar 只写 {@code sensorType} 与 {@code version}，不泄露其它 JSON 配置。</li>
     *   <li>{@code Sensor.getMinDelay()I} — 仅存活、同 VM、且创建时绑定的
     *       {@link TraceEnvironmentConfig} 实例仍是当前配置的 {@link ConfiguredSensor}，
     *       并且该 type 在 {@code android.sensors.minDelaysMicros} 中有显式条目。
     *       sidecar 只写 {@code sensorType} 与 {@code minDelayMicros}，不泄露其它 JSON 配置。
     *       显式 {@code -1}（一次性传感器）合法。
     *       不从 {@code versions} / {@code maxDelaysMicros} 或其他传感器字段推导。</li>
     *   <li>{@code Sensor.getMaxDelay()I} — 仅存活、同 VM、且创建时绑定的
     *       {@link TraceEnvironmentConfig} 实例仍是当前配置的 {@link ConfiguredSensor}，
     *       并且该 type 在 {@code android.sensors.maxDelaysMicros} 中有显式条目。
     *       sidecar 只写 {@code sensorType} 与 {@code maxDelayMicros}，不泄露其它 JSON 配置。
     *       不从 {@code minDelaysMicros} / {@code fifoReservedEventCounts} 或其他传感器字段推导，
     *       也不校验 {@code max>=min}。</li>
     *   <li>{@code Sensor.getFifoReservedEventCount()I} — 仅存活、同 VM、且创建时绑定的
     *       {@link TraceEnvironmentConfig} 实例仍是当前配置的 {@link ConfiguredSensor}，
     *       并且该 type 在 {@code android.sensors.fifoReservedEventCounts} 中有显式条目。
     *       sidecar 只写 {@code sensorType} 与 {@code fifoReservedEventCount}，不泄露其它 JSON 配置。
     *       不从 {@code minDelaysMicros} / {@code maxDelaysMicros} / {@code fifoMaxEventCounts}
     *       或其他传感器字段推导，也不校验与 {@code fifoMaxEventCounts} 的大小关系。</li>
     *   <li>{@code Sensor.getFifoMaxEventCount()I} — 仅存活、同 VM、且创建时绑定的
     *       {@link TraceEnvironmentConfig} 实例仍是当前配置的 {@link ConfiguredSensor}，
     *       并且该 type 在 {@code android.sensors.fifoMaxEventCounts} 中有显式条目。
     *       sidecar 只写 {@code sensorType} 与 {@code fifoMaxEventCount}，不泄露其它 JSON 配置。
     *       不从 {@code minDelaysMicros} / {@code maxDelaysMicros} / {@code fifoReservedEventCounts}
     *       或其他传感器字段推导，也不校验与 {@code fifoReservedEventCounts} 的大小关系。</li>
     *   <li>{@code Sensor.getId()I} — 仅存活同 VM {@link ConfiguredSensor} 且该 type 在
     *       {@code android.sensors.sensorIds} 中有显式条目。sidecar 只写 {@code sensorType} 与
     *       {@code id}。不从其它字段推导。显式 {@code 0}/{@code -1} 与缺失不同。</li>
     *   <li>{@code Sensor.getReportingMode()I} — 仅存活同 VM {@link ConfiguredSensor} 且该 type
     *       在 {@code android.sensors.reportingModes} 中有显式条目。sidecar 只写
     *       {@code sensorType} 与 {@code reportingMode}。</li>
     *   <li>{@code Sensor.getHighestDirectReportRateLevel()I} — 仅存活同 VM
     *       {@link ConfiguredSensor} 且该 type 在
     *       {@code android.sensors.highestDirectReportRateLevels} 中有显式条目。sidecar 只写
     *       {@code sensorType} 与 {@code highestDirectReportRateLevel}。
     *       不实现直接通道对象或 create/configure API。</li>
     * </ul>
     * Plain / foreign / stale markers, missing versions / minDelaysMicros / maxDelaysMicros /
     * fifoReservedEventCounts / fifoMaxEventCounts / type without the matching int field,
     * and missing config are notHandled (UOE path, no event).
     */
    private static AndroidSensorIntResult tryAndroidSensorInt(BaseVM vm, DvmObject<?> dvmObject,
                                                              String signature) {
        if (SENSOR_GET_VERSION_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetVersion(vm, dvmObject);
        }
        if (SENSOR_GET_MIN_DELAY_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetMinDelay(vm, dvmObject);
        }
        if (SENSOR_GET_MAX_DELAY_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetMaxDelay(vm, dvmObject);
        }
        if (SENSOR_GET_FIFO_RESERVED_EVENT_COUNT_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetFifoReservedEventCount(vm, dvmObject);
        }
        if (SENSOR_GET_FIFO_MAX_EVENT_COUNT_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetFifoMaxEventCount(vm, dvmObject);
        }
        if (SENSOR_GET_ID_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetId(vm, dvmObject);
        }
        if (SENSOR_GET_REPORTING_MODE_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetReportingMode(vm, dvmObject);
        }
        if (SENSOR_GET_HIGHEST_DIRECT_REPORT_RATE_LEVEL_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetHighestDirectReportRateLevel(vm, dvmObject);
        }
        if (!SENSOR_GET_TYPE_SIGNATURE.equals(signature)) {
            return AndroidSensorIntResult.notHandled();
        }
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null) {
            return AndroidSensorIntResult.notHandled();
        }
        int sensorType = marker.sensorType;
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getType",
                "sensorType=" + sensorType,
                "json-config", "读取配置的传感器类型");
        return AndroidSensorIntResult.of(sensorType);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式 {@code versions} 条目时
     * 处理 {@code Sensor.getVersion()I}。sidecar 只记录 type 与 version（不写 names/vendors
     * 或其它 JSON）。列表内但未配 version / 省略或空 {@code versions} / 节点缺失 / 普通、外来、
     * 过期标记（配置实例被替换，即使新配置仍含同 type 或同版本值）均 notHandled（UOE，无事件）。
     * 不发明默认/宿主版本号，也不从 {@code names} / {@code vendors} 推导。
     */
    private static AndroidSensorIntResult tryAndroidSensorGetVersion(BaseVM vm,
                                                                     DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null || !marker.config.isAndroidSensorVersionConfigured(marker.sensorType)) {
            return AndroidSensorIntResult.notHandled();
        }
        Integer version = marker.config.getAndroidSensorVersion(marker.sensorType);
        if (version == null) {
            return AndroidSensorIntResult.notHandled();
        }
        int versionValue = version.intValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getVersion",
                "sensorType=" + marker.sensorType + ",version=" + versionValue,
                "json-config", "读取配置的传感器版本号");
        return AndroidSensorIntResult.of(versionValue);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式 {@code minDelaysMicros} 条目时
     * 处理 {@code Sensor.getMinDelay()I}。sidecar 只记录 type 与 minDelayMicros（不写
     * names/vendors/versions/stringTypes/maximumRanges/resolutions/powers/maxDelaysMicros
     * 或其它 JSON）。
     * 列表内但未配最小延迟 / 省略或空 {@code minDelaysMicros} / 节点缺失 / 普通、外来、
     * 过期标记（配置实例被替换，即使新配置仍含同 type 或不同最小延迟值）均 notHandled（UOE，无事件）。
     * 不发明默认/宿主最小延迟，也不从 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} / {@code powers} /
     * {@code maxDelaysMicros} 推导。
     */
    private static AndroidSensorIntResult tryAndroidSensorGetMinDelay(BaseVM vm,
                                                                      DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorMinDelayMicrosConfigured(marker.sensorType)) {
            return AndroidSensorIntResult.notHandled();
        }
        Integer minDelay = marker.config.getAndroidSensorMinDelayMicros(marker.sensorType);
        if (minDelay == null) {
            return AndroidSensorIntResult.notHandled();
        }
        int minDelayValue = minDelay.intValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getMinDelay",
                "sensorType=" + marker.sensorType + ",minDelayMicros=" + minDelayValue,
                "json-config", "读取配置的传感器最小延迟");
        return AndroidSensorIntResult.of(minDelayValue);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式 {@code maxDelaysMicros} 条目时
     * 处理 {@code Sensor.getMaxDelay()I}。sidecar 只记录 type 与 maxDelayMicros（不写
     * names/vendors/versions/stringTypes/maximumRanges/resolutions/powers/minDelaysMicros
     * 或其它 JSON）。
     * 列表内但未配最大延迟 / 省略或空 {@code maxDelaysMicros} / 节点缺失 / 普通、外来、
     * 过期标记（配置实例被替换，即使新配置仍含同 type 或不同最大延迟值）均 notHandled（UOE，无事件）。
     * 不发明默认/宿主最大延迟，也不从 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} / {@code powers} /
     * {@code minDelaysMicros} 推导，也不校验 {@code max>=min}。
     */
    private static AndroidSensorIntResult tryAndroidSensorGetMaxDelay(BaseVM vm,
                                                                      DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorMaxDelayMicrosConfigured(marker.sensorType)) {
            return AndroidSensorIntResult.notHandled();
        }
        Integer maxDelay = marker.config.getAndroidSensorMaxDelayMicros(marker.sensorType);
        if (maxDelay == null) {
            return AndroidSensorIntResult.notHandled();
        }
        int maxDelayValue = maxDelay.intValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getMaxDelay",
                "sensorType=" + marker.sensorType + ",maxDelayMicros=" + maxDelayValue,
                "json-config", "读取配置的传感器最大延迟");
        return AndroidSensorIntResult.of(maxDelayValue);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式
     * {@code fifoReservedEventCounts} 条目时处理 {@code Sensor.getFifoReservedEventCount()I}。
     * sidecar 只记录 type 与 fifoReservedEventCount（不写
     * names/vendors/versions/stringTypes/maximumRanges/resolutions/powers/minDelaysMicros/
     * maxDelaysMicros/fifoMaxEventCounts 或其它 JSON）。
     * 列表内但未配 FIFO 预留事件数 / 省略或空 {@code fifoReservedEventCounts} / 节点缺失 /
     * 普通、外来、过期标记（配置实例被替换，即使新配置仍含同 type 或不同 FIFO 预留事件数）
     * 均 notHandled（UOE，无事件）。
     * 不发明默认/宿主 FIFO 预留事件数，也不从 {@code names} / {@code vendors} /
     * {@code versions} / {@code stringTypes} / {@code maximumRanges} / {@code resolutions} /
     * {@code powers} / {@code minDelaysMicros} / {@code maxDelaysMicros} /
     * {@code fifoMaxEventCounts} 推导，也不校验与 {@code fifoMaxEventCounts} 的大小关系。
     */
    private static AndroidSensorIntResult tryAndroidSensorGetFifoReservedEventCount(BaseVM vm,
                                                                                    DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorFifoReservedEventCountConfigured(marker.sensorType)) {
            return AndroidSensorIntResult.notHandled();
        }
        Integer fifoReserved = marker.config.getAndroidSensorFifoReservedEventCount(marker.sensorType);
        if (fifoReserved == null) {
            return AndroidSensorIntResult.notHandled();
        }
        int fifoReservedValue = fifoReserved.intValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getFifoReservedEventCount",
                "sensorType=" + marker.sensorType + ",fifoReservedEventCount=" + fifoReservedValue,
                "json-config", "读取配置的传感器 FIFO 预留事件数");
        return AndroidSensorIntResult.of(fifoReservedValue);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式
     * {@code fifoMaxEventCounts} 条目时处理 {@code Sensor.getFifoMaxEventCount()I}。
     * sidecar 只记录 type 与 fifoMaxEventCount（不写
     * names/vendors/versions/stringTypes/maximumRanges/resolutions/powers/minDelaysMicros/
     * maxDelaysMicros/fifoReservedEventCounts 或其它 JSON）。
     * 列表内但未配 FIFO 最大事件数 / 省略或空 {@code fifoMaxEventCounts} / 节点缺失 /
     * 普通、外来、过期标记（配置实例被替换，即使新配置仍含同 type 或不同 FIFO 最大事件数）
     * 均 notHandled（UOE，无事件）。
     * 不发明默认/宿主 FIFO 最大事件数，也不从 {@code names} / {@code vendors} /
     * {@code versions} / {@code stringTypes} / {@code maximumRanges} / {@code resolutions} /
     * {@code powers} / {@code minDelaysMicros} / {@code maxDelaysMicros} /
     * {@code fifoReservedEventCounts} 推导，也不校验与 {@code fifoReservedEventCounts}
     * 的大小关系。
     */
    private static AndroidSensorIntResult tryAndroidSensorGetFifoMaxEventCount(BaseVM vm,
                                                                               DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorFifoMaxEventCountConfigured(marker.sensorType)) {
            return AndroidSensorIntResult.notHandled();
        }
        Integer fifoMax = marker.config.getAndroidSensorFifoMaxEventCount(marker.sensorType);
        if (fifoMax == null) {
            return AndroidSensorIntResult.notHandled();
        }
        int fifoMaxValue = fifoMax.intValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getFifoMaxEventCount",
                "sensorType=" + marker.sensorType + ",fifoMaxEventCount=" + fifoMaxValue,
                "json-config", "读取配置的传感器 FIFO 最大事件数");
        return AndroidSensorIntResult.of(fifoMaxValue);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式 {@code sensorIds} 条目时
     * 处理 {@code Sensor.getId()I}。sidecar 只记录 type 与 id。
     * 列表内但未配 id / 省略或空 {@code sensorIds} / 节点缺失 / 普通、外来、过期标记均 notHandled。
     * 不发明默认/宿主 id，也不从其它传感器字段推导。
     */
    private static AndroidSensorIntResult tryAndroidSensorGetId(BaseVM vm, DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null || !marker.config.isAndroidSensorIdConfigured(marker.sensorType)) {
            return AndroidSensorIntResult.notHandled();
        }
        Integer id = marker.config.getAndroidSensorId(marker.sensorType);
        if (id == null) {
            return AndroidSensorIntResult.notHandled();
        }
        int idValue = id.intValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getId",
                "sensorType=" + marker.sensorType + ",id=" + idValue,
                "json-config", "读取配置的传感器标识");
        return AndroidSensorIntResult.of(idValue);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式 {@code reportingModes}
     * 条目时处理 {@code Sensor.getReportingMode()I}。sidecar 只记录 type 与 reportingMode。
     * 列表内但未配 reportingMode / 省略或空 {@code reportingModes} / 节点缺失 / 普通、外来、
     * 过期标记均 notHandled。不发明默认/宿主 reportingMode，也不从其它传感器字段推导。
     */
    private static AndroidSensorIntResult tryAndroidSensorGetReportingMode(BaseVM vm,
                                                                          DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorReportingModeConfigured(marker.sensorType)) {
            return AndroidSensorIntResult.notHandled();
        }
        Integer mode = marker.config.getAndroidSensorReportingMode(marker.sensorType);
        if (mode == null) {
            return AndroidSensorIntResult.notHandled();
        }
        int modeValue = mode.intValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getReportingMode",
                "sensorType=" + marker.sensorType + ",reportingMode=" + modeValue,
                "json-config", "读取配置的传感器报告模式");
        return AndroidSensorIntResult.of(modeValue);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式
     * {@code highestDirectReportRateLevels} 条目时处理
     * {@code Sensor.getHighestDirectReportRateLevel()I}。sidecar 只记录 type 与
     * highestDirectReportRateLevel。列表内但未配 / 省略或空映射 / 节点缺失 / 普通、外来、
     * 过期标记均 notHandled。不发明默认/宿主值，也不从其它传感器字段推导。
     * 不实现直接通道参数 API。
     */
    private static AndroidSensorIntResult tryAndroidSensorGetHighestDirectReportRateLevel(
            BaseVM vm, DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorHighestDirectReportRateLevelConfigured(
                        marker.sensorType)) {
            return AndroidSensorIntResult.notHandled();
        }
        Integer rate = marker.config.getAndroidSensorHighestDirectReportRateLevel(marker.sensorType);
        if (rate == null) {
            return AndroidSensorIntResult.notHandled();
        }
        int rateValue = rate.intValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getHighestDirectReportRateLevel",
                "sensorType=" + marker.sensorType + ",highestDirectReportRateLevel=" + rateValue,
                "json-config", "读取配置的传感器最高直接报告速率等级");
        return AndroidSensorIntResult.of(rateValue);
    }

    /**
     * Sensor / SensorManager boolean methods when {@code android.sensors} is configured.
     * Exact signatures only:
     * <ul>
     *   <li>{@code Sensor.isWakeUpSensor()Z} — 仅存活、同 VM、且创建时绑定的
     *       {@link TraceEnvironmentConfig} 实例仍是当前配置的 {@link ConfiguredSensor}，
     *       并且该 type 在 {@code android.sensors.wakeUpSensors} 中有显式条目。
     *       sidecar 只写 {@code sensorType} 与 {@code wakeUp}。不从 FIFO / names / 其它字段推导。
     *       显式 {@code false} 与缺失不同。</li>
     *   <li>{@code Sensor.isDynamicSensor()Z} — 仅存活同 VM {@link ConfiguredSensor} 且该 type
     *       在 {@code android.sensors.dynamicSensors} 中有显式条目。**不**从
     *       {@code dynamicTypes} 推断。sidecar 只写 {@code sensorType} 与 {@code dynamic}。</li>
     *   <li>{@code Sensor.isAdditionalInfoSupported()Z} — 仅存活同 VM {@link ConfiguredSensor}
     *       且该 type 在 {@code android.sensors.additionalInfoSupported} 中有显式条目。
     *       sidecar 只写 {@code sensorType} 与 {@code additionalInfo}。</li>
     *   <li>{@code Sensor.isDirectChannelTypeSupported(I)Z} — 仅存活同 VM
     *       {@link ConfiguredSensor} 且该 type 在
     *       {@code android.sensors.directChannelTypesSupported} 中有显式条目（含空数组）。
     *       返回集合是否包含参数 {@code sharedMemType}；未知通道类型返回 {@code false}。
     *       sidecar 只写 {@code sensorType}、{@code sharedMemType} 与 {@code result}。
     *       **不**从 {@code highestDirectReportRateLevels} 推断。不物化直接通道对象。</li>
     *   <li>{@code SensorManager.isDynamicSensorDiscoverySupported()Z} — SystemService
     *       sensor 标记 + 配置的 {@code dynamicDiscoverySupported}（缺省 {@code false}；
     *       独立于 {@code dynamicTypes}）。</li>
     * </ul>
     * Missing node / plain / foreign / stale / type without matching entry is notHandled
     * (UOE, no event).
     */
    private static AndroidSensorBooleanResult tryAndroidSensorBoolean(BaseVM vm,
                                                                      DvmObject<?> dvmObject,
                                                                      String signature,
                                                                      VarArg args) {
        if (SENSOR_IS_WAKE_UP_SENSOR_SIGNATURE.equals(signature)) {
            return tryAndroidSensorIsWakeUpSensor(vm, dvmObject);
        }
        if (SENSOR_IS_DYNAMIC_SENSOR_SIGNATURE.equals(signature)) {
            return tryAndroidSensorIsDynamicSensor(vm, dvmObject);
        }
        if (SENSOR_IS_ADDITIONAL_INFO_SUPPORTED_SIGNATURE.equals(signature)) {
            return tryAndroidSensorIsAdditionalInfoSupported(vm, dvmObject);
        }
        if (SENSOR_IS_DIRECT_CHANNEL_TYPE_SUPPORTED_SIGNATURE.equals(signature)) {
            return tryAndroidSensorIsDirectChannelTypeSupported(vm, dvmObject, args);
        }
        if (!SENSOR_MANAGER_IS_DYNAMIC_SENSOR_DISCOVERY_SUPPORTED_SIGNATURE.equals(signature)) {
            return AndroidSensorBooleanResult.notHandled();
        }
        if (!isSystemServiceSensorManager(dvmObject)) {
            return AndroidSensorBooleanResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidSensorsConfigured()) {
            return AndroidSensorBooleanResult.notHandled();
        }
        boolean value = config.getAndroidSensorsConfig().isDynamicDiscoverySupported();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "SensorManager.isDynamicSensorDiscoverySupported",
                "result=" + value,
                "json-config", "读取配置的动态传感器发现支持标记");
        return AndroidSensorBooleanResult.of(value);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式 {@code wakeUpSensors}
     * 条目时处理 {@code Sensor.isWakeUpSensor()Z}。
     * sidecar 只记录 type 与 wakeUp（不写 names/vendors/versions/stringTypes/maximumRanges/
     * resolutions/powers/minDelaysMicros/maxDelaysMicros/fifoReservedEventCounts/
     * fifoMaxEventCounts 或其它 JSON）。
     * 列表内但未配 wake-up / 省略或空 {@code wakeUpSensors} / 节点缺失 / 普通、外来、
     * 过期标记均 notHandled（UOE，无事件）。
     * 不发明默认/宿主 wake-up，也不从其它传感器字段推导。显式 {@code false} 与缺失不同。
     */
    private static AndroidSensorBooleanResult tryAndroidSensorIsWakeUpSensor(BaseVM vm,
                                                                             DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorWakeUpSensorConfigured(marker.sensorType)) {
            return AndroidSensorBooleanResult.notHandled();
        }
        Boolean wakeUp = marker.config.getAndroidSensorWakeUpSensor(marker.sensorType);
        if (wakeUp == null) {
            return AndroidSensorBooleanResult.notHandled();
        }
        boolean wakeUpValue = wakeUp.booleanValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.isWakeUpSensor",
                "sensorType=" + marker.sensorType + ",wakeUp=" + wakeUpValue,
                "json-config", "读取配置的传感器唤醒标记");
        return AndroidSensorBooleanResult.of(wakeUpValue);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式 {@code dynamicSensors}
     * 条目时处理 {@code Sensor.isDynamicSensor()Z}。
     * **不**从 {@code dynamicTypes} 推断。sidecar 只记录 type 与 dynamic。
     * 列表内但未配 dynamic / 省略或空 {@code dynamicSensors} / 节点缺失 / 普通、外来、
     * 过期标记均 notHandled。显式 {@code false} 与缺失不同。
     */
    private static AndroidSensorBooleanResult tryAndroidSensorIsDynamicSensor(BaseVM vm,
                                                                              DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorDynamicSensorConfigured(marker.sensorType)) {
            return AndroidSensorBooleanResult.notHandled();
        }
        Boolean dynamic = marker.config.getAndroidSensorDynamicSensor(marker.sensorType);
        if (dynamic == null) {
            return AndroidSensorBooleanResult.notHandled();
        }
        boolean dynamicValue = dynamic.booleanValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.isDynamicSensor",
                "sensorType=" + marker.sensorType + ",dynamic=" + dynamicValue,
                "json-config", "读取配置的传感器动态标记");
        return AndroidSensorBooleanResult.of(dynamicValue);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式
     * {@code additionalInfoSupported} 条目时处理 {@code Sensor.isAdditionalInfoSupported()Z}。
     * sidecar 只记录 type 与 additionalInfo。列表内但未配 / 省略或空映射 / 节点缺失 /
     * 普通、外来、过期标记均 notHandled。显式 {@code false} 与缺失不同。
     */
    private static AndroidSensorBooleanResult tryAndroidSensorIsAdditionalInfoSupported(
            BaseVM vm, DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorAdditionalInfoSupportedConfigured(marker.sensorType)) {
            return AndroidSensorBooleanResult.notHandled();
        }
        Boolean additional = marker.config.getAndroidSensorAdditionalInfoSupported(marker.sensorType);
        if (additional == null) {
            return AndroidSensorBooleanResult.notHandled();
        }
        boolean additionalValue = additional.booleanValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.isAdditionalInfoSupported",
                "sensorType=" + marker.sensorType + ",additionalInfo=" + additionalValue,
                "json-config", "读取配置的传感器附加信息支持标记");
        return AndroidSensorBooleanResult.of(additionalValue);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式
     * {@code directChannelTypesSupported} 条目时处理
     * {@code Sensor.isDirectChannelTypeSupported(I)Z}。sidecar 只记录 type、
     * sharedMemType 与 result。列表内但未配 / 省略或空映射 / 节点缺失 / 普通、外来、
     * 过期标记均 notHandled。显式空数组返回 {@code false} 并发事件。
     * 不发明默认/宿主值，也不从 {@code highestDirectReportRateLevels} 推导。
     * 不实现 SensorDirectChannel 对象或 create/configure/共享内存 API。
     */
    private static AndroidSensorBooleanResult tryAndroidSensorIsDirectChannelTypeSupported(
            BaseVM vm, DvmObject<?> dvmObject, VarArg args) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorDirectChannelTypeSupportedConfigured(
                        marker.sensorType)) {
            return AndroidSensorBooleanResult.notHandled();
        }
        int sharedMemType = args.getIntArg(0);
        Boolean supported = marker.config.isAndroidSensorDirectChannelTypeSupported(
                marker.sensorType, sharedMemType);
        if (supported == null) {
            return AndroidSensorBooleanResult.notHandled();
        }
        boolean result = supported.booleanValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.isDirectChannelTypeSupported",
                "sensorType=" + marker.sensorType + ",sharedMemType=" + sharedMemType
                        + ",result=" + result,
                "json-config", "读取配置的传感器直接通道类型支持");
        return AndroidSensorBooleanResult.of(result);
    }

    /**
     * Sensor float methods. Exact signature only, wired through {@code callFloatMethodV}
     * (no instance {@code callFloatMethod} VarArg path exists):
     * <ul>
     *   <li>{@code Sensor.getMaximumRange()F} — 仅存活、同 VM、且创建时绑定的
     *       {@link TraceEnvironmentConfig} 实例仍是当前配置的 {@link ConfiguredSensor}，
     *       并且该 type 在 {@code android.sensors.maximumRanges} 中有显式条目。
     *       sidecar 只写 {@code sensorType} 与 {@code maximumRange}，不泄露其它 JSON。</li>
     *   <li>{@code Sensor.getResolution()F} — 仅存活、同 VM、且创建时绑定的
     *       {@link TraceEnvironmentConfig} 实例仍是当前配置的 {@link ConfiguredSensor}，
     *       并且该 type 在 {@code android.sensors.resolutions} 中有显式条目。
     *       sidecar 只写 {@code sensorType} 与 {@code resolution}，不泄露其它 JSON。
     *       不从 {@code maximumRanges} 或其他字段推导。</li>
     *   <li>{@code Sensor.getPower()F} — 仅存活、同 VM、且创建时绑定的
     *       {@link TraceEnvironmentConfig} 实例仍是当前配置的 {@link ConfiguredSensor}，
     *       并且该 type 在 {@code android.sensors.powers} 中有显式条目。
     *       sidecar 只写 {@code sensorType} 与 {@code power}，不泄露其它 JSON。
     *       不从 {@code maximumRanges} / {@code resolutions} 或其他字段推导。</li>
     * </ul>
     * Plain / foreign / stale markers, missing maximumRanges, resolutions or powers / type without
     * the matching float field, and missing config are notHandled (UOE path, no event).
     */
    private static AndroidSensorFloatResult tryAndroidSensorFloat(BaseVM vm, DvmObject<?> dvmObject,
                                                                  String signature) {
        if (SENSOR_GET_MAXIMUM_RANGE_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetMaximumRange(vm, dvmObject);
        }
        if (SENSOR_GET_RESOLUTION_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetResolution(vm, dvmObject);
        }
        if (SENSOR_GET_POWER_SIGNATURE.equals(signature)) {
            return tryAndroidSensorGetPower(vm, dvmObject);
        }
        return AndroidSensorFloatResult.notHandled();
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式 {@code maximumRanges} 条目时
     * 处理 {@code Sensor.getMaximumRange()F}。sidecar 只记录 type 与 maximumRange（不写
     * names/vendors/versions/stringTypes 或其它 JSON）。列表内但未配量程 / 省略或空
     * {@code maximumRanges} / 节点缺失 / 普通、外来、过期标记（配置实例被替换，即使新配置
     * 仍含同 type 或不同量程值）均 notHandled（UOE，无事件）。
     * 不发明默认/宿主量程，也不从 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code resolutions} / {@code powers} 推导。
     */
    private static AndroidSensorFloatResult tryAndroidSensorGetMaximumRange(BaseVM vm,
                                                                            DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorMaximumRangeConfigured(marker.sensorType)) {
            return AndroidSensorFloatResult.notHandled();
        }
        Float maximumRange = marker.config.getAndroidSensorMaximumRange(marker.sensorType);
        if (maximumRange == null) {
            return AndroidSensorFloatResult.notHandled();
        }
        float rangeValue = maximumRange.floatValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getMaximumRange",
                "sensorType=" + marker.sensorType + ",maximumRange=" + rangeValue,
                "json-config", "读取配置的传感器最大量程");
        return AndroidSensorFloatResult.of(rangeValue);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式 {@code resolutions} 条目时
     * 处理 {@code Sensor.getResolution()F}。sidecar 只记录 type 与 resolution（不写
     * names/vendors/versions/stringTypes/maximumRanges 或其它 JSON）。列表内但未配分辨率 / 省略或空
     * {@code resolutions} / 节点缺失 / 普通、外来、过期标记（配置实例被替换，即使新配置
     * 仍含同 type 或不同分辨率值）均 notHandled（UOE，无事件）。
     * 不发明默认/宿主分辨率，也不从 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code powers} 推导。
     */
    private static AndroidSensorFloatResult tryAndroidSensorGetResolution(BaseVM vm,
                                                                          DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorResolutionConfigured(marker.sensorType)) {
            return AndroidSensorFloatResult.notHandled();
        }
        Float resolution = marker.config.getAndroidSensorResolution(marker.sensorType);
        if (resolution == null) {
            return AndroidSensorFloatResult.notHandled();
        }
        float resolutionValue = resolution.floatValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getResolution",
                "sensorType=" + marker.sensorType + ",resolution=" + resolutionValue,
                "json-config", "读取配置的传感器分辨率");
        return AndroidSensorFloatResult.of(resolutionValue);
    }

    /**
     * 仅对存活 {@link ConfiguredSensor} 且该 type 在绑定配置上有显式 {@code powers} 条目时
     * 处理 {@code Sensor.getPower()F}。sidecar 只记录 type 与 power（不写
     * names/vendors/versions/stringTypes/maximumRanges/resolutions 或其它 JSON）。列表内但未配功耗 / 省略或空
     * {@code powers} / 节点缺失 / 普通、外来、过期标记（配置实例被替换，即使新配置
     * 仍含同 type 或不同功耗值）均 notHandled（UOE，无事件）。
     * 不发明默认/宿主功耗，也不从 {@code names} / {@code vendors} / {@code versions} /
     * {@code stringTypes} / {@code maximumRanges} / {@code resolutions} 推导。
     */
    private static AndroidSensorFloatResult tryAndroidSensorGetPower(BaseVM vm,
                                                                     DvmObject<?> dvmObject) {
        ConfiguredSensor marker = liveConfiguredSensor(vm, dvmObject);
        if (marker == null
                || !marker.config.isAndroidSensorPowerConfigured(marker.sensorType)) {
            return AndroidSensorFloatResult.notHandled();
        }
        Float power = marker.config.getAndroidSensorPower(marker.sensorType);
        if (power == null) {
            return AndroidSensorFloatResult.notHandled();
        }
        float powerValue = power.floatValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_sensor",
                "Sensor.getPower",
                "sensorType=" + marker.sensorType + ",power=" + powerValue,
                "json-config", "读取配置的传感器功耗");
        return AndroidSensorFloatResult.of(powerValue);
    }

    /**
     * Provenance marker for {@code java.net.NetworkInterface} from configured
     * {@code network.interfaces}. Binds creating {@link BaseVM} and the exact
     * {@link TraceEnvironmentConfig.NetworkInterfaceConfig} instance identity.
     */
    private static final class ConfiguredNetworkInterface {
        final BaseVM owner;
        final TraceEnvironmentConfig.NetworkInterfaceConfig config;

        private ConfiguredNetworkInterface(BaseVM owner,
                                           TraceEnvironmentConfig.NetworkInterfaceConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    /**
     * Marker value for {@code InetAddress} objects created from
     * {@code NetworkInterface.getInetAddresses()} using configured
     * {@code network.interfaces[].ipv4}. Binds creating {@link BaseVM} and the parent
     * interface config identity (stale when that entry is no longer current).
     */
    private static final class ConfiguredNetworkInterfaceInetAddress {
        final BaseVM owner;
        final TraceEnvironmentConfig.NetworkInterfaceConfig ifaceConfig;

        private ConfiguredNetworkInterfaceInetAddress(
                BaseVM owner, TraceEnvironmentConfig.NetworkInterfaceConfig ifaceConfig) {
            this.owner = owner;
            this.ifaceConfig = ifaceConfig;
        }
    }

    private static final class NetworkInterfaceObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private NetworkInterfaceObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static NetworkInterfaceObjectResult notHandled() {
            return new NetworkInterfaceObjectResult(false, null);
        }

        static NetworkInterfaceObjectResult of(DvmObject<?> value) {
            return new NetworkInterfaceObjectResult(true, value);
        }
    }

    private static final class NetworkInterfaceIntResult {
        final boolean handled;
        final int value;

        private NetworkInterfaceIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static NetworkInterfaceIntResult notHandled() {
            return new NetworkInterfaceIntResult(false, 0);
        }

        static NetworkInterfaceIntResult of(int value) {
            return new NetworkInterfaceIntResult(true, value);
        }
    }

    private static final String NETWORK_INTERFACE_GET_NETWORK_INTERFACES_SIGNATURE =
            "java/net/NetworkInterface->getNetworkInterfaces()Ljava/util/Enumeration;";
    private static final String NETWORK_INTERFACE_GET_BY_NAME_SIGNATURE =
            "java/net/NetworkInterface->getByName(Ljava/lang/String;)Ljava/net/NetworkInterface;";
    private static final String NETWORK_INTERFACE_GET_BY_INDEX_SIGNATURE =
            "java/net/NetworkInterface->getByIndex(I)Ljava/net/NetworkInterface;";
    private static final String NETWORK_INTERFACE_GET_BY_INET_ADDRESS_SIGNATURE =
            "java/net/NetworkInterface->getByInetAddress(Ljava/net/InetAddress;)Ljava/net/NetworkInterface;";
    private static final String NETWORK_INTERFACE_GET_NAME_SIGNATURE =
            "java/net/NetworkInterface->getName()Ljava/lang/String;";
    private static final String NETWORK_INTERFACE_GET_DISPLAY_NAME_SIGNATURE =
            "java/net/NetworkInterface->getDisplayName()Ljava/lang/String;";
    private static final String NETWORK_INTERFACE_GET_HARDWARE_ADDRESS_SIGNATURE =
            "java/net/NetworkInterface->getHardwareAddress()[B";
    private static final String NETWORK_INTERFACE_GET_INET_ADDRESSES_SIGNATURE =
            "java/net/NetworkInterface->getInetAddresses()Ljava/util/Enumeration;";
    private static final String NETWORK_INTERFACE_INET_ADDRESS_GET_HOST_ADDRESS_SIGNATURE =
            "java/net/InetAddress->getHostAddress()Ljava/lang/String;";
    private static final String NETWORK_INTERFACE_GET_INDEX_SIGNATURE =
            "java/net/NetworkInterface->getIndex()I";
    private static final String NETWORK_INTERFACE_GET_MTU_SIGNATURE =
            "java/net/NetworkInterface->getMTU()I";
    private static final String NETWORK_INTERFACE_IS_UP_SIGNATURE =
            "java/net/NetworkInterface->isUp()Z";
    private static final String NETWORK_INTERFACE_IS_LOOPBACK_SIGNATURE =
            "java/net/NetworkInterface->isLoopback()Z";
    private static final String NETWORK_INTERFACE_SUPPORTS_MULTICAST_SIGNATURE =
            "java/net/NetworkInterface->supportsMulticast()Z";
    private static final String NETWORK_INTERFACE_IS_POINT_TO_POINT_SIGNATURE =
            "java/net/NetworkInterface->isPointToPoint()Z";
    private static final String NETWORK_INTERFACE_IS_VIRTUAL_SIGNATURE =
            "java/net/NetworkInterface->isVirtual()Z";
    /** Linux {@code IFF_UP} — used by {@code NetworkInterface.isUp()} when flags configured. */
    private static final int NETWORK_INTERFACE_IFF_UP = 0x1;
    /** Linux {@code IFF_LOOPBACK} — used by {@code NetworkInterface.isLoopback()} when flags configured. */
    private static final int NETWORK_INTERFACE_IFF_LOOPBACK = 0x8;
    /** Linux {@code IFF_POINTOPOINT} — used by {@code NetworkInterface.isPointToPoint()} when flags configured. */
    private static final int NETWORK_INTERFACE_IFF_POINTOPOINT = 0x10;
    /** Linux {@code IFF_MULTICAST} — used by {@code NetworkInterface.supportsMulticast()} when flags configured. */
    private static final int NETWORK_INTERFACE_IFF_MULTICAST = 0x1000;

    private static final class NetworkInterfaceBooleanResult {
        final boolean handled;
        final boolean value;

        private NetworkInterfaceBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static NetworkInterfaceBooleanResult notHandled() {
            return new NetworkInterfaceBooleanResult(false, false);
        }

        static NetworkInterfaceBooleanResult of(boolean value) {
            return new NetworkInterfaceBooleanResult(true, value);
        }
    }

    /**
     * True only when {@code dvmObject} holds a {@link ConfiguredNetworkInterface} owned by this
     * {@code vm} whose config entry is still present by identity in the current
     * {@code network.interfaces} list (stale / foreign markers fail).
     */
    private static boolean isLiveConfiguredNetworkInterface(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredNetworkInterface)) {
            return false;
        }
        ConfiguredNetworkInterface marker = (ConfiguredNetworkInterface) dvmObject.getValue();
        return isLiveNetworkInterfaceConfig(vm, marker.owner, marker.config);
    }

    /**
     * True only when {@code dvmObject} holds a {@link ConfiguredNetworkInterfaceInetAddress}
     * owned by this {@code vm} whose parent interface config is still in the current list.
     */
    private static boolean isLiveConfiguredNetworkInterfaceInetAddress(BaseVM vm,
                                                                       DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredNetworkInterfaceInetAddress)) {
            return false;
        }
        ConfiguredNetworkInterfaceInetAddress marker =
                (ConfiguredNetworkInterfaceInetAddress) dvmObject.getValue();
        return isLiveNetworkInterfaceConfig(vm, marker.owner, marker.ifaceConfig);
    }

    private static boolean isLiveNetworkInterfaceConfig(
            BaseVM vm, BaseVM owner, TraceEnvironmentConfig.NetworkInterfaceConfig ifaceConfig) {
        if (owner != vm || ifaceConfig == null) {
            return false;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isNetworkInterfacesConfigured()) {
            return false;
        }
        List<TraceEnvironmentConfig.NetworkInterfaceConfig> list = config.getNetworkInterfaces();
        for (TraceEnvironmentConfig.NetworkInterfaceConfig entry : list) {
            if (entry == ifaceConfig) {
                return true;
            }
        }
        return false;
    }

    /**
     * Static NetworkInterface methods when {@code network.interfaces} is present (including
     * {@code []}). Never reads host {@code java.net.NetworkInterface}. Missing node is notHandled
     * (UOE path, no event).
     * <ul>
     *   <li>{@code getNetworkInterfaces()} → fresh {@link Enumeration} of new VM-owned markers
     *       in config order</li>
     *   <li>{@code getByName(String)} → Java NPE if name is null (no event); non-null match →
     *       fresh marker; non-null miss / empty list → handled null; non-String arg is
     *       notHandled</li>
     *   <li>{@code getByIndex(int)} → Java IAE if index &lt; 0 (no event); non-negative match →
     *       fresh marker; non-negative miss / empty list → handled null</li>
     *   <li>{@code getByInetAddress(InetAddress)} → Java NPE if arg is null (no event); only a
     *       live same-VM {@link ConfiguredNetworkInterfaceInetAddress} → fresh interface marker;
     *       DNS/plain/foreign/stale address markers are notHandled (no raw String / host lookup)</li>
     * </ul>
     */
    private static NetworkInterfaceObjectResult tryNetworkInterfaceStaticObject(BaseVM vm,
                                                                                String signature,
                                                                                VarArg args) {
        final boolean getAll =
                NETWORK_INTERFACE_GET_NETWORK_INTERFACES_SIGNATURE.equals(signature);
        final boolean getByName = NETWORK_INTERFACE_GET_BY_NAME_SIGNATURE.equals(signature);
        final boolean getByIndex = NETWORK_INTERFACE_GET_BY_INDEX_SIGNATURE.equals(signature);
        final boolean getByInetAddress =
                NETWORK_INTERFACE_GET_BY_INET_ADDRESS_SIGNATURE.equals(signature);
        if (!getAll && !getByName && !getByIndex && !getByInetAddress) {
            return NetworkInterfaceObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isNetworkInterfacesConfigured()) {
            return NetworkInterfaceObjectResult.notHandled();
        }
        List<TraceEnvironmentConfig.NetworkInterfaceConfig> ifaces = config.getNetworkInterfaces();
        if (getAll) {
            List<DvmObject<?>> elements = new ArrayList<DvmObject<?>>(ifaces.size());
            for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : ifaces) {
                elements.add(newConfiguredNetworkInterfaceObject(vm, iface));
            }
            Enumeration enumeration = new Enumeration(vm, elements);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                    "NetworkInterface.getNetworkInterfaces",
                    "count=" + elements.size(),
                    "json-config", "枚举配置的网络接口");
            return NetworkInterfaceObjectResult.of(enumeration);
        }
        if (getByName) {
            // getByName(String): Java contract only after config gate.
            DvmObject<?> nameArg = args.getObjectArg(0);
            if (nameArg == null) {
                throw new NullPointerException();
            }
            if (!(nameArg instanceof StringObject)) {
                return NetworkInterfaceObjectResult.notHandled();
            }
            String name = ((StringObject) nameArg).getValue();
            for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : ifaces) {
                if (name.equals(iface.getName())) {
                    DvmObject<?> marker = newConfiguredNetworkInterfaceObject(vm, iface);
                    TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                            "NetworkInterface.getByName",
                            "name=" + name + ",result=found",
                            "json-config", "按名称查找配置的网络接口");
                    return NetworkInterfaceObjectResult.of(marker);
                }
            }
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                    "NetworkInterface.getByName",
                    "name=" + name + ",result=null",
                    "json-config", "按名称查找配置的网络接口");
            return NetworkInterfaceObjectResult.of(null);
        }
        if (getByInetAddress) {
            // getByInetAddress(InetAddress): only live interface-address markers after config gate.
            DvmObject<?> addrArg = args.getObjectArg(0);
            if (addrArg == null) {
                throw new NullPointerException();
            }
            if (!isLiveConfiguredNetworkInterfaceInetAddress(vm, addrArg)) {
                // DNS / plain / foreign / stale same-VM address markers → UOE, no event.
                return NetworkInterfaceObjectResult.notHandled();
            }
            ConfiguredNetworkInterfaceInetAddress addrMarker =
                    (ConfiguredNetworkInterfaceInetAddress) addrArg.getValue();
            TraceEnvironmentConfig.NetworkInterfaceConfig iface = addrMarker.ifaceConfig;
            String ipv4 = iface.getIpv4();
            DvmObject<?> marker = newConfiguredNetworkInterfaceObject(vm, iface);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                    "NetworkInterface.getByInetAddress",
                    "address=" + ipv4 + ",result=found",
                    "json-config", "按地址查找配置的网络接口");
            return NetworkInterfaceObjectResult.of(marker);
        }
        // getByIndex(int): Java contract only after config gate.
        int index = args.getIntArg(0);
        if (index < 0) {
            throw new IllegalArgumentException("Interface index can't be negative");
        }
        for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : ifaces) {
            if (iface.getIndex() == index) {
                DvmObject<?> marker = newConfiguredNetworkInterfaceObject(vm, iface);
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                        "NetworkInterface.getByIndex",
                        "index=" + index + ",result=found",
                        "json-config", "按索引查找配置的网络接口");
                return NetworkInterfaceObjectResult.of(marker);
            }
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                "NetworkInterface.getByIndex",
                "index=" + index + ",result=null",
                "json-config", "按索引查找配置的网络接口");
        return NetworkInterfaceObjectResult.of(null);
    }

    private static DvmObject<?> newConfiguredNetworkInterfaceObject(
            BaseVM vm, TraceEnvironmentConfig.NetworkInterfaceConfig iface) {
        return vm.resolveClass("java/net/NetworkInterface")
                .newObject(new ConfiguredNetworkInterface(vm, iface));
    }

    /**
     * Instance NetworkInterface / related InetAddress object methods:
     * <ul>
     *   <li>{@code getName()} / {@code getDisplayName()} / {@code getHardwareAddress()} on a live
     *       {@link ConfiguredNetworkInterface}</li>
     *   <li>{@code getInetAddresses()} → fresh {@link Enumeration} of one fresh VM-owned
     *       {@link ConfiguredNetworkInterfaceInetAddress} for the interface's required
     *       {@code ipv4}</li>
     *   <li>{@code InetAddress.getHostAddress()} only for a live
     *       {@link ConfiguredNetworkInterfaceInetAddress} (DNS markers handled separately)</li>
     * </ul>
     * Plain/foreign/stale markers are notHandled. Does not read host interfaces.
     */
    private static NetworkInterfaceObjectResult tryNetworkInterfaceObjectMethod(BaseVM vm,
                                                                                DvmObject<?> dvmObject,
                                                                                String signature) {
        if (NETWORK_INTERFACE_INET_ADDRESS_GET_HOST_ADDRESS_SIGNATURE.equals(signature)) {
            if (!isLiveConfiguredNetworkInterfaceInetAddress(vm, dvmObject)) {
                return NetworkInterfaceObjectResult.notHandled();
            }
            ConfiguredNetworkInterfaceInetAddress addrMarker =
                    (ConfiguredNetworkInterfaceInetAddress) dvmObject.getValue();
            String ipv4 = addrMarker.ifaceConfig.getIpv4();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                    "InetAddress.getHostAddress",
                    "address=" + ipv4,
                    "json-config", "读取配置的网络接口 IPv4 地址");
            return NetworkInterfaceObjectResult.of(new StringObject(vm, ipv4));
        }

        final boolean getName = NETWORK_INTERFACE_GET_NAME_SIGNATURE.equals(signature);
        final boolean getDisplayName =
                NETWORK_INTERFACE_GET_DISPLAY_NAME_SIGNATURE.equals(signature);
        final boolean getHardwareAddress =
                NETWORK_INTERFACE_GET_HARDWARE_ADDRESS_SIGNATURE.equals(signature);
        final boolean getInetAddresses =
                NETWORK_INTERFACE_GET_INET_ADDRESSES_SIGNATURE.equals(signature);
        if (!getName && !getDisplayName && !getHardwareAddress && !getInetAddresses) {
            return NetworkInterfaceObjectResult.notHandled();
        }
        if (!isLiveConfiguredNetworkInterface(vm, dvmObject)) {
            return NetworkInterfaceObjectResult.notHandled();
        }
        ConfiguredNetworkInterface marker = (ConfiguredNetworkInterface) dvmObject.getValue();
        if (getName) {
            String name = marker.config.getName();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                    "NetworkInterface.getName",
                    "name=" + name,
                    "json-config", "读取配置的网络接口名称");
            return NetworkInterfaceObjectResult.of(new StringObject(vm, name));
        }
        if (getDisplayName) {
            if (!marker.config.isDisplayNameConfigured()) {
                return NetworkInterfaceObjectResult.notHandled();
            }
            String displayName = marker.config.getDisplayName();
            if (displayName == null) {
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                        "NetworkInterface.getDisplayName",
                        "displayName=null",
                        "json-config", "读取配置的网络接口显示名称");
                return NetworkInterfaceObjectResult.of(null);
            }
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                    "NetworkInterface.getDisplayName",
                    "displayName=" + displayName,
                    "json-config", "读取配置的网络接口显示名称");
            return NetworkInterfaceObjectResult.of(new StringObject(vm, displayName));
        }
        if (getInetAddresses) {
            String ipv4 = marker.config.getIpv4();
            List<DvmObject<?>> elements = new ArrayList<DvmObject<?>>(1);
            elements.add(vm.resolveClass("java/net/InetAddress")
                    .newObject(new ConfiguredNetworkInterfaceInetAddress(vm, marker.config)));
            Enumeration enumeration = new Enumeration(vm, elements);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                    "NetworkInterface.getInetAddresses",
                    "count=1,addresses=[" + ipv4 + "]",
                    "json-config", "枚举配置的网络接口地址");
            return NetworkInterfaceObjectResult.of(enumeration);
        }
        String mac = marker.config.getMac();
        if (mac == null) {
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                    "NetworkInterface.getHardwareAddress",
                    "mac=null",
                    "json-config", "读取配置的网络接口硬件地址");
            return NetworkInterfaceObjectResult.of(null);
        }
        byte[] bytes = parseConfiguredNetworkInterfaceMacBytes(mac);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                "NetworkInterface.getHardwareAddress",
                "mac=" + mac,
                "json-config", "读取配置的网络接口硬件地址");
        return NetworkInterfaceObjectResult.of(new ByteArray(vm, bytes));
    }

    /**
     * Parse validated lowercase colon-hex MAC ({@code aa:bb:cc:dd:ee:ff}) into exactly 6 bytes.
     * Config parse already enforces the format.
     */
    private static byte[] parseConfiguredNetworkInterfaceMacBytes(String mac) {
        String[] parts = mac.split(":", -1);
        byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return bytes;
    }

    /**
     * Instance NetworkInterface boolean methods only for a live VM-owned
     * {@link ConfiguredNetworkInterface}:
     * <ul>
     *   <li>{@code isVirtual()Z} → only when {@code virtual} is explicitly configured;
     *       never inferred from name/flags/address/index; independent of flag bits</li>
     *   <li>{@code isUp()Z} → {@code (flags & IFF_UP) != 0} ({@code IFF_UP = 0x1})</li>
     *   <li>{@code isLoopback()Z} → {@code (flags & IFF_LOOPBACK) != 0}
     *       ({@code IFF_LOOPBACK = 0x8}); never inferred from name/address/index</li>
     *   <li>{@code isPointToPoint()Z} → {@code (flags & IFF_POINTOPOINT) != 0}
     *       ({@code IFF_POINTOPOINT = 0x10}); never inferred from name/address</li>
     *   <li>{@code supportsMulticast()Z} → {@code (flags & IFF_MULTICAST) != 0}
     *       ({@code IFF_MULTICAST = 0x1000})</li>
     * </ul>
     * Absent flags (for flags-based methods) or absent virtual is notHandled (UOE, no event).
     * Does not implement subinterfaces or multicast socket behavior.
     */
    private static NetworkInterfaceBooleanResult tryNetworkInterfaceBoolean(BaseVM vm,
                                                                            DvmObject<?> dvmObject,
                                                                            String signature) {
        final boolean isVirtual = NETWORK_INTERFACE_IS_VIRTUAL_SIGNATURE.equals(signature);
        final boolean isUp = NETWORK_INTERFACE_IS_UP_SIGNATURE.equals(signature);
        final boolean isLoopback = NETWORK_INTERFACE_IS_LOOPBACK_SIGNATURE.equals(signature);
        final boolean isPointToPoint =
                NETWORK_INTERFACE_IS_POINT_TO_POINT_SIGNATURE.equals(signature);
        final boolean supportsMulticast =
                NETWORK_INTERFACE_SUPPORTS_MULTICAST_SIGNATURE.equals(signature);
        if (!isVirtual && !isUp && !isLoopback && !isPointToPoint && !supportsMulticast) {
            return NetworkInterfaceBooleanResult.notHandled();
        }
        if (!isLiveConfiguredNetworkInterface(vm, dvmObject)) {
            return NetworkInterfaceBooleanResult.notHandled();
        }
        ConfiguredNetworkInterface marker = (ConfiguredNetworkInterface) dvmObject.getValue();
        if (isVirtual) {
            if (!marker.config.isVirtualConfigured()) {
                return NetworkInterfaceBooleanResult.notHandled();
            }
            boolean virtual = marker.config.isVirtual();
            String name = marker.config.getName();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                    "NetworkInterface.isVirtual",
                    "name=" + name + ",result=" + virtual,
                    "json-config", "读取配置的网络接口是否为虚拟接口");
            return NetworkInterfaceBooleanResult.of(virtual);
        }
        Integer flags = marker.config.getFlags();
        if (flags == null) {
            return NetworkInterfaceBooleanResult.notHandled();
        }
        int flagsValue = flags.intValue();
        if (isUp) {
            boolean up = (flagsValue & NETWORK_INTERFACE_IFF_UP) != 0;
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                    "NetworkInterface.isUp",
                    "flags=" + flagsValue + ",result=" + up,
                    "json-config", "读取配置的网络接口是否启用");
            return NetworkInterfaceBooleanResult.of(up);
        }
        if (isLoopback) {
            boolean loopback = (flagsValue & NETWORK_INTERFACE_IFF_LOOPBACK) != 0;
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                    "NetworkInterface.isLoopback",
                    "flags=" + flagsValue + ",result=" + loopback,
                    "json-config", "读取配置的网络接口是否为回环");
            return NetworkInterfaceBooleanResult.of(loopback);
        }
        if (isPointToPoint) {
            boolean pointToPoint = (flagsValue & NETWORK_INTERFACE_IFF_POINTOPOINT) != 0;
            String name = marker.config.getName();
            String flagsUnsigned = Integer.toUnsignedString(flagsValue);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                    "NetworkInterface.isPointToPoint",
                    "name=" + name + ",flags=" + flagsUnsigned + ",result=" + pointToPoint,
                    "json-config", "读取配置的网络接口是否为点对点");
            return NetworkInterfaceBooleanResult.of(pointToPoint);
        }
        boolean multicast = (flagsValue & NETWORK_INTERFACE_IFF_MULTICAST) != 0;
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                "NetworkInterface.supportsMulticast",
                "flags=" + flagsValue + ",result=" + multicast,
                "json-config", "读取配置的网络接口是否支持组播");
        return NetworkInterfaceBooleanResult.of(multicast);
    }

    /**
     * Instance NetworkInterface int methods only for a live VM-owned
     * {@link ConfiguredNetworkInterface}:
     * <ul>
     *   <li>{@code getIndex()} → always returns configured index</li>
     *   <li>{@code getMTU()} → only when {@code mtu} is explicitly configured; otherwise
     *       notHandled (UOE, no event; no default invented)</li>
     * </ul>
     * Plain/foreign/stale markers are notHandled.
     */
    private static NetworkInterfaceIntResult tryNetworkInterfaceInt(BaseVM vm,
                                                                    DvmObject<?> dvmObject,
                                                                    String signature) {
        final boolean getIndex = NETWORK_INTERFACE_GET_INDEX_SIGNATURE.equals(signature);
        final boolean getMtu = NETWORK_INTERFACE_GET_MTU_SIGNATURE.equals(signature);
        if (!getIndex && !getMtu) {
            return NetworkInterfaceIntResult.notHandled();
        }
        if (!isLiveConfiguredNetworkInterface(vm, dvmObject)) {
            return NetworkInterfaceIntResult.notHandled();
        }
        ConfiguredNetworkInterface marker = (ConfiguredNetworkInterface) dvmObject.getValue();
        if (getIndex) {
            int index = marker.config.getIndex();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                    "NetworkInterface.getIndex",
                    "index=" + index,
                    "json-config", "读取配置的网络接口索引");
            return NetworkInterfaceIntResult.of(index);
        }
        Integer mtu = marker.config.getMtu();
        if (mtu == null) {
            return NetworkInterfaceIntResult.notHandled();
        }
        int value = mtu.intValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_interface",
                "NetworkInterface.getMTU",
                "mtu=" + value,
                "json-config", "读取配置的网络接口 MTU");
        return NetworkInterfaceIntResult.of(value);
    }

    private static final class AndroidSecurityStateBooleanResult {
        final boolean handled;
        final boolean value;

        private AndroidSecurityStateBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidSecurityStateBooleanResult notHandled() {
            return new AndroidSecurityStateBooleanResult(false, false);
        }

        static AndroidSecurityStateBooleanResult of(boolean value) {
            return new AndroidSecurityStateBooleanResult(true, value);
        }
    }

    /**
     * KeyguardManager no-arg isKeyguardLocked / inKeyguardRestrictedInputMode (deprecated alias of
     * isKeyguardLocked) / isKeyguardSecure / isDeviceLocked / isDeviceSecure when
     * {@code android.securityState} is configured. Exact signatures only; fields independent
     * (never inferred). Both locked APIs map to {@code keyguardLocked}. Missing node is notHandled
     * (UOE path, no event). No biometrics/callbacks.
     */
    private static AndroidSecurityStateBooleanResult tryAndroidSecurityStateBoolean(BaseVM vm,
                                                                                    String signature) {
        final String field;
        final String api;
        final String note;
        if ("android/app/KeyguardManager->isKeyguardLocked()Z".equals(signature)) {
            field = "keyguardLocked";
            api = "KeyguardManager.isKeyguardLocked";
            note = "读取配置的锁屏锁定状态";
        } else if ("android/app/KeyguardManager->inKeyguardRestrictedInputMode()Z".equals(signature)) {
            // Deprecated public alias of isKeyguardLocked; same keyguardLocked field
            field = "keyguardLocked";
            api = "KeyguardManager.inKeyguardRestrictedInputMode";
            note = "读取配置的锁屏锁定状态（兼容别名）";
        } else if ("android/app/KeyguardManager->isKeyguardSecure()Z".equals(signature)) {
            field = "keyguardSecure";
            api = "KeyguardManager.isKeyguardSecure";
            note = "读取配置的安全锁屏状态";
        } else if ("android/app/KeyguardManager->isDeviceLocked()Z".equals(signature)) {
            field = "deviceLocked";
            api = "KeyguardManager.isDeviceLocked";
            note = "读取配置的设备锁定状态";
        } else if ("android/app/KeyguardManager->isDeviceSecure()Z".equals(signature)) {
            field = "deviceSecure";
            api = "KeyguardManager.isDeviceSecure";
            note = "读取配置的设备安全状态";
        } else {
            return AndroidSecurityStateBooleanResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidSecurityStateConfigured()) {
            return AndroidSecurityStateBooleanResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidSecurityStateConfig securityState =
                config.getAndroidSecurityStateConfig();
        final boolean value;
        if ("keyguardLocked".equals(field)) {
            value = securityState.isKeyguardLocked();
        } else if ("keyguardSecure".equals(field)) {
            value = securityState.isKeyguardSecure();
        } else if ("deviceLocked".equals(field)) {
            value = securityState.isDeviceLocked();
        } else {
            value = securityState.isDeviceSecure();
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_security_state",
                api, "field=" + field + ",result=" + value,
                "json-config", note);
        return AndroidSecurityStateBooleanResult.of(value);
    }

    private static final class AndroidSecurityStateIntResult {
        final boolean handled;
        final int value;

        private AndroidSecurityStateIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidSecurityStateIntResult notHandled() {
            return new AndroidSecurityStateIntResult(false, 0);
        }

        static AndroidSecurityStateIntResult of(int value) {
            return new AndroidSecurityStateIntResult(true, value);
        }
    }

    /**
     * BiometricManager.canAuthenticate()I and canAuthenticate(I)I when
     * {@code android.securityState} is configured. Both return the fixed
     * {@code biometricCanAuthenticateResult} marker (int arg only recorded in sidecar).
     * Exact signatures only; missing node is notHandled (UOE path, no event).
     * No enrollment/auth flow or AndroidX APIs.
     */
    private static AndroidSecurityStateIntResult tryAndroidSecurityStateInt(BaseVM vm, String signature,
                                                                            VarArg args) {
        final boolean noArg =
                "android/hardware/biometrics/BiometricManager->canAuthenticate()I".equals(signature);
        final boolean withAuthenticators =
                "android/hardware/biometrics/BiometricManager->canAuthenticate(I)I".equals(signature);
        if (!noArg && !withAuthenticators) {
            return AndroidSecurityStateIntResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidSecurityStateConfigured()) {
            return AndroidSecurityStateIntResult.notHandled();
        }
        int result = config.getAndroidSecurityStateConfig().getBiometricCanAuthenticateResult();
        if (noArg) {
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_security_state",
                    "BiometricManager.canAuthenticate",
                    "result=" + result,
                    "json-config", "读取配置的生物识别可用性结果（固定标记）");
        } else {
            int authenticators = args.getIntArg(0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_security_state",
                    "BiometricManager.canAuthenticate",
                    "result=" + result + ",authenticators=" + authenticators,
                    "json-config", "读取配置的生物识别可用性结果（固定标记，与 authenticators 无关）");
        }
        return AndroidSecurityStateIntResult.of(result);
    }

    private static final class AndroidSecurityStateLongResult {
        final boolean handled;
        final long value;

        private AndroidSecurityStateLongResult(boolean handled, long value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidSecurityStateLongResult notHandled() {
            return new AndroidSecurityStateLongResult(false, 0L);
        }

        static AndroidSecurityStateLongResult of(long value) {
            return new AndroidSecurityStateLongResult(true, value);
        }
    }

    /**
     * BiometricManager.getLastAuthenticationTime(I)J when {@code android.securityState} is
     * configured. Returns the fixed {@code biometricLastAuthenticationElapsedRealtimeMillis}
     * marker (int authenticators only recorded in sidecar). Exact signature only; missing node
     * is notHandled (UOE path, no event). Does not validate authenticator bits or derive from
     * time config.
     */
    private static AndroidSecurityStateLongResult tryAndroidSecurityStateLong(BaseVM vm,
                                                                              String signature,
                                                                              VarArg args) {
        if (!"android/hardware/biometrics/BiometricManager->getLastAuthenticationTime(I)J"
                .equals(signature)) {
            return AndroidSecurityStateLongResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidSecurityStateConfigured()) {
            return AndroidSecurityStateLongResult.notHandled();
        }
        long result = config.getAndroidSecurityStateConfig()
                .getBiometricLastAuthenticationElapsedRealtimeMillis();
        int authenticators = args.getIntArg(0);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_security_state",
                "BiometricManager.getLastAuthenticationTime",
                "result=" + result + ",authenticators=" + authenticators,
                "json-config", "读取配置的最近生物识别认证时间（固定标记，elapsedRealtime 毫秒；与 authenticators 无关）");
        return AndroidSecurityStateLongResult.of(result);
    }

    private static final class AndroidBatteryIntResult {
        final boolean handled;
        final int value;

        private AndroidBatteryIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidBatteryIntResult notHandled() {
            return new AndroidBatteryIntResult(false, 0);
        }

        static AndroidBatteryIntResult of(int value) {
            return new AndroidBatteryIntResult(true, value);
        }
    }

    /**
     * BatteryManager.getIntProperty(I) when {@code android.battery} is configured:
     * <ul>
     *   <li>{@code propertyId=4} ({@code BATTERY_PROPERTY_CAPACITY}) → capacityPercent</li>
     *   <li>{@code propertyId=1} ({@code BATTERY_PROPERTY_CHARGE_COUNTER}) → chargeCounterUah
     *       only when that key is present (no default)</li>
     *   <li>{@code propertyId=2} ({@code BATTERY_PROPERTY_CURRENT_NOW}) → currentNowUa
     *       only when that key is present (no default; signed int)</li>
     *   <li>{@code propertyId=3} ({@code BATTERY_PROPERTY_CURRENT_AVERAGE}) → currentAverageUa
     *       only when that key is present (no default; signed int)</li>
     *   <li>{@code propertyId=6} ({@code BATTERY_PROPERTY_STATUS}) → status
     *       only when that key is present (no default; int 1..5; not via getLongProperty)</li>
     * </ul>
     * {@code propertyId=5} ({@code BATTERY_PROPERTY_ENERGY_COUNTER}) is intentionally
     * not handled here (long-only via {@code getLongProperty} to avoid int truncation).
     * Other propertyIds / missing node / missing optional keys are notHandled
     * (UOE path, no android_battery event).
     */
    private static AndroidBatteryIntResult tryAndroidBatteryInt(BaseVM vm, String signature, VarArg args) {
        if (!"android/os/BatteryManager->getIntProperty(I)I".equals(signature)) {
            return AndroidBatteryIntResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidBatteryConfigured()) {
            return AndroidBatteryIntResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidBatteryConfig battery = config.getAndroidBatteryConfig();
        int propertyId = args.getIntArg(0);
        // BATTERY_PROPERTY_CAPACITY == 4
        if (propertyId == 4) {
            int value = battery.getCapacityPercent();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_battery",
                    "BatteryManager.getIntProperty",
                    "propertyId=4,field=capacityPercent,result=" + value,
                    "json-config", "读取配置的电池容量百分比");
            return AndroidBatteryIntResult.of(value);
        }
        // BATTERY_PROPERTY_CHARGE_COUNTER == 1
        if (propertyId == 1) {
            if (!battery.isChargeCounterUahConfigured()) {
                return AndroidBatteryIntResult.notHandled();
            }
            int value = battery.getChargeCounterUah();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_battery",
                    "BatteryManager.getIntProperty",
                    "propertyId=1,field=chargeCounterUah,result=" + value,
                    "json-config", "读取配置的电池电荷计数（微安时）");
            return AndroidBatteryIntResult.of(value);
        }
        // BATTERY_PROPERTY_CURRENT_NOW == 2
        if (propertyId == 2) {
            if (!battery.isCurrentNowUaConfigured()) {
                return AndroidBatteryIntResult.notHandled();
            }
            int value = battery.getCurrentNowUa();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_battery",
                    "BatteryManager.getIntProperty",
                    "propertyId=2,field=currentNowUa,result=" + value,
                    "json-config", "读取配置的电池瞬时电流（微安）");
            return AndroidBatteryIntResult.of(value);
        }
        // BATTERY_PROPERTY_CURRENT_AVERAGE == 3
        if (propertyId == 3) {
            if (!battery.isCurrentAverageUaConfigured()) {
                return AndroidBatteryIntResult.notHandled();
            }
            int value = battery.getCurrentAverageUa();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_battery",
                    "BatteryManager.getIntProperty",
                    "propertyId=3,field=currentAverageUa,result=" + value,
                    "json-config", "读取配置的电池平均电流（微安）");
            return AndroidBatteryIntResult.of(value);
        }
        // BATTERY_PROPERTY_STATUS == 6 (int-only; not via getLongProperty)
        if (propertyId == 6) {
            if (!battery.isStatusConfigured()) {
                return AndroidBatteryIntResult.notHandled();
            }
            int value = battery.getStatus();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_battery",
                    "BatteryManager.getIntProperty",
                    "propertyId=6,field=status,result=" + value,
                    "json-config", "读取配置的电池状态");
            return AndroidBatteryIntResult.of(value);
        }
        return AndroidBatteryIntResult.notHandled();
    }

    private static final class AndroidBatteryLongResult {
        final boolean handled;
        final long value;

        private AndroidBatteryLongResult(boolean handled, long value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidBatteryLongResult notHandled() {
            return new AndroidBatteryLongResult(false, 0L);
        }

        static AndroidBatteryLongResult of(long value) {
            return new AndroidBatteryLongResult(true, value);
        }
    }

    /**
     * BatteryManager long methods when {@code android.battery} is configured:
     * <ul>
     *   <li>{@code computeChargeTimeRemaining()J} → chargeTimeRemainingMillis only when key present
     *       (-1 unable-to-compute or nonnegative; never inferred)</li>
     *   <li>{@code getLongProperty(I)J}: propertyId 4/1/2/3/5 as documented below</li>
     * </ul>
     * {@code getLongProperty} propertyIds:
     * <ul>
     *   <li>{@code propertyId=4} → {@code (long) capacityPercent}</li>
     *   <li>{@code propertyId=1} → {@code (long) chargeCounterUah} only when key present</li>
     *   <li>{@code propertyId=2} → {@code (long) currentNowUa} only when key present</li>
     *   <li>{@code propertyId=3} → {@code (long) currentAverageUa} only when key present</li>
     *   <li>{@code propertyId=5} ({@code BATTERY_PROPERTY_ENERGY_COUNTER}) → energyCounterNwh
     *       only when key present (long-only; not exposed via getIntProperty)</li>
     * </ul>
     * {@code propertyId=6} ({@code BATTERY_PROPERTY_STATUS}) is intentionally not handled here
     * (int-only via {@code getIntProperty}).
     * Other propertyIds / missing node / missing optional keys are notHandled
     * (UOE path, no android_battery event).
     */
    private static AndroidBatteryLongResult tryAndroidBatteryLong(BaseVM vm, String signature, VarArg args) {
        if ("android/os/BatteryManager->computeChargeTimeRemaining()J".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidBatteryConfigured()) {
                return AndroidBatteryLongResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidBatteryConfig battery = config.getAndroidBatteryConfig();
            if (!battery.isChargeTimeRemainingMillisConfigured()) {
                return AndroidBatteryLongResult.notHandled();
            }
            long value = battery.getChargeTimeRemainingMillis();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_battery",
                    "BatteryManager.computeChargeTimeRemaining",
                    "field=chargeTimeRemainingMillis,result=" + value,
                    "json-config", "读取配置的电池剩余充电时间（毫秒）");
            return AndroidBatteryLongResult.of(value);
        }
        if (!"android/os/BatteryManager->getLongProperty(I)J".equals(signature)) {
            return AndroidBatteryLongResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidBatteryConfigured()) {
            return AndroidBatteryLongResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidBatteryConfig battery = config.getAndroidBatteryConfig();
        int propertyId = args.getIntArg(0);
        // BATTERY_PROPERTY_CAPACITY == 4
        if (propertyId == 4) {
            long value = battery.getCapacityPercent();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_battery",
                    "BatteryManager.getLongProperty",
                    "propertyId=4,field=capacityPercent,result=" + value,
                    "json-config", "读取配置的电池容量百分比（长整型）");
            return AndroidBatteryLongResult.of(value);
        }
        // BATTERY_PROPERTY_CHARGE_COUNTER == 1
        if (propertyId == 1) {
            if (!battery.isChargeCounterUahConfigured()) {
                return AndroidBatteryLongResult.notHandled();
            }
            long value = battery.getChargeCounterUah();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_battery",
                    "BatteryManager.getLongProperty",
                    "propertyId=1,field=chargeCounterUah,result=" + value,
                    "json-config", "读取配置的电池电荷计数（微安时，长整型）");
            return AndroidBatteryLongResult.of(value);
        }
        // BATTERY_PROPERTY_CURRENT_NOW == 2
        if (propertyId == 2) {
            if (!battery.isCurrentNowUaConfigured()) {
                return AndroidBatteryLongResult.notHandled();
            }
            long value = battery.getCurrentNowUa();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_battery",
                    "BatteryManager.getLongProperty",
                    "propertyId=2,field=currentNowUa,result=" + value,
                    "json-config", "读取配置的电池瞬时电流（微安，长整型）");
            return AndroidBatteryLongResult.of(value);
        }
        // BATTERY_PROPERTY_CURRENT_AVERAGE == 3
        if (propertyId == 3) {
            if (!battery.isCurrentAverageUaConfigured()) {
                return AndroidBatteryLongResult.notHandled();
            }
            long value = battery.getCurrentAverageUa();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_battery",
                    "BatteryManager.getLongProperty",
                    "propertyId=3,field=currentAverageUa,result=" + value,
                    "json-config", "读取配置的电池平均电流（微安，长整型）");
            return AndroidBatteryLongResult.of(value);
        }
        // BATTERY_PROPERTY_ENERGY_COUNTER == 5 (long-only; not via getIntProperty)
        if (propertyId == 5) {
            if (!battery.isEnergyCounterNwhConfigured()) {
                return AndroidBatteryLongResult.notHandled();
            }
            long value = battery.getEnergyCounterNwh();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_battery",
                    "BatteryManager.getLongProperty",
                    "propertyId=5,field=energyCounterNwh,result=" + value,
                    "json-config", "读取配置的电池能量计数（纳瓦时）");
            return AndroidBatteryLongResult.of(value);
        }
        return AndroidBatteryLongResult.notHandled();
    }

    private static final class AndroidWifiIntResult {
        final boolean handled;
        final int value;

        private AndroidWifiIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidWifiIntResult notHandled() {
            return new AndroidWifiIntResult(false, 0);
        }

        static AndroidWifiIntResult of(int value) {
            return new AndroidWifiIntResult(true, value);
        }
    }

    /**
     * Android WifiInfo.getIpAddress little-endian packing: a.b.c.d -&gt; a|(b&lt;&lt;8)|(c&lt;&lt;16)|(d&lt;&lt;24).
     * Input is parse-validated dotted decimal (no DNS).
     */
    private static int wifiIpv4ToAndroidInt(String ipv4) {
        String[] parts = ipv4.split("\\.", -1);
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[1]);
        int c = Integer.parseInt(parts[2]);
        int d = Integer.parseInt(parts[3]);
        return a | (b << 8) | (c << 16) | (d << 24);
    }

    /**
     * WifiManager.getWifiState and WifiInfo integer getters from network.wifi.
     * Signature matched before args. Explicit null ipv4 is handled as 0; missing key is notHandled.
     * {@code getWifiState} is independent of {@code enabled} (no inference).
     */
    private static AndroidWifiIntResult tryAndroidWifiInt(BaseVM vm, String signature) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        // WifiManager.WIFI_STATE_* from network.wifi.state (0..4); presence-gated, no default
        if ("android/net/wifi/WifiManager->getWifiState()I".equals(signature)) {
            if (config == null || !config.isWifiStateConfigured()) {
                return AndroidWifiIntResult.notHandled();
            }
            int value = config.getWifiState(0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_wifi",
                    "WifiManager.getWifiState",
                    "key=state,result=" + value,
                    "json-config", "读取配置的 Wi-Fi 状态码");
            return AndroidWifiIntResult.of(value);
        }
        if ("android/net/wifi/WifiInfo->getIpAddress()I".equals(signature)) {
            if (config == null || !config.isWifiStringConfigured("ipv4")) {
                return AndroidWifiIntResult.notHandled();
            }
            String ipv4 = config.getWifiString("ipv4");
            int value = ipv4 == null ? 0 : wifiIpv4ToAndroidInt(ipv4);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_wifi",
                    "WifiInfo.getIpAddress",
                    "key=ipv4,config=" + String.valueOf(ipv4) + ",result=" + value,
                    "json-config", "读取 Wi-Fi IPv4 地址整型");
            return AndroidWifiIntResult.of(value);
        }
        if ("android/net/wifi/WifiInfo->getRssi()I".equals(signature)) {
            if (config == null || !config.isWifiIntConfigured("rssi")) {
                return AndroidWifiIntResult.notHandled();
            }
            int value = config.getWifiInt("rssi", 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_wifi",
                    "WifiInfo.getRssi", String.valueOf(value),
                    "json-config", "读取 Wi-Fi 信号强度");
            return AndroidWifiIntResult.of(value);
        }
        if ("android/net/wifi/WifiInfo->getLinkSpeed()I".equals(signature)) {
            if (config == null || !config.isWifiIntConfigured("linkSpeedMbps")) {
                return AndroidWifiIntResult.notHandled();
            }
            int value = config.getWifiInt("linkSpeedMbps", 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_wifi",
                    "WifiInfo.getLinkSpeed", String.valueOf(value),
                    "json-config", "读取 Wi-Fi 链路速率");
            return AndroidWifiIntResult.of(value);
        }
        if ("android/net/wifi/WifiInfo->getFrequency()I".equals(signature)) {
            if (config == null || !config.isWifiIntConfigured("frequencyMhz")) {
                return AndroidWifiIntResult.notHandled();
            }
            int value = config.getWifiInt("frequencyMhz", 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_wifi",
                    "WifiInfo.getFrequency", String.valueOf(value),
                    "json-config", "读取 Wi-Fi 频率");
            return AndroidWifiIntResult.of(value);
        }
        if ("android/net/wifi/WifiInfo->getNetworkId()I".equals(signature)) {
            if (config == null || !config.isWifiIntConfigured("networkId")) {
                return AndroidWifiIntResult.notHandled();
            }
            int value = config.getWifiInt("networkId", 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_wifi",
                    "WifiInfo.getNetworkId", String.valueOf(value),
                    "json-config", "读取 Wi-Fi 网络 ID");
            return AndroidWifiIntResult.of(value);
        }
        return AndroidWifiIntResult.notHandled();
    }

    /**
     * Marker value for {@code DhcpInfo} objects created from configured wifi/links DHCP sources.
     * Binds the creating {@link BaseVM} and {@link TraceEnvironmentConfig} instance.
     * Only live same-VM, same-config markers are handled by DhcpInfo int field reads
     * with json-config source.
     */
    private static final class ConfiguredDhcpInfo {
        final BaseVM owner;
        final TraceEnvironmentConfig config;

        private ConfiguredDhcpInfo(BaseVM owner, TraceEnvironmentConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    private static final class AndroidDhcpObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidDhcpObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidDhcpObjectResult notHandled() {
            return new AndroidDhcpObjectResult(false, null);
        }

        static AndroidDhcpObjectResult of(DvmObject<?> value) {
            return new AndroidDhcpObjectResult(true, value);
        }
    }

    private static final class AndroidDhcpIntFieldResult {
        final boolean handled;
        final int value;

        private AndroidDhcpIntFieldResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidDhcpIntFieldResult notHandled() {
            return new AndroidDhcpIntFieldResult(false, 0);
        }

        static AndroidDhcpIntFieldResult of(int value) {
            return new AndroidDhcpIntFieldResult(true, value);
        }
    }

    /**
     * True when any DHCP projection source is configured:
     * network.wifi.ipv4, network.links.gatewayIpv4, dnsServers, dhcpServerIpv4, netmaskIpv4,
     * or leaseDurationSeconds.
     */
    private static boolean isAnyDhcpSourceConfigured(TraceEnvironmentConfig config) {
        if (config == null) {
            return false;
        }
        return config.isWifiStringConfigured("ipv4")
                || config.isLinkStringConfigured("gatewayIpv4")
                || config.isLinkDnsServersConfigured()
                || config.isLinkStringConfigured("dhcpServerIpv4")
                || config.isLinkStringConfigured("netmaskIpv4")
                || config.isLinkIntConfigured("leaseDurationSeconds");
    }

    /**
     * Live same-VM {@link ConfiguredDhcpInfo}: owner matches current {@code vm}, marker config is
     * the same instance as {@link TraceEnvironmentConfig#get} on this emulator, and that config
     * still has any DHCP source. Foreign / stale markers fail.
     */
    private static boolean isLiveConfiguredDhcpInfo(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredDhcpInfo)) {
            return false;
        }
        ConfiguredDhcpInfo marker = (ConfiguredDhcpInfo) dvmObject.getValue();
        if (marker.owner != vm || marker.config == null) {
            return false;
        }
        TraceEnvironmentConfig current = TraceEnvironmentConfig.get(vm.getEmulator());
        return current != null
                && current == marker.config
                && isAnyDhcpSourceConfigured(current);
    }

    /**
     * WifiManager.getDhcpInfo when any relevant DHCP source is configured.
     * Returns DhcpInfo holding a private immutable provenance marker bound to this VM and config.
     */
    private static AndroidDhcpObjectResult tryAndroidDhcpObject(BaseVM vm, String signature) {
        if (!"android/net/wifi/WifiManager->getDhcpInfo()Landroid/net/DhcpInfo;".equals(signature)) {
            return AndroidDhcpObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (!isAnyDhcpSourceConfigured(config)) {
            return AndroidDhcpObjectResult.notHandled();
        }
        DvmObject<?> dhcpInfo = vm.resolveClass("android/net/DhcpInfo")
                .newObject(new ConfiguredDhcpInfo(vm, config));
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_dhcp",
                "WifiManager.getDhcpInfo", "DhcpInfo",
                "json-config", "返回配置的 DhcpInfo 对象");
        return AndroidDhcpObjectResult.of(dhcpInfo);
    }

    /**
     * DhcpInfo int fields for live same-VM, same-config marker objects only.
     * Configured null IPv4 or empty/missing DNS slot returns 0; unconfigured source stays notHandled.
     * {@code netmask} maps from {@code network.links.netmaskIpv4} when that key is present.
     * Foreign-VM / stale markers stay notHandled (existing UOE, no json-config event).
     */
    private static AndroidDhcpIntFieldResult tryAndroidDhcpIntField(BaseVM vm, DvmObject<?> dvmObject,
                                                                    String signature) {
        if (!isLiveConfiguredDhcpInfo(vm, dvmObject)) {
            return AndroidDhcpIntFieldResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null) {
            return AndroidDhcpIntFieldResult.notHandled();
        }
        if ("android/net/DhcpInfo->ipAddress:I".equals(signature)) {
            if (!config.isWifiStringConfigured("ipv4")) {
                return AndroidDhcpIntFieldResult.notHandled();
            }
            String ipv4 = config.getWifiString("ipv4");
            int value = ipv4 == null ? 0 : wifiIpv4ToAndroidInt(ipv4);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_dhcp",
                    "DhcpInfo.ipAddress",
                    "key=ipv4,config=" + String.valueOf(ipv4) + ",result=" + value,
                    "json-config", "读取 DHCP IPv4 地址");
            return AndroidDhcpIntFieldResult.of(value);
        }
        if ("android/net/DhcpInfo->gateway:I".equals(signature)) {
            if (!config.isLinkStringConfigured("gatewayIpv4")) {
                return AndroidDhcpIntFieldResult.notHandled();
            }
            String ipv4 = config.getLinkString("gatewayIpv4");
            int value = ipv4 == null ? 0 : wifiIpv4ToAndroidInt(ipv4);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_dhcp",
                    "DhcpInfo.gateway",
                    "key=gatewayIpv4,config=" + String.valueOf(ipv4) + ",result=" + value,
                    "json-config", "读取 DHCP 网关地址");
            return AndroidDhcpIntFieldResult.of(value);
        }
        if ("android/net/DhcpInfo->dns1:I".equals(signature)) {
            if (!config.isLinkDnsServersConfigured()) {
                return AndroidDhcpIntFieldResult.notHandled();
            }
            List<String> servers = config.getLinkDnsServers();
            String ipv4 = servers.size() > 0 ? servers.get(0) : null;
            int value = ipv4 == null ? 0 : wifiIpv4ToAndroidInt(ipv4);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_dhcp",
                    "DhcpInfo.dns1",
                    "key=dnsServers[0],config=" + String.valueOf(ipv4) + ",result=" + value,
                    "json-config", "读取 DHCP DNS1");
            return AndroidDhcpIntFieldResult.of(value);
        }
        if ("android/net/DhcpInfo->dns2:I".equals(signature)) {
            if (!config.isLinkDnsServersConfigured()) {
                return AndroidDhcpIntFieldResult.notHandled();
            }
            List<String> servers = config.getLinkDnsServers();
            String ipv4 = servers.size() > 1 ? servers.get(1) : null;
            int value = ipv4 == null ? 0 : wifiIpv4ToAndroidInt(ipv4);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_dhcp",
                    "DhcpInfo.dns2",
                    "key=dnsServers[1],config=" + String.valueOf(ipv4) + ",result=" + value,
                    "json-config", "读取 DHCP DNS2");
            return AndroidDhcpIntFieldResult.of(value);
        }
        if ("android/net/DhcpInfo->serverAddress:I".equals(signature)) {
            if (!config.isLinkStringConfigured("dhcpServerIpv4")) {
                return AndroidDhcpIntFieldResult.notHandled();
            }
            String ipv4 = config.getLinkString("dhcpServerIpv4");
            int value = ipv4 == null ? 0 : wifiIpv4ToAndroidInt(ipv4);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_dhcp",
                    "DhcpInfo.serverAddress",
                    "key=dhcpServerIpv4,config=" + String.valueOf(ipv4) + ",result=" + value,
                    "json-config", "读取 DHCP 服务器地址");
            return AndroidDhcpIntFieldResult.of(value);
        }
        if ("android/net/DhcpInfo->netmask:I".equals(signature)) {
            if (!config.isLinkStringConfigured("netmaskIpv4")) {
                return AndroidDhcpIntFieldResult.notHandled();
            }
            String ipv4 = config.getLinkString("netmaskIpv4");
            int value = ipv4 == null ? 0 : wifiIpv4ToAndroidInt(ipv4);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_dhcp",
                    "DhcpInfo.netmask",
                    "key=netmaskIpv4,config=" + String.valueOf(ipv4) + ",result=" + value,
                    "json-config", "读取 DHCP 子网掩码");
            return AndroidDhcpIntFieldResult.of(value);
        }
        if ("android/net/DhcpInfo->leaseDuration:I".equals(signature)) {
            if (!config.isLinkIntConfigured("leaseDurationSeconds")) {
                return AndroidDhcpIntFieldResult.notHandled();
            }
            int value = config.getLinkInt("leaseDurationSeconds", 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_dhcp",
                    "DhcpInfo.leaseDuration", String.valueOf(value),
                    "json-config", "读取 DHCP 租约时长");
            return AndroidDhcpIntFieldResult.of(value);
        }
        return AndroidDhcpIntFieldResult.notHandled();
    }

    /**
     * Marker value for {@code PackageInfo} objects created from {@code android.packages}.
     * Bound to the creating {@link BaseVM}; field readers accept only same-owner live markers
     * (plain / foreign-VM receivers stay notHandled → UOE, no sidecar).
     */
    private static final class ConfiguredPackageInfo {
        final BaseVM owner;
        final TraceEnvironmentConfig.PackageConfig packageConfig;
        final int flags;

        private ConfiguredPackageInfo(BaseVM owner,
                                      TraceEnvironmentConfig.PackageConfig packageConfig, int flags) {
            this.owner = owner;
            this.packageConfig = packageConfig;
            this.flags = flags;
        }
    }

    /**
     * Marker value for {@code ApplicationInfo} objects created from {@code android.packages}.
     * Bound to the creating {@link BaseVM}; field readers accept only same-owner live markers
     * (plain / foreign-VM receivers stay notHandled → UOE, no sidecar).
     */
    private static final class ConfiguredApplicationInfo {
        final BaseVM owner;
        final TraceEnvironmentConfig.PackageConfig packageConfig;
        final int flags;

        private ConfiguredApplicationInfo(BaseVM owner,
                                          TraceEnvironmentConfig.PackageConfig packageConfig, int flags) {
            this.owner = owner;
            this.packageConfig = packageConfig;
            this.flags = flags;
        }
    }

    /**
     * Marker value for {@code InstallSourceInfo} objects created from {@code android.packages}.
     * Bound to the creating {@link BaseVM}; getters accept only same-owner markers
     * (plain / foreign-VM receivers stay notHandled → UOE, no sidecar).
     */
    private static final class ConfiguredInstallSourceInfo {
        final BaseVM owner;
        final TraceEnvironmentConfig.PackageConfig packageConfig;

        private ConfiguredInstallSourceInfo(BaseVM owner,
                                            TraceEnvironmentConfig.PackageConfig packageConfig) {
            this.owner = owner;
            this.packageConfig = packageConfig;
        }
    }

    /**
     * Marker value for {@code FeatureInfo} objects created from {@code android.features}.
     * Bound to the creating {@link BaseVM}; field readers accept only same-owner live markers
     * (plain / foreign-VM receivers stay notHandled → UOE, no sidecar).
     */
    private static final class ConfiguredFeatureInfo {
        final BaseVM owner;
        final TraceEnvironmentConfig.FeatureConfig featureConfig;

        private ConfiguredFeatureInfo(BaseVM owner, TraceEnvironmentConfig.FeatureConfig featureConfig) {
            this.owner = owner;
            this.featureConfig = featureConfig;
        }
    }

    /**
     * Marker value for {@code SigningInfo} objects created from {@code android.packages}
     * when {@code signaturesHex} and/or {@code signingCertificateHistoryHex} is configured.
     * Bound to the creating {@link BaseVM}; getters accept only same-owner live markers
     * (plain / foreign-VM receivers stay notHandled → UOE, no sidecar).
     */
    private static final class ConfiguredSigningInfo {
        final BaseVM owner;
        final TraceEnvironmentConfig.PackageConfig packageConfig;

        private ConfiguredSigningInfo(BaseVM owner, TraceEnvironmentConfig.PackageConfig packageConfig) {
            this.owner = owner;
            this.packageConfig = packageConfig;
        }
    }

    /**
     * Provenance marker for {@code android.os.UserHandle} from configured {@code android.userState}.
     * Binds both the creating {@link BaseVM} (owner identity) and the user-state config instance.
     * Instance methods must use the configured path only; unknown int methods throw UOE (no generic fallback).
     */
    private static final class ConfiguredUserHandle {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidUserStateConfig config;

        private ConfiguredUserHandle(BaseVM owner, TraceEnvironmentConfig.AndroidUserStateConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    private static final class AndroidUserHandleIntResult {
        final boolean handled;
        final int value;

        private AndroidUserHandleIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidUserHandleIntResult notHandled() {
            return new AndroidUserHandleIntResult(false, 0);
        }

        static AndroidUserHandleIntResult of(int value) {
            return new AndroidUserHandleIntResult(true, value);
        }
    }

    private static final class AndroidUserHandleObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidUserHandleObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidUserHandleObjectResult notHandled() {
            return new AndroidUserHandleObjectResult(false, null);
        }

        static AndroidUserHandleObjectResult of(DvmObject<?> value) {
            return new AndroidUserHandleObjectResult(true, value);
        }
    }

    /**
     * True only when {@code dvmObject} holds a {@link ConfiguredUserHandle} created by this {@code vm}
     * (reference identity on {@code owner}, not equals).
     */
    private static boolean isConfiguredUserHandle(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null || !(dvmObject.getValue() instanceof ConfiguredUserHandle)) {
            return false;
        }
        ConfiguredUserHandle marker = (ConfiguredUserHandle) dvmObject.getValue();
        return marker.owner == vm;
    }

    private static final String USER_HANDLE_MY_USER_ID_SIGNATURE =
            "android/os/UserHandle->myUserId()I";
    private static final String USER_HANDLE_MY_USER_HANDLE_SIGNATURE =
            "android/os/UserHandle->myUserHandle()Landroid/os/UserHandle;";
    private static final String USER_HANDLE_GET_IDENTIFIER_SIGNATURE =
            "android/os/UserHandle->getIdentifier()I";
    private static final String USER_MANAGER_GET_SERIAL_NUMBER_FOR_USER_SIGNATURE =
            "android/os/UserManager->getSerialNumberForUser(Landroid/os/UserHandle;)J";
    private static final String USER_MANAGER_GET_USER_HANDLE_FOR_SERIAL_NUMBER_SIGNATURE =
            "android/os/UserManager->getUserHandleForSerialNumber(J)Landroid/os/UserHandle;";

    private static final class AndroidUserManagerLongResult {
        final boolean handled;
        final long value;

        private AndroidUserManagerLongResult(boolean handled, long value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidUserManagerLongResult notHandled() {
            return new AndroidUserManagerLongResult(false, 0L);
        }

        static AndroidUserManagerLongResult of(long value) {
            return new AndroidUserManagerLongResult(true, value);
        }
    }

    /**
     * {@code UserManager.getSerialNumberForUser(UserHandle)} when {@code android.userState} is
     * configured and arg0 is a {@link ConfiguredUserHandle} owned by this {@link BaseVM}, with
     * config identity matching this emulator's current user-state config (both reference identity,
     * not equals). Null/plain handle, foreign marker, or missing node is notHandled (UOE path, no event).
     */
    private static AndroidUserManagerLongResult tryAndroidUserManagerGetSerialNumberForUser(
            BaseVM vm, String signature, VarArg args) {
        if (!USER_MANAGER_GET_SERIAL_NUMBER_FOR_USER_SIGNATURE.equals(signature)) {
            return AndroidUserManagerLongResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidUserStateConfigured()) {
            return AndroidUserManagerLongResult.notHandled();
        }
        DvmObject<?> handleArg = args.getObjectArg(0);
        if (!isConfiguredUserHandle(vm, handleArg)) {
            return AndroidUserManagerLongResult.notHandled();
        }
        ConfiguredUserHandle marker = (ConfiguredUserHandle) handleArg.getValue();
        // Second layer: marker must reference this emulator's current config instance.
        if (marker.config != config.getAndroidUserStateConfig()) {
            return AndroidUserManagerLongResult.notHandled();
        }
        long serialNumber = marker.config.getSerialNumber();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_user",
                "UserManager.getSerialNumberForUser",
                "result=" + serialNumber,
                "json-config", "读取配置的用户序列号");
        return AndroidUserManagerLongResult.of(serialNumber);
    }

    /**
     * {@code UserManager.getUserHandleForSerialNumber(long)} when {@code android.userState} is
     * configured. On serial match, returns a new {@link ConfiguredUserHandle} owned by this
     * {@link BaseVM} and bound to the same config instance; on mismatch, handled {@code null}.
     * Missing node is notHandled (UOE path, no event). Does not accept or produce cross-VM markers.
     */
    private static AndroidUserHandleObjectResult tryAndroidUserManagerGetUserHandleForSerialNumber(
            BaseVM vm, String signature, VarArg args) {
        if (!USER_MANAGER_GET_USER_HANDLE_FOR_SERIAL_NUMBER_SIGNATURE.equals(signature)) {
            return AndroidUserHandleObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidUserStateConfigured()) {
            return AndroidUserHandleObjectResult.notHandled();
        }
        long serialNumber = args.getLongArg(0);
        TraceEnvironmentConfig.AndroidUserStateConfig userStateConfig = config.getAndroidUserStateConfig();
        if (serialNumber == userStateConfig.getSerialNumber()) {
            DvmObject<?> handle = vm.resolveClass("android/os/UserHandle")
                    .newObject(new ConfiguredUserHandle(vm, userStateConfig));
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_user",
                    "UserManager.getUserHandleForSerialNumber",
                    "serialNumber=" + serialNumber + ",userId=" + userStateConfig.getUserId(),
                    "json-config", "根据序列号匹配配置的 UserHandle");
            return AndroidUserHandleObjectResult.of(handle);
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_user",
                "UserManager.getUserHandleForSerialNumber",
                "serialNumber=" + serialNumber + ",result=null",
                "json-config", "序列号未匹配配置的用户");
        return AndroidUserHandleObjectResult.of(null);
    }

    /**
     * Static {@code UserHandle.myUserId()} when {@code android.userState} is configured.
     * Missing node is notHandled (UOE path, no event).
     */
    private static AndroidUserHandleIntResult tryAndroidUserHandleStaticInt(BaseVM vm, String signature) {
        if (!USER_HANDLE_MY_USER_ID_SIGNATURE.equals(signature)) {
            return AndroidUserHandleIntResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidUserStateConfigured()) {
            return AndroidUserHandleIntResult.notHandled();
        }
        int userId = config.getAndroidUserStateConfig().getUserId();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_user",
                "UserHandle.myUserId",
                "result=" + userId,
                "json-config", "读取配置的当前用户 ID");
        return AndroidUserHandleIntResult.of(userId);
    }

    /**
     * Static {@code UserHandle.myUserHandle()} when {@code android.userState} is configured.
     * Returns a private {@link ConfiguredUserHandle} marker. Missing node is notHandled.
     */
    private static AndroidUserHandleObjectResult tryAndroidUserHandleStaticObject(BaseVM vm, DvmClass dvmClass,
                                                                                   String signature) {
        if (!USER_HANDLE_MY_USER_HANDLE_SIGNATURE.equals(signature)) {
            return AndroidUserHandleObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidUserStateConfigured()) {
            return AndroidUserHandleObjectResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidUserStateConfig userStateConfig = config.getAndroidUserStateConfig();
        DvmObject<?> handle = dvmClass.newObject(new ConfiguredUserHandle(vm, userStateConfig));
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_user",
                "UserHandle.myUserHandle",
                "userId=" + userStateConfig.getUserId(),
                "json-config", "读取配置的当前 UserHandle");
        return AndroidUserHandleObjectResult.of(handle);
    }

    private static final class AndroidDataDirObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidDataDirObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidDataDirObjectResult notHandled() {
            return new AndroidDataDirObjectResult(false, null);
        }

        static AndroidDataDirObjectResult of(DvmObject<?> value) {
            return new AndroidDataDirObjectResult(true, value);
        }
    }

    /**
     * Instance {@code Context}/{@code ContextWrapper}/{@code Application}
     * fixed dirs ({@code getDataDir}/{@code getFilesDir}/{@code getCacheDir}/
     * {@code getNoBackupFilesDir}/{@code getCodeCacheDir}) and
     * {@code getDir(Ljava/lang/String;I)Ljava/io/File;} only when root {@code android.dataDir}
     * is explicitly configured and non-null.
     * {@code getDataDir}: {@code new File(dataDir)}.
     * Fixed child dirs: {@code new File(dataDir, "files"|"cache"|"no_backup"|"code_cache")}.
     * {@code getDir}: name must be nonempty single segment (no {@code /}, {@code \\}, NUL, CR, LF);
     * returns {@code new File(dataDir, "app_" + name)}; mode is ignored. Does not create host
     * directories/FileIO, load Dex/code, or apply permissions. Missing/null dataDir, invalid name,
     * or wrong signature → notHandled (UOE, no event). No database paths.
     */
    private static AndroidDataDirObjectResult tryAndroidDataDirContextDirs(BaseVM vm, String signature,
                                                                          VarArg args) {
        final boolean isGetDir =
                "android/content/Context->getDir(Ljava/lang/String;I)Ljava/io/File;"
                        .equals(signature)
                        || "android/content/ContextWrapper->getDir(Ljava/lang/String;I)Ljava/io/File;"
                        .equals(signature)
                        || "android/app/Application->getDir(Ljava/lang/String;I)Ljava/io/File;"
                        .equals(signature);
        final boolean isGetDataDir =
                "android/content/Context->getDataDir()Ljava/io/File;".equals(signature)
                        || "android/content/ContextWrapper->getDataDir()Ljava/io/File;"
                        .equals(signature)
                        || "android/app/Application->getDataDir()Ljava/io/File;"
                        .equals(signature);
        final String child;
        final String api;
        final String note;
        if (isGetDataDir) {
            child = null;
            api = "Context.getDataDir";
            note = "读取配置的 dataDir";
        } else if ("android/content/Context->getFilesDir()Ljava/io/File;".equals(signature)
                || "android/content/ContextWrapper->getFilesDir()Ljava/io/File;".equals(signature)
                || "android/app/Application->getFilesDir()Ljava/io/File;".equals(signature)) {
            child = "files";
            api = "Context.getFilesDir";
            note = "读取配置的 dataDir/files";
        } else if ("android/content/Context->getCacheDir()Ljava/io/File;".equals(signature)
                || "android/content/ContextWrapper->getCacheDir()Ljava/io/File;".equals(signature)
                || "android/app/Application->getCacheDir()Ljava/io/File;".equals(signature)) {
            child = "cache";
            api = "Context.getCacheDir";
            note = "读取配置的 dataDir/cache";
        } else if ("android/content/Context->getNoBackupFilesDir()Ljava/io/File;".equals(signature)
                || "android/content/ContextWrapper->getNoBackupFilesDir()Ljava/io/File;"
                .equals(signature)
                || "android/app/Application->getNoBackupFilesDir()Ljava/io/File;"
                .equals(signature)) {
            child = "no_backup";
            api = "Context.getNoBackupFilesDir";
            note = "读取配置的 dataDir/no_backup";
        } else if ("android/content/Context->getCodeCacheDir()Ljava/io/File;".equals(signature)
                || "android/content/ContextWrapper->getCodeCacheDir()Ljava/io/File;"
                .equals(signature)
                || "android/app/Application->getCodeCacheDir()Ljava/io/File;"
                .equals(signature)) {
            child = "code_cache";
            api = "Context.getCodeCacheDir";
            note = "读取配置的 dataDir/code_cache";
        } else if (isGetDir) {
            child = null;
            api = "Context.getDir";
            note = "读取配置的 dataDir/app_<name>";
        } else {
            return AndroidDataDirObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidDataDirConfigured()) {
            return AndroidDataDirObjectResult.notHandled();
        }
        String dataDir = config.getAndroidDataDir();
        if (dataDir == null) {
            return AndroidDataDirObjectResult.notHandled();
        }
        final File dir;
        final String value;
        if (isGetDir) {
            String name = parseAndroidDataDirSingleSegmentName(args);
            if (name == null) {
                return AndroidDataDirObjectResult.notHandled();
            }
            // mode (int arg1) intentionally ignored — no mkdir/mode/permission semantics
            String childName = "app_" + name;
            dir = new File(dataDir, childName);
            value = "name=" + name + ",result=" + dataDir + "/" + childName;
        } else if (isGetDataDir) {
            dir = new File(dataDir);
            value = "result=" + dataDir;
        } else {
            dir = new File(dataDir, child);
            value = "result=" + dataDir + "/" + child;
        }
        DvmObject<?> file = vm.resolveClass("java/io/File").newObject(dir);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_data_dir",
                api,
                value,
                "json-config", note);
        return AndroidDataDirObjectResult.of(file);
    }

    /**
     * Parses {@code getDir} name arg0: nonempty single directory-name String without
     * {@code /}, {@code \\}, NUL, CR, or LF. Returns {@code null} when invalid.
     */
    private static String parseAndroidDataDirSingleSegmentName(VarArg args) {
        DvmObject<?> nameArg = args.getObjectArg(0);
        if (!(nameArg instanceof StringObject)) {
            return null;
        }
        String name = ((StringObject) nameArg).getValue();
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0
                || name.indexOf('\r') >= 0
                || name.indexOf('\n') >= 0) {
            return null;
        }
        return name;
    }

    private static final class FileSystemExternalStorageObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private FileSystemExternalStorageObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static FileSystemExternalStorageObjectResult notHandled() {
            return new FileSystemExternalStorageObjectResult(false, null);
        }

        static FileSystemExternalStorageObjectResult of(DvmObject<?> value) {
            return new FileSystemExternalStorageObjectResult(true, value);
        }
    }

    private static final class FileSystemExternalStorageBooleanResult {
        final boolean handled;
        final boolean value;

        private FileSystemExternalStorageBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static FileSystemExternalStorageBooleanResult notHandled() {
            return new FileSystemExternalStorageBooleanResult(false, false);
        }

        static FileSystemExternalStorageBooleanResult of(boolean value) {
            return new FileSystemExternalStorageBooleanResult(true, value);
        }
    }

    /**
     * Static {@code Environment.getExternalStorageDirectory} (no-arg) /
     * {@code getExternalStorageState} (no-arg and {@code (File)}) /
     * {@code getExternalStoragePublicDirectory(String)} when
     * {@code filesystem.externalStorage} is configured.
     * File overload models only the configured primary directory: arg0 must be a
     * {@link DvmObject} whose value is a {@link File} equal to {@code new File(directory)};
     * otherwise notHandled (no event).
     * Public-directory type must be a nonempty single path segment (no {@code /}, {@code \\},
     * NUL, CR, LF); result is {@code new File(directory, type)} only — no host FileIO.
     * Missing node / invalid arg is notHandled. No-arg sidecar values unchanged.
     */
    private static FileSystemExternalStorageObjectResult tryFilesystemExternalStorageStaticObject(
            BaseVM vm, String signature, VarArg args) {
        final boolean isDirectory =
                "android/os/Environment->getExternalStorageDirectory()Ljava/io/File;"
                        .equals(signature);
        final boolean isStateNoArg =
                "android/os/Environment->getExternalStorageState()Ljava/lang/String;"
                        .equals(signature);
        final boolean isStateWithFile =
                "android/os/Environment->getExternalStorageState(Ljava/io/File;)Ljava/lang/String;"
                        .equals(signature);
        final boolean isPublicDirectory =
                "android/os/Environment->getExternalStoragePublicDirectory(Ljava/lang/String;)Ljava/io/File;"
                        .equals(signature);
        if (!isDirectory && !isStateNoArg && !isStateWithFile && !isPublicDirectory) {
            return FileSystemExternalStorageObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isFilesystemExternalStorageConfigured()) {
            return FileSystemExternalStorageObjectResult.notHandled();
        }
        TraceEnvironmentConfig.FileSystemExternalStorageConfig externalStorage =
                config.getFilesystemExternalStorageConfig();
        if (isDirectory) {
            String directory = externalStorage.getDirectory();
            DvmObject<?> file = vm.resolveClass("java/io/File").newObject(new File(directory));
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                    "Environment.getExternalStorageDirectory",
                    "field=directory,result=" + directory,
                    "json-config", "读取配置的外部存储目录");
            return FileSystemExternalStorageObjectResult.of(file);
        }
        if (isPublicDirectory) {
            String type = parseExternalStoragePublicDirectoryType(args);
            if (type == null) {
                return FileSystemExternalStorageObjectResult.notHandled();
            }
            String directory = externalStorage.getDirectory();
            File publicDir = new File(directory, type);
            // Sidecar uses POSIX join of config directory + type (stable across host OS).
            String resultPath = directory + "/" + type;
            DvmObject<?> file = vm.resolveClass("java/io/File").newObject(publicDir);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                    "Environment.getExternalStoragePublicDirectory",
                    "type=" + type + ",result=" + resultPath,
                    "json-config", "读取配置的外部存储公共目录");
            return FileSystemExternalStorageObjectResult.of(file);
        }
        if (isStateNoArg) {
            String state = externalStorage.getState();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                    "Environment.getExternalStorageState",
                    "field=state,result=" + state,
                    "json-config", "读取配置的外部存储状态");
            return FileSystemExternalStorageObjectResult.of(new StringObject(vm, state));
        }
        // File-argument overload: only primary configured directory
        String directory = externalStorage.getDirectory();
        if (!isPrimaryExternalStorageFileArg(args, directory)) {
            return FileSystemExternalStorageObjectResult.notHandled();
        }
        String state = externalStorage.getState();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                "Environment.getExternalStorageState",
                "field=state,path=" + directory + ",result=" + state,
                "json-config", "读取配置的外部存储状态");
        return FileSystemExternalStorageObjectResult.of(new StringObject(vm, state));
    }

    /**
     * Parses the type argument for {@code getExternalStoragePublicDirectory}: nonempty
     * single directory-name String without {@code /}, {@code \\}, NUL, CR, or LF.
     * Returns {@code null} when arg is missing, not a String, or invalid (caller UOE, no event).
     */
    private static String parseExternalStoragePublicDirectoryType(VarArg args) {
        DvmObject<?> typeArg = args.getObjectArg(0);
        if (!(typeArg instanceof StringObject)) {
            return null;
        }
        String type = ((StringObject) typeArg).getValue();
        if (type == null || type.isEmpty()) {
            return null;
        }
        if (type.indexOf('/') >= 0
                || type.indexOf('\\') >= 0
                || type.indexOf('\0') >= 0
                || type.indexOf('\r') >= 0
                || type.indexOf('\n') >= 0) {
            return null;
        }
        return type;
    }

    /**
     * Instance {@code Context}/{@code ContextWrapper}/{@code Application}
     * {@code getExternalFilesDir(Ljava/lang/String;)Ljava/io/File;},
     * {@code getExternalFilesDirs(Ljava/lang/String;)[Ljava/io/File;},
     * {@code getExternalCacheDir()Ljava/io/File;},
     * {@code getExternalCacheDirs()[Ljava/io/File;}, {@code getObbDir()Ljava/io/File;},
     * {@code getObbDirs()[Ljava/io/File;},
     * and {@code getExternalMediaDirs()[Ljava/io/File;}
     * when {@code filesystem.externalStorage} is configured and the VM package name is available.
     * Files base: {@code <directory>/Android/data/<packageName>/files}
     * (null type → base; non-null type must be nonempty single segment without {@code /},
     * {@code \\}, NUL, CR, LF).
     * {@code getExternalFilesDirs} returns a one-element {@link ArrayObject} of that path only
     * (no multi-volume topology).
     * Cache: {@code <directory>/Android/data/<packageName>/cache};
     * {@code getExternalCacheDirs} returns a one-element {@link ArrayObject} of that path only.
     * OBB dir: {@code <directory>/Android/obb/<packageName>} (path only; no OBB content);
     * {@code getObbDirs} returns a one-element {@link ArrayObject} of that path only.
     * Media dirs: {@code <directory>/Android/media/<packageName>} (path only; no media scanning);
     * {@code getExternalMediaDirs} returns a fresh one-element {@link ArrayObject} of that path only
     * (configured primary volume only).
     * No host FileIO/dir creation or multi-volume config.
     * Missing node / no package / invalid type → notHandled (UOE, no event).
     */
    private static FileSystemExternalStorageObjectResult tryFilesystemExternalStorageContextAppExternalDirs(
            BaseVM vm, String signature, VarArg args) {
        final boolean isExternalFilesDir =
                "android/content/Context->getExternalFilesDir(Ljava/lang/String;)Ljava/io/File;"
                        .equals(signature)
                        || "android/content/ContextWrapper->getExternalFilesDir(Ljava/lang/String;)Ljava/io/File;"
                        .equals(signature)
                        || "android/app/Application->getExternalFilesDir(Ljava/lang/String;)Ljava/io/File;"
                        .equals(signature);
        final boolean isExternalFilesDirs =
                "android/content/Context->getExternalFilesDirs(Ljava/lang/String;)[Ljava/io/File;"
                        .equals(signature)
                        || "android/content/ContextWrapper->getExternalFilesDirs(Ljava/lang/String;)[Ljava/io/File;"
                        .equals(signature)
                        || "android/app/Application->getExternalFilesDirs(Ljava/lang/String;)[Ljava/io/File;"
                        .equals(signature);
        final boolean isExternalCacheDir =
                "android/content/Context->getExternalCacheDir()Ljava/io/File;"
                        .equals(signature)
                        || "android/content/ContextWrapper->getExternalCacheDir()Ljava/io/File;"
                        .equals(signature)
                        || "android/app/Application->getExternalCacheDir()Ljava/io/File;"
                        .equals(signature);
        final boolean isExternalCacheDirs =
                "android/content/Context->getExternalCacheDirs()[Ljava/io/File;"
                        .equals(signature)
                        || "android/content/ContextWrapper->getExternalCacheDirs()[Ljava/io/File;"
                        .equals(signature)
                        || "android/app/Application->getExternalCacheDirs()[Ljava/io/File;"
                        .equals(signature);
        final boolean isObbDir =
                "android/content/Context->getObbDir()Ljava/io/File;"
                        .equals(signature)
                        || "android/content/ContextWrapper->getObbDir()Ljava/io/File;"
                        .equals(signature)
                        || "android/app/Application->getObbDir()Ljava/io/File;"
                        .equals(signature);
        final boolean isObbDirs =
                "android/content/Context->getObbDirs()[Ljava/io/File;"
                        .equals(signature)
                        || "android/content/ContextWrapper->getObbDirs()[Ljava/io/File;"
                        .equals(signature)
                        || "android/app/Application->getObbDirs()[Ljava/io/File;"
                        .equals(signature);
        final boolean isExternalMediaDirs =
                "android/content/Context->getExternalMediaDirs()[Ljava/io/File;"
                        .equals(signature)
                        || "android/content/ContextWrapper->getExternalMediaDirs()[Ljava/io/File;"
                        .equals(signature)
                        || "android/app/Application->getExternalMediaDirs()[Ljava/io/File;"
                        .equals(signature);
        if (!isExternalFilesDir && !isExternalFilesDirs && !isExternalCacheDir
                && !isExternalCacheDirs && !isObbDir && !isObbDirs && !isExternalMediaDirs) {
            return FileSystemExternalStorageObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isFilesystemExternalStorageConfigured()) {
            return FileSystemExternalStorageObjectResult.notHandled();
        }
        String packageName = vm.getPackageName();
        if (packageName == null || packageName.isEmpty()) {
            return FileSystemExternalStorageObjectResult.notHandled();
        }
        String directory = config.getFilesystemExternalStorageConfig().getDirectory();
        // Nested File construction mirrors Android ContextImpl layout; sidecar uses POSIX join.
        if (isExternalCacheDir || isExternalCacheDirs) {
            File cacheDir = new File(new File(new File(new File(directory, "Android"), "data"),
                    packageName), "cache");
            String resultPath = directory + "/Android/data/" + packageName + "/cache";
            DvmObject<?> fileObj = vm.resolveClass("java/io/File").newObject(cacheDir);
            if (isExternalCacheDirs) {
                // Primary volume only: fixed one-element array (no multi-volume topology).
                ArrayObject array = new ArrayObject(fileObj);
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                        "Context.getExternalCacheDirs",
                        "count=1,result=" + resultPath,
                        "json-config", "读取配置的外部应用 cache 目录数组");
                return FileSystemExternalStorageObjectResult.of(array);
            }
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                    "Context.getExternalCacheDir",
                    "result=" + resultPath,
                    "json-config", "读取配置的外部应用 cache 目录");
            return FileSystemExternalStorageObjectResult.of(fileObj);
        }
        if (isObbDir || isObbDirs) {
            File obbDir = new File(new File(new File(directory, "Android"), "obb"), packageName);
            String resultPath = directory + "/Android/obb/" + packageName;
            DvmObject<?> fileObj = vm.resolveClass("java/io/File").newObject(obbDir);
            if (isObbDirs) {
                // Primary volume only: fixed one-element array (no multi-volume topology / OBB content).
                ArrayObject array = new ArrayObject(fileObj);
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                        "Context.getObbDirs",
                        "count=1,result=" + resultPath,
                        "json-config", "读取配置的外部应用 OBB 目录数组");
                return FileSystemExternalStorageObjectResult.of(array);
            }
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                    "Context.getObbDir",
                    "result=" + resultPath,
                    "json-config", "读取配置的外部应用 OBB 目录");
            return FileSystemExternalStorageObjectResult.of(fileObj);
        }
        if (isExternalMediaDirs) {
            File mediaDir = new File(new File(new File(directory, "Android"), "media"), packageName);
            String resultPath = directory + "/Android/media/" + packageName;
            DvmObject<?> fileObj = vm.resolveClass("java/io/File").newObject(mediaDir);
            // Primary volume only: fresh one-element array (no multi-volume topology / media scanning).
            ArrayObject array = new ArrayObject(fileObj);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                    "Context.getExternalMediaDirs",
                    "count=1,result=" + resultPath,
                    "json-config", "读取配置的外部应用 media 目录数组");
            return FileSystemExternalStorageObjectResult.of(array);
        }
        // getExternalFilesDir / getExternalFilesDirs — shared type rules
        final String type;
        DvmObject<?> typeArg = args.getObjectArg(0);
        if (typeArg == null) {
            type = null;
        } else if (typeArg instanceof StringObject) {
            String raw = ((StringObject) typeArg).getValue();
            if (raw == null) {
                type = null;
            } else if (raw.isEmpty()
                    || raw.indexOf('/') >= 0
                    || raw.indexOf('\\') >= 0
                    || raw.indexOf('\0') >= 0
                    || raw.indexOf('\r') >= 0
                    || raw.indexOf('\n') >= 0) {
                return FileSystemExternalStorageObjectResult.notHandled();
            } else {
                type = raw;
            }
        } else {
            return FileSystemExternalStorageObjectResult.notHandled();
        }
        File filesDir = new File(new File(new File(new File(directory, "Android"), "data"),
                packageName), "files");
        String resultPath = directory + "/Android/data/" + packageName + "/files";
        if (type != null) {
            filesDir = new File(filesDir, type);
            resultPath = resultPath + "/" + type;
        }
        DvmObject<?> file = vm.resolveClass("java/io/File").newObject(filesDir);
        if (isExternalFilesDirs) {
            // Primary volume only: fixed one-element array (no multi-volume topology).
            ArrayObject array = new ArrayObject(file);
            final String arrayValue = type != null
                    ? "count=1,type=" + type + ",result=" + resultPath
                    : "count=1,result=" + resultPath;
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                    "Context.getExternalFilesDirs",
                    arrayValue,
                    "json-config", "读取配置的外部应用 files 目录数组");
            return FileSystemExternalStorageObjectResult.of(array);
        }
        final String value = type != null
                ? "type=" + type + ",result=" + resultPath
                : "result=" + resultPath;
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                "Context.getExternalFilesDir",
                value,
                "json-config", "读取配置的外部应用 files 目录");
        return FileSystemExternalStorageObjectResult.of(file);
    }

    /**
     * Static {@code Environment.isExternalStorageEmulated}/{@code isExternalStorageRemovable}
     * (no-arg and {@code (File)}) when {@code filesystem.externalStorage} is configured.
     * File overload only matches the configured primary directory (see object helper).
     * Missing node / null / non-File / non-matching File is notHandled (no event).
     * No-arg sidecar values unchanged.
     */
    private static FileSystemExternalStorageBooleanResult tryFilesystemExternalStorageStaticBoolean(
            BaseVM vm, String signature, VarArg args) {
        final boolean isEmulatedNoArg =
                "android/os/Environment->isExternalStorageEmulated()Z".equals(signature);
        final boolean isRemovableNoArg =
                "android/os/Environment->isExternalStorageRemovable()Z".equals(signature);
        final boolean isEmulatedWithFile =
                "android/os/Environment->isExternalStorageEmulated(Ljava/io/File;)Z"
                        .equals(signature);
        final boolean isRemovableWithFile =
                "android/os/Environment->isExternalStorageRemovable(Ljava/io/File;)Z"
                        .equals(signature);
        if (!isEmulatedNoArg && !isRemovableNoArg && !isEmulatedWithFile && !isRemovableWithFile) {
            return FileSystemExternalStorageBooleanResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isFilesystemExternalStorageConfigured()) {
            return FileSystemExternalStorageBooleanResult.notHandled();
        }
        TraceEnvironmentConfig.FileSystemExternalStorageConfig externalStorage =
                config.getFilesystemExternalStorageConfig();
        if (isEmulatedNoArg) {
            boolean value = externalStorage.isEmulated();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                    "Environment.isExternalStorageEmulated",
                    "field=emulated,result=" + value,
                    "json-config", "读取配置的外部存储是否模拟");
            return FileSystemExternalStorageBooleanResult.of(value);
        }
        if (isRemovableNoArg) {
            boolean value = externalStorage.isRemovable();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                    "Environment.isExternalStorageRemovable",
                    "field=removable,result=" + value,
                    "json-config", "读取配置的外部存储是否可移除");
            return FileSystemExternalStorageBooleanResult.of(value);
        }
        String directory = externalStorage.getDirectory();
        if (!isPrimaryExternalStorageFileArg(args, directory)) {
            return FileSystemExternalStorageBooleanResult.notHandled();
        }
        if (isEmulatedWithFile) {
            boolean value = externalStorage.isEmulated();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                    "Environment.isExternalStorageEmulated",
                    "field=emulated,path=" + directory + ",result=" + value,
                    "json-config", "读取配置的外部存储是否模拟");
            return FileSystemExternalStorageBooleanResult.of(value);
        }
        boolean value = externalStorage.isRemovable();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_external_storage",
                "Environment.isExternalStorageRemovable",
                "field=removable,path=" + directory + ",result=" + value,
                "json-config", "读取配置的外部存储是否可移除");
        return FileSystemExternalStorageBooleanResult.of(value);
    }

    /**
     * True when arg0 is a non-null {@link DvmObject} whose value is a {@link File}
     * equal to {@code new File(directory)} (primary configured external storage path only).
     */
    private static boolean isPrimaryExternalStorageFileArg(VarArg args, String directory) {
        DvmObject<?> fileArg = args.getObjectArg(0);
        if (fileArg == null) {
            return false;
        }
        Object value = fileArg.getValue();
        if (!(value instanceof File)) {
            return false;
        }
        return new File(directory).equals(value);
    }

    private static final class FileSystemSystemDirectoriesObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private FileSystemSystemDirectoriesObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static FileSystemSystemDirectoriesObjectResult notHandled() {
            return new FileSystemSystemDirectoriesObjectResult(false, null);
        }

        static FileSystemSystemDirectoriesObjectResult of(DvmObject<?> value) {
            return new FileSystemSystemDirectoriesObjectResult(true, value);
        }
    }

    /**
     * Static {@code Environment.getRootDirectory} / {@code getDataDirectory} /
     * {@code getDownloadCacheDirectory} / {@code getStorageDirectory} when the corresponding
     * field under {@code filesystem.systemDirectories} is explicitly configured.
     * Returns {@code java.io.File} with value {@code new File(path)} only — does not create host
     * directories or FileIO entries. Missing node / missing field / wrong signature → notHandled
     * (historical UOE fallback, no event). Independent of {@code filesystem.externalStorage};
     * no multi-volume semantics.
     */
    private static FileSystemSystemDirectoriesObjectResult tryFilesystemSystemDirectoriesStaticObject(
            BaseVM vm, String signature) {
        final boolean isRoot =
                "android/os/Environment->getRootDirectory()Ljava/io/File;".equals(signature);
        final boolean isData =
                "android/os/Environment->getDataDirectory()Ljava/io/File;".equals(signature);
        final boolean isDownloadCache =
                "android/os/Environment->getDownloadCacheDirectory()Ljava/io/File;"
                        .equals(signature);
        final boolean isStorage =
                "android/os/Environment->getStorageDirectory()Ljava/io/File;".equals(signature);
        if (!isRoot && !isData && !isDownloadCache && !isStorage) {
            return FileSystemSystemDirectoriesObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isFilesystemSystemDirectoriesConfigured()) {
            return FileSystemSystemDirectoriesObjectResult.notHandled();
        }
        TraceEnvironmentConfig.FileSystemSystemDirectoriesConfig systemDirectories =
                config.getFilesystemSystemDirectoriesConfig();
        if (systemDirectories == null) {
            return FileSystemSystemDirectoriesObjectResult.notHandled();
        }
        if (isRoot) {
            if (!systemDirectories.isRootDirectoryConfigured()) {
                return FileSystemSystemDirectoriesObjectResult.notHandled();
            }
            String path = systemDirectories.getRootDirectory();
            DvmObject<?> file = vm.resolveClass("java/io/File").newObject(new File(path));
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_system_directories",
                    "Environment.getRootDirectory",
                    "field=rootDirectory,result=" + path,
                    "json-config", "读取配置的系统 root 目录");
            return FileSystemSystemDirectoriesObjectResult.of(file);
        }
        if (isData) {
            if (!systemDirectories.isDataDirectoryConfigured()) {
                return FileSystemSystemDirectoriesObjectResult.notHandled();
            }
            String path = systemDirectories.getDataDirectory();
            DvmObject<?> file = vm.resolveClass("java/io/File").newObject(new File(path));
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_system_directories",
                    "Environment.getDataDirectory",
                    "field=dataDirectory,result=" + path,
                    "json-config", "读取配置的系统 data 目录");
            return FileSystemSystemDirectoriesObjectResult.of(file);
        }
        if (isDownloadCache) {
            if (!systemDirectories.isDownloadCacheDirectoryConfigured()) {
                return FileSystemSystemDirectoriesObjectResult.notHandled();
            }
            String path = systemDirectories.getDownloadCacheDirectory();
            DvmObject<?> file = vm.resolveClass("java/io/File").newObject(new File(path));
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_system_directories",
                    "Environment.getDownloadCacheDirectory",
                    "field=downloadCacheDirectory,result=" + path,
                    "json-config", "读取配置的系统 download cache 目录");
            return FileSystemSystemDirectoriesObjectResult.of(file);
        }
        if (!systemDirectories.isStorageDirectoryConfigured()) {
            return FileSystemSystemDirectoriesObjectResult.notHandled();
        }
        String path = systemDirectories.getStorageDirectory();
        DvmObject<?> file = vm.resolveClass("java/io/File").newObject(new File(path));
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "filesystem_system_directories",
                "Environment.getStorageDirectory",
                "field=storageDirectory,result=" + path,
                "json-config", "读取配置的系统 storage 目录");
        return FileSystemSystemDirectoriesObjectResult.of(file);
    }

    /**
     * Instance int methods for ConfiguredUserHandle only: {@code getIdentifier()}.
     * Requires marker.owner == current BaseVM (and configured path). Unsupported signatures on the
     * marker throw rather than falling through to generic handlers. Foreign markers are notHandled.
     */
    private static AndroidUserHandleIntResult tryAndroidUserHandleInt(BaseVM vm, DvmObject<?> dvmObject,
                                                                      String signature) {
        if (!isConfiguredUserHandle(vm, dvmObject)) {
            return AndroidUserHandleIntResult.notHandled();
        }
        ConfiguredUserHandle marker = (ConfiguredUserHandle) dvmObject.getValue();
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidUserStateConfigured()
                || marker.config != config.getAndroidUserStateConfig()) {
            return AndroidUserHandleIntResult.notHandled();
        }
        if (!USER_HANDLE_GET_IDENTIFIER_SIGNATURE.equals(signature)) {
            throw new UnsupportedOperationException(signature);
        }
        int userId = marker.config.getUserId();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_user",
                "UserHandle.getIdentifier",
                "result=" + userId,
                "json-config", "读取配置的 UserHandle 标识");
        return AndroidUserHandleIntResult.of(userId);
    }

    /**
     * Provenance marker for one {@code AccessibilityServiceInfo} from
     * {@code android.accessibility.services}. Binds creating {@link BaseVM}.
     */
    private static final class ConfiguredAccessibilityServiceInfo {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidAccessibilityServiceConfig serviceConfig;

        private ConfiguredAccessibilityServiceInfo(
                BaseVM owner,
                TraceEnvironmentConfig.AndroidAccessibilityServiceConfig serviceConfig) {
            this.owner = owner;
            this.serviceConfig = serviceConfig;
        }
    }

    private static final class AndroidAccessibilityServiceObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidAccessibilityServiceObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidAccessibilityServiceObjectResult notHandled() {
            return new AndroidAccessibilityServiceObjectResult(false, null);
        }

        static AndroidAccessibilityServiceObjectResult of(DvmObject<?> value) {
            return new AndroidAccessibilityServiceObjectResult(true, value);
        }
    }

    private static boolean isConfiguredAccessibilityServiceInfo(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredAccessibilityServiceInfo)) {
            return false;
        }
        ConfiguredAccessibilityServiceInfo marker =
                (ConfiguredAccessibilityServiceInfo) dvmObject.getValue();
        return marker.owner == vm;
    }

    private static final String A11Y_GET_INSTALLED_SERVICE_LIST_SIGNATURE =
            "android/view/accessibility/AccessibilityManager->"
                    + "getInstalledAccessibilityServiceList()Ljava/util/List;";
    private static final String A11Y_GET_ENABLED_SERVICE_LIST_SIGNATURE =
            "android/view/accessibility/AccessibilityManager->"
                    + "getEnabledAccessibilityServiceList(I)Ljava/util/List;";
    private static final String A11Y_SERVICE_INFO_GET_ID_SIGNATURE =
            "android/accessibilityservice/AccessibilityServiceInfo->getId()Ljava/lang/String;";
    private static final String A11Y_SERVICE_INFO_CLASS =
            "android/accessibilityservice/AccessibilityServiceInfo";

    /**
     * Accessibility service lists when {@code android.accessibility.services} is configured, and
     * {@code AccessibilityServiceInfo.getId()} on same-VM markers. Exact signatures only.
     * <ul>
     *   <li>list signatures always require SystemService accessibility first (else notHandled)</li>
     *   <li>services configured → fresh lists + sidecar</li>
     *   <li>services missing + {@code getEnabledAccessibilityServiceList} → legacy fresh empty
     *       list, no sidecar</li>
     *   <li>services missing + installed list / wrong marker getId → notHandled</li>
     * </ul>
     * Feedback mask int is accepted but ignored. No settings/callbacks/lifecycle.
     */
    private static AndroidAccessibilityServiceObjectResult tryAndroidAccessibilityServiceObjectMethod(
            BaseVM vm, DvmObject<?> dvmObject, String signature) {
        final boolean installed =
                A11Y_GET_INSTALLED_SERVICE_LIST_SIGNATURE.equals(signature);
        final boolean enabledList =
                A11Y_GET_ENABLED_SERVICE_LIST_SIGNATURE.equals(signature);
        if (installed || enabledList) {
            // Receiver gate applies to both configured and legacy list paths
            if (!isSystemServiceAccessibilityManager(dvmObject)) {
                return AndroidAccessibilityServiceObjectResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            boolean servicesConfigured = config != null
                    && config.isAndroidAccessibilityConfigured()
                    && config.getAndroidAccessibilityConfig() != null
                    && config.getAndroidAccessibilityConfig().isServicesConfigured();
            if (servicesConfigured) {
                TraceEnvironmentConfig.AndroidAccessibilityConfig a11y =
                        config.getAndroidAccessibilityConfig();
                List<TraceEnvironmentConfig.AndroidAccessibilityServiceConfig> all =
                        a11y.getServices();
                List<DvmObject<?>> elements = new ArrayList<DvmObject<?>>();
                DvmClass infoClass = vm.resolveClass(A11Y_SERVICE_INFO_CLASS);
                for (int i = 0; i < all.size(); i++) {
                    TraceEnvironmentConfig.AndroidAccessibilityServiceConfig entry = all.get(i);
                    if (enabledList && !entry.isEnabled()) {
                        continue;
                    }
                    elements.add(infoClass.newObject(
                            new ConfiguredAccessibilityServiceInfo(vm, entry)));
                }
                ArrayListObject list = new ArrayListObject(vm, elements);
                String api = enabledList
                        ? "AccessibilityManager.getEnabledAccessibilityServiceList"
                        : "AccessibilityManager.getInstalledAccessibilityServiceList";
                String note = enabledList
                        ? "返回配置的已启用无障碍服务列表"
                        : "返回配置的已安装无障碍服务列表";
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_accessibility",
                        api, "count=" + elements.size(), "json-config", note);
                return AndroidAccessibilityServiceObjectResult.of(list);
            }
            // Legacy: fixed empty enabled list only on correct receiver; installed stays unsupported
            if (enabledList) {
                return AndroidAccessibilityServiceObjectResult.of(
                        new ArrayListObject(vm, Collections.emptyList()));
            }
            return AndroidAccessibilityServiceObjectResult.notHandled();
        }
        if (A11Y_SERVICE_INFO_GET_ID_SIGNATURE.equals(signature)) {
            if (!isConfiguredAccessibilityServiceInfo(vm, dvmObject)) {
                return AndroidAccessibilityServiceObjectResult.notHandled();
            }
            ConfiguredAccessibilityServiceInfo marker =
                    (ConfiguredAccessibilityServiceInfo) dvmObject.getValue();
            return AndroidAccessibilityServiceObjectResult.of(
                    new StringObject(vm, marker.serviceConfig.getId()));
        }
        return AndroidAccessibilityServiceObjectResult.notHandled();
    }

    /**
     * Provenance marker for one {@code InputMethodInfo} from {@code android.inputMethods}.
     * Binds creating {@link BaseVM}; {@code getId()} requires same-VM owner.
     */
    private static final class ConfiguredInputMethodInfo {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidInputMethodConfig imeConfig;

        private ConfiguredInputMethodInfo(BaseVM owner,
                                          TraceEnvironmentConfig.AndroidInputMethodConfig imeConfig) {
            this.owner = owner;
            this.imeConfig = imeConfig;
        }
    }

    private static final class AndroidInputMethodObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidInputMethodObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidInputMethodObjectResult notHandled() {
            return new AndroidInputMethodObjectResult(false, null);
        }

        static AndroidInputMethodObjectResult of(DvmObject<?> value) {
            return new AndroidInputMethodObjectResult(true, value);
        }
    }

    private static boolean isSystemServiceInputMethodManager(DvmObject<?> dvmObject) {
        return dvmObject instanceof SystemService
                && SystemService.INPUT_METHOD_SERVICE.equals(dvmObject.getValue());
    }

    private static boolean isConfiguredInputMethodInfo(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredInputMethodInfo)) {
            return false;
        }
        ConfiguredInputMethodInfo marker = (ConfiguredInputMethodInfo) dvmObject.getValue();
        return marker.owner == vm;
    }

    private static final String IMM_GET_INPUT_METHOD_LIST_SIGNATURE =
            "android/view/inputmethod/InputMethodManager->getInputMethodList()Ljava/util/List;";
    private static final String IMM_GET_ENABLED_INPUT_METHOD_LIST_SIGNATURE =
            "android/view/inputmethod/InputMethodManager->getEnabledInputMethodList()Ljava/util/List;";
    private static final String INPUT_METHOD_INFO_GET_ID_SIGNATURE =
            "android/view/inputmethod/InputMethodInfo->getId()Ljava/lang/String;";
    private static final String INPUT_METHOD_INFO_CLASS =
            "android/view/inputmethod/InputMethodInfo";

    /**
     * InputMethodManager list APIs on SystemService {@code input_method} when
     * {@code android.inputMethods} is configured, and {@code InputMethodInfo.getId()} on
     * same-VM markers. Exact signatures only. List calls emit {@code android_input_method}
     * sidecar; getId does not. Missing node / wrong receiver / cross-VM / wrong signature
     * is notHandled (UOE, no event). No subtype/current/switch/settings/listeners.
     */
    private static AndroidInputMethodObjectResult tryAndroidInputMethodObjectMethod(
            BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (IMM_GET_INPUT_METHOD_LIST_SIGNATURE.equals(signature)
                || IMM_GET_ENABLED_INPUT_METHOD_LIST_SIGNATURE.equals(signature)) {
            if (!isSystemServiceInputMethodManager(dvmObject)) {
                return AndroidInputMethodObjectResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidInputMethodsConfigured()) {
                return AndroidInputMethodObjectResult.notHandled();
            }
            boolean enabledOnly =
                    IMM_GET_ENABLED_INPUT_METHOD_LIST_SIGNATURE.equals(signature);
            List<TraceEnvironmentConfig.AndroidInputMethodConfig> all =
                    config.getAndroidInputMethods();
            List<DvmObject<?>> elements = new ArrayList<DvmObject<?>>();
            DvmClass infoClass = vm.resolveClass(INPUT_METHOD_INFO_CLASS);
            for (int i = 0; i < all.size(); i++) {
                TraceEnvironmentConfig.AndroidInputMethodConfig entry = all.get(i);
                if (enabledOnly && !entry.isEnabled()) {
                    continue;
                }
                elements.add(infoClass.newObject(new ConfiguredInputMethodInfo(vm, entry)));
            }
            ArrayListObject list = new ArrayListObject(vm, elements);
            String api = enabledOnly
                    ? "InputMethodManager.getEnabledInputMethodList"
                    : "InputMethodManager.getInputMethodList";
            String note = enabledOnly
                    ? "返回配置的已启用输入法列表"
                    : "返回配置的输入法列表";
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_input_method",
                    api, "count=" + elements.size(), "json-config", note);
            return AndroidInputMethodObjectResult.of(list);
        }
        if (INPUT_METHOD_INFO_GET_ID_SIGNATURE.equals(signature)) {
            if (!isConfiguredInputMethodInfo(vm, dvmObject)) {
                return AndroidInputMethodObjectResult.notHandled();
            }
            ConfiguredInputMethodInfo marker = (ConfiguredInputMethodInfo) dvmObject.getValue();
            return AndroidInputMethodObjectResult.of(
                    new StringObject(vm, marker.imeConfig.getId()));
        }
        return AndroidInputMethodObjectResult.notHandled();
    }

    /**
     * Provenance marker for {@code AccountManager} from configured {@code android.accounts}.
     * Binds the creating {@link BaseVM} (owner identity). Instance methods require
     * {@code owner ==} current VM; unknown methods on same-VM marker throw UOE.
     */
    private static final class ConfiguredAccountManager {
        final BaseVM owner;
        final TraceEnvironmentConfig config;

        private ConfiguredAccountManager(BaseVM owner, TraceEnvironmentConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    /**
     * Provenance marker for one {@code Account} entry from {@code android.accounts}.
     * Binds the creating {@link BaseVM} (owner identity). Object fields require
     * {@code owner ==} current VM; unknown fields on same-VM marker throw UOE.
     */
    private static final class ConfiguredAccount {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidAccountConfig accountConfig;

        private ConfiguredAccount(BaseVM owner,
                                  TraceEnvironmentConfig.AndroidAccountConfig accountConfig) {
            this.owner = owner;
            this.accountConfig = accountConfig;
        }
    }

    private static final class AndroidAccountObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidAccountObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidAccountObjectResult notHandled() {
            return new AndroidAccountObjectResult(false, null);
        }

        static AndroidAccountObjectResult of(DvmObject<?> value) {
            return new AndroidAccountObjectResult(true, value);
        }
    }

    /**
     * True only when {@code dvmObject} holds a {@link ConfiguredAccountManager} created by this
     * {@code vm} (reference identity on {@code owner}, not equals).
     */
    private static boolean isConfiguredAccountManager(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredAccountManager)) {
            return false;
        }
        ConfiguredAccountManager marker = (ConfiguredAccountManager) dvmObject.getValue();
        return marker.owner == vm;
    }

    /**
     * True only when {@code dvmObject} holds a {@link ConfiguredAccount} created by this
     * {@code vm} (reference identity on {@code owner}, not equals).
     */
    private static boolean isConfiguredAccount(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredAccount)) {
            return false;
        }
        ConfiguredAccount marker = (ConfiguredAccount) dvmObject.getValue();
        return marker.owner == vm;
    }

    private static final String ACCOUNT_MANAGER_GET_SIGNATURE =
            "android/accounts/AccountManager->get(Landroid/content/Context;)"
                    + "Landroid/accounts/AccountManager;";
    private static final String ACCOUNT_MANAGER_GET_ACCOUNTS_SIGNATURE =
            "android/accounts/AccountManager->getAccounts()[Landroid/accounts/Account;";
    private static final String ACCOUNT_MANAGER_GET_ACCOUNTS_BY_TYPE_SIGNATURE =
            "android/accounts/AccountManager->getAccountsByType(Ljava/lang/String;)"
                    + "[Landroid/accounts/Account;";
    private static final String ACCOUNT_NAME_FIELD_SIGNATURE =
            "android/accounts/Account->name:Ljava/lang/String;";
    private static final String ACCOUNT_TYPE_FIELD_SIGNATURE =
            "android/accounts/Account->type:Ljava/lang/String;";
    private static final String ACCOUNT_MANAGER_CLASS = "android/accounts/AccountManager";
    private static final String ACCOUNT_CLASS = "android/accounts/Account";

    /**
     * When {@code android.accounts} is configured, returns a private marker for
     * {@code AccountManager.get(Context)} bound to this {@link BaseVM}.
     * Missing node is notHandled (UOE path, no event).
     */
    private static AndroidAccountObjectResult tryAndroidAccountManagerStaticObject(BaseVM vm,
                                                                                   String signature) {
        if (!ACCOUNT_MANAGER_GET_SIGNATURE.equals(signature)) {
            return AndroidAccountObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidAccountsConfigured()) {
            return AndroidAccountObjectResult.notHandled();
        }
        DvmObject<?> manager = vm.resolveClass(ACCOUNT_MANAGER_CLASS)
                .newObject(new ConfiguredAccountManager(vm, config));
        return AndroidAccountObjectResult.of(manager);
    }

    /**
     * Same-VM {@link ConfiguredAccountManager} only:
     * <ul>
     *   <li>{@code getAccounts()} — full list, sidecar {@code AccountManager.getAccounts}</li>
     *   <li>{@code getAccountsByType(String)} — exact type match, non-null {@link StringObject}
     *       required; sidecar {@code AccountManager.getAccountsByType}</li>
     * </ul>
     * Fresh {@link ArrayObject} of same-VM {@link ConfiguredAccount} markers in JSON order.
     * Cross-VM / plain marker / null or non-String type arg is notHandled (UOE, no event).
     * Other signatures on same-VM marker throw UOE.
     */
    private static AndroidAccountObjectResult tryAndroidAccountManagerObjectMethod(BaseVM vm,
                                                                                   DvmObject<?> dvmObject,
                                                                                   String signature,
                                                                                   VarArg args) {
        if (!isConfiguredAccountManager(vm, dvmObject)) {
            return AndroidAccountObjectResult.notHandled();
        }
        if (ACCOUNT_MANAGER_GET_ACCOUNTS_SIGNATURE.equals(signature)) {
            ConfiguredAccountManager marker = (ConfiguredAccountManager) dvmObject.getValue();
            ArrayObject array = buildConfiguredAccountArray(vm, marker.config.getAndroidAccounts(),
                    null);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_account",
                    "AccountManager.getAccounts",
                    "count=" + array.length(),
                    "json-config", "返回配置的账户列表");
            return AndroidAccountObjectResult.of(array);
        }
        if (ACCOUNT_MANAGER_GET_ACCOUNTS_BY_TYPE_SIGNATURE.equals(signature)) {
            DvmObject<?> typeArg = args.getObjectArg(0);
            if (!(typeArg instanceof StringObject)) {
                return AndroidAccountObjectResult.notHandled();
            }
            String type = ((StringObject) typeArg).getValue();
            if (type == null) {
                return AndroidAccountObjectResult.notHandled();
            }
            ConfiguredAccountManager marker = (ConfiguredAccountManager) dvmObject.getValue();
            ArrayObject array = buildConfiguredAccountArray(vm, marker.config.getAndroidAccounts(),
                    type);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_account",
                    "AccountManager.getAccountsByType",
                    "type=" + type + ",count=" + array.length(),
                    "json-config", "返回配置的指定类型账户列表");
            return AndroidAccountObjectResult.of(array);
        }
        throw new UnsupportedOperationException(signature);
    }

    /**
     * Build a fresh Account[] of same-VM markers from {@code accounts} in JSON order.
     * When {@code typeFilter} is non-null, only entries whose configured type equals it are included.
     */
    private static ArrayObject buildConfiguredAccountArray(
            BaseVM vm,
            List<TraceEnvironmentConfig.AndroidAccountConfig> accounts,
            String typeFilter) {
        List<DvmObject<?>> matched = new ArrayList<DvmObject<?>>();
        DvmClass accountClass = vm.resolveClass(ACCOUNT_CLASS);
        for (int i = 0; i < accounts.size(); i++) {
            TraceEnvironmentConfig.AndroidAccountConfig entry = accounts.get(i);
            if (typeFilter != null && !typeFilter.equals(entry.getType())) {
                continue;
            }
            matched.add(accountClass.newObject(new ConfiguredAccount(vm, entry)));
        }
        return new ArrayObject(matched.toArray(new DvmObject<?>[matched.size()]));
    }

    /**
     * {@code Account.name} / {@code Account.type} only on same-VM {@link ConfiguredAccount}.
     * No sidecar. Cross-VM marker is notHandled. Wrong field on same-VM marker throws UOE.
     */
    private static AndroidAccountObjectResult tryAndroidAccountObjectField(BaseVM vm,
                                                                           DvmObject<?> dvmObject,
                                                                           String signature) {
        if (!isConfiguredAccount(vm, dvmObject)) {
            return AndroidAccountObjectResult.notHandled();
        }
        ConfiguredAccount marker = (ConfiguredAccount) dvmObject.getValue();
        if (ACCOUNT_NAME_FIELD_SIGNATURE.equals(signature)) {
            return AndroidAccountObjectResult.of(
                    new StringObject(vm, marker.accountConfig.getName()));
        }
        if (ACCOUNT_TYPE_FIELD_SIGNATURE.equals(signature)) {
            return AndroidAccountObjectResult.of(
                    new StringObject(vm, marker.accountConfig.getType()));
        }
        throw new UnsupportedOperationException(signature);
    }

    /**
     * Provenance marker for {@code AdvertisingIdClient.Info} from configured {@code android.identifiers}.
     * Binds the creating {@link BaseVM}; instance getters require {@code owner ==} current VM.
     * Instance methods must use the configured path only; unknown methods throw UOE (no generic fallback).
     */
    private static final class ConfiguredAdvertisingIdInfo {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidIdentifiersConfig config;

        private ConfiguredAdvertisingIdInfo(BaseVM owner,
                                            TraceEnvironmentConfig.AndroidIdentifiersConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    private static final class AndroidAdvertisingIdObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidAdvertisingIdObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidAdvertisingIdObjectResult notHandled() {
            return new AndroidAdvertisingIdObjectResult(false, null);
        }

        static AndroidAdvertisingIdObjectResult of(DvmObject<?> value) {
            return new AndroidAdvertisingIdObjectResult(true, value);
        }
    }

    private static final class AndroidAdvertisingIdBooleanResult {
        final boolean handled;
        final boolean value;

        private AndroidAdvertisingIdBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidAdvertisingIdBooleanResult notHandled() {
            return new AndroidAdvertisingIdBooleanResult(false, false);
        }

        static AndroidAdvertisingIdBooleanResult of(boolean value) {
            return new AndroidAdvertisingIdBooleanResult(true, value);
        }
    }

    /**
     * True only when {@code dvmObject} holds a {@link ConfiguredAdvertisingIdInfo} created by
     * this {@code vm} (reference identity on {@code owner}, not class name). Cross-VM markers,
     * ordinary/foreign objects, and nulls are rejected.
     */
    private static boolean isConfiguredAdvertisingIdInfo(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredAdvertisingIdInfo)) {
            return false;
        }
        ConfiguredAdvertisingIdInfo marker = (ConfiguredAdvertisingIdInfo) dvmObject.getValue();
        return marker.owner == vm;
    }

    private static final String ADVERTISING_ID_GET_INFO_SIGNATURE =
            "com/google/android/gms/ads/identifier/AdvertisingIdClient->"
                    + "getAdvertisingIdInfo(Landroid/content/Context;)"
                    + "Lcom/google/android/gms/ads/identifier/AdvertisingIdClient$Info;";
    private static final String ADVERTISING_ID_INFO_GET_ID_SIGNATURE =
            "com/google/android/gms/ads/identifier/AdvertisingIdClient$Info->getId()Ljava/lang/String;";
    private static final String ADVERTISING_ID_INFO_IS_LIMIT_SIGNATURE =
            "com/google/android/gms/ads/identifier/AdvertisingIdClient$Info->isLimitAdTrackingEnabled()Z";
    private static final String ADVERTISING_ID_INFO_CLASS =
            "com/google/android/gms/ads/identifier/AdvertisingIdClient$Info";

    /**
     * When {@code android.identifiers} is configured, returns a private marker for
     * {@code AdvertisingIdClient.getAdvertisingIdInfo}. Missing node is notHandled (UOE path, no event).
     */
    private static AndroidAdvertisingIdObjectResult tryAndroidAdvertisingIdStaticObject(BaseVM vm,
                                                                                        String signature) {
        if (!ADVERTISING_ID_GET_INFO_SIGNATURE.equals(signature)) {
            return AndroidAdvertisingIdObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidIdentifiersConfigured()) {
            return AndroidAdvertisingIdObjectResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidIdentifiersConfig identifiersConfig =
                config.getAndroidIdentifiersConfig();
        DvmObject<?> info = vm.resolveClass(ADVERTISING_ID_INFO_CLASS)
                .newObject(new ConfiguredAdvertisingIdInfo(vm, identifiersConfig));
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_identifier",
                "AdvertisingIdClient.getAdvertisingIdInfo",
                "advertisingId=" + identifiersConfig.getAdvertisingId()
                        + ",limitAdTracking=" + identifiersConfig.isLimitAdTracking(),
                "json-config", "读取配置的 AdvertisingIdClient.Info");
        return AndroidAdvertisingIdObjectResult.of(info);
    }

    /**
     * Instance object methods for ConfiguredAdvertisingIdInfo only: {@code getId()}.
     * Requires {@code marker.owner ==} current {@link BaseVM}. Unsupported signatures on the
     * same-VM marker throw rather than falling through to generic handlers.
     */
    private static AndroidAdvertisingIdObjectResult tryAndroidAdvertisingIdObjectMethod(BaseVM vm,
                                                                                        DvmObject<?> dvmObject,
                                                                                        String signature) {
        if (!isConfiguredAdvertisingIdInfo(vm, dvmObject)) {
            return AndroidAdvertisingIdObjectResult.notHandled();
        }
        ConfiguredAdvertisingIdInfo marker = (ConfiguredAdvertisingIdInfo) dvmObject.getValue();
        if (!ADVERTISING_ID_INFO_GET_ID_SIGNATURE.equals(signature)) {
            throw new UnsupportedOperationException(signature);
        }
        String advertisingId = marker.config.getAdvertisingId();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_identifier",
                "AdvertisingIdInfo.getId",
                "result=" + advertisingId,
                "json-config", "读取配置的广告标识符");
        return AndroidAdvertisingIdObjectResult.of(new StringObject(vm, advertisingId));
    }

    /**
     * Instance boolean methods for ConfiguredAdvertisingIdInfo only: {@code isLimitAdTrackingEnabled()}.
     * Requires {@code marker.owner ==} current {@link BaseVM}. Unsupported signatures on the
     * same-VM marker throw rather than falling through to generic handlers.
     */
    private static AndroidAdvertisingIdBooleanResult tryAndroidAdvertisingIdBoolean(BaseVM vm,
                                                                                    DvmObject<?> dvmObject,
                                                                                    String signature) {
        if (!isConfiguredAdvertisingIdInfo(vm, dvmObject)) {
            return AndroidAdvertisingIdBooleanResult.notHandled();
        }
        ConfiguredAdvertisingIdInfo marker = (ConfiguredAdvertisingIdInfo) dvmObject.getValue();
        if (!ADVERTISING_ID_INFO_IS_LIMIT_SIGNATURE.equals(signature)) {
            throw new UnsupportedOperationException(signature);
        }
        boolean limit = marker.config.isLimitAdTracking();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_identifier",
                "AdvertisingIdInfo.isLimitAdTrackingEnabled",
                "result=" + limit,
                "json-config", "读取配置的限制广告跟踪状态");
        return AndroidAdvertisingIdBooleanResult.of(limit);
    }

    /**
     * Provenance marker for {@code AppSetIdClient} from configured {@code android.identifiers.appSetId}.
     * Binds the creating {@link BaseVM}; instance methods require {@code owner ==} current VM.
     */
    private static final class ConfiguredAppSetIdClient {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidIdentifiersConfig config;

        private ConfiguredAppSetIdClient(BaseVM owner,
                                         TraceEnvironmentConfig.AndroidIdentifiersConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    /**
     * Completed {@code Task} marker produced by this App Set ID path only.
     * {@code getResult()} returns a same-VM {@link ConfiguredAppSetIdInfo}.
     */
    private static final class ConfiguredAppSetIdTask {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidIdentifiersConfig config;

        private ConfiguredAppSetIdTask(BaseVM owner,
                                       TraceEnvironmentConfig.AndroidIdentifiersConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    /**
     * Provenance marker for {@code AppSetIdInfo} from this App Set ID path only.
     */
    private static final class ConfiguredAppSetIdInfo {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidIdentifiersConfig config;

        private ConfiguredAppSetIdInfo(BaseVM owner,
                                       TraceEnvironmentConfig.AndroidIdentifiersConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    private static final class AndroidAppSetIdObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidAppSetIdObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidAppSetIdObjectResult notHandled() {
            return new AndroidAppSetIdObjectResult(false, null);
        }

        static AndroidAppSetIdObjectResult of(DvmObject<?> value) {
            return new AndroidAppSetIdObjectResult(true, value);
        }
    }

    private static final class AndroidAppSetIdIntResult {
        final boolean handled;
        final int value;

        private AndroidAppSetIdIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidAppSetIdIntResult notHandled() {
            return new AndroidAppSetIdIntResult(false, 0);
        }

        static AndroidAppSetIdIntResult of(int value) {
            return new AndroidAppSetIdIntResult(true, value);
        }
    }

    private static boolean isConfiguredAppSetIdClient(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredAppSetIdClient)) {
            return false;
        }
        ConfiguredAppSetIdClient marker = (ConfiguredAppSetIdClient) dvmObject.getValue();
        return marker.owner == vm;
    }

    private static boolean isConfiguredAppSetIdTask(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredAppSetIdTask)) {
            return false;
        }
        ConfiguredAppSetIdTask marker = (ConfiguredAppSetIdTask) dvmObject.getValue();
        return marker.owner == vm;
    }

    private static boolean isConfiguredAppSetIdInfo(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredAppSetIdInfo)) {
            return false;
        }
        ConfiguredAppSetIdInfo marker = (ConfiguredAppSetIdInfo) dvmObject.getValue();
        return marker.owner == vm;
    }

    private static TraceEnvironmentConfig.AndroidIdentifiersConfig configuredAppSetIdIdentifiers(BaseVM vm) {
        if (vm == null) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidIdentifiersConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.AndroidIdentifiersConfig identifiers = config.getAndroidIdentifiersConfig();
        if (identifiers == null || !identifiers.isAppSetIdConfigured()) {
            return null;
        }
        return identifiers;
    }

    private static final String APP_SET_GET_CLIENT_SIGNATURE =
            "com/google/android/gms/appset/AppSet->"
                    + "getClient(Landroid/content/Context;)"
                    + "Lcom/google/android/gms/appset/AppSetIdClient;";
    private static final String APP_SET_ID_CLIENT_GET_INFO_SIGNATURE =
            "com/google/android/gms/appset/AppSetIdClient->"
                    + "getAppSetIdInfo()Lcom/google/android/gms/tasks/Task;";
    private static final String APP_SET_TASK_GET_RESULT_SIGNATURE =
            "com/google/android/gms/tasks/Task->getResult()Ljava/lang/Object;";
    private static final String APP_SET_ID_INFO_GET_ID_SIGNATURE =
            "com/google/android/gms/appset/AppSetIdInfo->getId()Ljava/lang/String;";
    private static final String APP_SET_ID_INFO_GET_SCOPE_SIGNATURE =
            "com/google/android/gms/appset/AppSetIdInfo->getScope()I";
    private static final String APP_SET_ID_CLIENT_CLASS =
            "com/google/android/gms/appset/AppSetIdClient";
    private static final String APP_SET_TASK_CLASS = "com/google/android/gms/tasks/Task";
    private static final String APP_SET_ID_INFO_CLASS =
            "com/google/android/gms/appset/AppSetIdInfo";

    /**
     * When {@code android.identifiers.appSetId} is explicitly configured, returns a private
     * same-VM {@code AppSetIdClient} marker. Missing/unconfigured appSetId, wrong signature,
     * or missing identifiers node is notHandled (existing UOE, no event, no host read).
     * {@code AppSet.getClient} itself does not emit a sidecar event.
     */
    private static AndroidAppSetIdObjectResult tryAndroidAppSetIdStaticObject(BaseVM vm, String signature) {
        if (!APP_SET_GET_CLIENT_SIGNATURE.equals(signature)) {
            return AndroidAppSetIdObjectResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidIdentifiersConfig identifiers = configuredAppSetIdIdentifiers(vm);
        if (identifiers == null) {
            return AndroidAppSetIdObjectResult.notHandled();
        }
        DvmObject<?> client = vm.resolveClass(APP_SET_ID_CLIENT_CLASS)
                .newObject(new ConfiguredAppSetIdClient(vm, identifiers));
        return AndroidAppSetIdObjectResult.of(client);
    }

    /**
     * Instance object methods for this App Set ID path only: {@code getAppSetIdInfo()},
     * completed {@code Task.getResult()} (no Class overload), and {@code AppSetIdInfo.getId()}.
     * Wrong signature / foreign / cross-VM marker is notHandled (existing UOE, no event).
     */
    private static AndroidAppSetIdObjectResult tryAndroidAppSetIdObjectMethod(BaseVM vm,
                                                                              DvmObject<?> dvmObject,
                                                                              String signature) {
        if (APP_SET_ID_CLIENT_GET_INFO_SIGNATURE.equals(signature)) {
            if (!isConfiguredAppSetIdClient(vm, dvmObject)) {
                return AndroidAppSetIdObjectResult.notHandled();
            }
            ConfiguredAppSetIdClient marker = (ConfiguredAppSetIdClient) dvmObject.getValue();
            DvmObject<?> task = vm.resolveClass(APP_SET_TASK_CLASS)
                    .newObject(new ConfiguredAppSetIdTask(vm, marker.config));
            String id = marker.config.getAppSetId();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_identifier",
                    "AppSetIdClient.getAppSetIdInfo",
                    "appSetIdLength=" + id.length() + ",scope=" + marker.config.getAppSetScope(),
                    "json-config", "读取配置的应用集合标识符");
            return AndroidAppSetIdObjectResult.of(task);
        }
        if (APP_SET_TASK_GET_RESULT_SIGNATURE.equals(signature)) {
            if (!isConfiguredAppSetIdTask(vm, dvmObject)) {
                return AndroidAppSetIdObjectResult.notHandled();
            }
            ConfiguredAppSetIdTask marker = (ConfiguredAppSetIdTask) dvmObject.getValue();
            DvmObject<?> info = vm.resolveClass(APP_SET_ID_INFO_CLASS)
                    .newObject(new ConfiguredAppSetIdInfo(vm, marker.config));
            String id = marker.config.getAppSetId();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_identifier",
                    "Task.getResult",
                    "resultLength=" + id.length(),
                    "json-config", "读取配置的应用集合标识符");
            return AndroidAppSetIdObjectResult.of(info);
        }
        if (APP_SET_ID_INFO_GET_ID_SIGNATURE.equals(signature)) {
            if (!isConfiguredAppSetIdInfo(vm, dvmObject)) {
                return AndroidAppSetIdObjectResult.notHandled();
            }
            ConfiguredAppSetIdInfo marker = (ConfiguredAppSetIdInfo) dvmObject.getValue();
            String id = marker.config.getAppSetId();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_identifier",
                    "AppSetIdInfo.getId",
                    "appSetIdLength=" + id.length(),
                    "json-config", "读取配置的应用集合标识符");
            return AndroidAppSetIdObjectResult.of(new StringObject(vm, id));
        }
        return AndroidAppSetIdObjectResult.notHandled();
    }

    /**
     * {@code AppSetIdInfo.getScope()I} on a same-VM info marker only.
     * Wrong signature / foreign / cross-VM marker is notHandled (existing UOE, no event).
     */
    private static AndroidAppSetIdIntResult tryAndroidAppSetIdIntMethod(BaseVM vm,
                                                                        DvmObject<?> dvmObject,
                                                                        String signature) {
        if (!APP_SET_ID_INFO_GET_SCOPE_SIGNATURE.equals(signature)) {
            return AndroidAppSetIdIntResult.notHandled();
        }
        if (!isConfiguredAppSetIdInfo(vm, dvmObject)) {
            return AndroidAppSetIdIntResult.notHandled();
        }
        ConfiguredAppSetIdInfo marker = (ConfiguredAppSetIdInfo) dvmObject.getValue();
        int scope = marker.config.getAppSetScope();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_identifier",
                "AppSetIdInfo.getScope",
                "scope=" + scope,
                "json-config", "读取配置的应用集合标识符范围");
        return AndroidAppSetIdIntResult.of(scope);
    }

    /**
     * Provenance marker for {@code java.util.Locale} from configured {@code android.locale}.
     * Not a host {@link Locale}; instance methods must use the configured path only.
     */
    private static final class ConfiguredLocale {
        final TraceEnvironmentConfig.AndroidLocaleConfig config;

        private ConfiguredLocale(TraceEnvironmentConfig.AndroidLocaleConfig config) {
            this.config = config;
        }
    }

    /**
     * Provenance marker for {@code java.util.TimeZone} from configured {@code android.locale}.
     * Not a host {@link java.util.TimeZone}; instance methods must use the configured path only.
     */
    private static final class ConfiguredTimeZone {
        final TraceEnvironmentConfig.AndroidLocaleConfig config;

        private ConfiguredTimeZone(TraceEnvironmentConfig.AndroidLocaleConfig config) {
            this.config = config;
        }
    }

    private static final class AndroidLocaleObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidLocaleObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidLocaleObjectResult notHandled() {
            return new AndroidLocaleObjectResult(false, null);
        }

        static AndroidLocaleObjectResult of(DvmObject<?> value) {
            return new AndroidLocaleObjectResult(true, value);
        }
    }

    private static final class AndroidLocaleIntResult {
        final boolean handled;
        final int value;

        private AndroidLocaleIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidLocaleIntResult notHandled() {
            return new AndroidLocaleIntResult(false, 0);
        }

        static AndroidLocaleIntResult of(int value) {
            return new AndroidLocaleIntResult(true, value);
        }
    }

    /**
     * True only for a {@code ConfiguredLocale} marker whose {@code objectType} belongs to
     * {@code vm}. Cross-VM markers, ordinary/foreign objects, and nulls are rejected.
     */
    private static boolean isConfiguredLocale(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null || !(dvmObject.getValue() instanceof ConfiguredLocale)) {
            return false;
        }
        DvmClass objectType = dvmObject.getObjectType();
        return objectType != null && objectType.vm == vm;
    }

    /**
     * True only for a {@code ConfiguredTimeZone} marker whose {@code objectType} belongs to
     * {@code vm}. Cross-VM markers, ordinary/foreign objects, and nulls are rejected.
     */
    private static boolean isConfiguredTimeZone(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null || !(dvmObject.getValue() instanceof ConfiguredTimeZone)) {
            return false;
        }
        DvmClass objectType = dvmObject.getObjectType();
        return objectType != null && objectType.vm == vm;
    }

    /**
     * When {@code android.locale} is configured, returns marker objects for
     * {@code Locale.getDefault} / {@code TimeZone.getDefault}. When the node is absent, notHandled
     * so existing host fallback (VaList Locale only) remains.
     */
    private static AndroidLocaleObjectResult tryAndroidLocaleStaticObject(BaseVM vm, DvmClass dvmClass,
                                                                          String signature) {
        if (!"java/util/Locale->getDefault()Ljava/util/Locale;".equals(signature)
                && !"java/util/TimeZone->getDefault()Ljava/util/TimeZone;".equals(signature)) {
            return AndroidLocaleObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidLocaleConfigured()) {
            return AndroidLocaleObjectResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidLocaleConfig localeConfig = config.getAndroidLocaleConfig();
        if ("java/util/Locale->getDefault()Ljava/util/Locale;".equals(signature)) {
            DvmObject<?> obj = dvmClass.newObject(new ConfiguredLocale(localeConfig));
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_locale", "Locale.getDefault",
                    "languageTag=" + localeConfig.getLanguageTag()
                            + ",timezoneId=" + localeConfig.getTimezoneId(),
                    "json-config", "读取配置的默认 Locale");
            return AndroidLocaleObjectResult.of(obj);
        }
        DvmObject<?> obj = dvmClass.newObject(new ConfiguredTimeZone(localeConfig));
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_locale", "TimeZone.getDefault",
                "languageTag=" + localeConfig.getLanguageTag()
                        + ",timezoneId=" + localeConfig.getTimezoneId(),
                "json-config", "读取配置的默认 TimeZone");
        return AndroidLocaleObjectResult.of(obj);
    }

    /**
     * Instance methods for configured Locale / TimeZone markers only.
     * Unsupported signatures on markers throw rather than falling through to raw Locale casts.
     * Cross-VM {@code ConfiguredLocale} is refused without reading the other VM's config
     * (VaList still has host {@code Locale} getLanguage/getCountry casts).
     */
    private static AndroidLocaleObjectResult tryAndroidLocaleObjectMethod(BaseVM vm, DvmObject<?> dvmObject,
                                                                          String signature) {
        if (isConfiguredLocale(vm, dvmObject)) {
            ConfiguredLocale marker = (ConfiguredLocale) dvmObject.getValue();
            TraceEnvironmentConfig.AndroidLocaleConfig localeConfig = marker.config;
            Locale locale = localeConfig.getLocale();
            final String result;
            final String api;
            if ("java/util/Locale->getLanguage()Ljava/lang/String;".equals(signature)) {
                result = locale.getLanguage();
                api = "Locale.getLanguage";
            } else if ("java/util/Locale->getCountry()Ljava/lang/String;".equals(signature)) {
                result = locale.getCountry();
                api = "Locale.getCountry";
            } else if ("java/util/Locale->getScript()Ljava/lang/String;".equals(signature)) {
                result = locale.getScript();
                api = "Locale.getScript";
            } else if ("java/util/Locale->getVariant()Ljava/lang/String;".equals(signature)) {
                result = locale.getVariant();
                api = "Locale.getVariant";
            } else if ("java/util/Locale->toLanguageTag()Ljava/lang/String;".equals(signature)) {
                result = locale.toLanguageTag();
                api = "Locale.toLanguageTag";
            } else if ("java/util/Locale->toString()Ljava/lang/String;".equals(signature)) {
                result = locale.toString();
                api = "Locale.toString";
            } else if ("java/util/Locale->getISO3Language()Ljava/lang/String;".equals(signature)) {
                result = locale.getISO3Language();
                api = "Locale.getISO3Language";
            } else if ("java/util/Locale->getISO3Country()Ljava/lang/String;".equals(signature)) {
                result = locale.getISO3Country();
                api = "Locale.getISO3Country";
            } else {
                throw new UnsupportedOperationException(signature);
            }
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_locale", api,
                    "languageTag=" + localeConfig.getLanguageTag() + ",result=" + result,
                    "json-config", "读取配置的 Locale 属性 " + api);
            return AndroidLocaleObjectResult.of(new StringObject(vm, result));
        }
        if (dvmObject != null && dvmObject.getValue() instanceof ConfiguredLocale) {
            throw new UnsupportedOperationException(signature);
        }
        if (isConfiguredTimeZone(vm, dvmObject)) {
            ConfiguredTimeZone marker = (ConfiguredTimeZone) dvmObject.getValue();
            TraceEnvironmentConfig.AndroidLocaleConfig localeConfig = marker.config;
            if ("java/util/TimeZone->getID()Ljava/lang/String;".equals(signature)) {
                String result = localeConfig.getTimezoneId();
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_locale", "TimeZone.getID",
                        "timezoneId=" + result + ",result=" + result,
                        "json-config", "读取配置的 TimeZone ID");
                return AndroidLocaleObjectResult.of(new StringObject(vm, result));
            }
            throw new UnsupportedOperationException(signature);
        }
        return AndroidLocaleObjectResult.notHandled();
    }

    /**
     * Instance int methods for configured TimeZone markers only.
     * Exact {@code getRawOffset()I}: raw offset of canonical timezoneId, not DST / current-time offset.
     * Exact {@code getOffset(J)I}: offset at the given UTC epoch millis using timezoneId rules (includes DST);
     * does not read {@link TimeZone#getDefault()} or current system time.
     * ConfiguredLocale, ordinary/foreign/cross-VM TimeZone, missing android.locale, or other signatures: notHandled.
     */
    private static AndroidLocaleIntResult tryAndroidLocaleIntMethod(BaseVM vm, DvmObject<?> dvmObject,
                                                                    String signature, VarArg varArg) {
        if ("java/util/TimeZone->getRawOffset()I".equals(signature)) {
            if (!isConfiguredTimeZone(vm, dvmObject)) {
                return AndroidLocaleIntResult.notHandled();
            }
            ConfiguredTimeZone marker = (ConfiguredTimeZone) dvmObject.getValue();
            TraceEnvironmentConfig.AndroidLocaleConfig localeConfig = marker.config;
            String timezoneId = localeConfig.getTimezoneId();
            int rawOffset = localeConfig.getRawOffsetMillis();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_locale", "TimeZone.getRawOffset",
                    "timezoneId=" + timezoneId + ",rawOffsetMillis=" + rawOffset,
                    "json-config", "读取配置的 TimeZone raw offset");
            return AndroidLocaleIntResult.of(rawOffset);
        }
        if (!"java/util/TimeZone->getOffset(J)I".equals(signature)) {
            return AndroidLocaleIntResult.notHandled();
        }
        if (!isConfiguredTimeZone(vm, dvmObject) || varArg == null) {
            return AndroidLocaleIntResult.notHandled();
        }
        ConfiguredTimeZone marker = (ConfiguredTimeZone) dvmObject.getValue();
        TraceEnvironmentConfig.AndroidLocaleConfig localeConfig = marker.config;
        String timezoneId = localeConfig.getTimezoneId();
        long epochMillis = varArg.getLongArg(0);
        int offsetMillis = TimeZone.getTimeZone(timezoneId).getOffset(epochMillis);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_locale", "TimeZone.getOffset",
                "timezoneId=" + timezoneId + ",epochMillis=" + epochMillis + ",offsetMillis=" + offsetMillis,
                "json-config", "按配置时区读取指定时刻偏移");
        return AndroidLocaleIntResult.of(offsetMillis);
    }

    /**
     * Provenance marker for {@code android.util.DisplayMetrics} from configured {@code android.display}.
     * Binds the creating {@link BaseVM} (owner identity). Field access must use the configured
     * path only and requires {@code owner ==} current VM.
     * Not a host DisplayMetrics object.
     */
    private static final class ConfiguredDisplayMetrics {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidDisplayConfig config;

        private ConfiguredDisplayMetrics(BaseVM owner,
                                         TraceEnvironmentConfig.AndroidDisplayConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    /**
     * Provenance marker for {@code android.view.Display} from configured {@code android.display}.
     * Binds the creating {@link BaseVM} (owner identity). Display getters/void methods
     * require {@code owner ==} current VM.
     */
    private static final class ConfiguredDisplay {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidDisplayConfig config;

        private ConfiguredDisplay(BaseVM owner,
                                  TraceEnvironmentConfig.AndroidDisplayConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    /**
     * Provenance marker for {@code android.view.Display$Mode} from configured {@code android.display}.
     * Binds the creating {@link BaseVM} (owner identity). Mode int/float getters
     * require {@code owner ==} current VM.
     */
    private static final class ConfiguredDisplayMode {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidDisplayConfig config;

        private ConfiguredDisplayMode(BaseVM owner,
                                      TraceEnvironmentConfig.AndroidDisplayConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    private static final class AndroidDisplayObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidDisplayObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidDisplayObjectResult notHandled() {
            return new AndroidDisplayObjectResult(false, null);
        }

        static AndroidDisplayObjectResult of(DvmObject<?> value) {
            return new AndroidDisplayObjectResult(true, value);
        }
    }

    private static final class AndroidDisplayIntFieldResult {
        final boolean handled;
        final int value;

        private AndroidDisplayIntFieldResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidDisplayIntFieldResult notHandled() {
            return new AndroidDisplayIntFieldResult(false, 0);
        }

        static AndroidDisplayIntFieldResult of(int value) {
            return new AndroidDisplayIntFieldResult(true, value);
        }
    }

    private static final class AndroidDisplayFloatFieldResult {
        final boolean handled;
        final float value;

        private AndroidDisplayFloatFieldResult(boolean handled, float value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidDisplayFloatFieldResult notHandled() {
            return new AndroidDisplayFloatFieldResult(false, 0f);
        }

        static AndroidDisplayFloatFieldResult of(float value) {
            return new AndroidDisplayFloatFieldResult(true, value);
        }
    }

    private static final class AndroidDisplayVoidResult {
        final boolean handled;

        private AndroidDisplayVoidResult(boolean handled) {
            this.handled = handled;
        }

        static AndroidDisplayVoidResult notHandled() {
            return new AndroidDisplayVoidResult(false);
        }

        static AndroidDisplayVoidResult handled() {
            return new AndroidDisplayVoidResult(true);
        }
    }

    /**
     * True only when {@code dvmObject} holds a {@link ConfiguredDisplayMetrics} created by this
     * {@code vm} (reference identity on {@code owner}, not class name). Cross-VM markers,
     * ordinary/foreign objects, and nulls are rejected.
     */
    private static boolean isConfiguredDisplayMetrics(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredDisplayMetrics)) {
            return false;
        }
        ConfiguredDisplayMetrics marker = (ConfiguredDisplayMetrics) dvmObject.getValue();
        return marker.owner == vm;
    }

    /**
     * True only when {@code dvmObject} holds a {@link ConfiguredDisplay} created by this
     * {@code vm} (reference identity on {@code owner}, not class name). Cross-VM markers,
     * ordinary/foreign objects, and nulls are rejected.
     */
    private static boolean isConfiguredDisplay(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredDisplay)) {
            return false;
        }
        ConfiguredDisplay marker = (ConfiguredDisplay) dvmObject.getValue();
        return marker.owner == vm;
    }

    /**
     * True only when {@code dvmObject} holds a {@link ConfiguredDisplayMode} created by this
     * {@code vm} (reference identity on {@code owner}, not class name). Cross-VM markers,
     * ordinary/foreign objects, and nulls are rejected.
     */
    private static boolean isConfiguredDisplayMode(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredDisplayMode)) {
            return false;
        }
        ConfiguredDisplayMode marker = (ConfiguredDisplayMode) dvmObject.getValue();
        return marker.owner == vm;
    }

    private static final String DISPLAY_GET_METRICS_SIGNATURE =
            "android/view/Display->getMetrics(Landroid/util/DisplayMetrics;)V";
    private static final String DISPLAY_GET_REAL_METRICS_SIGNATURE =
            "android/view/Display->getRealMetrics(Landroid/util/DisplayMetrics;)V";

    /**
     * Void instance methods for ConfiguredDisplay only:
     * {@code getMetrics}/{@code getRealMetrics}. v1 uses the same configured profile for both
     * (no inset/real-size split). Writes {@link ConfiguredDisplayMetrics} onto the output
     * DisplayMetrics object. Non-marker / null / wrong type / cross-VM: notHandled (UOE, no event).
     */
    private static AndroidDisplayVoidResult tryAndroidDisplayVoidMethod(BaseVM vm, DvmObject<?> dvmObject,
                                                                        String signature, VarArg args) {
        final boolean getMetrics = DISPLAY_GET_METRICS_SIGNATURE.equals(signature);
        final boolean getRealMetrics = DISPLAY_GET_REAL_METRICS_SIGNATURE.equals(signature);
        if (!getMetrics && !getRealMetrics) {
            return AndroidDisplayVoidResult.notHandled();
        }
        if (!isConfiguredDisplay(vm, dvmObject)) {
            return AndroidDisplayVoidResult.notHandled();
        }
        DvmObject<?> out = args.getObjectArg(0);
        if (out == null || out.getObjectType() == null
                || !"android/util/DisplayMetrics".equals(out.getObjectType().getClassName())) {
            return AndroidDisplayVoidResult.notHandled();
        }
        ConfiguredDisplay marker = (ConfiguredDisplay) dvmObject.getValue();
        TraceEnvironmentConfig.AndroidDisplayConfig displayConfig = marker.config;
        out.setValue(new ConfiguredDisplayMetrics(vm, displayConfig));
        String api = getMetrics ? "Display.getMetrics" : "Display.getRealMetrics";
        String note = getMetrics
                ? "将配置的 DisplayMetrics 写入 getMetrics 输出（与 getRealMetrics 同 profile）"
                : "将配置的 DisplayMetrics 写入 getRealMetrics 输出（与 getMetrics 同 profile，不含 insets）";
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display", api,
                "width=" + displayConfig.getWidthPixels()
                        + ",height=" + displayConfig.getHeightPixels()
                        + ",densityDpi=" + displayConfig.getDensityDpi(),
                "json-config", note);
        return AndroidDisplayVoidResult.handled();
    }

    private static final String DISPLAY_MANAGER_GET_DISPLAY_SIGNATURE =
            "android/hardware/display/DisplayManager->getDisplay(I)Landroid/view/Display;";
    private static final String DISPLAY_MANAGER_GET_DISPLAYS_SIGNATURE =
            "android/hardware/display/DisplayManager->getDisplays()[Landroid/view/Display;";

    /**
     * Object methods for configured {@code android.display}:
     * {@code WindowManager.getDefaultDisplay}, {@code DisplayManager.getDisplay},
     * {@code DisplayManager.getDisplays}, {@code Display.getMode}, and
     * {@code Resources.getDisplayMetrics}.
     * Marker receivers with unsupported signatures throw.
     * Cross-VM ConfiguredDisplay stays notHandled (no mode marker, no sidecar).
     */
    private static AndroidDisplayObjectResult tryAndroidDisplayObjectMethod(BaseVM vm, DvmObject<?> dvmObject,
                                                                            String signature, VarArg args) {
        if (isConfiguredDisplay(vm, dvmObject)) {
            if ("android/view/Display->getMode()Landroid/view/Display$Mode;".equals(signature)) {
                ConfiguredDisplay marker = (ConfiguredDisplay) dvmObject.getValue();
                TraceEnvironmentConfig.AndroidDisplayConfig displayConfig = marker.config;
                DvmObject<?> mode = vm.resolveClass("android/view/Display$Mode")
                        .newObject(new ConfiguredDisplayMode(vm, displayConfig));
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display", "Display.getMode",
                        "modeId=" + displayConfig.getModeId()
                                + ",width=" + displayConfig.getWidthPixels()
                                + ",height=" + displayConfig.getHeightPixels()
                                + ",refreshRate=" + displayConfig.getRefreshRate(),
                        "json-config", "读取配置的 Display.Mode");
                return AndroidDisplayObjectResult.of(mode);
            }
            throw new UnsupportedOperationException(signature);
        }
        if (isConfiguredDisplayMode(vm, dvmObject)) {
            throw new UnsupportedOperationException(signature);
        }
        if ("android/view/WindowManager->getDefaultDisplay()Landroid/view/Display;".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidDisplayConfigured()) {
                return AndroidDisplayObjectResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidDisplayConfig displayConfig = config.getAndroidDisplayConfig();
            DvmObject<?> display = vm.resolveClass("android/view/Display")
                    .newObject(new ConfiguredDisplay(vm, displayConfig));
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display", "WindowManager.getDefaultDisplay",
                    "width=" + displayConfig.getWidthPixels()
                            + ",height=" + displayConfig.getHeightPixels()
                            + ",modeId=" + displayConfig.getModeId()
                            + ",refreshRate=" + displayConfig.getRefreshRate()
                            + ",rotation=" + displayConfig.getRotation(),
                    "json-config", "读取配置的默认 Display");
            return AndroidDisplayObjectResult.of(display);
        }
        if (DISPLAY_MANAGER_GET_DISPLAY_SIGNATURE.equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidDisplayConfigured()) {
                return AndroidDisplayObjectResult.notHandled();
            }
            int displayId = args.getIntArg(0);
            if (displayId != 0) {
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display",
                        "DisplayManager.getDisplay",
                        "displayId=" + displayId + ",result=null",
                        "json-config", "非默认 displayId 返回 null（不虚构多显示器）");
                return AndroidDisplayObjectResult.of(null);
            }
            TraceEnvironmentConfig.AndroidDisplayConfig displayConfig = config.getAndroidDisplayConfig();
            DvmObject<?> display = vm.resolveClass("android/view/Display")
                    .newObject(new ConfiguredDisplay(vm, displayConfig));
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display",
                    "DisplayManager.getDisplay",
                    "displayId=0,result=0",
                    "json-config", "读取配置的默认 Display（DisplayManager.getDisplay）");
            return AndroidDisplayObjectResult.of(display);
        }
        if (DISPLAY_MANAGER_GET_DISPLAYS_SIGNATURE.equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidDisplayConfigured()) {
                return AndroidDisplayObjectResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidDisplayConfig displayConfig = config.getAndroidDisplayConfig();
            DvmObject<?> display = vm.resolveClass("android/view/Display")
                    .newObject(new ConfiguredDisplay(vm, displayConfig));
            ArrayObject array = new ArrayObject(display);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display",
                    "DisplayManager.getDisplays",
                    "count=1",
                    "json-config", "枚举配置的默认 Display（仅单个 profile）");
            return AndroidDisplayObjectResult.of(array);
        }
        if ("android/content/res/Resources->getDisplayMetrics()Landroid/util/DisplayMetrics;"
                .equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isAndroidDisplayConfigured()) {
                return AndroidDisplayObjectResult.notHandled();
            }
            TraceEnvironmentConfig.AndroidDisplayConfig displayConfig = config.getAndroidDisplayConfig();
            DvmObject<?> metrics = vm.resolveClass("android/util/DisplayMetrics")
                    .newObject(new ConfiguredDisplayMetrics(vm, displayConfig));
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display", "Resources.getDisplayMetrics",
                    "width=" + displayConfig.getWidthPixels()
                            + ",height=" + displayConfig.getHeightPixels()
                            + ",densityDpi=" + displayConfig.getDensityDpi()
                            + ",density=" + displayConfig.getDensity(),
                    "json-config", "读取配置的 DisplayMetrics");
            return AndroidDisplayObjectResult.of(metrics);
        }
        return AndroidDisplayObjectResult.notHandled();
    }

    /**
     * Instance int methods for ConfiguredDisplay / ConfiguredDisplayMode only.
     * Cross-VM ConfiguredDisplay stays notHandled (existing UOE, no sidecar).
     * Cross-VM ConfiguredDisplayMode stays notHandled (existing UOE, no sidecar).
     */
    private static AndroidDisplayIntFieldResult tryAndroidDisplayIntMethod(BaseVM vm, DvmObject<?> dvmObject,
                                                                           String signature) {
        if (isConfiguredDisplay(vm, dvmObject)) {
            ConfiguredDisplay marker = (ConfiguredDisplay) dvmObject.getValue();
            TraceEnvironmentConfig.AndroidDisplayConfig displayConfig = marker.config;
            if ("android/view/Display->getDisplayId()I".equals(signature)) {
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display", "Display.getDisplayId",
                        "field=displayId,result=0",
                        "json-config", "读取配置 Display 的固定 displayId");
                return AndroidDisplayIntFieldResult.of(0);
            }
            if ("android/view/Display->getRotation()I".equals(signature)) {
                int result = displayConfig.getRotation();
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display", "Display.getRotation",
                        "field=rotation,result=" + result,
                        "json-config", "读取配置的 Display 旋转");
                return AndroidDisplayIntFieldResult.of(result);
            }
            if ("android/view/Display->getWidth()I".equals(signature)) {
                int result = displayConfig.getWidthPixels();
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display", "Display.getWidth",
                        "field=widthPixels,result=" + result,
                        "json-config", "读取配置的 Display 宽度");
                return AndroidDisplayIntFieldResult.of(result);
            }
            if ("android/view/Display->getHeight()I".equals(signature)) {
                int result = displayConfig.getHeightPixels();
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display", "Display.getHeight",
                        "field=heightPixels,result=" + result,
                        "json-config", "读取配置的 Display 高度");
                return AndroidDisplayIntFieldResult.of(result);
            }
            throw new UnsupportedOperationException(signature);
        }
        if (isConfiguredDisplayMode(vm, dvmObject)) {
            ConfiguredDisplayMode marker = (ConfiguredDisplayMode) dvmObject.getValue();
            TraceEnvironmentConfig.AndroidDisplayConfig displayConfig = marker.config;
            final int result;
            final String api;
            final String field;
            if ("android/view/Display$Mode->getModeId()I".equals(signature)) {
                result = displayConfig.getModeId();
                api = "Display.Mode.getModeId";
                field = "modeId";
            } else if ("android/view/Display$Mode->getPhysicalWidth()I".equals(signature)) {
                result = displayConfig.getWidthPixels();
                api = "Display.Mode.getPhysicalWidth";
                field = "physicalWidth";
            } else if ("android/view/Display$Mode->getPhysicalHeight()I".equals(signature)) {
                result = displayConfig.getHeightPixels();
                api = "Display.Mode.getPhysicalHeight";
                field = "physicalHeight";
            } else {
                throw new UnsupportedOperationException(signature);
            }
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display", api,
                    "field=" + field + ",result=" + result,
                    "json-config", "读取配置的 Display.Mode 整型 " + field);
            return AndroidDisplayIntFieldResult.of(result);
        }
        return AndroidDisplayIntFieldResult.notHandled();
    }

    /**
     * Instance float methods for ConfiguredDisplay / ConfiguredDisplayMode only
     * (wired through {@code callFloatMethodV}).
     * Cross-VM ConfiguredDisplay stays notHandled (existing UOE, no sidecar).
     * Cross-VM ConfiguredDisplayMode stays notHandled (existing UOE, no sidecar).
     */
    private static AndroidDisplayFloatFieldResult tryAndroidDisplayFloatMethod(BaseVM vm, DvmObject<?> dvmObject,
                                                                               String signature) {
        if (isConfiguredDisplay(vm, dvmObject)) {
            ConfiguredDisplay marker = (ConfiguredDisplay) dvmObject.getValue();
            TraceEnvironmentConfig.AndroidDisplayConfig displayConfig = marker.config;
            if ("android/view/Display->getRefreshRate()F".equals(signature)) {
                float result = displayConfig.getRefreshRate();
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display", "Display.getRefreshRate",
                        "field=refreshRate,result=" + result,
                        "json-config", "读取配置的 Display 刷新率");
                return AndroidDisplayFloatFieldResult.of(result);
            }
            throw new UnsupportedOperationException(signature);
        }
        if (isConfiguredDisplayMode(vm, dvmObject)) {
            ConfiguredDisplayMode marker = (ConfiguredDisplayMode) dvmObject.getValue();
            TraceEnvironmentConfig.AndroidDisplayConfig displayConfig = marker.config;
            if ("android/view/Display$Mode->getRefreshRate()F".equals(signature)) {
                float result = displayConfig.getRefreshRate();
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display", "Display.Mode.getRefreshRate",
                        "field=refreshRate,result=" + result,
                        "json-config", "读取配置的 Display.Mode 刷新率");
                return AndroidDisplayFloatFieldResult.of(result);
            }
            throw new UnsupportedOperationException(signature);
        }
        return AndroidDisplayFloatFieldResult.notHandled();
    }

    /**
     * Int fields for ConfiguredDisplayMetrics only: widthPixels, heightPixels, densityDpi.
     * Other field signatures on the marker throw rather than falling through.
     * Cross-VM markers stay notHandled (existing UOE, no sidecar).
     */
    private static AndroidDisplayIntFieldResult tryAndroidDisplayIntField(BaseVM vm, DvmObject<?> dvmObject,
                                                                          String signature) {
        if (!isConfiguredDisplayMetrics(vm, dvmObject)) {
            return AndroidDisplayIntFieldResult.notHandled();
        }
        ConfiguredDisplayMetrics marker = (ConfiguredDisplayMetrics) dvmObject.getValue();
        TraceEnvironmentConfig.AndroidDisplayConfig displayConfig = marker.config;
        final int result;
        final String field;
        if ("android/util/DisplayMetrics->widthPixels:I".equals(signature)) {
            result = displayConfig.getWidthPixels();
            field = "widthPixels";
        } else if ("android/util/DisplayMetrics->heightPixels:I".equals(signature)) {
            result = displayConfig.getHeightPixels();
            field = "heightPixels";
        } else if ("android/util/DisplayMetrics->densityDpi:I".equals(signature)) {
            result = displayConfig.getDensityDpi();
            field = "densityDpi";
        } else {
            throw new UnsupportedOperationException(signature);
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display", "DisplayMetrics." + field,
                "field=" + field + ",result=" + result,
                "json-config", "读取配置的 DisplayMetrics 整型字段 " + field);
        return AndroidDisplayIntFieldResult.of(result);
    }

    /**
     * Float fields for ConfiguredDisplayMetrics only: density, scaledDensity, xdpi, ydpi.
     * Other field signatures on the marker throw rather than falling through.
     * Cross-VM markers stay notHandled (existing UOE, no sidecar).
     */
    private static AndroidDisplayFloatFieldResult tryAndroidDisplayFloatField(BaseVM vm, DvmObject<?> dvmObject,
                                                                              String signature) {
        if (!isConfiguredDisplayMetrics(vm, dvmObject)) {
            return AndroidDisplayFloatFieldResult.notHandled();
        }
        ConfiguredDisplayMetrics marker = (ConfiguredDisplayMetrics) dvmObject.getValue();
        TraceEnvironmentConfig.AndroidDisplayConfig displayConfig = marker.config;
        final float result;
        final String field;
        if ("android/util/DisplayMetrics->density:F".equals(signature)) {
            result = displayConfig.getDensity();
            field = "density";
        } else if ("android/util/DisplayMetrics->scaledDensity:F".equals(signature)) {
            result = displayConfig.getScaledDensity();
            field = "scaledDensity";
        } else if ("android/util/DisplayMetrics->xdpi:F".equals(signature)) {
            result = displayConfig.getXdpi();
            field = "xdpi";
        } else if ("android/util/DisplayMetrics->ydpi:F".equals(signature)) {
            result = displayConfig.getYdpi();
            field = "ydpi";
        } else {
            throw new UnsupportedOperationException(signature);
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_display", "DisplayMetrics." + field,
                "field=" + field + ",result=" + result,
                "json-config", "读取配置的 DisplayMetrics 浮点字段 " + field);
        return AndroidDisplayFloatFieldResult.of(result);
    }

    /**
     * Provenance marker for {@code android.content.res.Configuration} from configured
     * {@code android.configuration}. Binds the creating {@link BaseVM} (owner identity).
     * Not a host Configuration object.
     */
    private static final class ConfiguredConfiguration {
        final BaseVM owner;
        final TraceEnvironmentConfig.AndroidConfigurationConfig config;

        private ConfiguredConfiguration(BaseVM owner,
                                        TraceEnvironmentConfig.AndroidConfigurationConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    private static final class AndroidConfigurationObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidConfigurationObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidConfigurationObjectResult notHandled() {
            return new AndroidConfigurationObjectResult(false, null);
        }

        static AndroidConfigurationObjectResult of(DvmObject<?> value) {
            return new AndroidConfigurationObjectResult(true, value);
        }
    }

    private static final class AndroidConfigurationIntFieldResult {
        final boolean handled;
        final int value;

        private AndroidConfigurationIntFieldResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidConfigurationIntFieldResult notHandled() {
            return new AndroidConfigurationIntFieldResult(false, 0);
        }

        static AndroidConfigurationIntFieldResult of(int value) {
            return new AndroidConfigurationIntFieldResult(true, value);
        }
    }

    private static final class AndroidConfigurationFloatFieldResult {
        final boolean handled;
        final float value;

        private AndroidConfigurationFloatFieldResult(boolean handled, float value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidConfigurationFloatFieldResult notHandled() {
            return new AndroidConfigurationFloatFieldResult(false, 0f);
        }

        static AndroidConfigurationFloatFieldResult of(float value) {
            return new AndroidConfigurationFloatFieldResult(true, value);
        }
    }

    /**
     * True only when {@code dvmObject} holds a {@link ConfiguredConfiguration} created by this
     * {@code vm} (reference identity on {@code owner}, not class name). Cross-VM markers,
     * ordinary/foreign objects, and nulls are rejected.
     */
    private static boolean isConfiguredConfiguration(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredConfiguration)) {
            return false;
        }
        ConfiguredConfiguration marker = (ConfiguredConfiguration) dvmObject.getValue();
        return marker.owner == vm;
    }

    /**
     * When {@code android.configuration} is configured, handles
     * {@code Resources.getConfiguration()} with a private provenance marker.
     */
    private static AndroidConfigurationObjectResult tryAndroidConfigurationObjectMethod(BaseVM vm,
                                                                                        String signature) {
        if (!"android/content/res/Resources->getConfiguration()Landroid/content/res/Configuration;"
                .equals(signature)) {
            return AndroidConfigurationObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidConfigurationConfigured()) {
            return AndroidConfigurationObjectResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidConfigurationConfig configurationConfig =
                config.getAndroidConfigurationConfig();
        DvmObject<?> configuration = vm.resolveClass("android/content/res/Configuration")
                .newObject(new ConfiguredConfiguration(vm, configurationConfig));
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_configuration",
                "Resources.getConfiguration",
                "orientation=" + configurationConfig.getOrientation()
                        + ",screenLayout=" + configurationConfig.getScreenLayout()
                        + ",uiMode=" + configurationConfig.getUiMode()
                        + ",fontScale=" + configurationConfig.getFontScale()
                        + ",densityDpi=" + configurationConfig.getDensityDpi()
                        + ",screenWidthDp=" + configurationConfig.getScreenWidthDp()
                        + ",screenHeightDp=" + configurationConfig.getScreenHeightDp()
                        + ",smallestScreenWidthDp="
                        + configurationConfig.getSmallestScreenWidthDp()
                        + ",keyboard=" + configurationConfig.getKeyboard()
                        + ",navigation=" + configurationConfig.getNavigation()
                        + ",keyboardHidden=" + configurationConfig.getKeyboardHidden()
                        + ",hardKeyboardHidden=" + configurationConfig.getHardKeyboardHidden()
                        + ",navigationHidden=" + configurationConfig.getNavigationHidden(),
                "json-config", "读取配置的 Configuration");
        return AndroidConfigurationObjectResult.of(configuration);
    }

    /**
     * Int fields for ConfiguredConfiguration only: orientation, screenLayout, uiMode, densityDpi,
     * screenWidthDp, screenHeightDp, smallestScreenWidthDp, keyboard, navigation,
     * keyboardHidden, hardKeyboardHidden, navigationHidden.
     * Cross-VM markers stay notHandled (existing UOE, no sidecar).
     */
    private static AndroidConfigurationIntFieldResult tryAndroidConfigurationIntField(
            BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (!isConfiguredConfiguration(vm, dvmObject)) {
            return AndroidConfigurationIntFieldResult.notHandled();
        }
        ConfiguredConfiguration marker = (ConfiguredConfiguration) dvmObject.getValue();
        TraceEnvironmentConfig.AndroidConfigurationConfig configurationConfig = marker.config;
        final int result;
        final String field;
        if ("android/content/res/Configuration->orientation:I".equals(signature)) {
            result = configurationConfig.getOrientation();
            field = "orientation";
        } else if ("android/content/res/Configuration->screenLayout:I".equals(signature)) {
            result = configurationConfig.getScreenLayout();
            field = "screenLayout";
        } else if ("android/content/res/Configuration->uiMode:I".equals(signature)) {
            result = configurationConfig.getUiMode();
            field = "uiMode";
        } else if ("android/content/res/Configuration->densityDpi:I".equals(signature)) {
            result = configurationConfig.getDensityDpi();
            field = "densityDpi";
        } else if ("android/content/res/Configuration->screenWidthDp:I".equals(signature)) {
            result = configurationConfig.getScreenWidthDp();
            field = "screenWidthDp";
        } else if ("android/content/res/Configuration->screenHeightDp:I".equals(signature)) {
            result = configurationConfig.getScreenHeightDp();
            field = "screenHeightDp";
        } else if ("android/content/res/Configuration->smallestScreenWidthDp:I"
                .equals(signature)) {
            result = configurationConfig.getSmallestScreenWidthDp();
            field = "smallestScreenWidthDp";
        } else if ("android/content/res/Configuration->keyboard:I".equals(signature)) {
            result = configurationConfig.getKeyboard();
            field = "keyboard";
        } else if ("android/content/res/Configuration->navigation:I".equals(signature)) {
            result = configurationConfig.getNavigation();
            field = "navigation";
        } else if ("android/content/res/Configuration->keyboardHidden:I".equals(signature)) {
            result = configurationConfig.getKeyboardHidden();
            field = "keyboardHidden";
        } else if ("android/content/res/Configuration->hardKeyboardHidden:I".equals(signature)) {
            result = configurationConfig.getHardKeyboardHidden();
            field = "hardKeyboardHidden";
        } else if ("android/content/res/Configuration->navigationHidden:I".equals(signature)) {
            result = configurationConfig.getNavigationHidden();
            field = "navigationHidden";
        } else {
            throw new UnsupportedOperationException(signature);
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_configuration",
                "Configuration." + field,
                "field=" + field + ",result=" + result,
                "json-config", "读取配置的 Configuration 整型字段 " + field);
        return AndroidConfigurationIntFieldResult.of(result);
    }

    /**
     * Float fields for ConfiguredConfiguration only: fontScale.
     * Cross-VM markers stay notHandled (existing UOE, no sidecar).
     */
    private static AndroidConfigurationFloatFieldResult tryAndroidConfigurationFloatField(
            BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (!isConfiguredConfiguration(vm, dvmObject)) {
            return AndroidConfigurationFloatFieldResult.notHandled();
        }
        ConfiguredConfiguration marker = (ConfiguredConfiguration) dvmObject.getValue();
        TraceEnvironmentConfig.AndroidConfigurationConfig configurationConfig = marker.config;
        if ("android/content/res/Configuration->fontScale:F".equals(signature)) {
            float result = configurationConfig.getFontScale();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_configuration",
                    "Configuration.fontScale",
                    "field=fontScale,result=" + result,
                    "json-config", "读取配置的 Configuration 浮点字段 fontScale");
            return AndroidConfigurationFloatFieldResult.of(result);
        }
        throw new UnsupportedOperationException(signature);
    }

    /**
     * {@code Configuration.locale} on a {@link ConfiguredConfiguration} only when
     * {@code android.locale} is also configured. Returns a new {@link ConfiguredLocale}
     * from the existing normalized locale config. Missing {@code android.locale},
     * plain/foreign/cross-VM Configuration, or any other signature stays notHandled
     * (existing UOE, no sidecar). Does not read host {@link Locale} or another VM's config.
     */
    private static AndroidConfigurationObjectResult tryAndroidConfigurationLocaleField(
            BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (!"android/content/res/Configuration->locale:Ljava/util/Locale;".equals(signature)) {
            return AndroidConfigurationObjectResult.notHandled();
        }
        if (!isConfiguredConfiguration(vm, dvmObject)) {
            return AndroidConfigurationObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidLocaleConfigured()) {
            return AndroidConfigurationObjectResult.notHandled();
        }
        TraceEnvironmentConfig.AndroidLocaleConfig localeConfig = config.getAndroidLocaleConfig();
        DvmObject<?> locale = vm.resolveClass("java/util/Locale")
                .newObject(new ConfiguredLocale(localeConfig));
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_configuration",
                "Configuration.locale",
                "languageTag=" + localeConfig.getLanguageTag(),
                "json-config", "读取配置的 Configuration.locale");
        return AndroidConfigurationObjectResult.of(locale);
    }

    /** True when receiver is {@code SystemService} for {@code Context.UI_MODE_SERVICE}. */
    private static boolean isSystemServiceUiModeManager(DvmObject<?> dvmObject) {
        return dvmObject instanceof SystemService
                && SystemService.UI_MODE_SERVICE.equals(dvmObject.getValue());
    }

    private static final class AndroidUiModeIntResult {
        final boolean handled;
        final int value;

        private AndroidUiModeIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidUiModeIntResult notHandled() {
            return new AndroidUiModeIntResult(false, 0);
        }

        static AndroidUiModeIntResult of(int value) {
            return new AndroidUiModeIntResult(true, value);
        }
    }

    /**
     * {@code UiModeManager.getCurrentModeType()I} on the SystemService uimode marker when
     * {@code android.configuration} is configured. Returns {@code uiMode & 0x0f}
     * ({@code Configuration.UI_MODE_TYPE_MASK}; night bits are excluded). Missing env /
     * missing configuration node is notHandled (UOE path, no event). Does not fabricate
     * a default type when unconfigured.
     */
    private static AndroidUiModeIntResult tryAndroidUiModeInt(BaseVM vm, DvmObject<?> dvmObject,
                                                              String signature) {
        if (!"android/app/UiModeManager->getCurrentModeType()I".equals(signature)) {
            return AndroidUiModeIntResult.notHandled();
        }
        if (!isSystemServiceUiModeManager(dvmObject)) {
            return AndroidUiModeIntResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidConfigurationConfigured()) {
            return AndroidUiModeIntResult.notHandled();
        }
        int type = config.getAndroidConfigurationConfig().getUiMode() & 0x0f;
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_configuration",
                "UiModeManager.getCurrentModeType",
                "field=uiMode,result=" + type,
                "json-config", "读取配置的 UI 模式类型位（不含夜间位）");
        return AndroidUiModeIntResult.of(type);
    }

    /**
     * Marker value for {@code KeyInfo} objects returned from configured {@code android.tee}
     * via {@code KeyFactory.getKeySpec(..., KeyInfo.class)}. Binds owning VM + config identity.
     * Provenance only — no real key ops.
     */
    private static final class ConfiguredTeeKeyInfo {
        final BaseVM owner;
        final TraceEnvironmentConfig config;

        private ConfiguredTeeKeyInfo(BaseVM owner, TraceEnvironmentConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    /**
     * Marker value for {@code java.security.Key} returned from {@code KeyStore.getKey}
     * when {@code android.tee.keyBlobHex} is configured. Stores owning VM + config identity
     * + alias; encoded bytes always come from config defensive copy (analysis marker, no crypto).
     */
    private static final class ConfiguredTeeKey {
        final BaseVM owner;
        final TraceEnvironmentConfig config;
        final String alias;

        private ConfiguredTeeKey(BaseVM owner, TraceEnvironmentConfig config, String alias) {
            this.owner = owner;
            this.config = config;
            this.alias = alias;
        }
    }

    private static final class AndroidPackageObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidPackageObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidPackageObjectResult notHandled() {
            return new AndroidPackageObjectResult(false, null);
        }

        static AndroidPackageObjectResult of(DvmObject<?> value) {
            return new AndroidPackageObjectResult(true, value);
        }
    }

    private static final class AndroidPackageObjectFieldResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidPackageObjectFieldResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidPackageObjectFieldResult notHandled() {
            return new AndroidPackageObjectFieldResult(false, null);
        }

        static AndroidPackageObjectFieldResult of(DvmObject<?> value) {
            return new AndroidPackageObjectFieldResult(true, value);
        }
    }

    private static final class AndroidPackageIntFieldResult {
        final boolean handled;
        final int value;

        private AndroidPackageIntFieldResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidPackageIntFieldResult notHandled() {
            return new AndroidPackageIntFieldResult(false, 0);
        }

        static AndroidPackageIntFieldResult of(int value) {
            return new AndroidPackageIntFieldResult(true, value);
        }
    }

    private static final class AndroidPackageBooleanFieldResult {
        final boolean handled;
        final boolean value;

        private AndroidPackageBooleanFieldResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidPackageBooleanFieldResult notHandled() {
            return new AndroidPackageBooleanFieldResult(false, false);
        }

        static AndroidPackageBooleanFieldResult of(boolean value) {
            return new AndroidPackageBooleanFieldResult(true, value);
        }
    }

    private static final class AndroidPackageLongFieldResult {
        final boolean handled;
        final long value;

        private AndroidPackageLongFieldResult(boolean handled, long value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidPackageLongFieldResult notHandled() {
            return new AndroidPackageLongFieldResult(false, 0L);
        }

        static AndroidPackageLongFieldResult of(long value) {
            return new AndroidPackageLongFieldResult(true, value);
        }
    }

    private static boolean isConfiguredPackageInfo(DvmObject<?> dvmObject) {
        return dvmObject != null && dvmObject.getValue() instanceof ConfiguredPackageInfo;
    }

    /**
     * Live same-VM {@link ConfiguredPackageInfo}: type match and {@code owner ==} current
     * {@link BaseVM}. Plain / foreign-VM markers return false (notHandled → UOE, no sidecar).
     */
    private static boolean isLiveConfiguredPackageInfo(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredPackageInfo)) {
            return false;
        }
        ConfiguredPackageInfo marker = (ConfiguredPackageInfo) dvmObject.getValue();
        return marker != null && marker.owner == vm;
    }

    /**
     * Live same-VM {@link ConfiguredApplicationInfo}: type match and {@code owner ==} current
     * {@link BaseVM}. Plain / foreign-VM markers return false (notHandled → UOE, no sidecar).
     */
    private static boolean isLiveConfiguredApplicationInfo(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredApplicationInfo)) {
            return false;
        }
        ConfiguredApplicationInfo marker = (ConfiguredApplicationInfo) dvmObject.getValue();
        return marker != null && marker.owner == vm;
    }

    /**
     * Same-VM {@link ConfiguredInstallSourceInfo}: type match and {@code owner ==} current
     * {@link BaseVM}. Plain / foreign-VM markers return false (notHandled → UOE, no sidecar).
     */
    private static boolean isConfiguredInstallSourceInfo(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredInstallSourceInfo)) {
            return false;
        }
        ConfiguredInstallSourceInfo marker = (ConfiguredInstallSourceInfo) dvmObject.getValue();
        return marker != null && marker.owner == vm;
    }

    private static boolean isConfiguredFeatureInfo(DvmObject<?> dvmObject) {
        return dvmObject != null && dvmObject.getValue() instanceof ConfiguredFeatureInfo;
    }

    /**
     * Live same-VM {@link ConfiguredFeatureInfo}: type match and {@code owner ==} current
     * {@link BaseVM}. Plain / foreign-VM markers return false (notHandled → UOE, no sidecar).
     */
    private static boolean isLiveConfiguredFeatureInfo(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredFeatureInfo)) {
            return false;
        }
        ConfiguredFeatureInfo marker = (ConfiguredFeatureInfo) dvmObject.getValue();
        return marker != null && marker.owner == vm;
    }

    private static boolean isConfiguredSigningInfo(DvmObject<?> dvmObject) {
        return dvmObject != null && dvmObject.getValue() instanceof ConfiguredSigningInfo;
    }

    /**
     * Live same-VM {@link ConfiguredSigningInfo}: type match and {@code owner ==} current
     * {@link BaseVM}. Plain / foreign-VM markers return false (notHandled → UOE, no sidecar).
     */
    private static boolean isLiveConfiguredSigningInfo(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredSigningInfo)) {
            return false;
        }
        ConfiguredSigningInfo marker = (ConfiguredSigningInfo) dvmObject.getValue();
        return marker != null && marker.owner == vm;
    }

    private static boolean isConfiguredTeeKeyInfo(DvmObject<?> dvmObject) {
        return dvmObject != null && dvmObject.getValue() instanceof ConfiguredTeeKeyInfo;
    }

    /**
     * Live same-VM {@link ConfiguredTeeKeyInfo}: owner matches, config instance still bound on the
     * emulator, and {@code android.tee} node remains present. Plain/foreign/stale → false.
     */
    private static boolean isLiveConfiguredTeeKeyInfo(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || !isConfiguredTeeKeyInfo(dvmObject)) {
            return false;
        }
        ConfiguredTeeKeyInfo marker = (ConfiguredTeeKeyInfo) dvmObject.getValue();
        if (marker == null || marker.owner != vm || marker.config == null) {
            return false;
        }
        TraceEnvironmentConfig current = TraceEnvironmentConfig.get(vm.getEmulator());
        return current != null && current == marker.config && current.isAndroidTeeConfigured();
    }

    private static boolean isConfiguredTeeKey(DvmObject<?> dvmObject) {
        return dvmObject != null && dvmObject.getValue() instanceof ConfiguredTeeKey;
    }

    /**
     * Live same-VM {@link ConfiguredTeeKey}: owner matches, config instance still bound on the
     * emulator, and {@code keyBlobHex} remains configured. Plain/foreign/stale → false.
     */
    private static boolean isLiveConfiguredTeeKey(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || !isConfiguredTeeKey(dvmObject)) {
            return false;
        }
        ConfiguredTeeKey marker = (ConfiguredTeeKey) dvmObject.getValue();
        if (marker == null || marker.owner != vm || marker.config == null) {
            return false;
        }
        TraceEnvironmentConfig current = TraceEnvironmentConfig.get(vm.getEmulator());
        return current != null && current == marker.config && current.isTeeKeyBlobConfigured();
    }

    /**
     * Decode a pre-validated lowercase even-length hex string from {@code signaturesHex}.
     */
    private static byte[] decodeConfiguredSignatureHex(String hex) {
        int n = hex.length() / 2;
        byte[] data = new byte[n];
        for (int i = 0; i < n; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            data[i] = (byte) ((high << 4) | low);
        }
        return data;
    }

    /** Build ordered {@link Signature} ArrayObject from configured lowercase hex strings. */
    private static ArrayObject signatureArrayFromHexes(BaseVM vm, List<String> hexes) {
        Signature[] signatures = new Signature[hexes.size()];
        for (int i = 0; i < hexes.size(); i++) {
            signatures[i] = new Signature(vm, decodeConfiguredSignatureHex(hexes.get(i)));
        }
        return new ArrayObject(signatures);
    }

    /**
     * Shared lookup for configured android.packages entries.
     * Returns null packageConfig only when the packages node is not configured (caller notHandled).
     * When the node is configured but the package is absent, throws UOE with package name.
     */
    private static TraceEnvironmentConfig.PackageConfig requireConfiguredAndroidPackage(BaseVM vm,
                                                                                        String signature,
                                                                                        String packageName) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidPackagesConfigured()) {
            return null;
        }
        TraceEnvironmentConfig.PackageConfig pkg;
        try {
            pkg = config.getAndroidPackage(packageName);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException(signature + " package=" + packageName, e);
        }
        if (pkg == null) {
            throw new UnsupportedOperationException(signature + " package=" + packageName);
        }
        return pkg;
    }

    /**
     * PackageManager.hasSystemFeature when {@code android.features} is configured.
     * Signature matched before args. Authoritative: no-version is true iff exact name exists;
     * version overload is true iff feature exists and (configured version if present else 0)
     * &gt;= requested version (negative requested compares normally). Explicit empty or absent
     * feature returns handled false. Missing features node is notHandled (preserve old UOE;
     * tee strongbox fallback may still apply).
     */
    private static AndroidPackageBooleanFieldResult tryAndroidFeatureHasSystemFeature(BaseVM vm, String signature,
                                                                                      VarArg args) {
        final boolean noVersion =
                "android/content/pm/PackageManager->hasSystemFeature(Ljava/lang/String;)Z".equals(signature);
        final boolean withVersion =
                "android/content/pm/PackageManager->hasSystemFeature(Ljava/lang/String;I)Z".equals(signature);
        if (!noVersion && !withVersion) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidFeaturesConfigured()) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        DvmObject<?> nameArg = args.getObjectArg(0);
        if (!(nameArg instanceof StringObject)) {
            throw new IllegalArgumentException("signature=" + signature
                    + ", arg0 must be StringObject, was=" + nameArg);
        }
        String name = ((StringObject) nameArg).getValue();
        TraceEnvironmentConfig.FeatureConfig feature = null;
        if (name != null && !name.trim().isEmpty()) {
            feature = config.getAndroidFeature(name);
        }
        final boolean result;
        final Integer requestedVersion;
        final String configuredLabel;
        if (noVersion) {
            requestedVersion = null;
            configuredLabel = feature != null ? "present" : "absent";
            result = feature != null;
        } else {
            int requested = args.getIntArg(1);
            requestedVersion = Integer.valueOf(requested);
            if (feature == null) {
                configuredLabel = "absent";
                result = false;
            } else {
                int configured = feature.isVersionConfigured() && feature.getVersion() != null
                        ? feature.getVersion().intValue() : 0;
                configuredLabel = String.valueOf(configured);
                result = configured >= requested;
            }
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_feature",
                noVersion ? "PackageManager.hasSystemFeature" : "PackageManager.hasSystemFeature(version)",
                "name=" + name
                        + ",requested=" + (requestedVersion == null ? "n/a" : requestedVersion)
                        + ",configured=" + configuredLabel
                        + ",result=" + result,
                "json-config", "校验配置的系统特性");
        return AndroidPackageBooleanFieldResult.of(result);
    }

    /**
     * Fallback for {@code android.hardware.strongbox_keystore} from {@code android.tee.strongBoxAvailable}
     * when {@code android.features} is <strong>not</strong> configured. Signature matched before args.
     * Version overload treats this feature as version 0: true iff available and requestedVersion &lt;= 0.
     * Other feature names notHandled. When features is configured, features path remains authoritative.
     */
    private static AndroidPackageBooleanFieldResult tryAndroidTeeStrongBoxHasSystemFeature(BaseVM vm,
                                                                                           String signature,
                                                                                           VarArg args) {
        final boolean noVersion =
                "android/content/pm/PackageManager->hasSystemFeature(Ljava/lang/String;)Z".equals(signature);
        final boolean withVersion =
                "android/content/pm/PackageManager->hasSystemFeature(Ljava/lang/String;I)Z".equals(signature);
        if (!noVersion && !withVersion) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        // Features node authoritative when present (including empty array).
        if (config == null || config.isAndroidFeaturesConfigured()) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        if (!config.isTeeStrongBoxAvailableConfigured()) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        DvmObject<?> nameArg = args.getObjectArg(0);
        if (!(nameArg instanceof StringObject)) {
            throw new IllegalArgumentException("signature=" + signature
                    + ", arg0 must be StringObject, was=" + nameArg);
        }
        String name = ((StringObject) nameArg).getValue();
        if (!"android.hardware.strongbox_keystore".equals(name)) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        boolean available = config.getTeeStrongBoxAvailable(false);
        final boolean result;
        final String requestedLabel;
        if (noVersion) {
            requestedLabel = "n/a";
            result = available;
        } else {
            int requested = args.getIntArg(1);
            requestedLabel = String.valueOf(requested);
            // Fallback feature version is 0: available && requested <= 0
            result = available && requested <= 0;
        }
        String markerSummary = config.isTeeMarkerConfigured() ? config.getTeeMarker() : "null";
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "tee",
                noVersion ? "PackageManager.hasSystemFeature" : "PackageManager.hasSystemFeature(version)",
                "feature=" + name
                        + ",requested=" + requestedLabel
                        + ",result=" + result
                        + ",marker=" + markerSummary,
                "json-config", "从 android.tee.strongBoxAvailable 回落 StrongBox 系统特性");
        return AndroidPackageBooleanFieldResult.of(result);
    }

    /**
     * Fallback for {@code android.hardware.hardware_keystore} from {@code android.tee.available}
     * and/or {@code keymasterVersion} when {@code android.features} is <strong>not</strong> configured.
     * Requires at least one of those fields. effectiveAvailable uses available when configured,
     * otherwise true (because keymasterVersion is present). Version overload:
     * effectiveAvailable &amp;&amp; configuredVersion &gt;= requested (missing keymasterVersion → 0).
     */
    private static AndroidPackageBooleanFieldResult tryAndroidTeeHardwareKeystoreHasSystemFeature(
            BaseVM vm, String signature, VarArg args) {
        final boolean noVersion =
                "android/content/pm/PackageManager->hasSystemFeature(Ljava/lang/String;)Z".equals(signature);
        final boolean withVersion =
                "android/content/pm/PackageManager->hasSystemFeature(Ljava/lang/String;I)Z".equals(signature);
        if (!noVersion && !withVersion) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || config.isAndroidFeaturesConfigured()) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        if (!config.isTeeAvailableConfigured() && !config.isTeeKeymasterVersionConfigured()) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        DvmObject<?> nameArg = args.getObjectArg(0);
        if (!(nameArg instanceof StringObject)) {
            throw new IllegalArgumentException("signature=" + signature
                    + ", arg0 must be StringObject, was=" + nameArg);
        }
        String name = ((StringObject) nameArg).getValue();
        if (!"android.hardware.hardware_keystore".equals(name)) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        // available configured → use it; else keymasterVersion alone implies available=true
        boolean effectiveAvailable = config.isTeeAvailableConfigured()
                ? config.getTeeAvailable(false)
                : true;
        int configuredVersion = config.isTeeKeymasterVersionConfigured()
                ? config.getTeeKeymasterVersion(0)
                : 0;
        final boolean result;
        final String requestedLabel;
        if (noVersion) {
            requestedLabel = "n/a";
            result = effectiveAvailable;
        } else {
            int requested = args.getIntArg(1);
            requestedLabel = String.valueOf(requested);
            result = effectiveAvailable && configuredVersion >= requested;
        }
        String markerSummary = config.isTeeMarkerConfigured() ? config.getTeeMarker() : "null";
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "tee",
                noVersion ? "PackageManager.hasSystemFeature" : "PackageManager.hasSystemFeature(version)",
                "feature=" + name
                        + ",requested=" + requestedLabel
                        + ",configuredVersion=" + configuredVersion
                        + ",result=" + result
                        + ",marker=" + markerSummary,
                "json-config", "从 android.tee.available/keymasterVersion 回落 hardware_keystore 特性");
        return AndroidPackageBooleanFieldResult.of(result);
    }

    /**
     * PackageManager.getSystemAvailableFeatures when {@code android.features} is configured.
     * Returns ArrayObject of FeatureInfo markers in config order; empty array when empty.
     * Missing features node is notHandled (preserve old UOE).
     */
    private static AndroidPackageObjectResult tryAndroidFeatureGetSystemAvailableFeatures(BaseVM vm,
                                                                                          String signature) {
        if (!"android/content/pm/PackageManager->getSystemAvailableFeatures()[Landroid/content/pm/FeatureInfo;"
                .equals(signature)) {
            return AndroidPackageObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidFeaturesConfigured()) {
            return AndroidPackageObjectResult.notHandled();
        }
        List<TraceEnvironmentConfig.FeatureConfig> features = config.getAndroidFeatures();
        DvmClass featureInfoClass = vm.resolveClass("android/content/pm/FeatureInfo");
        DvmObject<?>[] elements = new DvmObject<?>[features.size()];
        for (int i = 0; i < features.size(); i++) {
            elements[i] = featureInfoClass.newObject(new ConfiguredFeatureInfo(vm, features.get(i)));
        }
        ArrayObject array = new ArrayObject(elements);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_feature",
                "PackageManager.getSystemAvailableFeatures",
                "count=" + elements.length,
                "json-config", "返回配置的可用系统特性列表");
        return AndroidPackageObjectResult.of(array);
    }

    /**
     * FeatureInfo.name for same-VM configured marker objects only.
     * Plain / foreign-VM / unrelated FeatureInfo is notHandled (UOE / no json-config).
     */
    private static AndroidPackageObjectFieldResult tryAndroidFeatureObjectField(BaseVM vm, DvmObject<?> dvmObject,
                                                                                String signature) {
        if (!isLiveConfiguredFeatureInfo(vm, dvmObject)) {
            return AndroidPackageObjectFieldResult.notHandled();
        }
        if (!"android/content/pm/FeatureInfo->name:Ljava/lang/String;".equals(signature)) {
            return AndroidPackageObjectFieldResult.notHandled();
        }
        ConfiguredFeatureInfo marker = (ConfiguredFeatureInfo) dvmObject.getValue();
        String name = marker.featureConfig.getName();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_feature",
                "FeatureInfo.name",
                "name=" + name + ",value=" + name,
                "json-config", "读取配置的特性名称");
        return AndroidPackageObjectFieldResult.of(new StringObject(vm, name));
    }

    /**
     * FeatureInfo.version for same-VM configured marker objects only.
     * Returns configured version, or 0 when the version field was omitted in JSON.
     * Plain / foreign-VM / unrelated FeatureInfo is notHandled (UOE / no json-config).
     */
    private static AndroidPackageIntFieldResult tryAndroidFeatureIntField(BaseVM vm, DvmObject<?> dvmObject,
                                                                          String signature) {
        if (!isLiveConfiguredFeatureInfo(vm, dvmObject)) {
            return AndroidPackageIntFieldResult.notHandled();
        }
        if (!"android/content/pm/FeatureInfo->version:I".equals(signature)) {
            return AndroidPackageIntFieldResult.notHandled();
        }
        ConfiguredFeatureInfo marker = (ConfiguredFeatureInfo) dvmObject.getValue();
        TraceEnvironmentConfig.FeatureConfig feature = marker.featureConfig;
        int value = feature.isVersionConfigured() && feature.getVersion() != null
                ? feature.getVersion().intValue() : 0;
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_feature",
                "FeatureInfo.version",
                "name=" + feature.getName() + ",value=" + value,
                "json-config", "读取配置的特性版本");
        return AndroidPackageIntFieldResult.of(value);
    }

    /**
     * PackageManager.getPackagesForUid / getNameForUid when {@code android.packages} is configured.
     * Signature matched before arg0. Filters config-order packages with {@code isUidConfigured} and
     * matching uid. getPackagesForUid returns ArrayObject of names (or handled null when none);
     * getNameForUid returns first match (or handled null). Explicit empty packages yields null.
     * Node absent is notHandled so the old any-uid current-package fallback remains.
     */
    private static AndroidPackageObjectResult tryAndroidPackageUidMapping(BaseVM vm, String signature,
                                                                          VarArg args) {
        final boolean packagesForUid =
                "android/content/pm/PackageManager->getPackagesForUid(I)[Ljava/lang/String;".equals(signature);
        final boolean nameForUid =
                "android/content/pm/PackageManager->getNameForUid(I)Ljava/lang/String;".equals(signature);
        if (!packagesForUid && !nameForUid) {
            return AndroidPackageObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidPackagesConfigured()) {
            return AndroidPackageObjectResult.notHandled();
        }
        int uid = args.getIntArg(0);
        List<String> names = new ArrayList<String>();
        for (TraceEnvironmentConfig.PackageConfig pkg : config.getAndroidPackages()) {
            if (pkg.isUidConfigured() && pkg.getUid() != null && pkg.getUid().intValue() == uid) {
                names.add(pkg.getPackageName());
            }
        }
        if (packagesForUid) {
            if (names.isEmpty()) {
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "PackageManager.getPackagesForUid",
                        "uid=" + uid + ",count=0,value=null",
                        "json-config", "按UID查询配置包列表");
                return AndroidPackageObjectResult.of(null);
            }
            StringObject[] objects = new StringObject[names.size()];
            for (int i = 0; i < names.size(); i++) {
                objects[i] = new StringObject(vm, names.get(i));
            }
            ArrayObject array = new ArrayObject(objects);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                    "PackageManager.getPackagesForUid",
                    "uid=" + uid + ",count=" + names.size() + ",value=" + names,
                    "json-config", "按UID查询配置包列表");
            return AndroidPackageObjectResult.of(array);
        }
        // getNameForUid: first config-order match, or null
        if (names.isEmpty()) {
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                    "PackageManager.getNameForUid",
                    "uid=" + uid + ",count=0,value=null",
                    "json-config", "按UID查询配置包名");
            return AndroidPackageObjectResult.of(null);
        }
        String first = names.get(0);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                "PackageManager.getNameForUid",
                "uid=" + uid + ",count=" + names.size() + ",value=" + first,
                "json-config", "按UID查询配置包名");
        return AndroidPackageObjectResult.of(new StringObject(vm, first));
    }

    /**
     * PackageManager.checkPermission and Context/Application/ContextWrapper/Activity.checkSelfPermission
     * when {@code android.packages} is configured with an authoritative {@code permissions} map.
     * Signature matched before args. Returns 0 (granted) only for configured true; configured false
     * and permission absent from the map (including empty map) return -1 (denied). Missing packages
     * node or missing permissions node is notHandled; absent package throws UOE.
     */
    private static AndroidPackageIntFieldResult tryAndroidPackageCheckPermission(BaseVM vm, String signature,
                                                                                 VarArg args) {
        final boolean packageManagerCheck =
                "android/content/pm/PackageManager->checkPermission(Ljava/lang/String;Ljava/lang/String;)I"
                        .equals(signature);
        final boolean selfCheck =
                "android/content/Context->checkSelfPermission(Ljava/lang/String;)I".equals(signature)
                        || "android/app/Application->checkSelfPermission(Ljava/lang/String;)I".equals(signature)
                        || "android/content/ContextWrapper->checkSelfPermission(Ljava/lang/String;)I".equals(signature)
                        || "android/app/Activity->checkSelfPermission(Ljava/lang/String;)I".equals(signature);
        if (!packageManagerCheck && !selfCheck) {
            return AndroidPackageIntFieldResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidPackagesConfigured()) {
            return AndroidPackageIntFieldResult.notHandled();
        }

        DvmObject<?> permissionArg = args.getObjectArg(0);
        if (!(permissionArg instanceof StringObject)) {
            throw new IllegalArgumentException("signature=" + signature
                    + ", arg0 must be StringObject, was=" + permissionArg);
        }
        String permission = ((StringObject) permissionArg).getValue();

        final String packageName;
        if (packageManagerCheck) {
            DvmObject<?> packageArg = args.getObjectArg(1);
            if (!(packageArg instanceof StringObject)) {
                throw new IllegalArgumentException("signature=" + signature
                        + ", arg1 must be StringObject, was=" + packageArg);
            }
            packageName = ((StringObject) packageArg).getValue();
        } else {
            packageName = vm.getPackageName();
            if (packageName == null) {
                throw new UnsupportedOperationException(signature + " package=null");
            }
        }

        TraceEnvironmentConfig.PackageConfig pkg = requireConfiguredAndroidPackage(vm, signature, packageName);
        if (!pkg.isPermissionsConfigured()) {
            return AndroidPackageIntFieldResult.notHandled();
        }
        // Authoritative map: true → PERMISSION_GRANTED (0); false or absent → PERMISSION_DENIED (-1).
        Boolean granted = pkg.getPermissions().get(permission);
        int result = Boolean.TRUE.equals(granted) ? 0 : -1;
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_permission",
                packageManagerCheck ? "PackageManager.checkPermission" : "Context.checkSelfPermission",
                "package=" + packageName
                        + ",permission=" + permission
                        + ",granted=" + Boolean.TRUE.equals(granted)
                        + ",result=" + result,
                "json-config", "校验配置的应用权限");
        return AndroidPackageIntFieldResult.of(result);
    }

    /**
     * PackageManager.getPackageInfo when {@code android.packages} is configured.
     * Signature matched before args. Exact configured package returns marker PackageInfo;
     * configured but missing package throws UOE (blocks old arbitrary-package fallback);
     * node absent is notHandled (preserve old behavior).
     */
    private static AndroidPackageObjectResult tryAndroidPackageGetPackageInfo(BaseVM vm, String signature,
                                                                              VarArg args) {
        if (!"android/content/pm/PackageManager->getPackageInfo(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;"
                .equals(signature)) {
            return AndroidPackageObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidPackagesConfigured()) {
            return AndroidPackageObjectResult.notHandled();
        }
        DvmObject<?> nameArg = args.getObjectArg(0);
        if (!(nameArg instanceof StringObject)) {
            throw new IllegalArgumentException("signature=" + signature
                    + ", arg0 must be StringObject, was=" + nameArg);
        }
        String packageName = ((StringObject) nameArg).getValue();
        int flags = args.getIntArg(1);
        TraceEnvironmentConfig.PackageConfig pkg = requireConfiguredAndroidPackage(vm, signature, packageName);
        // packages node is configured; requireConfiguredAndroidPackage throws if package missing.
        DvmObject<?> packageInfo = vm.resolveClass("android/content/pm/PackageInfo")
                .newObject(new ConfiguredPackageInfo(vm, pkg, flags));
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                "PackageManager.getPackageInfo",
                "package=" + packageName + ",flags=0x" + Integer.toHexString(flags),
                "json-config", "返回配置的 PackageInfo 包信息");
        return AndroidPackageObjectResult.of(packageInfo);
    }

    /**
     * PackageManager.getApplicationInfo when {@code android.packages} is configured.
     * Signature matched before args. Exact configured package returns marker ApplicationInfo;
     * configured but missing package / empty array throws UOE (blocks old current-app fallback);
     * node absent is notHandled (preserve old behavior).
     */
    private static AndroidPackageObjectResult tryAndroidPackageGetApplicationInfo(BaseVM vm, String signature,
                                                                                  VarArg args) {
        if (!"android/content/pm/PackageManager->getApplicationInfo(Ljava/lang/String;I)Landroid/content/pm/ApplicationInfo;"
                .equals(signature)) {
            return AndroidPackageObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidPackagesConfigured()) {
            return AndroidPackageObjectResult.notHandled();
        }
        DvmObject<?> nameArg = args.getObjectArg(0);
        if (!(nameArg instanceof StringObject)) {
            throw new IllegalArgumentException("signature=" + signature
                    + ", arg0 must be StringObject, was=" + nameArg);
        }
        String packageName = ((StringObject) nameArg).getValue();
        int flags = args.getIntArg(1);
        TraceEnvironmentConfig.PackageConfig pkg = requireConfiguredAndroidPackage(vm, signature, packageName);
        DvmObject<?> applicationInfo = vm.resolveClass("android/content/pm/ApplicationInfo")
                .newObject(new ConfiguredApplicationInfo(vm, pkg, flags));
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                "PackageManager.getApplicationInfo",
                "package=" + packageName + ",flags=0x" + Integer.toHexString(flags),
                "json-config", "返回配置的 ApplicationInfo 应用信息");
        return AndroidPackageObjectResult.of(applicationInfo);
    }

    /**
     * PackageManager.getInstalledPackages / getInstalledApplications when {@code android.packages}
     * is configured. Signature matched before reading flags. Returns ArrayListObject in config
     * array order; empty config array yields an empty handled list. Node absent is notHandled.
     * List elements carry ConfiguredPackageInfo / ConfiguredApplicationInfo markers with the
     * requested flags so existing field readers work.
     */
    private static AndroidPackageObjectResult tryAndroidPackageGetInstalledList(BaseVM vm, String signature,
                                                                                VarArg args) {
        final boolean packagesList =
                "android/content/pm/PackageManager->getInstalledPackages(I)Ljava/util/List;".equals(signature);
        final boolean applicationsList =
                "android/content/pm/PackageManager->getInstalledApplications(I)Ljava/util/List;".equals(signature);
        if (!packagesList && !applicationsList) {
            return AndroidPackageObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidPackagesConfigured()) {
            return AndroidPackageObjectResult.notHandled();
        }
        int flags = args.getIntArg(0);
        List<TraceEnvironmentConfig.PackageConfig> packages = config.getAndroidPackages();
        List<DvmObject<?>> elements = new ArrayList<DvmObject<?>>(packages.size());
        if (packagesList) {
            DvmClass packageInfoClass = vm.resolveClass("android/content/pm/PackageInfo");
            for (TraceEnvironmentConfig.PackageConfig pkg : packages) {
                elements.add(packageInfoClass.newObject(new ConfiguredPackageInfo(vm, pkg, flags)));
            }
            ArrayListObject list = new ArrayListObject(vm, elements);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                    "PackageManager.getInstalledPackages",
                    "flags=0x" + Integer.toHexString(flags) + ",count=" + elements.size(),
                    "json-config", "返回配置的已安装包列表");
            return AndroidPackageObjectResult.of(list);
        }
        DvmClass applicationInfoClass = vm.resolveClass("android/content/pm/ApplicationInfo");
        for (TraceEnvironmentConfig.PackageConfig pkg : packages) {
            elements.add(applicationInfoClass.newObject(new ConfiguredApplicationInfo(vm, pkg, flags)));
        }
        ArrayListObject list = new ArrayListObject(vm, elements);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                "PackageManager.getInstalledApplications",
                "flags=0x" + Integer.toHexString(flags) + ",count=" + elements.size(),
                "json-config", "返回配置的已安装应用列表");
        return AndroidPackageObjectResult.of(list);
    }

    /**
     * PackageManager.getInstallerPackageName when {@code android.packages} is configured.
     * Signature matched before args. Exact package required; installerPackageName must be configured.
     * Explicit JSON null returns Java null. Missing field / absent package throws UOE.
     * Node absent is notHandled (preserve old behavior).
     */
    private static AndroidPackageObjectResult tryAndroidPackageGetInstallerPackageName(BaseVM vm, String signature,
                                                                                       VarArg args) {
        if (!"android/content/pm/PackageManager->getInstallerPackageName(Ljava/lang/String;)Ljava/lang/String;"
                .equals(signature)) {
            return AndroidPackageObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidPackagesConfigured()) {
            return AndroidPackageObjectResult.notHandled();
        }
        DvmObject<?> nameArg = args.getObjectArg(0);
        if (!(nameArg instanceof StringObject)) {
            throw new IllegalArgumentException("signature=" + signature
                    + ", arg0 must be StringObject, was=" + nameArg);
        }
        String packageName = ((StringObject) nameArg).getValue();
        TraceEnvironmentConfig.PackageConfig pkg = requireConfiguredAndroidPackage(vm, signature, packageName);
        if (!pkg.isInstallerPackageNameConfigured()) {
            throw new UnsupportedOperationException(signature + " package=" + packageName
                    + " installerPackageName not configured");
        }
        String installer = pkg.getInstallerPackageName();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                "PackageManager.getInstallerPackageName",
                "package=" + packageName + ",value=" + String.valueOf(installer),
                "json-config", "读取配置的安装来源包名");
        if (installer == null) {
            return AndroidPackageObjectResult.of(null);
        }
        return AndroidPackageObjectResult.of(new StringObject(vm, installer));
    }

    /**
     * PackageManager.getInstallSourceInfo when {@code android.packages} is configured.
     * Signature matched before args. Exact package returns marker InstallSourceInfo;
     * absent package / empty array throws UOE. Node absent is notHandled.
     */
    private static AndroidPackageObjectResult tryAndroidPackageGetInstallSourceInfo(BaseVM vm, String signature,
                                                                                    VarArg args) {
        if (!"android/content/pm/PackageManager->getInstallSourceInfo(Ljava/lang/String;)Landroid/content/pm/InstallSourceInfo;"
                .equals(signature)) {
            return AndroidPackageObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidPackagesConfigured()) {
            return AndroidPackageObjectResult.notHandled();
        }
        DvmObject<?> nameArg = args.getObjectArg(0);
        if (!(nameArg instanceof StringObject)) {
            throw new IllegalArgumentException("signature=" + signature
                    + ", arg0 must be StringObject, was=" + nameArg);
        }
        String packageName = ((StringObject) nameArg).getValue();
        TraceEnvironmentConfig.PackageConfig pkg = requireConfiguredAndroidPackage(vm, signature, packageName);
        DvmObject<?> installSourceInfo = vm.resolveClass("android/content/pm/InstallSourceInfo")
                .newObject(new ConfiguredInstallSourceInfo(vm, pkg));
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                "PackageManager.getInstallSourceInfo",
                "package=" + packageName,
                "json-config", "返回配置的 InstallSourceInfo 安装来源对象");
        return AndroidPackageObjectResult.of(installSourceInfo);
    }

    /**
     * InstallSourceInfo.getInstallingPackageName / getInitiatingPackageName /
     * getOriginatingPackageName for same-VM marker objects only.
     * Requires presence flags; explicit JSON null returns Java null; missing field UOE.
     * Plain / foreign-VM receivers → notHandled (UOE path, no sidecar).
     */
    private static AndroidPackageObjectResult tryAndroidPackageInstallSourceInfoMethod(BaseVM vm,
                                                                                       DvmObject<?> dvmObject,
                                                                                       String signature) {
        if (!isConfiguredInstallSourceInfo(vm, dvmObject)) {
            return AndroidPackageObjectResult.notHandled();
        }
        ConfiguredInstallSourceInfo marker = (ConfiguredInstallSourceInfo) dvmObject.getValue();
        TraceEnvironmentConfig.PackageConfig pkg = marker.packageConfig;
        if ("android/content/pm/InstallSourceInfo->getInstallingPackageName()Ljava/lang/String;"
                .equals(signature)) {
            if (!pkg.isInstallerPackageNameConfigured()) {
                throw new UnsupportedOperationException(signature + " package=" + pkg.getPackageName()
                        + " installerPackageName not configured");
            }
            String value = pkg.getInstallerPackageName();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                    "InstallSourceInfo.getInstallingPackageName",
                    "package=" + pkg.getPackageName() + ",value=" + String.valueOf(value),
                    "json-config", "读取配置的安装来源包名");
            if (value == null) {
                return AndroidPackageObjectResult.of(null);
            }
            return AndroidPackageObjectResult.of(new StringObject(vm, value));
        }
        if ("android/content/pm/InstallSourceInfo->getInitiatingPackageName()Ljava/lang/String;"
                .equals(signature)) {
            if (!pkg.isInitiatingPackageNameConfigured()) {
                throw new UnsupportedOperationException(signature + " package=" + pkg.getPackageName()
                        + " initiatingPackageName not configured");
            }
            String value = pkg.getInitiatingPackageName();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                    "InstallSourceInfo.getInitiatingPackageName",
                    "package=" + pkg.getPackageName() + ",value=" + String.valueOf(value),
                    "json-config", "读取配置的发起安装包名");
            if (value == null) {
                return AndroidPackageObjectResult.of(null);
            }
            return AndroidPackageObjectResult.of(new StringObject(vm, value));
        }
        if ("android/content/pm/InstallSourceInfo->getOriginatingPackageName()Ljava/lang/String;"
                .equals(signature)) {
            if (!pkg.isOriginatingPackageNameConfigured()) {
                throw new UnsupportedOperationException(signature + " package=" + pkg.getPackageName()
                        + " originatingPackageName not configured");
            }
            String value = pkg.getOriginatingPackageName();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                    "InstallSourceInfo.getOriginatingPackageName",
                    "package=" + pkg.getPackageName() + ",value=" + String.valueOf(value),
                    "json-config", "读取配置的原始安装来源包名");
            if (value == null) {
                return AndroidPackageObjectResult.of(null);
            }
            return AndroidPackageObjectResult.of(new StringObject(vm, value));
        }
        // Other InstallSourceInfo methods stay UOE.
        if (signature != null
                && signature.startsWith("android/content/pm/InstallSourceInfo->")) {
            throw new UnsupportedOperationException(signature);
        }
        return AndroidPackageObjectResult.notHandled();
    }

    /**
     * SigningInfo.getApkContentsSigners / getSigningCertificateHistory for same-VM
     * {@link ConfiguredSigningInfo} only. getApkContentsSigners requires signaturesHex.
     * History: multi current signers → Java null; else history field if present; else fall
     * back to current signaturesHex; both missing → UOE.
     * Foreign ConfiguredSigningInfo SigningInfo methods throw UOE (no sidecar, no fallback).
     * Plain receivers stay notHandled (existing UOE path).
     */
    private static AndroidPackageObjectResult tryAndroidPackageSigningInfoMethod(BaseVM vm,
                                                                                 DvmObject<?> dvmObject,
                                                                                 String signature) {
        if (!isLiveConfiguredSigningInfo(vm, dvmObject)) {
            if (isConfiguredSigningInfo(dvmObject)
                    && signature != null
                    && signature.startsWith("android/content/pm/SigningInfo->")) {
                throw new UnsupportedOperationException(signature);
            }
            return AndroidPackageObjectResult.notHandled();
        }
        ConfiguredSigningInfo marker = (ConfiguredSigningInfo) dvmObject.getValue();
        TraceEnvironmentConfig.PackageConfig pkg = marker.packageConfig;
        if ("android/content/pm/SigningInfo->getApkContentsSigners()[Landroid/content/pm/Signature;"
                .equals(signature)) {
            if (!pkg.isSignaturesConfigured()) {
                throw new UnsupportedOperationException(signature + " package=" + pkg.getPackageName()
                        + " signaturesHex not configured");
            }
            List<String> hexes = pkg.getSignatureHexes();
            ArrayObject array = signatureArrayFromHexes(vm, hexes);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                    "SigningInfo.getApkContentsSigners",
                    "package=" + pkg.getPackageName() + ",count=" + hexes.size(),
                    "json-config", "读取配置的 APK 内容签名");
            return AndroidPackageObjectResult.of(array);
        }
        if ("android/content/pm/SigningInfo->getSigningCertificateHistory()[Landroid/content/pm/Signature;"
                .equals(signature)) {
            // Android: when multiple APK content signers, certificate history is null.
            if (pkg.isSignaturesConfigured() && pkg.getSignatureHexes().size() > 1) {
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "SigningInfo.getSigningCertificateHistory",
                        "package=" + pkg.getPackageName() + ",count=null,reason=multiple-signers",
                        "json-config", "读取配置的证书轮换历史");
                return AndroidPackageObjectResult.of(null);
            }
            if (pkg.isSigningCertificateHistoryConfigured()) {
                List<String> hexes = pkg.getSigningCertificateHistoryHexes();
                ArrayObject array = signatureArrayFromHexes(vm, hexes);
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "SigningInfo.getSigningCertificateHistory",
                        "package=" + pkg.getPackageName() + ",count=" + hexes.size() + ",source=history",
                        "json-config", "读取配置的证书轮换历史");
                return AndroidPackageObjectResult.of(array);
            }
            if (pkg.isSignaturesConfigured()) {
                List<String> hexes = pkg.getSignatureHexes();
                ArrayObject array = signatureArrayFromHexes(vm, hexes);
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "SigningInfo.getSigningCertificateHistory",
                        "package=" + pkg.getPackageName() + ",count=" + hexes.size() + ",source=current",
                        "json-config", "读取配置的证书轮换历史");
                return AndroidPackageObjectResult.of(array);
            }
            throw new UnsupportedOperationException(signature + " package=" + pkg.getPackageName()
                    + " signaturesHex and signingCertificateHistoryHex not configured");
        }
        if (signature != null && signature.startsWith("android/content/pm/SigningInfo->")) {
            throw new UnsupportedOperationException(signature);
        }
        return AndroidPackageObjectResult.notHandled();
    }

    /**
     * SigningInfo.hasMultipleSigners for same-VM {@link ConfiguredSigningInfo} only.
     * Requires signaturesHex; true when size &gt; 1.
     * Foreign ConfiguredSigningInfo SigningInfo methods throw UOE (no sidecar, no fallback).
     * Plain receivers stay notHandled (existing UOE path).
     */
    private static AndroidPackageBooleanFieldResult tryAndroidPackageSigningInfoBoolean(BaseVM vm,
                                                                                        DvmObject<?> dvmObject,
                                                                                        String signature) {
        if (!isLiveConfiguredSigningInfo(vm, dvmObject)) {
            if (isConfiguredSigningInfo(dvmObject)
                    && signature != null
                    && signature.startsWith("android/content/pm/SigningInfo->")) {
                throw new UnsupportedOperationException(signature);
            }
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        if (!"android/content/pm/SigningInfo->hasMultipleSigners()Z".equals(signature)) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        ConfiguredSigningInfo marker = (ConfiguredSigningInfo) dvmObject.getValue();
        TraceEnvironmentConfig.PackageConfig pkg = marker.packageConfig;
        if (!pkg.isSignaturesConfigured()) {
            throw new UnsupportedOperationException(signature + " package=" + pkg.getPackageName()
                    + " signaturesHex not configured");
        }
        boolean multi = pkg.getSignatureHexes().size() > 1;
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                "SigningInfo.hasMultipleSigners",
                "package=" + pkg.getPackageName()
                        + ",count=" + pkg.getSignatureHexes().size()
                        + ",result=" + multi,
                "json-config", "判断是否多签名");
        return AndroidPackageBooleanFieldResult.of(multi);
    }

    /**
     * PackageManager.hasSigningCertificate String and UID overloads when {@code android.packages}
     * is configured. Signature matched before args.
     * <ul>
     *   <li>{@code (String,[B,I)Z}: exact package; both signature fields absent → UOE</li>
     *   <li>{@code (I,[B,I)Z}: exact configured uid; no match → false; shared uid picks
     *       newest-signed among packages with signature config (max history length, else
     *       signaturesHex length; ties keep config order); all matches lack sig config → UOE</li>
     * </ul>
     * Candidates are ordered de-duplicated union of signaturesHex then history.
     * inputType 0 = raw X.509; 1 = SHA-256. Other types → IAE.
     */
    private static AndroidPackageBooleanFieldResult tryAndroidPackageHasSigningCertificate(BaseVM vm,
                                                                                           String signature,
                                                                                           VarArg args) {
        final boolean byPackageName =
                "android/content/pm/PackageManager->hasSigningCertificate(Ljava/lang/String;[BI)Z"
                        .equals(signature);
        final boolean byUid =
                "android/content/pm/PackageManager->hasSigningCertificate(I[BI)Z".equals(signature);
        if (!byPackageName && !byUid) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidPackagesConfigured()) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }

        final int uid;
        final String packageNameArg;
        final DvmObject<?> certArg;
        final int inputType;
        if (byPackageName) {
            DvmObject<?> nameArg = args.getObjectArg(0);
            if (!(nameArg instanceof StringObject)) {
                throw new IllegalArgumentException("signature=" + signature
                        + ", arg0 must be StringObject, was=" + nameArg);
            }
            packageNameArg = ((StringObject) nameArg).getValue();
            uid = 0;
            certArg = args.getObjectArg(1);
            inputType = args.getIntArg(2);
        } else {
            packageNameArg = null;
            uid = args.getIntArg(0);
            certArg = args.getObjectArg(1);
            inputType = args.getIntArg(2);
        }

        if (!(certArg instanceof ByteArray)) {
            throw new IllegalArgumentException("signature=" + signature
                    + ", cert arg must be ByteArray, was=" + certArg);
        }
        byte[] input = ((ByteArray) certArg).getValue();
        if (input == null) {
            throw new IllegalArgumentException("signature=" + signature + ", cert byte[] must not be null");
        }
        requireSigningCertificateInputType(signature, inputType);

        final TraceEnvironmentConfig.PackageConfig pkg;
        final String selectedPackage;
        if (byPackageName) {
            pkg = requireConfiguredAndroidPackage(vm, signature, packageNameArg);
            if (!hasConfiguredSigningMaterial(pkg)) {
                throw new UnsupportedOperationException(signature + " package=" + packageNameArg
                        + " signaturesHex and signingCertificateHistoryHex not configured");
            }
            selectedPackage = pkg.getPackageName();
        } else {
            List<TraceEnvironmentConfig.PackageConfig> uidMatches =
                    new ArrayList<TraceEnvironmentConfig.PackageConfig>();
            for (TraceEnvironmentConfig.PackageConfig candidate : config.getAndroidPackages()) {
                if (candidate.isUidConfigured() && candidate.getUid() != null
                        && candidate.getUid().intValue() == uid) {
                    uidMatches.add(candidate);
                }
            }
            if (uidMatches.isEmpty()) {
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "PackageManager.hasSigningCertificate",
                        "uid=" + uid
                                + ",selectedPackage=null"
                                + ",type=" + inputType
                                + ",inputLength=" + input.length
                                + ",candidateCount=0"
                                + ",result=false",
                        "json-config", "校验配置的签名证书");
                return AndroidPackageBooleanFieldResult.of(false);
            }
            TraceEnvironmentConfig.PackageConfig best = null;
            int bestRank = -1;
            for (TraceEnvironmentConfig.PackageConfig candidate : uidMatches) {
                if (!hasConfiguredSigningMaterial(candidate)) {
                    continue;
                }
                int rank = signingNewestRank(candidate);
                // Strict greater keeps earlier config-order package on ties.
                if (best == null || rank > bestRank) {
                    best = candidate;
                    bestRank = rank;
                }
            }
            if (best == null) {
                throw new UnsupportedOperationException(signature + " uid=" + uid
                        + " matching packages lack signaturesHex and signingCertificateHistoryHex");
            }
            pkg = best;
            selectedPackage = pkg.getPackageName();
        }

        List<byte[]> candidates = collectSigningCertificateCandidates(pkg);
        boolean result = matchSigningCertificateInput(candidates, input, inputType);

        if (byPackageName) {
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                    "PackageManager.hasSigningCertificate",
                    "package=" + selectedPackage
                            + ",type=" + inputType
                            + ",inputLength=" + input.length
                            + ",candidateCount=" + candidates.size()
                            + ",result=" + result,
                    "json-config", "校验配置的签名证书");
        } else {
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                    "PackageManager.hasSigningCertificate",
                    "uid=" + uid
                            + ",selectedPackage=" + selectedPackage
                            + ",type=" + inputType
                            + ",inputLength=" + input.length
                            + ",candidateCount=" + candidates.size()
                            + ",result=" + result,
                    "json-config", "校验配置的签名证书");
        }
        return AndroidPackageBooleanFieldResult.of(result);
    }

    private static boolean hasConfiguredSigningMaterial(TraceEnvironmentConfig.PackageConfig pkg) {
        return pkg.isSignaturesConfigured() || pkg.isSigningCertificateHistoryConfigured();
    }

    /**
     * Ranking for newest-signed among shared-uid packages: history length when configured,
     * otherwise signaturesHex length.
     */
    private static int signingNewestRank(TraceEnvironmentConfig.PackageConfig pkg) {
        if (pkg.isSigningCertificateHistoryConfigured()) {
            return pkg.getSigningCertificateHistoryHexes().size();
        }
        return pkg.getSignatureHexes().size();
    }

    /**
     * Ordered de-duplicated union of {@code signaturesHex} then {@code signingCertificateHistoryHex}.
     */
    private static List<byte[]> collectSigningCertificateCandidates(
            TraceEnvironmentConfig.PackageConfig pkg) {
        List<byte[]> candidates = new ArrayList<byte[]>();
        Set<String> seenHex = new HashSet<String>();
        if (pkg.isSignaturesConfigured()) {
            for (String hex : pkg.getSignatureHexes()) {
                if (seenHex.add(hex)) {
                    candidates.add(decodeConfiguredSignatureHex(hex));
                }
            }
        }
        if (pkg.isSigningCertificateHistoryConfigured()) {
            for (String hex : pkg.getSigningCertificateHistoryHexes()) {
                if (seenHex.add(hex)) {
                    candidates.add(decodeConfiguredSignatureHex(hex));
                }
            }
        }
        return candidates;
    }

    private static void requireSigningCertificateInputType(String signature, int inputType) {
        if (inputType != 0 && inputType != 1) {
            throw new IllegalArgumentException("signature=" + signature
                    + ", inputType must be 0 (RAW_X509) or 1 (SHA256), was=" + inputType);
        }
    }

    /**
     * Match input against candidates: type 0 raw equality, type 1 SHA-256 of each candidate.
     * Caller must validate inputType.
     */
    private static boolean matchSigningCertificateInput(List<byte[]> candidates, byte[] input, int inputType) {
        if (inputType == 0) {
            for (byte[] candidate : candidates) {
                if (Arrays.equals(candidate, input)) {
                    return true;
                }
            }
            return false;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        for (byte[] candidate : candidates) {
            if (Arrays.equals(digest.digest(candidate), input)) {
                return true;
            }
        }
        return false;
    }

    /**
     * KeyFactory.getKeySpec(Key, Class) when {@code android.tee} is configured.
     * Signature matched before args. Only when arg1 is DvmClass KeyInfo: return marker KeyInfo.
     * Other target classes notHandled; wrong arg1 type → IAE. No real key material or crypto.
     */
    private static AndroidPackageObjectResult tryAndroidTeeKeyFactoryGetKeySpec(BaseVM vm, String signature,
                                                                                VarArg args) {
        if (!"java/security/KeyFactory->getKeySpec(Ljava/security/Key;Ljava/lang/Class;)Ljava/security/spec/KeySpec;"
                .equals(signature)) {
            return AndroidPackageObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidTeeConfigured()) {
            return AndroidPackageObjectResult.notHandled();
        }
        // arg0 is Key (ignored for marker path); arg1 is Class
        DvmObject<?> classArg = args.getObjectArg(1);
        if (!(classArg instanceof DvmClass)) {
            throw new IllegalArgumentException("signature=" + signature
                    + ", arg1 must be DvmClass, was=" + classArg);
        }
        DvmClass targetClass = (DvmClass) classArg;
        if (!"android/security/keystore/KeyInfo".equals(targetClass.getClassName())) {
            return AndroidPackageObjectResult.notHandled();
        }
        DvmObject<?> keyInfo = vm.resolveClass("android/security/keystore/KeyInfo")
                .newObject(new ConfiguredTeeKeyInfo(vm, config));
        String markerSummary = config.isTeeMarkerConfigured()
                ? config.getTeeMarker() : "null";
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "tee",
                "KeyFactory.getKeySpec",
                "method=getKeySpec,result=KeyInfo,marker=" + markerSummary,
                "json-config", "返回配置的 KeyInfo 标记对象（不做真实密钥运算）");
        return AndroidPackageObjectResult.of(keyInfo);
    }

    /**
     * KeyInfo.getKeystoreAlias for live same-VM ConfiguredTeeKeyInfo only.
     * Requires android.tee.marker. Foreign/stale markers UOE with no sidecar.
     */
    private static AndroidPackageObjectResult tryAndroidTeeKeyInfoObjectMethod(BaseVM vm,
                                                                               DvmObject<?> dvmObject,
                                                                               String signature) {
        if (!isConfiguredTeeKeyInfo(dvmObject)) {
            return AndroidPackageObjectResult.notHandled();
        }
        if (!"android/security/keystore/KeyInfo->getKeystoreAlias()Ljava/lang/String;"
                .equals(signature)) {
            if (signature != null && signature.startsWith("android/security/keystore/KeyInfo->")) {
                throw new UnsupportedOperationException(signature);
            }
            return AndroidPackageObjectResult.notHandled();
        }
        if (!isLiveConfiguredTeeKeyInfo(vm, dvmObject)) {
            throw new UnsupportedOperationException(signature);
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isTeeMarkerConfigured()) {
            throw new UnsupportedOperationException(signature + " android.tee.marker not configured");
        }
        String marker = config.getTeeMarker();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "tee",
                "KeyInfo.getKeystoreAlias",
                "method=getKeystoreAlias,result=" + marker + ",marker=" + marker,
                "json-config", "读取配置的 KeyInfo 别名标记（不做真实密钥运算）");
        return AndroidPackageObjectResult.of(new StringObject(vm, marker));
    }

    /**
     * KeyInfo.getSecurityLevel for live same-VM ConfiguredTeeKeyInfo only.
     * SOFTWARE=0, TRUSTED_ENVIRONMENT=1, STRONGBOX=2.
     * Foreign/stale markers UOE with no sidecar.
     */
    private static AndroidPackageIntFieldResult tryAndroidTeeKeyInfoIntMethod(BaseVM vm,
                                                                              DvmObject<?> dvmObject,
                                                                              String signature) {
        if (!isConfiguredTeeKeyInfo(dvmObject)) {
            return AndroidPackageIntFieldResult.notHandled();
        }
        if (!"android/security/keystore/KeyInfo->getSecurityLevel()I".equals(signature)) {
            return AndroidPackageIntFieldResult.notHandled();
        }
        if (!isLiveConfiguredTeeKeyInfo(vm, dvmObject)) {
            throw new UnsupportedOperationException(signature);
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isTeeSecurityLevelConfigured()) {
            throw new UnsupportedOperationException(signature + " android.tee.securityLevel not configured");
        }
        String level = config.getTeeSecurityLevel();
        final int result;
        if ("SOFTWARE".equals(level)) {
            result = 0;
        } else if ("TRUSTED_ENVIRONMENT".equals(level)) {
            result = 1;
        } else if ("STRONGBOX".equals(level)) {
            result = 2;
        } else {
            throw new UnsupportedOperationException(signature + " unknown securityLevel=" + level);
        }
        String markerSummary = config.isTeeMarkerConfigured() ? config.getTeeMarker() : "null";
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "tee",
                "KeyInfo.getSecurityLevel",
                "method=getSecurityLevel,result=" + result + ",marker=" + markerSummary,
                "json-config", "读取配置的安全等级（不做真实密钥运算）");
        return AndroidPackageIntFieldResult.of(result);
    }

    /**
     * KeyInfo.isInsideSecureHardware for live same-VM ConfiguredTeeKeyInfo only.
     * true when securityLevel is not SOFTWARE. Foreign/stale markers UOE with no sidecar.
     */
    private static AndroidPackageBooleanFieldResult tryAndroidTeeKeyInfoBooleanMethod(BaseVM vm,
                                                                                      DvmObject<?> dvmObject,
                                                                                      String signature) {
        if (!isConfiguredTeeKeyInfo(dvmObject)) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        if (!"android/security/keystore/KeyInfo->isInsideSecureHardware()Z".equals(signature)) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        if (!isLiveConfiguredTeeKeyInfo(vm, dvmObject)) {
            throw new UnsupportedOperationException(signature);
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isTeeSecurityLevelConfigured()) {
            throw new UnsupportedOperationException(signature + " android.tee.securityLevel not configured");
        }
        boolean inside = !"SOFTWARE".equals(config.getTeeSecurityLevel());
        String markerSummary = config.isTeeMarkerConfigured() ? config.getTeeMarker() : "null";
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "tee",
                "KeyInfo.isInsideSecureHardware",
                "method=isInsideSecureHardware,result=" + inside + ",marker=" + markerSummary,
                "json-config", "读取是否位于安全硬件（不做真实密钥运算）");
        return AndroidPackageBooleanFieldResult.of(inside);
    }

    /**
     * KeyStore.getKey(String, char[]) when {@code android.tee.keyBlobHex} is configured.
     * Signature matched before args. arg0 must be non-null StringObject (alias); password ignored.
     * Returns a marker {@code java/security/Key} ({@link ConfiguredTeeKey}); analysis-only, no crypto.
     */
    private static AndroidPackageObjectResult tryAndroidTeeKeyStoreGetKey(BaseVM vm, String signature,
                                                                          VarArg args) {
        if (!"java/security/KeyStore->getKey(Ljava/lang/String;[C)Ljava/security/Key;"
                .equals(signature)) {
            return AndroidPackageObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isTeeKeyBlobConfigured()) {
            return AndroidPackageObjectResult.notHandled();
        }
        DvmObject<?> aliasArg = args.getObjectArg(0);
        if (!(aliasArg instanceof StringObject)) {
            throw new IllegalArgumentException("signature=" + signature
                    + ", arg0 must be StringObject, was=" + aliasArg);
        }
        String alias = ((StringObject) aliasArg).getValue();
        if (alias == null) {
            throw new IllegalArgumentException("signature=" + signature + ", arg0 alias must not be null");
        }
        DvmObject<?> key = vm.resolveClass("java/security/Key")
                .newObject(new ConfiguredTeeKey(vm, config, alias));
        String markerSummary = config.isTeeMarkerConfigured() ? config.getTeeMarker() : "null";
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "tee",
                "KeyStore.getKey",
                "alias=" + alias + ",result=Key,marker=" + markerSummary,
                "json-config", "返回配置的固定 Key 标记（仅分析 marker，不做生成/加密/签名）");
        return AndroidPackageObjectResult.of(key);
    }

    /**
     * Key.getEncoded() for live same-VM ConfiguredTeeKey only. Returns a fresh ByteArray from
     * {@link TraceEnvironmentConfig#getTeeKeyBlob()} (defensive copy each call). No blob hex in sidecar.
     * Foreign/stale ConfiguredTeeKey → UOE with no sidecar. Plain Key → notHandled.
     */
    private static AndroidPackageObjectResult tryAndroidTeeKeyGetEncoded(BaseVM vm, DvmObject<?> dvmObject,
                                                                         String signature) {
        if (!"java/security/Key->getEncoded()[B".equals(signature)) {
            return AndroidPackageObjectResult.notHandled();
        }
        if (!isConfiguredTeeKey(dvmObject)) {
            return AndroidPackageObjectResult.notHandled();
        }
        if (!isLiveConfiguredTeeKey(vm, dvmObject)) {
            throw new UnsupportedOperationException(signature);
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isTeeKeyBlobConfigured()) {
            throw new UnsupportedOperationException(signature + " android.tee.keyBlobHex not configured");
        }
        byte[] blob = config.getTeeKeyBlob();
        if (blob == null) {
            throw new UnsupportedOperationException(signature + " android.tee.keyBlobHex empty");
        }
        ConfiguredTeeKey markerKey = (ConfiguredTeeKey) dvmObject.getValue();
        String markerSummary = config.isTeeMarkerConfigured()
                ? config.getTeeMarker()
                : (markerKey != null ? markerKey.alias : "null");
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "tee",
                "Key.getEncoded",
                "byteLength=" + blob.length + ",marker=" + markerSummary,
                "json-config", "读取配置的固定 key blob 标记字节（仅分析 marker，不做生成/加密/签名）");
        return AndroidPackageObjectResult.of(new ByteArray(vm, blob));
    }

    /**
     * Exact instance signatures for live same-VM {@link ConfiguredTeeKey} only:
     * <ul>
     *   <li>{@code java/security/Key->getAlgorithm()Ljava/lang/String;} when
     *       {@code android.tee.keyAlgorithm} is explicitly configured</li>
     *   <li>{@code java/security/Key->getFormat()Ljava/lang/String;} when
     *       {@code android.tee.keyFormat} is explicitly configured</li>
     * </ul>
     * Never inferred from marker, securityLevel, keyBlobHex, or each other.
     * Absent matching field, missing keyBlobHex/live marker, plain/stale/cross-VM, or wrong
     * signature → notHandled (UOE, no event). Analysis marker only; no crypto.
     */
    private static AndroidPackageObjectResult tryAndroidTeeKeyGetAlgorithmOrFormat(BaseVM vm,
                                                                                   DvmObject<?> dvmObject,
                                                                                   String signature) {
        final boolean isAlgorithm =
                "java/security/Key->getAlgorithm()Ljava/lang/String;".equals(signature);
        final boolean isFormat =
                "java/security/Key->getFormat()Ljava/lang/String;".equals(signature);
        if (!isAlgorithm && !isFormat) {
            return AndroidPackageObjectResult.notHandled();
        }
        if (!isLiveConfiguredTeeKey(vm, dvmObject)) {
            return AndroidPackageObjectResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isTeeKeyBlobConfigured()) {
            return AndroidPackageObjectResult.notHandled();
        }
        if (isAlgorithm) {
            if (!config.isTeeKeyAlgorithmConfigured()) {
                return AndroidPackageObjectResult.notHandled();
            }
            String algorithm = config.getTeeKeyAlgorithm();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "tee",
                    "Key.getAlgorithm",
                    "field=keyAlgorithm,result=" + algorithm,
                    "json-config", "读取配置的 Key 算法标记（不做真实密钥运算）");
            return AndroidPackageObjectResult.of(new StringObject(vm, algorithm));
        }
        if (!config.isTeeKeyFormatConfigured()) {
            return AndroidPackageObjectResult.notHandled();
        }
        String format = config.getTeeKeyFormat();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "tee",
                "Key.getFormat",
                "field=keyFormat,result=" + format,
                "json-config", "读取配置的 Key 编码格式标记（不做真实密钥运算）");
        return AndroidPackageObjectResult.of(new StringObject(vm, format));
    }

    /**
     * PackageInfo and ApplicationInfo object fields for configured marker objects only.
     * PackageInfo: same-VM live marker only. packageName always; versionName only when
     * isVersionNameConfigured (explicit null → Java null). ApplicationInfo: same-VM live marker only.
     * packageName always; sourceDir/publicSourceDir when isSourceDirConfigured (shared value);
     * dataDir when isDataDirConfigured. Explicit null → Java null;
     * omitted / plain / foreign-VM → notHandled + UOE (no sidecar).
     */
    private static AndroidPackageObjectFieldResult tryAndroidPackageObjectField(BaseVM vm, DvmObject<?> dvmObject,
                                                                                String signature) {
        if (isLiveConfiguredPackageInfo(vm, dvmObject)) {
            ConfiguredPackageInfo marker = (ConfiguredPackageInfo) dvmObject.getValue();
            TraceEnvironmentConfig.PackageConfig pkg = marker.packageConfig;
            if ("android/content/pm/PackageInfo->packageName:Ljava/lang/String;".equals(signature)) {
                String name = pkg.getPackageName();
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "PackageInfo.packageName",
                        "package=" + name + ",flags=0x" + Integer.toHexString(marker.flags) + ",value=" + name,
                        "json-config", "读取配置的包名");
                return AndroidPackageObjectFieldResult.of(new StringObject(vm, name));
            }
            if ("android/content/pm/PackageInfo->versionName:Ljava/lang/String;".equals(signature)) {
                if (!pkg.isVersionNameConfigured()) {
                    return AndroidPackageObjectFieldResult.notHandled();
                }
                String versionName = pkg.getVersionName();
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "PackageInfo.versionName",
                        "package=" + pkg.getPackageName()
                                + ",flags=0x" + Integer.toHexString(marker.flags)
                                + ",value=" + String.valueOf(versionName),
                        "json-config", "读取配置的版本名");
                if (versionName == null) {
                    return AndroidPackageObjectFieldResult.of(null);
                }
                return AndroidPackageObjectFieldResult.of(new StringObject(vm, versionName));
            }
            if ("android/content/pm/PackageInfo->signatures:[Landroid/content/pm/Signature;".equals(signature)) {
                if (!pkg.isSignaturesConfigured()) {
                    // Field absent: marker guard throws UOE (no fallthrough to APK signatures).
                    return AndroidPackageObjectFieldResult.notHandled();
                }
                List<String> hexes = pkg.getSignatureHexes();
                ArrayObject array = signatureArrayFromHexes(vm, hexes);
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "PackageInfo.signatures",
                        "package=" + pkg.getPackageName()
                                + ",flags=0x" + Integer.toHexString(marker.flags)
                                + ",count=" + hexes.size(),
                        "json-config", "读取配置的应用签名");
                return AndroidPackageObjectFieldResult.of(array);
            }
            if ("android/content/pm/PackageInfo->signingInfo:Landroid/content/pm/SigningInfo;".equals(signature)) {
                // Present when either signaturesHex or signingCertificateHistoryHex is configured.
                if (!pkg.isSignaturesConfigured() && !pkg.isSigningCertificateHistoryConfigured()) {
                    return AndroidPackageObjectFieldResult.notHandled();
                }
                DvmObject<?> signingInfo = vm.resolveClass("android/content/pm/SigningInfo")
                        .newObject(new ConfiguredSigningInfo(vm, pkg));
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "PackageInfo.signingInfo",
                        "package=" + pkg.getPackageName()
                                + ",flags=0x" + Integer.toHexString(marker.flags)
                                + ",signaturesConfigured=" + pkg.isSignaturesConfigured()
                                + ",historyConfigured=" + pkg.isSigningCertificateHistoryConfigured(),
                        "json-config", "返回配置的 SigningInfo");
                return AndroidPackageObjectFieldResult.of(signingInfo);
            }
            return AndroidPackageObjectFieldResult.notHandled();
        }
        if (isLiveConfiguredApplicationInfo(vm, dvmObject)) {
            ConfiguredApplicationInfo marker = (ConfiguredApplicationInfo) dvmObject.getValue();
            TraceEnvironmentConfig.PackageConfig pkg = marker.packageConfig;
            if ("android/content/pm/ApplicationInfo->packageName:Ljava/lang/String;".equals(signature)) {
                String name = pkg.getPackageName();
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "ApplicationInfo.packageName",
                        "package=" + name + ",flags=0x" + Integer.toHexString(marker.flags) + ",value=" + name,
                        "json-config", "读取配置的应用包名");
                return AndroidPackageObjectFieldResult.of(new StringObject(vm, name));
            }
            if ("android/content/pm/ApplicationInfo->sourceDir:Ljava/lang/String;".equals(signature)
                    || "android/content/pm/ApplicationInfo->publicSourceDir:Ljava/lang/String;".equals(signature)) {
                if (!pkg.isSourceDirConfigured()) {
                    return AndroidPackageObjectFieldResult.notHandled();
                }
                String sourceDir = pkg.getSourceDir();
                String fieldName = signature.contains("publicSourceDir") ? "publicSourceDir" : "sourceDir";
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "ApplicationInfo." + fieldName,
                        "package=" + pkg.getPackageName()
                                + ",flags=0x" + Integer.toHexString(marker.flags)
                                + ",value=" + String.valueOf(sourceDir),
                        "json-config", "读取配置的应用源路径");
                if (sourceDir == null) {
                    return AndroidPackageObjectFieldResult.of(null);
                }
                return AndroidPackageObjectFieldResult.of(new StringObject(vm, sourceDir));
            }
            if ("android/content/pm/ApplicationInfo->dataDir:Ljava/lang/String;".equals(signature)) {
                if (!pkg.isDataDirConfigured()) {
                    return AndroidPackageObjectFieldResult.notHandled();
                }
                String dataDir = pkg.getDataDir();
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "ApplicationInfo.dataDir",
                        "package=" + pkg.getPackageName()
                                + ",flags=0x" + Integer.toHexString(marker.flags)
                                + ",value=" + String.valueOf(dataDir),
                        "json-config", "读取配置的应用数据目录");
                if (dataDir == null) {
                    return AndroidPackageObjectFieldResult.of(null);
                }
                return AndroidPackageObjectFieldResult.of(new StringObject(vm, dataDir));
            }
            return AndroidPackageObjectFieldResult.notHandled();
        }
        return AndroidPackageObjectFieldResult.notHandled();
    }

    /**
     * PackageInfo.versionCode and ApplicationInfo.uid/flags for configured marker objects only.
     * PackageInfo.versionCode and ApplicationInfo.uid/flags require a same-VM live marker.
     * flags is projected from systemApp: true → 1 (FLAG_SYSTEM), false → 0.
     * Omitted / plain / foreign-VM → notHandled + UOE (no sidecar).
     */
    private static AndroidPackageIntFieldResult tryAndroidPackageIntField(BaseVM vm, DvmObject<?> dvmObject,
                                                                          String signature) {
        if (isLiveConfiguredPackageInfo(vm, dvmObject)) {
            if (!"android/content/pm/PackageInfo->versionCode:I".equals(signature)) {
                return AndroidPackageIntFieldResult.notHandled();
            }
            ConfiguredPackageInfo marker = (ConfiguredPackageInfo) dvmObject.getValue();
            TraceEnvironmentConfig.PackageConfig pkg = marker.packageConfig;
            if (!pkg.isVersionCodeConfigured()) {
                return AndroidPackageIntFieldResult.notHandled();
            }
            int value = pkg.getVersionCode().intValue();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                    "PackageInfo.versionCode",
                    "package=" + pkg.getPackageName()
                            + ",flags=0x" + Integer.toHexString(marker.flags)
                            + ",value=" + value,
                    "json-config", "读取配置的版本号");
            return AndroidPackageIntFieldResult.of(value);
        }
        if (isLiveConfiguredApplicationInfo(vm, dvmObject)) {
            ConfiguredApplicationInfo marker = (ConfiguredApplicationInfo) dvmObject.getValue();
            TraceEnvironmentConfig.PackageConfig pkg = marker.packageConfig;
            if ("android/content/pm/ApplicationInfo->uid:I".equals(signature)) {
                if (!pkg.isUidConfigured()) {
                    return AndroidPackageIntFieldResult.notHandled();
                }
                int value = pkg.getUid().intValue();
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "ApplicationInfo.uid",
                        "package=" + pkg.getPackageName()
                                + ",flags=0x" + Integer.toHexString(marker.flags)
                                + ",value=" + value,
                        "json-config", "读取配置的应用 UID");
                return AndroidPackageIntFieldResult.of(value);
            }
            if ("android/content/pm/ApplicationInfo->flags:I".equals(signature)) {
                if (!pkg.isSystemAppConfigured()) {
                    return AndroidPackageIntFieldResult.notHandled();
                }
                // ApplicationInfo.FLAG_SYSTEM = 1
                int value = Boolean.TRUE.equals(pkg.getSystemApp()) ? 1 : 0;
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                        "ApplicationInfo.flags",
                        "package=" + pkg.getPackageName()
                                + ",systemApp=" + pkg.getSystemApp()
                                + ",value=" + value,
                        "json-config", "读取配置的应用 flags（由 systemApp 投影）");
                return AndroidPackageIntFieldResult.of(value);
            }
            return AndroidPackageIntFieldResult.notHandled();
        }
        return AndroidPackageIntFieldResult.notHandled();
    }

    /**
     * ApplicationInfo.enabled for same-VM live marker objects only when
     * {@link TraceEnvironmentConfig.PackageConfig#isEnabledConfigured()}.
     * Omitted / plain / foreign-VM → notHandled + UOE (no sidecar).
     */
    private static AndroidPackageBooleanFieldResult tryAndroidPackageBooleanField(BaseVM vm, DvmObject<?> dvmObject,
                                                                                  String signature) {
        if (!isLiveConfiguredApplicationInfo(vm, dvmObject)) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        if (!"android/content/pm/ApplicationInfo->enabled:Z".equals(signature)) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        ConfiguredApplicationInfo marker = (ConfiguredApplicationInfo) dvmObject.getValue();
        TraceEnvironmentConfig.PackageConfig pkg = marker.packageConfig;
        if (!pkg.isEnabledConfigured()) {
            return AndroidPackageBooleanFieldResult.notHandled();
        }
        boolean value = Boolean.TRUE.equals(pkg.getEnabled());
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                "ApplicationInfo.enabled",
                "package=" + pkg.getPackageName()
                        + ",flags=0x" + Integer.toHexString(marker.flags)
                        + ",value=" + value,
                "json-config", "读取配置的应用启用状态");
        return AndroidPackageBooleanFieldResult.of(value);
    }

    /**
     * PackageInfo.firstInstallTime / lastUpdateTime for same-VM live marker objects only when the
     * corresponding presence flag is true. Omitted / plain / foreign-VM → notHandled + UOE (no sidecar).
     */
    private static AndroidPackageLongFieldResult tryAndroidPackageLongField(BaseVM vm, DvmObject<?> dvmObject,
                                                                            String signature) {
        if (!isLiveConfiguredPackageInfo(vm, dvmObject)) {
            return AndroidPackageLongFieldResult.notHandled();
        }
        ConfiguredPackageInfo marker = (ConfiguredPackageInfo) dvmObject.getValue();
        TraceEnvironmentConfig.PackageConfig pkg = marker.packageConfig;
        if ("android/content/pm/PackageInfo->firstInstallTime:J".equals(signature)) {
            if (!pkg.isFirstInstallTimeMillisConfigured()) {
                return AndroidPackageLongFieldResult.notHandled();
            }
            long value = pkg.getFirstInstallTimeMillis().longValue();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                    "PackageInfo.firstInstallTime",
                    "package=" + pkg.getPackageName()
                            + ",flags=0x" + Integer.toHexString(marker.flags)
                            + ",value=" + value,
                    "json-config", "读取配置的首次安装时间");
            return AndroidPackageLongFieldResult.of(value);
        }
        if ("android/content/pm/PackageInfo->lastUpdateTime:J".equals(signature)) {
            if (!pkg.isLastUpdateTimeMillisConfigured()) {
                return AndroidPackageLongFieldResult.notHandled();
            }
            long value = pkg.getLastUpdateTimeMillis().longValue();
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_package",
                    "PackageInfo.lastUpdateTime",
                    "package=" + pkg.getPackageName()
                            + ",flags=0x" + Integer.toHexString(marker.flags)
                            + ",value=" + value,
                    "json-config", "读取配置的最后更新时间");
            return AndroidPackageLongFieldResult.of(value);
        }
        return AndroidPackageLongFieldResult.notHandled();
    }

    private static final class AndroidNetworkLinkObjectResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidNetworkLinkObjectResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidNetworkLinkObjectResult notHandled() {
            return new AndroidNetworkLinkObjectResult(false, null);
        }

        static AndroidNetworkLinkObjectResult of(DvmObject<?> value) {
            return new AndroidNetworkLinkObjectResult(true, value);
        }
    }

    private static final class AndroidNetworkLinkIntResult {
        final boolean handled;
        final int value;

        private AndroidNetworkLinkIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidNetworkLinkIntResult notHandled() {
            return new AndroidNetworkLinkIntResult(false, 0);
        }

        static AndroidNetworkLinkIntResult of(int value) {
            return new AndroidNetworkLinkIntResult(true, value);
        }
    }

    private static final class AndroidNetworkLinkBooleanResult {
        final boolean handled;
        final boolean value;

        private AndroidNetworkLinkBooleanResult(boolean handled, boolean value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidNetworkLinkBooleanResult notHandled() {
            return new AndroidNetworkLinkBooleanResult(false, false);
        }

        static AndroidNetworkLinkBooleanResult of(boolean value) {
            return new AndroidNetworkLinkBooleanResult(true, value);
        }
    }

    /**
     * ConnectivityManager/NetworkInfo/LinkProperties object APIs when network.links is configured.
     * getLinkProperties signature is matched without reading the Network argument.
     * getDnsServers returns ArrayList of InetAddress objects holding
     * {@link ConfiguredLinkDnsInetAddress} markers (owner VM, config instance, host).
     */
    private static AndroidNetworkLinkObjectResult tryAndroidNetworkLinkObject(BaseVM vm, DvmObject<?> dvmObject,
                                                                              String signature) {
        if ("android/net/ConnectivityManager->getActiveNetworkInfo()Landroid/net/NetworkInfo;"
                .equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isNetworkLinksConfigured()) {
                return AndroidNetworkLinkObjectResult.notHandled();
            }
            DvmObject<?> networkInfo = vm.resolveClass("android/net/NetworkInfo").newObject(null);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "ConnectivityManager.getActiveNetworkInfo", "NetworkInfo",
                    "json-config", "返回配置的 NetworkInfo 活动链路");
            return AndroidNetworkLinkObjectResult.of(networkInfo);
        }
        if ("android/net/ConnectivityManager->getActiveNetwork()Landroid/net/Network;".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isNetworkLinksConfigured()) {
                return AndroidNetworkLinkObjectResult.notHandled();
            }
            DvmObject<?> network = vm.resolveClass("android/net/Network").newObject(null);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "ConnectivityManager.getActiveNetwork", "Network",
                    "json-config", "返回配置的 Network 活动网络对象");
            return AndroidNetworkLinkObjectResult.of(network);
        }
        if ("android/net/ConnectivityManager->getLinkProperties(Landroid/net/Network;)Landroid/net/LinkProperties;"
                .equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isNetworkLinksConfigured()) {
                return AndroidNetworkLinkObjectResult.notHandled();
            }
            DvmObject<?> linkProperties = vm.resolveClass("android/net/LinkProperties").newObject(null);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "ConnectivityManager.getLinkProperties", "LinkProperties",
                    "json-config", "返回配置的 LinkProperties 链路属性");
            return AndroidNetworkLinkObjectResult.of(linkProperties);
        }
        if ("android/net/NetworkInfo->getTypeName()Ljava/lang/String;".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isLinkStringConfigured("typeName")) {
                return AndroidNetworkLinkObjectResult.notHandled();
            }
            String value = config.getLinkString("typeName");
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "NetworkInfo.getTypeName",
                    "key=typeName,value=" + String.valueOf(value),
                    "json-config", "读取链路类型名称");
            if (value == null) {
                return AndroidNetworkLinkObjectResult.of(null);
            }
            return AndroidNetworkLinkObjectResult.of(new StringObject(vm, value));
        }
        if ("android/net/LinkProperties->getInterfaceName()Ljava/lang/String;".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isLinkStringConfigured("interfaceName")) {
                return AndroidNetworkLinkObjectResult.notHandled();
            }
            String value = config.getLinkString("interfaceName");
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "LinkProperties.getInterfaceName",
                    "key=interfaceName,value=" + String.valueOf(value),
                    "json-config", "读取链路接口名");
            if (value == null) {
                return AndroidNetworkLinkObjectResult.of(null);
            }
            return AndroidNetworkLinkObjectResult.of(new StringObject(vm, value));
        }
        if ("android/net/LinkProperties->getPrivateDnsServerName()Ljava/lang/String;".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isLinkStringConfigured("privateDnsServerName")) {
                return AndroidNetworkLinkObjectResult.notHandled();
            }
            String value = config.getLinkString("privateDnsServerName");
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "LinkProperties.getPrivateDnsServerName",
                    "key=privateDnsServerName,value=" + String.valueOf(value),
                    "json-config", "读取私有 DNS 服务器名");
            if (value == null) {
                return AndroidNetworkLinkObjectResult.of(null);
            }
            return AndroidNetworkLinkObjectResult.of(new StringObject(vm, value));
        }
        if ("android/net/LinkProperties->getDomains()Ljava/lang/String;".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isLinkStringConfigured("domains")) {
                return AndroidNetworkLinkObjectResult.notHandled();
            }
            String value = config.getLinkString("domains");
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "LinkProperties.getDomains",
                    "key=domains,value=" + String.valueOf(value),
                    "json-config", "读取链路搜索域");
            if (value == null) {
                return AndroidNetworkLinkObjectResult.of(null);
            }
            return AndroidNetworkLinkObjectResult.of(new StringObject(vm, value));
        }
        if ("android/net/LinkProperties->getHttpProxy()Landroid/net/ProxyInfo;".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isLinkStringConfigured("proxyHost")) {
                return AndroidNetworkLinkObjectResult.notHandled();
            }
            String host = config.getLinkString("proxyHost");
            if (host == null) {
                TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                        "LinkProperties.getHttpProxy", "null",
                        "json-config", "读取 HTTP 代理（显式 null）");
                return AndroidNetworkLinkObjectResult.of(null);
            }
            DvmObject<?> proxyInfo = vm.resolveClass("android/net/ProxyInfo")
                    .newObject(new ConfiguredLinkProxy(vm, config, host));
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "LinkProperties.getHttpProxy",
                    "key=proxyHost,value=" + host,
                    "json-config", "返回配置的 HTTP 代理 ProxyInfo");
            return AndroidNetworkLinkObjectResult.of(proxyInfo);
        }
        if ("android/net/ProxyInfo->getHost()Ljava/lang/String;".equals(signature)) {
            if (!isLiveConfiguredLinkProxy(vm, dvmObject)) {
                return AndroidNetworkLinkObjectResult.notHandled();
            }
            String host = ((ConfiguredLinkProxy) dvmObject.getValue()).host;
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "ProxyInfo.getHost", host,
                    "json-config", "读取配置的 HTTP 代理主机");
            return AndroidNetworkLinkObjectResult.of(new StringObject(vm, host));
        }
        if ("android/net/LinkProperties->getDnsServers()Ljava/util/List;".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isLinkDnsServersConfigured()) {
                return AndroidNetworkLinkObjectResult.notHandled();
            }
            List<String> servers = config.getLinkDnsServers();
            List<DvmObject<?>> addresses = new ArrayList<DvmObject<?>>(servers.size());
            DvmClass inetAddressClass = vm.resolveClass("java/net/InetAddress");
            for (String ip : servers) {
                addresses.add(inetAddressClass.newObject(new ConfiguredLinkDnsInetAddress(vm, config, ip)));
            }
            ArrayListObject list = new ArrayListObject(vm, addresses);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "LinkProperties.getDnsServers",
                    "size=" + servers.size() + ",servers=" + servers,
                    "json-config", "读取链路 DNS 服务器列表");
            return AndroidNetworkLinkObjectResult.of(list);
        }
        if ("java/net/InetAddress->getHostAddress()Ljava/lang/String;".equals(signature)) {
            if (!isLiveConfiguredLinkDnsInetAddress(vm, dvmObject)) {
                return AndroidNetworkLinkObjectResult.notHandled();
            }
            String host = ((ConfiguredLinkDnsInetAddress) dvmObject.getValue()).host;
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "InetAddress.getHostAddress", host,
                    "json-config", "读取配置的 DNS InetAddress 主机地址");
            return AndroidNetworkLinkObjectResult.of(new StringObject(vm, host));
        }
        return AndroidNetworkLinkObjectResult.notHandled();
    }

    /**
     * Marker value for {@code InetAddress} objects created from {@code network.links.dnsServers}.
     * Binds creating {@link BaseVM}, the {@link TraceEnvironmentConfig} instance at creation,
     * and the configured IPv4 host. Only live markers are handled by {@code getHostAddress}
     * with kind {@code network_link} (network-interface address markers use
     * {@link ConfiguredNetworkInterfaceInetAddress}).
     */
    private static final class ConfiguredLinkDnsInetAddress {
        final BaseVM owner;
        final TraceEnvironmentConfig config;
        final String host;

        private ConfiguredLinkDnsInetAddress(BaseVM owner, TraceEnvironmentConfig config, String host) {
            this.owner = owner;
            this.config = config;
            this.host = host;
        }
    }

    /**
     * Live same-VM {@link ConfiguredLinkDnsInetAddress}: owner matches current {@code vm},
     * marker config is the same instance as {@link TraceEnvironmentConfig#get} on this
     * emulator, and that config still explicitly configures {@code network.links.dnsServers}.
     * Foreign / stale / plain InetAddress fail (notHandled → UOE, no sidecar).
     */
    private static boolean isLiveConfiguredLinkDnsInetAddress(BaseVM vm, DvmObject<?> object) {
        if (vm == null || object == null
                || !(object.getValue() instanceof ConfiguredLinkDnsInetAddress)) {
            return false;
        }
        ConfiguredLinkDnsInetAddress marker = (ConfiguredLinkDnsInetAddress) object.getValue();
        if (marker.owner != vm || marker.config == null) {
            return false;
        }
        TraceEnvironmentConfig current = TraceEnvironmentConfig.get(vm.getEmulator());
        return current != null
                && current == marker.config
                && current.isLinkDnsServersConfigured();
    }

    /**
     * Marker value for {@code ProxyInfo} objects created from {@code network.links.proxyHost}.
     * Binds creating {@link BaseVM}, the {@link TraceEnvironmentConfig} instance at creation,
     * and the configured host. Only live markers are handled by ProxyInfo getHost/getPort
     * with json-config source.
     */
    private static final class ConfiguredLinkProxy {
        final BaseVM owner;
        final TraceEnvironmentConfig config;
        final String host;

        private ConfiguredLinkProxy(BaseVM owner, TraceEnvironmentConfig config, String host) {
            this.owner = owner;
            this.config = config;
            this.host = host;
        }
    }

    /**
     * Live same-VM {@link ConfiguredLinkProxy}: owner matches current {@code vm},
     * marker config is the same instance as {@link TraceEnvironmentConfig#get} on this
     * emulator, and that config still explicitly configures {@code network.links.proxyHost}.
     * Foreign / stale / plain ProxyInfo fail (notHandled → UOE, no sidecar).
     */
    private static boolean isLiveConfiguredLinkProxy(BaseVM vm, DvmObject<?> object) {
        if (vm == null || object == null
                || !(object.getValue() instanceof ConfiguredLinkProxy)) {
            return false;
        }
        ConfiguredLinkProxy marker = (ConfiguredLinkProxy) object.getValue();
        if (marker.owner != vm || marker.config == null) {
            return false;
        }
        TraceEnvironmentConfig current = TraceEnvironmentConfig.get(vm.getEmulator());
        return current != null
                && current == marker.config
                && current.isLinkStringConfigured("proxyHost");
    }

    /**
     * NetworkInfo.getType, LinkProperties.getMtu, and ProxyInfo.getPort from network.links.
     */
    private static AndroidNetworkLinkIntResult tryAndroidNetworkLinkInt(BaseVM vm, DvmObject<?> dvmObject,
                                                                        String signature) {
        if ("android/net/NetworkInfo->getType()I".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isLinkIntConfigured("type")) {
                return AndroidNetworkLinkIntResult.notHandled();
            }
            int value = config.getLinkInt("type", 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "NetworkInfo.getType", String.valueOf(value),
                    "json-config", "读取链路类型");
            return AndroidNetworkLinkIntResult.of(value);
        }
        if ("android/net/LinkProperties->getMtu()I".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isLinkIntConfigured("mtu")) {
                return AndroidNetworkLinkIntResult.notHandled();
            }
            int value = config.getLinkInt("mtu", 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "LinkProperties.getMtu", String.valueOf(value),
                    "json-config", "读取链路 MTU");
            return AndroidNetworkLinkIntResult.of(value);
        }
        if ("android/net/ProxyInfo->getPort()I".equals(signature)) {
            if (!isLiveConfiguredLinkProxy(vm, dvmObject)) {
                return AndroidNetworkLinkIntResult.notHandled();
            }
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isLinkIntConfigured("proxyPort")) {
                return AndroidNetworkLinkIntResult.notHandled();
            }
            int value = config.getLinkInt("proxyPort", 0);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "ProxyInfo.getPort", String.valueOf(value),
                    "json-config", "读取配置的 HTTP 代理端口");
            return AndroidNetworkLinkIntResult.of(value);
        }
        return AndroidNetworkLinkIntResult.notHandled();
    }

    /**
     * NetworkInfo.isConnected and LinkProperties.isPrivateDnsActive from network.links.
     */
    private static AndroidNetworkLinkBooleanResult tryAndroidNetworkLinkBoolean(BaseVM vm, String signature) {
        if ("android/net/NetworkInfo->isConnected()Z".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isLinkBooleanConfigured("connected")) {
                return AndroidNetworkLinkBooleanResult.notHandled();
            }
            boolean value = config.getLinkBoolean("connected", false);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "NetworkInfo.isConnected", String.valueOf(value),
                    "json-config", "读取链路连接状态");
            return AndroidNetworkLinkBooleanResult.of(value);
        }
        if ("android/net/LinkProperties->isPrivateDnsActive()Z".equals(signature)) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
            if (config == null || !config.isLinkBooleanConfigured("privateDnsActive")) {
                return AndroidNetworkLinkBooleanResult.notHandled();
            }
            boolean value = config.getLinkBoolean("privateDnsActive", false);
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "network_link",
                    "LinkProperties.isPrivateDnsActive", String.valueOf(value),
                    "json-config", "读取私有 DNS 启用状态");
            return AndroidNetworkLinkBooleanResult.of(value);
        }
        return AndroidNetworkLinkBooleanResult.notHandled();
    }

    /**
     * Static fields {@code Build.SUPPORTED_ABIS} / {@code Build.SUPPORTED_32_BIT_ABIS} /
     * {@code Build.SUPPORTED_64_BIT_ABIS} when the corresponding {@code android.build.*} array keys
     * are configured. Exact signatures only; each returns a fresh {@link ArrayObject} of
     * {@link StringObject} in config order. Fields are independent (no cross-inference).
     * Unconfigured returns null so the caller keeps UOE (no event).
     */
    private static DvmObject<?> tryAndroidBuildSupportedAbis(BaseVM vm, String signature) {
        final String api;
        final String note;
        final List<String> abis;
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null) {
            return null;
        }
        if ("android/os/Build->SUPPORTED_ABIS:[Ljava/lang/String;".equals(signature)) {
            if (!config.isAndroidBuildSupportedAbisConfigured()) {
                return null;
            }
            abis = config.getAndroidBuildSupportedAbis();
            api = "Build.SUPPORTED_ABIS";
            note = "读取配置的 Build.SUPPORTED_ABIS 列表";
        } else if ("android/os/Build->SUPPORTED_32_BIT_ABIS:[Ljava/lang/String;".equals(signature)) {
            if (!config.isAndroidBuildSupported32BitAbisConfigured()) {
                return null;
            }
            abis = config.getAndroidBuildSupported32BitAbis();
            api = "Build.SUPPORTED_32_BIT_ABIS";
            note = "读取配置的 Build.SUPPORTED_32_BIT_ABIS 列表";
        } else if ("android/os/Build->SUPPORTED_64_BIT_ABIS:[Ljava/lang/String;".equals(signature)) {
            if (!config.isAndroidBuildSupported64BitAbisConfigured()) {
                return null;
            }
            abis = config.getAndroidBuildSupported64BitAbis();
            api = "Build.SUPPORTED_64_BIT_ABIS";
            note = "读取配置的 Build.SUPPORTED_64_BIT_ABIS 列表";
        } else {
            return null;
        }
        if (abis == null) {
            return null;
        }
        DvmObject<?>[] elements = new DvmObject<?>[abis.size()];
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < abis.size(); i++) {
            String abi = abis.get(i);
            elements[i] = new StringObject(vm, abi);
            if (i > 0) {
                names.append(',');
            }
            names.append(abi);
        }
        ArrayObject array = new ArrayObject(elements);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_build",
                api,
                "count=" + abis.size() + ",abis=" + names,
                "json-config", note);
        return array;
    }

    /**
     * Static no-arg {@code Build.getSerial()} when {@code android.build.SERIAL} is available via the
     * existing scalar string accessor. Exact signature only; does not read {@code ro.serialno} or
     * simulate permission/SecurityException. Missing / unavailable scalar → null (caller UOE, no event).
     * Does not change static field {@code Build.SERIAL} behavior.
     */
    private static DvmObject<?> tryAndroidBuildGetSerial(BaseVM vm, String signature) {
        if (!"android/os/Build->getSerial()Ljava/lang/String;".equals(signature)) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null) {
            return null;
        }
        String serial = config.getAndroidBuildString("SERIAL");
        if (serial == null) {
            return null;
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_build",
                "Build.getSerial",
                "serial=" + serial,
                "json-config", "读取配置的 Build.SERIAL 序列号");
        return new StringObject(vm, serial);
    }

    /**
     * Static no-arg {@code Build.getRadioVersion()} when {@code android.build.RADIO} exists as a
     * JSON String via the existing scalar accessor. Exact signature only; returns the configured
     * text unchanged. Does not read {@code android.properties} {@code ro.*} keys and does not
     * simulate permission or modem. Missing / non-String → null (caller UOE, no event).
     * Does not change static field {@code Build.RADIO} or {@code Build.getSerial()} behavior.
     */
    private static DvmObject<?> tryAndroidBuildGetRadioVersion(BaseVM vm, String signature) {
        if (!"android/os/Build->getRadioVersion()Ljava/lang/String;".equals(signature)) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null) {
            return null;
        }
        String radio = config.getAndroidBuildStringIfString("RADIO");
        if (radio == null) {
            return null;
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_build",
                "Build.getRadioVersion",
                "radio=" + radio,
                "json-config", "读取配置的 Build.RADIO 基带版本");
        return new StringObject(vm, radio);
    }

    private static final class AndroidRuntimeSystemPropertyResult {
        final boolean handled;
        final DvmObject<?> value;

        private AndroidRuntimeSystemPropertyResult(boolean handled, DvmObject<?> value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidRuntimeSystemPropertyResult notHandled() {
            return new AndroidRuntimeSystemPropertyResult(false, null);
        }

        static AndroidRuntimeSystemPropertyResult of(DvmObject<?> value) {
            return new AndroidRuntimeSystemPropertyResult(true, value);
        }
    }

    private static final class AndroidRuntimeIntResult {
        final boolean handled;
        final int value;

        private AndroidRuntimeIntResult(boolean handled, int value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidRuntimeIntResult notHandled() {
            return new AndroidRuntimeIntResult(false, 0);
        }

        static AndroidRuntimeIntResult of(int value) {
            return new AndroidRuntimeIntResult(true, value);
        }
    }

    private static final class AndroidRuntimeLongResult {
        final boolean handled;
        final long value;

        private AndroidRuntimeLongResult(boolean handled, long value) {
            this.handled = handled;
            this.value = value;
        }

        static AndroidRuntimeLongResult notHandled() {
            return new AndroidRuntimeLongResult(false, 0L);
        }

        static AndroidRuntimeLongResult of(long value) {
            return new AndroidRuntimeLongResult(true, value);
        }
    }

    /**
     * VM 持有的 {@code java.lang.Runtime} marker：由已配置的 {@code availableProcessors}
     * 和/或 {@code maxMemoryBytes} 和/或 {@code totalMemoryBytes} 创建。绑定创建 VM 与 env
     * 配置实例。无宿主 {@link java.lang.Runtime} 载荷。
     */
    private static final class ConfiguredRuntime {
        final BaseVM owner;
        final TraceEnvironmentConfig config;

        private ConfiguredRuntime(BaseVM owner, TraceEnvironmentConfig config) {
            this.owner = owner;
            this.config = config;
        }
    }

    /**
     * 同 VM 存活的 {@link ConfiguredRuntime}：owner 匹配，且当前 env 配置实例仍为 marker
     * 绑定的同一实例。不要求 {@code availableProcessors} / {@code maxMemoryBytes} /
     * {@code totalMemoryBytes}；各 API 自行检查对应字段。
     */
    private static ConfiguredRuntime liveConfiguredRuntime(BaseVM vm, DvmObject<?> dvmObject) {
        if (vm == null || dvmObject == null
                || !(dvmObject.getValue() instanceof ConfiguredRuntime)) {
            return null;
        }
        ConfiguredRuntime marker = (ConfiguredRuntime) dvmObject.getValue();
        if (marker.owner != vm || marker.config == null) {
            return null;
        }
        TraceEnvironmentConfig current = TraceEnvironmentConfig.get(vm.getEmulator());
        if (current == null || current != marker.config) {
            return null;
        }
        return marker;
    }

    /**
     * 静态 {@code Runtime.getRuntime()Ljava/lang/Runtime;}：当 {@code availableProcessors}、
     * {@code maxMemoryBytes} 或 {@code totalMemoryBytes} 任一已显式配置时返回新的 VM 持有
     * marker（无宿主 Runtime）。三者都缺失 / 错误签名 → notHandled（UOE，无事件）。
     * getRuntime 不发 sidecar。
     */
    private static AndroidRuntimeSystemPropertyResult tryAndroidRuntimeGetRuntime(
            BaseVM vm, String signature) {
        if (!"java/lang/Runtime->getRuntime()Ljava/lang/Runtime;".equals(signature)) {
            return AndroidRuntimeSystemPropertyResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null
                || (!config.isAndroidRuntimeAvailableProcessorsConfigured()
                && !config.isAndroidRuntimeMaxMemoryBytesConfigured()
                && !config.isAndroidRuntimeTotalMemoryBytesConfigured())) {
            return AndroidRuntimeSystemPropertyResult.notHandled();
        }
        return AndroidRuntimeSystemPropertyResult.of(
                vm.resolveClass("java/lang/Runtime")
                        .newObject(new ConfiguredRuntime(vm, config)));
    }

    /**
     * Instance {@code Runtime.availableProcessors()I} on live {@link ConfiguredRuntime} only
     * when {@code availableProcessors} is configured. Returns configured exact int; summary-only
     * sidecar {@code field=availableProcessors,result=<n>} (no host leakage). Cross-VM / stale /
     * non-marker / missing field → notHandled (UOE, no event). Independent of {@code linux.cpu};
     * no other Runtime APIs.
     */
    private static AndroidRuntimeIntResult tryAndroidRuntimeAvailableProcessors(
            BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (!"java/lang/Runtime->availableProcessors()I".equals(signature)) {
            return AndroidRuntimeIntResult.notHandled();
        }
        ConfiguredRuntime marker = liveConfiguredRuntime(vm, dvmObject);
        if (marker == null || !marker.config.isAndroidRuntimeAvailableProcessorsConfigured()) {
            return AndroidRuntimeIntResult.notHandled();
        }
        int n = marker.config.getAndroidRuntimeAvailableProcessors();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_runtime",
                "Runtime.availableProcessors",
                "field=availableProcessors,result=" + n,
                "json-config", "读取配置的 availableProcessors");
        return AndroidRuntimeIntResult.of(n);
    }

    /**
     * 实例 {@code Runtime.maxMemory()J}：仅同 VM 存活 {@link ConfiguredRuntime} 且
     * {@code maxMemoryBytes} 已配置。返回配置精确 long；摘要 sidecar
     * {@code field=maxMemoryBytes,result=<n>}（不记录宿主内存）。
     * 跨 VM / 陈旧 / 非 marker / 缺字段 / 其它 Runtime long API → notHandled（UOE，无事件）。
     */
    private static AndroidRuntimeLongResult tryAndroidRuntimeMaxMemory(
            BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (!"java/lang/Runtime->maxMemory()J".equals(signature)) {
            return AndroidRuntimeLongResult.notHandled();
        }
        ConfiguredRuntime marker = liveConfiguredRuntime(vm, dvmObject);
        if (marker == null || !marker.config.isAndroidRuntimeMaxMemoryBytesConfigured()) {
            return AndroidRuntimeLongResult.notHandled();
        }
        long n = marker.config.getAndroidRuntimeMaxMemoryBytes();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_runtime",
                "Runtime.maxMemory",
                "field=maxMemoryBytes,result=" + n,
                "json-config", "读取配置的 maxMemoryBytes");
        return AndroidRuntimeLongResult.of(n);
    }

    /**
     * 实例 {@code Runtime.totalMemory()J}：仅同 VM 存活 {@link ConfiguredRuntime} 且
     * {@code totalMemoryBytes} 已配置。返回配置精确 long；摘要 sidecar
     * {@code field=totalMemoryBytes,result=<n>}（不记录宿主内存）。
     * 跨 VM / 陈旧 / 非 marker / 缺字段 / 其它 Runtime long API → notHandled（UOE，无事件）。
     */
    private static AndroidRuntimeLongResult tryAndroidRuntimeTotalMemory(
            BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (!"java/lang/Runtime->totalMemory()J".equals(signature)) {
            return AndroidRuntimeLongResult.notHandled();
        }
        ConfiguredRuntime marker = liveConfiguredRuntime(vm, dvmObject);
        if (marker == null || !marker.config.isAndroidRuntimeTotalMemoryBytesConfigured()) {
            return AndroidRuntimeLongResult.notHandled();
        }
        long n = marker.config.getAndroidRuntimeTotalMemoryBytes();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_runtime",
                "Runtime.totalMemory",
                "field=totalMemoryBytes,result=" + n,
                "json-config", "读取配置的 totalMemoryBytes");
        return AndroidRuntimeLongResult.of(n);
    }

    /**
     * 实例 {@code Runtime.freeMemory()J}：仅同 VM 存活 {@link ConfiguredRuntime} 且
     * {@code freeMemoryBytes} 已配置。返回配置精确 long；摘要 sidecar
     * {@code field=freeMemoryBytes,result=<n>}（不记录宿主内存）。
     * 跨 VM / 陈旧 / 非 marker / 缺字段 / 其它 Runtime long API → notHandled（UOE，无事件）。
     */
    private static AndroidRuntimeLongResult tryAndroidRuntimeFreeMemory(
            BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (!"java/lang/Runtime->freeMemory()J".equals(signature)) {
            return AndroidRuntimeLongResult.notHandled();
        }
        ConfiguredRuntime marker = liveConfiguredRuntime(vm, dvmObject);
        if (marker == null || !marker.config.isAndroidRuntimeFreeMemoryBytesConfigured()) {
            return AndroidRuntimeLongResult.notHandled();
        }
        long n = marker.config.getAndroidRuntimeFreeMemoryBytes();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_runtime",
                "Runtime.freeMemory",
                "field=freeMemoryBytes,result=" + n,
                "json-config", "读取配置的 freeMemoryBytes");
        return AndroidRuntimeLongResult.of(n);
    }

    /**
     * Static {@code System.getProperty} / {@code System.getProperties} when
     * {@code android.runtime.systemProperties} is configured.
     * Exact signatures only:
     * <ul>
     *   <li>{@code getProperty(Ljava/lang/String;)Ljava/lang/String;}</li>
     *   <li>{@code getProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;}</li>
     *   <li>{@code getProperties()Ljava/util/Properties;}</li>
     * </ul>
     * No host JVM fallback; independent of environmentVariables and linux.environ.
     * String form: only <strong>explicitly configured</strong> keys; non-null String → new
     * {@link StringObject}; configured JSON null → Java {@code null} (one-arg) or the supplied
     * default StringObject (two-arg).
     * Properties map form: fresh snapshot in configured JSON order containing only non-null
     * String entries (JSON null keys omitted); empty map when {@code {}}. Mutations are not
     * persisted across calls. Sidecar on success only: string form key/result; map form
     * {@code count} only (no keys/values). Missing node / wrong signature / string unconfigured
     * key → notHandled (UOE, no event). Does not implement setProperty / clearProperty / Runtime /
     * mutation persistence.
     */
    private static AndroidRuntimeSystemPropertyResult tryAndroidRuntimeSystemGetProperty(
            BaseVM vm, String signature, VarArg args) {
        final boolean oneArg =
                "java/lang/System->getProperty(Ljava/lang/String;)Ljava/lang/String;"
                        .equals(signature);
        final boolean twoArg =
                "java/lang/System->getProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
                        .equals(signature);
        final boolean mapForm =
                "java/lang/System->getProperties()Ljava/util/Properties;".equals(signature);
        if (!oneArg && !twoArg && !mapForm) {
            return AndroidRuntimeSystemPropertyResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidRuntimeSystemPropertiesConfigured()) {
            return AndroidRuntimeSystemPropertyResult.notHandled();
        }
        if (mapForm) {
            Map<String, String> configured = config.getAndroidRuntimeSystemProperties();
            // Fresh LinkedHashMap snapshot; skip explicit JSON-null entries (map values non-null).
            Map<String, String> snapshot = new LinkedHashMap<String, String>();
            for (Map.Entry<String, String> e : configured.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    snapshot.put(e.getKey(), e.getValue());
                }
            }
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_runtime",
                    "System.getProperties",
                    "count=" + snapshot.size(),
                    "json-config", "读取配置的 System 属性映射");
            // Properties-like Dvm object; Map size/get use host LinkedHashMap value (JSON order).
            return AndroidRuntimeSystemPropertyResult.of(
                    vm.resolveClass("java/util/Properties",
                            vm.resolveClass("java/util/Hashtable",
                                    vm.resolveClass("java/util/Map")))
                            .newObject(snapshot));
        }
        DvmObject<?> keyArg = args.getObjectArg(0);
        if (!(keyArg instanceof StringObject)) {
            return AndroidRuntimeSystemPropertyResult.notHandled();
        }
        String key = ((StringObject) keyArg).getValue();
        if (key == null || !config.isAndroidRuntimeSystemPropertyConfigured(key)) {
            // missing / unconfigured key: UOE path, no event (no host fallback)
            return AndroidRuntimeSystemPropertyResult.notHandled();
        }
        String configured = config.getAndroidRuntimeSystemProperty(key);
        final DvmObject<?> result;
        if (configured != null) {
            result = new StringObject(vm, configured);
        } else if (oneArg) {
            // configured explicit null → Java null
            result = null;
        } else {
            // two-arg + configured null → supplied default (StringObject or null ref only)
            DvmObject<?> defaultArg = args.getObjectArg(1);
            if (defaultArg == null) {
                result = null;
            } else if (!(defaultArg instanceof StringObject)) {
                return AndroidRuntimeSystemPropertyResult.notHandled();
            } else {
                String def = ((StringObject) defaultArg).getValue();
                result = def == null ? null : new StringObject(vm, def);
            }
        }
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_runtime",
                "System.getProperty",
                "key=" + key + ",result=" + configured,
                "json-config", "读取配置的 System 属性 " + key);
        return AndroidRuntimeSystemPropertyResult.of(result);
    }

    /**
     * Static {@code System.getenv} when {@code android.runtime.environmentVariables} is
     * configured. Exact signatures only:
     * <ul>
     *   <li>{@code getenv(Ljava/lang/String;)Ljava/lang/String;}</li>
     *   <li>{@code getenv()Ljava/util/Map;}</li>
     * </ul>
     * No host env fallback; independent of systemProperties and linux.environ.
     * String form: only explicitly configured keys; non-null String → new
     * {@link StringObject}; configured JSON null → Java {@code null}.
     * Map form: fresh snapshot in configured JSON order containing only non-null String
     * entries (JSON null keys omitted); empty map when {@code {}}. Mutations are not
     * persisted across calls. Sidecar on success only: string form key/result; map form
     * {@code count} only (no keys/values). Missing node / wrong signature / string unconfigured
     * key → notHandled (UOE, no event). Does not implement setenv / Runtime / mutation
     * persistence.
     */
    private static AndroidRuntimeSystemPropertyResult tryAndroidRuntimeSystemGetenv(
            BaseVM vm, String signature, VarArg args) {
        final boolean oneArg =
                "java/lang/System->getenv(Ljava/lang/String;)Ljava/lang/String;"
                        .equals(signature);
        final boolean mapForm =
                "java/lang/System->getenv()Ljava/util/Map;".equals(signature);
        if (!oneArg && !mapForm) {
            return AndroidRuntimeSystemPropertyResult.notHandled();
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidRuntimeEnvironmentVariablesConfigured()) {
            return AndroidRuntimeSystemPropertyResult.notHandled();
        }
        if (mapForm) {
            Map<String, String> configured = config.getAndroidRuntimeEnvironmentVariables();
            // Fresh LinkedHashMap snapshot; skip explicit JSON-null entries (map values non-null).
            Map<String, String> snapshot = new LinkedHashMap<String, String>();
            for (Map.Entry<String, String> e : configured.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    snapshot.put(e.getKey(), e.getValue());
                }
            }
            TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_runtime",
                    "System.getenv",
                    "count=" + snapshot.size(),
                    "json-config", "读取配置的环境变量映射");
            // HashMap/Map Dvm object; Map.get/size use host value
            return AndroidRuntimeSystemPropertyResult.of(
                    vm.resolveClass("java/util/HashMap", vm.resolveClass("java/util/Map"))
                            .newObject(snapshot));
        }
        DvmObject<?> keyArg = args.getObjectArg(0);
        if (!(keyArg instanceof StringObject)) {
            return AndroidRuntimeSystemPropertyResult.notHandled();
        }
        String key = ((StringObject) keyArg).getValue();
        if (key == null || !config.isAndroidRuntimeEnvironmentVariableConfigured(key)) {
            return AndroidRuntimeSystemPropertyResult.notHandled();
        }
        String configured = config.getAndroidRuntimeEnvironmentVariable(key);
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_runtime",
                "System.getenv",
                "key=" + key + ",result=" + configured,
                "json-config", "读取配置的环境变量 " + key);
        if (configured == null) {
            return AndroidRuntimeSystemPropertyResult.of(null);
        }
        return AndroidRuntimeSystemPropertyResult.of(new StringObject(vm, configured));
    }

    /**
     * Exact static {@code Build.TIME:J} when {@code android.build.TIME} is configured.
     * Missing / wrong signature → null (caller UOE, no event). Not available via String field path.
     */
    private static Long tryAndroidBuildTime(BaseVM vm, String signature) {
        if (!"android/os/Build->TIME:J".equals(signature)) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        if (config == null || !config.isAndroidBuildTimeConfigured()) {
            return null;
        }
        Long time = config.getAndroidBuildTime();
        if (time == null) {
            return null;
        }
        long value = time.longValue();
        TraceEnvironmentEventSink.emit(vm.getEmulator(), "android_build",
                "Build.TIME",
                "time=" + value,
                "json-config", "读取配置的 Build.TIME 构建时间戳");
        return time;
    }

    private String getAndroidBuildString(BaseVM vm, String signature) {
        if (!signature.endsWith(":Ljava/lang/String;")) {
            return null;
        }
        String key = getAndroidBuildKey(signature, ":Ljava/lang/String;");
        if (key == null) {
            return null;
        }
        // Build.TIME is long-only; wrong object signature must not coerce via String path.
        if ("TIME".equals(key)) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        return config == null ? null : config.getAndroidBuildString(key);
    }

    private Integer getAndroidBuildInt(BaseVM vm, String signature) {
        if (!signature.endsWith(":I")) {
            return null;
        }
        String key = getAndroidBuildKey(signature, ":I");
        if (key == null) {
            return null;
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        return config == null ? null : config.getAndroidBuildInt(key);
    }

    private String getAndroidBuildKey(String signature, String suffix) {
        final String buildPrefix = "android/os/Build->";
        final String versionPrefix = "android/os/Build$VERSION->";
        if (signature.startsWith(buildPrefix) && signature.endsWith(suffix)) {
            return signature.substring(buildPrefix.length(), signature.length() - suffix.length());
        }
        if (signature.startsWith(versionPrefix) && signature.endsWith(suffix)) {
            return "VERSION." + signature.substring(versionPrefix.length(), signature.length() - suffix.length());
        }
        return null;
    }

    private long getConfiguredCurrentTimeMillis(BaseVM vm) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(vm.getEmulator());
        Long configured = config == null ? null : config.getCurrentTimeMillis();
        return configured == null ? System.currentTimeMillis() : configured;
    }

    @Override
    public DvmObject<?> toReflectedMethod(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod) {
        return toReflectedMethod(vm, dvmClass, dvmMethod.getSignature());
    }

    @Override
    public DvmObject<?> toReflectedMethod(BaseVM vm, DvmClass dvmClass, String signature) {
        log.info("toReflectedMethod [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public boolean acceptMethod(DvmClass dvmClass, String signature, boolean isStatic) {
        return true;
    }

    @Override
    public boolean acceptField(DvmClass dvmClass, String signature, boolean isStatic) {
        return true;
    }
}

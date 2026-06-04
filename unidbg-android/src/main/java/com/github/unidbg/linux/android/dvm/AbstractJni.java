package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.env.TraceEnvironmentConfig;
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
import java.lang.management.ManagementFactory;
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
        switch (signature) {
            case "android/os/Debug->isDebuggerConnected()Z":
                return false;
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
        switch (signature) {
            case "android/os/Debug->isDebuggerConnected()Z":
                return false;
            
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

    @Override
    public int callStaticIntMethod(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VarArg varArg) {
        return callStaticIntMethod(vm, dvmClass, dvmMethod.getSignature(), varArg);
    }


    @Override
    public int callStaticIntMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        log.info("callStaticIntMethod [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public int callStaticIntMethodV(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VaList vaList) {
        return callStaticIntMethodV(vm, dvmClass, dvmMethod.getSignature(), vaList);
    }

    @Override
    public int callStaticIntMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        log.info("callStaticIntMethodV [Unidbg]: {}", signature);
        switch (signature) {
            // 获取当前进程 PID
            case "android/os/Process->myPid()I": {
                // unidbg 已经替我们做了; 源码如下,
                // String name = ManagementFactory.getRuntimeMXBean().getName();
                // String pid = name.split("@")[0];
                // this.pid = Integer.parseInt(pid) & 0x7fff;
                // 我已经在对应生成位置日志插桩log.info，可以在idea的console中直接跳过去
                return vm.getEmulator().getPid();
            }
        }
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public long callLongMethod(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VarArg varArg) {
        return callLongMethod(vm, dvmObject, dvmMethod.getSignature(), varArg);
    }

    @Override
    public long callLongMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        log.info("callLongMethod [Unidbg]: {}", signature);
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
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public DvmObject<?> callObjectMethodV(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VaList vaList) {
        return callObjectMethodV(vm, dvmObject, dvmMethod.getSignature(), vaList);
    }

    @Override
    public DvmObject<?> callObjectMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        log.info("callObjectMethodV [Unidbg]: {}", signature);
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
            case "android/app/Application->getSystemService(Ljava/lang/String;)Ljava/lang/Object;": {
                StringObject serviceName = vaList.getObjectArg(0);
                assert serviceName != null;
                return new SystemService(vm, serviceName.getValue());
            }
            
            // ==================== Java String 方法 ====================
            // String.toString(): 返回自身，Java 规范要求
            case "java/lang/String->toString()Ljava/lang/String;":
                return dvmObject;
            
            // Class.getName(): 返回类的全限定名，如 "java.lang.String"
            case "java/lang/Class->getName()Ljava/lang/String;":
                return new StringObject(vm, ((DvmClass) dvmObject).getName());
            
            // ==================== Android 无障碍服务 ====================
            // 获取已启用的无障碍服务列表, 这个一般是为了检测的，这里直接给个空代表我们没开启
            case "android/view/accessibility/AccessibilityManager->getEnabledAccessibilityServiceList(I)Ljava/util/List;":
                return new ArrayListObject(vm, Collections.emptyList());
            
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
                return vm.resolveClass("android/content/res/Configuration").newObject(null);
            case "android/view/WindowManager->getDefaultDisplay()Landroid/view/Display;":
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
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public long callStaticLongMethodV(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VaList vaList) {
        return callStaticLongMethodV(vm, dvmClass, dvmMethod.getSignature(), vaList);
    }


    @Override
    public long callStaticLongMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        log.info("callStaticLongMethodV [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public boolean callBooleanMethod(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VarArg varArg) {
        return callBooleanMethod(vm, dvmObject, dvmMethod.getSignature(), varArg);
    }

    @Override
    public boolean callBooleanMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        log.info("callBooleanMethod [Unidbg]: {}", signature);
        switch (signature) {
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
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public float getFloatField(BaseVM vm, DvmObject<?> dvmObject, DvmField dvmField) {
        return getFloatField(vm, dvmObject, dvmField.getSignature());
    }

    @Override
    public float getFloatField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        log.info("getFloatField [Unidbg]: {}", signature);
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public float callStaticFloatMethod(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VarArg varArg) {
        return callStaticFloatMethod(vm, dvmClass, dvmMethod.getSignature(), varArg);
    }

    @Override
    public float callStaticFloatMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        log.info("callStaticFloatMethod [Unidbg]: {}", signature);
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
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void callStaticVoidMethodV(BaseVM vm, DvmClass dvmClass, DvmMethod dvmMethod, VaList vaList) {
        callStaticVoidMethodV(vm, dvmClass, dvmMethod.getSignature(), vaList);
    }

    @Override
    public void callStaticVoidMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        log.info("callStaticVoidMethodV [Unidbg]: {}", signature);
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
        switch (signature) {
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
        throw new UnsupportedOperationException(signature);
    }

    @Override
    public void callVoidMethod(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VarArg varArg) {
        callVoidMethod(vm, dvmObject, dvmMethod.getSignature(), varArg);
    }

    @Override
    public void callVoidMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        log.info("callVoidMethod [Unidbg]: {}", signature);
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
        throw new UnsupportedOperationException(signature);
    }

    private String getAndroidBuildString(BaseVM vm, String signature) {
        if (!signature.endsWith(":Ljava/lang/String;")) {
            return null;
        }
        String key = getAndroidBuildKey(signature, ":Ljava/lang/String;");
        if (key == null) {
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

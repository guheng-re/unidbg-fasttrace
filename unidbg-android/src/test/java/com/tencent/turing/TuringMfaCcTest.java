package com.tencent.turing;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.EmulatorBuilder;
import com.github.unidbg.Module;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.ElfLibraryFile;
import com.github.unidbg.linux.android.dvm.AbstractJni;
import com.github.unidbg.linux.android.dvm.BaseVM;
import com.github.unidbg.linux.android.dvm.DalvikModule;
import com.github.unidbg.linux.android.dvm.DvmClass;
import com.github.unidbg.linux.android.dvm.DvmMethod;
import com.github.unidbg.linux.android.dvm.DvmObject;
import com.github.unidbg.linux.android.dvm.StringObject;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.linux.android.dvm.VaList;
import com.github.unidbg.linux.android.dvm.VarArg;
import com.github.unidbg.linux.android.dvm.api.PackageInfo;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.linux.android.dvm.jni.ProxyDvmObject;
import com.github.unidbg.linux.android.dvm.wrapper.DvmBoolean;
import com.github.unidbg.linux.android.dvm.wrapper.DvmInteger;
import com.github.unidbg.linux.android.dvm.wrapper.DvmLong;
import com.github.unidbg.memory.Memory;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * unidbg script 1/2 — libturingmfa_cc.so only.
 *
 * Java collectors (GOzix / kC0XR / A48DB) fill the cfg HashMap the same way
 * the APK does, then this loads libturingmfa_cc.so and calls:
 *   JNI_OnLoad → e209302 (8119) → a209302 (8117 tdid) → m209302 (front)
 *
 * Huiyan startLiveness packing lives in script 2:
 *   {@code com.tencent.huiyan.HuiYanAndroidSdkTest}
 */
public class TuringMfaCcTest extends AbstractJni {

    private static final File SO = new File(
            "D:\\project\\12306\\12306-5.9.6.6\\12306v5.9.6.6\\lib\\arm64-v8a\\libturingmfa_cc.so");
    private static final File PROFILE = new File(
            "D:\\project\\unidbg-fasttrace\\profiles\\pixel6-oriole-13\\device-fingerprint.json");
    private static final String PACKAGE = "com.MobileTicket";
    private static final String TNATIVE = "com/tencent/turingface/sdk/mfa_cc/TNative$aa";
    /** Pixel 6 (oriole) 12306 uid / install time from pixel6-oriole-13 packages. */
    private static final int APP_UID = 10273;
    /**
     * G1g37 Camera.Parameters blob (preview-format;preview-size;picture-size;fps;exposure).
     * Pixel 6 front/back typical sizes; commas become %54 in cfg 406.
     */
    private static final String PIXEL6_CAMERA_102 =
            "yuv420sp;yuv420p,yuv420sp;1920x1080;4080x3072,3840x2160,1920x1440,1920x1080,1440x1080,"
                    + "1280x960,1280x720,960x720,640x480,352x288,320x240,176x144;"
                    + "30000,30000;(15000,15000),(8000,30000),(30000,30000);20;-20";
    private static final int VERSION_CODE = 278;
    private static final long INSTALL_TIME_MS = 1787035407788L;
    private static final int APP_FLAGS = 0x4 | 0x40 | 0x100000;

    private AndroidEmulator emulator;
    private VM vm;
    private Module module;
    private DvmClass tNative;
    private final Map<String, Map<String, String>> prefsStore =
            new LinkedHashMap<String, Map<String, String>>();
    /** {@code Hc4Pk.a} — SDK init wall clock, used in cfg 210. */
    private long sdkInitMs;
    /** {@code aw2ka.c} skip set: hardcoded defaults plus 8119 field 4. */
    private Set<Integer> skipIds = new TreeSet<Integer>();

    private static final class PointValue {
        int x;
        int y;
    }

    public static void main(String[] args) throws Exception {
        new TuringMfaCcTest().run();
    }

    @Test
    public void testCallTuringMfa() throws Exception {
        run();
    }

    private void run() throws Exception {
        if (!SO.isFile()) {
            throw new IllegalStateException("missing so: " + SO.getAbsolutePath());
        }

        EmulatorBuilder<AndroidEmulator> builder = AndroidEmulatorBuilder.for64Bit()
                .setProcessName(PACKAGE);
        if (PROFILE.isFile()) {
            builder = builder.setEnvironmentProfile(PROFILE);
            System.out.println("profile " + PROFILE.getAbsolutePath());
        } else {
            throw new IllegalStateException("missing profile: " + PROFILE.getAbsolutePath());
        }
        if (!"false".equalsIgnoreCase(System.getProperty("turing.unicorn2", "true"))) {
            builder = builder.addBackendFactory(new Unicorn2Factory(true));
        }
        emulator = builder.build();
        emulator.getSyscallHandler().setEnableThreadDispatcher(true);

        Memory memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));

        vm = emulator.createDalvikVM();
        vm.setJni(this);
        vm.setVerbose(true);
        preRegisterJniClasses();

        // RegisterNatives looks these up by JNI name.
        tNative = vm.resolveClass(TNATIVE);
        vm.resolveClass("com/tencent/turingface/sdk/mfa_cc/TNative$aa$bb");

        System.out.println("loading " + SO.getAbsolutePath() + " size=" + SO.length());
        DalvikModule dm = vm.loadLibrary(SO, true);
        module = dm.getModule();
        System.out.println("so loaded base=0x" + Long.toHexString(module.base)
                + " size=0x" + Long.toHexString(module.size));

        dm.callJNI_OnLoad(emulator);
        System.out.println("JNI_OnLoad ok");
        mapNoLinkDecor();

        DvmObject<?> ctx = newContext();
        sdkInitMs = System.currentTimeMillis();
        callEnvInit(ctx);

        TuringPacketPacker packer = new TuringPacketPacker();
        String faceIdToken = propertyOr("huiyan.faceidToken", "");
        System.out.println("turing packer teaKey=" + packer.getTeaKey() + " guid=" + packer.getGuid()
                + " faceidToken=" + faceIdToken);

        Map<String, String> initCfg = baseCfg();
        byte[] initBody = callPack(
                "e209302_97A941171A843DDA(Landroid/util/SparseArray;Landroid/content/Context;Ljava/util/Map;I)Landroid/util/SparseArray;",
                "e209302/8119",
                "unidbg_8119_native.bin",
                ctx, initCfg);
        writeOut("unidbg_8119_plain.bin", initBody);
        logInitIdentity(initBody);
        TuringPacketPacker.TuringNewResponse initRsp = postCmd(packer, 8119, initBody, "unidbg_8119");
        if (initRsp.recordBody != null) {
            callUnpackF(initRsp.recordBody);
        }
        String skipCsv = buildSkipCsv(initRsp.recordBody);
        System.out.println("2001 skip=" + skipCsv);

        Map<String, String> tdidCfg = apkTdidCfg(skipCsv);
        byte[] tdidBody = callPack(
                "a209302_97A941171A843DDA(Landroid/util/SparseArray;Landroid/content/Context;Ljava/util/Map;Ljava/util/Map;I)Landroid/util/SparseArray;",
                "a209302/8117",
                "unidbg_8117_native.bin",
                ctx, tdidCfg, null, 0);
        writeOut("unidbg_8117_plain.bin", tdidBody);
        logDfpIdentity("8117", tdidBody);
        TuringPacketPacker.TuringNewResponse tdidRsp = postCmd(packer, 8117, tdidBody, "unidbg_8117");
        String tdid = "";
        Map<Integer, Object> tdidParsed = new LinkedHashMap<Integer, Object>();
        if (tdidRsp.recordBody != null) {
            tdidParsed = callUnpackB(tdidRsp.recordBody);
            Object tdidObj = unwrap(tdidParsed.get(101));
            if (tdidObj != null) {
                tdid = String.valueOf(tdidObj);
            }
        }
        if (tdid.isEmpty()) {
            tdid = tdidRsp.firstLongString(16);
        }
        persistTdidPrefs(tdid, tdidParsed);
        writeOut("unidbg_tdid.txt", tdid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        writeOut("unidbg_faceid_token.txt",
                faceIdToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("tdid=" + tdid);

        // APK G1g37.start(): t0bih.c=true skips 8123 HTTP, packs front with 3001=-10024.
        // Token/tdid go into cfg 406 as "100_<token>,101_<tdid>", plus 207=tdid.
        Map<String, String> frontCfg = apkStartFrontCfg(skipCsv, tdid, faceIdToken);
        byte[] mBody = new byte[0];
        try {
            mBody = callPackNoMode(
                    "m209302_97A941171A843DDA(Landroid/util/SparseArray;Landroid/content/Context;Ljava/util/Map;)Landroid/util/SparseArray;",
                    "m209302/front",
                    "unidbg_m209302.bin",
                    ctx, frontCfg);
        } catch (Exception e) {
            System.out.println("m209302 failed: " + e);
        }
        System.out.println("front-so " + TuringPacketPacker.frontBindPreview(mBody));
        System.out.println("front-so types " + TuringPacketPacker.frontTypeSketch(mBody));
        if (mBody.length == 0) {
            throw new IllegalStateException("m209302 produced empty front");
        }
        // APK s5pTT: native m209302 bytes → IyjbE.a(..., 1, null) → Base64 → HuiYan initDeviceRiskData.
        // Do not rewrite JCE types after native (no Go skeleton).
        writeOut("unidbg_m209302_go.bin", mBody);
        logDfpIdentity("front-so", mBody);
        String frontSecond = TuringPacketPacker.encryptFieldSecond(mBody);
        writeOut("unidbg_8123_front.txt", frontSecond.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        writeOut("unidbg_8123_risk.txt", frontSecond.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        writeOut("unidbg_turing_session.txt",
                (packer.getServerSession() == null ? "" : packer.getServerSession())
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        System.out.println("turing_new done sessionChars="
                + (packer.getServerSession() == null ? 0 : packer.getServerSession().length())
                + " tdidChars=" + tdid.length()
                + " frontSecond=" + frontSecond.length());
        emulator.close();
    }

    private TuringPacketPacker.TuringNewResponse postCmd(TuringPacketPacker packer, int cmd,
                                                         byte[] nativePlain, String prefix) {
        byte[] http = packer.wrapTuringNew(cmd, nativePlain);
        writeOut(prefix + ".bin", http);
        System.out.println("POST turing_new cmd=" + cmd + " http=" + http.length
                + " sessionChars=" + (packer.getServerSession() == null ? 0 : packer.getServerSession().length()));
        try {
            TuringPacketPacker.TuringNewResponse rsp = packer.postWrapped(http, cmd);
            writeOut(prefix + "_http_rsp.bin", rsp.raw == null ? new byte[0] : rsp.raw);
            if (rsp.innerPlain != null) {
                writeOut(prefix + "_rsp_inner.bin", rsp.innerPlain);
            }
            if (rsp.recordBody != null) {
                writeOut(prefix + "_rsp_body.bin", rsp.recordBody);
            }
            if (rsp.payloadString != null && !rsp.payloadString.isEmpty()) {
                writeOut(prefix + "_rsp_payload.txt",
                        rsp.payloadString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            System.out.println("turing_new " + cmd + " " + rsp);
            return rsp;
        } catch (Exception e) {
            System.out.println("turing_new POST " + cmd + " failed: " + e);
            TuringPacketPacker.TuringNewResponse empty = new TuringPacketPacker.TuringNewResponse();
            empty.error = String.valueOf(e);
            return empty;
        }
    }

    private Map<Integer, Object> callUnpackF(byte[] body) {
        Map<Integer, Object> sparse = new LinkedHashMap<Integer, Object>();
        DvmObject<?> sparseObj = newSparse(sparse);
        tNative.callStaticJniMethodObject(emulator,
                "f209302_97A941171A843DDA(Landroid/util/SparseArray;[BI)Landroid/util/SparseArray;",
                sparseObj, new ByteArray(vm, body), 0);
        dumpSparse("f209302/18119", sparse);
        return sparse;
    }

    private Map<Integer, Object> callUnpackB(byte[] body) {
        Map<Integer, Object> sparse = new LinkedHashMap<Integer, Object>();
        DvmObject<?> sparseObj = newSparse(sparse);
        DvmObject<?> cfgObj = newMap(new LinkedHashMap<String, String>());
        tNative.callStaticJniMethodObject(emulator,
                "b209302_97A941171A843DDA(Landroid/util/SparseArray;[BLjava/util/Map;I)Landroid/util/SparseArray;",
                sparseObj, new ByteArray(vm, body), cfgObj, 0);
        dumpSparse("b209302", sparse);
        return sparse;
    }

    private static String randomAlphaNum(int n) {
        java.security.SecureRandom r = new java.security.SecureRandom();
        char[] chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
        char[] out = new char[n];
        for (int i = 0; i < n; i++) {
            out[i] = chars[r.nextInt(chars.length)];
        }
        return new String(out);
    }

    /**
     * FindClass happens before our NewObject helpers. resolveClass is sticky, so
     * HashSet/HashMap/ArrayList must already have Set/Map/List as super so
     * interface method IDs (Set.add, Map.containsKey, ...) resolve on instances.
     */
    private void preRegisterJniClasses() {
        DvmClass collection = vm.resolveClass("java/util/Collection");
        DvmClass set = vm.resolveClass("java/util/Set", collection);
        DvmClass list = vm.resolveClass("java/util/List", collection);
        DvmClass map = vm.resolveClass("java/util/Map");
        vm.resolveClass("java/util/HashSet", set);
        vm.resolveClass("java/util/LinkedHashSet", set);
        vm.resolveClass("java/util/HashMap", map);
        vm.resolveClass("java/util/LinkedHashMap", map);
        vm.resolveClass("java/util/ArrayList", list);
        vm.resolveClass("java/util/Iterator");
        vm.resolveClass("java/util/Map$Entry");
        vm.resolveClass("android/util/SparseArray");
        vm.resolveClass("java/lang/Integer");
        vm.resolveClass("android/content/res/Resources");
        vm.resolveClass("com/android/internal/os/PowerProfile");
        vm.resolveClass("android/graphics/Point");
        vm.resolveClass("android/view/Display");
        vm.resolveClass("android/hardware/display/DisplayManager");
        vm.resolveClass("android/view/WindowManager");
        vm.resolveClass("android/util/DisplayMetrics");
        vm.resolveClass("android/media/RingtoneManager");
        vm.resolveClass("android/media/Ringtone");
        vm.resolveClass("android/net/Uri");
        vm.resolveClass("android/provider/Settings");
        vm.resolveClass("android/provider/Settings$System");
        vm.resolveClass("android/provider/Settings$Secure");
        vm.resolveClass("android/provider/Settings$Global");
        vm.resolveClass("android/content/ContentResolver");
        vm.resolveClass("android/content/SharedPreferences");
        vm.resolveClass("android/content/SharedPreferences$Editor");
        vm.resolveClass("android/content/pm/PackageManager");
        vm.resolveClass("android/content/pm/IPackageManager");
        vm.resolveClass("android/content/pm/IPackageManager$Stub");
        vm.resolveClass("android/os/IBinder");
        vm.resolveClass("android/os/IServiceManager");
        vm.resolveClass("android/os/ServiceManager");
        vm.resolveClass("android/os/ServiceManagerNative");
        vm.resolveClass("android/view/IWindowManager");
        vm.resolveClass("android/view/IWindowManager$Stub");
        vm.resolveClass("android/hardware/display/IDisplayManager");
        vm.resolveClass("android/hardware/display/IDisplayManager$Stub");
    }

    private DvmObject<?> newContext() {
        return vm.resolveClass(
                "android/app/Application",
                vm.resolveClass("android/content/ContextWrapper",
                        vm.resolveClass("android/content/Context"))).newObject(null);
    }

    private static Map<String, String> baseCfg() {
        Map<String, String> cfg = new LinkedHashMap<String, String>();
        cfg.put("1", "108098");
        cfg.put("101", "");
        cfg.put("207", "");
        return cfg;
    }

    /**
     * GOzix map for a209302/8117. Same keys the APK puts before the JNI call.
     */
    private Map<String, String> apkTdidCfg(String skipCsv) {
        Map<String, String> cfg = baseCfg();
        cfg.put("2001", skipCsv);
        cfg.put("2012", "300");
        if (!collectField(41)) {
            cfg.put("2003", "0");
        }
        if (!collectField(10002)) {
            cfg.put("2005", "0");
        }
        if (!collectField(10003)) {
            cfg.put("2006", "0");
        }
        cfg.put("210", gozix210());
        cfg.put("264", buildModel());
        fillGozixJavaCollectors(cfg);
        return cfg;
    }

    /**
     * G1g37.start() local path ({@code t0bih.c=true}) → kC0XR Java collectors → m209302.
     * Device values come from the same Android APIs / profile the APK would read.
     */
    private Map<String, String> apkStartFrontCfg(String skipCsv, String tdid, String faceIdToken) {
        Map<String, String> cfg = baseCfg();
        cfg.remove("101");
        cfg.put("2001", skipCsv);
        if (!collectField(41)) {
            cfg.put("2003", "0");
        } else {
            cfg.put("100", "");
        }
        if (!collectField(10002)) {
            cfg.put("2005", "0");
        }
        cfg.put("210", kc0xr210(false));
        if (tdid != null && !tdid.isEmpty()) {
            cfg.put("207", tdid);
            cfg.put("3005", "0");
        } else {
            cfg.put("3005", "1");
        }
        TraceEnvironmentConfig env = TraceEnvironmentConfig.get(emulator);
        if (collectField(102)) {
            cfg.put("252", settingInt("secure", "mock_location", 0) != 0 ? "1" : "0");
        }
        if (collectField(103)) {
            cfg.put("253", "0");
        }
        if (collectField(104)) {
            cfg.put("254", String.valueOf(batteryPlugState(env)));
        }
        if (collectField(105)) {
            cfg.put("255", String.valueOf(batteryPercent(env)));
        }
        cfg.put("264", buildModel());
        cfg.put("270", String.valueOf(debugFlags()));
        if (collectField(126)) {
            cfg.put("281", String.valueOf(System.currentTimeMillis() - elapsedRealtimeMs()));
        }
        if (collectField(138)) {
            String sim = simStates(env);
            if (sim != null) {
                cfg.put("282", sim);
            }
        }
        if (collectField(146)) {
            String net = a48dbActiveNetwork(env);
            if (net != null) {
                cfg.put("284", net);
            }
        }
        if (collectField(149)) {
            cfg.put("285", localeTag(env));
        }
        if (collectField(150)) {
            cfg.put("286", timezoneId(env));
        }
        if (collectField(151)) {
            String op = env == null ? null : env.getTelephonyString("simOperator");
            if (op != null && !op.isEmpty()) {
                cfg.put("287", op);
            }
        }
        cfg.put("308", String.valueOf(a48dbNetworkType(env)));
        cfg.put("2018", String.valueOf(APP_UID));
        fillKc0xrJavaCollectors(cfg);
        Map<Integer, String> extra = new LinkedHashMap<Integer, String>();
        if (faceIdToken != null && !faceIdToken.isEmpty()) {
            extra.put(100, faceIdToken);
        }
        if (tdid != null && !tdid.isEmpty()) {
            extra.put(101, tdid);
        }
        // G1g37.start: Camera.Parameters → extra 102. unidbg has no Camera; use Pixel 6 caps.
        extra.put(102, PIXEL6_CAMERA_102);
        String field406 = build406(extra);
        cfg.put("406", field406);
        cfg.put("3001", "-10024");
        cfg.put("3004", randomAlphaNum(32));
        // s5pTT: Constants.VIA_TO_TYPE_QZONE ("4") = XnM3A collector timings.
        cfg.put("4", xnM3ATimings(cfg));
        cfg.put("3002", "0");
        System.out.println("apk start cfg 207.chars="
                + (tdid == null ? 0 : tdid.length())
                + " 406=" + preview406(field406)
                + " 264=" + cfg.get("264")
                + " 281=" + cfg.get("281")
                + " 284=" + preview(cfg.get("284"), 80)
                + " 285=" + cfg.get("285")
                + " 286=" + cfg.get("286"));
        return cfg;
    }

    /**
     * {@code XnM3A.a()} blob that s5pTT writes to cfg "4": {@code id_elapsedMs} plus
     * {@code s9_...} for the second map. Elapsed is not wall-accurate under unidbg;
     * keep the same key set the APK collectors would have timed.
     */
    private static String xnM3ATimings(Map<String, String> cfg) {
        StringBuilder sb = new StringBuilder();
        int[] ids = new int[] {28, 24, 102, 103, 104, 105, 117, 126, 138, 146, 149, 150, 151};
        for (int id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id).append("_0");
        }
        if (cfg != null && cfg.containsKey("309")) {
            sb.append(",s9_0");
        }
        return sb.toString();
    }

    /**
     * Remaining kC0XR.a HashMap puts the APK Java layer writes before m209302.
     * Skip-gated the same way as {@code aw2ka.a(id)}. Empty string still counts as "collected".
     */
    private void fillKc0xrJavaCollectors(Map<String, String> cfg) {
        TraceEnvironmentConfig env = TraceEnvironmentConfig.get(emulator);
        if (collectField(22)) {
            cfg.put("205", "");
        }
        if (collectField(100)) {
            cfg.put("250", "");
        }
        if (collectField(101)) {
            cfg.put("251", "0");
            cfg.put("288", "0");
        }
        if (collectField(106)) {
            cfg.put("256", "");
        }
        if (collectField(107)) {
            cfg.put("257", apkCertTag());
        }
        if (collectField(115)) {
            cfg.put("268", "");
        }
        if (collectField(116)) {
            cfg.put("269", "");
        }
        if (collectField(44)) {
            cfg.put("240", tracerPid());
        }
        cfg.put("272", "");
        cfg.put("273", "");
        cfg.put("266", "");
        cfg.put("303", "");
        cfg.put("402", "");
        cfg.put("309", "");
        if (collectField(120)) {
            cfg.put("279", "");
        }
        if (collectField(122)) {
            cfg.put("280", "");
        }
        if (collectField(140)) {
            cfg.put("2021", accessibilityBlob(env));
        }
        if (collectField(145)) {
            cfg.put("283", "");
        }
        if (collectField(147)) {
            cfg.put("2029", "0");
        }
        if (collectField(155)) {
            cfg.put("290", "0");
        }
        cfg.put("2019", "android.content.pm.IPackageManager$Stub$Proxy,,");
        String androidId = settingString("secure", "android_id");
        if (androidId != null && !androidId.isEmpty()) {
            cfg.put("275", androidId);
        }
    }

    /** GOzix extra keys for a209302/8117 that apkTdidCfg used to omit. */
    private void fillGozixJavaCollectors(Map<String, String> cfg) {
        TraceEnvironmentConfig env = TraceEnvironmentConfig.get(emulator);
        cfg.put("270", String.valueOf(debugFlags()));
        cfg.put("308", String.valueOf(a48dbNetworkType(env)));
        if (collectField(102)) {
            cfg.put("252", settingInt("secure", "mock_location", 0) != 0 ? "1" : "0");
        }
        if (collectField(104)) {
            cfg.put("254", String.valueOf(batteryPlugState(env)));
        }
        if (collectField(105)) {
            cfg.put("255", String.valueOf(batteryPercent(env)));
        }
        if (collectField(126)) {
            cfg.put("281", String.valueOf(System.currentTimeMillis() - elapsedRealtimeMs()));
        }
        cfg.put("403", "0");
        cfg.put("402", "");
        cfg.put("404", "");
        cfg.put("401", "");
        cfg.put("302", "");
        cfg.put("303", "");
        cfg.put("305", "");
        cfg.put("309", "");
        cfg.put("310", "");
        cfg.put("266", "");
        if (collectField(41)) {
            cfg.put("100", "");
        }
    }

    /** OCkqn.e: {@code pkg_sha1(cert)} when signatures are available. */
    private String apkCertTag() {
        return PACKAGE + "_";
    }

    /** yfKKe.a: tracer pid line from /proc/self/status. Empty if not traced. */
    private String tracerPid() {
        return "";
    }

    /** FE6di.a abbreviated: enabled,touchExploration,services,setting. */
    private String accessibilityBlob(TraceEnvironmentConfig env) {
        int enabled = settingInt("secure", "accessibility_enabled", 0);
        String services = settingString("secure", "enabled_accessibility_services");
        if (services == null) {
            services = "";
        }
        return enabled + ",0," + services.replace(",", ";") + "," + enabled + ",";
    }

    private String settingString(String ns, String key) {
        TraceEnvironmentConfig env = TraceEnvironmentConfig.get(emulator);
        if (env == null) {
            return null;
        }
        return env.getAndroidSettingString(ns, key);
    }

    /** {@code aw2ka.a(id)}: collect when the id is not in the skip set. */
    private boolean collectField(int id) {
        return skipIds == null || !skipIds.contains(Integer.valueOf(id));
    }

    private String buildModel() {
        TraceEnvironmentConfig env = TraceEnvironmentConfig.get(emulator);
        if (env != null) {
            String model = env.getAndroidBuildString("MODEL");
            if (model != null && !model.isEmpty()) {
                return model;
            }
        }
        return "Pixel 6";
    }

    private String gozix210() {
        long now = System.currentTimeMillis();
        return "T:" + sdkInitMs
                + ",LT:" + (now - sdkInitMs)
                + ",F:1,PT:0,PKG:1,SC:1,SRT:0,R:0_1,US:0,GCI:c60899f";
    }

    /** kC0XR cfg 210 on the local start() path: {@code C:0,T:<init>,LT:<elapsed>}. */
    private String kc0xr210(boolean http8123) {
        long now = System.currentTimeMillis();
        return "C:" + (http8123 ? "1" : "0")
                + ",T:" + sdkInitMs
                + ",LT:" + (now - sdkInitMs);
    }

    private int settingInt(String ns, String key, int fallback) {
        TraceEnvironmentConfig env = TraceEnvironmentConfig.get(emulator);
        if (env == null) {
            return fallback;
        }
        String raw = env.getAndroidSettingString(ns, key);
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int debugFlags() {
        int flags = 0;
        // APK OCkqn.a reads Settings.Secure; some images keep the same keys in global.
        if (settingInt("secure", "adb_enabled", 0) > 0 || settingInt("global", "adb_enabled", 0) > 0) {
            flags |= 1;
        }
        if (settingInt("secure", "development_settings_enabled", 0) > 0
                || settingInt("global", "development_settings_enabled", 0) > 0) {
            flags |= 2;
        }
        if ((APP_FLAGS & 2) != 0) {
            flags |= 4;
        }
        return flags;
    }

    private int batteryPercent(TraceEnvironmentConfig env) {
        if (env == null || !env.isAndroidBatteryConfigured() || env.getAndroidBatteryConfig() == null
                || !env.getAndroidBatteryConfig().isCapacityPercentConfigured()) {
            return -1;
        }
        int pct = env.getAndroidBatteryConfig().getCapacityPercent();
        if (pct < 0) {
            return 0;
        }
        return pct > 100 ? 100 : pct;
    }

    /** OCkqn.b: 1=not charging, 2=AC, 3=USB, 0=other charging. */
    private int batteryPlugState(TraceEnvironmentConfig env) {
        if (env == null || !env.isAndroidBatteryConfigured() || env.getAndroidBatteryConfig() == null) {
            return 0;
        }
        TraceEnvironmentConfig.AndroidBatteryConfig bat = env.getAndroidBatteryConfig();
        int status = bat.isStatusConfigured() ? bat.getStatus() : 0;
        if (status != 2 && status != 5) {
            return 1;
        }
        int plugged = bat.isPluggedConfigured() ? bat.getPlugged() : 0;
        if (plugged == 2) {
            return 3;
        }
        if (plugged == 1) {
            return 2;
        }
        return 0;
    }

    private String simStates(TraceEnvironmentConfig env) {
        if (env == null || !env.isTelephonyPhoneCountConfigured()) {
            return null;
        }
        int count = env.getTelephonyPhoneCount(1);
        StringBuilder sb = new StringBuilder();
        sb.append(env.getTelephonySlotSimState(0, 0));
        sb.append(',').append(count);
        if (count > 1) {
            sb.append(',');
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    sb.append(';');
                }
                sb.append(env.getTelephonySlotSimState(i, 0));
            }
        }
        return sb.toString();
    }

    private String localeTag(TraceEnvironmentConfig env) {
        if (env != null && env.getAndroidLocaleConfig() != null) {
            Locale loc = env.getAndroidLocaleConfig().getLocale();
            if (loc != null) {
                String lang = loc.getLanguage();
                String country = loc.getCountry();
                if (lang != null && !lang.isEmpty()) {
                    if (country == null || country.isEmpty()) {
                        return lang;
                    }
                    return lang + "-" + country;
                }
            }
        }
        return "zh-CN";
    }

    private String timezoneId(TraceEnvironmentConfig env) {
        if (env != null && env.getAndroidLocaleConfig() != null
                && env.getAndroidLocaleConfig().isTimezoneIdConfigured()) {
            return env.getAndroidLocaleConfig().getTimezoneId();
        }
        return "Asia/Shanghai";
    }

    private long elapsedRealtimeMs() {
        TraceEnvironmentConfig env = TraceEnvironmentConfig.get(emulator);
        if (env != null) {
            Long nanos = env.getMonotonicNanos();
            if (nanos != null) {
                return nanos.longValue() / 1_000_000L;
            }
        }
        return 1_475_582_860L;
    }

    /**
     * A48DB.a(): transportBits,capabilityBits,iface,addrs(;),dns(;).
     * Same ConnectivityManager fields unidbg already serves from the profile.
     */
    private String a48dbActiveNetwork(TraceEnvironmentConfig env) {
        if (env == null || !env.isNetworkLinksConfigured()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        long transport = 0L;
        long caps = 0L;
        if (env.isNetworkCapabilitiesConfigured() && env.getNetworkCapabilitiesConfig() != null) {
            transport = env.getNetworkCapabilitiesConfig().transportBitset();
            caps = env.getNetworkCapabilitiesConfig().capabilityBitset();
        }
        sb.append(transport).append(',').append(caps);
        String iface = env.getLinkString("interfaceName");
        if (iface == null) {
            iface = "";
        }
        iface = iface.replace(",", "").replace(";", "");
        sb.append(',').append(iface).append(',');
        List<String> addrs = new ArrayList<String>();
        if (env.isNetworkIpv6AddressesConfigured()) {
            for (TraceEnvironmentConfig.NetworkIpv6AddressConfig v6 : env.getNetworkIpv6Addresses()) {
                if (iface.equals(v6.getInterfaceName())) {
                    addrs.add(ipv6HexToText(v6.getAddressHex()));
                }
            }
        }
        if (env.isNetworkInterfacesConfigured()) {
            for (TraceEnvironmentConfig.NetworkInterfaceConfig nic : env.getNetworkInterfaces()) {
                if (iface.equals(nic.getName()) && nic.getIpv4() != null
                        && !"169.254.1.1".equals(nic.getIpv4())) {
                    addrs.add(nic.getIpv4());
                }
            }
        }
        for (int i = 0; i < addrs.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(addrs.get(i));
        }
        sb.append(',');
        if (env.isLinkDnsServersConfigured()) {
            List<String> dns = env.getLinkDnsServers();
            for (int i = 0; i < dns.size(); i++) {
                if (i > 0) {
                    sb.append(';');
                }
                sb.append(dns.get(i));
            }
        }
        return sb.toString();
    }

    /** A48DB.b: 0=wifi, 1=cell, 2=cell+proxy, 3=other, -1=none. */
    private int a48dbNetworkType(TraceEnvironmentConfig env) {
        if (env == null || !env.isNetworkLinksConfigured() || !env.getLinkBoolean("connected", false)) {
            return -1;
        }
        int type = env.getLinkInt("type", -1);
        if (type == 1) {
            return 0;
        }
        if (type == 0) {
            return 1;
        }
        if (type > 0) {
            return 3;
        }
        return -1;
    }

    private static String ipv6HexToText(String hex) {
        if (hex == null || hex.length() != 32) {
            return "";
        }
        int[] g = new int[8];
        for (int i = 0; i < 8; i++) {
            g[i] = Integer.parseInt(hex.substring(i * 4, i * 4 + 4), 16);
        }
        int bestStart = -1;
        int bestLen = 0;
        int curStart = -1;
        int curLen = 0;
        for (int i = 0; i <= 8; i++) {
            if (i < 8 && g[i] == 0) {
                if (curStart < 0) {
                    curStart = i;
                    curLen = 1;
                } else {
                    curLen++;
                }
            } else {
                if (curLen > bestLen) {
                    bestStart = curStart;
                    bestLen = curLen;
                }
                curStart = -1;
                curLen = 0;
            }
        }
        if (bestLen < 2) {
            bestStart = -1;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < 8) {
            if (i == bestStart) {
                sb.append("::");
                i += bestLen;
                continue;
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ':') {
                sb.append(':');
            }
            sb.append(Integer.toHexString(g[i]));
            i++;
        }
        return sb.toString();
    }

    /** kC0XR: "100_token,101_tdid,102_camera" with _ → %5F and , → %54 in values. */
    private static String build406(Map<Integer, String> extra) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> e : extra.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            String value = e.getValue() == null ? "" : e.getValue();
            value = value.replace("_", "%5F").replace(",", "%54");
            sb.append(e.getKey()).append('_').append(value);
        }
        return sb.toString();
    }

    private static String preview406(String field406) {
        if (field406 == null) {
            return "";
        }
        if (field406.length() <= 80) {
            return field406;
        }
        return field406.substring(0, 24) + "...(len=" + field406.length() + ")";
    }

    private void persistTdidPrefs(String tdid, Map<Integer, Object> parsed) {
        Map<String, String> fd = prefsFor("turingfd_conf_209302_mfa_cc");
        if (tdid != null && !tdid.isEmpty()) {
            fd.put("101", tdid);
        }
        Object exp = parsed == null ? null : unwrap(parsed.get(102));
        if (exp != null && !String.valueOf(exp).isEmpty()) {
            fd.put("102", String.valueOf(exp));
        } else {
            fd.put("102", String.valueOf(System.currentTimeMillis() / 1000L + 86400L));
        }
        for (int key : new int[] {104, 105, 106, 110}) {
            Object v = parsed == null ? null : unwrap(parsed.get(key));
            if (v != null && !String.valueOf(v).isEmpty()) {
                fd.put(String.valueOf(key), String.valueOf(v));
            }
        }
        System.out.println("prefs turingfd keys=" + fd.keySet());
    }

    private Map<String, String> prefsFor(String name) {
        Map<String, String> existing = prefsStore.get(name);
        if (existing == null) {
            existing = new LinkedHashMap<String, String>();
            prefsStore.put(name, existing);
        }
        return existing;
    }

    /**
     * mmap extra ELF SOs into the process without linking (no DT_NEEDED / reloc / init).
     * They show up in the next {@code /proc/self/maps} open.
     * Default dir is {@code profiles/pixel6-oriole-13/pulled} (adb-pulled from Pixel 6).
     * Spec: {@code name|/maps/path,name2|/maps/path2}.
     */
    private void mapNoLinkDecor() {
        File defaultDir = new File(PROFILE.getParentFile(), "pulled");
        String dir = propertyOr("turing.mapSoDir",
                defaultDir.isDirectory() ? defaultDir.getAbsolutePath() : "");
        if (dir.isEmpty()) {
            System.out.println("mapNoLink skip: no turing.mapSoDir and " + defaultDir + " missing");
            return;
        }
        File root = new File(dir);
        String spec = propertyOr("turing.mapSos",
                "libart.so|/apex/com.android.art/lib64/libart.so,"
                        + "libandroid_runtime.so|/system/lib64/libandroid_runtime.so,"
                        + "libcamera_client.so|/system/lib64/libcamera_client.so,"
                        + "libgui.so|/system/lib64/libgui.so,"
                        + "libjnigraphics.so|/system/lib64/libjnigraphics.so,"
                        + "libandroid.so|/system/lib64/libandroid.so,"
                        + "libc++.so|/system/lib64/libc++.so,"
                        + "libutils.so|/system/lib64/libutils.so,"
                        + "libui.so|/system/lib64/libui.so,"
                        + "libbinder.so|/system/lib64/libbinder.so,"
                        + "libhwui.so|/system/lib64/libhwui.so,"
                        + "libnativeloader_lazy.so|/system/lib64/libnativeloader_lazy.so,"
                        + "libandroidfw.so|/system/lib64/libandroidfw.so,"
                        + "boot-framework.oat|/system/framework/arm64/boot-framework.oat,"
                        + "boot.oat|/system/framework/arm64/boot.oat,"
                        + "boot-framework-graphics.oat|/system/framework/arm64/boot-framework-graphics.oat,"
                        + "boot-core-libart.oat|/system/framework/arm64/boot-core-libart.oat,"
                        + "boot-core-icu4j.oat|/system/framework/arm64/boot-core-icu4j.oat,"
                        + "boot-okhttp.oat|/system/framework/arm64/boot-okhttp.oat,"
                        + "boot-ext.oat|/system/framework/arm64/boot-ext.oat");
        int mapped = 0;
        for (String item : spec.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int bar = trimmed.indexOf('|');
            String name = bar < 0 ? trimmed : trimmed.substring(0, bar);
            String mapsPath = bar < 0 ? "/system/lib64/" + name : trimmed.substring(bar + 1);
            File so = new File(root, name);
            if (!so.isFile()) {
                so = new File(mapsPath.replace('/', File.separatorChar));
            }
            if (!so.isFile() || so.length() < 64) {
                System.out.println("mapNoLink skip " + name + " file=" + so.getAbsolutePath()
                        + " size=" + (so.isFile() ? so.length() : -1));
                continue;
            }
            try {
                Module extra = emulator.getMemory().mapNoLink(
                        new ElfLibraryFile(so, true, mapsPath));
                System.out.println("mapNoLink " + extra.name
                        + " base=0x" + Long.toHexString(extra.base)
                        + " size=0x" + Long.toHexString(extra.size)
                        + " maps=" + mapsPath);
                mapped++;
            } catch (Exception e) {
                System.out.println("mapNoLink fail " + name + " " + e);
            }
        }
        System.out.println("mapNoLink done mapped=" + mapped + " loadedModules="
                + emulator.getMemory().getLoadedModules().size());
    }

    private static String propertyOr(String key, String fallback) {
        String value = System.getProperty(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        String envKey = key.replace('.', '_').toUpperCase(java.util.Locale.ROOT);
        String env = System.getenv(envKey);
        if (env != null && !env.isEmpty()) {
            return env;
        }
        String alt = key.replaceAll("([a-z])([A-Z])", "$1_$2")
                .replace('.', '_').toUpperCase(java.util.Locale.ROOT);
        if (!alt.equals(envKey)) {
            env = System.getenv(alt);
            if (env != null && !env.isEmpty()) {
                return env;
            }
        }
        return fallback;
    }

    private void callEnvInit(DvmObject<?> ctx) {
        Map<String, String> cfg = new LinkedHashMap<String, String>();
        cfg.put("2008", "0");
        cfg.put("2009", "1");
        cfg.put("2010", "1");
        cfg.put("2011", "1");
        cfg.put("2025", "0");
        cfg.put("2028", "0");
        Map<Integer, Object> sparse = new LinkedHashMap<Integer, Object>();
        DvmClass jobj = vm.resolveClass("java/lang/Object");
        DvmObject<?> oaidOff = vm.resolveClass("com/tencent/turingcam/cc/tmnyR", jobj).newObject("");
        DvmObject<?> oaidOn = vm.resolveClass("com/tencent/turingcam/cc/tmnyR", jobj).newObject("");
        DvmObject<?> readSwitch = vm.resolveClass("com/tencent/turingcam/cc/qQ6Lq", jobj).newObject("1");
        try {
            tNative.callStaticJniMethodObject(emulator,
                    "i209302_97A941171A843DDA(Landroid/util/SparseArray;Landroid/content/Context;Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Landroid/util/SparseArray;",
                    newSparse(sparse), ctx, newMap(cfg), oaidOff, oaidOn, readSwitch);
            dumpSparse("i209302/env", sparse);
        } catch (Exception e) {
            System.out.println("i209302 env init failed: " + e);
        }
        try {
            DvmObject<?> uuidObj = tNative.callStaticJniMethodObject(emulator,
                    "j209302_97A941171A843DDA()Ljava/lang/String;");
            System.out.println("j209302 uuid=" + stringify(uuidObj));
        } catch (Exception e) {
            System.out.println("j209302 uuid failed: " + e);
        }
    }

    /**
     * aw2ka.b(): start from hardcoded {@code aw2ka.i}, then 8119 CvowV.e
     * (value 0 → skip, nonzero → collect). Pixel brand does not add 148.
     */
    private String buildSkipCsv(byte[] initRspBody) {
        Set<Integer> skip = new TreeSet<Integer>();
        int[] hardcoded = {40, 43, 36, 45, 136, 143, 144, 114, 10002, 10003};
        for (int id : hardcoded) {
            skip.add(Integer.valueOf(id));
        }
        if (initRspBody != null && initRspBody.length > 0) {
            Map<Integer, Object> root = TuringPacketPacker.parseJce(initRspBody);
            Object cvow = root.get(1);
            if (cvow instanceof Map) {
                Object field4 = ((Map<?, ?>) cvow).get(4);
                if (field4 instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) field4).entrySet()) {
                        int key = toInt(e.getKey());
                        int val = toInt(e.getValue());
                        if (key == 0 && val == 0 && e.getKey() == null) {
                            continue;
                        }
                        if (val == 0) {
                            skip.add(Integer.valueOf(key));
                        } else {
                            skip.remove(Integer.valueOf(key));
                        }
                    }
                }
            }
        }
        // Pixel 6 APK collected #15/#148. 8119 field-4 skip from other identities
        // must not drop them or native never reads ringtone / boot_id.
        skip.remove(Integer.valueOf(15));
        skip.remove(Integer.valueOf(148));
        skipIds = skip;
        StringBuilder sb = new StringBuilder();
        for (Integer id : skip) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void logInitIdentity(byte[] body) {
        Map<Integer, Object> root = TuringPacketPacker.parseJce(body);
        Object ident = root.get(1);
        System.out.println("8119 ident=" + ident + " uuid3=" + root.get(3));
    }

    @SuppressWarnings("unchecked")
    private static void logDfpIdentity(String title, byte[] body) {
        Map<Integer, Object> root = TuringPacketPacker.parseJce(body);
        Map<Integer, Object> m = TuringPacketPacker.findDfpMap(root);
        if (m == null) {
            System.out.println(title + " no dfp map");
            return;
        }
        System.out.println(title + " keys=" + m.size()
                + " #8=" + m.get(8)
                + " #9=" + preview(m.get(9), 120)
                + " #11=" + preview(m.get(11), 160)
                + " #13=" + m.get(13)
                + " #15=" + m.get(15)
                + " #16=" + m.get(16)
                + " #18=" + preview(m.get(18), 180)
                + " #24=" + m.get(24)
                + " #26=" + m.get(26)
                + " #51=" + m.get(51)
                + " #131=" + m.get(131)
                + " #136=" + m.get(136)
                + " #148=" + m.get(148)
                + " #149=" + m.get(149)
                + " #150=" + m.get(150)
                + " #200=" + m.get(200));
        Object hash = root.get(6);
        if (hash == null) {
            Map<Integer, Object> inner = TuringPacketPacker.frontInner(body);
            if (inner != null) {
                hash = inner.get(5);
            }
        }
        Object hash7 = null;
        if (hash instanceof Map) {
            hash7 = ((Map<?, ?>) hash).get(7);
        }
        System.out.println(title + " #6.7=" + hash7);
        System.out.println(title + " hash=" + hash);
    }

    private static String preview(Object v, int max) {
        if (v == null) {
            return "null";
        }
        String s = String.valueOf(v);
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...(len=" + s.length() + ")";
    }

    private static int toInt(Object v) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt((String) v);
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    private DvmObject<?> newMap(Map<String, String> cfg) {
        return vm.resolveClass("java/util/HashMap", vm.resolveClass("java/util/Map")).newObject(cfg);
    }

    private DvmObject<?> newSparse(Map<Integer, Object> sparse) {
        return vm.resolveClass("android/util/SparseArray").newObject(sparse);
    }

    private byte[] callPack(String method, String title, String fileName, DvmObject<?> ctx,
                            Map<String, String> cfg, Object extra, int mode) {
        Map<Integer, Object> sparse = new LinkedHashMap<Integer, Object>();
        DvmObject<?> sparseObj = newSparse(sparse);
        DvmObject<?> cfgObj = newMap(cfg);
        DvmObject<?> extraObj = extra instanceof DvmObject ? (DvmObject<?>) extra : null;
        tNative.callStaticJniMethodObject(emulator, method, sparseObj, ctx, cfgObj, extraObj, mode);
        return requireBody(title, fileName, sparse);
    }

    private byte[] callPack(String method, String title, String fileName, DvmObject<?> ctx,
                            Map<String, String> cfg) {
        Map<Integer, Object> sparse = new LinkedHashMap<Integer, Object>();
        DvmObject<?> sparseObj = newSparse(sparse);
        DvmObject<?> cfgObj = newMap(cfg);
        tNative.callStaticJniMethodObject(emulator, method, sparseObj, ctx, cfgObj, 0);
        return requireBody(title, fileName, sparse);
    }

    private byte[] callPackNoMode(String method, String title, String fileName, DvmObject<?> ctx,
                                  Map<String, String> cfg) {
        Map<Integer, Object> sparse = new LinkedHashMap<Integer, Object>();
        DvmObject<?> sparseObj = newSparse(sparse);
        DvmObject<?> cfgObj = newMap(cfg);
        tNative.callStaticJniMethodObject(emulator, method, sparseObj, ctx, cfgObj);
        return requireBody(title, fileName, sparse);
    }

    private static byte[] requireBody(String title, String fileName, Map<Integer, Object> sparse) {
        dumpSparse(title, sparse);
        if (sparse.isEmpty()) {
            throw new IllegalStateException(title + " produced empty SparseArray (native call failed)");
        }
        Object err = unwrap(sparse.get(0));
        Object body = unwrap(sparse.get(1));
        if (!(err instanceof Integer) || ((Integer) err) != 0 || !(body instanceof byte[])) {
            throw new IllegalStateException(title + " unexpected result err=" + err + " body=" + body);
        }
        writeOut(fileName, (byte[]) body);
        System.out.println("wrote native " + fileName + " bytes=" + ((byte[]) body).length);
        return (byte[]) body;
    }

    private static void writeOut(String fileName, byte[] data) {
        File outBin = new File("D:\\project\\12306\\12306-5.9.6.6\\scripts\\ida_out\\" + fileName);
        outBin.getParentFile().mkdirs();
        try {
            java.nio.file.Files.write(outBin.toPath(), data);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("write " + outBin, e);
        }
        System.out.println("wrote " + outBin.getAbsolutePath() + " bytes=" + data.length);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Object> asSparse(DvmObject<?> obj) {
        return (Map<Integer, Object>) obj.getValue();
    }

    private static void dumpSparse(String title, Map<Integer, Object> sparse) {
        System.out.println("===== " + title + " keys=" + sparse.keySet() + " =====");
        for (Map.Entry<Integer, Object> e : sparse.entrySet()) {
            Object v = unwrap(e.getValue());
            if (v instanceof byte[]) {
                byte[] bytes = (byte[]) v;
                System.out.println("  [" + e.getKey() + "] byte[" + bytes.length + "] hex="
                        + hexPreview(bytes, 64));
            } else {
                System.out.println("  [" + e.getKey() + "] " + (v == null ? "null" : v.getClass().getSimpleName())
                        + " = " + v);
            }
        }
    }

    private static Object unwrap(Object v) {
        if (v instanceof DvmObject) {
            return unwrap(((DvmObject<?>) v).getValue());
        }
        return v;
    }

    private static String stringify(DvmObject<?> obj) {
        if (obj == null) {
            return "null";
        }
        Object v = unwrap(obj);
        return v == null ? "null" : String.valueOf(v);
    }

    private static String hexPreview(byte[] data, int max) {
        int n = Math.min(data.length, max);
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02x", data[i] & 0xff));
        }
        if (data.length > max) {
            sb.append("...");
        }
        return sb.toString();
    }

    @Override
    public boolean callBooleanMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        Boolean handled = mapBoolean(dvmObject, signature, varArg);
        if (handled != null) {
            return handled;
        }
        return super.callBooleanMethod(vm, dvmObject, signature, varArg);
    }

    @Override
    public boolean callBooleanMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        Boolean handled = mapBoolean(dvmObject, signature, vaList);
        if (handled != null) {
            return handled;
        }
        return super.callBooleanMethodV(vm, dvmObject, signature, vaList);
    }

    @Override
    public DvmObject<?> getObjectField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (signature.equals("android/content/pm/PackageInfo->versionName:Ljava/lang/String;")) {
            return new StringObject(vm, "5.9.6.6");
        }
        if (signature.equals("android/content/pm/PackageInfo->packageName:Ljava/lang/String;")) {
            Object value = dvmObject.getValue();
            String name = value instanceof String ? (String) value : PACKAGE;
            return new StringObject(vm, name);
        }
        if (signature.equals("android/content/pm/ApplicationInfo->packageName:Ljava/lang/String;")) {
            return new StringObject(vm, PACKAGE);
        }
        if (signature.equals("android/content/pm/PackageInfo->applicationInfo:Landroid/content/pm/ApplicationInfo;")) {
            String name = dvmObject.getValue() instanceof String ? (String) dvmObject.getValue() : PACKAGE;
            return vm.resolveClass("android/content/pm/ApplicationInfo").newObject(name);
        }
        return super.getObjectField(vm, dvmObject, signature);
    }

    @Override
    public long getLongField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (signature.equals("android/content/pm/PackageInfo->lastUpdateTime:J")
                || signature.equals("android/content/pm/PackageInfo->firstInstallTime:J")) {
            return INSTALL_TIME_MS;
        }
        return super.getLongField(vm, dvmObject, signature);
    }

    @Override
    public int getIntField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (signature.equals("android/content/pm/PackageInfo->versionCode:I")) {
            return VERSION_CODE;
        }
        if (signature.equals("android/content/pm/ApplicationInfo->flags:I")) {
            return APP_FLAGS;
        }
        if (signature.equals("android/content/pm/ApplicationInfo->uid:I")) {
            return APP_UID;
        }
        if (signature.equals("android/graphics/Point->x:I") && dvmObject.getValue() instanceof PointValue) {
            return ((PointValue) dvmObject.getValue()).x;
        }
        if (signature.equals("android/graphics/Point->y:I") && dvmObject.getValue() instanceof PointValue) {
            return ((PointValue) dvmObject.getValue()).y;
        }
        Integer metric = displayMetricField(dvmObject, signature);
        if (metric != null) {
            return metric;
        }
        return super.getIntField(vm, dvmObject, signature);
    }

    @Override
    public void setIntField(BaseVM vm, DvmObject<?> dvmObject, String signature, int value) {
        if (signature.equals("android/graphics/Point->x:I") && dvmObject.getValue() instanceof PointValue) {
            ((PointValue) dvmObject.getValue()).x = value;
            return;
        }
        if (signature.equals("android/graphics/Point->y:I") && dvmObject.getValue() instanceof PointValue) {
            ((PointValue) dvmObject.getValue()).y = value;
            return;
        }
        super.setIntField(vm, dvmObject, signature, value);
    }

    @Override
    public int getStaticIntField(BaseVM vm, DvmClass dvmClass, String signature) {
        Integer flag = applicationInfoFlag(signature);
        if (flag != null) {
            return flag;
        }
        Integer displayType = displayTypeFlag(signature);
        if (displayType != null) {
            return displayType;
        }
        Integer ringtoneType = ringtoneTypeFlag(signature);
        if (ringtoneType != null) {
            return ringtoneType;
        }
        Integer mode = stubStaticInt(signature);
        if (mode != null) {
            return mode;
        }
        if (signature.contains("->FLAG_") || signature.contains("->MATCH_")
                || signature.contains("->GET_")) {
            System.out.println("static int fallback " + signature + " -> 0");
            return 0;
        }
        return super.getStaticIntField(vm, dvmClass, signature);
    }

    @Override
    public DvmObject<?> getStaticObjectField(BaseVM vm, DvmClass dvmClass, String signature) {
        DvmObject<?> uri = settingsUri(vm, signature);
        if (uri != null) {
            return uri;
        }
        return super.getStaticObjectField(vm, dvmClass, signature);
    }

    @Override
    public DvmObject<?> callObjectMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        DvmObject<?> prefs = handleSharedPrefObject(vm, dvmObject, signature, varArg);
        if (prefs != null || isSharedPrefObject(signature)) {
            return prefs;
        }
        if (isSparseGet(signature)) {
            return handleSparseGet(vm, dvmObject, signature, intArg(varArg, 0));
        }
        if (isPackageName(signature)) {
            return new StringObject(vm, PACKAGE);
        }
        if (isGetResources(signature)) {
            return vm.resolveClass("android/content/res/Resources").newObject("resources");
        }
        if (isDisplayMetrics(signature)) {
            return newDisplayMetrics(vm);
        }
        DvmObject<?> pkg = handleGetPackageInfo(vm, signature, varArg);
        if (pkg != null) {
            return pkg;
        }
        DvmObject<?> collection = handleCollectionObject(vm, dvmObject, signature, varArg);
        if (collection != null || isHandledCollectionObject(signature)) {
            return collection;
        }
        if (signature.contains("getDefaultDisplay()Landroid/view/Display;")
                || signature.contains("getDisplay(I)Landroid/view/Display;")) {
            return vm.resolveClass("android/view/Display").newObject("display");
        }
        if (signature.contains("getDisplays(") && signature.contains("[Landroid/view/Display;")) {
            return new ArrayObject(vm.resolveClass("android/view/Display").newObject("display"));
        }
        if (isDisplayObject(signature)) {
            return handleDisplayObject(vm, signature);
        }
        if (isContentResolver(signature)) {
            return vm.resolveClass("android/content/ContentResolver").newObject("resolver");
        }
        DvmObject<?> installed = handleInstalledPackages(vm, signature);
        if (installed != null) {
            return installed;
        }
        DvmObject<?> pm = handlePackageManagerObject(vm, signature, varArg);
        if (pm != null) {
            return pm;
        }
        if (signature.endsWith("->toString()Ljava/lang/String;")
                || signature.equals("android/media/Ringtone->getTitle(Landroid/content/Context;)Ljava/lang/String;")) {
            if (signature.contains("qQ6Lq")) {
                return new StringObject(vm, "1");
            }
            Object value = dvmObject.getValue();
            return new StringObject(vm, value == null ? "" : String.valueOf(value));
        }
        return super.callObjectMethod(vm, dvmObject, signature, varArg);
    }

    @Override
    public DvmObject<?> callObjectMethodV(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VaList vaList) {
        return callObjectMethodV(vm, dvmObject, dvmMethod.getSignature(), vaList);
    }

    @Override
    public DvmObject<?> callObjectMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        DvmObject<?> prefs = handleSharedPrefObject(vm, dvmObject, signature, vaList);
        if (prefs != null || isSharedPrefObject(signature)) {
            return prefs;
        }
        if (isSparseGet(signature)) {
            return handleSparseGet(vm, dvmObject, signature, vaList.getIntArg(0));
        }
        if (isPackageName(signature)) {
            return new StringObject(vm, PACKAGE);
        }
        if (isGetResources(signature)) {
            return vm.resolveClass("android/content/res/Resources").newObject("resources");
        }
        if (isDisplayMetrics(signature)) {
            return newDisplayMetrics(vm);
        }
        DvmObject<?> pkg = handleGetPackageInfo(vm, signature, vaList);
        if (pkg != null) {
            return pkg;
        }
        DvmObject<?> collection = handleCollectionObject(vm, dvmObject, signature, vaList);
        if (collection != null || isHandledCollectionObject(signature)) {
            return collection;
        }
        if (signature.contains("getDefaultDisplay()Landroid/view/Display;")
                || signature.contains("getDisplay(I)Landroid/view/Display;")) {
            return vm.resolveClass("android/view/Display").newObject("display");
        }
        if (signature.contains("getDisplays(") && signature.contains("[Landroid/view/Display;")) {
            return new ArrayObject(vm.resolveClass("android/view/Display").newObject("display"));
        }
        if (isDisplayObject(signature)) {
            return handleDisplayObject(vm, signature);
        }
        if (isContentResolver(signature)) {
            return vm.resolveClass("android/content/ContentResolver").newObject("resolver");
        }
        DvmObject<?> installed = handleInstalledPackages(vm, signature);
        if (installed != null) {
            return installed;
        }
        DvmObject<?> pm = handlePackageManagerObject(vm, signature, vaList);
        if (pm != null) {
            return pm;
        }
        if (signature.endsWith("->toString()Ljava/lang/String;")
                || signature.equals("android/media/Ringtone->getTitle(Landroid/content/Context;)Ljava/lang/String;")) {
            if (signature.contains("qQ6Lq")) {
                return new StringObject(vm, "1");
            }
            Object value = dvmObject.getValue();
            return new StringObject(vm, value == null ? "" : String.valueOf(value));
        }
        return super.callObjectMethodV(vm, dvmObject, signature, vaList);
    }

    @Override
    public void callVoidMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        if (isSparsePut(signature)
                && handleSparsePut(dvmObject, signature, intArg(varArg, 0), varArg.getObjectArg(1))) {
            return;
        }
        if (handleCollectionVoid(dvmObject, signature, varArg)) {
            return;
        }
        if (handleDisplaySize(dvmObject, signature, varArg)) {
            return;
        }
        if (handleSharedPrefVoid(dvmObject, signature, varArg)) {
            return;
        }
        if (isIgnoredVoid(signature)) {
            return;
        }
        super.callVoidMethod(vm, dvmObject, signature, varArg);
    }

    @Override
    public void callVoidMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        if (isSparsePut(signature)
                && handleSparsePut(dvmObject, signature, vaList.getIntArg(0), vaList.getObjectArg(1))) {
            return;
        }
        if (handleCollectionVoid(dvmObject, signature, vaList)) {
            return;
        }
        if (handleDisplaySize(dvmObject, signature, vaList)) {
            return;
        }
        if (handleSharedPrefVoid(dvmObject, signature, vaList)) {
            return;
        }
        if (isIgnoredVoid(signature)) {
            return;
        }
        super.callVoidMethodV(vm, dvmObject, signature, vaList);
    }

    @Override
    public DvmObject<?> callStaticObjectMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        if (signature.equals("java/lang/Integer->valueOf(I)Ljava/lang/Integer;")) {
            return DvmInteger.valueOf(vm, intArg(varArg, 0));
        }
        DvmObject<?> stringValue = stringValueOf(vm, signature, varArg);
        if (stringValue != null) {
            return stringValue;
        }
        DvmObject<?> ringtone = handleRingtone(vm, signature);
        if (ringtone != null) {
            return ringtone;
        }
        DvmObject<?> settings = handleSettingsGet(vm, signature, varArg);
        if (settings != null) {
            return settings;
        }
        DvmObject<?> pm = handlePackageManagerStatic(vm, signature, varArg);
        if (pm != null) {
            return pm;
        }
        DvmObject<?> svc = handleServiceManagerStatic(vm, signature, varArg);
        if (svc != null || isServiceManagerStatic(signature)) {
            return svc;
        }
        DvmObject<?> stub = handleStubAsInterface(vm, signature);
        if (stub != null) {
            return stub;
        }
        DvmObject<?> prop = systemPropertyGet(vm, signature, varArg);
        if (prop != null) {
            return prop;
        }
        return super.callStaticObjectMethod(vm, dvmClass, signature, varArg);
    }

    @Override
    public DvmObject<?> callStaticObjectMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        if (signature.equals("java/lang/Integer->valueOf(I)Ljava/lang/Integer;")) {
            return DvmInteger.valueOf(vm, vaList.getIntArg(0));
        }
        DvmObject<?> stringValue = stringValueOf(vm, signature, vaList);
        if (stringValue != null) {
            return stringValue;
        }
        DvmObject<?> ringtone = handleRingtone(vm, signature);
        if (ringtone != null) {
            return ringtone;
        }
        DvmObject<?> settings = handleSettingsGet(vm, signature, vaList);
        if (settings != null) {
            return settings;
        }
        DvmObject<?> pm = handlePackageManagerStatic(vm, signature, vaList);
        if (pm != null) {
            return pm;
        }
        DvmObject<?> svc = handleServiceManagerStatic(vm, signature, vaList);
        if (svc != null || isServiceManagerStatic(signature)) {
            return svc;
        }
        DvmObject<?> stub = handleStubAsInterface(vm, signature);
        if (stub != null) {
            return stub;
        }
        DvmObject<?> prop = systemPropertyGet(vm, signature, vaList);
        if (prop != null) {
            return prop;
        }
        return super.callStaticObjectMethodV(vm, dvmClass, signature, vaList);
    }

    @Override
    public DvmObject<?> newObject(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        DvmObject<?> created = handleNewCollection(vm, dvmClass, signature, varArg);
        if (created != null) {
            return created;
        }
        return super.newObject(vm, dvmClass, signature, varArg);
    }

    @Override
    public DvmObject<?> newObjectV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        DvmObject<?> created = handleNewCollection(vm, dvmClass, signature, vaList);
        if (created != null) {
            return created;
        }
        return super.newObjectV(vm, dvmClass, signature, vaList);
    }

    @Override
    public DvmObject<?> allocObject(BaseVM vm, DvmClass dvmClass, String signature) {
        DvmObject<?> created = handleAllocCollection(vm, dvmClass, signature);
        if (created != null) {
            return created;
        }
        return super.allocObject(vm, dvmClass, signature);
    }

    @Override
    public int callStaticIntMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        Integer stub = stubStaticInt(signature);
        if (stub != null) {
            return stub;
        }
        return super.callStaticIntMethod(vm, dvmClass, signature, varArg);
    }

    @Override
    public int callStaticIntMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        Integer stub = stubStaticInt(signature);
        if (stub != null) {
            return stub;
        }
        return super.callStaticIntMethodV(vm, dvmClass, signature, vaList);
    }

    @Override
    public double callDoubleMethod(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VarArg varArg) {
        return callDoubleMethod(vm, dvmObject, dvmMethod.getSignature(), varArg);
    }

    @Override
    public double callDoubleMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        if (signature.equals("com/android/internal/os/PowerProfile->getAveragePower(Ljava/lang/String;)D")) {
            double value = batteryCapacityMah();
            System.out.println("PowerProfile.getAveragePower => " + value);
            return value;
        }
        return super.callDoubleMethod(vm, dvmObject, signature, varArg);
    }

    @Override
    public int callIntMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        if (isSparseSize(signature)) {
            return asSparse(dvmObject).size();
        }
        if (signature.equals("java/lang/Integer->intValue()I") && dvmObject.getValue() instanceof Integer) {
            return (Integer) dvmObject.getValue();
        }
        Integer collectionSize = handleCollectionInt(dvmObject, signature);
        if (collectionSize != null) {
            return collectionSize;
        }
        Integer display = handleDisplayInt(signature);
        if (display != null) {
            return display;
        }
        return super.callIntMethod(vm, dvmObject, signature, varArg);
    }

    @Override
    public int callIntMethodV(BaseVM vm, DvmObject<?> dvmObject, DvmMethod dvmMethod, VaList vaList) {
        return callIntMethodV(vm, dvmObject, dvmMethod.getSignature(), vaList);
    }

    @Override
    public int callIntMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        if (isSparseSize(signature)) {
            return asSparse(dvmObject).size();
        }
        if (signature.equals("java/lang/Integer->intValue()I") && dvmObject.getValue() instanceof Integer) {
            return (Integer) dvmObject.getValue();
        }
        Integer collectionSize = handleCollectionInt(dvmObject, signature);
        if (collectionSize != null) {
            return collectionSize;
        }
        Integer display = handleDisplayInt(signature);
        if (display != null) {
            return display;
        }
        return super.callIntMethodV(vm, dvmObject, signature, vaList);
    }

    private static boolean isSharedPrefObject(String signature) {
        return signature.contains("getSharedPreferences(Ljava/lang/String;I)")
                || signature.contains("SharedPreferences->getString(")
                || signature.contains("SharedPreferences->getAll(")
                || signature.contains("SharedPreferences->edit(")
                || signature.contains("SharedPreferences$Editor->putString(")
                || signature.contains("SharedPreferences$Editor->putInt(")
                || signature.contains("SharedPreferences$Editor->putLong(")
                || signature.contains("SharedPreferences$Editor->putBoolean(")
                || signature.contains("SharedPreferences$Editor->remove(")
                || signature.contains("SharedPreferences$Editor->clear(");
    }

    @SuppressWarnings("unchecked")
    private DvmObject<?> handleSharedPrefObject(BaseVM vm, DvmObject<?> dvmObject,
                                                String signature, Object args) {
        if (signature.contains("getSharedPreferences(Ljava/lang/String;I)")) {
            Object nameObj = objectArgValue(args, 0);
            String name = nameObj == null ? "default" : String.valueOf(nameObj);
            Map<String, String> store = prefsFor(name);
            System.out.println("getSharedPreferences " + name + " keys=" + store.keySet());
            return vm.resolveClass("android/content/SharedPreferences").newObject(store);
        }
        if (signature.contains("SharedPreferences->getString(")
                && dvmObject.getValue() instanceof Map) {
            Map<String, String> store = (Map<String, String>) dvmObject.getValue();
            Object keyObj = objectArgValue(args, 0);
            Object defObj = objectArgValue(args, 1);
            String key = keyObj == null ? "" : String.valueOf(keyObj);
            String value = store.get(key);
            if (value == null) {
                value = defObj == null ? "" : String.valueOf(defObj);
            }
            System.out.println("SharedPreferences.getString " + key + " chars=" + value.length());
            return new StringObject(vm, value);
        }
        if (signature.contains("SharedPreferences->getAll(")
                && dvmObject.getValue() instanceof Map) {
            return vm.resolveClass("java/util/HashMap", vm.resolveClass("java/util/Map"))
                    .newObject(dvmObject.getValue());
        }
        if (signature.contains("SharedPreferences->edit(")
                && dvmObject.getValue() instanceof Map) {
            return vm.resolveClass("android/content/SharedPreferences$Editor")
                    .newObject(dvmObject.getValue());
        }
        if ((signature.contains("SharedPreferences$Editor->putString(")
                || signature.contains("SharedPreferences$Editor->putInt(")
                || signature.contains("SharedPreferences$Editor->putLong(")
                || signature.contains("SharedPreferences$Editor->putBoolean(")
                || signature.contains("SharedPreferences$Editor->remove(")
                || signature.contains("SharedPreferences$Editor->clear("))
                && dvmObject.getValue() instanceof Map) {
            Map<String, String> store = (Map<String, String>) dvmObject.getValue();
            if (signature.contains("->clear(")) {
                store.clear();
            } else if (signature.contains("->remove(")) {
                Object keyObj = objectArgValue(args, 0);
                if (keyObj != null) {
                    store.remove(String.valueOf(keyObj));
                }
            } else {
                Object keyObj = objectArgValue(args, 0);
                Object valObj = objectArgValue(args, 1);
                if (keyObj != null) {
                    store.put(String.valueOf(keyObj), valObj == null ? "" : String.valueOf(valObj));
                }
            }
            return dvmObject;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean handleSharedPrefVoid(DvmObject<?> dvmObject, String signature, Object args) {
        if (!signature.contains("SharedPreferences$Editor->apply()V")) {
            return false;
        }
        return dvmObject.getValue() instanceof Map;
    }

    private static boolean isContentResolver(String signature) {
        return signature.equals("android/content/Context->getContentResolver()Landroid/content/ContentResolver;")
                || signature.equals("android/content/ContextWrapper->getContentResolver()Landroid/content/ContentResolver;")
                || signature.equals("android/app/Application->getContentResolver()Landroid/content/ContentResolver;");
    }

    private static boolean isServiceManagerStatic(String signature) {
        return signature.startsWith("android/os/ServiceManager->");
    }

    private DvmObject<?> handleServiceManagerStatic(BaseVM vm, String signature, Object args) {
        if (!isServiceManagerStatic(signature)) {
            return null;
        }
        System.out.println("ServiceManager " + signature);
        if (signature.contains("checkService") || signature.contains("getService")) {
            Object name = objectArgValue(args, 0);
            return vm.resolveClass("android/os/IBinder").newObject(name == null ? "service" : String.valueOf(name));
        }
        if (signature.contains("listServices")) {
            return packagesForUid(vm);
        }
        return null;
    }

    private DvmObject<?> handlePackageManagerStatic(BaseVM vm, String signature, Object args) {
        if (signature.equals(
                "android/content/pm/IPackageManager$Stub->asInterface(Landroid/os/IBinder;)Landroid/content/pm/IPackageManager;")) {
            return vm.resolveClass("android/content/pm/IPackageManager").newObject("package");
        }
        return null;
    }

    private DvmObject<?> handlePackageManagerObject(BaseVM vm, String signature, Object args) {
        if (signature.equals("android/os/IServiceManager->getService(Ljava/lang/String;)Landroid/os/IBinder;")) {
            String name = String.valueOf(objectArgValue(args, 0));
            System.out.println("IServiceManager.getService " + name);
            if ("package".equals(name)) {
                return vm.resolveClass("android/content/pm/IPackageManager").newObject("package");
            }
        }
        if (signature.contains("->getPackagesForUid(I)[Ljava/lang/String;")) {
            int uid = args instanceof VaList
                    ? ((VaList) args).getIntArg(0)
                    : intArg((VarArg) args, 0);
            if (uid != APP_UID) {
                return new ArrayObject();
            }
            return new ArrayObject(new StringObject(vm, PACKAGE));
        }
        if (signature.contains("->getNameForUid(I)Ljava/lang/String;")) {
            int uid = args instanceof VaList
                    ? ((VaList) args).getIntArg(0)
                    : intArg((VarArg) args, 0);
            if (uid == APP_UID) {
                return new StringObject(vm, PACKAGE);
            }
            return null;
        }
        if (signature.contains("IPackageManager->getPackageInfo(")
                || signature.contains("PackageManager->getPackageInfo(")) {
            return handleGetPackageInfo(vm,
                    "android/content/pm/PackageManager->getPackageInfo(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;",
                    args);
        }
        if (signature.contains("IPackageManager->getInstalledPackages(")
                || signature.contains("IPackageManager->getInstalledApplications(")) {
            return handleInstalledPackages(vm,
                    signature.contains("Applications")
                            ? "android/content/pm/PackageManager->getInstalledApplications(I)Ljava/util/List;"
                            : "android/content/pm/PackageManager->getInstalledPackages(I)Ljava/util/List;");
        }
        return null;
    }

    private ArrayObject packagesForUid(BaseVM vm) {
        List<StringObject> names = new ArrayList<StringObject>();
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null) {
            for (TraceEnvironmentConfig.PackageConfig pkg : config.getAndroidPackages()) {
                names.add(new StringObject(vm, pkg.getPackageName()));
            }
        }
        if (names.isEmpty()) {
            names.add(new StringObject(vm, PACKAGE));
        }
        return new ArrayObject(names.toArray(new StringObject[0]));
    }

    private DvmObject<?> handleInstalledPackages(BaseVM vm, String signature) {
        if (!signature.equals(
                "android/content/pm/PackageManager->getInstalledPackages(I)Ljava/util/List;")
                && !signature.equals(
                "android/content/pm/PackageManager->getInstalledApplications(I)Ljava/util/List;")) {
            return null;
        }
        List<Object> packages = new ArrayList<Object>();
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null) {
            for (TraceEnvironmentConfig.PackageConfig pkg : config.getAndroidPackages()) {
                packages.add(new PackageInfo(vm, pkg.getPackageName(), 0));
            }
        }
        if (packages.isEmpty()) {
            packages.add(new PackageInfo(vm, PACKAGE, 0));
        }
        return vm.resolveClass("java/util/ArrayList", vm.resolveClass("java/util/List")).newObject(packages);
    }

    private DvmObject<?> handleSettingsGet(BaseVM vm, String signature, Object args) {
        boolean system = signature.equals(
                "android/provider/Settings$System->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;");
        boolean secure = signature.equals(
                "android/provider/Settings$Secure->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;");
        boolean global = signature.equals(
                "android/provider/Settings$Global->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;");
        boolean uriFor = signature.contains("Settings$") && signature.contains("->getUriFor(");
        if (!system && !secure && !global && !uriFor) {
            return null;
        }
        DvmObject<?> keyObj = args instanceof VaList
                ? ((VaList) args).getObjectArg(uriFor ? 0 : 1)
                : ((VarArg) args).getObjectArg(uriFor ? 0 : 1);
        String key = keyObj == null ? "" : String.valueOf(keyObj.getValue());
        if (uriFor) {
            return vm.resolveClass("android/net/Uri").newObject("content://settings/system/" + key);
        }
        String ns = system ? "system" : secure ? "secure" : "global";
        String value = settingString(ns, key);
        if ((value == null || value.isEmpty()) && secure && "android_id".equals(key)) {
            TraceEnvironmentConfig env = TraceEnvironmentConfig.get(emulator);
            if (env != null && env.getAndroidIdentifiersConfig() != null
                    && env.getAndroidIdentifiersConfig().isAndroidIdConfigured()) {
                value = env.getAndroidIdentifiersConfig().getAndroidId();
            }
        }
        if (value != null) {
            System.out.println("Settings." + ns + ".getString " + key + "=" + value);
            return new StringObject(vm, value);
        }
        return null;
    }

    private DvmObject<?> settingsUri(BaseVM vm, String signature) {
        if (signature.equals("android/provider/Settings$System->DEFAULT_RINGTONE_URI:Landroid/net/Uri;")) {
            return vm.resolveClass("android/net/Uri").newObject("content://settings/system/ringtone");
        }
        if (signature.equals("android/provider/Settings$System->DEFAULT_NOTIFICATION_URI:Landroid/net/Uri;")) {
            return vm.resolveClass("android/net/Uri").newObject("content://settings/system/notification_sound");
        }
        if (signature.equals("android/provider/Settings$System->DEFAULT_ALARM_ALERT_URI:Landroid/net/Uri;")) {
            return vm.resolveClass("android/net/Uri").newObject("content://settings/system/alarm_alert");
        }
        return null;
    }

    private static Integer ringtoneTypeFlag(String signature) {
        if (signature.equals("android/media/RingtoneManager->TYPE_RINGTONE:I")) {
            return 1;
        }
        if (signature.equals("android/media/RingtoneManager->TYPE_NOTIFICATION:I")) {
            return 2;
        }
        if (signature.equals("android/media/RingtoneManager->TYPE_ALARM:I")) {
            return 4;
        }
        if (signature.equals("android/media/RingtoneManager->TYPE_ALL:I")) {
            return 7;
        }
        return null;
    }

    private DvmObject<?> handleRingtone(BaseVM vm, String signature) {
        if (signature.equals(
                "android/media/RingtoneManager->getActualDefaultRingtoneUri(Landroid/content/Context;I)Landroid/net/Uri;")) {
            return vm.resolveClass("android/net/Uri").newObject("content://settings/system/ringtone");
        }
        if (signature.equals("android/media/RingtoneManager->getRingtone(Landroid/content/Context;Landroid/net/Uri;)Landroid/media/Ringtone;")) {
            return vm.resolveClass("android/media/Ringtone").newObject("Your_new_adventure.ogg");
        }
        return null;
    }

    private static DvmObject<?> stringValueOf(BaseVM vm, String signature, Object args) {
        if (signature.equals("java/lang/String->valueOf(D)Ljava/lang/String;")) {
            double value = args instanceof VaList
                    ? ((VaList) args).getDoubleArg(0)
                    : ((VarArg) args).getDoubleArg(0);
            return new StringObject(vm, String.valueOf(value));
        }
        if (signature.equals("java/lang/String->valueOf(F)Ljava/lang/String;")) {
            float value = args instanceof VaList
                    ? ((VaList) args).getFloatArg(0)
                    : ((VarArg) args).getFloatArg(0);
            return new StringObject(vm, String.valueOf(value));
        }
        if (signature.equals("java/lang/String->valueOf(J)Ljava/lang/String;")) {
            long value = args instanceof VaList
                    ? ((VaList) args).getLongArg(0)
                    : ((VarArg) args).getLongArg(0);
            return new StringObject(vm, String.valueOf(value));
        }
        return null;
    }

    private DvmObject<?> systemPropertyGet(BaseVM vm, String signature, Object args) {
        boolean oneArg = signature.equals("android/os/SystemProperties->get(Ljava/lang/String;)Ljava/lang/String;");
        boolean twoArg = signature.equals(
                "android/os/SystemProperties->get(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
        if (!oneArg && !twoArg) {
            return null;
        }
        DvmObject<?> keyObj;
        DvmObject<?> defObj = null;
        if (args instanceof VaList) {
            keyObj = ((VaList) args).getObjectArg(0);
            if (twoArg) {
                defObj = ((VaList) args).getObjectArg(1);
            }
        } else {
            keyObj = ((VarArg) args).getObjectArg(0);
            if (twoArg) {
                defObj = ((VarArg) args).getObjectArg(1);
            }
        }
        String key = keyObj == null ? "" : String.valueOf(keyObj.getValue());
        String def = defObj == null ? "" : String.valueOf(defObj.getValue());
        String value = null;
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null) {
            value = config.getAndroidProperty(key);
        }
        if (value == null) {
            value = def;
        }
        System.out.println("SystemProperties.get " + key + "=" + value);
        return new StringObject(vm, value);
    }

    private static DvmObject<?> handleGetPackageInfo(BaseVM vm, String signature, Object args) {
        if (!signature.equals(
                "android/content/pm/PackageManager->getPackageInfo(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;")) {
            return null;
        }
        DvmObject<?> nameObj;
        int flags;
        if (args instanceof VaList) {
            nameObj = ((VaList) args).getObjectArg(0);
            flags = ((VaList) args).getIntArg(1);
        } else {
            nameObj = ((VarArg) args).getObjectArg(0);
            flags = ((VarArg) args).getIntArg(1);
        }
        String name = nameObj == null ? PACKAGE : String.valueOf(nameObj.getValue());
        return new PackageInfo(vm, name, flags);
    }

    private static Boolean mapBoolean(DvmObject<?> dvmObject, String signature, Object args) {
        if (signature.equals("java/util/Map->containsKey(Ljava/lang/Object;)Z")
                || signature.equals("java/util/HashMap->containsKey(Ljava/lang/Object;)Z")) {
            Map<?, ?> map = (Map<?, ?>) dvmObject.getValue();
            return map.containsKey(objectArgValue(args, 0));
        }
        if (signature.equals("java/util/Iterator->hasNext()Z")) {
            Iterator<?> it = (Iterator<?>) dvmObject.getValue();
            return it.hasNext();
        }
        if (signature.equals("java/util/Map->isEmpty()Z")
                || signature.equals("java/util/HashMap->isEmpty()Z")) {
            Map<?, ?> map = (Map<?, ?>) dvmObject.getValue();
            return map.isEmpty();
        }
        if (signature.equals("android/view/Display->isValid()Z")
                || signature.equals("android/view/Display->hasAccess(I)Z")) {
            return true;
        }
        if (signature.contains("SharedPreferences$Editor->commit()Z")) {
            return true;
        }
        if (signature.contains("SharedPreferences->contains(Ljava/lang/String;)Z")
                && dvmObject.getValue() instanceof Map) {
            Object key = objectArgValue(args, 0);
            return ((Map<?, ?>) dvmObject.getValue()).containsKey(key == null ? "" : String.valueOf(key));
        }
        return handleCollectionBoolean(dvmObject, signature, args);
    }

    private static Integer stubStaticInt(String signature) {
        if (signature.equals("android/os/UserHandle->myUserId()I")) {
            return 0;
        }
        if (signature.equals("android/content/Context->MODE_PRIVATE:I")
                || signature.equals("android/content/ContextWrapper->MODE_PRIVATE:I")
                || signature.equals("android/app/Application->MODE_PRIVATE:I")) {
            return 0;
        }
        return null;
    }

    private static Integer applicationInfoFlag(String signature) {
        switch (signature) {
            case "android/content/pm/ApplicationInfo->FLAG_SYSTEM:I":
                return 1;
            case "android/content/pm/ApplicationInfo->FLAG_DEBUGGABLE:I":
                return 2;
            case "android/content/pm/ApplicationInfo->FLAG_HAS_CODE:I":
                return 4;
            case "android/content/pm/ApplicationInfo->FLAG_PERSISTENT:I":
                return 8;
            case "android/content/pm/ApplicationInfo->FLAG_FACTORY_TEST:I":
                return 0x10;
            case "android/content/pm/ApplicationInfo->FLAG_ALLOW_TASK_REPARENTING:I":
                return 0x20;
            case "android/content/pm/ApplicationInfo->FLAG_ALLOW_CLEAR_USER_DATA:I":
                return 0x40;
            case "android/content/pm/ApplicationInfo->FLAG_UPDATED_SYSTEM_APP:I":
                return 0x80;
            case "android/content/pm/ApplicationInfo->FLAG_TEST_ONLY:I":
                return 0x100;
            case "android/content/pm/ApplicationInfo->FLAG_SUPPORTS_SMALL_SCREENS:I":
                return 0x200;
            case "android/content/pm/ApplicationInfo->FLAG_SUPPORTS_NORMAL_SCREENS:I":
                return 0x400;
            case "android/content/pm/ApplicationInfo->FLAG_SUPPORTS_LARGE_SCREENS:I":
                return 0x800;
            case "android/content/pm/ApplicationInfo->FLAG_RESIZEABLE_FOR_SCREENS:I":
                return 0x1000;
            case "android/content/pm/ApplicationInfo->FLAG_SUPPORTS_SCREEN_DENSITIES:I":
                return 0x2000;
            case "android/content/pm/ApplicationInfo->FLAG_VM_SAFE_MODE:I":
                return 0x4000;
            case "android/content/pm/ApplicationInfo->FLAG_ALLOW_BACKUP:I":
                return 0x8000;
            case "android/content/pm/ApplicationInfo->FLAG_KILL_AFTER_RESTORE:I":
                return 0x10000;
            case "android/content/pm/ApplicationInfo->FLAG_RESTORE_ANY_VERSION:I":
                return 0x20000;
            case "android/content/pm/ApplicationInfo->FLAG_EXTERNAL_STORAGE:I":
                return 0x40000;
            case "android/content/pm/ApplicationInfo->FLAG_SUPPORTS_XLARGE_SCREENS:I":
                return 0x80000;
            case "android/content/pm/ApplicationInfo->FLAG_LARGE_HEAP:I":
                return 0x100000;
            case "android/content/pm/ApplicationInfo->FLAG_STOPPED:I":
                return 0x200000;
            case "android/content/pm/ApplicationInfo->FLAG_SUPPORTS_RTL:I":
                return 0x400000;
            case "android/content/pm/ApplicationInfo->FLAG_INSTALLED:I":
                return 0x800000;
            case "android/content/pm/ApplicationInfo->FLAG_IS_DATA_ONLY:I":
                return 0x1000000;
            case "android/content/pm/ApplicationInfo->FLAG_IS_GAME:I":
                return 0x2000000;
            case "android/content/pm/ApplicationInfo->FLAG_FULL_BACKUP_ONLY:I":
                return 0x4000000;
            default:
                return null;
        }
    }

    private static boolean isDisplayMetrics(String signature) {
        return signature.equals("android/content/res/Resources->getDisplayMetrics()Landroid/util/DisplayMetrics;");
    }

    private DvmObject<?> newDisplayMetrics(BaseVM vm) {
        int[] size = displaySize();
        Map<String, Integer> metrics = new LinkedHashMap<String, Integer>();
        metrics.put("widthPixels", size[0]);
        metrics.put("heightPixels", size[1]);
        metrics.put("densityDpi", displayDensityDpi());
        return vm.resolveClass("android/util/DisplayMetrics").newObject(metrics);
    }

    @SuppressWarnings("unchecked")
    private static Integer displayMetricField(DvmObject<?> dvmObject, String signature) {
        if (!(dvmObject.getValue() instanceof Map)) {
            return null;
        }
        Map<String, Integer> metrics = (Map<String, Integer>) dvmObject.getValue();
        if (signature.equals("android/util/DisplayMetrics->widthPixels:I")) {
            return metrics.get("widthPixels");
        }
        if (signature.equals("android/util/DisplayMetrics->heightPixels:I")) {
            return metrics.get("heightPixels");
        }
        if (signature.equals("android/util/DisplayMetrics->densityDpi:I")) {
            return metrics.get("densityDpi");
        }
        return null;
    }

    private static boolean isGetResources(String signature) {
        return signature.equals("android/content/Context->getResources()Landroid/content/res/Resources;")
                || signature.equals("android/content/ContextWrapper->getResources()Landroid/content/res/Resources;")
                || signature.equals("android/app/Application->getResources()Landroid/content/res/Resources;")
                || signature.equals("android/app/Activity->getResources()Landroid/content/res/Resources;");
    }

    private static boolean isPackageName(String signature) {
        return signature.equals("android/content/Context->getPackageName()Ljava/lang/String;")
                || signature.equals("android/content/ContextWrapper->getPackageName()Ljava/lang/String;")
                || signature.equals("android/app/Application->getPackageName()Ljava/lang/String;")
                || signature.equals("android/app/Activity->getPackageName()Ljava/lang/String;");
    }

    private static boolean isSparseGet(String signature) {
        return signature.equals("android/util/SparseArray->get(I)Ljava/lang/Object;")
                || signature.equals("android/util/SparseArray->get(ILjava/lang/Object;)Ljava/lang/Object;");
    }

    private static boolean isSparsePut(String signature) {
        return signature.equals("android/util/SparseArray->put(ILjava/lang/Object;)V")
                || signature.equals("android/util/SparseArray->append(ILjava/lang/Object;)V");
    }

    private static boolean isSparseSize(String signature) {
        return signature.equals("android/util/SparseArray->size()I");
    }

    private DvmObject<?> handleSparseGet(BaseVM vm, DvmObject<?> dvmObject, String signature, int key) {
        if (!isSparseGet(signature)) {
            return null;
        }
        Object value = asSparse(dvmObject).get(key);
        if (value instanceof DvmObject) {
            return (DvmObject<?>) value;
        }
        if (value instanceof byte[]) {
            return new ByteArray(vm, (byte[]) value);
        }
        if (value instanceof Integer) {
            return DvmInteger.valueOf(vm, (Integer) value);
        }
        if (value instanceof String) {
            return new StringObject(vm, (String) value);
        }
        return null;
    }

    private boolean handleSparsePut(DvmObject<?> dvmObject, String signature, int key, DvmObject<?> value) {
        if (!signature.equals("android/util/SparseArray->put(ILjava/lang/Object;)V")
                && !signature.equals("android/util/SparseArray->append(ILjava/lang/Object;)V")) {
            return false;
        }
        asSparse(dvmObject).put(key, value);
        System.out.println("SparseArray.put " + key + " -> " + stringify(value)
                + (unwrap(value) instanceof byte[]
                ? " bytes=" + ((byte[]) unwrap(value)).length : ""));
        return true;
    }

    private static boolean isIgnoredVoid(String signature) {
        return signature.contains("IWindowManager->")
                || signature.contains("IDisplayManager->")
                || signature.contains("WindowManager->")
                || signature.contains("DisplayManager->");
    }

    private DvmObject<?> handleStubAsInterface(BaseVM vm, String signature) {
        int idx = signature.indexOf("$Stub->asInterface(");
        if (idx < 0) {
            return null;
        }
        String iface = signature.substring(0, idx);
        System.out.println("asInterface " + iface);
        return vm.resolveClass(iface).newObject("binder");
    }

    private boolean handleDisplaySize(DvmObject<?> dvmObject, String signature, Object args) {
        if (!signature.contains("getRealSize")
                && !signature.contains("getSize")
                && !signature.contains("getInitialDisplaySize")
                && !signature.contains("getBaseDisplaySize")
                && !signature.contains("getRealDisplaySize")) {
            return false;
        }
        if (!signature.contains("Landroid/graphics/Point;")) {
            return false;
        }
        DvmObject<?> pointObj = firstPointArg(args);
        if (pointObj == null) {
            return false;
        }
        PointValue point = (PointValue) pointObj.getValue();
        boolean physical = signature.contains("getReal")
                || signature.contains("getInitial")
                || signature.contains("getBase");
        int[] size = physical ? displayPhysicalSize() : displaySize();
        point.x = size[0];
        point.y = size[1];
        System.out.println(signature + " -> " + point.x + "x" + point.y);
        return true;
    }

    private static DvmObject<?> firstPointArg(Object args) {
        for (int i = 0; i < 3; i++) {
            try {
                DvmObject<?> obj = args instanceof VaList
                        ? ((VaList) args).getObjectArg(i)
                        : ((VarArg) args).getObjectArg(i);
                if (obj != null && obj.getValue() instanceof PointValue) {
                    return obj;
                }
            } catch (RuntimeException ignored) {
                // not an object arg
            }
        }
        return null;
    }

    private Integer handleDisplayInt(String signature) {
        if (signature.equals("android/view/Display->getWidth()I")) {
            return displayPhysicalSize()[0];
        }
        if (signature.equals("android/view/Display->getHeight()I")) {
            return displayPhysicalSize()[1];
        }
        if (signature.equals("android/view/Display->getRotation()I")
                || signature.contains("IWindowManager->getRotation()I")
                || signature.contains("IWindowManager->getDefaultDisplayRotation()I")) {
            return 0;
        }
        if (signature.equals("android/view/Display->getType()I")) {
            return 1; // TYPE_INTERNAL
        }
        if (signature.equals("android/view/Display->getDisplayId()I")) {
            return 0;
        }
        if (signature.equals("android/view/Display->getFlags()I")) {
            return displayFlags();
        }
        if (signature.equals("android/view/Display->getState()I")) {
            return 2; // STATE_ON
        }
        if (signature.equals("android/view/Display->getOwnerUid()I")) {
            return 0;
        }
        if (signature.equals("android/view/Display->getLayerStack()I")) {
            return 0;
        }
        return null;
    }

    private static boolean isDisplayObject(String signature) {
        return signature.equals("android/view/Display->getName()Ljava/lang/String;")
                || signature.equals("android/view/Display->getUniqueId()Ljava/lang/String;")
                || signature.equals("android/view/Display->getOwnerPackageName()Ljava/lang/String;");
    }

    private DvmObject<?> handleDisplayObject(BaseVM vm, String signature) {
        if (signature.equals("android/view/Display->getName()Ljava/lang/String;")) {
            return new StringObject(vm, displayName());
        }
        if (signature.equals("android/view/Display->getUniqueId()Ljava/lang/String;")) {
            return new StringObject(vm, "local:0");
        }
        if (signature.equals("android/view/Display->getOwnerPackageName()Ljava/lang/String;")) {
            return null;
        }
        return null;
    }

    private static Integer displayTypeFlag(String signature) {
        if (signature.equals("android/view/Display->DEFAULT_DISPLAY:I")) {
            return 0;
        }
        if (signature.equals("android/view/Display->INVALID_DISPLAY:I")) {
            return -1;
        }
        if (signature.equals("android/view/Display->TYPE_UNKNOWN:I")) {
            return 0;
        }
        if (signature.equals("android/view/Display->TYPE_INTERNAL:I")) {
            return 1;
        }
        if (signature.equals("android/view/Display->TYPE_EXTERNAL:I")) {
            return 2;
        }
        if (signature.equals("android/view/Display->TYPE_WIFI:I")) {
            return 3;
        }
        if (signature.equals("android/view/Display->TYPE_OVERLAY:I")) {
            return 4;
        }
        if (signature.equals("android/view/Display->TYPE_VIRTUAL:I")) {
            return 5;
        }
        if (signature.equals("android/view/Display->FLAG_SUPPORTS_PROTECTED_BUFFERS:I")) {
            return 1;
        }
        if (signature.equals("android/view/Display->FLAG_SECURE:I")) {
            return 2;
        }
        if (signature.equals("android/view/Display->FLAG_PRIVATE:I")) {
            return 4;
        }
        if (signature.equals("android/view/Display->FLAG_PRESENTATION:I")) {
            return 8;
        }
        if (signature.equals("android/view/Display->FLAG_ROUND:I")) {
            return 16;
        }
        if (signature.equals("android/view/Display->STATE_UNKNOWN:I")) {
            return 0;
        }
        if (signature.equals("android/view/Display->STATE_OFF:I")) {
            return 1;
        }
        if (signature.equals("android/view/Display->STATE_ON:I")) {
            return 2;
        }
        if (signature.equals("android/hardware/display/DisplayManager->DISPLAY_CATEGORY_PRESENTATION:I")
                || signature.contains("DISPLAY_CATEGORY")) {
            return 0;
        }
        return null;
    }

    private int[] displaySize() {
        int width = 1080;
        int height = 2219;
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.isAndroidDisplayConfigured() && config.getAndroidDisplayConfig() != null) {
            width = config.getAndroidDisplayConfig().getWidthPixels();
            height = config.getAndroidDisplayConfig().getHeightPixels();
        }
        return new int[]{width, height};
    }

    /** Physical panel size used by Display.getWidth/Height / getDisplays (#131). */
    private int[] displayPhysicalSize() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.isAndroidDisplaysConfigured()) {
            java.util.List<TraceEnvironmentConfig.AndroidDisplayEntryConfig> displays =
                    config.getAndroidDisplays();
            if (!displays.isEmpty()) {
                return new int[]{displays.get(0).getWidthPixels(), displays.get(0).getHeightPixels()};
            }
        }
        return new int[]{1080, 2400};
    }

    private int displayFlags() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.isAndroidDisplaysConfigured()) {
            java.util.List<TraceEnvironmentConfig.AndroidDisplayEntryConfig> displays =
                    config.getAndroidDisplays();
            if (!displays.isEmpty() && displays.get(0).getFlags() != 0) {
                return displays.get(0).getFlags();
            }
        }
        return 131; // FLAG_SECURE | FLAG_SUPPORTS_PROTECTED_BUFFERS | FLAG_TRUSTED
    }

    private String displayName() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.isAndroidDisplaysConfigured()) {
            java.util.List<TraceEnvironmentConfig.AndroidDisplayEntryConfig> displays =
                    config.getAndroidDisplays();
            if (!displays.isEmpty()) {
                String name = displays.get(0).getName();
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        }
        return "内置屏幕";
    }

    private double batteryCapacityMah() {
        TraceEnvironmentConfig env = TraceEnvironmentConfig.get(emulator);
        if (env != null && env.isAndroidPowerProfileConfigured()
                && env.getAndroidPowerProfileConfig() != null
                && env.getAndroidPowerProfileConfig().isAveragePowerConfigured()) {
            Double value = env.getAndroidPowerProfileConfig().getAveragePower("battery.capacity");
            if (value != null) {
                return value.doubleValue();
            }
        }
        return 4614.0d;
    }

    private int displayDensityDpi() {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.isAndroidDisplayConfigured()
                && config.getAndroidDisplayConfig() != null
                && config.getAndroidDisplayConfig().isDensityDpiConfigured()) {
            return config.getAndroidDisplayConfig().getDensityDpi();
        }
        return 420;
    }

    private static int intArg(VarArg varArg, int index) {
        return varArg.getIntArg(index);
    }

    private DvmObject<?> handleNewCollection(BaseVM vm, DvmClass dvmClass, String signature, Object args) {
        if (signature.equals("android/util/SparseArray-><init>()V")
                || signature.equals("android/util/SparseArray-><init>(I)V")) {
            return dvmClass.newObject(new LinkedHashMap<Integer, Object>());
        }
        if (signature.equals("java/util/HashMap-><init>()V")
                || signature.equals("java/util/HashMap-><init>(I)V")
                || signature.equals("java/util/LinkedHashMap-><init>()V")
                || signature.equals("java/util/LinkedHashMap-><init>(I)V")) {
            return dvmClass.newObject(new LinkedHashMap<Object, Object>());
        }
        if (signature.equals("java/util/HashSet-><init>()V")
                || signature.equals("java/util/LinkedHashSet-><init>()V")
                || signature.equals("java/util/HashSet-><init>(I)V")
                || signature.equals("java/util/LinkedHashSet-><init>(I)V")) {
            return dvmClass.newObject(new LinkedHashSet<Object>());
        }
        if (signature.equals("java/util/HashSet-><init>(Ljava/util/Collection;)V")
                || signature.equals("java/util/LinkedHashSet-><init>(Ljava/util/Collection;)V")) {
            return dvmClass.newObject(new LinkedHashSet<Object>(asJavaCollection(objectArgValue(args, 0))));
        }
        if (signature.equals("java/util/ArrayList-><init>()V")
                || signature.equals("java/util/ArrayList-><init>(I)V")) {
            return dvmClass.newObject(new ArrayList<Object>());
        }
        if (signature.equals("java/util/ArrayList-><init>(Ljava/util/Collection;)V")) {
            return dvmClass.newObject(new ArrayList<Object>(asJavaCollection(objectArgValue(args, 0))));
        }
        if (signature.equals("com/android/internal/os/PowerProfile-><init>(Landroid/content/Context;)V")) {
            return dvmClass.newObject("PowerProfile");
        }
        if (signature.equals("android/graphics/Point-><init>()V")) {
            return dvmClass.newObject(new PointValue());
        }
        if (signature.equals("android/graphics/Point-><init>(II)V")) {
            PointValue point = new PointValue();
            point.x = intArg(args, 0);
            point.y = intArg(args, 1);
            return dvmClass.newObject(point);
        }
        return null;
    }

    private DvmObject<?> handleAllocCollection(BaseVM vm, DvmClass dvmClass, String signature) {
        if (signature.equals("java/util/HashSet->allocObject")
                || signature.equals("java/util/LinkedHashSet->allocObject")) {
            return dvmClass.newObject(new LinkedHashSet<Object>());
        }
        if (signature.equals("java/util/HashMap->allocObject")
                || signature.equals("java/util/LinkedHashMap->allocObject")) {
            return dvmClass.newObject(new LinkedHashMap<Object, Object>());
        }
        if (signature.equals("java/util/ArrayList->allocObject")) {
            return dvmClass.newObject(new ArrayList<Object>());
        }
        return null;
    }

    private static Boolean handleCollectionBoolean(DvmObject<?> dvmObject, String signature, Object args) {
        Object value = dvmObject.getValue();
        if (signature.endsWith("->add(Ljava/lang/Object;)Z") && value instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<Object> collection = (Collection<Object>) value;
            return collection.add(objectArgValue(args, 0));
        }
        if (signature.endsWith("->contains(Ljava/lang/Object;)Z") && value instanceof Collection) {
            return ((Collection<?>) value).contains(objectArgValue(args, 0));
        }
        if (signature.endsWith("->remove(Ljava/lang/Object;)Z") && value instanceof Collection) {
            return ((Collection<?>) value).remove(objectArgValue(args, 0));
        }
        if (signature.endsWith("->isEmpty()Z") && value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }
        if (signature.endsWith("->containsKey(Ljava/lang/Object;)Z") && value instanceof Map) {
            return ((Map<?, ?>) value).containsKey(objectArgValue(args, 0));
        }
        return null;
    }

    private static Integer handleCollectionInt(DvmObject<?> dvmObject, String signature) {
        Object value = dvmObject.getValue();
        if (signature.endsWith("->size()I") && value instanceof Collection) {
            return ((Collection<?>) value).size();
        }
        if (signature.endsWith("->size()I") && value instanceof Map) {
            return ((Map<?, ?>) value).size();
        }
        return null;
    }

    private DvmObject<?> handleCollectionObject(BaseVM vm, DvmObject<?> dvmObject, String signature, Object args) {
        Object value = dvmObject.getValue();
        if (signature.endsWith("->iterator()Ljava/util/Iterator;") && value instanceof Iterable) {
            return vm.resolveClass("java/util/Iterator").newObject(((Iterable<?>) value).iterator());
        }
        if (signature.equals("java/util/Iterator->next()Ljava/lang/Object;") && value instanceof Iterator) {
            return wrapValue(vm, ((Iterator<?>) value).next());
        }
        if (signature.endsWith("->toArray()[Ljava/lang/Object;") && value instanceof Collection) {
            return ProxyDvmObject.createObject(vm, ((Collection<?>) value).toArray());
        }
        if ((signature.endsWith("->get(Ljava/lang/Object;)Ljava/lang/Object;")
                || signature.endsWith("->remove(Ljava/lang/Object;)Ljava/lang/Object;"))
                && value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) value;
            Object key = objectArgValue(args, 0);
            Object result = signature.contains("->remove") ? map.remove(key) : map.get(key);
            return wrapValue(vm, result);
        }
        if (signature.endsWith("->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                && value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) value;
            return wrapValue(vm, map.put(objectArgValue(args, 0), objectArgValue(args, 1)));
        }
        if (signature.endsWith("->keySet()Ljava/util/Set;") && value instanceof Map) {
            return vm.resolveClass("java/util/HashSet", vm.resolveClass("java/util/Set"))
                    .newObject(((Map<?, ?>) value).keySet());
        }
        if (signature.endsWith("->entrySet()Ljava/util/Set;") && value instanceof Map) {
            return vm.resolveClass("java/util/Set").newObject(((Map<?, ?>) value).entrySet());
        }
        if (signature.endsWith("->values()Ljava/util/Collection;") && value instanceof Map) {
            return vm.resolveClass("java/util/Collection").newObject(((Map<?, ?>) value).values());
        }
        if (signature.equals("java/util/Map$Entry->getKey()Ljava/lang/Object;") && value instanceof Map.Entry) {
            return wrapValue(vm, ((Map.Entry<?, ?>) value).getKey());
        }
        if (signature.equals("java/util/Map$Entry->getValue()Ljava/lang/Object;") && value instanceof Map.Entry) {
            return wrapValue(vm, ((Map.Entry<?, ?>) value).getValue());
        }
        if (signature.endsWith("->get(I)Ljava/lang/Object;") && value instanceof List) {
            return wrapValue(vm, ((List<?>) value).get(intArg(args, 0)));
        }
        return null;
    }

    private static boolean isHandledCollectionObject(String signature) {
        return signature.endsWith("->iterator()Ljava/util/Iterator;")
                || signature.equals("java/util/Iterator->next()Ljava/lang/Object;")
                || signature.endsWith("->toArray()[Ljava/lang/Object;")
                || signature.endsWith("->get(Ljava/lang/Object;)Ljava/lang/Object;")
                || signature.endsWith("->remove(Ljava/lang/Object;)Ljava/lang/Object;")
                || signature.endsWith("->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                || signature.endsWith("->keySet()Ljava/util/Set;")
                || signature.endsWith("->entrySet()Ljava/util/Set;")
                || signature.endsWith("->values()Ljava/util/Collection;")
                || signature.equals("java/util/Map$Entry->getKey()Ljava/lang/Object;")
                || signature.equals("java/util/Map$Entry->getValue()Ljava/lang/Object;")
                || signature.endsWith("->get(I)Ljava/lang/Object;");
    }

    private static boolean handleCollectionVoid(DvmObject<?> dvmObject, String signature, Object args) {
        Object value = dvmObject.getValue();
        if ((signature.equals("java/util/HashSet-><init>()V")
                || signature.equals("java/util/LinkedHashSet-><init>()V")
                || signature.equals("java/util/HashMap-><init>()V")
                || signature.equals("java/util/LinkedHashMap-><init>()V")
                || signature.equals("java/util/ArrayList-><init>()V")
                || signature.equals("android/util/SparseArray-><init>()V")
                || signature.endsWith("-><init>(I)V"))
                && value != null) {
            return true;
        }
        if (signature.endsWith("->clear()V") && value instanceof Collection) {
            ((Collection<?>) value).clear();
            return true;
        }
        if (signature.endsWith("->clear()V") && value instanceof Map) {
            ((Map<?, ?>) value).clear();
            return true;
        }
        if (signature.endsWith("->add(ILjava/lang/Object;)V") && value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            list.add(intArg(args, 0), objectArgValue(args, 1));
            return true;
        }
        return false;
    }

    private DvmObject<?> wrapValue(BaseVM vm, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof DvmObject) {
            return (DvmObject<?>) value;
        }
        if (value instanceof String) {
            return new StringObject(vm, (String) value);
        }
        if (value instanceof Integer) {
            return DvmInteger.valueOf(vm, (Integer) value);
        }
        if (value instanceof Long) {
            return DvmLong.valueOf(vm, (Long) value);
        }
        if (value instanceof Boolean) {
            return DvmBoolean.valueOf(vm, (Boolean) value);
        }
        if (value instanceof byte[]) {
            return new ByteArray(vm, (byte[]) value);
        }
        if (value instanceof Iterator) {
            return vm.resolveClass("java/util/Iterator").newObject(value);
        }
        if (value instanceof Set) {
            return vm.resolveClass("java/util/HashSet", vm.resolveClass("java/util/Set")).newObject(value);
        }
        if (value instanceof Map.Entry) {
            return vm.resolveClass("java/util/Map$Entry").newObject(value);
        }
        if (value instanceof Map) {
            return vm.resolveClass("java/util/HashMap", vm.resolveClass("java/util/Map")).newObject(value);
        }
        if (value instanceof List) {
            return vm.resolveClass("java/util/ArrayList", vm.resolveClass("java/util/List")).newObject(value);
        }
        return ProxyDvmObject.createObject(vm, value);
    }

    private static Object objectArgValue(Object args, int index) {
        DvmObject<?> obj = args instanceof VaList
                ? ((VaList) args).getObjectArg(index)
                : ((VarArg) args).getObjectArg(index);
        return obj == null ? null : unwrap(obj);
    }

    private static int intArg(Object args, int index) {
        return args instanceof VaList
                ? ((VaList) args).getIntArg(index)
                : ((VarArg) args).getIntArg(index);
    }

    private static String classNameOf(String signature) {
        int arrow = signature.indexOf("->");
        return arrow < 0 ? signature : signature.substring(0, arrow);
    }

    private static Collection<?> asJavaCollection(Object value) {
        if (value instanceof Collection) {
            return (Collection<?>) value;
        }
        return java.util.Collections.emptyList();
    }
}

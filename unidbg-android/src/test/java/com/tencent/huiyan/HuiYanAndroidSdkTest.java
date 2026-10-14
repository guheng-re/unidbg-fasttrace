package com.tencent.huiyan;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.EmulatorBuilder;
import com.github.unidbg.Module;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.debugger.BreakPointCallback;
import com.github.unidbg.env.TraceEnvironmentConfig;
import unicorn.Arm64Const;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.ElfLibraryFile;
import com.github.unidbg.linux.android.dvm.AbstractJni;
import com.github.unidbg.linux.android.dvm.BaseVM;
import com.github.unidbg.linux.android.dvm.DalvikModule;
import com.github.unidbg.linux.android.dvm.DvmClass;
import com.github.unidbg.linux.android.dvm.DvmObject;
import com.github.unidbg.linux.android.dvm.StringObject;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.linux.android.dvm.VaList;
import com.github.unidbg.linux.android.dvm.VarArg;
import com.github.unidbg.linux.android.dvm.api.AssetManager;
import com.github.unidbg.linux.android.dvm.api.Bitmap;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.spi.LibraryFile;
import com.github.unidbg.virtualmodule.android.AndroidModule;
import com.github.unidbg.virtualmodule.android.JniGraphics;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.security.MessageDigest;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * unidbg script 2/2 — libhuiyanandroidsdk.so.
 *
 * Native only (no APK Java):
 *   JNI_OnLoad
 *   → getVersion
 *   → initDeviceRiskData / initFaceIdToken / initPubKey / initNonce
 *   → getStartLivenessReqBody
 *   → YT initAuth + initYtLiveness
 *   → updateFrame(NV21 from zip jpegs) → innerCallBack 15/16
 *   → encryptVideoData(zip video_report.mp4)
 */
public class HuiYanAndroidSdkTest extends AbstractJni {

    private static final File LIB_DIR = new File(
            "D:\\project\\12306\\12306-5.9.6.6\\12306v5.9.6.6\\lib\\arm64-v8a");
    private static final File APK_DIR = new File(
            "D:\\project\\12306\\12306-5.9.6.6\\12306v5.9.6.6");
    private static final File ASSETS = new File(APK_DIR, "assets");
    private static final File LICENSE = new File(ASSETS, "YTFaceSDK.license");
    private static final File HUIYAN_SO = new File(LIB_DIR, "libhuiyanandroidsdk.so");
    private static final File YT_SO = new File(LIB_DIR, "libYTCommonLiveness.so");
    private static final File PROFILE = new File(
            "D:\\project\\unidbg-fasttrace\\profiles\\pixel6-oriole-13\\device-fingerprint.json");
    private static final JSONObject PROFILE_JSON = loadProfileJson(PROFILE);
    private static final File OUT_DIR = new File(
            "D:\\project\\12306\\12306-5.9.6.6\\scripts\\ida_out");
    private static final File RISK_READY = new File(OUT_DIR, "unidbg_8123_risk.txt");
    private static final File RISK_FRONT = new File(OUT_DIR, "unidbg_8123_front.txt");
    private static final File RISK_PLAIN = new File(OUT_DIR, "unidbg_8123_plain.bin");
    private static final File RISK_HTTP = new File(OUT_DIR, "unidbg_8123.bin");
    private static final File DEFAULT_ZIP = new File(
            "D:\\project\\12306\\12306-5.9.6.6\\CS_11_188c6034f6ca47f78ac9930b9330d243_20260818_133608.zip");

    private static final String PACKAGE = "com.MobileTicket";
    private static final String NATIVE_API = "com/tencent/cloud/huiyan/api/HuiYanNativeApi";
    private static final String YT_API = "com/tencent/youtu/liveness/YTCommonInterface";
    private static final String CACHE_VFS = "/data/data/com.MobileTicket/cache";

    private static final String DEFAULT_PUBKEY =
            "04831b62b0b4f66cbd374b03d025dad9f1e4d3e19d2ed6180af3f169bf9de51b600ba6be92d06613a197db786bcfc8da5fbbe154af9566d3bed2ad372395b24843";
    private static final String DEFAULT_NONCE = "8c9421bhk367l6310q";

    private AndroidEmulator emulator;
    private VM vm;
    private Module huiyanModule;
    private DvmClass nativeApi;
    private DvmClass ytApi;
    private final Map<Integer, String> callbacks = new LinkedHashMap<Integer, String>();
    /** NV21 canvas. Official AuthUiConfig is 480P; cb25 may report TextureView 1080x1440. */
    private int[] cameraSize = new int[] {480, 640};
    private String pendingCameraReply;
    private String poseHint = "";
    private boolean wantReflect;
    /** Camera TextureView held by setCurrentTextureView for AndroidShotHelper.getBitmap. */
    private DvmObject<?> textureView;
    private BufferedImage lastPreview;
    /** Original camera JPEG NV21, before letterbox. ShotHelper covers this to 3:4. */
    private Nv21Frame lastRawNv21;
    private int shotCount;
    /** Last values we told SO were applied (field14 f4/f10). */
    private float appliedExposure;
    private int appliedWhiteBalance;
    private int appliedZoom = 100;
    /** Next TextureView.getBitmap is a field14 cam ref (comment b/e). Else return null for field15. */
    private boolean allowCamShot;
    /** Session colorData from startLiveness: AARRGGBB flashes. */
    private int[] flashArgb;
    private int[] flashLoops;
    private int flashUnitMs = 120;
    private long reflectStartMs;
    private int liveCb2Argb;
    private long hostNanoAtCreate;
    private int frameTicks;
    private int pendingColorArgb;
    private boolean inStructure;
    private int structureTicks;
    /** Preallocated C string for NewStringUTF; malloc is illegal while the emu is running. */
    private long safeUtfBuf;
    private int utfPatchCount;
    private boolean postedSend;
    private boolean postedDo;
    /** Real Pixel FlashCam frames; skip synthetic tint. */
    private boolean liveFeed;
    private String sessionToken = "";
    private String sessionPubKey = "";
    private String sessionNonce = "";
    private String sessionRisk = "";
    private String sessionLight = "";
    /** PC-decrypted startLiveness inner JSON for phone initYtLiveness. */
    private String sessionLightPlain = "";
    private String sessionAesKeyHex = "";
    private String sessionAesIvHex = "";
    private File sitExportDir;
    /** SO-driven live camera on the Pixel. */
    private LiveCam liveCam;
    private boolean loggedFeed;
    /** Phone SO dumped raw NV21; do not letterbox or re-expose. */
    private boolean phoneRawNv21;
    private int protoDumpCount;
    /** Preallocated payload for skipaes-style ytLightData identity decrypt. */
    private long lightIdentityBuf;
    private boolean ytNeedEncrypt = true;
    /**
     * FlashCam sit that got send 0 used 480 + 0.92. Zip default is 0.72.
     */
    private static final double DEFAULT_FACE_FILL = 0.92;

    public static void main(String[] args) throws Exception {
        new HuiYanAndroidSdkTest().run();
    }

    @Test
    public void testStartLivenessBody() throws Exception {
        run();
    }

    private void run() throws Exception {
        if (!HUIYAN_SO.isFile()) {
            throw new IllegalStateException("missing so: " + HUIYAN_SO.getAbsolutePath());
        }
        if (!YT_SO.isFile()) {
            throw new IllegalStateException("missing so: " + YT_SO.getAbsolutePath());
        }

        EmulatorBuilder<AndroidEmulator> builder = AndroidEmulatorBuilder.for64Bit()
                .setProcessName(PACKAGE);
        if (PROFILE.isFile()) {
            builder = builder.setEnvironmentProfile(PROFILE);
            System.out.println("profile " + PROFILE.getAbsolutePath());
        } else {
            throw new IllegalStateException("missing profile: " + PROFILE.getAbsolutePath());
        }
        emulator = builder.addBackendFactory(new Unicorn2Factory(true)).build();
        emulator.getSyscallHandler().setEnableThreadDispatcher(true);
        hostNanoAtCreate = System.nanoTime();

        Memory memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23) {
            @Override
            public LibraryFile resolveLibrary(Emulator<?> emulator, String libraryName) {
                File local = new File(LIB_DIR, libraryName);
                if (local.isFile()) {
                    return new ElfLibraryFile(local, emulator.is64Bit());
                }
                return super.resolveLibrary(emulator, libraryName);
            }
        });

        File cacheDir = new File(emulator.getFileSystem().getRootDir(),
                "data/data/com.MobileTicket/cache");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IllegalStateException("mkdir cache failed: " + cacheDir);
        }
        copyYtModels(cacheDir);
        File overlayCache = new File(PROFILE.getParentFile(),
                "files/data/data/com.MobileTicket/cache");
        copyYtModels(overlayCache);

        vm = emulator.createDalvikVM(APK_DIR.isDirectory() ? APK_DIR : null);
        vm.setJni(this);
        vm.setVerbose(true);
        vm.setAssetResolver(this::resolveAsset);
        new AndroidModule(emulator, vm).register(memory);
        new JniGraphics(emulator, vm).register(memory);

        vm.resolveClass(NATIVE_API);
        vm.resolveClass(YT_API);
        nativeApi = vm.resolveClass(NATIVE_API);
        ytApi = vm.resolveClass(YT_API);

        System.out.println("loading " + YT_SO.getAbsolutePath() + " size=" + YT_SO.length());
        DalvikModule yt = vm.loadLibrary(YT_SO, true);
        yt.callJNI_OnLoad(emulator);
        System.out.println("YTCommonLiveness JNI_OnLoad ok base=0x"
                + Long.toHexString(yt.getModule().base));

        System.out.println("loading " + HUIYAN_SO.getAbsolutePath() + " size=" + HUIYAN_SO.length());
        DalvikModule hy = vm.loadLibrary(HUIYAN_SO, true);
        huiyanModule = hy.getModule();
        System.out.println("huiyanandroidsdk loaded base=0x" + Long.toHexString(huiyanModule.base)
                + " size=0x" + Long.toHexString(huiyanModule.size));
        hy.callJNI_OnLoad(emulator);
        System.out.println("huiyanandroidsdk JNI_OnLoad ok");

        String version = callString(nativeApi, "getVersion()Ljava/lang/String;");
        System.out.println("getVersion=" + version);
        writeOut("unidbg_huiyan_version.txt", version == null ? new byte[0]
                : version.getBytes(StandardCharsets.UTF_8));

        if ("true".equalsIgnoreCase(propertyOr("huiyan.dumpProto", "false"))) {
            hookDumpTnnProto();
            hookYtLightIdentity();
            String dumpToken = propertyOr("huiyan.faceidToken",
                    "289C78A8-CD54-431E-BDF1-0D2C107621FB");
            String dumpPub = propertyOr("huiyan.pubKey", DEFAULT_PUBKEY);
            String dumpNonce = propertyOr("huiyan.nonce", DEFAULT_NONCE);
            String dumpRisk = propertyOr("huiyan.riskData", loadRiskData());
            callInt(nativeApi, "initDeviceRiskData(Ljava/lang/String;)I", dumpRisk);
            callInt(nativeApi, "initFaceIdToken(Ljava/lang/String;)I", dumpToken);
            callInt(nativeApi, "initPubKey(Ljava/lang/String;)I", dumpPub);
            callInt(nativeApi, "initNonce(Ljava/lang/String;)I", dumpNonce);
            runYtAuth();
            String light = propertyOr("huiyan.lightPlain", "");
            if (light.isEmpty()) {
                light = readLightPlain();
            }
            if (light.isEmpty()) {
                light = readSitLightPlain();
            }
            if (light.isEmpty()) {
                throw new IllegalStateException(
                        "dumpProto needs plaintext light (ida_out encryptedRsp.json or sit light)");
            }
            ytNeedEncrypt = false;
            runYtLiveness(light);
            enableUpdateFrame();
            bindTextureView();
            byte[] nv21 = new byte[480 * 640 * 3 / 2];
            java.util.Arrays.fill(nv21, (byte) 128);
            try {
                int frc = nativeApi.callStaticJniMethodInt(emulator,
                        "updateFrame([BIIII[BIII)I",
                        new ByteArray(vm, nv21), 480, 640, 1, 1, null, 0, 0, 0);
                System.out.println("dumpProto updateFrame rc=" + frc);
            } catch (Exception e) {
                System.out.println("dumpProto updateFrame failed: " + e);
            }
            for (int i = 0; i < 16 && protoDumpCount == 0; i++) {
                pumpYtWorker("dumpProto");
            }
            System.out.println("dumpProto done count=" + protoDumpCount
                    + " lightChars=" + light.length());
            emulator.close();
            return;
        }

        if (propertyOr("huiyan.liveDir", "").isEmpty()
                && "true".equalsIgnoreCase(propertyOr("huiyan.live", "false"))) {
            liveCam = openLiveCam();
            liveFeed = true;
            System.out.println("FlashCam live early; sit in the green oval now");
        }

        String faceIdToken = propertyOr("huiyan.faceidToken",
                "289C78A8-CD54-431E-BDF1-0D2C107621FB");
        String pubKey = propertyOr("huiyan.pubKey", DEFAULT_PUBKEY);
        String nonce = propertyOr("huiyan.nonce", DEFAULT_NONCE);
        if (!"false".equalsIgnoreCase(propertyOr("huiyan.getConfig", "true"))) {
            String[] cfg = postGetConfig(faceIdToken);
            if (cfg[0] != null && !cfg[0].isEmpty()) {
                pubKey = cfg[0];
            }
            if (cfg[1] != null && !cfg[1].isEmpty()) {
                nonce = cfg[1];
            }
        }
        String risk = propertyOr("huiyan.riskData", loadRiskData());

        int riskRc = callInt(nativeApi, "initDeviceRiskData(Ljava/lang/String;)I", risk);
        int tokenRc = callInt(nativeApi, "initFaceIdToken(Ljava/lang/String;)I", faceIdToken);
        int pubRc = callInt(nativeApi, "initPubKey(Ljava/lang/String;)I", pubKey);
        int nonceRc = callInt(nativeApi, "initNonce(Ljava/lang/String;)I", nonce);
        nativeApi.callStaticJniMethod(emulator, "setRetry(I)V", 0);
        nativeApi.callStaticJniMethod(emulator, "setRetryIndex(I)V", 0);
        System.out.println("init rc risk=" + riskRc + " token=" + tokenRc
                + " pub=" + pubRc + " nonce=" + nonceRc
                + " riskChars=" + risk.length() + " token=" + faceIdToken);

        String packed = callString(nativeApi,
                "getStartLivenessReqBody(Ljava/lang/String;)Ljava/lang/String;", CACHE_VFS);
        if (packed == null || packed.isEmpty()) {
            throw new IllegalStateException("getStartLivenessReqBody returned empty");
        }
        int split = packed.indexOf("--");
        String body = split < 0 ? packed : packed.substring(0, split);
        String summary = split < 0 ? "" : packed.substring(split + 2);
        body = alignDeviceData(body);
        dumpSessionAesFromSo();
        sessionToken = faceIdToken;
        if ("true".equalsIgnoreCase(propertyOr("huiyan.emptyFrontSecond", "false"))) {
            String inner = decryptSessionBody(body);
            String replaced = replaceFrontSecond(inner, "");
            if (!replaced.equals(inner)) {
                String neuEnc = encryptSessionBody(replaced);
                String oldEnc = jsonStringField(body, "encryptedBody");
                if (!neuEnc.isEmpty() && !oldEnc.isEmpty()) {
                    body = body.replace(oldEnc, neuEnc);
                    summary = bodyDataSign(replaced, faceIdToken);
                    System.out.println("emptyFrontSecond firstKeep inner "
                            + inner.length() + "->" + replaced.length()
                            + " second " + frontSecondLen(inner) + "->0");
                }
            } else {
                System.out.println("emptyFrontSecond no-op");
            }
        }
        // APK HuiYanBaseApi.startLiveness: initDeviceRiskData(IyjbE blob) then
        // getStartLivenessReqBody. Do not swap in a post-JCE / Go-aligned front.
        packed = summary.isEmpty() ? body : body + "--" + summary;
        writeOut("unidbg_startLiveness.txt", packed.getBytes(StandardCharsets.UTF_8));
        writeOut("unidbg_startLiveness.json", body.getBytes(StandardCharsets.UTF_8));
        writeOut("unidbg_startLiveness_summary.txt", summary.getBytes(StandardCharsets.UTF_8));
        System.out.println("startLiveness body=" + body.length() + " summary=" + summary.length());
        int fd = body.indexOf("\"frontData\"");
        if (fd >= 0) {
            int q1 = body.indexOf('"', body.indexOf(':', fd) + 1);
            String front = q1 < 0 ? "" : body.substring(q1 + 1, Math.min(body.length(), q1 + 25));
            System.out.println("frontData prefix=" + front);
        }

        File zip = new File(propertyOr("huiyan.zip", DEFAULT_ZIP.getAbsolutePath()));
        Map<String, byte[]> zipEntries = readZip(zip);
        System.out.println("zip " + zip.getName() + " entries=" + zipEntries.size());

        boolean phoneSo = !"false".equalsIgnoreCase(propertyOr("huiyan.phoneSo", "true"));
        String encryptedRsp = propertyOr("huiyan.encryptedRsp", "");
        if (encryptedRsp.isEmpty() && !"false".equalsIgnoreCase(propertyOr("huiyan.post", "true"))) {
            encryptedRsp = postStartLiveness(body, summary);
        }
        if (encryptedRsp.isEmpty()) {
            encryptedRsp = loadLastEncryptedRsp();
        }
        if (encryptedRsp == null || encryptedRsp.isEmpty()) {
            throw new IllegalStateException("startLiveness encryptedRsp empty");
        }
        decryptStartLivenessRsp(encryptedRsp);
        loadSessionColors();
        sessionLightPlain = readLightPlain();
        if (phoneSo && (sessionLightPlain == null || sessionLightPlain.isEmpty())) {
            throw new IllegalStateException("PC decrypt encryptedRsp failed (no lightPlain)");
        }
        System.out.println("start light chars=" + (sessionLightPlain == null ? 0 : sessionLightPlain.length())
                + " needRetryRisk=" + (sessionLightPlain != null && sessionLightPlain.contains("needRetryRisk"))
                + " gzipStart=true");
        if (!phoneSo && !"true".equalsIgnoreCase(propertyOr("huiyan.continueSit", "false"))) {
            System.out.println("start-only (phoneSo=false), skip YT/send/do");
            return;
        }
        applyLivePreviewSize();
        sessionToken = faceIdToken;
        sessionPubKey = pubKey;
        sessionNonce = nonce;
        sessionRisk = risk;
        sessionLight = encryptedRsp;
        initSitExport();
        File liveDir = null;
        String reuse = propertyOr("huiyan.liveDir", "");
        if (!reuse.isEmpty()) {
            File reuseDir = new File(reuse);
            if (reuseDir.isDirectory()) {
                liveDir = reuseDir;
                liveFeed = true;
                System.out.println("reuse liveDir " + reuseDir.getAbsolutePath());
            }
        }
        boolean wantLive = liveDir == null
                && "true".equalsIgnoreCase(propertyOr("huiyan.live", "false"));
        if (wantLive) {
            if (liveCam == null) {
                liveCam = openLiveCam();
            }
            liveFeed = true;
        } else if (liveDir == null && !"false".equalsIgnoreCase(propertyOr("huiyan.flashCam", "true"))) {
            liveDir = captureFlashCam();
            liveFeed = liveDir != null;
            if (!liveFeed) {
                throw new IllegalStateException("FlashCam capture failed; not falling back to zip");
            }
        }

        if (phoneSo && liveDir != null) {
            importPhoneCallbacks(liveDir);
        }
        boolean skipYt = "true".equalsIgnoreCase(propertyOr("huiyan.skipYt", "false"))
                || (phoneSo && callbacks.containsKey(15));
        if (skipYt) {
            System.out.println("skip YT/updateFrame phoneSo=" + phoneSo
                    + " cb=" + callbacks.keySet());
            dumpAllCallbacks();
            dumpCallback(15, "unidbg_sendLiveness_phone.txt", "unidbg_sendLiveness_phone.json",
                    "unidbg_sendLiveness_phone_summary.txt");
            dumpCallback(16, "unidbg_doLiveness_phone.txt", "unidbg_doLiveness_phone.json",
                    "unidbg_doLiveness_phone_summary.txt");
            resignPhonePack(15);
            dumpCallback(15, "unidbg_sendLiveness.txt", "unidbg_sendLiveness.json",
                    "unidbg_sendLiveness_summary.txt");
            int sendCode = -1;
            if (!"false".equalsIgnoreCase(propertyOr("huiyan.postSend", "true"))) {
                postLivenessIfReady(15);
                sendCode = readPostedCode("unidbg_sendLiveness_http_rsp.txt");
                System.out.println("send code=" + sendCode);
            }
            if (sendCode == 0 && callbacks.containsKey(16)) {
                if ("true".equalsIgnoreCase(propertyOr("huiyan.rewriteDo", "false"))) {
                    rewriteDoPack();
                } else {
                    resignPhonePack(16);
                    dumpCallback(16, "unidbg_doLiveness.txt", "unidbg_doLiveness.json",
                            "unidbg_doLiveness_summary.txt");
                }
                postLivenessIfReady(16);
            } else if (sendCode != 0) {
                System.out.println("skip do because send code=" + sendCode);
            }
            exportSitFolder(liveDir);
            System.out.println("skip zip video (phone pack continues PC startLiveness)");
        } else {
            runYtAuth();
            runYtLiveness(encryptedRsp);
            enableUpdateFrame();
            bindTextureView();
            hookReflectStop();
            enableVirtualClock();
            pumpYtWorker("after initYtLiveness");
            if (liveCam != null) {
                runLivePump();
            } else if (liveFeed) {
                replayLiveFrames(liveDir);
            } else {
                replayZipFrames(zipEntries);
            }
            if (liveCam != null) {
                liveCam.close();
                liveCam = null;
                adb(8000, "shell", "svc", "power", "stayon", "false");
            }
            dumpAllCallbacks();
            dumpCallback(15, "unidbg_sendLiveness.txt", "unidbg_sendLiveness.json",
                    "unidbg_sendLiveness_summary.txt");
            dumpCallback(16, "unidbg_doLiveness.txt", "unidbg_doLiveness.json",
                    "unidbg_doLiveness_summary.txt");
            if (callbacks.containsKey(16)
                    && "true".equalsIgnoreCase(propertyOr("huiyan.rewriteDo", "false"))) {
                rewriteDoPack();
            }
            if (!"false".equalsIgnoreCase(propertyOr("huiyan.postSend", "true"))) {
                postLivenessIfReady(15);
                postLivenessIfReady(16);
            }
            encryptZipVideo(zipEntries, faceIdToken);
            if (!"false".equalsIgnoreCase(propertyOr("huiyan.postVideo", "true"))) {
                postVideoReport();
            }
        }

        emulator.close();
    }

    private void runYtAuth() {
        if (!LICENSE.isFile()) {
            System.out.println("missing license " + LICENSE.getAbsolutePath());
            return;
        }
        try {
            byte[] raw = Files.readAllBytes(LICENSE.toPath());
            String b64 = Base64.getEncoder().encodeToString(raw);
            int rc = callInt(ytApi,
                    "nativeInitAuthByString(Ljava/lang/String;Ljava/lang/String;)I", b64, "");
            System.out.println("nativeInitAuthByString rc=" + rc + " license=" + raw.length);
            writeOut("unidbg_yt_auth_rc.txt",
                    String.valueOf(rc).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            System.out.println("nativeInitAuthByString failed: " + e);
        }
    }

    private String loadLastEncryptedRsp() {
        File file = new File(OUT_DIR, "unidbg_startLiveness_http_rsp.txt");
        if (!file.isFile()) {
            return "";
        }
        try {
            String rsp = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            int key = rsp.indexOf("\"encryptedRsp\"");
            if (key < 0) {
                return "";
            }
            int colon = rsp.indexOf(':', key);
            int q1 = rsp.indexOf('"', colon + 1);
            int q2 = rsp.indexOf('"', q1 + 1);
            if (q1 < 0 || q2 < 0) {
                return "";
            }
            String light = rsp.substring(q1 + 1, q2);
            System.out.println("reuse last encryptedRsp chars=" + light.length());
            return light;
        } catch (Exception e) {
            return "";
        }
    }

    private String postStartLiveness(String body, String summary) {
        String rsp = postJson("https://sdk.faceid.qq.com/api/sdk/v4/startLiveness",
                body, summary, "unidbg_startLiveness_http_rsp.txt");
        if (rsp == null || rsp.isEmpty()) {
            return "";
        }
        int key = rsp.indexOf("\"encryptedRsp\"");
        if (key < 0) {
            return "";
        }
        int colon = rsp.indexOf(':', key);
        int q1 = rsp.indexOf('"', colon + 1);
        int q2 = rsp.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) {
            return "";
        }
        return rsp.substring(q1 + 1, q2);
    }

    private void runYtLiveness(String light) {
        if (light == null) {
            light = "";
        }
        String json = "{"
                + "\"ytLightData\":\"" + jsonEscape(light) + "\","
                + "\"needEncrypt\":" + (ytNeedEncrypt ? "true" : "false") + ","
                + "\"enhanceEncryptMethod\":0,"
                + "\"prepareTimeout\":30000,"
                + "\"actionTimeout\":60000,"
                + "\"livenessMode\":4,"
                + "\"landscape\":false,"
                + "\"ratio4_3\":\"0.020833, 0.140625, 0.020833, 0.140625\","
                + "\"ratio16_9\":\"0.013889, 0.226563, 0.013889, 0.226563\","
                + "\"resourceDownloadPath\":\"" + CACHE_VFS + "\""
                + "}";
        AssetManager assets = new AssetManager(vm, "assets");
        try {
            int rc = callInt(nativeApi,
                    "initYtLiveness(Ljava/lang/String;Landroid/content/res/AssetManager;)I",
                    json, assets);
            System.out.println("initYtLiveness rc=" + rc + " lightChars=" + light.length());
            writeOut("unidbg_initYtLiveness_rc.txt",
                    String.valueOf(rc).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            System.out.println("initYtLiveness failed: " + e);
        }
    }

    private void replayZipFrames(Map<String, byte[]> zipEntries) {
        List<NamedFrame> prepared = new ArrayList<NamedFrame>();
        List<NamedFrame> blink = new ArrayList<NamedFrame>();
        List<NamedFrame> reflect = new ArrayList<NamedFrame>();
        int minEdge = 300;
        try {
            minEdge = Integer.parseInt(propertyOr("huiyan.minEdge", "300"));
        } catch (Exception ignored) {
            minEdge = 300;
        }
        List<String> names = new ArrayList<String>(zipEntries.keySet());
        Collections.sort(names, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Integer.compare(frameOrder(a), frameOrder(b));
            }
        });
        for (String name : names) {
            String path = name.replace('\\', '/');
            if (path.contains("recv_") || path.contains("send_tx") || !name.endsWith(".jpg")) {
                continue;
            }
            Nv21Frame raw = jpegToNv21(zipEntries.get(name));
            if (raw == null || Math.min(raw.width, raw.height) < minEdge) {
                continue;
            }
            NamedFrame nf = new NamedFrame(name, raw, zipEntries.get(name));
            if (path.contains("send_BLINKEYE")) {
                blink.add(nf);
            } else if (path.contains("send_REFLECT")) {
                reflect.add(nf);
            } else if (path.contains("send_PREPARED")) {
                prepared.add(nf);
            }
        }
        if (prepared.isEmpty()) {
            prepared.addAll(blink);
        }
        replayBuckets(prepared, blink, reflect);
    }

    private void replayLiveFrames(File dir) {
        File root = dir;
        File nested = new File(dir, "out");
        if (nested.isDirectory()) {
            root = nested;
        }
        File[] files = root.listFiles();
        if (files == null || files.length == 0) {
            throw new IllegalStateException("no FlashCam frames in " + dir);
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareTo(b.getName());
            }
        });
        List<NamedFrame> prepared = new ArrayList<NamedFrame>();
        List<NamedFrame> blink = new ArrayList<NamedFrame>();
        List<NamedFrame> reflect = new ArrayList<NamedFrame>();
        boolean anyNv21 = false;
        for (File f : files) {
            if (f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".nv21")) {
                anyNv21 = true;
                break;
            }
        }
        phoneRawNv21 = anyNv21;
        for (File f : files) {
            String name = f.getName();
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            Nv21Frame raw;
            byte[] jpeg = new byte[0];
            if (anyNv21) {
                if (!lower.endsWith(".nv21")) {
                    continue;
                }
                raw = loadNv21File(f);
            } else if (lower.endsWith(".jpg")) {
                try {
                    jpeg = Files.readAllBytes(f.toPath());
                } catch (Exception e) {
                    System.out.println("read live frame failed " + name + " " + e);
                    continue;
                }
                raw = jpegToNv21(jpeg);
            } else {
                continue;
            }
            if (raw == null || Math.min(raw.width, raw.height) < 300) {
                System.out.println("skip live frame " + name);
                continue;
            }
            NamedFrame nf = new NamedFrame(name, raw, jpeg);
            String upper = name.toUpperCase(java.util.Locale.ROOT);
            if (upper.startsWith("BLINK")) {
                blink.add(nf);
            } else if (upper.startsWith("REFLECT")) {
                reflect.add(nf);
            } else {
                prepared.add(nf);
            }
        }
        if (prepared.isEmpty()) {
            prepared.addAll(blink);
        }
        System.out.println("live frames dir=" + root.getAbsolutePath()
                + " prepare=" + prepared.size()
                + " blink=" + blink.size()
                + " reflect=" + reflect.size()
                + (prepared.isEmpty() ? "" : (" first=" + prepared.get(0).frame.width
                + "x" + prepared.get(0).frame.height)));
        applyLivePreviewSize();
        System.out.println("live feed cameraSize " + cameraSize[0] + "x" + cameraSize[1]);
        if (phoneRawNv21) {
            List<NamedFrame> chrono = new ArrayList<NamedFrame>();
            chrono.addAll(prepared);
            chrono.addAll(blink);
            chrono.addAll(reflect);
            Collections.sort(chrono, new Comparator<NamedFrame>() {
                @Override
                public int compare(NamedFrame a, NamedFrame b) {
                    return Integer.compare(frameSeqName(a.name), frameSeqName(b.name));
                }
            });
            replayChronological(chrono);
        } else {
            replayBuckets(prepared, blink, reflect);
        }
    }

    private File captureFlashCam() {
        File dest = new File(OUT_DIR, "flashcam_out");
        if (!dest.exists() && !dest.mkdirs()) {
            throw new IllegalStateException("mkdir " + dest);
        }
        int cycle = 0;
        if (flashArgb != null) {
            for (int i = 0; i < flashArgb.length; i++) {
                int n = (flashLoops != null && i < flashLoops.length) ? flashLoops[i] : 3;
                cycle += Math.max(1, n) * Math.max(40, flashUnitMs);
            }
        }
        int reflectMs = Math.max(8000, cycle * 6);
        File spec = new File(OUT_DIR, "flashcam_spec.json");
        StringBuilder js = new StringBuilder();
        js.append("{\"unit_ms\":").append(flashUnitMs)
                .append(",\"prepare_ms\":3000,\"blink_ms\":4500,\"reflect_ms\":")
                .append(reflectMs).append(",\"interval_ms\":33,\"loops\":[");
        if (flashLoops != null) {
            for (int i = 0; i < flashLoops.length; i++) {
                if (i > 0) {
                    js.append(',');
                }
                js.append(flashLoops[i]);
            }
        } else {
            js.append("3,2,3");
        }
        js.append("],\"argb\":[");
        if (flashArgb != null) {
            for (int i = 0; i < flashArgb.length; i++) {
                if (i > 0) {
                    js.append(',');
                }
                js.append('"').append(String.format("%08x", flashArgb[i])).append('"');
            }
        } else {
            js.append("\"ffff0000\",\"ff00ff00\",\"ff0000ff\"");
        }
        js.append(']');
        boolean phoneSo = !"false".equalsIgnoreCase(propertyOr("huiyan.phoneSo", "true"));
        if (phoneSo) {
            js.append(",\"so\":true,\"needEncrypt\":false")
                    .append(",\"light\":\"").append(jsonEscape(sessionLightPlain)).append('"')
                    .append(",\"aesKey\":\"").append(jsonEscape(sessionAesKeyHex)).append('"')
                    .append(",\"aesIv\":\"").append(jsonEscape(sessionAesIvHex)).append('"')
                    .append(",\"token\":\"").append(jsonEscape(sessionToken)).append('"')
                    .append(",\"pubKey\":\"").append(jsonEscape(sessionPubKey)).append('"')
                    .append(",\"nonce\":\"").append(jsonEscape(sessionNonce)).append('"')
                    .append(",\"risk\":\"").append(jsonEscape(sessionRisk)).append('"')
                    .append(",\"dump_jpeg\":true,\"dump_ms\":100");
        }
        js.append('}');
        writeOut("flashcam_spec.json", js.toString().getBytes(StandardCharsets.UTF_8));
        String remoteSpec = "/data/local/tmp/flashcam/spec.json";
        String remoteOut = "/sdcard/Android/data/com.huiyan.flashcam/files/out";
        System.out.println("FlashCam spec " + spec.getAbsolutePath()
                + " cycleMs=" + cycle + " reflectMs=" + reflectMs);
        adb(15000, "shell", "mkdir", "-p", "/data/local/tmp/flashcam");
        adb(15000, "push", spec.getAbsolutePath(), remoteSpec);
        adb(15000, "shell", "rm", "-rf", remoteOut);
        adb(15000, "shell", "pm", "grant", "com.huiyan.flashcam", "android.permission.CAMERA");
        adb(8000, "shell", "input", "keyevent", "224");
        adb(8000, "shell", "input", "keyevent", "82");
        adb(8000, "shell", "input", "swipe", "540", "1800", "540", "800", "200");
        adb(8000, "shell", "am", "force-stop", "com.huiyan.flashcam");
        adb(8000, "shell", "am", "start", "-n", "com.huiyan.flashcam/.FlashActivity",
                "-f", "0x14000000");
        System.out.println("======= PIXEL: 手机只跑第6步活体，脸放进上方绿圈 =======");
        if (phoneSo) {
            System.out.println("PC 已 startLiveness+解密；等「引擎就绪」后点开始。手机不发包");
            System.out.println("眨眼后保持到「采集完成」——PC 会用手机 15/16 续上 send/do");
        } else {
            System.out.println("prepare 3s, blink 4.5s, reflect " + (reflectMs / 1000) + "s");
        }
        long deadline = System.currentTimeMillis() + 480000L;
        boolean done = false;
        boolean sawReady = false;
        boolean sawStarted = false;
        boolean saw15 = false;
        boolean saw16 = false;
        long pack15At = 0L;
        File pulledOut = new File(dest, "out");
        if (!pulledOut.exists() && !pulledOut.mkdirs()) {
            throw new IllegalStateException("mkdir " + pulledOut);
        }
        while (System.currentTimeMillis() < deadline) {
            String ls = adbQuiet(8000, "shell", "ls", remoteOut);
            if (ls == null) {
                ls = "";
            }
            boolean missing = ls.contains("No such");
            if (!sawReady && !missing && ls.contains("ENGINE_READY")) {
                sawReady = true;
                System.out.println("phone ENGINE_READY — 把脸放进上方绿圈，点开始");
            }
            if (!sawStarted && !missing && ls.contains("STARTED")) {
                sawStarted = true;
                System.out.println("phone sit started");
            }
            if (!saw15 && !missing && ls.contains("callback_15")) {
                saw15 = true;
                pack15At = System.currentTimeMillis();
                System.out.println("phone callback_15 ready");
            }
            if (!saw16 && !missing && ls.contains("callback_16")) {
                saw16 = true;
                System.out.println("phone callback_16 ready");
            }
            if (!missing && ls.contains("DONE")) {
                done = true;
                break;
            }
            if (saw15 && (saw16 || System.currentTimeMillis() - pack15At > 15000L)) {
                System.out.println("phone pack ready, pulling"
                        + (saw16 ? " (15+16)" : " (15 only)"));
                done = true;
                break;
            }
            try {
                Thread.sleep(800L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!done) {
            System.out.println("FlashCam DONE not seen ready=" + sawReady
                    + " started=" + sawStarted + " cb15=" + saw15 + " cb16=" + saw16);
            if (!saw15) {
                return null;
            }
            System.out.println("pulling partial phone pack anyway");
        }
        File pulled = new File(dest, "out");
        if (pulled.exists()) {
            File[] old = pulled.listFiles();
            if (old != null) {
                for (File f : old) {
                    f.delete();
                }
            }
        }
        adb(180000, "pull", remoteOut, dest.getAbsolutePath());
        String logcat = adbQuiet(15000, "logcat", "-d", "-t", "120", "-s",
                "FlashCam:I", "SkipAes:I");
        if (logcat != null && !logcat.isEmpty()) {
            writeOut("flashcam_logcat.txt", logcat.getBytes(StandardCharsets.UTF_8));
            if (logcat.contains("installSessionAes") || logcat.contains("installed PC AES")) {
                System.out.println("phone AES inject seen in logcat");
            }
            if (logcat.contains("callback 15") || logcat.contains("callback 16")) {
                System.out.println("phone pack logcat has callback 15/16");
            }
        }
        File root = pulled.isDirectory() ? pulled : dest;
        File[] pulledFiles = root.listFiles();
        int n = 0;
        if (pulledFiles != null) {
            for (File f : pulledFiles) {
                String nm = f.getName().toLowerCase(java.util.Locale.ROOT);
                if (nm.endsWith(".nv21") || nm.endsWith(".jpg")) {
                    n++;
                }
            }
        }
        System.out.println("FlashCam pulled " + n + " frames from " + root);
        File pack15 = findFile(dest, "callback_15.txt");
        boolean hasPack = pack15 != null;
        System.out.println("FlashCam pack15=" + (hasPack ? pack15.getAbsolutePath() : "none")
                + " pack16=" + (findFile(dest, "callback_16.txt") != null));
        if (!hasPack && n < 20) {
            System.out.println("FlashCam too few frames and no phone pack");
            return null;
        }
        return dest;
    }

    private static File findFile(File dir, String name) {
        if (dir == null || !dir.isDirectory() || name == null) {
            return null;
        }
        File direct = new File(dir, name);
        if (direct.isFile()) {
            return direct;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return null;
        }
        for (File kid : kids) {
            if (kid.isDirectory()) {
                File found = findFile(kid, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void importPhoneCallbacks(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File f15 = findFile(dir, "callback_15.txt");
        File f16 = findFile(dir, "callback_16.txt");
        importOneCallback(15, f15);
        importOneCallback(16, f16);
    }

    private void importOneCallback(int code, File file) {
        if (file == null || !file.isFile() || callbacks.containsKey(code)) {
            return;
        }
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (text.isEmpty()) {
                return;
            }
            callbacks.put(code, text);
            System.out.println("phone callback " + code + " chars=" + text.length());
        } catch (Exception e) {
            System.out.println("import callback " + code + " failed: " + e);
        }
    }

    /** Phone summary is signed with a different AES copy; re-sign with PC startLiveness AES. */
    private void resignPhonePack(int code) {
        String packed = callbacks.get(code);
        if (packed == null || packed.isEmpty()) {
            return;
        }
        int split = packed.indexOf("--");
        String body = split < 0 ? packed : packed.substring(0, split);
        String oldSum = split < 0 ? "" : packed.substring(split + 2);
        String inner = decryptSessionBody(body);
        if (inner.isEmpty()) {
            System.out.println("resign " + code + " decrypt failed");
            return;
        }
        writeOut("unidbg_callback_" + code + "_inner.json", inner.getBytes(StandardCharsets.UTF_8));
        String neu = bodyDataSign(inner, sessionToken);
        if (neu.isEmpty()) {
            System.out.println("resign " + code + " sign failed");
            return;
        }
        callbacks.put(code, body + "--" + neu);
        System.out.println("resign " + code + " inner=" + inner.length()
                + " phoneSum=" + oldSum + " pcSum=" + neu
                + (oldSum.equals(neu) ? " SAME" : " RETROFIT"));
    }

    /**
     * getStartLivenessReqBody re-encrypts frontData second. Swap in the
     * Go-aligned EncryptField blob from Turing (unidbg_8123_front.txt) and
     * re-sign so start actually carries ident/hash/bind maps.
     */
    private String[] injectGoAlignedFront(String body, String token) {
        if (!RISK_FRONT.isFile()) {
            System.out.println("inject front skip, no " + RISK_FRONT.getName());
            return null;
        }
        try {
            String second = new String(Files.readAllBytes(RISK_FRONT.toPath()), StandardCharsets.US_ASCII).trim();
            if (second.isEmpty()) {
                return null;
            }
            String inner = decryptSessionBody(body);
            if (inner.isEmpty()) {
                System.out.println("inject front decrypt inner failed");
                return null;
            }
            writeOut("unidbg_startLiveness_req_plain_so.json", inner.getBytes(StandardCharsets.UTF_8));
            String replaced = replaceFrontSecond(inner, second);
            if (replaced.equals(inner)) {
                System.out.println("inject front no-op (second already matched or parse miss)");
                return null;
            }
            writeOut("unidbg_startLiveness_req_plain.json", replaced.getBytes(StandardCharsets.UTF_8));
            String neuEnc = encryptSessionBody(replaced);
            if (neuEnc.isEmpty()) {
                System.out.println("inject front encrypt failed");
                return null;
            }
            String oldEnc = jsonStringField(body, "encryptedBody");
            if (oldEnc.isEmpty() || !body.contains(oldEnc)) {
                System.out.println("inject front missing encryptedBody");
                return null;
            }
            String neuBody = body.replace(oldEnc, neuEnc);
            String neuSum = bodyDataSign(replaced, token);
            System.out.println("inject front second " + frontSecondLen(inner) + "->" + second.length()
                    + " inner " + inner.length() + "->" + replaced.length()
                    + " enc " + oldEnc.length() + "->" + neuEnc.length()
                    + " sum=" + neuSum.length());
            return new String[] {neuBody, neuSum};
        } catch (Exception e) {
            System.out.println("inject front failed: " + e);
            return null;
        }
    }

    private static int frontSecondLen(String inner) {
        int k = inner.indexOf("\"frontData\"");
        if (k < 0) {
            return -1;
        }
        int q1 = inner.indexOf('"', inner.indexOf(':', k) + 1);
        int q2 = inner.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) {
            return -1;
        }
        String fd = inner.substring(q1 + 1, q2);
        int comma = fd.indexOf(',');
        return comma < 0 ? fd.length() : fd.length() - comma - 1;
    }

    private static String replaceFrontSecond(String inner, String second) {
        int k = inner.indexOf("\"frontData\"");
        if (k < 0) {
            return inner;
        }
        int q1 = inner.indexOf('"', inner.indexOf(':', k) + 1);
        int q2 = inner.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) {
            return inner;
        }
        String fd = inner.substring(q1 + 1, q2);
        int comma = fd.indexOf(',');
        if (comma < 0) {
            return inner.substring(0, q1 + 1) + fd + "," + second + inner.substring(q2);
        }
        String neu = fd.substring(0, comma + 1) + second;
        return inner.substring(0, q1 + 1) + neu + inner.substring(q2);
    }

    private String encryptSessionBody(String inner) {
        byte[] key = hexDecode(sessionAesKeyHex);
        byte[] iv = hexDecode(sessionAesIvHex);
        if (inner == null || key.length != 32 || iv.length != 16) {
            return "";
        }
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] ct = c.doFinal(inner.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ct);
        } catch (Exception e) {
            System.out.println("encryptSessionBody failed: " + e);
            return "";
        }
    }

    private String decryptSessionBody(String json) {
        String enc = jsonStringField(json, "encryptedBody");
        byte[] key = hexDecode(sessionAesKeyHex);
        byte[] iv = hexDecode(sessionAesIvHex);
        if (enc.isEmpty() || key.length != 32 || iv.length != 16) {
            return "";
        }
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] pt = c.doFinal(Base64.getDecoder().decode(enc));
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("decryptSessionBody failed: " + e);
            return "";
        }
    }

    private String bodyDataSign(String inner, String token) {
        byte[] key = hexDecode(sessionAesKeyHex);
        if (inner == null || key.length != 32 || token == null) {
            return "";
        }
        try {
            String aesB64 = Base64.getEncoder().encodeToString(key);
            String shaMsg = inner + "--" + token + "--" + aesB64 + "--" + "8c9421bhk367l6310q";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(shaMsg.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            System.out.println("bodyDataSign failed: " + e);
            return "";
        }
    }

    private static String jsonStringField(String json, String key) {
        if (json == null || key == null) {
            return "";
        }
        String pat = "\"" + key + "\":\"";
        int i = json.indexOf(pat);
        if (i < 0) {
            return "";
        }
        i += pat.length();
        int j = json.indexOf('"', i);
        return j < 0 ? "" : json.substring(i, j);
    }

    private static final long SESSION_AES_KEY_OFF = 0x2ece30L;
    private static final long SESSION_AES_IV_OFF = 0x2ece50L;

    private void dumpSessionAesFromSo() {
        if (huiyanModule == null) {
            return;
        }
        try {
            byte[] key = emulator.getBackend().mem_read(huiyanModule.base + SESSION_AES_KEY_OFF, 32);
            byte[] iv = emulator.getBackend().mem_read(huiyanModule.base + SESSION_AES_IV_OFF, 16);
            if (isAllZero(key) || isAllZero(iv)) {
                System.out.println("BSS session AES empty");
                return;
            }
            sessionAesKeyHex = hexEncode(key);
            sessionAesIvHex = hexEncode(iv);
            System.out.println("BSS aes keyChars=" + sessionAesKeyHex.length()
                    + " iv=" + sessionAesIvHex);
        } catch (Exception e) {
            System.out.println("BSS session AES read failed: " + e);
        }
    }

    private boolean decryptStartLivenessRsp(String encryptedRspB64) {
        byte[] key = hexDecode(sessionAesKeyHex);
        if (encryptedRspB64 == null || encryptedRspB64.isEmpty() || key.length != 32) {
            return false;
        }
        List<byte[]> ivs = new ArrayList<byte[]>();
        byte[] bssIv = hexDecode(sessionAesIvHex);
        if (bssIv.length == 16) {
            ivs.add(bssIv);
        }
        try {
            File reqFile = new File(OUT_DIR, "unidbg_startLiveness.json");
            if (reqFile.isFile()) {
                String req = new String(Files.readAllBytes(reqFile.toPath()), StandardCharsets.UTF_8);
                String ivB64 = jsonStringField(req, "Iv");
                if (!ivB64.isEmpty()) {
                    byte[] reqIv = Base64.getDecoder().decode(ivB64);
                    if (reqIv.length == 16) {
                        ivs.add(reqIv);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("read request Iv failed: " + e);
        }
        for (int i = 0; i < ivs.size(); i++) {
            byte[] iv = ivs.get(i);
            try {
                Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
                c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
                byte[] pt = c.doFinal(Base64.getDecoder().decode(encryptedRspB64));
                String text = new String(pt, StandardCharsets.UTF_8).trim()
                        .replace("\r", "").replace("\n", "");
                if (text.startsWith("{")) {
                    writeOut("unidbg_startLiveness_encryptedRsp.json",
                            text.getBytes(StandardCharsets.UTF_8));
                    sessionAesIvHex = hexEncode(iv);
                    System.out.println("decrypt encryptedRsp ok chars=" + text.length()
                            + " ivSource=" + i);
                    return true;
                }
            } catch (Exception e) {
                System.out.println("decrypt encryptedRsp try=" + i + " failed: " + e);
            }
        }
        return false;
    }

    private static boolean isAllZero(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return true;
        }
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static String hexEncode(byte[] raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (int i = 0; i < raw.length; i++) {
            sb.append(String.format("%02x", raw[i] & 0xff));
        }
        return sb.toString();
    }

    private static byte[] hexDecode(String hex) {
        if (hex == null) {
            return new byte[0];
        }
        String s = hex.trim();
        int n = s.length();
        if ((n & 1) != 0) {
            return new byte[0];
        }
        byte[] out = new byte[n / 2];
        for (int i = 0; i < n; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(s.substring(i, i + 2), 16);
        }
        return out;
    }

    private int readPostedCode(String fileName) {
        File f = new File(OUT_DIR, fileName);
        if (!f.isFile()) {
            return -1;
        }
        try {
            String js = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return jsonInt(js, "code", -1);
        } catch (Exception e) {
            return -1;
        }
    }

    private void pullRemoteFile(String remote, File local) {
        File parent = local.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        adb(15000, "pull", remote, local.getAbsolutePath());
    }

    private static String adb(long timeoutMs, String... args) {
        return runAdb(timeoutMs, true, args);
    }

    private static String adbQuiet(long timeoutMs, String... args) {
        return runAdb(timeoutMs, false, args);
    }

    private static String runAdb(long timeoutMs, boolean print, String... args) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(adbBin());
        cmd.addAll(Arrays.asList(args));
        try {
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            long end = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < end) {
                while (proc.getInputStream().available() > 0) {
                    int r = proc.getInputStream().read(tmp);
                    if (r < 0) {
                        break;
                    }
                    buf.write(tmp, 0, r);
                }
                try {
                    proc.exitValue();
                    break;
                } catch (IllegalThreadStateException ignored) {
                    Thread.sleep(40L);
                }
            }
            if (proc.isAlive()) {
                proc.destroyForcibly();
            }
            while (proc.getInputStream().available() > 0) {
                int r = proc.getInputStream().read(tmp);
                if (r < 0) {
                    break;
                }
                buf.write(tmp, 0, r);
            }
            String out = new String(buf.toByteArray(), StandardCharsets.UTF_8).trim();
            if (print && !out.isEmpty() && out.length() < 400) {
                System.out.println("adb " + args[0] + " " + out.replace('\n', ' '));
            }
            return out;
        } catch (Exception e) {
            System.out.println("adb failed " + Arrays.toString(args) + " " + e);
            return "";
        }
    }

    private static String adbBin() {
        String override = propertyOr("huiyan.adb", "");
        if (!override.isEmpty()) {
            return override;
        }
        File local = new File("D:\\platform-tools\\adb.exe");
        if (local.isFile()) {
            return local.getAbsolutePath();
        }
        return "adb";
    }

    private LiveCam openLiveCam() {
        int port = 18906;
        try {
            port = Integer.parseInt(propertyOr("huiyan.livePort", "18906"));
        } catch (Exception ignored) {
            port = 18906;
        }
        adb(15000, "forward", "--remove", "tcp:" + port);
        adb(8000, "shell", "svc", "power", "stayon", "usb");
        LiveCam cam = new LiveCam(port);
        try {
            cam.listen();
        } catch (Exception e) {
            adb(8000, "shell", "svc", "power", "stayon", "false");
            throw new IllegalStateException("live cam listen failed: " + e);
        }
        adb(15000, "reverse", "--remove", "tcp:" + port);
        String rev = adb(15000, "reverse", "tcp:" + port, "tcp:" + port);
        System.out.println("adb reverse " + rev);
        System.out.println("adb reverse list " + adb(8000, "reverse", "--list"));
        adb(8000, "shell", "input", "keyevent", "224");
        adb(8000, "shell", "input", "keyevent", "82");
        adb(8000, "shell", "pm", "grant", "com.huiyan.flashcam", "android.permission.CAMERA");
        adb(8000, "shell", "am", "force-stop", "com.huiyan.flashcam");
        adb(8000, "shell", "am", "start", "-n", "com.huiyan.flashcam/.FlashActivity",
                "-f", "0x14000000", "--ez", "live", "true");
        System.out.println("======= LIVE: sit in the green oval on Pixel =======");
        System.out.println("SO drives blink / color / exposure. Phone connects via adb reverse.");
        if (!cam.waitConnected(25000L)) {
            cam.close();
            adb(8000, "shell", "svc", "power", "stayon", "false");
            throw new IllegalStateException("live cam accept timeout port=" + port);
        }
        cam.sendHint("请将脸放进绿圈");
        System.out.println("live cam connected port=" + port);
        return cam;
    }

    private void runLivePump() {
        int maxFrames = 360;
        try {
            maxFrames = Integer.parseInt(propertyOr("huiyan.maxFrames", "360"));
        } catch (Exception ignored) {
            maxFrames = 360;
        }
        System.out.println("live pump maxFrames=" + maxFrames
                + " camera=" + cameraSize[0] + "x" + cameraSize[1]);
        boolean prevVerbose = true;
        vm.setVerbose(false);
        int ok = 0;
        int fail = 0;
        int waitFrames = 0;
        boolean clockArmed = false;
        List<Nv21Frame> blinkClip = null;
        int blinkIdx = 0;
        int blinkLoops = 0;
        int blinkBurst = 0;
        String lastHint = "";
        int lastColor = Integer.MIN_VALUE;
        long wallDeadline = System.currentTimeMillis() + 180000L;
        long faceDeadline = System.currentTimeMillis() + 120000L;
        try {
            while (ok < maxFrames && System.currentTimeMillis() < wallDeadline) {
                if (callbacks.containsKey(15) && callbacks.containsKey(16)) {
                    break;
                }
                if (inStructure && structureTicks >= 6) {
                    tickVirtualClock(33L);
                    pumpYtWorker("structure encode " + structureTicks);
                    structureTicks++;
                    if (structureTicks > 80) {
                        System.out.println("structure encode timeout ticks=" + structureTicks);
                        break;
                    }
                    continue;
                }
                boolean blinking = poseHint != null && poseHint.startsWith("20001")
                        && !wantReflect;
                Nv21Frame raw;
                if (blinking && liveCam != null) {
                    if (blinkClip == null || blinkLoops >= 4) {
                        blinkBurst++;
                        System.out.println("blink clip #" + blinkBurst
                                + " — 请连续眨眨眼 2.2s");
                        lastHint = "请连续眨眨眼";
                        blinkClip = liveCam.captureClip(2200);
                        blinkIdx = 0;
                        blinkLoops = 0;
                        System.out.println("blink clip #" + blinkBurst
                                + " frozen n=" + blinkClip.size());
                        if (blinkClip.size() < 12) {
                            System.out.println("blink clip too short, retry");
                            blinkClip = null;
                            continue;
                        }
                    }
                    raw = blinkClip.get(blinkIdx++);
                    if (blinkIdx >= blinkClip.size()) {
                        blinkIdx = 0;
                        blinkLoops++;
                        System.out.println("blink clip loop " + blinkLoops
                                + " pose=" + poseHint);
                    }
                } else {
                    if (blinkClip != null) {
                        System.out.println("blink clip done pose=" + poseHint
                                + " reflect=" + wantReflect
                                + " loops=" + blinkLoops);
                        blinkClip = null;
                        blinkIdx = 0;
                        blinkLoops = 0;
                    }
                    raw = liveCam == null ? null : liveCam.take(250);
                    if (raw == null) {
                        raw = lastRawNv21;
                    }
                }
                if (raw == null) {
                    pumpYtWorker("live wait");
                    continue;
                }
                lastRawNv21 = raw;
                Nv21Frame frame = prepareFeed(raw);
                String hint = hintForPose();
                if (liveCam != null && !hint.equals(lastHint)) {
                    liveCam.sendHint(hint);
                    lastHint = hint;
                    System.out.println("live hint " + hint + " pose=" + poseHint);
                }
                int col = liveFlashArgb();
                if (liveCam != null && col != lastColor) {
                    liveCam.sendColor(col);
                    lastColor = col;
                    System.out.println("live color 0x" + Integer.toHexString(col));
                }
                boolean haveFace = poseHint != null && poseHint.length() > 0
                        && !poseHint.startsWith("10000");
                if (!clockArmed) {
                    if (haveFace) {
                        clockArmed = true;
                        System.out.println("live face acquired pose=" + poseHint
                                + " waitFrames=" + waitFrames);
                    } else if (System.currentTimeMillis() > faceDeadline) {
                        System.out.println("live no face after wait waitFrames=" + waitFrames);
                        break;
                    }
                }
                if (clockArmed) {
                    tickVirtualClock(33L);
                } else {
                    tickVirtualClock(1L);
                }
                updateShotPreview(frame);
                try {
                    int rotate = 1;
                    try {
                        rotate = Integer.parseInt(propertyOr("huiyan.rotate", "1"));
                    } catch (Exception ignored) {
                        rotate = 1;
                    }
                    int rc = nativeApi.callStaticJniMethodInt(emulator,
                            "updateFrame([BIIII[BIII)I",
                            new ByteArray(vm, frame.data),
                            frame.width, frame.height, 1, rotate, null, 0, 0, 0);
                    if (clockArmed) {
                        ok++;
                        if (ok <= 8 || ok % 15 == 0 || wantReflect) {
                            System.out.println("updateFrame live " + ok
                                    + " " + frame.width + "x" + frame.height
                                    + " rc=" + rc + " pose=" + poseHint
                                    + " ev=" + appliedExposure
                                    + (blinkClip != null
                                    ? " clip=" + blinkIdx + "/" + blinkClip.size()
                                    + " L" + blinkLoops : "")
                                    + " cb=" + callbacks.keySet());
                        }
                    } else {
                        waitFrames++;
                        if (waitFrames <= 8 || waitFrames % 30 == 0) {
                            System.out.println("updateFrame wait " + waitFrames
                                    + " " + frame.width + "x" + frame.height
                                    + " rc=" + rc + " pose=" + poseHint);
                        }
                    }
                    flushCameraReply();
                    if (inStructure) {
                        structureTicks++;
                        pumpYtWorker("structure " + structureTicks);
                    } else if (wantReflect) {
                        pumpYtWorker("reflect " + ok);
                    } else {
                        pumpYtWorker("live " + ok);
                    }
                } catch (Exception e) {
                    fail++;
                    System.out.println("updateFrame live failed: " + e);
                    break;
                }
            }
            System.out.println("live pump done ok=" + ok + " wait=" + waitFrames
                    + " fail=" + fail + " pose=" + poseHint
                    + " callbacks=" + callbacks.keySet());
        } finally {
            vm.setVerbose(prevVerbose);
            if (liveCam != null) {
                liveCam.sendStop();
            }
        }
    }

    private String hintForPose() {
        String pose = poseHint == null ? "" : poseHint;
        if (wantReflect || pose.startsWith("20005") || pose.startsWith("20006")) {
            return "验证中，请保持姿势不变";
        }
        if (pose.startsWith("20001")) {
            return "请眨眨眼";
        }
        if (pose.startsWith("20002")) {
            return "请张张嘴";
        }
        if (pose.startsWith("20003")) {
            return "请摇摇头";
        }
        if (pose.startsWith("20004")) {
            return "请点点头";
        }
        if (pose.startsWith("10012")) {
            return "请稍远一些";
        }
        if (pose.startsWith("10013")) {
            return "请稍近一些";
        }
        if (pose.startsWith("10000")) {
            return "请将脸移入绿圈";
        }
        if (pose.startsWith("10002")) {
            return "请保持姿势不变";
        }
        return "请将脸放进绿圈";
    }

    private int liveFlashArgb() {
        if (!wantReflect) {
            return 0;
        }
        int argb = liveCb2Argb;
        if (argb == 0 || argb == 298634444) {
            return 0;
        }
        return argb;
    }

    private static final class LiveCam {
        private static final int MAGIC = 0x48594631;
        private final int port;
        private ServerSocket server;
        private Socket socket;
        private DataInputStream in;
        private OutputStream out;
        private final Object lock = new Object();
        private Nv21Frame latest;
        private volatile boolean closed;
        private volatile boolean connected;
        private int frames;

        LiveCam(int port) {
            this.port = port;
        }

        void listen() throws IOException {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress("127.0.0.1", port));
            server.setSoTimeout(1000);
            Thread acceptor = new Thread(new Runnable() {
                @Override
                public void run() {
                    acceptLoop();
                }
            }, "live-cam-accept");
            acceptor.setDaemon(true);
            acceptor.start();
            System.out.println("live cam listen 127.0.0.1:" + port);
        }

        private void acceptLoop() {
            while (!closed && server != null) {
                try {
                    Socket s = server.accept();
                    s.setTcpNoDelay(true);
                    s.setSoTimeout(0);
                    attach(s);
                } catch (SocketTimeoutException ignored) {
                } catch (Exception e) {
                    if (!closed) {
                        System.out.println("live cam accept " + e);
                    }
                }
            }
        }

        private void attach(Socket s) {
            Socket old;
            synchronized (this) {
                old = socket;
                socket = s;
                try {
                    in = new DataInputStream(s.getInputStream());
                    out = s.getOutputStream();
                    connected = true;
                } catch (Exception e) {
                    System.out.println("live cam attach " + e);
                    connected = false;
                    return;
                }
            }
            if (old != null) {
                try {
                    old.close();
                } catch (Exception ignored) {
                }
            }
            System.out.println("live cam client " + s.getRemoteSocketAddress());
            final DataInputStream mine = in;
            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    drain(mine);
                }
            }, "live-cam-read");
            reader.setDaemon(true);
            reader.start();
        }

        boolean waitConnected(long timeoutMs) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (!connected && !closed && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return connected;
                }
            }
            return connected;
        }

        private void drain(DataInputStream stream) {
            try {
                while (!closed && stream == in) {
                    int magic = stream.readInt();
                    if (magic != MAGIC) {
                        System.out.println("live cam bad magic 0x" + Integer.toHexString(magic));
                        break;
                    }
                    int w = stream.readUnsignedShort();
                    int h = stream.readUnsignedShort();
                    int n = stream.readInt();
                    if (n <= 0 || n > 4000000) {
                        System.out.println("live cam bad frame " + w + "x" + h + " n=" + n);
                        break;
                    }
                    byte[] data = new byte[n];
                    stream.readFully(data);
                    if (w < 2 || h < 2) {
                        System.out.println("live cam hello n=" + n);
                        continue;
                    }
                    Nv21Frame frame;
                    if (n >= w * h * 3 / 2) {
                        frame = new Nv21Frame(data, w, h);
                    } else {
                        frame = jpegToNv21(data);
                    }
                    if (frame == null) {
                        continue;
                    }
                    frames++;
                    if (frames == 1 || frames % 30 == 0) {
                        System.out.println("live cam frame " + frames
                                + " " + frame.width + "x" + frame.height
                                + " n=" + data.length);
                    }
                    synchronized (lock) {
                        latest = frame;
                        lock.notifyAll();
                    }
                }
            } catch (Exception e) {
                if (!closed) {
                    System.out.println("live cam reader " + e);
                }
            } finally {
                if (stream == in) {
                    connected = false;
                }
            }
        }

        Nv21Frame take(long waitMs) {
            synchronized (lock) {
                if (latest == null && waitMs > 0) {
                    try {
                        lock.wait(waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                Nv21Frame f = latest;
                latest = null;
                return f;
            }
        }

        /**
         * Freeze a real-time blink: tell the phone to countdown, then
         * snapshot every camera frame for {@code ms}. The list is immutable
         * afterwards — replay it, do not mix in live frames.
         */
        List<Nv21Frame> captureClip(int ms) {
            sendCmd("{\"cmd\":\"clip\",\"ms\":" + ms + "}");
            sendCmd("{\"cmd\":\"hint\",\"text\":\"请连续眨眨眼\"}");
            List<Nv21Frame> out = new ArrayList<Nv21Frame>();
            long until = System.currentTimeMillis() + Math.max(800, ms);
            while (!closed && System.currentTimeMillis() < until) {
                Nv21Frame f = take(40);
                if (f != null) {
                    out.add(f);
                }
            }
            return out;
        }

        void sendHint(String text) {
            sendCmd("{\"cmd\":\"hint\",\"text\":\"" + jsonEscape(text) + "\"}");
        }

        void sendColor(int argb) {
            sendCmd("{\"cmd\":\"color\",\"argb\":" + argb + "}");
        }

        void sendEv(float ev) {
            sendCmd("{\"cmd\":\"ev\",\"ev\":" + ev + "}");
        }

        void sendStop() {
            sendCmd("{\"cmd\":\"stop\"}");
        }

        private void sendCmd(String json) {
            synchronized (this) {
                if (out == null || closed) {
                    return;
                }
                try {
                    out.write((json + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (Exception e) {
                    System.out.println("live cam send failed " + e);
                }
            }
        }

        void close() {
            closed = true;
            sendStop();
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (server != null) {
                    server.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Phone SO dumps are a single timeline. Replay in capture order and
     * pump YT workers every frame — do not split/loop PREPARE/BLINK/REFLECT.
     */
    private void replayChronological(List<NamedFrame> frames) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalStateException("no chronological NV21 frames");
        }
        int extra = 120;
        try {
            extra = Integer.parseInt(propertyOr("huiyan.chronoExtra", "120"));
        } catch (Exception ignored) {
            extra = 120;
        }
        int maxFrames = frames.size() + Math.max(0, extra);
        System.out.println("replay chronological n=" + frames.size()
                + " extra=" + extra + " first=" + frames.get(0).name
                + " last=" + frames.get(frames.size() - 1).name);
        boolean prevVerbose = true;
        vm.setVerbose(false);
        int ok = 0;
        int fail = 0;
        int i = 0;
        String lastPhase = "";
        try {
            while (ok < maxFrames) {
                if (callbacks.containsKey(15) && callbacks.containsKey(16)) {
                    break;
                }
                if (inStructure && structureTicks >= 6) {
                    tickVirtualClock(33L);
                    pumpYtWorker("structure encode " + structureTicks);
                    structureTicks++;
                    if (structureTicks > 80) {
                        System.out.println("structure encode timeout ticks=" + structureTicks);
                        break;
                    }
                    continue;
                }
                NamedFrame nf = i < frames.size() ? frames.get(i) : frames.get(frames.size() - 1);
                boolean held = i >= frames.size();
                if (i < frames.size()) {
                    i++;
                }
                String phase = currentPhase();
                if (!phase.equals(lastPhase)) {
                    System.out.println("phase -> " + phase + " pose=" + poseHint
                            + " reflect=" + wantReflect + " i=" + i
                            + " file=" + nf.name);
                    lastPhase = phase;
                }
                lastRawNv21 = nf.frame;
                Nv21Frame frame = prepareFeed(nf.frame);
                tickVirtualClock(33L);
                updateShotPreview(frame);
                try {
                    int rotate = 1;
                    try {
                        rotate = Integer.parseInt(propertyOr("huiyan.rotate", "1"));
                    } catch (Exception ignored) {
                        rotate = 1;
                    }
                    int rc = nativeApi.callStaticJniMethodInt(emulator,
                            "updateFrame([BIIII[BIII)I",
                            new ByteArray(vm, frame.data),
                            frame.width, frame.height, 1, rotate, null, 0, 0, 0);
                    ok++;
                    System.out.println("updateFrame " + phase
                            + (held ? " HOLD" : "")
                            + " " + nf.name
                            + " " + frame.width + "x" + frame.height
                            + " nv21=" + frame.data.length + " rc=" + rc
                            + " pose=" + poseHint
                            + " cb=" + callbacks.keySet());
                    flushCameraReply();
                    if (inStructure) {
                        structureTicks++;
                        pumpYtWorker("structure " + structureTicks);
                    } else if (wantReflect || "reflect".equals(phase)) {
                        pumpYtWorker("reflect " + ok);
                    } else {
                        pumpYtWorker(phase + " " + ok);
                    }
                } catch (Exception e) {
                    fail++;
                    System.out.println("updateFrame " + nf.name + " failed: " + e);
                    break;
                }
            }
            System.out.println("chrono done ok=" + ok + " fail=" + fail
                    + " played=" + Math.min(i, frames.size()) + "/" + frames.size()
                    + " phase=" + lastPhase + " pose=" + poseHint
                    + " callbacks=" + callbacks.keySet());
        } finally {
            vm.setVerbose(prevVerbose);
        }
    }

    private static int frameSeqName(String name) {
        if (name == null) {
            return 0;
        }
        int a = name.indexOf('_');
        if (a < 0 || a + 1 >= name.length()) {
            return 0;
        }
        int c = a + 1;
        while (c < name.length() && Character.isDigit(name.charAt(c))) {
            c++;
        }
        if (c == a + 1) {
            return 0;
        }
        try {
            return Integer.parseInt(name.substring(a + 1, c));
        } catch (Exception e) {
            return 0;
        }
    }

    private void replayBuckets(List<NamedFrame> prepared, List<NamedFrame> blink,
            List<NamedFrame> reflect) {
        int maxFrames = 520;
        try {
            maxFrames = Integer.parseInt(propertyOr("huiyan.maxFrames", "520"));
        } catch (Exception ignored) {
            maxFrames = 520;
        }
        System.out.println("replay buckets prepared=" + prepared.size()
                + " blink=" + blink.size() + " reflect=" + reflect.size()
                + " live=" + liveFeed + " maxFrames=" + maxFrames);
        boolean prevVerbose = true;
        vm.setVerbose(false);
        int ok = 0;
        int fail = 0;
        int iPrep = 0;
        int iBlink = 0;
        int iRefl = 0;
        String lastPhase = "";
        float lastReflEv = Float.NaN;
        try {
            while (ok < maxFrames) {
                if (callbacks.containsKey(15) && callbacks.containsKey(16)) {
                    break;
                }
                if (inStructure && structureTicks >= 6) {
                    tickVirtualClock(33L);
                    pumpYtWorker("structure encode " + structureTicks);
                    structureTicks++;
                    if (structureTicks > 80) {
                        System.out.println("structure encode timeout ticks=" + structureTicks);
                        break;
                    }
                    continue;
                }
                String phase = currentPhase();
                List<NamedFrame> batch;
                int idx;
                if ("blink".equals(phase) && !blink.isEmpty()) {
                    batch = blink;
                    idx = iBlink++ % blink.size();
                } else if ("reflect".equals(phase) && !reflect.isEmpty()) {
                    batch = pickReflectByEv(reflect, appliedExposure);
                    if (Float.isNaN(lastReflEv) || Math.abs(lastReflEv - appliedExposure) > 0.05f) {
                        iRefl = 0;
                        lastReflEv = appliedExposure;
                        System.out.println("reflect ev bucket " + appliedExposure
                                + " n=" + batch.size());
                    }
                    idx = iRefl++ % batch.size();
                } else if (!prepared.isEmpty()) {
                    phase = "prepare";
                    batch = prepared;
                    idx = iPrep++ % prepared.size();
                } else if (!blink.isEmpty()) {
                    phase = "blink";
                    batch = blink;
                    idx = iBlink++ % blink.size();
                } else {
                    System.out.println("no frames for phase " + phase);
                    break;
                }
                if (!phase.equals(lastPhase)) {
                    System.out.println("phase -> " + phase + " pose=" + poseHint
                            + " reflect=" + wantReflect + " cb0=" + callbacks.get(0));
                    lastPhase = phase;
                }
                NamedFrame nf = batch.get(idx);
                lastRawNv21 = nf.frame;
                Nv21Frame frame = prepareFeed(nf.frame);
                if (!phoneRawNv21 && Float.isNaN(nf.ev)) {
                    applyExposureNv21(frame, appliedExposure);
                }
                tickVirtualClock(33L);
                boolean tintNow = !liveFeed && (liveCb2Argb != 0
                        || "true".equalsIgnoreCase(propertyOr("huiyan.forceTint", "false")));
                if (tintNow && (wantReflect || "reflect".equals(phase))) {
                    if (reflectStartMs == 0L) {
                        reflectStartMs = virtualNowMs();
                        System.out.println("reflect color clock start unit=" + flashUnitMs
                                + " colors=" + (flashArgb == null ? 0 : flashArgb.length));
                    }
                    int argb = currentFlashArgb();
                    if (argb != 0) {
                        tintNv21(frame, argb);
                    }
                }
                updateShotPreview(frame);
                try {
                    int rotate = 1;
                    try {
                        rotate = Integer.parseInt(propertyOr("huiyan.rotate", "1"));
                    } catch (Exception ignored) {
                        rotate = 1;
                    }
                    int rc = nativeApi.callStaticJniMethodInt(emulator,
                            "updateFrame([BIIII[BIII)I",
                            new ByteArray(vm, frame.data),
                            frame.width, frame.height, 1, rotate, null, 0, 0, 0);
                    ok++;
                    System.out.println("updateFrame " + phase + " " + nf.name
                            + " " + frame.width + "x" + frame.height
                            + " nv21=" + frame.data.length + " rc=" + rc
                            + " pose=" + poseHint
                            + (wantReflect ? " tint=0x" + Integer.toHexString(currentFlashArgb()) : "")
                            + " cb=" + callbacks.keySet());
                    flushCameraReply();
                    if (inStructure) {
                        structureTicks++;
                        pumpYtWorker("structure " + structureTicks);
                    } else if (wantReflect || "reflect".equals(phase)) {
                        pumpYtWorker("reflect " + iRefl);
                    } else {
                        pumpYtWorker(phase + " " + ok);
                    }
                } catch (Exception e) {
                    fail++;
                    System.out.println("updateFrame " + nf.name + " failed: " + e);
                    break;
                }
            }
            System.out.println("updateFrame done ok=" + ok + " fail=" + fail
                    + " phase=" + lastPhase + " pose=" + poseHint
                    + " callbacks=" + callbacks.keySet());
        } finally {
            vm.setVerbose(prevVerbose);
        }
    }

    private String currentPhase() {
        String pose = poseHint == null ? "" : poseHint;
        if (wantReflect || pose.startsWith("20006")) {
            return "reflect";
        }
        if (pose.startsWith("20001") || pose.equals("10006")) {
            return "blink";
        }
        if (pose.startsWith("20002") || pose.startsWith("20003") || pose.startsWith("20004")) {
            return "blink";
        }
        return "prepare";
    }

    private static List<NamedFrame> pickReflectByEv(List<NamedFrame> all, float ev) {
        List<NamedFrame> hit = new ArrayList<NamedFrame>();
        float best = 99f;
        for (int i = 0; i < all.size(); i++) {
            NamedFrame nf = all.get(i);
            if (Float.isNaN(nf.ev)) {
                continue;
            }
            float d = Math.abs(nf.ev - ev);
            if (d + 0.05f < best) {
                best = d;
                hit.clear();
            }
            if (Math.abs(d - best) <= 0.05f) {
                hit.add(nf);
            }
        }
        return hit.isEmpty() ? all : hit;
    }

    private Nv21Frame prepareFeed(Nv21Frame raw) {
        int[] target = parseWxH(propertyOr("huiyan.padTo", ""));
        if (target == null) {
            target = cameraSize;
        }
        double fill = faceFill();
        if (!loggedFeed) {
            loggedFeed = true;
            String src = raw == null ? "null" : (raw.width + "x" + raw.height);
            System.out.println("prepareFeed dest=" + target[0] + "x" + target[1]
                    + " fill=" + fill + " src=" + src
                    + " live=" + liveFeed + " liveCam=" + (liveCam != null));
        }
        if ("true".equalsIgnoreCase(propertyOr("huiyan.scaleUp", "false")) || fill >= 0.995) {
            if (raw != null && raw.width == target[0] && raw.height == target[1]) {
                return raw;
            }
            return coverNv21(raw, target[0], target[1]);
        }
        return fitNv21(raw, target[0], target[1], fill);
    }

    private double faceFill() {
        String raw = propertyOr("huiyan.faceFill", "");
        if (raw != null && !raw.isEmpty()) {
            try {
                return Double.parseDouble(raw);
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (phoneRawNv21) {
            return 1.0;
        }
        if (liveFeed) {
            return 0.92;
        }
        return 0.72;
    }

    /** 0=top, 0.5=center. Official oval sits above center. */
    private double faceY() {
        try {
            return Double.parseDouble(propertyOr("huiyan.faceY", "0.35"));
        } catch (Exception ignored) {
            return 0.35;
        }
    }

    private void encryptZipVideo(Map<String, byte[]> zipEntries, String faceIdToken) {
        byte[] mp4 = zipEntries.get("video_report.mp4");
        if (mp4 == null || mp4.length == 0) {
            for (Map.Entry<String, byte[]> e : zipEntries.entrySet()) {
                if (e.getKey() != null && e.getKey().toLowerCase(java.util.Locale.ROOT).endsWith(".mp4")
                        && e.getValue() != null && e.getValue().length > 0) {
                    mp4 = e.getValue();
                    System.out.println("use zip video " + e.getKey() + " bytes=" + mp4.length);
                    break;
                }
            }
        }
        if (mp4 == null || mp4.length == 0) {
            System.out.println("no video_report.mp4, skip encryptVideoData");
            return;
        }
        String videoB64 = Base64.getEncoder().encodeToString(mp4);
        boolean prev = true;
        vm.setVerbose(false);
        try {
            String enc = callString(nativeApi,
                    "encryptVideoData(Ljava/lang/String;)Ljava/lang/String;", videoB64);
            if (enc == null || enc.isEmpty()) {
                System.out.println("encryptVideoData empty, mp4=" + mp4.length);
                return;
            }
            String videoJson = "{\"faceidToken\":\"" + faceIdToken + "\",\"video\":\"" + enc + "\"}";
            writeOut("unidbg_videoReport.json", videoJson.getBytes(StandardCharsets.UTF_8));
            writeOut("unidbg_encryptVideoData.txt", enc.getBytes(StandardCharsets.UTF_8));
            System.out.println("encryptVideoData chars=" + enc.length() + " mp4=" + mp4.length);
        } catch (Exception e) {
            System.out.println("encryptVideoData failed: " + e);
        } finally {
            vm.setVerbose(prev);
        }
    }

    private static void copyYtModels(File cacheDir) {
        File src = new File(ASSETS, "models/face-tracker-v003");
        if (!src.isDirectory()) {
            System.out.println("missing YT models " + src.getAbsolutePath());
            return;
        }
        try {
            copyTree(src, cacheDir);
            File md5 = new File(cacheDir, "files_md5");
            System.out.println("yt models -> " + cacheDir.getAbsolutePath()
                    + " files_md5=" + md5.isFile() + " bytes=" + (md5.isFile() ? md5.length() : 0));
        } catch (Exception e) {
            throw new IllegalStateException("copy YT models to " + cacheDir, e);
        }
    }

    private static void copyTree(File src, File dst) throws Exception {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) {
                throw new IllegalStateException("mkdir " + dst);
            }
            File[] children = src.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                copyTree(child, new File(dst, child.getName()));
            }
            return;
        }
        Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void initSitExport() {
        String tok = sessionToken == null || sessionToken.isEmpty() ? "notoken" : sessionToken;
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
                .format(new java.util.Date());
        String shortTok = tok.length() > 8 ? tok.substring(0, 8) : tok;
        sitExportDir = new File(OUT_DIR, "sit_" + stamp + "_" + shortTok);
        File images = new File(sitExportDir, "images");
        File packets = new File(sitExportDir, "packets");
        if (!images.mkdirs() || !packets.mkdirs()) {
            System.out.println("sit export mkdir failed " + sitExportDir);
        }
        System.out.println("sit export " + sitExportDir.getAbsolutePath());
    }

    private void exportSitFolder(File liveDir) {
        if (sitExportDir == null) {
            initSitExport();
        }
        File images = new File(sitExportDir, "images");
        File packets = new File(sitExportDir, "packets");
        images.mkdirs();
        packets.mkdirs();
        File camOut = liveDir == null ? null : new File(liveDir, "out");
        if (camOut == null || !camOut.isDirectory()) {
            camOut = new File(OUT_DIR, "flashcam_out/out");
        }
        int nJpg = copyBySuffix(camOut, images, ".jpg");
        copyBySuffix(camOut, images, ".nv21");
        copySit("flashcam_spec.json", packets, "flashcam_spec.json");
        copySit("unidbg_startLiveness.json", packets, "startLiveness.json");
        copySit("unidbg_startLiveness.txt", packets, "startLiveness.txt");
        copySit("unidbg_startLiveness_summary.txt", packets, "startLiveness_summary.txt");
        copySit("unidbg_startLiveness_encryptedRsp.json", packets, "startLiveness_light.json");
        copySit("unidbg_sendLiveness_phone.txt", packets, "sendLiveness_phone.txt");
        copySit("unidbg_sendLiveness_phone.json", packets, "sendLiveness_phone.json");
        copySit("unidbg_sendLiveness.json", packets, "sendLiveness.json");
        copySit("unidbg_sendLiveness.txt", packets, "sendLiveness.txt");
        copySit("unidbg_sendLiveness_summary.txt", packets, "sendLiveness_summary.txt");
        copySit("unidbg_callback_15_inner.json", packets, "sendLiveness_inner.json");
        copySit("unidbg_doLiveness_phone.txt", packets, "doLiveness_phone.txt");
        copySit("unidbg_doLiveness_phone.json", packets, "doLiveness_phone.json");
        copySit("unidbg_doLiveness.json", packets, "doLiveness.json");
        copySit("unidbg_doLiveness.txt", packets, "doLiveness.txt");
        copySit("unidbg_doLiveness_summary.txt", packets, "doLiveness_summary.txt");
        copySit("unidbg_callback_16_inner.json", packets, "doLiveness_inner.json");
        extractPackedJpegs(new File(packets, "sendLiveness_inner.json"),
                new File(images, "pack15"));
        extractPackedJpegs(new File(packets, "doLiveness_inner.json"),
                new File(images, "pack16"));
        System.out.println("sit export done imagesJpg=" + nJpg
                + " dir=" + sitExportDir.getAbsolutePath());
    }

    private void copySit(String srcName, File destDir, String destName) {
        File src = new File(OUT_DIR, srcName);
        if (!src.isFile()) {
            return;
        }
        try {
            Files.copy(src.toPath(), new File(destDir, destName).toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.out.println("copy sit " + srcName + " failed: " + e);
        }
    }

    private static int copyBySuffix(File srcDir, File destDir, String suffix) {
        if (srcDir == null || !srcDir.isDirectory()) {
            return 0;
        }
        File[] files = srcDir.listFiles();
        if (files == null) {
            return 0;
        }
        int n = 0;
        String want = suffix.toLowerCase(java.util.Locale.ROOT);
        for (File f : files) {
            if (!f.isFile() || !f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(want)) {
                continue;
            }
            try {
                Files.copy(f.toPath(), new File(destDir, f.getName()).toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                n++;
            } catch (Exception ignored) {
            }
        }
        return n;
    }

    private void extractPackedJpegs(File innerJson, File destDir) {
        File exe = new File("D:\\project\\libhuiyan-http-android\\extract_pack_images.exe");
        if (!exe.isFile() || innerJson == null || !innerJson.isFile()) {
            return;
        }
        if (!destDir.exists() && !destDir.mkdirs()) {
            return;
        }
        try {
            Process proc = new ProcessBuilder(exe.getAbsolutePath(),
                    "-inner", innerJson.getAbsolutePath(),
                    "-out", destDir.getAbsolutePath())
                    .redirectErrorStream(true)
                    .start();
            byte[] out = readAllLimited(proc.getInputStream(), 4096);
            proc.waitFor();
            System.out.println("extract packed jpeg " + destDir.getName()
                    + " " + new String(out, StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            System.out.println("extract packed jpeg failed: " + e);
        }
    }

    private static byte[] readAllLimited(java.io.InputStream in, int max) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[512];
        int n;
        int total = 0;
        while ((n = in.read(buf)) > 0 && total < max) {
            int take = Math.min(n, max - total);
            bos.write(buf, 0, take);
            total += take;
        }
        return bos.toByteArray();
    }

    private void dumpAllCallbacks() {
        StringBuilder index = new StringBuilder();
        for (Map.Entry<Integer, String> e : callbacks.entrySet()) {
            String text = e.getValue() == null ? "" : e.getValue();
            writeOut("unidbg_callback_" + e.getKey() + ".txt",
                    text.getBytes(StandardCharsets.UTF_8));
            String preview = text.length() <= 80 ? text : text.substring(0, 80) + "...";
            index.append(e.getKey()).append('\t').append(text.length()).append('\t')
                    .append(preview).append('\n');
        }
        writeOut("unidbg_callbacks_index.txt",
                index.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void dumpCallback(int code, String rawName, String jsonName, String summaryName) {
        String packed = callbacks.get(code);
        if (packed == null || packed.isEmpty()) {
            System.out.println("no innerCallBack " + code);
            return;
        }
        int split = packed.indexOf("--");
        String body = split < 0 ? packed : packed.substring(0, split);
        String summary = split < 0 ? "" : packed.substring(split + 2);
        writeOut(rawName, packed.getBytes(StandardCharsets.UTF_8));
        writeOut(jsonName, body.getBytes(StandardCharsets.UTF_8));
        writeOut(summaryName, summary.getBytes(StandardCharsets.UTF_8));
        System.out.println("callback " + code + " body=" + body.length() + " summary=" + summary.length());
    }

    /** Optional: re-wrap phone do EncryptField/Body. Off by default; 16 only resigns like 15. */
    private void rewriteDoPack() {
        File exe = new File("D:\\project\\libhuiyan-http-android\\rewrite_do.exe");
        File doJson = new File(OUT_DIR, "unidbg_doLiveness.json");
        File summary = new File(OUT_DIR, "unidbg_doLiveness_summary.txt");
        File bak = new File(OUT_DIR, "unidbg_doLiveness_so.json");
        if (!exe.isFile() || !doJson.isFile()) {
            System.out.println("rewriteDo skip missing " + exe + " / " + doJson);
            return;
        }
        try {
            Files.copy(doJson.toPath(), bak.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Process proc = new ProcessBuilder(exe.getAbsolutePath())
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            java.io.InputStream in = proc.getInputStream();
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            boolean finished = proc.waitFor(60, TimeUnit.SECONDS);
            String log = out.toString("UTF-8").trim();
            System.out.println("rewriteDo " + (finished ? ("exit=" + proc.exitValue()) : "timeout")
                    + " " + log);
            if (!finished || proc.exitValue() != 0) {
                return;
            }
            String body = new String(Files.readAllBytes(doJson.toPath()), StandardCharsets.UTF_8);
            String sum = "";
            if (summary.isFile()) {
                sum = new String(Files.readAllBytes(summary.toPath()), StandardCharsets.UTF_8).trim();
            }
            callbacks.put(16, sum.isEmpty() ? body : body + "--" + sum);
        } catch (Exception e) {
            System.out.println("rewriteDo failed: " + e);
        }
    }

    /**
     * libhuiyanandroidsdk .data+0x2ea478 is an atomic bool, default 1.
     * updateFrame returns 1024 while it is set. initYtLiveness is supposed
     * to stlrb 0 after CommonProcess start; force-clear if it stayed 1.
     */
    private void enableUpdateFrame() {
        try {
            int pauseRc = callInt(nativeApi, "doResume()I");
            System.out.println("doResume rc=" + pauseRc);
        } catch (Exception e) {
            System.out.println("doResume failed: " + e);
        }
        if (huiyanModule == null) {
            return;
        }
        long addr = huiyanModule.base + 0x2ea478L;
        try {
            byte[] cur = emulator.getBackend().mem_read(addr, 1);
            int before = cur[0] & 0xff;
            if (before != 0) {
                emulator.getBackend().mem_write(addr, new byte[]{0});
            }
            byte[] after = emulator.getBackend().mem_read(addr, 1);
            System.out.println("updateFrame gate 0x" + Long.toHexString(addr)
                    + " before=" + before + " after=" + (after[0] & 0xff));
        } catch (Exception e) {
            System.out.println("updateFrame gate patch failed: " + e);
        }
    }

    /**
     * Official AuthingFragment.k() hands the camera TextureView to native so
     * AndroidShotHelper can TextureView.getBitmap() during reflect.
     * Without this the SO logs "JavaVM is empty / TextureView is null" and Reset.
     */
    private void bindTextureView() {
        try {
            textureView = vm.resolveClass("android/view/TextureView").newObject(null);
            nativeApi.callStaticJniMethod(emulator,
                    "setCurrentTextureView(Landroid/view/TextureView;)V", textureView);
            if (lastPreview == null) {
                lastPreview = new BufferedImage(cameraSize[0], cameraSize[1],
                        BufferedImage.TYPE_4BYTE_ABGR);
            }
            System.out.println("setCurrentTextureView ok preview="
                    + lastPreview.getWidth() + "x" + lastPreview.getHeight());
        } catch (Exception e) {
            System.out.println("setCurrentTextureView failed: " + e);
        }
    }

    private void loadSessionColors() {
        File script = new File("D:\\project\\12306\\12306-5.9.6.6\\scripts\\decrypt_startLiveness.py");
        File params = new File(OUT_DIR, "unidbg_startLiveness_params.json");
        try {
            if (script.isFile()) {
                Process proc = new ProcessBuilder("python", script.getAbsolutePath())
                        .redirectErrorStream(true).start();
                proc.waitFor(20, TimeUnit.SECONDS);
            }
            if (!params.isFile()) {
                System.out.println("no startLiveness params, skip colorData");
                return;
            }
            String js = new String(Files.readAllBytes(params.toPath()), StandardCharsets.UTF_8);
            if (sessionAesKeyHex.length() < 64) {
                sessionAesKeyHex = jsonString(js, "key");
                sessionAesIvHex = jsonString(js, "iv");
            }
            if (sessionAesKeyHex.length() >= 64 && sessionAesIvHex.length() >= 32) {
                System.out.println("session aes key=" + sessionAesKeyHex.length()
                        + " iv=" + sessionAesIvHex.length());
            }
            int unit = jsonInt(js, "unit_ms", 120);
            if (unit > 0) {
                flashUnitMs = unit;
            }
            int[] c0 = jsonIntArray(js, "rgba2");
            int[] c1 = jsonIntArray(js, "rgba3");
            int[] c2 = jsonIntArray(js, "rgba4");
            if (c0 != null && c1 != null && c2 != null) {
                flashArgb = new int[] {rgbaToArgb(c0), rgbaToArgb(c1), rgbaToArgb(c2)};
            }
            int[] loops = jsonIntArray(js, "loop");
            if (loops != null && loops.length >= 3) {
                flashLoops = loops;
            } else {
                flashLoops = new int[] {3, 2, 3};
            }
            StringBuilder sb = new StringBuilder("session colors unit=");
            sb.append(flashUnitMs).append(" loop=");
            if (flashLoops != null) {
                for (int i = 0; i < flashLoops.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(flashLoops[i]);
                }
            }
            if (flashArgb != null) {
                sb.append(" argb=");
                for (int i = 0; i < flashArgb.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(String.format("0x%08x", flashArgb[i]));
                }
            }
            System.out.println(sb);
        } catch (Exception e) {
            System.out.println("loadSessionColors failed: " + e);
        }
    }

    /**
     * Official 12306 default is VideoSize.SIZE_480P; official doLiveness
     * field14 is always 240x320 (half of 480x640). Do not lock 1080.
     */
    private void applyLivePreviewSize() {
        int[] target = parseWxH(propertyOr("huiyan.liveSize", "480x640"));
        if (target == null) {
            target = new int[] {480, 640};
        }
        cameraSize = target;
        lastPreview = new BufferedImage(target[0], target[1], BufferedImage.TYPE_4BYTE_ABGR);
        System.out.println("live preview " + target[0] + "x" + target[1]);
    }

    private int currentFlashArgb() {
        if (liveCb2Argb != 0) {
            return liveCb2Argb;
        }
        if (flashArgb == null || flashArgb.length == 0 || reflectStartMs == 0L) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < flashArgb.length; i++) {
            int n = (flashLoops != null && i < flashLoops.length) ? flashLoops[i] : 3;
            total += Math.max(1, n) * Math.max(40, flashUnitMs);
        }
        if (total <= 0) {
            return flashArgb[0];
        }
        long elapsed = virtualNowMs() - reflectStartMs;
        int t = (int) (elapsed % total);
        int acc = 0;
        for (int i = 0; i < flashArgb.length; i++) {
            int n = (flashLoops != null && i < flashLoops.length) ? flashLoops[i] : 3;
            acc += Math.max(1, n) * Math.max(40, flashUnitMs);
            if (t < acc) {
                return flashArgb[i];
            }
        }
        return flashArgb[flashArgb.length - 1];
    }

    private static int rgbaToArgb(int[] rgba) {
        int r = rgba[0] & 0xff;
        int g = rgba[1] & 0xff;
        int b = rgba[2] & 0xff;
        int a = rgba.length > 3 ? (rgba[3] & 0xff) : 0xf2;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Blend session flash color onto NV21 so reflect sees screen bounce on the face. */
    private static void tintNv21(Nv21Frame src, int argb) {
        if (src == null || src.data == null) {
            return;
        }
        int a = (argb >>> 24) & 0xff;
        if (a == 0) {
            return;
        }
        int r = (argb >> 16) & 0xff;
        int g = (argb >> 8) & 0xff;
        int b = argb & 0xff;
        int yC = clamp(((66 * r + 129 * g + 25 * b + 128) >> 8) + 16);
        int uC = clamp(((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128);
        int vC = clamp(((112 * r - 94 * g - 18 * b + 128) >> 8) + 128);
        int blend = Math.max(32, Math.min(180, (a * 140) / 255));
        int keep = 256 - blend;
        int w = src.width;
        int h = src.height;
        int ySize = w * h;
        for (int i = 0; i < ySize; i++) {
            int y = src.data[i] & 0xff;
            src.data[i] = (byte) ((y * keep + yC * blend) >> 8);
        }
        for (int i = ySize; i + 1 < src.data.length; i += 2) {
            int v = src.data[i] & 0xff;
            int u = src.data[i + 1] & 0xff;
            src.data[i] = (byte) ((v * keep + vC * blend) >> 8);
            src.data[i + 1] = (byte) ((u * keep + uC * blend) >> 8);
        }
    }

    private static int[] jsonIntArray(String js, String key) {
        String needle = "\"" + key + "\"";
        int k = js.lastIndexOf(needle);
        if (k < 0) {
            return null;
        }
        int lb = js.indexOf('[', k);
        int rb = js.indexOf(']', lb);
        if (lb < 0 || rb < 0) {
            return null;
        }
        String[] parts = js.substring(lb + 1, rb).split(",");
        int[] out = new int[parts.length];
        int n = 0;
        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty()) {
                continue;
            }
            try {
                out[n++] = (int) Double.parseDouble(p);
            } catch (Exception ignored) {
            }
        }
        if (n == 0) {
            return null;
        }
        if (n != out.length) {
            int[] slim = new int[n];
            System.arraycopy(out, 0, slim, 0, n);
            return slim;
        }
        return out;
    }

    /** Keep TextureView.getBitmap() on the same canvas as updateFrame (cb25 size). */
    private void updateShotPreview(Nv21Frame frame) {
        if (frame == null || frame.data == null) {
            return;
        }
        lastPreview = nv21ToAbgr(frame);
    }

    private static BufferedImage nv21ToAbgr(Nv21Frame src) {
        int w = src.width;
        int h = src.height;
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
        int ySize = w * h;
        for (int j = 0; j < h; j++) {
            int uvRow = ySize + (j / 2) * w;
            for (int i = 0; i < w; i++) {
                int y = src.data[j * w + i] & 0xff;
                int uv = uvRow + (i & ~1);
                int v = src.data[uv] & 0xff;
                int u = src.data[uv + 1] & 0xff;
                int c = y - 16;
                int d = u - 128;
                int e = v - 128;
                int r = clamp((298 * c + 409 * e + 128) >> 8);
                int g = clamp((298 * c - 100 * d - 208 * e + 128) >> 8);
                int b = clamp((298 * c + 516 * d + 128) >> 8);
                dst.setRGB(i, j, 0xff000000 | (r << 16) | (g << 8) | b);
            }
        }
        return dst;
    }

    private static BufferedImage toAbgr(BufferedImage src) {
        if (src == null) {
            return null;
        }
        if (src.getType() == BufferedImage.TYPE_4BYTE_ABGR) {
            return src;
        }
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    private static BufferedImage scaleAbgr(BufferedImage src, int width, int height) {
        if (src == null || width < 2 || height < 2) {
            return src;
        }
        if (src.getWidth() == width && src.getHeight() == height
                && src.getType() == BufferedImage.TYPE_4BYTE_ABGR) {
            return src;
        }
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return dst;
    }

    /**
     * Same canvas as updateFrame so field14 screenshots match the NV21 the
     * detector cropped. Official 240x320 is just a downscale of 480x640.
     */
    private BufferedImage camPreviewImage(int width, int height) {
        BufferedImage img = lastPreview;
        if (img == null && lastRawNv21 != null) {
            Nv21Frame fed = prepareFeed(lastRawNv21);
            if (liveCam == null) {
                applyExposureNv21(fed, appliedExposure);
            }
            img = nv21ToAbgr(fed);
        }
        if (img == null) {
            return null;
        }
        if (width > 1 && height > 1
                && (img.getWidth() != width || img.getHeight() != height)) {
            img = scaleAbgr(img, width, height);
        }
        return toAbgr(img);
    }

    /** field14 b/e only. Official field15 is often ErrCode=-1 empty. */
    private DvmObject<?> shotTexture(BaseVM vm, int width, int height) {
        shotCount++;
        if (!allowCamShot) {
            System.out.println("TextureView.getBitmap #" + shotCount
                    + " skip(f15) " + width + "x" + height
                    + " pose=" + poseHint);
            return null;
        }
        allowCamShot = false;
        BufferedImage src = camPreviewImage(width, height);
        if (src == null) {
            System.out.println("TextureView.getBitmap #" + shotCount + " empty");
            return null;
        }
        System.out.println("TextureView.getBitmap #" + shotCount
                + " " + src.getWidth() + "x" + src.getHeight()
                + " pose=" + poseHint + " ev=" + appliedExposure
                + " cover=" + cameraSize[0] + "x" + cameraSize[1]);
        return new Bitmap(vm, src);
    }

    private void pumpYtWorker(String why) {
        try {
            int tasks = emulator.getThreadDispatcher().getTaskCount();
            if (tasks <= 0) {
                return;
            }
            if (frameTicks <= 4 || frameTicks % 10 == 0 || why.startsWith("reflect")) {
                System.out.println("yt pump " + why + " tasks=" + tasks
                        + " virt=" + virtualNowMs());
            }
            int waitMs;
            if (why.startsWith("reflect") || why.startsWith("structure")) {
                waitMs = 16;
            } else if (why.startsWith("chrono") || why.startsWith("prepare")
                    || why.startsWith("blink")) {
                waitMs = 33;
            } else {
                waitMs = 400;
            }
            emulator.getThreadDispatcher().runThreads(waitMs, TimeUnit.MILLISECONDS);
            if (frameTicks <= 4 || frameTicks % 10 == 0 || why.startsWith("reflect")) {
                System.out.println("yt pump " + why + " after tasks="
                        + emulator.getThreadDispatcher().getTaskCount());
            }
        } catch (Exception e) {
            System.out.println("yt pump " + why + " failed: " + e);
        }
    }

    private void enableVirtualClock() {
        TraceEnvironmentConfig cfg = TraceEnvironmentConfig.get(emulator);
        if (cfg == null) {
            System.out.println("no TraceEnvironmentConfig, skip virtual clock");
            return;
        }
        long wall = System.currentTimeMillis();
        long mono = Math.max(1L, System.nanoTime() - hostNanoAtCreate);
        cfg.setVirtualTime(wall, mono);
        cfg.setNanosleepAdvancesVirtualTime(false);
        System.out.println("virtual clock on wall=" + wall + " monoNs=" + mono
                + " nanosleepVirt=false");
    }

    private void tickVirtualClock(long deltaMs) {
        TraceEnvironmentConfig cfg = TraceEnvironmentConfig.get(emulator);
        if (cfg == null) {
            return;
        }
        cfg.addVirtualTime(deltaMs);
        frameTicks++;
    }

    private long virtualNowMs() {
        TraceEnvironmentConfig cfg = TraceEnvironmentConfig.get(emulator);
        if (cfg != null) {
            Long wall = cfg.getCurrentTimeMillis();
            if (wall != null) {
                return wall.longValue();
            }
        }
        return System.currentTimeMillis();
    }

    private void hookReflectStop() {
        if (huiyanModule == null) {
            return;
        }
        try {
            hookSo(0x1ab518L, "genSequence");
            hookSo(0x1ab714L, "genSequence.ret");
            hookSo(0x1ab6d4L, "authFailed");
            hookSo(0x1ab6acL, "genSequence.fail");
            hookSo(0x1a5640L, "setRunning");
            hookSo(0xfeae0L, "shotPipeline");
            emulator.attach().addBreakPoint(huiyanModule, 0xb8a24L, new BreakPointCallback() {
                @Override
                public boolean onHit(Emulator<?> em, long address) {
                    int a = (int) em.getContext().getLongArg(1) & 0xff;
                    int r = (int) em.getContext().getLongArg(2) & 0xff;
                    int g = (int) em.getContext().getLongArg(3) & 0xff;
                    int b = (int) em.getContext().getLongArg(4) & 0xff;
                    int argb = (a << 24) | (r << 16) | (g << 8) | b;
                    pendingColorArgb = argb;
                    applyNativeColor(argb);
                    System.out.println("onColorChanged a=" + a + " r=" + r + " g=" + g
                            + " b=" + b + " argb=0x" + Integer.toHexString(argb)
                            + " virt=" + virtualNowMs());
                    return true;
                }
            });
            if (safeUtfBuf == 0L) {
                safeUtfBuf = emulator.getMemory().malloc(65536, false).getPointer().peer;
            }
            emulator.attach().addBreakPoint(huiyanModule, 0xb292cL, new BreakPointCallback() {
                @Override
                public boolean onHit(Emulator<?> em, long address) {
                    try {
                        long sp = em.getContext().getLongByReg(Arm64Const.UC_ARM64_REG_SP);
                        int code = 0;
                        try {
                            byte[] rawCode = em.getBackend().mem_read(sp, 4);
                            code = (rawCode[0] & 0xff)
                                    | ((rawCode[1] & 0xff) << 8)
                                    | ((rawCode[2] & 0xff) << 16)
                                    | ((rawCode[3] & 0xff) << 24);
                        } catch (Exception ignored) {
                        }
                        long cstr = libcppCstr(em, sp + 8L);
                        String text = readLibcppString(em, sp + 8L);
                        if ((text == null || text.isEmpty()) && pendingColorArgb != 0) {
                            text = Integer.toUnsignedString(pendingColorArgb);
                            cstr = 0L;
                        }
                        if (text == null) {
                            text = "";
                        }
                        long dest = cstr;
                        if (dest == 0L) {
                            dest = writeSafeUtf(em, text.length() > 65000 ? "" : text);
                        }
                        if (dest != 0L) {
                            em.getBackend().reg_write(Arm64Const.UC_ARM64_REG_X1, dest);
                        }
                        utfPatchCount++;
                        if (utfPatchCount <= 12 || code == 2 || code == 15 || code == 16
                                || code == 6) {
                            System.out.println("patched NewStringUTF code=" + code
                                    + " chars=" + text.length()
                                    + " preview=" + (text.length() <= 48 ? text
                                    : text.substring(0, 48) + "...")
                                    + " x1=0x" + Long.toHexString(dest));
                        }
                        if (code == 2) {
                            pendingColorArgb = 0;
                        }
                        if ((code == 15 || code == 16) && text.length() > 0) {
                            callbacks.put(code, text);
                            System.out.println("stashed callback " + code
                                    + " chars=" + text.length());
                        }
                    } catch (Exception e) {
                        System.out.println("patch NewStringUTF failed: " + e);
                    }
                    return true;
                }
            });
            hookSo(0x10cc08L, "setReflectionFullData");
            hookSo(0x10cff0L, "setReflectionFullData.enq");
            hookSo(0x10abf0L, "finishActionDetect");
            emulator.attach().addBreakPoint(huiyanModule, 0xeeed4L, new BreakPointCallback() {
                @Override
                public boolean onHit(Emulator<?> em, long address) {
                    inStructure = true;
                    System.out.println("STRUCTURE_STATE from REFLECT_END virt="
                            + virtualNowMs() + " pose=" + poseHint);
                    return true;
                }
            });
            emulator.attach().addBreakPoint(huiyanModule, 0xee740L, new BreakPointCallback() {
                @Override
                public boolean onHit(Emulator<?> em, long address) {
                    System.out.println("make reflection data error (action_state) virt="
                            + virtualNowMs());
                    return true;
                }
            });
            emulator.attach().addBreakPoint(huiyanModule, 0xeec88L, new BreakPointCallback() {
                @Override
                public boolean onHit(Emulator<?> em, long address) {
                    System.out.println("make reflection data error (reflection_state) virt="
                            + virtualNowMs());
                    return true;
                }
            });
            emulator.attach().addBreakPoint(huiyanModule, 0x1ac33cL, new BreakPointCallback() {
                @Override
                public boolean onHit(Emulator<?> em, long address) {
                    long self = em.getContext().getLongArg(0);
                    int cc = -1;
                    if (self != 0L) {
                        try {
                            byte[] raw = em.getBackend().mem_read(self + 0xccL, 4);
                            cc = (raw[0] & 0xff)
                                    | ((raw[1] & 0xff) << 8)
                                    | ((raw[2] & 0xff) << 16)
                                    | ((raw[3] & 0xff) << 24);
                            if (cc == 0) {
                                em.getBackend().mem_write(self + 0xccL, new byte[]{3, 0, 0, 0});
                                System.out.println("startColorThread force cc 0->3 self=0x"
                                        + Long.toHexString(self));
                            }
                        } catch (Exception e) {
                            System.out.println("startColorThread read cc failed: " + e);
                        }
                    }
                    System.out.println("startColorThread self=0x" + Long.toHexString(self)
                            + " cc=" + cc + " virt=" + virtualNowMs());
                    return true;
                }
            });
            emulator.attach().addBreakPoint(huiyanModule, 0x1a797cL, new BreakPointCallback() {
                @Override
                public boolean onHit(Emulator<?> em, long address) {
                    long lr = 0L;
                    try {
                        lr = em.getContext().getLRPointer().peer;
                    } catch (Exception ignored) {
                    }
                    long off = lr == 0L ? 0L : lr - huiyanModule.base;
                    System.out.println("stopReflect hit virt=" + virtualNowMs()
                            + " lr=0x" + Long.toHexString(off)
                            + " pose=" + poseHint
                            + " cb2=" + callbacks.get(2));
                    return true;
                }
            });
            System.out.println("hooked reflect engine + stopReflect");
        } catch (Exception e) {
            System.out.println("hook reflect engine failed: " + e);
        }
    }

    /** libc++ std::string at addr: short=(size<<1)+data[23], long=cap|1 + size + ptr. */
    private static String readLibcppString(Emulator<?> em, long addr) {
        long[] meta = libcppMeta(em, addr);
        if (meta == null) {
            return "";
        }
        long ptr = meta[0];
        int n = (int) meta[1];
        if (ptr == 0L || n <= 0) {
            return "";
        }
        byte[] raw = em.getBackend().mem_read(ptr, n);
        return new String(raw, StandardCharsets.UTF_8);
    }

    /** Returns c_str() of a libc++ string, or 0 if the payload looks unsafe. */
    private static long libcppCstr(Emulator<?> em, long addr) {
        long[] meta = libcppMeta(em, addr);
        if (meta == null || meta[0] == 0L) {
            return 0L;
        }
        try {
            em.getBackend().mem_write(meta[0] + meta[1], new byte[]{0});
        } catch (Exception ignored) {
            return 0L;
        }
        return meta[0];
    }

    /** [ptr, size] or null. */
    private static long[] libcppMeta(Emulator<?> em, long addr) {
        byte[] hdr = em.getBackend().mem_read(addr, 24);
        int flag = hdr[0] & 0xff;
        if ((flag & 1) == 0) {
            int size = Math.min(flag >>> 1, 23);
            return new long[] {addr + 1L, size};
        }
        long size = 0L;
        long ptr = 0L;
        for (int i = 0; i < 8; i++) {
            size |= ((long) (hdr[8 + i] & 0xff)) << (8 * i);
            ptr |= ((long) (hdr[16 + i] & 0xff)) << (8 * i);
        }
        if (ptr == 0L || size < 0L || size > 0x400000L) {
            return null;
        }
        return new long[] {ptr, size};
    }

    private long writeSafeUtf(Emulator<?> em, String text) {
        if (safeUtfBuf == 0L) {
            return 0L;
        }
        if (text == null) {
            text = "";
        }
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        int n = Math.min(raw.length, 65534);
        byte[] packed = new byte[n + 1];
        System.arraycopy(raw, 0, packed, 0, n);
        em.getBackend().mem_write(safeUtfBuf, packed);
        return safeUtfBuf;
    }

    private void applyNativeColor(int argb) {
        if (argb == 0 || argb == 298634444) {
            return;
        }
        liveCb2Argb = argb;
        wantReflect = true;
        if (reflectStartMs == 0L) {
            reflectStartMs = virtualNowMs();
        }
        callbacks.put(2, Integer.toUnsignedString(argb));
    }

    /**
     * skipaes: 0xc6900 AES-decrypt(ytLightData) → identity copy of the C string
     * into the sret libc++ string (x8). Lets dumpProto feed a saved light JSON.
     */
    private void hookYtLightIdentity() {
        if (huiyanModule == null) {
            return;
        }
        if (lightIdentityBuf == 0L) {
            lightIdentityBuf = emulator.getMemory().malloc(512 * 1024, false)
                    .getPointer().peer;
        }
        emulator.attach().addBreakPoint(huiyanModule, 0xc6900L, new BreakPointCallback() {
            @Override
            public boolean onHit(Emulator<?> em, long address) {
                try {
                    long src = em.getContext().getLongArg(0);
                    long dest = em.getContext().getLongByReg(Arm64Const.UC_ARM64_REG_X8);
                    String text = readCString(em, src, 400000);
                    if (text == null) {
                        text = "";
                    }
                    writeLibcppString(em, dest, text, lightIdentityBuf);
                    em.getBackend().reg_write(Arm64Const.UC_ARM64_REG_X0, dest);
                    long lr = em.getContext().getLRPointer().peer;
                    em.getBackend().reg_write(Arm64Const.UC_ARM64_REG_PC, lr);
                    System.out.println("ytLight identity chars=" + text.length()
                            + " head=" + (text.length() <= 48 ? text
                            : text.substring(0, 48).replace('\n', ' ')));
                } catch (Exception e) {
                    System.out.println("ytLight identity failed: " + e);
                }
                return true;
            }
        });
        System.out.println("hooked ytLight identity decrypt @0xc6900");
    }

    /**
     * ModelInterpreter::InterpretProto(std::string&amp;). By this point YouTu has
     * already decrypted *_bin.tnnproto into text. Dump x1 for ytx86.
     */
    private void hookDumpTnnProto() {
        if (huiyanModule == null) {
            return;
        }
        final File dumpDir = new File(
                "D:\\project\\libhuiyan-http-android\\ytx86\\out\\proto_dump");
        dumpDir.mkdirs();
        long[] loadOffs = new long[] {0x11EA90L, 0x120FA0L, 0x121744L, 0x18F3A4L, 0x1257BCL};
        for (final long off : loadOffs) {
            emulator.attach().addBreakPoint(huiyanModule, off, new BreakPointCallback() {
                @Override
                public boolean onHit(Emulator<?> em, long address) {
                    try {
                        String a = readLibcppString(em, em.getContext().getLongArg(0));
                        String b = readLibcppString(em, em.getContext().getLongArg(1));
                        System.out.println("load@" + Long.toHexString(off)
                                + " a=" + trimLog(a, 80) + " b=" + trimLog(b, 80));
                    } catch (Exception e) {
                        System.out.println("load@" + Long.toHexString(off) + " " + e);
                    }
                    return true;
                }
            });
        }
        emulator.attach().addBreakPoint(huiyanModule, 0x164094L, new BreakPointCallback() {
            @Override
            public boolean onHit(Emulator<?> em, long address) {
                try {
                    long p = em.getContext().getLongArg(1);
                    String text = readLibcppString(em, p);
                    if (text == null || text.length() < 40) {
                        text = readLibcppString(em, em.getContext().getLongArg(0));
                    }
                    if (text == null) {
                        text = "";
                    }
                    boolean looks = text.contains("Convolution") || text.contains("Pooling")
                            || text.contains("input ");
                    protoDumpCount++;
                    System.out.println("InterpretProto #" + protoDumpCount
                            + " chars=" + text.length() + " looks=" + looks
                            + " head=" + (text.length() <= 80 ? text
                            : text.substring(0, 80).replace('\n', ' ')));
                    if (looks && text.length() > 80) {
                        String tag = "dump_" + protoDumpCount;
                        if (text.contains("Conv_0") && text.contains(" 36 ")) {
                            tag = "acn_535_net2_36";
                        } else if (text.contains("Conv_0")) {
                            tag = "acn_536_net1_mobileone1000";
                        } else if (text.contains(" 212 ")) {
                            tag = "1102_mbv2_p1_eye.opt";
                        } else if (text.contains(" 246 ")) {
                            tag = "1105_mbv2_eye_ch3.opt";
                        }
                        File f = new File(dumpDir, tag + ".tnnproto");
                        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
                        System.out.println("dumped proto " + f.getAbsolutePath()
                                + " bytes=" + text.length());
                    }
                } catch (Exception e) {
                    System.out.println("InterpretProto dump failed: " + e);
                }
                return true;
            }
        });
        System.out.println("hooked InterpretProto @0x164094 -> " + dumpDir.getAbsolutePath());
    }

    private void hookSo(final long offset, final String name) {
        emulator.attach().addBreakPoint(huiyanModule, offset, new BreakPointCallback() {
            @Override
            public boolean onHit(Emulator<?> em, long address) {
                long x0 = em.getContext().getLongArg(0);
                long x1 = em.getContext().getLongArg(1);
                int w21 = (int) em.getContext().getLongByReg(Arm64Const.UC_ARM64_REG_X21);
                System.out.println("so " + name + " x0=0x" + Long.toHexString(x0)
                        + " x1=0x" + Long.toHexString(x1)
                        + " w21=" + w21
                        + " virt=" + virtualNowMs()
                        + " pose=" + poseHint);
                return true;
            }
        });
    }

    private void postLivenessIfReady(int code) {
        if (code == 15) {
            if (postedSend) {
                return;
            }
            postedSend = true;
            postPackedCallback(15, "https://sdk.faceid.qq.com/api/sdk/v4/sendLivenessData",
                    "unidbg_sendLiveness_http_rsp.txt");
            return;
        }
        if (code == 16) {
            if (postedDo) {
                return;
            }
            postedDo = true;
            postPackedCallback(16, "https://sdk.faceid.qq.com/api/sdk/v4/doLiveness",
                    "unidbg_doLiveness_http_rsp.txt");
        }
    }

    private void postPackedCallback(int code, String url, String outName) {
        String packed = callbacks.get(code);
        if (packed == null || packed.isEmpty()) {
            System.out.println("skip POST " + url + " (no callback " + code + ")");
            return;
        }
        int split = packed.indexOf("--");
        String body = split < 0 ? packed : packed.substring(0, split);
        String summary = split < 0 ? "" : packed.substring(split + 2);
        System.out.println("POST " + url + " body=" + body.length()
                + " summary=" + summary.length() + " gzip=true");
        postJson(url, body, summary, outName);
    }

    private void postVideoReport() {
        File video = new File(OUT_DIR, "unidbg_videoReport.json");
        if (!video.isFile()) {
            System.out.println("skip videoReport (no unidbg_videoReport.json)");
            return;
        }
        try {
            String body = new String(Files.readAllBytes(video.toPath()), StandardCharsets.UTF_8);
            postJson("https://sdk.faceid.qq.com/api/sdk/v4/videoReport", body, "",
                    "unidbg_videoReport_http_rsp.txt");
        } catch (Exception e) {
            System.out.println("videoReport read failed: " + e);
        }
    }

    private String postJson(String url, String body, String summary, String outName) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(url).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("User-Agent", "okhttp/3.14.0");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept-Encoding", "gzip");
            if (summary != null && !summary.isEmpty()) {
                conn.setRequestProperty("summary", summary);
            }
            byte[] raw = body.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream gz = new ByteArrayOutputStream();
            java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(gz);
            gzos.write(raw);
            gzos.close();
            byte[] payload = gz.toByteArray();
            conn.setRequestProperty("Content-Encoding", "gzip");
            conn.setRequestProperty("Content-Length", String.valueOf(payload.length));
            conn.getOutputStream().write(payload);
            int http = conn.getResponseCode();
            java.io.InputStream in = http >= 400 ? conn.getErrorStream() : conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (in != null) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                in.close();
            }
            String rsp = out.toString("UTF-8");
            writeOut(outName, rsp.getBytes(StandardCharsets.UTF_8));
            System.out.println("POST " + url + " HTTP " + http + " chars=" + rsp.length()
                    + " preview=" + (rsp.length() <= 180 ? rsp : rsp.substring(0, 180) + "..."));
            return rsp;
        } catch (Exception e) {
            System.out.println("POST " + url + " failed: " + e);
            return "";
        }
    }

    private byte[] resolveAsset(String fileName) {
        if (fileName == null) {
            return null;
        }
        File file = new File(ASSETS, fileName);
        if (!file.isFile()) {
            return null;
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (Exception e) {
            return null;
        }
    }

    private String callString(DvmClass clazz, String method, Object... args) {
        DvmObject<?> obj = clazz.callStaticJniMethodObject(emulator, method, args);
        if (obj == null) {
            return null;
        }
        Object value = obj.getValue();
        return value == null ? null : String.valueOf(value);
    }

    private int callInt(DvmClass clazz, String method, Object... args) {
        return clazz.callStaticJniMethodInt(emulator, method, args);
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

    private static String jsonEscape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String readLightPlain() {
        File inner = new File(OUT_DIR, "unidbg_startLiveness_encryptedRsp.json");
        if (!inner.isFile()) {
            return "";
        }
        try {
            String js = new String(Files.readAllBytes(inner.toPath()), StandardCharsets.UTF_8).trim();
            js = js.replace("\r", "").replace("\n", "");
            System.out.println("lightPlain chars=" + js.length());
            return js;
        } catch (Exception e) {
            System.out.println("read lightPlain failed: " + e);
            return "";
        }
    }

    private String readSitLightPlain() {
        File[] cands = new File[] {
                new File("D:\\project\\12306\\12306-5.9.6.6\\scripts\\ida_out"
                        + "\\sit_20260825_160802_60FA51BB\\packets\\startLiveness_light.json"),
                new File("D:\\project\\12306\\12306-5.9.6.6\\scripts\\ida_out"
                        + "\\sit_20260821_145233_15C07518\\packets\\startLiveness_light.json"),
        };
        for (File f : cands) {
            if (!f.isFile()) {
                continue;
            }
            try {
                String js = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
                        .trim().replace("\r", "").replace("\n", "");
                if (js.startsWith("{") && js.contains("actionReflectParams")) {
                    System.out.println("sit lightPlain " + f.getName() + " chars=" + js.length());
                    return js;
                }
            } catch (Exception e) {
                System.out.println("read sit light failed: " + e);
            }
        }
        return "";
    }

    private static String trimLog(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }

    private static String readCString(Emulator<?> em, long addr, int max) {
        if (addr == 0L) {
            return "";
        }
        int cap = Math.max(64, Math.min(max, 512 * 1024));
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        long p = addr;
        int got = 0;
        while (got < cap) {
            int chunk = Math.min(256, cap - got);
            byte[] raw;
            try {
                raw = em.getBackend().mem_read(p, chunk);
            } catch (Exception e) {
                break;
            }
            int n = 0;
            while (n < raw.length && raw[n] != 0) {
                n++;
            }
            if (n > 0) {
                bos.write(raw, 0, n);
                got += n;
            }
            if (n < raw.length) {
                break;
            }
            p += chunk;
        }
        if (bos.size() == 0) {
            return "";
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    /** libc++ string at dest; payload lives in preallocated buf (no malloc in-hook). */
    private static void writeLibcppString(Emulator<?> em, long dest, String text, long buf) {
        if (dest == 0L) {
            return;
        }
        if (text == null) {
            text = "";
        }
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        int n = Math.min(raw.length, 512 * 1024 - 1);
        byte[] hdr = new byte[24];
        if (n < 23) {
            hdr[0] = (byte) (n << 1);
            if (n > 0) {
                System.arraycopy(raw, 0, hdr, 1, n);
            }
            em.getBackend().mem_write(dest, hdr);
            return;
        }
        if (buf == 0L) {
            return;
        }
        byte[] packed = new byte[n + 1];
        System.arraycopy(raw, 0, packed, 0, n);
        em.getBackend().mem_write(buf, packed);
        hdr[0] = 1;
        long size = n;
        long ptr = buf;
        for (int i = 0; i < 8; i++) {
            hdr[8 + i] = (byte) (size >> (8 * i));
            hdr[16 + i] = (byte) (ptr >> (8 * i));
        }
        em.getBackend().mem_write(dest, hdr);
    }

    private static int frameOrder(String name) {
        String path = name == null ? "" : name.replace('\\', '/');
        String base = path;
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            base = path.substring(slash + 1);
        }
        int bucket = 500;
        if (path.contains("send_PREPARED")) {
            bucket = 0;
        } else if (path.contains("send_BLINKEYE")) {
            bucket = 1000;
        } else if (path.contains("send_REFLECT")) {
            bucket = 2000;
        } else if (path.contains("send_tx")) {
            bucket = 3000;
        } else if (path.contains("recv_")) {
            bucket = 9000;
        } else if (base.startsWith("frame_start")) {
            return 0;
        } else if (base.startsWith("frame_normal")) {
            return 1;
        } else if (base.startsWith("frame_bright")) {
            return 2;
        } else if (base.startsWith("frame_end")) {
            return 1000;
        } else if (base.startsWith("frame_")) {
            int under = base.indexOf('_', 6);
            if (under > 6) {
                try {
                    return 100 + Integer.parseInt(base.substring(6, under));
                } catch (Exception ignored) {
                    return 100;
                }
            }
        }
        return bucket * 10000 + frameSeq(base);
    }

    private static int frameSeq(String name) {
        int under = name.lastIndexOf('_');
        int dot = name.lastIndexOf('.');
        if (under >= 0 && dot > under + 1) {
            try {
                return Integer.parseInt(name.substring(under + 1, dot));
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static Map<String, byte[]> readZip(File zip) {
        Map<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        if (zip == null || !zip.isFile()) {
            return out;
        }
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] tmp = new byte[4096];
                int n;
                while ((n = zin.read(tmp)) > 0) {
                    buf.write(tmp, 0, n);
                }
                String name = entry.getName().replace('\\', '/');
                out.put(name, buf.toByteArray());
            }
        } catch (Exception e) {
            throw new IllegalStateException("read zip " + zip, e);
        }
        return out;
    }

    private static String loadRiskData() throws Exception {
        if (RISK_READY.isFile()) {
            String text = new String(Files.readAllBytes(RISK_READY.toPath()), StandardCharsets.US_ASCII).trim();
            System.out.println("initDeviceRiskData from " + RISK_READY.getName() + " chars=" + text.length());
            return text;
        }
        if (RISK_FRONT.isFile()) {
            String text = new String(Files.readAllBytes(RISK_FRONT.toPath()), StandardCharsets.US_ASCII).trim();
            System.out.println("initDeviceRiskData from " + RISK_FRONT.getName() + " chars=" + text.length());
            return text;
        }
        File src = RISK_PLAIN.isFile() ? RISK_PLAIN : (RISK_HTTP.isFile() ? RISK_HTTP : null);
        if (src == null) {
            System.out.println("no 8123 dump, initDeviceRiskData=\"\"");
            return "";
        }
        byte[] raw = Files.readAllBytes(src.toPath());
        String b64 = Base64.getEncoder().encodeToString(raw);
        System.out.println("initDeviceRiskData from " + src.getName() + " bytes=" + raw.length);
        return b64;
    }

    private String[] postGetConfig(String faceIdToken) {
        String[] out = new String[] {"", ""};
        try {
            String json = "{\"encryptAlgorithm\":0,\"faceidToken\":\"" + faceIdToken + "\"}";
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL("https://sdk.faceid.qq.com/api/sdk/v4/getConfig").openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("User-Agent", "okhttp/3.14.0");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(payload.length));
            conn.getOutputStream().write(payload);
            int code = conn.getResponseCode();
            java.io.InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            if (in != null) {
                byte[] tmp = new byte[4096];
                int n;
                while ((n = in.read(tmp)) > 0) {
                    buf.write(tmp, 0, n);
                }
                in.close();
            }
            String rsp = buf.toString("UTF-8");
            writeOut("unidbg_getConfig_http_rsp.txt", rsp.getBytes(StandardCharsets.UTF_8));
            System.out.println("getConfig HTTP " + code + " chars=" + rsp.length());
            out[0] = jsonString(rsp, "publicKey");
            out[1] = jsonString(rsp, "nonce");
            System.out.println("getConfig publicKeyChars=" + out[0].length() + " nonce=" + out[1]);
        } catch (Exception e) {
            System.out.println("getConfig HTTP failed: " + e);
        }
        return out;
    }

    private static String jsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return "";
        }
        int colon = json.indexOf(':', idx + needle.length());
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) {
            return "";
        }
        return json.substring(q1 + 1, q2);
    }

    private static int jsonInt(String json, String key, int fallback) {
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return fallback;
        }
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return fallback;
        }
        int i = colon + 1;
        while (i < json.length() && json.charAt(i) == ' ') {
            i++;
        }
        int j = i;
        if (j < json.length() && json.charAt(j) == '-') {
            j++;
        }
        while (j < json.length() && json.charAt(j) >= '0' && json.charAt(j) <= '9') {
            j++;
        }
        if (j == i || (j == i + 1 && json.charAt(i) == '-')) {
            return fallback;
        }
        try {
            return Integer.parseInt(json.substring(i, j));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double jsonDouble(String json, String key, double fallback) {
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return fallback;
        }
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return fallback;
        }
        int i = colon + 1;
        while (i < json.length() && json.charAt(i) == ' ') {
            i++;
        }
        int j = i;
        if (j < json.length() && json.charAt(j) == '-') {
            j++;
        }
        boolean dot = false;
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c >= '0' && c <= '9') {
                j++;
            } else if (c == '.' && !dot) {
                dot = true;
                j++;
            } else {
                break;
            }
        }
        if (j <= i) {
            return fallback;
        }
        try {
            return Double.parseDouble(json.substring(i, j));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean jsonBool(String json, String key, boolean fallback) {
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return fallback;
        }
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return fallback;
        }
        int i = colon + 1;
        while (i < json.length() && json.charAt(i) == ' ') {
            i++;
        }
        if (json.startsWith("true", i)) {
            return true;
        }
        if (json.startsWith("false", i)) {
            return false;
        }
        return fallback;
    }

    private static void writeOut(String fileName, byte[] data) {
        if (!OUT_DIR.exists() && !OUT_DIR.mkdirs()) {
            throw new IllegalStateException("mkdir " + OUT_DIR);
        }
        File out = new File(OUT_DIR, fileName);
        try {
            Files.write(out.toPath(), data);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("write " + out, e);
        }
        System.out.println("wrote " + out.getAbsolutePath() + " bytes=" + data.length);
    }

    private static int[] parseWxH(String text) {
        if (text == null || text.isEmpty() || "none".equalsIgnoreCase(text)) {
            return null;
        }
        int x = text.toLowerCase(java.util.Locale.ROOT).indexOf('x');
        if (x <= 0) {
            return null;
        }
        try {
            int w = Integer.parseInt(text.substring(0, x).trim()) & ~1;
            int h = Integer.parseInt(text.substring(x + 1).trim()) & ~1;
            if (w < 2 || h < 2) {
                return null;
            }
            return new int[] {w, h};
        } catch (Exception e) {
            return null;
        }
    }

    /** Scale to cover dest and center-crop. Used for real camera frames. */
    private static Nv21Frame coverNv21(Nv21Frame src, int destW, int destH) {
        destW &= ~1;
        destH &= ~1;
        if (src == null || destW < 2 || destH < 2) {
            return src;
        }
        if (src.width == destW && src.height == destH) {
            byte[] data = new byte[src.data.length];
            System.arraycopy(src.data, 0, data, 0, src.data.length);
            return new Nv21Frame(data, src.width, src.height);
        }
        int fitW;
        int fitH;
        if ((long) src.width * destH < (long) destW * src.height) {
            fitW = destW;
            fitH = Math.max(2, (src.height * destW / src.width) & ~1);
        } else {
            fitH = destH;
            fitW = Math.max(2, (src.width * destH / src.height) & ~1);
        }
        Nv21Frame scaled = (fitW == src.width && fitH == src.height)
                ? src : scaleNv21(src, fitW, fitH);
        if (scaled.width == destW && scaled.height == destH) {
            return scaled;
        }
        byte[] nv21 = new byte[destW * destH * 3 / 2];
        int ox = Math.max(0, ((scaled.width - destW) / 2) & ~1);
        int oy = Math.max(0, ((scaled.height - destH) / 2) & ~1);
        int copyW = Math.min(destW, scaled.width);
        int copyH = Math.min(destH, scaled.height);
        for (int j = 0; j < copyH; j++) {
            System.arraycopy(scaled.data, (oy + j) * scaled.width + ox,
                    nv21, j * destW, copyW);
        }
        int srcUv = scaled.width * scaled.height;
        int dstUv = destW * destH;
        for (int j = 0; j < copyH; j += 2) {
            System.arraycopy(scaled.data, srcUv + ((oy + j) / 2) * scaled.width + ox,
                    nv21, dstUv + (j / 2) * destW, copyW);
        }
        return new Nv21Frame(nv21, destW, destH);
    }

    /** Brighten/darken NV21 by EV stops. +2 EV is about 4x luma, matching cb23 e. */
    private static void applyExposureNv21(Nv21Frame src, float ev) {
        if (src == null || src.data == null || Math.abs(ev) < 0.05f) {
            return;
        }
        double gain = Math.pow(2.0, ev);
        int ySize = src.width * src.height;
        for (int i = 0; i < ySize; i++) {
            int y = src.data[i] & 0xff;
            src.data[i] = (byte) clamp(16 + (int) Math.round((y - 16) * gain));
        }
        if (gain > 1.15) {
            int pull = (int) Math.min(90, (gain - 1.0) * 48);
            for (int i = ySize; i < src.data.length; i++) {
                int u = src.data[i] & 0xff;
                src.data[i] = (byte) clamp(u + ((128 - u) * pull) / 256);
            }
        }
    }

    /**
     * Scale the original 480x640 so the face occupies {@code fill} of the
     * destination, then letterbox. 1.0 was too close (10012 / 182-wide 1001).
     */
    private Nv21Frame fitNv21(Nv21Frame src, int destW, int destH, double fill) {
        destW &= ~1;
        destH &= ~1;
        if (src == null || destW < 2 || destH < 2) {
            return src;
        }
        if (fill < 0.3) {
            fill = 0.3;
        }
        if (fill > 0.95) {
            fill = 0.95;
        }
        int boxW = Math.max(2, ((int) Math.round(destW * fill)) & ~1);
        int boxH = Math.max(2, ((int) Math.round(destH * fill)) & ~1);
        int fitW;
        int fitH;
        if ((long) src.width * boxH > (long) boxW * src.height) {
            fitW = boxW;
            fitH = Math.max(2, (src.height * boxW / src.width) & ~1);
        } else {
            fitH = boxH;
            fitW = Math.max(2, (src.width * boxH / src.height) & ~1);
        }
        return padNv21(scaleNv21(src, fitW, fitH), destW, destH, faceY());
    }

    /** Place the preview on a larger canvas. faceY 0=top, 0.5=center. */
    private static Nv21Frame padNv21(Nv21Frame src, int destW, int destH, double faceY) {
        destW &= ~1;
        destH &= ~1;
        if (src == null || destW < 2 || destH < 2 || (src.width == destW && src.height == destH)) {
            return src;
        }
        Nv21Frame placed = src;
        if (src.width > destW || src.height > destH) {
            int fitW = destW;
            int fitH = destH;
            if ((long) src.width * destH > (long) destW * src.height) {
                fitH = Math.max(2, (src.height * destW / src.width) & ~1);
            } else {
                fitW = Math.max(2, (src.width * destH / src.height) & ~1);
            }
            placed = scaleNv21(src, fitW, fitH);
        }
        byte[] nv21 = new byte[destW * destH * 3 / 2];
        java.util.Arrays.fill(nv21, 0, destW * destH, (byte) 16);
        java.util.Arrays.fill(nv21, destW * destH, nv21.length, (byte) 128);
        if (faceY < 0.0) {
            faceY = 0.0;
        }
        if (faceY > 1.0) {
            faceY = 1.0;
        }
        int ox = ((destW - placed.width) / 2) & ~1;
        int extraY = destH - placed.height;
        int oy = ((int) Math.round(extraY * faceY)) & ~1;
        if (oy < 0) {
            oy = 0;
        }
        if (oy > extraY) {
            oy = extraY & ~1;
        }
        for (int j = 0; j < placed.height; j++) {
            System.arraycopy(placed.data, j * placed.width,
                    nv21, (oy + j) * destW + ox, placed.width);
        }
        int srcUv = placed.width * placed.height;
        int dstUv = destW * destH;
        for (int j = 0; j < placed.height; j += 2) {
            System.arraycopy(placed.data, srcUv + (j / 2) * placed.width,
                    nv21, dstUv + ((oy + j) / 2) * destW + ox, placed.width);
        }
        return new Nv21Frame(nv21, destW, destH);
    }

    private static Nv21Frame scaleNv21(Nv21Frame src, int destW, int destH) {
        if (src == null || (src.width == destW && src.height == destH)) {
            return src;
        }
        byte[] nv21 = new byte[destW * destH * 3 / 2];
        int yOut = 0;
        int uvOut = destW * destH;
        for (int j = 0; j < destH; j++) {
            int sj = Math.min(src.height - 1, j * src.height / destH);
            for (int i = 0; i < destW; i++) {
                int si = Math.min(src.width - 1, i * src.width / destW);
                nv21[yOut++] = src.data[sj * src.width + si];
                if ((j & 1) == 0 && (i & 1) == 0) {
                    int srcUv = src.width * src.height + (sj / 2) * src.width + (si & ~1);
                    nv21[uvOut++] = src.data[srcUv];
                    nv21[uvOut++] = src.data[srcUv + 1];
                }
            }
        }
        return new Nv21Frame(nv21, destW, destH);
    }

    private static Nv21Frame loadNv21File(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            int[] wh = nv21SizeFromName(file.getName());
            int w = wh[0];
            int h = wh[1];
            int expect = w * h * 3 / 2;
            if (data.length != expect) {
                if (data.length == 480 * 640 * 3 / 2) {
                    w = 480;
                    h = 640;
                } else {
                    System.out.println("nv21 size mismatch " + file.getName()
                            + " bytes=" + data.length + " expect=" + expect);
                    return null;
                }
            }
            return new Nv21Frame(data, w, h);
        } catch (Exception e) {
            System.out.println("read nv21 failed " + file.getName() + " " + e);
            return null;
        }
    }

    private static int[] nv21SizeFromName(String name) {
        int[] def = new int[] {480, 640};
        if (name == null) {
            return def;
        }
        String base = name;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        int x = base.lastIndexOf('x');
        int us = base.lastIndexOf('_');
        if (x <= us + 1 || us < 0) {
            return def;
        }
        try {
            int w = Integer.parseInt(base.substring(us + 1, x));
            int h = Integer.parseInt(base.substring(x + 1));
            if (w >= 2 && h >= 2 && (w & 1) == 0 && (h & 1) == 0) {
                return new int[] {w, h};
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    private static Nv21Frame jpegToNv21(byte[] jpeg) {
        if (jpeg == null || jpeg.length == 0) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
            if (image == null) {
                return null;
            }
            image = applyExifOrientation(image, jpegExifOrientation(jpeg));
            int width = image.getWidth() & ~1;
            int height = image.getHeight() & ~1;
            if (width < 2 || height < 2) {
                return null;
            }
            byte[] nv21 = new byte[width * height * 3 / 2];
            int yIndex = 0;
            int uvIndex = width * height;
            for (int j = 0; j < height; j++) {
                for (int i = 0; i < width; i++) {
                    int argb = image.getRGB(i, j);
                    int r = (argb >> 16) & 0xff;
                    int g = (argb >> 8) & 0xff;
                    int b = argb & 0xff;
                    int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                    nv21[yIndex++] = (byte) clamp(y);
                    if ((j & 1) == 0 && (i & 1) == 0) {
                        int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                        int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                        nv21[uvIndex++] = (byte) clamp(v);
                        nv21[uvIndex++] = (byte) clamp(u);
                    }
                }
            }
            return new Nv21Frame(nv21, width, height);
        } catch (Exception e) {
            return null;
        }
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }

    /** EXIF Orientation tag: 1=normal, 3=180, 6=90CW, 8=90CCW. */
    private static int jpegExifOrientation(byte[] jpeg) {
        if (jpeg == null || jpeg.length < 12 || (jpeg[0] & 0xff) != 0xff || (jpeg[1] & 0xff) != 0xd8) {
            return 1;
        }
        int i = 2;
        while (i + 8 < jpeg.length) {
            if ((jpeg[i] & 0xff) != 0xff) {
                break;
            }
            int marker = jpeg[i + 1] & 0xff;
            int len = ((jpeg[i + 2] & 0xff) << 8) | (jpeg[i + 3] & 0xff);
            if (marker == 0xe1 && i + 10 < jpeg.length && len >= 8) {
                if (jpeg[i + 4] == 'E' && jpeg[i + 5] == 'x' && jpeg[i + 6] == 'i' && jpeg[i + 7] == 'f') {
                    return tiffOrientation(jpeg, i + 10, i + 2 + len);
                }
            }
            if (len < 2) {
                break;
            }
            i += 2 + len;
        }
        return 1;
    }

    private static int tiffOrientation(byte[] jpeg, int tiff, int end) {
        if (tiff + 8 >= end || tiff + 8 >= jpeg.length) {
            return 1;
        }
        boolean le = jpeg[tiff] == 'I' && jpeg[tiff + 1] == 'I';
        boolean be = jpeg[tiff] == 'M' && jpeg[tiff + 1] == 'M';
        if (!le && !be) {
            return 1;
        }
        int ifd = tiff + u32(jpeg, tiff + 4, le);
        if (ifd + 2 >= jpeg.length) {
            return 1;
        }
        int count = u16(jpeg, ifd, le);
        for (int n = 0; n < count; n++) {
            int off = ifd + 2 + n * 12;
            if (off + 12 > jpeg.length) {
                break;
            }
            int tag = u16(jpeg, off, le);
            if (tag == 0x0112) {
                return u16(jpeg, off + 8, le);
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int off, boolean le) {
        int a = b[off] & 0xff;
        int c = b[off + 1] & 0xff;
        return le ? (a | (c << 8)) : ((a << 8) | c);
    }

    private static int u32(byte[] b, int off, boolean le) {
        int a = b[off] & 0xff;
        int c = b[off + 1] & 0xff;
        int d = b[off + 2] & 0xff;
        int e = b[off + 3] & 0xff;
        return le ? (a | (c << 8) | (d << 16) | (e << 24))
                : ((a << 24) | (c << 16) | (d << 8) | e);
    }

    private static BufferedImage applyExifOrientation(BufferedImage src, int orient) {
        if (src == null || orient == 1 || orient == 0) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        int dw = (orient == 6 || orient == 8) ? h : w;
        int dh = (orient == 6 || orient == 8) ? w : h;
        BufferedImage dst = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        if (orient == 3) {
            g.translate(w, h);
            g.rotate(Math.PI);
        } else if (orient == 6) {
            g.translate(h, 0);
            g.rotate(Math.PI / 2);
        } else if (orient == 8) {
            g.translate(0, w);
            g.rotate(-Math.PI / 2);
        } else {
            g.dispose();
            return src;
        }
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    private static final class Nv21Frame {
        final byte[] data;
        final int width;
        final int height;

        Nv21Frame(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }
    }

    private static final class NamedFrame {
        final String name;
        final Nv21Frame frame;
        final byte[] jpeg;
        final float ev;

        NamedFrame(String name, Nv21Frame frame, byte[] jpeg) {
            this.name = name;
            this.frame = frame;
            this.jpeg = jpeg;
            this.ev = evFromName(name);
        }

        static float evFromName(String name) {
            if (name == null) {
                return Float.NaN;
            }
            int i = name.indexOf("_e");
            if (i < 0) {
                return Float.NaN;
            }
            int j = name.indexOf('_', i + 2);
            if (j < 0) {
                j = name.indexOf('.', i + 2);
            }
            if (j < 0) {
                return Float.NaN;
            }
            try {
                return Integer.parseInt(name.substring(i + 2, j)) / 10f;
            } catch (Exception e) {
                return Float.NaN;
            }
        }
    }

    @Override
    public void callStaticVoidMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        if (handleInnerCallBack(signature, varArg)) {
            return;
        }
        super.callStaticVoidMethod(vm, dvmClass, signature, varArg);
    }

    @Override
    public void callStaticVoidMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        if (handleInnerCallBack(signature, vaList)) {
            return;
        }
        super.callStaticVoidMethodV(vm, dvmClass, signature, vaList);
    }

    @Override
    public DvmObject<?> callStaticObjectMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        DvmObject<?> handled = handleStaticObject(vm, signature, varArg);
        if (handled != null) {
            return handled;
        }
        return super.callStaticObjectMethod(vm, dvmClass, signature, varArg);
    }

    @Override
    public DvmObject<?> callStaticObjectMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        DvmObject<?> handled = handleStaticObject(vm, signature, vaList);
        if (handled != null) {
            return handled;
        }
        return super.callStaticObjectMethodV(vm, dvmClass, signature, vaList);
    }

    private boolean handleInnerCallBack(String signature, Object args) {
        if (!signature.contains("HuiYanNativeApi->innerCallBack(ILjava/lang/String;)V")) {
            return false;
        }
        int code;
        Object msg;
        if (args instanceof VaList) {
            code = ((VaList) args).getIntArg(0);
            msg = unwrap(((VaList) args).getObjectArg(1));
        } else {
            code = ((VarArg) args).getIntArg(0);
            msg = unwrap(((VarArg) args).getObjectArg(1));
        }
        String text = msg == null ? "" : String.valueOf(msg);
        callbacks.put(code, text);
        String preview = text.length() <= 160 ? text : text.substring(0, 160) + "...";
        System.out.println("innerCallBack code=" + code + " chars=" + text.length() + " msg=" + preview);
        if (code == 0) {
            poseHint = text;
        } else if (code == 2 && text.length() > 0) {
            if (!"298634444".equals(text)) {
                wantReflect = true;
                if (reflectStartMs == 0L) {
                    reflectStartMs = virtualNowMs();
                }
                try {
                    liveCb2Argb = (int) Long.parseLong(text);
                    System.out.println("cb2 color 0x" + Integer.toHexString(liveCb2Argb));
                } catch (Exception ignored) {
                }
            }
        } else if (code == 7 && text.startsWith("1")) {
            // first field 1 = ALL_ACTION_DONE; mode 4 then goes to reflect.
            wantReflect = true;
            System.out.println("all actions done, switch to reflect");
        } else if (code == 15 || code == 16) {
            if (!"false".equalsIgnoreCase(propertyOr("huiyan.postSend", "true"))) {
                postLivenessIfReady(code);
            }
        } else if (code == 25) {
            int[] size = parseCbSize(text);
            if (size != null) {
                if (liveFeed) {
                    System.out.println("cb25 " + size[0] + "x" + size[1]
                            + " live keep " + cameraSize[0] + "x" + cameraSize[1]);
                } else {
                    // F1B320D6 (CS_11 zip, send 0): start 480, follow cb25 to 960.
                    // Adopting 1080x1440 made send 4 and later stuck the blink at 20001.
                    int[] adopted = capPreviewSize(size);
                    cameraSize = adopted;
                    if (lastPreview == null || lastPreview.getWidth() != adopted[0]
                            || lastPreview.getHeight() != adopted[1]) {
                        lastPreview = new BufferedImage(adopted[0], adopted[1],
                                BufferedImage.TYPE_4BYTE_ABGR);
                    }
                    System.out.println("camera size from cb25 " + size[0] + "x" + size[1]
                            + (adopted[0] != size[0] || adopted[1] != size[1]
                            ? " cap " + adopted[0] + "x" + adopted[1] : ""));
                }
            }
        } else if (code == 23 && !"false".equalsIgnoreCase(propertyOr("huiyan.replyCamera", "true"))) {
            pendingCameraReply = text;
        }
        return true;
    }

    /**
     * F1B320D6 followed 480→960 and send=0. Larger than 960 (1080x1440)
     * historically send=4 or blink-stuck. Smaller sizes are kept so the
     * first frames stay 480P; do not pad-to-960 from frame 1 (that was 10013).
     */
    private int[] capPreviewSize(int[] size) {
        int[] cap = parseWxH(propertyOr("huiyan.maxPreview", "960x1280"));
        if (cap == null) {
            cap = new int[] {960, 1280};
        }
        if (size[0] > cap[0] || size[1] > cap[1]) {
            return cap;
        }
        return size;
    }

    private static int[] parseCbSize(String text) {
        if (text == null) {
            return null;
        }
        int sep = text.indexOf("--");
        if (sep <= 0) {
            return parseWxH(text);
        }
        try {
            int w = Integer.parseInt(text.substring(0, sep).trim()) & ~1;
            int h = Integer.parseInt(text.substring(sep + 2).trim()) & ~1;
            if (w < 2 || h < 2) {
                return null;
            }
            return new int[] {w, h};
        } catch (Exception e) {
            return null;
        }
    }

    private void flushCameraReply() {
        String req = pendingCameraReply;
        if (req == null) {
            return;
        }
        pendingCameraReply = null;
        replyCameraParams(req);
    }

    private void replyCameraParams(String req) {
        int requestId = jsonInt(req, "requestId", 1);
        String comment = jsonString(req, "comment");
        if (comment == null || comment.isEmpty()) {
            comment = "reset";
        }
        boolean reset = jsonBool(req, "reset", false);
        boolean needEv = jsonBool(req, "needChangeExposure", false);
        boolean needWb = jsonBool(req, "needChangeWhiteBalance", false);
        boolean needZoom = jsonBool(req, "needChangeZoom", false);
        float wantEv = (float) jsonDouble(req, "exposure", appliedExposure);
        int wantWb = jsonInt(req, "whiteBalance", appliedWhiteBalance);
        int wantZoom = jsonInt(req, "zoom", appliedZoom);
        if (reset) {
            appliedExposure = 0f;
            appliedWhiteBalance = 0;
            appliedZoom = 100;
            allowCamShot = false;
        } else {
            if (needEv) {
                appliedExposure = Math.round(wantEv * 10f) / 10f;
            }
            if (needWb) {
                appliedWhiteBalance = wantWb;
            }
            if (needZoom && wantZoom > 0) {
                appliedZoom = wantZoom;
            }
            allowCamShot = "b".equals(comment) || "e".equals(comment);
        }
        if (liveCam != null) {
            liveCam.sendEv(appliedExposure);
        }
        String rsp = "{"
                + "\"code\":0,"
                + "\"message\":\"camera params set success\","
                + "\"requestId\":" + requestId + ","
                + "\"comment\":\"" + jsonEscape(comment) + "\","
                + "\"whiteBalance\":" + appliedWhiteBalance + ","
                + "\"exposure\":" + appliedExposure + ","
                + "\"zoom\":" + appliedZoom + ","
                + "\"needChangeWhiteBalance\":false,"
                + "\"needChangeExposure\":false,"
                + "\"needChangeZoom\":false,"
                + "\"reset\":false"
                + "}";
        try {
            boolean ok = nativeApi.callStaticJniMethodBoolean(emulator,
                    "callbackToPPL(ILjava/lang/String;)Z", 23, rsp);
            System.out.println("callbackToPPL 23 ok=" + ok
                    + " reqId=" + requestId + " comment=" + comment
                    + " ev=" + appliedExposure + " zoom=" + appliedZoom
                    + " shot=" + allowCamShot);
        } catch (Exception e) {
            System.out.println("callbackToPPL 23 failed: " + e);
        }
    }

    private DvmObject<?> handleStaticObject(BaseVM vm, String signature, Object args) {
        if (signature.equals("android/graphics/Bitmap->createBitmap(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;")) {
            int w = Math.max(2, intArg(args, 0));
            int h = Math.max(2, intArg(args, 1));
            return new Bitmap(vm, new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR));
        }
        return null;
    }

    @Override
    public DvmObject<?> callObjectMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        DvmObject<?> handled = handleObjectCall(vm, signature, varArg);
        if (handled != null) {
            return handled;
        }
        return super.callObjectMethod(vm, dvmObject, signature, varArg);
    }

    @Override
    public DvmObject<?> callObjectMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        DvmObject<?> handled = handleObjectCall(vm, signature, vaList);
        if (handled != null) {
            return handled;
        }
        return super.callObjectMethodV(vm, dvmObject, signature, vaList);
    }

    private DvmObject<?> handleObjectCall(BaseVM vm, String signature, Object args) {
        if (signature.equals("android/content/Context->getAssets()Landroid/content/res/AssetManager;")
                || signature.equals("android/app/Application->getAssets()Landroid/content/res/AssetManager;")) {
            return new AssetManager(vm, "assets");
        }
        if (signature.equals("android/view/TextureView->getBitmap()Landroid/graphics/Bitmap;")) {
            return shotTexture(vm, 0, 0);
        }
        if (signature.equals("android/view/TextureView->getBitmap(II)Landroid/graphics/Bitmap;")) {
            int w = intArg(args, 0);
            int h = intArg(args, 1);
            return shotTexture(vm, w, h);
        }
        if (signature.equals("android/view/TextureView->getBitmap(Landroid/graphics/Bitmap;)Landroid/graphics/Bitmap;")) {
            return shotTexture(vm, 0, 0);
        }
        if (signature.equals("android/graphics/Bitmap->getConfig()Landroid/graphics/Bitmap$Config;")) {
            return vm.resolveClass("android/graphics/Bitmap$Config").newObject("ARGB_8888");
        }
        return null;
    }

    @Override
    public int callIntMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        Integer v = handleIntCall(signature);
        if (v != null) {
            return v;
        }
        return super.callIntMethod(vm, dvmObject, signature, varArg);
    }

    @Override
    public int callIntMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        Integer v = handleIntCall(signature);
        if (v != null) {
            return v;
        }
        return super.callIntMethodV(vm, dvmObject, signature, vaList);
    }

    private Integer handleIntCall(String signature) {
        if (signature.equals("android/view/TextureView->getWidth()I")
                || signature.equals("android/view/TextureView->getMeasuredWidth()I")
                || signature.equals("android/view/View->getWidth()I")) {
            return cameraSize[0];
        }
        if (signature.equals("android/view/TextureView->getHeight()I")
                || signature.equals("android/view/TextureView->getMeasuredHeight()I")
                || signature.equals("android/view/View->getHeight()I")) {
            return cameraSize[1];
        }
        if (signature.equals("android/graphics/Bitmap->getWidth()I")) {
            return lastPreview == null ? cameraSize[0] : lastPreview.getWidth();
        }
        if (signature.equals("android/graphics/Bitmap->getHeight()I")) {
            return lastPreview == null ? cameraSize[1] : lastPreview.getHeight();
        }
        return null;
    }

    @Override
    public boolean callBooleanMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        Boolean v = handleBoolCall(signature);
        if (v != null) {
            return v;
        }
        return super.callBooleanMethod(vm, dvmObject, signature, varArg);
    }

    @Override
    public boolean callBooleanMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        Boolean v = handleBoolCall(signature);
        if (v != null) {
            return v;
        }
        return super.callBooleanMethodV(vm, dvmObject, signature, vaList);
    }

    private Boolean handleBoolCall(String signature) {
        if (signature.equals("android/view/TextureView->isAvailable()Z")
                || signature.equals("android/view/View->isShown()Z")) {
            return Boolean.TRUE;
        }
        return null;
    }

    private static int intArg(Object args, int index) {
        if (args instanceof VaList) {
            return ((VaList) args).getIntArg(index);
        }
        if (args instanceof VarArg) {
            return ((VarArg) args).getIntArg(index);
        }
        return 0;
    }

    /**
     * If SO wrote Build.MODEL into device/product, rewrite to profile Build.DEVICE/PRODUCT.
     * Values come from the fingerprint JSON, never from literals in this class.
     */
    private static String alignDeviceData(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        String model = profileBuild("MODEL");
        String device = profileBuild("DEVICE");
        String product = profileBuild("PRODUCT");
        String out = body;
        if (model != null && !model.isEmpty() && device != null && !device.isEmpty()
                && !model.equals(device)) {
            out = out.replace("\\\"build_device\\\":\\\"" + model + "\\\"",
                    "\\\"build_device\\\":\\\"" + device + "\\\"");
            out = out.replace("\"build_device\":\"" + model + "\"",
                    "\"build_device\":\"" + device + "\"");
        }
        if (model != null && !model.isEmpty() && product != null && !product.isEmpty()
                && !model.equals(product)) {
            out = out.replace("\\\"build_product\\\":\\\"" + model + "\\\"",
                    "\\\"build_product\\\":\\\"" + product + "\\\"");
            out = out.replace("\"build_product\":\"" + model + "\"",
                    "\"build_product\":\"" + product + "\"");
        }
        return out;
    }

    private static JSONObject loadProfileJson(File file) {
        if (file == null || !file.isFile()) {
            return new JSONObject();
        }
        try {
            byte[] raw = Files.readAllBytes(file.toPath());
            JSONObject obj = JSON.parseObject(new String(raw, StandardCharsets.UTF_8));
            return obj == null ? new JSONObject() : obj;
        } catch (IOException e) {
            throw new IllegalStateException("load profile json failed: " + file, e);
        }
    }

    private static String profileBuild(String key) {
        JSONObject android = PROFILE_JSON.getJSONObject("android");
        if (android == null) {
            return null;
        }
        JSONObject build = android.getJSONObject("build");
        if (build == null) {
            return null;
        }
        return build.getString(key);
    }

    private static Object unwrap(Object v) {
        if (v instanceof DvmObject) {
            return unwrap(((DvmObject<?>) v).getValue());
        }
        return v;
    }
}

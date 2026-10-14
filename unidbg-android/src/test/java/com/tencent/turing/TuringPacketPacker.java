package com.tencent.turing;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

/**
 * Java-side WUP envelope used by turingcam {@code Stvdh} after native mode-0 output.
 * Produces the exact bytes {@code KKOXW} POSTs to {@code /api/turing_new}.
 */
public final class TuringPacketPacker {

    /** Stvdh / Iioec — turing_new HTTP envelope. */
    static final String RSA_PUB_TURING_B64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtAg68zSoKYYQn13AFxhZO0a010h83/sa/"
                    + "u8r7do4u8E4FtBZq1HL5sQK6tM5eO6x/wjMDLSQWy1tZmfnmGUVLBBH9Ofsq387Nm2sL3IusyH47pO2o6AEsXjJ"
                    + "NVkq0jXwuLTBFe117btxtXdZosOt9Tlimknwbk6T1g6qCqlguIGMf6/iAS020FPcSneCP2uCeAi60+Yykld+wU+"
                    + "dK4uaPRUQWWUfOnI/Nh5D5afrjKg26xDNVkWPWqTnzcxHXvlHeI78vt4z4NUdIS+DzJKPSeXg+/vjozg9i48r/V"
                    + "/GERAzEWq65+o3R/50690/9+49s4Yb5e4zgLdS1gMY4y4epQIDAQAB";
    /**
     * Nb4Ck.a — APK {@code IyjbE.a(ts, m209302, 1, null)} for start.frontData second
     * / initDeviceRiskData. Not the turing_new key.
     */
    static final String RSA_PUB_FRONT_B64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtZX4OooO48FiYa3fwzU+fhPHO3YsMBP1MiV1Kp+"
                    + "osNDDPtf4EPCjuzI75ea+8Dbq8uQeCXWszSpWUWH1c2F0khl5BOCzDLyd166i4Hnh3jWW3PNu9ETh81aZh9c353"
                    + "U+c2fok/nPcepmGm3jl3GZrcxpN9/2wYcCl+t0TWRz+PuTNn0X8/Uf4inmxUJDWclZhao8oYKZM09rH6LRYZisz"
                    + "AV9HpO3b9OFd0+/BARTqd+qUbfObMh3jGvIQUjsbpIT+ttPlqS4RyiVPUVG1gyYuS+/XNXbB59MkMlorIzj7esv"
                    + "C96F5OY5nKl7hvruFfHvEpbo4OL4jmveAMUgu4cSJQIDAQAB";

    static final String TURING_NEW_URL = "https://sdk.faceid.qq.com/api/turing_new";
    static final int SDK_VER = 209302;

    private final SecureRandom random = new SecureRandom();
    /** Official Stvdh starts at 1 and increments before first use, so first packet is 2. */
    private int seq = 1;
    private final String guid;
    private final String teaKey;
    private final byte[] rsaKeyBlob;
    private String serverSession = "";

    public TuringPacketPacker() {
        this.guid = newGuid();
        this.teaKey = randomAlphaNum(16);
        this.rsaKeyBlob = buildRsaKeyBlob(rsaEncryptTuring(teaKey.getBytes(StandardCharsets.UTF_8)));
    }

    /** Re-bind a saved m209302 JCE with a newly issued faceidToken. */
    public static void main(String[] args) throws Exception {
        File dir = new File("D:\\project\\12306\\12306-5.9.6.6\\scripts\\ida_out");
        String token = args.length > 0 ? args[0] : System.getenv("HUIYAN_FACEID_TOKEN");
        if (token == null) {
            token = "";
        }
        byte[] front = java.nio.file.Files.readAllBytes(new File(dir, "unidbg_m209302.bin").toPath());
        byte[] tdidJce = java.nio.file.Files.readAllBytes(new File(dir, "unidbg_8117_plain.bin").toPath());
        String tdid = new String(java.nio.file.Files.readAllBytes(new File(dir, "unidbg_tdid.txt").toPath()),
                StandardCharsets.UTF_8).trim();
        byte[] patched = patchFrontWithTdid(front, tdidJce, tdid, token);
        java.nio.file.Files.write(new File(dir, "unidbg_m209302_patched.bin").toPath(), patched);
        String enc = encryptFieldSecond(patched);
        java.nio.file.Files.write(new File(dir, "unidbg_8123_front.txt").toPath(),
                enc.getBytes(StandardCharsets.US_ASCII));
        java.nio.file.Files.write(new File(dir, "unidbg_8123_risk.txt").toPath(),
                enc.getBytes(StandardCharsets.US_ASCII));
        java.nio.file.Files.write(new File(dir, "unidbg_faceid_token.txt").toPath(),
                token.getBytes(StandardCharsets.UTF_8));
        System.out.println("repatch " + frontBindPreview(patched)
                + " frontSecond=" + enc.length()
                + " token=" + token);
    }

    public String getTeaKey() {
        return teaKey;
    }

    public String getGuid() {
        return guid;
    }

    public String getServerSession() {
        return serverSession;
    }

    public void setServerSession(String session) {
        this.serverSession = session == null ? "" : session;
    }

    public byte[] wrapTuringNew(int cmd, byte[] nativePlain) {
        return wrapTuringNew(cmd, nativePlain, serverSession);
    }

    public byte[] wrapTuringNew(int cmd, byte[] nativePlain, String session) {
        seq++;
        byte[] inner = encodeYpipc(cmd, seq, nativePlain, guid, session);
        boolean compressed = false;
        if (inner.length > 50) {
            byte[] zipped = zlib(inner);
            if (zipped != null && zipped.length < inner.length) {
                inner = zipped;
                compressed = true;
            }
        }
        byte[] cipher = xxteaEncrypt(inner, teaKey.getBytes(StandardCharsets.UTF_8));
        if (cipher == null) {
            throw new IllegalStateException("xxtea encrypt failed");
        }
        return encodeOuter(compressed, cipher, session);
    }

    public TuringNewResponse postTuringNew(int cmd, byte[] nativePlain) throws Exception {
        return postWrapped(wrapTuringNew(cmd, nativePlain), cmd);
    }

    public TuringNewResponse postWrapped(byte[] req, int cmd) throws Exception {
        byte[] rsp = httpPost(TURING_NEW_URL, req, "Turing", "application/octet-stream");
        TuringNewResponse parsed = unpackTuringNew(rsp, cmd + 10000);
        if (parsed.session != null && !parsed.session.isEmpty()) {
            serverSession = parsed.session;
        }
        return parsed;
    }

    public static byte[] httpPost(String url, byte[] body, String userAgent, String contentType) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setRequestProperty("Content-Type", contentType);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Pragma", "no-cache");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));
        conn.getOutputStream().write(body);
        conn.getOutputStream().flush();
        int code = conn.getResponseCode();
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            in.close();
        }
        conn.disconnect();
        if (code != 200) {
            throw new IllegalStateException(url + " HTTP " + code + " bytes=" + out.size());
        }
        return out.toByteArray();
    }

    public static String httpPostJson(String url, String json, Map<String, String> extraHeaders) throws Exception {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("User-Agent", "okhttp/3.14.0");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (extraHeaders != null) {
            for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));
        conn.getOutputStream().write(body);
        int code = conn.getResponseCode();
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            in.close();
        }
        conn.disconnect();
        return out.toString("UTF-8");
    }

    /**
     * APK {@code IyjbE.a(now, m209302, 1, null)}: Nb4Ck RSA(tea) + zlib/xxTEA, Base64.
     * HuiYan {@code initDeviceRiskData} / start.frontData second uses this key, not turing_new.
     */
    public static String encryptFieldSecond(byte[] plainJce) {
        String key = randomAlphaNumStatic(16);
        byte[] rsa = rsaEncryptFront(key.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        first.write(1);
        first.write(rsa, 0, rsa.length);
        byte[] marked = new byte[plainJce.length + 1];
        marked[0] = 2;
        System.arraycopy(plainJce, 0, marked, 1, plainJce.length);
        byte[] tea = xxteaEncrypt(zlib(marked), key.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        try {
            all.write(first.toByteArray());
            all.write(tea);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        String b64 = Base64.getEncoder().encodeToString(all.toByteArray());
        System.out.println("IyjbE front RSA=Nb4Ck teaKeyChars=16 rsa=" + rsa.length
                + " cipher=" + tea.length + " b64=" + b64.length());
        return b64;
    }

    private byte[] encodeYpipc(int cmd, int requestSeq, byte[] body, String clientGuid, String session) {
        JceWriter w = new JceWriter();
        if (requestSeq != 0) {
            w.writeInt(requestSeq, 0);
        }
        w.startStruct(2);
        w.writeInt(2, 0);
        w.writeInt(0, 1);
        w.writeString(clientGuid, 2);
        w.writeString("", 3);
        if (session != null && !session.isEmpty()) {
            w.writeString(session, 4);
        }
        w.writeInt(SDK_VER, 5);
        w.writeString("", 9);
        w.endStruct();
        w.startList(3, 1);
        w.startStruct(0);
        w.writeInt(cmd, 0);
        if (requestSeq != 0) {
            w.writeInt(requestSeq, 1);
        }
        w.writeBytes(3, body);
        w.endStruct();
        return w.toByteArray();
    }

    private byte[] encodeOuter(boolean compressed, byte[] cipher, String session) {
        JceWriter w = new JceWriter();
        w.startStruct(0);
        if (session != null && !session.isEmpty()) {
            w.writeString(session, 1);
        }
        if (!compressed) {
            w.writeInt(1, 2);
        }
        w.writeString(guid, 3);
        w.writeInt(1, 4);
        if (seq != 0) {
            w.writeInt(seq, 5);
        }
        w.endStruct();
        w.writeBytes(1, rsaKeyBlob);
        w.writeBytes(2, cipher);
        return w.toByteArray();
    }

    public static Map<Integer, Object> parseJce(byte[] data) {
        return new JceReader(data).readMap();
    }

    public static byte[] encodeJce(Map<Integer, Object> map) {
        JceWriter w = new JceWriter();
        if (map != null) {
            for (Map.Entry<Integer, Object> e : map.entrySet()) {
                w.writeAny(e.getValue(), e.getKey());
            }
        }
        return w.toByteArray();
    }

    /**
     * Offline helper only. APK G1g37 does not rewrite m209302 JCE after native
     * returns; Java collectors go into cfg before the JNI call.
     */
    @SuppressWarnings("unchecked")
    public static byte[] fillMissingFrontKeys(byte[] frontJce, byte[] tdidJce,
                                             Map<Integer, String> extra) {
        if (frontJce == null || frontJce.length == 0) {
            return frontJce;
        }
        Map<Integer, Object> front = parseJce(frontJce);
        Map<Integer, Object> inner = asIntMap(front.get(4));
        if (inner == null) {
            return frontJce;
        }
        JceMap8 dfp = toMap8(inner.get(2));
        int before = dfp.size();
        Map<Integer, Object> src = findDfpMap(tdidJce == null ? null : parseJce(tdidJce));
        int copied = 0;
        if (src != null) {
            for (Map.Entry<Integer, Object> e : src.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                Object existing = dfp.get(e.getKey());
                if (existing != null && !String.valueOf(existing).isEmpty()) {
                    continue;
                }
                Object v = e.getValue();
                if (v == null || String.valueOf(v).isEmpty()) {
                    continue;
                }
                dfp.put(e.getKey(), v);
                copied++;
            }
        }
        int injected = 0;
        if (extra != null) {
            for (Map.Entry<Integer, String> e : extra.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                Object existing = dfp.get(e.getKey());
                if (existing != null && !String.valueOf(existing).isEmpty()) {
                    continue;
                }
                String v = e.getValue();
                if (v == null || v.isEmpty()) {
                    continue;
                }
                dfp.put(e.getKey(), v);
                injected++;
            }
        }
        inner.put(2, dfp);

        Map<Integer, Object> hash = asIntMap(inner.get(5));
        if (hash == null) {
            hash = new LinkedHashMap<Integer, Object>();
        }
        hash.put(9, "");
        long ts = toLong(front.get(1));
        if (ts == 0) {
            ts = System.currentTimeMillis();
        }
        Map<Integer, Object> dfpForHash = asIntMap(dfp);
        String mapHash = mapHash(dfpForHash, (int) ts);
        String serial = serialHash(dfpForHash, mapHash, asIntMap(front.get(0)));
        hash.put(10, mapHash);
        hash.put(12, serial);
        inner.put(5, hash);
        front.put(4, inner);
        byte[] out = encodeJce(front);
        System.out.println("fillMissing dfp " + before + "->" + dfp.size()
                + " copied=" + copied + " injected=" + injected
                + " bytes " + frontJce.length + "->" + out.length);
        return out;
    }

    /**
     * Rewrite m209302 JCE to the official Go turingsecond skeleton:
     * ident=0 map, hash=5 map (keys 3|8|9|7|10|12|4), bind=!8.8 map,
     * oaid=6 map, DFP=2 map. Extra wrapper 8 and Fa 4 stay structs.
     *
     * parseJce drops wire types; encodeJce of a plain LinkedHashMap would
     * turn maps into structs. Always re-wrap containers with JceMap8.
     */
    public static byte[] alignFrontToGoSkeleton(byte[] frontJce, byte[] tdidJce) {
        return alignFrontToGoSkeleton(frontJce, tdidJce, null);
    }

    public static byte[] alignFrontToGoSkeleton(byte[] frontJce, byte[] tdidJce, String fallbackOaid) {
        if (frontJce == null || frontJce.length == 0) {
            return frontJce;
        }
        String before = frontTypeSketch(frontJce);
        Map<Integer, Object> front = parseJce(frontJce);
        applyGoSkeleton(front, tdidJce, fallbackOaid);
        byte[] out = encodeJce(front);
        System.out.println("alignGo " + before + " -> " + frontTypeSketch(out)
                + " bytes " + frontJce.length + "->" + out.length);
        return out;
    }

    @SuppressWarnings("unchecked")
    static void applyGoSkeleton(Map<Integer, Object> front, byte[] tdidJce, String fallbackOaid) {
        if (front == null) {
            return;
        }
        if (front.get(0) instanceof Map) {
            front.put(0, toMap8(front.get(0)));
        }
        Map<Integer, Object> inner = asIntMap(front.get(4));
        if (inner == null) {
            inner = new LinkedHashMap<Integer, Object>();
            front.put(4, inner);
        }
        if (inner.get(2) instanceof Map) {
            inner.put(2, toMap8(inner.get(2)));
        }
        inner.put(5, alignGoHash(inner.get(5)));

        Object oaid = tdidOaid(tdidJce == null ? null : parseJce(tdidJce));
        if ((oaid == null || String.valueOf(oaid).isEmpty()) && fallbackOaid != null && !fallbackOaid.isEmpty()) {
            oaid = fallbackOaid;
        }
        if (oaid == null && inner.get(6) instanceof Map) {
            Map<Integer, Object> m6 = asIntMap(inner.get(6));
            Object existing = m6 == null ? null : m6.get(2);
            if (existing != null && !String.valueOf(existing).isEmpty()) {
                oaid = existing;
            }
        }
        if (oaid != null) {
            JceMap8 oaidMap = new JceMap8();
            oaidMap.put(2, oaid);
            inner.put(6, oaidMap);
        } else if (inner.get(6) instanceof Map) {
            inner.put(6, toMap8(inner.get(6)));
        }

        Map<Integer, Object> extra = asIntMap(inner.get(8));
        if (extra == null) {
            extra = new LinkedHashMap<Integer, Object>();
        }
        extra.put(8, toMap8(extra.get(8)));
        inner.put(8, extra);

        Map<Integer, Object> rebuilt = new LinkedHashMap<Integer, Object>();
        for (int tag : new int[] {0, 1, 2, 3, 5, 6, 8}) {
            if (inner.containsKey(tag)) {
                rebuilt.put(tag, inner.get(tag));
            }
        }
        for (Map.Entry<Integer, Object> e : inner.entrySet()) {
            if (!rebuilt.containsKey(e.getKey())) {
                rebuilt.put(e.getKey(), e.getValue());
            }
        }
        front.put(4, rebuilt);
    }

    static JceMap8 alignGoHash(Object raw) {
        Map<Integer, Object> src = asIntMap(raw);
        if (src == null) {
            src = new LinkedHashMap<Integer, Object>();
        }
        if (!src.containsKey(3)) {
            src.put(3, "");
        }
        if (!src.containsKey(8)) {
            src.put(8, "0");
        }
        Object nine = src.get(9);
        if (nine == null) {
            src.put(9, "");
        }
        Object h7 = src.get(7);
        if (h7 instanceof String && !((String) h7).endsWith("init")) {
            src.put(7, h7 + "init");
        }
        JceMap8 out = new JceMap8();
        for (int k : new int[] {3, 8, 9, 7, 10, 12, 4}) {
            if (src.containsKey(k)) {
                out.put(k, src.get(k));
            }
        }
        for (Map.Entry<Integer, Object> e : src.entrySet()) {
            if (!out.containsKey(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /** Optional post-JCE fill. TuringMfaCcTest no longer uses this; native netlink/cfg/files collect instead. */
    public static byte[] overlayDfpFields(byte[] frontJce, Map<String, String> fields) {
        if (frontJce == null || frontJce.length == 0 || fields == null || fields.isEmpty()) {
            return frontJce;
        }
        Map<Integer, Object> front = parseJce(frontJce);
        Map<Integer, Object> inner = asIntMap(front.get(4));
        if (inner == null) {
            return frontJce;
        }
        JceMap8 dfp = toMap8(inner.get(2));
        int added = 0;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            int key;
            try {
                key = Integer.parseInt(e.getKey());
            } catch (Exception ignored) {
                continue;
            }
            Object existing = dfp.get(key);
            if (existing != null && !String.valueOf(existing).isEmpty()) {
                continue;
            }
            dfp.put(key, e.getValue());
            added++;
        }
        inner.put(2, dfp);
        front.put(4, inner);
        byte[] out = encodeJce(front);
        System.out.println("overlayDfp added=" + added + " bytes " + frontJce.length + "->" + out.length);
        return out;
    }

    public static String frontTypeSketch(byte[] data) {
        if (data == null || data.length == 0) {
            return "empty";
        }
        Map<String, String> types = new LinkedHashMap<String, String>();
        new JceReader(data).walkOpen("", types, false);
        StringBuilder hashKeys = new StringBuilder();
        for (String k : types.keySet()) {
            if (k.startsWith("4.5.") && k.lastIndexOf('.') == 3) {
                if (hashKeys.length() > 0) {
                    hashKeys.append('|');
                }
                hashKeys.append(k.substring(4));
            }
        }
        return "ident0=" + types.get("0")
                + " fa4=" + types.get("4")
                + " dfp2=" + types.get("4.2")
                + " hash5=" + types.get("4.5")
                + " hashKeys=" + hashKeys
                + " oaid6=" + types.get("4.6")
                + " extra8=" + types.get("4.8")
                + " bind8=" + types.get("4.8.8");
    }

    /** Overlay 8117 DFP + tdid/token onto m209302 front JCE. */
    @SuppressWarnings("unchecked")
    public static byte[] patchFrontWithTdid(byte[] frontJce, byte[] tdidJce,
                                            String tdid, String faceIdToken) {
        if (frontJce == null || frontJce.length == 0) {
            return frontJce;
        }
        Map<Integer, Object> front = parseJce(frontJce);
        Map<Integer, Object> inner = asIntMap(front.get(4));
        if (inner == null) {
            inner = new LinkedHashMap<Integer, Object>();
            front.put(4, inner);
        }
        Map<Integer, Object> tdidRoot = null;
        Map<Integer, Object> dfp = null;
        if (tdidJce != null && tdidJce.length > 0) {
            tdidRoot = parseJce(tdidJce);
            System.out.println("patch 8117 keys=" + tdidRoot.keySet()
                    + " t1=" + typeName(tdidRoot.get(1)));
            dfp = findDfpMap(tdidRoot);
        }
        if (dfp == null || dfp.size() < 8) {
            Map<Integer, Object> local = asIntMap(inner.get(2));
            System.out.println("patch overlay miss, use front dfp n="
                    + (local == null ? -1 : local.size()));
            if (local != null) {
                dfp = local;
            }
        }
        if (dfp == null) {
            dfp = new LinkedHashMap<Integer, Object>();
        }
        JceMap8 dfp8 = toMap8(dfp);
        if (tdid != null && !tdid.isEmpty()) {
            dfp8.put(28, tdid);
        }
        inner.put(2, dfp8);
        System.out.println("patch dfp n=" + dfp8.size()
                + " #28.chars=" + strLen(dfp8.get(28))
                + " tdid.chars=" + (tdid == null ? 0 : tdid.length()));

        Map<Integer, Object> extra = asIntMap(inner.get(8));
        if (extra == null) {
            extra = new LinkedHashMap<Integer, Object>();
        }
        JceMap8 bind = toMap8(extra.get(8));
        if (faceIdToken != null && !faceIdToken.isEmpty()) {
            bind.put(100, faceIdToken);
        }
        if (tdid != null && !tdid.isEmpty()) {
            bind.put(101, tdid);
        }
        extra.put(8, bind);
        inner.put(8, extra);
        front.put(4, inner);

        Map<Integer, Object> hash = asIntMap(inner.get(5));
        if (hash == null) {
            hash = new LinkedHashMap<Integer, Object>();
        }
        // Official Go clears this; a leftover value forces mid-risk.
        hash.put(9, "");
        long ts = toLong(front.get(1));
        if (ts == 0) {
            ts = System.currentTimeMillis();
        }
        Map<Integer, Object> dfpForHash = asIntMap(dfp8);
        String mapHash = mapHash(dfpForHash, (int) ts);
        String serial = serialHash(dfpForHash, mapHash, asIntMap(front.get(0)));
        hash.put(10, mapHash);
        hash.put(12, serial);
        inner.put(5, hash);

        applyGoSkeleton(front, tdidJce, null);
        byte[] out = encodeJce(front);
        System.out.println("patch encode " + frontJce.length + " -> " + out.length
                + " extraKeys=" + extra.keySet()
                + " bindKeys=" + bind.keySet());
        return out;
    }

    static Map<Integer, Object> findDfpMap(Map<Integer, Object> root) {
        if (root == null) {
            return null;
        }
        Map<Integer, Object> direct = asIntMap(root.get(1));
        if (isDfpMap(direct)) {
            return direct;
        }
        Map<Integer, Object> best = null;
        int bestScore = 0;
        for (Object v : root.values()) {
            Map<Integer, Object> cand = asIntMap(v);
            int score = dfpScore(cand);
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return bestScore >= 3 ? best : direct;
    }

    static boolean isDfpMap(Map<Integer, Object> map) {
        return dfpScore(map) >= 3;
    }

    static int dfpScore(Map<Integer, Object> map) {
        if (map == null) {
            return 0;
        }
        int score = 0;
        for (int key : new int[] {8, 16, 24, 26, 28, 18, 10}) {
            if (map.containsKey(key)) {
                score++;
            }
        }
        if (map.size() >= 16) {
            score++;
        }
        return score;
    }

    static String typeName(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof JceMap8) {
            return "jceMap8#" + ((Map<?, ?>) v).size();
        }
        if (v instanceof Map) {
            return "structMap#" + ((Map<?, ?>) v).size();
        }
        if (v instanceof byte[]) {
            return "bytes#" + ((byte[]) v).length;
        }
        return v.getClass().getSimpleName();
    }

    static int strLen(Object v) {
        return v == null ? 0 : String.valueOf(v).length();
    }

    static final class JceMap8 extends LinkedHashMap<Object, Object> {
    }

    static JceMap8 toMap8(Object raw) {
        JceMap8 out = new JceMap8();
        if (!(raw instanceof Map)) {
            return out;
        }
        for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
            Object k = e.getKey();
            if (k instanceof Number) {
                out.put(((Number) k).intValue(), e.getValue());
            } else if (k instanceof String) {
                try {
                    out.put(Integer.parseInt((String) k), e.getValue());
                } catch (Exception ignored) {
                    out.put(k, e.getValue());
                }
            } else if (k != null) {
                out.put(k, e.getValue());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static Object tdidOaid(Map<Integer, Object> tdidRoot) {
        if (tdidRoot == null) {
            return null;
        }
        for (int tag : new int[] {7, 6}) {
            Object node = tdidRoot.get(tag);
            if (node instanceof Map) {
                Object oaid = ((Map<?, ?>) node).get(2);
                if (oaid instanceof String && !((String) oaid).isEmpty()) {
                    return oaid;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<Integer, Object> frontInner(byte[] frontJce) {
        if (frontJce == null || frontJce.length == 0) {
            return null;
        }
        return asIntMap(parseJce(frontJce).get(4));
    }

    static String frontBindPreview(byte[] frontJce) {
        Map<Integer, Object> inner = frontInner(frontJce);
        if (inner == null) {
            return "no-inner";
        }
        Map<Integer, Object> dfp = asIntMap(inner.get(2));
        Map<Integer, Object> extra = asIntMap(inner.get(8));
        Map<Integer, Object> bind = extra == null ? null : asIntMap(extra.get(8));
        Map<Integer, Object> hash = asIntMap(inner.get(5));
        Object f28 = dfp == null ? null : dfp.get(28);
        Object f100 = bind == null ? null : bind.get(100);
        Object f101 = bind == null ? null : bind.get(101);
        Object f102 = bind == null ? null : bind.get(102);
        Object h10 = hash == null ? null : hash.get(10);
        Object h12 = hash == null ? null : hash.get(12);
        return "#28=" + previewStr(f28, 24)
                + " #100=" + previewStr(f100, 36)
                + " #101=" + previewStr(f101, 24)
                + " #102=" + previewStr(f102, 48)
                + " hash10=" + previewStr(h10, 20)
                + " hash12=" + previewStr(h12, 20)
                + " dfpKeys=" + (dfp == null ? 0 : dfp.size());
    }

    private static String previewStr(Object v, int max) {
        if (v == null) {
            return "null";
        }
        String s = String.valueOf(v);
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...(len=" + s.length() + ")";
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Object> asIntMap(Object raw) {
        if (!(raw instanceof Map)) {
            return null;
        }
        Map<?, ?> in = (Map<?, ?>) raw;
        Map<Integer, Object> out = new LinkedHashMap<Integer, Object>();
        for (Map.Entry<?, ?> e : in.entrySet()) {
            Object k = e.getKey();
            int key;
            if (k instanceof Number) {
                key = ((Number) k).intValue();
            } else if (k instanceof String) {
                try {
                    key = Integer.parseInt((String) k);
                } catch (Exception ignored) {
                    continue;
                }
            } else {
                continue;
            }
            out.put(key, e.getValue());
        }
        return out;
    }

    private static long toLong(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        if (v instanceof String) {
            try {
                return Long.parseLong((String) v);
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    static String mapHash(Map<Integer, Object> dfp, int timeMs) {
        List<Integer> keys = new ArrayList<Integer>();
        for (Integer k : dfp.keySet()) {
            if (dfp.get(k) instanceof String) {
                keys.add(k);
            }
        }
        Collections.sort(keys);
        StringBuilder msg = new StringBuilder();
        for (Integer k : keys) {
            msg.append(String.valueOf(dfp.get(k)));
        }
        int n = (keys.size() >> 2) + 1;
        int t = timeMs;
        for (int i = 0; i < n; i++) {
            t *= 0x000343FD;
            t += 0x00269EC3;
            t >>= 16;
            t &= 0x7FFF;
            msg.append(Integer.toHexString(t));
        }
        return String.format("01%016x", murmur64(msg.toString().getBytes(StandardCharsets.UTF_8), 0));
    }

    static String serialHash(Map<Integer, Object> dfp, String sortHash, Map<Integer, Object> ident) {
        StringBuilder msg = new StringBuilder();
        for (Object v : dfp.values()) {
            if (v instanceof String) {
                msg.append((String) v);
            }
        }
        String version = identStr(ident, 0, "209302");
        String lc = identStr(ident, 3, "97A941171A843DDA");
        String channel = identStr(ident, 2, "108098");
        String pkgVer = identStr(ident, 4, "5.9.6.6,278");
        String pkg = identStr(ident, 1, "com.MobileTicket");
        // m209302 ident tags: 1=brand 2=model 3=channel 4=pkg — version/lc live in [4][1]
        if (ident != null && ident.get(1) instanceof String && ((String) ident.get(1)).length() < 12) {
            version = "209302";
            lc = "97A941171A843DDA";
            channel = identStr(ident, 3, "108098");
            pkg = identStr(ident, 4, "com.MobileTicket");
            pkgVer = "5.9.6.6,278";
        }
        String all = version + lc + channel + msg + sortHash + pkgVer + pkg;
        return String.format("%016x", murmur64(all.getBytes(StandardCharsets.UTF_8), 0));
    }

    private static String identStr(Map<Integer, Object> ident, int key, String fallback) {
        if (ident == null) {
            return fallback;
        }
        Object v = ident.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    static long murmur64(byte[] data, int seed) {
        final long m = 0xc6a4a7935bd1e995L;
        final int r = 47;
        int len = data.length;
        long h = (seed & 0xffffffffL) ^ (len * m);
        int nblocks = len / 8;
        for (int i = 0; i < nblocks; i++) {
            int o = i * 8;
            long k = (data[o] & 0xffL)
                    | ((data[o + 1] & 0xffL) << 8)
                    | ((data[o + 2] & 0xffL) << 16)
                    | ((data[o + 3] & 0xffL) << 24)
                    | ((data[o + 4] & 0xffL) << 32)
                    | ((data[o + 5] & 0xffL) << 40)
                    | ((data[o + 6] & 0xffL) << 48)
                    | ((data[o + 7] & 0xffL) << 56);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h ^= k;
            h *= m;
        }
        int off = nblocks * 8;
        switch (len & 7) {
            case 7:
                h ^= (data[off + 6] & 0xffL) << 48;
            case 6:
                h ^= (data[off + 5] & 0xffL) << 40;
            case 5:
                h ^= (data[off + 4] & 0xffL) << 32;
            case 4:
                h ^= (data[off + 3] & 0xffL) << 24;
            case 3:
                h ^= (data[off + 2] & 0xffL) << 16;
            case 2:
                h ^= (data[off + 1] & 0xffL) << 8;
            case 1:
                h ^= (data[off] & 0xffL);
                h *= m;
        }
        h ^= h >>> r;
        h *= m;
        h ^= h >>> r;
        return h;
    }

    public TuringNewResponse unpackTuringNew(byte[] raw, int expectCmd) {
        TuringNewResponse out = new TuringNewResponse();
        out.raw = raw;
        if (raw == null || raw.length == 0) {
            out.error = "empty response";
            return out;
        }
        JceReader reader = new JceReader(raw);
        Map<Integer, Object> outer = reader.readMap();
        out.headerFlags = asInt(nested(outer, 0, 0));
        out.headerError = asInt(nested(outer, 0, 1));
        Object sessionBlob = outer.get(1);
        if (sessionBlob instanceof byte[]) {
            Map<Integer, Object> sess = new JceReader((byte[]) sessionBlob).readMap();
            Object sid = sess.get(0);
            if (sid instanceof String) {
                out.session = (String) sid;
            }
            out.expire = asInt(sess.get(1));
        }
        Object cipherObj = outer.get(2);
        if (!(cipherObj instanceof byte[])) {
            out.error = "missing ^2 cipher, headerError=" + out.headerError;
            return out;
        }
        byte[] inner = (byte[]) cipherObj;
        int flags = out.headerFlags;
        if ((flags & 2) == 0) {
            inner = xxteaDecrypt(inner, teaKey.getBytes(StandardCharsets.UTF_8));
        }
        if (inner != null && (flags & 1) == 0) {
            byte[] inflated = inflate(inner);
            if (inflated != null && inflated.length > 0) {
                inner = inflated;
            }
        }
        if (inner == null || inner.length == 0) {
            out.error = "decrypt/inflate failed flags=" + flags;
            return out;
        }
        out.innerPlain = inner;
        Map<Integer, Object> ypipc = new JceReader(inner).readMap();
        Object listObj = ypipc.get(2);
        if (listObj instanceof List) {
            for (Object item : (List<?>) listObj) {
                if (!(item instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<Integer, Object> rec = (Map<Integer, Object>) item;
                int recCmd = asInt(rec.get(0));
                if (expectCmd != 0 && recCmd != 0 && recCmd != expectCmd) {
                    continue;
                }
                out.recordCmd = recCmd;
                out.recordErr = asInt(rec.get(3));
                Object body = rec.get(5);
                if (body instanceof byte[]) {
                    out.recordBody = (byte[]) body;
                }
                break;
            }
        }
        if (out.recordBody != null) {
            Map<Integer, Object> rec = new JceReader(out.recordBody).readMap();
            out.recordJson = rec;
            Object v0 = rec.get(0);
            Object v1 = rec.get(1);
            if (v1 instanceof String) {
                out.payloadString = (String) v1;
            } else if (v0 instanceof String) {
                out.payloadString = (String) v0;
            }
            out.strings = collectStrings(rec);
        }
        if (out.headerError != 0) {
            out.error = "headerError=" + out.headerError;
        }
        return out;
    }

    static int asInt(Object v) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    static Object nested(Map<Integer, Object> map, int a, int b) {
        Object first = map.get(a);
        if (first instanceof Map) {
            return ((Map<Integer, Object>) first).get(b);
        }
        return null;
    }

    static List<String> collectStrings(Object node) {
        List<String> out = new ArrayList<String>();
        collectStrings(node, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    static void collectStrings(Object node, List<String> out) {
        if (node instanceof String) {
            String s = (String) node;
            if (!s.isEmpty()) {
                out.add(s);
            }
        } else if (node instanceof Map) {
            for (Object v : ((Map<?, ?>) node).values()) {
                collectStrings(v, out);
            }
        } else if (node instanceof List) {
            for (Object v : (List<?>) node) {
                collectStrings(v, out);
            }
        }
    }

    static byte[] inflate(byte[] data) {
        try {
            Inflater inflater = new Inflater(false);
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0) {
                    break;
                }
                out.write(buf, 0, n);
            }
            inflater.end();
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] xxteaDecrypt(byte[] data, byte[] keyBytes) {
        if (keyBytes != null && keyBytes.length > 16) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                keyBytes = md.digest(keyBytes);
            } catch (Exception e) {
                return null;
            }
        }
        if (data == null || keyBytes == null || data.length < 8 || (data.length & 3) != 0) {
            return null;
        }
        int length = data.length >>> 2;
        int[] v = new int[length];
        bytesToInts(data, v);
        int keyWords = keyBytes.length % 4 == 0 ? keyBytes.length >>> 2 : (keyBytes.length >>> 2) + 1;
        int kLen = Math.max(keyWords, 4);
        int[] k = new int[kLen];
        bytesToInts(keyBytes, k);
        int n = length - 1;
        int q = 52 / length + 6;
        int sum = q * -1640531527;
        int y = v[0];
        while (q-- > 0) {
            int e = (sum >>> 2) & 3;
            int p;
            for (p = n; p > 0; p--) {
                int z = v[p - 1];
                y = v[p] -= mx(z, y, sum, p, e, k);
            }
            int z = v[n];
            y = v[0] -= mx(z, y, sum, p, e, k);
            sum += 1640531527;
        }
        int orig = v[n];
        if (orig < 0 || orig > n * 4) {
            return null;
        }
        byte[] raw = new byte[length << 2];
        intsToBytes(v, length, raw);
        byte[] out = new byte[orig];
        System.arraycopy(raw, 0, out, 0, orig);
        return out;
    }

    public static final class TuringNewResponse {
        public byte[] raw;
        public byte[] innerPlain;
        public byte[] recordBody;
        public Map<Integer, Object> recordJson;
        public List<String> strings = new ArrayList<String>();
        public String session = "";
        public String payloadString = "";
        public String error = "";
        public int expire;
        public int headerFlags;
        public int headerError;
        public int recordCmd;
        public int recordErr;

        public String firstLongString(int minLen) {
            for (String s : strings) {
                if (s != null && s.length() >= minLen) {
                    return s;
                }
            }
            return payloadString;
        }

        @Override
        public String toString() {
            return "TuringNewResponse{err=" + error
                    + " headerErr=" + headerError
                    + " flags=" + headerFlags
                    + " recCmd=" + recordCmd
                    + " recErr=" + recordErr
                    + " sessionChars=" + (session == null ? 0 : session.length())
                    + " expire=" + expire
                    + " body=" + (recordBody == null ? 0 : recordBody.length)
                    + " payload=" + preview(payloadString)
                    + " strings=" + strings.size()
                    + "}";
        }

        private static String preview(String s) {
            if (s == null) {
                return "";
            }
            return s.length() <= 80 ? s : s.substring(0, 80) + "...";
        }
    }

    static final class JceReader {
        private final byte[] data;
        private int pos;

        JceReader(byte[] data) {
            this.data = data == null ? new byte[0] : data;
        }

        Map<Integer, Object> readMap() {
            Map<Integer, Object> map = new LinkedHashMap<Integer, Object>();
            while (pos < data.length) {
                int saved = pos;
                Head h = readHead();
                if (h == null) {
                    break;
                }
                if (h.type == 11) {
                    break;
                }
                Object value = readValue(h);
                if (value == SKIP) {
                    pos = saved;
                    break;
                }
                map.put(h.tag, value);
            }
            return map;
        }

        void walkOpen(String path, Map<String, String> out, boolean untilEnd) {
            while (pos < data.length) {
                int saved = pos;
                Head h = readHead();
                if (h == null) {
                    break;
                }
                if (h.type == 11) {
                    if (!untilEnd) {
                        pos = saved;
                    }
                    return;
                }
                String p = path.isEmpty() ? String.valueOf(h.tag) : path + "." + h.tag;
                consumeSketch(p, h, out);
            }
        }

        private void consumeSketch(String path, Head h, Map<String, String> out) {
            switch (h.type) {
                case 8: {
                    Head sizeHead = readHead();
                    int n = ((Number) readValue(sizeHead)).intValue();
                    out.put(path, "map#" + n);
                    for (int i = 0; i < n; i++) {
                        Head kh = readHead();
                        Object k = readValue(kh);
                        Head vh = readHead();
                        consumeSketch(path + "." + k, vh, out);
                    }
                    return;
                }
                case 10:
                    out.put(path, "struct");
                    walkOpen(path, out, true);
                    return;
                case 9: {
                    Head sizeHead = readHead();
                    int n = ((Number) readValue(sizeHead)).intValue();
                    out.put(path, "list#" + n);
                    for (int i = 0; i < n; i++) {
                        Head eh = readHead();
                        consumeSketch(path + "[]", eh, out);
                    }
                    return;
                }
                default:
                    out.put(path, sketchScalar(h.type));
                    readValue(h);
            }
        }

        private static String sketchScalar(int type) {
            switch (type) {
                case 6:
                case 7:
                    return "str";
                case 0:
                case 1:
                case 2:
                case 3:
                case 12:
                    return "int";
                case 13:
                    return "bytes";
                case 4:
                    return "float";
                case 5:
                    return "double";
                default:
                    return "t" + type;
            }
        }

        private static final Object SKIP = new Object();

        private Head readHead() {
            if (pos >= data.length) {
                return null;
            }
            int b = data[pos++] & 0xff;
            int type = b & 0x0f;
            int tag = (b & 0xf0) >> 4;
            if (tag == 15) {
                if (pos >= data.length) {
                    return null;
                }
                tag = data[pos++] & 0xff;
            }
            return new Head(type, tag);
        }

        private Object readValue(Head h) {
            switch (h.type) {
                case 0:
                    return data[pos++] & 0xff;
                case 1:
                    int s = ((data[pos] & 0xff) << 8) | (data[pos + 1] & 0xff);
                    pos += 2;
                    return (int) (short) s;
                case 2:
                    int i = ((data[pos] & 0xff) << 24)
                            | ((data[pos + 1] & 0xff) << 16)
                            | ((data[pos + 2] & 0xff) << 8)
                            | (data[pos + 3] & 0xff);
                    pos += 4;
                    return i;
                case 3:
                    long l = 0;
                    for (int n = 0; n < 8; n++) {
                        l = (l << 8) | (data[pos++] & 0xff);
                    }
                    return l;
                case 6: {
                    int n = data[pos++] & 0xff;
                    String str = new String(data, pos, n, StandardCharsets.UTF_8);
                    pos += n;
                    return str;
                }
                case 7: {
                    int n = ((data[pos] & 0xff) << 24)
                            | ((data[pos + 1] & 0xff) << 16)
                            | ((data[pos + 2] & 0xff) << 8)
                            | (data[pos + 3] & 0xff);
                    pos += 4;
                    String str = new String(data, pos, n, StandardCharsets.UTF_8);
                    pos += n;
                    return str;
                }
                case 8: {
                    Head sizeHead = readHead();
                    int size = ((Number) readValue(sizeHead)).intValue();
                    Map<Object, Object> map = new LinkedHashMap<Object, Object>();
                    for (int n = 0; n < size; n++) {
                        Head kh = readHead();
                        Object k = readValue(kh);
                        Head vh = readHead();
                        Object v = readValue(vh);
                        map.put(k, v);
                    }
                    return map;
                }
                case 9: {
                    Head sizeHead = readHead();
                    int size = ((Number) readValue(sizeHead)).intValue();
                    List<Object> list = new ArrayList<Object>(size);
                    for (int n = 0; n < size; n++) {
                        Head eh = readHead();
                        list.add(readValue(eh));
                    }
                    return list;
                }
                case 10:
                    return readMap();
                case 11:
                    return null;
                case 12:
                    return 0;
                case 13: {
                    readHead();
                    Head lenHead = readHead();
                    int n = ((Number) readValue(lenHead)).intValue();
                    byte[] bytes = new byte[n];
                    System.arraycopy(data, pos, bytes, 0, n);
                    pos += n;
                    return bytes;
                }
                default:
                    return SKIP;
            }
        }

        private static final class Head {
            final int type;
            final int tag;

            Head(int type, int tag) {
                this.type = type;
                this.tag = tag;
            }
        }
    }

    private byte[] buildRsaKeyBlob(byte[] rsaKey) {
        JceWriter w = new JceWriter();
        w.writeInt(1, 0);
        w.writeBytes(1, rsaKey);
        w.writeString("EP_TuringMM", 2);
        return w.toByteArray();
    }

    private static byte[] rsaEncryptTuring(byte[] data) {
        return rsaEncrypt(data, RSA_PUB_TURING_B64);
    }

    private static byte[] rsaEncryptFront(byte[] data) {
        return rsaEncrypt(data, RSA_PUB_FRONT_B64);
    }

    private static byte[] rsaEncrypt(byte[] data, String pubB64) {
        try {
            byte[] der = Base64.getDecoder().decode(pubB64);
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("rsa", e);
        }
    }

    private static byte[] zlib(byte[] data) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, false);
            DeflaterOutputStream stream = new DeflaterOutputStream(out, deflater);
            stream.write(data);
            stream.finish();
            stream.close();
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private String newGuid() {
        String date = new SimpleDateFormat("yyMMddHHmmss", Locale.getDefault()).format(new Date());
        StringBuilder sb = new StringBuilder("030292");
        sb.append(date);
        sb.append("00");
        sb.append(String.format(Locale.US, "%010d", Math.abs(random.nextLong()) % 10_000_000_000L));
        sb.append((char) ('0' + random.nextInt(10)));
        String body = sb.toString();
        int odd = 0;
        int even = 0;
        for (int i = body.length() - 1; i >= 0; i -= 2) {
            int d = body.charAt(i) - '0';
            d *= 2;
            if (d > 9) {
                d -= 9;
            }
            odd += d;
        }
        for (int i = body.length() - 2; i >= 0; i -= 2) {
            even += body.charAt(i) - '0';
        }
        int check = (10 - ((odd + even) % 10)) % 10;
        return body + check;
    }

    private String randomAlphaNum(int n) {
        return randomAlphaNumStatic(n);
    }

    private static String randomAlphaNumStatic(int n) {
        SecureRandom r = new SecureRandom();
        char[] chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
        char[] out = new char[n];
        for (int i = 0; i < n; i++) {
            out[i] = chars[r.nextInt(chars.length)];
        }
        return new String(out);
    }

    static byte[] xxteaEncrypt(byte[] data, byte[] keyBytes) {
        if (keyBytes != null && keyBytes.length > 16) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                keyBytes = md.digest(keyBytes);
            } catch (Exception e) {
                return null;
            }
        }
        if (data == null || keyBytes == null || data.length == 0) {
            return null;
        }
        int length = data.length % 4 == 0 ? (data.length >>> 2) + 1 : (data.length >>> 2) + 2;
        int[] v = new int[length];
        bytesToInts(data, v);
        v[length - 1] = data.length;
        int keyWords = keyBytes.length % 4 == 0 ? keyBytes.length >>> 2 : (keyBytes.length >>> 2) + 1;
        int kLen = Math.max(keyWords, 4);
        int[] k = new int[kLen];
        bytesToInts(keyBytes, k);
        int n = length - 1;
        int z = v[n];
        int y = v[0];
        int q = 52 / length + 6;
        int sum = 0;
        while (q-- > 0) {
            sum -= 1640531527;
            int e = (sum >>> 2) & 3;
            int p;
            for (p = 0; p < n; p++) {
                y = v[p + 1];
                z = v[p] += mx(z, y, sum, p, e, k);
            }
            y = v[0];
            z = v[n] += mx(z, y, sum, p, e, k);
        }
        byte[] out = new byte[length << 2];
        intsToBytes(v, length, out);
        return out;
    }

    private static int mx(int z, int y, int sum, int p, int e, int[] k) {
        return (((z >>> 5) ^ (y << 2)) + ((y >>> 3) ^ (z << 4))) ^ ((y ^ sum) + (z ^ k[(p & 3) ^ e]));
    }

    private static void bytesToInts(byte[] src, int[] dest) {
        int n = src.length >> 2;
        int i = 0;
        int j = 0;
        while (i < n && i < dest.length) {
            dest[i] = (src[j] & 0xff)
                    | ((src[j + 1] & 0xff) << 8)
                    | ((src[j + 2] & 0xff) << 16)
                    | ((src[j + 3] & 0xff) << 24);
            i++;
            j += 4;
        }
        if (j < src.length && i < dest.length) {
            int val = src[j] & 0xff;
            int shift = 8;
            j++;
            while (j < src.length) {
                val |= (src[j] & 0xff) << shift;
                j++;
                shift += 8;
            }
            dest[i] = val;
        }
    }

    private static void intsToBytes(int[] src, int count, byte[] dest) {
        int n = Math.min(dest.length >> 2, count);
        int i = 0;
        int j = 0;
        while (i < n) {
            int val = src[i];
            dest[j] = (byte) val;
            dest[j + 1] = (byte) (val >>> 8);
            dest[j + 2] = (byte) (val >>> 16);
            dest[j + 3] = (byte) (val >>> 24);
            i++;
            j += 4;
        }
    }

    static final class JceWriter {
        private ByteBuffer buf = ByteBuffer.allocate(256);

        private void grow(int n) {
            if (buf.remaining() < n) {
                ByteBuffer next = ByteBuffer.allocate((buf.capacity() + n) * 2);
                buf.flip();
                next.put(buf);
                buf = next;
            }
        }

        void head(int type, int tag) {
            grow(2);
            if (tag < 15) {
                buf.put((byte) (type | (tag << 4)));
            } else {
                buf.put((byte) (type | 0xf0));
                buf.put((byte) tag);
            }
        }

        void writeInt(int value, int tag) {
            if (value >= -32768 && value <= 32767) {
                writeShort((short) value, tag);
                return;
            }
            grow(5);
            head(2, tag);
            buf.putInt(value);
        }

        void writeShort(short value, int tag) {
            if (value >= -128 && value <= 127) {
                writeByte((byte) value, tag);
                return;
            }
            grow(3);
            head(1, tag);
            buf.putShort(value);
        }

        void writeByte(byte value, int tag) {
            grow(2);
            if (value == 0) {
                head(12, tag);
            } else {
                head(0, tag);
                buf.put(value);
            }
        }

        void writeString(String value, int tag) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            grow(bytes.length + 6);
            if (bytes.length > 255) {
                head(7, tag);
                buf.putInt(bytes.length);
            } else {
                head(6, tag);
                buf.put((byte) bytes.length);
            }
            buf.put(bytes);
        }

        void writeBytes(int tag, byte[] data) {
            grow(data.length + 8);
            head(13, tag);
            head(0, 0);
            writeInt(data.length, 0);
            buf.put(data);
        }

        void startStruct(int tag) {
            grow(1);
            head(10, tag);
        }

        void endStruct() {
            grow(1);
            head(11, 0);
        }

        void startList(int tag, int size) {
            grow(8);
            head(9, tag);
            writeInt(size, 0);
        }

        void writeLong(long value, int tag) {
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                writeInt((int) value, tag);
                return;
            }
            grow(9);
            head(3, tag);
            buf.putLong(value);
        }

        @SuppressWarnings("unchecked")
        void writeAny(Object value, int tag) {
            if (value == null) {
                writeString("", tag);
                return;
            }
            if (value instanceof String) {
                writeString((String) value, tag);
                return;
            }
            if (value instanceof Integer) {
                writeInt((Integer) value, tag);
                return;
            }
            if (value instanceof Long) {
                writeLong((Long) value, tag);
                return;
            }
            if (value instanceof byte[]) {
                writeBytes(tag, (byte[]) value);
                return;
            }
            if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;
                if (value instanceof JceMap8) {
                    grow(8);
                    head(8, tag);
                    writeInt(map.size(), 0);
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        writeAny(e.getKey(), 0);
                        writeAny(e.getValue(), 1);
                    }
                    return;
                }
                Object firstKey = null;
                for (Object k : map.keySet()) {
                    firstKey = k;
                    break;
                }
                if (firstKey instanceof String) {
                    grow(8);
                    head(8, tag);
                    writeInt(map.size(), 0);
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        writeAny(e.getKey(), 0);
                        writeAny(e.getValue(), 1);
                    }
                    return;
                }
                startStruct(tag);
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    int childTag = e.getKey() instanceof Number
                            ? ((Number) e.getKey()).intValue() : 0;
                    writeAny(e.getValue(), childTag);
                }
                endStruct();
                return;
            }
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                startList(tag, list.size());
                for (Object item : list) {
                    writeAny(item, 0);
                }
                return;
            }
            writeString(String.valueOf(value), tag);
        }

        byte[] toByteArray() {
            byte[] out = new byte[buf.position()];
            System.arraycopy(buf.array(), 0, out, 0, out.length);
            return out;
        }
    }
}

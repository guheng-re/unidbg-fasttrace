package com.github.unidbg.env;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.unidbg.Emulator;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class TraceEnvironmentConfig {

    public static final String KEY = TraceEnvironmentConfig.class.getName();
    public static final String SYSTEM_PROPERTY = "unidbg.env.config";

    private static final String[] RANDOM_HEX_KEYS = {
            "devRandomHex",
            "devUrandomHex",
            "devSrandomHex",
            "getrandomHex",
            "stackGuardHex",
            "atRandomHex",
            "mediaDrmDeviceUniqueIdHex"
    };

    public static TraceEnvironmentConfig get(Emulator<?> emulator) {
        return emulator == null ? null : emulator.get(KEY);
    }

    public static TraceEnvironmentConfig fromSystemProperty() {
        String path = System.getProperty(SYSTEM_PROPERTY);
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        return load(path);
    }

    public static TraceEnvironmentConfig load(String path) {
        return load(new File(path));
    }

    public static TraceEnvironmentConfig load(File file) {
        try {
            return parse(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("load trace environment config failed: " + file, e);
        }
    }

    public static TraceEnvironmentConfig parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("json is empty");
        }
        JSONObject root = JSON.parseObject(json);
        TraceEnvironmentConfig config = new TraceEnvironmentConfig(root);
        config.validate();
        return config;
    }

    private final JSONObject root;

    private TraceEnvironmentConfig(JSONObject root) {
        this.root = root == null ? new JSONObject() : root;
    }

    private void validate() {
        for (String key : RANDOM_HEX_KEYS) {
            String hex = getRandomHex(key);
            if (hex != null) {
                parseHex(key, hex);
            }
        }
        String uuid = getRandomString("uuid");
        if (uuid != null) {
            UUID.fromString(uuid);
        }
    }

    public String getProcessName(String fallback) {
        return getString(section("process"), "processName", fallback);
    }

    public int getPid(int fallback) {
        return getInt(section("process"), "pid", fallback);
    }

    public int getPpid(int fallback) {
        return getInt(section("process"), "ppid", fallback);
    }

    public int getTid(int fallback) {
        return getInt(section("process"), "tid", fallback);
    }

    public int getUid(int fallback) {
        return getInt(section("process"), "uid", fallback);
    }

    public int getGid(int fallback) {
        return getInt(section("process"), "gid", fallback);
    }

    public int getEuid(int fallback) {
        return getInt(section("process"), "euid", fallback);
    }

    public int getEgid(int fallback) {
        return getInt(section("process"), "egid", fallback);
    }

    public String getThreadName(String fallback) {
        return getString(section("process"), "threadName", fallback);
    }

    public long getCurrentTimeMillis(long fallback) {
        return getLong(section("time"), "currentTimeMillis", fallback);
    }

    public Long getCurrentTimeMillis() {
        return getLongObject(section("time"), "currentTimeMillis");
    }

    public Long getMonotonicNanos() {
        return getLongObject(section("time"), "monotonicNanos");
    }

    public long getMonotonicNanos(long fallback) {
        Long value = getMonotonicNanos();
        return value == null ? fallback : value;
    }

    public Integer getTimezoneMinutesWest() {
        return getIntegerObject(section("time"), "timezoneMinutesWest");
    }

    public int getTimezoneMinutesWest(int fallback) {
        Integer value = getTimezoneMinutesWest();
        return value == null ? fallback : value;
    }

    public byte[] getRandomSeed(String key) {
        String hex = getRandomHex(key);
        return hex == null ? null : parseHex(key, hex);
    }

    public byte[] getRandomBytes(String key, int length) {
        byte[] seed = getRandomSeed(key);
        return seed == null ? null : repeat(seed, length);
    }

    public UUID getUuid(UUID fallback) {
        String uuid = getRandomString("uuid");
        return uuid == null ? fallback : UUID.fromString(uuid);
    }

    public String getUnameSysname(String fallback) {
        return getString(uname(), "sysname", fallback);
    }

    public String getUnameNodename(String fallback) {
        return getString(uname(), "nodename", fallback);
    }

    public String getUnameRelease(String fallback) {
        return getString(uname(), "release", fallback);
    }

    public String getUnameVersion(String fallback) {
        return getString(uname(), "version", fallback);
    }

    public String getUnameMachine(boolean is64Bit, String fallback) {
        return getString(uname(), is64Bit ? "machine64" : "machine32", fallback);
    }

    public String getUnameDomainname(String fallback) {
        return getString(uname(), "domainname", fallback);
    }

    public byte[] getLinuxFileBytes(String pathname) {
        JSONObject files = linuxFiles();
        if (files == null) {
            return null;
        }
        Object value = files.get(pathname);
        if (value == null) {
            return null;
        }
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    public String getAndroidPackageName(String fallback) {
        return getString(android(), "packageName", fallback);
    }

    public String getVersionName(String fallback) {
        return getString(android(), "versionName", fallback);
    }

    public long getVersionCode(long fallback) {
        return getLong(android(), "versionCode", fallback);
    }

    public String getApkPath(String fallback) {
        return getString(android(), "apkPath", fallback);
    }

    public String getDataDir(String fallback) {
        return getString(android(), "dataDir", fallback);
    }

    public String getNativeLibraryDir(boolean is64Bit, String fallback) {
        return getString(android(), is64Bit ? "nativeLibraryDir64" : "nativeLibraryDir32", fallback);
    }

    public String getAndroidBuildString(String key) {
        JSONObject build = androidBuild();
        if (build == null) {
            return null;
        }
        Object value = build.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public Integer getAndroidBuildInt(String key) {
        return getIntegerObject(androidBuild(), key);
    }

    public String getAndroidProperty(String key) {
        return getString(androidProperties(), key, null);
    }

    public boolean hasAndroidProperties() {
        JSONObject properties = androidProperties();
        return properties != null && !properties.isEmpty();
    }

    private String getRandomHex(String key) {
        return getString(section("random"), key, null);
    }

    private String getRandomString(String key) {
        return getString(section("random"), key, null);
    }

    private JSONObject section(String key) {
        return root.getJSONObject(key);
    }

    private JSONObject android() {
        return section("android");
    }

    private JSONObject androidBuild() {
        JSONObject android = android();
        return android == null ? null : android.getJSONObject("build");
    }

    private JSONObject androidProperties() {
        JSONObject android = android();
        return android == null ? null : android.getJSONObject("properties");
    }

    private JSONObject uname() {
        JSONObject linux = section("linux");
        return linux == null ? null : linux.getJSONObject("uname");
    }

    private JSONObject linuxFiles() {
        JSONObject linux = section("linux");
        return linux == null ? null : linux.getJSONObject("files");
    }

    private static String getString(JSONObject object, String key, String fallback) {
        if (object == null) {
            return fallback;
        }
        Object value = object.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int getInt(JSONObject object, String key, int fallback) {
        Integer value = getIntegerObject(object, key);
        return value == null ? fallback : value;
    }

    private static Integer getIntegerObject(JSONObject object, String key) {
        if (object == null) {
            return null;
        }
        Object value = object.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Integer.parseInt(text);
    }

    private static long getLong(JSONObject object, String key, long fallback) {
        Long value = getLongObject(object, key);
        return value == null ? fallback : value;
    }

    private static Long getLongObject(JSONObject object, String key) {
        if (object == null) {
            return null;
        }
        Object value = object.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.parseLong(text);
    }

    private static byte[] parseHex(String key, String hex) {
        String normalized = normalizeHex(hex);
        if (normalized.isEmpty() || (normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("invalid hex length: " + key);
        }
        byte[] data = new byte[normalized.length() / 2];
        for (int i = 0; i < data.length; i++) {
            int high = Character.digit(normalized.charAt(i * 2), 16);
            int low = Character.digit(normalized.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("invalid hex value: " + key);
            }
            data[i] = (byte) ((high << 4) | low);
        }
        return data;
    }

    private static String normalizeHex(String hex) {
        StringBuilder builder = new StringBuilder(hex.length());
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (Character.isWhitespace(c) || c == ':') {
                continue;
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private static byte[] repeat(byte[] seed, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length=" + length);
        }
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = seed[i % seed.length];
        }
        return data;
    }
}

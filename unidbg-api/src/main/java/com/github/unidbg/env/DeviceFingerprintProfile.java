package com.github.unidbg.env;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase-1 loader for {@code docs/device-fingerprint-quick-profile.md}.
 * Converts a shortcut profile into the existing {@link TraceEnvironmentConfig} model
 * and attaches a read-only {@code fileOverlayRoot}. Does not reimplement environment backends.
 */
public final class DeviceFingerprintProfile {

    public static final String SCHEMA_VERSION = "traceai-device-fingerprint/v1";
    public static final String SYSTEM_PROPERTY = "unidbg.env.profile";

    private static final Logger log = LoggerFactory.getLogger(DeviceFingerprintProfile.class);

    private final String profileName;
    private final File profileFile;
    private final File overlayRoot;
    private final TraceEnvironmentConfig environmentConfig;
    private final Map<String, Object> reservedFields;
    private final List<String> reservedWarnings;
    private final List<String> consistencyWarnings;

    private DeviceFingerprintProfile(String profileName, File profileFile, File overlayRoot,
                                     TraceEnvironmentConfig environmentConfig,
                                     Map<String, Object> reservedFields,
                                     List<String> reservedWarnings,
                                     List<String> consistencyWarnings) {
        this.profileName = profileName;
        this.profileFile = profileFile;
        this.overlayRoot = overlayRoot;
        this.environmentConfig = environmentConfig;
        this.reservedFields = Collections.unmodifiableMap(reservedFields);
        this.reservedWarnings = Collections.unmodifiableList(reservedWarnings);
        this.consistencyWarnings = Collections.unmodifiableList(consistencyWarnings);
    }

    public static DeviceFingerprintProfile fromSystemProperty() {
        String path = System.getProperty(SYSTEM_PROPERTY);
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        return load(new File(path));
    }

    public static DeviceFingerprintProfile load(String path) {
        return load(new File(path));
    }

    public static DeviceFingerprintProfile load(File file) {
        if (file == null) {
            throw new IllegalArgumentException("profile file is null");
        }
        String json;
        try {
            json = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("load device fingerprint profile failed: " + file, e);
        }
        return parse(json, file);
    }

    public static DeviceFingerprintProfile parse(String json, File profileFile) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("profile json is empty");
        }
        JSONObject root = JSON.parseObject(json, Feature.OrderedField);
        if (root == null) {
            throw new IllegalArgumentException("profile json is empty");
        }
        Object schema = root.get("schemaVersion");
        if (!(schema instanceof String) || !SCHEMA_VERSION.equals(schema)) {
            throw new IllegalArgumentException(
                    "schemaVersion must be \"" + SCHEMA_VERSION + "\"");
        }

        String profileName = null;
        if (root.containsKey("profileName")) {
            Object rawName = root.get("profileName");
            if (!(rawName instanceof String) || ((String) rawName).isEmpty()) {
                throw new IllegalArgumentException("profileName must be a non-empty String when present");
            }
            profileName = (String) rawName;
        }

        File overlayRoot = resolveOverlayRoot(profileFile, root.get("fileOverlayRoot"));
        Map<String, Object> reservedFields = new LinkedHashMap<String, Object>();
        List<String> reserved = new ArrayList<String>();
        collectReserved(root, reservedFields, reserved);

        JSONObject converted = JSON.parseObject(root.toJSONString(), Feature.OrderedField);
        stripProfileOnlyKeys(converted);
        convertTeeSecurityLevel(converted, reserved);

        List<String> consistency = new ArrayList<String>();
        collectConsistencyWarnings(converted, consistency);

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(converted.toJSONString());
        int pid = config.getPid(-1);
        File profileDir = profileFile == null ? null : profileFile.getParentFile();
        ProfileFileOverlay overlay = (overlayRoot == null || profileDir == null)
                ? null : new ProfileFileOverlay(overlayRoot, profileDir, pid);
        config.attachDeviceFingerprintProfile(profileName, overlay, reservedFields);

        if (!reserved.isEmpty()) {
            log.warn("device fingerprint reserved fields kept but inactive: {}", reserved);
        }
        if (!consistency.isEmpty()) {
            log.warn("device fingerprint consistency: {}", consistency);
        }
        return new DeviceFingerprintProfile(profileName, profileFile, overlayRoot, config,
                reservedFields, reserved, consistency);
    }

    public String getProfileName() {
        return profileName;
    }

    public File getProfileFile() {
        return profileFile;
    }

    public File getOverlayRoot() {
        return overlayRoot;
    }

    public TraceEnvironmentConfig getEnvironmentConfig() {
        return environmentConfig;
    }

    /**
     * Original reserved payloads (samples / cellInfo / capabilities / native / backendStatus).
     * Not applied to the current backend; kept so later wiring need not change the profile format.
     */
    public Map<String, Object> getReservedFields() {
        return reservedFields;
    }

    public Object getReservedField(String path) {
        return reservedFields.get(path);
    }

    public List<String> getReservedWarnings() {
        return reservedWarnings;
    }

    public List<String> getConsistencyWarnings() {
        return consistencyWarnings;
    }

    private static File resolveOverlayRoot(File profileFile, Object rawOverlay) {
        String relative = "files";
        if (rawOverlay != null) {
            if (!(rawOverlay instanceof String) || ((String) rawOverlay).isEmpty()) {
                throw new IllegalArgumentException("fileOverlayRoot must be a non-empty relative path");
            }
            relative = (String) rawOverlay;
        }
        List<String> parts = splitOverlayRoot(relative);
        File parent = profileFile == null ? null : profileFile.getParentFile();
        if (parent == null) {
            return null;
        }
        File overlay = parent;
        for (int i = 0; i < parts.size(); i++) {
            overlay = new File(overlay, parts.get(i));
        }
        if (overlay.exists() && !overlay.isDirectory()) {
            throw new IllegalArgumentException("fileOverlayRoot must be a directory: " + overlay);
        }
        if (overlay.exists() && ProfileFileOverlay.resolveContainedDirectory(overlay, parent) == null) {
            throw new IllegalArgumentException("fileOverlayRoot escaped the profile directory");
        }
        return overlay;
    }

    /**
     * Relative POSIX components only. Rejects absolute paths, drive letters, NUL, empty
     * segments, trailing slash, {@code .}/{@code ..}, and whitespace. Does not collapse
     * {@code files/} or {@code files//sub} into a shorter path.
     */
    static List<String> splitOverlayRoot(String relative) {
        if (relative == null || relative.isEmpty()
                || !ProfileFileOverlay.isSafeGuestPathChars(relative)
                || relative.charAt(0) == '/' || relative.charAt(0) == '\\'
                || relative.charAt(relative.length() - 1) == '/') {
            throw new IllegalArgumentException("fileOverlayRoot must stay inside the profile directory");
        }
        String[] raw = relative.split("/", -1);
        List<String> parts = new ArrayList<String>();
        for (int i = 0; i < raw.length; i++) {
            String part = raw[i];
            if (part.isEmpty() || !ProfileFileOverlay.isSafePathComponent(part)
                    || overlayComponentHasWhitespace(part)) {
                throw new IllegalArgumentException("fileOverlayRoot must stay inside the profile directory");
            }
            parts.add(part);
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("fileOverlayRoot must stay inside the profile directory");
        }
        return parts;
    }

    private static boolean overlayComponentHasWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void collectReserved(JSONObject root, Map<String, Object> reservedFields,
                                        List<String> reserved) {
        snapshotReserved(root, "android.telephony.cellInfo", reservedFields, reserved);
        snapshotReserved(root, "graphics.native", reservedFields, reserved);
        if (isLegacyNetworkCapabilities(root)) {
            snapshotReserved(root, "network.capabilities", reservedFields, reserved);
        }
        if (root.containsKey("backendStatus")) {
            reservedFields.put("backendStatus", copyJson(root.get("backendStatus")));
            if (!reserved.contains("backendStatus")) {
                reserved.add("backendStatus");
            }
        }
    }

    private static boolean isLegacyNetworkCapabilities(JSONObject root) {
        if (root == null) {
            return false;
        }
        JSONObject network = root.getJSONObject("network");
        return network != null && isLegacyNetworkCapabilitiesObject(network.get("capabilities"));
    }

    private static boolean isLegacyNetworkCapabilitiesObject(Object raw) {
        if (!(raw instanceof JSONObject)) {
            return false;
        }
        JSONObject caps = (JSONObject) raw;
        return !caps.containsKey("transportTypes") && !caps.containsKey("networkCapabilities");
    }

    private static void snapshotReserved(JSONObject root, String path,
                                         Map<String, Object> reservedFields, List<String> reserved) {
        Object value = lookupPath(root, path);
        if (value == null && !pathExists(root, path)) {
            return;
        }
        reservedFields.put(path, copyJson(value));
        reserved.add(path);
    }

    private static boolean pathExists(JSONObject root, String path) {
        String[] parts = path.split("\\.");
        JSONObject current = root;
        for (int i = 0; i < parts.length; i++) {
            if (current == null || !current.containsKey(parts[i])) {
                return false;
            }
            if (i == parts.length - 1) {
                return true;
            }
            Object next = current.get(parts[i]);
            current = next instanceof JSONObject ? (JSONObject) next : null;
        }
        return false;
    }

    private static Object lookupPath(JSONObject root, String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (int i = 0; i < parts.length; i++) {
            if (!(current instanceof JSONObject)) {
                return null;
            }
            current = ((JSONObject) current).get(parts[i]);
        }
        return current;
    }

    private static Object copyJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return JSON.parse(JSON.toJSONString(value));
        }
        return value;
    }

    private static void stripProfileOnlyKeys(JSONObject converted) {
        converted.remove("schemaVersion");
        converted.remove("profileName");
        converted.remove("fileOverlayRoot");
        converted.remove("backendStatus");
        JSONObject android = converted.getJSONObject("android");
        if (android != null) {
            JSONObject telephony = android.getJSONObject("telephony");
            if (telephony != null) {
                telephony.remove("cellInfo");
            }
        }
        JSONObject network = converted.getJSONObject("network");
        if (network != null && isLegacyNetworkCapabilitiesObject(network.get("capabilities"))) {
            network.remove("capabilities");
        }
        JSONObject graphics = converted.getJSONObject("graphics");
        if (graphics != null) {
            graphics.remove("native");
        }
    }

    /**
     * Existing {@code android.tee.securityLevel} is a string enum. Profile examples may use
     * Android {@code KeyInfo} integers 0/1/2; convert without inventing other values.
     */
    private static void convertTeeSecurityLevel(JSONObject converted, List<String> reserved) {
        JSONObject android = converted.getJSONObject("android");
        if (android == null) {
            return;
        }
        JSONObject tee = android.getJSONObject("tee");
        if (tee == null || !tee.containsKey("securityLevel")) {
            return;
        }
        Object raw = tee.get("securityLevel");
        if (raw instanceof String) {
            return;
        }
        if (raw instanceof Number && !(raw instanceof Float) && !(raw instanceof Double)
                && !(raw instanceof BigDecimal)) {
            int value = ((Number) raw).intValue();
            if (value == 0) {
                tee.put("securityLevel", "SOFTWARE");
            } else if (value == 1) {
                tee.put("securityLevel", "TRUSTED_ENVIRONMENT");
            } else if (value == 2) {
                tee.put("securityLevel", "STRONGBOX");
            } else {
                tee.remove("securityLevel");
                reserved.add("android.tee.securityLevel: unsupported integer " + value + " (stripped)");
            }
            return;
        }
        tee.remove("securityLevel");
        reserved.add("android.tee.securityLevel: unsupported type (stripped)");
    }

    private static void collectConsistencyWarnings(JSONObject converted, List<String> warnings) {
        JSONObject process = converted.getJSONObject("process");
        JSONObject android = converted.getJSONObject("android");
        if (process != null && android != null) {
            Object processName = process.get("processName");
            Object packageName = android.get("packageName");
            if (processName instanceof String && packageName instanceof String
                    && !processName.equals(packageName)) {
                warnings.add("process.processName != android.packageName");
            }
        }
        if (android == null) {
            return;
        }
        JSONObject build = android.getJSONObject("build");
        JSONObject properties = android.getJSONObject("properties");
        if (build != null && properties != null) {
            warnIfMismatch(build.get("MODEL"), properties.get("ro.product.model"),
                    "android.build.MODEL != android.properties.ro.product.model", warnings);
            warnIfMismatch(build.get("HARDWARE"), properties.get("ro.hardware"),
                    "android.build.HARDWARE != android.properties.ro.hardware", warnings);
            Object sdk = build.get("VERSION.SDK_INT");
            Object roSdk = properties.get("ro.build.version.sdk");
            if (sdk instanceof Number && roSdk instanceof String
                    && !String.valueOf(((Number) sdk).intValue()).equals(roSdk)) {
                warnings.add("android.build.VERSION.SDK_INT != android.properties.ro.build.version.sdk");
            }
        }
        JSONObject locale = android.getJSONObject("locale");
        if (locale != null && properties != null) {
            warnIfMismatch(locale.get("timezoneId"), properties.get("persist.sys.timezone"),
                    "android.locale.timezoneId != android.properties.persist.sys.timezone", warnings);
        }
    }

    private static void warnIfMismatch(Object left, Object right, String message, List<String> warnings) {
        if (left instanceof String && right instanceof String && !left.equals(right)) {
            warnings.add(message);
        }
    }
}

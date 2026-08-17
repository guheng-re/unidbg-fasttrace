package com.github.unidbg.virtualmodule.android;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.Arm64Svc;
import com.github.unidbg.arm.ArmSvc;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import com.github.unidbg.virtualmodule.VirtualModule;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@SuppressWarnings("unused")
public class MediaNdkModule extends VirtualModule<VM> {

    private static final Logger log = LoggerFactory.getLogger(MediaNdkModule.class);

    public static final byte[] WIDE_VINE_UUID = {(byte) 0xed, (byte) 0xef, (byte) 0x8b, (byte) 0xa9, 0x79, (byte) 0xd6, 0x4a,
            (byte) 0xce, (byte) 0xa3, (byte) 0xc8, 0x27, (byte) 0xdc, (byte) 0xd5, 0x1d, 0x21, (byte) 0xed};

    /** NDK media_status_t values used by session APIs. */
    public static final int AMEDIA_OK = 0;
    public static final int AMEDIA_DRM_NOT_PROVISIONED = -20001;
    public static final int AMEDIA_DRM_SESSION_NOT_OPENED = -20005;

    private MemoryBlock handleBlock;
    private MemoryBlock propertyStringBlock;
    private int propertyStringCapacity;
    private MemoryBlock propertyByteArrayBlock;
    private int propertyByteArrayCapacity;
    private MemoryBlock sessionIdBlock;
    private int sessionIdCapacity;
    private byte[] activeSessionId;
    private final boolean sessionApisRegistered;

    public MediaNdkModule(Emulator<?> emulator, VM vm) {
        super(emulator, vm, "libmediandk.so");
        // sessionApisRegistered is set in onInitialize via field assignment before super returns...
        // Actually onInitialize runs inside super(); field init order: defaults then ctor body after super.
        // So we capture from TraceEnvironmentConfig here (same as onInitialize used).
        TraceEnvironmentConfig cfg = TraceEnvironmentConfig.get(emulator);
        this.sessionApisRegistered = cfg != null && cfg.isAndroidDrmConfigured();
    }

    @Override
    protected void onInitialize(Emulator<?> emulator, VM extra, Map<String, UnidbgPointer> symbols) {
        boolean is64Bit = emulator.is64Bit();
        SvcMemory svcMemory = emulator.getSvcMemory();
        symbols.put("AMediaDrm_createByUUID", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return createByUUID(emulator);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return createByUUID(emulator);
            }
        }));

        symbols.put("AMediaDrm_getPropertyByteArray", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getPropertyByteArray(emulator);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getPropertyByteArray(emulator);
            }
        }));

        symbols.put("AMediaDrm_getPropertyString", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getPropertyString(emulator);
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return getPropertyString(emulator);
            }
        }));

        symbols.put("AMediaDrm_release", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return release();
            }
        } : new ArmSvc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return release();
            }
        }));

        // open/close session only when android.drm is configured (analysis path)
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.isAndroidDrmConfigured()) {
            symbols.put("AMediaDrm_openSession", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
                @Override
                public long handle(Emulator<?> emulator) {
                    return openSession(emulator);
                }
            } : new ArmSvc() {
                @Override
                public long handle(Emulator<?> emulator) {
                    return openSession(emulator);
                }
            }));
            symbols.put("AMediaDrm_closeSession", svcMemory.registerSvc(is64Bit ? new Arm64Svc() {
                @Override
                public long handle(Emulator<?> emulator) {
                    return closeSession(emulator);
                }
            } : new ArmSvc() {
                @Override
                public long handle(Emulator<?> emulator) {
                    return closeSession(emulator);
                }
            }));
        }
    }

    private long createByUUID(Emulator<?> emulator) {
        if (log.isDebugEnabled()) {
            log.debug("call createByUUID");
        }
        RegisterContext context = emulator.getContext();
        Pointer uuidPtr = context.getPointerArg(0);
        byte[] uuid = uuidPtr.getByteArray(0, 0x10);
        return createByUuidBytes(emulator, uuid);
    }

    /**
     * Shared create logic used by SVC and tests.
     *
     * @return handle peer, or {@code 0} when configured DRM rejects the UUID / is unavailable
     */
    long createByUuidBytes(Emulator<?> emulator, byte[] uuid) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.isAndroidDrmConfigured()) {
            TraceEnvironmentConfig.AndroidDrmConfig drm = config.getAndroidDrmConfig();
            String canonical = uuidBytesToCanonical(uuid);
            boolean available = drm.isAvailable();
            boolean supported = available && schemeSupported(drm.getSchemeUuids(), canonical);
            String marker = drm.getMarker();
            TraceEnvironmentEventSink.emit(emulator, "drm", "AMediaDrm_createByUUID",
                    "supported=" + supported + ",available=" + available
                            + ",uuid=" + canonical + ",marker=" + marker,
                    "json-config",
                    "配置的 DRM create " + canonical);
            if (!supported) {
                return 0L;
            }
            return ensureHandlePeer(emulator);
        }
        // legacy: only Widevine, otherwise throw
        if (Arrays.equals(uuid, WIDE_VINE_UUID)) {
            return ensureHandlePeer(emulator);
        }
        throw new UnsupportedOperationException("createByUUID");
    }

    private long getPropertyByteArray(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer propertyNamePtr = context.getPointerArg(1);
        Pointer propertyValuePtr = context.getPointerArg(2);
        String propertyName = propertyNamePtr.getString(0);
        return getPropertyByteArrayByName(emulator, propertyName, propertyValuePtr);
    }

    /**
     * Shared byte-array property logic used by SVC and tests.
     * Only {@code deviceUniqueId} is supported (legacy and configured).
     */
    public long getPropertyByteArrayByName(Emulator<?> emulator, String propertyName, Pointer propertyValuePtr) {
        if (!"deviceUniqueId".equals(propertyName)) {
            throw new UnsupportedOperationException("getPropertyByteArray: " + propertyName);
        }
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        final byte[] bytes;
        if (config != null && config.isAndroidDrmConfigured()) {
            TraceEnvironmentConfig.AndroidDrmConfig drm = config.getAndroidDrmConfig();
            bytes = config.resolveMediaDrmDeviceUniqueId();
            writePropertyByteArray(emulator, propertyValuePtr, bytes);
            TraceEnvironmentEventSink.emit(emulator, "drm", "AMediaDrm_getPropertyByteArray",
                    "property=deviceUniqueId,bytes=" + (bytes == null ? 0 : bytes.length)
                            + ",marker=" + drm.getMarker() + ",source=json-config",
                    "json-config",
                    "读取配置的 DRM 字节属性 deviceUniqueId");
            return 0;
        }
        // legacy unconfigured: 32 random or config-random bytes
        byte[] b = config == null ? null : config.resolveMediaDrmDeviceUniqueId();
        if (b == null) {
            b = new byte[0x20];
            new Random().nextBytes(b);
        }
        writePropertyByteArray(emulator, propertyValuePtr, b);
        return 0;
    }

    private long getPropertyString(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer propertyNamePtr = context.getPointerArg(1);
        Pointer propertyValuePtr = context.getPointerArg(2);
        String propertyName = propertyNamePtr.getString(0);
        return getPropertyStringByName(emulator, propertyName, propertyValuePtr);
    }

    /**
     * Shared property-string logic used by SVC and tests.
     */
    long getPropertyStringByName(Emulator<?> emulator, String propertyName, Pointer propertyValuePtr) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.isAndroidDrmConfigured()) {
            TraceEnvironmentConfig.AndroidDrmConfig drm = config.getAndroidDrmConfig();
            String value = resolveConfiguredProperty(drm, propertyName);
            if (value == null) {
                throw new UnsupportedOperationException("getPropertyString: " + propertyName);
            }
            writePropertyString(emulator, propertyValuePtr, value);
            TraceEnvironmentEventSink.emit(emulator, "drm", "AMediaDrm_getPropertyString",
                    "property=" + propertyName + ",value=" + value + ",marker=" + drm.getMarker(),
                    "json-config",
                    "读取配置的 DRM 属性 " + propertyName);
            return 0;
        }
        // legacy: only vendor
        if ("vendor".equals(propertyName)) {
            String manufacturer = config == null ? null : config.getAndroidBuildString("MANUFACTURER");
            final String value = manufacturer == null ? "Google" : manufacturer;
            writePropertyString(emulator, propertyValuePtr, value);
            return 0;
        }
        throw new UnsupportedOperationException("getPropertyString: " + propertyName);
    }

    private long openSession(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer sessionIdPtr = context.getPointerArg(1);
        return openSessionTo(emulator, sessionIdPtr);
    }

    private long closeSession(Emulator<?> emulator) {
        RegisterContext context = emulator.getContext();
        Pointer sessionIdPtr = context.getPointerArg(1);
        return closeSessionFrom(emulator, sessionIdPtr);
    }

    /**
     * Shared openSession logic (NDK: {@code AMediaDrm_openSession(mObj, sessionId)}).
     * Writes {@link #AMediaDrmSessionId} layout: pointer @0, size_t @4 (32-bit int) / @8 (64-bit long).
     */
    int openSessionTo(Emulator<?> emulator, Pointer sessionIdPtr) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config == null || !config.isAndroidDrmConfigured()) {
            // symbols only registered when configured; defensive
            return AMEDIA_DRM_SESSION_NOT_OPENED;
        }
        TraceEnvironmentConfig.AndroidDrmConfig drm = config.getAndroidDrmConfig();
        if (!drm.isProvisioned()) {
            TraceEnvironmentEventSink.emit(emulator, "drm", "AMediaDrm_openSession",
                    "status=NOT_PROVISIONED,bytes=0,marker=" + drm.getMarker() + ",source=json-config",
                    "json-config",
                    "配置的 DRM openSession 未 provision");
            return AMEDIA_DRM_NOT_PROVISIONED;
        }
        byte[] sessionBytes;
        if (drm.isSessionIdConfigured()) {
            sessionBytes = drm.getSessionId();
        } else {
            sessionBytes = drm.getMarker().getBytes(StandardCharsets.UTF_8);
        }
        if (sessionBytes == null) {
            sessionBytes = new byte[0];
        }
        writeSessionId(emulator, sessionIdPtr, sessionBytes);
        activeSessionId = Arrays.copyOf(sessionBytes, sessionBytes.length);
        TraceEnvironmentEventSink.emit(emulator, "drm", "AMediaDrm_openSession",
                "status=OK,bytes=" + sessionBytes.length
                        + ",marker=" + drm.getMarker() + ",source=json-config",
                "json-config",
                "配置的 DRM openSession");
        return AMEDIA_OK;
    }

    /**
     * Shared closeSession logic. Succeeds only when {@code sessionId} matches the active session
     * exact byte sequence.
     */
    int closeSessionFrom(Emulator<?> emulator, Pointer sessionIdPtr) {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        String marker = "null";
        if (config != null && config.isAndroidDrmConfigured()) {
            marker = config.getAndroidDrmConfig().getMarker();
        }
        byte[] supplied = readSessionIdBytes(emulator, sessionIdPtr);
        boolean ok = activeSessionId != null && supplied != null
                && Arrays.equals(activeSessionId, supplied);
        if (ok) {
            activeSessionId = null;
            TraceEnvironmentEventSink.emit(emulator, "drm", "AMediaDrm_closeSession",
                    "status=OK,bytes=" + supplied.length
                            + ",marker=" + marker + ",source=json-config",
                    "json-config",
                    "配置的 DRM closeSession");
            return AMEDIA_OK;
        }
        int len = supplied == null ? 0 : supplied.length;
        TraceEnvironmentEventSink.emit(emulator, "drm", "AMediaDrm_closeSession",
                "status=SESSION_NOT_OPENED,bytes=" + len
                        + ",marker=" + marker + ",source=json-config",
                "json-config",
                "配置的 DRM closeSession 会话无效");
        return AMEDIA_DRM_SESSION_NOT_OPENED;
    }

    long release() {
        if (propertyStringBlock != null) {
            propertyStringBlock.free();
            propertyStringBlock = null;
            propertyStringCapacity = 0;
        }
        if (propertyByteArrayBlock != null) {
            propertyByteArrayBlock.free();
            propertyByteArrayBlock = null;
            propertyByteArrayCapacity = 0;
        }
        if (sessionIdBlock != null) {
            sessionIdBlock.free();
            sessionIdBlock = null;
            sessionIdCapacity = 0;
        }
        activeSessionId = null;
        if (handleBlock != null) {
            handleBlock.free();
            handleBlock = null;
        }
        return 0;
    }

    /**
     * Write session id bytes into capacity-tracked block and fill {@code AMediaDrmSessionId}.
     */
    void writeSessionId(Emulator<?> emulator, Pointer sessionIdPtr, byte[] data) {
        if (data == null) {
            data = new byte[0];
        }
        int need = Math.max(data.length, 1);
        if (sessionIdBlock == null || sessionIdCapacity < need) {
            if (sessionIdBlock != null) {
                sessionIdBlock.free();
                sessionIdBlock = null;
            }
            sessionIdBlock = emulator.getMemory().malloc(need, true);
            sessionIdCapacity = need;
        }
        Pointer p = sessionIdBlock.getPointer();
        if (data.length > 0) {
            p.write(0, data, 0, data.length);
        }
        sessionIdPtr.setPointer(0, p);
        if (emulator.is32Bit()) {
            sessionIdPtr.setInt(4, data.length);
        } else {
            sessionIdPtr.setLong(8, data.length);
        }
    }

    /**
     * Read session id from {@code AMediaDrmSessionId}; bounds-checks length (0..1MiB).
     *
     * @return copy of bytes, or {@code null} if struct/pointer invalid
     */
    static byte[] readSessionIdBytes(Emulator<?> emulator, Pointer sessionIdPtr) {
        if (sessionIdPtr == null) {
            return null;
        }
        Pointer dataPtr = sessionIdPtr.getPointer(0);
        if (dataPtr == null) {
            return null;
        }
        long lenLong;
        if (emulator.is32Bit()) {
            lenLong = sessionIdPtr.getInt(4) & 0xffffffffL;
        } else {
            lenLong = sessionIdPtr.getLong(8);
        }
        if (lenLong < 0 || lenLong > 1024 * 1024) {
            return null;
        }
        int len = (int) lenLong;
        if (len == 0) {
            return new byte[0];
        }
        return dataPtr.getByteArray(0, len);
    }

    private long ensureHandlePeer(Emulator<?> emulator) {
        if (handleBlock == null) {
            handleBlock = emulator.getMemory().malloc(0x8, true);
        }
        return handleBlock.getPointer().peer;
    }

    /**
     * Write raw bytes into a capacity-tracked reusable block and fill AMediaDrm byte-array struct.
     * 32-bit: size_t as {@code int} at offset 4; 64-bit: size_t as {@code long} at offset 8.
     * Length is exact {@code data.length} (no pad/truncate).
     */
    void writePropertyByteArray(Emulator<?> emulator, Pointer propertyValuePtr, byte[] data) {
        if (data == null) {
            data = new byte[0];
        }
        int need = Math.max(data.length, 1);
        if (propertyByteArrayBlock == null || propertyByteArrayCapacity < need) {
            if (propertyByteArrayBlock != null) {
                propertyByteArrayBlock.free();
                propertyByteArrayBlock = null;
            }
            propertyByteArrayBlock = emulator.getMemory().malloc(need, true);
            propertyByteArrayCapacity = need;
        }
        Pointer p = propertyByteArrayBlock.getPointer();
        if (data.length > 0) {
            p.write(0, data, 0, data.length);
        }
        propertyValuePtr.setPointer(0, p);
        if (emulator.is32Bit()) {
            propertyValuePtr.setInt(4, data.length);
        } else {
            propertyValuePtr.setLong(8, data.length);
        }
    }

    /**
     * Write UTF-8 C string into a capacity-tracked shared block and fill AMediaDrm property struct.
     * 32-bit: length {@code int} at offset 4; 64-bit: length {@code long} at offset 8.
     * Length is UTF-8 byte count without trailing NUL.
     */
    void writePropertyString(Emulator<?> emulator, Pointer propertyValuePtr, String value) {
        if (value == null) {
            value = "";
        }
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        int need = utf8.length + 1;
        if (propertyStringBlock == null || propertyStringCapacity < need) {
            if (propertyStringBlock != null) {
                propertyStringBlock.free();
                propertyStringBlock = null;
            }
            propertyStringBlock = emulator.getMemory().malloc(need, true);
            propertyStringCapacity = need;
        }
        Pointer p = propertyStringBlock.getPointer();
        if (utf8.length > 0) {
            p.write(0, utf8, 0, utf8.length);
        }
        p.setByte(utf8.length, (byte) 0);

        propertyValuePtr.setPointer(0, p);
        if (emulator.is32Bit()) {
            propertyValuePtr.setInt(4, utf8.length);
        } else {
            propertyValuePtr.setLong(8, utf8.length);
        }
    }

    static String resolveConfiguredProperty(TraceEnvironmentConfig.AndroidDrmConfig drm, String propertyName) {
        if (drm == null || propertyName == null) {
            return null;
        }
        if ("vendor".equals(propertyName)) {
            return drm.getVendor();
        }
        if ("version".equals(propertyName)) {
            return drm.getVersion();
        }
        if ("description".equals(propertyName)) {
            return drm.getDescription();
        }
        if ("algorithms".equals(propertyName)) {
            return drm.getAlgorithms();
        }
        if ("securityLevel".equals(propertyName)) {
            return drm.getSecurityLevel();
        }
        if ("hdcpLevel".equals(propertyName)) {
            return drm.getHdcpLevel();
        }
        if ("maxHdcpLevel".equals(propertyName)) {
            return drm.getMaxHdcpLevel();
        }
        return null;
    }

    static boolean schemeSupported(List<String> schemeUuids, String canonicalUuid) {
        if (schemeUuids == null || canonicalUuid == null) {
            return false;
        }
        for (String u : schemeUuids) {
            if (canonicalUuid.equals(u)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Interpret 16 bytes as a standard big-endian UUID and return lowercase {@link UUID#toString()}.
     */
    static String uuidBytesToCanonical(byte[] uuid16) {
        if (uuid16 == null || uuid16.length != 16) {
            throw new IllegalArgumentException("uuid must be 16 bytes");
        }
        ByteBuffer bb = ByteBuffer.wrap(uuid16).order(ByteOrder.BIG_ENDIAN);
        long msb = bb.getLong();
        long lsb = bb.getLong();
        return new UUID(msb, lsb).toString().toLowerCase(Locale.ROOT);
    }

    /** Package-visible for tests: current shared string block capacity, or 0. */
    int getPropertyStringCapacityForTest() {
        return propertyStringCapacity;
    }

    /** Package-visible for tests. */
    MemoryBlock getHandleBlockForTest() {
        return handleBlock;
    }

    /** Package-visible for tests. */
    MemoryBlock getPropertyStringBlockForTest() {
        return propertyStringBlock;
    }

    /** Package-visible for tests. */
    int getPropertyByteArrayCapacityForTest() {
        return propertyByteArrayCapacity;
    }

    /** Package-visible for tests. */
    MemoryBlock getPropertyByteArrayBlockForTest() {
        return propertyByteArrayBlock;
    }

    /** Package-visible for tests. */
    boolean isSessionApiRegisteredForTest() {
        return sessionApisRegistered;
    }

    /** Package-visible for tests. */
    MemoryBlock getSessionIdBlockForTest() {
        return sessionIdBlock;
    }

    /** Package-visible for tests. */
    int getSessionIdCapacityForTest() {
        return sessionIdCapacity;
    }

    /** Package-visible for tests. */
    byte[] getActiveSessionIdForTest() {
        return activeSessionId == null ? null : Arrays.copyOf(activeSessionId, activeSessionId.length);
    }
}

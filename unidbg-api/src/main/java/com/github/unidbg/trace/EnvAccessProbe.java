package com.github.unidbg.trace;

import com.github.unidbg.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observer for environment / fingerprint reads.
 * Does not invent values and does not change takeover rules. <strong>On by
 * default.</strong> Disable with {@code -Dunidbg.env.probe=false} or
 * {@link #setEnabled(boolean) setEnabled(false)}. When on, every sidecar emit
 * is also logged, and previously silent misses on interesting paths / JNI /
 * ioctl / getifaddrs emit {@code kind=env_probe} with {@code source=unconfigured}.
 */
public final class EnvAccessProbe {

    public static final String SYSTEM_PROPERTY = "unidbg.env.probe";
    public static final String KIND = "env_probe";

    private static final Logger log = LoggerFactory.getLogger(EnvAccessProbe.class);

    private static volatile Boolean override;

    private EnvAccessProbe() {
    }

    public static void setEnabled(boolean enabled) {
        override = Boolean.valueOf(enabled);
    }

    public static void clearOverride() {
        override = null;
    }

    public static boolean isEnabled() {
        Boolean forced = override;
        if (forced != null) {
            return forced.booleanValue();
        }
        String raw = System.getProperty(SYSTEM_PROPERTY);
        if (raw == null || raw.trim().isEmpty()) {
            return true;
        }
        return Boolean.parseBoolean(raw);
    }

    /** Mirror an existing sidecar emit to the log when the probe is on. */
    public static void logEmit(String kind, String api, Object value, String source, String note) {
        if (!isEnabled()) {
            return;
        }
        log.info("[环境探测] kind={} api={} value={} source={} note={}",
                kind, api, summarize(value), source, note);
    }

    /**
     * Record that the guest tried to read an interesting environment slot that
     * the template does not fill. No-op when the probe is off.
     */
    public static void miss(Emulator<?> emulator, String api, String value, String note) {
        if (!isEnabled()) {
            return;
        }
        TraceEnvironmentEventSink.emit(emulator, KIND, api, value, "unconfigured", note);
    }

    public static boolean isInterestingPath(String pathname) {
        if (pathname == null || pathname.isEmpty()) {
            return false;
        }
        if (pathname.startsWith("/proc/") || pathname.equals("/proc")) {
            return true;
        }
        if (pathname.startsWith("/sys/") || pathname.equals("/sys")) {
            return true;
        }
        if ("/dev/random".equals(pathname) || "/dev/urandom".equals(pathname)
                || "/dev/srandom".equals(pathname) || "/dev/alarm".equals(pathname)) {
            return true;
        }
        return pathname.startsWith("/etc/") && (pathname.contains("mtab")
                || pathname.contains("hosts") || pathname.contains("passwd"));
    }

    public static boolean isInterestingJniSignature(String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        return startsWithAny(signature,
                "android/os/Build",
                "android/os/SystemClock",
                "android/provider/Settings",
                "android/telephony/",
                "android/net/wifi/",
                "android/net/ConnectivityManager",
                "android/net/NetworkInfo",
                "android/net/LinkProperties",
                "android/net/wifi/Wifi",
                "java/net/NetworkInterface",
                "android/hardware/Camera",
                "android/hardware/camera2/",
                "android/media/ImageReader",
                "android/hardware/Sensor",
                "android/os/BatteryManager",
                "android/os/PowerManager",
                "android/location/",
                "android/media/MediaDrm",
                "android/security/",
                "android/os/Debug",
                "android/os/SELinux",
                "android/app/ActivityManager",
                "com/google/android/gms/ads/identifier/",
                "com/google/android/gms/appset/",
                "java/lang/System->currentTimeMillis",
                "java/lang/System->nanoTime",
                "java/lang/System->getProperty",
                "java/lang/System->getenv",
                "java/lang/System->getProperties",
                "java/lang/Runtime->",
                "android/os/Process->myPid",
                "android/os/Process->myUid",
                "android/os/Process->myTid");
    }

    public static boolean isNetworkIoctl(long request) {
        return request == 0x8912L /* SIOCGIFCONF */
                || request == 0x8913L /* SIOCGIFFLAGS */
                || request == 0x8915L /* SIOCGIFADDR */
                || request == 0x8916L /* SIOCGIFDSTADDR */
                || request == 0x8918L /* SIOCGIFBRDADDR */
                || request == 0x8919L /* SIOCGIFNETMASK */
                || request == 0x8910L /* SIOCGIFNAME */
                || request == 0x8927L /* SIOCGIFHWADDR */
                || request == 0x8921L /* SIOCGIFMTU */
                || request == 0x8933L /* SIOCGIFINDEX */;
    }

    public static String networkIoctlName(long request) {
        if (request == 0x8912L) {
            return "ioctl(SIOCGIFCONF)";
        }
        if (request == 0x8913L) {
            return "ioctl(SIOCGIFFLAGS)";
        }
        if (request == 0x8915L) {
            return "ioctl(SIOCGIFADDR)";
        }
        if (request == 0x8916L) {
            return "ioctl(SIOCGIFDSTADDR)";
        }
        if (request == 0x8918L) {
            return "ioctl(SIOCGIFBRDADDR)";
        }
        if (request == 0x8919L) {
            return "ioctl(SIOCGIFNETMASK)";
        }
        if (request == 0x8910L) {
            return "ioctl(SIOCGIFNAME)";
        }
        if (request == 0x8927L) {
            return "ioctl(SIOCGIFHWADDR)";
        }
        if (request == 0x8921L) {
            return "ioctl(SIOCGIFMTU)";
        }
        if (request == 0x8933L) {
            return "ioctl(SIOCGIFINDEX)";
        }
        return "ioctl(0x" + Long.toHexString(request) + ")";
    }

    private static boolean startsWithAny(String signature, String... prefixes) {
        for (int i = 0; i < prefixes.length; i++) {
            if (signature.startsWith(prefixes[i])) {
                return true;
            }
        }
        return false;
    }

    private static String summarize(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof byte[]) {
            return "bytes=" + ((byte[]) value).length;
        }
        String text = String.valueOf(value);
        return text.length() > 240 ? text.substring(0, 240) + "..." : text;
    }
}

package com.github.unidbg.file.linux;

import com.github.unidbg.env.TraceEnvironmentConfig;

import java.nio.charset.StandardCharsets;

/**
 * Pure renderer for {@code /sys/class/power_supply/battery/*} from {@code android.battery}
 * extras. No host sysfs, no sidecar.
 */
public final class ConfiguredPowerSupplyFiles {

    public static final String HEALTH_PATH = "/sys/class/power_supply/battery/health";
    public static final String VOLTAGE_NOW_PATH = "/sys/class/power_supply/battery/voltage_now";
    public static final String TEMP_PATH = "/sys/class/power_supply/battery/temp";

    private ConfiguredPowerSupplyFiles() {
    }

    /**
     * @return UTF-8 file bytes including a trailing LF, or {@code null} when the matching
     *         battery key is absent (caller must not take over the path).
     */
    public static byte[] render(TraceEnvironmentConfig config, String pathname) {
        if (config == null || !config.isAndroidBatteryConfigured()
                || config.getAndroidBatteryConfig() == null || pathname == null) {
            return null;
        }
        TraceEnvironmentConfig.AndroidBatteryConfig battery = config.getAndroidBatteryConfig();
        if (HEALTH_PATH.equals(pathname)) {
            if (!battery.isHealthConfigured()) {
                return null;
            }
            return utf8Line(healthText(battery.getHealth()));
        }
        if (VOLTAGE_NOW_PATH.equals(pathname)) {
            if (!battery.isVoltageMvConfigured()) {
                return null;
            }
            long microvolts = battery.getVoltageMv() * 1000L;
            return utf8Line(Long.toString(microvolts));
        }
        if (TEMP_PATH.equals(pathname)) {
            if (!battery.isTemperatureTenthsCConfigured()) {
                return null;
            }
            return utf8Line(Integer.toString(battery.getTemperatureTenthsC()));
        }
        return null;
    }

    static String healthText(int health) {
        switch (health) {
            case 2:
                return "Good";
            case 3:
                return "Overheat";
            case 4:
                return "Dead";
            case 5:
                return "Over voltage";
            case 6:
                return "Unspecified failure";
            case 7:
                return "Cold";
            case 1:
            default:
                return "Unknown";
        }
    }

    private static byte[] utf8Line(String text) {
        return (text + "\n").getBytes(StandardCharsets.UTF_8);
    }
}

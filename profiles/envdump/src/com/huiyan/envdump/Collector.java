package com.huiyan.envdump;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaDrm;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.accessibility.AccessibilityManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class Collector {
    private final Context ctx;
    private final File outDir;
    private final File filesDir;
    private final JSONArray skipped = new JSONArray();
    private final JSONObject root = new JSONObject();

    Collector(Context ctx, File outDir) {
        this.ctx = ctx.getApplicationContext();
        this.outDir = outDir;
        this.filesDir = new File(outDir, "files");
    }

    File run(Progress p) throws Exception {
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("mkdir " + outDir);
        }
        wipe(outDir);
        filesDir.mkdirs();
        p.on("native process/linux");
        fillProcessLinux();
        p.on("properties");
        fillProperties();
        p.on("build/settings");
        fillBuildSettings();
        p.on("packages/features");
        fillPackages();
        p.on("display/battery/sensors");
        fillDeviceJava();
        p.on("network");
        fillNetwork();
        p.on("files overlay");
        fillFileWhitelist();
        p.on("filesystem");
        fillFilesystem();
        p.on("graphics/drm");
        fillGraphicsDrm();
        p.on("zip");
        writeText(new File(outDir, "device-fingerprint.json"), root.toString(2));
        writeText(new File(outDir, "skipped.json"), skipped.toString(2));
        File zip = new File(outDir, "envdump.zip");
        zipDir(outDir, zip);
        writeText(new File(outDir, "DONE"), "ok\n");
        return zip;
    }

    interface Progress {
        void on(String msg);
    }

    private void fillProcessLinux() throws Exception {
        JSONObject process = new JSONObject();
        int pid = Native.getpid();
        int uid = Native.getuid();
        int gid = Native.getgid();
        process.put("processName", ctx.getPackageName());
        process.put("pid", pid);
        process.put("pgid", pid);
        process.put("sid", pid);
        process.put("ppid", Native.getppid());
        process.put("tid", pid);
        process.put("uid", uid);
        process.put("gid", gid);
        process.put("euid", uid);
        process.put("egid", gid);
        process.put("threadName", "main");
        String status = readUtf("/proc/self/status");
        JSONArray gids = new JSONArray();
        if (status != null) {
            for (String line : status.split("\n")) {
                if (line.startsWith("Groups:")) {
                    String[] sp = line.substring(7).trim().split("\\s+");
                    for (int i = 0; i < sp.length; i++) {
                        if (sp[i].length() > 0) {
                            try {
                                gids.put(Integer.parseInt(sp[i]));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }
        }
        process.put("supplementaryGids", gids);
        root.put("process", process);

        JSONObject time = new JSONObject();
        time.put("timezoneMinutesWest", -TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000);
        root.put("time", time);

        JSONObject linux = new JSONObject();
        JSONObject uname = parseObj(Native.unameJson());
        if (uname != null) {
            String mach = uname.optString("machine", "aarch64");
            JSONObject u = new JSONObject();
            u.put("sysname", uname.optString("sysname"));
            u.put("nodename", uname.optString("nodename"));
            u.put("release", uname.optString("release"));
            u.put("version", uname.optString("version"));
            u.put("machine64", mach);
            u.put("machine32", mach.contains("aarch64") || mach.contains("arm64") ? "armv8l" : mach);
            u.put("domainname", "(none)");
            linux.put("uname", u);
        } else {
            skip("linux.uname", "uname failed");
        }

        JSONObject cpu = new JSONObject();
        String online = readUtf("/sys/devices/system/cpu/online");
        String present = readUtf("/sys/devices/system/cpu/present");
        String possible = readUtf("/sys/devices/system/cpu/possible");
        if (online != null) {
            cpu.put("online", online.trim());
        }
        if (present != null) {
            cpu.put("present", present.trim());
        }
        if (possible != null) {
            cpu.put("possible", possible.trim());
        }
        int ncpu = Runtime.getRuntime().availableProcessors();
        cpu.put("configuredProcessorCount", ncpu);
        cpu.put("onlineProcessorCount", ncpu);
        linux.put("cpu", cpu);

        JSONObject auxv = new JSONObject();
        auxv.put("hwcap64", Native.getauxval(16));
        auxv.put("hwcap2_64", Native.getauxval(26));
        auxv.put("hwcap32", 1);
        auxv.put("hwcap2_32", 2);
        auxv.put("platform64", "aarch64");
        auxv.put("platform32", "v8l");
        auxv.put("execFn", "/system/bin/app_process64");
        linux.put("auxv", auxv);

        JSONArray env = new JSONArray();
        String[] en = Native.environ();
        if (en != null) {
            for (int i = 0; i < en.length; i++) {
                env.put(en[i]);
            }
        }
        linux.put("environ", env);

        JSONObject rlimits = new JSONObject();
        JSONObject nofile = parseObj(Native.rlimitJson(7));
        if (nofile != null) {
            rlimits.put("nofile", nofile);
        }
        linux.put("rlimits", rlimits);

        JSONObject sysinfo = new JSONObject();
        String mem = readUtf("/proc/meminfo");
        String up = readUtf("/proc/uptime");
        String load = readUtf("/proc/loadavg");
        if (mem != null) {
            sysinfo.put("totalRam", kb(mem, "MemTotal:"));
            sysinfo.put("freeRam", kb(mem, "MemFree:"));
            sysinfo.put("sharedRam", kb(mem, "Shmem:"));
            sysinfo.put("bufferRam", kb(mem, "Buffers:"));
            sysinfo.put("totalSwap", kb(mem, "SwapTotal:"));
            sysinfo.put("freeSwap", kb(mem, "SwapFree:"));
        }
        if (up != null) {
            try {
                sysinfo.put("uptime", (long) Double.parseDouble(up.trim().split("\\s+")[0]));
            } catch (Exception ignored) {
            }
        }
        if (load != null) {
            String[] lp = load.trim().split("\\s+");
            if (lp.length >= 3) {
                JSONArray loads = new JSONArray();
                try {
                    loads.put((int) (Double.parseDouble(lp[0]) * 65536));
                    loads.put((int) (Double.parseDouble(lp[1]) * 65536));
                    loads.put((int) (Double.parseDouble(lp[2]) * 65536));
                    sysinfo.put("loads", loads);
                } catch (Exception ignored) {
                }
            }
        }
        sysinfo.put("memUnit", 4096);
        linux.put("sysinfo", sysinfo);

        JSONObject proc = new JSONObject();
        JSONArray cmd = new JSONArray();
        cmd.put(ctx.getPackageName());
        proc.put("cmdline", cmd);
        String cg = readUtf("/proc/self/cgroup");
        JSONArray cgs = new JSONArray();
        if (cg != null) {
            String[] lines = cg.split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].length() > 0) {
                    cgs.put(lines[i]);
                }
            }
        }
        proc.put("cgroups", cgs);
        proc.put("comm", "main");
        proc.put("tracerPid", 0);
        String boot = readUtf("/proc/sys/kernel/random/boot_id");
        if (boot != null) {
            proc.put("bootId", boot.trim());
        }
        String sel = readUtf("/proc/self/attr/current");
        if (sel != null) {
            proc.put("selinuxContext", sel.trim());
        }
        linux.put("proc", proc);

        JSONObject commands = new JSONObject();
        putCmd(commands, "uptime", "/proc/uptime");
        putCmd(commands, "cat /proc/version", "/proc/version");
        putCmd(commands, "cat /proc/mounts", "/proc/mounts");
        linux.put("commands", commands);

        linux.put("files", new JSONObject());
        linux.put("processes", collectProcesses());
        root.put("schemaVersion", "traceai-device-fingerprint/v1");
        root.put("profileName", safeName(Build.BRAND + "-" + Build.DEVICE + "-" + Build.VERSION.SDK_INT));
        root.put("fileOverlayRoot", "files");
        root.put("linux", linux);
    }

    private void fillProperties() throws Exception {
        JSONObject android = androidObj();
        JSONObject props = new JSONObject();
        String[] keys = assetLines("property_keys.txt");
        int ok = 0;
        for (int i = 0; i < keys.length; i++) {
            String k = keys[i].trim();
            if (k.length() == 0) {
                continue;
            }
            String v = Native.getprop(k);
            if (v == null || v.length() == 0) {
                skip("android.properties." + k, "empty");
                continue;
            }
            props.put(k, v);
            ok++;
        }
        android.put("properties", props);
        skip("android.properties.count", "ok=" + ok + " keys=" + keys.length);
    }

    private void fillBuildSettings() throws Exception {
        JSONObject android = androidObj();
        android.put("packageName", ctx.getPackageName());
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            android.put("versionName", pi.versionName == null ? "" : pi.versionName);
            android.put("versionCode", pi.versionCode);
            android.put("apkPath", pi.applicationInfo.sourceDir);
            android.put("dataDir", pi.applicationInfo.dataDir);
            android.put("nativeLibraryDir64", pi.applicationInfo.nativeLibraryDir);
        } catch (Exception e) {
            skip("android.selfPackage", e.toString());
        }
        JSONObject build = new JSONObject();
        build.put("BRAND", Build.BRAND);
        build.put("MANUFACTURER", Build.MANUFACTURER);
        build.put("MODEL", Build.MODEL);
        build.put("DEVICE", Build.DEVICE);
        build.put("PRODUCT", Build.PRODUCT);
        build.put("BOARD", Build.BOARD);
        build.put("HARDWARE", Build.HARDWARE);
        build.put("BOOTLOADER", Build.BOOTLOADER);
        build.put("DISPLAY", Build.DISPLAY);
        build.put("ID", Build.ID);
        build.put("HOST", Build.HOST);
        build.put("USER", Build.USER);
        build.put("TAGS", Build.TAGS);
        build.put("TYPE", Build.TYPE);
        build.put("TIME", Build.TIME);
        build.put("FINGERPRINT", Build.FINGERPRINT);
        build.put("CPU_ABI", Build.CPU_ABI);
        build.put("CPU_ABI2", Build.CPU_ABI2);
        try {
            build.put("SERIAL", Build.getSerial());
        } catch (Exception e) {
            skip("android.build.SERIAL", e.toString());
        }
        JSONArray abis = new JSONArray();
        String[] sa = Build.SUPPORTED_ABIS;
        if (sa != null) {
            for (int i = 0; i < sa.length; i++) {
                abis.put(sa[i]);
            }
        }
        build.put("SUPPORTED_ABIS", abis);
        build.put("VERSION.RELEASE", Build.VERSION.RELEASE);
        build.put("VERSION.SDK_INT", Build.VERSION.SDK_INT);
        build.put("VERSION.INCREMENTAL", Build.VERSION.INCREMENTAL);
        build.put("VERSION.CODENAME", Build.VERSION.CODENAME);
        build.put("VERSION.SECURITY_PATCH", Build.VERSION.SECURITY_PATCH);
        android.put("build", build);

        JSONObject settings = new JSONObject();
        settings.put("secure", settingsNs("secure", "settings_secure.txt"));
        settings.put("global", settingsNs("global", "settings_global.txt"));
        settings.put("system", settingsNs("system", "settings_system.txt"));
        android.put("settings", settings);

        JSONObject ids = new JSONObject();
        try {
            String aid = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (aid != null) {
                ids.put("androidId", aid);
            }
        } catch (Exception e) {
            skip("android.identifiers.androidId", e.toString());
        }
        android.put("identifiers", ids);

        JSONObject runtime = new JSONObject();
        JSONObject sp = new JSONObject();
        sp.put("os.name", System.getProperty("os.name"));
        sp.put("os.arch", System.getProperty("os.arch"));
        sp.put("java.vm.name", System.getProperty("java.vm.name"));
        runtime.put("systemProperties", sp);
        runtime.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        runtime.put("maxMemoryBytes", Runtime.getRuntime().maxMemory());
        runtime.put("totalMemoryBytes", Runtime.getRuntime().totalMemory());
        runtime.put("freeMemoryBytes", Runtime.getRuntime().freeMemory());
        android.put("runtime", runtime);
        android.put("locale", localeJson());
    }

    private JSONObject settingsNs(String ns, String asset) {
        JSONObject o = new JSONObject();
        String[] keys = assetLines(asset);
        for (int i = 0; i < keys.length; i++) {
            String k = keys[i].trim();
            if (k.length() == 0) {
                continue;
            }
            try {
                String v = null;
                if ("secure".equals(ns)) {
                    v = Settings.Secure.getString(ctx.getContentResolver(), k);
                } else if ("global".equals(ns)) {
                    v = Settings.Global.getString(ctx.getContentResolver(), k);
                } else {
                    v = Settings.System.getString(ctx.getContentResolver(), k);
                }
                if (v == null) {
                    skip("settings." + ns + "." + k, "null");
                    continue;
                }
                o.put(k, v);
            } catch (Exception e) {
                skip("settings." + ns + "." + k, e.toString());
            }
        }
        return o;
    }

    private void fillPackages() throws Exception {
        JSONObject android = androidObj();
        PackageManager pm = ctx.getPackageManager();
        JSONArray pkgs = new JSONArray();
        List<PackageInfo> list;
        try {
            list = pm.getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES);
        } catch (Exception e) {
            skip("android.packages", e.toString());
            list = new ArrayList<PackageInfo>();
        }
        for (int i = 0; i < list.size(); i++) {
            PackageInfo pi = list.get(i);
            JSONObject o = new JSONObject();
            o.put("packageName", pi.packageName);
            o.put("versionName", pi.versionName == null ? "" : pi.versionName);
            o.put("versionCode", pi.versionCode);
            if (pi.applicationInfo != null) {
                o.put("sourceDir", pi.applicationInfo.sourceDir);
                o.put("dataDir", pi.applicationInfo.dataDir);
                o.put("uid", pi.applicationInfo.uid);
                o.put("enabled", pi.applicationInfo.enabled);
                o.put("systemApp", (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            }
            JSONArray sigs = new JSONArray();
            try {
                Signature[] ss = pi.signatures;
                if (pi.signingInfo != null) {
                    ss = pi.signingInfo.getApkContentsSigners();
                }
                if (ss != null) {
                    for (int j = 0; j < ss.length; j++) {
                        sigs.put(bytesToHex(ss[j].toByteArray()));
                    }
                }
            } catch (Exception ignored) {
            }
            o.put("signaturesHex", sigs);
            pkgs.put(o);
        }
        android.put("packages", pkgs);
        JSONArray feats = new JSONArray();
        FeatureInfo[] fi = pm.getSystemAvailableFeatures();
        if (fi != null) {
            for (int i = 0; i < fi.length; i++) {
                if (fi[i] != null && fi[i].name != null) {
                    JSONObject f = new JSONObject();
                    f.put("name", fi[i].name);
                    feats.put(f);
                }
            }
        }
        android.put("features", feats);
    }

    private void fillDeviceJava() throws Exception {
        JSONObject android = androidObj();
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        JSONObject display = new JSONObject();
        display.put("widthPixels", dm.widthPixels);
        display.put("heightPixels", dm.heightPixels);
        display.put("densityDpi", dm.densityDpi);
        display.put("scaledDensity", (double) dm.scaledDensity);
        display.put("xdpi", (double) dm.xdpi);
        display.put("ydpi", (double) dm.ydpi);
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            display.put("refreshRate", (double) wm.getDefaultDisplay().getRefreshRate());
            display.put("rotation", wm.getDefaultDisplay().getRotation());
        } catch (Exception e) {
            skip("android.display.refresh", e.toString());
        }
        android.put("display", display);

        Configuration cfg = ctx.getResources().getConfiguration();
        JSONObject configuration = new JSONObject();
        configuration.put("orientation", cfg.orientation);
        configuration.put("screenLayout", cfg.screenLayout);
        configuration.put("uiMode", cfg.uiMode);
        configuration.put("fontScale", (double) cfg.fontScale);
        configuration.put("densityDpi", cfg.densityDpi);
        configuration.put("screenWidthDp", cfg.screenWidthDp);
        configuration.put("screenHeightDp", cfg.screenHeightDp);
        configuration.put("smallestScreenWidthDp", cfg.smallestScreenWidthDp);
        configuration.put("keyboard", cfg.keyboard);
        configuration.put("navigation", cfg.navigation);
        configuration.put("keyboardHidden", cfg.keyboardHidden);
        android.put("configuration", configuration);

        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            JSONObject battery = new JSONObject();
            battery.put("capacityPercent", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            battery.put("chargeCounterUah", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));
            battery.put("currentNowUa", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
            battery.put("currentAverageUa", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE));
            android.put("battery", battery);
        } catch (Exception e) {
            skip("android.battery", e.toString());
        }

        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            JSONObject power = new JSONObject();
            power.put("interactive", pm.isInteractive());
            power.put("powerSaveMode", pm.isPowerSaveMode());
            power.put("deviceIdleMode", pm.isDeviceIdleMode());
            android.put("power", power);
            JSONObject thermal = new JSONObject();
            if (Build.VERSION.SDK_INT >= 29) {
                thermal.put("currentThermalStatus", pm.getCurrentThermalStatus());
            }
            android.put("thermal", thermal);
        } catch (Exception e) {
            skip("android.power", e.toString());
        }

        try {
            SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
            List<Sensor> sl = sm.getSensorList(Sensor.TYPE_ALL);
            JSONObject sensors = new JSONObject();
            JSONArray types = new JSONArray();
            JSONObject names = new JSONObject();
            JSONObject vendors = new JSONObject();
            JSONObject versions = new JSONObject();
            for (int i = 0; i < sl.size(); i++) {
                Sensor s = sl.get(i);
                int t = s.getType();
                String k = String.valueOf(t);
                if (names.has(k)) {
                    continue;
                }
                types.put(t);
                names.put(k, s.getName());
                vendors.put(k, s.getVendor());
                versions.put(k, s.getVersion());
            }
            sensors.put("types", types);
            sensors.put("names", names);
            sensors.put("vendors", vendors);
            sensors.put("versions", versions);
            android.put("sensors", sensors);
        } catch (Exception e) {
            skip("android.sensors", e.toString());
        }

        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            JSONObject tel = new JSONObject();
            tel.put("networkCountryIso", tm.getNetworkCountryIso());
            tel.put("simCountryIso", tm.getSimCountryIso());
            tel.put("phoneType", tm.getPhoneType());
            tel.put("networkRoaming", tm.isNetworkRoaming());
            try {
                tel.put("dataNetworkType", tm.getDataNetworkType());
            } catch (Exception e) {
                skip("android.telephony.dataNetworkType", e.toString());
            }
            JSONArray slots = new JSONArray();
            JSONObject slot = new JSONObject();
            slot.put("slotIndex", 0);
            slot.put("simState", tm.getSimState());
            try {
                slot.put("imei", tm.getImei());
            } catch (Exception e) {
                skip("android.telephony.imei", e.toString());
            }
            slots.put(slot);
            tel.put("slots", slots);
            android.put("telephony", tel);
        } catch (Exception e) {
            skip("android.telephony", e.toString());
        }

        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            String[] ids = cm.getCameraIdList();
            JSONObject cameras = new JSONObject();
            cameras.put("count", ids.length);
            JSONArray infos = new JSONArray();
            for (int i = 0; i < ids.length; i++) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(ids[i]);
                JSONObject inf = new JSONObject();
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                Integer ori = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
                inf.put("facing", facing == null ? -1 : facing.intValue());
                inf.put("orientation", ori == null ? 0 : ori.intValue());
                infos.put(inf);
            }
            cameras.put("infos", infos);
            android.put("cameras", cameras);
        } catch (Exception e) {
            skip("android.cameras", e.toString());
        }

        try {
            if (ctx.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")
                    == PackageManager.PERMISSION_GRANTED
                    || ctx.checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION")
                    == PackageManager.PERMISSION_GRANTED) {
                LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
                Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (loc == null) {
                    loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
                if (loc != null) {
                    JSONObject location = new JSONObject();
                    location.put("latitude", loc.getLatitude());
                    location.put("longitude", loc.getLongitude());
                    location.put("accuracy", (double) loc.getAccuracy());
                    android.put("location", location);
                } else {
                    skip("android.location", "no last location");
                }
            } else {
                skip("android.location", "no permission");
            }
        } catch (Exception e) {
            skip("android.location", e.toString());
        }

        try {
            AccessibilityManager am = (AccessibilityManager) ctx.getSystemService(Context.ACCESSIBILITY_SERVICE);
            JSONObject acc = new JSONObject();
            acc.put("enabled", am.isEnabled());
            android.put("accessibility", acc);
        } catch (Exception e) {
            skip("android.accessibility", e.toString());
        }

        try {
            AudioManager au = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            JSONObject audio = new JSONObject();
            audio.put("mode", au.getMode());
            audio.put("ringerMode", au.getRingerMode());
            android.put("audio", audio);
        } catch (Exception e) {
            skip("android.audio", e.toString());
        }

        try {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            List<InputMethodInfo> ims = imm.getInputMethodList();
            JSONArray arr = new JSONArray();
            if (ims != null) {
                for (int i = 0; i < ims.size(); i++) {
                    JSONObject o = new JSONObject();
                    o.put("id", ims.get(i).getId());
                    arr.put(o);
                }
            }
            android.put("inputMethods", arr);
        } catch (Exception e) {
            skip("android.inputMethods", e.toString());
        }

        try {
            Account[] accts = AccountManager.get(ctx).getAccounts();
            JSONArray arr = new JSONArray();
            for (int i = 0; i < accts.length; i++) {
                JSONObject o = new JSONObject();
                o.put("type", accts[i].type);
                arr.put(o);
            }
            android.put("accounts", arr);
        } catch (Exception e) {
            skip("android.accounts", e.toString());
        }

    }

    private void fillNetwork() throws Exception {
        JSONObject net = networkObj();
        JSONArray ifaces = new JSONArray();
        String[] names = Native.listDir("/sys/class/net");
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                String n = names[i];
                JSONObject o = new JSONObject();
                o.put("name", n);
                putIf(o, "mac", readUtf("/sys/class/net/" + n + "/address"));
                putIf(o, "mtu", readUtf("/sys/class/net/" + n + "/mtu"));
                putIf(o, "operState", readUtf("/sys/class/net/" + n + "/operstate"));
                String car = readUtf("/sys/class/net/" + n + "/carrier");
                if (car != null) {
                    o.put("carrier", car.trim().equals("1"));
                }
                ifaces.put(o);
                copyOverlay("/sys/class/net/" + n + "/address");
                copyOverlay("/sys/class/net/" + n + "/mtu");
                copyOverlay("/sys/class/net/" + n + "/operstate");
                copyOverlay("/sys/class/net/" + n + "/carrier");
            }
        }
        net.put("interfaces", ifaces);
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            JSONObject wifi = new JSONObject();
            wifi.put("enabled", wm.isWifiEnabled());
            WifiInfo wi = wm.getConnectionInfo();
            if (wi != null) {
                wifi.put("ssid", wi.getSSID());
                wifi.put("bssid", wi.getBSSID());
                wifi.put("rssi", wi.getRssi());
                wifi.put("ip", wi.getIpAddress());
                try {
                    wifi.put("mac", wi.getMacAddress());
                } catch (Exception e) {
                    skip("network.wifi.mac", e.toString());
                }
            }
            net.put("wifi", wifi);
        } catch (Exception e) {
            skip("network.wifi", e.toString());
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network nw = cm.getActiveNetwork();
            JSONObject cap = new JSONObject();
            if (nw != null) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(nw);
                if (nc != null) {
                    cap.put("internet", nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
                    cap.put("wifi", nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
                    cap.put("cellular", nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
                    cap.put("vpn", nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
                }
            }
            net.put("capabilities", cap);
        } catch (Exception e) {
            skip("network.capabilities", e.toString());
        }
        try {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            JSONObject bt = new JSONObject();
            if (ba != null) {
                bt.put("enabled", ba.isEnabled());
                try {
                    bt.put("name", ba.getName());
                    bt.put("address", ba.getAddress());
                } catch (Exception e) {
                    skip("network.bluetooth.detail", e.toString());
                }
            }
            net.put("bluetooth", bt);
        } catch (Exception e) {
            skip("network.bluetooth", e.toString());
        }
        root.put("network", net);
    }

    private void fillFileWhitelist() {
        JSONObject linux = root.optJSONObject("linux");
        JSONObject files = linux != null ? linux.optJSONObject("files") : null;
        if (files == null) {
            files = new JSONObject();
        }
        String[] paths = assetLines("file_whitelist.txt");
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i].trim();
            if (path.length() == 0) {
                continue;
            }
            byte[] raw = Native.readFile(path);
            if (raw == null) {
                skip("files." + path, "unreadable");
                continue;
            }
            try {
                files.put(path, new String(raw, StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
            writeOverlay(path, raw);
        }
        try {
            if (linux != null) {
                linux.put("files", files);
            }
        } catch (Exception ignored) {
        }
    }

    private void fillFilesystem() throws Exception {
        JSONObject fs = new JSONObject();
        JSONObject statfs = new JSONObject();
        String[] mounts = new String[] {"/", "/system", "/data", "/storage/emulated/0", "/vendor"};
        for (int i = 0; i < mounts.length; i++) {
            String js = Native.statfsJson(mounts[i]);
            if (js == null) {
                skip("filesystem.statfs." + mounts[i], "failed");
                continue;
            }
            statfs.put(mounts[i], new JSONObject(js));
        }
        fs.put("statfs", statfs);
        JSONObject stat = new JSONObject();
        String[] fontNames = Native.listDir("/system/fonts");
        JSONArray ls = new JSONArray();
        if (fontNames != null) {
            for (int i = 0; i < fontNames.length; i++) {
                String fp = "/system/fonts/" + fontNames[i];
                String st = Native.statJson(fp);
                if (st != null) {
                    JSONObject o = new JSONObject(st);
                    o.put("name", fontNames[i]);
                    stat.put(fp, o);
                    ls.put(fontNames[i] + " " + o.optLong("size"));
                }
            }
        }
        writeOverlay("/system/fonts_ls.txt", ls.toString().getBytes(StandardCharsets.UTF_8));
        String bp = Native.statJson("/system/build.prop");
        if (bp != null) {
            stat.put("/system/build.prop", new JSONObject(bp));
        }
        fs.put("stat", stat);
        root.put("filesystem", fs);
    }

    private void fillGraphicsDrm() {
        try {
            JSONObject drm = new JSONObject();
            UUID widevine = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
            MediaDrm md = new MediaDrm(widevine);
            byte[] id = md.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID);
            md.release();
            if (id != null) {
                JSONObject random = root.optJSONObject("random");
                if (random == null) {
                    random = new JSONObject();
                    root.put("random", random);
                }
                random.put("mediaDrmDeviceUniqueIdHex", bytesToHex(id).toLowerCase(Locale.US));
            }
            JSONObject android = androidObj();
            android.put("drm", drm);
        } catch (Exception e) {
            skip("drm", e.toString());
        }
        try {
            byte[] rnd = Native.readUrandom(16);
            if (rnd != null) {
                JSONObject random = root.optJSONObject("random");
                if (random == null) {
                    random = new JSONObject();
                    root.put("random", random);
                }
                random.put("getrandomHex", bytesToHex(rnd).toLowerCase(Locale.US));
                random.put("uuid", UUID.randomUUID().toString());
                skip("random.devUrandomHex", "sampled; do not use for HuiYan AES");
            }
        } catch (Exception e) {
            skip("random", e.toString());
        }
        try {
            JSONObject g = new JSONObject();
            g.put("eglVendor", "skip");
            skip("graphics.gl", "no EGL context in collector");
            root.put("graphics", g);
        } catch (Exception ignored) {
        }
    }

    private JSONArray collectProcesses() {
        JSONArray arr = new JSONArray();
        String[] ents = Native.listDir("/proc");
        if (ents == null) {
            skip("linux.processes", "cannot list /proc");
            return arr;
        }
        int n = 0;
        for (int i = 0; i < ents.length && n < 64; i++) {
            if (!ents[i].matches("[0-9]+")) {
                continue;
            }
            String base = "/proc/" + ents[i];
            String comm = readUtf(base + "/comm");
            String cmdline = readUtf(base + "/cmdline");
            JSONObject o = new JSONObject();
            try {
                o.put("pid", Integer.parseInt(ents[i]));
                if (comm != null) {
                    o.put("comm", comm.trim());
                }
                JSONArray cmd = new JSONArray();
                if (cmdline != null && cmdline.length() > 0) {
                    String[] parts = cmdline.split("\0");
                    for (int j = 0; j < parts.length; j++) {
                        if (parts[j].length() > 0) {
                            cmd.put(parts[j]);
                        }
                    }
                }
                o.put("cmdline", cmd);
                arr.put(o);
                n++;
            } catch (Exception ignored) {
            }
        }
        return arr;
    }

    private JSONObject localeJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("languageTag", Locale.getDefault().toLanguageTag());
        o.put("timezoneId", TimeZone.getDefault().getID());
        JSONArray tags = new JSONArray();
        tags.put(Locale.getDefault().toLanguageTag());
        o.put("languageTags", tags);
        return o;
    }

    private JSONObject androidObj() throws Exception {
        JSONObject a = root.optJSONObject("android");
        if (a == null) {
            a = new JSONObject();
            root.put("android", a);
        }
        return a;
    }

    private JSONObject networkObj() throws Exception {
        JSONObject n = root.optJSONObject("network");
        if (n == null) {
            n = new JSONObject();
            root.put("network", n);
        }
        return n;
    }

    private void putCmd(JSONObject commands, String name, String path) {
        String s = readUtf(path);
        if (s == null) {
            skip("linux.commands." + name, "unreadable");
            return;
        }
        try {
            commands.put(name, s.trim());
        } catch (Exception ignored) {
        }
    }

    private void putIf(JSONObject o, String k, String v) throws Exception {
        if (v != null) {
            o.put(k, v.trim());
        }
    }

    private String readUtf(String path) {
        byte[] b = Native.readFile(path);
        if (b == null) {
            return null;
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    private void copyOverlay(String path) {
        byte[] b = Native.readFile(path);
        if (b != null) {
            writeOverlay(path, b);
        }
    }

    private void writeOverlay(String absPath, byte[] data) {
        String rel = absPath.startsWith("/") ? absPath.substring(1) : absPath;
        File f = new File(filesDir, rel);
        File parent = f.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            out.write(data);
            out.close();
        } catch (Exception e) {
            skip("overlay." + absPath, e.toString());
        }
    }

    private JSONObject parseObj(String s) {
        if (s == null || s.length() == 0) {
            return null;
        }
        try {
            return new JSONObject(s);
        } catch (Exception e) {
            skip("json", e.toString());
            return null;
        }
    }

    private void skip(String path, String reason) {
        try {
            JSONObject o = new JSONObject();
            o.put("path", path);
            o.put("reason", reason);
            skipped.put(o);
        } catch (Exception ignored) {
        }
    }

    private String[] assetLines(String name) {
        try {
            InputStream in = ctx.getAssets().open(name);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) > 0) {
                buf.write(tmp, 0, n);
            }
            in.close();
            String s = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            return s.split("\n");
        } catch (Exception e) {
            skip("asset." + name, e.toString());
            return new String[0];
        }
    }

    private static long kb(String mem, String key) {
        int i = mem.indexOf(key);
        if (i < 0) {
            return 0;
        }
        String rest = mem.substring(i + key.length()).trim();
        int sp = rest.indexOf(' ');
        String num = sp < 0 ? rest : rest.substring(0, sp);
        try {
            return Long.parseLong(num);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            sb.append(String.format(Locale.US, "%02X", b[i] & 0xff));
        }
        return sb.toString();
    }

    private static String safeName(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void writeText(File f, String s) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.close();
    }

    private static void wipe(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (int i = 0; i < kids.length; i++) {
            if (kids[i].isDirectory()) {
                wipe(kids[i]);
            }
            kids[i].delete();
        }
    }

    private static void zipDir(File root, File zip) throws Exception {
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip));
        addZip(z, root, root);
        z.close();
    }

    private static void addZip(ZipOutputStream z, File root, File f) throws Exception {
        if (f.equals(new File(root, "envdump.zip"))) {
            return;
        }
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids == null) {
                return;
            }
            for (int i = 0; i < kids.length; i++) {
                addZip(z, root, kids[i]);
            }
            return;
        }
        String rel = root.toURI().relativize(f.toURI()).getPath();
        z.putNextEntry(new ZipEntry(rel));
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            z.write(buf, 0, n);
        }
        in.close();
        z.closeEntry();
    }
}

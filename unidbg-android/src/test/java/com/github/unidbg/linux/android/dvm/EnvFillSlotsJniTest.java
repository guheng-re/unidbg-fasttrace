package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * JNI coverage for Display Point/identity, ringtone settings, cellInfo, scanResults,
 * battery extras, and PowerProfile.averagePower. Drives shipped AbstractJni.
 */
public class EnvFillSlotsJniTest {

    private static final String TELEPHONY_PREFIX =
            "\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[{\"slotIndex\":0}],";

    @Test
    public void testDisplayPointAndIdentity() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"display\":{\"widthPixels\":1080,\"heightPixels\":2400,"
                + "\"uniqueId\":\"local:abc\"}}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass wm = vm.resolveClass("android/view/WindowManager");
            DvmMethod getDefault = new DvmMethod(wm, "getDefaultDisplay",
                    "()Landroid/view/Display;", false);
            DvmObject<?> display = jni.callObjectMethod(baseVM, wm.newObject(null),
                    getDefault.getSignature(), new TestNoArgVarArg(baseVM, getDefault));
            assertNotNull(display);

            DvmObject<?> point = vm.resolveClass("android/graphics/Point").newObject(null);
            DvmMethod getSize = new DvmMethod(vm.resolveClass("android/view/Display"),
                    "getSize", "(Landroid/graphics/Point;)V", false);
            jni.callVoidMethod(baseVM, display, getSize.getSignature(),
                    new TestObjectVarArg(baseVM, getSize, point));
            assertEquals(1080, jni.getIntField(baseVM, point, "android/graphics/Point->x:I"));
            assertEquals(2400, jni.getIntField(baseVM, point, "android/graphics/Point->y:I"));

            DvmMethod getName = new DvmMethod(vm.resolveClass("android/view/Display"),
                    "getName", "()Ljava/lang/String;", false);
            assertEquals("Built-in Screen", ((StringObject) jni.callObjectMethod(baseVM, display,
                    getName.getSignature(), new TestNoArgVarArg(baseVM, getName))).getValue());
            DvmMethod getUnique = new DvmMethod(vm.resolveClass("android/view/Display"),
                    "getUniqueId", "()Ljava/lang/String;", false);
            assertEquals("local:abc", ((StringObject) jni.callObjectMethod(baseVM, display,
                    getUnique.getSignature(), new TestNoArgVarArg(baseVM, getUnique))).getValue());
            DvmMethod getType = new DvmMethod(vm.resolveClass("android/view/Display"),
                    "getType", "()I", false);
            assertEquals(1, jni.callIntMethod(baseVM, display, getType.getSignature(),
                    new TestNoArgVarArg(baseVM, getType)));
            DvmMethod getState = new DvmMethod(vm.resolveClass("android/view/Display"),
                    "getState", "()I", false);
            assertEquals(2, jni.callIntMethod(baseVM, display, getState.getSignature(),
                    new TestNoArgVarArg(baseVM, getState)));
            DvmMethod getFlags = new DvmMethod(vm.resolveClass("android/view/Display"),
                    "getFlags", "()I", false);
            assertEquals(2, jni.callIntMethod(baseVM, display, getFlags.getSignature(),
                    new TestNoArgVarArg(baseVM, getFlags)));
            DvmMethod getDisplayId = new DvmMethod(vm.resolveClass("android/view/Display"),
                    "getDisplayId", "()I", false);
            assertEquals(0, jni.callIntMethod(baseVM, display, getDisplayId.getSignature(),
                    new TestNoArgVarArg(baseVM, getDisplayId)));
            DvmMethod isValid = new DvmMethod(vm.resolveClass("android/view/Display"),
                    "isValid", "()Z", false);
            assertTrue(jni.callBooleanMethod(baseVM, display, isValid.getSignature(),
                    new TestNoArgVarArg(baseVM, isValid)));
            DvmObject<?> realPoint = vm.resolveClass("android/graphics/Point").newObject(null);
            DvmMethod getRealSize = new DvmMethod(vm.resolveClass("android/view/Display"),
                    "getRealSize", "(Landroid/graphics/Point;)V", false);
            jni.callVoidMethod(baseVM, display, getRealSize.getSignature(),
                    new TestObjectVarArg(baseVM, getRealSize, realPoint));
            assertEquals(1080, jni.getIntField(baseVM, realPoint, "android/graphics/Point->x:I"));
            assertEquals(2400, jni.getIntField(baseVM, realPoint, "android/graphics/Point->y:I"));

            DvmClass stub = vm.resolveClass("android/view/IWindowManager$Stub");
            DvmMethod asInterface = new DvmMethod(stub, "asInterface",
                    "(Landroid/os/IBinder;)Landroid/view/IWindowManager;", true);
            DvmObject<?> binder = jni.callStaticObjectMethod(baseVM, stub, asInterface.getSignature(),
                    new TestObjectVarArg(baseVM, asInterface, vm.resolveClass("android/os/IBinder").newObject(null)));
            assertNotNull(binder);
            DvmObject<?> binderPoint = vm.resolveClass("android/graphics/Point").newObject(null);
            DvmMethod initial = new DvmMethod(vm.resolveClass("android/view/IWindowManager"),
                    "getInitialDisplaySize", "(ILandroid/graphics/Point;)V", false);
            jni.callVoidMethod(baseVM, binder, initial.getSignature(),
                    new TestIntObjectVarArg(baseVM, initial, 0, binderPoint));
            assertEquals(1080, jni.getIntField(baseVM, binderPoint, "android/graphics/Point->x:I"));
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testDisplayAbsentDoesNotTakeOver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{\"android\":{}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass stub = vm.resolveClass("android/view/IWindowManager$Stub");
            DvmMethod asInterface = new DvmMethod(stub, "asInterface",
                    "(Landroid/os/IBinder;)Landroid/view/IWindowManager;", true);
            try {
                jni.callStaticObjectMethod(baseVM, stub, asInterface.getSignature(),
                        new TestObjectVarArg(baseVM, asInterface,
                                vm.resolveClass("android/os/IBinder").newObject(null)));
                fail();
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage().contains("asInterface"));
            }
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testRingtoneFromSettingsAndProperties() throws Exception {
        TraceEnvironmentConfig fromSettings = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"settings\":{\"system\":{\"ringtone\":\"FromSettings\"}}}}");
        assertRingtone(fromSettings, "FromSettings");

        TraceEnvironmentConfig fromProp = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"properties\":{\"ro.config.ringtone\":\"FromProp\"}}}");
        assertRingtone(fromProp, "FromProp");

        TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse("{\"android\":{}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(absent).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            BaseVM baseVM = (BaseVM) vm;
            DvmClass mgr = vm.resolveClass("android/media/RingtoneManager");
            DvmMethod getUri = new DvmMethod(mgr, "getActualDefaultRingtoneUri",
                    "(Landroid/content/Context;I)Landroid/net/Uri;", true);
            try {
                jni.callStaticObjectMethod(baseVM, mgr, getUri.getSignature(),
                        new TestObjectIntVarArg(baseVM, getUri, vm.resolveClass("android/content/Context").newObject(null), 1));
                fail();
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage().contains("getActualDefaultRingtoneUri"));
            }
        } finally {
            emulator.close();
        }
    }

    private static void assertRingtone(TraceEnvironmentConfig config, String expected) throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass system = vm.resolveClass("android/provider/Settings$System");
            DvmMethod getString = new DvmMethod(system, "getString",
                    "(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;", true);
            DvmObject<?> value = jni.callStaticObjectMethod(baseVM, system, getString.getSignature(),
                    new TestTwoObjectVarArg(baseVM, getString, null, new StringObject(vm, "ringtone")));
            assertEquals(expected, ((StringObject) value).getValue());

            DvmClass mgr = vm.resolveClass("android/media/RingtoneManager");
            DvmMethod getUri = new DvmMethod(mgr, "getActualDefaultRingtoneUri",
                    "(Landroid/content/Context;I)Landroid/net/Uri;", true);
            DvmObject<?> uri = jni.callStaticObjectMethod(baseVM, mgr, getUri.getSignature(),
                    new TestObjectIntVarArg(baseVM, getUri,
                            vm.resolveClass("android/content/Context").newObject(null), 1));
            assertEquals(expected, String.valueOf(uri.getValue()));
            DvmMethod getRingtone = new DvmMethod(mgr, "getRingtone",
                    "(Landroid/content/Context;Landroid/net/Uri;)Landroid/media/Ringtone;", true);
            DvmObject<?> ringtone = jni.callStaticObjectMethod(baseVM, mgr, getRingtone.getSignature(),
                    new TestTwoObjectVarArg(baseVM, getRingtone,
                            vm.resolveClass("android/content/Context").newObject(null), uri));
            DvmMethod getTitle = new DvmMethod(vm.resolveClass("android/media/Ringtone"),
                    "getTitle", "(Landroid/content/Context;)Ljava/lang/String;", false);
            assertEquals(expected, ((StringObject) jni.callObjectMethod(baseVM, ringtone,
                    getTitle.getSignature(),
                    new TestObjectVarArg(baseVM, getTitle,
                            vm.resolveClass("android/content/Context").newObject(null)))).getValue());
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testCellInfoPresentEmptyAbsent() throws Exception {
        TraceEnvironmentConfig present = TraceEnvironmentConfig.parse("{"
                + TELEPHONY_PREFIX
                + "\"cellInfo\":[{\"type\":\"lte\",\"registered\":true,\"mcc\":\"460\","
                + "\"mnc\":\"11\",\"ci\":99,\"tac\":7}]}}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(present).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass tm = vm.resolveClass("android/telephony/TelephonyManager");
            DvmMethod getAll = new DvmMethod(tm, "getAllCellInfo", "()Ljava/util/List;", false);
            DvmObject<?> list = jni.callObjectMethod(baseVM, tm.newObject(null),
                    getAll.getSignature(), new TestNoArgVarArg(baseVM, getAll));
            assertTrue(list instanceof ArrayListObject);
            assertEquals(1, ((ArrayListObject) list).size());
            DvmObject<?> cell = ((ArrayListObject) list).getValue().get(0);
            DvmMethod isReg = new DvmMethod(vm.resolveClass("android/telephony/CellInfoLte"),
                    "isRegistered", "()Z", false);
            assertTrue(jni.callBooleanMethod(baseVM, cell, isReg.getSignature(),
                    new TestNoArgVarArg(baseVM, isReg)));
            DvmMethod getId = new DvmMethod(vm.resolveClass("android/telephony/CellInfoLte"),
                    "getCellIdentity", "()Landroid/telephony/CellIdentityLte;", false);
            DvmObject<?> identity = jni.callObjectMethod(baseVM, cell, getId.getSignature(),
                    new TestNoArgVarArg(baseVM, getId));
            DvmMethod getCi = new DvmMethod(vm.resolveClass("android/telephony/CellIdentityLte"),
                    "getCi", "()I", false);
            assertEquals(99, jni.callIntMethod(baseVM, identity, getCi.getSignature(),
                    new TestNoArgVarArg(baseVM, getCi)));
            DvmMethod getLoc = new DvmMethod(tm, "getCellLocation",
                    "()Landroid/telephony/CellLocation;", false);
            DvmObject<?> loc = jni.callObjectMethod(baseVM, tm.newObject(null),
                    getLoc.getSignature(), new TestNoArgVarArg(baseVM, getLoc));
            DvmMethod getCid = new DvmMethod(vm.resolveClass("android/telephony/gsm/GsmCellLocation"),
                    "getCid", "()I", false);
            assertEquals(99, jni.callIntMethod(baseVM, loc, getCid.getSignature(),
                    new TestNoArgVarArg(baseVM, getCid)));
        } finally {
            emulator.close();
        }

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse("{"
                + TELEPHONY_PREFIX + "\"cellInfo\":[]}}}");
        AndroidEmulator emptyEmu = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(empty).build();
        try {
            VM vm = emptyEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            BaseVM baseVM = (BaseVM) vm;
            DvmClass tm = vm.resolveClass("android/telephony/TelephonyManager");
            DvmMethod getAll = new DvmMethod(tm, "getAllCellInfo", "()Ljava/util/List;", false);
            ArrayListObject list = (ArrayListObject) jni.callObjectMethod(baseVM, tm.newObject(null),
                    getAll.getSignature(), new TestNoArgVarArg(baseVM, getAll));
            assertTrue(list.isEmpty());
            DvmMethod getLoc = new DvmMethod(tm, "getCellLocation",
                    "()Landroid/telephony/CellLocation;", false);
            assertNull(jni.callObjectMethod(baseVM, tm.newObject(null),
                    getLoc.getSignature(), new TestNoArgVarArg(baseVM, getLoc)));
        } finally {
            emptyEmu.close();
        }

        TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"telephony\":{\"phoneCount\":1,\"slots\":[{\"slotIndex\":0}]}}}");
        AndroidEmulator absentEmu = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(absent).build();
        try {
            VM vm = absentEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            BaseVM baseVM = (BaseVM) vm;
            DvmClass tm = vm.resolveClass("android/telephony/TelephonyManager");
            DvmMethod getAll = new DvmMethod(tm, "getAllCellInfo", "()Ljava/util/List;", false);
            try {
                jni.callObjectMethod(baseVM, tm.newObject(null), getAll.getSignature(),
                        new TestNoArgVarArg(baseVM, getAll));
                fail();
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage().contains("getAllCellInfo"));
            }
        } finally {
            absentEmu.close();
        }
    }

    @Test
    public void testWifiScanResultsPresentEmptyAbsent() throws Exception {
        TraceEnvironmentConfig present = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"wifi\":{\"scanResults\":[{\"ssid\":\"Office\","
                + "\"bssid\":\"02:00:00:00:00:01\",\"rssi\":-40,\"frequencyMhz\":2412}]}}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(present).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass wm = vm.resolveClass("android/net/wifi/WifiManager");
            DvmMethod getScan = new DvmMethod(wm, "getScanResults", "()Ljava/util/List;", false);
            ArrayListObject list = (ArrayListObject) jni.callObjectMethod(baseVM, wm.newObject(null),
                    getScan.getSignature(), new TestNoArgVarArg(baseVM, getScan));
            assertEquals(1, list.size());
            DvmObject<?> row = list.getValue().get(0);
            assertEquals("Office", ((StringObject) jni.getObjectField(baseVM, row,
                    "android/net/wifi/ScanResult->SSID:Ljava/lang/String;")).getValue());
            assertEquals(-40, jni.getIntField(baseVM, row, "android/net/wifi/ScanResult->level:I"));
            assertEquals(2412, jni.getIntField(baseVM, row, "android/net/wifi/ScanResult->frequency:I"));
        } finally {
            emulator.close();
        }

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(
                "{\"network\":{\"wifi\":{\"scanResults\":[]}}}");
        AndroidEmulator emptyEmu = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(empty).build();
        try {
            VM vm = emptyEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            BaseVM baseVM = (BaseVM) vm;
            DvmClass wm = vm.resolveClass("android/net/wifi/WifiManager");
            DvmMethod getScan = new DvmMethod(wm, "getScanResults", "()Ljava/util/List;", false);
            assertTrue(((ArrayListObject) jni.callObjectMethod(baseVM, wm.newObject(null),
                    getScan.getSignature(), new TestNoArgVarArg(baseVM, getScan))).isEmpty());
        } finally {
            emptyEmu.close();
        }

        TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(
                "{\"network\":{\"wifi\":{\"enabled\":true}}}");
        AndroidEmulator absentEmu = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(absent).build();
        try {
            VM vm = absentEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            BaseVM baseVM = (BaseVM) vm;
            DvmClass wm = vm.resolveClass("android/net/wifi/WifiManager");
            DvmMethod getScan = new DvmMethod(wm, "getScanResults", "()Ljava/util/List;", false);
            try {
                jni.callObjectMethod(baseVM, wm.newObject(null), getScan.getSignature(),
                        new TestNoArgVarArg(baseVM, getScan));
                fail();
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage().contains("getScanResults"));
            }
        } finally {
            absentEmu.close();
        }
    }

    @Test
    public void testBatteryExtrasAndPowerProfile() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"battery\":{\"health\":3,\"voltageMv\":4118,"
                + "\"temperatureTenthsC\":284,\"plugged\":2},"
                + "\"powerProfile\":{\"averagePower\":{\"battery.capacity\":4400.5}}}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass ctx = vm.resolveClass("android/content/Context");
            DvmMethod register = new DvmMethod(ctx, "registerReceiver",
                    "(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;",
                    false);
            DvmObject<?> intent = jni.callObjectMethod(baseVM, ctx.newObject(null),
                    register.getSignature(), new TestObjectVarArg(baseVM, register, null));
            assertNotNull(intent);
            DvmClass intentClass = vm.resolveClass("android/content/Intent");
            DvmMethod extra = new DvmMethod(intentClass, "getIntExtra", "(Ljava/lang/String;I)I", false);
            assertEquals(3, jni.callIntMethod(baseVM, intent, extra.getSignature(),
                    new TestObjectIntVarArg(baseVM, extra, new StringObject(vm, "health"), -1)));
            assertEquals(4118, jni.callIntMethod(baseVM, intent, extra.getSignature(),
                    new TestObjectIntVarArg(baseVM, extra, new StringObject(vm, "voltage"), -1)));
            assertEquals(284, jni.callIntMethod(baseVM, intent, extra.getSignature(),
                    new TestObjectIntVarArg(baseVM, extra, new StringObject(vm, "temperature"), -1)));

            DvmClass profile = vm.resolveClass("com/android/internal/os/PowerProfile");
            DvmMethod avg = new DvmMethod(profile, "getAveragePower", "(Ljava/lang/String;)D", false);
            assertEquals(4400.5, jni.callDoubleMethod(baseVM, profile.newObject(null),
                    avg.getSignature(),
                    new TestObjectVarArg(baseVM, avg, new StringObject(vm, "battery.capacity"))), 0.0);
            try {
                jni.callDoubleMethod(baseVM, profile.newObject(null), avg.getSignature(),
                        new TestObjectVarArg(baseVM, avg, new StringObject(vm, "missing.name")));
                fail();
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage().contains("getAveragePower"));
            }
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testExistingAndroidIdAndPackageInfoStillUseSlots() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"identifiers\":{\"androidId\":\"aabbccddeeff0011\"},"
                + "\"packages\":[{\"packageName\":\"com.demo.app\",\"versionName\":\"1.0\","
                + "\"versionCode\":2,\"firstInstallTimeMillis\":10,\"lastUpdateTimeMillis\":20}]}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass secure = vm.resolveClass("android/provider/Settings$Secure");
            DvmMethod getString = new DvmMethod(secure, "getString",
                    "(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;", true);
            DvmObject<?> androidId = jni.callStaticObjectMethod(baseVM, secure, getString.getSignature(),
                    new TestTwoObjectVarArg(baseVM, getString, null, new StringObject(vm, "android_id")));
            assertEquals("aabbccddeeff0011", ((StringObject) androidId).getValue());

            DvmClass pm = vm.resolveClass("android/content/pm/PackageManager");
            DvmMethod getPkg = new DvmMethod(pm, "getPackageInfo",
                    "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;", false);
            DvmObject<?> pkg = jni.callObjectMethod(baseVM, pm.newObject(null), getPkg.getSignature(),
                    new TestObjectIntVarArg(baseVM, getPkg, new StringObject(vm, "com.demo.app"), 0));
            assertEquals("1.0", ((StringObject) jni.getObjectField(baseVM, pkg,
                    "android/content/pm/PackageInfo->versionName:Ljava/lang/String;")).getValue());
            assertEquals(10L, jni.getLongField(baseVM, pkg,
                    "android/content/pm/PackageInfo->firstInstallTime:J"));
        } finally {
            emulator.close();
        }
    }

    private static final class TestNoArgVarArg extends VarArg {
        TestNoArgVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class TestObjectVarArg extends VarArg {
        TestObjectVarArg(BaseVM vm, DvmMethod method, DvmObject<?> object) {
            super(vm, method);
            args.add(Integer.valueOf(object == null ? 0 : object.hashCode()));
            if (object != null) {
                vm.addLocalObject(object);
            }
        }
    }

    private static final class TestTwoObjectVarArg extends VarArg {
        TestTwoObjectVarArg(BaseVM vm, DvmMethod method, DvmObject<?> a, DvmObject<?> b) {
            super(vm, method);
            args.add(Integer.valueOf(a == null ? 0 : a.hashCode()));
            args.add(Integer.valueOf(b == null ? 0 : b.hashCode()));
            if (a != null) {
                vm.addLocalObject(a);
            }
            if (b != null) {
                vm.addLocalObject(b);
            }
        }
    }

    private static final class TestObjectIntVarArg extends VarArg {
        TestObjectIntVarArg(BaseVM vm, DvmMethod method, DvmObject<?> object, int value) {
            super(vm, method);
            args.add(Integer.valueOf(object == null ? 0 : object.hashCode()));
            args.add(Integer.valueOf(value));
            if (object != null) {
                vm.addLocalObject(object);
            }
        }
    }

    private static final class TestIntObjectVarArg extends VarArg {
        TestIntObjectVarArg(BaseVM vm, DvmMethod method, int value, DvmObject<?> object) {
            super(vm, method);
            args.add(Integer.valueOf(value));
            args.add(Integer.valueOf(object == null ? 0 : object.hashCode()));
            if (object != null) {
                vm.addLocalObject(object);
            }
        }
    }
}

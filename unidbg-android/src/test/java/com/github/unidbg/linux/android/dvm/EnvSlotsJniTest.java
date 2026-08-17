package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.linux.android.dvm.array.FloatArray;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EnvSlotsJniTest {

    @Test
    public void testMediaDrmJavaMatchesNativeResolver() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"drm\":{\"deviceUniqueIdHex\":\"0a0b0c0d\"}}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass drmClass = vm.resolveClass("android/media/MediaDrm");
            DvmMethod ctor = new DvmMethod(drmClass, "<init>", "(Ljava/util/UUID;)V", false);
            DvmObject<?> drm = jni.newObject(baseVM, drmClass, ctor.getSignature(),
                    new TestNoArgVarArg(baseVM, ctor));
            assertNotNull(drm);
            DvmMethod get = new DvmMethod(drmClass, "getPropertyByteArray",
                    "(Ljava/lang/String;)[B", false);
            DvmObject<?> bytes = jni.callObjectMethod(baseVM, drm, get.getSignature(),
                    new TestObjectVarArg(baseVM, get, new StringObject(vm, "deviceUniqueId")));
            assertTrue(bytes instanceof ByteArray);
            assertArrayEquals(config.resolveMediaDrmDeviceUniqueId(), ((ByteArray) bytes).getValue());
        } finally {
            emulator.close();
        }
    }

    @Test
    public void testCapabilitiesLocalePluggedDisplaysSettings() throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse("{"
                + "\"network\":{\"capabilities\":{\"transportTypes\":[1],\"networkCapabilities\":[12]}},"
                + "\"android\":{"
                + "\"locale\":{\"languageTags\":[\"zh-Hans-CN\",\"en-US\"]},"
                + "\"battery\":{\"plugged\":0},"
                + "\"displays\":["
                + "{\"id\":0,\"name\":\"a\",\"flags\":2,\"widthPixels\":100,\"heightPixels\":200,\"densityDpi\":160},"
                + "{\"id\":1,\"name\":\"b\",\"flags\":0,\"widthPixels\":10,\"heightPixels\":20,\"densityDpi\":160}"
                + "],"
                + "\"accessibility\":{\"services\":[{\"id\":\"com.demo/.A11y\",\"enabled\":true}]},"
                + "\"sensors\":{\"types\":[1],\"samples\":{\"1\":[0.0,0.0,9.8]}},"
                + "\"location\":{\"enabled\":true,\"lastKnownLocations\":["
                + "{\"provider\":\"gps\",\"latitude\":1.0,\"longitude\":2.0}]}"
                + "}}");
        AndroidEmulator emulator = AndroidEmulatorBuilder.for64Bit().setEnvironmentConfig(config).build();
        try {
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() { };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmClass cm = vm.resolveClass("android/net/ConnectivityManager");
            DvmMethod getCaps = new DvmMethod(cm, "getNetworkCapabilities",
                    "(Landroid/net/Network;)Landroid/net/NetworkCapabilities;", false);
            DvmObject<?> caps = jni.callObjectMethod(baseVM, cm.newObject(null),
                    getCaps.getSignature(), new TestNoArgVarArg(baseVM, getCaps));
            assertNotNull(caps);
            DvmClass nc = vm.resolveClass("android/net/NetworkCapabilities");
            DvmMethod hasTransport = new DvmMethod(nc, "hasTransport", "(I)Z", false);
            assertTrue(jni.callBooleanMethod(baseVM, caps, hasTransport.getSignature(),
                    new TestIntVarArg(baseVM, hasTransport, 1)));
            assertFalse(jni.callBooleanMethod(baseVM, caps, hasTransport.getSignature(),
                    new TestIntVarArg(baseVM, hasTransport, 0)));
            assertEquals(2L, jni.getLongField(baseVM, caps,
                    "android/net/NetworkCapabilities->mTransportTypes:J"));

            DvmClass localeList = vm.resolveClass("android/os/LocaleList");
            DvmMethod getDefault = new DvmMethod(localeList, "getDefault",
                    "()Landroid/os/LocaleList;", true);
            DvmObject<?> list = jni.callStaticObjectMethod(baseVM, localeList,
                    getDefault.getSignature(), new TestNoArgVarArg(baseVM, getDefault));
            DvmMethod size = new DvmMethod(localeList, "size", "()I", false);
            assertEquals(2, jni.callIntMethod(baseVM, list, size.getSignature(),
                    new TestNoArgVarArg(baseVM, size)));

            DvmClass ctx = vm.resolveClass("android/content/Context");
            DvmMethod register = new DvmMethod(ctx, "registerReceiver",
                    "(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;",
                    false);
            DvmObject<?> intent = jni.callObjectMethod(baseVM, ctx.newObject(null),
                    register.getSignature(), new TestObjectVarArg(baseVM, register, null));
            assertNotNull(intent);
            DvmClass intentClass = vm.resolveClass("android/content/Intent");
            DvmMethod extra = new DvmMethod(intentClass, "getIntExtra", "(Ljava/lang/String;I)I", false);
            assertEquals(0, jni.callIntMethod(baseVM, intent, extra.getSignature(),
                    new TestObjectIntVarArg(baseVM, extra, new StringObject(vm, "plugged"), -1)));

            DvmClass dm = vm.resolveClass("android/hardware/display/DisplayManager");
            DvmMethod getDisplays = new DvmMethod(dm, "getDisplays", "()[Landroid/view/Display;", false);
            DvmObject<?> displays = jni.callObjectMethod(baseVM, dm.newObject(null),
                    getDisplays.getSignature(), new TestNoArgVarArg(baseVM, getDisplays));
            assertTrue(displays instanceof ArrayObject);
            assertEquals(2, ((ArrayObject) displays).length());

            DvmClass secure = vm.resolveClass("android/provider/Settings$Secure");
            DvmMethod getString = new DvmMethod(secure, "getString",
                    "(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;", true);
            DvmObject<?> setting = jni.callStaticObjectMethod(baseVM, secure, getString.getSignature(),
                    new TestTwoObjectVarArg(baseVM, getString, null, new StringObject(vm, "enabled_accessibility_services")));
            assertEquals("com.demo/.A11y", ((StringObject) setting).getValue());

            DvmClass sm = vm.resolveClass("android/hardware/SensorManager");
            DvmObject<?> sensorMgr = new com.github.unidbg.linux.android.dvm.api.SystemService(vm, "sensor");
            DvmMethod getDefaultSensor = new DvmMethod(sm, "getDefaultSensor",
                    "(I)Landroid/hardware/Sensor;", false);
            DvmObject<?> sensor = jni.callObjectMethod(baseVM, sensorMgr, getDefaultSensor.getSignature(),
                    new TestIntVarArg(baseVM, getDefaultSensor, 1));
            assertNotNull(sensor);
            List<DvmObject<?>> sensorInbox = new ArrayList<DvmObject<?>>();
            DvmObject<?> sensorListener = vm.resolveClass("android/hardware/SensorEventListener")
                    .newObject(sensorInbox);
            DvmMethod registerListener = new DvmMethod(sm, "registerListener",
                    "(Landroid/hardware/SensorEventListener;Landroid/hardware/Sensor;I)Z", false);
            assertTrue(jni.callBooleanMethod(baseVM, sensorMgr, registerListener.getSignature(),
                    new TestListenerSensorIntVarArg(baseVM, registerListener, sensorListener, sensor, 0)));
            assertEquals(1, sensorInbox.size());
            DvmObject<?> sensorEvent = sensorInbox.get(0);
            DvmObject<?> valuesObj = jni.getObjectField(baseVM, sensorEvent,
                    "android/hardware/SensorEvent->values:[F");
            assertTrue(valuesObj instanceof FloatArray);
            assertArrayEquals(new float[]{0.0f, 0.0f, 9.8f}, ((FloatArray) valuesObj).getValue(), 0.001f);

            DvmClass lm = vm.resolveClass("android/location/LocationManager");
            DvmMethod request = new DvmMethod(lm, "requestLocationUpdates",
                    "(Ljava/lang/String;JFLandroid/location/LocationListener;)V", false);
            DvmObject<?> locMgr = new com.github.unidbg.linux.android.dvm.api.SystemService(vm, "location");
            List<DvmObject<?>> locationInbox = new ArrayList<DvmObject<?>>();
            DvmObject<?> locationListener = vm.resolveClass("android/location/LocationListener")
                    .newObject(locationInbox);
            jni.callVoidMethod(baseVM, locMgr, request.getSignature(),
                    new TestLocationUpdatesVarArg(baseVM, request,
                            new StringObject(vm, "gps"), 0L, 0f, locationListener));
            assertEquals(1, locationInbox.size());
            DvmObject<?> location = locationInbox.get(0);
            assertEquals(1.0d, jni.callDoubleMethod(baseVM, location,
                    "android/location/Location->getLatitude()D",
                    new TestNoArgVarArg(baseVM, new DvmMethod(
                            vm.resolveClass("android/location/Location"),
                            "getLatitude", "()D", false))), 0.0001d);

            locationInbox.clear();
            jni.callVoidMethodV(baseVM, locMgr, request.getSignature(),
                    new TestLocationUpdatesVaList(baseVM, request,
                            new StringObject(vm, "gps"), 0L, 0f, locationListener));
            assertEquals(1, locationInbox.size());
            assertEquals(2.0d, jni.callDoubleMethod(baseVM, locationInbox.get(0),
                    "android/location/Location->getLongitude()D",
                    new TestNoArgVarArg(baseVM, new DvmMethod(
                            vm.resolveClass("android/location/Location"),
                            "getLongitude", "()D", false))), 0.0001d);
        } finally {
            emulator.close();
        }
    }

    private static final class TestNoArgVarArg extends VarArg {
        TestNoArgVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class TestIntVarArg extends VarArg {
        TestIntVarArg(BaseVM vm, DvmMethod method, int value) {
            super(vm, method);
            args.add(value);
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
            args.add(value);
            if (object != null) {
                vm.addLocalObject(object);
            }
        }
    }

    private static final class TestListenerSensorIntVarArg extends VarArg {
        TestListenerSensorIntVarArg(BaseVM vm, DvmMethod method, DvmObject<?> listener,
                                    DvmObject<?> sensor, int period) {
            super(vm, method);
            args.add(Integer.valueOf(listener == null ? 0 : listener.hashCode()));
            args.add(Integer.valueOf(sensor == null ? 0 : sensor.hashCode()));
            args.add(period);
            if (listener != null) {
                vm.addLocalObject(listener);
            }
            if (sensor != null) {
                vm.addLocalObject(sensor);
            }
        }
    }

    private static void addLocationUpdateArgs(BaseVM vm, List<Object> args, DvmObject<?> provider,
                                              long minTime, float minDistance, DvmObject<?> listener) {
        args.add(Integer.valueOf(provider == null ? 0 : provider.hashCode()));
        args.add(Long.valueOf(minTime));
        args.add(Float.valueOf(minDistance));
        args.add(Integer.valueOf(listener == null ? 0 : listener.hashCode()));
        if (provider != null) {
            vm.addLocalObject(provider);
        }
        if (listener != null) {
            vm.addLocalObject(listener);
        }
    }

    private static final class TestLocationUpdatesVarArg extends VarArg {
        TestLocationUpdatesVarArg(BaseVM vm, DvmMethod method, DvmObject<?> provider,
                                  long minTime, float minDistance, DvmObject<?> listener) {
            super(vm, method);
            addLocationUpdateArgs(vm, args, provider, minTime, minDistance, listener);
        }
    }

    private static final class TestLocationUpdatesVaList extends VaList {
        TestLocationUpdatesVaList(BaseVM vm, DvmMethod method, DvmObject<?> provider,
                                  long minTime, float minDistance, DvmObject<?> listener) {
            super(vm, method);
            addLocationUpdateArgs(vm, args, provider, minTime, minDistance, listener);
        }
    }
}

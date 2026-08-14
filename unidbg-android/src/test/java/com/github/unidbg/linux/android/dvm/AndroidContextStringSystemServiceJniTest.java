package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.api.SystemService;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 覆盖裸 {@code Context.getSystemService(String)}：与既有
 * {@code Application.getSystemService(String)} 同样返回 {@code SystemService}
 * marker；lookup 不读环境、不发 sidecar。{@code "user"} 走 {@code USER_SERVICE}
 * 映射为 {@code UserManager}；{@code "wifi"} 走既有 {@code WifiManager} 类型。
 */
public class AndroidContextStringSystemServiceJniTest {

    private static final String USER_MANAGER_CLASS = "android/os/UserManager";
    private static final String WIFI_MANAGER_CLASS = "android/net/wifi/WifiManager";

    private static final String MINIMAL_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testContextStringGetSystemServiceVarArg32() throws Exception {
        runContextStringGetSystemService(false, false);
    }

    @Test
    public void testContextStringGetSystemServiceVaList64() throws Exception {
        runContextStringGetSystemService(true, true);
    }

    private static void runContextStringGetSystemService(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MINIMAL_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);

            int eventsBeforeUser = sink.events.size();
            DvmObject<?> userService = invokeGetSystemServiceString(jni, baseVM, useVaList, context,
                    SystemService.USER_SERVICE);
            assertUserManagerMarker(userService);
            assertNoEnvironmentSidecarSince(sink, eventsBeforeUser);

            int eventsBeforeWifi = sink.events.size();
            DvmObject<?> wifiService = invokeGetSystemServiceString(jni, baseVM, useVaList, context,
                    SystemService.WIFI_SERVICE);
            assertWifiManagerMarker(wifiService);
            assertNoEnvironmentSidecarSince(sink, eventsBeforeWifi);

            int eventsBeforeNonString = sink.events.size();
            try {
                invokeGetSystemServiceNonString(jni, baseVM, useVaList, context);
                fail("expected UOE for Context.getSystemService with non-String argument");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSystemService"));
            }
            assertNoEnvironmentSidecarSince(sink, eventsBeforeNonString);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertUserManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals(USER_MANAGER_CLASS, manager.getObjectType().getClassName());
        assertEquals(SystemService.USER_SERVICE, manager.getValue());
        assertEquals("user", manager.getValue());
    }

    private static void assertWifiManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals(WIFI_MANAGER_CLASS, manager.getObjectType().getClassName());
        assertEquals(SystemService.WIFI_SERVICE, manager.getValue());
        assertEquals("wifi", manager.getValue());
    }

    private static void assertNoEnvironmentSidecarSince(CapturingSink sink, int fromIndex) {
        assertEquals("lookup must not emit environment sidecar", fromIndex, sink.events.size());
    }

    private static DvmObject<?> invokeGetSystemServiceString(AbstractJni jni, BaseVM vm,
                                                             boolean useVaList,
                                                             DvmObject<?> receiver,
                                                             String serviceName) {
        int nameHash = vm.addLocalObject(new StringObject(vm, serviceName));
        DvmClass dvmClass = receiver.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/String;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature,
                    new TestVaList(vm, method, nameHash));
        }
        return jni.callObjectMethod(vm, receiver, signature,
                new TestVarArg(vm, method, nameHash));
    }

    private static DvmObject<?> invokeGetSystemServiceNonString(AbstractJni jni, BaseVM vm,
                                                                boolean useVaList,
                                                                DvmObject<?> receiver) {
        DvmObject<?> notString = vm.resolveClass("java/lang/Integer").newObject(1);
        int argHash = vm.addLocalObject(notString);
        DvmClass dvmClass = receiver.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/Integer;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature,
                    new TestVaList(vm, method, argHash));
        }
        return jni.callObjectMethod(vm, receiver, signature,
                new TestVarArg(vm, method, argHash));
    }

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }
    }

    private static final class CapturedEvent {
        final String kind;
        final String api;

        CapturedEvent(String kind, String api) {
            this.kind = kind;
            this.api = api;
        }
    }

    private static final class CapturingSink implements TraceEnvironmentEventSink {
        final List<CapturedEvent> events = new ArrayList<CapturedEvent>();

        @Override
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api));
        }
    }
}

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 覆盖类型化 {@code Application}/{@code Context.getSystemService(ClipboardManager.class)}：
 * 返回既有 {@code SystemService} clipboard 标记；lookup 不发 sidecar；
 * {@code hasPrimaryClip}/{@code getPrimaryClip} 与 ClipData/ClipDescription 读取仍由
 * {@code android.clipboard} 门控。
 */
public class AndroidClipboardTypedServiceJniTest {

    private static final String CLIPBOARD_MANAGER_CLASS = "android/content/ClipboardManager";
    private static final String CLIP_DATA_CLASS = "android/content/ClipData";
    private static final String CLIP_ITEM_CLASS = "android/content/ClipData$Item";
    private static final String CLIP_DESCRIPTION_CLASS = "android/content/ClipDescription";
    private static final String PRIMARY_TEXT = "TRACEAI_CLIP";
    private static final String PRIMARY_LABEL = "TRACEAI_LABEL";
    private static final long TIMESTAMP_MILLIS = 1710000000000L;
    private static final String MIMETYPE_TEXT_PLAIN = "text/plain";

    private static final String CLIPBOARD_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"clipboard\":{"
            + "\"hasPrimaryClip\":true,"
            + "\"primaryText\":\"" + PRIMARY_TEXT + "\","
            + "\"primaryLabel\":\"" + PRIMARY_LABEL + "\","
            + "\"timestampMillis\":" + TIMESTAMP_MILLIS
            + "}"
            + "}"
            + "}";

    private static final String NO_CLIPBOARD_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testClipboardTypedGetSystemServiceVarArg32() throws Exception {
        runTypedGetSystemService(false, false);
    }

    @Test
    public void testClipboardTypedGetSystemServiceVaList64() throws Exception {
        runTypedGetSystemService(true, true);
    }

    private static void runTypedGetSystemService(boolean is64Bit, boolean useVaList) throws Exception {
        runTypedConfigured(is64Bit, useVaList);
        runTypedAbsent(is64Bit, useVaList);
    }

    private static void runTypedConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_JSON);
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
            DvmClass clipboardClass = vm.resolveClass(CLIPBOARD_MANAGER_CLASS);

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            int eventsBeforeApp = sink.events.size();
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, clipboardClass);
            assertClipboardManagerMarker(fromApp);
            assertNoAndroidClipboardSince(sink, eventsBeforeApp);
            assertConfiguredClipboardFromMarker(jni, baseVM, useVaList, fromApp, sink);

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            int eventsBeforeCtx = sink.events.size();
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, clipboardClass);
            assertClipboardManagerMarker(fromCtx);
            assertNoAndroidClipboardSince(sink, eventsBeforeCtx);
            assertConfiguredClipboardFromMarker(jni, baseVM, useVaList, fromCtx, sink);

            assertEquals(2, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.hasPrimaryClip"));
            assertEquals(2, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runTypedAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_CLIPBOARD_JSON);
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
            DvmClass clipboardClass = vm.resolveClass(CLIPBOARD_MANAGER_CLASS);

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, clipboardClass);
            assertClipboardManagerMarker(fromApp);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, clipboardClass);
            assertClipboardManagerMarker(fromCtx);
            assertClipboardReadersUnsupported(jni, baseVM, useVaList, fromApp);
            assertClipboardReadersUnsupported(jni, baseVM, useVaList, fromCtx);
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_clipboard sidecar: " + e.api,
                        "android_clipboard".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertClipboardManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals(CLIPBOARD_MANAGER_CLASS, manager.getObjectType().getClassName());
        assertEquals(SystemService.CLIPBOARD_SERVICE, manager.getValue());
    }

    private static void assertNoAndroidClipboardSince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            assertFalse("unexpected android_clipboard sidecar on typed lookup: "
                            + sink.events.get(i).api,
                    "android_clipboard".equals(sink.events.get(i).kind));
        }
    }

    private static void assertConfiguredClipboardFromMarker(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                            DvmObject<?> manager, CapturingSink sink) {
        assertTrue(invokeHasPrimaryClip(jni, vm, useVaList, manager));
        CapturedEvent hasClip = findLastEvent(sink.events, "android_clipboard",
                "ClipboardManager.hasPrimaryClip");
        assertNotNull(hasClip);
        assertEquals("json-config", hasClip.source);
        assertEquals("field=hasPrimaryClip,result=true", String.valueOf(hasClip.value));
        assertNotNull(hasClip.note);
        assertFalse(hasClip.note.isEmpty());

        DvmObject<?> clipData = invokeGetPrimaryClip(jni, vm, useVaList, manager);
        assertNotNull(clipData);
        assertEquals(CLIP_DATA_CLASS, clipData.getObjectType().getClassName());
        assertTrue(clipData.getValue().getClass().getName().contains("ConfiguredClipData"));

        CapturedEvent getClip = findLastEvent(sink.events, "android_clipboard",
                "ClipboardManager.getPrimaryClip");
        assertNotNull(getClip);
        assertEquals("json-config", getClip.source);
        assertEquals("hasPrimaryClip=true,textLength=" + PRIMARY_TEXT.length(),
                String.valueOf(getClip.value));
        assertFalse(String.valueOf(getClip.value).contains(PRIMARY_TEXT));
        assertNotNull(getClip.note);
        assertFalse(getClip.note.isEmpty());

        int sidecarAfterClip = countEvents(sink.events, "android_clipboard",
                "ClipboardManager.getPrimaryClip");
        assertEquals(1, invokeGetItemCount(jni, vm, useVaList, clipData));
        DvmObject<?> item = invokeGetItemAt(jni, vm, useVaList, clipData, 0);
        assertNotNull(item);
        assertEquals(CLIP_ITEM_CLASS, item.getObjectType().getClassName());
        assertTrue(item.getValue().getClass().getName().contains("ConfiguredClipDataItem"));
        assertEquals(PRIMARY_TEXT, invokeGetText(jni, vm, useVaList, item));

        DvmObject<?> description = invokeGetDescription(jni, vm, useVaList, clipData);
        assertNotNull(description);
        assertEquals(CLIP_DESCRIPTION_CLASS, description.getObjectType().getClassName());
        assertTrue(description.getValue().getClass().getName().contains("ConfiguredClipDescription"));
        assertEquals(1, invokeGetMimeTypeCount(jni, vm, useVaList, description));
        DvmObject<?> mime0 = invokeGetMimeType(jni, vm, useVaList, description, 0);
        assertTrue(mime0 instanceof StringObject);
        assertEquals(MIMETYPE_TEXT_PLAIN, ((StringObject) mime0).getValue());
        DvmObject<?> label = invokeGetLabel(jni, vm, useVaList, description);
        assertTrue(label instanceof StringObject);
        assertEquals(PRIMARY_LABEL, ((StringObject) label).getValue());
        assertEquals(TIMESTAMP_MILLIS, invokeGetTimestamp(jni, vm, useVaList, description));

        assertEquals(sidecarAfterClip, countEvents(sink.events, "android_clipboard",
                "ClipboardManager.getPrimaryClip"));
        for (CapturedEvent e : sink.events) {
            if ("android_clipboard".equals(e.kind)
                    && !"ClipboardManager.hasPrimaryClip".equals(e.api)
                    && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                fail("unexpected clipboard event api=" + e.api);
            }
        }
    }

    private static void assertClipboardReadersUnsupported(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                          DvmObject<?> manager) {
        try {
            invokeHasPrimaryClip(jni, vm, useVaList, manager);
            fail("expected UOE for hasPrimaryClip without android.clipboard");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("hasPrimaryClip"));
        }
        try {
            invokeGetPrimaryClip(jni, vm, useVaList, manager);
            fail("expected UOE for getPrimaryClip without android.clipboard");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("getPrimaryClip"));
        }
    }

    private static DvmObject<?> invokeGetSystemServiceClass(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                            DvmObject<?> receiver, DvmClass serviceClass) {
        int classHash = vm.addLocalObject(serviceClass);
        DvmClass dvmClass = receiver.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature,
                    new TestVaList(vm, method, classHash));
        }
        return jni.callObjectMethod(vm, receiver, signature,
                new TestVarArg(vm, method, classHash));
    }

    private static boolean invokeHasPrimaryClip(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(CLIPBOARD_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasPrimaryClip", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetPrimaryClip(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                     DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(CLIPBOARD_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getPrimaryClip",
                "()Landroid/content/ClipData;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, target, signature, new TestVarArg(vm, method));
    }

    private static int invokeGetItemCount(AbstractJni jni, BaseVM vm, boolean useVaList,
                                          DvmObject<?> clipData) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DATA_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getItemCount", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, clipData, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, clipData, signature, new TestVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetItemAt(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmObject<?> clipData, int index) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DATA_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getItemAt",
                "(I)Landroid/content/ClipData$Item;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, clipData, signature, new TestIntVaList(vm, method, index));
        }
        return jni.callObjectMethod(vm, clipData, signature, new TestIntVarArg(vm, method, index));
    }

    private static String invokeGetText(AbstractJni jni, BaseVM vm, boolean useVaList,
                                        DvmObject<?> item) {
        DvmClass dvmClass = vm.resolveClass(CLIP_ITEM_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getText", "()Ljava/lang/CharSequence;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, item, signature, new TestVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, item, signature, new TestVarArg(vm, method));
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static DvmObject<?> invokeGetDescription(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                     DvmObject<?> clipData) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DATA_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getDescription",
                "()Landroid/content/ClipDescription;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, clipData, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, clipData, signature, new TestVarArg(vm, method));
    }

    private static int invokeGetMimeTypeCount(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> description) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getMimeTypeCount", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, description, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, description, signature, new TestVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetMimeType(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> description, int index) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getMimeType", "(I)Ljava/lang/String;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, description, signature, new TestIntVaList(vm, method, index));
        }
        return jni.callObjectMethod(vm, description, signature, new TestIntVarArg(vm, method, index));
    }

    private static DvmObject<?> invokeGetLabel(AbstractJni jni, BaseVM vm, boolean useVaList,
                                               DvmObject<?> description) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getLabel",
                "()Ljava/lang/CharSequence;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, description, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, description, signature, new TestVarArg(vm, method));
    }

    private static long invokeGetTimestamp(AbstractJni jni, BaseVM vm, boolean useVaList,
                                           DvmObject<?> description) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getTimestamp", "()J", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callLongMethodV(vm, description, signature, new TestVaList(vm, method));
        }
        return jni.callLongMethod(vm, description, signature, new TestVarArg(vm, method));
    }

    private static CapturedEvent findLastEvent(List<CapturedEvent> events, String kind, String api) {
        CapturedEvent found = null;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)) {
                found = e;
            }
        }
        return found;
    }

    private static int countEvents(List<CapturedEvent> events, String kind, String api) {
        int n = 0;
        for (CapturedEvent e : events) {
            if (kind.equals(e.kind) && api.equals(e.api)) {
                n++;
            }
        }
        return n;
    }

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVaList(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }
    }

    private static final class TestIntVarArg extends VarArg {
        TestIntVarArg(BaseVM vm, DvmMethod method, int intArg0) {
            super(vm, method);
            args.add(intArg0);
        }
    }

    private static final class TestIntVaList extends VaList {
        TestIntVaList(BaseVM vm, DvmMethod method, int intArg0) {
            super(vm, method);
            args.add(intArg0);
        }
    }

    private static final class CapturedEvent {
        final String kind;
        final String api;
        final Object value;
        final String source;
        final String note;

        CapturedEvent(String kind, String api, Object value, String source, String note) {
            this.kind = kind;
            this.api = api;
            this.value = value;
            this.source = source;
            this.note = note;
        }
    }

    private static final class CapturingSink implements TraceEnvironmentEventSink {
        final List<CapturedEvent> events = new ArrayList<CapturedEvent>();

        @Override
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source, note));
        }
    }
}

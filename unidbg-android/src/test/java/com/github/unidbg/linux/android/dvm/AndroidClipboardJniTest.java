package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.api.SystemService;
import com.github.unidbg.linux.android.dvm.wrapper.DvmInteger;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Coverage for {@code android.clipboard} + {@code ClipboardManager.hasPrimaryClip()Z},
 * optional primary text ClipData path ({@code Item.getText()} and
 * {@code Item.coerceToText(Context)}; Context unused on the plain-text subset),
 * plain-text ClipDescription metadata including exact {@code hasMimeType(String)},
 * optional {@code getLabel()}, optional {@code getTimestamp()J}, and
 * {@code isStyledText()Z} (false on the plain-text marker only; SystemService
 * clipboard marker; VarArg + VaList).
 */
public class AndroidClipboardJniTest {

    private static final String CLIPBOARD_MANAGER_CLASS = "android/content/ClipboardManager";
    private static final String CLIP_DATA_CLASS = "android/content/ClipData";
    private static final String CLIP_ITEM_CLASS = "android/content/ClipData$Item";
    private static final String CLIP_DESCRIPTION_CLASS = "android/content/ClipDescription";
    private static final String MIMETYPE_TEXT_PLAIN = "text/plain";
    private static final String MIMETYPE_TEXT_WILDCARD = "text/*";
    private static final String MIMETYPE_ANY = "*/*";

    private static final String CLIPBOARD_TRUE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"clipboard\":{\"hasPrimaryClip\":true}"
            + "}"
            + "}";

    private static final String CLIPBOARD_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"clipboard\":{}"
            + "}"
            + "}";

    private static final String CLIPBOARD_TEXT_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"clipboard\":{"
            + "\"hasPrimaryClip\":true,"
            + "\"primaryText\":\"TRACEAI_CLIP\""
            + "}"
            + "}"
            + "}";

    private static final String CLIPBOARD_EMPTY_TEXT_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"clipboard\":{"
            + "\"hasPrimaryClip\":true,"
            + "\"primaryText\":\"\""
            + "}"
            + "}"
            + "}";

    private static final String CLIPBOARD_LABEL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"clipboard\":{"
            + "\"hasPrimaryClip\":true,"
            + "\"primaryText\":\"TRACEAI_CLIP\","
            + "\"primaryLabel\":\"TRACEAI_LABEL\""
            + "}"
            + "}"
            + "}";

    private static final String CLIPBOARD_EMPTY_LABEL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"clipboard\":{"
            + "\"hasPrimaryClip\":true,"
            + "\"primaryText\":\"TRACEAI_CLIP\","
            + "\"primaryLabel\":\"\""
            + "}"
            + "}"
            + "}";

    private static final String CLIPBOARD_TIMESTAMP_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"clipboard\":{"
            + "\"hasPrimaryClip\":true,"
            + "\"primaryText\":\"TRACEAI_CLIP\","
            + "\"timestampMillis\":1710000000000"
            + "}"
            + "}"
            + "}";

    private static final String CLIPBOARD_TIMESTAMP_MAX_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"clipboard\":{"
            + "\"hasPrimaryClip\":true,"
            + "\"primaryText\":\"TRACEAI_CLIP\","
            + "\"timestampMillis\":" + Long.MAX_VALUE
            + "}"
            + "}"
            + "}";

    private static final String NO_CLIPBOARD_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testHasPrimaryClipTrueVarArg32() throws Exception {
        runConfiguredHasPrimaryClip(false, false, CLIPBOARD_TRUE_JSON, true);
    }

    @Test
    public void testHasPrimaryClipTrueVaList64() throws Exception {
        runConfiguredHasPrimaryClip(true, true, CLIPBOARD_TRUE_JSON, true);
    }

    @Test
    public void testHasPrimaryClipDefaultFalseVarArg32() throws Exception {
        runConfiguredHasPrimaryClip(false, false, CLIPBOARD_EMPTY_JSON, false);
    }

    @Test
    public void testHasPrimaryClipDefaultFalseVaList64() throws Exception {
        runConfiguredHasPrimaryClip(true, true, CLIPBOARD_EMPTY_JSON, false);
    }

    @Test
    public void testHasPrimaryClipAbsentVarArg32() throws Exception {
        runAbsentHasPrimaryClip(false, false);
    }

    @Test
    public void testHasPrimaryClipAbsentVaList64() throws Exception {
        runAbsentHasPrimaryClip(true, true);
    }

    @Test
    public void testPlainAndOtherSystemServiceIsolationVarArg32() throws Exception {
        runPlainAndOtherIsolation(false, false);
    }

    @Test
    public void testPlainAndOtherSystemServiceIsolationVaList64() throws Exception {
        runPlainAndOtherIsolation(true, true);
    }

    @Test
    public void testClipboardServiceNameAndValueVarArg32() throws Exception {
        runServiceNameAndValue(false, false);
    }

    @Test
    public void testClipboardServiceNameAndValueVaList64() throws Exception {
        runServiceNameAndValue(true, true);
    }

    @Test
    public void testPrimaryTextClipDataVarArg32() throws Exception {
        runPrimaryTextClipData(false, false);
    }

    @Test
    public void testPrimaryTextClipDataVaList64() throws Exception {
        runPrimaryTextClipData(true, true);
    }

    @Test
    public void testPrimaryTextClipDataEmptyAndNegativeVarArg32() throws Exception {
        runPrimaryTextEmptyAndNegative(false, false);
    }

    @Test
    public void testPrimaryTextClipDataEmptyAndNegativeVaList64() throws Exception {
        runPrimaryTextEmptyAndNegative(true, true);
    }

    @Test
    public void testClipDescriptionMetadataVarArg32() throws Exception {
        runClipDescriptionMetadata(false, false);
    }

    @Test
    public void testClipDescriptionMetadataVaList64() throws Exception {
        runClipDescriptionMetadata(true, true);
    }

    @Test
    public void testClipDescriptionMetadataNegativeVarArg32() throws Exception {
        runClipDescriptionMetadataNegative(false, false);
    }

    @Test
    public void testClipDescriptionMetadataNegativeVaList64() throws Exception {
        runClipDescriptionMetadataNegative(true, true);
    }

    @Test
    public void testHasMimeTypeVarArg32() throws Exception {
        runHasMimeType(false, false);
    }

    @Test
    public void testHasMimeTypeVaList64() throws Exception {
        runHasMimeType(true, true);
    }

    @Test
    public void testHasMimeTypeNegativeVarArg32() throws Exception {
        runHasMimeTypeNegative(false, false);
    }

    @Test
    public void testHasMimeTypeNegativeVaList64() throws Exception {
        runHasMimeTypeNegative(true, true);
    }

    @Test
    public void testClipDescriptionGetLabelVarArg32() throws Exception {
        runClipDescriptionGetLabel(false, false);
    }

    @Test
    public void testClipDescriptionGetLabelVaList64() throws Exception {
        runClipDescriptionGetLabel(true, true);
    }

    @Test
    public void testClipDescriptionGetLabelNegativeVarArg32() throws Exception {
        runClipDescriptionGetLabelNegative(false, false);
    }

    @Test
    public void testClipDescriptionGetLabelNegativeVaList64() throws Exception {
        runClipDescriptionGetLabelNegative(true, true);
    }

    @Test
    public void testClipDescriptionGetTimestampVarArg32() throws Exception {
        runClipDescriptionGetTimestamp(false, false);
    }

    @Test
    public void testClipDescriptionGetTimestampVaList64() throws Exception {
        runClipDescriptionGetTimestamp(true, true);
    }

    @Test
    public void testClipDescriptionGetTimestampNegativeVarArg32() throws Exception {
        runClipDescriptionGetTimestampNegative(false, false);
    }

    @Test
    public void testClipDescriptionGetTimestampNegativeVaList64() throws Exception {
        runClipDescriptionGetTimestampNegative(true, true);
    }

    @Test
    public void testClipDescriptionIsStyledTextVarArg32() throws Exception {
        runClipDescriptionIsStyledText(false, false);
    }

    @Test
    public void testClipDescriptionIsStyledTextVaList64() throws Exception {
        runClipDescriptionIsStyledText(true, true);
    }

    @Test
    public void testClipDescriptionIsStyledTextNegativeVarArg32() throws Exception {
        runClipDescriptionIsStyledTextNegative(false, false);
    }

    @Test
    public void testClipDescriptionIsStyledTextNegativeVaList64() throws Exception {
        runClipDescriptionIsStyledTextNegative(true, true);
    }

    private static void runConfiguredHasPrimaryClip(boolean is64Bit, boolean useVaList,
                                                    String json, boolean expected) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
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

            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            assertEquals(expected, invokeHasPrimaryClip(jni, baseVM, useVaList, manager));

            CapturedEvent ev = findLastEvent(sink.events, "android_clipboard",
                    "ClipboardManager.hasPrimaryClip");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=hasPrimaryClip,result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.hasPrimaryClip"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentHasPrimaryClip(boolean is64Bit, boolean useVaList) throws Exception {
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

            // getSystemService still yields clipboard marker without android.clipboard
            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            try {
                invokeHasPrimaryClip(jni, baseVM, useVaList, manager);
                fail("expected UnsupportedOperationException without clipboard config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasPrimaryClip"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_clipboard event when config absent: " + e.api,
                        "android_clipboard".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runPlainAndOtherIsolation(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_TRUE_JSON);
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

            // plain ClipboardManager (not SystemService marker) → UOE, no event
            DvmObject<?> plain = vm.resolveClass(CLIPBOARD_MANAGER_CLASS).newObject(null);
            try {
                invokeHasPrimaryClip(jni, baseVM, useVaList, plain);
                fail("expected UOE for hasPrimaryClip on plain ClipboardManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasPrimaryClip"));
            }

            // other SystemService (wifi) must not hit clipboard path even if signature forced
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> wifi = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            assertTrue(wifi instanceof SystemService);
            try {
                invokeHasPrimaryClip(jni, baseVM, useVaList, wifi);
                fail("expected UOE for hasPrimaryClip on non-clipboard SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasPrimaryClip"));
            }

            // wrong signature on clipboard marker → UOE, no event
            DvmObject<?> clipboard = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            try {
                DvmClass dvmClass = clipboard.getObjectType();
                DvmMethod method = new DvmMethod(dvmClass, "getPrimaryClip",
                        "()Landroid/content/ClipData;", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, clipboard, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callObjectMethod(baseVM, clipboard, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for getPrimaryClip");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPrimaryClip"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_clipboard event on isolation paths: " + e.api,
                        "android_clipboard".equals(e.kind));
            }

            // control: real clipboard SystemService still works after isolation checks
            assertTrue(invokeHasPrimaryClip(jni, baseVM, useVaList, clipboard));
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.hasPrimaryClip"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runServiceNameAndValue(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_TRUE_JSON);
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

            DvmClass contextClass = vm.resolveClass("android/content/Context");
            DvmObject<?> serviceName = jni.getStaticObjectField(baseVM, contextClass,
                    "android/content/Context->CLIPBOARD_SERVICE:Ljava/lang/String;");
            assertTrue(serviceName instanceof StringObject);
            assertEquals(SystemService.CLIPBOARD_SERVICE, ((StringObject) serviceName).getValue());
            assertEquals("clipboard", ((StringObject) serviceName).getValue());

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> manager = invokeGetSystemService(jni, baseVM, useVaList, app,
                    ((StringObject) serviceName).getValue());
            assertNotNull(manager);
            assertTrue(manager instanceof SystemService);
            assertEquals(CLIPBOARD_MANAGER_CLASS, manager.getObjectType().getClassName());
            assertEquals(SystemService.CLIPBOARD_SERVICE, manager.getValue());

            assertTrue(invokeHasPrimaryClip(jni, baseVM, useVaList, manager));
            CapturedEvent ev = findLastEvent(sink.events, "android_clipboard",
                    "ClipboardManager.hasPrimaryClip");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=hasPrimaryClip,result=true", String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runPrimaryTextClipData(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
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

            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            assertTrue(invokeHasPrimaryClip(jni, baseVM, useVaList, manager));

            DvmObject<?> clipData = invokeGetPrimaryClip(jni, baseVM, useVaList, manager);
            assertNotNull(clipData);
            assertEquals(CLIP_DATA_CLASS, clipData.getObjectType().getClassName());
            assertTrue(clipData.getValue().getClass().getName().contains("ConfiguredClipData"));

            CapturedEvent getClipEv = findLastEvent(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip");
            assertNotNull(getClipEv);
            assertEquals("json-config", getClipEv.source);
            assertEquals("hasPrimaryClip=true,textLength=12", String.valueOf(getClipEv.value));
            assertFalse(String.valueOf(getClipEv.value).contains("TRACEAI_CLIP"));
            assertNotNull(getClipEv.note);
            assertFalse(getClipEv.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            assertEquals(1, invokeGetItemCount(jni, baseVM, useVaList, clipData));
            // getItemCount emits no event
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            DvmObject<?> item = invokeGetItemAt(jni, baseVM, useVaList, clipData, 0);
            assertNotNull(item);
            assertEquals(CLIP_ITEM_CLASS, item.getObjectType().getClassName());
            assertTrue(item.getValue().getClass().getName().contains("ConfiguredClipDataItem"));

            String text = invokeGetText(jni, baseVM, useVaList, item);
            assertEquals("TRACEAI_CLIP", text);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            assertEquals("TRACEAI_CLIP", invokeCoerceToText(jni, baseVM, useVaList, item, context));
            // getItemAt / getText / coerceToText emit no events
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));
            for (CapturedEvent e : sink.events) {
                if ("android_clipboard".equals(e.kind)
                        && !"ClipboardManager.hasPrimaryClip".equals(e.api)
                        && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                    fail("unexpected clipboard event api=" + e.api);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runPrimaryTextEmptyAndNegative(boolean is64Bit, boolean useVaList)
            throws Exception {
        // empty primaryText still works; textLength=0
        {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_EMPTY_TEXT_JSON);
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
                DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
                DvmObject<?> clipData = invokeGetPrimaryClip(jni, baseVM, useVaList, manager);
                CapturedEvent getClipEv = findLastEvent(sink.events, "android_clipboard",
                        "ClipboardManager.getPrimaryClip");
                assertNotNull(getClipEv);
                assertEquals("hasPrimaryClip=true,textLength=0", String.valueOf(getClipEv.value));
                DvmObject<?> emptyItem = invokeGetItemAt(jni, baseVM, useVaList, clipData, 0);
                assertEquals("", invokeGetText(jni, baseVM, useVaList, emptyItem));
                DvmObject<?> emptyContext = vm.resolveClass("android/content/Context").newObject(null);
                assertEquals("", invokeCoerceToText(jni, baseVM, useVaList, emptyItem, emptyContext));
            } finally {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                    emulator.close();
                }
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
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
            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> clipData = invokeGetPrimaryClip(jni, baseVM, useVaList, manager);
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            // invalid index → UOE, no extra getPrimaryClip event
            try {
                invokeGetItemAt(jni, baseVM, useVaList, clipData, 1);
                fail("expected UOE for getItemAt(1)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getItemAt"));
            }
            try {
                invokeGetItemAt(jni, baseVM, useVaList, clipData, -1);
                fail("expected UOE for getItemAt(-1)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getItemAt"));
            }

            // plain ClipData / Item
            DvmObject<?> plainClip = vm.resolveClass(CLIP_DATA_CLASS).newObject(null);
            try {
                invokeGetItemCount(jni, baseVM, useVaList, plainClip);
                fail("expected UOE for getItemCount on plain ClipData");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getItemCount"));
            }
            DvmObject<?> plainItem = vm.resolveClass(CLIP_ITEM_CLASS).newObject(null);
            try {
                invokeGetText(jni, baseVM, useVaList, plainItem);
                fail("expected UOE for getText on plain Item");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getText"));
            }
            try {
                invokeCoerceToText(jni, baseVM, useVaList, plainItem,
                        vm.resolveClass("android/content/Context").newObject(null));
                fail("expected UOE for coerceToText on plain Item");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("coerceToText"));
            }

            // no primaryText: getPrimaryClip UOE
            TraceEnvironmentConfig noText = TraceEnvironmentConfig.parse(CLIPBOARD_TRUE_JSON);
            AndroidEmulator noTextEmu = null;
            CapturingSink noTextSink = new CapturingSink();
            try {
                noTextEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noText)
                        .build();
                TraceEnvironmentEventSink.register(noTextEmu, noTextSink);
                VM noTextVm = noTextEmu.createDalvikVM();
                AbstractJni noTextJni = new AbstractJni() {
                };
                noTextVm.setJni(noTextJni);
                BaseVM noTextBase = (BaseVM) noTextVm;
                DvmObject<?> noTextMgr = resolveClipboardSystemService(noTextJni, noTextBase, useVaList, noTextVm);
                try {
                    invokeGetPrimaryClip(noTextJni, noTextBase, useVaList, noTextMgr);
                    fail("expected UOE without primaryText");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getPrimaryClip"));
                }
                assertEquals(0, countEvents(noTextSink.events, "android_clipboard",
                        "ClipboardManager.getPrimaryClip"));
            } finally {
                if (noTextEmu != null) {
                    TraceEnvironmentEventSink.unregister(noTextEmu, noTextSink);
                    noTextEmu.close();
                }
            }

            // stale config identity
            emulator.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON));
            try {
                invokeGetItemCount(jni, baseVM, useVaList, clipData);
                fail("expected UOE for stale ClipData");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getItemCount"));
            }
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            // cross-VM
            TraceEnvironmentConfig crossCfg = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
            AndroidEmulator crossEmu = null;
            try {
                crossEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(crossCfg)
                        .build();
                VM crossVm = crossEmu.createDalvikVM();
                AbstractJni crossJni = new AbstractJni() {
                };
                crossVm.setJni(crossJni);
                BaseVM crossBase = (BaseVM) crossVm;
                DvmObject<?> crossMgr = resolveClipboardSystemService(crossJni, crossBase, useVaList, crossVm);
                DvmObject<?> foreignClip = invokeGetPrimaryClip(crossJni, crossBase, useVaList, crossMgr);
                try {
                    invokeGetItemCount(jni, baseVM, useVaList, foreignClip);
                    fail("expected UOE for cross-VM ClipData");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getItemCount"));
                }
            } finally {
                if (crossEmu != null) {
                    crossEmu.close();
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runClipDescriptionMetadata(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
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

            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> clipData = invokeGetPrimaryClip(jni, baseVM, useVaList, manager);
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            DvmObject<?> description = invokeGetDescription(jni, baseVM, useVaList, clipData);
            assertNotNull(description);
            assertEquals(CLIP_DESCRIPTION_CLASS, description.getObjectType().getClassName());
            assertTrue(description.getValue().getClass().getName()
                    .contains("ConfiguredClipDescription"));
            assertEquals(1, invokeGetMimeTypeCount(jni, baseVM, useVaList, description));

            DvmObject<?> mime0 = invokeGetMimeType(jni, baseVM, useVaList, description, 0);
            assertTrue(mime0 instanceof StringObject);
            assertEquals(MIMETYPE_TEXT_PLAIN, ((StringObject) mime0).getValue());

            DvmObject<?> description2 = invokeGetDescription(jni, baseVM, useVaList, clipData);
            assertNotSame(description, description2);
            assertTrue(description.getValue() != description2.getValue());
            assertEquals(1, invokeGetMimeTypeCount(jni, baseVM, useVaList, description2));

            DvmObject<?> mime0b = invokeGetMimeType(jni, baseVM, useVaList, description, 0);
            assertNotSame(mime0, mime0b);
            assertTrue(mime0b instanceof StringObject);
            assertEquals(MIMETYPE_TEXT_PLAIN, ((StringObject) mime0b).getValue());

            // metadata getters must not add sidecar; getPrimaryClip sidecar unchanged
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));
            for (CapturedEvent e : sink.events) {
                if ("android_clipboard".equals(e.kind)
                        && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                    fail("unexpected clipboard event api=" + e.api);
                }
            }

            // empty primaryText still yields the same plain-text MIME subset
            TraceEnvironmentConfig emptyCfg = TraceEnvironmentConfig.parse(CLIPBOARD_EMPTY_TEXT_JSON);
            AndroidEmulator emptyEmu = null;
            CapturingSink emptySink = new CapturingSink();
            try {
                emptyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(emptyCfg)
                        .build();
                TraceEnvironmentEventSink.register(emptyEmu, emptySink);
                VM emptyVm = emptyEmu.createDalvikVM();
                AbstractJni emptyJni = new AbstractJni() {
                };
                emptyVm.setJni(emptyJni);
                BaseVM emptyBase = (BaseVM) emptyVm;
                DvmObject<?> emptyMgr = resolveClipboardSystemService(emptyJni, emptyBase, useVaList, emptyVm);
                DvmObject<?> emptyClip = invokeGetPrimaryClip(emptyJni, emptyBase, useVaList, emptyMgr);
                DvmObject<?> emptyDesc = invokeGetDescription(emptyJni, emptyBase, useVaList, emptyClip);
                assertEquals(1, invokeGetMimeTypeCount(emptyJni, emptyBase, useVaList, emptyDesc));
                DvmObject<?> emptyMime = invokeGetMimeType(emptyJni, emptyBase, useVaList, emptyDesc, 0);
                assertEquals(MIMETYPE_TEXT_PLAIN, ((StringObject) emptyMime).getValue());
                assertEquals(1, countEvents(emptySink.events, "android_clipboard",
                        "ClipboardManager.getPrimaryClip"));
            } finally {
                if (emptyEmu != null) {
                    TraceEnvironmentEventSink.unregister(emptyEmu, emptySink);
                    emptyEmu.close();
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runClipDescriptionMetadataNegative(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
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
            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> clipData = invokeGetPrimaryClip(jni, baseVM, useVaList, manager);
            DvmObject<?> description = invokeGetDescription(jni, baseVM, useVaList, clipData);
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            // bad index → UOE, no extra getPrimaryClip event
            try {
                invokeGetMimeType(jni, baseVM, useVaList, description, 1);
                fail("expected UOE for getMimeType(1)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMimeType"));
            }
            try {
                invokeGetMimeType(jni, baseVM, useVaList, description, -1);
                fail("expected UOE for getMimeType(-1)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMimeType"));
            }

            // isolation: plain ClipData / ClipDescription
            DvmObject<?> plainClip = vm.resolveClass(CLIP_DATA_CLASS).newObject(null);
            try {
                invokeGetDescription(jni, baseVM, useVaList, plainClip);
                fail("expected UOE for getDescription on plain ClipData");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDescription"));
            }
            DvmObject<?> plainDesc = vm.resolveClass(CLIP_DESCRIPTION_CLASS).newObject(null);
            try {
                invokeGetMimeTypeCount(jni, baseVM, useVaList, plainDesc);
                fail("expected UOE for getMimeTypeCount on plain ClipDescription");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMimeTypeCount"));
            }
            try {
                invokeGetMimeType(jni, baseVM, useVaList, plainDesc, 0);
                fail("expected UOE for getMimeType on plain ClipDescription");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMimeType"));
            }

            // wrong receiver: ClipData vs ClipDescription
            try {
                invokeGetMimeTypeCount(jni, baseVM, useVaList, clipData);
                fail("expected UOE for getMimeTypeCount on ClipData");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMimeTypeCount"));
            }
            try {
                invokeGetDescription(jni, baseVM, useVaList, description);
                fail("expected UOE for getDescription on ClipDescription");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDescription"));
            }

            // wrong signature: getLabel()Ljava/lang/String; is not handled
            try {
                DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getLabel",
                        "()Ljava/lang/String;", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, description, signature,
                            new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callObjectMethod(baseVM, description, signature,
                            new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for ClipDescription.getLabel");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLabel"));
            }

            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            // missing primaryText: getPrimaryClip UOE; metadata on plain objects UOE; no events
            TraceEnvironmentConfig noText = TraceEnvironmentConfig.parse(CLIPBOARD_TRUE_JSON);
            AndroidEmulator noTextEmu = null;
            CapturingSink noTextSink = new CapturingSink();
            try {
                noTextEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noText)
                        .build();
                TraceEnvironmentEventSink.register(noTextEmu, noTextSink);
                VM noTextVm = noTextEmu.createDalvikVM();
                AbstractJni noTextJni = new AbstractJni() {
                };
                noTextVm.setJni(noTextJni);
                BaseVM noTextBase = (BaseVM) noTextVm;
                DvmObject<?> noTextMgr = resolveClipboardSystemService(noTextJni, noTextBase, useVaList, noTextVm);
                try {
                    invokeGetPrimaryClip(noTextJni, noTextBase, useVaList, noTextMgr);
                    fail("expected UOE without primaryText");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getPrimaryClip"));
                }
                DvmObject<?> noTextPlainClip = noTextVm.resolveClass(CLIP_DATA_CLASS).newObject(null);
                try {
                    invokeGetDescription(noTextJni, noTextBase, useVaList, noTextPlainClip);
                    fail("expected UOE for getDescription without primaryText");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDescription"));
                }
                assertEquals(0, countEvents(noTextSink.events, "android_clipboard",
                        "ClipboardManager.getPrimaryClip"));
                for (CapturedEvent e : noTextSink.events) {
                    assertFalse("unexpected android_clipboard event without primaryText: " + e.api,
                            "android_clipboard".equals(e.kind));
                }
            } finally {
                if (noTextEmu != null) {
                    TraceEnvironmentEventSink.unregister(noTextEmu, noTextSink);
                    noTextEmu.close();
                }
            }

            // absence: no clipboard node → UOE, no events
            TraceEnvironmentConfig absent = TraceEnvironmentConfig.parse(NO_CLIPBOARD_JSON);
            AndroidEmulator absentEmu = null;
            CapturingSink absentSink = new CapturingSink();
            try {
                absentEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(absent)
                        .build();
                TraceEnvironmentEventSink.register(absentEmu, absentSink);
                VM absentVm = absentEmu.createDalvikVM();
                AbstractJni absentJni = new AbstractJni() {
                };
                absentVm.setJni(absentJni);
                BaseVM absentBase = (BaseVM) absentVm;
                DvmObject<?> absentClip = absentVm.resolveClass(CLIP_DATA_CLASS).newObject(null);
                try {
                    invokeGetDescription(absentJni, absentBase, useVaList, absentClip);
                    fail("expected UOE for getDescription when clipboard node absent");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDescription"));
                }
                DvmObject<?> absentDesc = absentVm.resolveClass(CLIP_DESCRIPTION_CLASS).newObject(null);
                try {
                    invokeGetMimeTypeCount(absentJni, absentBase, useVaList, absentDesc);
                    fail("expected UOE for getMimeTypeCount when clipboard node absent");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getMimeTypeCount"));
                }
                for (CapturedEvent e : absentSink.events) {
                    assertFalse("unexpected android_clipboard event when config absent: " + e.api,
                            "android_clipboard".equals(e.kind));
                }
            } finally {
                if (absentEmu != null) {
                    TraceEnvironmentEventSink.unregister(absentEmu, absentSink);
                    absentEmu.close();
                }
            }

            // stale config identity
            emulator.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON));
            try {
                invokeGetDescription(jni, baseVM, useVaList, clipData);
                fail("expected UOE for stale ClipData getDescription");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDescription"));
            }
            try {
                invokeGetMimeTypeCount(jni, baseVM, useVaList, description);
                fail("expected UOE for stale ClipDescription getMimeTypeCount");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMimeTypeCount"));
            }
            try {
                invokeGetMimeType(jni, baseVM, useVaList, description, 0);
                fail("expected UOE for stale ClipDescription getMimeType");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMimeType"));
            }
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            // cross-VM / foreign
            TraceEnvironmentConfig crossCfg = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
            AndroidEmulator crossEmu = null;
            try {
                crossEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(crossCfg)
                        .build();
                VM crossVm = crossEmu.createDalvikVM();
                AbstractJni crossJni = new AbstractJni() {
                };
                crossVm.setJni(crossJni);
                BaseVM crossBase = (BaseVM) crossVm;
                DvmObject<?> crossMgr = resolveClipboardSystemService(crossJni, crossBase, useVaList, crossVm);
                DvmObject<?> foreignClip = invokeGetPrimaryClip(crossJni, crossBase, useVaList, crossMgr);
                DvmObject<?> foreignDesc = invokeGetDescription(crossJni, crossBase, useVaList, foreignClip);
                try {
                    invokeGetDescription(jni, baseVM, useVaList, foreignClip);
                    fail("expected UOE for cross-VM ClipData getDescription");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDescription"));
                }
                try {
                    invokeGetMimeTypeCount(jni, baseVM, useVaList, foreignDesc);
                    fail("expected UOE for cross-VM ClipDescription getMimeTypeCount");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getMimeTypeCount"));
                }
                try {
                    invokeGetMimeType(jni, baseVM, useVaList, foreignDesc, 0);
                    fail("expected UOE for cross-VM ClipDescription getMimeType");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getMimeType"));
                }
            } finally {
                if (crossEmu != null) {
                    crossEmu.close();
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runHasMimeType(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
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

            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> clipData = invokeGetPrimaryClip(jni, baseVM, useVaList, manager);
            DvmObject<?> description = invokeGetDescription(jni, baseVM, useVaList, clipData);
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            assertTrue(invokeHasMimeType(jni, baseVM, useVaList, description, MIMETYPE_TEXT_PLAIN));
            assertTrue(invokeHasMimeType(jni, baseVM, useVaList, description, MIMETYPE_TEXT_WILDCARD));
            assertTrue(invokeHasMimeType(jni, baseVM, useVaList, description, MIMETYPE_ANY));

            assertFalse(invokeHasMimeType(jni, baseVM, useVaList, description, "text/html"));
            assertFalse(invokeHasMimeType(jni, baseVM, useVaList, description, "image/*"));
            assertFalse(invokeHasMimeType(jni, baseVM, useVaList, description, ""));
            assertFalse(invokeHasMimeType(jni, baseVM, useVaList, description, "text"));
            assertFalse(invokeHasMimeType(jni, baseVM, useVaList, description, "TEXT/PLAIN"));
            assertFalse(invokeHasMimeType(jni, baseVM, useVaList, description, "text/plain;charset=utf-8"));

            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));
            for (CapturedEvent e : sink.events) {
                if ("android_clipboard".equals(e.kind)
                        && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                    fail("unexpected clipboard event api=" + e.api);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runHasMimeTypeNegative(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
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
            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> clipData = invokeGetPrimaryClip(jni, baseVM, useVaList, manager);
            DvmObject<?> description = invokeGetDescription(jni, baseVM, useVaList, clipData);
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            try {
                invokeHasMimeTypeNull(jni, baseVM, useVaList, description);
                fail("expected UOE for null hasMimeType argument");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMimeType"));
            }
            try {
                invokeHasMimeTypeNonString(jni, baseVM, useVaList, description);
                fail("expected UOE for non-String hasMimeType argument");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMimeType"));
            }

            try {
                DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "hasMimeType", "()Z", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callBooleanMethodV(baseVM, description, signature,
                            new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callBooleanMethod(baseVM, description, signature,
                            new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for hasMimeType()Z");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMimeType"));
            }

            DvmObject<?> plainDesc = vm.resolveClass(CLIP_DESCRIPTION_CLASS).newObject(null);
            try {
                invokeHasMimeType(jni, baseVM, useVaList, plainDesc, MIMETYPE_TEXT_PLAIN);
                fail("expected UOE for hasMimeType on plain ClipDescription");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMimeType"));
            }
            try {
                invokeHasMimeType(jni, baseVM, useVaList, clipData, MIMETYPE_TEXT_PLAIN);
                fail("expected UOE for hasMimeType on ClipData");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMimeType"));
            }

            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));
            for (CapturedEvent e : sink.events) {
                if ("android_clipboard".equals(e.kind)
                        && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                    fail("unexpected clipboard event api=" + e.api);
                }
            }

            emulator.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON));
            try {
                invokeHasMimeType(jni, baseVM, useVaList, description, MIMETYPE_TEXT_PLAIN);
                fail("expected UOE for stale ClipDescription hasMimeType");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMimeType"));
            }
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            TraceEnvironmentConfig crossCfg = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
            AndroidEmulator crossEmu = null;
            try {
                crossEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(crossCfg)
                        .build();
                VM crossVm = crossEmu.createDalvikVM();
                AbstractJni crossJni = new AbstractJni() {
                };
                crossVm.setJni(crossJni);
                BaseVM crossBase = (BaseVM) crossVm;
                DvmObject<?> crossMgr = resolveClipboardSystemService(crossJni, crossBase, useVaList, crossVm);
                DvmObject<?> foreignClip = invokeGetPrimaryClip(crossJni, crossBase, useVaList, crossMgr);
                DvmObject<?> foreignDesc = invokeGetDescription(crossJni, crossBase, useVaList, foreignClip);
                try {
                    invokeHasMimeType(jni, baseVM, useVaList, foreignDesc, MIMETYPE_TEXT_PLAIN);
                    fail("expected UOE for cross-VM ClipDescription hasMimeType");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("hasMimeType"));
                }
            } finally {
                if (crossEmu != null) {
                    crossEmu.close();
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runClipDescriptionGetLabel(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_LABEL_JSON);
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

            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> clipData = invokeGetPrimaryClip(jni, baseVM, useVaList, manager);
            DvmObject<?> description = invokeGetDescription(jni, baseVM, useVaList, clipData);
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            DvmObject<?> label = invokeGetLabel(jni, baseVM, useVaList, description);
            assertTrue(label instanceof StringObject);
            assertEquals("TRACEAI_LABEL", ((StringObject) label).getValue());

            DvmObject<?> label2 = invokeGetLabel(jni, baseVM, useVaList, description);
            assertNotSame(label, label2);
            assertTrue(label2 instanceof StringObject);
            assertEquals("TRACEAI_LABEL", ((StringObject) label2).getValue());

            // getLabel must not add sidecar; getPrimaryClip sidecar unchanged
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));
            for (CapturedEvent e : sink.events) {
                if ("android_clipboard".equals(e.kind)
                        && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                    fail("unexpected clipboard event api=" + e.api);
                }
            }

            // omitted primaryLabel → Java null; not derived from primaryText
            TraceEnvironmentConfig omittedCfg = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
            AndroidEmulator omittedEmu = null;
            CapturingSink omittedSink = new CapturingSink();
            try {
                omittedEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(omittedCfg)
                        .build();
                TraceEnvironmentEventSink.register(omittedEmu, omittedSink);
                VM omittedVm = omittedEmu.createDalvikVM();
                AbstractJni omittedJni = new AbstractJni() {
                };
                omittedVm.setJni(omittedJni);
                BaseVM omittedBase = (BaseVM) omittedVm;
                DvmObject<?> omittedMgr = resolveClipboardSystemService(
                        omittedJni, omittedBase, useVaList, omittedVm);
                DvmObject<?> omittedClip = invokeGetPrimaryClip(
                        omittedJni, omittedBase, useVaList, omittedMgr);
                DvmObject<?> omittedDesc = invokeGetDescription(
                        omittedJni, omittedBase, useVaList, omittedClip);
                assertNull(invokeGetLabel(omittedJni, omittedBase, useVaList, omittedDesc));
                assertEquals(1, countEvents(omittedSink.events, "android_clipboard",
                        "ClipboardManager.getPrimaryClip"));
                for (CapturedEvent e : omittedSink.events) {
                    if ("android_clipboard".equals(e.kind)
                            && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                        fail("unexpected clipboard event api=" + e.api);
                    }
                }
            } finally {
                if (omittedEmu != null) {
                    TraceEnvironmentEventSink.unregister(omittedEmu, omittedSink);
                    omittedEmu.close();
                }
            }

            // empty primaryLabel still returns a StringObject (not Java null)
            TraceEnvironmentConfig emptyCfg = TraceEnvironmentConfig.parse(CLIPBOARD_EMPTY_LABEL_JSON);
            AndroidEmulator emptyEmu = null;
            CapturingSink emptySink = new CapturingSink();
            try {
                emptyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(emptyCfg)
                        .build();
                TraceEnvironmentEventSink.register(emptyEmu, emptySink);
                VM emptyVm = emptyEmu.createDalvikVM();
                AbstractJni emptyJni = new AbstractJni() {
                };
                emptyVm.setJni(emptyJni);
                BaseVM emptyBase = (BaseVM) emptyVm;
                DvmObject<?> emptyMgr = resolveClipboardSystemService(
                        emptyJni, emptyBase, useVaList, emptyVm);
                DvmObject<?> emptyClip = invokeGetPrimaryClip(
                        emptyJni, emptyBase, useVaList, emptyMgr);
                DvmObject<?> emptyDesc = invokeGetDescription(
                        emptyJni, emptyBase, useVaList, emptyClip);
                DvmObject<?> emptyLabel = invokeGetLabel(emptyJni, emptyBase, useVaList, emptyDesc);
                assertTrue(emptyLabel instanceof StringObject);
                assertEquals("", ((StringObject) emptyLabel).getValue());
                assertEquals(1, countEvents(emptySink.events, "android_clipboard",
                        "ClipboardManager.getPrimaryClip"));
                for (CapturedEvent e : emptySink.events) {
                    if ("android_clipboard".equals(e.kind)
                            && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                        fail("unexpected clipboard event api=" + e.api);
                    }
                }
            } finally {
                if (emptyEmu != null) {
                    TraceEnvironmentEventSink.unregister(emptyEmu, emptySink);
                    emptyEmu.close();
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runClipDescriptionGetLabelNegative(boolean is64Bit, boolean useVaList)
            throws Exception {
        try {
            TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\","
                    + "\"clipboard\":{\"hasPrimaryClip\":true,\"primaryLabel\":\"x\"}}}");
            fail("expected parse rejection for label-only clipboard");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("android.clipboard.primaryLabel"));
        }
        try {
            TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\","
                    + "\"clipboard\":{\"primaryLabel\":\"x\"}}}");
            fail("expected parse rejection for label-only clipboard without hasPrimaryClip");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("android.clipboard.primaryLabel"));
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_LABEL_JSON);
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
            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> clipData = invokeGetPrimaryClip(jni, baseVM, useVaList, manager);
            DvmObject<?> description = invokeGetDescription(jni, baseVM, useVaList, clipData);
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            // isolation: plain ClipDescription
            DvmObject<?> plainDesc = vm.resolveClass(CLIP_DESCRIPTION_CLASS).newObject(null);
            try {
                invokeGetLabel(jni, baseVM, useVaList, plainDesc);
                fail("expected UOE for getLabel on plain ClipDescription");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLabel"));
            }

            // wrong receiver: ClipData
            try {
                invokeGetLabel(jni, baseVM, useVaList, clipData);
                fail("expected UOE for getLabel on ClipData");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLabel"));
            }

            // wrong signature
            try {
                DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getLabel",
                        "()Ljava/lang/String;", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, description, signature,
                            new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callObjectMethod(baseVM, description, signature,
                            new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for ClipDescription.getLabel wrong signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLabel"));
            }

            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));
            for (CapturedEvent e : sink.events) {
                if ("android_clipboard".equals(e.kind)
                        && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                    fail("unexpected clipboard event api=" + e.api);
                }
            }

            // no primaryText: getPrimaryClip UOE; getLabel on plain UOE; no events
            TraceEnvironmentConfig noText = TraceEnvironmentConfig.parse(CLIPBOARD_TRUE_JSON);
            AndroidEmulator noTextEmu = null;
            CapturingSink noTextSink = new CapturingSink();
            try {
                noTextEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noText)
                        .build();
                TraceEnvironmentEventSink.register(noTextEmu, noTextSink);
                VM noTextVm = noTextEmu.createDalvikVM();
                AbstractJni noTextJni = new AbstractJni() {
                };
                noTextVm.setJni(noTextJni);
                BaseVM noTextBase = (BaseVM) noTextVm;
                DvmObject<?> noTextMgr = resolveClipboardSystemService(
                        noTextJni, noTextBase, useVaList, noTextVm);
                try {
                    invokeGetPrimaryClip(noTextJni, noTextBase, useVaList, noTextMgr);
                    fail("expected UOE without primaryText");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getPrimaryClip"));
                }
                DvmObject<?> noTextPlainDesc = noTextVm.resolveClass(CLIP_DESCRIPTION_CLASS)
                        .newObject(null);
                try {
                    invokeGetLabel(noTextJni, noTextBase, useVaList, noTextPlainDesc);
                    fail("expected UOE for getLabel without primaryText");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getLabel"));
                }
                for (CapturedEvent e : noTextSink.events) {
                    assertFalse("unexpected android_clipboard event without primaryText: " + e.api,
                            "android_clipboard".equals(e.kind));
                }
            } finally {
                if (noTextEmu != null) {
                    TraceEnvironmentEventSink.unregister(noTextEmu, noTextSink);
                    noTextEmu.close();
                }
            }

            // stale config identity
            emulator.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(CLIPBOARD_LABEL_JSON));
            try {
                invokeGetLabel(jni, baseVM, useVaList, description);
                fail("expected UOE for stale ClipDescription getLabel");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLabel"));
            }
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            // cross-VM / foreign
            TraceEnvironmentConfig crossCfg = TraceEnvironmentConfig.parse(CLIPBOARD_LABEL_JSON);
            AndroidEmulator crossEmu = null;
            try {
                crossEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(crossCfg)
                        .build();
                VM crossVm = crossEmu.createDalvikVM();
                AbstractJni crossJni = new AbstractJni() {
                };
                crossVm.setJni(crossJni);
                BaseVM crossBase = (BaseVM) crossVm;
                DvmObject<?> crossMgr = resolveClipboardSystemService(
                        crossJni, crossBase, useVaList, crossVm);
                DvmObject<?> foreignClip = invokeGetPrimaryClip(
                        crossJni, crossBase, useVaList, crossMgr);
                DvmObject<?> foreignDesc = invokeGetDescription(
                        crossJni, crossBase, useVaList, foreignClip);
                try {
                    invokeGetLabel(jni, baseVM, useVaList, foreignDesc);
                    fail("expected UOE for cross-VM ClipDescription getLabel");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getLabel"));
                }
            } finally {
                if (crossEmu != null) {
                    crossEmu.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                if ("android_clipboard".equals(e.kind)
                        && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                    fail("unexpected clipboard event api=" + e.api);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runClipDescriptionGetTimestamp(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_TIMESTAMP_JSON);
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

            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> clipData = invokeGetPrimaryClip(jni, baseVM, useVaList, manager);
            DvmObject<?> description = invokeGetDescription(jni, baseVM, useVaList, clipData);
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            assertEquals(1710000000000L, invokeGetTimestamp(jni, baseVM, useVaList, description));
            assertEquals(1710000000000L, invokeGetTimestamp(jni, baseVM, useVaList, description));

            // getTimestamp must not add sidecar; getPrimaryClip sidecar unchanged
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));
            for (CapturedEvent e : sink.events) {
                if ("android_clipboard".equals(e.kind)
                        && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                    fail("unexpected clipboard event api=" + e.api);
                }
            }

            // omitted timestampMillis → primitive 0; not derived from primaryText
            TraceEnvironmentConfig omittedCfg = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
            AndroidEmulator omittedEmu = null;
            CapturingSink omittedSink = new CapturingSink();
            try {
                omittedEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(omittedCfg)
                        .build();
                TraceEnvironmentEventSink.register(omittedEmu, omittedSink);
                VM omittedVm = omittedEmu.createDalvikVM();
                AbstractJni omittedJni = new AbstractJni() {
                };
                omittedVm.setJni(omittedJni);
                BaseVM omittedBase = (BaseVM) omittedVm;
                DvmObject<?> omittedMgr = resolveClipboardSystemService(
                        omittedJni, omittedBase, useVaList, omittedVm);
                DvmObject<?> omittedClip = invokeGetPrimaryClip(
                        omittedJni, omittedBase, useVaList, omittedMgr);
                DvmObject<?> omittedDesc = invokeGetDescription(
                        omittedJni, omittedBase, useVaList, omittedClip);
                assertEquals(0L, invokeGetTimestamp(omittedJni, omittedBase, useVaList, omittedDesc));
                assertEquals(1, countEvents(omittedSink.events, "android_clipboard",
                        "ClipboardManager.getPrimaryClip"));
                for (CapturedEvent e : omittedSink.events) {
                    if ("android_clipboard".equals(e.kind)
                            && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                        fail("unexpected clipboard event api=" + e.api);
                    }
                }
            } finally {
                if (omittedEmu != null) {
                    TraceEnvironmentEventSink.unregister(omittedEmu, omittedSink);
                    omittedEmu.close();
                }
            }

            // max long is returned exactly
            TraceEnvironmentConfig maxCfg = TraceEnvironmentConfig.parse(CLIPBOARD_TIMESTAMP_MAX_JSON);
            AndroidEmulator maxEmu = null;
            CapturingSink maxSink = new CapturingSink();
            try {
                maxEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(maxCfg)
                        .build();
                TraceEnvironmentEventSink.register(maxEmu, maxSink);
                VM maxVm = maxEmu.createDalvikVM();
                AbstractJni maxJni = new AbstractJni() {
                };
                maxVm.setJni(maxJni);
                BaseVM maxBase = (BaseVM) maxVm;
                DvmObject<?> maxMgr = resolveClipboardSystemService(
                        maxJni, maxBase, useVaList, maxVm);
                DvmObject<?> maxClip = invokeGetPrimaryClip(
                        maxJni, maxBase, useVaList, maxMgr);
                DvmObject<?> maxDesc = invokeGetDescription(
                        maxJni, maxBase, useVaList, maxClip);
                assertEquals(Long.MAX_VALUE, invokeGetTimestamp(maxJni, maxBase, useVaList, maxDesc));
                assertEquals(1, countEvents(maxSink.events, "android_clipboard",
                        "ClipboardManager.getPrimaryClip"));
                for (CapturedEvent e : maxSink.events) {
                    if ("android_clipboard".equals(e.kind)
                            && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                        fail("unexpected clipboard event api=" + e.api);
                    }
                }
            } finally {
                if (maxEmu != null) {
                    TraceEnvironmentEventSink.unregister(maxEmu, maxSink);
                    maxEmu.close();
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runClipDescriptionGetTimestampNegative(boolean is64Bit, boolean useVaList)
            throws Exception {
        try {
            TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\","
                    + "\"clipboard\":{\"hasPrimaryClip\":true,\"timestampMillis\":1}}}");
            fail("expected parse rejection for timestamp-only clipboard");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("android.clipboard.timestampMillis"));
        }
        try {
            TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\","
                    + "\"clipboard\":{\"timestampMillis\":1}}}");
            fail("expected parse rejection for timestamp-only clipboard without hasPrimaryClip");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("android.clipboard.timestampMillis"));
        }
        try {
            TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\","
                    + "\"clipboard\":{\"hasPrimaryClip\":true,\"primaryText\":\"TRACEAI_CLIP\","
                    + "\"timestamp\":1}}}");
            fail("expected parse rejection for old timestamp key");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("android.clipboard.timestamp is not an allowed key"));
        }
        try {
            TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\","
                    + "\"clipboard\":{\"hasPrimaryClip\":true,\"primaryText\":\"TRACEAI_CLIP\","
                    + "\"primaryTimestamp\":1}}}");
            fail("expected parse rejection for old primaryTimestamp key");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("android.clipboard.primaryTimestamp")
                    && expected.getMessage().contains("is not an allowed key"));
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_TIMESTAMP_JSON);
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
            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> clipData = invokeGetPrimaryClip(jni, baseVM, useVaList, manager);
            DvmObject<?> description = invokeGetDescription(jni, baseVM, useVaList, clipData);
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            // isolation: plain ClipDescription
            DvmObject<?> plainDesc = vm.resolveClass(CLIP_DESCRIPTION_CLASS).newObject(null);
            try {
                invokeGetTimestamp(jni, baseVM, useVaList, plainDesc);
                fail("expected UOE for getTimestamp on plain ClipDescription");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getTimestamp"));
            }

            // wrong receiver: ClipData
            try {
                invokeGetTimestamp(jni, baseVM, useVaList, clipData);
                fail("expected UOE for getTimestamp on ClipData");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getTimestamp"));
            }

            // wrong signature
            try {
                DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "getTimestamp", "()I", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callLongMethodV(baseVM, description, signature,
                            new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callLongMethod(baseVM, description, signature,
                            new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for ClipDescription.getTimestamp wrong signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getTimestamp"));
            }

            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));
            for (CapturedEvent e : sink.events) {
                if ("android_clipboard".equals(e.kind)
                        && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                    fail("unexpected clipboard event api=" + e.api);
                }
            }

            // no primaryText: getPrimaryClip UOE; getTimestamp on plain UOE; no events
            TraceEnvironmentConfig noText = TraceEnvironmentConfig.parse(CLIPBOARD_TRUE_JSON);
            AndroidEmulator noTextEmu = null;
            CapturingSink noTextSink = new CapturingSink();
            try {
                noTextEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noText)
                        .build();
                TraceEnvironmentEventSink.register(noTextEmu, noTextSink);
                VM noTextVm = noTextEmu.createDalvikVM();
                AbstractJni noTextJni = new AbstractJni() {
                };
                noTextVm.setJni(noTextJni);
                BaseVM noTextBase = (BaseVM) noTextVm;
                DvmObject<?> noTextMgr = resolveClipboardSystemService(
                        noTextJni, noTextBase, useVaList, noTextVm);
                try {
                    invokeGetPrimaryClip(noTextJni, noTextBase, useVaList, noTextMgr);
                    fail("expected UOE without primaryText");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getPrimaryClip"));
                }
                DvmObject<?> noTextPlainDesc = noTextVm.resolveClass(CLIP_DESCRIPTION_CLASS)
                        .newObject(null);
                try {
                    invokeGetTimestamp(noTextJni, noTextBase, useVaList, noTextPlainDesc);
                    fail("expected UOE for getTimestamp without primaryText");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getTimestamp"));
                }
                for (CapturedEvent e : noTextSink.events) {
                    assertFalse("unexpected android_clipboard event without primaryText: " + e.api,
                            "android_clipboard".equals(e.kind));
                }
            } finally {
                if (noTextEmu != null) {
                    TraceEnvironmentEventSink.unregister(noTextEmu, noTextSink);
                    noTextEmu.close();
                }
            }

            // stale config identity
            emulator.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(CLIPBOARD_TIMESTAMP_JSON));
            try {
                invokeGetTimestamp(jni, baseVM, useVaList, description);
                fail("expected UOE for stale ClipDescription getTimestamp");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getTimestamp"));
            }
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            // cross-VM / foreign
            TraceEnvironmentConfig crossCfg = TraceEnvironmentConfig.parse(CLIPBOARD_TIMESTAMP_JSON);
            AndroidEmulator crossEmu = null;
            try {
                crossEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(crossCfg)
                        .build();
                VM crossVm = crossEmu.createDalvikVM();
                AbstractJni crossJni = new AbstractJni() {
                };
                crossVm.setJni(crossJni);
                BaseVM crossBase = (BaseVM) crossVm;
                DvmObject<?> crossMgr = resolveClipboardSystemService(
                        crossJni, crossBase, useVaList, crossVm);
                DvmObject<?> foreignClip = invokeGetPrimaryClip(
                        crossJni, crossBase, useVaList, crossMgr);
                DvmObject<?> foreignDesc = invokeGetDescription(
                        crossJni, crossBase, useVaList, foreignClip);
                try {
                    invokeGetTimestamp(jni, baseVM, useVaList, foreignDesc);
                    fail("expected UOE for cross-VM ClipDescription getTimestamp");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getTimestamp"));
                }
            } finally {
                if (crossEmu != null) {
                    crossEmu.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                if ("android_clipboard".equals(e.kind)
                        && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                    fail("unexpected clipboard event api=" + e.api);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runClipDescriptionIsStyledText(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
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

            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> clipData = invokeGetPrimaryClip(jni, baseVM, useVaList, manager);
            DvmObject<?> description = invokeGetDescription(jni, baseVM, useVaList, clipData);
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            assertFalse(invokeIsStyledText(jni, baseVM, useVaList, description));
            assertFalse(invokeIsStyledText(jni, baseVM, useVaList, description));

            // isStyledText must not add sidecar; getPrimaryClip sidecar unchanged
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));
            for (CapturedEvent e : sink.events) {
                if ("android_clipboard".equals(e.kind)
                        && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                    fail("unexpected clipboard event api=" + e.api);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runClipDescriptionIsStyledTextNegative(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
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
            DvmObject<?> manager = resolveClipboardSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> clipData = invokeGetPrimaryClip(jni, baseVM, useVaList, manager);
            DvmObject<?> description = invokeGetDescription(jni, baseVM, useVaList, clipData);
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            DvmObject<?> plainDesc = vm.resolveClass(CLIP_DESCRIPTION_CLASS).newObject(null);
            try {
                invokeIsStyledText(jni, baseVM, useVaList, plainDesc);
                fail("expected UOE for isStyledText on plain ClipDescription");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isStyledText"));
            }

            try {
                invokeIsStyledText(jni, baseVM, useVaList, clipData);
                fail("expected UOE for isStyledText on ClipData");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isStyledText"));
            }

            try {
                DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
                DvmMethod method = new DvmMethod(dvmClass, "isStyledText", "()I", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callBooleanMethodV(baseVM, description, signature,
                            new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callBooleanMethod(baseVM, description, signature,
                            new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for ClipDescription.isStyledText wrong signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isStyledText"));
            }

            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));
            for (CapturedEvent e : sink.events) {
                if ("android_clipboard".equals(e.kind)
                        && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                    fail("unexpected clipboard event api=" + e.api);
                }
            }

            TraceEnvironmentConfig noText = TraceEnvironmentConfig.parse(CLIPBOARD_TRUE_JSON);
            AndroidEmulator noTextEmu = null;
            CapturingSink noTextSink = new CapturingSink();
            try {
                noTextEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noText)
                        .build();
                TraceEnvironmentEventSink.register(noTextEmu, noTextSink);
                VM noTextVm = noTextEmu.createDalvikVM();
                AbstractJni noTextJni = new AbstractJni() {
                };
                noTextVm.setJni(noTextJni);
                BaseVM noTextBase = (BaseVM) noTextVm;
                DvmObject<?> noTextMgr = resolveClipboardSystemService(
                        noTextJni, noTextBase, useVaList, noTextVm);
                try {
                    invokeGetPrimaryClip(noTextJni, noTextBase, useVaList, noTextMgr);
                    fail("expected UOE without primaryText");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getPrimaryClip"));
                }
                DvmObject<?> noTextPlainDesc = noTextVm.resolveClass(CLIP_DESCRIPTION_CLASS)
                        .newObject(null);
                try {
                    invokeIsStyledText(noTextJni, noTextBase, useVaList, noTextPlainDesc);
                    fail("expected UOE for isStyledText without primaryText");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("isStyledText"));
                }
                for (CapturedEvent e : noTextSink.events) {
                    assertFalse("unexpected android_clipboard event without primaryText: " + e.api,
                            "android_clipboard".equals(e.kind));
                }
            } finally {
                if (noTextEmu != null) {
                    TraceEnvironmentEventSink.unregister(noTextEmu, noTextSink);
                    noTextEmu.close();
                }
            }

            emulator.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON));
            try {
                invokeIsStyledText(jni, baseVM, useVaList, description);
                fail("expected UOE for stale ClipDescription isStyledText");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isStyledText"));
            }
            assertEquals(1, countEvents(sink.events, "android_clipboard",
                    "ClipboardManager.getPrimaryClip"));

            TraceEnvironmentConfig crossCfg = TraceEnvironmentConfig.parse(CLIPBOARD_TEXT_JSON);
            AndroidEmulator crossEmu = null;
            try {
                crossEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(crossCfg)
                        .build();
                VM crossVm = crossEmu.createDalvikVM();
                AbstractJni crossJni = new AbstractJni() {
                };
                crossVm.setJni(crossJni);
                BaseVM crossBase = (BaseVM) crossVm;
                DvmObject<?> crossMgr = resolveClipboardSystemService(
                        crossJni, crossBase, useVaList, crossVm);
                DvmObject<?> foreignClip = invokeGetPrimaryClip(
                        crossJni, crossBase, useVaList, crossMgr);
                DvmObject<?> foreignDesc = invokeGetDescription(
                        crossJni, crossBase, useVaList, foreignClip);
                try {
                    invokeIsStyledText(jni, baseVM, useVaList, foreignDesc);
                    fail("expected UOE for cross-VM ClipDescription isStyledText");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("isStyledText"));
                }
            } finally {
                if (crossEmu != null) {
                    crossEmu.close();
                }
            }

            for (CapturedEvent e : sink.events) {
                if ("android_clipboard".equals(e.kind)
                        && !"ClipboardManager.getPrimaryClip".equals(e.api)) {
                    fail("unexpected clipboard event api=" + e.api);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> resolveClipboardSystemService(AbstractJni jni, BaseVM vm,
                                                              boolean useVaList, VM dalvikVm) {
        DvmObject<?> app = dalvikVm.resolveClass("android/app/Application").newObject(null);
        return invokeGetSystemService(jni, vm, useVaList, app, "clipboard");
    }

    private static DvmObject<?> invokeGetSystemService(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> app, String serviceName) {
        int nameHash = vm.addLocalObject(new StringObject(vm, serviceName));
        DvmClass dvmClass = app.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/String;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, app, signature, new TestObjectVaList(vm, method, nameHash));
        }
        return jni.callObjectMethod(vm, app, signature, new TestObjectVarArg(vm, method, nameHash));
    }

    private static boolean invokeHasPrimaryClip(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmObject<?> target) {
        // Always use exact ClipboardManager signature (even for isolation receivers)
        DvmClass dvmClass = vm.resolveClass(CLIPBOARD_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasPrimaryClip", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static DvmObject<?> invokeGetPrimaryClip(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                     DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(CLIPBOARD_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getPrimaryClip",
                "()Landroid/content/ClipData;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callObjectMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static int invokeGetItemCount(AbstractJni jni, BaseVM vm, boolean useVaList,
                                          DvmObject<?> clipData) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DATA_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getItemCount", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, clipData, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, clipData, signature, new TestNoArgVarArg(vm, method));
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
            result = jni.callObjectMethodV(vm, item, signature, new TestNoArgVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, item, signature, new TestNoArgVarArg(vm, method));
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static String invokeCoerceToText(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             DvmObject<?> item, DvmObject<?> context) {
        DvmClass dvmClass = vm.resolveClass(CLIP_ITEM_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "coerceToText",
                "(Landroid/content/Context;)Ljava/lang/CharSequence;", false);
        String signature = method.getSignature();
        int contextHash = vm.addLocalObject(context);
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, item, signature,
                    new TestObjectVaList(vm, method, contextHash));
        } else {
            result = jni.callObjectMethod(vm, item, signature,
                    new TestObjectVarArg(vm, method, contextHash));
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
            return jni.callObjectMethodV(vm, clipData, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callObjectMethod(vm, clipData, signature, new TestNoArgVarArg(vm, method));
    }

    private static int invokeGetMimeTypeCount(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> description) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getMimeTypeCount", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, description, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, description, signature, new TestNoArgVarArg(vm, method));
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
            return jni.callObjectMethodV(vm, description, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callObjectMethod(vm, description, signature, new TestNoArgVarArg(vm, method));
    }

    private static long invokeGetTimestamp(AbstractJni jni, BaseVM vm, boolean useVaList,
                                           DvmObject<?> description) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getTimestamp", "()J", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callLongMethodV(vm, description, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callLongMethod(vm, description, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsStyledText(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> description) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isStyledText", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, description, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, description, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeHasMimeType(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             DvmObject<?> description, String mimeType) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasMimeType",
                "(Ljava/lang/String;)Z", false);
        String signature = method.getSignature();
        int nameHash = vm.addLocalObject(new StringObject(vm, mimeType));
        if (useVaList) {
            return jni.callBooleanMethodV(vm, description, signature,
                    new TestObjectVaList(vm, method, nameHash));
        }
        return jni.callBooleanMethod(vm, description, signature,
                new TestObjectVarArg(vm, method, nameHash));
    }

    private static boolean invokeHasMimeTypeNull(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> description) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasMimeType",
                "(Ljava/lang/String;)Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, description, signature,
                    new TestObjectVaList(vm, method, 0));
        }
        return jni.callBooleanMethod(vm, description, signature,
                new TestObjectVarArg(vm, method, 0));
    }

    private static boolean invokeHasMimeTypeNonString(AbstractJni jni, BaseVM vm,
                                                      boolean useVaList, DvmObject<?> description) {
        DvmClass dvmClass = vm.resolveClass(CLIP_DESCRIPTION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasMimeType",
                "(Ljava/lang/String;)Z", false);
        String signature = method.getSignature();
        int hash = vm.addLocalObject(DvmInteger.valueOf(vm, 1));
        if (useVaList) {
            return jni.callBooleanMethodV(vm, description, signature,
                    new TestObjectVaList(vm, method, hash));
        }
        return jni.callBooleanMethod(vm, description, signature,
                new TestObjectVarArg(vm, method, hash));
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

    private static final class TestObjectVarArg extends VarArg {
        TestObjectVarArg(BaseVM vm, DvmMethod method, int objectHash) {
            super(vm, method);
            args.add(objectHash);
        }
    }

    private static final class TestObjectVaList extends VaList {
        TestObjectVaList(BaseVM vm, DvmMethod method, int objectHash) {
            super(vm, method);
            args.add(objectHash);
        }
    }

    private static final class TestNoArgVarArg extends VarArg {
        TestNoArgVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class TestNoArgVaList extends VaList {
        TestNoArgVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
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

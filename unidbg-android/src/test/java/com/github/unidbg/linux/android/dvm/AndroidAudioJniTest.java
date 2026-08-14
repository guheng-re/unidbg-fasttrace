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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Coverage for {@code android.audio} + {@code AudioManager.isMusicActive()Z} /
 * {@code isSpeakerphoneOn()Z} / {@code getRingerMode()I} / {@code getMode()I} /
 * {@code getProperty(String)} / {@code getStreamVolume(I)} / {@code getStreamMaxVolume(I)} /
 * {@code getStreamMinVolume(I)}
 * (SystemService audio marker only; VarArg + VaList), plus typed
 * {@code Application}/{@code Context.getSystemService(Class)} for {@code AudioManager}.
 */
public class AndroidAudioJniTest {

    private static final String AUDIO_MANAGER_CLASS = "android/media/AudioManager";

    private static final String AUDIO_TRUE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"musicActive\":true}"
            + "}"
            + "}";

    private static final String AUDIO_FALSE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"musicActive\":false}"
            + "}"
            + "}";

    private static final String AUDIO_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{}"
            + "}"
            + "}";

    private static final String SPEAKER_TRUE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"speakerphoneOn\":true}"
            + "}"
            + "}";

    private static final String SPEAKER_FALSE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"speakerphoneOn\":false}"
            + "}"
            + "}";

    private static final String RINGER_SILENT_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"ringerMode\":0}"
            + "}"
            + "}";

    private static final String RINGER_VIBRATE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"ringerMode\":1}"
            + "}"
            + "}";

    private static final String RINGER_NORMAL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"ringerMode\":2}"
            + "}"
            + "}";

    private static final String MODE_NORMAL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"mode\":0}"
            + "}"
            + "}";

    private static final String MODE_IN_CALL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"mode\":2}"
            + "}"
            + "}";

    private static final String MODE_ASSISTANT_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"mode\":7}"
            + "}"
            + "}";

    private static final String INDEPENDENT_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"musicActive\":true,\"speakerphoneOn\":false,\"ringerMode\":1,\"mode\":3}"
            + "}"
            + "}";

    private static final String NO_AUDIO_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    private static final String PROP_SAMPLE_RATE =
            "android.media.property.OUTPUT_SAMPLE_RATE";
    private static final String PROP_FRAMES =
            "android.media.property.OUTPUT_FRAMES_PER_BUFFER";
    private static final String PROP_ABSENT = "android.media.property.ABSENT_KEY";

    private static final String AUDIO_PROPERTIES_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"properties\":{"
            + "\"" + PROP_SAMPLE_RATE + "\":\"48000\","
            + "\"" + PROP_FRAMES + "\":null"
            + "}}"
            + "}"
            + "}";

    private static final String AUDIO_EMPTY_WITH_PROPS_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"properties\":{}}"
            + "}"
            + "}";

    private static final String STREAM_VOLUMES_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"streamVolumes\":["
            + "{\"streamType\":3,\"volume\":7,\"maxVolume\":15},"
            + "{\"streamType\":0,\"volume\":0,\"maxVolume\":7}"
            + "]}"
            + "}"
            + "}";

    private static final String STREAM_VOLUMES_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"streamVolumes\":[]}"
            + "}"
            + "}";

    private static final String STREAM_VOLUMES_WITH_MIN_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"audio\":{\"streamVolumes\":["
            + "{\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":1},"
            + "{\"streamType\":0,\"volume\":0,\"maxVolume\":7},"
            + "{\"streamType\":2,\"volume\":5,\"maxVolume\":7,\"minVolume\":0}"
            + "]}"
            + "}"
            + "}";

    @Test
    public void testGetPropertyConfiguredStringVarArg32() throws Exception {
        runConfiguredGetProperty(false, false, PROP_SAMPLE_RATE, "48000", true);
    }

    @Test
    public void testGetPropertyConfiguredStringVaList64() throws Exception {
        runConfiguredGetProperty(true, true, PROP_SAMPLE_RATE, "48000", true);
    }

    @Test
    public void testGetPropertyConfiguredNullVarArg32() throws Exception {
        runConfiguredGetProperty(false, false, PROP_FRAMES, null, true);
    }

    @Test
    public void testGetPropertyConfiguredNullVaList64() throws Exception {
        runConfiguredGetProperty(true, true, PROP_FRAMES, null, true);
    }

    @Test
    public void testGetPropertyAbsentKeyNoEventVarArg32() throws Exception {
        runGetPropertyAbsentKey(false, false);
    }

    @Test
    public void testGetPropertyAbsentKeyNoEventVaList64() throws Exception {
        runGetPropertyAbsentKey(true, true);
    }

    @Test
    public void testGetPropertyNodeMissingUoeVarArg32() throws Exception {
        runGetPropertyNodeMissing(false, false);
    }

    @Test
    public void testGetPropertyNodeMissingUoeVaList64() throws Exception {
        runGetPropertyNodeMissing(true, true);
    }

    @Test
    public void testGetPropertyMarkerIsolationVarArg32() throws Exception {
        runGetPropertyIsolation(false, false);
    }

    @Test
    public void testGetPropertyMarkerIsolationVaList64() throws Exception {
        runGetPropertyIsolation(true, true);
    }

    @Test
    public void testStreamVolumesConfiguredVarArg32() throws Exception {
        runStreamVolumesConfigured(false, false);
    }

    @Test
    public void testStreamVolumesConfiguredVaList64() throws Exception {
        runStreamVolumesConfigured(true, true);
    }

    @Test
    public void testStreamVolumesEmptyUnmatchedVarArg32() throws Exception {
        runStreamVolumesEmptyUnmatched(false, false);
    }

    @Test
    public void testStreamVolumesEmptyUnmatchedVaList64() throws Exception {
        runStreamVolumesEmptyUnmatched(true, true);
    }

    @Test
    public void testStreamVolumesAbsentAndIsolationVarArg32() throws Exception {
        runStreamVolumesAbsentAndIsolation(false, false);
    }

    @Test
    public void testStreamVolumesAbsentAndIsolationVaList64() throws Exception {
        runStreamVolumesAbsentAndIsolation(true, true);
    }

    @Test
    public void testGetStreamMinVolumeConfiguredVarArg32() throws Exception {
        runStreamMinVolumeConfigured(false, false);
    }

    @Test
    public void testGetStreamMinVolumeConfiguredVaList64() throws Exception {
        runStreamMinVolumeConfigured(true, true);
    }

    @Test
    public void testGetStreamMinVolumeUoePathsVarArg32() throws Exception {
        runStreamMinVolumeUoePaths(false, false);
    }

    @Test
    public void testGetStreamMinVolumeUoePathsVaList64() throws Exception {
        runStreamMinVolumeUoePaths(true, true);
    }

    @Test
    public void testGetStreamMinVolumeZeroLegalVarArg32() throws Exception {
        runStreamMinVolumeZeroLegal(false, false);
    }

    @Test
    public void testGetStreamMinVolumeZeroLegalVaList64() throws Exception {
        runStreamMinVolumeZeroLegal(true, true);
    }

    @Test
    public void testStreamVolumeMinVolumeParseAndAccessors() {
        runStreamVolumeMinVolumeParseAndAccessors();
    }

    @Test
    public void testIsMusicActiveTrueVarArg32() throws Exception {
        runConfiguredIsMusicActive(false, false, AUDIO_TRUE_JSON, true);
    }

    @Test
    public void testIsMusicActiveTrueVaList64() throws Exception {
        runConfiguredIsMusicActive(true, true, AUDIO_TRUE_JSON, true);
    }

    @Test
    public void testIsMusicActiveFalseVarArg32() throws Exception {
        runConfiguredIsMusicActive(false, false, AUDIO_FALSE_JSON, false);
    }

    @Test
    public void testIsMusicActiveFalseVaList64() throws Exception {
        runConfiguredIsMusicActive(true, true, AUDIO_FALSE_JSON, false);
    }

    @Test
    public void testIsMusicActiveDefaultFalseVarArg32() throws Exception {
        runConfiguredIsMusicActive(false, false, AUDIO_EMPTY_JSON, false);
    }

    @Test
    public void testIsMusicActiveDefaultFalseVaList64() throws Exception {
        runConfiguredIsMusicActive(true, true, AUDIO_EMPTY_JSON, false);
    }

    @Test
    public void testIsMusicActiveAbsentVarArg32() throws Exception {
        runAbsentBoolean(false, false, "isMusicActive");
    }

    @Test
    public void testIsMusicActiveAbsentVaList64() throws Exception {
        runAbsentBoolean(true, true, "isMusicActive");
    }

    @Test
    public void testIsSpeakerphoneOnTrueVarArg32() throws Exception {
        runConfiguredIsSpeakerphoneOn(false, false, SPEAKER_TRUE_JSON, true);
    }

    @Test
    public void testIsSpeakerphoneOnTrueVaList64() throws Exception {
        runConfiguredIsSpeakerphoneOn(true, true, SPEAKER_TRUE_JSON, true);
    }

    @Test
    public void testIsSpeakerphoneOnFalseVarArg32() throws Exception {
        runConfiguredIsSpeakerphoneOn(false, false, SPEAKER_FALSE_JSON, false);
    }

    @Test
    public void testIsSpeakerphoneOnFalseVaList64() throws Exception {
        runConfiguredIsSpeakerphoneOn(true, true, SPEAKER_FALSE_JSON, false);
    }

    @Test
    public void testIsSpeakerphoneOnDefaultFalseVarArg32() throws Exception {
        runConfiguredIsSpeakerphoneOn(false, false, AUDIO_EMPTY_JSON, false);
    }

    @Test
    public void testIsSpeakerphoneOnDefaultFalseVaList64() throws Exception {
        runConfiguredIsSpeakerphoneOn(true, true, AUDIO_EMPTY_JSON, false);
    }

    @Test
    public void testIsSpeakerphoneOnAbsentVarArg32() throws Exception {
        runAbsentBoolean(false, false, "isSpeakerphoneOn");
    }

    @Test
    public void testIsSpeakerphoneOnAbsentVaList64() throws Exception {
        runAbsentBoolean(true, true, "isSpeakerphoneOn");
    }

    @Test
    public void testGetRingerModeSilentVarArg32() throws Exception {
        runConfiguredGetRingerMode(false, false, RINGER_SILENT_JSON, 0);
    }

    @Test
    public void testGetRingerModeSilentVaList64() throws Exception {
        runConfiguredGetRingerMode(true, true, RINGER_SILENT_JSON, 0);
    }

    @Test
    public void testGetRingerModeVibrateVarArg32() throws Exception {
        runConfiguredGetRingerMode(false, false, RINGER_VIBRATE_JSON, 1);
    }

    @Test
    public void testGetRingerModeVibrateVaList64() throws Exception {
        runConfiguredGetRingerMode(true, true, RINGER_VIBRATE_JSON, 1);
    }

    @Test
    public void testGetRingerModeNormalVarArg32() throws Exception {
        runConfiguredGetRingerMode(false, false, RINGER_NORMAL_JSON, 2);
    }

    @Test
    public void testGetRingerModeNormalVaList64() throws Exception {
        runConfiguredGetRingerMode(true, true, RINGER_NORMAL_JSON, 2);
    }

    @Test
    public void testGetRingerModeDefaultNormalVarArg32() throws Exception {
        runConfiguredGetRingerMode(false, false, AUDIO_EMPTY_JSON, 2);
    }

    @Test
    public void testGetRingerModeDefaultNormalVaList64() throws Exception {
        runConfiguredGetRingerMode(true, true, AUDIO_EMPTY_JSON, 2);
    }

    @Test
    public void testGetRingerModeAbsentVarArg32() throws Exception {
        runAbsentGetRingerMode(false, false);
    }

    @Test
    public void testGetRingerModeAbsentVaList64() throws Exception {
        runAbsentGetRingerMode(true, true);
    }

    @Test
    public void testGetModeNormalVarArg32() throws Exception {
        runConfiguredGetMode(false, false, MODE_NORMAL_JSON, 0);
    }

    @Test
    public void testGetModeNormalVaList64() throws Exception {
        runConfiguredGetMode(true, true, MODE_NORMAL_JSON, 0);
    }

    @Test
    public void testGetModeInCallVarArg32() throws Exception {
        runConfiguredGetMode(false, false, MODE_IN_CALL_JSON, 2);
    }

    @Test
    public void testGetModeInCallVaList64() throws Exception {
        runConfiguredGetMode(true, true, MODE_IN_CALL_JSON, 2);
    }

    @Test
    public void testGetModeAssistantVarArg32() throws Exception {
        runConfiguredGetMode(false, false, MODE_ASSISTANT_JSON, 7);
    }

    @Test
    public void testGetModeAssistantVaList64() throws Exception {
        runConfiguredGetMode(true, true, MODE_ASSISTANT_JSON, 7);
    }

    @Test
    public void testGetModeDefaultNormalVarArg32() throws Exception {
        runConfiguredGetMode(false, false, AUDIO_EMPTY_JSON, 0);
    }

    @Test
    public void testGetModeDefaultNormalVaList64() throws Exception {
        runConfiguredGetMode(true, true, AUDIO_EMPTY_JSON, 0);
    }

    @Test
    public void testGetModeAbsentVarArg32() throws Exception {
        runAbsentGetMode(false, false);
    }

    @Test
    public void testGetModeAbsentVaList64() throws Exception {
        runAbsentGetMode(true, true);
    }

    @Test
    public void testIndependentFieldsVarArg32() throws Exception {
        runIndependentFields(false, false);
    }

    @Test
    public void testIndependentFieldsVaList64() throws Exception {
        runIndependentFields(true, true);
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
    public void testAudioServiceNameAndValueVarArg32() throws Exception {
        runServiceNameAndValue(false, false);
    }

    @Test
    public void testAudioServiceNameAndValueVaList64() throws Exception {
        runServiceNameAndValue(true, true);
    }

    @Test
    public void testAudioTypedGetSystemServiceVarArg32() throws Exception {
        runAudioTypedGetSystemService(false, false);
    }

    @Test
    public void testAudioTypedGetSystemServiceVaList64() throws Exception {
        runAudioTypedGetSystemService(true, true);
    }

    private static void runConfiguredIsMusicActive(boolean is64Bit, boolean useVaList,
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertEquals(expected, invokeIsMusicActive(jni, baseVM, useVaList, manager));

            CapturedEvent ev = findLastEvent(sink.events, "android_audio",
                    "AudioManager.isMusicActive");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=musicActive,result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.isMusicActive"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredIsSpeakerphoneOn(boolean is64Bit, boolean useVaList,
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertEquals(expected, invokeIsSpeakerphoneOn(jni, baseVM, useVaList, manager));

            CapturedEvent ev = findLastEvent(sink.events, "android_audio",
                    "AudioManager.isSpeakerphoneOn");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=speakerphoneOn,result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.isSpeakerphoneOn"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredGetRingerMode(boolean is64Bit, boolean useVaList,
                                                   String json, int expected) throws Exception {
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertEquals(expected, invokeGetRingerMode(jni, baseVM, useVaList, manager));

            CapturedEvent ev = findLastEvent(sink.events, "android_audio",
                    "AudioManager.getRingerMode");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=ringerMode,result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.getRingerMode"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConfiguredGetMode(boolean is64Bit, boolean useVaList,
                                             String json, int expected) throws Exception {
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertEquals(expected, invokeGetMode(jni, baseVM, useVaList, manager));

            CapturedEvent ev = findLastEvent(sink.events, "android_audio",
                    "AudioManager.getMode");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=mode,result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.getMode"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIndependentFields(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(INDEPENDENT_JSON);
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertTrue(invokeIsMusicActive(jni, baseVM, useVaList, manager));
            assertFalse(invokeIsSpeakerphoneOn(jni, baseVM, useVaList, manager));
            assertEquals(1, invokeGetRingerMode(jni, baseVM, useVaList, manager));
            assertEquals(3, invokeGetMode(jni, baseVM, useVaList, manager));

            CapturedEvent music = findLastEvent(sink.events, "android_audio",
                    "AudioManager.isMusicActive");
            assertNotNull(music);
            assertEquals("field=musicActive,result=true", String.valueOf(music.value));
            CapturedEvent speaker = findLastEvent(sink.events, "android_audio",
                    "AudioManager.isSpeakerphoneOn");
            assertNotNull(speaker);
            assertEquals("field=speakerphoneOn,result=false", String.valueOf(speaker.value));
            CapturedEvent ringer = findLastEvent(sink.events, "android_audio",
                    "AudioManager.getRingerMode");
            assertNotNull(ringer);
            assertEquals("field=ringerMode,result=1", String.valueOf(ringer.value));
            assertEquals("json-config", ringer.source);
            CapturedEvent mode = findLastEvent(sink.events, "android_audio",
                    "AudioManager.getMode");
            assertNotNull(mode);
            assertEquals("field=mode,result=3", String.valueOf(mode.value));
            assertEquals("json-config", mode.source);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentBoolean(boolean is64Bit, boolean useVaList, String methodName)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_AUDIO_JSON);
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            try {
                if ("isSpeakerphoneOn".equals(methodName)) {
                    invokeIsSpeakerphoneOn(jni, baseVM, useVaList, manager);
                } else {
                    invokeIsMusicActive(jni, baseVM, useVaList, manager);
                }
                fail("expected UnsupportedOperationException without audio config for " + methodName);
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains(methodName));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_audio event when config absent: " + e.api,
                        "android_audio".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentGetRingerMode(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_AUDIO_JSON);
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            try {
                invokeGetRingerMode(jni, baseVM, useVaList, manager);
                fail("expected UnsupportedOperationException without audio config for getRingerMode");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getRingerMode"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_audio event when config absent: " + e.api,
                        "android_audio".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentGetMode(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_AUDIO_JSON);
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            try {
                invokeGetMode(jni, baseVM, useVaList, manager);
                fail("expected UnsupportedOperationException without audio config for getMode");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMode"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_audio event when config absent: " + e.api,
                        "android_audio".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runPlainAndOtherIsolation(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(INDEPENDENT_JSON);
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

            // plain AudioManager → UOE, no event
            DvmObject<?> plain = vm.resolveClass(AUDIO_MANAGER_CLASS).newObject(null);
            try {
                invokeIsMusicActive(jni, baseVM, useVaList, plain);
                fail("expected UOE for isMusicActive on plain AudioManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isMusicActive"));
            }
            try {
                invokeGetProperty(jni, baseVM, useVaList, plain, PROP_SAMPLE_RATE);
                fail("expected UOE for getProperty on plain AudioManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProperty"));
            }
            try {
                invokeIsSpeakerphoneOn(jni, baseVM, useVaList, plain);
                fail("expected UOE for isSpeakerphoneOn on plain AudioManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isSpeakerphoneOn"));
            }
            try {
                invokeGetRingerMode(jni, baseVM, useVaList, plain);
                fail("expected UOE for getRingerMode on plain AudioManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getRingerMode"));
            }
            try {
                invokeGetMode(jni, baseVM, useVaList, plain);
                fail("expected UOE for getMode on plain AudioManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMode"));
            }

            // other SystemService (wifi)
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> wifi = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            assertTrue(wifi instanceof SystemService);
            try {
                invokeIsMusicActive(jni, baseVM, useVaList, wifi);
                fail("expected UOE for isMusicActive on non-audio SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isMusicActive"));
            }
            try {
                invokeIsSpeakerphoneOn(jni, baseVM, useVaList, wifi);
                fail("expected UOE for isSpeakerphoneOn on non-audio SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isSpeakerphoneOn"));
            }
            try {
                invokeGetRingerMode(jni, baseVM, useVaList, wifi);
                fail("expected UOE for getRingerMode on non-audio SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getRingerMode"));
            }
            try {
                invokeGetMode(jni, baseVM, useVaList, wifi);
                fail("expected UOE for getMode on non-audio SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMode"));
            }

            // wrong signature on audio marker → UOE, no event
            DvmObject<?> audio = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            try {
                DvmClass dvmClass = audio.getObjectType();
                DvmMethod method = new DvmMethod(dvmClass, "isBluetoothScoOn", "()Z", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callBooleanMethodV(baseVM, audio, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callBooleanMethod(baseVM, audio, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for isBluetoothScoOn");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isBluetoothScoOn"));
            }
            try {
                DvmClass dvmClass = audio.getObjectType();
                DvmMethod method = new DvmMethod(dvmClass, "getStreamVolume", "(I)I", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callIntMethodV(baseVM, audio, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callIntMethod(baseVM, audio, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for getStreamVolume");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamVolume"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_audio event on isolation paths: " + e.api,
                        "android_audio".equals(e.kind));
            }

            // control: real audio SystemService still works for all four getters
            assertTrue(invokeIsMusicActive(jni, baseVM, useVaList, audio));
            assertFalse(invokeIsSpeakerphoneOn(jni, baseVM, useVaList, audio));
            assertEquals(1, invokeGetRingerMode(jni, baseVM, useVaList, audio));
            assertEquals(3, invokeGetMode(jni, baseVM, useVaList, audio));
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.isMusicActive"));
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.isSpeakerphoneOn"));
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.getRingerMode"));
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.getMode"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runServiceNameAndValue(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(AUDIO_TRUE_JSON);
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
                    "android/content/Context->AUDIO_SERVICE:Ljava/lang/String;");
            assertTrue(serviceName instanceof StringObject);
            assertEquals(SystemService.AUDIO_SERVICE, ((StringObject) serviceName).getValue());
            assertEquals("audio", ((StringObject) serviceName).getValue());

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> manager = invokeGetSystemService(jni, baseVM, useVaList, app,
                    ((StringObject) serviceName).getValue());
            assertNotNull(manager);
            assertTrue(manager instanceof SystemService);
            assertEquals(AUDIO_MANAGER_CLASS, manager.getObjectType().getClassName());
            assertEquals(SystemService.AUDIO_SERVICE, manager.getValue());

            assertTrue(invokeIsMusicActive(jni, baseVM, useVaList, manager));
            CapturedEvent ev = findLastEvent(sink.events, "android_audio",
                    "AudioManager.isMusicActive");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=musicActive,result=true", String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAudioTypedGetSystemService(boolean is64Bit, boolean useVaList)
            throws Exception {
        runAudioTypedConfigured(is64Bit, useVaList);
        runAudioTypedAbsent(is64Bit, useVaList);
    }

    private static void runAudioTypedConfigured(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(AUDIO_TRUE_JSON);
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
            DvmClass audioClass = vm.resolveClass(AUDIO_MANAGER_CLASS);

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromString = invokeGetSystemService(jni, baseVM, useVaList, app, "audio");
            int eventsBeforeApp = sink.events.size();
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, audioClass);
            assertAudioManagerMarker(fromApp);
            assertEquals(fromString.getObjectType().getClassName(), fromApp.getObjectType().getClassName());
            assertEquals(fromString.getValue(), fromApp.getValue());
            assertNoAndroidAudioSince(sink, eventsBeforeApp);
            assertTrue(invokeIsMusicActive(jni, baseVM, useVaList, fromApp));

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            int eventsBeforeCtx = sink.events.size();
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, audioClass);
            assertAudioManagerMarker(fromCtx);
            assertNoAndroidAudioSince(sink, eventsBeforeCtx);
            assertTrue(invokeIsMusicActive(jni, baseVM, useVaList, fromCtx));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAudioTypedAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_AUDIO_JSON);
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
            DvmClass audioClass = vm.resolveClass(AUDIO_MANAGER_CLASS);
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, audioClass);
            assertAudioManagerMarker(fromApp);
            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, audioClass);
            assertAudioManagerMarker(fromCtx);
            try {
                invokeIsMusicActive(jni, baseVM, useVaList, fromApp);
                fail("expected UOE for isMusicActive without android.audio");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isMusicActive"));
            }
            try {
                invokeGetRingerMode(jni, baseVM, useVaList, fromCtx);
                fail("expected UOE for getRingerMode without android.audio");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getRingerMode"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_audio event when audio absent: " + e.api,
                        "android_audio".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertAudioManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals(AUDIO_MANAGER_CLASS, manager.getObjectType().getClassName());
        assertEquals(SystemService.AUDIO_SERVICE, manager.getValue());
    }

    private static void assertNoAndroidAudioSince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            assertFalse("unexpected android_audio event on typed lookup: " + sink.events.get(i).api,
                    "android_audio".equals(sink.events.get(i).kind));
        }
    }

    private static DvmObject<?> resolveAudioSystemService(AbstractJni jni, BaseVM vm,
                                                          boolean useVaList, VM dalvikVm) {
        DvmObject<?> app = dalvikVm.resolveClass("android/app/Application").newObject(null);
        return invokeGetSystemService(jni, vm, useVaList, app, "audio");
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

    private static DvmObject<?> invokeGetSystemServiceClass(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                            DvmObject<?> receiver, DvmClass serviceClass) {
        int classHash = vm.addLocalObject(serviceClass);
        DvmClass dvmClass = receiver.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature, new TestObjectVaList(vm, method, classHash));
        }
        return jni.callObjectMethod(vm, receiver, signature, new TestObjectVarArg(vm, method, classHash));
    }

    private static boolean invokeIsMusicActive(AbstractJni jni, BaseVM vm, boolean useVaList,
                                               DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(AUDIO_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isMusicActive", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsSpeakerphoneOn(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(AUDIO_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isSpeakerphoneOn", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static int invokeGetRingerMode(AbstractJni jni, BaseVM vm, boolean useVaList,
                                           DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(AUDIO_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getRingerMode", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static int invokeGetMode(AbstractJni jni, BaseVM vm, boolean useVaList,
                                     DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(AUDIO_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getMode", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static int invokeGetStreamVolume(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             DvmObject<?> target, int streamType) {
        DvmClass dvmClass = vm.resolveClass(AUDIO_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getStreamVolume", "(I)I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature,
                    new TestIntVaList(vm, method, streamType));
        }
        return jni.callIntMethod(vm, target, signature,
                new TestIntVarArg(vm, method, streamType));
    }

    private static int invokeGetStreamMaxVolume(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmObject<?> target, int streamType) {
        DvmClass dvmClass = vm.resolveClass(AUDIO_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getStreamMaxVolume", "(I)I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature,
                    new TestIntVaList(vm, method, streamType));
        }
        return jni.callIntMethod(vm, target, signature,
                new TestIntVarArg(vm, method, streamType));
    }

    private static int invokeGetStreamMinVolume(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmObject<?> target, int streamType) {
        DvmClass dvmClass = vm.resolveClass(AUDIO_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getStreamMinVolume", "(I)I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature,
                    new TestIntVaList(vm, method, streamType));
        }
        return jni.callIntMethod(vm, target, signature,
                new TestIntVarArg(vm, method, streamType));
    }

    private static void runStreamMinVolumeConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(STREAM_VOLUMES_WITH_MIN_JSON);
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertEquals(1, invokeGetStreamMinVolume(jni, baseVM, useVaList, manager, 3));
            // max/current regression on the same marker
            assertEquals(7, invokeGetStreamVolume(jni, baseVM, useVaList, manager, 3));
            assertEquals(15, invokeGetStreamMaxVolume(jni, baseVM, useVaList, manager, 3));

            CapturedEvent min = findLastEvent(sink.events, "android_audio",
                    "AudioManager.getStreamMinVolume");
            assertNotNull(min);
            assertEquals("json-config", min.source);
            assertEquals("streamType=3,result=1", String.valueOf(min.value));
            assertNotNull(min.note);

            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.getStreamMinVolume"));
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.getStreamVolume"));
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.getStreamMaxVolume"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runStreamMinVolumeZeroLegal(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(STREAM_VOLUMES_WITH_MIN_JSON);
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertEquals(0, invokeGetStreamMinVolume(jni, baseVM, useVaList, manager, 2));
            CapturedEvent min = findLastEvent(sink.events, "android_audio",
                    "AudioManager.getStreamMinVolume");
            assertNotNull(min);
            assertEquals("json-config", min.source);
            assertEquals("streamType=2,result=0", String.valueOf(min.value));
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.getStreamMinVolume"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runStreamMinVolumeUoePaths(boolean is64Bit, boolean useVaList)
            throws Exception {
        // matched entry with minVolume omitted
        runStreamMinVolumeExpectUoe(is64Bit, useVaList, STREAM_VOLUMES_JSON, 3, true);
        // empty streamVolumes
        runStreamMinVolumeExpectUoe(is64Bit, useVaList, STREAM_VOLUMES_EMPTY_JSON, 3, true);
        // unknown streamType
        runStreamMinVolumeExpectUoe(is64Bit, useVaList, STREAM_VOLUMES_WITH_MIN_JSON, 1, true);
        // audio present without streamVolumes
        runStreamMinVolumeExpectUoe(is64Bit, useVaList, AUDIO_EMPTY_JSON, 3, true);
        // no android.audio node
        runStreamMinVolumeExpectUoe(is64Bit, useVaList, NO_AUDIO_JSON, 3, true);

        // plain AudioManager isolation
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(STREAM_VOLUMES_WITH_MIN_JSON);
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

            DvmObject<?> plain = vm.resolveClass(AUDIO_MANAGER_CLASS).newObject(null);
            try {
                invokeGetStreamMinVolume(jni, baseVM, useVaList, plain, 3);
                fail("expected UOE for plain AudioManager getStreamMinVolume");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamMinVolume"));
            }
            DvmObject<?> wifi = new SystemService(baseVM, SystemService.WIFI_SERVICE);
            try {
                invokeGetStreamMinVolume(jni, baseVM, useVaList, wifi, 3);
                fail("expected UOE for non-audio SystemService getStreamMinVolume");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamMinVolume"));
            }
            DvmObject<?> audio = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            try {
                DvmClass dvmClass = audio.getObjectType();
                DvmMethod method = new DvmMethod(dvmClass, "getStreamMinVolume", "()I", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callIntMethodV(baseVM, audio, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callIntMethod(baseVM, audio, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for wrong getStreamMinVolume signature");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamMinVolume"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected getStreamMinVolume event: " + e.api,
                        "AudioManager.getStreamMinVolume".equals(e.api));
            }

            // control: SystemService audio still returns configured min
            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertEquals(1, invokeGetStreamMinVolume(jni, baseVM, useVaList, manager, 3));
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.getStreamMinVolume"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runStreamMinVolumeExpectUoe(boolean is64Bit, boolean useVaList,
                                                    String json, int streamType,
                                                    boolean expectNoMinEvent) throws Exception {
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
            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeGetStreamMinVolume(jni, baseVM, useVaList, manager, streamType);
                fail("expected UOE for getStreamMinVolume");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamMinVolume"));
            }
            if (expectNoMinEvent) {
                assertEquals(0, countEvents(sink.events, "android_audio",
                        "AudioManager.getStreamMinVolume"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runStreamVolumeMinVolumeParseAndAccessors() {
        // omitted minVolume stays unconfigured; max/current accessors unchanged
        TraceEnvironmentConfig omitted = TraceEnvironmentConfig.parse(STREAM_VOLUMES_JSON);
        TraceEnvironmentConfig.AndroidStreamVolumeConfig omittedEntry =
                omitted.getAndroidAudioConfig().findStreamVolume(3);
        assertNotNull(omittedEntry);
        assertFalse(omittedEntry.isMinVolumeConfigured());
        assertEquals(3, omittedEntry.getStreamType());
        assertEquals(7, omittedEntry.getVolume());
        assertEquals(15, omittedEntry.getMaxVolume());

        TraceEnvironmentConfig withMin = TraceEnvironmentConfig.parse(STREAM_VOLUMES_WITH_MIN_JSON);
        TraceEnvironmentConfig.AndroidAudioConfig audio = withMin.getAndroidAudioConfig();
        assertTrue(audio.isStreamVolumesConfigured());
        assertEquals(3, audio.getStreamVolumes().size());

        TraceEnvironmentConfig.AndroidStreamVolumeConfig music = audio.findStreamVolume(3);
        assertNotNull(music);
        assertTrue(music.isMinVolumeConfigured());
        assertEquals(1, music.getMinVolume());
        assertEquals(7, music.getVolume());
        assertEquals(15, music.getMaxVolume());

        TraceEnvironmentConfig.AndroidStreamVolumeConfig voice = audio.findStreamVolume(0);
        assertNotNull(voice);
        assertFalse(voice.isMinVolumeConfigured());
        assertEquals(0, voice.getVolume());
        assertEquals(7, voice.getMaxVolume());

        TraceEnvironmentConfig.AndroidStreamVolumeConfig ring = audio.findStreamVolume(2);
        assertNotNull(ring);
        assertTrue(ring.isMinVolumeConfigured());
        assertEquals(0, ring.getMinVolume());

        // minVolume == volume == maxVolume allowed
        TraceEnvironmentConfig bounds = TraceEnvironmentConfig.parse("{"
                + "\"android\":{\"audio\":{\"streamVolumes\":["
                + "{\"streamType\":3,\"volume\":5,\"maxVolume\":5,\"minVolume\":5}"
                + "]}}}"
        );
        TraceEnvironmentConfig.AndroidStreamVolumeConfig equal =
                bounds.getAndroidAudioConfig().getStreamVolumes().get(0);
        assertTrue(equal.isMinVolumeConfigured());
        assertEquals(5, equal.getMinVolume());
        assertEquals(5, equal.getVolume());
        assertEquals(5, equal.getMaxVolume());

        assertStreamVolumeParseInvalid("{"
                        + "\"android\":{\"audio\":{\"streamVolumes\":["
                        + "{\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":8}"
                        + "]}}}",
                "android.audio.streamVolumes[0].minVolume");
        assertStreamVolumeParseInvalid("{"
                        + "\"android\":{\"audio\":{\"streamVolumes\":["
                        + "{\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":-1}"
                        + "]}}}",
                "android.audio.streamVolumes[0].minVolume");
        assertStreamVolumeParseInvalid("{"
                        + "\"android\":{\"audio\":{\"streamVolumes\":["
                        + "{\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":1.5}"
                        + "]}}}",
                "android.audio.streamVolumes[0].minVolume");
        assertStreamVolumeParseInvalid("{"
                        + "\"android\":{\"audio\":{\"streamVolumes\":["
                        + "{\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":\"0\"}"
                        + "]}}}",
                "android.audio.streamVolumes[0].minVolume");
        assertStreamVolumeParseInvalid("{"
                        + "\"android\":{\"audio\":{\"streamVolumes\":["
                        + "{\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":null}"
                        + "]}}}",
                "android.audio.streamVolumes[0].minVolume");
        assertStreamVolumeParseInvalid("{"
                        + "\"android\":{\"audio\":{\"streamVolumes\":["
                        + "{\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":[]}"
                        + "]}}}",
                "android.audio.streamVolumes[0].minVolume");
        assertStreamVolumeParseInvalid("{"
                        + "\"android\":{\"audio\":{\"streamVolumes\":["
                        + "{\"streamType\":3,\"volume\":7,\"maxVolume\":15,\"minVolume\":0,\"extra\":1}"
                        + "]}}}",
                "android.audio.streamVolumes[0].extra");
        // existing volume<=maxVolume rule unchanged
        assertStreamVolumeParseInvalid("{"
                        + "\"android\":{\"audio\":{\"streamVolumes\":["
                        + "{\"streamType\":3,\"volume\":8,\"maxVolume\":7,\"minVolume\":0}"
                        + "]}}}",
                "android.audio.streamVolumes[0].volume");
    }

    private static void assertStreamVolumeParseInvalid(String json, String expectedPath) {
        try {
            TraceEnvironmentConfig.parse(json);
            fail("expected IllegalArgumentException containing path: " + expectedPath
                    + " for json: " + json);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue("message missing path " + expectedPath + ", was: " + message,
                    message != null && message.contains(expectedPath));
        }
    }

    private static void runStreamVolumesConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(STREAM_VOLUMES_JSON);
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertEquals(7, invokeGetStreamVolume(jni, baseVM, useVaList, manager, 3));
            assertEquals(15, invokeGetStreamMaxVolume(jni, baseVM, useVaList, manager, 3));
            assertEquals(0, invokeGetStreamVolume(jni, baseVM, useVaList, manager, 0));
            assertEquals(7, invokeGetStreamMaxVolume(jni, baseVM, useVaList, manager, 0));
            try {
                invokeGetStreamMinVolume(jni, baseVM, useVaList, manager, 3);
                fail("expected UOE when minVolume omitted");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamMinVolume"));
            }

            CapturedEvent vol = findLastEvent(sink.events, "android_audio",
                    "AudioManager.getStreamVolume");
            assertNotNull(vol);
            assertEquals("json-config", vol.source);
            assertEquals("streamType=0,result=0", String.valueOf(vol.value));
            assertNotNull(vol.note);

            CapturedEvent max = findLastEvent(sink.events, "android_audio",
                    "AudioManager.getStreamMaxVolume");
            assertNotNull(max);
            assertEquals("streamType=0,result=7", String.valueOf(max.value));

            assertEquals(2, countEvents(sink.events, "android_audio",
                    "AudioManager.getStreamVolume"));
            assertEquals(2, countEvents(sink.events, "android_audio",
                    "AudioManager.getStreamMaxVolume"));
            assertEquals(0, countEvents(sink.events, "android_audio",
                    "AudioManager.getStreamMinVolume"));

            // first streamType=3 events present
            boolean sawType3Vol = false;
            for (CapturedEvent e : sink.events) {
                if ("android_audio".equals(e.kind)
                        && "AudioManager.getStreamVolume".equals(e.api)
                        && "streamType=3,result=7".equals(String.valueOf(e.value))) {
                    sawType3Vol = true;
                }
            }
            assertTrue(sawType3Vol);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runStreamVolumesEmptyUnmatched(boolean is64Bit, boolean useVaList)
            throws Exception {
        // empty array configured → unmatched always UOE
        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(STREAM_VOLUMES_EMPTY_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(empty)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeGetStreamVolume(jni, baseVM, useVaList, manager, 3);
                fail("expected UOE for empty streamVolumes");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamVolume"));
            }
            try {
                invokeGetStreamMaxVolume(jni, baseVM, useVaList, manager, 3);
                fail("expected UOE for empty streamVolumes max");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamMaxVolume"));
            }
            try {
                invokeGetStreamMinVolume(jni, baseVM, useVaList, manager, 3);
                fail("expected UOE for empty streamVolumes min");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamMinVolume"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected stream volume event on empty list: " + e.api,
                        "AudioManager.getStreamVolume".equals(e.api)
                                || "AudioManager.getStreamMaxVolume".equals(e.api)
                                || "AudioManager.getStreamMinVolume".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        // non-empty: unmatched streamType UOE, matched still works
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(STREAM_VOLUMES_JSON);
        sink = new CapturingSink();
        emulator = null;
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
            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeGetStreamVolume(jni, baseVM, useVaList, manager, 1);
                fail("expected UOE for unmatched streamType");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamVolume"));
            }
            assertEquals(7, invokeGetStreamVolume(jni, baseVM, useVaList, manager, 3));
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.getStreamVolume"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runStreamVolumesAbsentAndIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        // audio present without streamVolumes key
        TraceEnvironmentConfig noKey = TraceEnvironmentConfig.parse(AUDIO_EMPTY_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(noKey)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeGetStreamVolume(jni, baseVM, useVaList, manager, 3);
                fail("expected UOE without streamVolumes key");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamVolume"));
            }
            // other audio APIs still work
            assertEquals(2, invokeGetRingerMode(jni, baseVM, useVaList, manager));
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected getStreamVolume event: " + e.api,
                        "AudioManager.getStreamVolume".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        // absent audio node
        TraceEnvironmentConfig noAudio = TraceEnvironmentConfig.parse(NO_AUDIO_JSON);
        sink = new CapturingSink();
        emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(noAudio)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeGetStreamMaxVolume(jni, baseVM, useVaList, manager, 3);
                fail("expected UOE without audio node");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamMaxVolume"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        // plain AudioManager + other SystemService isolation
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(STREAM_VOLUMES_JSON);
        sink = new CapturingSink();
        emulator = null;
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

            DvmObject<?> plain = vm.resolveClass(AUDIO_MANAGER_CLASS).newObject(null);
            try {
                invokeGetStreamVolume(jni, baseVM, useVaList, plain, 3);
                fail("expected UOE for plain AudioManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamVolume"));
            }

            DvmObject<?> wifi = new SystemService(baseVM, SystemService.WIFI_SERVICE);
            try {
                invokeGetStreamMaxVolume(jni, baseVM, useVaList, wifi, 3);
                fail("expected UOE for non-audio SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getStreamMaxVolume"));
            }

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertEquals(7, invokeGetStreamVolume(jni, baseVM, useVaList, manager, 3));
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.getStreamVolume"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeGetProperty(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> target, String key) {
        int keyHash = vm.addLocalObject(new StringObject(vm, key));
        DvmClass dvmClass = vm.resolveClass(AUDIO_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getProperty",
                "(Ljava/lang/String;)Ljava/lang/String;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature,
                    new TestObjectVaList(vm, method, keyHash));
        }
        return jni.callObjectMethod(vm, target, signature,
                new TestObjectVarArg(vm, method, keyHash));
    }

    private static void runConfiguredGetProperty(boolean is64Bit, boolean useVaList,
                                                 String key, String expectedValue,
                                                 boolean expectSidecar) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(AUDIO_PROPERTIES_JSON);
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            DvmObject<?> result = invokeGetProperty(jni, baseVM, useVaList, manager, key);
            if (expectedValue == null) {
                assertNull(result);
            } else {
                assertTrue(result instanceof StringObject);
                assertEquals(expectedValue, ((StringObject) result).getValue());
            }
            if (expectSidecar) {
                CapturedEvent ev = findLastEvent(sink.events, "android_audio",
                        "AudioManager.getProperty");
                assertNotNull(ev);
                assertEquals("json-config", ev.source);
                assertEquals("key=" + key + ",result=" + expectedValue, String.valueOf(ev.value));
                assertTrue(ev.note != null && ev.note.contains(key));
                // only this key — no other property keys leaked
                assertFalse(String.valueOf(ev.value).contains(PROP_ABSENT));
                if (!PROP_FRAMES.equals(key)) {
                    assertFalse(String.valueOf(ev.value).contains(PROP_FRAMES));
                }
                if (!PROP_SAMPLE_RATE.equals(key)) {
                    assertFalse(String.valueOf(ev.value).contains(PROP_SAMPLE_RATE));
                }
                assertEquals(1, countEvents(sink.events, "android_audio",
                        "AudioManager.getProperty"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetPropertyAbsentKey(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(AUDIO_PROPERTIES_JSON);
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> result = invokeGetProperty(jni, baseVM, useVaList, manager, PROP_ABSENT);
            assertNull(result);
            assertEquals(0, countEvents(sink.events, "android_audio",
                    "AudioManager.getProperty"));

            // empty properties map: also null, no event
            sink.events.clear();
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        // empty properties {} under present audio node
        config = TraceEnvironmentConfig.parse(AUDIO_EMPTY_WITH_PROPS_EMPTY_JSON);
        sink = new CapturingSink();
        emulator = null;
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
            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            assertNull(invokeGetProperty(jni, baseVM, useVaList, manager, PROP_SAMPLE_RATE));
            assertEquals(0, countEvents(sink.events, "android_audio",
                    "AudioManager.getProperty"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetPropertyNodeMissing(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_AUDIO_JSON);
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

            DvmObject<?> manager = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeGetProperty(jni, baseVM, useVaList, manager, PROP_SAMPLE_RATE);
                fail("expected UOE without audio config for getProperty");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProperty"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_audio event when config absent: " + e.api,
                        "android_audio".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetPropertyIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(AUDIO_PROPERTIES_JSON);
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

            DvmObject<?> plain = vm.resolveClass(AUDIO_MANAGER_CLASS).newObject(null);
            try {
                invokeGetProperty(jni, baseVM, useVaList, plain, PROP_SAMPLE_RATE);
                fail("expected UOE for getProperty on plain AudioManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProperty"));
            }

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> wifi = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            try {
                invokeGetProperty(jni, baseVM, useVaList, wifi, PROP_SAMPLE_RATE);
                fail("expected UOE for getProperty on non-audio SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProperty"));
            }

            // wrong signature on audio marker
            DvmObject<?> audio = resolveAudioSystemService(jni, baseVM, useVaList, vm);
            try {
                DvmClass dvmClass = audio.getObjectType();
                DvmMethod method = new DvmMethod(dvmClass, "getParameters",
                        "(Ljava/lang/String;)Ljava/lang/String;", false);
                int keyHash = baseVM.addLocalObject(new StringObject(baseVM, "x"));
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, audio, signature,
                            new TestObjectVaList(baseVM, method, keyHash));
                } else {
                    jni.callObjectMethod(baseVM, audio, signature,
                            new TestObjectVarArg(baseVM, method, keyHash));
                }
                fail("expected UOE for getParameters");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getParameters"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_audio event on isolation paths: " + e.api,
                        "android_audio".equals(e.kind));
            }

            // control: audio SystemService getProperty still works
            DvmObject<?> ok = invokeGetProperty(jni, baseVM, useVaList, audio, PROP_SAMPLE_RATE);
            assertTrue(ok instanceof StringObject);
            assertEquals("48000", ((StringObject) ok).getValue());
            assertEquals(1, countEvents(sink.events, "android_audio",
                    "AudioManager.getProperty"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
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
        TestIntVarArg(BaseVM vm, DvmMethod method, int int0) {
            super(vm, method);
            args.add(int0);
        }
    }

    private static final class TestIntVaList extends VaList {
        TestIntVaList(BaseVM vm, DvmMethod method, int int0) {
            super(vm, method);
            args.add(int0);
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

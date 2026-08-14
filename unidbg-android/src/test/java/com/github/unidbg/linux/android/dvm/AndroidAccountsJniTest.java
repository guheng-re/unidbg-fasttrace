package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Focused JNI tests for {@code android.accounts} → AccountManager.get / getAccounts / Account fields.
 */
public class AndroidAccountsJniTest {

    private static final String AM_CLASS = "android/accounts/AccountManager";
    private static final String ACCOUNT_CLASS = "android/accounts/Account";
    private static final String GET_ARGS =
            "(Landroid/content/Context;)Landroid/accounts/AccountManager;";
    private static final String GET_ACCOUNTS_ARGS = "()[Landroid/accounts/Account;";
    private static final String GET_ACCOUNTS_BY_TYPE_ARGS =
            "(Ljava/lang/String;)[Landroid/accounts/Account;";
    private static final String NAME_FIELD = "name:Ljava/lang/String;";
    private static final String TYPE_FIELD = "type:Ljava/lang/String;";

    private static final String FULL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accounts\":["
            + "{\"name\":\"alice@demo.com\",\"type\":\"com.google\"},"
            + "{\"name\":\"bob\",\"type\":\"com.demo.account\"}"
            + "]"
            + "}"
            + "}";

    /** Two google accounts then one demo, for order-preserving by-type filter tests. */
    private static final String MULTI_TYPE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accounts\":["
            + "{\"name\":\"alice@demo.com\",\"type\":\"com.google\"},"
            + "{\"name\":\"carol@demo.com\",\"type\":\"com.google\"},"
            + "{\"name\":\"bob\",\"type\":\"com.demo.account\"}"
            + "]"
            + "}"
            + "}";

    private static final String EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"accounts\":[]"
            + "}"
            + "}";

    private static final String ABSENT_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testAccountsFullVarArg32() throws Exception {
        runConfigured(false, false, FULL_JSON, 2, "alice@demo.com", "com.google",
                "bob", "com.demo.account");
    }

    @Test
    public void testAccountsFullVaList64() throws Exception {
        runConfigured(true, true, FULL_JSON, 2, "alice@demo.com", "com.google",
                "bob", "com.demo.account");
    }

    @Test
    public void testAccountsEmptyArrayVarArg32() throws Exception {
        runConfigured(false, false, EMPTY_JSON, 0, null, null, null, null);
    }

    @Test
    public void testAccountsEmptyArrayVaList64() throws Exception {
        runConfigured(true, true, EMPTY_JSON, 0, null, null, null, null);
    }

    @Test
    public void testAccountsAbsentVarArg32() throws Exception {
        runAbsent(false, false);
    }

    @Test
    public void testAccountsAbsentVaList64() throws Exception {
        runAbsent(true, true);
    }

    /**
     * Same config on two emulators: AccountManager/Account markers from A must not authorize B.
     * Owner A still succeeds; B gets UOE and no android_account sidecar.
     */
    @Test
    public void testAccountsCrossVmRejectedVarArg32() throws Exception {
        runCrossVmRejected(false, false);
    }

    @Test
    public void testAccountsCrossVmRejectedVaList64() throws Exception {
        runCrossVmRejected(true, true);
    }

    /** Same-VM get → getAccounts → name/type still works with owner binding. */
    @Test
    public void testAccountsSameVmBoundRegressionVarArg32() throws Exception {
        runConfigured(false, false, FULL_JSON, 2, "alice@demo.com", "com.google",
                "bob", "com.demo.account");
    }

    @Test
    public void testGetAccountsByTypeMatchOrderVarArg32() throws Exception {
        runGetAccountsByType(false, false);
    }

    @Test
    public void testGetAccountsByTypeMatchOrderVaList64() throws Exception {
        runGetAccountsByType(true, true);
    }

    @Test
    public void testGetAccountsByTypeCrossVmRejectedVarArg32() throws Exception {
        runGetAccountsByTypeCrossVmRejected(false, false);
    }

    @Test
    public void testGetAccountsByTypeCrossVmRejectedVaList64() throws Exception {
        runGetAccountsByTypeCrossVmRejected(true, true);
    }

    private static void runGetAccountsByType(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(MULTI_TYPE_JSON);
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

            DvmObject<?> manager = invokeGet(jni, baseVM, useVaList);
            ArrayObject first = invokeGetAccountsByType(jni, baseVM, useVaList, manager, "com.google");
            ArrayObject second = invokeGetAccountsByType(jni, baseVM, useVaList, manager, "com.google");
            assertNotSame(first, second);
            assertEquals(2, first.length());
            assertEquals("alice@demo.com", getStringField(jni, baseVM, first.getValue()[0], NAME_FIELD));
            assertEquals("com.google", getStringField(jni, baseVM, first.getValue()[0], TYPE_FIELD));
            assertEquals("carol@demo.com", getStringField(jni, baseVM, first.getValue()[1], NAME_FIELD));
            assertTrue(first.getValue()[0].getValue().getClass().getName()
                    .contains("ConfiguredAccount"));

            ArrayObject empty = invokeGetAccountsByType(jni, baseVM, useVaList, manager,
                    "com.missing.type");
            assertEquals(0, empty.length());

            assertEquals(3, countEvents(sink.events, "android_account",
                    "AccountManager.getAccountsByType"));
            CapturedEvent last = findLastEvent(sink.events, "android_account",
                    "AccountManager.getAccountsByType");
            assertNotNull(last);
            assertEquals("json-config", last.source);
            assertEquals("type=com.missing.type,count=0", String.valueOf(last.value));
            assertTrue(last.note != null && last.note.contains("类型"));

            CapturedEvent matchEv = sink.events.get(0);
            assertEquals("AccountManager.getAccountsByType", matchEv.api);
            assertEquals("type=com.google,count=2", String.valueOf(matchEv.value));

            // null / non-String arg → UOE, no extra by-type event
            int before = countEvents(sink.events, "android_account",
                    "AccountManager.getAccountsByType");
            try {
                invokeGetAccountsByTypeNullArg(jni, baseVM, useVaList, manager);
                fail("expected UOE for null type arg");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAccountsByType"));
            }
            assertEquals(before, countEvents(sink.events, "android_account",
                    "AccountManager.getAccountsByType"));

            // plain AccountManager → UOE / no event
            DvmObject<?> plainAm = vm.resolveClass(AM_CLASS).newObject(null);
            try {
                invokeGetAccountsByType(jni, baseVM, useVaList, plainAm, "com.google");
                fail("expected UOE for getAccountsByType without marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAccountsByType"));
            }
            assertEquals(before, countEvents(sink.events, "android_account",
                    "AccountManager.getAccountsByType"));

            // unsupported other method still UOE
            try {
                invokeObjectMethod(jni, baseVM, useVaList, manager, AM_CLASS,
                        "addAccountExplicitly",
                        "(Landroid/accounts/Account;Ljava/lang/String;Landroid/os/Bundle;)Z");
                fail("expected UOE for addAccountExplicitly");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("addAccountExplicitly"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetAccountsByTypeCrossVmRejected(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig shared = TraceEnvironmentConfig.parse(FULL_JSON);
        AndroidEmulator emuA = null;
        AndroidEmulator emuB = null;
        CapturingSink sinkB = new CapturingSink();
        try {
            AndroidEmulatorBuilder builderA = is64Bit
                    ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
            AndroidEmulatorBuilder builderB = is64Bit
                    ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
            emuA = builderA.setEnvironmentConfig(shared).build();
            emuB = builderB.setEnvironmentConfig(shared).build();
            TraceEnvironmentEventSink.register(emuB, sinkB);

            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            VM vmA = emuA.createDalvikVM();
            VM vmB = emuB.createDalvikVM();
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = invokeGet(jniA, baseA, useVaList);
            try {
                invokeGetAccountsByType(jniB, baseB, useVaList, managerA, "com.google");
                fail("expected UOE for cross-VM getAccountsByType");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAccountsByType"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("android_account".equals(e.kind));
            }
        } finally {
            if (emuA != null) {
                emuA.close();
            }
            if (emuB != null) {
                TraceEnvironmentEventSink.unregister(emuB, sinkB);
                emuB.close();
            }
        }
    }

    private static void runCrossVmRejected(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig shared = TraceEnvironmentConfig.parse(FULL_JSON);
        AndroidEmulator emuA = null;
        AndroidEmulator emuB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            AndroidEmulatorBuilder builderA = is64Bit
                    ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
            AndroidEmulatorBuilder builderB = is64Bit
                    ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit();
            emuA = builderA.setEnvironmentConfig(shared).build();
            emuB = builderB.setEnvironmentConfig(shared).build();
            TraceEnvironmentEventSink.register(emuA, sinkA);
            TraceEnvironmentEventSink.register(emuB, sinkB);

            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            VM vmA = emuA.createDalvikVM();
            VM vmB = emuB.createDalvikVM();
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = invokeGet(jniA, baseA, useVaList);
            assertNotNull(managerA);
            ArrayObject accountsA = invokeGetAccounts(jniA, baseA, useVaList, managerA);
            assertEquals(2, accountsA.length());
            DvmObject<?> accountA = accountsA.getValue()[0];
            assertEquals("alice@demo.com", getStringField(jniA, baseA, accountA, NAME_FIELD));
            assertEquals("com.google", getStringField(jniA, baseA, accountA, TYPE_FIELD));
            assertEquals(1, countEvents(sinkA.events, "android_account",
                    "AccountManager.getAccounts"));

            // Cross-VM: B must not accept A's AccountManager marker
            try {
                invokeGetAccounts(jniB, baseB, useVaList, managerA);
                fail("expected UnsupportedOperationException for cross-VM AccountManager marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAccounts"));
            }
            // Cross-VM: B must not accept A's Account field marker
            try {
                getStringField(jniB, baseB, accountA, NAME_FIELD);
                fail("expected UnsupportedOperationException for cross-VM Account.name");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("name"));
            }
            try {
                getStringField(jniB, baseB, accountA, TYPE_FIELD);
                fail("expected UnsupportedOperationException for cross-VM Account.type");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("type"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("B must not emit android_account for foreign markers: " + e.api,
                        "android_account".equals(e.kind));
            }
            assertEquals(0, countEvents(sinkB.events, "android_account",
                    "AccountManager.getAccounts"));

            // Owner A still succeeds with same markers
            ArrayObject againA = invokeGetAccounts(jniA, baseA, useVaList, managerA);
            assertEquals(2, againA.length());
            assertEquals(2, countEvents(sinkA.events, "android_account",
                    "AccountManager.getAccounts"));
            assertEquals("alice@demo.com", getStringField(jniA, baseA, accountA, NAME_FIELD));
        } finally {
            if (emuA != null) {
                TraceEnvironmentEventSink.unregister(emuA, sinkA);
                emuA.close();
            }
            if (emuB != null) {
                TraceEnvironmentEventSink.unregister(emuB, sinkB);
                emuB.close();
            }
        }
    }

    private static void runConfigured(boolean is64Bit, boolean useVaList, String json,
                                      int expectedCount,
                                      String name0, String type0,
                                      String name1, String type1) throws Exception {
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

            DvmObject<?> manager = invokeGet(jni, baseVM, useVaList);
            assertNotNull(manager);
            assertEquals(AM_CLASS, manager.getObjectType().getClassName());
            assertTrue(manager.getValue().getClass().getName().contains("ConfiguredAccountManager"));

            // getAccounts emits sidecar; fresh array each call
            ArrayObject first = invokeGetAccounts(jni, baseVM, useVaList, manager);
            ArrayObject second = invokeGetAccounts(jni, baseVM, useVaList, manager);
            assertNotSame(first, second);
            assertEquals(expectedCount, first.length());
            assertEquals(expectedCount, second.length());

            if (expectedCount >= 1) {
                DvmObject<?> acc0 = first.getValue()[0];
                assertTrue(acc0.getValue().getClass().getName().contains("ConfiguredAccount"));
                assertEquals(name0, getStringField(jni, baseVM, acc0, NAME_FIELD));
                assertEquals(type0, getStringField(jni, baseVM, acc0, TYPE_FIELD));
            }
            if (expectedCount >= 2) {
                DvmObject<?> acc1 = first.getValue()[1];
                assertEquals(name1, getStringField(jni, baseVM, acc1, NAME_FIELD));
                assertEquals(type1, getStringField(jni, baseVM, acc1, TYPE_FIELD));
            }

            // unsupported account API on marker → UOE
            try {
                invokeObjectMethod(jni, baseVM, useVaList, manager, AM_CLASS,
                        "addAccountExplicitly",
                        "(Landroid/accounts/Account;Ljava/lang/String;Landroid/os/Bundle;)Z");
                fail("expected UOE for addAccountExplicitly on marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("addAccountExplicitly"));
            }

            // plain AccountManager / Account without marker → UOE / no account event for fields
            DvmObject<?> plainAm = vm.resolveClass(AM_CLASS).newObject(null);
            try {
                invokeGetAccounts(jni, baseVM, useVaList, plainAm);
                fail("expected UOE for getAccounts without marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAccounts"));
            }
            if (expectedCount >= 1) {
                DvmObject<?> plainAccount = vm.resolveClass(ACCOUNT_CLASS).newObject(null);
                try {
                    getStringField(jni, baseVM, plainAccount, NAME_FIELD);
                    fail("expected UOE for Account.name without marker");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("name"));
                }
            }

            int getAccountsEvents = countEvents(sink.events, "android_account",
                    "AccountManager.getAccounts");
            assertEquals(2, getAccountsEvents);
            CapturedEvent last = findLastEvent(sink.events, "android_account",
                    "AccountManager.getAccounts");
            assertNotNull(last);
            assertEquals("json-config", last.source);
            assertEquals("count=" + expectedCount, String.valueOf(last.value));
            assertTrue(last.note != null && last.note.contains("账户"));

            // no sidecar for get or field reads (getAccounts only in this path)
            for (CapturedEvent e : sink.events) {
                if ("android_account".equals(e.kind)) {
                    assertEquals("AccountManager.getAccounts", e.api);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(ABSENT_JSON);
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

            try {
                invokeGet(jni, baseVM, useVaList);
                fail("expected UnsupportedOperationException without accounts config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("AccountManager"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_account event when config absent: " + e.api,
                        "android_account".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeGet(AbstractJni jni, BaseVM vm, boolean useVaList) {
        DvmClass dvmClass = vm.resolveClass(AM_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "get", GET_ARGS, true);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, dvmClass, signature, new TestVaList(vm, method));
        }
        return jni.callStaticObjectMethod(vm, dvmClass, signature, new TestVarArg(vm, method));
    }

    private static ArrayObject invokeGetAccounts(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> manager) {
        DvmObject<?> result = invokeObjectMethod(jni, vm, useVaList, manager, AM_CLASS,
                "getAccounts", GET_ACCOUNTS_ARGS);
        assertTrue(result instanceof ArrayObject);
        return (ArrayObject) result;
    }

    private static ArrayObject invokeGetAccountsByType(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> manager, String type) {
        int typeHash = vm.addLocalObject(new StringObject(vm, type));
        DvmClass dvmClass = vm.resolveClass(AM_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getAccountsByType", GET_ACCOUNTS_BY_TYPE_ARGS, false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, manager, signature,
                    new TestObjectVaList(vm, method, typeHash));
        } else {
            result = jni.callObjectMethod(vm, manager, signature,
                    new TestObjectVarArg(vm, method, typeHash));
        }
        assertTrue(result instanceof ArrayObject);
        return (ArrayObject) result;
    }

    private static DvmObject<?> invokeGetAccountsByTypeNullArg(AbstractJni jni, BaseVM vm,
                                                               boolean useVaList,
                                                               DvmObject<?> manager) {
        DvmClass dvmClass = vm.resolveClass(AM_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getAccountsByType", GET_ACCOUNTS_BY_TYPE_ARGS, false);
        String signature = method.getSignature();
        // arg0 hash 0 → getObject returns null
        if (useVaList) {
            return jni.callObjectMethodV(vm, manager, signature,
                    new TestObjectVaList(vm, method, 0));
        }
        return jni.callObjectMethod(vm, manager, signature, new TestObjectVarArg(vm, method, 0));
    }

    private static String getStringField(AbstractJni jni, BaseVM vm, DvmObject<?> account,
                                         String fieldArgs) {
        DvmClass dvmClass = vm.resolveClass(ACCOUNT_CLASS);
        String signature = ACCOUNT_CLASS + "->" + fieldArgs;
        DvmObject<?> result = jni.getObjectField(vm, account, signature);
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static DvmObject<?> invokeObjectMethod(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmObject<?> receiver, String className,
                                                   String methodName, String args) {
        DvmClass dvmClass = vm.resolveClass(className);
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, receiver, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, receiver, signature, new TestVarArg(vm, method));
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
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }
    }

    private static final class TestObjectVarArg extends VarArg {
        TestObjectVarArg(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
        }
    }

    private static final class TestObjectVaList extends VaList {
        TestObjectVaList(BaseVM vm, DvmMethod method, int objectHash0) {
            super(vm, method);
            args.add(objectHash0);
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

package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.trace.TraceEnvironmentEventSink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidDhcpJniTest {

    /** Android little-endian IPv4 packing: a|(b<<8)|(c<<16)|(d<<24). */
    private static int ipv4(int a, int b, int c, int d) {
        return a | (b << 8) | (c << 16) | (d << 24);
    }

    private static final int IP_192_168_50_23 = ipv4(192, 168, 50, 23);
    private static final int IP_192_168_50_1 = ipv4(192, 168, 50, 1);
    private static final int IP_8_8_8_8 = ipv4(8, 8, 8, 8);
    private static final int IP_1_1_1_1 = ipv4(1, 1, 1, 1);
    private static final int IP_192_168_50_254 = ipv4(192, 168, 50, 254);
    private static final int IP_255_255_255_0 = ipv4(255, 255, 255, 0);

    private static final String DHCP_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"},"
            + "\"network\":{"
            + "\"wifi\":{\"ipv4\":\"192.168.50.23\"},"
            + "\"links\":{"
            + "\"gatewayIpv4\":\"192.168.50.1\","
            + "\"dnsServers\":[\"8.8.8.8\",\"1.1.1.1\"],"
            + "\"dhcpServerIpv4\":\"192.168.50.254\","
            + "\"netmaskIpv4\":\"255.255.255.0\","
            + "\"leaseDurationSeconds\":3600"
            + "}}"
            + "}";

    private static final String DHCP_JSON_B = "{"
            + "\"android\":{\"packageName\":\"com.other.app\"},"
            + "\"network\":{"
            + "\"wifi\":{\"ipv4\":\"10.0.0.5\"},"
            + "\"links\":{\"gatewayIpv4\":\"10.0.0.1\"}"
            + "}"
            + "}";

    @Test
    public void testDhcpJniVarArg32() throws Exception {
        runDhcpJni(false, false);
    }

    @Test
    public void testDhcpJniVaList64() throws Exception {
        runDhcpJni(true, true);
    }

    private static void runDhcpJni(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(DHCP_JSON);
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

            DvmObject<?> wifiManager = vm.resolveClass("android/net/wifi/WifiManager").newObject(null);
            DvmObject<?> dhcpInfo = invokeGetDhcpInfo(jni, baseVM, useVaList, wifiManager);
            assertNotNull(dhcpInfo);
            assertEquals("android/net/DhcpInfo", dhcpInfo.getObjectType().getClassName());

            // all configured fields including netmask
            assertEquals(IP_192_168_50_23, getIntField(jni, baseVM, dhcpInfo, "ipAddress"));
            assertEquals(IP_192_168_50_1, getIntField(jni, baseVM, dhcpInfo, "gateway"));
            assertEquals(IP_8_8_8_8, getIntField(jni, baseVM, dhcpInfo, "dns1"));
            assertEquals(IP_1_1_1_1, getIntField(jni, baseVM, dhcpInfo, "dns2"));
            assertEquals(IP_192_168_50_254, getIntField(jni, baseVM, dhcpInfo, "serverAddress"));
            assertEquals(IP_255_255_255_0, getIntField(jni, baseVM, dhcpInfo, "netmask"));
            assertEquals(3600, getIntField(jni, baseVM, dhcpInfo, "leaseDuration"));

            CapturedEvent netmaskEv = findLastEvent(sink.events, "network_dhcp", "DhcpInfo.netmask");
            assertNotNull(netmaskEv);
            assertEquals("json-config", netmaskEv.source);
            assertEquals("key=netmaskIpv4,config=255.255.255.0,result=" + IP_255_255_255_0,
                    String.valueOf(netmaskEv.value));
            assertNotNull(netmaskEv.note);
            assertFalse(netmaskEv.note.isEmpty());

            // unrelated DhcpInfo provenance must stay UOE and not be treated as json-config
            DvmObject<?> unrelated = vm.resolveClass("android/net/DhcpInfo").newObject("evil.dhcp");
            try {
                getIntField(jni, baseVM, unrelated, "ipAddress");
                fail("expected UnsupportedOperationException for unrelated DhcpInfo.ipAddress");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("DhcpInfo->ipAddress"));
            }
            try {
                getIntField(jni, baseVM, unrelated, "gateway");
                fail("expected UnsupportedOperationException for unrelated DhcpInfo.gateway");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("DhcpInfo->gateway"));
            }
            try {
                getIntField(jni, baseVM, unrelated, "netmask");
                fail("expected UnsupportedOperationException for unrelated DhcpInfo.netmask");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("DhcpInfo->netmask"));
            }
            // configured marker still works after unrelated regression
            assertEquals(IP_192_168_50_23, getIntField(jni, baseVM, dhcpInfo, "ipAddress"));
            assertEquals(IP_255_255_255_0, getIntField(jni, baseVM, dhcpInfo, "netmask"));

            // cross-VM: A's DhcpInfo must not be read via B's config / sink
            TraceEnvironmentConfig configB = TraceEnvironmentConfig.parse(DHCP_JSON_B);
            AndroidEmulator emuB = null;
            CapturingSink sinkB = new CapturingSink();
            try {
                emuB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(configB)
                        .build();
                TraceEnvironmentEventSink.register(emuB, sinkB);
                VM vmB = emuB.createDalvikVM();
                AbstractJni jniB = new AbstractJni() {
                };
                vmB.setJni(jniB);
                BaseVM baseB = (BaseVM) vmB;
                int eventsBeforeReject = sinkB.events.size();
                try {
                    getIntField(jniB, baseB, dhcpInfo, "ipAddress");
                    fail("expected UnsupportedOperationException for cross-VM DhcpInfo.ipAddress");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("DhcpInfo->ipAddress"));
                }
                try {
                    getIntField(jniB, baseB, dhcpInfo, "gateway");
                    fail("expected UnsupportedOperationException for cross-VM DhcpInfo.gateway");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("DhcpInfo->gateway"));
                }
                assertEquals(eventsBeforeReject, sinkB.events.size());
            } finally {
                if (emuB != null) {
                    TraceEnvironmentEventSink.unregister(emuB, sinkB);
                    emuB.close();
                }
            }
            assertEquals(IP_192_168_50_23, getIntField(jni, baseVM, dhcpInfo, "ipAddress"));
            assertEquals(IP_192_168_50_1, getIntField(jni, baseVM, dhcpInfo, "gateway"));

            // one DNS: dns1 packed, dns2 = 0
            TraceEnvironmentConfig oneDnsCfg = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\"},"
                    + "\"network\":{\"links\":{\"dnsServers\":[\"8.8.8.8\"]}}}"
            );
            AndroidEmulator oneDnsEmu = null;
            try {
                oneDnsEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(oneDnsCfg)
                        .build();
                VM oneDnsVm = oneDnsEmu.createDalvikVM();
                AbstractJni oneDnsJni = new AbstractJni() {
                };
                oneDnsVm.setJni(oneDnsJni);
                BaseVM oneDnsBase = (BaseVM) oneDnsVm;
                DvmObject<?> oneDnsWm = oneDnsVm.resolveClass("android/net/wifi/WifiManager").newObject(null);
                DvmObject<?> oneDnsInfo = invokeGetDhcpInfo(oneDnsJni, oneDnsBase, useVaList, oneDnsWm);
                assertNotNull(oneDnsInfo);
                assertEquals(IP_8_8_8_8, getIntField(oneDnsJni, oneDnsBase, oneDnsInfo, "dns1"));
                assertEquals(0, getIntField(oneDnsJni, oneDnsBase, oneDnsInfo, "dns2"));
                try {
                    getIntField(oneDnsJni, oneDnsBase, oneDnsInfo, "ipAddress");
                    fail("expected UnsupportedOperationException for missing wifi.ipv4");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("DhcpInfo->ipAddress"));
                }
                try {
                    getIntField(oneDnsJni, oneDnsBase, oneDnsInfo, "netmask");
                    fail("expected UnsupportedOperationException for missing netmaskIpv4");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("DhcpInfo->netmask"));
                }
            } finally {
                if (oneDnsEmu != null) {
                    oneDnsEmu.close();
                }
            }

            // empty DNS list: dns1/dns2 = 0
            TraceEnvironmentConfig emptyDnsCfg = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\"},"
                    + "\"network\":{\"links\":{\"dnsServers\":[]}}}"
            );
            AndroidEmulator emptyDnsEmu = null;
            try {
                emptyDnsEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(emptyDnsCfg)
                        .build();
                VM emptyDnsVm = emptyDnsEmu.createDalvikVM();
                AbstractJni emptyDnsJni = new AbstractJni() {
                };
                emptyDnsVm.setJni(emptyDnsJni);
                BaseVM emptyDnsBase = (BaseVM) emptyDnsVm;
                DvmObject<?> emptyDnsWm = emptyDnsVm.resolveClass("android/net/wifi/WifiManager").newObject(null);
                DvmObject<?> emptyDnsInfo = invokeGetDhcpInfo(emptyDnsJni, emptyDnsBase, useVaList, emptyDnsWm);
                assertNotNull(emptyDnsInfo);
                assertEquals(0, getIntField(emptyDnsJni, emptyDnsBase, emptyDnsInfo, "dns1"));
                assertEquals(0, getIntField(emptyDnsJni, emptyDnsBase, emptyDnsInfo, "dns2"));
            } finally {
                if (emptyDnsEmu != null) {
                    emptyDnsEmu.close();
                }
            }

            // explicit null IPv4 sources return 0 (including netmask)
            TraceEnvironmentConfig nullIpCfg = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\"},"
                    + "\"network\":{"
                    + "\"wifi\":{\"ipv4\":null},"
                    + "\"links\":{\"gatewayIpv4\":null,\"dhcpServerIpv4\":null,"
                    + "\"netmaskIpv4\":null,\"leaseDurationSeconds\":0}"
                    + "}}"
            );
            AndroidEmulator nullIpEmu = null;
            CapturingSink nullIpSink = new CapturingSink();
            try {
                nullIpEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(nullIpCfg)
                        .build();
                TraceEnvironmentEventSink.register(nullIpEmu, nullIpSink);
                VM nullIpVm = nullIpEmu.createDalvikVM();
                AbstractJni nullIpJni = new AbstractJni() {
                };
                nullIpVm.setJni(nullIpJni);
                BaseVM nullIpBase = (BaseVM) nullIpVm;
                DvmObject<?> nullIpWm = nullIpVm.resolveClass("android/net/wifi/WifiManager").newObject(null);
                DvmObject<?> nullIpInfo = invokeGetDhcpInfo(nullIpJni, nullIpBase, useVaList, nullIpWm);
                assertNotNull(nullIpInfo);
                assertEquals(0, getIntField(nullIpJni, nullIpBase, nullIpInfo, "ipAddress"));
                assertEquals(0, getIntField(nullIpJni, nullIpBase, nullIpInfo, "gateway"));
                assertEquals(0, getIntField(nullIpJni, nullIpBase, nullIpInfo, "serverAddress"));
                assertEquals(0, getIntField(nullIpJni, nullIpBase, nullIpInfo, "netmask"));
                assertEquals(0, getIntField(nullIpJni, nullIpBase, nullIpInfo, "leaseDuration"));
                CapturedEvent nullMaskEv = findLastEvent(nullIpSink.events, "network_dhcp",
                        "DhcpInfo.netmask");
                assertNotNull(nullMaskEv);
                assertEquals("json-config", nullMaskEv.source);
                assertEquals("key=netmaskIpv4,config=null,result=0", String.valueOf(nullMaskEv.value));
                try {
                    getIntField(nullIpJni, nullIpBase, nullIpInfo, "dns1");
                    fail("expected UnsupportedOperationException for missing dnsServers");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("DhcpInfo->dns1"));
                }
            } finally {
                if (nullIpEmu != null) {
                    TraceEnvironmentEventSink.unregister(nullIpEmu, nullIpSink);
                    nullIpEmu.close();
                }
            }

            // only netmask configured: getDhcpInfo succeeds; netmask reads; other fields UOE
            TraceEnvironmentConfig maskOnlyCfg = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\"},"
                    + "\"network\":{\"links\":{\"netmaskIpv4\":\"255.255.255.0\"}}}"
            );
            AndroidEmulator maskOnlyEmu = null;
            CapturingSink maskOnlySink = new CapturingSink();
            try {
                maskOnlyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(maskOnlyCfg)
                        .build();
                TraceEnvironmentEventSink.register(maskOnlyEmu, maskOnlySink);
                VM maskOnlyVm = maskOnlyEmu.createDalvikVM();
                AbstractJni maskOnlyJni = new AbstractJni() {
                };
                maskOnlyVm.setJni(maskOnlyJni);
                BaseVM maskOnlyBase = (BaseVM) maskOnlyVm;
                DvmObject<?> maskOnlyWm = maskOnlyVm.resolveClass("android/net/wifi/WifiManager")
                        .newObject(null);
                DvmObject<?> maskOnlyInfo = invokeGetDhcpInfo(maskOnlyJni, maskOnlyBase, useVaList,
                        maskOnlyWm);
                assertNotNull(maskOnlyInfo);
                assertEquals(IP_255_255_255_0,
                        getIntField(maskOnlyJni, maskOnlyBase, maskOnlyInfo, "netmask"));
                CapturedEvent maskOnlyEv = findLastEvent(maskOnlySink.events, "network_dhcp",
                        "DhcpInfo.netmask");
                assertNotNull(maskOnlyEv);
                assertEquals("json-config", maskOnlyEv.source);
                assertEquals("key=netmaskIpv4,config=255.255.255.0,result=" + IP_255_255_255_0,
                        String.valueOf(maskOnlyEv.value));
                try {
                    getIntField(maskOnlyJni, maskOnlyBase, maskOnlyInfo, "gateway");
                    fail("expected UnsupportedOperationException for missing gatewayIpv4");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("DhcpInfo->gateway"));
                }
            } finally {
                if (maskOnlyEmu != null) {
                    TraceEnvironmentEventSink.unregister(maskOnlyEmu, maskOnlySink);
                    maskOnlyEmu.close();
                }
            }

            // only leaseDuration configured: netmask field missing → UOE; other fields UOE
            TraceEnvironmentConfig leaseOnlyCfg = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\"},"
                    + "\"network\":{\"links\":{\"leaseDurationSeconds\":7200}}}"
            );
            AndroidEmulator leaseOnlyEmu = null;
            try {
                leaseOnlyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(leaseOnlyCfg)
                        .build();
                VM leaseOnlyVm = leaseOnlyEmu.createDalvikVM();
                AbstractJni leaseOnlyJni = new AbstractJni() {
                };
                leaseOnlyVm.setJni(leaseOnlyJni);
                BaseVM leaseOnlyBase = (BaseVM) leaseOnlyVm;
                DvmObject<?> leaseOnlyWm = leaseOnlyVm.resolveClass("android/net/wifi/WifiManager").newObject(null);
                DvmObject<?> leaseOnlyInfo = invokeGetDhcpInfo(leaseOnlyJni, leaseOnlyBase, useVaList, leaseOnlyWm);
                assertNotNull(leaseOnlyInfo);
                assertEquals(7200, getIntField(leaseOnlyJni, leaseOnlyBase, leaseOnlyInfo, "leaseDuration"));
                try {
                    getIntField(leaseOnlyJni, leaseOnlyBase, leaseOnlyInfo, "gateway");
                    fail("expected UnsupportedOperationException for missing gatewayIpv4");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("DhcpInfo->gateway"));
                }
                try {
                    getIntField(leaseOnlyJni, leaseOnlyBase, leaseOnlyInfo, "netmask");
                    fail("expected UnsupportedOperationException for missing netmaskIpv4");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("DhcpInfo->netmask"));
                }
            } finally {
                if (leaseOnlyEmu != null) {
                    leaseOnlyEmu.close();
                }
            }

            // no dhcp-related config at all: getDhcpInfo UOE
            TraceEnvironmentConfig noDhcp = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\"}}");
            AndroidEmulator noDhcpEmu = null;
            try {
                noDhcpEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noDhcp)
                        .build();
                VM noDhcpVm = noDhcpEmu.createDalvikVM();
                AbstractJni noDhcpJni = new AbstractJni() {
                };
                noDhcpVm.setJni(noDhcpJni);
                BaseVM noDhcpBase = (BaseVM) noDhcpVm;
                DvmObject<?> noWm = noDhcpVm.resolveClass("android/net/wifi/WifiManager").newObject(null);
                try {
                    invokeGetDhcpInfo(noDhcpJni, noDhcpBase, useVaList, noWm);
                    fail("expected UnsupportedOperationException without dhcp sources");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("WifiManager->getDhcpInfo"));
                }
            } finally {
                if (noDhcpEmu != null) {
                    noDhcpEmu.close();
                }
            }

            // zero-arg unrelated regression
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmClass appClass = app.getObjectType();
            DvmMethod getPackageName = new DvmMethod(appClass, "getPackageName", "()Ljava/lang/String;", false);
            if (useVaList) {
                DvmObject<?> pkg = jni.callObjectMethodV(baseVM, app, getPackageName.getSignature(),
                        new TestVaList(baseVM, getPackageName));
                assertTrue(pkg instanceof StringObject);
                assertEquals("com.demo.app", ((StringObject) pkg).getValue());
            } else {
                DvmObject<?> pkg = jni.callObjectMethod(baseVM, app, getPackageName.getSignature(),
                        new TestVarArg(baseVM, getPackageName));
                assertTrue(pkg instanceof StringObject);
                assertEquals("com.demo.app", ((StringObject) pkg).getValue());
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeGetDhcpInfo(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> wifiManager) {
        DvmClass dvmClass = wifiManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getDhcpInfo", "()Landroid/net/DhcpInfo;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, wifiManager, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, wifiManager, signature, new TestVarArg(vm, method));
    }

    private static int getIntField(AbstractJni jni, BaseVM vm, DvmObject<?> target, String fieldName) {
        return jni.getIntField(vm, target, "android/net/DhcpInfo->" + fieldName + ":I");
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

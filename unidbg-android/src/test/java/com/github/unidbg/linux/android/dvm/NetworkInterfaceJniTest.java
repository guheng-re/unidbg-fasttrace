package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
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
 * Coverage for {@code network.interfaces} + Java
 * {@code NetworkInterface.getNetworkInterfaces}/{@code getByName}/{@code getByIndex}/
 * {@code getName}/{@code getDisplayName}/{@code getIndex}/{@code getHardwareAddress}/
 * {@code getInetAddresses}/{@code getByInetAddress}/{@code getMTU}/{@code isUp}/{@code isLoopback}/
 * {@code isPointToPoint}/{@code isVirtual}/{@code supportsMulticast}
 * (ConfiguredNetworkInterface markers; VarArg 32 + VaList 64). Does not use host NetworkInterface.
 */
public class NetworkInterfaceJniTest {

    private static final String NETWORK_INTERFACE_CLASS = "java/net/NetworkInterface";
    private static final String GET_NETWORK_INTERFACES_SIGNATURE =
            "java/net/NetworkInterface->getNetworkInterfaces()Ljava/util/Enumeration;";
    private static final String GET_BY_NAME_SIGNATURE =
            "java/net/NetworkInterface->getByName(Ljava/lang/String;)Ljava/net/NetworkInterface;";
    private static final String GET_BY_INDEX_SIGNATURE =
            "java/net/NetworkInterface->getByIndex(I)Ljava/net/NetworkInterface;";
    private static final String GET_BY_INET_ADDRESS_SIGNATURE =
            "java/net/NetworkInterface->getByInetAddress(Ljava/net/InetAddress;)Ljava/net/NetworkInterface;";

    private static final String IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\"}"
            + "]}"
            + "}";

    private static final String IFACES_WITH_MAC_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
            + "\"mac\":\"02:00:00:00:00:01\"},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\","
            + "\"mac\":\"AA:BB:CC:DD:EE:FF\"}"
            + "]}"
            + "}";

    private static final String IFACES_WITH_MTU_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\",\"mtu\":1500},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\",\"mtu\":9000}"
            + "]}"
            + "}";

    private static final String IFACES_WITH_DISPLAY_NAME_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\","
            + "\"displayName\":\"Wireless LAN\"},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\",\"displayName\":null}"
            + "]}"
            + "}";

    private static final String IFACES_WITH_FLAGS_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\",\"flags\":73},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\",\"flags\":0}"
            + "]}"
            + "}";

    private static final String IFACES_WITH_VIRTUAL_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"},"
            + "{\"name\":\"veth0\",\"index\":2,\"ipv4\":\"10.0.0.1\",\"virtual\":true},"
            + "{\"name\":\"wlan0\",\"index\":3,\"ipv4\":\"192.168.1.100\",\"virtual\":false,\"flags\":73}"
            + "]}"
            + "}";

    /** flags=4096 (0x1000 IFF_MULTICAST) vs 73 (no multicast) vs omitted. */
    private static final String IFACES_WITH_MULTICAST_FLAGS_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"},"
            + "{\"name\":\"wlan0\",\"index\":2,\"ipv4\":\"192.168.1.100\",\"flags\":4096},"
            + "{\"name\":\"eth0\",\"index\":3,\"ipv4\":\"10.0.0.2\",\"flags\":73}"
            + "]}"
            + "}";

    /** flags=16 (0x10 IFF_POINTOPOINT) vs 73 (no point-to-point) vs omitted. */
    private static final String IFACES_WITH_POINTOPOINT_FLAGS_JSON = "{"
            + "\"network\":{\"interfaces\":["
            + "{\"name\":\"lo\",\"index\":1,\"ipv4\":\"127.0.0.1\"},"
            + "{\"name\":\"ppp0\",\"index\":2,\"ipv4\":\"10.0.0.1\",\"flags\":16},"
            + "{\"name\":\"wlan0\",\"index\":3,\"ipv4\":\"192.168.1.100\",\"flags\":73}"
            + "]}"
            + "}";

    private static final String EMPTY_IFACES_JSON = "{"
            + "\"network\":{\"interfaces\":[]}"
            + "}";

    private static final String NO_IFACES_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    @Test
    public void testGetNetworkInterfacesOrderAndGettersVarArg32() throws Exception {
        runEnumerationOrderAndGetters(false, false);
    }

    @Test
    public void testGetNetworkInterfacesOrderAndGettersVaList64() throws Exception {
        runEnumerationOrderAndGetters(true, true);
    }

    @Test
    public void testGetHardwareAddressBytesAndNullVarArg32() throws Exception {
        runGetHardwareAddress(false, false);
    }

    @Test
    public void testGetHardwareAddressBytesAndNullVaList64() throws Exception {
        runGetHardwareAddress(true, true);
    }

    @Test
    public void testGetHardwareAddressFreshnessAndIsolationVarArg32() throws Exception {
        runGetHardwareAddressFreshnessAndIsolation(false, false);
    }

    @Test
    public void testGetHardwareAddressFreshnessAndIsolationVaList64() throws Exception {
        runGetHardwareAddressFreshnessAndIsolation(true, true);
    }

    @Test
    public void testGetMTUConfiguredAndAbsentVarArg32() throws Exception {
        runGetMTU(false, false);
    }

    @Test
    public void testGetMTUConfiguredAndAbsentVaList64() throws Exception {
        runGetMTU(true, true);
    }

    @Test
    public void testGetMTUProvenanceStaleMissingVarArg32() throws Exception {
        runGetMTUProvenanceStaleMissing(false, false);
    }

    @Test
    public void testGetMTUProvenanceStaleMissingVaList64() throws Exception {
        runGetMTUProvenanceStaleMissing(true, true);
    }

    @Test
    public void testGetByNameMatchAndMissVarArg32() throws Exception {
        runGetByNameMatchAndMiss(false, false);
    }

    @Test
    public void testGetByNameMatchAndMissVaList64() throws Exception {
        runGetByNameMatchAndMiss(true, true);
    }

    @Test
    public void testGetByNameEmptyAndNullAndMissingVarArg32() throws Exception {
        runGetByNameEmptyNullMissing(false, false);
    }

    @Test
    public void testGetByNameEmptyAndNullAndMissingVaList64() throws Exception {
        runGetByNameEmptyNullMissing(true, true);
    }

    @Test
    public void testGetByIndexMatchAndMissVarArg32() throws Exception {
        runGetByIndexMatchAndMiss(false, false);
    }

    @Test
    public void testGetByIndexMatchAndMissVaList64() throws Exception {
        runGetByIndexMatchAndMiss(true, true);
    }

    @Test
    public void testGetByIndexEmptyNegativeMissingVarArg32() throws Exception {
        runGetByIndexEmptyNegativeMissing(false, false);
    }

    @Test
    public void testGetByIndexEmptyNegativeMissingVaList64() throws Exception {
        runGetByIndexEmptyNegativeMissing(true, true);
    }

    @Test
    public void testGetDisplayNameStringNullOmittedVarArg32() throws Exception {
        runGetDisplayName(false, false);
    }

    @Test
    public void testGetDisplayNameStringNullOmittedVaList64() throws Exception {
        runGetDisplayName(true, true);
    }

    @Test
    public void testGetDisplayNameProvenanceStaleVarArg32() throws Exception {
        runGetDisplayNameProvenanceStale(false, false);
    }

    @Test
    public void testGetDisplayNameProvenanceStaleVaList64() throws Exception {
        runGetDisplayNameProvenanceStale(true, true);
    }

    @Test
    public void testIsUpFlagsTrueFalseAbsentVarArg32() throws Exception {
        runIsUp(false, false);
    }

    @Test
    public void testIsUpFlagsTrueFalseAbsentVaList64() throws Exception {
        runIsUp(true, true);
    }

    @Test
    public void testIsUpProvenanceStaleMissingVarArg32() throws Exception {
        runIsUpProvenanceStaleMissing(false, false);
    }

    @Test
    public void testIsUpProvenanceStaleMissingVaList64() throws Exception {
        runIsUpProvenanceStaleMissing(true, true);
    }

    @Test
    public void testIsLoopbackFlagsTrueFalseAbsentVarArg32() throws Exception {
        runIsLoopback(false, false);
    }

    @Test
    public void testIsLoopbackFlagsTrueFalseAbsentVaList64() throws Exception {
        runIsLoopback(true, true);
    }

    @Test
    public void testIsLoopbackProvenanceStaleMissingVarArg32() throws Exception {
        runIsLoopbackProvenanceStaleMissing(false, false);
    }

    @Test
    public void testIsLoopbackProvenanceStaleMissingVaList64() throws Exception {
        runIsLoopbackProvenanceStaleMissing(true, true);
    }

    @Test
    public void testSupportsMulticastFlagsTrueFalseAbsentVarArg32() throws Exception {
        runSupportsMulticast(false, false);
    }

    @Test
    public void testSupportsMulticastFlagsTrueFalseAbsentVaList64() throws Exception {
        runSupportsMulticast(true, true);
    }

    @Test
    public void testSupportsMulticastProvenanceStaleMissingVarArg32() throws Exception {
        runSupportsMulticastProvenanceStaleMissing(false, false);
    }

    @Test
    public void testSupportsMulticastProvenanceStaleMissingVaList64() throws Exception {
        runSupportsMulticastProvenanceStaleMissing(true, true);
    }

    @Test
    public void testIsPointToPointFlagsTrueFalseAbsentVarArg32() throws Exception {
        runIsPointToPoint(false, false);
    }

    @Test
    public void testIsPointToPointFlagsTrueFalseAbsentVaList64() throws Exception {
        runIsPointToPoint(true, true);
    }

    @Test
    public void testIsPointToPointProvenanceStaleMissingVarArg32() throws Exception {
        runIsPointToPointProvenanceStaleMissing(false, false);
    }

    @Test
    public void testIsPointToPointProvenanceStaleMissingVaList64() throws Exception {
        runIsPointToPointProvenanceStaleMissing(true, true);
    }

    @Test
    public void testIsVirtualConfiguredTrueFalseAbsentVarArg32() throws Exception {
        runIsVirtual(false, false);
    }

    @Test
    public void testIsVirtualConfiguredTrueFalseAbsentVaList64() throws Exception {
        runIsVirtual(true, true);
    }

    @Test
    public void testIsVirtualProvenanceStaleMissingVarArg32() throws Exception {
        runIsVirtualProvenanceStaleMissing(false, false);
    }

    @Test
    public void testIsVirtualProvenanceStaleMissingVaList64() throws Exception {
        runIsVirtualProvenanceStaleMissing(true, true);
    }

    @Test
    public void testGetInetAddressesAndHostAddressVarArg32() throws Exception {
        runGetInetAddressesAndHostAddress(false, false);
    }

    @Test
    public void testGetInetAddressesAndHostAddressVaList64() throws Exception {
        runGetInetAddressesAndHostAddress(true, true);
    }

    @Test
    public void testGetInetAddressesProvenanceStaleMissingVarArg32() throws Exception {
        runGetInetAddressesProvenanceStaleMissing(false, false);
    }

    @Test
    public void testGetInetAddressesProvenanceStaleMissingVaList64() throws Exception {
        runGetInetAddressesProvenanceStaleMissing(true, true);
    }

    @Test
    public void testGetByInetAddressRoundTripVarArg32() throws Exception {
        runGetByInetAddressRoundTrip(false, false);
    }

    @Test
    public void testGetByInetAddressRoundTripVaList64() throws Exception {
        runGetByInetAddressRoundTrip(true, true);
    }

    @Test
    public void testGetByInetAddressNullDnsPlainForeignStaleMissingVarArg32() throws Exception {
        runGetByInetAddressIsolation(false, false);
    }

    @Test
    public void testGetByInetAddressNullDnsPlainForeignStaleMissingVaList64() throws Exception {
        runGetByInetAddressIsolation(true, true);
    }

    @Test
    public void testGetNetworkInterfacesFreshnessVarArg32() throws Exception {
        runEnumerationFreshness(false, false);
    }

    @Test
    public void testGetNetworkInterfacesFreshnessVaList64() throws Exception {
        runEnumerationFreshness(true, true);
    }

    @Test
    public void testGetNetworkInterfacesEmptyVarArg32() throws Exception {
        runEmptyEnumeration(false, false);
    }

    @Test
    public void testGetNetworkInterfacesEmptyVaList64() throws Exception {
        runEmptyEnumeration(true, true);
    }

    @Test
    public void testGetNetworkInterfacesMissingVarArg32() throws Exception {
        runMissing(false, false);
    }

    @Test
    public void testGetNetworkInterfacesMissingVaList64() throws Exception {
        runMissing(true, true);
    }

    @Test
    public void testPlainForeignStaleIsolationVarArg32() throws Exception {
        runPlainForeignStaleIsolation(false, false);
    }

    @Test
    public void testPlainForeignStaleIsolationVaList64() throws Exception {
        runPlainForeignStaleIsolation(true, true);
    }

    private static void runEnumerationOrderAndGetters(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            assertNotNull(enumeration);
            assertTrue(invokeHasMoreElements(jni, baseVM, useVaList, enumeration));

            DvmObject<?> first = invokeNextElement(jni, baseVM, useVaList, enumeration);
            assertNotNull(first);
            assertEquals(NETWORK_INTERFACE_CLASS, first.getObjectType().getClassName());
            assertEquals("lo", invokeGetName(jni, baseVM, useVaList, first));
            assertEquals(1, invokeGetIndex(jni, baseVM, useVaList, first));

            assertTrue(invokeHasMoreElements(jni, baseVM, useVaList, enumeration));
            DvmObject<?> second = invokeNextElement(jni, baseVM, useVaList, enumeration);
            assertEquals("wlan0", invokeGetName(jni, baseVM, useVaList, second));
            assertEquals(2, invokeGetIndex(jni, baseVM, useVaList, second));

            assertFalse(invokeHasMoreElements(jni, baseVM, useVaList, enumeration));

            CapturedEvent listEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getNetworkInterfaces");
            assertNotNull(listEv);
            assertEquals("json-config", listEv.source);
            assertEquals("count=2", String.valueOf(listEv.value));
            assertNotNull(listEv.note);
            assertFalse(listEv.note.isEmpty());

            CapturedEvent nameEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getName");
            assertNotNull(nameEv);
            assertEquals("json-config", nameEv.source);
            assertEquals("name=wlan0", String.valueOf(nameEv.value));

            CapturedEvent indexEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getIndex");
            assertNotNull(indexEv);
            assertEquals("json-config", indexEv.source);
            assertEquals("index=2", String.valueOf(indexEv.value));

            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getNetworkInterfaces"));
            assertEquals(2, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getName"));
            assertEquals(2, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getIndex"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runEnumerationFreshness(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration e1 = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            Enumeration e2 = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            assertTrue("each call returns a fresh Enumeration", e1 != e2);

            DvmObject<?> a0 = invokeNextElement(jni, baseVM, useVaList, e1);
            DvmObject<?> b0 = invokeNextElement(jni, baseVM, useVaList, e2);
            assertTrue("each call creates new ConfiguredNetworkInterface markers", a0 != b0);
            assertEquals("lo", invokeGetName(jni, baseVM, useVaList, a0));
            assertEquals("lo", invokeGetName(jni, baseVM, useVaList, b0));
            assertEquals(1, invokeGetIndex(jni, baseVM, useVaList, a0));

            assertEquals(2, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getNetworkInterfaces"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runEmptyEnumeration(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(EMPTY_IFACES_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            assertNotNull(enumeration);
            assertFalse(invokeHasMoreElements(jni, baseVM, useVaList, enumeration));

            CapturedEvent listEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getNetworkInterfaces");
            assertNotNull(listEv);
            assertEquals("count=0", String.valueOf(listEv.value));
            assertEquals("json-config", listEv.source);
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getNetworkInterfaces"));
            // no host NetworkInterface objects should appear
            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getName"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMissing(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_IFACES_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            try {
                invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
                fail("expected UOE without network.interfaces");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getNetworkInterfaces"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_interface event when missing: " + e.api,
                        "network_interface".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetByNameMatchAndMiss(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            DvmObject<?> wlan0 = invokeGetByName(jni, baseVM, useVaList, niClass, "wlan0");
            assertNotNull(wlan0);
            assertEquals(NETWORK_INTERFACE_CLASS, wlan0.getObjectType().getClassName());
            assertEquals("wlan0", invokeGetName(jni, baseVM, useVaList, wlan0));
            assertEquals(2, invokeGetIndex(jni, baseVM, useVaList, wlan0));

            DvmObject<?> again = invokeGetByName(jni, baseVM, useVaList, niClass, "wlan0");
            assertNotNull(again);
            assertTrue("each getByName match must return a fresh marker", wlan0 != again);
            assertEquals("wlan0", invokeGetName(jni, baseVM, useVaList, again));

            DvmObject<?> lo = invokeGetByName(jni, baseVM, useVaList, niClass, "lo");
            assertNotNull(lo);
            assertEquals("lo", invokeGetName(jni, baseVM, useVaList, lo));
            assertEquals(1, invokeGetIndex(jni, baseVM, useVaList, lo));

            CapturedEvent foundEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getByName");
            assertNotNull(foundEv);
            assertEquals("json-config", foundEv.source);
            assertEquals("name=lo,result=found", String.valueOf(foundEv.value));
            assertNotNull(foundEv.note);
            assertFalse(foundEv.note.isEmpty());

            assertNull(invokeGetByName(jni, baseVM, useVaList, niClass, "eth0"));
            CapturedEvent missEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getByName");
            assertNotNull(missEv);
            assertEquals("name=eth0,result=null", String.valueOf(missEv.value));
            assertEquals("json-config", missEv.source);

            assertEquals(4, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getByName"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetByNameEmptyNullMissing(boolean is64Bit, boolean useVaList)
            throws Exception {
        // empty array → handled null for any name
        TraceEnvironmentConfig emptyConfig = TraceEnvironmentConfig.parse(EMPTY_IFACES_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(emptyConfig)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            assertNull(invokeGetByName(jni, baseVM, useVaList, niClass, "wlan0"));
            CapturedEvent emptyEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getByName");
            assertNotNull(emptyEv);
            assertEquals("name=wlan0,result=null", String.valueOf(emptyEv.value));
            assertEquals("json-config", emptyEv.source);

            // null name → NPE, no additional event
            int before = countEvents(sink.events, "network_interface",
                    "NetworkInterface.getByName");
            try {
                invokeGetByName(jni, baseVM, useVaList, niClass, null);
                fail("expected NullPointerException for getByName(null)");
            } catch (NullPointerException expected) {
                // Java contract
            }
            assertEquals(before, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getByName"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        // missing network.interfaces → UOE, no event
        TraceEnvironmentConfig missingConfig = TraceEnvironmentConfig.parse(NO_IFACES_JSON);
        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(missingConfig)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            try {
                invokeGetByName(jni, baseVM, useVaList, niClass, "wlan0");
                fail("expected UOE without network.interfaces");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getByName"));
            }
            // null when unconfigured also UOE (not NPE)
            try {
                invokeGetByName(jni, baseVM, useVaList, niClass, null);
                fail("expected UOE for getByName(null) without network.interfaces");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getByName"));
            } catch (NullPointerException unexpected) {
                fail("null arg without config must not apply Java NPE path: " + unexpected);
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_interface event when missing: " + e.api,
                        "network_interface".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetByInetAddressRoundTrip(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            DvmObject<?> wlan0 = invokeGetByName(jni, baseVM, useVaList, niClass, "wlan0");
            Enumeration addrs = invokeGetInetAddresses(jni, baseVM, useVaList, wlan0);
            DvmObject<?> inet = invokeNextElement(jni, baseVM, useVaList, addrs);

            DvmObject<?> byAddr1 = invokeGetByInetAddress(jni, baseVM, useVaList, niClass, inet);
            assertNotNull(byAddr1);
            assertEquals(NETWORK_INTERFACE_CLASS, byAddr1.getObjectType().getClassName());
            assertEquals("wlan0", invokeGetName(jni, baseVM, useVaList, byAddr1));
            assertEquals(2, invokeGetIndex(jni, baseVM, useVaList, byAddr1));
            assertTrue("getByInetAddress must return a fresh interface marker", byAddr1 != wlan0);

            DvmObject<?> byAddr2 = invokeGetByInetAddress(jni, baseVM, useVaList, niClass, inet);
            assertNotNull(byAddr2);
            assertTrue("each getByInetAddress must return a fresh marker", byAddr1 != byAddr2);
            assertEquals("wlan0", invokeGetName(jni, baseVM, useVaList, byAddr2));

            // round-trip still yields usable getInetAddresses
            Enumeration again = invokeGetInetAddresses(jni, baseVM, useVaList, byAddr2);
            assertEquals("192.168.1.100",
                    invokeGetHostAddress(jni, baseVM, useVaList,
                            invokeNextElement(jni, baseVM, useVaList, again)));

            CapturedEvent ev = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getByInetAddress");
            // last getByInetAddress was for wlan0 before the second getInetAddresses...
            // findLast for getByInetAddress after two calls
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("address=192.168.1.100,result=found", String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(2, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getByInetAddress"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetByInetAddressIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            DvmObject<?> wlan0 = invokeGetByName(jni, baseVM, useVaList, niClass, "wlan0");
            Enumeration addrs = invokeGetInetAddresses(jni, baseVM, useVaList, wlan0);
            DvmObject<?> inet = invokeNextElement(jni, baseVM, useVaList, addrs);

            // null arg → NPE, no getByInetAddress event
            try {
                invokeGetByInetAddress(jni, baseVM, useVaList, niClass, null);
                fail("expected NullPointerException for getByInetAddress(null)");
            } catch (NullPointerException expected) {
                // Java contract
            }
            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getByInetAddress"));

            // plain InetAddress
            DvmObject<?> plainInet = vm.resolveClass("java/net/InetAddress")
                    .newObject("192.168.1.100");
            try {
                invokeGetByInetAddress(jni, baseVM, useVaList, niClass, plainInet);
                fail("expected UOE for plain InetAddress");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getByInetAddress"));
            }

            // DNS marker (ConfiguredLinkDnsInetAddress style is private; use links-style
            // newObject with a non-interface marker won't match — plain String already covered.
            // Foreign VM address marker:
            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> foreignNi = invokeGetByName(jni2, baseVM2, useVaList,
                        vm2.resolveClass(NETWORK_INTERFACE_CLASS), "wlan0");
                Enumeration foreignAddrs =
                        invokeGetInetAddresses(jni2, baseVM2, useVaList, foreignNi);
                DvmObject<?> foreignInet =
                        invokeNextElement(jni2, baseVM2, useVaList, foreignAddrs);
                try {
                    invokeGetByInetAddress(jni, baseVM, useVaList, niClass, foreignInet);
                    fail("expected UOE for foreign InetAddress marker");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getByInetAddress"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getByInetAddress"));

            // control: live works
            DvmObject<?> found = invokeGetByInetAddress(jni, baseVM, useVaList, niClass, inet);
            assertEquals("wlan0", invokeGetName(jni, baseVM, useVaList, found));
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getByInetAddress"));

            // stale address marker after re-parse
            TraceEnvironmentConfig config2 = TraceEnvironmentConfig.parse(IFACES_JSON);
            emulator.set(TraceEnvironmentConfig.KEY, config2);
            try {
                invokeGetByInetAddress(jni, baseVM, useVaList, niClass, inet);
                fail("expected UOE for stale InetAddress marker");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getByInetAddress"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getByInetAddress"));

            // missing interfaces → UOE (including null, not NPE)
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(NO_IFACES_JSON));
            try {
                invokeGetByInetAddress(jni, baseVM, useVaList, niClass, inet);
                fail("expected UOE without network.interfaces");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getByInetAddress"));
            }
            try {
                invokeGetByInetAddress(jni, baseVM, useVaList, niClass, null);
                fail("expected UOE for null without network.interfaces");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getByInetAddress"));
            } catch (NullPointerException unexpected) {
                fail("null without config must not apply Java NPE path: " + unexpected);
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getByInetAddress"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetInetAddressesAndHostAddress(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            DvmObject<?> wlan0 = invokeGetByName(jni, baseVM, useVaList, niClass, "wlan0");
            assertNotNull(wlan0);

            Enumeration addrs1 = invokeGetInetAddresses(jni, baseVM, useVaList, wlan0);
            assertNotNull(addrs1);
            assertTrue(invokeHasMoreElements(jni, baseVM, useVaList, addrs1));
            DvmObject<?> inet1 = invokeNextElement(jni, baseVM, useVaList, addrs1);
            assertNotNull(inet1);
            assertEquals("java/net/InetAddress", inet1.getObjectType().getClassName());
            assertFalse(invokeHasMoreElements(jni, baseVM, useVaList, addrs1));

            assertEquals("192.168.1.100", invokeGetHostAddress(jni, baseVM, useVaList, inet1));

            Enumeration addrs2 = invokeGetInetAddresses(jni, baseVM, useVaList, wlan0);
            assertTrue("each getInetAddresses must return a fresh Enumeration", addrs1 != addrs2);
            DvmObject<?> inet2 = invokeNextElement(jni, baseVM, useVaList, addrs2);
            assertTrue("each getInetAddresses must create a fresh InetAddress marker", inet1 != inet2);
            assertEquals("192.168.1.100", invokeGetHostAddress(jni, baseVM, useVaList, inet2));

            DvmObject<?> lo = invokeGetByName(jni, baseVM, useVaList, niClass, "lo");
            Enumeration loAddrs = invokeGetInetAddresses(jni, baseVM, useVaList, lo);
            DvmObject<?> loInet = invokeNextElement(jni, baseVM, useVaList, loAddrs);
            assertEquals("127.0.0.1", invokeGetHostAddress(jni, baseVM, useVaList, loInet));

            CapturedEvent listEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getInetAddresses");
            assertNotNull(listEv);
            assertEquals("json-config", listEv.source);
            assertEquals("count=1,addresses=[127.0.0.1]", String.valueOf(listEv.value));
            assertNotNull(listEv.note);
            assertFalse(listEv.note.isEmpty());

            CapturedEvent hostEv = findLastEvent(sink.events, "network_interface",
                    "InetAddress.getHostAddress");
            assertNotNull(hostEv);
            assertEquals("json-config", hostEv.source);
            assertEquals("address=127.0.0.1", String.valueOf(hostEv.value));
            assertNotNull(hostEv.note);
            assertFalse(hostEv.note.isEmpty());

            // plain InetAddress is not the network-interface marker (and not DNS) → UOE
            DvmObject<?> plainInet = vm.resolveClass("java/net/InetAddress").newObject("10.0.0.1");
            try {
                invokeGetHostAddress(jni, baseVM, useVaList, plainInet);
                fail("expected UOE for plain InetAddress getHostAddress");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getHostAddress"));
            }

            assertEquals(3, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getInetAddresses"));
            assertEquals(3, countEvents(sink.events, "network_interface",
                    "InetAddress.getHostAddress"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetInetAddressesProvenanceStaleMissing(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            DvmObject<?> wlan0 = invokeGetByName(jni, baseVM, useVaList, niClass, "wlan0");
            Enumeration addrs = invokeGetInetAddresses(jni, baseVM, useVaList, wlan0);
            DvmObject<?> inet = invokeNextElement(jni, baseVM, useVaList, addrs);

            // plain NetworkInterface
            DvmObject<?> plain = niClass.newObject(null);
            try {
                invokeGetInetAddresses(jni, baseVM, useVaList, plain);
                fail("expected UOE for getInetAddresses on plain NetworkInterface");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInetAddresses"));
            }

            // foreign interface + foreign inet address
            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> foreignNi = invokeGetByName(jni2, baseVM2, useVaList,
                        vm2.resolveClass(NETWORK_INTERFACE_CLASS), "wlan0");
                try {
                    invokeGetInetAddresses(jni, baseVM, useVaList, foreignNi);
                    fail("expected UOE for foreign getInetAddresses");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getInetAddresses"));
                }
                Enumeration foreignAddrs = invokeGetInetAddresses(jni2, baseVM2, useVaList, foreignNi);
                DvmObject<?> foreignInet = invokeNextElement(jni2, baseVM2, useVaList, foreignAddrs);
                try {
                    invokeGetHostAddress(jni, baseVM, useVaList, foreignInet);
                    fail("expected UOE for foreign InetAddress getHostAddress");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getHostAddress"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            int listBefore = countEvents(sink.events, "network_interface",
                    "NetworkInterface.getInetAddresses");
            int hostBefore = countEvents(sink.events, "network_interface",
                    "InetAddress.getHostAddress");
            assertEquals("192.168.1.100", invokeGetHostAddress(jni, baseVM, useVaList, inet));
            assertEquals(hostBefore + 1, countEvents(sink.events, "network_interface",
                    "InetAddress.getHostAddress"));

            // stale config: interface and address markers become UOE
            TraceEnvironmentConfig config2 = TraceEnvironmentConfig.parse(IFACES_JSON);
            emulator.set(TraceEnvironmentConfig.KEY, config2);
            try {
                invokeGetInetAddresses(jni, baseVM, useVaList, wlan0);
                fail("expected UOE for stale getInetAddresses");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInetAddresses"));
            }
            try {
                invokeGetHostAddress(jni, baseVM, useVaList, inet);
                fail("expected UOE for stale InetAddress getHostAddress");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getHostAddress"));
            }
            assertEquals(listBefore, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getInetAddresses"));
            assertEquals(hostBefore + 1, countEvents(sink.events, "network_interface",
                    "InetAddress.getHostAddress"));

            // missing interfaces
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(NO_IFACES_JSON));
            try {
                invokeGetInetAddresses(jni, baseVM, useVaList, wlan0);
                fail("expected UOE for getInetAddresses after config removed");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getInetAddresses"));
            }
            try {
                invokeGetHostAddress(jni, baseVM, useVaList, inet);
                fail("expected UOE for getHostAddress after config removed");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getHostAddress"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsVirtual(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_VIRTUAL_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            DvmObject<?> lo = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> veth0 = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            // virtual omitted → UOE, no event
            try {
                invokeIsVirtual(jni, baseVM, useVaList, lo);
                fail("expected UOE for isVirtual when virtual absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isVirtual"));
            }
            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isVirtual"));

            // virtual=true
            assertTrue(invokeIsVirtual(jni, baseVM, useVaList, veth0));
            CapturedEvent trueEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.isVirtual");
            assertNotNull(trueEv);
            assertEquals("json-config", trueEv.source);
            assertEquals("name=veth0,result=true", String.valueOf(trueEv.value));
            assertNotNull(trueEv.note);
            assertFalse(trueEv.note.isEmpty());

            // virtual=false (independent of flags)
            assertFalse(invokeIsVirtual(jni, baseVM, useVaList, wlan0));
            CapturedEvent falseEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.isVirtual");
            assertNotNull(falseEv);
            assertEquals("name=wlan0,result=false", String.valueOf(falseEv.value));

            assertEquals(2, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isVirtual"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsVirtualProvenanceStaleMissing(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_VIRTUAL_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            invokeNextElement(jni, baseVM, useVaList, enumeration); // lo
            DvmObject<?> veth0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            // plain NetworkInterface
            DvmObject<?> plain = niClass.newObject(null);
            try {
                invokeIsVirtual(jni, baseVM, useVaList, plain);
                fail("expected UOE for isVirtual on plain NetworkInterface");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isVirtual"));
            }

            // live marker still works
            assertTrue(invokeIsVirtual(jni, baseVM, useVaList, veth0));
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isVirtual"));

            // stale: re-parse same JSON → new config identity
            emulator.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(IFACES_WITH_VIRTUAL_JSON));
            try {
                invokeIsVirtual(jni, baseVM, useVaList, veth0);
                fail("expected UOE for stale isVirtual");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isVirtual"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isVirtual"));

            // config removed
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(NO_IFACES_JSON));
            try {
                invokeIsVirtual(jni, baseVM, useVaList, veth0);
                fail("expected UOE for isVirtual after config removed");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isVirtual"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isVirtual"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsPointToPoint(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_POINTOPOINT_FLAGS_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            DvmObject<?> lo = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> ppp0 = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            // flags omitted → UOE, no event
            try {
                invokeIsPointToPoint(jni, baseVM, useVaList, lo);
                fail("expected UOE for isPointToPoint when flags absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isPointToPoint"));
            }
            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isPointToPoint"));

            // flags=16 (0x10 IFF_POINTOPOINT) → true
            assertTrue(invokeIsPointToPoint(jni, baseVM, useVaList, ppp0));
            CapturedEvent ptpEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.isPointToPoint");
            assertNotNull(ptpEv);
            assertEquals("json-config", ptpEv.source);
            assertEquals("name=ppp0,flags=16,result=true", String.valueOf(ptpEv.value));
            assertNotNull(ptpEv.note);
            assertFalse(ptpEv.note.isEmpty());

            // flags=73 (no IFF_POINTOPOINT) → false
            assertFalse(invokeIsPointToPoint(jni, baseVM, useVaList, wlan0));
            CapturedEvent nonPtpEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.isPointToPoint");
            assertNotNull(nonPtpEv);
            assertEquals("name=wlan0,flags=73,result=false", String.valueOf(nonPtpEv.value));
            assertEquals("json-config", nonPtpEv.source);

            assertEquals(2, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isPointToPoint"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsPointToPointProvenanceStaleMissing(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_POINTOPOINT_FLAGS_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            invokeNextElement(jni, baseVM, useVaList, enumeration); // lo
            DvmObject<?> ppp0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            DvmObject<?> plain = niClass.newObject(null);
            try {
                invokeIsPointToPoint(jni, baseVM, useVaList, plain);
                fail("expected UOE for isPointToPoint on plain NetworkInterface");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isPointToPoint"));
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                Enumeration foreignEnum = invokeGetNetworkInterfaces(jni2, baseVM2, useVaList,
                        vm2.resolveClass(NETWORK_INTERFACE_CLASS));
                invokeNextElement(jni2, baseVM2, useVaList, foreignEnum);
                DvmObject<?> foreign = invokeNextElement(jni2, baseVM2, useVaList, foreignEnum);
                try {
                    invokeIsPointToPoint(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for foreign isPointToPoint");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("isPointToPoint"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isPointToPoint"));

            assertTrue(invokeIsPointToPoint(jni, baseVM, useVaList, ppp0));
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isPointToPoint"));

            TraceEnvironmentConfig config2 =
                    TraceEnvironmentConfig.parse(IFACES_WITH_POINTOPOINT_FLAGS_JSON);
            emulator.set(TraceEnvironmentConfig.KEY, config2);
            try {
                invokeIsPointToPoint(jni, baseVM, useVaList, ppp0);
                fail("expected UOE for stale isPointToPoint");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isPointToPoint"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isPointToPoint"));

            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(NO_IFACES_JSON));
            try {
                invokeIsPointToPoint(jni, baseVM, useVaList, ppp0);
                fail("expected UOE for isPointToPoint after config removed");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isPointToPoint"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isPointToPoint"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSupportsMulticast(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_MULTICAST_FLAGS_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            DvmObject<?> lo = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> eth0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            // flags omitted → UOE, no event
            try {
                invokeSupportsMulticast(jni, baseVM, useVaList, lo);
                fail("expected UOE for supportsMulticast when flags absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsMulticast"));
            }
            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.supportsMulticast"));

            // flags=4096 (0x1000 IFF_MULTICAST) → true
            assertTrue(invokeSupportsMulticast(jni, baseVM, useVaList, wlan0));
            CapturedEvent multiEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.supportsMulticast");
            assertNotNull(multiEv);
            assertEquals("json-config", multiEv.source);
            assertEquals("flags=4096,result=true", String.valueOf(multiEv.value));
            assertNotNull(multiEv.note);
            assertFalse(multiEv.note.isEmpty());

            // flags=73 (no IFF_MULTICAST) → false
            assertFalse(invokeSupportsMulticast(jni, baseVM, useVaList, eth0));
            CapturedEvent noMultiEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.supportsMulticast");
            assertNotNull(noMultiEv);
            assertEquals("flags=73,result=false", String.valueOf(noMultiEv.value));
            assertEquals("json-config", noMultiEv.source);

            assertEquals(2, countEvents(sink.events, "network_interface",
                    "NetworkInterface.supportsMulticast"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSupportsMulticastProvenanceStaleMissing(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_MULTICAST_FLAGS_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            invokeNextElement(jni, baseVM, useVaList, enumeration); // lo
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            DvmObject<?> plain = niClass.newObject(null);
            try {
                invokeSupportsMulticast(jni, baseVM, useVaList, plain);
                fail("expected UOE for supportsMulticast on plain NetworkInterface");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsMulticast"));
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                Enumeration foreignEnum = invokeGetNetworkInterfaces(jni2, baseVM2, useVaList,
                        vm2.resolveClass(NETWORK_INTERFACE_CLASS));
                invokeNextElement(jni2, baseVM2, useVaList, foreignEnum);
                DvmObject<?> foreign = invokeNextElement(jni2, baseVM2, useVaList, foreignEnum);
                try {
                    invokeSupportsMulticast(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for foreign supportsMulticast");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("supportsMulticast"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.supportsMulticast"));

            assertTrue(invokeSupportsMulticast(jni, baseVM, useVaList, wlan0));
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.supportsMulticast"));

            TraceEnvironmentConfig config2 =
                    TraceEnvironmentConfig.parse(IFACES_WITH_MULTICAST_FLAGS_JSON);
            emulator.set(TraceEnvironmentConfig.KEY, config2);
            try {
                invokeSupportsMulticast(jni, baseVM, useVaList, wlan0);
                fail("expected UOE for stale supportsMulticast");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsMulticast"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.supportsMulticast"));

            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(NO_IFACES_JSON));
            try {
                invokeSupportsMulticast(jni, baseVM, useVaList, wlan0);
                fail("expected UOE for supportsMulticast after config removed");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsMulticast"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.supportsMulticast"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsLoopback(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_FLAGS_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            DvmObject<?> lo = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> eth0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            // flags omitted → UOE, no event (not inferred from name "lo")
            try {
                invokeIsLoopback(jni, baseVM, useVaList, lo);
                fail("expected UOE for isLoopback when flags absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isLoopback"));
            }
            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isLoopback"));
            assertEquals("lo", invokeGetName(jni, baseVM, useVaList, lo));

            // flags=73 (0x49) has IFF_LOOPBACK (0x8) → true
            assertTrue(invokeIsLoopback(jni, baseVM, useVaList, wlan0));
            CapturedEvent loopEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.isLoopback");
            assertNotNull(loopEv);
            assertEquals("json-config", loopEv.source);
            assertEquals("flags=73,result=true", String.valueOf(loopEv.value));
            assertNotNull(loopEv.note);
            assertFalse(loopEv.note.isEmpty());

            // flags=0 → false
            assertFalse(invokeIsLoopback(jni, baseVM, useVaList, eth0));
            CapturedEvent nonLoopEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.isLoopback");
            assertNotNull(nonLoopEv);
            assertEquals("flags=0,result=false", String.valueOf(nonLoopEv.value));
            assertEquals("json-config", nonLoopEv.source);

            assertEquals(2, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isLoopback"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsLoopbackProvenanceStaleMissing(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_FLAGS_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            invokeNextElement(jni, baseVM, useVaList, enumeration); // lo
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            DvmObject<?> plain = niClass.newObject(null);
            try {
                invokeIsLoopback(jni, baseVM, useVaList, plain);
                fail("expected UOE for isLoopback on plain NetworkInterface");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isLoopback"));
            }

            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                Enumeration foreignEnum = invokeGetNetworkInterfaces(jni2, baseVM2, useVaList,
                        vm2.resolveClass(NETWORK_INTERFACE_CLASS));
                invokeNextElement(jni2, baseVM2, useVaList, foreignEnum);
                DvmObject<?> foreign = invokeNextElement(jni2, baseVM2, useVaList, foreignEnum);
                try {
                    invokeIsLoopback(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for foreign isLoopback");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("isLoopback"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isLoopback"));

            assertTrue(invokeIsLoopback(jni, baseVM, useVaList, wlan0));
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isLoopback"));

            TraceEnvironmentConfig config2 = TraceEnvironmentConfig.parse(IFACES_WITH_FLAGS_JSON);
            emulator.set(TraceEnvironmentConfig.KEY, config2);
            try {
                invokeIsLoopback(jni, baseVM, useVaList, wlan0);
                fail("expected UOE for stale isLoopback");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isLoopback"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isLoopback"));

            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(NO_IFACES_JSON));
            try {
                invokeIsLoopback(jni, baseVM, useVaList, wlan0);
                fail("expected UOE for isLoopback after config removed");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isLoopback"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isLoopback"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsUp(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_FLAGS_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            DvmObject<?> lo = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> eth0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            // flags omitted → UOE, no event
            try {
                invokeIsUp(jni, baseVM, useVaList, lo);
                fail("expected UOE for isUp when flags absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isUp"));
            }
            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isUp"));

            // flags=73 (0x49) has IFF_UP bit → true
            assertTrue(invokeIsUp(jni, baseVM, useVaList, wlan0));
            CapturedEvent upEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.isUp");
            assertNotNull(upEv);
            assertEquals("json-config", upEv.source);
            assertEquals("flags=73,result=true", String.valueOf(upEv.value));
            assertNotNull(upEv.note);
            assertFalse(upEv.note.isEmpty());

            // flags=0 → false
            assertFalse(invokeIsUp(jni, baseVM, useVaList, eth0));
            CapturedEvent downEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.isUp");
            assertNotNull(downEv);
            assertEquals("flags=0,result=false", String.valueOf(downEv.value));
            assertEquals("json-config", downEv.source);

            assertEquals(2, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isUp"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsUpProvenanceStaleMissing(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_FLAGS_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            invokeNextElement(jni, baseVM, useVaList, enumeration); // lo
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            // plain
            DvmObject<?> plain = niClass.newObject(null);
            try {
                invokeIsUp(jni, baseVM, useVaList, plain);
                fail("expected UOE for isUp on plain NetworkInterface");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isUp"));
            }

            // foreign
            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                Enumeration foreignEnum = invokeGetNetworkInterfaces(jni2, baseVM2, useVaList,
                        vm2.resolveClass(NETWORK_INTERFACE_CLASS));
                invokeNextElement(jni2, baseVM2, useVaList, foreignEnum);
                DvmObject<?> foreign = invokeNextElement(jni2, baseVM2, useVaList, foreignEnum);
                try {
                    invokeIsUp(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for foreign isUp");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("isUp"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isUp"));

            assertTrue(invokeIsUp(jni, baseVM, useVaList, wlan0));
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isUp"));

            // stale
            TraceEnvironmentConfig config2 = TraceEnvironmentConfig.parse(IFACES_WITH_FLAGS_JSON);
            emulator.set(TraceEnvironmentConfig.KEY, config2);
            try {
                invokeIsUp(jni, baseVM, useVaList, wlan0);
                fail("expected UOE for stale isUp");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isUp"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isUp"));

            // missing interfaces
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(NO_IFACES_JSON));
            try {
                invokeIsUp(jni, baseVM, useVaList, wlan0);
                fail("expected UOE for isUp after config removed");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isUp"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.isUp"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDisplayName(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_DISPLAY_NAME_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            DvmObject<?> lo = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> eth0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            // omitted displayName → UOE, no event
            try {
                invokeGetDisplayName(jni, baseVM, useVaList, lo);
                fail("expected UOE for getDisplayName when displayName omitted");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDisplayName"));
            }
            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getDisplayName"));
            // not inferred from name
            assertEquals("lo", invokeGetName(jni, baseVM, useVaList, lo));

            assertEquals("Wireless LAN", invokeGetDisplayName(jni, baseVM, useVaList, wlan0));
            CapturedEvent strEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getDisplayName");
            assertNotNull(strEv);
            assertEquals("json-config", strEv.source);
            assertEquals("displayName=Wireless LAN", String.valueOf(strEv.value));
            assertNotNull(strEv.note);
            assertFalse(strEv.note.isEmpty());

            assertNull(invokeGetDisplayName(jni, baseVM, useVaList, eth0));
            CapturedEvent nullEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getDisplayName");
            assertNotNull(nullEv);
            assertEquals("displayName=null", String.valueOf(nullEv.value));
            assertEquals("json-config", nullEv.source);

            assertEquals(2, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getDisplayName"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetDisplayNameProvenanceStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_DISPLAY_NAME_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            invokeNextElement(jni, baseVM, useVaList, enumeration); // lo
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            // plain
            DvmObject<?> plain = niClass.newObject(null);
            try {
                invokeGetDisplayName(jni, baseVM, useVaList, plain);
                fail("expected UOE for getDisplayName on plain NetworkInterface");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDisplayName"));
            }

            // foreign
            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                Enumeration foreignEnum = invokeGetNetworkInterfaces(jni2, baseVM2, useVaList,
                        vm2.resolveClass(NETWORK_INTERFACE_CLASS));
                invokeNextElement(jni2, baseVM2, useVaList, foreignEnum);
                DvmObject<?> foreign = invokeNextElement(jni2, baseVM2, useVaList, foreignEnum);
                try {
                    invokeGetDisplayName(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for foreign getDisplayName");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getDisplayName"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getDisplayName"));

            assertEquals("Wireless LAN", invokeGetDisplayName(jni, baseVM, useVaList, wlan0));
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getDisplayName"));

            // stale
            TraceEnvironmentConfig config2 =
                    TraceEnvironmentConfig.parse(IFACES_WITH_DISPLAY_NAME_JSON);
            emulator.set(TraceEnvironmentConfig.KEY, config2);
            try {
                invokeGetDisplayName(jni, baseVM, useVaList, wlan0);
                fail("expected UOE for stale getDisplayName");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDisplayName"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getDisplayName"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetByIndexMatchAndMiss(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            DvmObject<?> wlan0 = invokeGetByIndex(jni, baseVM, useVaList, niClass, 2);
            assertNotNull(wlan0);
            assertEquals(NETWORK_INTERFACE_CLASS, wlan0.getObjectType().getClassName());
            assertEquals("wlan0", invokeGetName(jni, baseVM, useVaList, wlan0));
            assertEquals(2, invokeGetIndex(jni, baseVM, useVaList, wlan0));

            DvmObject<?> again = invokeGetByIndex(jni, baseVM, useVaList, niClass, 2);
            assertNotNull(again);
            assertTrue("each getByIndex match must return a fresh marker", wlan0 != again);
            assertEquals("wlan0", invokeGetName(jni, baseVM, useVaList, again));

            DvmObject<?> lo = invokeGetByIndex(jni, baseVM, useVaList, niClass, 1);
            assertNotNull(lo);
            assertEquals("lo", invokeGetName(jni, baseVM, useVaList, lo));
            assertEquals(1, invokeGetIndex(jni, baseVM, useVaList, lo));

            CapturedEvent foundEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getByIndex");
            assertNotNull(foundEv);
            assertEquals("json-config", foundEv.source);
            assertEquals("index=1,result=found", String.valueOf(foundEv.value));
            assertNotNull(foundEv.note);
            assertFalse(foundEv.note.isEmpty());

            assertNull(invokeGetByIndex(jni, baseVM, useVaList, niClass, 99));
            CapturedEvent missEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getByIndex");
            assertNotNull(missEv);
            assertEquals("index=99,result=null", String.valueOf(missEv.value));
            assertEquals("json-config", missEv.source);

            // zero is non-negative; no interface has index 0 in config → null
            assertNull(invokeGetByIndex(jni, baseVM, useVaList, niClass, 0));
            CapturedEvent zeroEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getByIndex");
            assertNotNull(zeroEv);
            assertEquals("index=0,result=null", String.valueOf(zeroEv.value));

            assertEquals(5, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getByIndex"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetByIndexEmptyNegativeMissing(boolean is64Bit, boolean useVaList)
            throws Exception {
        // empty array → handled null for any non-negative index
        TraceEnvironmentConfig emptyConfig = TraceEnvironmentConfig.parse(EMPTY_IFACES_JSON);
        AndroidEmulator emulator = null;
        CapturingSink sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(emptyConfig)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            assertNull(invokeGetByIndex(jni, baseVM, useVaList, niClass, 1));
            CapturedEvent emptyEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getByIndex");
            assertNotNull(emptyEv);
            assertEquals("index=1,result=null", String.valueOf(emptyEv.value));
            assertEquals("json-config", emptyEv.source);

            // negative index → IAE, no event
            int before = countEvents(sink.events, "network_interface",
                    "NetworkInterface.getByIndex");
            try {
                invokeGetByIndex(jni, baseVM, useVaList, niClass, -1);
                fail("expected IllegalArgumentException for getByIndex(-1)");
            } catch (IllegalArgumentException expected) {
                // Java contract
            }
            assertEquals(before, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getByIndex"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        // missing network.interfaces → UOE, no event
        TraceEnvironmentConfig missingConfig = TraceEnvironmentConfig.parse(NO_IFACES_JSON);
        emulator = null;
        sink = new CapturingSink();
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(missingConfig)
                    .build();
            TraceEnvironmentEventSink.register(emulator, sink);
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            try {
                invokeGetByIndex(jni, baseVM, useVaList, niClass, 1);
                fail("expected UOE without network.interfaces");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getByIndex"));
            }
            // negative when unconfigured also UOE (not IAE)
            try {
                invokeGetByIndex(jni, baseVM, useVaList, niClass, -1);
                fail("expected UOE for getByIndex(-1) without network.interfaces");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getByIndex"));
            } catch (IllegalArgumentException unexpected) {
                fail("negative index without config must not apply Java IAE path: " + unexpected);
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_interface event when missing: " + e.api,
                        "network_interface".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetMTU(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_MTU_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            DvmObject<?> lo = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> eth0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            // lo has no mtu → UOE, no event
            try {
                invokeGetMTU(jni, baseVM, useVaList, lo);
                fail("expected UOE for getMTU when mtu absent");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMTU"));
            }
            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getMTU"));

            assertEquals(1500, invokeGetMTU(jni, baseVM, useVaList, wlan0));
            CapturedEvent wlanEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getMTU");
            assertNotNull(wlanEv);
            assertEquals("json-config", wlanEv.source);
            assertEquals("mtu=1500", String.valueOf(wlanEv.value));
            assertNotNull(wlanEv.note);
            assertFalse(wlanEv.note.isEmpty());

            assertEquals(9000, invokeGetMTU(jni, baseVM, useVaList, eth0));
            CapturedEvent ethEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getMTU");
            assertNotNull(ethEv);
            assertEquals("mtu=9000", String.valueOf(ethEv.value));
            assertEquals(2, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getMTU"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetMTUProvenanceStaleMissing(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_MTU_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            invokeNextElement(jni, baseVM, useVaList, enumeration); // lo
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            // plain → UOE, no getMTU event
            DvmObject<?> plain = niClass.newObject(null);
            try {
                invokeGetMTU(jni, baseVM, useVaList, plain);
                fail("expected UOE for getMTU on plain NetworkInterface");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMTU"));
            }

            // foreign marker
            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                Enumeration foreignEnum = invokeGetNetworkInterfaces(jni2, baseVM2, useVaList,
                        vm2.resolveClass(NETWORK_INTERFACE_CLASS));
                invokeNextElement(jni2, baseVM2, useVaList, foreignEnum);
                DvmObject<?> foreign = invokeNextElement(jni2, baseVM2, useVaList, foreignEnum);
                try {
                    invokeGetMTU(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for foreign getMTU");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getMTU"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            assertEquals(0, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getMTU"));

            // control: live works
            assertEquals(1500, invokeGetMTU(jni, baseVM, useVaList, wlan0));
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getMTU"));

            // stale config identity
            TraceEnvironmentConfig config2 = TraceEnvironmentConfig.parse(IFACES_WITH_MTU_JSON);
            emulator.set(TraceEnvironmentConfig.KEY, config2);
            try {
                invokeGetMTU(jni, baseVM, useVaList, wlan0);
                fail("expected UOE for stale getMTU");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMTU"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getMTU"));

            // missing network.interfaces
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(NO_IFACES_JSON));
            try {
                invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
                fail("expected UOE without network.interfaces");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getNetworkInterfaces"));
            }
            try {
                invokeGetMTU(jni, baseVM, useVaList, wlan0);
                fail("expected UOE for getMTU after config removed");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getMTU"));
            }
            assertEquals(1, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getMTU"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetHardwareAddress(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_MAC_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            DvmObject<?> lo = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> eth0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            // lo has no mac → handled null
            assertNull(invokeGetHardwareAddress(jni, baseVM, useVaList, lo));
            CapturedEvent nullEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getHardwareAddress");
            assertNotNull(nullEv);
            assertEquals("json-config", nullEv.source);
            assertEquals("mac=null", String.valueOf(nullEv.value));
            assertNotNull(nullEv.note);
            assertFalse(nullEv.note.isEmpty());

            // wlan0 has mac
            ByteArray wlanMac = invokeGetHardwareAddress(jni, baseVM, useVaList, wlan0);
            assertNotNull(wlanMac);
            assertTrue(wlanMac instanceof ByteArray);
            byte[] wlanBytes = wlanMac.getValue();
            assertEquals(6, wlanBytes.length);
            assertEquals((byte) 0x02, wlanBytes[0]);
            assertEquals((byte) 0x00, wlanBytes[1]);
            assertEquals((byte) 0x00, wlanBytes[2]);
            assertEquals((byte) 0x00, wlanBytes[3]);
            assertEquals((byte) 0x00, wlanBytes[4]);
            assertEquals((byte) 0x01, wlanBytes[5]);

            CapturedEvent wlanEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getHardwareAddress");
            assertNotNull(wlanEv);
            assertEquals("mac=02:00:00:00:00:01", String.valueOf(wlanEv.value));
            assertEquals("json-config", wlanEv.source);

            // eth0 uppercase input normalized to lowercase in config/sidecar
            ByteArray ethMac = invokeGetHardwareAddress(jni, baseVM, useVaList, eth0);
            assertNotNull(ethMac);
            byte[] ethBytes = ethMac.getValue();
            assertEquals(6, ethBytes.length);
            assertEquals((byte) 0xAA, ethBytes[0]);
            assertEquals((byte) 0xBB, ethBytes[1]);
            assertEquals((byte) 0xCC, ethBytes[2]);
            assertEquals((byte) 0xDD, ethBytes[3]);
            assertEquals((byte) 0xEE, ethBytes[4]);
            assertEquals((byte) 0xFF, ethBytes[5]);
            CapturedEvent ethEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getHardwareAddress");
            assertNotNull(ethEv);
            assertEquals("mac=aa:bb:cc:dd:ee:ff", String.valueOf(ethEv.value));

            assertEquals(3, countEvents(sink.events, "network_interface",
                    "NetworkInterface.getHardwareAddress"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetHardwareAddressFreshnessAndIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_WITH_MAC_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            // skip lo (no mac)
            invokeNextElement(jni, baseVM, useVaList, enumeration);
            DvmObject<?> wlan0 = invokeNextElement(jni, baseVM, useVaList, enumeration);

            ByteArray a = invokeGetHardwareAddress(jni, baseVM, useVaList, wlan0);
            ByteArray b = invokeGetHardwareAddress(jni, baseVM, useVaList, wlan0);
            assertNotNull(a);
            assertNotNull(b);
            assertTrue("each getHardwareAddress must return a fresh ByteArray", a != b);
            assertTrue(a.getValue() != b.getValue());
            assertEquals(6, a.getValue().length);
            assertEquals(a.getValue()[0], b.getValue()[0]);

            // plain → UOE
            DvmObject<?> plain = niClass.newObject(null);
            try {
                invokeGetHardwareAddress(jni, baseVM, useVaList, plain);
                fail("expected UOE for getHardwareAddress on plain NetworkInterface");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getHardwareAddress"));
            }

            // foreign marker
            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                Enumeration foreignEnum = invokeGetNetworkInterfaces(jni2, baseVM2, useVaList,
                        vm2.resolveClass(NETWORK_INTERFACE_CLASS));
                invokeNextElement(jni2, baseVM2, useVaList, foreignEnum); // lo
                DvmObject<?> foreign = invokeNextElement(jni2, baseVM2, useVaList, foreignEnum); // wlan0
                try {
                    invokeGetHardwareAddress(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for foreign getHardwareAddress");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getHardwareAddress"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            // stale config identity
            TraceEnvironmentConfig config2 = TraceEnvironmentConfig.parse(IFACES_WITH_MAC_JSON);
            emulator.set(TraceEnvironmentConfig.KEY, config2);
            try {
                invokeGetHardwareAddress(jni, baseVM, useVaList, wlan0);
                fail("expected UOE for stale getHardwareAddress");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getHardwareAddress"));
            }

            // missing network.interfaces → UOE on getNetworkInterfaces (and no getHardwareAddress path)
            emulator.set(TraceEnvironmentConfig.KEY, TraceEnvironmentConfig.parse(NO_IFACES_JSON));
            try {
                invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
                fail("expected UOE without network.interfaces");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getNetworkInterfaces"));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runPlainForeignStaleIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(IFACES_JSON);
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
            DvmClass niClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);

            // plain NetworkInterface
            DvmObject<?> plain = niClass.newObject(null);
            try {
                invokeGetName(jni, baseVM, useVaList, plain);
                fail("expected UOE for getName on plain NetworkInterface");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            try {
                invokeGetIndex(jni, baseVM, useVaList, plain);
                fail("expected UOE for getIndex on plain NetworkInterface");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getIndex"));
            }
            try {
                invokeGetHardwareAddress(jni, baseVM, useVaList, plain);
                fail("expected UOE for getHardwareAddress on plain NetworkInterface");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getHardwareAddress"));
            }

            // wrong signature
            Enumeration enumeration = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            DvmObject<?> live = invokeNextElement(jni, baseVM, useVaList, enumeration);
            try {
                DvmMethod method = new DvmMethod(niClass, "getDisplayName", "()Ljava/lang/String;", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, live, signature, new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callObjectMethod(baseVM, live, signature, new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for getDisplayName");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getDisplayName"));
            }

            // foreign marker from another VM
            AndroidEmulator emulator2 = null;
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                Enumeration foreignEnum = invokeGetNetworkInterfaces(jni2, baseVM2, useVaList,
                        vm2.resolveClass(NETWORK_INTERFACE_CLASS));
                DvmObject<?> foreign = invokeNextElement(jni2, baseVM2, useVaList, foreignEnum);
                try {
                    invokeGetName(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for foreign ConfiguredNetworkInterface getName");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getName"));
                }
                try {
                    invokeGetIndex(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for foreign ConfiguredNetworkInterface getIndex");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getIndex"));
                }
                try {
                    invokeGetHardwareAddress(jni, baseVM, useVaList, foreign);
                    fail("expected UOE for foreign getHardwareAddress");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getHardwareAddress"));
                }
            } finally {
                if (emulator2 != null) {
                    emulator2.close();
                }
            }

            // stale config identity: re-parse same JSON → new NetworkInterfaceConfig instances
            TraceEnvironmentConfig config2 = TraceEnvironmentConfig.parse(IFACES_JSON);
            emulator.set(TraceEnvironmentConfig.KEY, config2);
            try {
                invokeGetName(jni, baseVM, useVaList, live);
                fail("expected UOE for stale ConfiguredNetworkInterface getName");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            try {
                invokeGetIndex(jni, baseVM, useVaList, live);
                fail("expected UOE for stale ConfiguredNetworkInterface getIndex");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getIndex"));
            }
            try {
                invokeGetHardwareAddress(jni, baseVM, useVaList, live);
                fail("expected UOE for stale getHardwareAddress");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getHardwareAddress"));
            }

            // restore and control path still works with fresh enumeration
            emulator.set(TraceEnvironmentConfig.KEY, config);
            Enumeration control = invokeGetNetworkInterfaces(jni, baseVM, useVaList, niClass);
            DvmObject<?> controlNi = invokeNextElement(jni, baseVM, useVaList, control);
            assertEquals("lo", invokeGetName(jni, baseVM, useVaList, controlNi));
            assertEquals(1, invokeGetIndex(jni, baseVM, useVaList, controlNi));
            // lo has no mac → handled null with event after restore
            assertNull(invokeGetHardwareAddress(jni, baseVM, useVaList, controlNi));
            CapturedEvent macEv = findLastEvent(sink.events, "network_interface",
                    "NetworkInterface.getHardwareAddress");
            assertNotNull(macEv);
            assertEquals("mac=null", String.valueOf(macEv.value));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static Enumeration invokeGetNetworkInterfaces(AbstractJni jni, BaseVM vm,
                                                          boolean useVaList, DvmClass niClass) {
        DvmMethod method = new DvmMethod(niClass, "getNetworkInterfaces",
                "()Ljava/util/Enumeration;", true);
        String signature = method.getSignature();
        assertEquals(GET_NETWORK_INTERFACES_SIGNATURE, signature);
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callStaticObjectMethodV(vm, niClass, signature,
                    new TestNoArgVaList(vm, method));
        } else {
            result = jni.callStaticObjectMethod(vm, niClass, signature,
                    new TestNoArgVarArg(vm, method));
        }
        assertTrue(result instanceof Enumeration);
        return (Enumeration) result;
    }

    private static DvmObject<?> invokeGetByName(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmClass niClass, String name) {
        DvmMethod method = new DvmMethod(niClass, "getByName",
                "(Ljava/lang/String;)Ljava/net/NetworkInterface;", true);
        String signature = method.getSignature();
        assertEquals(GET_BY_NAME_SIGNATURE, signature);
        if (name == null) {
            if (useVaList) {
                return jni.callStaticObjectMethodV(vm, niClass, signature,
                        new TestObjectVaList(vm, method, 0));
            }
            return jni.callStaticObjectMethod(vm, niClass, signature,
                    new TestObjectVarArg(vm, method, 0));
        }
        int nameHash = vm.addLocalObject(new StringObject(vm, name));
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, niClass, signature,
                    new TestObjectVaList(vm, method, nameHash));
        }
        return jni.callStaticObjectMethod(vm, niClass, signature,
                new TestObjectVarArg(vm, method, nameHash));
    }

    private static DvmObject<?> invokeGetByIndex(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmClass niClass, int index) {
        DvmMethod method = new DvmMethod(niClass, "getByIndex",
                "(I)Ljava/net/NetworkInterface;", true);
        String signature = method.getSignature();
        assertEquals(GET_BY_INDEX_SIGNATURE, signature);
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, niClass, signature,
                    new TestIntVaList(vm, method, index));
        }
        return jni.callStaticObjectMethod(vm, niClass, signature,
                new TestIntVarArg(vm, method, index));
    }

    private static DvmObject<?> invokeGetByInetAddress(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmClass niClass, DvmObject<?> inetAddress) {
        DvmMethod method = new DvmMethod(niClass, "getByInetAddress",
                "(Ljava/net/InetAddress;)Ljava/net/NetworkInterface;", true);
        String signature = method.getSignature();
        assertEquals(GET_BY_INET_ADDRESS_SIGNATURE, signature);
        int hash = inetAddress == null ? 0 : vm.addLocalObject(inetAddress);
        if (useVaList) {
            return jni.callStaticObjectMethodV(vm, niClass, signature,
                    new TestObjectVaList(vm, method, hash));
        }
        return jni.callStaticObjectMethod(vm, niClass, signature,
                new TestObjectVarArg(vm, method, hash));
    }

    private static boolean invokeHasMoreElements(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> enumeration) {
        DvmClass enumClass = enumeration.getObjectType();
        DvmMethod method = new DvmMethod(enumClass, "hasMoreElements", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, enumeration, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, enumeration, signature, new TestNoArgVarArg(vm, method));
    }

    private static DvmObject<?> invokeNextElement(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> enumeration) {
        DvmClass enumClass = enumeration.getObjectType();
        DvmMethod method = new DvmMethod(enumClass, "nextElement", "()Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, enumeration, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callObjectMethod(vm, enumeration, signature, new TestNoArgVarArg(vm, method));
    }

    private static String invokeGetName(AbstractJni jni, BaseVM vm, boolean useVaList,
                                        DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getName", "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static String invokeGetDisplayName(AbstractJni jni, BaseVM vm, boolean useVaList,
                                               DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getDisplayName", "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
        }
        if (result == null) {
            return null;
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static boolean invokeIsUp(AbstractJni jni, BaseVM vm, boolean useVaList,
                                      DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isUp", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsLoopback(AbstractJni jni, BaseVM vm, boolean useVaList,
                                            DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isLoopback", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeSupportsMulticast(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "supportsMulticast", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsPointToPoint(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isPointToPoint", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsVirtual(AbstractJni jni, BaseVM vm, boolean useVaList,
                                           DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isVirtual", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static Enumeration invokeGetInetAddresses(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                      DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getInetAddresses",
                "()Ljava/util/Enumeration;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
        }
        assertTrue(result instanceof Enumeration);
        return (Enumeration) result;
    }

    private static String invokeGetHostAddress(AbstractJni jni, BaseVM vm, boolean useVaList,
                                               DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass("java/net/InetAddress");
        DvmMethod method = new DvmMethod(dvmClass, "getHostAddress", "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
        }
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static int invokeGetIndex(AbstractJni jni, BaseVM vm, boolean useVaList,
                                      DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getIndex", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static int invokeGetMTU(AbstractJni jni, BaseVM vm, boolean useVaList,
                                    DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getMTU", "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static ByteArray invokeGetHardwareAddress(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                      DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(NETWORK_INTERFACE_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getHardwareAddress", "()[B", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
        }
        if (result == null) {
            return null;
        }
        assertTrue(result instanceof ByteArray);
        return (ByteArray) result;
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

    private static final class TestIntVarArg extends VarArg {
        TestIntVarArg(BaseVM vm, DvmMethod method, int value) {
            super(vm, method);
            args.add(value);
        }
    }

    private static final class TestIntVaList extends VaList {
        TestIntVaList(BaseVM vm, DvmMethod method, int value) {
            super(vm, method);
            args.add(value);
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

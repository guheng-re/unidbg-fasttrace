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

public class AndroidNetworkLinksJniTest {

    private static final String LINKS_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"},"
            + "\"network\":{\"links\":{"
            + "\"connected\":false,"
            + "\"type\":-1,"
            + "\"typeName\":\"TRACEAI_LINK_TYPE_MARKER_V1\","
            + "\"interfaceName\":\"TRACEAI_IFACE\","
            + "\"mtu\":65536,"
            + "\"dnsServers\":[\"8.8.8.8\",\"1.1.1.1\"],"
            + "\"privateDnsActive\":true,"
            + "\"privateDnsServerName\":\"TRACEAI_PRIVATE_DNS_V1\","
            + "\"domains\":\"lan.local\","
            + "\"proxyHost\":\"TRACEAI_PROXY_HOST_V1\","
            + "\"proxyPort\":8080"
            + "}}"
            + "}";

    @Test
    public void testNetworkLinksJniVarArg32() throws Exception {
        runNetworkLinksJni(false, false);
    }

    @Test
    public void testNetworkLinksJniVaList64() throws Exception {
        runNetworkLinksJni(true, true);
    }

    @Test
    public void testConnectivityTypedGetSystemServiceVarArg32() throws Exception {
        runConnectivityTypedGetSystemService(false, false);
    }

    @Test
    public void testConnectivityTypedGetSystemServiceVaList64() throws Exception {
        runConnectivityTypedGetSystemService(true, true);
    }

    private static void runNetworkLinksJni(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LINKS_JSON);
        AndroidEmulator emulator = null;
        try {
            emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            VM vm = emulator.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> cm = vm.resolveClass("android/net/ConnectivityManager").newObject(null);
            DvmObject<?> networkInfo = invokeNoArgObject(jni, baseVM, useVaList, cm,
                    "getActiveNetworkInfo", "()Landroid/net/NetworkInfo;");
            assertNotNull(networkInfo);
            assertEquals("android/net/NetworkInfo", networkInfo.getObjectType().getClassName());

            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, networkInfo, "isConnected"));
            assertEquals(-1, invokeNoArgInt(jni, baseVM, useVaList, networkInfo, "getType"));
            assertEquals("TRACEAI_LINK_TYPE_MARKER_V1",
                    invokeNoArgString(jni, baseVM, useVaList, networkInfo, "getTypeName"));

            // modern Network / LinkProperties surface
            DvmObject<?> network = invokeNoArgObject(jni, baseVM, useVaList, cm,
                    "getActiveNetwork", "()Landroid/net/Network;");
            assertNotNull(network);
            assertEquals("android/net/Network", network.getObjectType().getClassName());

            DvmObject<?> linkProps = invokeGetLinkProperties(jni, baseVM, useVaList, cm, network);
            assertNotNull(linkProps);
            assertEquals("android/net/LinkProperties", linkProps.getObjectType().getClassName());
            assertEquals("TRACEAI_IFACE",
                    invokeNoArgString(jni, baseVM, useVaList, linkProps, "getInterfaceName"));
            assertEquals(65536, invokeNoArgInt(jni, baseVM, useVaList, linkProps, "getMtu"));
            assertTrue(invokeNoArgBoolean(jni, baseVM, useVaList, linkProps, "isPrivateDnsActive"));
            assertEquals("TRACEAI_PRIVATE_DNS_V1",
                    invokeNoArgString(jni, baseVM, useVaList, linkProps, "getPrivateDnsServerName"));
            assertEquals("lan.local",
                    invokeNoArgString(jni, baseVM, useVaList, linkProps, "getDomains"));

            // HTTP proxy with provenance marker
            DvmObject<?> proxyInfo = invokeNoArgObject(jni, baseVM, useVaList, linkProps,
                    "getHttpProxy", "()Landroid/net/ProxyInfo;");
            assertNotNull(proxyInfo);
            assertEquals("android/net/ProxyInfo", proxyInfo.getObjectType().getClassName());
            assertEquals("TRACEAI_PROXY_HOST_V1",
                    invokeNoArgString(jni, baseVM, useVaList, proxyInfo, "getHost"));
            assertEquals(8080, invokeNoArgInt(jni, baseVM, useVaList, proxyInfo, "getPort"));

            // A's ProxyInfo must not be treated as B's json-config (different proxyHost/proxyPort)
            TraceEnvironmentConfig otherProxyCfg = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\"},"
                    + "\"network\":{\"links\":{\"proxyHost\":\"OTHER_PROXY_HOST_V1\",\"proxyPort\":9090}}}"
            );
            AndroidEmulator otherProxyEmu = null;
            CapturingSink otherProxySink = new CapturingSink();
            try {
                otherProxyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(otherProxyCfg)
                        .build();
                TraceEnvironmentEventSink.register(otherProxyEmu, otherProxySink);
                VM otherProxyVm = otherProxyEmu.createDalvikVM();
                AbstractJni otherProxyJni = new AbstractJni() {
                };
                otherProxyVm.setJni(otherProxyJni);
                BaseVM otherProxyBase = (BaseVM) otherProxyVm;
                int eventsBefore = otherProxySink.events.size();
                try {
                    invokeNoArgObject(otherProxyJni, otherProxyBase, useVaList, proxyInfo,
                            "getHost", "()Ljava/lang/String;");
                    fail("expected UnsupportedOperationException for foreign-VM ProxyInfo.getHost");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("ProxyInfo->getHost"));
                }
                assertEquals(eventsBefore, otherProxySink.events.size());
                try {
                    invokeNoArgInt(otherProxyJni, otherProxyBase, useVaList, proxyInfo, "getPort");
                    fail("expected UnsupportedOperationException for foreign-VM ProxyInfo.getPort");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("ProxyInfo->getPort"));
                }
                assertEquals(eventsBefore, otherProxySink.events.size());
            } finally {
                if (otherProxyEmu != null) {
                    TraceEnvironmentEventSink.unregister(otherProxyEmu, otherProxySink);
                    otherProxyEmu.close();
                }
            }
            assertEquals("TRACEAI_PROXY_HOST_V1",
                    invokeNoArgString(jni, baseVM, useVaList, proxyInfo, "getHost"));
            assertEquals(8080, invokeNoArgInt(jni, baseVM, useVaList, proxyInfo, "getPort"));

            // unrelated ProxyInfo must not be treated as json-config proxy
            DvmObject<?> unrelatedProxy = vm.resolveClass("android/net/ProxyInfo").newObject("evil.proxy");
            try {
                invokeNoArgObject(jni, baseVM, useVaList, unrelatedProxy,
                        "getHost", "()Ljava/lang/String;");
                fail("expected UnsupportedOperationException for unrelated ProxyInfo.getHost");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ProxyInfo->getHost"));
            }
            try {
                invokeNoArgInt(jni, baseVM, useVaList, unrelatedProxy, "getPort");
                fail("expected UnsupportedOperationException for unrelated ProxyInfo.getPort");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ProxyInfo->getPort"));
            }
            // configured proxy still works
            assertEquals("TRACEAI_PROXY_HOST_V1",
                    invokeNoArgString(jni, baseVM, useVaList, proxyInfo, "getHost"));
            assertEquals(8080, invokeNoArgInt(jni, baseVM, useVaList, proxyInfo, "getPort"));

            // DNS servers list
            DvmObject<?> dnsListObj = invokeNoArgObject(jni, baseVM, useVaList, linkProps,
                    "getDnsServers", "()Ljava/util/List;");
            assertTrue(dnsListObj instanceof ArrayListObject);
            ArrayListObject dnsList = (ArrayListObject) dnsListObj;
            assertEquals(2, dnsList.size());
            DvmObject<?> dns0 = invokeListGet(jni, baseVM, useVaList, dnsList, 0);
            DvmObject<?> dns1 = invokeListGet(jni, baseVM, useVaList, dnsList, 1);
            assertNotNull(dns0);
            assertNotNull(dns1);
            assertEquals("java/net/InetAddress", dns0.getObjectType().getClassName());
            assertEquals("java/net/InetAddress", dns1.getObjectType().getClassName());
            assertEquals("8.8.8.8", invokeNoArgString(jni, baseVM, useVaList, dns0, "getHostAddress"));
            assertEquals("1.1.1.1", invokeNoArgString(jni, baseVM, useVaList, dns1, "getHostAddress"));

            // A's DNS InetAddress must not be treated as B's json-config (different dnsServers)
            TraceEnvironmentConfig otherDnsCfg = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\"},"
                    + "\"network\":{\"links\":{\"dnsServers\":[\"9.9.9.9\"]}}}"
            );
            AndroidEmulator otherEmu = null;
            CapturingSink otherSink = new CapturingSink();
            try {
                otherEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(otherDnsCfg)
                        .build();
                TraceEnvironmentEventSink.register(otherEmu, otherSink);
                VM otherVm = otherEmu.createDalvikVM();
                AbstractJni otherJni = new AbstractJni() {
                };
                otherVm.setJni(otherJni);
                BaseVM otherBase = (BaseVM) otherVm;
                int eventsBefore = otherSink.events.size();
                try {
                    invokeNoArgObject(otherJni, otherBase, useVaList, dns0,
                            "getHostAddress", "()Ljava/lang/String;");
                    fail("expected UnsupportedOperationException for foreign-VM DNS InetAddress");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("InetAddress->getHostAddress"));
                }
                assertEquals(eventsBefore, otherSink.events.size());
            } finally {
                if (otherEmu != null) {
                    TraceEnvironmentEventSink.unregister(otherEmu, otherSink);
                    otherEmu.close();
                }
            }
            assertEquals("8.8.8.8", invokeNoArgString(jni, baseVM, useVaList, dns0, "getHostAddress"));

            // unrelated InetAddress with plain String value must not be treated as json-config DNS
            DvmObject<?> unrelatedInet = vm.resolveClass("java/net/InetAddress").newObject("203.0.113.9");
            try {
                invokeNoArgObject(jni, baseVM, useVaList, unrelatedInet,
                        "getHostAddress", "()Ljava/lang/String;");
                fail("expected UnsupportedOperationException for unrelated InetAddress");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("InetAddress->getHostAddress"));
            }
            // configured DNS objects still work after the unrelated regression
            assertEquals("8.8.8.8", invokeNoArgString(jni, baseVM, useVaList, dns0, "getHostAddress"));

            // explicit null privateDnsServerName / domains / proxyHost
            TraceEnvironmentConfig nullDnsCfg = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\"},"
                    + "\"network\":{\"links\":{"
                    + "\"privateDnsActive\":false,"
                    + "\"privateDnsServerName\":null,"
                    + "\"domains\":null,"
                    + "\"proxyHost\":null"
                    + "}}}"
            );
            AndroidEmulator nullDnsEmu = null;
            try {
                nullDnsEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(nullDnsCfg)
                        .build();
                VM nullDnsVm = nullDnsEmu.createDalvikVM();
                AbstractJni nullDnsJni = new AbstractJni() {
                };
                nullDnsVm.setJni(nullDnsJni);
                BaseVM nullDnsBase = (BaseVM) nullDnsVm;
                DvmObject<?> nullCm = nullDnsVm.resolveClass("android/net/ConnectivityManager").newObject(null);
                DvmObject<?> nullNet = invokeNoArgObject(nullDnsJni, nullDnsBase, useVaList, nullCm,
                        "getActiveNetwork", "()Landroid/net/Network;");
                DvmObject<?> nullLp = invokeGetLinkProperties(nullDnsJni, nullDnsBase, useVaList, nullCm, nullNet);
                assertFalse(invokeNoArgBoolean(nullDnsJni, nullDnsBase, useVaList, nullLp, "isPrivateDnsActive"));
                assertNull(invokeNoArgObject(nullDnsJni, nullDnsBase, useVaList, nullLp,
                        "getPrivateDnsServerName", "()Ljava/lang/String;"));
                assertNull(invokeNoArgObject(nullDnsJni, nullDnsBase, useVaList, nullLp,
                        "getDomains", "()Ljava/lang/String;"));
                assertNull(invokeNoArgObject(nullDnsJni, nullDnsBase, useVaList, nullLp,
                        "getHttpProxy", "()Landroid/net/ProxyInfo;"));
            } finally {
                if (nullDnsEmu != null) {
                    nullDnsEmu.close();
                }
            }

            // proxyHost without proxyPort: getHost works, getPort UOE
            TraceEnvironmentConfig hostOnlyCfg = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\"},"
                    + "\"network\":{\"links\":{\"proxyHost\":\"host.only\"}}}"
            );
            AndroidEmulator hostOnlyEmu = null;
            try {
                hostOnlyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(hostOnlyCfg)
                        .build();
                VM hostOnlyVm = hostOnlyEmu.createDalvikVM();
                AbstractJni hostOnlyJni = new AbstractJni() {
                };
                hostOnlyVm.setJni(hostOnlyJni);
                BaseVM hostOnlyBase = (BaseVM) hostOnlyVm;
                DvmObject<?> hostOnlyCm = hostOnlyVm.resolveClass("android/net/ConnectivityManager").newObject(null);
                DvmObject<?> hostOnlyNet = invokeNoArgObject(hostOnlyJni, hostOnlyBase, useVaList, hostOnlyCm,
                        "getActiveNetwork", "()Landroid/net/Network;");
                DvmObject<?> hostOnlyLp = invokeGetLinkProperties(hostOnlyJni, hostOnlyBase, useVaList, hostOnlyCm,
                        hostOnlyNet);
                DvmObject<?> hostOnlyProxy = invokeNoArgObject(hostOnlyJni, hostOnlyBase, useVaList, hostOnlyLp,
                        "getHttpProxy", "()Landroid/net/ProxyInfo;");
                assertNotNull(hostOnlyProxy);
                assertEquals("host.only",
                        invokeNoArgString(hostOnlyJni, hostOnlyBase, useVaList, hostOnlyProxy, "getHost"));
                try {
                    invokeNoArgInt(hostOnlyJni, hostOnlyBase, useVaList, hostOnlyProxy, "getPort");
                    fail("expected UnsupportedOperationException for missing proxyPort");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("ProxyInfo->getPort"));
                }
            } finally {
                if (hostOnlyEmu != null) {
                    hostOnlyEmu.close();
                }
            }

            // explicit empty DNS list
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
                DvmObject<?> emptyCm = emptyDnsVm.resolveClass("android/net/ConnectivityManager").newObject(null);
                DvmObject<?> emptyNet = invokeNoArgObject(emptyDnsJni, emptyDnsBase, useVaList, emptyCm,
                        "getActiveNetwork", "()Landroid/net/Network;");
                DvmObject<?> emptyLp = invokeGetLinkProperties(emptyDnsJni, emptyDnsBase, useVaList, emptyCm,
                        emptyNet);
                DvmObject<?> emptyDnsListObj = invokeNoArgObject(emptyDnsJni, emptyDnsBase, useVaList, emptyLp,
                        "getDnsServers", "()Ljava/util/List;");
                assertTrue(emptyDnsListObj instanceof ArrayListObject);
                assertEquals(0, ((ArrayListObject) emptyDnsListObj).size());
            } finally {
                if (emptyDnsEmu != null) {
                    emptyDnsEmu.close();
                }
            }

            // missing individual keys
            TraceEnvironmentConfig sparse = TraceEnvironmentConfig.parse("{"
                    + "\"android\":{\"packageName\":\"com.demo.app\"},"
                    + "\"network\":{\"links\":{\"connected\":true}}}"
            );
            AndroidEmulator sparseEmu = null;
            try {
                sparseEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(sparse)
                        .build();
                VM sparseVm = sparseEmu.createDalvikVM();
                AbstractJni sparseJni = new AbstractJni() {
                };
                sparseVm.setJni(sparseJni);
                BaseVM sparseBase = (BaseVM) sparseVm;
                DvmObject<?> sparseCm = sparseVm.resolveClass("android/net/ConnectivityManager").newObject(null);
                DvmObject<?> sparseInfo = invokeNoArgObject(sparseJni, sparseBase, useVaList, sparseCm,
                        "getActiveNetworkInfo", "()Landroid/net/NetworkInfo;");
                assertNotNull(sparseInfo);
                assertTrue(invokeNoArgBoolean(sparseJni, sparseBase, useVaList, sparseInfo, "isConnected"));
                DvmObject<?> sparseNetwork = invokeNoArgObject(sparseJni, sparseBase, useVaList, sparseCm,
                        "getActiveNetwork", "()Landroid/net/Network;");
                assertNotNull(sparseNetwork);
                DvmObject<?> sparseLp = invokeGetLinkProperties(sparseJni, sparseBase, useVaList, sparseCm,
                        sparseNetwork);
                assertNotNull(sparseLp);
                try {
                    invokeNoArgInt(sparseJni, sparseBase, useVaList, sparseInfo, "getType");
                    fail("expected UnsupportedOperationException for missing type");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("NetworkInfo->getType"));
                }
                try {
                    invokeNoArgObject(sparseJni, sparseBase, useVaList, sparseInfo,
                            "getTypeName", "()Ljava/lang/String;");
                    fail("expected UnsupportedOperationException for missing typeName");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("NetworkInfo->getTypeName"));
                }
                try {
                    invokeNoArgObject(sparseJni, sparseBase, useVaList, sparseLp,
                            "getInterfaceName", "()Ljava/lang/String;");
                    fail("expected UnsupportedOperationException for missing interfaceName");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("LinkProperties->getInterfaceName"));
                }
                try {
                    invokeNoArgInt(sparseJni, sparseBase, useVaList, sparseLp, "getMtu");
                    fail("expected UnsupportedOperationException for missing mtu");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("LinkProperties->getMtu"));
                }
                try {
                    invokeNoArgObject(sparseJni, sparseBase, useVaList, sparseLp,
                            "getDnsServers", "()Ljava/util/List;");
                    fail("expected UnsupportedOperationException for missing dnsServers");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("LinkProperties->getDnsServers"));
                }
                try {
                    invokeNoArgBoolean(sparseJni, sparseBase, useVaList, sparseLp, "isPrivateDnsActive");
                    fail("expected UnsupportedOperationException for missing privateDnsActive");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("LinkProperties->isPrivateDnsActive"));
                }
                try {
                    invokeNoArgObject(sparseJni, sparseBase, useVaList, sparseLp,
                            "getPrivateDnsServerName", "()Ljava/lang/String;");
                    fail("expected UnsupportedOperationException for missing privateDnsServerName");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("LinkProperties->getPrivateDnsServerName"));
                }
                try {
                    invokeNoArgObject(sparseJni, sparseBase, useVaList, sparseLp,
                            "getDomains", "()Ljava/lang/String;");
                    fail("expected UnsupportedOperationException for missing domains");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("LinkProperties->getDomains"));
                }
                try {
                    invokeNoArgObject(sparseJni, sparseBase, useVaList, sparseLp,
                            "getHttpProxy", "()Landroid/net/ProxyInfo;");
                    fail("expected UnsupportedOperationException for missing proxyHost");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("LinkProperties->getHttpProxy"));
                }
            } finally {
                if (sparseEmu != null) {
                    sparseEmu.close();
                }
            }

            // no network.links
            TraceEnvironmentConfig noLinks = TraceEnvironmentConfig.parse(
                    "{\"android\":{\"packageName\":\"com.demo.app\"}}");
            AndroidEmulator noLinksEmu = null;
            try {
                noLinksEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(noLinks)
                        .build();
                VM noLinksVm = noLinksEmu.createDalvikVM();
                AbstractJni noLinksJni = new AbstractJni() {
                };
                noLinksVm.setJni(noLinksJni);
                BaseVM noLinksBase = (BaseVM) noLinksVm;
                DvmObject<?> noCm = noLinksVm.resolveClass("android/net/ConnectivityManager").newObject(null);
                try {
                    invokeNoArgObject(noLinksJni, noLinksBase, useVaList, noCm,
                            "getActiveNetworkInfo", "()Landroid/net/NetworkInfo;");
                    fail("expected UnsupportedOperationException without network.links");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("ConnectivityManager->getActiveNetworkInfo"));
                }
                try {
                    invokeNoArgObject(noLinksJni, noLinksBase, useVaList, noCm,
                            "getActiveNetwork", "()Landroid/net/Network;");
                    fail("expected UnsupportedOperationException for getActiveNetwork without links");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("ConnectivityManager->getActiveNetwork"));
                }
            } finally {
                if (noLinksEmu != null) {
                    noLinksEmu.close();
                }
            }

            // zero-arg unrelated regression
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmMethod getPackageName = new DvmMethod(app.getObjectType(), "getPackageName",
                    "()Ljava/lang/String;", false);
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
            StringObject strObj = new StringObject(baseVM, "hello");
            DvmMethod hashCode = new DvmMethod(strObj.getObjectType(), "hashCode", "()I", false);
            if (useVaList) {
                assertEquals("hello".hashCode(),
                        jni.callIntMethodV(baseVM, strObj, hashCode.getSignature(),
                                new TestVaList(baseVM, hashCode)));
            } else {
                assertEquals("hello".hashCode(),
                        jni.callIntMethod(baseVM, strObj, hashCode.getSignature(),
                                new TestVarArg(baseVM, hashCode)));
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    /**
     * Typed {@code Application}/{@code Context.getSystemService(ConnectivityManager.class)} returns
     * the same {@code SystemService("connectivity")} marker as the string route. Lookup itself
     * emits no sidecar and does not require {@code network.links}; getters stay node-gated.
     */
    private static void runConnectivityTypedGetSystemService(boolean is64Bit, boolean useVaList)
            throws Exception {
        runConnectivityTypedConfigured(is64Bit, useVaList);
        runConnectivityTypedAbsent(is64Bit, useVaList);
        runConnectivityTypedUnrelated(is64Bit, useVaList);
    }

    private static void runConnectivityTypedConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LINKS_JSON);
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
            DvmClass cmClass = vm.resolveClass("android/net/ConnectivityManager");

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromString = invokeGetSystemService(jni, baseVM, useVaList, app, "connectivity");
            int eventsBeforeApp = sink.events.size();
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, cmClass);
            assertConnectivityManagerMarker(fromApp);
            assertEquals(fromString.getObjectType().getClassName(), fromApp.getObjectType().getClassName());
            assertEquals(fromString.getValue(), fromApp.getValue());
            assertNoNetworkLinkSince(sink, eventsBeforeApp);

            DvmObject<?> networkInfo = invokeNoArgObject(jni, baseVM, useVaList, fromApp,
                    "getActiveNetworkInfo", "()Landroid/net/NetworkInfo;");
            assertNotNull(networkInfo);
            assertEquals("android/net/NetworkInfo", networkInfo.getObjectType().getClassName());
            assertFalse(invokeNoArgBoolean(jni, baseVM, useVaList, networkInfo, "isConnected"));
            CapturedEvent infoEv = findLastEvent(sink.events, "network_link",
                    "ConnectivityManager.getActiveNetworkInfo");
            assertNotNull(infoEv);
            assertEquals("json-config", infoEv.source);
            assertEquals("NetworkInfo", String.valueOf(infoEv.value));

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            int eventsBeforeCtx = sink.events.size();
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, cmClass);
            assertConnectivityManagerMarker(fromCtx);
            assertNoNetworkLinkSince(sink, eventsBeforeCtx);
            DvmObject<?> ctxInfo = invokeNoArgObject(jni, baseVM, useVaList, fromCtx,
                    "getActiveNetworkInfo", "()Landroid/net/NetworkInfo;");
            assertNotNull(ctxInfo);
            assertEquals("android/net/NetworkInfo", ctxInfo.getObjectType().getClassName());
            CapturedEvent ctxEv = findLastEvent(sink.events, "network_link",
                    "ConnectivityManager.getActiveNetworkInfo");
            assertNotNull(ctxEv);
            assertEquals("json-config", ctxEv.source);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConnectivityTypedAbsent(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                "{\"android\":{\"packageName\":\"com.demo.app\"}}");
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
            DvmClass cmClass = vm.resolveClass("android/net/ConnectivityManager");
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            int eventsBeforeLookup = sink.events.size();
            DvmObject<?> manager = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, cmClass);
            assertConnectivityManagerMarker(manager);
            assertNoNetworkLinkSince(sink, eventsBeforeLookup);
            try {
                invokeNoArgObject(jni, baseVM, useVaList, manager,
                        "getActiveNetworkInfo", "()Landroid/net/NetworkInfo;");
                fail("expected UOE for getActiveNetworkInfo without network.links");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("ConnectivityManager->getActiveNetworkInfo"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_link event when links absent: " + e.api,
                        "network_link".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runConnectivityTypedUnrelated(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LINKS_JSON);
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
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmClass unrelated = vm.resolveClass("android/print/PrintManager");
            try {
                invokeGetSystemServiceClass(jni, baseVM, useVaList, app, unrelated);
                fail("expected UOE for getSystemService(Class) with unrelated class");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSystemService"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected network_link event on unrelated typed lookup: " + e.api,
                        "network_link".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertConnectivityManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals("android/net/ConnectivityManager", manager.getObjectType().getClassName());
        assertEquals(SystemService.CONNECTIVITY_SERVICE, manager.getValue());
    }

    private static void assertNoNetworkLinkSince(CapturingSink sink, int fromIndex) {
        for (int i = fromIndex; i < sink.events.size(); i++) {
            assertFalse("unexpected network_link event on typed lookup: " + sink.events.get(i).api,
                    "network_link".equals(sink.events.get(i).kind));
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

    private static String invokeNoArgString(AbstractJni jni, BaseVM vm, boolean useVaList,
                                            DvmObject<?> target, String methodName) {
        DvmObject<?> result = invokeNoArgObject(jni, vm, useVaList, target, methodName,
                "()Ljava/lang/String;");
        assertTrue(result instanceof StringObject);
        return ((StringObject) result).getValue();
    }

    private static DvmObject<?> invokeNoArgObject(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> target, String methodName, String args) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, args, false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature, new TestVaList(vm, method));
        }
        return jni.callObjectMethod(vm, target, signature, new TestVarArg(vm, method));
    }

    private static boolean invokeNoArgBoolean(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              DvmObject<?> target, String methodName) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestVarArg(vm, method));
    }

    private static int invokeNoArgInt(AbstractJni jni, BaseVM vm, boolean useVaList,
                                      DvmObject<?> target, String methodName) {
        DvmClass dvmClass = target.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, methodName, "()I", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callIntMethodV(vm, target, signature, new TestVaList(vm, method));
        }
        return jni.callIntMethod(vm, target, signature, new TestVarArg(vm, method));
    }

    /**
     * getLinkProperties(Network) — implementation does not inspect arg; still pass a Network handle.
     */
    private static DvmObject<?> invokeGetLinkProperties(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                        DvmObject<?> connectivityManager,
                                                        DvmObject<?> network) {
        int networkHash = vm.addLocalObject(network);
        DvmClass dvmClass = connectivityManager.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getLinkProperties",
                "(Landroid/net/Network;)Landroid/net/LinkProperties;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, connectivityManager, signature,
                    new TestVaList(vm, method, networkHash));
        }
        return jni.callObjectMethod(vm, connectivityManager, signature,
                new TestVarArg(vm, method, networkHash));
    }

    private static DvmObject<?> invokeGetSystemService(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> app, String serviceName) {
        int nameHash = vm.addLocalObject(new StringObject(vm, serviceName));
        DvmClass dvmClass = app.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/String;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, app, signature, new TestVaList(vm, method, nameHash));
        }
        return jni.callObjectMethod(vm, app, signature, new TestVarArg(vm, method, nameHash));
    }

    private static DvmObject<?> invokeGetSystemServiceClass(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                            DvmObject<?> app, DvmClass serviceClass) {
        int classHash = vm.addLocalObject(serviceClass);
        DvmClass dvmClass = app.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, app, signature, new TestVaList(vm, method, classHash));
        }
        return jni.callObjectMethod(vm, app, signature, new TestVarArg(vm, method, classHash));
    }

    private static DvmObject<?> invokeListGet(AbstractJni jni, BaseVM vm, boolean useVaList,
                                              ArrayListObject list, int index) {
        // ArrayList.get is wired on callObjectMethodV; VarArg 32-bit path uses list contents directly.
        if (useVaList) {
            DvmClass dvmClass = list.getObjectType();
            DvmMethod method = new DvmMethod(dvmClass, "get", "(I)Ljava/lang/Object;", false);
            return jni.callObjectMethodV(vm, list, method.getSignature(),
                    new TestVaList(vm, method, index));
        }
        return list.getValue().get(index);
    }

    private static final class CapturedEvent {
        final String kind;
        final String api;
        final Object value;
        final String source;

        CapturedEvent(String kind, String api, Object value, String source) {
            this.kind = kind;
            this.api = api;
            this.value = value;
            this.source = source;
        }
    }

    private static final class CapturingSink implements TraceEnvironmentEventSink {
        final List<CapturedEvent> events = new ArrayList<CapturedEvent>();

        @Override
        public void emitEnvironmentEvent(String kind, String api, Object value, String source, String note) {
            events.add(new CapturedEvent(kind, api, value, source));
        }
    }

    private static final class TestVarArg extends VarArg {
        TestVarArg(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVarArg(BaseVM vm, DvmMethod method, int hash0) {
            super(vm, method);
            args.add(hash0);
        }
    }

    private static final class TestVaList extends VaList {
        TestVaList(BaseVM vm, DvmMethod method) {
            super(vm, method);
        }

        TestVaList(BaseVM vm, DvmMethod method, int hash0) {
            super(vm, method);
            args.add(hash0);
        }
    }
}

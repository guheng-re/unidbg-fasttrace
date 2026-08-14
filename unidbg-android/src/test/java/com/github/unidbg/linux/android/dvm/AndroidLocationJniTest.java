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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Coverage for {@code android.location} ({@code enabled} + {@code providers} +
 * {@code lastKnownLocations} + {@code providerCapabilities}) +
 * {@code LocationManager.isLocationEnabled()} / {@code isProviderEnabled(String)} /
 * {@code hasProvider(String)} /
 * {@code getAllProviders()} / {@code getProviders(boolean)} /
 * {@code getProvider(String)} / {@code LocationProvider.getName()} /
 * {@code LocationProvider.requiresNetwork()} /
 * {@code LocationProvider.requiresSatellite()} /
 * {@code LocationProvider.requiresCell()} /
 * {@code LocationProvider.hasMonetaryCost()} /
 * {@code LocationProvider.supportsAltitude()} /
 * {@code LocationProvider.supportsSpeed()} /
 * {@code LocationProvider.supportsBearing()} /
 * {@code LocationProvider.meetsCriteria(Criteria)} /
 * {@code LocationProvider.getAccuracy()} /
 * {@code LocationProvider.getPowerRequirement()} /
 * {@code getLastKnownLocation(String)} and {@code Location} marker getters
 * (SystemService location marker only; VarArg + VaList where available).
 */
public class AndroidLocationJniTest {

    private static final String LOCATION_MANAGER_CLASS = "android/location/LocationManager";
    private static final String LOCATION_CLASS = "android/location/Location";
    private static final String LOCATION_PROVIDER_CLASS = "android/location/LocationProvider";
    private static final String CRITERIA_CLASS = "android/location/Criteria";

    private static final String PROVIDERS_FULL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "}}"
            + "}"
            + "}";

    /** PROVIDERS_FULL_JSON plus gps/network {@code requiresNetwork}; passive entry omitted. */
    private static final String PROVIDERS_REQUIRES_NETWORK_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "},"
            + "\"providerCapabilities\":{"
            + "\"gps\":{\"requiresNetwork\":false},"
            + "\"network\":{\"requiresNetwork\":true}"
            + "}}"
            + "}"
            + "}";

    /** PROVIDERS_FULL_JSON plus gps/network {@code requiresSatellite}; passive entry omitted. */
    private static final String PROVIDERS_REQUIRES_SATELLITE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "},"
            + "\"providerCapabilities\":{"
            + "\"gps\":{\"requiresSatellite\":false},"
            + "\"network\":{\"requiresSatellite\":true}"
            + "}}"
            + "}"
            + "}";

    /** PROVIDERS_FULL_JSON plus gps/network {@code requiresCell}; passive entry omitted. */
    private static final String PROVIDERS_REQUIRES_CELL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "},"
            + "\"providerCapabilities\":{"
            + "\"gps\":{\"requiresCell\":false},"
            + "\"network\":{\"requiresCell\":true}"
            + "}}"
            + "}"
            + "}";

    /** PROVIDERS_FULL_JSON plus gps/network {@code hasMonetaryCost}; passive entry omitted. */
    private static final String PROVIDERS_HAS_MONETARY_COST_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "},"
            + "\"providerCapabilities\":{"
            + "\"gps\":{\"hasMonetaryCost\":false},"
            + "\"network\":{\"hasMonetaryCost\":true}"
            + "}}"
            + "}"
            + "}";

    /** PROVIDERS_FULL_JSON plus gps/network {@code supportsAltitude}; passive entry omitted. */
    private static final String PROVIDERS_SUPPORTS_ALTITUDE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "},"
            + "\"providerCapabilities\":{"
            + "\"gps\":{\"supportsAltitude\":false},"
            + "\"network\":{\"supportsAltitude\":true}"
            + "}}"
            + "}"
            + "}";

    /** PROVIDERS_FULL_JSON plus gps/network {@code supportsSpeed}; passive entry omitted. */
    private static final String PROVIDERS_SUPPORTS_SPEED_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "},"
            + "\"providerCapabilities\":{"
            + "\"gps\":{\"supportsSpeed\":false},"
            + "\"network\":{\"supportsSpeed\":true}"
            + "}}"
            + "}"
            + "}";

    /** PROVIDERS_FULL_JSON plus gps/network {@code supportsBearing}; passive entry omitted. */
    private static final String PROVIDERS_SUPPORTS_BEARING_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "},"
            + "\"providerCapabilities\":{"
            + "\"gps\":{\"supportsBearing\":false},"
            + "\"network\":{\"supportsBearing\":true}"
            + "}}"
            + "}"
            + "}";

    /** PROVIDERS_FULL_JSON plus gps/network {@code meetsCriteria}; passive entry omitted. */
    private static final String PROVIDERS_MEETS_CRITERIA_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "},"
            + "\"providerCapabilities\":{"
            + "\"gps\":{\"meetsCriteria\":false},"
            + "\"network\":{\"meetsCriteria\":true}"
            + "}}"
            + "}"
            + "}";

    /** PROVIDERS_FULL_JSON plus gps/network {@code accuracy} 1/2; passive entry omitted. */
    private static final String PROVIDERS_ACCURACY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "},"
            + "\"providerCapabilities\":{"
            + "\"gps\":{\"accuracy\":1},"
            + "\"network\":{\"accuracy\":2}"
            + "}}"
            + "}"
            + "}";

    /**
     * PROVIDERS_ACCURACY_JSON plus last-known gps {@code accuracyMeters} so
     * {@code Location.getAccuracy()F} stays distinct from {@code LocationProvider.getAccuracy()I}.
     */
    private static final String PROVIDERS_ACCURACY_WITH_LAST_KNOWN_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "},"
            + "\"providerCapabilities\":{"
            + "\"gps\":{\"accuracy\":1},"
            + "\"network\":{\"accuracy\":2}"
            + "},"
            + "\"lastKnownLocations\":[{"
            + "\"provider\":\"gps\","
            + "\"latitude\":31.2304,"
            + "\"longitude\":121.4737,"
            + "\"accuracyMeters\":8.25"
            + "}]}"
            + "}"
            + "}";

    /** PROVIDERS_FULL_JSON plus gps/network {@code powerRequirement} 1/3; passive entry omitted. */
    private static final String PROVIDERS_POWER_REQUIREMENT_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "},"
            + "\"providerCapabilities\":{"
            + "\"gps\":{\"powerRequirement\":1},"
            + "\"network\":{\"powerRequirement\":3}"
            + "}}"
            + "}"
            + "}";

    private static final String PROVIDERS_CAPABILITIES_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "},"
            + "\"providerCapabilities\":{}}"
            + "}"
            + "}";

    private static final String PROVIDERS_CAPABILITIES_GPS_EMPTY_ENTRY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"gps\":true,\"network\":false,\"passive\":true"
            + "},"
            + "\"providerCapabilities\":{\"gps\":{}}}"
            + "}"
            + "}";

    private static final String CAPABILITIES_WITHOUT_PROVIDERS_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providerCapabilities\":{"
            + "\"gps\":{\"requiresNetwork\":true}"
            + "}}"
            + "}"
            + "}";

    private static final String LAST_KNOWN_FULL_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{"
            + "\"lastKnownLocations\":[{"
            + "\"provider\":\"gps\","
            + "\"latitude\":31.2304,"
            + "\"longitude\":121.4737,"
            + "\"altitude\":12.5,"
            + "\"accuracyMeters\":8.25,"
            + "\"timeMillis\":1700000000000,"
            + "\"elapsedRealtimeNanos\":9000000000000,"
            + "\"mock\":false,"
            + "\"speedMetersPerSecond\":1.5,"
            + "\"bearingDegrees\":90.0,"
            + "\"verticalAccuracyMeters\":2.5,"
            + "\"speedAccuracyMetersPerSecond\":0.25,"
            + "\"bearingAccuracyDegrees\":5.0"
            + "},{"
            + "\"provider\":\"network\","
            + "\"latitude\":-33.8688,"
            + "\"longitude\":151.2093,"
            + "\"mock\":true"
            + "}]"
            + "}"
            + "}"
            + "}";

    private static final String LAST_KNOWN_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"lastKnownLocations\":[]}"
            + "}"
            + "}";

    private static final String PROVIDERS_EMPTY_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{}}"
            + "}"
            + "}";

    private static final String PROVIDERS_PARTIAL_ORDER_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"providers\":{"
            + "\"passive\":false,\"gps\":true"
            + "}}"
            + "}"
            + "}";

    private static final String NO_LOCATION_JSON = "{"
            + "\"android\":{\"packageName\":\"com.demo.app\"}"
            + "}";

    /** @deprecated use NO_LOCATION_JSON; kept for older provider-absent tests */
    private static final String NO_PROVIDERS_JSON = NO_LOCATION_JSON;

    private static final String LOCATION_WITHOUT_PROVIDERS_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{}"
            + "}"
            + "}";

    private static final String LOCATION_ENABLED_TRUE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"enabled\":true}"
            + "}"
            + "}";

    private static final String LOCATION_ENABLED_FALSE_JSON = "{"
            + "\"android\":{"
            + "\"packageName\":\"com.demo.app\","
            + "\"location\":{\"enabled\":false}"
            + "}"
            + "}";

    @Test
    public void testIsProviderEnabledValuesVarArg32() throws Exception {
        runConfiguredProviders(false, false, PROVIDERS_FULL_JSON, true, false, true);
    }

    @Test
    public void testIsProviderEnabledValuesVaList64() throws Exception {
        runConfiguredProviders(true, true, PROVIDERS_FULL_JSON, true, false, true);
    }

    @Test
    public void testIsProviderEnabledDefaultFalseVarArg32() throws Exception {
        runConfiguredProviders(false, false, PROVIDERS_EMPTY_JSON, false, false, false);
    }

    @Test
    public void testIsProviderEnabledDefaultFalseVaList64() throws Exception {
        runConfiguredProviders(true, true, PROVIDERS_EMPTY_JSON, false, false, false);
    }

    @Test
    public void testIsProviderEnabledAbsentVarArg32() throws Exception {
        runAbsentProviders(false, false, NO_PROVIDERS_JSON);
    }

    @Test
    public void testIsProviderEnabledAbsentVaList64() throws Exception {
        runAbsentProviders(true, true, NO_PROVIDERS_JSON);
    }

    @Test
    public void testLocationWithoutProvidersAbsentVarArg32() throws Exception {
        runAbsentProviders(false, false, LOCATION_WITHOUT_PROVIDERS_JSON);
    }

    @Test
    public void testLocationWithoutProvidersAbsentVaList64() throws Exception {
        runAbsentProviders(true, true, LOCATION_WITHOUT_PROVIDERS_JSON);
    }

    @Test
    public void testPlainInvalidIsolationVarArg32() throws Exception {
        runPlainInvalidIsolation(false, false);
    }

    @Test
    public void testPlainInvalidIsolationVaList64() throws Exception {
        runPlainInvalidIsolation(true, true);
    }

    @Test
    public void testLocationServiceMarkerVarArg32() throws Exception {
        runLocationServiceMarker(false, false);
    }

    @Test
    public void testLocationServiceMarkerVaList64() throws Exception {
        runLocationServiceMarker(true, true);
    }

    @Test
    public void testTypedGetSystemServiceVarArg32() throws Exception {
        runTypedGetSystemService(false, false);
    }

    @Test
    public void testTypedGetSystemServiceVaList64() throws Exception {
        runTypedGetSystemService(true, true);
    }

    @Test
    public void testGetAllProvidersOrderedIncludingDisabledVarArg32() throws Exception {
        runGetAllProviders(false, false, PROVIDERS_FULL_JSON,
                new String[] {"gps", "network", "passive"},
                "count=3,providers=gps,network,passive");
    }

    @Test
    public void testGetAllProvidersOrderedIncludingDisabledVaList64() throws Exception {
        runGetAllProviders(true, true, PROVIDERS_FULL_JSON,
                new String[] {"gps", "network", "passive"},
                "count=3,providers=gps,network,passive");
    }

    @Test
    public void testGetAllProvidersPartialOrderVarArg32() throws Exception {
        // JSON key order must not affect list order; missing network key is not listed
        runGetAllProviders(false, false, PROVIDERS_PARTIAL_ORDER_JSON,
                new String[] {"gps", "passive"},
                "count=2,providers=gps,passive");
    }

    @Test
    public void testGetAllProvidersPartialOrderVaList64() throws Exception {
        runGetAllProviders(true, true, PROVIDERS_PARTIAL_ORDER_JSON,
                new String[] {"gps", "passive"},
                "count=2,providers=gps,passive");
    }

    @Test
    public void testGetAllProvidersEmptyListVarArg32() throws Exception {
        runGetAllProviders(false, false, PROVIDERS_EMPTY_JSON,
                new String[] {},
                "count=0,providers=");
    }

    @Test
    public void testGetAllProvidersEmptyListVaList64() throws Exception {
        runGetAllProviders(true, true, PROVIDERS_EMPTY_JSON,
                new String[] {},
                "count=0,providers=");
    }

    @Test
    public void testGetAllProvidersAbsentPlainIsolationVarArg32() throws Exception {
        runGetAllProvidersAbsentPlainIsolation(false, false);
    }

    @Test
    public void testGetAllProvidersAbsentPlainIsolationVaList64() throws Exception {
        runGetAllProvidersAbsentPlainIsolation(true, true);
    }

    @Test
    public void testGetProvidersEnabledOnlyFalseIncludingDisabledVarArg32() throws Exception {
        // enabledOnly=false: same selection as getAllProviders (all explicit keys)
        runGetProviders(false, false, PROVIDERS_FULL_JSON, false,
                new String[] {"gps", "network", "passive"},
                "enabledOnly=false,count=3,providers=gps,network,passive");
    }

    @Test
    public void testGetProvidersEnabledOnlyFalseIncludingDisabledVaList64() throws Exception {
        runGetProviders(true, true, PROVIDERS_FULL_JSON, false,
                new String[] {"gps", "network", "passive"},
                "enabledOnly=false,count=3,providers=gps,network,passive");
    }

    @Test
    public void testGetProvidersEnabledOnlyTrueFiltersDisabledVarArg32() throws Exception {
        // FULL: gps=true, network=false, passive=true → only gps,passive
        runGetProviders(false, false, PROVIDERS_FULL_JSON, true,
                new String[] {"gps", "passive"},
                "enabledOnly=true,count=2,providers=gps,passive");
    }

    @Test
    public void testGetProvidersEnabledOnlyTrueFiltersDisabledVaList64() throws Exception {
        runGetProviders(true, true, PROVIDERS_FULL_JSON, true,
                new String[] {"gps", "passive"},
                "enabledOnly=true,count=2,providers=gps,passive");
    }

    @Test
    public void testGetProvidersPartialOrderVarArg32() throws Exception {
        // partial: gps=true, passive=false, network omitted
        runGetProviders(false, false, PROVIDERS_PARTIAL_ORDER_JSON, false,
                new String[] {"gps", "passive"},
                "enabledOnly=false,count=2,providers=gps,passive");
        runGetProviders(false, false, PROVIDERS_PARTIAL_ORDER_JSON, true,
                new String[] {"gps"},
                "enabledOnly=true,count=1,providers=gps");
    }

    @Test
    public void testGetProvidersPartialOrderVaList64() throws Exception {
        runGetProviders(true, true, PROVIDERS_PARTIAL_ORDER_JSON, false,
                new String[] {"gps", "passive"},
                "enabledOnly=false,count=2,providers=gps,passive");
        runGetProviders(true, true, PROVIDERS_PARTIAL_ORDER_JSON, true,
                new String[] {"gps"},
                "enabledOnly=true,count=1,providers=gps");
    }

    @Test
    public void testGetProvidersEmptyListVarArg32() throws Exception {
        runGetProviders(false, false, PROVIDERS_EMPTY_JSON, false,
                new String[] {},
                "enabledOnly=false,count=0,providers=");
        runGetProviders(false, false, PROVIDERS_EMPTY_JSON, true,
                new String[] {},
                "enabledOnly=true,count=0,providers=");
    }

    @Test
    public void testGetProvidersEmptyListVaList64() throws Exception {
        runGetProviders(true, true, PROVIDERS_EMPTY_JSON, false,
                new String[] {},
                "enabledOnly=false,count=0,providers=");
        runGetProviders(true, true, PROVIDERS_EMPTY_JSON, true,
                new String[] {},
                "enabledOnly=true,count=0,providers=");
    }

    @Test
    public void testGetProvidersAbsentPlainIsolationVarArg32() throws Exception {
        runGetProvidersAbsentPlainIsolation(false, false);
    }

    @Test
    public void testGetProvidersAbsentPlainIsolationVaList64() throws Exception {
        runGetProvidersAbsentPlainIsolation(true, true);
    }

    @Test
    public void testGetProviderExplicitIncludingDisabledVarArg32() throws Exception {
        runGetProviderExplicitIncludingDisabled(false, false);
    }

    @Test
    public void testGetProviderExplicitIncludingDisabledVaList64() throws Exception {
        runGetProviderExplicitIncludingDisabled(true, true);
    }

    @Test
    public void testGetProviderOmittedUnknownNullVarArg32() throws Exception {
        runGetProviderOmittedUnknownNull(false, false);
    }

    @Test
    public void testGetProviderOmittedUnknownNullVaList64() throws Exception {
        runGetProviderOmittedUnknownNull(true, true);
    }

    @Test
    public void testGetProviderAbsentPlainIsolationVarArg32() throws Exception {
        runGetProviderAbsentPlainIsolation(false, false);
    }

    @Test
    public void testGetProviderAbsentPlainIsolationVaList64() throws Exception {
        runGetProviderAbsentPlainIsolation(true, true);
    }

    @Test
    public void testGetProviderGetNameForeignStaleVarArg32() throws Exception {
        runGetProviderGetNameForeignStale(false, false);
    }

    @Test
    public void testGetProviderGetNameForeignStaleVaList64() throws Exception {
        runGetProviderGetNameForeignStale(true, true);
    }

    @Test
    public void testHasProviderExplicitIncludingDisabledVarArg32() throws Exception {
        runHasProviderExplicitIncludingDisabled(false, false);
    }

    @Test
    public void testHasProviderExplicitIncludingDisabledVaList64() throws Exception {
        runHasProviderExplicitIncludingDisabled(true, true);
    }

    @Test
    public void testHasProviderOmittedUnknownFalseVarArg32() throws Exception {
        runHasProviderOmittedUnknownFalse(false, false);
    }

    @Test
    public void testHasProviderOmittedUnknownFalseVaList64() throws Exception {
        runHasProviderOmittedUnknownFalse(true, true);
    }

    @Test
    public void testHasProviderAbsentPlainIsolationVarArg32() throws Exception {
        runHasProviderAbsentPlainIsolation(false, false);
    }

    @Test
    public void testHasProviderAbsentPlainIsolationVaList64() throws Exception {
        runHasProviderAbsentPlainIsolation(true, true);
    }

    @Test
    public void testRequiresNetworkConfiguredVarArg32() throws Exception {
        runRequiresNetworkConfigured(false, false);
    }

    @Test
    public void testRequiresNetworkConfiguredVaList64() throws Exception {
        runRequiresNetworkConfigured(true, true);
    }

    @Test
    public void testRequiresNetworkAbsentPlainIsolationVarArg32() throws Exception {
        runRequiresNetworkAbsentPlainIsolation(false, false);
    }

    @Test
    public void testRequiresNetworkAbsentPlainIsolationVaList64() throws Exception {
        runRequiresNetworkAbsentPlainIsolation(true, true);
    }

    @Test
    public void testRequiresNetworkForeignStaleVarArg32() throws Exception {
        runRequiresNetworkForeignStale(false, false);
    }

    @Test
    public void testRequiresNetworkForeignStaleVaList64() throws Exception {
        runRequiresNetworkForeignStale(true, true);
    }

    @Test
    public void testRequiresSatelliteConfiguredVarArg32() throws Exception {
        runRequiresSatelliteConfigured(false, false);
    }

    @Test
    public void testRequiresSatelliteConfiguredVaList64() throws Exception {
        runRequiresSatelliteConfigured(true, true);
    }

    @Test
    public void testRequiresSatelliteAbsentPlainIsolationVarArg32() throws Exception {
        runRequiresSatelliteAbsentPlainIsolation(false, false);
    }

    @Test
    public void testRequiresSatelliteAbsentPlainIsolationVaList64() throws Exception {
        runRequiresSatelliteAbsentPlainIsolation(true, true);
    }

    @Test
    public void testRequiresSatelliteForeignStaleVarArg32() throws Exception {
        runRequiresSatelliteForeignStale(false, false);
    }

    @Test
    public void testRequiresSatelliteForeignStaleVaList64() throws Exception {
        runRequiresSatelliteForeignStale(true, true);
    }

    @Test
    public void testRequiresCellConfiguredVarArg32() throws Exception {
        runRequiresCellConfigured(false, false);
    }

    @Test
    public void testRequiresCellConfiguredVaList64() throws Exception {
        runRequiresCellConfigured(true, true);
    }

    @Test
    public void testRequiresCellAbsentPlainIsolationVarArg32() throws Exception {
        runRequiresCellAbsentPlainIsolation(false, false);
    }

    @Test
    public void testRequiresCellAbsentPlainIsolationVaList64() throws Exception {
        runRequiresCellAbsentPlainIsolation(true, true);
    }

    @Test
    public void testRequiresCellForeignStaleVarArg32() throws Exception {
        runRequiresCellForeignStale(false, false);
    }

    @Test
    public void testRequiresCellForeignStaleVaList64() throws Exception {
        runRequiresCellForeignStale(true, true);
    }

    @Test
    public void testHasMonetaryCostConfiguredVarArg32() throws Exception {
        runHasMonetaryCostConfigured(false, false);
    }

    @Test
    public void testHasMonetaryCostConfiguredVaList64() throws Exception {
        runHasMonetaryCostConfigured(true, true);
    }

    @Test
    public void testHasMonetaryCostAbsentPlainIsolationVarArg32() throws Exception {
        runHasMonetaryCostAbsentPlainIsolation(false, false);
    }

    @Test
    public void testHasMonetaryCostAbsentPlainIsolationVaList64() throws Exception {
        runHasMonetaryCostAbsentPlainIsolation(true, true);
    }

    @Test
    public void testHasMonetaryCostForeignStaleVarArg32() throws Exception {
        runHasMonetaryCostForeignStale(false, false);
    }

    @Test
    public void testHasMonetaryCostForeignStaleVaList64() throws Exception {
        runHasMonetaryCostForeignStale(true, true);
    }

    @Test
    public void testSupportsAltitudeConfiguredVarArg32() throws Exception {
        runSupportsAltitudeConfigured(false, false);
    }

    @Test
    public void testSupportsAltitudeConfiguredVaList64() throws Exception {
        runSupportsAltitudeConfigured(true, true);
    }

    @Test
    public void testSupportsAltitudeAbsentPlainIsolationVarArg32() throws Exception {
        runSupportsAltitudeAbsentPlainIsolation(false, false);
    }

    @Test
    public void testSupportsAltitudeAbsentPlainIsolationVaList64() throws Exception {
        runSupportsAltitudeAbsentPlainIsolation(true, true);
    }

    @Test
    public void testSupportsAltitudeForeignStaleVarArg32() throws Exception {
        runSupportsAltitudeForeignStale(false, false);
    }

    @Test
    public void testSupportsAltitudeForeignStaleVaList64() throws Exception {
        runSupportsAltitudeForeignStale(true, true);
    }

    @Test
    public void testSupportsSpeedConfiguredVarArg32() throws Exception {
        runSupportsSpeedConfigured(false, false);
    }

    @Test
    public void testSupportsSpeedConfiguredVaList64() throws Exception {
        runSupportsSpeedConfigured(true, true);
    }

    @Test
    public void testSupportsSpeedAbsentPlainIsolationVarArg32() throws Exception {
        runSupportsSpeedAbsentPlainIsolation(false, false);
    }

    @Test
    public void testSupportsSpeedAbsentPlainIsolationVaList64() throws Exception {
        runSupportsSpeedAbsentPlainIsolation(true, true);
    }

    @Test
    public void testSupportsSpeedForeignStaleVarArg32() throws Exception {
        runSupportsSpeedForeignStale(false, false);
    }

    @Test
    public void testSupportsSpeedForeignStaleVaList64() throws Exception {
        runSupportsSpeedForeignStale(true, true);
    }

    @Test
    public void testSupportsBearingConfiguredVarArg32() throws Exception {
        runSupportsBearingConfigured(false, false);
    }

    @Test
    public void testSupportsBearingConfiguredVaList64() throws Exception {
        runSupportsBearingConfigured(true, true);
    }

    @Test
    public void testSupportsBearingAbsentPlainIsolationVarArg32() throws Exception {
        runSupportsBearingAbsentPlainIsolation(false, false);
    }

    @Test
    public void testSupportsBearingAbsentPlainIsolationVaList64() throws Exception {
        runSupportsBearingAbsentPlainIsolation(true, true);
    }

    @Test
    public void testSupportsBearingForeignStaleVarArg32() throws Exception {
        runSupportsBearingForeignStale(false, false);
    }

    @Test
    public void testSupportsBearingForeignStaleVaList64() throws Exception {
        runSupportsBearingForeignStale(true, true);
    }

    @Test
    public void testAccuracyConfiguredVarArg32() throws Exception {
        runAccuracyConfigured(false, false);
    }

    @Test
    public void testAccuracyConfiguredVaList64() throws Exception {
        runAccuracyConfigured(true, true);
    }

    @Test
    public void testAccuracyAbsentPlainIsolationVarArg32() throws Exception {
        runAccuracyAbsentPlainIsolation(false, false);
    }

    @Test
    public void testAccuracyAbsentPlainIsolationVaList64() throws Exception {
        runAccuracyAbsentPlainIsolation(true, true);
    }

    @Test
    public void testAccuracyForeignStaleVarArg32() throws Exception {
        runAccuracyForeignStale(false, false);
    }

    @Test
    public void testAccuracyForeignStaleVaList64() throws Exception {
        runAccuracyForeignStale(true, true);
    }

    @Test
    public void testPowerRequirementConfiguredVarArg32() throws Exception {
        runPowerRequirementConfigured(false, false);
    }

    @Test
    public void testPowerRequirementConfiguredVaList64() throws Exception {
        runPowerRequirementConfigured(true, true);
    }

    @Test
    public void testPowerRequirementAbsentPlainIsolationVarArg32() throws Exception {
        runPowerRequirementAbsentPlainIsolation(false, false);
    }

    @Test
    public void testPowerRequirementAbsentPlainIsolationVaList64() throws Exception {
        runPowerRequirementAbsentPlainIsolation(true, true);
    }

    @Test
    public void testPowerRequirementForeignStaleVarArg32() throws Exception {
        runPowerRequirementForeignStale(false, false);
    }

    @Test
    public void testPowerRequirementForeignStaleVaList64() throws Exception {
        runPowerRequirementForeignStale(true, true);
    }

    @Test
    public void testMeetsCriteriaConfiguredVarArg32() throws Exception {
        runMeetsCriteriaConfigured(false, false);
    }

    @Test
    public void testMeetsCriteriaConfiguredVaList64() throws Exception {
        runMeetsCriteriaConfigured(true, true);
    }

    @Test
    public void testMeetsCriteriaAbsentPlainIsolationVarArg32() throws Exception {
        runMeetsCriteriaAbsentPlainIsolation(false, false);
    }

    @Test
    public void testMeetsCriteriaAbsentPlainIsolationVaList64() throws Exception {
        runMeetsCriteriaAbsentPlainIsolation(true, true);
    }

    @Test
    public void testMeetsCriteriaForeignStaleVarArg32() throws Exception {
        runMeetsCriteriaForeignStale(false, false);
    }

    @Test
    public void testMeetsCriteriaForeignStaleVaList64() throws Exception {
        runMeetsCriteriaForeignStale(true, true);
    }

    @Test
    public void testProviderCapabilitiesJsonParse() {
        TraceEnvironmentConfig missing = TraceEnvironmentConfig.parse(NO_LOCATION_JSON);
        assertFalse(missing.isAndroidLocationProviderCapabilitiesConfigured());
        assertTrue(missing.getAndroidLocationProviderCapabilitiesConfig() == null);
        assertTrue(missing.getAndroidLocationProviderCapability("gps") == null);

        TraceEnvironmentConfig locationEmpty = TraceEnvironmentConfig.parse(
                LOCATION_WITHOUT_PROVIDERS_JSON);
        assertFalse(locationEmpty.isAndroidLocationProviderCapabilitiesConfigured());
        assertTrue(locationEmpty.getAndroidLocationProviderCapabilitiesConfig() == null);

        TraceEnvironmentConfig providersOnly = TraceEnvironmentConfig.parse(PROVIDERS_FULL_JSON);
        assertTrue(providersOnly.isAndroidLocationProvidersConfigured());
        assertFalse(providersOnly.isAndroidLocationProviderCapabilitiesConfigured());
        assertTrue(providersOnly.getAndroidLocationProviderCapabilitiesConfig() == null);

        TraceEnvironmentConfig emptyCaps = TraceEnvironmentConfig.parse(
                PROVIDERS_CAPABILITIES_EMPTY_JSON);
        assertTrue(emptyCaps.isAndroidLocationProviderCapabilitiesConfigured());
        TraceEnvironmentConfig.AndroidLocationProviderCapabilitiesConfig emptyView =
                emptyCaps.getAndroidLocationProviderCapabilitiesConfig();
        assertNotNull(emptyView);
        assertFalse(emptyView.isGpsConfigured());
        assertTrue(emptyView.getGps() == null);
        assertTrue(emptyView.get("gps") == null);
        assertFalse(emptyView.isNetworkConfigured());
        assertFalse(emptyView.isPassiveConfigured());
        assertTrue(emptyCaps.isAndroidLocationProvidersConfigured());
        assertTrue(emptyCaps.getAndroidLocationProvidersConfig().isGps());
        assertFalse(emptyCaps.getAndroidLocationProvidersConfig().isNetwork());

        TraceEnvironmentConfig full = TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_NETWORK_JSON);
        assertTrue(full.isAndroidLocationProvidersConfigured());
        assertTrue(full.getAndroidLocationProvidersConfig().isGps());
        assertFalse(full.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(full.getAndroidLocationProvidersConfig().isPassive());
        assertTrue(full.isAndroidLocationProviderCapabilitiesConfigured());
        TraceEnvironmentConfig.AndroidLocationProviderCapabilitiesConfig caps =
                full.getAndroidLocationProviderCapabilitiesConfig();
        assertNotNull(caps);
        assertTrue(caps.isGpsConfigured());
        assertTrue(caps.getGps().isRequiresNetworkConfigured());
        assertFalse(caps.getGps().isRequiresNetwork());
        assertFalse(caps.getGps().isRequiresSatelliteConfigured());
        assertFalse(caps.getGps().isRequiresSatellite());
        assertFalse(caps.getGps().isRequiresCellConfigured());
        assertFalse(caps.getGps().isRequiresCell());
        assertFalse(caps.getGps().isHasMonetaryCostConfigured());
        assertFalse(caps.getGps().isHasMonetaryCost());
        assertFalse(caps.getGps().isSupportsAltitudeConfigured());
        assertFalse(caps.getGps().isSupportsAltitude());
        assertFalse(caps.getGps().isSupportsSpeedConfigured());
        assertFalse(caps.getGps().isSupportsSpeed());
        assertFalse(caps.getGps().isSupportsBearingConfigured());
        assertFalse(caps.getGps().isSupportsBearing());
        assertFalse(caps.getGps().isMeetsCriteriaConfigured());
        assertFalse(caps.getGps().isMeetsCriteria());
        assertFalse(caps.getGps().isAccuracyConfigured());
        assertEquals(0, caps.getGps().getAccuracy());
        assertFalse(caps.getGps().isPowerRequirementConfigured());
        assertEquals(0, caps.getGps().getPowerRequirement());
        assertTrue(caps.isNetworkConfigured());
        assertTrue(caps.getNetwork().isRequiresNetworkConfigured());
        assertTrue(caps.getNetwork().isRequiresNetwork());
        assertFalse(caps.getNetwork().isRequiresSatelliteConfigured());
        assertFalse(caps.getNetwork().isRequiresSatellite());
        assertFalse(caps.getNetwork().isRequiresCellConfigured());
        assertFalse(caps.getNetwork().isRequiresCell());
        assertFalse(caps.getNetwork().isHasMonetaryCostConfigured());
        assertFalse(caps.getNetwork().isHasMonetaryCost());
        assertFalse(caps.getNetwork().isSupportsAltitudeConfigured());
        assertFalse(caps.getNetwork().isSupportsAltitude());
        assertFalse(caps.getNetwork().isSupportsSpeedConfigured());
        assertFalse(caps.getNetwork().isSupportsSpeed());
        assertFalse(caps.getNetwork().isSupportsBearingConfigured());
        assertFalse(caps.getNetwork().isSupportsBearing());
        assertFalse(caps.getNetwork().isMeetsCriteriaConfigured());
        assertFalse(caps.getNetwork().isMeetsCriteria());
        assertFalse(caps.getNetwork().isAccuracyConfigured());
        assertEquals(0, caps.getNetwork().getAccuracy());
        assertFalse(caps.getNetwork().isPowerRequirementConfigured());
        assertEquals(0, caps.getNetwork().getPowerRequirement());
        assertFalse(caps.isPassiveConfigured());
        assertTrue(caps.getPassive() == null);
        assertTrue(caps.get("gps") == caps.getGps());
        assertTrue(full.getAndroidLocationProviderCapability("network") == caps.getNetwork());
        assertTrue(full.getAndroidLocationProviderCapability("passive") == null);
        assertTrue(full.getAndroidLocationProviderCapability("fused") == null);

        TraceEnvironmentConfig emptyEntry = TraceEnvironmentConfig.parse(
                PROVIDERS_CAPABILITIES_GPS_EMPTY_ENTRY_JSON);
        assertTrue(emptyEntry.isAndroidLocationProviderCapabilitiesConfigured());
        assertTrue(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().isGpsConfigured());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isRequiresNetworkConfigured());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isRequiresNetwork());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isRequiresSatelliteConfigured());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isRequiresSatellite());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isRequiresCellConfigured());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isRequiresCell());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isHasMonetaryCostConfigured());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isHasMonetaryCost());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isSupportsAltitudeConfigured());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isSupportsAltitude());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isSupportsSpeedConfigured());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isSupportsSpeed());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isSupportsBearingConfigured());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isSupportsBearing());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isMeetsCriteriaConfigured());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isMeetsCriteria());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isAccuracyConfigured());
        assertEquals(0, emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .getAccuracy());
        assertFalse(emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .isPowerRequirementConfigured());
        assertEquals(0, emptyEntry.getAndroidLocationProviderCapabilitiesConfig().getGps()
                .getPowerRequirement());

        TraceEnvironmentConfig satellite = TraceEnvironmentConfig.parse(
                PROVIDERS_REQUIRES_SATELLITE_JSON);
        assertTrue(satellite.isAndroidLocationProvidersConfigured());
        assertTrue(satellite.getAndroidLocationProvidersConfig().isGps());
        assertFalse(satellite.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(satellite.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isRequiresNetworkConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isRequiresNetwork());
        assertTrue(satellite.getAndroidLocationProviderCapability("gps").isRequiresSatelliteConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isRequiresSatellite());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isRequiresCellConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isRequiresCell());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isHasMonetaryCostConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isHasMonetaryCost());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isSupportsAltitudeConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isSupportsAltitude());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isSupportsSpeedConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isSupportsSpeed());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isSupportsBearingConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isSupportsBearing());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isMeetsCriteriaConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isMeetsCriteria());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isAccuracyConfigured());
        assertEquals(0, satellite.getAndroidLocationProviderCapability("gps").getAccuracy());
        assertFalse(satellite.getAndroidLocationProviderCapability("gps").isPowerRequirementConfigured());
        assertEquals(0, satellite.getAndroidLocationProviderCapability("gps").getPowerRequirement());
        assertFalse(satellite.getAndroidLocationProviderCapability("network").isRequiresNetworkConfigured());
        assertTrue(satellite.getAndroidLocationProviderCapability("network").isRequiresSatelliteConfigured());
        assertTrue(satellite.getAndroidLocationProviderCapability("network").isRequiresSatellite());
        assertFalse(satellite.getAndroidLocationProviderCapability("network").isRequiresCellConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("network").isHasMonetaryCostConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("network").isSupportsAltitudeConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("network").isSupportsSpeedConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("network").isSupportsBearingConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("network").isMeetsCriteriaConfigured());
        assertFalse(satellite.getAndroidLocationProviderCapability("network").isAccuracyConfigured());
        assertEquals(0, satellite.getAndroidLocationProviderCapability("network").getAccuracy());
        assertFalse(satellite.getAndroidLocationProviderCapability("network").isPowerRequirementConfigured());
        assertEquals(0, satellite.getAndroidLocationProviderCapability("network").getPowerRequirement());
        assertTrue(satellite.getAndroidLocationProviderCapability("passive") == null);

        TraceEnvironmentConfig cell = TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_CELL_JSON);
        assertTrue(cell.isAndroidLocationProvidersConfigured());
        assertTrue(cell.getAndroidLocationProvidersConfig().isGps());
        assertFalse(cell.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(cell.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isRequiresNetworkConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isRequiresNetwork());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isRequiresSatelliteConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isRequiresSatellite());
        assertTrue(cell.getAndroidLocationProviderCapability("gps").isRequiresCellConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isRequiresCell());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isHasMonetaryCostConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isHasMonetaryCost());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isSupportsAltitudeConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isSupportsAltitude());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isSupportsSpeedConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isSupportsSpeed());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isSupportsBearingConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isSupportsBearing());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isMeetsCriteriaConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isMeetsCriteria());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isAccuracyConfigured());
        assertEquals(0, cell.getAndroidLocationProviderCapability("gps").getAccuracy());
        assertFalse(cell.getAndroidLocationProviderCapability("gps").isPowerRequirementConfigured());
        assertEquals(0, cell.getAndroidLocationProviderCapability("gps").getPowerRequirement());
        assertFalse(cell.getAndroidLocationProviderCapability("network").isRequiresNetworkConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("network").isRequiresSatelliteConfigured());
        assertTrue(cell.getAndroidLocationProviderCapability("network").isRequiresCellConfigured());
        assertTrue(cell.getAndroidLocationProviderCapability("network").isRequiresCell());
        assertFalse(cell.getAndroidLocationProviderCapability("network").isHasMonetaryCostConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("network").isSupportsAltitudeConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("network").isSupportsSpeedConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("network").isSupportsBearingConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("network").isMeetsCriteriaConfigured());
        assertFalse(cell.getAndroidLocationProviderCapability("network").isAccuracyConfigured());
        assertEquals(0, cell.getAndroidLocationProviderCapability("network").getAccuracy());
        assertFalse(cell.getAndroidLocationProviderCapability("network").isPowerRequirementConfigured());
        assertEquals(0, cell.getAndroidLocationProviderCapability("network").getPowerRequirement());
        assertTrue(cell.getAndroidLocationProviderCapability("passive") == null);

        TraceEnvironmentConfig monetary = TraceEnvironmentConfig.parse(
                PROVIDERS_HAS_MONETARY_COST_JSON);
        assertTrue(monetary.isAndroidLocationProvidersConfigured());
        assertTrue(monetary.getAndroidLocationProvidersConfig().isGps());
        assertFalse(monetary.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(monetary.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isRequiresNetworkConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isRequiresNetwork());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isRequiresSatelliteConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isRequiresSatellite());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isRequiresCellConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isRequiresCell());
        assertTrue(monetary.getAndroidLocationProviderCapability("gps").isHasMonetaryCostConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isHasMonetaryCost());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isSupportsAltitudeConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isSupportsAltitude());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isSupportsSpeedConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isSupportsSpeed());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isSupportsBearingConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isSupportsBearing());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isMeetsCriteriaConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isMeetsCriteria());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isAccuracyConfigured());
        assertEquals(0, monetary.getAndroidLocationProviderCapability("gps").getAccuracy());
        assertFalse(monetary.getAndroidLocationProviderCapability("gps").isPowerRequirementConfigured());
        assertEquals(0, monetary.getAndroidLocationProviderCapability("gps").getPowerRequirement());
        assertFalse(monetary.getAndroidLocationProviderCapability("network").isRequiresNetworkConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("network").isRequiresSatelliteConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("network").isRequiresCellConfigured());
        assertTrue(monetary.getAndroidLocationProviderCapability("network").isHasMonetaryCostConfigured());
        assertTrue(monetary.getAndroidLocationProviderCapability("network").isHasMonetaryCost());
        assertFalse(monetary.getAndroidLocationProviderCapability("network").isSupportsAltitudeConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("network").isSupportsSpeedConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("network").isSupportsBearingConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("network").isMeetsCriteriaConfigured());
        assertFalse(monetary.getAndroidLocationProviderCapability("network").isAccuracyConfigured());
        assertEquals(0, monetary.getAndroidLocationProviderCapability("network").getAccuracy());
        assertFalse(monetary.getAndroidLocationProviderCapability("network").isPowerRequirementConfigured());
        assertEquals(0, monetary.getAndroidLocationProviderCapability("network").getPowerRequirement());
        assertTrue(monetary.getAndroidLocationProviderCapability("passive") == null);

        TraceEnvironmentConfig altitude = TraceEnvironmentConfig.parse(
                PROVIDERS_SUPPORTS_ALTITUDE_JSON);
        assertTrue(altitude.isAndroidLocationProvidersConfigured());
        assertTrue(altitude.getAndroidLocationProvidersConfig().isGps());
        assertFalse(altitude.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(altitude.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isRequiresNetworkConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isRequiresNetwork());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isRequiresSatelliteConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isRequiresSatellite());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isRequiresCellConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isRequiresCell());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isHasMonetaryCostConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isHasMonetaryCost());
        assertTrue(altitude.getAndroidLocationProviderCapability("gps").isSupportsAltitudeConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isSupportsAltitude());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isSupportsSpeedConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isSupportsSpeed());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isSupportsBearingConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isSupportsBearing());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isMeetsCriteriaConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isMeetsCriteria());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isAccuracyConfigured());
        assertEquals(0, altitude.getAndroidLocationProviderCapability("gps").getAccuracy());
        assertFalse(altitude.getAndroidLocationProviderCapability("gps").isPowerRequirementConfigured());
        assertEquals(0, altitude.getAndroidLocationProviderCapability("gps").getPowerRequirement());
        assertFalse(altitude.getAndroidLocationProviderCapability("network").isRequiresNetworkConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("network").isRequiresSatelliteConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("network").isRequiresCellConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("network").isHasMonetaryCostConfigured());
        assertTrue(altitude.getAndroidLocationProviderCapability("network").isSupportsAltitudeConfigured());
        assertTrue(altitude.getAndroidLocationProviderCapability("network").isSupportsAltitude());
        assertFalse(altitude.getAndroidLocationProviderCapability("network").isSupportsSpeedConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("network").isSupportsBearingConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("network").isMeetsCriteriaConfigured());
        assertFalse(altitude.getAndroidLocationProviderCapability("network").isAccuracyConfigured());
        assertEquals(0, altitude.getAndroidLocationProviderCapability("network").getAccuracy());
        assertFalse(altitude.getAndroidLocationProviderCapability("network").isPowerRequirementConfigured());
        assertEquals(0, altitude.getAndroidLocationProviderCapability("network").getPowerRequirement());
        assertTrue(altitude.getAndroidLocationProviderCapability("passive") == null);

        TraceEnvironmentConfig speed = TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_SPEED_JSON);
        assertTrue(speed.isAndroidLocationProvidersConfigured());
        assertTrue(speed.getAndroidLocationProvidersConfig().isGps());
        assertFalse(speed.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(speed.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isRequiresNetworkConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isRequiresNetwork());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isRequiresSatelliteConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isRequiresSatellite());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isRequiresCellConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isRequiresCell());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isHasMonetaryCostConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isHasMonetaryCost());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isSupportsAltitudeConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isSupportsAltitude());
        assertTrue(speed.getAndroidLocationProviderCapability("gps").isSupportsSpeedConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isSupportsSpeed());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isSupportsBearingConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isSupportsBearing());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isMeetsCriteriaConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isMeetsCriteria());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isAccuracyConfigured());
        assertEquals(0, speed.getAndroidLocationProviderCapability("gps").getAccuracy());
        assertFalse(speed.getAndroidLocationProviderCapability("gps").isPowerRequirementConfigured());
        assertEquals(0, speed.getAndroidLocationProviderCapability("gps").getPowerRequirement());
        assertFalse(speed.getAndroidLocationProviderCapability("network").isRequiresNetworkConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("network").isRequiresSatelliteConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("network").isRequiresCellConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("network").isHasMonetaryCostConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("network").isSupportsAltitudeConfigured());
        assertTrue(speed.getAndroidLocationProviderCapability("network").isSupportsSpeedConfigured());
        assertTrue(speed.getAndroidLocationProviderCapability("network").isSupportsSpeed());
        assertFalse(speed.getAndroidLocationProviderCapability("network").isSupportsBearingConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("network").isMeetsCriteriaConfigured());
        assertFalse(speed.getAndroidLocationProviderCapability("network").isAccuracyConfigured());
        assertEquals(0, speed.getAndroidLocationProviderCapability("network").getAccuracy());
        assertFalse(speed.getAndroidLocationProviderCapability("network").isPowerRequirementConfigured());
        assertEquals(0, speed.getAndroidLocationProviderCapability("network").getPowerRequirement());
        assertTrue(speed.getAndroidLocationProviderCapability("passive") == null);

        TraceEnvironmentConfig bearing = TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_BEARING_JSON);
        assertTrue(bearing.isAndroidLocationProvidersConfigured());
        assertTrue(bearing.getAndroidLocationProvidersConfig().isGps());
        assertFalse(bearing.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(bearing.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isRequiresNetworkConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isRequiresNetwork());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isRequiresSatelliteConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isRequiresSatellite());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isRequiresCellConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isRequiresCell());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isHasMonetaryCostConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isHasMonetaryCost());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isSupportsAltitudeConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isSupportsAltitude());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isSupportsSpeedConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isSupportsSpeed());
        assertTrue(bearing.getAndroidLocationProviderCapability("gps").isSupportsBearingConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isSupportsBearing());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isMeetsCriteriaConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isMeetsCriteria());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isAccuracyConfigured());
        assertEquals(0, bearing.getAndroidLocationProviderCapability("gps").getAccuracy());
        assertFalse(bearing.getAndroidLocationProviderCapability("gps").isPowerRequirementConfigured());
        assertEquals(0, bearing.getAndroidLocationProviderCapability("gps").getPowerRequirement());
        assertFalse(bearing.getAndroidLocationProviderCapability("network").isRequiresNetworkConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("network").isRequiresSatelliteConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("network").isRequiresCellConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("network").isHasMonetaryCostConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("network").isSupportsAltitudeConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("network").isSupportsSpeedConfigured());
        assertTrue(bearing.getAndroidLocationProviderCapability("network").isSupportsBearingConfigured());
        assertTrue(bearing.getAndroidLocationProviderCapability("network").isSupportsBearing());
        assertFalse(bearing.getAndroidLocationProviderCapability("network").isMeetsCriteriaConfigured());
        assertFalse(bearing.getAndroidLocationProviderCapability("network").isAccuracyConfigured());
        assertEquals(0, bearing.getAndroidLocationProviderCapability("network").getAccuracy());
        assertFalse(bearing.getAndroidLocationProviderCapability("network").isPowerRequirementConfigured());
        assertEquals(0, bearing.getAndroidLocationProviderCapability("network").getPowerRequirement());
        assertTrue(bearing.getAndroidLocationProviderCapability("passive") == null);

        TraceEnvironmentConfig accuracy = TraceEnvironmentConfig.parse(PROVIDERS_ACCURACY_JSON);
        assertTrue(accuracy.isAndroidLocationProvidersConfigured());
        assertTrue(accuracy.getAndroidLocationProvidersConfig().isGps());
        assertFalse(accuracy.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(accuracy.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isRequiresNetworkConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isRequiresNetwork());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isRequiresSatelliteConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isRequiresSatellite());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isRequiresCellConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isRequiresCell());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isHasMonetaryCostConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isHasMonetaryCost());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isSupportsAltitudeConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isSupportsAltitude());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isSupportsSpeedConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isSupportsSpeed());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isSupportsBearingConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isSupportsBearing());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isMeetsCriteriaConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isMeetsCriteria());
        assertTrue(accuracy.getAndroidLocationProviderCapability("gps").isAccuracyConfigured());
        assertEquals(1, accuracy.getAndroidLocationProviderCapability("gps").getAccuracy());
        assertFalse(accuracy.getAndroidLocationProviderCapability("gps").isPowerRequirementConfigured());
        assertEquals(0, accuracy.getAndroidLocationProviderCapability("gps").getPowerRequirement());
        assertFalse(accuracy.getAndroidLocationProviderCapability("network").isRequiresNetworkConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("network").isRequiresSatelliteConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("network").isRequiresCellConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("network").isHasMonetaryCostConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("network").isSupportsAltitudeConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("network").isSupportsSpeedConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("network").isSupportsBearingConfigured());
        assertFalse(accuracy.getAndroidLocationProviderCapability("network").isMeetsCriteriaConfigured());
        assertTrue(accuracy.getAndroidLocationProviderCapability("network").isAccuracyConfigured());
        assertEquals(2, accuracy.getAndroidLocationProviderCapability("network").getAccuracy());
        assertFalse(accuracy.getAndroidLocationProviderCapability("network").isPowerRequirementConfigured());
        assertEquals(0, accuracy.getAndroidLocationProviderCapability("network").getPowerRequirement());
        assertTrue(accuracy.getAndroidLocationProviderCapability("passive") == null);

        TraceEnvironmentConfig power = TraceEnvironmentConfig.parse(PROVIDERS_POWER_REQUIREMENT_JSON);
        assertTrue(power.isAndroidLocationProvidersConfigured());
        assertTrue(power.getAndroidLocationProvidersConfig().isGps());
        assertFalse(power.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(power.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isRequiresNetworkConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isRequiresNetwork());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isRequiresSatelliteConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isRequiresSatellite());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isRequiresCellConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isRequiresCell());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isHasMonetaryCostConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isHasMonetaryCost());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isSupportsAltitudeConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isSupportsAltitude());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isSupportsSpeedConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isSupportsSpeed());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isSupportsBearingConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isSupportsBearing());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isMeetsCriteriaConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isMeetsCriteria());
        assertFalse(power.getAndroidLocationProviderCapability("gps").isAccuracyConfigured());
        assertEquals(0, power.getAndroidLocationProviderCapability("gps").getAccuracy());
        assertTrue(power.getAndroidLocationProviderCapability("gps").isPowerRequirementConfigured());
        assertEquals(1, power.getAndroidLocationProviderCapability("gps").getPowerRequirement());
        assertFalse(power.getAndroidLocationProviderCapability("network").isRequiresNetworkConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("network").isRequiresSatelliteConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("network").isRequiresCellConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("network").isHasMonetaryCostConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("network").isSupportsAltitudeConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("network").isSupportsSpeedConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("network").isSupportsBearingConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("network").isMeetsCriteriaConfigured());
        assertFalse(power.getAndroidLocationProviderCapability("network").isAccuracyConfigured());
        assertEquals(0, power.getAndroidLocationProviderCapability("network").getAccuracy());
        assertTrue(power.getAndroidLocationProviderCapability("network").isPowerRequirementConfigured());
        assertEquals(3, power.getAndroidLocationProviderCapability("network").getPowerRequirement());
        assertTrue(power.getAndroidLocationProviderCapability("passive") == null);

        TraceEnvironmentConfig mediumPower = TraceEnvironmentConfig.parse(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"powerRequirement\":2}}}}}");
        assertTrue(mediumPower.getAndroidLocationProviderCapability("gps").isPowerRequirementConfigured());
        assertEquals(2, mediumPower.getAndroidLocationProviderCapability("gps").getPowerRequirement());
        assertFalse(mediumPower.getAndroidLocationProviderCapability("gps").isAccuracyConfigured());
        assertEquals(0, mediumPower.getAndroidLocationProviderCapability("gps").getAccuracy());
        assertFalse(mediumPower.getAndroidLocationProviderCapability("gps").isMeetsCriteriaConfigured());
        assertFalse(mediumPower.getAndroidLocationProviderCapability("gps").isMeetsCriteria());

        TraceEnvironmentConfig criteria = TraceEnvironmentConfig.parse(PROVIDERS_MEETS_CRITERIA_JSON);
        assertTrue(criteria.isAndroidLocationProvidersConfigured());
        assertTrue(criteria.getAndroidLocationProvidersConfig().isGps());
        assertFalse(criteria.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(criteria.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isRequiresNetworkConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isRequiresNetwork());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isRequiresSatelliteConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isRequiresSatellite());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isRequiresCellConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isRequiresCell());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isHasMonetaryCostConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isHasMonetaryCost());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isSupportsAltitudeConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isSupportsAltitude());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isSupportsSpeedConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isSupportsSpeed());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isSupportsBearingConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isSupportsBearing());
        assertTrue(criteria.getAndroidLocationProviderCapability("gps").isMeetsCriteriaConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isMeetsCriteria());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isAccuracyConfigured());
        assertEquals(0, criteria.getAndroidLocationProviderCapability("gps").getAccuracy());
        assertFalse(criteria.getAndroidLocationProviderCapability("gps").isPowerRequirementConfigured());
        assertEquals(0, criteria.getAndroidLocationProviderCapability("gps").getPowerRequirement());
        assertFalse(criteria.getAndroidLocationProviderCapability("network").isRequiresNetworkConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("network").isRequiresSatelliteConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("network").isRequiresCellConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("network").isHasMonetaryCostConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("network").isSupportsAltitudeConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("network").isSupportsSpeedConfigured());
        assertFalse(criteria.getAndroidLocationProviderCapability("network").isSupportsBearingConfigured());
        assertTrue(criteria.getAndroidLocationProviderCapability("network").isMeetsCriteriaConfigured());
        assertTrue(criteria.getAndroidLocationProviderCapability("network").isMeetsCriteria());
        assertFalse(criteria.getAndroidLocationProviderCapability("network").isAccuracyConfigured());
        assertEquals(0, criteria.getAndroidLocationProviderCapability("network").getAccuracy());
        assertFalse(criteria.getAndroidLocationProviderCapability("network").isPowerRequirementConfigured());
        assertEquals(0, criteria.getAndroidLocationProviderCapability("network").getPowerRequirement());
        assertTrue(criteria.getAndroidLocationProviderCapability("passive") == null);

        TraceEnvironmentConfig capsOnly = TraceEnvironmentConfig.parse(
                CAPABILITIES_WITHOUT_PROVIDERS_JSON);
        assertTrue(capsOnly.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(capsOnly.isAndroidLocationProvidersConfigured());
        assertTrue(capsOnly.getAndroidLocationProvidersConfig() == null);

        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"fused\":{\"requiresNetwork\":true}}}}}",
                "android.location.providerCapabilities.fused");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":true}}}}",
                "android.location.providerCapabilities.gps");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"unknownCapability\":true}}}}}",
                "android.location.providerCapabilities.gps.unknownCapability is not an allowed key (requiresNetwork|requiresSatellite|requiresCell|hasMonetaryCost|supportsAltitude|supportsSpeed|supportsBearing|meetsCriteria|accuracy|powerRequirement)");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"requiresNetwork\":1}}}}}",
                "android.location.providerCapabilities.gps.requiresNetwork");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"requiresNetwork\":\"true\"}}}}}",
                "android.location.providerCapabilities.gps.requiresNetwork");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"requiresSatellite\":1}}}}}",
                "android.location.providerCapabilities.gps.requiresSatellite");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"requiresSatellite\":\"true\"}}}}}",
                "android.location.providerCapabilities.gps.requiresSatellite");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"requiresCell\":1}}}}}",
                "android.location.providerCapabilities.gps.requiresCell");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"requiresCell\":\"true\"}}}}}",
                "android.location.providerCapabilities.gps.requiresCell");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"hasMonetaryCost\":1}}}}}",
                "android.location.providerCapabilities.gps.hasMonetaryCost");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"hasMonetaryCost\":\"true\"}}}}}",
                "android.location.providerCapabilities.gps.hasMonetaryCost");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"supportsAltitude\":1}}}}}",
                "android.location.providerCapabilities.gps.supportsAltitude");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"supportsAltitude\":\"true\"}}}}}",
                "android.location.providerCapabilities.gps.supportsAltitude");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"supportsSpeed\":1}}}}}",
                "android.location.providerCapabilities.gps.supportsSpeed");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"supportsSpeed\":\"true\"}}}}}",
                "android.location.providerCapabilities.gps.supportsSpeed");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"supportsBearing\":1}}}}}",
                "android.location.providerCapabilities.gps.supportsBearing");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"supportsBearing\":\"true\"}}}}}",
                "android.location.providerCapabilities.gps.supportsBearing");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"meetsCriteria\":1}}}}}",
                "android.location.providerCapabilities.gps.meetsCriteria");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"meetsCriteria\":\"true\"}}}}}",
                "android.location.providerCapabilities.gps.meetsCriteria");
        assertLocationAccuracyJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"accuracy\":0}}}}}");
        assertLocationAccuracyJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"accuracy\":3}}}}}");
        assertLocationAccuracyJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"accuracy\":1.5}}}}}");
        assertLocationAccuracyJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"accuracy\":\"1\"}}}}}");
        assertLocationAccuracyJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"accuracy\":true}}}}}");
        assertLocationAccuracyJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"accuracy\":null}}}}}");
        assertLocationPowerRequirementJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"powerRequirement\":0}}}}}");
        assertLocationPowerRequirementJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"powerRequirement\":4}}}}}");
        assertLocationPowerRequirementJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"powerRequirement\":1.5}}}}}");
        assertLocationPowerRequirementJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"powerRequirement\":\"1\"}}}}}");
        assertLocationPowerRequirementJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"powerRequirement\":true}}}}}");
        assertLocationPowerRequirementJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"powerRequirement\":null}}}}}");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":{\"gps\":{\"getPowerRequirement\":1}}}}}",
                "android.location.providerCapabilities.gps.getPowerRequirement is not an allowed key (requiresNetwork|requiresSatellite|requiresCell|hasMonetaryCost|supportsAltitude|supportsSpeed|supportsBearing|meetsCriteria|accuracy|powerRequirement)");
        assertLocationJsonInvalid(
                "{\"android\":{\"location\":{\"providerCapabilities\":[]}}}",
                "android.location.providerCapabilities");
    }

    @Test
    public void testIsLocationEnabledTrueVarArg32() throws Exception {
        runIsLocationEnabled(false, false, LOCATION_ENABLED_TRUE_JSON, true);
    }

    @Test
    public void testIsLocationEnabledTrueVaList64() throws Exception {
        runIsLocationEnabled(true, true, LOCATION_ENABLED_TRUE_JSON, true);
    }

    @Test
    public void testIsLocationEnabledDefaultFalseVarArg32() throws Exception {
        // parent location without enabled key → default false; works without providers
        runIsLocationEnabled(false, false, LOCATION_WITHOUT_PROVIDERS_JSON, false);
    }

    @Test
    public void testIsLocationEnabledDefaultFalseVaList64() throws Exception {
        runIsLocationEnabled(true, true, LOCATION_WITHOUT_PROVIDERS_JSON, false);
    }

    @Test
    public void testIsLocationEnabledExplicitFalseVarArg32() throws Exception {
        runIsLocationEnabled(false, false, LOCATION_ENABLED_FALSE_JSON, false);
    }

    @Test
    public void testIsLocationEnabledExplicitFalseVaList64() throws Exception {
        runIsLocationEnabled(true, true, LOCATION_ENABLED_FALSE_JSON, false);
    }

    @Test
    public void testIsLocationEnabledAbsentPlainIsolationVarArg32() throws Exception {
        runIsLocationEnabledAbsentPlainIsolation(false, false);
    }

    @Test
    public void testIsLocationEnabledAbsentPlainIsolationVaList64() throws Exception {
        runIsLocationEnabledAbsentPlainIsolation(true, true);
    }

    @Test
    public void testGetLastKnownLocationFullVarArg32() throws Exception {
        runLastKnownLocationFull(false, false);
    }

    @Test
    public void testGetLastKnownLocationFullVaList64() throws Exception {
        runLastKnownLocationFull(true, true);
    }

    @Test
    public void testGetLastKnownLocationNullNoMatchVarArg32() throws Exception {
        runLastKnownLocationNullNoMatch(false, false);
    }

    @Test
    public void testGetLastKnownLocationNullNoMatchVaList64() throws Exception {
        runLastKnownLocationNullNoMatch(true, true);
    }

    @Test
    public void testGetLastKnownLocationAbsentVarArg32() throws Exception {
        runLastKnownLocationAbsent(false, false, NO_LOCATION_JSON);
    }

    @Test
    public void testGetLastKnownLocationAbsentVaList64() throws Exception {
        runLastKnownLocationAbsent(true, true, LOCATION_WITHOUT_PROVIDERS_JSON);
    }

    @Test
    public void testGetLastKnownLocationCrossVmLeakageVarArg32() throws Exception {
        runLastKnownLocationCrossVmLeakage(false, false);
    }

    @Test
    public void testGetLastKnownLocationCrossVmLeakageVaList64() throws Exception {
        runLastKnownLocationCrossVmLeakage(true, true);
    }

    @Test
    public void testLocationIsMockAliasVarArg32() throws Exception {
        runLocationIsMockAlias(false, false);
    }

    @Test
    public void testLocationIsMockAliasVaList64() throws Exception {
        runLocationIsMockAlias(true, true);
    }

    @Test
    public void testLocationSpeedBearingVarArg32() throws Exception {
        runLocationSpeedBearing(false, false);
    }

    @Test
    public void testLocationSpeedBearingVaList64() throws Exception {
        runLocationSpeedBearing(true, true);
    }

    @Test
    public void testLocationSpeedBearingIsolationVarArg32() throws Exception {
        runLocationSpeedBearingIsolation(false, false);
    }

    @Test
    public void testLocationSpeedBearingIsolationVaList64() throws Exception {
        runLocationSpeedBearingIsolation(true, true);
    }

    @Test
    public void testLocationAccuracyExtensionsVarArg32() throws Exception {
        runLocationAccuracyExtensions(false, false);
    }

    @Test
    public void testLocationAccuracyExtensionsVaList64() throws Exception {
        runLocationAccuracyExtensions(true, true);
    }

    @Test
    public void testLocationAccuracyExtensionsIsolationVarArg32() throws Exception {
        runLocationAccuracyExtensionsIsolation(false, false);
    }

    @Test
    public void testLocationAccuracyExtensionsIsolationVaList64() throws Exception {
        runLocationAccuracyExtensionsIsolation(true, true);
    }

    @Test
    public void testLocationIsMockIsolationVarArg32() throws Exception {
        runLocationIsMockIsolation(false, false);
    }

    @Test
    public void testLocationIsMockIsolationVaList64() throws Exception {
        runLocationIsMockIsolation(true, true);
    }

    private static void runLastKnownLocationFull(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LAST_KNOWN_FULL_JSON);
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gpsLoc = invokeGetLastKnownLocation(jni, baseVM, useVaList, manager, "gps");
            assertNotNull(gpsLoc);
            assertTrue(String.valueOf(gpsLoc.getValue().getClass().getName())
                    .contains("ConfiguredLocation"));

            CapturedEvent hit = findLastEvent(sink.events, "android_location",
                    "LocationManager.getLastKnownLocation");
            assertNotNull(hit);
            assertEquals("json-config", hit.source);
            assertEquals("provider=gps,result=location", String.valueOf(hit.value));
            assertFalse(String.valueOf(hit.value).contains("31.2304"));
            assertFalse(String.valueOf(hit.value).contains("121.4737"));

            assertEquals("gps", invokeGetProvider(jni, baseVM, useVaList, gpsLoc));
            assertEquals(31.2304d, invokeGetLatitude(jni, baseVM, gpsLoc), 0.0d);
            assertEquals(121.4737d, invokeGetLongitude(jni, baseVM, gpsLoc), 0.0d);
            assertTrue(invokeHasAltitude(jni, baseVM, useVaList, gpsLoc));
            assertEquals(12.5d, invokeGetAltitude(jni, baseVM, gpsLoc), 0.0d);
            assertTrue(invokeHasAccuracy(jni, baseVM, useVaList, gpsLoc));
            assertEquals(8.25f, invokeGetAccuracy(jni, baseVM, gpsLoc), 0.0f);
            assertEquals(1700000000000L, invokeGetTime(jni, baseVM, useVaList, gpsLoc));
            assertEquals(9000000000000L, invokeGetElapsedRealtimeNanos(jni, baseVM, useVaList, gpsLoc));
            assertFalse(invokeIsFromMockProvider(jni, baseVM, useVaList, gpsLoc));
            assertFalse(invokeIsMock(jni, baseVM, useVaList, gpsLoc));
            assertTrue(invokeHasSpeed(jni, baseVM, useVaList, gpsLoc));
            assertEquals(1.5f, invokeGetSpeed(jni, baseVM, gpsLoc), 0.0f);
            assertTrue(invokeHasBearing(jni, baseVM, useVaList, gpsLoc));
            assertEquals(90.0f, invokeGetBearing(jni, baseVM, gpsLoc), 0.0f);
            assertTrue(invokeHasVerticalAccuracy(jni, baseVM, useVaList, gpsLoc));
            assertEquals(2.5f, invokeGetVerticalAccuracyMeters(jni, baseVM, gpsLoc), 0.0f);
            assertTrue(invokeHasSpeedAccuracy(jni, baseVM, useVaList, gpsLoc));
            assertEquals(0.25f, invokeGetSpeedAccuracyMetersPerSecond(jni, baseVM, gpsLoc), 0.0f);
            assertTrue(invokeHasBearingAccuracy(jni, baseVM, useVaList, gpsLoc));
            assertEquals(5.0f, invokeGetBearingAccuracyDegrees(jni, baseVM, gpsLoc), 0.0f);

            // second call yields a fresh marker instance
            DvmObject<?> gpsLoc2 = invokeGetLastKnownLocation(jni, baseVM, useVaList, manager, "gps");
            assertNotNull(gpsLoc2);
            assertTrue(gpsLoc != gpsLoc2);

            DvmObject<?> networkLoc = invokeGetLastKnownLocation(jni, baseVM, useVaList, manager,
                    "network");
            assertNotNull(networkLoc);
            assertEquals("network", invokeGetProvider(jni, baseVM, useVaList, networkLoc));
            assertEquals(-33.8688d, invokeGetLatitude(jni, baseVM, networkLoc), 0.0d);
            assertEquals(151.2093d, invokeGetLongitude(jni, baseVM, networkLoc), 0.0d);
            assertFalse(invokeHasAltitude(jni, baseVM, useVaList, networkLoc));
            assertEquals(0.0d, invokeGetAltitude(jni, baseVM, networkLoc), 0.0d);
            assertFalse(invokeHasAccuracy(jni, baseVM, useVaList, networkLoc));
            assertEquals(0.0f, invokeGetAccuracy(jni, baseVM, networkLoc), 0.0f);
            assertEquals(0L, invokeGetTime(jni, baseVM, useVaList, networkLoc));
            assertEquals(0L, invokeGetElapsedRealtimeNanos(jni, baseVM, useVaList, networkLoc));
            assertTrue(invokeIsFromMockProvider(jni, baseVM, useVaList, networkLoc));
            assertTrue(invokeIsMock(jni, baseVM, useVaList, networkLoc));
            // omitted speed/bearing/accuracy extensions
            assertFalse(invokeHasSpeed(jni, baseVM, useVaList, networkLoc));
            assertEquals(0.0f, invokeGetSpeed(jni, baseVM, networkLoc), 0.0f);
            assertFalse(invokeHasBearing(jni, baseVM, useVaList, networkLoc));
            assertEquals(0.0f, invokeGetBearing(jni, baseVM, networkLoc), 0.0f);
            assertFalse(invokeHasVerticalAccuracy(jni, baseVM, useVaList, networkLoc));
            assertEquals(0.0f, invokeGetVerticalAccuracyMeters(jni, baseVM, networkLoc), 0.0f);
            assertFalse(invokeHasSpeedAccuracy(jni, baseVM, useVaList, networkLoc));
            assertEquals(0.0f, invokeGetSpeedAccuracyMetersPerSecond(jni, baseVM, networkLoc), 0.0f);
            assertFalse(invokeHasBearingAccuracy(jni, baseVM, useVaList, networkLoc));
            assertEquals(0.0f, invokeGetBearingAccuracyDegrees(jni, baseVM, networkLoc), 0.0f);

            // Location getters must not emit sidecar
            for (CapturedEvent e : sink.events) {
                if ("android_location".equals(e.kind)) {
                    assertTrue("unexpected Location getter sidecar: " + e.api,
                            e.api.startsWith("LocationManager."));
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runLastKnownLocationNullNoMatch(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LAST_KNOWN_EMPTY_JSON);
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> miss = invokeGetLastKnownLocation(jni, baseVM, useVaList, manager, "gps");
            assertTrue(miss == null);
            CapturedEvent ev = findLastEvent(sink.events, "android_location",
                    "LocationManager.getLastKnownLocation");
            assertNotNull(ev);
            assertEquals("provider=gps,result=null", String.valueOf(ev.value));

            // non-empty config, exact match only
            TraceEnvironmentConfig config2 = TraceEnvironmentConfig.parse(LAST_KNOWN_FULL_JSON);
            AndroidEmulator emulator2 = null;
            CapturingSink sink2 = new CapturingSink();
            try {
                emulator2 = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                        : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config2)
                        .build();
                TraceEnvironmentEventSink.register(emulator2, sink2);
                VM vm2 = emulator2.createDalvikVM();
                AbstractJni jni2 = new AbstractJni() {
                };
                vm2.setJni(jni2);
                BaseVM baseVM2 = (BaseVM) vm2;
                DvmObject<?> manager2 = resolveLocationSystemService(jni2, baseVM2, useVaList, vm2);
                DvmObject<?> noMatch = invokeGetLastKnownLocation(jni2, baseVM2, useVaList, manager2,
                        "passive");
                assertTrue(noMatch == null);
                CapturedEvent missEv = findLastEvent(sink2.events, "android_location",
                        "LocationManager.getLastKnownLocation");
                assertNotNull(missEv);
                assertEquals("provider=passive,result=null", String.valueOf(missEv.value));
            } finally {
                if (emulator2 != null) {
                    TraceEnvironmentEventSink.unregister(emulator2, sink2);
                    emulator2.close();
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runLastKnownLocationAbsent(boolean is64Bit, boolean useVaList, String json)
            throws Exception {
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeGetLastKnownLocation(jni, baseVM, useVaList, manager, "gps");
                fail("expected UOE for getLastKnownLocation without lastKnownLocations");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLastKnownLocation"));
            }

            // plain LocationManager (not SystemService)
            DvmObject<?> plain = vm.resolveClass(LOCATION_MANAGER_CLASS).newObject(null);
            try {
                invokeGetLastKnownLocation(jni, baseVM, useVaList, plain, "gps");
                fail("expected UOE for getLastKnownLocation on plain LocationManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLastKnownLocation"));
            }

            // plain Location getters UOE
            DvmObject<?> plainLoc = vm.resolveClass(LOCATION_CLASS).newObject(null);
            try {
                invokeGetLatitude(jni, baseVM, plainLoc);
                fail("expected UOE for getLatitude on plain Location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLatitude"));
            }
            try {
                invokeGetProvider(jni, baseVM, useVaList, plainLoc);
                fail("expected UOE for getProvider on plain Location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_location event: " + e.api,
                        "android_location".equals(e.kind)
                                && "LocationManager.getLastKnownLocation".equals(e.api));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runLocationAccuracyExtensions(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LAST_KNOWN_FULL_JSON);
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gpsLoc = invokeGetLastKnownLocation(jni, baseVM, useVaList, manager, "gps");
            DvmObject<?> networkLoc = invokeGetLastKnownLocation(jni, baseVM, useVaList, manager,
                    "network");
            assertNotNull(gpsLoc);
            assertNotNull(networkLoc);

            assertTrue(invokeHasVerticalAccuracy(jni, baseVM, useVaList, gpsLoc));
            assertEquals(2.5f, invokeGetVerticalAccuracyMeters(jni, baseVM, gpsLoc), 0.0f);
            assertTrue(invokeHasSpeedAccuracy(jni, baseVM, useVaList, gpsLoc));
            assertEquals(0.25f, invokeGetSpeedAccuracyMetersPerSecond(jni, baseVM, gpsLoc), 0.0f);
            assertTrue(invokeHasBearingAccuracy(jni, baseVM, useVaList, gpsLoc));
            assertEquals(5.0f, invokeGetBearingAccuracyDegrees(jni, baseVM, gpsLoc), 0.0f);

            assertFalse(invokeHasVerticalAccuracy(jni, baseVM, useVaList, networkLoc));
            assertEquals(0.0f, invokeGetVerticalAccuracyMeters(jni, baseVM, networkLoc), 0.0f);
            assertFalse(invokeHasSpeedAccuracy(jni, baseVM, useVaList, networkLoc));
            assertEquals(0.0f, invokeGetSpeedAccuracyMetersPerSecond(jni, baseVM, networkLoc), 0.0f);
            assertFalse(invokeHasBearingAccuracy(jni, baseVM, useVaList, networkLoc));
            assertEquals(0.0f, invokeGetBearingAccuracyDegrees(jni, baseVM, networkLoc), 0.0f);

            for (CapturedEvent e : sink.events) {
                if ("android_location".equals(e.kind)) {
                    assertEquals("LocationManager.getLastKnownLocation", e.api);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runLocationAccuracyExtensionsIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LAST_KNOWN_FULL_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> plain = vmA.resolveClass(LOCATION_CLASS).newObject(null);
            try {
                invokeHasVerticalAccuracy(jniA, baseA, useVaList, plain);
                fail("expected UOE for hasVerticalAccuracy on plain Location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasVerticalAccuracy"));
            }
            try {
                invokeGetVerticalAccuracyMeters(jniA, baseA, plain);
                fail("expected UOE for getVerticalAccuracyMeters on plain Location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getVerticalAccuracyMeters"));
            }
            try {
                invokeHasSpeedAccuracy(jniA, baseA, useVaList, plain);
                fail("expected UOE for hasSpeedAccuracy on plain Location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasSpeedAccuracy"));
            }
            try {
                invokeGetSpeedAccuracyMetersPerSecond(jniA, baseA, plain);
                fail("expected UOE for getSpeedAccuracyMetersPerSecond on plain Location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSpeedAccuracyMetersPerSecond"));
            }
            try {
                invokeHasBearingAccuracy(jniA, baseA, useVaList, plain);
                fail("expected UOE for hasBearingAccuracy on plain Location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasBearingAccuracy"));
            }
            try {
                invokeGetBearingAccuracyDegrees(jniA, baseA, plain);
                fail("expected UOE for getBearingAccuracyDegrees on plain Location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getBearingAccuracyDegrees"));
            }

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> locA = invokeGetLastKnownLocation(jniA, baseA, useVaList, managerA, "gps");
            assertNotNull(locA);
            assertTrue(invokeHasVerticalAccuracy(jniA, baseA, useVaList, locA));
            assertEquals(2.5f, invokeGetVerticalAccuracyMeters(jniA, baseA, locA), 0.0f);

            try {
                invokeHasVerticalAccuracy(jniB, baseB, useVaList, locA);
                fail("expected UOE for cross-VM hasVerticalAccuracy");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasVerticalAccuracy"));
            }
            try {
                invokeGetBearingAccuracyDegrees(jniB, baseB, locA);
                fail("expected UOE for cross-VM getBearingAccuracyDegrees");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getBearingAccuracyDegrees"));
            }

            for (CapturedEvent e : sinkB.events) {
                assertFalse("unexpected android_location event on VM B: " + e.api,
                        "android_location".equals(e.kind));
            }
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runLocationSpeedBearing(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LAST_KNOWN_FULL_JSON);
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gpsLoc = invokeGetLastKnownLocation(jni, baseVM, useVaList, manager, "gps");
            DvmObject<?> networkLoc = invokeGetLastKnownLocation(jni, baseVM, useVaList, manager,
                    "network");
            assertNotNull(gpsLoc);
            assertNotNull(networkLoc);

            assertTrue(invokeHasSpeed(jni, baseVM, useVaList, gpsLoc));
            assertEquals(1.5f, invokeGetSpeed(jni, baseVM, gpsLoc), 0.0f);
            assertTrue(invokeHasBearing(jni, baseVM, useVaList, gpsLoc));
            assertEquals(90.0f, invokeGetBearing(jni, baseVM, gpsLoc), 0.0f);

            assertFalse(invokeHasSpeed(jni, baseVM, useVaList, networkLoc));
            assertEquals(0.0f, invokeGetSpeed(jni, baseVM, networkLoc), 0.0f);
            assertFalse(invokeHasBearing(jni, baseVM, useVaList, networkLoc));
            assertEquals(0.0f, invokeGetBearing(jni, baseVM, networkLoc), 0.0f);

            for (CapturedEvent e : sink.events) {
                if ("android_location".equals(e.kind)) {
                    assertEquals("LocationManager.getLastKnownLocation", e.api);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runLocationSpeedBearingIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LAST_KNOWN_FULL_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> plain = vmA.resolveClass(LOCATION_CLASS).newObject(null);
            try {
                invokeHasSpeed(jniA, baseA, useVaList, plain);
                fail("expected UOE for hasSpeed on plain Location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasSpeed"));
            }
            try {
                invokeGetSpeed(jniA, baseA, plain);
                fail("expected UOE for getSpeed on plain Location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getSpeed"));
            }
            try {
                invokeHasBearing(jniA, baseA, useVaList, plain);
                fail("expected UOE for hasBearing on plain Location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasBearing"));
            }
            try {
                invokeGetBearing(jniA, baseA, plain);
                fail("expected UOE for getBearing on plain Location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getBearing"));
            }

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> locA = invokeGetLastKnownLocation(jniA, baseA, useVaList, managerA, "gps");
            assertNotNull(locA);
            assertTrue(invokeHasSpeed(jniA, baseA, useVaList, locA));
            assertEquals(1.5f, invokeGetSpeed(jniA, baseA, locA), 0.0f);

            try {
                invokeHasSpeed(jniB, baseB, useVaList, locA);
                fail("expected UOE for cross-VM hasSpeed");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasSpeed"));
            }
            try {
                invokeGetBearing(jniB, baseB, locA);
                fail("expected UOE for cross-VM getBearing");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getBearing"));
            }

            for (CapturedEvent e : sinkB.events) {
                assertFalse("unexpected android_location event on VM B: " + e.api,
                        "android_location".equals(e.kind));
            }
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    /**
     * {@code Location.isMock()Z} is a read-only alias of configured mock /
     * {@code isFromMockProvider()Z} on live same-VM markers (true and false).
     */
    private static void runLocationIsMockAlias(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LAST_KNOWN_FULL_JSON);
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gpsLoc = invokeGetLastKnownLocation(jni, baseVM, useVaList, manager, "gps");
            DvmObject<?> networkLoc = invokeGetLastKnownLocation(jni, baseVM, useVaList, manager,
                    "network");
            assertNotNull(gpsLoc);
            assertNotNull(networkLoc);

            // mock=false
            assertFalse(invokeIsFromMockProvider(jni, baseVM, useVaList, gpsLoc));
            assertFalse(invokeIsMock(jni, baseVM, useVaList, gpsLoc));
            // mock=true — alias matches
            assertTrue(invokeIsFromMockProvider(jni, baseVM, useVaList, networkLoc));
            assertTrue(invokeIsMock(jni, baseVM, useVaList, networkLoc));

            // no sidecar from isMock / isFromMockProvider
            for (CapturedEvent e : sink.events) {
                if ("android_location".equals(e.kind)) {
                    assertEquals("LocationManager.getLastKnownLocation", e.api);
                }
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    /**
     * Absent config / plain Location / cross-VM marker: isMock remains UOE with no event.
     */
    private static void runLocationIsMockIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        // absent lastKnownLocations
        TraceEnvironmentConfig absentConfig = TraceEnvironmentConfig.parse(NO_LOCATION_JSON);
        AndroidEmulator emulatorAbsent = null;
        CapturingSink sinkAbsent = new CapturingSink();
        try {
            emulatorAbsent = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                    : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(absentConfig)
                    .build();
            TraceEnvironmentEventSink.register(emulatorAbsent, sinkAbsent);
            VM vm = emulatorAbsent.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;

            DvmObject<?> plainLoc = vm.resolveClass(LOCATION_CLASS).newObject(null);
            try {
                invokeIsMock(jni, baseVM, useVaList, plainLoc);
                fail("expected UOE for isMock on plain Location without config");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isMock"));
            }
            for (CapturedEvent e : sinkAbsent.events) {
                assertFalse("unexpected android_location event: " + e.api,
                        "android_location".equals(e.kind));
            }
        } finally {
            if (emulatorAbsent != null) {
                TraceEnvironmentEventSink.unregister(emulatorAbsent, sinkAbsent);
                emulatorAbsent.close();
            }
        }

        // configured + plain Location (not marker) + cross-VM
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LAST_KNOWN_FULL_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> plain = vmA.resolveClass(LOCATION_CLASS).newObject(null);
            try {
                invokeIsMock(jniA, baseA, useVaList, plain);
                fail("expected UOE for isMock on plain Location with config present");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isMock"));
            }

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> locA = invokeGetLastKnownLocation(jniA, baseA, useVaList, managerA, "gps");
            assertNotNull(locA);
            // live same-VM still works
            assertFalse(invokeIsMock(jniA, baseA, useVaList, locA));

            try {
                invokeIsMock(jniB, baseB, useVaList, locA);
                fail("expected UOE for cross-VM isMock");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isMock"));
            }

            for (CapturedEvent e : sinkB.events) {
                assertFalse("unexpected android_location event on VM B: " + e.api,
                        "android_location".equals(e.kind));
            }
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runLastKnownLocationCrossVmLeakage(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LAST_KNOWN_FULL_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> locA = invokeGetLastKnownLocation(jniA, baseA, useVaList, managerA, "gps");
            assertNotNull(locA);

            // cross-VM: Location marker from A used on B → UOE, no event
            try {
                invokeGetLatitude(jniB, baseB, locA);
                fail("expected UOE for cross-VM getLatitude");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLatitude"));
            }
            try {
                invokeGetProvider(jniB, baseB, useVaList, locA);
                fail("expected UOE for cross-VM getProvider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }
            try {
                invokeHasAltitude(jniB, baseB, useVaList, locA);
                fail("expected UOE for cross-VM hasAltitude");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasAltitude"));
            }
            try {
                invokeGetTime(jniB, baseB, useVaList, locA);
                fail("expected UOE for cross-VM getTime");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getTime"));
            }
            try {
                invokeGetAccuracy(jniB, baseB, locA);
                fail("expected UOE for cross-VM getAccuracy");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAccuracy"));
            }

            for (CapturedEvent e : sinkB.events) {
                assertFalse("unexpected android_location event on VM B: " + e.api,
                        "android_location".equals(e.kind));
            }

            // same-VM control still works
            assertEquals(31.2304d, invokeGetLatitude(jniA, baseA, locA), 0.0d);
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runConfiguredProviders(boolean is64Bit, boolean useVaList, String json,
                                               boolean expectedGps, boolean expectedNetwork,
                                               boolean expectedPassive) throws Exception {
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            assertEquals(expectedGps, invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps"));
            assertEquals(expectedNetwork,
                    invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "network"));
            assertEquals(expectedPassive,
                    invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "passive"));

            assertProviderEvent(sink, "gps", expectedGps);
            assertProviderEvent(sink, "network", expectedNetwork);
            assertProviderEvent(sink, "passive", expectedPassive);
            assertEquals(1, countEventsForProvider(sink.events, "gps"));
            assertEquals(1, countEventsForProvider(sink.events, "network"));
            assertEquals(1, countEventsForProvider(sink.events, "passive"));
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationManager.isProviderEnabled"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAbsentProviders(boolean is64Bit, boolean useVaList, String json)
            throws Exception {
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

            // getSystemService still yields location marker without providers config
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            assertTrue(manager instanceof SystemService);
            for (String provider : new String[] {"gps", "network", "passive"}) {
                try {
                    invokeIsProviderEnabled(jni, baseVM, useVaList, manager, provider);
                    fail("expected UOE without location.providers for " + provider);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("isProviderEnabled"));
                }
            }
            try {
                invokeGetAllProviders(jni, baseVM, useVaList, manager);
                fail("expected UOE for getAllProviders without location.providers");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAllProviders"));
            }
            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_location event when config absent: " + e.api,
                        "android_location".equals(e.kind));
            }
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetAllProviders(boolean is64Bit, boolean useVaList, String json,
                                           String[] expectedNames, String expectedEventValue)
            throws Exception {
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> listObj = invokeGetAllProviders(jni, baseVM, useVaList, manager);
            assertNotNull(listObj);
            assertTrue(listObj instanceof ArrayListObject);
            ArrayListObject list = (ArrayListObject) listObj;
            assertEquals(expectedNames.length, list.size());
            for (int i = 0; i < expectedNames.length; i++) {
                DvmObject<?> item = list.getValue().get(i);
                assertTrue(item instanceof StringObject);
                assertEquals(expectedNames[i], ((StringObject) item).getValue());
            }

            CapturedEvent ev = findLastEvent(sink.events, "android_location",
                    "LocationManager.getAllProviders");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals(expectedEventValue, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_location",
                    "LocationManager.getAllProviders"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetAllProvidersAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        // absent config
        {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_PROVIDERS_JSON);
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
                DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
                try {
                    invokeGetAllProviders(jni, baseVM, useVaList, manager);
                    fail("expected UOE for getAllProviders without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getAllProviders"));
                }
                for (CapturedEvent e : sink.events) {
                    assertFalse("unexpected android_location event: " + e.api,
                            "android_location".equals(e.kind));
                }
            } finally {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                    emulator.close();
                }
            }
        }

        // plain / other SystemService isolation with config present
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_FULL_JSON);
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

            DvmObject<?> plain = vm.resolveClass(LOCATION_MANAGER_CLASS).newObject(null);
            try {
                invokeGetAllProviders(jni, baseVM, useVaList, plain);
                fail("expected UOE for getAllProviders on plain LocationManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAllProviders"));
            }

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> wifi = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            try {
                invokeGetAllProviders(jni, baseVM, useVaList, wifi);
                fail("expected UOE for getAllProviders on non-location SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAllProviders"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_location event on isolation: " + e.api,
                        "android_location".equals(e.kind));
            }

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> listObj = invokeGetAllProviders(jni, baseVM, useVaList, manager);
            assertTrue(listObj instanceof ArrayListObject);
            assertEquals(3, ((ArrayListObject) listObj).size());
            assertEquals(1, countEvents(sink.events, "android_location",
                    "LocationManager.getAllProviders"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runPlainInvalidIsolation(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_FULL_JSON);
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

            // plain LocationManager (not SystemService marker) → UOE, no event
            DvmObject<?> plain = vm.resolveClass(LOCATION_MANAGER_CLASS).newObject(null);
            try {
                invokeIsProviderEnabled(jni, baseVM, useVaList, plain, "gps");
                fail("expected UOE for isProviderEnabled on plain LocationManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isProviderEnabled"));
            }

            // other SystemService (wifi) → UOE, no event
            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> wifi = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            assertTrue(wifi instanceof SystemService);
            try {
                invokeIsProviderEnabled(jni, baseVM, useVaList, wifi, "gps");
                fail("expected UOE for isProviderEnabled on non-location SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isProviderEnabled"));
            }

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);

            // null provider arg
            try {
                invokeIsProviderEnabledNull(jni, baseVM, useVaList, manager);
                fail("expected UOE for null provider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isProviderEnabled"));
            }

            // non-String provider arg
            try {
                invokeIsProviderEnabledNonString(jni, baseVM, useVaList, manager);
                fail("expected UOE for non-String provider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isProviderEnabled"));
            }

            // unknown provider name
            try {
                invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "fused");
                fail("expected UOE for unknown provider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isProviderEnabled"));
            }

            // wrong signature
            try {
                DvmClass dvmClass = manager.getObjectType();
                DvmMethod method = new DvmMethod(dvmClass, "getLastKnownLocation",
                        "(Ljava/lang/String;)Landroid/location/Location;", false);
                String signature = method.getSignature();
                int nameHash = baseVM.addLocalObject(new StringObject(baseVM, "gps"));
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, manager, signature,
                            new TestObjectVaList(baseVM, method, nameHash));
                } else {
                    jni.callObjectMethod(baseVM, manager, signature,
                            new TestObjectVarArg(baseVM, method, nameHash));
                }
                fail("expected UOE for getLastKnownLocation");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getLastKnownLocation"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_location event on isolation paths: " + e.api,
                        "android_location".equals(e.kind));
            }

            // control: real location SystemService still works after isolation checks
            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps"));
            assertEquals(1, countEvents(sink.events, "android_location",
                    "LocationManager.isProviderEnabled"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runLocationServiceMarker(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_FULL_JSON);
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
                    "android/content/Context->LOCATION_SERVICE:Ljava/lang/String;");
            assertTrue(serviceName instanceof StringObject);
            assertEquals(SystemService.LOCATION_SERVICE, ((StringObject) serviceName).getValue());
            assertEquals("location", ((StringObject) serviceName).getValue());

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> manager = invokeGetSystemService(jni, baseVM, useVaList, app,
                    ((StringObject) serviceName).getValue());
            assertNotNull(manager);
            assertTrue(manager instanceof SystemService);
            assertEquals(LOCATION_MANAGER_CLASS, manager.getObjectType().getClassName());
            assertEquals(SystemService.LOCATION_SERVICE, manager.getValue());

            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps"));
            assertFalse(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "network"));
            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "passive"));

            assertProviderEvent(sink, "gps", true);
            assertProviderEvent(sink, "network", false);
            assertProviderEvent(sink, "passive", true);
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationManager.isProviderEnabled"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void assertProviderEvent(CapturingSink sink, String provider, boolean expected) {
        CapturedEvent found = null;
        for (CapturedEvent e : sink.events) {
            if ("android_location".equals(e.kind)
                    && "LocationManager.isProviderEnabled".equals(e.api)
                    && String.valueOf(e.value).startsWith("provider=" + provider + ",")) {
                found = e;
            }
        }
        assertNotNull("missing event for provider=" + provider, found);
        assertEquals("json-config", found.source);
        assertEquals("provider=" + provider + ",result=" + expected, String.valueOf(found.value));
        assertNotNull(found.note);
        assertFalse(found.note.isEmpty());
    }

    private static DvmObject<?> resolveLocationSystemService(AbstractJni jni, BaseVM vm,
                                                             boolean useVaList, VM dalvikVm) {
        DvmObject<?> app = dalvikVm.resolveClass("android/app/Application").newObject(null);
        return invokeGetSystemService(jni, vm, useVaList, app, "location");
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

    /**
     * Typed {@code Application}/{@code Context.getSystemService(LocationManager.class)} returns
     * the same {@code SystemService("location")} marker as the string route. Lookup does not
     * require {@code android.location}; location field APIs stay node-gated (UOE).
     */
    private static void runTypedGetSystemService(boolean is64Bit, boolean useVaList) throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_LOCATION_JSON);
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
            DvmClass lmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> fromString = invokeGetSystemService(jni, baseVM, useVaList, app, "location");
            DvmObject<?> fromApp = invokeGetSystemServiceClass(jni, baseVM, useVaList, app, lmClass);
            assertLocationManagerMarker(fromApp);
            assertEquals(fromString.getObjectType().getClassName(), fromApp.getObjectType().getClassName());
            assertEquals(fromString.getValue(), fromApp.getValue());

            DvmObject<?> context = vm.resolveClass("android/content/Context").newObject(null);
            DvmObject<?> fromCtx = invokeGetSystemServiceClass(jni, baseVM, useVaList, context, lmClass);
            assertLocationManagerMarker(fromCtx);
            assertEquals(fromString.getValue(), fromCtx.getValue());

            try {
                invokeIsLocationEnabled(jni, baseVM, useVaList, fromApp);
                fail("expected UOE for isLocationEnabled without android.location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isLocationEnabled"));
            }
            try {
                invokeIsLocationEnabled(jni, baseVM, useVaList, fromCtx);
                fail("expected UOE for isLocationEnabled without android.location");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isLocationEnabled"));
            }
        } finally {
            if (emulator != null) {
                emulator.close();
            }
        }
    }

    private static void assertLocationManagerMarker(DvmObject<?> manager) {
        assertNotNull(manager);
        assertTrue(manager instanceof SystemService);
        assertEquals(LOCATION_MANAGER_CLASS, manager.getObjectType().getClassName());
        assertEquals(SystemService.LOCATION_SERVICE, manager.getValue());
    }

    private static DvmObject<?> invokeGetSystemServiceClass(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                            DvmObject<?> app, DvmClass serviceClass) {
        int classHash = vm.addLocalObject(serviceClass);
        DvmClass dvmClass = app.getObjectType();
        DvmMethod method = new DvmMethod(dvmClass, "getSystemService",
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, app, signature, new TestObjectVaList(vm, method, classHash));
        }
        return jni.callObjectMethod(vm, app, signature, new TestObjectVarArg(vm, method, classHash));
    }

    private static void runIsLocationEnabled(boolean is64Bit, boolean useVaList, String json,
                                             boolean expected) throws Exception {
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            assertEquals(expected, invokeIsLocationEnabled(jni, baseVM, useVaList, manager));

            CapturedEvent ev = findLastEvent(sink.events, "android_location",
                    "LocationManager.isLocationEnabled");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals("field=enabled,result=" + expected, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_location",
                    "LocationManager.isLocationEnabled"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runIsLocationEnabledAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_LOCATION_JSON);
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
                DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
                try {
                    invokeIsLocationEnabled(jni, baseVM, useVaList, manager);
                    fail("expected UOE for isLocationEnabled without android.location");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("isLocationEnabled"));
                }
                for (CapturedEvent e : sink.events) {
                    assertFalse("unexpected android_location event: " + e.api,
                            "android_location".equals(e.kind));
                }
            } finally {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                    emulator.close();
                }
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(LOCATION_ENABLED_TRUE_JSON);
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

            DvmObject<?> plain = vm.resolveClass(LOCATION_MANAGER_CLASS).newObject(null);
            try {
                invokeIsLocationEnabled(jni, baseVM, useVaList, plain);
                fail("expected UOE for isLocationEnabled on plain LocationManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isLocationEnabled"));
            }

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> wifi = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            try {
                invokeIsLocationEnabled(jni, baseVM, useVaList, wifi);
                fail("expected UOE for isLocationEnabled on non-location SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isLocationEnabled"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_location event on isolation: " + e.api,
                        "android_location".equals(e.kind));
            }

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            assertTrue(invokeIsLocationEnabled(jni, baseVM, useVaList, manager));
            assertEquals(1, countEvents(sink.events, "android_location",
                    "LocationManager.isLocationEnabled"));

            // providers still unconfigured when only enabled is set
            try {
                invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps");
                fail("expected UOE for isProviderEnabled without providers node");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("isProviderEnabled"));
            }
            assertEquals(1, countEvents(sink.events, "android_location",
                    "LocationManager.isLocationEnabled"));
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationManager.isProviderEnabled"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static boolean invokeIsLocationEnabled(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isLocationEnabled", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsProviderEnabled(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmObject<?> target, String provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isProviderEnabled",
                "(Ljava/lang/String;)Z", false);
        String signature = method.getSignature();
        int nameHash = vm.addLocalObject(new StringObject(vm, provider));
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature,
                    new TestObjectVaList(vm, method, nameHash));
        }
        return jni.callBooleanMethod(vm, target, signature,
                new TestObjectVarArg(vm, method, nameHash));
    }

    private static boolean invokeHasProvider(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             DvmObject<?> target, String provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasProvider",
                "(Ljava/lang/String;)Z", false);
        String signature = method.getSignature();
        int nameHash = vm.addLocalObject(new StringObject(vm, provider));
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature,
                    new TestObjectVaList(vm, method, nameHash));
        }
        return jni.callBooleanMethod(vm, target, signature,
                new TestObjectVarArg(vm, method, nameHash));
    }

    private static DvmObject<?> invokeGetAllProviders(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                      DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getAllProviders", "()Ljava/util/List;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callObjectMethod(vm, target, signature, new TestNoArgVarArg(vm, method));
    }

    private static void runGetProviders(boolean is64Bit, boolean useVaList, String json,
                                        boolean enabledOnly, String[] expectedNames,
                                        String expectedEventValue) throws Exception {
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> listObj = invokeGetProviders(jni, baseVM, useVaList, manager, enabledOnly);
            assertNotNull(listObj);
            assertTrue(listObj instanceof ArrayListObject);
            ArrayListObject list = (ArrayListObject) listObj;
            assertEquals(expectedNames.length, list.size());
            for (int i = 0; i < expectedNames.length; i++) {
                DvmObject<?> item = list.getValue().get(i);
                assertTrue(item instanceof StringObject);
                assertEquals(expectedNames[i], ((StringObject) item).getValue());
            }

            CapturedEvent ev = findLastEvent(sink.events, "android_location",
                    "LocationManager.getProviders");
            assertNotNull(ev);
            assertEquals("json-config", ev.source);
            assertEquals(expectedEventValue, String.valueOf(ev.value));
            assertNotNull(ev.note);
            assertFalse(ev.note.isEmpty());
            assertEquals(1, countEvents(sink.events, "android_location",
                    "LocationManager.getProviders"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetProvidersAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(NO_PROVIDERS_JSON);
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
                DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
                try {
                    invokeGetProviders(jni, baseVM, useVaList, manager, false);
                    fail("expected UOE for getProviders without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getProviders"));
                }
                try {
                    invokeGetProviders(jni, baseVM, useVaList, manager, true);
                    fail("expected UOE for getProviders(true) without config");
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getProviders"));
                }
                for (CapturedEvent e : sink.events) {
                    assertFalse("unexpected android_location event: " + e.api,
                            "android_location".equals(e.kind));
                }
            } finally {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                    emulator.close();
                }
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_FULL_JSON);
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

            DvmObject<?> plain = vm.resolveClass(LOCATION_MANAGER_CLASS).newObject(null);
            try {
                invokeGetProviders(jni, baseVM, useVaList, plain, false);
                fail("expected UOE for getProviders on plain LocationManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProviders"));
            }

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> wifi = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            try {
                invokeGetProviders(jni, baseVM, useVaList, wifi, true);
                fail("expected UOE for getProviders on non-location SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProviders"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_location event on isolation: " + e.api,
                        "android_location".equals(e.kind));
            }

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> all = invokeGetProviders(jni, baseVM, useVaList, manager, false);
            assertTrue(all instanceof ArrayListObject);
            assertEquals(3, ((ArrayListObject) all).size());
            DvmObject<?> enabled = invokeGetProviders(jni, baseVM, useVaList, manager, true);
            assertTrue(enabled instanceof ArrayListObject);
            assertEquals(2, ((ArrayListObject) enabled).size());
            assertEquals(2, countEvents(sink.events, "android_location",
                    "LocationManager.getProviders"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetProviderExplicitIncludingDisabled(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_FULL_JSON);
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gps = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            DvmObject<?> network = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "network");
            DvmObject<?> passive = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "passive");
            assertConfiguredLocationProvider(gps);
            assertConfiguredLocationProvider(network);
            assertConfiguredLocationProvider(passive);

            assertEquals("gps", invokeLocationProviderGetName(jni, baseVM, useVaList, gps));
            assertEquals("network", invokeLocationProviderGetName(jni, baseVM, useVaList, network));
            assertEquals("passive", invokeLocationProviderGetName(jni, baseVM, useVaList, passive));

            DvmObject<?> gps2 = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            assertConfiguredLocationProvider(gps2);
            assertTrue(gps != gps2);

            assertGetProviderEvent(sink, "gps", "provider");
            assertGetProviderEvent(sink, "network", "provider");
            assertGetProviderEvent(sink, "passive", "provider");
            assertGetNameEvent(sink, "gps");
            assertGetNameEvent(sink, "network");
            assertGetNameEvent(sink, "passive");
            assertEquals(4, countEvents(sink.events, "android_location",
                    "LocationManager.getProvider"));
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationProvider.getName"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetProviderOmittedUnknownNull(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_PARTIAL_ORDER_JSON);
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gps = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            DvmObject<?> network = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "network");
            DvmObject<?> passive = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "passive");
            DvmObject<?> fused = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "fused");
            assertConfiguredLocationProvider(gps);
            assertTrue(network == null);
            assertConfiguredLocationProvider(passive);
            assertTrue(fused == null);
            assertEquals("gps", invokeLocationProviderGetName(jni, baseVM, useVaList, gps));
            assertEquals("passive", invokeLocationProviderGetName(jni, baseVM, useVaList, passive));

            assertGetProviderEvent(sink, "gps", "provider");
            assertGetProviderEvent(sink, "network", "null");
            assertGetProviderEvent(sink, "passive", "provider");
            assertGetProviderEvent(sink, "fused", "null");
            assertEquals(4, countEvents(sink.events, "android_location",
                    "LocationManager.getProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(PROVIDERS_EMPTY_JSON);
        AndroidEmulator emptyEmu = null;
        CapturingSink emptySink = new CapturingSink();
        try {
            emptyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(empty)
                    .build();
            TraceEnvironmentEventSink.register(emptyEmu, emptySink);
            VM emptyVm = emptyEmu.createDalvikVM();
            AbstractJni emptyJni = new AbstractJni() {
            };
            emptyVm.setJni(emptyJni);
            BaseVM emptyBase = (BaseVM) emptyVm;
            DvmObject<?> manager = resolveLocationSystemService(emptyJni, emptyBase, useVaList,
                    emptyVm);
            for (String name : new String[] {"gps", "network", "passive", "fused"}) {
                DvmObject<?> miss = invokeLocationManagerGetProvider(emptyJni, emptyBase, useVaList,
                        manager, name);
                assertTrue(miss == null);
                assertGetProviderEvent(emptySink, name, "null");
            }
            assertEquals(4, countEvents(emptySink.events, "android_location",
                    "LocationManager.getProvider"));
        } finally {
            if (emptyEmu != null) {
                TraceEnvironmentEventSink.unregister(emptyEmu, emptySink);
                emptyEmu.close();
            }
        }
    }

    private static void runGetProviderAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        for (String json : new String[] {NO_LOCATION_JSON, LOCATION_WITHOUT_PROVIDERS_JSON,
                LAST_KNOWN_FULL_JSON}) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
            AndroidEmulator emulator = null;
            CapturingSink sink = new CapturingSink();
            try {
                emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                        : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                TraceEnvironmentEventSink.register(emulator, sink);
                VM vm = emulator.createDalvikVM();
                AbstractJni jni = new AbstractJni() {
                };
                vm.setJni(jni);
                BaseVM baseVM = (BaseVM) vm;
                DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
                try {
                    invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager, "gps");
                    fail("expected UOE for getProvider without providers: " + json);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("getProvider"));
                }
                for (CapturedEvent e : sink.events) {
                    assertFalse("unexpected android_location getProvider event: " + e.api,
                            "android_location".equals(e.kind)
                                    && "LocationManager.getProvider".equals(e.api));
                }
            } finally {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                    emulator.close();
                }
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_FULL_JSON);
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

            DvmObject<?> plain = vm.resolveClass(LOCATION_MANAGER_CLASS).newObject(null);
            try {
                invokeLocationManagerGetProvider(jni, baseVM, useVaList, plain, "gps");
                fail("expected UOE for getProvider on plain LocationManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> wifi = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            try {
                invokeLocationManagerGetProvider(jni, baseVM, useVaList, wifi, "gps");
                fail("expected UOE for getProvider on non-location SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeLocationManagerGetProviderNull(jni, baseVM, useVaList, manager);
                fail("expected UOE for null getProvider argument");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }
            try {
                invokeLocationManagerGetProviderNonString(jni, baseVM, useVaList, manager);
                fail("expected UOE for non-String getProvider argument");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }

            try {
                DvmClass dvmClass = manager.getObjectType();
                DvmMethod method = new DvmMethod(dvmClass, "getBestProvider",
                        "(Landroid/location/Criteria;Z)Ljava/lang/String;", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callObjectMethodV(baseVM, manager, signature,
                            new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callObjectMethod(baseVM, manager, signature,
                            new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for getBestProvider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getBestProvider"));
            }

            DvmObject<?> plainProvider = vm.resolveClass(LOCATION_PROVIDER_CLASS).newObject(null);
            try {
                invokeLocationProviderGetName(jni, baseVM, useVaList, plainProvider);
                fail("expected UOE for getName on plain LocationProvider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_location event on isolation: " + e.api,
                        "android_location".equals(e.kind));
            }

            DvmObject<?> gps = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            assertConfiguredLocationProvider(gps);
            assertEquals("gps", invokeLocationProviderGetName(jni, baseVM, useVaList, gps));
            assertEquals(1, countEvents(sink.events, "android_location",
                    "LocationManager.getProvider"));
            assertEquals(1, countEvents(sink.events, "android_location",
                    "LocationProvider.getName"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runGetProviderGetNameForeignStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_FULL_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> gpsA = invokeLocationManagerGetProvider(jniA, baseA, useVaList, managerA,
                    "gps");
            assertConfiguredLocationProvider(gpsA);
            assertEquals("gps", invokeLocationProviderGetName(jniA, baseA, useVaList, gpsA));
            assertEquals(1, countEvents(sinkA.events, "android_location",
                    "LocationProvider.getName"));

            try {
                invokeLocationProviderGetName(jniB, baseB, useVaList, gpsA);
                fail("expected UOE for cross-VM LocationProvider.getName");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            for (CapturedEvent e : sinkB.events) {
                assertFalse("unexpected android_location event on VM B: " + e.api,
                        "android_location".equals(e.kind));
            }

            emulatorA.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(PROVIDERS_FULL_JSON));
            try {
                invokeLocationProviderGetName(jniA, baseA, useVaList, gpsA);
                fail("expected UOE for stale LocationProvider.getName");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getName"));
            }
            assertEquals(1, countEvents(sinkA.events, "android_location",
                    "LocationProvider.getName"));

            DvmObject<?> gpsFresh = invokeLocationManagerGetProvider(jniA, baseA, useVaList,
                    managerA, "gps");
            assertConfiguredLocationProvider(gpsFresh);
            assertTrue(gpsA != gpsFresh);
            assertEquals("gps", invokeLocationProviderGetName(jniA, baseA, useVaList, gpsFresh));
            assertEquals(2, countEvents(sinkA.events, "android_location",
                    "LocationProvider.getName"));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runRequiresNetworkConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_NETWORK_JSON);
        assertTrue(config.isAndroidLocationProvidersConfigured());
        assertTrue(config.getAndroidLocationProvidersConfig().isGps());
        assertFalse(config.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(config.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isRequiresNetwork());
        assertTrue(config.getAndroidLocationProviderCapability("network").isRequiresNetwork());

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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gps = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            DvmObject<?> network = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "network");
            DvmObject<?> passive = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "passive");
            assertConfiguredLocationProvider(gps);
            assertConfiguredLocationProvider(network);
            assertConfiguredLocationProvider(passive);

            assertFalse(invokeLocationProviderRequiresNetwork(jni, baseVM, useVaList, gps));
            assertTrue(invokeLocationProviderRequiresNetwork(jni, baseVM, useVaList, network));

            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps"));
            assertFalse(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "network"));
            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "passive"));

            int beforePassive = sink.events.size();
            try {
                invokeLocationProviderRequiresNetwork(jni, baseVM, useVaList, passive);
                fail("expected UOE for requiresNetwork without passive capability entry");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresNetwork"));
            }
            assertEquals(beforePassive, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.requiresNetwork"));
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationManager.getProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runRequiresNetworkAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runRequiresNetworkUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_FULL_JSON, "gps",
                "missing providerCapabilities node");
        runRequiresNetworkUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_CAPABILITIES_EMPTY_JSON,
                "gps", "empty providerCapabilities");
        runRequiresNetworkUoeAfterGetProvider(is64Bit, useVaList,
                PROVIDERS_CAPABILITIES_GPS_EMPTY_ENTRY_JSON, "gps",
                "gps entry missing requiresNetwork");
        runRequiresNetworkUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_NETWORK_JSON,
                "passive", "passive capability entry omitted");

        TraceEnvironmentConfig capsOnly = TraceEnvironmentConfig.parse(
                CAPABILITIES_WITHOUT_PROVIDERS_JSON);
        AndroidEmulator capsOnlyEmu = null;
        CapturingSink capsOnlySink = new CapturingSink();
        try {
            capsOnlyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                    : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(capsOnly)
                    .build();
            TraceEnvironmentEventSink.register(capsOnlyEmu, capsOnlySink);
            VM vm = capsOnlyEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager, "gps");
                fail("expected UOE for getProvider without providers (capabilities-only)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }
            assertNoAndroidLocationEvents(capsOnlySink);
        } finally {
            if (capsOnlyEmu != null) {
                TraceEnvironmentEventSink.unregister(capsOnlyEmu, capsOnlySink);
                capsOnlyEmu.close();
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_NETWORK_JSON);
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

            DvmObject<?> plainProvider = vm.resolveClass(LOCATION_PROVIDER_CLASS).newObject(null);
            try {
                invokeLocationProviderRequiresNetwork(jni, baseVM, useVaList, plainProvider);
                fail("expected UOE for requiresNetwork on plain LocationProvider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresNetwork"));
            }
            assertNoAndroidLocationEvents(sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runRequiresNetworkUoeAfterGetProvider(boolean is64Bit, boolean useVaList,
                                                              String json, String provider,
                                                              String reason) throws Exception {
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
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> marker = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    provider);
            assertConfiguredLocationProvider(marker);
            int before = sink.events.size();
            try {
                invokeLocationProviderRequiresNetwork(jni, baseVM, useVaList, marker);
                fail("expected UOE for requiresNetwork: " + reason);
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresNetwork"));
            }
            assertEquals(before, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.requiresNetwork"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runRequiresNetworkForeignStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_NETWORK_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> gpsA = invokeLocationManagerGetProvider(jniA, baseA, useVaList, managerA,
                    "gps");
            assertConfiguredLocationProvider(gpsA);
            assertFalse(invokeLocationProviderRequiresNetwork(jniA, baseA, useVaList, gpsA));

            try {
                invokeLocationProviderRequiresNetwork(jniB, baseB, useVaList, gpsA);
                fail("expected UOE for cross-VM LocationProvider.requiresNetwork");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresNetwork"));
            }
            assertNoAndroidLocationEvents(sinkB);

            emulatorA.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_NETWORK_JSON));
            try {
                invokeLocationProviderRequiresNetwork(jniA, baseA, useVaList, gpsA);
                fail("expected UOE for stale LocationProvider.requiresNetwork");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresNetwork"));
            }
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.requiresNetwork"));

            DvmObject<?> gpsFresh = invokeLocationManagerGetProvider(jniA, baseA, useVaList,
                    managerA, "gps");
            assertConfiguredLocationProvider(gpsFresh);
            assertTrue(gpsA != gpsFresh);
            assertFalse(invokeLocationProviderRequiresNetwork(jniA, baseA, useVaList, gpsFresh));
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.requiresNetwork"));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runRequiresSatelliteConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_SATELLITE_JSON);
        assertTrue(config.isAndroidLocationProvidersConfigured());
        assertTrue(config.getAndroidLocationProvidersConfig().isGps());
        assertFalse(config.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(config.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isRequiresSatellite());
        assertTrue(config.getAndroidLocationProviderCapability("network").isRequiresSatellite());

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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gps = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            DvmObject<?> network = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "network");
            DvmObject<?> passive = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "passive");
            assertConfiguredLocationProvider(gps);
            assertConfiguredLocationProvider(network);
            assertConfiguredLocationProvider(passive);

            assertFalse(invokeLocationProviderRequiresSatellite(jni, baseVM, useVaList, gps));
            assertTrue(invokeLocationProviderRequiresSatellite(jni, baseVM, useVaList, network));

            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps"));
            assertFalse(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "network"));
            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "passive"));

            int beforePassive = sink.events.size();
            try {
                invokeLocationProviderRequiresSatellite(jni, baseVM, useVaList, passive);
                fail("expected UOE for requiresSatellite without passive capability entry");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresSatellite"));
            }
            assertEquals(beforePassive, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.requiresSatellite"));
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationManager.getProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runRequiresSatelliteAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runRequiresSatelliteUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_FULL_JSON, "gps",
                "missing providerCapabilities node");
        runRequiresSatelliteUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_CAPABILITIES_EMPTY_JSON,
                "gps", "empty providerCapabilities");
        runRequiresSatelliteUoeAfterGetProvider(is64Bit, useVaList,
                PROVIDERS_CAPABILITIES_GPS_EMPTY_ENTRY_JSON, "gps",
                "gps entry missing requiresSatellite");
        runRequiresSatelliteUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_NETWORK_JSON,
                "gps", "gps entry has requiresNetwork but not requiresSatellite");
        runRequiresSatelliteUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_SATELLITE_JSON,
                "passive", "passive capability entry omitted");

        TraceEnvironmentConfig capsOnly = TraceEnvironmentConfig.parse(
                CAPABILITIES_WITHOUT_PROVIDERS_JSON);
        AndroidEmulator capsOnlyEmu = null;
        CapturingSink capsOnlySink = new CapturingSink();
        try {
            capsOnlyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                    : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(capsOnly)
                    .build();
            TraceEnvironmentEventSink.register(capsOnlyEmu, capsOnlySink);
            VM vm = capsOnlyEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager, "gps");
                fail("expected UOE for getProvider without providers (capabilities-only)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }
            assertNoAndroidLocationEvents(capsOnlySink);
        } finally {
            if (capsOnlyEmu != null) {
                TraceEnvironmentEventSink.unregister(capsOnlyEmu, capsOnlySink);
                capsOnlyEmu.close();
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_SATELLITE_JSON);
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

            DvmObject<?> plainProvider = vm.resolveClass(LOCATION_PROVIDER_CLASS).newObject(null);
            try {
                invokeLocationProviderRequiresSatellite(jni, baseVM, useVaList, plainProvider);
                fail("expected UOE for requiresSatellite on plain LocationProvider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresSatellite"));
            }
            assertNoAndroidLocationEvents(sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runRequiresSatelliteUoeAfterGetProvider(boolean is64Bit, boolean useVaList,
                                                                String json, String provider,
                                                                String reason) throws Exception {
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
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> marker = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    provider);
            assertConfiguredLocationProvider(marker);
            int before = sink.events.size();
            try {
                invokeLocationProviderRequiresSatellite(jni, baseVM, useVaList, marker);
                fail("expected UOE for requiresSatellite: " + reason);
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresSatellite"));
            }
            assertEquals(before, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.requiresSatellite"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runRequiresSatelliteForeignStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_SATELLITE_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> gpsA = invokeLocationManagerGetProvider(jniA, baseA, useVaList, managerA,
                    "gps");
            assertConfiguredLocationProvider(gpsA);
            assertFalse(invokeLocationProviderRequiresSatellite(jniA, baseA, useVaList, gpsA));

            try {
                invokeLocationProviderRequiresSatellite(jniB, baseB, useVaList, gpsA);
                fail("expected UOE for cross-VM LocationProvider.requiresSatellite");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresSatellite"));
            }
            assertNoAndroidLocationEvents(sinkB);

            emulatorA.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_SATELLITE_JSON));
            try {
                invokeLocationProviderRequiresSatellite(jniA, baseA, useVaList, gpsA);
                fail("expected UOE for stale LocationProvider.requiresSatellite");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresSatellite"));
            }
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.requiresSatellite"));

            DvmObject<?> gpsFresh = invokeLocationManagerGetProvider(jniA, baseA, useVaList,
                    managerA, "gps");
            assertConfiguredLocationProvider(gpsFresh);
            assertTrue(gpsA != gpsFresh);
            assertFalse(invokeLocationProviderRequiresSatellite(jniA, baseA, useVaList, gpsFresh));
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.requiresSatellite"));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runRequiresCellConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_CELL_JSON);
        assertTrue(config.isAndroidLocationProvidersConfigured());
        assertTrue(config.getAndroidLocationProvidersConfig().isGps());
        assertFalse(config.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(config.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isRequiresCell());
        assertTrue(config.getAndroidLocationProviderCapability("network").isRequiresCell());

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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gps = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            DvmObject<?> network = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "network");
            DvmObject<?> passive = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "passive");
            assertConfiguredLocationProvider(gps);
            assertConfiguredLocationProvider(network);
            assertConfiguredLocationProvider(passive);

            assertFalse(invokeLocationProviderRequiresCell(jni, baseVM, useVaList, gps));
            assertTrue(invokeLocationProviderRequiresCell(jni, baseVM, useVaList, network));

            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps"));
            assertFalse(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "network"));
            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "passive"));

            int beforePassive = sink.events.size();
            try {
                invokeLocationProviderRequiresCell(jni, baseVM, useVaList, passive);
                fail("expected UOE for requiresCell without passive capability entry");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresCell"));
            }
            assertEquals(beforePassive, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.requiresCell"));
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationManager.getProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runRequiresCellAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runRequiresCellUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_FULL_JSON, "gps",
                "missing providerCapabilities node");
        runRequiresCellUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_CAPABILITIES_EMPTY_JSON,
                "gps", "empty providerCapabilities");
        runRequiresCellUoeAfterGetProvider(is64Bit, useVaList,
                PROVIDERS_CAPABILITIES_GPS_EMPTY_ENTRY_JSON, "gps",
                "gps entry missing requiresCell");
        runRequiresCellUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_NETWORK_JSON,
                "gps", "gps entry has requiresNetwork but not requiresCell");
        runRequiresCellUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_SATELLITE_JSON,
                "gps", "gps entry has requiresSatellite but not requiresCell");
        runRequiresCellUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_CELL_JSON,
                "passive", "passive capability entry omitted");

        TraceEnvironmentConfig capsOnly = TraceEnvironmentConfig.parse(
                CAPABILITIES_WITHOUT_PROVIDERS_JSON);
        AndroidEmulator capsOnlyEmu = null;
        CapturingSink capsOnlySink = new CapturingSink();
        try {
            capsOnlyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                    : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(capsOnly)
                    .build();
            TraceEnvironmentEventSink.register(capsOnlyEmu, capsOnlySink);
            VM vm = capsOnlyEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager, "gps");
                fail("expected UOE for getProvider without providers (capabilities-only)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }
            assertNoAndroidLocationEvents(capsOnlySink);
        } finally {
            if (capsOnlyEmu != null) {
                TraceEnvironmentEventSink.unregister(capsOnlyEmu, capsOnlySink);
                capsOnlyEmu.close();
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_CELL_JSON);
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

            DvmObject<?> plainProvider = vm.resolveClass(LOCATION_PROVIDER_CLASS).newObject(null);
            try {
                invokeLocationProviderRequiresCell(jni, baseVM, useVaList, plainProvider);
                fail("expected UOE for requiresCell on plain LocationProvider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresCell"));
            }
            assertNoAndroidLocationEvents(sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runRequiresCellUoeAfterGetProvider(boolean is64Bit, boolean useVaList,
                                                           String json, String provider,
                                                           String reason) throws Exception {
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
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> marker = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    provider);
            assertConfiguredLocationProvider(marker);
            int before = sink.events.size();
            try {
                invokeLocationProviderRequiresCell(jni, baseVM, useVaList, marker);
                fail("expected UOE for requiresCell: " + reason);
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresCell"));
            }
            assertEquals(before, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.requiresCell"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runRequiresCellForeignStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_CELL_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> gpsA = invokeLocationManagerGetProvider(jniA, baseA, useVaList, managerA,
                    "gps");
            assertConfiguredLocationProvider(gpsA);
            assertFalse(invokeLocationProviderRequiresCell(jniA, baseA, useVaList, gpsA));

            try {
                invokeLocationProviderRequiresCell(jniB, baseB, useVaList, gpsA);
                fail("expected UOE for cross-VM LocationProvider.requiresCell");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresCell"));
            }
            assertNoAndroidLocationEvents(sinkB);

            emulatorA.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(PROVIDERS_REQUIRES_CELL_JSON));
            try {
                invokeLocationProviderRequiresCell(jniA, baseA, useVaList, gpsA);
                fail("expected UOE for stale LocationProvider.requiresCell");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresCell"));
            }
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.requiresCell"));

            DvmObject<?> gpsFresh = invokeLocationManagerGetProvider(jniA, baseA, useVaList,
                    managerA, "gps");
            assertConfiguredLocationProvider(gpsFresh);
            assertTrue(gpsA != gpsFresh);
            assertFalse(invokeLocationProviderRequiresCell(jniA, baseA, useVaList, gpsFresh));
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.requiresCell"));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runHasMonetaryCostConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_HAS_MONETARY_COST_JSON);
        assertTrue(config.isAndroidLocationProvidersConfigured());
        assertTrue(config.getAndroidLocationProvidersConfig().isGps());
        assertFalse(config.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(config.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isHasMonetaryCost());
        assertTrue(config.getAndroidLocationProviderCapability("network").isHasMonetaryCost());

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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gps = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            DvmObject<?> network = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "network");
            DvmObject<?> passive = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "passive");
            assertConfiguredLocationProvider(gps);
            assertConfiguredLocationProvider(network);
            assertConfiguredLocationProvider(passive);

            assertFalse(invokeLocationProviderHasMonetaryCost(jni, baseVM, useVaList, gps));
            assertTrue(invokeLocationProviderHasMonetaryCost(jni, baseVM, useVaList, network));

            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps"));
            assertFalse(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "network"));
            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "passive"));

            int beforePassive = sink.events.size();
            try {
                invokeLocationProviderHasMonetaryCost(jni, baseVM, useVaList, passive);
                fail("expected UOE for hasMonetaryCost without passive capability entry");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMonetaryCost"));
            }
            assertEquals(beforePassive, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.hasMonetaryCost"));
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationManager.getProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runHasMonetaryCostAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runHasMonetaryCostUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_FULL_JSON, "gps",
                "missing providerCapabilities node");
        runHasMonetaryCostUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_CAPABILITIES_EMPTY_JSON,
                "gps", "empty providerCapabilities");
        runHasMonetaryCostUoeAfterGetProvider(is64Bit, useVaList,
                PROVIDERS_CAPABILITIES_GPS_EMPTY_ENTRY_JSON, "gps",
                "gps entry missing hasMonetaryCost");
        runHasMonetaryCostUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_NETWORK_JSON,
                "gps", "gps entry has requiresNetwork but not hasMonetaryCost");
        runHasMonetaryCostUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_SATELLITE_JSON,
                "gps", "gps entry has requiresSatellite but not hasMonetaryCost");
        runHasMonetaryCostUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_CELL_JSON,
                "gps", "gps entry has requiresCell but not hasMonetaryCost");
        runHasMonetaryCostUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_HAS_MONETARY_COST_JSON,
                "passive", "passive capability entry omitted");

        TraceEnvironmentConfig capsOnly = TraceEnvironmentConfig.parse(
                CAPABILITIES_WITHOUT_PROVIDERS_JSON);
        AndroidEmulator capsOnlyEmu = null;
        CapturingSink capsOnlySink = new CapturingSink();
        try {
            capsOnlyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                    : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(capsOnly)
                    .build();
            TraceEnvironmentEventSink.register(capsOnlyEmu, capsOnlySink);
            VM vm = capsOnlyEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager, "gps");
                fail("expected UOE for getProvider without providers (capabilities-only)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }
            assertNoAndroidLocationEvents(capsOnlySink);
        } finally {
            if (capsOnlyEmu != null) {
                TraceEnvironmentEventSink.unregister(capsOnlyEmu, capsOnlySink);
                capsOnlyEmu.close();
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_HAS_MONETARY_COST_JSON);
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

            DvmObject<?> plainProvider = vm.resolveClass(LOCATION_PROVIDER_CLASS).newObject(null);
            try {
                invokeLocationProviderHasMonetaryCost(jni, baseVM, useVaList, plainProvider);
                fail("expected UOE for hasMonetaryCost on plain LocationProvider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMonetaryCost"));
            }
            assertNoAndroidLocationEvents(sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runHasMonetaryCostUoeAfterGetProvider(boolean is64Bit, boolean useVaList,
                                                              String json, String provider,
                                                              String reason) throws Exception {
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
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> marker = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    provider);
            assertConfiguredLocationProvider(marker);
            int before = sink.events.size();
            try {
                invokeLocationProviderHasMonetaryCost(jni, baseVM, useVaList, marker);
                fail("expected UOE for hasMonetaryCost: " + reason);
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMonetaryCost"));
            }
            assertEquals(before, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.hasMonetaryCost"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runHasMonetaryCostForeignStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_HAS_MONETARY_COST_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> gpsA = invokeLocationManagerGetProvider(jniA, baseA, useVaList, managerA,
                    "gps");
            assertConfiguredLocationProvider(gpsA);
            assertFalse(invokeLocationProviderHasMonetaryCost(jniA, baseA, useVaList, gpsA));

            try {
                invokeLocationProviderHasMonetaryCost(jniB, baseB, useVaList, gpsA);
                fail("expected UOE for cross-VM LocationProvider.hasMonetaryCost");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMonetaryCost"));
            }
            assertNoAndroidLocationEvents(sinkB);

            emulatorA.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(PROVIDERS_HAS_MONETARY_COST_JSON));
            try {
                invokeLocationProviderHasMonetaryCost(jniA, baseA, useVaList, gpsA);
                fail("expected UOE for stale LocationProvider.hasMonetaryCost");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasMonetaryCost"));
            }
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.hasMonetaryCost"));

            DvmObject<?> gpsFresh = invokeLocationManagerGetProvider(jniA, baseA, useVaList,
                    managerA, "gps");
            assertConfiguredLocationProvider(gpsFresh);
            assertTrue(gpsA != gpsFresh);
            assertFalse(invokeLocationProviderHasMonetaryCost(jniA, baseA, useVaList, gpsFresh));
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.hasMonetaryCost"));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runSupportsAltitudeConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_ALTITUDE_JSON);
        assertTrue(config.isAndroidLocationProvidersConfigured());
        assertTrue(config.getAndroidLocationProvidersConfig().isGps());
        assertFalse(config.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(config.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isSupportsAltitude());
        assertTrue(config.getAndroidLocationProviderCapability("network").isSupportsAltitude());

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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gps = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            DvmObject<?> network = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "network");
            DvmObject<?> passive = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "passive");
            assertConfiguredLocationProvider(gps);
            assertConfiguredLocationProvider(network);
            assertConfiguredLocationProvider(passive);

            assertFalse(invokeLocationProviderSupportsAltitude(jni, baseVM, useVaList, gps));
            assertTrue(invokeLocationProviderSupportsAltitude(jni, baseVM, useVaList, network));

            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps"));
            assertFalse(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "network"));
            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "passive"));

            int beforePassive = sink.events.size();
            try {
                invokeLocationProviderSupportsAltitude(jni, baseVM, useVaList, passive);
                fail("expected UOE for supportsAltitude without passive capability entry");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsAltitude"));
            }
            assertEquals(beforePassive, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.supportsAltitude"));
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationManager.getProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSupportsAltitudeAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runSupportsAltitudeUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_FULL_JSON, "gps",
                "missing providerCapabilities node");
        runSupportsAltitudeUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_CAPABILITIES_EMPTY_JSON,
                "gps", "empty providerCapabilities");
        runSupportsAltitudeUoeAfterGetProvider(is64Bit, useVaList,
                PROVIDERS_CAPABILITIES_GPS_EMPTY_ENTRY_JSON, "gps",
                "gps entry missing supportsAltitude");
        runSupportsAltitudeUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_NETWORK_JSON,
                "gps", "gps entry has requiresNetwork but not supportsAltitude");
        runSupportsAltitudeUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_SATELLITE_JSON,
                "gps", "gps entry has requiresSatellite but not supportsAltitude");
        runSupportsAltitudeUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_CELL_JSON,
                "gps", "gps entry has requiresCell but not supportsAltitude");
        runSupportsAltitudeUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_HAS_MONETARY_COST_JSON,
                "gps", "gps entry has hasMonetaryCost but not supportsAltitude");
        runSupportsAltitudeUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_ALTITUDE_JSON,
                "passive", "passive capability entry omitted");

        TraceEnvironmentConfig capsOnly = TraceEnvironmentConfig.parse(
                CAPABILITIES_WITHOUT_PROVIDERS_JSON);
        AndroidEmulator capsOnlyEmu = null;
        CapturingSink capsOnlySink = new CapturingSink();
        try {
            capsOnlyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                    : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(capsOnly)
                    .build();
            TraceEnvironmentEventSink.register(capsOnlyEmu, capsOnlySink);
            VM vm = capsOnlyEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager, "gps");
                fail("expected UOE for getProvider without providers (capabilities-only)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }
            assertNoAndroidLocationEvents(capsOnlySink);
        } finally {
            if (capsOnlyEmu != null) {
                TraceEnvironmentEventSink.unregister(capsOnlyEmu, capsOnlySink);
                capsOnlyEmu.close();
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_ALTITUDE_JSON);
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

            DvmObject<?> plainProvider = vm.resolveClass(LOCATION_PROVIDER_CLASS).newObject(null);
            try {
                invokeLocationProviderSupportsAltitude(jni, baseVM, useVaList, plainProvider);
                fail("expected UOE for supportsAltitude on plain LocationProvider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsAltitude"));
            }
            assertNoAndroidLocationEvents(sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSupportsAltitudeUoeAfterGetProvider(boolean is64Bit, boolean useVaList,
                                                               String json, String provider,
                                                               String reason) throws Exception {
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
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> marker = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    provider);
            assertConfiguredLocationProvider(marker);
            int before = sink.events.size();
            try {
                invokeLocationProviderSupportsAltitude(jni, baseVM, useVaList, marker);
                fail("expected UOE for supportsAltitude: " + reason);
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsAltitude"));
            }
            assertEquals(before, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.supportsAltitude"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSupportsAltitudeForeignStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_ALTITUDE_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> gpsA = invokeLocationManagerGetProvider(jniA, baseA, useVaList, managerA,
                    "gps");
            assertConfiguredLocationProvider(gpsA);
            assertFalse(invokeLocationProviderSupportsAltitude(jniA, baseA, useVaList, gpsA));

            try {
                invokeLocationProviderSupportsAltitude(jniB, baseB, useVaList, gpsA);
                fail("expected UOE for cross-VM LocationProvider.supportsAltitude");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsAltitude"));
            }
            assertNoAndroidLocationEvents(sinkB);

            emulatorA.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_ALTITUDE_JSON));
            try {
                invokeLocationProviderSupportsAltitude(jniA, baseA, useVaList, gpsA);
                fail("expected UOE for stale LocationProvider.supportsAltitude");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsAltitude"));
            }
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.supportsAltitude"));

            DvmObject<?> gpsFresh = invokeLocationManagerGetProvider(jniA, baseA, useVaList,
                    managerA, "gps");
            assertConfiguredLocationProvider(gpsFresh);
            assertTrue(gpsA != gpsFresh);
            assertFalse(invokeLocationProviderSupportsAltitude(jniA, baseA, useVaList, gpsFresh));
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.supportsAltitude"));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runSupportsSpeedConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_SPEED_JSON);
        assertTrue(config.isAndroidLocationProvidersConfigured());
        assertTrue(config.getAndroidLocationProvidersConfig().isGps());
        assertFalse(config.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(config.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isSupportsSpeed());
        assertTrue(config.getAndroidLocationProviderCapability("network").isSupportsSpeed());

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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gps = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            DvmObject<?> network = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "network");
            DvmObject<?> passive = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "passive");
            assertConfiguredLocationProvider(gps);
            assertConfiguredLocationProvider(network);
            assertConfiguredLocationProvider(passive);

            assertFalse(invokeLocationProviderSupportsSpeed(jni, baseVM, useVaList, gps));
            assertTrue(invokeLocationProviderSupportsSpeed(jni, baseVM, useVaList, network));

            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps"));
            assertFalse(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "network"));
            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "passive"));

            int beforePassive = sink.events.size();
            try {
                invokeLocationProviderSupportsSpeed(jni, baseVM, useVaList, passive);
                fail("expected UOE for supportsSpeed without passive capability entry");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsSpeed"));
            }
            assertEquals(beforePassive, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.supportsSpeed"));
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationManager.getProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSupportsSpeedAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runSupportsSpeedUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_FULL_JSON, "gps",
                "missing providerCapabilities node");
        runSupportsSpeedUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_CAPABILITIES_EMPTY_JSON,
                "gps", "empty providerCapabilities");
        runSupportsSpeedUoeAfterGetProvider(is64Bit, useVaList,
                PROVIDERS_CAPABILITIES_GPS_EMPTY_ENTRY_JSON, "gps",
                "gps entry missing supportsSpeed");
        runSupportsSpeedUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_NETWORK_JSON,
                "gps", "gps entry has requiresNetwork but not supportsSpeed");
        runSupportsSpeedUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_SATELLITE_JSON,
                "gps", "gps entry has requiresSatellite but not supportsSpeed");
        runSupportsSpeedUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_CELL_JSON,
                "gps", "gps entry has requiresCell but not supportsSpeed");
        runSupportsSpeedUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_HAS_MONETARY_COST_JSON,
                "gps", "gps entry has hasMonetaryCost but not supportsSpeed");
        runSupportsSpeedUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_ALTITUDE_JSON,
                "gps", "gps entry has supportsAltitude but not supportsSpeed");
        runSupportsSpeedUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_SPEED_JSON,
                "passive", "passive capability entry omitted");

        TraceEnvironmentConfig capsOnly = TraceEnvironmentConfig.parse(
                CAPABILITIES_WITHOUT_PROVIDERS_JSON);
        AndroidEmulator capsOnlyEmu = null;
        CapturingSink capsOnlySink = new CapturingSink();
        try {
            capsOnlyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                    : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(capsOnly)
                    .build();
            TraceEnvironmentEventSink.register(capsOnlyEmu, capsOnlySink);
            VM vm = capsOnlyEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager, "gps");
                fail("expected UOE for getProvider without providers (capabilities-only)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }
            assertNoAndroidLocationEvents(capsOnlySink);
        } finally {
            if (capsOnlyEmu != null) {
                TraceEnvironmentEventSink.unregister(capsOnlyEmu, capsOnlySink);
                capsOnlyEmu.close();
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_SPEED_JSON);
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

            DvmObject<?> plainProvider = vm.resolveClass(LOCATION_PROVIDER_CLASS).newObject(null);
            try {
                invokeLocationProviderSupportsSpeed(jni, baseVM, useVaList, plainProvider);
                fail("expected UOE for supportsSpeed on plain LocationProvider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsSpeed"));
            }
            assertNoAndroidLocationEvents(sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSupportsSpeedUoeAfterGetProvider(boolean is64Bit, boolean useVaList,
                                                            String json, String provider,
                                                            String reason) throws Exception {
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
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> marker = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    provider);
            assertConfiguredLocationProvider(marker);
            int before = sink.events.size();
            try {
                invokeLocationProviderSupportsSpeed(jni, baseVM, useVaList, marker);
                fail("expected UOE for supportsSpeed: " + reason);
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsSpeed"));
            }
            assertEquals(before, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.supportsSpeed"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSupportsSpeedForeignStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_SPEED_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> gpsA = invokeLocationManagerGetProvider(jniA, baseA, useVaList, managerA,
                    "gps");
            assertConfiguredLocationProvider(gpsA);
            assertFalse(invokeLocationProviderSupportsSpeed(jniA, baseA, useVaList, gpsA));

            try {
                invokeLocationProviderSupportsSpeed(jniB, baseB, useVaList, gpsA);
                fail("expected UOE for cross-VM LocationProvider.supportsSpeed");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsSpeed"));
            }
            assertNoAndroidLocationEvents(sinkB);

            emulatorA.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_SPEED_JSON));
            try {
                invokeLocationProviderSupportsSpeed(jniA, baseA, useVaList, gpsA);
                fail("expected UOE for stale LocationProvider.supportsSpeed");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsSpeed"));
            }
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.supportsSpeed"));

            DvmObject<?> gpsFresh = invokeLocationManagerGetProvider(jniA, baseA, useVaList,
                    managerA, "gps");
            assertConfiguredLocationProvider(gpsFresh);
            assertTrue(gpsA != gpsFresh);
            assertFalse(invokeLocationProviderSupportsSpeed(jniA, baseA, useVaList, gpsFresh));
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.supportsSpeed"));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runSupportsBearingConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_BEARING_JSON);
        assertTrue(config.isAndroidLocationProvidersConfigured());
        assertTrue(config.getAndroidLocationProvidersConfig().isGps());
        assertFalse(config.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(config.isAndroidLocationProviderCapabilitiesConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isSupportsBearing());
        assertTrue(config.getAndroidLocationProviderCapability("network").isSupportsBearing());

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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gps = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            DvmObject<?> network = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "network");
            DvmObject<?> passive = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "passive");
            assertConfiguredLocationProvider(gps);
            assertConfiguredLocationProvider(network);
            assertConfiguredLocationProvider(passive);

            assertFalse(invokeLocationProviderSupportsBearing(jni, baseVM, useVaList, gps));
            assertTrue(invokeLocationProviderSupportsBearing(jni, baseVM, useVaList, network));

            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps"));
            assertFalse(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "network"));
            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "passive"));

            int beforePassive = sink.events.size();
            try {
                invokeLocationProviderSupportsBearing(jni, baseVM, useVaList, passive);
                fail("expected UOE for supportsBearing without passive capability entry");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsBearing"));
            }
            assertEquals(beforePassive, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.supportsBearing"));
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationManager.getProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSupportsBearingAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runSupportsBearingUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_FULL_JSON, "gps",
                "missing providerCapabilities node");
        runSupportsBearingUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_CAPABILITIES_EMPTY_JSON,
                "gps", "empty providerCapabilities");
        runSupportsBearingUoeAfterGetProvider(is64Bit, useVaList,
                PROVIDERS_CAPABILITIES_GPS_EMPTY_ENTRY_JSON, "gps",
                "gps entry missing supportsBearing");
        runSupportsBearingUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_NETWORK_JSON,
                "gps", "gps entry has requiresNetwork but not supportsBearing");
        runSupportsBearingUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_SATELLITE_JSON,
                "gps", "gps entry has requiresSatellite but not supportsBearing");
        runSupportsBearingUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_CELL_JSON,
                "gps", "gps entry has requiresCell but not supportsBearing");
        runSupportsBearingUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_HAS_MONETARY_COST_JSON,
                "gps", "gps entry has hasMonetaryCost but not supportsBearing");
        runSupportsBearingUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_ALTITUDE_JSON,
                "gps", "gps entry has supportsAltitude but not supportsBearing");
        runSupportsBearingUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_SPEED_JSON,
                "gps", "gps entry has supportsSpeed but not supportsBearing");
        runSupportsBearingUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_BEARING_JSON,
                "passive", "passive capability entry omitted");

        TraceEnvironmentConfig capsOnly = TraceEnvironmentConfig.parse(
                CAPABILITIES_WITHOUT_PROVIDERS_JSON);
        AndroidEmulator capsOnlyEmu = null;
        CapturingSink capsOnlySink = new CapturingSink();
        try {
            capsOnlyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                    : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(capsOnly)
                    .build();
            TraceEnvironmentEventSink.register(capsOnlyEmu, capsOnlySink);
            VM vm = capsOnlyEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager, "gps");
                fail("expected UOE for getProvider without providers (capabilities-only)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }
            assertNoAndroidLocationEvents(capsOnlySink);
        } finally {
            if (capsOnlyEmu != null) {
                TraceEnvironmentEventSink.unregister(capsOnlyEmu, capsOnlySink);
                capsOnlyEmu.close();
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_BEARING_JSON);
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

            DvmObject<?> plainProvider = vm.resolveClass(LOCATION_PROVIDER_CLASS).newObject(null);
            try {
                invokeLocationProviderSupportsBearing(jni, baseVM, useVaList, plainProvider);
                fail("expected UOE for supportsBearing on plain LocationProvider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsBearing"));
            }
            assertNoAndroidLocationEvents(sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSupportsBearingUoeAfterGetProvider(boolean is64Bit, boolean useVaList,
                                                              String json, String provider,
                                                              String reason) throws Exception {
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
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> marker = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    provider);
            assertConfiguredLocationProvider(marker);
            int before = sink.events.size();
            try {
                invokeLocationProviderSupportsBearing(jni, baseVM, useVaList, marker);
                fail("expected UOE for supportsBearing: " + reason);
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsBearing"));
            }
            assertEquals(before, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.supportsBearing"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runSupportsBearingForeignStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_BEARING_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> gpsA = invokeLocationManagerGetProvider(jniA, baseA, useVaList, managerA,
                    "gps");
            assertConfiguredLocationProvider(gpsA);
            assertFalse(invokeLocationProviderSupportsBearing(jniA, baseA, useVaList, gpsA));

            try {
                invokeLocationProviderSupportsBearing(jniB, baseB, useVaList, gpsA);
                fail("expected UOE for cross-VM LocationProvider.supportsBearing");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsBearing"));
            }
            assertNoAndroidLocationEvents(sinkB);

            emulatorA.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(PROVIDERS_SUPPORTS_BEARING_JSON));
            try {
                invokeLocationProviderSupportsBearing(jniA, baseA, useVaList, gpsA);
                fail("expected UOE for stale LocationProvider.supportsBearing");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsBearing"));
            }
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.supportsBearing"));

            DvmObject<?> gpsFresh = invokeLocationManagerGetProvider(jniA, baseA, useVaList,
                    managerA, "gps");
            assertConfiguredLocationProvider(gpsFresh);
            assertTrue(gpsA != gpsFresh);
            assertFalse(invokeLocationProviderSupportsBearing(jniA, baseA, useVaList, gpsFresh));
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.supportsBearing"));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runMeetsCriteriaConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_MEETS_CRITERIA_JSON);
        assertTrue(config.isAndroidLocationProvidersConfigured());
        assertTrue(config.getAndroidLocationProvidersConfig().isGps());
        assertFalse(config.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(config.isAndroidLocationProviderCapabilitiesConfigured());
        assertTrue(config.getAndroidLocationProviderCapability("gps").isMeetsCriteriaConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isMeetsCriteria());
        assertTrue(config.getAndroidLocationProviderCapability("network").isMeetsCriteriaConfigured());
        assertTrue(config.getAndroidLocationProviderCapability("network").isMeetsCriteria());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isRequiresNetworkConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isSupportsBearingConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isAccuracyConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isPowerRequirementConfigured());

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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gps = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            DvmObject<?> network = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "network");
            DvmObject<?> passive = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "passive");
            assertConfiguredLocationProvider(gps);
            assertConfiguredLocationProvider(network);
            assertConfiguredLocationProvider(passive);

            DvmObject<?> criteria = newLocationCriteria(vm);
            assertEquals(CRITERIA_CLASS, criteria.getObjectType().getClassName());
            assertFalse(invokeLocationProviderMeetsCriteria(jni, baseVM, useVaList, gps, criteria));
            assertTrue(invokeLocationProviderMeetsCriteria(jni, baseVM, useVaList, network,
                    criteria));

            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps"));
            assertFalse(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "network"));
            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "passive"));

            try {
                invokeLocationProviderRequiresNetwork(jni, baseVM, useVaList, gps);
                fail("expected UOE for requiresNetwork when only meetsCriteria is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("requiresNetwork"));
            }
            try {
                invokeLocationProviderSupportsBearing(jni, baseVM, useVaList, gps);
                fail("expected UOE for supportsBearing when only meetsCriteria is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("supportsBearing"));
            }
            try {
                invokeLocationProviderGetAccuracy(jni, baseVM, useVaList, gps);
                fail("expected UOE for getAccuracy when only meetsCriteria is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAccuracy"));
            }
            try {
                invokeLocationProviderGetPowerRequirement(jni, baseVM, useVaList, gps);
                fail("expected UOE for getPowerRequirement when only meetsCriteria is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPowerRequirement"));
            }

            int beforeArgs = sink.events.size();
            try {
                invokeLocationProviderMeetsCriteria(jni, baseVM, useVaList, gps, null);
                fail("expected UOE for null Criteria");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("meetsCriteria"));
            }
            try {
                invokeLocationProviderMeetsCriteria(jni, baseVM, useVaList, gps,
                        vm.resolveClass(LOCATION_CLASS).newObject(null));
                fail("expected UOE for non-Criteria Location object");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("meetsCriteria"));
            }
            try {
                invokeLocationProviderMeetsCriteria(jni, baseVM, useVaList, gps,
                        new StringObject(baseVM, "criteria"));
                fail("expected UOE for non-Criteria String object");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("meetsCriteria"));
            }
            assertEquals(beforeArgs, sink.events.size());

            int beforePassive = sink.events.size();
            try {
                invokeLocationProviderMeetsCriteria(jni, baseVM, useVaList, passive, criteria);
                fail("expected UOE for meetsCriteria without passive capability entry");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("meetsCriteria"));
            }
            assertEquals(beforePassive, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.meetsCriteria"));
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationManager.getProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMeetsCriteriaAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runMeetsCriteriaUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_FULL_JSON, "gps",
                "missing providerCapabilities node");
        runMeetsCriteriaUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_CAPABILITIES_EMPTY_JSON,
                "gps", "empty providerCapabilities");
        runMeetsCriteriaUoeAfterGetProvider(is64Bit, useVaList,
                PROVIDERS_CAPABILITIES_GPS_EMPTY_ENTRY_JSON, "gps",
                "gps entry missing meetsCriteria");
        runMeetsCriteriaUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_NETWORK_JSON,
                "gps", "gps entry has requiresNetwork but not meetsCriteria");
        runMeetsCriteriaUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_SATELLITE_JSON,
                "gps", "gps entry has requiresSatellite but not meetsCriteria");
        runMeetsCriteriaUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_CELL_JSON,
                "gps", "gps entry has requiresCell but not meetsCriteria");
        runMeetsCriteriaUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_HAS_MONETARY_COST_JSON,
                "gps", "gps entry has hasMonetaryCost but not meetsCriteria");
        runMeetsCriteriaUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_ALTITUDE_JSON,
                "gps", "gps entry has supportsAltitude but not meetsCriteria");
        runMeetsCriteriaUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_SPEED_JSON,
                "gps", "gps entry has supportsSpeed but not meetsCriteria");
        runMeetsCriteriaUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_BEARING_JSON,
                "gps", "gps entry has supportsBearing but not meetsCriteria");
        runMeetsCriteriaUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_ACCURACY_JSON,
                "gps", "gps entry has accuracy but not meetsCriteria");
        runMeetsCriteriaUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_POWER_REQUIREMENT_JSON,
                "gps", "gps entry has powerRequirement but not meetsCriteria");
        runMeetsCriteriaUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_MEETS_CRITERIA_JSON,
                "passive", "passive capability entry omitted");

        TraceEnvironmentConfig capsOnly = TraceEnvironmentConfig.parse(
                CAPABILITIES_WITHOUT_PROVIDERS_JSON);
        AndroidEmulator capsOnlyEmu = null;
        CapturingSink capsOnlySink = new CapturingSink();
        try {
            capsOnlyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                    : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(capsOnly)
                    .build();
            TraceEnvironmentEventSink.register(capsOnlyEmu, capsOnlySink);
            VM vm = capsOnlyEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager, "gps");
                fail("expected UOE for getProvider without providers (capabilities-only)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }
            assertNoAndroidLocationEvents(capsOnlySink);
        } finally {
            if (capsOnlyEmu != null) {
                TraceEnvironmentEventSink.unregister(capsOnlyEmu, capsOnlySink);
                capsOnlyEmu.close();
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_MEETS_CRITERIA_JSON);
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

            DvmObject<?> plainProvider = vm.resolveClass(LOCATION_PROVIDER_CLASS).newObject(null);
            try {
                invokeLocationProviderMeetsCriteria(jni, baseVM, useVaList, plainProvider,
                        newLocationCriteria(vm));
                fail("expected UOE for meetsCriteria on plain LocationProvider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("meetsCriteria"));
            }
            assertNoAndroidLocationEvents(sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMeetsCriteriaUoeAfterGetProvider(boolean is64Bit, boolean useVaList,
                                                            String json, String provider,
                                                            String reason) throws Exception {
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
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> marker = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    provider);
            assertConfiguredLocationProvider(marker);
            int before = sink.events.size();
            try {
                invokeLocationProviderMeetsCriteria(jni, baseVM, useVaList, marker,
                        newLocationCriteria(vm));
                fail("expected UOE for meetsCriteria: " + reason);
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("meetsCriteria"));
            }
            assertEquals(before, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.meetsCriteria"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runMeetsCriteriaForeignStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_MEETS_CRITERIA_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> gpsA = invokeLocationManagerGetProvider(jniA, baseA, useVaList, managerA,
                    "gps");
            assertConfiguredLocationProvider(gpsA);
            assertFalse(invokeLocationProviderMeetsCriteria(jniA, baseA, useVaList, gpsA,
                    newLocationCriteria(vmA)));

            int beforeForeignCriteriaA = sinkA.events.size();
            int beforeForeignCriteriaB = sinkB.events.size();
            try {
                invokeLocationProviderMeetsCriteria(jniA, baseA, useVaList, gpsA,
                        newLocationCriteria(vmB));
                fail("expected UOE for foreign-VM Criteria on LocationProvider.meetsCriteria");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("meetsCriteria"));
            }
            assertEquals(beforeForeignCriteriaA, sinkA.events.size());
            assertEquals(beforeForeignCriteriaB, sinkB.events.size());
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.meetsCriteria"));
            assertEquals(0, countEvents(sinkB.events, "android_location",
                    "LocationProvider.meetsCriteria"));
            assertNoAndroidLocationEvents(sinkB);

            try {
                invokeLocationProviderMeetsCriteria(jniB, baseB, useVaList, gpsA,
                        newLocationCriteria(vmB));
                fail("expected UOE for cross-VM LocationProvider.meetsCriteria");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("meetsCriteria"));
            }
            assertNoAndroidLocationEvents(sinkB);

            emulatorA.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(PROVIDERS_MEETS_CRITERIA_JSON));
            try {
                invokeLocationProviderMeetsCriteria(jniA, baseA, useVaList, gpsA,
                        newLocationCriteria(vmA));
                fail("expected UOE for stale LocationProvider.meetsCriteria");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("meetsCriteria"));
            }
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.meetsCriteria"));

            DvmObject<?> gpsFresh = invokeLocationManagerGetProvider(jniA, baseA, useVaList,
                    managerA, "gps");
            assertConfiguredLocationProvider(gpsFresh);
            assertTrue(gpsA != gpsFresh);
            assertFalse(invokeLocationProviderMeetsCriteria(jniA, baseA, useVaList, gpsFresh,
                    newLocationCriteria(vmA)));
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.meetsCriteria"));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runAccuracyConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                PROVIDERS_ACCURACY_WITH_LAST_KNOWN_JSON);
        assertTrue(config.isAndroidLocationProvidersConfigured());
        assertTrue(config.getAndroidLocationProvidersConfig().isGps());
        assertFalse(config.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(config.isAndroidLocationProviderCapabilitiesConfigured());
        assertTrue(config.getAndroidLocationProviderCapability("gps").isAccuracyConfigured());
        assertEquals(1, config.getAndroidLocationProviderCapability("gps").getAccuracy());
        assertTrue(config.getAndroidLocationProviderCapability("network").isAccuracyConfigured());
        assertEquals(2, config.getAndroidLocationProviderCapability("network").getAccuracy());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isRequiresNetworkConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isSupportsBearingConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isMeetsCriteriaConfigured());
        assertTrue(config.isAndroidLocationLastKnownLocationsConfigured());

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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gps = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            DvmObject<?> network = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "network");
            DvmObject<?> passive = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "passive");
            assertConfiguredLocationProvider(gps);
            assertConfiguredLocationProvider(network);
            assertConfiguredLocationProvider(passive);

            assertEquals(1, invokeLocationProviderGetAccuracy(jni, baseVM, useVaList, gps));
            assertEquals(2, invokeLocationProviderGetAccuracy(jni, baseVM, useVaList, network));

            DvmObject<?> gpsLoc = invokeGetLastKnownLocation(jni, baseVM, useVaList, manager, "gps");
            assertNotNull(gpsLoc);
            assertEquals(8.25f, invokeGetAccuracy(jni, baseVM, gpsLoc), 0.0f);

            int beforePower = sink.events.size();
            try {
                invokeLocationProviderGetPowerRequirement(jni, baseVM, useVaList, gps);
                fail("expected UOE for getPowerRequirement when only accuracy is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPowerRequirement"));
            }
            assertEquals(beforePower, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.getPowerRequirement"));

            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps"));
            assertFalse(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "network"));
            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "passive"));

            int beforePassive = sink.events.size();
            try {
                invokeLocationProviderGetAccuracy(jni, baseVM, useVaList, passive);
                fail("expected UOE for getAccuracy without passive capability entry");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAccuracy"));
            }
            assertEquals(beforePassive, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.getAccuracy"));
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationManager.getProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAccuracyAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runAccuracyUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_FULL_JSON, "gps",
                "missing providerCapabilities node");
        runAccuracyUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_CAPABILITIES_EMPTY_JSON,
                "gps", "empty providerCapabilities");
        runAccuracyUoeAfterGetProvider(is64Bit, useVaList,
                PROVIDERS_CAPABILITIES_GPS_EMPTY_ENTRY_JSON, "gps",
                "gps entry missing accuracy");
        runAccuracyUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_NETWORK_JSON,
                "gps", "gps entry has requiresNetwork but not accuracy");
        runAccuracyUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_SATELLITE_JSON,
                "gps", "gps entry has requiresSatellite but not accuracy");
        runAccuracyUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_CELL_JSON,
                "gps", "gps entry has requiresCell but not accuracy");
        runAccuracyUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_HAS_MONETARY_COST_JSON,
                "gps", "gps entry has hasMonetaryCost but not accuracy");
        runAccuracyUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_ALTITUDE_JSON,
                "gps", "gps entry has supportsAltitude but not accuracy");
        runAccuracyUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_SPEED_JSON,
                "gps", "gps entry has supportsSpeed but not accuracy");
        runAccuracyUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_BEARING_JSON,
                "gps", "gps entry has supportsBearing but not accuracy");
        runAccuracyUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_POWER_REQUIREMENT_JSON,
                "gps", "gps entry has powerRequirement but not accuracy");
        runAccuracyUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_ACCURACY_JSON,
                "passive", "passive capability entry omitted");

        TraceEnvironmentConfig capsOnly = TraceEnvironmentConfig.parse(
                CAPABILITIES_WITHOUT_PROVIDERS_JSON);
        AndroidEmulator capsOnlyEmu = null;
        CapturingSink capsOnlySink = new CapturingSink();
        try {
            capsOnlyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                    : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(capsOnly)
                    .build();
            TraceEnvironmentEventSink.register(capsOnlyEmu, capsOnlySink);
            VM vm = capsOnlyEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager, "gps");
                fail("expected UOE for getProvider without providers (capabilities-only)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }
            assertNoAndroidLocationEvents(capsOnlySink);
        } finally {
            if (capsOnlyEmu != null) {
                TraceEnvironmentEventSink.unregister(capsOnlyEmu, capsOnlySink);
                capsOnlyEmu.close();
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_ACCURACY_JSON);
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

            DvmObject<?> plainProvider = vm.resolveClass(LOCATION_PROVIDER_CLASS).newObject(null);
            try {
                invokeLocationProviderGetAccuracy(jni, baseVM, useVaList, plainProvider);
                fail("expected UOE for getAccuracy on plain LocationProvider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAccuracy"));
            }
            assertNoAndroidLocationEvents(sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAccuracyUoeAfterGetProvider(boolean is64Bit, boolean useVaList,
                                                       String json, String provider,
                                                       String reason) throws Exception {
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
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> marker = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    provider);
            assertConfiguredLocationProvider(marker);
            int before = sink.events.size();
            try {
                invokeLocationProviderGetAccuracy(jni, baseVM, useVaList, marker);
                fail("expected UOE for getAccuracy: " + reason);
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAccuracy"));
            }
            assertEquals(before, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.getAccuracy"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runAccuracyForeignStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_ACCURACY_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> gpsA = invokeLocationManagerGetProvider(jniA, baseA, useVaList, managerA,
                    "gps");
            assertConfiguredLocationProvider(gpsA);
            assertEquals(1, invokeLocationProviderGetAccuracy(jniA, baseA, useVaList, gpsA));

            try {
                invokeLocationProviderGetAccuracy(jniB, baseB, useVaList, gpsA);
                fail("expected UOE for cross-VM LocationProvider.getAccuracy");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAccuracy"));
            }
            assertNoAndroidLocationEvents(sinkB);

            emulatorA.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(PROVIDERS_ACCURACY_JSON));
            try {
                invokeLocationProviderGetAccuracy(jniA, baseA, useVaList, gpsA);
                fail("expected UOE for stale LocationProvider.getAccuracy");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAccuracy"));
            }
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.getAccuracy"));

            DvmObject<?> gpsFresh = invokeLocationManagerGetProvider(jniA, baseA, useVaList,
                    managerA, "gps");
            assertConfiguredLocationProvider(gpsFresh);
            assertTrue(gpsA != gpsFresh);
            assertEquals(1, invokeLocationProviderGetAccuracy(jniA, baseA, useVaList, gpsFresh));
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.getAccuracy"));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runPowerRequirementConfigured(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(
                PROVIDERS_POWER_REQUIREMENT_JSON);
        assertTrue(config.isAndroidLocationProvidersConfigured());
        assertTrue(config.getAndroidLocationProvidersConfig().isGps());
        assertFalse(config.getAndroidLocationProvidersConfig().isNetwork());
        assertTrue(config.isAndroidLocationProviderCapabilitiesConfigured());
        assertTrue(config.getAndroidLocationProviderCapability("gps").isPowerRequirementConfigured());
        assertEquals(1, config.getAndroidLocationProviderCapability("gps").getPowerRequirement());
        assertTrue(config.getAndroidLocationProviderCapability("network").isPowerRequirementConfigured());
        assertEquals(3, config.getAndroidLocationProviderCapability("network").getPowerRequirement());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isAccuracyConfigured());
        assertEquals(0, config.getAndroidLocationProviderCapability("gps").getAccuracy());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isRequiresNetworkConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isSupportsBearingConfigured());
        assertFalse(config.getAndroidLocationProviderCapability("gps").isMeetsCriteriaConfigured());

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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> gps = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "gps");
            DvmObject<?> network = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "network");
            DvmObject<?> passive = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    "passive");
            assertConfiguredLocationProvider(gps);
            assertConfiguredLocationProvider(network);
            assertConfiguredLocationProvider(passive);

            assertEquals(1, invokeLocationProviderGetPowerRequirement(jni, baseVM, useVaList, gps));
            assertEquals(3, invokeLocationProviderGetPowerRequirement(jni, baseVM, useVaList,
                    network));

            int beforeAccuracy = sink.events.size();
            try {
                invokeLocationProviderGetAccuracy(jni, baseVM, useVaList, gps);
                fail("expected UOE for getAccuracy when only powerRequirement is configured");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getAccuracy"));
            }
            assertEquals(beforeAccuracy, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.getAccuracy"));

            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "gps"));
            assertFalse(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "network"));
            assertTrue(invokeIsProviderEnabled(jni, baseVM, useVaList, manager, "passive"));

            int beforePassive = sink.events.size();
            try {
                invokeLocationProviderGetPowerRequirement(jni, baseVM, useVaList, passive);
                fail("expected UOE for getPowerRequirement without passive capability entry");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPowerRequirement"));
            }
            assertEquals(beforePassive, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.getPowerRequirement"));
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationManager.getProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runPowerRequirementAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        runPowerRequirementUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_FULL_JSON, "gps",
                "missing providerCapabilities node");
        runPowerRequirementUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_CAPABILITIES_EMPTY_JSON,
                "gps", "empty providerCapabilities");
        runPowerRequirementUoeAfterGetProvider(is64Bit, useVaList,
                PROVIDERS_CAPABILITIES_GPS_EMPTY_ENTRY_JSON, "gps",
                "gps entry missing powerRequirement");
        runPowerRequirementUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_NETWORK_JSON,
                "gps", "gps entry has requiresNetwork but not powerRequirement");
        runPowerRequirementUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_SATELLITE_JSON,
                "gps", "gps entry has requiresSatellite but not powerRequirement");
        runPowerRequirementUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_REQUIRES_CELL_JSON,
                "gps", "gps entry has requiresCell but not powerRequirement");
        runPowerRequirementUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_HAS_MONETARY_COST_JSON,
                "gps", "gps entry has hasMonetaryCost but not powerRequirement");
        runPowerRequirementUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_ALTITUDE_JSON,
                "gps", "gps entry has supportsAltitude but not powerRequirement");
        runPowerRequirementUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_SPEED_JSON,
                "gps", "gps entry has supportsSpeed but not powerRequirement");
        runPowerRequirementUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_SUPPORTS_BEARING_JSON,
                "gps", "gps entry has supportsBearing but not powerRequirement");
        runPowerRequirementUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_ACCURACY_JSON,
                "gps", "gps entry has accuracy but not powerRequirement");
        runPowerRequirementUoeAfterGetProvider(is64Bit, useVaList, PROVIDERS_POWER_REQUIREMENT_JSON,
                "passive", "passive capability entry omitted");

        TraceEnvironmentConfig capsOnly = TraceEnvironmentConfig.parse(
                CAPABILITIES_WITHOUT_PROVIDERS_JSON);
        AndroidEmulator capsOnlyEmu = null;
        CapturingSink capsOnlySink = new CapturingSink();
        try {
            capsOnlyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                    : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(capsOnly)
                    .build();
            TraceEnvironmentEventSink.register(capsOnlyEmu, capsOnlySink);
            VM vm = capsOnlyEmu.createDalvikVM();
            AbstractJni jni = new AbstractJni() {
            };
            vm.setJni(jni);
            BaseVM baseVM = (BaseVM) vm;
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager, "gps");
                fail("expected UOE for getProvider without providers (capabilities-only)");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getProvider"));
            }
            assertNoAndroidLocationEvents(capsOnlySink);
        } finally {
            if (capsOnlyEmu != null) {
                TraceEnvironmentEventSink.unregister(capsOnlyEmu, capsOnlySink);
                capsOnlyEmu.close();
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_POWER_REQUIREMENT_JSON);
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

            DvmObject<?> plainProvider = vm.resolveClass(LOCATION_PROVIDER_CLASS).newObject(null);
            try {
                invokeLocationProviderGetPowerRequirement(jni, baseVM, useVaList, plainProvider);
                fail("expected UOE for getPowerRequirement on plain LocationProvider");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPowerRequirement"));
            }
            assertNoAndroidLocationEvents(sink);
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runPowerRequirementUoeAfterGetProvider(boolean is64Bit, boolean useVaList,
                                                               String json, String provider,
                                                               String reason) throws Exception {
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
            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            DvmObject<?> marker = invokeLocationManagerGetProvider(jni, baseVM, useVaList, manager,
                    provider);
            assertConfiguredLocationProvider(marker);
            int before = sink.events.size();
            try {
                invokeLocationProviderGetPowerRequirement(jni, baseVM, useVaList, marker);
                fail("expected UOE for getPowerRequirement: " + reason);
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPowerRequirement"));
            }
            assertEquals(before, sink.events.size());
            assertEquals(0, countEvents(sink.events, "android_location",
                    "LocationProvider.getPowerRequirement"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runPowerRequirementForeignStale(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_POWER_REQUIREMENT_JSON);
        AndroidEmulator emulatorA = null;
        AndroidEmulator emulatorB = null;
        CapturingSink sinkA = new CapturingSink();
        CapturingSink sinkB = new CapturingSink();
        try {
            emulatorA = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            emulatorB = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(config)
                    .build();
            TraceEnvironmentEventSink.register(emulatorA, sinkA);
            TraceEnvironmentEventSink.register(emulatorB, sinkB);
            VM vmA = emulatorA.createDalvikVM();
            VM vmB = emulatorB.createDalvikVM();
            AbstractJni jniA = new AbstractJni() {
            };
            AbstractJni jniB = new AbstractJni() {
            };
            vmA.setJni(jniA);
            vmB.setJni(jniB);
            BaseVM baseA = (BaseVM) vmA;
            BaseVM baseB = (BaseVM) vmB;

            DvmObject<?> managerA = resolveLocationSystemService(jniA, baseA, useVaList, vmA);
            DvmObject<?> gpsA = invokeLocationManagerGetProvider(jniA, baseA, useVaList, managerA,
                    "gps");
            assertConfiguredLocationProvider(gpsA);
            assertEquals(1, invokeLocationProviderGetPowerRequirement(jniA, baseA, useVaList, gpsA));

            try {
                invokeLocationProviderGetPowerRequirement(jniB, baseB, useVaList, gpsA);
                fail("expected UOE for cross-VM LocationProvider.getPowerRequirement");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPowerRequirement"));
            }
            assertNoAndroidLocationEvents(sinkB);

            emulatorA.set(TraceEnvironmentConfig.KEY,
                    TraceEnvironmentConfig.parse(PROVIDERS_POWER_REQUIREMENT_JSON));
            try {
                invokeLocationProviderGetPowerRequirement(jniA, baseA, useVaList, gpsA);
                fail("expected UOE for stale LocationProvider.getPowerRequirement");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("getPowerRequirement"));
            }
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.getPowerRequirement"));

            DvmObject<?> gpsFresh = invokeLocationManagerGetProvider(jniA, baseA, useVaList,
                    managerA, "gps");
            assertConfiguredLocationProvider(gpsFresh);
            assertTrue(gpsA != gpsFresh);
            assertEquals(1, invokeLocationProviderGetPowerRequirement(jniA, baseA, useVaList,
                    gpsFresh));
            assertEquals(0, countEvents(sinkA.events, "android_location",
                    "LocationProvider.getPowerRequirement"));
        } finally {
            if (emulatorA != null) {
                TraceEnvironmentEventSink.unregister(emulatorA, sinkA);
                emulatorA.close();
            }
            if (emulatorB != null) {
                TraceEnvironmentEventSink.unregister(emulatorB, sinkB);
                emulatorB.close();
            }
        }
    }

    private static void runHasProviderExplicitIncludingDisabled(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_FULL_JSON);
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            assertTrue(invokeHasProvider(jni, baseVM, useVaList, manager, "gps"));
            assertTrue(invokeHasProvider(jni, baseVM, useVaList, manager, "network"));
            assertTrue(invokeHasProvider(jni, baseVM, useVaList, manager, "passive"));

            assertHasProviderEvent(sink, "gps", true);
            assertHasProviderEvent(sink, "network", true);
            assertHasProviderEvent(sink, "passive", true);
            assertEquals(3, countEvents(sink.events, "android_location",
                    "LocationManager.hasProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static void runHasProviderOmittedUnknownFalse(boolean is64Bit, boolean useVaList)
            throws Exception {
        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_PARTIAL_ORDER_JSON);
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

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            assertTrue(invokeHasProvider(jni, baseVM, useVaList, manager, "gps"));
            assertFalse(invokeHasProvider(jni, baseVM, useVaList, manager, "network"));
            assertTrue(invokeHasProvider(jni, baseVM, useVaList, manager, "passive"));
            assertFalse(invokeHasProvider(jni, baseVM, useVaList, manager, "fused"));

            assertHasProviderEvent(sink, "gps", true);
            assertHasProviderEvent(sink, "network", false);
            assertHasProviderEvent(sink, "passive", true);
            assertHasProviderEvent(sink, "fused", false);
            assertEquals(4, countEvents(sink.events, "android_location",
                    "LocationManager.hasProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }

        TraceEnvironmentConfig empty = TraceEnvironmentConfig.parse(PROVIDERS_EMPTY_JSON);
        AndroidEmulator emptyEmu = null;
        CapturingSink emptySink = new CapturingSink();
        try {
            emptyEmu = (is64Bit ? AndroidEmulatorBuilder.for64Bit() : AndroidEmulatorBuilder.for32Bit())
                    .setEnvironmentConfig(empty)
                    .build();
            TraceEnvironmentEventSink.register(emptyEmu, emptySink);
            VM emptyVm = emptyEmu.createDalvikVM();
            AbstractJni emptyJni = new AbstractJni() {
            };
            emptyVm.setJni(emptyJni);
            BaseVM emptyBase = (BaseVM) emptyVm;
            DvmObject<?> manager = resolveLocationSystemService(emptyJni, emptyBase, useVaList,
                    emptyVm);
            for (String name : new String[] {"gps", "network", "passive", "fused"}) {
                assertFalse(invokeHasProvider(emptyJni, emptyBase, useVaList, manager, name));
                assertHasProviderEvent(emptySink, name, false);
            }
            assertEquals(4, countEvents(emptySink.events, "android_location",
                    "LocationManager.hasProvider"));
        } finally {
            if (emptyEmu != null) {
                TraceEnvironmentEventSink.unregister(emptyEmu, emptySink);
                emptyEmu.close();
            }
        }
    }

    private static void runHasProviderAbsentPlainIsolation(boolean is64Bit, boolean useVaList)
            throws Exception {
        for (String json : new String[] {NO_LOCATION_JSON, LOCATION_WITHOUT_PROVIDERS_JSON,
                LAST_KNOWN_FULL_JSON}) {
            TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(json);
            AndroidEmulator emulator = null;
            CapturingSink sink = new CapturingSink();
            try {
                emulator = (is64Bit ? AndroidEmulatorBuilder.for64Bit()
                        : AndroidEmulatorBuilder.for32Bit())
                        .setEnvironmentConfig(config)
                        .build();
                TraceEnvironmentEventSink.register(emulator, sink);
                VM vm = emulator.createDalvikVM();
                AbstractJni jni = new AbstractJni() {
                };
                vm.setJni(jni);
                BaseVM baseVM = (BaseVM) vm;
                DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
                try {
                    invokeHasProvider(jni, baseVM, useVaList, manager, "gps");
                    fail("expected UOE for hasProvider without providers: " + json);
                } catch (UnsupportedOperationException expected) {
                    assertTrue(expected.getMessage() != null
                            && expected.getMessage().contains("hasProvider"));
                }
                for (CapturedEvent e : sink.events) {
                    assertFalse("unexpected android_location hasProvider event: " + e.api,
                            "android_location".equals(e.kind)
                                    && "LocationManager.hasProvider".equals(e.api));
                }
            } finally {
                if (emulator != null) {
                    TraceEnvironmentEventSink.unregister(emulator, sink);
                    emulator.close();
                }
            }
        }

        TraceEnvironmentConfig config = TraceEnvironmentConfig.parse(PROVIDERS_FULL_JSON);
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

            DvmObject<?> plain = vm.resolveClass(LOCATION_MANAGER_CLASS).newObject(null);
            try {
                invokeHasProvider(jni, baseVM, useVaList, plain, "gps");
                fail("expected UOE for hasProvider on plain LocationManager");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasProvider"));
            }

            DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);
            DvmObject<?> wifi = invokeGetSystemService(jni, baseVM, useVaList, app, "wifi");
            try {
                invokeHasProvider(jni, baseVM, useVaList, wifi, "gps");
                fail("expected UOE for hasProvider on non-location SystemService");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasProvider"));
            }

            DvmObject<?> manager = resolveLocationSystemService(jni, baseVM, useVaList, vm);
            try {
                invokeHasProviderNull(jni, baseVM, useVaList, manager);
                fail("expected UOE for null hasProvider argument");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasProvider"));
            }
            try {
                invokeHasProviderNonString(jni, baseVM, useVaList, manager);
                fail("expected UOE for non-String hasProvider argument");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasProvider"));
            }

            try {
                DvmClass dvmClass = manager.getObjectType();
                DvmMethod method = new DvmMethod(dvmClass, "hasProvider", "()Z", false);
                String signature = method.getSignature();
                if (useVaList) {
                    jni.callBooleanMethodV(baseVM, manager, signature,
                            new TestNoArgVaList(baseVM, method));
                } else {
                    jni.callBooleanMethod(baseVM, manager, signature,
                            new TestNoArgVarArg(baseVM, method));
                }
                fail("expected UOE for hasProvider()Z");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage() != null
                        && expected.getMessage().contains("hasProvider"));
            }

            for (CapturedEvent e : sink.events) {
                assertFalse("unexpected android_location event on isolation: " + e.api,
                        "android_location".equals(e.kind));
            }

            assertTrue(invokeHasProvider(jni, baseVM, useVaList, manager, "gps"));
            assertHasProviderEvent(sink, "gps", true);
            assertEquals(1, countEvents(sink.events, "android_location",
                    "LocationManager.hasProvider"));
        } finally {
            if (emulator != null) {
                TraceEnvironmentEventSink.unregister(emulator, sink);
                emulator.close();
            }
        }
    }

    private static DvmObject<?> invokeGetProviders(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                   DvmObject<?> target, boolean enabledOnly) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getProviders", "(Z)Ljava/util/List;", false);
        String signature = method.getSignature();
        int z = enabledOnly ? 1 : 0;
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature, new TestIntVaList(vm, method, z));
        }
        return jni.callObjectMethod(vm, target, signature, new TestIntVarArg(vm, method, z));
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

    private static DvmObject<?> invokeGetLastKnownLocation(AbstractJni jni, BaseVM vm,
                                                           boolean useVaList, DvmObject<?> target,
                                                           String provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getLastKnownLocation",
                "(Ljava/lang/String;)Landroid/location/Location;", false);
        String signature = method.getSignature();
        int nameHash = vm.addLocalObject(new StringObject(vm, provider));
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature,
                    new TestObjectVaList(vm, method, nameHash));
        }
        return jni.callObjectMethod(vm, target, signature,
                new TestObjectVarArg(vm, method, nameHash));
    }

    private static DvmObject<?> invokeLocationManagerGetProvider(AbstractJni jni, BaseVM vm,
                                                                 boolean useVaList,
                                                                 DvmObject<?> target,
                                                                 String provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getProvider",
                "(Ljava/lang/String;)Landroid/location/LocationProvider;", false);
        String signature = method.getSignature();
        int nameHash = vm.addLocalObject(new StringObject(vm, provider));
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature,
                    new TestObjectVaList(vm, method, nameHash));
        }
        return jni.callObjectMethod(vm, target, signature,
                new TestObjectVarArg(vm, method, nameHash));
    }

    private static DvmObject<?> invokeLocationManagerGetProviderNull(AbstractJni jni, BaseVM vm,
                                                                     boolean useVaList,
                                                                     DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getProvider",
                "(Ljava/lang/String;)Landroid/location/LocationProvider;", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature,
                    new TestObjectVaList(vm, method, 0));
        }
        return jni.callObjectMethod(vm, target, signature,
                new TestObjectVarArg(vm, method, 0));
    }

    private static DvmObject<?> invokeLocationManagerGetProviderNonString(AbstractJni jni, BaseVM vm,
                                                                          boolean useVaList,
                                                                          DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getProvider",
                "(Ljava/lang/String;)Landroid/location/LocationProvider;", false);
        String signature = method.getSignature();
        int hash = vm.addLocalObject(DvmInteger.valueOf(vm, 1));
        if (useVaList) {
            return jni.callObjectMethodV(vm, target, signature,
                    new TestObjectVaList(vm, method, hash));
        }
        return jni.callObjectMethod(vm, target, signature,
                new TestObjectVarArg(vm, method, hash));
    }

    private static String invokeLocationProviderGetName(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                        DvmObject<?> provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_PROVIDER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getName", "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, provider, signature,
                    new TestNoArgVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, provider, signature,
                    new TestNoArgVarArg(vm, method));
        }
        assertNotNull(result);
        return String.valueOf(result.getValue());
    }

    private static boolean invokeLocationProviderRequiresNetwork(AbstractJni jni, BaseVM vm,
                                                                 boolean useVaList,
                                                                 DvmObject<?> provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_PROVIDER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "requiresNetwork", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, provider, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, provider, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeLocationProviderRequiresSatellite(AbstractJni jni, BaseVM vm,
                                                                   boolean useVaList,
                                                                   DvmObject<?> provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_PROVIDER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "requiresSatellite", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, provider, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, provider, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeLocationProviderRequiresCell(AbstractJni jni, BaseVM vm,
                                                              boolean useVaList,
                                                              DvmObject<?> provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_PROVIDER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "requiresCell", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, provider, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, provider, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeLocationProviderHasMonetaryCost(AbstractJni jni, BaseVM vm,
                                                                 boolean useVaList,
                                                                 DvmObject<?> provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_PROVIDER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasMonetaryCost", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, provider, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, provider, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeLocationProviderSupportsAltitude(AbstractJni jni, BaseVM vm,
                                                                  boolean useVaList,
                                                                  DvmObject<?> provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_PROVIDER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "supportsAltitude", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, provider, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, provider, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeLocationProviderSupportsSpeed(AbstractJni jni, BaseVM vm,
                                                               boolean useVaList,
                                                               DvmObject<?> provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_PROVIDER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "supportsSpeed", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, provider, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, provider, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeLocationProviderSupportsBearing(AbstractJni jni, BaseVM vm,
                                                                 boolean useVaList,
                                                                 DvmObject<?> provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_PROVIDER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "supportsBearing", "()Z", false);
        String signature = method.getSignature();
        assertEquals("android/location/LocationProvider->supportsBearing()Z", signature);
        if (useVaList) {
            return jni.callBooleanMethodV(vm, provider, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, provider, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static DvmObject<?> newLocationCriteria(VM vm) {
        return vm.resolveClass(CRITERIA_CLASS).newObject(null);
    }

    private static boolean invokeLocationProviderMeetsCriteria(AbstractJni jni, BaseVM vm,
                                                               boolean useVaList,
                                                               DvmObject<?> provider,
                                                               DvmObject<?> criteria) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_PROVIDER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "meetsCriteria",
                "(Landroid/location/Criteria;)Z", false);
        String signature = method.getSignature();
        assertEquals("android/location/LocationProvider->meetsCriteria(Landroid/location/Criteria;)Z",
                signature);
        int hash = criteria == null ? 0 : vm.addLocalObject(criteria);
        if (useVaList) {
            return jni.callBooleanMethodV(vm, provider, signature,
                    new TestObjectVaList(vm, method, hash));
        }
        return jni.callBooleanMethod(vm, provider, signature,
                new TestObjectVarArg(vm, method, hash));
    }

    private static int invokeLocationProviderGetAccuracy(AbstractJni jni, BaseVM vm,
                                                         boolean useVaList,
                                                         DvmObject<?> provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_PROVIDER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getAccuracy", "()I", false);
        String signature = method.getSignature();
        assertEquals("android/location/LocationProvider->getAccuracy()I", signature);
        if (useVaList) {
            return jni.callIntMethodV(vm, provider, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, provider, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static int invokeLocationProviderGetPowerRequirement(AbstractJni jni, BaseVM vm,
                                                                 boolean useVaList,
                                                                 DvmObject<?> provider) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_PROVIDER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getPowerRequirement", "()I", false);
        String signature = method.getSignature();
        assertEquals("android/location/LocationProvider->getPowerRequirement()I", signature);
        if (useVaList) {
            return jni.callIntMethodV(vm, provider, signature,
                    new TestNoArgVaList(vm, method));
        }
        return jni.callIntMethod(vm, provider, signature,
                new TestNoArgVarArg(vm, method));
    }

    private static void assertConfiguredLocationProvider(DvmObject<?> obj) {
        assertNotNull(obj);
        assertTrue(String.valueOf(obj.getValue().getClass().getName())
                .contains("ConfiguredLocationProvider"));
    }

    private static void assertGetProviderEvent(CapturingSink sink, String provider, String result) {
        CapturedEvent found = null;
        for (CapturedEvent e : sink.events) {
            if ("android_location".equals(e.kind)
                    && "LocationManager.getProvider".equals(e.api)
                    && String.valueOf(e.value).startsWith("provider=" + provider + ",")) {
                found = e;
            }
        }
        assertNotNull("missing getProvider event for provider=" + provider, found);
        assertEquals("json-config", found.source);
        assertEquals("provider=" + provider + ",result=" + result, String.valueOf(found.value));
        assertNotNull(found.note);
        assertFalse(found.note.isEmpty());
    }

    private static void assertHasProviderEvent(CapturingSink sink, String provider, boolean expected) {
        CapturedEvent found = null;
        for (CapturedEvent e : sink.events) {
            if ("android_location".equals(e.kind)
                    && "LocationManager.hasProvider".equals(e.api)
                    && String.valueOf(e.value).startsWith("provider=" + provider + ",")) {
                found = e;
            }
        }
        assertNotNull("missing hasProvider event for provider=" + provider, found);
        assertEquals("json-config", found.source);
        assertEquals("provider=" + provider + ",result=" + expected, String.valueOf(found.value));
        assertNotNull(found.note);
        assertFalse(found.note.isEmpty());
    }

    private static void assertGetNameEvent(CapturingSink sink, String provider) {
        CapturedEvent found = null;
        for (CapturedEvent e : sink.events) {
            if ("android_location".equals(e.kind)
                    && "LocationProvider.getName".equals(e.api)
                    && String.valueOf(e.value).startsWith("provider=" + provider + ",")) {
                found = e;
            }
        }
        assertNotNull("missing getName event for provider=" + provider, found);
        assertEquals("json-config", found.source);
        assertEquals("provider=" + provider + ",result=" + provider, String.valueOf(found.value));
        assertNotNull(found.note);
        assertFalse(found.note.isEmpty());
    }

    private static String invokeGetProvider(AbstractJni jni, BaseVM vm, boolean useVaList,
                                            DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getProvider", "()Ljava/lang/String;", false);
        String signature = method.getSignature();
        DvmObject<?> result;
        if (useVaList) {
            result = jni.callObjectMethodV(vm, location, signature,
                    new TestNoArgVaList(vm, method));
        } else {
            result = jni.callObjectMethod(vm, location, signature,
                    new TestNoArgVarArg(vm, method));
        }
        assertNotNull(result);
        return String.valueOf(result.getValue());
    }

    private static double invokeGetLatitude(AbstractJni jni, BaseVM vm, DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getLatitude", "()D", false);
        return jni.callDoubleMethod(vm, location, method.getSignature(),
                new TestNoArgVarArg(vm, method));
    }

    private static double invokeGetLongitude(AbstractJni jni, BaseVM vm, DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getLongitude", "()D", false);
        return jni.callDoubleMethod(vm, location, method.getSignature(),
                new TestNoArgVarArg(vm, method));
    }

    private static double invokeGetAltitude(AbstractJni jni, BaseVM vm, DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getAltitude", "()D", false);
        return jni.callDoubleMethod(vm, location, method.getSignature(),
                new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeHasAltitude(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasAltitude", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, location, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, location, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeHasAccuracy(AbstractJni jni, BaseVM vm, boolean useVaList,
                                             DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasAccuracy", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, location, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, location, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsFromMockProvider(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                    DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isFromMockProvider", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, location, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, location, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsMock(AbstractJni jni, BaseVM vm, boolean useVaList,
                                        DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isMock", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, location, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, location, signature, new TestNoArgVarArg(vm, method));
    }

    private static float invokeGetAccuracy(AbstractJni jni, BaseVM vm, DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getAccuracy", "()F", false);
        return jni.callFloatMethodV(vm, location, method.getSignature(),
                new TestNoArgVaList(vm, method));
    }

    private static boolean invokeHasSpeed(AbstractJni jni, BaseVM vm, boolean useVaList,
                                          DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasSpeed", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, location, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, location, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeHasBearing(AbstractJni jni, BaseVM vm, boolean useVaList,
                                            DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasBearing", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, location, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, location, signature, new TestNoArgVarArg(vm, method));
    }

    private static float invokeGetSpeed(AbstractJni jni, BaseVM vm, DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getSpeed", "()F", false);
        return jni.callFloatMethodV(vm, location, method.getSignature(),
                new TestNoArgVaList(vm, method));
    }

    private static float invokeGetBearing(AbstractJni jni, BaseVM vm, DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getBearing", "()F", false);
        return jni.callFloatMethodV(vm, location, method.getSignature(),
                new TestNoArgVaList(vm, method));
    }

    private static boolean invokeHasVerticalAccuracy(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                     DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasVerticalAccuracy", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, location, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, location, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeHasSpeedAccuracy(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                  DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasSpeedAccuracy", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, location, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, location, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeHasBearingAccuracy(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                    DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasBearingAccuracy", "()Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, location, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callBooleanMethod(vm, location, signature, new TestNoArgVarArg(vm, method));
    }

    private static float invokeGetVerticalAccuracyMeters(AbstractJni jni, BaseVM vm,
                                                         DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getVerticalAccuracyMeters", "()F", false);
        return jni.callFloatMethodV(vm, location, method.getSignature(),
                new TestNoArgVaList(vm, method));
    }

    private static float invokeGetSpeedAccuracyMetersPerSecond(AbstractJni jni, BaseVM vm,
                                                               DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getSpeedAccuracyMetersPerSecond", "()F", false);
        return jni.callFloatMethodV(vm, location, method.getSignature(),
                new TestNoArgVaList(vm, method));
    }

    private static float invokeGetBearingAccuracyDegrees(AbstractJni jni, BaseVM vm,
                                                         DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getBearingAccuracyDegrees", "()F", false);
        return jni.callFloatMethodV(vm, location, method.getSignature(),
                new TestNoArgVaList(vm, method));
    }

    private static long invokeGetTime(AbstractJni jni, BaseVM vm, boolean useVaList,
                                      DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getTime", "()J", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callLongMethodV(vm, location, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callLongMethod(vm, location, signature, new TestNoArgVarArg(vm, method));
    }

    private static long invokeGetElapsedRealtimeNanos(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                      DvmObject<?> location) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "getElapsedRealtimeNanos", "()J", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callLongMethodV(vm, location, signature, new TestNoArgVaList(vm, method));
        }
        return jni.callLongMethod(vm, location, signature, new TestNoArgVarArg(vm, method));
    }

    private static boolean invokeIsProviderEnabledNull(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                       DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isProviderEnabled",
                "(Ljava/lang/String;)Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature,
                    new TestObjectVaList(vm, method, 0));
        }
        return jni.callBooleanMethod(vm, target, signature,
                new TestObjectVarArg(vm, method, 0));
    }

    private static boolean invokeIsProviderEnabledNonString(AbstractJni jni, BaseVM vm,
                                                            boolean useVaList, DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "isProviderEnabled",
                "(Ljava/lang/String;)Z", false);
        String signature = method.getSignature();
        int hash = vm.addLocalObject(DvmInteger.valueOf(vm, 1));
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature,
                    new TestObjectVaList(vm, method, hash));
        }
        return jni.callBooleanMethod(vm, target, signature,
                new TestObjectVarArg(vm, method, hash));
    }

    private static boolean invokeHasProviderNull(AbstractJni jni, BaseVM vm, boolean useVaList,
                                                 DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasProvider",
                "(Ljava/lang/String;)Z", false);
        String signature = method.getSignature();
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature,
                    new TestObjectVaList(vm, method, 0));
        }
        return jni.callBooleanMethod(vm, target, signature,
                new TestObjectVarArg(vm, method, 0));
    }

    private static boolean invokeHasProviderNonString(AbstractJni jni, BaseVM vm,
                                                      boolean useVaList, DvmObject<?> target) {
        DvmClass dvmClass = vm.resolveClass(LOCATION_MANAGER_CLASS);
        DvmMethod method = new DvmMethod(dvmClass, "hasProvider",
                "(Ljava/lang/String;)Z", false);
        String signature = method.getSignature();
        int hash = vm.addLocalObject(DvmInteger.valueOf(vm, 1));
        if (useVaList) {
            return jni.callBooleanMethodV(vm, target, signature,
                    new TestObjectVaList(vm, method, hash));
        }
        return jni.callBooleanMethod(vm, target, signature,
                new TestObjectVarArg(vm, method, hash));
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

    private static void assertNoAndroidLocationEvents(CapturingSink sink) {
        for (CapturedEvent e : sink.events) {
            assertFalse("unexpected android_location event: " + e.api,
                    "android_location".equals(e.kind));
        }
    }

    private static void assertLocationJsonInvalid(String json, String expectedPath) {
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

    private static void assertLocationAccuracyJsonInvalid(String json) {
        try {
            TraceEnvironmentConfig.parse(json);
            fail("expected IllegalArgumentException for accuracy json: " + json);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue("message missing accuracy path, was: " + message,
                    message != null
                            && message.contains("android.location.providerCapabilities.gps.accuracy"));
            assertTrue("message must mention allowed 1 or 2, was: " + message,
                    message.contains("1 or 2") || message.contains("1..2"));
        }
    }

    private static void assertLocationPowerRequirementJsonInvalid(String json) {
        try {
            TraceEnvironmentConfig.parse(json);
            fail("expected IllegalArgumentException for powerRequirement json: " + json);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue("message missing powerRequirement path, was: " + message,
                    message != null
                            && message.contains(
                                    "android.location.providerCapabilities.gps.powerRequirement"));
            assertTrue("message must mention allowed 1, 2, or 3, was: " + message,
                    message.contains("1, 2, or 3")
                            || message.contains("1, 2 or 3")
                            || message.contains("1..3"));
        }
    }

    private static int countEventsForProvider(List<CapturedEvent> events, String provider) {
        int n = 0;
        String prefix = "provider=" + provider + ",";
        for (CapturedEvent e : events) {
            if ("android_location".equals(e.kind)
                    && "LocationManager.isProviderEnabled".equals(e.api)
                    && String.valueOf(e.value).startsWith(prefix)) {
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

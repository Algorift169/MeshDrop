package com.meshdrop.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SettingsDefaultsTest {
    @Test
    public void settingDefaultsMatchProductPolicy() {
        assertTrue(ForceScan.DEFAULT_ENABLED);
        assertTrue(ForceBluetooth.DEFAULT_ENABLED);
        assertTrue(AutoAdvertise.DEFAULT_ENABLED);
        assertTrue(DeviceVisibility.DEFAULT_ENABLED);
        assertFalse(AnonymousDiscovery.DEFAULT_ENABLED);
        assertFalse(BatterySaver.DEFAULT_ENABLED);
        assertTrue(RadarAnimation.DEFAULT_ENABLED);
        assertFalse(PeerNotifications.DEFAULT_ENABLED);
        assertEquals(ScanInterval.Mode.CONTINUOUS, ScanInterval.DEFAULT_MODE);
    }

    @Test
    public void nonContinuousModesHaveRealDutyCycles() {
        assertEquals(30_000L, ScanInterval.Mode.BALANCED.getActiveDurationMillis());
        assertEquals(15_000L, ScanInterval.Mode.BALANCED.getIdleDurationMillis());
        assertEquals(10_000L, ScanInterval.Mode.BATTERY_EFFICIENT.getActiveDurationMillis());
        assertEquals(60_000L, ScanInterval.Mode.BATTERY_EFFICIENT.getIdleDurationMillis());
    }
}

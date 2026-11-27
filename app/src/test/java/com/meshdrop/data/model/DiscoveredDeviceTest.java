package com.meshdrop.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class DiscoveredDeviceTest {
    @Test
    public void endpointIdentityControlsEquality() {
        DiscoveredDevice first = new DiscoveredDevice("endpoint-a", "Phone One");
        DiscoveredDevice renamed = new DiscoveredDevice("endpoint-a", "Phone Two");
        DiscoveredDevice other = new DiscoveredDevice("endpoint-b", "Phone One");

        assertEquals(first, renamed);
        assertEquals(first.hashCode(), renamed.hashCode());
        assertNotEquals(first, other);
    }

    @Test
    public void discoveredDeviceStartsAvailableAndNotConnected() {
        DiscoveredDevice device = new DiscoveredDevice("endpoint-a", "Phone One");

        assertEquals(DiscoveredDevice.ConnectionStatus.NOT_CONNECTED,
                device.getConnectionStatus());
        assertEquals(true, device.isAvailable());
        device.setAvailable(false);
        assertFalse(device.isAvailable());
    }
}

package com.meshdrop.data.model;

import androidx.annotation.NonNull;

public final class DiscoveredDevice {
    public enum ConnectionStatus {
        NOT_CONNECTED
    }

    private final String endpointId;
    private final String deviceName;
    private final long discoveryTimestamp;
    private final ConnectionStatus connectionStatus;
    private boolean available;

    public DiscoveredDevice(@NonNull String endpointId, @NonNull String deviceName) {
        this(endpointId, deviceName, System.currentTimeMillis(), ConnectionStatus.NOT_CONNECTED, true);
    }

    public DiscoveredDevice(
            @NonNull String endpointId,
            @NonNull String deviceName,
            long discoveryTimestamp,
            @NonNull ConnectionStatus connectionStatus,
            boolean available) {
        this.endpointId = endpointId;
        this.deviceName = deviceName;
        this.discoveryTimestamp = discoveryTimestamp;
        this.connectionStatus = connectionStatus;
        this.available = available;
    }

    @NonNull
    public String getEndpointId() {
        return endpointId;
    }

    @NonNull
    public String getDeviceName() {
        return deviceName;
    }

    public long getDiscoveryTimestamp() {
        return discoveryTimestamp;
    }

    @NonNull
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiscoveredDevice)) return false;
        DiscoveredDevice that = (DiscoveredDevice) other;
        return endpointId.equals(that.endpointId);
    }

    @Override
    public int hashCode() {
        return endpointId.hashCode();
    }
}

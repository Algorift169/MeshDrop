package com.meshdrop.discovery;

import androidx.annotation.NonNull;

import com.meshdrop.data.model.DiscoveredDevice;
import com.meshdrop.data.model.PeerTreeNode;

import java.util.List;

public interface DiscoveryCallback {
    default void onDeviceDiscovered(@NonNull DiscoveredDevice device) {}
    default void onDeviceLost(@NonNull String endpointId) {}
    default void onDiscoveryStarted() {}
    default void onDiscoveryStopped() {}
    default void onDiscoveryError(@NonNull DiscoveryError error) {}
    default void onAdvertisingStatusChanged(boolean advertising) {}
    default void onRequirementChanged(@NonNull DiscoveryState state) {}
    default void onDeviceRegistryChanged(@NonNull List<DiscoveredDevice> devices) {}
    default void onPeerTopologyChanged(@NonNull List<PeerTreeNode> peers) {}
}

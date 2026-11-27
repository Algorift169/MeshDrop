package com.meshdrop.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class PeerTreeNode {
    private final String peerId;
    private final String deviceName;
    private final String parentId;
    private final String endpointId;
    private final int hopCount;

    public PeerTreeNode(
            @NonNull String peerId,
            @NonNull String deviceName,
            @Nullable String parentId,
            @Nullable String endpointId,
            int hopCount) {
        this.peerId = peerId;
        this.deviceName = deviceName;
        this.parentId = parentId;
        this.endpointId = endpointId;
        this.hopCount = hopCount;
    }

    @NonNull
    public String getPeerId() {
        return peerId;
    }

    @NonNull
    public String getDeviceName() {
        return deviceName;
    }

    @Nullable
    public String getParentId() {
        return parentId;
    }

    @Nullable
    public String getEndpointId() {
        return endpointId;
    }

    public int getHopCount() {
        return hopCount;
    }
}

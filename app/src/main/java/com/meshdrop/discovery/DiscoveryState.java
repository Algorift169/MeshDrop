package com.meshdrop.discovery;

public enum DiscoveryState {
    IDLE,
    CHECKING_REQUIREMENTS,
    REQUESTING_PERMISSIONS,
    ENABLING_BLUETOOTH,
    STARTING,
    DISCOVERING,
    ADVERTISING,
    STOPPING,
    ERROR
}

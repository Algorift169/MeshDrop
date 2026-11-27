package com.meshdrop.discovery;

import androidx.annotation.NonNull;

public final class DiscoveryError {
    public enum Reason {
        MISSING_PERMISSION,
        BLUETOOTH_UNAVAILABLE,
        BLUETOOTH_DISABLED,
        NEARBY_SERVICE_UNAVAILABLE,
        ADVERTISING_FAILURE,
        DISCOVERY_FAILURE,
        UNSUPPORTED_DEVICE,
        UNKNOWN_ERROR
    }

    private final Reason reason;
    private final String description;

    public DiscoveryError(@NonNull Reason reason, @NonNull String description) {
        this.reason = reason;
        this.description = description;
    }

    @NonNull
    public Reason getReason() {
        return reason;
    }

    @NonNull
    public String getDescription() {
        return description;
    }
}

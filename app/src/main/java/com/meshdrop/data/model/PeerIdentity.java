package com.meshdrop.data.model;

import androidx.annotation.NonNull;

public final class PeerIdentity {
    @NonNull public final String userId;
    @NonNull public final String username;
    @NonNull public final String publicDisplayName;
    public final boolean anonymousMode;

    public PeerIdentity(@NonNull String userId,
                        @NonNull String username,
                        @NonNull String publicDisplayName,
                        boolean anonymousMode) {
        this.userId = userId;
        this.username = username;
        this.publicDisplayName = publicDisplayName;
        this.anonymousMode = anonymousMode;
    }
}
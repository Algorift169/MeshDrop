package com.meshdrop.database.entity;

import androidx.annotation.NonNull;

public final class ConversationEntity {
    @NonNull public final String peerId;
    @NonNull public final String peerName;
    public final long updatedAt;

    public ConversationEntity(
            @NonNull String peerId,
            @NonNull String peerName,
            long updatedAt) {
        this.peerId = peerId;
        this.peerName = peerName;
        this.updatedAt = updatedAt;
    }
}
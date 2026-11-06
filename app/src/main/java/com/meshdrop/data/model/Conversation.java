package com.meshdrop.data.model;

import androidx.annotation.NonNull;

public final class Conversation {
    @NonNull public final String peerId;
    @NonNull public final String peerName;
    @NonNull public final String lastMessage;
    public final long updatedAt;

    public Conversation(
            @NonNull String peerId,
            @NonNull String peerName,
            @NonNull String lastMessage,
            long updatedAt) {
        this.peerId = peerId;
        this.peerName = peerName;
        this.lastMessage = lastMessage;
        this.updatedAt = updatedAt;
    }
}
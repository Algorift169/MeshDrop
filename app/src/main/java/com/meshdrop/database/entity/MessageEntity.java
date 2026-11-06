package com.meshdrop.database.entity;

import androidx.annotation.NonNull;

public final class MessageEntity {
    public final long id;
    @NonNull public final String peerId;
    @NonNull public final String body;
    public final boolean outgoing;
    public final long createdAt;

    public MessageEntity(
            long id,
            @NonNull String peerId,
            @NonNull String body,
            boolean outgoing,
            long createdAt) {
        this.id = id;
        this.peerId = peerId;
        this.body = body;
        this.outgoing = outgoing;
        this.createdAt = createdAt;
    }
}
package com.meshdrop.data.model;

import androidx.annotation.NonNull;

public final class Message {
    @NonNull public final String body;
    public final boolean outgoing;
    public final long createdAt;

    public Message(@NonNull String body, boolean outgoing, long createdAt) {
        this.body = body;
        this.outgoing = outgoing;
        this.createdAt = createdAt;
    }
}
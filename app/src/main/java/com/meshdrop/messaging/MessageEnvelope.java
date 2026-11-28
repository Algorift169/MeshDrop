package com.meshdrop.messaging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class MessageEnvelope {
    public final int protocolVersion;
    @NonNull public final MessageProtocol.PacketType packetType;
    @NonNull public final String messageId;
    @NonNull public final String senderUserId;
    @NonNull public final String recipientUserId;
    public final long timestamp;
    @NonNull public final String payload;
    @Nullable public final String acknowledgementFor;

    public MessageEnvelope(int protocolVersion,
                           @NonNull MessageProtocol.PacketType packetType,
                           @NonNull String messageId,
                           @NonNull String senderUserId,
                           @NonNull String recipientUserId,
                           long timestamp,
                           @NonNull String payload,
                           @Nullable String acknowledgementFor) {
        this.protocolVersion = protocolVersion;
        this.packetType = packetType;
        this.messageId = messageId;
        this.senderUserId = senderUserId;
        this.recipientUserId = recipientUserId;
        this.timestamp = timestamp;
        this.payload = payload;
        this.acknowledgementFor = acknowledgementFor;
    }
}

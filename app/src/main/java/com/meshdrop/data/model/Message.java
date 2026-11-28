package com.meshdrop.data.model;

import androidx.annotation.NonNull;

public final class Message {
    @NonNull public final String messageId;
    @NonNull public final String conversationId;
    @NonNull public final String senderUserId;
    @NonNull public final String receiverUserId;
    @NonNull public final String body;
    public final boolean outgoing;
    public final long createdAt;
    @NonNull public final String deliveryStatus;
    @NonNull public final String messageType;
    public final boolean isRead;

    public Message(@NonNull String body, boolean outgoing, long createdAt) {
        this("", "", "", "", body, outgoing, createdAt,
            outgoing ? "PENDING" : "DELIVERED", "TEXT", outgoing);
    }

    public Message(@NonNull String messageId,
                   @NonNull String conversationId,
                   @NonNull String senderUserId,
                   @NonNull String receiverUserId,
                   @NonNull String body,
                   boolean outgoing,
                   long createdAt,
                   @NonNull String deliveryStatus,
                   @NonNull String messageType) {
            this(messageId, conversationId, senderUserId, receiverUserId, body, outgoing,
                createdAt, deliveryStatus, messageType, outgoing);
            }

            public Message(@NonNull String messageId,
                   @NonNull String conversationId,
                   @NonNull String senderUserId,
                   @NonNull String receiverUserId,
                   @NonNull String body,
                   boolean outgoing,
                   long createdAt,
                   @NonNull String deliveryStatus,
                   @NonNull String messageType,
                   boolean isRead) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderUserId = senderUserId;
        this.receiverUserId = receiverUserId;
        this.body = body;
        this.outgoing = outgoing;
        this.createdAt = createdAt;
        this.deliveryStatus = deliveryStatus;
        this.messageType = messageType;
        this.isRead = isRead;
    }
}
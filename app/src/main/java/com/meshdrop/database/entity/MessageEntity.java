package com.meshdrop.database.entity;

import androidx.annotation.NonNull;

import java.util.UUID;

public final class MessageEntity {
    public final long id;
    @NonNull public final String messageId;
    @NonNull public final String conversationId;
    @NonNull public final String senderUserId;
    @NonNull public final String receiverUserId;
    @NonNull public final String messageContent;
    public final long timestamp;
    @NonNull public final String deliveryStatus;
    @NonNull public final String messageType;
    public final boolean isOutgoing;
    public final boolean isRead;

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
        this(id, UUID.randomUUID().toString(), peerId,
                outgoing ? "legacy-local" : peerId,
                outgoing ? peerId : "legacy-local",
                body, outgoing, createdAt, outgoing ? "PENDING" : "DELIVERED", "TEXT");
    }

    public MessageEntity(
            long id,
            @NonNull String messageId,
            @NonNull String conversationId,
            @NonNull String senderUserId,
            @NonNull String receiverUserId,
            @NonNull String messageContent,
            boolean isOutgoing,
            long timestamp,
            @NonNull String deliveryStatus,
            @NonNull String messageType) {
            this(id, messageId, conversationId, senderUserId, receiverUserId, messageContent,
                isOutgoing, timestamp, deliveryStatus, messageType, isOutgoing);
            }

            public MessageEntity(
                long id,
                @NonNull String messageId,
                @NonNull String conversationId,
                @NonNull String senderUserId,
                @NonNull String receiverUserId,
                @NonNull String messageContent,
                boolean isOutgoing,
                long timestamp,
                @NonNull String deliveryStatus,
                @NonNull String messageType,
                boolean isRead) {
        this.id = id;
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderUserId = senderUserId;
        this.receiverUserId = receiverUserId;
        this.messageContent = messageContent;
        this.isOutgoing = isOutgoing;
        this.timestamp = timestamp;
        this.deliveryStatus = deliveryStatus;
        this.messageType = messageType;
        this.isRead = isRead;
        this.peerId = conversationId;
        this.body = messageContent;
        this.outgoing = isOutgoing;
        this.createdAt = timestamp;
    }
}
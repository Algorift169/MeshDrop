package com.meshdrop.data.model;

import androidx.annotation.NonNull;

public final class Conversation {
    @NonNull public final String conversationId;
    @NonNull public final String peerUserId;
    @NonNull public final String peerUsername;
    @NonNull public final String lastMessage;
    @NonNull public final String lastMessageType;
    public final long lastMessageTimestamp;
    public final long createdAt;
    public final long updatedAt;
    public final int unreadCount;

    @NonNull public final String peerId;
    @NonNull public final String peerName;

    public Conversation(
            @NonNull String peerId,
            @NonNull String peerName,
            @NonNull String lastMessage,
            long updatedAt) {
        this(peerId, peerId, peerName, lastMessage, updatedAt, updatedAt, updatedAt, "TEXT");
    }

    public Conversation(
            @NonNull String conversationId,
            @NonNull String peerUserId,
            @NonNull String peerUsername,
            @NonNull String lastMessage,
            long lastMessageTimestamp,
            long createdAt,
            long updatedAt,
            @NonNull String lastMessageType) {
            this(conversationId, peerUserId, peerUsername, lastMessage, lastMessageTimestamp,
                createdAt, updatedAt, lastMessageType, 0);
            }

            public Conversation(
                @NonNull String conversationId,
                @NonNull String peerUserId,
                @NonNull String peerUsername,
                @NonNull String lastMessage,
                long lastMessageTimestamp,
                long createdAt,
                long updatedAt,
                @NonNull String lastMessageType,
                int unreadCount) {
        this.conversationId = conversationId;
        this.peerUserId = peerUserId;
        this.peerUsername = peerUsername;
        this.lastMessage = lastMessage;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastMessageType = lastMessageType;
        this.unreadCount = unreadCount;
        this.peerId = peerUserId;
        this.peerName = peerUsername;
    }
}
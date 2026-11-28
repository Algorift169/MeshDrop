package com.meshdrop.database.entity;

import androidx.annotation.NonNull;

public final class ConversationEntity {
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

    public ConversationEntity(
            @NonNull String peerId,
            @NonNull String peerName,
            long updatedAt) {
        this(peerId, peerId, peerName, "", "TEXT", 0, updatedAt, updatedAt);
    }

    public ConversationEntity(
            @NonNull String conversationId,
            @NonNull String peerUserId,
            @NonNull String peerUsername,
            @NonNull String lastMessage,
            @NonNull String lastMessageType,
            long lastMessageTimestamp,
            long createdAt,
            long updatedAt) {
            this(conversationId, peerUserId, peerUsername, lastMessage, lastMessageType,
                lastMessageTimestamp, createdAt, updatedAt, 0);
            }

            public ConversationEntity(
                @NonNull String conversationId,
                @NonNull String peerUserId,
                @NonNull String peerUsername,
                @NonNull String lastMessage,
                @NonNull String lastMessageType,
                long lastMessageTimestamp,
                long createdAt,
                long updatedAt,
                int unreadCount) {
        this.conversationId = conversationId;
        this.peerUserId = peerUserId;
        this.peerUsername = peerUsername;
        this.lastMessage = lastMessage;
        this.lastMessageType = lastMessageType;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.unreadCount = unreadCount;
        this.peerId = peerUserId;
        this.peerName = peerUsername;
    }
}
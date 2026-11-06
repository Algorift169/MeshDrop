package com.meshdrop.database.repository;

import androidx.annotation.NonNull;

import com.meshdrop.data.model.Conversation;
import com.meshdrop.data.model.Message;

import java.util.List;

public interface ChatRepository {
    void saveMessage(
            @NonNull String peerId,
            @NonNull String peerName,
            @NonNull String body,
            boolean outgoing,
            long createdAt,
            @NonNull Callback<Void> callback);

    void getConversations(@NonNull Callback<List<Conversation>> callback);

    void getConversation(
            @NonNull String peerId,
            @NonNull Callback<Conversation> callback);

    void getMessages(
            @NonNull String peerId,
            @NonNull Callback<List<Message>> callback);

    void deleteConversation(@NonNull String peerId, @NonNull Callback<Void> callback);

    interface Callback<T> {
        void onSuccess(T result);

        void onError(@NonNull Exception error);
    }
}
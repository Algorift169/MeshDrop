package com.meshdrop.database.repository;

import androidx.annotation.NonNull;

import com.meshdrop.data.model.Conversation;
import com.meshdrop.data.model.Message;
import com.meshdrop.data.model.PeerIdentity;

import java.util.List;

public interface ChatRepository {
    void saveMessage(
            @NonNull String peerId,
            @NonNull String peerName,
            @NonNull String body,
            boolean outgoing,
            long createdAt,
            @NonNull Callback<Void> callback);

        void upsertPeerIdentity(@NonNull PeerIdentity peer,
                                                        @NonNull Callback<Conversation> callback);

        void saveOutgoingMessage(@NonNull PeerIdentity localIdentity,
                                                         @NonNull PeerIdentity peerIdentity,
                                                         @NonNull String messageId,
                                                         @NonNull String body,
                                                         long timestamp,
                                                         @NonNull Callback<Message> callback);

        void saveIncomingMessage(@NonNull String messageId,
                                                         @NonNull String senderUserId,
                                                         @NonNull String senderUsername,
                                                         @NonNull String receiverUserId,
                                                         @NonNull String body,
                                                         long timestamp,
                                                         boolean isRead,
                                                         @NonNull Callback<Boolean> callback);

        void markConversationRead(@NonNull String peerId, @NonNull Callback<Void> callback);

        void updateDeliveryStatus(@NonNull String messageId,
                                                          @NonNull String deliveryStatus,
                                                          @NonNull Callback<Void> callback);

        void updateOutgoingDeliveryStatusForPeer(@NonNull String messageId,
                                                 @NonNull String peerUserId,
                                                 @NonNull String deliveryStatus,
                                                 @NonNull Callback<Integer> callback);

        void getPendingMessages(@NonNull Callback<List<Message>> callback);

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
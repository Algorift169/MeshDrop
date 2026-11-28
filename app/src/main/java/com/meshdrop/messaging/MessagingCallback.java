package com.meshdrop.messaging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.meshdrop.data.model.Message;
import com.meshdrop.data.model.PeerIdentity;

public interface MessagingCallback {
    default void onConnectionRequestReceived(@NonNull String endpointId,
                                            @NonNull String displayName,
                                            @NonNull String authenticationToken,
                                            boolean outgoingRequest) {}
    default void onConnectionEstablished(@NonNull PeerIdentity peer) {}
    default void onConnectionRejected(@NonNull String endpointId) {}
    default void onConnectionDisconnected(@NonNull String peerUserId) {}
    default void onPeerIdentityReceived(@NonNull String endpointId,
                                        @NonNull PeerIdentity identity) {}
    default void onMessagePersisted(@NonNull Message message) {}
    default void onMessageSending(@NonNull Message message) {}
    default void onMessageSent(@NonNull Message message) {}
    default void onMessageDelivered(@NonNull String messageId) {}
    default void onMessageReceived(@NonNull Message message) {}
    default void onMessageFailed(@NonNull String messageId, @NonNull String reason) {}
    default void onMessagingError(@NonNull String message, @Nullable Throwable cause) {}
}

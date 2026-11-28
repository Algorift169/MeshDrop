package com.meshdrop.messaging;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.meshdrop.R;
import com.meshdrop.data.model.Message;
import com.meshdrop.data.model.PeerIdentity;
import com.meshdrop.data.model.PeerTreeNode;
import com.meshdrop.discovery.NearbyDiscoveryManager;
import com.meshdrop.database.repository.ChatRepository;
import com.meshdrop.database.repository.ChatRepositoryImpl;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public final class NearbyChatManager {
    private static final String TAG = "NearbyChat";
    private static final String UNKNOWN_RECIPIENT = "unknown";
    private static volatile NearbyChatManager instance;

    private final Context appContext;
    private final ConnectionsClient connectionsClient;
    private final PeerIdentityManager peerIdentityManager;
    private final ChatRepository repository;
    private final PendingMessageManager pendingMessageManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArraySet<MessagingCallback> callbacks = new CopyOnWriteArraySet<>();
    private final Map<String, ConnectionInfo> pendingRequests = new ConcurrentHashMap<>();
        private final Set<String> acceptedEndpoints = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());
        private final Set<String> connectedEndpoints = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());
        private final Set<String> identitySentEndpoints = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());
            private final Set<String> identityPersistencePending = Collections.newSetFromMap(
                new ConcurrentHashMap<String, Boolean>());
            private final Set<String> requestedEndpoints = Collections.newSetFromMap(
                new ConcurrentHashMap<String, Boolean>());
    private final Map<String, PeerIdentity> identitiesByEndpoint = new ConcurrentHashMap<>();
    private final Map<String, String> endpointsByPeerId = new ConcurrentHashMap<>();
        private final Set<String> outgoingConnectionEndpoints = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());
    private volatile String activeConversationUserId;

    private NearbyChatManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        connectionsClient = Nearby.getConnectionsClient(appContext);
        peerIdentityManager = new PeerIdentityManager(appContext);
        repository = ChatRepositoryImpl.getInstance(appContext);
        pendingMessageManager = new PendingMessageManager(repository);
    }

    @NonNull
    public static NearbyChatManager getInstance(@NonNull Context context) {
        NearbyChatManager current = instance;
        if (current == null) {
            synchronized (NearbyChatManager.class) {
                current = instance;
                if (current == null) {
                    current = new NearbyChatManager(context.getApplicationContext());
                    instance = current;
                }
            }
        }
        return current;
    }

    public void addCallback(@NonNull MessagingCallback callback) {
        callbacks.add(callback);
        for (Map.Entry<String, ConnectionInfo> request : pendingRequests.entrySet()) {
            dispatch(() -> callback.onConnectionRequestReceived(
                    request.getKey(), safeEndpointName(request.getValue()),
                    request.getValue().getAuthenticationToken(),
                    !request.getValue().isIncomingConnection()));
        }
    }

    public void removeCallback(@NonNull MessagingCallback callback) {
        callbacks.remove(callback);
    }

    public void onNearbyConnectionInitiated(@NonNull String endpointId,
                                            @NonNull ConnectionInfo connectionInfo) {
        connectionLifecycleCallback.onConnectionInitiated(endpointId, connectionInfo);
    }

    public void onNearbyConnectionResult(@NonNull String endpointId,
                                         @NonNull ConnectionResolution result) {
        connectionLifecycleCallback.onConnectionResult(endpointId, result);
    }

    public void onNearbyDisconnected(@NonNull String endpointId) {
        connectionLifecycleCallback.onDisconnected(endpointId);
    }

    @NonNull
    public PeerIdentity getLocalIdentity() {
        return peerIdentityManager.getLocalIdentity();
    }

    public boolean isConnected(@NonNull String peerUserId) {
        String endpointId = endpointsByPeerId.get(peerUserId);
        return endpointId != null && connectedEndpoints.contains(endpointId)
                && identitiesByEndpoint.containsKey(endpointId);
    }

    @Nullable
    public String getEndpointForPeer(@NonNull String peerUserId) {
        return endpointsByPeerId.get(peerUserId);
    }

    public void connectToEndpoint(@NonNull String endpointId) {
        if (connectedEndpoints.contains(endpointId)) {
            PeerIdentity connectedPeer = identitiesByEndpoint.get(endpointId);
            if (connectedPeer != null) {
                dispatch(() -> {
                    for (MessagingCallback callback : callbacks) {
                        callback.onConnectionEstablished(connectedPeer);
                    }
                });
            }
            return;
        }
        if (pendingRequests.containsKey(endpointId) || !requestedEndpoints.add(endpointId)) return;
        outgoingConnectionEndpoints.add(endpointId);
        PeerIdentity localIdentity = peerIdentityManager.getLocalIdentity();
        try {
            connectionsClient.requestConnection(
                    localIdentity.publicDisplayName, endpointId, connectionLifecycleCallback)
                    .addOnFailureListener(error -> {
                        requestedEndpoints.remove(endpointId);
                        outgoingConnectionEndpoints.remove(endpointId);
                        Log.w(TAG, "Could not request connection", error);
                        notifyError("Could not request a connection.", error);
                    });
        } catch (SecurityException error) {
            requestedEndpoints.remove(endpointId);
            outgoingConnectionEndpoints.remove(endpointId);
            notifyError("Nearby connection permission is unavailable.", error);
        }
    }

    public void acceptConnection(@NonNull String endpointId) {
        if (!pendingRequests.containsKey(endpointId) || !acceptedEndpoints.add(endpointId)) return;
        try {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                    .addOnFailureListener(error -> {
                        acceptedEndpoints.remove(endpointId);
                        Log.w(TAG, "Could not accept connection", error);
                        notifyError("Could not accept the connection.", error);
                    });
        } catch (SecurityException error) {
            acceptedEndpoints.remove(endpointId);
            notifyError("Nearby connection permission is unavailable.", error);
        }
    }

    public void rejectConnection(@NonNull String endpointId) {
        ConnectionInfo request = pendingRequests.remove(endpointId);
        acceptedEndpoints.remove(endpointId);
        if (request == null) return;
        connectionsClient.rejectConnection(endpointId)
                .addOnCompleteListener(task -> dispatch(() -> {
                    for (MessagingCallback callback : callbacks) {
                        callback.onConnectionRejected(endpointId);
                    }
                }));
    }

    public void sendText(@NonNull String peerUserId,
                         @NonNull String body,
                         @NonNull ChatRepository.Callback<Message> callback) {
        final String validBody;
        try {
            validBody = body.trim();
            MessageProtocol.validateText(validBody);
        } catch (IllegalArgumentException error) {
            callback.onError(error);
            return;
        }

        PeerIdentity peer = identityForPeer(peerUserId);
        if (peer == null) {
            callback.onError(new IllegalStateException("Peer identity is not established."));
            return;
        }
        PeerIdentity localIdentity = peerIdentityManager.getLocalIdentity();
        String messageId = UUID.randomUUID().toString();
        repository.saveOutgoingMessage(localIdentity, peer, messageId, validBody,
                System.currentTimeMillis(), new ChatRepository.Callback<Message>() {
                    @Override
                    public void onSuccess(Message message) {
                        notifyMessagePersisted(message);
                        callback.onSuccess(message);
                        pendingMessageManager.retryMessage(message, sender);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        callback.onError(error);
                    }
                });
    }

    public void retryPendingForPeer(@NonNull String peerUserId) {
        pendingMessageManager.retryForPeer(peerUserId, sender);
    }

    public void onLocalProfileChanged() {
        broadcastPeerInformation(null);
    }

    public void setActiveConversation(@Nullable String peerUserId) {
        activeConversationUserId = peerUserId;
    }

    public void clearActiveConversation(@NonNull String peerUserId) {
        if (peerUserId.equals(activeConversationUserId)) {
            activeConversationUserId = null;
        }
    }

    @Nullable
    private PeerIdentity identityForPeer(@NonNull String peerUserId) {
        String endpointId = endpointsByPeerId.get(peerUserId);
        return endpointId == null ? null : identitiesByEndpoint.get(endpointId);
    }

    private final PendingMessageManager.Sender sender = new PendingMessageManager.Sender() {
        @Override
        public boolean isConnected(@NonNull String peerUserId) {
            return NearbyChatManager.this.isConnected(peerUserId);
        }

        @Override
        public void send(@NonNull Message message,
                         @NonNull PendingMessageManager.SendResult result) {
            String endpointId = endpointsByPeerId.get(message.receiverUserId);
            if (endpointId == null || !connectedEndpoints.contains(endpointId)) {
                result.onFailure("Peer is disconnected");
                return;
            }
            notifyMessageSending(message);
            MessageEnvelope envelope = new MessageEnvelope(
                    MessageProtocol.VERSION,
                    MessageProtocol.PacketType.TEXT_MESSAGE,
                    message.messageId,
                    message.senderUserId,
                    message.receiverUserId,
                    message.createdAt,
                    message.body,
                    null);
            try {
                connectionsClient.sendPayload(endpointId,
                        Payload.fromBytes(MessageProtocol.encode(envelope)))
                        .addOnSuccessListener(unused -> {
                            result.onSuccess();
                            notifyMessageSent(message);
                        })
                        .addOnFailureListener(error -> {
                            Log.w(TAG, "Nearby payload send failed", error);
                            result.onFailure("Nearby payload transmission failed");
                        });
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not send message payload", error);
                result.onFailure("Message payload could not be sent");
            }
        }

        @Override
        public void onPermanentlyFailed(@NonNull Message message, @NonNull String reason) {
            dispatch(() -> {
                for (MessagingCallback callback : callbacks) {
                    callback.onMessageFailed(message.messageId, reason);
                }
            });
        }
    };

    private void sendHandshake(@NonNull String endpointId) {
        if (!connectedEndpoints.contains(endpointId) || !identitySentEndpoints.add(endpointId)) return;
        sendPeerInformation(endpointId, MessageProtocol.PacketType.HANDSHAKE, UNKNOWN_RECIPIENT);
        }

        private void sendPeerInformation(@NonNull String endpointId,
                         @NonNull MessageProtocol.PacketType packetType,
                         @NonNull String recipientUserId) {
        if (!connectedEndpoints.contains(endpointId)) return;
        PeerIdentity local = peerIdentityManager.getLocalIdentity();
        JSONObject identity = new JSONObject();
        try {
            identity.put("username", local.anonymousMode
                    ? local.publicDisplayName
                    : local.username);
            identity.put("displayName", local.publicDisplayName);
            identity.put("anonymous", local.anonymousMode);
            org.json.JSONArray peers = new org.json.JSONArray();
            List<PeerTreeNode> currentPeers = NearbyDiscoveryManager.getInstance(appContext)
                .getPeerTree();
            int limit = Math.min(currentPeers.size(), 32);
            for (int index = 0; index < limit; index++) {
            PeerTreeNode peer = currentPeers.get(index);
            JSONObject peerJson = new JSONObject();
            peerJson.put("id", peer.getPeerId());
            peerJson.put("name", peer.getDeviceName());
            peerJson.put("parent", peer.getParentId() == null
                ? local.userId
                : peer.getParentId());
            peers.put(peerJson);
            }
            identity.put("peers", peers);
            MessageEnvelope envelope = new MessageEnvelope(
                    MessageProtocol.VERSION,
                packetType,
                    UUID.randomUUID().toString(),
                    local.userId,
                recipientUserId,
                    System.currentTimeMillis(),
                    identity.toString(),
                    null);
            connectionsClient.sendPayload(endpointId,
                    Payload.fromBytes(MessageProtocol.encode(envelope)))
                    .addOnFailureListener(error -> {
                        if (packetType == MessageProtocol.PacketType.HANDSHAKE) {
                            identitySentEndpoints.remove(endpointId);
                        }
                        Log.w(TAG, "Could not send identity handshake", error);
                        notifyError("Could not exchange MeshDrop identity.", error);
                    });
        } catch (JSONException error) {
            if (packetType == MessageProtocol.PacketType.HANDSHAKE) {
                identitySentEndpoints.remove(endpointId);
            }
            notifyError("Could not encode MeshDrop identity.", error);
        }
    }

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId,
                                                  @NonNull ConnectionInfo connectionInfo) {
                    pendingRequests.put(endpointId, connectionInfo);
                    dispatch(() -> {
                        for (MessagingCallback callback : callbacks) {
                            callback.onConnectionRequestReceived(endpointId,
                                    safeEndpointName(connectionInfo),
                                    connectionInfo.getAuthenticationToken(),
                                    !connectionInfo.isIncomingConnection());
                        }
                    });
                }

                @Override
                public void onConnectionResult(@NonNull String endpointId,
                                              @NonNull ConnectionResolution result) {
                    pendingRequests.remove(endpointId);
                    requestedEndpoints.remove(endpointId);
                    if (result.getStatus().isSuccess()) {
                        connectedEndpoints.add(endpointId);
                        sendHandshake(endpointId);
                    } else {
                        outgoingConnectionEndpoints.remove(endpointId);
                        acceptedEndpoints.remove(endpointId);
                        dispatch(() -> {
                            for (MessagingCallback callback : callbacks) {
                                callback.onConnectionRejected(endpointId);
                            }
                        });
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    connectedEndpoints.remove(endpointId);
                    acceptedEndpoints.remove(endpointId);
                    requestedEndpoints.remove(endpointId);
                    outgoingConnectionEndpoints.remove(endpointId);
                    identitySentEndpoints.remove(endpointId);
                    identityPersistencePending.remove(endpointId);
                    PeerIdentity peer = identitiesByEndpoint.remove(endpointId);
                    peerIdentityManager.removeEndpoint(endpointId);
                            boolean topologyChanged = NearbyDiscoveryManager.getInstance(appContext)
                                .removeConnectedPeerTopology(endpointId);
                        if (topologyChanged) broadcastPeerInformation(null);
                    if (peer != null) {
                        if (endpointId.equals(endpointsByPeerId.get(peer.userId))) {
                            endpointsByPeerId.remove(peer.userId);
                        }
                        dispatch(() -> {
                            for (MessagingCallback callback : callbacks) {
                                callback.onConnectionDisconnected(peer.userId);
                            }
                        });
                    }
                }
            };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() != Payload.Type.BYTES || payload.asBytes() == null) return;
            final MessageEnvelope envelope;
            try {
                envelope = MessageProtocol.decode(payload.asBytes());
            } catch (IllegalArgumentException error) {
                Log.w(TAG, "Rejected malformed message packet", error);
                notifyError("Received an invalid message packet.", error);
                return;
            }
            if (!connectedEndpoints.contains(endpointId)) return;
            if (envelope.packetType == MessageProtocol.PacketType.HANDSHAKE
                    || envelope.packetType == MessageProtocol.PacketType.CONNECTION_INFO) {
                receiveIdentity(endpointId, envelope);
            } else {
                PeerIdentity peer = identitiesByEndpoint.get(endpointId);
                if (peer == null || !peer.userId.equals(envelope.senderUserId)
                    || !peerIdentityManager.getLocalIdentity().userId
                    .equals(envelope.recipientUserId)) {
                    return;
                }
                if (envelope.packetType == MessageProtocol.PacketType.TEXT_MESSAGE) {
                    receiveText(endpointId, peer, envelope);
                } else if (envelope.packetType == MessageProtocol.PacketType.MESSAGE_ACK) {
                    receiveAcknowledgement(peer, envelope);
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId,
                                            @NonNull PayloadTransferUpdate update) {
        }
    };

    private void receiveIdentity(@NonNull String endpointId,
                                 @NonNull MessageEnvelope envelope) {
        PeerIdentity local = peerIdentityManager.getLocalIdentity();
        if (envelope.senderUserId.equals(local.userId)
                || !(UNKNOWN_RECIPIENT.equals(envelope.recipientUserId)
                || local.userId.equals(envelope.recipientUserId))) {
            return;
        }
        try {
            JSONObject payload = new JSONObject(envelope.payload);
            String username = sanitizeUsername(payload.getString("username"));
            String publicName = sanitizeUsername(payload.getString("displayName"));
            boolean anonymous = payload.optBoolean("anonymous", false);
            PeerIdentity identity = new PeerIdentity(envelope.senderUserId,
                    username, publicName, anonymous);
                List<PeerTreeNode> peerTree = parsePeerTree(payload);
            PeerIdentity existingIdentity = identitiesByEndpoint.get(endpointId);
            String existingEndpoint = endpointsByPeerId.get(identity.userId);
            if ((existingIdentity != null && !existingIdentity.userId.equals(identity.userId))
                    || (existingEndpoint != null && !existingEndpoint.equals(endpointId)
                    && connectedEndpoints.contains(existingEndpoint))) {
                connectionsClient.disconnectFromEndpoint(endpointId);
                notifyError("This peer identity is already associated with another active connection.", null);
                return;
            }
            PeerIdentity previousIdentity = identitiesByEndpoint.get(endpointId);
            if (previousIdentity != null
                    && previousIdentity.userId.equals(identity.userId)
                    && previousIdentity.username.equals(identity.username)
                    && previousIdentity.publicDisplayName.equals(identity.publicDisplayName)) {
                if (mergePeerTree(endpointId, identity, peerTree)) {
                    broadcastPeerInformation(endpointId);
                }
                return;
            }
            if (!identityPersistencePending.add(endpointId)) return;
            repository.upsertPeerIdentity(identity, new ChatRepository.Callback<com.meshdrop.data.model.Conversation>() {
                @Override
                public void onSuccess(com.meshdrop.data.model.Conversation conversation) {
                    identityPersistencePending.remove(endpointId);
                    peerIdentityManager.rememberPeer(endpointId, identity);
                    identitiesByEndpoint.put(endpointId, identity);
                    endpointsByPeerId.put(identity.userId, endpointId);
                    boolean topologyChanged = mergePeerTree(endpointId, identity, peerTree);
                    boolean openInitiatedChat = previousIdentity == null
                            && outgoingConnectionEndpoints.remove(endpointId);
                    dispatch(() -> {
                        for (MessagingCallback callback : callbacks) {
                            callback.onPeerIdentityReceived(endpointId, identity);
                            if (openInitiatedChat) {
                                callback.onConnectionEstablished(identity);
                            }
                        }
                    });
                    pendingMessageManager.retryForPeer(identity.userId, sender);
                    if (topologyChanged) broadcastPeerInformation(endpointId);
                }

                @Override
                public void onError(@NonNull Exception error) {
                    identityPersistencePending.remove(endpointId);
                    notifyError("Could not store peer identity.", error);
                }
            });
        } catch (JSONException | RuntimeException error) {
            Log.w(TAG, "Rejected invalid identity handshake", error);
            notifyError("Received an invalid peer identity.", error);
        }
    }

    private void receiveText(@NonNull String endpointId,
                             @NonNull PeerIdentity peer,
                             @NonNull MessageEnvelope envelope) {
        PeerIdentity local = peerIdentityManager.getLocalIdentity();
        boolean conversationOpen = peer.userId.equals(activeConversationUserId);
        repository.saveIncomingMessage(envelope.messageId, peer.userId, peer.username,
            local.userId, envelope.payload, envelope.timestamp, conversationOpen,
                new ChatRepository.Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean inserted) {
                        sendAcknowledgement(endpointId, envelope);
                        if (Boolean.TRUE.equals(inserted)) {
                            Message message = new Message(envelope.messageId, peer.userId,
                                    peer.userId, local.userId, envelope.payload, false,
                                    envelope.timestamp, MessageDeliveryStatus.DELIVERED.name(), "TEXT");
                            dispatch(() -> {
                                for (MessagingCallback callback : callbacks) {
                                    callback.onMessageReceived(message);
                                }
                            });
                            if (!conversationOpen) {
                                ChatNotifications.show(appContext, peer.userId, peer.username,
                                    envelope.payload);
                            }
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        notifyError("Could not save the received message.", error);
                    }
                });
    }

    private void sendAcknowledgement(@NonNull String endpointId,
                                    @NonNull MessageEnvelope received) {
        PeerIdentity local = peerIdentityManager.getLocalIdentity();
        MessageEnvelope acknowledgement = new MessageEnvelope(
                MessageProtocol.VERSION,
                MessageProtocol.PacketType.MESSAGE_ACK,
                UUID.randomUUID().toString(),
                local.userId,
                received.senderUserId,
                System.currentTimeMillis(),
                "",
                received.messageId);
        sendControlPacket(endpointId, acknowledgement);
    }

    private void receiveAcknowledgement(@NonNull PeerIdentity peer,
                                        @NonNull MessageEnvelope envelope) {
        if (envelope.acknowledgementFor == null
                || !peer.userId.equals(envelope.senderUserId)
                || !peerIdentityManager.getLocalIdentity().userId.equals(envelope.recipientUserId)) {
            return;
        }
        pendingMessageManager.markDelivered(envelope.acknowledgementFor, peer.userId,
                new ChatRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        dispatch(() -> {
                            for (MessagingCallback callback : callbacks) {
                                callback.onMessageDelivered(envelope.acknowledgementFor);
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        notifyError("Could not update message delivery state.", error);
                    }
                });
    }

    private void sendControlPacket(@NonNull String endpointId,
                                   @NonNull MessageEnvelope envelope) {
        try {
            connectionsClient.sendPayload(endpointId,
                    Payload.fromBytes(MessageProtocol.encode(envelope)))
                    .addOnFailureListener(error -> Log.w(TAG, "Control payload failed", error));
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not send control packet", error);
        }
    }

    @NonNull
    private List<PeerTreeNode> parsePeerTree(@NonNull JSONObject identityPayload) {
        List<PeerTreeNode> peers = new ArrayList<>();
        org.json.JSONArray peerArray = identityPayload.optJSONArray("peers");
        if (peerArray == null) return peers;
        int limit = Math.min(peerArray.length(), 32);
        for (int index = 0; index < limit; index++) {
            JSONObject item = peerArray.optJSONObject(index);
            if (item == null) continue;
            String peerId = item.optString("id", "");
            String parentId = item.optString("parent", "");
            String peerName = sanitizeUsername(item.optString("name", ""));
            if (!isValidIdentity(peerId) || !isValidIdentity(parentId)
                    || peerId.equals(parentId)) {
                continue;
            }
            peers.add(new PeerTreeNode(peerId, peerName, parentId, null, 1));
        }
        return peers;
    }

    private boolean mergePeerTree(@NonNull String endpointId,
                                  @NonNull PeerIdentity identity,
                                  @NonNull List<PeerTreeNode> peers) {
        return NearbyDiscoveryManager.getInstance(appContext).updateConnectedPeerTopology(
                endpointId, identity.userId, identity.publicDisplayName, peers);
    }

    private void broadcastPeerInformation(@Nullable String excludedEndpoint) {
        for (String endpointId : connectedEndpoints) {
            if (endpointId.equals(excludedEndpoint)) continue;
            PeerIdentity peer = identitiesByEndpoint.get(endpointId);
            if (peer != null) {
                sendPeerInformation(endpointId, MessageProtocol.PacketType.CONNECTION_INFO,
                        peer.userId);
            }
        }
    }

    private boolean isValidIdentity(@NonNull String value) {
        if (value.isEmpty() || value.length() > 128) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(Character.isLetterOrDigit(character)
                    || character == '-' || character == '_' || character == '.')) return false;
        }
        return true;
    }

    @NonNull
    private String safeEndpointName(@NonNull ConnectionInfo info) {
        return appContext.getString(R.string.chat_unknown_peer);
    }

    @NonNull
    private String sanitizeUsername(@NonNull String username) {
        StringBuilder safe = new StringBuilder();
        for (int index = 0; index < username.length() && safe.length() < 48; index++) {
            char character = username.charAt(index);
            if (!Character.isISOControl(character)) safe.append(character);
        }
        String value = safe.toString().trim();
        return value.isEmpty() ? "Unknown MeshDrop user" : value;
    }

    private void notifyMessagePersisted(@NonNull Message message) {
        dispatch(() -> {
            for (MessagingCallback callback : callbacks) callback.onMessagePersisted(message);
        });
    }

    private void notifyMessageSending(@NonNull Message message) {
        dispatch(() -> {
            for (MessagingCallback callback : callbacks) callback.onMessageSending(message);
        });
    }

    private void notifyMessageSent(@NonNull Message message) {
        dispatch(() -> {
            for (MessagingCallback callback : callbacks) callback.onMessageSent(message);
        });
    }

    private void notifyError(@NonNull String message, @Nullable Throwable cause) {
        dispatch(() -> {
            for (MessagingCallback callback : callbacks) callback.onMessagingError(message, cause);
        });
    }

    private void dispatch(@NonNull Runnable callback) {
        if (Looper.myLooper() == Looper.getMainLooper()) callback.run();
        else mainHandler.post(callback);
    }
}

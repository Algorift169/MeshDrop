package com.meshdrop.messaging;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.meshdrop.data.model.Message;
import com.meshdrop.database.repository.ChatRepository;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class PendingMessageManager {
    private static final String TAG = "PendingMessages";
    private static final int MAX_ATTEMPTS = 3;
    private static final long[] RETRY_DELAYS_MILLIS = {3_000L, 10_000L};
    private static final long ACK_TIMEOUT_MILLIS = 15_000L;

    public interface Sender {
        boolean isConnected(@NonNull String peerUserId);
        void send(@NonNull Message message, @NonNull SendResult result);
        default void onPermanentlyFailed(@NonNull Message message, @NonNull String reason) {}
    }

    public interface SendResult {
        void onSuccess();
        void onFailure(@NonNull String reason);
    }

    private final ChatRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Integer> attemptsByMessage = new HashMap<>();
    private final Map<String, Runnable> retriesByMessage = new HashMap<>();

    public PendingMessageManager(@NonNull ChatRepository repository) {
        this.repository = repository;
    }

    public void retryForPeer(@NonNull String peerUserId, @NonNull Sender sender) {
        if (!sender.isConnected(peerUserId)) return;
        repository.getPendingMessages(new ChatRepository.Callback<List<Message>>() {
            @Override
            public void onSuccess(List<Message> messages) {
                for (Message message : messages) {
                    if (peerUserId.equals(message.receiverUserId)) {
                        transmit(message, sender);
                    }
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "Could not load persisted pending messages", error);
            }
        });
    }

    public void retryMessage(@NonNull Message message, @NonNull Sender sender) {
        if (sender.isConnected(message.receiverUserId)) {
            transmit(message, sender);
        }
    }

    public void markDelivered(@NonNull String messageId,
                              @NonNull String peerUserId,
                              @NonNull ChatRepository.Callback<Void> callback) {
        cancelRetry(messageId);
        attemptsByMessage.remove(messageId);
        repository.updateOutgoingDeliveryStatusForPeer(messageId, peerUserId,
                MessageDeliveryStatus.DELIVERED.name(), new ChatRepository.Callback<Integer>() {
                    @Override
                    public void onSuccess(Integer updatedRows) {
                        if (updatedRows != null && updatedRows > 0) callback.onSuccess(null);
                        else callback.onError(new IllegalArgumentException(
                                "Acknowledgement does not match an outgoing message for this peer."));
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        callback.onError(error);
                    }
                });
    }

    public void cancelAll() {
        for (Runnable retry : retriesByMessage.values()) {
            mainHandler.removeCallbacks(retry);
        }
        retriesByMessage.clear();
        attemptsByMessage.clear();
    }

    private synchronized void transmit(@NonNull Message message, @NonNull Sender sender) {
        if (retriesByMessage.containsKey(message.messageId)) return;
        Integer previousAttempts = attemptsByMessage.get(message.messageId);
        int attempt = previousAttempts == null ? 1 : previousAttempts + 1;
        attemptsByMessage.put(message.messageId, attempt);
        repository.updateDeliveryStatus(message.messageId,
                MessageDeliveryStatus.SENDING.name(), new EmptyCallback());
        sender.send(message, new SendResult() {
            @Override
            public void onSuccess() {
                repository.updateDeliveryStatus(message.messageId,
                        MessageDeliveryStatus.SENT.name(), new EmptyCallback());
                scheduleRetry(message, sender, attempt, ACK_TIMEOUT_MILLIS);
            }

            @Override
            public void onFailure(@NonNull String reason) {
                if (attempt >= MAX_ATTEMPTS) {
                    attemptsByMessage.remove(message.messageId);
                    repository.updateDeliveryStatus(message.messageId,
                            MessageDeliveryStatus.FAILED.name(), new EmptyCallback());
                    sender.onPermanentlyFailed(message, reason);
                    return;
                }
                repository.updateDeliveryStatus(message.messageId,
                        MessageDeliveryStatus.PENDING.name(), new EmptyCallback());
                scheduleRetry(message, sender,
                        attempt, RETRY_DELAYS_MILLIS[Math.min(attempt - 1,
                                RETRY_DELAYS_MILLIS.length - 1)]);
            }
        });
    }

    private synchronized void scheduleRetry(@NonNull Message message,
                                            @NonNull Sender sender,
                                            int attempt,
                                            long delayMillis) {
        cancelRetry(message.messageId);
        Runnable retry = () -> {
            retriesByMessage.remove(message.messageId);
            if (attempt >= MAX_ATTEMPTS) {
                attemptsByMessage.remove(message.messageId);
                repository.updateDeliveryStatus(message.messageId,
                        MessageDeliveryStatus.FAILED.name(), new EmptyCallback());
                sender.onPermanentlyFailed(message, "No delivery acknowledgement received");
            } else if (sender.isConnected(message.receiverUserId)) {
                transmit(message, sender);
            } else {
                repository.updateDeliveryStatus(message.messageId,
                        MessageDeliveryStatus.PENDING.name(), new EmptyCallback());
            }
        };
        retriesByMessage.put(message.messageId, retry);
        mainHandler.postDelayed(retry, delayMillis);
    }

    private synchronized void cancelRetry(@NonNull String messageId) {
        Runnable retry = retriesByMessage.remove(messageId);
        if (retry != null) mainHandler.removeCallbacks(retry);
    }

    private static final class EmptyCallback implements ChatRepository.Callback<Void> {
        @Override
        public void onSuccess(Void result) {}

        @Override
        public void onError(@NonNull Exception error) {
            Log.w(TAG, "Could not update persisted delivery state", error);
        }
    }
}

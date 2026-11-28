package com.meshdrop.ui.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.meshdrop.R;
import com.meshdrop.data.model.Message;
import com.meshdrop.data.model.PeerIdentity;
import com.meshdrop.database.repository.ChatRepository;
import com.meshdrop.database.repository.ChatRepositoryImpl;
import com.meshdrop.messaging.MessageDeliveryStatus;
import com.meshdrop.messaging.ChatNotifications;
import com.meshdrop.messaging.PeerAvatarStore;
import com.meshdrop.messaging.MessagingCallback;
import com.meshdrop.messaging.NearbyChatManager;
import com.meshdrop.ui.adapter.MessageAdapter;

import java.util.List;

public final class ChatActivity extends AppCompatActivity implements MessagingCallback {
    public static final String EXTRA_PEER_USER_ID = "peer_user_id";
    public static final String EXTRA_PEER_USERNAME = "peer_username";

    private String peerUserId;
    private String peerUsername;
    private NearbyChatManager chatManager;
    private ChatRepository repository;
    private MessageAdapter messageAdapter;
    private RecyclerView messageList;
    private EditText messageInput;
    private TextView connectionStatus;
    private TextView peerAvatar;
    private ImageView peerAvatarPhoto;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        peerUserId = getIntent().getStringExtra(EXTRA_PEER_USER_ID);
        peerUsername = getIntent().getStringExtra(EXTRA_PEER_USERNAME);
        if (peerUserId == null || peerUserId.trim().isEmpty()) {
            finish();
            return;
        }
        if (peerUsername == null || peerUsername.trim().isEmpty()) {
            peerUsername = getString(R.string.chat_unknown_peer);
        }

        setContentView(R.layout.activity_chat);
        chatManager = NearbyChatManager.getInstance(getApplicationContext());
        repository = ChatRepositoryImpl.getInstance(getApplicationContext());

        TextView peerName = findViewById(R.id.chat_peer_name);
        connectionStatus = findViewById(R.id.chat_connection_status);
        peerAvatar = findViewById(R.id.chat_peer_avatar);
        peerAvatarPhoto = findViewById(R.id.chat_peer_avatar_photo);
        peerName.setText(peerUsername);
        peerAvatar.setText(initialFor(peerUsername));
        updatePeerAvatar();
        connectionStatus.setText(chatManager.isConnected(peerUserId)
                ? R.string.chat_status_connected
                : R.string.chat_status_disconnected);

        messageList = findViewById(R.id.message_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messageList.setLayoutManager(layoutManager);
        messageAdapter = new MessageAdapter();
        messageList.setAdapter(messageAdapter);

        messageInput = findViewById(R.id.chat_message_input);
        ImageButton back = findViewById(R.id.chat_back);
        back.setOnClickListener(view -> finish());
        findViewById(R.id.chat_send_button).setOnClickListener(view -> sendMessage());
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                findViewById(R.id.chat_send_button).setEnabled(value.toString().trim().length() > 0);
            }

            @Override
            public void afterTextChanged(Editable value) {}
        });
        messageInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
        loadHistory();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (chatManager == null || peerUserId == null) return;
        chatManager.addCallback(this);
        chatManager.setActiveConversation(peerUserId);
        ChatNotifications.cancel(this, peerUserId);
        repository.markConversationRead(peerUserId, new ChatRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {}

            @Override
            public void onError(@NonNull Exception error) {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(ChatActivity.this, R.string.chat_storage_error,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        chatManager.retryPendingForPeer(peerUserId);
    }

    @Override
    protected void onStop() {
        if (chatManager != null) {
            chatManager.clearActiveConversation(peerUserId);
            chatManager.removeCallback(this);
        }
        super.onStop();
    }

    private void sendMessage() {
        String body = messageInput.getText().toString().trim();
        if (body.isEmpty()) return;
        findViewById(R.id.chat_send_button).setEnabled(false);
        chatManager.sendText(peerUserId, body, new ChatRepository.Callback<Message>() {
            @Override
            public void onSuccess(Message message) {
                if (isFinishing() || isDestroyed()) return;
                messageInput.setText("");
                messageAdapter.addOrUpdate(message);
                scrollToLatest();
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (isFinishing() || isDestroyed()) return;
                findViewById(R.id.chat_send_button).setEnabled(true);
                Toast.makeText(ChatActivity.this, R.string.message_send_error,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadHistory() {
        repository.getMessages(peerUserId, new ChatRepository.Callback<List<Message>>() {
            @Override
            public void onSuccess(List<Message> messages) {
                if (isFinishing() || isDestroyed()) return;
                messageAdapter.mergeHistory(messages);
                updateEmptyState();
                scrollToLatest();
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(ChatActivity.this, R.string.chat_storage_error,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onConnectionEstablished(@NonNull PeerIdentity peer) {
        if (!peer.userId.equals(peerUserId)) return;
        updatePeerName(peer.username);
        connectionStatus.setText(R.string.chat_status_connected);
    }

    @Override
    public void onPeerIdentityReceived(@NonNull String endpointId,
                                       @NonNull PeerIdentity peer) {
        if (!peer.userId.equals(peerUserId)) return;
        updatePeerName(peer.username);
        connectionStatus.setText(R.string.chat_status_connected);
    }

    @Override
    public void onPeerAvatarUpdated(@NonNull String updatedPeerUserId) {
        if (peerUserId.equals(updatedPeerUserId)) {
            updatePeerAvatar();
        }
    }

    private void updatePeerName(@NonNull String username) {
        peerUsername = username;
        ((TextView) findViewById(R.id.chat_peer_name)).setText(peerUsername);
        peerAvatar.setText(initialFor(peerUsername));
        updatePeerAvatar();
    }

    private void updatePeerAvatar() {
        android.net.Uri avatarUri = PeerAvatarStore.getPeerAvatarUri(this, peerUserId);
        if (avatarUri == null) {
            peerAvatarPhoto.setVisibility(android.view.View.GONE);
            peerAvatar.setVisibility(android.view.View.VISIBLE);
        } else {
            peerAvatarPhoto.setImageURI(null);
            peerAvatarPhoto.setImageURI(avatarUri);
            peerAvatarPhoto.setVisibility(android.view.View.VISIBLE);
            peerAvatar.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    public void onConnectionDisconnected(@NonNull String disconnectedPeerUserId) {
        if (peerUserId.equals(disconnectedPeerUserId)) {
            connectionStatus.setText(R.string.chat_status_disconnected);
        }
    }

    @Override
    public void onMessagePersisted(@NonNull Message message) {
        if (peerUserId.equals(message.conversationId)) {
            messageAdapter.addOrUpdate(message);
            updateEmptyState();
            scrollToLatest();
        }
    }

    @Override
    public void onMessageSent(@NonNull Message message) {
        if (peerUserId.equals(message.conversationId)) {
            messageAdapter.updateDeliveryStatus(message.messageId,
                    MessageDeliveryStatus.SENT.name());
        }
    }

    @Override
    public void onMessageDelivered(@NonNull String messageId) {
        messageAdapter.updateDeliveryStatus(messageId, MessageDeliveryStatus.DELIVERED.name());
    }

    @Override
    public void onMessageReceived(@NonNull Message message) {
        if (peerUserId.equals(message.conversationId)) {
            messageAdapter.addOrUpdate(message);
            updateEmptyState();
            scrollToLatest();
        }
    }

    @Override
    public void onMessageFailed(@NonNull String messageId, @NonNull String reason) {
        messageAdapter.updateDeliveryStatus(messageId, MessageDeliveryStatus.FAILED.name());
    }

    @Override
    public void onMessagingError(@NonNull String message, @Nullable Throwable cause) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void scrollToLatest() {
        int lastPosition = messageAdapter.getItemCount() - 1;
        if (lastPosition >= 0) {
            messageList.post(() -> messageList.scrollToPosition(lastPosition));
        }
    }

    private void updateEmptyState() {
        findViewById(R.id.message_empty_state).setVisibility(
                messageAdapter.getItemCount() == 0 ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    @NonNull
    private String initialFor(@NonNull String username) {
        String value = username.trim();
        return value.isEmpty() ? "?" : value.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }
}

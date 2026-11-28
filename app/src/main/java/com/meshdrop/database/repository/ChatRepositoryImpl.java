package com.meshdrop.database.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.meshdrop.data.model.Conversation;
import com.meshdrop.data.model.Message;
import com.meshdrop.data.model.PeerIdentity;
import com.meshdrop.database.MeshDropDatabase;
import com.meshdrop.database.dao.ConversationDao;
import com.meshdrop.database.dao.MessageDao;
import com.meshdrop.database.entity.ConversationEntity;
import com.meshdrop.database.entity.MessageEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

public final class ChatRepositoryImpl implements ChatRepository {
    private static volatile ChatRepositoryImpl instance;

    private final MeshDropDatabase database;
    private final ConversationDao conversationDao;
    private final MessageDao messageDao;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    private ChatRepositoryImpl(@NonNull Context context) {
        database = MeshDropDatabase.getInstance(context);
        conversationDao = new ConversationDao(database);
        messageDao = new MessageDao(database);
    }

    @NonNull
    public static ChatRepositoryImpl getInstance(@NonNull Context context) {
        ChatRepositoryImpl current = instance;
        if (current == null) {
            synchronized (ChatRepositoryImpl.class) {
                current = instance;
                if (current == null) {
                    current = new ChatRepositoryImpl(context.getApplicationContext());
                    instance = current;
                }
            }
        }
        return current;
    }

    @Override
    public void saveMessage(
            @NonNull String peerId,
            @NonNull String peerName,
            @NonNull String body,
            boolean outgoing,
            long createdAt,
            @NonNull Callback<Void> callback) {
        execute(() -> {
            SQLiteDatabase sqliteDatabase = database.getWritableDatabase();
            sqliteDatabase.beginTransaction();
            try {
                ConversationEntity conversation = new ConversationEntity(peerId, peerName, createdAt);
                conversationDao.insertIfMissing(conversation);
                conversationDao.updateLastMessage(peerId, body, "TEXT", createdAt);
                messageDao.insert(new MessageEntity(-1, UUID.randomUUID().toString(), peerId,
                    outgoing ? "legacy-local" : peerId,
                    outgoing ? peerId : "legacy-local", body, outgoing, createdAt,
                    outgoing ? "PENDING" : "DELIVERED", "TEXT"));
                sqliteDatabase.setTransactionSuccessful();
            } finally {
                sqliteDatabase.endTransaction();
            }
            return null;
        }, callback);
    }

    @Override
    public void getConversations(@NonNull Callback<List<Conversation>> callback) {
        execute(() -> {
            List<ConversationEntity> entities = conversationDao.getAll();
            List<Conversation> conversations = new ArrayList<>(entities.size());
            for (ConversationEntity entity : entities) {
                conversations.add(conversationFrom(entity));
            }
            return conversations;
        }, callback);
    }

    @Override
    public void getConversation(
            @NonNull String peerId,
            @NonNull Callback<Conversation> callback) {
        execute(() -> {
            ConversationEntity entity = conversationDao.getByPeerId(peerId);
            if (entity == null) {
                return null;
            }
                return conversationFrom(entity);
        }, callback);
    }

    @Override
    public void getMessages(
            @NonNull String peerId,
            @NonNull Callback<List<Message>> callback) {
        execute(() -> {
            List<MessageEntity> entities = messageDao.getForPeer(peerId);
            List<Message> messages = new ArrayList<>(entities.size());
            for (MessageEntity entity : entities) {
                messages.add(messageFrom(entity));
            }
            return messages;
        }, callback);
    }

    @Override
    public void upsertPeerIdentity(@NonNull PeerIdentity peer,
                                  @NonNull Callback<Conversation> callback) {
        execute(() -> {
            SQLiteDatabase sqliteDatabase = database.getWritableDatabase();
            sqliteDatabase.beginTransaction();
            try {
                ConversationEntity existing = conversationDao.getByPeerId(peer.userId);
                long now = System.currentTimeMillis();
                ConversationEntity updated = existing == null
                        ? new ConversationEntity(peer.userId, peer.userId, peer.username,
                        "", "TEXT", 0, now, now)
                        : new ConversationEntity(peer.userId, peer.userId, peer.username,
                        existing.lastMessage, existing.lastMessageType,
                        existing.lastMessageTimestamp, existing.createdAt, now);
                conversationDao.upsert(updated);
                sqliteDatabase.setTransactionSuccessful();
                return conversationFrom(updated);
            } finally {
                sqliteDatabase.endTransaction();
            }
        }, callback);
    }

    @Override
    public void saveOutgoingMessage(@NonNull PeerIdentity localIdentity,
                                    @NonNull PeerIdentity peerIdentity,
                                    @NonNull String messageId,
                                    @NonNull String body,
                                    long timestamp,
                                    @NonNull Callback<Message> callback) {
        execute(() -> {
            SQLiteDatabase sqliteDatabase = database.getWritableDatabase();
            sqliteDatabase.beginTransaction();
            try {
                ConversationEntity existing = conversationDao.getByPeerId(peerIdentity.userId);
                ConversationEntity conversation = new ConversationEntity(
                        peerIdentity.userId, peerIdentity.userId, peerIdentity.username,
                        body, "TEXT", timestamp,
                        existing == null ? timestamp : existing.createdAt, timestamp);
                conversationDao.upsert(conversation);
                MessageEntity message = new MessageEntity(-1, messageId,
                        peerIdentity.userId, localIdentity.userId, peerIdentity.userId,
                        body, true, timestamp, "PENDING", "TEXT");
                messageDao.insert(message);
                sqliteDatabase.setTransactionSuccessful();
                return messageFrom(message);
            } finally {
                sqliteDatabase.endTransaction();
            }
        }, callback);
    }

    @Override
    public void saveIncomingMessage(@NonNull String messageId,
                                    @NonNull String senderUserId,
                                    @NonNull String senderUsername,
                                    @NonNull String receiverUserId,
                                    @NonNull String body,
                                    long timestamp,
                                    boolean isRead,
                                    @NonNull Callback<Boolean> callback) {
        execute(() -> {
            SQLiteDatabase sqliteDatabase = database.getWritableDatabase();
            sqliteDatabase.beginTransaction();
            try {
                if (messageDao.getByMessageId(messageId) != null) {
                    sqliteDatabase.setTransactionSuccessful();
                    return false;
                }
                ConversationEntity existing = conversationDao.getByPeerId(senderUserId);
                ConversationEntity conversation = new ConversationEntity(
                        senderUserId, senderUserId, senderUsername,
                        body, "TEXT", timestamp,
                        existing == null ? timestamp : existing.createdAt,
                        Math.max(timestamp, existing == null ? timestamp : existing.updatedAt));
                conversationDao.upsert(conversation);
                MessageEntity message = new MessageEntity(-1, messageId,
                        senderUserId, senderUserId, receiverUserId,
                    body, false, timestamp, "DELIVERED", "TEXT", isRead);
                long rowId = messageDao.insertIfMissing(message);
                sqliteDatabase.setTransactionSuccessful();
                return rowId >= 0;
            } finally {
                sqliteDatabase.endTransaction();
            }
        }, callback);
    }

    @Override
    public void markConversationRead(@NonNull String peerId, @NonNull Callback<Void> callback) {
        execute(() -> {
            messageDao.markConversationRead(peerId);
            return null;
        }, callback);
    }

    @Override
    public void updateDeliveryStatus(@NonNull String messageId,
                                     @NonNull String deliveryStatus,
                                     @NonNull Callback<Void> callback) {
        execute(() -> {
            messageDao.updateDeliveryStatus(messageId, deliveryStatus);
            return null;
        }, callback);
    }

    @Override
    public void updateOutgoingDeliveryStatusForPeer(@NonNull String messageId,
                                                   @NonNull String peerUserId,
                                                   @NonNull String deliveryStatus,
                                                   @NonNull Callback<Integer> callback) {
        execute(() -> messageDao.updateOutgoingDeliveryStatusForPeer(
                messageId, peerUserId, deliveryStatus), callback);
    }

    @Override
    public void getPendingMessages(@NonNull Callback<List<Message>> callback) {
        execute(() -> {
            List<MessageEntity> entities = messageDao.getPendingMessages();
            List<Message> messages = new ArrayList<>(entities.size());
            for (MessageEntity entity : entities) messages.add(messageFrom(entity));
            return messages;
        }, callback);
    }

    @Override
    public void deleteConversation(@NonNull String peerId, @NonNull Callback<Void> callback) {
        execute(() -> {
            conversationDao.delete(peerId);
            return null;
        }, callback);
    }

    private <T> void execute(
            @NonNull Callable<T> operation,
            @NonNull Callback<T> callback) {
        databaseExecutor.execute(() -> {
            try {
                T result = operation.call();
                mainThreadHandler.post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                mainThreadHandler.post(() -> callback.onError(error));
            }
        });
    }

    @NonNull
    private Conversation conversationFrom(@NonNull ConversationEntity entity) {
        return new Conversation(entity.conversationId, entity.peerUserId, entity.peerUsername,
                entity.lastMessage, entity.lastMessageTimestamp, entity.createdAt,
            entity.updatedAt, entity.lastMessageType, entity.unreadCount);
    }

    @NonNull
    private Message messageFrom(@NonNull MessageEntity entity) {
        return new Message(entity.messageId, entity.conversationId, entity.senderUserId,
                entity.receiverUserId, entity.messageContent, entity.isOutgoing,
            entity.timestamp, entity.deliveryStatus, entity.messageType, entity.isRead);
    }
}
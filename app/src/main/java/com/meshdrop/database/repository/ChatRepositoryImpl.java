package com.meshdrop.database.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.meshdrop.data.model.Conversation;
import com.meshdrop.data.model.Message;
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
                conversationDao.update(conversation);
                messageDao.insert(new MessageEntity(-1, peerId, body, outgoing, createdAt));
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
            List<String> peerIds = new ArrayList<>(entities.size());
            for (ConversationEntity entity : entities) {
                peerIds.add(entity.peerId);
            }
            Map<String, MessageEntity> latestMessages = messageDao.getLatestForPeers(peerIds);
            List<Conversation> conversations = new ArrayList<>(entities.size());
            for (ConversationEntity entity : entities) {
                MessageEntity latest = latestMessages.get(entity.peerId);
                conversations.add(new Conversation(
                        entity.peerId,
                        entity.peerName,
                        latest == null ? "" : latest.body,
                        entity.updatedAt));
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
            MessageEntity latest = messageDao.getLatestForPeer(peerId);
            return new Conversation(
                    entity.peerId,
                    entity.peerName,
                    latest == null ? "" : latest.body,
                    entity.updatedAt);
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
                messages.add(new Message(entity.body, entity.outgoing, entity.createdAt));
            }
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
}
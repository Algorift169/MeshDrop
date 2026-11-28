package com.meshdrop.database.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.meshdrop.database.MeshDropDatabase;
import com.meshdrop.database.entity.ConversationEntity;

import java.util.ArrayList;
import java.util.List;

public final class ConversationDao {
    private final MeshDropDatabase database;

    public ConversationDao(@NonNull MeshDropDatabase database) {
        this.database = database;
    }

    public long insertIfMissing(@NonNull ConversationEntity conversation) {
        ContentValues values = valuesFor(conversation);
        return database.getWritableDatabase().insertWithOnConflict(
                MeshDropDatabase.TABLE_CONVERSATIONS,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    public int update(@NonNull ConversationEntity conversation) {
        ContentValues values = valuesFor(conversation);
        values.remove(MeshDropDatabase.COLUMN_PEER_ID);
        return database.getWritableDatabase().update(
                MeshDropDatabase.TABLE_CONVERSATIONS,
                values,
                MeshDropDatabase.COLUMN_PEER_ID + " = ?",
                new String[] {conversation.peerId});
    }

    public int updateTimestamp(@NonNull String peerId, long updatedAt) {
        ContentValues values = new ContentValues();
        values.put(MeshDropDatabase.COLUMN_UPDATED_AT, updatedAt);
        return database.getWritableDatabase().update(
                MeshDropDatabase.TABLE_CONVERSATIONS,
                values,
                MeshDropDatabase.COLUMN_PEER_ID + " = ?",
                new String[] {peerId});
    }

    @Nullable
    public ConversationEntity getByPeerId(@NonNull String peerId) {
        try (Cursor cursor = database.getReadableDatabase().query(
                MeshDropDatabase.TABLE_CONVERSATIONS,
                conversationColumns(),
                MeshDropDatabase.COLUMN_PEER_ID + " = ?",
                new String[] {peerId},
                null,
                null,
                null)) {
            return cursor.moveToFirst() ? entityFrom(cursor) : null;
        }
    }

    @Nullable
    public ConversationEntity getByConversationId(@NonNull String conversationId) {
        return getByPeerId(conversationId);
    }

    public long upsert(@NonNull ConversationEntity conversation) {
        long insertedId = insertIfMissing(conversation);
        if (insertedId < 0) {
            update(conversation);
            return 0;
        }
        return insertedId;
    }

    public int updateLastMessage(@NonNull String peerUserId,
                                 @NonNull String preview,
                                 @NonNull String messageType,
                                 long timestamp) {
        ContentValues values = new ContentValues();
        values.put(MeshDropDatabase.COLUMN_LAST_MESSAGE, preview);
        values.put(MeshDropDatabase.COLUMN_LAST_MESSAGE_TYPE, messageType);
        values.put(MeshDropDatabase.COLUMN_LAST_MESSAGE_TIMESTAMP, timestamp);
        values.put(MeshDropDatabase.COLUMN_UPDATED_AT, timestamp);
        return database.getWritableDatabase().update(
                MeshDropDatabase.TABLE_CONVERSATIONS,
                values,
                MeshDropDatabase.COLUMN_PEER_ID + " = ?",
                new String[] {peerUserId});
    }

    @NonNull
    public List<ConversationEntity> getAll() {
        List<ConversationEntity> conversations = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                MeshDropDatabase.TABLE_CONVERSATIONS,
                conversationColumns(),
                null,
                null,
                null,
                null,
                "unread_count DESC, " + MeshDropDatabase.COLUMN_UPDATED_AT + " DESC, "
                        + MeshDropDatabase.COLUMN_PEER_NAME + " COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) {
                conversations.add(entityFrom(cursor));
            }
        }
        return conversations;
    }

    public int delete(@NonNull String peerId) {
        return database.getWritableDatabase().delete(
                MeshDropDatabase.TABLE_CONVERSATIONS,
                MeshDropDatabase.COLUMN_PEER_ID + " = ?",
                new String[] {peerId});
    }

    @NonNull
    private ContentValues valuesFor(@NonNull ConversationEntity conversation) {
        ContentValues values = new ContentValues();
        values.put(MeshDropDatabase.COLUMN_PEER_ID, conversation.peerId);
        values.put(MeshDropDatabase.COLUMN_PEER_NAME, conversation.peerUsername);
        values.put(MeshDropDatabase.COLUMN_UPDATED_AT, conversation.updatedAt);
        values.put(MeshDropDatabase.COLUMN_LAST_MESSAGE, conversation.lastMessage);
        values.put(MeshDropDatabase.COLUMN_LAST_MESSAGE_TYPE, conversation.lastMessageType);
        values.put(MeshDropDatabase.COLUMN_LAST_MESSAGE_TIMESTAMP,
            conversation.lastMessageTimestamp);
        values.put(MeshDropDatabase.COLUMN_CREATED_AT, conversation.createdAt);
        return values;
    }

    @NonNull
    private ConversationEntity entityFrom(@NonNull Cursor cursor) {
        return new ConversationEntity(
                cursor.getString(0),
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getLong(4),
                cursor.getLong(5),
                cursor.getLong(6),
                cursor.getInt(7));
            }

            @NonNull
            private String[] conversationColumns() {
            return new String[] {
                MeshDropDatabase.COLUMN_PEER_ID,
                MeshDropDatabase.COLUMN_PEER_NAME,
                MeshDropDatabase.COLUMN_LAST_MESSAGE,
                MeshDropDatabase.COLUMN_LAST_MESSAGE_TYPE,
                MeshDropDatabase.COLUMN_LAST_MESSAGE_TIMESTAMP,
                MeshDropDatabase.COLUMN_CREATED_AT,
                MeshDropDatabase.COLUMN_UPDATED_AT,
                "(SELECT COUNT(*) FROM " + MeshDropDatabase.TABLE_MESSAGES
                    + " WHERE " + MeshDropDatabase.TABLE_MESSAGES + "."
                    + MeshDropDatabase.COLUMN_PEER_ID + " = "
                    + MeshDropDatabase.TABLE_CONVERSATIONS + "."
                    + MeshDropDatabase.COLUMN_PEER_ID + " AND "
                    + MeshDropDatabase.COLUMN_OUTGOING + " = 0 AND "
                    + MeshDropDatabase.COLUMN_IS_READ + " = 0) AS unread_count"
            };
    }
}
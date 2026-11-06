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
                new String[] {
                        MeshDropDatabase.COLUMN_PEER_ID,
                        MeshDropDatabase.COLUMN_PEER_NAME,
                        MeshDropDatabase.COLUMN_UPDATED_AT
                },
                MeshDropDatabase.COLUMN_PEER_ID + " = ?",
                new String[] {peerId},
                null,
                null,
                null)) {
            return cursor.moveToFirst() ? entityFrom(cursor) : null;
        }
    }

    @NonNull
    public List<ConversationEntity> getAll() {
        List<ConversationEntity> conversations = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                MeshDropDatabase.TABLE_CONVERSATIONS,
                new String[] {
                        MeshDropDatabase.COLUMN_PEER_ID,
                        MeshDropDatabase.COLUMN_PEER_NAME,
                        MeshDropDatabase.COLUMN_UPDATED_AT
                },
                null,
                null,
                null,
                null,
                MeshDropDatabase.COLUMN_UPDATED_AT + " DESC, "
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
        values.put(MeshDropDatabase.COLUMN_PEER_NAME, conversation.peerName);
        values.put(MeshDropDatabase.COLUMN_UPDATED_AT, conversation.updatedAt);
        return values;
    }

    @NonNull
    private ConversationEntity entityFrom(@NonNull Cursor cursor) {
        return new ConversationEntity(
                cursor.getString(0), cursor.getString(1), cursor.getLong(2));
    }
}
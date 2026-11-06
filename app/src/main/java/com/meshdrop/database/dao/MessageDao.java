package com.meshdrop.database.dao;

import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.meshdrop.database.MeshDropDatabase;
import com.meshdrop.database.entity.MessageEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MessageDao {
    private final MeshDropDatabase database;

    public MessageDao(@NonNull MeshDropDatabase database) {
        this.database = database;
    }

    public long insert(@NonNull MessageEntity message) {
        ContentValues values = new ContentValues();
        values.put(MeshDropDatabase.COLUMN_PEER_ID, message.peerId);
        values.put(MeshDropDatabase.COLUMN_BODY, message.body);
        values.put(MeshDropDatabase.COLUMN_OUTGOING, message.outgoing ? 1 : 0);
        values.put(MeshDropDatabase.COLUMN_CREATED_AT, message.createdAt);
        return database.getWritableDatabase().insertOrThrow(
                MeshDropDatabase.TABLE_MESSAGES, null, values);
    }

    @NonNull
    public List<MessageEntity> getForPeer(@NonNull String peerId) {
        List<MessageEntity> messages = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                MeshDropDatabase.TABLE_MESSAGES,
                new String[] {
                        MeshDropDatabase.COLUMN_MESSAGE_ID,
                        MeshDropDatabase.COLUMN_PEER_ID,
                        MeshDropDatabase.COLUMN_BODY,
                        MeshDropDatabase.COLUMN_OUTGOING,
                        MeshDropDatabase.COLUMN_CREATED_AT
                },
                MeshDropDatabase.COLUMN_PEER_ID + " = ?",
                new String[] {peerId},
                null,
                null,
                MeshDropDatabase.COLUMN_CREATED_AT + " ASC, "
                        + MeshDropDatabase.COLUMN_MESSAGE_ID + " ASC")) {
            while (cursor.moveToNext()) {
                messages.add(entityFrom(cursor));
            }
        }
        return messages;
    }

    @Nullable
    public MessageEntity getLatestForPeer(@NonNull String peerId) {
        try (Cursor cursor = database.getReadableDatabase().query(
                MeshDropDatabase.TABLE_MESSAGES,
                new String[] {
                        MeshDropDatabase.COLUMN_MESSAGE_ID,
                        MeshDropDatabase.COLUMN_PEER_ID,
                        MeshDropDatabase.COLUMN_BODY,
                        MeshDropDatabase.COLUMN_OUTGOING,
                        MeshDropDatabase.COLUMN_CREATED_AT
                },
                MeshDropDatabase.COLUMN_PEER_ID + " = ?",
                new String[] {peerId},
                null,
                null,
                MeshDropDatabase.COLUMN_MESSAGE_ID + " DESC",
                "1")) {
            return cursor.moveToFirst() ? entityFrom(cursor) : null;
        }
    }

    @NonNull
    public Map<String, MessageEntity> getLatestForPeers(@NonNull List<String> peerIds) {
        Map<String, MessageEntity> latestMessages = new HashMap<>();
        if (peerIds.isEmpty()) {
            return latestMessages;
        }

        StringBuilder placeholders = new StringBuilder();
        String[] selectionArgs = new String[peerIds.size()];
        for (int index = 0; index < peerIds.size(); index++) {
            if (index > 0) {
                placeholders.append(", ");
            }
            placeholders.append('?');
            selectionArgs[index] = peerIds.get(index);
        }
        String query = "SELECT m." + MeshDropDatabase.COLUMN_MESSAGE_ID
                + ", m." + MeshDropDatabase.COLUMN_PEER_ID
                + ", m." + MeshDropDatabase.COLUMN_BODY
                + ", m." + MeshDropDatabase.COLUMN_OUTGOING
                + ", m." + MeshDropDatabase.COLUMN_CREATED_AT
                + " FROM " + MeshDropDatabase.TABLE_MESSAGES + " m"
                + " INNER JOIN (SELECT " + MeshDropDatabase.COLUMN_PEER_ID
                + ", MAX(" + MeshDropDatabase.COLUMN_MESSAGE_ID + ") AS latest_id FROM "
                + MeshDropDatabase.TABLE_MESSAGES + " WHERE "
                + MeshDropDatabase.COLUMN_PEER_ID + " IN (" + placeholders + ") GROUP BY "
                + MeshDropDatabase.COLUMN_PEER_ID + ") latest ON m."
                + MeshDropDatabase.COLUMN_MESSAGE_ID + " = latest.latest_id";
        try (Cursor cursor = database.getReadableDatabase().rawQuery(query, selectionArgs)) {
            while (cursor.moveToNext()) {
                MessageEntity message = entityFrom(cursor);
                latestMessages.put(message.peerId, message);
            }
        }
        return latestMessages;
    }

    public int deleteForPeer(@NonNull String peerId) {
        return database.getWritableDatabase().delete(
                MeshDropDatabase.TABLE_MESSAGES,
                MeshDropDatabase.COLUMN_PEER_ID + " = ?",
                new String[] {peerId});
    }

    @NonNull
    private MessageEntity entityFrom(@NonNull Cursor cursor) {
        return new MessageEntity(
                cursor.getLong(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getInt(3) != 0,
                cursor.getLong(4));
    }
}
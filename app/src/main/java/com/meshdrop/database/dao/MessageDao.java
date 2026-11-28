package com.meshdrop.database.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

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
        return insertMessage(message, false);
    }

    public long insertIfMissing(@NonNull MessageEntity message) {
        return insertMessage(message, true);
    }

    private long insertMessage(@NonNull MessageEntity message, boolean ignoreDuplicate) {
        ContentValues values = new ContentValues();
        values.put(MeshDropDatabase.COLUMN_PEER_ID, message.conversationId);
        values.put(MeshDropDatabase.COLUMN_BODY, message.messageContent);
        values.put(MeshDropDatabase.COLUMN_OUTGOING, message.isOutgoing ? 1 : 0);
        values.put(MeshDropDatabase.COLUMN_CREATED_AT, message.timestamp);
        values.put(MeshDropDatabase.COLUMN_MESSAGE_UUID, message.messageId);
        values.put(MeshDropDatabase.COLUMN_SENDER_USER_ID, message.senderUserId);
        values.put(MeshDropDatabase.COLUMN_RECEIVER_USER_ID, message.receiverUserId);
        values.put(MeshDropDatabase.COLUMN_DELIVERY_STATUS, message.deliveryStatus);
        values.put(MeshDropDatabase.COLUMN_MESSAGE_TYPE, message.messageType);
        values.put(MeshDropDatabase.COLUMN_IS_READ, message.isRead ? 1 : 0);
        if (ignoreDuplicate) {
            return database.getWritableDatabase().insertWithOnConflict(
                    MeshDropDatabase.TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
        return database.getWritableDatabase().insertOrThrow(MeshDropDatabase.TABLE_MESSAGES, null, values);
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
                        MeshDropDatabase.COLUMN_CREATED_AT,
                        MeshDropDatabase.COLUMN_MESSAGE_UUID,
                        MeshDropDatabase.COLUMN_SENDER_USER_ID,
                        MeshDropDatabase.COLUMN_RECEIVER_USER_ID,
                        MeshDropDatabase.COLUMN_DELIVERY_STATUS,
                        MeshDropDatabase.COLUMN_MESSAGE_TYPE
                        , MeshDropDatabase.COLUMN_IS_READ
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
                        MeshDropDatabase.COLUMN_CREATED_AT,
                        MeshDropDatabase.COLUMN_MESSAGE_UUID,
                        MeshDropDatabase.COLUMN_SENDER_USER_ID,
                        MeshDropDatabase.COLUMN_RECEIVER_USER_ID,
                        MeshDropDatabase.COLUMN_DELIVERY_STATUS,
                        MeshDropDatabase.COLUMN_MESSAGE_TYPE
                        , MeshDropDatabase.COLUMN_IS_READ
                },
                MeshDropDatabase.COLUMN_PEER_ID + " = ?",
                new String[] {peerId},
                null,
                null,
                MeshDropDatabase.COLUMN_CREATED_AT + " DESC, "
                    + MeshDropDatabase.COLUMN_MESSAGE_ID + " DESC",
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
                + ", m." + MeshDropDatabase.COLUMN_MESSAGE_UUID
                + ", m." + MeshDropDatabase.COLUMN_SENDER_USER_ID
                + ", m." + MeshDropDatabase.COLUMN_RECEIVER_USER_ID
                + ", m." + MeshDropDatabase.COLUMN_DELIVERY_STATUS
                + ", m." + MeshDropDatabase.COLUMN_MESSAGE_TYPE
                + ", m." + MeshDropDatabase.COLUMN_IS_READ
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

    @Nullable
    public MessageEntity getByMessageId(@NonNull String messageId) {
        try (Cursor cursor = database.getReadableDatabase().query(
                MeshDropDatabase.TABLE_MESSAGES,
                messageColumns(),
                MeshDropDatabase.COLUMN_MESSAGE_UUID + " = ?",
                new String[] {messageId}, null, null, null, "1")) {
            return cursor.moveToFirst() ? entityFrom(cursor) : null;
        }
    }

    @NonNull
    public List<MessageEntity> getPendingMessages() {
        List<MessageEntity> messages = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                MeshDropDatabase.TABLE_MESSAGES,
                messageColumns(),
                MeshDropDatabase.COLUMN_DELIVERY_STATUS + " IN (?, ?)",
                new String[] {"PENDING", "SENDING"},
                null, null,
                MeshDropDatabase.COLUMN_CREATED_AT + " ASC, "
                        + MeshDropDatabase.COLUMN_MESSAGE_ID + " ASC")) {
            while (cursor.moveToNext()) messages.add(entityFrom(cursor));
        }
        return messages;
    }

    public int updateDeliveryStatus(@NonNull String messageId,
                                    @NonNull String deliveryStatus) {
        ContentValues values = new ContentValues();
        values.put(MeshDropDatabase.COLUMN_DELIVERY_STATUS, deliveryStatus);
        return database.getWritableDatabase().update(
                MeshDropDatabase.TABLE_MESSAGES,
                values,
                MeshDropDatabase.COLUMN_MESSAGE_UUID + " = ?",
                new String[] {messageId});
    }

        public int markConversationRead(@NonNull String peerId) {
        ContentValues values = new ContentValues();
        values.put(MeshDropDatabase.COLUMN_IS_READ, 1);
        return database.getWritableDatabase().update(
            MeshDropDatabase.TABLE_MESSAGES,
            values,
            MeshDropDatabase.COLUMN_PEER_ID + " = ? AND "
                + MeshDropDatabase.COLUMN_OUTGOING + " = 0 AND "
                + MeshDropDatabase.COLUMN_IS_READ + " = 0",
            new String[] {peerId});
        }

            public int updateOutgoingDeliveryStatusForPeer(@NonNull String messageId,
                                   @NonNull String peerUserId,
                                   @NonNull String deliveryStatus) {
            ContentValues values = new ContentValues();
            values.put(MeshDropDatabase.COLUMN_DELIVERY_STATUS, deliveryStatus);
            return database.getWritableDatabase().update(
                MeshDropDatabase.TABLE_MESSAGES,
                values,
                MeshDropDatabase.COLUMN_MESSAGE_UUID + " = ? AND "
                    + MeshDropDatabase.COLUMN_RECEIVER_USER_ID + " = ? AND "
                    + MeshDropDatabase.COLUMN_OUTGOING + " = 1",
                new String[] {messageId, peerUserId});
            }

    @NonNull
    private String[] messageColumns() {
        return new String[] {
                MeshDropDatabase.COLUMN_MESSAGE_ID,
                MeshDropDatabase.COLUMN_PEER_ID,
                MeshDropDatabase.COLUMN_BODY,
                MeshDropDatabase.COLUMN_OUTGOING,
                MeshDropDatabase.COLUMN_CREATED_AT,
                MeshDropDatabase.COLUMN_MESSAGE_UUID,
                MeshDropDatabase.COLUMN_SENDER_USER_ID,
                MeshDropDatabase.COLUMN_RECEIVER_USER_ID,
                MeshDropDatabase.COLUMN_DELIVERY_STATUS,
                MeshDropDatabase.COLUMN_MESSAGE_TYPE
                , MeshDropDatabase.COLUMN_IS_READ
        };
    }

    @NonNull
    private MessageEntity entityFrom(@NonNull Cursor cursor) {
        return new MessageEntity(
                cursor.getLong(0),
            cursor.getString(5),
            cursor.getString(1),
            cursor.getString(6),
            cursor.getString(7),
                cursor.getString(2),
                cursor.getInt(3) != 0,
            cursor.getLong(4),
            cursor.getString(8),
            cursor.getString(9),
            cursor.getInt(10) != 0);
    }
}
package com.meshdrop.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class ChatDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "meshdrop_chats.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_CONVERSATIONS = "conversations";
    private static final String TABLE_MESSAGES = "messages";

    public ChatDatabase(@NonNull Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(@NonNull SQLiteDatabase database) {
        database.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase database) {
        database.execSQL("CREATE TABLE " + TABLE_CONVERSATIONS + " ("
                + "peer_id TEXT PRIMARY KEY NOT NULL, "
                + "peer_name TEXT NOT NULL, "
                + "updated_at INTEGER NOT NULL)");
        database.execSQL("CREATE TABLE " + TABLE_MESSAGES + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "peer_id TEXT NOT NULL, "
                + "body TEXT NOT NULL, "
                + "outgoing INTEGER NOT NULL, "
                + "created_at INTEGER NOT NULL, "
                + "FOREIGN KEY(peer_id) REFERENCES " + TABLE_CONVERSATIONS
                + "(peer_id) ON DELETE CASCADE)");
        database.execSQL("CREATE INDEX messages_peer_created_at ON " + TABLE_MESSAGES
                + "(peer_id, created_at, _id)");
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase database, int oldVersion, int newVersion) {
    }

    public void saveMessage(
            @NonNull String peerId,
            @NonNull String peerName,
            @NonNull String body,
            boolean outgoing,
            long createdAt) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            ContentValues conversation = new ContentValues();
            conversation.put("peer_id", peerId);
            conversation.put("peer_name", peerName);
            conversation.put("updated_at", createdAt);
            database.insertWithOnConflict(
                    TABLE_CONVERSATIONS, null, conversation, SQLiteDatabase.CONFLICT_IGNORE);
            database.update(TABLE_CONVERSATIONS, conversation, "peer_id = ?",
                    new String[] {peerId});

            ContentValues message = new ContentValues();
            message.put("peer_id", peerId);
            message.put("body", body);
            message.put("outgoing", outgoing ? 1 : 0);
            message.put("created_at", createdAt);
            database.insertOrThrow(TABLE_MESSAGES, null, message);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    @NonNull
    public List<Conversation> getConversations() {
        List<Conversation> conversations = new ArrayList<>();
        String query = "SELECT c.peer_id, c.peer_name, c.updated_at, "
                + "(SELECT m.body FROM " + TABLE_MESSAGES + " m "
                + "WHERE m.peer_id = c.peer_id ORDER BY m._id DESC LIMIT 1) AS last_message "
                + "FROM " + TABLE_CONVERSATIONS + " c "
                + "ORDER BY c.updated_at DESC, c.peer_name COLLATE NOCASE ASC";
        try (Cursor cursor = getReadableDatabase().rawQuery(query, null)) {
            while (cursor.moveToNext()) {
                conversations.add(new Conversation(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(3),
                        cursor.getLong(2)));
            }
        }
        return conversations;
    }

    @NonNull
    public List<Message> getMessages(@NonNull String peerId) {
        List<Message> messages = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_MESSAGES,
                new String[] {"body", "outgoing", "created_at"},
                "peer_id = ?",
                new String[] {peerId},
                null,
                null,
                "created_at ASC, _id ASC")) {
            while (cursor.moveToNext()) {
                messages.add(new Message(
                        cursor.getString(0), cursor.getInt(1) != 0, cursor.getLong(2)));
            }
        }
        return messages;
    }

    public static final class Conversation {
        @NonNull public final String peerId;
        @NonNull public final String peerName;
        @NonNull public final String lastMessage;
        public final long updatedAt;

        private Conversation(
                @NonNull String peerId,
                @NonNull String peerName,
                @NonNull String lastMessage,
                long updatedAt) {
            this.peerId = peerId;
            this.peerName = peerName;
            this.lastMessage = lastMessage;
            this.updatedAt = updatedAt;
        }
    }

    public static final class Message {
        @NonNull public final String body;
        public final boolean outgoing;
        public final long createdAt;

        private Message(@NonNull String body, boolean outgoing, long createdAt) {
            this.body = body;
            this.outgoing = outgoing;
            this.createdAt = createdAt;
        }
    }
}
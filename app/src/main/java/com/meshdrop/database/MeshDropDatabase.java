package com.meshdrop.database;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MeshDropDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "meshdrop_chats.db";
    private static final int DATABASE_VERSION = 3;

    public static final String TABLE_CONVERSATIONS = "conversations";
    public static final String TABLE_MESSAGES = "messages";
    public static final String COLUMN_PEER_ID = "peer_id";
    public static final String COLUMN_PEER_NAME = "peer_name";
    public static final String COLUMN_UPDATED_AT = "updated_at";
    public static final String COLUMN_MESSAGE_ID = "_id";
    public static final String COLUMN_BODY = "body";
    public static final String COLUMN_OUTGOING = "outgoing";
    public static final String COLUMN_CREATED_AT = "created_at";
    public static final String COLUMN_CONVERSATION_ID = COLUMN_PEER_ID;
    public static final String COLUMN_PEER_USER_ID = COLUMN_PEER_ID;
    public static final String COLUMN_PEER_USERNAME = COLUMN_PEER_NAME;
    public static final String COLUMN_LAST_MESSAGE = "last_message";
    public static final String COLUMN_LAST_MESSAGE_TIMESTAMP = "last_message_timestamp";
    public static final String COLUMN_LAST_MESSAGE_TYPE = "last_message_type";
    public static final String COLUMN_MESSAGE_UUID = "message_uuid";
    public static final String COLUMN_SENDER_USER_ID = "sender_user_id";
    public static final String COLUMN_RECEIVER_USER_ID = "receiver_user_id";
    public static final String COLUMN_DELIVERY_STATUS = "delivery_status";
    public static final String COLUMN_MESSAGE_TYPE = "message_type";
    public static final String COLUMN_IS_READ = "is_read";

    private static volatile MeshDropDatabase instance;

    private MeshDropDatabase(@NonNull Context context) {
        this(context, DATABASE_NAME);
    }

    MeshDropDatabase(@NonNull Context context, @NonNull String databaseName) {
        super(context.getApplicationContext(), databaseName, null, DATABASE_VERSION);
    }

    @NonNull
    public static MeshDropDatabase getInstance(@NonNull Context context) {
        MeshDropDatabase current = instance;
        if (current == null) {
            synchronized (MeshDropDatabase.class) {
                current = instance;
                if (current == null) {
                    current = new MeshDropDatabase(context);
                    instance = current;
                }
            }
        }
        return current;
    }

    @Override
    public void onConfigure(@NonNull SQLiteDatabase database) {
        database.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase database) {
        database.execSQL("CREATE TABLE " + TABLE_CONVERSATIONS + " ("
                + COLUMN_PEER_ID + " TEXT PRIMARY KEY NOT NULL, "
                + COLUMN_PEER_NAME + " TEXT NOT NULL, "
            + COLUMN_UPDATED_AT + " INTEGER NOT NULL, "
            + COLUMN_LAST_MESSAGE + " TEXT NOT NULL DEFAULT '', "
            + COLUMN_LAST_MESSAGE_TIMESTAMP + " INTEGER NOT NULL DEFAULT 0, "
            + COLUMN_LAST_MESSAGE_TYPE + " TEXT NOT NULL DEFAULT 'TEXT', "
            + COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0)");
        database.execSQL("CREATE TABLE " + TABLE_MESSAGES + " ("
                + COLUMN_MESSAGE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_PEER_ID + " TEXT NOT NULL, "
                + COLUMN_BODY + " TEXT NOT NULL, "
                + COLUMN_OUTGOING + " INTEGER NOT NULL, "
                + COLUMN_CREATED_AT + " INTEGER NOT NULL, "
            + COLUMN_MESSAGE_UUID + " TEXT NOT NULL UNIQUE, "
            + COLUMN_SENDER_USER_ID + " TEXT NOT NULL, "
            + COLUMN_RECEIVER_USER_ID + " TEXT NOT NULL, "
            + COLUMN_DELIVERY_STATUS + " TEXT NOT NULL, "
            + COLUMN_MESSAGE_TYPE + " TEXT NOT NULL DEFAULT 'TEXT', "
            + COLUMN_IS_READ + " INTEGER NOT NULL DEFAULT 1, "
                + "FOREIGN KEY(" + COLUMN_PEER_ID + ") REFERENCES " + TABLE_CONVERSATIONS
                + "(" + COLUMN_PEER_ID + ") ON DELETE CASCADE)");
        database.execSQL("CREATE INDEX messages_peer_created_at ON " + TABLE_MESSAGES
                + "(" + COLUMN_PEER_ID + ", " + COLUMN_CREATED_AT + ", " + COLUMN_MESSAGE_ID + ")");
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            migrateToVersionTwo(database);
        }
        if (oldVersion < 3) {
            database.execSQL("ALTER TABLE " + TABLE_MESSAGES + " ADD COLUMN "
                    + COLUMN_IS_READ + " INTEGER NOT NULL DEFAULT 1");
        }
        }

        private void migrateToVersionTwo(@NonNull SQLiteDatabase database) {
        database.execSQL("ALTER TABLE " + TABLE_CONVERSATIONS + " ADD COLUMN "
            + COLUMN_LAST_MESSAGE + " TEXT NOT NULL DEFAULT ''");
        database.execSQL("ALTER TABLE " + TABLE_CONVERSATIONS + " ADD COLUMN "
            + COLUMN_LAST_MESSAGE_TIMESTAMP + " INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE " + TABLE_CONVERSATIONS + " ADD COLUMN "
            + COLUMN_LAST_MESSAGE_TYPE + " TEXT NOT NULL DEFAULT 'TEXT'");
        database.execSQL("ALTER TABLE " + TABLE_CONVERSATIONS + " ADD COLUMN "
            + COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0");
        database.execSQL("UPDATE " + TABLE_CONVERSATIONS + " SET " + COLUMN_CREATED_AT
            + " = " + COLUMN_UPDATED_AT);

        database.execSQL("ALTER TABLE " + TABLE_MESSAGES + " ADD COLUMN "
            + COLUMN_MESSAGE_UUID + " TEXT NOT NULL DEFAULT ''");
        database.execSQL("ALTER TABLE " + TABLE_MESSAGES + " ADD COLUMN "
            + COLUMN_SENDER_USER_ID + " TEXT NOT NULL DEFAULT ''");
        database.execSQL("ALTER TABLE " + TABLE_MESSAGES + " ADD COLUMN "
            + COLUMN_RECEIVER_USER_ID + " TEXT NOT NULL DEFAULT ''");
        database.execSQL("ALTER TABLE " + TABLE_MESSAGES + " ADD COLUMN "
            + COLUMN_DELIVERY_STATUS + " TEXT NOT NULL DEFAULT 'PENDING'");
        database.execSQL("ALTER TABLE " + TABLE_MESSAGES + " ADD COLUMN "
            + COLUMN_MESSAGE_TYPE + " TEXT NOT NULL DEFAULT 'TEXT'");

        List<LegacyMessage> legacyMessages = new ArrayList<>();
        try (Cursor cursor = database.query(TABLE_MESSAGES,
            new String[] {COLUMN_MESSAGE_ID, COLUMN_PEER_ID, COLUMN_OUTGOING},
            null, null, null, null, null)) {
            while (cursor.moveToNext()) {
            legacyMessages.add(new LegacyMessage(cursor.getLong(0), cursor.getString(1),
                cursor.getInt(2) != 0));
            }
        }
        for (LegacyMessage message : legacyMessages) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_MESSAGE_UUID, UUID.randomUUID().toString());
            values.put(COLUMN_SENDER_USER_ID,
                message.outgoing ? "legacy-local" : message.peerUserId);
            values.put(COLUMN_RECEIVER_USER_ID,
                message.outgoing ? message.peerUserId : "legacy-local");
            values.put(COLUMN_DELIVERY_STATUS, message.outgoing ? "FAILED" : "DELIVERED");
            database.update(TABLE_MESSAGES, values, COLUMN_MESSAGE_ID + " = ?",
                new String[] {Long.toString(message.rowId)});
        }

        database.execSQL("UPDATE " + TABLE_CONVERSATIONS + " SET "
            + COLUMN_LAST_MESSAGE + " = COALESCE((SELECT " + COLUMN_BODY + " FROM "
            + TABLE_MESSAGES + " WHERE " + TABLE_MESSAGES + "." + COLUMN_PEER_ID + " = "
            + TABLE_CONVERSATIONS + "." + COLUMN_PEER_ID + " ORDER BY " + COLUMN_MESSAGE_ID
            + " DESC LIMIT 1), ''), " + COLUMN_LAST_MESSAGE_TIMESTAMP + " = COALESCE((SELECT "
            + COLUMN_CREATED_AT + " FROM " + TABLE_MESSAGES + " WHERE " + TABLE_MESSAGES
            + "." + COLUMN_PEER_ID + " = " + TABLE_CONVERSATIONS + "." + COLUMN_PEER_ID
            + " ORDER BY " + COLUMN_MESSAGE_ID + " DESC LIMIT 1), " + COLUMN_UPDATED_AT + ")");
        database.execSQL("CREATE UNIQUE INDEX messages_uuid_unique ON " + TABLE_MESSAGES
            + "(" + COLUMN_MESSAGE_UUID + ")");
    }

    private static final class LegacyMessage {
        final long rowId;
        final String peerUserId;
        final boolean outgoing;

        LegacyMessage(long rowId, String peerUserId, boolean outgoing) {
            this.rowId = rowId;
            this.peerUserId = peerUserId;
            this.outgoing = outgoing;
        }
    }
}
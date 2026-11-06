package com.meshdrop.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

public final class MeshDropDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "meshdrop_chats.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_CONVERSATIONS = "conversations";
    public static final String TABLE_MESSAGES = "messages";
    public static final String COLUMN_PEER_ID = "peer_id";
    public static final String COLUMN_PEER_NAME = "peer_name";
    public static final String COLUMN_UPDATED_AT = "updated_at";
    public static final String COLUMN_MESSAGE_ID = "_id";
    public static final String COLUMN_BODY = "body";
    public static final String COLUMN_OUTGOING = "outgoing";
    public static final String COLUMN_CREATED_AT = "created_at";

    private static volatile MeshDropDatabase instance;

    private MeshDropDatabase(@NonNull Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
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
                + COLUMN_UPDATED_AT + " INTEGER NOT NULL)");
        database.execSQL("CREATE TABLE " + TABLE_MESSAGES + " ("
                + COLUMN_MESSAGE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_PEER_ID + " TEXT NOT NULL, "
                + COLUMN_BODY + " TEXT NOT NULL, "
                + COLUMN_OUTGOING + " INTEGER NOT NULL, "
                + COLUMN_CREATED_AT + " INTEGER NOT NULL, "
                + "FOREIGN KEY(" + COLUMN_PEER_ID + ") REFERENCES " + TABLE_CONVERSATIONS
                + "(" + COLUMN_PEER_ID + ") ON DELETE CASCADE)");
        database.execSQL("CREATE INDEX messages_peer_created_at ON " + TABLE_MESSAGES
                + "(" + COLUMN_PEER_ID + ", " + COLUMN_CREATED_AT + ", " + COLUMN_MESSAGE_ID + ")");
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase database, int oldVersion, int newVersion) {
    }
}
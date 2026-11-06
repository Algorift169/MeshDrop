# Chats

## Current behavior

The Chats destination displays conversations saved on this device. Opening a conversation shows its stored messages, and the composer saves outgoing text locally. Returning to the Chats list refreshes the conversation previews.

Conversations are keyed by peer ID. The thread UI is opened through `MainActivity.openConversation(peerId, displayName)`. At present there is no connected discovery flow that creates a peer conversation, and the composer does not send over a network.

## Storage

`MeshDropDatabase` uses `SQLiteOpenHelper` and creates `meshdrop_chats.db` at schema version 1. Its singleton uses the application context. The existing database name, version, tables, columns, foreign key, and index are preserved, so this refactor does not require deleting or recreating existing chat data.

- `conversations`: `peer_id` primary key, peer display name, and last-updated timestamp.
- `messages`: auto-increment ID, peer ID, text body, outgoing flag, and creation timestamp.
- A foreign key cascades message deletion when its conversation is deleted; an index supports ordered thread queries.

`ChatRepositoryImpl` coordinates the DAOs and performs save operations in one transaction: create the conversation if needed, update its name/activity, then insert the message. An error rolls back the transaction. Conversation and message queries return most recent conversations first and thread messages in chronological order. The DAO uses parameterized selections and closes each cursor.

Repository operations run on a single-thread executor; results and errors are delivered through callbacks on the main thread. The UI receives application models and does not access SQLite or cursors.

## Limitations

Messages are plain local text records. There is no delivery, encryption, attachment, inbound-message, sync, or conversation-deletion UI. The database is private to the app installation.

## Implementation

- UI: `app/src/main/java/com/meshdrop/ui/fragment/ChatFragment.java`
- Database setup: `app/src/main/java/com/meshdrop/database/MeshDropDatabase.java`
- DAOs: `app/src/main/java/com/meshdrop/database/dao/`
- Repository: `app/src/main/java/com/meshdrop/database/repository/`
- Entities: `app/src/main/java/com/meshdrop/database/entity/`
- App models: `app/src/main/java/com/meshdrop/data/model/`
- Chat layout: `app/src/main/res/layout/fragment_chat.xml`

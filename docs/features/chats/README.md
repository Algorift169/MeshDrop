# Chats

## Current behavior

The Chats destination displays conversations saved on this device. Opening a conversation shows its stored messages, and the composer saves outgoing text locally. Returning to the Chats list refreshes the conversation previews.

Conversations are keyed by peer ID. The thread UI is opened through `MainActivity.openConversation(peerId, displayName)`. At present there is no connected discovery flow that creates a peer conversation, and the composer does not send over a network.

## Storage

`ChatDatabase` uses `SQLiteOpenHelper` and creates `meshdrop_chats.db` at schema version 1.

- `conversations`: `peer_id` primary key, peer display name, and last-updated timestamp.
- `messages`: auto-increment ID, peer ID, text body, outgoing flag, and creation timestamp.
- A foreign key cascades message deletion when its conversation is deleted; an index supports ordered thread queries.

Saving a message updates the conversation and inserts the message in one transaction. Conversation and message queries return most recent conversations first and thread messages in chronological order.

## Limitations

Messages are plain local text records. There is no delivery, encryption, attachment, inbound-message, sync, or conversation-deletion UI. The database is private to the app installation.

## Implementation

- UI: `app/src/main/java/com/meshdrop/ui/fragment/ChatFragment.java`
- SQLite helper: `app/src/main/java/com/meshdrop/data/ChatDatabase.java`
- Chat layout: `app/src/main/res/layout/fragment_chat.xml`
